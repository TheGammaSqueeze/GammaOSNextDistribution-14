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

package android.display.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.view.Display;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.DisplayUtil;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class DefaultDisplayModeTest {
    private final static int DISPLAY_CHANGE_TIMEOUT_SECS = 3;

    private DisplayManager mDisplayManager;
    private Display mDefaultDisplay;
    private Display.Mode mOriginalGlobalDisplayModeSettings;
    private Display.Mode mOriginalDisplaySpecificModeSettings;

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.MODIFY_USER_PREFERRED_DISPLAY_MODE,
            Manifest.permission.HDMI_CEC);

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        assumeTrue("Need an Android TV device to run this test.", FeatureUtil.isTV());
        assertTrue("Physical display is expected.", DisplayUtil.isDisplayConnected(context)
                || MediaUtils.onCuttlefish());

        mDisplayManager = context.getSystemService(DisplayManager.class);
        mDefaultDisplay = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        cacheOriginalUserPreferredModeSetting();
        mDisplayManager.clearGlobalUserPreferredDisplayMode();
        mDefaultDisplay.clearUserPreferredDisplayMode();
    }

    @After
    public void tearDown() throws Exception {
        restoreOriginalDisplayModeSettings();
    }

    @Test
    public void testSetUserPreferredDisplayModeThrowsExceptionWithInvalidMode() {
        assertThrows(
                "The mode is invalid. Width, height and refresh rate should be positive.",
                IllegalArgumentException.class,
                () -> mDisplayManager.setGlobalUserPreferredDisplayMode(
                        new Display.Mode(-1, 1080, 120.0f)));

        assertThrows(
                "The mode is invalid. Width, height and refresh rate should be positive.",
                IllegalArgumentException.class,
                () -> mDisplayManager.setGlobalUserPreferredDisplayMode(
                        new Display.Mode(720, 1080, 0.0f)));
    }

    @Test
    public void testSetAndClearUserPreferredDisplayModeGeneratesDisplayChangedEvents()
            throws Exception {
        Display.Mode[] modes = mDefaultDisplay.getSupportedModes();
        assumeTrue("Need two or more display modes to exercise switching.", modes.length > 1);

        // Test set
        Display.Mode initialDefaultMode = mDefaultDisplay.getDefaultMode();

        Display.Mode newDefaultMode = findNonDefaultMode(mDefaultDisplay);
        assertNotNull(newDefaultMode);
        final CountDownLatch setUserPrefModeSignal = new CountDownLatch(1);
        DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {}

            @Override
            public void onDisplayRemoved(int displayId) {}

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId != mDefaultDisplay.getDisplayId()) {
                    return;
                }
                if (newDefaultMode.getModeId() == mDefaultDisplay.getDefaultMode().getModeId()) {
                    setUserPrefModeSignal.countDown();
                }
            }
        };
        Handler handler = new Handler(Looper.getMainLooper());
        mDisplayManager.registerDisplayListener(listener, handler);
        try {
            mDisplayManager.setGlobalUserPreferredDisplayMode(newDefaultMode);
            // Wait until the display change is effective.
            assertTrue(setUserPrefModeSignal.await(DISPLAY_CHANGE_TIMEOUT_SECS, TimeUnit.SECONDS));
        } finally {
            mDisplayManager.unregisterDisplayListener(listener);
        }

        // Test clear
        final CountDownLatch clearUserPrefModeSignal = new CountDownLatch(1);
        listener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {}

            @Override
            public void onDisplayRemoved(int displayId) {}

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId != mDefaultDisplay.getDisplayId()) {
                    return;
                }
                if (initialDefaultMode.getModeId()
                        == mDefaultDisplay.getDefaultMode().getModeId()) {
                    clearUserPrefModeSignal.countDown();
                }
            }
        };
        mDisplayManager.registerDisplayListener(listener, handler);
        try {
            mDisplayManager.clearGlobalUserPreferredDisplayMode();
            // Wait until the display change is effective.
            assertTrue(clearUserPrefModeSignal.await(DISPLAY_CHANGE_TIMEOUT_SECS,
                    TimeUnit.SECONDS));
        } finally {
            mDisplayManager.unregisterDisplayListener(listener);
        }
    }

    @Test
    public void
            testSetAndClearUserPreferredDisplayModeForSpecificDisplayGeneratesDisplayChangedEvents()
            throws Exception {
        Display.Mode[] modes = mDefaultDisplay.getSupportedModes();
        assumeTrue("Need two or more display modes to exercise switching.", modes.length > 1);

        // Test set
        Display.Mode initialDefaultMode = mDefaultDisplay.getDefaultMode();

        Display.Mode newDefaultMode = findNonDefaultMode(mDefaultDisplay);
        assertNotNull(newDefaultMode);
        final CountDownLatch setUserPrefModeSignal = new CountDownLatch(1);
        DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {}

            @Override
            public void onDisplayRemoved(int displayId) {}

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId != mDefaultDisplay.getDisplayId()) {
                    return;
                }
                if (newDefaultMode.getModeId()
                        == mDefaultDisplay.getDefaultMode().getModeId()) {
                    setUserPrefModeSignal.countDown();
                }
            }
        };
        Handler handler = new Handler(Looper.getMainLooper());
        mDisplayManager.registerDisplayListener(listener, handler);
        try {
            mDefaultDisplay.setUserPreferredDisplayMode(newDefaultMode);
            // Wait until the display change is effective.
            assertTrue(setUserPrefModeSignal.await(DISPLAY_CHANGE_TIMEOUT_SECS, TimeUnit.SECONDS));
        } finally {
            mDisplayManager.unregisterDisplayListener(listener);
        }

        // Test clear
        final CountDownLatch clearUserPrefModeSignal = new CountDownLatch(1);
        listener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {}

            @Override
            public void onDisplayRemoved(int displayId) {}

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId != mDefaultDisplay.getDisplayId()) {
                    return;
                }
                if (initialDefaultMode.getModeId()
                        == mDefaultDisplay.getDefaultMode().getModeId()) {
                    clearUserPrefModeSignal.countDown();
                }
            }
        };
        mDisplayManager.registerDisplayListener(listener, handler);
        try {
            mDefaultDisplay.clearUserPreferredDisplayMode();
            // Wait until the display change is effective.
            assertTrue(clearUserPrefModeSignal.await(DISPLAY_CHANGE_TIMEOUT_SECS,
                    TimeUnit.SECONDS));
        } finally {
            mDisplayManager.unregisterDisplayListener(listener);
        }
    }

    @Test
    public void testSetUserPreferredDisplayModeForSpecificDisplay() {
        Display.Mode[] modes = mDefaultDisplay.getSupportedModes();
        assumeTrue("Need two or more display modes to exercise switching.", modes.length > 1);

        // Set a display mode which is different from default display mode
        Display.Mode newDefaultMode = findNonDefaultMode(mDefaultDisplay);
        assertNotNull(newDefaultMode);
        mDefaultDisplay.setUserPreferredDisplayMode(newDefaultMode);
        assertTrue(mDefaultDisplay.getUserPreferredDisplayMode()
                .matches(newDefaultMode.getPhysicalWidth(),
                        newDefaultMode.getPhysicalHeight(),
                        newDefaultMode.getRefreshRate()));

        mDefaultDisplay.clearUserPreferredDisplayMode();
        assertNull(mDefaultDisplay.getUserPreferredDisplayMode());
    }

    @Test
    public void testSetUserPreferredRefreshRateForSpecificDisplay() {
        Display.Mode[] modes = mDefaultDisplay.getSupportedModes();
        assumeTrue("Need two or more display modes to exercise switching.", modes.length > 1);

        // Set a refresh rate which is different from default display refresh rate
        float refreshRate = findNonDefaultRefreshRate(mDefaultDisplay);
        assumeTrue("Need two or more refresh rates to exercise switching.", refreshRate != 0.0f);

        mDefaultDisplay.setUserPreferredDisplayMode(
                new Display.Mode.Builder().setRefreshRate(refreshRate).build());
        assertNotNull(mDefaultDisplay.getUserPreferredDisplayMode());
        assertEquals(
                refreshRate,
                mDefaultDisplay.getUserPreferredDisplayMode().getRefreshRate(),
                0.00001 /* delta */);

        mDefaultDisplay.clearUserPreferredDisplayMode();
        assertNull(mDefaultDisplay.getUserPreferredDisplayMode());
    }

    @Test
    public void testSetUserPreferredResolutionForSpecificDisplay() {
        Display.Mode[] modes = mDefaultDisplay.getSupportedModes();
        assumeTrue("Need two or more display modes to exercise switching.", modes.length > 1);

        // Set a refresh rate which is different from default display refresh rate
        Point resolution = findNonDefaultResolution(mDefaultDisplay);
        assumeTrue("Need two or more resolutions to exercise switching.",
                resolution.x != -1 && resolution.y != -1);

        mDefaultDisplay.setUserPreferredDisplayMode(
                new Display.Mode.Builder().setResolution(resolution.x, resolution.y).build());
        assertNotNull(mDefaultDisplay.getUserPreferredDisplayMode());
        assertEquals(resolution.x,
                mDefaultDisplay.getUserPreferredDisplayMode().getPhysicalWidth());
        assertEquals(resolution.y,
                mDefaultDisplay.getUserPreferredDisplayMode().getPhysicalHeight());

        mDefaultDisplay.clearUserPreferredDisplayMode();
        assertNull(mDefaultDisplay.getUserPreferredDisplayMode());
    }

    @Test
    public void testGetUserPreferredDisplayMode() {
        Display.Mode[] modes = mDefaultDisplay.getSupportedModes();
        assumeTrue("Need two or more display modes to exercise switching.", modes.length > 1);

        // Set a display mode which is different from default display mode
        Display.Mode newDefaultMode = findNonDefaultMode(mDefaultDisplay);
        assertNotNull(newDefaultMode);
        mDisplayManager.setGlobalUserPreferredDisplayMode(newDefaultMode);
        assertTrue(mDisplayManager.getGlobalUserPreferredDisplayMode()
                .matches(newDefaultMode.getPhysicalWidth(),
                        newDefaultMode.getPhysicalHeight(),
                        newDefaultMode.getRefreshRate()));

        mDisplayManager.clearGlobalUserPreferredDisplayMode();
        assertNull(mDisplayManager.getGlobalUserPreferredDisplayMode());
    }

    private void cacheOriginalUserPreferredModeSetting() {
        mOriginalGlobalDisplayModeSettings =
                mDisplayManager.getGlobalUserPreferredDisplayMode();
        mOriginalDisplaySpecificModeSettings = mDefaultDisplay.getUserPreferredDisplayMode();
    }

    private void restoreOriginalDisplayModeSettings() {
        // mDisplayManager can be null if the test assumptions if setUp have failed.
        if (mDisplayManager == null) {
            return;
        }
        if (mOriginalGlobalDisplayModeSettings == null) {
            mDisplayManager.clearGlobalUserPreferredDisplayMode();
        } else {
            mDisplayManager.setGlobalUserPreferredDisplayMode(mOriginalGlobalDisplayModeSettings);
        }
        if (mOriginalDisplaySpecificModeSettings == null) {
            mDefaultDisplay.clearUserPreferredDisplayMode();
        } else {
            mDefaultDisplay.setUserPreferredDisplayMode(mOriginalDisplaySpecificModeSettings);
        }
    }

    private Display.Mode findNonDefaultMode(Display display) {
        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getModeId() != display.getDefaultMode().getModeId()) {
                return mode;
            }
        }
        return null;
    }

    private float findNonDefaultRefreshRate(Display display) {
        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getRefreshRate() != display.getDefaultMode().getRefreshRate()) {
                return mode.getRefreshRate();
            }
        }
        return 0.0f;
    }

    private Point findNonDefaultResolution(Display display) {
        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() != display.getDefaultMode().getPhysicalWidth()
                    || mode.getPhysicalHeight() != display.getDefaultMode().getPhysicalHeight()) {
                return new Point(mode.getPhysicalWidth(), mode.getPhysicalHeight());
            }
        }
        return new Point(-1, -1);
    }

    private void waitUntil(Display display, Predicate<Display> pred, Duration maxWait)
            throws InterruptedException {
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
                    return;
                }
                remainingNanos = displayChanged.awaitNanos(remainingNanos);
            }
        } finally {
            lock.unlock();
        }
    }
}
