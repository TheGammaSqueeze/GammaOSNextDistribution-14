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

package com.android.ondevicepersonalization.services.data.user;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesCommonStates;
import android.adservices.common.AdServicesCommonStatesResponse;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.annotation.NonNull;
import android.content.Context;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationApplication;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.PhFlags;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * A singleton class that stores all user privacy statuses in memory.
 */
public final class UserPrivacyStatus {
    private static final String TAG = "UserPrivacyStatus";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    @VisibleForTesting
    static final int CONSENT_GIVEN_STATUS_CODE = 3;
    @VisibleForTesting
    static final int CONSENT_REVOKED_STATUS_CODE = 2;
    static UserPrivacyStatus sUserPrivacyStatus = null;
    private boolean mPersonalizationStatusEnabled;
    private boolean mProtectedAudienceEnabled;
    private boolean mMeasurementEnabled;
    private long mLastUserConsentCacheUpdate;

    private enum CommonState {
        PROTECTED_AUDIENCE,
        MEASUREMENT
    }

    private UserPrivacyStatus() {
        // Assume the more privacy-safe option until updated.
        mPersonalizationStatusEnabled = false;
        mProtectedAudienceEnabled = false;
        mMeasurementEnabled = false;
        mLastUserConsentCacheUpdate = -1L;
    }

    /** Returns an instance of UserPrivacyStatus. */
    public static UserPrivacyStatus getInstance() {
        synchronized (UserPrivacyStatus.class) {
            if (sUserPrivacyStatus == null) {
                sUserPrivacyStatus = new UserPrivacyStatus();
            }
            return sUserPrivacyStatus;
        }
    }

    public void setPersonalizationStatusEnabled(boolean personalizationStatusEnabled) {
        PhFlags phFlags = PhFlags.getInstance();
        if (!phFlags.isPersonalizationStatusOverrideEnabled()) {
            mPersonalizationStatusEnabled = personalizationStatusEnabled;
        }
    }

    public boolean isPersonalizationStatusEnabled() {
        PhFlags phFlags = PhFlags.getInstance();
        if (phFlags.isPersonalizationStatusOverrideEnabled()) {
            return phFlags.getPersonalizationStatusOverrideValue();
        }
        return mPersonalizationStatusEnabled;
    }

    /**
     * Returns the user consent status of Protected Audience (PA).
     */
    public boolean isProtectedAudienceEnabled() {
        if (isUserConsentCacheValid()) {
            return mProtectedAudienceEnabled;
        }
        // make request to AdServices#getCommonStates API.
        return fetchStateFromAdServices(CommonState.PROTECTED_AUDIENCE);
    }

    /**
     * Returns the user consent status of Measurement.
     */
    public boolean isMeasurementEnabled() {
        if (isUserConsentCacheValid()) {
            return mMeasurementEnabled;
        }
        // make request to AdServices#getCommonStates API.
        return fetchStateFromAdServices(CommonState.MEASUREMENT);
    }

    /**
     * Update user consent cache and timestamp metadata.
     */
    @VisibleForTesting
    void updateUserConsentCache(boolean protectedAudienceEnabled,
            boolean measurementEnabled) {
        mProtectedAudienceEnabled = protectedAudienceEnabled;
        mMeasurementEnabled = measurementEnabled;
        mLastUserConsentCacheUpdate = System.currentTimeMillis();
    }

    /**
     * Returns whether the user consent cache remains valid.
     */
    @VisibleForTesting
    boolean isUserConsentCacheValid() {
        if (mLastUserConsentCacheUpdate == -1L) {
            return false;
        }
        long cacheDuration = System.currentTimeMillis() - mLastUserConsentCacheUpdate;
        return cacheDuration >= 0
                        && cacheDuration < PhFlags.getInstance().getUserConsentCacheInMillis();
    }

    /**
     * Reset user consent info for testing.
     */
    @VisibleForTesting
    void resetUserConsentForTesting() {
        mProtectedAudienceEnabled = false;
        mMeasurementEnabled = false;
        mLastUserConsentCacheUpdate = -1L;
    }

    /**
     * Invalidate the user consent cache for testing.
     */
    @VisibleForTesting
    void invalidateUserConsentCacheForTesting() {
        mLastUserConsentCacheUpdate = System.currentTimeMillis()
                        - 2 * PhFlags.getInstance().getUserConsentCacheInMillis();
    }

    private boolean fetchStateFromAdServices(CommonState state) {
        try {
            // IPC.
            AdServicesCommonManager adServicesCommonManager = getAdServicesCommonManager();
            AdServicesCommonStates commonStates =
                            getAdServicesCommonStates(adServicesCommonManager);

            // update cache.
            boolean updatedProtectedAudienceEnabled =
                    (commonStates.getPaState() == CONSENT_GIVEN_STATUS_CODE);
            boolean updatedMeasurementEnabled =
                    (commonStates.getMeasurementState() == CONSENT_GIVEN_STATUS_CODE);
            updateUserConsentCache(updatedProtectedAudienceEnabled, updatedMeasurementEnabled);

            // return requested common state.
            switch (state) {
                case PROTECTED_AUDIENCE -> {
                    return updatedProtectedAudienceEnabled;
                }
                case MEASUREMENT -> {
                    return updatedMeasurementEnabled;
                }
            }
            return false;
        } catch (Exception e) {
            sLogger.e(TAG + ": fetchStateFromAdServices error", e);
            return false;
        }
    }

    /**
     * Get AdServices common manager from ODP.
     */
    private static AdServicesCommonManager getAdServicesCommonManager() {
        Context odpContext = OnDevicePersonalizationApplication.getAppContext();
        try {
            return odpContext.getSystemService(AdServicesCommonManager.class);
        } catch (NoClassDefFoundError e) {
            throw new IllegalStateException("Cannot find AdServicesCommonManager.", e);
        }
    }

    /**
     * Get common states from AdServices, such as user consent.
     */
    private AdServicesCommonStates getAdServicesCommonStates(
                    @NonNull AdServicesCommonManager adServicesCommonManager) {
        ListenableFuture<AdServicesCommonStatesResponse> response =
                        getAdServicesResponse(adServicesCommonManager);
        try {
            return response.get().getAdServicesCommonStates();
        } catch (Exception e) {
            throw new IllegalStateException("Failed when calling "
                    + "AdServicesCommonManager#getAdServicesCommonStates().", e);
        }
    }

    /**
     * IPC to AdServices API.
     */
    private ListenableFuture<AdServicesCommonStatesResponse> getAdServicesResponse(
                    @NonNull AdServicesCommonManager adServicesCommonManager) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    adServicesCommonManager.getAdservicesCommonStates(
                            OnDevicePersonalizationExecutors.getBackgroundExecutor(),
                            new AdServicesOutcomeReceiver<AdServicesCommonStatesResponse,
                                    Exception>() {
                                @Override
                                public void onResult(AdServicesCommonStatesResponse result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // For debugging purpose only.
                    return "getAdServicesCommonStates";
                }
        );
    }
}
