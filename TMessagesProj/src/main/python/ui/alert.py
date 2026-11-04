# This file is part of HorusGram for Android, an unofficial Telegram client.
# Copyright (C) 2026 HorusGram contributors. Licensed under the GNU GPL v2 or
# later, see the LICENSE file in the root of this repository.
"""A Python wrapper of Telegram's AlertDialog.Builder."""

import traceback

from java import dynamic_proxy, jarray, jint
from android.content import DialogInterface
from org.telegram.ui.ActionBar import AlertDialog


class _ButtonListener(dynamic_proxy(AlertDialog.OnButtonClickListener)):
    def __init__(self, builder, fn):
        super().__init__()
        self.builder = builder
        self.fn = fn

    def onClick(self, dialog, which):
        if self.fn:
            try:
                self.fn(self.builder, which)
            except Exception:
                traceback.print_exc()


class _ItemListener(dynamic_proxy(DialogInterface.OnClickListener)):
    def __init__(self, builder, fn):
        super().__init__()
        self.builder = builder
        self.fn = fn

    def onClick(self, dialog, which):
        if self.fn:
            try:
                self.fn(self.builder, which)
            except Exception:
                traceback.print_exc()


class _DismissListener(dynamic_proxy(DialogInterface.OnDismissListener)):
    def __init__(self, builder, fn):
        super().__init__()
        self.builder = builder
        self.fn = fn

    def onDismiss(self, dialog):
        try:
            self.fn(self.builder)
        except Exception:
            traceback.print_exc()


class _CancelListener(dynamic_proxy(DialogInterface.OnCancelListener)):
    def __init__(self, builder, fn):
        super().__init__()
        self.builder = builder
        self.fn = fn

    def onCancel(self, dialog):
        try:
            self.fn(self.builder)
        except Exception:
            traceback.print_exc()


class AlertDialogBuilder:
    ALERT_TYPE_MESSAGE = 0
    ALERT_TYPE_LOADING = 2
    ALERT_TYPE_SPINNER = 3

    BUTTON_POSITIVE = DialogInterface.BUTTON_POSITIVE
    BUTTON_NEGATIVE = DialogInterface.BUTTON_NEGATIVE
    BUTTON_NEUTRAL = DialogInterface.BUTTON_NEUTRAL

    def __init__(self, context, progress_style=ALERT_TYPE_MESSAGE, resources_provider=None):
        self._builder = AlertDialog.Builder(context, int(progress_style), resources_provider)
        self._dialog = None
        self._listeners = []

    def _keep(self, listener):
        self._listeners.append(listener)
        return listener

    def set_title(self, title):
        self._builder.setTitle(title)
        return self

    def set_message(self, message):
        self._builder.setMessage(message)
        return self

    def set_message_text_view_clickable(self, clickable):
        self._builder.setMessageTextViewClickable(bool(clickable))
        return self

    def set_view(self, view, height=-2):
        self._builder.setView(view, int(height))
        return self

    def set_items(self, items, listener=None, icons=None):
        texts = jarray(str)([str(i) for i in items])
        handler = self._keep(_ItemListener(self, listener))
        if icons:
            self._builder.setItems(texts, jarray(jint)([int(i) for i in icons]), handler)
        else:
            self._builder.setItems(texts, handler)
        return self

    def set_positive_button(self, text, listener=None):
        self._builder.setPositiveButton(text, self._keep(_ButtonListener(self, listener)))
        return self

    def set_negative_button(self, text, listener=None):
        self._builder.setNegativeButton(text, self._keep(_ButtonListener(self, listener)))
        return self

    def set_neutral_button(self, text, listener=None):
        self._builder.setNeutralButton(text, self._keep(_ButtonListener(self, listener)))
        return self

    def set_on_back_button_listener(self, listener=None):
        self._builder.setOnBackButtonListener(self._keep(_ButtonListener(self, listener)))
        return self

    def set_on_dismiss_listener(self, listener=None):
        if listener:
            self._builder.setOnDismissListener(self._keep(_DismissListener(self, listener)))
        return self

    def set_on_cancel_listener(self, listener=None):
        if listener:
            self._builder.setOnCancelListener(self._keep(_CancelListener(self, listener)))
        return self

    def set_top_image(self, res_id, background_color):
        self._builder.setTopImage(int(res_id), int(background_color))
        return self

    def set_top_drawable(self, drawable, background_color):
        self._builder.setTopImage(drawable, int(background_color))
        return self

    def set_top_animation(self, res_id, size, auto_repeat, background_color, layer_colors=None):
        if layer_colors:
            from java.util import HashMap
            from java.lang import Integer
            colors = HashMap()
            for k, v in layer_colors.items():
                colors.put(str(k), Integer(int(v)))
            self._builder.setTopAnimation(int(res_id), int(size), bool(auto_repeat), int(background_color), colors)
        else:
            self._builder.setTopAnimation(int(res_id), int(size), bool(auto_repeat), int(background_color))
        return self

    def set_dim_enabled(self, enabled):
        self._builder.setDimEnabled(bool(enabled))
        return self

    def set_dialog_button_color_key(self, theme_key):
        self._builder.setDialogButtonColorKey(int(theme_key))
        return self

    def set_blurred_background(self, blur, blur_behind_if_possible=True):
        self._builder.setBlurredBackground(bool(blur))
        return self

    def create(self):
        if self._dialog is None:
            self._dialog = self._builder.create()
        return self

    def show(self):
        from android_utils import run_on_ui_thread
        self.create()
        dialog = self._dialog
        run_on_ui_thread(dialog.show)
        return self

    def dismiss(self):
        if self._dialog is not None:
            from android_utils import run_on_ui_thread
            dialog = self._dialog
            run_on_ui_thread(dialog.dismiss)
            self._dialog = None

    def get_dialog(self):
        return self._dialog

    def get_button(self, button_type):
        return self._dialog.getButton(int(button_type)) if self._dialog is not None else None

    def make_button_red(self, button_type):
        button = self.get_button(button_type)
        if button is not None:
            from org.telegram.ui.ActionBar import Theme
            try:
                button.setTextColor(Theme.getColor(Theme.key_text_RedBold))
            except Exception:
                pass

    def set_cancelable(self, cancelable):
        if self._dialog is not None:
            self._dialog.setCancelable(bool(cancelable))
        return self

    def set_canceled_on_touch_outside(self, cancel):
        if self._dialog is not None:
            self._dialog.setCanceledOnTouchOutside(bool(cancel))
        return self

    def set_progress(self, progress):
        if self._dialog is not None:
            from android_utils import run_on_ui_thread
            dialog = self._dialog
            run_on_ui_thread(lambda: dialog.setProgress(int(progress)))
        return self
