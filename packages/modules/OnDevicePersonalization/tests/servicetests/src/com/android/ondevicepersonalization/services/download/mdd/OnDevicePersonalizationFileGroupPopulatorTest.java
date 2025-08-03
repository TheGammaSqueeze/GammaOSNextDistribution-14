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

import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.util.PackageUtils;

import com.google.android.libraries.mobiledatadownload.AddFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.DownloadFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.GetFileGroupsByFilterRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.RemoveFileGroupsByFilterRequest;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;
import com.google.mobiledatadownload.DownloadConfigProto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationFileGroupPopulatorTest {
    private static final String BASE_URL =
            "android.resource://com.android.ondevicepersonalization.servicetests/raw/test_data1";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationFileGroupPopulator mPopulator;
    private String mPackageName;
    private MobileDataDownload mMdd;
    private SynchronousFileStorage mFileStorage;

    @Before
    public void setup() throws Exception {
        mFileStorage = MobileDataDownloadFactory.getFileStorage(mContext);
        // Use direct executor to keep all work sequential for the tests
        ListeningExecutorService executorService = MoreExecutors.newDirectExecutorService();
        mMdd = MobileDataDownloadFactory.getMdd(mContext, executorService, executorService);
        mPackageName = mContext.getPackageName();
        mPopulator = new OnDevicePersonalizationFileGroupPopulator(mContext);
        RemoveFileGroupsByFilterRequest request =
                RemoveFileGroupsByFilterRequest.newBuilder().build();
        MobileDataDownloadFactory.getMdd(mContext).removeFileGroupsByFilter(request).get();
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
    }

    @Test
    public void testRefreshFileGroup() throws Exception {
        var originalIsolatedServiceAllowList =
                FlagsFactory.getFlags().getIsolatedServiceAllowList();
        PhFlagsTestUtil.setIsolatedServiceAllowList(
                "com.android.ondevicepersonalization.servicetests");
        mPopulator.refreshFileGroups(mMdd).get();
        PhFlagsTestUtil.setIsolatedServiceAllowList(originalIsolatedServiceAllowList);

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

    @Test
    public void cleanupOldFileGroup() throws Exception {
        addTestFileGroup("groupToBeRemoved");
        GetFileGroupsByFilterRequest request =
                GetFileGroupsByFilterRequest.newBuilder().setIncludeAllGroups(true).build();
        List<ClientFileGroup> clientFileGroups = mMdd.getFileGroupsByFilter(request).get();
        assertEquals(1, clientFileGroups.size());
        assertEquals("groupToBeRemoved", clientFileGroups.get(0).getGroupName());

        var originalIsolatedServiceAllowList =
                FlagsFactory.getFlags().getIsolatedServiceAllowList();
        PhFlagsTestUtil.setIsolatedServiceAllowList(
                "com.android.ondevicepersonalization.servicetests");
        mPopulator.refreshFileGroups(mMdd).get();
        PhFlagsTestUtil.setIsolatedServiceAllowList(originalIsolatedServiceAllowList);

        request = GetFileGroupsByFilterRequest.newBuilder().setIncludeAllGroups(true).build();
        clientFileGroups = mMdd.getFileGroupsByFilter(request).get();
        assertEquals(1, clientFileGroups.size());
        assertEquals(OnDevicePersonalizationFileGroupPopulator.createPackageFileGroupName(
                mPackageName, mContext), clientFileGroups.get(0).getGroupName());
    }

    @Test
    public void testCreateDownloadUrlNoSyncToken() throws Exception {
        String downloadUrl = OnDevicePersonalizationFileGroupPopulator.createDownloadUrl(
                mPackageName, mContext);
        assertTrue(downloadUrl.startsWith(BASE_URL));
    }

    @Test
    public void testCreateDownloadUrlOverrideManifest() throws Exception {
        ShellUtils.runShellCommand(
                "setprop debug.ondevicepersonalization.override_download_url_package "
                        + mPackageName);
        String overrideUrl = "https://google.com";
        ShellUtils.runShellCommand(
                "setprop debug.ondevicepersonalization.override_download_url " + overrideUrl);
        String downloadUrl = OnDevicePersonalizationFileGroupPopulator.createDownloadUrl(
                mPackageName, mContext);
        assertTrue(downloadUrl.startsWith(overrideUrl));
    }

    @Test
    public void testCreateDownloadUrlQueryParameters() throws Exception {
        long timestamp = System.currentTimeMillis();
        ComponentName service = ComponentName.createRelative(
                mPackageName, "com.test.TestPersonalizationService");
        assertTrue(OnDevicePersonalizationVendorDataDao.getInstanceForTest(mContext, service,
                        PackageUtils.getCertDigest(mContext, mPackageName))
                .batchUpdateOrInsertVendorDataTransaction(new ArrayList<>(), new ArrayList<>(),
                        timestamp));

        String downloadUrl =
                OnDevicePersonalizationFileGroupPopulator.createDownloadUrl(mPackageName, mContext);
        assertTrue(downloadUrl.startsWith(BASE_URL));
        assertTrue(downloadUrl.contains(String.valueOf(timestamp)));
    }

    private void addTestFileGroup(String groupName) throws Exception {
        String ownerPackage = mContext.getPackageName();
        String fileId = groupName;
        int byteSize = 0;
        String checksum = "";
        DownloadConfigProto.DataFile.ChecksumType checksumType =
                DownloadConfigProto.DataFile.ChecksumType.NONE;
        String downloadUrl = "http://google.com/";
        DownloadConfigProto.DownloadConditions.DeviceNetworkPolicy
                deviceNetworkPolicy =
                DownloadConfigProto.DownloadConditions.DeviceNetworkPolicy.DOWNLOAD_ONLY_ON_WIFI;
        DownloadConfigProto.DataFileGroup dataFileGroup =
                OnDevicePersonalizationFileGroupPopulator.createDataFileGroup(
                groupName,
                ownerPackage,
                new String[]{fileId},
                new int[]{byteSize},
                new String[]{checksum},
                new DownloadConfigProto.DataFile.ChecksumType[]{checksumType},
                new String[]{downloadUrl},
                deviceNetworkPolicy);
        mMdd.addFileGroup(
                AddFileGroupRequest.newBuilder().setDataFileGroup(
                        dataFileGroup).build()).get();
    }

    @After
    public void cleanup() {
        ShellUtils.runShellCommand(
                "setprop debug.ondevicepersonalization.override_download_url_package \"\"");
        ShellUtils.runShellCommand(
                "setprop debug.ondevicepersonalization.override_download_url \"\"");
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }
}
