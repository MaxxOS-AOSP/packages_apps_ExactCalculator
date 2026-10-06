/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.calculator2;

import android.app.Activity;
import android.content.Context;

public final class ThemeUtils {
    private static final String PREFS = "calculator_ui";
    private static final String KEY_VINTAGE = "vintage_theme";

    private ThemeUtils() {}

    public static void apply(Activity activity) {
        if (activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_VINTAGE, false)) {
            activity.setTheme(R.style.Theme_Calculator_Vintage);
        } else {
            activity.setTheme(R.style.Theme_Calculator);
        }
    }

    public static boolean isVintage(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_VINTAGE, false);
    }

    public static void setVintage(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_VINTAGE, enabled)
                .apply();
    }
}
