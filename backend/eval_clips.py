import glob, os
import numpy as np
import soundfile as sf
from transformers import pipeline

pipe = pipeline("audio-classification",
                model="garystafford/wav2vec2-deepfake-voice-detector")

def fake_prob(path):
    data, sr = sf.read(path, dtype="float32")
    if data.ndim > 1:
        data = data.mean(axis=1)
    out = pipe({"raw": data, "sampling_rate": sr})
    p = 0.0
    for o in out:
        lab = o["label"].lower()
        if lab == "fake":
            return float(o["score"])
        if lab == "real":
            p = 1.0 - float(o["score"])
    return p

scores = {}
for label in ("real", "fake"):
    files = []
    for e in ("*.wav", "*.ogg", "*.flac", "*.mp3"):
        files += glob.glob(os.path.join("clips", label, e))
    for f in files:
        try:
            scores[(label, f)] = fake_prob(f)
            print(label, os.path.basename(f), round(scores[(label, f)], 3))
        except Exception as ex:
            print("SKIP", f, ex)

real = [s for (l, f), s in scores.items() if l == "real"]
fake = [s for (l, f), s in scores.items() if l == "fake"]
print("\nclips:", len(real), "real,", len(fake), "fake")
if real and fake:
    best = None
    for t in np.arange(0.01, 1.0, 0.01):
        acc = (sum(s < t for s in real) + sum(s >= t for s in fake)) / (len(real) + len(fake))
        if best is None or acc > best[1]:
            best = (round(float(t), 2), acc)
    print("best threshold:", best[0], "accuracy:", round(best[1] * 100, 1), "%")
    print("real max:", round(max(real), 3), "| fake min:", round(min(fake), 3))
