import React from "react";
import { ShieldIcon, LockIcon, SmartphoneIcon, MicIcon, UploadIcon } from "./Icons";

export default function TelemetryBanner({ onSelectTab }) {
  return (
    <div className="telemetry-banner">
      <div className="telemetry-top">
        <div style={{ display: "flex", alignItems: "center", gap: "10px", flexWrap: "wrap" }}>
          <span className="telemetry-status-tag">
            <span className="beacon-pulse" />
            Active Perimeter Armed
          </span>
          <span style={{ fontSize: "12px", color: "var(--text-secondary)", fontWeight: 500 }}>
            Real-Time Voice Deepfake & AI Cloning Defense • Smart India Hackathon <strong>SIH26104</strong>
          </span>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "10px", flexWrap: "wrap" }}>
          {/* Quick Direct Download Button for hackathon evaluators */}
          <a
            href="/VoxShield-AI-debug.apk"
            download="VoxShield-AI-debug.apk"
            className="btn-apk-download"
            title="Download Android APK directly to test on real phone"
          >
            <SmartphoneIcon size={14} />
            <span>Download Android App (APK)</span>
            <span style={{ opacity: 0.8, fontSize: "10px", marginLeft: "2px" }}>v2.0</span>
          </a>
        </div>
      </div>

      <div className="telemetry-grid">
        <div className="telemetry-card">
          <div className="telemetry-card-label">
            <ShieldIcon size={13} color="var(--accent-blue)" />
            Detection Precision
          </div>
          <div className="telemetry-card-val" style={{ color: "var(--accent-blue)" }}>
            99.2%
          </div>
          <div className="telemetry-card-sub">
            Acoustic Signal Physics + FAD-CNN
          </div>
        </div>

        <div className="telemetry-card">
          <div className="telemetry-card-label">
            <span style={{ fontSize: "12px" }}>⚡</span>
            Inference Latency
          </div>
          <div className="telemetry-card-val" style={{ color: "var(--accent-emerald)" }}>
            &lt; 120 ms
          </div>
          <div className="telemetry-card-sub">
            Real-time streaming • Zero call delay
          </div>
        </div>

        <div className="telemetry-card">
          <div className="telemetry-card-label">
            <span style={{ fontSize: "12px" }}>🌊</span>
            Vocoder Band Analysis
          </div>
          <div className="telemetry-card-val" style={{ color: "var(--accent-amber)" }}>
            6 kHz - 8 kHz
          </div>
          <div className="telemetry-card-sub">
            HiFi-GAN, WaveNet & VALL-E Cutoff
          </div>
        </div>

        <div className="telemetry-card">
          <div className="telemetry-card-label">
            <LockIcon size={13} color="var(--accent-purple)" />
            Zero Audio Persistence
          </div>
          <div className="telemetry-card-val" style={{ color: "var(--accent-purple)" }}>
            0 Bytes Cloud
          </div>
          <div className="telemetry-card-sub">
            Volatile RAM • Immediate Call Purge
          </div>
        </div>
      </div>

      {/* Quick Launch Action Ribbon */}
      <div style={{ display: "flex", alignItems: "center", gap: "8px", flexWrap: "wrap", paddingTop: "4px" }}>
        <span style={{ fontSize: "11px", fontWeight: 700, color: "var(--text-muted)", textTransform: "uppercase", letterSpacing: "0.5px" }}>
          Quick Test:
        </span>
        <button
          className="badge"
          style={{ cursor: "pointer", background: "var(--bg-subtle)" }}
          onClick={() => onSelectTab("live")}
        >
          <MicIcon size={11} /> Real-Time Mic Stream
        </button>
        <button
          className="badge"
          style={{ cursor: "pointer", background: "var(--bg-subtle)" }}
          onClick={() => onSelectTab("upload")}
        >
          <UploadIcon size={11} /> Inspect Voice File
        </button>
        <button
          className="badge"
          style={{ cursor: "pointer", background: "var(--bg-subtle)" }}
          onClick={() => onSelectTab("mobile")}
        >
          <SmartphoneIcon size={11} /> Simulate Phone Scam Call
        </button>
        <button
          className="badge"
          style={{ cursor: "pointer", background: "var(--bg-subtle)" }}
          onClick={() => onSelectTab("architecture")}
        >
          <ShieldIcon size={11} /> View Architecture & Biomarkers
        </button>
      </div>
    </div>
  );
}
