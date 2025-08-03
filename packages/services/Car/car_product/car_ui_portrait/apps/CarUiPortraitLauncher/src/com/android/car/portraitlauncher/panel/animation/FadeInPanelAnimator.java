/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.portraitlauncher.panel.animation;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * A {@code PanelAnimator} to animate the panel into the open state using the fade-in animation.
 */
public class FadeInPanelAnimator extends PanelAnimator {
    private static final Interpolator INTERPOLATOR = new PathInterpolator(0f, 0, 0.5f, 1);
    private static final int DELAY = 300;
    private static final int DURATION = 300;
    private static final float INITIAL_SCALE = 0.9f;
    private static final float FINAL_SCALE = 1f;

    private final View mTaskView;
    private final Rect mBounds;
    private ViewPropertyAnimator mViewPropertyAnimator;

    /**
     * A {@code PanelAnimator} to animate the panel into the open state using the fade-in animation.
     *
     * @param panel The panel that should animate
     * @param taskView The task view of the panel.
     * @param toBounds The final bounds of the panel within its parent
     */
    public FadeInPanelAnimator(ViewGroup panel, View taskView, Rect toBounds) {
        super(panel);
        mTaskView = taskView;
        mBounds = toBounds;
    }

    @Override
    public void animate(Runnable endAction) {
        updateBounds(mBounds);
        mTaskView.setScaleX(INITIAL_SCALE);
        mTaskView.setScaleY(INITIAL_SCALE);
        mViewPropertyAnimator = mTaskView.animate()
                .scaleX(FINAL_SCALE)
                .scaleY(FINAL_SCALE)
                .setDuration(DURATION)
                .setInterpolator(INTERPOLATOR)
                .setStartDelay(DELAY)
                .withEndAction(endAction);
    }

    @Override
    public void cancel() {
        if (mViewPropertyAnimator != null) {
            mViewPropertyAnimator.cancel();
        }
        mPanel.setScaleX(FINAL_SCALE);
        mPanel.setScaleY(FINAL_SCALE);
        updateBounds(mBounds);
    }
}
