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

package android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.view.WindowInsets.Type.displayCutout;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.test.core.app.ApplicationProvider;

import com.android.modules.utils.build.SdkLevel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Helper class to test {@link WindowMetrics} behaviors. */
public class WindowMetricsTestHelper {
    public static void assertMetricsMatchesLayout(WindowMetrics currentMetrics,
            WindowMetrics maxMetrics, Rect layoutBounds, WindowInsets layoutInsets) {
        assertMetricsMatchesLayout(currentMetrics, maxMetrics, layoutBounds, layoutInsets,
                false /* inMultiWindowMode */);
    }

    public static void assertMetricsMatchesLayout(WindowMetrics currentMetrics,
            WindowMetrics maxMetrics, Rect layoutBounds, WindowInsets layoutInsets,
            boolean inMultiWindowMode) {
        // Only validate the size portion of the bounds, regardless of the position on the screen to
        // take into consideration multiple screen devices (e.g. the dialog is on another screen)
        final Rect currentMetricsBounds = currentMetrics.getBounds();
        assertEquals(layoutBounds.height(), currentMetricsBounds.height());
        assertEquals(layoutBounds.width(), currentMetricsBounds.width());
        // Don't verify maxMetrics or insets for an Activity that is in multi-window mode.
        // This is because:
        // - Multi-window mode activities doesn't guarantee max window metrics bounds is larger than
        // the current window metrics bounds. The bounds of a multi-window mode activity is
        // unlimited except that it must be contained in display bounds.
        // - WindowMetrics always reports all possible insets, whereas LayoutInsets may not report
        // insets in multi-window mode. This means that when the Activity is in multi-window mode
        // (*not* fullscreen), the two insets can in fact be different.
        if (inMultiWindowMode) {
            return;
        }
        assertTrue(maxMetrics.getBounds().width()
                >= currentMetrics.getBounds().width());
        assertTrue(maxMetrics.getBounds().height()
                >= currentMetrics.getBounds().height());
        final int insetsType = statusBars() | navigationBars() | displayCutout();
        assertEquals(layoutInsets.getInsets(insetsType),
                currentMetrics.getWindowInsets().getInsets(insetsType));
        assertEquals(layoutInsets.getDisplayCutout(),
                currentMetrics.getWindowInsets().getDisplayCutout());
    }

    /**
     * Verifies two scenarios for a {@link Context}.
     * <ul>
     *     <li>{@link WindowManager#getCurrentWindowMetrics()} matches
     *     {@link Display#getSize(Point)}</li>
     *     <li>{@link WindowManager#getMaximumWindowMetrics()} and {@link Display#getSize(Point)}
     *     either matches DisplayArea bounds which the {@link Context} is attached to, or matches
     *     {@link WindowManager#getCurrentWindowMetrics()} if sandboxing is applied.</li>
     * </ul>
     * @param context the context under test
     * @param displayAreaBounds the bounds of the DisplayArea
     */
    public static void assertMetricsValidity(Context context, Rect displayAreaBounds) {
        final boolean isFreeForm = context instanceof Activity
                && context.getResources().getConfiguration().windowConfiguration
                .getWindowingMode() == WINDOWING_MODE_FREEFORM;
        final WindowManager windowManager = context.getSystemService(WindowManager.class);
        final WindowMetrics currentMetrics = windowManager.getCurrentWindowMetrics();
        final WindowMetrics maxMetrics = windowManager.getMaximumWindowMetrics();
        final Rect maxBounds = maxMetrics.getBounds();
        final Display display = context.getDisplay();
        assertMetricsMatchDisplay(maxMetrics, currentMetrics, display, isFreeForm);

        // Max window bounds should match either DisplayArea bounds, or current window bounds.
        if (maxWindowBoundsSandboxed(displayAreaBounds, maxBounds)) {
            final Rect currentBounds = isFreeForm ? currentMetrics.getBounds()
                    : getBoundsExcludingInsetsTypes(currentMetrics, configExcludedInsetsTypes());
            // Max window bounds are sandboxed, so max window bounds and real display size
            // should match current window bounds.
            assertEquals("Max window size matches current window size, due to sandboxing",
                    currentBounds, maxBounds);
        } else {
            // Max window bounds are not sandboxed, so max window bounds and real display size
            // should match display area bounds.
            assertEquals("Display area bounds must match max window size",
                    displayAreaBounds, maxBounds);
        }
    }

    /**
     * Verifies for the provided {@link WindowMetrics} and {@link Display}:
     * <ul>
     *     <li>Bounds and density from {@link WindowManager#getCurrentWindowMetrics()} matches
     *     {@link Display#getMetrics(DisplayMetrics)}</li>
     *     <li>Bounds and density from {@link WindowManager#getMaximumWindowMetrics()} matches
     *     {@link Display#getRealMetrics(DisplayMetrics)}
     *
     * </ul>
     * @param maxMetrics the {@link WindowMetrics} from
     *                  {@link WindowManager#getMaximumWindowMetrics()}
     * @param currentMetrics the {@link WindowMetrics} from
     *                      {@link WindowManager#getCurrentWindowMetrics()}
     * @param display the display to compare bounds against
     * @param shouldBoundsIncludeInsets whether the bounds to be verified should include insets
     */
    public static void assertMetricsMatchDisplay(
            WindowMetrics maxMetrics,
            WindowMetrics currentMetrics,
            Display display,
            boolean shouldBoundsIncludeInsets) {
        // Check window bounds
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        display.getMetrics(displayMetrics);
        final Rect currentBounds = shouldBoundsIncludeInsets ? currentMetrics.getBounds()
                : getBoundsExcludingInsetsTypes(currentMetrics, configExcludedInsetsTypes());
        assertEquals("Reported display width must match window width",
                displayMetrics.widthPixels, currentBounds.width());
        assertEquals("Reported display height must match window height",
                displayMetrics.heightPixels, currentBounds.height());
        assertEquals("Reported display density must match density from window metrics",
                displayMetrics.density, currentMetrics.getDensity(), 0.0f);

        // Max window bounds should match real display size.
        final DisplayMetrics realDisplayMetrics = new DisplayMetrics();
        display.getRealMetrics(realDisplayMetrics);
        final Rect maxBounds = maxMetrics.getBounds();
        assertEquals("Reported real display width must match max window width",
                realDisplayMetrics.widthPixels, maxBounds.width());
        assertEquals("Reported real display height must match max window height",
                realDisplayMetrics.heightPixels, maxBounds.height());
        assertEquals("Reported real display density must match density from window metrics",
                realDisplayMetrics.density, currentMetrics.getDensity(), 0.0f);
    }

    public static Rect getBoundsExcludingInsetsTypes(WindowMetrics windowMetrics,
            int excludingTypes) {
        WindowInsets windowInsets = windowMetrics.getWindowInsets();
        final Insets excludingInsets =
                windowInsets.getInsetsIgnoringVisibility(excludingTypes);

        final Rect bounds = windowMetrics.getBounds();
        return inset(bounds, excludingInsets);
    }

    /**
     * Returns {@code true} if the bounds from {@link WindowManager#getMaximumWindowMetrics()} are
     * sandboxed, so are smaller than the DisplayArea.
     */
    public static boolean maxWindowBoundsSandboxed(Rect displayAreaBounds, Rect maxBounds) {
        return maxBounds.width() < displayAreaBounds.width()
                || maxBounds.height() < displayAreaBounds.height();
    }

    private static Rect inset(Rect original, Insets insets) {
        final int left = original.left + insets.left;
        final int top = original.top + insets.top;
        final int right = original.right - insets.right;
        final int bottom = original.bottom - insets.bottom;
        return new Rect(left, top, right, bottom);
    }

    private static int configExcludedInsetsTypes() {
        // The device owners can choose to decouple the display cutout from the configuration app
        // bounds to ensure a better experience. Return the legacy value if the decouple config is
        // not set to true.
        final int legacyNonDecorTypes = navigationBars() | displayCutout();
        if (!SdkLevel.isAtLeastU()) {
            return legacyNonDecorTypes;
        }
        try {
            final boolean isScreenSizeDecoupledFromStatusBarAndCutout =
                    ApplicationProvider.getApplicationContext().getResources().getBoolean(
                            Resources.getSystem().getIdentifier(
                            "config_decoupleStatusBarAndDisplayCutoutFromScreenSize",
                            "bool", "android"));
            return isScreenSizeDecoupledFromStatusBarAndCutout
                    ? navigationBars() : legacyNonDecorTypes;
        } catch (Resources.NotFoundException e) {
            return legacyNonDecorTypes;
        }
    }

    public static class OnLayoutChangeListener implements View.OnLayoutChangeListener {
        private CountDownLatch mLayoutLatch = new CountDownLatch(1);

        private volatile Rect mOnLayoutBoundsInScreen;
        private volatile WindowInsets mOnLayoutInsets;

        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                int oldTop, int oldRight, int oldBottom) {
            synchronized (this) {
                mOnLayoutBoundsInScreen = new Rect(left, top, right, bottom);
                // Convert decorView's bounds from window coordinates to screen coordinates.
                final int[] locationOnScreen = new int[2];
                v.getLocationOnScreen(locationOnScreen);
                mOnLayoutBoundsInScreen.offset(locationOnScreen[0], locationOnScreen[1]);

                mOnLayoutInsets = v.getRootWindowInsets();
                mLayoutLatch.countDown();
            }
        }

        public Rect getLayoutBounds() {
            synchronized (this) {
                return mOnLayoutBoundsInScreen;
            }
        }

        public WindowInsets getLayoutInsets() {
            synchronized (this) {
                return mOnLayoutInsets;
            }
        }

        public void waitForLayout() {
            try {
                assertTrue("Timed out waiting for layout.",
                        mLayoutLatch.await(4, TimeUnit.SECONDS));
                mLayoutLatch = new CountDownLatch(1);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }
}
