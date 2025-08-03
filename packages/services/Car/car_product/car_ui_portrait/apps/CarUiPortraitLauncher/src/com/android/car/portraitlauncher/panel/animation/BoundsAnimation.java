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

import android.animation.RectEvaluator;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;

/** The animation used to update the bounds of a {@code ViewGroup}. */
public class BoundsAnimation extends Animation {
    private View mPanel;
    private Rect mFromBounds;
    private Rect mToBounds;
    private ViewGroup.MarginLayoutParams mLayoutParams;
    private RectEvaluator mEvaluator = new RectEvaluator(new Rect());
    private boolean mAnimationEnded;
    private boolean mIsCanceled;

    BoundsAnimation(ViewGroup panel, Rect toBounds, Runnable onAnimationEnd) {
        mPanel = panel;
        mToBounds = toBounds;
        mAnimationEnded = false;
        mLayoutParams = (ViewGroup.MarginLayoutParams) mPanel.getLayoutParams();
        mFromBounds = new Rect(mLayoutParams.leftMargin, mLayoutParams.topMargin,
                mLayoutParams.width, mLayoutParams.topMargin + mLayoutParams.height);

        setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mAnimationEnded = true;
                if (!mIsCanceled) {
                    onAnimationEnd.run();
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        if (mAnimationEnded) {
            return;
        }
        Rect bounds = mEvaluator.evaluate(interpolatedTime, mFromBounds, mToBounds);
        mLayoutParams.topMargin = bounds.top;
        mLayoutParams.leftMargin = bounds.left;
        mLayoutParams.width = bounds.width();
        mLayoutParams.height = bounds.height();
        mPanel.setLayoutParams(mLayoutParams);
    }

    @Override
    public void cancel() {
        super.cancel();
        mIsCanceled = true;
    }
}
