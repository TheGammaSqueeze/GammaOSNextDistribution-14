/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.car.vehiclehal.test;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.Car;
import android.car.diagnostic.CarDiagnosticEvent;
import android.car.diagnostic.CarDiagnosticManager;
import android.car.diagnostic.FloatSensorIndex;
import android.car.diagnostic.IntegerSensorIndex;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.CarSensorManager;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.util.Log;
import android.util.SparseIntArray;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class CarDiagnosticTest extends E2eCarTestBase {

    private static final String TAG = Utils.concatTag(CarDiagnosticTest.class);

    private static final Duration TEST_TIME_OUT = Duration.ofSeconds(10);

    private static final String CAR_DIAGNOSTIC_TEST_JSON = "car_diagnostic_test.json";

    private static final SparseIntArray DIAGNOSTIC_PROPERTY_MAP;

    static {
        DIAGNOSTIC_PROPERTY_MAP = new SparseIntArray();
        DIAGNOSTIC_PROPERTY_MAP.append(
                CarDiagnosticManager.FRAME_TYPE_LIVE, VehicleProperty.OBD2_LIVE_FRAME);
        DIAGNOSTIC_PROPERTY_MAP.append(
                CarDiagnosticManager.FRAME_TYPE_FREEZE, VehicleProperty.OBD2_FREEZE_FRAME);
    }

    private boolean mPropertySaved;

    private static class CarDiagnosticListener implements
            CarDiagnosticManager.OnDiagnosticEventListener {

        private VhalEventVerifier mVerifier;

        CarDiagnosticListener(VhalEventVerifier verifier) {
            mVerifier = verifier;
        }

        @Override
        public void onDiagnosticEvent(CarDiagnosticEvent event) {
            mVerifier.verify(fromCarDiagnosticEvent(event));
        }
    }

    private static CarPropertyValue<Object[]> fromCarDiagnosticEvent(
            final CarDiagnosticEvent event) {
        int prop = DIAGNOSTIC_PROPERTY_MAP.get(event.frameType);
        List<Object> valuesList = new ArrayList<>();

        for (int i = 0; i <= IntegerSensorIndex.LAST_SYSTEM; i++) {
            valuesList.add(event.getSystemIntegerSensor(i, 0));
        }
        for (int i = 0; i <= FloatSensorIndex.LAST_SYSTEM; i++) {
            valuesList.add(event.getSystemFloatSensor(i, 0));
        }
        valuesList.add(event.dtc);
        return new CarPropertyValue<>(prop, 0, valuesList.toArray());
    }

    @Before
    public void setUp() throws Exception {
        checkRefAidlVHal();
        saveProperty(VehicleProperty.OBD2_LIVE_FRAME, 0);
        mPropertySaved = true;
    }

    @After
    public void tearDown() throws Exception {
        if (mPropertySaved) {
            restoreProperty(VehicleProperty.OBD2_LIVE_FRAME, 0);
        }
    }

    @Test
    public void testDiagnosticEvents() throws Exception {
        Log.d(TAG, "Prepare Diagnostic test data");
        List<CarPropertyValue> expectedEvents = getExpectedEvents(CAR_DIAGNOSTIC_TEST_JSON);
        VhalEventVerifier verifier = new VhalEventVerifier(expectedEvents);

        CarDiagnosticManager diagnosticManager = (CarDiagnosticManager) mCar.getCarManager(
                Car.DIAGNOSTIC_SERVICE);

        CarDiagnosticListener listener = new CarDiagnosticListener(verifier);

        assertWithMessage("Failed to register for OBD2 diagnostic live frame.").that(
                diagnosticManager.registerListener(listener,
                        CarDiagnosticManager.FRAME_TYPE_LIVE,
                        CarSensorManager.SENSOR_RATE_NORMAL)).isTrue();
        assertWithMessage("Failed to register for OBD2 diagnostic freeze frame.").that(
                diagnosticManager.registerListener(listener,
                        CarDiagnosticManager.FRAME_TYPE_FREEZE,
                        CarSensorManager.SENSOR_RATE_NORMAL)).isTrue();

        Log.d(TAG, "Send command to VHAL to start generation");

        VhalEventGenerator diagnosticGenerator = new JsonVhalEventGenerator(mCarTestManager,
                VHAL_DUMP_TIMEOUT_MS).setJson(getTestFileContent(CAR_DIAGNOSTIC_TEST_JSON));
        diagnosticGenerator.start();

        Log.d(TAG, "Receiving and verifying VHAL events");
        boolean result = verifier.waitForEnd(TEST_TIME_OUT.toMillis());

        Log.d(TAG, "Send command to VHAL to stop generation");
        diagnosticGenerator.stop();
        diagnosticManager.unregisterListener(listener);

        assertWithMessage("Detected mismatched events (ignore timestamp and status): "
                + verifier.getResultString()).that(result).isTrue();
    }
}
