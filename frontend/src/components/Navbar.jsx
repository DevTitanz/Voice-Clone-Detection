import React, { useState, useEffect } from "react";
import { ShieldIcon, LockIcon, SmartphoneIcon } from "./Icons";

export default function Navbar({
  currentUser,
  onLogout,
  onOpenAuth,
  onOpenPrivacy,
  activeTab,
  setActiveTab,
  theme,
  toggleTheme
}) {
  const [apiStatus, setApiStatus] = useState("checking"); // 'online' | 'offline' | 'checking'

  useEffect(() => {
    let isMounted = true;
    const checkHealth = async () => {
      try {
        const res = await fetch("http://localhost:8000/api/v1/health", { method: "GET" });
        if (res.ok && isMounted) {
          setApiStatus("online");
        } else if (isMounted) {
          setApiStatus("offline");
        }
      } catch {
        if (isMounted) setApiStatus("offline");
      }
    };

    checkHealth();
    const interval = setInterval(checkHealth, 12000);
    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, []);

  return (
    <header className="navbar">
      <div className="brand-group">
        <div className="brand-icon">
          <ShieldIcon size={22} />
        </div>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <h1 className="brand-title">VoxShield AI</h1>
            <span
              style={{
                fontSize: "10px",
                fontWeight: 700,
                background: "var(--accent-blue-bg)",
                color: "var(--accent-blue)",
                border: "1px solid var(--accent-blue-border)",
                padding: "2px 6px",
                borderRadius: "4px"
              }}
            >
              PROD v2.0
            </span>
          </div>
          <p className="brand-subtitle">SIH26104 • Dual-Surface Real-Time Voice Cloning Defense</p>
        </div>
      </div>

      <div className="security-indicators-row">
        {/* Real-Time API Health Status */}
        <span
          className="badge"
          style={{
            background: apiStatus === "online" ? "var(--accent-emerald-bg)" : "var(--accent-amber-bg)",
            color: apiStatus === "online" ? "var(--accent-emerald)" : "var(--accent-amber)",
            borderColor: apiStatus === "online" ? "var(--accent-emerald-border)" : "var(--accent-amber-border)"
          }}
          title={apiStatus === "online" ? "FastAPI Gateway Online on localhost:8000" : "FastAPI Gateway Standby"}
        >
          <span className={`beacon-pulse ${apiStatus === "online" ? "" : "amber"}`} />
          {apiStatus === "online" ? "Gateway Online (8000)" : "Gateway Connecting..."}
        </span>

        {/* Direct APK Download Link */}
        <a
          href="/VoxShield-AI-debug.apk"
          download="VoxShield-AI-debug.apk"
          className="badge badge-emerald"
          style={{ cursor: "pointer", textDecoration: "none", fontWeight: 700 }}
          title="Download Android Mobile App APK"
        >
          <SmartphoneIcon size={12} />
          Android APK
        </a>

        <span className="badge badge-purple" title="Zero audio persistence guarantee">
          <LockIcon size={12} /> Zero Storage
        </span>

        <button
          className="badge"
          style={{ cursor: "pointer", background: "var(--bg-subtle)", color: "var(--text-primary)" }}
          onClick={toggleTheme}
          title="Toggle Dark/Light Mode"
        >
          {theme === "dark" ? "☀️ Light" : "🌙 Dark"}
        </button>

        <button
          className="badge badge-cyan"
          style={{ cursor: "pointer" }}
          onClick={onOpenPrivacy}
          title="View Data Minimization & Privacy Architecture"
        >
          Privacy
        </button>

        {currentUser ? (
          <div style={{ display: "flex", alignItems: "center", gap: "8px", marginLeft: "4px" }}>
            <span
              className="badge"
              style={{
                background: currentUser.role === "ADMIN" ? "var(--accent-amber-bg)" : "var(--accent-blue-bg)",
                color: currentUser.role === "ADMIN" ? "var(--accent-amber)" : "var(--accent-blue)",
                borderColor: currentUser.role === "ADMIN" ? "var(--accent-amber-border)" : "var(--accent-blue-border)"
              }}
            >
              {currentUser.username} ({currentUser.role})
            </span>
            <button className="btn btn-outline" style={{ padding: "5px 10px", fontSize: "12px" }} onClick={onLogout}>
              Sign Out
            </button>
          </div>
        ) : (
          <button className="btn btn-cyan" style={{ padding: "6px 14px", fontSize: "12px" }} onClick={onOpenAuth}>
            Sign In
          </button>
        )}
      </div>
    </header>
  );
}

