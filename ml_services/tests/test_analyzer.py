from scipy.sparse import csr_matrix

from app import analyzer


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
