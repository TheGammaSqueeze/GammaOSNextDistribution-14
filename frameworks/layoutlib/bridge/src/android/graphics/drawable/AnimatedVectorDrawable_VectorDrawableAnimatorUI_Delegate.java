/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License") {}
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

import android.animation.AnimationHandler;
import android.graphics.Canvas;
import android.graphics.drawable.AnimatedVectorDrawable.VectorDrawableAnimatorUI;

/**
 * Delegate used to provide new implementation of a select few methods of {@link
 * AnimatedVectorDrawable}
 * <p>
 * Through the layoutlib_create tool, the original  methods of AnimatedVectorDrawable have been
 * replaced by calls to methods of the same name in this delegate class.
 */
@SuppressWarnings("unused")
public class AnimatedVectorDrawable_VectorDrawableAnimatorUI_Delegate {

    public static long sFrameTime;

    @LayoutlibDelegate
    /*package*/ static void onDraw(VectorDrawableAnimatorUI thisDrawableAnimator, Canvas canvas) {
        thisDrawableAnimator.onDraw_Original(canvas);
        AnimationHandler handler = AnimationHandler.getInstance();
        if (thisDrawableAnimator.mSet.mLastFrameTime < 0) {
            handler.doAnimationFrame(0);
        }
        handler.doAnimationFrame(sFrameTime);
    }
}
