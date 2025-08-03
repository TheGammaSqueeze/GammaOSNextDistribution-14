/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm.window;

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_FOCUS;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED;
import static android.server.wm.CtsWindowInfoUtils.waitForStableWindowGeometry;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.FLAG_CANCELED;
import static android.view.KeyEvent.KEYCODE_0;
import static android.view.KeyEvent.KEYCODE_1;
import static android.view.KeyEvent.KEYCODE_2;
import static android.view.KeyEvent.KEYCODE_3;
import static android.view.KeyEvent.KEYCODE_4;
import static android.view.KeyEvent.KEYCODE_5;
import static android.view.KeyEvent.KEYCODE_6;
import static android.view.KeyEvent.KEYCODE_7;
import static android.view.KeyEvent.KEYCODE_8;
import static android.view.KeyEvent.KEYCODE_9;
import static android.view.KeyEvent.keyCodeToString;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.server.wm.BuildUtils;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerTestBase;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager.LayoutParams;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.GuardedBy;

/**
 * Ensure window focus assignment is executed as expected.
 *
 * Build/Install/Run:
 *     atest WindowFocusTests
 */
@Presubmit
public class WindowFocusTests extends WindowManagerTestBase {

    private static void sendKey(int action, int keyCode, int displayId) {
        final KeyEvent keyEvent = new KeyEvent(action, keyCode);
        keyEvent.setDisplayId(displayId);
        getInstrumentation().sendKeySync(keyEvent);
    }

    private static void sendAndAssertTargetConsumedKey(InputTargetActivity target, int keyCode,
            int targetDisplayId) {
        sendAndAssertTargetConsumedKey(target, ACTION_DOWN, keyCode, targetDisplayId);
        sendAndAssertTargetConsumedKey(target, ACTION_UP, keyCode, targetDisplayId);
    }

    private static void sendAndAssertTargetConsumedKey(InputTargetActivity target, int action,
            int keyCode, int targetDisplayId) {
        final int eventCount = target.getKeyEventCount();
        sendKey(action, keyCode, targetDisplayId);
        target.assertAndConsumeKeyEvent(action, keyCode, 0 /* flags */);
        assertEquals(target.getLogTag() + " must only receive key event sent.", eventCount,
                target.getKeyEventCount());
    }

    private static void tapOn(@NonNull Activity activity) {
        final Point p = getCenterOfActivityOnScreen(activity);
        final int displayId = activity.getDisplayId();

        final long downTime = SystemClock.elapsedRealtime();
        final MotionEvent downEvent = MotionEvent.obtain(downTime, downTime,
                MotionEvent.ACTION_DOWN, p.x, p.y, 0 /* metaState */);
        downEvent.setDisplayId(displayId);
        getInstrumentation().sendPointerSync(downEvent);
        final MotionEvent upEvent = MotionEvent.obtain(downTime, SystemClock.elapsedRealtime(),
                MotionEvent.ACTION_UP, p.x, p.y, 0 /* metaState */);
        upEvent.setDisplayId(displayId);
        getInstrumentation().sendPointerSync(upEvent);
    }

    private static Point getCenterOfActivityOnScreen(@NonNull Activity activity) {
        final View decorView = activity.getWindow().getDecorView();
        final int[] location = new int[2];
        decorView.getLocationOnScreen(location);
        return new Point(location[0] + decorView.getWidth() / 2,
                location[1] + decorView.getHeight() / 2);
    }

    /**
     * Test the following conditions:
     * - Each display can have a focused window at the same time.
     * - Focused windows can receive display-specified key events.
     * - The top focused window can receive display-unspecified key events.
     * - Taping on a display will make the focused window on it become top-focused.
     * - The window which lost top-focus can receive display-unspecified cancel events.
     */
    @Test
    public void testKeyReceiving() {
        final PrimaryActivity primaryActivity = startActivity(PrimaryActivity.class,
                DEFAULT_DISPLAY);
        sendAndAssertTargetConsumedKey(primaryActivity, KEYCODE_0, INVALID_DISPLAY);
        sendAndAssertTargetConsumedKey(primaryActivity, KEYCODE_1, DEFAULT_DISPLAY);

        assumeTrue(supportsMultiDisplay());

        // VirtualDisplay can't maintain perDisplayFocus because it is not trusted,
        // so uses SimulatedDisplay instead.
        final SimulatedDisplaySession session = createManagedSimulatedDisplaySession();
        final int secondaryDisplayId = session.getDisplayId();
        final SecondaryActivity secondaryActivity = session.startActivityAndFocus();
        sendAndAssertTargetConsumedKey(secondaryActivity, KEYCODE_2, INVALID_DISPLAY);
        sendAndAssertTargetConsumedKey(secondaryActivity, KEYCODE_3, secondaryDisplayId);

        // After launching the second activity the primary activities focus depends on the state of
        // perDisplayFocusEnabled. If the display has its own focus, then the activities still has
        // window focus. If it is disabled, then primary activity should no longer have window focus
        // because the secondary activity got it.
        primaryActivity.waitAndAssertWindowFocusState(perDisplayFocusEnabled());

        // Press display-unspecified keys and a display-specified key but not release them.
        sendKey(ACTION_DOWN, KEYCODE_5, INVALID_DISPLAY);
        sendKey(ACTION_DOWN, KEYCODE_6, secondaryDisplayId);
        sendKey(ACTION_DOWN, KEYCODE_7, INVALID_DISPLAY);
        secondaryActivity.assertAndConsumeKeyEvent(ACTION_DOWN, KEYCODE_5, 0 /* flags */);
        secondaryActivity.assertAndConsumeKeyEvent(ACTION_DOWN, KEYCODE_6, 0 /* flags */);
        secondaryActivity.assertAndConsumeKeyEvent(ACTION_DOWN, KEYCODE_7, 0 /* flags */);

        tapOn(primaryActivity);

        // Assert only display-unspecified key would be cancelled after secondary activity is
        // not top focused if per-display focus is enabled. Otherwise, assert all non-released
        // key events sent to secondary activity would be cancelled.
        secondaryActivity.waitAssertAndConsumeKeyEvent(ACTION_UP, KEYCODE_5, FLAG_CANCELED);
        secondaryActivity.waitAssertAndConsumeKeyEvent(ACTION_UP, KEYCODE_7, FLAG_CANCELED);
        if (!perDisplayFocusEnabled()) {
            secondaryActivity.waitAssertAndConsumeKeyEvent(ACTION_UP, KEYCODE_6, FLAG_CANCELED);
        }
        assertEquals(secondaryActivity.getLogTag() + " must only receive expected events",
                0 /* expected event count */, secondaryActivity.getKeyEventCount());

        // Assert primary activity become top focused after tapping on default display.
        sendAndAssertTargetConsumedKey(primaryActivity, KEYCODE_8, INVALID_DISPLAY);
    }

    @Test
    public void testKeyReceivingWithDisplayWithOwnFocus() {
        assumeTrue(supportsMultiDisplay());
        // This test specifically tests the behavior if a single display manages its own focus.
        // Key receiving with perDisplayFocusEnabled is handled in #testKeyReceiving()
        assumeFalse(perDisplayFocusEnabled());

        final PrimaryActivity primaryActivity = startActivity(PrimaryActivity.class,
                DEFAULT_DISPLAY);

        final VirtualDisplayWithOwnFocusSession session =
                createManagedVirtualDisplayWithOwnFocusSession();
        final int secondaryDisplayId = session.getDisplayId();
        final SecondaryActivity secondaryActivity = session.startActivityAndFocus(
                SecondaryActivity.class);

        // The secondary display and activity gained focus; the window on default display
        // has no longer focus because the secondary display is also the top display.
        primaryActivity.waitAndAssertWindowFocusState(/* hasFocus= */ false);
        secondaryActivity.waitAndAssertWindowFocusState(/* hasFocus= */ true);

        sendAndAssertTargetConsumedKey(secondaryActivity, KEYCODE_0, INVALID_DISPLAY);
        sendAndAssertTargetConsumedKey(secondaryActivity, KEYCODE_1, secondaryDisplayId);

        // Send a key event to the primary activity on the default display to make it the top
        // focused display.; the secondary ones did not lose window focus.
        sendAndAssertTargetConsumedKey(primaryActivity, KEYCODE_6, DEFAULT_DISPLAY);
        primaryActivity.waitAndAssertWindowFocusState(/* hasFocus= */ true);
        secondaryActivity.waitAndAssertWindowFocusState(/* hasFocus= */ true);

        // Assert primary activity become top focused after sending targeted key to default display
        sendAndAssertTargetConsumedKey(primaryActivity, KEYCODE_8, INVALID_DISPLAY);
        // And targeted keys to the secondary display should still arrive at the secondary
        sendAndAssertTargetConsumedKey(secondaryActivity, KEYCODE_9, secondaryDisplayId);

        assertEquals(secondaryActivity.getLogTag() + " must only receive expected events",
                0 /* expected event count */, secondaryActivity.getKeyEventCount());
    }

    /**
     * Test the {@link Display#FLAG_OWN_FOCUS} behavior.
     * The flag is similar to {@link #perDisplayFocusEnabled()} but instead of affecting all
     * displays it only affects the displays that have the flag set.
     */
    @Test
    public void testOwnFocus() {
        assumeTrue(supportsMultiDisplay());

        final PrimaryActivity primaryActivity = startActivity(PrimaryActivity.class,
                DEFAULT_DISPLAY);

        // Create two VirtualDisplays with its own focus and launch an activity on them
        final VirtualDisplayWithOwnFocusSession secondarySession =
                createManagedVirtualDisplayWithOwnFocusSession();
        final SecondaryActivity secondaryActivity = secondarySession.startActivityAndFocus(
                SecondaryActivity.class);
        final VirtualDisplayWithOwnFocusSession tertiarySession =
                createManagedVirtualDisplayWithOwnFocusSession();
        final TertiaryActivity tertiaryActivity = tertiarySession.startActivityAndFocus(
                TertiaryActivity.class);

        // The primary activity will have window focus based on perDisplayFocusEnabled. If it is
        // enabled then all displays have their own focus. The primary activity should have focus.
        // If it is disabled then it should have lost the focus when the secondary activity launched
        // on the second monitor. That brought that display to the top and removed window focus from
        // the default display (where primary activity is running).
        primaryActivity.waitAndAssertWindowFocusState(perDisplayFocusEnabled());

        // Both activities running on displays with their own focus should have window focus.
        secondaryActivity.waitAndAssertWindowFocusState(true);
        tertiaryActivity.waitAndAssertWindowFocusState(true);

        // Making the primary activity the top focus (by tapping it) will make
        // it focused. The other two displays still have a focused window
        tapOn(primaryActivity);
        primaryActivity.waitAndAssertWindowFocusState(true);
        secondaryActivity.waitAndAssertWindowFocusState(true);
        tertiaryActivity.waitAndAssertWindowFocusState(true);
    }

    /**
     * Test if a display targeted by a key event can be moved to top in a single-focus system.
     */
    @Test
    public void testMovingDisplayToTopByKeyEvent() {
        assumeTrue(supportsMultiDisplay());

        final PrimaryActivity primaryActivity = startActivity(PrimaryActivity.class,
                DEFAULT_DISPLAY);
        final InvisibleVirtualDisplaySession session = createManagedInvisibleDisplaySession();
        final int secondaryDisplayId = session.getDisplayId();
        final SecondaryActivity secondaryActivity = session.startActivityAndFocus();

        sendAndAssertTargetConsumedKey(primaryActivity, KEYCODE_0, DEFAULT_DISPLAY);
        sendAndAssertTargetConsumedKey(primaryActivity, KEYCODE_1, INVALID_DISPLAY);

        sendAndAssertTargetConsumedKey(secondaryActivity, KEYCODE_2, secondaryDisplayId);
        sendAndAssertTargetConsumedKey(secondaryActivity, KEYCODE_3, INVALID_DISPLAY);
    }

    /**
     * The display flag FLAG_STEAL_TOP_FOCUS_DISABLED prevents a display from stealing the top
     * focus from another display. Sending targeted key events to a display usually raises that
     * display to be the top focused display if it is not yet. If the FLAG_STEAL_TOP_FOCUS_DISABLED
     * is set then that should not happen and the previous display stays the top focused display.
     */
    @Test
    public void testStealingTopFocusDisabledDoesNotMoveDisplayToTop() {
        assumeTrue(supportsMultiDisplay());

        final PrimaryActivity primaryActivity = startActivity(PrimaryActivity.class,
                DEFAULT_DISPLAY);
        // Primary should have window focus for sure after launching
        primaryActivity.waitAndAssertWindowFocusState(/* hasFocus */ true);
        // Confirm this display has the top focus and receives untargeted events
        sendAndAssertTargetConsumedKey(primaryActivity, KEYCODE_0, INVALID_DISPLAY);
        // Confirm this display has the top focus and receives targeted events
        sendAndAssertTargetConsumedKey(primaryActivity, KEYCODE_1, DEFAULT_DISPLAY);

        // Create a VirtualDisplay with top focus disabled and launch an activity on it
        final VirtualDisplayWithOwnFocusSession session =
                createManagedVirtualDisplayWithOwnFocusSession(
                        VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED);
        final int secondaryDisplayId = session.getDisplayId();
        // Launching the activity on the secondary display will give it window focus.
        final SecondaryActivity secondaryActivity = session.startActivityAndFocus(
                SecondaryActivity.class);

        // Primary should have window focus because it still is top focused display
        // Secondary should have window focus because it manages its own focus
        primaryActivity.waitAndAssertWindowFocusState(/* hasFocus */ true);
        secondaryActivity.waitAndAssertWindowFocusState(/* hasFocus */ true);

        // Confirm the default display still has top display focus
        sendAndAssertTargetConsumedKey(primaryActivity, KEYCODE_2, INVALID_DISPLAY);

        // Send a targeted key event to the secondary display.
        // The secondary display should not get top focus because of FLAG_STEAL_TOP_FOCUS_DISABLED
        sendAndAssertTargetConsumedKey(secondaryActivity, KEYCODE_3, secondaryDisplayId);
        sendAndAssertTargetConsumedKey(primaryActivity, KEYCODE_4, INVALID_DISPLAY);

        // Now also check a tap does also not raise the top focus to the secondary display
        tapOn(secondaryActivity);
        sendAndAssertTargetConsumedKey(primaryActivity, KEYCODE_5, INVALID_DISPLAY);

        // Tap the default display and check that the secondary display still has a window focus
        tapOn(primaryActivity);
        secondaryActivity.waitAndAssertWindowFocusState(/*hasFocus*/ true);
        sendAndAssertTargetConsumedKey(primaryActivity, KEYCODE_6, INVALID_DISPLAY);
        sendAndAssertTargetConsumedKey(secondaryActivity, KEYCODE_7, secondaryDisplayId);
    }

    /**
     * Test if the client is notified about window-focus lost after the new focused window is drawn.
     */
    @Test
    public void testDelayLosingFocus() {
        final LosingFocusActivity activity = startActivity(LosingFocusActivity.class,
                DEFAULT_DISPLAY);

        getInstrumentation().runOnMainSync(activity::addChildWindow);
        activity.waitAndAssertWindowFocusState(false /* hasFocus */);
        assertFalse("Activity must lose window focus after new focused window is drawn.",
                activity.losesFocusWhenNewFocusIsNotDrawn());
    }


    /**
     * Test the following conditions:
     * - Only the top focused window can have pointer capture.
     * - The window which lost top-focus can be notified about pointer-capture lost.
     */
    @Test
    public void testPointerCapture() {
        final PrimaryActivity primaryActivity = startActivity(PrimaryActivity.class,
                DEFAULT_DISPLAY);

        // Assert primary activity can have pointer capture before we have multiple focused windows.
        getInstrumentation().runOnMainSync(primaryActivity::requestPointerCapture);
        primaryActivity.waitAndAssertPointerCaptureState(true /* hasCapture */);

        assumeTrue(supportsMultiDisplay());
        final SecondaryActivity secondaryActivity =
                createManagedInvisibleDisplaySession().startActivityAndFocus();

        // Assert primary activity lost pointer capture when it is not top focused.
        primaryActivity.waitAndAssertPointerCaptureState(false /* hasCapture */);

        // Assert secondary activity can have pointer capture when it is top focused.
        getInstrumentation().runOnMainSync(secondaryActivity::requestPointerCapture);
        secondaryActivity.waitAndAssertPointerCaptureState(true /* hasCapture */);

        tapOn(primaryActivity);
        primaryActivity.waitAndAssertWindowFocusState(true);

        // Assert secondary activity lost pointer capture when it is not top focused.
        secondaryActivity.waitAndAssertPointerCaptureState(false /* hasCapture */);
    }

    /**
     * Pointer capture could be requested after activity regains focus.
     */
    @Test
    public void testPointerCaptureWhenFocus() throws Throwable {
        final AutoEngagePointerCaptureActivity primaryActivity =
                startActivity(AutoEngagePointerCaptureActivity.class, DEFAULT_DISPLAY);
        assertTrue("Failed to reach stable window geometry",
                waitForStableWindowGeometry(5, TimeUnit.SECONDS));

        // Assert primary activity can have pointer capture before we have multiple focused windows.
        primaryActivity.waitAndAssertPointerCaptureState(true /* hasCapture */);

        assumeTrue(supportsMultiDisplay());

        // This test only makes sense if `config_perDisplayFocusEnabled` is disabled.
        assumeFalse(perDisplayFocusEnabled());

        final SecondaryActivity secondaryActivity =
                createManagedInvisibleDisplaySession().startActivityAndFocus();

        primaryActivity.waitAndAssertWindowFocusState(false /* hasFocus */);
        // Assert primary activity lost pointer capture when it is not top focused.
        primaryActivity.waitAndAssertPointerCaptureState(false /* hasCapture */);
        secondaryActivity.waitAndAssertPointerCaptureState(false /* hasCapture */);

        tapOn(primaryActivity);
        primaryActivity.waitAndAssertWindowFocusState(true /* hasFocus */);
        primaryActivity.waitAndAssertPointerCaptureState(true /* hasCapture */);
    }

    /**
     * Test if the focused window can still have focus after it is moved to another display.
     */
    @Test
    public void testDisplayChanged() {
        assumeTrue(supportsMultiDisplay());

        final PrimaryActivity primaryActivity = startActivity(PrimaryActivity.class,
                DEFAULT_DISPLAY);

        final InvisibleVirtualDisplaySession session = createManagedInvisibleDisplaySession();
        final SecondaryActivity secondaryActivity = session.startActivityAndFocus();
        // Secondary display disconnected.
        session.close();

        assertNotNull("SecondaryActivity must be started.", secondaryActivity);
        secondaryActivity.waitAndAssertDisplayId(DEFAULT_DISPLAY);
        secondaryActivity.waitAndAssertWindowFocusState(true /* hasFocus */);

        primaryActivity.waitAndAssertWindowFocusState(false /* hasFocus */);
    }

    /**
     * Ensure that a non focused display becomes focused when tapping on a focusable window on
     * that display.
     */
    @Test
    public void testTapFocusableWindow() {
        assumeTrue(supportsMultiDisplay());
        assumeFalse(perDisplayFocusEnabled());

        PrimaryActivity primaryActivity = startActivity(PrimaryActivity.class, DEFAULT_DISPLAY);
        final SecondaryActivity secondaryActivity =
                createManagedInvisibleDisplaySession().startActivityAndFocus();

        tapOn(primaryActivity);
        // Ensure primary activity got focus
        primaryActivity.waitAndAssertWindowFocusState(true);
        secondaryActivity.waitAndAssertWindowFocusState(false);
    }

    /**
     * Ensure that a non focused display does not become focused when tapping on a non-focusable
     * window on that display.
     */
    @Test
    public void testTapNonFocusableWindow() throws Throwable {
        assumeTrue(supportsMultiDisplay());
        assumeFalse(perDisplayFocusEnabled());

        PrimaryActivity primaryActivity = startActivity(PrimaryActivity.class, DEFAULT_DISPLAY);
        final SecondaryActivity secondaryActivity =
                createManagedInvisibleDisplaySession().startActivityAndFocus();

        // Tap on a window that can't be focused and ensure that the other window in that
        // display, primaryActivity's window, doesn't get focus.
        getInstrumentation().runOnMainSync(() -> {
            View view = new View(primaryActivity);
            LayoutParams p = new LayoutParams();
            p.flags = LayoutParams.FLAG_NOT_FOCUSABLE;
            primaryActivity.getWindowManager().addView(view, p);
        });
        assertTrue("Failed to reach stable window geometry",
                waitForStableWindowGeometry(5, TimeUnit.SECONDS));

        tapOn(primaryActivity);
        // Ensure secondary activity still has focus
        secondaryActivity.waitAndAssertWindowFocusState(true);
        primaryActivity.waitAndAssertWindowFocusState(false);
    }

    private static class InputTargetActivity extends FocusableActivity {
        private static final long TIMEOUT_DISPLAY_CHANGED = 5000; // milliseconds
        private static final long TIMEOUT_POINTER_CAPTURE_CHANGED = 3000
                * BuildUtils.HW_TIMEOUT_MULTIPLIER;
        private static final long TIMEOUT_NEXT_KEY_EVENT = 1000;
        private final Object mLockPointerCapture = new Object();
        private final Object mLockKeyEvent = new Object();

        @GuardedBy("this")
        private int mDisplayId = INVALID_DISPLAY;
        @GuardedBy("mLockPointerCapture")
        private boolean mHasPointerCapture;
        @GuardedBy("mLockKeyEvent")
        private ArrayList<KeyEvent> mKeyEventList = new ArrayList<>();

        @Override
        public void onAttachedToWindow() {
            synchronized (this) {
                mDisplayId = getWindow().getDecorView().getDisplay().getDisplayId();
                notify();
            }
        }

        @Override
        public void onMovedToDisplay(int displayId, Configuration config) {
            synchronized (this) {
                mDisplayId = displayId;
                notify();
            }
        }

        void waitAndAssertDisplayId(int displayId) {
            synchronized (this) {
                if (mDisplayId != displayId) {
                    try {
                        wait(TIMEOUT_DISPLAY_CHANGED);
                    } catch (InterruptedException e) {
                    }
                }
                assertEquals(getLogTag() + " must be moved to the display.",
                        displayId, mDisplayId);
            }
        }

        @Override
        public void onPointerCaptureChanged(boolean hasCapture) {
            synchronized (mLockPointerCapture) {
                mHasPointerCapture = hasCapture;
                mLockPointerCapture.notify();
            }
        }

        void waitAndAssertPointerCaptureState(boolean hasCapture) {
            synchronized (mLockPointerCapture) {
                if (mHasPointerCapture != hasCapture) {
                    try {
                        mLockPointerCapture.wait(TIMEOUT_POINTER_CAPTURE_CHANGED);
                    } catch (InterruptedException e) {
                    }
                }
                assertEquals(getLogTag() + " must" + (hasCapture ? "" : " not")
                        + " have pointer capture.", hasCapture, mHasPointerCapture);
            }
        }

        // Should be only called from the main thread.
        void requestPointerCapture() {
            getWindow().getDecorView().requestPointerCapture();
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            synchronized (mLockKeyEvent) {
                mKeyEventList.add(event);
                mLockKeyEvent.notify();
            }
            return true;
        }

        int getKeyEventCount() {
            synchronized (mLockKeyEvent) {
                return mKeyEventList.size();
            }
        }

        private KeyEvent consumeKeyEvent(int action, int keyCode, int flags) {
            synchronized (mLockKeyEvent) {
                for (int i = mKeyEventList.size() - 1; i >= 0; i--) {
                    final KeyEvent event = mKeyEventList.get(i);
                    if (event.getAction() == action && event.getKeyCode() == keyCode
                            && (event.getFlags() & flags) == flags) {
                        mKeyEventList.remove(event);
                        return event;
                    }
                }
            }
            return null;
        }

        void assertAndConsumeKeyEvent(int action, int keyCode, int flags) {
            assertNotNull(getLogTag() + " must receive key event " + keyCodeToString(keyCode),
                    consumeKeyEvent(action, keyCode, flags));
        }

        void waitAssertAndConsumeKeyEvent(int action, int keyCode, int flags) {
            if (consumeKeyEvent(action, keyCode, flags) == null) {
                synchronized (mLockKeyEvent) {
                    try {
                        mLockKeyEvent.wait(TIMEOUT_NEXT_KEY_EVENT);
                    } catch (InterruptedException e) {
                    }
                }
                assertAndConsumeKeyEvent(action, keyCode, flags);
            }
        }
    }

    public static class PrimaryActivity extends InputTargetActivity { }

    public static class SecondaryActivity extends InputTargetActivity { }

    public static class TertiaryActivity extends InputTargetActivity { }

    public static class LosingFocusActivity extends InputTargetActivity {
        private boolean mChildWindowHasDrawn = false;

        @GuardedBy("this")
        private boolean mLosesFocusWhenNewFocusIsNotDrawn = false;

        void addChildWindow() {
            getWindowManager().addView(new View(this) {
                @Override
                protected void onDraw(Canvas canvas) {
                    mChildWindowHasDrawn = true;
                }
            }, new LayoutParams());
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            if (!hasFocus && !mChildWindowHasDrawn) {
                synchronized (this) {
                    mLosesFocusWhenNewFocusIsNotDrawn = true;
                }
            }
            super.onWindowFocusChanged(hasFocus);
        }

        boolean losesFocusWhenNewFocusIsNotDrawn() {
            synchronized (this) {
                return mLosesFocusWhenNewFocusIsNotDrawn;
            }
        }
    }

    public static class AutoEngagePointerCaptureActivity extends InputTargetActivity {
        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            if (hasFocus) {
                requestPointerCapture();
            }
            super.onWindowFocusChanged(hasFocus);
        }
    }

    private InvisibleVirtualDisplaySession createManagedInvisibleDisplaySession() {
        return mObjectTracker.manage(
                new InvisibleVirtualDisplaySession(getInstrumentation().getTargetContext()));
    }

    /** An untrusted virtual display that won't show on default screen. */
    private static class InvisibleVirtualDisplaySession implements AutoCloseable {
        private static final int WIDTH = 800;
        private static final int HEIGHT = 480;
        private static final int DENSITY = 160;

        private final VirtualDisplay mVirtualDisplay;
        private final ImageReader mReader;
        private final Display mDisplay;

        InvisibleVirtualDisplaySession(Context context) {
            mReader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888,
                    2 /* maxImages */);
            mVirtualDisplay = context.getSystemService(DisplayManager.class)
                    .createVirtualDisplay(WindowFocusTests.class.getSimpleName(),
                            WIDTH, HEIGHT, DENSITY, mReader.getSurface(),
                            VIRTUAL_DISPLAY_FLAG_PUBLIC | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
            mDisplay = mVirtualDisplay.getDisplay();
        }

        int getDisplayId() {
            return mDisplay.getDisplayId();
        }

        SecondaryActivity startActivityAndFocus() {
            return WindowFocusTests.startActivityAndFocus(getDisplayId(), false /* hasFocus */,
                    SecondaryActivity.class);
        }

        @Override
        public void close() {
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
            }
            if (mReader != null) {
                mReader.close();
            }
        }
    }

    private VirtualDisplayWithOwnFocusSession createManagedVirtualDisplayWithOwnFocusSession() {
        return createManagedVirtualDisplayWithOwnFocusSession(/* additionalFlags= */ 0);
    }

    private VirtualDisplayWithOwnFocusSession createManagedVirtualDisplayWithOwnFocusSession(
            int additionalFlags) {
        return mObjectTracker.manage(
                new VirtualDisplayWithOwnFocusSession(getInstrumentation().getTargetContext(),
                        additionalFlags));
    }

    /** A trusted virtual display that has its own focus and touch mode states. */
    private static class VirtualDisplayWithOwnFocusSession implements AutoCloseable {
        private static final int WIDTH = 800;
        private static final int HEIGHT = 480;
        private static final int DENSITY = 160;

        private VirtualDisplay mVirtualDisplay;
        private final ImageReader mReader;
        private final Display mDisplay;

        /**
         * @param context         The context, used to get the DisplayManager.
         * @param additionalFlags Additional VirtualDisplayFlag to add. See
         *                        {@link #getVirtualDisplayFlags()} for the default flags that are
         *                        set.
         */
        VirtualDisplayWithOwnFocusSession(Context context, int additionalFlags) {
            mReader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888,
                    /* maxImages= */ 2);
            SystemUtil.runWithShellPermissionIdentity(() -> {
                mVirtualDisplay = context.getSystemService(DisplayManager.class)
                        .createVirtualDisplay(WindowFocusTests.class.getSimpleName(), WIDTH, HEIGHT,
                                DENSITY, mReader.getSurface(),
                                getVirtualDisplayFlags() | additionalFlags);
            });
            mDisplay = mVirtualDisplay.getDisplay();
        }

        /**
         * @return Get the default VirtualDisplayFlags to set for the creation of the VirtualDisplay
         */
        int getVirtualDisplayFlags() {
            return VIRTUAL_DISPLAY_FLAG_PUBLIC
                    | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                    | VIRTUAL_DISPLAY_FLAG_TRUSTED
                    | VIRTUAL_DISPLAY_FLAG_OWN_FOCUS;
        }

        int getDisplayId() {
            return mDisplay.getDisplayId();
        }

        <T extends InputTargetActivity> T startActivityAndFocus(Class<T> cls) {
            return WindowFocusTests.startActivityAndFocus(getDisplayId(), /* hasFocus= */ true,
                    cls);
        }

        @Override
        public void close() {
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
            }
            if (mReader != null) {
                mReader.close();
            }
        }
    }

    private SimulatedDisplaySession createManagedSimulatedDisplaySession() {
        return mObjectTracker.manage(new SimulatedDisplaySession());
    }

    private class SimulatedDisplaySession implements AutoCloseable {
        private final VirtualDisplaySession mVirtualDisplaySession;
        private final WindowManagerState.DisplayContent mVirtualDisplay;

        SimulatedDisplaySession() {
            mVirtualDisplaySession = new VirtualDisplaySession();
            mVirtualDisplay = mVirtualDisplaySession.setSimulateDisplay(true).createDisplay();
        }

        int getDisplayId() {
            return mVirtualDisplay.mId;
        }

        SecondaryActivity startActivityAndFocus() {
            return WindowFocusTests.startActivityAndFocus(getDisplayId(), true /* hasFocus */,
                    SecondaryActivity.class);
        }

        @Override
        public void close() {
            mVirtualDisplaySession.close();
        }
    }

    private static <T extends InputTargetActivity> T startActivityAndFocus(int displayId,
            boolean hasFocus, Class<T> cls) {
        // An untrusted virtual display won't have focus until the display is touched.
        final T activity = WindowManagerTestBase.startActivity(
                cls, displayId, hasFocus);
        tapOn(activity);
        activity.waitAndAssertWindowFocusState(true);
        return activity;
    }
}
