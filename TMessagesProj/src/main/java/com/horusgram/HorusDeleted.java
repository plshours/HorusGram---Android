/*
 * This file is part of HorusGram for Android, an unofficial Telegram client.
 * Copyright (C) 2026 HorusGram contributors.
 *
 * HorusGram is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 2 of the License, or (at your option)
 * any later version. See the LICENSE file in the root of this repository.
 */
package com.horusgram;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

// The messages others deleted that HorusGram kept, as in AyuGram. The servers
// number messages of private chats and groups in one sequence per account and
// those of a channel in one sequence per channel, so a message is found by
// its id inside a bucket: 0 for the first, minus the channel id for a channel.
public final class HorusDeleted {

    private static final String DATABASE = "horusgram_deleted.db";

    private static Helper helper;
    // Account -> bucket -> ids, read from the database on first use.
    private static final HashMap<Integer, HashMap<Long, HashSet<Integer>>> cache = new HashMap<>();

    private HorusDeleted() {
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context) {
            super(context, DATABASE, null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE deleted (account INTEGER, bucket INTEGER, mid INTEGER, PRIMARY KEY (account, bucket, mid))");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    private static synchronized Helper helper() {
        if (helper == null) {
            helper = new Helper(ApplicationLoader.applicationContext);
        }
        return helper;
    }

    private static synchronized HashMap<Long, HashSet<Integer>> account(int account) {
        HashMap<Long, HashSet<Integer>> result = cache.get(account);
        if (result != null) {
            return result;
        }
        result = new HashMap<>();
        try (Cursor cursor = helper().getReadableDatabase().rawQuery(
                "SELECT bucket, mid FROM deleted WHERE account = ?",
                new String[]{String.valueOf(account)})) {
            while (cursor.moveToNext()) {
                long bucket = cursor.getLong(0);
                HashSet<Integer> ids = result.get(bucket);
                if (ids == null) {
                    ids = new HashSet<>();
                    result.put(bucket, ids);
                }
                ids.add(cursor.getInt(1));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        cache.put(account, result);
        return result;
    }

    public static long bucketOf(TLRPC.Message message) {
        if (message == null || message.peer_id == null || message.peer_id.channel_id == 0) {
            return 0;
        }
        return -message.peer_id.channel_id;
    }

    // Remembers messages the servers said were deleted, and tells the open
    // chats to mark them.
    public static void add(int account, long bucket, ArrayList<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        synchronized (HorusDeleted.class) {
            HashMap<Long, HashSet<Integer>> buckets = account(account);
            HashSet<Integer> set = buckets.get(bucket);
            if (set == null) {
                set = new HashSet<>();
                buckets.put(bucket, set);
            }
            set.addAll(ids);
            try {
                SQLiteDatabase db = helper().getWritableDatabase();
                db.beginTransaction();
                try {
                    ContentValues values = new ContentValues();
                    for (Integer id : ids) {
                        values.clear();
                        values.put("account", account);
                        values.put("bucket", bucket);
                        values.put("mid", id);
                        db.insertWithOnConflict("deleted", null, values, SQLiteDatabase.CONFLICT_IGNORE);
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        final ArrayList<Integer> copy = new ArrayList<>(ids);
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(account)
                .postNotificationName(NotificationCenter.horusMessagesKept, bucket, copy));
    }

    public static boolean isDeleted(int account, MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null || messageObject.getId() <= 0) {
            return false;
        }
        synchronized (HorusDeleted.class) {
            HashSet<Integer> ids = account(account).get(bucketOf(messageObject.messageOwner));
            return ids != null && ids.contains(messageObject.getId());
        }
    }
}
