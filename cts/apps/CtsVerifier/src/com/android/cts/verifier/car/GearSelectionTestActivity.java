/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.cts.verifier.car;

import android.car.Car;
import android.car.VehicleGear;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** A CTS Verifier test case to verify GEAR_SELECTION is implemented correctly.*/
public final class GearSelectionTestActivity extends PassFailButtons.Activity {
    private static final String TAG = GearSelectionTestActivity.class.getSimpleName();

    // Need to finish the test in 10 minutes.
    private static final long TEST_TIMEOUT_MINUTES = 10;

    private List<Integer> mSupportedGears;
    private TextView mExpectedGearSelectionTextView;
    private TextView mCurrentGearSelectionTextView;
    private CarPropertyManager mCarPropertyManager;
    private ExecutorService mExecutor;
    private GearSelectionCallback mGearSelectionCallback = new GearSelectionCallback();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup the UI.
        setContentView(R.layout.gear_selection_test);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.gear_selection_test, R.string.gear_selection_test_desc, -1);
        getPassButton().setEnabled(false);

        mExpectedGearSelectionTextView = (TextView) findViewById(R.id.expected_gear_selection);
        mCurrentGearSelectionTextView = (TextView) findViewById(R.id.current_gear_selection);
        mExecutor = Executors.newSingleThreadExecutor();
        setUpTest();
    }

    private void setUpTest() {
        mCarPropertyManager =
                (CarPropertyManager) Car.createCar(this).getCarManager(Car.PROPERTY_SERVICE);
        if (mCarPropertyManager == null) {
            Log.e(TAG, "Failed to get CarPropertyManager");
            mExpectedGearSelectionTextView.setText("CONNECTING ERROR");
            return;
        }

        //Verify property config
        CarPropertyConfig<?> gearConfig = mCarPropertyManager.getCarPropertyConfig(
                VehiclePropertyIds.GEAR_SELECTION);
        if (gearConfig == null || gearConfig.getConfigArray().size() == 0) {
            Log.e(TAG, "No gears specified in the config array of GEAR_SELECTION property");
            mExpectedGearSelectionTextView.setText("GEAR CONFIG ERROR");
            return;
        }

        //Register the callback for testing
        mSupportedGears = gearConfig.getConfigArray();
        Log.i(TAG, "New Expected Gear: " + VehicleGear.toString(mSupportedGears.get(0)));
        mExpectedGearSelectionTextView.setText(VehicleGear.toString(mSupportedGears.get(0)));
        mGearSelectionCallback = new GearSelectionCallback();
        mGearSelectionCallback.setSupportedGearCounter(mSupportedGears.size());
        if (!mCarPropertyManager.registerCallback(mGearSelectionCallback,
                VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)) {
            Log.e(TAG,
                    "Failed to register callback for GEAR_SELECTION with CarPropertyManager");
            mExpectedGearSelectionTextView.setText("CONNECTING ERROR");
            return;
        }

        //Unregister if test is timeout
        mExecutor.execute(() -> {
            try {
                mGearSelectionCallback.unregisterIfTimeout();
            } catch (InterruptedException e) {
                Log.e(TAG, "Test is interrupted: " + e);
                mExpectedGearSelectionTextView.setText("INTERRUPTED");
                Thread.currentThread().interrupt();
            }
        });
    }

    private final class GearSelectionCallback implements
            CarPropertyManager.CarPropertyEventCallback {
        private CountDownLatch mCountDownLatch;
        private int mVerifyingIndex;
        @Override
        public void onChangeEvent(CarPropertyValue value) {
            if(value.getStatus() != CarPropertyValue.STATUS_AVAILABLE) {
                Log.e(TAG, "New CarPropertyValue's status is not available - propId: " +
                    value.getPropertyId() + " status: " + value.getStatus());
                return;
            }
            Integer newGearSelection = (Integer) value.getValue();
            mCurrentGearSelectionTextView.setText(VehicleGear.toString(newGearSelection));
            Log.i(TAG, "New Gear Selection: " + VehicleGear.toString(newGearSelection));

            // Check to see if new gear matches the expected gear.
            if (newGearSelection.equals(mSupportedGears.get(mVerifyingIndex))) {
                mCountDownLatch.countDown();
                mVerifyingIndex++;
                Log.i(TAG, "Matched gear: " + VehicleGear.toString(newGearSelection));
                if (mCountDownLatch.getCount() != 0) {
                    // Test is not finished so update the expected gear.
                    mExpectedGearSelectionTextView.setText(
                            VehicleGear.toString(mSupportedGears.get(mVerifyingIndex)));
                    Log.i(TAG, "New Expected Gear: "
                            + VehicleGear.toString(mSupportedGears.get(mVerifyingIndex)));
                } else {
                    // Test is finished, unregister the callback
                    mCarPropertyManager.unregisterCallback(mGearSelectionCallback);
                    mExpectedGearSelectionTextView.setText("Finished");
                    getPassButton().setEnabled(true);
                    Log.i(TAG, "Finished Test");
                }
            }
        }

        @Override
        public void onErrorEvent(int propId, int zone) {
            Log.e(TAG, "propId: " + propId + " zone: " + zone);
        }

        public void setSupportedGearCounter(int counter) {
            mCountDownLatch = new CountDownLatch(counter);
        }

        public void unregisterIfTimeout() throws InterruptedException {
            if (!mCountDownLatch.await(TEST_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                Log.e(TAG, "Failed to complete tests in 10 minutes");
                runOnUiThread(() -> mExpectedGearSelectionTextView.setText("Failed(Timeout)"));
                mCarPropertyManager.unregisterCallback(mGearSelectionCallback);
            }
        }
    }
}
