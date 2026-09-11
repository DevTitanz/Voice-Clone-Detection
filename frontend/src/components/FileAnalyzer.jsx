import React, { useState, useEffect, useRef } from "react";
import { UploadIcon, LockIcon } from "./Icons";

// Magma colormap approximation for 64x128 Mel-Spectrogram (matching pk9444/2xqcTCqAYvy0SJbK)
function getMagmaColor(val) {
  // val is normalized in [0.0, 1.0]
  const clamped = Math.max(0, Math.min(1, val));
  // Interpolate dark purple -> dark red -> vibrant orange -> yellow
  let r, g, b;
  if (clamped < 0.25) {
    const t = clamped / 0.25;
    r = Math.round(15 + t * (80 - 15));
    g = Math.round(10 + t * (15 - 10));
    b = Math.round(40 + t * (120 - 40));
  } else if (clamped < 0.5) {
    const t = (clamped - 0.25) / 0.25;
    r = Math.round(80 + t * (182 - 80));
    g = Math.round(15 + t * (54 - 15));
    b = Math.round(120 + t * (121 - 120));
  } else if (clamped < 0.75) {
    const t = (clamped - 0.5) / 0.25;
    r = Math.round(182 + t * (251 - 182));
    g = Math.round(54 + t * (136 - 54));
    b = Math.round(121 + t * (97 - 121));
  } else {
    const t = (clamped - 0.75) / 0.25;
    r = Math.round(251 + t * (252 - 251));
    g = Math.round(136 + t * (253 - 136));
    b = Math.round(97 + t * (191 - 97));
  }
  return `rgb(${r}, ${g}, ${b})`;
}

function MelSpectrogramViewer({ melData }) {
  const canvasRef = useRef(null);

  useEffect(() => {
    if (!melData || !canvasRef.current) return;
    const canvas = canvasRef.current;
    const ctx = canvas.getContext("2d");
    const numBands = melData.length; // 64
    const numFrames = melData[0].length; // 128

    const width = canvas.width;
    const height = canvas.height;
    const cellW = width / numFrames;
    const cellH = height / numBands;

    // Find min and max for normalization
    let minVal = -80.0;
    let maxVal = 0.0;

    for (let r = 0; r < numBands; r++) {
      for (let c = 0; c < numFrames; c++) {
        const v = melData[r][c];
        if (v < minVal) minVal = v;
        if (v > maxVal) maxVal = v;
      }
    }
    const range = Math.max(1e-5, maxVal - minVal);

    // Render inverted vertically so low frequency is at bottom
    for (let r = 0; r < numBands; r++) {
      const yIdx = numBands - 1 - r;
      for (let c = 0; c < numFrames; c++) {
        const norm = (melData[r][c] - minVal) / range;
        ctx.fillStyle = getMagmaColor(norm);
        ctx.fillRect(c * cellW, yIdx * cellH, Math.ceil(cellW), Math.ceil(cellH));
      }
    }
  }, [melData]);

  return (
    <div style={{ marginTop: "12px", background: "#0a0a0c", borderRadius: "10px", padding: "12px", border: "1px solid var(--border-color)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "6px" }}>
        <div style={{ fontSize: "12px", fontWeight: 700, color: "var(--text-primary)", display: "flex", alignItems: "center", gap: "6px" }}>
          <span style={{ width: "8px", height: "8px", borderRadius: "50%", background: "#ff8c00", display: "inline-block" }}></span>
          FAD-CNN Log-Mel Spectrogram (64 Mels × 128 Frames)
        </div>
        <span style={{ fontSize: "11px", color: "var(--text-muted)" }}>Magma dBFS Colormap</span>
      </div>
      <div style={{ position: "relative", width: "100%", height: "160px", overflow: "hidden", borderRadius: "6px" }}>
        <canvas
          ref={canvasRef}
          width={512}
          height={160}
          style={{ width: "100%", height: "100%", display: "block" }}
        />
      </div>
      <div style={{ display: "flex", justifyContent: "space-between", fontSize: "10px", color: "var(--text-muted)", marginTop: "4px" }}>
        <span>0 Hz (Low Vocals)</span>
        <span>Time Frames (0 - 128)</span>
        <span>8,000 Hz (Neural Vocoder Band)</span>
      </div>
    </div>
  );
}

function WaveformViewer({ waveform }) {
  const canvasRef = useRef(null);

  useEffect(() => {
    if (!waveform || !canvasRef.current) return;
    const canvas = canvasRef.current;
    const ctx = canvas.getContext("2d");
    const width = canvas.width;
    const height = canvas.height;

    ctx.clearRect(0, 0, width, height);

    // Background
    ctx.fillStyle = "#111827";
    ctx.fillRect(0, 0, width, height);

    // Center line
    ctx.strokeStyle = "rgba(255, 255, 255, 0.1)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(0, height / 2);
    ctx.lineTo(width, height / 2);
    ctx.stroke();

    // Waveform line
    ctx.strokeStyle = "#ff8c00";
    ctx.lineWidth = 1.5;
    ctx.beginPath();

    const n = waveform.length;
    const step = width / (n - 1);

    for (let i = 0; i < n; i++) {
      const x = i * step;
      const y = height / 2 - (waveform[i] * (height / 2) * 0.85);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    // Fill area
    ctx.lineTo(width, height / 2);
    ctx.lineTo(0, height / 2);
    ctx.fillStyle = "rgba(255, 140, 0, 0.15)";
    ctx.fill();
  }, [waveform]);

  return (
    <div style={{ marginTop: "12px", background: "#0a0a0c", borderRadius: "10px", padding: "12px", border: "1px solid var(--border-color)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "6px" }}>
        <div style={{ fontSize: "12px", fontWeight: 700, color: "var(--text-primary)" }}>
          Normalized Audio Waveform
        </div>
        <span style={{ fontSize: "11px", color: "var(--text-muted)" }}>Normalized [-1.0, 1.0]</span>
      </div>
      <div style={{ width: "100%", height: "90px", borderRadius: "6px", overflow: "hidden" }}>
        <canvas
          ref={canvasRef}
          width={600}
          height={90}
          style={{ width: "100%", height: "100%", display: "block" }}
        />
      </div>
    </div>
  );
}

export default function FileAnalyzer({ token, onTriggerStepUp, onTokenExpired }) {
  const [selectedFile, setSelectedFile] = useState(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [result, setResult] = useState(null);
  const [errorMsg, setErrorMsg] = useState(null);
  const [activeTab, setActiveTab] = useState("ensemble");

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

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
        if (response.status === 401) {
          onTokenExpired?.();
          throw new Error("Session credentials expired. A fresh session has been refreshed — please click Analyze again.");
        }
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
            {analyzing ? "Evaluating FAD-CNN & Acoustic Biomarkers..." : "Run AI Deepfake Analysis"}
          </button>
        </div>
      )}

      {/* Analysis Result Card */}
      {result && (
        <div style={{ marginTop: "20px", padding: "18px", background: "var(--bg-subtle)", borderRadius: "14px", border: "1px solid var(--border-color)" }}>
          {/* Header & Risk Score */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "14px" }}>
            <div>
              <div style={{ fontSize: "11px", color: "var(--text-muted)", textTransform: "uppercase", letterSpacing: "1px", fontWeight: 700 }}>
                Ensemble Classification
              </div>
              <div style={{ fontSize: "20px", fontWeight: 800, color: result.risk_score >= 70 ? "var(--accent-crimson)" : result.risk_score >= 40 ? "var(--accent-amber)" : "var(--accent-emerald)" }}>
                {result.classification_label}
              </div>
            </div>
            <div style={{ textAlign: "right" }}>
              <div style={{ fontSize: "32px", fontWeight: 900, color: result.risk_score >= 70 ? "var(--accent-crimson)" : result.risk_score >= 40 ? "var(--accent-amber)" : "var(--accent-emerald)" }}>
                {result.risk_score}%
              </div>
              <div style={{ fontSize: "11px", color: "var(--text-muted)" }}>VoiceGuard Risk Score</div>
            </div>
          </div>

          {/* Model Comparison Pill Bar */}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10px", marginBottom: "14px" }}>
            <div style={{ padding: "10px 14px", background: "var(--bg-surface)", borderRadius: "10px", border: "1px solid var(--border-color)" }}>
              <div style={{ fontSize: "11px", color: "var(--text-muted)", marginBottom: "2px" }}>FAD-CNN Mel Model (pk9444)</div>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                <span style={{ fontWeight: 800, fontSize: "15px", color: result.fad_cnn_prediction === "FAKE" ? "var(--accent-crimson)" : "var(--accent-emerald)" }}>
                  {result.fad_cnn_prediction || "EVALUATED"}
                </span>
                <span className="badge badge-purple" style={{ fontSize: "11px" }}>
                  {result.fad_cnn_prob !== undefined ? `${(result.fad_cnn_prob * 100).toFixed(1)}% Fake Prob` : "Active"}
                </span>
              </div>
            </div>

            <div style={{ padding: "10px 14px", background: "var(--bg-surface)", borderRadius: "10px", border: "1px solid var(--border-color)" }}>
              <div style={{ fontSize: "11px", color: "var(--text-muted)", marginBottom: "2px" }}>Confidence & Integrity</div>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                <span style={{ fontWeight: 800, fontSize: "15px", color: "var(--text-primary)" }}>
                  {((result.confidence || 0.85) * 100).toFixed(0)}%
                </span>
                <span className="badge badge-cyan" style={{ fontSize: "11px" }}>Zero Disk Retention</span>
              </div>
            </div>
          </div>

          {/* Smart AI Diagnostic Explanation (From pk9444/2xqcTCqAYvy0SJbK) */}
          {result.smart_explanation && (
            <div style={{ padding: "12px 14px", background: "var(--accent-blue-bg)", border: "1px solid var(--accent-blue-border)", borderRadius: "10px", marginBottom: "14px" }}>
              <div style={{ fontSize: "12px", fontWeight: 700, color: "var(--accent-blue)", display: "flex", alignItems: "center", gap: "6px", marginBottom: "4px" }}>
                <span>💡</span> Smart Acoustic Explanation
              </div>
              <p style={{ fontSize: "13px", color: "var(--text-primary)", lineHeight: "1.5", margin: 0 }}>
                {result.smart_explanation}
              </p>
            </div>
          )}

          {/* Mel Spectrogram Heatmap */}
          {result.mel_spectrogram && (
            <MelSpectrogramViewer melData={result.mel_spectrogram} />
          )}

          {/* Waveform Preview */}
          {result.waveform_preview && (
            <WaveformViewer waveform={result.waveform_preview} />
          )}

          {/* Deep Acoustic Biomarkers Grid */}
          {result.acoustic_traits && (
            <div style={{ marginTop: "14px", padding: "12px", background: "var(--bg-surface)", borderRadius: "10px", border: "1px solid var(--border-color)" }}>
              <div style={{ fontSize: "11px", fontWeight: 700, color: "var(--text-muted)", textTransform: "uppercase", marginBottom: "8px" }}>
                Acoustic Trait Biomarkers
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "8px", textAlign: "center" }}>
                <div style={{ padding: "8px", background: "var(--bg-subtle)", borderRadius: "8px" }}>
                  <div style={{ fontSize: "14px", fontWeight: 700, color: "var(--text-primary)" }}>
                    {result.acoustic_traits.duration_sec}s
                  </div>
                  <div style={{ fontSize: "10px", color: "var(--text-muted)" }}>Duration</div>
                </div>
                <div style={{ padding: "8px", background: "var(--bg-subtle)", borderRadius: "8px" }}>
                  <div style={{ fontSize: "14px", fontWeight: 700, color: "var(--text-primary)" }}>
                    {result.acoustic_traits.rms_db} dB
                  </div>
                  <div style={{ fontSize: "10px", color: "var(--text-muted)" }}>RMS Energy</div>
                </div>
                <div style={{ padding: "8px", background: "var(--bg-subtle)", borderRadius: "8px" }}>
                  <div style={{ fontSize: "14px", fontWeight: 700, color: "var(--text-primary)" }}>
                    {((result.acoustic_traits.silence_ratio || 0) * 100).toFixed(1)}%
                  </div>
                  <div style={{ fontSize: "10px", color: "var(--text-muted)" }}>Silence Ratio</div>
                </div>
                <div style={{ padding: "8px", background: "var(--bg-subtle)", borderRadius: "8px" }}>
                  <div style={{ fontSize: "14px", fontWeight: 700, color: "var(--text-primary)" }}>
                    {result.acoustic_traits.spectral_centroid_hz} Hz
                  </div>
                  <div style={{ fontSize: "10px", color: "var(--text-muted)" }}>Centroid</div>
                </div>
              </div>
            </div>
          )}

          {/* Session Metadata */}
          <div style={{ fontSize: "11px", color: "var(--text-secondary)", marginTop: "12px", display: "flex", justifyContent: "space-between" }}>
            <span>• <strong>Session:</strong> {result.session_id}</span>
            <span>• <strong>Model Engine:</strong> {result.model_version}</span>
          </div>

          {result.verification_required && (
            <button
              className="btn btn-crimson btn-block"
              style={{ marginTop: "14px", padding: "10px", fontSize: "13px" }}
              onClick={() => onTriggerStepUp(result.session_id)}
            >
              Trigger Step-Up Verification Challenge
            </button>
          )}
        </div>
      )}
    </div>
  );
}
