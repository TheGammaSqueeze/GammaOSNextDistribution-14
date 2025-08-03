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

package com.android.adservices.service.signals;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Utility class to perform Protected Signals maintenance tasks. */
public class SignalsMaintenanceTasksWorker {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final ProtectedSignalsDao mProtectedSignalsDao;
    @NonNull private final EnrollmentDao mEnrollmentDao;
    @NonNull private final EncodedPayloadDao mEncodedPayloadDao;
    @NonNull private final Flags mFlags;
    @NonNull private final Clock mClock;
    @NonNull private final PackageManager mPackageManager;
    @NonNull private final EncoderLogicHandler mEncoderLogicHandler;

    @VisibleForTesting
    public SignalsMaintenanceTasksWorker(
            @NonNull Flags flags,
            @NonNull ProtectedSignalsDao protectedSignalsDao,
            @NonNull EncoderLogicHandler encoderLogicHandler,
            @NonNull EncodedPayloadDao encodedPayloadDao,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull Clock clock,
            @NonNull PackageManager packageManager) {
        Objects.requireNonNull(flags);
        Objects.requireNonNull(protectedSignalsDao);
        Objects.requireNonNull(enrollmentDao);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(packageManager);

        mFlags = flags;
        mProtectedSignalsDao = protectedSignalsDao;
        mEnrollmentDao = enrollmentDao;
        mEncodedPayloadDao = encodedPayloadDao;
        mEncoderLogicHandler = encoderLogicHandler;
        mClock = clock;
        mPackageManager = packageManager;
    }

    private SignalsMaintenanceTasksWorker(@NonNull Context context) {
        Objects.requireNonNull(context);
        mFlags = FlagsFactory.getFlags();
        mProtectedSignalsDao = ProtectedSignalsDatabase.getInstance(context).protectedSignalsDao();
        mEnrollmentDao = EnrollmentDao.getInstance(context);
        mEncoderLogicHandler = new EncoderLogicHandler(context);
        mEncodedPayloadDao = ProtectedSignalsDatabase.getInstance(context).getEncodedPayloadDao();
        mClock = Clock.systemUTC();
        mPackageManager = context.getPackageManager();
    }

    /** Creates a new instance of {@link SignalsMaintenanceTasksWorker}. */
    public static SignalsMaintenanceTasksWorker create(@NonNull Context context) {
        Objects.requireNonNull(context);
        return new SignalsMaintenanceTasksWorker(context);
    }

    /**
     * Clears invalid signals from the protected signals table. Not flagged since the job will only
     * run if protected signals is enabled.
     *
     * <ul>
     *   <li>Expired signals
     *   <li>Disallowed buyer signals
     *   <li>Disallowed source app signals
     *   <li>Uninstalled source app signals
     *       <p>Also clears data for disallowed buyers and expired encoding related information such
     *       as:
     *       <ul>
     *         <li>Encoder end-point
     *         <li>Encoding logic
     *         <li>Encoded Signals payload
     *       </ul>
     */
    public void clearInvalidProtectedSignalsData() {
        Instant now = mClock.instant();
        Instant expirationInstant = now.minusSeconds(ProtectedSignal.EXPIRATION_SECONDS);
        clearInvalidSignals(expirationInstant, now);
        clearInvalidEncoders(expirationInstant);
        clearInvalidEncodedPayloads(expirationInstant);
    }

    @VisibleForTesting
    void clearInvalidSignals(Instant expirationInstant, Instant now) {

        sLogger.v("Clearing expired signals older than %s", expirationInstant);
        int numExpiredSignals =
                mProtectedSignalsDao.deleteExpiredSignalsAndUpdateSignalsUpdateMetadata(
                        expirationInstant, now);
        sLogger.v("Cleared %d expired signals", numExpiredSignals);

        // Read from flags directly, since this maintenance task worker is attached to a background
        //  job with unknown lifetime
        if (mFlags.getDisableFledgeEnrollmentCheck()) {
            sLogger.v(
                    "FLEDGE enrollment check disabled; skipping disallowed buyer signal"
                            + " maintenance");
        } else {
            sLogger.v("Clearing signals for disallowed buyer ad techs");
            int numDisallowedBuyerEvents =
                    mProtectedSignalsDao.deleteDisallowedBuyerSignals(mEnrollmentDao);
            sLogger.v("Cleared %d signals for disallowed buyer ad techs", numDisallowedBuyerEvents);
        }

        sLogger.v("Clearing signals for disallowed source apps");
        int numDisallowedSourceAppSignals =
                mProtectedSignalsDao.deleteAllDisallowedPackageSignalsAndUpdateSignalUpdateMetadata(
                        mPackageManager, mFlags, now);
        sLogger.v("Cleared %d signals for disallowed source apps", numDisallowedSourceAppSignals);
    }

    @VisibleForTesting
    void clearInvalidEncoders(Instant expirationInstant) {

        Set<AdTechIdentifier> buyersWithConsentRevoked = new HashSet<>();
        if (mFlags.getDisableFledgeEnrollmentCheck()) {
            sLogger.v(
                    "FLEDGE enrollment check disabled; skipping disallowed buyer encoding logic"
                            + " maintenance");
        } else {
            sLogger.v("Gathering buyers with consent revoked");
            List<AdTechIdentifier> registeredBuyers = mEncoderLogicHandler.getBuyersWithEncoders();
            buyersWithConsentRevoked = getBuyersWithRevokedConsent(new HashSet<>(registeredBuyers));
        }

        sLogger.v("Clearing expired encoders older than %s", expirationInstant);
        Set<AdTechIdentifier> buyersWithStaleEncoders =
                new HashSet<>(mEncoderLogicHandler.getBuyersWithStaleEncoders(expirationInstant));

        // Union of two sets with revoked buyers and buyers with stale encoders
        buyersWithStaleEncoders.addAll(buyersWithConsentRevoked);
        mEncoderLogicHandler.deleteEncodersForBuyers(buyersWithStaleEncoders);
    }

    @VisibleForTesting
    void clearInvalidEncodedPayloads(Instant expirationInstant) {

        if (mFlags.getDisableFledgeEnrollmentCheck()) {
            sLogger.v(
                    "FLEDGE enrollment check disabled; skipping disallowed buyer encoded payload"
                            + " maintenance");
        } else {
            sLogger.v("Gathering buyers with consent revoked");
            List<AdTechIdentifier> buyersWithEncodedPayloads =
                    mEncodedPayloadDao.getAllBuyersWithEncodedPayloads();
            Set<AdTechIdentifier> buyersWithConsentRevoked =
                    getBuyersWithRevokedConsent(new HashSet<>(buyersWithEncodedPayloads));
            for (AdTechIdentifier revokedBuyer : buyersWithConsentRevoked) {
                mEncodedPayloadDao.deleteEncodedPayload(revokedBuyer);
            }
        }

        sLogger.v("Clearing expired encodings older than %s", expirationInstant);
        mEncodedPayloadDao.deleteEncodedPayloadsBeforeTime(expirationInstant);
    }

    private Set<AdTechIdentifier> getBuyersWithRevokedConsent(Set<AdTechIdentifier> buyers) {
        Set<AdTechIdentifier> enrolledAdTechs = mEnrollmentDao.getAllFledgeEnrolledAdTechs();
        buyers.removeAll(enrolledAdTechs);
        return buyers;
    }
}
