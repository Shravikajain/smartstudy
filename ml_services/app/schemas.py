from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel
from typing import Literal


class CamelModel(BaseModel):
    # Python uses snake_case, but our JSON contract (and Java) uses camelCase.
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class PaperIn(CamelModel):
    paper_id: int
    exam_year: int | None = None
    text: str


class AnalyzeRequest(CamelModel):
    papers: list[PaperIn] = Field(min_length=1)
    max_clusters: int = Field(default=8, ge=2, le=20)
    hot_threshold: float = Field(default=0.6, ge=0.0, le=1.0)


class QuestionOut(CamelModel):
    paper_id: int
    exam_year: int | None
    text: str


class ClusterOut(CamelModel):
    cluster_id: int
    keywords: list[str]
    question_count: int
    papers_appeared_in: int
    reappearance_score: float
    questions: list[QuestionOut]


class HotQuestionOut(CamelModel):
    text: str
    occurrences: int
    years: list[int]
    paper_ids: list[int]


class AnalyzeResponse(CamelModel):
    total_questions: int
    total_papers: int
    clusters: list[ClusterOut]
    hot_questions: list[HotQuestionOut]


class GenerateRequest(CamelModel):
    subject_name: str = Field(min_length=1)
    topic_name: str = Field(min_length=1)
    count: int = Field(default=5, ge=1, le=20)
    context: str | None = None
    question_type: Literal["multiple_choice", "subjective"] = "multiple_choice"


class GeneratedQuestion(CamelModel):
    prompt: str
    question_type: Literal["multiple_choice", "subjective"] = "multiple_choice"
    options: list[str] = Field(default_factory=list)
    correct_index: int | None = None
    reference_answer: str | None = None
    explanation: str
    difficulty: int = Field(ge=1, le=5)


class GenerateResponse(CamelModel):
    questions: list[GeneratedQuestion]


class DetectAiRequest(CamelModel):
    text: str = Field(min_length=50, description="Student notes or essay submission")
    threshold: float = Field(default=0.60, ge=0.0, le=1.0)


class GradeAnswerRequest(CamelModel):
    prompt: str = Field(min_length=1)
    reference_answer: str = Field(min_length=1)
    rubric: str = Field(min_length=1)
    student_answer: str = Field(min_length=50, max_length=12000)


class GradeAnswerResponse(CamelModel):
    score: float = Field(ge=0.0, le=1.0)
    feedback: str = Field(min_length=1)


class InterviewMessage(CamelModel):
    speaker: Literal["interviewer", "candidate"]
    content: str = Field(min_length=1, max_length=12000)


class InterviewTurnRequest(CamelModel):
    target_type: Literal["subject", "engineering_role"]
    target: str = Field(min_length=1, max_length=120)
    level: Literal["beginner", "intermediate", "advanced"] = "intermediate"
    question_number: int = Field(ge=0, le=20)
    question_count: int = Field(ge=1, le=20)
    history: list[InterviewMessage] = Field(default_factory=list, max_length=40)
    answer: str | None = Field(default=None, max_length=12000)


class InterviewTurnResponse(CamelModel):
    question: str | None
    feedback: str
    score: float = Field(ge=0.0, le=1.0)
    complete: bool


class DetectAiResponse(CamelModel):
    is_ai_generated: bool
    ai_probability: float
    human_probability: float
    burstiness_score: float
    feedback: str