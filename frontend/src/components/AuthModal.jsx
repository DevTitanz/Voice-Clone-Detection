import React, { useState } from "react";
import { ShieldIcon, ZapIcon, XIcon, AlertTriangleIcon } from "./Icons";

export default function AuthModal({ onClose, onAuthSuccess }) {
  const [isLogin, setIsLogin] = useState(true);
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState(null);

  const fillAdminPreset = () => {
    setUsername("admin_demo");
    setEmail("admin@voiceguard.ai");
    setPassword("SecurePassword123!");
    setErrorMsg(null);
  };

  const fillUserPreset = () => {
    setUsername("officer_sharma");
    setEmail("sharma@banksecure.in");
    setPassword("SecurePassword123!");
    setErrorMsg(null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setErrorMsg(null);

    const endpoint = isLogin ? "/api/v1/auth/login" : "/api/v1/auth/register";
    const body = isLogin
      ? { username, password }
      : { username, email, password };

    try {
      const response = await fetch(`http://localhost:8000${endpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      });

      const data = await response.json();
      if (!response.ok) {
        throw new Error(data.detail || "Authentication request rejected.");
      }

      if (isLogin) {
        onAuthSuccess(data.access_token, data.user);
        onClose();
      } else {
        // Automatically login after registering
        const loginRes = await fetch("http://localhost:8000/api/v1/auth/login", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ username, password })
        });
        const loginData = await loginRes.json();
        onAuthSuccess(loginData.access_token, loginData.user);
        onClose();
      }
    } catch (err) {
      setErrorMsg(err.message || "Failed to authenticate.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <span style={{ color: "var(--accent-cyan)", display: "flex", alignItems: "center" }}>
              <ShieldIcon size={20} />
            </span>
            <h3 style={{ fontSize: "18px", fontWeight: 700 }}>
              {isLogin ? "Sign In to VoiceGuard" : "Create Protected Account"}
            </h3>
          </div>
          <button className="btn btn-outline" style={{ padding: "6px", display: "inline-flex", alignItems: "center", justifyContent: "center" }} onClick={onClose} aria-label="Close">
            <XIcon size={14} />
          </button>
        </div>

        {/* Quick-fill preset buttons */}
        <div style={{ display: "flex", gap: "8px", marginBottom: "16px" }}>
          <button
            type="button"
            className="btn btn-outline"
            style={{ flex: 1, fontSize: "11px", padding: "6px 8px", display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "6px" }}
            onClick={fillAdminPreset}
          >
            <ZapIcon size={12} />
            <span>Fill Demo Admin</span>
          </button>
          <button
            type="button"
            className="btn btn-outline"
            style={{ flex: 1, fontSize: "11px", padding: "6px 8px", display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "6px" }}
            onClick={fillUserPreset}
          >
            <ZapIcon size={12} />
            <span>Fill Demo User</span>
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: "12px" }}>
            <label style={{ display: "block", fontSize: "12px", color: "var(--text-muted)", marginBottom: "4px" }}>
              Username
            </label>
            <input
              type="text"
              required
              style={{
                width: "100%",
                padding: "10px",
                borderRadius: "8px",
                background: "var(--bg-subtle)",
                border: "1px solid var(--border-color)",
                color: "var(--text-primary)"
              }}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>

          {!isLogin && (
            <div style={{ marginBottom: "12px" }}>
              <label style={{ display: "block", fontSize: "12px", color: "var(--text-muted)", marginBottom: "4px" }}>
                Email Address
              </label>
              <input
                type="email"
                required
                style={{
                  width: "100%",
                  padding: "10px",
                  borderRadius: "8px",
                  background: "var(--bg-subtle)",
                  border: "1px solid var(--border-color)",
                  color: "var(--text-primary)"
                }}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
          )}

          <div style={{ marginBottom: "16px" }}>
            <label style={{ display: "block", fontSize: "12px", color: "var(--text-muted)", marginBottom: "4px" }}>
              Password (Min 8 chars, 1 uppercase, 1 lowercase, 1 digit)
            </label>
            <input
              type="password"
              required
              style={{
                width: "100%",
                padding: "10px",
                borderRadius: "8px",
                background: "var(--bg-subtle)",
                border: "1px solid var(--border-color)",
                color: "var(--text-primary)"
              }}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          {errorMsg && (
            <div style={{ padding: "10px", borderRadius: "8px", background: "rgba(239, 68, 68, 0.15)", color: "#f87171", fontSize: "13px", marginBottom: "14px", display: "flex", alignItems: "center", gap: "8px" }}>
              <AlertTriangleIcon size={16} />
              <span>{errorMsg}</span>
            </div>
          )}

          <button type="submit" className="btn btn-cyan btn-block" disabled={loading}>
            {loading ? "Verifying..." : isLogin ? "Authenticate & Enter" : "Register Protected Account"}
          </button>
        </form>

        <div style={{ marginTop: "16px", textAlign: "center", fontSize: "13px", color: "var(--text-muted)" }}>
          {isLogin ? "Need a new account? " : "Already have an account? "}
          <button
            type="button"
            style={{ background: "none", border: "none", color: "var(--accent-cyan)", cursor: "pointer", fontWeight: 600 }}
            onClick={() => { setIsLogin(!isLogin); setErrorMsg(null); }}
          >
            {isLogin ? "Register here" : "Sign In"}
          </button>
        </div>
      </div>
    </div>
  );
}
