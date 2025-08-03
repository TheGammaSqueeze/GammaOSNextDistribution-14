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
package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_CONSENT_MIGRATED__MIGRATION_STATUS__FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_CONSENT_MIGRATED__MIGRATION_STATUS__SUCCESS_WITH_SHARED_PREF_NOT_UPDATED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_CONSENT_MIGRATED__MIGRATION_STATUS__SUCCESS_WITH_SHARED_PREF_UPDATED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_CONSENT_MIGRATED__MIGRATION_STATUS__UNSPECIFIED_MIGRATION_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_CONSENT_MIGRATED__MIGRATION_TYPE__ADEXT_SERVICE_TO_APPSEARCH;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_CONSENT_MIGRATED__MIGRATION_TYPE__ADEXT_SERVICE_TO_SYSTEM_SERVICE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_CONSENT_MIGRATED__MIGRATION_TYPE__APPSEARCH_TO_SYSTEM_SERVICE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_CONSENT_MIGRATED__MIGRATION_TYPE__PPAPI_TO_SYSTEM_SERVICE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_CONSENT_MIGRATED__MIGRATION_TYPE__UNSPECIFIED_MIGRATION_TYPE;

import com.google.auto.value.AutoValue;
/***
 * Class for AdServicesConsentMigrationEvent atom
 *
 *  Please see go/rb-consent-migration-metrics for more details.
 */
@AutoValue
public abstract class ConsentMigrationStats {
    /**
     * @return type of consent migration
     */
    public abstract MigrationType getMigrationType();
    /**
     * @return status of consent migration
     */
    public abstract MigrationStatus getMigrationStatus();
    /**
     * @return opt in/out value of measuremnt app
     */
    public abstract boolean getMsmtConsent();
    /**
     * @return opt in/out value of topics app
     */
    public abstract boolean getTopicsConsent();
    /**
     * @return opt in/out value of fledge app
     */
    public abstract boolean getFledgeConsent();
    /***
     *
     * @return region of OTA
     */
    public abstract int getRegion();
    /**
     * @return generic builder.
     */
    public static ConsentMigrationStats.Builder builder() {
        return new AutoValue_ConsentMigrationStats.Builder();
    }

    public enum MigrationType {
        UNSPECIFIED_MIGRATION_TYPE(
                AD_SERVICES_CONSENT_MIGRATED__MIGRATION_TYPE__UNSPECIFIED_MIGRATION_TYPE),
        // Migrating consent from PPAPI to system service
        PPAPI_TO_SYSTEM_SERVICE(
                AD_SERVICES_CONSENT_MIGRATED__MIGRATION_TYPE__PPAPI_TO_SYSTEM_SERVICE),
        // Migrating consent from App Search to system service
        APPSEARCH_TO_SYSTEM_SERVICE(
                AD_SERVICES_CONSENT_MIGRATED__MIGRATION_TYPE__APPSEARCH_TO_SYSTEM_SERVICE),
        // Migrating consent from AdServicesExtDataStorageService to System Server
        ADEXT_SERVICE_TO_SYSTEM_SERVICE(
                AD_SERVICES_CONSENT_MIGRATED__MIGRATION_TYPE__ADEXT_SERVICE_TO_SYSTEM_SERVICE),
        // Migrating consent from AdServicesExtDataStorageService to App Search
        ADEXT_SERVICE_TO_APPSEARCH(
                AD_SERVICES_CONSENT_MIGRATED__MIGRATION_TYPE__ADEXT_SERVICE_TO_APPSEARCH);

        private final int mMigrationType;

        MigrationType(int migrationType) {
            this.mMigrationType = migrationType;
        }
        /**
         * @return Autogen enum logging value for migrationType in AdServicesConsentMigrated atom
         */
        public int getMigrationTypeValue() {
            return mMigrationType;
        }
    }
    // Logs the Migration status
    public enum MigrationStatus {
        UNSPECIFIED_MIGRATION_STATUS(
                AD_SERVICES_CONSENT_MIGRATED__MIGRATION_STATUS__UNSPECIFIED_MIGRATION_STATUS),
        // Consent migration unsuccessful
        FAILURE(AD_SERVICES_CONSENT_MIGRATED__MIGRATION_STATUS__FAILURE),
        // Consent migration successful with shared prefs updated
        SUCCESS_WITH_SHARED_PREF_UPDATED(
                AD_SERVICES_CONSENT_MIGRATED__MIGRATION_STATUS__SUCCESS_WITH_SHARED_PREF_UPDATED),
        // Consent migration successful with shared prefs not updated
        SUCCESS_WITH_SHARED_PREF_NOT_UPDATED(
                AD_SERVICES_CONSENT_MIGRATED__MIGRATION_STATUS__SUCCESS_WITH_SHARED_PREF_NOT_UPDATED);
        private final int mMigrationStatus;

        MigrationStatus(int migrationStatus) {
            this.mMigrationStatus = migrationStatus;
        }
        /**
         * @return Autogen enum logging value for migrationStatus in AdServicesConsentMigrated atom
         */
        public int getMigrationStatusValue() {
            return mMigrationStatus;
        }
    }
    /** Builder class for {@link ConsentMigrationStats}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set type of consent migration */
        public abstract Builder setMigrationType(MigrationType value);
        /** Set status of consent migration */
        public abstract Builder setMigrationStatus(MigrationStatus value);
        /** Set opt in/out value of measurement app */
        public abstract Builder setMsmtConsent(boolean value);
        /** Set opt in/out value of topics app */
        public abstract Builder setTopicsConsent(boolean value);
        /** Set opt in/out value of fledge app */
        public abstract Builder setFledgeConsent(boolean value);
        /** Set region of OTA */
        public abstract Builder setRegion(int value);
        /** build for {@link ConsentMigrationStats}. */
        public abstract ConsentMigrationStats build();
    }
}
