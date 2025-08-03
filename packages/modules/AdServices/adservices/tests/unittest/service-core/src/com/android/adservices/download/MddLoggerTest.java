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

package com.android.adservices.download;

import static com.android.adservices.service.stats.AdServicesStatsLog.MOBILE_DATA_DOWNLOAD_DOWNLOAD_RESULT_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.MOBILE_DATA_DOWNLOAD_FILE_GROUP_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.MOBILE_DATA_DOWNLOAD_FILE_GROUP_STORAGE_STATS_REPORTED;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;

import static com.google.mobiledatadownload.LogEnumsProto.MddDownloadResult.Code.SUCCESS;
import static com.google.mobiledatadownload.LogEnumsProto.MddDownloadResult.Code.SUCCESS_VALUE;
import static com.google.mobiledatadownload.LogEnumsProto.MddFileGroupDownloadStatus.Code.COMPLETE;
import static com.google.mobiledatadownload.LogEnumsProto.MddFileGroupDownloadStatus.Code.COMPLETE_VALUE;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.mobiledatadownload.LogProto.DataDownloadFileGroupStats;
import com.google.mobiledatadownload.LogProto.MddDownloadResultLog;
import com.google.mobiledatadownload.LogProto.MddFileGroupStatus;
import com.google.mobiledatadownload.LogProto.MddLogData;
import com.google.mobiledatadownload.LogProto.MddStorageStats;
import com.google.protobuf.MessageLite;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;

@SmallTest
public class MddLoggerTest {

    // Enum code defined in log_enums.proto.
    private static final int EVENT_CODE_UNSPECIFIED = 0;
    private static final int DATA_DOWNLOAD_FILE_GROUP_STATUS = 1044;
    private static final int DATA_DOWNLOAD_RESULT_LOG = 1068;
    private static final int DATA_DOWNLOAD_STORAGE_STATS = 1055;
    private static final long SAMPLE_INTERVAL = 1;
    private static final long TEST_TIMESTAMP = 1L;
    private static final int TEST_DAYS = 3;
    private static final long TEST_BYTE_USED = 5;

    private MockitoSession mMockitoSession;
    private MddLogger mMddLogger = new MddLogger();
    private MessageLite mMessageLite;

    @Mock private MessageLite mMockLog;
    @Spy private MddDownloadResultLog mMockMddDownloadResultLog;
    @Spy private MddFileGroupStatus mMockMddFileGroupStatus;
    @Spy private DataDownloadFileGroupStats mSpyDataDownloadFileGroupStats;
    @Spy private MddStorageStats mMockMddStorageStats;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(AdServicesStatsLog.class)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void teardown() {
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void mddLoggerTest_unspecified() {
        mMddLogger.log(mMockLog, EVENT_CODE_UNSPECIFIED);
        // Unspecified event does not trigger MDD logging.
        ExtendedMockito.verifyZeroInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void mddLoggerTest_logFileGroupStatusComplete() {
        // This test will not log any test data.
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyInt(),
                                        anyLong(),
                                        anyLong(),
                                        any(byte[].class),
                                        anyInt()));

        // Create a MessageLite using mock or default value.
        mMessageLite =
                MddLogData.newBuilder()
                        .setSamplingInterval(SAMPLE_INTERVAL)
                        .setDataDownloadFileGroupStats(mSpyDataDownloadFileGroupStats)
                        .setMddFileGroupStatus(mMockMddFileGroupStatus)
                        .build();

        when(mMockMddFileGroupStatus.getFileGroupDownloadStatus()).thenReturn(COMPLETE);
        when(mMockMddFileGroupStatus.getGroupAddedTimestampInSeconds()).thenReturn(TEST_TIMESTAMP);
        when(mMockMddFileGroupStatus.getGroupDownloadedTimestampInSeconds())
                .thenReturn(TEST_TIMESTAMP);
        when(mMockMddFileGroupStatus.getDaysSinceLastLog()).thenReturn(TEST_DAYS);

        mMddLogger.log(mMessageLite, DATA_DOWNLOAD_FILE_GROUP_STATUS);

        // Verify AdServicesStatsLog code and mocked value.
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(MOBILE_DATA_DOWNLOAD_FILE_GROUP_STATUS_REPORTED),
                                /* file_group_download_status default value */ eq(COMPLETE_VALUE),
                                /* group_added_timestamp default value  */ eq(TEST_TIMESTAMP),
                                /* group_downloaded_timestamp default value */ eq(TEST_TIMESTAMP),
                                /* file_group_stats */ any(byte[].class),
                                /* days_since_last_log default value */ eq(TEST_DAYS)));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void mddLoggerTest_logDownloadResultSuccess() {
        // This test will not log any test data.
        ExtendedMockito.doNothing()
                .when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), any(byte[].class)));

        // Create a MessageLite using mock or default value.
        mMessageLite =
                MddLogData.newBuilder()
                        .setSamplingInterval(SAMPLE_INTERVAL)
                        .setDataDownloadFileGroupStats(mSpyDataDownloadFileGroupStats)
                        .setMddDownloadResultLog(mMockMddDownloadResultLog)
                        .build();

        when(mMockMddDownloadResultLog.getResult()).thenReturn(SUCCESS);
        when(mMockMddDownloadResultLog.getDataDownloadFileGroupStats())
                .thenReturn(mSpyDataDownloadFileGroupStats);

        mMddLogger.log(mMessageLite, DATA_DOWNLOAD_RESULT_LOG);

        // Verify AdServicesStatsLog code and mocked value.
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(MOBILE_DATA_DOWNLOAD_DOWNLOAD_RESULT_REPORTED),
                                /* download_result */ eq(SUCCESS_VALUE),
                                /* file_group_stats */ any(byte[].class)));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void mddLoggerTest_logStorageStats() {
        // This test will not log any test data.
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), any(byte[].class), anyLong(), anyLong()));

        // Create a MessageLite using mock or default value.
        mMessageLite =
                MddLogData.newBuilder()
                        .setSamplingInterval(SAMPLE_INTERVAL)
                        .setMddStorageStats(mMockMddStorageStats)
                        .build();

        when(mMockMddStorageStats.getTotalMddBytesUsed()).thenReturn(TEST_BYTE_USED);
        when(mMockMddStorageStats.getTotalMddDirectoryBytesUsed()).thenReturn(TEST_BYTE_USED);

        mMddLogger.log(mMessageLite, DATA_DOWNLOAD_STORAGE_STATS);

        // Verify AdServicesStatsLog code and mocked value.
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(MOBILE_DATA_DOWNLOAD_FILE_GROUP_STORAGE_STATS_REPORTED),
                                /* storage status */ any(byte[].class),
                                /* total mdd bytes used */ eq(TEST_BYTE_USED),
                                /* total directory bytes used */ eq(TEST_BYTE_USED)));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }
}
