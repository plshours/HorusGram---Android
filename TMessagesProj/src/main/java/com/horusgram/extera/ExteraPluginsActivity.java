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

import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

// The installed exteraGram plugins: each can be turned off, set up and deleted.
public class ExteraPluginsActivity extends BaseNekoSettingsActivity {

    // Rows of a plugin are PLUGIN_ROW + index * 10 + part.
    private static final int PLUGIN_ROW = 1000;
    private static final int PART_ENABLED = 0;
    private static final int PART_SETTINGS = 1;
    private static final int PART_DELETE = 2;

    private ArrayList<ExteraPlugins.Plugin> plugins = new ArrayList<>();
    private final Runnable listener = () -> {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    };

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

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        plugins = ExteraPlugins.list();

        items.add(UItem.asHeader(LocaleController.getString(R.string.ExteraPlugins)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.ExteraPluginsInfo)));

        for (int i = 0; i < plugins.size(); i++) {
            ExteraPlugins.Plugin plugin = plugins.get(i);
            int base = PLUGIN_ROW + i * 10;
            String title = TextUtils.isEmpty(plugin.version) ? plugin.name : plugin.name + " " + plugin.version;
            items.add(UItem.asHeader(title));
            items.add(UItem.asCheck(base + PART_ENABLED, LocaleController.getString(R.string.HorusPluginEnabled)).setChecked(plugin.enabled));
            if (plugin.enabled && plugin.hasSettings) {
                items.add(UItem.asButton(base + PART_SETTINGS, R.drawable.msg_settings, LocaleController.getString(R.string.HorusPluginSettings)));
            }
            items.add(UItem.asButton(base + PART_DELETE, R.drawable.msg_delete, LocaleController.getString(R.string.HorusPluginDelete)).red());
            items.add(UItem.asShadow(describe(plugin)));
        }
    }

    private static String describe(ExteraPlugins.Plugin plugin) {
        StringBuilder text = new StringBuilder();
        if (!TextUtils.isEmpty(plugin.description)) {
            text.append(plugin.description);
        }
        if (!TextUtils.isEmpty(plugin.author)) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(LocaleController.formatString(R.string.HorusPluginAuthor, plugin.author));
        }
        if (plugin.enabled && !plugin.loaded && plugin.error == null) {
            text.append('\n').append(LocaleController.getString(R.string.ExteraPluginLoading));
        }
        if (plugin.error != null) {
            text.append('\n').append(LocaleController.formatString(R.string.HorusPluginFailed, ExteraPlugins.firstLine(plugin.error)));
        }
        return text.toString();
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id < PLUGIN_ROW) {
            return;
        }
        int index = (id - PLUGIN_ROW) / 10;
        int part = (id - PLUGIN_ROW) % 10;
        if (index >= plugins.size()) {
            return;
        }
        ExteraPlugins.Plugin plugin = plugins.get(index);
        if (part == PART_ENABLED) {
            ExteraPlugins.setEnabled(plugin, !plugin.enabled);
            listView.adapter.update(true);
        } else if (part == PART_SETTINGS) {
            presentFragment(new ExteraPluginSettingsActivity(plugin, "[]", plugin.name));
        } else if (part == PART_DELETE) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
            builder.setTitle(LocaleController.getString(R.string.HorusPluginDelete));
            builder.setMessage(LocaleController.formatString(R.string.HorusPluginDeleteAsk, plugin.name));
            builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> ExteraPlugins.delete(plugin));
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            AlertDialog dialog = builder.create();
            showDialog(dialog);
            android.widget.TextView button = (android.widget.TextView) dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_text_RedBold, resourcesProvider));
            }
        }
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.ExteraPlugins);
    }
}
