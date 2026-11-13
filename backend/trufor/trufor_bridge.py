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

TRUFOR_DIR = Path(os.environ.get("TRUFOR_ROOT", "/opt/trufor")).resolve()
PYTHON = os.environ.get("TRUFOR_PYTHON", sys.executable)
TEST_SCRIPT = TRUFOR_DIR / "src" / "trufor_test.py"
MODEL = TRUFOR_DIR / "weights" / "trufor.pth.tar"
OUTPUT_ROOT = Path(os.environ.get("TRUFOR_OUTPUT_DIR", "uploads/trufor")).resolve()
TIMEOUT_SECONDS = 900


def fail(message, code=1):
    print("TRUFOR_ERROR:", message, flush=True)
    raise SystemExit(code)


def main():
    if len(sys.argv) not in (2, 3):
        fail("Usage: trufor_bridge.py <image_path> [evidence_id]")

    image_path = Path(sys.argv[1]).expanduser().resolve()
    evidence_id = sys.argv[2] if len(sys.argv) == 3 else image_path.stem

    if not image_path.is_file():
        fail(f"Evidence image not found: {image_path}")
    if not TEST_SCRIPT.is_file():
        fail(f"Official TruFor inference script not found: {TEST_SCRIPT}")
    if not MODEL.is_file():
        fail(f"TruFor pretrained weights not found: {MODEL}")

    safe_id = re.sub(r"[^A-Za-z0-9_.-]+", "_", evidence_id)[:80] or "evidence"
    output_dir = OUTPUT_ROOT / safe_id / uuid.uuid4().hex
    output_dir.mkdir(parents=True, exist_ok=True)

    command = [
        PYTHON,
        str(TEST_SCRIPT),
        "-gpu", "-1",
        "-in", str(image_path),
        "-out", str(output_dir),
        "TEST.MODEL_FILE", str(MODEL),
    ]

    env = os.environ.copy()
    env["OMP_NUM_THREADS"] = "1"
    env["MKL_NUM_THREADS"] = "1"
    env["OPENBLAS_NUM_THREADS"] = "1"
    env["PYTHONUNBUFFERED"] = "1"

    print("Running official GRIP-UNINA TruFor inference on CPU.", flush=True)
    print("Image:", str(image_path), flush=True)
    print("Model:", str(MODEL), flush=True)

    try:
        result = subprocess.run(
            command,
            cwd=str(TRUFOR_DIR / "src"),
            capture_output=True,
            text=True,
            timeout=TIMEOUT_SECONDS,
            env=env,
        )
    except subprocess.TimeoutExpired:
        fail(f"TruFor inference exceeded {TIMEOUT_SECONDS} seconds.")
    except OSError as exc:
        fail(f"Could not launch the TruFor Python runtime: {exc}")

    if result.stdout:
        print(result.stdout, end="", flush=True)
    if result.stderr:
        print(result.stderr, file=sys.stderr, end="", flush=True)
    if result.returncode != 0:
        fail(f"Official TruFor inference exited with code {result.returncode}.")

    npz_files = glob.glob(str(output_dir / "**" / "*.npz"), recursive=True)
    if len(npz_files) != 1:
        fail(
            "TruFor did not create exactly one result NPZ. "
            f"Found {len(npz_files)} file(s). Review the inference log above."
        )
    npz_file = Path(npz_files[0])

    try:
        with np.load(npz_file, allow_pickle=False) as data:
            required = {"score", "conf", "map"}
            missing = required.difference(data.files)
            if missing:
                fail("TruFor NPZ is missing arrays: " + ", ".join(sorted(missing)))

            score = float(np.asarray(data["score"]).squeeze())
            confidence_map = np.asarray(data["conf"], dtype=np.float32).squeeze()
            localization_map = np.asarray(data["map"], dtype=np.float32).squeeze()
    except (OSError, ValueError, KeyError) as exc:
        fail(f"Unable to read the TruFor result NPZ: {exc}")

    if not np.isfinite(score) or not (0.0 <= score <= 1.0):
        fail(f"Invalid TruFor image score: {score}")
    if localization_map.ndim != 2:
        fail(f"Expected a 2D localization map; received {localization_map.shape}.")
    if confidence_map.ndim != 2:
        fail(f"Expected a 2D confidence map; received {confidence_map.shape}.")
    if not np.isfinite(localization_map).all() or not np.isfinite(confidence_map).all():
        fail("TruFor output maps contain NaN or infinite values.")

    confidence = float(np.mean(confidence_map))
    heatmap_path = output_dir / "trufor_localization_map.png"
    confidence_path = output_dir / "trufor_confidence_map.png"

    plt.imsave(str(heatmap_path), localization_map, cmap="RdBu_r", vmin=0.0, vmax=1.0)
    plt.imsave(str(confidence_path), confidence_map, cmap="gray", vmin=0.0, vmax=1.0)

    print("Score:", format(score, ".10f"), flush=True)
    print("Confidence:", format(confidence, ".10f"), flush=True)
    print("Heatmap:", str(heatmap_path), flush=True)
    print("ConfidenceMap:", str(confidence_path), flush=True)
    print("NPZ:", str(npz_file), flush=True)
    print("Map shape:", localization_map.shape, flush=True)


if __name__ == "__main__":
    main()
