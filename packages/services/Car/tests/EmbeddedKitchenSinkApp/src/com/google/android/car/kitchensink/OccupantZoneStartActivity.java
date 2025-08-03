/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.car.kitchensink;

import static android.car.Car.APP_FOCUS_SERVICE;
import static android.car.Car.CAR_OCCUPANT_ZONE_SERVICE;
import static android.car.Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER;
import static android.car.CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.Activity;
import android.car.Car;
import android.car.CarAppFocusManager;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;

import java.util.List;

public final class OccupantZoneStartActivity extends Activity {

    private static final String TAG = "CAR.OCCUPANT.KS.START";
    private static final String AUDIO_ACTIVITY =
            "com.google.android.car.kitchensink/.AudioAutoStartActivity";

    private Handler mHandler;
    private Car mCar;
    private CarAppFocusManager mAppFocusManager;
    private CarOccupantZoneManager mOccupantZoneManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectCar();
        setContentView(R.layout.empty_activity);
        mHandler.post(() -> startActivity());
    }

    private void startActivity() {
        Intent intent = getAppIntent();

        Display display = getDisplay();

        if (display == null) {
            Log.e(TAG, "Could not start activity, null display.");
            return;
        }

        OccupantZoneInfo zoneInfo = getOccupantZoneForDisplay(display);
        if (zoneInfo == null) {
            Log.e(TAG, "Could not start activity, no occupant zone for display");
            return;
        }
        int userId = mOccupantZoneManager.getUserForOccupant(zoneInfo);
        if (userId == UserHandle.USER_NULL) {
            Log.e(TAG, "Could not start activity, no userId for occupant zone");
            return;
        }
        Log.i(TAG, "Start activty for user " + userId);
        startActivityAsUser(intent, UserHandle.of(userId));
    }

    private OccupantZoneInfo getOccupantZoneForDisplay(Display display) {
        List<OccupantZoneInfo> occupantZoneInfos = mOccupantZoneManager.getAllOccupantZones();
        for (int index = 0; index < occupantZoneInfos.size(); index++) {
            OccupantZoneInfo occupantZoneInfo = occupantZoneInfos.get(index);
            List<Display> displays = mOccupantZoneManager.getAllDisplaysForOccupant(
                    occupantZoneInfo);
            for (int displayIndex = 0; displayIndex < displays.size(); displayIndex++) {
                if (displays.get(displayIndex).getDisplayId() == display.getDisplayId()) {
                    return occupantZoneInfo;
                }
            }
        }
        return null;
    }

    private Intent getAppIntent() {
        return new Intent()
                .setComponent(ComponentName.unflattenFromString(AUDIO_ACTIVITY))
                .addFlags(FLAG_ACTIVITY_NEW_TASK);
    }

    private void connectCar() {
        mHandler = new Handler(Looper.getMainLooper(), null, true);
        mCar = Car.createCar(this, null,
                CAR_WAIT_TIMEOUT_WAIT_FOREVER, (car, ready) -> {
                    if (!ready) {
                        return;
                    }
                    mAppFocusManager =
                            (CarAppFocusManager) car.getCarManager(APP_FOCUS_SERVICE);
                    mAppFocusManager.addFocusListener(
                            (CarAppFocusManager.OnAppFocusChangedListener) (appType, active) -> {

                            },
                            APP_FOCUS_TYPE_NAVIGATION);

                    mOccupantZoneManager = (CarOccupantZoneManager)
                            car.getCarManager(CAR_OCCUPANT_ZONE_SERVICE);
                });
    }
}
