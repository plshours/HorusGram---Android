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

import org.telegram.messenger.ApplicationLoader;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;

// Ghost mode, as in AyuGram: what the client tells the servers about the
// user. Every request goes through ConnectionsManager, which asks `filter`
// first, so a read receipt or a typing status never leaves the device no
// matter which screen made it.
public final class HorusGhost {

    private static final String PREFS = "horusgram_ghost";

    // Each is true when the client behaves as Telegram does.
    public static volatile boolean sendReadMessages;
    public static volatile boolean sendReadStories;
    public static volatile boolean sendOnline;
    public static volatile boolean sendTyping;

    // Messages deleted by others stay in the chat with a mark.
    public static volatile boolean saveDeleted;

    static {
        SharedPreferences prefs = prefs();
        sendReadMessages = prefs.getBoolean("sendReadMessages", true);
        sendReadStories = prefs.getBoolean("sendReadStories", true);
        sendOnline = prefs.getBoolean("sendOnline", true);
        sendTyping = prefs.getBoolean("sendTyping", true);
        saveDeleted = prefs.getBoolean("saveDeleted", true);
    }

    private HorusGhost() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
    }

    public static boolean isGhost() {
        return !sendReadMessages && !sendReadStories && !sendOnline && !sendTyping;
    }

    // Turns every part of ghost mode on or off at once.
    public static void setGhost(boolean ghost) {
        sendReadMessages = !ghost;
        sendReadStories = !ghost;
        sendOnline = !ghost;
        sendTyping = !ghost;
        save();
    }

    public static void save() {
        prefs().edit()
                .putBoolean("sendReadMessages", sendReadMessages)
                .putBoolean("sendReadStories", sendReadStories)
                .putBoolean("sendOnline", sendOnline)
                .putBoolean("sendTyping", sendTyping)
                .putBoolean("saveDeleted", saveDeleted)
                .apply();
    }

    // True when the request must not be sent. An online status is not
    // dropped but turned into an offline one, so the user does not stay
    // online on the servers from an earlier session.
    public static boolean filter(TLObject request) {
        if (request == null) {
            return false;
        }
        if (!sendReadMessages && (request instanceof TLRPC.TL_messages_readHistory
                || request instanceof TLRPC.TL_channels_readHistory
                || request instanceof TLRPC.TL_messages_readDiscussion
                || request instanceof TLRPC.TL_messages_readMessageContents
                || request instanceof TLRPC.TL_channels_readMessageContents
                || request instanceof TLRPC.TL_messages_readEncryptedHistory)) {
            return true;
        }
        if (!sendReadStories && (request instanceof TL_stories.TL_stories_readStories
                || request instanceof TL_stories.TL_stories_incrementStoryViews)) {
            return true;
        }
        if (!sendTyping && (request instanceof TLRPC.TL_messages_setTyping
                || request instanceof TLRPC.TL_messages_setEncryptedTyping)) {
            return true;
        }
        if (!sendOnline && request instanceof TL_account.updateStatus) {
            ((TL_account.updateStatus) request).offline = true;
        }
        return false;
    }
}
