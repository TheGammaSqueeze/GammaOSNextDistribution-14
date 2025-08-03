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

package com.android.tests.apex;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.cts.install.lib.host.InstallUtilsHost;

import com.android.tests.rollback.host.AbandonSessionsRule;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.ApexInfo;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.Duration;
import java.util.Set;

/**
 * Test for automatic recovery of apex update that causes boot loop.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class ApexRollbackTests extends BaseHostJUnit4Test {
    private final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);
    @Rule
    public AbandonSessionsRule mHostTestRule = new AbandonSessionsRule(this);

    private boolean mWasAdbRoot = false;

    private static boolean sCheckedIfCrashingProcessExists = false;
    private static boolean sAlreadyCrashingProcessExists = false;
    private static String sCrashingProcess;

    @Before
    public void setUp() throws Exception {
        mHostUtils.uninstallShimApexIfNecessary();
        resetProperties();
        mWasAdbRoot = getDevice().isAdbRoot();
        if (!mWasAdbRoot) {
            assumeTrue("Requires root", getDevice().enableAdbRoot());
        }
        if (!sCheckedIfCrashingProcessExists) {
            sAlreadyCrashingProcessExists =
                    getDevice().getBooleanProperty("sys.init.updatable_crashing", false);
            sCrashingProcess = getDevice().getProperty("sys.init.updatable_crashing_process_name");
            sCheckedIfCrashingProcessExists = true;
        }
    }

    /**
     * Uninstalls any version greater than 1 of shim apex and reboots the device if necessary
     * to complete the uninstall.
     */
    @After
    public void tearDown() throws Exception {
        mHostUtils.uninstallShimApexIfNecessary();
        resetProperties();
        if (!mWasAdbRoot) {
            getDevice().disableAdbRoot();
        }
    }

    private void resetProperties() throws Exception {
        resetProperty("persist.debug.trigger_watchdog.apex");
        resetProperty("persist.debug.trigger_updatable_crashing_for_testing");
        resetProperty("persist.debug.trigger_reboot_after_activation");
        resetProperty("persist.debug.trigger_reboot_twice_after_activation");
    }

    private void resetProperty(String propertyName) throws Exception {
        assertWithMessage("Failed to reset value of property %s", propertyName).that(
                getDevice().setProperty(propertyName, "")).isTrue();
    }

    /**
     * Test for automatic recovery of apex update that causes boot loop.
     */
    @Test
    public void testAutomaticBootLoopRecovery() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        ITestDevice device = getDevice();
        // Skip this test if there is already crashing process on device
        assumeFalse(
                "Device already has a crashing process: " + sCrashingProcess,
                sAlreadyCrashingProcessExists);
        File apexFile = mHostUtils.getTestFile("com.android.apex.cts.shim.v2.apex");

        // To simulate an apex update that causes a boot loop, we install a
        // trigger_watchdog.rc file that arranges for a trigger_watchdog.sh
        // script to be run at boot. The trigger_watchdog.sh script checks if
        // the apex version specified in the property
        // persist.debug.trigger_watchdog.apex is installed. If so,
        // trigger_watchdog.sh repeatedly kills the system server causing a
        // boot loop.
        assertThat(device.setProperty("persist.debug.trigger_watchdog.apex",
                "com.android.apex.cts.shim@2")).isTrue();
        String error = mHostUtils.installStagedPackage(apexFile);
        assertThat(error).isNull();

        String sessionIdToCheck = device.executeShellCommand("pm get-stagedsessions --only-ready "
                + "--only-parent --only-sessionid").trim();
        assertThat(sessionIdToCheck).isNotEmpty();

        // After we reboot the device, we expect the device to go into boot
        // loop from trigger_watchdog.sh. Native watchdog should detect and
        // report the boot loop, causing apexd to roll back to the previous
        // version of the apex and force reboot. When the device comes up
        // after the forced reboot, trigger_watchdog.sh will see the different
        // version of the apex and refrain from forcing a boot loop, so the
        // device will be recovered.
        device.reboot();

        ApexInfo ctsShimV1 = new ApexInfo("com.android.apex.cts.shim", 1L);
        ApexInfo ctsShimV2 = new ApexInfo("com.android.apex.cts.shim", 2L);
        Set<ApexInfo> activatedApexes = device.getActiveApexes();
        assertThat(activatedApexes).contains(ctsShimV1);
        assertThat(activatedApexes).doesNotContain(ctsShimV2);

        // Assert that a session has failed with the expected reason
        String sessionInfo = device.executeShellCommand("cmd -w apexservice getStagedSessionInfo "
                    + sessionIdToCheck);
        assertThat(sessionInfo).contains("revertReason: zygote");
    }

    /**
     * Test to verify that a device that does not support checkpointing will not revert a session
     * if it reboots during boot.
     */
    @Test
    public void testSessionNotRevertedWithCheckpointingDisabled() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeFalse("Fs checkpointing is enabled", mHostUtils.isCheckpointSupported());

        File apexFile = mHostUtils.getTestFile("com.android.apex.cts.shim.v2.apex");

        ITestDevice device = getDevice();
        assertThat(device.setProperty("persist.debug.trigger_reboot_after_activation",
                "com.android.apex.cts.shim@2.apex")).isTrue();
        assertThat(device.setProperty("debug.trigger_reboot_once_after_activation",
                "1")).isTrue();

        String error = mHostUtils.installStagedPackage(apexFile);
        assertThat(error).isNull();

        String sessionIdToCheck = device.executeShellCommand("pm get-stagedsessions --only-ready "
                + "--only-parent --only-sessionid").trim();
        assertThat(sessionIdToCheck).isNotEmpty();

        // After we reboot the device, the apexd session should be activated as normal. After this,
        // trigger_reboot.sh will reboot the device before the system server boots.
        device.reboot();

        ApexInfo ctsShimV1 = new ApexInfo("com.android.apex.cts.shim", 1L);
        ApexInfo ctsShimV2 = new ApexInfo("com.android.apex.cts.shim", 2L);
        String stagedSessionInfo = getStagedSession(sessionIdToCheck);
        assertThat(stagedSessionInfo).contains("isApplied = true");

        Set<ApexInfo> activatedApexes = device.getActiveApexes();
        assertThat(activatedApexes).contains(ctsShimV2);
        assertThat(activatedApexes).doesNotContain(ctsShimV1);
    }

    /**
     * Test to verify that rebooting twice when a session is activated will cause the session to
     * be reverted due to filesystem checkpointing.
     */
    @Test
    public void testCheckpointingRevertsSession() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device doesn't support fs checkpointing", mHostUtils.isCheckpointSupported());

        File apexFile = mHostUtils.getTestFile("com.android.apex.cts.shim.v2.apex");

        ITestDevice device = getDevice();
        assertThat(device.setProperty("persist.debug.trigger_reboot_after_activation",
                "com.android.apex.cts.shim@2.apex")).isTrue();
        assertThat(device.setProperty("persist.debug.trigger_reboot_twice_after_activation",
                "1")).isTrue();
        String error = mHostUtils.installStagedPackage(apexFile);
        assertThat(error).isNull();

        String sessionIdToCheck = device.executeShellCommand("pm get-stagedsessions --only-ready "
                + "--only-parent --only-sessionid").trim();
        assertThat(sessionIdToCheck).isNotEmpty();

        // After we reboot the device, the apexd session should be activated as normal. After this,
        // trigger_reboot.sh will reboot the device before the system server boots. Checkpointing
        // will kick in, and at the next boot any non-finalized sessions will be reverted.
        device.reboot();

        ApexInfo ctsShimV1 = new ApexInfo("com.android.apex.cts.shim", 1L);
        ApexInfo ctsShimV2 = new ApexInfo("com.android.apex.cts.shim", 2L);
        String stagedSessionInfo = getStagedSession(sessionIdToCheck);
        assertThat(stagedSessionInfo).contains("isFailed = true");

        Set<ApexInfo> activatedApexes = device.getActiveApexes();
        assertThat(activatedApexes).contains(ctsShimV1);
        assertThat(activatedApexes).doesNotContain(ctsShimV2);
    }

    /**
     * Test to verify that rebooting once upon apex activation does not cause checkpointing to kick
     * in and revert a session, since the checkpointing retry count should be 2.
     */
    @Test
    public void testRebootingOnceDoesNotRevertSession() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device doesn't support fs checkpointing", mHostUtils.isCheckpointSupported());

        File apexFile = mHostUtils.getTestFile("com.android.apex.cts.shim.v2.apex");

        ITestDevice device = getDevice();
        assertThat(device.setProperty("persist.debug.trigger_reboot_after_activation",
                "com.android.apex.cts.shim@2.apex")).isTrue();
        assertThat(device.setProperty("debug.trigger_reboot_once_after_activation",
                "1")).isTrue();
        String error = mHostUtils.installStagedPackage(apexFile);
        assertThat(error).isNull();

        String sessionIdToCheck = device.executeShellCommand("pm get-stagedsessions --only-ready "
                + "--only-parent --only-sessionid").trim();
        assertThat(sessionIdToCheck).isNotEmpty();

        // After we reboot the device, the apexd session should be activated as normal. After this,
        // trigger_reboot.sh will reboot the device before the system server boots. Checkpointing
        // will kick in, and at the next boot any non-finalized sessions will be reverted.
        device.reboot();

        ApexInfo ctsShimV1 = new ApexInfo("com.android.apex.cts.shim", 1L);
        ApexInfo ctsShimV2 = new ApexInfo("com.android.apex.cts.shim", 2L);
        String stagedSessionInfo = getStagedSession(sessionIdToCheck);
        assertThat(stagedSessionInfo).contains("isApplied = true");

        Set<ApexInfo> activatedApexes = device.getActiveApexes();
        assertThat(activatedApexes).contains(ctsShimV2);
        assertThat(activatedApexes).doesNotContain(ctsShimV1);
    }

    /**
     * Test to verify that apexd won't boot loop a device in case {@code sys.init
     * .updatable_crashing} is {@code true} and there is no apex session to revert.
     */
    @Test
    public void testApexdDoesNotBootLoopDeviceIfThereIsNothingToRevert() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        // On next boot trigger setprop sys.init.updatable_crashing 1, which will trigger a
        // revert mechanism in apexd. Since there is nothing to revert, this should be a no-op
        // and device will boot successfully.
        assertThat(getDevice().setProperty("persist.debug.trigger_updatable_crashing_for_testing",
                "1")).isTrue();
        getDevice().reboot();
        assertWithMessage("Device didn't boot in 1 minute").that(
                getDevice().waitForBootComplete(Duration.ofMinutes(1).toMillis())).isTrue();
        // Verify that property was set to true.
        assertThat(getDevice().getBooleanProperty("sys.init.updatable_crashing", false)).isTrue();
    }

    /**
     * Test to verify that boot cleanup logic in apexd is triggered when there is a crash looping
     * process, but there is nothing to revert.
     */
    @Test
    public void testBootCompletedCleanupHappensEvenWhenThereIsCrashingProcess() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());
        try {
            // On next boot trigger setprop sys.init.updatable_crashing 1, which will trigger a
            // revert mechanism in apexd. Since there is nothing to revert, this should be a no-op
            // and device will boot successfully.
            getDevice().setProperty("persist.debug.trigger_updatable_crashing_for_testing", "1");
            assertThat(getDevice().pushFile(mHostUtils.getTestFile("apex.apexd_test_v2.apex"),
                    "/data/apex/active/apexd_test_v2.apex")).isTrue();
            getDevice().reboot();
            assertWithMessage("Timed out waiting for device to boot").that(
                    getDevice().waitForBootComplete(Duration.ofMinutes(2).toMillis())).isTrue();
            // Verify that property was set to true.
            assertThat(
                    getDevice().getBooleanProperty("sys.init.updatable_crashing", false)).isTrue();
            final Set<ITestDevice.ApexInfo> activeApexes = getDevice().getActiveApexes();
            ITestDevice.ApexInfo testApex = new ITestDevice.ApexInfo(
                    "com.android.apex.cts.shim", 2L);
            assertThat(activeApexes).doesNotContain(testApex);
            mHostUtils.waitForFileDeleted("/data/apex/active/apexd_test_v2.apex",
                    Duration.ofMinutes(3));
        } finally {
            getDevice().executeShellV2Command("rm /data/apex/active/apexd_test_v2.apex");
        }
    }

    /**
     * Test reason for revert is properly logged during boot loops
     */
    @Test
    public void testReasonForRevertIsLoggedDuringBootloop() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Fs checkpointing is enabled", mHostUtils.isCheckpointSupported());

        ITestDevice device = getDevice();
        assumeFalse(
                "Device already has a crashing process: " + sCrashingProcess,
                sAlreadyCrashingProcessExists);
        final File apexFile = mHostUtils.getTestFile("com.android.apex.cts.shim.v2.apex");

        // To simulate an apex update that causes a boot loop, we install a
        // trigger_watchdog.rc file that arranges for a trigger_watchdog.sh
        // script to be run at boot. The trigger_watchdog.sh script checks if
        // the apex version specified in the property
        // persist.debug.trigger_watchdog.apex is installed. If so,
        // trigger_watchdog.sh repeatedly kills the system server causing a
        // boot loop.
        assertThat(device.setProperty("persist.debug.trigger_watchdog.apex",
                "com.android.apex.cts.shim@2")).isTrue();
        final String error = mHostUtils.installStagedPackage(apexFile);
        assertThat(error).isNull();

        final String sessionIdToCheck = device.executeShellCommand("pm get-stagedsessions "
                + "--only-ready --only-parent --only-sessionid").trim();
        assertThat(sessionIdToCheck).isNotEmpty();

        // After we reboot the device, we expect the device to go into boot
        // loop from trigger_watchdog.sh. Native watchdog should detect and
        // report the boot loop, causing apexd to roll back to the previous
        // version of the apex and force reboot. When the device comes up
        // after the forced reboot, trigger_watchdog.sh will see the different
        // version of the apex and refrain from forcing a boot loop, so the
        // device will be recovered.
        device.reboot();

        final ApexInfo ctsShimV1 = new ApexInfo("com.android.apex.cts.shim", 1L);
        final ApexInfo ctsShimV2 = new ApexInfo("com.android.apex.cts.shim", 2L);
        final Set<ApexInfo> activatedApexes = device.getActiveApexes();
        assertThat(activatedApexes).contains(ctsShimV1);
        assertThat(activatedApexes).doesNotContain(ctsShimV2);

        // Assert that a session has failed with the expected reason
        final String stagedSessionString = getStagedSession(sessionIdToCheck);
        assertThat(stagedSessionString).contains("Session reverted due to crashing native process");
    }

    String getStagedSession(String sessionId) throws DeviceNotAvailableException {
        final String[] lines = getDevice().executeShellCommand(
                "pm get-stagedsessions").split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("sessionId = " + sessionId + ";")) {
                // Join all lines realted to this session
                final StringBuilder result = new StringBuilder(lines[i]);
                for (int j = i + 1; j < lines.length; j++) {
                    if (lines[j].startsWith("sessionId = ")) {
                        // A new session block has started
                        break;
                    }
                    result.append(lines[j]);
                }
                return result.toString();
            }
        }
        return "";
    }
}
