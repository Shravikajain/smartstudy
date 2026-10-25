import numpy as np
from scipy.sparse import csr_matrix

from app import analyzer
from app.embeddings import EmbeddingModelUnavailableError
from app.schemas import AnalyzeRequest


def test_choose_k_uses_bounded_silhouette_sample(monkeypatch):
    sample_sizes = []
    initialization_counts = []

    class FakeKMeans:
        def __init__(self, n_clusters, n_init, random_state):
            initialization_counts.append(n_init)
            self.n_clusters = n_clusters

        def fit_predict(self, matrix):
            return [index % self.n_clusters for index in range(matrix.shape[0])]

    def fake_silhouette_score(matrix, labels, sample_size, random_state):
        sample_sizes.append(sample_size)
        return len(set(labels))

    monkeypatch.setattr(analyzer, "KMeans", FakeKMeans)
    monkeypatch.setattr(analyzer, "silhouette_score", fake_silhouette_score)

    selected = analyzer.choose_k(csr_matrix((600, 4)), 8)

    assert selected == 8
    assert sample_sizes == [500] * 7
    assert initialization_counts == [3] * 7


QUEENS = "Explain the N-Queens problem using backtracking technique."
EIGHT_QUEENS = "Place eight non-attacking chess pieces on a board via state space search."
KNAPSACK = "Explain the 0/1 knapsack problem using dynamic programming."
SPANNING = "Write Prim's algorithm for minimum spanning tree."


def _request():
    return AnalyzeRequest(papers=[
        {"paper_id": 1, "text": f"1. {QUEENS}\n2. {KNAPSACK}\n"},
        {"paper_id": 2, "text": f"1. {EIGHT_QUEENS}\n2. {SPANNING}\n"},
    ], max_clusters=3)


def _cluster_of(result, text):
    return next(c.cluster_id for c in result.clusters if any(q.text == text for q in c.questions))


def test_embeddings_group_reworded_questions_that_share_no_words(monkeypatch):
    vectors = {QUEENS: [1, 0, 0], EIGHT_QUEENS: [0.95, 0.31, 0], KNAPSACK: [0, 1, 0],
               SPANNING: [0, 0, 1]}
    monkeypatch.setattr(analyzer, "ML_EMBEDDINGS", True)
    monkeypatch.setattr(analyzer, "embed_if_ready", lambda texts: np.array([vectors[t] for t in texts]))

    result = analyzer.analyze(_request())

    assert _cluster_of(result, QUEENS) == _cluster_of(result, EIGHT_QUEENS)
    assert result.hot_questions == []            # same topic, but not a repeated question
    queens_cluster = next(c for c in result.clusters if c.cluster_id == _cluster_of(result, QUEENS))
    assert "queens" in queens_cluster.keywords   # keywords come from the TF-IDF block only


def test_analysis_falls_back_to_tfidf_when_embedding_model_is_unavailable(monkeypatch):
    def unavailable(texts):
        raise EmbeddingModelUnavailableError("offline")

    monkeypatch.setattr(analyzer, "ML_EMBEDDINGS", True)
    monkeypatch.setattr(analyzer, "embed_if_ready", unavailable)

    result = analyzer.analyze(_request())

    assert result.total_questions == 4
    assert sum(c.question_count for c in result.clusters) == 4


def test_analysis_does_not_wait_for_an_embedding_model_that_is_still_loading(monkeypatch):
    monkeypatch.setattr(analyzer, "ML_EMBEDDINGS", True)
    monkeypatch.setattr(analyzer, "embed_if_ready", lambda texts: None)

    result = analyzer.analyze(_request())

    assert sum(c.question_count for c in result.clusters) == 4
