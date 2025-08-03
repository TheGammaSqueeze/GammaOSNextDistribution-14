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

package com.android.car.telemetry;

import static android.car.telemetry.CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_SUCCEEDED;

import static com.google.common.truth.Truth.assertThat;

import android.car.telemetry.CarTelemetryManager;
import android.car.telemetry.TelemetryProto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

@RunWith(JUnit4.class)
public class MetricsConfigStoreTest {
    private static final String NAME_FOO = "Foo";
    private static final String NAME_BAR = "Bar";
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_FOO =
            TelemetryProto.MetricsConfig.newBuilder().setName(NAME_FOO).setVersion(1).build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_BAR =
            TelemetryProto.MetricsConfig.newBuilder().setName(NAME_BAR).setVersion(1).build();

    private File mTestRootDir;
    private File mTestMetricsConfigDir;
    private MetricsConfigStore mMetricsConfigStore;

    @Before
    public void setUp() throws Exception {
        mTestRootDir = Files.createTempDirectory("car_telemetry_test").toFile();
        mTestMetricsConfigDir = new File(mTestRootDir, MetricsConfigStore.METRICS_CONFIG_DIR);

        mMetricsConfigStore = new MetricsConfigStore(mTestRootDir);
        assertThat(mTestMetricsConfigDir.exists()).isTrue();
    }

    @Test
    public void testRetrieveActiveMetricsConfigs_shouldSendConfigsToListener() throws Exception {
        writeConfigToDisk(METRICS_CONFIG_FOO);
        writeConfigToDisk(METRICS_CONFIG_BAR);
        mMetricsConfigStore = new MetricsConfigStore(mTestRootDir); // reload data

        List<TelemetryProto.MetricsConfig> result = mMetricsConfigStore.getActiveMetricsConfigs();

        assertThat(result).containsExactly(METRICS_CONFIG_FOO, METRICS_CONFIG_BAR);
    }

    @Test
    public void testAddMetricsConfig_shouldWriteConfigToDisk() throws Exception {
        int status = mMetricsConfigStore.addMetricsConfig(METRICS_CONFIG_FOO);

        assertThat(status).isEqualTo(CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_SUCCEEDED);
        assertThat(readConfigFromFile(NAME_FOO)).isEqualTo(METRICS_CONFIG_FOO);
    }

    @Test
    public void testAddMetricsConfig_whenInvalidVersion_shouldNotWriteToDisk() {
        TelemetryProto.MetricsConfig invalidConfig = TelemetryProto.MetricsConfig.newBuilder()
                .setName(NAME_BAR).setVersion(-1).build();

        int status = mMetricsConfigStore.addMetricsConfig(invalidConfig);

        assertThat(status).isEqualTo(CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_VERSION_TOO_OLD);
        assertThat(new File(mTestMetricsConfigDir, NAME_BAR).exists()).isFalse();
    }

    @Test
    public void testRemoveMetricsConfig_shouldDeleteConfigFromDisk() {
        int status = mMetricsConfigStore.addMetricsConfig(METRICS_CONFIG_BAR);
        assertThat(status).isEqualTo(STATUS_ADD_METRICS_CONFIG_SUCCEEDED);

        mMetricsConfigStore.removeMetricsConfig(NAME_BAR);

        assertThat(new File(mTestMetricsConfigDir, NAME_BAR).exists()).isFalse();
    }

    @Test
    public void testRemoveMetricsConfig_whenConfigDoesNotExist_shouldDoNothing() {
        boolean success = mMetricsConfigStore.removeMetricsConfig(NAME_BAR);

        assertThat(success).isFalse();
    }

    @Test
    public void testRemoveAllMetricsConfigs_shouldDeleteAll() throws Exception {
        writeConfigToDisk(METRICS_CONFIG_FOO);
        writeConfigToDisk(METRICS_CONFIG_BAR);

        mMetricsConfigStore.removeAllMetricsConfigs();

        assertThat(mTestMetricsConfigDir.listFiles()).isEmpty();
    }

    @Test
    public void testContainsConfig() {
        mMetricsConfigStore.addMetricsConfig(METRICS_CONFIG_FOO);

        assertThat(mMetricsConfigStore.containsConfig(NAME_FOO)).isTrue();
        assertThat(mMetricsConfigStore.containsConfig(NAME_BAR)).isFalse();
    }

    private void writeConfigToDisk(TelemetryProto.MetricsConfig config) throws Exception {
        File file = new File(mTestMetricsConfigDir, config.getName());
        Files.write(file.toPath(), config.toByteArray());
        assertThat(file.exists()).isTrue();
    }

    private TelemetryProto.MetricsConfig readConfigFromFile(String fileName) throws Exception {
        byte[] bytes = Files.readAllBytes(new File(mTestMetricsConfigDir, fileName).toPath());
        return TelemetryProto.MetricsConfig.parseFrom(bytes);
    }
}
