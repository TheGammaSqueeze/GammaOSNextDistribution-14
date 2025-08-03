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

package com.android.server.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.testutils.FakeSdkSandboxManagerLocal;
import android.app.sdksandbox.testutils.SdkSandboxStorageManagerUtility;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.os.FileUtils;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.pm.PackageManagerLocal;
import com.android.server.sdksandbox.SdkSandboxStorageManager.StorageDirInfo;
import com.android.server.sdksandbox.SdkSandboxStorageManager.SubDirectories;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Unit tests for {@link SdkSandboxStorageManager}. */
public class SdkSandboxStorageManagerUnitTest {

    /** Test directory on Android where all storage will be created */
    private static final int CLIENT_UID = 11000;

    private static final String CLIENT_PKG_NAME = "client";
    private static final String SDK_NAME = "sdk";
    private static final String SDK2_NAME = "sdk2";
    private static final String STORAGE_UUID = "41217664-9172-527a-b3d5-edabb50a7d69";
    private static final int USER_ID = 0;

    // Use the test app's private storage as mount point for sdk storage testing
    private SdkSandboxStorageManager mSdkSandboxStorageManager;
    // Separate location where all of the sdk storage directories will be created for testing
    private String mTestDir;
    private FakeSdkSandboxManagerLocal mSdkSandboxManagerLocal;
    private PackageManager mPmMock;
    private Context mSpyContext;
    private SdkSandboxStorageManagerUtility mSdkSandboxStorageManagerUtility;

    @Before
    public void setup() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mTestDir = context.getDir("test_dir", Context.MODE_PRIVATE).getPath();
        mSpyContext = Mockito.spy(context);

        mPmMock = Mockito.mock(PackageManager.class);
        Mockito.doReturn(mPmMock).when(mSpyContext).getPackageManager();
        Mockito.doReturn(mSpyContext)
                .when(mSpyContext)
                .createContextAsUser(Mockito.any(UserHandle.class), Mockito.anyInt());

        PackageManagerLocal packageManagerLocal = Mockito.mock(PackageManagerLocal.class);

        mSdkSandboxManagerLocal = new FakeSdkSandboxManagerLocal();
        mSdkSandboxStorageManager =
                new SdkSandboxStorageManager(
                        mSpyContext, mSdkSandboxManagerLocal, packageManagerLocal, mTestDir);
        mSdkSandboxStorageManagerUtility =
                new SdkSandboxStorageManagerUtility(mSdkSandboxStorageManager);
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteContents(new File(mTestDir));
    }

    @Test
    public void test_GetSdkDataPackageDirectory() throws Exception {
        assertThat(mSdkSandboxStorageManager.getSdkDataPackageDirectory(null, USER_ID, "foo", true))
                .isEqualTo(mTestDir + "/data/misc_ce/0/sdksandbox/foo");
        // Build DE path
        assertThat(
                        mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                                null, USER_ID, "foo", false))
                .isEqualTo(mTestDir + "/data/misc_de/0/sdksandbox/foo");
        // Build with different package name
        assertThat(mSdkSandboxStorageManager.getSdkDataPackageDirectory(null, USER_ID, "bar", true))
                .isEqualTo(mTestDir + "/data/misc_ce/0/sdksandbox/bar");
        // Build with different user
        assertThat(mSdkSandboxStorageManager.getSdkDataPackageDirectory(null, 10, "foo", true))
                .isEqualTo(mTestDir + "/data/misc_ce/10/sdksandbox/foo");
        // Build with different volume
        assertThat(
                        mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                                "hello", USER_ID, "foo", true))
                .isEqualTo(mTestDir + "/mnt/expand/hello/misc_ce/0/sdksandbox/foo");
    }

    @Test
    public void test_StorageDirInfo_GetterApis() throws Exception {
        final StorageDirInfo sdkInfo = new StorageDirInfo("foo", "bar");
        assertThat(sdkInfo.getCeDataDir()).isEqualTo("foo");
        assertThat(sdkInfo.getDeDataDir()).isEqualTo("bar");
    }

    @Test
    public void test_getSdkStorageDirInfo_nonExistingStorage() throws Exception {
        // Call getSdkStorageDirInfo on SdkStorageManager
        final CallingInfo callingInfo = new CallingInfo(CLIENT_UID, CLIENT_PKG_NAME);
        final StorageDirInfo sdkInfo =
                mSdkSandboxStorageManager.getSdkStorageDirInfo(callingInfo, SDK_NAME);

        assertThat(sdkInfo.getCeDataDir()).isNull();
        assertThat(sdkInfo.getDeDataDir()).isNull();
    }

    @Test
    public void test_getSdkStorageDirInfo_storageExists() throws Exception {
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                USER_ID, CLIENT_PKG_NAME, Arrays.asList(SDK_NAME), Collections.emptyList());

        final ApplicationInfo info = new ApplicationInfo();
        info.storageUuid = UUID.fromString(STORAGE_UUID);
        Mockito.doReturn(info)
                .when(mPmMock)
                .getApplicationInfo(Mockito.any(String.class), Mockito.anyInt());

        // Call getSdkStorageDirInfo on SdkStorageManager
        final CallingInfo callingInfo = new CallingInfo(CLIENT_UID, CLIENT_PKG_NAME);
        final StorageDirInfo sdkInfo =
                mSdkSandboxStorageManager.getSdkStorageDirInfo(callingInfo, SDK_NAME);

        assertThat(sdkInfo.getCeDataDir())
                .isEqualTo(mTestDir + "/data/misc_ce/0/sdksandbox/client/sdk@sdk");
        assertThat(sdkInfo.getDeDataDir())
                .isEqualTo(mTestDir + "/data/misc_de/0/sdksandbox/client/sdk@sdk");
    }

    @Test
    public void test_getInernalStorageDirInfo_nonExistingStorage() throws Exception {
        final CallingInfo callingInfo = new CallingInfo(CLIENT_UID, CLIENT_PKG_NAME);
        final StorageDirInfo dirInfo =
                mSdkSandboxStorageManager.getInternalStorageDirInfo(callingInfo, SDK_NAME);

        assertThat(dirInfo.getCeDataDir()).isNull();
        assertThat(dirInfo.getDeDataDir()).isNull();
    }

    @Test
    public void test_getInternalStorageDirInfo_storageExists() throws Exception {
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                USER_ID,
                CLIENT_PKG_NAME,
                Collections.emptyList(),
                Arrays.asList(SubDirectories.SANDBOX_DIR));

        final ApplicationInfo info = new ApplicationInfo();
        info.storageUuid = UUID.fromString(STORAGE_UUID);
        Mockito.doReturn(info)
                .when(mPmMock)
                .getApplicationInfo(Mockito.any(String.class), Mockito.anyInt());

        final CallingInfo callingInfo = new CallingInfo(CLIENT_UID, CLIENT_PKG_NAME);
        final StorageDirInfo internalSubDirInfo =
                mSdkSandboxStorageManager.getInternalStorageDirInfo(
                        callingInfo, SubDirectories.SANDBOX_DIR);

        assertThat(internalSubDirInfo.getCeDataDir())
                .isEqualTo(mTestDir + "/data/misc_ce/0/sdksandbox/client/sandbox#sandbox");
        assertThat(internalSubDirInfo.getDeDataDir())
                .isEqualTo(mTestDir + "/data/misc_de/0/sdksandbox/client/sandbox#sandbox");
    }

    @Test
    public void test_getMountedVolumes_newVolumeExists() throws Exception {
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                /*volumeUuid=*/ null,
                USER_ID,
                CLIENT_PKG_NAME,
                Arrays.asList(SDK_NAME),
                Collections.emptyList());
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                "newVolume",
                /*userId=*/ 0,
                CLIENT_PKG_NAME,
                Arrays.asList(SDK_NAME),
                Collections.emptyList());

        final List<String> mountedVolumes = mSdkSandboxStorageManager.getMountedVolumes();

        assertThat(mountedVolumes).containsExactly(null, "newVolume");
    }

    @Test
    public void test_GetMountedVolumes_NewVolumeDoesNotExist() throws Exception {
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                /*volumeUuid=*/ null,
                USER_ID,
                CLIENT_PKG_NAME,
                Arrays.asList(SDK_NAME),
                Collections.emptyList());

        final List<String> mountedVolumes = mSdkSandboxStorageManager.getMountedVolumes();

        assertThat(mountedVolumes).containsExactly((String) null);
    }

    @Test
    public void test_onUserUnlocking_Instrumentation_NoSdk_PackageDirNotRemoved() throws Exception {
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                /*volumeUuid=*/ null,
                USER_ID,
                CLIENT_PKG_NAME,
                Collections.emptyList(),
                Collections.emptyList());

        // Set instrumentation started, so that isInstrumentationRunning will return true
        mSdkSandboxManagerLocal.notifyInstrumentationStarted(CLIENT_PKG_NAME, CLIENT_UID);

        mSdkSandboxStorageManager.onUserUnlocking(0);

        final Path ceDataPackageDirectory =
                Paths.get(
                        mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                                null, USER_ID, CLIENT_PKG_NAME, true));

        final Path deDataPackageDirectory =
                Paths.get(
                        mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                                null, USER_ID, CLIENT_PKG_NAME, false));

        assertThat(Files.exists(ceDataPackageDirectory)).isTrue();
        assertThat(Files.exists(deDataPackageDirectory)).isTrue();
    }

    @Test
    public void test_onUserUnlocking_NoInstrumentation_NoSdk_PackageDirRemoved() throws Exception {
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                /*volumeUuid=*/ null,
                USER_ID,
                CLIENT_PKG_NAME,
                Collections.emptyList(),
                Collections.emptyList());

        mSdkSandboxStorageManager.onUserUnlocking(USER_ID);

        final Path ceDataPackageDirectory =
                Paths.get(
                        mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                                null, USER_ID, CLIENT_PKG_NAME, true));

        final Path deDataPackageDirectory =
                Paths.get(
                        mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                                null, USER_ID, CLIENT_PKG_NAME, false));

        assertThat(Files.exists(ceDataPackageDirectory)).isFalse();
        assertThat(Files.exists(deDataPackageDirectory)).isFalse();
    }

    @Test
    public void test_SdkSubDirectories_NonExistingParentPath() throws Exception {
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                USER_ID, CLIENT_PKG_NAME, Arrays.asList(SDK_NAME), Collections.emptyList());

        final SubDirectories subDirs = new SubDirectories("/does/not/exist");

        assertThat(subDirs.getSdkSubDir("does.not.exist")).isNull();
        assertThat(subDirs.getSdkSubDir(SDK_NAME)).isNull();
    }

    @Test
    public void test_SdkSubDirectories_GetSdkSubDir() throws Exception {
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                USER_ID, CLIENT_PKG_NAME, Arrays.asList(SDK_NAME), Collections.emptyList());

        final String packageDir =
                mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                        /*volumeUuid=*/ null, USER_ID, CLIENT_PKG_NAME, /*isCeData=*/ true);

        final SubDirectories subDirs = new SubDirectories(packageDir);

        assertThat(subDirs.getSdkSubDir("does.not.exist")).isNull();
        assertThat(subDirs.getSdkSubDir("does.not.exist", /*fullPath=*/ true)).isNull();

        final String expectedSubDirName = SDK_NAME + "@" + SDK_NAME;
        assertThat(subDirs.getSdkSubDir(SDK_NAME)).isEqualTo(expectedSubDirName);
        final String expectedFullPath = Paths.get(packageDir, expectedSubDirName).toString();
        assertThat(subDirs.getSdkSubDir(SDK_NAME, /*fullPath=*/ true)).isEqualTo(expectedFullPath);
    }

    @Test
    public void test_SdkSubDirectories_IsValid() throws Exception {
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                USER_ID,
                CLIENT_PKG_NAME,
                Arrays.asList(SDK_NAME, SDK2_NAME),
                Arrays.asList(SubDirectories.SHARED_DIR, SubDirectories.SANDBOX_DIR));

        final String packageDir =
                mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                        /*volumeUuid=*/ null, USER_ID, CLIENT_PKG_NAME, /*isCeData=*/ true);

        final SubDirectories subDirs = new SubDirectories(packageDir);

        assertThat(subDirs.isValid(Collections.emptySet())).isFalse();
        assertThat(subDirs.isValid(Set.of(SDK_NAME))).isFalse();
        assertThat(subDirs.isValid(Set.of(SDK_NAME, SDK2_NAME))).isTrue();
    }

    @Test
    public void test_SdkSubDirectories_IsValid_MissingNonSdkStorage() throws Exception {
        // Avoid creating "shared" storage
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                USER_ID, CLIENT_PKG_NAME, Arrays.asList(SDK_NAME), Collections.emptyList());

        final String packageDir =
                mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                        /*volumeUuid=*/ null, USER_ID, CLIENT_PKG_NAME, /*isCeData=*/ true);

        final SubDirectories subDirs = new SubDirectories(packageDir);

        assertThat(subDirs.isValid(Set.of(SDK_NAME))).isFalse();
    }

    @Test
    public void test_SdkSubDirectories_IsValid_HasUnknownSubDir() throws Exception {
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                USER_ID,
                CLIENT_PKG_NAME,
                Arrays.asList(SDK_NAME),
                Arrays.asList(SubDirectories.SHARED_DIR));

        // Create a random subdir not following our format
        final String packageDir =
                mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                        /*volumeUuid=*/ null, USER_ID, CLIENT_PKG_NAME, /*isCeData=*/ true);
        Path invalidSubDir = Paths.get(packageDir, "invalid");
        Files.createDirectories(invalidSubDir);

        final SubDirectories subDirs = new SubDirectories(packageDir);

        assertThat(subDirs.isValid(Set.of(SDK_NAME))).isFalse();
    }

    @Test
    public void test_SdkSubDirectories_GenerateSubDirNames_InternalOnly_NonExisting()
            throws Exception {
        final SubDirectories subDirs = new SubDirectories("does.not.exist");

        final List<String> internalSubDirs = subDirs.generateSubDirNames(Collections.emptyList());
        assertThat(internalSubDirs).hasSize(SubDirectories.INTERNAL_SUBDIRS.size());
        assertThat(internalSubDirs).contains(SubDirectories.SHARED_DIR);
        for (String subDir : internalSubDirs) {
            if (subDir.equals(SubDirectories.SHARED_DIR)) continue;
            final String[] tokens = subDir.split("#");
            assertThat(tokens).asList().hasSize(2);
            assertThat(SubDirectories.INTERNAL_SUBDIRS).contains(tokens[0]);
        }
    }

    @Test
    public void test_SdkSubDirectories_GenerateSubDirNames_InternalOnly_Existing()
            throws Exception {
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                USER_ID,
                CLIENT_PKG_NAME,
                Collections.emptyList(),
                Arrays.asList(SubDirectories.SHARED_DIR, SubDirectories.SANDBOX_DIR));

        final String packageDir =
                mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                        /*volumeUuid=*/ null, USER_ID, CLIENT_PKG_NAME, /*isCeData=*/ true);
        final SubDirectories subDirs = new SubDirectories(packageDir);

        final List<String> internalSubDirs = subDirs.generateSubDirNames(Collections.emptyList());
        final String expectedSandboxDirName =
                SubDirectories.SANDBOX_DIR + "#" + SubDirectories.SANDBOX_DIR;
        assertThat(internalSubDirs).containsExactly("shared", expectedSandboxDirName);
    }

    @Test
    public void test_SdkSubDirectories_GenerateSubDirNames_WithSdkNames() throws Exception {
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                USER_ID,
                CLIENT_PKG_NAME,
                Arrays.asList(SDK_NAME),
                Arrays.asList(SubDirectories.SHARED_DIR, SubDirectories.SANDBOX_DIR));

        final String packageDir =
                mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                        /*volumeUuid=*/ null, USER_ID, CLIENT_PKG_NAME, /*isCeData=*/ true);
        final SubDirectories subDirs = new SubDirectories(packageDir);

        final List<String> allSubDirNames =
                subDirs.generateSubDirNames(Arrays.asList(SDK_NAME, "foo"));
        assertThat(allSubDirNames).hasSize(2 + SubDirectories.INTERNAL_SUBDIRS.size());

        // Assert internal directories
        final String expectedSandboxDirName =
                SubDirectories.SANDBOX_DIR + "#" + SubDirectories.SANDBOX_DIR;
        assertThat(allSubDirNames).containsAtLeast("shared", expectedSandboxDirName);

        // Assert per-sdk directories
        assertThat(allSubDirNames).contains(SDK_NAME + "@" + SDK_NAME);
        boolean foundFoo =
                allSubDirNames.stream()
                        .anyMatch(s -> s.contains("@") && s.split("@")[0].equals("foo"));
        assertThat(foundFoo).isTrue();
    }

    @Test
    public void test_getSdkStorageDirInfo() throws Exception {
        final List<String> sdkNames = Arrays.asList(SDK_NAME);
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                USER_ID, CLIENT_PKG_NAME, sdkNames, new ArrayList<>());

        final ApplicationInfo info = new ApplicationInfo();
        info.storageUuid = UUID.fromString(STORAGE_UUID);
        Mockito.doReturn(info)
                .when(mPmMock)
                .getApplicationInfo(Mockito.any(String.class), Mockito.anyInt());

        final CallingInfo callingInfo = new CallingInfo(CLIENT_UID, CLIENT_PKG_NAME);

        List<StorageDirInfo> sdkStorageDirInfo =
                mSdkSandboxStorageManager.getSdkStorageDirInfo(callingInfo);
        List<StorageDirInfo> expectedSdkStorageDirInfo =
                mSdkSandboxStorageManagerUtility.getSdkStorageDirInfoForTest(
                        /*volumeUuid=*/ null, USER_ID, CLIENT_PKG_NAME, sdkNames);

        assertThat(sdkStorageDirInfo).containsExactlyElementsIn(expectedSdkStorageDirInfo);
    }

    @Test
    public void test_getInternalStorageDirInfo() throws Exception {
        final List<String> nonSdkDirectories =
                Arrays.asList(SubDirectories.SHARED_DIR, SubDirectories.SANDBOX_DIR);
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                USER_ID, CLIENT_PKG_NAME, new ArrayList<>(), nonSdkDirectories);

        final ApplicationInfo info = new ApplicationInfo();
        info.storageUuid = UUID.fromString(STORAGE_UUID);
        Mockito.doReturn(info)
                .when(mPmMock)
                .getApplicationInfo(Mockito.any(String.class), Mockito.anyInt());

        final CallingInfo callingInfo = new CallingInfo(CLIENT_UID, CLIENT_PKG_NAME);

        List<StorageDirInfo> internalStorageDirInfo =
                mSdkSandboxStorageManager.getInternalStorageDirInfo(callingInfo);
        List<StorageDirInfo> expectedInternalStorageDirInfo =
                mSdkSandboxStorageManagerUtility.getInternalStorageDirInfoForTest(
                        /*volumeUuid=*/ null, USER_ID, CLIENT_PKG_NAME, nonSdkDirectories);

        assertThat(internalStorageDirInfo)
                .containsExactlyElementsIn(expectedInternalStorageDirInfo);
    }
}
