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

package com.android.server.healthconnect.migration;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.migration.AppInfoMigrationPayload;
import android.health.connect.migration.MetadataMigrationPayload;
import android.health.connect.migration.MigrationEntity;
import android.health.connect.migration.MigrationPayload;
import android.health.connect.migration.PermissionMigrationPayload;
import android.health.connect.migration.PriorityMigrationPayload;
import android.health.connect.migration.RecordMigrationPayload;
import android.os.UserHandle;

import com.android.internal.annotations.GuardedBy;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthConnectPermissionHelper;
import com.android.server.healthconnect.storage.AutoDeleteService;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.ActivityDateHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MigrationEntityHelper;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;
import com.android.server.healthconnect.storage.utils.StorageUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Controls the data migration flow. Accepts and applies collections of {@link MigrationEntity}.
 *
 * @hide
 */
public final class DataMigrationManager {

    private static final Object sLock = new Object();

    private final Context mUserContext;
    private final TransactionManager mTransactionManager;
    private final HealthConnectPermissionHelper mPermissionHelper;
    private final FirstGrantTimeManager mFirstGrantTimeManager;
    private final DeviceInfoHelper mDeviceInfoHelper;
    private final AppInfoHelper mAppInfoHelper;
    private final MigrationEntityHelper mMigrationEntityHelper;
    private final RecordHelperProvider mRecordHelperProvider;
    private final PriorityMigrationHelper mPriorityMigrationHelper;
    private final HealthDataCategoryPriorityHelper mHealthDataCategoryPriorityHelper;
    private final ActivityDateHelper mActivityDateHelper;

    public DataMigrationManager(
            @NonNull Context userContext,
            @NonNull TransactionManager transactionManager,
            @NonNull HealthConnectPermissionHelper permissionHelper,
            @NonNull FirstGrantTimeManager firstGrantTimeManager,
            @NonNull DeviceInfoHelper deviceInfoHelper,
            @NonNull AppInfoHelper appInfoHelper,
            @NonNull MigrationEntityHelper migrationEntityHelper,
            @NonNull RecordHelperProvider recordHelperProvider,
            @NonNull HealthDataCategoryPriorityHelper healthDataCategoryPriorityHelper,
            @NonNull PriorityMigrationHelper priorityMigrationHelper,
            @NonNull ActivityDateHelper activityDateHelper) {
        mUserContext = userContext;
        mTransactionManager = transactionManager;
        mPermissionHelper = permissionHelper;
        mFirstGrantTimeManager = firstGrantTimeManager;
        mDeviceInfoHelper = deviceInfoHelper;
        mAppInfoHelper = appInfoHelper;
        mMigrationEntityHelper = migrationEntityHelper;
        mRecordHelperProvider = recordHelperProvider;
        mHealthDataCategoryPriorityHelper = healthDataCategoryPriorityHelper;
        mPriorityMigrationHelper = priorityMigrationHelper;
        mActivityDateHelper = activityDateHelper;
    }

    /**
     * Parses and applies the provided migration entities.
     *
     * @param entities a collection of {@link MigrationEntity} to be applied.
     */
    public void apply(@NonNull Collection<MigrationEntity> entities) throws EntityWriteException {
        synchronized (sLock) {
            mTransactionManager.runAsTransaction(
                    db -> {
                        // Grab the lock again to make sure error-prone is happy, and so that tests
                        // break if the following code is run asynchronously
                        synchronized (sLock) {
                            for (MigrationEntity entity : entities) {
                                migrateEntity(db, entity);
                            }
                        }
                    });
        }
    }

    /** Migrates the provided {@link MigrationEntity}. Must be called inside a DB transaction. */
    @GuardedBy("sLock")
    private void migrateEntity(@NonNull SQLiteDatabase db, @NonNull MigrationEntity entity)
            throws EntityWriteException {
        try {
            if (checkEntityForDuplicates(db, entity)) {
                return;
            }

            final MigrationPayload payload = entity.getPayload();
            if (payload instanceof RecordMigrationPayload) {
                migrateRecord(db, (RecordMigrationPayload) payload);
            } else if (payload instanceof PermissionMigrationPayload) {
                migratePermissions((PermissionMigrationPayload) payload);
            } else if (payload instanceof AppInfoMigrationPayload) {
                migrateAppInfo((AppInfoMigrationPayload) payload);
            } else if (payload instanceof PriorityMigrationPayload) {
                migratePriority((PriorityMigrationPayload) payload);
            } else if (payload instanceof MetadataMigrationPayload) {
                migrateMetadata((MetadataMigrationPayload) payload);
            } else {
                throw new IllegalArgumentException("Unsupported payload type: " + payload);
            }
        } catch (RuntimeException e) {
            throw new EntityWriteException(entity.getEntityId(), e);
        }
    }

    @GuardedBy("sLock")
    private void migrateRecord(
            @NonNull SQLiteDatabase db, @NonNull RecordMigrationPayload payload) {
        long recordRowId = mTransactionManager.insertOrIgnore(db, parseRecord(payload));
        if (recordRowId != -1) {
            mTransactionManager.insertOrIgnore(
                    db, mActivityDateHelper.getUpsertTableRequest(payload.getRecordInternal()));
        }
    }

    @NonNull
    private UpsertTableRequest parseRecord(@NonNull RecordMigrationPayload payload) {
        final RecordInternal<?> record = payload.getRecordInternal();
        mAppInfoHelper.populateAppInfoId(record, mUserContext, false);
        mDeviceInfoHelper.populateDeviceInfoId(record);

        if (record.getUuid() == null) {
            StorageUtils.addNameBasedUUIDTo(record);
        }

        return mRecordHelperProvider
                .getRecordHelper(record.getRecordType())
                .getUpsertTableRequest(record);
    }

    @GuardedBy("sLock")
    private void migratePermissions(@NonNull PermissionMigrationPayload payload) {
        final String packageName = payload.getHoldingPackageName();
        final List<String> permissions = payload.getPermissions();
        final UserHandle userHandle = mUserContext.getUser();

        if (permissions.isEmpty()
                || mPermissionHelper.hasGrantedHealthPermissions(packageName, userHandle)) {
            return;
        }

        final List<Exception> errors = new ArrayList<>();

        for (String permissionName : permissions) {
            try {
                mPermissionHelper.grantHealthPermission(packageName, permissionName, userHandle);
            } catch (Exception e) {
                errors.add(e);
            }
        }

        // Throw if no permissions were migrated
        if (errors.size() == permissions.size()) {
            final RuntimeException error =
                    new RuntimeException(
                            "Error migrating permissions for "
                                    + packageName
                                    + ": "
                                    + String.join(", ", payload.getPermissions()));
            for (Exception e : errors) {
                error.addSuppressed(e);
            }
            throw error;
        }

        mFirstGrantTimeManager.setFirstGrantTime(
                packageName, payload.getFirstGrantTime(), userHandle);
    }

    @GuardedBy("sLock")
    private void migrateAppInfo(@NonNull AppInfoMigrationPayload payload) {
        mAppInfoHelper.addOrUpdateAppInfoIfNotInstalled(
                mUserContext,
                payload.getPackageName(),
                payload.getAppName(),
                payload.getAppIcon(),
                true /* onlyReplace */);
    }

    /**
     * Checks the provided entity for duplicates by {@code entityId}. Modifies {@link
     * MigrationEntityHelper} table as a side effect.
     *
     * <p>Entities with the following payload types are exempt from deduplication checks (the result
     * is always {@code false}): {@link RecordMigrationPayload}.
     *
     * @return {@code true} if the entity is duplicated and thus should be ignored, {@code false}
     *     otherwise.
     */
    @GuardedBy("sLock")
    private boolean checkEntityForDuplicates(
            @NonNull SQLiteDatabase db, @NonNull MigrationEntity entity) {
        final MigrationPayload payload = entity.getPayload();

        if (payload instanceof RecordMigrationPayload) {
            return false; // Do not deduplicate records by entityId
        }

        return !insertEntityIdIfNotPresent(db, entity.getEntityId());
    }

    /**
     * Inserts the provided {@code entity} into the database if it doesn't exist yet. Used for data
     * deduplication.
     *
     * @return {@code true} if inserted successfully, {@code false} otherwise.
     */
    @GuardedBy("sLock")
    private boolean insertEntityIdIfNotPresent(
            @NonNull SQLiteDatabase db, @NonNull String entityId) {
        final UpsertTableRequest request = mMigrationEntityHelper.getInsertRequest(entityId);
        return mTransactionManager.insertOrIgnore(db, request) != -1;
    }

    /** Indicates an error during entity migration. */
    public static final class EntityWriteException extends Exception {
        private final String mEntityId;

        private EntityWriteException(@NonNull String entityId, @Nullable Throwable cause) {
            super("Error writing entity: " + entityId, cause);

            mEntityId = entityId;
        }

        /**
         * Returns an identifier of the failed entity, as specified in {@link
         * MigrationEntity#getEntityId()}.
         */
        @NonNull
        public String getEntityId() {
            return mEntityId;
        }
    }

    /**
     * Internal method to migrate priority list of packages for data category
     *
     * @param priorityMigrationPayload contains data category and priority list
     */
    private void migratePriority(@NonNull PriorityMigrationPayload priorityMigrationPayload) {
        if (priorityMigrationPayload.getDataOrigins().isEmpty()) {
            return;
        }

        List<String> priorityToMigrate =
                priorityMigrationPayload.getDataOrigins().stream()
                        .map(dataOrigin -> dataOrigin.getPackageName())
                        .toList();

        List<String> preMigrationPriority =
                mAppInfoHelper.getPackageNames(
                        mPriorityMigrationHelper.getPreMigrationPriority(
                                priorityMigrationPayload.getDataCategory()));

        /*
        The combined priority would contain priority order from module appended by additional
        packages from apk priority order.
        */
        List<String> combinedPriorityOrder =
                Stream.concat(preMigrationPriority.stream(), priorityToMigrate.stream())
                        .distinct()
                        .collect(Collectors.toList());

        /*
         * setPriorityOrder removes any additional packages that were not present already in
         * priority, and it adds any package in priority that was present earlier but missing in
         * updated priority. This means it will remove any package that don't have required
         * permission for category as well as it will remove any package that is uninstalled.
         */
        mHealthDataCategoryPriorityHelper.setPriorityOrder(
                priorityMigrationPayload.getDataCategory(), combinedPriorityOrder);
    }

    /**
     * Migrates Metadata like recordRetentionPeriod
     *
     * @param payload of type MetadataMigrationPayload having retention period.
     */
    private void migrateMetadata(MetadataMigrationPayload payload) {
        AutoDeleteService.setRecordRetentionPeriodInDays(payload.getRecordRetentionPeriodDays());
    }
}
