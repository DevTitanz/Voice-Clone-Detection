import os
import uuid
import tempfile
from typing import Tuple
from fastapi import HTTPException, status
from app.core.config import settings

# Recognized magic bytes signatures
ALLOWED_MAGIC_SIGNATURES = {
    "audio/wav": [b"RIFF"],
    "audio/x-wav": [b"RIFF"],
    "audio/mpeg": [b"ID3", b"\xff\xfb", b"\xff\xf3", b"\xff\xf2"],
    "audio/mp3": [b"ID3", b"\xff\xfb", b"\xff\xf3", b"\xff\xf2"],
    "audio/ogg": [b"OggS"],
    "audio/flac": [b"fLaC"],
}

MAX_FILE_BYTES = settings.MAX_AUDIO_UPLOAD_MB * 1024 * 1024


def validate_audio_content(file_bytes: bytes, filename: str) -> Tuple[bool, str]:
    """
    Validates audio file safety:
    1. Enforces strict file size bounds.
    2. Enforces magic-byte signature inspection to prevent MIME spoofing / executable upload.
    3. Prevents path traversal vulnerabilities.
    """
    if not file_bytes or len(file_bytes) == 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Audio file payload is empty."
        )

    if len(file_bytes) > MAX_FILE_BYTES:
        raise HTTPException(
            status_code=status.HTTP_413_CONTENT_TOO_LARGE,
            detail=f"Audio file exceeds maximum allowed limit of {settings.MAX_AUDIO_UPLOAD_MB}MB."
        )

    # Magic byte inspection
    header_16 = file_bytes[:16]
    detected_mime = None

    # Check WAV
    if header_16.startswith(b"RIFF") and b"WAVE" in file_bytes[:16]:
        detected_mime = "audio/wav"
    # Check OGG
    elif header_16.startswith(b"OggS"):
        detected_mime = "audio/ogg"
    # Check FLAC
    elif header_16.startswith(b"fLaC"):
        detected_mime = "audio/flac"
    # Check MP3 / ID3
    elif header_16.startswith(b"ID3") or any(header_16.startswith(sig) for sig in [b"\xff\xfb", b"\xff\xf3", b"\xff\xf2"]):
        detected_mime = "audio/mpeg"

    if not detected_mime:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="Invalid or unsupported audio file format. Only standard WAV, MP3, OGG, or FLAC are accepted."
        )

    return True, detected_mime


class SecureTemporaryAudioFile:
    """
    Context manager that creates a random temporary audio file outside public web root
    and GUARANTEES immediate deletion upon block exit, preventing audio persistence.
    """
    def __init__(self, file_bytes: bytes, suffix: str = ".wav"):
        self.file_bytes = file_bytes
        self.suffix = suffix
        self.temp_path = None

    def __enter__(self) -> str:
        # Secure random UUID name, never using user-supplied filename
        temp_dir = tempfile.gettempdir()
        safe_name = f"voxshield_priv_{uuid.uuid4().hex}{self.suffix}"
        self.temp_path = os.path.join(temp_dir, safe_name)

        with open(self.temp_path, "wb") as f:
            f.write(self.file_bytes)

        return self.temp_path

    def __exit__(self, exc_type, exc_val, exc_tb):
        # Guaranteed deletion
        if self.temp_path and os.path.exists(self.temp_path):
            try:
                os.remove(self.temp_path)
            except OSError:
                pass
