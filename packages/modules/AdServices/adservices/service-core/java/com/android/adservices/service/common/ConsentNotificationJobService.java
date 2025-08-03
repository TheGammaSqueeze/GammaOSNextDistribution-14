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

package com.android.adservices.service.common;

import static com.android.adservices.data.common.AdservicesEntryPointConstant.FIRST_ENTRY_REQUEST_TIMESTAMP;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__LOAD_MDD_FILE_GROUP_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.RVC_UX;
import static com.android.adservices.spe.AdServicesJobInfo.CONSENT_NOTIFICATION_JOB;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PersistableBundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.download.MddJobService;
import com.android.adservices.download.MobileDataDownloadFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.spe.AdServicesJobServiceLogger;

import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;

/**
 * Consent Notification job. This will be run every day during acceptable hours (provided by PH
 * flags) to trigger the Notification for Privacy Sandbox.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class ConsentNotificationJobService extends JobService {
    static final int CONSENT_NOTIFICATION_JOB_ID = CONSENT_NOTIFICATION_JOB.getJobId();
    static final long MILLISECONDS_IN_THE_DAY = 86400000L;

    static final String ADID_ENABLE_STATUS = "adid_enable_status";
    static final String RE_CONSENT_STATUS = "re_consent_status";
    private static final String ADSERVICES_STATUS_SHARED_PREFERENCE =
            "AdserviceStatusSharedPreference";

    private ConsentManager mConsentManager;

    private UxStatesManager mUxStatesManager;

    /** Schedule the Job. */
    public static void schedule(Context context, boolean adidEnabled, boolean reConsentStatus) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        long initialDelay = calculateInitialDelay(Calendar.getInstance(TimeZone.getDefault()));
        long deadline = calculateDeadline(Calendar.getInstance(TimeZone.getDefault()));
        LogUtil.d("initial delay is " + initialDelay + ", deadline is " + deadline);

        SharedPreferences sharedPref =
                context.getSharedPreferences(
                        ADSERVICES_STATUS_SHARED_PREFERENCE, Context.MODE_PRIVATE);

        long currentTimestamp = System.currentTimeMillis();
        long firstEntryRequestTimestamp =
                sharedPref.getLong(FIRST_ENTRY_REQUEST_TIMESTAMP, currentTimestamp);
        if (firstEntryRequestTimestamp == currentTimestamp) {
            // schedule the background download tasks for OTA resources at the first PPAPI request.
            MddJobService.scheduleIfNeeded(context, /* forceSchedule */ false);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putLong(FIRST_ENTRY_REQUEST_TIMESTAMP, currentTimestamp);
            if (!editor.commit()) {
                LogUtil.e("Failed to save " + FIRST_ENTRY_REQUEST_TIMESTAMP);
            }
        }
        LogUtil.d(FIRST_ENTRY_REQUEST_TIMESTAMP + ": " + firstEntryRequestTimestamp);

        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(ADID_ENABLE_STATUS, adidEnabled);
        bundle.putLong(FIRST_ENTRY_REQUEST_TIMESTAMP, firstEntryRequestTimestamp);
        bundle.putBoolean(RE_CONSENT_STATUS, reConsentStatus);

        final JobInfo job =
                new JobInfo.Builder(
                                CONSENT_NOTIFICATION_JOB_ID,
                                new ComponentName(context, ConsentNotificationJobService.class))
                        .setMinimumLatency(initialDelay)
                        .setOverrideDeadline(deadline)
                        .setExtras(bundle)
                        .setPersisted(true)
                        .build();
        jobScheduler.schedule(job);
        LogUtil.d("Scheduling Consent notification job ...");
    }

    static long calculateInitialDelay(Calendar calendar) {
        Flags flags = FlagsFactory.getFlags();
        if (flags.getConsentNotificationDebugMode()) {
            LogUtil.d("Debug mode is enabled. Setting initial delay to 0");
            return 0L;
        }
        long millisecondsInTheCurrentDay = getMillisecondsInTheCurrentDay(calendar);

        // If the current time (millisecondsInTheCurrentDay) is before
        // ConsentNotificationIntervalBeginMs (by default 9AM), schedule a job the same day at
        // earliest (ConsentNotificationIntervalBeginMs).
        if (millisecondsInTheCurrentDay < flags.getConsentNotificationIntervalBeginMs()) {
            return flags.getConsentNotificationIntervalBeginMs() - millisecondsInTheCurrentDay;
        }

        // If the current time (millisecondsInTheCurrentDay) is in the interval:
        // (ConsentNotificationIntervalBeginMs, ConsentNotificationIntervalEndMs) schedule
        // a job ASAP.
        if (millisecondsInTheCurrentDay >= flags.getConsentNotificationIntervalBeginMs()
                && millisecondsInTheCurrentDay
                        < flags.getConsentNotificationIntervalEndMs()
                                - flags.getConsentNotificationMinimalDelayBeforeIntervalEnds()) {
            return 0L;
        }

        // If the current time (millisecondsInTheCurrentDay) is after
        // ConsentNotificationIntervalEndMs (by default 5 PM) schedule a job the following day at
        // ConsentNotificationIntervalBeginMs (by default 9AM).
        return MILLISECONDS_IN_THE_DAY
                - millisecondsInTheCurrentDay
                + flags.getConsentNotificationIntervalBeginMs();
    }

    static long calculateDeadline(Calendar calendar) {
        Flags flags = FlagsFactory.getFlags();
        if (flags.getConsentNotificationDebugMode()) {
            LogUtil.d("Debug mode is enabled. Setting initial delay to 0");
            return 0L;
        }

        long millisecondsInTheCurrentDay = getMillisecondsInTheCurrentDay(calendar);

        // If the current time (millisecondsInTheCurrentDay) is before
        // ConsentNotificationIntervalEndMs (by default 5PM) reduced by
        // ConsentNotificationMinimalDelayBeforeIntervalEnds (offset period - default 1 hour) set
        // a deadline for the ConsentNotificationIntervalEndMs the same day.
        if (millisecondsInTheCurrentDay
                < flags.getConsentNotificationIntervalEndMs()
                        - flags.getConsentNotificationMinimalDelayBeforeIntervalEnds()) {
            return flags.getConsentNotificationIntervalEndMs() - millisecondsInTheCurrentDay;
        }

        // Otherwise, set a deadline for the ConsentNotificationIntervalEndMs the following day.
        return MILLISECONDS_IN_THE_DAY
                - millisecondsInTheCurrentDay
                + flags.getConsentNotificationIntervalEndMs();
    }

    private static long getMillisecondsInTheCurrentDay(Calendar calendar) {
        long currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        long currentMinute = calendar.get(Calendar.MINUTE);
        long currentSeconds = calendar.get(Calendar.SECOND);
        long currentMilliseconds = calendar.get(Calendar.MILLISECOND);
        long millisecondsInTheCurrentDay = 0;

        millisecondsInTheCurrentDay += currentHour * 60 * 60 * 1000;
        millisecondsInTheCurrentDay += currentMinute * 60 * 1000;
        millisecondsInTheCurrentDay += currentSeconds * 1000;
        millisecondsInTheCurrentDay += currentMilliseconds;

        return millisecondsInTheCurrentDay;
    }

    /** Set the consent manager instance explicitly (for testing purposes). */
    public void setConsentManager(@NonNull ConsentManager consentManager) {
        mConsentManager = consentManager;
    }

    /** Set the ux states manager instance explicitly (for testing purposes). */
    public void setUxStatesManager(@NonNull UxStatesManager uxStatesManager) {
        mUxStatesManager = uxStatesManager;
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling ConsentNotificationJobService job because it's running in"
                            + " ExtServices on T+");
            return skipAndCancelBackgroundJob(params, /* skipReason=*/ 0, /* doRecord=*/ false);
        }

        LogUtil.d("ConsentNotificationJobService.onStartJob");
        AdServicesJobServiceLogger.getInstance(this).recordOnStartJob(CONSENT_NOTIFICATION_JOB_ID);

        if (mConsentManager == null) {
            setConsentManager(ConsentManager.getInstance());
        }
        if (mUxStatesManager == null) {
            setUxStatesManager(UxStatesManager.getInstance());
        }

        mConsentManager.recordDefaultAdIdState(mConsentManager.isAdIdEnabled());
        boolean isEeaNotification =
                !mConsentManager.isAdIdEnabled() || mUxStatesManager.isEeaDevice();
        LogUtil.d(
                "ConsentNotificationJobService states. isAdIdEnabled: %s, isEeaDevice: %s,"
                        + " isEeaNotification: %s.",
                mConsentManager.isAdIdEnabled(), mUxStatesManager.isEeaDevice(), isEeaNotification);
        if (mConsentManager.getUx() == RVC_UX) {
            mConsentManager.recordMeasurementDefaultConsent(!isEeaNotification);
        } else {
            mConsentManager.recordDefaultConsent(!isEeaNotification);
        }
        boolean reConsentStatus = params.getExtras().getBoolean(RE_CONSENT_STATUS, false);

        AdServicesExecutors.getBackgroundExecutor()
                .execute(
                        () -> {
                            try {
                                boolean gaUxEnabled =
                                        FlagsFactory.getFlags().getGaUxFeatureEnabled();
                                if (!FlagsFactory.getFlags().getConsentNotificationDebugMode()
                                        && reConsentStatus
                                        && !gaUxEnabled) {
                                    LogUtil.d("already notified, return back");
                                    return;
                                }

                                if (FlagsFactory.getFlags().getUiOtaStringsFeatureEnabled()) {
                                    handleOtaStrings(
                                            params.getExtras()
                                                    .getLong(
                                                            FIRST_ENTRY_REQUEST_TIMESTAMP,
                                                            System.currentTimeMillis()),
                                            isEeaNotification);
                                } else {
                                    LogUtil.d(
                                            "OTA strings feature is not enabled, sending"
                                                    + " notification now.");
                                    AdServicesSyncUtil.getInstance()
                                            .execute(this, isEeaNotification);
                                }
                            } finally {
                                boolean shouldRetry = false;
                                AdServicesJobServiceLogger.getInstance(
                                                ConsentNotificationJobService.this)
                                        .recordJobFinished(
                                                CONSENT_NOTIFICATION_JOB_ID,
                                                /* isSuccessful= */ true,
                                                shouldRetry);

                                jobFinished(params, shouldRetry);
                            }
                        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("ConsentNotificationJobService.onStopJob");

        boolean shouldRetry = true;

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, CONSENT_NOTIFICATION_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);

        if (jobScheduler != null) {
            jobScheduler.cancel(CONSENT_NOTIFICATION_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance(this)
                    .recordJobSkipped(CONSENT_NOTIFICATION_JOB_ID, skipReason);
        }

        // Tell the JobScheduler that the job has completed and does not need to be
        // rescheduled.
        jobFinished(params, false);

        // Returning false means that this job has completed its work.
        return false;
    }

    private void handleOtaStrings(long firstEntryRequestTimestamp, boolean isEeaNotification) {
        if (System.currentTimeMillis() - firstEntryRequestTimestamp
                >= FlagsFactory.getFlags().getUiOtaStringsDownloadDeadline()) {
            LogUtil.d("Passed OTA strings download deadline, sending" + " notification now.");
            AdServicesSyncUtil.getInstance().execute(this, isEeaNotification);
        } else {
            sendNotificationIfOtaStringsDownloadCompleted(isEeaNotification);
        }
    }

    private void sendNotificationIfOtaStringsDownloadCompleted(boolean isEeaNotification) {
        try {
            ClientFileGroup cfg =
                    MobileDataDownloadFactory.getMdd(this, FlagsFactory.getFlags())
                            .getFileGroup(
                                    GetFileGroupRequest.newBuilder()
                                            .setGroupName(
                                                    FlagsFactory.getFlags()
                                                            .getUiOtaStringsGroupName())
                                            .build())
                            .get();
            if (cfg != null && cfg.getStatus() == ClientFileGroup.Status.DOWNLOADED) {
                LogUtil.d("finished downloading OTA strings." + " Sending notification now.");
                AdServicesSyncUtil.getInstance().execute(this, isEeaNotification);
                return;
            }
        } catch (InterruptedException | ExecutionException e) {
            LogUtil.e("Error while fetching clientFileGroup: " + e.getMessage());
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__LOAD_MDD_FILE_GROUP_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
        }
        LogUtil.d("OTA strings are not yet downloaded.");
        return;
    }
}
