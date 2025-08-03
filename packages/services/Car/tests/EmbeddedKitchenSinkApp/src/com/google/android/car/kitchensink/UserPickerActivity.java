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

package com.google.android.car.kitchensink;

import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.car.Car;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;

import androidx.fragment.app.FragmentActivity;

/**
 * Activity that simply shows the SimpleUserPickerFragment.
 */
public final class UserPickerActivity extends FragmentActivity {
    private static final String TAG = UserPickerActivity.class.getSimpleName();

    private Car mCar;

    public Car getCar() {
        return mCar;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int myUserId = UserHandle.myUserId();
        Log.i(TAG, "onCreate userid " + myUserId);
        if (myUserId != UserHandle.USER_SYSTEM) {
            // "Trampoline pattern": restarting itself as user 0 so the user picker can stay
            // when the user that launched the user picker logs out of the display.
            Log.i(TAG, "onCreate re-starting self as user 0");
            Intent selfIntent = new Intent(UserPickerActivity.this, UserPickerActivity.class)
                    .addFlags(FLAG_ACTIVITY_MULTIPLE_TASK | FLAG_ACTIVITY_NEW_TASK);
            startActivityAsUser(selfIntent, UserHandle.SYSTEM);
            finish();
        } else {
            Log.i(TAG, "onCreate rendering user picker");
            connectCar();
            setContentView(R.layout.user_picker_activity);
        }
    }

    @Override
    protected void onDestroy() {
        if (mCar != null) {
            mCar.disconnect();
        }
        super.onDestroy();
    }

    // TODO(244370727): Factor out the car connection logic to be shared by KitchenSinkActivity
    // and this activity and possibly any activity that needs car connection.
    private void connectCar() {
        if (mCar != null && mCar.isConnected()) {
            return;
        }
        mCar = Car.createCar(this, null,
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT, (car, ready) -> {
                    if (!ready) {
                        // Let car service relaunch this.
                        Log.i(TAG, "Car service crashed, finish");
                        finish();
                        return;
                    }
                });
        if (mCar == null) {
            // Car service has crashed. Let it restart this.
            Log.i(TAG, "null Car from createCar, finish");
            finish();
        }
    }
}
