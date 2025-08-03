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

package com.android.cts.verifier.car;

import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_IDLING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_MOVING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_PARKED;

import android.car.Car;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.os.Bundle;
import android.util.Slog;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.concurrent.CountDownLatch;

/**
 * A CTS Verifier test case to verify if {@link ActivityBlockingActivity} is launched correctly
 * on multiple displays respecting per-display UX restrictions.
 */
public final class UxRestrictionsTestActivity extends PassFailButtons.Activity {

    private static final String TAG = UxRestrictionsTestActivity.class.getSimpleName();

    private Car mCar;
    private TextView mStatusText;
    private TextView mDrivingStateText;
    private TextView mDistractionOptimized;
    private CarUxRestrictionsManager mCarUxRestrictionsManager;
    private CarDrivingStateManager mCarDrivingStateManager;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initCar();
        setContentView(R.layout.car_uxre_test);

        setInfoResources(R.string.car_uxre_test, R.string.car_uxre_test_desc, /* viewId= */ -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        mStatusText = findViewById(R.id.car_uxre_results);
        mDrivingStateText = findViewById(R.id.car_uxre_driving_state);
        mDistractionOptimized = findViewById(R.id.car_uxre_distraction_optimized);

        mCarUxRestrictionsManager.registerListener(this::updateUxRestrictionsText);
        updateUxRestrictionsText(mCarUxRestrictionsManager.getCurrentCarUxRestrictions());

        mCarDrivingStateManager.registerListener(new CarDrivingStateEventTestListener());
        updateDrivingStateText(mCarDrivingStateManager.getCurrentCarDrivingState());

        mStatusText.setText(getString(R.string.car_uxre_test_test_progress));
    }

    private final class CarDrivingStateEventTestListener implements
            CarDrivingStateManager.CarDrivingStateEventListener {
        private final CountDownLatch mCountDownLatch = new CountDownLatch(2);

        @Override
        public void onDrivingStateChanged(CarDrivingStateEvent event) {
            updateDrivingStateText(event);
            mCountDownLatch.countDown();
            if (mCountDownLatch.getCount() == 0) {
                mStatusText.setText(getString(R.string.car_uxre_test_test_pass));
                mStatusText.requestLayout();
                getPassButton().setEnabled(true);
            }
        }
    }

    private void updateUxRestrictionsText(CarUxRestrictions carUxRestrictions) {
        if (carUxRestrictions.isRequiresDistractionOptimization()) {
            mDistractionOptimized.setText(
                    getString(R.string.car_uxre_test_req_distraction_optimization));
        } else {
            mDistractionOptimized.setText(
                    getString(R.string.car_uxre_test_not_req_distraction_optimization));
        }
        mDistractionOptimized.requestLayout();
    }

    private void updateDrivingStateText(CarDrivingStateEvent currentCarDrivingState) {
        if (currentCarDrivingState == null) {
            Slog.d(TAG, "Current car driving state is null");
            return;
        }
        String displayText;
        switch (currentCarDrivingState.eventValue) {
            case DRIVING_STATE_PARKED:
                displayText = getString(R.string.car_uxre_test_parked);
                break;
            case DRIVING_STATE_IDLING:
                displayText = getString(R.string.car_uxre_test_idling);
                break;
            case DRIVING_STATE_MOVING:
                displayText = getString(R.string.car_uxre_test_moving);
                break;
            default:
                displayText = getString(R.string.car_uxre_test_unknown);
        }
        mDrivingStateText.setText(getString(R.string.car_uxre_test_driving_state, displayText));
        mDrivingStateText.requestLayout();
    }


    private void initCar() {
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }
        mCar = Car.createCar(this);
        mCarUxRestrictionsManager = mCar.getCarManager(CarUxRestrictionsManager.class);
        mCarDrivingStateManager = mCar.getCarManager(CarDrivingStateManager.class);
    }

    @Override
    protected void onDestroy() {
        if (mCar != null) {
            mCar.disconnect();
            mCar = null;
        }
        if (mCarUxRestrictionsManager != null) {
            mCarUxRestrictionsManager.unregisterListener();
            mCarUxRestrictionsManager = null;
        }
        if (mCarDrivingStateManager != null) {
            mCarDrivingStateManager.unregisterListener();
            mCarDrivingStateManager = null;
        }
        super.onDestroy();
    }
}
