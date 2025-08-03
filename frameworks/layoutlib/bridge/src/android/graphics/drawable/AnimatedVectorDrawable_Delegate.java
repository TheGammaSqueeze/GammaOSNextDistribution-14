/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.graphics.drawable;

import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import android.graphics.Canvas;
import android.graphics.drawable.AnimatedVectorDrawable.VectorDrawableAnimatorRT;

public class AnimatedVectorDrawable_Delegate {

    /**
     * We would like to do the same as in {@link AnimatedVectorDrawable#draw}, but bypass the
     * {@link Canvas#isHardwareAccelerated} check and call
     * {@link AnimatedVectorDrawable#forceAnimationOnUI}. We need this for the callbacks to be
     * properly set up so that {@link AnimatedVectorDrawable} receives property updates.
     * TODO (b/141682855): Figure out how to properly manage this in the hardware accelerated case
     */
    @LayoutlibDelegate
    static void draw(AnimatedVectorDrawable thisDrawable, Canvas canvas) {
        if (thisDrawable.mAnimatorSet instanceof VectorDrawableAnimatorRT) {
            // If we have SW canvas and the RT animation is waiting to start, We need to fallback
            // to UI thread animation for AVD.
            if (!thisDrawable.mAnimatorSet.isRunning() &&
                    ((VectorDrawableAnimatorRT)thisDrawable.mAnimatorSet).mPendingAnimationActions.size() > 0) {
                thisDrawable.forceAnimationOnUI();
            }
        }
        thisDrawable.draw_Original(canvas);
    }
}
