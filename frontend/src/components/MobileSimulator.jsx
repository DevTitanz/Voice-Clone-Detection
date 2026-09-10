import React, { useState, useEffect, useRef } from "react";
import { SmartphoneIcon, LockIcon, BotIcon, UserIcon, MicIcon, PhoneIcon, PhoneOffIcon, AlertTriangleIcon, GlobeIcon, CheckCircleIcon } from "./Icons";

// Multilingual scenario text corpora held strictly in ephemeral RAM
const SCENARIO_TRANSCRIPTS = {
  synthetic_fake_urgency: {
    mr: {
      text: "मी सीबीआय (CBI) क्राईम ब्रँचमधून बोलतोय. तुमच्या मुलाला पार्सलमध्ये बेकायदेशीर वस्तू सापडल्यामुळे डिजिटल अटक केली आहे. लगेच तातडीने ५०,००० रुपये पाठवा अन्यथा गुन्हा दाखल होईल.",
      keywords: ["सीबीआय", "डिजिटल अटक", "तातडीने", "पैसे पाठवा"],
      threat: "Digital Arrest / Extortion (मराठी)",
      emotion: "Panic / Forced Urgency"
    },
    hi: {
      text: "मैं सीबीआई हेडक्वार्टर से इंस्पेक्टर बोल रहा हूँ। आपके बेटे को डिजिटल अरेस्ट किया गया है। तुरंत पैसे ट्रांसफर करें और ओटीपी बताएं वरना एफआईआर दर्ज होगी।",
      keywords: ["सीबीआई", "डिजिटल अरेस्ट", "तुरंत पैसे", "ओटीपी"],
      threat: "Digital Arrest Fraud (हिन्दी)",
      emotion: "Panic / High-Pressure Coercion"
    },
    en: {
      text: "This is Inspector Sharma from Central Bureau of Investigation. Your son has been placed under digital arrest for narcotics contraband. Wire 50,000 rupees immediately or face non-bailable detention.",
      keywords: ["CBI", "digital arrest", "narcotics contraband", "wire immediately"],
      threat: "Digital Arrest / Contraband Extortion (EN)",
      emotion: "Panic / Forced Urgency"
    }
  },
  synthetic_monotone: {
    mr: {
      text: "प्रिय ग्राहक, संशयास्पद हालचालींमुळे तुमचे बँक खाते ब्लॉक करण्यात आले आहे. सेवा सुरळीत ठेवण्यासाठी तुमचा पासवर्ड व ओटीपी त्वरित सामायिक करा.",
      keywords: ["बँक खाते ब्लॉक", "ओटीपी", "पासवर्ड"],
      threat: "Banking Phishing / OTP Theft (मराठी)",
      emotion: "Monotone / Flat Affect"
    },
    hi: {
      text: "प्रिय ग्राहक, संदिग्ध लेनदेन के कारण आपका बैंक खाता ब्लॉक कर दिया गया है। जारी रखने हेतु अपना गुप्त ओटीपी व पासवर्ड तुरंत दर्ज करें।",
      keywords: ["बैंक खाता ब्लॉक", "ओटीपी", "पासवर्ड"],
      threat: "Banking Freeze Scam (हिन्दी)",
      emotion: "Monotone / Robotic Affect"
    },
    en: {
      text: "Automated alert: Your national bank account has been locked due to suspicious transactions. Please verify your OTP to keep access uninterrupted.",
      keywords: ["bank account locked", "verify your OTP"],
      threat: "Automated Banking Phishing (EN)",
      emotion: "Monotone / Flat Affect"
    }
  },
  asking_money_scam: {
    mr: {
      text: "काका, मी रोहन बोलतोय, माझा मोठा अपघात झाला आहे. हॉस्पिटलमध्ये ॲडमिट आहे. लगेच तातडीने ५०,००० पैसे पाठवा, डॉक्टर ऑपरेशन सुरू करत नाहीत. प्लीज लगेच मदत करा!",
      keywords: ["पैसे पाठवा", "तातडीने", "अपघात", "हॉस्पिटल"],
      threat: "Urgent Money Demand (मराठी)",
      emotion: "Panic / Extreme Urgency"
    },
    hi: {
      text: "पापा, मेरा भयानक एक्सीडेंट हो गया है। मुझे अस्पताल में 50,000 रुपये तुरंत चाहिए, प्लीज मुझे पैसे भेजो या ट्रांसफर करो, डॉक्टर पैसे मांग रहे हैं!",
      keywords: ["पैसे भेजो", "रुपये", "पैसे मांग", "तुरंत"],
      threat: "Urgent Money Demand (हिन्दी)",
      emotion: "Panic / High Pressure"
    },
    en: {
      text: "Dad, I had a terrible car accident. I am in the emergency room and need 50,000 rupees immediately. Please send me money right now! Wire the cash urgently.",
      keywords: ["send me money", "asking money", "wire cash", "immediately"],
      threat: "Urgent Money Demand (EN)",
      emotion: "Panic / Extreme Urgency"
    }
  },
  natural_human: {
    mr: {
      text: "नमस्कार काका, मी रोहन बोलतोय. उद्या दुपारी आपण कौटुंबिक जेवणासाठी भेटू शकतो का? घरचे सगळे मजेत आहेत, काळजी नसावी.",
      keywords: [],
      threat: null,
      emotion: "Calm / Conversational"
    },
    hi: {
      text: "नमस्ते भैया, कल दोपहर को क्या हम लंच के लिए मिल सकते हैं? घर पर सब कुशल मंगल हैं, बस यही पूछने के लिए कॉल किया था।",
      keywords: [],
      threat: null,
      emotion: "Calm / Warm"
    },
    en: {
      text: "Hey there, just checking in to see if we can catch up for coffee tomorrow afternoon. Hope everything is going well with the family!",
      keywords: [],
      threat: null,
      emotion: "Calm / Conversational"
    }
  }
};

export default function MobileSimulator({ token, onTriggerStepUp }) {
  const [callState, setCallState] = useState("active"); // 'active', 'ringing', 'standby'
  const [callerNumber, setCallerNumber] = useState("+91 98200 45678");
  const [selectedLanguage, setSelectedLanguage] = useState("mr"); // 'mr' (मराठी), 'hi' (हिन्दी), 'en' (English)
  const [riskScore, setRiskScore] = useState(14.0);
  const [classificationLabel, setClassificationLabel] = useState("Natural voice patterns detected");
  const [detectedEmotion, setDetectedEmotion] = useState("Calm / Conversational");
  const [emotionFlag, setEmotionFlag] = useState(null);
  const [statusText, setStatusText] = useState("On-Device Shield: Active (Auto-Started on Open)");
  const [showDialog, setShowDialog] = useState(false);
  const [highFreqRatio, setHighFreqRatio] = useState(0.04);
  const [pitchJitter, setPitchJitter] = useState(0.28);
  const [pitchVariance, setPitchVariance] = useState(38);
  const [simType, setSimType] = useState("synthetic_fake_urgency"); // 'synthetic_fake_urgency', 'synthetic_monotone', 'natural_human'
  
  // Ephemeral on-device speech transcript (RAM only, 0 disk storage)
  const [liveTranscript, setLiveTranscript] = useState("");
  const [matchedKeywords, setMatchedKeywords] = useState([]);
  const [scamThreatCategory, setScamThreatCategory] = useState(null);
  const [ramBytesUsed, setRamBytesUsed] = useState(0);
  const [memoryPurgeNotice, setMemoryPurgeNotice] = useState(false);

  const simIntervalRef = useRef(null);
  const autoAnswerTimerRef = useRef(null);

  // 100% On-Device Analysis Loop (Zero Network Transmission)
  const startOnDeviceMonitoring = () => {
    setCallState("active");
    setMemoryPurgeNotice(false);
    setStatusText("100% On-Device AI Shield Active • 0 Bytes Sent to Server");

    let tick = 0;
    if (simIntervalRef.current) clearInterval(simIntervalRef.current);

    simIntervalRef.current = setInterval(() => {
      tick++;

      const langData = SCENARIO_TRANSCRIPTS[simType]?.[selectedLanguage] || SCENARIO_TRANSCRIPTS[simType]?.["en"];
      const fullText = langData.text;
      // Stream characters gradually into ephemeral RAM
      const charLimit = Math.min(fullText.length, Math.max(25, tick * 18));
      const partialText = fullText.slice(0, charLimit);
      setLiveTranscript(partialText);
      setRamBytesUsed(new Blob([partialText]).size);

      if (simType === "synthetic_fake_urgency") {
        // High frequency vocoder artifacts + artificial panic/urgency with rigid monotone pitch
        const simHf = 0.19 + 0.04 * Math.sin(tick * 0.4);
        const simJitter = 0.03 + 0.01 * Math.cos(tick * 0.2); // Unnaturally robotic regularity
        const simPVar = 6 + Math.round(Math.abs(Math.sin(tick * 0.1)) * 3); // Unnaturally flat pitch variance despite shouting
        const calculatedRisk = Math.min(96, Math.max(82, Math.round(simHf * 320 + (0.4 - simJitter) * 60 + 20)));

        setHighFreqRatio(simHf);
        setPitchJitter(simJitter);
        setPitchVariance(simPVar);
        setRiskScore(calculatedRisk);
        setDetectedEmotion(langData.emotion || "Panic / Forced Urgency");
        setEmotionFlag("Fake Urgency Detected: Flat pitch variance with forced urgency shouting");
        setClassificationLabel("AI Voice Scam Suspected (Fake Emotion)");
        setMatchedKeywords(langData.keywords);
        setScamThreatCategory(langData.threat);

        if (calculatedRisk >= 70 && tick > 3) {
          setShowDialog(true);
        }
      } else if (simType === "asking_money_scam") {
        // High panic extortion with urgent money demand (Grandparent / Emergency money scam)
        const simHf = 0.17 + 0.03 * Math.sin(tick * 0.3);
        const simJitter = 0.04 + 0.01 * Math.cos(tick * 0.2);
        const simPVar = 7 + Math.round(Math.abs(Math.sin(tick * 0.15)) * 3);
        const calculatedRisk = Math.min(98, Math.max(86, Math.round(simHf * 330 + 35)));

        setHighFreqRatio(simHf);
        setPitchJitter(simJitter);
        setPitchVariance(simPVar);
        setRiskScore(calculatedRisk);
        setDetectedEmotion(langData.emotion || "Panic / Extreme Urgency");
        setEmotionFlag("Scam Coercion Detected: Emergency money demand with AI vocal signature");
        setClassificationLabel("AI Voice Scam Suspected (Money Extortion)");
        setMatchedKeywords(langData.keywords);
        setScamThreatCategory(langData.threat);

        if (calculatedRisk >= 70 && tick > 3) {
          setShowDialog(true);
        }
      } else if (simType === "synthetic_monotone") {
        // Standard robotic TTS flat affect
        const simHf = 0.15 + 0.03 * Math.sin(tick * 0.3);
        const simJitter = 0.02 + 0.01 * Math.cos(tick * 0.3);
        const simPVar = 8 + Math.round(Math.abs(Math.sin(tick * 0.2)) * 4);
        const calculatedRisk = Math.min(88, Math.max(74, Math.round(simHf * 300 + (0.4 - simJitter) * 55 + 15)));

        setHighFreqRatio(simHf);
        setPitchJitter(simJitter);
        setPitchVariance(simPVar);
        setRiskScore(calculatedRisk);
        setDetectedEmotion(langData.emotion || "Monotone / Flat Affect");
        setEmotionFlag("Synthetic Emotional Disconnect: Flat prosody with neural vocoder noise");
        setClassificationLabel("AI-generated voice suspected");
        setMatchedKeywords(langData.keywords);
        setScamThreatCategory(langData.threat);
      } else {
        // Natural human voice patterns with healthy organic vocal micro-tremors and natural pitch variance
        const simHf = 0.03 + 0.01 * Math.sin(tick * 0.3);
        const simJitter = 0.26 + 0.05 * Math.sin(tick * 0.5);
        const simPVar = 36 + Math.round(Math.abs(Math.sin(tick * 0.3)) * 14); // Natural human pitch variance ~36-50 Hz
        const calculatedRisk = Math.min(28, Math.max(8, Math.round(simHf * 140 + simJitter * 20)));

        setHighFreqRatio(simHf);
        setPitchJitter(simJitter);
        setPitchVariance(simPVar);
        setRiskScore(calculatedRisk);
        setDetectedEmotion(langData.emotion || "Calm / Conversational");
        setEmotionFlag(null);
        setClassificationLabel("Natural voice patterns detected");
        setMatchedKeywords([]);
        setScamThreatCategory(null);
      }
    }, 250);
  };

  const stopOnDeviceMonitoring = () => {
    setCallState("standby");
    if (simIntervalRef.current) {
      clearInterval(simIntervalRef.current);
      simIntervalRef.current = null;
    }
    if (autoAnswerTimerRef.current) {
      clearTimeout(autoAnswerTimerRef.current);
      autoAnswerTimerRef.current = null;
    }
    // STRICT MEMORY PURGE: Erase volatile transcript completely
    setLiveTranscript("");
    setMatchedKeywords([]);
    setScamThreatCategory(null);
    setRamBytesUsed(0);
    setMemoryPurgeNotice(true);

    setStatusText("Call Ended • On-Device Volatile RAM Purged (0 Bytes Stored)");
    setRiskScore(14.0);
    setClassificationLabel("Natural voice patterns detected");
    setEmotionFlag(null);
  };

  // Trigger Incoming Call Simulation:
  // Ringing -> VoiceGuard AI automatically detects incoming call -> answers and starts processing
  const triggerIncomingCall = (phone = "+91 98200 45678") => {
    stopOnDeviceMonitoring();
    setCallerNumber(phone);
    setCallState("ringing");
    setMemoryPurgeNotice(false);

    // Auto-answer after 2 seconds to simulate real-world incoming call activation
    autoAnswerTimerRef.current = setTimeout(() => {
      startOnDeviceMonitoring();
    }, 2200);
  };

  // AUTO-START ON OPEN: As requested, the app immediately starts processing upon opening/mounting
  useEffect(() => {
    startOnDeviceMonitoring();
    return () => {
      if (simIntervalRef.current) clearInterval(simIntervalRef.current);
      if (autoAnswerTimerRef.current) clearTimeout(autoAnswerTimerRef.current);
    };
  }, [simType, selectedLanguage]);

  return (
    <div className="card" style={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
      <div className="card-title-row" style={{ width: "100%" }}>
        <div className="card-title">
          <SmartphoneIcon size={18} /> Android Mobile App: 100% On-Device Live Call Processing
        </div>
        <span className="badge badge-emerald">On-Device AI Engine Active</span>
      </div>

      <div style={{ padding: "10px 16px", background: "rgba(16, 185, 129, 0.1)", borderRadius: "10px", border: "1px solid rgba(16, 185, 129, 0.3)", marginBottom: "16px", maxWidth: "680px", textAlign: "center", fontSize: "13px", color: "var(--accent-emerald)", display: "flex", alignItems: "center", justifyContent: "center", gap: "8px" }}>
        <LockIcon size={16} />
        <span>
          <strong>Air-Gapped Audio & Text Privacy:</strong> Audio analysis and multilingual STT run <strong>strictly on device CPU</strong>. Transcripts are stored in volatile RAM during the call and <strong>instantly purged on call end (0 bytes written to disk)</strong>.
        </span>
      </div>

      {/* Incoming Call Simulation Banner */}
      <div style={{ display: "flex", gap: "8px", marginBottom: "12px", maxWidth: "560px", width: "100%" }}>
        <button
          className="btn btn-outline"
          style={{ flex: 1, padding: "8px 12px", fontSize: "11px", display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "6px", borderColor: "var(--accent-emerald)", color: "var(--accent-emerald)" }}
          onClick={() => triggerIncomingCall("+91 98200 45678")}
        >
          <PhoneIcon size={13} />
          <span>Simulate Incoming Call (+91 98200 45678)</span>
        </button>
      </div>

      {/* Simulator Scenario Selector */}
      <div style={{ display: "flex", gap: "8px", marginBottom: "16px", maxWidth: "560px", width: "100%" }}>
        <button
          className={`btn ${simType === "synthetic_fake_urgency" ? "btn-crimson" : "btn-outline"}`}
          style={{ flex: 1, padding: "8px 6px", fontSize: "11px", display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "4px" }}
          onClick={() => { setSimType("synthetic_fake_urgency"); }}
        >
          <AlertTriangleIcon size={13} />
          <span>Digital Arrest</span>
        </button>
        <button
          className={`btn ${simType === "asking_money_scam" ? "btn-crimson" : "btn-outline"}`}
          style={{ flex: 1, padding: "8px 6px", fontSize: "11px", display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "4px" }}
          onClick={() => { setSimType("asking_money_scam"); }}
        >
          <AlertTriangleIcon size={13} />
          <span>Asking Money Scam</span>
        </button>
        <button
          className={`btn ${simType === "synthetic_monotone" ? "btn-crimson" : "btn-outline"}`}
          style={{ flex: 1, padding: "8px 6px", fontSize: "11px", display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "4px" }}
          onClick={() => { setSimType("synthetic_monotone"); }}
        >
          <BotIcon size={13} />
          <span>AI TTS Monotone</span>
        </button>
        <button
          className={`btn ${simType === "natural_human" ? "btn-emerald" : "btn-outline"}`}
          style={{ flex: 1, padding: "8px 6px", fontSize: "11px", display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "4px" }}
          onClick={() => { setSimType("natural_human"); }}
        >
          <UserIcon size={13} />
          <span>Natural Human</span>
        </button>
      </div>


      {/* Smartphone Device Frame */}
      <div className="smartphone-frame">
        <div className="smartphone-notch" />

        <div className="smartphone-screen" style={{ display: "flex", flexDirection: "column", justifyContent: "space-between", padding: "16px" }}>
          {/* Minimal Header with Multilingual Language Tabs */}
          <div>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                <span className="pulse-dot" style={{ color: callState === "active" ? (riskScore >= 70 ? "#ef4444" : "#10b981") : (callState === "ringing" ? "#f59e0b" : "#94a3b8"), width: "8px", height: "8px" }} />
                <span style={{ fontSize: "12px", fontWeight: 700, color: "var(--text-primary)" }}>VoiceGuard</span>
              </div>

              {/* Language Selection Pills */}
              <div style={{ display: "flex", background: "var(--bg-secondary)", borderRadius: "8px", padding: "2px", gap: "2px" }}>
                {[
                  { code: "mr", label: "मराठी" },
                  { code: "hi", label: "हिन्दी" },
                  { code: "en", label: "EN" }
                ].map((lang) => (
                  <button
                    key={lang.code}
                    onClick={() => setSelectedLanguage(lang.code)}
                    style={{
                      border: "none",
                      background: selectedLanguage === lang.code ? "var(--bg-surface)" : "transparent",
                      color: selectedLanguage === lang.code ? "var(--text-primary)" : "var(--text-muted)",
                      fontWeight: selectedLanguage === lang.code ? 700 : 500,
                      fontSize: "9px",
                      padding: "2px 6px",
                      borderRadius: "6px",
                      cursor: "pointer",
                      boxShadow: selectedLanguage === lang.code ? "0 1px 3px rgba(0,0,0,0.08)" : "none"
                    }}
                  >
                    {lang.label}
                  </button>
                ))}
              </div>
            </div>

            <div style={{ fontSize: "10px", color: "var(--text-muted)", marginTop: "4px", display: "flex", justifyContent: "space-between" }}>
              <span>
                {callState === "active" ? `Screening: ${callerNumber}` : (callState === "ringing" ? "Incoming Call Ringing..." : "Call Shield Standby")}
              </span>
              <span>100% On-Device</span>
            </div>
          </div>

          {/* STATE 1: RINGING STATE (Phone is ringing, app auto-starts) */}
          {callState === "ringing" && (
            <div
              style={{
                background: "rgba(245, 158, 11, 0.08)",
                border: "1.5px solid rgba(245, 158, 11, 0.4)",
                borderRadius: "16px",
                padding: "20px 14px",
                textAlign: "center",
                margin: "12px 0"
              }}
            >
              <div style={{ display: "inline-block", padding: "8px", background: "#fef3c7", borderRadius: "50%", marginBottom: "8px" }}>
                <PhoneIcon size={24} className="pulse-dot" style={{ color: "#d97706" }} />
              </div>
              <div style={{ fontSize: "15px", fontWeight: 800, color: "var(--text-primary)", marginBottom: "2px" }}>
                {callerNumber}
              </div>
              <div style={{ fontSize: "11px", color: "var(--text-muted)", marginBottom: "14px" }}>
                Incoming Call • Telephony Detector Triggered
              </div>
              <div style={{ fontSize: "11px", fontWeight: 600, color: "#d97706", background: "rgba(245, 158, 11, 0.15)", padding: "6px 10px", borderRadius: "8px", marginBottom: "16px" }}>
                Auto-starting AI Shield upon answer...
              </div>

              <div style={{ display: "flex", gap: "8px" }}>
                <button
                  className="btn btn-emerald"
                  style={{ flex: 1, padding: "8px", fontSize: "11px", borderRadius: "8px" }}
                  onClick={startOnDeviceMonitoring}
                >
                  Answer Call
                </button>
                <button
                  className="btn btn-outline"
                  style={{ flex: 1, padding: "8px", fontSize: "11px", borderRadius: "8px" }}
                  onClick={stopOnDeviceMonitoring}
                >
                  Decline
                </button>
              </div>
            </div>
          )}

          {/* STATE 2: ACTIVE CALL SCREENING STATE */}
          {callState === "active" && (
            <div
              style={{
                background: riskScore >= 70 ? "rgba(239, 68, 68, 0.06)" : "rgba(16, 185, 129, 0.06)",
                border: `1.5px solid ${riskScore >= 70 ? "rgba(239, 68, 68, 0.3)" : "rgba(16, 185, 129, 0.3)"}`,
                borderRadius: "16px",
                padding: "14px 10px",
                textAlign: "center",
                margin: "8px 0"
              }}
            >
              <div style={{ fontSize: "13px", fontWeight: 800, color: riskScore >= 70 ? "#dc2626" : "#059669", marginBottom: "2px" }}>
                {riskScore >= 70 ? "AI Voice Scam Suspected" : "Caller Verified Safe"}
              </div>
              <p style={{ fontSize: "10px", color: "var(--text-secondary)", marginBottom: "10px" }}>
                {riskScore >= 70 ? "Do not send funds or share OTP" : "Natural human speech verified"}
              </p>

              {/* Clean Gauge Circle */}
              <div
                style={{
                  width: "90px",
                  height: "90px",
                  borderRadius: "50%",
                  background: "var(--bg-surface)",
                  border: `3px solid ${riskScore >= 70 ? "#ef4444" : "#10b981"}`,
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  justifyContent: "center",
                  margin: "0 auto 8px",
                  boxShadow: "0 2px 8px rgba(0, 0, 0, 0.04)"
                }}
              >
                <div style={{ fontSize: "24px", fontWeight: 900, color: riskScore >= 70 ? "#ef4444" : "#10b981" }}>
                  {Math.round(riskScore)}%
                </div>
                <div style={{ fontSize: "7px", letterSpacing: "1px", color: "var(--text-muted)", fontWeight: 700 }}>
                  AI RISK
                </div>
              </div>

              {/* Emotion & Threat Pill */}
              <div
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "4px",
                  padding: "3px 8px",
                  borderRadius: "10px",
                  fontSize: "9px",
                  fontWeight: 700,
                  background: emotionFlag ? "rgba(239, 68, 68, 0.15)" : "rgba(100, 116, 139, 0.1)",
                  color: emotionFlag ? "#dc2626" : "var(--text-primary)",
                  marginBottom: "8px"
                }}
              >
                {emotionFlag && <AlertTriangleIcon size={11} />}
                <span>{emotionFlag ? "Flag: Fake Urgency Detected" : `Tone: ${detectedEmotion}`}</span>
              </div>

              {/* DEDICATED ON-SCREEN VOICE TO TEXT DISPLAY */}
              <div
                style={{
                  background: scamThreatCategory ? "#fef2f2" : "var(--bg-surface)",
                  borderRadius: "10px",
                  padding: "10px",
                  border: `1.5px solid ${scamThreatCategory ? "#f87171" : "var(--border-color)"}`,
                  textAlign: "left",
                  marginTop: "8px"
                }}
              >
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "6px" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: "5px" }}>
                    <span className="pulse-dot" style={{ width: "6px", height: "6px", color: scamThreatCategory ? "#dc2626" : "#10b981" }} />
                    <span style={{ fontSize: "9px", fontWeight: 800, color: "var(--text-primary)", letterSpacing: "0.5px" }}>
                      ON-SCREEN VOICE TO TEXT
                    </span>
                  </div>
                  <span
                    style={{
                      fontSize: "8px",
                      fontWeight: 800,
                      color: scamThreatCategory ? "#b91c1c" : (liveTranscript ? "#047857" : "var(--text-muted)"),
                      background: scamThreatCategory ? "#fee2e2" : (liveTranscript ? "#dcfce7" : "rgba(100,116,139,0.1)"),
                      padding: "1px 6px",
                      borderRadius: "4px"
                    }}
                  >
                    {scamThreatCategory ? `FLAGGED: ${scamThreatCategory}` : (liveTranscript ? "SAFE" : "READY")}
                  </span>
                </div>

                <div
                  style={{
                    fontSize: "11px",
                    fontWeight: liveTranscript ? 700 : 400,
                    color: scamThreatCategory ? "#991b1b" : (liveTranscript ? "var(--text-primary)" : "var(--text-muted)"),
                    lineHeight: "1.4",
                    minHeight: "32px",
                    padding: "6px 8px",
                    background: scamThreatCategory ? "#ffffff" : "var(--bg-secondary)",
                    borderRadius: "6px",
                    border: "1px solid var(--border-color)"
                  }}
                >
                  {liveTranscript ? `"${liveTranscript}"` : "Waiting for speech... Speak or click a voice preset below to see on-screen speech to text."}
                </div>

                {/* Localized Scam Intent Tags */}
                {matchedKeywords.length > 0 && (
                  <div style={{ display: "flex", flexWrap: "wrap", gap: "3px", marginTop: "6px" }}>
                    {matchedKeywords.map((kw, idx) => (
                      <span
                        key={idx}
                        style={{
                          fontSize: "8px",
                          fontWeight: 700,
                          background: "#fee2e2",
                          color: "#b91c1c",
                          padding: "1px 5px",
                          borderRadius: "4px"
                        }}
                      >
                        {kw}
                      </span>
                    ))}
                  </div>
                )}

                {/* Quick Interactive Voice Testing Presets */}
                <div style={{ marginTop: "8px" }}>
                  <div style={{ fontSize: "8px", fontWeight: 700, color: "var(--text-muted)", marginBottom: "4px" }}>
                    INSTANT VOICE TESTING PRESETS:
                  </div>
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "4px" }}>
                    <button
                      className="btn"
                      style={{ padding: "4px", fontSize: "9px", background: "#fee2e2", color: "#991b1b", border: "1px solid #fca5a5", borderRadius: "6px", fontWeight: 700 }}
                      onClick={() => {
                        const txt = "Please give me money send 50000 rupees immediately";
                        setLiveTranscript(txt);
                        setRamBytesUsed(new Blob([txt]).size);
                        setScamThreatCategory("Urgent Money Demand");
                        setMatchedKeywords(["money", "send money", "rupees", "immediately"]);
                        setRiskScore(96);
                        setDetectedEmotion("Urgent / Coercion");
                        setEmotionFlag("Fake Urgency Detected: Emergency money demand");
                        setClassificationLabel("AI Voice Scam Suspected (Money Demand)");
                        setShowDialog(true);
                      }}
                    >
                      Ask Money (50k Rs)
                    </button>
                    <button
                      className="btn"
                      style={{ padding: "4px", fontSize: "9px", background: "#fef3c7", color: "#92400e", border: "1px solid #fcd34d", borderRadius: "6px", fontWeight: 700 }}
                      onClick={() => {
                        const txt = "लगेच तातडीने खात्यात ५०,००० पैसे पाठवा";
                        setLiveTranscript(txt);
                        setRamBytesUsed(new Blob([txt]).size);
                        setScamThreatCategory("Urgent Money Demand");
                        setMatchedKeywords(["पैसे", "पैसे पाठवा", "लगेच", "तातडीने"]);
                        setRiskScore(95);
                        setDetectedEmotion("High Urgency");
                        setEmotionFlag("Fake Urgency Detected: Marathi money demand");
                        setClassificationLabel("AI Voice Scam Suspected (Money Demand)");
                        setShowDialog(true);
                      }}
                    >
                      पैसे पाठवा (मराठी)
                    </button>
                    <button
                      className="btn"
                      style={{ padding: "4px", fontSize: "9px", background: "#dcfce7", color: "#166534", border: "1px solid #86efac", borderRadius: "6px", fontWeight: 700 }}
                      onClick={() => {
                        const txt = "Hello, how are you? Let us meet tomorrow at 10 AM.";
                        setLiveTranscript(txt);
                        setRamBytesUsed(new Blob([txt]).size);
                        setScamThreatCategory(null);
                        setMatchedKeywords([]);
                        setRiskScore(14);
                        setDetectedEmotion("Calm / Conversational");
                        setEmotionFlag(null);
                        setClassificationLabel("Caller Verified Safe (Natural Speech)");
                      }}
                    >
                      Safe Voice (14%)
                    </button>
                    <button
                      className="btn"
                      style={{ padding: "4px", fontSize: "9px", background: "var(--bg-secondary)", color: "var(--text-secondary)", border: "1px solid var(--border-color)", borderRadius: "6px", fontWeight: 600 }}
                      onClick={() => {
                        setLiveTranscript("");
                        setRamBytesUsed(0);
                        setScamThreatCategory(null);
                        setMatchedKeywords([]);
                        setRiskScore(14);
                        setEmotionFlag(null);
                        setClassificationLabel("Natural voice patterns detected");
                      }}
                    >
                      Clear RAM (0 B)
                    </button>
                  </div>
                </div>
              </div>

              {/* Step-up challenge button if high risk */}
              {riskScore >= 70 && (
                <div style={{ marginTop: "8px" }}>
                  <button
                    className="btn btn-crimson"
                    style={{ width: "100%", padding: "6px", fontSize: "10px", borderRadius: "8px" }}
                    onClick={() => onTriggerStepUp("mobile_live_alert")}
                  >
                    Verify Caller with OTP
                  </button>
                </div>
              )}
            </div>
          )}

          {/* STATE 3: STANDBY STATE (Waiting for incoming call) */}
          {callState === "standby" && (
            <div
              style={{
                background: "var(--bg-surface)",
                border: "1px dashed var(--border-color)",
                borderRadius: "16px",
                padding: "24px 14px",
                textAlign: "center",
                margin: "12px 0"
              }}
            >
              <div style={{ display: "inline-block", padding: "10px", background: "rgba(16, 185, 129, 0.1)", borderRadius: "50%", marginBottom: "10px" }}>
                <LockIcon size={22} style={{ color: "#10b981" }} />
              </div>
              <div style={{ fontSize: "14px", fontWeight: 700, color: "var(--text-primary)", marginBottom: "4px" }}>
                Shield Standing By
              </div>
              <p style={{ fontSize: "11px", color: "var(--text-secondary)", lineHeight: "1.4", marginBottom: "12px" }}>
                VoiceGuard automatically detects incoming phone calls and starts screening without opening or tapping.
              </p>

              {/* Volatile Memory Purged Indicator */}
              {memoryPurgeNotice && (
                <div
                  style={{
                    background: "rgba(16, 185, 129, 0.1)",
                    border: "1px solid rgba(16, 185, 129, 0.3)",
                    borderRadius: "8px",
                    padding: "6px 8px",
                    fontSize: "9px",
                    color: "#059669",
                    fontWeight: 600,
                    margin: "8px 0",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: "4px"
                  }}
                >
                  <CheckCircleIcon size={12} />
                  <span>Volatile RAM Purged (0 Bytes Stored)</span>
                </div>
              )}
            </div>
          )}

          {/* Minimal Bottom Actions */}
          <div>
            {callState === "active" ? (
              <button
                className="btn btn-crimson btn-block"
                style={{ borderRadius: "12px", padding: "12px", fontSize: "12px", display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "6px" }}
                onClick={stopOnDeviceMonitoring}
              >
                <PhoneOffIcon size={14} />
                <span>End Call Protection</span>
              </button>
            ) : (
              <button
                className="btn btn-emerald btn-block"
                style={{ borderRadius: "12px", padding: "12px", fontSize: "12px", display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "6px" }}
                onClick={() => triggerIncomingCall("+91 98200 45678")}
              >
                <PhoneIcon size={14} />
                <span>Simulate Incoming Call</span>
              </button>
            )}

            <div style={{ fontSize: "9px", color: "var(--text-muted)", textAlign: "center", marginTop: "8px" }}>
              Auto-activates on incoming phone calls • 100% on-device CPU
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

