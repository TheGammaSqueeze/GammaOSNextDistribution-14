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

package android.server.wm.backnavigation;

import static android.view.Display.DEFAULT_DISPLAY;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.TouchHelper;
import android.view.KeyEvent;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.CddTest;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Integration test for back navigation
 */
public class OnBackInvokedCallbackGestureTest extends ActivityManagerTestBase {
    private static final int PROGRESS_SWIPE_STEPS = 10;

    private Instrumentation mInstrumentation;
    private UiDevice mUiDevice;
    private BackInvocationTracker mTracker = new BackInvocationTracker();
    private BackNavigationActivity mActivity;

    private final OnBackAnimationCallback mAnimationCallback = new OnBackAnimationCallback() {
        @Override
        public void onBackStarted(BackEvent e) {
            mTracker.trackBackStarted();
        }

        @Override
        public void onBackInvoked() {
            mTracker.trackBackInvoked();
        }

        @Override
        public void onBackCancelled() {
            mTracker.trackBackCancelled();
        }

        @Override
        public void onBackProgressed(BackEvent e) {
            mTracker.trackBackProgressed(e);
        }
    };

    @Before
    public void setup() throws Exception {
        super.setUp();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mUiDevice = UiDevice.getInstance(mInstrumentation);
        enableAndAssumeGestureNavigationMode();

        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();
        mTracker.reset();

        final TestActivitySession<BackNavigationActivity> activitySession =
                createManagedTestActivitySession();
        activitySession.launchTestActivityOnDisplaySync(
                BackNavigationActivity.class, DEFAULT_DISPLAY);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        mInstrumentation.getUiAutomation().syncInputTransactions();

        mActivity = activitySession.getActivity();
        registerBackCallback(mActivity);
    }

    @Test
    @CddTest(requirements = { "7.2.3/H-0-5,H-0-6,H-0-8" })
    public void invokesCallback_invoked() throws InterruptedException {
        int midHeight = mUiDevice.getDisplayHeight() / 2;
        int midWidth = mUiDevice.getDisplayWidth() / 2;

        final TouchHelper.SwipeSession touchSession = new TouchHelper.SwipeSession(
                DEFAULT_DISPLAY, true, false);
        touchSession.beginSwipe(0, midHeight);
        // Back start event shouldn't be sent until the edge swipe threshold is crossed.
        assertNotInvoked(mTracker.mStartLatch);

        touchSession.continueSwipe(midWidth, midHeight, PROGRESS_SWIPE_STEPS);
        assertInvoked(mTracker.mStartLatch);
        assertInvoked(mTracker.mProgressLatch);
        assertNotInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);
        List<BackEvent> events = mTracker.mProgressEvents;
        assertTrue(events.size() > 0);
        for (int i = 0; i < events.size() - 1; i++) {
            // Check that progress events report increasing progress values.
            // TODO(b/258817762): Verify more once the progress clamping behavior is implemented.
            BackEvent event = events.get(i);
            assertTrue(event.getProgress() <= events.get(i + 1).getProgress());
            assertTrue(event.getTouchX() <= events.get(i + 1).getTouchX());
            assertEquals(midHeight, midHeight, event.getTouchY());
            assertEquals(BackEvent.EDGE_LEFT, event.getSwipeEdge());
        }

        touchSession.finishSwipe();
        assertInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);
    }

    @Test
    @CddTest(requirements = { "7.2.3/H-0-7" })
    public void invokesCallback_cancelled() throws InterruptedException {
        int midHeight = mUiDevice.getDisplayHeight() / 2;
        int midWidth = mUiDevice.getDisplayWidth() / 2;

        final TouchHelper.SwipeSession touchSession = new TouchHelper.SwipeSession(
                DEFAULT_DISPLAY, true, false);
        long startDownTime = touchSession.beginSwipe(0, midHeight);
        touchSession.continueSwipe(midWidth, midHeight, PROGRESS_SWIPE_STEPS);
        assertInvoked(mTracker.mProgressLatch);

        mTracker.reset();
        mTracker.mIsCancelRequested = true;
        touchSession.cancelSwipe();

        assertInvoked(mTracker.mCancelLatch);
        assertNotInvoked(mTracker.mInvokeLatch);
        assertInvoked(mTracker.mCancelProgressLatch);
        List<BackEvent> events = mTracker.mProgressEvents;
        assertTrue(events.size() > 0);
        assertTrue(events.get(events.size() - 1).getProgress() == 0);
    }

    @Test
    @CddTest(requirements = { "7.2.3/H-0-5,H-0-6" })
    public void invokesCallbackInButtonsNav_invoked() throws InterruptedException {
        long downTime = TouchHelper.injectKeyActionDown(KeyEvent.KEYCODE_BACK,
                /* longpress = */ false,
                /* sync = */ true);

        assertInvoked(mTracker.mStartLatch);
        assertNotInvoked(mTracker.mProgressLatch);
        assertNotInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);

        TouchHelper.injectKeyActionUp(KeyEvent.KEYCODE_BACK,
                /* downTime = */ downTime,
                /* cancelled = */ false,
                /* sync = */ true);

        assertInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mProgressLatch);
        assertNotInvoked(mTracker.mCancelLatch);
    }

    @Test
    @CddTest(requirements = { "7.2.3/H-0-7" })
    public void invokesCallbackInButtonsNav_cancelled() throws InterruptedException {
        long downTime = TouchHelper.injectKeyActionDown(KeyEvent.KEYCODE_BACK,
                /* longpress = */ false,
                /* sync = */ true);

        TouchHelper.injectKeyActionUp(KeyEvent.KEYCODE_BACK,
                /* downTime = */ downTime,
                /* cancelled = */ true,
                /* sync = */ true);

        assertInvoked(mTracker.mCancelLatch);
        assertNotInvoked(mTracker.mProgressLatch);
        assertNotInvoked(mTracker.mInvokeLatch);
    }

    @Test
    public void ignoresKeyCodeBackDuringDispatch() {
        int midHeight = mUiDevice.getDisplayHeight() / 2;
        int midWidth = mUiDevice.getDisplayWidth() / 2;

        final TouchHelper.SwipeSession touchSession = new TouchHelper.SwipeSession(
                DEFAULT_DISPLAY, true, false);
        touchSession.beginSwipe(0, midHeight);
        touchSession.continueSwipe(midWidth, midHeight, PROGRESS_SWIPE_STEPS);
        waitForIdle();
        mTracker.reset();
        TouchHelper.injectKey(KeyEvent.KEYCODE_BACK, false /* longpress */, true /* sync */);
        waitForIdle();
        // Make sure the KEYCODE_BACKs don't invoke callbacks.
        assertNotInvoked(mTracker.mStartLatch);
        assertNotInvoked(mTracker.mProgressLatch);
        assertNotInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);
        touchSession.finishSwipe();
    }

    @Test
    public void constructsEvent() {
        final float x = 200;
        final float y = 300;
        final float progress = 0.5f;
        final int swipeEdge = BackEvent.EDGE_RIGHT;
        BackEvent event = new BackEvent(x, y, progress, swipeEdge);
        assertEquals(x, event.getTouchX());
        assertEquals(y, event.getTouchY());
        assertEquals(progress, event.getProgress());
        assertEquals(swipeEdge, event.getSwipeEdge());
    }

    private void assertInvoked(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
    }

    private void assertNotInvoked(CountDownLatch latch) {
        assertTrue(latch.getCount() >= 1);
    }

    private void registerBackCallback(BackNavigationActivity activity) {
        CountDownLatch backRegisteredLatch = new CountDownLatch(1);
        activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                0, mAnimationCallback);
        backRegisteredLatch.countDown();
        try {
            if (!backRegisteredLatch.await(100, TimeUnit.MILLISECONDS)) {
                fail("Back callback was not registered on the Activity thread. This might be "
                        + "an error with the test itself.");
            }
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }

    /** Helper class to track {@link android.window.OnBackAnimationCallback} invocations. */
    static class BackInvocationTracker {
        private CountDownLatch mStartLatch;
        private CountDownLatch mInvokeLatch;
        private CountDownLatch mProgressLatch;
        private CountDownLatch mCancelLatch;
        private CountDownLatch mCancelProgressLatch;
        private boolean mIsCancelRequested = false;
        private final ArrayList<BackEvent> mProgressEvents = new ArrayList<>();

        private void reset() {
            mStartLatch = new CountDownLatch(1);
            mInvokeLatch = new CountDownLatch(1);
            mProgressLatch = new CountDownLatch(PROGRESS_SWIPE_STEPS);
            mCancelLatch = new CountDownLatch(1);
            mCancelProgressLatch = new CountDownLatch(1);
            mIsCancelRequested = false;
            mProgressEvents.clear();
        }

        private void trackBackStarted() {
            mStartLatch.countDown();
        }

        private void trackBackProgressed(BackEvent e) {
            mProgressEvents.add(e);
            if (mIsCancelRequested && 0 == e.getProgress()) {
                // Ensure the progress could reach to 0 for cancel animation.
                mCancelProgressLatch.countDown();
            } else {
                mProgressLatch.countDown();
            }
        }

        private void trackBackCancelled() {
            mCancelLatch.countDown();
        }

        private void trackBackInvoked() {
            mInvokeLatch.countDown();
        }
    }
}
