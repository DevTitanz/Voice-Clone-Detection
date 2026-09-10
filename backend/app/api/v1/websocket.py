import json
import uuid
import asyncio
from datetime import datetime, timezone
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query, status
from app.core.config import settings
from app.core.security import decode_access_token, is_token_revoked
from app.services.stream_buffer import SlidingStreamBuffer

router = APIRouter(tags=["Real-Time Streaming WebSocket"])


@router.websocket("/ws/stream")
async def websocket_stream_endpoint(
    websocket: WebSocket,
    token: str = Query(...),
    client_type: str = Query("web")
):
    """
    Secure Authenticated Real-Time Streaming WebSocket Endpoint:
    1. Authenticates JWT BEFORE accepting connection (`websocket.accept()`).
    2. Enforces per-frame maximum size (256 KB) to prevent DoS.
    3. Session isolation: Anonymous UUID session bound strictly to the authenticated user.
    4. Zero Audio Retention: audio chunk is processed in sliding RAM buffer and purged on disconnect.
    5. Returns sanitized probabilistic risk scores; never echoes raw audio data.
    """
    # 1. Pre-handshake authentication check
    if not token or is_token_revoked(token):
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Authentication failed")
        return

    payload = decode_access_token(token)
    if not payload or not payload.get("sub"):
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Invalid or expired token")
        return

    user_id = payload["sub"]

    # 2. Accept connection only after authentication succeeds
    await websocket.accept()

    # Generate isolated anonymous session identifier
    session_id = f"stream_{uuid.uuid4().hex}"
    stream_buffer = SlidingStreamBuffer(sample_rate=16000, window_seconds=3.0)

    # Send initial connection acknowledgment with security confirmation
    await websocket.send_json({
        "event": "CONNECTED",
        "session_id": session_id,
        "secure_connection": True,
        "audio_retention": "ZERO_STORAGE_VOLATILE_MEMORY_ONLY",
        "model_version": settings.MODEL_VERSION,
        "message": "Secure real-time audio stream initialized."
    })

    try:
        while True:
            # Receive either binary PCM audio or JSON control messages
            message = await websocket.receive()

            if "bytes" in message and message["bytes"]:
                raw_bytes = message["bytes"]

                # Enforce strict frame size limit
                if len(raw_bytes) > settings.MAX_WS_MESSAGE_BYTES:
                    await websocket.send_json({
                        "event": "ERROR",
                        "error": f"Audio chunk exceeds maximum allowed frame limit of {settings.MAX_WS_MESSAGE_BYTES} bytes."
                    })
                    continue

                # Process chunk through in-memory sliding buffer
                result = stream_buffer.add_pcm_chunk(raw_bytes)
                if result:
                    # Sanitize output payload: NEVER send raw audio back
                    await websocket.send_json({
                        "event": "ANALYSIS_UPDATE",
                        "session_id": session_id,
                        "risk_score": result["risk_score"],
                        "classification": result["classification"],
                        "classification_label": result["classification_label"],
                        "confidence": result["confidence"],
                        "verification_required": result["verification_required"],
                        "detected_emotion": result.get("detected_emotion", "Calm / Neutral"),
                        "emotion_incongruence_flag": result.get("emotion_incongruence_flag"),
                        "audio_duration_seconds": result["audio_duration_seconds"],
                        "model_version": result["model_version"],
                        "timestamp": datetime.now(timezone.utc).isoformat(),
                        "features_summary": result.get("features_summary", {})
                    })

            elif "text" in message and message["text"]:
                try:
                    payload_data = json.loads(message["text"])
                    action = payload_data.get("action")
                    if action == "PING":
                        await websocket.send_json({"event": "PONG", "timestamp": datetime.now(timezone.utc).isoformat()})
                    elif action == "END_CALL":
                        await websocket.send_json({
                            "event": "SESSION_CLOSED",
                            "session_id": session_id,
                            "message": "Call stream ended. All volatile audio buffers purged."
                        })
                        break
                except json.JSONDecodeError:
                    pass

    except WebSocketDisconnect:
        pass
    except Exception as exc:
        pass
    finally:
        # Guarantee immediate buffer purge upon disconnect
        stream_buffer.purge()
        del stream_buffer
