import React, { useState } from "react";
import { UploadIcon, LockIcon } from "./Icons";

export default function FileAnalyzer({ token, onTriggerStepUp }) {
  const [selectedFile, setSelectedFile] = useState(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [result, setResult] = useState(null);
  const [errorMsg, setErrorMsg] = useState(null);

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // Client-side file size check (10MB limit)
    if (file.size > 10 * 1024 * 1024) {
      setErrorMsg("File exceeds the maximum allowed 10MB limit.");
      setSelectedFile(null);
      return;
    }

    setErrorMsg(null);
    setSelectedFile(file);
    setResult(null);
  };

  const handleUploadAndAnalyze = async () => {
    if (!selectedFile) return;
    if (!token) {
      setErrorMsg("Authentication required. Please sign in.");
      return;
    }

    setAnalyzing(true);
    setErrorMsg(null);

    const formData = new FormData();
    formData.append("file", selectedFile);

    try {
      const response = await fetch("http://localhost:8000/api/v1/detect/upload", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`
        },
        body: formData
      });

      if (!response.ok) {
        const errData = await response.json().catch(() => ({ detail: "Upload failed." }));
        throw new Error(errData.detail || `Server returned error ${response.status}`);
      }

      const data = await response.json();
      setResult(data);
    } catch (err) {
      setErrorMsg(err.message || "Failed to analyze audio file.");
    } finally {
      setAnalyzing(false);
    }
  };

  return (
    <div className="card">
      <div className="card-title-row">
        <div className="card-title">
          <UploadIcon size={17} /> Audio File Security Inspector
        </div>
        <span className="badge badge-purple">
          <LockIcon size={12} /> Zero Storage Guaranteed
        </span>
      </div>

      {/* Upload Dropzone */}
      <div
        className="dropzone"
        onClick={() => document.getElementById("audio-file-input")?.click()}
      >
        <input
          id="audio-file-input"
          type="file"
          accept="audio/wav,audio/mp3,audio/mpeg,audio/ogg,audio/flac"
          style={{ display: "none" }}
          onChange={handleFileChange}
        />
        <div className="dropzone-icon">
          <UploadIcon size={32} />
        </div>
        <p style={{ fontWeight: 600, color: "var(--text-primary)", marginBottom: "4px" }}>
          {selectedFile ? selectedFile.name : "Select or drag an audio file to analyze"}
        </p>
        <p style={{ fontSize: "12px", color: "var(--text-muted)" }}>
          WAV, MP3, OGG, FLAC (Max 10MB) • Verified via Content Magic Bytes
        </p>
      </div>

      {errorMsg && (
        <div style={{ marginTop: "12px", padding: "10px", borderRadius: "8px", background: "var(--accent-crimson-bg)", color: "var(--accent-crimson)", fontSize: "13px" }}>
          {errorMsg}
        </div>
      )}

      {selectedFile && (
        <div style={{ marginTop: "16px" }}>
          <button
            className="btn btn-cyan btn-block"
            onClick={handleUploadAndAnalyze}
            disabled={analyzing}
          >
            {analyzing ? "Extracting Acoustic Biomarkers..." : "Run AI Deepfake Analysis"}
          </button>
        </div>
      )}

      {/* Analysis Result Card */}
      {result && (
        <div style={{ marginTop: "20px", padding: "16px", background: "var(--bg-subtle)", borderRadius: "12px", border: "1px solid var(--border-color)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "12px" }}>
            <div>
              <div style={{ fontSize: "12px", color: "var(--text-muted)", textTransform: "uppercase", letterSpacing: "1px" }}>
                Classification
              </div>
              <div style={{ fontSize: "18px", fontWeight: 700, color: result.risk_score >= 70 ? "var(--accent-crimson)" : result.risk_score >= 40 ? "var(--accent-amber)" : "var(--accent-emerald)" }}>
                {result.classification_label}
              </div>
            </div>
            <div style={{ textAlign: "right" }}>
              <div style={{ fontSize: "28px", fontWeight: 800, color: result.risk_score >= 70 ? "var(--accent-crimson)" : result.risk_score >= 40 ? "var(--accent-amber)" : "var(--accent-emerald)" }}>
                {result.risk_score}%
              </div>
              <div style={{ fontSize: "11px", color: "var(--text-muted)" }}>Risk Score</div>
            </div>
          </div>

          <div style={{ fontSize: "12px", color: "var(--text-secondary)", marginBottom: "12px" }}>
            <div>• <strong>Session ID:</strong> {result.session_id}</div>
            <div>• <strong>Model Version:</strong> {result.model_version}</div>
            <div>• <strong>Confidence:</strong> {(result.confidence * 100).toFixed(0)}%</div>
            <div>• <strong>Zero Persistence:</strong> Audio buffer purged from memory immediately.</div>
          </div>

          {result.verification_required && (
            <button
              className="btn btn-crimson btn-block"
              style={{ padding: "8px", fontSize: "13px" }}
              onClick={() => onTriggerStepUp(result.session_id)}
            >
              Trigger Step-Up Verification
            </button>
          )}
        </div>
      )}
    </div>
  );
}
