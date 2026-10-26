import sys
import threading
from functools import lru_cache
from types import ModuleType, SimpleNamespace

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


def test_transformers_models_are_built_under_the_shared_lock(monkeypatch):
    # Building two models at once left weights on the "meta" device ("Tensor on device meta").
    from app import background, embeddings

    held = []

    def build(result):
        return lambda *args, **kwargs: held.append(background.transformers_lock.locked()) or result

    model = SimpleNamespace(eval=lambda: model)
    fake = ModuleType("transformers")
    fake.pipeline = build("clf")
    fake.AutoTokenizer = SimpleNamespace(from_pretrained=build("tok"))
    fake.AutoModel = SimpleNamespace(from_pretrained=build(model))
    monkeypatch.setitem(sys.modules, "transformers", fake)
    ai_detector._get_classifier.cache_clear()
    embeddings._load_encoder.cache_clear()
    try:
        assert ai_detector._get_classifier() == "clf"
        assert embeddings._load_encoder() == ("tok", model)
    finally:
        ai_detector._get_classifier.cache_clear()
        embeddings._load_encoder.cache_clear()
    assert held == [True, True, True]


def test_detector_reports_loading_instead_of_waiting_for_download(monkeypatch):
    started = []
    monkeypatch.setattr(ai_detector.classifier_loader, "ready", lambda: False)
    monkeypatch.setattr(ai_detector.classifier_loader, "start", lambda: started.append(1))

    with pytest.raises(ai_detector.AiModelUnavailableError, match="still loading"):
        ai_detector.detect_ai(DetectAiRequest(text="x" * 60))
    assert started == [1]
