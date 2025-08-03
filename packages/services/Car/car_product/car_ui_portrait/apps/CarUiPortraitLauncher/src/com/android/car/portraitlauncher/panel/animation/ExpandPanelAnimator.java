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

import static android.view.View.INVISIBLE;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.Interpolator;

/**
 * A {@code PanelAnimator} to animate the panel into the open state using the expand animation.
 */
public class ExpandPanelAnimator extends PanelAnimator {
    private static final Interpolator INTERPOLATOR = createEmphasizedInterpolator();
    private static final long DURATION = 600;

    private static final float INITIAL_SCALE = 0;
    private static final float FINAL_SCALE = 1;

    private final Rect mBounds;
    private final View mGripBar;
    private final Point mOrigin;
    private ViewPropertyAnimator mViewPropertyAnimator;

    /**
     * A {@code PanelAnimator} to animate the panel into the open state using the expand animation.
     *
     * @param panel The panel that should animate
     * @param origin The origin of the expand animation within the panel's parent
     * @param bounds The final bounds of the panel within its parent
     * @param gripBar The grip bar of the panel.
     */
    public ExpandPanelAnimator(ViewGroup panel, Point origin, Rect bounds, View gripBar) {
        super(panel);
        mBounds = bounds;
        mGripBar = gripBar;
        mOrigin = origin;
    }

    @Override
    public void animate(Runnable endAction) {
        mGripBar.setVisibility(INVISIBLE);
        updateBounds(mBounds);
        mPanel.setScaleX(INITIAL_SCALE);
        mPanel.setScaleY(INITIAL_SCALE);

        mPanel.setTranslationX(mOrigin.x - mBounds.centerX());
        mPanel.setTranslationY(mOrigin.y - mBounds.centerY());

        mViewPropertyAnimator = mPanel.animate()
                .scaleX(FINAL_SCALE)
                .scaleY(FINAL_SCALE)
                .translationX(/* value= */ 0)
                .translationY(/* value= */ 0)
                .setDuration(DURATION)
                .setInterpolator(INTERPOLATOR)
                .withEndAction(endAction);
    }

    @Override
    public void cancel() {
        if (mViewPropertyAnimator != null) {
            mViewPropertyAnimator.cancel();
        }
        mPanel.setScaleX(FINAL_SCALE);
        mPanel.setScaleY(FINAL_SCALE);
        mPanel.setTranslationX(/* value= */ 0);
        mPanel.setTranslationY(/* value= */ 0);
    }
}
