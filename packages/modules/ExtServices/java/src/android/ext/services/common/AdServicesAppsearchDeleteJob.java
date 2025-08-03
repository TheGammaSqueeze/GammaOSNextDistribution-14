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

package android.ext.services.common;


import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.appsearch.app.AppSearchSession;
import androidx.appsearch.app.SearchResults;
import androidx.appsearch.app.SearchSpec;
import androidx.appsearch.app.SetSchemaRequest;
import androidx.appsearch.app.SetSchemaResponse;
import androidx.appsearch.platformstorage.PlatformStorage;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Background Period Job that deletes Appsearch Data after OTA To T and the data has been migrated
 * to System Server. First time it runs, it stores the timestamp and check if Appsearch has any data
 * and if not, cancels itself. Next time, it checks if the maximum allowed time from OTA to keep the
 * Appsearch data has passed and if so deletes the data and cancels itself. Else it checks
 * if minimum time to the device has AdServices enabled in Flags, has passed. If so, it checks
 * if the AdServices is enabled and the first time it finds enabled, the job stores the timestamp.
 * Next runs it keeps checking if the minimum allowed time to run the data migration after enabling
 * AdServices has passed and if so deletes the Appsearch data and cancels itself.
 **/
public class AdServicesAppsearchDeleteJob extends JobService {

    private static final String TAG = "extservices";
    public static final int JOB_ID = 27; // The job id matches the placeholder in AdServicesJobInfo
    private static final String KEY_EXT_ADSERVICES_APPSEARCH_PREFS =
            "ext_adservices_appsearch_delete_job_prefs";

    static final String SHARED_PREFS_KEY_OTA_DATE = "ota_date";
    static final String SHARED_PREFS_KEY_APPSEARCH_DATA_FOUND = "appsearch_data_found";
    static final String SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE = "adservices_enabled_date";

    static final String SHARED_PREFS_KEY_ADSERVICES_APPSEARCH_DELETED =
            "adservices_appsearch_deleted";

    static final String SHARED_PREFS_KEY_ATTEMPTED_DELETE_COUNT =
            "attempted_delete_count";
    static final String SHARED_PREFS_KEY_JOB_RUN_COUNT = "job_run_count";

    private static final String CONSENT_DATABASE_NAME = "adservices_consent";
    private static final String APP_CONSENT_DATABASE_NAME = "adservices_app_consent";
    private static final String NOTIFICATION_DATABASE_NAME = "adservices_notification";
    private static final String INTERACTIONS_DATABASE_NAME = "adservices_interactions";
    private static final String TOPICS_DATABASE_NAME = "adservices-topics";
    private static final String UX_STATES_DATABASE_NAME = "adservices-ux-states";
    private static final String MEASUREMENT_ROLLBACK_DATABASE_NAME = "measurement_rollback";

    private static final List<String> AD_SERVICES_APPSEARCH_DBS_TO_DELETE = List.of(
            CONSENT_DATABASE_NAME,
            APP_CONSENT_DATABASE_NAME,
            NOTIFICATION_DATABASE_NAME,
            INTERACTIONS_DATABASE_NAME,
            TOPICS_DATABASE_NAME,
            UX_STATES_DATABASE_NAME,
            MEASUREMENT_ROLLBACK_DATABASE_NAME);

    private final Executor mExecutor = Executors.newCachedThreadPool();

    @Override
    public boolean onStartJob(JobParameters params) {
        int jobId = params.getJobId();
        Log.i(TAG, "AdServicesAppsearchDeleteJobService invoked with job id: "
                + jobId);
        SharedPreferences sharedPref = getSharedPreferences();
        SharedPreferences.Editor editor = sharedPref.edit();

        try {
            AdservicesPhFlags adservicesPhFlags = getAdservicesPhFlags();
            if (!adservicesPhFlags.isAppsearchDeleteJobEnabled()) {
                Log.d(TAG,
                        "AdServicesAppsearchDeleteJobService is not enabled in config,"
                                + " cancelling job id: " + params.getJobId());
                cancelPeriodicJob(this, params);
                return false;
            }
            if (adservicesPhFlags.shouldDoNothingAdServicesAppsearchDeleteJob()) {
                Log.d(TAG,
                        "AdServicesAppsearchDeleteJobService is set to do nothing in config,"
                                + " returning.... ");
                return false;
            }

            long jobRunCount = sharedPref.getLong(SHARED_PREFS_KEY_JOB_RUN_COUNT,
                    /* defaultValue= */ 0L) + 1;
            Log.d(TAG,
                    "AdServicesAppsearchDeleteJobService job run count is " + jobRunCount);
            editor.putLong(SHARED_PREFS_KEY_JOB_RUN_COUNT, jobRunCount);

            long otaDate = sharedPref.getLong(SHARED_PREFS_KEY_OTA_DATE, 0L);
            // Check if the job is run first time after OTA
            if (otaDate == 0L) {
                long currentTime = System.currentTimeMillis();
                Log.d(TAG,
                        "AdServicesAppsearchDeleteJobService OTA to T "
                                + " on : " + currentTime);
                editor.putLong(SHARED_PREFS_KEY_OTA_DATE, currentTime);
                boolean foundData = !isAppsearchDbEmpty(this, mExecutor,
                        NOTIFICATION_DATABASE_NAME);
                editor.putBoolean(SHARED_PREFS_KEY_APPSEARCH_DATA_FOUND, foundData);
                Log.d(TAG, "AdServicesAppsearchDeleteJobService found data in Appsearch: "
                        + foundData);
                if (!foundData) {
                    cancelPeriodicJob(this, params);
                }
            } else if (hasMinMinutesPassed(otaDate,
                    adservicesPhFlags.getMinMinutesFromOtaToDeleteAppsearchData())) {
                Log.d(TAG, "Deleting Appsearch Data as maximum allowed time passed "
                        + "from OTA");
                deleteAppsearchData(params, editor, sharedPref,
                        adservicesPhFlags.getMaxAppsearchAdServicesDeleteAttempts());
            } else if (!hasMinMinutesPassed(otaDate,
                    adservicesPhFlags.getMinMinutesFromOtaToCheckAdServicesStatus())) {
                Log.d(TAG, "Minimum time to check AdServices status from OTA "
                        + "has not passed, returning....");
            } else {
                long adServicesEnabledDate = sharedPref
                        .getLong(SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE, 0L);
                boolean adServicesEnabled = adservicesPhFlags.isAdServicesEnabled();
                if (!adServicesEnabled) {
                    // restart the timer
                    editor.putLong(SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE, 0L);
                    Log.d(TAG,
                            "AdServicesAppsearchDeleteJobService found "
                                    + "AdServices Disabled");
                } else if (adServicesEnabledDate == 0L) {
                    long currentTime = System.currentTimeMillis();
                    editor.putLong(SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE, currentTime);
                    Log.d(TAG, "AdServicesAppsearchDeleteJobService found "
                            + "AdServices Enabled on : " + currentTime);
                } else if (hasMinMinutesPassed(adServicesEnabledDate,
                        adservicesPhFlags.getMinMinutesToDeleteFromAdServicesEnabled())) {
                    Log.d(TAG, "Deleting Appsearch Data after verifying minimum time passed "
                            + "from AdServices enabled");
                    deleteAppsearchData(params, editor, sharedPref,
                            adservicesPhFlags.getMaxAppsearchAdServicesDeleteAttempts());
                } else {
                    Log.d(TAG, "Not Deleting Appsearch Data as minimum time"
                            + " has not passed from AdServices enabled");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception in AdServicesAppsearchDeleteJob " + e);
        }

        if (!editor.commit()) {
            Log.e(TAG, "AdServicesAppsearchDeleteJob could not commit shared prefs");
        }
        return false;
    }

    private void deleteAppsearchData(JobParameters params, SharedPreferences.Editor editor,
            SharedPreferences sharedPreferences, int maxAttempts) {

        if (deleteAppsearchDbs(this, mExecutor, AD_SERVICES_APPSEARCH_DBS_TO_DELETE)) {
            Log.d(TAG,
                    "AdServicesAppsearchDeleteJobService deleted data in Appsearch "
                            + " cancelling future runs of job id: "
                            + params.getJobId());
            editor.putLong(SHARED_PREFS_KEY_ADSERVICES_APPSEARCH_DELETED,
                    System.currentTimeMillis());
            cancelPeriodicJob(this, params);
        } else {
            int attemptedDeletes = sharedPreferences
                    .getInt(SHARED_PREFS_KEY_ATTEMPTED_DELETE_COUNT, 0) + 1;
            editor.putInt(SHARED_PREFS_KEY_ATTEMPTED_DELETE_COUNT, attemptedDeletes);
            Log.e(TAG,
                    "AdServicesAppsearchDeleteJobService did not delete"
                            + " all Appsearch dbs on attempt " + attemptedDeletes);
            if (attemptedDeletes >= maxAttempts) {
                Log.e(TAG, "Max attempts to deletes has been reached, cancelling future jobs");
                cancelPeriodicJob(this, params);
            }
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "AdServicesAppsearchDeleteJobService onStopJob invoked with "
                + "job id " + params.getJobId());
        return false;
    }

    /**
     * checks if data is empty in Appsearch database
     *
     * @param context  android context
     * @param executor Executor service
     * @param db       Appsearch db to check data is empty
     * @return {@code true} Appsearch database is empty; else {@code false}.
     **/
    @VisibleForTesting
    @TargetApi(31)
    public boolean isAppsearchDbEmpty(Context context, Executor executor, String db)
            throws TimeoutException, ExecutionException, InterruptedException {
        final ListenableFuture<AppSearchSession> appSearchSession =
                PlatformStorage.createSearchSessionAsync(
                        new PlatformStorage.SearchContext.Builder(context, db).build());

        ListenableFuture<SearchResults> searchFuture =
                Futures.transform(
                        appSearchSession,
                        session -> session.search("", new SearchSpec.Builder().build()),
                        executor);
        FluentFuture<Integer> future =
                FluentFuture.from(searchFuture)
                        .transformAsync(
                                results ->
                                        Futures.transform(results.getNextPageAsync(),
                                                List::size, executor),
                                executor);
        int resultsSize = future.get(500, TimeUnit.MILLISECONDS);
        Log.d(TAG, "Appsearch found results of size " + resultsSize);
        return resultsSize == 0;
    }

    /**
     * Deletes App search Database by calling setForceOverride true on the schemaRequest
     *
     * @param context  the android context
     * @param executor Executor
     * @param dbs      the list of database names to be deleted
     * @return {@code true} deletion was a success; else {@code false}.
     **/
    @VisibleForTesting
    public boolean deleteAppsearchDbs(Context context, Executor executor, List<String> dbs) {
        int successCount = 0;
        for (String appsearchDb : dbs) {
            if (deleteAppsearchDb(context, executor, appsearchDb)) successCount++;
        }
        boolean success = successCount == dbs.size();
        Log.d(TAG, "AdServicesAppsearchDeleteJobService Complete with success " + successCount
                + " out of " + dbs.size() + ",success status is " + success);
        return success;

    }

    /**
     * Deletes App search Database
     *
     * @param context  the android context
     * @param executor Executor
     * @param db       the database name to be deleted
     * @return {@code true} deletion was a success; else {@code false}.
     **/
    @VisibleForTesting
    public boolean deleteAppsearchDb(Context context, Executor executor, String db) {
        Log.d(TAG, "Deleting AdServices Appsearch db " + db);
        try {
            SetSchemaResponse setSchemaResponse = getDeleteSchemaResponse(context,
                    executor,
                    db);
            if (!setSchemaResponse.getMigrationFailures().isEmpty()) {
                Log.e(TAG,
                        "Delete failed for AdServices Appsearch db " + db
                                + " , SetSchemaResponse migration failure: "
                                + setSchemaResponse
                                .getMigrationFailures()
                                .get(0));
                return false;
            }
            Log.d(TAG, "Delete types size " + setSchemaResponse.getDeletedTypes().size());
            for (String deletedType : setSchemaResponse.getDeletedTypes()) {
                Log.d(TAG, "Deleted type is " + deletedType);
            }
            Log.d(TAG, "Delete successful for AdServices Appsearch db " + db);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Delete failed for AdServices Appsearch db " + db + " " + e);
            return false;
        }
    }

    /**
     * Creates the appSearch session and calls schema request to setForceOverride on a database
     * to delete it.
     *
     * @param context  the android context
     * @param executor executor service
     * @param db       database name to delete
     * @return SetSchemaResponse from executing the schema request to delete db
     **/
    @VisibleForTesting
    @TargetApi(31)
    public SetSchemaResponse getDeleteSchemaResponse(Context context, Executor executor,
            String db) throws InterruptedException, TimeoutException, ExecutionException {
        final ListenableFuture<AppSearchSession> appSearchSession =
                PlatformStorage.createSearchSessionAsync(
                        new PlatformStorage.SearchContext.Builder(context, db).build());
        SetSchemaRequest setSchemaRequest = new SetSchemaRequest.Builder().setForceOverride(
                true).build();
        return FluentFuture.from(appSearchSession)
                .transformAsync(
                        session -> session.setSchemaAsync(setSchemaRequest), executor)
                .get(500, TimeUnit.MILLISECONDS);
    }

    /**
     * Checks if the give date in string has passed the minutes compared to current date
     *
     * @param timestampToCheckInMillis date time in millis to check against
     * @param minMinutesToCheck        minimum minutes
     **/
    @VisibleForTesting
    public boolean hasMinMinutesPassed(long timestampToCheckInMillis,
            long minMinutesToCheck) {
        long currentTimestamp = System.currentTimeMillis();
        long millisToMinutes = 60000L;
        long minutesPassed = (currentTimestamp - timestampToCheckInMillis)
                / millisToMinutes;
        Log.d(TAG, "The minutes from current date " + currentTimestamp
                + " to dateToCheck " + timestampToCheckInMillis
                + " is " + minutesPassed
                + " and minimum minutes to check " + minMinutesToCheck);
        return minutesPassed >= minMinutesToCheck;
    }


    /**
     * Cancels the current periodic job and sets the job to not reschedule
     *
     * @param context the android context
     * @param params  the job params
     **/
    @VisibleForTesting
    public void cancelPeriodicJob(Context context, JobParameters params) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            int jobId = params.getJobId();
            jobScheduler.cancel(jobId);
            Log.d(TAG, "AdServicesAppsearchDeletePeriodicJobService cancelled job "
                    + jobId);
        }
        setReschedule(params, false);
    }

    /**
     * call the parent jobFinished method with setting the re-schedule flag
     *
     * @param jobParameters job params
     * @param reschedule    whether to reschedule the job
     **/
    @VisibleForTesting
    public void setReschedule(JobParameters jobParameters, boolean reschedule) {
        jobFinished(jobParameters, reschedule);
    }

    /**
     * returns the instance of Adservices Ph flags object
     **/
    @VisibleForTesting
    public AdservicesPhFlags getAdservicesPhFlags() {
        return new AdservicesPhFlags();
    }

    /**
     * returns the shared prefs object
     **/
    @VisibleForTesting
    SharedPreferences getSharedPreferences() {
        return this.getSharedPreferences(KEY_EXT_ADSERVICES_APPSEARCH_PREFS,
                Context.MODE_PRIVATE);
    }

    /**
     * Schedules AdServicesAppsearchDeleteJob run periodically to check the
     * AdServices status and then create the actual delete job to delete all the AdServices
     * app search data.
     *
     * @param context the android context
     **/
    @SuppressLint("MissingPermission")
    public static void scheduleAdServicesAppsearchDeletePeriodicJob(
            Context context, AdservicesPhFlags adservicesPhFlags) {
        try {
            Log.d(TAG,
                    "Scheduling AdServicesAppsearchDeleteJobService ...");

            if (!adservicesPhFlags.isAppsearchDeleteJobEnabled()) {
                Log.d(TAG,
                        "AdServicesAppsearchDeleteJobService periodic job disabled in "
                                + "config, Cancelling Scheduling  ...");
                return;
            }

            final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
            if (jobScheduler == null) {
                Log.e(TAG, "AdServicesAppsearchDeleteJobService JobScheduler is null");
                return;
            }
            final JobInfo oldJob = jobScheduler.getPendingJob(JOB_ID);
            if (oldJob != null) {
                Log.d(TAG, "AdServicesAppsearchDeleteJobService already scheduled"
                        + " with job id:" + JOB_ID
                        + ", skipping reschedule");
                return;
            }
            JobInfo.Builder jobInfoBuild = new JobInfo.Builder(JOB_ID,
                    new ComponentName(context, AdServicesAppsearchDeleteJob.class));
            jobInfoBuild.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            jobInfoBuild.setPersisted(true);

            jobInfoBuild.setPeriodic(
                    adservicesPhFlags.getAppsearchDeletePeriodicIntervalMillis(),
                    adservicesPhFlags.getAppsearchDeleteJobFlexMillis());

            final JobInfo job = jobInfoBuild.build();

            jobScheduler.schedule(job);
            Log.d(TAG, "Scheduled AdServicesAppsearchDeleteJobService with job id: " + JOB_ID);
        } catch (Exception e) {
            Log.e(TAG, "Exception in scheduling job AdServicesAppsearchDeleteJobService " + e);
        }
    }

}
