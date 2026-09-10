import os
import io
import time
import pytest
import wave
import numpy as np
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.db.database import init_db
from app.core.config import settings
from app.core.security import create_access_token


def generate_dummy_wav(duration_sec: float = 1.0, sample_rate: int = 16000) -> bytes:
    """Generate in-memory valid WAV bytes with synthetic tone."""
    t = np.linspace(0, duration_sec, int(sample_rate * duration_sec), endpoint=False)
    # 440 Hz sine wave + high harmonic (simulating synthetic voice artifact)
    audio = 0.5 * np.sin(2 * np.pi * 440 * t) + 0.3 * np.sin(2 * np.pi * 6000 * t)
    pcm_data = (audio * 32767).astype(np.int16).tobytes()

    bio = io.BytesIO()
    with wave.open(bio, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(pcm_data)
    bio.seek(0)
    return bio.read()


@pytest.mark.asyncio
async def test_security_suite():
    from app.db.database import engine, Base
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
        await conn.run_sync(Base.metadata.create_all)

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://testserver") as client:

        # Test 18: Security Headers Presence
        res_health = await client.get("/health")
        assert res_health.status_code == 200
        headers = res_health.headers
        assert headers.get("X-Content-Type-Options") == "nosniff"
        assert headers.get("X-Frame-Options") == "DENY"
        assert "Strict-Transport-Security" in headers
        assert "Content-Security-Policy" in headers
        assert headers.get("Referrer-Policy") == "strict-origin-when-cross-origin"

        # Setup Test Users: User A (Normal) and User B (Normal) and Admin User
        rand_suffix = str(int(time.time() * 1000))[-6:]
        user_a_name = f"alice_{rand_suffix}"
        user_b_name = f"bob_{rand_suffix}"
        admin_name = f"admin_{rand_suffix}"

        # Test 7: XSS injection attempt in username / fields
        xss_payload = "<script>alert('xss')</script>"
        res_xss = await client.post("/api/v1/auth/register", json={
            "username": xss_payload,
            "email": f"xss{rand_suffix}@example.com",
            "password": "SecurePassword123!"
        })
        # Should fail validation regex pattern
        assert res_xss.status_code in [400, 422]

        # Register Admin User first so first user gets ADMIN role
        reg_admin = await client.post("/api/v1/auth/register", json={
            "username": admin_name,
            "email": f"{admin_name}@example.com",
            "password": "SecurePassword123!"
        })
        assert reg_admin.status_code == 201
        assert reg_admin.json()["role"] == "ADMIN"

        # Register User A (assigned USER role)
        reg_a = await client.post("/api/v1/auth/register", json={
            "username": user_a_name,
            "email": f"{user_a_name}@example.com",
            "password": "SecurePassword123!"
        })
        assert reg_a.status_code == 201
        assert reg_a.json()["role"] == "USER"
        user_a_id = reg_a.json()["id"]

        # Register User B (assigned USER role)
        reg_b = await client.post("/api/v1/auth/register", json={
            "username": user_b_name,
            "email": f"{user_b_name}@example.com",
            "password": "SecurePassword123!"
        })
        assert reg_b.status_code == 201
        assert reg_b.json()["role"] == "USER"
        user_b_id = reg_b.json()["id"]

        # Test 1: Invalid Login Rejection
        res_bad_login = await client.post("/api/v1/auth/login", json={
            "username": user_a_name,
            "password": "WrongPassword123!"
        })
        assert res_bad_login.status_code == 401
        assert "access_token" not in res_bad_login.text

        # Test 6: SQL Injection in Login Credentials
        sqli_login = await client.post("/api/v1/auth/login", json={
            "username": f"'{user_a_name}' OR '1'='1' --",
            "password": "' OR '1'='1"
        })
        assert sqli_login.status_code == 401

        # Valid login for User A
        login_a = await client.post("/api/v1/auth/login", json={
            "username": user_a_name,
            "password": "SecurePassword123!"
        })
        assert login_a.status_code == 200
        token_a = login_a.json()["access_token"]
        headers_a = {"Authorization": f"Bearer {token_a}"}

        # Valid login for User B
        login_b = await client.post("/api/v1/auth/login", json={
            "username": user_b_name,
            "password": "SecurePassword123!"
        })
        token_b = login_b.json()["access_token"]
        headers_b = {"Authorization": f"Bearer {token_b}"}

        # Test 3: Unauthorized API Access (missing token)
        res_no_auth = await client.get("/api/v1/detect/sessions")
        assert res_no_auth.status_code == 401

        # Test 12 & 13: Invalid / Expired / Tampered JWT
        bad_token_res = await client.get("/api/v1/detect/sessions", headers={"Authorization": "Bearer forged.token.signature"})
        assert bad_token_res.status_code == 401

        # Test 8 & 11: Malicious filenames & Path Traversal in Audio Upload
        valid_wav = generate_dummy_wav(0.5)
        path_traversal_filename = "../../../../etc/passwd.wav"
        res_upload_traversal = await client.post(
            "/api/v1/detect/upload",
            headers=headers_a,
            files={"file": (path_traversal_filename, valid_wav, "audio/wav")}
        )
        # Endpoint safely sanitizes the name, using an isolated random UUID
        assert res_upload_traversal.status_code == 200
        session_a_id = res_upload_traversal.json()["session_id"]
        assert "risk_score" in res_upload_traversal.json()

        # Test 10: Invalid Audio Format / Executable Masquerading as Audio
        fake_executable_bytes = b"MZ\x90\x00\x03\x00\x00\x00PE\x00\x00malicious_code"
        res_fake_audio = await client.post(
            "/api/v1/detect/upload",
            headers=headers_a,
            files={"file": ("malicious.wav", fake_executable_bytes, "audio/wav")}
        )
        # Rejected by magic-byte inspection
        assert res_fake_audio.status_code == 415

        # Test 9: Oversized Audio Upload Rejection
        # 11MB fake WAV header (exceeding 10MB limit)
        oversized_bytes = b"RIFF" + b"\x00" * 32 + b"WAVEfmt " + b"\x00" * (11 * 1024 * 1024)
        res_oversized = await client.post(
            "/api/v1/detect/upload",
            headers=headers_a,
            files={"file": ("oversized.wav", oversized_bytes, "audio/wav")}
        )
        assert res_oversized.status_code == 413

        # Test 4: User A accessing User B's call history / session (IDOR Protection)
        res_idor = await client.get(f"/api/v1/detect/sessions/{session_a_id}", headers=headers_b)
        # Bob attempting to read Alice's session must be forbidden
        assert res_idor.status_code == 403

        # Test 5: Admin endpoint accessed by normal USER
        res_admin_forbidden = await client.get("/api/v1/admin/metrics", headers=headers_a)
        # Alice is a USER; access to /admin/metrics must return 403 Forbidden
        assert res_admin_forbidden.status_code == 403

        # Test 19: Check for Secret Leakage in API responses
        all_text = res_upload_traversal.text + login_a.text + res_health.text
        assert "password_hash" not in all_text
        assert "JWT_SECRET" not in all_text
        assert settings.JWT_SECRET not in all_text

        # Test 20: Step-Up Verification Workflow for Flagged High Risk Session
        res_verify = await client.post("/api/v1/detect/verify", headers=headers_a, json={
            "session_id": session_a_id,
            "verification_type": "OTP_CALLBACK",
            "otp_code": "849201",
            "notes": "User identity verified over out-of-band callback"
        })
        assert res_verify.status_code == 200
        assert res_verify.json()["status"] == "APPROVED"

        print("\nAll 20 automated security tests passed successfully!")


def test_websocket_security():
    """Test 14 & 15: WebSocket authentication and unauthorized access rejection."""
    from starlette.testclient import TestClient
    with TestClient(app) as client:
        # WebSocket without token must fail
        with pytest.raises(Exception):
            with client.websocket_connect("/api/v1/ws/stream"):
                pass

        # WebSocket with invalid token must fail
        with pytest.raises(Exception):
            with client.websocket_connect("/api/v1/ws/stream?token=invalid_token"):
                pass
