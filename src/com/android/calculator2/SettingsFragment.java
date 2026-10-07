/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.calculator2;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;

/** In-place settings panel hosted by Calculator (no separate window). */
public class SettingsFragment extends Fragment {
    public static final String TAG = "SettingsFragment";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(
                R.layout.fragment_settings, container, false /* attachToRoot */);

        view.findViewById(R.id.settings_back)
                .setOnClickListener(v -> requireActivity().onBackPressed());

        view.findViewById(R.id.about_card).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AboutActivity.class)));

        final boolean night = UiModes.isNight(requireContext());
        applyIosPalette(view, night);

        final MaterialButtonToggleGroup group = view.findViewById(R.id.theme_group);
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
                CalculatorApplication.saveNightMode(requireContext(),
                        AppCompatDelegate.MODE_NIGHT_YES);
            } else if (checkedId == R.id.theme_light) {
                CalculatorApplication.saveNightMode(requireContext(),
                        AppCompatDelegate.MODE_NIGHT_NO);
            } else {
                CalculatorApplication.saveNightMode(requireContext(),
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
            paintModeButtons(g);
        });
        paintModeButtons(group);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Pager-style enter: slide in from the travel side (right going up
        // the tabs, left going down) like a sideways swipe.
        final Bundle args = getArguments();
        final boolean fromRight = args == null || args.getBoolean("slide_from_right", true);
        view.getViewTreeObserver().addOnPreDrawListener(
                new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (view.getWidth() == 0) {
                    return true;
                }
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                view.setTranslationX(fromRight ? view.getWidth() : -view.getWidth());
                view.animate()
                        .translationX(0f)
                        .setDuration(320)
                        .setInterpolator(
                                new android.view.animation.DecelerateInterpolator())
                        .start();
                return true;
            }
        });
    }

    /** iOS palette (dark keypad look, or its light configuration). */
    private void applyIosPalette(View root, boolean night) {
        final int canvas = night ? 0xFF000000 : 0xFFF2F2F7;
        final int cardBg = night ? 0xFF1C1C1E : 0xFFFFFFFF;
        final int cardEdge = night ? 0xFF2C2C2E : 0xFFD1D1D6;
        final int title = night ? 0xFFFFFFFF : 0xFF000000;
        final int gray = night ? 0xFFAEAEB2 : 0xFF6E6E6E;

        root.setBackgroundColor(canvas);
        ((android.widget.TextView) root.findViewById(R.id.settings_title))
                .setTextColor(title);
        ((android.widget.TextView) root.findViewById(R.id.settings_subtitle))
                .setTextColor(gray);
        ((android.widget.TextView) root.findViewById(R.id.appearance_title))
                .setTextColor(title);
        ((android.widget.TextView) root.findViewById(R.id.about_title))
                .setTextColor(title);
        ((android.widget.TextView) root.findViewById(R.id.about_summary))
                .setTextColor(gray);

        final android.widget.ImageButton back = root.findViewById(R.id.settings_back);
        back.setImageTintList(android.content.res.ColorStateList.valueOf(title));

        final MaterialCardView appearance = root.findViewById(R.id.appearance_card);
        appearance.setCardBackgroundColor(cardBg);
        appearance.setStrokeColor(cardEdge);
        final MaterialCardView about = root.findViewById(R.id.about_card);
        about.setCardBackgroundColor(cardBg);
        about.setStrokeColor(cardEdge);
    }

    /** Orange selected mode button; unselected buttons follow the theme. */
    private void paintModeButtons(MaterialButtonToggleGroup group) {
        final boolean night = UiModes.isNight(requireContext());
        final int off = night ? 0xFF1C1C1E : 0xFFFFFFFF;
        final int offText = night ? 0xFFAEAEB2 : 0xFF3A3A3C;
        final int checkedId = group.getCheckedButtonId();
        for (int i = 0; i < group.getChildCount(); i++) {
            final View child = group.getChildAt(i);
            if (!(child instanceof MaterialButton)) {
                continue;
            }
            final MaterialButton button = (MaterialButton) child;
            final boolean checked = button.getId() == checkedId;
            button.setBackgroundColor(checked ? 0xFFFF9F0A : off);
            button.setTextColor(checked ? 0xFFFFFFFF : offText);
        }
    }
}
