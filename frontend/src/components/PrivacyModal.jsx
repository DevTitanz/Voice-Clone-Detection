import React from "react";
import { LockIcon, XIcon } from "./Icons";

export default function PrivacyModal({ onClose }) {
  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ maxWidth: "600px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <span style={{ color: "var(--accent-emerald)", display: "flex", alignItems: "center" }}>
              <LockIcon size={20} />
            </span>
            <h3 style={{ fontSize: "18px", fontWeight: 700, color: "var(--text-primary)" }}>
              Privacy by Design & Data Minimization Policy
            </h3>
          </div>
          <button className="btn btn-outline" style={{ padding: "6px", display: "inline-flex", alignItems: "center", justifyContent: "center" }} onClick={onClose} aria-label="Close">
            <XIcon size={14} />
          </button>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "14px", fontSize: "13px", color: "var(--text-secondary)", maxHeight: "400px", overflowY: "auto", paddingRight: "8px" }}>
          <div style={{ padding: "12px", background: "rgba(16, 185, 129, 0.1)", borderRadius: "8px", border: "1px solid rgba(16, 185, 129, 0.3)" }}>
            <strong style={{ color: "#34d399" }}>Zero Raw Audio Storage Guarantee:</strong>
            <p style={{ marginTop: "4px", color: "#e2e8f0" }}>
              Voice recordings are processed exclusively in volatile memory (RAM) through an ephemeral sliding window buffer. Raw voice samples, audio waveforms, or caller recordings are NEVER written to flash/disk or saved in any database.
            </p>
          </div>

          <div>
            <strong style={{ color: "#ffffff" }}>1. Data Minimization (What We Store):</strong>
            <ul style={{ paddingLeft: "20px", marginTop: "4px" }}>
              <li>Anonymous Session UUIDs (e.g. <code>sess_a81f09b...</code>)</li>
              <li>Calculated synthetic voice risk score (0.0–100.0%)</li>
              <li>Probabilistic classification & technical feature summaries</li>
              <li>Call duration in seconds & AI model version</li>
              <li>Timestamp of inference</li>
            </ul>
          </div>

          <div>
            <strong style={{ color: "#ffffff" }}>2. What We Explicitly Avoid Collecting:</strong>
            <ul style={{ paddingLeft: "20px", marginTop: "4px" }}>
              <li>No caller or callee phone numbers</li>
              <li>No contact lists or address books</li>
              <li>No persistent raw audio recordings or MP3/WAV files</li>
              <li>No unnecessary device hardware identifiers or GPS location</li>
            </ul>
          </div>

          <div>
            <strong style={{ color: "#ffffff" }}>3. Cryptographic Security Safeguards:</strong>
            <ul style={{ paddingLeft: "20px", marginTop: "4px" }}>
              <li>Enforced HTTPS & WSS (Strict Transport Security)</li>
              <li>Argon2 / bcrypt password hashing with strong cost factors</li>
              <li>Short-lived, signed JWT tokens with instant revocation</li>
              <li>Strict OWASP security headers (CSP, HSTS, X-Frame-Options: DENY, nosniff)</li>
              <li>Parameterized database queries preventing SQL injection</li>
            </ul>
          </div>
        </div>

        <div style={{ marginTop: "20px", textAlign: "right" }}>
          <button className="btn btn-cyan" onClick={onClose}>
            Understood & Acknowledged
          </button>
        </div>
      </div>
    </div>
  );
}
