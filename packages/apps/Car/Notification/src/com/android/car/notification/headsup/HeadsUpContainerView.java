/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.notification.headsup;

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS;

import static com.android.car.ui.utils.RotaryConstants.ROTARY_FOCUS_DELEGATING_CONTAINER;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.notification.R;
import com.android.car.ui.FocusArea;

/**
 * Container that is used to present heads-up notifications. It is responsible for delegating the
 * focus to the topmost notification and ensuring that new HUNs gains focus automatically when
 * one of the existing HUNs already has focus.
 */
public class HeadsUpContainerView extends FrameLayout {
    private final boolean mFocusHUNWhenShown;
    private final int mEnterAnimationDuration;
    private final int mExitAnimationDuration;
    private Handler mHandler;

    public HeadsUpContainerView(@NonNull Context context) {
        super(context);
    }

    public HeadsUpContainerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public HeadsUpContainerView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public HeadsUpContainerView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    {
        mHandler = new Handler(Looper.getMainLooper());
        mEnterAnimationDuration = getResources()
                .getInteger(R.integer.headsup_total_enter_duration_ms);
        mExitAnimationDuration = getResources().getInteger(R.integer.headsup_exit_duration_ms);
        mFocusHUNWhenShown = getResources().getBoolean(R.bool.config_focusHUNWhenShown);

        // This tag is required to make this container receive the focus request in order to
        // delegate focus to its children, even though the container itself isn't focusable.
        setContentDescription(ROTARY_FOCUS_DELEGATING_CONTAINER);
        setClickable(false);
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        if (isInTouchMode()) {
            return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
        }
        return focusTopmostChild();
    }

    @Override
    public void addView(View child) {
        super.addView(child);

        if (!isInTouchMode() && (getFocusedChild() != null || mFocusHUNWhenShown)
                && !topmostChildHasFocus()) {
            // Wait for the duration of the heads-up enter animation for a smoother UI experience.
            mHandler.postDelayed(() -> focusTopmostChild(), mEnterAnimationDuration);
        }
    }

    @Override
    public void removeViewAt(int index) {
        super.removeViewAt(index);

        if (!isInTouchMode() && mFocusHUNWhenShown && index == getChildCount()
                && getChildCount() > 0 && !topmostChildHasFocus()) {
            // Wait for the duration of the heads-up exit animation for a smoother UI experience.
            mHandler.postDelayed(() -> focusTopmostChild(), mExitAnimationDuration);
        }
    }

    private boolean topmostChildHasFocus() {
        int childCount = getChildCount();
        if (childCount <= 0) {
            return false;
        }

        View topmostChild = getChildAt(childCount - 1);
        if (!(topmostChild instanceof FocusArea)) {
            return false;
        }

        return topmostChild.hasFocus();
    }

    private boolean focusTopmostChild() {
        int childCount = getChildCount();
        if (childCount <= 0) {
            return false;
        }

        View topmostChild = getChildAt(childCount - 1);
        if (!(topmostChild instanceof FocusArea)) {
            return false;
        }

        FocusArea focusArea = (FocusArea) topmostChild;
        View view = focusArea.findViewById(R.id.action_1);
        if (view != null) {
            focusArea.setDefaultFocus(view);
        }

        return topmostChild.performAccessibilityAction(ACTION_FOCUS, /* arguments= */ null);
    }

    @VisibleForTesting
    void setHandler(Handler handler) {
        mHandler = handler;
    }
}
