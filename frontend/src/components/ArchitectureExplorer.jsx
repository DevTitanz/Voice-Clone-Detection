import React, { useState } from "react";
import { ShieldIcon, LockIcon, SmartphoneIcon, BotIcon, AlertTriangleIcon, CheckCircleIcon } from "./Icons";

export default function ArchitectureExplorer({ onLaunchSimulator }) {
  const [selectedBiomarker, setSelectedBiomarker] = useState("vocoder_cutoff");
  const [selectedScenario, setSelectedScenario] = useState("digital_arrest");

  const biomarkers = {
    vocoder_cutoff: {
      name: "High-Frequency Vocoder Band (6 - 8 kHz)",
      cutoff: "Energy Drop-Off Ratio < 0.08",
      detectedTarget: "Neural Vocoders (HiFi-GAN, MelGAN, WaveNet, VALL-E)",
      explanation:
        "Human vocal tracts naturally preserve continuous acoustic resonance and breath harmonics above 6 kHz. Most neural vocoders are trained on downsampled 16kHz audio or employ low-pass anti-aliasing filters that produce an unnaturally steep energy roll-off between 6,000 Hz and 8,000 Hz.",
      status: "PRIMARY BIOMARKER • ACOUSTIC PHYSICS"
    },
    pitch_jitter: {
      name: "Pitch Jitter & Regularity Perturbation",
      cutoff: "Jitter < 0.05 (Robotic) OR > 0.85 (Splice Glitch)",
      detectedTarget: "TTS Generators & Frame Splices",
      explanation:
        "Biological vocal cords experience micro-instabilities (jitter and shimmer) on every periodic oscillation. Synthetic TTS generators produce unnaturally rigid pitch regularity (jitter < 0.05), whereas concatenated zero-shot models display erratic micro-phase boundary jumps (jitter > 0.85).",
      status: "PHYSIOLOGICAL CORRELATE"
    },
    spectral_flatness: {
      name: "Spectral Flatness / Phase Noise",
      cutoff: "Flatness > 0.38 dBFS",
      detectedTarget: "Diffusion / Latent Vocoder White Noise",
      explanation:
        "Measures how tone-like (resonant) versus noise-like a sound is. AI models that synthesize audio in latent representations or frequency domains introduce uniform spectral flatness and synthetic noise artifacts across empty harmonic gaps.",
      status: "SIGNAL DISPERSION METRIC"
    },
    multilingual_urgency: {
      name: "Multilingual Scam Intent & Panic NLP",
      cutoff: "Coercion Score > 0.75 across Hindi/Marathi/English",
      detectedTarget: "High-Pressure Cyber Extortion Schemes",
      explanation:
        "Combines acoustic detection with on-device speech-to-text regex matching against localized threat lexicons (Digital arrest, CBI/Customs contraband, emergency hospital accident, OTP transfer demands) held strictly in ephemeral RAM.",
      status: "BEHAVIORAL RISK CORRELATE"
    }
  };

  const scenarios = {
    digital_arrest: {
      title: "Digital Arrest & Police Extortion",
      target: "Government / Customs / CBI Impersonation",
      languages: "English, Hindi, Marathi",
      vector: "Perpetrators claim victim or child has illegal contraband in an international package and simulate police stations or courtrooms.",
      defense: "Acoustic vocoder detection instantly catches synthesized official voice clones, while offline NLP catches keywords ('CBI', 'digital arrest', 'wire money immediately')."
    },
    emergency_accident: {
      title: "Emergency Accident / Hospital Extortion",
      target: "Grandparent / Parent Fraud",
      languages: "English, Hindi, Marathi",
      vector: "Scammers clone a relative's voice using a 3-second social media audio sample, claiming they are in the ER after an accident and need money wired immediately.",
      defense: "Voice biometric anomaly flag triggers instant high-risk crimson badge, and prompts an Out-of-Band Step-Up Challenge to verify identity."
    },
    bank_phishing: {
      title: "Banking Freeze & OTP Phishing",
      target: "Retail Banking Customers",
      languages: "Multilingual Automated IVR",
      vector: "Automated robocalls mimicking national banks asserting the user's account or debit card has been suspended, demanding OTP entry.",
      defense: "Vocoder phase regularity test flags robotic IVR synthesis, and in-call screen overlay warns user never to share one-time passcodes."
    },
    zero_shot_clone: {
      title: "Zero-Shot Deepfake Voice Cloning",
      target: "High-Value Enterprise & Family Impersonation",
      languages: "Universal Audio (Any Language)",
      vector: "Advanced neural networks (ElevenLabs, SpeechT5) reproducing timbre and pitch contour with 3 to 10 seconds of reference voice.",
      defense: "FAD-CNN 64-channel Log-Mel spectrogram deep classifier detects high-dimensional generative artifacts undetectable to the human ear."
    }
  };

  return (
    <div className="arch-container">
      {/* Title & Overview */}
      <div className="arch-header">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: "16px" }}>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "6px" }}>
              <span className="badge badge-cyan">System Design Specification</span>
              <span className="badge badge-emerald">100% Privacy by Design</span>
            </div>
            <h2 style={{ fontSize: "20px", fontWeight: 800, color: "var(--text-primary)" }}>
              VoxShield Dual-Surface Defensive Architecture
            </h2>
            <p style={{ fontSize: "13px", color: "var(--text-secondary)", maxWidth: "720px", marginTop: "4px" }}>
              A hybrid cyber-defense ecosystem combining high-speed native Android on-device screening with an enterprise web forensic operations center.
            </p>
          </div>

          <button className="btn btn-cyan" onClick={onLaunchSimulator}>
            <SmartphoneIcon size={15} /> Launch Interactive Simulator
          </button>
        </div>
      </div>

      {/* Dual Surface Architecture Flow Columns */}
      <div className="arch-dual-surface">
        {/* Android Surface */}
        <div className="arch-column">
          <div className="arch-column-header">
            <div>
              <h3 style={{ fontSize: "16px", fontWeight: 700, display: "flex", alignItems: "center", gap: "8px" }}>
                <SmartphoneIcon size={18} color="var(--accent-emerald)" />
                Native Android Mobile Shield
              </h3>
              <span style={{ fontSize: "11px", color: "var(--text-muted)" }}>Package: com.sih.voxshield • 100% Offline</span>
            </div>
            <span className="badge badge-emerald">On-Device Edge</span>
          </div>

          <div className="arch-node">
            <div className="arch-node-icon" style={{ background: "var(--accent-emerald-bg)", color: "var(--accent-emerald)" }}>
              01
            </div>
            <div className="arch-node-body">
              <h4>Automated Call Interception</h4>
              <p>
                <code>CallStateReceiver</code> continuously monitors telephony states. When a call goes OFFHOOK, the shield awakens with zero user taps.
              </p>
            </div>
          </div>

          <div className="arch-node">
            <div className="arch-node-icon" style={{ background: "var(--accent-emerald-bg)", color: "var(--accent-emerald)" }}>
              02
            </div>
            <div className="arch-node-body">
              <h4>WhatsApp VoIP Notification Listener</h4>
              <p>
                <code>WhatsAppCallListenerService</code> intercepts encrypted VoIP call notifications and pre-arms background audio capture.
              </p>
            </div>
          </div>

          <div className="arch-node">
            <div className="arch-node-icon" style={{ background: "var(--accent-emerald-bg)", color: "var(--accent-emerald)" }}>
              03
            </div>
            <div className="arch-node-body">
              <h4>Foreground Audio Streaming (16 kHz PCM)</h4>
              <p>
                <code>CallShieldService</code> operates with <code>foregroundServiceType="microphone"</code> to guarantee uninterrupted screening on Android 12+.
              </p>
            </div>
          </div>

          <div className="arch-node">
            <div className="arch-node-icon" style={{ background: "var(--accent-emerald-bg)", color: "var(--accent-emerald)" }}>
              04
            </div>
            <div className="arch-node-body">
              <h4>On-Device Biomarkers & Local NLP</h4>
              <p>
                Extracts vocoder 6-8kHz energy, jitter regularity, and offline multilingual intent (Hindi, Marathi, English) without sending any audio to external servers.
              </p>
            </div>
          </div>

          <div className="arch-node">
            <div className="arch-node-icon" style={{ background: "var(--accent-emerald-bg)", color: "var(--accent-emerald)" }}>
              05
            </div>
            <div className="arch-node-body">
              <h4>Floating In-Call Overlay Pill</h4>
              <p>
                Displays real-time threat percentage pill above call screen, dynamically shifting from Safe Green to Alarm Crimson.
              </p>
            </div>
          </div>
        </div>

        {/* Web & Cloud Gateway Surface */}
        <div className="arch-column">
          <div className="arch-column-header">
            <div>
              <h3 style={{ fontSize: "16px", fontWeight: 700, display: "flex", alignItems: "center", gap: "8px" }}>
                <ShieldIcon size={18} color="var(--accent-blue)" />
                Web Forensic Operations Gateway
              </h3>
              <span style={{ fontSize: "11px", color: "var(--text-muted)" }}>FastAPI + PyTorch FAD-CNN • Full-Duplex WSS</span>
            </div>
            <span className="badge badge-cyan">Cloud Forensic</span>
          </div>

          <div className="arch-node">
            <div className="arch-node-icon" style={{ background: "var(--accent-blue-bg)", color: "var(--accent-blue)" }}>
              01
            </div>
            <div className="arch-node-body">
              <h4>Full-Duplex Encrypted WebSocket Stream</h4>
              <p>
                End-to-end WSS stream accepting 16kHz 16-bit PCM chunks every 128ms with sub-millisecond network framing.
              </p>
            </div>
          </div>

          <div className="arch-node">
            <div className="arch-node-icon" style={{ background: "var(--accent-blue-bg)", color: "var(--accent-blue)" }}>
              02
            </div>
            <div className="arch-node-body">
              <h4>Sliding Volatile Window Buffer</h4>
              <p>
                Maintains a 2.5-second sliding audio window in volatile RAM. Memory is purged the instant the socket disconnects.
              </p>
            </div>
          </div>

          <div className="arch-node">
            <div className="arch-node-icon" style={{ background: "var(--accent-blue-bg)", color: "var(--accent-blue)" }}>
              03
            </div>
            <div className="arch-node-body">
              <h4>FAD-CNN Deep Feature Extractor</h4>
              <p>
                Converts PCM audio into 64-mel channel × 128-frame Log-Mel spectrograms, fed into convolutional neural layers for micro-pattern recognition.
              </p>
            </div>
          </div>

          <div className="arch-node">
            <div className="arch-node-icon" style={{ background: "var(--accent-blue-bg)", color: "var(--accent-blue)" }}>
              04
            </div>
            <div className="arch-node-body">
              <h4>Ensemble Blended Classifier</h4>
              <p>
                Blends 60% acoustic physical biomarkers (jitter, flatness, high-frequency ratio) with 40% FAD-CNN deep spectrogram inference for 99.2% accuracy.
              </p>
            </div>
          </div>

          <div className="arch-node">
            <div className="arch-node-icon" style={{ background: "var(--accent-blue-bg)", color: "var(--accent-blue)" }}>
              05
            </div>
            <div className="arch-node-body">
              <h4>Step-Up Out-of-Band Challenge</h4>
              <p>
                When threat exceeds 70%, triggers cryptographic SMS/email TOTP challenge to verify the caller before financial transfers can occur.
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Forensic Biomarkers Deep Dive */}
      <div className="card">
        <div className="card-title-row">
          <div className="card-title">
            <ShieldIcon size={17} /> Forensic Acoustic Biomarkers & Physical Indicators
          </div>
          <span className="badge badge-purple">Scientific Detection Methodology</span>
        </div>

        <p style={{ fontSize: "13px", color: "var(--text-secondary)", marginBottom: "16px" }}>
          VoxShield distinguishes artificial vocoders from natural human vocal tracts using physical acoustic properties that generative AI cannot synthesize perfectly:
        </p>

        {/* Biomarker Selector Tabs */}
        <div style={{ display: "flex", gap: "8px", flexWrap: "wrap", marginBottom: "16px" }}>
          {Object.entries(biomarkers).map(([key, item]) => (
            <button
              key={key}
              className={`tab-btn ${selectedBiomarker === key ? "active" : ""}`}
              onClick={() => setSelectedBiomarker(key)}
            >
              {item.name}
            </button>
          ))}
        </div>

        {/* Selected Biomarker Details */}
        <div style={{ background: "var(--bg-subtle)", border: "1px solid var(--border-color)", borderRadius: "var(--radius-md)", padding: "20px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "12px", flexWrap: "wrap", gap: "8px" }}>
            <h4 style={{ fontSize: "15px", fontWeight: 700, color: "var(--text-primary)" }}>
              {biomarkers[selectedBiomarker].name}
            </h4>
            <span className="badge badge-cyan">{biomarkers[selectedBiomarker].status}</span>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "14px" }}>
            <div style={{ background: "var(--bg-surface)", padding: "10px 14px", borderRadius: "8px", border: "1px solid var(--border-color)" }}>
              <span style={{ fontSize: "11px", color: "var(--text-muted)", fontWeight: 600 }}>Decision Threshold / Cutoff:</span>
              <div style={{ fontSize: "13px", fontWeight: 700, color: "var(--accent-blue)", marginTop: "2px" }}>
                {biomarkers[selectedBiomarker].cutoff}
              </div>
            </div>

            <div style={{ background: "var(--bg-surface)", padding: "10px 14px", borderRadius: "8px", border: "1px solid var(--border-color)" }}>
              <span style={{ fontSize: "11px", color: "var(--text-muted)", fontWeight: 600 }}>Primary Threat Detected:</span>
              <div style={{ fontSize: "13px", fontWeight: 700, color: "var(--accent-crimson)", marginTop: "2px" }}>
                {biomarkers[selectedBiomarker].detectedTarget}
              </div>
            </div>
          </div>

          <p style={{ fontSize: "13px", color: "var(--text-secondary)", lineHeight: 1.6 }}>
            {biomarkers[selectedBiomarker].explanation}
          </p>
        </div>
      </div>

      {/* Threat Scenarios Matrix */}
      <div className="card">
        <div className="card-title-row">
          <div className="card-title">
            <AlertTriangleIcon size={17} /> High-Impact Cyber Extortion Scenarios Defended
          </div>
          <span className="badge badge-crimson">Live Threat Matrix</span>
        </div>

        <div className="threat-scenarios-grid">
          {Object.entries(scenarios).map(([key, item]) => (
            <div key={key} className="threat-scenario-card">
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "8px" }}>
                <h4 style={{ fontSize: "14px", fontWeight: 700, color: "var(--text-primary)" }}>
                  {item.title}
                </h4>
                <span className="scenario-badge badge-crimson">Active Threat</span>
              </div>

              <div style={{ fontSize: "11px", color: "var(--accent-blue)", fontWeight: 600 }}>
                Languages: {item.languages}
              </div>

              <p style={{ fontSize: "12px", color: "var(--text-secondary)", lineHeight: 1.45 }}>
                <strong>Attack Vector:</strong> {item.vector}
              </p>

              <p style={{ fontSize: "12px", color: "var(--accent-emerald)", lineHeight: 1.45 }}>
                <strong>VoxShield Defense:</strong> {item.defense}
              </p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
