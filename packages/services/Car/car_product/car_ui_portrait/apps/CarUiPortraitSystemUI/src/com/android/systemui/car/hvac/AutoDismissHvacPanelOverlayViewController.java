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

package com.android.systemui.car.hvac;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.wm.shell.animation.FlingAnimationUtils;

import javax.inject.Inject;

/**
 * An extension of {@link HvacPanelOverlayViewController} which auto dismisses the panel if there
 * is no activity for some configured amount of time.
 */
@SysUISingleton
public class AutoDismissHvacPanelOverlayViewController extends HvacPanelOverlayViewController {

    private final Resources mResources;
    private final Handler mHandler;
    private final Context mContext;

    private int mAutoDismissDurationMs;
    private ObjectAnimator mOpenAnimator;
    private ObjectAnimator mCloseAnimator;

    private final Runnable mAutoDismiss = () -> {
        if (isPanelExpanded()) {
            toggle();
        }
    };

    @Inject
    public AutoDismissHvacPanelOverlayViewController(Context context,
            @Main Resources resources,
            HvacController hvacController,
            OverlayViewGlobalStateController overlayViewGlobalStateController,
            FlingAnimationUtils.Builder flingAnimationUtilsBuilder,
            CarDeviceProvisionedController carDeviceProvisionedController,
            @Main Handler handler,
            ConfigurationController configurationController,
            UiModeManager uiModeManager) {
        super(context, resources, hvacController, overlayViewGlobalStateController,
                flingAnimationUtilsBuilder, carDeviceProvisionedController, configurationController,
                uiModeManager);
        mResources = resources;
        mHandler = handler;
        mContext = context;
    }

    @Override
    protected void onTouchEvent(View view, MotionEvent event) {
        int hvacHeight = mResources.getDimensionPixelSize(R.dimen.hvac_panel_full_expanded_height);
        if (isPanelExpanded() && (event.getY() < (view.getHeight() - hvacHeight))
                && (event.getAction() == MotionEvent.ACTION_UP)) {
            mHandler.post(mAutoDismiss);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mAutoDismissDurationMs = mResources.getInteger(R.integer.config_hvacAutoDismissDurationMs);

        mOpenAnimator = (ObjectAnimator) AnimatorInflater.loadAnimator(mContext,
                R.anim.hvac_open_anim);
        mOpenAnimator.setTarget(getLayout());
        mCloseAnimator = (ObjectAnimator) AnimatorInflater.loadAnimator(mContext,
                R.anim.hvac_close_anim);
        mCloseAnimator.setTarget(getLayout());
    }

    @Override
    protected void onAnimateExpandPanel() {
        super.onAnimateExpandPanel();

        mHandler.postDelayed(mAutoDismiss, mAutoDismissDurationMs);
    }

    @Override
    protected void onAnimateCollapsePanel() {
        super.onAnimateCollapsePanel();

        mHandler.removeCallbacks(mAutoDismiss);
    }

    @Override
    protected void animate(float from, float to, float velocity, boolean isClosing) {
        if (isAnimating()) {
            return;
        }
        mIsAnimating = true;
        setIsTracking(true);

        ObjectAnimator animator = isClosing ? mCloseAnimator : mOpenAnimator;
        animator.setFloatValues(from, to);
        animator.removeAllListeners();
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mIsAnimating = false;
                setIsTracking(false);
                mOpeningVelocity = DEFAULT_FLING_VELOCITY;
                mClosingVelocity = DEFAULT_FLING_VELOCITY;
                if (isClosing) {
                    resetPanelVisibility();
                } else {
                    onExpandAnimationEnd();
                    setPanelExpanded(true);
                    setViewClipBounds((int) to);
                }
            }
        });
        animator.start();
    }

    @Override
    protected void setUpHandleBar() {
        // No-op
    }
}
