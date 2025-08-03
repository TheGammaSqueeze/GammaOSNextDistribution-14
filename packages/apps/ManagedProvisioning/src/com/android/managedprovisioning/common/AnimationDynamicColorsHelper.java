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

import androidx.annotation.ColorRes;
import androidx.appcompat.app.AppCompatDelegate;

import com.android.managedprovisioning.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper which handles animation dynamic colors.
 *
 * <p>Animations adapt according to the system theme. The colors of specific layers are updated
 * according to a color map.
 *
 * <p>{@link AnimationDynamicColorsHelper#setupAnimationDynamicColors(
 * AnimationDynamicColorsHelper.AnimationWrapper, int)} does the following:
 * <ol>
 *     <li>Iterates through a predefined map of
 *     light-dark mode layer names and colors (or vice-versa if device is in dark-mode)
 *     and sets each layer to the appropriate color</li>
 *     <li>Not all colors map bi-directionally, some colors are asymmetric
 *     as several light colors can map to a single dark color. For these colors the method
 *     iterates through a predefined map of light->dark mode layer names, and <i>only</i>
 *     sets the light mode layers to the appropriate dark color. If the system is in light
 *     mode, the method does nothing for this case.</li>
 * </ol>
 *
 */
public class AnimationDynamicColorsHelper {
    private static final HomogenousBiMap<ColorItem> sLightToDarkColorsBiMap =
            buildLightToDarkColorsBiMap();

    /**
     * A map which only maps colors from light to dark mode.
     *
     * <p>This is necessary in cases when multiple colors in light mode map to a single color to
     * dark mode.
     */
    private static final Map<ColorItem, ColorItem> sLightToDarkOnly = buildLightToDarkColorsMap();

    /**
     * A wrapper over dynamically-colored animations.
     */
    public interface AnimationWrapper {

        /**
         * Loads the animation asynchronously. Must invoke {@code callback} when done.
         */
        void loadAnimation(Runnable callback);
        /**
         * Sets this animation's {@code layerName} layer to color with resource id
         * {@code targetColorRes}.
         */
        void setLayerColor(String layerName, @ColorRes int targetColorRes);

    }
    /**
     * Updates the relevant animation with theme-specific colors.
     *
     * <p>The algorithm is:
     * <ol>
     *     <li>Iterates through a predefined map of
     *     light-dark mode layer names and colors (or vice-versa if device is in dark-mode)
     *     and sets each layer to the appropriate color</li>
     *     <li>Not all colors map bi-directionally, some colors are asymmetric
     *     as several light colors can map to a single dark color. For these colors the method
     *     iterates through a predefined map of light->dark mode layer names, and <i>only</i>
     *     sets the light mode layers to the appropriate dark color. If the system is in light
     *     mode, the method does nothing for this case.</li>
     * </ol>
     *
     * @param animationWrapper - a wrapper over the animation that supports dynamic colors
     * @param nightMode - {@link AppCompatDelegate#MODE_NIGHT_NO} or {@link
     * AppCompatDelegate#MODE_NIGHT_YES}
     */
    public void setupAnimationDynamicColors(AnimationWrapper animationWrapper, int nightMode) {
        animationWrapper.loadAnimation(
                () -> setupAnimationDynamicColorsInternal(animationWrapper, nightMode));
    }

    private void setupAnimationDynamicColorsInternal(
            AnimationWrapper animationWrapper, int nightMode) {
        setupSymmetricLayerColors(animationWrapper, nightMode);
        setupLightToDarkLayerColors(animationWrapper, nightMode);
    }

    private void setupLightToDarkLayerColors(AnimationWrapper animationWrapper, int nightMode) {
        if (nightMode == AppCompatDelegate.MODE_NIGHT_NO) {
            return;
        }
        for (ColorItem lightColorItem : sLightToDarkOnly.keySet()) {
            ColorItem darkColorItem = sLightToDarkOnly.get(lightColorItem);
            animationWrapper.setLayerColor(lightColorItem.mLayerName, darkColorItem.mColorRes);
        }
    }

    private void setupSymmetricLayerColors(AnimationWrapper animationWrapper, int nightMode) {
        Map<ColorItem, ColorItem> colorMap = getColorMap(nightMode);
        for (ColorItem targetColorItem : colorMap.keySet()) {
            ColorItem otherColorItem = colorMap.get(targetColorItem);
            animationWrapper.setLayerColor(otherColorItem.mLayerName, targetColorItem.mColorRes);
        }
    }

    private Map<ColorItem, ColorItem> getColorMap(int nightMode) {
        Map<ColorItem, ColorItem> map;
        if (isDayMode(nightMode)) {
            map = sLightToDarkColorsBiMap.getForwardMap();
        } else {
            map = sLightToDarkColorsBiMap.getReverseMap();
        }
        return map;
    }

    private boolean isDayMode(int nightMode) {
        return nightMode == AppCompatDelegate.MODE_NIGHT_NO;
    }

    private static class ColorItem {

        private final int mColorRes;
        private final String mLayerName;
        ColorItem(int colorRes, String layerName) {
            this.mColorRes = colorRes;
            this.mLayerName = requireNonNull(layerName);
        }

    }

    private static HomogenousBiMap<ColorItem> buildLightToDarkColorsBiMap() {
        HomogenousBiMap<ColorItem> result = new HomogenousBiMap<>();
        result.put(
                new ColorItem(R.color.blue600, ".blue600"),
                new ColorItem(R.color.blue400, ".blue400"));
        result.put(
                new ColorItem(R.color.green600, ".green600"),
                new ColorItem(R.color.green400, ".green400"));
        result.put(
                new ColorItem(R.color.red600, ".red600"),
                new ColorItem(R.color.red400, ".red400"));
        result.put(
                new ColorItem(R.color.yellow600, ".yellow600"),
                new ColorItem(R.color.yellow400, ".yellow400"));
        result.put(
                new ColorItem(R.color.blue400, ".blue400"),
                new ColorItem(R.color.blue100, ".blue100"));
        result.put(
                new ColorItem(R.color.green400, ".green400"),
                new ColorItem(R.color.green100, ".green100"));
        result.put(
                new ColorItem(R.color.red400, ".red400"),
                new ColorItem(R.color.red100, ".red100"));
        result.put(
                new ColorItem(R.color.yellow400, ".yellow400"),
                new ColorItem(R.color.yellow100, ".yellow100"));
        result.put(
                new ColorItem(R.color.blue300, ".blue300"),
                new ColorItem(R.color.blue800, ".blue800"));
        result.put(
                new ColorItem(R.color.grey300, ".grey300"),
                new ColorItem(R.color.grey600, ".grey600"));
        result.put(
                new ColorItem(R.color.grey400, ".grey400"),
                new ColorItem(R.color.grey700, ".grey700"));
        result.put(
                new ColorItem(R.color.grey200, ".grey200"),
                new ColorItem(R.color.grey800, ".grey800"));
        result.put(
                new ColorItem(R.color.white, ".white"),
                new ColorItem(R.color.black, ".black"));
        result.put(
                new ColorItem(R.color.orange600, ".orange600"),
                new ColorItem(R.color.orange300, ".orange300"));
        result.put(
                new ColorItem(R.color.pink600, ".pink600"),
                new ColorItem(R.color.pink300, ".pink300"));
        result.put(
                new ColorItem(R.color.purple600, ".purple600"),
                new ColorItem(R.color.purple300, ".purple300"));
        result.put(
                new ColorItem(R.color.cyan600, ".cyan600"),
                new ColorItem(R.color.cyan300, ".cyan300"));
        result.put(
                new ColorItem(R.color.orange400, ".orange400"),
                new ColorItem(R.color.orange100, ".orange100"));
        result.put(
                new ColorItem(R.color.pink400, ".pink400"),
                new ColorItem(R.color.pink100, ".pink100"));
        result.put(
                new ColorItem(R.color.cyan400, ".cyan400"),
                new ColorItem(R.color.cyan100, ".cyan100"));
        return result;
    }

    private static Map<ColorItem, ColorItem> buildLightToDarkColorsMap() {
        Map<ColorItem, ColorItem> result = new HashMap<>();
        result.put(
                new ColorItem(R.color.blue50, ".blue50"),
                new ColorItem(R.color.grey900, ".grey900"));
        result.put(
                new ColorItem(R.color.green50, ".green50"),
                new ColorItem(R.color.grey900, ".grey900"));
        result.put(
                new ColorItem(R.color.red50, ".red50"),
                new ColorItem(R.color.grey900, ".grey900"));
        result.put(
                new ColorItem(R.color.yellow50, ".yellow50"),
                new ColorItem(R.color.grey900, ".grey900"));
        result.put(
                new ColorItem(R.color.purple400, ".purple400"),
                new ColorItem(R.color.purple800, ".purple800"));
        result.put(
                new ColorItem(R.color.purple300, ".purple300"),
                new ColorItem(R.color.purple800, ".purple800"));
        return result;
    }
}
