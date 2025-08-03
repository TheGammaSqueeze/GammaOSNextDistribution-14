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

package com.android.tv.settings.device.displaysound;

import static android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION;

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.hardware.display.DisplayManager;
import android.hardware.display.HdrConversionMode;
import android.view.Display;

import com.android.tv.settings.R;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper methods for display and sound settings.
 *
 * @hide
 */
public class DisplaySoundUtils {

    /** Gets the match-content dynamic range status */
    public static boolean getMatchContentDynamicRangeStatus(DisplayManager displayManager) {
        return displayManager.getHdrConversionModeSetting().getConversionMode()
                == HdrConversionMode.HDR_CONVERSION_PASSTHROUGH;
    }

    /** Sets the match-content dynamic range status */
    public static void setMatchContentDynamicRangeStatus(DisplayManager displayManager,
            boolean status) {
        HdrConversionMode mode = status
                ? new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_PASSTHROUGH)
                : new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_SYSTEM);

        displayManager.setHdrConversionMode(mode);
    }

    /** Returns if Dolby vision is supported by the device */
    public static boolean isDolbyVisionSupported(Display.Mode[] modes) {
        for (int i = 0; i < modes.length; i++) {
            if (isHdrFormatSupported(modes[i], HDR_TYPE_DOLBY_VISION)) {
                return true;
            }
        }
        return false;
    }

    /** Returns if the passed HDR format is supported by the device in case of a specific mode */
    public static boolean isHdrFormatSupported(Display.Mode mode, int hdrFormat) {
        return Arrays.stream(mode.getSupportedHdrTypes()).anyMatch(
                hdr -> hdr == hdrFormat);
    }

    /**
     * Returns true if the current mode is above 4k30Hz and this is a device that does not support
     * Dolby Vision at resolutions above 4k30Hz
     */
    public static boolean doesCurrentModeNotSupportDvBecauseLimitedTo4k30(Display display) {
        Display.Mode[] supportedModes = display.getSupportedModes();
        Display.Mode currentMode = display.getMode();
        boolean is4k60HzMode = (currentMode.getPhysicalHeight() >= 2160
                || currentMode.getPhysicalWidth() >= 2160)
                && currentMode.getRefreshRate() >= 59.9;

        return is4k60HzMode && isDolbyVisionSupported(supportedModes)
                && !isHdrFormatSupported(currentMode, HDR_TYPE_DOLBY_VISION);
    }

    /**
     * Returns a 1080p 60Hz mode if one is supported by the display, null otherwise
     */
    @Nullable
    public static Display.Mode findMode1080p60(Display display) {
        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() == 1920 && mode.getPhysicalHeight() == 1080
                    && mode.getRefreshRate() >= 59.9) {
                return mode;
            }
        }
        return null;
    }

    static AlertDialog createAlertDialog(Context context, String title, String description,
            OnClickListener onOkClicked, OnClickListener onCancelClicked) {
        return new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(description)
                .setPositiveButton(R.string.resolution_selection_dialog_ok, onOkClicked)
                .setNegativeButton(R.string.resolution_selection_dialog_cancel, onCancelClicked)
                .create();
    }

    static void enableHdrType(DisplayManager displayManager, int hdrType) {
        Set<Integer> disabledHdrTypes = toSet(displayManager.getUserDisabledHdrTypes());
        disabledHdrTypes.remove(hdrType);
        displayManager.setUserDisabledHdrTypes(toArray(disabledHdrTypes));
    }

    static void disableHdrType(DisplayManager displayManager, int hdrType) {
        Set<Integer> disabledHdrTypes = toSet(displayManager.getUserDisabledHdrTypes());
        disabledHdrTypes.add(hdrType);
        displayManager.setUserDisabledHdrTypes(toArray(disabledHdrTypes));
    }

    /** Converts set to int array */
    public static int[] toArray(Set<Integer> set) {
        return set.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Converts int array to set */
    public static Set<Integer> toSet(int[] array) {
        return Arrays.stream(array).boxed().collect(Collectors.toSet());
    }
}
