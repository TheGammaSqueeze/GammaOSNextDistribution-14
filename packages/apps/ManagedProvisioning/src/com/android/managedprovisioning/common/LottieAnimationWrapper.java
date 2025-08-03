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

package com.android.managedprovisioning.common;

import static java.util.Objects.requireNonNull;

import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import androidx.annotation.ColorRes;
import androidx.appcompat.app.AppCompatDelegate;

import com.android.managedprovisioning.common.AnimationDynamicColorsHelper.AnimationWrapper;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieFrameInfo;
import com.airbnb.lottie.value.SimpleLottieValueCallback;

/**
 * An {@link AnimationWrapper} implementation for Lottie animations.
 */
public class LottieAnimationWrapper implements AnimationWrapper {
    private final LottieAnimationView mLottieAnimationView;

    public LottieAnimationWrapper(LottieAnimationView lottieAnimationView) {
        mLottieAnimationView = requireNonNull(lottieAnimationView);
    }

    /**
     * Loads the animation.
     *
     * <p>If the {@link LottieAnimationView} does not have a loaded {@link
     * LottieComposition}, it asynchronously waits for it to load and then applies the colors.
     */
    @Override
    public void loadAnimation(Runnable callback) {
        if (mLottieAnimationView.getComposition() != null) {
            callback.run();
        } else {
            mLottieAnimationView.addLottieOnCompositionLoadedListener(
                    lottieComposition -> callback.run());
        }
    }

    @Override
    public void setLayerColor(String layerName, @ColorRes int targetColorRes) {
        KeyPath keyPath = new KeyPath("**", layerName, "**");
        int targetColor = mLottieAnimationView.getContext().getColor(targetColorRes);
        mLottieAnimationView.addValueCallback(keyPath, LottieProperty.COLOR_FILTER,
                new SimpleLottieValueCallback<ColorFilter>() {
                    @Override
                    public ColorFilter getValue(LottieFrameInfo<ColorFilter> frameInfo) {
                        return new PorterDuffColorFilter(targetColor, PorterDuff.Mode.SRC_ATOP);
                    }
                });
    }
}
