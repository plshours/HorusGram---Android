# This file is part of HorusGram for Android, an unofficial Telegram client.
# Copyright (C) 2026 HorusGram contributors. Licensed under the GNU GPL v2 or
# later, see the LICENSE file in the root of this repository.
"""Queues, requests, controllers and sending or editing messages."""

import traceback

from java import dynamic_proxy
from java.util import ArrayList

from android_utils import R
from base_plugin import current_hook_account
from log_utils import Logger

_log = Logger("plugins.client_utils")

STAGE_QUEUE = "stageQueue"
GLOBAL_QUEUE = "globalQueue"
CACHE_CLEAR_QUEUE = "cacheClearQueue"
SEARCH_QUEUE = "searchQueue"
PHONE_BOOK_QUEUE = "phoneBookQueue"
THEME_QUEUE = "themeQueue"
EXTERNAL_NETWORK_QUEUE = "externalNetworkQueue"
PLUGINS_QUEUE = "pluginsQueue"


# ---- Accounts ----

def get_selected_account():
    from org.telegram.messenger import UserConfig
    return UserConfig.selectedAccount


def get_hook_account():
    return current_hook_account()


_warned = set()


def _account(account, helper):
    if account is not None:
        return int(account)
    selected = get_selected_account()
    scoped = current_hook_account()
    if scoped is not None and scoped != selected and helper not in _warned:
        _warned.add(helper)
        _log.warning(
            "%s() is using account %s because it is the one selected in the UI, but it was called "
            "while handling account %s. Pass account=%s, or use get_client(%s), if you meant that "
            "account. Logged once per helper.", helper, selected, scoped, scoped, scoped)
    return selected


# ---- Queues ----

def get_queue_by_name(name):
    if name == PLUGINS_QUEUE:
        from com.horusgram.extera import ExteraPlugins
        return ExteraPlugins.queue
    from org.telegram.messenger import Utilities
    return getattr(Utilities, name, None)


def run_on_queue(fn, queue_name=PLUGINS_QUEUE, delay=0):
    queue = get_queue_by_name(queue_name)
    if queue is None:
        raise ValueError(f"Unknown queue {queue_name}")
    account = current_hook_account()
    if account is not None:
        import base_plugin
        task = lambda: base_plugin._with_scope(account, fn)  # noqa: E731
    else:
        task = fn
    queue.postRunnable(R(task), int(delay))


# ---- Controllers ----

def get_account_instance(account=None):
    from org.telegram.messenger import AccountInstance
    return AccountInstance.getInstance(_account(account, "get_account_instance"))


def _instance(helper, account):
    from org.telegram.messenger import AccountInstance
    return AccountInstance.getInstance(_account(account, helper))


def get_messages_controller(account=None):
    return _instance("get_messages_controller", account).getMessagesController()


def get_contacts_controller(account=None):
    return _instance("get_contacts_controller", account).getContactsController()


def get_media_data_controller(account=None):
    return _instance("get_media_data_controller", account).getMediaDataController()


def get_connections_manager(account=None):
    return _instance("get_connections_manager", account).getConnectionsManager()


def get_location_controller(account=None):
    return _instance("get_location_controller", account).getLocationController()


def get_notifications_controller(account=None):
    return _instance("get_notifications_controller", account).getNotificationsController()


def get_messages_storage(account=None):
    return _instance("get_messages_storage", account).getMessagesStorage()


def get_send_messages_helper(account=None):
    return _instance("get_send_messages_helper", account).getSendMessagesHelper()


def get_file_loader(account=None):
    return _instance("get_file_loader", account).getFileLoader()


def get_secret_chat_helper(account=None):
    return _instance("get_secret_chat_helper", account).getSecretChatHelper()


def get_download_controller(account=None):
    return _instance("get_download_controller", account).getDownloadController()


def get_notifications_settings(account=None):
    return _instance("get_notifications_settings", account).getNotificationsSettings()


def get_notification_center(account=None):
    return _instance("get_notification_center", account).getNotificationCenter()


def get_user_config(account=None):
    return _instance("get_user_config", account).getUserConfig()


def get_media_controller():
    from org.telegram.messenger import MediaController
    return MediaController.getInstance()


def get_last_fragment():
    from org.telegram.ui import LaunchActivity
    return LaunchActivity.getLastFragment()


# ---- Requests ----

from org.telegram.tgnet import RequestDelegate  # noqa: E402


class RequestCallback(dynamic_proxy(RequestDelegate)):
    def __init__(self, fn, account=None):
        super().__init__()
        self.fn = fn
        self.account = account

    def run(self, response, error):
        import base_plugin
        try:
            base_plugin._with_scope(self.account, self.fn, response, error)
        except Exception:
            traceback.print_exc()


def send_request(request, fn, account=None):
    account = _account(account, "send_request")
    callback = fn if isinstance(fn, RequestCallback) else RequestCallback(fn, account)
    from org.telegram.tgnet import ConnectionsManager
    return ConnectionsManager.getInstance(account).sendRequest(request, callback)


# ---- Sending ----

def _parse(text, parse_mode, is_caption=False):
    if not parse_mode or text is None:
        return text, None
    mode = str(parse_mode).lower()
    if mode not in ("html", "markdown"):
        raise ValueError(f"Unsupported parse_mode {parse_mode}")
    from extera_utils.text_formatting import parse_text
    parsed = parse_text(text, "HTML" if mode == "html" else "Markdown", is_caption)
    entities = ArrayList()
    for entity in parsed["entities"]:
        entities.add(entity)
    return parsed["caption" if is_caption else "message"], entities


def _reply(account, peer, reply):
    """A MessageObject to reply to: one, or a message id in `peer`."""
    if reply is None:
        return None
    if not isinstance(reply, int):
        return reply
    from org.telegram.messenger import MessageObject
    from org.telegram.tgnet import TLRPC
    controller = get_messages_controller(account)
    message = TLRPC.TL_message()
    message.id = int(reply)
    message.dialog_id = int(peer)
    message.peer_id = controller.getPeer(int(peer))
    return MessageObject(account, message, False, False)


def send_message(params, parse_mode=None, account=None):
    account = _account(account, "send_message")
    from org.telegram.messenger import SendMessagesHelper
    from com.horusgram.extera import ExteraPlugins
    peer = int(params.get("peer"))
    text = params.get("message")
    caption = params.get("caption")
    entities = params.get("entities")
    if parse_mode:
        if text is not None:
            text, entities = _parse(text, parse_mode)
        elif caption is not None:
            caption, entities = _parse(caption, parse_mode, True)
    send = SendMessagesHelper.SendMessageParams.of(text or "", peer)
    send.message = text
    if caption is not None:
        send.caption = caption
    if entities is not None:
        if not isinstance(entities, ArrayList):
            items = ArrayList()
            for entity in entities:
                items.add(entity)
            entities = items
        send.entities = entities
    for key, value in params.items():
        if key in ("peer", "message", "caption", "entities"):
            continue
        if key in ("replyToMsg", "replyToTopMsg"):
            value = _reply(account, peer, value)
        elif key == "params" and isinstance(value, dict):
            from java.util import HashMap
            mapped = HashMap()
            for k, v in value.items():
                mapped.put(str(k), str(v))
            value = mapped
        try:
            setattr(send, key, value)
        except Exception:
            _log.warning("send_message: unknown field %s", key)
    ExteraPlugins.sendMessage(account, send)
    return send


def send_text(peer, text, replyToMsg=None, parse_mode=None, account=None, **kwargs):
    params = {"peer": peer, "message": text}
    if replyToMsg is not None:
        params["replyToMsg"] = replyToMsg
    params.update(kwargs)
    return send_message(params, parse_mode=parse_mode, account=account)


def _media(kind, peer, path, caption=None, parse_mode=None, account=None, replyToMsg=None,
           editing=None, with_spoiler=False):
    from com.horusgram.extera import ExteraPlugins
    account = _account(account, "send_" + kind)
    text, entities = _parse(caption, parse_mode, True)
    reply = _reply(account, peer, replyToMsg)
    if kind == "photo":
        ExteraPlugins.sendPhoto(account, int(peer), str(path), text, entities, reply, editing)
    elif kind == "video":
        ExteraPlugins.sendVideo(account, int(peer), str(path), text, entities, reply, editing, bool(with_spoiler))
    else:
        ExteraPlugins.sendDocument(account, int(peer), str(path), text, reply, editing)


def send_photo(peer, path, caption=None, high_quality=False, parse_mode=None, account=None, replyToMsg=None, **kwargs):
    _media("photo", peer, path, caption, parse_mode, account, replyToMsg)


def send_document(peer, path, caption=None, parse_mode=None, account=None, replyToMsg=None, **kwargs):
    _media("document", peer, path, caption, parse_mode, account, replyToMsg)


def send_video(peer, path, caption=None, parse_mode=None, account=None, replyToMsg=None, **kwargs):
    _media("video", peer, path, caption, parse_mode, account, replyToMsg)


def send_audio(peer, path, caption=None, parse_mode=None, account=None, replyToMsg=None, **kwargs):
    _media("audio", peer, path, caption, parse_mode, account, replyToMsg)


_IMAGE = (".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic", ".bmp")
_VIDEO = (".mp4", ".mov", ".mkv", ".webm", ".avi", ".3gp")


def edit_message(message_obj, text=None, file_path=None, with_spoiler=False, parse_mode=None, account=None, **kwargs):
    from com.horusgram.extera import ExteraPlugins
    if account is None:
        account = message_obj.currentAccount
    if file_path:
        peer = message_obj.getDialogId()
        lower = str(file_path).lower()
        kind = "photo" if lower.endswith(_IMAGE) else "video" if lower.endswith(_VIDEO) else "document"
        if text is None:
            text = str(message_obj.caption) if message_obj.caption is not None else None
            parse_mode = None
        _media(kind, peer, file_path, text, parse_mode, account, None, message_obj, with_spoiler)
        return
    if text is None:
        return
    text, entities = _parse(text, parse_mode)
    ExteraPlugins.editText(int(account), message_obj, text, entities, get_last_fragment())


# ---- NotificationCenter ----

from org.telegram.messenger import NotificationCenter  # noqa: E402


class NotificationCenterDelegate(dynamic_proxy(NotificationCenter.NotificationCenterDelegate)):
    def didReceivedNotification(self, id, account, args):
        pass


# ---- One account at a time ----

class AccountClient:
    """The helpers of this module, bound to one account."""

    def __init__(self, account):
        self.account = int(account)

    def __getattr__(self, name):
        target = globals().get(name)
        if not callable(target):
            raise AttributeError(name)

        def bound(*args, **kwargs):
            kwargs.setdefault("account", self.account)
            return target(*args, **kwargs)
        return bound

    def get_media_controller(self):
        return get_media_controller()

    def get_last_fragment(self):
        return get_last_fragment()


def get_client(account=None):
    if account is None:
        account = current_hook_account()
    if account is None:
        account = get_selected_account()
    return AccountClient(account)
