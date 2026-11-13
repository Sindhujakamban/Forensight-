import glob
import os
import re
import subprocess
import sys
import uuid
from pathlib import Path
from PIL import Image

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
MAX_MODEL_SIDE = int(os.environ.get('TRUFOR_MAX_SIDE', '768'))


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

    # TruFor's official test loader preserves every source pixel. On a 2 GB
    # serverless container, large camera images can OOM during feature-map
    # construction. Preserve the original for ELA/metadata in Java, but feed
    # the model a proportional, high-quality copy capped at MAX_MODEL_SIDE.
    # This keeps the original 75/25 score formula while making inference
    # predictable under the Vercel Hobby memory ceiling.
    try:
        with Image.open(image_path) as source_image:
            original_width, original_height = source_image.size
            inference_image = source_image.convert("RGB")
            inference_image.thumbnail(
                (MAX_MODEL_SIDE, MAX_MODEL_SIDE),
                Image.Resampling.LANCZOS
            )
            model_width, model_height = inference_image.size
            if (model_width, model_height) != (original_width, original_height):
                resized_suffix = ".png" if image_path.suffix.lower() == ".png" else ".jpg"
                inference_image_path = output_dir / ("trufor_input" + resized_suffix)
                save_options = {"optimize": True}
                if resized_suffix == ".jpg":
                    save_options["quality"] = 95
                inference_image.save(inference_image_path, **save_options)
            else:
                inference_image_path = image_path
    except Exception as exc:
        fail(f"Could not prepare TruFor input image: {exc}")

    print(
        f"Input image: {original_width}x{original_height}; "
        f"TruFor model input: {model_width}x{model_height}; "
        f"maximum side: {MAX_MODEL_SIDE}px",
        flush=True
    )

    gpu_setting = os.environ.get("TRUFOR_GPU", "-1")
    try:
        gpu_id = int(gpu_setting)
    except ValueError:
        fail(f"TRUFOR_GPU must be -1 or a non-negative GPU index; received: {gpu_setting}")

    command = [
        PYTHON,
        str(TEST_SCRIPT),
        "-gpu", str(gpu_id),
        "-in", str(inference_image_path),
        "-out", str(output_dir),
        "TEST.MODEL_FILE", str(MODEL),
    ]

    env = os.environ.copy()
    env["OMP_NUM_THREADS"] = "1"
    env["MKL_NUM_THREADS"] = "1"
    env["OPENBLAS_NUM_THREADS"] = "1"
    env["NUMEXPR_NUM_THREADS"] = "1"
    env["MALLOC_ARENA_MAX"] = "2"
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
