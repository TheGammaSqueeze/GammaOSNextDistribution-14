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

import static android.health.connect.HealthPermissions.getDataCategoriesWithWritePermissionsForPackage;
import static android.health.connect.HealthPermissions.getPackageHasWriteHealthPermissionsForCategory;
import static android.health.connect.internal.datatypes.utils.RecordTypeRecordCategoryMapper.getRecordCategoryForRecordType;

import static com.android.server.healthconnect.storage.request.UpsertTableRequest.TYPE_STRING;
import static com.android.server.healthconnect.storage.utils.StorageUtils.DELIMITER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_UNIQUE;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.health.connect.HealthDataCategory;
import android.health.connect.HealthPermissions;
import android.os.UserHandle;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.permission.HealthConnectPermissionHelper;
import com.android.server.healthconnect.permission.PackageInfoUtils;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.StorageUtils;

import java.util.ArrayList;
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
 * Helper class to get priority of the apps for each {@link HealthDataCategory}
 *
 * @hide
 */
public class HealthDataCategoryPriorityHelper extends DatabaseHelper {
    private static final String TABLE_NAME = "health_data_category_priority_table";
    private static final String HEALTH_DATA_CATEGORY_COLUMN_NAME = "health_data_category";
    public static final List<Pair<String, Integer>> UNIQUE_COLUMN_INFO =
            Collections.singletonList(new Pair<>(HEALTH_DATA_CATEGORY_COLUMN_NAME, TYPE_STRING));
    private static final String APP_ID_PRIORITY_ORDER_COLUMN_NAME = "app_id_priority_order";
    private static final String TAG = "HealthConnectPrioHelper";
    private static final String DEFAULT_APP_RESOURCE_NAME =
            "android:string/config_defaultHealthConnectApp";

    public static final String INACTIVE_APPS_ADDED = "inactive_apps_added";

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    private static volatile HealthDataCategoryPriorityHelper sHealthDataCategoryPriorityHelper;

    /**
     * map of {@link HealthDataCategory} to list of app ids from {@link AppInfoHelper}, in the order
     * of their priority
     */
    private volatile ConcurrentHashMap<Integer, List<Long>> mHealthDataCategoryToAppIdPriorityMap;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    private HealthDataCategoryPriorityHelper() {}

    /**
     * Returns a requests representing the tables that should be created corresponding to this
     * helper
     */
    @NonNull
    public final CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(TABLE_NAME, getColumnInfo());
    }

    /**
     * Appends a packageName to the priority list for this category when an app gets write
     * permissions or during the one-time operation to add inactive apps.
     *
     * <p>Inactive apps are added at the bottom of the priority list even if they are the default
     * app.
     */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public synchronized void appendToPriorityList(
            @NonNull String packageName,
            @HealthDataCategory.Type int dataCategory,
            Context context,
            boolean isInactiveApp) {
        List<Long> newPriorityOrder;
        getHealthDataCategoryToAppIdPriorityMap().putIfAbsent(dataCategory, new ArrayList<>());
        long appInfoId = AppInfoHelper.getInstance().getOrInsertAppInfoId(packageName, context);
        if (getHealthDataCategoryToAppIdPriorityMap().get(dataCategory).contains(appInfoId)) {
            return;
        }
        newPriorityOrder =
                new ArrayList<>(getHealthDataCategoryToAppIdPriorityMap().get(dataCategory));

        if (isDefaultApp(packageName, context) && !isInactiveApp) {
            newPriorityOrder.add(0, appInfoId);
        } else {
            newPriorityOrder.add(appInfoId);
        }
        safelyUpdateDBAndUpdateCache(
                new UpsertTableRequest(
                        TABLE_NAME,
                        getContentValuesFor(dataCategory, newPriorityOrder),
                        UNIQUE_COLUMN_INFO),
                dataCategory,
                newPriorityOrder);
    }

    @VisibleForTesting
    boolean isDefaultApp(@NonNull String packageName, @NonNull Context context) {
        String defaultApp =
                context.getResources()
                        .getString(
                                Resources.getSystem()
                                        .getIdentifier(DEFAULT_APP_RESOURCE_NAME, null, null));

        return Objects.equals(packageName, defaultApp);
    }

    /**
     * Removes a packageName from the priority list of a particular category if the package name
     * does not have any granted write permissions. In the new aggregation source control, the
     * package name is not removed if it has data in this category.
     */
    public synchronized void maybeRemoveAppFromPriorityList(
            @NonNull String packageName,
            @HealthDataCategory.Type int dataCategory,
            HealthConnectPermissionHelper permissionHelper,
            UserHandle userHandle) {

        final List<String> grantedPermissions =
                permissionHelper.getGrantedHealthPermissions(packageName, userHandle);
        for (String permission : HealthPermissions.getWriteHealthPermissionsFor(dataCategory)) {
            if (grantedPermissions.contains(permission)) {
                return;
            }
        }

        maybeRemoveAppFromPriorityListInternal(dataCategory, packageName);
    }

    /**
     * Removes apps from the priority list if they no longer hold write permissions to the category
     * and have no data for that category.
     *
     * <p>If the new aggregation source control flag is off, apps that don't have write permissions
     * are removed regardless of whether they hold data in that category.
     */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public synchronized void updateHealthDataPriority(
            @NonNull String[] packageNames, @NonNull UserHandle user, @NonNull Context context) {
        Objects.requireNonNull(packageNames);
        Objects.requireNonNull(user);
        Objects.requireNonNull(context);
        PackageInfoUtils packageInfoUtils = PackageInfoUtils.getInstance();
        for (String packageName : packageNames) {
            PackageInfo packageInfo =
                    packageInfoUtils.getPackageInfoWithPermissionsAsUser(
                            packageName, user, context);

            Set<Integer> dataCategoriesWithWritePermission =
                    getDataCategoriesWithWritePermissionsForPackage(packageInfo, context);

            for (int category : getHealthDataCategoryToAppIdPriorityMap().keySet()) {
                if (!dataCategoriesWithWritePermission.contains(category)) {
                    maybeRemoveAppFromPriorityListInternal(category, packageInfo.packageName);
                }
            }
        }
    }

    /**
     * Removes app from priorityList for all HealthData Categories if the package is uninstalled or
     * if it has no health permissions. In the new aggregation source behaviour, the package name is
     * not removed if it still has health data in a category.
     */
    public synchronized void maybeRemoveAppWithoutWritePermissionsFromPriorityList(
            @NonNull String packageName) {
        Objects.requireNonNull(packageName);
        for (Integer dataCategory : getHealthDataCategoryToAppIdPriorityMap().keySet()) {
            maybeRemoveAppFromPriorityListInternal(dataCategory, packageName);
        }
    }

    /** Returns list of package names based on priority for the input {@link HealthDataCategory} */
    @NonNull
    public List<String> getPriorityOrder(
            @HealthDataCategory.Type int type, @NonNull Context context) {
        boolean newAggregationSourceControl =
                HealthConnectDeviceConfigManager.getInitialisedInstance()
                        .isAggregationSourceControlsEnabled();
        if (newAggregationSourceControl) {
            reSyncHealthDataPriorityTable(context);
        }
        return AppInfoHelper.getInstance().getPackageNames(getAppIdPriorityOrder(type));
    }

    /** Returns list of App ids based on priority for the input {@link HealthDataCategory} */
    @NonNull
    public List<Long> getAppIdPriorityOrder(@HealthDataCategory.Type int type) {
        List<Long> packageIds = getHealthDataCategoryToAppIdPriorityMap().get(type);
        if (packageIds == null) {
            return Collections.emptyList();
        }

        return packageIds;
    }

    /**
     * Sets a new priority order for the given category, and allows adding and removing packages
     * from the priority list.
     *
     * <p>In the old behaviour it is not allowed to add or remove packages so the new priority order
     * needs to be sanitised before applying the operation.
     */
    public void setPriorityOrder(int dataCategory, @NonNull List<String> packagePriorityOrder) {
        boolean newAggregationSourceControl =
                HealthConnectDeviceConfigManager.getInitialisedInstance()
                        .isAggregationSourceControlsEnabled();

        List<Long> newPriorityOrder =
                AppInfoHelper.getInstance().getAppInfoIds(packagePriorityOrder);

        if (!newAggregationSourceControl) {
            newPriorityOrder = sanitizePriorityOder(dataCategory, newPriorityOrder);
        }

        safelyUpdateDBAndUpdateCache(
                new UpsertTableRequest(
                        TABLE_NAME,
                        getContentValuesFor(dataCategory, newPriorityOrder),
                        UNIQUE_COLUMN_INFO),
                dataCategory,
                newPriorityOrder);
    }

    /**
     * Sanitizes the new priority order by ensuring it contains the same elements as the old
     * priority order, for the old behaviour of aggregation source control.
     */
    private List<Long> sanitizePriorityOder(int dataCategory, List<Long> newPriorityOrder) {

        List<Long> currentPriorityOrder =
                getHealthDataCategoryToAppIdPriorityMap()
                        .getOrDefault(dataCategory, Collections.emptyList());

        // Remove appId from the priority order if it is not part of the current priority order,
        // this is because in the time app tried to update the order an app permission might
        // have been removed, and we only store priority order of apps with permission.
        newPriorityOrder.removeIf(priorityOrder -> !currentPriorityOrder.contains(priorityOrder));

        // Make sure we don't remove any new entries. So append old priority in new priority and
        // remove duplicates
        newPriorityOrder.addAll(currentPriorityOrder);
        newPriorityOrder = newPriorityOrder.stream().distinct().collect(Collectors.toList());

        return newPriorityOrder;
    }

    @Override
    protected synchronized void clearData(@NonNull TransactionManager transactionManager) {
        clearCache();
        super.clearData(transactionManager);
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    public synchronized void clearCache() {
        mHealthDataCategoryToAppIdPriorityMap = null;
    }

    @Override
    protected String getMainTableName() {
        return TABLE_NAME;
    }

    private Map<Integer, List<Long>> getHealthDataCategoryToAppIdPriorityMap() {
        if (mHealthDataCategoryToAppIdPriorityMap == null) {
            populateDataCategoryToAppIdPriorityMap();
        }

        return mHealthDataCategoryToAppIdPriorityMap;
    }

    /** Returns an immutable map of data categories along with their priority order. */
    public Map<Integer, List<Long>> getHealthDataCategoryToAppIdPriorityMapImmutable() {
        return Collections.unmodifiableMap(getHealthDataCategoryToAppIdPriorityMap());
    }

    private synchronized void populateDataCategoryToAppIdPriorityMap() {
        if (mHealthDataCategoryToAppIdPriorityMap != null) {
            return;
        }

        ConcurrentHashMap<Integer, List<Long>> healthDataCategoryToAppIdPriorityMap =
                new ConcurrentHashMap<>();
        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        try (Cursor cursor = transactionManager.read(new ReadTableRequest(TABLE_NAME))) {
            while (cursor.moveToNext()) {
                int dataCategory =
                        cursor.getInt(cursor.getColumnIndex(HEALTH_DATA_CATEGORY_COLUMN_NAME));
                List<Long> appIdsInOrder =
                        StorageUtils.getCursorLongList(
                                cursor, APP_ID_PRIORITY_ORDER_COLUMN_NAME, DELIMITER);

                healthDataCategoryToAppIdPriorityMap.put(dataCategory, appIdsInOrder);
            }
        }

        mHealthDataCategoryToAppIdPriorityMap = healthDataCategoryToAppIdPriorityMap;
    }

    private synchronized void safelyUpdateDBAndUpdateCache(
            UpsertTableRequest request,
            @HealthDataCategory.Type int dataCategory,
            List<Long> newList) {
        try {
            TransactionManager.getInitialisedInstance().insertOrReplace(request);
            getHealthDataCategoryToAppIdPriorityMap().put(dataCategory, newList);
        } catch (Exception e) {
            Slog.e(TAG, "Priority update failed", e);
            throw e;
        }
    }

    private synchronized void safelyUpdateDBAndUpdateCache(
            DeleteTableRequest request, @HealthDataCategory.Type int dataCategory) {
        try {
            TransactionManager.getInitialisedInstance().delete(request);
            getHealthDataCategoryToAppIdPriorityMap().remove(dataCategory);
        } catch (Exception e) {
            Slog.e(TAG, "Delete from priority DB failed: ", e);
            throw e;
        }
    }

    private ContentValues getContentValuesFor(
            @HealthDataCategory.Type int dataCategory, List<Long> priorityList) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(HEALTH_DATA_CATEGORY_COLUMN_NAME, dataCategory);
        contentValues.put(
                APP_ID_PRIORITY_ORDER_COLUMN_NAME, StorageUtils.flattenLongList(priorityList));

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
    @NonNull
    protected List<Pair<String, String>> getColumnInfo() {
        ArrayList<Pair<String, String>> columnInfo = new ArrayList<>();
        columnInfo.add(new Pair<>(RecordHelper.PRIMARY_COLUMN_NAME, PRIMARY));
        columnInfo.add(new Pair<>(HEALTH_DATA_CATEGORY_COLUMN_NAME, INTEGER_UNIQUE));
        columnInfo.add(new Pair<>(APP_ID_PRIORITY_ORDER_COLUMN_NAME, TEXT_NOT_NULL));

        return columnInfo;
    }

    @NonNull
    public static synchronized HealthDataCategoryPriorityHelper getInstance() {
        if (sHealthDataCategoryPriorityHelper == null) {
            sHealthDataCategoryPriorityHelper = new HealthDataCategoryPriorityHelper();
        }

        return sHealthDataCategoryPriorityHelper;
    }

    /** Syncs priority table with the permissions and data. */
    public synchronized void reSyncHealthDataPriorityTable(@NonNull Context context) {
        Objects.requireNonNull(context);
        boolean newAggregationSourceControl =
                HealthConnectDeviceConfigManager.getInitialisedInstance()
                        .isAggregationSourceControlsEnabled();
        // Candidates to be added to the priority list
        Map<Integer, List<Long>> dataCategoryToAppIdMapHavingPermission =
                getHealthDataCategoryToAppIdPriorityMap().entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey, e -> new ArrayList<>(e.getValue())));
        // Candidates to be removed from the priority list
        Map<Integer, Set<Long>> dataCategoryToAppIdMapWithoutPermission =
                getHealthDataCategoryToAppIdPriorityMap().entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey, e -> new HashSet<>(e.getValue())));

        List<PackageInfo> validHealthApps = getValidHealthApps(context);
        AppInfoHelper appInfoHelper = AppInfoHelper.getInstance();
        for (PackageInfo packageInfo : validHealthApps) {
            Set<Integer> dataCategoriesWithWritePermissionsForThisPackage =
                    getDataCategoriesWithWritePermissionsForPackage(packageInfo, context);
            long appInfoId = appInfoHelper.getOrInsertAppInfoId(packageInfo.packageName, context);

            for (int dataCategory : dataCategoriesWithWritePermissionsForThisPackage) {
                List<Long> appIdsHavingPermission =
                        dataCategoryToAppIdMapHavingPermission.getOrDefault(
                                dataCategory, new ArrayList<>());
                if (!appIdsHavingPermission.contains(appInfoId)
                        && appIdsHavingPermission.add(appInfoId)) {
                    dataCategoryToAppIdMapHavingPermission.put(
                            dataCategory, appIdsHavingPermission);
                }

                Set<Long> appIdsWithoutPermission =
                        dataCategoryToAppIdMapWithoutPermission.getOrDefault(
                                dataCategory, new HashSet<>());
                if (appIdsWithoutPermission.remove(appInfoId)) {
                    dataCategoryToAppIdMapWithoutPermission.put(
                            dataCategory, appIdsWithoutPermission);
                }
            }
        }

        // The new behaviour does not automatically add to the priority list if there is
        // a write permission for a package name
        if (!newAggregationSourceControl) {
            updateTableWithNewPriorityList(dataCategoryToAppIdMapHavingPermission);
        }
        maybeRemoveAppsFromPriorityList(dataCategoryToAppIdMapWithoutPermission);
    }

    /** Returns a list of PackageInfos holding health permissions for this user. */
    private List<PackageInfo> getValidHealthApps(@NonNull Context context) {
        UserHandle user = TransactionManager.getInitialisedInstance().getCurrentUserHandle();
        Context currentUserContext = context.createContextAsUser(user, /*flags*/ 0);
        return PackageInfoUtils.getInstance()
                .getPackagesHoldingHealthPermissions(user, currentUserContext);
    }

    /**
     * Removes a packageName from the priority list of a category. For the new aggregation source
     * control, the package name is not removed if it has data in that category.
     */
    private synchronized void maybeRemoveAppFromPriorityListInternal(
            @HealthDataCategory.Type int dataCategory, @NonNull String packageName) {
        boolean newAggregationSourceControl =
                HealthConnectDeviceConfigManager.getInitialisedInstance()
                        .isAggregationSourceControlsEnabled();
        boolean dataExistsForPackageName = appHasDataInCategory(packageName, dataCategory);
        if (newAggregationSourceControl && dataExistsForPackageName) {
            // Do not remove if data exists for packageName in the new aggregation
            return;
        }

        List<Long> newPriorityList =
                new ArrayList<>(
                        getHealthDataCategoryToAppIdPriorityMap()
                                .getOrDefault(dataCategory, Collections.emptyList()));
        if (newPriorityList.isEmpty()) {
            return;
        }

        newPriorityList.remove(AppInfoHelper.getInstance().getAppInfoId(packageName));
        if (newPriorityList.isEmpty()) {
            safelyUpdateDBAndUpdateCache(
                    new DeleteTableRequest(TABLE_NAME)
                            .setId(HEALTH_DATA_CATEGORY_COLUMN_NAME, String.valueOf(dataCategory)),
                    dataCategory);
            return;
        }

        safelyUpdateDBAndUpdateCache(
                new UpsertTableRequest(
                        TABLE_NAME,
                        getContentValuesFor(dataCategory, newPriorityList),
                        UNIQUE_COLUMN_INFO),
                dataCategory,
                newPriorityList);
    }

    /**
     * Removes apps without permissions for these categories from the priority list. In the new
     * aggregation source control, the packages are not removed if they still have data in these
     * categories.
     */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    private synchronized void maybeRemoveAppsFromPriorityList(
            Map<Integer, Set<Long>> dataCategoryToAppIdsWithoutPermissions) {
        for (int dataCategory : dataCategoryToAppIdsWithoutPermissions.keySet()) {
            for (Long appInfoId : dataCategoryToAppIdsWithoutPermissions.get(dataCategory)) {
                maybeRemoveAppFromPriorityListInternal(
                        dataCategory, AppInfoHelper.getInstance().getPackageName(appInfoId));
            }
        }
    }

    private synchronized void updateTableWithNewPriorityList(
            Map<Integer, List<Long>> healthDataCategoryToAppIdPriorityMap) {
        for (int dataCategory : healthDataCategoryToAppIdPriorityMap.keySet()) {
            List<Long> appInfoIdList =
                    List.copyOf(healthDataCategoryToAppIdPriorityMap.get(dataCategory));
            if (!appInfoIdList.equals(
                    getHealthDataCategoryToAppIdPriorityMap().get(dataCategory))) {
                safelyUpdateDBAndUpdateCache(
                        new UpsertTableRequest(
                                TABLE_NAME,
                                getContentValuesFor(dataCategory, appInfoIdList),
                                UNIQUE_COLUMN_INFO),
                        dataCategory,
                        appInfoIdList);
            }
        }
    }

    /**
     * A one-time operation which adds inactive apps (without permissions but with data) to the
     * priority list if the new aggregation source controls are available.
     *
     * <p>The inactive apps are added in ascending order of their package names.
     */
    public void maybeAddInactiveAppsToPriorityList(Context context) {
        if (!shouldAddInactiveApps()) {
            return;
        }

        Map<Integer, Set<String>> inactiveApps = getAllInactiveApps(context);

        for (Map.Entry<Integer, Set<String>> entry : inactiveApps.entrySet()) {
            int category = entry.getKey();
            entry.getValue().stream()
                    .sorted()
                    .forEach(
                            packageName ->
                                    appendToPriorityList(
                                            packageName,
                                            category,
                                            context,
                                            /* isInactiveApp= */ true));
        }

        PreferenceHelper.getInstance()
                .insertOrReplacePreference(INACTIVE_APPS_ADDED, String.valueOf(true));
    }

    private boolean shouldAddInactiveApps() {
        boolean newAggregationSourceControl =
                HealthConnectDeviceConfigManager.getInitialisedInstance()
                        .isAggregationSourceControlsEnabled();

        if (!newAggregationSourceControl) {
            return false;
        }

        String haveInactiveAppsBeenAddedString =
                PreferenceHelper.getInstance().getPreference(INACTIVE_APPS_ADDED);

        // No-op if this operation has already been completed
        if (haveInactiveAppsBeenAddedString != null
                && Boolean.parseBoolean(haveInactiveAppsBeenAddedString)) {
            return false;
        }

        return true;
    }

    @VisibleForTesting
    boolean appHasDataInCategory(String packageName, int category) {
        return getDataCategoriesWithDataForPackage(packageName).contains(category);
    }

    @VisibleForTesting
    Set<Integer> getDataCategoriesWithDataForPackage(String packageName) {
        Map<Integer, Set<String>> recordTypeToContributingPackages =
                AppInfoHelper.getInstance().getRecordTypesToContributingPackagesMap();
        Set<Integer> dataCategoriesWithData = new HashSet<>();

        for (Map.Entry<Integer, Set<String>> entry : recordTypeToContributingPackages.entrySet()) {
            Integer recordType = entry.getKey();
            Set<String> contributingPackages = entry.getValue();
            int recordCategory = getRecordCategoryForRecordType(recordType);
            boolean isPackageNameContributor = contributingPackages.contains(packageName);
            if (isPackageNameContributor) {
                dataCategoriesWithData.add(recordCategory);
            }
        }
        return dataCategoriesWithData;
    }

    /**
     * Returns a set of contributing apps for each dataCategory. If a dataCategory does not have any
     * data it will not be present in the map.
     */
    @VisibleForTesting
    Map<Integer, Set<String>> getAllContributorApps() {
        Map<Integer, Set<String>> recordTypeToContributingPackages =
                AppInfoHelper.getInstance().getRecordTypesToContributingPackagesMap();

        Map<Integer, Set<String>> allContributorApps = new HashMap<>();

        for (Map.Entry<Integer, Set<String>> entry : recordTypeToContributingPackages.entrySet()) {
            int recordCategory = getRecordCategoryForRecordType(entry.getKey());
            Set<String> contributingPackages = entry.getValue();

            Set<String> currentPackages =
                    allContributorApps.getOrDefault(recordCategory, new HashSet<>());
            currentPackages.addAll(contributingPackages);
            allContributorApps.put(recordCategory, currentPackages);
        }

        return allContributorApps;
    }

    /**
     * Returns a map of dataCategory to sets of packageNames that are inactive.
     *
     * <p>An inactive app is one that has data for the dataCategory but no write permissions.
     */
    @VisibleForTesting
    Map<Integer, Set<String>> getAllInactiveApps(Context context) {
        Map<Integer, Set<String>> allContributorApps = getAllContributorApps();
        Map<Integer, Set<String>> inactiveApps = new HashMap<>();

        for (Map.Entry<Integer, Set<String>> entry : allContributorApps.entrySet()) {
            int category = entry.getKey();
            Set<String> contributorApps = entry.getValue();

            for (String app : contributorApps) {
                if (!appHasWriteHealthPermissionsForCategory(app, category, context)) {
                    Set<String> currentPackages =
                            inactiveApps.getOrDefault(category, new HashSet<>());
                    if (currentPackages.add(app)) {
                        inactiveApps.put(category, currentPackages);
                    }
                }
            }
        }

        return inactiveApps;
    }

    /**
     * Returns true if this packageName has at least one granted WRITE permission for this
     * dataCategory.
     */
    @VisibleForTesting
    boolean appHasWriteHealthPermissionsForCategory(
            @NonNull String packageName,
            @HealthDataCategory.Type int dataCategory,
            @NonNull Context context) {

        List<PackageInfo> validHealthApps = getValidHealthApps(context);

        for (PackageInfo validHealthApp : validHealthApps) {
            if (Objects.equals(validHealthApp.packageName, packageName)) {
                if (getPackageHasWriteHealthPermissionsForCategory(
                        validHealthApp, dataCategory, context)) {
                    return true;
                }
            }
        }

        return false;
    }
}
