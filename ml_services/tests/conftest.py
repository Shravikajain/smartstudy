import pytest

from app import ai_detector, analyzer, transcription


@pytest.fixture(autouse=True)
def tfidf_only_analysis(monkeypatch):
    # Keep tests offline and deterministic: no sentence-embedding model download.
    monkeypatch.setattr(analyzer, "ML_EMBEDDINGS", False)


@pytest.fixture(autouse=True)
def stubbed_models_are_loaded(monkeypatch):
    # Tests replace the model loaders with fakes, so treat the models as already loaded.
    monkeypatch.setattr(ai_detector.classifier_loader, "ready", lambda: True)
    monkeypatch.setattr(transcription.model_loader, "ready", lambda: True)
