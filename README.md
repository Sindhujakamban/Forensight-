# ForenSight AI

A digital-forensics workspace with a Vite frontend and Spring Boot REST API. It supports investigation/case management, evidence uploads, metadata review, Error Level Analysis (ELA), optional TruFor integration, and PDF reports.

## Repository layout

- `frontend/` — Vite frontend
- `backend/` — Spring Boot REST API (Java 25, MongoDB)
- `Dockerfile` — backend container build
- `.github/workflows/build-and-deploy.yml` — build verification and GitHub Pages deployment

## Run locally

### Prerequisites

- Node.js 22 or newer
- JDK 25
- MongoDB local instance or MongoDB Atlas connection string

### Backend

Set `MONGODB_URI` and optionally `MONGODB_DATABASE` in your shell or environment. Do not put credentials in source files.

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

1. In **Settings → Pages**, choose **GitHub Actions** as the source.
2. The workflow deploys the frontend automatically after successful builds on `main`.
3. Add an Actions variable named `VITE_API_BASE_URL` with the public backend API base URL (for example, `https://your-api-host.example.com`). Until a backend is deployed and this variable is set, the frontend can load but API-backed features will not work.
4. Pages websites are public to anyone on the internet. Do not publish evidence, case records, API keys, or private data in the frontend.

## Deploy the backend

Deploy the root `Dockerfile` to a container host that supports Java 25. Configure these variables in the host dashboard:

- `MONGODB_URI` — MongoDB connection string (secret)
- `MONGODB_DATABASE` — defaults to `forensight_db`
- `PORT` — host-provided service port; defaults to `8080`
- `TRUFOR_ENABLED=false` — keep disabled until TruFor model code and pretrained weights are installed
- `TRUFOR_PYTHON` and `TRUFOR_BRIDGE` — set only when configuring TruFor

Mount persistent storage at the backend working directory's `uploads/` path. Uploaded evidence is stored on disk, so ephemeral storage is not suitable for production evidence retention. Confirm access controls, authentication, backups, TLS, and your MongoDB network allowlist before using real case data.

## TruFor limitation

The ZIP contains the Python bridge script but not the TruFor model repository or pretrained weights. The bridge also previously contained machine-specific absolute paths. TruFor is therefore disabled by default. Deploy the model code and weights, fix the bridge paths for the host, and configure the variables above before enabling it.

## Security and included files

Local `.env` files, MongoDB credentials, evidence uploads, generated analysis data, dependency folders, and build outputs are excluded from Git. Supply all credentials through environment variables/secrets. Rotate the MongoDB credential that was present in the original ZIP before deploying.
