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

package com.android.systemui.car.hvac.referenceui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.android.systemui.R;

/**
 * Represents a single bar in the fan speed bar.
 */
public class FanSpeedBarSegment extends ImageView {
    private boolean mTurnedOn;

    private int mDotExpandedSize;
    private int mDotSize;

    private ValueAnimator mDotWidthExpandAnimator;

    private final ValueAnimator.AnimatorUpdateListener mExpandListener =
            new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int size = (int) animation.getAnimatedValue();
                    GradientDrawable drawable = (GradientDrawable) getDrawable();
                    drawable.setCornerRadius(size / 2);
                    drawable.setSize(size, size);
                }
            };

    public FanSpeedBarSegment(Context context) {
        super(context);
        initView();
    }

    public FanSpeedBarSegment(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public FanSpeedBarSegment(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    /**
     * Plays the animation for the fan speed bar segment to be turned on.
     */
    public void playTurnOnAnimation(int delayMs, int duration) {
        getContext().getMainExecutor().execute(() -> {
            mDotWidthExpandAnimator.setStartDelay(delayMs);
            mDotWidthExpandAnimator.setDuration(duration);
            mDotWidthExpandAnimator.start();
            mTurnedOn = true;
        });
    }

    /**
     * Plays the animation for the fan speed bar segment to be turned off.
     */
    public void playTurnOffAnimation(int delayMs, int duration) {
        getContext().getMainExecutor().execute(() -> {
            mDotWidthExpandAnimator.setStartDelay(delayMs);
            mDotWidthExpandAnimator.setDuration(duration);
            mDotWidthExpandAnimator.reverse();
            mTurnedOn = false;
        });
    }

    public void setTurnedOn(boolean isOn) {
        mTurnedOn = isOn;
        GradientDrawable drawable = (GradientDrawable) getDrawable();
        if (mTurnedOn) {
            drawable.setCornerRadius(0);
            // Setting size -1, makes the drawable grow to the size of the image view.
            drawable.setSize(-1, -1);
        } else {
            drawable.setCornerRadius(mDotSize / 2);
            drawable.setSize(mDotSize, mDotSize);
        }
        setImageDrawable(drawable);
    }

    public boolean isTurnedOn() {
        return mTurnedOn;
    }

    private void initView() {
        setScaleType(ScaleType.CENTER);

        Resources res = getResources();
        mDotExpandedSize = res.getDimensionPixelSize(R.dimen.hvac_fan_speed_dot_expanded_size);
        mDotSize = res.getDimensionPixelSize(R.dimen.hvac_fan_speed_dot_size);
        mDotWidthExpandAnimator = ValueAnimator.ofInt(mDotSize, mDotExpandedSize);
        mDotWidthExpandAnimator.addUpdateListener(mExpandListener);

        GradientDrawable dot = new GradientDrawable();
        dot.setColor(res.getColor(R.color.hvac_fanspeed_segment_color));
        dot.setSize(mDotSize, mDotSize);
        dot.setCornerRadius(mDotSize / 2);
        setImageDrawable(dot);
    }
}
