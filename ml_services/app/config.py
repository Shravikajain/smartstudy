import os
from pathlib import Path
from dotenv import dotenv_values

_ENV_FILE = Path(__file__).resolve().parent.parent / ".env"
_ENV = dotenv_values(_ENV_FILE)

OLLAMA_API_KEY = os.getenv("OLLAMA_API_KEY", _ENV.get("OLLAMA_API_KEY"))
OLLAMA_HOST = os.getenv("OLLAMA_HOST", _ENV.get("OLLAMA_HOST", "https://ollama.com"))
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", _ENV.get("OLLAMA_MODEL", "gpt-oss:120b"))
OLLAMA_THINK = os.getenv("OLLAMA_THINK", _ENV.get("OLLAMA_THINK"))
ML_INTERNAL_KEY = os.getenv("ML_INTERNAL_KEY", _ENV.get("ML_INTERNAL_KEY"))
WHISPER_MODEL = os.getenv("WHISPER_MODEL", _ENV.get("WHISPER_MODEL", "base"))