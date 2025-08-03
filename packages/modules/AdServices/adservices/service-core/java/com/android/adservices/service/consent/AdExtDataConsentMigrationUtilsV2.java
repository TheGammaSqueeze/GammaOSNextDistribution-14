/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.consent;

import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_TRUE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_MANUAL_INTERACTIONS_RECORDED;

import static com.android.adservices.AdServicesCommon.ADSERVICES_APK_PACKAGE_NAME_SUFFIX;
import static com.android.adservices.service.consent.ConsentManager.getConsentManagerStatsForLogging;

import android.adservices.extdata.AdServicesExtDataParams;
import android.annotation.NonNull;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.service.appsearch.AppSearchConsentStorageManager;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.adservices.service.extdata.AdServicesExtDataStorageServiceManager;
import com.android.adservices.service.stats.ConsentMigrationStats;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.modules.utils.build.SdkLevel;

import java.io.IOException;
import java.util.Objects;

/**
 * Utility methods for consent migration from AdExtDataStorage (Android R) to AppSearch (Android S)
 * and System Server (Android T+) for ConsentManagerV2 paradigm.
 */
public class AdExtDataConsentMigrationUtilsV2 {
    private AdExtDataConsentMigrationUtilsV2() {
        // prevent instantiations
    }

    /**
     * This method handles migration of consent data from AdExtDataStorageService to either
     * AppSearch or System Server based on SDK level. If any new data is written for consent, we
     * need to make sure it is migrated correctly post-OTA in this method.
     */
    public static void handleConsentMigrationFromAdExtDataIfNeededV2(
            @NonNull Context context,
            @Nullable AppSearchConsentStorageManager appSearchConsentManager,
            @Nullable AdServicesExtDataStorageServiceManager adExtDataManager,
            @NonNull StatsdAdServicesLogger statsdAdServicesLogger,
            @Nullable AdServicesStorageManager adServicesManager) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(statsdAdServicesLogger);

        AppConsents appConsents = null;
        try {
            SharedPreferences sharedPreferences =
                    FileCompatUtils.getSharedPreferencesHelper(
                            context, ConsentConstants.SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);

            LogUtil.d("Check if consent migration from AdExtData is needed.");
            IConsentStorage transferStorage =
                    SdkLevel.isAtLeastT() ? adServicesManager : appSearchConsentManager;
            if (!isMigrationFromAdExtDataNeededV2(
                    context, sharedPreferences, transferStorage, adExtDataManager)) {
                LogUtil.d("Skipping consent migration from AdExtData");
                return;
            }

            // Reduce number of read calls by fetching all the AdExt data at once.
            AdServicesExtDataParams dataFromR = adExtDataManager.getAdServicesExtData();
            if (dataFromR == null || dataFromR.getIsNotificationDisplayed() != BOOLEAN_TRUE) {
                LogUtil.d("Skipping consent migration from AdExtData; notification not shown on R");
                return;
            }

            appConsents = migrateAdExtDataV2(transferStorage, dataFromR);

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(getSharedPrefMigrationKey(), true);
            if (editor.commit()) {
                LogUtil.d("Finished migrating consent from AdExtData.");
                logMigrationStatus(
                        statsdAdServicesLogger,
                        appConsents,
                        ConsentMigrationStats.MigrationStatus.SUCCESS_WITH_SHARED_PREF_UPDATED,
                        context);
            } else {
                LogUtil.e("Finished migrating consent from AdExtData. Shared prefs not updated.");
                logMigrationStatus(
                        statsdAdServicesLogger,
                        appConsents,
                        ConsentMigrationStats.MigrationStatus.SUCCESS_WITH_SHARED_PREF_NOT_UPDATED,
                        context);
            }

            // No longer need access to Android R data. Safe to clear here.
            adExtDataManager.clearDataOnOtaAsync();
        } catch (Exception e) {
            LogUtil.e("Consent migration from AdExtData failed: ", e);
            logMigrationStatus(
                    statsdAdServicesLogger,
                    appConsents,
                    ConsentMigrationStats.MigrationStatus.FAILURE,
                    context);
        }
    }

    private static boolean isMigrationFromAdExtDataNeededV2(
            Context context,
            SharedPreferences sharedPreferences,
            IConsentStorage transferStorage,
            AdServicesExtDataStorageServiceManager adExtDataManager)
            throws IOException {
        if (!SdkLevel.isAtLeastS()) {
            LogUtil.d("Not S+ device. Consent migration from AdExtData not needed");
            return false;
        }

        // Ensure appropriate source of truth data store is non-null.
        Objects.requireNonNull(transferStorage);

        // There could be a case where we may need to ramp down enable_adext_service_consent_data
        // flag on S+, in which case we should gracefully handle consent migration by skipping.
        if (adExtDataManager == null) {
            LogUtil.d("AdExtDataManager is null. Consent migration from AdExtData not needed");
            return false;
        }

        // On T+, this migration should only execute if it's within the AdServices APK and not
        // ExtServices. So check if it's within ExtServices, and bail out if that's the case on
        // any platform.
        if (SdkLevel.isAtLeastT() && !isValidAdServicesPackage(context)) {
            LogUtil.d("Aborting attempt to migrate AdExtData to System Server in ExtServices");
            return false;
        }

        // If any migration was marked as being done, this migration no longer needs to take place.
        if (isMigrationDone(sharedPreferences)) {
            LogUtil.d(
                    "Consent migration already done for user %d.",
                    context.getUser().getIdentifier());
            return false;
        }

        // Just in case, check all notification types to ensure notification is not shown on
        // current SDK version. We do not want to override consent if notification is already shown.
        boolean isNotificationDisplayed = isNotificationShownOnCurrentSdkV2(transferStorage);
        LogUtil.d("Notification shown status on current SDK version: " + isNotificationDisplayed);

        // If notification is not shown, we will need to perform another check to ensure
        // notification was shown on R before performing migration. This check will be performed
        // later in order to reduce number of calls to AdExtDataService in the consent migration
        // process.
        return !isNotificationDisplayed;
    }

    private static AppConsents migrateAdExtDataV2(
            IConsentStorage transferStorage, AdServicesExtDataParams dataFromR) throws IOException {
        // Migrate measurement consent
        boolean isMeasurementConsented = dataFromR.getIsMeasurementConsented() == BOOLEAN_TRUE;
        transferStorage.setConsent(AdServicesApiType.MEASUREMENTS, isMeasurementConsented);

        // Migrate U18 notification displayed status
        boolean u18NotificationDisplayed = dataFromR.getIsNotificationDisplayed() == BOOLEAN_TRUE;
        transferStorage.setU18NotificationDisplayed(u18NotificationDisplayed);

        // Migrate isU18Account
        int isU18AccountRawValue = dataFromR.getIsU18Account();
        if (isU18AccountRawValue != BOOLEAN_UNKNOWN) {
            boolean isU18Account = isU18AccountRawValue == BOOLEAN_TRUE;
            transferStorage.setU18Account(isU18Account);
        }

        // Migrate isAdultAccount
        int isAdultAccountRawValue = dataFromR.getIsAdultAccount();
        if (isAdultAccountRawValue != BOOLEAN_UNKNOWN) {
            boolean isAdultAccount = isAdultAccountRawValue == BOOLEAN_TRUE;
            transferStorage.setAdultAccount(isAdultAccount);
        }

        // Migrate interaction data only if we recorded an interaction
        int manualInteractionRecorded = dataFromR.getManualInteractionWithConsentStatus();
        if (manualInteractionRecorded == STATE_MANUAL_INTERACTIONS_RECORDED) {
            transferStorage.recordUserManualInteractionWithConsent(manualInteractionRecorded);
        }

        // Logging false for fledge and topics consent by default because only measurement is
        // supported on R.
        AppConsents appConsents =
                AppConsents.builder()
                        .setMsmtConsent(isMeasurementConsented)
                        .setFledgeConsent(false)
                        .setTopicsConsent(false)
                        .build();
        return appConsents;
    }

    @TargetApi(Build.VERSION_CODES.S)
    private static void logMigrationStatus(
            StatsdAdServicesLogger statsdAdServicesLogger,
            AppConsents appConsents,
            ConsentMigrationStats.MigrationStatus migrationStatus,
            Context context) {
        statsdAdServicesLogger.logConsentMigrationStats(
                getConsentManagerStatsForLogging(
                        appConsents,
                        migrationStatus,
                        SdkLevel.isAtLeastT()
                                ? ConsentMigrationStats.MigrationType
                                .ADEXT_SERVICE_TO_SYSTEM_SERVICE
                                : ConsentMigrationStats.MigrationType.ADEXT_SERVICE_TO_APPSEARCH,
                        context));
    }

    private static boolean isValidAdServicesPackage(Context context) {
        String packageName = context.getPackageName();
        return packageName != null && packageName.endsWith(ADSERVICES_APK_PACKAGE_NAME_SUFFIX);
    }

    private static boolean isMigrationDone(SharedPreferences sharedPreferences) {
        return sharedPreferences.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED,
                        /* defValue= */ false)
                || sharedPreferences.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED, /* defValue= */ false)
                || sharedPreferences.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED_TO_APP_SEARCH,
                        /* defValue= */ false)
                || sharedPreferences.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_MIGRATED_FROM_ADEXTDATA_TO_SYSTEM_SERVER,
                        /* defValue= */ false);
    }

    private static boolean isNotificationShownOnCurrentSdkV2(IConsentStorage storage)
            throws IOException {
        return storage.wasU18NotificationDisplayed()
                || storage.wasNotificationDisplayed()
                || storage.wasGaUxNotificationDisplayed();
    }

    private static String getSharedPrefMigrationKey() {
        return SdkLevel.isAtLeastT()
                ? ConsentConstants.SHARED_PREFS_KEY_MIGRATED_FROM_ADEXTDATA_TO_SYSTEM_SERVER
                : ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED_TO_APP_SEARCH;
    }
}
