/*
 * This file is part of HorusGram for Android, an unofficial Telegram client.
 * Copyright (C) 2026 HorusGram contributors.
 *
 * HorusGram is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 2 of the License, or (at your option)
 * any later version. See the LICENSE file in the root of this repository.
 */
package com.horusgram.music;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Lyrics for the track that plays, as in HorusGram Desktop
// (Telegram/SourceFiles/hoursgram/music/): the cache on the device first,
// then lrclib.net, a public database of synchronized lyrics that needs no key
// and asks callers to name themselves in the user agent.
public final class HorusLyrics {

    public static final class Line {
        // Milliseconds from the start, or -1 for plain lyrics without timing.
        public final long time;
        public final String text;

        Line(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    public interface Callback {
        void run(ArrayList<Line> lines);
    }

    private static final String API = "https://lrclib.net/api/";
    private static final String USER_AGENT = "HorusGram Android (https://github.com/plshours/HorusGram---Android)";
    private static final int MAX_SIZE = 1024 * 1024;
    private static final Pattern TIME = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]");

    // A track is asked about once per run, found or not.
    private static final HashSet<Long> requested = new HashSet<>();

    private HorusLyrics() {
    }

    private static File cacheFile(long documentId) {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "horus_lyrics");
        dir.mkdirs();
        return new File(dir, documentId + ".lrc");
    }

    // Calls back on the UI thread, with an empty list when nothing is found.
    public static void load(long documentId, String title, String performer, int durationSeconds, Callback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<Line> lines = new ArrayList<>();
            try {
                File cache = cacheFile(documentId);
                String text = cache.exists() ? read(cache) : null;
                boolean ask;
                synchronized (requested) {
                    ask = text == null && requested.add(documentId);
                }
                if (ask && !TextUtils.isEmpty(title) && !TextUtils.isEmpty(performer)) {
                    text = fetch(title.trim(), performer.trim(), durationSeconds);
                    if (!TextUtils.isEmpty(text)) {
                        try (FileOutputStream out = new FileOutputStream(cache)) {
                            out.write(text.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
                if (!TextUtils.isEmpty(text)) {
                    lines = parse(text);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final ArrayList<Line> result = lines;
            AndroidUtilities.runOnUIThread(() -> callback.run(result));
        });
    }

    private static String fetch(String title, String performer, int duration) throws Exception {
        String query = "track_name=" + URLEncoder.encode(title, "UTF-8")
                + "&artist_name=" + URLEncoder.encode(performer, "UTF-8");
        // The exact match wants the length of the track as well; the search
        // by name is the looser one, tried when that finds nothing.
        String body = get(API + "get?" + query + (duration > 0 ? "&duration=" + duration : ""));
        String text = body != null && body.trim().startsWith("{") ? pick(new JSONObject(body)) : null;
        if (!TextUtils.isEmpty(text)) {
            return text;
        }
        body = get(API + "search?" + query);
        if (body != null && body.trim().startsWith("[")) {
            JSONArray array = new JSONArray(body);
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.optJSONObject(i);
                if (entry != null) {
                    text = pick(entry);
                    if (!TextUtils.isEmpty(text)) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    // The timed lyrics when there are some, so the view can follow the song.
    private static String pick(JSONObject entry) {
        String synced = entry.optString("syncedLyrics", "");
        if (!entry.isNull("syncedLyrics") && !synced.trim().isEmpty()) {
            return synced;
        }
        String plain = entry.optString("plainLyrics", "");
        return entry.isNull("plainLyrics") ? null : plain;
    }

    private static String get(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                return readStream(in);
            }
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String read(File file) throws Exception {
        if (file.length() > MAX_SIZE) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            return readStream(in);
        }
    }

    private static String readStream(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_SIZE) {
                return null;
            }
        }
        return out.toString("UTF-8");
    }

    // "[01:23.45]words" lines become timed ones; anything else is kept as a
    // plain line, so lyrics without timing still show up line by line.
    public static ArrayList<Line> parse(String text) {
        ArrayList<Line> timed = new ArrayList<>();
        ArrayList<Line> plain = new ArrayList<>();
        for (String raw : text.split("\r?\n")) {
            Matcher matcher = TIME.matcher(raw);
            ArrayList<Long> times = new ArrayList<>();
            int end = 0;
            while (matcher.find() && matcher.start() == end) {
                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                String fraction = matcher.group(3);
                long millis = 0;
                if (fraction != null) {
                    millis = Long.parseLong(fraction);
                    if (fraction.length() == 1) {
                        millis *= 100;
                    } else if (fraction.length() == 2) {
                        millis *= 10;
                    }
                }
                times.add(minutes * 60000 + seconds * 1000 + millis);
                end = matcher.end();
            }
            String words = raw.substring(end).trim();
            if (!times.isEmpty()) {
                for (Long time : times) {
                    timed.add(new Line(time, words));
                }
            } else if (!words.isEmpty() && !words.startsWith("[")) {
                plain.add(new Line(-1, words));
            }
        }
        if (!timed.isEmpty()) {
            Collections.sort(timed, (a, b) -> Long.compare(a.time, b.time));
            return timed;
        }
        return plain;
    }
}
