/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.pvmfw.test;

import static com.android.tradefed.device.TestDevice.MicrodroidBuilder;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.assertThrows;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.microdroid.test.host.CommandRunner;
import com.android.microdroid.test.host.MicrodroidHostTestCaseBase;
import com.android.pvmfw.test.host.Pvmfw;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceRuntimeException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Tests debug policy */
@RunWith(DeviceJUnit4ClassRunner.class)
public class DebugPolicyHostTests extends MicrodroidHostTestCaseBase {
    @NonNull private static final String PVMFW_FILE_NAME = "pvmfw_test.bin";
    @NonNull private static final String BCC_FILE_NAME = "bcc.dat";
    @NonNull private static final String PACKAGE_FILE_NAME = "MicrodroidTestApp.apk";
    @NonNull private static final String PACKAGE_NAME = "com.android.microdroid.test";
    @NonNull private static final String MICRODROID_DEBUG_FULL = "full";
    @NonNull private static final String MICRODROID_DEBUG_NONE = "none";

    @NonNull
    private static final String MICRODROID_CONFIG_PATH = "assets/microdroid/vm_config_apex.json";

    @NonNull private static final String MICRODROID_LOG_PATH = TEST_ROOT + "log.txt";
    private static final int BOOT_COMPLETE_TIMEOUT_MS = 30000; // 30 seconds
    private static final int BOOT_FAILURE_WAIT_TIME_MS = 10000; // 10 seconds
    private static final int CONSOLE_OUTPUT_WAIT_MS = 5000; // 5 seconds

    @NonNull private static final String CUSTOM_PVMFW_FILE_PREFIX = "pvmfw";
    @NonNull private static final String CUSTOM_PVMFW_FILE_SUFFIX = ".bin";
    @NonNull private static final String CUSTOM_PVMFW_IMG_PATH = TEST_ROOT + PVMFW_FILE_NAME;
    @NonNull private static final String CUSTOM_PVMFW_IMG_PATH_PROP = "hypervisor.pvmfw.path";

    @NonNull private static final String CUSTOM_DEBUG_POLICY_FILE_NAME = "debug_policy.dtb";

    @NonNull
    private static final String CUSTOM_DEBUG_POLICY_PATH =
            TEST_ROOT + CUSTOM_DEBUG_POLICY_FILE_NAME;

    @NonNull
    private static final String CUSTOM_DEBUG_POLICY_PATH_PROP =
            "hypervisor.virtualizationmanager.debug_policy.path";

    @NonNull
    private static final String AVF_DEBUG_POLICY_ADB_DT_PROP_PATH = "/avf/guest/microdroid/adb";

    @NonNull private static final String MICRODROID_CMDLINE_PATH = "/proc/cmdline";
    @NonNull private static final String MICRODROID_DT_ROOT_PATH = "/proc/device-tree";

    @NonNull
    private static final String MICRODROID_DT_BOOTARGS_PATH =
            MICRODROID_DT_ROOT_PATH + "/chosen/bootargs";

    @NonNull
    private static final String MICRODROID_DT_RAMDUMP_PATH =
            MICRODROID_DT_ROOT_PATH + "/avf/guest/common/ramdump";

    @NonNull private static final String HEX_STRING_ZERO = "00000000";
    @NonNull private static final String HEX_STRING_ONE = "00000001";

    @Nullable private static File mPvmfwBinFileOnHost;
    @Nullable private static File mBccFileOnHost;

    @Nullable private TestDevice mAndroidDevice;
    @Nullable private ITestDevice mMicrodroidDevice;
    @Nullable private File mCustomPvmfwBinFileOnHost;
    @Nullable private File mCustomDebugPolicyFileOnHost;

    @Before
    public void setUp() throws Exception {
        mAndroidDevice = (TestDevice) Objects.requireNonNull(getDevice());

        // Check device capabilities
        assumeDeviceIsCapable(mAndroidDevice);
        assumeTrue(
                "Skip if protected VMs are not supported",
                mAndroidDevice.supportsMicrodroid(/* protectedVm= */ true));

        // tradefed copies the test artfacts under /tmp when running tests,
        // so we should *find* the artifacts with the file name.
        mPvmfwBinFileOnHost =
                getTestInformation().getDependencyFile(PVMFW_FILE_NAME, /* targetFirst= */ false);
        mBccFileOnHost =
                getTestInformation().getDependencyFile(BCC_FILE_NAME, /* targetFirst= */ false);

        // Prepare for system properties for custom debug policy.
        // File will be prepared later in individual test by setupCustomDebugPolicy()
        // and then pushed to device when launching with launchProtectedVmAndWaitForBootCompleted()
        // or tryLaunchProtectedNonDebuggableVm().
        mCustomPvmfwBinFileOnHost =
                FileUtil.createTempFile(CUSTOM_PVMFW_FILE_PREFIX, CUSTOM_PVMFW_FILE_SUFFIX);
        setPropertyOrThrow(mAndroidDevice, CUSTOM_PVMFW_IMG_PATH_PROP, CUSTOM_PVMFW_IMG_PATH);
        setPropertyOrThrow(mAndroidDevice, CUSTOM_DEBUG_POLICY_PATH_PROP, CUSTOM_DEBUG_POLICY_PATH);

        // Prepare for launching microdroid
        mAndroidDevice.installPackage(findTestFile(PACKAGE_FILE_NAME), /* reinstall */ false);
        prepareVirtualizationTestSetup(mAndroidDevice);
        mMicrodroidDevice = null;
    }

    @After
    public void shutdown() throws Exception {
        if (!mAndroidDevice.supportsMicrodroid(/* protectedVm= */ true)) {
            return;
        }
        if (mMicrodroidDevice != null) {
            mAndroidDevice.shutdownMicrodroid(mMicrodroidDevice);
            mMicrodroidDevice = null;
        }
        mAndroidDevice.uninstallPackage(PACKAGE_NAME);

        // Cleanup for custom debug policies
        setPropertyOrThrow(mAndroidDevice, CUSTOM_DEBUG_POLICY_PATH_PROP, "");
        setPropertyOrThrow(mAndroidDevice, CUSTOM_PVMFW_IMG_PATH_PROP, "");
        FileUtil.deleteFile(mCustomPvmfwBinFileOnHost);

        cleanUpVirtualizationTestSetup(mAndroidDevice);
    }

    @Test
    public void testAdbInDebugPolicy_withDebugLevelNone_bootWithAdbConnection() throws Exception {
        prepareCustomDebugPolicy("avf_debug_policy_with_adb.dtbo");

        launchProtectedVmAndWaitForBootCompleted(MICRODROID_DEBUG_NONE);
    }

    @Test
    public void testNoAdbInDebugPolicy_withDebugLevelNone_boots() throws Exception {
        prepareCustomDebugPolicy("avf_debug_policy_without_adb.dtbo");

        // VM would boot, but cannot verify directly because of no adbd in the VM.
        CommandResult result = tryLaunchProtectedNonDebuggableVm();
        assertThat(result.getStatus()).isEqualTo(CommandStatus.TIMED_OUT);
        assertWithMessage("Microdroid should have booted")
                .that(result.getStderr())
                .contains("payload is ready");
    }

    @Test
    public void testNoAdbInDebugPolicy_withDebugLevelNone_noConnection() throws Exception {
        prepareCustomDebugPolicy("avf_debug_policy_without_adb.dtbo");

        assertThrows(
                "Microdroid shouldn't be recognized because of missing adb connection",
                DeviceRuntimeException.class,
                () ->
                        launchProtectedVmAndWaitForBootCompleted(
                                MICRODROID_DEBUG_NONE, BOOT_FAILURE_WAIT_TIME_MS));
    }

    @Test
    public void testNoAdbInDebugPolicy_withDebugLevelFull_bootWithAdbConnection() throws Exception {
        prepareCustomDebugPolicy("avf_debug_policy_without_adb.dtbo");

        launchProtectedVmAndWaitForBootCompleted(MICRODROID_DEBUG_FULL);
    }

    @Test
    public void testRamdumpInDebugPolicy_withDebugLevelNone_hasRamdumpArgs() throws Exception {
        prepareCustomDebugPolicy("avf_debug_policy_with_ramdump.dtbo");
        mMicrodroidDevice = launchProtectedVmAndWaitForBootCompleted(MICRODROID_DEBUG_NONE);

        assertThat(readMicrodroidFileAsString(MICRODROID_CMDLINE_PATH)).contains("crashkernel=");
        assertThat(readMicrodroidFileAsString(MICRODROID_DT_BOOTARGS_PATH))
                .contains("crashkernel=");
        assertThat(readMicrodroidFileAsHexString(MICRODROID_DT_RAMDUMP_PATH))
                .isEqualTo(HEX_STRING_ONE);
    }

    @Test
    public void testNoRamdumpInDebugPolicy_withDebugLevelNone_noRamdumpArgs() throws Exception {
        prepareCustomDebugPolicy("avf_debug_policy_without_ramdump.dtbo");
        mMicrodroidDevice = launchProtectedVmAndWaitForBootCompleted(MICRODROID_DEBUG_NONE);

        assertThat(readMicrodroidFileAsString(MICRODROID_CMDLINE_PATH))
                .doesNotContain("crashkernel=");
        assertThat(readMicrodroidFileAsString(MICRODROID_DT_BOOTARGS_PATH))
                .doesNotContain("crashkernel=");
        assertThat(readMicrodroidFileAsHexString(MICRODROID_DT_RAMDUMP_PATH))
                .isEqualTo(HEX_STRING_ZERO);
    }

    @Test
    public void testNoRamdumpInDebugPolicy_withDebugLevelFull_hasRamdumpArgs() throws Exception {
        prepareCustomDebugPolicy("avf_debug_policy_without_ramdump.dtbo");
        mMicrodroidDevice = launchProtectedVmAndWaitForBootCompleted(MICRODROID_DEBUG_FULL);

        assertThat(readMicrodroidFileAsString(MICRODROID_CMDLINE_PATH)).contains("crashkernel=");
        assertThat(readMicrodroidFileAsString(MICRODROID_DT_BOOTARGS_PATH))
                .contains("crashkernel=");
        assertThat(readMicrodroidFileAsHexString(MICRODROID_DT_RAMDUMP_PATH))
                .isEqualTo(HEX_STRING_ZERO);
    }

    private boolean isDebugPolicyEnabled(@NonNull String dtPropertyPath)
            throws DeviceNotAvailableException {
        CommandRunner runner = new CommandRunner(mAndroidDevice);
        CommandResult result =
                runner.runForResult("xxd", "-p", "/proc/device-tree" + dtPropertyPath);
        if (result.getStatus() == CommandStatus.SUCCESS) {
            return HEX_STRING_ONE.equals(result.getStdout().trim());
        }
        return false;
    }

    @NonNull
    private String readMicrodroidFileAsString(@NonNull String path)
            throws DeviceNotAvailableException {
        return new CommandRunner(mMicrodroidDevice).run("cat", path);
    }

    @NonNull
    private String readMicrodroidFileAsHexString(@NonNull String path)
            throws DeviceNotAvailableException {
        return new CommandRunner(mMicrodroidDevice).run("xxd", "-p", path);
    }

    private void prepareCustomDebugPolicy(@NonNull String debugPolicyFileName) throws Exception {
        mCustomDebugPolicyFileOnHost =
                getTestInformation()
                        .getDependencyFile(debugPolicyFileName, /* targetFirst= */ false);

        Pvmfw pvmfw =
                new Pvmfw.Builder(mPvmfwBinFileOnHost, mBccFileOnHost)
                        .setDebugPolicyOverlay(mCustomDebugPolicyFileOnHost)
                        .build();
        pvmfw.serialize(mCustomPvmfwBinFileOnHost);
    }

    private boolean hasConsoleOutput(@NonNull CommandResult result)
            throws DeviceNotAvailableException {
        return result.getStdout().contains("Run /init as init process");
    }

    private boolean hasMicrodroidLogcatOutput() throws DeviceNotAvailableException {
        CommandResult result =
                new CommandRunner(mAndroidDevice).runForResult("test", "-s", MICRODROID_LOG_PATH);
        return result.getExitCode() == 0;
    }

    private ITestDevice launchProtectedVmAndWaitForBootCompleted(String debugLevel)
            throws DeviceNotAvailableException {
        return launchProtectedVmAndWaitForBootCompleted(debugLevel, BOOT_COMPLETE_TIMEOUT_MS);
    }

    private ITestDevice launchProtectedVmAndWaitForBootCompleted(
            String debugLevel, long adbTimeoutMs) throws DeviceNotAvailableException {
        mMicrodroidDevice =
                MicrodroidBuilder.fromDevicePath(
                                getPathForPackage(PACKAGE_NAME), MICRODROID_CONFIG_PATH)
                        .debugLevel(debugLevel)
                        .protectedVm(/* protectedVm= */ true)
                        .addBootFile(mCustomPvmfwBinFileOnHost, PVMFW_FILE_NAME)
                        .addBootFile(mCustomDebugPolicyFileOnHost, CUSTOM_DEBUG_POLICY_FILE_NAME)
                        .setAdbConnectTimeoutMs(adbTimeoutMs)
                        .build(mAndroidDevice);
        assertThat(mMicrodroidDevice.waitForBootComplete(BOOT_COMPLETE_TIMEOUT_MS)).isTrue();
        assertThat(mMicrodroidDevice.enableAdbRoot()).isTrue();
        return mMicrodroidDevice;
    }

    // Try to launch protected non-debuggable VM for a while and quit.
    // Non-debuggable VM might not enable adb, so there's no ITestDevice instance of it.
    private CommandResult tryLaunchProtectedNonDebuggableVm() throws Exception {
        // Can't use MicrodroidBuilder because it expects adb connection
        // but non-debuggable VM may not enable adb.
        CommandRunner runner = new CommandRunner(mAndroidDevice);
        runner.run("mkdir", "-p", TEST_ROOT);
        mAndroidDevice.pushFile(mCustomPvmfwBinFileOnHost, CUSTOM_PVMFW_IMG_PATH);
        mAndroidDevice.pushFile(mCustomDebugPolicyFileOnHost, CUSTOM_DEBUG_POLICY_PATH);

        // This will fail because app wouldn't finish itself.
        // But let's run the app once and get logs.
        String command =
                String.join(
                        " ",
                        VIRT_APEX + "bin/vm",
                        "run-app",
                        "--log",
                        MICRODROID_LOG_PATH,
                        "--protected",
                        getPathForPackage(PACKAGE_NAME),
                        TEST_ROOT + "idsig",
                        TEST_ROOT + "instance.img",
                        "--config-path",
                        MICRODROID_CONFIG_PATH);
        if (isFeatureEnabled("com.android.kvm.LLPVM_CHANGES")) {
            command = String.join(" ", command, "--instance-id-file", TEST_ROOT + "instance_id");
        }
        return mAndroidDevice.executeShellV2Command(
                command, CONSOLE_OUTPUT_WAIT_MS, TimeUnit.MILLISECONDS, /* retryAttempts= */ 0);
    }
}
