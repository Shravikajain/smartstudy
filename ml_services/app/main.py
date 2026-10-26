import logging
import secrets
from contextlib import asynccontextmanager

from fastapi import APIRouter, Depends, FastAPI, File, Form, Header, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse
from starlette.concurrency import run_in_threadpool

from .ai_detector import AiModelUnavailableError, classifier_loader, detect_ai
from .analyzer import analyze
from .llm_client import LlmError, generate_questions, grade_answer, run_interview_turn
from .config import ML_EMBEDDINGS, ML_INTERNAL_KEY, ML_WARMUP
from .embeddings import encoder_loader
from .pdf_ocr import MAX_PDF_BYTES, PdfOcrError, extract_pdf_text
from .transcription import MAX_AUDIO_BYTES, TranscriptionError, transcribe_audio
from .transcription import model_loader as whisper_loader
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

# Show the model loaders' progress messages next to uvicorn's own logs.
logging.basicConfig(level=logging.INFO, format="%(levelname)s:     %(name)s - %(message)s")


@asynccontextmanager
async def lifespan(_app: FastAPI):
    # Start downloading/loading models in the background. Requests that arrive first never wait:
    # analysis falls back to TF-IDF, and detection/transcription report that the model is loading.
    if ML_WARMUP:
        classifier_loader.start()
        whisper_loader.start()
        if ML_EMBEDDINGS:
            encoder_loader.start()
    yield


def require_internal_key(x_internal_key: str | None = Header(default=None)):
    if not ML_INTERNAL_KEY:
        raise HTTPException(status_code=503, detail="Internal service authentication is not configured")
    if not x_internal_key or not secrets.compare_digest(x_internal_key, ML_INTERNAL_KEY):
        raise HTTPException(status_code=403, detail="Internal service authentication failed")


app = FastAPI(title="Smart Study ML Service", lifespan=lifespan)
logger = logging.getLogger(__name__)


# An unhandled exception makes uvicorn close the connection after its 500 response; the backend
# then reuses that pooled connection for its next call (e.g. grading right after a failed
# detection) and gets "Connection reset". Answering here keeps the connection usable.
@app.middleware("http")
async def unexpected_errors_as_json(request: Request, call_next):
    try:
        return await call_next(request)
    except Exception:
        logger.exception("Unhandled error in %s", request.url.path)
        return JSONResponse(status_code=500, content={"detail": "The ML service hit an unexpected error."})


# Every endpoint except /health is for the backend only.
internal = APIRouter(dependencies=[Depends(require_internal_key)])


@app.get("/health")
def health():
    return {"status": "ok"}

@internal.post("/analyze", response_model=AnalyzeResponse)
def analyze_papers(request: AnalyzeRequest):
    try:
        return analyze(request)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))

@internal.post("/generate-questions", response_model=GenerateResponse)
def generate_exam_questions(request: GenerateRequest):
    try:
        return GenerateResponse(questions=generate_questions(request))
    except LlmError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e

@internal.post("/detect-ai", response_model=DetectAiResponse)
def detect_ai_content(request: DetectAiRequest):
    try:
        return detect_ai(request)
    except AiModelUnavailableError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e


@internal.post("/grade-answer", response_model=GradeAnswerResponse)
def grade_subjective_answer(request: GradeAnswerRequest):
    try:
        return grade_answer(request)
    except LlmError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e


@internal.post("/interview/turn", response_model=InterviewTurnResponse)
def interview_turn(request: InterviewTurnRequest):
    try:
        return run_interview_turn(request)
    except LlmError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e


@internal.post("/extract-pdf-text")
async def extract_scanned_pdf_text(file: UploadFile = File(...)):
    pdf_bytes = await file.read(MAX_PDF_BYTES + 1)
    if len(pdf_bytes) > MAX_PDF_BYTES:
        raise HTTPException(status_code=413, detail="PDF is too large for scanned-page OCR (20 MB maximum).")

    try:
        text, page_count = extract_pdf_text(pdf_bytes)
    except PdfOcrError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error
    return {"text": text, "pageCount": page_count}


@internal.post("/transcribe-audio")
async def transcribe_interview_audio(
    file: UploadFile = File(...),
    language: str | None = Form(default=None),
):
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


app.include_router(internal)
