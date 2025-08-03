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

package com.android.ondevicepersonalization.services.download.mdd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.libraries.mobiledatadownload.AddFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.DownloadFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;
import com.google.mobiledatadownload.DownloadConfigProto.DataFile;
import com.google.mobiledatadownload.DownloadConfigProto.DataFileGroup;
import com.google.mobiledatadownload.DownloadConfigProto.DownloadConditions;
import com.google.mobiledatadownload.DownloadConfigProto.DownloadConditions.DeviceNetworkPolicy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.ExecutionException;

/** Unit tests for {@link MobileDataDownloadFactory} */
@RunWith(JUnit4.class)
public class MobileDataDownloadTest {

    private static final String FILE_GROUP_NAME_1 = "test-group-1";
    private static final String FILE_ID_1 = "test-file-1";
    private static final String FILE_ID_2 = "test-file-2";
    private static final String FILE_CHECKSUM_1 = "c1ef7864c76a99ae738ddad33882ed65972c99cc";
    private static final String FILE_URL_1 = "https://www.gstatic.com/suggest-dev/odws1_test_4.jar";
    private static final int FILE_SIZE_1 = 85769;
    private static final String FILE_CHECKSUM_2 = "a1cba9d87b1440f41ce9e7da38c43e1f6bd7d5df";
    private static final String FILE_URL_2 = "https://www.gstatic.com/suggest-dev/odws1_empty.jar";
    private static final int FILE_SIZE_2 = 554;
    private final Context mContext = ApplicationProvider.getApplicationContext();

    // A helper function to create a DataFilegroup.
    private static DataFileGroup createDataFileGroup(
            String groupName,
            String ownerPackage,
            int versionNumber,
            String[] fileId,
            int[] byteSize,
            String[] checksum,
            String[] url,
            DeviceNetworkPolicy deviceNetworkPolicy) {
        if (fileId.length != byteSize.length
                || fileId.length != checksum.length
                || fileId.length != url.length) {
            throw new IllegalArgumentException();
        }

        DataFileGroup.Builder dataFileGroupBuilder =
                DataFileGroup.newBuilder()
                        .setGroupName(groupName)
                        .setOwnerPackage(ownerPackage)
                        .setFileGroupVersionNumber(versionNumber)
                        .setDownloadConditions(
                                DownloadConditions.newBuilder()
                                        .setDeviceNetworkPolicy(deviceNetworkPolicy));

        for (int i = 0; i < fileId.length; ++i) {
            DataFile file =
                    DataFile.newBuilder()
                            .setFileId(fileId[i])
                            .setByteSize(byteSize[i])
                            .setChecksum(checksum[i])
                            .setUrlToDownload(url[i])
                            .build();
            dataFileGroupBuilder.addFile(file);
        }

        return dataFileGroupBuilder.build();
    }

    @Test
    public void testCreateMddManagerSuccessfully() throws ExecutionException, InterruptedException {
        MobileDataDownload mdd =
                MobileDataDownloadFactory.getMdd(mContext);

        DataFileGroup dataFileGroup =
                createDataFileGroup(
                        FILE_GROUP_NAME_1,
                        mContext.getPackageName(),
                        5 /* versionNumber */,
                        new String[]{FILE_ID_1, FILE_ID_2},
                        new int[]{FILE_SIZE_1, FILE_SIZE_2},
                        new String[]{FILE_CHECKSUM_1, FILE_CHECKSUM_2},
                        new String[]{FILE_URL_1, FILE_URL_2},
                        DeviceNetworkPolicy.DOWNLOAD_ONLY_ON_WIFI);
        // Add the DataFileGroup to MDD
        AddFileGroupRequest request = AddFileGroupRequest.newBuilder()
                .setDataFileGroup(dataFileGroup)
                .build();
        assertTrue(mdd.addFileGroup(request).get());

        // Trigger the download immediately.
        ClientFileGroup clientFileGroup =
                mdd.downloadFileGroup(
                        DownloadFileGroupRequest.newBuilder()
                                .setGroupName(FILE_GROUP_NAME_1)
                                .build())
                        .get();

        // Verify the downloaded DataFileGroup.
        assertEquals(FILE_GROUP_NAME_1, clientFileGroup.getGroupName());
        assertEquals(mContext.getPackageName(), clientFileGroup.getOwnerPackage());
        assertEquals(5, clientFileGroup.getVersionNumber());
        assertEquals(2, clientFileGroup.getFileCount());
        assertFalse(clientFileGroup.hasAccount());
    }
}
