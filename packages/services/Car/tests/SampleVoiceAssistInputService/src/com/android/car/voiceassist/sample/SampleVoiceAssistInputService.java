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
package com.android.car.voiceassistinput.sample;

import android.app.ActivityOptions;
import android.app.Service;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.input.CarInputManager;
import android.car.media.CarAudioManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import java.net.URISyntaxException;
import java.util.List;

/**
 * This service is a reference implementation to be used as an example on how to define and handle
 * VOICE_ASSIST_KEY events.
 */
public class SampleVoiceAssistInputService extends Service {

    private static final String TAG = "SampleVoiceAssist";

    private Car mCar;
    private CarInputManager mCarInputManager;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Entering onStartCommand with intent={" + intent +
                    "}, flags={" + flags + "} and startId={" + startId + "}");
        }
        if (intent != null) {
            connectToCarService();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Entering onBind with intent={" + intent + "}");
        }
        if (intent != null) {
            connectToCarService();
        }
        return null;
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
                                new int[]{CarInputManager.INPUT_TYPE_SYSTEM_NAVIGATE_KEYS},
                                CarInputManager.CAPTURE_REQ_FLAGS_ALLOW_DELAYED_GRANT,
                                /* callback= */
                                new VoiceAssistEventHandler(getApplicationContext(), this,
                                        (CarOccupantZoneManager) mCar.getCarManager(
                                                Car.CAR_OCCUPANT_ZONE_SERVICE)));
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
}
