/*
 * Copyright (C) 2018 The Android Open Source Project
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.device.cloud.GceAvdInfo.GceStatus;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.MultiMap;

import com.google.common.net.HostAndPort;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Unit tests for {@link GceManager} */
@RunWith(JUnit4.class)
public class GceManagerTest {

    private GceManager mGceManager;
    @Mock DeviceDescriptor mMockDeviceDesc;
    private TestDeviceOptions mOptions;
    private IBuildInfo mMockBuildInfo;
    @Mock IRunUtil mMockRunUtil;
    private File mAvdBinary;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mMockBuildInfo = new BuildInfo();
        mOptions = new TestDeviceOptions();
        OptionSetter setter = new OptionSetter(mOptions);
        setter.setOptionValue("wait-gce-teardown", "true");
        setter.setOptionValue("invocation-attribute-to-metadata", "foo");
        mAvdBinary = FileUtil.createTempFile("acloud", ".par");
        mAvdBinary.setExecutable(true);
        mOptions.setAvdDriverBinary(mAvdBinary);
        mOptions.setAvdConfigFile(mAvdBinary);
        mGceManager =
                new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo) {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }
                };
    }

    @After
    public void tearDown() {
        FileUtil.deleteFile(mAvdBinary);
    }

    /** Test {@link GceManager#extractInstanceName(String)} with a typical gce log. */
    @Test
    public void testExtractNameFromLog() {
        String log =
                "2016-09-13 00:05:08,261 |INFO| gcompute_client:283| "
                        + "Image image-gce-x86-phone-userdebug-fastbuild-linux-3266697-1f7cc554 "
                        + "has been created.\n2016-09-13 00:05:08,261 |INFO| gstorage_client:102| "
                        + "Deleting file: bucket: android-artifacts-cache, object: 9a76b1f96c7e4da"
                        + "19b90b0c4e97f9450-avd-system.tar.gz\n2016-09-13 00:05:08,412 |INFO| gst"
                        + "orage_client:107| Deleted file: bucket: android-artifacts-cache, object"
                        + ": 9a76b1f96c7e4da19b90b0c4e97f9450-avd-system.tar.gz\n2016-09-13 00:05:"
                        + "09,331 |INFO| gcompute_client:728| Creating instance: project android-t"
                        + "reehugger, zone us-central1-f, body:{'networkInterfaces': [{'network': "
                        + "u'https://www.googleapis.com/compute/v1/projects/android-treehugger/glo"
                        + "bal/networks/default', 'accessConfigs': [{'type': 'ONE_TO_ONE_NAT', 'na"
                        + "me': 'External NAT'}]}], 'name': u'gce-x86-phone-userdebug-fastbuild-lin"
                        + "ux-3266697-144fcf59', 'serviceAccounts': [{'email': 'default', 'scopes'"
                        + ": ['https://www.googleapis.com/auth/devstorage.read_only', 'https://www"
                        + ".googleapis.com/auth/logging.write']}], 'disks': [{'autoDelete': True, "
                        + "'boot': True, 'mode': 'READ_WRITE', 'initializeParams': {'diskName': 'g"
                        + "ce-x86-phone-userdebug-fastbuild-linux-3266697-144fcf59', 'sourceImage'"
                        + ": u'https://www.googleapis.com/compute/v1/projects/a";
        String result = mGceManager.extractInstanceName(log);
        assertEquals("gce-x86-phone-userdebug-fastbuild-linux-3266697-144fcf59", result);
    }

    /** Test {@link GceManager#extractInstanceName(String)} with a typical gce log. */
    @Test
    public void testExtractNameFromLog_newFormat() {
        String log =
                "2016-09-20 08:11:02,287 |INFO| gcompute_client:728| Creating instance: "
                        + "project android-treehugger, zone us-central1-f, body:{'name': "
                        + "'ins-80bd5bd1-3708674-gce-x86-phone-userdebug-fastbuild3c-linux', "
                        + "'disks': [{'autoDelete': True, 'boot': True, 'mode': 'READ_WRITE', "
                        + "'initializeParams': {'diskName': 'gce-x86-phone-userdebug-fastbuild-"
                        + "linux-3286354-eb1fd2e3', 'sourceImage': u'https://www.googleapis.com"
                        + "compute/v1/projects/android-treehugger/global/images/image-gce-x86-ph"
                        + "one-userdebug-fastbuild-linux-3286354-b6b99338'}, 'type': 'PERSISTENT'"
                        + "}, {'autoDelete': True, 'deviceName': 'gce-x86-phone-userdebug-fastbuil"
                        + "d-linux-3286354-eb1fd2e3-data', 'interface': 'SCSI', 'mode': 'READ_WRI"
                        + "TE', 'type': 'PERSISTENT', 'boot': False, 'source': u'projects/andro"
                        + "id-treehugger/zones/us-c}]}";
        String result = mGceManager.extractInstanceName(log);
        assertEquals("ins-80bd5bd1-3708674-gce-x86-phone-userdebug-fastbuild3c-linux", result);
    }

    /**
     * Test {@link GceManager#extractInstanceName(String)} with a log that does not contains the gce
     * name.
     */
    @Test
    public void testExtractNameFromLog_notfound() {
        String log =
                "2016-09-20 08:11:02,287 |INFO| gcompute_client:728| Creating instance: "
                        + "project android-treehugger, zone us-central1-f, body:{'name': "
                        + "'name-80bd5bd1-3708674-gce-x86-phone-userdebug-fastbuild3c-linux',"
                        + "[{'autoDelete': True, 'boot': True, 'mode': 'READ_WRITE', 'initia "
                        + "{'diskName': 'gce-x86-phone-userdebug-fastbuild-linux-3286354-eb1 "
                        + "'sourceImage': u'https://www.googleapis.com/compute/v1/projects/an"
                        + "treehugger/global/images/image-gce-x86-phone-userdebug-fastbuild-g";
        String result = mGceManager.extractInstanceName(log);
        assertNull(result);
    }

    /** Test {@link GceManager#buildGceCmd(File, IBuildInfo, String)}. */
    @Test
    public void testBuildGceCommand() throws IOException {
        IBuildInfo mockBuildInfo = mock(IBuildInfo.class);
        when(mockBuildInfo.getBuildAttributes()).thenReturn(Collections.<String, String>emptyMap());
        when(mockBuildInfo.getBuildFlavor()).thenReturn("FLAVOR");
        when(mockBuildInfo.getBuildBranch()).thenReturn("BRANCH");
        when(mockBuildInfo.getBuildId()).thenReturn("BUILDID");

        MultiMap<String, String> stubAttributes = new MultiMap<>();
        stubAttributes.put("foo", "bar");
        File reportFile = null;
        try {
            reportFile = FileUtil.createTempFile("test-gce-cmd", "report");
            List<String> result =
                    mGceManager.buildGceCmd(
                            reportFile, mockBuildInfo, null, null, null, stubAttributes);
            List<String> expected =
                    ArrayUtil.list(
                            mOptions.getAvdDriverBinary().getAbsolutePath(),
                            "create",
                            "--build-target",
                            "FLAVOR",
                            "--branch",
                            "BRANCH",
                            "--build-id",
                            "BUILDID",
                            "--gce-metadata",
                            "foo:bar",
                            "--config_file",
                            mGceManager.getAvdConfigFile().getAbsolutePath(),
                            "--report_file",
                            reportFile.getAbsolutePath(),
                            "-v");
            assertEquals(expected, result);
        } finally {
            FileUtil.deleteFile(reportFile);
        }
    }

    /** Test {@link GceManager#buildGceCmd(File, IBuildInfo, String)} with json key file set. */
    @Test
    public void testBuildGceCommand_withServiceAccountJsonKeyFile() throws Exception {
        IBuildInfo mMockBuildInfo = mock(IBuildInfo.class);
        when(mMockBuildInfo.getBuildAttributes())
                .thenReturn(Collections.<String, String>emptyMap());
        when(mMockBuildInfo.getBuildFlavor()).thenReturn("FLAVOR");
        when(mMockBuildInfo.getBuildBranch()).thenReturn("BRANCH");
        when(mMockBuildInfo.getBuildId()).thenReturn("BUILDID");

        File reportFile = null;
        OptionSetter setter = new OptionSetter(mOptions);
        setter.setOptionValue("gce-driver-service-account-json-key-path", "/path/to/key.json");
        try {
            reportFile = FileUtil.createTempFile("test-gce-cmd", "report");
            List<String> result =
                    mGceManager.buildGceCmd(reportFile, mMockBuildInfo, null, null, null, null);
            List<String> expected =
                    ArrayUtil.list(
                            mOptions.getAvdDriverBinary().getAbsolutePath(),
                            "create",
                            "--build-target",
                            "FLAVOR",
                            "--branch",
                            "BRANCH",
                            "--build-id",
                            "BUILDID",
                            "--config_file",
                            mGceManager.getAvdConfigFile().getAbsolutePath(),
                            "--service-account-json-private-key-path",
                            "/path/to/key.json",
                            "--report_file",
                            reportFile.getAbsolutePath(),
                            "-v");
            assertEquals(expected, result);
        } finally {
            FileUtil.deleteFile(reportFile);
        }
    }

    /** Test {@link GceManager#buildGceCmd(File, IBuildInfo, String)} with IP based device. */
    @Test
    public void testBuildGceCommandWithIpDevice() throws Exception {
        IBuildInfo mMockBuildInfo = mock(IBuildInfo.class);
        when(mMockBuildInfo.getBuildAttributes())
                .thenReturn(Collections.<String, String>emptyMap());
        when(mMockBuildInfo.getBuildFlavor()).thenReturn("FLAVOR");
        when(mMockBuildInfo.getBuildBranch()).thenReturn("BRANCH");
        when(mMockBuildInfo.getBuildId()).thenReturn("BUILDID");

        File reportFile = null;
        OptionSetter setter = new OptionSetter(mOptions);
        setter.setOptionValue("gce-driver-service-account-json-key-path", "/path/to/key.json");
        setter.setOptionValue("gce-private-key-path", "/path/to/id_rsa");
        setter.setOptionValue("instance-user", "foo");
        try {
            reportFile = FileUtil.createTempFile("test-gce-cmd", "report");
            List<String> result =
                    mGceManager.buildGceCmd(reportFile, mMockBuildInfo, "bar", null, null, null);
            List<String> expected =
                    ArrayUtil.list(
                            mOptions.getAvdDriverBinary().getAbsolutePath(),
                            "create",
                            "--build-target",
                            "FLAVOR",
                            "--branch",
                            "BRANCH",
                            "--build-id",
                            "BUILDID",
                            "--config_file",
                            mGceManager.getAvdConfigFile().getAbsolutePath(),
                            "--service-account-json-private-key-path",
                            "/path/to/key.json",
                            "--host",
                            "bar",
                            "--host-user",
                            "foo",
                            "--host-ssh-private-key-path",
                            "/path/to/id_rsa",
                            "--report_file",
                            reportFile.getAbsolutePath(),
                            "-v");
            assertEquals(expected, result);
        } finally {
            FileUtil.deleteFile(reportFile);
        }
    }

    /** Test {@link GceManager#buildGceCmd(File, IBuildInfo, String)}. */
    @Test
    public void testBuildGceCommandWithEmulatorBuild() throws Exception {
        IBuildInfo mMockBuildInfo = mock(IBuildInfo.class);
        when(mMockBuildInfo.getBuildAttributes())
                .thenReturn(Collections.<String, String>emptyMap());
        when(mMockBuildInfo.getBuildFlavor()).thenReturn("TARGET");
        when(mMockBuildInfo.getBuildBranch()).thenReturn("BRANCH");
        when(mMockBuildInfo.getBuildId()).thenReturn("BUILDID");

        File reportFile = null;

        try {
            OptionSetter setter = new OptionSetter(mOptions);
            setter.setOptionValue("gce-driver-param", "--emulator-build-id");
            setter.setOptionValue("gce-driver-param", "EMULATOR_BUILD_ID");
            mGceManager =
                    new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo) {
                        @Override
                        IRunUtil getRunUtil() {
                            return mMockRunUtil;
                        }
                    };
            reportFile = FileUtil.createTempFile("test-gce-cmd", "report");
            List<String> result =
                    mGceManager.buildGceCmd(reportFile, mMockBuildInfo, null, null, null, null);
            List<String> expected =
                    ArrayUtil.list(
                            mOptions.getAvdDriverBinary().getAbsolutePath(),
                            "create",
                            "--build-target",
                            "TARGET",
                            "--branch",
                            "BRANCH",
                            "--build-id",
                            "BUILDID",
                            "--emulator-build-id",
                            "EMULATOR_BUILD_ID",
                            "--config_file",
                            mGceManager.getAvdConfigFile().getAbsolutePath(),
                            "--report_file",
                            reportFile.getAbsolutePath(),
                            "-v");
            assertEquals(expected, result);
        } finally {
            FileUtil.deleteFile(reportFile);
        }
    }

    /** Test {@link GceManager#buildGceCmd(File, IBuildInfo, String)}. */
    @Test
    public void testBuildGceCommandWithSpecifiedImages() throws Exception {
        IBuildInfo mMockBuildInfo = mock(IBuildInfo.class);
        when(mMockBuildInfo.getBuildAttributes())
                .thenReturn(Collections.<String, String>emptyMap());
        when(mMockBuildInfo.getBuildFlavor()).thenReturn("TARGET");
        when(mMockBuildInfo.getBuildBranch()).thenReturn("BRANCH");
        when(mMockBuildInfo.getBuildId()).thenReturn("BUILDID");

        File reportFile = null;

        try {
            OptionSetter setter = new OptionSetter(mOptions);
            setter.setOptionValue(
                    "gce-driver-file-param",
                    "cvd-host-package",
                    "/path/to/cvd-host-package.tar.gz");
            setter.setOptionValue(
                    "gce-driver-file-param",
                    "local-image",
                    "/path/to/cvd-cuttlefish-android-os.tar.gz");
            mGceManager =
                    new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo) {
                        @Override
                        IRunUtil getRunUtil() {
                            return mMockRunUtil;
                        }
                    };
            reportFile = FileUtil.createTempFile("test-gce-cmd", "report");
            List<String> result =
                    mGceManager.buildGceCmd(reportFile, mMockBuildInfo, null, null, null, null);
            List<String> expected =
                    ArrayUtil.list(
                            mOptions.getAvdDriverBinary().getAbsolutePath(),
                            "create",
                            "--cvd-host-package",
                            "/path/to/cvd-host-package.tar.gz",
                            "--local-image",
                            "/path/to/cvd-cuttlefish-android-os.tar.gz",
                            "--config_file",
                            mGceManager.getAvdConfigFile().getAbsolutePath(),
                            "--report_file",
                            reportFile.getAbsolutePath(),
                            "-v");
            assertEquals(expected, result);
        } finally {
            FileUtil.deleteFile(reportFile);
        }
    }

    /**
     * Test {@link GceManager#buildGceCmd(File, IBuildInfo, String, String, Integer,
     * MultiMap<String, String>)} with preconfigured virtual device.
     */
    @Test
    public void testBuildGceCommand_withPreconfiguredVirtualDevice() throws Exception {
        IBuildInfo mMockBuildInfo = mock(IBuildInfo.class);
        when(mMockBuildInfo.getBuildAttributes())
                .thenReturn(Collections.<String, String>emptyMap());
        when(mMockBuildInfo.getBuildFlavor()).thenReturn("FLAVOR");
        when(mMockBuildInfo.getBuildBranch()).thenReturn("BRANCH");
        when(mMockBuildInfo.getBuildId()).thenReturn("BUILDID");

        File reportFile = null;
        OptionSetter setter = new OptionSetter(mOptions);
        setter.setOptionValue("gce-driver-service-account-json-key-path", "/path/to/key.json");
        setter.setOptionValue("gce-private-key-path", "/path/to/id_rsa");

        try {
            reportFile = FileUtil.createTempFile("test-gce-cmd", "report");
            List<String> result =
                    mGceManager.buildGceCmd(reportFile, mMockBuildInfo, "bar", "vsoc-1", 2, null);
            List<String> expected =
                    ArrayUtil.list(
                            mOptions.getAvdDriverBinary().getAbsolutePath(),
                            "create",
                            "--build-target",
                            "FLAVOR",
                            "--branch",
                            "BRANCH",
                            "--build-id",
                            "BUILDID",
                            "--config_file",
                            mGceManager.getAvdConfigFile().getAbsolutePath(),
                            "--service-account-json-private-key-path",
                            "/path/to/key.json",
                            "--host",
                            "bar",
                            "--host-user",
                            "vsoc-1",
                            "--host-ssh-private-key-path",
                            "/path/to/id_rsa",
                            "--report_file",
                            reportFile.getAbsolutePath(),
                            "--base-instance-num",
                            "3",
                            "-v");
            assertEquals(expected, result);
        } finally {
            FileUtil.deleteFile(reportFile);
        }
    }

    /** Test {@link GceManager#buildGceCmd(File, IBuildInfo, String)}. */
    @Test
    public void testBuildGceCommandWithGceDriverParam() throws Exception {
        IBuildInfo mMockBuildInfo = mock(IBuildInfo.class);
        when(mMockBuildInfo.getBuildAttributes())
                .thenReturn(Collections.<String, String>emptyMap());
        when(mMockBuildInfo.getBuildFlavor()).thenReturn("FLAVOR");
        when(mMockBuildInfo.getBuildBranch()).thenReturn("BRANCH");
        when(mMockBuildInfo.getBuildId()).thenReturn("BUILDID");

        File reportFile = null;
        OptionSetter setter = new OptionSetter(mOptions);
        setter.setOptionValue("gce-driver-param", "--report-internal-ip");
        setter.setOptionValue("gce-driver-param", "--no-autoconnect");
        try {
            reportFile = FileUtil.createTempFile("test-gce-cmd", "report");
            List<String> result =
                    mGceManager.buildGceCmd(reportFile, mMockBuildInfo, null, null, null, null);
            List<String> expected =
                    ArrayUtil.list(
                            mOptions.getAvdDriverBinary().getAbsolutePath(),
                            "create",
                            "--build-target",
                            "FLAVOR",
                            "--branch",
                            "BRANCH",
                            "--build-id",
                            "BUILDID",
                            "--report-internal-ip",
                            "--no-autoconnect",
                            "--config_file",
                            mGceManager.getAvdConfigFile().getAbsolutePath(),
                            "--report_file",
                            reportFile.getAbsolutePath(),
                            "-v");
            assertEquals(expected, result);
        } finally {
            FileUtil.deleteFile(reportFile);
        }
    }

    /** Test {@link GceManager#buildGceCmd(File, IBuildInfo, String)}. */
    @Test
    public void testBuildGceCommandWithExtraFiles() throws Exception {
        IBuildInfo mMockBuildInfo = mock(IBuildInfo.class);
        when(mMockBuildInfo.getBuildAttributes())
                .thenReturn(Collections.<String, String>emptyMap());
        when(mMockBuildInfo.getBuildFlavor()).thenReturn("TARGET");
        when(mMockBuildInfo.getBuildBranch()).thenReturn("BRANCH");
        when(mMockBuildInfo.getBuildId()).thenReturn("BUILDID");

        File reportFile = null;
        MultiMap<File, String> extraFiles = new MultiMap<>();
        File file1 = FileUtil.createTempFile("test_file1", ".txt");
        File file2 = FileUtil.createTempFile("test_file2", ".txt");
        extraFiles.put(file1, "/home/vsoc-01/test_file1.txt");
        extraFiles.put(file2, "/home/vsoc-01/test_file2.txt");
        try {
            mOptions.setExtraFiles(extraFiles);
            mGceManager =
                    new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo) {
                        @Override
                        IRunUtil getRunUtil() {
                            return mMockRunUtil;
                        }
                    };
            reportFile = FileUtil.createTempFile("test-gce-cmd", "report");
            List<String> result =
                    mGceManager.buildGceCmd(reportFile, mMockBuildInfo, null, null, null, null);
            List<String> expected =
                    ArrayUtil.list(
                            mOptions.getAvdDriverBinary().getAbsolutePath(),
                            "create",
                            "--build-target",
                            "TARGET",
                            "--branch",
                            "BRANCH",
                            "--build-id",
                            "BUILDID",
                            "--extra-files",
                            file1.getAbsolutePath() + ",/home/vsoc-01/test_file1.txt",
                            file2.getAbsolutePath() + ",/home/vsoc-01/test_file2.txt",
                            "--config_file",
                            mGceManager.getAvdConfigFile().getAbsolutePath(),
                            "--report_file",
                            reportFile.getAbsolutePath(),
                            "-v");
            assertEquals(expected, result);
        } finally {
            FileUtil.deleteFile(reportFile);
            FileUtil.deleteFile(file1);
            FileUtil.deleteFile(file2);
            mOptions.setExtraFiles(new MultiMap<>());
        }
    }

    /** Ensure exception is thrown after a timeout from the acloud command. */
    @Test
    public void testStartGce_timeout() throws Exception {
        mOptions.getGceDriverParams().add("--boot-timeout");
        mOptions.getGceDriverParams().add("900");
        OptionSetter setter = new OptionSetter(mOptions);
        // Boot-time on Acloud params will be overridden by TF option.
        setter.setOptionValue("allow-gce-boot-timeout-override", "false");
        mGceManager =
                new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo) {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    protected List<String> buildGceCmd(
                            File reportFile,
                            IBuildInfo b,
                            String ipDevice,
                            String user,
                            Integer offset,
                            MultiMap<String, String> attributes) {
                        List<String> tmp = new ArrayList<String>();
                        tmp.add("");
                        return tmp;
                    }
                };
        final String expectedException =
                "acloud errors: timeout after 1620000ms, acloud did not return null";
        CommandResult cmd = new CommandResult();
        cmd.setStatus(CommandStatus.TIMED_OUT);
        cmd.setStdout("output err");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.eq(1800000L),
                        Mockito.any(),
                        Mockito.eq("--boot-timeout"),
                        Mockito.eq("1620")))
                .thenReturn(cmd);

        doReturn(null).when(mMockDeviceDesc).toString();
        try {
            mGceManager.startGce();
            fail("A TargetSetupError should have been thrown");
        } catch (TargetSetupError expected) {
            assertEquals(expectedException, expected.getMessage());
        }
    }

    /** Test {@link GceManager#buildGceCmd(File, IBuildInfo, String)}. */
    @Test
    public void testBuildGceCommandWithKernelBuild() throws Exception {
        IBuildInfo mMockBuildInfo = mock(IBuildInfo.class);
        when(mMockBuildInfo.getBuildAttributes())
                .thenReturn(Collections.<String, String>emptyMap());
        when(mMockBuildInfo.getBuildFlavor()).thenReturn("FLAVOR");
        when(mMockBuildInfo.getBuildBranch()).thenReturn("BRANCH");
        when(mMockBuildInfo.getBuildId()).thenReturn("BUILDID");

        File reportFile = null;
        try {
            OptionSetter setter = new OptionSetter(mOptions);
            setter.setOptionValue("gce-driver-param", "--kernel-build-id");
            setter.setOptionValue("gce-driver-param", "KERNELBUILDID");
            reportFile = FileUtil.createTempFile("test-gce-cmd", "report");
            List<String> result =
                    mGceManager.buildGceCmd(reportFile, mMockBuildInfo, null, null, null, null);
            List<String> expected =
                    ArrayUtil.list(
                            mOptions.getAvdDriverBinary().getAbsolutePath(),
                            "create",
                            "--build-target",
                            "FLAVOR",
                            "--branch",
                            "BRANCH",
                            "--build-id",
                            "BUILDID",
                            "--kernel-build-id",
                            "KERNELBUILDID",
                            "--config_file",
                            mGceManager.getAvdConfigFile().getAbsolutePath(),
                            "--report_file",
                            reportFile.getAbsolutePath(),
                            "-v");
            assertEquals(expected, result);
        } finally {
            FileUtil.deleteFile(reportFile);
        }
    }

    /**
     * Test that a {@link com.android.tradefed.device.cloud.GceAvdInfo} is properly created when the
     * output of acloud and runutil is fine.
     */
    @Test
    public void testStartGce() throws Exception {
        OptionSetter setter = new OptionSetter(mOptions);
        setter.setOptionValue("allow-gce-boot-timeout-override", "true");
        mGceManager =
                new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo) {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    protected List<String> buildGceCmd(
                            File reportFile,
                            IBuildInfo b,
                            String ipDevice,
                            String user,
                            Integer offset,
                            MultiMap<String, String> attributes) {
                        String valid =
                                " {\n"
                                        + "\"data\": {\n"
                                        + "\"devices\": [\n"
                                        + "{\n"
                                        + "\"ip\": \"104.154.62.236\",\n"
                                        + "\"instance_name\": \"gce-x86-phone-userdebug-22\"\n"
                                        + "}\n"
                                        + "]\n"
                                        + "},\n"
                                        + "\"errors\": [],\n"
                                        + "\"command\": \"create\",\n"
                                        + "\"status\": \"SUCCESS\"\n"
                                        + "}";
                        try {
                            FileUtil.writeToFile(valid, reportFile);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        List<String> tmp = new ArrayList<String>();
                        tmp.add("");
                        return tmp;
                    }
                };
        CommandResult cmd = new CommandResult();
        cmd.setStatus(CommandStatus.SUCCESS);
        cmd.setStdout("output");
        when(mMockRunUtil.runTimedCmd(Mockito.anyLong(), (String[]) Mockito.any())).thenReturn(cmd);

        GceAvdInfo res = mGceManager.startGce();

        assertNotNull(res);
        assertEquals(GceStatus.SUCCESS, res.getStatus());
    }

    /**
     * Test that in case of improper output from acloud we throw an exception since we could not get
     * the valid information we are looking for.
     */
    @Test
    public void testStartGce_failed() throws Exception {
        OptionSetter setter = new OptionSetter(mOptions);
        setter.setOptionValue("allow-gce-boot-timeout-override", "true");
        mGceManager =
                new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo) {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    protected List<String> buildGceCmd(
                            File reportFile,
                            IBuildInfo b,
                            String ipDevice,
                            String user,
                            Integer offset,
                            MultiMap<String, String> attributes) {
                        // We delete the potential report file to create an issue.
                        FileUtil.deleteFile(reportFile);
                        List<String> tmp = new ArrayList<String>();
                        tmp.add("");
                        return tmp;
                    }
                };
        CommandResult cmd = new CommandResult();
        cmd.setStatus(CommandStatus.FAILED);
        cmd.setStdout("output");
        when(mMockRunUtil.runTimedCmd(Mockito.anyLong(), (String[]) Mockito.any())).thenReturn(cmd);

        try {
            mGceManager.startGce();
            fail("Should have thrown an exception");
        } catch (TargetSetupError expected) {
        }
    }

    /**
     * Test that even in case of BOOT_FAIL if we can get some valid information about the GCE
     * instance, then we still return a GceAvdInfo to describe it.
     */
    @Test
    public void testStartGce_bootFail() throws Exception {
        OptionSetter setter = new OptionSetter(mOptions);
        setter.setOptionValue("allow-gce-boot-timeout-override", "true");
        mGceManager =
                new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo) {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    protected List<String> buildGceCmd(
                            File reportFile,
                            IBuildInfo b,
                            String ipDevice,
                            String user,
                            Integer offset,
                            MultiMap<String, String> attributes) {
                        String validFail =
                                " {\n"
                                        + "\"data\": {\n"
                                        + "\"devices_failing_boot\": [\n"
                                        + "{\n"
                                        + "\"ip\": \"104.154.62.236\",\n"
                                        + "\"instance_name\": \"ins-x86-phone-userdebug-229\"\n"
                                        + "}\n"
                                        + "]\n"
                                        + "},\n"
                                        + "\"errors\": [\"device did not boot\"],\n"
                                        + "\"command\": \"create\",\n"
                                        + "\"status\": \"BOOT_FAIL\"\n"
                                        + "}";
                        try {
                            FileUtil.writeToFile(validFail, reportFile);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        List<String> tmp = new ArrayList<String>();
                        tmp.add("");
                        return tmp;
                    }
                };
        CommandResult cmd = new CommandResult();
        cmd.setStatus(CommandStatus.FAILED);
        cmd.setStdout("output");
        when(mMockRunUtil.runTimedCmd(Mockito.anyLong(), (String[]) Mockito.any())).thenReturn(cmd);

        GceAvdInfo res = mGceManager.startGce();

        assertNotNull(res);
        assertEquals(GceStatus.BOOT_FAIL, res.getStatus());
    }

    /**
     * Test {@link GceManager#buildShutdownCommand(File, TestDeviceOptions, String, String,
     * boolean)}.
     */
    @Test
    public void testBuildShutdownCommand() {
        List<String> result =
                GceManager.buildShutdownCommand(
                        mGceManager.getAvdConfigFile(), mOptions, "instance1", null, false);
        List<String> expected =
                ArrayUtil.list(
                        mOptions.getAvdDriverBinary().getAbsolutePath(),
                        "delete",
                        "--config_file",
                        mGceManager.getAvdConfigFile().getAbsolutePath(),
                        "--instance_names",
                        "instance1");
        assertEquals(expected, result);
    }

    /**
     * Test {@link GceManager#buildShutdownCommand(File, TestDeviceOptions, String, String,
     * boolean)}.
     */
    @Test
    public void testBuildShutdownCommandWithJsonKeyFile() throws ConfigurationException {
        OptionSetter setter = new OptionSetter(mOptions);
        setter.setOptionValue("gce-driver-service-account-json-key-path", "/path/to/key.json");
        List<String> result =
                GceManager.buildShutdownCommand(
                        mGceManager.getAvdConfigFile(), mOptions, "instance1", null, false);
        List<String> expected =
                ArrayUtil.list(
                        mOptions.getAvdDriverBinary().getAbsolutePath(),
                        "delete",
                        "--service-account-json-private-key-path",
                        "/path/to/key.json",
                        "--config_file",
                        mGceManager.getAvdConfigFile().getAbsolutePath(),
                        "--instance_names",
                        "instance1");
        assertEquals(expected, result);
    }

    /**
     * Test {@link GceManager#buildShutdownCommand(File, TestDeviceOptions, String, String,
     * boolean)}.
     */
    @Test
    public void testBuildShutdownCommandWithIpDevice() throws ConfigurationException {
        OptionSetter setter = new OptionSetter(mOptions);
        setter.setOptionValue("gce-driver-service-account-json-key-path", "/path/to/key.json");
        setter.setOptionValue("gce-private-key-path", "/path/to/id_rsa");
        setter.setOptionValue("instance-user", "bar");
        List<String> result =
                GceManager.buildShutdownCommand(
                        mGceManager.getAvdConfigFile(), mOptions, "instance1", "foo", true);
        List<String> expected =
                ArrayUtil.list(
                        mOptions.getAvdDriverBinary().getAbsolutePath(),
                        "delete",
                        "--service-account-json-private-key-path",
                        "/path/to/key.json",
                        "--host",
                        "foo",
                        "--host-user",
                        "bar",
                        "--host-ssh-private-key-path",
                        "/path/to/id_rsa",
                        "--instance_names",
                        "instance1");
        assertEquals(expected, result);
    }

    /**
     * Test for {@link GceManager#shutdownGce() }.
     *
     * @throws Exception
     */
    @Test
    public void testShutdownGce() throws Exception {
        mGceManager =
                new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo, "instance1", "host1") {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }
                };
        mGceManager.startGce();
        CommandResult cmd = new CommandResult();
        cmd.setStatus(CommandStatus.SUCCESS);
        cmd.setStdout("output");
        when(mMockRunUtil.runTimedCmd(Mockito.anyLong(), (String[]) Mockito.any())).thenReturn(cmd);

        mGceManager.shutdownGce();

        // Attributes are marked when successful
        assertTrue(
                "build attribute did not contain " + GceManager.GCE_INSTANCE_CLEANED_KEY,
                mMockBuildInfo
                        .getBuildAttributes()
                        .containsKey(GceManager.GCE_INSTANCE_CLEANED_KEY));
    }

    @Test
    public void testShutdownGce_noWait() throws Exception {
        OptionSetter setter = new OptionSetter(mOptions);
        setter.setOptionValue("wait-gce-teardown", "false");
        mGceManager =
                new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo, "instance1", "host1") {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }
                };
        mGceManager.startGce();
        CommandResult cmd = new CommandResult();
        cmd.setStatus(CommandStatus.SUCCESS);
        cmd.setStdout("output");
        ArgumentCaptor<List<String>> capture = ArgumentCaptor.forClass(List.class);
        when(mMockRunUtil.runCmdInBackground(Mockito.eq(Redirect.DISCARD), capture.capture()))
                .thenReturn(Mockito.mock(Process.class));

        mGceManager.shutdownGce();

        List<String> args = capture.getValue();
        assertTrue(args.get(3).contains(mAvdBinary.getName()));
    }

    /** Test a success case for collecting the bugreport with ssh. */
    @Test
    public void testGetSshBugreport() throws Exception {
        GceAvdInfo fakeInfo = new GceAvdInfo("ins-gce", HostAndPort.fromHost("127.0.0.1"));
        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
        res.setStdout("bugreport success!\nOK:/bugreports/bugreport.zip\n");
        OutputStream stdout = null;
        OutputStream stderr = null;
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq(stdout),
                        Mockito.eq(stderr),
                        Mockito.eq("ssh"),
                        Mockito.eq("-o"),
                        Mockito.eq("LogLevel=ERROR"),
                        Mockito.eq("-o"),
                        Mockito.eq("UserKnownHostsFile=/dev/null"),
                        Mockito.eq("-o"),
                        Mockito.eq("StrictHostKeyChecking=no"),
                        Mockito.eq("-o"),
                        Mockito.eq("ServerAliveInterval=10"),
                        Mockito.eq("-i"),
                        Mockito.any(),
                        Mockito.eq("root@127.0.0.1"),
                        Mockito.eq("bugreportz")))
                .thenReturn(res);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("scp"),
                        Mockito.eq("-o"),
                        Mockito.eq("LogLevel=ERROR"),
                        Mockito.eq("-o"),
                        Mockito.eq("UserKnownHostsFile=/dev/null"),
                        Mockito.eq("-o"),
                        Mockito.eq("StrictHostKeyChecking=no"),
                        Mockito.eq("-o"),
                        Mockito.eq("ServerAliveInterval=10"),
                        Mockito.eq("-i"),
                        Mockito.any(),
                        Mockito.eq("root@127.0.0.1:/bugreports/bugreport.zip"),
                        Mockito.any()))
                .thenReturn(res);

        File bugreport = null;
        try {
            bugreport = GceManager.getBugreportzWithSsh(fakeInfo, mOptions, mMockRunUtil);
            assertNotNull(bugreport);
        } finally {
            FileUtil.deleteFile(bugreport);
        }
    }

    /**
     * Test pulling a bugreport of a remote nested instance. This requires a middle step of dumping
     * and pulling the bugreport to the remote virtual box then scp-ing from it.
     */
    @Test
    public void testGetNestedSshBugreport() throws Exception {
        GceAvdInfo fakeInfo = new GceAvdInfo("ins-gce", HostAndPort.fromHost("127.0.0.1"));
        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
        res.setStdout("bugreport success!\nOK:/bugreports/bugreport.zip\n");
        OutputStream stdout = null;
        OutputStream stderr = null;
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq(stdout),
                        Mockito.eq(stderr),
                        Mockito.eq("ssh"),
                        Mockito.eq("-o"),
                        Mockito.eq("LogLevel=ERROR"),
                        Mockito.eq("-o"),
                        Mockito.eq("UserKnownHostsFile=/dev/null"),
                        Mockito.eq("-o"),
                        Mockito.eq("StrictHostKeyChecking=no"),
                        Mockito.eq("-o"),
                        Mockito.eq("ServerAliveInterval=10"),
                        Mockito.eq("-i"),
                        Mockito.any(),
                        Mockito.eq("root@127.0.0.1"),
                        Mockito.eq("./bin/adb"),
                        Mockito.eq("wait-for-device"),
                        Mockito.eq("shell"),
                        Mockito.eq("bugreportz")))
                .thenReturn(res);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq(stdout),
                        Mockito.eq(stderr),
                        Mockito.eq("ssh"),
                        Mockito.eq("-o"),
                        Mockito.eq("LogLevel=ERROR"),
                        Mockito.eq("-o"),
                        Mockito.eq("UserKnownHostsFile=/dev/null"),
                        Mockito.eq("-o"),
                        Mockito.eq("StrictHostKeyChecking=no"),
                        Mockito.eq("-o"),
                        Mockito.eq("ServerAliveInterval=10"),
                        Mockito.eq("-i"),
                        Mockito.any(),
                        Mockito.eq("root@127.0.0.1"),
                        Mockito.eq("./bin/adb"),
                        Mockito.eq("pull"),
                        Mockito.eq("/bugreports/bugreport.zip")))
                .thenReturn(res);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("scp"),
                        Mockito.eq("-o"),
                        Mockito.eq("LogLevel=ERROR"),
                        Mockito.eq("-o"),
                        Mockito.eq("UserKnownHostsFile=/dev/null"),
                        Mockito.eq("-o"),
                        Mockito.eq("StrictHostKeyChecking=no"),
                        Mockito.eq("-o"),
                        Mockito.eq("ServerAliveInterval=10"),
                        Mockito.eq("-i"),
                        Mockito.any(),
                        Mockito.eq("root@127.0.0.1:./bugreport.zip"),
                        Mockito.any()))
                .thenReturn(res);

        File bugreport = null;
        try {
            bugreport = GceManager.getNestedDeviceSshBugreportz(fakeInfo, mOptions, mMockRunUtil);
            assertNotNull(bugreport);
        } finally {
            FileUtil.deleteFile(bugreport);
        }
    }

    /**
     * Test a case where bugreportz command timeout or may have failed but we still get an output.
     * In this case we still proceed and try to get the bugreport.
     */
    @Test
    public void testGetSshBugreport_Fail() throws Exception {
        GceAvdInfo fakeInfo = new GceAvdInfo("ins-gce", HostAndPort.fromHost("127.0.0.1"));
        CommandResult res = new CommandResult(CommandStatus.FAILED);
        res.setStdout("bugreport failed!\n");
        OutputStream stdout = null;
        OutputStream stderr = null;
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq(stdout),
                        Mockito.eq(stderr),
                        Mockito.eq("ssh"),
                        Mockito.eq("-o"),
                        Mockito.eq("LogLevel=ERROR"),
                        Mockito.eq("-o"),
                        Mockito.eq("UserKnownHostsFile=/dev/null"),
                        Mockito.eq("-o"),
                        Mockito.eq("StrictHostKeyChecking=no"),
                        Mockito.eq("-o"),
                        Mockito.eq("ServerAliveInterval=10"),
                        Mockito.eq("-i"),
                        Mockito.any(),
                        Mockito.eq("root@127.0.0.1"),
                        Mockito.eq("bugreportz")))
                .thenReturn(res);

        File bugreport = null;
        try {
            bugreport = GceManager.getBugreportzWithSsh(fakeInfo, mOptions, mMockRunUtil);
            assertNull(bugreport);
        } finally {
            FileUtil.deleteFile(bugreport);
        }
    }

    /**
     * Test that if the instance bring up reach a timeout but we are able to find a device instance
     * in the logs, we raise it as a failure and attempt to clean up the instance.
     */
    @Test
    public void testStartGce_timeoutAndClean() throws Exception {
        OptionSetter setter = new OptionSetter(mOptions);
        setter.setOptionValue("allow-gce-boot-timeout-override", "true");
        DeviceDescriptor desc = null;
        mGceManager =
                new GceManager(desc, mOptions, mMockBuildInfo) {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    protected List<String> buildGceCmd(
                            File reportFile,
                            IBuildInfo b,
                            String ipDevice,
                            String user,
                            Integer offset,
                            MultiMap<String, String> attributes) {
                        // We delete the potential report file to create an issue.
                        FileUtil.deleteFile(reportFile);
                        List<String> tmp = new ArrayList<String>();
                        tmp.add("");
                        return tmp;
                    }
                };
        CommandResult cmd = new CommandResult();
        cmd.setStatus(CommandStatus.TIMED_OUT);
        cmd.setStderr(
                "2016-09-20 08:11:02,287 |INFO| gcompute_client:728| Creating instance: "
                        + "project android-treehugger, zone us-central1-f, body:{'name': "
                        + "'ins-fake-instance-linux', "
                        + "'disks': [{'autoDelete': True, 'boot': True, 'mode': 'READ_WRITE', "
                        + "'initializeParams': {'diskName': 'gce-x86-phone-userdebug-fastbuild-"
                        + "linux-3286354-eb1fd2e3', 'sourceImage': u'https://www.googleapis.com"
                        + "compute/v1/projects/android-treehugger/global/images/image-gce-x86-ph"
                        + "one-userdebug-fastbuild-linux-3286354-b6b99338'}, 'type': 'PERSISTENT'"
                        + "}, {'autoDelete': True, 'deviceName': 'gce-x86-phone-userdebug-fastbuil"
                        + "d-linux-3286354-eb1fd2e3-data', 'interface': 'SCSI', 'mode': 'READ_WRI"
                        + "TE', 'type': 'PERSISTENT', 'boot': False, 'source': u'projects/andro"
                        + "id-treehugger/zones/us-c}]}");
        cmd.setStdout("output");
        when(mMockRunUtil.runTimedCmd(Mockito.anyLong(), (String[]) Mockito.any())).thenReturn(cmd);
        // Ensure that the instance can be shutdown.
        CommandResult shutdownResult = new CommandResult();
        shutdownResult.setStatus(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.any(),
                        Mockito.eq("delete"),
                        Mockito.eq("--config_file"),
                        Mockito.any(),
                        Mockito.eq("--instance_names"),
                        Mockito.eq("ins-fake-instance-linux"),
                        Mockito.eq("--report_file"),
                        Mockito.any()))
                .thenReturn(shutdownResult);

        GceAvdInfo gceAvd = mGceManager.startGce();
        assertEquals(
                "acloud errors: timeout after 1800000ms, acloud did not return",
                gceAvd.getErrors());
        // If we attempt to clean up afterward
        mGceManager.shutdownGce();
    }

    @Test
    public void testUpdateTimeout() throws Exception {
        OptionSetter setter = new OptionSetter(mOptions);
        setter.setOptionValue("allow-gce-boot-timeout-override", "true");
        mOptions.getGceDriverParams().add("--boot-timeout");
        mOptions.getGceDriverParams().add("900");
        assertEquals(1800000L, mOptions.getGceCmdTimeout());
        mGceManager = new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo);
        assertEquals(1080000L, mOptions.getGceCmdTimeout());
    }

    @Test
    public void testUpdateTimeout_multiBootTimeout() throws Exception {
        OptionSetter setter = new OptionSetter(mOptions);
        setter.setOptionValue("allow-gce-boot-timeout-override", "true");
        mOptions.getGceDriverParams().add("--boot-timeout");
        mOptions.getGceDriverParams().add("900");
        mOptions.getGceDriverParams().add("--boot-timeout");
        mOptions.getGceDriverParams().add("450");
        assertEquals(1800000L, mOptions.getGceCmdTimeout());
        mGceManager = new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo);
        // The last specified boot-timeout is used.
        assertEquals(630000L, mOptions.getGceCmdTimeout());
    }

    @Test
    public void testUpdateTimeout_noBootTimeout() throws Exception {
        OptionSetter setter = new OptionSetter(mOptions);
        setter.setOptionValue("allow-gce-boot-timeout-override", "true");
        mOptions.getGceDriverParams().add("--someargs");
        mOptions.getGceDriverParams().add("900");
        assertEquals(1800000L, mOptions.getGceCmdTimeout());
        mGceManager = new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo);
        assertEquals(1800000L, mOptions.getGceCmdTimeout());
    }
}
