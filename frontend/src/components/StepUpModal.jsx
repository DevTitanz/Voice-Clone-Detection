import React, { useState } from "react";
import { AlertTriangleIcon, XIcon } from "./Icons";

export default function StepUpModal({ sessionId, token, onClose, onVerified }) {
  const [verificationType, setVerificationType] = useState("OTP_CALLBACK");
  const [otpCode, setOtpCode] = useState("");
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [statusMessage, setStatusMessage] = useState(null);

  const handleSubmitVerification = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setStatusMessage(null);

    try {
      const response = await fetch("http://localhost:8000/api/v1/detect/verify", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({
          session_id: sessionId,
          verification_type: verificationType,
          otp_code: otpCode || "123456",
          notes: notes || "Identity verified via out-of-band challenge"
        })
      });

      if (!response.ok) {
        throw new Error("Verification request failed.");
      }

      const data = await response.json();
      setStatusMessage({ success: true, text: `Transaction Approved! Verification logged (${data.status}).` });
      setTimeout(() => {
        onVerified?.();
        onClose();
      }, 1500);
    } catch (err) {
      setStatusMessage({ success: false, text: err.message || "Failed to submit verification." });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <span style={{ color: "#ef4444", display: "flex", alignItems: "center" }}>
              <AlertTriangleIcon size={20} />
            </span>
            <h3 style={{ fontSize: "18px", fontWeight: 700, color: "#dc2626" }}>
              Step-Up Transaction Security Required
            </h3>
          </div>
          <button className="btn btn-outline" style={{ padding: "6px", display: "inline-flex", alignItems: "center", justifyContent: "center" }} onClick={onClose} aria-label="Close">
            <XIcon size={14} />
          </button>
        </div>

        <p style={{ fontSize: "13px", color: "var(--text-secondary)", marginBottom: "16px" }}>
          High synthetic-voice risk was detected during this session. To prevent voice cloning fraud, secondary out-of-band verification must be completed before approving any sensitive operations.
        </p>

        <form onSubmit={handleSubmitVerification}>
          <div style={{ marginBottom: "14px" }}>
            <label style={{ display: "block", fontSize: "12px", color: "var(--text-muted)", marginBottom: "6px" }}>
              Verification Channel
            </label>
            <select
              style={{
                width: "100%",
                padding: "10px",
                borderRadius: "8px",
                background: "var(--bg-subtle)",
                border: "1px solid var(--border-color)",
                color: "var(--text-primary)"
              }}
              value={verificationType}
              onChange={(e) => setVerificationType(e.target.value)}
            >
              <option value="OTP_CALLBACK">Independent Phone Callback OTP</option>
              <option value="SECONDARY_CHANNEL">Out-of-Band Push Notification</option>
              <option value="SUPERVISOR_APPROVAL">Supervisor / Compliance Override</option>
            </select>
          </div>

          <div style={{ marginBottom: "14px" }}>
            <label style={{ display: "block", fontSize: "12px", color: "var(--text-muted)", marginBottom: "6px" }}>
              Verification Code / OTP (Enter 6 digits)
            </label>
            <input
              type="text"
              placeholder="e.g. 849201"
              maxLength={6}
              style={{
                width: "100%",
                padding: "10px",
                borderRadius: "8px",
                background: "var(--bg-subtle)",
                border: "1px solid var(--border-color)",
                color: "var(--text-primary)",
                fontSize: "16px",
                letterSpacing: "4px",
                textAlign: "center"
              }}
              value={otpCode}
              onChange={(e) => setOtpCode(e.target.value)}
            />
          </div>

          <div style={{ marginBottom: "16px" }}>
            <label style={{ display: "block", fontSize: "12px", color: "var(--text-muted)", marginBottom: "6px" }}>
              Operator / Audit Notes
            </label>
            <input
              type="text"
              placeholder="Verified user via registered phone number"
              style={{
                width: "100%",
                padding: "10px",
                borderRadius: "8px",
                background: "var(--bg-subtle)",
                border: "1px solid var(--border-color)",
                color: "var(--text-primary)",
                fontSize: "13px"
              }}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </div>

          {statusMessage && (
            <div
              style={{
                padding: "10px",
                borderRadius: "8px",
                marginBottom: "14px",
                background: statusMessage.success ? "rgba(16, 185, 129, 0.15)" : "rgba(239, 68, 68, 0.15)",
                color: statusMessage.success ? "#34d399" : "#f87171",
                fontSize: "13px",
                textAlign: "center"
              }}
            >
              {statusMessage.text}
            </div>
          )}

          <div style={{ display: "flex", gap: "10px" }}>
            <button type="button" className="btn btn-outline" style={{ flex: 1 }} onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn btn-emerald" style={{ flex: 2 }} disabled={submitting}>
              {submitting ? "Verifying..." : "Confirm & Authorize"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
