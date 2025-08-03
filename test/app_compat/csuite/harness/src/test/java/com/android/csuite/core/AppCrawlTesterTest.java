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

import static com.android.csuite.core.RoboLoginConfigProvider.CRAWL_GUIDANCE_FILE_SUFFIX;
import static com.android.csuite.core.RoboLoginConfigProvider.ROBOSCRIPT_FILE_SUFFIX;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.csuite.core.TestUtils.TestArtifactReceiver;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import com.google.common.jimfs.Jimfs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

@RunWith(JUnit4.class)
public final class AppCrawlTesterTest {
    private static final String PACKAGE_NAME = "package.name";
    private final TestArtifactReceiver mTestArtifactReceiver =
            Mockito.mock(TestArtifactReceiver.class);
    private final FileSystem mFileSystem =
            Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix());
    private final ITestDevice mDevice = Mockito.mock(ITestDevice.class);
    private final IRunUtil mRunUtil = Mockito.mock(IRunUtil.class);
    private TestInformation mTestInfo;
    private TestUtils mTestUtils;
    private DeviceUtils mDeviceUtils = Mockito.spy(DeviceUtils.getInstance(mDevice));

    @Before
    public void setUp() throws Exception {
        Mockito.when(mDevice.getSerialNumber()).thenReturn("serial");
        mTestInfo = createTestInfo();
        mTestUtils = createTestUtils();
    }

    @Test
    public void start_apkNotProvided_throwsException() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setUiAutomatorMode(false);

        assertThrows(NullPointerException.class, () -> sut.start());
    }

    @Test
    public void start_roboscriptDirectoryProvided_throws() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setUiAutomatorMode(true);
        Path roboDir = mFileSystem.getPath("robo");
        Files.createDirectories(roboDir);

        sut.setRoboscriptFile(roboDir);

        assertThrows(AssertionError.class, () -> sut.start());
    }

    @Test
    public void start_crawlGuidanceDirectoryProvided_throws() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setUiAutomatorMode(true);
        Path crawlGuidanceDir = mFileSystem.getPath("crawlguide");
        Files.createDirectories(crawlGuidanceDir);

        sut.setCrawlGuidanceProtoFile(crawlGuidanceDir);

        assertThrows(AssertionError.class, () -> sut.start());
    }

    @Test
    public void startAndAssertAppNoCrash_noCrashDetected_doesNotThrow() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(createApkPathWithSplitApks());
        Mockito.doReturn(new DeviceUtils.DeviceTimestamp(1L))
                .when(mDeviceUtils)
                .currentTimeMillis();
        Mockito.doReturn(new ArrayList<>())
                .when(mDeviceUtils)
                .getDropboxEntries(
                        Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.any());

        sut.startAndAssertAppNoCrash();
    }

    @Test
    public void startAndAssertAppNoCrash_dropboxEntriesDetected_throws() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(createApkPathWithSplitApks());
        Mockito.doReturn(new DeviceUtils.DeviceTimestamp(1L))
                .when(mDeviceUtils)
                .currentTimeMillis();
        Mockito.doReturn("crash")
                .when(mTestUtils)
                .getDropboxPackageCrashLog(
                        Mockito.anyString(), Mockito.any(), Mockito.anyBoolean());

        assertThrows(AssertionError.class, () -> sut.startAndAssertAppNoCrash());
    }

    @Test
    public void startAndAssertAppNoCrash_crawlerExceptionIsThrown_throws() throws Exception {
        AppCrawlTester sut = createNotPreparedTestSubject();
        sut.setApkPath(createApkPathWithSplitApks());
        Mockito.doReturn(new DeviceUtils.DeviceTimestamp(1L))
                .when(mDeviceUtils)
                .currentTimeMillis();
        String noCrashLog = null;
        Mockito.doReturn(noCrashLog)
                .when(mTestUtils)
                .getDropboxPackageCrashLog(
                        Mockito.anyString(), Mockito.any(), Mockito.anyBoolean());

        assertThrows(AssertionError.class, () -> sut.startAndAssertAppNoCrash());
    }

    @Test
    public void start_screenRecordEnabled_screenIsRecorded() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(createApkPathWithSplitApks());
        sut.setRecordScreen(true);

        sut.start();

        Mockito.verify(mTestUtils, Mockito.times(1))
                .collectScreenRecord(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void start_screenRecordDisabled_screenIsNotRecorded() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(createApkPathWithSplitApks());
        sut.setRecordScreen(false);

        sut.start();

        Mockito.verify(mTestUtils, Mockito.never())
                .collectScreenRecord(Mockito.any(), Mockito.anyString(), Mockito.any());
    }

    @Test
    public void start_collectGmsVersionEnabled_versionIsCollected() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(createApkPathWithSplitApks());
        sut.setCollectGmsVersion(true);

        sut.start();

        Mockito.verify(mTestUtils, Mockito.times(1)).collectGmsVersion(Mockito.anyString());
    }

    @Test
    public void start_collectGmsVersionDisabled_versionIsNotCollected() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(createApkPathWithSplitApks());
        sut.setCollectGmsVersion(false);

        sut.start();

        Mockito.verify(mTestUtils, Mockito.never()).collectGmsVersion(Mockito.anyString());
    }

    @Test
    public void start_collectAppVersionEnabled_versionIsCollected() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(createApkPathWithSplitApks());
        sut.setCollectAppVersion(true);

        sut.start();

        Mockito.verify(mTestUtils, Mockito.times(1)).collectAppVersion(Mockito.anyString());
    }

    @Test
    public void start_collectAppVersionDisabled_versionIsNotCollected() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(createApkPathWithSplitApks());
        sut.setCollectAppVersion(false);

        sut.start();

        Mockito.verify(mTestUtils, Mockito.never()).collectAppVersion(Mockito.anyString());
    }

    @Test
    public void start_withSplitApksDirectory_doesNotThrowException() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(createApkPathWithSplitApks());

        sut.start();
    }

    @Test
    public void start_sdkPathIsProvidedToCrawler() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(createApkPathWithSplitApks());

        sut.start();

        Mockito.verify(mRunUtil).setEnvVariable(Mockito.eq("ANDROID_SDK"), Mockito.anyString());
    }

    @Test
    public void start_withSplitApksInSubDirectory_doesNotThrowException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("sub"));
        Files.createFile(root.resolve("sub").resolve("base.apk"));
        Files.createFile(root.resolve("sub").resolve("config.apk"));
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(root);

        sut.start();
    }

    @Test
    public void start_withSingleSplitApkDirectory_doesNotThrowException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("base.apk"));
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(root);

        sut.start();
    }

    @Test
    public void start_withSingleApkDirectory_doesNotThrowException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("single.apk"));
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(root);

        sut.start();
    }

    @Test
    public void start_withSingleApkFile_doesNotThrowException() throws Exception {
        Path root = mFileSystem.getPath("single.apk");
        Files.createFile(root);
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(root);

        sut.start();
    }

    @Test
    public void start_withApkDirectoryContainingOtherFileTypes_doesNotThrowException()
            throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("single.apk"));
        Files.createFile(root.resolve("single.not_apk"));
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(root);

        sut.start();
    }

    @Test
    public void start_withApkDirectoryContainingNoApks_throwException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("single.not_apk"));
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(root);

        assertThrows(AppCrawlTester.CrawlerException.class, () -> sut.start());
    }

    @Test
    public void start_withNonApkPath_throwException() throws Exception {
        Path root = mFileSystem.getPath("single.not_apk");
        Files.createFile(root);
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(root);

        assertThrows(AppCrawlTester.CrawlerException.class, () -> sut.start());
    }

    @Test
    public void start_withApksInMultipleDirectories_throwException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("1"));
        Files.createDirectories(root.resolve("2"));
        Files.createFile(root.resolve("1").resolve("single.apk"));
        Files.createFile(root.resolve("2").resolve("single.apk"));
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(root);

        assertThrows(AppCrawlTester.CrawlerException.class, () -> sut.start());
    }

    @Test
    public void start_preparerNotRun_throwsException() throws Exception {
        AppCrawlTester sut = createNotPreparedTestSubject();
        sut.setApkPath(createApkPathWithSplitApks());

        assertThrows(AppCrawlTester.CrawlerException.class, () -> sut.start());
    }

    @Test
    public void start_alreadyRun_throwsException() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(createApkPathWithSplitApks());
        sut.start();

        assertThrows(AppCrawlTester.CrawlerException.class, () -> sut.start());
    }

    @Test
    public void cleanUp_removesOutputDirectory() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(createApkPathWithSplitApks());
        sut.start();
        assertTrue(Files.exists(sut.mOutput));

        sut.cleanUp();

        assertFalse(Files.exists(sut.mOutput));
    }

    @Test
    public void createUtpCrawlerRunCommand_containsRequiredCrawlerParams() throws Exception {
        Path apkRoot = mFileSystem.getPath("apk");
        Files.createDirectories(apkRoot);
        Files.createFile(apkRoot.resolve("some.apk"));
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(apkRoot);
        sut.start();

        String[] result = sut.createUtpCrawlerRunCommand(mTestInfo);

        assertThat(result).asList().contains("android");
        assertThat(result).asList().contains("robo");
        assertThat(result).asList().contains("--device-id");
        assertThat(result).asList().contains("--app-id");
        assertThat(result).asList().contains("--utp-binaries-dir");
        assertThat(result).asList().contains("--key-file");
        assertThat(result).asList().contains("--base-crawler-apk");
        assertThat(result).asList().contains("--stub-crawler-apk");
    }

    @Test
    public void createUtpCrawlerRunCommand_containsRoboscriptFileWhenProvided() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        Path roboDir = mFileSystem.getPath("/robo");
        Files.createDirectory(roboDir);
        Path roboFile = Files.createFile(roboDir.resolve("app.roboscript"));
        sut.setUiAutomatorMode(true);
        sut.setRoboscriptFile(roboFile);
        sut.start();

        String[] result = sut.createUtpCrawlerRunCommand(mTestInfo);

        assertThat(result).asList().contains("--crawler-asset");
        assertThat(result).asList().contains("robo.script=" + roboFile.toString());
    }

    @Test
    public void createUtpCrawlerRunCommand_containsCrawlGuidanceFileWhenProvided()
            throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        Path crawlGuideDir = mFileSystem.getPath("/cg");
        Files.createDirectory(crawlGuideDir);
        Path crawlGuideFile = Files.createFile(crawlGuideDir.resolve("app.crawlguide"));

        sut.setUiAutomatorMode(true);
        sut.setCrawlGuidanceProtoFile(crawlGuideFile);
        sut.start();
        String[] result = sut.createUtpCrawlerRunCommand(mTestInfo);

        assertThat(result).asList().contains("--crawl-guidance-proto-path");
    }

    @Test
    public void createUtpCrawlerRunCommand_loginDirContainsOnlyCrawlGuidanceFile_addsFilePath()
            throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        Path loginFilesDir = mFileSystem.getPath("/login");
        Files.createDirectory(loginFilesDir);
        Path crawlGuideFile =
                Files.createFile(loginFilesDir.resolve(PACKAGE_NAME + CRAWL_GUIDANCE_FILE_SUFFIX));

        sut.setUiAutomatorMode(true);
        sut.setLoginConfigDir(loginFilesDir);
        sut.start();
        String[] result = sut.createUtpCrawlerRunCommand(mTestInfo);

        assertThat(result).asList().contains("--crawl-guidance-proto-path");
        assertThat(result).asList().contains(crawlGuideFile.toString());
    }

    @Test
    public void createUtpCrawlerRunCommand_loginDirContainsOnlyRoboscriptFile_addsFilePath()
            throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        Path loginFilesDir = mFileSystem.getPath("/login");
        Files.createDirectory(loginFilesDir);
        Path roboscriptFile =
                Files.createFile(loginFilesDir.resolve(PACKAGE_NAME + ROBOSCRIPT_FILE_SUFFIX));

        sut.setUiAutomatorMode(true);
        sut.setLoginConfigDir(loginFilesDir);
        sut.start();
        String[] result = sut.createUtpCrawlerRunCommand(mTestInfo);

        assertThat(result).asList().contains("--crawler-asset");
        assertThat(result).asList().contains("robo.script=" + roboscriptFile.toString());
    }

    @Test
    public void
            createUtpCrawlerRunCommand_loginDirContainsMultipleLoginFiles_addsRoboscriptFilePath()
                    throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        Path loginFilesDir = mFileSystem.getPath("/login");
        Files.createDirectory(loginFilesDir);
        Path roboscriptFile =
                Files.createFile(loginFilesDir.resolve(PACKAGE_NAME + ROBOSCRIPT_FILE_SUFFIX));
        Path crawlGuideFile =
                Files.createFile(loginFilesDir.resolve(PACKAGE_NAME + CRAWL_GUIDANCE_FILE_SUFFIX));

        sut.setUiAutomatorMode(true);
        sut.setLoginConfigDir(loginFilesDir);
        sut.start();
        String[] result = sut.createUtpCrawlerRunCommand(mTestInfo);

        assertThat(result).asList().contains("--crawler-asset");
        assertThat(result).asList().contains("robo.script=" + roboscriptFile.toString());
        assertThat(result).asList().doesNotContain(crawlGuideFile.toString());
    }

    @Test
    public void createUtpCrawlerRunCommand_loginDirEmpty_doesNotAddFlag() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();
        Path loginFilesDir = mFileSystem.getPath("/login");
        Files.createDirectory(loginFilesDir);

        sut.setUiAutomatorMode(true);
        sut.setLoginConfigDir(loginFilesDir);
        sut.start();
        String[] result = sut.createUtpCrawlerRunCommand(mTestInfo);

        assertThat(result).asList().doesNotContain("--crawler-asset");
        assertThat(result).asList().doesNotContain("--crawl-guidance-proto-path");
    }

    @Test
    public void createUtpCrawlerRunCommand_crawlerIsExecutedThroughJavaJar() throws Exception {
        Path apkRoot = mFileSystem.getPath("apk");
        Files.createDirectories(apkRoot);
        Files.createFile(apkRoot.resolve("some.apk"));
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(apkRoot);
        sut.start();

        String[] result = sut.createUtpCrawlerRunCommand(mTestInfo);

        assertThat(result).asList().contains("java");
        assertThat(result).asList().contains("-jar");
    }

    @Test
    public void createUtpCrawlerRunCommand_splitApksProvided_includedInTheCommand()
            throws Exception {
        Path apkRoot = mFileSystem.getPath("apk");
        Files.createDirectories(apkRoot);
        Files.createFile(apkRoot.resolve("base.apk"));
        Files.createFile(apkRoot.resolve("config1.apk"));
        Files.createFile(apkRoot.resolve("config2.apk"));
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(apkRoot);
        sut.start();

        String[] result = sut.createUtpCrawlerRunCommand(mTestInfo);

        assertThat(Arrays.asList(result).stream().filter(s -> s.equals("--apks-to-crawl")).count())
                .isEqualTo(3);
        assertThat(Arrays.asList(result).stream().filter(s -> s.contains("config1.apk")).count())
                .isEqualTo(1);
    }

    @Test
    public void createUtpCrawlerRunCommand_obbProvided_includedInTheCommand() throws Exception {
        Path apkRoot = mFileSystem.getPath("apk");
        Files.createDirectories(apkRoot);
        Files.createFile(apkRoot.resolve("base.apk"));
        Files.createFile(apkRoot.resolve("config1.apk"));
        Files.createFile(apkRoot.resolve("main.package.obb"));
        Files.createFile(apkRoot.resolve("patch.package.obb"));
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(apkRoot);
        sut.start();

        String[] result = sut.createUtpCrawlerRunCommand(mTestInfo);

        assertThat(Arrays.asList(result).stream().filter(s -> s.equals("--apks-to-crawl")).count())
                .isEqualTo(2);
        assertThat(Arrays.asList(result).stream().filter(s -> s.equals("--files-to-push")).count())
                .isEqualTo(2);
        assertThat(
                        Arrays.asList(result).stream()
                                .filter(s -> s.contains("main.package.obb"))
                                .count())
                .isEqualTo(1);
    }

    @Test
    public void createUtpCrawlerRunCommand_uiAutomatorModeEnabled_doesNotContainApks()
            throws Exception {
        Path apkRoot = mFileSystem.getPath("apk");
        Files.createDirectories(apkRoot);
        Files.createFile(apkRoot.resolve("base.apk"));
        Files.createFile(apkRoot.resolve("config1.apk"));
        Files.createFile(apkRoot.resolve("config2.apk"));
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(apkRoot);
        sut.setUiAutomatorMode(true);
        sut.start();

        String[] result = sut.createUtpCrawlerRunCommand(mTestInfo);

        assertThat(Arrays.asList(result).stream().filter(s -> s.equals("--apks-to-crawl")).count())
                .isEqualTo(0);
    }

    @Test
    public void createUtpCrawlerRunCommand_uiAutomatorModeEnabled_containsUiAutomatorParam()
            throws Exception {
        Path apkRoot = mFileSystem.getPath("apk");
        Files.createDirectories(apkRoot);
        Files.createFile(apkRoot.resolve("base.apk"));
        Files.createFile(apkRoot.resolve("config1.apk"));
        Files.createFile(apkRoot.resolve("config2.apk"));
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(apkRoot);
        sut.setUiAutomatorMode(true);
        sut.start();

        String[] result = sut.createUtpCrawlerRunCommand(mTestInfo);

        assertThat(
                        Arrays.asList(result).stream()
                                .filter(s -> s.equals("--ui-automator-mode"))
                                .count())
                .isEqualTo(1);
        assertThat(
                        Arrays.asList(result).stream()
                                .filter(s -> s.equals("--app-installed-on-device"))
                                .count())
                .isEqualTo(1);
    }

    @Test
    public void createUtpCrawlerRunCommand_doesNotContainNullOrEmptyStrings() throws Exception {
        Path apkRoot = mFileSystem.getPath("apk");
        Files.createDirectories(apkRoot);
        Files.createFile(apkRoot.resolve("base.apk"));
        Files.createFile(apkRoot.resolve("config1.apk"));
        Files.createFile(apkRoot.resolve("config2.apk"));
        AppCrawlTester sut = createPreparedTestSubject();
        sut.setApkPath(apkRoot);
        sut.start();

        String[] result = sut.createUtpCrawlerRunCommand(mTestInfo);

        assertThat(Arrays.asList(result).stream().filter(s -> s == null).count()).isEqualTo(0);
        assertThat(Arrays.asList(result).stream().map(String::trim).filter(String::isEmpty).count())
                .isEqualTo(0);
    }

    @Test
    public void getRoboscriptSignal_withSuccessfulRoboscriptActions_successSignal()
            throws Exception {
        Path roboOutput = createMockRoboOutputFile(7, 7);
        AppCrawlTester sut = createPreparedTestSubject();

        TestUtils.RoboscriptSignal signal = sut.getRoboscriptSignal(Optional.of(roboOutput));

        assertThat(signal).isEqualTo(TestUtils.RoboscriptSignal.SUCCESS);
    }

    @Test
    public void getRoboscriptSignal_withNoRoboscriptOutput_unknownSignal() throws Exception {
        Path roboOutput = Files.createFile(mFileSystem.getPath("output.txt"));
        AppCrawlTester sut = createPreparedTestSubject();

        TestUtils.RoboscriptSignal signal = sut.getRoboscriptSignal(Optional.of(roboOutput));

        assertThat(signal).isEqualTo(TestUtils.RoboscriptSignal.UNKNOWN);
    }

    @Test
    public void getRoboscriptSignal_withEmptyOutputFile_unknownSignal() throws Exception {
        AppCrawlTester sut = createPreparedTestSubject();

        TestUtils.RoboscriptSignal signal = sut.getRoboscriptSignal(Optional.empty());

        assertThat(signal).isEqualTo(TestUtils.RoboscriptSignal.UNKNOWN);
    }

    @Test
    public void getRoboscriptSignal_withUnsuccessfulActions_failureSignal() throws Exception {
        Path roboOutput = createMockRoboOutputFile(0, 7);
        AppCrawlTester sut = createPreparedTestSubject();

        TestUtils.RoboscriptSignal signal = sut.getRoboscriptSignal(Optional.of(roboOutput));

        assertThat(signal).isEqualTo(TestUtils.RoboscriptSignal.FAIL);
    }

    private Path createMockRoboOutputFile(int totalActions, int successfulActions)
            throws IOException {
        Path roboOutput = Files.createFile(mFileSystem.getPath("output.txt"));
        ArrayList<String> outputContent = new ArrayList<>();
        outputContent.add("some previous fields");
        outputContent.add("robo_script_execution {");
        outputContent.add("  total_actions: " + String.valueOf(totalActions) + "\n");
        outputContent.add("  successful_actions: " + String.valueOf(successfulActions));
        outputContent.add("}");
        Files.write(roboOutput, outputContent, StandardCharsets.UTF_8);
        return roboOutput;
    }

    private void simulatePreparerWasExecutedSuccessfully()
            throws ConfigurationException, IOException, TargetSetupError {
        IRunUtil runUtil = Mockito.mock(IRunUtil.class);
        Mockito.when(runUtil.runTimedCmd(Mockito.anyLong(), ArgumentMatchers.<String>any()))
                .thenReturn(createSuccessfulCommandResult());
        AppCrawlTesterHostPreparer preparer =
                new AppCrawlTesterHostPreparer(() -> runUtil, mFileSystem);
        OptionSetter optionSetter = new OptionSetter(preparer);

        Path bin = Files.createDirectories(mFileSystem.getPath("/bin"));
        Files.createFile(bin.resolve("utp-cli-android_deploy.jar"));

        optionSetter.setOptionValue(
                AppCrawlTesterHostPreparer.SDK_TAR_OPTION,
                Files.createDirectories(mFileSystem.getPath("/sdk")).toString());
        optionSetter.setOptionValue(AppCrawlTesterHostPreparer.CRAWLER_BIN_OPTION, bin.toString());
        optionSetter.setOptionValue(
                AppCrawlTesterHostPreparer.CREDENTIAL_JSON_OPTION,
                Files.createDirectories(mFileSystem.getPath("/cred.json")).toString());
        preparer.setUp(mTestInfo);
    }

    private AppCrawlTester createNotPreparedTestSubject() throws DeviceNotAvailableException {
        Mockito.when(mRunUtil.runTimedCmd(Mockito.anyLong(), ArgumentMatchers.<String>any()))
                .thenReturn(createSuccessfulCommandResult());
        Mockito.when(mDevice.getSerialNumber()).thenReturn("serial");
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo ${EPOCHREALTIME")))
                .thenReturn(createSuccessfulCommandResultWithStdout("1"));
        when(mDevice.executeShellV2Command(Mockito.eq("getprop ro.build.version.sdk")))
                .thenReturn(createSuccessfulCommandResultWithStdout("33"));
        return new AppCrawlTester(PACKAGE_NAME, mTestUtils, () -> mRunUtil, mFileSystem);
    }

    private AppCrawlTester createPreparedTestSubject()
            throws IOException, ConfigurationException, TargetSetupError,
                    DeviceNotAvailableException {
        simulatePreparerWasExecutedSuccessfully();
        Mockito.when(mRunUtil.runTimedCmd(Mockito.anyLong(), ArgumentMatchers.<String>any()))
                .thenReturn(createSuccessfulCommandResult());
        Mockito.when(mDevice.getSerialNumber()).thenReturn("serial");
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo ${EPOCHREALTIME")))
                .thenReturn(createSuccessfulCommandResultWithStdout("1"));
        when(mDevice.executeShellV2Command(Mockito.eq("getprop ro.build.version.sdk")))
                .thenReturn(createSuccessfulCommandResultWithStdout("33"));
        return new AppCrawlTester(PACKAGE_NAME, mTestUtils, () -> mRunUtil, mFileSystem);
    }

    private TestUtils createTestUtils() throws DeviceNotAvailableException {
        TestUtils testUtils =
                Mockito.spy(new TestUtils(mTestInfo, mTestArtifactReceiver, mDeviceUtils));
        Mockito.doAnswer(
                        invocation -> {
                            ((DeviceUtils.RunnableThrowingDeviceNotAvailable)
                                            invocation.getArguments()[0])
                                    .run();
                            return null;
                        })
                .when(testUtils)
                .collectScreenRecord(Mockito.any(), Mockito.anyString(), Mockito.any());
        Mockito.doNothing().when(testUtils).collectAppVersion(Mockito.anyString());
        Mockito.doNothing().when(testUtils).collectGmsVersion(Mockito.anyString());
        return testUtils;
    }

    private TestInformation createTestInfo() {
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device1", mDevice);
        context.addDeviceBuildInfo("device1", new BuildInfo());
        return TestInformation.newBuilder().setInvocationContext(context).build();
    }

    private Path createApkPathWithSplitApks() throws IOException {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("base.apk"));
        Files.createFile(root.resolve("config.apk"));

        return root;
    }

    private static CommandResult createSuccessfulCommandResultWithStdout(String stdout) {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setExitCode(0);
        commandResult.setStdout(stdout);
        commandResult.setStderr("");
        return commandResult;
    }

    private static CommandResult createSuccessfulCommandResult() {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setExitCode(0);
        commandResult.setStdout("");
        commandResult.setStderr("");
        return commandResult;
    }
}
