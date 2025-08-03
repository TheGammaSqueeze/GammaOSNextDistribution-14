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

package com.android.systemui.car.decor;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

import androidx.constraintlayout.motion.widget.MotionLayout;

import com.android.systemui.R;
import com.android.systemui.util.concurrency.DelayableExecutor;

/**
 * This class controls the animation of the immersive privacy chip.
 */
public class CarPrivacyChipAnimationHelper {
    private static final String TAG = CarPrivacyChipViewController.class.getSimpleName();
    private final Context mContext;
    private final long mDuration;
    private final long mDotTransitionDelay;
    private DelayableExecutor mExecutor;

    /**
     * Constructor for CarPrivacyChipAnimationHelper.
     */
    public CarPrivacyChipAnimationHelper(Context context) {
        mContext = context;
        mDuration = Long.valueOf(
                context.getResources().getInteger(R.integer.privacy_indicator_animation_duration));
        mDotTransitionDelay = Long.valueOf(
                context.getResources().getInteger(R.integer.privacy_chip_pill_to_circle_delay));
    }

    /**
     * Shows both mic and camara indicators.
     */
    void showCameraAndMicChip(View container) {
        MotionLayout chipView = container.findViewById(R.id.immersive_indicator_container);
        showIcon(container, chipView, R.id.immersive_show_mic_and_camera_transition,
                R.id.immersive_mic_and_camera_transition_collapse);
    }

    /**
     * Shows the camara indicator.
     */
    void showCameraChip(View container) {
        MotionLayout chipView = container.findViewById(R.id.immersive_indicator_container);
        showIcon(container, chipView, R.id.immersive_show_camera_transition,
                R.id.immersive_camera_transition_collapse);
    }

    /**
     * Shows the microphone indicator.
     */
    void showMicChip(View container) {
        MotionLayout chipView = container.findViewById(R.id.immersive_indicator_container);
        showIcon(container, chipView, R.id.immersive_show_mic_transition,
                R.id.immersive_mic_transition_collapse);
    }

    /**
     * Shows the indicator as a privacy dot.
     */
    void showPrivacyDot(View container) {
        container.setVisibility(View.VISIBLE);
        MotionLayout chipView = container.findViewById(R.id.immersive_indicator_container);
        chipView.setTransition(R.id.immersive_show_dot_transition);
        chipView.transitionToEnd();
        chipView.setVisibility(View.VISIBLE);
    }

    /**
     * Hides the privacy dot.
     */
    void hidePrivacyDot(View container) {
        MotionLayout chipView = container.findViewById(R.id.immersive_indicator_container);
        hideIcon(container, chipView, R.id.immersive_hide_dot_transition);
    }

    /**
     * Hides the privacy dot without the animation.
     */
    void hidePrivacyDotWithoutAnimation(View container) {
        hideViewWithoutAnimation(container, R.id.immersive_privacy_microphone);
        hideViewWithoutAnimation(container, R.id.immersive_privacy_camera);
        hideViewWithoutAnimation(container, R.id.immersive_privacy_dot);

        container.setVisibility(View.GONE);
    }

    private void hideViewWithoutAnimation(View container, int viewId) {
        ImageView imageView = container.findViewById(viewId);
        imageView.setVisibility(View.GONE);
    }

    private void showIcon(View container, MotionLayout view, int showTransitionId,
            int collapseTransistionId) {
        container.setVisibility(View.VISIBLE);
        view.setTransition(showTransitionId);
        view.transitionToEnd();
        view.addTransitionListener(new MotionLayout.TransitionListener() {
            @Override
            public void onTransitionStarted(MotionLayout motionLayout, int i, int i1) {
                // Do nothing.
            }

            @Override
            public void onTransitionChange(MotionLayout motionLayout, int i, int i1, float v) {
                // Do nothing.
            }

            @Override
            public void onTransitionCompleted(MotionLayout motionLayout, int i) {
                if (mExecutor != null) {
                    mExecutor.executeDelayed(new Runnable() {
                        @Override
                        public void run() {
                            view.setTransition(collapseTransistionId);
                            view.transitionToEnd();
                        }
                    }, mDotTransitionDelay);
                }
                view.removeTransitionListener(this);
            }

            @Override
            public void onTransitionTrigger(MotionLayout motionLayout, int i, boolean b,
                    float v) {
                // Do nothing.
            }
        });
        view.setVisibility(View.VISIBLE);
    }

    private void hideIcon(View container, MotionLayout view, int transitionId) {
        if (view.getVisibility() == View.VISIBLE) {
            view.setTransition(transitionId);
            view.transitionToEnd();
            view.addTransitionListener(new MotionLayout.TransitionListener() {
                @Override
                public void onTransitionStarted(MotionLayout motionLayout, int i, int i1) {
                    // Do nothing.
                }

                @Override
                public void onTransitionChange(MotionLayout motionLayout, int i, int i1,
                        float v) {
                    // Do nothing.
                }

                @Override
                public void onTransitionCompleted(MotionLayout motionLayout, int i) {
                    view.setVisibility(View.GONE);
                    container.setVisibility(View.GONE);
                    view.removeTransitionListener(this);
                }

                @Override
                public void onTransitionTrigger(MotionLayout motionLayout, int i, boolean b,
                        float v) {
                    // Do nothing.
                }
            });
        }
    }

    /**
     * sets an executor.
     */
    void setExecutor(DelayableExecutor executor) {
        mExecutor = executor;
    }
}
