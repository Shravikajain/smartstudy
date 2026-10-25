FROM python:3.11-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends poppler-utils tesseract-ocr \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
# The pip cache mount keeps downloaded wheels (torch is large) between builds, so a retry
# after a network timeout does not start from scratch.
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --retries 10 --timeout 120 torch --index-url https://download.pytorch.org/whl/cpu \
    && pip install --retries 10 --timeout 120 -r requirements.txt
COPY app ./app

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
