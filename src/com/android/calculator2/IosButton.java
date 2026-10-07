/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * iOS-style key: a HapticButton that renders as a true circle (square cell
 * enforced in onMeasure so ConstraintLayout row height can't stretch it
 * into an oval) or, with app:iosCircle="false", keeps its measured shape
 * (used by the wide "0" stadium key).
 */
package com.android.calculator2;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

public class IosButton extends HapticButton {
    private final boolean mCircle;

    public IosButton(Context context) {
        this(context, null);
    }

    public IosButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IosButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        boolean circle = true;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.IosButton);
            circle = a.getBoolean(R.styleable.IosButton_iosCircle, true);
            a.recycle();
        }
        mCircle = circle;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mCircle) {
            int size = Math.min(getMeasuredWidth(), getMeasuredHeight());
            if (size > 0) {
                setMeasuredDimension(size, size);
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            setCornerRadius(Math.min(w, h) / 2);
        }
    }
}
