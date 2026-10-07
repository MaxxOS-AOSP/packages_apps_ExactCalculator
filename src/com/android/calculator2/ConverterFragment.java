/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.calculator2;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** MaxxOS advanced unit converter, hosted in-place by Calculator. Rates for currencies are bundled reference rates. */
public class ConverterFragment extends Fragment {
    public static final String TAG = "ConverterFragment";

    private android.widget.EditText input;
    private TextView result;
    private Spinner fromSpinner;
    private Spinner toSpinner;
    private TextView categoryDescription;

    private enum Category {
        LENGTH, AREA, VOLUME, WEIGHT, TEMPERATURE, SPEED, TIME,
        PRESSURE, ENERGY, POWER, DATA, ANGLE, FREQUENCY, FORCE, CURRENCY
    }

    private Category category = Category.LENGTH;
    private boolean updatingSpinners;

    private static final class Unit {
        final String label;
        final double factor;
        Unit(String label, double factor) { this.label = label; this.factor = factor; }
    }

    private static final Map<Category, Unit[]> UNITS = new LinkedHashMap<>();
    static {
        UNITS.put(Category.LENGTH, new Unit[] {
                new Unit("Meter (m)", 1), new Unit("Kilometer (km)", 1000),
                new Unit("Centimeter (cm)", .01), new Unit("Millimeter (mm)", .001),
                new Unit("Mile (mi)", 1609.344), new Unit("Yard (yd)", .9144),
                new Unit("Foot (ft)", .3048), new Unit("Inch (in)", .0254),
                new Unit("Nautical mile (nmi)", 1852)
        });
        UNITS.put(Category.AREA, new Unit[] {
                new Unit("Square meter (m²)", 1), new Unit("Square kilometer (km²)", 1_000_000),
                new Unit("Square centimeter (cm²)", .0001), new Unit("Hectare (ha)", 10_000),
                new Unit("Acre (ac)", 4046.8564224), new Unit("Square mile (mi²)", 2_589_988.110336),
                new Unit("Square foot (ft²)", .09290304), new Unit("Square inch (in²)", .00064516)
        });
        UNITS.put(Category.VOLUME, new Unit[] {
                new Unit("Liter (L)", 1), new Unit("Milliliter (mL)", .001),
                new Unit("Cubic meter (m³)", 1000), new Unit("US gallon (gal)", 3.785411784),
                new Unit("US quart (qt)", .946352946), new Unit("US pint (pt)", .473176473),
                new Unit("Cup (US)", .2365882365), new Unit("Cubic foot (ft³)", 28.316846592)
        });
        UNITS.put(Category.WEIGHT, new Unit[] {
                new Unit("Kilogram (kg)", 1), new Unit("Gram (g)", .001),
                new Unit("Milligram (mg)", .000001), new Unit("Metric ton (t)", 1000),
                new Unit("Pound (lb)", .45359237), new Unit("Ounce (oz)", .028349523125),
                new Unit("Stone (st)", 6.35029318)
        });
        UNITS.put(Category.TEMPERATURE, new Unit[] {
                new Unit("Celsius (°C)", 1), new Unit("Fahrenheit (°F)", 1), new Unit("Kelvin (K)", 1)
        });
        UNITS.put(Category.SPEED, new Unit[] {
                new Unit("Meter/second (m/s)", 1), new Unit("Kilometer/hour (km/h)", 1 / 3.6),
                new Unit("Mile/hour (mph)", .44704), new Unit("Knot (kn)", .5144444444),
                new Unit("Foot/second (ft/s)", .3048)
        });
        UNITS.put(Category.TIME, new Unit[] {
                new Unit("Second (s)", 1), new Unit("Millisecond (ms)", .001),
                new Unit("Minute (min)", 60), new Unit("Hour (h)", 3600),
                new Unit("Day (d)", 86400), new Unit("Week (wk)", 604800), new Unit("Year (365d)", 31536000)
        });
        UNITS.put(Category.PRESSURE, new Unit[] {
                new Unit("Pascal (Pa)", 1), new Unit("Kilopascal (kPa)", 1000),
                new Unit("Bar", 100000), new Unit("Atmosphere (atm)", 101325),
                new Unit("PSI", 6894.757293), new Unit("mmHg", 133.3223874)
        });
        UNITS.put(Category.ENERGY, new Unit[] {
                new Unit("Joule (J)", 1), new Unit("Kilojoule (kJ)", 1000),
                new Unit("Calorie (cal)", 4.184), new Unit("Kilocalorie (kcal)", 4184),
                new Unit("Watt-hour (Wh)", 3600), new Unit("Kilowatt-hour (kWh)", 3_600_000),
                new Unit("BTU", 1055.05585262)
        });
        UNITS.put(Category.POWER, new Unit[] {
                new Unit("Watt (W)", 1), new Unit("Kilowatt (kW)", 1000),
                new Unit("Megawatt (MW)", 1_000_000), new Unit("Horsepower (hp)", 745.6998716)
        });
        UNITS.put(Category.DATA, new Unit[] {
                new Unit("Byte (B)", 1), new Unit("Kilobyte (KB)", 1024),
                new Unit("Megabyte (MB)", 1024L * 1024), new Unit("Gigabyte (GB)", 1024L * 1024 * 1024),
                new Unit("Terabyte (TB)", 1024L * 1024 * 1024 * 1024),
                new Unit("Bit (bit)", .125), new Unit("Megabit (Mb)", 131072)
        });
        UNITS.put(Category.ANGLE, new Unit[] {
                new Unit("Degree (°)", 1), new Unit("Radian (rad)", 180 / Math.PI),
                new Unit("Gradian (gon)", .9), new Unit("Arcminute (′)", 1 / 60.0), new Unit("Arcsecond (″)", 1 / 3600.0)
        });
        UNITS.put(Category.FREQUENCY, new Unit[] {
                new Unit("Hertz (Hz)", 1), new Unit("Kilohertz (kHz)", 1000),
                new Unit("Megahertz (MHz)", 1_000_000), new Unit("Gigahertz (GHz)", 1_000_000_000)
        });
        UNITS.put(Category.FORCE, new Unit[] {
                new Unit("Newton (N)", 1), new Unit("Kilonewton (kN)", 1000),
                new Unit("Dyne (dyn)", .00001), new Unit("Pound-force (lbf)", 4.4482216153)
        });
        // Reference rates: units of each currency per 1 USD. Not a live market feed.
        UNITS.put(Category.CURRENCY, new Unit[] {
                new Unit("US Dollar (USD)", 1), new Unit("Euro (EUR)", .92),
                new Unit("British Pound (GBP)", .78), new Unit("Indian Rupee (INR)", 83.5),
                new Unit("Japanese Yen (JPY)", 149.5), new Unit("Chinese Yuan (CNY)", 7.12),
                new Unit("Australian Dollar (AUD)", 1.52), new Unit("Canadian Dollar (CAD)", 1.36),
                new Unit("Swiss Franc (CHF)", .88), new Unit("Singapore Dollar (SGD)", 1.34),
                new Unit("UAE Dirham (AED)", 3.6725), new Unit("Saudi Riyal (SAR)", 3.75),
                new Unit("South Korean Won (KRW)", 1380), new Unit("Thai Baht (THB)", 34.5)
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(
                R.layout.fragment_converter, container, false /* attachToRoot */);

        input = view.findViewById(R.id.converter_input);
        result = view.findViewById(R.id.converter_result);
        fromSpinner = view.findViewById(R.id.converter_from_unit);
        toSpinner = view.findViewById(R.id.converter_to_unit);
        categoryDescription = view.findViewById(R.id.converter_category_description);

        view.findViewById(R.id.converter_back)
                .setOnClickListener(v -> requireActivity().onBackPressed());

        applyIosPalette(view);

        bindCategory(view, R.id.category_length, Category.LENGTH, "Distance, metric and imperial length");
        bindCategory(view, R.id.category_area, Category.AREA, "Surface area and land measurements");
        bindCategory(view, R.id.category_volume, Category.VOLUME, "Liquid and cubic volume");
        bindCategory(view, R.id.category_weight, Category.WEIGHT, "Mass and everyday weight");
        bindCategory(view, R.id.category_temperature, Category.TEMPERATURE, "Celsius, Fahrenheit and Kelvin");
        bindCategory(view, R.id.category_speed, Category.SPEED, "Road, nautical and SI speed");
        bindCategory(view, R.id.category_time, Category.TIME, "Seconds through years");
        bindCategory(view, R.id.category_pressure, Category.PRESSURE, "Pressure and atmospheric units");
        bindCategory(view, R.id.category_energy, Category.ENERGY, "Energy, calories and electricity");
        bindCategory(view, R.id.category_power, Category.POWER, "Watts and horsepower");
        bindCategory(view, R.id.category_data, Category.DATA, "Digital storage and transfer sizes");
        bindCategory(view, R.id.category_angle, Category.ANGLE, "Degrees, radians and angular units");
        bindCategory(view, R.id.category_frequency, Category.FREQUENCY, "Cycles per second");
        bindCategory(view, R.id.category_force, Category.FORCE, "Newton and force units");
        bindCategory(view, R.id.category_currency, Category.CURRENCY, "Major currencies using bundled reference rates");

        fromSpinner.setOnItemSelectedListener(new SimpleItemListener() { @Override public void selected() { calculate(); } });
        toSpinner.setOnItemSelectedListener(new SimpleItemListener() { @Override public void selected() { calculate(); } });
        view.findViewById(R.id.converter_swap).setOnClickListener(v -> swapUnits());

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) { calculate(); }
            @Override public void afterTextChanged(Editable e) {}
        });
        select(Category.LENGTH, "Distance, metric and imperial length");
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

    private void bindCategory(View root, int id, Category value, String description) {
        root.findViewById(id).setOnClickListener(v -> {
            final MaterialButtonToggleGroup group =
                    root.findViewById(R.id.converter_categories);
            if (group != null) {
                group.check(id);
            }
            select(value, description);
        });
    }

    /** iOS palette (dark keypad look, or its light configuration). */
    private void applyIosPalette(View root) {
        final boolean night = UiModes.isNight(requireContext());
        final int canvas = night ? 0xFF000000 : 0xFFF2F2F7;
        final int cardBg = night ? 0xFF1C1C1E : 0xFFFFFFFF;
        final int cardEdge = night ? 0xFF2C2C2E : 0xFFD1D1D6;
        final int title = night ? 0xFFFFFFFF : 0xFF000000;
        final int gray = night ? 0xFFAEAEB2 : 0xFF6E6E6E;
        final int orange = 0xFFFF9F0A;

        root.findViewById(R.id.converter_root).setBackgroundColor(canvas);
        ((android.widget.TextView) root.findViewById(R.id.converter_title)).setTextColor(title);
        ((android.widget.TextView) root.findViewById(R.id.converter_subtitle)).setTextColor(gray);
        ((android.widget.TextView) root.findViewById(R.id.converter_category_description))
                .setTextColor(gray);
        ((android.widget.TextView) root.findViewById(R.id.converter_from_label)).setTextColor(gray);
        ((android.widget.TextView) root.findViewById(R.id.converter_to_label)).setTextColor(gray);
        input.setTextColor(title);
        input.setHintTextColor(gray);
        result.setTextColor(title);

        final android.widget.ImageButton back =
                root.findViewById(R.id.converter_back);
        back.setImageTintList(android.content.res.ColorStateList.valueOf(title));
        final android.widget.ImageButton more = root.findViewById(R.id.converter_more);
        more.setImageTintList(android.content.res.ColorStateList.valueOf(title));

        final MaterialCardView card = root.findViewById(R.id.converter_card);
        card.setCardBackgroundColor(cardBg);
        card.setStrokeColor(cardEdge);

        final android.content.res.ColorStateList orangeTint =
                android.content.res.ColorStateList.valueOf(orange);
        fromSpinner.setBackgroundTintList(orangeTint);
        toSpinner.setBackgroundTintList(orangeTint);

        final MaterialButton swap = root.findViewById(R.id.converter_swap);
        swap.setBackgroundColor(orange);
        swap.setTextColor(0xFFFFFFFF);

        final MaterialButtonToggleGroup group = root.findViewById(R.id.converter_categories);
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> paintChips(group));
        paintChips(group);
    }

    /** Orange selected chip; unselected chips follow the theme. */
    private void paintChips(MaterialButtonToggleGroup group) {
        final boolean night = UiModes.isNight(requireContext());
        final int chipOff = night ? 0xFF1C1C1E : 0xFFFFFFFF;
        final int chipTextOff = night ? 0xFFAEAEB2 : 0xFF3A3A3C;
        final int checkedId = group.getCheckedButtonId();
        for (int i = 0; i < group.getChildCount(); i++) {
            final android.view.View child = group.getChildAt(i);
            if (!(child instanceof MaterialButton)) {
                continue;
            }
            final MaterialButton chip = (MaterialButton) child;
            final boolean checked = chip.getId() == checkedId;
            chip.setBackgroundColor(checked ? 0xFFFF9F0A : chipOff);
            chip.setTextColor(checked ? 0xFFFFFFFF : chipTextOff);
        }
    }

    private void select(Category value, String description) {
        category = value;
        categoryDescription.setText(description);
        Unit[] units = UNITS.get(value);
        ArrayList<String> labels = new ArrayList<>();
        for (Unit u : units) labels.add(u.label);
        ArrayAdapter<String> adapter;
        if (UiModes.isNight(requireContext())) {
            adapter = new ArrayAdapter<>(
                    requireContext(), R.layout.spinner_item_dark, labels);
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_dark);
        } else {
            adapter = new ArrayAdapter<>(
                    requireContext(), R.layout.spinner_item_light, labels);
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_light);
        }
        updatingSpinners = true;
        fromSpinner.setAdapter(adapter);
        toSpinner.setAdapter(adapter);
        fromSpinner.setSelection(0);
        toSpinner.setSelection(Math.min(1, units.length - 1));
        updatingSpinners = false;
        calculate();
    }

    private void swapUnits() {
        int a = fromSpinner.getSelectedItemPosition();
        int b = toSpinner.getSelectedItemPosition();
        updatingSpinners = true;
        fromSpinner.setSelection(b);
        toSpinner.setSelection(a);
        updatingSpinners = false;
        calculate();
    }

    private void calculate() {
        if (updatingSpinners || fromSpinner == null || toSpinner == null || fromSpinner.getAdapter() == null) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty() || "-".equals(text) || ".".equals(text)) { result.setText("—"); return; }
        try {
            double value = Double.parseDouble(text);
            Unit[] units = UNITS.get(category);
            int from = fromSpinner.getSelectedItemPosition();
            int to = toSpinner.getSelectedItemPosition();
            double converted;
            if (category == Category.TEMPERATURE) {
                double celsius = toCelsius(value, from);
                converted = fromCelsius(celsius, to);
            } else if (category == Category.CURRENCY) {
                double usd = value / units[from].factor;
                converted = usd * units[to].factor;
            } else {
                converted = value * units[from].factor / units[to].factor;
            }
            result.setText(formatNumber(converted) + " " + unitShort(units[to].label));
        } catch (NumberFormatException e) { result.setText("—"); }
    }

    private double toCelsius(double value, int unit) {
        if (unit == 1) return (value - 32) * 5 / 9;
        if (unit == 2) return value - 273.15;
        return value;
    }
    private double fromCelsius(double value, int unit) {
        if (unit == 1) return value * 9 / 5 + 32;
        if (unit == 2) return value + 273.15;
        return value;
    }
    private String formatNumber(double value) {
        if (Math.abs(value) >= 1e12 || (Math.abs(value) > 0 && Math.abs(value) < 1e-6)) return String.format(Locale.US, "%.6e", value);
        return String.format(Locale.US, "%.6f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
    private String unitShort(String label) {
        int p = label.lastIndexOf('(');
        if (p >= 0 && label.endsWith(")")) return label.substring(p + 1, label.length() - 1);
        return label;
    }

    private abstract static class SimpleItemListener implements android.widget.AdapterView.OnItemSelectedListener {
        public abstract void selected();
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { selected(); }
    }
}
