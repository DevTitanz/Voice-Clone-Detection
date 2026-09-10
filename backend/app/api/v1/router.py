from fastapi import APIRouter
from app.api.v1.auth import router as auth_router
from app.api.v1.detect import router as detect_router
from app.api.v1.admin import router as admin_router
from app.api.v1.websocket import router as ws_router

api_v1_router = APIRouter()

api_v1_router.include_router(auth_router)
api_v1_router.include_router(detect_router)
api_v1_router.include_router(admin_router)
api_v1_router.include_router(ws_router)
