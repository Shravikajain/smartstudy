import secrets

from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile
from starlette.concurrency import run_in_threadpool

# Import your new module here
from .ai_detector import AiModelUnavailableError, detect_ai

from .analyzer import analyze
from .llm_client import LlmError, generate_questions, grade_answer, run_interview_turn
from .config import ML_INTERNAL_KEY
from .pdf_ocr import MAX_PDF_BYTES, PdfOcrError, extract_pdf_text
from .transcription import MAX_AUDIO_BYTES, TranscriptionError, transcribe_audio
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


@app.post("/extract-pdf-text")
async def extract_scanned_pdf_text(
    file: UploadFile = File(...),
    x_internal_key: str | None = Header(default=None),
):
    if not ML_INTERNAL_KEY:
        raise HTTPException(status_code=503, detail="PDF OCR is not configured")
    if not x_internal_key or not secrets.compare_digest(x_internal_key, ML_INTERNAL_KEY):
        raise HTTPException(status_code=403, detail="Internal service authentication failed")

    pdf_bytes = await file.read(MAX_PDF_BYTES + 1)
    if len(pdf_bytes) > MAX_PDF_BYTES:
        raise HTTPException(status_code=413, detail="PDF is too large for scanned-page OCR (20 MB maximum).")

    try:
        text, page_count = extract_pdf_text(pdf_bytes)
    except PdfOcrError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error
    return {"text": text, "pageCount": page_count}


@app.post("/transcribe-audio")
async def transcribe_interview_audio(
    file: UploadFile = File(...),
    language: str | None = Form(default=None),
    x_internal_key: str | None = Header(default=None),
):
    if not ML_INTERNAL_KEY:
        raise HTTPException(status_code=503, detail="Audio transcription is not configured")
    if not x_internal_key or not secrets.compare_digest(x_internal_key, ML_INTERNAL_KEY):
        raise HTTPException(status_code=403, detail="Internal service authentication failed")

    audio_bytes = await file.read(MAX_AUDIO_BYTES + 1)
    if len(audio_bytes) > MAX_AUDIO_BYTES:
        raise HTTPException(status_code=413, detail="The audio recording is too large (10 MB maximum).")

    try:
        text, detected_language = await run_in_threadpool(
            transcribe_audio, audio_bytes, language
        )
    except TranscriptionError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error
    return {"text": text, "language": detected_language}