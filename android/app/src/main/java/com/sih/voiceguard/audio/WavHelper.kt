package com.sih.voiceguard.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavHelper {
    /**
     * Converts float audio samples (-1.0 to +1.0) into a standard 16-bit mono 16kHz WAV byte array.
     */
    fun floatsToWavBytes(samples: FloatArray, sampleRate: Int = 16000): ByteArray {
        val shortData = ShortArray(samples.size)
        for (i in samples.indices) {
            val clamped = samples[i].coerceIn(-1.0f, 1.0f)
            shortData[i] = (clamped * 32767.0f).toInt().toShort()
        }

        val byteBuffer = ByteBuffer.allocate(shortData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in shortData) {
            byteBuffer.putShort(s)
        }
        val pcmBytes = byteBuffer.array()
        val totalAudioLen = pcmBytes.size
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = sampleRate * channels * 2

        val header = ByteArray(44)
        val out = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray())
        out.putInt(totalDataLen)
        out.put("WAVE".toByteArray())
        out.put("fmt ".toByteArray())
        out.putInt(16) // Subchunk1Size for PCM
        out.putShort(1) // AudioFormat 1 = PCM
        out.putShort(channels.toShort())
        out.putInt(sampleRate)
        out.putInt(byteRate)
        out.putShort((channels * 2).toShort()) // BlockAlign
        out.putShort(16) // BitsPerSample
        out.put("data".toByteArray())
        out.putInt(totalAudioLen)

        return header + pcmBytes
    }
}
