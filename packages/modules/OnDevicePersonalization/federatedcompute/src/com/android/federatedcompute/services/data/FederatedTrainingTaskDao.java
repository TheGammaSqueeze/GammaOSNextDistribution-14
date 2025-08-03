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

package com.android.federatedcompute.services.data;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE;
import static com.android.federatedcompute.services.data.FederatedTraningTaskContract.FEDERATED_TRAINING_TASKS_TABLE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.data.FederatedTraningTaskContract.FederatedTrainingTaskColumns;
import com.android.federatedcompute.services.statsd.ClientErrorLogger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;

import java.util.ArrayList;
import java.util.List;

/** DAO for accessing training task table. */
public class FederatedTrainingTaskDao {

    private static final String TAG = FederatedTrainingTaskDao.class.getSimpleName();

    private final FederatedComputeDbHelper mDbHelper;
    private static volatile FederatedTrainingTaskDao sSingletonInstance;

    private FederatedTrainingTaskDao(FederatedComputeDbHelper dbHelper) {
        this.mDbHelper = dbHelper;
    }

    /** Returns an instance of the FederatedTrainingTaskDao given a context. */
    @NonNull
    public static FederatedTrainingTaskDao getInstance(Context context) {
        if (sSingletonInstance == null) {
            synchronized (FederatedTrainingTaskDao.class) {
                if (sSingletonInstance == null) {
                    sSingletonInstance =
                            new FederatedTrainingTaskDao(
                                    FederatedComputeDbHelper.getInstance(context));
                }
            }
        }
        return sSingletonInstance;
    }

    /** It's only public to unit test. */
    @VisibleForTesting
    public static FederatedTrainingTaskDao getInstanceForTest(Context context) {
        synchronized (FederatedTrainingTaskDao.class) {
            if (sSingletonInstance == null) {
                FederatedComputeDbHelper dbHelper =
                        FederatedComputeDbHelper.getInstanceForTest(context);
                sSingletonInstance = new FederatedTrainingTaskDao(dbHelper);
            }
            return sSingletonInstance;
        }
    }

    /** Deletes a training task in FederatedTrainingTask table. */
    private void deleteFederatedTrainingTask(String selection, String[] selectionArgs) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            ClientErrorLogger.getInstance()
                    .logError(
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
            return;
        }
        db.delete(FEDERATED_TRAINING_TASKS_TABLE, selection, selectionArgs);
    }

    /** Insert a training task or update it if task already exists. */
    public boolean updateOrInsertFederatedTrainingTask(FederatedTrainingTask trainingTask) {
        try {
            SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
            if (db == null) {
                return false;
            }
            return trainingTask.addToDatabase(db);
        } catch (SQLException e) {
            LogUtil.e(
                    TAG,
                    e,
                    "Failed to persist federated training task %s",
                    trainingTask.populationName());
            return false;
        }
    }

    /** Get the list of tasks that match select conditions. */
    @Nullable
    public List<FederatedTrainingTask> getFederatedTrainingTask(
            String selection, String[] selectionArgs) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return null;
        }
        return FederatedTrainingTask.readFederatedTrainingTasksFromDatabase(
                db, selection, selectionArgs);
    }

    /** Delete a task from table based on job scheduler id. */
    public FederatedTrainingTask findAndRemoveTaskByJobId(int jobId) {
        String selection = FederatedTrainingTaskColumns.JOB_SCHEDULER_JOB_ID + " = ?";
        String[] selectionArgs = selectionArgs(jobId);
        FederatedTrainingTask task =
                Iterables.getOnlyElement(getFederatedTrainingTask(selection, selectionArgs), null);
        try {
            if (task != null) {
                deleteFederatedTrainingTask(selection, selectionArgs);
            } else {
                ClientErrorLogger.getInstance()
                        .logError(
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
            }
            return task;
        } catch (SQLException e) {
            LogUtil.e(TAG, e, "Failed to delete federated training task by job id %d", jobId);
            ClientErrorLogger.getInstance()
                    .logErrorWithExceptionInfo(
                            e,
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
            return null;
        }
    }

    /** Delete a task from table based on population name. */
    public FederatedTrainingTask findAndRemoveTaskByPopulationName(String populationName) {
        String selection = FederatedTrainingTaskColumns.POPULATION_NAME + " = ?";
        String[] selectionArgs = {populationName};
        FederatedTrainingTask task =
                Iterables.getOnlyElement(getFederatedTrainingTask(selection, selectionArgs), null);
        try {
            if (task != null) {
                deleteFederatedTrainingTask(selection, selectionArgs);
            } else {
                ClientErrorLogger.getInstance()
                        .logError(
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
            }
            return task;
        } catch (SQLException e) {
            LogUtil.e(
                    TAG,
                    e,
                    "Failed to delete federated training task by population name %s",
                    populationName);
            ClientErrorLogger.getInstance()
                    .logErrorWithExceptionInfo(
                            e,
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
            return null;
        }
    }

    /** Delete a task from table based on population name and calling package. */
    public FederatedTrainingTask findAndRemoveTaskByPopulationNameAndCallingPackage(
            String populationName, String callingPackage) {
        String selection =
                FederatedTrainingTaskColumns.POPULATION_NAME
                        + " = ? AND "
                        + FederatedTrainingTaskColumns.APP_PACKAGE_NAME
                        + " = ?";
        String[] selectionArgs = {populationName, callingPackage};
        FederatedTrainingTask task =
                Iterables.getOnlyElement(getFederatedTrainingTask(selection, selectionArgs), null);
        try {
            if (task != null) {
                deleteFederatedTrainingTask(selection, selectionArgs);
            } else {
                ClientErrorLogger.getInstance()
                        .logError(
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
            }
            return task;
        } catch (SQLException e) {
            LogUtil.e(
                    TAG,
                    e,
                    "Failed to delete federated training task by "
                            + "population name %s and calling package: %s",
                    populationName,
                    callingPackage);
            ClientErrorLogger.getInstance()
                    .logErrorWithExceptionInfo(
                            e,
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
            return null;
        }
    }

    /** Delete a task from table based on population name and owner Id (package and class name). */
    public FederatedTrainingTask findAndRemoveTaskByPopulationNameAndOwnerId(
            String populationName, String ownerId, String ownerCertDigest) {
        String selection =
                FederatedTrainingTaskColumns.POPULATION_NAME
                        + " = ? AND "
                        + FederatedTrainingTaskColumns.OWNER_ID
                        + " = ? AND "
                        + FederatedTrainingTaskColumns.OWNER_ID_CERT_DIGEST
                        + " = ?";
        String[] selectionArgs = {populationName, ownerId, ownerCertDigest};
        FederatedTrainingTask task =
                Iterables.getOnlyElement(getFederatedTrainingTask(selection, selectionArgs), null);
        try {
            if (task != null) {
                deleteFederatedTrainingTask(selection, selectionArgs);
            } else {
                ClientErrorLogger.getInstance()
                        .logError(
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
            }
            return task;
        } catch (SQLException e) {
            LogUtil.e(
                    TAG,
                    e,
                    "Failed to delete federated training task by population name %s and ATP: %s",
                    populationName,
                    ownerId);
            ClientErrorLogger.getInstance()
                    .logErrorWithExceptionInfo(
                            e,
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
            return null;
        }
    }

    /** Delete a task from table based on population name and job scheduler id. */
    public FederatedTrainingTask findAndRemoveTaskByPopulationAndJobId(
            String populationName, int jobId) {
        String selection =
                FederatedTrainingTaskColumns.POPULATION_NAME
                        + " = ? AND "
                        + FederatedTrainingTaskColumns.JOB_SCHEDULER_JOB_ID
                        + " = ?";
        String[] selectionArgs = {populationName, String.valueOf(jobId)};
        FederatedTrainingTask task =
                Iterables.getOnlyElement(getFederatedTrainingTask(selection, selectionArgs), null);
        try {
            if (task != null) {
                deleteFederatedTrainingTask(selection, selectionArgs);
            } else {
                ClientErrorLogger.getInstance()
                        .logError(
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
            }
            return task;
        } catch (SQLException e) {
            LogUtil.e(
                    TAG,
                    e,
                    "Failed to delete federated training task by population name %s and job id %d",
                    populationName,
                    jobId);
            ClientErrorLogger.getInstance()
                    .logErrorWithExceptionInfo(
                            e,
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
            return null;
        }
    }

    /** Insert a training task history record or update it if task already exists. */
    public boolean updateOrInsertTaskHistory(TaskHistory taskHistory) {
        try {
            SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(TaskHistoryContract.TaskHistoryEntry.JOB_ID, taskHistory.getJobId());
            values.put(
                    TaskHistoryContract.TaskHistoryEntry.POPULATION_NAME,
                    taskHistory.getPopulationName());
            values.put(TaskHistoryContract.TaskHistoryEntry.TASK_ID, taskHistory.getTaskId());
            values.put(
                    TaskHistoryContract.TaskHistoryEntry.CONTRIBUTION_ROUND,
                    taskHistory.getContributionRound());
            values.put(
                    TaskHistoryContract.TaskHistoryEntry.CONTRIBUTION_TIME,
                    taskHistory.getContributionTime());
            values.put(
                    TaskHistoryContract.TaskHistoryEntry.TOTAL_PARTICIPATION,
                    taskHistory.getTotalParticipation());
            return db.insertWithOnConflict(
                            TaskHistoryContract.TaskHistoryEntry.TABLE_NAME,
                            null,
                            values,
                            SQLiteDatabase.CONFLICT_REPLACE)
                    != -1;
        } catch (SQLException e) {
            LogUtil.e(
                    TAG,
                    "Failed to update or insert task history %s %s",
                    taskHistory.getPopulationName(),
                    taskHistory.getTaskId());
        }
        return false;
    }

    /** Gets the list of task history based on provided job id, population name and task id. */
    public List<TaskHistory> getTaskHistoryList(int jobId, String populationName, String taskId) {
        return getTaskHistory(jobId, populationName, taskId, false);
    }

    /** Get the latest record of task history based on job id, population name and task name. */
    public TaskHistory getLatestTaskHistory(int jobId, String populationName, String taskId) {
        List<TaskHistory> taskList = getTaskHistory(jobId, populationName, taskId, true);
        return taskList.isEmpty() ? null : taskList.get(0);
    }

    private List<TaskHistory> getTaskHistory(
            int jobId, String populationName, String taskId, boolean latest) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        String selection =
                TaskHistoryContract.TaskHistoryEntry.JOB_ID
                        + " = ? AND "
                        + TaskHistoryContract.TaskHistoryEntry.POPULATION_NAME
                        + " = ? AND "
                        + TaskHistoryContract.TaskHistoryEntry.TASK_ID
                        + " = ?";
        String[] selectionArgs = {String.valueOf(jobId), populationName, taskId};
        String orderBy = TaskHistoryContract.TaskHistoryEntry.CONTRIBUTION_TIME + " DESC";
        String[] projection = {
            TaskHistoryContract.TaskHistoryEntry.CONTRIBUTION_TIME,
            TaskHistoryContract.TaskHistoryEntry.CONTRIBUTION_ROUND,
            TaskHistoryContract.TaskHistoryEntry.TOTAL_PARTICIPATION
        };
        List<TaskHistory> taskList = new ArrayList<>();
        try (Cursor cursor =
                db.query(
                        TaskHistoryContract.TaskHistoryEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ orderBy)) {
            while (cursor.moveToNext()) {
                long contributionTime =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TaskHistoryContract.TaskHistoryEntry.CONTRIBUTION_TIME));
                long contributionRound =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TaskHistoryContract.TaskHistoryEntry.CONTRIBUTION_ROUND));
                long totalParticipation =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TaskHistoryContract.TaskHistoryEntry.TOTAL_PARTICIPATION));
                taskList.add(
                        new TaskHistory.Builder()
                                .setJobId(jobId)
                                .setTaskId(taskId)
                                .setPopulationName(populationName)
                                .setContributionRound(contributionRound)
                                .setContributionTime(contributionTime)
                                .setTotalParticipation(totalParticipation)
                                .build());
                if (latest) {
                    cursor.close();
                    return taskList;
                }
            }
            cursor.close();
            return taskList;
        } catch (SQLiteException e) {
            LogUtil.e(TAG, "Failed to read TaskHistory db", e);
        }
        return null;
    }

    /** Batch delete expired task history records. */
    public int deleteExpiredTaskHistory(long deleteTime) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            throw new SQLiteException(TAG + ": Failed to open database.");
        }
        String whereClause = TaskHistoryContract.TaskHistoryEntry.CONTRIBUTION_TIME + " < ?";
        String[] whereArgs = {String.valueOf(deleteTime)};
        int deletedRows =
                db.delete(TaskHistoryContract.TaskHistoryEntry.TABLE_NAME, whereClause, whereArgs);
        LogUtil.d(TAG, "Deleted %d expired tokens", deletedRows);
        return deletedRows;
    }

    private String[] selectionArgs(Number... args) {
        String[] values = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            values[i] = String.valueOf(args[i]);
        }
        return values;
    }
}
