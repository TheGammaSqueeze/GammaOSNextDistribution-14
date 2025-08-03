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

package android.media.projection.cts;


import static android.media.projection.cts.MediaProjectionCustomIntentActivity.EXTRA_FGS_CLASS;
import static android.media.projection.cts.MediaProjectionCustomIntentActivity.EXTRA_SCREEN_CAPTURE_INTENT;
import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.cts.ForegroundServiceUtil;
import android.media.cts.LocalMediaProjectionHelperService;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link MediaProjectionManager}.
 *
 * Run with:
 * atest CtsMediaProjectionTestCases:MediaProjectionManagerTest
 */
@NonMainlineTest
public class MediaProjectionManagerTest {
    private Context mContext;

    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection;

    @Rule
    public ActivityTestRule<MediaProjectionCustomIntentActivity> mActivityRule =
            new ActivityTestRule<>(MediaProjectionCustomIntentActivity.class, false, false);

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        runWithShellPermissionIdentity(() -> {
            mContext.getPackageManager().revokeRuntimePermission(
                    mContext.getPackageName(),
                    android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                    new UserHandle(ActivityManager.getCurrentUser()));
        });
        mProjectionManager = mContext.getSystemService(MediaProjectionManager.class);
        mMediaProjection = null;
    }

    @After
    public void tearDown() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionManager#createScreenCaptureIntent")
    @Test
    public void testCreateScreenCaptureIntent() {
        assertThat(mProjectionManager.createScreenCaptureIntent()).isNotNull();
        assertThat(mProjectionManager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay())).isNotNull();
        assertThat(mProjectionManager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForUserChoice())).isNotNull();
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionManager#getMediaProjection")
    @Test
    public void testGetMediaProjection() throws Exception {
        // Launch the activity.
        mActivityRule.launchActivity(null);
        MediaProjectionCustomIntentActivity activity = mActivityRule.getActivity();
        // Ensure mediaprojection instance is valid.
        mMediaProjection = activity.waitForMediaProjection();
        assertThat(mMediaProjection).isNotNull();
    }

    @Test
    public void testGetMediaProjectionSecondaryProcessFgs() throws Exception {
        // Launch the activity, with a media projection FGS running from another process of this app
        mActivityRule.launchActivity(getActivityIntentWithSecondaryProcessFgs());
        MediaProjectionCustomIntentActivity activity = mActivityRule.getActivity();
        // Ensure mediaprojection instance is valid.
        mMediaProjection = activity.waitForMediaProjection();
        assertThat(mMediaProjection).isNotNull();
    }

    @Test
    public void testGetMediaProjectionWithOtherFgs() throws Exception {
        final ComponentName name =
                new ComponentName(mContext, LocalMediaProjectionHelperService.class);
        final long timeOutMs = 5000L * HW_TIMEOUT_MULTIPLIER;
        final CountDownLatch[] latchHolder = new CountDownLatch[2];
        final Runnable helperFgsStarted = () -> {
            latchHolder[0].countDown();
        };
        final Runnable helperFgsStopped = () -> {
            latchHolder[0].countDown();
        };

        // Start a FGS with a type other than the "mediaProjection"
        latchHolder[0] = new CountDownLatch(1);
        ForegroundServiceUtil.requestStartForegroundService(mContext, name,
                helperFgsStarted, helperFgsStopped);
        assertTrue("Can't start FGS", latchHolder[0].await(timeOutMs, TimeUnit.MILLISECONDS));

        // Launch the activity, with a media projection FGS running from another process of this app
        mActivityRule.launchActivity(getActivityIntentWithSecondaryProcessFgs());
        MediaProjectionCustomIntentActivity activity = mActivityRule.getActivity();

        // Ensure mediaprojection instance is valid.
        mMediaProjection = activity.waitForMediaProjection();
        assertThat(mMediaProjection).isNotNull();

        // Register a callback to the mediaprojection instance.
        final MediaProjection.Callback callback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                latchHolder[1].countDown();
            }
        };
        latchHolder[1] = new CountDownLatch(1);
        mMediaProjection.registerCallback(callback, new Handler(Looper.getMainLooper()));

        // Stop the first FGS.
        latchHolder[0] = new CountDownLatch(1);
        mContext.stopService(new Intent().setComponent(name));
        assertTrue("Can't stop FGS", latchHolder[0].await(timeOutMs, TimeUnit.MILLISECONDS));

        // Now the mediaprojection instance should still be valid.
        assertFalse("MediaProjection stopped",
                latchHolder[1].await(timeOutMs, TimeUnit.MILLISECONDS));
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionManager#getMediaProjection")
    @Test
    public void testGetMediaProjectionWithOtherFgsAlter() throws Exception {
        final ComponentName name =
                new ComponentName(mContext, LocalMediaProjectionHelperService.class);
        final long timeOutMs = 5000L * HW_TIMEOUT_MULTIPLIER;
        final CountDownLatch[] latchHolder = new CountDownLatch[2];
        final Runnable helperFgsStarted = () -> {
            latchHolder[0].countDown();
        };
        final Runnable helperFgsStopped = () -> {
            latchHolder[0].countDown();
        };

        // Launch the activity, with a media projection FGS running from another process of this app
        mActivityRule.launchActivity(getActivityIntentWithSecondaryProcessFgs());
        MediaProjectionCustomIntentActivity activity = mActivityRule.getActivity();

        // Ensure mediaprojection instance is valid.
        mMediaProjection = activity.waitForMediaProjection();
        assertThat(mMediaProjection).isNotNull();

        // Register a callback to the mediaprojection instance.
        final MediaProjection.Callback callback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                latchHolder[1].countDown();
            }
        };
        latchHolder[1] = new CountDownLatch(1);
        mMediaProjection.registerCallback(callback, new Handler(Looper.getMainLooper()));

        // Start a FGS with a type other than the "mediaProjection"
        latchHolder[0] = new CountDownLatch(1);
        ForegroundServiceUtil.requestStartForegroundService(mContext, name,
                helperFgsStarted, helperFgsStopped);
        assertTrue("Can't start FGS", latchHolder[0].await(timeOutMs, TimeUnit.MILLISECONDS));

        // Stop the second FGS.
        latchHolder[0] = new CountDownLatch(1);
        mContext.stopService(new Intent().setComponent(name));
        assertTrue("Can't stop FGS", latchHolder[0].await(timeOutMs, TimeUnit.MILLISECONDS));

        // Now the mediaprojection instance should still be valid.
        assertFalse("MediaProjection stopped",
                latchHolder[1].await(timeOutMs, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testGetMediaProjectionMultipleProjections() throws Exception {
        final long timeOutMs = 5000L * HW_TIMEOUT_MULTIPLIER;
        final CountDownLatch[] latchHolder = new CountDownLatch[2];

        // Launch the first activity with a media projection
        mActivityRule.launchActivity(null);
        MediaProjectionCustomIntentActivity activity = mActivityRule.getActivity();

        // Ensure the first mediaprojection instance is valid.
        mMediaProjection = activity.waitForMediaProjection();
        assertThat(mMediaProjection).isNotNull();

        // Register a callback to the first mediaprojection instance.
        final MediaProjection.Callback callback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                latchHolder[0].countDown();
            }
        };
        latchHolder[0] = new CountDownLatch(1);
        mMediaProjection.registerCallback(callback, new Handler(Looper.getMainLooper()));

        // Launch the second activity, with a media projection FGS running from another process
        mActivityRule.launchActivity(getActivityIntentWithSecondaryProcessFgs());
        MediaProjectionCustomIntentActivity activity2 = mActivityRule.getActivity();

        // Ensure the second mediaprojection instance is valid.
        MediaProjection mediaProjection2 = activity2.waitForMediaProjection();
        assertThat(mediaProjection2).isNotNull();

        // Register a callback to the second mediaprojection instance.
        final MediaProjection.Callback callback2 = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                latchHolder[1].countDown();
            }
        };
        latchHolder[1] = new CountDownLatch(1);
        mediaProjection2.registerCallback(callback2, new Handler(Looper.getMainLooper()));

        // Check that first projection IS stopped, but second projection IS NOT stopped
        assertTrue("First MediaProjection was not stopped",
                latchHolder[0].await(timeOutMs, TimeUnit.MILLISECONDS));
        assertFalse("Second projection was stopped",
                latchHolder[1].await(timeOutMs, TimeUnit.MILLISECONDS));
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionManager#getMediaProjection")
    @Test
    public void testGetMediaProjection_displayConfig() throws Exception {
        validateValidMediaProjectionFromIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay());
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionManager#getMediaProjection")
    @Test
    public void testGetMediaProjection_usersChoiceConfig() throws Exception {
        validateValidMediaProjectionFromIntent(MediaProjectionConfig.createConfigForUserChoice());
    }

    private void validateValidMediaProjectionFromIntent(MediaProjectionConfig config)
            throws Exception {
        // Build intent to launch test activity.
        Intent testActivityIntent = new Intent();
        testActivityIntent.setClass(mContext, MediaProjectionCustomIntentActivity.class);
        // Ensure test activity uses given intent when requesting permission.
        testActivityIntent.putExtra(EXTRA_SCREEN_CAPTURE_INTENT,
                mProjectionManager.createScreenCaptureIntent(config));
        // Launch the activity.
        mActivityRule.launchActivity(testActivityIntent);
        MediaProjectionCustomIntentActivity activity = mActivityRule.getActivity();
        // Ensure mediaprojection instance is valid.
        mMediaProjection = activity.waitForMediaProjection();
        assertThat(mMediaProjection).isNotNull();
    }

    private Intent getActivityIntentWithSecondaryProcessFgs() {
        return new Intent().putExtra(EXTRA_FGS_CLASS,
                "android.media.cts.LocalMediaProjectionSecondaryService");
    }
}
