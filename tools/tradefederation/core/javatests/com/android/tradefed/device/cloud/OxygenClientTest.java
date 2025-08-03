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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.MultiMap;

import com.google.common.base.Joiner;
import com.google.common.net.HostAndPort;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link OxygenClient} */
@RunWith(JUnit4.class)
public class OxygenClientTest {

    private File mOxygenBinaryFile;

    private OxygenClient mOxygenClient;

    private BuildInfo mBuildInfo;

    private GceAvdInfo mGceAvdInfo;

    private TestDeviceOptions mTestDeviceOptions;

    private IRunUtil mRunUtil;

    private static final String[] GCE_DEVICE_PARAMS =
            new String[] {
                "random-arg",
                "--branch",
                "testBranch",
                "--build-target",
                "target",
                "--build-id",
                "P1234567",
                "--system-build-target",
                "testSystemTarget",
                "--system-build-id",
                "S1234567",
                "--kernel-build-target",
                "testKernelTarget",
                "--kernel-build-id",
                "K1234567"
            };
    private static final String[] BOOT_IMAGE_PARAMS =
            new String[] {
                "--boot-build-target",
                "testBootTarget",
                "--boot-build-id",
                "B1234567",
                "--boot-artifact",
                "boot-5.10.img"
            };

    private static final String[] BOOTLOADER_PARAMS =
            new String[] {
                "--bootloader-build-target",
                "testBootloaderTarget",
                "--bootloader-build-id",
                "BL1234567"
            };

    private static final String[] HOST_PACKAGE_PARAMS =
            new String[] {
                "--host_package_build_target",
                "testHostPackageTarget",
                "--host_package_build_id",
                "HP1234567"
            };

    private static final String EXPECTED_OUTPUT =
            "debug info lease result: session_id:\"6a6a744e-0653-4926-b7b8-535d121a2fc9\"\n"
                    + " server_url:\"10.0.80.227\"\n"
                    + " ports:{type:test value:12345}\n"
                    + " random_key:\"this-is-12345678\"\n"
                    + " leased_device_spec:{type:TESTTYPE build_artifacts:{build_id:\"P1234567\""
                    + " build_target:\"target\" build_branch:\"testBranch\"}}"
                    + " debug_info:{reserved_cores:1 region:\"test-region\" environment:\"test\"}";

    @Before
    public void setUp() throws Exception {
        mOxygenBinaryFile = FileUtil.createTempFile("oxygen", "binary");
        mBuildInfo = new BuildInfo("P1234567", "target");
        mBuildInfo.setBuildBranch("testBranch");
        mBuildInfo.addBuildAttribute("build_target", "target");
        mGceAvdInfo =
                new GceAvdInfo(
                        "6a6a744e-0653-4926-b7b8-535d121a2fc9",
                        HostAndPort.fromString("10.0.80.227").withDefaultPort(12345));
        mTestDeviceOptions =
                new TestDeviceOptions() {
                    @Override
                    public List<String> getGceDriverParams() {
                        return Arrays.asList(GCE_DEVICE_PARAMS);
                    }
                };
        OptionSetter setter = new OptionSetter(mTestDeviceOptions);
        setter.setOptionValue("oxygen-target-region", "us-east");
        setter.setOptionValue("oxygen-lease-length", "60m");
        setter.setOptionValue("oxygen-device-size", "large");
        setter.setOptionValue("oxygen-service-address", "10.1.23.45");
        setter.setOptionValue("gce-boot-timeout", "900000");
        setter.setOptionValue("oxygen-accounting-user", "random1234@space.com");
        setter.setOptionValue("extra-oxygen-args", "arg1", "value1");
        mRunUtil = Mockito.mock(IRunUtil.class);
        mOxygenClient = new OxygenClient(mOxygenBinaryFile, mRunUtil);
    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mOxygenBinaryFile);
    }

    /** Test leasing a device with Oxygen client binary. */
    @Test
    public void testLease() throws Exception {
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                long timeout = mock.getArgument(0);
                                List<String> cmd = new ArrayList<>();
                                for (int i = 1; i < mock.getArguments().length; i++) {
                                    cmd.add(mock.getArgument(i));
                                }
                                String cmdString = Joiner.on(" ").join(cmd);
                                String expectedCmdString =
                                        mOxygenBinaryFile.getAbsolutePath()
                                                + " -lease -build_branch testBranch -build_target"
                                                + " target -build_id P1234567"
                                                + " -system_build_target testSystemTarget"
                                                + " -system_build_id S1234567"
                                                + " -kernel_build_target testKernelTarget"
                                                + " -kernel_build_id K1234567 -target_region"
                                                + " us-east -accounting_user random1234@space.com"
                                                + " -lease_length_secs 3600"
                                                + " -arg1 value1"
                                                + " -user_debug_info work_unit_id:some_id";
                                assertEquals(timeout, 900000);
                                assertEquals(expectedCmdString, cmdString);

                                CommandResult res = new CommandResult();
                                res.setStatus(CommandStatus.SUCCESS);
                                res.setStdout("");
                                res.setStderr(EXPECTED_OUTPUT);
                                return res;
                            }
                        })
                .when(mRunUtil)
                .runTimedCmd(Mockito.anyLong(), Mockito.any());
        MultiMap<String, String> attributes = new MultiMap<>();
        attributes.put("work_unit_id", "some_id");
        CommandResult res = mOxygenClient.leaseDevice(mBuildInfo, mTestDeviceOptions, attributes);
        assertEquals(res.getStatus(), CommandStatus.SUCCESS);
        assertEquals(res.getStderr(), EXPECTED_OUTPUT);
    }

    /** Test leasing a device with Oxygen client binary without build-id specified. */
    @Test
    public void testLeaseWithoutBuildId() throws Exception {
        TestDeviceOptions testDeviceOptions =
                new TestDeviceOptions() {
                    @Override
                    public List<String> getGceDriverParams() {
                        return Arrays.asList(
                                new String[] {
                                    "--branch", "testBranch", "--build-target", "target"
                                });
                    }
                };
        OptionSetter setter = new OptionSetter(testDeviceOptions);
        setter.setOptionValue("oxygen-target-region", "us-east");
        setter.setOptionValue("oxygen-lease-length", "60m");
        setter.setOptionValue("oxygen-device-size", "large");
        setter.setOptionValue("oxygen-service-address", "10.1.23.45");
        setter.setOptionValue("gce-boot-timeout", "900000");
        setter.setOptionValue("oxygen-accounting-user", "random1234@space.com");
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                long timeout = mock.getArgument(0);
                                List<String> cmd = new ArrayList<>();
                                for (int i = 1; i < mock.getArguments().length; i++) {
                                    cmd.add(mock.getArgument(i));
                                }
                                String cmdString = Joiner.on(" ").join(cmd);
                                String expectedCmdString =
                                        mOxygenBinaryFile.getAbsolutePath()
                                                + " -lease -build_branch testBranch -build_target"
                                                + " target -build_id testBranch -target_region"
                                                + " us-east -accounting_user random1234@space.com"
                                                + " -lease_length_secs 3600"
                                                + " -user_debug_info work_unit_id:some_id";
                                assertEquals(timeout, 900000);
                                assertEquals(expectedCmdString, cmdString);

                                CommandResult res = new CommandResult();
                                res.setStatus(CommandStatus.SUCCESS);
                                res.setStdout("");
                                res.setStderr(EXPECTED_OUTPUT);
                                return res;
                            }
                        })
                .when(mRunUtil)
                .runTimedCmd(Mockito.anyLong(), Mockito.any());
        MultiMap<String, String> attributes = new MultiMap<>();
        attributes.put("work_unit_id", "some_id");
        CommandResult res = mOxygenClient.leaseDevice(mBuildInfo, testDeviceOptions, attributes);
        assertEquals(res.getStatus(), CommandStatus.SUCCESS);
        assertEquals(res.getStderr(), EXPECTED_OUTPUT);
    }

    /** Test leasing multiple devices with Oxygen client binary. */
    @Test
    public void testLeaseMultipleDevice() throws Exception {
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                long timeout = mock.getArgument(0);
                                List<String> cmd = new ArrayList<>();
                                for (int i = 1; i < mock.getArguments().length; i++) {
                                    cmd.add(mock.getArgument(i));
                                }
                                String cmdString = Joiner.on(" ").join(cmd);
                                String expectedCmdString =
                                        mOxygenBinaryFile.getAbsolutePath()
                                                + " -lease"
                                                + " -build_target target,target"
                                                + " -build_branch testBranch,testBranch"
                                                + " -build_id P1234567,P1234567"
                                                + " -multidevice_size 2"
                                                + " -target_region us-east"
                                                + " -accounting_user random1234@space.com"
                                                + " -lease_length_secs 3600"
                                                + " -arg1 value1"
                                                + " -user_debug_info work_unit_id:some_id";
                                assertEquals(timeout, 900000);
                                assertEquals(expectedCmdString, cmdString);

                                CommandResult res = new CommandResult();
                                res.setStatus(CommandStatus.SUCCESS);
                                res.setStdout("");
                                res.setStderr(EXPECTED_OUTPUT);
                                return res;
                            }
                        })
                .when(mRunUtil)
                .runTimedCmd(Mockito.anyLong(), Mockito.any());
        MultiMap<String, String> attributes = new MultiMap<>();
        attributes.put("work_unit_id", "some_id");
        CommandResult res =
                mOxygenClient.leaseMultipleDevices(
                        Arrays.asList(mBuildInfo, mBuildInfo), mTestDeviceOptions, attributes);
        assertEquals(res.getStatus(), CommandStatus.SUCCESS);
        assertEquals(res.getStderr(), EXPECTED_OUTPUT);
    }

    /** Test releasing a device with Oxygen client binary. */
    @Test
    public void testRelease() throws Exception {
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                long timeout = mock.getArgument(0);
                                List<String> cmd = new ArrayList<>();
                                for (int i = 1; i < mock.getArguments().length; i++) {
                                    cmd.add(mock.getArgument(i));
                                }
                                String cmdString = Joiner.on(" ").join(cmd);
                                String expectedCmdString =
                                        mOxygenBinaryFile.getAbsolutePath()
                                                + " -arg1 value1 -release -target_region us-east"
                                                + " -server_url 10.0.80.227"
                                                + " -session_id"
                                                + " 6a6a744e-0653-4926-b7b8-535d121a2fc9"
                                                + " -accounting_user"
                                                + " random1234@space.com";
                                assertEquals(timeout, 900000);
                                assertEquals(expectedCmdString, cmdString);

                                CommandResult res = new CommandResult();
                                res.setStatus(CommandStatus.SUCCESS);
                                return res;
                            }
                        })
                .when(mRunUtil)
                .runTimedCmd(Mockito.anyLong(), Mockito.any());
        boolean isReleased = mOxygenClient.release(mGceAvdInfo, mTestDeviceOptions);
        assertTrue(isReleased);
    }

    /** Test releasing an empty GceAvdInfo. */
    @Test
    public void testReleaseEmptyGceAvdInfo() throws Exception {
        // Empty GceAvdInfo happen when the lease was unsuccessful
        GceAvdInfo emptyGceAvdInfo = new GceAvdInfo(null, null);
        boolean isReleased = mOxygenClient.release(emptyGceAvdInfo, mTestDeviceOptions);
        // Should return true as there is nothing need to be released
        assertTrue(isReleased);
    }

    @Test
    public void testNoWaitForBootSpecified() throws Exception {
        assertFalse(mOxygenClient.noWaitForBootSpecified(mTestDeviceOptions));

        OptionSetter setter = new OptionSetter(mTestDeviceOptions);
        setter.setOptionValue("extra-oxygen-args", "no_wait_for_boot", "");
        assertTrue(mOxygenClient.noWaitForBootSpecified(mTestDeviceOptions));
    }

    /** Test fetch cvd path override. */
    @Test
    public void testFetchCvdOverride() throws Exception {
        assertNull(mOxygenClient.getOverrideFetchCvdPath(mTestDeviceOptions));
        OptionSetter setter = new OptionSetter(mTestDeviceOptions);
        setter.setOptionValue("extra-oxygen-args", "override_fetch_cvd_path", "gs://abc/fetch_cvd");
        assertEquals(
                mOxygenClient.getOverrideFetchCvdPath(mTestDeviceOptions), "gs://abc/fetch_cvd");
    }

    @Test
    public void testLeaseWithBootImageAndBootArtifact() throws Exception {
        mTestDeviceOptions =
                new TestDeviceOptions() {
                    @Override
                    public List<String> getGceDriverParams() {
                        List<String> paramsList = new ArrayList<>();
                        paramsList.addAll(Arrays.asList(GCE_DEVICE_PARAMS));
                        paramsList.addAll(Arrays.asList(BOOT_IMAGE_PARAMS));
                        return paramsList;
                    }
                };
        OptionSetter setter = new OptionSetter(mTestDeviceOptions);
        setter.setOptionValue("oxygen-target-region", "us-east");
        setter.setOptionValue("oxygen-lease-length", "60m");
        setter.setOptionValue("oxygen-device-size", "large");
        setter.setOptionValue("oxygen-service-address", "10.1.23.45");
        setter.setOptionValue("gce-boot-timeout", "900000");
        setter.setOptionValue("oxygen-accounting-user", "random1234@space.com");
        setter.setOptionValue("extra-oxygen-args", "arg1", "value1");
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                List<String> cmd = new ArrayList<>();
                                for (int i = 1; i < mock.getArguments().length; i++) {
                                    cmd.add(mock.getArgument(i));
                                }
                                String cmdString = String.join(" ", cmd);
                                String expectedCmdString =
                                        mOxygenBinaryFile.getAbsolutePath()
                                                + " -lease -build_branch testBranch -build_target"
                                                + " target -build_id P1234567"
                                                + " -system_build_target testSystemTarget"
                                                + " -system_build_id S1234567"
                                                + " -kernel_build_target testKernelTarget"
                                                + " -kernel_build_id K1234567"
                                                + " -boot_build_target testBootTarget"
                                                + " -boot_build_id B1234567"
                                                + " -boot_artifact boot-5.10.img"
                                                + " -target_region us-east"
                                                + " -accounting_user random1234@space.com"
                                                + " -lease_length_secs 3600"
                                                + " -arg1 value1"
                                                + " -user_debug_info work_unit_id:some_id";
                                assertEquals(expectedCmdString, cmdString);

                                CommandResult res = new CommandResult();
                                res.setStatus(CommandStatus.SUCCESS);
                                res.setStdout("");
                                res.setStderr(EXPECTED_OUTPUT);
                                return res;
                            }
                        })
                .when(mRunUtil)
                .runTimedCmd(Mockito.anyLong(), Mockito.any());
        MultiMap<String, String> attributes = new MultiMap<>();
        attributes.put("work_unit_id", "some_id");
        CommandResult res = mOxygenClient.leaseDevice(mBuildInfo, mTestDeviceOptions, attributes);
        assertEquals(res.getStatus(), CommandStatus.SUCCESS);
        assertEquals(res.getStderr(), EXPECTED_OUTPUT);
    }

    @Test
    public void testLeaseWithBootloader() throws Exception {
        mTestDeviceOptions =
                new TestDeviceOptions() {
                    @Override
                    public List<String> getGceDriverParams() {
                        List<String> paramsList = new ArrayList<>();
                        paramsList.addAll(Arrays.asList(GCE_DEVICE_PARAMS));
                        paramsList.addAll(Arrays.asList(BOOTLOADER_PARAMS));
                        return paramsList;
                    }
                };
        OptionSetter setter = new OptionSetter(mTestDeviceOptions);
        setter.setOptionValue("oxygen-target-region", "us-east");
        setter.setOptionValue("oxygen-lease-length", "60m");
        setter.setOptionValue("oxygen-device-size", "large");
        setter.setOptionValue("oxygen-service-address", "10.1.23.45");
        setter.setOptionValue("gce-boot-timeout", "900000");
        setter.setOptionValue("oxygen-accounting-user", "random1234@space.com");
        setter.setOptionValue("extra-oxygen-args", "arg1", "value1");
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                List<String> cmd = new ArrayList<>();
                                for (int i = 1; i < mock.getArguments().length; i++) {
                                    cmd.add(mock.getArgument(i));
                                }
                                String cmdString = String.join(" ", cmd);
                                String expectedCmdString =
                                        mOxygenBinaryFile.getAbsolutePath()
                                                + " -lease -build_branch testBranch -build_target"
                                                + " target -build_id P1234567"
                                                + " -system_build_target testSystemTarget"
                                                + " -system_build_id S1234567"
                                                + " -kernel_build_target testKernelTarget"
                                                + " -kernel_build_id K1234567"
                                                + " -bootloader_build_target testBootloaderTarget"
                                                + " -bootloader_build_id BL1234567"
                                                + " -target_region us-east"
                                                + " -accounting_user random1234@space.com"
                                                + " -lease_length_secs 3600"
                                                + " -arg1 value1"
                                                + " -user_debug_info work_unit_id:some_id";
                                assertEquals(expectedCmdString, cmdString);

                                CommandResult res = new CommandResult();
                                res.setStatus(CommandStatus.SUCCESS);
                                res.setStdout("");
                                res.setStderr(EXPECTED_OUTPUT);
                                return res;
                            }
                        })
                .when(mRunUtil)
                .runTimedCmd(Mockito.anyLong(), Mockito.any());
        MultiMap<String, String> attributes = new MultiMap<>();
        attributes.put("work_unit_id", "some_id");
        CommandResult res = mOxygenClient.leaseDevice(mBuildInfo, mTestDeviceOptions, attributes);
        assertEquals(res.getStatus(), CommandStatus.SUCCESS);
        assertEquals(res.getStderr(), EXPECTED_OUTPUT);
    }

    @Test
    public void testLeaseWithHostPackage() throws Exception {
        mTestDeviceOptions =
                new TestDeviceOptions() {
                    @Override
                    public List<String> getGceDriverParams() {
                        List<String> paramsList = new ArrayList<>();
                        paramsList.addAll(Arrays.asList(GCE_DEVICE_PARAMS));
                        paramsList.addAll(Arrays.asList(HOST_PACKAGE_PARAMS));
                        return paramsList;
                    }
                };
        OptionSetter setter = new OptionSetter(mTestDeviceOptions);
        setter.setOptionValue("oxygen-target-region", "us-east");
        setter.setOptionValue("oxygen-lease-length", "60m");
        setter.setOptionValue("oxygen-device-size", "large");
        setter.setOptionValue("oxygen-service-address", "10.1.23.45");
        setter.setOptionValue("gce-boot-timeout", "900000");
        setter.setOptionValue("oxygen-accounting-user", "random1234@space.com");
        setter.setOptionValue("extra-oxygen-args", "arg1", "value1");
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                List<String> cmd = new ArrayList<>();
                                for (int i = 1; i < mock.getArguments().length; i++) {
                                    cmd.add(mock.getArgument(i));
                                }
                                String cmdString = String.join(" ", cmd);
                                String expectedCmdString =
                                        mOxygenBinaryFile.getAbsolutePath()
                                                + " -lease -build_branch testBranch -build_target"
                                                + " target -build_id P1234567 -system_build_target"
                                                + " testSystemTarget -system_build_id S1234567"
                                                + " -kernel_build_target testKernelTarget"
                                                + " -kernel_build_id K1234567"
                                                + " -host_package_build_target"
                                                + " testHostPackageTarget -host_package_build_id"
                                                + " HP1234567 -target_region us-east"
                                                + " -accounting_user random1234@space.com"
                                                + " -lease_length_secs 3600 -arg1 value1"
                                                + " -user_debug_info work_unit_id:some_id";
                                assertEquals(expectedCmdString, cmdString);

                                CommandResult res = new CommandResult();
                                res.setStatus(CommandStatus.SUCCESS);
                                res.setStdout("");
                                res.setStderr(EXPECTED_OUTPUT);
                                return res;
                            }
                        })
                .when(mRunUtil)
                .runTimedCmd(Mockito.anyLong(), Mockito.any());
        MultiMap<String, String> attributes = new MultiMap<>();
        attributes.put("work_unit_id", "some_id");
        CommandResult res = mOxygenClient.leaseDevice(mBuildInfo, mTestDeviceOptions, attributes);
        assertEquals(res.getStatus(), CommandStatus.SUCCESS);
        assertEquals(res.getStderr(), EXPECTED_OUTPUT);
    }
}
