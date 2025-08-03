/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.IOException;

/**
 * Helper class to enable gesture navigation on the device.
 */
public class GestureNavSwitchHelper {
    private static final String NAV_BAR_INTERACTION_MODE_RES_NAME = "config_navBarInteractionMode";
    private static final int NAV_BAR_MODE_GESTURAL = 2;

    private static final String GESTURAL_OVERLAY_NAME =
            "com.android.internal.systemui.navbar.gestural";

    private static final int WAIT_OVERLAY_TIMEOUT = 3000;
    private static final int PEEK_INTERVAL = 200;

    private final Instrumentation mInstrumentation;
    private final UiDevice mDevice;
    private final WindowManager mWindowManager;
    // This object has tried to enable gesture navigation but failed.
    private boolean mTriedEnableButFail;

    /**
     * Initialize all options in System Gesture.
     */
    public GestureNavSwitchHelper() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(mInstrumentation);
        final Context context = mInstrumentation.getTargetContext();

        mWindowManager = context.getSystemService(WindowManager.class);
    }

    private boolean hasSystemGestureFeature() {
        if (!containsNavigationBar()) {
            return false;
        }
        Context context = mInstrumentation.getTargetContext();
        final PackageManager pm = context.getPackageManager();

        // No bars on embedded devices.
        // No bars on TVs and watches.
        return !(pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                || pm.hasSystemFeature(PackageManager.FEATURE_EMBEDDED)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
    }

    private void insetsToRect(Insets insets, Rect outRect) {
        outRect.set(insets.left, insets.top, insets.right, insets.bottom);
    }

    /**
     * Attempt to enable gesture navigation mode.
     * @return true if gesture navigation mode is enabled.
     */
    public boolean enableGestureNavigationMode() {
        // skip retry
        if (mTriedEnableButFail) {
            return false;
        }
        if (!hasSystemGestureFeature()) {
            return false;
        }
        if (isGestureMode()) {
            return true;
        }
        enableGestureNav();
        final boolean success = isGestureMode();
        mTriedEnableButFail = !success;
        return success;
    }

    private void enableGestureNav() {
        if (!hasSystemGestureFeature()) {
            return;
        }
        try {
            if (!mDevice.executeShellCommand("cmd overlay list").contains(GESTURAL_OVERLAY_NAME)) {
                return;
            }
        } catch (IOException ignore) {
            //
        }
        monitorOverlayChange(() -> {
            try {
                mDevice.executeShellCommand("cmd overlay enable " + GESTURAL_OVERLAY_NAME);
            } catch (IOException e) {
                // Do nothing
            }
        });
    }

    private void getCurrentInsetsSize(Rect outSize) {
        outSize.setEmpty();
        if (mWindowManager != null) {
            WindowInsets insets = mWindowManager.getCurrentWindowMetrics().getWindowInsets();
            Insets navInsets = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars());
            insetsToRect(navInsets, outSize);
        }
    }

    // Monitoring the navigation bar insets size change as a hint of gesture mode has changed, not
    // the best option for every kind of devices. We can consider listening OVERLAY_CHANGED
    // broadcast in U.
    private void monitorOverlayChange(Runnable overlayChangeCommand) {
        if (mWindowManager != null) {
            final Rect initSize = new Rect();
            getCurrentInsetsSize(initSize);
            overlayChangeCommand.run();
            // wait for insets size change
            final Rect peekSize = new Rect();
            int t = 0;
            while (t < WAIT_OVERLAY_TIMEOUT) {
                SystemClock.sleep(PEEK_INTERVAL);
                t += PEEK_INTERVAL;
                getCurrentInsetsSize(peekSize);
                if (!peekSize.equals(initSize)) {
                    break;
                }
            }
        } else {
            // shouldn't happen
            overlayChangeCommand.run();
            SystemClock.sleep(WAIT_OVERLAY_TIMEOUT);
        }
    }

    private int getCurrentNavMode() {
        final Context context  = mInstrumentation.getTargetContext();
        final Resources res = context.getResources();
        int naviModeId = res.getIdentifier(NAV_BAR_INTERACTION_MODE_RES_NAME, "integer", "android");
        return res.getInteger(naviModeId);
    }

    private boolean containsNavigationBar() {
        final Rect peekSize = new Rect();
        getCurrentInsetsSize(peekSize);
        return peekSize.height() != 0;
    }

    /**
     * @return Whether gesture navigation mode is enabled.
     */
    public boolean isGestureMode() {
        if (!containsNavigationBar()) {
            return false;
        }
        final int naviMode = getCurrentNavMode();
        return naviMode == NAV_BAR_MODE_GESTURAL;
    }
}
