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

package com.android.adservices.download;

import static com.android.adservices.download.MddFlags.KEY_MDD_ANDROID_SHARING_SAMPLE_INTERVAL;
import static com.android.adservices.download.MddFlags.KEY_MDD_API_LOGGING_SAMPLE_INTERVAL;
import static com.android.adservices.download.MddFlags.KEY_MDD_CELLULAR_CHARGING_GCM_TASK_PERIOD_SECONDS;
import static com.android.adservices.download.MddFlags.KEY_MDD_CHARGING_GCM_TASK_PERIOD_SECONDS;
import static com.android.adservices.download.MddFlags.KEY_MDD_DEFAULT_SAMPLE_INTERVAL;
import static com.android.adservices.download.MddFlags.KEY_MDD_DOWNLOAD_EVENTS_SAMPLE_INTERVAL;
import static com.android.adservices.download.MddFlags.KEY_MDD_GROUP_STATS_LOGGING_SAMPLE_INTERVAL;
import static com.android.adservices.download.MddFlags.KEY_MDD_MAINTENANCE_GCM_TASK_PERIOD_SECONDS;
import static com.android.adservices.download.MddFlags.KEY_MDD_MOBSTORE_FILE_SERVICE_STATS_SAMPLE_INTERVAL;
import static com.android.adservices.download.MddFlags.KEY_MDD_NETWORK_STATS_LOGGING_SAMPLE_INTERVAL;
import static com.android.adservices.download.MddFlags.KEY_MDD_STORAGE_STATS_LOGGING_SAMPLE_INTERVAL;
import static com.android.adservices.download.MddFlags.KEY_MDD_WIFI_CHARGING_GCM_TASK_PERIOD_SECONDS;

import static com.google.common.truth.Truth.assertThat;

import android.provider.DeviceConfig;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.modules.utils.testing.TestableDeviceConfig;

import com.google.android.libraries.mobiledatadownload.Flags;

import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link MddFlags} */
@SmallTest
public class MddFlagsTest {
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule(order = 1)
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    private static final Flags DEFAULT_MDD_FLAGS = new Flags() {};

    @Test
    public void testMaintenanceGcmTaskPeriod() {
        // Without any overriding, the value is the hard coded constant.
        MddFlags mddFlags = new MddFlags();
        assertThat(mddFlags.maintenanceGcmTaskPeriod())
                .isEqualTo(DEFAULT_MDD_FLAGS.maintenanceGcmTaskPeriod());

        // Now overriding with the value from PH.
        final long phOverridingValue = 123;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MDD_MAINTENANCE_GCM_TASK_PERIOD_SECONDS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mddFlags.maintenanceGcmTaskPeriod()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testChargingGcmTaskPeriod() {
        // Without any overriding, the value is the hard coded constant.
        MddFlags mddFlags = new MddFlags();
        assertThat(mddFlags.chargingGcmTaskPeriod())
                .isEqualTo(DEFAULT_MDD_FLAGS.chargingGcmTaskPeriod());

        // Now overriding with the value from PH.
        final long phOverridingValue = 124;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MDD_CHARGING_GCM_TASK_PERIOD_SECONDS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mddFlags.chargingGcmTaskPeriod()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testCellularChargingGcmTaskPeriod() {
        // Without any overriding, the value is the hard coded constant.
        MddFlags mddFlags = new MddFlags();
        assertThat(mddFlags.cellularChargingGcmTaskPeriod())
                .isEqualTo(DEFAULT_MDD_FLAGS.cellularChargingGcmTaskPeriod());

        // Now overriding with the value from PH.
        final long phOverridingValue = 125;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MDD_CELLULAR_CHARGING_GCM_TASK_PERIOD_SECONDS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mddFlags.cellularChargingGcmTaskPeriod()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testWifiChargingGcmTaskPeriod() {
        // Without any overriding, the value is the hard coded constant.
        MddFlags mddFlags = new MddFlags();
        assertThat(mddFlags.wifiChargingGcmTaskPeriod())
                .isEqualTo(DEFAULT_MDD_FLAGS.wifiChargingGcmTaskPeriod());

        // Now overriding with the value from PH.
        final long phOverridingValue = 126;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MDD_WIFI_CHARGING_GCM_TASK_PERIOD_SECONDS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mddFlags.wifiChargingGcmTaskPeriod()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testMddDefaultSampleInterval() {
        // Without any overriding, the value is the hard coded constant.
        MddFlags mddFlags = new MddFlags();
        assertThat(mddFlags.mddDefaultSampleInterval())
                .isEqualTo(DEFAULT_MDD_FLAGS.mddDefaultSampleInterval());

        // Now overriding with the value from PH.
        final long phOverridingValue = 127;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MDD_DEFAULT_SAMPLE_INTERVAL,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mddFlags.mddDefaultSampleInterval()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testMddDownloadEventsSampleInterval() {
        // Without any overriding, the value is the hard coded constant.
        MddFlags mddFlags = new MddFlags();
        assertThat(mddFlags.mddDownloadEventsSampleInterval())
                .isEqualTo(DEFAULT_MDD_FLAGS.mddDownloadEventsSampleInterval());

        // Now overriding with the value from PH.
        final long phOverridingValue = 128;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MDD_DOWNLOAD_EVENTS_SAMPLE_INTERVAL,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mddFlags.mddDownloadEventsSampleInterval()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGroupStatsLoggingSampleInterval() {
        // Without any overriding, the value is the hard coded constant.
        MddFlags mddFlags = new MddFlags();
        assertThat(mddFlags.groupStatsLoggingSampleInterval())
                .isEqualTo(DEFAULT_MDD_FLAGS.groupStatsLoggingSampleInterval());

        // Now overriding with the value from PH.
        final long phOverridingValue = 128;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MDD_GROUP_STATS_LOGGING_SAMPLE_INTERVAL,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mddFlags.groupStatsLoggingSampleInterval()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testApiLoggingSampleInterval() {
        // Without any overriding, the value is the hard coded constant.
        MddFlags mddFlags = new MddFlags();
        assertThat(mddFlags.apiLoggingSampleInterval())
                .isEqualTo(DEFAULT_MDD_FLAGS.apiLoggingSampleInterval());

        // Now overriding with the value from PH.
        final long phOverridingValue = 129;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MDD_API_LOGGING_SAMPLE_INTERVAL,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mddFlags.apiLoggingSampleInterval()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testStorageStatsLoggingSampleInterval() {
        // Without any overriding, the value is the hard coded constant.
        MddFlags mddFlags = new MddFlags();
        assertThat(mddFlags.storageStatsLoggingSampleInterval())
                .isEqualTo(DEFAULT_MDD_FLAGS.storageStatsLoggingSampleInterval());

        // Now overriding with the value from PH.
        final long phOverridingValue = 129;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MDD_STORAGE_STATS_LOGGING_SAMPLE_INTERVAL,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mddFlags.storageStatsLoggingSampleInterval()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testNetworkStatsLoggingSampleInterval() {
        // Without any overriding, the value is the hard coded constant.
        MddFlags mddFlags = new MddFlags();
        assertThat(mddFlags.networkStatsLoggingSampleInterval())
                .isEqualTo(DEFAULT_MDD_FLAGS.networkStatsLoggingSampleInterval());

        // Now overriding with the value from PH.
        final long phOverridingValue = 130;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MDD_NETWORK_STATS_LOGGING_SAMPLE_INTERVAL,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mddFlags.networkStatsLoggingSampleInterval()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testMobstoreFileServiceStatsSampleInterval() {
        // Without any overriding, the value is the hard coded constant.
        MddFlags mddFlags = new MddFlags();
        assertThat(mddFlags.mobstoreFileServiceStatsSampleInterval())
                .isEqualTo(DEFAULT_MDD_FLAGS.mobstoreFileServiceStatsSampleInterval());

        // Now overriding with the value from PH.
        final long phOverridingValue = 131;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MDD_MOBSTORE_FILE_SERVICE_STATS_SAMPLE_INTERVAL,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mddFlags.mobstoreFileServiceStatsSampleInterval()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testMddAndroidSharingSampleInterval() {
        // Without any overriding, the value is the hard coded constant.
        MddFlags mddFlags = new MddFlags();
        assertThat(mddFlags.mddAndroidSharingSampleInterval())
                .isEqualTo(DEFAULT_MDD_FLAGS.mddAndroidSharingSampleInterval());

        // Now overriding with the value from PH.
        final long phOverridingValue = 132;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MDD_ANDROID_SHARING_SAMPLE_INTERVAL,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mddFlags.mddAndroidSharingSampleInterval()).isEqualTo(phOverridingValue);
    }
}
