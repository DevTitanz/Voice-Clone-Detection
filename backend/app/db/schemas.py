from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel, EmailStr, Field, field_validator, ConfigDict


# User Schemas
class UserRegisterRequest(BaseModel):
    username: str = Field(..., min_length=3, max_length=50, pattern=r"^[a-zA-Z0-9_-]+$")
    email: EmailStr
    password: str = Field(..., min_length=8, max_length=128)

    @field_validator("password")
    @classmethod
    def validate_password_strength(cls, v: str) -> str:
        if not any(c.isupper() for c in v):
            raise ValueError("Password must contain at least one uppercase letter.")
        if not any(c.islower() for c in v):
            raise ValueError("Password must contain at least one lowercase letter.")
        if not any(c.isdigit() for c in v):
            raise ValueError("Password must contain at least one digit.")
        return v


class UserLoginRequest(BaseModel):
    username: str = Field(..., min_length=3, max_length=50)
    password: str = Field(..., min_length=1, max_length=128)


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_in_minutes: int
    user: "UserPublicResponse"


class UserPublicResponse(BaseModel):
    id: str
    username: str
    email: str
    role: str
    is_active: bool
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


# Detection Session Schemas
class DetectionResult(BaseModel):
    session_id: str
    risk_score: float = Field(..., ge=0.0, le=100.0)
    classification: str  # 'LOW_RISK', 'MEDIUM_RISK', 'HIGH_RISK'
    classification_label: str  # 'Natural voice patterns detected', 'Suspicious acoustic characteristics', 'AI-generated voice suspected'
    confidence: float
    verification_required: bool
    audio_duration_seconds: float
    model_version: str
    analysis_timestamp: datetime
    features_summary: Optional[dict] = None


class SessionHistoryItem(BaseModel):
    id: str
    anonymous_session_id: str
    client_type: str
    final_risk_score: float
    classification: str
    confidence: float
    audio_duration_seconds: float
    model_version: str
    verification_required: bool
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


# Verification Schemas
class StepUpVerificationRequest(BaseModel):
    session_id: str
    verification_type: str = Field(..., pattern=r"^(OTP_CALLBACK|SUPERVISOR_APPROVAL|SECONDARY_CHANNEL)$")
    otp_code: Optional[str] = Field(None, min_length=4, max_length=8)
    notes: Optional[str] = Field(None, max_length=255)


class StepUpVerificationResponse(BaseModel):
    id: str
    session_id: str
    verification_type: str
    status: str
    details: Optional[str]
    created_at: datetime


# Admin & Config Schemas
class SecurityConfigUpdate(BaseModel):
    threshold_low: float = Field(..., ge=10.0, le=50.0)
    threshold_high: float = Field(..., ge=51.0, le=95.0)
    enforce_strict_stepup: bool


class SecurityConfigResponse(BaseModel):
    threshold_low: float
    threshold_high: float
    enforce_strict_stepup: bool
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)


class AdminMetricsResponse(BaseModel):
    total_users: int
    total_analyses: int
    high_risk_count: int
    medium_risk_count: int
    low_risk_count: int
    verifications_pending: int
    verifications_approved: int
