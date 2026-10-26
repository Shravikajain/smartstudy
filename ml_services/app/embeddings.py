from functools import lru_cache

import numpy as np

from .background import BackgroundLoader, transformers_lock

EMBEDDING_MODEL = "sentence-transformers/all-MiniLM-L6-v2"
EMBEDDING_MODEL_REVISION = "1110a243fdf4706b3f48f1d95db1a4f5529b4d41"
MAX_EMBEDDING_TOKENS = 256
EMBEDDING_BATCH_SIZE = 64


class EmbeddingModelUnavailableError(RuntimeError):
    pass


@lru_cache(maxsize=1)
def _load_encoder():
    try:
        with transformers_lock:
            from transformers import AutoModel, AutoTokenizer

            tokenizer = AutoTokenizer.from_pretrained(
                EMBEDDING_MODEL, revision=EMBEDDING_MODEL_REVISION, token=False)
            model = AutoModel.from_pretrained(
                EMBEDDING_MODEL, revision=EMBEDDING_MODEL_REVISION, token=False)
            return tokenizer, model.eval()
    except (ImportError, OSError) as e:
        raise EmbeddingModelUnavailableError(
            "Could not load the local sentence-embedding model."
        ) from e


encoder_loader = BackgroundLoader("Sentence embedding", _load_encoder)


def embed_if_ready(texts: list[str]) -> np.ndarray | None:
    """Embeddings once the model is loaded; until then None, so callers never wait on a download."""
    if not encoder_loader.ready():
        encoder_loader.start()
        return None
    return embed(texts)


def embed(texts: list[str]) -> np.ndarray:
    """Return one L2-normalized sentence embedding per text (mean-pooled MiniLM)."""
    import torch

    tokenizer, model = _load_encoder()
    vectors = []
    with torch.inference_mode():
        for start in range(0, len(texts), EMBEDDING_BATCH_SIZE):
            batch = tokenizer(
                texts[start : start + EMBEDDING_BATCH_SIZE],
                padding=True,
                truncation=True,
                max_length=MAX_EMBEDDING_TOKENS,
                return_tensors="pt",
            )
            hidden = model(**batch).last_hidden_state
            mask = batch["attention_mask"].unsqueeze(-1).to(hidden.dtype)
            pooled = (hidden * mask).sum(dim=1) / mask.sum(dim=1).clamp(min=1e-9)
            vectors.append(torch.nn.functional.normalize(pooled, dim=1).numpy())
    return np.vstack(vectors)
