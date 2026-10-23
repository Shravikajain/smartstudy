import math
import re
from functools import lru_cache
from typing import Callable

from .schemas import DetectAiRequest, DetectAiResponse

HF_MODEL = "Hello-SimpleAI/chatgpt-detector-roberta"
HF_MODEL_REVISION = "d2b342c61775d5dd0221808a79983ed3b86ffd86"
MAX_SEQUENCE_LENGTH = 512
CHUNK_OVERLAP_TOKENS = 64


class AiModelUnavailableError(RuntimeError):
    pass


@lru_cache(maxsize=1)
def _get_classifier() -> Callable[..., list[dict[str, str | float]]]:
    try:
        from transformers import pipeline

        return pipeline(
            "text-classification",
            model=HF_MODEL,
            revision=HF_MODEL_REVISION,
            device=-1,
            token=False,
        )
    except (ImportError, OSError) as e:
        raise AiModelUnavailableError(
            "Could not load the local AI detector. Install the torch and transformers "
            "dependencies and ensure the model can be downloaded from Hugging Face."
        ) from e


def calculate_burstiness(text: str) -> float:
    """
    Measures variation in sentence lengths (std deviation / mean).
    AI models tend to have very uniform sentence lengths.
    """
    sentences = [s.strip() for s in re.split(r"[.!?]+", text) if len(s.strip()) > 3]
    if len(sentences) < 3:
        return 0.5  # Neutral default for very short inputs

    lengths = [len(s.split()) for s in sentences]
    mean_length = sum(lengths) / len(lengths)
    if mean_length == 0:
        return 0.0

    variance = sum((l - mean_length) ** 2 for l in lengths) / len(lengths)
    return round(math.sqrt(variance) / mean_length, 2)


def _split_into_chunks(text: str, tokenizer) -> list[str]:
    token_ids = tokenizer.encode(text, add_special_tokens=False)
    chunk_size = MAX_SEQUENCE_LENGTH - tokenizer.num_special_tokens_to_add(pair=False)
    if len(token_ids) <= chunk_size:
        return [text]

    step = chunk_size - min(CHUNK_OVERLAP_TOKENS, chunk_size - 1)
    return [
        tokenizer.decode(
            token_ids[start : start + chunk_size],
            skip_special_tokens=True,
        )
        for start in range(0, len(token_ids), step)
    ]


def _chunk_probabilities(result: list[dict[str, str | float]]) -> tuple[float, float]:
    ai_prob = 0.0
    human_prob = 0.0

    for classification in result:
        label = str(classification["label"]).lower()
        score = float(classification["score"])

        if label in {"fake", "ai", "gpt", "chatgpt", "1"}:
            ai_prob = score
        elif label in {"real", "human", "0"}:
            human_prob = score

    return ai_prob, human_prob


def detect_ai(req: DetectAiRequest) -> DetectAiResponse:
    burstiness = calculate_burstiness(req.text)
    classifier = _get_classifier()
    tokenizer = getattr(classifier, "tokenizer", None)
    chunks = _split_into_chunks(req.text, tokenizer) if tokenizer else [req.text]

    result = classifier(
        chunks[0] if len(chunks) == 1 else chunks,
        truncation=True,
        max_length=MAX_SEQUENCE_LENGTH,
        top_k=None,
    )
    chunk_results = [result] if len(chunks) == 1 else result
    probabilities = [_chunk_probabilities(chunk) for chunk in chunk_results]
    ai_prob = sum(probability[0] for probability in probabilities) / len(probabilities)
    human_prob = sum(probability[1] for probability in probabilities) / len(probabilities)

    is_ai = ai_prob >= req.threshold

    if is_ai and burstiness < 0.35:
        feedback = (
            "The detector flagged this text as potentially AI-generated and found "
            "uniform sentence lengths. This is an uncertain signal, not proof; review "
            "the work in context."
        )
    elif is_ai:
        feedback = (
            "The detector flagged this text as potentially AI-generated. This is an "
            "uncertain signal, not proof; review the work in context."
        )
    elif not is_ai and burstiness < 0.35:
        feedback = (
            "The detector did not flag this text, but automated detection can miss "
            "AI-generated writing. Sentence lengths are also highly uniform."
        )
    else:
        feedback = (
            "The detector did not flag this text. This result is not proof of human "
            "authorship."
        )

    return DetectAiResponse(
        is_ai_generated=is_ai,
        ai_probability=round(ai_prob, 4),
        human_probability=round(human_prob, 4),
        burstiness_score=burstiness,
        feedback=feedback
    )