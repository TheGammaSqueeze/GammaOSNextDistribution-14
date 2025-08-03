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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.android.csuite.core.DeviceUtils.DeviceTimestamp;
import com.android.csuite.core.DeviceUtils.DropboxEntry;
import com.android.csuite.core.TestUtils.TestArtifactReceiver;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.InputStreamSource;

import com.google.common.jimfs.Jimfs;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public final class TestUtilsTest {
    private final TestArtifactReceiver mMockTestArtifactReceiver =
            Mockito.mock(TestArtifactReceiver.class);
    private final ITestDevice mMockDevice = mock(ITestDevice.class);
    private final DeviceUtils mMockDeviceUtils = Mockito.mock(DeviceUtils.class);
    private static final String TEST_PACKAGE_NAME = "package.name";
    @Rule public final TemporaryFolder mTempFolder = new TemporaryFolder();
    private final FileSystem mFileSystem =
            Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix());

    @Test
    public void listApks_withSplitApksInSubDirectory_returnsApks() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("sub"));
        Files.createFile(root.resolve("sub").resolve("base.apk"));
        Files.createFile(root.resolve("sub").resolve("config.apk"));

        List<Path> res = TestUtils.listApks(root);

        List<String> fileNames =
                res.stream()
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toList());
        assertThat(fileNames).containsExactly("base.apk", "config.apk");
    }

    @Test
    public void listApks_withSingleSplitApkDirectory_returnsApks() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("base.apk"));

        List<Path> res = TestUtils.listApks(root);

        List<String> fileNames =
                res.stream()
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toList());
        assertThat(fileNames).containsExactly("base.apk");
    }

    @Test
    public void listApks_withSplitApkDirectory_returnsListWithBaseApkAsTheFirstElement()
            throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("base.apk"));
        Files.createFile(root.resolve("a.apk"));
        Files.createFile(root.resolve("b.apk"));
        Files.createFile(root.resolve("c.apk"));

        List<Path> res = TestUtils.listApks(root);

        assertThat(res.get(0).getFileName().toString()).isEqualTo("base.apk");
    }

    @Test
    public void listApks_withSingleApkDirectory_returnsApks() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("single.apk"));

        List<Path> res = TestUtils.listApks(root);

        List<String> fileNames =
                res.stream()
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toList());
        assertThat(fileNames).containsExactly("single.apk");
    }

    @Test
    public void listApks_withSingleApkFile_returnsApks() throws Exception {
        Path root = mFileSystem.getPath("single.apk");
        Files.createFile(root);

        List<Path> res = TestUtils.listApks(root);

        List<String> fileNames =
                res.stream()
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toList());
        assertThat(fileNames).containsExactly("single.apk");
    }

    @Test
    public void listApks_withApkDirectoryContainingObbFiles_returnsApksWithObb() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("single.apk"));
        Files.createFile(root.resolve("single.not_apk"));
        Files.createFile(root.resolve("main.123.package.obb"));

        List<Path> res = TestUtils.listApks(root);

        List<String> fileNames =
                res.stream()
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toList());
        assertThat(fileNames).containsExactly("single.apk", "main.123.package.obb");
    }

    @Test
    public void listApks_withApkDirectoryContainingOtherFileTypes_returnsApksOnly()
            throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("single.apk"));
        Files.createFile(root.resolve("single.not_apk"));

        List<Path> res = TestUtils.listApks(root);

        List<String> fileNames =
                res.stream()
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toList());
        assertThat(fileNames).containsExactly("single.apk");
    }

    @Test
    public void listApks_withApkDirectoryContainingNoApks_throwException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("single.not_apk"));

        assertThrows(TestUtils.TestUtilsException.class, () -> TestUtils.listApks(root));
    }

    @Test
    public void listApks_withApkDirectoryContainingOnlyObbFiles_throwException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("main.123.package.obb"));

        assertThrows(TestUtils.TestUtilsException.class, () -> TestUtils.listApks(root));
    }

    @Test
    public void listApks_withNonApkFile_throwException() throws Exception {
        Path root = mFileSystem.getPath("single.not_apk");
        Files.createFile(root);

        assertThrows(TestUtils.TestUtilsException.class, () -> TestUtils.listApks(root));
    }

    @Test
    public void listApks_withMultipleSingleApks_throwException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("single1.apk"));
        Files.createFile(root.resolve("single2.apk"));

        assertThrows(TestUtils.TestUtilsException.class, () -> TestUtils.listApks(root));
    }

    @Test
    public void listApks_withApksInMultipleDirectories_throwException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("1"));
        Files.createDirectories(root.resolve("2"));
        Files.createFile(root.resolve("1").resolve("base.apk"));
        Files.createFile(root.resolve("2").resolve("config.apk"));

        assertThrows(TestUtils.TestUtilsException.class, () -> TestUtils.listApks(root));
    }

    @Test
    public void listApks_apksInTheSameDirectoryAndObbsInADifferentDirectory_doesNotThrow()
            throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("1"));
        Files.createDirectories(root.resolve("2"));
        Files.createFile(root.resolve("1").resolve("base.apk"));
        Files.createFile(root.resolve("1").resolve("config.apk"));
        Files.createFile(root.resolve("2").resolve("main.123.com.package.obb"));

        TestUtils.listApks(root);
    }

    @Test
    public void collectScreenshot_savesToTestLog() throws Exception {
        TestUtils sut = createSubjectUnderTest();
        InputStreamSource screenshotData = new FileInputStreamSource(mTempFolder.newFile());
        when(mMockDevice.getScreenshot()).thenReturn(screenshotData);
        when(mMockDevice.getSerialNumber()).thenReturn("SERIAL");

        sut.collectScreenshot(TEST_PACKAGE_NAME);

        Mockito.verify(mMockTestArtifactReceiver, times(1))
                .addTestArtifact(
                        Mockito.contains("screenshot"),
                        Mockito.any(),
                        Mockito.any(InputStreamSource.class));
    }

    @Test
    public void saveApks_always_savesOnTestPass() throws Exception {
        TestUtils sut = createSubjectUnderTest();
        boolean testPassed = true;

        sut.saveApks(
                TestUtils.TakeEffectWhen.ALWAYS, testPassed, "apk", Arrays.asList(new File("")));

        Mockito.verify(mMockTestArtifactReceiver, times(1))
                .addTestArtifact(Mockito.contains("apk"), Mockito.any(), Mockito.any(File.class));
    }

    @Test
    public void saveApks_always_savesOnTestFail() throws Exception {
        TestUtils sut = createSubjectUnderTest();
        boolean testPassed = false;

        sut.saveApks(
                TestUtils.TakeEffectWhen.ALWAYS, testPassed, "apk", Arrays.asList(new File("")));

        Mockito.verify(mMockTestArtifactReceiver, times(1))
                .addTestArtifact(Mockito.contains("apk"), Mockito.any(), Mockito.any(File.class));
    }

    @Test
    public void saveApks_never_doesNotSaveOnTestPass() throws Exception {
        TestUtils sut = createSubjectUnderTest();
        boolean testPassed = true;

        sut.saveApks(
                TestUtils.TakeEffectWhen.NEVER, testPassed, "apk", Arrays.asList(new File("")));

        Mockito.verify(mMockTestArtifactReceiver, times(0))
                .addTestArtifact(Mockito.contains("apk"), Mockito.any(), Mockito.any(File.class));
    }

    @Test
    public void saveApks_never_doesNotSaveOnTestFail() throws Exception {
        TestUtils sut = createSubjectUnderTest();
        boolean testPassed = false;

        sut.saveApks(
                TestUtils.TakeEffectWhen.NEVER, testPassed, "apk", Arrays.asList(new File("")));

        Mockito.verify(mMockTestArtifactReceiver, times(0))
                .addTestArtifact(Mockito.contains("apk"), Mockito.any(), Mockito.any(File.class));
    }

    @Test
    public void saveApks_onPass_doesNotSaveOnTestFail() throws Exception {
        TestUtils sut = createSubjectUnderTest();
        boolean testPassed = false;

        sut.saveApks(
                TestUtils.TakeEffectWhen.ON_PASS, testPassed, "apk", Arrays.asList(new File("")));

        Mockito.verify(mMockTestArtifactReceiver, times(0))
                .addTestArtifact(Mockito.contains("apk"), Mockito.any(), Mockito.any(File.class));
    }

    @Test
    public void saveApks_onPass_savesOnTestPass() throws Exception {
        TestUtils sut = createSubjectUnderTest();
        boolean testPassed = true;

        sut.saveApks(
                TestUtils.TakeEffectWhen.ON_PASS, testPassed, "apk", Arrays.asList(new File("")));

        Mockito.verify(mMockTestArtifactReceiver, times(1))
                .addTestArtifact(Mockito.contains("apk"), Mockito.any(), Mockito.any(File.class));
    }

    @Test
    public void saveApks_onFail_doesNotSaveOnTestPass() throws Exception {
        TestUtils sut = createSubjectUnderTest();
        boolean testPassed = true;

        sut.saveApks(
                TestUtils.TakeEffectWhen.ON_FAIL, testPassed, "apk", Arrays.asList(new File("")));

        Mockito.verify(mMockTestArtifactReceiver, times(0))
                .addTestArtifact(Mockito.contains("apk"), Mockito.any(), Mockito.any(File.class));
    }

    @Test
    public void saveApks_onFail_savesOnTestFail() throws Exception {
        TestUtils sut = createSubjectUnderTest();
        boolean testPassed = false;

        sut.saveApks(
                TestUtils.TakeEffectWhen.ON_FAIL, testPassed, "apk", Arrays.asList(new File("")));

        Mockito.verify(mMockTestArtifactReceiver, times(1))
                .addTestArtifact(Mockito.contains("apk"), Mockito.any(), Mockito.any(File.class));
    }

    @Test
    public void getDropboxPackageCrashLog_noEntries_returnsNull() throws Exception {
        TestUtils sut = createSubjectUnderTest();
        when(mMockDeviceUtils.getDropboxEntries(Mockito.any())).thenReturn(List.of());
        DeviceTimestamp startTime = new DeviceTimestamp(0);

        String result = sut.getDropboxPackageCrashLog(TEST_PACKAGE_NAME, startTime, false);

        assertThat(result).isNull();
    }

    @Test
    public void getDropboxPackageCrashLog_noEntries_doesNotSaveOutput() throws Exception {
        TestUtils sut = createSubjectUnderTest();
        when(mMockDeviceUtils.getDropboxEntries(Mockito.any())).thenReturn(List.of());
        DeviceTimestamp startTime = new DeviceTimestamp(0);
        boolean saveToFile = true;

        sut.getDropboxPackageCrashLog(TEST_PACKAGE_NAME, startTime, saveToFile);

        Mockito.verify(mMockTestArtifactReceiver, Mockito.never())
                .addTestArtifact(
                        Mockito.contains("dropbox"), Mockito.any(), Mockito.any(byte[].class));
    }

    @Test
    public void getDropboxPackageCrashLog_appCrashed_saveOutput() throws Exception {
        TestUtils sut = createSubjectUnderTest();
        when(mMockDeviceUtils.getDropboxEntries(
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(
                        List.of(
                                new DeviceUtils.DropboxEntry(
                                        2,
                                        DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                .toArray(
                                                        new String
                                                                [DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                                        .size()])[0],
                                        "Package: " + TEST_PACKAGE_NAME)));
        DeviceTimestamp startTime = new DeviceTimestamp(0);
        boolean saveToFile = true;

        sut.getDropboxPackageCrashLog(TEST_PACKAGE_NAME, startTime, saveToFile);

        Mockito.verify(mMockTestArtifactReceiver, Mockito.times(1))
                .addTestArtifact(
                        Mockito.contains("dropbox"), Mockito.any(), Mockito.any(byte[].class));
    }

    @Test
    public void compileTestFailureMessage_videoStartTimeProvided_returnWithVideoTimeForCrashes()
            throws Exception {
        TestUtils sut = createSubjectUnderTest();
        int videoStartTime = 10000;
        int crashTime1 = 12000;
        int crashTime2 = 72000;
        String expectedTime1 = "00:02";
        String expectedTime2 = "01:02";
        List<DropboxEntry> crashEntries =
                List.of(
                        new DeviceUtils.DropboxEntry(
                                crashTime1,
                                DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                        .toArray(
                                                new String
                                                        [DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                                .size()])[0],
                                TEST_PACKAGE_NAME + " entry1"),
                        new DeviceUtils.DropboxEntry(
                                crashTime2,
                                DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                        .toArray(
                                                new String
                                                        [DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                                .size()])[0],
                                TEST_PACKAGE_NAME + " entry2"));

        String crashMessage =
                sut.compileTestFailureMessage(
                        TEST_PACKAGE_NAME,
                        crashEntries,
                        false,
                        new DeviceTimestamp(videoStartTime));

        assertThat(crashMessage).contains(expectedTime1);
        assertThat(crashMessage).contains(expectedTime2);
    }

    private TestUtils createSubjectUnderTest() {
        return new TestUtils(createTestInfo(), mMockTestArtifactReceiver, mMockDeviceUtils);
    }

    private TestInformation createTestInfo() {
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device1", mMockDevice);
        context.addDeviceBuildInfo("device1", new BuildInfo());
        return TestInformation.newBuilder().setInvocationContext(context).build();
    }
}
