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

package android.car.cts.permissiontest.telemetry;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.Car;
import android.car.telemetry.CarTelemetryManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import android.car.cts.permissiontest.AbstractCarManagerPermissionTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * This class contains security permission tests for {@link CarTelemetryManager}'s system APIs.
 */
@RunWith(AndroidJUnit4.class)
public class CarTelemetryManagerPermissionTest extends AbstractCarManagerPermissionTest {
    private static final String METRICS_CONFIG_NAME = "name";

    private final byte[] mMetricsConfigBytes = "metricsConfig".getBytes();
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private CarTelemetryManager mCarTelemetryManager;

    @Before
    public void setUp() {
        super.connectCar();
        mCarTelemetryManager = (CarTelemetryManager) mCar.getCarManager(Car.CAR_TELEMETRY_SERVICE);
    }

    @Test
    public void testAddMetricsConfig() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarTelemetryManager.addMetricsConfig("name", mMetricsConfigBytes, mExecutor,
                        (metricsConfigName, statusCode) -> { }));

        assertThat(e.getMessage()).contains(Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE);
    }

    @Test
    public void testRemoveMetricsConfig() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarTelemetryManager.removeMetricsConfig(METRICS_CONFIG_NAME));

        assertThat(e.getMessage()).contains(Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE);
    }

    @Test
    public void testRemoveAllMetricsConfigs() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarTelemetryManager.removeAllMetricsConfigs());

        assertThat(e.getMessage()).contains(Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE);
    }

    @Test
    public void testGetFinishedReport() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarTelemetryManager.getFinishedReport(METRICS_CONFIG_NAME, mExecutor,
                        (metricsConfigName, report, telemetryError, status) -> { }));

        assertThat(e.getMessage()).contains(Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE);
    }

    @Test
    public void testGetAllFinishedReports() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarTelemetryManager.getAllFinishedReports(mExecutor,
                        (metricsConfigName, report, telemetryError, status) -> { }));

        assertThat(e.getMessage()).contains(Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE);
    }

    @Test
    public void testSetReportReadyListener() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarTelemetryManager.setReportReadyListener(
                        mExecutor, (metricsConfigName) -> { }));

        assertThat(e.getMessage()).contains(Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE);
    }

    @Test
    public void testClearReportReadyListener() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarTelemetryManager.clearReportReadyListener());

        assertThat(e.getMessage()).contains(Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE);
    }
}
