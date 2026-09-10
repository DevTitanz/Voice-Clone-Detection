package com.sih.voiceguard.ai

import kotlin.math.*

/**
 * OnDeviceFeatureExtractor:
 * Extracts acoustic vocoder biomarkers, spectral rolloff, centroid, pitch jitter,
 * and high-frequency energy ratios directly on the Android device's CPU.
 * ZERO raw audio is transmitted over the network or saved to disk.
 */
class OnDeviceFeatureExtractor {

    data class AcousticFeatures(
        val spectralCentroid: Double,
        val spectralRolloff: Double,
        val highFreqRatio: Double,
        val jitterFactor: Double,
        val zeroCrossingRate: Double,
        val spectralFlatness: Double,
        val pitchFundamentalF0: Double = 180.0,
        val pitchVarianceHz: Double = 35.0,
        val energyRms: Double = 0.05
    )

    fun extract(samples: FloatArray, sampleRate: Int = 16000): AcousticFeatures {
        if (samples.isEmpty()) {
            return AcousticFeatures(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val n = samples.size

        // 1. Zero Crossing Rate (ZCR)
        var zeroCrossings = 0
        for (i in 1 until n) {
            if ((samples[i] >= 0 && samples[i - 1] < 0) || (samples[i] < 0 && samples[i - 1] >= 0)) {
                zeroCrossings++
            }
        }
        val zcr = zeroCrossings.toDouble() / n.coerceAtLeast(1)

        // 2. Windowing (Hann) & FFT on 1024 or 2048 samples
        val fftSize = 1024.coerceAtMost(Integer.highestOneBit(n))
        val real = DoubleArray(fftSize)
        val imag = DoubleArray(fftSize)

        for (i in 0 until fftSize) {
            val hann = 0.5 * (1.0 - cos(2.0 * Math.PI * i / (fftSize - 1)))
            real[i] = samples[i].toDouble() * hann
            imag[i] = 0.0
        }

        fft(real, imag)

        // Half spectrum magnitudes
        val numBins = fftSize / 2
        val magnitudes = DoubleArray(numBins)
        val freqPerBin = sampleRate.toDouble() / fftSize
        var totalEnergy = 0.0
        var weightedFreqSum = 0.0

        for (i in 0 until numBins) {
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
            magnitudes[i] = mag
            val energy = mag * mag
            totalEnergy += energy
            val freq = i * freqPerBin
            weightedFreqSum += freq * mag
        }

        // 3. Spectral Centroid
        val spectralCentroid = if (magnitudes.sum() > 0) weightedFreqSum / magnitudes.sum() else 0.0

        // 4. Spectral Rolloff (85% energy frequency)
        var cumulative = 0.0
        val targetEnergy = 0.85 * totalEnergy
        var spectralRolloff = 0.0
        for (i in 0 until numBins) {
            cumulative += magnitudes[i] * magnitudes[i]
            if (cumulative >= targetEnergy) {
                spectralRolloff = i * freqPerBin
                break
            }
        }

        // 5. High-Frequency Vocoder Band Ratio (energy > 5500 Hz vs total)
        var highFreqEnergy = 0.0
        val highFreqCutoffBin = (5500.0 / freqPerBin).toInt().coerceAtMost(numBins - 1)
        for (i in highFreqCutoffBin until numBins) {
            highFreqEnergy += magnitudes[i] * magnitudes[i]
        }
        val highFreqRatio = if (totalEnergy > 0) highFreqEnergy / totalEnergy else 0.0

        // 6. Spectral Flatness (geometric mean / arithmetic mean)
        var logSum = 0.0
        var arithSum = 0.0
        var count = 0
        for (i in 0 until numBins) {
            val mag = magnitudes[i]
            if (mag > 1e-9) {
                logSum += ln(mag)
                arithSum += mag
                count++
            }
        }
        val spectralFlatness = if (count > 0 && arithSum > 0) {
            val geomMean = exp(logSum / count)
            val arithMean = arithSum / count
            (geomMean / arithMean).coerceIn(0.0, 1.0)
        } else 0.0

        // 7. Energy RMS (Intensity / Volume Dynamics)
        var sumSquares = 0.0
        for (i in 0 until n) {
            sumSquares += samples[i] * samples[i]
        }
        val energyRms = sqrt(sumSquares / n.coerceAtLeast(1))

        // 8. Pitch Jitter & F0 estimation via Autocorrelation
        val minLag = (sampleRate / 400).coerceAtLeast(1)
        val maxLag = (sampleRate / 70).coerceAtMost(n - 1)
        var peakLag = minLag
        var maxCorr = -1.0

        for (lag in minLag until maxLag) {
            var corr = 0.0
            val len = n - lag
            for (i in 0 until len) {
                corr += samples[i] * samples[i + lag]
            }
            if (corr > maxCorr) {
                maxCorr = corr
                peakLag = lag
            }
        }

        val fundamentalF0 = if (peakLag > 0) sampleRate.toDouble() / peakLag else 180.0

        // Curvature around peak for jitter factor
        val jitterFactor = if (peakLag in 1 until n - 1 && maxCorr > 0) {
            val cPrev = calculateLagCorr(samples, peakLag - 1)
            val cNext = calculateLagCorr(samples, peakLag + 1)
            val curvature = abs(cPrev - 2 * maxCorr + cNext)
            (curvature / (maxCorr + 1e-5)).coerceIn(0.0, 1.0)
        } else 0.4

        // 9. Frame-by-frame Pitch Variance (Emotional Prosody dynamics)
        // Divide buffer into 4 sub-frames to evaluate pitch dynamism
        val frameSize = n / 4
        val subPitches = mutableListOf<Double>()
        if (frameSize >= 256) {
            for (f in 0 until 4) {
                val start = f * frameSize
                var subMaxCorr = -1.0
                var subPeakLag = minLag
                val subEnd = (start + frameSize).coerceAtMost(n)
                for (lag in minLag until ((sampleRate / 80).coerceAtMost(frameSize - 1))) {
                    var c = 0.0
                    for (i in start until (subEnd - lag)) {
                        c += samples[i] * samples[i + lag]
                    }
                    if (c > subMaxCorr) {
                        subMaxCorr = c
                        subPeakLag = lag
                    }
                }
                if (subPeakLag > 0 && subMaxCorr > 0.001) {
                    subPitches.add(sampleRate.toDouble() / subPeakLag)
                }
            }
        }

        val pitchVarianceHz = if (subPitches.size >= 2) {
            val meanP = subPitches.average()
            sqrt(subPitches.map { (it - meanP) * (it - meanP) }.average())
        } else {
            // Default based on jitter
            jitterFactor * 45.0
        }

        return AcousticFeatures(
            spectralCentroid = spectralCentroid,
            spectralRolloff = spectralRolloff,
            highFreqRatio = highFreqRatio,
            jitterFactor = jitterFactor,
            zeroCrossingRate = zcr,
            spectralFlatness = spectralFlatness,
            pitchFundamentalF0 = fundamentalF0,
            pitchVarianceHz = pitchVarianceHz,
            energyRms = energyRms
        )
    }

    private fun calculateLagCorr(samples: FloatArray, lag: Int): Double {
        var sum = 0.0
        val len = samples.size - lag
        for (i in 0 until len) {
            sum += samples[i] * samples[i + lag]
        }
        return sum
    }

    // Cooley-Tukey Radix-2 FFT
    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * Math.PI / len
            val wStepR = cos(angle)
            val wStepI = sin(angle)
            var i = 0
            while (i < n) {
                var wR = 1.0
                var wI = 0.0
                for (k in 0 until halfLen) {
                    val pos = i + k
                    val match = pos + halfLen
                    val tR = wR * real[match] - wI * imag[match]
                    val tI = wR * imag[match] + wI * real[match]
                    real[match] = real[pos] - tR
                    imag[match] = imag[pos] - tI
                    real[pos] += tR
                    imag[pos] += tI
                    val nextWR = wR * wStepR - wI * wStepI
                    wI = wR * wStepI + wI * wStepR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }
    }
}
