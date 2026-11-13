# ForenSight AI

A digital-forensics workspace with a Vite frontend and Spring Boot REST API. It supports investigation/case management, evidence uploads, metadata review, Error Level Analysis (ELA), optional TruFor integration, and PDF reports.

## Repository layout

- `frontend/` — Vite frontend
- `backend/` — Spring Boot REST API (Java 25, MongoDB)
- `Dockerfile` — backend container build
- `.github/workflows/build-and-deploy.yml` — frontend/backend verification and optional Pages deployment

## Run locally

### Prerequisites

- Node.js 22 or newer
- JDK 25
- MongoDB local instance or MongoDB Atlas connection string

### Backend

Set `APP_PASSWORD` to a unique local password, and set `MONGODB_URI` and optionally `MONGODB_DATABASE` in your shell or environment. The default username is `investigator`. Do not put credentials in source files.

```bash
cd backend
bash ./mvnw spring-boot:run
```

The API listens on port `8080` by default. Without `MONGODB_URI`, it attempts to use a local MongoDB at `mongodb://localhost:27017/forensight_db`.

### Frontend

Open a second terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open the Vite URL shown in the terminal. During local development the frontend uses `http://localhost:8080` for the backend. Set `VITE_API_BASE_URL` when the API is hosted elsewhere.

## GitHub Actions: build and verify

Open the [Actions tab](https://github.com/Sindhujakamban/Forensight-/actions). The `ForenSight Build and Deploy` workflow builds the frontend and compiles the backend on pushes to `main`. You can also start it manually using **Run workflow**.

## Publish the frontend

GitHub Pages serves only static frontend files; it does not host the Spring Boot API or MongoDB.

1. The repository is public and Pages publishes only the static frontend. The Spring Boot API and MongoDB must be hosted separately.
2. Pages websites are public to anyone on the internet, even when the source repository is private. Do not publish evidence, case records, API keys, or private data in the frontend.
3. GitHub Pages is already configured through GitHub Actions; the most recent deployment completed successfully. The public frontend is `https://sindhujakamban.github.io/Forensight-/`.
4. The frontend build defaults to `https://forensight-api-br5x.onrender.com`. To use another backend, add a repository Actions variable named `VITE_API_BASE_URL` with its public API base URL.
5. Add a repository Actions variable named `DEPLOY_TO_GITHUB_PAGES` with value `true`. Rerun the workflow. Without that variable, CI builds both components but intentionally skips the public deployment.

## Deploy the backend

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/Sindhujakamban/Forensight-)

The included `render.yaml` is a Render Blueprint for the backend. Open the Deploy to Render button, sign in to Render, connect GitHub if asked, review the service, and apply the Blueprint. The expected service URL is `https://forensight-api-br5x.onrender.com`; if Render assigns a different URL, set that URL as the GitHub Actions variable `VITE_API_BASE_URL`.

Create a fresh free MongoDB Atlas cluster at [MongoDB Atlas](https://www.mongodb.com/atlas/database), create a database user, configure the Atlas IP access list for the Render service, and copy its new connection string. During the Render Blueprint setup, enter that value for `MONGODB_URI` and set a new strong `APP_PASSWORD`. Never reuse the credential embedded in the original ZIP. Do not paste either secret into GitHub source or chat.

- `MONGODB_URI` — a newly rotated MongoDB Atlas connection string (secret)
- `APP_PASSWORD` — a unique, strong password for the investigator account (secret)
- `APP_USERNAME` — defaults to `investigator`
- `MONGODB_DATABASE` — defaults to `forensight_db`
- `TRUFOR_ENABLED=false` — keep disabled until TruFor model code and pretrained weights are installed
- `TRUFOR_PYTHON` and `TRUFOR_BRIDGE` — set only when configuring TruFor

All `/api/**` endpoints require HTTP Basic authentication; only `/api/health` is public for host health checks. The health check now verifies MongoDB connectivity, so Render will not mark the API healthy until the database can be reached. GitHub Pages shows an investigator sign-in screen and keeps the password in memory only. The Render Blueprint is intentionally set to the Free plan. The Free service has limited CPU/RAM, sleeps when idle, and has no persistent disk. It is suitable only for a demo and does not run the TruFor model. No paid Render compute or storage is configured.

## Forensic scoring

The ZIP's original calculation is **TruFor authenticity 75% + ELA supporting score 25%**:

`Final Authenticity = (TruFor authenticity × 0.75) + (ELA score × 0.25)`

TruFor authenticity is calculated as `(1 − native TruFor manipulation score) × 100`. Metadata is extracted and shown as forensic context only; it is not part of the original score. The ZIP's original result threshold is AUTHENTIC at 80% or above, otherwise MANIPULATED. Risk bands are LOW at 80% or above, MEDIUM from 50% to below 80%, and HIGH below 50%.

The ZIP's ELA implementation supports JPEG/JPG. For image formats where ELA is unsupported, the original backend uses TruFor alone and discloses that the 75/25 combined formula was not applied. A complete 75/25 result requires the real TruFor model, its pretrained weights, and the bridge runtime. These model weights are not included in the uploaded ZIP and must be installed on the backend before enabling TruFor. Do not substitute a made-up TruFor score.

## TruFor limitation

The uploaded ZIP includes the Java scoring pipeline and Python bridge but not the upstream TruFor code or pretrained checkpoint. The zero-cost setup downloads the official GRIP-UNINA inference source and pretrained weights to the investigator's own Windows computer, verifies the weight archive MD5, and runs the model locally. The weight files stay out of Git. The user's computer performs inference; Render Free is used only for the lightweight demo API.

**License restriction:** GRIP-UNINA's TruFor code and weights are licensed for informational and nonprofit purposes only; industrial or profit-oriented use is prohibited. Use this setup only for academic/informational, nonprofit work, retain both upstream license notices, and cite GRIP-UNINA when publishing results. Do not use it commercially without written permission.

## Security and included files

Local `.env` files, MongoDB credentials, evidence uploads, generated analysis data, dependency folders, and build outputs are excluded from Git. Supply all credentials through environment variables/secrets. Rotate the MongoDB credential that was present in the original ZIP before deploying.



## Zero-cost full TruFor analysis on Windows

Render Free does not have enough compute for the TruFor model and has no persistent disk. To avoid monthly charges, run the full application locally on the investigator's Windows computer and use the existing MongoDB Atlas database. The local backend stores uploaded evidence and TruFor maps in `backend/uploads` on that computer; keep the computer on while using the app.

1. Install Python 3.10, JDK 25, Node.js 22 or newer, and Git.
2. From the repository root, open PowerShell and run:

   ```powershell
   Set-ExecutionPolicy -Scope Process Bypass
   .\setup-and-run-local.ps1
   ```

3. The script downloads the official TruFor source and 248 MB weights directly to a local ignored folder, verifies the official checksum, installs PyTorch with CUDA 11.3 for an NVIDIA GPU when available (CPU fallback otherwise), and starts the local backend and Vite frontend.
4. Paste the MongoDB Atlas URI and set the investigator login password when prompted. Secrets are held in the local process environment; they are not written into Git.
5. Open `http://localhost:5173`, sign in as `investigator`, and upload evidence again from your computer. Files uploaded to the old Render Free instance are not present on your local disk.

For the ZIP's complete 75/25 score, use an original JPG/JPEG evidence file. The original ELA implementation supports JPEG/JPG only. PNG files still receive the original TruFor-only result; they do not get a fabricated ELA component. The local setup uses the computer's GPU when PyTorch can see it, avoiding paid cloud compute.

