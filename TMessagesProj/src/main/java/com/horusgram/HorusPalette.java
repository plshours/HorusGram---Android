/*
 * This file is part of HorusGram for Android, an unofficial Telegram client.
 * Copyright (C) 2026 HorusGram contributors.
 *
 * HorusGram is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 2 of the License, or (at your option)
 * any later version. See the LICENSE file in the root of this repository.
 */
package com.horusgram;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.SparseIntArray;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeColors;

import java.util.HashMap;
import java.util.Map;

// The HorusGram theme and what the user changed in it. It works like the
// Customization section of HorusGram Desktop
// (Telegram/SourceFiles/hoursgram/customization/customization_palette.cpp):
// five base colors, each standing for every place the theme uses it, whose
// shades move along with them, and on top of that any single key of the
// palette with a color of its own.
public final class HorusPalette {

    public static final String THEME_NAME = "HorusGram";
    public static final String ASSET_NAME = "horusgram.attheme";
    public static final String MAC_DARK_NAME = "HorusGram macOS Dark";
    public static final String MAC_DARK_ASSET = "horusgram_mac_dark.attheme";
    public static final String MAC_LIGHT_NAME = "HorusGram macOS Light";
    public static final String MAC_LIGHT_ASSET = "horusgram_mac_light.attheme";

    private static final String PREFS = "horusgram_palette";
    private static final String SLOT_PREFIX = "slot/";
    private static final String KEY_PREFIX = "key/";

    // One editable base color. `base` is RGB without alpha, as the theme
    // uses it; `followers` are the shades the theme derives from it (pressed,
    // selected, the outgoing bubble), moved by the same amount so that no part
    // of the old color stays behind.
    public static final class Slot {
        public final String id;
        public final int base;
        public final int[] followers;
        public final int titleRes;

        Slot(String id, int base, int[] followers, int titleRes) {
            this.id = id;
            this.base = base;
            this.followers = followers;
            this.titleRes = titleRes;
        }
    }

    public static final Slot[] SLOTS = {
            new Slot("windowBg", 0x343641, new int[]{0x3D404C, 0x464855, 0x46485B, 0x4F5268, 0x2C2E38}, R.string.HorusColorWindowBg),
            new Slot("surface", 0x282A34, new int[0], R.string.HorusColorSurface),
            new Slot("accent", 0x8993D8, new int[]{0x717CC1, 0x7B86CF, 0x6E74A0, 0x565B7B, 0x505991, 0x5A6090}, R.string.HorusColorAccent),
            new Slot("accentLight", 0xAAB2ED, new int[0], R.string.HorusColorAccentLight),
            new Slot("subtitle", 0xB1B2B7, new int[]{0x626875}, R.string.HorusColorSubtitle),
    };

    // A palette key that can be given its own color. `key` is the name the
    // .attheme file uses.
    public static final class Key {
        public final String key;
        public final int titleRes;

        Key(String key, int titleRes) {
            this.key = key;
            this.titleRes = titleRes;
        }
    }

    public static final class Group {
        public final int titleRes;
        public final Key[] keys;

        Group(int titleRes, Key... keys) {
            this.titleRes = titleRes;
            this.keys = keys;
        }
    }

    public static final Group[] GROUPS = {
            new Group(R.string.HorusGroupIn,
                    new Key("chat_inBubble", R.string.HorusKeyBubble),
                    new Key("chat_inBubbleSelected", R.string.HorusKeyBubbleSelected),
                    new Key("chat_inBubbleShadow", R.string.HorusKeyShadow),
                    new Key("chat_messageTextIn", R.string.HorusKeyText),
                    new Key("chat_inTimeText", R.string.HorusKeyTime),
                    new Key("chat_messageLinkIn", R.string.HorusKeyLinks),
                    new Key("chat_inReplyNameText", R.string.HorusKeyNames),
                    new Key("chat_inReplyLine", R.string.HorusKeyReplyBar),
                    new Key("chat_inFileBackground", R.string.HorusKeyFile),
                    new Key("chat_inVoiceSeekbarFill", R.string.HorusKeyWaveform)),
            new Group(R.string.HorusGroupOut,
                    new Key("chat_outBubble", R.string.HorusKeyBubble),
                    new Key("chat_outBubbleSelected", R.string.HorusKeyBubbleSelected),
                    new Key("chat_outBubbleShadow", R.string.HorusKeyShadow),
                    new Key("chat_messageTextOut", R.string.HorusKeyText),
                    new Key("chat_outTimeText", R.string.HorusKeyTime),
                    new Key("chat_messageLinkOut", R.string.HorusKeyLinks),
                    new Key("chat_outReplyNameText", R.string.HorusKeyNames),
                    new Key("chat_outReplyLine", R.string.HorusKeyReplyBar),
                    new Key("chat_outFileBackground", R.string.HorusKeyFile),
                    new Key("chat_outVoiceSeekbarFill", R.string.HorusKeyWaveform),
                    new Key("chat_outSentCheck", R.string.HorusKeyChecks),
                    new Key("chat_outSentCheckRead", R.string.HorusKeyChecksRead)),
            new Group(R.string.HorusGroupChat,
                    new Key("chat_wallpaper", R.string.HorusKeyChatBackground),
                    new Key("chat_serviceBackground", R.string.HorusKeyServiceBg),
                    new Key("chat_serviceText", R.string.HorusKeyServiceText),
                    new Key("chat_selectedBackground", R.string.HorusKeySelection),
                    new Key("actionBarDefault", R.string.HorusKeyTopBar),
                    new Key("chat_topPanelBackground", R.string.HorusKeyPinned),
                    new Key("chat_goDownButton", R.string.HorusKeyToDown),
                    new Key("chat_unreadMessagesStartBackground", R.string.HorusKeyUnreadBar)),
            new Group(R.string.HorusGroupInput,
                    new Key("chat_messagePanelBackground", R.string.HorusKeyInputBg),
                    new Key("chat_messagePanelText", R.string.HorusKeyInputText),
                    new Key("chat_messagePanelHint", R.string.HorusKeyInputHint),
                    new Key("chat_messagePanelIcons", R.string.HorusKeyInputIcons),
                    new Key("chat_messagePanelSend", R.string.HorusKeySend),
                    new Key("chat_emojiPanelBackground", R.string.HorusKeyEmojiPanel)),
            new Group(R.string.HorusGroupList,
                    new Key("windowBackgroundWhite", R.string.HorusKeyListBg),
                    new Key("chats_name", R.string.HorusKeyListName),
                    new Key("chats_message", R.string.HorusKeyListText),
                    new Key("chats_date", R.string.HorusKeyTime),
                    new Key("chats_unreadCounter", R.string.HorusKeyUnread),
                    new Key("chats_unreadCounterMuted", R.string.HorusKeyUnreadMuted),
                    new Key("chats_unreadCounterText", R.string.HorusKeyUnreadText),
                    new Key("chats_actionBackground", R.string.HorusKeyFloatingButton),
                    new Key("chats_menuBackground", R.string.HorusKeyMainMenu),
                    new Key("actionBarTabLine", R.string.HorusKeyFoldersActive)),
            new Group(R.string.HorusGroupProfile,
                    new Key("avatar_backgroundActionBarBlue", R.string.HorusKeyProfileHeader),
                    new Key("profile_title", R.string.HorusKeyProfileName),
                    new Key("profile_status", R.string.HorusKeyProfileStatus),
                    new Key("profile_verifiedBackground", R.string.HorusKeyVerified),
                    new Key("profile_actionBackground", R.string.HorusKeyProfileButton),
                    new Key("windowBackgroundWhiteGrayIcon", R.string.HorusKeyProfileIcons)),
            new Group(R.string.HorusGroupControls,
                    new Key("featuredStickers_addButton", R.string.HorusKeyButton),
                    new Key("featuredStickers_buttonText", R.string.HorusKeyButtonText),
                    new Key("windowBackgroundWhiteBlueText4", R.string.HorusKeyLinksSettings),
                    new Key("switchTrackChecked", R.string.HorusKeySwitch),
                    new Key("checkboxSquareBackground", R.string.HorusKeyCheckbox),
                    new Key("radioBackgroundChecked", R.string.HorusKeyRadio),
                    new Key("windowBackgroundGray", R.string.HorusKeySections),
                    new Key("dialogBackground", R.string.HorusKeyDialogs)),
            new Group(R.string.HorusGroupAvatars,
                    new Key("avatar_backgroundRed", R.string.HorusKeyAvatarRed),
                    new Key("avatar_backgroundOrange", R.string.HorusKeyAvatarOrange),
                    new Key("avatar_backgroundViolet", R.string.HorusKeyAvatarViolet),
                    new Key("avatar_backgroundGreen", R.string.HorusKeyAvatarGreen),
                    new Key("avatar_backgroundCyan", R.string.HorusKeyAvatarCyan),
                    new Key("avatar_backgroundBlue", R.string.HorusKeyAvatarBlue),
                    new Key("avatar_backgroundPink", R.string.HorusKeyAvatarPink)),
    };

    private HorusPalette() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
    }

    public static boolean isHorusTheme(Theme.ThemeInfo info) {
        return info != null && ASSET_NAME.equals(info.assetName);
    }

    // The color a slot has now, opaque.
    public static int getSlotColor(Slot slot) {
        return 0xFF000000 | prefs().getInt(SLOT_PREFIX + slot.id, slot.base);
    }

    public static boolean isSlotChanged(Slot slot) {
        return prefs().contains(SLOT_PREFIX + slot.id);
    }

    public static void setSlotColor(Slot slot, int color) {
        prefs().edit().putInt(SLOT_PREFIX + slot.id, color & 0xFFFFFF).apply();
    }

    public static boolean hasKeyColor(String key) {
        return prefs().contains(KEY_PREFIX + key);
    }

    public static void setKeyColor(String key, int color) {
        prefs().edit().putInt(KEY_PREFIX + key, color).apply();
    }

    public static void resetKeyColor(String key) {
        prefs().edit().remove(KEY_PREFIX + key).apply();
    }

    public static void resetSlot(Slot slot) {
        prefs().edit().remove(SLOT_PREFIX + slot.id).apply();
    }

    public static void resetAll() {
        prefs().edit().clear().apply();
    }

    // A shade keeps its distance from the color it was derived from: if the
    // background goes from #343641 to #0C0C0C, its pressed shade #3D404C
    // goes to #151618 and does not stay where it was.
    private static int shifted(int follower, int base, int picked) {
        int r = clamp(Color.red(follower) + Color.red(picked) - Color.red(base));
        int g = clamp(Color.green(follower) + Color.green(picked) - Color.green(base));
        int b = clamp(Color.blue(follower) + Color.blue(picked) - Color.blue(base));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    // Lays the user's picks over the colors read from the HorusGram theme
    // file. Called by Theme.getThemeFileValues for that file only, so the
    // other themes are never touched.
    public static void apply(SparseIntArray values) {
        SharedPreferences prefs = prefs();
        Map<String, ?> all = prefs.getAll();
        if (all.isEmpty()) {
            return;
        }
        HashMap<Integer, Integer> replacements = new HashMap<>();
        HashMap<Integer, Integer> shades = new HashMap<>();
        for (Slot slot : SLOTS) {
            Object saved = all.get(SLOT_PREFIX + slot.id);
            if (!(saved instanceof Integer)) {
                continue;
            }
            int picked = ((Integer) saved) & 0xFFFFFF;
            if (picked == slot.base) {
                continue;
            }
            replacements.put(slot.base, picked);
            for (int follower : slot.followers) {
                shades.put(follower, shifted(follower, slot.base, picked));
            }
        }
        // A color that is a slot of its own is never moved as someone's shade.
        for (Map.Entry<Integer, Integer> shade : shades.entrySet()) {
            if (!replacements.containsKey(shade.getKey())) {
                replacements.put(shade.getKey(), shade.getValue());
            }
        }
        if (!replacements.isEmpty()) {
            for (int i = 0; i < values.size(); i++) {
                if (values.keyAt(i) == Theme.key_wallpaperFileOffset) {
                    continue;
                }
                int value = values.valueAt(i);
                Integer replacement = replacements.get(value & 0xFFFFFF);
                if (replacement != null) {
                    // Only the color is swapped; the alpha stays as it was.
                    values.setValueAt(i, (value & 0xFF000000) | replacement);
                }
            }
        }
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (!entry.getKey().startsWith(KEY_PREFIX) || !(entry.getValue() instanceof Integer)) {
                continue;
            }
            int key = ThemeColors.stringKeyToInt(entry.getKey().substring(KEY_PREFIX.length()));
            if (key >= 0) {
                values.put(key, (Integer) entry.getValue());
            }
        }
    }

    // The color a key has right now in the running theme.
    public static int getCurrentKeyColor(String key) {
        int id = ThemeColors.stringKeyToInt(key);
        return id >= 0 ? Theme.getColor(id) : 0;
    }

    // Reloads the HorusGram theme, picks included, if it is the one shown.
    public static void reapply() {
        Theme.ThemeInfo active = Theme.getActiveTheme();
        if (!isHorusTheme(active)) {
            return;
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, active, Theme.isCurrentThemeNight(), null, -1);
    }
}
