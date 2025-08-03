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

package com.android.car.bugreport;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.car.Car;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.common.base.Preconditions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenshotService extends Service {
    private static final String TAG = ScreenshotService.class.getSimpleName();

    /** Notifications on this channel will silently appear in notification bar. */
    private static final String NOTIFICATION_CHANNEL_ID = "BUGREPORT_SCREENSHOT_CHANNEL";
    private static final int SCREENSHOT_NOTIFICATION_ID = 3;

    private final AtomicBoolean mIsTakingScreenshot = new AtomicBoolean(false);
    private Context mWindowContext;
    private Car mCar;
    private ExecutorService mSingleThreadExecutor;
    private Handler mHandler;

    @Override
    public void onCreate() {
        Preconditions.checkState(Config.isBugReportEnabled(), "BugReport is disabled.");

        DisplayManager displayManager = getSystemService(DisplayManager.class);
        Display primaryDisplay = null;
        if (displayManager != null) {
            primaryDisplay = displayManager.getDisplay(DEFAULT_DISPLAY);
        }
        mWindowContext = createDisplayContext(primaryDisplay)
                .createWindowContext(TYPE_APPLICATION_OVERLAY, null);
        mSingleThreadExecutor = Executors.newSingleThreadExecutor();
        mHandler = new Handler(Looper.myLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mIsTakingScreenshot.get()) {
            Log.w(TAG, "Screenshot is already being taken, ignoring");
            return START_NOT_STICKY;
        }
        mIsTakingScreenshot.set(true);
        startForeground(SCREENSHOT_NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        startScreenshotTask();

        return START_NOT_STICKY;
    }

    private Notification buildNotification() {
        NotificationManager manager = getBaseContext().getSystemService(NotificationManager.class);

        if (manager != null) {
            NotificationChannel serviceChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    getString(R.string.notification_screenshot_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(serviceChannel);
        }

        return new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_screenshot_message))
                .setSmallIcon(R.drawable.download_animation)
                .build();
    }

    private void startScreenshotTask() {
        connectToCarService();
        mSingleThreadExecutor.execute(this::takeScreenshot);
    }

    private void connectToCarService() {
        if (mCar == null || !(mCar.isConnected() || mCar.isConnecting())) {
            mCar = Car.createCar(this);
        }
    }

    private void disconnectFromCarService() {
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }
    }

    private void takeScreenshot() {
        ScreenshotUtils.takeScreenshot(this, mCar);
        mHandler.post(this::finishScreenshotTask);
    }

    private void finishScreenshotTask() {
        Toast.makeText(mWindowContext,
                getText(R.string.toast_screenshot_finished), Toast.LENGTH_SHORT).show();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        mIsTakingScreenshot.set(false);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        disconnectFromCarService();
        super.onDestroy();
    }
}

