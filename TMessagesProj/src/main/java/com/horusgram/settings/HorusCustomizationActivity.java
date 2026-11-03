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
import android.view.View;

import com.horusgram.HorusConfig;
import com.horusgram.HorusPalette;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.WallpapersListActivity;

import java.util.ArrayList;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

// The HorusGram section of the settings, like the Customization section of
// HorusGram Desktop: the theme, its five base colors, the tails and corners of
// the bubbles, the chat background, and every palette color by group.
public class HorusCustomizationActivity extends BaseNekoSettingsActivity implements NotificationCenter.NotificationCenterDelegate {

    private static final int SLOT_ROW = 1000;
    private static final int GROUP_ROW = 2000;

    private final int themeHorusRow = rowId++;
    private final int themeMacDarkRow = rowId++;
    private final int themeMacLightRow = rowId++;
    private final int inTailRow = rowId++;
    private final int outTailRow = rowId++;
    private final int backgroundRow = rowId++;
    private final int resetRow = rowId++;

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
        boolean horusActive = HorusPalette.isHorusTheme(Theme.getActiveTheme());

        // The three themes HorusGram Desktop has, picked like a radio group.
        String activeAsset = Theme.getActiveTheme() != null ? Theme.getActiveTheme().assetName : null;
        items.add(UItem.asHeader(LocaleController.getString(R.string.HorusTheme)));
        items.add(UItem.asCheck(themeHorusRow, LocaleController.getString(R.string.HorusThemeMain)).slug("themeHorus").setChecked(HorusPalette.ASSET_NAME.equals(activeAsset)));
        items.add(UItem.asCheck(themeMacDarkRow, LocaleController.getString(R.string.HorusThemeMacDark)).slug("themeMacDark").setChecked(HorusPalette.MAC_DARK_ASSET.equals(activeAsset)));
        items.add(UItem.asCheck(themeMacLightRow, LocaleController.getString(R.string.HorusThemeMacLight)).slug("themeMacLight").setChecked(HorusPalette.MAC_LIGHT_ASSET.equals(activeAsset)));
        if (!horusActive) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.HorusApplyThemeInfo)));
            items.add(UItem.asHeader(LocaleController.getString(R.string.HorusBaseColors)));
        } else {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(LocaleController.getString(R.string.HorusBaseColors)));
        }
        for (int i = 0; i < HorusPalette.SLOTS.length; i++) {
            HorusPalette.Slot slot = HorusPalette.SLOTS[i];
            items.add(ColorCellFactory.of(SLOT_ROW + i, LocaleController.getString(slot.titleRes), HorusPalette.getSlotColor(slot)).slug("slot_" + slot.id));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.HorusBaseColorsInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.HorusBubbles)));
        items.add(UItem.asCheck(inTailRow, LocaleController.getString(R.string.HorusInTail)).slug("inTail").setChecked(HorusConfig.inTail));
        items.add(UItem.asCheck(outTailRow, LocaleController.getString(R.string.HorusOutTail)).slug("outTail").setChecked(HorusConfig.outTail));
        items.add(UItem.asIntSlideView(0, 0, SharedConfig.bubbleRadius, 17, value -> String.valueOf(value), this::setBubbleRadius));
        items.add(UItem.asButton(backgroundRow, R.drawable.msg_background, LocaleController.getString(R.string.HorusChatBackground)).slug("background"));
        items.add(UItem.asShadow(LocaleController.getString(R.string.HorusBubblesInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.HorusAllColors)));
        for (int i = 0; i < HorusPalette.GROUPS.length; i++) {
            items.add(UItem.asButton(GROUP_ROW + i, LocaleController.getString(HorusPalette.GROUPS[i].titleRes)));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.HorusAllColorsInfo)));

        items.add(UItem.asButton(resetRow, LocaleController.getString(R.string.HorusResetAll)).red());
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == themeHorusRow || id == themeMacDarkRow || id == themeMacLightRow) {
            String name = id == themeHorusRow
                    ? HorusPalette.THEME_NAME
                    : id == themeMacDarkRow
                    ? HorusPalette.MAC_DARK_NAME
                    : HorusPalette.MAC_LIGHT_NAME;
            Theme.ThemeInfo theme = Theme.getTheme(name);
            if (theme != null && theme != Theme.getActiveTheme()) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, theme, false, null, -1);
            }
        } else if (id >= SLOT_ROW && id < SLOT_ROW + HorusPalette.SLOTS.length) {
            HorusPalette.Slot slot = HorusPalette.SLOTS[id - SLOT_ROW];
            HorusColorPicker.show(this, LocaleController.getString(slot.titleRes), HorusPalette.getSlotColor(slot), false, color -> {
                HorusPalette.setSlotColor(slot, color);
                HorusPalette.reapply();
                listView.adapter.update(true);
            }, HorusPalette.isSlotChanged(slot) ? () -> {
                HorusPalette.resetSlot(slot);
                HorusPalette.reapply();
                listView.adapter.update(true);
            } : null);
        } else if (id >= GROUP_ROW && id < GROUP_ROW + HorusPalette.GROUPS.length) {
            presentFragment(new HorusPaletteGroupActivity(HorusPalette.GROUPS[id - GROUP_ROW]));
        } else if (id == inTailRow || id == outTailRow) {
            boolean out = id == outTailRow;
            HorusConfig.setTail(out, !HorusConfig.hasTail(out));
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(HorusConfig.hasTail(out));
            }
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (id == backgroundRow) {
            presentFragment(new WallpapersListActivity(WallpapersListActivity.TYPE_ALL));
        } else if (id == resetRow) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString(R.string.HorusResetAll));
            builder.setMessage(LocaleController.getString(R.string.HorusResetAllInfo));
            builder.setPositiveButton(LocaleController.getString(R.string.HorusReset), (dialog, which) -> {
                HorusPalette.resetAll();
                HorusPalette.reapply();
                listView.adapter.update(true);
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(builder.create());
        }
    }

    private void setBubbleRadius(int radius) {
        if (radius == SharedConfig.bubbleRadius) {
            return;
        }
        SharedConfig.bubbleRadius = radius;
        MessagesController.getGlobalMainSettings().edit().putInt("bubbleRadius", radius).apply();
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.HorusCustomization);
    }

    // A row with a color swatch on the right.
    static class ColorCellFactory extends UItem.UItemFactory<TextColorCell> {
        static {
            setup(new ColorCellFactory());
        }

        @Override
        public TextColorCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new TextColorCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((TextColorCell) view).setTextAndColor(item.text.toString(), item.intValue, divider);
        }

        public static UItem of(int id, CharSequence title, int color) {
            UItem item = UItem.ofFactory(ColorCellFactory.class);
            item.id = id;
            item.text = title;
            item.intValue = color;
            return item;
        }
    }
}
