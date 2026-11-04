# This file is part of HorusGram for Android, an unofficial Telegram client.
# Copyright (C) 2026 HorusGram contributors. Licensed under the GNU GPL v2 or
# later, see the LICENSE file in the root of this repository.
"""The plugin logger: levels, a logcat tag per plugin, tracebacks and dumps."""

import contextlib
import inspect
import os
import sys
import time
import traceback
from dataclasses import dataclass
from enum import IntEnum


class LogLevel(IntEnum):
    VERBOSE = 2
    DEBUG = 3
    INFO = 4
    WARNING = 5
    ERROR = 6
    OFF = 100


_NAMES = {name.lower(): level for name, level in LogLevel.__members__.items()}


def _level(value):
    if value is None or isinstance(value, LogLevel):
        return value
    if isinstance(value, str):
        return _NAMES[value.lower()]
    return LogLevel(int(value))


@dataclass
class LogRecord:
    logger: str
    level: LogLevel
    message: str
    location: str
    time: float


def _android_log(level, tag, text):
    try:
        from android.util import Log
        Log.println(int(min(level, LogLevel.ERROR)), tag[:100], text)
    except Exception:
        print(f"{tag}: {text}", file=sys.stderr)


class Logger:
    def __init__(self, name, parent=None):
        self.name = name
        self.parent = parent
        self._level = None
        self.handlers = []

    @property
    def level(self):
        if self._level is not None:
            return self._level
        if self.parent is not None:
            return self.parent.level
        import horus_extera
        return LogLevel.DEBUG if horus_extera.debug else LogLevel.INFO

    @level.setter
    def level(self, value):
        self._level = _level(value)

    def child(self, name):
        return Logger(f"{self.name}.{name}", self)

    def add_handler(self, handler):
        self.handlers.append(handler)

    def remove_handler(self, handler):
        if handler in self.handlers:
            self.handlers.remove(handler)

    def is_enabled_for(self, level):
        return level >= self.level

    def _emit(self, level, message, args, exc_info=None):
        if not self.is_enabled_for(level):
            return
        try:
            text = (str(message) % args) if args else str(message)
        except Exception:
            text = f"{message} {args}"
        frame = inspect.currentframe()
        for _ in range(2):
            if frame is not None:
                frame = frame.f_back
        location = ""
        if frame is not None:
            location = f"[{os.path.basename(frame.f_code.co_filename)}:{frame.f_code.co_name}:{frame.f_lineno}] "
        if exc_info:
            if exc_info is True:
                text += "\n" + traceback.format_exc()
            elif isinstance(exc_info, BaseException):
                text += "\n" + "".join(traceback.format_exception(type(exc_info), exc_info, exc_info.__traceback__))
        _android_log(level, self.name, location + text)
        record = LogRecord(self.name, LogLevel(level), text, location.strip(), time.time())
        logger = self
        while logger is not None:
            for handler in list(logger.handlers):
                try:
                    handler(record)
                except Exception:
                    pass
            logger = logger.parent

    def verbose(self, message, *args, **kwargs):
        self._emit(LogLevel.VERBOSE, message, args, kwargs.get("exc_info"))

    def debug(self, message, *args, **kwargs):
        self._emit(LogLevel.DEBUG, message, args, kwargs.get("exc_info"))

    def info(self, message, *args, **kwargs):
        self._emit(LogLevel.INFO, message, args, kwargs.get("exc_info"))

    def warning(self, message, *args, **kwargs):
        self._emit(LogLevel.WARNING, message, args, kwargs.get("exc_info"))

    warn = warning

    def error(self, message, *args, **kwargs):
        self._emit(LogLevel.ERROR, message, args, kwargs.get("exc_info"))

    def exception(self, message, *args, **kwargs):
        self._emit(LogLevel.ERROR, message, args, kwargs.get("exc_info", True))

    def dump(self, obj, name=None, depth=2, max_items=20):
        if not self.is_enabled_for(LogLevel.DEBUG):
            return
        text = dump(obj, depth=depth, max_items=max_items)
        self._emit(LogLevel.DEBUG, "%s: %s" % (name, text) if name else text, ())

    @contextlib.contextmanager
    def timed(self, label, level=LogLevel.DEBUG):
        start = time.perf_counter()
        failed = False
        try:
            yield
        except BaseException:
            failed = True
            raise
        finally:
            took = (time.perf_counter() - start) * 1000
            self._emit(level, "%s took %.1f ms%s", (label, took, " (failed)" if failed else ""))


_PLATFORM_PREFIXES = ("java.", "javax.", "android.", "androidx.", "kotlin.", "dalvik.")


def _is_java(obj):
    return type(obj).__module__.startswith("java") or hasattr(obj, "getClass") and hasattr(type(obj), "_chaquopy_name")


def dump(obj, depth=2, max_items=20):
    lines = []
    seen = set()

    def short(value):
        if value is None or isinstance(value, (bool, int, float)):
            return repr(value)
        if isinstance(value, str):
            return repr(value)
        return None

    def walk(value, indent, level, label):
        prefix = "  " * indent + (f"{label} = " if label else "")
        simple = short(value)
        if simple is not None:
            lines.append(prefix + simple)
            return
        ident = id(value)
        if ident in seen:
            lines.append(prefix + "<cycle>")
            return
        if isinstance(value, (list, tuple, set)):
            lines.append(prefix + f"{type(value).__name__}[{len(value)}]")
            if level >= depth:
                return
            seen.add(ident)
            for i, item in enumerate(list(value)[:max_items]):
                walk(item, indent + 1, level + 1, f"[{i}]")
            return
        if isinstance(value, dict):
            lines.append(prefix + f"dict[{len(value)}]")
            if level >= depth:
                return
            seen.add(ident)
            for i, (k, v) in enumerate(list(value.items())[:max_items]):
                walk(v, indent + 1, level + 1, repr(k))
            return
        try:
            clazz = value.getClass()
        except Exception:
            clazz = None
        if clazz is not None:
            name = clazz.getName()
            if name.startswith(_PLATFORM_PREFIXES) or clazz.isArray():
                try:
                    if clazz.isArray():
                        lines.append(prefix + f"{clazz.getComponentType().getName()}[{len(value)}]")
                    else:
                        lines.append(prefix + str(value.toString()))
                except Exception:
                    lines.append(prefix + name)
                return
            title = name.rsplit(".", 1)[-1].replace("$", ".")
            lines.append(prefix + f"{title} @{abs(hash(ident)) % 0xffffff:06x}")
            if level >= depth:
                lines[-1] += " {…}"
                return
            seen.add(ident)
            from java.lang.reflect import Modifier
            fields = []
            c = clazz
            while c is not None and not c.getName().startswith(_PLATFORM_PREFIXES):
                for f in c.getDeclaredFields():
                    if not Modifier.isStatic(f.getModifiers()):
                        fields.append(f)
                c = c.getSuperclass()
            for f in sorted(fields, key=lambda f: f.getName()):
                try:
                    f.setAccessible(True)
                    walk(f.get(value), indent + 1, level + 1, f.getName())
                except Exception:
                    pass
            return
        lines.append(prefix + f"{type(value).__name__}")
        if level >= depth:
            return
        seen.add(ident)
        for k, v in sorted(getattr(value, "__dict__", {}).items()):
            walk(v, indent + 1, level + 1, k)

    walk(obj, 0, 0, None)
    return "\n".join(lines)


def get_logger(name=None):
    import horus_extera
    frame = inspect.currentframe().f_back
    filename = frame.f_code.co_filename if frame else ""
    for plugin in horus_extera.loaded_plugins():
        if getattr(plugin, "path", None) == filename:
            return plugin.logger if not name or name == plugin.id else plugin.logger.child(name)
    return Logger("plugin." + (name or "unknown"))
