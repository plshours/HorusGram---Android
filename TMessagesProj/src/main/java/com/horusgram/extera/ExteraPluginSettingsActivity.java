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

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;

import com.chaquo.python.PyObject;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

// The page a plugin builds in create_settings(), and its sub-pages.
public class ExteraPluginSettingsActivity extends BaseNekoSettingsActivity {

    private static final int ROW = 100;

    private final ExteraPlugins.Plugin plugin;
    private final String path;
    private final String title;
    private JSONArray schema = new JSONArray();
    private final Runnable listener = () -> {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    };

    public ExteraPluginSettingsActivity(ExteraPlugins.Plugin plugin, String path, String title) {
        this.plugin = plugin;
        this.path = path;
        this.title = title;
    }

    @Override
    public boolean onFragmentCreate() {
        ExteraPlugins.addListener(listener);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        ExteraPlugins.removeListener(listener);
        super.onFragmentDestroy();
    }

    private static int icon(Context context, String name) {
        if (TextUtils.isEmpty(name) || context == null) {
            return 0;
        }
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }

    private static String valueText(JSONObject entry) {
        Object value = entry.opt("value");
        return value == null || value == JSONObject.NULL ? "" : String.valueOf(value);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        PyObject host = ExteraPlugins.host();
        schema = new JSONArray();
        if (host == null) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.ExteraPluginNoPython)));
            return;
        }
        try {
            schema = new JSONArray(host.callAttr("settings_json", plugin.id, path).toString());
        } catch (Throwable e) {
            FileLog.e(e);
        }
        Context context = getParentActivity();
        boolean open = false;
        for (int i = 0; i < schema.length(); i++) {
            JSONObject entry = schema.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String type = entry.optString("type");
            String text = entry.optString("text");
            String subtext = entry.optString("subtext", null);
            int iconRes = icon(context, entry.optString("icon", null));
            int id = ROW + i;
            switch (type) {
                case "header":
                    if (open) {
                        items.add(UItem.asShadow(null));
                    }
                    items.add(UItem.asHeader(text));
                    open = true;
                    break;
                case "divider":
                    items.add(UItem.asShadow(entry.has("text") ? text : null));
                    open = false;
                    break;
                case "switch": {
                    UItem row = TextUtils.isEmpty(subtext) ? UItem.asCheck(id, text) : UItem.asCheck(id, text, subtext);
                    items.add(row.setChecked(entry.optBoolean("value")));
                    open = true;
                    break;
                }
                case "selector": {
                    JSONArray options = entry.optJSONArray("items");
                    int selected = entry.optInt("value");
                    String label = options != null && selected >= 0 && selected < options.length() ? options.optString(selected) : "";
                    items.add(iconRes != 0 ? UItem.asButton(id, iconRes, text, label) : UItem.asButton(id, text, label));
                    open = true;
                    break;
                }
                case "input":
                    items.add(iconRes != 0 ? UItem.asButton(id, iconRes, text, valueText(entry)) : UItem.asButton(id, text, valueText(entry)));
                    open = true;
                    break;
                case "edittext": {
                    String value = valueText(entry);
                    items.add(UItem.asButton(id, TextUtils.isEmpty(value) ? entry.optString("hint") : value));
                    open = true;
                    break;
                }
                case "text": {
                    UItem row;
                    if (!TextUtils.isEmpty(subtext)) {
                        row = UItem.asButtonSubtext(id, iconRes, text, subtext);
                    } else if (iconRes != 0) {
                        row = UItem.asButton(id, iconRes, text);
                    } else {
                        row = UItem.asButton(id, text);
                    }
                    if (entry.optBoolean("red")) {
                        row.red();
                    } else if (entry.optBoolean("accent")) {
                        row.accent();
                    }
                    items.add(row);
                    open = true;
                    break;
                }
                case "custom": {
                    try {
                        PyObject custom = host.callAttr("settings_custom", plugin.id, path, i, context);
                        Object value = custom == null ? null : custom.toJava(Object.class);
                        if (value instanceof UItem) {
                            UItem row = (UItem) value;
                            if (row.id == 0) {
                                row.id = id;
                            }
                            items.add(row);
                        } else if (value instanceof View) {
                            View view = (View) value;
                            if (view.getParent() instanceof android.view.ViewGroup) {
                                ((android.view.ViewGroup) view.getParent()).removeView(view);
                            }
                            items.add(UItem.asCustom(id, view));
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                    open = true;
                    break;
                }
            }
        }
        if (open) {
            items.add(UItem.asShadow(null));
        }
    }

    private JSONObject entry(UItem item) {
        return item.id >= ROW ? schema.optJSONObject(item.id - ROW) : null;
    }

    private void change(int index, String json) {
        PyObject host = ExteraPlugins.host();
        if (host == null) {
            return;
        }
        try {
            host.callAttr("settings_change", plugin.id, path, index, json);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        listView.adapter.update(true);
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        JSONObject entry = entry(item);
        if (entry == null) {
            return;
        }
        int index = item.id - ROW;
        switch (entry.optString("type")) {
            case "switch":
                change(index, entry.optBoolean("value") ? "false" : "true");
                break;
            case "selector":
                chooseOption(entry, index);
                break;
            case "input":
                editText(entry, index, false, 0);
                break;
            case "edittext":
                editText(entry, index, entry.optBoolean("multiline"), entry.optInt("max_length", 0));
                break;
            case "text":
            case "custom":
                PyObject host = ExteraPlugins.host();
                if (host == null) {
                    return;
                }
                if (entry.optBoolean("sub")) {
                    try {
                        JSONArray next = new JSONArray(path);
                        next.put(index);
                        presentFragment(new ExteraPluginSettingsActivity(plugin, next.toString(), entry.optString("text", title)));
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
                if (entry.optBoolean("clickable")) {
                    try {
                        host.callAttr("settings_click", plugin.id, path, index, view, false);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
                break;
        }
    }

    @Override
    protected boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        JSONObject entry = entry(item);
        PyObject host = ExteraPlugins.host();
        if (entry == null || host == null) {
            return false;
        }
        try {
            return host.callAttr("settings_click", plugin.id, path, item.id - ROW, view, true).toBoolean();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    private void chooseOption(JSONObject entry, int index) {
        JSONArray options = entry.optJSONArray("items");
        if (options == null || getParentActivity() == null) {
            return;
        }
        CharSequence[] labels = new CharSequence[options.length()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = options.optString(i);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
        builder.setTitle(entry.optString("text"));
        builder.setItems(labels, (dialog, which) -> change(index, String.valueOf(which)));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void editText(JSONObject entry, int index, boolean multiline, int maxLength) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        EditTextBoldCursor field = new EditTextBoldCursor(context);
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        field.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        field.setBackground(null);
        field.setLineColors(
                Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider),
                Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
        field.setInputType(multiline ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_CLASS_TEXT);
        if (!multiline) {
            field.setSingleLine(true);
        }
        if (maxLength > 0) {
            field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        }
        field.setHint(entry.optString("hint", ""));
        field.setText(valueText(entry));
        field.setSelection(field.length());

        FrameLayout container = new FrameLayout(context);
        container.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 6, 24, 0));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(entry.optString("text", entry.optString("hint")));
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> change(index, JSONObject.quote(field.getText().toString())));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
        AndroidUtilities.runOnUIThread(() -> {
            field.requestFocus();
            AndroidUtilities.showKeyboard(field);
        }, 200);
    }

    @Override
    protected String getActionBarTitle() {
        return title;
    }
}
