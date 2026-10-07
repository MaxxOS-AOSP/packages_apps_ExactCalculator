/*
 * SPDX-FileCopyrightText: 2016 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2;

import static androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class HistoryFragment extends Fragment {

    public static final String TAG = "HistoryFragment";
    public static final String CLEAR_DIALOG_TAG = "clear";

    private RecyclerView mRecyclerView;
    private HistoryAdapter mAdapter;

    private Evaluator mEvaluator;

    private ArrayList<HistoryItem> mDataSet = new ArrayList<>();

    private boolean mIsDisplayEmpty;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new HistoryAdapter(mDataSet);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(
                R.layout.fragment_history, container, false /* attachToRoot */);

        mRecyclerView = view.findViewById(R.id.history_recycler_view);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == SCROLL_STATE_DRAGGING) {
                    stopActionModeOrContextMenu();
                }
                super.onScrollStateChanged(recyclerView, newState);
            }
        });

        // The size of the RecyclerView is not affected by the adapter's contents.
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mAdapter);

        final View close = view.findViewById(R.id.history_close);
        if (close != null) {
            // Single close path via back press (which plays the slide-down
            // once); no nested animateClose here.
            close.setOnClickListener(v -> getActivity().onBackPressed());
        }
        final View clear = view.findViewById(R.id.history_clear);
        if (clear != null) {
            clear.setOnClickListener(v -> {
                final Calculator calculator = (Calculator) getActivity();
                AlertDialogFragment.showMessageDialog(calculator, "" /* title */,
                        getString(R.string.dialog_clear),
                        getString(R.string.menu_clear_history),
                        CLEAR_DIALOG_TAG);
            });
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Guaranteed bottom slide, driven by the sheet itself. Wait for the
        // first real measurement: View.post() can run pre-layout (height 0
        // -> no visible slide), onPreDraw cannot.
        final View sheet = view.findViewById(R.id.history_sheet);
        if (sheet != null) {
            sheet.getViewTreeObserver().addOnPreDrawListener(
                    new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (sheet.getHeight() == 0) {
                        return true;
                    }
                    sheet.getViewTreeObserver().removeOnPreDrawListener(this);
                    sheet.setTranslationY(sheet.getHeight());
                    sheet.animate()
                            .translationY(0f)
                            .setDuration(320)
                            .setInterpolator(
                                    new android.view.animation.DecelerateInterpolator())
                            .start();
                    return true;
                }
            });
        }

        final Calculator activity = (Calculator) getActivity();
        mEvaluator = Evaluator.getInstance(activity);
        mAdapter.setEvaluator(mEvaluator);

        final boolean isResultLayout = activity.isResultLayout();

        // Snapshot display state here. For the rest of the lifecycle of this current
        // HistoryFragment, this is what we will consider the display state.
        // In rare cases, the display state can change after our adapter is initialized.
        final CalculatorExpr mainExpr = mEvaluator.getExpr(Evaluator.MAIN_INDEX);
        mIsDisplayEmpty = mainExpr == null || mainExpr.isEmpty();

        final long maxIndex = mEvaluator.getMaxIndex();

        final ArrayList<HistoryItem> newDataSet = new ArrayList<>();

        for (long i = 0; i < maxIndex; ++i) {
            newDataSet.add(null);
        }
        final boolean isEmpty = newDataSet.isEmpty();
        if (isEmpty) {
            newDataSet.add(new HistoryItem());
        }
        mDataSet = newDataSet;
        mAdapter.setDataSet(mDataSet);
        mAdapter.setIsResultLayout(isResultLayout);
        mAdapter.setIsOneLine(activity.isOneLine());
        mAdapter.setIsDisplayEmpty(mIsDisplayEmpty);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mEvaluator != null) {
            // Note that the view is destroyed when the fragment backstack is popped, so
            // these are essentially called when the DragLayout is closed.
            mEvaluator.cancelNonMain();
        }
    }

    /** Slide the sheet down, then run [after]. Used for X/back close. */
    public void animateClose(Runnable after) {
        final View root = getView();
        final View sheet = root == null ? null : root.findViewById(R.id.history_sheet);
        if (sheet == null) {
            if (after != null) {
                after.run();
            }
            return;
        }
        sheet.animate()
                .translationY(sheet.getHeight())
                .setDuration(240)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(after)
                .start();
    }

    /** Cancel a pending close slide and snap the sheet back (reopen race). */
    public void cancelClose() {
        final View root = getView();
        if (root == null) {
            return;
        }
        root.animate().cancel();
        root.setVisibility(View.VISIBLE);
        final View sheet = root.findViewById(R.id.history_sheet);
        if (sheet != null) {
            sheet.animate().cancel();
            sheet.setTranslationY(0f);
        }
    }

    public boolean stopActionModeOrContextMenu() {
        if (mRecyclerView == null) {
            return false;
        }
        for (int i = 0; i < mRecyclerView.getChildCount(); i++) {
            final View view = mRecyclerView.getChildAt(i);
            final HistoryAdapter.ViewHolder viewHolder =
                    (HistoryAdapter.ViewHolder) mRecyclerView.getChildViewHolder(view);
            if (viewHolder != null && viewHolder.getResult() != null
                    && viewHolder.getResult().stopActionModeOrContextMenu()) {
                return true;
            }
        }
        return false;
    }
}
