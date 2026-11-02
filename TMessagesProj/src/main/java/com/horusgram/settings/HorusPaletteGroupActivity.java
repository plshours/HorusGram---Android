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

import com.horusgram.HorusPalette;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

// Every color of one group of the palette (incoming messages, the input bar,
// the chat list...), each one picked on its own over what the base colors
// give it. Changed colors are marked, and the picker can put them back.
public class HorusPaletteGroupActivity extends BaseNekoSettingsActivity implements NotificationCenter.NotificationCenterDelegate {

    private static final int KEY_ROW = 1000;

    private final HorusPalette.Group group;

    public HorusPaletteGroupActivity(HorusPalette.Group group) {
        this.group = group;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme && listView != null) {
            listView.adapter.update(false);
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(group.titleRes)));
        for (int i = 0; i < group.keys.length; i++) {
            HorusPalette.Key key = group.keys[i];
            String title = LocaleController.getString(key.titleRes);
            if (HorusPalette.hasKeyColor(key.key)) {
                title = title + " •";
            }
            items.add(HorusCustomizationActivity.ColorCellFactory.of(KEY_ROW + i, title, HorusPalette.getCurrentKeyColor(key.key)));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.HorusGroupInfo)));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        int index = item.id - KEY_ROW;
        if (index < 0 || index >= group.keys.length) {
            return;
        }
        HorusPalette.Key key = group.keys[index];
        HorusColorPicker.show(this, LocaleController.getString(key.titleRes), HorusPalette.getCurrentKeyColor(key.key), true, color -> {
            HorusPalette.setKeyColor(key.key, color);
            HorusPalette.reapply();
        }, HorusPalette.hasKeyColor(key.key) ? () -> {
            HorusPalette.resetKeyColor(key.key);
            HorusPalette.reapply();
        } : null);
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(group.titleRes);
    }
}
