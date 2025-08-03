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

import static com.android.federatedcompute.services.data.FederatedTraningTaskContract.FEDERATED_TRAINING_TASKS_TABLE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.federatedcompute.services.data.FederatedTraningTaskContract.FederatedTrainingTaskColumns;
import com.android.federatedcompute.services.data.fbs.TrainingConstraints;
import com.android.federatedcompute.services.data.fbs.TrainingIntervalOptions;

import com.google.auto.value.AutoValue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Contains the details of a training task. */
@AutoValue
public abstract class FederatedTrainingTask {
    private static final String TAG = FederatedTrainingTask.class.getSimpleName();

    /**
     * @return client app package name
     */
    public abstract String appPackageName();

    /**
     * @return the ID to use for the JobScheduler job that will run the training for this session.
     */
    public abstract int jobId();

    /**
     * @return owner identifier package and class name
     */
    public abstract String ownerId();

    /**
     * @return owner identifier cert digest
     */
    public abstract String ownerIdCertDigest();

    /**
     * @return the population name to uniquely identify the training job by.
     */
    public abstract String populationName();

    /**
     * @return the remote federated compute server address that federated client need contact when
     *     job starts.
     */
    public abstract String serverAddress();

    /**
     * @return the byte array of training interval including scheduling mode and minimum latency.
     *     The byte array is constructed from TrainingConstraints flatbuffer.
     */
    @Nullable
    @SuppressWarnings("mutable")
    public abstract byte[] intervalOptions();

    /**
     * @return the training interval including scheduling mode and minimum latency.
     */
    @Nullable
    public final TrainingIntervalOptions getTrainingIntervalOptions() {
        if (intervalOptions() == null) {
            return null;
        }
        return TrainingIntervalOptions.getRootAsTrainingIntervalOptions(
                ByteBuffer.wrap(intervalOptions()));
    }

    /**
     * @return the context data that clients pass when schedule the job.
     */
    @Nullable
    @SuppressWarnings("mutable")
    public abstract byte[] contextData();

    /**
     * @return the time the task was originally created.
     */
    public abstract Long creationTime();

    /**
     * @return the time the task was last scheduled.
     */
    public abstract Long lastScheduledTime();

    /**
     * @return the start time of the task's last run.
     */
    @Nullable
    public abstract Long lastRunStartTime();

    @NonNull
    public long getLastRunStartTime() {
        return lastRunStartTime() == null ? 0 : lastRunStartTime();
    }

    /**
     * @return the end time of the task's last run.
     */
    @Nullable
    public abstract Long lastRunEndTime();

    @NonNull
    public long getLastRunEndTime() {
        return lastRunEndTime() == null ? 0 : lastRunEndTime();
    }

    /**
     * @return the earliest time to run the task by.
     */
    public abstract Long earliestNextRunTime();

    /**
     * @return the byte array of training constraints that should apply to this task. The byte array
     *     is constructed from TrainingConstraints flatbuffer.
     */
    @SuppressWarnings("mutable")
    public abstract byte[] constraints();

    /**
     * @return the training constraints that should apply to this task.
     */
    public final TrainingConstraints getTrainingConstraints() {
        return TrainingConstraints.getRootAsTrainingConstraints(ByteBuffer.wrap(constraints()));
    }

    /**
     * @return the reason to schedule the task.
     */
    public abstract int schedulingReason();

    /**
     * @return the number of rescheduling happened for this task.
     */
    public abstract int rescheduleCount();

    /** Builder for {@link FederatedTrainingTask} */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set client application package name. */
        public abstract Builder appPackageName(String appPackageName);

        /** Set job scheduler Id. */
        public abstract Builder jobId(int jobId);

        /** Set owner ID which consists of a package name and class name. */
        public abstract Builder ownerId(String ownerId);

        /** Set owner identifier cert digest. */
        public abstract Builder ownerIdCertDigest(String ownerIdCertDigest);

        /** Set population name which uniquely identify the job. */
        public abstract Builder populationName(String populationName);

        /** Set remote federated compute server address. */
        public abstract Builder serverAddress(String serverAddress);

        /** Set the training interval including scheduling mode and minimum latency. */
        @SuppressWarnings("mutable")
        public abstract Builder intervalOptions(@Nullable byte[] intervalOptions);

        /** Set the context data that clients pass when schedule job. */
        @SuppressWarnings("mutable")
        public abstract Builder contextData(@Nullable byte[] contextData);

        /** Set the time the task was originally created. */
        public abstract Builder creationTime(Long creationTime);

        /** Set the time the task was last scheduled. */
        public abstract Builder lastScheduledTime(Long lastScheduledTime);

        /** Set the start time of the task's last run. */
        public abstract Builder lastRunStartTime(@Nullable Long lastRunStartTime);

        /** Set the end time of the task's last run. */
        public abstract Builder lastRunEndTime(@Nullable Long lastRunEndTime);

        /** Set the earliest time to run the task by. */
        public abstract Builder earliestNextRunTime(Long earliestNextRunTime);

        /** Set the training constraints that should apply to this task. */
        @SuppressWarnings("mutable")
        public abstract Builder constraints(byte[] constraints);

        /** Set the reason to schedule the task. */
        public abstract Builder schedulingReason(int schedulingReason);

        /** Set the count of reschedules. */
        public abstract Builder rescheduleCount(int rescheduleCount);

        /** Build a federated training task instance. */
        @NonNull
        public abstract FederatedTrainingTask build();
    }

    /**
     * @return a builder of federated training task.
     */
    public abstract Builder toBuilder();

    /**
     * @return a generic builder.
     */
    @NonNull
    public static Builder builder() {
        return new AutoValue_FederatedTrainingTask.Builder().rescheduleCount(0);
    }

    boolean addToDatabase(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(FederatedTrainingTaskColumns.APP_PACKAGE_NAME, appPackageName());
        values.put(FederatedTrainingTaskColumns.JOB_SCHEDULER_JOB_ID, jobId());
        values.put(FederatedTrainingTaskColumns.OWNER_ID, ownerId());
        values.put(FederatedTrainingTaskColumns.OWNER_ID_CERT_DIGEST, ownerIdCertDigest());

        values.put(FederatedTrainingTaskColumns.POPULATION_NAME, populationName());
        values.put(FederatedTrainingTaskColumns.SERVER_ADDRESS, serverAddress());
        if (intervalOptions() != null) {
            values.put(FederatedTrainingTaskColumns.INTERVAL_OPTIONS, intervalOptions());
        }

        if (contextData() != null) {
            values.put(FederatedTrainingTaskColumns.CONTEXT_DATA, contextData());
        }

        values.put(FederatedTrainingTaskColumns.CREATION_TIME, creationTime());
        values.put(FederatedTrainingTaskColumns.LAST_SCHEDULED_TIME, lastScheduledTime());
        if (lastRunStartTime() != null) {
            values.put(FederatedTrainingTaskColumns.LAST_RUN_START_TIME, lastRunStartTime());
        }
        if (lastRunEndTime() != null) {
            values.put(FederatedTrainingTaskColumns.LAST_RUN_END_TIME, lastRunEndTime());
        }
        values.put(FederatedTrainingTaskColumns.EARLIEST_NEXT_RUN_TIME, earliestNextRunTime());
        values.put(FederatedTrainingTaskColumns.CONSTRAINTS, constraints());
        values.put(FederatedTrainingTaskColumns.SCHEDULING_REASON, schedulingReason());
        values.put(FederatedTrainingTaskColumns.RESCHEDULE_COUNT, rescheduleCount());
        long jobId =
                db.insertWithOnConflict(
                        FEDERATED_TRAINING_TASKS_TABLE,
                        "",
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE);
        return jobId != -1;
    }

    static List<FederatedTrainingTask> readFederatedTrainingTasksFromDatabase(
            SQLiteDatabase db, String selection, String[] selectionArgs) {
        List<FederatedTrainingTask> taskList = new ArrayList<>();
        String[] selectColumns = {
            FederatedTrainingTaskColumns.APP_PACKAGE_NAME,
            FederatedTrainingTaskColumns.JOB_SCHEDULER_JOB_ID,
            FederatedTrainingTaskColumns.OWNER_ID,
            FederatedTrainingTaskColumns.OWNER_ID_CERT_DIGEST,
            FederatedTrainingTaskColumns.POPULATION_NAME,
            FederatedTrainingTaskColumns.SERVER_ADDRESS,
            FederatedTrainingTaskColumns.INTERVAL_OPTIONS,
            FederatedTrainingTaskColumns.CONTEXT_DATA,
            FederatedTrainingTaskColumns.CREATION_TIME,
            FederatedTrainingTaskColumns.LAST_SCHEDULED_TIME,
            FederatedTrainingTaskColumns.LAST_RUN_START_TIME,
            FederatedTrainingTaskColumns.LAST_RUN_END_TIME,
            FederatedTrainingTaskColumns.EARLIEST_NEXT_RUN_TIME,
            FederatedTrainingTaskColumns.CONSTRAINTS,
            FederatedTrainingTaskColumns.SCHEDULING_REASON,
            FederatedTrainingTaskColumns.RESCHEDULE_COUNT,
        };
        Cursor cursor = null;
        try {
            cursor =
                    db.query(
                            FEDERATED_TRAINING_TASKS_TABLE,
                            selectColumns,
                            selection,
                            selectionArgs,
                            null,
                            null
                            /* groupBy= */ ,
                            null
                            /* having= */ ,
                            null
                            /* orderBy= */ );
            while (cursor.moveToNext()) {
                FederatedTrainingTask.Builder trainingTaskBuilder =
                        FederatedTrainingTask.builder()
                                .appPackageName(
                                        cursor.getString(
                                                cursor.getColumnIndexOrThrow(
                                                        FederatedTrainingTaskColumns
                                                                .APP_PACKAGE_NAME)))
                                .jobId(
                                        cursor.getInt(
                                                cursor.getColumnIndexOrThrow(
                                                        FederatedTrainingTaskColumns
                                                                .JOB_SCHEDULER_JOB_ID)))
                                .ownerId(cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                                FederatedTrainingTaskColumns
                                                        .OWNER_ID)))
                                .ownerIdCertDigest(cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                                FederatedTrainingTaskColumns
                                                        .OWNER_ID_CERT_DIGEST)))
                                .populationName(
                                        cursor.getString(
                                                cursor.getColumnIndexOrThrow(
                                                        FederatedTrainingTaskColumns
                                                                .POPULATION_NAME)))
                                .serverAddress(
                                        cursor.getString(
                                                cursor.getColumnIndexOrThrow(
                                                        FederatedTrainingTaskColumns
                                                                .SERVER_ADDRESS)))
                                .creationTime(
                                        cursor.getLong(
                                                cursor.getColumnIndexOrThrow(
                                                        FederatedTrainingTaskColumns
                                                                .CREATION_TIME)))
                                .lastScheduledTime(
                                        cursor.getLong(
                                                cursor.getColumnIndexOrThrow(
                                                        FederatedTrainingTaskColumns
                                                                .LAST_SCHEDULED_TIME)))
                                .lastRunStartTime(
                                        cursor.getLong(
                                                cursor.getColumnIndexOrThrow(
                                                        FederatedTrainingTaskColumns
                                                                .LAST_RUN_START_TIME)))
                                .lastRunEndTime(
                                        cursor.getLong(
                                                cursor.getColumnIndexOrThrow(
                                                        FederatedTrainingTaskColumns
                                                                .LAST_RUN_END_TIME)))
                                .earliestNextRunTime(
                                        cursor.getLong(
                                                cursor.getColumnIndexOrThrow(
                                                        FederatedTrainingTaskColumns
                                                                .EARLIEST_NEXT_RUN_TIME)))
                                .rescheduleCount(cursor.getInt(
                                        cursor.getColumnIndexOrThrow(
                                                FederatedTrainingTaskColumns.RESCHEDULE_COUNT)));
                int schedulingReason =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        FederatedTrainingTaskColumns.SCHEDULING_REASON));
                if (!cursor.isNull(schedulingReason)) {
                    trainingTaskBuilder.schedulingReason(schedulingReason);
                }
                byte[] intervalOptions =
                        cursor.getBlob(
                                cursor.getColumnIndexOrThrow(
                                        FederatedTrainingTaskColumns.INTERVAL_OPTIONS));
                if (intervalOptions != null) {
                    trainingTaskBuilder.intervalOptions(intervalOptions);
                }
                byte[] contextData =
                        cursor.getBlob(
                                cursor.getColumnIndexOrThrow(
                                        FederatedTrainingTaskColumns.CONTEXT_DATA));
                if (contextData != null) {
                    trainingTaskBuilder.contextData(contextData);
                }
                byte[] constraints =
                        cursor.getBlob(
                                cursor.getColumnIndexOrThrow(
                                        FederatedTrainingTaskColumns.CONSTRAINTS));
                trainingTaskBuilder.constraints(constraints);
                taskList.add(trainingTaskBuilder.build());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return taskList;
    }
}
