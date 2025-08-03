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

package com.android.server.healthconnect.storage.datatypehelpers;

import static android.health.connect.Constants.DEBUG;
import static android.health.connect.Constants.DEFAULT_LONG;

import static com.android.server.healthconnect.storage.request.UpsertTableRequest.TYPE_STRING;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BLOB;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL_UNIQUE;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorBlob;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorString;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ApplicationInfoFlags;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.health.connect.Constants;
import android.health.connect.datatypes.AppInfo;
import android.health.connect.internal.datatypes.AppInfoInternal;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.utils.RecordMapper;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A class to help with the DB transaction for storing Application Info. {@link AppInfoHelper} acts
 * as a layer b/w the application_igenfo_table stored in the DB and helps perform insert and read
 * operations on the table
 *
 * @hide
 */
public final class AppInfoHelper extends DatabaseHelper {
    public static final String TABLE_NAME = "application_info_table";
    public static final String APPLICATION_COLUMN_NAME = "app_name";
    public static final String PACKAGE_COLUMN_NAME = "package_name";
    public static final List<Pair<String, Integer>> UNIQUE_COLUMN_INFO =
            Collections.singletonList(new Pair<>(PACKAGE_COLUMN_NAME, TYPE_STRING));
    public static final String APP_ICON_COLUMN_NAME = "app_icon";
    private static final String TAG = "HealthConnectAppInfoHelper";
    private static final String RECORD_TYPES_USED_COLUMN_NAME = "record_types_used";
    private static final int COMPRESS_FACTOR = 100;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    private static volatile AppInfoHelper sAppInfoHelper;

    /**
     * Map to store appInfoId -> packageName mapping for populating record for read
     *
     * <p>TO HAVE THREAD SAFETY DON'T USE THESE VARIABLES DIRECTLY, INSTEAD USE ITS GETTER
     */
    private volatile ConcurrentHashMap<Long, String> mIdPackageNameMap;

    /**
     * Map to store application package-name -> AppInfo mapping (such as packageName -> appName,
     * icon, rowId in the DB etc.)
     *
     * <p>TO HAVE THREAD SAFETY DON'T USE THESE VARIABLES DIRECTLY, INSTEAD USE ITS GETTER
     */
    private volatile ConcurrentHashMap<String, AppInfoInternal> mAppInfoMap;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    private AppInfoHelper() {}

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    public synchronized void clearCache() {
        mAppInfoMap = null;
        mIdPackageNameMap = null;
    }

    @Override
    protected String getMainTableName() {
        return TABLE_NAME;
    }

    /**
     * Returns a requests representing the tables that should be created corresponding to this
     * helper
     */
    @NonNull
    public CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(TABLE_NAME, getColumnInfo());
    }

    /** Populates record with appInfoId */
    public void populateAppInfoId(
            @NonNull RecordInternal<?> record, @NonNull Context context, boolean requireAllFields) {
        final String packageName = requireNonNull(record.getPackageName());
        AppInfoInternal appInfo = getAppInfoMap().get(packageName);

        if (appInfo == null) {
            try {
                appInfo = getAppInfo(packageName, context);
            } catch (NameNotFoundException e) {
                if (requireAllFields) {
                    throw new IllegalArgumentException("Could not find package info", e);
                }

                appInfo =
                        new AppInfoInternal(
                                DEFAULT_LONG, packageName, record.getAppName(), null, null);
            }

            insertIfNotPresent(packageName, appInfo);
        }

        record.setAppInfoId(appInfo.getId());
        record.setPackageName(appInfo.getPackageName());
    }

    /**
     * Inserts or replaces (based on the passed param onlyUpdate) the application info of the
     * specified {@code packageName} with the specified {@code name} and {@code icon}, only if the
     * corresponding application is not currently installed.
     *
     * <p>If onlyUpdate is true then only replace the exiting AppInfo; no new insertion. If
     * onlyUpdate is false then only insert a new AppInfo entry; no replacement.
     */
    public void addOrUpdateAppInfoIfNotInstalled(
            @NonNull Context context,
            @NonNull String packageName,
            @Nullable String name,
            @Nullable byte[] icon,
            boolean onlyUpdate) {
        if (!isAppInstalled(context, packageName)) {
            // using pre-existing value of recordTypesUsed.
            @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
            var recordTypesUsed =
                    containsAppInfo(packageName)
                            ? mAppInfoMap.get(packageName).getRecordTypesUsed()
                            : null;
            AppInfoInternal appInfoInternal =
                    new AppInfoInternal(
                            DEFAULT_LONG, packageName, name, decodeBitmap(icon), recordTypesUsed);
            if (onlyUpdate) {
                updateIfPresent(packageName, appInfoInternal);
            } else {
                insertIfNotPresent(packageName, appInfoInternal);
            }
        }
    }

    private boolean isAppInstalled(@NonNull Context context, @NonNull String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, ApplicationInfoFlags.of(0));
            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    /**
     * @return id of {@code packageName} or {@link Constants#DEFAULT_LONG} if the id is not found
     */
    public long getAppInfoId(String packageName) {
        if (packageName == null) {
            return DEFAULT_LONG;
        }

        AppInfoInternal appInfo = getAppInfoMap().getOrDefault(packageName, null);

        if (appInfo == null) {
            return DEFAULT_LONG;
        }
        return appInfo.getId();
    }

    private boolean containsAppInfo(String packageName) {
        return getAppInfoMap().containsKey(packageName);
    }

    /**
     * @param packageNames List of package names
     * @return A list of appinfo ids from the application_info_table.
     */
    public List<Long> getAppInfoIds(List<String> packageNames) {
        if (DEBUG) {
            Slog.d(TAG, "App info map: " + mAppInfoMap);
        }
        if (packageNames == null || packageNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> result = new ArrayList<>(packageNames.size());
        packageNames.forEach(packageName -> result.add(getAppInfoId(packageName)));

        return result;
    }

    /** Gets the package name corresponding to the {@code packageId}. */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @NonNull
    public String getPackageName(long packageId) {
        return getIdPackageNameMap().get(packageId);
    }

    @NonNull
    public List<String> getPackageNames(List<Long> packageIds) {
        if (packageIds == null || packageIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> packageNames = new ArrayList<>();
        packageIds.forEach(
                (packageId) -> {
                    String packageName = getPackageName(packageId);
                    requireNonNull(packageName);

                    packageNames.add(packageName);
                });

        return packageNames;
    }

    /** Returns a list of AppInfo objects which are contributing data to some recordType. */
    public List<AppInfo> getApplicationInfosWithRecordTypes() {
        return getAppInfoMap().values().stream()
                .filter(
                        (appInfo) ->
                                (appInfo.getRecordTypesUsed() != null
                                        && !appInfo.getRecordTypesUsed().isEmpty()))
                .map(AppInfoInternal::toExternal)
                .collect(Collectors.toList());
    }

    /** Returns AppInfo id for the provided {@code packageName}, creating it if needed. */
    public long getOrInsertAppInfoId(@NonNull String packageName, @NonNull Context context) {
        AppInfoInternal appInfoInternal = getAppInfoMap().get(packageName);

        if (appInfoInternal == null) {
            try {
                appInfoInternal = getAppInfo(packageName, context);
            } catch (NameNotFoundException e) {
                throw new IllegalArgumentException("Could not find package info for package", e);
            }

            insertIfNotPresent(packageName, appInfoInternal);
        }

        return appInfoInternal.getId();
    }

    private synchronized void populateAppInfoMap() {
        if (mAppInfoMap != null) {
            return;
        }
        ConcurrentHashMap<String, AppInfoInternal> appInfoMap = new ConcurrentHashMap<>();
        ConcurrentHashMap<Long, String> idPackageNameMap = new ConcurrentHashMap<>();
        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        try (Cursor cursor = transactionManager.read(new ReadTableRequest(TABLE_NAME))) {
            while (cursor.moveToNext()) {
                long rowId = getCursorLong(cursor, RecordHelper.PRIMARY_COLUMN_NAME);
                String packageName = getCursorString(cursor, PACKAGE_COLUMN_NAME);
                String appName = getCursorString(cursor, APPLICATION_COLUMN_NAME);
                byte[] icon = getCursorBlob(cursor, APP_ICON_COLUMN_NAME);
                Bitmap bitmap = decodeBitmap(icon);
                String recordTypesUsed = getCursorString(cursor, RECORD_TYPES_USED_COLUMN_NAME);

                Set<Integer> recordTypesListAsSet = getRecordTypesAsSet(recordTypesUsed);

                appInfoMap.put(
                        packageName,
                        new AppInfoInternal(
                                rowId, packageName, appName, bitmap, recordTypesListAsSet));
                idPackageNameMap.put(rowId, packageName);
            }
        }
        mAppInfoMap = appInfoMap;
        mIdPackageNameMap = idPackageNameMap;
    }

    @Nullable
    private Set<Integer> getRecordTypesAsSet(String recordTypesUsed) {
        if (recordTypesUsed != null && !recordTypesUsed.isEmpty()) {
            return Arrays.stream(recordTypesUsed.split(","))
                    .map(Integer::parseInt)
                    .collect(Collectors.toSet());
        }
        return null;
    }

    /**
     * Updates recordTypesUsed for the {@code packageName} in app info table.
     *
     * <p><b>NOTE:</b> This method should only be used for insert operation on recordType tables.
     * Should not be called elsewhere.
     *
     * <p>see {@link AppInfoHelper#syncAppInfoMapRecordTypesUsed(Map)}} for updating this table
     * during delete operations on recordTypes.
     *
     * @param recordTypes The record types that needs to be inserted.
     * @param packageName The package for which the records need to be inserted.
     */
    @SuppressLint("LongLogTag")
    public synchronized void updateAppInfoRecordTypesUsedOnInsert(
            Set<Integer> recordTypes, String packageName) {
        AppInfoInternal appInfo = getAppInfoMap().get(packageName);
        if (appInfo == null) {
            Log.e(
                    TAG,
                    "AppInfo for the current package: "
                            + packageName
                            + " does not exist. "
                            + "Hence recordTypesUsed is not getting updated.");

            return;
        }

        if (recordTypes == null || recordTypes.isEmpty()) {
            return;
        }
        Set<Integer> updatedRecordTypes = new HashSet<>(recordTypes);
        if (appInfo.getRecordTypesUsed() != null) {
            updatedRecordTypes.addAll(appInfo.getRecordTypesUsed());
        }
        if (!updatedRecordTypes.equals(appInfo.getRecordTypesUsed())) {
            updateAppInfoRecordTypesUsedSync(packageName, appInfo, updatedRecordTypes);
        }
    }

    /**
     * Updates recordTypesUsed by for all packages in app info table.
     *
     * <p><b>NOTE:</b> This method should only be used for delete operation on recordType tables.
     * Should not be called elsewhere.
     *
     * <p>Use this method to update the table for passed recordTypes, not passing any record will
     * update all recordTypes.
     *
     * <p>see {@link AppInfoHelper#updateAppInfoRecordTypesUsedOnInsert(Set, String)} for updating
     * this table during insert operations on recordTypes.
     */
    public synchronized void syncAppInfoRecordTypesUsed() {
        syncAppInfoRecordTypesUsed(null);
    }

    /**
     * Updates recordTypesUsed by for all packages in app info table.
     *
     * <p><b>NOTE:</b> This method should only be used for delete operation on recordType tables.
     * Should not be called elsewhere.
     *
     * <p>Use this method to update the table for passed {@code recordTypesToBeSynced}, not passing
     * any record will update all recordTypes.
     *
     * <p>see {@link AppInfoHelper#updateAppInfoRecordTypesUsedOnInsert(Set, String)} for updating
     * this table during insert operations on recordTypes.
     */
    public synchronized void syncAppInfoRecordTypesUsed(
            @Nullable Set<Integer> recordTypesToBeSynced) {
        Set<Integer> recordTypesToBeUpdated =
                Objects.requireNonNullElseGet(
                        recordTypesToBeSynced,
                        () ->
                                RecordMapper.getInstance()
                                        .getRecordIdToExternalRecordClassMap()
                                        .keySet());

        HashMap<Integer, HashSet<String>> recordTypeToContributingPackagesMap =
                TransactionManager.getInitialisedInstance()
                        .getDistinctPackageNamesForRecordsTable(recordTypesToBeUpdated);

        if (recordTypesToBeSynced == null) {
            syncAppInfoMapRecordTypesUsed(recordTypeToContributingPackagesMap);
        } else {
            getAppInfoMap()
                    .keySet()
                    .forEach(
                            (packageName) -> {
                                deleteRecordTypesForPackagesIfRequiredInternal(
                                        recordTypesToBeUpdated,
                                        recordTypeToContributingPackagesMap,
                                        packageName);
                            });
        }
    }

    /**
     * This method updates recordTypesUsed for all packages and hence is a heavy operation. This
     * method is used during AutoDeleteService and is run once per day.
     */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @SuppressLint("LongLogTag")
    private synchronized void syncAppInfoMapRecordTypesUsed(
            @NonNull Map<Integer, HashSet<String>> recordTypeToContributingPackagesMap) {
        HashMap<String, List<Integer>> packageToRecordTypesMap =
                getPackageToRecordTypesMap(recordTypeToContributingPackagesMap);
        getAppInfoMap()
                .forEach(
                        (packageName, appInfo) -> {
                            if (packageToRecordTypesMap.containsKey(packageName)) {
                                updateAppInfoRecordTypesUsedSync(
                                        packageName,
                                        appInfo,
                                        new HashSet<>(packageToRecordTypesMap.get(packageName)));
                            } else {
                                updateAppInfoRecordTypesUsedSync(
                                        packageName, appInfo, /* recordTypesUsed */ null);
                            }
                            if (DEBUG) {
                                Log.d(
                                        TAG,
                                        "Syncing packages and corresponding recordTypesUsed for"
                                                + " package : "
                                                + packageName
                                                + ", recordTypesUsed : "
                                                + appInfo.getRecordTypesUsed());
                            }
                        });
    }

    private HashMap<String, List<Integer>> getPackageToRecordTypesMap(
            @NonNull Map<Integer, HashSet<String>> recordTypeToContributingPackagesMap) {
        HashMap<String, List<Integer>> packageToRecordTypesMap = new HashMap<>();
        recordTypeToContributingPackagesMap.forEach(
                (recordType, packageList) -> {
                    packageList.forEach(
                            (packageName) -> {
                                if (packageToRecordTypesMap.containsKey(packageName)) {
                                    packageToRecordTypesMap.get(packageName).add(recordType);
                                } else {
                                    packageToRecordTypesMap.put(
                                            packageName,
                                            new ArrayList<>() {
                                                {
                                                    add(recordType);
                                                }
                                            });
                                }
                            });
                });
        return packageToRecordTypesMap;
    }

    /**
     * Checks and deletes record types in app info table for which the package is no longer
     * contributing data. This is done after delete records operation has been performed.
     */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @SuppressLint("LongLogTag")
    private synchronized void deleteRecordTypesForPackagesIfRequiredInternal(
            Set<Integer> recordTypesToBeDeleted,
            HashMap<Integer, HashSet<String>> currentRecordTypePackageMap,
            String packageName) {
        AppInfoInternal appInfo = getAppInfoMap().get(packageName);
        if (appInfo == null) {
            Log.e(
                    TAG,
                    "AppInfo for the current package: "
                            + packageName
                            + " does not exist. "
                            + "Hence recordTypesUsed is not getting updated.");

            return;
        }
        if (appInfo.getRecordTypesUsed() == null || appInfo.getRecordTypesUsed().isEmpty()) {
            // return since this package is not contributing to any recordType and hence there
            // is nothing to delete.
            return;
        }
        Set<Integer> updatedRecordTypesUsed = new HashSet<>(appInfo.getRecordTypesUsed());
        for (Integer recordType : recordTypesToBeDeleted) {
            // get the distinct packages used by the record after the deletion process, check if
            // the recordType does not have the current package then remove record type from
            // the package's app info record.
            if (!currentRecordTypePackageMap.get(recordType).contains(packageName)) {
                updatedRecordTypesUsed.remove(recordType);
            }
        }
        if (updatedRecordTypesUsed.equals(appInfo.getRecordTypesUsed())) {
            return;
        }
        if (updatedRecordTypesUsed.isEmpty()) {
            updatedRecordTypesUsed = null;
        }
        updateAppInfoRecordTypesUsedSync(packageName, appInfo, updatedRecordTypesUsed);
    }

    @SuppressLint("LongLogTag")
    private synchronized void updateAppInfoRecordTypesUsedSync(
            @NonNull String packageName,
            @NonNull AppInfoInternal appInfo,
            Set<Integer> recordTypesUsed) {
        appInfo.setRecordTypesUsed(recordTypesUsed);
        // create upsert table request to modify app info table, keyed by packages name.
        WhereClauses whereClauseForAppInfoTableUpdate = new WhereClauses(AND);
        whereClauseForAppInfoTableUpdate.addWhereEqualsClause(
                PACKAGE_COLUMN_NAME, appInfo.getPackageName());
        UpsertTableRequest upsertRequestForAppInfoUpdate =
                new UpsertTableRequest(
                        TABLE_NAME, getContentValues(packageName, appInfo), UNIQUE_COLUMN_INFO);
        TransactionManager.getInitialisedInstance().update(upsertRequestForAppInfoUpdate);

        // update locally stored maps to keep data in sync.
        getAppInfoMap().put(packageName, appInfo);
        getIdPackageNameMap().put(appInfo.getId(), packageName);
        if (DEBUG) {
            Log.d(
                    TAG,
                    "Updated app info table. PackageName : "
                            + packageName
                            + " , RecordTypesUsed : "
                            + appInfo.getRecordTypesUsed()
                            + ".");
        }
    }

    /** Returns a map for recordTypes and their contributing packages. */
    public Map<Integer, Set<String>> getRecordTypesToContributingPackagesMap() {
        Map<Integer, Set<String>> recordTypeContributingPackagesMap = new HashMap<>();
        Map<String, AppInfoInternal> appInfoMap = getAppInfoMap();
        appInfoMap.forEach(
                (packageName, appInfo) -> {
                    Set<Integer> recordTypesUsed = appInfo.getRecordTypesUsed();
                    if (recordTypesUsed != null) {
                        recordTypesUsed.forEach(
                                (recordType) -> {
                                    if (recordTypeContributingPackagesMap.containsKey(recordType)) {
                                        recordTypeContributingPackagesMap
                                                .get(recordType)
                                                .add(packageName);
                                    } else {
                                        recordTypeContributingPackagesMap.put(
                                                recordType,
                                                new HashSet<>(Collections.singleton(packageName)));
                                    }
                                });
                    }
                });
        return recordTypeContributingPackagesMap;
    }

    private Map<String, AppInfoInternal> getAppInfoMap() {
        if (Objects.isNull(mAppInfoMap)) {
            populateAppInfoMap();
        }

        return mAppInfoMap;
    }

    private Map<Long, String> getIdPackageNameMap() {
        if (mIdPackageNameMap == null) {
            populateAppInfoMap();
        }

        return mIdPackageNameMap;
    }

    private AppInfoInternal getAppInfo(@NonNull String packageName, @NonNull Context context)
            throws NameNotFoundException {
        PackageManager packageManager = context.getPackageManager();
        ApplicationInfo info =
                packageManager.getApplicationInfo(
                        packageName, PackageManager.ApplicationInfoFlags.of(0));
        String appName = packageManager.getApplicationLabel(info).toString();
        Drawable icon = packageManager.getApplicationIcon(info);
        Bitmap bitmap = getBitmapFromDrawable(icon);
        return new AppInfoInternal(DEFAULT_LONG, packageName, appName, bitmap, null);
    }

    private synchronized void insertIfNotPresent(
            @NonNull String packageName, @NonNull AppInfoInternal appInfo) {
        if (getAppInfoMap().containsKey(packageName)) {
            return;
        }

        long rowId =
                TransactionManager.getInitialisedInstance()
                        .insert(
                                new UpsertTableRequest(
                                        TABLE_NAME,
                                        getContentValues(packageName, appInfo),
                                        UNIQUE_COLUMN_INFO));
        appInfo.setId(rowId);
        getAppInfoMap().put(packageName, appInfo);
        getIdPackageNameMap().put(appInfo.getId(), packageName);
    }

    private synchronized void updateIfPresent(String packageName, AppInfoInternal appInfoInternal) {
        if (!getAppInfoMap().containsKey(packageName)) {
            return;
        }

        UpsertTableRequest upsertTableRequest =
                new UpsertTableRequest(
                        TABLE_NAME,
                        getContentValues(packageName, appInfoInternal),
                        UNIQUE_COLUMN_INFO);

        TransactionManager.getInitialisedInstance().updateTable(upsertTableRequest);
        getAppInfoMap().put(packageName, appInfoInternal);
    }

    @NonNull
    private ContentValues getContentValues(String packageName, AppInfoInternal appInfo) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(PACKAGE_COLUMN_NAME, packageName);
        contentValues.put(APPLICATION_COLUMN_NAME, appInfo.getName());
        contentValues.put(APP_ICON_COLUMN_NAME, encodeBitmap(appInfo.getIcon()));
        String recordTypesUsedAsString = null;
        // Since a list of recordTypeIds cannot be saved directly in the database, record types IDs
        // are concatenated using ',' and are saved as a string.
        if (appInfo.getRecordTypesUsed() != null) {
            recordTypesUsedAsString =
                    appInfo.getRecordTypesUsed().stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(","));
        }
        contentValues.put(RECORD_TYPES_USED_COLUMN_NAME, recordTypesUsedAsString);

        return contentValues;
    }

    /**
     * This implementation should return the column names with which the table should be created.
     *
     * <p>NOTE: New columns can only be added via onUpgrade. Why? Consider what happens if a table
     * already exists on the device
     *
     * <p>PLEASE DON'T USE THIS METHOD TO ADD NEW COLUMNS
     */
    @Override
    @NonNull
    protected List<Pair<String, String>> getColumnInfo() {
        ArrayList<Pair<String, String>> columnInfo = new ArrayList<>();
        columnInfo.add(new Pair<>(RecordHelper.PRIMARY_COLUMN_NAME, PRIMARY));
        columnInfo.add(new Pair<>(PACKAGE_COLUMN_NAME, TEXT_NOT_NULL_UNIQUE));
        columnInfo.add(new Pair<>(APPLICATION_COLUMN_NAME, TEXT_NULL));
        columnInfo.add(new Pair<>(APP_ICON_COLUMN_NAME, BLOB));
        columnInfo.add(new Pair<>(RECORD_TYPES_USED_COLUMN_NAME, TEXT_NULL));

        return columnInfo;
    }

    public static synchronized AppInfoHelper getInstance() {
        if (sAppInfoHelper == null) {
            sAppInfoHelper = new AppInfoHelper();
        }

        return sAppInfoHelper;
    }

    @Nullable
    private static byte[] encodeBitmap(@Nullable Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.PNG, COMPRESS_FACTOR, stream);
            return stream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    @Nullable
    private static Bitmap decodeBitmap(@Nullable byte[] bytes) {
        return bytes != null ? BitmapFactory.decodeByteArray(bytes, 0, bytes.length) : null;
    }

    @NonNull
    private static Bitmap getBitmapFromDrawable(@NonNull Drawable drawable) {
        final Bitmap bmp =
                Bitmap.createBitmap(
                        drawable.getIntrinsicWidth(),
                        drawable.getIntrinsicHeight(),
                        Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bmp;
    }
}
