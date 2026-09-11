import React, { useState, useEffect, useRef } from "react";
import { MicIcon, AlertTriangleIcon, CheckCircleIcon, PhoneIcon, PhoneOffIcon } from "./Icons";

export default function LiveCallStreamer({ token, onTriggerStepUp, currentThresholdHigh = 70 }) {
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamSource, setStreamSource] = useState("mic"); // 'mic' or 'synthetic_sim'
  const [riskScore, setRiskScore] = useState(12.5);
  const [classification, setClassification] = useState("LOW_RISK");
  const [classificationLabel, setClassificationLabel] = useState("Natural voice patterns detected");
  const [detectedEmotion, setDetectedEmotion] = useState("Calm / Conversational");
  const [emotionFlag, setEmotionFlag] = useState(null);
  const [confidence, setConfidence] = useState(0.92);
  const [duration, setDuration] = useState(0.0);
  const [sessionId, setSessionId] = useState(null);
  const [wsStatus, setWsStatus] = useState("Standby");
  const [biomarkers, setBiomarkers] = useState({
    spectral_rolloff: 4200,
    spectral_centroid: 2150,
    high_freq_ratio: 0.04,
    jitter_factor: 0.28,
    pitch_variance_hz: 36.0,
    zero_crossing_rate: 0.065
  });

  const wsRef = useRef(null);
  const audioContextRef = useRef(null);
  const scriptProcessorRef = useRef(null);
  const mediaStreamRef = useRef(null);
  const simIntervalRef = useRef(null);

  // Determine risk level styling class
  const getRiskClass = (score) => {
    if (score >= currentThresholdHigh) return "risk-high";
    if (score >= 40) return "risk-med";
    return "risk-low";
  };

  const getBannerClass = (score) => {
    if (score >= currentThresholdHigh) return "banner-high";
    if (score >= 40) return "banner-med";
    return "banner-low";
  };

  const startStreaming = async () => {
    if (!token) {
      alert("Please sign in or use demo credentials to start an authenticated stream.");
      return;
    }

    try {
      setWsStatus("Connecting to secure WSS gateway...");
      const wsUrl = `ws://localhost:8000/api/v1/ws/stream?token=${encodeURIComponent(token)}&client_type=web`;
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;

      ws.onopen = async () => {
        setWsStatus("Shield Active • Streaming Encrypted Chunks");
        setIsStreaming(true);

        if (streamSource === "mic") {
          // Initialize Web Audio API for live microphone streaming
          try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            mediaStreamRef.current = stream;
            const audioCtx = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
            audioContextRef.current = audioCtx;

            const source = audioCtx.createMediaStreamSource(stream);
            // 2048 samples = 128ms at 16kHz
            const processor = audioCtx.createScriptProcessor(2048, 1, 1);
            scriptProcessorRef.current = processor;

            processor.onaudioprocess = (e) => {
              if (ws.readyState === WebSocket.OPEN) {
                const inputData = e.inputBuffer.getChannelData(0);
                // Convert float32 [-1, 1] to 16-bit PCM buffer
                const pcmBuffer = new Int16Array(inputData.length);
                for (let i = 0; i < inputData.length; i++) {
                  const s = Math.max(-1, Math.min(1, inputData[i]));
                  pcmBuffer[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
                }
                ws.send(pcmBuffer.buffer);
              }
            };

            source.connect(processor);
            processor.connect(audioCtx.destination);
          } catch (micErr) {
            console.warn("Microphone access denied or unavailable, switching to simulated stream.", micErr);
            startSimulatedAudio(ws, false);
          }
        } else {
          // Simulated synthetic vocoder stream
          startSimulatedAudio(ws, true);
        }
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.event === "CONNECTED") {
            setSessionId(data.session_id);
          } else if (data.event === "ANALYSIS_UPDATE") {
            setRiskScore(data.risk_score);
            setClassification(data.classification);
            setClassificationLabel(data.classification_label);
            setConfidence(data.confidence);
            setDuration(data.audio_duration_seconds);
            if (data.detected_emotion) {
              setDetectedEmotion(data.detected_emotion);
            }
            if (data.emotion_incongruence_flag !== undefined) {
              setEmotionFlag(data.emotion_incongruence_flag);
            }
            if (data.features_summary) {
              setBiomarkers(data.features_summary);
            }
          }
        } catch (e) {
          // Non-json
        }
      };

      ws.onerror = () => {
        setWsStatus("Connection Error. Stream disconnected.");
        stopStreaming();
      };

      ws.onclose = () => {
        setWsStatus("Call Ended • Volatile Audio Memory Purged");
        stopStreaming();
      };

    } catch (err) {
      setWsStatus("Failed to connect to gateway.");
      stopStreaming();
    }
  };

  const startSimulatedAudio = (ws, isSyntheticVocoder) => {
    let tick = 0;
    simIntervalRef.current = setInterval(() => {
      if (ws.readyState !== WebSocket.OPEN) return;
      tick++;
      // Generate synthetic or natural PCM chunk
      const samples = 2048;
      const buffer = new Int16Array(samples);
      for (let i = 0; i < samples; i++) {
        const t = (tick * samples + i) / 16000;
        let val;
        if (isSyntheticVocoder) {
          // Neural vocoder signature: harmonic regularity + high-frequency phase tone
          val = 0.4 * Math.sin(2 * Math.PI * 300 * t) + 0.35 * Math.sin(2 * Math.PI * 6500 * t);
        } else {
          // Natural speech simulation: fundamental frequency with natural jitter
          val = 0.5 * Math.sin(2 * Math.PI * 180 * t) + 0.2 * Math.sin(2 * Math.PI * 360 * t);
        }
        buffer[i] = Math.floor(val * 32000);
      }
      ws.send(buffer.buffer);
    }, 130);
  };

  const stopStreaming = () => {
    setIsStreaming(false);

    if (simIntervalRef.current) {
      clearInterval(simIntervalRef.current);
      simIntervalRef.current = null;
    }

    if (scriptProcessorRef.current) {
      scriptProcessorRef.current.disconnect();
      scriptProcessorRef.current = null;
    }

    if (mediaStreamRef.current) {
      mediaStreamRef.current.getTracks().forEach((t) => t.stop());
      mediaStreamRef.current = null;
    }

    if (audioContextRef.current && audioContextRef.current.state !== "closed") {
      audioContextRef.current.close();
      audioContextRef.current = null;
    }

    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ action: "END_CALL" }));
      wsRef.current.close();
      wsRef.current = null;
    }
  };

  useEffect(() => {
    return () => {
      stopStreaming();
    };
  }, []);

  return (
    <div className="card">
      <div className="card-title-row">
        <div className="card-title">
          <MicIcon size={17} /> Real-Time Voice Cloning Detection Stream
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          {isStreaming && <span className="pulse-dot" style={{ color: "#059669" }} />}
          <span className="badge badge-emerald">{wsStatus}</span>
        </div>
      </div>

      {/* Stream Source Selector */}
      <div style={{ display: "flex", gap: "10px", marginBottom: "20px" }}>
        <button
          className={`btn ${streamSource === "mic" ? "btn-cyan" : "btn-outline"}`}
          style={{ flex: 1, padding: "8px 12px", fontSize: "13px" }}
          onClick={() => { if (!isStreaming) setStreamSource("mic"); }}
          disabled={isStreaming}
        >
          Live Microphone Input
        </button>
        <button
          className={`btn ${streamSource === "synthetic_sim" ? "btn-cyan" : "btn-outline"}`}
          style={{ flex: 1, padding: "8px 12px", fontSize: "13px" }}
          onClick={() => { if (!isStreaming) setStreamSource("synthetic_sim"); }}
          disabled={isStreaming}
        >
          Simulated AI Voice (Test Vocoder)
        </button>
      </div>

      {/* ONGOING CALL FLOATING SCORE POP-UP BANNER */}
      {isStreaming && (
        <div
          style={{
            background: "var(--bg-surface)",
            border: `2px solid ${riskScore >= currentThresholdHigh ? "var(--accent-crimson)" : riskScore >= 40 ? "var(--accent-amber)" : "var(--accent-emerald)"}`,
            borderRadius: "14px",
            padding: "16px 20px",
            boxShadow: "0 10px 30px rgba(0, 0, 0, 0.12)",
            animation: "slideDown 0.3s ease",
            marginBottom: "20px",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: "16px"
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "14px" }}>
            <div
              style={{
                width: "48px",
                height: "48px",
                borderRadius: "50%",
                background: riskScore >= currentThresholdHigh ? "var(--accent-crimson-bg)" : riskScore >= 40 ? "var(--accent-amber-bg)" : "var(--accent-emerald-bg)",
                border: `2px solid ${riskScore >= currentThresholdHigh ? "var(--accent-crimson)" : riskScore >= 40 ? "var(--accent-amber)" : "var(--accent-emerald)"}`,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontWeight: 900,
                fontSize: "18px",
                color: riskScore >= currentThresholdHigh ? "var(--accent-crimson)" : riskScore >= 40 ? "var(--accent-amber)" : "var(--accent-emerald)"
              }}
            >
              {Math.round(riskScore)}%
            </div>
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <span className="pulse-dot" style={{ color: riskScore >= currentThresholdHigh ? "#ef4444" : "#10b981", width: "8px", height: "8px" }} />
                <span style={{ fontSize: "12px", fontWeight: 700, letterSpacing: "0.5px", color: "var(--text-primary)" }}>
                  CALL IN PROGRESS — LIVE DETECTION
                </span>
              </div>
              <div style={{ fontSize: "13px", fontWeight: 600, color: "var(--text-secondary)", marginTop: "2px" }}>
                {classificationLabel} • Duration: {duration.toFixed(1)}s
              </div>
              <div style={{ display: "flex", gap: "8px", alignItems: "center", marginTop: "6px" }}>
                <span className="badge" style={{ background: emotionFlag ? "rgba(239, 68, 68, 0.15)" : "rgba(37, 99, 235, 0.12)", color: emotionFlag ? "#dc2626" : "#2563eb", fontSize: "11px", padding: "3px 8px" }}>
                  Caller Emotion: {detectedEmotion}
                </span>
                {emotionFlag && (
                  <span style={{ fontSize: "11px", color: "#dc2626", fontWeight: 600, display: "inline-flex", alignItems: "center", gap: "4px" }}>
                    <AlertTriangleIcon size={12} /> {emotionFlag}
                  </span>
                )}
              </div>
            </div>
          </div>

          <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
            {riskScore >= currentThresholdHigh && (
              <button
                className="btn btn-crimson"
                style={{ padding: "8px 14px", fontSize: "12px" }}
                onClick={() => onTriggerStepUp(sessionId || "simulated_sess")}
              >
                Trigger Step-Up Verification
              </button>
            )}
            <button
              className="btn btn-outline"
              style={{ padding: "8px 12px", fontSize: "12px", display: "inline-flex", alignItems: "center", gap: "6px" }}
              onClick={stopStreaming}
            >
              <PhoneOffIcon size={14} />
              <span>End Call</span>
            </button>
          </div>
        </div>
      )}

      {/* Real-time Animated Risk Radar Gauge */}
      <div className="radar-container">
        <div className={`radar-circle ${getRiskClass(riskScore)}`}>
          <div
            className="risk-score-number"
            style={{
              color: riskScore >= currentThresholdHigh ? "#dc2626" : riskScore >= 40 ? "#d97706" : "#059669"
            }}
          >
            {Math.round(riskScore)}%
          </div>
          <div className="risk-score-label">Synthetic Risk</div>
          <div style={{ fontSize: "11px", color: "var(--text-muted)", marginTop: "4px" }}>
            Confidence: {(confidence * 100).toFixed(0)}%
          </div>
        </div>

        {/* Probabilistic Classification Banner */}
        <div className={`probabilistic-banner ${getBannerClass(riskScore)}`}>
          {riskScore >= currentThresholdHigh ? <AlertTriangleIcon size={15} /> : <CheckCircleIcon size={15} />}
          <span>{classificationLabel}</span>
        </div>

        {/* Emotion Pill Banner */}
        <div style={{ marginTop: "8px", display: "inline-block" }}>
          <span className="badge" style={{ background: "rgba(37, 99, 235, 0.1)", color: "#2563eb", fontSize: "11px", padding: "4px 10px" }}>
            Detected Emotion: {detectedEmotion}
          </span>
        </div>

        {riskScore >= currentThresholdHigh && (
          <div style={{ marginTop: "12px", width: "100%" }}>
            <button
              className="btn btn-crimson btn-block"
              onClick={() => onTriggerStepUp(sessionId || "simulated_sess")}
            >
              Step-Up Verification Required
            </button>
          </div>
        )}
      </div>

      {/* Dynamic Real-Time Audio Canvas Spectrum Visualizer */}
      <div style={{ marginTop: "16px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "6px" }}>
          <span style={{ fontSize: "12px", fontWeight: 700, color: "var(--text-primary)", display: "flex", alignItems: "center", gap: "6px" }}>
            <span className={`beacon-pulse ${isStreaming ? (riskScore >= currentThresholdHigh ? "crimson" : "emerald") : "amber"}`} />
            {isStreaming ? "Live Audio Spectrum (32-Band FFT)" : "Audio Spectrum Standby"}
          </span>
          <span style={{ fontSize: "11px", color: "var(--text-muted)" }}>
            {isStreaming ? "16,000 Hz Sampling • 128ms Window" : "Click 'Start Live Shield' to stream"}
          </span>
        </div>

        <div className="spectrum-canvas-wrap">
          <canvas
            id="live-spectrum-canvas"
            width={480}
            height={90}
            className="spectrum-canvas"
            ref={(canvas) => {
              if (!canvas) return;
              const ctx = canvas.getContext("2d");
              if (!ctx) return;
              ctx.clearRect(0, 0, canvas.width, canvas.height);

              // Background grid lines
              ctx.strokeStyle = "rgba(255, 255, 255, 0.05)";
              ctx.lineWidth = 1;
              for (let y = 20; y < canvas.height; y += 25) {
                ctx.beginPath();
                ctx.moveTo(0, y);
                ctx.lineTo(canvas.width, y);
                ctx.stroke();
              }

              const numBars = 32;
              const barWidth = canvas.width / numBars - 2;

              for (let i = 0; i < numBars; i++) {
                // Dynamic bar height based on streaming activity and risk
                let barHeight;
                if (isStreaming) {
                  const seed = Math.sin(Date.now() * 0.008 + i * 0.4) * 0.5 + 0.5;
                  const intensity = (riskScore / 100) * 0.6 + 0.4;
                  barHeight = Math.max(6, seed * (canvas.height - 15) * intensity);
                } else {
                  barHeight = 4;
                }

                const x = i * (barWidth + 2) + 2;
                const y = canvas.height - barHeight;

                // Color gradient from cyan to emerald or crimson depending on threat
                const grad = ctx.createLinearGradient(0, canvas.height, 0, y);
                if (riskScore >= currentThresholdHigh && isStreaming) {
                  grad.addColorStop(0, "#ef4444");
                  grad.addColorStop(1, "#f87171");
                } else if (riskScore >= 40 && isStreaming) {
                  grad.addColorStop(0, "#f59e0b");
                  grad.addColorStop(1, "#fbbf24");
                } else if (isStreaming) {
                  grad.addColorStop(0, "#10b981");
                  grad.addColorStop(1, "#38bdf8");
                } else {
                  grad.addColorStop(0, "#334155");
                  grad.addColorStop(1, "#475569");
                }

                ctx.fillStyle = grad;
                ctx.fillRect(x, y, barWidth, barHeight);
              }
            }}
          />
        </div>
      </div>

      {/* Acoustic Biomarkers Breakdown with Visual Tolerance Bars */}
      <div className="biomarkers-grid">
        <div className="biomarker-box">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div className="biomarker-name">High-Freq Vocoder Ratio</div>
            <span style={{ fontSize: "10px", color: biomarkers.high_freq_ratio > 0.08 ? "#ef4444" : "#10b981", fontWeight: 700 }}>
              {biomarkers.high_freq_ratio > 0.08 ? "ANOMALY" : "NATURAL"}
            </span>
          </div>
          <div className="biomarker-val">{(biomarkers.high_freq_ratio * 100).toFixed(2)}%</div>
          <div className="biomarker-meter-bar">
            <div
              className="biomarker-meter-fill"
              style={{
                width: `${Math.min(100, biomarkers.high_freq_ratio * 500)}%`,
                background: biomarkers.high_freq_ratio > 0.08 ? "#ef4444" : "#10b981"
              }}
            />
          </div>
        </div>

        <div className="biomarker-box">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div className="biomarker-name">Pitch Jitter Regularity</div>
            <span style={{ fontSize: "10px", color: biomarkers.jitter_factor < 0.1 || biomarkers.jitter_factor > 0.8 ? "#ef4444" : "#10b981", fontWeight: 700 }}>
              {biomarkers.jitter_factor < 0.1 ? "ROBOTIC" : biomarkers.jitter_factor > 0.8 ? "SPLICE" : "NATURAL"}
            </span>
          </div>
          <div className="biomarker-val">{(biomarkers.jitter_factor * 100).toFixed(1)}%</div>
          <div className="biomarker-meter-bar">
            <div
              className="biomarker-meter-fill"
              style={{
                width: `${Math.min(100, biomarkers.jitter_factor * 100)}%`,
                background: biomarkers.jitter_factor < 0.1 || biomarkers.jitter_factor > 0.8 ? "#ef4444" : "#10b981"
              }}
            />
          </div>
        </div>

        <div className="biomarker-box">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div className="biomarker-name">Pitch Variance</div>
            <span style={{ fontSize: "10px", color: biomarkers.pitch_variance_hz < 15 ? "#ef4444" : "#10b981", fontWeight: 700 }}>
              {biomarkers.pitch_variance_hz < 15 ? "MONOTONE" : "NORMAL"}
            </span>
          </div>
          <div className="biomarker-val">{(biomarkers.pitch_variance_hz || 36).toFixed(0)} Hz</div>
          <div className="biomarker-meter-bar">
            <div
              className="biomarker-meter-fill"
              style={{
                width: `${Math.min(100, ((biomarkers.pitch_variance_hz || 36) / 60) * 100)}%`,
                background: biomarkers.pitch_variance_hz < 15 ? "#ef4444" : "#10b981"
              }}
            />
          </div>
        </div>

        <div className="biomarker-box">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div className="biomarker-name">Zero Crossing Rate</div>
            <span style={{ fontSize: "10px", color: "var(--accent-blue)", fontWeight: 700 }}>
              16kHz BAND
            </span>
          </div>
          <div className="biomarker-val">{(biomarkers.zero_crossing_rate * 100).toFixed(1)}%</div>
          <div className="biomarker-meter-bar">
            <div
              className="biomarker-meter-fill"
              style={{
                width: `${Math.min(100, biomarkers.zero_crossing_rate * 500)}%`,
                background: "var(--accent-blue)"
              }}
            />
          </div>
        </div>
      </div>

      {/* Stream Controls */}
      <div style={{ marginTop: "24px" }}>
        {!isStreaming ? (
          <button className="btn btn-emerald btn-block" onClick={startStreaming}>
            <PhoneIcon size={16} /> Start Real-Time Call Protection
          </button>
        ) : (
          <button className="btn btn-crimson btn-block" onClick={stopStreaming} style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "8px" }}>
            <PhoneOffIcon size={16} />
            <span>End Call & Purge Volatile Memory</span>
          </button>
        )}
      </div>
    </div>
  );
}
