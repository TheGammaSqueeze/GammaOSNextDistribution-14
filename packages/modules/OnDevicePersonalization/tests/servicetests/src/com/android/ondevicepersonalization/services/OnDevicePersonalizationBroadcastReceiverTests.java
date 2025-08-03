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

package com.android.ondevicepersonalization.services;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.TestableDeviceConfig;
import com.android.ondevicepersonalization.services.download.mdd.MobileDataDownloadFactory;
import com.android.ondevicepersonalization.services.policyengine.api.ChronicleManager;
import com.android.ondevicepersonalization.services.util.DeviceUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationBroadcastReceiverTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .addStaticMockFixtures(TestableDeviceConfig::new)
            .spyStatic(DeviceUtils.class)
            .setStrictness(Strictness.LENIENT)
            .build();

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();
        ExtendedMockito.doReturn(true).when(() -> DeviceUtils.isOdpSupported(any()));
        ChronicleManager.instance = null;
        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        jobScheduler.cancel(OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID);
        jobScheduler.cancel(OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID);
        jobScheduler.cancel(
                OnDevicePersonalizationConfig.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID);
        jobScheduler.cancel(OnDevicePersonalizationConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID);
        jobScheduler.cancel(OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID);
        jobScheduler.cancel(OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID);
    }

    @Test
    public void testOnReceive() {
        // Use direct executor to keep all work sequential for the tests
        ListeningExecutorService executorService = MoreExecutors.newDirectExecutorService();
        MobileDataDownloadFactory.getMdd(mContext, executorService, executorService);

        OnDevicePersonalizationBroadcastReceiver receiver =
                new OnDevicePersonalizationBroadcastReceiver(executorService);

        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        receiver.onReceive(mContext, intent);
        // Policy engine should be initialized
        assertNotNull(ChronicleManager.instance);

        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);

        assertTrue(
                jobScheduler.getPendingJob(OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID)
                        != null);
        assertTrue(
                jobScheduler.getPendingJob(OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID)
                        != null);
        // MDD tasks
        assertTrue(
                jobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID)
                        != null);
        assertTrue(
                jobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID)
                        != null);
        assertTrue(
                jobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig
                                        .MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID)
                        != null);
        assertTrue(
                jobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig
                                        .MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID)
                        != null);
    }

    @Test
    public void testOnReceiveKillSwitchOn() {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        // Use direct executor to keep all work sequential for the tests
        ListeningExecutorService executorService = MoreExecutors.newDirectExecutorService();
        OnDevicePersonalizationBroadcastReceiver receiver =
                new OnDevicePersonalizationBroadcastReceiver(executorService);

        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        receiver.onReceive(mContext, intent);

        assertNull(ChronicleManager.instance);

        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);

        assertTrue(
                jobScheduler.getPendingJob(OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID)
                        == null);
        assertTrue(
                jobScheduler.getPendingJob(OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID)
                        == null);
        // MDD tasks
        assertTrue(
                jobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                jobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                jobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                jobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
    }

    @Test
    public void testOnReceiveDeviceNotSupported() {
        ExtendedMockito.doReturn(false).when(() -> DeviceUtils.isOdpSupported(any()));
        // Use direct executor to keep all work sequential for the tests
        ListeningExecutorService executorService = MoreExecutors.newDirectExecutorService();
        OnDevicePersonalizationBroadcastReceiver receiver =
                new OnDevicePersonalizationBroadcastReceiver(executorService);

        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        receiver.onReceive(mContext, intent);

        assertNull(ChronicleManager.instance);

        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);

        assertTrue(
                jobScheduler.getPendingJob(OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID)
                        == null);
        assertTrue(
                jobScheduler.getPendingJob(OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID)
                        == null);
        // MDD tasks
        assertTrue(
                jobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                jobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                jobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                jobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
    }

    @Test
    public void testOnReceiveInvalidIntent() {
        OnDevicePersonalizationBroadcastReceiver receiver =
                new OnDevicePersonalizationBroadcastReceiver();

        Intent intent = new Intent(Intent.ACTION_DIAL_EMERGENCY);
        receiver.onReceive(mContext, intent);
        assertNull(ChronicleManager.instance);

        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);

        assertTrue(
                jobScheduler.getPendingJob(OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID)
                        == null);
        assertTrue(
                jobScheduler.getPendingJob(OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID)
                        == null);
        // MDD tasks
        assertTrue(
                jobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                jobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                jobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig
                                        .MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                jobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig
                                        .MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
    }

    @Test
    public void testEnableReceiver() {
        assertTrue(OnDevicePersonalizationBroadcastReceiver.enableReceiver(mContext));
        ComponentName componentName =
                new ComponentName(mContext, OnDevicePersonalizationBroadcastReceiver.class);
        final PackageManager pm = mContext.getPackageManager();
        final int result = pm.getComponentEnabledSetting(componentName);
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED, result);
    }
}
