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

import android.graphics.Point;
import android.graphics.Rect;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import com.android.car.portraitlauncher.panel.TaskViewPanelOverlay;

/**
 * A {@code PanelAnimator} to animate the panel into the full screen state.
 *
 * This animator resizes the panel into the given bounds using the full-screen panel animation
 * parameters. Covering the panel with overlay and centered icon during animation.
 */
public class FullScreenPanelAnimator extends PanelAnimator {
    private static final Interpolator INTERPOLATOR = new PathInterpolator(0.05f, 0.7f, 0.1f, 1);
    private static final long DURATION = 400;
    private static final float OVERLAY_FADE_OUT_END_ALPHA = 0f;
    private static final float OVERLAY_FADE_OUT_START_ALPHA = 1f;
    private static final long OVERLAY_FADE_OUT_DURATION = 300;
    private static final long OVERLAY_FADE_OUT_START_DELAY = 300;

    private final Rect mBounds;
    private final Point mInitialOffset;
    private final TaskViewPanelOverlay mOverlay;
    private Animation mAnimation;
    private ViewPropertyAnimator mOverlayAnimator;

    /**
     * A {@code PanelAnimator} to animate the panel into the full screen state.
     *
     * @param panel         The panel that should animate
     * @param bounds        The final bounds of the panel within its parent
     * @param initialOffset The initial top left corner of the panel in its parent.
     * @param overlay       The overlay view that covers the {@code TaskView}. Used to display
     *                      the application icon during animation.
     */
    public FullScreenPanelAnimator(ViewGroup panel, Rect bounds, Point initialOffset,
            TaskViewPanelOverlay overlay) {
        super(panel);
        mBounds = bounds;
        mInitialOffset = initialOffset;
        mOverlay = overlay;
    }

    @Override
    public void animate(Runnable endAction) {
        // To reduce the visual glitches, resize the panel before starting the animation.
        Rect bounds = new Rect(mBounds);
        bounds.offset(mInitialOffset.x, mInitialOffset.y);
        updateBounds(bounds);
        mOverlay.show(/* withIcon= */ true);
        mOverlay.setAlpha(OVERLAY_FADE_OUT_START_ALPHA);

        mAnimation = new BoundsAnimation(mPanel, mBounds, () -> {
            mOverlayAnimator = mOverlay.animate().alpha(OVERLAY_FADE_OUT_END_ALPHA)
                    .setStartDelay(OVERLAY_FADE_OUT_START_DELAY)
                    .setDuration(OVERLAY_FADE_OUT_DURATION)
                    .withEndAction(() -> {
                        mOverlay.hide();
                        mOverlay.setAlpha(OVERLAY_FADE_OUT_START_ALPHA);
                        endAction.run();
                    });
        });

        mAnimation.setInterpolator(INTERPOLATOR);
        mAnimation.setDuration(DURATION);
        mPanel.post(() -> mPanel.startAnimation(mAnimation));
    }

    @Override
    public void cancel() {
        if (mAnimation != null) {
            mAnimation.cancel();
        }
        if (mOverlayAnimator != null) {
            mOverlayAnimator.cancel();
        }
        mOverlay.hide();
        mOverlay.setAlpha(OVERLAY_FADE_OUT_START_ALPHA);
    }
}
