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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.telemetry.CarTelemetryManager;
import android.car.telemetry.TelemetryProto;
import android.os.PersistableBundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.Semaphore;

@RequiresDevice
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant Apps cannot get car related permissions")
public final class CarTelemetryManagerTest extends AbstractCarTestCase {

    private static final String TAG = CarTelemetryManagerTest.class.getSimpleName();

    /** Test MetricsConfig that does nothing. */
    private static final TelemetryProto.MetricsConfig TEST_CONFIG =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("test_config")
                    .setVersion(1)
                    .setScript("no-op")
                    .build();
    private static final String TEST_CONFIG_NAME = TEST_CONFIG.getName();

    /** MetricsConfig with simple script that listens for parking brake change. */
    private static final String PARKING_BRAKE_CHANGE_SCRIPT = new StringBuilder()
            .append("function onParkingBrakeChange(published_data, saved_state)\n")
            .append("    result = {data = \"Hello World!\"}\n")
            .append("    on_script_finished(result)\n")
            .append("end\n")
            .toString();
    private static final TelemetryProto.Publisher PARKING_BRAKE_PROPERTY_PUBLISHER =
            TelemetryProto.Publisher.newBuilder()
                    .setVehicleProperty(
                            TelemetryProto.VehiclePropertyPublisher.newBuilder()
                                    .setVehiclePropertyId(VehiclePropertyIds.PARKING_BRAKE_ON)
                                    .setReadRate(0f))
                    .build();
    private static final TelemetryProto.Subscriber PARKING_BRAKE_PROPERTY_SUBSCRIBER =
            TelemetryProto.Subscriber.newBuilder()
                    .setHandler("onParkingBrakeChange")
                    .setPublisher(PARKING_BRAKE_PROPERTY_PUBLISHER)
                    .setPriority(0)
                    .build();
    private static final TelemetryProto.MetricsConfig PARKING_BRAKE_CONFIG =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("parking_brake_config")
                    .setVersion(1)
                    .setScript(PARKING_BRAKE_CHANGE_SCRIPT)
                    .addSubscribers(PARKING_BRAKE_PROPERTY_SUBSCRIBER)
                    .build();
    private static final String PARKING_BRAKE_CONFIG_NAME = PARKING_BRAKE_CONFIG.getName();

    /**
     * MetricsConfig with a bad script that listens for parking brake change, will produce error.
     */
    private static final TelemetryProto.MetricsConfig ERROR_CONFIG =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("error_config")
                    .setVersion(1)
                    .setScript("a bad script that should produce a runtime error")
                    .addSubscribers(PARKING_BRAKE_PROPERTY_SUBSCRIBER)
                    .build();
    private static final String ERROR_CONFIG_NAME = ERROR_CONFIG.getName();

    private CarTelemetryManager mCarTelemetryManager;
    private UiAutomation mUiAutomation;

    @Before
    public void setUp() throws Exception {
        assumeTrue("CarTelemetryService is not enabled, skipping test",
                getCar().isFeatureEnabled(Car.CAR_TELEMETRY_SERVICE));

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mUiAutomation = instrumentation.getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(
                "android.car.permission.USE_CAR_TELEMETRY_SERVICE");
        mCarTelemetryManager = (CarTelemetryManager) Car.createCar(
                instrumentation.getContext()).getCarManager(Car.CAR_TELEMETRY_SERVICE);
        assertThat(mCarTelemetryManager).isNotNull();

        // start from a clean state
        mCarTelemetryManager.clearReportReadyListener();
        mCarTelemetryManager.removeAllMetricsConfigs();
    }

    @After
    public void tearDown() throws Exception {
        // end in a clean state
        mCarTelemetryManager.clearReportReadyListener();
        mCarTelemetryManager.removeAllMetricsConfigs();
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @ApiTest(apis = {
            "android.car.telemetry.CarTelemetryManager#"
                    + "addMetricsConfig(String, byte[], Executor, AddMetricsConfigCallback)",
            "android.car.telemetry.CarTelemetryManager#removeMetricsConfig(String)",
            "android.car.telemetry.CarTelemetryManager#STATUS_ADD_METRICS_CONFIG_SUCCEEDED",
            "android.car.telemetry.CarTelemetryManager#STATUS_ADD_METRICS_CONFIG_ALREADY_EXISTS",
    })
    public void testAddRemoveMetricsConfig() throws Exception {
        // Test: add new MetricsConfig. Expect: SUCCESS
        AddMetricsConfigCallbackImpl callback = new AddMetricsConfigCallbackImpl();
        mCarTelemetryManager.addMetricsConfig(TEST_CONFIG_NAME, TEST_CONFIG.toByteArray(),
                Runnable::run, callback);
        callback.mSemaphore.acquire();
        assertThat(callback.mAddConfigStatusMap.get(TEST_CONFIG_NAME))
                .isEqualTo(CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_SUCCEEDED);

        // Test: add a duplicate MetricsConfig. Expect: ALREADY_EXISTS status code
        mCarTelemetryManager.addMetricsConfig(TEST_CONFIG_NAME, TEST_CONFIG.toByteArray(),
                Runnable::run, callback);
        callback.mSemaphore.acquire();
        assertThat(callback.mAddConfigStatusMap.get(TEST_CONFIG_NAME))
                .isEqualTo(CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_ALREADY_EXISTS);

        // Test: remove a MetricsConfig. Expect: the next add should return SUCCESS
        mCarTelemetryManager.removeMetricsConfig(TEST_CONFIG_NAME);
        mCarTelemetryManager.addMetricsConfig(TEST_CONFIG_NAME, TEST_CONFIG.toByteArray(),
                Runnable::run, callback);
        callback.mSemaphore.acquire();
        assertThat(callback.mAddConfigStatusMap.get(TEST_CONFIG_NAME))
                .isEqualTo(CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_SUCCEEDED);
    }

    @Test
    @ApiTest(apis = {
            "android.car.telemetry.CarTelemetryManager#"
                    + "addMetricsConfig(String, byte[], Executor, AddMetricsConfigCallback)",
            "android.car.telemetry.CarTelemetryManager#"
                    + "setReportReadyListener(Executor, ReportReadyListener)",
            "android.car.telemetry.CarTelemetryManager#"
                    + "getFinishedReport(String, Executor, MetricsReportCallback)",
            "android.car.telemetry.CarTelemetryManager#STATUS_ADD_METRICS_CONFIG_SUCCEEDED",
            "android.car.telemetry.CarTelemetryManager#STATUS_GET_METRICS_CONFIG_FINISHED",
    })
    public void testEndToEndScriptExecution_getFinishedReport() throws Exception {
        // set listener to receive report ready notification
        Semaphore reportReadySemaphore = new Semaphore(0);
        mCarTelemetryManager.setReportReadyListener(Runnable::run, metricsConfigName -> {
            if (metricsConfigName.equals(PARKING_BRAKE_CONFIG_NAME)) {
                reportReadySemaphore.release();
            }
        });

        // add metrics config and assert success
        AddMetricsConfigCallbackImpl callback = new AddMetricsConfigCallbackImpl();
        mCarTelemetryManager.addMetricsConfig(PARKING_BRAKE_CONFIG_NAME,
                PARKING_BRAKE_CONFIG.toByteArray(), Runnable::run, callback);
        callback.mSemaphore.acquire();
        assertThat(callback.mAddConfigStatusMap.get(PARKING_BRAKE_CONFIG_NAME))
                .isEqualTo(CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_SUCCEEDED);

        // inject event to set parking brake on, triggering script execution
        executeShellCommand("cmd car_service inject-vhal-event %d %s",
                VehiclePropertyIds.PARKING_BRAKE_ON, true);

        // wait for report ready notification, then call getFinishedReport()
        reportReadySemaphore.acquire();
        FinishedReportListenerImpl reportListener = new FinishedReportListenerImpl();
        mCarTelemetryManager.getFinishedReport(
                PARKING_BRAKE_CONFIG_NAME, Runnable::run, reportListener);
        reportListener.mSemaphore.acquire();
        assertThat(reportListener.mReportMap.get(PARKING_BRAKE_CONFIG_NAME)).isNotNull();
        assertThat(reportListener.mStatusMap.get(PARKING_BRAKE_CONFIG_NAME))
                .isEqualTo(CarTelemetryManager.STATUS_GET_METRICS_CONFIG_FINISHED);
    }

    @Test
    @ApiTest(apis = {
            "android.car.telemetry.CarTelemetryManager#"
                    + "addMetricsConfig(String, byte[], Executor, AddMetricsConfigCallback)",
            "android.car.telemetry.CarTelemetryManager#"
                    + "setReportReadyListener(Executor, ReportReadyListener)",
            "android.car.telemetry.CarTelemetryManager#"
                    + "getAllFinishedReports(Executor, MetricsReportCallback)",
            "android.car.telemetry.CarTelemetryManager#STATUS_ADD_METRICS_CONFIG_SUCCEEDED",
            "android.car.telemetry.CarTelemetryManager#STATUS_GET_METRICS_CONFIG_FINISHED",
            "android.car.telemetry.CarTelemetryManager#STATUS_GET_METRICS_CONFIG_RUNTIME_ERROR",
    })
    public void testEndToEndScriptExecution_getAllFinishedReports() throws Exception {
        // set listener to receive report ready notification
        Semaphore reportReadySemaphore = new Semaphore(0);
        mCarTelemetryManager.setReportReadyListener(
                Runnable::run, metricsConfigName -> reportReadySemaphore.release());

        // add 2 metrics configs, one will produce a final report and one will error
        AddMetricsConfigCallbackImpl callback = new AddMetricsConfigCallbackImpl();
        mCarTelemetryManager.addMetricsConfig(PARKING_BRAKE_CONFIG_NAME,
                PARKING_BRAKE_CONFIG.toByteArray(), Runnable::run, callback);
        mCarTelemetryManager.addMetricsConfig(ERROR_CONFIG_NAME, ERROR_CONFIG.toByteArray(),
                Runnable::run, callback);
        callback.mSemaphore.acquire(2);
        assertThat(callback.mAddConfigStatusMap.get(PARKING_BRAKE_CONFIG_NAME))
                .isEqualTo(CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_SUCCEEDED);
        assertThat(callback.mAddConfigStatusMap.get(ERROR_CONFIG_NAME))
                .isEqualTo(CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_SUCCEEDED);

        // inject event to set parking brake on, triggering both scripts
        executeShellCommand("cmd car_service inject-vhal-event %d %s",
                VehiclePropertyIds.PARKING_BRAKE_ON, true);

        // wait for report ready notification, then call getFinishedReport()
        reportReadySemaphore.acquire(2);

        // get all reports
        FinishedReportListenerImpl reportListener = new FinishedReportListenerImpl();
        mCarTelemetryManager.getAllFinishedReports(Runnable::run, reportListener);

        // semaphore should be released 2 times, one for each report
        reportListener.mSemaphore.acquire(2);
        assertThat(reportListener.mReportMap.get(PARKING_BRAKE_CONFIG_NAME)).isNotNull();
        assertThat(reportListener.mStatusMap.get(PARKING_BRAKE_CONFIG_NAME))
                .isEqualTo(CarTelemetryManager.STATUS_GET_METRICS_CONFIG_FINISHED);
        assertThat(reportListener.mErrorMap.get(ERROR_CONFIG_NAME)).isNotNull();
        assertThat(reportListener.mStatusMap.get(ERROR_CONFIG_NAME))
                .isEqualTo(CarTelemetryManager.STATUS_GET_METRICS_CONFIG_RUNTIME_ERROR);
    }

    @Test
    @ApiTest(apis = {
            "android.car.telemetry.CarTelemetryManager#"
                    + "setReportReadyListener(Executor, ReportReadyListener)",
            "android.car.telemetry.CarTelemetryManager#clearReportReadyListener",
    })
    public void testSetClearReportReadyListener() {
        CarTelemetryManager.ReportReadyListener listener = metricsConfigName -> { };

        // test clearReportReadyListener, should not error
        mCarTelemetryManager.setReportReadyListener(Runnable::run, listener);

        // setListener multiple times should fail
        assertThrows(IllegalStateException.class,
                () -> mCarTelemetryManager.setReportReadyListener(Runnable::run, listener));

        // test clearReportReadyListener, a successful "clear" should allow for a successful "set"
        mCarTelemetryManager.clearReportReadyListener();
        mCarTelemetryManager.setReportReadyListener(Runnable::run, listener);
    }

    private final class AddMetricsConfigCallbackImpl
            implements CarTelemetryManager.AddMetricsConfigCallback {

        private final Semaphore mSemaphore = new Semaphore(0);
        private final Map<String, Integer> mAddConfigStatusMap = new ArrayMap<>();

        @Override
        public void onAddMetricsConfigStatus(@NonNull String metricsConfigName, int statusCode) {
            mAddConfigStatusMap.put(metricsConfigName, statusCode);
            mSemaphore.release();
        }
    }

    private final class FinishedReportListenerImpl
            implements CarTelemetryManager.MetricsReportCallback {

        private final Semaphore mSemaphore = new Semaphore(0);
        private final Map<String, byte[]> mErrorMap = new ArrayMap<>();
        private final Map<String, PersistableBundle> mReportMap = new ArrayMap<>();
        private final Map<String, Integer> mStatusMap = new ArrayMap<>();

        @Override
        public void onResult(@NonNull String metricsConfigName, @Nullable PersistableBundle report,
                @Nullable byte[] error, int status) {
            mReportMap.put(metricsConfigName, report);
            mErrorMap.put(metricsConfigName, error);
            mStatusMap.put(metricsConfigName, status);
            mSemaphore.release();
        }
    }
}
