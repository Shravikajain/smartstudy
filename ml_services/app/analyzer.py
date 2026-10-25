import logging
import math
import re

from scipy.sparse import csr_matrix, hstack
from sklearn.cluster import KMeans
from sklearn.feature_extraction.text import ENGLISH_STOP_WORDS, TfidfVectorizer
from sklearn.metrics import silhouette_score

from .config import ML_EMBEDDINGS
from .embeddings import EmbeddingModelUnavailableError, embed_if_ready
from .question_splitter import split_questions
from .schemas import AnalyzeRequest, AnalyzeResponse, ClusterOut, HotQuestionOut, QuestionOut

logger = logging.getLogger(__name__)

# Share of the clustering similarity that comes from sentence meaning rather than shared words.
# Embeddings group reworded questions on one topic ("N-Queens" / "8 queens puzzle"), which
# TF-IDF scores as unrelated. Hot-question detection stays on TF-IDF: MiniLM scores different
# questions on the same topic almost as high as true repeats, so it would merge them.
EMBEDDING_WEIGHT = 0.6

# Words that appear in every exam and say nothing about the topic.
EXAM_WORDS = {
    "explain", "write", "short", "note", "notes", "discuss", "define", "describe",
    "mark", "marks", "following", "briefly", "example", "examples", "state", "give",
    "using", "illustrate", "differentiate", "compare", "list",
    "solve", "find", "analyse", "analyze", "draw", "show", "calculate", "derive",
}
STOP_WORDS = list(ENGLISH_STOP_WORDS | EXAM_WORDS)
MARKS = re.compile(r"[\[(]\s*\d+\s*marks?\s*[\])]")


def normalize(text: str) -> str:
    text = MARKS.sub(" ", text.lower())
    text = re.sub(r"[^a-z\s]", " ", text)       # drop digits and punctuation
    return re.sub(r"\s+", " ", text).strip()


class DisjointSet:
    """Union-Find with path compression."""

    def __init__(self, n: int):
        self.parent = list(range(n))

    def find(self, x: int) -> int:
        while self.parent[x] != x:
            self.parent[x] = self.parent[self.parent[x]]
            x = self.parent[x]
        return x

    def union(self, a: int, b: int) -> None:
        ra, rb = self.find(a), self.find(b)
        if ra != rb:
            self.parent[rb] = ra


def choose_k(X, max_k: int) -> int:
    n = X.shape[0]
    upper = min(max_k, n - 1)
    if upper < 2:
        return 1
    best_k, best_score = 2, -1.0
    for k in range(2, upper + 1):
        labels = KMeans(n_clusters=k, n_init=3, random_state=42).fit_predict(X)
        if len(set(labels)) < 2:
            continue
        score = silhouette_score(
            X,
            labels,
            sample_size=min(500, n),
            random_state=42,
        )
        if score > best_score:
            best_k, best_score = k, score
    return best_k


def find_hot_questions(X, questions: list[QuestionOut], threshold: float) -> list[HotQuestionOut]:
    n = len(questions)
    similarities = (X @ X.T).tocoo()
    ds = DisjointSet(n)
    for i, j, similarity in zip(similarities.row, similarities.col, similarities.data):
        if (i < j and questions[i].paper_id != questions[j].paper_id
                and similarity >= threshold):
            ds.union(i, j)

    groups: dict[int, list[int]] = {}
    for i in range(n):
        groups.setdefault(ds.find(i), []).append(i)

    hot = []
    for members in groups.values():
        paper_ids = sorted({questions[i].paper_id for i in members})
        if len(paper_ids) < 2:
            continue
        years = sorted({questions[i].exam_year for i in members if questions[i].exam_year is not None})
        hot.append(HotQuestionOut(
            text=questions[members[0]].text,
            occurrences=len(paper_ids),
            years=years,
            paper_ids=paper_ids,
        ))
    hot.sort(key=lambda h: -h.occurrences)
    return hot


def cluster_features(X, texts: list[str]):
    """TF-IDF rows joined with weighted sentence embeddings; falls back to TF-IDF alone."""
    if not ML_EMBEDDINGS:
        return X
    try:
        embeddings = embed_if_ready(texts)
    except EmbeddingModelUnavailableError:
        logger.warning("Embedding model unavailable; clustering with TF-IDF only", exc_info=True)
        return X
    if embeddings is None:
        logger.info("Embedding model is still loading; clustering with TF-IDF only")
        return X
    # Both blocks are L2-normalized, so a row's cosine similarity is the weighted average of the
    # TF-IDF and embedding similarities. The TF-IDF block stays first for keyword extraction.
    return hstack([
        X * math.sqrt(1 - EMBEDDING_WEIGHT),
        csr_matrix(embeddings * math.sqrt(EMBEDDING_WEIGHT)),
    ]).tocsr()


def analyze(req: AnalyzeRequest) -> AnalyzeResponse:
    questions: list[QuestionOut] = []
    for paper in req.papers:
        for text in split_questions(paper.text):
            questions.append(QuestionOut(paper_id=paper.paper_id, exam_year=paper.exam_year, text=text))
    if not questions:
        raise ValueError("No questions could be extracted from the uploaded papers")

    vectorizer = TfidfVectorizer(
        preprocessor=normalize, stop_words=STOP_WORDS, ngram_range=(1, 2), sublinear_tf=True
    )
    try:
        X = vectorizer.fit_transform([q.text for q in questions])
    except ValueError:
        raise ValueError("The questions contained no usable words")

    features = cluster_features(X, [q.text for q in questions])
    k = choose_k(features, req.max_clusters)
    km = KMeans(n_clusters=k, n_init=5, random_state=42).fit(features)
    terms = vectorizer.get_feature_names_out()
    total_papers = len(req.papers)

    clusters = []
    for c in range(k):
        idx = [i for i, label in enumerate(km.labels_) if label == c]
        if not idx:
            continue
        center = km.cluster_centers_[c][: len(terms)]     # TF-IDF block only
        top_terms = center.argsort()[::-1][:5]
        keywords = [str(terms[t]) for t in top_terms if center[t] > 0]
        paper_ids = {questions[i].paper_id for i in idx}
        clusters.append(ClusterOut(
            cluster_id=c,
            keywords=keywords,
            question_count=len(idx),
            papers_appeared_in=len(paper_ids),
            reappearance_score=round(len(paper_ids) / total_papers, 2),
            questions=[questions[i] for i in idx],
        ))
    clusters.sort(key=lambda cl: (-cl.reappearance_score, -cl.question_count))

    return AnalyzeResponse(
        total_questions=len(questions),
        total_papers=total_papers,
        clusters=clusters,
        hot_questions=find_hot_questions(X, questions, req.hot_threshold),
    )