import io
import json
import logging
import os
import threading

from gallery_dl import config, exception, job

_init_lock = threading.Lock()
_cancel_lock = threading.Lock()
_configured_root = None
_cancelled_slots = set()


def _is_cancelled(slot_id):
    with _cancel_lock:
        return int(slot_id) in _cancelled_slots


def cancel_slot(slot_id):
    with _cancel_lock:
        _cancelled_slots.add(int(slot_id))


def reset_slot(slot_id):
    with _cancel_lock:
        _cancelled_slots.discard(int(slot_id))


def _configure(base_dir):
    global _configured_root
    base_dir = os.path.abspath(base_dir)
    with _init_lock:
        if _configured_root == base_dir:
            return
        os.makedirs(base_dir, exist_ok=True)
        archive_path = os.path.join(base_dir, ".reeldrop-archive.sqlite3")
        config.clear()
        config.set(("extractor",), "base-directory", base_dir)
        config.set(("extractor",), "archive", archive_path)
        config.set(("extractor",), "skip", True)
        config.set(("extractor",), "retries", 4)
        config.set(("extractor",), "timeout", 30.0)
        config.set(("extractor",), "sleep-retries", "lin=2")
        config.set(("extractor",), "sleep-429", 60.0)
        config.set(("extractor", "instagram"), "sleep-request", "6.0-12.0")
        config.set(("extractor", "instagram"), "videos", True)
        config.set(("extractor", "instagram"), "max-posts", None)
        config.set(("extractor", "instagram"), "include", "posts")
        config.set(("extractor", "instagram"), "api", "rest")
        config.set(("extractor", "instagram"), "user-cache", "disk")
        _configured_root = base_dir


class _ThreadFilter(logging.Filter):
    def __init__(self, thread_id):
        super().__init__()
        self.thread_id = thread_id

    def filter(self, record):
        return record.thread == self.thread_id


class _SlotDownloadJob(job.DownloadJob):
    def __init__(self, url, slot_id):
        self._slot_id = int(slot_id)
        super().__init__(url)

    def dispatch(self, messages):
        def guarded():
            for message in messages:
                if _is_cancelled(self._slot_id):
                    raise exception.StopExtraction()
                yield message
        return super().dispatch(guarded())


def source_dir(platform, source_key, base_dir):
    source_key = str(source_key).lstrip("#").strip().lower()
    if str(platform).upper() == "INSTAGRAM_HASHTAG":
        return os.path.join(base_dir, "instagram", "tag", source_key)
    return os.path.join(base_dir, "instagram", source_key)


def run_download(slot_id, platform, url, source_key, base_dir):
    slot_id = int(slot_id)
    reset_slot(slot_id)
    _configure(base_dir)
    target = source_dir(platform, source_key, base_dir)
    os.makedirs(target, exist_ok=True)

    stream = io.StringIO()
    handler = logging.StreamHandler(stream)
    handler.setLevel(logging.INFO)
    handler.setFormatter(logging.Formatter("%(levelname)s: %(message)s"))
    handler.addFilter(_ThreadFilter(threading.get_ident()))
    root = logging.getLogger()
    previous_level = root.level
    root.setLevel(logging.INFO)
    root.addHandler(handler)

    status = 1
    error = ""
    try:
        runner = _SlotDownloadJob(str(url), slot_id)
        status = int(runner.run())
    except BaseException as exc:
        status = 1
        error = f"{exc.__class__.__name__}: {exc}"
    finally:
        root.removeHandler(handler)
        root.setLevel(previous_level)
        handler.close()

    cancelled = _is_cancelled(slot_id)
    reset_slot(slot_id)
    lines = [line.strip() for line in stream.getvalue().splitlines() if line.strip()]
    if not error and status:
        meaningful = [line for line in lines if "warning" not in line.lower()]
        error = (meaningful or lines or [f"gallery-dl çıkış kodu: {status}"])[-1]

    return json.dumps({
        "status": status,
        "cancelled": cancelled,
        "error": error,
        "source_dir": target,
        "log": "\n".join(lines[-40:]),
    }, ensure_ascii=False)
