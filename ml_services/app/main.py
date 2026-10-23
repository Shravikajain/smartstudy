from fastapi import FastAPI, HTTPException

# Import your new module here
from .ai_detector import AiModelUnavailableError, detect_ai

from .analyzer import analyze
from .llm_client import LlmError, generate_questions, grade_answer, run_interview_turn
from .schemas import (
    AnalyzeRequest,
    AnalyzeResponse,
    DetectAiRequest,
    DetectAiResponse,
    GenerateRequest,
    GenerateResponse,
    GradeAnswerRequest,
    GradeAnswerResponse,
    InterviewTurnRequest,
    InterviewTurnResponse,
)

app = FastAPI(title="Smart Study ML Service")

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/analyze", response_model=AnalyzeResponse)
def analyze_papers(request: AnalyzeRequest):
    try:
        return analyze(request)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))

@app.post("/generate-questions", response_model=GenerateResponse)
def generate_exam_questions(request: GenerateRequest):
    try:
        return GenerateResponse(questions=generate_questions(request))
    except LlmError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e

# Add your new endpoint here
@app.post("/detect-ai", response_model=DetectAiResponse)
def detect_ai_content(request: DetectAiRequest):
    try:
        return detect_ai(request)
    except AiModelUnavailableError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e


@app.post("/grade-answer", response_model=GradeAnswerResponse)
def grade_subjective_answer(request: GradeAnswerRequest):
    try:
        return grade_answer(request)
    except LlmError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e


@app.post("/interview/turn", response_model=InterviewTurnResponse)
def interview_turn(request: InterviewTurnRequest):
    try:
        return run_interview_turn(request)
    except LlmError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e