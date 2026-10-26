import logging
import threading
from typing import Callable

logger = logging.getLogger(__name__)

# Loaders hold this lock for the whole import-and-build of a transformers model. The lazy import is
# not thread-safe ("cannot import name 'AutoModel'"), and from_pretrained briefly patches torch
# globally so new weights start on the "meta" device: two models built at once leave each other's
# weights there, and inference then fails with "Tensor on device meta". Whisper does not use
# transformers, so it still loads in parallel.
transformers_lock = threading.Lock()


class BackgroundLoader:
    """Loads a model on a daemon thread so requests never wait on a slow download.

    `load` must be an lru_cached zero-argument loader: once it has returned, the model is cached
    and `ready()` is true. A failed load can be retried by calling `start()` again.
    """

    def __init__(self, name: str, load: Callable[[], object]):
        self.name = name
        self._load = load
        self._lock = threading.Lock()
        self._thread: threading.Thread | None = None

    def ready(self) -> bool:
        return self._load.cache_info().currsize > 0

    def start(self) -> None:
        with self._lock:
            if self.ready() or (self._thread is not None and self._thread.is_alive()):
                return
            self._thread = threading.Thread(
                target=self._run, name=f"load-{self.name}", daemon=True)
            self._thread.start()

    def _run(self) -> None:
        try:
            self._load()
            logger.info("%s model loaded", self.name)
        except Exception:
            logger.warning("%s model could not be loaded", self.name, exc_info=True)
