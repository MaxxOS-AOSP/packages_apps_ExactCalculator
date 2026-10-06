/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Liquid-glass bottom navigation bar ported from OriginSU's Compose
 * FloatingBottomBar (manager/.../ui/component/FloatingBottomBar.kt,
 * adapted from compose-miuix-ui's IosLiquidGlassNavigationBar, Apache 2.0).
 *
 * This calculator is View-based (no Compose / miuix-blur runtime), so the
 * miuix Backdrop effects (4dp blur, 24dp lens refraction, vibrancy, gravity
 * rotated BloomStroke highlight) are approximated with a translucent pill,
 * dual specular strokes and a drop shadow. Everything measurable is kept
 * identical to OriginSU:
 *   - floating centered pill, 64dp tall, 4dp inner padding (CircleShape)
 *   - container = colorSurfaceContainer at 40% alpha
 *   - outer specular stroke white ~12%, inner stroke white ~8%
 *   - drop shadow radius 10dp, black 10% (light) / 20% (dark)
 *   - tabs min width 76dp, icon 24dp handled by layout, label 11sp in style
 *   - sliding selection pill 56dp tall = one tab slot wide
 *   - selection pill fill black 10% (light) / white 10% (dark),
 *     selected tab content tinted with colorPrimary (accent)
 *   - critically-damped spring to the new tab (Compose spring(1, 300)):
 *     ~280ms, no overshoot
 *   - press: tab content 1.2x, selection pill 78/56 (~1.39x)
 *   - drag across tabs to select, 4dp rubber-band overscroll w/ ease-out,
 *     TEXT_HANDLE_MOVE haptic on change, full RTL support
 */

package com.android.calculator2;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

public class LiquidGlassBottomBar extends FrameLayout {

    /** Must match the R.id.nav_tabs row in res/layout/bottom_navigation.xml. */
    private static final int BAR_PADDING_DP = 4;
    private static final int PILL_RADIUS_DP = 32;
    private static final int INDICATOR_RADIUS_DP = 28;
    private static final float CONTAINER_ALPHA = 0.4f;
    private static final int SHADOW_RADIUS_DP = 10;
    private static final float TAB_PRESS_SCALE = 1.2f;
    /** OriginSU pressedScale = 78f / 56f. */
    private static final float INDICATOR_PRESS_SCALE = 78f / 56f;
    private static final float RUBBER_BAND_DP = 4f;
    /** Compose spring(dampingRatio = 1, stiffness = 300) settles in ~0.25s. */
    private static final long SPRING_DURATION_MS = 280;
    private static final long PRESS_DURATION_MS = 120;

    /** Critically-damped spring step response: 1 - (1 + k t) e^-k t. */
    private static final Interpolator SPRING = t -> {
        float k = 5.5f;
        return 1f - (1f + k * t) * (float) Math.exp(-k * t);
    };

    private static float easeOutCubic(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float u = 1f - t;
        return 1f - u * u * u;
    }

    public interface OnTabSelectedListener {
        void onTabSelected(int index);
    }

    private final View mIndicator = new View(getContext());
    private final List<View> mTabs = new ArrayList<>();
    private LinearLayout mTabsRow;

    private int mSelectedIndex;
    /** Indicator position in tab-index units; may overshoot while dragging. */
    private float mPosition;
    private float mSlotPitch;
    private ValueAnimator mPositionAnimator;
    private OnTabSelectedListener mListener;

    private float mDownX;
    private boolean mDragging;
    private int mDragTarget = -1;
    private final int mTouchSlop;

    public LiquidGlassBottomBar(Context context) {
        this(context, null);
    }

    public LiquidGlassBottomBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LiquidGlassBottomBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mTouchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
        setClipToPadding(false);
        setClipToOutline(false);
        int padding = dp(BAR_PADDING_DP);
        setPadding(padding, padding, padding, padding);
        // Indicator sits behind the tab row (row is inflated on top of it).
        mIndicator.setVisibility(INVISIBLE);
        addView(mIndicator);
        applyGlassBackground();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTabsRow = findViewById(R.id.nav_tabs);
        mTabs.clear();
        if (mTabsRow != null) {
            for (int i = 0; i < mTabsRow.getChildCount(); i++) {
                final int index = mTabs.size();
                final View tab = mTabsRow.getChildAt(i);
                mTabs.add(tab);
                tab.setOnClickListener(v -> userSelect(index));
                // Press visuals independent of click/drag handling.
                tab.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        pressOn(index);
                    } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                            || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        pressOff();
                    }
                    return false;
                });
            }
        }
        refreshTints();
        snapToSelection();
    }

    /** Tints selected tab with colorPrimary, others with colorOnSurface. */
    private void refreshTints() {
        if (!isInEditMode()) {
            int primary = MaterialColors.getColor(this,
                    androidx.appcompat.R.attr.colorPrimary);
            int onSurface = MaterialColors.getColor(this,
                    com.google.android.material.R.attr.colorOnSurface);
            for (int i = 0; i < mTabs.size(); i++) {
                View tab = mTabs.get(i);
                boolean selected = i == mSelectedIndex;
                tab.setSelected(selected);
                int color = selected ? primary : onSurface;
                if (tab instanceof MaterialButton) {
                    MaterialButton button = (MaterialButton) tab;
                    button.setTextColor(color);
                    button.setIconTint(android.content.res.ColorStateList.valueOf(color));
                }
            }
        }
    }

    private void applyGlassBackground() {
        if (isInEditMode()) {
            return;
        }
        boolean dark = isDark();
        int surfaceContainer = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainer);
        int container = ColorUtils.setAlphaComponent(surfaceContainer,
                Math.round(255 * CONTAINER_ALPHA));

        GradientDrawable outer = new GradientDrawable();
        outer.setShape(GradientDrawable.RECTANGLE);
        outer.setCornerRadius(dp(PILL_RADIUS_DP));
        outer.setColor(container);
        // BloomStroke specular: white ~12% outer line.
        outer.setStroke(dp(1), ColorUtils.setAlphaComponent(Color.WHITE, 31));

        GradientDrawable inner = new GradientDrawable();
        inner.setShape(GradientDrawable.RECTANGLE);
        inner.setCornerRadius(dp(PILL_RADIUS_DP - 2));
        // Second specular line just inside the edge, white ~8%.
        inner.setStroke(dp(1), ColorUtils.setAlphaComponent(Color.WHITE, 20));

        LayerDrawable background = new LayerDrawable(new Drawable[]{outer, inner});
        int insetH = dp(2);
        background.setLayerInset(1, insetH, dp(1), insetH, insetH);
        setBackground(background);

        // Rounded-rect outline drives the drop shadow (background stays unclipped
        // so pressed tabs can scale past the pill edge like OriginSU's layer).
        final float radius = dp(PILL_RADIUS_DP);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setElevation(dp(SHADOW_RADIUS_DP));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // OriginSU dropShadow: black 10% light / 20% dark, radius 10dp.
            int shadow = ColorUtils.setAlphaComponent(Color.BLACK, dark ? 51 : 26);
            setOutlineSpotShadowColor(shadow);
            setOutlineAmbientShadowColor(shadow);
        }

        GradientDrawable indicatorBg = new GradientDrawable();
        indicatorBg.setShape(GradientDrawable.RECTANGLE);
        indicatorBg.setCornerRadius(dp(INDICATOR_RADIUS_DP));
        // OriginSU blurred selection fill: black 10% light / white 10% dark.
        indicatorBg.setColor(dark
                ? ColorUtils.setAlphaComponent(Color.WHITE, 26)
                : ColorUtils.setAlphaComponent(Color.BLACK, 26));
        indicatorBg.setStroke(dp(1), ColorUtils.setAlphaComponent(Color.WHITE, 51));
        mIndicator.setBackground(indicatorBg);
    }

    private boolean isDark() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---- selection API ----------------------------------------------------

    public void setOnTabSelectedListener(@Nullable OnTabSelectedListener listener) {
        mListener = listener;
    }

    public int getSelectedIndex() {
        return mSelectedIndex;
    }

    public int getTabCount() {
        return mTabs.size();
    }

    public void setSelectedIndex(int index, boolean animate) {
        if (mTabs.isEmpty()) {
            return;
        }
        index = Math.max(0, Math.min(mTabs.size() - 1, index));
        mSelectedIndex = index;
        refreshTints();
        if (animate && mSlotPitch > 0) {
            animatePositionTo(index);
        } else {
            snapToSelection();
        }
    }

    private void userSelect(int index) {
        if (index < 0 || index >= mTabs.size()) {
            return;
        }
        if (index != mSelectedIndex) {
            mSelectedIndex = index;
            refreshTints();
            performHaptic();
            if (mListener != null) {
                mListener.onTabSelected(index);
            }
        }
        animatePositionTo(index);
    }

    private void performHaptic() {
        // OriginSU plays TextHandleMove on page change.
        performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
    }

    // ---- indicator motion -------------------------------------------------

    private float slotOf(float position) {
        if (mTabs.isEmpty()) {
            return 0f;
        }
        return isRtl() ? (mTabs.size() - 1 - position) : position;
    }

    private boolean isRtl() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    private void applyPosition() {
        if (mSlotPitch <= 0) {
            return;
        }
        mIndicator.setTranslationX(slotOf(mPosition) * mSlotPitch);
    }

    private void snapToSelection() {
        cancelPositionAnimator();
        mPosition = mSelectedIndex;
        applyPosition();
    }

    private void animatePositionTo(float target) {
        cancelPositionAnimator();
        if (mSlotPitch <= 0) {
            mPosition = target;
            return;
        }
        mPositionAnimator = ValueAnimator.ofFloat(mPosition, target);
        mPositionAnimator.setDuration(SPRING_DURATION_MS);
        mPositionAnimator.setInterpolator(SPRING);
        mPositionAnimator.addUpdateListener(animation -> {
            mPosition = (float) animation.getAnimatedValue();
            applyPosition();
        });
        mPositionAnimator.start();
    }

    private void cancelPositionAnimator() {
        if (mPositionAnimator != null) {
            mPositionAnimator.cancel();
            mPositionAnimator = null;
        }
    }

    private void pressOn(int index) {
        if (index < 0 || index >= mTabs.size()) {
            return;
        }
        mTabs.get(index).animate()
                .scaleX(TAB_PRESS_SCALE).scaleY(TAB_PRESS_SCALE)
                .setDuration(PRESS_DURATION_MS).start();
        mIndicator.animate()
                .scaleX(INDICATOR_PRESS_SCALE).scaleY(INDICATOR_PRESS_SCALE)
                .setDuration(PRESS_DURATION_MS).start();
    }

    private void pressOff() {
        for (View tab : mTabs) {
            tab.animate().scaleX(1f).scaleY(1f)
                    .setDuration(SPRING_DURATION_MS).setInterpolator(SPRING).start();
        }
        mIndicator.animate().scaleX(1f).scaleY(1f)
                .setDuration(SPRING_DURATION_MS).setInterpolator(SPRING).start();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mTabsRow == null || mTabs.isEmpty()) {
            return;
        }
        float pitch = (float) mTabsRow.getWidth() / mTabs.size();
        boolean pitchChanged = Math.abs(pitch - mSlotPitch) > 0.5f;
        mSlotPitch = pitch;
        // Indicator fills one tab slot and tracks the animated position.
        int rowLeft = mTabsRow.getLeft();
        int rowTop = mTabsRow.getTop();
        int rowBottom = mTabsRow.getBottom();
        mIndicator.layout(rowLeft, rowTop,
                Math.round(rowLeft + pitch), rowBottom);
        if (mIndicator.getVisibility() != VISIBLE) {
            mIndicator.setVisibility(VISIBLE);
        }
        if (pitchChanged && (mPositionAnimator == null || !mPositionAnimator.isRunning())) {
            // Rotation/resize: keep the pill under the selected tab.
            snapToSelection();
        } else {
            applyPosition();
        }
    }

    // ---- drag to select ---------------------------------------------------

    private float slotFloatForX(float x) {
        float rowLeft = mTabsRow.getLeft();
        float slot = (x - rowLeft) / mSlotPitch - 0.5f;
        if (isRtl()) {
            slot = (mTabs.size() - 1) - slot;
        }
        return slot;
    }

    private int indexAt(float x) {
        if (mTabs.isEmpty() || mSlotPitch <= 0) {
            return mSelectedIndex;
        }
        return Math.max(0, Math.min(mTabs.size() - 1, Math.round(slotFloatForX(x))));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (mTabsRow == null || mTabs.isEmpty() || mSlotPitch <= 0) {
            return super.onInterceptTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDragging = false;
                mDragTarget = -1;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging && Math.abs(event.getX() - mDownX) > mTouchSlop) {
                    mDragging = true;
                    mDragTarget = mSelectedIndex;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    cancelPositionAnimator();
                    pressOn(mSelectedIndex);
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mDragging) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                float raw = slotFloatForX(event.getX());
                // Clamp to the tab range with a 4dp ease-out rubber band.
                float low = -0.5f;
                float high = mTabs.size() - 0.5f;
                float clamped = Math.max(low, Math.min(high, raw));
                float over = raw - clamped;
                float maxOver = (RUBBER_BAND_DP * getResources().getDisplayMetrics().density)
                        / mSlotPitch;
                float eased = (float) (Math.signum(over) * maxOver
                        * easeOutCubic(Math.abs(over)));
                mPosition = clamped + eased;
                applyPosition();
                int target = Math.max(0,
                        Math.min(mTabs.size() - 1, Math.round(clamped)));
                if (target != mDragTarget) {
                    mDragTarget = target;
                    pressOff();
                    pressOn(target);
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                mDragging = false;
                pressOff();
                int target = indexAt(event.getX());
                if (target != mSelectedIndex) {
                    mSelectedIndex = target;
                    refreshTints();
                    performHaptic();
                    if (mListener != null) {
                        mListener.onTabSelected(target);
                    }
                }
                animatePositionTo(target);
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                mDragging = false;
                pressOff();
                animatePositionTo(mSelectedIndex);
                return true;
            }
            default:
                break;
        }
        return super.onTouchEvent(event);
    }
}
