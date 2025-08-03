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

package com.android.tv.settings.util;

import static java.math.RoundingMode.HALF_UP;

import android.content.Context;
import android.icu.number.LocalizedNumberFormatter;
import android.icu.number.NumberFormatter;
import android.view.Display;

import com.android.tv.settings.R;

import java.util.Locale;


/** This utility class for Resolution Setting **/
public class ResolutionSelectionUtils {

    /**
     * Returns the refresh rate converted to a string in the local language. If the refresh rate has
     * only 0s after the floating point, they are removed.
     * The unit "Hz" is added to end of refresh rate.
     */
    public static String getRefreshRateString(float refreshRate) {
        LocalizedNumberFormatter localizedNumberFormatter = NumberFormatter.with().roundingMode(
                HALF_UP).locale(Locale.getDefault());
        double roundedRefreshRate = Math.round(refreshRate * 100.0f) / 100.0f;
        if (roundedRefreshRate % 1 == 0) {
            return localizedNumberFormatter.format(roundedRefreshRate).toString();
        } else {
            return String.format(Locale.getDefault(), "%.2f",
                    localizedNumberFormatter.format(roundedRefreshRate).toBigDecimal());
        }
    }

    /**
     * Returns the resolution converted to a string. The unit "p" is added to end of refresh rate.
     * If the resolution in 2160p, the string returned is "4k".
     */
    public static String getResolutionString(int width, int height) {
        int resolution = Math.min(width, height);
        if (resolution == 2160) {
            return "4k";
        }
        return resolution + "p";
    }

    /**
     * Returns the {@link Display.Mode} converted to a string.
     * Format: Resolution + "p" + RefreshRate + "Hz"
     */
    public static String modeToString(Display.Mode mode, Context context) {
        if (mode == null) {
            return context.getString(R.string.resolution_selection_auto_title);
        }
        final String modeString = context.getString(R.string.resolution_display_mode,
                ResolutionSelectionUtils.getResolutionString(
                        mode.getPhysicalWidth(), mode.getPhysicalHeight()),
                ResolutionSelectionUtils.getRefreshRateString(mode.getRefreshRate()));
        return modeString;
    }

    /**
     * Returns the resolution mode converted to a string in the local language.
     * Format: width + " x " + height
     */
    public static String getResolutionSummary(int physicalWidth, int physicalHeight) {
        LocalizedNumberFormatter localizedNumberFormatter = NumberFormatter.with().locale(
                Locale.getDefault());
        return localizedNumberFormatter.format(physicalWidth).toString() + " x "
                + localizedNumberFormatter.format(physicalHeight).toString();
    }
}
