package android.health.connect.aidl;

import android.content.AttributionSource;
import android.health.connect.aidl.ActivityDatesRequestParcel;
import android.health.connect.aidl.AggregateDataRequestParcel;
import android.health.connect.aidl.IAggregateRecordsResponseCallback;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.aidl.DeleteUsingFiltersRequestParcel;
import android.health.connect.aidl.IAccessLogsResponseCallback;
import android.health.connect.aidl.IChangeLogsResponseCallback;
import android.health.connect.aidl.IDataStagingFinishedCallback;
import android.health.connect.aidl.IEmptyResponseCallback;
import android.health.connect.aidl.IGetChangeLogTokenCallback;
import android.health.connect.aidl.IGetPriorityResponseCallback;
import android.health.connect.aidl.IGetHealthConnectMigrationUiStateCallback;
import android.health.connect.aidl.IGetHealthConnectDataStateCallback;
import android.health.connect.aidl.RecordsParcel;
import android.health.connect.aidl.IMigrationCallback;
import android.health.connect.migration.MigrationEntityParcel;
import android.health.connect.aidl.IApplicationInfoResponseCallback;
import android.health.connect.aidl.IEmptyResponseCallback;
import android.health.connect.aidl.IInsertRecordsResponseCallback;
import android.health.connect.aidl.RecordsParcel;
import android.health.connect.aidl.UpdatePriorityRequestParcel;
import android.health.connect.aidl.IReadRecordsResponseCallback;
import android.health.connect.aidl.IActivityDatesResponseCallback;
import android.health.connect.aidl.IRecordTypeInfoResponseCallback;
import android.health.connect.aidl.ReadRecordsRequestParcel;
import android.health.connect.exportimport.ScheduledExportSettings;
import android.health.connect.migration.MigrationEntity;
import android.health.connect.restore.BackupFileNamesSet;
import android.health.connect.restore.StageRemoteDataRequest;

import android.os.UserHandle;

import java.util.List;
import java.util.Map;

/**
 * Interface for {@link com.android.health.connect.HealthConnectManager}
 * {@hide}
 */
interface IHealthConnectService {
    void grantHealthPermission(String packageName, String permissionName, in UserHandle user);
    void revokeHealthPermission(String packageName, String permissionName, String reason, in UserHandle user);
    void revokeAllHealthPermissions(String packageName, String reason, in UserHandle user);
    List<String> getGrantedHealthPermissions(String packageName, in UserHandle user);

    /**
     * Returns a Map<String, Integer> from a permission name to permission flags.
     * @hide
     */
    Map getHealthPermissionsFlags(String packageName, in UserHandle user, in List<String> permissions);

    /**
     * @hide
     */
    void setHealthPermissionsUserFixedFlagValue(String packageName, in UserHandle user, in List<String> permissions, boolean value);

    /* @hide */
    long getHistoricalAccessStartDateInMilliseconds(String packageName, in UserHandle user);

    /**
     * Inserts {@code records} into the HealthConnect database.
     *
     * @param attributionSource attribution source for the data.
     * @param recordsParcel represents records to be inserted.
     * @param callback Callback to receive result of performing this operation.
     */
    void insertRecords(
        in AttributionSource attributionSource,
        in RecordsParcel recordsParcel,
        in IInsertRecordsResponseCallback callback);

    /**
     * Returns aggregation results based on the {@code request} into the HealthConnect database.
     *
     * @param attributionSource attribution source for the data.
     * @param request represents the request using which the aggregation is to be performed.
     * @param callback Callback to receive result of performing this operation.
     */
    void aggregateRecords(
        in AttributionSource attributionSource,
        in AggregateDataRequestParcel request,
        in IAggregateRecordsResponseCallback callback);

    /**
     * Reads from the HealthConnect database.
     *
     * @param attributionSource attribution source for the data.
     * @param request represents the request to be read.
     * @param callback Callback to receive result of performing this operation.
     */
    void readRecords(
        in AttributionSource attributionSource,
        in ReadRecordsRequestParcel request,
        in IReadRecordsResponseCallback callback);

    /**
     * Updates {@param records} in the HealthConnect database.
     *
     * @param attributionSource attribution source for the data.
     * @param recordsParcel represents records to be updated.
     * @param callback Callback to receive result of performing this operation.
     */
    void updateRecords(
            in AttributionSource attributionSource,
            in RecordsParcel recordsParcel,
            in IEmptyResponseCallback callback);

    /**
     * @param packageName calling package name
     * @param request token request
     * @return a token that can be used with {@code getChanges(token)} to fetch the upsert and
     *     delete changes corresponding to {@code request}
     */
    void getChangeLogToken(
        in AttributionSource attributionSource,
        in ChangeLogTokenRequest request,
        in IGetChangeLogTokenCallback callback);

    /**
     * @param attributionSource attribution source for the data.
     * @param token request token from {@code getChangeLogToken}
     */
    void getChangeLogs(
        in AttributionSource attributionSource,
        in ChangeLogsRequest token,
        in IChangeLogsResponseCallback callback);

    /**
     * @param attributionSource attribution source for the data.
     * @param request Delete request using the mentioned filters
     * @param callback Callback to receive result of performing this operation
     */
    void deleteUsingFilters(
        in AttributionSource attributionSource,
        in DeleteUsingFiltersRequestParcel request,
        in IEmptyResponseCallback callback);

    /**
     * @param attributionSource attribution source for the data.
     * @param request Delete request using the mentioned filters
     * @param callback Callback to receive result of performing this operation
     */
    void deleteUsingFiltersForSelf(
        in AttributionSource attributionSource,
        in DeleteUsingFiltersRequestParcel request,
        in IEmptyResponseCallback callback);

    /**
     * @param packageName Calling package's name
     * @param permissionCategory PermissionCategory corresponding to which priority is requested
     * @param callback Callback to receive result of performing this operation
     */
    void getCurrentPriority(
        String packageName,
        int permissionCategory,
        in IGetPriorityResponseCallback callback);

    /**
     * @param packageName Calling package's name
     * @param request Delete request using the mentioned filters
     * @param callback Callback to receive result of performing this operation
     */
    void updatePriority(
        String packageName,
        in UpdatePriorityRequestParcel request,
        in IEmptyResponseCallback callback);

    /** Sets record rention period for HC DB */
    void setRecordRetentionPeriodInDays(
        int days,
        in UserHandle userHandle,
        in IEmptyResponseCallback callback);

    /** Gets record rention period for HC DB */
    int getRecordRetentionPeriodInDays(in UserHandle userHandle);

    /**
     * Returns information, represented by {@code ApplicationInfoResponse}, for all the
     * packages that have contributed to the health connect DB.
     *
     * @param callback Callback to receive result of performing this operation.
     */
    void getContributorApplicationsInfo(in IApplicationInfoResponseCallback callback);

    /** Returns information for each RecordType like health permission category, record category and
     * contributing packages.
     * @param callback Callback to receive result of performing this operation.
     */
    void queryAllRecordTypesInfo(in IRecordTypeInfoResponseCallback callback);

    /**
     * @param packageName name of the package reading access logs
     * @param callback Callback to receive result of performing this operation
     */
    void queryAccessLogs(
        String packageName,
        in IAccessLogsResponseCallback callback);

    /**
     * Returns a list of unique dates for which at least one record type has at least one entry.
     *
     * @param recordTypes List of record types classes for which to get the activity dates.
     * @param callback Callback to receive the result of performing this operation.
     * {@hide}
     */
    void getActivityDates(
        in ActivityDatesRequestParcel recordTypes,
        in IActivityDatesResponseCallback callback);

    /**
     * Marks the start of the migration.
     *
     * @param packageName calling package name
     * @param callback Callback to receive a result or an error encountered while performing this
     * operation.
     */
    void startMigration(String packageName, in IMigrationCallback callback);

    /**
     * Marks the end of the migration.
     *
     * @param packageName calling package name
     * @param callback Callback to receive a result or an error encountered while performing this
     * operation.
     */
    void finishMigration(String packageName, in IMigrationCallback callback);

    /**
     * Writes given entities to the module database.
     *
     * @param packageName calling package name
     * @param entities List of {@link MigrationEntity} to migrate.
     * @param callback Callback to receive a result or an error encountered while performing this
     * operation.
     */
    void writeMigrationData(
        String packageName,
        in MigrationEntityParcel parcel,
        in IMigrationCallback callback);

    /**
     * @param packageName calling package name
     * @param callback Callback to receive a result or an error encountered while performing this
     * operation.
     */
    void insertMinDataMigrationSdkExtensionVersion(
        String packageName,
        int requiredSdkExtension,
        in IMigrationCallback callback);

    /**
     * Stages all HealthConnect remote data and returns any errors in a callback. Errors encountered
     * for all the files are shared in the provided callback.
     *
     * @param pfdsByFileName The map of file names and their {@link ParcelFileDescriptor}s.
     * @param executor       The {@link Executor} on which to invoke the callback.
     * @param callback       The callback which will receive the outcome of this call.
     * @throws NullPointerException if null is passed for any of the required {@link NonNull}
     *                              parameters.
     * @hide
     */
    void stageAllHealthConnectRemoteData(in StageRemoteDataRequest stageRemoteDataRequest,
            in UserHandle userHandle, in IDataStagingFinishedCallback callback);

    /**
     * Copies all HealthConnect backup data in the passed FDs.
     *
     * <p>The shared data should later be sent for cloud backup or to another device for backup.
     *
     * <p>We are responsible for closing the original file descriptors. The caller must not close
     * the FD before that.
     *
     * @param pfdsByFileName The map of file names and their {@link ParcelFileDescriptor}s.
     * @hide
     */
    void getAllDataForBackup(in StageRemoteDataRequest stageRemoteDataRequest, in UserHandle userHandle);

    /**
     * Shares the names of all HealthConnect backup files
     *
     * @hide
     */
    BackupFileNamesSet getAllBackupFileNames(in boolean forDeviceToDevice);

    /**
     * Deletes all previously staged HealthConnect data from the disk.
     * For testing purposes only.
     *
     * @hide
     */
    void deleteAllStagedRemoteData(in UserHandle userHandle);

    /**
     * Updates the download state of the Health Connect data.
     *
     * @param downloadState The download state which needs to be purely one of:
     *                      {@link HealthConnectManager#CLOUD_DOWNLOAD_STARTED}, {@link
     *                      HealthConnectManager#CLOUD_DOWNLOAD_RETRY}, {@link
     *                      HealthConnectManager#CLOUD_DOWNLOAD_FAILED}, {@link
     *                      HealthConnectManager#CLOUD_DOWNLOAD_COMPLETE}
     * @hide
     */
     void updateDataDownloadState(int downloadState);

    /**
     * Asynchronously returns the current state of the Health Connect data as it goes through the Data-Restore and/or the Data-Migration process.
     *
     * <p>See also {@link HealthConnectDataState} object describing the HealthConnect state.
     *
     * @param callback The callback which will receive the current {@link HealthConnectDataState}.
     *
     * @hide
     */
    void getHealthConnectDataState(in IGetHealthConnectDataStateCallback callback);

    /**
     * Asynchronously returns the current UI state of Health Connect as it goes through the Data-Migration process.
     *
     * <p>See also {@link HealthConnectMigrationUiState} object describing the HealthConnect UI state.
     *
     * @param callback The callback which will receive the current {@link HealthConnectMigrationUiState}.
     *
     * @hide
     */
    void getHealthConnectMigrationUiState(in IGetHealthConnectMigrationUiStateCallback callback);

    /**
    * Configures the settings for the scheduled export of Health Connect data.
    *
    * @param settings Settings to use for the scheduled export. Use null to clear the settings.
    *
    * @hide
    */
    void configureScheduledExport(in @nullable ScheduledExportSettings settings, in UserHandle userHandle);

    /**
    * Gets the period in days between scheduled exports of Health Connect data.
    *
    * @hide
    */
    int getScheduledExportPeriodInDays(in UserHandle userHandle);
}
