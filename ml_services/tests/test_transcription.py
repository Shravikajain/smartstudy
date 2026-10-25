from types import SimpleNamespace

import pytest

from app import transcription


def test_transcribe_audio_normalizes_browser_locale(monkeypatch):
    class FakeModel:
        def transcribe(self, audio, **kwargs):
            assert audio.read() == b"recording"
            assert kwargs == {
                "language": "en",
                "beam_size": 1,
                "vad_filter": True,
            }
            return iter([
                SimpleNamespace(text=" Hello"),
                SimpleNamespace(text=" world. "),
            ]), SimpleNamespace(language="en")

    monkeypatch.setattr(transcription, "_load_model", lambda: FakeModel())

    text, language = transcription.transcribe_audio(b"recording", "en-US")

    assert text == "Hello world."
    assert language == "en"


def test_transcribe_audio_rejects_oversized_recording():
    with pytest.raises(transcription.TranscriptionError, match="10 MB maximum"):
        transcription.transcribe_audio(b"x" * (transcription.MAX_AUDIO_BYTES + 1))


def test_transcribe_audio_rejects_empty_recording():
    with pytest.raises(transcription.TranscriptionError, match="empty"):
        transcription.transcribe_audio(b"")


def test_transcribe_audio_reports_recording_without_speech(monkeypatch):
    class FakeModel:
        def transcribe(self, _audio, **_kwargs):
            return iter([]), SimpleNamespace(language="en")

    monkeypatch.setattr(transcription, "_load_model", lambda: FakeModel())

    with pytest.raises(transcription.TranscriptionError, match="No speech was detected"):
        transcription.transcribe_audio(b"recording")
