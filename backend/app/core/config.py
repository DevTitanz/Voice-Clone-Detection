import os
from typing import List, Union
from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    ENVIRONMENT: str = "development"
    PROJECT_NAME: str = "VoiceGuard AI - Real-Time Voice Cloning Detection"
    DEBUG: bool = False

    # Security & Auth
    # A safe default for development; in production, validated to require at least 32 characters
    JWT_SECRET: str = "voiceguard_dev_secret_key_minimum_32_chars_long_abcdef123456789"
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30

    # Database: Supports PostgreSQL (postgresql+asyncpg://...) or SQLite fallback for testing
    DATABASE_URL: str = "sqlite+aiosqlite:///./voiceguard.db"

    # CORS: Explicit allowlist, comma-separated
    CORS_ORIGINS: Union[List[str], str] = [
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:3000",
        "http://127.0.0.1:3000",
    ]

    # API Prefix
    API_V1_PREFIX: str = "/api/v1"
    API_BASE_URL: str = "http://localhost:8000"

    # AI Detection Thresholds (Configurable on backend only)
    RISK_THRESHOLD_LOW: float = 40.0
    RISK_THRESHOLD_HIGH: float = 70.0
    MODEL_VERSION: str = "voiceguard-v1.2-acoustic"

    # Privacy & Limits
    ALLOW_PERSISTENT_AUDIO: bool = False
    MAX_AUDIO_UPLOAD_MB: int = 10
    MAX_WS_MESSAGE_BYTES: int = 262144  # 256 KB per frame

    # Rate Limiting
    RATE_LIMIT_AUTH: str = "10/minute"
    RATE_LIMIT_UPLOAD: str = "30/minute"
    RATE_LIMIT_STREAM: str = "120/minute"

    @field_validator("CORS_ORIGINS", mode="before")
    @classmethod
    def parse_cors_origins(cls, v: Union[str, List[str]]) -> List[str]:
        if isinstance(v, str):
            return [i.strip() for i in v.split(",") if i.strip()]
        return v

    @field_validator("JWT_SECRET")
    @classmethod
    def validate_jwt_secret(cls, v: str, info) -> str:
        # In production mode, forbid default or weak secrets
        env = os.getenv("ENVIRONMENT", "development")
        if env.lower() == "production":
            if "dev" in v.lower() or len(v) < 32:
                raise ValueError("In production, JWT_SECRET must be at least 32 characters and cannot use dev keys.")
        return v

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )


settings = Settings()
