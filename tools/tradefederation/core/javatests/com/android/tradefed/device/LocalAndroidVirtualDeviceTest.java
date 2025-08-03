/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tradefed.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Unit tests for {@link LocalAndroidVirtualDevice}. */
@RunWith(JUnit4.class)
public class LocalAndroidVirtualDeviceTest {

    private class TestableLocalAndroidVirtualDevice extends LocalAndroidVirtualDevice {

        TestableLocalAndroidVirtualDevice(
                IDevice device,
                IDeviceStateMonitor stateMonitor,
                IDeviceMonitor allocationMonitor) {
            super(device, stateMonitor, allocationMonitor);
        }

        IRunUtil currentRunUtil;
        boolean expectToConnect;

        @Override
        public boolean adbTcpConnect(String host, String port) {
            Assert.assertTrue("Unexpected method call to adbTcpConnect.", expectToConnect);
            Assert.assertEquals(IP_ADDRESS, host);
            Assert.assertEquals(PORT, port);
            return true;
        }

        @Override
        public boolean adbTcpDisconnect(String host, String port) {
            Assert.assertEquals(IP_ADDRESS, host);
            Assert.assertEquals(PORT, port);
            return true;
        }

        @Override
        public boolean waitForDeviceAvailable() {
            Assert.assertTrue("Unexpected method call to waitForDeviceAvailable.", expectToConnect);
            return true;
        }

        @Override
        protected IRunUtil createRunUtil() {
            Assert.assertNotNull("Unexpected method call to createRunUtil.", currentRunUtil);
            IRunUtil returnValue = currentRunUtil;
            currentRunUtil = null;
            return returnValue;
        }
    }

    private static final String STUB_SERIAL_NUMBER = "local-virtual-device-0";
    private static final Integer DEVICE_NUM_OFFSET = 5;
    private static final String IP_ADDRESS = "127.0.0.1";
    private static final String PORT = "6520";
    private static final String ONLINE_SERIAL_NUMBER = IP_ADDRESS + ":" + PORT;
    private static final String INSTANCE_NAME = "local-instance-1";
    private static final long ACLOUD_TIMEOUT = 12345;
    private static final String SUCCESS_REPORT_FORMAT =
            "{"
                    + " \"command\": \"create\","
                    + " \"data\": {"
                    + "  \"devices\": ["
                    + "   {"
                    + "    \"ip\": \"%s\","
                    + "    \"instance_name\": \"%s\","
                    + "    \"logs\": ["
                    + "     {"
                    + "      \"path\": \"%s\","
                    + "      \"type\": \"KERNEL_LOG\","
                    + "      \"name\": \"kernel.1.log\""
                    + "     }"
                    + "    ]"
                    + "   }"
                    + "  ]"
                    + " },"
                    + " \"errors\": [],"
                    + " \"status\": \"SUCCESS\""
                    + "}";
    private static final String FAILURE_REPORT_FORMAT =
            "{"
                    + " \"command\": \"create\","
                    + " \"data\": {"
                    + "  \"devices_failing_boot\": ["
                    + "   {"
                    + "    \"ip\": \"%s\","
                    + "    \"instance_name\": \"%s\","
                    + "    \"logs\": ["
                    + "     {"
                    + "      \"path\": \"%s\","
                    + "      \"type\": \"KERNEL_LOG\","
                    + "      \"name\": \"kernel.1.log\""
                    + "     }"
                    + "    ]"
                    + "   }"
                    + "  ]"
                    + " },"
                    + " \"errors\": [],"
                    + " \"status\": \"BOOT_FAIL\""
                    + "}";

    // Temporary files.
    private File mAcloud;
    private File mImageZip;
    private File mHostPackageTarGzip;
    private File mBootImageZip;
    private File mSystemImageZip;
    private File mOtaToolsZip;

    // Mock object.
    @Mock IBuildInfo mMockBuildInfo;

    // The object under test.
    private TestableLocalAndroidVirtualDevice mLocalAvd;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        mAcloud = FileUtil.createTempFile("acloud-dev", "");
        mImageZip = ZipUtil.createZip(new ArrayList<File>());
        mHostPackageTarGzip = FileUtil.createTempFile("cvd-host_package", ".tar.gz");
        createHostPackage(mHostPackageTarGzip);
        mBootImageZip = null;
        mSystemImageZip = null;
        mOtaToolsZip = null;

        when(mMockBuildInfo.getFile((String) any()))
                .thenAnswer(
                        invocation -> {
                            switch ((String) invocation.getArguments()[0]) {
                                case "device":
                                    return mImageZip;
                                case "cvd-host_package.tar.gz":
                                    return mHostPackageTarGzip;
                                case "boot-img.zip":
                                    return mBootImageZip;
                                case "system-img.zip":
                                    return mSystemImageZip;
                                case "otatools.zip":
                                    return mOtaToolsZip;
                                default:
                                    return null;
                            }
                        });
        IDeviceStateMonitor mockDeviceStateMonitor = mock(IDeviceStateMonitor.class);
        mockDeviceStateMonitor.setIDevice(any());

        IDeviceMonitor mockDeviceMonitor = mock(IDeviceMonitor.class);

        mLocalAvd =
                new TestableLocalAndroidVirtualDevice(
                        new StubLocalAndroidVirtualDevice(STUB_SERIAL_NUMBER, DEVICE_NUM_OFFSET),
                        mockDeviceStateMonitor,
                        mockDeviceMonitor);
        TestDeviceOptions options = mLocalAvd.getOptions();
        options.setGceCmdTimeout(ACLOUD_TIMEOUT);
        options.setAvdDriverBinary(mAcloud);
        options.setGceDriverLogLevel(LogLevel.DEBUG);
        options.getGceDriverParams().add("-test");
    }

    private void setUpExtraZips() throws IOException {
        ArrayList<File> empty = new ArrayList<File>();
        mBootImageZip = ZipUtil.createZip(empty);
        mSystemImageZip = ZipUtil.createZip(empty);
        mOtaToolsZip = ZipUtil.createZip(empty);
    }

    @After
    public void tearDown() {
        if (mLocalAvd != null) {
            // Ensure cleanup in case the test failed before calling postInvocationTearDown.
            mLocalAvd.deleteTempDirs();
            mLocalAvd = null;
        }
        FileUtil.deleteFile(mAcloud);
        FileUtil.deleteFile(mImageZip);
        FileUtil.deleteFile(mHostPackageTarGzip);
        FileUtil.deleteFile(mBootImageZip);
        FileUtil.deleteFile(mSystemImageZip);
        FileUtil.deleteFile(mOtaToolsZip);
        mAcloud = null;
        mImageZip = null;
        mHostPackageTarGzip = null;
        mBootImageZip = null;
        mSystemImageZip = null;
        mOtaToolsZip = null;
    }

    private static void createHostPackage(File hostPackageTarGzip) throws IOException {
        OutputStream out = null;
        try {
            out = new FileOutputStream(hostPackageTarGzip);
            out = new BufferedOutputStream(out);
            out = new GZIPOutputStream(out);
            out = new TarArchiveOutputStream(out);
            TarArchiveOutputStream tar = (TarArchiveOutputStream) out;
            TarArchiveEntry tarEntry = new TarArchiveEntry("bin" + File.separator);
            tar.putArchiveEntry(tarEntry);
            tar.closeArchiveEntry();
            tar.finish();
        } finally {
            StreamUtil.close(out);
        }
    }

    private Answer<CommandResult> writeToReportFile(CommandStatus status, String reportFormat) {
        return invocation -> {
            Object[] args = invocation.getArguments();
            String instanceDir = null;
            String reportFile = null;
            for (int index = 0; index < args.length; index++) {
                if ("--report_file".equals(args[index])) {
                    index++;
                    reportFile = (String) args[index];
                } else if ("--local-instance-dir".equals(args[index])) {
                    index++;
                    instanceDir = (String) args[index];
                }
            }
            Assert.assertNotNull(
                    "Acloud command does not contain --local-instance-dir.", instanceDir);
            Assert.assertNotNull("Acloud command does not contain --report_file.", reportFile);

            File logFile = new File(instanceDir, "kernel.log");
            Assert.assertTrue(logFile.createNewFile());
            FileUtil.writeToFile(
                    String.format(
                            reportFormat,
                            ONLINE_SERIAL_NUMBER,
                            INSTANCE_NAME,
                            logFile.getAbsolutePath()),
                    new File(reportFile));

            CommandResult result = new CommandResult(status);
            result.setStderr("acloud create");
            result.setStdout("acloud create");
            return result;
        };
    }

    private IRunUtil mockAcloudCreate(
            Answer<CommandResult> answer,
            ArgumentCaptor<String> reportFile,
            ArgumentCaptor<String> hostPackageDir,
            ArgumentCaptor<String> imageDir,
            ArgumentCaptor<String> instanceDir) {
        IRunUtil runUtil = mock(IRunUtil.class);
        when(runUtil.runTimedCmd(
                        eq(ACLOUD_TIMEOUT),
                        eq(mAcloud.getAbsolutePath()),
                        eq("create"),
                        eq("--local-instance"),
                        eq(Integer.toString(DEVICE_NUM_OFFSET + 1)),
                        eq("--local-instance-dir"),
                        instanceDir.capture(),
                        eq("--report_file"),
                        reportFile.capture(),
                        eq("--no-autoconnect"),
                        eq("--yes"),
                        eq("--skip-pre-run-check"),
                        eq("-vv"),
                        eq("--local-image"),
                        imageDir.capture(),
                        eq("--local-tool"),
                        hostPackageDir.capture(),
                        eq("-test")))
                .thenAnswer(answer);

        return runUtil;
    }

    private IRunUtil mockAcloudCreateWithExtraDirs(
            Answer<CommandResult> answer,
            ArgumentCaptor<String> reportFile,
            ArgumentCaptor<String> hostPackageDir,
            ArgumentCaptor<String> imageDir,
            ArgumentCaptor<String> instanceDir,
            ArgumentCaptor<String> bootImageDir,
            ArgumentCaptor<String> systemImageDir,
            ArgumentCaptor<String> otaToolsDir) {
        mLocalAvd.getOptions().getGceDriverFileParams().put("test-file", new File("/test/file"));
        IRunUtil runUtil = mock(IRunUtil.class);
        when(runUtil.runTimedCmd(
                        eq(ACLOUD_TIMEOUT),
                        eq(mAcloud.getAbsolutePath()),
                        eq("create"),
                        eq("--local-instance"),
                        eq(Integer.toString(DEVICE_NUM_OFFSET + 1)),
                        eq("--local-instance-dir"),
                        instanceDir.capture(),
                        eq("--report_file"),
                        reportFile.capture(),
                        eq("--no-autoconnect"),
                        eq("--yes"),
                        eq("--skip-pre-run-check"),
                        eq("-vv"),
                        eq("--local-image"),
                        imageDir.capture(),
                        eq("--local-tool"),
                        hostPackageDir.capture(),
                        eq("--local-boot-image"),
                        bootImageDir.capture(),
                        eq("--local-system-image"),
                        systemImageDir.capture(),
                        eq("--local-tool"),
                        otaToolsDir.capture(),
                        eq("--test-file"),
                        eq("/test/file"),
                        eq("-test")))
                .thenAnswer(answer);

        return runUtil;
    }

    private IRunUtil mockAcloudDelete(CommandStatus status) {
        IRunUtil runUtil = mock(IRunUtil.class);
        CommandResult result = new CommandResult(status);
        result.setStderr("acloud delete");
        result.setStdout("acloud delete");
        when(runUtil.runTimedCmd(
                        eq(ACLOUD_TIMEOUT),
                        eq(mAcloud.getAbsolutePath()),
                        eq("delete"),
                        eq("--local-only"),
                        eq("--instance-names"),
                        eq(INSTANCE_NAME),
                        eq("-vv")))
                .thenReturn(result);

        return runUtil;
    }

    private void assertFinalDeviceState(IDevice device) {
        Assert.assertTrue(StubLocalAndroidVirtualDevice.class.equals(device.getClass()));
        StubLocalAndroidVirtualDevice stubDevice = (StubLocalAndroidVirtualDevice) device;
        Assert.assertEquals(STUB_SERIAL_NUMBER, stubDevice.getSerialNumber());
        Assert.assertEquals(DEVICE_NUM_OFFSET, stubDevice.getDeviceNumOffset());
    }

    /**
     * Test that both {@link LocalAndroidVirtualDevice#preInvocationSetup(IBuildInfo,
     * List<IBuildInfo>)} and {@link LocalAndroidVirtualDevice#postInvocationTearDown(Throwable)}
     * succeed.
     */
    @Test
    public void testPreinvocationSetupSuccess()
            throws DeviceNotAvailableException, IOException, TargetSetupError {
        setUpExtraZips();

        ArgumentCaptor<String> reportFile = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hostPackageDir = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> imageDir = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> instanceDir = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bootImageDir = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> systemImageDir = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> otaToolsDir = ArgumentCaptor.forClass(String.class);
        IRunUtil acloudCreateRunUtil =
                mockAcloudCreateWithExtraDirs(
                        writeToReportFile(CommandStatus.SUCCESS, SUCCESS_REPORT_FORMAT),
                        reportFile,
                        hostPackageDir,
                        imageDir,
                        instanceDir,
                        bootImageDir,
                        systemImageDir,
                        otaToolsDir);
        // Map the names shown in error message to the captured arguments.
        Map<String, ArgumentCaptor<String>> captureDirs = new HashMap<>();
        captureDirs.put("hostPackageDir", hostPackageDir);
        captureDirs.put("imageDir", imageDir);
        captureDirs.put("instanceDir", instanceDir);
        captureDirs.put("bootImageDir", bootImageDir);
        captureDirs.put("systemImageDir", systemImageDir);
        captureDirs.put("otaToolsDir", otaToolsDir);

        IRunUtil acloudDeleteRunUtil = mockAcloudDelete(CommandStatus.SUCCESS);

        ITestLogger testLogger = mock(ITestLogger.class);

        // Test setUp.
        mLocalAvd.setTestLogger(testLogger);
        mLocalAvd.currentRunUtil = acloudCreateRunUtil;
        mLocalAvd.expectToConnect = true;
        mLocalAvd.preInvocationSetup(mMockBuildInfo, null);

        Assert.assertEquals(ONLINE_SERIAL_NUMBER, mLocalAvd.getIDevice().getSerialNumber());
        verify(acloudCreateRunUtil).setEnvVariable(eq("TMPDIR"), any());

        for (Map.Entry<String, ArgumentCaptor<String>> entry : captureDirs.entrySet()) {
            File capturedDir = new File(entry.getValue().getValue());
            Assert.assertTrue(entry.getKey() + " is not a directory.", capturedDir.isDirectory());
        }

        // Test tearDown.
        mLocalAvd.currentRunUtil = acloudDeleteRunUtil;
        mLocalAvd.expectToConnect = false;
        mLocalAvd.postInvocationTearDown(null);

        assertFinalDeviceState(mLocalAvd.getIDevice());
        verify(acloudDeleteRunUtil).setEnvVariable(eq("TMPDIR"), any());
        verify(testLogger).testLog(eq("kernel.1.log"), eq(LogDataType.KERNEL_LOG), any());

        Assert.assertFalse(new File(reportFile.getValue()).exists());
        for (Map.Entry<String, ArgumentCaptor<String>> entry : captureDirs.entrySet()) {
            File capturedDir = new File(entry.getValue().getValue());
            Assert.assertFalse(entry.getKey() + " is not deleted.", capturedDir.exists());
        }
    }

    /** Test shutting down the device during the invocation. */
    @Test
    public void testShutdown() throws DeviceNotAvailableException, TargetSetupError {
        ArgumentCaptor<String> reportFile = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hostPackageDir = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> imageDir = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> instanceDir = ArgumentCaptor.forClass(String.class);
        IRunUtil acloudCreateRunUtil =
                mockAcloudCreate(
                        writeToReportFile(CommandStatus.SUCCESS, SUCCESS_REPORT_FORMAT),
                        reportFile,
                        hostPackageDir,
                        imageDir,
                        instanceDir);

        IRunUtil acloudDeleteRunUtil = mockAcloudDelete(CommandStatus.SUCCESS);

        ITestLogger testLogger = mock(ITestLogger.class);

        // Test setUp.
        mLocalAvd.setTestLogger(testLogger);
        mLocalAvd.currentRunUtil = acloudCreateRunUtil;
        mLocalAvd.expectToConnect = true;
        mLocalAvd.preInvocationSetup(mMockBuildInfo, null);

        Assert.assertEquals(ONLINE_SERIAL_NUMBER, mLocalAvd.getIDevice().getSerialNumber());
        verify(acloudCreateRunUtil).setEnvVariable(eq("TMPDIR"), any());

        File capturedHostPackageDir = new File(hostPackageDir.getValue());
        File capturedImageDir = new File(imageDir.getValue());
        File capturedInstanceDir = new File(instanceDir.getValue());
        Assert.assertTrue(capturedHostPackageDir.isDirectory());
        Assert.assertTrue(capturedImageDir.isDirectory());
        Assert.assertTrue(capturedInstanceDir.isDirectory());

        // Shutdown the device.
        mLocalAvd.currentRunUtil = acloudDeleteRunUtil;
        mLocalAvd.expectToConnect = false;
        mLocalAvd.shutdown();

        // Test that tearDown does not invoke acloud again.
        mLocalAvd.currentRunUtil = null;
        mLocalAvd.expectToConnect = false;
        mLocalAvd.postInvocationTearDown(null);

        assertFinalDeviceState(mLocalAvd.getIDevice());
        verify(acloudDeleteRunUtil).setEnvVariable(eq("TMPDIR"), any());
        verify(testLogger).testLog(eq("kernel.1.log"), eq(LogDataType.KERNEL_LOG), any());

        Assert.assertFalse(new File(reportFile.getValue()).exists());
        Assert.assertFalse(capturedHostPackageDir.exists());
        Assert.assertFalse(capturedImageDir.exists());
        Assert.assertFalse(capturedInstanceDir.exists());
    }

    /** Test that the acloud command reports failure. */
    @Test
    public void testPreInvocationSetupBootFailure() throws DeviceNotAvailableException {
        ArgumentCaptor<String> reportFile = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hostPackageDir = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> imageDir = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> instanceDir = ArgumentCaptor.forClass(String.class);
        IRunUtil acloudCreateRunUtil =
                mockAcloudCreate(
                        writeToReportFile(CommandStatus.SUCCESS, FAILURE_REPORT_FORMAT),
                        reportFile,
                        hostPackageDir,
                        imageDir,
                        instanceDir);

        IRunUtil acloudDeleteRunUtil = mockAcloudDelete(CommandStatus.FAILED);

        ITestLogger testLogger = mock(ITestLogger.class);

        // Test setUp.
        TargetSetupError expectedException = null;
        mLocalAvd.setTestLogger(testLogger);
        mLocalAvd.currentRunUtil = acloudCreateRunUtil;
        try {
            mLocalAvd.preInvocationSetup(mMockBuildInfo, null);
            Assert.fail("TargetSetupError is not thrown");
        } catch (TargetSetupError e) {
            expectedException = e;
        }

        Assert.assertEquals(STUB_SERIAL_NUMBER, mLocalAvd.getIDevice().getSerialNumber());

        File capturedHostPackageDir = new File(hostPackageDir.getValue());
        File capturedImageDir = new File(imageDir.getValue());
        File capturedInstanceDir = new File(instanceDir.getValue());
        Assert.assertTrue(capturedHostPackageDir.isDirectory());
        Assert.assertTrue(capturedImageDir.isDirectory());
        Assert.assertTrue(capturedInstanceDir.isDirectory());

        // Test tearDown.
        mLocalAvd.currentRunUtil = acloudDeleteRunUtil;
        mLocalAvd.postInvocationTearDown(expectedException);

        assertFinalDeviceState(mLocalAvd.getIDevice());
        verify(testLogger).testLog(eq("kernel.1.log"), eq(LogDataType.KERNEL_LOG), any());

        Assert.assertFalse(new File(reportFile.getValue()).exists());
        Assert.assertFalse(capturedHostPackageDir.exists());
        Assert.assertFalse(capturedImageDir.exists());
        Assert.assertFalse(capturedInstanceDir.exists());
    }

    /** Test that the acloud command fails, and the report is empty. */
    @Test
    public void testPreInvocationSetupFailure() throws DeviceNotAvailableException {
        ArgumentCaptor<String> reportFile = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hostPackageDir = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> imageDir = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> instanceDir = ArgumentCaptor.forClass(String.class);
        IRunUtil acloudCreateRunUtil =
                mockAcloudCreate(
                        writeToReportFile(CommandStatus.FAILED, ""),
                        reportFile,
                        hostPackageDir,
                        imageDir,
                        instanceDir);

        ITestLogger testLogger = mock(ITestLogger.class);

        // Test setUp.
        TargetSetupError expectedException = null;
        mLocalAvd.setTestLogger(testLogger);
        mLocalAvd.currentRunUtil = acloudCreateRunUtil;
        try {
            mLocalAvd.preInvocationSetup(mMockBuildInfo, null);
            Assert.fail("TargetSetupError is not thrown");
        } catch (TargetSetupError e) {
            expectedException = e;
        }

        Assert.assertEquals(STUB_SERIAL_NUMBER, mLocalAvd.getIDevice().getSerialNumber());

        File capturedHostPackageDir = new File(hostPackageDir.getValue());
        File capturedImageDir = new File(imageDir.getValue());
        File capturedInstanceDir = new File(instanceDir.getValue());
        Assert.assertTrue(capturedHostPackageDir.isDirectory());
        Assert.assertTrue(capturedImageDir.isDirectory());
        Assert.assertTrue(capturedInstanceDir.isDirectory());

        // Test tearDown.
        mLocalAvd.currentRunUtil = null;
        mLocalAvd.postInvocationTearDown(expectedException);

        assertFinalDeviceState(mLocalAvd.getIDevice());

        Assert.assertFalse(new File(reportFile.getValue()).exists());
        Assert.assertFalse(capturedHostPackageDir.exists());
        Assert.assertFalse(capturedImageDir.exists());
        Assert.assertFalse(capturedInstanceDir.exists());
    }
}
