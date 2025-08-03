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

package com.android.adservices.data.customaudience;

import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.PartialCustomAudience;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.common.CleanupUtils;
import com.android.adservices.data.common.DecisionLogic;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.JsVersionHelper;
import com.android.adservices.service.customaudience.CustomAudienceUpdatableData;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableMap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DAO abstract class used to access Custom Audience persistent storage.
 *
 * <p>Annotations will generate Room-based SQLite Dao impl.
 */
@Dao
public abstract class CustomAudienceDao {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    /**
     * Add user to a new custom audience. As designed, will override existing one.
     *
     * <p>This method is not meant to be used on its own, since custom audiences must be persisted
     * alongside matching background fetch data. Use {@link
     * #insertOrOverwriteCustomAudience(DBCustomAudience, Uri)} instead.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void persistCustomAudience(@NonNull DBCustomAudience customAudience);

    /**
     * Adds or updates background fetch data for a custom audience.
     *
     * <p>This method does not update the corresponding custom audience. Use {@link
     * #updateCustomAudienceAndBackgroundFetchData(DBCustomAudienceBackgroundFetchData,
     * CustomAudienceUpdatableData)} to do so safely.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void persistCustomAudienceBackgroundFetchData(
            @NonNull DBCustomAudienceBackgroundFetchData fetchData);

    /**
     * Adds a new {@link DBCustomAudienceQuarantine} entry to the {@code custom_audience_quarantine}
     * table.
     *
     * <p>This method is not meant to be used on its own, since it doesn't take into account the
     * maximum size of {@code custom_audience_quarantine}. Use {@link
     * #safelyInsertCustomAudienceQuarantine} instead.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void persistCustomAudienceQuarantineData(
            DBCustomAudienceQuarantine dbCustomAudienceQuarantine);

    /**
     * Adds or updates a given custom audience and background fetch data in a single transaction.
     *
     * <p>This transaction is separate in order to minimize the critical region while locking the
     * database. It is not meant to be exposed or used by itself; use {@link
     * #insertOrOverwriteCustomAudience(DBCustomAudience, Uri)} instead.
     */
    @Transaction
    protected void insertOrOverwriteCustomAudienceAndBackgroundFetchData(
            @NonNull DBCustomAudience customAudience,
            @NonNull DBCustomAudienceBackgroundFetchData fetchData) {
        persistCustomAudience(customAudience);
        persistCustomAudienceBackgroundFetchData(fetchData);
    }

    /**
     * Adds the user to the given custom audience.
     *
     * <p>If a custom audience already exists, it is overwritten completely.
     *
     * <p>Background fetch data is also created based on the given {@code customAudience} and {@code
     * dailyUpdateUri} and overwrites any existing background fetch data. This method assumes the
     * input parameters have already been validated and are correct.
     */
    public void insertOrOverwriteCustomAudience(
            @NonNull DBCustomAudience customAudience,
            @NonNull Uri dailyUpdateUri,
            boolean debuggable) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(dailyUpdateUri);

        Instant eligibleUpdateTime;
        if (customAudience.getUserBiddingSignals() == null
                || customAudience.getTrustedBiddingData() == null
                || customAudience.getAds() == null
                || customAudience.getAds().isEmpty()) {
            eligibleUpdateTime = Instant.EPOCH;
        } else {
            eligibleUpdateTime =
                    DBCustomAudienceBackgroundFetchData
                            .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                    customAudience.getCreationTime());
        }

        DBCustomAudienceBackgroundFetchData fetchData =
                DBCustomAudienceBackgroundFetchData.builder()
                        .setOwner(customAudience.getOwner())
                        .setBuyer(customAudience.getBuyer())
                        .setName(customAudience.getName())
                        .setDailyUpdateUri(dailyUpdateUri)
                        .setEligibleUpdateTime(eligibleUpdateTime)
                        .setIsDebuggable(debuggable)
                        .build();

        insertOrOverwriteCustomAudienceAndBackgroundFetchData(customAudience, fetchData);
    }

    /**
     * Updates a custom audience and its background fetch data based on the given {@link
     * CustomAudienceUpdatableData} in a single transaction.
     *
     * <p>If no custom audience is found corresponding to the given {@link
     * DBCustomAudienceBackgroundFetchData}, no action is taken.
     */
    @Transaction
    public void updateCustomAudienceAndBackgroundFetchData(
            @NonNull DBCustomAudienceBackgroundFetchData fetchData,
            @NonNull CustomAudienceUpdatableData updatableData) {
        Objects.requireNonNull(fetchData);
        Objects.requireNonNull(updatableData);

        DBCustomAudience customAudience =
                getCustomAudienceByPrimaryKey(
                        fetchData.getOwner(), fetchData.getBuyer(), fetchData.getName());

        if (customAudience == null) {
            // This custom audience could have been cleaned up while it was being updated
            return;
        }

        customAudience = customAudience.copyWithUpdatableData(updatableData);

        persistCustomAudience(customAudience);
        persistCustomAudienceBackgroundFetchData(fetchData);
    }

    /** Returns total size of the {@code custom_audience_quarantine} table. */
    @Query("SELECT COUNT(*) FROM custom_audience_quarantine")
    abstract long getTotalNumCustomAudienceQuarantineEntries();

    /**
     * Safely inserts a {@link DBCustomAudienceQuarantine} into the table.
     *
     * @throws IllegalStateException if {@link #getTotalNumCustomAudienceQuarantineEntries} exceeds
     *     {@code maxEntries}.
     *     <p>This transaction is separate in order to minimize the critical region while locking
     *     the database.
     */
    @Transaction
    public void safelyInsertCustomAudienceQuarantine(
            @NonNull DBCustomAudienceQuarantine dbCustomAudienceQuarantine, long maxEntries)
            throws IllegalStateException {
        Objects.requireNonNull(dbCustomAudienceQuarantine);

        if (getTotalNumCustomAudienceQuarantineEntries() >= maxEntries) {
            String errorMessage =
                    "Quarantine table maximum has been reached! Not persisting this entry";
            sLogger.e(errorMessage);
            throw new IllegalStateException(errorMessage);
        }
        persistCustomAudienceQuarantineData(dbCustomAudienceQuarantine);
    }

    /** Get count of custom audience. */
    @Query("SELECT COUNT(*) FROM custom_audience")
    public abstract long getCustomAudienceCount();

    /** Get count of custom audience of a given owner. */
    @Query("SELECT COUNT(*) FROM custom_audience WHERE owner=:owner")
    public abstract long getCustomAudienceCountForOwner(String owner);

    /** Get the total number of distinct custom audience owner. */
    @Query("SELECT COUNT(DISTINCT owner) FROM custom_audience")
    public abstract long getCustomAudienceOwnerCount();

    /** List all custom audiences by owner and buyer that are marked as debuggable. */
    @Query(
            "SELECT * FROM custom_audience "
                    + "WHERE owner=:owner AND buyer=:buyer AND debuggable=1")
    @Nullable
    public abstract List<DBCustomAudience> listDebuggableCustomAudiencesByOwnerAndBuyer(
            @NonNull String owner, @NonNull AdTechIdentifier buyer);

    /** Get custom audience by owner, buyer and name that are marked as debuggable. */
    @Query(
            "SELECT * FROM custom_audience "
                    + "WHERE owner = :owner AND buyer = :buyer AND name = :name AND debuggable = 1")
    public abstract DBCustomAudience getDebuggableCustomAudienceByPrimaryKey(
            @NonNull String owner, @NonNull AdTechIdentifier buyer, @NonNull String name);

    /**
     * Get the count of total custom audience, the count for the given owner and the count of
     * distinct owner in one transaction.
     *
     * @param owner the owner we need check the count against.
     * @return the aggregated data of custom audience count
     */
    @Transaction
    @NonNull
    public CustomAudienceStats getCustomAudienceStats(@NonNull String owner) {
        Objects.requireNonNull(owner);

        long customAudienceCount = getCustomAudienceCount();
        long customAudienceCountPerOwner = getCustomAudienceCountForOwner(owner);
        long ownerCount = getCustomAudienceOwnerCount();

        // TODO(b/255780705): Add buyer and per-buyer stats
        return CustomAudienceStats.builder()
                .setOwner(owner)
                .setTotalCustomAudienceCount(customAudienceCount)
                .setPerOwnerCustomAudienceCount(customAudienceCountPerOwner)
                .setTotalOwnerCount(ownerCount)
                .build();
    }

    /**
     * Add a custom audience override into the table custom_audience_overrides
     *
     * @param customAudienceOverride is the CustomAudienceOverride to add to table
     *     custom_audience_overrides. If a {@link DBCustomAudienceOverride} object with the primary
     *     key already exists, this will replace the existing object.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void persistCustomAudienceOverride(
            DBCustomAudienceOverride customAudienceOverride);

    /**
     * Checks if there is a row in the custom audience override data with the unique key combination
     * of owner, buyer, and name
     *
     * @return true if row exists, false otherwise
     */
    @Query(
            "SELECT EXISTS(SELECT 1 FROM custom_audience_overrides WHERE owner = :owner "
                    + "AND buyer = :buyer AND name = :name LIMIT 1)")
    public abstract boolean doesCustomAudienceOverrideExist(
            @NonNull String owner, @NonNull AdTechIdentifier buyer, @NonNull String name);

    /**
     * Checks if there is a row in the {@code custom_audience_quarantine} with the unique key
     * combination of owner and buyer.
     *
     * @return true if row exists, false otherwise
     */
    @Query(
            "SELECT EXISTS(SELECT 1 FROM custom_audience_quarantine WHERE owner = :owner "
                    + "AND buyer = :buyer LIMIT 1)")
    public abstract boolean doesCustomAudienceQuarantineExist(
            @NonNull String owner, @NonNull AdTechIdentifier buyer);

    /**
     * Gets expiration time if it exists with the unique key combination of owner and buyer. Returns
     * null otherwise.
     */
    @Nullable
    @Query(
            "SELECT quarantine_expiration_time FROM custom_audience_quarantine WHERE owner = :owner"
                    + " AND buyer = :buyer")
    public abstract Instant getCustomAudienceQuarantineExpiration(
            @NonNull String owner, @NonNull AdTechIdentifier buyer);

    /**
     * Get custom audience by its unique key.
     *
     * @return custom audience result if exists.
     */
    @Query("SELECT * FROM custom_audience WHERE owner = :owner AND buyer = :buyer AND name = :name")
    @Nullable
    public abstract DBCustomAudience getCustomAudienceByPrimaryKey(
            @NonNull String owner, @NonNull AdTechIdentifier buyer, @NonNull String name);

    /**
     * Get custom audiences by buyer and name.
     *
     * @return custom audiences result if exists.
     */
    @Query("SELECT * FROM custom_audience WHERE buyer = :buyer AND name = :name")
    @NonNull
    public abstract List<DBCustomAudience> getCustomAudiencesForBuyerAndName(
            @NonNull AdTechIdentifier buyer, @NonNull String name);

    /**
     * Get custom audience background fetch data by its unique key.
     *
     * @return custom audience background fetch data if it exists
     */
    @Query(
            "SELECT * FROM custom_audience_background_fetch_data "
                    + "WHERE owner = :owner AND buyer = :buyer AND name = :name")
    @Nullable
    @VisibleForTesting
    public abstract DBCustomAudienceBackgroundFetchData
            getCustomAudienceBackgroundFetchDataByPrimaryKey(
                    @NonNull String owner, @NonNull AdTechIdentifier buyer, @NonNull String name);

    /**
     * Get debuggable custom audience background fetch data by its unique key.
     *
     * @return custom audience background fetch data if it exists
     */
    @Query(
            "SELECT * FROM custom_audience_background_fetch_data WHERE owner = :owner AND buyer ="
                    + " :buyer AND name = :name AND is_debuggable = 1")
    @Nullable
    public abstract DBCustomAudienceBackgroundFetchData
            getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                    @NonNull String owner, @NonNull AdTechIdentifier buyer, @NonNull String name);

    /**
     * List debuggable custom audience background fetch data by its unique key.
     *
     * @return custom audience background fetch data if it exists
     */
    @Query(
            "SELECT * FROM custom_audience_background_fetch_data "
                    + "WHERE owner = :owner AND buyer = :buyer AND is_debuggable = 1")
    @Nullable
    public abstract List<DBCustomAudienceBackgroundFetchData>
            listDebuggableCustomAudienceBackgroundFetchData(
                    @NonNull String owner, @NonNull AdTechIdentifier buyer);

    /**
     * Get custom audience JS override by its unique key.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #getBiddingLogicUriOverride(String, AdTechIdentifier, String, String)} instead.
     *
     * @return custom audience override result if exists.
     */
    @Query(
            "SELECT bidding_logic as bidding_logic_js, bidding_logic_version as"
                    + " buyer_bidding_logic_version FROM custom_audience_overrides WHERE owner ="
                    + " :owner AND buyer = :buyer AND name = :name AND app_package_name="
                    + " :appPackageName")
    @Nullable
    protected abstract BiddingLogicJsWithVersion getBiddingLogicUriOverrideInternal(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull String appPackageName);

    /**
     * Get custom audience JS override by its unique key.
     *
     * @return custom audience override result if exists.
     */
    public DecisionLogic getBiddingLogicUriOverride(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull String appPackageName) {
        BiddingLogicJsWithVersion biddingLogicJsWithVersion =
                getBiddingLogicUriOverrideInternal(owner, buyer, name, appPackageName);

        if (Objects.isNull(biddingLogicJsWithVersion)) {
            return null;
        }

        ImmutableMap.Builder<Integer, Long> versionMap = new ImmutableMap.Builder<>();
        if (Objects.nonNull(biddingLogicJsWithVersion.getBuyerBiddingLogicVersion())) {
            versionMap.put(
                    JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS,
                    biddingLogicJsWithVersion.getBuyerBiddingLogicVersion());
        }

        return DecisionLogic.create(
                biddingLogicJsWithVersion.getBiddingLogicJs(), versionMap.build());
    }

    /**
     * Get trusted bidding data override by its unique key.
     *
     * @return custom audience override result if exists.
     */
    @Query(
            "SELECT trusted_bidding_data FROM custom_audience_overrides WHERE owner = :owner "
                    + "AND buyer = :buyer AND name = :name AND app_package_name= :appPackageName")
    @Nullable
    public abstract String getTrustedBiddingDataOverride(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull String appPackageName);

    /** Delete the custom audience given owner, buyer, and name. */
    @Query("DELETE FROM custom_audience WHERE owner = :owner AND buyer = :buyer AND name = :name")
    protected abstract void deleteCustomAudienceByPrimaryKey(
            @NonNull String owner, @NonNull AdTechIdentifier buyer, @NonNull String name);

    /** Delete background fetch data for the custom audience given owner, buyer, and name. */
    @Query(
            "DELETE FROM custom_audience_background_fetch_data WHERE owner = :owner "
                    + "AND buyer = :buyer AND name = :name")
    protected abstract void deleteCustomAudienceBackgroundFetchDataByPrimaryKey(
            @NonNull String owner, @NonNull AdTechIdentifier buyer, @NonNull String name);

    /**
     * Delete all custom audience data corresponding to the given {@code owner}, {@code buyer}, and
     * {@code name} in a single transaction.
     */
    @Transaction
    public void deleteAllCustomAudienceDataByPrimaryKey(
            @NonNull String owner, @NonNull AdTechIdentifier buyer, @NonNull String name) {
        deleteCustomAudienceByPrimaryKey(owner, buyer, name);
        deleteCustomAudienceBackgroundFetchDataByPrimaryKey(owner, buyer, name);
    }

    /**
     * Deletes all custom audiences which are expired, where the custom audiences' expiration times
     * match or precede the given {@code expiryTime}.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteAllExpiredCustomAudienceData(Instant)} instead.
     *
     * @return the number of deleted custom audiences
     */
    @Query("DELETE FROM custom_audience WHERE expiration_time <= :expiryTime")
    protected abstract int deleteAllExpiredCustomAudiences(@NonNull Instant expiryTime);

    /**
     * Deletes all expired entries of the {@code custom_audience_quarantine} table.
     *
     * @return the number of deleted entries
     */
    @Query(
            "DELETE FROM custom_audience_quarantine WHERE quarantine_expiration_time <="
                    + " :expiryTime")
    public abstract int deleteAllExpiredQuarantineEntries(@NonNull Instant expiryTime);

    /**
     * Deletes all entries with the unique combination of owner and buyer.
     *
     * @return the number of deleted entries
     */
    @Query("DELETE FROM custom_audience_quarantine WHERE owner = :owner " + "AND buyer = :buyer")
    public abstract int deleteQuarantineEntry(
            @NonNull String owner, @NonNull AdTechIdentifier buyer);

    /**
     * Deletes background fetch data for all custom audiences which are expired, where the custom
     * audiences' expiration times match or precede the given {@code expiryTime}.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteAllExpiredCustomAudienceData(Instant)} instead.
     */
    @Query(
            "DELETE FROM custom_audience_background_fetch_data WHERE ROWID IN "
                    + "(SELECT bgf.ROWID FROM custom_audience_background_fetch_data AS bgf "
                    + "INNER JOIN custom_audience AS ca "
                    + "ON bgf.buyer = ca.buyer AND bgf.owner = ca.owner AND bgf.name = ca.name "
                    + "WHERE expiration_time <= :expiryTime)")
    protected abstract void deleteAllExpiredCustomAudienceBackgroundFetchData(
            @NonNull Instant expiryTime);

    /**
     * Deletes all expired custom audience data in a single transaction, where the custom audiences'
     * expiration times match or precede the given {@code expiryTime}.
     *
     * @return the number of deleted custom audiences
     */
    @Transaction
    public int deleteAllExpiredCustomAudienceData(@NonNull Instant expiryTime) {
        deleteAllExpiredCustomAudienceBackgroundFetchData(expiryTime);
        return deleteAllExpiredCustomAudiences(expiryTime);
    }

    /** Returns the set of all unique owner apps in the custom audience table. */
    @Query("SELECT DISTINCT owner FROM custom_audience")
    public abstract List<String> getAllCustomAudienceOwners();

    /**
     * Deletes all custom audiences belonging to any app in the given set of {@code ownersToRemove}.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteAllDisallowedOwnerCustomAudienceData(PackageManager, Flags)} instead.
     *
     * @return the number of deleted custom audiences
     */
    @Query("DELETE FROM custom_audience WHERE owner IN (:ownersToRemove)")
    protected abstract int deleteCustomAudiencesByOwner(@NonNull List<String> ownersToRemove);

    /**
     * Deletes all custom audience background fetch data belonging to any app in the given set of
     * {@code ownersToRemove}.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteAllDisallowedOwnerCustomAudienceData(PackageManager, Flags)} instead.
     */
    @Query("DELETE FROM custom_audience_background_fetch_data WHERE owner IN (:ownersToRemove)")
    protected abstract void deleteCustomAudienceBackgroundFetchDataByOwner(
            @NonNull List<String> ownersToRemove);

    /**
     * Deletes all custom audience data belonging to disallowed owner apps in a single transaction,
     * where the custom audiences' owner apps cannot be found in the installed list or where the
     * owner apps are not found in the app allowlist.
     *
     * @return a {@link CustomAudienceStats} object containing only the number of deleted custom
     *     audiences and the number of disallowed owner apps found
     */
    @Transaction
    @NonNull
    public CustomAudienceStats deleteAllDisallowedOwnerCustomAudienceData(
            @NonNull PackageManager packageManager, @NonNull Flags flags) {
        Objects.requireNonNull(packageManager);
        Objects.requireNonNull(flags);
        List<String> ownersToRemove = getAllCustomAudienceOwners();

        CleanupUtils.removeAllowedPackages(
                ownersToRemove, packageManager, Arrays.asList(flags.getPpapiAppAllowList()));

        long numDisallowedOwnersFound = ownersToRemove.size();
        long numRemovedCustomAudiences = 0;
        if (!ownersToRemove.isEmpty()) {
            deleteCustomAudienceBackgroundFetchDataByOwner(ownersToRemove);
            numRemovedCustomAudiences = deleteCustomAudiencesByOwner(ownersToRemove);
        }

        return CustomAudienceStats.builder()
                .setTotalCustomAudienceCount(numRemovedCustomAudiences)
                .setTotalOwnerCount(numDisallowedOwnersFound)
                .build();
    }

    /** Returns the set of all unique buyer ad techs in the custom audience table. */
    @Query("SELECT DISTINCT buyer FROM custom_audience")
    public abstract List<AdTechIdentifier> getAllCustomAudienceBuyers();

    /**
     * Deletes all custom audiences belonging to any ad tech in the given set of {@code
     * buyersToRemove}.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteAllDisallowedBuyerCustomAudienceData(EnrollmentDao, Flags)} instead.
     *
     * @return the number of deleted custom audiences
     */
    @Query("DELETE FROM custom_audience WHERE buyer IN (:buyersToRemove)")
    protected abstract int deleteCustomAudiencesByBuyer(
            @NonNull List<AdTechIdentifier> buyersToRemove);

    /**
     * Deletes all custom audience background fetch data belonging to any ad tech in the given set
     * of {@code buyersToRemove}.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteAllDisallowedBuyerCustomAudienceData(EnrollmentDao, Flags)} instead.
     */
    @Query("DELETE FROM custom_audience_background_fetch_data WHERE buyer IN (:buyersToRemove)")
    protected abstract void deleteCustomAudienceBackgroundFetchDataByBuyer(
            @NonNull List<AdTechIdentifier> buyersToRemove);

    /**
     * Deletes all custom audience data belonging to disallowed buyer ad techs in a single
     * transaction, where the custom audiences' buyer ad techs cannot be found in the enrollment
     * database.
     *
     * @return a {@link CustomAudienceStats} object containing only the number of deleted custom
     *     audiences and the number of disallowed owner apps found
     */
    @Transaction
    @NonNull
    public CustomAudienceStats deleteAllDisallowedBuyerCustomAudienceData(
            @NonNull EnrollmentDao enrollmentDao, @NonNull Flags flags) {
        Objects.requireNonNull(enrollmentDao);
        Objects.requireNonNull(flags);

        if (flags.getDisableFledgeEnrollmentCheck()) {
            sLogger.d("FLEDGE enrollment check disabled; skipping enrolled buyer cleanup");
            return CustomAudienceStats.builder()
                    .setTotalCustomAudienceCount(0)
                    .setTotalBuyerCount(0)
                    .build();
        }

        List<AdTechIdentifier> buyersToRemove = getAllCustomAudienceBuyers();

        if (!buyersToRemove.isEmpty()) {
            Set<AdTechIdentifier> allowedAdTechs = enrollmentDao.getAllFledgeEnrolledAdTechs();
            buyersToRemove.removeAll(allowedAdTechs);
        }

        long numDisallowedBuyersFound = buyersToRemove.size();
        long numRemovedCustomAudiences = 0;
        if (!buyersToRemove.isEmpty()) {
            deleteCustomAudienceBackgroundFetchDataByBuyer(buyersToRemove);
            numRemovedCustomAudiences = deleteCustomAudiencesByBuyer(buyersToRemove);
        }

        return CustomAudienceStats.builder()
                .setTotalCustomAudienceCount(numRemovedCustomAudiences)
                .setTotalBuyerCount(numDisallowedBuyersFound)
                .build();
    }

    /**
     * Deletes ALL custom audiences from the table.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteAllCustomAudienceData()} instead.
     */
    @Query("DELETE FROM custom_audience")
    protected abstract void deleteAllCustomAudiences();

    /**
     * Deletes ALL custom audience background fetch data from the table.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteAllCustomAudienceData()} instead.
     */
    @Query("DELETE FROM custom_audience_background_fetch_data")
    protected abstract void deleteAllCustomAudienceBackgroundFetchData();

    /**
     * Deletes ALL custom audience overrides from the table.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteAllCustomAudienceData()} instead.
     */
    @Query("DELETE FROM custom_audience_overrides")
    protected abstract void deleteAllCustomAudienceOverrides();

    /** Deletes ALL custom audience data from the database in a single transaction. */
    @Transaction
    public void deleteAllCustomAudienceData() {
        deleteAllCustomAudiences();
        deleteAllCustomAudienceBackgroundFetchData();
        deleteAllCustomAudienceOverrides();
    }

    /**
     * Deletes all custom audiences belonging to the {@code owner} application from the table.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteCustomAudienceDataByOwner(String)} instead.
     */
    @Query("DELETE FROM custom_audience WHERE owner = :owner")
    protected abstract void deleteCustomAudiencesByOwner(@NonNull String owner);

    /**
     * Deletes all custom audience background fetch data belonging to the {@code owner} application
     * from the table.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteCustomAudienceDataByOwner(String)} instead.
     */
    @Query("DELETE FROM custom_audience_background_fetch_data WHERE owner = :owner")
    protected abstract void deleteCustomAudienceBackgroundFetchDataByOwner(@NonNull String owner);

    /**
     * Deletes all custom audience overrides belonging to the {@code owner} application from the
     * table.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteCustomAudienceDataByOwner(String)} instead.
     */
    @Query("DELETE FROM custom_audience_overrides WHERE owner = :owner")
    protected abstract void deleteCustomAudienceOverridesByOwner(@NonNull String owner);

    /**
     * Deletes all custom audience data belonging to the {@code owner} application from the database
     * in a single transaction.
     */
    @Transaction
    public void deleteCustomAudienceDataByOwner(@NonNull String owner) {
        deleteCustomAudiencesByOwner(owner);
        deleteCustomAudienceBackgroundFetchDataByOwner(owner);
        deleteCustomAudienceOverridesByOwner(owner);
    }

    /** Clean up selected custom audience override data by its primary key */
    @Query(
            "DELETE FROM custom_audience_overrides WHERE owner = :owner AND buyer = :buyer "
                    + "AND name = :name AND app_package_name = :appPackageName")
    public abstract void removeCustomAudienceOverrideByPrimaryKeyAndPackageName(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull String appPackageName);

    /** Clean up all custom audience override data for the given package name. */
    @Query("DELETE FROM custom_audience_overrides WHERE app_package_name = :appPackageName")
    public abstract void removeCustomAudienceOverridesByPackageName(@NonNull String appPackageName);

    /**
     * Fetch all active Custom Audience including null user_bidding_signals
     *
     * @param currentTime to compare against CA time values and find an active CA
     * @return All the Custom Audience that represent
     */
    @Query(
            "SELECT * FROM custom_audience WHERE activation_time <= (:currentTime) AND"
                    + " (:currentTime) < expiration_time AND"
                    + " (last_ads_and_bidding_data_updated_time + (:activeWindowTimeMs)) >="
                    + " (:currentTime) AND trusted_bidding_data_uri IS NOT NULL AND ads IS"
                    + " NOT NULL ")
    @Nullable
    public abstract List<DBCustomAudience> getAllActiveCustomAudienceForServerSideAuction(
            Instant currentTime, long activeWindowTimeMs);

    /**
     * Fetch all the Custom Audience corresponding to the buyers
     *
     * @param buyers associated with the Custom Audience
     * @param currentTime to compare against CA time values and find an active CA
     * @return All the Custom Audience that represent given buyers
     */
    @Query(
            "SELECT * FROM custom_audience WHERE buyer in (:buyers) AND activation_time <="
                    + " (:currentTime) AND (:currentTime) < expiration_time AND"
                    + " (last_ads_and_bidding_data_updated_time + (:activeWindowTimeMs)) >="
                    + " (:currentTime) AND user_bidding_signals IS NOT NULL AND"
                    + " trusted_bidding_data_uri IS NOT NULL AND ads IS NOT NULL ")
    @Nullable
    public abstract List<DBCustomAudience> getActiveCustomAudienceByBuyers(
            List<AdTechIdentifier> buyers, Instant currentTime, long activeWindowTimeMs);

    /**
     * Gets up to {@code maxRowsReturned} rows of {@link DBCustomAudienceBackgroundFetchData} which
     * correspond to custom audiences that are active, not expired, and eligible for update.
     */
    @Query(
            "SELECT bgf.* FROM custom_audience_background_fetch_data AS bgf "
                    + "INNER JOIN custom_audience AS ca "
                    + "ON bgf.buyer = ca.buyer AND bgf.owner = ca.owner AND bgf.name = ca.name "
                    + "WHERE bgf.eligible_update_time <= :currentTime "
                    + "AND ca.activation_time <= :currentTime "
                    + "AND :currentTime < ca.expiration_time "
                    + "ORDER BY ca.last_ads_and_bidding_data_updated_time ASC "
                    + "LIMIT :maxRowsReturned")
    @NonNull
    public abstract List<DBCustomAudienceBackgroundFetchData>
            getActiveEligibleCustomAudienceBackgroundFetchData(
                    @NonNull Instant currentTime, long maxRowsReturned);

    /**
     * Gets the number of all {@link DBCustomAudienceBackgroundFetchData} for custom audiences that
     * are active, not expired, and eligible for update.
     */
    @Query(
            "SELECT COUNT(DISTINCT bgf.ROWID) FROM custom_audience_background_fetch_data AS bgf "
                    + "INNER JOIN custom_audience AS ca "
                    + "ON bgf.buyer = ca.buyer AND bgf.owner = ca.owner AND bgf.name = ca.name "
                    + "WHERE bgf.eligible_update_time <= :currentTime "
                    + "AND ca.activation_time <= :currentTime "
                    + "AND :currentTime < ca.expiration_time")
    public abstract int getNumActiveEligibleCustomAudienceBackgroundFetchData(
            @NonNull Instant currentTime);

    /**
     * Persists a delayed Custom Audience Update along with the overrides
     *
     * @param update delayed update
     * @param partialCustomAudienceList overrides for incoming custom audiences
     */
    // TODO(b/324478492) Refactor Update queries in a separate Dao
    @Transaction
    public void insertScheduledUpdateAndPartialCustomAudienceList(
            @NonNull DBScheduledCustomAudienceUpdate update,
            @NonNull List<PartialCustomAudience> partialCustomAudienceList) {
        long updateId = insertScheduledCustomAudienceUpdate(update);

        List<DBPartialCustomAudience> dbPartialCustomAudienceList =
                partialCustomAudienceList.stream()
                        .map(
                                partialCa ->
                                        DBPartialCustomAudience.builder()
                                                .setUpdateId(updateId)
                                                .setName(partialCa.getName())
                                                .setActivationTime(partialCa.getActivationTime())
                                                .setExpirationTime(partialCa.getExpirationTime())
                                                .setUserBiddingSignals(
                                                        partialCa.getUserBiddingSignals())
                                                .build())
                        .collect(Collectors.toList());
        insertPartialCustomAudiencesForUpdate(dbPartialCustomAudienceList);
    }

    /** Gets updates schedule before a given time along with its corresponding overrides */
    @Transaction
    public List<Pair<DBScheduledCustomAudienceUpdate, List<DBPartialCustomAudience>>>
            getScheduledUpdatesAndOverridesBeforeTime(@NonNull Instant timestamp) {

        List<DBScheduledCustomAudienceUpdate> scheduledUpdates =
                getCustomAudienceUpdatesScheduledBeforeTime(timestamp);

        List<Pair<DBScheduledCustomAudienceUpdate, List<DBPartialCustomAudience>>> updatesList =
                new ArrayList<>();
        scheduledUpdates.forEach(
                update ->
                        updatesList.add(
                                new Pair(
                                        update,
                                        getPartialAudienceListForUpdateId(update.getUpdateId()))));
        return updatesList;
    }

    /** Persists a delayed Custom Audience Update and generated a unique update_id */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract long insertScheduledCustomAudienceUpdate(
            @NonNull DBScheduledCustomAudienceUpdate update);

    /** Persists Custom Audience Overrides associated with a delayed update */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertPartialCustomAudiencesForUpdate(
            @NonNull List<DBPartialCustomAudience> partialCustomAudienceList);

    /** Gets Custom Audience Overrides associated with a delayed update */
    @Query("SELECT * FROM partial_custom_audience WHERE update_id = :updateId")
    public abstract List<DBPartialCustomAudience> getPartialAudienceListForUpdateId(Long updateId);

    /** Gets list of delayed Custom Audience Updates scheduled before the given time */
    @Query("SELECT * FROM scheduled_custom_audience_update WHERE scheduled_time <= :timestamp")
    public abstract List<DBScheduledCustomAudienceUpdate>
            getCustomAudienceUpdatesScheduledBeforeTime(Instant timestamp);

    /** Gets list of delayed Custom Audience Updates created before the given time */
    @Query("DELETE FROM scheduled_custom_audience_update WHERE creation_time <= :timestamp")
    public abstract void deleteScheduledCustomAudienceUpdatesCreatedBeforeTime(Instant timestamp);

    /**
     * Removes a Custom Audience Update from storage and cascades the deletion to associated Partial
     * Custom Audiences for overrides
     */
    @Delete
    public abstract void deleteScheduledCustomAudienceUpdate(
            @NonNull DBScheduledCustomAudienceUpdate update);

    @VisibleForTesting
    static class BiddingLogicJsWithVersion {
        @ColumnInfo(name = "bidding_logic_js")
        @NonNull
        String mBiddingLogicJs;

        @ColumnInfo(name = "buyer_bidding_logic_version")
        @Nullable
        Long mBuyerBiddingLogicVersion;

        BiddingLogicJsWithVersion(
                @NonNull String biddingLogicJs, @Nullable Long buyerBiddingLogicVersion) {
            this.mBiddingLogicJs = biddingLogicJs;
            this.mBuyerBiddingLogicVersion = buyerBiddingLogicVersion;
        }

        @NonNull
        public String getBiddingLogicJs() {
            return mBiddingLogicJs;
        }

        @Nullable
        public Long getBuyerBiddingLogicVersion() {
            return mBuyerBiddingLogicVersion;
        }
    }
}
