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

// HorusGram settings that are not colors.
public final class HorusConfig {

    private static final String PREFS = "horusgram_config";

    // Tails of the message bubbles, for incoming and outgoing messages
    // separately. Read on every bubble that is drawn, so they are kept here
    // and not looked up in the preferences each time.
    public static boolean inTail = true;
    public static boolean outTail = true;

    // Bumped on every tail change, so the bubbles drop the backgrounds they
    // drew with the old shape.
    public static int tailVersion;

    static {
        SharedPreferences prefs = prefs();
        inTail = prefs.getBoolean("inTail", true);
        outTail = prefs.getBoolean("outTail", true);
    }

    private HorusConfig() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
    }

    public static boolean hasTail(boolean out) {
        return out ? outTail : inTail;
    }

    public static void setTail(boolean out, boolean value) {
        if (out) {
            outTail = value;
        } else {
            inTail = value;
        }
        tailVersion++;
        prefs().edit().putBoolean(out ? "outTail" : "inTail", value).apply();
    }
}
