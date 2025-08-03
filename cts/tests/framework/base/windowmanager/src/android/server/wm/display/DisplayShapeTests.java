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

package android.server.wm.display;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.RoundedCorner.POSITION_BOTTOM_LEFT;
import static android.view.RoundedCorner.POSITION_TOP_LEFT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.graphics.Path;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.platform.test.annotations.Presubmit;
import android.server.wm.MultiDisplayTestBase;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerTestBase;
import android.view.Display;
import android.view.DisplayShape;
import android.view.RoundedCorner;
import android.view.View;

import com.android.compatibility.common.util.PropertyUtil;

import org.junit.Before;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceDisplay:DisplayShapeTests
 */
@Presubmit
@android.server.wm.annotation.Group3
public class DisplayShapeTests extends WindowManagerTestBase {

    private Display mDisplay;

    @Before
    public void setUp() throws Exception {
        mDisplay = mDm.getDisplay(DEFAULT_DISPLAY);
        assumeNotNull(mDisplay);
    }

    @Test
    public void testNonNull() {
        final DisplayShape shape = mDisplay.getShape();
        assertNotNull(shape);

        final Path path = shape.getPath();
        assertNotNull(path);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testDisplayShapeConfig() {
        // Skip the mix build case when the base build < U (e.x. U GSI on T base build) and only
        // test for U+ base builds.
        assumeTrue("Test does not apply for vendor image with API level lower than U",
                PropertyUtil.isVendorApiLevelNewerThan(Build.VERSION_CODES.TIRAMISU));

        boolean hasRoundedCorner = false;
        for (int i = POSITION_TOP_LEFT; i <= POSITION_BOTTOM_LEFT; i++) {
            final RoundedCorner r = mDisplay.getRoundedCorner(i);
            if (r != null && r.getRadius() > 0) {
                hasRoundedCorner = true;
                break;
            }
        }

        if (hasRoundedCorner) {
            // If the display shape is not configured, the returned path will be rectangular if the
            // display is not round. The config must be set if the display is not round or
            // rectangular.
            assertFalse("The display has a rounded corner but the shape specifies a rectangle."
                    + " Please set the config (config_mainDisplayShape) for the display shape.",
                    mDisplay.getShape().getPath().isRect(null));
        }
    }

    @Test
    public void testDisplayShapeFromWindowInsets() {
        DisplayShapeTests.TestActivity activity =
                startActivity(DisplayShapeTests.TestActivity.class, DEFAULT_DISPLAY);

        final DisplayShape fromDisplay = mDisplay.getShape();
        final View decorView = activity.getWindow().getDecorView();
        final DisplayShape fromInsets = decorView.getRootWindowInsets().getDisplayShape();

        final int[] location = new int[2];
        decorView.getLocationOnScreen(location);
        final RectF boundsFromDisplay = getBoundsFromPath(fromDisplay.getPath());
        boundsFromDisplay.offset(-location[0], -location[1]);
        final RectF boundsFromView = getBoundsFromPath(fromInsets.getPath());
        assertEquals(boundsFromView, boundsFromDisplay);
    }

    @Test
    public void testDisplayShapeOnVirtualDisplay() {
        try (MultiDisplayTestBase.VirtualDisplaySession session =
                     new MultiDisplayTestBase.VirtualDisplaySession()) {
            // Setup a simulated display.
            WindowManagerState.DisplayContent dc = session.setSimulateDisplay(true).createDisplay();
            Display simulatedDisplay = mContext.getSystemService(DisplayManager.class)
                    .getDisplay(dc.mId);
            final DisplayShape shape = simulatedDisplay.getShape();
            assertNotNull(shape);
            assertTrue(shape.getPath().isRect(null));

            final int displayWidth = dc.getDisplayRect().width();
            final int displayHeight = dc.getDisplayRect().height();
            final RectF expectRect = new RectF(0, 0, displayWidth, displayHeight);
            final RectF actualRect = getBoundsFromPath(shape.getPath());
            assertEquals(expectRect, actualRect);
        }
    }

    private static RectF getBoundsFromPath(Path path) {
        final RectF rect = new RectF();
        path.computeBounds(rect, false);
        return rect;
    }

    public static class TestActivity extends FocusableActivity {
    }
}
