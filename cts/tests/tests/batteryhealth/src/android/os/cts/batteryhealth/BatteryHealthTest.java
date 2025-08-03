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
package android.os.cts.batteryhealth;

import static android.os.Flags.stateOfHealthPublic;
import static android.os.Flags.batteryPartStatusApi;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Test;

public class BatteryHealthTest {
    private static final String TAG = "BatteryHealthTest";

    // Battery usage date: check the range from 2020-12-01 to 2038-01-19
    private static final long BATTERY_USAGE_DATE_IN_EPOCH_MIN = 1606780800;
    private static final long BATTERY_USAGE_DATE_IN_EPOCH_MAX = 2147472000;

    // Battery state_of_health: value must be in the range 0 to 100
    private static final int BATTERY_STATE_OF_HEALTH_MIN = 0;
    private static final int BATTERY_STATE_OF_HEALTH_MAX = 100;

    // ChargingPolicy
    private static final int CHARGING_POLICY_DEFAULT = 1;

    private BatteryManager mBatteryManager;

    private UiAutomation mAutomation;

    @Before
    public void setUp() {
        final Context context = InstrumentationRegistry.getContext();

        mBatteryManager = context.getSystemService(BatteryManager.class);
    }

    @Test
    @ApiTest(apis = {"android.os.BatteryManager#BATTERY_PROPERTY_MANUFACTURING_DATE"})
    public void testManufacturingDate_dataInRange() {
        mAutomation = getInstrumentation().getUiAutomation();
        mAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BATTERY_STATS);
        final long manufacturingDate = mBatteryManager.getLongProperty(BatteryManager
                .BATTERY_PROPERTY_MANUFACTURING_DATE);

        if (manufacturingDate > 0) {
            assertThat(manufacturingDate).isAtLeast(BATTERY_USAGE_DATE_IN_EPOCH_MIN);
            assertThat(manufacturingDate).isLessThan(BATTERY_USAGE_DATE_IN_EPOCH_MAX + 1);
        }

        mAutomation.dropShellPermissionIdentity();
    }

    @Test
    @ApiTest(apis = {"android.os.BatteryManager#BATTERY_PROPERTY_FIRST_USAGE_DATE"})
    public void testFirstUsageDate_dataInRange() {
        mAutomation = getInstrumentation().getUiAutomation();
        mAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BATTERY_STATS);
        final long firstUsageDate = mBatteryManager.getLongProperty(BatteryManager
                .BATTERY_PROPERTY_FIRST_USAGE_DATE);

        if (firstUsageDate > 0) {
            assertThat(firstUsageDate).isAtLeast(BATTERY_USAGE_DATE_IN_EPOCH_MIN);
            assertThat(firstUsageDate).isLessThan(BATTERY_USAGE_DATE_IN_EPOCH_MAX + 1);
        }

        mAutomation.dropShellPermissionIdentity();
    }

    @Test
    @ApiTest(apis = {"android.os.BatteryManager#BATTERY_PROPERTY_CHARGING_POLICY"})
    public void testChargingPolicy_dataInRange() {
        mAutomation = getInstrumentation().getUiAutomation();
        mAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BATTERY_STATS);
        final int chargingPolicy = mBatteryManager.getIntProperty(BatteryManager
                .BATTERY_PROPERTY_CHARGING_POLICY);

        if (chargingPolicy >= 0) {
            assertThat(chargingPolicy).isAtLeast(CHARGING_POLICY_DEFAULT);
        }

        mAutomation.dropShellPermissionIdentity();
    }

    @Test
    @ApiTest(apis = {"android.os.BatteryManager#BATTERY_PROPERTY_STATE_OF_HEALTH"})
    public void testBatteryStateOfHealth_dataInRange() {
        if (!stateOfHealthPublic()) {
            mAutomation = getInstrumentation().getUiAutomation();
            mAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BATTERY_STATS);
        }
        final int stateOfHealth = mBatteryManager.getIntProperty(BatteryManager
                .BATTERY_PROPERTY_STATE_OF_HEALTH);

        if (stateOfHealth >= 0) {
            assertThat(stateOfHealth).isAtLeast(BATTERY_STATE_OF_HEALTH_MIN);
            assertThat(stateOfHealth).isLessThan(BATTERY_STATE_OF_HEALTH_MAX + 1);
        }
        if (!stateOfHealthPublic()) {
            mAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @ApiTest(apis = {"android.os.BatteryManager#BATTERY_PROPERTY_SERIAL_NUMBER"})
    public void testBatterySerialNumber_dataValid() {
        if (!batteryPartStatusApi()) {
            return;
        }
        mAutomation = getInstrumentation().getUiAutomation();
        mAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BATTERY_STATS);
        final String serialNumber = mBatteryManager.getStringProperty(BatteryManager
                .BATTERY_PROPERTY_SERIAL_NUMBER);

        if (serialNumber != null) {
            assertThat(serialNumber.length()).isAtLeast(6);
        }
        mAutomation.dropShellPermissionIdentity();
    }

    @Test
    @ApiTest(apis = {"android.os.BatteryManager#BATTERY_PROPERTY_PART_STATUS"})
    public void testBatteryPartStatus_dataInRange() {
        if (!batteryPartStatusApi()) {
            return;
        }
        mAutomation = getInstrumentation().getUiAutomation();
        mAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BATTERY_STATS);
        final int partStatus = mBatteryManager.getIntProperty(BatteryManager
                .BATTERY_PROPERTY_PART_STATUS);

        assertThat(partStatus).isAtLeast(BatteryManager.PART_STATUS_UNSUPPORTED);
        assertThat(partStatus).isAtMost(BatteryManager.PART_STATUS_REPLACED);
        mAutomation.dropShellPermissionIdentity();
    }

    @Test
    @ApiTest(apis = {"android.os.BatteryManager#EXTRA_CYCLE_COUNT"})
    public void testBatteryCycleCount_dataInRange() {
        final Context context = InstrumentationRegistry.getContext();
        final Intent batteryInfo = context.registerReceiver(null,
                                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        final int batteryCycleCount = batteryInfo.getIntExtra(BatteryManager
                .EXTRA_CYCLE_COUNT, -1);

        assertThat(batteryCycleCount).isAtLeast(0);
    }

    @Test
    @ApiTest(apis = {"android.os.BatteryManager#BATTERY_PROPERTY_MANUFACTURING_DATE"})
    public void testManufacturingDate_noPermission() {
        try {
            final long manufacturingDate = mBatteryManager.getLongProperty(BatteryManager
                    .BATTERY_PROPERTY_MANUFACTURING_DATE);
        } catch (SecurityException expected) {
            return;
        }
        fail("Didn't throw SecurityException");
    }

    @Test
    @ApiTest(apis = {"android.os.BatteryManager#BATTERY_PROPERTY_FIRST_USAGE_DATE"})
    public void testFirstUsageDate_noPermission() {
        try {
            final long firstUsageDate = mBatteryManager.getLongProperty(BatteryManager
                    .BATTERY_PROPERTY_FIRST_USAGE_DATE);
        } catch (SecurityException expected) {
            return;
        }
        fail("Didn't throw SecurityException");
    }

    @Test
    @ApiTest(apis = {"android.os.BatteryManager#BATTERY_PROPERTY_CHARGING_POLICY"})
    public void testChargingPolicy_noPermission() {
        try {
            final int chargingPolicy = mBatteryManager.getIntProperty(BatteryManager
                    .BATTERY_PROPERTY_CHARGING_POLICY);
        } catch (SecurityException expected) {
            return;
        }
        fail("Didn't throw SecurityException");
    }
}
