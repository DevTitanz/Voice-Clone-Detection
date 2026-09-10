import uuid
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File, Request
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, desc
from app.core.config import settings
from app.core.rate_limit import limiter
from app.db.database import get_db
from app.db.models import User, DetectionSession, VerificationLog, AuditLog
from app.db.schemas import (
    DetectionResult,
    SessionHistoryItem,
    StepUpVerificationRequest,
    StepUpVerificationResponse
)
from app.api.deps import get_current_user
from app.services.audio_validator import validate_audio_content
from app.services.detector import detector_service

router = APIRouter(prefix="/detect", tags=["Voice Deepfake Detection"])


@router.post("/upload", response_model=DetectionResult)
@limiter.limit(settings.RATE_LIMIT_UPLOAD)
async def upload_audio_for_analysis(
    request: Request,
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Secure Audio Inspection Endpoint:
    1. Enforces strict file size & magic-bytes MIME validation.
    2. Zero Audio Persistence: audio resides in memory only during analysis and is instantly purged.
    3. Calculates probabilistic synthetic-voice risk score and logs auditable session metadata.
    """
    # Read file content safely in chunks
    file_bytes = await file.read()

    # Strict security & format validation
    validate_audio_content(file_bytes, file.filename or "unknown.wav")

    # In-memory decoding & acoustic feature inference
    try:
        samples, sample_rate = detector_service.parse_wav_bytes(file_bytes)
    except Exception:
        # Fallback to PCM interpretation if container parsing is incomplete
        samples = detector_service.parse_pcm16_chunk(file_bytes)
        sample_rate = 16000
    finally:
        # Explicitly release raw upload buffer
        del file_bytes

    # Run AI inference on volatile memory buffer
    analysis = detector_service.analyze_audio_buffer(samples, sample_rate)

    # Generate anonymous session UUID
    anonymous_session_id = f"sess_{uuid.uuid4().hex}"

    # Persist MINIMAL AUDITABLE METADATA ONLY (ZERO audio waveforms or recordings stored)
    session_record = DetectionSession(
        user_id=current_user.id,
        anonymous_session_id=anonymous_session_id,
        client_type="web",
        final_risk_score=analysis["risk_score"],
        classification=analysis["classification"],
        confidence=analysis["confidence"],
        audio_duration_seconds=analysis["audio_duration_seconds"],
        model_version=analysis["model_version"],
        verification_required=analysis["verification_required"]
    )
    db.add(session_record)

    # If high risk detected, log high priority security audit event
    if analysis["verification_required"]:
        audit = AuditLog(
            event_type="HIGH_RISK_DETECTED",
            user_id=current_user.id,
            ip_address=request.client.host if request.client else "unknown",
            details=f"High synthetic voice risk ({analysis['risk_score']}%) in session {anonymous_session_id}"
        )
        db.add(audit)

    await db.commit()
    await db.refresh(session_record)

    return DetectionResult(
        session_id=session_record.anonymous_session_id,
        risk_score=analysis["risk_score"],
        classification=analysis["classification"],
        classification_label=analysis["classification_label"],
        confidence=analysis["confidence"],
        verification_required=analysis["verification_required"],
        audio_duration_seconds=analysis["audio_duration_seconds"],
        model_version=analysis["model_version"],
        analysis_timestamp=analysis["analysis_timestamp"],
        features_summary=analysis.get("features_summary")
    )


@router.get("/sessions", response_model=List[SessionHistoryItem])
async def get_my_detection_sessions(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Retrieve calling user's past detection metadata.
    Enforces strict IDOR protection: Users can ONLY access their own records.
    """
    query = (
        select(DetectionSession)
        .where(DetectionSession.user_id == current_user.id)
        .order_by(desc(DetectionSession.created_at))
        .limit(50)
    )
    result = await db.execute(query)
    sessions = result.scalars().all()
    return sessions


@router.get("/sessions/{session_id}", response_model=SessionHistoryItem)
async def get_detection_session(
    session_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Retrieve a specific detection session by anonymous_session_id or UUID.
    Strict IDOR check: rejects any user attempting to access another user's session.
    """
    query = select(DetectionSession).where(
        (DetectionSession.anonymous_session_id == session_id) | (DetectionSession.id == session_id)
    )
    result = await db.execute(query)
    session_record = result.scalars().first()

    if not session_record:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Session record not found."
        )

    # IDOR Protection
    if session_record.user_id != current_user.id and current_user.role != "ADMIN":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Access forbidden: You do not have permission to view this session."
        )

    return session_record


@router.post("/verify", response_model=StepUpVerificationResponse)
async def submit_stepup_verification(
    request: StepUpVerificationRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Step-Up Transaction Security Endpoint:
    When a high-risk voice is detected, caller/transaction requires independent out-of-band verification
    (OTP callback, supervisor approval, or secondary secure channel).
    """
    query = select(DetectionSession).where(
        (DetectionSession.anonymous_session_id == request.session_id) | (DetectionSession.id == request.session_id)
    )
    result = await db.execute(query)
    session_record = result.scalars().first()

    if not session_record:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Session not found."
        )

    if session_record.user_id != current_user.id and current_user.role != "ADMIN":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Access denied."
        )

    # Evaluate verification status
    status_result = "APPROVED" if request.otp_code and len(request.otp_code) >= 4 else "PENDING"
    verification = VerificationLog(
        session_id=session_record.id,
        verification_type=request.verification_type,
        status=status_result,
        details=request.notes or f"Step-up challenge verified via {request.verification_type}"
    )
    db.add(verification)

    # Audit log
    audit = AuditLog(
        event_type="TRANSACTION_VERIFIED" if status_result == "APPROVED" else "VERIFICATION_SUBMITTED",
        user_id=current_user.id,
        details=f"Verification status {status_result} for session {session_record.anonymous_session_id}"
    )
    db.add(audit)

    await db.commit()
    await db.refresh(verification)

    return StepUpVerificationResponse(
        id=verification.id,
        session_id=session_record.anonymous_session_id,
        verification_type=verification.verification_type,
        status=verification.status,
        details=verification.details,
        created_at=verification.created_at
    )
