from datetime import timedelta
from fastapi import APIRouter, Depends, HTTPException, status, Request
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.core.config import settings
from app.core.security import (
    verify_password,
    get_password_hash,
    create_access_token,
    revoke_token
)
from app.core.rate_limit import limiter
from app.db.database import get_db
from app.db.models import User, UserRole, AuditLog
from app.db.schemas import (
    UserRegisterRequest,
    UserLoginRequest,
    TokenResponse,
    UserPublicResponse
)
from app.api.deps import get_current_user, oauth2_scheme

router = APIRouter(prefix="/auth", tags=["Authentication"])


@router.post("/register", response_model=UserPublicResponse, status_code=status.HTTP_201_CREATED)
@limiter.limit(settings.RATE_LIMIT_AUTH)
async def register(
    request: Request,
    user_in: UserRegisterRequest,
    db: AsyncSession = Depends(get_db)
):
    """Register a new user account with strong password hashing."""
    # Check if username or email already exists
    existing_user = await db.execute(
        select(User).where((User.username == user_in.username) | (User.email == user_in.email))
    )
    if existing_user.scalars().first():
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Username or email already registered."
        )

    # First user can be registered as ADMIN if none exist, otherwise USER
    user_count_result = await db.execute(select(User))
    first_user = user_count_result.scalars().first() is None
    role = UserRole.ADMIN.value if first_user else UserRole.USER.value

    new_user = User(
        username=user_in.username,
        email=user_in.email,
        hashed_password=get_password_hash(user_in.password),
        role=role,
        is_active=True
    )
    db.add(new_user)

    # Audit log entry (safe: no passwords/secrets logged)
    audit = AuditLog(
        event_type="USER_REGISTERED",
        user_id=new_user.id,
        ip_address=request.client.host if request.client else "unknown",
        details=f"User {user_in.username} registered with role {role}"
    )
    db.add(audit)

    await db.commit()
    await db.refresh(new_user)
    return new_user


@router.post("/login", response_model=TokenResponse)
@limiter.limit(settings.RATE_LIMIT_AUTH)
async def login(
    request: Request,
    credentials: UserLoginRequest,
    db: AsyncSession = Depends(get_db)
):
    """Authenticate user with rate-limiting and issue short-lived JWT access token."""
    result = await db.execute(select(User).where(User.username == credentials.username))
    user = result.scalars().first()

    if not user or not verify_password(credentials.password, user.hashed_password):
        # Generic authentication failure message (prevents username enumeration)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid username or password.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Account is deactivated."
        )

    # Issue short-lived JWT token
    access_token = create_access_token(
        subject=user.id,
        role=user.role,
        expires_delta=timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    )

    # Audit event
    audit = AuditLog(
        event_type="LOGIN_SUCCESS",
        user_id=user.id,
        ip_address=request.client.host if request.client else "unknown",
        details="User successfully authenticated"
    )
    db.add(audit)
    await db.commit()

    return TokenResponse(
        access_token=access_token,
        token_type="bearer",
        expires_in_minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES,
        user=UserPublicResponse.model_validate(user)
    )


@router.post("/logout")
async def logout(
    token: str = Depends(oauth2_scheme),
    current_user: User = Depends(get_current_user)
):
    """Revoke active JWT access token."""
    if token:
        revoke_token(token)
    return {"success": True, "message": "Successfully logged out. Token revoked."}


@router.get("/me", response_model=UserPublicResponse)
async def get_me(current_user: User = Depends(get_current_user)):
    """Retrieve current authenticated user's profile."""
    return current_user
