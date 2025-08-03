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

package android.server.wm.window;

import static android.server.wm.WindowMetricsTestHelper.assertMetricsMatchesLayout;
import static android.server.wm.WindowMetricsTestHelper.assertMetricsValidity;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerTestBase;
import android.server.wm.WindowMetricsTestHelper.OnLayoutChangeListener;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

/**
 * Tests that verify the behavior of {@link WindowMetrics} APIs on {@link Context contexts} created
 * from {@link Context#createWindowContext(Display, int, Bundle)}.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceWindow:WindowMetricsWindowContextTests
 */
@Presubmit
@ApiTest(apis = {"android.view.WindowManager#getCurrentWindowMetrics",
        "android.view.WindowManager#getMaximumWindowMetrics",
        "android.content.Context#createWindowContext"})
public class WindowMetricsWindowContextTests extends WindowManagerTestBase {
    @Test
    public void testMetricsMatchesLayoutOnWindowContext() {
        createAllowSystemAlertWindowAppOpSession();
        final WindowContextTestSession mWindowContextSession =
                mObjectTracker.manage(new WindowContextTestSession());

        mWindowContextSession.assertWindowContextMetricsMatchesLayout();
    }

    @Test
    public void testMetricsMatchesDisplayAreaOnWindowContext() {
        createAllowSystemAlertWindowAppOpSession();
        final WindowContextTestSession mWindowContextSession =
                mObjectTracker.manage(new WindowContextTestSession());

        mWindowContextSession.assertWindowContextMetricsMatchesDisplayArea();
    }

    private class WindowContextTestSession implements AutoCloseable {
        private static final String TEST_WINDOW_NAME = "WindowMetricsTests";
        private View mView;
        private final Context mWindowContext;
        private final WindowManager mWm;
        private final OnLayoutChangeListener mListener = new OnLayoutChangeListener();

        private WindowContextTestSession() {
            final Context appContext = ApplicationProvider.getApplicationContext();
            final Display display = appContext.getSystemService(DisplayManager.class)
                    .getDisplay(DEFAULT_DISPLAY);
            mWindowContext = appContext.createDisplayContext(display)
                    .createWindowContext(TYPE_APPLICATION_OVERLAY, null /* options */);

            mWm = mWindowContext.getSystemService(WindowManager.class);

            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                mView = new View(mWindowContext);
                mView.addOnLayoutChangeListener(mListener);
                final WindowManager.LayoutParams params = getFullscreenOverlayAttributes();
                mWm.addView(mView, params);
            });
        }

        private void assertWindowContextMetricsMatchesLayout() {
            mListener.waitForLayout();

            final WindowMetrics currentMetrics = mWm.getCurrentWindowMetrics();
            final WindowMetrics maxMetrics = mWm.getMaximumWindowMetrics();

            assertMetricsMatchesLayout(currentMetrics, maxMetrics,
                    mListener.getLayoutBounds(), mListener.getLayoutInsets());
        }

        /**
         * Verifies two scenarios for a {@link android.window.WindowContext}.
         * <ul>
         *     <li>{@link WindowManager#getCurrentWindowMetrics()} matches
         *     {@link Display#getSize(Point)}</li>
         *     <li>{@link WindowManager#getMaximumWindowMetrics()} and
         *     {@link Display#getSize(Point)} either matches DisplayArea bounds, or matches
         *     {@link WindowManager#getCurrentWindowMetrics()} if sandboxing is applied.</li>
         * </ul>
         */
        private void assertWindowContextMetricsMatchesDisplayArea() {
            mWmState.computeState();
            WindowManagerState.DisplayArea da = mWmState.getDisplayArea(TEST_WINDOW_NAME);
            final Rect daBounds = da.getFullConfiguration().windowConfiguration.getBounds();
            assertMetricsValidity(mWindowContext, daBounds);
        }

        @Override
        public void close() throws Exception {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()
                    -> mWm.removeViewImmediate(mView));
            mView.removeOnLayoutChangeListener(mListener);
        }

        private WindowManager.LayoutParams getFullscreenOverlayAttributes() {
            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    MATCH_PARENT, MATCH_PARENT, TYPE_APPLICATION_OVERLAY, 0,
                    PixelFormat.TRANSLUCENT);
            // Used for obtain the attached DisplayArea.
            params.setTitle(TEST_WINDOW_NAME);
            params.setFitInsetsTypes(0 /* types */);
            params.setFitInsetsIgnoringVisibility(true);
            params.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            return params;
        }
    }
}
