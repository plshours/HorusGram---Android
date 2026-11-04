# This file is part of HorusGram for Android, an unofficial Telegram client.
# Copyright (C) 2026 HorusGram contributors. Licensed under the GNU GPL v2 or
# later, see the LICENSE file in the root of this repository.
"""The rows of a plugin's settings page (create_settings)."""

from dataclasses import dataclass, field
from typing import Any, Callable, List, Optional


@dataclass
class Header:
    text: str


@dataclass
class Divider:
    text: Optional[str] = None


@dataclass
class Switch:
    key: str
    text: str
    default: bool
    subtext: Optional[str] = None
    icon: Optional[str] = None
    on_change: Optional[Callable[[bool], None]] = None
    on_long_click: Optional[Callable[[Any], Any]] = None
    link_alias: Optional[str] = None


@dataclass
class Selector:
    key: str
    text: str
    default: int
    items: List[str] = field(default_factory=list)
    icon: Optional[str] = None
    on_change: Optional[Callable[[int], None]] = None
    on_long_click: Optional[Callable[[Any], Any]] = None
    link_alias: Optional[str] = None


@dataclass
class Input:
    key: str
    text: str
    default: Optional[str] = ""
    subtext: Optional[str] = None
    icon: Optional[str] = None
    on_change: Optional[Callable[[str], None]] = None
    on_long_click: Optional[Callable[[Any], Any]] = None
    link_alias: Optional[str] = None


@dataclass
class Text:
    text: str
    subtext: Optional[str] = None
    icon: Optional[str] = None
    accent: bool = False
    red: bool = False
    on_click: Optional[Callable[[Any], None]] = None
    on_long_click: Optional[Callable[[Any], Any]] = None
    create_sub_fragment: Optional[Callable[[], List[Any]]] = None
    link_alias: Optional[str] = None


@dataclass
class EditText:
    key: str
    hint: str
    default: Optional[str] = ""
    multiline: bool = False
    max_length: Optional[int] = None
    mask: Optional[str] = None
    on_change: Optional[Callable[[str], None]] = None


@dataclass
class Custom:
    item: Any = None
    view: Any = None
    factory: Any = None
    factory_args: Any = None
    on_click: Optional[Callable[[Any], None]] = None
    on_long_click: Optional[Callable[[Any], Any]] = None
    create_sub_fragment: Optional[Callable[[], List[Any]]] = None
    link_alias: Optional[str] = None


_factories = []


class _FactoryHandle:
    def __init__(self, factory):
        self.factory = factory

    @property
    def java(self):
        return self.factory


class SimpleSettingFactory:
    """A custom row built by create_view / bind_view.

    HorusGram has no Java factory for custom rows: the page asks the
    factory for a view, binds it once and shows it as a custom row.
    """

    def __init__(self, create_view, bind_view, is_clickable=False, is_shadow=False, create_item=None,
                 on_click=None, on_long_click=None, attached_view=None, equals=None, content_equals=None):
        self.create_view = create_view
        self.bind_view = bind_view
        self.is_clickable = is_clickable
        self.is_shadow = is_shadow
        self.on_click = on_click
        self.on_long_click = on_long_click
        self.instance = _FactoryHandle(self)
        _factories.append(self)

    def build_view(self, context, args=None):
        from org.telegram.messenger import UserConfig
        view = self.create_view(context, None, UserConfig.selectedAccount, 0, None)
        try:
            self.bind_view(view, None, False, None, None)
        except Exception:
            pass
        return view

    def __call__(self, *args, **kwargs):
        return Custom(factory=self, factory_args=list(args) or None, **kwargs)

    def release(self):
        if self in _factories:
            _factories.remove(self)


def _release_factories(plugin):
    pass
