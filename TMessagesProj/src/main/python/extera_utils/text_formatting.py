# This file is part of HorusGram for Android, an unofficial Telegram client.
# Copyright (C) 2026 HorusGram contributors. Licensed under the GNU GPL v2 or
# later, see the LICENSE file in the root of this repository.
"""HTML and Markdown to plain text plus Telegram message entities."""

import re
from dataclasses import dataclass, field
from enum import Enum
from html.parser import HTMLParser
from typing import Any, Dict, List, Optional


class TLEntityType(Enum):
    CODE = "code"
    PRE = "pre"
    STRIKETHROUGH = "strikethrough"
    TEXT_LINK = "text_link"
    BOLD = "bold"
    ITALIC = "italic"
    UNDERLINE = "underline"
    SPOILER = "spoiler"
    CUSTOM_EMOJI = "custom_emoji"
    BLOCKQUOTE = "blockquote"


@dataclass
class RawEntity:
    type: TLEntityType
    offset: int
    length: int
    url: Optional[str] = None
    language: Optional[str] = None
    document_id: Optional[int] = None
    collapsed: bool = False

    def to_tlrpc_object(self):
        from org.telegram.tgnet import TLRPC
        kind = self.type
        if kind == TLEntityType.BOLD:
            entity = TLRPC.TL_messageEntityBold()
        elif kind == TLEntityType.ITALIC:
            entity = TLRPC.TL_messageEntityItalic()
        elif kind == TLEntityType.UNDERLINE:
            entity = TLRPC.TL_messageEntityUnderline()
        elif kind == TLEntityType.STRIKETHROUGH:
            entity = TLRPC.TL_messageEntityStrike()
        elif kind == TLEntityType.CODE:
            entity = TLRPC.TL_messageEntityCode()
        elif kind == TLEntityType.PRE:
            entity = TLRPC.TL_messageEntityPre()
            entity.language = self.language or ""
        elif kind == TLEntityType.TEXT_LINK:
            entity = TLRPC.TL_messageEntityTextUrl()
            entity.url = self.url or ""
        elif kind == TLEntityType.SPOILER:
            entity = TLRPC.TL_messageEntitySpoiler()
        elif kind == TLEntityType.CUSTOM_EMOJI:
            entity = TLRPC.TL_messageEntityCustomEmoji()
            entity.document_id = int(self.document_id or 0)
        else:
            entity = TLRPC.TL_messageEntityBlockquote()
            entity.collapsed = bool(self.collapsed)
        entity.offset = self.offset
        entity.length = self.length
        return entity


def _u16(text):
    return len(text.encode("utf-16-le")) // 2


_TAGS = {
    "b": TLEntityType.BOLD, "strong": TLEntityType.BOLD,
    "i": TLEntityType.ITALIC, "em": TLEntityType.ITALIC,
    "u": TLEntityType.UNDERLINE, "ins": TLEntityType.UNDERLINE,
    "s": TLEntityType.STRIKETHROUGH, "del": TLEntityType.STRIKETHROUGH, "strike": TLEntityType.STRIKETHROUGH,
    "code": TLEntityType.CODE, "pre": TLEntityType.PRE,
    "a": TLEntityType.TEXT_LINK,
    "spoiler": TLEntityType.SPOILER, "tg-spoiler": TLEntityType.SPOILER,
    "blockquote": TLEntityType.BLOCKQUOTE,
    "emoji": TLEntityType.CUSTOM_EMOJI, "tg-emoji": TLEntityType.CUSTOM_EMOJI,
}


class _HTML(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.text = []
        self.length = 0
        self.stack = []
        self.entities = []

    def handle_starttag(self, tag, attrs):
        tag = tag.lower()
        attrs = dict(attrs)
        if tag == "br":
            self.handle_data("\n")
            return
        if tag == "span" and "tg-spoiler" in (attrs.get("class") or ""):
            tag = "tg-spoiler"
        kind = _TAGS.get(tag)
        if kind is None:
            return
        if kind == TLEntityType.CODE and self.stack and self.stack[-1][0].type == TLEntityType.PRE:
            # <pre><code class="language-x"> gives the block its language.
            language = (attrs.get("class") or "").replace("language-", "")
            if language:
                self.stack[-1][0].language = language
            self.stack.append((None, tag))
            return
        entity = RawEntity(kind, self.length, 0)
        if kind == TLEntityType.TEXT_LINK:
            entity.url = attrs.get("href") or ""
        elif kind == TLEntityType.PRE:
            entity.language = attrs.get("language") or ""
        elif kind == TLEntityType.CUSTOM_EMOJI:
            value = attrs.get("id") or attrs.get("emoji-id") or attrs.get("document_id") or "0"
            entity.document_id = int(value) if str(value).isdigit() else 0
        elif kind == TLEntityType.BLOCKQUOTE:
            entity.collapsed = "expandable" in attrs or "collapsed" in attrs
        self.stack.append((entity, tag))

    def handle_endtag(self, tag):
        tag = tag.lower()
        if tag == "span":
            tag = "tg-spoiler" if any(t == "tg-spoiler" for _, t in self.stack) else tag
        for i in range(len(self.stack) - 1, -1, -1):
            entity, open_tag = self.stack[i]
            if open_tag == tag or _TAGS.get(open_tag) == _TAGS.get(tag):
                del self.stack[i]
                if entity is not None:
                    entity.length = self.length - entity.offset
                    if entity.length > 0:
                        self.entities.append(entity)
                return

    def handle_data(self, data):
        self.text.append(data)
        self.length += _u16(data)


def _parse_html(text):
    parser = _HTML()
    parser.feed(text)
    parser.close()
    return "".join(parser.text), sorted(parser.entities, key=lambda e: (e.offset, -e.length))


def _escape(text):
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def _markdown_to_html(text):
    out = []
    # Code blocks and inline code first, so nothing inside them is touched.
    pieces = re.split(r"(```[\s\S]*?```|`[^`\n]+`)", text)
    for piece in pieces:
        if piece.startswith("```") and piece.endswith("```") and len(piece) >= 6:
            body = piece[3:-3]
            language = ""
            first, _, rest = body.partition("\n")
            if rest and re.fullmatch(r"[\w+#.-]+", first.strip() or "-"):
                language, body = first.strip(), rest
            attr = f' language="{_escape(language)}"' if language else ""
            out.append(f"<pre{attr}>{_escape(body)}</pre>")
            continue
        if piece.startswith("`") and piece.endswith("`") and len(piece) >= 2:
            out.append(f"<code>{_escape(piece[1:-1])}</code>")
            continue
        out.append(_inline_markdown(piece))
    return "".join(out)


def _inline_markdown(text):
    lines = text.split("\n")
    result = []
    quote = []
    expandable = False

    def flush():
        nonlocal quote, expandable
        if quote:
            tag = "<blockquote expandable>" if expandable else "<blockquote>"
            result.append(tag + "\n".join(quote) + "</blockquote>")
            quote = []
            expandable = False

    for line in lines:
        m = re.match(r"^(\*\*)?>\s?(.*)$", line)
        if m:
            if m.group(1):
                expandable = True
            quote.append(_inline(m.group(2)))
            continue
        flush()
        result.append(_inline(line))
    flush()
    return "\n".join(result)


def _inline(text):
    text = _escape(text)
    text = re.sub(r"!\[([^\]]*)\]\(tg://emoji\?id=(\d+)\)", r'<emoji id="\2">\1</emoji>', text)
    text = re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)", r'<a href="\2">\1</a>', text)
    text = re.sub(r"\|\|(.+?)\|\|", r"<spoiler>\1</spoiler>", text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    text = re.sub(r"__(.+?)__", r"<u>\1</u>", text)
    text = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"<b>\1</b>", text)
    text = re.sub(r"(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?![\w_])", r"<i>\1</i>", text)
    text = re.sub(r"~(.+?)~", r"<s>\1</s>", text)
    return text


def parse_text(text: str, parse_mode: Optional[str] = "HTML", is_caption: bool = False) -> Dict[str, Any]:
    mode = (parse_mode or "HTML").lower()
    if mode == "markdown":
        plain, raw = _parse_html(_markdown_to_html(text))
    elif mode == "html":
        plain, raw = _parse_html(text)
    else:
        raise ValueError(f"Unsupported parse_mode {parse_mode}")
    try:
        entities = [entity.to_tlrpc_object() for entity in raw]
    except ImportError:
        entities = raw
    return {("caption" if is_caption else "message"): plain, "entities": entities}
