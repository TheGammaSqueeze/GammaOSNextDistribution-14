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

package com.android.car.settings.admin;

import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_PARKED;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.car.Car;
import android.car.ICarResultReceiver;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;


/**
 * Activity shown when a factory request is imminent, it gives the user the option to reset now or
 * wait until the device is rebooted / resumed from suspend.
 */
public final class FactoryResetActivity extends Activity {
    private static final String EXTRA_FACTORY_RESET_CALLBACK = "factory_reset_callback";
    private static final int FACTORY_RESET_NOTIFICATION_ID = 42;
    private static final Logger LOG = new Logger(FactoryResetActivity.class);
    private ICarResultReceiver mCallback;
    private Car mCar;
    private CarDrivingStateManager mCarDrivingStateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Object binder = null;

        try {
            binder = intent.getExtra(EXTRA_FACTORY_RESET_CALLBACK);
            mCallback = ICarResultReceiver.Stub.asInterface((IBinder) binder);
        } catch (Exception e) {
            LOG.w("error getting IResultReveiver from " + EXTRA_FACTORY_RESET_CALLBACK
                    + " extra (" + binder + ") on " + intent, e);
        }

        if (mCallback == null) {
            LOG.wtf("no ICarResultReceiver / " + EXTRA_FACTORY_RESET_CALLBACK
                    + " extra  on " + intent);
            finish();
            return;
        }

        // Connect to car service
        mCar = Car.createCar(this);
        mCarDrivingStateManager = (CarDrivingStateManager) mCar.getCarManager(
                Car.CAR_DRIVING_STATE_SERVICE);
        showMore();
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    private void showMore() {
        CarDrivingStateEvent state = mCarDrivingStateManager.getCurrentCarDrivingState();
        switch (state.eventValue) {
            case DRIVING_STATE_PARKED:
                showFactoryResetDialog();
                break;
            default:
                showFactoryResetToast();
        }
    }

    private void showFactoryResetDialog() {
        AlertDialog dialog = new AlertDialog.Builder(/* context= */ this,
                        com.android.internal.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.factory_reset_parked_title)
                .setMessage(R.string.factory_reset_parked_text)
                .setPositiveButton(R.string.factory_reset_later_button,
                        (d, which) -> factoryResetLater())
                .setNegativeButton(R.string.factory_reset_now_button,
                        (d, which) -> factoryResetNow())
                .setCancelable(false)
                .setOnDismissListener((d) -> finish())
                .create();

        dialog.show();
    }

    private void showFactoryResetToast() {
        showToast(R.string.factory_reset_driving_text);
        finish();
    }

    private void factoryResetNow() {
        LOG.i("Factory reset acknowledged; finishing it");

        try {
            mCallback.send(/* resultCode= */ 0, /* resultData= */ null);

            // Cancel pending intent and notification
            getSystemService(NotificationManager.class).cancel(FACTORY_RESET_NOTIFICATION_ID);
            PendingIntent.getActivity(this, FACTORY_RESET_NOTIFICATION_ID, getIntent(),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT).cancel();
        } catch (Exception e) {
            LOG.e("error factory resetting or cancelling notification / intent", e);
            return;
        } finally {
            finish();
        }
    }

    private void factoryResetLater() {
        LOG.i("Delaying factory reset.");
        showToast(R.string.factory_reset_later_text);
        finish();
    }

    private void showToast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_LONG).show();
    }
}
