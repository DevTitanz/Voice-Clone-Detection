from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from slowapi.errors import RateLimitExceeded

from app.core.config import settings
from app.core.rate_limit import limiter
from app.core.middleware import SecurityHeadersMiddleware, StructuredLoggingMiddleware
from app.db.database import init_db
from app.api.v1.router import api_v1_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Safe startup: initialize database tables
    await init_db()
    yield
    # Shutdown logic if required


app = FastAPI(
    title=settings.PROJECT_NAME,
    version="1.0.0",
    docs_url="/docs" if settings.DEBUG or settings.ENVIRONMENT == "development" else None,
    redoc_url=None,
    lifespan=lifespan
)

# 1. Attach SlowAPI Rate Limiter
app.state.limiter = limiter


@app.exception_handler(RateLimitExceeded)
async def custom_rate_limit_handler(request: Request, exc: RateLimitExceeded):
    """Production-safe HTTP 429 response when rate limits are exceeded."""
    return JSONResponse(
        status_code=429,
        content={
            "success": False,
            "error": "Rate limit exceeded. Please wait before retrying.",
            "detail": str(exc.detail)
        }
    )


# 2. Add Security Headers Middleware
app.add_middleware(SecurityHeadersMiddleware)

# 3. Add Structured JSON Logging Middleware
app.add_middleware(StructuredLoggingMiddleware)

# 4. Add Strict CORS Middleware (Strict allowlist - NO wildcards in authenticated production)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "X-Request-ID", "Accept"],
    expose_headers=["X-Request-ID"],
    max_age=600,
)

# 5. Mount API v1 Routes
app.include_router(api_v1_router, prefix=settings.API_V1_PREFIX)


@app.get("/health", tags=["Health"])
async def health_check():
    """System health check and privacy assurance endpoint."""
    return {
        "status": "healthy",
        "service": "VoiceGuard AI Detection Gateway",
        "version": "1.0.0",
        "ai_engine": settings.MODEL_VERSION,
        "audio_storage_policy": "ZERO_RETENTION_VOLATILE_MEMORY_ONLY",
        "security": {
            "tls_enforced": True,
            "rate_limiting_active": True,
            "rbac_enabled": True
        }
    }
