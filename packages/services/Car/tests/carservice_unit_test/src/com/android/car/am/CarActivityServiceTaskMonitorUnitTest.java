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
package com.android.car.am;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.wm.shell.ShellTaskOrganizer.TASK_LISTENER_TYPE_FULLSCREEN;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Instrumentation.ActivityMonitor;
import android.app.TaskInfo;
import android.car.test.util.DisplayUtils.VirtualDisplaySession;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceControl;

import androidx.test.filters.MediumTest;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.HandlerExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.fullscreen.FullscreenTaskListener;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

@RunWith(MockitoJUnitRunner.class)
@MediumTest
public class CarActivityServiceTaskMonitorUnitTest {
    private static final String TAG = CarActivityServiceTaskMonitorUnitTest.class.getSimpleName();

    private static final long ACTIVITY_TIMEOUT_MS = 5000;
    private static final long NO_ACTIVITY_TIMEOUT_MS = 1000;
    private static final long DEFAULT_TIMEOUT_MS = 10_000;
    private static final int SLEEP_MS = 50;
    private static final long SHORT_MIRRORING_TOKEN_TIMEOUT_MS = 100;

    private static CopyOnWriteArrayList<Activity> sTestActivities = new CopyOnWriteArrayList<>();

    private CarActivityService mService;
    @Mock
    private IBinder mToken;
    @Captor
    ArgumentCaptor<IBinder.DeathRecipient> mDeathRecipientCaptor;

    private ShellTaskOrganizer mTaskOrganizer;
    private FullscreenTaskListener mFullscreenTaskListener;

    private final ComponentName mActivityA = new ComponentName(getTestContext(), ActivityA.class);
    private final ComponentName mActivityB = new ComponentName(getTestContext(), ActivityB.class);
    private final ComponentName mActivityC = new ComponentName(getTestContext(), ActivityC.class);
    private final ComponentName mBlockingActivity = new ComponentName(
            getTestContext(), BlockingActivity.class);

    @Rule
    public final Expect expect = Expect.create();
    @Rule
    public TestName mTestName = new TestName();

    @Before
    public void setUp() throws Exception {
        long timeOutMs = DEFAULT_TIMEOUT_MS;
        if (mTestName.getMethodName().contains("ExpiredToken")) {
            timeOutMs = SHORT_MIRRORING_TOKEN_TIMEOUT_MS;
        }
        mService = new CarActivityService(getContext(), timeOutMs);
        mService.init();
        mService.registerTaskMonitor(mToken);
        setUpTaskOrganizer();
    }

    @After
    public void tearDown() {
        tearDownTaskOrganizer();
        for (Activity activity : sTestActivities) {
            activity.finish();
        }
        mService.unregisterTaskMonitor(mToken);
        // Any remaining ActivityLaunchListeners will be flushed in release().
        mService.release();
        mService = null;
    }

    private void setUpTaskOrganizer() throws Exception {
        Context context = getContext();
        HandlerExecutor mExecutor = new HandlerExecutor(context.getMainThreadHandler());
        mTaskOrganizer = new ShellTaskOrganizer(mExecutor);
        TransactionPool transactionPool = new TransactionPool();
        SyncTransactionQueue syncQueue = new SyncTransactionQueue(transactionPool, mExecutor);
        mFullscreenTaskListener = new TestTaskListener(syncQueue);
        mTaskOrganizer.addListenerForType(mFullscreenTaskListener, TASK_LISTENER_TYPE_FULLSCREEN);
        mTaskOrganizer.registerOrganizer();
    }

    private void tearDownTaskOrganizer() {
        mTaskOrganizer.removeListener(mFullscreenTaskListener);
        mTaskOrganizer.unregisterOrganizer();
    }

    private class TestTaskListener extends FullscreenTaskListener {
        TestTaskListener(SyncTransactionQueue syncQueue) {
            super(syncQueue);
        }

        @Override
        public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
            super.onTaskAppeared(taskInfo, leash);
            mService.onTaskAppeared(mToken, taskInfo, leash);
        }

        @Override
        public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
            super.onTaskInfoChanged(taskInfo);
            mService.onTaskInfoChanged(mToken, taskInfo);
        }

        @Override
        public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
            super.onTaskVanished(taskInfo);
            mService.onTaskVanished(mToken, taskInfo);
        }
    }

    @Test
    public void testActivityLaunch() throws Exception {
        startActivityAndAssertLaunched(mActivityA);

        startActivityAndAssertLaunched(mActivityB);
    }

    @Test
    public void testMultipleActivityLaunchListeners() throws Exception {
        FilteredLaunchListener listener1 = new FilteredLaunchListener(mActivityA);
        mService.registerActivityLaunchListener(listener1);
        FilteredLaunchListener listener2 = new FilteredLaunchListener(mActivityA);
        mService.registerActivityLaunchListener(listener2);

        startActivity(mActivityA, Display.DEFAULT_DISPLAY);

        listener2.assertTopTaskActivityLaunched();
        assertThat(listener1.mActivityLaunched.getCount()).isEqualTo(0);
    }

    @Test
    public void testUnregisterActivityLaunchListener() throws Exception {
        FilteredLaunchListener listener1 = new FilteredLaunchListener(mActivityA);
        mService.registerActivityLaunchListener(listener1);
        FilteredLaunchListener listener2 = new FilteredLaunchListener(mActivityA);
        mService.registerActivityLaunchListener(listener2);
        mService.unregisterActivityLaunchListener(listener1);

        startActivity(mActivityA, Display.DEFAULT_DISPLAY);

        listener2.assertTopTaskActivityLaunched();
        assertThat(listener1.mActivityLaunched.getCount()).isEqualTo(1);
    }

    @Test
    public void testDeathRecipientIsSet() throws Exception {
        FilteredLaunchListener listenerA = new FilteredLaunchListener(mActivityA);
        mService.registerActivityLaunchListener(listenerA);

        verify(mToken).linkToDeath(mDeathRecipientCaptor.capture(), anyInt());
    }

    @Test
    public void testBinderDied_cleansUpDeathRecipient() throws Exception {
        FilteredLaunchListener listenerA = new FilteredLaunchListener(mActivityA);
        mService.registerActivityLaunchListener(listenerA);

        verify(mToken).linkToDeath(mDeathRecipientCaptor.capture(), anyInt());
        mDeathRecipientCaptor.getValue().binderDied();

        // Checks if binderDied() will clean-up the death recipient.
        verify(mToken).unlinkToDeath(eq(mDeathRecipientCaptor.getValue()), anyInt());

        startActivity(mActivityA);
        // Starting a Activity shouldn't trigger the listener since the token is invalid.
        assertWithMessage("Shouldn't trigger the ActivityLaunched listener")
                .that(listenerA.waitForTopTaskActivityLaunched(NO_ACTIVITY_TIMEOUT_MS)).isFalse();
    }

    @Test
    public void testActivityBlocking() throws Exception {
        Intent blockingIntent = new Intent().setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        blockingIntent.setComponent(mBlockingActivity);

        // start a black listed activity
        FilteredLaunchListener listenerDenyListed = startActivityAndAssertLaunched(mActivityC);

        // Instead of start activity, invoke blockActivity.
        FilteredLaunchListener listenerBlocking = new FilteredLaunchListener(mBlockingActivity);
        mService.registerActivityLaunchListener(listenerBlocking);
        mService.blockActivity(listenerDenyListed.mTopTask, blockingIntent);
        listenerBlocking.assertTopTaskActivityLaunched();
    }

    @Test
    public void testRemovesFromTopTasks() throws Exception {
        FilteredLaunchListener listenerA = new FilteredLaunchListener(mActivityA);
        mService.registerActivityLaunchListener(listenerA);
        Activity launchedActivity = startActivity(mActivityA);
        listenerA.assertTopTaskActivityLaunched();
        assertTrue(topTasksHasComponent(mActivityA));

        getInstrumentation().runOnMainSync(launchedActivity::finish);
        waitUntil(() -> !topTasksHasComponent(mActivityA));
    }

    @Test
    public void testGetTopTasksOnMultiDisplay() throws Exception {
        // TaskOrganizer gets the callbacks only on the tasks launched in the actual Surface.
        try (VirtualDisplaySession session = new VirtualDisplaySession()) {
            int virtualDisplayId = session.createDisplayWithDefaultDisplayMetricsAndWait(
                    getTestContext(), /* isPrivate= */ false).getDisplayId();

            startActivityAndAssertLaunched(mActivityA);
            assertTrue(topTasksHasComponent(mActivityA));

            startActivityAndAssertLaunched(mActivityB, virtualDisplayId);
            assertTrue(topTasksHasComponent(mActivityB));
            assertTrue(topTasksHasComponent(mActivityA));

            startActivityAndAssertLaunched(mActivityC, virtualDisplayId);
            assertTrue(topTasksHasComponent(mActivityC));
            assertFalse(topTasksHasComponent(mActivityB));
            assertTrue(topTasksHasComponent(mActivityA));
        }
    }

    @Test
    public void testGetTopTasksOnDefaultDisplay() throws Exception {
        startActivityAndAssertLaunched(mActivityA);
        assertTrue(topTasksHasComponent(mActivityA));

        startActivityAndAssertLaunched(mActivityB);
        assertTrue(topTasksHasComponent(mActivityB));
        assertFalse(topTasksHasComponent(mActivityA));
    }

    @Test
    public void testGetTaskInfoForTopActivity() throws Exception {
        startActivityAndAssertLaunched(mActivityA);

        TaskInfo taskInfo = mService.getTaskInfoForTopActivity(mActivityA);
        assertNotNull(taskInfo);
        assertEquals(mActivityA, taskInfo.topActivity);
    }

    @Test
    public void testRestartTask() throws Exception {
        startActivityAndAssertLaunched(mActivityA);

        startActivityAndAssertLaunched(mActivityB);

        FilteredLaunchListener listenerRestartA = new FilteredLaunchListener(mActivityA);
        mService.registerActivityLaunchListener(listenerRestartA);

        // ActivityA and ActivityB are in the same package, so ActivityA becomes the root task of
        // ActivityB, so when we restarts ActivityB, it'll start ActivityA.
        TaskInfo taskInfo = mService.getTaskInfoForTopActivity(mActivityB);
        mService.restartTask(taskInfo.taskId);

        listenerRestartA.assertTopTaskActivityLaunched();
    }

    @Test
    public void testCreateMirroredToken_throwsExceptionForNonExistentTask() {
        int nonExistentTaskId = -999;
        assertThrows(IllegalArgumentException.class,
                () -> mService.createTaskMirroringToken(nonExistentTaskId));
    }

    @Test
    public void testCreateMirroredToken_returnsToken() throws Exception {
        FilteredLaunchListener listenerA = startActivityAndAssertLaunched(mActivityA);

        IBinder token = mService.createTaskMirroringToken(listenerA.mTopTask.taskId);
        assertThat(token).isNotNull();
    }

    @Test
    public void testGetMirroredSurface_throwsExceptionForInvalidToken() {
        IBinder invalidToken = new Binder();
        Rect outBounds = new Rect();
        assertThrows(IllegalArgumentException.class,
                () -> mService.getMirroredSurface(invalidToken, outBounds));
    }

    @Test
    public void testGetMirroredSurface_throwsExceptionForForgedToken() {
        CarActivityService fakeService = new CarActivityService(getContext());
        IBinder forgedToken = fakeService.createDisplayMirroringToken(Display.DEFAULT_DISPLAY);
        Rect outBounds = new Rect();
        assertThrows(IllegalArgumentException.class,
                () -> mService.getMirroredSurface(forgedToken, outBounds));
    }

    @Test
    public void testGetMirroredSurface_throwsExceptionForExpiredToken() throws Exception {
        FilteredLaunchListener listenerA = startActivityAndAssertLaunched(mActivityA);

        IBinder token = mService.createTaskMirroringToken(listenerA.mTopTask.taskId);
        Rect outBounds = new Rect();

        SystemClock.sleep(SHORT_MIRRORING_TOKEN_TIMEOUT_MS * 2);

        assertThrows(IllegalArgumentException.class,
                () -> mService.getMirroredSurface(token, outBounds));
    }

    @Test
    public void testGetMirroredSurface_returnsNullForInvisibleToken() throws Exception {
        FilteredLaunchListener listenerA = startActivityAndAssertLaunched(mActivityA);

        IBinder token = mService.createTaskMirroringToken(listenerA.mTopTask.taskId);

        // Uses the Activity with the different taskAffinity to make the previous Task hidden.
        startActivityAndAssertLaunched(mBlockingActivity);
        // Now the Surface of the token will be invisible.

        Rect outBounds = new Rect();
        assertThat(mService.getMirroredSurface(token, outBounds)).isNull();
    }

    @Test
    public void testGetMirroredSurface_returnsSurface() throws Exception {
        FilteredLaunchListener listenerA = startActivityAndAssertLaunched(mActivityA);

        IBinder token = mService.createTaskMirroringToken(listenerA.mTopTask.taskId);
        Rect outBounds = new Rect();

        SurfaceControl mirror = mService.getMirroredSurface(token, outBounds);

        expect.that(outBounds.isEmpty()).isFalse();
        expect.that(outBounds).isEqualTo(
                listenerA.mTopTask.getConfiguration().windowConfiguration.getBounds());
        assertThat(mirror).isNotNull();
        assertThat(mirror.isValid()).isTrue();
    }

    private FilteredLaunchListener startActivityAndAssertLaunched(ComponentName activity)
            throws InterruptedException {
        return startActivityAndAssertLaunched(activity, Display.DEFAULT_DISPLAY);
    }

    private FilteredLaunchListener startActivityAndAssertLaunched(
            ComponentName activity, int displayId) throws InterruptedException {
        FilteredLaunchListener listener = new FilteredLaunchListener(activity);
        mService.registerActivityLaunchListener(listener);
        startActivity(activity, displayId);
        listener.assertTopTaskActivityLaunched();
        return listener;
    }

    private void waitUntil(BooleanSupplier condition) {
        for (long i = DEFAULT_TIMEOUT_MS / SLEEP_MS; !condition.getAsBoolean() && i > 0; --i) {
            SystemClock.sleep(SLEEP_MS);
        }
        if (!condition.getAsBoolean()) {
            throw new RuntimeException("failed while waiting for condition to become true");
        }
    }

    private boolean topTasksHasComponent(ComponentName component) {
        for (TaskInfo topTaskInfoContainer : mService.getVisibleTasksInternal()) {
            if (topTaskInfoContainer.topActivity.equals(component)) {
                return true;
            }
        }
        return false;
    }

    /** Activity that closes itself after some timeout to clean up the screen. */
    public static class TempActivity extends Activity {
        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            sTestActivities.add(this);
        }
    }

    public static class ActivityA extends TempActivity {}

    public static class ActivityB extends TempActivity {}

    public static class ActivityC extends TempActivity {}

    public static class BlockingActivity extends TempActivity {}

    private static Context getContext() {
        return getInstrumentation().getTargetContext();
    }

    private static Context getTestContext() {
        return getInstrumentation().getContext();
    }

    private static Activity startActivity(ComponentName name) {
        return startActivity(name, Display.DEFAULT_DISPLAY);
    }

    private static Activity startActivity(ComponentName name, int displayId) {
        ActivityMonitor monitor = new ActivityMonitor(name.getClassName(), null, false);
        getInstrumentation().addMonitor(monitor);

        Intent intent = new Intent();
        intent.setComponent(name);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Bundle bundle = null;
        if (displayId != Display.DEFAULT_DISPLAY) {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(displayId);
            bundle = options.toBundle();
        }
        getContext().startActivity(intent, bundle);
        return monitor.waitForActivityWithTimeout(ACTIVITY_TIMEOUT_MS);
    }

    private class FilteredLaunchListener
            implements CarActivityService.ActivityLaunchListener {

        private final ComponentName mDesiredComponent;
        private final CountDownLatch mActivityLaunched = new CountDownLatch(1);
        private TaskInfo mTopTask;

        /**
         * Creates an instance of an
         * {@link com.android.car.am.CarActivityService.ActivityLaunchListener}
         * that filters based on the component name or does not filter if component name is null.
         */
        private FilteredLaunchListener(@NonNull ComponentName desiredComponent) {
            mDesiredComponent = desiredComponent;
        }

        @Override
        public void onActivityLaunch(TaskInfo topTask) {
            // Ignore activities outside of this test case
            if (!getTestContext().getPackageName().equals(topTask.topActivity.getPackageName())) {
                Log.d(TAG, "Component launched from other package: "
                        + topTask.topActivity.getClassName());
                return;
            }
            if (!topTask.topActivity.equals(mDesiredComponent)) {
                Log.d(TAG, String.format("Unexpected component: %s. Expected: %s",
                        topTask.topActivity.getClassName(), mDesiredComponent));
                return;
            }
            if (mTopTask == null) {  // We are interested in the first one only.
                mTopTask = topTask;
            }
            mActivityLaunched.countDown();
        }

        private void assertTopTaskActivityLaunched() throws InterruptedException {
            assertThat(waitForTopTaskActivityLaunched(DEFAULT_TIMEOUT_MS)).isTrue();
        }

        private boolean waitForTopTaskActivityLaunched(long timeoutMs) throws InterruptedException {
            return mActivityLaunched.await(timeoutMs, TimeUnit.MILLISECONDS);
        }
    }
}
