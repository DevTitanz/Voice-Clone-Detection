import React, { useState, useEffect } from "react";
import { SettingsIcon, ShieldIcon, AlertTriangleIcon } from "./Icons";

export default function AdminPanel({ token, currentUser }) {
  const [metrics, setMetrics] = useState(null);
  const [thresholdLow, setThresholdLow] = useState(40.0);
  const [thresholdHigh, setThresholdHigh] = useState(70.0);
  const [auditLogs, setAuditLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [statusMsg, setStatusMsg] = useState(null);

  const isAdmin = currentUser?.role === "ADMIN";

  const fetchAdminData = async () => {
    if (!token || !isAdmin) return;
    setLoading(true);
    try {
      // 1. Fetch metrics
      const mRes = await fetch("http://localhost:8000/api/v1/admin/metrics", {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (mRes.ok) {
        setMetrics(await mRes.json());
      }

      // 2. Fetch thresholds
      const tRes = await fetch("http://localhost:8000/api/v1/admin/thresholds", {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (tRes.ok) {
        const tData = await tRes.json();
        setThresholdLow(tData.threshold_low);
        setThresholdHigh(tData.threshold_high);
      }

      // 3. Fetch audit logs
      const aRes = await fetch("http://localhost:8000/api/v1/admin/audit-logs?limit=15", {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (aRes.ok) {
        setAuditLogs(await aRes.json());
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAdminData();
  }, [token, isAdmin]);

  const handleUpdateThresholds = async () => {
    try {
      const res = await fetch("http://localhost:8000/api/v1/admin/thresholds", {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({
          threshold_low: parseFloat(thresholdLow),
          threshold_high: parseFloat(thresholdHigh),
          enforce_strict_stepup: true
        })
      });
      if (res.ok) {
        setStatusMsg({ success: true, text: "Risk thresholds updated on backend successfully." });
        setTimeout(() => setStatusMsg(null), 3000);
      } else {
        throw new Error("Failed to update thresholds.");
      }
    } catch (err) {
      setStatusMsg({ success: false, text: err.message });
    }
  };

  if (!isAdmin) {
    return (
      <div className="card" style={{ textAlign: "center", padding: "40px" }}>
        <div style={{ display: "flex", justifyContent: "center", marginBottom: "16px", color: "var(--accent-crimson)" }}>
          <AlertTriangleIcon size={48} />
        </div>
        <h3 style={{ color: "var(--accent-crimson)", marginBottom: "8px" }}>403 Access Denied: Admin Role Required</h3>
        <p style={{ color: "var(--text-muted)", fontSize: "14px", maxWidth: "480px", margin: "0 auto" }}>
          In accordance with our strict Role-Based Access Control (RBAC) security policy, system metrics, security thresholds, and audit trails are inaccessible to standard USER accounts.
        </p>
      </div>
    );
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
      {/* Metric Cards Row */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: "16px" }}>
        <div className="card" style={{ padding: "16px" }}>
          <div style={{ fontSize: "12px", color: "var(--text-muted)" }}>TOTAL ANALYSES</div>
          <div style={{ fontSize: "28px", fontWeight: 800, color: "var(--accent-blue)" }}>{metrics?.total_analyses ?? "--"}</div>
        </div>
        <div className="card" style={{ padding: "16px" }}>
          <div style={{ fontSize: "12px", color: "var(--text-muted)" }}>HIGH RISK DETECTIONS</div>
          <div style={{ fontSize: "28px", fontWeight: 800, color: "var(--accent-crimson)" }}>{metrics?.high_risk_count ?? "--"}</div>
        </div>
        <div className="card" style={{ padding: "16px" }}>
          <div style={{ fontSize: "12px", color: "var(--text-muted)" }}>VERIFICATIONS APPROVED</div>
          <div style={{ fontSize: "28px", fontWeight: 800, color: "var(--accent-emerald)" }}>{metrics?.verifications_approved ?? "--"}</div>
        </div>
        <div className="card" style={{ padding: "16px" }}>
          <div style={{ fontSize: "12px", color: "var(--text-muted)" }}>REGISTERED USERS</div>
          <div style={{ fontSize: "28px", fontWeight: 800, color: "var(--accent-purple)" }}>{metrics?.total_users ?? "--"}</div>
        </div>
      </div>

      {/* Threshold Configuration Card */}
      <div className="card">
        <div className="card-title-row">
          <div className="card-title">
            <SettingsIcon size={17} /> Backend Security Threshold Configuration
          </div>
          <span className="badge badge-cyan">ADMIN Controlled Only</span>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "24px", margin: "20px 0" }}>
          <div>
            <label style={{ display: "block", fontSize: "13px", fontWeight: 600, marginBottom: "8px" }}>
              Low Risk Cutoff: <span style={{ color: "var(--accent-emerald)" }}>{thresholdLow}%</span>
            </label>
            <input
              type="range"
              min="10"
              max="50"
              step="1"
              value={thresholdLow}
              onChange={(e) => setThresholdLow(e.target.value)}
              style={{ width: "100%", accentColor: "var(--accent-emerald)" }}
            />
            <p style={{ fontSize: "11px", color: "var(--text-muted)", marginTop: "4px" }}>
              Scores below this are considered natural voice patterns.
            </p>
          </div>

          <div>
            <label style={{ display: "block", fontSize: "13px", fontWeight: 600, marginBottom: "8px" }}>
              High Risk Step-Up Trigger: <span style={{ color: "var(--accent-crimson)" }}>{thresholdHigh}%</span>
            </label>
            <input
              type="range"
              min="51"
              max="95"
              step="1"
              value={thresholdHigh}
              onChange={(e) => setThresholdHigh(e.target.value)}
              style={{ width: "100%", accentColor: "var(--accent-crimson)" }}
            />
            <p style={{ fontSize: "11px", color: "var(--text-muted)", marginTop: "4px" }}>
              Scores above this mandate out-of-band step-up verification.
            </p>
          </div>
        </div>

        {statusMsg && (
          <div
            style={{
              padding: "10px",
              borderRadius: "8px",
              marginBottom: "12px",
              background: statusMsg.success ? "var(--accent-emerald-bg)" : "var(--accent-crimson-bg)",
              color: statusMsg.success ? "var(--accent-emerald)" : "var(--accent-crimson)",
              fontSize: "13px"
            }}
          >
            {statusMsg.text}
          </div>
        )}

        <button className="btn btn-cyan" onClick={handleUpdateThresholds}>
          Save Updated Thresholds to Backend
        </button>
      </div>

      {/* Security Audit Trail */}
      <div className="card">
        <div className="card-title-row">
          <div className="card-title">
            <ShieldIcon size={17} /> System Security Audit Trail
          </div>
          <span className="badge badge-purple">Zero PII / Passwords</span>
        </div>

        <div style={{ overflowX: "auto" }}>
          <table className="custom-table">
            <thead>
              <tr>
                <th>Event</th>
                <th>IP Address</th>
                <th>Details</th>
                <th>Timestamp</th>
              </tr>
            </thead>
            <tbody>
              {auditLogs.map((log) => (
                <tr key={log.id}>
                  <td><span className="badge badge-cyan" style={{ fontSize: "11px" }}>{log.event_type}</span></td>
                  <td style={{ fontFamily: "monospace", fontSize: "12px" }}>{log.ip_address || "127.0.0.1"}</td>
                  <td style={{ fontSize: "12px" }}>{log.details}</td>
                  <td style={{ fontSize: "11px", color: "var(--text-muted)" }}>{new Date(log.timestamp).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
