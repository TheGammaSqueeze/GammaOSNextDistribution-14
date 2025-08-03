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

package com.android.devicelockcontroller.provision.worker;

import static android.os.Looper.getMainLooper;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceIdType.DEVICE_ID_TYPE_IMEI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceIdType.DEVICE_ID_TYPE_MEID;
import static com.android.devicelockcontroller.common.DeviceLockConstants.READY_FOR_PROVISION;
import static com.android.devicelockcontroller.common.DeviceLockConstants.RETRY_CHECK_IN;
import static com.android.devicelockcontroller.common.DeviceLockConstants.STOP_CHECK_IN;
import static com.android.devicelockcontroller.stats.StatsLogger.CheckInRetryReason.CONFIG_UNAVAILABLE;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.ArraySet;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.common.DeviceId;
import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceCheckInStatus;
import com.android.devicelockcontroller.policy.FinalizationController;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.ProvisioningConfiguration;
import com.android.devicelockcontroller.receivers.ProvisionReadyReceiver;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;
import com.android.devicelockcontroller.storage.GlobalParametersClient;

import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowTelephonyManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@LooperMode(LEGACY)
@RunWith(RobolectricTestRunner.class)
public final class DeviceCheckInHelperTest {
    static final Duration TEST_CHECK_RETRY_DURATION = Duration.ofDays(30);
    static final Duration TEST_NEGATIVE_CHECK_RETRY_DURATION =
            Duration.ZERO.minus(TEST_CHECK_RETRY_DURATION);
    public static final boolean IS_PROVISIONING_MANDATORY = false;
    private TestDeviceLockControllerApplication mTestApplication;
    static final int TOTAL_SLOT_COUNT = 2;
    static final int TOTAL_ID_COUNT = 4;
    static final String IMEI_1 = "IMEI1";
    static final String IMEI_2 = "IMEI2";
    static final String MEID_1 = "MEID1";
    static final String MEID_2 = "MEID2";
    static final ArraySet<DeviceId> ACTUAL_DEVICE_IDs =
            new ArraySet<>(new DeviceId[]{
                    new DeviceId(DEVICE_ID_TYPE_IMEI, IMEI_1),
                    new DeviceId(DEVICE_ID_TYPE_IMEI, IMEI_2),
                    new DeviceId(DEVICE_ID_TYPE_MEID, MEID_1),
                    new DeviceId(DEVICE_ID_TYPE_MEID, MEID_2),
            });
    static final ProvisioningConfiguration TEST_CONFIGURATION = new ProvisioningConfiguration(
            /* kioskAppProviderName= */ "test_provider",
            /* kioskAppPackageName= */ "test_package",
            /* kioskAppAllowlistPackages= */ List.of("test_allowed_app1", "test_allowed_app2"),
            /* kioskAppEnableOutgoingCalls= */ false,
            /* kioskAppEnableEnableNotifications= */ true,
            /* disallowInstallingFromUnknownSources= */ false,
            /* termsAndConditionsUrl= */ "test_terms_and_configurations_url",
            /* supportUrl= */ "test_support_url"
    );
    static final int DEVICE_ID_TYPE_BITMAP =
            (1 << DEVICE_ID_TYPE_IMEI) | (1 << DEVICE_ID_TYPE_MEID);

    private FinalizationController mFinalizationController;
    private DeviceCheckInHelper mHelper;

    private ShadowTelephonyManager mTelephonyManager;
    private GlobalParametersClient mGlobalParametersClient;
    private DeviceLockControllerScheduler mScheduler;
    private StatsLogger mStatsLogger;
    private ShadowPackageManager mPackageManager;

    @Before
    public void setUp() {
        mTestApplication = ApplicationProvider.getApplicationContext();
        mScheduler = mTestApplication.getDeviceLockControllerScheduler();
        mFinalizationController = mTestApplication.getFinalizationController();
        when(mFinalizationController.notifyRestrictionsCleared()).thenReturn(
                Futures.immediateVoidFuture());

        mTelephonyManager = Shadows.shadowOf(
                mTestApplication.getSystemService(TelephonyManager.class));
        mHelper = new DeviceCheckInHelper(mTestApplication);
        WorkManagerTestInitHelper.initializeTestWorkManager(mTestApplication,
                new Configuration.Builder()
                        .setMinimumLoggingLevel(android.util.Log.DEBUG)
                        .setExecutor(new SynchronousExecutor())
                        .build());
        mGlobalParametersClient = GlobalParametersClient.getInstance();
        mStatsLogger = ((StatsLoggerProvider) mTestApplication).getStatsLogger();
        mPackageManager = Shadows.shadowOf(mTestApplication.getPackageManager());
    }

    @Test
    public void getDeviceAvailableUniqueIds_shouldReturnAllAvailableUniqueIds() {
        mPackageManager.setSystemFeature(PackageManager.FEATURE_TELEPHONY_GSM,
                /* supported= */ true);
        mPackageManager.setSystemFeature(PackageManager.FEATURE_TELEPHONY_CDMA,
                /* supported= */ true);
        mTelephonyManager.setActiveModemCount(TOTAL_SLOT_COUNT);
        mTelephonyManager.setImei(/* slotIndex= */ 0, IMEI_1);
        mTelephonyManager.setImei(/* slotIndex= */ 1, IMEI_2);
        mTelephonyManager.setMeid(/* slotIndex= */ 0, MEID_1);
        mTelephonyManager.setMeid(/* slotIndex= */ 1, MEID_2);
        final ArraySet<DeviceId> deviceIds = mHelper.getDeviceAvailableUniqueIds(
                DEVICE_ID_TYPE_BITMAP);
        assertThat(Objects.requireNonNull(deviceIds).size()).isEqualTo(TOTAL_ID_COUNT);
        assertThat(deviceIds).containsExactlyElementsIn(ACTUAL_DEVICE_IDs);
    }

    @Test
    public void testHandleGetDeviceCheckInStatusResponse_stopCheckIn_clearsRestrictions() {
        final GetDeviceCheckInStatusGrpcResponse response = createStopResponse();

        assertThat(mHelper.handleGetDeviceCheckInStatusResponse(response,
                mock(DeviceLockControllerScheduler.class))).isTrue();
        Shadows.shadowOf(getMainLooper()).idle();
        verify(mFinalizationController).notifyRestrictionsCleared();
    }

    @Test
    public void handleProvisionReadyResponse_validConfiguration_shouldSendBroadcast() {
        GetDeviceCheckInStatusGrpcResponse response = createReadyResponse(TEST_CONFIGURATION);

        assertThat(mHelper.handleProvisionReadyResponse(response)).isTrue();

        assertThat(Futures.getUnchecked(mGlobalParametersClient.isProvisionReady())).isTrue();
        List<Intent> intents = Shadows.shadowOf(mTestApplication).getBroadcastIntents();
        assertThat(intents.size()).isEqualTo(1);
        assertThat(intents.get(0).getComponent().getClassName()).isEqualTo(
                ProvisionReadyReceiver.class.getName());
    }

    @Test
    public void testHandleProvisionReadyResponse_invalidConfiguration_shouldNotSendBroadcast() {
        GetDeviceCheckInStatusGrpcResponse response = createReadyResponse(
                /* configuration= */ null);

        assertThat(mHelper.handleProvisionReadyResponse(response)).isFalse();

        assertThat(Futures.getUnchecked(mGlobalParametersClient.isProvisionReady())).isFalse();
        List<Intent> intents = Shadows.shadowOf(mTestApplication).getBroadcastIntents();
        assertThat(intents.size()).isEqualTo(0);
    }

    @Test
    public void handleProvisionReadyResponse_invalidConfiguration_shouldLogRetryCheckIn() {
        GetDeviceCheckInStatusGrpcResponse response = createReadyResponse(
                /* configuration= */ null);

        mHelper.handleProvisionReadyResponse(response);

        verify(mStatsLogger).logCheckInRetry(CONFIG_UNAVAILABLE);
    }

    @Test
    public void testHandleGetDeviceCheckInStatusResponse_retryCheckIn_shouldScheduleRetryWork() {
        final GetDeviceCheckInStatusGrpcResponse response = createRetryResponse(
                SystemClock.currentNetworkTimeClock().instant().plus(TEST_CHECK_RETRY_DURATION));

        assertThat(mHelper.handleGetDeviceCheckInStatusResponse(response, mScheduler)).isTrue();

        verify(mScheduler).scheduleRetryCheckInWork(eq(TEST_CHECK_RETRY_DURATION));
    }

    @Test
    public void handleGetDeviceCheckInStatusResponse_retryCheckIn_durationIsNegative_shouldRetry() {
        final GetDeviceCheckInStatusGrpcResponse response = createRetryResponse(
                SystemClock.currentNetworkTimeClock().instant().plus(
                        TEST_NEGATIVE_CHECK_RETRY_DURATION));

        assertThat(mHelper.handleGetDeviceCheckInStatusResponse(response, mScheduler)).isTrue();

        verify(mScheduler).scheduleRetryCheckInWork(eq(Duration.ZERO));

    }

    private static GetDeviceCheckInStatusGrpcResponse createStopResponse() {
        return createMockResponse(STOP_CHECK_IN, /* nextCheckInTime= */ null, /* config= */ null);
    }


    private static GetDeviceCheckInStatusGrpcResponse createRetryResponse(Instant nextCheckInTime) {
        return createMockResponse(RETRY_CHECK_IN, nextCheckInTime, /* config= */ null);
    }

    private static GetDeviceCheckInStatusGrpcResponse createReadyResponse(
            ProvisioningConfiguration configuration) {
        return createMockResponse(READY_FOR_PROVISION, /* nextCheckInTime= */ null, configuration);
    }

    private static GetDeviceCheckInStatusGrpcResponse createMockResponse(
            @DeviceCheckInStatus int checkInStatus,
            @Nullable Instant nextCheckInTime, @Nullable ProvisioningConfiguration config) {
        GetDeviceCheckInStatusGrpcResponse response = Mockito.mock(
                GetDeviceCheckInStatusGrpcResponse.class);
        when(response.getDeviceCheckInStatus()).thenReturn(checkInStatus);
        when(response.isProvisioningMandatory()).thenReturn(IS_PROVISIONING_MANDATORY);
        if (nextCheckInTime != null) {
            when(response.getNextCheckInTime()).thenReturn(nextCheckInTime);
        }
        if (config != null) {
            when(response.getProvisioningConfig()).thenReturn(config);
        }
        return response;
    }
}
