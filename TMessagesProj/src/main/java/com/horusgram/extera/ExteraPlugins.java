/*
 * This file is part of HorusGram for Android, an unofficial Telegram client.
 * Copyright (C) 2026 HorusGram contributors.
 *
 * HorusGram is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 2 of the License, or (at your option)
 * any later version. See the LICENSE file in the root of this repository.
 */
package com.horusgram.extera;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.Toast;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessageChatArguments;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Plugins written for exteraGram: .plugin files in Python, run by Chaquopy
// with the modules exteraGram gives them (base_plugin, client_utils, ui, ...).
// Those modules are HorusGram's own, in src/main/python; this class starts
// Python, keeps the list, and is where the client calls into the plugins.
public final class ExteraPlugins {

    public static final String EXTENSION = ".plugin";
    private static final String PREFS = "horusgram_extera";
    private static final long MAX_SOURCE = 4L * 1024 * 1024;

    public static final DispatchQueue queue = new DispatchQueue("pluginsQueue");

    public static class Plugin {
        public String id;
        public String name;
        public String version;
        public String author;
        public String description;
        public boolean enabled;
        public boolean loaded;
        public String error;
        public boolean hasSettings;
    }

    private static final Map<String, Plugin> plugins = Collections.synchronizedMap(new java.util.LinkedHashMap<>());
    private static volatile boolean started;
    private static volatile PyObject host;
    private static volatile boolean pythonFailed;

    // What the loaded plugins asked to see, so the client only calls into
    // Python when one of them wants it.
    private static final Map<String, HashSet<String>> hookNames = new ConcurrentHashMap<>();
    private static final Map<String, HashSet<String>> hookSubstrings = new ConcurrentHashMap<>();
    private static final HashSet<String> sendHooks = new HashSet<>();
    private static volatile HashSet<String> allNames = new HashSet<>();
    private static volatile String[] allSubstrings = new String[0];
    private static volatile boolean anySendHook;

    private static final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    private ExteraPlugins() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static File directory() {
        File dir = new File(ApplicationLoader.getFilesDirFixed(), "extera_plugins");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static File file(String id) {
        return new File(directory(), id + EXTENSION);
    }

    public static boolean isPluginFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    public static void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private static void changed() {
        AndroidUtilities.runOnUIThread(() -> {
            for (Runnable listener : listeners) {
                listener.run();
            }
        });
    }

    // ---- Python ----

    // Starts Python and the plugin host once; null when Python cannot run.
    public static PyObject host() {
        if (host != null || pythonFailed) {
            return host;
        }
        synchronized (ExteraPlugins.class) {
            if (host != null || pythonFailed) {
                return host;
            }
            try {
                if (!Python.isStarted()) {
                    Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));
                }
                try {
                    top.canyie.pine.PineConfig.debug = false;
                    top.canyie.pine.PineConfig.debuggable = BuildConfig.DEBUG;
                    top.canyie.pine.Pine.ensureInitialized();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                PyObject module = Python.getInstance().getModule("horus_extera");
                module.callAttr("init", directory().getAbsolutePath(), BuildConfig.DEBUG);
                host = module;
            } catch (Throwable e) {
                FileLog.e(e);
                pythonFailed = true;
            }
            return host;
        }
    }

    // Loads the enabled plugins in the background once the app is up.
    public static void start() {
        if (started) {
            return;
        }
        started = true;
        readList();
        boolean any = false;
        for (Plugin plugin : list()) {
            any |= plugin.enabled;
        }
        if (!any) {
            return;
        }
        queue.postRunnable(() -> {
            for (Plugin plugin : list()) {
                if (plugin.enabled) {
                    load(plugin);
                }
            }
            appEvent("START");
            changed();
        });
    }

    private static void readList() {
        SharedPreferences prefs = prefs();
        File[] files = directory().listFiles();
        if (files == null) {
            return;
        }
        ArrayList<File> sorted = new ArrayList<>();
        Collections.addAll(sorted, files);
        Collections.sort(sorted, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : sorted) {
            String name = f.getName();
            if (!name.endsWith(EXTENSION)) {
                continue;
            }
            String id = name.substring(0, name.length() - EXTENSION.length());
            String meta = prefs.getString("meta:" + id, null);
            Plugin plugin = fromMeta(meta);
            if (plugin == null) {
                plugin = new Plugin();
                plugin.id = id;
                plugin.name = id;
            }
            plugin.id = id;
            plugin.enabled = prefs.getBoolean("enabled:" + id, true);
            plugins.put(id, plugin);
        }
    }

    private static Plugin fromMeta(String json) {
        if (json == null) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(json);
            Plugin plugin = new Plugin();
            plugin.id = object.optString("id");
            plugin.name = object.optString("name", plugin.id);
            plugin.version = object.optString("version", "");
            plugin.author = object.optString("author", "");
            plugin.description = object.optString("description", "");
            return TextUtils.isEmpty(plugin.id) ? null : plugin;
        } catch (Throwable e) {
            return null;
        }
    }

    public static ArrayList<Plugin> list() {
        synchronized (plugins) {
            return new ArrayList<>(plugins.values());
        }
    }

    public static Plugin get(String id) {
        return plugins.get(id);
    }

    private static void load(Plugin plugin) {
        PyObject module = host();
        if (module == null) {
            plugin.error = "Python did not start";
            return;
        }
        try {
            PyObject error = module.callAttr("load_plugin", plugin.id, file(plugin.id).getAbsolutePath());
            plugin.error = error == null ? null : error.toString();
            plugin.loaded = plugin.error == null;
            plugin.hasSettings = plugin.loaded && module.callAttr("has_settings", plugin.id).toBoolean();
        } catch (Throwable e) {
            FileLog.e(e);
            plugin.error = String.valueOf(e.getMessage());
            plugin.loaded = false;
        }
    }

    private static void unload(Plugin plugin) {
        PyObject module = host;
        if (module != null && plugin.loaded) {
            try {
                module.callAttr("unload_plugin", plugin.id);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        plugin.loaded = false;
        plugin.hasSettings = false;
        clearHooks(plugin.id);
    }

    public static void setEnabled(Plugin plugin, boolean enabled) {
        plugin.enabled = enabled;
        prefs().edit().putBoolean("enabled:" + plugin.id, enabled).apply();
        queue.postRunnable(() -> {
            if (enabled) {
                load(plugin);
            } else {
                unload(plugin);
                plugin.error = null;
            }
            changed();
        });
    }

    public static void delete(Plugin plugin) {
        queue.postRunnable(() -> {
            unload(plugin);
            PyObject module = host;
            if (module != null) {
                try {
                    module.callAttr("forget_plugin", plugin.id);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            file(plugin.id).delete();
            prefs().edit().remove("enabled:" + plugin.id).remove("meta:" + plugin.id).apply();
            plugins.remove(plugin.id);
            changed();
        });
    }

    // ---- Installing ----

    private static String read(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int read;
        long total = 0;
        while ((read = in.read(buffer)) > 0) {
            total += read;
            if (total > MAX_SOURCE) {
                throw new IllegalArgumentException("too big");
            }
            out.write(buffer, 0, read);
        }
        return out.toString("UTF-8");
    }

    // The plugin's metadata, read by Python the way exteraGram reads it:
    // the top-level __id__, __name__, ... of the file.
    private static Plugin parse(String source) {
        PyObject module = host();
        if (module == null || source == null) {
            return null;
        }
        try {
            PyObject json = module.callAttr("parse_meta", source);
            return json == null ? null : fromMeta(json.toString());
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static Plugin install(String source, Plugin meta) throws Exception {
        Plugin existing = plugins.get(meta.id);
        if (existing != null) {
            unload(existing);
        }
        try (OutputStream out = new FileOutputStream(file(meta.id))) {
            out.write(source.getBytes(StandardCharsets.UTF_8));
        }
        JSONObject json = new JSONObject();
        json.put("id", meta.id);
        json.put("name", meta.name);
        json.put("version", meta.version);
        json.put("author", meta.author);
        json.put("description", meta.description);
        prefs().edit().putString("meta:" + meta.id, json.toString()).putBoolean("enabled:" + meta.id, true).apply();
        meta.enabled = true;
        plugins.put(meta.id, meta);
        load(meta);
        changed();
        return meta;
    }

    // Opening a .plugin file from a chat asks before installing it.
    public static void askInstall(Activity activity, File source) {
        AlertDialog progress = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        queue.postRunnable(() -> {
            String text = null;
            try (InputStream in = new FileInputStream(source)) {
                text = read(in);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final String finalText = text;
            final Plugin meta = parse(text);
            final boolean noPython = host == null;
            AndroidUtilities.runOnUIThread(() -> {
                progress.dismiss();
                showInstall(activity, finalText, meta, noPython);
            });
        });
    }

    private static void showInstall(Activity activity, String source, Plugin meta, boolean noPython) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        if (meta == null) {
            builder.setTitle(LocaleController.getString(R.string.ExteraPlugins));
            builder.setMessage(LocaleController.getString(noPython ? R.string.ExteraPluginNoPython : R.string.ExteraPluginInvalid));
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            builder.show();
            return;
        }
        boolean installed = plugins.containsKey(meta.id);
        StringBuilder text = new StringBuilder();
        text.append(meta.name);
        if (!TextUtils.isEmpty(meta.version)) {
            text.append(' ').append(meta.version);
        }
        if (!TextUtils.isEmpty(meta.author)) {
            text.append('\n').append(LocaleController.formatString(R.string.HorusPluginAuthor, meta.author));
        }
        if (!TextUtils.isEmpty(meta.description)) {
            text.append("\n\n").append(meta.description);
        }
        text.append("\n\n").append(LocaleController.getString(R.string.ExteraPluginPermissions));
        builder.setTitle(LocaleController.getString(installed ? R.string.HorusPluginUpdate : R.string.HorusPluginInstall));
        builder.setMessage(text.toString());
        builder.setPositiveButton(LocaleController.getString(installed ? R.string.HorusPluginUpdate : R.string.HorusPluginInstall), (dialog, which) -> queue.postRunnable(() -> {
            String message;
            try {
                Plugin result = install(source, meta);
                message = result.error == null
                        ? LocaleController.formatString(R.string.HorusPluginInstalled, result.name)
                        : LocaleController.formatString(R.string.HorusPluginFailed, firstLine(result.error));
            } catch (Throwable e) {
                FileLog.e(e);
                message = LocaleController.getString(R.string.ExteraPluginInvalid);
            }
            final String finalMessage = message;
            AndroidUtilities.runOnUIThread(() -> Toast.makeText(activity, finalMessage, Toast.LENGTH_LONG).show());
        }));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    public static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        int lastBreak = trimmed.lastIndexOf('\n');
        // A Python traceback ends with the line that says what went wrong.
        return lastBreak < 0 ? trimmed : trimmed.substring(lastBreak + 1);
    }

    // ---- Hooks the plugins register (called from Python) ----

    public static void registerHook(String pluginId, String name, boolean substring) {
        Map<String, HashSet<String>> target = substring ? hookSubstrings : hookNames;
        HashSet<String> set = target.get(pluginId);
        if (set == null) {
            set = new HashSet<>();
            target.put(pluginId, set);
        }
        synchronized (set) {
            set.add(name);
        }
        rebuildHooks();
    }

    public static void unregisterHook(String pluginId, String name) {
        for (Map<String, HashSet<String>> target : new Map[]{hookNames, hookSubstrings}) {
            HashSet<String> set = target.get(pluginId);
            if (set != null) {
                synchronized (set) {
                    set.remove(name);
                }
            }
        }
        rebuildHooks();
    }

    public static void setSendHook(String pluginId, boolean enabled) {
        synchronized (sendHooks) {
            if (enabled) {
                sendHooks.add(pluginId);
            } else {
                sendHooks.remove(pluginId);
            }
            anySendHook = !sendHooks.isEmpty();
        }
    }

    private static void clearHooks(String pluginId) {
        hookNames.remove(pluginId);
        hookSubstrings.remove(pluginId);
        setSendHook(pluginId, false);
        rebuildHooks();
    }

    private static synchronized void rebuildHooks() {
        HashSet<String> names = new HashSet<>();
        HashSet<String> substrings = new HashSet<>();
        for (HashSet<String> set : hookNames.values()) {
            synchronized (set) {
                names.addAll(set);
            }
        }
        for (HashSet<String> set : hookSubstrings.values()) {
            synchronized (set) {
                substrings.addAll(set);
            }
        }
        allNames = names;
        allSubstrings = substrings.toArray(new String[0]);
    }

    // TLRPC.TL_messages_setTyping -> "TL_messages_setTyping",
    // TL_account.updateStatus -> "TL_account_updateStatus", as exteraGram
    // names them.
    public static String tlName(Object object) {
        Class<?> clazz = object.getClass();
        Class<?> outer = clazz.getEnclosingClass();
        if (outer == null || outer == TLRPC.class || clazz.getSimpleName().startsWith("TL_")) {
            return clazz.getSimpleName();
        }
        return outer.getSimpleName() + "_" + clazz.getSimpleName();
    }

    private static boolean wants(String name) {
        if (allNames.contains(name)) {
            return true;
        }
        for (String part : allSubstrings) {
            if (name.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFalse(PyObject result) {
        try {
            return result != null && result.toJava(Object.class) instanceof Boolean && !result.toBoolean();
        } catch (Throwable e) {
            return false;
        }
    }

    // ---- Hook points in the client ----

    // True when a plugin cancelled the message.
    public static boolean onSendMessage(int account, SendMessagesHelper.SendMessageParams params) {
        if (!anySendHook || host == null) {
            return false;
        }
        try {
            PyObject result = host.callAttr("send_message_hook", account, params);
            return result != null && result.toBoolean();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    // The request to send instead, or null to send nothing.
    public static TLObject preRequest(int account, TLObject request) {
        if (host == null || request == null || (allNames.isEmpty() && allSubstrings.length == 0)) {
            return request;
        }
        String name = tlName(request);
        if (!wants(name)) {
            return request;
        }
        try {
            PyObject result = host.callAttr("pre_request_hook", name, account, request);
            if (result == null) {
                return request;
            }
            if (isFalse(result)) {
                return null;
            }
            Object value = result.toJava(Object.class);
            return value instanceof TLObject ? (TLObject) value : request;
        } catch (Throwable e) {
            FileLog.e(e);
            return request;
        }
    }

    public static RequestDelegate wrapRequest(int account, TLObject request, RequestDelegate onComplete) {
        if (host == null || request == null || (allNames.isEmpty() && allSubstrings.length == 0)) {
            return onComplete;
        }
        String name = tlName(request);
        if (!wants(name)) {
            return onComplete;
        }
        return (response, error) -> {
            TLObject finalResponse = response;
            try {
                PyObject result = host.callAttr("post_request_hook", name, account, response, error);
                if (result != null && !isFalse(result)) {
                    Object value = result.toJava(Object.class);
                    if (value instanceof TLObject) {
                        finalResponse = (TLObject) value;
                    }
                } else if (isFalse(result)) {
                    return;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            if (onComplete != null) {
                onComplete.run(finalResponse, error);
            }
        };
    }

    // True when a plugin dropped the whole updates container.
    public static boolean onUpdates(int account, TLRPC.Updates updates) {
        if (host == null || updates == null || (allNames.isEmpty() && allSubstrings.length == 0)) {
            return false;
        }
        String name = tlName(updates);
        if (!wants(name)) {
            return false;
        }
        try {
            return isFalse(host.callAttr("updates_hook", name, account, updates));
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    // Lets the plugins drop or replace single updates of a batch.
    public static void onUpdateArray(int account, ArrayList<TLRPC.Update> updates) {
        if (host == null || updates == null || updates.isEmpty() || (allNames.isEmpty() && allSubstrings.length == 0)) {
            return;
        }
        for (int i = updates.size() - 1; i >= 0; i--) {
            TLRPC.Update update = updates.get(i);
            if (update == null) {
                continue;
            }
            String name = tlName(update);
            if (!wants(name)) {
                continue;
            }
            try {
                PyObject result = host.callAttr("update_hook", name, account, update);
                if (result == null) {
                    continue;
                }
                if (isFalse(result)) {
                    updates.remove(i);
                    continue;
                }
                Object value = result.toJava(Object.class);
                if (value instanceof TLRPC.Update) {
                    updates.set(i, (TLRPC.Update) value);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    public static void appEvent(String event) {
        if (host == null) {
            return;
        }
        queue.postRunnable(() -> {
            try {
                host.callAttr("app_event", event);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // ---- Helpers the Python side uses for things too long to write there ----

    private static SendMessageChatArguments arguments() {
        return SendMessageChatArguments.EMPTY;
    }

    public static void sendPhoto(int account, long peer, String path, String caption, ArrayList<TLRPC.MessageEntity> entities, MessageObject replyTo, MessageObject editing) {
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.prepareSendingPhoto(AccountInstance.getInstance(account), path, null, peer, replyTo, null, null, caption, entities, null, null, 0, editing, true, 0, 0, arguments()));
    }

    public static void sendVideo(int account, long peer, String path, String caption, ArrayList<TLRPC.MessageEntity> entities, MessageObject replyTo, MessageObject editing, boolean spoiler) {
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.prepareSendingVideo(AccountInstance.getInstance(account), path, null, null, null, peer, replyTo, null, null, null, entities, 0, editing, true, 0, 0, false, spoiler, caption, arguments(), 0, 0));
    }

    public static void sendDocument(int account, long peer, String path, String caption, MessageObject replyTo, MessageObject editing) {
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.prepareSendingDocument(AccountInstance.getInstance(account), path, path, null, caption, null, peer, replyTo, null, null, null, editing, true, 0, null, arguments(), false));
    }

    public static void editText(int account, MessageObject message, String text, ArrayList<TLRPC.MessageEntity> entities, BaseFragment fragment) {
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.getInstance(account).editMessage(message, text, false, fragment, entities, 0, 0));
    }

    public static void sendMessage(int account, SendMessagesHelper.SendMessageParams params) {
        if (params.sendMessageChatArguments == null) {
            params.sendMessageChatArguments = arguments();
        }
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.getInstance(account).sendMessage(params));
    }

    // Reloads an open settings page of the plugin (set_setting(..., reload_settings=True)).
    public static void reloadSettings(String pluginId) {
        changed();
    }

    public static HashMap<String, Object> contextOf(Object... pairs) {
        HashMap<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}
