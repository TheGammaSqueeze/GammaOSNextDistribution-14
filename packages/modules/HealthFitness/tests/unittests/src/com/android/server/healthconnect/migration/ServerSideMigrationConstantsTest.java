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

package com.android.server.healthconnect.migration;

import static com.android.server.healthconnect.HealthConnectDeviceConfigManager.COUNT_MIGRATION_STATE_ALLOWED_FLAG;
import static com.android.server.healthconnect.HealthConnectDeviceConfigManager.COUNT_MIGRATION_STATE_IN_PROGRESS_FLAG;
import static com.android.server.healthconnect.HealthConnectDeviceConfigManager.ENABLE_PAUSE_STATE_CHANGE_JOBS_FLAG;
import static com.android.server.healthconnect.HealthConnectDeviceConfigManager.EXECUTION_TIME_BUFFER_MINUTES_FLAG;
import static com.android.server.healthconnect.HealthConnectDeviceConfigManager.IDLE_STATE_TIMEOUT_DAYS_FLAG;
import static com.android.server.healthconnect.HealthConnectDeviceConfigManager.IN_PROGRESS_STATE_TIMEOUT_HOURS_FLAG;
import static com.android.server.healthconnect.HealthConnectDeviceConfigManager.MAX_START_MIGRATION_CALLS_ALLOWED_FLAG;
import static com.android.server.healthconnect.HealthConnectDeviceConfigManager.MIGRATION_COMPLETION_JOB_RUN_INTERVAL_DAYS_FLAG;
import static com.android.server.healthconnect.HealthConnectDeviceConfigManager.MIGRATION_PAUSE_JOB_RUN_INTERVAL_HOURS_FLAG;
import static com.android.server.healthconnect.HealthConnectDeviceConfigManager.NON_IDLE_STATE_TIMEOUT_DAYS_FLAG;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.UiAutomation;
import android.content.Context;
import android.provider.DeviceConfig;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.healthconnect.HealthConnectDeviceConfigManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Unit tests for server-side configuration of {@link MigrationConstants} */
@RunWith(AndroidJUnit4.class)
public class ServerSideMigrationConstantsTest {
    @Mock DeviceConfig.Properties mProperties;

    private static final int VALUE_TO_SET = 150;
    private static final UiAutomation UI_AUTOMATION =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private static HealthConnectDeviceConfigManager sHealthConnectDeviceConfigManager;

    @Before
    public void setUp() {
        UI_AUTOMATION.adoptShellPermissionIdentity(Manifest.permission.READ_DEVICE_CONFIG);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        HealthConnectDeviceConfigManager.initializeInstance(context);
        sHealthConnectDeviceConfigManager =
                HealthConnectDeviceConfigManager.getInitialisedInstance();
    }

    @After
    public void tearDown() {
        UI_AUTOMATION.dropShellPermissionIdentity();
    }

    @Test
    public void testCountMigrationStateInProgress_changeValueInDeviceConfig_onPropertiesChanged() {
        Map<String, String> keyValueMap = new HashMap<String, String>();
        keyValueMap.put(COUNT_MIGRATION_STATE_IN_PROGRESS_FLAG, Integer.toString(VALUE_TO_SET));
        mProperties =
                new DeviceConfig.Properties(DeviceConfig.NAMESPACE_HEALTH_FITNESS, keyValueMap);

        sHealthConnectDeviceConfigManager.onPropertiesChanged(mProperties);

        assertThat(sHealthConnectDeviceConfigManager.getMigrationStateInProgressCount())
                .isEqualTo(VALUE_TO_SET);
    }

    @Test
    public void testCountMigrationStateAllowed_changeValueInDeviceConfig_onPropertiesChanged() {
        Map<String, String> keyValueMap = new HashMap<String, String>();
        keyValueMap.put(COUNT_MIGRATION_STATE_ALLOWED_FLAG, Integer.toString(VALUE_TO_SET));
        mProperties =
                new DeviceConfig.Properties(DeviceConfig.NAMESPACE_HEALTH_FITNESS, keyValueMap);

        sHealthConnectDeviceConfigManager.onPropertiesChanged(mProperties);

        assertThat(sHealthConnectDeviceConfigManager.getMigrationStateAllowedCount())
                .isEqualTo(VALUE_TO_SET);
    }

    @Test
    public void testMaxStartMigrationCallsAllowed_changeValueInDeviceConfig_onPropertiesChanged() {
        Map<String, String> keyValueMap = new HashMap<String, String>();
        keyValueMap.put(MAX_START_MIGRATION_CALLS_ALLOWED_FLAG, Integer.toString(VALUE_TO_SET));
        mProperties =
                new DeviceConfig.Properties(DeviceConfig.NAMESPACE_HEALTH_FITNESS, keyValueMap);

        sHealthConnectDeviceConfigManager.onPropertiesChanged(mProperties);

        assertThat(sHealthConnectDeviceConfigManager.getMaxStartMigrationCalls())
                .isEqualTo(VALUE_TO_SET);
    }

    @Test
    public void testIdleStateTimeoutPeriod_changeValueInDeviceConfig_onPropertiesChanged() {
        Map<String, String> keyValueMap = new HashMap<String, String>();
        keyValueMap.put(IDLE_STATE_TIMEOUT_DAYS_FLAG, Integer.toString(VALUE_TO_SET));
        mProperties =
                new DeviceConfig.Properties(DeviceConfig.NAMESPACE_HEALTH_FITNESS, keyValueMap);

        sHealthConnectDeviceConfigManager.onPropertiesChanged(mProperties);

        assertThat(sHealthConnectDeviceConfigManager.getIdleStateTimeoutPeriod())
                .isEqualTo(Duration.ofDays((VALUE_TO_SET)));
    }

    @Test
    public void testNonIdleStateTimeoutPeriod_changeValueInDeviceConfig_onPropertiesChanged() {
        Map<String, String> keyValueMap = new HashMap<String, String>();
        keyValueMap.put(NON_IDLE_STATE_TIMEOUT_DAYS_FLAG, Integer.toString(VALUE_TO_SET));
        mProperties =
                new DeviceConfig.Properties(DeviceConfig.NAMESPACE_HEALTH_FITNESS, keyValueMap);

        sHealthConnectDeviceConfigManager.onPropertiesChanged(mProperties);

        assertThat(sHealthConnectDeviceConfigManager.getNonIdleStateTimeoutPeriod())
                .isEqualTo(Duration.ofDays((VALUE_TO_SET)));
    }

    @Test
    public void testInProgressStateTimeoutPeriod_changeValueInDeviceConfig_onPropertiesChanged() {
        Map<String, String> keyValueMap = new HashMap<String, String>();
        keyValueMap.put(IN_PROGRESS_STATE_TIMEOUT_HOURS_FLAG, Integer.toString(VALUE_TO_SET));
        mProperties =
                new DeviceConfig.Properties(DeviceConfig.NAMESPACE_HEALTH_FITNESS, keyValueMap);

        sHealthConnectDeviceConfigManager.onPropertiesChanged(mProperties);

        assertThat(sHealthConnectDeviceConfigManager.getInProgressStateTimeoutPeriod())
                .isEqualTo(Duration.ofHours(VALUE_TO_SET));
    }

    @Test
    public void testExecutionTimeBuffer_changeValueInDeviceConfig_onPropertiesChanged() {
        Map<String, String> keyValueMap = new HashMap<String, String>();
        keyValueMap.put(EXECUTION_TIME_BUFFER_MINUTES_FLAG, Integer.toString(VALUE_TO_SET));
        mProperties =
                new DeviceConfig.Properties(DeviceConfig.NAMESPACE_HEALTH_FITNESS, keyValueMap);

        sHealthConnectDeviceConfigManager.onPropertiesChanged(mProperties);

        assertThat(sHealthConnectDeviceConfigManager.getExecutionTimeBuffer())
                .isEqualTo(TimeUnit.MINUTES.toMillis(VALUE_TO_SET));
    }

    @Test
    public void
            testMigrationCompletionJobRunInterval_changeValueInDeviceConfig_onPropertiesChanged() {
        Map<String, String> keyValueMap = new HashMap<String, String>();
        keyValueMap.put(
                MIGRATION_COMPLETION_JOB_RUN_INTERVAL_DAYS_FLAG, Integer.toString(VALUE_TO_SET));
        mProperties =
                new DeviceConfig.Properties(DeviceConfig.NAMESPACE_HEALTH_FITNESS, keyValueMap);

        sHealthConnectDeviceConfigManager.onPropertiesChanged(mProperties);

        assertThat(sHealthConnectDeviceConfigManager.getMigrationCompletionJobRunInterval())
                .isEqualTo(TimeUnit.DAYS.toMillis(VALUE_TO_SET));
    }

    @Test
    public void testMigrationPauseJobRunInterval_changeValueInDeviceConfig_onPropertiesChanged() {
        Map<String, String> keyValueMap = new HashMap<String, String>();
        keyValueMap.put(
                MIGRATION_PAUSE_JOB_RUN_INTERVAL_HOURS_FLAG, Integer.toString(VALUE_TO_SET));
        mProperties =
                new DeviceConfig.Properties(DeviceConfig.NAMESPACE_HEALTH_FITNESS, keyValueMap);

        sHealthConnectDeviceConfigManager.onPropertiesChanged(mProperties);

        assertThat(sHealthConnectDeviceConfigManager.getMigrationPauseJobRunInterval())
                .isEqualTo(TimeUnit.HOURS.toMillis(VALUE_TO_SET));
    }

    @Test
    public void testEnableStateChangeJobs_changeValueInDeviceConfig_onPropertiesChanged() {
        Map<String, String> keyValueMap = new HashMap<String, String>();
        keyValueMap.put(ENABLE_PAUSE_STATE_CHANGE_JOBS_FLAG, Boolean.toString(false));
        mProperties =
                new DeviceConfig.Properties(DeviceConfig.NAMESPACE_HEALTH_FITNESS, keyValueMap);

        sHealthConnectDeviceConfigManager.onPropertiesChanged(mProperties);

        assertThat(sHealthConnectDeviceConfigManager.isPauseStateChangeJobEnabled())
                .isEqualTo(false);
    }
}
