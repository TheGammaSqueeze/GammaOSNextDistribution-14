/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.car.bugreport.Status.STATUS_PENDING_USER_ACTION;
import static com.android.car.bugreport.Status.STATUS_UPLOADED_BEFORE;
import static com.android.car.bugreport.Status.STATUS_UPLOAD_FAILED;
import static com.android.car.bugreport.Status.STATUS_UPLOAD_PENDING;
import static com.android.car.bugreport.Status.STATUS_WRITE_PENDING;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Date;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class TtlPointsDecrementalTest {
    private static final String TIMESTAMP_TODAY = MetaBugReport.toBugReportTimestamp(new Date());

    private static final int USE_DEFAULT_TTL_POINTS = -9000;

    private Context mContext;
    private TtlPointsDecremental mSubject;

    @Mock
    Runnable mFinishedCallback;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getContext();
        mSubject = new TtlPointsDecremental(mContext, mFinishedCallback);
        deleteAllBugReports();
    }

    /** Makes sure only certain bugreports will get ttl_points decreased. */
    @Test
    public void test_decrementsTtlPointsForPendingBugReports() {
        MetaBugReport bugUserPending_ttl_points0 = createBugReport(TIMESTAMP_TODAY,
                STATUS_PENDING_USER_ACTION, /* ttl_points= */ 0);
        MetaBugReport bugUserPending_ttl_points1 = createBugReport(TIMESTAMP_TODAY,
                STATUS_PENDING_USER_ACTION, /* ttl_points= */ 1);
        MetaBugReport bugUserPending_ttl_points2 = createBugReport(TIMESTAMP_TODAY,
                STATUS_PENDING_USER_ACTION, /* ttl_points= */ 2);
        MetaBugReport bugUploadPending = createBugReport(TIMESTAMP_TODAY,
                STATUS_UPLOAD_PENDING, /* ttl_points= */ 2);
        MetaBugReport bugUploadFailed = createBugReport(TIMESTAMP_TODAY,
                STATUS_UPLOAD_FAILED, /* ttl_points= */ 2);
        MetaBugReport bugUploadWritePending = createBugReport(TIMESTAMP_TODAY,
                STATUS_WRITE_PENDING, /* ttl_points= */ 2);
        MetaBugReport bugUploadedBefore = createBugReport(TIMESTAMP_TODAY,
                STATUS_UPLOADED_BEFORE, /* ttl_points= */ 2);

        mSubject.run();

        assertThat(find(bugUserPending_ttl_points0)).isEqualTo(bugUserPending_ttl_points0);
        assertThat(find(bugUserPending_ttl_points1)).isEqualTo(
                bugUserPending_ttl_points1.toBuilder().setTtlPoints(0).build());
        assertThat(find(bugUserPending_ttl_points2)).isEqualTo(
                bugUserPending_ttl_points2.toBuilder().setTtlPoints(1).build());
        assertThat(find(bugUploadPending)).isEqualTo(
                bugUploadPending.toBuilder().setTtlPoints(1).build());
        assertThat(find(bugUploadFailed)).isEqualTo(
                bugUploadFailed.toBuilder().setTtlPoints(1).build());
        assertThat(find(bugUploadWritePending)).isEqualTo(bugUploadWritePending);
        assertThat(find(bugUploadedBefore)).isEqualTo(
                bugUploadedBefore.toBuilder().setTtlPoints(1).build());
        verify(mFinishedCallback).run();
    }

    private MetaBugReport find(MetaBugReport bug) {
        return BugStorageUtils.findBugReport(mContext, bug.getId()).get();
    }

    private MetaBugReport createBugReport(String timestamp, Status status, int ttlPoints) {
        ContentValues values = new ContentValues();
        values.put(BugStorageProvider.COLUMN_USERNAME, "driver");
        values.put(BugStorageProvider.COLUMN_TITLE, "[ABCDEF] sample title");
        values.put(BugStorageProvider.COLUMN_TIMESTAMP, timestamp);
        values.put(BugStorageProvider.COLUMN_STATUS, status.getValue());
        values.put(BugStorageProvider.COLUMN_STATUS_MESSAGE, "");
        values.put(BugStorageProvider.COLUMN_TYPE, MetaBugReport.TYPE_AUDIO_LATER);
        if (ttlPoints != USE_DEFAULT_TTL_POINTS) {
            values.put(BugStorageProvider.COLUMN_TTL_POINTS, ttlPoints);
        }
        ContentResolver r = mContext.getContentResolver();
        Uri uri = r.insert(BugStorageProvider.BUGREPORT_CONTENT_URI, values);
        return BugStorageUtils.findBugReport(mContext,
                Integer.parseInt(uri.getLastPathSegment())).get();
    }

    private void deleteAllBugReports() {
        for (MetaBugReport bug : BugStorageUtils.getAllBugReportsDescending(mContext)) {
            BugStorageUtils.completeDeleteBugReport(mContext, bug.getId());
        }
    }
}
