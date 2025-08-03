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

package android.app.sdksandbox.testutils;

import android.util.Log;

import com.android.server.sdksandbox.SdkSandboxStorageManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SdkSandboxStorageManagerUtility {

    private final SdkSandboxStorageManager mSdkSandboxStorageManager;
    private static final String TAG = "SdkSandboxStorageManagerUtility";

    public SdkSandboxStorageManagerUtility(SdkSandboxStorageManager sdkSandboxStorageManager) {
        mSdkSandboxStorageManager = sdkSandboxStorageManager;
    }

    public void createSdkStorageForTest(
            int userId,
            String clientPackageName,
            List<String> sdkNames,
            List<String> nonSdkDirectories) {
        try {
            createSdkStorageForTest(
                    /*volumeUuid=*/ null, userId, clientPackageName, sdkNames, nonSdkDirectories);
        } catch (Exception e) {
            Log.d(TAG, "Error while creating files: " + e.toString());
        }
    }

    /**
     * A helper method for create sdk storage for test purpose.
     *
     * <p>It creates <volume>/misc_ce/<userId>/sdksandbox/<packageName>/<name>[@,#]<name>
     *
     * <p>We are reusing the name of directory as random suffix for simplicity.
     */
    public void createSdkStorageForTest(
            String volumeUuid,
            int userId,
            String packageName,
            List<String> sdkNames,
            List<String> nonSdkDirectories)
            throws Exception {
        final List<SdkSandboxStorageManager.StorageDirInfo> sdkStorageInfos =
                getSdkStorageDirInfoForTest(volumeUuid, userId, packageName, sdkNames);
        final List<SdkSandboxStorageManager.StorageDirInfo> internalStorageDirInfos =
                getInternalStorageDirInfoForTest(
                        volumeUuid, userId, packageName, nonSdkDirectories);

        createPackagePath(volumeUuid, userId, packageName, /*isCeData=*/ true);
        createPackagePath(volumeUuid, userId, packageName, /*isCeData=*/ false);

        createFilesFromList(sdkStorageInfos);
        createFilesFromList(internalStorageDirInfos);
    }

    /** A helper method to get the internal storage paths for test purpose */
    public List<SdkSandboxStorageManager.StorageDirInfo> getInternalStorageDirInfoForTest(
            String volumeUuid, int userId, String packageName, List<String> nonSdkDirectories) {
        final List<SdkSandboxStorageManager.StorageDirInfo> internalStorageDirInfo =
                new ArrayList<>();
        final String cePackageDir =
                mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                        volumeUuid, userId, packageName, /*isCeData=*/ true);
        final String dePackageDir =
                mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                        volumeUuid, userId, packageName, /*isCeData=*/ false);

        for (String dir : nonSdkDirectories) {
            String path =
                    dir.equals(SdkSandboxStorageManager.SubDirectories.SHARED_DIR)
                            ? dir
                            : dir + '#' + dir;
            String sdkCeSubDirPath = cePackageDir + "/" + path;
            String sdkDeSubDirPath = dePackageDir + "/" + path;
            internalStorageDirInfo.add(
                    new SdkSandboxStorageManager.StorageDirInfo(sdkCeSubDirPath, sdkDeSubDirPath));
        }
        return internalStorageDirInfo;
    }

    /** A helper method to get the storage paths of SDKs for test purpose */
    public List<SdkSandboxStorageManager.StorageDirInfo> getSdkStorageDirInfoForTest(
            String volumeUuid, int userId, String packageName, List<String> sdkNames) {

        final List<SdkSandboxStorageManager.StorageDirInfo> sdkStorageDirInfo = new ArrayList<>();
        final String cePackageDir =
                mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                        volumeUuid, userId, packageName, /*isCeData=*/ true);
        final String dePackageDir =
                mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                        volumeUuid, userId, packageName, /*isCeData=*/ false);

        for (String sdkName : sdkNames) {
            String sdkCeSubDirPath = cePackageDir + "/" + sdkName + "@" + sdkName;
            String sdkDeSubDirPath = dePackageDir + "/" + sdkName + "@" + sdkName;
            sdkStorageDirInfo.add(
                    new SdkSandboxStorageManager.StorageDirInfo(sdkCeSubDirPath, sdkDeSubDirPath));
        }
        return sdkStorageDirInfo;
    }

    private void createFilesFromList(List<SdkSandboxStorageManager.StorageDirInfo> storageDirInfos)
            throws Exception {
        final int storageDirInfosSize = storageDirInfos.size();

        for (int i = 0; i < storageDirInfosSize; i++) {
            final Path ceSdkStoragePath = Paths.get(storageDirInfos.get(i).getCeDataDir());
            Files.createDirectories(ceSdkStoragePath);

            final Path deSdkStoragePath = Paths.get(storageDirInfos.get(i).getDeDataDir());
            Files.createDirectories(deSdkStoragePath);
        }
    }

    private void createPackagePath(
            String volumeUuid, int userId, String packageName, boolean isCeData) throws Exception {
        final String packageDir =
                mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                        volumeUuid, userId, packageName, isCeData);
        Files.createDirectories(Paths.get(packageDir));
    }
}
