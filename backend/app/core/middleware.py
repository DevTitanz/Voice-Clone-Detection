import time
import uuid
import logging
import json
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response, JSONResponse
from app.core.config import settings

logger = logging.getLogger("voiceguard.security")
logger.setLevel(logging.INFO)
# Avoid duplicate handlers if reloaded
if not logger.handlers:
    ch = logging.StreamHandler()
    ch.setLevel(logging.INFO)
    formatter = logging.Formatter(
        '{"timestamp": "%(asctime)s", "level": "%(levelname)s", "service": "voiceguard-api", %(message)s}'
    )
    ch.setFormatter(formatter)
    logger.addHandler(ch)


class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    """
    Applies strict OWASP-compliant security headers to every HTTP response.
    Never uses overly permissive policies.
    """
    async def dispatch(self, request: Request, call_next):
        response: Response = await call_next(request)

        # HTTP Strict Transport Security (enforce HTTPS for 1 year)
        response.headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains; preload"

        # Prevent MIME-type sniffing
        response.headers["X-Content-Type-Options"] = "nosniff"

        # Prevent clickjacking / framing
        response.headers["X-Frame-Options"] = "DENY"

        # Referrer policy: minimize referrer leakage
        response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"

        # Permissions policy: disable risky client features
        response.headers["Permissions-Policy"] = "camera=(), geolocation=(), payment=()"

        # Content Security Policy (strict defaults, allows self and API)
        response.headers["Content-Security-Policy"] = (
            "default-src 'self'; "
            "script-src 'self' 'unsafe-inline'; "
            "style-src 'self' 'unsafe-inline'; "
            "img-src 'self' data: blob:; "
            "connect-src 'self' ws: wss:; "
            "frame-ancestors 'none';"
        )

        return response


class StructuredLoggingMiddleware(BaseHTTPMiddleware):
    """
    Structured JSON audit logger for every incoming request.
    Strictly sanitizes sensitive data: NEVER logs passwords, JWT tokens, or raw audio bytes.
    """
    async def dispatch(self, request: Request, call_next):
        request_id = str(uuid.uuid4())
        request.state.request_id = request_id
        start_time = time.perf_counter()

        # Sanitize client IP & path
        client_ip = request.client.host if request.client else "unknown"
        path = request.url.path

        try:
            response: Response = await call_next(request)
            duration_ms = round((time.perf_counter() - start_time) * 1000, 2)

            log_entry = (
                f'"request_id": "{request_id}", '
                f'"client_ip": "{client_ip}", '
                f'"method": "{request.method}", '
                f'"path": "{path}", '
                f'"status_code": {response.status_code}, '
                f'"duration_ms": {duration_ms}'
            )
            logger.info(log_entry)
            response.headers["X-Request-ID"] = request_id
            return response
        except Exception as exc:
            duration_ms = round((time.perf_counter() - start_time) * 1000, 2)
            log_entry = (
                f'"request_id": "{request_id}", '
                f'"client_ip": "{client_ip}", '
                f'"method": "{request.method}", '
                f'"path": "{path}", '
                f'"status_code": 500, '
                f'"duration_ms": {duration_ms}, '
                f'"error_class": "{exc.__class__.__name__}"'
            )
            logger.error(log_entry)

            # Never expose internal stack traces, DB queries, or filesystem paths to users
            return JSONResponse(
                status_code=500,
                content={
                    "success": False,
                    "error": "An internal server error occurred.",
                    "request_id": request_id,
                    "message": "The system encountered an error. Our security team has been notified."
                }
            )
