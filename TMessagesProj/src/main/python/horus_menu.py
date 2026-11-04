# This file is part of HorusGram for Android, an unofficial Telegram client.
# Copyright (C) 2026 HorusGram contributors. Licensed under the GNU GPL v2 or
# later, see the LICENSE file in the root of this repository.
"""Menu items the plugins add with BasePlugin.add_menu_item."""

import traceback


def changed():
    pass


def items(menu_type):
    """The plugin menu items of one menu, highest priority first, as (plugin, id, data)."""
    import horus_extera
    result = []
    for plugin in horus_extera.loaded_plugins():
        for item_id, data in list(getattr(plugin, "_horus_menu_items", {}).items()):
            kind = getattr(data.menu_type, "value", data.menu_type)
            if kind == menu_type:
                result.append((plugin, item_id, data))
    result.sort(key=lambda entry: -int(entry[2].priority or 0))
    return result


def items_json(menu_type):
    import json
    return json.dumps([
        {"plugin": plugin.id, "id": item_id, "text": data.text, "icon": data.icon, "subtext": data.subtext}
        for plugin, item_id, data in items(menu_type)
    ])


def click(plugin_id, item_id, context):
    import horus_extera
    plugin = horus_extera.get_plugin(plugin_id)
    if plugin is None:
        return
    data = plugin._horus_menu_items.get(item_id)
    if data is None:
        return
    values = {}
    if context is not None:
        for entry in context.entrySet().toArray():
            values[str(entry.getKey())] = entry.getValue()
    try:
        data.on_click(values)
    except Exception:
        plugin.logger.exception("menu item %s failed", item_id)
