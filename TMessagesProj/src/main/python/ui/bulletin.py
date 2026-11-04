# This file is part of HorusGram for Android, an unofficial Telegram client.
# Copyright (C) 2026 HorusGram contributors. Licensed under the GNU GPL v2 or
# later, see the LICENSE file in the root of this repository.
"""Telegram's bottom notifications (bulletins)."""

import traceback

from android_utils import R, run_on_ui_thread


def _factory(fragment=None):
    from org.telegram.ui.Components import BulletinFactory
    from org.telegram.ui import LaunchActivity
    if fragment is None:
        try:
            fragment = LaunchActivity.getSafeLastFragment()
        except Exception:
            fragment = None
    if fragment is not None:
        try:
            if BulletinFactory.canShowBulletin(fragment):
                return BulletinFactory.of(fragment)
        except Exception:
            return BulletinFactory.of(fragment)
    return BulletinFactory.global_()


def _show(build, fragment=None):
    def run():
        try:
            bulletin = build(_factory(fragment))
            if bulletin is not None:
                bulletin.show()
        except Exception:
            traceback.print_exc()
    run_on_ui_thread(run)


def _raw(name):
    from org.telegram.messenger import R as TR
    return getattr(TR.raw, name)


class BulletinHelper:
    DURATION_SHORT = 1500
    DURATION_LONG = 2750
    DURATION_PROLONG = 5000

    @classmethod
    def show_info(cls, message, fragment=None):
        _show(lambda f: f.createSimpleBulletin(_raw("info"), str(message)), fragment)

    @classmethod
    def show_error(cls, message, fragment=None):
        _show(lambda f: f.createErrorBulletin(str(message)), fragment)

    @classmethod
    def show_success(cls, message, fragment=None):
        _show(lambda f: f.createSuccessBulletin(str(message)), fragment)

    @classmethod
    def show_simple(cls, text, icon_res_id, fragment=None):
        _show(lambda f: f.createSimpleBulletin(int(icon_res_id), str(text)), fragment)

    @classmethod
    def show_two_line(cls, title, subtitle, icon_res_id, fragment=None):
        _show(lambda f: f.createSimpleBulletin(int(icon_res_id), str(title), str(subtitle)), fragment)

    @classmethod
    def show_with_button(cls, text, icon_res_id, button_text, on_click, fragment=None, duration=DURATION_PROLONG):
        action = R(on_click) if on_click else R(lambda: None)
        _show(lambda f: f.createSimpleBulletin(int(icon_res_id), str(text), str(button_text), int(duration), action), fragment)

    @classmethod
    def show_undo(cls, text, on_undo, on_action=None, subtitle=None, fragment=None):
        undo = R(on_undo) if on_undo else None
        action = R(on_action) if on_action else None
        if subtitle:
            _show(lambda f: f.createUndoBulletin(str(text), str(subtitle), undo, action), fragment)
        else:
            _show(lambda f: f.createUndoBulletin(str(text), undo, action), fragment)

    @classmethod
    def show_copied_to_clipboard(cls, message=None, fragment=None):
        if message:
            _show(lambda f: f.createCopyBulletin(str(message)), fragment)
        else:
            from org.telegram.messenger import LocaleController, R as TR
            _show(lambda f: f.createCopyBulletin(LocaleController.getString(TR.string.TextCopied)), fragment)

    @classmethod
    def show_link_copied(cls, is_private_link_info=False, fragment=None):
        _show(lambda f: f.createCopyLinkBulletin(), fragment)

    @classmethod
    def show_file_saved_to_gallery(cls, is_video=False, amount=1, fragment=None):
        from org.telegram.ui.Components import BulletinFactory
        kind = ("VIDEOS" if amount > 1 else "VIDEO") if is_video else ("PHOTOS" if amount > 1 else "PHOTO")
        file_type = getattr(BulletinFactory.FileType, kind)
        _show(lambda f: f.createDownloadBulletin(file_type, int(amount), None), fragment)

    @classmethod
    def show_file_saved_to_downloads(cls, file_type_enum_name="UNKNOWN", amount=1, fragment=None):
        from org.telegram.ui.Components import BulletinFactory
        file_type = getattr(BulletinFactory.FileType, file_type_enum_name, None) or BulletinFactory.FileType.UNKNOWN
        _show(lambda f: f.createDownloadBulletin(file_type, int(amount), None), fragment)
