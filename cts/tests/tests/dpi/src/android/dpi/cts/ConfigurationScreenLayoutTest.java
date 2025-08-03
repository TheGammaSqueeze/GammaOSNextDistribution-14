/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.dpi.cts;

import static android.content.res.Configuration.SCREENLAYOUT_LONG_MASK;
import static android.content.res.Configuration.SCREENLAYOUT_LONG_NO;
import static android.content.res.Configuration.SCREENLAYOUT_LONG_YES;
import static android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE;
import static android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK;
import static android.content.res.Configuration.SCREENLAYOUT_SIZE_NORMAL;
import static android.content.res.Configuration.SCREENLAYOUT_SIZE_XLARGE;
import static android.view.WindowInsets.Type.displayCutout;
import static android.view.WindowInsets.Type.systemBars;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Rect;
import android.server.wm.IgnoreOrientationRequestSession;
import android.server.wm.WindowManagerStateHelper;
import android.test.ActivityInstrumentationTestCase2;
import android.view.WindowInsets;
import android.view.WindowMetrics;

public class ConfigurationScreenLayoutTest
        extends ActivityInstrumentationTestCase2<OrientationActivity> {

    private static final int[] ORIENTATIONS = new int[] {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
    };

    private static final int BIGGEST_LAYOUT = SCREENLAYOUT_SIZE_XLARGE
            | SCREENLAYOUT_LONG_YES;

    public ConfigurationScreenLayoutTest() {
        super(OrientationActivity.class);
    }

    public void testScreenLayout() throws Exception {
        if (!supportsRotation()) {
            // test has no effect if device does not support rotation
            tearDown();
            return;
        }
        if (isPC()) {
            // The test skips mainly for Chromebook clamshell mode. For Chromebook clamshell mode
            // with non-rotated landscape physical screen, the portrait window/activity has special
            // behavior with black background on both sides to make the window/activity look
            // portrait, which returns smaller screen layout size.
            tearDown();
            return;
        }
        // Disable IgnoreOrientationRequest feature because when it's enabled, the device would only
        // follow physical rotations.
        try (IgnoreOrientationRequestSession session =
                     new IgnoreOrientationRequestSession(false /* enable */)) {

            // Check that all four orientations report the same configuration value.
            for (int orientation : ORIENTATIONS) {
                Activity activity = startOrientationActivity(orientation);
                WindowManagerStateHelper wmState = new WindowManagerStateHelper();
                wmState.computeState();
                if (activity.isInMultiWindowMode()
                        || wmState.isTaskDisplayAreaIgnoringOrientationRequest(
                                activity.getComponentName())) {
                    // activity.setRequestedOrientation has no effect in multi-window mode.
                    // Besides, the test is not feasible when current display area is ignoring
                    // orientation since configuration will be updated, so it goes to quit this
                    // test as well.
                    tearDown();
                    return;
                }
                final int expectedLayout = computeScreenLayout(activity);
                final int expectedSize = expectedLayout & SCREENLAYOUT_SIZE_MASK;
                final int expectedLong = expectedLayout & SCREENLAYOUT_LONG_MASK;

                final Configuration config = activity.getResources().getConfiguration();
                final int actualSize = config.screenLayout & SCREENLAYOUT_SIZE_MASK;
                final int actualLong = config.screenLayout & SCREENLAYOUT_LONG_MASK;

                assertEquals("Expected screen size value of " + expectedSize + " but got "
                        + actualSize + " for orientation "
                        + orientation, expectedSize, actualSize);
                assertEquals("Expected screen long value of " + expectedLong + " but got "
                        + actualLong + " for orientation "
                        + orientation, expectedLong, actualLong);
                tearDown();
            }
        } finally {
            tearDown();
        }
    }

    private boolean hasDeviceFeature(final String requiredFeature) {
        return getInstrumentation().getContext()
                .getPackageManager()
                .hasSystemFeature(requiredFeature);
    }

    private Activity startOrientationActivity(int orientation) {
        Intent intent = new Intent();
        intent.putExtra(OrientationActivity.EXTRA_ORIENTATION, orientation);
        setActivityIntent(intent);
        return getActivity();
    }

    // Logic copied from Configuration#reduceScreenLayout(int, int, int)
    /**
     * Returns expected value of {@link Configuration#screenLayout} with the
     *         {@link Configuration#SCREENLAYOUT_LONG_MASK} and
     *         {@link Configuration#SCREENLAYOUT_SIZE_MASK} defined
     */
    private int computeScreenLayout(Activity activity) {
        final WindowInsets windowInsets = activity.getWindowManager().getCurrentWindowMetrics()
                .getWindowInsets();
        final Insets insets = windowInsets.getInsets(systemBars() | displayCutout());
        return reduceScreenLayout(activity, insets, BIGGEST_LAYOUT);
    }

    private int reduceScreenLayout(Activity activity, Insets excludeInsets, int screenLayout) {
        int screenLayoutSize;
        boolean screenLayoutLong;

        final WindowMetrics windowMetrics = activity.getWindowManager().getCurrentWindowMetrics();
        final Rect bounds = new Rect(windowMetrics.getBounds());
        bounds.inset(excludeInsets);

        final float density = activity.getResources().getDisplayMetrics().density;
        final int widthDp = (int) (bounds.width() / density);
        final int heightDp = (int) (bounds.height() / density);
        final int longSize = Math.max(widthDp, heightDp);
        final int shortSize = Math.min(widthDp, heightDp);

        if (longSize < 470) {
            screenLayoutSize = Configuration.SCREENLAYOUT_SIZE_SMALL;
            screenLayoutLong = false;
        } else {
            if (longSize >= 960 && shortSize >= 720) {
                screenLayoutSize = SCREENLAYOUT_SIZE_XLARGE;
            } else if (longSize >= 640 && shortSize >= 480) {
                screenLayoutSize = SCREENLAYOUT_SIZE_LARGE;
            } else {
                screenLayoutSize = SCREENLAYOUT_SIZE_NORMAL;
            }
            screenLayoutLong = ((longSize * 3) / 5) >= (shortSize - 1);
        }

        if (!screenLayoutLong) {
            screenLayout = (screenLayout & ~SCREENLAYOUT_LONG_MASK) | SCREENLAYOUT_LONG_NO;
        }
        int curSize = screenLayout & SCREENLAYOUT_SIZE_MASK;
        if (screenLayoutSize < curSize) {
            screenLayout = (screenLayout & ~SCREENLAYOUT_SIZE_MASK) | screenLayoutSize;
        }
        return screenLayout;
    }

    /**
     * Rotation support is indicated by explicitly having both landscape and portrait
     * features or not listing either at all.
     */
    private boolean supportsRotation() {
        final boolean supportsLandscape = hasDeviceFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE);
        final boolean supportsPortrait = hasDeviceFeature(PackageManager.FEATURE_SCREEN_PORTRAIT);
        return (supportsLandscape && supportsPortrait)
                || (!supportsLandscape && !supportsPortrait);
    }

    /** Checks if it is a PC device */
    private boolean isPC() {
        return hasDeviceFeature(PackageManager.FEATURE_PC);
    }
}
