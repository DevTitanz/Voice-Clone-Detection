import bcrypt
from datetime import datetime, timedelta, timezone
from typing import Optional, Dict, Any, Set
from jose import jwt, JWTError
from app.core.config import settings

# Blacklist set for revoked tokens (logout)
_revoked_tokens: Set[str] = set()


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Safely verify a password against its bcrypt hash."""
    try:
        return bcrypt.checkpw(plain_password.encode("utf-8"), hashed_password.encode("utf-8"))
    except Exception:
        return False


def get_password_hash(password: str) -> str:
    """Hash password using bcrypt with strong work factor (rounds=12)."""
    salt = bcrypt.gensalt(rounds=12)
    return bcrypt.hashpw(password.encode("utf-8"), salt).decode("utf-8")


def create_access_token(
    subject: str,
    role: str,
    expires_delta: Optional[timedelta] = None,
    extra_claims: Optional[Dict[str, Any]] = None
) -> str:
    """Generate a signed, short-lived JWT token."""
    now = datetime.now(timezone.utc)
    if expires_delta:
        expire = now + expires_delta
    else:
        expire = now + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)

    to_encode: Dict[str, Any] = {
        "sub": str(subject),
        "role": role,
        "iat": now,
        "exp": expire,
        "iss": "voxshield-auth-service",
    }
    if extra_claims:
        to_encode.update(extra_claims)

    encoded_jwt = jwt.encode(
        to_encode,
        settings.JWT_SECRET,
        algorithm=settings.JWT_ALGORITHM
    )
    return encoded_jwt


def decode_access_token(token: str) -> Optional[Dict[str, Any]]:
    """Decode and validate a JWT access token."""
    if token in _revoked_tokens:
        return None
    try:
        payload = jwt.decode(
            token,
            settings.JWT_SECRET,
            algorithms=[settings.JWT_ALGORITHM],
            options={"verify_aud": False}
        )
        return payload
    except JWTError:
        return None


def revoke_token(token: str) -> None:
    """Add a token to the revoked tokens blacklist."""
    _revoked_tokens.add(token)


def is_token_revoked(token: str) -> bool:
    """Check if token is in blacklist."""
    return token in _revoked_tokens
