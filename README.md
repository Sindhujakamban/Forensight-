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
3. After Pages is available for this repository, open **Settings → Pages** and choose **GitHub Actions** as the source.
4. The frontend build defaults to `https://forensight-api.onrender.com`. To use another backend, add a repository Actions variable named `VITE_API_BASE_URL` with its public API base URL.
5. Add a repository Actions variable named `DEPLOY_TO_GITHUB_PAGES` with value `true`. Rerun the workflow. Without that variable, CI builds both components but intentionally skips the public deployment.

## Deploy the backend

The included `render.yaml` is a Render Blueprint for the backend. Create it from the public GitHub repository at Render; the service URL is `https://forensight-api.onrender.com`. Set these variables in the Render dashboard before the service can start:

- `MONGODB_URI` — a newly rotated MongoDB Atlas connection string (secret)
- `APP_PASSWORD` — a unique, strong password for the investigator account (secret)
- `APP_USERNAME` — defaults to `investigator`
- `MONGODB_DATABASE` — defaults to `forensight_db`
- `TRUFOR_ENABLED=false` — keep disabled until TruFor model code and pretrained weights are installed
- `TRUFOR_PYTHON` and `TRUFOR_BRIDGE` — set only when configuring TruFor

All `/api/**` endpoints require HTTP Basic authentication; only `/api/health` is public for host health checks. GitHub Pages will show a sign-in screen and send credentials only in memory. The included free Render service has an ephemeral filesystem. Evidence uploads can be lost when it restarts or sleeps, so do not use it to retain real forensic evidence. Configure durable, access-controlled object storage or a paid persistent disk before real investigations.

## TruFor limitation

The ZIP contains the Python bridge script but not the TruFor model repository or pretrained weights. The bridge also previously contained machine-specific absolute paths. TruFor is therefore disabled by default. Deploy the model code and weights, fix the bridge paths for the host, and configure the variables above before enabling it.

## Security and included files

Local `.env` files, MongoDB credentials, evidence uploads, generated analysis data, dependency folders, and build outputs are excluded from Git. Supply all credentials through environment variables/secrets. Rotate the MongoDB credential that was present in the original ZIP before deploying.

