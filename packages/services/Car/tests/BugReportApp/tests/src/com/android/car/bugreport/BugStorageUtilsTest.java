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

import static com.android.car.bugreport.MetaBugReport.TYPE_AUDIO_FIRST;
import static com.android.car.bugreport.MetaBugReport.TYPE_AUDIO_LATER;
import static com.android.car.bugreport.Status.STATUS_EXPIRED;
import static com.android.car.bugreport.Status.STATUS_PENDING_USER_ACTION;
import static com.android.car.bugreport.Status.STATUS_UPLOADED_BEFORE;
import static com.android.car.bugreport.Status.STATUS_UPLOAD_FAILED;
import static com.android.car.bugreport.Status.STATUS_UPLOAD_PENDING;
import static com.android.car.bugreport.Status.STATUS_UPLOAD_SUCCESS;
import static com.android.car.bugreport.Status.STATUS_WRITE_PENDING;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class BugStorageUtilsTest {
    private static final String TIMESTAMP_TODAY = MetaBugReport.toBugReportTimestamp(new Date());
    private static final String BUGREPORT_FILE_NAME = "bugreport@ASD.zip";
    private static final String AUDIO_FILE_NAME = "bugreport@ASD-message.3gp";
    private static final int BUGREPORT_ZIP_FILE_CONTENT = 1;

    /**
     * Used in createBugreport() method below. If provided this value, the method creates
     * a bugreport with the BugReportApp's default time-to-live points.
     */
    private static final int USE_DEFAULT_TTL_POINTS = -9000;

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getContext();
        deleteAllBugReports();
    }

    @Test
    public void test_createBugReport_createsAndReturnsMetaBugReport() throws Exception {
        MetaBugReport bug = createBugReport(TIMESTAMP_TODAY,
                STATUS_PENDING_USER_ACTION, TYPE_AUDIO_FIRST, /* ttlPoints= */ 0);

        assertThat(BugStorageUtils.findBugReport(mContext, bug.getId()).get()).isEqualTo(bug);
    }

    @Test
    public void test_expireBugReport_marksBugReportDeletedAndDeletesZip() throws Exception {
        MetaBugReport bug = createBugReport(TIMESTAMP_TODAY,
                STATUS_PENDING_USER_ACTION, TYPE_AUDIO_FIRST, /* ttlPoints= */ 0);
        bug = createBugReportFile(bug);
        bug = createAudioFile(bug);
        try (InputStream in = BugStorageUtils.openBugReportFileToRead(mContext, bug)) {
            assertThat(in).isNotNull();
        }
        try (InputStream in = BugStorageUtils.openAudioFileToRead(mContext, bug)) {
            assertThat(in).isNotNull();
        }

        boolean deleteResult = BugStorageUtils.expireBugReport(mContext, bug.getId());

        assertThat(deleteResult).isTrue();
        MetaBugReport updatedBug = BugStorageUtils.findBugReport(mContext, bug.getId()).get();
        // Because we don't have a way of mocking Instant in BugStorageProvider#delete,
        // we can't predict its value in getStatusMessage(); so we do this kind of shenanigans.
        assertThat(updatedBug)
                .isEqualTo(bug.toBuilder()
                        .setStatus(STATUS_EXPIRED.getValue())
                        .setStatusMessage(updatedBug.getStatusMessage())
                        .build());
        assertThat(updatedBug.getStatusMessage()).startsWith("Expired at ");
        final MetaBugReport finalBug = bug;
        assertThrows(FileNotFoundException.class, () ->
                BugStorageUtils.openBugReportFileToRead(mContext, finalBug));
        assertThrows(FileNotFoundException.class, () ->
                BugStorageUtils.openAudioFileToRead(mContext, finalBug));
    }

    @Test
    public void test_completeDeleteBugReport_removesBugReportRecordFromDb() throws Exception {
        MetaBugReport bug = createBugReport(TIMESTAMP_TODAY,
                STATUS_PENDING_USER_ACTION, TYPE_AUDIO_FIRST, /* ttlPoints= */ 0);
        bug = createBugReportFile(bug);
        bug = createAudioFile(bug);
        try (InputStream in = BugStorageUtils.openBugReportFileToRead(mContext, bug)) {
            assertThat(in).isNotNull();
        }
        try (InputStream in = BugStorageUtils.openAudioFileToRead(mContext, bug)) {
            assertThat(in).isNotNull();
        }

        boolean deleteResult = BugStorageUtils.completeDeleteBugReport(mContext, bug.getId());

        assertThat(deleteResult).isTrue();
        assertThat(BugStorageUtils.findBugReport(mContext, bug.getId()).isPresent()).isFalse();
        final MetaBugReport finalBug = bug;
        assertThrows(IllegalArgumentException.class, () ->
                BugStorageUtils.openBugReportFileToRead(mContext, finalBug));
        assertThrows(IllegalArgumentException.class, () ->
                BugStorageUtils.openAudioFileToRead(mContext, finalBug));
    }

    @Test
    public void test_getUnexpiredBugReportsWithZipFile_withNonZeroTtlPoints() {
        MetaBugReport bugUserPending = createBugReport(TIMESTAMP_TODAY,
                STATUS_PENDING_USER_ACTION, TYPE_AUDIO_LATER, USE_DEFAULT_TTL_POINTS);
        createBugReport(TIMESTAMP_TODAY,
                STATUS_PENDING_USER_ACTION, TYPE_AUDIO_LATER, /* ttlPoints= */ 0);
        MetaBugReport bugUserPendingTtlPoints1 = createBugReport(TIMESTAMP_TODAY,
                STATUS_PENDING_USER_ACTION, TYPE_AUDIO_LATER, /* ttlPoints= */ 1);
        MetaBugReport bugUploadPending = createBugReport(TIMESTAMP_TODAY,
                STATUS_UPLOAD_PENDING, TYPE_AUDIO_LATER, USE_DEFAULT_TTL_POINTS);
        MetaBugReport bugUploadFailed = createBugReport(TIMESTAMP_TODAY,
                STATUS_UPLOAD_FAILED, TYPE_AUDIO_LATER, USE_DEFAULT_TTL_POINTS);
        MetaBugReport bugUploadedBefore =
                createBugReport(
                        TIMESTAMP_TODAY,
                        STATUS_UPLOADED_BEFORE,
                        TYPE_AUDIO_LATER,
                        USE_DEFAULT_TTL_POINTS);
        createBugReport(
                TIMESTAMP_TODAY, STATUS_WRITE_PENDING, TYPE_AUDIO_LATER, USE_DEFAULT_TTL_POINTS);
        createBugReport(TIMESTAMP_TODAY, STATUS_EXPIRED, TYPE_AUDIO_LATER, USE_DEFAULT_TTL_POINTS);
        createBugReport(
                TIMESTAMP_TODAY, STATUS_UPLOAD_SUCCESS, TYPE_AUDIO_LATER, USE_DEFAULT_TTL_POINTS);

        List<MetaBugReport> bugReports = BugStorageUtils.getUnexpiredBugReportsWithZipFile(
                mContext, /* ttlPointsReachedZero= */ false);

        assertThat(bugReports).containsExactly(bugUserPending, bugUserPendingTtlPoints1,
                bugUploadPending, bugUploadFailed, bugUploadedBefore);
    }

    @Test
    public void test_getUnexpiredBugReportsWithZipFile_withZeroTtlPoints() {
        createBugReport(TIMESTAMP_TODAY,
                STATUS_PENDING_USER_ACTION, TYPE_AUDIO_LATER, USE_DEFAULT_TTL_POINTS);
        MetaBugReport bugUserPendingTtlPoints0 = createBugReport(TIMESTAMP_TODAY,
                STATUS_PENDING_USER_ACTION, TYPE_AUDIO_LATER, /* ttlPoints= */ 0);
        createBugReport(TIMESTAMP_TODAY,
                STATUS_PENDING_USER_ACTION, TYPE_AUDIO_LATER, /* ttlPoints= */ 1);
        MetaBugReport bugUploadPendingTtlPoints0 = createBugReport(TIMESTAMP_TODAY,
                STATUS_UPLOAD_PENDING, TYPE_AUDIO_LATER, /* ttlPoints= */ 0);
        MetaBugReport bugUploadedBeforeTtlPoints0 = createBugReport(TIMESTAMP_TODAY,
                STATUS_UPLOADED_BEFORE, TYPE_AUDIO_LATER, /* ttlPoints= */ 0);
        createBugReport(
                TIMESTAMP_TODAY, STATUS_UPLOAD_PENDING, TYPE_AUDIO_LATER, USE_DEFAULT_TTL_POINTS);
        createBugReport(
                TIMESTAMP_TODAY, STATUS_UPLOAD_FAILED, TYPE_AUDIO_LATER, USE_DEFAULT_TTL_POINTS);
        createBugReport(
                TIMESTAMP_TODAY, STATUS_WRITE_PENDING, TYPE_AUDIO_LATER, USE_DEFAULT_TTL_POINTS);
        createBugReport(TIMESTAMP_TODAY, STATUS_EXPIRED, TYPE_AUDIO_LATER, USE_DEFAULT_TTL_POINTS);
        createBugReport(
                TIMESTAMP_TODAY, STATUS_UPLOAD_SUCCESS, TYPE_AUDIO_LATER, USE_DEFAULT_TTL_POINTS);

        List<MetaBugReport> bugReports = BugStorageUtils.getUnexpiredBugReportsWithZipFile(
                mContext, /* ttlPointsReachedZero= */ true);

        assertThat(bugReports).containsExactly(bugUserPendingTtlPoints0,
                bugUploadPendingTtlPoints0, bugUploadedBeforeTtlPoints0);
    }

    private MetaBugReport createBugReport(
            String timestamp, Status status, int type, int ttlPoints) {
        ContentValues values = new ContentValues();
        values.put(BugStorageProvider.COLUMN_USERNAME, "driver");
        values.put(BugStorageProvider.COLUMN_TITLE, "[ABCDEF] sample title");
        values.put(BugStorageProvider.COLUMN_TIMESTAMP, timestamp);
        values.put(BugStorageProvider.COLUMN_STATUS, status.getValue());
        values.put(BugStorageProvider.COLUMN_STATUS_MESSAGE, "");
        values.put(BugStorageProvider.COLUMN_TYPE, type);
        if (ttlPoints != USE_DEFAULT_TTL_POINTS) {
            values.put(BugStorageProvider.COLUMN_TTL_POINTS, ttlPoints);
        }
        ContentResolver r = mContext.getContentResolver();
        Uri uri = r.insert(BugStorageProvider.BUGREPORT_CONTENT_URI, values);
        Log.d("asd-bugreport", "uri=" + uri);
        return BugStorageUtils.findBugReport(mContext,
                Integer.parseInt(uri.getLastPathSegment())).get();
    }

    private MetaBugReport createBugReportFile(MetaBugReport bug) throws IOException {
        bug = BugStorageUtils.update(mContext,
                bug.toBuilder().setBugReportFileName(BUGREPORT_FILE_NAME).build());
        try (OutputStream out = BugStorageUtils.openBugReportFileToWrite(mContext, bug)) {
            out.write(BUGREPORT_ZIP_FILE_CONTENT);
        }
        return bug;
    }

    private MetaBugReport createAudioFile(MetaBugReport bug) throws IOException {
        bug = BugStorageUtils.update(mContext,
                bug.toBuilder().setAudioFileName(AUDIO_FILE_NAME).build());
        try (OutputStream out = BugStorageUtils.openAudioMessageFileToWrite(mContext, bug)) {
            out.write(BUGREPORT_ZIP_FILE_CONTENT);
        }
        return bug;
    }

    private void deleteAllBugReports() {
        for (MetaBugReport bug : BugStorageUtils.getAllBugReportsDescending(mContext)) {
            BugStorageUtils.completeDeleteBugReport(mContext, bug.getId());
        }
    }

    private static void assertThrows(Class<? extends Throwable> exceptionClass,
            ExceptionRunnable r) {
        try {
            r.run();
        } catch (Throwable e) {
            assertTrue("Expected exception type " + exceptionClass.getName() + " but got "
                    + e.getClass().getName(), exceptionClass.isAssignableFrom(e.getClass()));
            return;
        }
        fail("Expected exception type " + exceptionClass.getName()
                + ", but no exception was thrown");
    }

    private interface ExceptionRunnable {
        void run() throws Exception;
    }
}
