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

All `/api/**` endpoints require HTTP Basic authentication; only `/api/health` is public for host health checks. The health check now verifies MongoDB connectivity, so Render will not mark the API healthy until the database can be reached. GitHub Pages shows an investigator sign-in screen and keeps the password in memory only. The current free Render service has an ephemeral filesystem: uploaded evidence can be lost on restart or redeploy. Use this free setup for a demo only. Before storing real forensic evidence, move uploads to durable, access-controlled object storage or use a paid persistent disk (Render persistent disks are only available on paid services).

## Forensic scoring

The restored original combined formula is **ELA 40% + metadata availability 20% + TruFor authenticity 40%**. The full formula is used only when the TruFor model completes successfully. The original risk bands are LOW at 80 or above, MEDIUM at 50–79.99, and HIGH below 50; the original result threshold is AUTHENTIC at 70 or above, otherwise MANIPULATED.

ELA screening and visualization accept JPEG and PNG. PNG files are recompressed to JPEG for the ELA comparison, so the result is a recompression-based screening signal rather than proof of the image's original compression history.

When TruFor is not installed or fails on the host, the backend still produces a clearly labelled **PRELIMINARY** result using ELA and metadata only. Their original 40:20 relative weights are normalized across the available 60% of signals. The absent TruFor 40% score is never invented; preliminary results are not the complete original combined assessment.

## TruFor limitation

The ZIP contains the Python bridge script but not the TruFor model repository or pretrained weights. The bridge also previously contained machine-specific absolute paths. TruFor is therefore disabled by default. Deploy the model code and weights, fix the bridge paths for the host, and configure the variables above before enabling it.

## Security and included files

Local `.env` files, MongoDB credentials, evidence uploads, generated analysis data, dependency folders, and build outputs are excluded from Git. Supply all credentials through environment variables/secrets. Rotate the MongoDB credential that was present in the original ZIP before deploying.

