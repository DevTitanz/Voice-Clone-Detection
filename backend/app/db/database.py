from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm import declarative_base
from app.core.config import settings

# In production: "postgresql+asyncpg://user:pass@host/dbname"
# In local dev: "sqlite+aiosqlite:///./voxshield.db"
DATABASE_URL = settings.DATABASE_URL

# Safe connection arguments: SQLite needs check_same_thread=False; PostgreSQL uses default pooling
connect_args = {}
if DATABASE_URL.startswith("sqlite"):
    connect_args["check_same_thread"] = False

engine = create_async_engine(
    DATABASE_URL,
    echo=settings.DEBUG,
    connect_args=connect_args,
    pool_pre_ping=True
)

AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autocommit=False,
    autoflush=False
)

Base = declarative_base()


async def get_db():
    """FastAPI dependency for yielding database session with automatic closure."""
    async with AsyncSessionLocal() as session:
        try:
            yield session
        finally:
            await session.close()


async def init_db():
    """Create all tables asynchronously at application startup."""
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
