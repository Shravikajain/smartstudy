import logging
import threading
from typing import Callable

logger = logging.getLogger(__name__)

# Concurrent first imports from transformers' lazy module are not thread-safe ("cannot import
# name 'AutoModel'"), so loaders hold this lock while importing it. Downloads still run in parallel.
transformers_import_lock = threading.Lock()


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
