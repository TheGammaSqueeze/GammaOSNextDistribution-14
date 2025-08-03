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

package com.android.adservices.service.extdata;

import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_ADULT_ACCOUNT;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_MEASUREMENT_CONSENTED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_NOTIFICATION_DISPLAYED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_U18_ACCOUNT;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION;

import static com.android.adservices.service.measurement.rollback.MeasurementRollbackCompatManager.APEX_VERSION_WHEN_NOT_FOUND;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.extdata.AdServicesExtDataParams;
import android.adservices.extdata.AdServicesExtDataStorageService;
import android.annotation.NonNull;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.adservices.LogUtil;
import com.android.adservices.service.common.compat.FileCompatUtils;

import java.util.Objects;

/**
 * Proxy class for AdServicesExtDataStorageService for testing when the actual service is
 * unavailable. It is meant for debugging only and not to replace the actual service.
 */
public final class AdServicesExtDataStorageServiceDebugProxy {

    private static final String KEY_EXT_ADEXT_SERVICE_PROXY_PREFS =
            "adext_storage_service_proxy_prefs";
    private static final String KEY_FIELD_IS_NOTIFICATION_DISPLAYED = "is_notification_displayed";
    private static final String KEY_FIELD_IS_MEASUREMENT_CONSENTED = "is_measurement_consented";
    private static final String KEY_FIELD_IS_U18_ACCOUNT = "is_u18_account";
    private static final String KEY_FIELD_IS_ADULT_ACCOUNT = "is_adult_account";
    private static final String KEY_FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS =
            "manual_interaction_with_consent_status";
    private static final String KEY_MEASUREMENT_ROLLBACK_APEX_VERSION =
            "measurement_rollback_apex_version";

    private final SharedPreferences mSharedPreferences;

    private AdServicesExtDataStorageServiceDebugProxy(Context context) {
        mSharedPreferences =
                FileCompatUtils.getSharedPreferencesHelper(
                        context,
                        FileCompatUtils.getAdservicesFilename(KEY_EXT_ADEXT_SERVICE_PROXY_PREFS),
                        Context.MODE_PRIVATE);
    }

    /** Gets a instance of {@link AdServicesExtDataStorageServiceDebugProxy}. */
    public static AdServicesExtDataStorageServiceDebugProxy getInstance(Context context) {
        return new AdServicesExtDataStorageServiceDebugProxy(context);
    }

    /**
     * Sets data in shared prefs to update the desired fields.
     *
     * @param params data object that holds the values to be updated.
     * @param fieldsToUpdate explicit list of field IDs that correspond to the fields from params,
     *     that are to be updated.
     * @param callback callback to return result for confirming status of operation.
     */
    public void setAdServicesExtData(
            @NonNull AdServicesExtDataParams params,
            @NonNull @AdServicesExtDataStorageService.AdServicesExtDataFieldId int[] fieldsToUpdate,
            @NonNull AdServicesOutcomeReceiver<AdServicesExtDataParams, Exception> callback) {
        Objects.requireNonNull(params);
        Objects.requireNonNull(fieldsToUpdate);
        Objects.requireNonNull(callback);
        try {
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            for (int field : fieldsToUpdate) {
                switch (field) {
                    case FIELD_IS_NOTIFICATION_DISPLAYED -> editor.putInt(
                            KEY_FIELD_IS_NOTIFICATION_DISPLAYED,
                            params.getIsNotificationDisplayed());
                    case FIELD_IS_MEASUREMENT_CONSENTED -> editor.putInt(
                            KEY_FIELD_IS_MEASUREMENT_CONSENTED, params.getIsMeasurementConsented());
                    case FIELD_IS_U18_ACCOUNT -> editor.putInt(
                            KEY_FIELD_IS_U18_ACCOUNT, params.getIsU18Account());
                    case FIELD_IS_ADULT_ACCOUNT -> editor.putInt(
                            KEY_FIELD_IS_ADULT_ACCOUNT, params.getIsAdultAccount());
                    case FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS -> editor.putInt(
                            KEY_FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS,
                            params.getManualInteractionWithConsentStatus());
                    case FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION -> editor.putLong(
                            KEY_FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS,
                            params.getMeasurementRollbackApexVersion());
                    default -> throw new IllegalArgumentException("Invalid field " + field);
                }
            }
            editor.apply();
            callback.onResult(params);
        } catch (Exception e) {
            LogUtil.e(
                    "Exception in AdServicesExtDataStorageServiceProxy setAdServicesExtData %s",
                    e.getMessage());
            callback.onError(e);
        }
    }

    /**
     * Gets data from shared prefs to fetch AdExt data.
     *
     * @param callback to return result.
     */
    public void getAdServicesExtData(
            @NonNull AdServicesOutcomeReceiver<AdServicesExtDataParams, Exception> callback) {
        Objects.requireNonNull(callback);
        try {
            AdServicesExtDataParams adServicesExtDataParams =
                    new AdServicesExtDataParams.Builder()
                            .setNotificationDisplayed(
                                    mSharedPreferences.getInt(
                                            KEY_FIELD_IS_NOTIFICATION_DISPLAYED, BOOLEAN_UNKNOWN))
                            .setMsmtConsent(
                                    mSharedPreferences.getInt(
                                            KEY_FIELD_IS_MEASUREMENT_CONSENTED, BOOLEAN_UNKNOWN))
                            .setIsU18Account(
                                    mSharedPreferences.getInt(
                                            KEY_FIELD_IS_U18_ACCOUNT, BOOLEAN_UNKNOWN))
                            .setIsAdultAccount(
                                    mSharedPreferences.getInt(
                                            KEY_FIELD_IS_ADULT_ACCOUNT, BOOLEAN_UNKNOWN))
                            .setManualInteractionWithConsentStatus(
                                    mSharedPreferences.getInt(
                                            KEY_FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS,
                                            STATE_UNKNOWN))
                            .setMsmtRollbackApexVersion(
                                    mSharedPreferences.getLong(
                                            KEY_MEASUREMENT_ROLLBACK_APEX_VERSION,
                                            APEX_VERSION_WHEN_NOT_FOUND))
                            .build();
            callback.onResult(adServicesExtDataParams);
        } catch (Exception e) {
            LogUtil.e(
                    "Exception in AdServicesExtDataStorageServiceProxy getAdServicesExtData %s",
                    e.getMessage());
            callback.onError(e);
        }
    }
}
