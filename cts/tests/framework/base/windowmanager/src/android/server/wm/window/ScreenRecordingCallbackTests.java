/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.view.WindowManager.SCREEN_RECORDING_STATE_NOT_VISIBLE;
import static android.view.WindowManager.SCREEN_RECORDING_STATE_VISIBLE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.app.ActivityOptions;
import android.app.ActivityOptions.LaunchCookie;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.MediaProjectionHelper;
import android.server.wm.WindowManagerTestBase;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.SystemUtil;
import com.android.window.flags.Flags;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * CTS tests for {@link android.view.WindowManager#addScreenRecordingCallback}.
 *
 * Media Projection set up is handled by {@link android.server.wm.MediaProjectionHelper}. The
 * helper handles Media Projection authorization and foreground service requirements. For each test,
 * a new instance of MediaProjection is obtained through the Intent flow and a new instance of the
 * required foreground service is started.
 */
public class ScreenRecordingCallbackTests extends WindowManagerTestBase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static class TestableScreenRecordingCallback implements Consumer<Integer> {
        private final BlockingQueue<Integer> mQueue = new LinkedBlockingDeque<>();

        @Override
        public void accept(Integer state) {
            mQueue.add(state);
        }

        int getState() throws InterruptedException {
            Integer value = mQueue.poll(5L * HW_TIMEOUT_MULTIPLIER, TimeUnit.SECONDS);
            if (value == null) {
                throw new AssertionError("Callback not called");
            }
            return value;
        }

        boolean queueEmpty() {
            return mQueue.isEmpty();
        }
    }

    public static class ScreenRecordingCallbackActivity extends FocusableActivity {

        final TestableScreenRecordingCallback mCallback = new TestableScreenRecordingCallback();

        @Override
        public void onStart() {
            super.onStart();
            int initialState = getWindowManager().addScreenRecordingCallback(getMainExecutor(),
                    mCallback);
            mCallback.accept(initialState);
        }

        @Override
        public void onStop() {
            super.onStop();
            getWindowManager().removeScreenRecordingCallback(mCallback);
        }
    }

    private MediaProjectionHelper mMediaProjectionHelper = new MediaProjectionHelper();
    private MediaProjection mMediaProjection = null;

    @After
    public void stopMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }
    }

    private ScreenRecordingCallbackActivity startCallbackActivityWithLaunchCookie(
            @NonNull LaunchCookie launchCookie) {
        Intent intent = new Intent(getInstrumentation().getTargetContext(),
                ScreenRecordingCallbackActivity.class).addFlags(FLAG_ACTIVITY_NEW_TASK);

        ActivityOptions activityOptions = ActivityOptions.makeBasic();
        activityOptions.setLaunchCookie(launchCookie);
        ScreenRecordingCallbackActivity[] activity = new ScreenRecordingCallbackActivity[1];
        SystemUtil.runWithShellPermissionIdentity(() -> activity[0] =
                (ScreenRecordingCallbackActivity) getInstrumentation().startActivitySync(intent,
                        activityOptions.toBundle()));
        return activity[0];
    }

    private void resumeCallbackActivity() {
        Intent intent = new Intent(mContext, ScreenRecordingCallbackActivity.class);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP);
        mContext.startActivity(intent);
    }

    private void startExternalActivity(@Nullable LaunchCookie launchCookie) {
        ComponentName componentName = ComponentName.createRelative("android.server.wm",
                ".ExternalActivity");

        Intent intent = new Intent();
        intent.setComponent(componentName);
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);

        ActivityOptions activityOptions = ActivityOptions.makeBasic();
        if (launchCookie != null) {
            activityOptions.setLaunchCookie(launchCookie);
        }
        mContext.startActivity(intent, activityOptions.toBundle());
    }

    /**
     * Test the screen recording callback is called correctly for an activity that starts before
     * MediaProjection starts.
     */
    @RequiresFlagsEnabled(Flags.FLAG_SCREEN_RECORDING_CALLBACKS)
    @ApiTest(apis = {"android.view.WindowManager#addScreenRecordingCallback",
            "android.view.WindowManager#removeScreenRecordingCallback"})
    @Test
    public void testFullDisplayMediaProjectionStartsAfterActivity() throws InterruptedException {
        mMediaProjectionHelper.authorizeMediaProjection();
        ScreenRecordingCallbackActivity activity = startActivity(
                ScreenRecordingCallbackActivity.class);

        assertEquals("Expected app to not be visible in screen recording before media projection"
                + " starts", SCREEN_RECORDING_STATE_NOT_VISIBLE, activity.mCallback.getState());

        mMediaProjection = mMediaProjectionHelper.startMediaProjection();
        assertEquals("Expected app to be visible in screen recording after starting media"
                + " projection", SCREEN_RECORDING_STATE_VISIBLE, activity.mCallback.getState());

        mMediaProjection.stop();
        assertEquals("Expected app to no longer be visible in screen recording after stopping media"
                + " projection", SCREEN_RECORDING_STATE_NOT_VISIBLE, activity.mCallback.getState());

        assertTrue(activity.mCallback.queueEmpty());
    }

    /**
     * Test the screen recording callback is called correctly for an activity that starts after
     * MediaProjection starts.
     */
    @RequiresFlagsEnabled(Flags.FLAG_SCREEN_RECORDING_CALLBACKS)
    @ApiTest(apis = {"android.view.WindowManager#addScreenRecordingCallback",
            "android.view.WindowManager#removeScreenRecordingCallback"})
    @Test
    public void testFullDisplayMediaProjectionStartsBeforeActivity() throws InterruptedException {
        mMediaProjectionHelper.authorizeMediaProjection();
        mMediaProjection = mMediaProjectionHelper.startMediaProjection();

        ScreenRecordingCallbackActivity activity = startActivity(
                ScreenRecordingCallbackActivity.class);
        assertEquals("Expected app to be visible in screen recording when the app starts",
                SCREEN_RECORDING_STATE_VISIBLE, activity.mCallback.getState());

        mMediaProjection.stop();
        assertEquals("Expected app to no longer be visible in screen recording after stopping media"
                + " projection", SCREEN_RECORDING_STATE_NOT_VISIBLE, activity.mCallback.getState());

        assertTrue(activity.mCallback.queueEmpty());
    }

    /**
     * Test the screen recording callback is called correctly when it is not added/removed during an
     * Activity's onStart/onStop methods.
     */
    @RequiresFlagsEnabled(Flags.FLAG_SCREEN_RECORDING_CALLBACKS)
    @ApiTest(apis = {"android.view.WindowManager#addScreenRecordingCallback",
            "android.view.WindowManager#removeScreenRecordingCallback"})
    @Test
    public void testFullDisplayMediaProjectionAppCallback() throws InterruptedException {
        mMediaProjectionHelper.authorizeMediaProjection();
        mMediaProjection = mMediaProjectionHelper.startMediaProjection();

        TestableScreenRecordingCallback callback = new TestableScreenRecordingCallback();
        WindowManager windowManager = mTargetContext.getSystemService(WindowManager.class);
        int initialState = windowManager.addScreenRecordingCallback(
                mTargetContext.getMainExecutor(), callback);
        assertEquals("Expected app to not be visible in screen recording before activity starts",
                SCREEN_RECORDING_STATE_NOT_VISIBLE, initialState);

        startActivity(ScreenRecordingCallbackActivity.class);
        assertEquals("Expected app to be visible in screen recording after activity starts",
                SCREEN_RECORDING_STATE_VISIBLE, callback.getState());

        mMediaProjection.stop();
        assertEquals(
                "Expected app to not be visible in screen recording after media projection stops",
                SCREEN_RECORDING_STATE_NOT_VISIBLE, callback.getState());

        assertTrue(callback.queueEmpty());
    }

    /**
     * Test the screen recording callback is called correctly when media projection records a
     * specific task.
     */
    @RequiresFlagsEnabled(Flags.FLAG_SCREEN_RECORDING_CALLBACKS)
    @ApiTest(apis = {"android.view.WindowManager#addScreenRecordingCallback",
            "android.view.WindowManager#removeScreenRecordingCallback"})
    @Test
    public void testPartialScreenSharingRecorded() throws InterruptedException {
        // The LaunchCookie is used to test partial screen sharing. In the typical Media
        // Projection flow, when a user selects partial screen sharing, their selected app is
        // launched into a new task with a specific launch cookie. Media Projection then records
        // the window container corresponding to the task with that launch cookie. In this test,
        // we specify the launch cookie to record and then directly start an activity with that
        // launch cookie.
        ActivityOptions.LaunchCookie launchCookie = new ActivityOptions.LaunchCookie();
        mMediaProjectionHelper.authorizeMediaProjection(launchCookie);
        startCallbackActivityWithLaunchCookie(launchCookie);

        TestableScreenRecordingCallback callback = new TestableScreenRecordingCallback();
        WindowManager windowManager = mTargetContext.getSystemService(WindowManager.class);
        Objects.requireNonNull(windowManager);
        int initialState = windowManager.addScreenRecordingCallback(
                mTargetContext.getMainExecutor(), callback);
        assertEquals("Expected app to not be visible in screen recording before activity starts",
                SCREEN_RECORDING_STATE_NOT_VISIBLE, initialState);

        mMediaProjection = mMediaProjectionHelper.startMediaProjection();
        assertEquals("Expected app to be visible in screen recording after starting media"
                + " projection", SCREEN_RECORDING_STATE_VISIBLE, callback.getState());

        startExternalActivity(/*launchCookie=*/ null);

        assertEquals(
                "Expected app to not be visible in screen recording when another activity starts",
                SCREEN_RECORDING_STATE_NOT_VISIBLE, callback.getState());

        resumeCallbackActivity();
        assertEquals("Expected app to be visible in screen recording when activity resumed",
                SCREEN_RECORDING_STATE_VISIBLE, callback.getState());

        mMediaProjection.stop();
        assertEquals("Expected app to not be visible in screen recording after media projection"
                + " stops", SCREEN_RECORDING_STATE_NOT_VISIBLE, callback.getState());

        assertTrue(callback.queueEmpty());
    }

    /**
     * Test the screen recording callback is not called when media projection is recording a
     * different task.
     */
    @RequiresFlagsEnabled(Flags.FLAG_SCREEN_RECORDING_CALLBACKS)
    @ApiTest(apis = {"android.view.WindowManager#addScreenRecordingCallback",
            "android.view.WindowManager#removeScreenRecordingCallback"})
    @Test
    public void testPartialScreenSharingNotRecorded() throws InterruptedException {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));

        // The LaunchCookie is used to test partial screen sharing. In the typical Media
        // Projection flow, when a user selects partial screen sharing, their selected app is
        // launched into a new task with a specific launch cookie. Media Projection then records
        // the window container corresponding to the task with that launch cookie. In this test,
        // we specify the launch cookie to record and then directly start an activity with that
        // launch cookie.
        LaunchCookie launchCookie = new LaunchCookie();
        mMediaProjectionHelper.authorizeMediaProjection(launchCookie);
        startExternalActivity(launchCookie);
        mMediaProjection = mMediaProjectionHelper.startMediaProjection();

        ScreenRecordingCallbackActivity activity = startActivity(
                ScreenRecordingCallbackActivity.class);
        assertEquals(
                "Expected app to not be visible in screen recording when recording a different "
                        + "task", SCREEN_RECORDING_STATE_NOT_VISIBLE,
                activity.mCallback.getState());

        mMediaProjection.stop();

        assertTrue(activity.mCallback.queueEmpty());
    }
}
