/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Liquid-glass bottom navigation bar ported from OriginSU's Compose
 * FloatingBottomBar (manager/.../ui/component/FloatingBottomBar.kt,
 * adapted from compose-miuix-ui's IosLiquidGlassNavigationBar, Apache 2.0,
 * itself adapted from Kyant0/AndroidLiquidGlass, Apache 2.0).
 *
 * Real in-window backdrop blur for the View system, mirroring OriginSU's
 * Backdrop pipeline (vibrancy -> blur -> lens -> container tint):
 *   - the bar snapshots the live content behind it into a downscaled
 *     bitmap on every tree pre-draw, and only invalidates itself when the
 *     snapshot actually changed (pixel compare), so a static screen costs
 *     nothing and animations (e.g. MotionLayout transitions, ripples)
 *     refract live through the pill;
 *   - the snapshot is saturated 1.5x (OriginSU vibrancy), blurred ~4dp
 *     through a hardware RenderNode (OriginSU blur(4dp)), and on API 33+
 *     passed through the same rounded-rect refraction shader OriginSU's
 *     lens() uses (24dp height / 24dp amount);
 *   - the processed backdrop is drawn clipped to the pill, then tinted
 *     with colorSurfaceContainer at 40% alpha, exactly like OriginSU's
 *     onDrawSurface(containerColor).
 * Everything else measurable is kept identical to OriginSU:
 *   - floating centered pill, 64dp tall, 4dp inner padding (CircleShape)
 *   - outer specular stroke white ~12%, inner stroke white ~8%
 *   - drop shadow radius 10dp, black 10% (light) / 20% (dark)
 *   - tabs min width 76dp, icon 24dp, label 11sp (see BottomNavItem style)
 *   - sliding selection pill 56dp tall = one tab slot wide, fill black 10%
 *     (light) / white 10% (dark); selected tab content in colorPrimary
 *   - critically-damped spring to the new tab (Compose spring(1, 300))
 *   - press: tab content 1.2x, selection pill 78/56 (~1.39x)
 *   - drag across tabs to select, 4dp rubber-band overscroll w/ ease-out,
 *     TEXT_HANDLE_MOVE haptic on change, full RTL support
 */

package com.android.calculator2;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
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

    // ---- live backdrop pipeline (OriginSU Backdrop equivalent) ------------
    /** Sampling margin around the pill, mirrors miuix backdrop padding. */
    private static final int BACKDROP_MARGIN_DP = 40;
    /** Snapshot downscale; blur radius is derived from it (see below). */
    private static final int BACKDROP_DOWNSCALE = 6;
    /** OriginSU vibrancy(): saturation 1.5x. */
    private static final float BACKDROP_SATURATION = 1.5f;
    /** OriginSU blur(4dp): radius in snapshot px so the on-screen effect is ~4dp. */
    private static final int BACKDROP_BLUR_DP = 4;
    /** OriginSU lens(): refraction height / amount. */
    private static final int LENS_HEIGHT_DP = 24;
    private static final int LENS_AMOUNT_DP = 24;

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

    // ---- live backdrop state ------------------------------------------------
    private View mContentRoot;
    private Bitmap mBackdrop;
    private Bitmap mScratch;
    private final Canvas mScratchCanvas = new Canvas();
    private RenderNode mBlurNode;
    private RenderEffect mBlurEffect;
    @Nullable
    private RenderNode mLensNode;
    @Nullable
    private RuntimeShader mLensShader;
    private final Paint mSaturatePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint mTintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOuterStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mInnerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPillPath = new Path();
    private final RectF mPillRect = new RectF();
    private final Rect mCaptureRect = new Rect();
    private boolean mCapturing;
    private boolean mBackdropReady;
    private boolean mGlassSupported = true;
    private final ViewTreeObserver.OnPreDrawListener mPreDrawListener =
            new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    // Refresh the snapshot; invalidate only when the pixels
                    // behind the bar actually changed, so a static screen idles
                    // instead of re-rendering every frame.
                    if (!mCapturing && isShown() && refreshBackdrop()) {
                        invalidate();
                    }
                    return true;
                }
            };

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
        setWillNotDraw(false);
        int padding = dp(BAR_PADDING_DP);
        setPadding(padding, padding, padding, padding);
        // Indicator sits behind the tab row (row is inflated on top of it).
        mIndicator.setVisibility(INVISIBLE);
        addView(mIndicator);
        ensureGlassPaints();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mContentRoot = getParent() instanceof View ? (View) getParent() : null;
        getViewTreeObserver().addOnPreDrawListener(mPreDrawListener);
        mBackdropReady = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(mPreDrawListener);
        super.onDetachedFromWindow();
        releaseBackdrop();
        mContentRoot = null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mBackdropReady = false;
    }

    private void releaseBackdrop() {
        mBlurNode = null;
        mBlurEffect = null;
        mLensNode = null;
        mLensShader = null;
        if (mBackdrop != null) {
            mBackdrop.recycle();
            mBackdrop = null;
        }
        if (mScratch != null) {
            mScratch.recycle();
            mScratch = null;
        }
        mBackdropReady = false;
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

    /** Static paints: container tint + specular strokes + shadow + indicator. */
    private void ensureGlassPaints() {
        if (isInEditMode()) {
            mTintPaint.setColor(0x66999999);
        } else {
            int surfaceContainer = MaterialColors.getColor(this,
                    com.google.android.material.R.attr.colorSurfaceContainer);
            mTintPaint.setColor(ColorUtils.setAlphaComponent(surfaceContainer,
                    Math.round(255 * CONTAINER_ALPHA)));
        }
        // BloomStroke specular: white ~12% outer line, ~8% inner line.
        mOuterStrokePaint.setStyle(Paint.Style.STROKE);
        mOuterStrokePaint.setStrokeWidth(dp(1));
        mOuterStrokePaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, 31));
        mInnerStrokePaint.setStyle(Paint.Style.STROKE);
        mInnerStrokePaint.setStrokeWidth(dp(1));
        mInnerStrokePaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, 20));

        ColorMatrix saturate = new ColorMatrix();
        saturate.setSaturation(BACKDROP_SATURATION);
        mSaturatePaint.setColorFilter(new ColorMatrixColorFilter(saturate));

        if (!isInEditMode()) {
            // Rounded-rect outline drives the drop shadow; the pill itself is
            // drawn unclipped in dispatchDraw so pressed tabs can scale past
            // the edge like OriginSU's layer.
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
                int shadow = ColorUtils.setAlphaComponent(Color.BLACK, isDark() ? 51 : 26);
                setOutlineSpotShadowColor(shadow);
                setOutlineAmbientShadowColor(shadow);
            }

            android.graphics.drawable.GradientDrawable indicatorBg =
                    new android.graphics.drawable.GradientDrawable();
            indicatorBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            indicatorBg.setCornerRadius(dp(INDICATOR_RADIUS_DP));
            // OriginSU blurred selection fill: black 10% light / white 10% dark.
            indicatorBg.setColor(isDark()
                    ? ColorUtils.setAlphaComponent(Color.WHITE, 26)
                    : ColorUtils.setAlphaComponent(Color.BLACK, 26));
            indicatorBg.setStroke(dp(1), ColorUtils.setAlphaComponent(Color.WHITE, 51));
            mIndicator.setBackground(indicatorBg);
        }
    }

    private boolean isDark() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dpF(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    // ---- live backdrop pipeline -------------------------------------------

    /**
     * Snapshots the live content behind the bar into the downscaled scratch
     * bitmap. Returns true when the pixels changed and the bar must redraw.
     * Runs on the tree pre-draw so animations refract through the pill.
     */
    private boolean refreshBackdrop() {
        if (!mGlassSupported || mCapturing || mContentRoot == null
                || getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }
        try {
            // Bar bounds in content-root coordinates, outset by the sampling margin.
            mCaptureRect.set(0, 0, getWidth(), getHeight());
            if (mContentRoot instanceof ViewGroup) {
                ((ViewGroup) mContentRoot).offsetDescendantRectToMyCoords(this, mCaptureRect);
            } else {
                mCaptureRect.offset(getLeft(), getTop());
            }
            int margin = dp(BACKDROP_MARGIN_DP);
            mCaptureRect.inset(-margin, -margin);

            int bw = Math.max(1, mCaptureRect.width() / BACKDROP_DOWNSCALE);
            int bh = Math.max(1, mCaptureRect.height() / BACKDROP_DOWNSCALE);
            if (mBackdrop == null || mBackdrop.getWidth() != bw || mBackdrop.getHeight() != bh) {
                releaseBitmaps();
                mBackdrop = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                mScratch = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                mBackdropReady = false;
            }

            mScratchCanvas.setBitmap(mScratch);
            mScratchCanvas.save();
            float scale = 1f / BACKDROP_DOWNSCALE;
            mScratchCanvas.scale(scale, scale);
            mScratchCanvas.translate(-mCaptureRect.left, -mCaptureRect.top);
            // The bar skips its own draw while capturing (see dispatchDraw),
            // so the snapshot holds only the content behind the pill.
            mCapturing = true;
            try {
                mContentRoot.draw(mScratchCanvas);
            } finally {
                mCapturing = false;
            }
            mScratchCanvas.restore();
            mScratchCanvas.setBitmap(null);

            if (!mBackdropReady || !mScratch.sameAs(mBackdrop)) {
                Bitmap swap = mBackdrop;
                mBackdrop = mScratch;
                mScratch = swap;
                mBackdropReady = true;
                return true;
            }
            return false;
        } catch (Throwable t) {
            // Any snapshot failure (e.g. exotic OEM canvas) falls back to the
            // static glass tint instead of breaking the navigation bar.
            mGlassSupported = false;
            releaseBitmaps();
            return true;
        }
    }

    private void releaseBitmaps() {
        if (mBackdrop != null) {
            mBackdrop.recycle();
            mBackdrop = null;
        }
        if (mScratch != null) {
            mScratch.recycle();
            mScratch = null;
        }
    }

    /** Draws backdrop texture -> container tint -> inner stroke, clipped to the pill. */
    private void drawGlassBackground(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float radius = dpF(PILL_RADIUS_DP);
        mPillRect.set(0, 0, w, h);
        mPillPath.rewind();
        mPillPath.addRoundRect(mPillRect, radius, radius, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(mPillPath);
        try {
            if (mGlassSupported && mBackdropReady && mBackdrop != null
                    && canvas.isHardwareAccelerated()) {
                drawLiveBackdrop(canvas);
            } else if (mGlassSupported && mBackdropReady && mBackdrop != null) {
                // Software canvas: draw the saturated snapshot directly.
                RectF dest = new RectF(
                        -(mCaptureRect.left - barLeftInRoot()),
                        -(mCaptureRect.top - barTopInRoot()),
                        (mCaptureRect.left - barLeftInRoot()) + (float) mCaptureRect.width(),
                        (mCaptureRect.top - barTopInRoot()) + (float) mCaptureRect.height());
                canvas.drawBitmap(mBackdrop, null, dest, mSaturatePaint);
            }
            // OriginSU onDrawSurface: container tint over the processed backdrop.
            canvas.drawRect(mPillRect, mTintPaint);
        } finally {
            canvas.restore();
        }

        // Inner specular line just inside the pill edge.
        float inset = dpF(2);
        mPillPath.rewind();
        mPillPath.addRoundRect(new RectF(inset, dpF(1), w - inset, h - inset),
                radius - inset, radius - inset, Path.Direction.CW);
        canvas.drawPath(mPillPath, mInnerStrokePaint);
    }

    private int barLeftInRoot() {
        return mCaptureRectLeftOfBar(true);
    }

    private int barTopInRoot() {
        return mCaptureRectLeftOfBar(false);
    }

    /** Bar origin in content-root coordinates (capture rect minus margin). */
    private int mCaptureRectLeftOfBar(boolean horizontal) {
        Rect bar = new Rect(0, 0, getWidth(), getHeight());
        if (mContentRoot instanceof ViewGroup) {
            ((ViewGroup) mContentRoot).offsetDescendantRectToMyCoords(this, bar);
        } else {
            bar.offset(getLeft(), getTop());
        }
        return horizontal ? bar.left : bar.top;
    }

    /**
     * Blur (+ lens on API 33+) the snapshot through hardware RenderNodes and
     * paint the result over the pill, compensating for the sampling margin.
     */
    private void drawLiveBackdrop(Canvas canvas) {
        int bw = mBackdrop.getWidth();
        int bh = mBackdrop.getHeight();
        float destLeft = -(mCaptureRect.left - barLeftInRoot());
        float destTop = -(mCaptureRect.top - barTopInRoot());
        float scaleX = (float) mCaptureRect.width() / bw;
        float scaleY = (float) mCaptureRect.height() / bh;

        ensureBlurNode(bw, bh);
        android.graphics.RecordingCanvas recording = mBlurNode.beginRecording(bw, bh);
        try {
            recording.drawBitmap(mBackdrop, 0, 0, mSaturatePaint);
        } finally {
            mBlurNode.endRecording();
        }

        RenderNode source = mBlurNode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ensureLensNode(bw, bh)) {
            android.graphics.RecordingCanvas lensRecording = mLensNode.beginRecording(bw, bh);
            try {
                lensRecording.drawRenderNode(mBlurNode);
            } finally {
                mLensNode.endRecording();
            }
            source = mLensNode;
        }

        canvas.save();
        canvas.translate(destLeft, destTop);
        canvas.scale(scaleX, scaleY);
        try {
            canvas.drawRenderNode(source);
        } finally {
            canvas.restore();
        }
    }

    private void ensureBlurNode(int bw, int bh) {
        if (mBlurNode == null) {
            mBlurNode = new RenderNode("LiquidGlassBlur");
            float radius = Math.max(2f, dpF(BACKDROP_BLUR_DP) / BACKDROP_DOWNSCALE);
            mBlurEffect = RenderEffect.createBlurEffect(
                    radius, radius, Shader.TileMode.CLAMP);
            mBlurNode.setRenderEffect(mBlurEffect);
        }
        mBlurNode.setPosition(0, 0, bw, bh);
    }

    /** API 33+: rounded-rect refraction over the blurred snapshot. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private boolean ensureLensNode(int bw, int bh) {
        try {
            if (mLensNode == null) {
                mLensShader = new RuntimeShader(LENS_SHADER);
                mLensNode = new RenderNode("LiquidGlassLens");
                mLensNode.setRenderEffect(
                        RenderEffect.createRuntimeShaderEffect(mLensShader, "content"));
            }
            mLensNode.setPosition(0, 0, bw, bh);
            float sf = BACKDROP_DOWNSCALE;
            float margin = dpF(BACKDROP_MARGIN_DP) / sf;
            float corner = dpF(PILL_RADIUS_DP) / sf;
            mLensShader.setFloatUniform("size", (float) bw, (float) bh);
            mLensShader.setFloatUniform("offset", -margin, -margin);
            mLensShader.setFloatUniform("cornerRadii", corner, corner, corner, corner);
            mLensShader.setFloatUniform("refractionHeight", dpF(LENS_HEIGHT_DP) / sf);
            mLensShader.setFloatUniform("refractionAmount", -dpF(LENS_AMOUNT_DP) / sf);
            mLensShader.setFloatUniform("depthEffect", 0f);
            return true;
        } catch (Throwable t) {
            mLensNode = null;
            mLensShader = null;
            return false;
        }
    }

    /**
     * OriginSU lens() ROUNDED_RECT_REFRACTION_SHADER, ported to AGSL
     * (Kyant0/AndroidLiquidGlass, Apache 2.0).
     */
    private static final String LENS_SHADER =
            "uniform shader content;\n"
            + "uniform float2 size;\n"
            + "uniform float2 offset;\n"
            + "uniform float4 cornerRadii;\n"
            + "uniform float refractionHeight;\n"
            + "uniform float refractionAmount;\n"
            + "uniform float depthEffect;\n"
            + "float radiusAt(float2 coord, float4 radii) {\n"
            + "    if (coord.x >= 0.0) {\n"
            + "        if (coord.y <= 0.0) return radii.y;\n"
            + "        else return radii.z;\n"
            + "    } else {\n"
            + "        if (coord.y <= 0.0) return radii.x;\n"
            + "        else return radii.w;\n"
            + "    }\n"
            + "}\n"
            + "float sdRoundedRect(float2 coord, float2 halfSize, float radius) {\n"
            + "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n"
            + "    float outside = length(max(cornerCoord, 0.0)) - radius;\n"
            + "    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);\n"
            + "    return outside + inside;\n"
            + "}\n"
            + "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n"
            + "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n"
            + "    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n"
            + "        return sign(coord) * normalize(max(cornerCoord, 0.0));\n"
            + "    } else {\n"
            + "        float gradX = step(cornerCoord.y, cornerCoord.x);\n"
            + "        return sign(coord) * float2(gradX, 1.0 - gradX);\n"
            + "    }\n"
            + "}\n"
            + "float circleMap(float x) {\n"
            + "    return 1.0 - sqrt(1.0 - x * x);\n"
            + "}\n"
            + "half4 main(float2 coord) {\n"
            + "    float2 halfSize = size * 0.5;\n"
            + "    float2 centeredCoord = (coord + offset) - halfSize;\n"
            + "    float radius = radiusAt(coord, cornerRadii);\n"
            + "    float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n"
            + "    if (-sd >= refractionHeight) {\n"
            + "        return content.eval(coord);\n"
            + "    }\n"
            + "    sd = min(sd, 0.0);\n"
            + "    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;\n"
            + "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n"
            + "    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius)"
            + "        + depthEffect * normalize(centeredCoord));\n"
            + "    float2 refractedCoord = coord + d * grad;\n"
            + "    return content.eval(refractedCoord);\n"
            + "}\n";

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Excluded from our own snapshot (see refreshBackdrop).
        if (mCapturing) {
            return;
        }
        if (isInEditMode()) {
            canvas.drawRoundRect(mPillRectFor(canvas),
                    dpF(PILL_RADIUS_DP), dpF(PILL_RADIUS_DP), mTintPaint);
            super.dispatchDraw(canvas);
            return;
        }
        drawGlassBackground(canvas);
        super.dispatchDraw(canvas);
        // Outer specular line on top, like OriginSU's BloomStroke highlight.
        canvas.drawRoundRect(mPillRectFor(canvas),
                dpF(PILL_RADIUS_DP), dpF(PILL_RADIUS_DP), mOuterStrokePaint);
    }

    private RectF mPillRectFor(Canvas canvas) {
        mPillRect.set(0, 0, getWidth(), getHeight());
        return mPillRect;
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
