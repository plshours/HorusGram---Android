# This file is part of HorusGram for Android, an unofficial Telegram client.
# Copyright (C) 2026 HorusGram contributors. Licensed under the GNU GPL v2 or
# later, see the LICENSE file in the root of this repository.
"""The host that runs exteraGram plugins in HorusGram.

ExteraPlugins.java calls the functions here. A plugin is a single Python
file with top-level metadata (__id__, __name__, ...) and one BasePlugin
subclass, the format exteraGram documents at plugins.exteragram.app; the
modules it imports (base_plugin, client_utils, ui.settings, ...) are
HorusGram's own versions of the SDK, next to this file.
"""

import ast
import json
import os
import sys
import threading
import traceback
import types

_lock = threading.RLock()
_plugins = {}  # id -> BasePlugin instance
_dir = None
debug = False

_META_KEYS = {
    "__id__": "id",
    "__name__": "name",
    "__description__": "description",
    "__author__": "author",
    "__version__": "version",
    "__icon__": "icon",
    "__app_version__": "app_version",
    "__min_version__": "app_version",
    "__sdk_version__": "sdk_version",
    "__requirements__": "requirements",
    "__category__": "category",
}


def init(directory, is_debug=False):
    global _dir, debug
    _dir = directory
    debug = bool(is_debug)
    for sub in ("settings", "data", "site-packages"):
        os.makedirs(os.path.join(directory, sub), exist_ok=True)
    packages = os.path.join(directory, "site-packages")
    if packages not in sys.path:
        sys.path.append(packages)


def plugins_dir():
    return _dir


def get_plugin(plugin_id):
    return _plugins.get(plugin_id)


def loaded_plugins():
    with _lock:
        return list(_plugins.values())


def read_meta(source):
    """The literal top-level metadata of a plugin, read without running it."""
    tree = ast.parse(source)
    meta = {}
    for node in tree.body:
        if isinstance(node, ast.Assign) and len(node.targets) == 1:
            target = node.targets[0]
        elif isinstance(node, ast.AnnAssign) and node.value is not None:
            target = node.target
        else:
            continue
        if not isinstance(target, ast.Name) or target.id not in _META_KEYS:
            continue
        try:
            value = ast.literal_eval(node.value)
        except Exception:
            continue
        key = _META_KEYS[target.id]
        if target.id == "__min_version__":
            value = ">=" + str(value)
        meta[key] = value
    return meta


def _valid_id(value):
    if not isinstance(value, str) or not (2 <= len(value) <= 32):
        return False
    if not value[0].isascii() or not value[0].isalpha():
        return False
    return all(c.isascii() and (c.isalnum() or c in "_-") for c in value)


def parse_meta(source):
    try:
        meta = read_meta(source)
    except SyntaxError:
        return None
    if not _valid_id(meta.get("id")) or not meta.get("name"):
        return None
    meta.setdefault("version", "1.0")
    meta.setdefault("author", "")
    meta.setdefault("description", "")
    return json.dumps({k: (v if isinstance(v, (str, int, float, bool, list)) else str(v)) for k, v in meta.items()})


def _install_requirements(plugin_id, requirements):
    if not requirements:
        return
    import pip_lite
    pip_lite.install(requirements, os.path.join(_dir, "site-packages"))


def load_plugin(plugin_id, path):
    """Runs the plugin file and starts its plugin class. Returns an error text or None."""
    import base_plugin
    with _lock:
        unload_plugin(plugin_id)
        try:
            with open(path, "r", encoding="utf-8") as f:
                source = f.read()
            meta = read_meta(source)
            requirements = meta.get("requirements")
            if isinstance(requirements, str):
                requirements = [requirements]
            _install_requirements(plugin_id, requirements or [])

            module = types.ModuleType(plugin_id)
            module.__file__ = path
            module.__dict__["__builtins__"] = __builtins__
            sys.modules[plugin_id] = module
            code = compile(source, path, "exec")
            exec(code, module.__dict__)

            classes = [
                value for value in module.__dict__.values()
                if isinstance(value, type)
                and issubclass(value, base_plugin.BasePlugin)
                and value is not base_plugin.BasePlugin
                and value.__module__ == module.__name__
            ]
            if not classes:
                return "No BasePlugin subclass in the plugin"
            cls = classes[-1]
            instance = cls.__new__(cls)
            base_plugin.BasePlugin._horus_setup(instance, plugin_id, meta, path)
            cls.__init__(instance)
            _plugins[plugin_id] = instance
            base_plugin._with_scope(None, instance.on_plugin_load)
            return None
        except BaseException:
            sys.modules.pop(plugin_id, None)
            failed = _plugins.pop(plugin_id, None)
            if failed is not None:
                try:
                    failed._horus_cleanup()
                except Exception:
                    pass
            return traceback.format_exc()


def unload_plugin(plugin_id):
    with _lock:
        plugin = _plugins.pop(plugin_id, None)
        if plugin is None:
            return
        try:
            plugin.on_plugin_unload()
        except Exception:
            plugin.logger.exception("on_plugin_unload failed")
        plugin._horus_cleanup()
        sys.modules.pop(plugin_id, None)


def forget_plugin(plugin_id):
    for sub, ext in (("settings", ".json"),):
        try:
            os.remove(os.path.join(_dir, sub, plugin_id + ext))
        except OSError:
            pass


def has_settings(plugin_id):
    plugin = _plugins.get(plugin_id)
    if plugin is None:
        return False
    return type(plugin).create_settings is not __import__("base_plugin").BasePlugin.create_settings


def _sorted(kind):
    with _lock:
        hooks = []
        for plugin in _plugins.values():
            for entry in plugin._horus_hooks.get(kind, ()):
                hooks.append((entry, plugin))
    hooks.sort(key=lambda item: -item[0][1])
    return hooks


def _matches(entry, name):
    hook_name, _priority, substring = entry
    return name == hook_name or (substring and hook_name in name)


# ---- Hooks, called by ExteraPlugins.java ----

def send_message_hook(account, params):
    """True to cancel the message. The plugins change `params` in place."""
    from base_plugin import HookStrategy, _with_scope
    for (_name, _priority, _sub), plugin in _sorted("send"):
        try:
            result = _with_scope(account, plugin.on_send_message_hook, account, params)
        except Exception:
            plugin.logger.exception("on_send_message_hook failed")
            continue
        if result is None:
            continue
        strategy = getattr(result, "strategy", HookStrategy.DEFAULT)
        if strategy == HookStrategy.CANCEL:
            return True
        if strategy in (HookStrategy.MODIFY, HookStrategy.MODIFY_FINAL):
            new_params = getattr(result, "params", None)
            if new_params is not None and new_params is not params:
                _copy_params(new_params, params)
            if strategy == HookStrategy.MODIFY_FINAL:
                break
    return False


def _copy_params(source, target):
    for field in ("message", "caption", "entities", "peer", "replyToMsg", "replyToTopMsg",
                  "replyMarkup", "notify", "scheduleDate", "ttl", "hasMediaSpoilers", "searchLinks"):
        try:
            setattr(target, field, getattr(source, field))
        except Exception:
            pass


def _run_hooks(kind, method, attr, name, account, value, *extra):
    """None to leave it, False to cancel, or the replacement object."""
    from base_plugin import HookStrategy, _with_scope
    changed = None
    for entry, plugin in _sorted("request"):
        if not _matches(entry, name):
            continue
        handler = getattr(plugin, method, None)
        if handler is None:
            continue
        try:
            result = _with_scope(account, handler, name, account, value, *extra)
        except Exception:
            plugin.logger.exception("%s failed", method)
            continue
        if result is None:
            continue
        strategy = getattr(result, "strategy", HookStrategy.DEFAULT)
        if strategy == HookStrategy.CANCEL:
            return False
        if strategy in (HookStrategy.MODIFY, HookStrategy.MODIFY_FINAL):
            new_value = getattr(result, attr, None)
            if new_value is not None:
                value = new_value
                changed = new_value
            if strategy == HookStrategy.MODIFY_FINAL:
                break
    return changed


def pre_request_hook(name, account, request):
    return _run_hooks("request", "pre_request_hook", "request", name, account, request)


def post_request_hook(name, account, response, error):
    return _run_hooks("request", "post_request_hook", "response", name, account, response, error)


def update_hook(name, account, update):
    return _run_hooks("request", "on_update_hook", "update", name, account, update)


def updates_hook(name, account, updates):
    return _run_hooks("request", "on_updates_hook", "updates", name, account, updates)


def app_event(event):
    from base_plugin import AppEvent
    value = getattr(AppEvent, event, None)
    if value is None:
        return
    for plugin in loaded_plugins():
        try:
            plugin.on_app_event(value)
        except Exception:
            plugin.logger.exception("on_app_event failed")


# ---- Settings screen, called by ExteraPluginSettingsActivity.java ----

def _items(plugin_id, path):
    plugin = _plugins.get(plugin_id)
    if plugin is None:
        return None, []
    items = plugin.create_settings() or []
    for index in json.loads(path or "[]"):
        item = items[index]
        builder = getattr(item, "create_sub_fragment", None)
        items = (builder() if builder else []) or []
    return plugin, list(items)


def settings_json(plugin_id, path):
    from ui import settings as ui
    try:
        plugin, items = _items(plugin_id, path)
    except Exception:
        traceback.print_exc()
        return "[]"
    if plugin is None:
        return "[]"
    out = []
    for item in items:
        kind = type(item).__name__.lower()
        entry = {"type": kind}
        for field in ("key", "text", "subtext", "icon", "hint", "accent", "red", "multiline", "max_length"):
            value = getattr(item, field, None)
            if value is not None:
                entry[field] = value
        if isinstance(item, ui.Selector):
            entry["items"] = [str(x) for x in (item.items or [])]
        if getattr(item, "create_sub_fragment", None):
            entry["sub"] = True
        if getattr(item, "on_click", None):
            entry["clickable"] = True
        key = getattr(item, "key", None)
        if key is not None:
            default = getattr(item, "default", None)
            value = plugin.get_setting(key, default)
            try:
                entry["value"] = json.loads(json.dumps(value))
            except Exception:
                entry["value"] = str(value)
        if isinstance(item, ui.Custom):
            entry["custom"] = ("item" if item.item is not None else "view" if item.view is not None
                               else "factory" if item.factory is not None else "none")
        out.append(entry)
    return json.dumps(out)


def settings_custom(plugin_id, path, index, context):
    plugin, items = _items(plugin_id, path)
    item = items[index]
    if item.item is not None:
        return item.item
    if item.view is not None:
        return item.view
    factory = item.factory
    if factory is not None:
        factory = getattr(factory, "factory", factory)
        if hasattr(factory, "build_view"):
            return factory.build_view(context, item.factory_args)
    return None


def settings_change(plugin_id, path, index, value_json):
    plugin, items = _items(plugin_id, path)
    if plugin is None:
        return
    item = items[index]
    value = json.loads(value_json)
    plugin.set_setting(item.key, value)
    callback = getattr(item, "on_change", None)
    if callback:
        try:
            callback(value)
        except Exception:
            plugin.logger.exception("on_change failed")


def settings_click(plugin_id, path, index, view, long_click=False):
    plugin, items = _items(plugin_id, path)
    if plugin is None:
        return False
    item = items[index]
    callback = getattr(item, "on_long_click" if long_click else "on_click", None)
    if not callback:
        return False
    try:
        result = callback(view)
    except Exception:
        plugin.logger.exception("click handler failed")
        return False
    return bool(result) if long_click else True
