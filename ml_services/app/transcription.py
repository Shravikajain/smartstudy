import re
from functools import lru_cache
from io import BytesIO

from av.error import FFmpegError
from faster_whisper import WhisperModel
from huggingface_hub.errors import HfHubHTTPError

from .config import WHISPER_MODEL

MAX_AUDIO_BYTES = 10 * 1024 * 1024


class TranscriptionError(ValueError):
    pass


@lru_cache(maxsize=1)
def _load_model() -> WhisperModel:
    try:
        return WhisperModel(WHISPER_MODEL, device="cpu", compute_type="int8")
    except (HfHubHTTPError, OSError, RuntimeError, ValueError) as error:
        raise TranscriptionError(
            "The local speech model could not be loaded. Check the ML service logs and try again."
        ) from error


def transcribe_audio(audio_bytes: bytes, language: str | None = None) -> tuple[str, str]:
    if not audio_bytes:
        raise TranscriptionError("The audio recording is empty.")
    if len(audio_bytes) > MAX_AUDIO_BYTES:
        raise TranscriptionError("The audio recording is too large (10 MB maximum).")

    language_code = None
    if language:
        language_code = language.split("-", maxsplit=1)[0].lower()
        if not re.fullmatch(r"[a-z]{2,3}", language_code):
            raise TranscriptionError("The selected speech language is invalid.")

    try:
        segments, info = _load_model().transcribe(
            BytesIO(audio_bytes),
            language=language_code,
            beam_size=1,
            vad_filter=True,
        )
        text = " ".join(segment.text.strip() for segment in segments).strip()
    except (FFmpegError, OSError, RuntimeError, ValueError) as error:
        raise TranscriptionError(
            "The recording could not be transcribed. Try recording again or type your answer."
        ) from error

    if not text:
        raise TranscriptionError("No speech was detected. Try recording again or type your answer.")
    return text, info.language
