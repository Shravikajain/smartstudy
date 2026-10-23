import re

from sklearn.cluster import KMeans
from sklearn.feature_extraction.text import ENGLISH_STOP_WORDS, TfidfVectorizer
from sklearn.metrics import silhouette_score
from sklearn.metrics.pairwise import cosine_similarity

from .question_splitter import split_questions
from .schemas import AnalyzeRequest, AnalyzeResponse, ClusterOut, HotQuestionOut, QuestionOut

# Words that appear in every exam and say nothing about the topic.
EXAM_WORDS = {
    "explain", "write", "short", "note", "notes", "discuss", "define", "describe",
    "mark", "marks", "following", "briefly", "example", "examples", "state", "give",
    "using", "illustrate", "differentiate", "compare", "list",
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
        labels = KMeans(n_clusters=k, n_init=10, random_state=42).fit_predict(X)
        if len(set(labels)) < 2:
            continue
        score = silhouette_score(X, labels)
        if score > best_score:
            best_k, best_score = k, score
    return best_k


def find_hot_questions(X, questions: list[QuestionOut], threshold: float) -> list[HotQuestionOut]:
    n = len(questions)
    sim = cosine_similarity(X)
    ds = DisjointSet(n)
    for i in range(n):
        for j in range(i + 1, n):
            if questions[i].paper_id != questions[j].paper_id and sim[i, j] >= threshold:
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

    k = choose_k(X, req.max_clusters)
    km = KMeans(n_clusters=k, n_init=10, random_state=42).fit(X)
    terms = vectorizer.get_feature_names_out()
    total_papers = len(req.papers)

    clusters = []
    for c in range(k):
        idx = [i for i, label in enumerate(km.labels_) if label == c]
        if not idx:
            continue
        center = km.cluster_centers_[c]
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