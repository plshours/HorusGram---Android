# This file is part of HorusGram for Android, an unofficial Telegram client.
# Copyright (C) 2026 HorusGram contributors. Licensed under the GNU GPL v2 or
# later, see the LICENSE file in the root of this repository.
"""Deep-link handlers. HorusGram accepts them but does not route links to plugins yet."""


class IntentsManager:
    @staticmethod
    def new_global_before_handler(*args, **kwargs):
        return None

    @staticmethod
    def new_global_after_handler(*args, **kwargs):
        return None

    @staticmethod
    def remove_handler(*args, **kwargs):
        return None
