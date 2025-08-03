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

package com.android.tradefed.targetprep;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

/** Unit test for {@link ModulePusher} */
@RunWith(JUnit4.class)
public final class ModulePusherTest {
    public static final String TESTHARNESS_ENABLE = "cmd testharness enable";
    private static final String APEX_PACKAGE_NAME = "com.android.FAKE.APEX.PACKAGE.NAME";
    private static final String APK_PACKAGE_NAME = "com.android.FAKE.APK.PACKAGE.NAME";
    private static final String SPLIT_APK_PACKAGE_NAME = "com.android.SPLIT.FAKE.APK.PACKAGE.NAME";
    private static final String APEX_PRELOAD_NAME = APEX_PACKAGE_NAME + ".apex";
    private static final String APK_PRELOAD_NAME = APK_PACKAGE_NAME + ".apk";
    private static final String SPLIT_APK_PRELOAD_NAME = SPLIT_APK_PACKAGE_NAME + ".apk";
    private static final String APEX_PATH_ON_DEVICE = "/system/apex/" + APEX_PRELOAD_NAME;
    private static final String APK_PATH_ON_DEVICE = "/system/apps/" + APK_PRELOAD_NAME;
    public static final String SPLIT_APK_PACKAGE_ON_DEVICE =
            "/system/apps/com.android.SPLIT.FAKE.APK.PACKAGE.NAME";
    private static final String SPLIT_APK_PATH_ON_DEVICE =
            SPLIT_APK_PACKAGE_ON_DEVICE + "/" + SPLIT_APK_PRELOAD_NAME;
    private static final String HDPI_PATH_ON_DEVICE =
            SPLIT_APK_PACKAGE_ON_DEVICE + "/com.android.SPLIT.FAKE.APK.PACKAGE.NAME-hdpi.apk";
    private static final String TEST_APEX_NAME = "fakeApex.apex";
    private static final String TEST_APK_NAME = "fakeApk.apk";
    private static final String TEST_SPLIT_APK_NAME = "FakeSplit/base-master.apk";
    private static final String TEST_HDPI_APK_NAME = "FakeSplit/base-hdpi.apk";

    @Rule public TemporaryFolder testDir = new TemporaryFolder();
    private static final String SERIAL = "serial";
    private static final int API = 30;
    private ModulePusher mPusher;
    @Mock ITestDevice mMockDevice;
    private File mFakeApex;
    private File mFakeApk;
    private File mFakeSplitDir;
    private File mFakeSplitApk;
    private File mFakeHdpiApk;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mFakeApex = testDir.newFile(TEST_APEX_NAME);
        mFakeApk = testDir.newFile(TEST_APK_NAME);
        mFakeSplitDir = testDir.newFolder("FakeSplit");
        mFakeSplitApk = testDir.newFile(TEST_SPLIT_APK_NAME);
        mFakeHdpiApk = testDir.newFile(TEST_HDPI_APK_NAME);

        when(mMockDevice.executeAdbCommand("disable-verity")).thenReturn("disabled");
        when(mMockDevice.executeAdbCommand("remount")).thenReturn("remount succeeded");
        when(mMockDevice.getApiLevel()).thenReturn(API);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        CommandResult apexCr = getCommandResult(APEX_PRELOAD_NAME + "\n");
        when(mMockDevice.executeShellV2Command("ls /system/apex/")).thenReturn(apexCr);
        CommandResult cr = getCommandResult("Good!");
        when(mMockDevice.executeShellV2Command("pm get-moduleinfo | grep 'com.google'"))
                .thenReturn(cr);
        when(mMockDevice.executeShellV2Command("cmd testharness enable")).thenReturn(cr);
        CommandResult cr1 =
                getCommandResult("package:/system/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex\n");
        when(mMockDevice.executeShellV2Command("pm path " + APEX_PACKAGE_NAME)).thenReturn(cr1);
        CommandResult cr2 =
                getCommandResult("package:/system/apps/com.android.FAKE.APK.PACKAGE.NAME.apk\n");
        when(mMockDevice.executeShellV2Command("pm path " + APK_PACKAGE_NAME)).thenReturn(cr2);
        CommandResult cr3 =
                getCommandResult(
                        String.format(
                                "package:%s\npackage:%s\n",
                                SPLIT_APK_PATH_ON_DEVICE, HDPI_PATH_ON_DEVICE));
        when(mMockDevice.executeShellV2Command("pm path " + SPLIT_APK_PACKAGE_NAME))
                .thenReturn(cr3);
        CommandResult cr4 =
                getCommandResult(
                        "com.android.SPLIT.FAKE.APK.PACKAGE.NAME.apk\n"
                                + "com.android.SPLIT.FAKE.APK.PACKAGE.NAME-hdpi.apk\n");
        when(mMockDevice.executeShellV2Command(
                        "ls /system/apps/com.android.SPLIT.FAKE.APK.PACKAGE.NAME"))
                .thenReturn(cr4);

        mPusher =
                new ModulePusher(mMockDevice, 0, 0) {
                    @Override
                    protected ModulePusher.ModuleInfo retrieveModuleInfo(File packageFile) {
                        if (mFakeApex.equals(packageFile)) {
                            return ModuleInfo.create(APEX_PACKAGE_NAME, "2", false);
                        } else if (mFakeApk.equals(packageFile)) {
                            return ModuleInfo.create(APK_PACKAGE_NAME, "2", true);
                        } else {
                            return ModuleInfo.create(SPLIT_APK_PACKAGE_NAME, "2", true);
                        }
                    }

                    @Override
                    protected void waitForDeviceToBeResponsive(long waitTime) {}
                };
    }

    /** Test parsePackageVersionCodes get the right version codes. */
    @Test
    public void testParsePackageVersionCodes() {
        String outputs =
                "package:com.google.android.media versionCode:301800200\n"
                        + "package:com.google.android.mediaprovider versionCode:301501700\n"
                        + "package: com.google.wrong.pattern version:\n"
                        + "package:com.google.empty versionCode:\n"
                        + "package:com.google.android.media.swcodec versionCode:301700000";

        Map<String, String> versionCodes = mPusher.parsePackageVersionCodes(outputs);

        assertEquals(3, versionCodes.size());
        assertEquals("301800200", versionCodes.get("com.google.android.media"));
        assertEquals("301501700", versionCodes.get("com.google.android.mediaprovider"));
        assertEquals("301700000", versionCodes.get("com.google.android.media.swcodec"));
    }

    /** Test getting paths on device. */
    @Test
    public void testGetPathsOnDevice() throws Exception {
        String[] files = mPusher.getPathsOnDevice(mMockDevice, SPLIT_APK_PACKAGE_NAME);

        assertArrayEquals(new String[] {SPLIT_APK_PATH_ON_DEVICE, HDPI_PATH_ON_DEVICE}, files);
    }

    @Test
    public void testGetApexPathUnderSystem() throws Exception {
        CommandResult apexCr =
                getCommandResult(
                        "com.android.apex.cts.shim.apex\n"
                                + "com.android.wifi.capex\n"
                                + "com.android.appsearch.apex\n"
                                + "com.google.android.adbd_trimmed_compressed.apex\n"
                                + "com.google.android.art_compressed.apex\n"
                                + "com.google.android.media.swcodec_compressed.apex\n"
                                + "com.google.android.media_compressed.apex\n"
                                + "com.google.android.mediaprovider_compressed.apex\n"
                                + "com.google.mainline.primary.libs.apex");
        when(mMockDevice.executeShellV2Command("ls /system/apex/")).thenReturn(apexCr);

        assertEquals(
                Paths.get("/system/apex/com.android.appsearch.apex"),
                mPusher.getApexPathUnderSystem(mMockDevice, "com.android.appsearch"));
        assertEquals(
                Paths.get("/system/apex/com.google.android.media_compressed.apex"),
                mPusher.getApexPathUnderSystem(mMockDevice, "com.google.android.media"));
        assertEquals(
                Paths.get("/system/apex/com.google.android.adbd_trimmed_compressed.apex"),
                mPusher.getApexPathUnderSystem(mMockDevice, "com.google.android.adbd"));
    }

    /** Test getting preload paths for split apks. */
    @Test
    public void testGetPreLoadPathsOnSplitApk() throws Exception {
        File[] files = new File[] {mFakeSplitApk, mFakeHdpiApk};
        Path[] actual =
                new Path[] {
                    Paths.get(SPLIT_APK_PACKAGE_ON_DEVICE),
                    Paths.get(SPLIT_APK_PATH_ON_DEVICE),
                    Paths.get(HDPI_PATH_ON_DEVICE)
                };

        Path[] results = mPusher.getPreloadPaths(mMockDevice, files, SPLIT_APK_PACKAGE_NAME, 30);

        assertArrayEquals(results, actual);
    }

    /** Test getting preload paths for apex */
    @Test
    public void testGetPreLoadPathsOnApex() throws Exception {
        File[] files = new File[] {mFakeApex};
        Path[] actual = new Path[] {Paths.get(APEX_PATH_ON_DEVICE)};

        Path[] results = mPusher.getPreloadPaths(mMockDevice, files, APEX_PACKAGE_NAME, 30);

        assertArrayEquals(results, actual);
    }

    /** Test getting default path if `pm path` fails on Q */
    @Test
    public void testGetPreLoadPathsOnQReturnDefault() throws Exception {
        File[] files = new File[] {mFakeApex};
        Path[] actual = new Path[] {Paths.get(APEX_PATH_ON_DEVICE)};
        when(mMockDevice.executeShellV2Command("pm path " + APEX_PACKAGE_NAME))
                .thenReturn(getCommandResult(""));

        Path[] results = mPusher.getPreloadPaths(mMockDevice, files, APEX_PACKAGE_NAME, 29);

        assertArrayEquals(results, actual);
    }

    /** Test get preload paths throws an exception if `pm path` fails on S */
    @Test
    public void testGetPreLoadPathsOnSThrowsException() throws Exception {
        File[] files = new File[] {mFakeApex};
        when(mMockDevice.executeShellV2Command("pm path " + APEX_PACKAGE_NAME))
                .thenReturn(getCommandResult(""));

        assertThrows(
                ModulePusher.ModulePushError.class,
                () -> mPusher.getPreloadPaths(mMockDevice, files, APEX_PACKAGE_NAME, 31));
    }

    /** Test getting preload paths for non-split apk. */
    @Test
    public void testGetPreLoadPathsOnApk() throws Exception {
        File[] files = new File[] {mFakeApk};
        Path[] actual = new Path[] {Paths.get(APK_PATH_ON_DEVICE)};

        Path[] results = mPusher.getPreloadPaths(mMockDevice, files, APK_PACKAGE_NAME, 30);

        assertArrayEquals(results, actual);
    }

    /** Test getting preload paths for /data/apex/decompressed */
    @Test
    public void testGetPreLoadPathsOverridesApexDecompressedPath() throws Exception {
        File[] files = new File[] {mFakeApex};
        when(mMockDevice.executeShellV2Command("pm path " + APEX_PACKAGE_NAME))
                .thenReturn(
                        getCommandResult(
                                "package:/data/apex/decompressed/"
                                        + APEX_PACKAGE_NAME
                                        + "@310000000.decompressed.apex\n"));
        Path[] actual = new Path[] {Paths.get(APEX_PATH_ON_DEVICE)};

        Path[] results = mPusher.getPreloadPaths(mMockDevice, files, APEX_PACKAGE_NAME, 31);

        assertArrayEquals(results, actual);
    }

    /** Test install modules when there are non-split files to push. */
    @Test
    public void testInstallModulesSuccess() throws Exception {
        Path dir = mFakeApex.toPath().getParent();
        File renamedApex = dir.resolve(APEX_PRELOAD_NAME).toFile();
        File renamedApk = dir.resolve(APK_PRELOAD_NAME).toFile();
        when(mMockDevice.pushFile(renamedApex, APEX_PATH_ON_DEVICE)).thenReturn(true);
        when(mMockDevice.pushFile(renamedApk, APK_PATH_ON_DEVICE)).thenReturn(true);
        setVersionCodesOnDevice(ImmutableMap.of(APEX_PACKAGE_NAME, "2", APK_PACKAGE_NAME, "2"));
        activateVersion(2);

        mPusher.installModules(
                ImmutableMultimap.of(APEX_PACKAGE_NAME, mFakeApex, APK_PACKAGE_NAME, mFakeApk),
                /*factoryReset=*/ true,
                /*disablePackageCache=*/ false);

        verify(mMockDevice, atLeastOnce()).getActiveApexes();
        verify(mMockDevice, times(2)).reboot();
        verify(mMockDevice, times(1)).executeShellV2Command("cmd testharness enable");
        verify(mMockDevice, times(1)).pushFile(renamedApex, APEX_PATH_ON_DEVICE);
        verify(mMockDevice, times(1)).pushFile(renamedApk, APK_PATH_ON_DEVICE);
        assertFalse(Files.exists(mFakeApex.toPath()));
        assertFalse(Files.exists(mFakeApk.toPath()));
        assertTrue(Files.isRegularFile(renamedApex.toPath()));
        assertTrue(Files.isRegularFile(renamedApk.toPath()));
    }

    /** Test install modules when there are non-split files to push with reboot. */
    @Test
    public void testInstallModulesSuccessViaReboot() throws Exception {
        Path dir = mFakeApex.toPath().getParent();
        File renamedApex = dir.resolve(APEX_PRELOAD_NAME).toFile();
        File renamedApk = dir.resolve(APK_PRELOAD_NAME).toFile();
        when(mMockDevice.pushFile(renamedApex, APEX_PATH_ON_DEVICE)).thenReturn(true);
        when(mMockDevice.pushFile(renamedApk, APK_PATH_ON_DEVICE)).thenReturn(true);
        setVersionCodesOnDevice(ImmutableMap.of(APEX_PACKAGE_NAME, "2", APK_PACKAGE_NAME, "2"));
        activateVersion(2);

        mPusher.installModules(
                ImmutableMultimap.of(APEX_PACKAGE_NAME, mFakeApex, APK_PACKAGE_NAME, mFakeApk),
                /*factoryReset=*/ false,
                /*disablePackageCache=*/ false);

        verify(mMockDevice, atLeastOnce()).getActiveApexes();
        verify(mMockDevice, never()).executeShellV2Command("cmd testharness enable");
        verify(mMockDevice, times(3)).reboot();
        verify(mMockDevice, times(1)).pushFile(renamedApex, APEX_PATH_ON_DEVICE);
        verify(mMockDevice, times(1)).pushFile(renamedApk, APK_PATH_ON_DEVICE);
        assertFalse(Files.exists(mFakeApex.toPath()));
        assertFalse(Files.exists(mFakeApk.toPath()));
        assertTrue(Files.isRegularFile(renamedApex.toPath()));
        assertTrue(Files.isRegularFile(renamedApk.toPath()));
    }

    /** Test install modules when there are apks to push. */
    @Test
    public void testInstallModulesSuccessWithApks() throws Exception {
        Path dir = mFakeApex.toPath().getParent();
        File renamedApex = dir.resolve(APEX_PRELOAD_NAME).toFile();
        File renamedSplitApk = dir.resolve(SPLIT_APK_PACKAGE_NAME).toFile();
        when(mMockDevice.pushFile(renamedApex, APEX_PATH_ON_DEVICE)).thenReturn(true);
        when(mMockDevice.pushDir(renamedSplitApk, SPLIT_APK_PACKAGE_ON_DEVICE)).thenReturn(true);
        setVersionCodesOnDevice(
                ImmutableMap.of(APEX_PACKAGE_NAME, "2", SPLIT_APK_PACKAGE_NAME, "2"));
        activateVersion(2);

        mPusher.installModules(
                ImmutableMultimap.of(
                        APEX_PACKAGE_NAME,
                        mFakeApex,
                        SPLIT_APK_PACKAGE_NAME,
                        mFakeSplitApk,
                        SPLIT_APK_PACKAGE_NAME,
                        mFakeHdpiApk),
                /*factoryReset=*/ true,
                /*disablePackageCache=*/ false);

        verify(mMockDevice, atLeastOnce()).getActiveApexes();
        verify(mMockDevice, times(1)).executeShellV2Command("cmd testharness enable");
        verify(mMockDevice, times(2)).reboot();
        verify(mMockDevice, times(1)).pushFile(renamedApex, APEX_PATH_ON_DEVICE);
        verify(mMockDevice, times(1)).pushDir(renamedSplitApk, SPLIT_APK_PACKAGE_ON_DEVICE);
        assertFalse(Files.exists(mFakeApex.toPath()));
        assertFalse(Files.exists(mFakeSplitDir.toPath()));
        assertTrue(Files.isRegularFile(renamedApex.toPath()));
        assertTrue(Files.isDirectory(renamedSplitApk.toPath()));
        assertTrue(Files.isRegularFile(renamedSplitApk.toPath().resolve("base-master.apk")));
        assertTrue(Files.isRegularFile(renamedSplitApk.toPath().resolve("base-hdpi.apk")));
    }

    /** Test install modules when disable package cache. */
    @Test
    public void testInstallModulesSuccessDisablePackageCache() throws Exception {
        Path dir = mFakeApex.toPath().getParent();
        File renamedApex = dir.resolve(APEX_PRELOAD_NAME).toFile();
        File renamedApk = dir.resolve(APK_PRELOAD_NAME).toFile();
        when(mMockDevice.pushFile(renamedApex, APEX_PATH_ON_DEVICE)).thenReturn(true);
        when(mMockDevice.pushFile(renamedApk, APK_PATH_ON_DEVICE)).thenReturn(true);
        setVersionCodesOnDevice(ImmutableMap.of(APEX_PACKAGE_NAME, "2", APK_PACKAGE_NAME, "2"));
        activateVersion(2);

        mPusher.installModules(
                ImmutableMultimap.of(APEX_PACKAGE_NAME, mFakeApex, APK_PACKAGE_NAME, mFakeApk),
                /*factoryReset=*/ false,
                /*disablePackageCache=*/ true);

        verify(mMockDevice, atLeastOnce()).getActiveApexes();
        verify(mMockDevice, never()).executeShellV2Command("cmd testharness enable");
        verify(mMockDevice, times(1)).executeShellV2Command("rm -Rf /data/system/package_cache/");
        verify(mMockDevice, times(3)).reboot();
        verify(mMockDevice, times(1)).pushFile(renamedApex, APEX_PATH_ON_DEVICE);
        verify(mMockDevice, times(1)).pushFile(renamedApk, APK_PATH_ON_DEVICE);
        assertFalse(Files.exists(mFakeApex.toPath()));
        assertFalse(Files.exists(mFakeApk.toPath()));
        assertTrue(Files.isRegularFile(renamedApex.toPath()));
        assertTrue(Files.isRegularFile(renamedApk.toPath()));
    }

    /** Throws exception when missing one version code. */
    @Test
    public void testInstallModulesThrowsExceptionWhenMissingVersionCode() throws Exception {
        Path dir = mFakeApex.toPath().getParent();
        File renamedApex = dir.resolve(APEX_PRELOAD_NAME).toFile();
        File renamedApk = dir.resolve(APK_PRELOAD_NAME).toFile();
        when(mMockDevice.pushFile(renamedApex, APEX_PATH_ON_DEVICE)).thenReturn(true);
        when(mMockDevice.pushFile(renamedApk, APK_PATH_ON_DEVICE)).thenReturn(true);
        setVersionCodesOnDevice(ImmutableMap.of(APEX_PACKAGE_NAME, "2"));
        activateVersion(2);

        assertThrows(
                ModulePusher.ModulePushError.class,
                () ->
                        mPusher.installModules(
                                ImmutableMultimap.of(
                                        APEX_PACKAGE_NAME, mFakeApex, APK_PACKAGE_NAME, mFakeApk),
                                /*factoryReset=*/ false,
                                /*disablePackageCache=*/ false));
        verify(mMockDevice, atLeastOnce()).getActiveApexes();
        verify(mMockDevice, never()).executeShellV2Command("cmd testharness enable");
        verify(mMockDevice, times(2)).reboot();
        verify(mMockDevice, never()).pushFile(any(File.class), anyString());
    }

    /** Throws ModulePushError if the only file push fails. */
    @Test
    public void testInstallModulesFailureIfPushFails() throws Exception {
        Path dir = mFakeApex.toPath().getParent();
        File renamedApex = dir.resolve(APEX_PRELOAD_NAME).toFile();
        when(mMockDevice.pushFile(any(), eq(APEX_PATH_ON_DEVICE))).thenReturn(false);
        setVersionCodesOnDevice(ImmutableMap.of(APEX_PACKAGE_NAME, "2"));

        assertThrows(
                ModulePusher.ModulePushError.class,
                () ->
                        mPusher.installModules(
                                ImmutableMultimap.of(APEX_PACKAGE_NAME, mFakeApex),
                                /*factoryReset=*/ true,
                                /*disablePackageCache=*/ false));
        verify(mMockDevice, never()).executeShellV2Command(TESTHARNESS_ENABLE);
        verify(mMockDevice, times(1)).pushFile(renamedApex, APEX_PATH_ON_DEVICE);
    }

    /** Throws ModulePushError if any file push fails and there are more than one test files. */
    @Test
    public void testInstallModulesFailureIfAnyPushFails() throws Exception {
        Path dir = mFakeApex.toPath().getParent();
        File renamedApex = dir.resolve(APEX_PRELOAD_NAME).toFile();
        File renamedApk = dir.resolve(APK_PRELOAD_NAME).toFile();
        when(mMockDevice.pushFile(any(), eq(APEX_PATH_ON_DEVICE))).thenReturn(true);
        when(mMockDevice.pushFile(any(), eq(APK_PATH_ON_DEVICE))).thenReturn(false);
        setVersionCodesOnDevice(ImmutableMap.of(APEX_PACKAGE_NAME, "2", APK_PACKAGE_NAME, "2"));

        assertThrows(
                ModulePusher.ModulePushError.class,
                () ->
                        mPusher.installModules(
                                ImmutableMultimap.of(
                                        APEX_PACKAGE_NAME, mFakeApex, APK_PACKAGE_NAME, mFakeApk),
                                /*factoryReset=*/ true,
                                /*disablePackageCache=*/ false));

        verify(mMockDevice, never()).executeShellV2Command(TESTHARNESS_ENABLE);
        verify(mMockDevice, times(1)).pushFile(renamedApex, APEX_PATH_ON_DEVICE);
        verify(mMockDevice, times(1)).pushFile(renamedApk, APK_PATH_ON_DEVICE);
        assertFalse(Files.exists(mFakeApex.toPath()));
        assertFalse(Files.exists(mFakeApk.toPath()));
        assertTrue(Files.isRegularFile(renamedApex.toPath()));
        assertTrue(Files.isRegularFile(renamedApk.toPath()));
    }

    /** Throws ModulePushError if activated version code is different. */
    @Test
    public void testInstallModulesFailureIfActivationVersionCodeDifferent() throws Exception {
        when(mMockDevice.pushFile(any(), eq(APEX_PATH_ON_DEVICE))).thenReturn(true);
        setVersionCodesOnDevice(ImmutableMap.of(APEX_PACKAGE_NAME, "2"));
        activateVersion(1);

        assertThrows(
                ModulePusher.ModulePushError.class,
                () ->
                        mPusher.installModules(
                                ImmutableMultimap.of(APEX_PACKAGE_NAME, mFakeApex),
                                /*factoryReset=*/ true,
                                /*disablePackageCache=*/ false));
        verify(mMockDevice, times(1)).pushFile(any(), any());
    }

    /** Throws ModulePushError if failed to activate. */
    @Test
    public void testInstallModulesFailureIfActivationFailed() throws Exception {
        when(mMockDevice.pushFile(any(), eq(APEX_PATH_ON_DEVICE))).thenReturn(true);
        setVersionCodesOnDevice(ImmutableMap.of(APEX_PACKAGE_NAME, "2"));
        when(mMockDevice.getActiveApexes()).thenReturn(new HashSet<>());

        assertThrows(
                ModulePusher.ModulePushError.class,
                () ->
                        mPusher.installModules(
                                ImmutableMultimap.of(APEX_PACKAGE_NAME, mFakeApex),
                                /*factoryReset=*/ true,
                                /*disablePackageCache=*/ false));
        verify(mMockDevice, times(1)).pushFile(any(), any());
    }

    /** Throws ModulePushError if version code not updated. */
    @Test
    public void testInstallModulesFailureIfVersionCodeDifferent() throws Exception {
        when(mMockDevice.pushFile(any(), eq(APEX_PATH_ON_DEVICE))).thenReturn(true);
        setVersionCodesOnDevice(ImmutableMap.of(APEX_PACKAGE_NAME, "1"));
        activateVersion(2);

        assertThrows(
                ModulePusher.ModulePushError.class,
                () ->
                        mPusher.installModules(
                                ImmutableMultimap.of(APEX_PACKAGE_NAME, mFakeApex),
                                /*factoryReset=*/ true,
                                /*disablePackageCache=*/ false));
        verify(mMockDevice, times(1)).pushFile(any(), any());
    }

    private void setVersionCodesOnDevice(Map<String, String> updatedPackageCodes) throws Exception {
        StringBuilder apkSb1 = new StringBuilder();
        StringBuilder apkSb2 = new StringBuilder();
        StringBuilder apexSb1 = new StringBuilder();
        StringBuilder apexSb2 = new StringBuilder();
        for (String pack : updatedPackageCodes.keySet()) {
            String old = String.format("package:%s versionCode:%s\n", pack, "1");
            String updated =
                    String.format(
                            "package:%s versionCode:%s\n", pack, updatedPackageCodes.get(pack));
            if (pack.contains("APEX")) {
                apexSb1.append(old);
                apexSb2.append(updated);
            } else {
                apkSb1.append(old);
                apkSb2.append(updated);
            }
        }
        when(mMockDevice.executeShellV2Command(
                        "cmd package list packages --apex-only --show-versioncode| grep"
                                + " 'com.google'"))
                .thenReturn(getCommandResult(apexSb1.toString()))
                .thenReturn(getCommandResult(apexSb2.toString()));
        when(mMockDevice.executeShellV2Command(
                        "cmd package list packages --show-versioncode| grep 'com.google'"))
                .thenReturn(getCommandResult(apkSb1.toString()))
                .thenReturn(getCommandResult(apkSb2.toString()));
    }

    private void activateVersion(long versionCode) throws DeviceNotAvailableException {
        ITestDevice.ApexInfo fakeApexData =
                new ITestDevice.ApexInfo(APEX_PACKAGE_NAME, versionCode, APEX_PATH_ON_DEVICE);
        when(mMockDevice.getActiveApexes())
                .thenReturn(new HashSet<>(Collections.singletonList(fakeApexData)));
    }

    private static CommandResult getCommandResult(String output) {
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStdout(output);
        result.setExitCode(0);
        return result;
    }
}
