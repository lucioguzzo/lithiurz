FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    BRIEF_DATABASE_PATH=/dati/usage.db \
    PORT=8000

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# I contatori di utilizzo vanno su un volume: solo numeri, nessun testo.
RUN mkdir -p /dati && useradd --create-home --uid 10001 brief \
    && chown -R brief /dati /app
USER brief
VOLUME ["/dati"]

EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s \
    CMD python -c "import os,urllib.request,sys; port=os.environ.get('PORT','8000'); sys.exit(0 if urllib.request.urlopen(f'http://127.0.0.1:{port}/health').status==200 else 1)"

# La porta arriva dall'ambiente perché le piattaforme serverless (Cloud
# Run e simili) la impongono con $PORT; in locale resta la 8000.
CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
