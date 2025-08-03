/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.server.wm.window;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.server.wm.ComponentNameUtils.getWindowName;
import static android.server.wm.WindowManagerState.dpToPx;
import static android.server.wm.app.Components.BOTTOM_LEFT_LAYOUT_ACTIVITY;
import static android.server.wm.app.Components.BOTTOM_RIGHT_LAYOUT_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.app.Components.TOP_LEFT_LAYOUT_ACTIVITY;
import static android.server.wm.app.Components.TOP_RIGHT_LAYOUT_ACTIVITY;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowInsets.Type.captionBar;
import static android.view.WindowInsets.Type.systemBars;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerState.WindowState;
import android.util.DisplayMetrics;
import android.view.DisplayCutout;
import android.view.WindowMetrics;

import org.junit.Test;

import java.util.List;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceWindow:ManifestLayoutTests
 */
@Presubmit
public class ManifestLayoutTests extends ActivityManagerTestBase {

    // Test parameters
    private static final int DEFAULT_WIDTH_DP = 240;
    private static final int DEFAULT_HEIGHT_DP = 160;
    private static final float DEFAULT_WIDTH_FRACTION = 0.50f;
    private static final float DEFAULT_HEIGHT_FRACTION = 0.70f;
    private static final int MIN_WIDTH_DP = 100;
    private static final int MIN_HEIGHT_DP = 80;

    private static final int GRAVITY_VER_CENTER = 0x01;
    private static final int GRAVITY_VER_TOP    = 0x02;
    private static final int GRAVITY_VER_BOTTOM = 0x04;
    private static final int GRAVITY_HOR_CENTER = 0x10;
    private static final int GRAVITY_HOR_LEFT   = 0x20;
    private static final int GRAVITY_HOR_RIGHT  = 0x40;

    private WindowManagerState.DisplayContent mDisplay;
    private WindowState mWindowState;

    @Test
    public void testGravityAndDefaultSizeTopLeft() throws Exception {
        testLayout(GRAVITY_VER_TOP, GRAVITY_HOR_LEFT, false /*fraction*/);
    }

    @Test
    public void testGravityAndDefaultSizeTopRight() throws Exception {
        testLayout(GRAVITY_VER_TOP, GRAVITY_HOR_RIGHT, true /*fraction*/);
    }

    @Test
    public void testGravityAndDefaultSizeBottomLeft() throws Exception {
        testLayout(GRAVITY_VER_BOTTOM, GRAVITY_HOR_LEFT, true /*fraction*/);
    }

    @Test
    public void testGravityAndDefaultSizeBottomRight() throws Exception {
        testLayout(GRAVITY_VER_BOTTOM, GRAVITY_HOR_RIGHT, false /*fraction*/);
    }

    @Test
    public void testMinimalSizeFreeform() throws Exception {
        assumeTrue("Skipping test: no freeform support", supportsFreeform());

        testMinimalSize(true /* freeform */);
    }

    @Test
    @Presubmit
    public void testMinimalSizeDocked() throws Exception {
        assumeTrue("Skipping test: no multi-window support", supportsSplitScreenMultiWindow());

        testMinimalSize(false /* freeform */);
    }

    private void testMinimalSize(boolean freeform) throws Exception {
        // Issue command to resize to <0,0,1,1>. We expect the size to be floored at
        // MIN_WIDTH_DPxMIN_HEIGHT_DP.
        if (freeform) {
            launchActivity(BOTTOM_RIGHT_LAYOUT_ACTIVITY, WINDOWING_MODE_FREEFORM);
            resizeActivityTask(BOTTOM_RIGHT_LAYOUT_ACTIVITY, 0, 0, 1, 1);
        } else { // stackId == DOCKED_STACK_ID
            launchActivitiesInSplitScreen(
                    getLaunchActivityBuilder().setTargetActivity(BOTTOM_RIGHT_LAYOUT_ACTIVITY),
                    getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY));
            mTaskOrganizer.setRootPrimaryTaskBounds(new Rect(0, 0, 1, 1));
        }
        getDisplayAndWindowState(BOTTOM_RIGHT_LAYOUT_ACTIVITY, false);

        // Use default density because ActivityInfo.WindowLayout is initialized by that.
        final int minWidth = dpToPx(MIN_WIDTH_DP, DisplayMetrics.DENSITY_DEVICE_STABLE);
        final int minHeight = dpToPx(MIN_HEIGHT_DP, DisplayMetrics.DENSITY_DEVICE_STABLE);

        // The alternative size of the current display density.
        final int alternativeMinWidth = dpToPx(MIN_WIDTH_DP, mDisplay.getDpi());
        final int alternativeMinHeight = dpToPx(MIN_HEIGHT_DP, mDisplay.getDpi());

        final Rect parentFrame = mWindowState.getParentFrame();
        final int cutoutSize = getCutoutSizeByHorGravity(GRAVITY_HOR_LEFT);
        final int actualWidth = parentFrame.width() + cutoutSize;
        final int actualHeight = parentFrame.height();

        assertTrue("Min width is incorrect",
                (actualWidth == minWidth || actualWidth == alternativeMinWidth));
        assertTrue("Min height is incorrect",
                (actualHeight == minHeight || actualHeight == alternativeMinHeight));
    }

    private void testLayout(
            int vGravity, int hGravity, boolean fraction) throws Exception {
        assumeTrue("Skipping test: no freeform support", supportsFreeform());

        final ComponentName activityName;
        if (vGravity == GRAVITY_VER_TOP) {
            activityName = (hGravity == GRAVITY_HOR_LEFT) ? TOP_LEFT_LAYOUT_ACTIVITY
                    : TOP_RIGHT_LAYOUT_ACTIVITY;
        } else {
            activityName = (hGravity == GRAVITY_HOR_LEFT) ? BOTTOM_LEFT_LAYOUT_ACTIVITY
                    : BOTTOM_RIGHT_LAYOUT_ACTIVITY;
        }

        // Launch in freeform stack
        launchActivity(activityName, WINDOWING_MODE_FREEFORM);

        getDisplayAndWindowState(activityName, true);

        final Rect parentFrame = mWindowState.getParentFrame();
        final WindowMetrics windowMetrics = mWm.getMaximumWindowMetrics();
        final Rect stableBounds = new Rect(windowMetrics.getBounds());
        stableBounds.inset(windowMetrics.getWindowInsets().getInsetsIgnoringVisibility(
                systemBars() & ~captionBar()));
        final int expectedWidthPx, expectedHeightPx;
        // Evaluate the expected window size in px. If we're using fraction dimensions,
        // calculate the size based on the app rect size. Otherwise, convert the expected
        // size in dp to px.
        if (fraction) {
            expectedWidthPx = (int) (stableBounds.width() * DEFAULT_WIDTH_FRACTION);
            expectedHeightPx = (int) (stableBounds.height() * DEFAULT_HEIGHT_FRACTION);
        } else {
            final int densityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE;
            expectedWidthPx = dpToPx(DEFAULT_WIDTH_DP, densityDpi);
            expectedHeightPx = dpToPx(DEFAULT_HEIGHT_DP, densityDpi);
        }

        verifyFrameSizeAndPosition(vGravity, hGravity, expectedWidthPx, expectedHeightPx,
                parentFrame, stableBounds);
    }

    private void getDisplayAndWindowState(ComponentName activityName, boolean checkFocus)
            throws Exception {
        final String windowName = getWindowName(activityName);

        mWmState.computeState(activityName);

        if (checkFocus) {
            mWmState.assertFocusedWindow("Test window must be the front window.", windowName);
        } else {
            mWmState.assertVisibility(activityName, true);
        }

        final List<WindowState> windowList =
                mWmState.getMatchingVisibleWindowState(windowName);

        assertEquals("Should have exactly one window state for the activity.",
                1, windowList.size());

        mWindowState = windowList.get(0);
        assertNotNull("Should have a valid window", mWindowState);

        mDisplay = mWmState.getDisplay(mWindowState.getDisplayId());
        assertNotNull("Should be on a display", mDisplay);
    }

    private void verifyFrameSizeAndPosition(
            int vGravity, int hGravity, int expectedWidthPx, int expectedHeightPx,
            Rect parentFrame, Rect stableBounds) {
        final int cutoutSize = getCutoutSizeByHorGravity(hGravity);
        assertEquals("Width is incorrect",
                expectedWidthPx, parentFrame.width() + cutoutSize);
        assertEquals("Height is incorrect", expectedHeightPx, parentFrame.height());

        if (vGravity == GRAVITY_VER_TOP) {
            assertEquals("Should be on the top", stableBounds.top, parentFrame.top);
        } else if (vGravity == GRAVITY_VER_BOTTOM) {
            assertEquals("Should be on the bottom", stableBounds.bottom, parentFrame.bottom);
        }

        if (hGravity == GRAVITY_HOR_LEFT) {
            assertEquals("Should be on the left",
                    stableBounds.left, parentFrame.left - cutoutSize);
        } else if (hGravity == GRAVITY_HOR_RIGHT){
            assertEquals("Should be on the right",
                    stableBounds.right, parentFrame.right + cutoutSize);
        }
    }

    private int getCutoutSizeByHorGravity(int hGravity) {
        DisplayCutout cutout = mDm.getDisplay(DEFAULT_DISPLAY).getCutout();
        if (cutout == null) {
            return 0;
        }

        // When the layoutInDisplayCutoutMode is default, the status bar & navigation bar already
        // take top and bottom cutout into account.
        // Here we only need to account for left & right cutout areas.
        if (hGravity == GRAVITY_HOR_LEFT) {
            return cutout.getSafeInsetLeft();
        } else if (hGravity == GRAVITY_HOR_RIGHT) {
            return cutout.getSafeInsetRight();
        } else {
            return 0;
        }
    }
}
