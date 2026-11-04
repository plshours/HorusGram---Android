# This file is part of HorusGram for Android, an unofficial Telegram client.
# Copyright (C) 2026 HorusGram contributors. Licensed under the GNU GPL v2 or
# later, see the LICENSE file in the root of this repository.
"""BasePlugin and the hook types, with the names exteraGram plugins use."""

import json
import os
import threading
import uuid
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Callable, Dict, List, Optional

from log_utils import Logger

_scope = threading.local()


def _with_scope(account, fn, *args):
    """Runs fn with `account` as the account being handled (get_hook_account)."""
    previous = getattr(_scope, "account", None)
    _scope.account = account
    try:
        return fn(*args)
    finally:
        _scope.account = previous


def current_hook_account():
    return getattr(_scope, "account", None)


class HookStrategy(Enum):
    DEFAULT = 0
    CANCEL = 1
    MODIFY = 2
    MODIFY_FINAL = 3


class HookResult:
    def __init__(self, strategy=HookStrategy.DEFAULT, request=None, response=None,
                 update=None, updates=None, params=None):
        self.strategy = strategy
        self.request = request
        self.response = response
        self.update = update
        self.updates = updates
        self.params = params


class AppEvent(Enum):
    START = "START"
    STOP = "STOP"
    PAUSE = "PAUSE"
    RESUME = "RESUME"


class MenuItemType(Enum):
    MESSAGE_CONTEXT_MENU = "MESSAGE_CONTEXT_MENU"
    DRAWER_MENU = "DRAWER_MENU"
    MAIN_MENU = "MAIN_MENU"
    CHAT_ACTION_MENU = "CHAT_ACTION_MENU"
    PROFILE_ACTION_MENU = "PROFILE_ACTION_MENU"


@dataclass
class MenuItemData:
    menu_type: MenuItemType
    text: str
    on_click: Callable[[Dict[str, Any]], None]
    item_id: Optional[str] = None
    icon: Optional[str] = None
    subtext: Optional[str] = None
    condition: Optional[str] = None
    priority: int = 0


# ---- Xposed-style method hooks ----

def hook_filters(*filters):
    def decorate(fn):
        fn._horus_filters = list(filters)
        return fn
    return decorate


def _passes(filters, param):
    for f in filters or ():
        try:
            if not f(param):
                return False
        except Exception:
            return False
    return True


class MethodHook:
    """before_hooked_method / after_hooked_method around a Java method."""

    def before_hooked_method(self, param):
        pass

    def after_hooked_method(self, param):
        pass

    def _horus_before(self, param):
        method = self.before_hooked_method
        if _passes(getattr(method, "_horus_filters", None), param):
            method(param)

    def _horus_after(self, param):
        method = self.after_hooked_method
        if _passes(getattr(method, "_horus_filters", None), param):
            method(param)


class MethodReplacement(MethodHook):
    """replace_hooked_method becomes the whole method."""

    def replace_hooked_method(self, param):
        return None

    def _horus_before(self, param):
        method = self.replace_hooked_method
        if not _passes(getattr(method, "_horus_filters", None), param):
            return
        try:
            param.setResult(method(param))
        except Exception as e:
            from java.lang import RuntimeException
            param.setThrowable(RuntimeException(str(e)))

    def _horus_after(self, param):
        pass


class BaseHook(MethodHook):
    def __init__(self, before=None, after=None, before_filters=None, after_filters=None):
        self._before = before
        self._after = after
        self._before_filters = before_filters or []
        self._after_filters = after_filters or []

    def _horus_before(self, param):
        if self._before and _passes(self._before_filters, param):
            self._before(param)

    def _horus_after(self, param):
        if self._after and _passes(self._after_filters, param):
            self._after(param)


def _java_equals(a, b):
    try:
        return a == b or (a is not None and a.equals(b))
    except Exception:
        return a == b


class HookFilter:
    @staticmethod
    def RESULT_IS_NULL(p):
        return p.getResult() is None

    @staticmethod
    def RESULT_NOT_NULL(p):
        return p.getResult() is not None

    @staticmethod
    def RESULT_IS_TRUE(p):
        return p.getResult() is True or p.getResult() == True  # noqa: E712

    @staticmethod
    def RESULT_IS_FALSE(p):
        return p.getResult() is False or p.getResult() == False  # noqa: E712

    @staticmethod
    def ResultIsInstanceOf(clazz):
        return lambda p: p.getResult() is not None and clazz.isInstance(p.getResult())

    @staticmethod
    def ResultEqual(value):
        return lambda p: _java_equals(p.getResult(), value)

    @staticmethod
    def ResultNotEqual(value):
        return lambda p: not _java_equals(p.getResult(), value)

    @staticmethod
    def ArgumentIsNull(index):
        return lambda p: p.args[index] is None

    @staticmethod
    def ArgumentNotNull(index):
        return lambda p: p.args[index] is not None

    @staticmethod
    def ArgumentIsFalse(index):
        return lambda p: p.args[index] == False  # noqa: E712

    @staticmethod
    def ArgumentIsTrue(index):
        return lambda p: p.args[index] == True  # noqa: E712

    @staticmethod
    def ArgumentIsInstanceOf(index, clazz):
        return lambda p: p.args[index] is not None and clazz.isInstance(p.args[index])

    @staticmethod
    def ArgumentEqual(index, value):
        return lambda p: _java_equals(p.args[index], value)

    @staticmethod
    def ArgumentNotEqual(index, value):
        return lambda p: not _java_equals(p.args[index], value)

    @staticmethod
    def Condition(condition, object=None):
        # exteraGram evaluates these with MVEL, which HorusGram does not have.
        # A Python callable works here; a text condition always passes.
        if callable(condition):
            return lambda p: condition(p) if object is None else condition(p, object)
        return lambda p: True

    @staticmethod
    def Or(*filters):
        return lambda p: any(_passes([f], p) for f in filters)


# ---- The plugin class ----

class BasePlugin:
    def __init__(self):
        pass

    def _horus_setup(self, plugin_id, meta, path):
        self.id = plugin_id
        self.name = meta.get("name", plugin_id)
        self.description = meta.get("description", "")
        self.author = meta.get("author", "")
        self.version = meta.get("version", "1.0")
        self.icon = meta.get("icon")
        self.path = path
        self.logger = Logger("plugin." + plugin_id)
        self._horus_hooks = {"send": [], "request": []}
        self._horus_method_hooks = []
        self._horus_menu_items = {}
        self._horus_settings = None
        self._horus_settings_lock = threading.RLock()

    # Java code (exteraGram's PluginsController) calls these on the plugin.
    def getId(self):
        return self.id

    def getName(self):
        return self.name

    # Lifecycle, for the plugin to override.
    def on_plugin_load(self):
        pass

    def on_plugin_unload(self):
        pass

    def on_app_event(self, event_type):
        pass

    def create_settings(self) -> List[Any]:
        return []

    def on_send_message_hook(self, account, params):
        return HookResult()

    def pre_request_hook(self, request_name, account, request):
        return HookResult()

    def post_request_hook(self, request_name, account, response, error):
        return HookResult()

    def on_update_hook(self, update_name, account, update):
        return HookResult()

    def on_updates_hook(self, container_name, account, updates):
        return HookResult()

    # Logging.
    def log(self, message):
        self.logger.debug("%s", message)

    # Accounts.
    def client(self, account=None):
        import client_utils
        if account is None:
            account = current_hook_account()
        return client_utils.get_client(account)

    # Settings.
    def _settings_path(self):
        import horus_extera
        return os.path.join(horus_extera.plugins_dir(), "settings", self.id + ".json")

    def _load_settings(self):
        with self._horus_settings_lock:
            if self._horus_settings is None:
                try:
                    with open(self._settings_path(), "r", encoding="utf-8") as f:
                        self._horus_settings = json.load(f)
                except Exception:
                    self._horus_settings = {}
            return self._horus_settings

    def _save_settings(self):
        with self._horus_settings_lock:
            path = self._settings_path()
            temp = path + ".tmp"
            with open(temp, "w", encoding="utf-8") as f:
                json.dump(self._horus_settings or {}, f, ensure_ascii=False)
            os.replace(temp, path)

    def get_setting(self, key, default=None):
        return self._load_settings().get(key, default)

    def set_setting(self, key, value, reload_settings=False):
        with self._horus_settings_lock:
            self._load_settings()[key] = value
            self._save_settings()
        if reload_settings:
            self._reload_settings()

    def export_settings(self):
        return dict(self._load_settings())

    def import_settings(self, settings, reload_settings=True):
        with self._horus_settings_lock:
            self._load_settings().update(settings or {})
            self._save_settings()
        if reload_settings:
            self._reload_settings()

    def _reload_settings(self):
        from com.horusgram.extera import ExteraPlugins
        ExteraPlugins.reloadSettings(self.id)

    # Event hooks.
    def add_hook(self, name, match_substring=False, priority=0):
        from com.horusgram.extera import ExteraPlugins
        self._horus_hooks["request"].append((name, priority, bool(match_substring)))
        ExteraPlugins.registerHook(self.id, name, bool(match_substring))

    def remove_hook(self, name):
        from com.horusgram.extera import ExteraPlugins
        self._horus_hooks["request"] = [h for h in self._horus_hooks["request"] if h[0] != name]
        ExteraPlugins.unregisterHook(self.id, name)

    def add_on_send_message_hook(self, priority=0):
        from com.horusgram.extera import ExteraPlugins
        self._horus_hooks["send"] = [("send", priority, False)]
        ExteraPlugins.setSendHook(self.id, True)

    def remove_on_send_message_hook(self):
        from com.horusgram.extera import ExteraPlugins
        self._horus_hooks["send"] = []
        ExteraPlugins.setSendHook(self.id, False)

    # Menu items.
    def add_menu_item(self, data: MenuItemData):
        import horus_menu
        item_id = data.item_id or uuid.uuid4().hex
        self._horus_menu_items[item_id] = data
        horus_menu.changed()
        return item_id

    def remove_menu_item(self, item_id):
        import horus_menu
        if self._horus_menu_items.pop(item_id, None) is not None:
            horus_menu.changed()
            return True
        return False

    # Method hooks.
    def hook_method(self, method, handler=None, priority=0, before=None, after=None,
                    before_filters=None, after_filters=None):
        from com.horusgram.extera import ExteraMethodHook
        if handler is None:
            handler = BaseHook(before, after, before_filters, after_filters)
        unhook = ExteraMethodHook.hook(method, handler, int(priority))
        if unhook is not None:
            self._horus_method_hooks.append((unhook, handler))
        return unhook

    def hook_all_methods(self, clazz, method_name, handler, priority=0):
        result = []
        for method in clazz.getDeclaredMethods():
            if method.getName() == method_name:
                unhook = self.hook_method(method, handler, priority)
                if unhook is not None:
                    result.append(unhook)
        return result

    def hook_all_constructors(self, clazz, handler, priority=0):
        result = []
        for constructor in clazz.getDeclaredConstructors():
            unhook = self.hook_method(constructor, handler, priority)
            if unhook is not None:
                result.append(unhook)
        return result

    def unhook_method(self, unhook):
        from com.horusgram.extera import ExteraMethodHook
        ExteraMethodHook.unhook(unhook)
        self._horus_method_hooks = [h for h in self._horus_method_hooks if h[0] is not unhook]

    def _horus_cleanup(self):
        from com.horusgram.extera import ExteraMethodHook
        for unhook, _handler in getattr(self, "_horus_method_hooks", []):
            ExteraMethodHook.unhook(unhook)
        self._horus_method_hooks = []
        if getattr(self, "_horus_menu_items", None):
            import horus_menu
            self._horus_menu_items = {}
            horus_menu.changed()
        try:
            import ui.settings as settings
            settings._release_factories(self)
        except Exception:
            pass
