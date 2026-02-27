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
COPY ml/app.py .
RUN mkdir -p /app/models && chown mluser:mluser /app/models
USER mluser
ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    MODEL_DIR=/app/models \
    MAX_BATCH_SIZE=256
EXPOSE 5000
HEALTHCHECK --interval=30s --timeout=10s --start-period=15s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:5000/health')" || exit 1
CMD ["gunicorn","--workers=3","--threads=2","--timeout=120","--bind=0.0.0.0:5000","--access-logfile=-","--error-logfile=-","app:create_app()"]
