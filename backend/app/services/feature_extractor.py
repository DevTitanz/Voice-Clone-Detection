import numpy as np
from scipy import signal
from typing import Dict, Any, Tuple


class AcousticFeatureExtractor:
    """
    Extracts acoustic biomarkers indicative of neural vocoders, voice cloning,
    and synthetic speech synthesis directly from audio waveforms in memory.
    """

    @staticmethod
    def extract_features(audio_data: np.ndarray, sample_rate: int = 16000) -> Dict[str, float]:
        """
        Extract key synthetic speech biomarkers from mono audio buffer.
        """
        if len(audio_data) == 0:
            return {
                "spectral_rolloff": 0.0,
                "spectral_centroid": 0.0,
                "spectral_flatness": 0.0,
                "jitter_factor": 0.0,
                "zero_crossing_rate": 0.0,
                "high_freq_ratio": 0.0,
                "energy_variance": 0.0,
            }

        # Normalize audio
        audio = audio_data.astype(np.float32)
        max_val = np.max(np.abs(audio))
        if max_val > 0:
            audio = audio / max_val

        n_samples = len(audio)
        if n_samples < 512:
            return {
                "spectral_rolloff": 0.0,
                "spectral_centroid": 0.0,
                "spectral_flatness": 0.0,
                "jitter_factor": 0.0,
                "zero_crossing_rate": 0.0,
                "high_freq_ratio": 0.0,
                "energy_variance": 0.0,
            }

        # 1. Zero Crossing Rate (ZCR)
        zero_crossings = np.nonzero(np.diff(audio > 0))[0]
        zcr = float(len(zero_crossings) / max(1, n_samples))

        # 2. FFT Spectrum Analysis
        # Use Hann window to minimize spectral leakage
        window = np.hanning(min(n_samples, 2048))
        segment = audio[:len(window)] * window
        fft_vals = np.abs(np.fft.rfft(segment))
        freqs = np.fft.rfftfreq(len(segment), 1.0 / sample_rate)

        # 3. Spectral Centroid
        sum_fft = np.sum(fft_vals)
        if sum_fft > 0:
            spectral_centroid = float(np.sum(freqs * fft_vals) / sum_fft)
        else:
            spectral_centroid = 0.0

        # 4. Spectral Rolloff (85% energy frequency)
        cumulative_energy = np.cumsum(fft_vals**2)
        total_energy = cumulative_energy[-1] if len(cumulative_energy) > 0 else 0
        if total_energy > 0:
            rolloff_idx = np.where(cumulative_energy >= 0.85 * total_energy)[0]
            spectral_rolloff = float(freqs[rolloff_idx[0]]) if len(rolloff_idx) > 0 else 0.0
        else:
            spectral_rolloff = 0.0

        # 5. High-Frequency Vocoder Energy Ratio (energy > 5500 Hz vs total)
        high_freq_mask = freqs > 5500
        high_freq_energy = np.sum(fft_vals[high_freq_mask]**2) if np.any(high_freq_mask) else 0.0
        high_freq_ratio = float(high_freq_energy / total_energy) if total_energy > 0 else 0.0

        # 6. Spectral Flatness (Wiener entropy: geometric mean / arithmetic mean)
        positive_fft = fft_vals[fft_vals > 1e-10]
        if len(positive_fft) > 0:
            geom_mean = np.exp(np.mean(np.log(positive_fft)))
            arith_mean = np.mean(positive_fft)
            spectral_flatness = float(geom_mean / arith_mean) if arith_mean > 0 else 0.0
        else:
            spectral_flatness = 0.0

        # 7. Pitch Jitter / Regularity estimation via Autocorrelation
        # Human vocal folds have ~0.5% - 2.0% natural jitter; vocoders often have unnaturally smooth or anomalous pitch
        corr = signal.correlate(audio, audio, mode="full")
        corr = corr[len(corr)//2:]
        # Search fundamental frequency in human speech range (70Hz - 400Hz)
        min_lag = int(sample_rate / 400)
        max_lag = int(sample_rate / 70)
        fundamental_f0 = 180.0
        if len(corr) > max_lag:
            sub_corr = corr[min_lag:max_lag]
            peak_lag = min_lag + np.argmax(sub_corr)
            if peak_lag > 0:
                fundamental_f0 = float(sample_rate / peak_lag)
            # Local peak sharpness
            if peak_lag > 1 and peak_lag < len(corr) - 1:
                curvature = float(corr[peak_lag-1] - 2*corr[peak_lag] + corr[peak_lag+1])
                jitter_factor = float(abs(curvature) / (abs(corr[peak_lag]) + 1e-6))
            else:
                jitter_factor = 0.5
        else:
            jitter_factor = 0.5

        # 8. Frame-by-frame pitch variance (emotional prosodic dynamics)
        frame_len = min(1024, n_samples // 4) if n_samples >= 2048 else n_samples
        sub_pitches = []
        if frame_len >= 256:
            num_f = min(4, n_samples // frame_len)
            for f in range(num_f):
                sub_audio = audio[f*frame_len:(f+1)*frame_len]
                sub_c = signal.correlate(sub_audio, sub_audio, mode="full")[len(sub_audio)-1:]
                if len(sub_c) > max_lag:
                    sub_p_lag = min_lag + np.argmax(sub_c[min_lag:max_lag])
                    if sub_p_lag > 0:
                        sub_pitches.append(float(sample_rate / sub_p_lag))
        
        pitch_variance_hz = float(np.std(sub_pitches)) if len(sub_pitches) >= 2 else float(jitter_factor * 40.0)

        # 9. Short-time energy variance & RMS intensity
        energy_rms = float(np.sqrt(np.mean(audio**2)))
        frame_len_energy = min(512, n_samples // 4) if n_samples >= 1024 else n_samples
        if frame_len_energy > 0:
            num_frames = n_samples // frame_len_energy
            frames = audio[:num_frames * frame_len_energy].reshape((num_frames, frame_len_energy))
            frame_energies = np.sum(frames**2, axis=1)
            energy_variance = float(np.var(frame_energies))
        else:
            energy_variance = 0.0

        return {
            "spectral_rolloff": round(spectral_rolloff, 2),
            "spectral_centroid": round(spectral_centroid, 2),
            "spectral_flatness": round(spectral_flatness, 4),
            "jitter_factor": round(jitter_factor, 4),
            "zero_crossing_rate": round(zcr, 4),
            "high_freq_ratio": round(high_freq_ratio, 4),
            "energy_variance": round(energy_variance, 6),
            "pitch_fundamental_f0": round(fundamental_f0, 1),
            "pitch_variance_hz": round(pitch_variance_hz, 1),
            "energy_rms": round(energy_rms, 4)
        }
