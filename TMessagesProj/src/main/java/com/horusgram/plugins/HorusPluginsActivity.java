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

import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

// Installed plugins: each can be turned off, set up and deleted.
public class HorusPluginsActivity extends BaseNekoSettingsActivity {

    private static final int EXAMPLE_ROW = 1;
    // Rows of a plugin are PLUGIN_ROW + index * 10 + part.
    private static final int PLUGIN_ROW = 1000;
    private static final int PART_ENABLED = 0;
    private static final int PART_SETTINGS = 1;
    private static final int PART_DELETE = 2;

    private ArrayList<HorusPlugins.Plugin> plugins = new ArrayList<>();

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        plugins = HorusPlugins.list();

        items.add(UItem.asHeader(LocaleController.getString(R.string.HorusPlugins)));
        items.add(UItem.asButton(EXAMPLE_ROW, R.drawable.msg_add, LocaleController.getString(R.string.HorusPluginExample)).accent());
        items.add(UItem.asShadow(LocaleController.getString(R.string.HorusPluginsInfo)));

        for (int i = 0; i < plugins.size(); i++) {
            HorusPlugins.Plugin plugin = plugins.get(i);
            int base = PLUGIN_ROW + i * 10;
            String title = TextUtils.isEmpty(plugin.version) ? plugin.name : plugin.name + " " + plugin.version;
            items.add(UItem.asHeader(title));
            items.add(UItem.asCheck(base + PART_ENABLED, LocaleController.getString(R.string.HorusPluginEnabled)).setChecked(plugin.enabled));
            if (plugin.enabled && plugin.hasSettings()) {
                items.add(UItem.asButton(base + PART_SETTINGS, R.drawable.msg_settings, LocaleController.getString(R.string.HorusPluginSettings)));
            }
            items.add(UItem.asButton(base + PART_DELETE, R.drawable.msg_delete, LocaleController.getString(R.string.HorusPluginDelete)).red());
            items.add(UItem.asShadow(describe(plugin)));
        }
    }

    private static String describe(HorusPlugins.Plugin plugin) {
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
        if (!plugin.commands.isEmpty()) {
            text.append('\n').append(LocaleController.getString(R.string.HorusPluginCommands));
            for (String[] command : plugin.commands) {
                text.append("\n.").append(command[0]);
                if (!TextUtils.isEmpty(command[1])) {
                    text.append(" — ").append(command[1]);
                }
            }
        }
        if (plugin.error != null) {
            text.append("\n\n").append(LocaleController.formatString(R.string.HorusPluginFailed, plugin.error));
        }
        return text.toString();
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == EXAMPLE_ROW) {
            try {
                HorusPlugins.Plugin plugin = HorusPlugins.installExample();
                Toast.makeText(getParentActivity(), LocaleController.formatString(R.string.HorusPluginInstalled, plugin.name), Toast.LENGTH_SHORT).show();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            listView.adapter.update(true);
            return;
        }
        if (id < PLUGIN_ROW) {
            return;
        }
        int index = (id - PLUGIN_ROW) / 10;
        int part = (id - PLUGIN_ROW) % 10;
        if (index >= plugins.size()) {
            return;
        }
        HorusPlugins.Plugin plugin = plugins.get(index);
        if (part == PART_ENABLED) {
            HorusPlugins.setEnabled(plugin, !plugin.enabled);
            if (plugin.error != null) {
                Toast.makeText(getParentActivity(), LocaleController.formatString(R.string.HorusPluginFailed, plugin.error), Toast.LENGTH_LONG).show();
            }
            listView.adapter.update(true);
        } else if (part == PART_SETTINGS) {
            presentFragment(new HorusPluginSettingsActivity(plugin));
        } else if (part == PART_DELETE) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString(R.string.HorusPluginDelete));
            builder.setMessage(LocaleController.formatString(R.string.HorusPluginDeleteAsk, plugin.name));
            builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                HorusPlugins.delete(plugin);
                listView.adapter.update(true);
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(builder.create());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) {
            listView.adapter.update(false);
        }
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.HorusPlugins);
    }
}
