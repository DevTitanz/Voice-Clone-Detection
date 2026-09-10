import uuid
from datetime import datetime, timezone
import enum
from sqlalchemy import Column, String, Float, Boolean, DateTime, ForeignKey, Integer, Text
from sqlalchemy.orm import relationship
from app.db.database import Base


class UserRole(str, enum.Enum):
    USER = "USER"
    ADMIN = "ADMIN"


class User(Base):
    __tablename__ = "users"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()), index=True)
    username = Column(String(50), unique=True, index=True, nullable=False)
    email = Column(String(255), unique=True, index=True, nullable=False)
    hashed_password = Column(String(255), nullable=False)
    role = Column(String(20), default=UserRole.USER, nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), nullable=False)

    # Relationships
    sessions = relationship("DetectionSession", back_populates="user", cascade="all, delete-orphan")


class DetectionSession(Base):
    """
    Stores auditable detection metadata ONLY.
    ZERO raw audio, waveforms, or caller audio recordings are ever stored here.
    """
    __tablename__ = "detection_sessions"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()), index=True)
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    anonymous_session_id = Column(String(64), unique=True, index=True, nullable=False)
    client_type = Column(String(20), default="web", nullable=False)  # 'android' or 'web'
    final_risk_score = Column(Float, nullable=False)
    classification = Column(String(30), nullable=False)  # 'LOW_RISK', 'MEDIUM_RISK', 'HIGH_RISK'
    confidence = Column(Float, default=0.85, nullable=False)
    audio_duration_seconds = Column(Float, default=0.0, nullable=False)
    model_version = Column(String(50), default="voiceguard-v1.2-acoustic", nullable=False)
    verification_required = Column(Boolean, default=False, nullable=False)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), nullable=False, index=True)

    user = relationship("User", back_populates="sessions")
    verifications = relationship("VerificationLog", back_populates="session", cascade="all, delete-orphan")


class VerificationLog(Base):
    """
    Logs step-up verification events when synthetic voice risk is flagged.
    """
    __tablename__ = "verification_logs"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()), index=True)
    session_id = Column(String(36), ForeignKey("detection_sessions.id", ondelete="CASCADE"), nullable=False, index=True)
    verification_type = Column(String(50), nullable=False)  # 'OTP_CALLBACK', 'SUPERVISOR_APPROVAL', 'SECONDARY_CHANNEL'
    status = Column(String(30), default="PENDING", nullable=False)  # 'PENDING', 'APPROVED', 'REJECTED'
    details = Column(Text, nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), nullable=False)

    session = relationship("DetectionSession", back_populates="verifications")


class AuditLog(Base):
    """
    Security event log for audit compliance.
    Never contains passwords, JWT tokens, or raw voice data.
    """
    __tablename__ = "audit_logs"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()), index=True)
    event_type = Column(String(50), nullable=False, index=True)  # 'LOGIN_SUCCESS', 'LOGIN_FAILED', 'HIGH_RISK_DETECTED', 'SETTINGS_UPDATED'
    user_id = Column(String(36), nullable=True, index=True)
    ip_address = Column(String(45), nullable=True)
    details = Column(Text, nullable=True)
    timestamp = Column(DateTime, default=lambda: datetime.now(timezone.utc), nullable=False, index=True)


class SecurityConfig(Base):
    """
    Global security threshold configuration (modifiable only by ADMIN).
    """
    __tablename__ = "security_config"

    id = Column(Integer, primary_key=True, default=1)
    threshold_low = Column(Float, default=40.0, nullable=False)
    threshold_high = Column(Float, default=70.0, nullable=False)
    enforce_strict_stepup = Column(Boolean, default=True, nullable=False)
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc), nullable=False)
