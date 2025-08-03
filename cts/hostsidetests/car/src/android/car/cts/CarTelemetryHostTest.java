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

import static org.junit.Assume.assumeTrue;

import android.car.telemetry.TelemetryProto;

import com.android.compatibility.common.util.PollingCheck;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CarTelemetryHostTest extends CarHostJUnit4TestCase {

    // Publisher/subscriber listening for gear change property
    private static final int GEAR_SELECTION_PROPERTY_ID = 287310850;
    private static final TelemetryProto.Publisher GEAR_CHANGE_PUBLISHER =
            TelemetryProto.Publisher.newBuilder()
                    .setVehicleProperty(
                            TelemetryProto.VehiclePropertyPublisher.newBuilder()
                                    .setVehiclePropertyId(GEAR_SELECTION_PROPERTY_ID)
                                    .setReadRate(0f))
                    .build();
    private static final TelemetryProto.Subscriber GEAR_CHANGE_SUBSCRIBER =
            TelemetryProto.Subscriber.newBuilder()
                    .setHandler("onGearChange")
                    .setPublisher(GEAR_CHANGE_PUBLISHER)
                    .setPriority(0)
                    .build();

    // only produces interim result
    private static final String LUA_SCRIPT_INTERIM = new StringBuilder()
            .append("function onGearChange(published_data, saved_state)\n")
            .append("    saved_state['interim_result_exists'] = true\n")
            .append("    on_success(saved_state)\n")
            .append("end\n")
            .toString();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_1 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("test_config_1")
                    .setVersion(1)
                    .setScript(LUA_SCRIPT_INTERIM)
                    .addSubscribers(GEAR_CHANGE_SUBSCRIBER)
                    .build();
    private static final String CONFIG_NAME_1 = METRICS_CONFIG_1.getName();

    // only produces final result
    private static final String LUA_SCRIPT_FINAL = new StringBuilder()
            .append("function onGearChange(published_data, saved_state)\n")
            .append("    saved_state['final_result_exists'] = true\n")
            .append("    on_script_finished(saved_state)\n")
            .append("end\n")
            .toString();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_2 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("test_config_2")
                    .setVersion(1)
                    .setScript(LUA_SCRIPT_FINAL)
                    .addSubscribers(GEAR_CHANGE_SUBSCRIBER)
                    .build();
    private static final String CONFIG_NAME_2 = METRICS_CONFIG_2.getName();

    private static final long SCRIPT_EXECUTION_TIMEOUT_MILLIS = 30_000L;
    private static final String FINAL_RESULT_DIR = "/data/system/car/telemetry/final";
    private static final String INTERIM_RESULT_DIR = "/data/system/car/telemetry/interim";

    @Before
    public void setUp() throws Exception {
        getDevice().enableAdbRoot();
        String output = executeCommand("cmd car_service enable-feature car_telemetry_service");
        if (!output.startsWith("Already enabled")) {
            // revert the feature to its original setting
            executeCommand("cmd car_service disable-feature car_telemetry_service");
            assumeTrue("CarTelemetryService is not enabled, skipping test", false);
        }
    }

    @After
    public void tearDown() throws Exception {
        executeCommand("cmd car_service telemetry remove %s", CONFIG_NAME_1);
        executeCommand("rm %s/%s", FINAL_RESULT_DIR, CONFIG_NAME_2);
    }

    @Test
    public void testSavingResultsAcrossReboot() throws Exception {
        // create temp files, which will be the argument to the telemetry car shell cmd
        File config1 = createMetricsConfigTempFile(METRICS_CONFIG_1);
        File config2 = createMetricsConfigTempFile(METRICS_CONFIG_2);
        config1.deleteOnExit();
        config2.deleteOnExit();

        // outputs that should be produced by the Lua scripts above
        String scriptOutput1 = "interim_result_exists";
        String scriptOutput2 = "final_result_exists";

        // add metrics configs using car shell command
        getDevice().executeShellV2Command("cmd car_service telemetry add " + CONFIG_NAME_1,
                config1);
        getDevice().executeShellV2Command("cmd car_service telemetry add " + CONFIG_NAME_2,
                config2);

        // inject gear change event, should trigger script execution for both scripts
        executeCommand("cmd car_service inject-vhal-event %d %d",
                GEAR_SELECTION_PROPERTY_ID, 2);

        // block until script execution finishes
        PollingCheck.waitFor(SCRIPT_EXECUTION_TIMEOUT_MILLIS, () -> {
            String dump = dumpTelemetryService();
            return dump.contains(scriptOutput1) && dump.contains(scriptOutput2);
        });

        // verify that both results are stored in memory, not in disk
        assertThat(executeCommand("ls %s", INTERIM_RESULT_DIR))
                .doesNotContain(CONFIG_NAME_1);
        assertThat(executeCommand("ls %s", FINAL_RESULT_DIR))
                .doesNotContain(CONFIG_NAME_2);

        // trigger reboot, which should save results to disk
        getDevice().reboot();
        waitForCarServiceReady();

        // verify data is saved across reboot
        assertThat(dumpTelemetryService()).contains(scriptOutput1);
        String result = executeCommand(
                "cmd car_service telemetry get-result --config %s --result-count 1"
                + " --timeout 20 --print-results", CONFIG_NAME_2);
        assertThat(result).contains(scriptOutput2);
    }

    private String dumpTelemetryService() throws Exception {
        return executeCommand("dumpsys car_service --services CarTelemetryService");
    }

    private File createMetricsConfigTempFile(TelemetryProto.MetricsConfig metricsConfig)
            throws Exception {
        File tempFile = File.createTempFile(metricsConfig.getName(), ".bin");
        FileOutputStream os = new FileOutputStream(tempFile);
        os.write(metricsConfig.toByteArray());
        os.flush();
        os.close();
        return tempFile;
    }
}
