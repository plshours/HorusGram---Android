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

import android.view.View;

import com.horusgram.HorusGhost;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

// Ghost mode and the kept deleted messages, as in AyuGram.
public class HorusGhostActivity extends BaseNekoSettingsActivity {

    private final int ghostRow = rowId++;
    private final int readMessagesRow = rowId++;
    private final int readStoriesRow = rowId++;
    private final int onlineRow = rowId++;
    private final int typingRow = rowId++;
    private final int saveDeletedRow = rowId++;

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.HorusGhostMode)));
        items.add(UItem.asCheck(ghostRow, LocaleController.getString(R.string.HorusGhostEnable)).slug("ghost").setChecked(HorusGhost.isGhost()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.HorusGhostInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.HorusGhostDetails)));
        items.add(UItem.asCheck(readMessagesRow, LocaleController.getString(R.string.HorusGhostReadMessages)).slug("readMessages").setChecked(HorusGhost.sendReadMessages));
        items.add(UItem.asCheck(readStoriesRow, LocaleController.getString(R.string.HorusGhostReadStories)).slug("readStories").setChecked(HorusGhost.sendReadStories));
        items.add(UItem.asCheck(onlineRow, LocaleController.getString(R.string.HorusGhostOnline)).slug("online").setChecked(HorusGhost.sendOnline));
        items.add(UItem.asCheck(typingRow, LocaleController.getString(R.string.HorusGhostTyping)).slug("typing").setChecked(HorusGhost.sendTyping));
        items.add(UItem.asShadow(LocaleController.getString(R.string.HorusGhostDetailsInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.HorusDeletedHeader)));
        items.add(UItem.asCheck(saveDeletedRow, LocaleController.getString(R.string.HorusSaveDeleted)).slug("saveDeleted").setChecked(HorusGhost.saveDeleted));
        items.add(UItem.asShadow(LocaleController.getString(R.string.HorusSaveDeletedInfo)));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == ghostRow) {
            HorusGhost.setGhost(!HorusGhost.isGhost());
        } else if (id == readMessagesRow) {
            HorusGhost.sendReadMessages = !HorusGhost.sendReadMessages;
        } else if (id == readStoriesRow) {
            HorusGhost.sendReadStories = !HorusGhost.sendReadStories;
        } else if (id == onlineRow) {
            HorusGhost.sendOnline = !HorusGhost.sendOnline;
        } else if (id == typingRow) {
            HorusGhost.sendTyping = !HorusGhost.sendTyping;
        } else if (id == saveDeletedRow) {
            HorusGhost.saveDeleted = !HorusGhost.saveDeleted;
        } else {
            return;
        }
        HorusGhost.save();
        listView.adapter.update(true);
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.HorusGhostMode);
    }
}
