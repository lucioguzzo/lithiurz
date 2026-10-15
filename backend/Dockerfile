FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    VOCENOTA_DATABASE_PATH=/dati/usage.db

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# I contatori di utilizzo vanno su un volume: solo numeri, nessun testo.
RUN mkdir -p /dati && useradd --create-home --uid 10001 vocenota \
    && chown -R vocenota /dati /app
USER vocenota
VOLUME ["/dati"]

EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health').status==200 else 1)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
