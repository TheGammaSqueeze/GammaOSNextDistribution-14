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

package android.platform.helpers;

import android.graphics.Point;
import android.graphics.Rect;
import android.platform.spectatio.utils.SpectatioConfigUtil;
import android.platform.spectatio.utils.SpectatioUiUtil;

import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Utility file for seek functions */
public class SeekUtility {
    private static final int SEEK_BOUNDS_BUFFER = 5;

    private static SeekUtility sSeekUtilityInstance;

    public enum SeekLayout {
        VERTICAL,
        HORIZONTAL,
    }

    private static class SeekBarDetails {
        public BySelector mSeekBarSelector;
        public SeekLayout mLayout;
        public Supplier<Integer> mValueSupplier;
    }

    private final SpectatioUiUtil mSpectatioUiUtil;
    private final Map<String, SeekBarDetails> mRegisteredSeekBars;

    private SeekUtility(SpectatioUiUtil spectatioUiUtil) {
        mSpectatioUiUtil = spectatioUiUtil;
        mRegisteredSeekBars = new HashMap<>();
    }

    /**
     * Get the singleton instance of SeekUtility
     *
     * @param spectatioUiUtil Spectatio util to initialize with
     * @return The singleton instance of SeekUtility
     */
    public static SeekUtility getInstance(SpectatioUiUtil spectatioUiUtil) {
        if (sSeekUtilityInstance == null) {
            sSeekUtilityInstance = new SeekUtility(spectatioUiUtil);
        }
        return sSeekUtilityInstance;
    }

    /**
     * Tell SeekUtility that a seekbar exists and how to retrieve the value it corresponds to
     *
     * @param id Any string you choose, which you will use to refer to this seekbar when calling
     *     `seek`
     * @param seekBarConfig What config element holds the seekbar's selector
     * @param layout horizontal or vertical
     * @param valueSupplier How to retrieve the value this seekbar sets
     */
    public void registerSeekBar(
            String id, String seekBarConfig, SeekLayout layout, Supplier<Integer> valueSupplier) {
        if (mRegisteredSeekBars.containsKey(id)) {
            return;
        }

        SeekBarDetails details = new SeekBarDetails();
        details.mSeekBarSelector =
                SpectatioConfigUtil.getInstance().getUiElementFromConfig(seekBarConfig);
        details.mLayout = layout;
        details.mValueSupplier = valueSupplier;

        mRegisteredSeekBars.put(id, details);
    }

    /**
     * Tap on a seekbar, then retrieve the resulting value
     *
     * @param id Which seekbar to tap on -- this is the string you chose when you called register
     * @param targetPercentage Where to tap
     * @return The resulting service value
     */
    public int seek(String id, float targetPercentage) {
        if ((targetPercentage < 0) || (targetPercentage > 1)) {
            throw new IllegalArgumentException(
                    "Seekbar target percentage %f is not between 0 and 1"
                            .formatted(targetPercentage));
        }

        SeekBarDetails details = mRegisteredSeekBars.get(id);
        UiObject2 seekBar = mSpectatioUiUtil.findUiObject(details.mSeekBarSelector);
        if (seekBar == null) {
            throw new IllegalStateException(
                    String.format("Unable to find seekbar using %s.", details.mSeekBarSelector));
        }

        Rect seekBounds = seekBar.getVisibleBounds();
        Point clickLocation = new Point(seekBounds.centerX(), seekBounds.centerY());
        switch (details.mLayout) {
            case VERTICAL:
                int bottom = seekBounds.bottom - SEEK_BOUNDS_BUFFER;
                int top = seekBounds.top + SEEK_BOUNDS_BUFFER;
                clickLocation.y = (int) ((bottom - top) * (1 - targetPercentage) + top);
                break;
            case HORIZONTAL:
                int right = seekBounds.right - SEEK_BOUNDS_BUFFER;
                int left = seekBounds.left + SEEK_BOUNDS_BUFFER;
                clickLocation.x = (int) ((right - left) * targetPercentage + left);
                break;
        }
        mSpectatioUiUtil.clickAndWait(clickLocation);

        return details.mValueSupplier.get();
    }

    /**
     * Get the service value associated with a seekbar
     *
     * @param id Which seekbar to retrieve -- this is the string you chose when you called register
     * @return The value
     */
    public int getValue(String id) {
        return mRegisteredSeekBars.get(id).mValueSupplier.get();
    }
}
