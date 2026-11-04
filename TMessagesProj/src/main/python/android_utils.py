# This file is part of HorusGram for Android, an unofficial Telegram client.
# Copyright (C) 2026 HorusGram contributors. Licensed under the GNU GPL v2 or
# later, see the LICENSE file in the root of this repository.
"""UI-thread work, listeners, the clipboard and Runnable wrappers."""

import traceback

from java import dynamic_proxy
from java.lang import Runnable
from android.view import View


class R(dynamic_proxy(Runnable)):
    """A java.lang.Runnable that calls a Python callable."""

    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def run(self):
        try:
            self.fn()
        except Exception:
            traceback.print_exc()


class OnClickListener(dynamic_proxy(View.OnClickListener)):
    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def onClick(self, view):
        try:
            self.fn(view)
        except Exception:
            traceback.print_exc()


class OnLongClickListener(dynamic_proxy(View.OnLongClickListener)):
    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def onLongClick(self, view):
        try:
            return bool(self.fn(view))
        except Exception:
            traceback.print_exc()
            return False


def run_on_ui_thread(func, delay=0):
    from org.telegram.messenger import AndroidUtilities
    AndroidUtilities.runOnUIThread(R(func), int(delay))


def log(data):
    from log_utils import Logger, dump
    logger = Logger("plugins")
    if data is None or isinstance(data, (str, int, float, bool)):
        logger.debug("%s", data)
    else:
        logger.debug("%s", dump(data))


def copy_to_clipboard(text):
    from org.telegram.messenger import AndroidUtilities
    ok = AndroidUtilities.addToClipboard(str(text))
    if ok is not False:
        try:
            from ui.bulletin import BulletinHelper
            BulletinHelper.show_copied_to_clipboard()
        except Exception:
            pass
    return ok
