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

import android.graphics.Path;
import android.graphics.Rect;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

/** An abstract class for all the animators that can be used on the {@code TaskViewPanel}. */
public abstract class PanelAnimator {

    /** The panel on which the animation will be applied. */
    protected ViewGroup mPanel;

    protected PanelAnimator(ViewGroup panel) {
        mPanel = panel;
    }

    /**
     * Performs the animation on the panel.
     *
     * The {@code endAction} should be called at the end of the animation.
     */
    public abstract void animate(Runnable endAction);

    /**
     * Cancels any on going animations.
     */
    public abstract void cancel();

    /** Updates the bounds of the panel to the given {@code bounds}. */
    protected void updateBounds(Rect bounds) {
        final ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) mPanel.getLayoutParams();
        layoutParams.topMargin = bounds.top;
        layoutParams.rightMargin = bounds.right;
        layoutParams.width = bounds.width();
        layoutParams.height = bounds.height();
        mPanel.setLayoutParams(layoutParams);
    }

    // Create the default emphasized interpolator
    protected static PathInterpolator createEmphasizedInterpolator() {
        Path path = new Path();
        // Doing the same as fast_out_extra_slow_in
        path.moveTo(/* x= */ 0f, /* y= */ 0f);
        path.cubicTo(/* x1= */ 0.05f, /* y1= */ 0f, /* x2= */ 0.133333f,
                /* y2= */ 0.06f, /* x3= */ 0.166666f, /* y3= */0.4f);
        path.cubicTo(/* x1= */ 0.208333f, /* y1= */ 0.82f, /* x2= */ 0.25f,
                /* y2= */ 1f, /* x3= */ 1f, /* y3= */ 1f);
        return new PathInterpolator(path);
    }
}
