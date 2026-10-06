/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.calculator2;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class ConverterActivity extends AppCompatActivity {
    private android.widget.EditText input;
    private android.widget.TextView result;
    private android.widget.TextView fromUnit;
    private android.widget.TextView toUnit;

    private enum Category { LENGTH, WEIGHT, TEMPERATURE }
    private Category category = Category.LENGTH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_converter);

        input = findViewById(R.id.converter_input);
        result = findViewById(R.id.converter_result);
        fromUnit = findViewById(R.id.converter_from_unit);
        toUnit = findViewById(R.id.converter_to_unit);

        findViewById(R.id.converter_back).setOnClickListener(v -> finish());

        MaterialButton length = findViewById(R.id.category_length);
        MaterialButton weight = findViewById(R.id.category_weight);
        MaterialButton temp = findViewById(R.id.category_temperature);

        length.setOnClickListener(v -> select(Category.LENGTH));
        weight.setOnClickListener(v -> select(Category.WEIGHT));
        temp.setOnClickListener(v -> select(Category.TEMPERATURE));
        length.performClick();

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                calculate();
            }
            @Override public void afterTextChanged(Editable e) {}
        });
    }

    private void select(Category value) {
        category = value;
        switch (value) {
            case LENGTH:
                fromUnit.setText("Kilometer (km)");
                toUnit.setText("Mile (mi)");
                break;
            case WEIGHT:
                fromUnit.setText("Kilogram (kg)");
                toUnit.setText("Pound (lb)");
                break;
            case TEMPERATURE:
                fromUnit.setText("Celsius (°C)");
                toUnit.setText("Fahrenheit (°F)");
                break;
        }
        calculate();
    }

    private void calculate() {
        String text = input.getText().toString().trim();
        if (text.isEmpty() || "-".equals(text) || ".".equals(text)) {
            result.setText("—");
            return;
        }

        try {
            double value = Double.parseDouble(text);
            double converted;
            switch (category) {
                case LENGTH:
                    converted = value * 0.621371192237334;
                    result.setText(String.format(Locale.US, "%.6f mi", converted));
                    break;
                case WEIGHT:
                    converted = value * 2.20462262185;
                    result.setText(String.format(Locale.US, "%.6f lb", converted));
                    break;
                default:
                    converted = (value * 9.0 / 5.0) + 32.0;
                    result.setText(String.format(Locale.US, "%.2f °F", converted));
                    break;
            }
        } catch (NumberFormatException e) {
            result.setText("—");
        }
    }
}
