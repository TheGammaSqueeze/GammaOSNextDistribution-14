/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.car.custominput.sample;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.input.CarInputManager;
import android.car.input.CustomInputEvent;
import android.car.media.CarAudioManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * This service is a reference implementation to be used as an example on how to define and handle
 * HW_CUSTOM_INPUT events.
 */
// TODO(b/171405561): Add scenarios for cluster display type.
public class SampleCustomInputService extends Service implements
        CarInputManager.CarInputCaptureCallback {

    private static final String TAG = SampleCustomInputService.class.getSimpleName();

    // Notification channel and notification id used by this foreground service
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_CHANNEL_ID =
            SampleCustomInputService.class.getSimpleName();

    private Car mCar;
    private CarInputManager mCarInputManager;
    private CustomInputEventListener mEventHandler;

    @Override
    public void onCreate() {
        createNotificationChannelId(this, NOTIFICATION_CHANNEL_ID);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Entering onBind with intent={" + intent + "}");
        }
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Entering onStartCommand with [intent=" + intent + ", flags=" + flags
                + ", startId=" + startId + "]");
        Notification status = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(createNotificationIcon())
                .setWhen(System.currentTimeMillis())
                .setContentTitle(SampleCustomInputService.class.getSimpleName())
                .setContentText(SampleCustomInputService.class.getSimpleName() + " running")
                .build();
        connectToCarService();
        startForeground(NOTIFICATION_ID, status);
        return START_STICKY;
    }

    private static void createNotificationChannelId(Context context, String channelName) {
        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
        String description = SampleCustomInputService.class.getName();
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, channelName, importance);
        channel.setDescription(description);
        notificationManager.createNotificationChannel(channel);
    }

    private static Icon createNotificationIcon() {
        Bitmap smallIcon = Bitmap.createBitmap(/* width= */ 50, /* height= */ 50,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(smallIcon);
        canvas.drawColor(Color.BLUE);
        return Icon.createWithBitmap(smallIcon);
    }

    private void connectToCarService() {
        if (mCar != null && mCar.isConnected()) {
            Log.w(TAG, "Ignoring request to connect against car service");
            return;
        }
        Log.i(TAG, "Connecting against car service");
        mCar = Car.createCar(this, /* handler= */ null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, ready) -> {
                    mCar = car;
                    if (ready) {
                        mCarInputManager =
                                (CarInputManager) mCar.getCarManager(Car.CAR_INPUT_SERVICE);
                        mCarInputManager.requestInputEventCapture(
                                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                                new int[]{CarInputManager.INPUT_TYPE_CUSTOM_INPUT_EVENT},
                                CarInputManager.CAPTURE_REQ_FLAGS_ALLOW_DELAYED_GRANT,
                                /* callback= */ this);
                        mEventHandler = new CustomInputEventListener(getApplicationContext(),
                                (CarAudioManager) mCar.getCarManager(Car.AUDIO_SERVICE),
                                (CarOccupantZoneManager) mCar.getCarManager(
                                        Car.CAR_OCCUPANT_ZONE_SERVICE),
                                this);
                    }
                });
    }

    @Override
    public void onDestroy() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Entering onDestroy");
        }
        if (mCarInputManager != null) {
            mCarInputManager.releaseInputEventCapture(CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        }
        if (mCar != null) {
            mCar.disconnect();
            mCar = null;
        }
    }

    @Override
    public void onCustomInputEvents(int targetDisplayType,
            @NonNull List<CustomInputEvent> events) {
        for (CustomInputEvent event : events) {
            mEventHandler.handle(targetDisplayType, event);
        }
    }

    public void injectKeyEvent(KeyEvent event, int targetDisplayType) {
        if (mCarInputManager == null) {
            throw new IllegalStateException(
                    "Service was properly initialized, reference to CarInputManager is null");
        }
        mCarInputManager.injectKeyEvent(event, targetDisplayType);
    }
}
