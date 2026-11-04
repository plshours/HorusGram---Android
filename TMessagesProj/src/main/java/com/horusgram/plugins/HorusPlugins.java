/*
 * This file is part of HorusGram for Android, an unofficial Telegram client.
 * Copyright (C) 2026 HorusGram contributors.
 *
 * HorusGram is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 2 of the License, or (at your option)
 * any later version. See the LICENSE file in the root of this repository.
 */
package com.horusgram.plugins;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// HorusGram plugins: single JavaScript files run by Rhino, with the `HG`
// object of PLUGINS.md and nothing else. The runtime that makes `HG`
// (assets/horus_plugin_prelude.js) is shared with HorusGram Desktop, so a
// plugin behaves the same on both.
public final class HorusPlugins {

    public static final String EXTENSION = ".hgplugin";
    private static final int MAX_SIZE = 512 * 1024;
    private static final long TIME_LIMIT = 2000;
    private static final Pattern HEADER = Pattern.compile("//\\s*==HorusGramPlugin==(.*?)//\\s*==/HorusGramPlugin==", Pattern.DOTALL);
    private static final Pattern FIELD = Pattern.compile("//\\s*@([A-Za-z][A-Za-z-]*)\\s+(.*)");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    public static final class Plugin {
        public String id;
        public String name;
        public String version = "";
        public String author = "";
        public String description = "";
        public String source;
        public File file;
        public boolean enabled;
        public String error;
        public String settingsJson = "[]";
        public final ArrayList<String[]> commands = new ArrayList<>();
        Scriptable scope;
        Scriptable runtime;

        public boolean hasSettings() {
            return settingsJson != null && !settingsJson.equals("[]");
        }
    }

    private static final ArrayList<Plugin> plugins = new ArrayList<>();
    private static boolean loaded;
    private static String prelude;
    private static final DispatchQueue queue = new DispatchQueue("horusPlugins");
    // A message a plugin sends itself is not handed to the plugins again.
    private static final ThreadLocal<Boolean> fromPlugin = new ThreadLocal<>();

    private HorusPlugins() {
    }

    // Plugins never see Java: the only class they may touch is the bridge,
    // and a call that runs too long is stopped.
    private static final ContextFactory factory = new ContextFactory() {
        @Override
        protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setOptimizationLevel(-1);
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setInstructionObserverThreshold(10000);
            cx.setClassShutter(name -> name.equals(Bridge.class.getName()));
            // Strings reach scripts as JavaScript strings, not as Java objects.
            cx.getWrapFactory().setJavaPrimitiveWrap(false);
            return cx;
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            Object start = cx.getThreadLocal("start");
            if (start instanceof Long && SystemClock.elapsedRealtime() - (Long) start > TIME_LIMIT) {
                throw new Error("HorusGram plugin took too long");
            }
        }
    };

    // What a plugin reaches through `HG`; see horus_plugin_prelude.js.
    public static final class Bridge {
        private final Plugin plugin;

        Bridge(Plugin plugin) {
            this.plugin = plugin;
        }

        public String platform() {
            return "android";
        }

        public void declareSettings(String json) {
            plugin.settingsJson = json;
        }

        public String getSetting(String key) {
            return prefs(plugin.id).getString("s:" + key, null);
        }

        public String storageGet(String key) {
            return prefs(plugin.id).getString("d:" + key, null);
        }

        public void storageSet(String key, String json) {
            prefs(plugin.id).edit().putString("d:" + key, json).apply();
        }

        public void sendMessage(String chatId, String text) {
            final long peer;
            try {
                peer = Long.parseLong(chatId);
            } catch (NumberFormatException e) {
                return;
            }
            final int account = UserConfig.selectedAccount;
            AndroidUtilities.runOnUIThread(() -> {
                fromPlugin.set(true);
                try {
                    SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(text, peer));
                } finally {
                    fromPlugin.set(false);
                }
            });
        }

        public void toast(String text) {
            AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_SHORT).show());
        }

        public void log(String text) {
            FileLog.d("HorusGram plugin " + plugin.id + ": " + text);
        }
    }

    // ---- Files and state.

    private static File directory() {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "horus_plugins");
        dir.mkdirs();
        return dir;
    }

    static SharedPreferences prefs(String id) {
        return ApplicationLoader.applicationContext.getSharedPreferences("horusgram_plugin_" + id, Activity.MODE_PRIVATE);
    }

    private static SharedPreferences state() {
        return ApplicationLoader.applicationContext.getSharedPreferences("horusgram_plugins", Activity.MODE_PRIVATE);
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
            if (out.size() > MAX_SIZE) {
                throw new IllegalArgumentException("too large");
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String prelude() throws Exception {
        if (prelude == null) {
            try (InputStream in = ApplicationLoader.applicationContext.getAssets().open("horus_plugin_prelude.js")) {
                prelude = read(in);
            }
        }
        return prelude;
    }

    // The header of a plugin, or null when the text is not one.
    public static Plugin parse(String source) {
        if (source == null) {
            return null;
        }
        Matcher header = HEADER.matcher(source);
        if (!header.find()) {
            return null;
        }
        Plugin plugin = new Plugin();
        plugin.source = source;
        for (String line : header.group(1).split("\r?\n")) {
            Matcher field = FIELD.matcher(line.trim());
            if (!field.matches()) {
                continue;
            }
            String value = field.group(2).trim();
            switch (field.group(1).toLowerCase(Locale.ROOT)) {
                case "id": plugin.id = value; break;
                case "name": plugin.name = value; break;
                case "version": plugin.version = value; break;
                case "author": plugin.author = value; break;
                case "description": plugin.description = value; break;
            }
        }
        if (plugin.id == null || !ID.matcher(plugin.id).matches() || TextUtils.isEmpty(plugin.name)) {
            return null;
        }
        return plugin;
    }

    public static synchronized ArrayList<Plugin> list() {
        ensureLoaded();
        return new ArrayList<>(plugins);
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        File[] files = directory().listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.getName().endsWith(EXTENSION)) {
                continue;
            }
            try (InputStream in = new FileInputStream(file)) {
                Plugin plugin = parse(read(in));
                if (plugin == null) {
                    continue;
                }
                plugin.file = file;
                plugin.enabled = state().getBoolean("enabled:" + plugin.id, true);
                plugins.add(plugin);
                if (plugin.enabled) {
                    start(plugin);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    // ---- Running.

    private static synchronized void start(Plugin plugin) {
        plugin.error = null;
        plugin.commands.clear();
        plugin.settingsJson = "[]";
        Context cx = factory.enterContext();
        try {
            cx.putThreadLocal("start", SystemClock.elapsedRealtime());
            ScriptableObject scope = cx.initSafeStandardObjects();
            ScriptableObject.putProperty(scope, "__hgBridge", Context.javaToJS(new Bridge(plugin), scope));
            cx.evaluateString(scope, prelude(), "horus_plugin_prelude.js", 1, null);
            plugin.runtime = (Scriptable) ScriptableObject.getProperty(scope, "__hgRuntime");
            scope.delete("__hgBridge");
            scope.delete("__hgRuntime");
            plugin.scope = scope;
            cx.evaluateString(scope, plugin.source, plugin.id + EXTENSION, 1, null);
            JSONArray commands = new JSONArray(Context.toString(ScriptableObject.callMethod(cx, plugin.runtime, "commands", new Object[0])));
            for (int i = 0; i < commands.length(); i++) {
                JSONObject command = commands.getJSONObject(i);
                plugin.commands.add(new String[]{command.getString("name"), command.optString("description")});
            }
        } catch (Throwable e) {
            FileLog.e(e);
            plugin.error = e.getMessage() != null ? e.getMessage() : e.toString();
            plugin.runtime = null;
            plugin.scope = null;
        } finally {
            Context.exit();
        }
    }

    private static synchronized JSONObject call(Plugin plugin, String method, Object... args) {
        if (plugin.runtime == null) {
            return null;
        }
        Context cx = factory.enterContext();
        try {
            cx.putThreadLocal("start", SystemClock.elapsedRealtime());
            return new JSONObject(Context.toString(ScriptableObject.callMethod(cx, plugin.runtime, method, args)));
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        } finally {
            Context.exit();
        }
    }

    // The text to send in place of `text`, or null to send nothing.
    public static String processOutgoing(long peer, String text) {
        if (text == null || Boolean.TRUE.equals(fromPlugin.get())) {
            return text;
        }
        ArrayList<Plugin> running = new ArrayList<>();
        for (Plugin plugin : list()) {
            if (plugin.enabled && plugin.runtime != null) {
                running.add(plugin);
            }
        }
        if (running.isEmpty()) {
            return text;
        }
        try {
            String chat = new JSONObject().put("chatId", String.valueOf(peer)).toString();
            if (text.startsWith(".") && text.length() > 1) {
                String body = text.substring(1);
                int space = body.indexOf(' ');
                String name = (space < 0 ? body : body.substring(0, space)).toLowerCase(Locale.ROOT);
                String args = space < 0 ? "" : body.substring(space + 1).trim();
                for (Plugin plugin : running) {
                    if (!hasCommand(plugin, name)) {
                        continue;
                    }
                    JSONObject result = call(plugin, "command", name, args, chat);
                    if (result == null || result.optBoolean("cancel") || !result.has("text")) {
                        return null;
                    }
                    text = result.getString("text");
                    break;
                }
            }
            for (Plugin plugin : running) {
                JSONObject message = new JSONObject()
                        .put("text", text)
                        .put("chatId", String.valueOf(peer))
                        .put("fromId", String.valueOf(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()))
                        .put("out", true);
                JSONObject result = call(plugin, "dispatch", "outgoingMessage", message.toString());
                if (result == null) {
                    continue;
                }
                if (result.optBoolean("cancel")) {
                    return null;
                }
                if (result.has("text")) {
                    text = result.getString("text");
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return text;
    }

    private static boolean hasCommand(Plugin plugin, String name) {
        for (String[] command : plugin.commands) {
            if (command[0].equals(name)) {
                return true;
            }
        }
        return false;
    }

    // New messages others sent; the plugins see them off the caller's thread.
    public static void processIncoming(ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        final ArrayList<String> events = new ArrayList<>();
        for (MessageObject object : messages) {
            if (object == null || object.isOut() || object.messageOwner == null || TextUtils.isEmpty(object.messageOwner.message)) {
                continue;
            }
            try {
                events.add(new JSONObject()
                        .put("text", object.messageOwner.message)
                        .put("chatId", String.valueOf(object.getDialogId()))
                        .put("fromId", String.valueOf(object.getFromChatId()))
                        .put("out", false)
                        .toString());
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        if (events.isEmpty()) {
            return;
        }
        queue.postRunnable(() -> {
            for (Plugin plugin : list()) {
                if (!plugin.enabled || plugin.runtime == null) {
                    continue;
                }
                for (String event : events) {
                    call(plugin, "dispatch", "incomingMessage", event);
                }
            }
        });
    }

    // ---- Managing.

    public static synchronized void setEnabled(Plugin plugin, boolean enabled) {
        plugin.enabled = enabled;
        state().edit().putBoolean("enabled:" + plugin.id, enabled).apply();
        if (enabled) {
            start(plugin);
        } else {
            plugin.runtime = null;
            plugin.scope = null;
            plugin.commands.clear();
        }
    }

    public static synchronized void delete(Plugin plugin) {
        plugins.remove(plugin);
        if (plugin.file != null) {
            plugin.file.delete();
        }
        state().edit().remove("enabled:" + plugin.id).apply();
        prefs(plugin.id).edit().clear().apply();
    }

    public static void setSetting(Plugin plugin, String key, String json) {
        prefs(plugin.id).edit().putString("s:" + key, json).apply();
    }

    public static String getSettingJson(Plugin plugin, String key) {
        return prefs(plugin.id).getString("s:" + key, null);
    }

    // Installs or replaces a plugin; returns it, with `error` set when its
    // script fails to start.
    public static synchronized Plugin install(String source) throws Exception {
        ensureLoaded();
        Plugin plugin = parse(source);
        if (plugin == null) {
            throw new IllegalArgumentException("not a plugin");
        }
        for (int i = 0; i < plugins.size(); i++) {
            if (plugins.get(i).id.equals(plugin.id)) {
                plugins.remove(i);
                break;
            }
        }
        plugin.file = new File(directory(), plugin.id + EXTENSION);
        try (FileOutputStream out = new FileOutputStream(plugin.file)) {
            out.write(source.getBytes(StandardCharsets.UTF_8));
        }
        plugin.enabled = true;
        state().edit().putBoolean("enabled:" + plugin.id, true).apply();
        plugins.add(plugin);
        start(plugin);
        return plugin;
    }

    public static Plugin installExample() throws Exception {
        try (InputStream in = ApplicationLoader.applicationContext.getAssets().open("horus_example.hgplugin")) {
            return install(read(in));
        }
    }

    // Opening a .hgplugin file from a chat asks before installing it.
    public static void askInstall(Activity activity, File file) {
        String source = null;
        try (InputStream in = new FileInputStream(file)) {
            source = read(in);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        Plugin plugin = parse(source);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        if (plugin == null) {
            builder.setTitle(LocaleController.getString(R.string.HorusPlugins));
            builder.setMessage(LocaleController.getString(R.string.HorusPluginInvalid));
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            builder.show();
            return;
        }
        boolean installed = false;
        for (Plugin existing : list()) {
            if (existing.id.equals(plugin.id)) {
                installed = true;
                break;
            }
        }
        StringBuilder text = new StringBuilder();
        text.append(plugin.name);
        if (!TextUtils.isEmpty(plugin.version)) {
            text.append(' ').append(plugin.version);
        }
        if (!TextUtils.isEmpty(plugin.author)) {
            text.append('\n').append(LocaleController.formatString(R.string.HorusPluginAuthor, plugin.author));
        }
        if (!TextUtils.isEmpty(plugin.description)) {
            text.append("\n\n").append(plugin.description);
        }
        text.append("\n\n").append(LocaleController.getString(R.string.HorusPluginPermissions));
        final String finalSource = source;
        builder.setTitle(LocaleController.getString(installed ? R.string.HorusPluginUpdate : R.string.HorusPluginInstall));
        builder.setMessage(text.toString());
        builder.setPositiveButton(LocaleController.getString(installed ? R.string.HorusPluginUpdate : R.string.HorusPluginInstall), (dialog, which) -> {
            try {
                Plugin result = install(finalSource);
                String message = result.error == null
                        ? LocaleController.formatString(R.string.HorusPluginInstalled, result.name)
                        : LocaleController.formatString(R.string.HorusPluginFailed, result.error);
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            } catch (Throwable e) {
                FileLog.e(e);
                Toast.makeText(activity, LocaleController.getString(R.string.HorusPluginInvalid), Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    public static boolean isPluginFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    // The settings a plugin declared, as a list of objects.
    public static JSONArray settings(Plugin plugin) {
        try {
            return new JSONArray(plugin.settingsJson);
        } catch (Throwable e) {
            return new JSONArray();
        }
    }

    public static HashSet<String> ids() {
        HashSet<String> result = new HashSet<>();
        for (Plugin plugin : list()) {
            result.add(plugin.id);
        }
        return result;
    }
}
