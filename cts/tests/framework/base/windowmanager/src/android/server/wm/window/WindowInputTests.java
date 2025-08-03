/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.server.wm.ActivityManagerTestBase.launchHomeActivityNoWait;
import static android.server.wm.BarTestUtils.assumeHasStatusBar;
import static android.server.wm.CtsWindowInfoUtils.waitForStableWindowGeometry;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowInfo;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowOnTop;
import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;
import static android.server.wm.app.Components.OverlayTestService.EXTRA_LAYOUT_PARAMS;
import static android.server.wm.window.WindowUntrustedTouchTest.MIN_POSITIVE_OPACITY;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.server.wm.WindowManagerState.WindowState;
import android.server.wm.WindowManagerStateHelper;
import android.server.wm.app.Components;
import android.server.wm.settings.SettingsSession;
import android.util.ArraySet;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.window.WindowInfosListenerForTest.WindowInfo;

import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.input.DebugInputRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Ensure moving windows and tapping is done synchronously.
 *
 * <p>Build/Install/Run: atest CtsWindowManagerDeviceWindow:WindowInputTests
 */
@Presubmit
public class WindowInputTests {
    private static final String TAG = "WindowInputTests";
    private final int TOTAL_NUMBER_OF_CLICKS = 100;
    private final ActivityTestRule<TestActivity> mActivityRule =
            new ActivityTestRule<>(TestActivity.class);
    private static final int TAPPING_TARGET_WINDOW_SIZE = 100;
    private static final int PARTIAL_OBSCURING_WINDOW_SIZE = 30;

    private Instrumentation mInstrumentation;
    private CtsTouchUtils mCtsTouchUtils;
    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();
    private TestActivity mActivity;
    private InputManager mInputManager;

    @Rule
    public DebugInputRule mDebugInputRule = new DebugInputRule();

    private View mView;
    private final Random mRandom = new Random(1);

    private int mClickCount = 0;
    private final long EVENT_FLAGS_WAIT_TIME = 2;

    @Before
    public void setUp() throws InterruptedException {
        pressWakeupButton();
        pressUnlockButton();
        launchHomeActivityNoWait();

        mInstrumentation = getInstrumentation();
        mCtsTouchUtils = new CtsTouchUtils(mInstrumentation.getTargetContext());
        mActivity = mActivityRule.launchActivity(null);
        mInputManager = mActivity.getSystemService(InputManager.class);
        mInstrumentation.waitForIdleSync();
        assertTrue("Failed to reach stable window geometry",
                waitForStableWindowGeometry(5, TimeUnit.SECONDS));
        mClickCount = 0;
    }

    @DebugInputRule.DebugInput(bug = 295885275)
    @Test
    public void testMoveWindowAndTap() throws Throwable {
        final WindowManager wm = mActivity.getWindowManager();
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();
        p.setFitInsetsTypes(WindowInsets.Type.systemBars() | WindowInsets.Type.systemGestures());
        p.flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        p.width = p.height = 20;
        p.gravity = Gravity.LEFT | Gravity.TOP;

        // Set up window.
        mActivityRule.runOnUiThread(
                () -> {
                    mView = new View(mActivity);
                    mView.setBackgroundColor(Color.RED);
                    mView.setOnClickListener(
                            (v) -> {
                                mClickCount++;
                            });
                    mActivity.addWindow(mView, p);
                });
        mInstrumentation.waitForIdleSync();
        assertTrue("Failed to reach stable window geometry",
                waitForStableWindowGeometry(5, TimeUnit.SECONDS));

        // The window location will be picked randomly from the selectBounds. Because the x, y of
        // LayoutParams is the offset from the gravity edge, make sure it offsets to (0,0) in case
        // the activity is not fullscreen, and insets system bar and window width.
        final WindowMetrics windowMetrics = wm.getCurrentWindowMetrics();
        final WindowInsets windowInsets = windowMetrics.getWindowInsets();
        final Rect selectBounds = new Rect(windowMetrics.getBounds());
        selectBounds.offsetTo(0, 0);
        final Insets insets = windowInsets.getInsetsIgnoringVisibility(p.getFitInsetsTypes());
        selectBounds.inset(
                0, 0, insets.left + insets.right + p.width, insets.top + insets.bottom + p.height);

        // Move the window to a random location in the window and attempt to tap on view multiple
        // times.
        final Point locationInWindow = new Point();
        for (int i = 0; i < TOTAL_NUMBER_OF_CLICKS; i++) {
            selectRandomLocationInWindow(selectBounds, locationInWindow);
            mActivityRule.runOnUiThread(
                    () -> {
                        p.x = locationInWindow.x;
                        p.y = locationInWindow.y;
                        wm.updateViewLayout(mView, p);
                    });
            mInstrumentation.waitForIdleSync();
            int previousCount = mClickCount;

            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

            mInstrumentation.waitForIdleSync();
            if (mClickCount != previousCount + 1) {
                final int vW = mView.getWidth();
                final int vH = mView.getHeight();
                final int[] viewOnScreenXY = new int[2];
                mView.getLocationOnScreen(viewOnScreenXY);
                final Point tapPosition =
                        new Point(viewOnScreenXY[0] + vW / 2, viewOnScreenXY[1] + vH / 2);
                final Rect realBounds =
                        new Rect(
                                viewOnScreenXY[0],
                                viewOnScreenXY[1],
                                viewOnScreenXY[0] + vW,
                                viewOnScreenXY[1] + vH);
                final Rect requestedBounds =
                        new Rect(
                                p.x + insets.left,
                                p.y + insets.top,
                                p.x + insets.left + p.width,
                                p.y + insets.top + p.height);
                dumpWindows("Dumping windows due to failure");
                fail(
                        "Tap #"
                                + i
                                + " on "
                                + tapPosition
                                + " failed; realBounds="
                                + realBounds
                                + " requestedBounds="
                                + requestedBounds);
            }
        }

        assertEquals(TOTAL_NUMBER_OF_CLICKS, mClickCount);
    }

    private void dumpWindows(String message) {
        Log.d(TAG, message);
        mWmState.computeState();
        for (WindowState window : mWmState.getWindows()) {
            Log.d(TAG, "    => " + window.toLongString());
        }
    }

    private void selectRandomLocationInWindow(Rect bounds, Point outLocation) {
        int randomX = mRandom.nextInt(bounds.right - bounds.left) + bounds.left;
        int randomY = mRandom.nextInt(bounds.bottom - bounds.top) + bounds.top;
        outLocation.set(randomX, randomY);
    }

    @Test
    public void testTouchModalWindow() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        // Set up 2 touch modal windows, expect the last one will receive all touch events.
        mActivityRule.runOnUiThread(
                () -> {
                    mView = new View(mActivity);
                    p.width = 20;
                    p.height = 20;
                    p.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
                    mView.setFilterTouchesWhenObscured(true);
                    mView.setOnClickListener(
                            (v) -> {
                                mClickCount++;
                            });
                    mActivity.addWindow(mView, p);

                    View view2 = new View(mActivity);
                    p.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
                    p.type = WindowManager.LayoutParams.TYPE_APPLICATION;
                    mActivity.addWindow(view2, p);
                });
        mInstrumentation.waitForIdleSync();
        assertTrue("Failed to reach stable window geometry",
                waitForStableWindowGeometry(5, TimeUnit.SECONDS));

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        assertEquals(0, mClickCount);
    }

    // If a window is obscured by another window from the same app, touches should still get
    // delivered to the bottom window, and the FLAG_WINDOW_IS_OBSCURED should not be set.
    @Test
    public void testFilterTouchesWhenObscuredByWindowFromSameUid() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        final AtomicBoolean touchReceived = new AtomicBoolean(false);
        final CompletableFuture<Integer> eventFlags = new CompletableFuture<>();
        // Set up a touchable window.
        mActivityRule.runOnUiThread(
                () -> {
                    mView = new View(mActivity);
                    p.flags = FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN;
                    p.width = 100;
                    p.height = 100;
                    p.gravity = Gravity.CENTER;
                    mView.setFilterTouchesWhenObscured(true);
                    mView.setOnClickListener(
                            (v) -> {
                                mClickCount++;
                            });
                    mView.setOnTouchListener(
                            (v, ev) -> {
                                touchReceived.set(true);
                                eventFlags.complete(ev.getFlags());
                                return false;
                            });
                    mActivity.addWindow(mView, p);

                    // Set up an overlap window, use same process.
                    View overlay = new View(mActivity);
                    p.flags = FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN | FLAG_NOT_TOUCHABLE;
                    p.width = 100;
                    p.height = 100;
                    p.gravity = Gravity.CENTER;
                    p.type = WindowManager.LayoutParams.TYPE_APPLICATION;
                    mActivity.addWindow(overlay, p);
                });
        mInstrumentation.waitForIdleSync();
        assertTrue("Failed to reach stable window geometry",
                waitForStableWindowGeometry(5, TimeUnit.SECONDS));
        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

        assertTrue(touchReceived.get());
        assertEquals(
                0,
                eventFlags.get(EVENT_FLAGS_WAIT_TIME, TimeUnit.SECONDS)
                        & MotionEvent.FLAG_WINDOW_IS_OBSCURED);
        assertEquals(1, mClickCount);
    }

    @Test
    public void testFilterTouchesWhenObscuredByWindowFromDifferentUid() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        final Intent intent = new Intent();
        intent.setComponent(Components.OVERLAY_TEST_SERVICE);
        final String windowName = "Test Overlay";
        final AtomicBoolean touchReceived = new AtomicBoolean(false);
        final int[] viewOnScreenLocation = new int[2];
        try {
            // Set up a touchable window.
            mActivityRule.runOnUiThread(
                    () -> {
                        mView = new View(mActivity);
                        p.flags = FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN;
                        p.width = TAPPING_TARGET_WINDOW_SIZE;
                        p.height = TAPPING_TARGET_WINDOW_SIZE;
                        p.gravity = Gravity.CENTER;
                        mView.setFilterTouchesWhenObscured(true);
                        mView.setOnClickListener(
                                (v) -> {
                                    mClickCount++;
                                });
                        mView.setOnTouchListener(
                                (v, ev) -> {
                                    touchReceived.set(true);
                                    return false;
                                });
                        mActivity.addWindow(mView, p);
                    });
            mInstrumentation.waitForIdleSync();
            mActivityRule.runOnUiThread(
                    () -> {
                        mView.getLocationOnScreen(viewOnScreenLocation);
                        // Set up an overlap window from service, use different process.
                        WindowManager.LayoutParams params =
                                getObscuringViewLayoutParams(windowName);
                        params.flags |= FLAG_NOT_TOUCHABLE;
                        placeWindowAtLayoutCenter(
                                params,
                                TAPPING_TARGET_WINDOW_SIZE,
                                viewOnScreenLocation[0],
                                viewOnScreenLocation[1],
                                TAPPING_TARGET_WINDOW_SIZE);
                        // Any opacity higher than this would make InputDispatcher block the touch
                        params.alpha = mInputManager.getMaximumObscuringOpacityForTouch();
                        params.setFitInsetsTypes(0);
                        intent.putExtra(EXTRA_LAYOUT_PARAMS, params);
                        mActivity.startForegroundService(intent);
                    });
            mInstrumentation.waitForIdleSync();
            waitForWindow(windowName);
            assertTrue("Failed to reach stable window geometry",
                    waitForStableWindowGeometry(5, TimeUnit.SECONDS));
            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

            // Touch not received due to setFilterTouchesWhenObscured(true)
            assertFalse(touchReceived.get());
            assertEquals(0, mClickCount);
        } finally {
            mActivity.stopService(intent);
        }
    }

    @Test
    public void testFlagTouchesWhenObscuredByWindowFromDifferentUid() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        final Intent intent = new Intent();
        intent.setComponent(Components.OVERLAY_TEST_SERVICE);
        final String windowName = "Test Overlay";
        final AtomicBoolean touchReceived = new AtomicBoolean(false);
        final CompletableFuture<Integer> eventFlags = new CompletableFuture<>();
        final int[] viewOnScreenLocation = new int[2];
        try {
            // Set up a touchable window.
            mActivityRule.runOnUiThread(
                    () -> {
                        mView = new View(mActivity);
                        p.flags = FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN;
                        p.width = TAPPING_TARGET_WINDOW_SIZE;
                        p.height = TAPPING_TARGET_WINDOW_SIZE;
                        p.gravity = Gravity.CENTER;
                        mView.setOnClickListener(
                                (v) -> {
                                    mClickCount++;
                                });
                        mView.setOnTouchListener(
                                (v, ev) -> {
                                    touchReceived.set(true);
                                    eventFlags.complete(ev.getFlags());
                                    return false;
                                });
                        mActivity.addWindow(mView, p);
                    });
            mInstrumentation.waitForIdleSync();
            mActivityRule.runOnUiThread(
                    () -> {
                        mView.getLocationOnScreen(viewOnScreenLocation);
                        // Set up an overlap window from service, use different process.
                        WindowManager.LayoutParams params =
                                getObscuringViewLayoutParams(windowName);
                        params.flags |= FLAG_NOT_TOUCHABLE;
                        // Any opacity higher than this would make InputDispatcher block the touch
                        params.alpha = mInputManager.getMaximumObscuringOpacityForTouch();
                        placeWindowAtLayoutCenter(
                                params,
                                TAPPING_TARGET_WINDOW_SIZE,
                                viewOnScreenLocation[0],
                                viewOnScreenLocation[1],
                                TAPPING_TARGET_WINDOW_SIZE);
                        params.setFitInsetsTypes(0);
                        intent.putExtra(EXTRA_LAYOUT_PARAMS, params);
                        mActivity.startForegroundService(intent);
                    });
            mInstrumentation.waitForIdleSync();
            waitForWindow(windowName);
            assertTrue("Failed to reach stable window geometry",
                    waitForStableWindowGeometry(5, TimeUnit.SECONDS));

            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

            assertTrue(touchReceived.get());
            assertEquals(
                    MotionEvent.FLAG_WINDOW_IS_OBSCURED,
                    eventFlags.get(EVENT_FLAGS_WAIT_TIME, TimeUnit.SECONDS)
                            & MotionEvent.FLAG_WINDOW_IS_OBSCURED);
            assertEquals(1, mClickCount);
        } finally {
            mActivity.stopService(intent);
        }
    }

    @Test
    public void testDoNotFlagTouchesWhenObscuredByZeroOpacityWindow() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        final Intent intent = new Intent();
        intent.setComponent(Components.OVERLAY_TEST_SERVICE);
        final String windowName = "Test Overlay";
        final AtomicBoolean touchReceived = new AtomicBoolean(false);
        final CompletableFuture<Integer> eventFlags = new CompletableFuture<>();

        try {
            mActivityRule.runOnUiThread(
                    () -> {
                        mView = new View(mActivity);
                        mView.setBackgroundColor(Color.GREEN);
                        p.flags = FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN;
                        p.width = 100;
                        p.height = 100;
                        p.gravity = Gravity.CENTER;
                        mView.setOnClickListener(
                                (v) -> {
                                    mClickCount++;
                                });
                        mView.setOnTouchListener(
                                (v, ev) -> {
                                    touchReceived.set(true);
                                    eventFlags.complete(ev.getFlags());
                                    return false;
                                });
                        mActivity.addWindow(mView, p);

                        // Set up an overlap window from service, use different process.
                        WindowManager.LayoutParams params =
                                getObscuringViewLayoutParams(windowName);
                        params.flags |= FLAG_NOT_TOUCHABLE;
                        params.alpha = 0;
                        intent.putExtra(EXTRA_LAYOUT_PARAMS, params);
                        mActivity.startForegroundService(intent);
                    });
            mInstrumentation.waitForIdleSync();
            waitForWindow(windowName);
            assertTrue("Failed to reach stable window geometry",
                    waitForStableWindowGeometry(5, TimeUnit.SECONDS));
            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

            assertTrue(touchReceived.get());
            assertEquals(
                    0,
                    eventFlags.get(EVENT_FLAGS_WAIT_TIME, TimeUnit.SECONDS)
                            & MotionEvent.FLAG_WINDOW_IS_OBSCURED);
            assertEquals(1, mClickCount);
        } finally {
            mActivity.stopService(intent);
        }
    }

    @Test
    public void testFlagTouchesWhenObscuredByMinPositiveOpacityWindow() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();
        final CompletableFuture<Integer> eventFlags = new CompletableFuture<>();
        final Intent intent = new Intent();
        intent.setComponent(Components.OVERLAY_TEST_SERVICE);
        final String windowName = "Test Overlay";
        final AtomicBoolean touchReceived = new AtomicBoolean(false);
        final int[] viewOnScreenLocation = new int[2];
        try {
            mActivityRule.runOnUiThread(
                    () -> {
                        mView = new View(mActivity);
                        mView.setBackgroundColor(Color.GREEN);
                        p.flags = FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN;
                        p.width = TAPPING_TARGET_WINDOW_SIZE;
                        p.height = TAPPING_TARGET_WINDOW_SIZE;
                        p.gravity = Gravity.CENTER;
                        mView.setOnClickListener(
                                (v) -> {
                                    mClickCount++;
                                });
                        mView.setOnTouchListener(
                                (v, ev) -> {
                                    touchReceived.set(true);
                                    eventFlags.complete(ev.getFlags());
                                    return false;
                                });
                        mActivity.addWindow(mView, p);
                    });
            mInstrumentation.waitForIdleSync();
            mActivityRule.runOnUiThread(
                    () -> {
                        mView.getLocationOnScreen(viewOnScreenLocation);
                        // Set up an overlap window from service, use different process.
                        WindowManager.LayoutParams params =
                                getObscuringViewLayoutParams(windowName);
                        params.flags |= FLAG_NOT_TOUCHABLE;
                        params.alpha = MIN_POSITIVE_OPACITY;
                        placeWindowAtLayoutCenter(
                                params,
                                TAPPING_TARGET_WINDOW_SIZE,
                                viewOnScreenLocation[0],
                                viewOnScreenLocation[1],
                                TAPPING_TARGET_WINDOW_SIZE);
                        params.setFitInsetsTypes(0);
                        intent.putExtra(EXTRA_LAYOUT_PARAMS, params);
                        mActivity.startForegroundService(intent);
                    });
            mInstrumentation.waitForIdleSync();
            waitForWindow(windowName);
            assertTrue("Failed to reach stable window geometry",
                    waitForStableWindowGeometry(5, TimeUnit.SECONDS));
            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

            assertTrue(touchReceived.get());
            assertEquals(
                    MotionEvent.FLAG_WINDOW_IS_OBSCURED,
                    eventFlags.get(EVENT_FLAGS_WAIT_TIME, TimeUnit.SECONDS)
                            & MotionEvent.FLAG_WINDOW_IS_OBSCURED);
            assertEquals(1, mClickCount);
        } finally {
            mActivity.stopService(intent);
        }
    }

    @DebugInputRule.DebugInput(bug = 295884840)
    @Test
    public void testFlagTouchesWhenPartiallyObscuredByZeroOpacityWindow() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        final Intent intent = new Intent();
        intent.setComponent(Components.OVERLAY_TEST_SERVICE);
        final String windowName = "Test Overlay";
        final AtomicBoolean touchReceived = new AtomicBoolean(false);
        final CompletableFuture<Integer> eventFlags = new CompletableFuture<>();
        final int[] viewOnScreenLocation = new int[2];
        try {
            mActivityRule.runOnUiThread(
                    () -> {
                        mView = new View(mActivity);
                        mView.setBackgroundColor(Color.GREEN);
                        p.flags = FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN;
                        p.width = TAPPING_TARGET_WINDOW_SIZE;
                        p.height = TAPPING_TARGET_WINDOW_SIZE;
                        p.gravity = Gravity.CENTER;
                        mView.setOnClickListener(
                                (v) -> {
                                    mClickCount++;
                                });
                        mView.setOnTouchListener(
                                (v, ev) -> {
                                    touchReceived.set(true);
                                    eventFlags.complete(ev.getFlags());
                                    return false;
                                });
                        mActivity.addWindow(mView, p);
                    });
            mInstrumentation.waitForIdleSync();
            mActivityRule.runOnUiThread(
                    () -> {
                        mView.getLocationOnScreen(viewOnScreenLocation);
                        // Set up an overlap window from service, use different process.
                        WindowManager.LayoutParams params =
                                getObscuringViewLayoutParams(
                                        windowName, PARTIAL_OBSCURING_WINDOW_SIZE);
                        placeWindowAtLayoutCenter(
                                params,
                                PARTIAL_OBSCURING_WINDOW_SIZE,
                                viewOnScreenLocation[0],
                                viewOnScreenLocation[1],
                                TAPPING_TARGET_WINDOW_SIZE);
                        // Move it off the touch path (center) but still overlap with window above
                        params.y += PARTIAL_OBSCURING_WINDOW_SIZE;
                        params.setFitInsetsTypes(0);
                        intent.putExtra(EXTRA_LAYOUT_PARAMS, params);
                        mActivity.startForegroundService(intent);
                    });
            mInstrumentation.waitForIdleSync();
            waitForWindow(windowName);
            assertTrue("Failed to reach stable window geometry",
                    waitForStableWindowGeometry(5, TimeUnit.SECONDS));
            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

            assertTrue(touchReceived.get());
            assertEquals(
                    MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED,
                    eventFlags.get(EVENT_FLAGS_WAIT_TIME, TimeUnit.SECONDS)
                            & MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED);
            assertEquals(1, mClickCount);
        } finally {
            mActivity.stopService(intent);
        }
    }

    @Test
    public void testDoNotFlagTouchesWhenPartiallyObscuredByNotTouchableZeroOpacityWindow()
            throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        final Intent intent = new Intent();
        intent.setComponent(Components.OVERLAY_TEST_SERVICE);
        final String windowName = "Test Overlay";
        final AtomicBoolean touchReceived = new AtomicBoolean(false);
        final CompletableFuture<Integer> eventFlags = new CompletableFuture<>();

        try {
            mActivityRule.runOnUiThread(
                    () -> {
                        mView = new View(mActivity);
                        mView.setBackgroundColor(Color.GREEN);
                        p.flags = FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN;
                        p.width = 100;
                        p.height = 100;
                        p.gravity = Gravity.CENTER;
                        mView.setOnClickListener(
                                (v) -> {
                                    mClickCount++;
                                });
                        mView.setOnTouchListener(
                                (v, ev) -> {
                                    touchReceived.set(true);
                                    eventFlags.complete(ev.getFlags());
                                    return false;
                                });
                        mActivity.addWindow(mView, p);

                        // Set up an overlap window from service, use different process.
                        WindowManager.LayoutParams params =
                                getObscuringViewLayoutParams(windowName, 30);
                        params.flags |= FLAG_NOT_TOUCHABLE;
                        // Move it off the touch path (center) but still overlap with window above
                        params.y = 30;
                        params.alpha = 0;
                        intent.putExtra(EXTRA_LAYOUT_PARAMS, params);
                        mActivity.startForegroundService(intent);
                    });
            mInstrumentation.waitForIdleSync();
            waitForWindow(windowName);
            assertTrue("Failed to reach stable window geometry",
                    waitForStableWindowGeometry(5, TimeUnit.SECONDS));

            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

            assertTrue(touchReceived.get());
            assertEquals(
                    0,
                    eventFlags.get(EVENT_FLAGS_WAIT_TIME, TimeUnit.SECONDS)
                            & MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED);
            assertEquals(1, mClickCount);
        } finally {
            mActivity.stopService(intent);
        }
    }

    private WindowManager.LayoutParams getObscuringViewLayoutParams(String windowName) {
        return getObscuringViewLayoutParams(windowName, 100);
    }

    private WindowManager.LayoutParams getObscuringViewLayoutParams(String windowName, int size) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.setTitle(windowName);
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        params.flags = FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN;
        params.width = size;
        params.height = size;
        params.gravity = Gravity.CENTER;
        return params;
    }

    @Test
    public void testTrustedOverlapWindow() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();
        try (final PointerLocationSession session = new PointerLocationSession()) {
            session.set(true);
            session.waitForReady(mActivity.getDisplayId());

            // Set up window.
            mActivityRule.runOnUiThread(
                    () -> {
                        mView = new View(mActivity);
                        p.width = 20;
                        p.height = 20;
                        p.gravity = Gravity.CENTER;
                        mView.setFilterTouchesWhenObscured(true);
                        mView.setOnClickListener(
                                (v) -> {
                                    mClickCount++;
                                });
                        mActivity.addWindow(mView, p);
                    });
            mInstrumentation.waitForIdleSync();
            assertTrue("Failed to reach stable window geometry",
                    waitForStableWindowGeometry(5, TimeUnit.SECONDS));

            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        }
        assertEquals(1, mClickCount);
    }

    @Test
    public void testWindowBecomesUnTouchable() throws Throwable {
        final WindowManager wm = mActivity.getWindowManager();
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        final View viewOverlap = new View(mActivity);

        // Set up window.
        mActivityRule.runOnUiThread(
                () -> {
                    mView = new View(mActivity);
                    p.width = 20;
                    p.height = 20;
                    p.gravity = Gravity.CENTER;
                    mView.setOnClickListener(
                            (v) -> {
                                mClickCount++;
                            });
                    mActivity.addWindow(mView, p);

                    p.width = 100;
                    p.height = 100;
                    p.gravity = Gravity.CENTER;
                    p.type = WindowManager.LayoutParams.TYPE_APPLICATION;
                    mActivity.addWindow(viewOverlap, p);
                });
        mInstrumentation.waitForIdleSync();
        assertTrue("Failed to reach stable window geometry",
                waitForStableWindowGeometry(5, TimeUnit.SECONDS));

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        assertEquals(0, mClickCount);

        mActivityRule.runOnUiThread(
                () -> {
                    p.flags = FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE;
                    wm.updateViewLayout(viewOverlap, p);
                });
        mInstrumentation.waitForIdleSync();
        Predicate<WindowInfo> hasInputConfigFlags =
                windowInfo -> !windowInfo.isTouchable && !windowInfo.isFocusable;
        assertTrue(waitForWindowInfo(hasInputConfigFlags, 5, TimeUnit.SECONDS,
                viewOverlap::getWindowToken, viewOverlap.getDisplay().getDisplayId()));

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        assertEquals(1, mClickCount);
    }

    @Test
    public void testTapInsideUntouchableWindowResultInOutsideTouches() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        final Set<MotionEvent> events = new ArraySet<>();
        mActivityRule.runOnUiThread(
                () -> {
                    mView = new View(mActivity);
                    p.width = 20;
                    p.height = 20;
                    p.gravity = Gravity.CENTER;
                    p.flags = FLAG_NOT_TOUCHABLE | FLAG_WATCH_OUTSIDE_TOUCH;
                    mView.setOnTouchListener(
                            (v, e) -> {
                                // Copying to make sure we are not dealing with a reused object
                                events.add(MotionEvent.obtain(e));
                                return false;
                            });
                    mActivity.addWindow(mView, p);
                });
        mInstrumentation.waitForIdleSync();
        assertTrue("Failed to reach stable window geometry",
                waitForStableWindowGeometry(5, TimeUnit.SECONDS));

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

        assertEquals(1, events.size());
        MotionEvent event = events.iterator().next();
        assertEquals(MotionEvent.ACTION_OUTSIDE, event.getAction());
    }

    @Test
    public void testTapOutsideUntouchableWindowResultInOutsideTouches() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        Set<MotionEvent> events = new ArraySet<>();
        int size = 20;
        mActivityRule.runOnUiThread(
                () -> {
                    mView = new View(mActivity);
                    p.width = size;
                    p.height = size;
                    p.gravity = Gravity.CENTER;
                    p.flags = FLAG_NOT_TOUCHABLE | FLAG_WATCH_OUTSIDE_TOUCH;
                    mView.setOnTouchListener(
                            (v, e) -> {
                                // Copying to make sure we are not dealing with a reused object
                                events.add(MotionEvent.obtain(e));
                                return false;
                            });
                    mActivity.addWindow(mView, p);
                });
        mInstrumentation.waitForIdleSync();
        assertTrue("Failed to reach stable window geometry",
                waitForStableWindowGeometry(5, TimeUnit.SECONDS));

        mCtsTouchUtils.emulateTapOnView(mInstrumentation, mActivityRule, mView, size + 5, size + 5);

        assertEquals(1, events.size());
        MotionEvent event = events.iterator().next();
        assertEquals(MotionEvent.ACTION_OUTSIDE, event.getAction());
    }

    @Test
    public void testInjectToStatusBar() {
        // Try to inject event to status bar.
        assumeHasStatusBar(mActivityRule);
        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent eventHover =
                MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_HOVER_MOVE, 0, 0, 0);
        eventHover.setSource(InputDevice.SOURCE_MOUSE);
        try {
            mInstrumentation.sendPointerSync(eventHover);
            fail("Not allowed to inject to windows owned by another uid from Instrumentation.");
        } catch (RuntimeException e) {
            // Should not be allowed to inject event to a window owned by another uid from the
            // Instrumentation class.
        }
    }

    @Test
    public void testInjectFromThread() throws InterruptedException {
        assertTrue("Window did not become visible", waitForWindowOnTop(mActivity.getWindow()));

        // Continually inject event to activity from thread.
        final int[] decorViewLocation = new int[2];
        final View decorView = mActivity.getWindow().getDecorView();
        decorView.getLocationOnScreen(decorViewLocation);
        // Tap at the center of the view. Calculate and tap at the absolute view center location on
        // screen, so that the tapping location is always as expected regardless of windowing mode.
        final Point testPoint =
                new Point(
                        decorViewLocation[0] + decorView.getWidth() / 2,
                        decorViewLocation[1] + decorView.getHeight() / 2);

        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent eventDown =
                MotionEvent.obtain(
                        downTime,
                        downTime,
                        MotionEvent.ACTION_DOWN,
                        testPoint.x,
                        testPoint.y,
                        /* metaState= */ 0);
        mInstrumentation.sendPointerSync(eventDown);

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        boolean[] securityExceptionCaught = new boolean[1];
        Exception[] illegalArgumentException = new Exception[1];
        executor.execute(
                () -> {
                    for (int i = 0; i < 20; i++) {
                        final long eventTime = SystemClock.uptimeMillis();
                        final MotionEvent eventMove =
                                MotionEvent.obtain(
                                        downTime,
                                        eventTime,
                                        MotionEvent.ACTION_MOVE,
                                        testPoint.x,
                                        testPoint.y,
                                        /* metaState= */ 0);
                        try {
                            mInstrumentation.sendPointerSync(eventMove);
                        } catch (SecurityException e) {
                            securityExceptionCaught[0] = true;
                            return;
                        } catch (IllegalArgumentException e) {
                            // InputManagerService throws this exception when input target does not
                            // match.
                            // Store the exception, and raise test failure later to avoid cts thread
                            // crash.
                            illegalArgumentException[0] = e;
                            return;
                        }
                    }
                });

        // Launch another activity, should not crash the process.
        final Intent intent = new Intent(mActivity, TestActivity.class);
        mActivityRule.launchActivity(intent);
        mInstrumentation.waitForIdleSync();

        executor.shutdown();
        executor.awaitTermination(5L, TimeUnit.SECONDS);

        if (securityExceptionCaught[0]) {
            // Fail the test here instead of in the executor lambda,
            // so the failure is thrown in the test thread.
            fail("Should be allowed to inject event.");
        }

        if (illegalArgumentException[0] != null) {
            fail(
                    "Failed to inject event due to input target mismatch: "
                            + illegalArgumentException[0].getMessage());
        }
    }

    private void waitForWindow(String name) {
        mWmState.waitAndAssertWindowSurfaceShown(name, true);
    }

    public static class TestActivity extends Activity {
        private ArrayList<View> mViews = new ArrayList<>();

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        void addWindow(View view, WindowManager.LayoutParams attrs) {
            getWindowManager().addView(view, attrs);
            mViews.add(view);
        }

        void removeAllWindows() {
            for (View view : mViews) {
                getWindowManager().removeViewImmediate(view);
            }
            mViews.clear();
        }

        @Override
        protected void onPause() {
            super.onPause();
            removeAllWindows();
        }
    }

    /** Set a square window to display at the center of a square layout */
    static void placeWindowAtLayoutCenter(
            WindowManager.LayoutParams windowParams,
            int windowWidth,
            int layoutLeft,
            int layoutTop,
            int layoutWidth) {
        windowParams.gravity = Gravity.TOP | Gravity.LEFT;
        int offset = (layoutWidth - windowWidth) / 2;
        windowParams.x = layoutLeft + offset;
        windowParams.y = layoutTop + offset;
    }

    /** Helper class to save, set, and restore pointer location preferences. */
    private static class PointerLocationSession extends SettingsSession<Boolean> {
        PointerLocationSession() {
            super(
                    Settings.System.getUriFor("pointer_location" /* POINTER_LOCATION */),
                    PointerLocationSession::get,
                    PointerLocationSession::put);
        }

        private static void put(ContentResolver contentResolver, String s, boolean v) {
            SystemUtil.runShellCommand(
                    "settings put system " + "pointer_location" + " " + (v ? 1 : 0));
        }

        private static boolean get(ContentResolver contentResolver, String s) {
            try {
                return Integer.parseInt(
                                SystemUtil.runShellCommand(
                                                "settings get system " + "pointer_location")
                                        .trim())
                        == 1;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // Wait until pointer location surface shown.
        static void waitForReady(int displayId) {
            final WindowManagerStateHelper wmState = new WindowManagerStateHelper();
            final String windowName = "PointerLocation - display " + displayId;
            wmState.waitForWithAmState(
                    state -> {
                        return state.isWindowSurfaceShown(windowName);
                    },
                    windowName + "'s surface is appeared");
        }
    }
}
