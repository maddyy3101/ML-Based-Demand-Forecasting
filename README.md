# ML-Based-Demand-Forecasting

Demand forecasting application with:
- `demand-forecasting/ml` (Flask inference API)
- `demand-forecasting/backend` (Spring Boot backend API)

## Deployment

- Vercel + Render deployment guide: [`DEPLOYMENT_VERCEL_RENDER.md`](./DEPLOYMENT_VERCEL_RENDER.md)

## Secure API Key Setup (No Manual ProcDoc Entry Each Run)

1. Create a local env file:
   - `cp .env.example .env.local`
2. Put your provider key in `.env.local`:
   - `GROQ_API_KEY=...` (recommended), or `GEMINI_API_KEY=...`
3. Start the system normally:
   - `./startSystem.sh`

`startSystem.sh` auto-loads `.env.local` (or `.env`) and exports values to backend.
Do not commit `.env.local` or real keys.

## High-value APIs implemented

- `POST /api/v1/forecasts/async`
- `GET /api/v1/jobs/{jobId}`
- `GET /api/v1/models/active`
- `POST /api/v1/models/{version}/activate`
- `POST /api/v1/backtests`
- `GET /api/v1/backtests/{jobId}`
- `GET /api/v1/metrics/performance?from=&to=&category=&region=`
- `GET /api/v1/drift/summary`
- `GET /api/v1/forecasts/{id}/explanation`
- `POST /api/v1/forecasts/what-if`

## Existing core APIs

- `POST /api/v1/forecasts`
- `POST /api/v1/forecasts/batch`
- `GET /api/v1/forecasts/history`
- `PATCH /api/v1/forecasts/{id}/actual`
- `GET /api/v1/forecasts/features`
- `GET /api/v1/forecasts/accuracy`
- `GET /api/v1/ml/health`
- `GET /api/v1/ml/model-info`

## Supply chain planning APIs

- `POST /api/v1/planning/replenishment`
- `POST /api/v1/planning/purchase-plan`
- `GET /api/v1/planning/exceptions`

Planning APIs are designed for multi-product and multi-warehouse inputs. They include:
- safety stock calculation
- reorder point and target stock logic
- demand variability analysis
- stockout and overstock risk detection
