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

package android.sensitivecontentprotection.cts;

import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.Activity;
import android.app.ActivityOptions.LaunchCookie;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for going through the MediaProjection Intent based authorization flow.
 */
public class SensitiveContentMediaProjectionHelper {

    private static final String EXTRA_LAUNCH_COOKIE = "android.server.wm.extra.EXTRA_LAUNCH_COOKIE";

    /**
     * Activity that launches the MediaProjection screenCaptureIntent. If
     * {@link #EXTRA_LAUNCH_COOKIE} is set, media projection will record the task
     * associated with that launch cookie. Otherwise, media projection will record the full display.
     */
    public static class MediaProjectionActivity extends Activity {

        private static final int REQUEST_MEDIA_PROJECTION = 1;

        private int mResultCode;
        private Intent mResultIntent;
        private final CountDownLatch mStopLatch = new CountDownLatch(1);

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            MediaProjectionManager mediaProjectionManager = getSystemService(
                    MediaProjectionManager.class);
            Objects.requireNonNull(mediaProjectionManager);

            // Adopt shell permissions so we have CAPTURE_VIDEO_OUTPUT. This lets us bypass the
            // MediaProjection authorization dialog.
            UiAutomation uiAutomation =
                    InstrumentationRegistry.getInstrumentation().getUiAutomation();
            uiAutomation.adoptShellPermissionIdentity();

            LaunchCookie launchCookie = getIntent().getParcelableExtra(EXTRA_LAUNCH_COOKIE,
                    LaunchCookie.class);
            Intent screenCaptureIntent =
                    launchCookie == null ? mediaProjectionManager.createScreenCaptureIntent()
                            : mediaProjectionManager.createScreenCaptureIntent(launchCookie);
            startActivityForResult(screenCaptureIntent, REQUEST_MEDIA_PROJECTION);
        }

        @Override
        public void onStop() {
            super.onStop();
            mStopLatch.countDown();
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            UiAutomation uiAutomation =
                    InstrumentationRegistry.getInstrumentation().getUiAutomation();
            uiAutomation.dropShellPermissionIdentity();
            if (requestCode != REQUEST_MEDIA_PROJECTION) {
                throw new IllegalStateException("Unexpected request code " + requestCode);
            }
            if (resultCode != RESULT_OK) {
                throw new IllegalStateException("MediaProjection request denied");
            }
            mResultCode = resultCode;
            mResultIntent = data;
            finish();
        }

        int getResultCode() {
            return mResultCode;
        }

        Intent getResultData() {
            return mResultIntent;
        }

        CountDownLatch getStopLatch() {
            return mStopLatch;
        }
    }

    /**
     * Foreground MediaProjection service. MediaProjection requires a foreground service before
     * recording begins.
     */
    public static class MediaProjectionService extends Service {

        private static final String CHANNEL_ID = "ScreenRecordingCallbackTests";
        private static final String CHANNEL_NAME = "MediaProjectionService";
        private static final int NOTIFICATION_ID = 1;

        private final IBinder mBinder = new ServiceBinder(this);
        private final CountDownLatch mForegroundStarted = new CountDownLatch(1);
        private Bitmap mIconBitmap;

        @Override
        public IBinder onBind(Intent intent) {
            return mBinder;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_NONE);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);

            Notification.Builder notificationBuilder = new Notification.Builder(this, CHANNEL_ID);

            mIconBitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mIconBitmap);
            canvas.drawColor(Color.BLUE);
            Icon icon = Icon.createWithBitmap(mIconBitmap);

            Notification notification = notificationBuilder.setOngoing(true).setContentTitle(
                    "App is running").setSmallIcon(icon).setCategory(
                    Notification.CATEGORY_SERVICE).setContentText("Context").build();

            startForeground(NOTIFICATION_ID, notification);
            mForegroundStarted.countDown();

            return super.onStartCommand(intent, flags, startId);
        }

        @Override
        public void onDestroy() {
            if (mIconBitmap != null) {
                mIconBitmap.recycle();
                mIconBitmap = null;
            }
            super.onDestroy();
        }

        void waitForForegroundService() {
            await(mForegroundStarted, "MediaProjectionService failed to start foreground service");
        }
    }

    private static class ServiceBinder extends Binder {
        final MediaProjectionService mService;

        ServiceBinder(MediaProjectionService service) {
            mService = service;
        }
    }

    private int mResultCode;
    private Intent mResultData;

    private static void await(CountDownLatch latch, String failureMessage) {
        try {
            if (!latch.await(100, TimeUnit.SECONDS)) {
                throw new IllegalStateException(failureMessage);
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(failureMessage, e);
        }
    }

    /**
     * See {@link #authorizeMediaProjection(LaunchCookie)}.
     */
    public void authorizeMediaProjection() {
        authorizeMediaProjection(/*launchCookie=*/null);
    }

    /**
     * Launches an activity that triggers MediaProjection authorization.
     *
     * @param launchCookie  Optional launch cookie to specify the task to record.
     */
    public void authorizeMediaProjection(@Nullable LaunchCookie launchCookie) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context targetContext = instrumentation.getTargetContext();

        Intent intent = new Intent(targetContext, MediaProjectionActivity.class).addFlags(
                FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_LAUNCH_COOKIE, launchCookie);

        MediaProjectionActivity[] activity = new MediaProjectionActivity[1];
        SystemUtil.runWithShellPermissionIdentity(() -> {
            activity[0] = (MediaProjectionActivity) instrumentation.startActivitySync(intent);
        });
        await(activity[0].getStopLatch(), "Failed to wait for MediaProjectionActivity to stop");

        CountDownLatch serviceLatch = new CountDownLatch(1);
        MediaProjectionService[] service = new MediaProjectionService[1];

        Intent serviceIntent = new Intent(targetContext, MediaProjectionService.class);
        targetContext.bindService(serviceIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                service[0] = ((ServiceBinder) binder).mService;
                serviceLatch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        }, BIND_AUTO_CREATE);
        targetContext.startForegroundService(serviceIntent);

        await(serviceLatch, "Failed to connect to MediaProjectionService");
        service[0].waitForForegroundService();

        mResultCode = activity[0].getResultCode();
        mResultData = activity[0].getResultData();
    }

    /**
     * Start MediaProjection. {@link #authorizeMediaProjection(LaunchCookie)} must be called before
     * calling this method.
     *
     * @return the {@link MediaProjection} instance returned by {@link MediaProjectionManager}.
     */
    public MediaProjection startMediaProjection() {
        if (mResultData == null) {
            throw new IllegalStateException(
                    "authorizeMediaProjection not called before startMediaProjection");
        }

        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MediaProjectionManager mediaProjectionManager = targetContext.getSystemService(
                MediaProjectionManager.class);
        Objects.requireNonNull(mediaProjectionManager);
        return mediaProjectionManager.getMediaProjection(mResultCode, mResultData);
    }
}
