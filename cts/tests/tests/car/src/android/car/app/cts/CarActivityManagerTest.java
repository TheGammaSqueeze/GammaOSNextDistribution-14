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
package android.car.app.cts;

import static android.car.cts.utils.DisplayUtils.VirtualDisplaySession;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.car.app.CarTaskViewController;
import android.car.app.CarTaskViewControllerCallback;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class CarActivityManagerTest {

    // Comes from android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER
    private static final int FEATURE_DEFAULT_TASK_CONTAINER = 1;

    // Comes from android.window.DisplayAreaOrganizer.FEATURE_UNDEFINED
    private static final int FEATURE_UNDEFINED = -1;

    private static final long TIMEOUT_FOR_ACTIVITY_DESTROYED = 1_000;  // ms

    private final Instrumentation mInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();
    private final Context mTargetContext = mInstrumentation.getTargetContext();
    private final UiAutomation mUiAutomation = mInstrumentation.getUiAutomation();
    private final ComponentName mTestActivity =
            new ComponentName(mTargetContext, TestActivity.class);
    private final ComponentName mFakedTestActivity = new ComponentName("fake_pkg", "fake_class");

    private CarActivityManager mCarActivityManager;
    // When Maps in TaskView is unstable, the test can be flaky, launches a BlankActivity to make
    // the test robust. See details at b/270989631.
    private Activity mBlankActivity;

    @Before
    public void setUp() {
        Car car = Car.createCar(mContext);
        mUiAutomation.adoptShellPermissionIdentity(
                Car.PERMISSION_CONTROL_CAR_APP_LAUNCH,  // for CAM.setPersistentActivity
                Car.PERMISSION_MANAGE_CAR_SYSTEM_UI,  // for CAM.getCarTaskViewController
                // to launch an Activity in the virtual display
                Manifest.permission.ACTIVITY_EMBEDDING,
                Manifest.permission.MANAGE_ACTIVITY_TASKS /* for CAM.getVisibleTasks */);
        mCarActivityManager =
            (CarActivityManager) car.getCarManager(Car.CAR_ACTIVITY_SERVICE);
        assertThat(mCarActivityManager).isNotNull();

        mBlankActivity = mInstrumentation.startActivitySync(
                Intent.makeMainActivity(new ComponentName(mTargetContext, BlankActivity.class))
                        .addFlags(FLAG_ACTIVITY_NEW_TASK), /* option */ null);
    }

    @After
    public void tearDown() {
        mBlankActivity.finishAndRemoveTask();

        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testSetPersistentActivity() throws Exception {
        try (VirtualDisplaySession session = new VirtualDisplaySession()) {
            // create a secondary virtual display
            Display secondaryDisplay =
                    session.createDisplayWithDefaultDisplayMetricsAndWait(mContext, true);
            assertThat(secondaryDisplay).isNotNull();
            int secondaryDisplayId = secondaryDisplay.getDisplayId();

            // Need some delay to propagate the virtual display to ActivityTaskManager internal.
            // Without this, setPersistentActivity() would fail or cause the system crash.
            SystemClock.sleep(200);

            // assign the activity to the secondary virtual display
            int ret = mCarActivityManager.setPersistentActivity(mTestActivity,
                    secondaryDisplayId, FEATURE_DEFAULT_TASK_CONTAINER);
            assertThat(ret).isEqualTo(CarActivityManager.RESULT_SUCCESS);

            // launch the activity
            Intent startIntent = Intent.makeMainActivity(mTestActivity)
                    .addFlags(FLAG_ACTIVITY_NEW_TASK);
            TestActivity activity = (TestActivity) mInstrumentation.startActivitySync(
                    startIntent, /* option */ null);

            // assert the activity is launched into the virtual display
            assertThat(activity.getDisplay().getDisplayId()).isEqualTo(secondaryDisplayId);

            // destroy the newly created activity and remove the launch display area assignment
            activity.finishAndRemoveTask();

            // Wait for the Activity is completely removed.
            assertThat(activity.waitForDestroyed()).isTrue();

            ret = mCarActivityManager.setPersistentActivity(mTestActivity,
                    secondaryDisplayId, FEATURE_UNDEFINED);
            assertThat(ret).isEqualTo(CarActivityManager.RESULT_SUCCESS);

            // re-launch again and assert that it is not launched in the secondaryDisplay
            activity = (TestActivity) mInstrumentation.startActivitySync(
                    startIntent, /* option */ null);
            assertThat(activity.getDisplay().getDisplayId()).isEqualTo(DEFAULT_DISPLAY);

            // tear down
            activity.finishAndRemoveTask();
        }
    }

    @Test
    public void testSetPersistentActivity_throwsExceptionForInvalidDisplayId() {
        int invalidDisplayId = 999999990;

        assertThrows(IllegalArgumentException.class,
                () -> mCarActivityManager.setPersistentActivity(
                        mFakedTestActivity, invalidDisplayId, FEATURE_DEFAULT_TASK_CONTAINER));
    }

    @Test
    public void testSetPersistentActivity_throwsExceptionForInvalidFeatureId() {
        int unknownFeatureId = 999999990;

        assertThrows(IllegalArgumentException.class,
                () -> mCarActivityManager.setPersistentActivity(
                        mFakedTestActivity, Display.DEFAULT_DISPLAY, unknownFeatureId));
    }

    @Test
    public void testSetPersistentActivity_throwsExceptionForUnregsiteringUnknownActivity() {
        // Tries to remove the Activity without registering it.
        assertThrows(ActivityNotFoundException.class,
                () -> mCarActivityManager.setPersistentActivity(
                        mFakedTestActivity, Display.DEFAULT_DISPLAY, FEATURE_UNDEFINED));
    }

    @Test
    @ApiTest(apis = {"android.car.app.CarActivityManager#moveRootTaskToDisplay(int,int)",
            "android.car.builtin.app.ActivityManagerHelper#moveRootTaskToDisplay(int,int)"})
    public void testMoveRootTaskToDisplay() throws Exception {
        // TODO(b/300466988): Create a new API that can move a leaf task to display even when there
        // are extra nested root tasks in the hierarchy.
        assumeFalse(hasSplitscreenMultitaskingFeature());

        try (VirtualDisplaySession session = new VirtualDisplaySession()) {
            // create a secondary virtual display
            Display secondaryDisplay = session.createDisplay(mContext,
                    /* width= */ 400, /* height= */ 300, /* density= */ 120, /* private= */ true);
            assertThat(secondaryDisplay).isNotNull();
            int secondaryDisplayId = secondaryDisplay.getDisplayId();

            // launch the activity
            Intent startIntent = Intent.makeMainActivity(mTestActivity)
                    .addFlags(FLAG_ACTIVITY_NEW_TASK);
            TestActivity activity = (TestActivity) mInstrumentation.startActivitySync(
                    startIntent, /* option */ null);

            // assert the activity is launched into the default display
            assertThat(activity.getDisplay().getDisplayId()).isEqualTo(DEFAULT_DISPLAY);

            mCarActivityManager.moveRootTaskToDisplay(activity.getTaskId(), secondaryDisplayId);

            // The activity will be recreated since the moved display has the different geometry.
            assertThat(activity.waitForDestroyed()).isTrue();

            // Wait for the new Activity is created.
            PollingCheck.waitFor(() -> TestActivity.sInstance != null
                    && activity != TestActivity.sInstance);

            assertThat(TestActivity.sInstance.getDisplay().getDisplayId())
                    .isEqualTo(secondaryDisplayId);
        }
    }

    @ApiTest(apis = {"android.car.app.CarActivityManager#getVisibleTasks"})
    @Test
    public void testGetVisibleTasks() throws Exception {
        // launch the activity
        Intent startIntent = Intent.makeMainActivity(mTestActivity)
                .addFlags(FLAG_ACTIVITY_NEW_TASK);
        TestActivity activity = (TestActivity) mInstrumentation.startActivitySync(
                startIntent, /* option */ null);

        List<RunningTaskInfo> tasks = mCarActivityManager.getVisibleTasks();
        List<RunningTaskInfo> filteredTasks = tasks.stream()
                .filter(t -> t.topActivity.getClassName().equals(TestActivity.class.getName()))
                .collect(Collectors.toList());
        assertThat(filteredTasks).hasSize(1);
        assertThat(filteredTasks.get(0).isVisible()).isTrue();
        assertThat(filteredTasks.get(0).getDisplayId()).isEqualTo(0);
    }

    @ApiTest(apis = {"android.car.app.CarActivityManager#getVisibleTasks(int)"})
    @Test
    public void testGetVisibleTasksByDisplayId() throws Exception {
        try (VirtualDisplaySession session = new VirtualDisplaySession()) {
            // create a secondary virtual display
            Display virtualDisplay = session.createDisplay(mContext,
                    /* width= */ 400, /* height= */ 300, /* density= */ 120, /* private= */ true);
            assertThat(virtualDisplay).isNotNull();
            int displayId = virtualDisplay.getDisplayId();

            // launch the activity
            Intent startIntent = Intent.makeMainActivity(mTestActivity)
                    .addFlags(FLAG_ACTIVITY_NEW_TASK);
            ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId);
            TestActivity activity = (TestActivity) mInstrumentation.startActivitySync(
                    startIntent, options.toBundle());

            List<RunningTaskInfo> tasks = mCarActivityManager.getVisibleTasks(displayId);
            assertThat(tasks).hasSize(1);
            assertThat(tasks.get(0).isVisible()).isTrue();
            assertThat(tasks.get(0).getDisplayId()).isEqualTo(displayId);
        }
    }

    @Test
    @ApiTest(apis = {"android.car.app.CarActivityManager#getCarTaskViewController(Activity,"
            + "Executor,CarTaskViewControllerCallback)"})
    public void getCarTaskViewController() throws Exception {
        assumeTrue(mCarActivityManager.isCarSystemUIProxyRegistered());
        Intent startIntent = Intent.makeMainActivity(mTestActivity)
                .addFlags(FLAG_ACTIVITY_NEW_TASK);
        TestActivity activity = (TestActivity) mInstrumentation.startActivitySync(
                startIntent, /* option */ null);
        TestCarTaskViewControllerCallback callback = new TestCarTaskViewControllerCallback();

        mTargetContext.getMainExecutor().execute(() ->
                mCarActivityManager.getCarTaskViewController(activity, mContext.getMainExecutor(),
                        callback));

        PollingCheck.waitFor(() -> callback.isConnected());
        assertTrue(callback.isConnected());
        activity.finishAndRemoveTask();
        activity.waitForDestroyed();
    }

    public static final class TestCarTaskViewControllerCallback
            implements CarTaskViewControllerCallback {
        private boolean mConnected = false;

        @Override
        public void onConnected(@NonNull CarTaskViewController carTaskViewController) {
            mConnected = true;
        }

        @Override
        public void onDisconnected(@NonNull CarTaskViewController carTaskViewController) {
            mConnected = false;
        }

        public boolean isConnected() {
            return mConnected;
        }
    }

    public static final class TestActivity extends Activity {
        private static TestActivity sInstance;
        private final CountDownLatch mDestroyed = new CountDownLatch(1);

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            sInstance = this;
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            mDestroyed.countDown();
            sInstance = null;
        }

        private boolean waitForDestroyed() throws InterruptedException {
            return mDestroyed.await(TIMEOUT_FOR_ACTIVITY_DESTROYED, TimeUnit.MILLISECONDS);
        }
    }

    public static final class BlankActivity extends Activity {
    }

    /**
     * Checks whether the device has automotive split-screen multitasking feature enabled
     */
    private boolean hasSplitscreenMultitaskingFeature() {
        return mContext.getPackageManager()
                .hasSystemFeature(/* PackageManager.FEATURE_CAR_SPLITSCREEN_MULTITASKING */
                        "android.software.car.splitscreen_multitasking");
    }
}
