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

import static com.google.common.truth.Truth.assertThat;

import androidx.appcompat.app.AppCompatDelegate;

import com.android.managedprovisioning.R;

import org.junit.Test;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieFrameInfo;
import com.airbnb.lottie.value.SimpleLottieValueCallback;
import androidx.test.InstrumentationRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * A test class that tests {@link AnimationDynamicColorsHelper}.
 */
public class AnimationDynamicColorsHelperTest {
    private static final String DARK_LAYER_ASYMMETRIC = ".grey900";

    private static final Map<String, Integer> LIGHT_LAYERS_TO_DARK_COLORS =
            buildLightLayersToDarkColorsMap();

    private static final Map<String, Integer> DARK_LAYERS_TO_LIGHT_COLORS =
            buildDarkLayersToLightColorsMap();

    private static final Map<String, Integer> LIGHT_LAYERS_TO_DARK_COLORS_ASYMMETRIC =
            buildLightLayersToDarkColorsAsymmetricMap();

    private final AnimationDynamicColorsHelper mAnimationHelper
            = new AnimationDynamicColorsHelper();

    @Test
    public void setupAnimationDynamicColors_lightMode_containsLightColors() {
        Map<String, Integer> layerToColor = new HashMap<>();
        mAnimationHelper.setupAnimationDynamicColors(
                createAnimationWrapper(layerToColor),
                AppCompatDelegate.MODE_NIGHT_NO);

        assertThat(layerToColor).containsAtLeastEntriesIn(DARK_LAYERS_TO_LIGHT_COLORS);
    }

    @Test
    public void setupAnimationDynamicColors_darkMode_setsSymmetricDarkColors() {
        Map<String, Integer> layerToColor = new HashMap<>();
        mAnimationHelper.setupAnimationDynamicColors(
                createAnimationWrapper(layerToColor),
                AppCompatDelegate.MODE_NIGHT_YES);


        assertThat(layerToColor).containsAtLeastEntriesIn(LIGHT_LAYERS_TO_DARK_COLORS);
    }

    @Test
    public void setupAnimationDynamicColors_darkMode_setsAsymmetricColors() {
        Map<String, Integer> layerToColor = new HashMap<>();
        mAnimationHelper.setupAnimationDynamicColors(
                createAnimationWrapper(layerToColor),
                AppCompatDelegate.MODE_NIGHT_YES);

        assertThat(layerToColor).containsAtLeastEntriesIn(LIGHT_LAYERS_TO_DARK_COLORS_ASYMMETRIC);
    }

    @Test
    public void setupAnimationDynamicColors_lightMode_doesNotSetAsymmetricColors() {
        Map<String, Integer> layerToColor =
                getUpdatedLayersForMode(AppCompatDelegate.MODE_NIGHT_NO);

        assertThat(layerToColor).doesNotContainKey(DARK_LAYER_ASYMMETRIC);
    }

    private Map<String, Integer> getUpdatedLayersForMode(int nightMode) {
        Map<String, Integer> layerToColor = new HashMap<>();
        mAnimationHelper.setupAnimationDynamicColors(
                createAnimationWrapper(layerToColor),
                nightMode);
        return layerToColor;
    }

    private AnimationDynamicColorsHelper.AnimationWrapper createAnimationWrapper(
            Map<String, Integer> layerToColor) {
        return new AnimationDynamicColorsHelper.AnimationWrapper() {
            @Override
            public void loadAnimation(Runnable callback) {
                callback.run();
            }

            @Override
            public void setLayerColor(String layerName, int targetColorRes) {
                layerToColor.put(layerName, targetColorRes);
            }
        };
    }

    private static Map<String, Integer> buildLightLayersToDarkColorsMap() {
        Map<String, Integer> result = new HashMap<>();
        result.put(".blue600", R.color.blue400);
        return result;
    }

    private static Map<String, Integer> buildDarkLayersToLightColorsMap() {
        Map<String, Integer> result = new HashMap<>();
        result.put(".grey600", R.color.grey300);
        return result;
    }

    private static Map<String, Integer> buildLightLayersToDarkColorsAsymmetricMap() {
        Map<String, Integer> result = new HashMap<>();
        result.put(".blue50", R.color.grey900);
        return result;
    }
}
