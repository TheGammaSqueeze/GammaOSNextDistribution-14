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
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.libraries.mobiledatadownload.DownloadFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.RemoveFileGroupsByFilterRequest;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationFileGroupPopulatorTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationFileGroupPopulator mPopulator;
    private MobileDataDownload mMdd;
    private String mPackageName;
    private static final String READ_DEVICE_CONFIG_PERMISSION =
            "android.permission.READ_DEVICE_CONFIG";

    @Before
    public void setup() throws Exception {
        mPackageName = mContext.getPackageName();
        mMdd = MobileDataDownloadFactory.getMdd(mContext);
        mPopulator = new OnDevicePersonalizationFileGroupPopulator(mContext);
        RemoveFileGroupsByFilterRequest request =
                RemoveFileGroupsByFilterRequest.newBuilder().build();
        MobileDataDownloadFactory.getMdd(mContext).removeFileGroupsByFilter(request).get();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(READ_DEVICE_CONFIG_PERMISSION);
    }

    @Test
    public void testRefreshFileGroup() throws Exception {
        mPopulator.refreshFileGroups(mMdd).get();

        String fileGroupName = OnDevicePersonalizationFileGroupPopulator.createPackageFileGroupName(
                mPackageName, mContext);
        // Trigger the download immediately.
        ClientFileGroup clientFileGroup =
                mMdd.downloadFileGroup(DownloadFileGroupRequest.newBuilder().setGroupName(
                        fileGroupName).build()).get();

        // Verify the downloaded DataFileGroup.
        assertEquals(fileGroupName, clientFileGroup.getGroupName());
        assertEquals(mContext.getPackageName(), clientFileGroup.getOwnerPackage());
        assertEquals(0, clientFileGroup.getVersionNumber());
        assertEquals(1, clientFileGroup.getFileCount());
        assertFalse(clientFileGroup.hasAccount());

        ClientFile clientFile = clientFileGroup.getFile(0);
        assertEquals(fileGroupName, clientFile.getFileId());
        assertTrue(clientFile.hasFileUri());
    }
}
