import React from "react";
import { ShieldIcon, LockIcon } from "./Icons";

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
  return (
    <header className="navbar">
      <div className="brand-group">
        <div className="brand-icon">
          <ShieldIcon size={22} />
        </div>
        <div>
          <h1 className="brand-title">VoxShield AI</h1>
          <p className="brand-subtitle">SIH26104 • Synthetic Voice Detection System</p>
        </div>
      </div>

      <div className="security-indicators-row">
        <span className="badge badge-emerald" title="Enforced TLS/WSS on all communication">
          <ShieldIcon size={13} /> TLS / WSS Enforced
        </span>
        <span className="badge badge-purple" title="Zero audio persistence guarantee">
          <LockIcon size={13} /> Zero Audio Storage
        </span>
        <button
          className="badge"
          style={{ cursor: "pointer", background: "var(--bg-subtle)", color: "var(--text-primary)" }}
          onClick={toggleTheme}
          title="Toggle Color Mode"
        >
          {theme === "dark" ? "Light Theme" : "Dark Theme"}
        </button>
        <button
          className="badge badge-cyan"
          style={{ cursor: "pointer" }}
          onClick={onOpenPrivacy}
          title="View Data Minimization & Privacy Architecture"
        >
          Privacy Policy
        </button>

        {currentUser ? (
          <div style={{ display: "flex", alignItems: "center", gap: "10px", marginLeft: "8px" }}>
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
            <button className="btn btn-outline" style={{ padding: "6px 12px", fontSize: "12px" }} onClick={onLogout}>
              Sign Out
            </button>
          </div>
        ) : (
          <button className="btn btn-cyan" style={{ padding: "6px 16px", fontSize: "13px" }} onClick={onOpenAuth}>
            Sign In / Register
          </button>
        )}
      </div>
    </header>
  );
}
