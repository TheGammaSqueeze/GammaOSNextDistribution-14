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

package com.android.car.telemetry;

import static android.car.telemetry.CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_ALREADY_EXISTS;
import static android.car.telemetry.CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_PARSE_FAILED;
import static android.car.telemetry.CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_SUCCEEDED;
import static android.car.telemetry.CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_VERSION_TOO_OLD;
import static android.car.telemetry.CarTelemetryManager.STATUS_GET_METRICS_CONFIG_DOES_NOT_EXIST;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.car.Car;
import android.car.telemetry.CarTelemetryManager;
import android.car.telemetry.TelemetryProto;
import android.util.ArrayMap;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.car.MockedCarTestBase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Tests the public entry points for the CarTelemetryManager. It uses a real instance of
 * CarTelemetryService, however the service cannot find ScriptExecutor package so this class
 * cannot test script execution and report retrieval.
 * Tests that use a real CarTelemetryService should be in CarTelemetryManagerTest.
 * Tests that use a spied CarTelemetryService should be in CarTelemetryManagerSpyServiceTest.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarTelemetryManagerTest extends MockedCarTestBase {
    private static final byte[] INVALID_METRICS_CONFIG = "bad config".getBytes();
    private static final Executor CALLBACK_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final String CONFIG_NAME = "my_metrics_config";
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_V1 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName(CONFIG_NAME).setVersion(1).setScript("no-op").build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_V2 =
            METRICS_CONFIG_V1.toBuilder().setVersion(2).build();

    private final AddMetricsConfigCallbackImpl mAddMetricsConfigCallback =
            new AddMetricsConfigCallbackImpl();

    private CarTelemetryManager mCarTelemetryManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(getCar().isFeatureEnabled(Car.CAR_TELEMETRY_SERVICE));

        mCarTelemetryManager = (CarTelemetryManager) getCar().getCarManager(
                Car.CAR_TELEMETRY_SERVICE);
    }

    @Override
    protected CarTelemetryService createCarTelemetryService() {
        // Forces the base class implementation to instantiate real instance of
        // CarTelemetryService in ICarImpl.
        return null;
    }

    @Test
    public void testAddMetricsConfig() throws Exception {
        // invalid config, should fail
        mCarTelemetryManager.addMetricsConfig(CONFIG_NAME, INVALID_METRICS_CONFIG,
                CALLBACK_EXECUTOR, mAddMetricsConfigCallback);
        mAddMetricsConfigCallback.mSemaphore.acquire();
        assertThat(mAddMetricsConfigCallback.mAddConfigStatusMap.get(CONFIG_NAME)).isEqualTo(
                STATUS_ADD_METRICS_CONFIG_PARSE_FAILED);

        // new valid config, should succeed
        mCarTelemetryManager.addMetricsConfig(CONFIG_NAME, METRICS_CONFIG_V1.toByteArray(),
                CALLBACK_EXECUTOR, mAddMetricsConfigCallback);
        mAddMetricsConfigCallback.mSemaphore.acquire();
        assertThat(mAddMetricsConfigCallback.mAddConfigStatusMap.get(CONFIG_NAME)).isEqualTo(
                STATUS_ADD_METRICS_CONFIG_SUCCEEDED);

        // duplicate config, should fail
        mCarTelemetryManager.addMetricsConfig(CONFIG_NAME, METRICS_CONFIG_V1.toByteArray(),
                CALLBACK_EXECUTOR, mAddMetricsConfigCallback);
        mAddMetricsConfigCallback.mSemaphore.acquire();
        assertThat(mAddMetricsConfigCallback.mAddConfigStatusMap.get(CONFIG_NAME)).isEqualTo(
                STATUS_ADD_METRICS_CONFIG_ALREADY_EXISTS);

        // newer version of the config should replace older version
        mCarTelemetryManager.addMetricsConfig(CONFIG_NAME, METRICS_CONFIG_V2.toByteArray(),
                CALLBACK_EXECUTOR, mAddMetricsConfigCallback);
        mAddMetricsConfigCallback.mSemaphore.acquire();
        assertThat(mAddMetricsConfigCallback.mAddConfigStatusMap.get(CONFIG_NAME)).isEqualTo(
                STATUS_ADD_METRICS_CONFIG_SUCCEEDED);

        // older version of the config should not be accepted
        mCarTelemetryManager.addMetricsConfig(CONFIG_NAME, METRICS_CONFIG_V1.toByteArray(),
                CALLBACK_EXECUTOR, mAddMetricsConfigCallback);
        mAddMetricsConfigCallback.mSemaphore.acquire();
        assertThat(mAddMetricsConfigCallback.mAddConfigStatusMap.get(CONFIG_NAME)).isEqualTo(
                STATUS_ADD_METRICS_CONFIG_VERSION_TOO_OLD);
    }

    @Test
    public void testAddMetricsConfig_invalidFieldInConfig_shouldFail() throws Exception {
        // configure a bad publisher, read interval is not allowed to be less than 1
        TelemetryProto.Publisher.Builder badPublisher =
                TelemetryProto.Publisher.newBuilder().setMemory(
                        TelemetryProto.MemoryPublisher.newBuilder().setReadIntervalSec(-1));
        TelemetryProto.Subscriber.Builder badSubscriber =
                TelemetryProto.Subscriber.newBuilder()
                        .setHandler("handler_fn_1")
                        .setPublisher(badPublisher);
        TelemetryProto.MetricsConfig config =
                METRICS_CONFIG_V1.toBuilder().addSubscribers(badSubscriber).build();

        mCarTelemetryManager.addMetricsConfig(
                CONFIG_NAME, config.toByteArray(), CALLBACK_EXECUTOR, mAddMetricsConfigCallback);

        mAddMetricsConfigCallback.mSemaphore.acquire();
        assertThat(mAddMetricsConfigCallback.mAddConfigStatusMap.get(CONFIG_NAME)).isEqualTo(
                STATUS_ADD_METRICS_CONFIG_PARSE_FAILED);
    }

    @Test
    public void testSetClearListener() {
        CarTelemetryManager.ReportReadyListener listener = metricsConfigName -> { };

        // test clearReportReadyListener, should not error
        mCarTelemetryManager.setReportReadyListener(CALLBACK_EXECUTOR, listener);

        // setListener multiple times should fail
        assertThrows(IllegalStateException.class,
                () -> mCarTelemetryManager.setReportReadyListener(CALLBACK_EXECUTOR, listener));

        // test clearReportReadyListener, should not error
        mCarTelemetryManager.clearReportReadyListener();
        mCarTelemetryManager.setReportReadyListener(CALLBACK_EXECUTOR, listener);
    }

    @Test
    public void testGetFinishedReport_noSuchConfig() throws Exception {
        Semaphore semaphore = new Semaphore(0);
        CarTelemetryManager.MetricsReportCallback callback = mock(
                CarTelemetryManager.MetricsReportCallback.class);
        doAnswer((invocation) -> {
            semaphore.release();
            return null;
        }).when(callback).onResult(any(), any(), any(), anyInt());

        mCarTelemetryManager.getFinishedReport(CONFIG_NAME, CALLBACK_EXECUTOR, callback);

        semaphore.acquire();
        verify(callback).onResult(
                eq(CONFIG_NAME),
                isNull(),
                isNull(),
                eq(STATUS_GET_METRICS_CONFIG_DOES_NOT_EXIST));
    }

    private static final class AddMetricsConfigCallbackImpl
            implements CarTelemetryManager.AddMetricsConfigCallback {

        private Semaphore mSemaphore = new Semaphore(0);
        private Map<String, Integer> mAddConfigStatusMap = new ArrayMap<>();

        @Override
        public void onAddMetricsConfigStatus(@NonNull String metricsConfigName, int statusCode) {
            mAddConfigStatusMap.put(metricsConfigName, statusCode);
            mSemaphore.release();
        }
    }
}
