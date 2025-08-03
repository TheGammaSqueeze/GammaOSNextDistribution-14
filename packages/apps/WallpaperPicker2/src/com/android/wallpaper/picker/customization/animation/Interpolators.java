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
package com.android.wallpaper.picker.customization.animation;

import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * Copied over from:
 * frameworks/base/packages/SystemUI/animation/src/com/android/systemui/animation/Interpolators.java
 *
 * Utility class to receive interpolators from.
 *
 * Make sure that changes made to this class are also reflected in {@link InterpolatorsAndroidX}.
 * Please consider using the androidx dependencies featuring better testability altogether.
 */
// TODO (b/281878827): remove this and use loading animation in SystemUIShaderLib when available
public class Interpolators {
    /*
     * ============================================================================================
     * Standard interpolators.
     * ============================================================================================
     */

    /**
     * The standard interpolator that should be used on every normal animation
     */
    public static final Interpolator STANDARD = new PathInterpolator(
            0.2f, 0f, 0f, 1f);

    /**
     * The standard decelerating interpolator that should be used on every regular movement of
     * content that is appearing e.g. when coming from off screen.
     */
    public static final Interpolator STANDARD_DECELERATE = new PathInterpolator(
            0f, 0f, 0f, 1f);
}
