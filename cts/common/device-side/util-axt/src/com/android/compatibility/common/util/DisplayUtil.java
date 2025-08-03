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

package com.android.compatibility.common.util;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.hdmi.HdmiControlManager;
import android.view.Display;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DisplayUtil {

    private static final float REFRESH_RATE_TOLERANCE = 0.01f;

    /**
     * Returns if a physical display is connected to DUT. The method may return a false positive,
     * but no false negative.
     */
    public static boolean isDisplayConnected(Context context) throws Exception {
        // DisplayManager will return a display even if there is no connected display.
        // For that reason we use HdmiControlManager to check if there's something connected
        // to the HDMI port.
        HdmiControlManager cecManager = context.getSystemService(HdmiControlManager.class);

        if (cecManager == null) {
            // CEC is not available. Can't do anything more, so assume that there is a display.
            return true;
        }

        if (cecManager.getPlaybackClient() == null) {
            // The device is not HDMI playback device (e.g. set-top box), so we assume it has
            // a built-in display.
            return true;
        }

        CountDownLatch notifyLatch = new CountDownLatch(1);
        cecManager.addHotplugEventListener(event -> {
            // TODO(b/189837682): Check if the port is HDMI out
            if (event.isConnected()) {
                notifyLatch.countDown();
            }
        });

        return notifyLatch.await(3, TimeUnit.SECONDS);
    }

    public static boolean isModeSwitchSeamless(Display.Mode from, Display.Mode to) {
        if (from.getModeId() == to.getModeId()) {
            return true;
        }

        if (from.getPhysicalHeight() != to.getPhysicalHeight()
                || from.getPhysicalWidth() != to.getPhysicalWidth()) {
            return false;
        }

        for (float alternativeRefreshRate : from.getAlternativeRefreshRates()) {
            if (Math.abs(alternativeRefreshRate - to.getRefreshRate()) <  REFRESH_RATE_TOLERANCE) {
                return true;
            }
        }

        return false;
    }

    public static int getRefreshRateSwitchingType(DisplayManager displayManager) {
        return toSwitchingType(displayManager.getMatchContentFrameRateUserPreference());
    }

    private static int toSwitchingType(int matchContentFrameRateUserPreference) {
        switch (matchContentFrameRateUserPreference) {
            case DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER:
                return DisplayManager.SWITCHING_TYPE_NONE;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY:
                return DisplayManager.SWITCHING_TYPE_WITHIN_GROUPS;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS:
                return DisplayManager.SWITCHING_TYPE_ACROSS_AND_WITHIN_GROUPS;
            default:
                return -1;
        }
    }
}
