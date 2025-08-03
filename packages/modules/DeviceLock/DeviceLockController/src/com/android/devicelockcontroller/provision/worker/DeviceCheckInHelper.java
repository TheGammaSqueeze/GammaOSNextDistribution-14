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

import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceIdType.DEVICE_ID_TYPE_IMEI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceIdType.DEVICE_ID_TYPE_MEID;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_MANDATORY_PROVISION;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_PROVISIONING_TYPE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.READY_FOR_PROVISION;
import static com.android.devicelockcontroller.common.DeviceLockConstants.RETRY_CHECK_IN;
import static com.android.devicelockcontroller.common.DeviceLockConstants.STATUS_UNSPECIFIED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.STOP_CHECK_IN;
import static com.android.devicelockcontroller.common.DeviceLockConstants.TOTAL_DEVICE_ID_TYPES;
import static com.android.devicelockcontroller.receivers.CheckInBootCompletedReceiver.disableCheckInBootCompletedReceiver;
import static com.android.devicelockcontroller.stats.StatsLogger.CheckInRetryReason.CONFIG_UNAVAILABLE;
import static com.android.devicelockcontroller.stats.StatsLogger.CheckInRetryReason.NETWORK_TIME_UNAVAILABLE;
import static com.android.devicelockcontroller.stats.StatsLogger.CheckInRetryReason.RESPONSE_UNSPECIFIED;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.common.DeviceId;
import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.ProvisioningConfiguration;
import com.android.devicelockcontroller.receivers.ProvisionReadyReceiver;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.DateTimeException;
import java.time.Duration;

/**
 * Helper class to perform the device check-in process with device lock backend server
 */
public final class DeviceCheckInHelper extends AbstractDeviceCheckInHelper {
    private static final String TAG = "DeviceCheckInHelper";
    private final Context mAppContext;
    private final TelephonyManager mTelephonyManager;
    private final StatsLogger mStatsLogger;

    public DeviceCheckInHelper(Context appContext) {
        mAppContext = appContext;
        mTelephonyManager = mAppContext.getSystemService(TelephonyManager.class);
        mStatsLogger = ((StatsLoggerProvider) mAppContext).getStatsLogger();
    }

    private boolean hasGsm() {
        return mAppContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_GSM);
    }

    private boolean hasCdma() {
        return mAppContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_CDMA);
    }

    @Override
    ArraySet<DeviceId> getDeviceUniqueIds() {
        final int deviceIdTypeBitmap = mAppContext.getResources().getInteger(
                R.integer.device_id_type_bitmap);
        if (deviceIdTypeBitmap < 0) {
            LogUtil.e(TAG, "getDeviceId: Cannot get device_id_type_bitmap");
            return new ArraySet<>();
        }

        return getDeviceAvailableUniqueIds(deviceIdTypeBitmap);
    }

    @VisibleForTesting
    ArraySet<DeviceId> getDeviceAvailableUniqueIds(int deviceIdTypeBitmap) {

        final int totalSlotCount = mTelephonyManager.getActiveModemCount();
        final int maximumIdCount = TOTAL_DEVICE_ID_TYPES * totalSlotCount;
        final ArraySet<DeviceId> deviceIds = new ArraySet<>(maximumIdCount);
        if (maximumIdCount == 0) return deviceIds;

        for (int i = 0; i < totalSlotCount; i++) {
            if (hasGsm() && (deviceIdTypeBitmap & (1 << DEVICE_ID_TYPE_IMEI)) != 0) {
                final String imei = mTelephonyManager.getImei(i);

                if (imei != null) {
                    deviceIds.add(new DeviceId(DEVICE_ID_TYPE_IMEI, imei));
                }
            }

            if (hasCdma() && (deviceIdTypeBitmap & (1 << DEVICE_ID_TYPE_MEID)) != 0) {
                final String meid = mTelephonyManager.getMeid(i);

                if (meid != null) {
                    deviceIds.add(new DeviceId(DEVICE_ID_TYPE_MEID, meid));
                }
            }
        }

        return deviceIds;
    }

    @Override
    String getCarrierInfo() {
        return mTelephonyManager.getSimOperator();
    }

    @Override
    @WorkerThread
    boolean handleGetDeviceCheckInStatusResponse(
            GetDeviceCheckInStatusGrpcResponse response,
            DeviceLockControllerScheduler scheduler) {
        Futures.getUnchecked(GlobalParametersClient.getInstance().setRegisteredDeviceId(
                response.getRegisteredDeviceIdentifier()));
        LogUtil.d(TAG, "check in response: " + response.getDeviceCheckInStatus());
        switch (response.getDeviceCheckInStatus()) {
            case READY_FOR_PROVISION:
                boolean result = handleProvisionReadyResponse(response);
                disableCheckInBootCompletedReceiver(mAppContext);
                return result;
            case RETRY_CHECK_IN:
                try {
                    Duration delay = Duration.between(
                            SystemClock.currentNetworkTimeClock().instant(),
                            response.getNextCheckInTime());
                    // Retry immediately if next check in time is in the past.
                    delay = delay.isNegative() ? Duration.ZERO : delay;
                    scheduler.scheduleRetryCheckInWork(delay);
                    return true;
                } catch (DateTimeException e) {
                    LogUtil.e(TAG, "No network time is available!");
                    mStatsLogger.logCheckInRetry(NETWORK_TIME_UNAVAILABLE);
                    return false;
                }
            case STOP_CHECK_IN:
                final ListenableFuture<Void> clearRestrictionsFuture =
                        ((PolicyObjectsProvider) mAppContext).getFinalizationController()
                                .notifyRestrictionsCleared();
                Futures.addCallback(clearRestrictionsFuture,
                        new FutureCallback<>() {
                            @Override
                            public void onSuccess(Void result) {
                                // no-op
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                LogUtil.e(TAG, "Failed to clear restrictions", t);
                            }
                        }, MoreExecutors.directExecutor()
                );
                disableCheckInBootCompletedReceiver(mAppContext);
                return true;
            case STATUS_UNSPECIFIED:
            default:
                mStatsLogger.logCheckInRetry(RESPONSE_UNSPECIFIED);
                return false;
        }
    }

    @VisibleForTesting
    @WorkerThread
    boolean handleProvisionReadyResponse(
            @NonNull GetDeviceCheckInStatusGrpcResponse response) {
        GlobalParametersClient globalParametersClient = GlobalParametersClient.getInstance();
        Futures.getUnchecked(globalParametersClient.setProvisionForced(
                response.isProvisionForced()));
        final ProvisioningConfiguration configuration = response.getProvisioningConfig();
        if (configuration == null) {
            LogUtil.e(TAG, "Provisioning Configuration is not provided by server!");
            mStatsLogger.logCheckInRetry(CONFIG_UNAVAILABLE);
            return false;
        }
        final Bundle provisionBundle = configuration.toBundle();
        provisionBundle.putInt(EXTRA_PROVISIONING_TYPE, response.getProvisioningType());
        provisionBundle.putBoolean(EXTRA_MANDATORY_PROVISION,
                response.isProvisioningMandatory());
        Futures.getUnchecked(
                SetupParametersClient.getInstance().createPrefs(provisionBundle));
        Futures.getUnchecked(globalParametersClient.setProvisionReady(true));
        mAppContext.sendBroadcastAsUser(
                new Intent(mAppContext, ProvisionReadyReceiver.class),
                UserHandle.ALL);
        return true;
    }
}
