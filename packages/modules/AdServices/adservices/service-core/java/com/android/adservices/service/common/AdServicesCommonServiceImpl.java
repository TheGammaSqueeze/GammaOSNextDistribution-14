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

package com.android.adservices.service.common;

import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_STATE;
import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_STATE_COMPAT;
import static android.adservices.common.AdServicesPermissions.MODIFY_ADSERVICES_STATE;
import static android.adservices.common.AdServicesPermissions.MODIFY_ADSERVICES_STATE_COMPAT;
import static android.adservices.common.AdServicesPermissions.UPDATE_PRIVILEGED_AD_ID;
import static android.adservices.common.AdServicesPermissions.UPDATE_PRIVILEGED_AD_ID_COMPAT;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_MANIFEST_ADSERVICES_CONFIG_NO_PERMISSION;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_UNSET;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_KILLSWITCH_ENABLED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.ConsentStatus.SERVICE_NOT_ENABLED;

import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_DISABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_ENABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.KEY_ADSERVICES_ENTRY_POINT_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__COMMON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_ADSERVICES_COMMON_STATES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SERVICES_ENTRY_POINT_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX;
import static com.android.adservices.service.ui.constants.DebugMessages.BACK_COMPAT_FEATURE_ENABLED_MESSAGE;
import static com.android.adservices.service.ui.constants.DebugMessages.CALLER_NOT_ALLOWED_MESSAGE;
import static com.android.adservices.service.ui.constants.DebugMessages.ENABLE_AD_SERVICES_API_CALLED_MESSAGE;
import static com.android.adservices.service.ui.constants.DebugMessages.ENABLE_AD_SERVICES_API_DISABLED_MESSAGE;
import static com.android.adservices.service.ui.constants.DebugMessages.ENABLE_AD_SERVICES_API_ENABLED_MESSAGE;
import static com.android.adservices.service.ui.constants.DebugMessages.IS_AD_SERVICES_ENABLED_API_CALLED_MESSAGE;
import static com.android.adservices.service.ui.constants.DebugMessages.SET_AD_SERVICES_ENABLED_API_CALLED_MESSAGE;
import static com.android.adservices.service.ui.constants.DebugMessages.UNAUTHORIZED_CALLER_MESSAGE;

import android.adservices.adid.AdId;
import android.adservices.common.AdServicesCommonStates;
import android.adservices.common.AdServicesCommonStatesResponse;
import android.adservices.common.AdServicesStates;
import android.adservices.common.CallerMetadata;
import android.adservices.common.ConsentStatus;
import android.adservices.common.EnableAdServicesResponse;
import android.adservices.common.GetAdServicesCommonStatesParams;
import android.adservices.common.IAdServicesCommonCallback;
import android.adservices.common.IAdServicesCommonService;
import android.adservices.common.IAdServicesCommonStatesCallback;
import android.adservices.common.IEnableAdServicesCallback;
import android.adservices.common.IUpdateAdIdCallback;
import android.adservices.common.IsAdServicesEnabledResult;
import android.adservices.common.UpdateAdIdRequest;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.RemoteException;
import android.os.Trace;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adid.AdIdCacheManager;
import com.android.adservices.service.adid.AdIdWorker;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.consent.DeviceRegionProvider;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.service.ui.UxEngine;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.shared.util.Clock;

import java.util.Objects;
import java.util.concurrent.Executor;

import javax.annotation.Nonnull;

/**
 * Implementation of {@link IAdServicesCommonService}.
 *
 * @hide
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AdServicesCommonServiceImpl extends IAdServicesCommonService.Stub {

    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();
    public final String ADSERVICES_STATUS_SHARED_PREFERENCE = "AdserviceStatusSharedPreference";
    private final Context mContext;
    private final UxEngine mUxEngine;
    private final UxStatesManager mUxStatesManager;

    private final AdServicesLogger mAdServicesLogger;
    private final Flags mFlags;
    private final AdIdWorker mAdIdWorker;
    private final Clock mClock;

    public AdServicesCommonServiceImpl(
            Context context,
            Flags flags,
            UxEngine uxEngine,
            UxStatesManager uxStatesManager,
            AdIdWorker adIdWorker,
            AdServicesLogger adServicesLogger,
            @NonNull Clock clock) {
        mContext = context;
        mFlags = flags;
        mUxEngine = uxEngine;
        mUxStatesManager = uxStatesManager;
        mAdIdWorker = adIdWorker;
        mAdServicesLogger = adServicesLogger;
        mClock = clock;
    }

    @Override
    @RequiresPermission(anyOf = {ACCESS_ADSERVICES_STATE, ACCESS_ADSERVICES_STATE_COMPAT})
    public void isAdServicesEnabled(@NonNull IAdServicesCommonCallback callback) {
        LogUtil.d(IS_AD_SERVICES_ENABLED_API_CALLED_MESSAGE);

        boolean hasAccessAdServicesStatePermission =
                PermissionHelper.hasAccessAdServicesStatePermission(mContext);

        sBackgroundExecutor.execute(
                () -> {
                    try {
                        if (!hasAccessAdServicesStatePermission) {
                            LogUtil.e(UNAUTHORIZED_CALLER_MESSAGE);
                            callback.onFailure(STATUS_UNAUTHORIZED);
                            return;
                        }

                        boolean isAdServicesEnabled = mFlags.getAdServicesEnabled();
                        if (mFlags.isBackCompatActivityFeatureEnabled()) {
                            LogUtil.d(BACK_COMPAT_FEATURE_ENABLED_MESSAGE);
                            isAdServicesEnabled &=
                                    PackageManagerCompatUtils.isAdServicesActivityEnabled(mContext);
                        }

                        // TO-DO (b/286664178): remove the block after API is fully ramped up.
                        if (mFlags.getEnableAdServicesSystemApi()
                                && ConsentManager.getInstance().getUx() != null) {
                            LogUtil.d(ENABLE_AD_SERVICES_API_ENABLED_MESSAGE);
                            // PS entry point should be hidden from unenrolled users.
                            isAdServicesEnabled &= mUxStatesManager.isEnrolledUser(mContext);
                        } else {
                            LogUtil.d(ENABLE_AD_SERVICES_API_DISABLED_MESSAGE);
                            // Reconsent is already handled by the enableAdServices API.
                            reconsentIfNeededForEU();
                        }

                        LogUtil.d("isAdServiceseEnabled: " + isAdServicesEnabled);
                        callback.onResult(
                                new IsAdServicesEnabledResult.Builder()
                                        .setAdServicesEnabled(isAdServicesEnabled)
                                        .build());
                    } catch (Exception e) {
                        try {
                            callback.onFailure(STATUS_INTERNAL_ERROR);
                        } catch (RemoteException re) {
                            LogUtil.e(re, "Unable to send result to the callback");
                            ErrorLogUtil.e(
                                    re,
                                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
                        }
                    }
                });
    }

    /**
     * Set the adservices entry point Status from UI side, and also check adid zero-out status, and
     * Schedule notification if both adservices entry point enabled and adid not opt-out and
     * Adservice Is enabled
     */
    @Override
    @RequiresPermission(anyOf = {MODIFY_ADSERVICES_STATE, MODIFY_ADSERVICES_STATE_COMPAT})
    public void setAdServicesEnabled(boolean adServicesEntryPointEnabled, boolean adIdEnabled) {
        LogUtil.d(SET_AD_SERVICES_ENABLED_API_CALLED_MESSAGE);

        boolean hasModifyAdServicesStatePermission =
                PermissionHelper.hasModifyAdServicesStatePermission(mContext);

        sBackgroundExecutor.execute(
                () -> {
                    try {
                        if (!hasModifyAdServicesStatePermission) {
                            // TODO(b/242578032): handle the security exception in a better way
                            LogUtil.d(UNAUTHORIZED_CALLER_MESSAGE);
                            return;
                        }

                        SharedPreferences preferences =
                                mContext.getSharedPreferences(
                                        ADSERVICES_STATUS_SHARED_PREFERENCE, Context.MODE_PRIVATE);

                        int adServiceEntryPointStatusInt =
                                adServicesEntryPointEnabled
                                        ? ADSERVICES_ENTRY_POINT_STATUS_ENABLE
                                        : ADSERVICES_ENTRY_POINT_STATUS_DISABLE;
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt(
                                KEY_ADSERVICES_ENTRY_POINT_STATUS, adServiceEntryPointStatusInt);
                        if (!editor.commit()) {
                            LogUtil.e("saving to the sharedpreference failed");
                            ErrorLogUtil.e(
                                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE,
                                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
                        }
                        LogUtil.d(
                                "adid status is "
                                        + adIdEnabled
                                        + ", adservice status is "
                                        + mFlags.getAdServicesEnabled());
                        LogUtil.d("entry point: " + adServicesEntryPointEnabled);

                        ConsentManager consentManager = ConsentManager.getInstance();
                        consentManager.setAdIdEnabled(adIdEnabled);
                        if (mFlags.getAdServicesEnabled() && adServicesEntryPointEnabled) {
                            // Check if it is reconsent for ROW.
                            if (reconsentIfNeededForROW()) {
                                LogUtil.d("Reconsent for ROW.");
                                ConsentNotificationJobService.schedule(mContext, adIdEnabled, true);
                            } else if (getFirstConsentStatus()) {
                                ConsentNotificationJobService.schedule(
                                        mContext, adIdEnabled, false);
                            }

                            if (ConsentManager.getInstance().getConsent().isGiven()) {
                                PackageChangedReceiver.enableReceiver(mContext, mFlags);
                                BackgroundJobsManager.scheduleAllBackgroundJobs(mContext);
                            }
                        }
                    } catch (Exception e) {
                        LogUtil.e(
                                "unable to save the adservices entry point status of "
                                        + e.getMessage());
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SERVICES_ENTRY_POINT_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
                    }
                });
    }

    /** Init the AdServices Status Service. */
    public void init() {}

    /** Check EU device and reconsent logic and schedule the notification if needed. */
    public void reconsentIfNeededForEU() {
        boolean adserviceEnabled = mFlags.getAdServicesEnabled();
        if (adserviceEnabled
                && mFlags.getGaUxFeatureEnabled()
                && DeviceRegionProvider.isEuDevice(mContext, mFlags)) {
            // Check if GA UX was notice before
            ConsentManager consentManager = ConsentManager.getInstance();
            if (!consentManager.wasGaUxNotificationDisplayed()) {
                // Check Beta notification displayed and user opt-in, we will re-consent
                SharedPreferences preferences =
                        mContext.getSharedPreferences(
                                ADSERVICES_STATUS_SHARED_PREFERENCE, Context.MODE_PRIVATE);
                // Check the setAdServicesEnabled was called before
                if (preferences.contains(KEY_ADSERVICES_ENTRY_POINT_STATUS)
                        && consentManager.getConsent().isGiven()) {
                    ConsentNotificationJobService.schedule(mContext, false, true);
                }
            }
        }
    }

    /** Check if user is first time consent */
    public boolean getFirstConsentStatus() {
        ConsentManager consentManager = ConsentManager.getInstance();
        return (!consentManager.wasGaUxNotificationDisplayed()
                        && !consentManager.wasNotificationDisplayed())
                || mFlags.getConsentNotificationDebugMode();
    }

    /** Check ROW device and see if it fit reconsent */
    public boolean reconsentIfNeededForROW() {
        ConsentManager consentManager = ConsentManager.getInstance();
        return mFlags.getGaUxFeatureEnabled()
                && !DeviceRegionProvider.isEuDevice(mContext, mFlags)
                && !consentManager.wasGaUxNotificationDisplayed()
                && consentManager.wasNotificationDisplayed()
                && consentManager.getConsent().isGiven();
    }

    @Override
    @RequiresPermission(anyOf = {MODIFY_ADSERVICES_STATE, MODIFY_ADSERVICES_STATE_COMPAT})
    public void enableAdServices(
            @NonNull AdServicesStates adServicesStates,
            @NonNull IEnableAdServicesCallback callback) {
        LogUtil.d(ENABLE_AD_SERVICES_API_CALLED_MESSAGE);

        Trace.beginSection("AdServicesCommonService#EnableAdServices_PermissionCheck");
        boolean authorizedCaller = PermissionHelper.hasModifyAdServicesStatePermission(mContext);
        Trace.endSection();

        sBackgroundExecutor.execute(
                () -> {
                    try {
                        if (!authorizedCaller) {
                            callback.onFailure(STATUS_UNAUTHORIZED);
                            LogUtil.d(UNAUTHORIZED_CALLER_MESSAGE);
                            return;
                        }

                        // TO-DO (b/286664178): remove the block after API is fully ramped up.
                        if (!mFlags.getEnableAdServicesSystemApi()) {
                            callback.onResult(
                                    new EnableAdServicesResponse.Builder()
                                            .setStatusCode(STATUS_SUCCESS)
                                            .setApiEnabled(false)
                                            .setSuccess(false)
                                            .build());
                            LogUtil.d("enableAdServices(): API is disabled.");
                            return;
                        }

                        Trace.beginSection("AdServicesCommonService#EnableAdServices_UxEngineFlow");
                        mUxEngine.start(adServicesStates);
                        Trace.endSection();

                        LogUtil.d("enableAdServices(): UxEngine started.");

                        callback.onResult(
                                new EnableAdServicesResponse.Builder()
                                        .setStatusCode(STATUS_SUCCESS)
                                        .setApiEnabled(true)
                                        .setSuccess(true)
                                        .build());
                    } catch (Exception e) {
                        LogUtil.e("enableAdServices() failed to complete: " + e.getMessage());
                    }
                });
    }

    /**
     * Updates {@link AdId} cache in {@link AdIdCacheManager} when the device changes {@link AdId}.
     * This API is used by AdIdProvider to update the {@link AdId} Cache.
     */
    @Override
    @RequiresPermission(anyOf = {UPDATE_PRIVILEGED_AD_ID, UPDATE_PRIVILEGED_AD_ID_COMPAT})
    public void updateAdIdCache(
            @NonNull UpdateAdIdRequest updateAdIdRequest, @NonNull IUpdateAdIdCallback callback) {
        boolean authorizedCaller = PermissionHelper.hasUpdateAdIdCachePermission(mContext);
        int callerUid = Binder.getCallingUid();

        sBackgroundExecutor.execute(
                () -> {
                    try {
                        if (!mFlags.getAdIdCacheEnabled()) {
                            LogUtil.w("notifyAdIdChange() is disabled.");
                            callback.onFailure(STATUS_KILLSWITCH_ENABLED);
                            return;
                        }

                        if (!authorizedCaller) {
                            LogUtil.w(
                                    "Caller %d is not authorized to update AdId Cache!", callerUid);
                            callback.onFailure(STATUS_UNAUTHORIZED);
                            return;
                        }

                        mAdIdWorker.updateAdId(updateAdIdRequest);

                        // The message in on debugging purpose.
                        callback.onResult("Success");
                    } catch (Exception e) {
                        LogUtil.e(e, "updateAdIdCache() failed to complete.");
                    }
                });
    }

    /** add a new API for ODP to get the common states from the adservices service. */
    @Override
    @RequiresPermission(anyOf = {ACCESS_ADSERVICES_STATE, ACCESS_ADSERVICES_STATE_COMPAT})
    public void getAdServicesCommonStates(
            @Nonnull GetAdServicesCommonStatesParams param,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IAdServicesCommonStatesCallback callback) {
        Objects.requireNonNull(param);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(callback);
        final long serviceStartTime = mClock.elapsedRealtime();
        final String packageName = param.getAppPackageName();
        final String sdkName = param.getSdkPackageName();
        LogUtil.i(packageName);
        boolean hasAccessAdServicesCommonStatePermission =
                PermissionHelper.hasAccessAdServicesCommonStatePermission(mContext, packageName);

        sBackgroundExecutor.execute(
                () -> {
                    int resultCode = STATUS_SUCCESS;
                    int failureReason = FAILURE_REASON_UNSET;
                    try {
                        // Check permissions
                        if (!hasAccessAdServicesCommonStatePermission) {
                            LogUtil.e(UNAUTHORIZED_CALLER_MESSAGE);
                            resultCode = STATUS_UNAUTHORIZED;
                            failureReason = FAILURE_REASON_MANIFEST_ADSERVICES_CONFIG_NO_PERMISSION;
                            callback.onFailure(STATUS_UNAUTHORIZED);
                            return;
                        }
                        // Check package in allowlist
                        boolean appCanUseGetCommonStatesService =
                                AllowLists.isPackageAllowListed(
                                        mFlags.getAdServicesCommonStatesAllowList(),
                                        param.getAppPackageName());
                        if (!appCanUseGetCommonStatesService) {
                            LogUtil.e(CALLER_NOT_ALLOWED_MESSAGE);
                            resultCode = STATUS_CALLER_NOT_ALLOWED;
                            failureReason = FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST;
                            callback.onFailure(STATUS_CALLER_NOT_ALLOWED);
                            return;
                        }
                        if (mFlags.isGetAdServicesCommonStatesApiEnabled()) {
                            LogUtil.d("start getting states");
                            ConsentManager consentManager = ConsentManager.getInstance();
                            AdServicesCommonStates adservicesCommonStates =
                                    new AdServicesCommonStates.Builder()
                                            .setMeasurementState(
                                                    getConsentStatus(
                                                            consentManager,
                                                            AdServicesApiType.MEASUREMENTS))
                                            .setPaState(
                                                    getConsentStatus(
                                                            consentManager,
                                                            AdServicesApiType.FLEDGE))
                                            .build();
                            callback.onResult(
                                    new AdServicesCommonStatesResponse.Builder(
                                                    adservicesCommonStates)
                                            .build());
                            return;
                        }
                        LogUtil.d("service is not started");
                        AdServicesCommonStates adservicesCommonStates =
                                new AdServicesCommonStates.Builder()
                                        .setMeasurementState(SERVICE_NOT_ENABLED)
                                        .setPaState(SERVICE_NOT_ENABLED)
                                        .build();
                        callback.onResult(
                                new AdServicesCommonStatesResponse.Builder(adservicesCommonStates)
                                        .build());
                    } catch (Exception e) {
                        LogUtil.e("get error " + e.getMessage());
                        resultCode = STATUS_INTERNAL_ERROR;
                        failureReason = FAILURE_REASON_UNSET;
                        try {
                            callback.onFailure(STATUS_INTERNAL_ERROR);
                        } catch (RemoteException ex) {
                            LogUtil.e("Unable to send result to the callback " + ex.getMessage());
                        }
                    } finally {
                        mAdServicesLogger.logApiCallStats(
                                new ApiCallStats.Builder()
                                        .setCode(AdServicesStatsLog.AD_SERVICES_API_CALLED)
                                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__COMMON)
                                        .setApiName(
                                                AD_SERVICES_API_CALLED__API_NAME__GET_ADSERVICES_COMMON_STATES)
                                        .setAppPackageName(packageName)
                                        .setSdkPackageName(sdkName)
                                        .setLatencyMillisecond(
                                                getLatency(callerMetadata, serviceStartTime))
                                        .setResult(resultCode, failureReason)
                                        .build());
                    }
                });
    }

    private int getLatency(CallerMetadata metadata, long serviceStartTime) {
        long binderCallStartTimeMillis = metadata.getBinderElapsedTimestamp();
        long serviceLatency = mClock.elapsedRealtime() - serviceStartTime;
        // Double it to simulate the return binder time is same to call binder time
        long binderLatency = (serviceStartTime - binderCallStartTimeMillis) * 2;

        return (int) (serviceLatency + binderLatency);
    }

    private @ConsentStatus.ConsentStatusCode int getConsentStatus(
            ConsentManager consentManager, AdServicesApiType apiType) {
        if (apiType == AdServicesApiType.FLEDGE) {
            if (consentManager.isPasFledgeConsentGiven()) {
                return ConsentStatus.GIVEN;
            }
            return ConsentStatus.REVOKED;
        }
        if (apiType == AdServicesApiType.MEASUREMENTS) {
            if (consentManager.isPasMeasurementConsentGiven()) {
                return ConsentStatus.GIVEN;
            }
            return ConsentStatus.REVOKED;
        }
        return ConsentStatus.REVOKED;
    }
}
