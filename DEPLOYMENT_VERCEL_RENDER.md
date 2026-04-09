# Deployment Guide (Vercel + Render)

This repo is configured for:
- Frontend on Vercel (`demand-forecasting/frontend`)
- PostgreSQL + ML API + Spring Boot backend on Render (`render.yaml`)

## 1. Deploy Render stack

1. Push this repo to GitHub.
2. In Render, create a new **Blueprint** service from the repo.
3. Render will read [`render.yaml`](./render.yaml) and create:
   - `powergrid-postgres` (managed PostgreSQL)
   - `powergrid-ml-api` (private Docker service)
   - `powergrid-backend-api` (public Docker web service)
4. After creation, set `GROQ_API_KEY` (or `GEMINI_API_KEY`) on backend service.
5. Run bootstrap SQL once against Postgres:
   - [`demand-forecasting/backend/sql/bootstrap_postgres.sql`](./demand-forecasting/backend/sql/bootstrap_postgres.sql)

Notes:
- Backend and ML use persistent disks for uploads/models.
- Backend JWT secret is auto-generated via `JWT_SECRET`.
- Default user passwords are not reset on each deploy (`POWERGRID_SEED_RESET_DEFAULT_PASSWORDS=false`).

## 2. Deploy frontend to Vercel

1. Import repo in Vercel.
2. Set **Root Directory** to `demand-forecasting/frontend`.
3. Deploy.

The frontend config file [`demand-forecasting/frontend/vercel.json`](./demand-forecasting/frontend/vercel.json):
- Proxies `/api/*` to `https://powergrid-backend-api.onrender.com/api/*`
- Adds SPA fallback to `/index.html`

If your Render backend URL differs, update `dest` in `vercel.json`.

## 3. Post-deploy sanity checks

1. Backend health:
   - `https://<your-backend>.onrender.com/api/v1/health/`
2. Frontend login page loads on Vercel URL.
3. Login works and dashboard API calls succeed.
4. Admin dataset upload triggers retraining successfully (backend now uploads CSV to ML service directly).
