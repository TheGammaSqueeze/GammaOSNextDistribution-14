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

package com.android.ondevicepersonalization.services.download;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.adservices.ondevicepersonalization.DownloadCompletedOutputParcel;
import android.content.ComponentName;
import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.data.vendor.VendorData;
import com.android.ondevicepersonalization.services.data.vendor.VendorDataContract;
import com.android.ondevicepersonalization.services.download.mdd.MobileDataDownloadFactory;
import com.android.ondevicepersonalization.services.download.mdd.OnDevicePersonalizationFileGroupPopulator;
import com.android.ondevicepersonalization.services.util.PackageUtils;

import com.google.android.libraries.mobiledatadownload.DownloadFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.RemoveFileGroupsByFilterRequest;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@RunWith(Parameterized.class)
public class OnDevicePersonalizationDataProcessingAsyncCallableTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationFileGroupPopulator mPopulator;
    private MobileDataDownload mMdd;
    private String mPackageName;
    private SynchronousFileStorage mFileStorage;
    private final VendorData mContent1 = new VendorData.Builder()
            .setKey("key1")
            .setData("dGVzdGRhdGEx".getBytes())
            .build();

    private final VendorData mContent2 = new VendorData.Builder()
            .setKey("key2")
            .setData("dGVzdGRhdGEy".getBytes())
            .build();

    private final VendorData mContentExtra = new VendorData.Builder()
            .setKey("keyExtra")
            .setData("extra".getBytes())
            .build();

    private ComponentName mService;
    private FutureCallback mTestCallback;
    private boolean mCallbackSuccess;
    private boolean mCallbackFailure;
    private CountDownLatch mLatch;
    @Parameterized.Parameter(0)
    public boolean mIsSipFeatureEnabled;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                        {true}, {false}
                }
        );
    }

    @Before
    public void setup() throws Exception {
        mPackageName = mContext.getPackageName();
        mService = ComponentName.createRelative(
                mPackageName, "com.test.TestPersonalizationService");
        mFileStorage = MobileDataDownloadFactory.getFileStorage(mContext);
        // Use direct executor to keep all work sequential for the tests
        ListeningExecutorService executorService = MoreExecutors.newDirectExecutorService();
        mMdd = MobileDataDownloadFactory.getMdd(mContext, executorService, executorService);
        mPopulator = new OnDevicePersonalizationFileGroupPopulator(mContext);
        RemoveFileGroupsByFilterRequest request =
                RemoveFileGroupsByFilterRequest.newBuilder().build();
        MobileDataDownloadFactory.getMdd(mContext).removeFileGroupsByFilter(request).get();

        // Initialize the DB as a test instance
        OnDevicePersonalizationVendorDataDao.getInstanceForTest(mContext, mService,
                PackageUtils.getCertDigest(mContext, mPackageName));

        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        ShellUtils.runShellCommand("settings put global hidden_api_policy 1");
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "shared_isolated_process_feature_enabled "
                        + mIsSipFeatureEnabled);

        mLatch = new CountDownLatch(1);
        mTestCallback = new FutureCallback<DownloadCompletedOutputParcel>() {
            @Override
            public void onSuccess(DownloadCompletedOutputParcel result) {
                mCallbackSuccess = true;
                mLatch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
                mCallbackFailure = true;
                mLatch.countDown();
            }
        };
    }

    @Test
    public void testRun() throws Exception {
        OnDevicePersonalizationVendorDataDao dao =
                OnDevicePersonalizationVendorDataDao.getInstanceForTest(mContext, mService,
                        PackageUtils.getCertDigest(mContext, mPackageName));
        var originalIsolatedServiceAllowList =
                FlagsFactory.getFlags().getIsolatedServiceAllowList();
        PhFlagsTestUtil.setIsolatedServiceAllowList(
                "com.android.ondevicepersonalization.servicetests");
        mPopulator.refreshFileGroups(mMdd).get();
        PhFlagsTestUtil.setIsolatedServiceAllowList(originalIsolatedServiceAllowList);
        String fileGroupName = OnDevicePersonalizationFileGroupPopulator.createPackageFileGroupName(
                mPackageName, mContext);
        // Trigger the download immediately.
        mMdd.downloadFileGroup(
                DownloadFileGroupRequest.newBuilder().setGroupName(fileGroupName).build()).get();

        List<VendorData> existingData = new ArrayList<>();
        existingData.add(mContentExtra);
        List<String> retain = new ArrayList<>();
        retain.add("keyExtra");
        assertTrue(dao.batchUpdateOrInsertVendorDataTransaction(existingData, retain,
                100));

        OnDevicePersonalizationDataProcessingAsyncCallable callable =
                new OnDevicePersonalizationDataProcessingAsyncCallable(
                        mPackageName, mContext, new TestInjector());

        callable.call();
        mLatch.await();

        Cursor cursor = dao.readAllVendorData();
        List<VendorData> vendorDataList = new ArrayList<>();
        while (cursor.moveToNext()) {
            String key = cursor.getString(
                    cursor.getColumnIndexOrThrow(VendorDataContract.VendorDataEntry.KEY));

            byte[] data = cursor.getBlob(
                    cursor.getColumnIndexOrThrow(VendorDataContract.VendorDataEntry.DATA));

            vendorDataList.add(new VendorData.Builder()
                    .setKey(key)
                    .setData(data)
                    .build());
        }
        cursor.close();
        assertEquals(3, vendorDataList.size());
        for (VendorData data : vendorDataList) {
            if (data.getKey().equals(mContent1.getKey())) {
                compareDataContent(mContent1, data, false);
            } else if (data.getKey().equals(mContent2.getKey())) {
                compareDataContent(mContent2, data, true);
            } else if (data.getKey().equals(mContentExtra.getKey())) {
                compareDataContent(mContentExtra, data, false);
            } else {
                fail("Vendor data from DB contains unexpected key");
            }
        }
    }

    @Test
    public void testRunOldDataDownloaded() throws Exception {
        OnDevicePersonalizationVendorDataDao dao =
                OnDevicePersonalizationVendorDataDao.getInstanceForTest(mContext, mService,
                        PackageUtils.getCertDigest(mContext, mPackageName));
        var originalIsolatedServiceAllowList =
                FlagsFactory.getFlags().getIsolatedServiceAllowList();
        PhFlagsTestUtil.setIsolatedServiceAllowList(
                "com.android.ondevicepersonalization.servicetests");
        mPopulator.refreshFileGroups(mMdd).get();
        PhFlagsTestUtil.setIsolatedServiceAllowList(originalIsolatedServiceAllowList);
        String fileGroupName = OnDevicePersonalizationFileGroupPopulator.createPackageFileGroupName(
                mPackageName, mContext);
        // Trigger the download immediately.
        mMdd.downloadFileGroup(
                DownloadFileGroupRequest.newBuilder().setGroupName(fileGroupName).build()).get();

        List<VendorData> existingData = new ArrayList<>();
        existingData.add(mContentExtra);
        List<String> retain = new ArrayList<>();
        retain.add("keyExtra");
        assertTrue(dao.batchUpdateOrInsertVendorDataTransaction(existingData, retain,
                System.currentTimeMillis()));

        OnDevicePersonalizationDataProcessingAsyncCallable callable =
                new OnDevicePersonalizationDataProcessingAsyncCallable(
                        mPackageName, mContext, new TestInjector());

        callable.call();
        mLatch.await();

        Cursor cursor = dao.readAllVendorData();
        List<VendorData> vendorDataList = new ArrayList<>();
        while (cursor.moveToNext()) {
            String key = cursor.getString(
                    cursor.getColumnIndexOrThrow(VendorDataContract.VendorDataEntry.KEY));

            byte[] data = cursor.getBlob(
                    cursor.getColumnIndexOrThrow(VendorDataContract.VendorDataEntry.DATA));

            vendorDataList.add(new VendorData.Builder()
                    .setKey(key)
                    .setData(data)
                    .build());
        }
        cursor.close();
        assertEquals(1, vendorDataList.size());
        for (VendorData data : vendorDataList) {
            if (data.getKey().equals(mContentExtra.getKey())) {
                compareDataContent(mContentExtra, data, false);
            } else {
                fail("Vendor data from DB contains unexpected key");
            }
        }
    }

    class TestInjector extends OnDevicePersonalizationDataProcessingAsyncCallable.Injector {
        @Override
        FutureCallback<DownloadCompletedOutputParcel> getFutureCallback(
                SettableFuture<Boolean> settableFuture) {
            return mTestCallback;
        }
    }

    private void compareDataContent(VendorData expectedData, VendorData actualData,
            boolean base64) {
        assertEquals(expectedData.getKey(), actualData.getKey());
        if (base64) {
            assertArrayEquals(Base64.getDecoder().decode(expectedData.getData()),
                    actualData.getData());
        } else {
            assertArrayEquals(expectedData.getData(), actualData.getData());
        }
    }

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }
}
