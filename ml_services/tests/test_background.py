import threading
from functools import lru_cache

import pytest

from app import ai_detector
from app.background import BackgroundLoader
from app.schemas import DetectAiRequest


def test_loader_loads_once_in_the_background_and_reports_ready():
    release = threading.Event()
    calls = []

    @lru_cache(maxsize=1)
    def load():
        calls.append(1)
        release.wait(5)
        return "model"

    loader = BackgroundLoader("Test", load)
    loader.start()
    loader.start()                              # already loading: no second thread
    assert not loader.ready()

    release.set()
    loader._thread.join(5)
    assert loader.ready()
    assert calls == [1]


def test_failed_load_can_be_retried():
    attempts = []

    @lru_cache(maxsize=1)
    def load():
        attempts.append(1)
        if len(attempts) == 1:
            raise OSError("network down")
        return "model"

    loader = BackgroundLoader("Test", load)
    loader.start()
    loader._thread.join(5)
    assert not loader.ready()

    loader.start()
    loader._thread.join(5)
    assert loader.ready()


def test_detector_reports_loading_instead_of_waiting_for_download(monkeypatch):
    started = []
    monkeypatch.setattr(ai_detector.classifier_loader, "ready", lambda: False)
    monkeypatch.setattr(ai_detector.classifier_loader, "start", lambda: started.append(1))

    with pytest.raises(ai_detector.AiModelUnavailableError, match="still loading"):
        ai_detector.detect_ai(DetectAiRequest(text="x" * 60))
    assert started == [1]
