import collections
import numpy as np
from typing import Optional, Dict, Any
from app.services.detector import detector_service


class SlidingStreamBuffer:
    """
    Sliding window buffer for real-time audio chunk processing.
    Stores audio only in volatile RAM within a strictly bounded window (max 3 seconds).
    Purges buffer immediately upon session termination.
    """

    def __init__(self, sample_rate: int = 16000, window_seconds: float = 3.0):
        self.sample_rate = sample_rate
        self.max_samples = int(sample_rate * window_seconds)
        self.min_samples_to_analyze = int(sample_rate * 0.5)  # 0.5 sec min before analyzing
        # Use deque with maxlen to guarantee fixed RAM usage and auto-drop of old samples
        self._samples = collections.deque(maxlen=self.max_samples)
        self.total_samples_processed = 0

    def add_pcm_chunk(self, raw_pcm_bytes: bytes) -> Optional[Dict[str, Any]]:
        """Add a raw PCM16 chunk, update window, and run inference if sufficient audio exists."""
        if not raw_pcm_bytes:
            return None

        # Convert int16 bytes to float32
        chunk_arr = detector_service.parse_pcm16_chunk(raw_pcm_bytes, self.sample_rate)
        self._samples.extend(chunk_arr.tolist())
        self.total_samples_processed += len(chunk_arr)

        if len(self._samples) >= self.min_samples_to_analyze:
            window_arr = np.array(self._samples, dtype=np.float32)
            result = detector_service.analyze_audio_buffer(window_arr, self.sample_rate)
            result["audio_duration_seconds"] = round(self.total_samples_processed / self.sample_rate, 2)
            return result

        return None

    def purge(self):
        """Immediately clear all audio samples from volatile memory."""
        self._samples.clear()
        self.total_samples_processed = 0
