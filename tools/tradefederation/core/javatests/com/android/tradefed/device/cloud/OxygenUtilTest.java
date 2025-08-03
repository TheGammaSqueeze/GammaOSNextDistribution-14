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
package com.android.tradefed.device.cloud;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.GCSFileDownloader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.util.List;

/** Unit tests for {@link OxygenUtil}. */
@RunWith(JUnit4.class)
public class OxygenUtilTest {

    /** Test downloadLaunchFailureLogs. */
    @Test
    public void testDownloadLaunchFailureLogs() throws Exception {
        ITestLogger logger = Mockito.mock(ITestLogger.class);
        GCSFileDownloader downloader = Mockito.mock(GCSFileDownloader.class);
        final String error =
                "Device launcher failed, check out logs for more details: \n"
                    + "some error:"
                    + " https://domain.name/storage/browser/bucket_name/instance_name?&project=project_name\n"
                    + "\tat leaseDevice\n"
                    + "\tat ";
        final String expectedUrl = "gs://bucket_name/instance_name";
        TargetSetupError setupError =
                new TargetSetupError(
                        "some error",
                        new Exception(error),
                        DeviceErrorIdentifier.FAILED_TO_LAUNCH_GCE);

        File tmpDir = null;
        try {
            tmpDir = FileUtil.createTempDir("oxygen");
            File file1 = FileUtil.createTempFile("kernel", ".log", tmpDir);
            File tmpDir2 = FileUtil.createTempDir("dir", tmpDir);
            File file2 = FileUtil.createTempFile("file2", ".txt", tmpDir2);
            when(downloader.downloadFile(expectedUrl)).thenReturn(tmpDir);

            OxygenUtil util = new OxygenUtil(downloader);
            util.downloadLaunchFailureLogs(setupError, logger);

            verify(logger, times(1)).testLog(Mockito.any(), eq(LogDataType.KERNEL_LOG), Mockito.any());
            verify(logger, times(1))
                .testLog(Mockito.any(), eq(LogDataType.CUTTLEFISH_LOG), Mockito.any());
        } finally {
            FileUtil.recursiveDelete(tmpDir);
        }
    }

    /** Test getDefaultLogType. */
    @Test
    public void testGetDefaultLogType() {
        assertEquals(OxygenUtil.getDefaultLogType("logcat_1234567.txt"), LogDataType.LOGCAT);
        assertEquals(OxygenUtil.getDefaultLogType("kernel.log_12345.txt"), LogDataType.KERNEL_LOG);
        assertEquals(
                OxygenUtil.getDefaultLogType("invocation_ended_bugreport_123456.zip"),
                LogDataType.BUGREPORTZ);
        assertEquals(
                OxygenUtil.getDefaultLogType("invocation_started_bugreport_123456.txt"),
                LogDataType.BUGREPORT);
    }

    /** Test collectErrorSignatures. */
    @Test
    public void testCollectErrorSignatures() throws Exception {
        File tmpDir = null;
        try {
            tmpDir = FileUtil.createTempDir("logs");
            File file1 = FileUtil.createTempFile("launcher.log", ".randomstring", tmpDir);
            String content =
                    "some content\n"
                            + "some Address already in use\n"
                            + "some vcpu hw run failure: 0x7.\n"
                            + "tailing string";
            FileUtil.writeToFile(content, file1);
            List<String> signatures = OxygenUtil.collectErrorSignatures(tmpDir);
            assertEquals("crosvm_vcpu_hw_run_failure_7", signatures.get(0));
            assertEquals("launch_cvd_port_collision", signatures.get(1));
        } finally {
            FileUtil.recursiveDelete(tmpDir);
        }
    }

    /**
     * Test collectDeviceLaunchMetrics before introducing cuttlefish-host-resources and
     * cuttlefish-operator replacing cuttlefish-common.
     */
    @Test
    public void testCollectDeviceLaunchMetrics() throws Exception {
        File tmpDir = null;
        try {
            tmpDir = FileUtil.createTempDir("logs");
            File file1 = FileUtil.createTempFile("vdl_stdout.txt", ".randomstring", tmpDir);
            String content =
                    "some content\n2023/02/09 21:25:25 launch_cvd exited."
                            + "2023/02/09 21:25:30   Ended At  | Duration | Event Name\n"
                            + "2023/02/09 21:25:30      62.21  |    0.00  | SetupDependencies\n"
                            + "2023/02/09 21:25:30      62.55  |    0.33  | CuttlefishCommon\n"
                            + "2023/02/09 21:25:30     186.84  |  124.63  | LaunchDevice\n"
                            + "2023/02/09 21:25:30     186.84  |  186.84  |"
                            + " CuttlefishLauncherMainstart\n"
                            + "tailing string";
            FileUtil.writeToFile(content, file1);
            long[] metrics = OxygenUtil.collectDeviceLaunchMetrics(tmpDir);
            assertEquals(61880, metrics[0]);
            assertEquals(124630, metrics[1]);

        } finally {
            FileUtil.recursiveDelete(tmpDir);
        }
    }

    /**
     * Test collectDeviceLaunchMetrics after replacing cuttlefish-common into
     * cuttlefish-host-resources and cuttlefish-operator.
     */
    @Test
    public void testCollectDeviceLaunchMetricsV2() throws Exception {
        File tmpDir = null;
        try {
            tmpDir = FileUtil.createTempDir("logs");
            File file1 = FileUtil.createTempFile("vdl_stdout.txt", ".randomstring", tmpDir);
            String content =
                    "some content\n"
                        + "2023/12/01 12:12:00 launch_cvd exited.2023/12/01 12:12:12   Ended At  |"
                        + " Duration | Event Name\n"
                        + "2023/12/01 12:12:12      50.50  |    0.00  | SetupDependencies\n"
                        + "2023/12/01 12:12:12      51.51  |    1.01  | CuttlefishHostResources\n"
                        + "2023/12/01 12:12:12      52.52  |    2.02  | CuttlefishOperator\n"
                        + "2023/12/01 12:12:12     131.30  |   80.80  | LaunchDevice\n"
                        + "2023/12/01 12:12:12     131.30  |  131.30  |"
                        + " CuttlefishLauncherMainstart\n"
                        + "tailing string";
            FileUtil.writeToFile(content, file1);
            long[] metrics = OxygenUtil.collectDeviceLaunchMetrics(tmpDir);
            assertEquals(47470, metrics[0]);
            assertEquals(80800, metrics[1]);

        } finally {
            FileUtil.recursiveDelete(tmpDir);
        }
    }

    @Test
    public void testCollectOxygenVersion() throws Exception {
        File tmpDir = null;
        try {
            tmpDir = FileUtil.createTempDir("logs");
            File file1 = FileUtil.createTempFile("oxygen_version.txt", "", tmpDir);
            String content = "version_number \n\n\n";
            FileUtil.writeToFile(content, file1);
            assertEquals("version_number", OxygenUtil.collectOxygenVersion(tmpDir));

        } finally {
            FileUtil.recursiveDelete(tmpDir);
        }
    }

    @Test
    public void testGetTargetRegion_WithExplicitRegion() throws Exception {
        TestDeviceOptions deviceOptions = new TestDeviceOptions();
        OptionSetter setter = new OptionSetter(deviceOptions);
        setter.setOptionValue("oxygen-target-region", "us-east");
        String targetRegion = OxygenUtil.getTargetRegion(deviceOptions);
        assertEquals("us-east", targetRegion);
    }

    @Test
    public void testGetRegionFromZoneMeta() throws Exception {
        assertEquals(
                "us-west12", OxygenUtil.getRegionFromZoneMeta("projects/12345/zones/us-west12-a"));
    }
}
