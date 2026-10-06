/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.calculator2;

import android.content.Context;
import android.os.Bundle;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.about_card).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        MaterialButtonToggleGroup group = findViewById(R.id.theme_group);
        MaterialButton system = findViewById(R.id.theme_system);
        MaterialButton light = findViewById(R.id.theme_light);
        MaterialButton dark = findViewById(R.id.theme_dark);
        com.google.android.material.materialswitch.MaterialSwitch vintage =
                findViewById(R.id.vintage_switch);
        vintage.setChecked(ThemeUtils.isVintage(this));

        vintage.setOnCheckedChangeListener((button, checked) -> {
            ThemeUtils.setVintage(this, checked);
            setResult(RESULT_OK);
            recreate();
        });

        int current = AppCompatDelegate.getDefaultNightMode();
        if (current == AppCompatDelegate.MODE_NIGHT_YES) {
            group.check(R.id.theme_dark);
        } else if (current == AppCompatDelegate.MODE_NIGHT_NO) {
            group.check(R.id.theme_light);
        } else {
            group.check(R.id.theme_system);
        }

        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.theme_dark) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else if (checkedId == R.id.theme_light) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
        });
    }
}
