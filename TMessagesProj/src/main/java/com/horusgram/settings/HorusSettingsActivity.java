/*
 * This file is part of HorusGram for Android, an unofficial Telegram client.
 * Copyright (C) 2026 HorusGram contributors.
 *
 * HorusGram is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 2 of the License, or (at your option)
 * any later version. See the LICENSE file in the root of this repository.
 */
package com.horusgram.settings;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;

import com.horusgram.HorusGhost;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.Locale;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoSettingsActivity;

// The HorusGram menu, laid out like the preferences of AyuGram: ghost mode,
// what the client keeps for you, how it looks, and the rest of the settings.
public class HorusSettingsActivity extends BaseNekoSettingsActivity {

    private final int ghostRow = rowId++;
    private final int ghostDetailsRow = rowId++;
    private final int saveDeletedRow = rowId++;
    private final int deletedMarkRow = rowId++;
    private final int appearanceRow = rowId++;
    private final int playerRow = rowId++;
    private final int pluginsRow = rowId++;
    private final int otherRow = rowId++;
    private final int sourceRow = rowId++;

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.HorusGhostMode)));
        items.add(UItem.asCheck(ghostRow, LocaleController.getString(R.string.HorusGhostEnable)).slug("ghost").setChecked(HorusGhost.isGhost()));
        items.add(UItem.asButton(ghostDetailsRow, R.drawable.msg_secret, LocaleController.getString(R.string.HorusGhostDetails)).slug("ghostDetails"));
        items.add(UItem.asShadow(LocaleController.getString(R.string.HorusGhostInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.HorusSpy)));
        items.add(UItem.asCheck(saveDeletedRow, LocaleController.getString(R.string.HorusSaveDeleted)).slug("saveDeleted").setChecked(HorusGhost.saveDeleted));
        items.add(UItem.asButton(deletedMarkRow, LocaleController.getString(R.string.HorusDeletedMarkTitle), HorusGhost.getDeletedMark()).slug("deletedMark"));
        items.add(UItem.asShadow(LocaleController.getString(R.string.HorusSaveDeletedInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.HorusMusic)));
        items.add(UItem.asButton(playerRow, R.drawable.msg_filled_data_music, LocaleController.getString(R.string.HorusPlayer)).slug("player"));
        items.add(UItem.asShadow(LocaleController.getString(R.string.HorusPlayerInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.HorusPlugins)));
        items.add(UItem.asButton(pluginsRow, R.drawable.msg_bots, LocaleController.getString(R.string.HorusPlugins)).slug("plugins"));
        items.add(UItem.asShadow(LocaleController.getString(R.string.HorusPluginsShort)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.HorusPreferences)));
        items.add(UItem.asButton(appearanceRow, R.drawable.msg_theme, LocaleController.getString(R.string.HorusCustomization)).slug("appearance"));
        items.add(UItem.asButton(otherRow, R.drawable.msg_settings, LocaleController.getString(R.string.NekoSettings)).slug("other"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asButton(sourceRow, R.drawable.msg_link, LocaleController.getString(R.string.ViewSourceCode), "GitHub").slug("source"));
        items.add(UItem.asShadow(String.format(Locale.US, "HorusGram %s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == ghostRow) {
            HorusGhost.setGhost(!HorusGhost.isGhost());
            listView.adapter.update(true);
        } else if (id == ghostDetailsRow) {
            presentFragment(new HorusGhostActivity());
        } else if (id == saveDeletedRow) {
            HorusGhost.saveDeleted = !HorusGhost.saveDeleted;
            HorusGhost.save();
            listView.adapter.update(true);
        } else if (id == deletedMarkRow) {
            showDeletedMarkDialog();
        } else if (id == pluginsRow) {
            presentFragment(new com.horusgram.plugins.HorusPluginsActivity());
        } else if (id == playerRow) {
            com.horusgram.music.HorusFloatingPlayer.toggle(getParentActivity());
        } else if (id == appearanceRow) {
            presentFragment(new HorusCustomizationActivity());
        } else if (id == otherRow) {
            presentFragment(new NekoSettingsActivity());
        } else if (id == sourceRow) {
            Browser.openUrl(getParentActivity(), "https://github.com/plshours/HorusGram---Android");
        }
    }

    private void showDeletedMarkDialog() {
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
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setHint(LocaleController.getString(R.string.HorusDeletedMark));
        field.setText(HorusGhost.deletedMark);
        field.setSelection(field.length());

        FrameLayout container = new FrameLayout(context);
        container.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, 0, 24, 6, 24, 0));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.HorusDeletedMarkTitle));
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
            HorusGhost.deletedMark = field.getText().toString().trim();
            HorusGhost.save();
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
        return "HorusGram";
    }
}
