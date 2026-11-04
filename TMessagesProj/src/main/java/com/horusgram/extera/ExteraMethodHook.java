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

import com.chaquo.python.PyObject;

import org.telegram.messenger.FileLog;

import java.lang.reflect.Member;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

// The Java side of a plugin's MethodHook: Pine calls it around the hooked
// method and it hands the call to the Python handler (base_plugin.py).
public class ExteraMethodHook extends XC_MethodHook {

    private final PyObject handler;

    public ExteraMethodHook(PyObject handler, int priority) {
        super(priority);
        this.handler = handler;
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        try {
            handler.callAttr("_horus_before", param);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    @Override
    protected void afterHookedMethod(MethodHookParam param) {
        try {
            handler.callAttr("_horus_after", param);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // Null when the method cannot be hooked on this device.
    public static XC_MethodHook.Unhook hook(Member method, PyObject handler, int priority) {
        try {
            return XposedBridge.hookMethod(method, new ExteraMethodHook(handler, priority));
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    public static void unhook(XC_MethodHook.Unhook unhook) {
        try {
            if (unhook != null) {
                unhook.unhook();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
