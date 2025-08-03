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

import static android.adservices.ondevicepersonalization.OnDevicePersonalizationPermissions.MODIFY_ONDEVICEPERSONALIZATION_STATE;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_REMOTE_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ON_DEVICE_PERSONALIZATION_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ODP;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationPermissions;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationConfigService;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationConfigServiceCallback;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemService;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemServiceCallback;
import android.ondevicepersonalization.OnDevicePersonalizationSystemServiceManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.RemoteException;

import com.android.modules.utils.build.SdkLevel;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.data.user.RawUserData;
import com.android.ondevicepersonalization.services.data.user.UserDataCollector;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.statsd.errorlogging.ClientErrorLogger;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * ODP service that modifies and persists ODP enablement status
 */
public class OnDevicePersonalizationConfigServiceDelegate
        extends IOnDevicePersonalizationConfigService.Stub {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationConfigServiceDelegate";
    private final Context mContext;
    private static final Executor sBackgroundExecutor =
            OnDevicePersonalizationExecutors.getBackgroundExecutor();
    private static final int SERVICE_NOT_IMPLEMENTED = 501;

    public OnDevicePersonalizationConfigServiceDelegate(Context context) {
        mContext = context;
    }

    @Override
    @RequiresPermission(MODIFY_ONDEVICEPERSONALIZATION_STATE)
    public void setPersonalizationStatus(boolean enabled,
                                     @NonNull IOnDevicePersonalizationConfigServiceCallback
                                             callback) {
        if (getGlobalKillSwitch()) {
            throw new IllegalStateException("Service skipped as the API flag is turned off.");
        }
        // Verify caller's permission
        OnDevicePersonalizationPermissions.enforceCallingPermission(mContext,
                MODIFY_ONDEVICEPERSONALIZATION_STATE);
        Objects.requireNonNull(callback);
        sBackgroundExecutor.execute(
                () -> {
                    try {
                        UserPrivacyStatus userPrivacyStatus = UserPrivacyStatus.getInstance();

                        boolean oldStatus = userPrivacyStatus.isPersonalizationStatusEnabled();
                        userPrivacyStatus.setPersonalizationStatusEnabled(enabled);
                        boolean newStatus = userPrivacyStatus.isPersonalizationStatusEnabled();

                        if (oldStatus == newStatus) {
                            sendSuccess(callback);
                            return;
                        }

                        // Rollback all user data if personalization status changes
                        RawUserData userData = RawUserData.getInstance();
                        UserDataCollector userDataCollector =
                                UserDataCollector.getInstance(mContext);
                        userDataCollector.clearUserData(userData);
                        userDataCollector.clearMetadata();

                        // TODO(b/302018665): replicate system server storage to T devices.
                        if (!SdkLevel.isAtLeastU()) {
                            userPrivacyStatus.setPersonalizationStatusEnabled(enabled);
                            sendSuccess(callback);
                            return;
                        }
                        // Persist in the system server for U+ devices
                        OnDevicePersonalizationSystemServiceManager systemServiceManager =
                                mContext.getSystemService(
                                        OnDevicePersonalizationSystemServiceManager.class);
                        // Cannot find system server on U+.
                        if (systemServiceManager == null) {
                            sendError(callback, SERVICE_NOT_IMPLEMENTED);
                            return;
                        }
                        IOnDevicePersonalizationSystemService systemService =
                                systemServiceManager.getService();
                        // The system service is not ready.
                        if (systemService == null) {
                            sendError(callback, SERVICE_NOT_IMPLEMENTED);
                            return;
                        }
                        try {
                            systemService.setPersonalizationStatus(
                                    enabled,
                                    new IOnDevicePersonalizationSystemServiceCallback.Stub() {
                                        @Override
                                        public void onResult(Bundle bundle) throws RemoteException {
                                            userPrivacyStatus.setPersonalizationStatusEnabled(
                                                    enabled);
                                            callback.onSuccess();
                                        }

                                        @Override
                                        public void onError(int errorCode) throws RemoteException {
                                            callback.onFailure(errorCode);
                                        }
                                    });
                        } catch (RemoteException re) {
                            sLogger.e(TAG + ": Unable to send result to the callback.", re);
                            ClientErrorLogger.getInstance()
                                    .logErrorWithExceptionInfo(
                                            re,
                                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_REMOTE_EXCEPTION,
                                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ODP);
                        }
                    } catch (Exception e) {
                        sLogger.e(TAG + ": Failed to set personalization status.", e);
                        ClientErrorLogger.getInstance()
                                .logErrorWithExceptionInfo(
                                        e,
                                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ON_DEVICE_PERSONALIZATION_ERROR,
                                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ODP);
                        sendError(callback, Constants.STATUS_INTERNAL_ERROR);
                    }
                });
    }

    private void sendSuccess(
            @NonNull IOnDevicePersonalizationConfigServiceCallback callback) {
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            sLogger.e(TAG + ": Callback error", e);
            ClientErrorLogger.getInstance()
                    .logErrorWithExceptionInfo(
                            e,
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ODP);
        }
    }

    private void sendError(
            @NonNull IOnDevicePersonalizationConfigServiceCallback callback, int errorCode) {
        try {
            callback.onFailure(errorCode);
        } catch (RemoteException e) {
            sLogger.e(TAG + ": Callback error", e);
            ClientErrorLogger.getInstance()
                    .logErrorWithExceptionInfo(
                            e,
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ODP);
        }
    }

    private boolean getGlobalKillSwitch() {
        long origId = Binder.clearCallingIdentity();
        boolean globalKillSwitch = FlagsFactory.getFlags().getGlobalKillSwitch();
        Binder.restoreCallingIdentity(origId);
        return globalKillSwitch;
    }
}
