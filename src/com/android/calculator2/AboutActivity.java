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
        ThemeUtils.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

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

    private void openSource() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)));
        } catch (Exception ignored) {
            // No browser available. Keep the About page usable.
        }
    }
}
