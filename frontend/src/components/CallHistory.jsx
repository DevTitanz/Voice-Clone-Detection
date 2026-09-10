import React, { useState, useEffect } from "react";
import { HistoryIcon, LockIcon } from "./Icons";

export default function CallHistory({ token }) {
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchHistory = async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    try {
      const response = await fetch("http://localhost:8000/api/v1/detect/sessions", {
        headers: {
          Authorization: `Bearer ${token}`
        }
      });
      if (!response.ok) {
        throw new Error("Failed to load call history.");
      }
      const data = await response.json();
      setSessions(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHistory();
  }, [token]);

  return (
    <div className="card">
      <div className="card-title-row">
        <div className="card-title">
          <HistoryIcon size={17} /> Call Analysis History & Audit Trail
        </div>
        <button className="btn btn-outline" style={{ padding: "6px 14px", fontSize: "12px" }} onClick={fetchHistory}>
          Refresh
        </button>
      </div>

      <div style={{ padding: "10px 14px", background: "var(--accent-purple-bg)", borderRadius: "8px", border: "1px solid var(--accent-purple-border)", marginBottom: "16px", fontSize: "12px", color: "var(--accent-purple)" }}>
        <LockIcon size={12} /> <strong>Privacy Guarantee:</strong> Each entry stores auditable technical metadata only. Raw caller voice recordings are never retained.
      </div>

      {loading ? (
        <p style={{ color: "var(--text-muted)", textAlign: "center", padding: "20px" }}>Loading session history...</p>
      ) : error ? (
        <p style={{ color: "#f87171", textAlign: "center", padding: "20px" }}>{error}</p>
      ) : sessions.length === 0 ? (
        <p style={{ color: "var(--text-muted)", textAlign: "center", padding: "24px" }}>
          No calls analyzed yet. Start a real-time stream or upload an audio recording to inspect!
        </p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="custom-table">
            <thead>
              <tr>
                <th>Anonymous Session</th>
                <th>Client</th>
                <th>Risk Score</th>
                <th>Classification</th>
                <th>Duration</th>
                <th>Model</th>
                <th>Timestamp</th>
              </tr>
            </thead>
            <tbody>
              {sessions.map((s) => (
                <tr key={s.id}>
                  <td style={{ fontFamily: "monospace", fontSize: "12px" }}>{s.anonymous_session_id.substring(0, 16)}...</td>
                  <td><span className="badge" style={{ fontSize: "11px" }}>{s.client_type.toUpperCase()}</span></td>
                  <td>
                    <span style={{ fontWeight: 700, color: s.final_risk_score >= 70 ? "#ef4444" : s.final_risk_score >= 40 ? "#f59e0b" : "#10b981" }}>
                      {s.final_risk_score}%
                    </span>
                  </td>
                  <td>
                    <span
                      className="badge"
                      style={{
                        fontSize: "11px",
                        background: s.classification === "HIGH_RISK" ? "rgba(239, 68, 68, 0.15)" : s.classification === "MEDIUM_RISK" ? "rgba(245, 158, 11, 0.15)" : "rgba(16, 185, 129, 0.15)",
                        color: s.classification === "HIGH_RISK" ? "#f87171" : s.classification === "MEDIUM_RISK" ? "#fbbf24" : "#34d399",
                        borderColor: "transparent"
                      }}
                    >
                      {s.classification}
                    </span>
                  </td>
                  <td>{s.audio_duration_seconds}s</td>
                  <td style={{ fontSize: "11px", color: "var(--text-muted)" }}>{s.model_version}</td>
                  <td style={{ fontSize: "12px", color: "var(--text-muted)" }}>{new Date(s.created_at).toLocaleTimeString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
