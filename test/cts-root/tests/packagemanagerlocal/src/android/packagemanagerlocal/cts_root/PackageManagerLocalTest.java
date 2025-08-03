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

package android.packagemanagerlocal.cts_root;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalManagerRegistry;
import com.android.server.pm.PackageManagerLocal;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class PackageManagerLocalTest {
    private static final String TAG = "PackageManagerLocalTest";

    private PackageManagerLocal mPackageManagerLocal;

    @Before
    public void setup() {
        mPackageManagerLocal = LocalManagerRegistry.getManager(PackageManagerLocal.class);
    }

    @Test
    public void testPackageManagerLocal_ReconcileSdkData_DifferentStorageFlags() throws Exception {
        final String volumeUuid = null;
        final String packageName = "android.packagemanagerlocal.test";
        final List<String> subDirNames = Arrays.asList("one", "two@random");
        final int userId = 0;
        final int appId = 10000;
        final int previousAppId = -1;
        final String seInfo = "default";

        // There are two flags: FLAG_STORAGE_CE and FLAG_STORAGE_DE. So total of 4 combination
        // to test.
        for (int currentFlag = 0; currentFlag < 4; currentFlag++) {
            final String errorMsg = "Failed for flag: " + currentFlag;

            File cePackageDirFile = new File("/data/misc_ce/0/sdksandbox/" + packageName);
            File dePackageDirFile = new File("/data/misc_de/0/sdksandbox/" + packageName);

            try {
                mPackageManagerLocal.reconcileSdkData(volumeUuid, packageName, subDirNames, userId,
                        appId, previousAppId, seInfo, currentFlag);

                // Verify that sdk data directories have been created in the desired location
                boolean hasCeFlag = (currentFlag & PackageManagerLocal.FLAG_STORAGE_CE) != 0;
                if (hasCeFlag) {
                    assertWithMessage(errorMsg).that(cePackageDirFile.isDirectory()).isTrue();
                    assertWithMessage(errorMsg).that(
                            cePackageDirFile.list()).asList().containsExactly("one", "two@random");
                } else {
                    assertWithMessage(errorMsg).that(cePackageDirFile.exists()).isFalse();
                }

                boolean hasDeFlag = (currentFlag & PackageManagerLocal.FLAG_STORAGE_DE) != 0;
                if (hasDeFlag) {
                    assertWithMessage(errorMsg).that(dePackageDirFile.isDirectory()).isTrue();
                    assertWithMessage(errorMsg).that(
                            dePackageDirFile.list()).asList().containsExactly("one", "two@random");
                } else {
                    assertWithMessage(errorMsg).that(dePackageDirFile.exists()).isFalse();
                }
            } finally {
                // Clean up the created directories
                final List<String> emptyDir = new ArrayList<String>();
                mPackageManagerLocal.reconcileSdkData(volumeUuid, packageName, emptyDir, userId,
                        appId, previousAppId, seInfo, currentFlag);
                Files.deleteIfExists(cePackageDirFile.toPath());
                Files.deleteIfExists(dePackageDirFile.toPath());
            }
        }
    }

    @Test
    public void testPackageManagerLocal_ReconcileSdkData_Reconcile() throws Exception {
        final String volumeUuid = null;
        final String packageName = "android.packagemanagerlocal.test";
        final List<String> subDirNames = Arrays.asList("one", "two@random");
        final int userId = 0;
        final int appId = 10000;
        final int previousAppId = -1;
        final String seInfo = "default";
        final int flag = PackageManagerLocal.FLAG_STORAGE_CE;

        File cePackageDirFile = new File("/data/misc_ce/0/sdksandbox/" + packageName);

        try {
            mPackageManagerLocal.reconcileSdkData(volumeUuid, packageName, subDirNames, userId,
                    appId, previousAppId, seInfo, flag);

            // Call reconcileSdkData again, with different subDirNames
            final List<String> differentSubDirNames = Arrays.asList("three");
            mPackageManagerLocal.reconcileSdkData(volumeUuid, packageName, differentSubDirNames,
                    userId, appId, previousAppId, seInfo, flag);

            // Verify that sdk data directories have been created in the desired location
            assertThat(cePackageDirFile.isDirectory()).isTrue();
            assertThat(cePackageDirFile.list()).asList().containsExactly("three");
        } finally {
            // Clean up the created directories
            final List<String> emptyDir = new ArrayList<String>();
            mPackageManagerLocal.reconcileSdkData(volumeUuid, packageName, emptyDir, userId, appId,
                    previousAppId, seInfo, flag);
            Files.deleteIfExists(cePackageDirFile.toPath());
        }
    }
}
