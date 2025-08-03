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

import android.adservices.common.KeyedFrequencyCap;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.AdSelectionDebugReportingDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.EncryptionContextDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.kanon.KAnonDatabase;
import com.android.adservices.data.kanon.KAnonMessageDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.InteractionReportingTableClearedStats;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Utility class to perform Fledge maintenance tasks */
public class FledgeMaintenanceTasksWorker {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final AdSelectionDebugReportDao mAdSelectionDebugReportDao;
    @NonNull private final FrequencyCapDao mFrequencyCapDao;
    @NonNull private final EnrollmentDao mEnrollmentDao;
    @NonNull private final EncryptionContextDao mEncryptionContextDao;
    @NonNull private final Flags mFlags;
    @NonNull private final Clock mClock;
    @NonNull private final KAnonMessageDao mKAnonMessageDao;

    @NonNull private final AdServicesLogger mAdServicesLogger;

    @VisibleForTesting
    public FledgeMaintenanceTasksWorker(
            @NonNull Flags flags,
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull FrequencyCapDao frequencyCapDao,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull EncryptionContextDao encryptionContextDao,
            @NonNull AdSelectionDebugReportDao adSelectionDebugReportDao,
            @NonNull Clock clock,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull KAnonMessageDao kAnonMessageDao) {
        Objects.requireNonNull(flags);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(frequencyCapDao);
        Objects.requireNonNull(enrollmentDao);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(encryptionContextDao);
        Objects.requireNonNull(adSelectionDebugReportDao);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(kAnonMessageDao);

        mFlags = flags;
        mAdSelectionEntryDao = adSelectionEntryDao;
        mFrequencyCapDao = frequencyCapDao;
        mEnrollmentDao = enrollmentDao;
        mEncryptionContextDao = encryptionContextDao;
        mClock = clock;
        mAdSelectionDebugReportDao = adSelectionDebugReportDao;
        mAdServicesLogger = adServicesLogger;
        mKAnonMessageDao = kAnonMessageDao;
    }

    private FledgeMaintenanceTasksWorker(@NonNull Context context) {
        Objects.requireNonNull(context);
        mFlags = FlagsFactory.getFlags();
        mAdSelectionEntryDao = AdSelectionDatabase.getInstance(context).adSelectionEntryDao();
        mFrequencyCapDao = SharedStorageDatabase.getInstance(context).frequencyCapDao();
        mEnrollmentDao = EnrollmentDao.getInstance(context);
        mEncryptionContextDao =
                AdSelectionServerDatabase.getInstance(context).encryptionContextDao();
        mClock = Clock.systemUTC();
        mAdSelectionDebugReportDao =
                AdSelectionDebugReportingDatabase.getInstance(context)
                        .getAdSelectionDebugReportDao();
        mAdServicesLogger = StatsdAdServicesLogger.getInstance();
        mKAnonMessageDao = KAnonDatabase.getInstance(context).kAnonMessageDao();
    }

    /** Creates a new instance of {@link FledgeMaintenanceTasksWorker}. */
    public static FledgeMaintenanceTasksWorker create(@NonNull Context context) {
        Objects.requireNonNull(context);
        return new FledgeMaintenanceTasksWorker(context);
    }

    /**
     * Clears all entries in the {@code ad_selection} table that are older than {@code
     * expirationTime}. Then, clears all expired entries in the {@code buyer_decision_logic} as well
     * as the {@code registered_ad_interactions} table.
     */
    public void clearExpiredAdSelectionData() {
        // Read from flags directly, since this maintenance task worker is attached to a background
        //  job with unknown lifetime
        Instant expirationTime =
                mClock.instant().minusSeconds(mFlags.getAdSelectionExpirationWindowS());

        sLogger.v("Clearing expired Ad Selection data");
        mAdSelectionEntryDao.removeExpiredAdSelection(expirationTime);

        sLogger.v("Clearing expired Buyer Decision Logic data ");
        mAdSelectionEntryDao.removeExpiredBuyerDecisionLogic();

        if (mFlags.getFledgeBeaconReportingMetricsEnabled()) {
            long numUrisBeforeClearing = mAdSelectionEntryDao.getTotalNumRegisteredAdInteractions();

            sLogger.v("Clearing expired Registered Ad Interaction data ");
            mAdSelectionEntryDao.removeExpiredRegisteredAdInteractions();

            long numUrisAfterClearing = mAdSelectionEntryDao.getTotalNumRegisteredAdInteractions();

            mAdServicesLogger.logInteractionReportingTableClearedStats(
                    InteractionReportingTableClearedStats.builder()
                            .setNumUrisCleared((int) (numUrisAfterClearing - numUrisBeforeClearing))
                            .build());
        } else {
            sLogger.v("Clearing expired Registered Ad Interaction data ");
            mAdSelectionEntryDao.removeExpiredRegisteredAdInteractions();
        }

        if (mFlags.getFledgeAuctionServerEnabled()
                || mFlags.getFledgeOnDeviceAuctionShouldUseUnifiedTables()) {
            sLogger.v("Clearing expired Ad Selection Initialization data");
            mAdSelectionEntryDao.removeExpiredAdSelectionInitializations(expirationTime);
        }

        if (mFlags.getFledgeOnDeviceAuctionShouldUseUnifiedTables()) {
            sLogger.v("Clearing expired Registered Ad Interaction data from unified table ");
            mAdSelectionEntryDao.removeExpiredRegisteredAdInteractionsFromUnifiedTable();
        }

        if (mFlags.getFledgeAuctionServerEnabled()) {
            sLogger.v("Clearing expired Encryption Context");
            mEncryptionContextDao.removeExpiredEncryptionContext(expirationTime);
        }

        if (mFlags.getFledgeEventLevelDebugReportingEnabled()) {
            sLogger.v("Clearing expired debug reports ");
            mAdSelectionDebugReportDao.deleteDebugReportsBeforeTime(expirationTime);
        }
    }

    /**
     * Clears invalid histogram data from the frequency cap database if the ad filtering feature is
     * enabled.
     *
     * <p>Invalid histogram data includes:
     *
     * <ul>
     *   <li>Expired histogram data
     *   <li>Disallowed buyer histogram data
     *   <li>Disallowed source app histogram data
     *   <li>Uninstalled source app histogram data
     * </ul>
     */
    public void clearInvalidFrequencyCapHistogramData(@NonNull PackageManager packageManager) {
        Objects.requireNonNull(packageManager);

        // Read from flags directly, since this maintenance task worker is attached to a background
        //  job with unknown lifetime
        if (!mFlags.getFledgeAdSelectionFilteringEnabled()) {
            sLogger.v(
                    "Ad selection filtering disabled; skipping Frequency Cap histogram"
                            + " maintenance");
            return;
        }

        Instant expirationInstant =
                mClock.instant().minusSeconds(KeyedFrequencyCap.MAX_INTERVAL.getSeconds());

        sLogger.v(
                "Clearing expired Frequency Cap histogram events older than %s", expirationInstant);
        int numExpiredEvents = mFrequencyCapDao.deleteAllExpiredHistogramData(expirationInstant);
        sLogger.v("Cleared %d expired Frequency Cap histogram events", numExpiredEvents);

        // Read from flags directly, since this maintenance task worker is attached to a background
        //  job with unknown lifetime
        if (mFlags.getDisableFledgeEnrollmentCheck()) {
            sLogger.v(
                    "FLEDGE enrollment check disabled; skipping disallowed buyer Frequency Cap "
                            + "histogram maintenance");
        } else {
            sLogger.v("Clearing Frequency Cap histogram events for disallowed buyer ad techs");
            int numDisallowedBuyerEvents =
                    mFrequencyCapDao.deleteAllDisallowedBuyerHistogramData(mEnrollmentDao);
            sLogger.v(
                    "Cleared %d Frequency Cap histogram events for disallowed buyer ad techs",
                    numDisallowedBuyerEvents);
        }

        // Read from flags directly, since this maintenance task worker is attached to a background
        //  job with unknown lifetime
        sLogger.v("Clearing Frequency Cap histogram events for disallowed source apps");
        int numDisallowedSourceAppEvents =
                mFrequencyCapDao.deleteAllDisallowedSourceAppHistogramData(packageManager, mFlags);
        sLogger.v(
                "Cleared %d Frequency Cap histogram events for disallowed source apps",
                numDisallowedSourceAppEvents);
    }

    /** Deletes the expired {@link com.android.adservices.service.kanon.KAnonMessageEntity} */
    public void clearExpiredKAnonMessageEntities() {
        if (mFlags.getFledgeKAnonSignJoinFeatureEnabled()) {
            sLogger.v("Clearing expired KAnon message entities");
            mKAnonMessageDao.removeExpiredEntities(mClock.instant());
            sLogger.v("Cleared expired KAnon message entities");
        }
    }
}
