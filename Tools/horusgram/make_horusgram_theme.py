#!/usr/bin/env python3
# HorusGram for Android - builds the HorusGram theme from Telegram's Night theme.
#
# Usage: make_horusgram_theme.py <night.attheme> <horusgram.attheme>
#
# Every key of the Night theme is kept, so nothing is left to Telegram's light
# defaults. Its colors are carried over to the HorusGram palette of the desktop
# client (Telegram/Resources/hoursgram.tdesktop-palette in HoursGram-Desktop):
# grays become the blue-gray surfaces, blues become the accent, and the
# colors that mean something (red errors, green online) stay as they are.
# The keys a person sees the most are then set by hand below.

import colorsys
import sys

BG = 0x343641        # windowBg: lists, settings, the chat list
SURFACE = 0x282A34   # bars, menus, dialogs, incoming bubbles, input bar
OVER = 0x3D404C      # pressed and selected states
RIPPLE = 0x464855
OUT = 0x46485B       # outgoing bubbles
OUT_SELECTED = 0x4F5268
SECTION = 0x2C2E38   # the gaps between settings sections, chat background
ACCENT = 0x8993D8
ACCENT_LIGHT = 0xAAB2ED
ACCENT_DARK = 0x717CC1
BUTTON = 0x5A6090
SUBTITLE = 0xB1B2B7
DISABLED = 0x626875
WHITE = 0xFFFFFF

ACCENT_HUE = colorsys.rgb_to_hls(*(c / 255 for c in (0x89, 0x93, 0xD8)))[0]
NEUTRAL_HUE = colorsys.rgb_to_hls(*(c / 255 for c in (0x34, 0x36, 0x41)))[0]

OVERRIDES = {
    # Surfaces.
    'windowBackgroundWhite': BG,
    'windowBackgroundGray': SECTION,
    'windowBackgroundGrayShadow': 0x000000,
    'actionBarDefault': SURFACE,
    'actionBarDefaultSubmenuBackground': SURFACE,
    'actionBarDefaultSubmenuItem': WHITE,
    'actionBarDefaultSubmenuItemIcon': SUBTITLE,
    'actionBarDefaultSelector': OVER,
    'actionBarWhiteSelector': OVER,
    'actionBarActionModeDefault': SURFACE,
    'actionBarDefaultSearch': WHITE,
    'actionBarDefaultSearchPlaceholder': 0x88B1B2B7,
    'actionBarTabActiveText': ACCENT,
    'actionBarTabLine': ACCENT,
    'actionBarTabUnactiveText': SUBTITLE,
    'actionBarTabSelector': OVER,
    'avatar_backgroundActionBarBlue': SURFACE,
    'avatar_backgroundInProfileBlue': ACCENT,
    'dialogBackground': SURFACE,
    'dialogBackgroundGray': BG,
    'dialogButton': ACCENT,
    'dialogTextBlue': ACCENT,
    'dialogTextBlue2': ACCENT,
    'dialogTextBlue4': ACCENT,
    'dialogLinkSelection': 0x338993D8,
    'dialogInputFieldActivated': ACCENT,
    'dialogCheckboxSquareBackground': ACCENT,
    'dialogRadioBackgroundChecked': ACCENT,
    'dialogRoundCheckBox': ACCENT,
    'dialogProgressCircle': ACCENT,
    'dialogLineProgress': ACCENT,
    'dialogFloatingButton': ACCENT,
    'dialogSearchBackground': BG,
    'dialogScrollGlow': SURFACE,
    'divider': 0x3D404C,

    # Chat list and side menu.
    'chats_menuBackground': BG,
    'chats_menuTopBackgroundCats': SURFACE,
    'chats_menuItemText': WHITE,
    'chats_menuItemIcon': SUBTITLE,
    'chats_menuName': WHITE,
    'chats_menuPhone': SUBTITLE,
    'chats_actionBackground': ACCENT,
    'chats_actionPressedBackground': ACCENT_DARK,
    'chats_actionIcon': WHITE,
    'chats_unreadCounter': ACCENT,
    'chats_unreadCounterMuted': OVER,
    'chats_unreadCounterText': WHITE,
    'chats_name': WHITE,
    'chats_secretName': ACCENT_LIGHT,
    'chats_message': SUBTITLE,
    'chats_message_threeLines': SUBTITLE,
    'chats_nameMessage': ACCENT_LIGHT,
    'chats_nameMessage_threeLines': ACCENT_LIGHT,
    'chats_actionMessage': ACCENT,
    'chats_attachMessage': ACCENT,
    'chats_draft': ACCENT,
    'chats_date': SUBTITLE,
    'chats_pinnedOverlay': 0x0AFFFFFF,
    'chats_tabletSelectedOverlay': 0x0FFFFFFF,
    'chats_sentCheck': ACCENT_LIGHT,
    'chats_sentReadCheck': ACCENT_LIGHT,
    'chats_sentClock': SUBTITLE,
    'chats_verifiedBackground': ACCENT,
    'chats_verifiedCheck': WHITE,
    'chats_onlineCircle': ACCENT,
    'chats_archiveBackground': DISABLED,
    'chats_archivePinBackground': OVER,
    'chats_tabUnreadActiveBackground': ACCENT,
    'chats_tabUnreadUnactiveBackground': OVER,

    # Messages.
    'chat_wallpaper': SECTION,
    'chat_inBubble': SURFACE,
    'chat_inBubbleSelected': OVER,
    'chat_inBubbleShadow': 0x33000000,
    'chat_outBubble': OUT,
    'chat_outBubbleSelected': OUT_SELECTED,
    'chat_outBubbleShadow': 0x33000000,
    'chat_outBubbleGradientSelectedOverlay': 0x14FFFFFF,
    'chat_messageTextIn': WHITE,
    'chat_messageTextOut': WHITE,
    'chat_messageLinkIn': ACCENT_LIGHT,
    'chat_messageLinkOut': ACCENT_LIGHT,
    'chat_inTimeText': SUBTITLE,
    'chat_outTimeText': SUBTITLE,
    'chat_inTimeSelectedText': SUBTITLE,
    'chat_outTimeSelectedText': WHITE,
    'chat_outSentCheck': SUBTITLE,
    'chat_outSentCheckSelected': WHITE,
    'chat_outSentCheckRead': ACCENT_LIGHT,
    'chat_outSentCheckReadSelected': WHITE,
    'chat_outSentClock': SUBTITLE,
    'chat_inReplyLine': ACCENT,
    'chat_outReplyLine': ACCENT_LIGHT,
    'chat_inReplyNameText': ACCENT_LIGHT,
    'chat_outReplyNameText': ACCENT_LIGHT,
    'chat_inPreviewLine': ACCENT,
    'chat_outPreviewLine': ACCENT_LIGHT,
    'chat_inForwardedNameText': ACCENT_LIGHT,
    'chat_outForwardedNameText': ACCENT_LIGHT,
    'chat_inViaBotNameText': ACCENT_LIGHT,
    'chat_outViaBotNameText': ACCENT_LIGHT,
    'chat_inAudioSeekbarFill': ACCENT,
    'chat_outAudioSeekbarFill': ACCENT_LIGHT,
    'chat_inVoiceSeekbarFill': ACCENT,
    'chat_outVoiceSeekbarFill': ACCENT_LIGHT,
    'chat_inVoiceSeekbar': OVER,
    'chat_outVoiceSeekbar': ACCENT_DARK,
    'chat_inLoader': ACCENT,
    'chat_outLoader': ACCENT_LIGHT,
    'chat_inFileBackground': OVER,
    'chat_outFileBackground': OUT_SELECTED,
    'chat_serviceBackground': 0xB3282A34,
    'chat_serviceBackgroundSelected': 0xCC464855,
    'chat_serviceText': WHITE,
    'chat_serviceLink': ACCENT_LIGHT,
    'chat_unreadMessagesStartBackground': SURFACE,
    'chat_unreadMessagesStartText': WHITE,
    'chat_unreadMessagesStartArrowIcon': ACCENT,
    'chat_selectedBackground': 0x338993D8,
    'chat_goDownButton': SURFACE,
    'chat_goDownButtonIcon': SUBTITLE,
    'chat_goDownButtonCounterBackground': ACCENT,
    'chat_topPanelBackground': SURFACE,
    'chat_topPanelLine': ACCENT,
    'chat_topPanelTitle': ACCENT_LIGHT,
    'chat_topPanelMessage': SUBTITLE,

    # Input bar.
    'chat_messagePanelBackground': SURFACE,
    'chat_messagePanelText': WHITE,
    'chat_messagePanelHint': 0x88B1B2B7,
    'chat_messagePanelIcons': SUBTITLE,
    'chat_messagePanelSend': ACCENT,
    'chat_messagePanelVoicePressed': WHITE,
    'chat_messagePanelVoiceBackground': ACCENT,
    'chat_messagePanelCursor': ACCENT,
    'chat_recordedVoiceBackground': ACCENT,
    'chat_recordedVoicePlayPause': WHITE,
    'chat_recordVoiceCancel': ACCENT,
    'chat_emojiPanelBackground': SURFACE,
    'chat_emojiSearchBackground': BG,
    'chat_emojiPanelIconSelected': ACCENT,
    'chat_emojiPanelStickerSetNameHighlight': ACCENT,
    'chat_attachCheckBoxBackground': ACCENT,
    'chat_searchPanelText': ACCENT,
    'chat_searchPanelIcons': ACCENT,
    'chat_replyPanelIcons': ACCENT,
    'chat_replyPanelClose': SUBTITLE,
    'chat_replyPanelName': ACCENT_LIGHT,
    'chat_replyPanelLine': OVER,

    # Controls.
    'windowBackgroundWhiteBlackText': WHITE,
    'windowBackgroundWhiteGrayText': SUBTITLE,
    'windowBackgroundWhiteGrayText2': SUBTITLE,
    'windowBackgroundWhiteGrayText3': SUBTITLE,
    'windowBackgroundWhiteGrayText4': SUBTITLE,
    'windowBackgroundWhiteGrayIcon': SUBTITLE,
    'windowBackgroundWhiteBlueHeader': ACCENT,
    'windowBackgroundWhiteBlueText': ACCENT,
    'windowBackgroundWhiteBlueText2': ACCENT,
    'windowBackgroundWhiteBlueText3': ACCENT,
    'windowBackgroundWhiteBlueText4': ACCENT,
    'windowBackgroundWhiteBlueText5': ACCENT,
    'windowBackgroundWhiteBlueText6': ACCENT,
    'windowBackgroundWhiteBlueText7': ACCENT,
    'windowBackgroundWhiteBlueButton': ACCENT,
    'windowBackgroundWhiteBlueIcon': ACCENT,
    'windowBackgroundWhiteLinkText': ACCENT_LIGHT,
    'windowBackgroundWhiteLinkSelection': 0x338993D8,
    'windowBackgroundWhiteValueText': ACCENT,
    'windowBackgroundWhiteInputField': OVER,
    'windowBackgroundWhiteInputFieldActivated': ACCENT,
    'windowBackgroundChecked': ACCENT,
    'windowBackgroundCheckText': WHITE,
    'switchTrack': DISABLED,
    'switchTrackChecked': ACCENT,
    'switchTrackBlue': DISABLED,
    'switchTrackBlueChecked': ACCENT,
    'switchTrackBlueThumb': SURFACE,
    'switchTrackBlueThumbChecked': SURFACE,
    'switchTrackBlueSelector': 0x17FFFFFF,
    'switchTrackBlueSelectorChecked': 0x218993D8,
    'switch2Track': DISABLED,
    'switch2TrackChecked': ACCENT,
    'checkbox': ACCENT,
    'checkboxCheck': WHITE,
    'checkboxSquareBackground': ACCENT,
    'checkboxSquareCheck': SURFACE,
    'checkboxSquareUnchecked': SUBTITLE,
    'radioBackground': SUBTITLE,
    'radioBackgroundChecked': ACCENT,
    'progressCircle': ACCENT,
    'player_progress': ACCENT,
    'player_progressBackground': OVER,
    'player_button': WHITE,
    'player_buttonActive': ACCENT,
    'player_background': SURFACE,
    'inappPlayerBackground': SURFACE,
    'inappPlayerPlayPause': ACCENT,
    'inappPlayerTitle': WHITE,
    'inappPlayerPerformer': SUBTITLE,
    'inappPlayerClose': SUBTITLE,
    'featuredStickers_addButton': ACCENT,
    'featuredStickers_addButtonPressed': ACCENT_DARK,
    'featuredStickers_addedIcon': ACCENT,
    'featuredStickers_buttonText': WHITE,
    'featuredStickers_unread': ACCENT,
    'fastScrollActive': ACCENT,
    'fastScrollInactive': OVER,
    'listSelectorSDK21': 0x0FFFFFFF,
    'profile_actionBackground': ACCENT,
    'profile_actionPressedBackground': ACCENT_DARK,
    'profile_actionIcon': WHITE,
    'profile_verifiedBackground': ACCENT,
    'profile_verifiedCheck': WHITE,
    'profile_status': SUBTITLE,
    'profile_tabSelectedLine': ACCENT,
    'profile_tabSelectedText': ACCENT,
    'profile_tabText': SUBTITLE,
    'profile_tabSelector': OVER,
    'undo_background': 0xF0282A34,
    'undo_cancelColor': ACCENT_LIGHT,
    'undo_infoColor': WHITE,
    'location_sendLocationBackground': ACCENT,
    'location_sendLiveLocationBackground': 0xFFE05454,
    'contextProgressInner1': OVER,
    'contextProgressOuter1': ACCENT,
    'avatar_backgroundSaved': ACCENT,
    'avatar_background2Saved': ACCENT_DARK,

    # Avatars, from the desktop's historyPeerNUserpicBg pairs.
    'avatar_backgroundRed': 0xB18577,
    'avatar_background2Red': 0x975953,
    'avatar_backgroundOrange': 0xB19977,
    'avatar_background2Orange': 0x976E53,
    'avatar_backgroundViolet': 0x8A77B1,
    'avatar_background2Violet': 0x595397,
    'avatar_backgroundGreen': 0x94B177,
    'avatar_background2Green': 0x559753,
    'avatar_backgroundCyan': 0x77A5B1,
    'avatar_background2Cyan': 0x538997,
    'avatar_backgroundBlue': 0x777EB1,
    'avatar_background2Blue': 0x535D97,
    'avatar_backgroundPink': 0xB17788,
    'avatar_background2Pink': 0x975363,
}

# Keys that are dropped: an outgoing gradient would cover the flat bubble.
DROPPED = {
    'chat_outBubbleGradient',
    'chat_outBubbleGradient2',
    'chat_outBubbleGradient3',
}


def to_argb(value):
    """An int of the .attheme file, signed, as an unsigned ARGB."""
    return value & 0xFFFFFFFF


def from_argb(argb):
    """Back to the signed int the .attheme parser reads."""
    return argb - (1 << 32) if argb & 0x80000000 else argb


def with_alpha(rgb):
    """Six-digit colors in the table above are opaque."""
    return rgb if rgb > 0xFFFFFF else (0xFF000000 | rgb)


def carry(argb):
    """Moves one Night color into the HorusGram palette, keeping its alpha."""
    alpha = argb >> 24
    r, g, b = ((argb >> 16) & 0xFF) / 255, ((argb >> 8) & 0xFF) / 255, (argb & 0xFF) / 255
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    hue_deg = h * 360
    if s > 0.3 and 185 <= hue_deg <= 240 and 0.2 < l < 0.9:
        # Telegram's blue: becomes the accent, as light as it was.
        r, g, b = colorsys.hls_to_rgb(ACCENT_HUE, min(max(l, 0.45), 0.82), 0.52)
    elif s < 0.12 and l < 0.5:
        # The near-black grays of Night: the blue-gray surfaces, where
        # #000 is the section background and #181819 the window.
        l = 0.165 + l * 0.75
        r, g, b = colorsys.hls_to_rgb(NEUTRAL_HUE, l, 0.11)
    elif s < 0.12 and l < 0.95:
        # Light grays are secondary text: tinted like #B1B2B7.
        r, g, b = colorsys.hls_to_rgb(NEUTRAL_HUE, l, 0.04)
    rgb = (round(r * 255) << 16) | (round(g * 255) << 8) | round(b * 255)
    return (alpha << 24) | rgb


def main(source, target):
    out = []
    seen = set()
    with open(source, encoding='utf-8') as file:
        for line in file:
            line = line.rstrip('\r\n')
            if '=' not in line:
                continue
            key, value = line.split('=', 1)
            if key in DROPPED:
                continue
            seen.add(key)
            if key in OVERRIDES:
                argb = with_alpha(OVERRIDES[key])
            else:
                try:
                    argb = carry(to_argb(int(value)))
                except ValueError:
                    out.append(line)
                    continue
            out.append('%s=%d' % (key, from_argb(argb)))
    for key, rgb in OVERRIDES.items():
        if key not in seen:
            out.append('%s=%d' % (key, from_argb(with_alpha(rgb))))
    with open(target, 'w', encoding='utf-8', newline='\n') as file:
        file.write('\n'.join(out) + '\n')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
