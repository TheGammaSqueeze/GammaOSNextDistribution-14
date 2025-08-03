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
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;

import com.android.car.portraitlauncher.panel.TaskViewPanelOverlay;

/**
 * A {@code PanelAnimator} to animate the panel into the open state with centered icon.
 *
 * This animator resizes the panel into the given bounds using the open panel animation
 * parameters. Covering the panel with overlay and centered icon during animation.
 */
public class OpenPanelWithIconAnimator extends OpenPanelAnimator {

    private static final float OVERLAY_FADE_OUT_END_ALPHA = 0f;
    private static final float OVERLAY_FADE_OUT_START_ALPHA = 1f;
    private static final long OVERLAY_FADE_OUT_DURATION = 300;
    private static final long OVERLAY_FADE_OUT_START_DELAY = 300;

    private final TaskViewPanelOverlay mOverlay;
    private ViewPropertyAnimator mOverlayAnimator;

    /**
     * A {@code PanelAnimator} to animate the panel into the open state.
     *
     * @param panel           The panel on which the animator acts.
     * @param bounds          The final bounds that the panel should animate to.
     * @param overlay         The overlay view that covers the {@code TaskView}. Used to cover
     *                        the panel during animation.
     */
    public OpenPanelWithIconAnimator(ViewGroup panel, Rect bounds,
            TaskViewPanelOverlay overlay) {
        super(panel, bounds);
        mOverlay = overlay;
    }

    @Override
    public void animate(Runnable endAction) {
        mOverlay.show(/* withIcon= */ true);
        mOverlay.setAlpha(OVERLAY_FADE_OUT_START_ALPHA);

        super.animate(() ->
                mOverlayAnimator = mOverlay
                        .animate()
                        .alpha(OVERLAY_FADE_OUT_END_ALPHA)
                        .setStartDelay(OVERLAY_FADE_OUT_START_DELAY)
                        .setDuration(OVERLAY_FADE_OUT_DURATION)
                        .withEndAction(() -> {
                            mOverlay.hide();
                            mOverlay.setAlpha(OVERLAY_FADE_OUT_START_ALPHA);
                            endAction.run();
                        })
        );
    }

    @Override
    public void cancel() {
        super.cancel();
        if (mOverlayAnimator != null) {
            mOverlayAnimator.cancel();
        }
        mOverlay.hide();
        mOverlay.setAlpha(OVERLAY_FADE_OUT_START_ALPHA);
    }
}
