FROM python:3.11-slim AS builder
WORKDIR /build
RUN apt-get update && apt-get install -y --no-install-recommends gcc g++ && rm -rf /var/lib/apt/lists/*
COPY ml/requirements.txt .
RUN python -m venv /opt/venv && \
    /opt/venv/bin/pip install --upgrade pip && \
    /opt/venv/bin/pip install --no-cache-dir -r requirements.txt

FROM python:3.11-slim AS runtime
RUN useradd -m -u 1001 mluser
WORKDIR /app
COPY --from=builder /opt/venv /opt/venv
COPY ml/app.py ./app.py
COPY ml/demand_forecasting_train.py ./demand_forecasting_train.py
COPY ml/powergrid_material_dataset.csv ./powergrid_material_dataset.csv
RUN mkdir -p /app/models && chown -R mluser:mluser /app

USER mluser
ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    MODEL_DIR=/app/models

EXPOSE 5001
HEALTHCHECK --interval=30s --timeout=10s --start-period=15s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:5001/health')" || exit 1

CMD ["gunicorn","--workers=2","--threads=2","--timeout=120","--bind=0.0.0.0:5001","--access-logfile=-","--error-logfile=-","app:create_app()"]
