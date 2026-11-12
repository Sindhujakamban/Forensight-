import glob
import os
import re
import subprocess
import sys
import uuid
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

TRUFOR_DIR = Path("/home/santhosh/forensight-ai/TruFor")
PYTHON = "/home/santhosh/forensight-ai/.venv/bin/python"
TEST_SCRIPT = TRUFOR_DIR / "TruFor_train_test" / "test.py"
MODEL = TRUFOR_DIR / "pretrained_models" / "weights" / "trufor.pth.tar"

def fail(message, code=1):
    print("TRUFOR_ERROR:", message, flush=True)
    raise SystemExit(code)

def main():
    if len(sys.argv) not in (2, 3):
        fail("Usage: trufor_bridge.py <image_path> [evidence_id]")

    image_path = Path(sys.argv[1]).expanduser().resolve()
    evidence_id = sys.argv[2] if len(sys.argv) == 3 else image_path.stem
    if not image_path.is_file():
        fail(f"Image not found: {image_path}")
    if not TEST_SCRIPT.is_file():
        fail(f"TruFor test script not found: {TEST_SCRIPT}")
    if not MODEL.is_file():
        fail(f"TruFor model weights not found: {MODEL}")

    # Each run gets a unique output folder. This prevents an older NPZ
    # from a different image from being selected accidentally.
    safe_id = re.sub(r"[^A-Za-z0-9_.-]+", "_", evidence_id)[:80] or "evidence"
    output_dir = TRUFOR_DIR / "bridge_output" / safe_id / uuid.uuid4().hex
    output_dir.mkdir(parents=True, exist_ok=True)

    try:
        import torch
        gpu = "0" if torch.cuda.is_available() else "-1"
    except Exception:
        gpu = "-1"

    command = [
        PYTHON, str(TEST_SCRIPT),
        "-g", gpu,
        "-in", str(image_path),
        "-out", str(output_dir),
        "-exp", "trufor_ph3",
        "TEST.MODEL_FILE", str(MODEL),
    ]

    print("Running existing TruFor model...", flush=True)
    print("Image:", str(image_path), flush=True)
    print("GPU setting:", gpu, flush=True)

    try:
        result = subprocess.run(
            command,
            cwd=str(TRUFOR_DIR / "TruFor_train_test"),
            capture_output=True,
            text=True,
            timeout=900,
        )
    except subprocess.TimeoutExpired:
        fail("TruFor inference exceeded 15 minutes.")

    if result.stdout:
        print(result.stdout, end="", flush=True)
    if result.returncode != 0:
        if result.stderr:
            print(result.stderr, file=sys.stderr, flush=True)
        fail(f"TruFor test.py exited with code {result.returncode}.", result.returncode)

    npz_files = glob.glob(str(output_dir / "*.npz"))
    if len(npz_files) != 1:
        fail(f"Expected exactly one NPZ in this run folder, found {len(npz_files)}.")
    npz_file = Path(npz_files[0])

    with np.load(npz_file, allow_pickle=False) as data:
        required = {"score", "conf", "map"}
        missing = required.difference(data.files)
        if missing:
            fail("NPZ is missing required arrays: " + ", ".join(sorted(missing)))

        score = float(np.asarray(data["score"]).squeeze())
        conf_map = np.asarray(data["conf"], dtype=np.float32).squeeze()
        localization_map = np.asarray(data["map"], dtype=np.float32).squeeze()

    if not np.isfinite(score) or not (0.0 <= score <= 1.0):
        fail(f"Invalid TruFor image score: {score}")
    if localization_map.ndim != 2:
        fail(f"Expected a 2D localization map after squeeze; got {localization_map.shape}")
    if conf_map.ndim != 2:
        fail(f"Expected a 2D confidence map after squeeze; got {conf_map.shape}")
    if not np.isfinite(localization_map).all() or not np.isfinite(conf_map).all():
        fail("TruFor map contains NaN or infinite values.")

    confidence = float(np.mean(conf_map))
    heatmap_path = output_dir / "trufor_localization_map.png"
    confidence_path = output_dir / "trufor_confidence_map.png"

    # Matches the official TruFor visualizer: map with RdBu_r and [0,1] range;
    # confidence map in grayscale with [0,1] range.
    plt.imsave(str(heatmap_path), localization_map, cmap="RdBu_r", vmin=0.0, vmax=1.0)
    plt.imsave(str(confidence_path), conf_map, cmap="gray", vmin=0.0, vmax=1.0)

    print("Score:", format(score, ".10f"), flush=True)
    print("Confidence:", format(confidence, ".10f"), flush=True)
    print("Heatmap:", str(heatmap_path), flush=True)
    print("ConfidenceMap:", str(confidence_path), flush=True)
    print("NPZ:", str(npz_file), flush=True)
    print("Map shape:", localization_map.shape, flush=True)

if __name__ == "__main__":
    main()
