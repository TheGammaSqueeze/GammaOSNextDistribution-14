/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.qc.view;

import static com.android.car.qc.QCItem.QC_ACTION_SLIDER_VALUE;
import static com.android.car.qc.QCItem.QC_ACTION_TOGGLE_STATE;
import static com.android.car.qc.QCItem.QC_TYPE_ACTION_SWITCH;
import static com.android.car.qc.view.QCView.QCActionListener;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.qc.QCActionItem;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCRow;
import com.android.car.qc.QCSlider;
import com.android.car.qc.R;
import com.android.car.ui.utils.CarUiUtils;
import com.android.car.ui.utils.DirectManipulationHelper;
import com.android.car.ui.uxr.DrawableStateToggleButton;

/**
 * Quick Controls view for {@link QCRow} instances.
 */
public class QCRowView extends FrameLayout {
    private static final String TAG = "QCRowView";

    private LayoutInflater mLayoutInflater;
    private BidiFormatter mBidiFormatter;
    private View mContentView;
    private TextView mTitle;
    private TextView mSubtitle;
    private ImageView mStartIcon;
    @ColorInt
    private int mStartIconTint;
    private LinearLayout mStartItemsContainer;
    private LinearLayout mEndItemsContainer;
    private LinearLayout mSeekBarContainer;
    @Nullable
    private QCSlider mQCSlider;
    private QCSeekBarView mSeekBar;
    private QCActionListener mActionListener;
    private boolean mInDirectManipulationMode;

    private QCSeekbarChangeListener mSeekbarChangeListener;
    private final View.OnKeyListener mSeekBarKeyListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (mSeekBar == null || (!mSeekBar.isEnabled()
                    && !mSeekBar.isClickableWhileDisabled())) {
                return false;
            }
            // Consume nudge events in direct manipulation mode.
            if (mInDirectManipulationMode
                    && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
                return true;
            }

            // Handle events to enter or exit direct manipulation mode.
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (mQCSlider != null) {
                        if (mQCSlider.isEnabled()) {
                            setInDirectManipulationMode(v, mSeekBar, !mInDirectManipulationMode);
                        } else {
                            fireAction(mQCSlider, new Intent());
                        }
                    }
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (mInDirectManipulationMode) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        setInDirectManipulationMode(v, mSeekBar, false);
                    }
                    return true;
                }
            }

            // Don't propagate confirm keys to the SeekBar to prevent a ripple effect on the thumb.
            if (KeyEvent.isConfirmKey(keyCode)) {
                return false;
            }

            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return mSeekBar.onKeyDown(keyCode, event);
            } else {
                return mSeekBar.onKeyUp(keyCode, event);
            }
        }
    };

    private final View.OnFocusChangeListener mSeekBarFocusChangeListener =
            (v, hasFocus) -> {
                if (!hasFocus && mInDirectManipulationMode && mSeekBar != null) {
                    setInDirectManipulationMode(v, mSeekBar, false);
                }
            };

    private final View.OnGenericMotionListener mSeekBarScrollListener =
            (v, event) -> {
                if (!mInDirectManipulationMode || mSeekBar == null) {
                    return false;
                }
                int adjustment = Math.round(event.getAxisValue(MotionEvent.AXIS_SCROLL));
                if (adjustment == 0) {
                    return false;
                }
                int count = Math.abs(adjustment);
                int keyCode =
                        adjustment < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
                KeyEvent downEvent = new KeyEvent(event.getDownTime(), event.getEventTime(),
                        KeyEvent.ACTION_DOWN, keyCode, /* repeat= */ 0);
                KeyEvent upEvent = new KeyEvent(event.getDownTime(), event.getEventTime(),
                        KeyEvent.ACTION_UP, keyCode, /* repeat= */ 0);
                for (int i = 0; i < count; i++) {
                    mSeekBar.onKeyDown(keyCode, downEvent);
                    mSeekBar.onKeyUp(keyCode, upEvent);
                }
                return true;
            };

    QCRowView(Context context) {
        super(context);
        init(context);
    }

    QCRowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    QCRowView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    QCRowView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        mLayoutInflater = LayoutInflater.from(context);
        mBidiFormatter = BidiFormatter.getInstance();
        mLayoutInflater.inflate(R.layout.qc_row_view, /* root= */ this);
        mContentView = findViewById(R.id.qc_row_content);
        mTitle = findViewById(R.id.qc_title);
        mSubtitle = findViewById(R.id.qc_summary);
        mStartIcon = findViewById(R.id.qc_icon);
        mStartItemsContainer = findViewById(R.id.qc_row_start_items);
        mEndItemsContainer = findViewById(R.id.qc_row_end_items);
        mSeekBarContainer = findViewById(R.id.qc_seekbar_wrapper);
        mSeekBar = findViewById(R.id.qc_seekbar);
    }

    void setActionListener(QCActionListener listener) {
        mActionListener = listener;
    }

    void setRow(QCRow row) {
        if (row == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        CarUiUtils.makeAllViewsEnabled(mContentView, row.isEnabled());
        if (!row.isEnabled()) {
            if (row.isClickableWhileDisabled() && (row.getDisabledClickAction() != null
                    || row.getDisabledClickActionHandler() != null)) {
                mContentView.setOnClickListener(v -> {
                    fireAction(row, /* intent= */ null);
                });
            }
        } else if (row.getPrimaryAction() != null || row.getActionHandler() != null) {
            mContentView.setOnClickListener(v -> {
                fireAction(row, /* intent= */ null);
            });
        }
        if (!TextUtils.isEmpty(row.getTitle())) {
            mTitle.setVisibility(VISIBLE);
            mTitle.setText(
                    mBidiFormatter.unicodeWrap(row.getTitle(), TextDirectionHeuristics.LOCALE));
        } else {
            mTitle.setVisibility(GONE);
        }
        if (!TextUtils.isEmpty(row.getSubtitle())) {
            mSubtitle.setVisibility(VISIBLE);
            mSubtitle.setText(
                    mBidiFormatter.unicodeWrap(row.getSubtitle(), TextDirectionHeuristics.LOCALE));
        } else {
            mSubtitle.setVisibility(GONE);
        }
        if (row.getStartIcon() != null) {
            mStartIcon.setVisibility(VISIBLE);
            Drawable drawable = row.getStartIcon().loadDrawable(getContext());
            if (drawable != null && row.isStartIconTintable()) {
                if (mStartIconTint == 0) {
                    mStartIconTint = getContext().getColor(R.color.qc_start_icon_color);
                }
                drawable.setTint(mStartIconTint);
            }
            mStartIcon.setImageDrawable(drawable);
        } else {
            mStartIcon.setImageDrawable(null);
            mStartIcon.setVisibility(GONE);
        }
        QCSlider slider = row.getSlider();
        if (slider != null) {
            mSeekBarContainer.setVisibility(View.VISIBLE);
            initSlider(slider);
        } else {
            mSeekBarContainer.setVisibility(View.GONE);
            mQCSlider = null;
        }

        int startItemCount = row.getStartItems().size();
        for (int i = 0; i < startItemCount; i++) {
            QCActionItem action = row.getStartItems().get(i);
            initActionItem(mStartItemsContainer, mStartItemsContainer.getChildAt(i), action);
        }
        if (mStartItemsContainer.getChildCount() > startItemCount) {
            // remove extra items
            mStartItemsContainer.removeViews(startItemCount,
                    mStartItemsContainer.getChildCount() - startItemCount);
        }
        if (startItemCount == 0) {
            mStartItemsContainer.setVisibility(View.GONE);
        } else {
            mStartItemsContainer.setVisibility(View.VISIBLE);
        }

        int endItemCount = row.getEndItems().size();
        for (int i = 0; i < endItemCount; i++) {
            QCActionItem action = row.getEndItems().get(i);
            initActionItem(mEndItemsContainer, mEndItemsContainer.getChildAt(i), action);
        }
        if (mEndItemsContainer.getChildCount() > endItemCount) {
            // remove extra items
            mEndItemsContainer.removeViews(endItemCount,
                    mEndItemsContainer.getChildCount() - endItemCount);
        }
        if (endItemCount == 0) {
            mEndItemsContainer.setVisibility(View.GONE);
        } else {
            mEndItemsContainer.setVisibility(View.VISIBLE);
        }
    }

    private void initActionItem(@NonNull ViewGroup root, @Nullable View actionView,
            @NonNull QCActionItem action) {
        if (action.getType().equals(QC_TYPE_ACTION_SWITCH)) {
            initSwitchView(action, root, actionView);
        } else {
            initToggleView(action, root, actionView);
        }
    }

    private void initSwitchView(QCActionItem action, ViewGroup root, View actionView) {
        Switch switchView = actionView == null ? null : actionView.findViewById(
                android.R.id.switch_widget);
        if (switchView == null) {
            actionView = createActionView(root, actionView, R.layout.qc_action_switch);
            switchView = actionView.requireViewById(android.R.id.switch_widget);
        }
        CarUiUtils.makeAllViewsEnabled(switchView, action.isEnabled());

        boolean shouldEnableView =
                (action.isEnabled() || action.isClickableWhileDisabled()) && action.isAvailable()
                && action.isClickable();
        switchView.setOnCheckedChangeListener(null);
        switchView.setEnabled(shouldEnableView);
        switchView.setChecked(action.isChecked());
        switchView.setContentDescription(action.getContentDescription());
        switchView.setOnTouchListener((v, event) -> {
            if (!action.isEnabled()) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    fireAction(action, new Intent());
                }
                return true;
            }
            return false;
        });
        switchView.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    Intent intent = new Intent();
                    intent.putExtra(QC_ACTION_TOGGLE_STATE, isChecked);
                    fireAction(action, intent);
                });
    }

    private void initToggleView(QCActionItem action, ViewGroup root, View actionView) {
        DrawableStateToggleButton tmpToggleButton =
                actionView == null ? null : actionView.findViewById(R.id.qc_toggle_button);
        if (tmpToggleButton == null) {
            actionView = createActionView(root, actionView, R.layout.qc_action_toggle);
            tmpToggleButton = actionView.requireViewById(R.id.qc_toggle_button);
        }
        DrawableStateToggleButton toggleButton = tmpToggleButton; // must be effectively final
        boolean shouldEnableView =
                (action.isEnabled() || action.isClickableWhileDisabled()) && action.isAvailable()
                && action.isClickable();
        toggleButton.setText(null);
        toggleButton.setTextOn(null);
        toggleButton.setTextOff(null);
        toggleButton.setOnCheckedChangeListener(null);
        Drawable icon = QCViewUtils.getToggleIcon(mContext, action.getIcon(), action.isAvailable());
        toggleButton.setContentDescription(action.getContentDescription());
        toggleButton.setButtonDrawable(icon);
        toggleButton.setChecked(action.isChecked());
        toggleButton.setEnabled(shouldEnableView);
        setToggleButtonDrawableState(toggleButton, action.isEnabled(), action.isAvailable());
        toggleButton.setOnTouchListener((v, event) -> {
            if (!action.isEnabled()) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    fireAction(action, new Intent());
                }
                return true;
            }
            return false;
        });
        toggleButton.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    Intent intent = new Intent();
                    intent.putExtra(QC_ACTION_TOGGLE_STATE, isChecked);
                    fireAction(action, intent);
                });
    }

    private void setToggleButtonDrawableState(DrawableStateToggleButton view,
            boolean enabled, boolean available) {
        int[] statesToAdd = null;
        int[] statesToRemove = null;
        if (enabled) {
            if (!available) {
                statesToAdd =
                        new int[]{android.R.attr.state_enabled, R.attr.state_toggle_unavailable};
            } else {
                statesToAdd = new int[]{android.R.attr.state_enabled};
                statesToRemove = new int[]{R.attr.state_toggle_unavailable};
            }
        } else {
            if (available) {
                statesToRemove =
                        new int[]{android.R.attr.state_enabled, R.attr.state_toggle_unavailable};
            } else {
                statesToAdd = new int[]{R.attr.state_toggle_unavailable};
                statesToRemove = new int[]{android.R.attr.state_enabled};
            }
        }
        CarUiUtils.applyDrawableStatesToAllViews(view, statesToAdd, statesToRemove);
    }

    @NonNull
    private View createActionView(@NonNull ViewGroup root, @Nullable View actionView,
            @LayoutRes int resId) {
        if (actionView != null) {
            // remove current action view
            root.removeView(actionView);
        }
        actionView = mLayoutInflater.inflate(resId, root, /* attachToRoot= */ false);
        root.addView(actionView);
        return actionView;
    }

    private void initSlider(QCSlider slider) {
        mQCSlider = slider;
        CarUiUtils.makeAllViewsEnabled(mSeekBar, slider.isEnabled());

        mSeekBar.setOnSeekBarChangeListener(null);
        mSeekBar.setMin(slider.getMin());
        mSeekBar.setMax(slider.getMax());
        mSeekBar.setProgress(slider.getValue());
        mSeekBar.setEnabled(slider.isEnabled());
        mSeekBar.setClickableWhileDisabled(slider.isClickableWhileDisabled());
        mSeekBar.setDisabledClickListener(seekBar -> fireAction(slider, new Intent()));
        if (!slider.isEnabled() && mInDirectManipulationMode) {
            setInDirectManipulationMode(mSeekBarContainer, mSeekBar, false);
        }
        if (mSeekbarChangeListener == null) {
            mSeekbarChangeListener = new QCSeekbarChangeListener();
        }
        mSeekbarChangeListener.setSlider(slider);
        mSeekBar.setOnSeekBarChangeListener(mSeekbarChangeListener);
        // set up rotary support
        mSeekBarContainer.setOnKeyListener(mSeekBarKeyListener);
        mSeekBarContainer.setOnFocusChangeListener(mSeekBarFocusChangeListener);
        mSeekBarContainer.setOnGenericMotionListener(mSeekBarScrollListener);
    }

    private void setInDirectManipulationMode(View view, SeekBar seekbar, boolean enable) {
        mInDirectManipulationMode = enable;
        DirectManipulationHelper.enableDirectManipulationMode(seekbar, enable);
        view.setSelected(enable);
        seekbar.setSelected(enable);
    }

    private void fireAction(QCItem item, Intent intent) {
        if (!item.isEnabled()) {
            if (item.getDisabledClickAction() != null) {
                try {
                    item.getDisabledClickAction().send(getContext(), 0, intent);
                    if (mActionListener != null) {
                        mActionListener.onQCAction(item, item.getDisabledClickAction());
                    }
                } catch (PendingIntent.CanceledException e) {
                    Log.d(TAG, "Error sending intent", e);
                }
            } else if (item.getDisabledClickActionHandler() != null) {
                item.getDisabledClickActionHandler().onAction(item, getContext(), intent);
                if (mActionListener != null) {
                    mActionListener.onQCAction(item, item.getDisabledClickActionHandler());
                }
            }
            return;
        }

        if (item.getPrimaryAction() != null) {
            try {
                item.getPrimaryAction().send(getContext(), 0, intent);
                if (mActionListener != null) {
                    mActionListener.onQCAction(item, item.getPrimaryAction());
                }
            } catch (PendingIntent.CanceledException e) {
                Log.d(TAG, "Error sending intent", e);
            }
        } else if (item.getActionHandler() != null) {
            item.getActionHandler().onAction(item, getContext(), intent);
            if (mActionListener != null) {
                mActionListener.onQCAction(item, item.getActionHandler());
            }
        }
    }

    private class QCSeekbarChangeListener implements SeekBar.OnSeekBarChangeListener {
        // Interval of updates (in ms) sent in response to seekbar moving.
        private static final int SLIDER_UPDATE_INTERVAL = 200;

        private final Handler mSliderUpdateHandler;
        private QCSlider mSlider;
        private int mCurrSliderValue;
        private boolean mSliderUpdaterRunning;
        private long mLastSentSliderUpdate;
        private final Runnable mSliderUpdater = () -> {
            sendSliderValue();
            mSliderUpdaterRunning = false;
        };

        QCSeekbarChangeListener() {
            mSliderUpdateHandler = new Handler();
        }

        void setSlider(QCSlider slider) {
            mSlider = slider;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            mCurrSliderValue = progress;
            long now = System.currentTimeMillis();
            if (mLastSentSliderUpdate != 0
                    && now - mLastSentSliderUpdate > SLIDER_UPDATE_INTERVAL) {
                mSliderUpdaterRunning = false;
                mSliderUpdateHandler.removeCallbacks(mSliderUpdater);
                sendSliderValue();
            } else if (!mSliderUpdaterRunning) {
                mSliderUpdaterRunning = true;
                mSliderUpdateHandler.postDelayed(mSliderUpdater, SLIDER_UPDATE_INTERVAL);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (mSliderUpdaterRunning) {
                mSliderUpdaterRunning = false;
                mSliderUpdateHandler.removeCallbacks(mSliderUpdater);
            }
            mCurrSliderValue = seekBar.getProgress();
            sendSliderValue();
        }

        private void sendSliderValue() {
            if (mSlider == null) {
                return;
            }
            mLastSentSliderUpdate = System.currentTimeMillis();
            Intent intent = new Intent();
            intent.putExtra(QC_ACTION_SLIDER_VALUE, mCurrSliderValue);
            fireAction(mSlider, intent);
        }
    }
}
