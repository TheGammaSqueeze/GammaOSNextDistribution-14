package com.android.compatibility.common.util;
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


import static org.junit.Assert.assertNotNull;

import android.graphics.Rect;
import android.util.Log;
import android.util.TypedValue;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import java.util.Objects;
import java.util.regex.Pattern;

public class UiAutomatorUtils2 {
    private UiAutomatorUtils2() {}

    private static final String LOG_TAG = "UiAutomatorUtils";

    /** Default swipe deadzone percentage. See {@link UiScrollable}. */
    private static final double DEFAULT_SWIPE_DEADZONE_PCT_TV       = 0.1f;
    private static final double DEFAULT_SWIPE_DEADZONE_PCT_ALL      = 0.25f;
    /**
     * On Wear, some cts tests like CtsPermissionUiTestCases that run on
     * low performance device. Keep 0.05 to have better matching.
     */
    private static final double DEFAULT_SWIPE_DEADZONE_PCT_WEAR     = 0.05f;

    /** Minimum view height accepted (before needing to scroll more). */
    private static final float MIN_VIEW_HEIGHT_DP = 8;

    private static Pattern sCollapsingToolbarResPattern =
            Pattern.compile(".*:id/collapsing_toolbar");

    public static UiDevice getUiDevice() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    private static int convertDpToPx(float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                ApplicationProvider.getApplicationContext().getResources().getDisplayMetrics()));
    }

    private static double getSwipeDeadZonePct() {
        if (FeatureUtil.isTV()) {
            return DEFAULT_SWIPE_DEADZONE_PCT_TV;
        } else if (FeatureUtil.isWatch()) {
            return DEFAULT_SWIPE_DEADZONE_PCT_WEAR;
        } else {
            return DEFAULT_SWIPE_DEADZONE_PCT_ALL;
        }
    }

    public static void waitUntilObjectGone(BySelector selector) {
        waitUntilObjectGone(selector, 20_000);
    }

    public static void waitUntilObjectGone(BySelector selector, long timeoutMs) {
        try {
            if (getUiDevice().wait(Until.gone(selector), timeoutMs)) {
                return;
            }
        } catch (StaleObjectException exception) {
            // UiDevice.wait() may cause StaleObjectException if the {@link View} attached to
            // UiObject2 is no longer in the view tree.
            return;
        }

        throw new RuntimeException("view " + selector + " is still visible after " + timeoutMs
                + "ms");
    }

    public static UiObject2 waitFindObject(BySelector selector) throws UiObjectNotFoundException {
        return waitFindObject(selector, 20_000);
    }

    public static UiObject2 waitFindObject(BySelector selector, long timeoutMs)
            throws UiObjectNotFoundException {
        final UiObject2 view = waitFindObjectOrNull(selector, timeoutMs);
        ExceptionUtils.wrappingExceptions(UiDumpUtils::wrapWithUiDump, () -> {
            assertNotNull("View not found after waiting for " + timeoutMs + "ms: " + selector,
                    view);
        });
        return view;
    }

    public static UiObject2 waitFindObjectOrNull(BySelector selector)
            throws UiObjectNotFoundException {
        return waitFindObjectOrNull(selector, 20_000);
    }

    public static UiObject2 waitFindObjectOrNull(BySelector selector, long timeoutMs)
            throws UiObjectNotFoundException {
        UiObject2 view = null;
        long start = System.currentTimeMillis();

        boolean isAtEnd = false;
        boolean wasScrolledUpAlready = false;
        boolean scrolledPastCollapsibleToolbar = false;

        final int minViewHeightPx = convertDpToPx(MIN_VIEW_HEIGHT_DP);

        int viewHeight = -1;
        while (view == null && start + timeoutMs > System.currentTimeMillis()) {
            try {
                view = getUiDevice().wait(Until.findObject(selector), 1000);
                if (view != null) {
                    viewHeight = view.getVisibleBounds().height();
                }
            } catch (StaleObjectException exception) {
                // UiDevice.wait() may cause StaleObjectException if the {@link View} attached to
                // UiObject2 is no longer in the view tree.
                Log.v(LOG_TAG, "UiObject2 view is no longer in the view tree.", exception);
                getUiDevice().waitForIdle();
                continue;
            }

            if (view == null || viewHeight < minViewHeightPx) {
                final double deadZone = getSwipeDeadZonePct();
                UiScrollable scrollable = new UiScrollable(new UiSelector().scrollable(true));
                scrollable.setSwipeDeadZonePercentage(deadZone);
                if (scrollable.exists()) {
                    if (!scrolledPastCollapsibleToolbar) {
                        scrollPastCollapsibleToolbar(scrollable, deadZone);
                        scrolledPastCollapsibleToolbar = true;
                        continue;
                    }
                    if (isAtEnd) {
                        if (wasScrolledUpAlready) {
                            return null;
                        }
                        scrollable.scrollToBeginning(Integer.MAX_VALUE);
                        isAtEnd = false;
                        wasScrolledUpAlready = true;
                        scrolledPastCollapsibleToolbar = false;
                    } else {
                        Rect boundsBeforeScroll = scrollable.getBounds();
                        boolean scrollAtStartOrEnd;
                        boolean isWearCompose = FeatureUtil.isWatch() && Objects.equals(
                                scrollable.getPackageName(),
                                InstrumentationRegistry.getInstrumentation().getContext()
                                        .getPackageManager().getPermissionControllerPackageName());
                        if (isWearCompose) {
                            // TODO(b/306483780): Removed the condition once the scrollForward is
                            //  fixed.
                            scrollable.scrollForward();
                            scrollAtStartOrEnd = false;
                        } else {
                            scrollAtStartOrEnd = !scrollable.scrollForward();
                        }
                        // The scrollable view may no longer be scrollable after the toolbar is
                        // collapsed.
                        if (scrollable.exists()) {
                            Rect boundsAfterScroll = scrollable.getBounds();
                            isAtEnd = scrollAtStartOrEnd && boundsBeforeScroll.equals(
                                    boundsAfterScroll);
                        } else {
                            isAtEnd = scrollAtStartOrEnd;
                        }
                    }
                } else {
                    // There might be a collapsing toolbar, but no scrollable view. Try to collapse
                    scrollPastCollapsibleToolbar(null, deadZone);
                }
            }
        }
        return view;
    }

    private static void scrollPastCollapsibleToolbar(UiScrollable scrollable, double deadZone)
            throws UiObjectNotFoundException {
        final UiObject2 collapsingToolbar = getUiDevice().findObject(
                By.res(sCollapsingToolbarResPattern));
        if (collapsingToolbar == null) {
            return;
        }

        final int steps = 55; // == UiScrollable.SCROLL_STEPS
        if (scrollable != null && scrollable.exists()) {
            final Rect scrollableBounds = scrollable.getVisibleBounds();
            final int distanceToSwipe = collapsingToolbar.getVisibleBounds().height() / 2;
            getUiDevice().swipe(scrollableBounds.centerX(), scrollableBounds.centerY(),
                    scrollableBounds.centerX(), scrollableBounds.centerY() - distanceToSwipe,
                    steps);
        } else {
            // There might be a collapsing toolbar, but no scrollable view. Try to collapse
            int maxY = getUiDevice().getDisplayHeight();
            int minY = (int) (deadZone * maxY);
            maxY -= minY;
            getUiDevice().drag(0, maxY, 0, minY, steps);
        }
    }
}
