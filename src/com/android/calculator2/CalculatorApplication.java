/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

public class CalculatorApplication extends Application {
    private static final String PREFS = "calculator_ui";
    private static final String KEY_NIGHT_MODE = "night_mode";

    @Override
    public void onCreate() {
        super.onCreate();
        // Re-apply the saved theme choice before any activity starts so it
        // sticks across restarts (AppCompat does not persist this itself).
        final int saved = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(saved);
        DynamicColors.applyToActivitiesIfAvailable(this);
    }

    /** Persist the theme choice (called from settings). */
    public static void saveNightMode(android.content.Context context, int mode) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_NIGHT_MODE, mode)
                .apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
