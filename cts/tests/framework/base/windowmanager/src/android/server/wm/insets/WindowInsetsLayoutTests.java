/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.wm.insets;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;

import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowManagerTestBase;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsets.Side;
import android.view.WindowInsets.Type;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Test;

/**
 * Test whether WindowManager performs the correct layout while the app applies the fit-window-
 * insets APIs.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceInsets:WindowInsetsLayoutTests
 */
@Presubmit
@android.server.wm.annotation.Group2
public class WindowInsetsLayoutTests extends WindowManagerTestBase {

    private static final long TIMEOUT = 1000; // milliseconds

    @Test
    public void testSetFitInsetsTypes() {
        // Start the Activity in fullscreen windowing mode for its bounds to match display bounds.
        final TestActivity activity =
                startActivityInWindowingMode(TestActivity.class, WINDOWING_MODE_FULLSCREEN);

        // Make sure the main window has been laid out.
        final View mainWindowRoot = activity.getWindow().getDecorView();
        PollingCheck.waitFor(TIMEOUT, () -> mainWindowRoot.getWidth() > 0);

        getInstrumentation().runOnMainSync(() -> {
            activity.assertMatchesWindowBounds();
        });

        testSetFitInsetsTypesInner(Type.statusBars(), activity);
        testSetFitInsetsTypesInner(Type.navigationBars(), activity);
        testSetFitInsetsTypesInner(Type.systemBars(), activity);
    }

    private void testSetFitInsetsTypesInner(int types, TestActivity activity) {
        getInstrumentation().runOnMainSync(() -> {
            activity.addOverlayWindow(types, Side.all(), false);
        });

        // Make sure the overlay window has been laid out.
        final View overlayWindowRoot = activity.getOverlayWindowRoot();
        PollingCheck.waitFor(TIMEOUT, () -> overlayWindowRoot.getWidth() > 0);

        getInstrumentation().runOnMainSync(() -> {
            final WindowInsets windowInsets = overlayWindowRoot.getRootWindowInsets();
            final Insets insets = windowInsets.getInsets(types);
            assertEquals(Insets.NONE, insets);
            activity.removeOverlayWindow();
        });
    }

    @Test
    public void testSetFitInsetsSides() {
        // Start the Activity in fullscreen windowing mode for its bounds to match display bounds.
        final TestActivity activity =
                startActivityInWindowingMode(TestActivity.class, WINDOWING_MODE_FULLSCREEN);

        // Make sure the main window has been laid out.
        final View mainWindowRoot = activity.getWindow().getDecorView();
        PollingCheck.waitFor(TIMEOUT, () -> mainWindowRoot.getWidth() > 0);

        getInstrumentation().runOnMainSync(() -> {
            activity.assertMatchesWindowBounds();
        });

        testSetFitInsetsSidesInner(Side.LEFT, activity);
        testSetFitInsetsSidesInner(Side.TOP, activity);
        testSetFitInsetsSidesInner(Side.RIGHT, activity);
        testSetFitInsetsSidesInner(Side.BOTTOM, activity);
    }

    private void testSetFitInsetsSidesInner(int sides, TestActivity activity) {
        final int types = Type.systemBars();
        getInstrumentation().runOnMainSync(() -> {
            activity.addOverlayWindow(types, sides, false);
        });

        // Make sure the overlay window has been laid out.
        final View overlayWindowRoot = activity.getOverlayWindowRoot();
        PollingCheck.waitFor(TIMEOUT, () -> overlayWindowRoot.getWidth() > 0);

        getInstrumentation().runOnMainSync(() -> {
            final WindowInsets windowInsets = overlayWindowRoot.getRootWindowInsets();
            final Insets insets = windowInsets.getInsets(types);
            if ((sides & Side.LEFT) != 0) {
                assertEquals(0, insets.left);
            }
            if ((sides & Side.TOP) != 0) {
                assertEquals(0, insets.top);
            }
            if ((sides & Side.RIGHT) != 0) {
                assertEquals(0, insets.right);
            }
            if ((sides & Side.BOTTOM) != 0) {
                assertEquals(0, insets.bottom);
            }
            activity.removeOverlayWindow();
        });
    }

    @Test
    public void testSetFitInsetsIgnoringVisibility() {
        // Start the Activity in fullscreen windowing mode for its bounds to match display bounds.
        final TestActivity activity =
                startActivityInWindowingMode(TestActivity.class, WINDOWING_MODE_FULLSCREEN);

        // Make sure the main window has been laid out.
        final View mainWindowRoot = activity.getWindow().getDecorView();
        PollingCheck.waitFor(TIMEOUT, () -> mainWindowRoot.getWidth() > 0);

        final int types = Type.systemBars();
        final int sides = Side.all();
        final int[] locationAndSize1 = new int[4];
        final int[] locationAndSize2 = new int[4];

        getInstrumentation().runOnMainSync(() -> {
            activity.assertMatchesWindowBounds();
            activity.addOverlayWindow(types, sides, false);
        });

        // Make sure the 1st overlay window has been laid out.
        final View overlayWindowRoot1 = activity.getOverlayWindowRoot();
        PollingCheck.waitFor(TIMEOUT, () -> overlayWindowRoot1.getWidth() > 0);

        getInstrumentation().runOnMainSync(() -> {
            overlayWindowRoot1.getLocationOnScreen(locationAndSize1);
            locationAndSize1[2] = overlayWindowRoot1.getWidth();
            locationAndSize1[3] = overlayWindowRoot1.getHeight();
            activity.removeOverlayWindow();

            mainWindowRoot.getWindowInsetsController().hide(types);

            activity.addOverlayWindow(types, sides, true);
        });

        // Make sure the 2nd overlay window has been laid out.
        final View overlayWindowRoot2 = activity.getOverlayWindowRoot();
        PollingCheck.waitFor(TIMEOUT, () -> overlayWindowRoot2.getWidth() > 0);

        getInstrumentation().runOnMainSync(() -> {
            overlayWindowRoot2.getLocationOnScreen(locationAndSize2);
            locationAndSize2[2] = overlayWindowRoot2.getWidth();
            locationAndSize2[3] = overlayWindowRoot2.getHeight();
            activity.removeOverlayWindow();
        });

        for (int i = 0; i < 4; i++) {
            assertEquals(locationAndSize1[i], locationAndSize2[i]);
        }
    }

    public static class TestActivity extends FocusableActivity {

        private View mOverlayWindowRoot;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            final View view = new View(this);
            setContentView(view);
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            getWindow().setAttributes(lp);
        }

        void addOverlayWindow(int types, int sides, boolean ignoreVis) {
            final WindowManager.LayoutParams attrs = new WindowManager.LayoutParams();
            // In targets like automotive portrait UI, when the system bars are hidden, the
            // system can change the bounds of the app to be fullscreen which can cause issues
            // with the location. Use TYPE_APPLICATION_OVERLAY so that overlay window always ends
            // up taking the fullscreen bounds.
            attrs.type = TYPE_APPLICATION_OVERLAY;
            attrs.width = MATCH_PARENT;
            attrs.height = MATCH_PARENT;
            attrs.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            attrs.flags = FLAG_NOT_FOCUSABLE;
            attrs.setFitInsetsTypes(types);
            attrs.setFitInsetsSides(sides);
            attrs.setFitInsetsIgnoringVisibility(ignoreVis);
            mOverlayWindowRoot = new View(this);
            getWindowManager().addView(mOverlayWindowRoot, attrs);
        }

        void removeOverlayWindow() {
            getWindowManager().removeViewImmediate(mOverlayWindowRoot);
        }

        View getOverlayWindowRoot() {
            return mOverlayWindowRoot;
        }

        void assertMatchesWindowBounds() {
            final View rootView = getWindow().getDecorView();
            final Rect windowMetricsBounds =
                    getWindowManager().getCurrentWindowMetrics().getBounds();
            assertEquals(windowMetricsBounds.width(), rootView.getWidth());
            assertEquals(windowMetricsBounds.height(), rootView.getHeight());
            final int[] locationOnScreen = new int[2];
            rootView.getLocationOnScreen(locationOnScreen);
            assertEquals(locationOnScreen[0] /* expected x */, windowMetricsBounds.left);
            assertEquals(locationOnScreen[1] /* expected y */, windowMetricsBounds.top);
        }
    }
}
