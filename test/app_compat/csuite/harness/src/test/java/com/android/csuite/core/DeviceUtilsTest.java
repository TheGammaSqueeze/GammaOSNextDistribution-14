/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.csuite.core;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.service.dropbox.DropBoxManagerServiceDumpProto;

import com.android.csuite.core.DeviceUtils.DeviceTimestamp;
import com.android.csuite.core.DeviceUtils.DeviceUtilsException;
import com.android.csuite.core.DeviceUtils.DropboxEntry;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceRuntimeException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import com.google.common.jimfs.Jimfs;
import com.google.protobuf.ByteString;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public final class DeviceUtilsTest {
    private ITestDevice mDevice = Mockito.mock(ITestDevice.class);
    private IRunUtil mRunUtil = Mockito.mock(IRunUtil.class);
    private final FileSystem mFileSystem =
            Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix());
    private static final String TEST_PACKAGE_NAME = "package.name";

    @Test
    public void grantExternalStoragePermissions_commandFailed_doesNotThrow() throws Exception {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        when(mDevice.executeShellV2Command(captor.capture()))
                .thenReturn(createFailedCommandResult());
        DeviceUtils sut = createSubjectUnderTest();

        sut.grantExternalStoragePermissions(TEST_PACKAGE_NAME);

        assertThat(captor.getValue()).contains("MANAGE_EXTERNAL_STORAGE allow");
    }

    @Test
    public void isPackageInstalled_packageIsInstalled_returnsTrue() throws Exception {
        String packageName = "package.name";
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm list packages")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout("\npackage:" + packageName + "\n"));
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isPackageInstalled(packageName);

        assertTrue(res);
    }

    @Test
    public void isPackageInstalled_packageIsNotInstalled_returnsFalse() throws Exception {
        String packageName = "package.name";
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm list packages")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isPackageInstalled(packageName);

        assertFalse(res);
    }

    @Test
    public void isPackageInstalled_commandFailed_throws() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm list packages")))
                .thenReturn(createFailedCommandResult());
        DeviceUtils sut = createSubjectUnderTest();

        assertThrows(DeviceUtilsException.class, () -> sut.isPackageInstalled("package.name"));
    }

    @Test
    public void launchPackage_pmDumpFailedAndPackageDoesNotExist_throws() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("monkey")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm dump")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm list packages")))
                .thenReturn(createSuccessfulCommandResultWithStdout("no packages"));
        DeviceUtils sut = createSubjectUnderTest();

        assertThrows(DeviceUtilsException.class, () -> sut.launchPackage("package.name"));
    }

    @Test
    public void launchPackage_pmDumpFailedAndPackageExists_throws() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("monkey")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm dump")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm list packages")))
                .thenReturn(createSuccessfulCommandResultWithStdout("package:package.name"));
        DeviceUtils sut = createSubjectUnderTest();

        assertThrows(DeviceUtilsException.class, () -> sut.launchPackage("package.name"));
    }

    @Test
    public void launchPackage_amStartCommandFailed_throws() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("monkey")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm dump")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "        87f1610"
                                    + " com.google.android.gms/.app.settings.GoogleSettingsActivity"
                                    + " filter 7357509\n"
                                    + "          Action: \"android.intent.action.MAIN\"\n"
                                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                                    + "          Category:"
                                    + " \"android.intent.category.NOTIFICATION_PREFERENCES\""));
        when(mDevice.executeShellV2Command(Mockito.startsWith("am start")))
                .thenReturn(createFailedCommandResult());
        DeviceUtils sut = createSubjectUnderTest();

        assertThrows(DeviceUtilsException.class, () -> sut.launchPackage("com.google.android.gms"));
    }

    @Test
    public void launchPackage_amFailedToLaunchThePackage_throws() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("monkey")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm dump")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "        87f1610"
                                    + " com.google.android.gms/.app.settings.GoogleSettingsActivity"
                                    + " filter 7357509\n"
                                    + "          Action: \"android.intent.action.MAIN\"\n"
                                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                                    + "          Category:"
                                    + " \"android.intent.category.NOTIFICATION_PREFERENCES\""));
        when(mDevice.executeShellV2Command(Mockito.startsWith("am start")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "Error: Activity not started, unable to resolve Intent"));
        DeviceUtils sut = createSubjectUnderTest();

        assertThrows(DeviceUtilsException.class, () -> sut.launchPackage("com.google.android.gms"));
    }

    @Test
    public void launchPackage_monkeyFailedButAmSucceed_doesNotThrow() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("monkey")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm dump")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "        87f1610"
                                    + " com.google.android.gms/.app.settings.GoogleSettingsActivity"
                                    + " filter 7357509\n"
                                    + "          Action: \"android.intent.action.MAIN\"\n"
                                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                                    + "          Category:"
                                    + " \"android.intent.category.NOTIFICATION_PREFERENCES\""));
        when(mDevice.executeShellV2Command(Mockito.startsWith("am start")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        DeviceUtils sut = createSubjectUnderTest();

        sut.launchPackage("com.google.android.gms");
    }

    @Test
    public void launchPackage_monkeySucceed_doesNotThrow() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("monkey")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm dump")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("am start")))
                .thenReturn(createFailedCommandResult());
        DeviceUtils sut = createSubjectUnderTest();

        sut.launchPackage("package.name");
    }

    @Test
    public void getLaunchActivity_oneActivityIsLauncherAndMainAndDefault_returnsIt()
            throws Exception {
        String pmDump =
                "        eecc562 com.google.android.gms/.bugreport.BugreportActivity filter"
                    + " ac016f3\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                    + "        87f1610 com.google.android.gms/.app.settings.GoogleSettingsActivity"
                    + " filter 7357509\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                    + "          Category: \"android.intent.category.NOTIFICATION_PREFERENCES\"\n"
                    + "        28957f2 com.google.android.gms/.kids.SyncTailTrapperActivity filter"
                    + " 83cbcc0\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "          Category: \"android.intent.category.HOME\"\n"
                    + "          Category: \"android.intent.category.DEFAULT\"";
        DeviceUtils sut = createSubjectUnderTest();

        String res = sut.getLaunchActivity(pmDump);

        assertThat(res).isEqualTo("com.google.android.gms/.app.settings.GoogleSettingsActivity");
    }

    @Test
    public void getLaunchActivity_oneActivityIsLauncherAndMain_returnsIt() throws Exception {
        String pmDump =
                "        eecc562 com.google.android.gms/.bugreport.BugreportActivity filter"
                    + " ac016f3\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "        87f1610 com.google.android.gms/.app.settings.GoogleSettingsActivity"
                    + " filter 7357509\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                    + "          Category: \"android.intent.category.NOTIFICATION_PREFERENCES\"\n"
                    + "        28957f2 com.google.android.gms/.kids.SyncTailTrapperActivity filter"
                    + " 83cbcc0\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "          Category: \"android.intent.category.HOME\"\n"
                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                    + "          mPriority=10, mOrder=0, mHasStaticPartialTypes=false,"
                    + " mHasDynamicPartialTypes=false";
        DeviceUtils sut = createSubjectUnderTest();

        String res = sut.getLaunchActivity(pmDump);

        assertThat(res).isEqualTo("com.google.android.gms/.app.settings.GoogleSettingsActivity");
    }

    @Test
    public void
            getLaunchActivity_oneActivityIsLauncherAndOneActivityIsMain_returnsTheLauncherActivity()
                    throws Exception {
        String pmDump =
                "        eecc562 com.google.android.gms/.bugreport.BugreportActivity filter"
                    + " ac016f3\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "        87f1610 com.google.android.gms/.app.settings.GoogleSettingsActivity"
                    + " filter 7357509\n"
                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                    + "          Category: \"android.intent.category.NOTIFICATION_PREFERENCES\"\n"
                    + "        28957f2 com.google.android.gms/.kids.SyncTailTrapperActivity filter"
                    + " 83cbcc0\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "          Category: \"android.intent.category.HOME\"\n"
                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                    + "          mPriority=10, mOrder=0, mHasStaticPartialTypes=false,"
                    + " mHasDynamicPartialTypes=false";
        DeviceUtils sut = createSubjectUnderTest();

        String res = sut.getLaunchActivity(pmDump);

        assertThat(res).isEqualTo("com.google.android.gms/.app.settings.GoogleSettingsActivity");
    }

    @Test
    public void getLaunchActivity_oneActivityIsMain_returnsIt() throws Exception {
        String pmDump =
                "        eecc562 com.google.android.gms/.bugreport.BugreportActivity filter"
                    + " ac016f3\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "        87f1610 com.google.android.gms/.app.settings.GoogleSettingsActivity"
                    + " filter 7357509\n"
                    + "          Category: \"android.intent.category.NOTIFICATION_PREFERENCES\"\n"
                    + "        28957f2 com.google.android.gms/.kids.SyncTailTrapperActivity filter"
                    + " 83cbcc0\n"
                    + "          Category: \"android.intent.category.HOME\"\n"
                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                    + "          mPriority=10, mOrder=0, mHasStaticPartialTypes=false,"
                    + " mHasDynamicPartialTypes=false";
        DeviceUtils sut = createSubjectUnderTest();

        String res = sut.getLaunchActivity(pmDump);

        assertThat(res).isEqualTo("com.google.android.gms/.bugreport.BugreportActivity");
    }

    @Test
    public void getLaunchActivity_oneActivityIsLauncher_returnsIt() throws Exception {
        String pmDump =
                "        eecc562 com.google.android.gms/.bugreport.BugreportActivity filter"
                    + " ac016f3\n"
                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                    + "        87f1610 com.google.android.gms/.app.settings.GoogleSettingsActivity"
                    + " filter 7357509\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "          Category: \"android.intent.category.NOTIFICATION_PREFERENCES\"\n"
                    + "        28957f2 com.google.android.gms/.kids.SyncTailTrapperActivity filter"
                    + " 83cbcc0\n"
                    + "          Category: \"android.intent.category.HOME\"\n"
                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                    + "          mPriority=10, mOrder=0, mHasStaticPartialTypes=false,"
                    + " mHasDynamicPartialTypes=false";
        DeviceUtils sut = createSubjectUnderTest();

        String res = sut.getLaunchActivity(pmDump);

        assertThat(res).isEqualTo("com.google.android.gms/.bugreport.BugreportActivity");
    }

    @Test
    public void getLaunchActivity_noMainOrLauncherActivities_throws() throws Exception {
        String pmDump =
                "        eecc562 com.google.android.gms/.bugreport.BugreportActivity filter"
                    + " ac016f3\n"
                    + "          Category: \"android.intent.category.HOME\"\n"
                    + "        87f1610 com.google.android.gms/.app.settings.GoogleSettingsActivity"
                    + " filter 7357509\n"
                    + "          Category: \"android.intent.category.NOTIFICATION_PREFERENCES\"\n"
                    + "        28957f2 com.google.android.gms/.kids.SyncTailTrapperActivity filter"
                    + " 83cbcc0\n"
                    + "          Category: \"android.intent.category.HOME\"\n"
                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                    + "          mPriority=10, mOrder=0, mHasStaticPartialTypes=false,"
                    + " mHasDynamicPartialTypes=false";
        DeviceUtils sut = createSubjectUnderTest();

        assertThrows(DeviceUtilsException.class, () -> sut.getLaunchActivity(pmDump));
    }

    @Test
    public void currentTimeMillis_deviceCommandFailed_throwsException() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo")))
                .thenReturn(createFailedCommandResult());

        assertThrows(DeviceRuntimeException.class, () -> sut.currentTimeMillis());
    }

    @Test
    public void currentTimeMillis_unexpectedFormat_throwsException() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));

        assertThrows(DeviceRuntimeException.class, () -> sut.currentTimeMillis());
    }

    @Test
    public void currentTimeMillis_successful_returnsTime() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo")))
                .thenReturn(createSuccessfulCommandResultWithStdout("123"));

        DeviceTimestamp result = sut.currentTimeMillis();

        assertThat(result.get()).isEqualTo(Long.parseLong("123"));
    }

    @Test
    public void runWithScreenRecording_recordingDidNotStart_jobIsExecuted() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mRunUtil.runCmdInBackground(Mockito.argThat(contains("shell", "screenrecord"))))
                .thenReturn(Mockito.mock(Process.class));
        when(mDevice.executeShellV2Command(Mockito.startsWith("ls")))
                .thenReturn(createFailedCommandResult());
        AtomicBoolean executed = new AtomicBoolean(false);
        DeviceUtils.RunnableThrowingDeviceNotAvailable job = () -> executed.set(true);

        sut.runWithScreenRecording(job, (video, time) -> {});

        assertThat(executed.get()).isTrue();
    }

    @Test
    public void runWithScreenRecording_recordCommandThrowsException_jobIsExecuted()
            throws Exception {
        when(mRunUtil.runCmdInBackground(Mockito.argThat(contains("shell", "screenrecord"))))
                .thenThrow(new IOException());
        DeviceUtils sut = createSubjectUnderTest();
        AtomicBoolean executed = new AtomicBoolean(false);
        DeviceUtils.RunnableThrowingDeviceNotAvailable job = () -> executed.set(true);

        sut.runWithScreenRecording(job, (video, time) -> {});

        assertThat(executed.get()).isTrue();
    }

    @Test
    public void runWithScreenRecording_jobThrowsException_videoFileIsHandled() throws Exception {
        when(mRunUtil.runCmdInBackground(Mockito.argThat(contains("shell", "screenrecord"))))
                .thenReturn(Mockito.mock(Process.class));
        when(mDevice.executeShellV2Command(Mockito.startsWith("ls")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        DeviceUtils sut = createSubjectUnderTest();
        DeviceUtils.RunnableThrowingDeviceNotAvailable job =
                () -> {
                    throw new RuntimeException();
                };
        AtomicBoolean handled = new AtomicBoolean(false);

        assertThrows(
                RuntimeException.class,
                () -> sut.runWithScreenRecording(job, (video, time) -> handled.set(true)));

        assertThat(handled.get()).isTrue();
    }

    @Test
    public void getSdkLevel_returnsSdkLevelInteger() throws DeviceNotAvailableException {
        DeviceUtils sut = createSubjectUnderTest();
        int sdkLevel = 30;
        when(mDevice.executeShellV2Command(Mockito.eq("getprop ro.build.version.sdk")))
                .thenReturn(createSuccessfulCommandResultWithStdout("" + sdkLevel));

        int result = sut.getSdkLevel();

        assertThat(result).isEqualTo(sdkLevel);
    }

    @Test
    public void getPackageVersionName_deviceCommandFailed_returnsUnknown() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(createFailedCommandResult());

        String result = sut.getPackageVersionName("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionName_deviceCommandReturnsUnexpected_returnsUnknown()
            throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "unexpected " + DeviceUtils.VERSION_NAME_PREFIX));

        String result = sut.getPackageVersionName("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionName_deviceCommandSucceed_returnsVersionName() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                " " + DeviceUtils.VERSION_NAME_PREFIX + "123"));

        String result = sut.getPackageVersionName("any");

        assertThat(result).isEqualTo("123");
    }

    @Test
    public void getPackageVersionCode_deviceCommandFailed_returnsUnknown() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(createFailedCommandResult());

        String result = sut.getPackageVersionCode("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionCode_deviceCommandReturnsUnexpected_returnsUnknown()
            throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "unexpected " + DeviceUtils.VERSION_CODE_PREFIX));

        String result = sut.getPackageVersionCode("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionCode_deviceCommandSucceed_returnVersionCode() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                " " + DeviceUtils.VERSION_CODE_PREFIX + "123"));

        String result = sut.getPackageVersionCode("any");

        assertThat(result).isEqualTo("123");
    }

    @Test
    public void isDropboxEntryFromPackageProcess_cmdlineMatched_returnsTrue() throws Exception {
        String dropboxEntryData = "Cmd line: com.app.package";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_processMatched_returnsTrue() throws Exception {
        String dropboxEntryData = "Process: com.app.package";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_processMatchedInLines_returnsTrue()
            throws Exception {
        String dropboxEntryData = "line\nProcess: com.app.package\nline";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_processNameFollowedByOtherChar_returnsTrue()
            throws Exception {
        String dropboxEntryData = "line\nProcess: com.app.package, (time)\nline";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_processNameFollowedByDot_returnsFalse()
            throws Exception {
        String dropboxEntryData = "line\nProcess: com.app.package.sub, (time)\nline";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_processNameFollowedByColon_returnsTrue()
            throws Exception {
        String dropboxEntryData = "line\nProcess: com.app.package:sub, (time)\nline";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_processNameFollowedByUnderscore_returnsFalse()
            throws Exception {
        String dropboxEntryData = "line\nProcess: com.app.package_sub, (time)\nline";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_doesNotContainPackageName_returnsFalse()
            throws Exception {
        String dropboxEntryData = "line\n";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_packageNameWithUnderscorePrefix_returnsFalse()
            throws Exception {
        String dropboxEntryData = "line\na_com.app.package\n";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_packageNameWithUnderscorePostfix_returnsFalse()
            throws Exception {
        String dropboxEntryData = "line\ncom.app.package_a\n";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_packageNameWithDotPrefix_returnsFalse()
            throws Exception {
        String dropboxEntryData = "line\na.com.app.package\n";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_packageNameWithDotPostfix_returnsFalse()
            throws Exception {
        String dropboxEntryData = "line\ncom.app.package.a\n";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_packageNameWithColonPostfix_returnsTrue()
            throws Exception {
        String dropboxEntryData = "line\ncom.app.package:a\n";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void
            isDropboxEntryFromPackageProcess_packageNameWithAcceptiblePrefixAndPostfix_returnsTrue()
                    throws Exception {
        String dropboxEntryData = "line\ncom.app.package)\n";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void
            isDropboxEntryFromPackageProcess_wrongProcessNameWithCorrectPackageName_returnsFalse()
                    throws Exception {
        String dropboxEntryData = "line\nProcess: com.app.package_other\ncom.app.package";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isFalse();
    }

    @Test
    public void isDropboxEntryFromPackageProcess_MultipleProcessNamesWithOneMatching_returnsTrue()
            throws Exception {
        String dropboxEntryData =
                "line\n"
                        + "Process: com.app.package_other\n"
                        + "Process: com.app.package\n"
                        + "Process: com.other";
        String packageName = "com.app.package";
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isDropboxEntryFromPackageProcess(dropboxEntryData, packageName);

        assertThat(res).isTrue();
    }

    @Test
    public void getDropboxEntries_containsEntriesOutsideTimeRange_onlyReturnsNewEntries()
            throws Exception {
        DeviceUtils sut = Mockito.spy(createSubjectUnderTest());
        DeviceTimestamp startTime = new DeviceTimestamp(1);
        DeviceTimestamp endTime = new DeviceTimestamp(3);
        Mockito.doAnswer(
                        inv ->
                                List.of(
                                        new DeviceUtils.DropboxEntry(
                                                0,
                                                DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                        .toArray(
                                                                new String
                                                                        [DeviceUtils
                                                                                .DROPBOX_APP_CRASH_TAGS
                                                                                .size()])[0],
                                                TEST_PACKAGE_NAME + " entry1"),
                                        new DeviceUtils.DropboxEntry(
                                                2,
                                                DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                        .toArray(
                                                                new String
                                                                        [DeviceUtils
                                                                                .DROPBOX_APP_CRASH_TAGS
                                                                                .size()])[0],
                                                TEST_PACKAGE_NAME + " entry2"),
                                        new DeviceUtils.DropboxEntry(
                                                100,
                                                DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                        .toArray(
                                                                new String
                                                                        [DeviceUtils
                                                                                .DROPBOX_APP_CRASH_TAGS
                                                                                .size()])[0],
                                                TEST_PACKAGE_NAME + " entry3")))
                .when(sut)
                .getDropboxEntries(DeviceUtils.DROPBOX_APP_CRASH_TAGS);

        String result =
                sut
                        .getDropboxEntries(
                                DeviceUtils.DROPBOX_APP_CRASH_TAGS,
                                TEST_PACKAGE_NAME,
                                startTime,
                                endTime)
                        .stream()
                        .map(DropboxEntry::toString)
                        .collect(Collectors.joining("\n"));

        assertThat(result).doesNotContain("entry1");
        assertThat(result).contains("entry2");
        assertThat(result).doesNotContain("entry3");
    }

    @Test
    public void getDropboxEntries_containsOtherProcessEntries_onlyReturnsPackageEntries()
            throws Exception {
        DeviceUtils sut = Mockito.spy(createSubjectUnderTest());
        DeviceTimestamp startTime = new DeviceTimestamp(1);
        Mockito.doAnswer(
                        inv ->
                                List.of(
                                        new DeviceUtils.DropboxEntry(
                                                2,
                                                DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                        .toArray(
                                                                new String
                                                                        [DeviceUtils
                                                                                .DROPBOX_APP_CRASH_TAGS
                                                                                .size()])[0],
                                                "other.package" + " entry1"),
                                        new DeviceUtils.DropboxEntry(
                                                2,
                                                DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                        .toArray(
                                                                new String
                                                                        [DeviceUtils
                                                                                .DROPBOX_APP_CRASH_TAGS
                                                                                .size()])[0],
                                                TEST_PACKAGE_NAME + " entry2")))
                .when(sut)
                .getDropboxEntries(DeviceUtils.DROPBOX_APP_CRASH_TAGS);

        String result =
                sut
                        .getDropboxEntries(
                                DeviceUtils.DROPBOX_APP_CRASH_TAGS,
                                TEST_PACKAGE_NAME,
                                startTime,
                                null)
                        .stream()
                        .map(DropboxEntry::toString)
                        .collect(Collectors.joining("\n"));

        assertThat(result).doesNotContain("entry1");
        assertThat(result).contains("entry2");
    }

    @Test
    public void getDropboxEntries_noEntries_returnsEmptyList() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("dumpsys dropbox --proto")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));

        List<DropboxEntry> result = sut.getDropboxEntries(Set.of(""));

        assertThat(result).isEmpty();
    }

    @Test
    public void getDropboxEntries_entryExists_returnsEntry() throws Exception {
        Path dumpFile = Files.createTempFile(mFileSystem.getPath("/"), "dropbox", ".proto");
        long time = 123;
        String data = "abc";
        String tag = "tag";
        DropBoxManagerServiceDumpProto proto =
                DropBoxManagerServiceDumpProto.newBuilder()
                        .addEntries(
                                DropBoxManagerServiceDumpProto.Entry.newBuilder()
                                        .setTimeMs(time)
                                        .setData(ByteString.copyFromUtf8(data)))
                        .build();
        Files.write(dumpFile, proto.toByteArray());
        DeviceUtils sut = createSubjectUnderTestWithTempFile(dumpFile);
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("dumpsys dropbox --proto")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));

        List<DropboxEntry> result = sut.getDropboxEntries(Set.of(tag));

        assertThat(result.get(0).getTime()).isEqualTo(time);
        assertThat(result.get(0).getData()).isEqualTo(data);
        assertThat(result.get(0).getTag()).isEqualTo(tag);
    }

    @Test
    public void getDropboxEntriesFromStdout_entryExists_returnsEntry() throws Exception {
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("dumpsys dropbox --file")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("dumpsys dropbox --print")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        Path fileDumpFile = Files.createTempFile(mFileSystem.getPath("/"), "file", ".dump");
        Path printDumpFile = Files.createTempFile(mFileSystem.getPath("/"), "print", ".dump");
        String fileResult =
                "Drop box contents: 351 entries\n"
                        + "Max entries: 1000\n"
                        + "Low priority rate limit period: 2000 ms\n"
                        + "Low priority tags: {data_app_wtf, keymaster, system_server_wtf,"
                        + " system_app_strictmode, system_app_wtf, system_server_strictmode,"
                        + " data_app_strictmode, netstats}\n"
                        + "\n"
                        + "2022-09-05 04:17:21 system_server_wtf (text, 1730 bytes)\n"
                        + "    /data/system/dropbox/system_server_wtf@1662351441269.txt\n"
                        + "2022-09-05 04:31:06 event_data (text, 39 bytes)\n"
                        + "    /data/system/dropbox/event_data@1662352266197.txt\n";
        String printResult =
                "Drop box contents: 351 entries\n"
                    + "Max entries: 1000\n"
                    + "Low priority rate limit period: 2000 ms\n"
                    + "Low priority tags: {data_app_wtf, keymaster, system_server_wtf,"
                    + " system_app_strictmode, system_app_wtf, system_server_strictmode,"
                    + " data_app_strictmode, netstats}\n"
                    + "\n"
                    + "========================================\n"
                    + "2022-09-05 04:17:21 system_server_wtf (text, 1730 bytes)\n"
                    + "Process: system_server\n"
                    + "Subject: ActivityManager\n"
                    + "Build:"
                    + " generic/cf_x86_64_phone/vsoc_x86_64:UpsideDownCake/MASTER/8990215:userdebug/dev-keys\n"
                    + "Dropped-Count: 0\n"
                    + "\n"
                    + "android.util.Log$TerribleFailure: Sending non-protected broadcast"
                    + " com.android.bluetooth.btservice.BLUETOOTH_COUNTER_METRICS_ACTION from"
                    + " system uid 1002 pkg com.android.bluetooth\n"
                    + "    at android.util.Log.wtf(Log.java:332)\n"
                    + "    at android.util.Log.wtf(Log.java:326)\n"
                    + "    at"
                    + " com.android.server.am.ActivityManagerService.checkBroadcastFromSystem(ActivityManagerService.java:13609)\n"
                    + "    at"
                    + " com.android.server.am.ActivityManagerService.broadcastIntentLocked(ActivityManagerService.java:14330)\n"
                    + "    at"
                    + " com.android.server.am.ActivityManagerService.broadcastIntentInPackage(ActivityManagerService.java:14530)\n"
                    + "    at"
                    + " com.android.server.am.ActivityManagerService$LocalService.broadcastIntentInPackage(ActivityManagerService.java:17065)\n"
                    + "    at"
                    + " com.android.server.am.PendingIntentRecord.sendInner(PendingIntentRecord.java:526)\n"
                    + "    at"
                    + " com.android.server.am.PendingIntentRecord.sendWithResult(PendingIntentRecord.java:311)\n"
                    + "    at"
                    + " com.android.server.am.ActivityManagerService.sendIntentSender(ActivityManagerService.java:5379)\n"
                    + "    at"
                    + " android.app.PendingIntent.sendAndReturnResult(PendingIntent.java:1012)\n"
                    + "    at android.app.PendingIntent.send(PendingIntent.java:983)\n"
                    + "    at"
                    + " com.android.server.alarm.AlarmManagerService$DeliveryTracker.deliverLocked(AlarmManagerService.java:5500)\n"
                    + "    at"
                    + " com.android.server.alarm.AlarmManagerService.deliverAlarmsLocked(AlarmManagerService.java:4400)\n"
                    + "    at"
                    + " com.android.server.alarm.AlarmManagerService$AlarmThread.run(AlarmManagerService.java:4711)\n"
                    + "Caused by: java.lang.Throwable\n"
                    + "    at"
                    + " com.android.server.am.ActivityManagerService.checkBroadcastFromSystem(ActivityManagerService.java:13610)\n"
                    + "    ... 11 more\n"
                    + "\n"
                    + "========================================\n"
                    + "2022-09-05 04:31:06 event_data (text, 39 bytes)\n"
                    + "start=1662350731248\n"
                    + "end=1662352266140\n"
                    + "\n";
        Files.write(fileDumpFile, fileResult.getBytes());
        Files.write(printDumpFile, printResult.getBytes());
        DeviceUtils sut = createSubjectUnderTestWithTempFile(fileDumpFile, printDumpFile);

        List<DropboxEntry> result = sut.getDropboxEntriesFromStdout(Set.of("system_server_wtf"));

        assertThat(result.get(0).getTime()).isEqualTo(1662351441269L);
        assertThat(result.get(0).getData()).contains("Sending non-protected broadcast");
        assertThat(result.get(0).getTag()).isEqualTo("system_server_wtf");
        assertThat(result.size()).isEqualTo(1);
    }

    private DeviceUtils createSubjectUnderTestWithTempFile(Path... tempFiles) {
        when(mDevice.getSerialNumber()).thenReturn("SERIAL");
        FakeClock fakeClock = new FakeClock();
        Iterator<Path> iter = Arrays.asList(tempFiles).iterator();
        return new DeviceUtils(
                mDevice, fakeClock.getSleeper(), fakeClock, () -> mRunUtil, () -> iter.next());
    }

    private DeviceUtils createSubjectUnderTest() throws DeviceNotAvailableException {
        when(mDevice.getSerialNumber()).thenReturn("SERIAL");
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo ${EPOCHREALTIME")))
                .thenReturn(createSuccessfulCommandResultWithStdout("1"));
        when(mDevice.executeShellV2Command(Mockito.eq("getprop ro.build.version.sdk")))
                .thenReturn(createSuccessfulCommandResultWithStdout("34"));
        FakeClock fakeClock = new FakeClock();
        return new DeviceUtils(
                mDevice,
                fakeClock.getSleeper(),
                fakeClock,
                () -> mRunUtil,
                () -> Files.createTempFile(mFileSystem.getPath("/"), "test", ".tmp"));
    }

    private static class FakeClock implements DeviceUtils.Clock {
        private long mCurrentTime = System.currentTimeMillis();
        private DeviceUtils.Sleeper mSleeper = duration -> mCurrentTime += duration;

        private DeviceUtils.Sleeper getSleeper() {
            return mSleeper;
        }

        @Override
        public long currentTimeMillis() {
            return mCurrentTime += 1;
        }
    }

    private static ArgumentMatcher<String[]> contains(String... args) {
        return array -> Arrays.asList(array).containsAll(Arrays.asList(args));
    }

    private static CommandResult createSuccessfulCommandResultWithStdout(String stdout) {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setExitCode(0);
        commandResult.setStdout(stdout);
        commandResult.setStderr("");
        return commandResult;
    }

    private static CommandResult createFailedCommandResult() {
        CommandResult commandResult = new CommandResult(CommandStatus.FAILED);
        commandResult.setExitCode(1);
        commandResult.setStdout("");
        commandResult.setStderr("error");
        return commandResult;
    }
}
