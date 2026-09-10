from datetime import datetime, timezone
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, desc
from app.db.database import get_db
from app.db.models import User, DetectionSession, VerificationLog, AuditLog, SecurityConfig
from app.db.schemas import (
    AdminMetricsResponse,
    SecurityConfigResponse,
    SecurityConfigUpdate
)
from app.api.deps import require_admin
from app.core.config import settings

router = APIRouter(prefix="/admin", tags=["Admin Controls"])


@router.get("/metrics", response_model=AdminMetricsResponse)
async def get_system_metrics(
    admin_user: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db)
):
    """
    Aggregate security analytics and detection counts.
    Strictly restricted to users with ADMIN role.
    """
    total_users = (await db.execute(select(func.count(User.id)))).scalar() or 0
    total_analyses = (await db.execute(select(func.count(DetectionSession.id)))).scalar() or 0

    high_risk_count = (await db.execute(
        select(func.count(DetectionSession.id)).where(DetectionSession.classification == "HIGH_RISK")
    )).scalar() or 0

    med_risk_count = (await db.execute(
        select(func.count(DetectionSession.id)).where(DetectionSession.classification == "MEDIUM_RISK")
    )).scalar() or 0

    low_risk_count = (await db.execute(
        select(func.count(DetectionSession.id)).where(DetectionSession.classification == "LOW_RISK")
    )).scalar() or 0

    verif_pending = (await db.execute(
        select(func.count(VerificationLog.id)).where(VerificationLog.status == "PENDING")
    )).scalar() or 0

    verif_approved = (await db.execute(
        select(func.count(VerificationLog.id)).where(VerificationLog.status == "APPROVED")
    )).scalar() or 0

    return AdminMetricsResponse(
        total_users=total_users,
        total_analyses=total_analyses,
        high_risk_count=high_risk_count,
        medium_risk_count=med_risk_count,
        low_risk_count=low_risk_count,
        verifications_pending=verif_pending,
        verifications_approved=verif_approved
    )


@router.get("/thresholds", response_model=SecurityConfigResponse)
async def get_thresholds(
    admin_user: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db)
):
    """Retrieve security thresholds."""
    result = await db.execute(select(SecurityConfig).where(SecurityConfig.id == 1))
    config = result.scalars().first()
    if not config:
        config = SecurityConfig(
            id=1,
            threshold_low=settings.RISK_THRESHOLD_LOW,
            threshold_high=settings.RISK_THRESHOLD_HIGH,
            enforce_strict_stepup=True,
            updated_at=datetime.now(timezone.utc)
        )
        db.add(config)
        await db.commit()
        await db.refresh(config)
    return config


@router.put("/thresholds", response_model=SecurityConfigResponse)
async def update_thresholds(
    update_data: SecurityConfigUpdate,
    admin_user: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db)
):
    """Update system security thresholds. Restricted to ADMIN role."""
    result = await db.execute(select(SecurityConfig).where(SecurityConfig.id == 1))
    config = result.scalars().first()
    if not config:
        config = SecurityConfig(id=1)
        db.add(config)

    config.threshold_low = update_data.threshold_low
    config.threshold_high = update_data.threshold_high
    config.enforce_strict_stepup = update_data.enforce_strict_stepup
    config.updated_at = datetime.now(timezone.utc)

    # Dynamic settings update
    settings.RISK_THRESHOLD_LOW = update_data.threshold_low
    settings.RISK_THRESHOLD_HIGH = update_data.threshold_high

    audit = AuditLog(
        event_type="SETTINGS_UPDATED",
        user_id=admin_user.id,
        details=f"Thresholds modified: Low={update_data.threshold_low}, High={update_data.threshold_high}"
    )
    db.add(audit)

    await db.commit()
    await db.refresh(config)
    return config


@router.get("/audit-logs")
async def get_audit_logs(
    limit: int = 50,
    admin_user: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db)
):
    """View system security audit trail."""
    result = await db.execute(
        select(AuditLog).order_by(desc(AuditLog.timestamp)).limit(min(100, limit))
    )
    logs = result.scalars().all()
    return [
        {
            "id": l.id,
            "event_type": l.event_type,
            "user_id": l.user_id,
            "ip_address": l.ip_address,
            "details": l.details,
            "timestamp": l.timestamp
        }
        for l in logs
    ]
