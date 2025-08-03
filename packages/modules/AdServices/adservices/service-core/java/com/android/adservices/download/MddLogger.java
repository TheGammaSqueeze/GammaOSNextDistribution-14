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

import com.android.adservices.service.stats.AdServicesStatsLog;

import com.google.android.libraries.mobiledatadownload.Logger;
import com.google.android.libraries.mobiledatadownload.internal.logging.LogUtil;
import com.google.mobiledatadownload.LogEnumsProto.MddClientEvent.Code;
import com.google.mobiledatadownload.LogProto;
import com.google.mobiledatadownload.LogProto.DataDownloadFileGroupStats;
import com.google.mobiledatadownload.LogProto.MddDownloadResultLog;
import com.google.mobiledatadownload.LogProto.MddFileGroupStatus;
import com.google.mobiledatadownload.LogProto.MddLogData;
import com.google.mobiledatadownload.MobileDataDownloadFileGroupStats;
import com.google.mobiledatadownload.MobileDataDownloadFileGroupStorageStats;
import com.google.mobiledatadownload.MobileDataDownloadStorageStats;
import com.google.protobuf.MessageLite;

/** A MDD {@link Logger} which uses {@link AdServicesStatsLog} to write logs. */
public class MddLogger implements Logger {
    private static final String TAG = "AdServicesLogger";

    @Override
    public void log(MessageLite log, int eventCode) {

        switch (Code.forNumber(eventCode)) {
            case DATA_DOWNLOAD_FILE_GROUP_STATUS:
                logFileGroupStatus(log);
                break;
            case DATA_DOWNLOAD_RESULT_LOG:
                logDownloadResult(log);
                break;
            case DATA_DOWNLOAD_STORAGE_STATS:
                logStorageStats(log);
                break;
            default:
                LogUtil.d("%s: Received unsupported event code %d, skipping log", TAG, eventCode);
                break;
        }
    }

    /** Helper method to handle logging File Group Status events. */
    private void logFileGroupStatus(MessageLite log) {
        // NOTE: log will always be MddLogData
        MddLogData logData = (MddLogData) log;
        DataDownloadFileGroupStats mddGroupStats = logData.getDataDownloadFileGroupStats();
        MddFileGroupStatus mddFileGroupStatus = logData.getMddFileGroupStatus();
        MobileDataDownloadFileGroupStats groupStats =
                buildGroupStats((int) logData.getSamplingInterval(), mddGroupStats);

        AdServicesStatsLog.write(
                AdServicesStatsLog.MOBILE_DATA_DOWNLOAD_FILE_GROUP_STATUS_REPORTED,
                /* file_group_download_status = */ mddFileGroupStatus
                        .getFileGroupDownloadStatus()
                        .getNumber(),
                /* group_added_timestamp = */ mddFileGroupStatus.getGroupAddedTimestampInSeconds(),
                /* group_downloaded_timestamp = */ mddFileGroupStatus
                        .getGroupDownloadedTimestampInSeconds(),
                /* file_group_stats = */ groupStats.toByteArray(),
                /* days_since_last_log = */ mddFileGroupStatus.getDaysSinceLastLog());
    }

    private void logDownloadResult(MessageLite log) {
        MddLogData logData = (MddLogData) log;
        MddDownloadResultLog mddDownloadResult = logData.getMddDownloadResultLog();
        MobileDataDownloadFileGroupStats groupStats =
                buildGroupStats(
                        (int) logData.getSamplingInterval(),
                        mddDownloadResult.getDataDownloadFileGroupStats());
        AdServicesStatsLog.write(
                AdServicesStatsLog.MOBILE_DATA_DOWNLOAD_DOWNLOAD_RESULT_REPORTED,
                /* download_result = */ mddDownloadResult.getResult().getNumber(),
                /* file_group_stats = */ groupStats.toByteArray());
    }

    private void logStorageStats(MessageLite log) {
        MddLogData logData = (MddLogData) log;
        LogProto.MddStorageStats mddStorageStats = logData.getMddStorageStats();
        MobileDataDownloadStorageStats.Builder storageStats =
                MobileDataDownloadStorageStats.newBuilder();
        for (int i = 0; i < mddStorageStats.getDataDownloadFileGroupStatsCount(); i++) {
            storageStats.addMobileDataDownloadFileGroupStorageStats(
                    MobileDataDownloadFileGroupStorageStats.newBuilder()
                            .setTotalBytesUsed(mddStorageStats.getTotalBytesUsed(i))
                            .setTotalInlineBytesUsed(mddStorageStats.getTotalInlineBytesUsed(i))
                            .setDownloadedGroupBytesUsed(
                                    mddStorageStats.getDownloadedGroupBytesUsed(i))
                            .setFileGroupStats(
                                    buildGroupStats(
                                            (int) logData.getSamplingInterval(),
                                            mddStorageStats.getDataDownloadFileGroupStats(i)))
                            .build());
        }
        AdServicesStatsLog.write(
                AdServicesStatsLog.MOBILE_DATA_DOWNLOAD_FILE_GROUP_STORAGE_STATS_REPORTED,
                storageStats.build().toByteArray(),
                mddStorageStats.getTotalMddBytesUsed(),
                mddStorageStats.getTotalMddDirectoryBytesUsed());
    }

    private static MobileDataDownloadFileGroupStats buildGroupStats(
            int samplingInterval, DataDownloadFileGroupStats mddGroupStats) {
        return MobileDataDownloadFileGroupStats.newBuilder()
                .setSamplingInterval(samplingInterval)
                .setFileGroupName(mddGroupStats.getFileGroupName())
                .setVariantId(mddGroupStats.getVariantId())
                .setBuildId(mddGroupStats.getBuildId())
                .setFileCount(mddGroupStats.getFileCount())
                .setHasAccount(mddGroupStats.getHasAccount())
                .build();
    }
}
