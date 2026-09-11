import pytest
import numpy as np
from app.services.fad_cnn_service import (
    fad_cnn_service,
    preprocess_audio_to_mel,
    extract_deep_acoustic_features,
    generate_smart_explanation,
    SpeechT5VoiceCloner,
    WEREvaluator
)
from app.services.detector import detector_service


def test_preprocess_audio_to_mel_shape():
    """Verify Mel-spectrogram output is exactly 64 bands by 128 frames."""
    data = np.random.randn(16000).astype(np.float32)
    mel = preprocess_audio_to_mel(data, sr=16000, n_mels=64, target_frames=128)
    assert mel.shape == (64, 128)
    assert mel.dtype == np.float32


def test_extract_deep_acoustic_features():
    """Verify deep acoustic features from pk9444/2xqcTCqAYvy0SJbK are extracted."""
    data = np.sin(2 * np.pi * 440 * np.linspace(0, 1, 16000)).astype(np.float32)
    feats = extract_deep_acoustic_features(data, sr=16000)

    assert "duration_sec" in feats
    assert "rms_db" in feats
    assert "silence_ratio" in feats
    assert "spectral_centroid_hz" in feats
    assert "spectral_spread_hz" in feats
    assert "mfcc_mean" in feats
    assert "mfcc_std" in feats
    assert len(feats["mfcc_mean"]) == 13
    assert len(feats["mfcc_std"]) == 13


def test_smart_explanation_generation():
    """Verify smart explanation generates meaningful text based on acoustic traits."""
    fake_feats = {
        "duration_sec": 2.0,
        "rms_db": -8.0,
        "silence_ratio": 0.05,
        "spectral_centroid_hz": 2400.0,
        "spectral_spread_hz": 800.0,
    }
    explanation = generate_smart_explanation(fake_feats, label="FAKE", prob=0.88)
    assert "FAKE" in explanation
    assert "88" in explanation
    assert "neural" in explanation.lower() or "vocoder" in explanation.lower() or "synthesis" in explanation.lower()


def test_fad_cnn_predict_buffer():
    """Verify end-to-end predict_audio_buffer produces all required output fields."""
    data = np.random.randn(16000).astype(np.float32)
    result = fad_cnn_service.predict_audio_buffer(data, sample_rate=16000)

    assert result["label"] in ["FAKE", "REAL"]
    assert 0.0 <= result["fake_probability"] <= 1.0
    assert len(result["mel_spectrogram"]) == 64
    assert len(result["mel_spectrogram"][0]) == 128
    assert len(result["waveform_preview"]) > 0
    assert len(result["smart_explanation"]) > 20


def test_detector_integration_with_fad_cnn():
    """Verify VoiceDeepfakeDetector produces blended risk and FAD-CNN diagnostics."""
    data = np.random.randn(16000).astype(np.float32)
    res = detector_service.analyze_audio_buffer(data, sample_rate=16000)

    assert "risk_score" in res
    assert "fad_cnn_prediction" in res
    assert "fad_cnn_prob" in res
    assert "mel_spectrogram" in res
    assert "smart_explanation" in res
    assert res["fad_cnn_prediction"] in ["FAKE", "REAL"]


def test_voice_cloner_and_wer():
    """Verify SpeechT5 voice cloner and WER evaluator interfaces."""
    cloner = SpeechT5VoiceCloner()
    clone_res = cloner.clone_voice("Hello world")
    assert clone_res["status"] == "success"

    evaluator = WEREvaluator()
    wer_res = evaluator.evaluate_relative_wer("this is authentic speech", "this is synthetic clone speech")
    assert "relative_wer" in wer_res
    assert 0.0 <= wer_res["relative_wer"] <= 1.0
