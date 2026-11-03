/*
 * This file is part of HorusGram for Android, an unofficial Telegram client.
 * Copyright (C) 2026 HorusGram contributors.
 *
 * HorusGram is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 2 of the License, or (at your option)
 * any later version. See the LICENSE file in the root of this repository.
 */
package com.horusgram.music;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;

// A small player with the lyrics of the song, opened when the user wants it
// and moved anywhere over the app by its header. It can fold into one line
// and be closed; the music itself goes on in Telegram's player.
@SuppressLint("ViewConstructor")
public class HorusFloatingPlayer extends FrameLayout {

    private static HorusFloatingPlayer instance;

    private static final int WIDTH = 300;
    private static final int HEIGHT = 400;
    private static final int FOLDED_HEIGHT = 64;
    private static final long TICK = 200;

    private final TextView titleView;
    private final TextView performerView;
    private final TextView playView;
    private final SeekBar seekBar;
    private final LinearLayout body;
    private final ScrollView lyricsScroll;
    private final LinearLayout lyricsList;
    private final TextView statusView;

    private boolean folded;
    private boolean seeking;
    private long shownDocumentId = Long.MIN_VALUE;
    private ArrayList<HorusLyrics.Line> lines = new ArrayList<>();
    private int currentLine = -1;

    private float downRawX, downRawY, downTranslationX, downTranslationY;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            update();
            if (isAttachedToWindow()) {
                AndroidUtilities.runOnUIThread(this, TICK);
            }
        }
    };

    public static void toggle(Activity activity) {
        if (instance != null && instance.getParent() != null) {
            instance.close();
        } else {
            show(activity);
        }
    }

    public static void show(Activity activity) {
        if (activity == null) {
            return;
        }
        if (instance != null && instance.getParent() != null) {
            return;
        }
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        instance = new HorusFloatingPlayer(activity);
        decor.addView(instance, new FrameLayout.LayoutParams(dp(WIDTH), dp(HEIGHT), Gravity.TOP | Gravity.LEFT));
        instance.setTranslationX(Math.max(0, decor.getWidth() - dp(WIDTH + 12)));
        instance.setTranslationY(decor.getHeight() * 0.3f);
    }

    private HorusFloatingPlayer(Context context) {
        super(context);

        int background = Theme.getColor(Theme.key_dialogBackground);
        int text = Theme.getColor(Theme.key_dialogTextBlack);
        int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);

        GradientDrawable card = new GradientDrawable();
        card.setColor(background);
        card.setCornerRadius(dp(18));
        card.setStroke(dp(1), (accent & 0x00FFFFFF) | 0x55000000);
        setBackground(card);
        setElevation(dp(12));
        setClipToOutline(true);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The header moves the window; it also folds and closes it.
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), 0, dp(6), 0);
        column.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(FOLDED_HEIGHT)));

        LinearLayout names = new LinearLayout(context);
        names.setOrientation(LinearLayout.VERTICAL);
        header.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        titleView = new TextView(context);
        titleView.setTextColor(text);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        names.addView(titleView);

        performerView = new TextView(context);
        performerView.setTextColor(gray);
        performerView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        performerView.setSingleLine(true);
        performerView.setEllipsize(TextUtils.TruncateAt.END);
        names.addView(performerView);

        TextView foldView = button(context, "–", text, 22);
        foldView.setOnClickListener(v -> setFolded(!folded));
        header.addView(foldView, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView closeView = button(context, "×", text, 24);
        closeView.setOnClickListener(v -> close());
        header.addView(closeView, new LinearLayout.LayoutParams(dp(40), dp(40)));

        header.setOnTouchListener(this::onHeaderTouch);

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        column.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        body.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView previous = button(context, "⏮", text, 22);
        previous.setOnClickListener(v -> MediaController.getInstance().playPreviousMessage());
        controls.addView(previous, new LinearLayout.LayoutParams(dp(56), dp(48)));

        playView = button(context, "▶", accent, 26);
        playView.setOnClickListener(v -> {
            MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
            if (playing == null) {
                return;
            }
            if (MediaController.getInstance().isMessagePaused()) {
                MediaController.getInstance().playMessage(playing);
            } else {
                MediaController.getInstance().pauseMessage(playing);
            }
            update();
        });
        controls.addView(playView, new LinearLayout.LayoutParams(dp(64), dp(48)));

        TextView next = button(context, "⏭", text, 22);
        next.setOnClickListener(v -> MediaController.getInstance().playNextMessage());
        controls.addView(next, new LinearLayout.LayoutParams(dp(56), dp(48)));

        seekBar = new SeekBar(context);
        seekBar.setMax(1000);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                seeking = false;
                MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
                if (playing != null) {
                    MediaController.getInstance().seekToProgress(playing, bar.getProgress() / 1000f);
                }
            }
        });
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        seekParams.setMargins(dp(4), 0, dp(4), 0);
        body.addView(seekBar, seekParams);

        statusView = new TextView(context);
        statusView.setTextColor(gray);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(16), dp(16), dp(16), dp(16));
        body.addView(statusView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        lyricsScroll = new ScrollView(context);
        lyricsScroll.setVerticalScrollBarEnabled(false);
        lyricsList = new LinearLayout(context);
        lyricsList.setOrientation(LinearLayout.VERTICAL);
        lyricsList.setPadding(dp(16), dp(8), dp(16), dp(120));
        lyricsScroll.addView(lyricsList);
        body.addView(lyricsScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private static TextView button(Context context, String label, int color, int size) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        view.setGravity(Gravity.CENTER);
        view.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        return view;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        AndroidUtilities.runOnUIThread(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        AndroidUtilities.cancelRunOnUIThread(ticker);
    }

    private void close() {
        AndroidUtilities.cancelRunOnUIThread(ticker);
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
        if (instance == this) {
            instance = null;
        }
    }

    private void setFolded(boolean value) {
        folded = value;
        body.setVisibility(folded ? GONE : VISIBLE);
        ViewGroup.LayoutParams params = getLayoutParams();
        params.height = dp(folded ? FOLDED_HEIGHT : HEIGHT);
        setLayoutParams(params);
    }

    private boolean onHeaderTouch(View view, MotionEvent event) {
        View parent = (View) getParent();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downTranslationX = getTranslationX();
                downTranslationY = getTranslationY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (parent == null) {
                    return true;
                }
                float x = downTranslationX + event.getRawX() - downRawX;
                float y = downTranslationY + event.getRawY() - downRawY;
                setTranslationX(Math.max(0, Math.min(x, parent.getWidth() - getWidth())));
                setTranslationY(Math.max(AndroidUtilities.statusBarHeight, Math.min(y, parent.getHeight() - getHeight())));
                return true;
            default:
                return true;
        }
    }

    private void update() {
        MediaController controller = MediaController.getInstance();
        MessageObject playing = controller.getPlayingMessageObject();
        if (playing == null || !playing.isMusic()) {
            titleView.setText(LocaleController.getString(R.string.HorusPlayer));
            performerView.setText("");
            playView.setText("▶");
            seekBar.setProgress(0);
            showStatus(LocaleController.getString(R.string.HorusPlayerNothing));
            shownDocumentId = Long.MIN_VALUE;
            lines = new ArrayList<>();
            lyricsList.removeAllViews();
            return;
        }
        playView.setText(controller.isMessagePaused() ? "▶" : "⏸");
        if (!seeking) {
            seekBar.setProgress((int) (playing.audioProgress * 1000));
        }
        long documentId = playing.getDocument() != null ? playing.getDocument().id : playing.getId();
        if (documentId != shownDocumentId) {
            shownDocumentId = documentId;
            titleView.setText(playing.getMusicTitle());
            performerView.setText(playing.getMusicAuthor());
            lines = new ArrayList<>();
            currentLine = -1;
            lyricsList.removeAllViews();
            showStatus(LocaleController.getString(R.string.HorusLyricsLoading));
            final long requestedId = documentId;
            HorusLyrics.load(documentId, playing.getMusicTitle(), playing.getMusicAuthor(), (int) playing.getDuration(), result -> {
                if (requestedId != shownDocumentId) {
                    return;
                }
                showLines(result);
            });
        }
        highlight((long) (playing.audioProgress * playing.getDuration() * 1000));
    }

    private void showStatus(String text) {
        statusView.setText(text);
        statusView.setVisibility(VISIBLE);
    }

    private void showLines(ArrayList<HorusLyrics.Line> result) {
        lines = result;
        currentLine = -1;
        lyricsList.removeAllViews();
        if (result.isEmpty()) {
            showStatus(LocaleController.getString(R.string.HorusLyricsNotFound));
            return;
        }
        statusView.setVisibility(GONE);
        int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        for (HorusLyrics.Line line : result) {
            TextView view = new TextView(getContext());
            view.setText(line.text.isEmpty() ? "♪" : line.text);
            view.setTextColor(gray);
            view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            view.setPadding(0, dp(5), 0, dp(5));
            lyricsList.addView(view);
        }
    }

    // The line being sung is lit and kept in view; plain lyrics stay as they are.
    private void highlight(long position) {
        if (lines.isEmpty() || lines.get(0).time < 0) {
            return;
        }
        int index = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).time <= position) {
                index = i;
            } else {
                break;
            }
        }
        if (index == currentLine || index >= lyricsList.getChildCount()) {
            return;
        }
        int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        int text = Theme.getColor(Theme.key_dialogTextBlack);
        if (currentLine >= 0 && currentLine < lyricsList.getChildCount()) {
            TextView old = (TextView) lyricsList.getChildAt(currentLine);
            old.setTextColor(gray);
            old.setTypeface(null, Typeface.NORMAL);
        }
        currentLine = index;
        if (index < 0) {
            return;
        }
        TextView now = (TextView) lyricsList.getChildAt(index);
        now.setTextColor(text);
        now.setTypeface(null, Typeface.BOLD);
        int target = now.getTop() - lyricsScroll.getHeight() / 3;
        lyricsScroll.smoothScrollTo(0, Math.max(0, target));
    }
}
