/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.car.bugreport;

import android.annotation.NonNull;
import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipOutputStream;

/**
 * Uploads a bugreport files to GCS using a simple (no-multipart / no-resume) upload policy.
 *
 * <p>It merges bugreport zip file and audio file into one final zip file and uploads it.
 *
 * <p>Please see {@code res/values/configs.xml} and {@code res/raw/gcs_credentials.json} for the
 * configuration.
 *
 * <p>Must be run under user0.
 */
class SimpleUploaderAsyncTask extends AsyncTask<Void, Void, Boolean> {
    private static final String TAG = SimpleUploaderAsyncTask.class.getSimpleName();

    private static final String ACCESS_SCOPE =
            "https://www.googleapis.com/auth/devstorage.read_write";

    private static final String STORAGE_METADATA_TITLE = "title";

    private final Context mContext;
    private final Result mResult;

    /**
     * The uploader uploads only one bugreport each time it is called. This interface is
     * used to reschedule upload job, if there are more bugreports waiting.
     *
     * Pass true to reschedule upload job, false not to reschedule.
     */
    interface Result {
        void reschedule(boolean s);
    }

    /** Constructs SimpleUploaderAsyncTask. */
    SimpleUploaderAsyncTask(@NonNull Context context, @NonNull Result result) {
        mContext = context;
        mResult = result;
    }

    private StorageObject uploadSimple(
            Storage storage, MetaBugReport bugReport, String uploadName, InputStream data)
            throws IOException {
        InputStreamContent mediaContent = new InputStreamContent("application/zip", data);

        String bucket = getGcsBucket();

        // Create GCS MetaData.
        Map<String, String> metadata = ImmutableMap.of(
                STORAGE_METADATA_TITLE, bugReport.getTitle()
        );
        StorageObject object = new StorageObject()
                .setBucket(bucket)
                .setName(uploadName)
                .setMetadata(metadata)
                .setContentDisposition("attachment");
        Storage.Objects.Insert insertObject = storage.objects().insert(bucket, object,
                mediaContent);

        // The media uploader gzips content by default, and alters the Content-Encoding accordingly.
        // GCS dutifully stores content as-uploaded. This line disables the media uploader behavior,
        // so the service stores exactly what is in the InputStream, without transformation.
        insertObject.getMediaHttpUploader().setDisableGZipContent(true);
        Log.v(TAG, "started uploading object " + uploadName + " to bucket " + bucket);
        return insertObject.execute();
    }

    private String getGcsBucket() {
        String propGcsBucket = Config.getPropGcsBucket();
        if (!TextUtils.isEmpty(propGcsBucket)) {
            return propGcsBucket;
        }

        String gcsBucket = mContext.getString(R.string.config_gcs_bucket);
        if (TextUtils.isEmpty(gcsBucket)) {
            throw new RuntimeException("config_gcs_bucket is empty.");
        }
        return gcsBucket;
    }

    private void upload(MetaBugReport bugReport) throws IOException {
        GoogleCredential credential = GoogleCredential
                .fromStream(mContext.getResources().openRawResource(R.raw.gcs_credentials))
                .createScoped(Collections.singleton(ACCESS_SCOPE));
        Log.v(TAG, "Created credential");
        HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        Storage storage = new Storage.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("Bugreportupload/1.0").build();

        File zipFileToUpload = zipBugReportFiles(bugReport);
        String uploadName = bugReport.getBugReportFileName();
        Log.d(TAG, "Uploading file " + zipFileToUpload + " as " + uploadName);
        try (FileInputStream inputStream = new FileInputStream(zipFileToUpload)) {
            StorageObject object = uploadSimple(storage, bugReport, uploadName, inputStream);
            Log.v(TAG, "finished uploading object " + object.getName());
            File pendingDir = FileUtils.getPendingDir(mContext);
            // Delete only after successful upload; the files are needed for retry.
            if (!Strings.isNullOrEmpty(bugReport.getAudioFileName())) {
                Log.v(TAG, "Deleting file " + bugReport.getAudioFileName());
                FileUtils.deleteFileQuietly(new File(pendingDir, bugReport.getAudioFileName()));
            }
            if (!Strings.isNullOrEmpty(bugReport.getBugReportFileName())) {
                Log.v(TAG, "Deleting file " + bugReport.getBugReportFileName());
                FileUtils.deleteFileQuietly(new File(pendingDir, bugReport.getBugReportFileName()));
            }
        } finally {
            // No need to throw exception even if it fails to delete the file, as the task
            // shouldn't retry the upload again.
            Log.v(TAG, "Deleting file " + zipFileToUpload);
            FileUtils.deleteFileQuietly(zipFileToUpload);

            // Deletes replaced wav files because of re-recording.
            String lookupCode = FileUtils.extractLookupCode(bugReport);
            File pendingDir = FileUtils.getPendingDir(mContext);

            // Do not delete the current bug report and audio files in MetaBugReport because they
            // should be deleted only when uploading is completed successfully in the try clause
            // above.
            FilenameFilter filter = (folder, name) -> name.toLowerCase(Locale.ROOT).contains(
                    lookupCode.toLowerCase(Locale.ROOT)) && !name.equals(
                    bugReport.getBugReportFileName()) && !name.equals(bugReport.getAudioFileName());

            File[] filesToDelete = pendingDir.listFiles(filter);
            for (File file : filesToDelete) {
                Log.v(TAG, "Deleting file " + file.getName());
                FileUtils.deleteFileQuietly(file);
            }
        }
    }

    private File zipBugReportFiles(MetaBugReport bugReport) throws IOException {
        File finalZipFile =
                File.createTempFile("bugreport", ".zip", mContext.getCacheDir());
        File pendingDir = FileUtils.getPendingDir(mContext);
        try (ZipOutputStream zipStream = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(finalZipFile)))) {
            ZipUtils.extractZippedFileToZipStream(
                    new File(pendingDir, bugReport.getBugReportFileName()), zipStream);
            ZipUtils.addFileToZipStream(
                    new File(pendingDir, bugReport.getAudioFileName()), zipStream);
        }
        return finalZipFile;
    }

    @Override
    protected void onPostExecute(Boolean success) {
        mResult.reschedule(success);
    }

    /** Returns true is there are more files to upload. */
    @Override
    protected Boolean doInBackground(Void... voids) {
        List<MetaBugReport> bugReports = BugStorageUtils.getUploadPendingBugReports(mContext);
        boolean shouldRescheduleJob = false;

        for (MetaBugReport bugReport : bugReports) {
            try {
                if (isCancelled()) {
                    BugStorageUtils.setUploadRetry(mContext, bugReport, "Upload Job Cancelled");
                    return true;
                }
                upload(bugReport);
                BugStorageUtils.setUploadSuccess(mContext, bugReport);
            } catch (Exception e) {
                if (isFileExistsError(e)) {
                    Log.w(TAG, "Failed uploading " + bugReport.getTimestamp()
                            + " - it was already uploaded before. Marking it success.");
                    // It may leave bugreport files in the device for some time, but they are
                    // cleaned-up during ExpireOldBugReportsJob.
                    BugStorageUtils.setUploadedBefore(mContext, bugReport, e);
                    continue;
                }
                Log.w(TAG, String.format("Failed uploading %s - likely a transient error",
                        bugReport.getTimestamp()), e);
                BugStorageUtils.setUploadRetry(mContext, bugReport, e);
                shouldRescheduleJob = true;
            }
        }
        return shouldRescheduleJob;
    }

    /** Return true if the file exists with the same name in the back-end. */
    private static boolean isFileExistsError(Exception e) {
        if (!(e instanceof GoogleJsonResponseException)) {
            return false;
        }
        GoogleJsonError error = ((GoogleJsonResponseException) e).getDetails();
        // Note: In order to replace existing objects, both storage.objects.create and
        // storage.objects.delete permissions are required.
        // https://cloud.google.com/storage/docs/access-control/iam-permissions#object_permissions
        return error != null
                && error.getCode() == 403
                && error.getMessage().contains("storage.objects.delete");
    }

    @Override
    protected void onCancelled(Boolean success) {
        mResult.reschedule(true);
    }
}
