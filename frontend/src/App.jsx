import React, { useState, useEffect } from "react";
import Navbar from "./components/Navbar";
import TelemetryBanner from "./components/TelemetryBanner";
import ArchitectureExplorer from "./components/ArchitectureExplorer";
import LiveCallStreamer from "./components/LiveCallStreamer";
import FileAnalyzer from "./components/FileAnalyzer";
import CallHistory from "./components/CallHistory";
import StepUpModal from "./components/StepUpModal";
import AdminPanel from "./components/AdminPanel";
import MobileSimulator from "./components/MobileSimulator";
import PrivacyModal from "./components/PrivacyModal";
import AuthModal from "./components/AuthModal";
import { MicIcon, UploadIcon, HistoryIcon, SmartphoneIcon, SettingsIcon, ShieldIcon } from "./components/Icons";

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem("voxshield_token") || "");
  const [currentUser, setCurrentUser] = useState(() => {
    const saved = localStorage.getItem("voxshield_user");
    return saved ? JSON.parse(saved) : null;
  });

  const [activeTab, setActiveTab] = useState("live");
  const [stepUpSessionId, setStepUpSessionId] = useState(null);
  const [isPrivacyOpen, setIsPrivacyOpen] = useState(false);
  const [isAuthOpen, setIsAuthOpen] = useState(false);
  const [currentThresholdHigh, setCurrentThresholdHigh] = useState(70.0);
  const [theme, setTheme] = useState(() => localStorage.getItem("voxshield_theme") || "light");

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("voxshield_theme", theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme((prev) => (prev === "light" ? "dark" : "light"));
  };

  const isTokenExpired = (tok) => {
    if (!tok) return true;
    try {
      const parts = tok.split(".");
      if (parts.length !== 3) return true;
      const payload = JSON.parse(atob(parts[1]));
      if (!payload.exp) return false;
      return payload.exp * 1000 < Date.now();
    } catch {
      return true;
    }
  };

  // Auto-authenticate default demo user if no token present or if token is expired
  useEffect(() => {
    if (!token || isTokenExpired(token)) {
      handleQuickDemoLogin();
    }
  }, [token]);

  const handleQuickDemoLogin = async () => {
    try {
      // Clear stale token
      localStorage.removeItem("voxshield_token");
      // Register or login default demo admin
      const res = await fetch("http://localhost:8000/api/v1/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: "admin_demo", password: "SecurePassword123!" })
      });

      if (res.ok) {
        const data = await res.json();
        handleAuthSuccess(data.access_token, data.user);
      } else {
        // First-time setup: register demo admin
        const regRes = await fetch("http://localhost:8000/api/v1/auth/register", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            username: "admin_demo",
            email: "admin@voxshield.ai",
            password: "SecurePassword123!"
          })
        });
        if (regRes.ok) {
          const loginRes = await fetch("http://localhost:8000/api/v1/auth/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username: "admin_demo", password: "SecurePassword123!" })
          });
          const loginData = await loginRes.json();
          handleAuthSuccess(loginData.access_token, loginData.user);
        }
      }
    } catch (e) {
      console.warn("Backend offline or booting:", e);
    }
  };

  const handleAuthSuccess = (newToken, user) => {
    setToken(newToken);
    setCurrentUser(user);
    localStorage.setItem("voxshield_token", newToken);
    localStorage.setItem("voxshield_user", JSON.stringify(user));
  };

  const handleLogout = () => {
    setToken("");
    setCurrentUser(null);
    localStorage.removeItem("voxshield_token");
    localStorage.removeItem("voxshield_user");
    setActiveTab("live");
  };

  return (
    <div className="app-container">
      <div className="ambient-glow" />

      {/* Top Header & Security Badges */}
      <Navbar
        currentUser={currentUser}
        onLogout={handleLogout}
        onOpenAuth={() => setIsAuthOpen(true)}
        onOpenPrivacy={() => setIsPrivacyOpen(true)}
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        theme={theme}
        toggleTheme={toggleTheme}
      />

      {/* Live Threat Telemetry Strip */}
      <TelemetryBanner onSelectTab={(tab) => setActiveTab(tab)} />

      {/* Main Navigation Tabs */}
      <nav className="tabs-bar">
        <button
          className={`tab-btn ${activeTab === "live" ? "active" : ""}`}
          onClick={() => setActiveTab("live")}
        >
          <MicIcon size={15} /> Live Call Protection
        </button>
        <button
          className={`tab-btn ${activeTab === "upload" ? "active" : ""}`}
          onClick={() => setActiveTab("upload")}
        >
          <UploadIcon size={15} /> Audio File Inspector
        </button>
        <button
          className={`tab-btn ${activeTab === "mobile" ? "active" : ""}`}
          onClick={() => setActiveTab("mobile")}
        >
          <SmartphoneIcon size={15} /> Mobile Simulator
        </button>
        <button
          className={`tab-btn ${activeTab === "architecture" ? "active" : ""}`}
          onClick={() => setActiveTab("architecture")}
        >
          <ShieldIcon size={15} /> Threat & Architecture
        </button>
        <button
          className={`tab-btn ${activeTab === "history" ? "active" : ""}`}
          onClick={() => setActiveTab("history")}
        >
          <HistoryIcon size={15} /> Audit History
        </button>
        <button
          className={`tab-btn ${activeTab === "admin" ? "active" : ""}`}
          onClick={() => setActiveTab("admin")}
        >
          <SettingsIcon size={15} /> Admin Console
        </button>
      </nav>

      {/* Content Area */}
      <main>
        {activeTab === "live" && (
          <div className="dashboard-grid">
            <LiveCallStreamer
              token={token}
              onTriggerStepUp={(sessId) => setStepUpSessionId(sessId)}
              currentThresholdHigh={currentThresholdHigh}
            />
            <FileAnalyzer
              token={token}
              onTriggerStepUp={(sessId) => setStepUpSessionId(sessId)}
              onTokenExpired={handleQuickDemoLogin}
            />
          </div>
        )}

        {activeTab === "upload" && (
          <div style={{ maxWidth: "840px", margin: "0 auto" }}>
            <FileAnalyzer
              token={token}
              onTriggerStepUp={(sessId) => setStepUpSessionId(sessId)}
              onTokenExpired={handleQuickDemoLogin}
            />
          </div>
        )}

        {activeTab === "mobile" && (
          <MobileSimulator
            token={token}
            onTriggerStepUp={(sessId) => setStepUpSessionId(sessId)}
          />
        )}

        {activeTab === "architecture" && (
          <ArchitectureExplorer onLaunchSimulator={() => setActiveTab("mobile")} />
        )}

        {activeTab === "history" && (
          <CallHistory token={token} />
        )}

        {activeTab === "admin" && (
          <AdminPanel token={token} currentUser={currentUser} />
        )}
      </main>

      {/* Step-Up Transaction Security Modal */}
      {stepUpSessionId && (
        <StepUpModal
          sessionId={stepUpSessionId}
          token={token}
          onClose={() => setStepUpSessionId(null)}
          onVerified={() => {
            alert("Step-Up verification recorded. Risk mitigation completed.");
          }}
        />
      )}

      {/* Privacy by Design Modal */}
      {isPrivacyOpen && (
        <PrivacyModal onClose={() => setIsPrivacyOpen(false)} />
      )}

      {/* Authentication Modal */}
      {isAuthOpen && (
        <AuthModal
          onClose={() => setIsAuthOpen(false)}
          onAuthSuccess={handleAuthSuccess}
        />
      )}
    </div>
  );
}
