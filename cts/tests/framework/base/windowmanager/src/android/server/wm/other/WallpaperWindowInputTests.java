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

package android.server.wm.other;

import static android.content.pm.PackageManager.FEATURE_LIVE_WALLPAPER;
import static android.server.wm.CliIntentExtra.extraBool;
import static android.server.wm.app.Components.TestInteractiveLiveWallpaperKeys.COMPONENT;
import static android.server.wm.app.Components.TestInteractiveLiveWallpaperKeys.LAST_RECEIVED_MOTION_EVENT;
import static android.server.wm.app.Components.WALLPAPER_TARGET_ACTIVITY;
import static android.server.wm.app.Components.WallpaperTargetActivity.EXTRA_ENABLE_WALLPAPER_TOUCH;
import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.WallpaperManager;
import android.graphics.Rect;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.TestJournalProvider;
import android.server.wm.WindowManagerState;
import android.server.wm.app.Components;
import android.view.InputDevice;
import android.view.MotionEvent;

import com.android.compatibility.common.util.PollingCheck;

import junit.framework.AssertionFailedError;

import org.junit.Before;
import org.junit.Test;


/**
 * Ensure moving windows and tapping is done synchronously.
 *
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceOther:WallpaperWindowInputTests
 */
@Presubmit
@android.server.wm.annotation.Group2
public class WallpaperWindowInputTests extends ActivityManagerTestBase {
    private static final String TAG = "WallpaperWindowInputTests";

    private MotionEvent mLastMotionEvent;

    @Before
    public void setup() {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
        assumeTrue("Device does not support wallpapers",
                wallpaperManager.isWallpaperSupported());
        assumeTrue("Device does not support live wallpapers",
                mContext.getPackageManager().hasSystemFeature(FEATURE_LIVE_WALLPAPER));
    }

    @Test
    public void testShowWallpaper_withTouchEnabled() {
        final ChangeWallpaperSession wallpaperSession = createManagedChangeWallpaperSession();
        wallpaperSession.setWallpaperComponent(Components.TEST_INTERACTIVE_LIVE_WALLPAPER_SERVICE);

        launchActivity(WALLPAPER_TARGET_ACTIVITY, extraBool(EXTRA_ENABLE_WALLPAPER_TOUCH, true));
        mWmState.waitAndAssertWindowShown(TYPE_WALLPAPER, true);
        TestJournalProvider.TestJournalContainer.start();
        final WindowManagerState.Task task = mWmState.getTaskByActivity(WALLPAPER_TARGET_ACTIVITY);
        MotionEvent motionEvent = getDownEventForTaskCenter(task);
        mInstrumentation.getUiAutomation().injectInputEvent(motionEvent, true, true);

        PollingCheck.waitFor(2000 /* timeout */, this::updateLastMotionEventFromTestJournal,
                "Waiting for wallpaper to receive the touch events");

        assertNotNull(mLastMotionEvent);
        assertMotionEvent(mLastMotionEvent, motionEvent);
    }

    @Test
    public void testShowWallpaper_withWallpaperTouchDisabled() {
        final ChangeWallpaperSession wallpaperSession = createManagedChangeWallpaperSession();
        wallpaperSession.setWallpaperComponent(Components.TEST_INTERACTIVE_LIVE_WALLPAPER_SERVICE);

        launchActivity(WALLPAPER_TARGET_ACTIVITY, extraBool(EXTRA_ENABLE_WALLPAPER_TOUCH, false));
        mWmState.waitAndAssertWindowShown(TYPE_WALLPAPER, true);

        TestJournalProvider.TestJournalContainer.start();
        final WindowManagerState.Task task = mWmState.getTaskByActivity(WALLPAPER_TARGET_ACTIVITY);
        MotionEvent motionEvent = getDownEventForTaskCenter(task);
        mInstrumentation.getUiAutomation().injectInputEvent(motionEvent, true, true);

        final String failMsg = "Waiting for wallpaper to receive the touch events";
        Throwable exception = assertThrows(AssertionFailedError.class,
                () -> PollingCheck.waitFor(2000 /* timeout */,
                        this::updateLastMotionEventFromTestJournal,
                        "Waiting for wallpaper to receive the touch events"));
        assertEquals(failMsg, exception.getMessage());
    }

    private boolean updateLastMotionEventFromTestJournal() {
        TestJournalProvider.TestJournal journal =
                TestJournalProvider.TestJournalContainer.get(COMPONENT);
        TestJournalProvider.TestJournalContainer.withThreadSafeAccess(() -> {
            mLastMotionEvent = journal.extras.containsKey(LAST_RECEIVED_MOTION_EVENT)
                    ? journal.extras.getParcelable(LAST_RECEIVED_MOTION_EVENT,
                    MotionEvent.class) : null;
        });
        return mLastMotionEvent != null;
    }

    private static MotionEvent getDownEventForTaskCenter(WindowManagerState.Task task) {
        // Get anchor coordinates on the screen
        final Rect bounds = task.getBounds();
        int xOnScreen = bounds.width() / 2;
        int yOnScreen = bounds.height() / 2;
        final long downTime = SystemClock.uptimeMillis();

        MotionEvent eventDown = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, xOnScreen, yOnScreen, 1);
        eventDown.setSource(InputDevice.SOURCE_TOUCHSCREEN);

        return eventDown;
    }

    private static void assertMotionEvent(MotionEvent event, MotionEvent expectedEvent) {
        assertEquals(TAG + "(action)", event.getAction(), expectedEvent.getAction());
        assertEquals(TAG + "(source)", event.getSource(), expectedEvent.getSource());
        assertEquals(TAG + "(down time)", event.getDownTime(), expectedEvent.getDownTime());
        assertEquals(TAG + "(event time)", event.getEventTime(), expectedEvent.getEventTime());
        assertEquals(TAG + "(position x)", event.getX(), expectedEvent.getX(), 0.01F);
        assertEquals(TAG + "(position y)", event.getY(), expectedEvent.getY(), 0.01F);
    }
}
