/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.calculator2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    private static final String SOURCE_URL =
            "https://github.com/MaxxOS-AOSP/packages_apps_ExactCalculator";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        applyAppColors();

        ImageView back = findViewById(R.id.about_back);
        back.setOnClickListener(v -> finish());

        TextView version = findViewById(R.id.about_version);
        try {
            version.setText(getString(R.string.about_version_format,
                    getPackageManager().getPackageInfo(getPackageName(), 0).versionName));
        } catch (Exception e) {
            version.setText(getString(R.string.about_version_unknown));
        }

        findViewById(R.id.about_source_card).setOnClickListener(v -> openSource());
        findViewById(R.id.about_github_icon).setOnClickListener(v -> openSource());
    }

    /** App colors (black/orange iOS look, or its light configuration). */
    private void applyAppColors() {
        final boolean night = UiModes.isNight(this);
        final int title = night ? 0xFFFFFFFF : 0xFF000000;
        final int gray = night ? 0xFFAEAEB2 : 0xFF6E6E6E;
        final int orange = 0xFFFF9F0A;

        ((ImageView) findViewById(R.id.about_back)).setImageTintList(
                android.content.res.ColorStateList.valueOf(title));

        final com.google.android.material.card.MaterialCardView header =
                findViewById(R.id.about_header_card);
        header.setCardBackgroundColor(night ? 0xFF1C1C1E : 0xFFFFFFFF);
        ((TextView) findViewById(R.id.about_product_name)).setTextColor(title);
        ((TextView) findViewById(R.id.about_tagline)).setTextColor(gray);

        final TextView badge = findViewById(R.id.about_badge);
        final android.graphics.drawable.GradientDrawable badgeBg =
                new android.graphics.drawable.GradientDrawable();
        badgeBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        badgeBg.setColor(orange);
        final float density = getResources().getDisplayMetrics().density;
        badgeBg.setCornerRadius(24 * density);
        badge.setBackground(badgeBg);
        badge.setTextColor(night ? 0xFF000000 : 0xFFFFFFFF);
    }

    private void openSource() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)));
        } catch (Exception ignored) {
            // No browser available. Keep the About page usable.
        }
    }
}
