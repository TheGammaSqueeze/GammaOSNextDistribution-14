/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static android.content.pm.PackageManager.FEATURE_LEANBACK;
import static android.hardware.flags.Flags.FLAG_OVERLAYPROPERTIES_CLASS_API;
import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.app.Presentation;
import android.app.UiAutomation;
import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.OverlayProperties;
import android.hardware.display.DeviceProductInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.server.wm.WakeUpAndUnlockRule;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Display.HdrCapabilities;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.DisplayStateManager;
import com.android.compatibility.common.util.DisplayUtil;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.PropertyUtil;
import com.android.compatibility.common.util.StateKeeperRule;

import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class DisplayTest extends TestBase {
    private static final String TAG = "DisplayTest";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    // The CTS package brings up an overlay display on the target device (see AndroidTest.xml).
    // The overlay display parameters must match the ones defined there which are
    // 181x161/214 (wxh/dpi).  It only matters that these values are different from any real
    // display.

    private static final int SECONDARY_DISPLAY_WIDTH = 181;
    private static final int SECONDARY_DISPLAY_HEIGHT = 161;
    private static final int SECONDARY_DISPLAY_DPI = 214;
    private static final float SCALE_DENSITY_LOWER_BOUND =
            (float)(SECONDARY_DISPLAY_DPI - 1) / DisplayMetrics.DENSITY_DEFAULT;
    private static final float SCALE_DENSITY_UPPER_BOUND =
            (float)(SECONDARY_DISPLAY_DPI + 1) / DisplayMetrics.DENSITY_DEFAULT;
    // Matches com.android.internal.R.string.display_manager_overlay_display_name.
    private static final String OVERLAY_DISPLAY_NAME_PREFIX = "Overlay #";

    private static final int BRIGHTNESS_MAX = 255;
    private static final float REFRESH_RATE_TOLERANCE = 0.001f;

    private DisplayManager mDisplayManager;
    private WindowManager mWindowManager;
    private UiModeManager mUiModeManager;
    private Context mContext;
    private ColorSpace[] mSupportedWideGamuts;
    private Display mDefaultDisplay;
    private int mInitialRefreshRateSwitchingType;

    // To test display mode switches.
    private TestPresentation mPresentation;

    private UiAutomation mUiAutomation;

    private static class DisplayModeState {
        public final int mHeight;
        public final int mWidth;
        public final float mRefreshRate;
        public final int[] mSupportedHdrTypes;

        DisplayModeState(Display display) {
            mHeight = display.getMode().getPhysicalHeight();
            mWidth = display.getMode().getPhysicalWidth();
            mSupportedHdrTypes = display.getMode().getSupportedHdrTypes();

            // Starting Android S the, the platform might throttle down
            // applications frame rate to a divisor of the refresh rate instead if changing the
            // physical display refresh rate. Applications should use
            // {@link android.view.Display#getRefreshRate} to know their frame rate as opposed to
            // {@link android.view.Display.Mode#getRefreshRate} that returns the physical display
            // refresh rate. See
            // {@link com.android.server.display.DisplayManagerService.DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE}
            // for more details.
            mRefreshRate = display.getRefreshRate();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof DisplayModeState)) {
                return false;
            }

            DisplayModeState other = (DisplayModeState) obj;
            return mHeight == other.mHeight
                && mWidth == other.mWidth
                && mRefreshRate == other.mRefreshRate
                && Arrays.equals(mSupportedHdrTypes, other.mSupportedHdrTypes);
        }

        @Override
        public String toString() {
            return new StringBuilder("{")
                    .append("width=").append(mWidth)
                    .append(", height=").append(mHeight)
                    .append(", fps=").append(mRefreshRate)
                    .append(", supportedHdrTypes=").append(Arrays.toString(mSupportedHdrTypes))
                    .append("}")
                    .toString();
        }
    }

    @Rule
    public ActivityTestRule<DisplayTestActivity> mDisplayTestActivity =
            new ActivityTestRule<>(
                    DisplayTestActivity.class,
                    false /* initialTouchMode */,
                    false /* launchActivity */);

    @Rule
    public ActivityTestRule<RetainedDisplayTestActivity> mRetainedDisplayTestActivity =
            new ActivityTestRule<>(
                    RetainedDisplayTestActivity.class,
                    false /* initialTouchMode */,
                    false /* launchActivity */);

    @Rule(order = 0)
    public WakeUpAndUnlockRule mWakeUpAndUnlockRule = new WakeUpAndUnlockRule();

    /**
     * This rule adopts the Shell process permissions, needed because OVERRIDE_DISPLAY_MODE_REQUESTS
     * and ACCESS_SURFACE_FLINGER are privileged permission.
     */
    @Rule(order = 1)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.OVERRIDE_DISPLAY_MODE_REQUESTS,
            Manifest.permission.ACCESS_SURFACE_FLINGER,
            Manifest.permission.WRITE_SECURE_SETTINGS,
            Manifest.permission.HDMI_CEC,
            Manifest.permission.MODIFY_REFRESH_RATE_SWITCHING_TYPE,
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 2)
    public StateKeeperRule<DisplayStateManager.DisplayState>
            mDisplayManagerStateKeeper =
            new StateKeeperRule<>(new DisplayStateManager(
                    InstrumentationRegistry.getInstrumentation().getTargetContext()));

    @Before
    public void setUp() throws Exception {
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        mContext = getInstrumentation().getTargetContext();
        assertTrue("Physical display is expected.", DisplayUtil.isDisplayConnected(mContext)
                || MediaUtils.onCuttlefish());

        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mWindowManager = mContext.getSystemService(WindowManager.class);
        mUiModeManager = mContext.getSystemService(UiModeManager.class);
        mDefaultDisplay = mDisplayManager.getDisplay(DEFAULT_DISPLAY);
        mSupportedWideGamuts = mDefaultDisplay.getSupportedWideColorGamut();

        addSecondaryDisplay();
    }

    @After
    public void tearDown() throws InterruptedException {
        if (mDisplayManager != null) {
            mDisplayManager.overrideHdrTypes(DEFAULT_DISPLAY, new int[]{});
        }
        removeSecondaryDisplays();
    }

    private void enableAppOps() {
        StringBuilder cmd = new StringBuilder();
        cmd.append("appops set ");
        cmd.append(InstrumentationRegistry.getInstrumentation().getContext().getPackageName());
        cmd.append(" android:system_alert_window allow");
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(cmd.toString());

        StringBuilder query = new StringBuilder();
        query.append("appops get ");
        query.append(InstrumentationRegistry.getInstrumentation().getContext().getPackageName());
        query.append(" android:system_alert_window");
        String queryStr = query.toString();

        String result = "No operations.";
        while (result.contains("No operations")) {
            ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().executeShellCommand(queryStr);
            InputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
            result = convertStreamToString(inputStream);
        }
    }

    private String convertStreamToString(InputStream is) {
        try (java.util.Scanner s = new Scanner(is).useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }

    /** Check if the display is an overlay display, created by this test. */
    private boolean isSecondaryDisplay(Display display) {
        return display.getType() == Display.TYPE_OVERLAY;
    }

    /** Get the overlay display, created by this test. */
    private Display getSecondaryDisplay(Display[] displays) {
        for (Display display : displays) {
            if (isSecondaryDisplay(display)) {
                return display;
            }
        }
        return null;
    }

    private void addSecondaryDisplay() throws InterruptedException {
        final CountDownLatch signal = new CountDownLatch(1);
        Handler handler = new Handler(Looper.getMainLooper());
        mDisplayManager.registerDisplayListener(new DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                if (getSecondaryDisplay(mDisplayManager.getDisplays()) != null) {
                    signal.countDown();
                }
            }

            @Override
            public void onDisplayRemoved(int displayId) {}

            @Override
            public void onDisplayChanged(int displayId) {}
        }, handler);

        // Add a secondary display
        mUiAutomation.executeShellCommand(
                "settings put global overlay_display_devices 181x161/214|182x162/214");

        // Wait for the secondary display to become available
        assertTrue(signal.await(20, TimeUnit.SECONDS));
    }

    private void removeSecondaryDisplays() throws InterruptedException {
        final CountDownLatch signal = new CountDownLatch(1);
        Handler handler = new Handler(Looper.getMainLooper());
        mDisplayManager.registerDisplayListener(new DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {}

            @Override
            public void onDisplayRemoved(int displayId) {
                if (getSecondaryDisplay(mDisplayManager.getDisplays()) == null) {
                    signal.countDown();
                }
            }

            @Override
            public void onDisplayChanged(int displayId) {}
        }, handler);

        // Remove secondary displays
        mUiAutomation.executeShellCommand("settings delete global overlay_display_devices");

        // Wait for the change to go through
        signal.await(20, TimeUnit.SECONDS);
    }

    /**
     * Verify that the getDisplays method returns both a default and an overlay display.
     */
    @Test
    public void testGetDisplays() {
        Display[] displays = mDisplayManager.getDisplays();
        assertNotNull(displays);
        assertTrue(2 <= displays.length);
        boolean hasDefaultDisplay = false;
        boolean hasSecondaryDisplay = false;
        for (Display display : displays) {
            if (display.getDisplayId() == DEFAULT_DISPLAY) {
                hasDefaultDisplay = true;
            }
            if (isSecondaryDisplay(display)) {
                hasSecondaryDisplay = true;
            }
        }
        assertTrue(hasDefaultDisplay);
        assertTrue(hasSecondaryDisplay);
    }

    @Test
    public void getDisplaysWithInvalidCategory_returnsEmptyArray() {
        Display[] displays = mDisplayManager.getDisplays("InvalidDisplayCategory");

        assertThat(displays).isEmpty();
    }

    /**
     * Verify that the WindowManager returns the default display.
     */
    @Presubmit
    @Test
    public void testDefaultDisplay() {
        assertEquals(DEFAULT_DISPLAY, mWindowManager.getDefaultDisplay().getDisplayId());
    }

    /**
     * Verify default display's HDR capability.
     */
    @Test
    public void testDefaultDisplayHdrCapability() {
        HdrCapabilities cap = mDefaultDisplay.getHdrCapabilities();
        int[] hdrTypes = cap.getSupportedHdrTypes();
        for (int type : hdrTypes) {
            assertTrue(type >= 1 && type <= 4);
        }
        assertFalse(cap.getDesiredMaxLuminance() < -1.0f);
        assertFalse(cap.getDesiredMinLuminance() < -1.0f);
        assertFalse(cap.getDesiredMaxAverageLuminance() < -1.0f);
        assertTrue(cap.getDesiredMinLuminance() <= cap.getDesiredMaxAverageLuminance());
        assertTrue(cap.getDesiredMaxAverageLuminance() <= cap.getDesiredMaxLuminance());
        if (hdrTypes.length > 0) {
            assertTrue(mDefaultDisplay.isHdr());
        } else {
            assertFalse(mDefaultDisplay.isHdr());
        }
    }

    /**
     * Verifies that getHdrCapabilities filters out specified HDR types after
     * setUserDisabledHdrTypes is called and setAreUserDisabledHdrTypes is false.
     */
    @Test
    public void
            testGetHdrCapabilitiesWhenUserDisabledFormatsAreNotAllowedReturnsFilteredHdrTypes()
                    throws Exception {
        overrideHdrTypes();
        waitUntil(
                mDefaultDisplay,
                mDefaultDisplay ->
                        mDefaultDisplay.getHdrCapabilities().getSupportedHdrTypes().length == 4,
                Duration.ofSeconds(5));

        mDisplayManager.setAreUserDisabledHdrTypesAllowed(false);
        int[] emptyUserDisabledFormats = new int[] {};
        mDisplayManager.setUserDisabledHdrTypes(emptyUserDisabledFormats);
        int[] expectedHdrTypes = new int[]{
                HdrCapabilities.HDR_TYPE_DOLBY_VISION, HdrCapabilities.HDR_TYPE_HDR10,
                HdrCapabilities.HDR_TYPE_HLG, HdrCapabilities.HDR_TYPE_HDR10_PLUS};
        assertArrayEquals(expectedHdrTypes,
                mDefaultDisplay.getHdrCapabilities().getSupportedHdrTypes());

        int[] userDisabledHdrTypes =
                {HdrCapabilities.HDR_TYPE_DOLBY_VISION,  HdrCapabilities.HDR_TYPE_HLG};
        mDisplayManager.setUserDisabledHdrTypes(userDisabledHdrTypes);
        expectedHdrTypes = new int[]{
                HdrCapabilities.HDR_TYPE_HDR10,
                HdrCapabilities.HDR_TYPE_HDR10_PLUS};
        assertArrayEquals(expectedHdrTypes,
                mDefaultDisplay.getHdrCapabilities().getSupportedHdrTypes());

        mDisplayManager.setUserDisabledHdrTypes(emptyUserDisabledFormats);
        expectedHdrTypes = new int[]{
                HdrCapabilities.HDR_TYPE_DOLBY_VISION, HdrCapabilities.HDR_TYPE_HDR10,
                HdrCapabilities.HDR_TYPE_HLG, HdrCapabilities.HDR_TYPE_HDR10_PLUS};
        assertArrayEquals(expectedHdrTypes,
                mDefaultDisplay.getHdrCapabilities().getSupportedHdrTypes());
    }

    /**
     * Verifies that getHdrCapabilities doesn't filter out HDR types after
     * setUserDisabledHdrTypes is called and setAreUserDisabledHdrTypes is true.
     */
    @Test
    public void
            testGetHdrCapabilitiesWhenUserDisabledFormatsAreAllowedReturnsNonFilteredHdrTypes()
                    throws Exception {
        overrideHdrTypes();
        waitUntil(
                mDefaultDisplay,
                mDefaultDisplay ->
                        mDefaultDisplay.getHdrCapabilities().getSupportedHdrTypes().length == 4,
                Duration.ofSeconds(5));

        mDisplayManager.setAreUserDisabledHdrTypesAllowed(true);
        int[] userDisabledHdrTypes =
                {HdrCapabilities.HDR_TYPE_DOLBY_VISION,  HdrCapabilities.HDR_TYPE_HLG};
        mDisplayManager.setUserDisabledHdrTypes(userDisabledHdrTypes);
        int[] expectedHdrTypes = new int[]{
                HdrCapabilities.HDR_TYPE_DOLBY_VISION, HdrCapabilities.HDR_TYPE_HDR10,
                HdrCapabilities.HDR_TYPE_HLG, HdrCapabilities.HDR_TYPE_HDR10_PLUS};
        assertArrayEquals(expectedHdrTypes,
                mDefaultDisplay.getHdrCapabilities().getSupportedHdrTypes());

        int[] emptyUserDisabledFormats = {};
        mDisplayManager.setUserDisabledHdrTypes(emptyUserDisabledFormats);
        assertArrayEquals(expectedHdrTypes,
                mDefaultDisplay.getHdrCapabilities().getSupportedHdrTypes());
    }

    /**
     * Verifies that if userDisabledFormats are not allowed, and are modified by
     * setUserDisabledHdrTypes, the setting is persisted in Settings.Global.
     */
    @Test
    public void testSetUserDisabledHdrTypesStoresDisabledFormatsInSettings() throws Exception {
        overrideHdrTypes();
        waitUntil(
                mDefaultDisplay,
                mDefaultDisplay ->
                        mDefaultDisplay.getHdrCapabilities().getSupportedHdrTypes().length == 4,
                Duration.ofSeconds(5));

        mDisplayManager.setAreUserDisabledHdrTypesAllowed(false);
        int[] emptyUserDisabledFormats = {};
        mDisplayManager.setUserDisabledHdrTypes(emptyUserDisabledFormats);

        int[] userDisabledHdrTypes =
                {HdrCapabilities.HDR_TYPE_DOLBY_VISION,  HdrCapabilities.HDR_TYPE_HLG};
        mDisplayManager.setUserDisabledHdrTypes(userDisabledHdrTypes);
        String userDisabledFormatsString =
                Settings.Global.getString(mContext.getContentResolver(),
                        Settings.Global.USER_DISABLED_HDR_FORMATS);
        int[] userDisabledFormats = Arrays.stream(
                TextUtils.split(userDisabledFormatsString, ","))
                .mapToInt(Integer::parseInt).toArray();

        assertEquals(HdrCapabilities.HDR_TYPE_DOLBY_VISION, userDisabledFormats[0]);
        assertEquals(HdrCapabilities.HDR_TYPE_HLG, userDisabledFormats[1]);
    }

    private void overrideHdrTypes() {
        mDisplayManager.overrideHdrTypes(DEFAULT_DISPLAY, new int[]{
                HdrCapabilities.HDR_TYPE_DOLBY_VISION, HdrCapabilities.HDR_TYPE_HDR10,
                HdrCapabilities.HDR_TYPE_HLG, HdrCapabilities.HDR_TYPE_HDR10_PLUS});
        mDisplayManager.setAreUserDisabledHdrTypesAllowed(true);
    }

    private void waitUntil(Display display, Predicate<Display> pred, Duration maxWait)
            throws Exception {
        final int id = display.getDisplayId();
        final Lock lock = new ReentrantLock();
        final Condition displayChanged = lock.newCondition();
        DisplayListener listener = new DisplayListener() {
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

    /**
     * Verify that there is a secondary display.
     */
    @Test
    public void testSecondaryDisplay() {
        Display display = getSecondaryDisplay(mDisplayManager.getDisplays());
        assertNotNull(display);
        assertTrue(DEFAULT_DISPLAY != display.getDisplayId());
    }

    /**
     * Test the properties of the secondary Display.
     */
    @Test
    public void testGetDisplayAttrs() {
        Display display = getSecondaryDisplay(mDisplayManager.getDisplays());

        assertEquals(SECONDARY_DISPLAY_WIDTH, display.getWidth());
        assertEquals(SECONDARY_DISPLAY_HEIGHT, display.getHeight());

        Point outSize = new Point();
        display.getSize(outSize);
        assertEquals(SECONDARY_DISPLAY_WIDTH, outSize.x);
        assertEquals(SECONDARY_DISPLAY_HEIGHT, outSize.y);

        assertEquals(0, display.getOrientation());

        assertEquals(PixelFormat.RGBA_8888, display.getPixelFormat());

        assertTrue(0 < display.getRefreshRate());

        assertTrue(display.getName().contains(OVERLAY_DISPLAY_NAME_PREFIX));

        assertFalse(display.isWideColorGamut());
    }

    /**
     * Test that the getMetrics method fills in correct values.
     */
    @Test
    public void testGetMetrics() {
        testGetMetrics(mDisplayManager);
    }

    /**
     * Tests getting metrics from the Activity context.
     */
    @Test
    public void testActivityContextGetMetrics() {
        final Activity activity = launchActivity(mDisplayTestActivity);
        final DisplayManager dm =
                (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
        testGetMetrics(dm);
    }

    public void testGetMetrics(DisplayManager manager) {
        Display display = getSecondaryDisplay(manager.getDisplays());

        Point outSize = new Point();
        display.getRealSize(outSize);

        DisplayMetrics outMetrics = new DisplayMetrics();
        outMetrics.setToDefaults();
        display.getMetrics(outMetrics);

        assertEquals("Secondary display real size width is unexpected; height: " + outSize.y
                + " name " + display.getName() + " id " + display.getDisplayId()
                + " type " + display.getType(), SECONDARY_DISPLAY_WIDTH, outSize.x);
        assertEquals(SECONDARY_DISPLAY_HEIGHT, outSize.y);

        assertEquals("Secondary display width is unexpected; height: " + outMetrics.heightPixels
                + " name " + display.getName() + " id " + display.getDisplayId()
                + " type " + display.getType(), SECONDARY_DISPLAY_WIDTH, outMetrics.widthPixels);
        assertEquals(SECONDARY_DISPLAY_HEIGHT, outMetrics.heightPixels);

        // The scale is in [0.1, 3], and density is the scale factor.
        assertTrue(SCALE_DENSITY_LOWER_BOUND <= outMetrics.density
                && outMetrics.density <= SCALE_DENSITY_UPPER_BOUND);
        assertTrue(SCALE_DENSITY_LOWER_BOUND <= outMetrics.scaledDensity
                && outMetrics.scaledDensity <= SCALE_DENSITY_UPPER_BOUND);

        assertEquals(SECONDARY_DISPLAY_DPI, outMetrics.densityDpi);
        assertEquals((float)SECONDARY_DISPLAY_DPI, outMetrics.xdpi, 0.0001f);
        assertEquals((float)SECONDARY_DISPLAY_DPI, outMetrics.ydpi, 0.0001f);
    }

    /** Test that the getFlags method returns expected flag bits set for the overlay display. */
    @Test
    public void testFlags() {
        Display display = getSecondaryDisplay(mDisplayManager.getDisplays());

        assertEquals(Display.FLAG_PRESENTATION | Display.FLAG_TRUSTED, display.getFlags());
    }

    /**
     * Test that a mode switch to every reported display mode is successful.
     */
    @Test
    public void testModeSwitchOnPrimaryDisplay() throws Exception {
        Display.Mode[] modes = mDefaultDisplay.getSupportedModes();
        assumeTrue("Need two or more display modes to exercise switching.", modes.length > 1);

        try {
            mDisplayManager.setShouldAlwaysRespectAppRequestedMode(true);
            assertTrue(mDisplayManager.shouldAlwaysRespectAppRequestedMode());
            mInitialRefreshRateSwitchingType =
                    DisplayUtil.getRefreshRateSwitchingType(mDisplayManager);
            mDisplayManager.setRefreshRateSwitchingType(DisplayManager.SWITCHING_TYPE_NONE);

            final DisplayTestActivity activity = launchActivity(mRetainedDisplayTestActivity);

            // Create a deterministically shuffled list of display modes, which ends with the
            // current active mode. We'll switch to the modes in this order. The active mode is last
            // so we don't need an extra mode switch in case the test completes successfully.
            Display.Mode activeMode = mDefaultDisplay.getMode();
            List<Display.Mode> modesList = new ArrayList<>(modes.length);
            for (Display.Mode mode : modes) {
                if (mode.getModeId() != activeMode.getModeId()) {
                    modesList.add(mode);
                }
            }
            Random random = new Random(42);
            Collections.shuffle(modesList, random);
            modesList.add(activeMode);

            for (Display.Mode mode : modesList) {
                testSwitchToModeId(activity, mode);
            }
        } finally {
            mDisplayManager.setShouldAlwaysRespectAppRequestedMode(false);
            mDisplayManager.setRefreshRateSwitchingType(mInitialRefreshRateSwitchingType);
        }
    }

    /**
     * Test that a mode switch to another display mode works when the requesting Activity
     * is destroyed and re-created as part of the configuration change from the display mode.
     */
    @Test
    public void testModeSwitchOnPrimaryDisplayWithRestart() throws Exception {
        final Display.Mode oldMode = mDefaultDisplay.getMode();
        final Optional<Display.Mode> newMode = Arrays.stream(mDefaultDisplay.getSupportedModes())
                .filter(x -> !getPhysicalSize(x).equals(getPhysicalSize(oldMode)))
                .findFirst();
        assumeTrue("Modes with different sizes are not available", newMode.isPresent());

        try {
            mDisplayManager.setShouldAlwaysRespectAppRequestedMode(true);
            assertTrue(mDisplayManager.shouldAlwaysRespectAppRequestedMode());
            mInitialRefreshRateSwitchingType =
                    DisplayUtil.getRefreshRateSwitchingType(mDisplayManager);
            mDisplayManager.setRefreshRateSwitchingType(DisplayManager.SWITCHING_TYPE_NONE);
            testSwitchToModeId(launchActivity(mDisplayTestActivity), newMode.get());
        } finally {
            mDisplayManager.setShouldAlwaysRespectAppRequestedMode(false);
            mDisplayManager.setRefreshRateSwitchingType(mInitialRefreshRateSwitchingType);
        }
    }

    private static Point getPhysicalSize(Display.Mode mode) {
        return new Point(mode.getPhysicalWidth(), mode.getPhysicalHeight());
    }

    private void testSwitchToModeId(DisplayTestActivity activity, Display.Mode targetMode)
            throws Exception {
        final DisplayModeState initialMode = new DisplayModeState(mDefaultDisplay);
        Log.i(TAG, "Testing switching to mode " + targetMode + ". Current mode = " + initialMode);

        final CountDownLatch changeSignal = new CountDownLatch(1);
        final AtomicInteger changeCounter = new AtomicInteger(0);
        final AtomicInteger changesToReachTargetMode = new AtomicInteger(0);

        DisplayListener listener = new DisplayListener() {
            private DisplayModeState mLastMode = initialMode;
            private boolean mIsDesiredModeReached = false;
            @Override
            public void onDisplayAdded(int displayId) {}

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId != mDefaultDisplay.getDisplayId()) {
                    return;
                }
                DisplayModeState newMode = new DisplayModeState(mDefaultDisplay);
                if (mLastMode.equals(newMode)) {
                    // We assume this display change is caused by an external factor so it's
                    // unrelated.
                    return;
                }

                Log.i(TAG, "Switched mode from=" + mLastMode + " to=" + newMode);
                changeCounter.incrementAndGet();

                if (targetMode.getPhysicalHeight() == newMode.mHeight
                        && targetMode.getPhysicalWidth() == newMode.mWidth
                        && Math.abs(targetMode.getRefreshRate() - newMode.mRefreshRate)
                            < REFRESH_RATE_TOLERANCE
                        && !mIsDesiredModeReached) {
                    mIsDesiredModeReached = true;
                    changeSignal.countDown();
                    changesToReachTargetMode.set(changeCounter.get());
                }

                mLastMode = newMode;
            }

            @Override
            public void onDisplayRemoved(int displayId) {}
        };

        Handler handler = new Handler(Looper.getMainLooper());
        mDisplayManager.registerDisplayListener(listener, handler);

        final CountDownLatch presentationSignal = new CountDownLatch(1);
        handler.post(() -> {
            activity.setPreferredDisplayMode(targetMode);
            presentationSignal.countDown();
        });

        assertTrue(presentationSignal.await(5, TimeUnit.SECONDS));

        // Wait until the display change is effective.
        assertTrue(changeSignal.await(5, TimeUnit.SECONDS));
        DisplayModeState currentMode = new DisplayModeState(mDefaultDisplay);
        assertEquals(targetMode.getPhysicalHeight(), currentMode.mHeight);
        assertEquals(targetMode.getPhysicalWidth(), currentMode.mWidth);
        assertEquals(targetMode.getRefreshRate(), currentMode.mRefreshRate, REFRESH_RATE_TOLERANCE);
        assertArrayEquals(targetMode.getSupportedHdrTypes(), currentMode.mSupportedHdrTypes);
        assertArrayEquals(mDefaultDisplay.getHdrCapabilities().getSupportedHdrTypes(),
                currentMode.mSupportedHdrTypes);


        boolean isResolutionSwitch = initialMode.mHeight != targetMode.getPhysicalHeight()
                || initialMode.mWidth != targetMode.getPhysicalHeight();
        boolean isRefreshRateSwitch =
                Math.abs(initialMode.mRefreshRate - targetMode.getRefreshRate())
                        > REFRESH_RATE_TOLERANCE;
        // When both resolution and refresh rate are changed the transition can happen with two
        // mode switches:
        // 1) When the frame rate vote is applied in
        //        java.com.android.server.wm.WindowState#updateFrameRateSelectionPriorityIfNeeded
        // 2) When the DisplayManager policy is applied to RefreshRateConfigs in SurfaceFlinger.
        // TODO(b/199895248) Expect only 1 mode change.
        Truth.assertThat(changesToReachTargetMode.get())
                .isAtMost((isResolutionSwitch && isRefreshRateSwitch) ? 2 : 1);

        // Make sure no more display mode changes are registered.
        Thread.sleep(Duration.ofSeconds(3).toMillis());

        // When a resolution switch occurs the DisplayManager policy in RefreshRateConfigs
        // is cleared  and later reapplied. This may lead to two additional mode switches.
        // TODO(b/200265160) Expect no changes.
        Truth.assertThat(changeCounter.get() - changesToReachTargetMode.get())
                .isAtMost(isResolutionSwitch ? 2 : 0);

        // Many TV apps use the vendor.display-size sysprop to detect the display size (although
        // it's not an official API). In Android S the bugs which required this workaround were
        // fixed and the sysprop should be either unset or should have the same value as the
        // official API. The assertions are done after the delay above because on some
        // devices the sysprop is not updated immediately after onDisplayChanged is called.
        if (PropertyUtil.getVendorApiLevel() >= Build.VERSION_CODES.S) {
            Point vendorDisplaySize = getVendorDisplaySize();
            if (vendorDisplaySize != null) {
                assertEquals(targetMode.getPhysicalWidth(), vendorDisplaySize.x);
                assertEquals(targetMode.getPhysicalHeight(), vendorDisplaySize.y);
            }
        }

        mDisplayManager.unregisterDisplayListener(listener);
    }

    /**
     * Tests that the mode-related attributes and methods work as expected.
     */
    @Test
    public void testMode() {
        Display display = getSecondaryDisplay(mDisplayManager.getDisplays());
        assertEquals(2, display.getSupportedModes().length);
        Display.Mode mode = display.getMode();
        assertEquals(display.getSupportedModes()[0], mode);
        assertEquals(SECONDARY_DISPLAY_WIDTH, mode.getPhysicalWidth());
        assertEquals(SECONDARY_DISPLAY_HEIGHT, mode.getPhysicalHeight());
        assertEquals(display.getRefreshRate(), mode.getRefreshRate(), 0.0001f);
    }

    /**
     * Tests that getSupportedModes works as expected.
     */
    @Test
    public void testGetSupportedModesOnDefaultDisplay() {
        Display.Mode[] supportedModes = mDefaultDisplay.getSupportedModes();
        // We need to check that the graph defined by getAlternativeRefreshRates() is symmetric and
        // transitive.
        // For that reason we run a primitive Union-Find algorithm. In the end of the algorithm
        // groups[i] == groups[j] iff supportedModes[i] and supportedModes[j] are in the same
        // connected component. The complexity is O(N^2*M) where N is the number of modes and M is
        // the max number of alternative refresh rates). This is okay as we expect a relatively
        // small number of supported modes.
        int[] groups = new int[supportedModes.length];
        for (int i = 0; i < groups.length; i++) {
            groups[i] = i;
        }

        for (int i = 0; i < supportedModes.length; i++) {
            Display.Mode supportedMode = supportedModes[i];
            for (float alternativeRate : supportedMode.getAlternativeRefreshRates()) {
                assertTrue(alternativeRate != supportedMode.getRefreshRate());

                // The alternative exists.
                int matchingModeIdx = -1;
                for (int j = 0; j < supportedModes.length; j++) {
                    boolean matches = displayModeMatches(supportedModes[j],
                            supportedMode.getPhysicalWidth(),
                            supportedMode.getPhysicalHeight(),
                            alternativeRate);
                    if (matches) {
                        matchingModeIdx = j;
                        break;
                    }
                }
                String message = "Could not find alternative display mode with refresh rate "
                        + alternativeRate + " for " + supportedMode +  ". All supported"
                        + " modes are " + Arrays.toString(supportedModes);
                assertNotEquals(message, -1, matchingModeIdx);

                // Merge the groups of i and matchingModeIdx
                for (int k = 0; k < groups.length; k++) {
                    if (groups[k] == groups[matchingModeIdx]) {
                        groups[k] = groups[i];
                    }
                }
            }
        }

        for (int i = 0; i < supportedModes.length; i++) {
            for (int j = 0; j < supportedModes.length; j++) {
                if (i != j && groups[i] == groups[j]) {
                    float fpsI = supportedModes[i].getRefreshRate();
                    boolean iIsAlternativeToJ = false;
                    for (float alternatives : supportedModes[j].getAlternativeRefreshRates()) {
                        if (alternatives == fpsI) {
                            iIsAlternativeToJ = true;
                            break;
                        }
                    }
                    String message = "Expected " + supportedModes[i] + " to be listed as "
                            + "alternative refresh rate of " + supportedModes[j] + ". All supported"
                            + " modes are " + Arrays.toString(supportedModes);
                    assertTrue(message, iIsAlternativeToJ);
                }
            }
        }
    }

    private boolean displayModeMatches(Display.Mode mode, int width, int height,
            float refreshRate) {
        return mode.getPhysicalWidth() == width &&
                mode.getPhysicalHeight() == height &&
                Float.floatToIntBits(mode.getRefreshRate()) == Float.floatToIntBits(refreshRate);
    }

    /**
     * Tests that getMode() returns a mode which is in getSupportedModes().
     */
    @Test
    public void testActiveModeIsSupportedModesOnDefaultDisplay() {
        Display.Mode[] supportedModes = mDefaultDisplay.getSupportedModes();
        Display.Mode activeMode = mDefaultDisplay.getMode();
        boolean activeModeIsSupported = false;
        for (Display.Mode mode : supportedModes) {
            if (mode.equals(activeMode)) {
                activeModeIsSupported = true;
                break;
            }
        }
        assertTrue(activeModeIsSupported);
    }

    /**
     * Test that refresh rate switch app requests are correctly executed on a secondary display.
     */
    @Test
    public void testRefreshRateSwitchOnSecondaryDisplay() throws Exception {
        // Standalone VR devices globally ignore SYSTEM_ALERT_WINDOW via AppOps.
        // Skip this test, which depends on a Presentation SYSTEM_ALERT_WINDOW to pass.
        if (mUiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_VR_HEADSET) {
            return;
        }

        enableAppOps();
        final Display display = getSecondaryDisplay(mDisplayManager.getDisplays());
        Display.Mode[] modes = display.getSupportedModes();
        assertEquals(2, modes.length);
        Display.Mode mode = display.getMode();
        assertEquals(modes[0], mode);
        final Display.Mode newMode = modes[1];

        Handler handler = new Handler(Looper.getMainLooper());

        // Register for display events.
        final CountDownLatch changeSignal = new CountDownLatch(1);
        mDisplayManager.registerDisplayListener(new DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {}

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId == display.getDisplayId()
                        && display.getMode() != null
                        && display.getMode().getModeId() == newMode.getModeId()) {
                    changeSignal.countDown();
                }
            }

            @Override
            public void onDisplayRemoved(int displayId) {}
        }, handler);

        // Show the presentation.
        final CountDownLatch presentationSignal = new CountDownLatch(1);
        handler.post(() -> {
            mPresentation = new TestPresentation(
                    InstrumentationRegistry.getInstrumentation().getContext(),
                    display, newMode.getModeId());
            mPresentation.show();
            presentationSignal.countDown();
        });
        assertTrue(presentationSignal.await(5, TimeUnit.SECONDS));

        // Wait until the display change is effective.
        assertTrue(changeSignal.await(5, TimeUnit.SECONDS));

        assertEquals(newMode, display.getMode());
        handler.post(() -> mPresentation.dismiss());
    }

    /**
     * Verify that getColorSpace method returns the expected color space of the display.
     */
    @Test
    public void testGetPreferredWideGamutColorSpace() {
        final ColorSpace colorSpace = mDefaultDisplay.getPreferredWideGamutColorSpace();

        if (mDefaultDisplay.isWideColorGamut()) {
            assertFalse(colorSpace.isSrgb());
            assertTrue(colorSpace.isWideGamut());
        } else {
            assertNull(colorSpace);
        }
    }

    private void testGetOverlaySupportInternal(OverlayProperties overlayProperties) {
        assertNotNull(overlayProperties);
        Parcel parcel = Parcel.obtain();
        assertEquals(0, overlayProperties.describeContents());
        overlayProperties.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        OverlayProperties dest = OverlayProperties.CREATOR.createFromParcel(parcel);
        assertEquals(overlayProperties.isMixedColorSpacesSupported(),
                     dest.isMixedColorSpacesSupported());
        parcel.recycle();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_OVERLAYPROPERTIES_CLASS_API)
    public void testGetOverlaySupportForPrimary() {
        testGetOverlaySupportInternal(mDefaultDisplay.getOverlaySupport());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_OVERLAYPROPERTIES_CLASS_API)
    public void testGetOverlaySupportForSecondary() {
        Display secondaryDisplay = getSecondaryDisplay(mDisplayManager.getDisplays());
        testGetOverlaySupportInternal(secondaryDisplay.getOverlaySupport());
        assertTrue(secondaryDisplay.getOverlaySupport().isMixedColorSpacesSupported());
    }

    @Test
    public void testGetDeviceProductInfo() {
        DeviceProductInfo deviceProductInfo = mDefaultDisplay.getDeviceProductInfo();
        assumeNotNull(deviceProductInfo);

        assertNotNull(deviceProductInfo.getManufacturerPnpId());

        assertNotNull(deviceProductInfo.getProductId());

        final boolean isYearPresent = (deviceProductInfo.getModelYear() != -1) ||
                (deviceProductInfo.getManufactureYear() != -1);
        assertTrue(isYearPresent);
        int year = deviceProductInfo.getModelYear() != -1 ?
                deviceProductInfo.getModelYear() : deviceProductInfo.getManufactureYear();
        // Verify if the model year or manufacture year is greater than or equal to 1990.
        // This assumption is based on Section of 3.4.4 - Week and Year of Manufacture or Model Year
        // of VESA EDID STANDARD Version 1, Revision 4
        assertTrue(year >= 1990);

        int week = deviceProductInfo.getManufactureWeek();
        assertTrue(week == -1 || (week >= 1 && week <= 53));

        List<Integer> allowedConnectionToSinkValues = List.of(
                DeviceProductInfo.CONNECTION_TO_SINK_UNKNOWN,
                DeviceProductInfo.CONNECTION_TO_SINK_BUILT_IN,
                DeviceProductInfo.CONNECTION_TO_SINK_DIRECT,
                DeviceProductInfo.CONNECTION_TO_SINK_TRANSITIVE
        );
        assertTrue(
                allowedConnectionToSinkValues.contains(
                        deviceProductInfo.getConnectionToSinkType()));
    }

    @Test
    public void testDeviceProductInfo() {
        DeviceProductInfo deviceProductInfo = new DeviceProductInfo(
                "DeviceName" /* name */,
                "TTL" /* manufacturePnpId */,
                "ProductId1" /* productId */,
                2000 /* modelYear */,
                DeviceProductInfo.CONNECTION_TO_SINK_DIRECT);

        assertEquals("DeviceName", deviceProductInfo.getName());
        assertEquals("TTL", deviceProductInfo.getManufacturerPnpId());
        assertEquals("ProductId1", deviceProductInfo.getProductId());
        assertEquals(2000, deviceProductInfo.getModelYear());
        assertEquals(DeviceProductInfo.CONNECTION_TO_SINK_DIRECT,
                deviceProductInfo.getConnectionToSinkType());
    }

    @Test
    public void testFailBrightnessChangeWithoutPermission() throws Exception {
        final DisplayTestActivity activity = launchActivity(mDisplayTestActivity);
        final int originalValue = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, BRIGHTNESS_MAX);

        try {
            final int valueToSet = originalValue > 128 ? 40 : 200;  // sufficiently different value
            boolean wasSet = setBrightness(((float) valueToSet) / BRIGHTNESS_MAX);

            assertFalse(wasSet);
            int newValue = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, BRIGHTNESS_MAX);
            assertEquals(originalValue, newValue);  // verify that setting the new value failed.
        } finally {
            try {
                // Clean up just in case the test fails and we did actually manage to change the
                // brightness.
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, originalValue);
            } catch (Exception e) {
            }
        }
    }

    @Test
    public void testGetSupportedWideColorGamut_shouldNotBeNull() {
        assertNotNull(mSupportedWideGamuts);
    }

    @Test
    public void testGetSupportWideColorGamut_displayIsWideColorGamut() {
        final ColorSpace displayP3 = ColorSpace.get(ColorSpace.Named.DISPLAY_P3);
        final ColorSpace dciP3 = ColorSpace.get(ColorSpace.Named.DCI_P3);
        final List<ColorSpace> list = Arrays.asList(mSupportedWideGamuts);
        final boolean supportsWideGamut = mDefaultDisplay.isWideColorGamut()
                && mSupportedWideGamuts.length > 0;
        final boolean supportsP3 = list.contains(displayP3) || list.contains(dciP3);
        assertEquals(supportsWideGamut, supportsP3);
    }

    @CddTest(requirement="7.1.1.1/H-0-2")
    @Test
    public void testRestrictedFramebufferSize() {
        PackageManager packageManager = mContext.getPackageManager();
        if (packageManager.hasSystemFeature(FEATURE_LEANBACK)) {
            // TV devices are allowed to restrict their framebuffer size.
            return;
        }

        // Non-TV devices are not allowed by Android CDD to restrict their framebuffer size.
        String width = SystemProperties.get("ro.surface_flinger.max_graphics_width");
        assertEquals("", width);
        String height = SystemProperties.get("ro.surface_flinger.max_graphics_height");
        assertEquals("", height);
    }

    /**
     * Used to force mode changes on a display.
     * <p>
     * Note that due to limitations of the Presentation class, the modes must have the same size
     * otherwise the presentation will be automatically dismissed.
     */
    private static final class TestPresentation extends Presentation {

        private final int mModeId;

        public TestPresentation(Context context, Display display, int modeId) {
            super(context, display);
            mModeId = modeId;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            View content = new View(getContext());
            content.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            content.setBackgroundColor(Color.RED);
            setContentView(content);

            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.preferredDisplayModeId = mModeId;
            params.setTitle("CtsTestPresentation");
            getWindow().setAttributes(params);
        }

        @Override
        public void cancel() {
            // Ignore attempts to force cancel the presentation. This is going to happen when we
            // change the mode of the display since doing so will change the display metrics, which
            // Presentations don't yet support. Ignoring it means the Presentation will stay up and
            // the mode will stay changed until dismiss is called, preventing a race condition
            // between the test checking the mode of the display and the mode changing back to the
            // default because the requesting Presentation is no longer showing.
        }
    }

    /**
     * Sets the brightness via the shell cmd.
     */
    public boolean setBrightness(float value) throws Exception {
        Process process = Runtime.getRuntime().exec("cmd display set-brightness " + value);
        return 0 == process.waitFor();
    }

    private Point getVendorDisplaySize() {
        String value = PropertyUtil.getProperty("vendor.display-size");
        if (TextUtils.isEmpty(value)) {
            return null;
        }

        String[] parts = value.split("x");
        assertEquals(2, parts.length);
        return new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
}
