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

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
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

// The settings a plugin declared with HG.settings.declare.
public class HorusPluginSettingsActivity extends BaseNekoSettingsActivity {

    private static final int ROW = 100;

    private final HorusPlugins.Plugin plugin;
    private JSONArray schema = new JSONArray();

    public HorusPluginSettingsActivity(HorusPlugins.Plugin plugin) {
        this.plugin = plugin;
    }

    private Object value(JSONObject entry) {
        String json = HorusPlugins.getSettingJson(plugin, entry.optString("key"));
        if (json != null) {
            try {
                return new JSONArray("[" + json + "]").get(0);
            } catch (Throwable ignored) {
            }
        }
        return entry.opt("default");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        schema = HorusPlugins.settings(plugin);
        boolean open = false;
        for (int i = 0; i < schema.length(); i++) {
            JSONObject entry = schema.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String type = entry.optString("type");
            String title = entry.optString("title");
            switch (type) {
                case "header":
                    if (open) {
                        items.add(UItem.asShadow(null));
                    }
                    items.add(UItem.asHeader(title));
                    open = true;
                    break;
                case "switch":
                    items.add(UItem.asCheck(ROW + i, title).setChecked(Boolean.TRUE.equals(value(entry))));
                    open = true;
                    break;
                case "text": {
                    Object current = value(entry);
                    items.add(UItem.asButton(ROW + i, title, current == null ? "" : String.valueOf(current)));
                    open = true;
                    break;
                }
                case "info":
                    items.add(UItem.asShadow(title));
                    open = false;
                    break;
            }
        }
        if (open) {
            items.add(UItem.asShadow(null));
        }
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        JSONObject entry = schema.optJSONObject(item.id - ROW);
        if (entry == null) {
            return;
        }
        String key = entry.optString("key");
        if (key.isEmpty()) {
            return;
        }
        String type = entry.optString("type");
        if ("switch".equals(type)) {
            HorusPlugins.setSetting(plugin, key, Boolean.TRUE.equals(value(entry)) ? "false" : "true");
            listView.adapter.update(true);
        } else if ("text".equals(type)) {
            editText(entry, key);
        }
    }

    private void editText(JSONObject entry, String key) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        EditTextBoldCursor field = new EditTextBoldCursor(context);
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        field.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        field.setBackground(null);
        field.setLineColors(
                Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider),
                Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        Object current = value(entry);
        field.setText(current == null ? "" : String.valueOf(current));
        field.setSelection(field.length());

        FrameLayout container = new FrameLayout(context);
        container.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 6, 24, 0));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(entry.optString("title"));
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
            HorusPlugins.setSetting(plugin, key, JSONObject.quote(field.getText().toString()));
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
        AndroidUtilities.runOnUIThread(() -> {
            field.requestFocus();
            AndroidUtilities.showKeyboard(field);
        }, 200);
    }

    @Override
    protected String getActionBarTitle() {
        return plugin.name;
    }
}
