/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.calculator2;

import android.content.Context;
import android.content.res.Configuration;

/** Night-mode probe shared by the iOS palettes (the app has no vintage theme). */
public final class UiModes {
    private UiModes() {}

    public static boolean isNight(Context context) {
        final int night = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }
}
