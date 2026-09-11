import React from "react";
import { ShieldIcon, MicIcon, UploadIcon, SmartphoneIcon, HistoryIcon, SettingsIcon } from "./Icons";

export default function Navbar({ activeTab, setActiveTab }) {
  return (
    <header className="navbar">
      <div className="brand-group">
        <div className="brand-icon">
          <ShieldIcon size={20} />
        </div>
        <div>
          <h1 className="brand-title">VoxShield</h1>
          <p className="brand-subtitle">Voice Clone & Deepfake Defense</p>
        </div>
      </div>

      <nav className="header-nav">
        <button
          className={`nav-link ${activeTab === "live" ? "active" : ""}`}
          onClick={() => setActiveTab("live")}
        >
          <MicIcon size={14} /> Live Call
        </button>
        <button
          className={`nav-link ${activeTab === "upload" ? "active" : ""}`}
          onClick={() => setActiveTab("upload")}
        >
          <UploadIcon size={14} /> File Inspector
        </button>
        <button
          className={`nav-link ${activeTab === "mobile" ? "active" : ""}`}
          onClick={() => setActiveTab("mobile")}
        >
          <SmartphoneIcon size={14} /> Mobile Simulator
        </button>
        <button
          className={`nav-link ${activeTab === "history" ? "active" : ""}`}
          onClick={() => setActiveTab("history")}
        >
          <HistoryIcon size={14} /> History
        </button>
        <button
          className={`nav-link ${activeTab === "admin" ? "active" : ""}`}
          onClick={() => setActiveTab("admin")}
        >
          <SettingsIcon size={14} /> Settings
        </button>
      </nav>
    </header>
  );
}
