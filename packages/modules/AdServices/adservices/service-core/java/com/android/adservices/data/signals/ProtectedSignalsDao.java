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

package com.android.adservices.data.signals;

import android.adservices.common.AdTechIdentifier;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.android.adservices.data.common.CleanupUtils;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * DAO abstract class used to access ProtectedSignal storage
 *
 * <p>Annotations will generate Room-based SQLite Dao impl.
 */
@Dao
public abstract class ProtectedSignalsDao {

    /**
     * Returns a list of all signals owned by the given buyer.
     *
     * @param buyer The buyer to retrieve signals for.
     * @return A list of all protected signals owned by the input buyer.
     */
    @Query("SELECT * FROM protected_signals WHERE buyer = :buyer")
    public abstract List<DBProtectedSignal> getSignalsByBuyer(AdTechIdentifier buyer);

    /**
     * Inserts signals into the database.
     *
     * @param signals The signals to insert.
     */
    @Insert
    public abstract void insertSignals(@NonNull List<DBProtectedSignal> signals);

    /**
     * Deletes signals from the database.
     *
     * @param signals The signals to delete.
     */
    @Delete
    public abstract void deleteSignals(@NonNull List<DBProtectedSignal> signals);

    /**
     * Inserts and deletes signals in a single transaction.
     *
     * @param signalsToInsert The signals to insert.
     * @param signalsToDelete The signals to delete.
     */
    @Transaction
    public void insertAndDelete(
            @NonNull AdTechIdentifier buyer,
            @NonNull Instant now,
            @NonNull List<DBProtectedSignal> signalsToInsert,
            @NonNull List<DBProtectedSignal> signalsToDelete) {
        insertSignals(signalsToInsert);
        deleteSignals(signalsToDelete);
        persistSignalsUpdateMetadata(
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(buyer)
                        .setLastSignalsUpdatedTime(now)
                        .build());
    }

    /**
     * Deletes all signals {@code expiryTime}.
     *
     * @return the number of deleted signals
     */
    @Query("DELETE FROM protected_signals WHERE creationTime < :expiryTime")
    protected abstract int deleteSignalsBeforeTime(@NonNull Instant expiryTime);

    /** Returns buyers with expired signals. */
    @Query("SELECT DISTINCT buyer FROM protected_signals WHERE creationTime < :expiryTime")
    protected abstract List<AdTechIdentifier> getBuyersWithExpiredSignals(
            @NonNull Instant expiryTime);

    /**
     * Deletes expired signals and updates buyer metadata.
     *
     * @return the number of deleted signals
     */
    @Transaction
    public int deleteExpiredSignalsAndUpdateSignalsUpdateMetadata(
            @NonNull Instant expiryTime, @NonNull Instant now) {
        List<AdTechIdentifier> buyers = getBuyersWithExpiredSignals(expiryTime);
        for (AdTechIdentifier buyer : buyers) {
            persistSignalsUpdateMetadata(
                    DBSignalsUpdateMetadata.builder()
                            .setBuyer(buyer)
                            .setLastSignalsUpdatedTime(now)
                            .build());
        }
        return deleteSignalsBeforeTime(expiryTime);
    }

    /**
     * Deletes all signals belonging to disallowed buyer ad techs in a single transaction, where the
     * buyer ad techs cannot be found in the enrollment database.
     *
     * @return the number of deleted signals
     */
    @Transaction
    public int deleteDisallowedBuyerSignals(@NonNull EnrollmentDao enrollmentDao) {
        Objects.requireNonNull(enrollmentDao);

        List<AdTechIdentifier> buyersToRemove = getAllBuyers();
        if (buyersToRemove.isEmpty()) {
            return 0;
        }

        Set<AdTechIdentifier> enrolledAdTechs = enrollmentDao.getAllFledgeEnrolledAdTechs();
        buyersToRemove.removeAll(enrolledAdTechs);

        int numDeletedEvents = 0;
        if (!buyersToRemove.isEmpty()) {
            numDeletedEvents = deleteByBuyers(buyersToRemove);
            for (AdTechIdentifier buyer : buyersToRemove) {
                deleteSignalsUpdateMetadata(buyer);
            }
        }

        return numDeletedEvents;
    }

    /**
     * Helper method for {@link #deleteDisallowedBuyerSignals}
     *
     * @return All buyers with signals in the DB.
     */
    @Query("SELECT DISTINCT buyer FROM protected_signals")
    protected abstract List<AdTechIdentifier> getAllBuyers();

    /**
     * Deletes all signals for the list of buyers. Helper method for {@link
     * #deleteDisallowedBuyerSignals}
     *
     * @return Number of buyers deleted.
     */
    @Query("DELETE FROM protected_signals where buyer in (:buyers)")
    protected abstract int deleteByBuyers(@NonNull List<AdTechIdentifier> buyers);

    /**
     * Deletes all signals belonging to disallowed source apps in a single transaction, where the
     * source apps cannot be found in the app package name allowlist or are not installed on the
     * device.
     *
     * @return the number of deleted signals
     */
    @Transaction
    public int deleteAllDisallowedPackageSignalsAndUpdateSignalUpdateMetadata(
            @NonNull PackageManager packageManager, @NonNull Flags flags, @NonNull Instant now) {
        Objects.requireNonNull(packageManager);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(now);

        List<String> sourceAppsToRemove = getAllPackages();
        if (sourceAppsToRemove.isEmpty()) {
            return 0;
        }

        CleanupUtils.removeAllowedPackages(
                sourceAppsToRemove, packageManager, Arrays.asList(flags.getPasAppAllowList()));

        int numDeletedEvents = 0;
        if (!sourceAppsToRemove.isEmpty()) {
            List<AdTechIdentifier> buyers = getBuyersForPackages(sourceAppsToRemove);
            for (AdTechIdentifier buyer : buyers) {
                persistSignalsUpdateMetadata(
                        DBSignalsUpdateMetadata.builder()
                                .setBuyer(buyer)
                                .setLastSignalsUpdatedTime(now)
                                .build());
            }
            numDeletedEvents = deleteSignalsByPackage(sourceAppsToRemove);
            // TODO(b/300661099): Collect and send telemetry on signal deletion
        }
        return numDeletedEvents;
    }

    /**
     * Returns the list of all unique packages in the signals table.
     *
     * <p>This method is not meant to be called externally, but is a helper for {@link
     * #deleteAllDisallowedPackageSignalsAndUpdateSignalUpdateMetadata(PackageManager, Flags,
     * Instant)}
     */
    @Query("SELECT DISTINCT packageName FROM protected_signals")
    protected abstract List<String> getAllPackages();

    /**
     * Deletes all signals generated from the given packages.
     *
     * @return the number of deleted signals
     */
    @Query("DELETE FROM protected_signals WHERE packageName in (:packages)")
    public abstract int deleteSignalsByPackage(@NonNull List<String> packages);

    /** Deletes all signals */
    @Query("DELETE FROM protected_signals")
    public abstract int deleteAllSignals();

    /** Returns all buyers for the given packages. */
    @Query("SELECT DISTINCT buyer FROM protected_signals WHERE packageName in (:packages)")
    protected abstract List<AdTechIdentifier> getBuyersForPackages(@NonNull List<String> packages);

    /** Create or update a buyer metadata entry. */
    @Insert(entity = DBSignalsUpdateMetadata.class, onConflict = OnConflictStrategy.REPLACE)
    @VisibleForTesting
    protected abstract long persistSignalsUpdateMetadata(
            DBSignalsUpdateMetadata dbSignalsUpdateMetadata);

    /** Returns a metadata entry according to the buyer. */
    @Query("SELECT * FROM signals_update_metadata WHERE buyer=:buyer")
    public abstract DBSignalsUpdateMetadata getSignalsUpdateMetadata(AdTechIdentifier buyer);

    /** Delete the metadata for the buyer. */
    @Query("DELETE FROM signals_update_metadata WHERE buyer=:buyer")
    public abstract void deleteSignalsUpdateMetadata(AdTechIdentifier buyer);

    /** Delete all metadata in the storage. */
    @Query("DELETE FROM signals_update_metadata")
    public abstract void deleteAllSignalsUpdateMetadata();
}
