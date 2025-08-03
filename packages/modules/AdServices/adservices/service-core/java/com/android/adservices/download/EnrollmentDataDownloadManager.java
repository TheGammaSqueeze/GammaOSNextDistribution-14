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

package com.android.adservices.download;

import static com.android.adservices.service.enrollment.EnrollmentUtil.BUILD_ID;
import static com.android.adservices.service.enrollment.EnrollmentUtil.ENROLLMENT_SHARED_PREF;
import static com.android.adservices.service.enrollment.EnrollmentUtil.FILE_GROUP_STATUS;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats.FetchJobType.MDD_DOWNLOAD_JOB;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_FAILED_PARSING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__LOAD_MDD_FILE_GROUP_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.adservices.service.encryptionkey.EncryptionKeyFetcher;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.enrollment.EnrollmentUtil;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.internal.annotations.VisibleForTesting;

import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.openers.ReadStreamOpener;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/** Handles EnrollmentData download from MDD server to device. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class EnrollmentDataDownloadManager {
    private final Context mContext;
    private static volatile EnrollmentDataDownloadManager sEnrollmentDataDownloadManager;
    private final MobileDataDownload mMobileDataDownload;
    private final SynchronousFileStorage mFileStorage;
    private final Flags mFlags;
    private final AdServicesLogger mLogger;
    private final EnrollmentUtil mEnrollmentUtil;
    private final EncryptionKeyFetcher mEncryptionKeyFetcher;

    private static final String GROUP_NAME = "adtech_enrollment_data";
    private static final String DOWNLOADED_ENROLLMENT_DATA_FILE_ID = "adtech_enrollment_data.csv";
    private static final String ENROLLMENT_FILE_READ_STATUS_SHARED_PREFERENCES =
            "enrollment_data_read_status";

    @VisibleForTesting
    EnrollmentDataDownloadManager(Context context, Flags flags) {
        this(
                context,
                flags,
                AdServicesLoggerImpl.getInstance(),
                EnrollmentUtil.getInstance(context),
                new EncryptionKeyFetcher(MDD_DOWNLOAD_JOB));
    }

    @VisibleForTesting
    EnrollmentDataDownloadManager(
            Context context,
            Flags flags,
            AdServicesLogger logger,
            EnrollmentUtil enrollmentUtil,
            EncryptionKeyFetcher encryptionKeyFetcher) {
        mContext = context.getApplicationContext();
        mMobileDataDownload = MobileDataDownloadFactory.getMdd(context, flags);
        mFileStorage = MobileDataDownloadFactory.getFileStorage(context);
        mFlags = flags;
        mLogger = logger;
        mEnrollmentUtil = enrollmentUtil;
        mEncryptionKeyFetcher = encryptionKeyFetcher;
    }

    /** Gets an instance of EnrollmentDataDownloadManager to be used. */
    public static EnrollmentDataDownloadManager getInstance(@NonNull Context context) {
        if (sEnrollmentDataDownloadManager == null) {
            synchronized (EnrollmentDataDownloadManager.class) {
                if (sEnrollmentDataDownloadManager == null) {
                    sEnrollmentDataDownloadManager =
                            new EnrollmentDataDownloadManager(
                                    context,
                                    FlagsFactory.getFlags(),
                                    AdServicesLoggerImpl.getInstance(),
                                    EnrollmentUtil.getInstance(context),
                                    new EncryptionKeyFetcher(MDD_DOWNLOAD_JOB));
                }
            }
        }
        return sEnrollmentDataDownloadManager;
    }

    /**
     * Find, open and read the enrollment data file from MDD and only insert new data into the
     * enrollment database.
     */
    public ListenableFuture<DownloadStatus> readAndInsertEnrollmentDataFromMdd() {
        LogUtil.d("Reading MDD data from file.");
        Pair<ClientFile, String> FileGroupAndBuildIdPair = getEnrollmentDataFile();
        if (FileGroupAndBuildIdPair == null || FileGroupAndBuildIdPair.first == null) {
            return Futures.immediateFuture(DownloadStatus.NO_FILE_AVAILABLE);
        }

        ClientFile enrollmentDataFile = FileGroupAndBuildIdPair.first;
        String fileGroupBuildId = FileGroupAndBuildIdPair.second;
        SharedPreferences sharedPrefs =
                mContext.getSharedPreferences(
                        ENROLLMENT_FILE_READ_STATUS_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        if (sharedPrefs.getBoolean(fileGroupBuildId, false)) {
            LogUtil.d(
                    "Enrollment data build id = %s has been saved into DB. Skip adding same data.",
                    fileGroupBuildId);
            return Futures.immediateFuture(DownloadStatus.SKIP);
        }
        boolean shouldTrimEnrollmentData = mFlags.getEnrollmentMddRecordDeletionEnabled();
        Optional<List<EnrollmentData>> enrollmentDataList =
                processDownloadedFile(enrollmentDataFile, shouldTrimEnrollmentData);
        if (enrollmentDataList.isPresent()) {
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.clear().putBoolean(fileGroupBuildId, true);
            if (!editor.commit()) {
                LogUtil.e("Saving to the enrollment file read status sharedpreference failed");
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            }
            LogUtil.d(
                    "Inserted new enrollment data build id = %s into DB. "
                            + "Enrollment Mdd Record Deletion Feature Enabled: %b",
                    fileGroupBuildId, shouldTrimEnrollmentData);
            mEnrollmentUtil.logEnrollmentFileDownloadStats(mLogger, true, fileGroupBuildId);

            if (!mFlags.getEncryptionKeyNewEnrollmentFetchKillSwitch()) {
                // For new enrollment, fetch and save encryption/signing keys into DB.
                LogUtil.i("Fetch and save encryption/signing keys for new enrollment.");
                fetchEncryptionKeysForNewEnrollment(enrollmentDataList.get());
            }
            return Futures.immediateFuture(DownloadStatus.SUCCESS);
        } else {
            mEnrollmentUtil.logEnrollmentFileDownloadStats(mLogger, false, fileGroupBuildId);
            return Futures.immediateFuture(DownloadStatus.PARSING_FAILED);
        }
    }

    private void fetchEncryptionKeysForNewEnrollment(List<EnrollmentData> enrollmentDataList) {
        EncryptionKeyDao encryptionKeyDao = EncryptionKeyDao.getInstance(mContext);
        for (EnrollmentData enrollmentData : enrollmentDataList) {
            List<EncryptionKey> existingKeys =
                    encryptionKeyDao.getEncryptionKeyFromEnrollmentId(
                            enrollmentData.getEnrollmentId());
            // New enrollment which doesn't have any keys before, fetch keys for the first time.
            if (existingKeys == null || existingKeys.size() == 0) {
                Optional<List<EncryptionKey>> currentEncryptionKeys =
                        mEncryptionKeyFetcher.fetchEncryptionKeys(null, enrollmentData, true);
                if (currentEncryptionKeys.isEmpty()) {
                    LogUtil.d("No encryption key is provided by this enrollment data.");
                } else {
                    for (EncryptionKey encryptionKey : currentEncryptionKeys.get()) {
                        encryptionKeyDao.insert(encryptionKey);
                    }
                }
            }
        }
    }

    private Optional<List<EnrollmentData>> processDownloadedFile(
            ClientFile enrollmentDataFile, boolean trimTable) {
        LogUtil.d("Inserting MDD data into DB.");
        try {
            InputStream inputStream =
                    mFileStorage.open(
                            Uri.parse(enrollmentDataFile.getFileUri()), ReadStreamOpener.create());
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            bufferedReader.readLine();
            String line = null;
            // While loop runs from the second line.
            EnrollmentDao enrollmentDao = EnrollmentDao.getInstance(mContext);
            List<EnrollmentData> newEnrollments = new ArrayList<>();

            while ((line = bufferedReader.readLine()) != null) {
                // Parses CSV into EnrollmentData list.
                String[] data = line.split(",");
                if (data.length == 8) {
                    String enrollmentId = data[0];
                    LogUtil.d("Adding enrollmentId - %s", enrollmentId);
                    EnrollmentData enrollmentData =
                            new EnrollmentData.Builder()
                                    .setEnrollmentId(enrollmentId)
                                    .setEnrolledAPIs(data[1])
                                    .setSdkNames(data[2])
                                    .setAttributionSourceRegistrationUrl(
                                            data[3].contains(" ")
                                                    ? Arrays.asList(data[3].split(" "))
                                                    : List.of(data[3]))
                                    .setAttributionTriggerRegistrationUrl(
                                            data[4].contains(" ")
                                                    ? Arrays.asList(data[4].split(" "))
                                                    : List.of(data[4]))
                                    .setAttributionReportingUrl(
                                            data[5].contains(" ")
                                                    ? Arrays.asList(data[5].split(" "))
                                                    : List.of(data[5]))
                                    .setRemarketingResponseBasedRegistrationUrl(
                                            data[6].contains(" ")
                                                    ? Arrays.asList(data[6].split(" "))
                                                    : List.of(data[6]))
                                    .setEncryptionKeyUrl(data[7])
                                    .build();
                    newEnrollments.add(enrollmentData);
                } else {
                    LogUtil.e("Incorrect number of elements in row.");
                    ErrorLogUtil.e(
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_FAILED_PARSING,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
                }
            }
            if (trimTable) {
                enrollmentDao.overwriteData(newEnrollments);
                return Optional.of(newEnrollments);
            }
            for (EnrollmentData enrollmentData : newEnrollments) {
                enrollmentDao.insert(enrollmentData);
            }
            return Optional.of(newEnrollments);
        } catch (IOException e) {
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_FAILED_PARSING,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            return Optional.empty();
        }
    }

    @VisibleForTesting
    public enum DownloadStatus {
        SUCCESS,
        NO_FILE_AVAILABLE,
        PARSING_FAILED,
        // Skip reading and inserting same enrollment data to DB if the data has been saved
        // previously.
        SKIP;
    }

    private Pair<ClientFile, String> getEnrollmentDataFile() {
        GetFileGroupRequest getFileGroupRequest =
                GetFileGroupRequest.newBuilder().setGroupName(GROUP_NAME).build();
        try {
            ListenableFuture<ClientFileGroup> fileGroupFuture =
                    mMobileDataDownload.getFileGroup(getFileGroupRequest);
            ClientFileGroup fileGroup = fileGroupFuture.get();
            if (fileGroup == null) {
                LogUtil.d("MDD has not downloaded the Enrollment Data Files yet.");
                return null;
            }

            // store file group status and build id in shared preference for logging purposes
            commitFileGroupDataToSharedPref(fileGroup);
            String fileGroupBuildId = String.valueOf(fileGroup.getBuildId());
            ClientFile enrollmentDataFile = null;
            for (ClientFile file : fileGroup.getFileList()) {
                if (file.getFileId().equals(DOWNLOADED_ENROLLMENT_DATA_FILE_ID)) {
                    enrollmentDataFile = file;
                }
            }
            return Pair.create(enrollmentDataFile, fileGroupBuildId);

        } catch (ExecutionException | InterruptedException e) {
            LogUtil.e(e, "Unable to load MDD file group.");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__LOAD_MDD_FILE_GROUP_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            return null;
        }
    }

    private void commitFileGroupDataToSharedPref(ClientFileGroup fileGroup) {
        Long buildId = fileGroup.getBuildId();
        ClientFileGroup.Status fileGroupStatus = fileGroup.getStatus();
        SharedPreferences prefs =
                mContext.getSharedPreferences(ENROLLMENT_SHARED_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        if (buildId != null) {
            edit.putInt(BUILD_ID, buildId.intValue());
        }
        if (fileGroupStatus != null) {
            edit.putInt(FILE_GROUP_STATUS, fileGroupStatus.getNumber());
        }
        if (buildId != null || fileGroupStatus != null) {
            if (!edit.commit()) {
                LogUtil.e(
                        "Saving shared preferences - %s , %s and %s failed",
                        ENROLLMENT_SHARED_PREF, BUILD_ID, FILE_GROUP_STATUS);
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            }
        }
    }
}
