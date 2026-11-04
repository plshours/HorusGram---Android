# This file is part of HorusGram for Android, an unofficial Telegram client.
# Copyright (C) 2026 HorusGram contributors. Licensed under the GNU GPL v2 or
# later, see the LICENSE file in the root of this repository.
"""Folders of the app and small file helpers."""

import os


def _context():
    from org.telegram.messenger import ApplicationLoader
    return ApplicationLoader.applicationContext


def get_plugins_dir():
    import horus_extera
    return os.path.join(horus_extera.plugins_dir(), "data")


def get_cache_dir():
    return str(_context().getCacheDir().getAbsolutePath())


def get_files_dir():
    from org.telegram.messenger import ApplicationLoader
    return str(ApplicationLoader.getFilesDirFixed().getAbsolutePath())


def _media_dir(kind):
    from org.telegram.messenger import FileLoader
    try:
        directory = FileLoader.getDirectory(kind)
        if directory is not None:
            return str(directory.getAbsolutePath())
    except Exception:
        pass
    return get_cache_dir()


def get_images_dir():
    from org.telegram.messenger import FileLoader
    return _media_dir(FileLoader.MEDIA_DIR_IMAGE)


def get_videos_dir():
    from org.telegram.messenger import FileLoader
    return _media_dir(FileLoader.MEDIA_DIR_VIDEO)


def get_audios_dir():
    from org.telegram.messenger import FileLoader
    return _media_dir(FileLoader.MEDIA_DIR_AUDIO)


def get_documents_dir():
    from org.telegram.messenger import FileLoader
    return _media_dir(FileLoader.MEDIA_DIR_DOCUMENT)


def ensure_dir_exists(path):
    os.makedirs(path, exist_ok=True)
    return path


def list_dir(path, recursive=False, include_files=True, include_dirs=False, extensions=None):
    result = []
    if not os.path.isdir(path):
        return result
    exts = tuple(e.lower() if e.startswith(".") else "." + e.lower() for e in extensions) if extensions else None
    walker = os.walk(path) if recursive else [(path, [d for d in os.listdir(path) if os.path.isdir(os.path.join(path, d))],
                                                [f for f in os.listdir(path) if os.path.isfile(os.path.join(path, f))])]
    for root, dirs, files in walker:
        if include_dirs:
            result.extend(os.path.join(root, d) for d in dirs)
        if include_files:
            for f in files:
                if exts is None or f.lower().endswith(exts):
                    result.append(os.path.join(root, f))
    return result


def write_file(path, content):
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    return True


def read_file(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return f.read()
    except OSError:
        return None


def write_file_bytes(path, content):
    with open(path, "wb") as f:
        f.write(content)
    return True


def read_file_bytes(path):
    try:
        with open(path, "rb") as f:
            return f.read()
    except OSError:
        return None


def delete_file(path):
    try:
        os.remove(path)
        return True
    except OSError:
        return False
