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

package com.android.cts.graphics.displaymode;

import android.Manifest;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.Window;
import android.view.WindowManager;

import static org.junit.Assume.assumeTrue;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * Tests for the behaviour of display mode APIs:
 * {@link Display#setUserPreferredDisplayMode(Display.Mode)} and
 * {@link Display.Mode#clearUserPreferredDisplayMode()}.
 */
@RunWith(AndroidJUnit4.class)
public final class BootDisplayModeTest {
    private static final String TAG = BootDisplayModeTest.class.getSimpleName();
    private DisplayManager mDisplayManager;
    private Display.Mode mInitialUserPreferredMode;

    @Rule
    public ActivityTestRule<BootDisplayModeTestActivity> mActivityRule =
            new ActivityTestRule<>(BootDisplayModeTestActivity.class);

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.MODIFY_USER_PREFERRED_DISPLAY_MODE);

    @Before
    public void setUp() throws Exception {
        final UiDevice uiDevice =
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        uiDevice.wakeUp();
        uiDevice.executeShellCommand("wm dismiss-keyguard");

        mDisplayManager = mActivityRule.getActivity().getSystemService(DisplayManager.class);
        mInitialUserPreferredMode = mDisplayManager.getGlobalUserPreferredDisplayMode();

        assumeTrue("Boot mode should be supported to run this test.",
                SurfaceControl.getBootDisplayModeSupport());
    }

    @After
    public void tearDown() {
        // Restore the original value of settings
        if (mInitialUserPreferredMode == null) {
            mDisplayManager.clearGlobalUserPreferredDisplayMode();
        } else {
            mDisplayManager.setGlobalUserPreferredDisplayMode(mInitialUserPreferredMode);
        }
    }

    @Test
    public void testGetBootDisplayMode() throws Exception {
        mDisplayManager.clearGlobalUserPreferredDisplayMode();
        Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        Display.Mode[] supportedModes = display.getSupportedModes();
        Display.Mode initialDefaultMode = display.getDefaultMode();

        // 3 or more modes are needed to run the test. 1st mode is the initialDefaultMode.
        // 2nd mode is selected as user-pref mode. 3rd mode is chosen as active mode. These 3 modes
        // need to be different to properly verify the mode after reboot.
        assumeTrue("3 or more modes should be supported by display to run the test.",
                supportedModes.length >= 3);

        logMode("initial-default-mode", initialDefaultMode);

        // Set the user preferred mode different from initial default mode
        Display.Mode userPreferredMode = null;
        for (Display.Mode mode : supportedModes) {
            if (mode.getModeId() != initialDefaultMode.getModeId()) {
                mDisplayManager.setGlobalUserPreferredDisplayMode(mode);
                waitUntil(display,
                        display1 -> display.getDefaultMode().getModeId() == mode.getModeId(),
                        Duration.ofSeconds(5));
                userPreferredMode = mode;
                break;
            }
        }
        logMode("user-preferred-mode", userPreferredMode);

        // Set the active mode different from initial default mode and user pref mode
        Display.Mode activeMode = null;
        for (Display.Mode mode : supportedModes) {
            if (mode.getModeId() != initialDefaultMode.getModeId()
                    && mode.getModeId() != userPreferredMode.getModeId()) {
                setMode(mode);
                activeMode = mode;
                break;
            }
        }
        logMode("active-mode", activeMode);
    }

    @Test
    public void testClearBootDisplayMode() throws Exception {
        mDisplayManager.clearGlobalUserPreferredDisplayMode();
        Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        Display.Mode[] supportedModes = display.getSupportedModes();

        Display.Mode initialDefaultMode = display.getDefaultMode();
        logMode("initial-default-mode", initialDefaultMode);

        Display.Mode systemPreferredMode = display.getSystemPreferredDisplayMode();
        logMode("system-preferred-mode", systemPreferredMode);

        // 3 or more modes are needed to run the test. 1st mode is the initialDefaultMode.
        // 2nd mode system preferred mode. A mode different from these is selected as user preferred
        // mode, to properly verify the mode after reboot.
        assumeTrue("3 or more modes should be supported by display to run the test.",
                supportedModes.length >= 3);

        // Set the user preferred mode different from initial default mode, and system pref mode
        Display.Mode userPreferredMode = null;
        for (Display.Mode mode : supportedModes) {
            if (mode.getModeId() != initialDefaultMode.getModeId()
                    && mode.getModeId() != systemPreferredMode
                    .getModeId()) {
                mDisplayManager.setGlobalUserPreferredDisplayMode(mode);
                waitUntil(display,
                        display1 -> display.getDefaultMode().getModeId() == mode.getModeId(),
                        Duration.ofSeconds(5));
                userPreferredMode = mode;
                break;
            }
        }
        logMode("user-preferred-mode", userPreferredMode);

        // Clear the user preferred mode
        mDisplayManager.clearGlobalUserPreferredDisplayMode();
        Display.Mode finalUserPreferredMode = userPreferredMode;
        waitUntil(display,
                display1 -> display.getDefaultMode().getModeId()
                        != finalUserPreferredMode.getModeId(),
                Duration.ofSeconds(5));
    }


    private void setMode(Display.Mode mode) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            Window window = mActivityRule.getActivity().getWindow();
            WindowManager.LayoutParams params = window.getAttributes();
            params.preferredDisplayModeId = mode.getModeId();
            window.setAttributes(params);
        });
    }

    private void waitUntil(Display display, Predicate<Display> pred, Duration maxWait)
            throws Exception {
        final int id = display.getDisplayId();
        final Lock lock = new ReentrantLock();
        final Condition displayChanged = lock.newCondition();
        DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId != id) {
                    return;
                }
                lock.lock();
                try {
                    displayChanged.signal();
                } finally {
                    lock.unlock();
                }
            }
            @Override
            public void onDisplayAdded(int displayId) {}
            @Override
            public void onDisplayRemoved(int displayId) {}
        };
        Handler handler = new Handler(Looper.getMainLooper());
        mDisplayManager.registerDisplayListener(listener, handler);
        long remainingNanos = maxWait.toNanos();
        lock.lock();
        try {
            while (!pred.test(display)) {
                if (remainingNanos <= 0L) {
                    throw new TimeoutException();
                }
                remainingNanos = displayChanged.awaitNanos(remainingNanos);
            }
        } finally {
            lock.unlock();
        }
    }

    private void logMode(String modeType, Display.Mode mode) {
        if (mode != null) {
            Log.i(TAG, modeType + ": " + mode.getPhysicalWidth() + " " + mode.getPhysicalHeight()
                    + " " + mode.getRefreshRate());
        }
    }
}
