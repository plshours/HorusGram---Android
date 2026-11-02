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
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Locale;

// A small color picker: a preview, the hex code, and sliders for hue,
// saturation, brightness and, where it makes sense, opacity.
public final class HorusColorPicker {

    public interface Callback {
        void onPicked(int color);
    }

    private HorusColorPicker() {
    }

    public static void show(BaseFragment fragment, CharSequence title, int color, boolean withAlpha, Callback onPicked, Runnable onReset) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final int[] current = {withAlpha ? color : (0xFF000000 | color)};
        final float[] hsv = new float[3];
        Color.colorToHSV(current[0], hsv);
        final boolean[] updating = {false};

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);

        FrameLayout previewFrame = new FrameLayout(context);
        GradientDrawable preview = new GradientDrawable();
        preview.setCornerRadius(AndroidUtilities.dp(12));
        preview.setStroke(AndroidUtilities.dp(1), Theme.getColor(Theme.key_divider));
        previewFrame.setBackground(preview);
        layout.addView(previewFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56, 0, 0, 0, 12));

        EditTextBoldCursor hexField = new EditTextBoldCursor(context);
        hexField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        hexField.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        hexField.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        hexField.setHint(withAlpha ? "#RRGGBBAA" : "#RRGGBB");
        hexField.setSingleLine(true);
        hexField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hexField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(withAlpha ? 9 : 7)});
        hexField.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        hexField.setCursorSize(AndroidUtilities.dp(20));
        hexField.setCursorWidth(1.5f);
        hexField.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_text_RedBold));
        hexField.setBackground(null);
        hexField.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        layout.addView(hexField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        SeekBar hue = addSlider(context, layout, R.string.HorusPickerHue, 360);
        SeekBar saturation = addSlider(context, layout, R.string.HorusPickerSaturation, 100);
        SeekBar brightness = addSlider(context, layout, R.string.HorusPickerBrightness, 100);
        SeekBar opacity = withAlpha ? addSlider(context, layout, R.string.HorusPickerOpacity, 255) : null;

        Runnable refresh = () -> {
            updating[0] = true;
            preview.setColor(current[0]);
            hue.setProgress(Math.round(hsv[0]));
            saturation.setProgress(Math.round(hsv[1] * 100));
            brightness.setProgress(Math.round(hsv[2] * 100));
            if (opacity != null) {
                opacity.setProgress(Color.alpha(current[0]));
            }
            String hex = toHex(current[0], withAlpha);
            if (!hex.equalsIgnoreCase(hexField.getText().toString())) {
                hexField.setText(hex);
                hexField.setSelection(hexField.length());
            }
            updating[0] = false;
        };

        SeekBar.OnSeekBarChangeListener sliders = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (updating[0] || !fromUser) {
                    return;
                }
                hsv[0] = hue.getProgress();
                hsv[1] = saturation.getProgress() / 100f;
                hsv[2] = brightness.getProgress() / 100f;
                int alpha = opacity != null ? opacity.getProgress() : 255;
                current[0] = Color.HSVToColor(alpha, hsv);
                refresh.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        hue.setOnSeekBarChangeListener(sliders);
        saturation.setOnSeekBarChangeListener(sliders);
        brightness.setOnSeekBarChangeListener(sliders);
        if (opacity != null) {
            opacity.setOnSeekBarChangeListener(sliders);
        }

        hexField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (updating[0]) {
                    return;
                }
                Integer parsed = parseHex(s.toString(), withAlpha);
                if (parsed == null) {
                    return;
                }
                current[0] = parsed;
                Color.colorToHSV(parsed, hsv);
                updating[0] = true;
                preview.setColor(parsed);
                hue.setProgress(Math.round(hsv[0]));
                saturation.setProgress(Math.round(hsv[1] * 100));
                brightness.setProgress(Math.round(hsv[2] * 100));
                if (opacity != null) {
                    opacity.setProgress(Color.alpha(parsed));
                }
                updating[0] = false;
            }
        });
        refresh.run();

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> onPicked.onPicked(current[0]));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        if (onReset != null) {
            builder.setNeutralButton(LocaleController.getString(R.string.HorusReset), (dialog, which) -> onReset.run());
        }
        fragment.showDialog(builder.create());
    }

    private static SeekBar addSlider(Context context, LinearLayout layout, int titleRes, int max) {
        TextView label = new TextView(context);
        label.setText(LocaleController.getString(titleRes));
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        label.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        label.setGravity(Gravity.START);
        layout.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(max);
        int accent = Theme.getColor(Theme.key_dialogTextBlue);
        seekBar.setProgressTintList(ColorStateList.valueOf(accent));
        seekBar.setThumbTintList(ColorStateList.valueOf(accent));
        layout.addView(seekBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 0, 0, 4));
        return seekBar;
    }

    // "#RRGGBB", or "#RRGGBBAA" when the alpha is asked for, as the desktop
    // client writes colors.
    public static String toHex(int color, boolean withAlpha) {
        String rgb = String.format(Locale.US, "#%06X", color & 0xFFFFFF);
        return withAlpha ? rgb + String.format(Locale.US, "%02X", Color.alpha(color)) : rgb;
    }

    private static Integer parseHex(String text, boolean withAlpha) {
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (!hex.matches("[0-9A-Fa-f]{6}") && !(withAlpha && hex.matches("[0-9A-Fa-f]{8}"))) {
            return null;
        }
        int rgb = Integer.parseInt(hex.substring(0, 6), 16);
        int alpha = hex.length() == 8 ? Integer.parseInt(hex.substring(6, 8), 16) : 0xFF;
        return (alpha << 24) | rgb;
    }
}
