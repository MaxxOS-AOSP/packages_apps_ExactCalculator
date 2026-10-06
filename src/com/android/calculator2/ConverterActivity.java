/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.calculator2;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** MaxxOS advanced unit converter. Rates for currencies are bundled reference rates. */
public class ConverterActivity extends AppCompatActivity {
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

    @Override protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_converter);

        input = findViewById(R.id.converter_input);
        result = findViewById(R.id.converter_result);
        fromSpinner = findViewById(R.id.converter_from_unit);
        toSpinner = findViewById(R.id.converter_to_unit);
        categoryDescription = findViewById(R.id.converter_category_description);

        findViewById(R.id.converter_back).setOnClickListener(v -> finish());
        setupNavigation();

        bindCategory(R.id.category_length, Category.LENGTH, "Distance, metric and imperial length");
        bindCategory(R.id.category_area, Category.AREA, "Surface area and land measurements");
        bindCategory(R.id.category_volume, Category.VOLUME, "Liquid and cubic volume");
        bindCategory(R.id.category_weight, Category.WEIGHT, "Mass and everyday weight");
        bindCategory(R.id.category_temperature, Category.TEMPERATURE, "Celsius, Fahrenheit and Kelvin");
        bindCategory(R.id.category_speed, Category.SPEED, "Road, nautical and SI speed");
        bindCategory(R.id.category_time, Category.TIME, "Seconds through years");
        bindCategory(R.id.category_pressure, Category.PRESSURE, "Pressure and atmospheric units");
        bindCategory(R.id.category_energy, Category.ENERGY, "Energy, calories and electricity");
        bindCategory(R.id.category_power, Category.POWER, "Watts and horsepower");
        bindCategory(R.id.category_data, Category.DATA, "Digital storage and transfer sizes");
        bindCategory(R.id.category_angle, Category.ANGLE, "Degrees, radians and angular units");
        bindCategory(R.id.category_frequency, Category.FREQUENCY, "Cycles per second");
        bindCategory(R.id.category_force, Category.FORCE, "Newton and force units");
        bindCategory(R.id.category_currency, Category.CURRENCY, "Major currencies using bundled reference rates");

        fromSpinner.setOnItemSelectedListener(new SimpleItemListener() { @Override public void selected() { calculate(); } });
        toSpinner.setOnItemSelectedListener(new SimpleItemListener() { @Override public void selected() { calculate(); } });
        findViewById(R.id.converter_swap).setOnClickListener(v -> swapUnits());

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) { calculate(); }
            @Override public void afterTextChanged(Editable e) {}
        });
        select(Category.LENGTH, "Distance, metric and imperial length");
    }

    private void setupNavigation() {
        LiquidGlassBottomBar navBar = findViewById(R.id.bottom_navigation);
        navBar.setSelectedIndex(2, false);
        navBar.setOnTabSelectedListener(index -> {
            if (index == 0) {
                Intent i = new Intent(this, Calculator.class); i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); startActivity(i); finish();
            } else if (index == 1) {
                Intent i = new Intent(this, Calculator.class); i.putExtra("open_history", true); i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); startActivity(i); finish();
            } else if (index == 3) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        // Returning from Settings slides the pill back under Converter.
        LiquidGlassBottomBar navBar = findViewById(R.id.bottom_navigation);
        if (navBar != null) {
            navBar.setSelectedIndex(2, true);
        }
    }

    private void bindCategory(int id, Category value, String description) {
        findViewById(id).setOnClickListener(v -> select(value, description));
    }

    private void select(Category value, String description) {
        category = value;
        categoryDescription.setText(description);
        Unit[] units = UNITS.get(value);
        ArrayList<String> labels = new ArrayList<>();
        for (Unit u : units) labels.add(u.label);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
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
