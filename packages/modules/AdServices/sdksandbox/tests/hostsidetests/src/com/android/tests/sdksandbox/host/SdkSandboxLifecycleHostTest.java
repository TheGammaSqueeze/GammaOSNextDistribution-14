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

package com.android.tests.sdksandbox.host;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.hosttestutils.DeviceSupportHostUtils;
import android.app.sdksandbox.hosttestutils.SecondaryUserUtils;

import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class SdkSandboxLifecycleHostTest extends BaseHostJUnit4Test {

    private static final String APP_PACKAGE = "com.android.sdksandbox.app";
    private static final String APP_APK = "SdkSandboxTestApp.apk";
    private static final String APP_2_PACKAGE = "com.android.sdksandbox.app2";

    private static final String APP_SHARED_PACKAGE = "com.android.sdksandbox.shared.app1";
    private static final String APP_SHARED_ACTIVITY = "SdkSandboxTestSharedActivity";
    private static final String APP_SHARED_2_PACKAGE = "com.android.sdksandbox.shared.app2";

    private static final String APP_ACTIVITY = "SdkSandboxTestActivity";
    private static final String APP_2_ACTIVITY = "SdkSandboxTestActivity2";
    private static final String APP_2_EMPTY_ACTIVITY = "SdkSandboxEmptyActivity";

    private static final String CODE_APK = "TestCodeProvider.apk";
    private static final String CODE_APK_2 = "TestCodeProvider2.apk";

    private static final String APP_2_PROCESS_NAME = "com.android.sdksandbox.processname";
    private static final String APP_2_PROCESS_NAME_2 = "com.android.sdksandbox.emptyactivity";
    private static final String SANDBOX_2_PROCESS_NAME = APP_2_PROCESS_NAME
                                                            + "_sdk_sandbox";
    /**
     * process name for app1 is not defined and it takes the package name by default
     */
    private static final String SANDBOX_1_PROCESS_NAME = APP_PACKAGE + "_sdk_sandbox";

    private static final String SANDBOX_SHARED_1_PROCESS_NAME = APP_SHARED_PACKAGE + "_sdk_sandbox";
    private static final String SANDBOX_SHARED_2_PROCESS_NAME =
            APP_SHARED_2_PACKAGE + "_sdk_sandbox";

    private final SecondaryUserUtils mUserUtils = new SecondaryUserUtils(this);
    private final DeviceSupportHostUtils mDeviceSupportUtils = new DeviceSupportHostUtils(this);

    private boolean mWasRoot;
    private DeviceSdkLevel mDeviceSdkLevel;

    @Before
    public void setUp() throws Exception {
        assumeTrue("Device supports SdkSandbox", mDeviceSupportUtils.isSdkSandboxSupported());

        assertThat(getBuild()).isNotNull();
        assertThat(getDevice()).isNotNull();

        mWasRoot = getDevice().isAdbRoot();
        getDevice().enableAdbRoot();

        mDeviceSdkLevel = new DeviceSdkLevel(getDevice());
    }

    @After
    public void tearDown() throws Exception {
        mUserUtils.removeSecondaryUserIfNecessary();
        cleanUpAppAndSandboxProcesses();

        if (!mWasRoot) {
            getDevice().disableAdbRoot();
        }
    }

    @Test
    public void testSdkSandboxIsDestroyedOnAppDestroy() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        killApp(APP_PACKAGE);
        waitForProcessDeath(SANDBOX_1_PROCESS_NAME);
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(APP_PACKAGE + '\n');
        assertThat(processDump).doesNotContain(SANDBOX_1_PROCESS_NAME);

        // Wait 5 seconds to ensure that the sandbox has not restarted dying.
        Thread.sleep(5000);
        waitForProcessDeath(SANDBOX_1_PROCESS_NAME);
    }

    @Test
    public void testSdkSandboxIsCreatedPerApp() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        startActivity(APP_2_PACKAGE, APP_2_ACTIVITY);
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_2_PROCESS_NAME + '\n');
        assertThat(processDump).contains(SANDBOX_2_PROCESS_NAME);
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        killApp(APP_2_PACKAGE);
        // Wait a bit to allow sandbox death
        waitForProcessDeath(SANDBOX_2_PROCESS_NAME);
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(APP_2_PROCESS_NAME + '\n');
        assertThat(processDump).doesNotContain(SANDBOX_2_PROCESS_NAME);
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);
    }

    @Ignore("b/275299487")
    @Test
    public void testSandboxIsCreatedPerUser() throws Exception {
        assumeTrue(getDevice().isMultiUserSupported());

        int secondaryUserId = mUserUtils.createAndStartSecondaryUser();
        installPackageAsUser(APP_APK, false, secondaryUserId);

        // Start app for the primary user
        startActivity(APP_PACKAGE, APP_ACTIVITY);
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        mUserUtils.switchToSecondaryUser();

        // Should still see an app/sdk sandbox running.
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        // Start the app for the secondary user.
        startActivity(APP_PACKAGE, APP_ACTIVITY);
        // There should be two instances of app and sandbox processes - one for each user.
        assertThat(getProcessOccurrenceCount(APP_PACKAGE + '\n')).isEqualTo(2);
        assertThat(getProcessOccurrenceCount(SANDBOX_1_PROCESS_NAME)).isEqualTo(2);

        // Kill the app process for the secondary user.
        killApp(APP_PACKAGE + '\n');
        // There should be one instance of app and sandbox process after kill
        assertThat(getProcessOccurrenceCount(APP_PACKAGE + '\n')).isEqualTo(1);
        assertThat(getProcessOccurrenceCount(SANDBOX_1_PROCESS_NAME)).isEqualTo(1);
    }

    @Test
    public void testAppAndSdkSandboxAreKilledOnLoadedSdkUpdate() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);

        // Should see app/sdk sandbox running
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        // Update package loaded by app
        installPackage(CODE_APK, "-d");
        waitForProcessDeath(SANDBOX_1_PROCESS_NAME);

        // Should no longer see app/sdk sandbox running
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(APP_PACKAGE + '\n');
        assertThat(processDump).doesNotContain(SANDBOX_1_PROCESS_NAME);
    }

    @Test
    public void testAppAndSdkSandboxAreKilledForNonLoadedSdkUpdate() throws Exception {
        // Have the app load the first SDK.
        startActivity(APP_2_PACKAGE, APP_2_ACTIVITY);

        // Should see app/sdk sandbox running
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_2_PROCESS_NAME + '\n');
        assertThat(processDump).contains(SANDBOX_2_PROCESS_NAME);

        // Update package consumed by the app, but not loaded into the sandbox.
        installPackage(CODE_APK_2, "-d");
        waitForProcessDeath(SANDBOX_2_PROCESS_NAME);

        // Should no longer see app/sdk sandbox running
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(APP_2_PROCESS_NAME + '\n');
        assertThat(processDump).doesNotContain(SANDBOX_2_PROCESS_NAME);
    }

    @Test
    public void testAppsWithSharedUidCanLoadSameSdk() throws Exception {
        startActivity(APP_SHARED_PACKAGE, APP_SHARED_ACTIVITY);
        assertThat(runDeviceTests(APP_SHARED_2_PACKAGE,
                "com.android.sdksandbox.shared.app2.SdkSandboxTestSharedApp2",
                "testLoadSdkIsSuccessful")).isTrue();
    }

    @Test
    public void testAppsWithSharedUid_OneAppDies() throws Exception {
        startActivity(APP_SHARED_PACKAGE, APP_SHARED_ACTIVITY);
        assertThat(runDeviceTests(APP_SHARED_2_PACKAGE,
                "com.android.sdksandbox.shared.app2.SdkSandboxTestSharedApp2",
                "testLoadSdkIsSuccessful")).isTrue();

        // APP_SHARED_2_PACKAGE dies after running device-side tests.
        waitForProcessDeath(SANDBOX_SHARED_2_PROCESS_NAME);
        if (mDeviceSdkLevel.isDeviceAtLeastU()) {
            // For U+, the other sandbox should still be alive.
            String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
            assertThat(processDump).contains(SANDBOX_SHARED_1_PROCESS_NAME);
        } else {
            // For T, the sandbox for APP_SHARED_PACKAGE should also die since we kill by uid.
            waitForProcessDeath(SANDBOX_SHARED_1_PROCESS_NAME);

            // Neither of the sandboxes should be respawned later
            Thread.sleep(5000);
            waitForProcessDeath(SANDBOX_SHARED_1_PROCESS_NAME);
            waitForProcessDeath(SANDBOX_SHARED_2_PROCESS_NAME);
        }
    }

    @Test
    public void testAppOwnedSdkSandboxInterfaceRemoval_AppDies() throws Exception {
        startActivity(APP_SHARED_PACKAGE, APP_SHARED_ACTIVITY);
        assertThat(
                        runDeviceTests(
                                APP_SHARED_2_PACKAGE,
                                "com.android.sdksandbox.shared.app2.SdkSandboxTestSharedApp2",
                                "testRegisterAppOwedSdkSandboxInterfacesBeforeAppDeath"))
                .isTrue();

        // APP_SHARED_2_PACKAGE dies after running device-side tests.
        waitForProcessDeath(SANDBOX_SHARED_2_PROCESS_NAME);
        assertThat(
                        runDeviceTests(
                                APP_SHARED_2_PACKAGE,
                                "com.android.sdksandbox.shared.app2.SdkSandboxTestSharedApp2",
                                "testGetAppOwedSdkSandboxInterfacesOnAppDeath"))
                .isTrue();
    }

    @Test
    public void testSandboxIsKilledWhenKillswitchEnabled() throws Exception {
        try {
            getDevice()
                    .executeShellCommand("device_config put adservices disable_sdk_sandbox false");
            startActivity(APP_2_PACKAGE, APP_2_ACTIVITY);
            String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
            assertThat(processDump).contains(APP_2_PROCESS_NAME + '\n');
            assertThat(processDump).contains(SANDBOX_2_PROCESS_NAME);

            getDevice()
                    .executeShellCommand("device_config put adservices disable_sdk_sandbox true");
            waitForProcessDeath(SANDBOX_2_PROCESS_NAME);
            if (mDeviceSdkLevel.isDeviceAtLeastU()) {
                waitForProcessDeath(APP_2_PROCESS_NAME);
            }

            processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
            // In U+ the app should be killed when the sandbox is killed.
            if (mDeviceSdkLevel.isDeviceAtLeastU()) {
                assertThat(processDump).doesNotContain(APP_2_PROCESS_NAME + '\n');
            } else {
                assertThat(processDump).contains(APP_2_PROCESS_NAME + '\n');
            }
            assertThat(processDump).doesNotContain(SANDBOX_2_PROCESS_NAME);
        } finally {
            getDevice().executeShellCommand("cmd sdk_sandbox set-state --enabled");
        }
    }

    @Test
    public void testSpecificAppProcessIsKilledOnSandboxDeath() throws Exception {
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastU());

        try {
            getDevice()
                    .executeShellCommand("device_config put adservices disable_sdk_sandbox false");

            // Start two activities running in two different processes for the same app. One
            // activity loads an SDK while the other does nothing.
            startActivity(APP_2_PACKAGE, APP_2_EMPTY_ACTIVITY);
            startActivity(APP_2_PACKAGE, APP_2_ACTIVITY);
            String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
            assertThat(processDump).contains(APP_2_PROCESS_NAME + '\n');
            assertThat(processDump).contains(APP_2_PROCESS_NAME_2 + '\n');
            assertThat(processDump).contains(SANDBOX_2_PROCESS_NAME);

            final String initialAppProcessPid = getDevice().getProcessPid(APP_2_PROCESS_NAME);

            // Kill the sandbox.
            getDevice()
                    .executeShellCommand("device_config put adservices disable_sdk_sandbox true");
            waitForProcessDeath(SANDBOX_2_PROCESS_NAME);
            try {
                waitForProcessDeath(APP_2_PROCESS_NAME + '\n');
            } catch (Exception e) {
                // If the app process has not died, it could have restarted as it was the top
                // activity. Verify that it is not the same process by checking the PID.
                final String finalAppProcessPid = getDevice().getProcessPid(APP_2_PROCESS_NAME);
                assertThat(finalAppProcessPid).isNotEqualTo(initialAppProcessPid);
            }

            // Only the app process which loaded the SDK should die. The other app process should
            // still be alive.
            processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
            assertThat(processDump).doesNotContain(APP_2_PROCESS_NAME + '\n');
            assertThat(processDump).doesNotContain(SANDBOX_2_PROCESS_NAME);
            assertThat(processDump).contains(APP_2_PROCESS_NAME_2 + '\n');

        } finally {
            getDevice().executeShellCommand("cmd sdk_sandbox set-state --enabled");
        }
    }

    @Test
    public void testBackgroundingAppReducesSandboxPriority() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);

        // Should see app/sdk sandbox running
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        int sandboxOomScoreAdj1 = getOomScoreAdj(SANDBOX_1_PROCESS_NAME);
        int appOomScoreAdj1 = getOomScoreAdj(APP_PACKAGE);
        // Verify that the sandbox process has lower priority than the app process.
        assertThat(sandboxOomScoreAdj1).isAtLeast(appOomScoreAdj1);

        // Navigate to home screen to send both apps to the background.
        getDevice().executeShellCommand("input keyevent KEYCODE_HOME");

        // Wait for app to be backgrounded and unbinding of sandbox to complete.
        Thread.sleep(5000);

        // Should see app/sdk sandbox running
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        int sandboxOomScoreAdj2 = getOomScoreAdj(SANDBOX_1_PROCESS_NAME);
        int appOomScoreAdj2 = getOomScoreAdj(APP_PACKAGE);
        // The higher the oom adj score, the lower the priority of the process.
        assertThat(sandboxOomScoreAdj2).isGreaterThan(sandboxOomScoreAdj1);
        assertThat(appOomScoreAdj2).isGreaterThan(appOomScoreAdj1);

        if (mDeviceSdkLevel.isDeviceAtLeastV()) {
            assertThat(sandboxOomScoreAdj2).isAtLeast(appOomScoreAdj2);

            // Start other apps to try to reduce the priority of the app.
            startActivity(APP_2_PACKAGE, APP_2_ACTIVITY);
            startActivity(APP_SHARED_PACKAGE, APP_SHARED_ACTIVITY);
            Thread.sleep(2000);

            processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
            assertThat(processDump).contains(APP_PACKAGE + '\n');
            assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

            int sandboxOomScoreAdj3 = getOomScoreAdj(SANDBOX_1_PROCESS_NAME);
            int appOomScoreAdj3 = getOomScoreAdj(APP_PACKAGE);
            assertThat(appOomScoreAdj3).isAtLeast(appOomScoreAdj2);
            assertThat(sandboxOomScoreAdj3).isAtLeast(sandboxOomScoreAdj2);
            assertThat(sandboxOomScoreAdj3).isAtLeast(appOomScoreAdj3);
        }
    }

    @Test
    public void testSandboxReconnectionAfterDeath() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);

        // Should see app/sdk sandbox running
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        String initialSandboxPid = getDevice().getProcessPid(SANDBOX_1_PROCESS_NAME);
        getDevice().executeShellCommand("kill -9 " + initialSandboxPid);

        Thread.sleep(5000);

        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        if (mDeviceSdkLevel.isDeviceAtLeastU()) {
            // The sandbox should not restart in U+.
            assertThat(processDump).doesNotContain(SANDBOX_1_PROCESS_NAME);
        } else {
            // The sandbox gets restarted, so it should still be running
            assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

            String finalSandboxPid = getDevice().getProcessPid(SANDBOX_1_PROCESS_NAME);
            assertThat(initialSandboxPid).isNotEqualTo(finalSandboxPid);

            int initialSandboxOomScoreAdj = getOomScoreAdj(SANDBOX_1_PROCESS_NAME);

            // Navigate to home screen to send both apps to the background.
            getDevice().executeShellCommand("input keyevent KEYCODE_HOME");

            // Wait for app to be backgrounded and unbinding of sandbox to complete.
            Thread.sleep(2000);

            // Should see app/sdk sandbox running
            processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
            assertThat(processDump).contains(APP_PACKAGE + '\n');
            assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

            // Verify that unbinding in the background still works for a restarted sandbox.
            int finalSandboxOomScoreAdj = getOomScoreAdj(SANDBOX_1_PROCESS_NAME);
            // The higher the oom adj score, the lower the priority of the process.
            assertThat(finalSandboxOomScoreAdj).isGreaterThan(initialSandboxOomScoreAdj);
        }
    }

    @Ignore("b/310160187")
    @Test
    public void testSdkSandboxProcessNameForSecondaryUser() throws Exception {
        assumeTrue(getDevice().isMultiUserSupported());
        String appApk2 = "SdkSandboxTestApp2.apk";

        int secondaryUserId = mUserUtils.createAndStartSecondaryUser();
        mUserUtils.switchToSecondaryUser();
        installPackageAsUser(appApk2, false, secondaryUserId);
        startActivity(APP_2_PACKAGE, APP_2_ACTIVITY);
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_2_PROCESS_NAME + '\n');
        assertThat(processDump).contains(SANDBOX_2_PROCESS_NAME);
    }

    private void startActivity(String pkg, String activity) throws Exception {
        getDevice()
                .executeShellCommand(
                        String.format(
                                "am start -W -n %s/.%s --user %d",
                                pkg, activity, getDevice().getCurrentUser()));

        // Check that the activity has started correctly by checking that its process has started.
        // Depending on the test package configuration, the package may differ from the process
        // name.
        String expectedProcessName = pkg;
        if (pkg.equals(APP_2_PACKAGE)) {
            if (activity.equals(APP_2_ACTIVITY)) {
                expectedProcessName = APP_2_PROCESS_NAME;
            } else {
                expectedProcessName = APP_2_PROCESS_NAME_2;
            }
        }
        assertThat(
                        getDevice()
                                .executeShellCommand(
                                        String.format("ps -A | grep %s", expectedProcessName)))
                .isNotEmpty();
    }

    private void killApp(String pkg) throws Exception {
        getDevice().executeShellCommand(String.format("am force-stop --user current %s", pkg));
        waitForProcessDeath(pkg + '\n');
    }

    // Get the number of running processes with the given process name.
    private int getProcessOccurrenceCount(String processName) throws Exception {
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");

        int count = 0;
        int processOccurrenceIndex = processDump.indexOf(processName);
        while (processOccurrenceIndex >= 0) {
            count++;
            processOccurrenceIndex = processDump.indexOf(processName, processOccurrenceIndex + 1);
        }
        return count;
    }

    private void cleanUpAppAndSandboxProcesses() throws Exception {
        for (String pkg :
                new String[] {
                    APP_PACKAGE, APP_2_PACKAGE, APP_SHARED_PACKAGE, APP_SHARED_2_PACKAGE
                }) {
            killApp(pkg);
        }

        // Ensure no sandbox is currently running
        for (String sandbox :
                new String[] {
                    SANDBOX_1_PROCESS_NAME,
                    SANDBOX_2_PROCESS_NAME,
                    SANDBOX_SHARED_1_PROCESS_NAME,
                    SANDBOX_SHARED_2_PROCESS_NAME
                }) {
            waitForProcessDeath(sandbox);
        }
    }

    private int getOomScoreAdj(String processName) throws DeviceNotAvailableException {
        String pid = getDevice().getProcessPid(processName);
        String oomScoreAdj =
                getDevice().executeShellCommand("cat /proc/" + pid + "/oom_score_adj").trim();
        return Integer.parseInt(oomScoreAdj);
    }

    private void waitForProcessDeath(String processName) throws Exception {
        int timeElapsed = 0;
        while (timeElapsed <= 30000) {
            final String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
            if (processDump.contains(processName)) {
                Thread.sleep(1000);
                timeElapsed += 1000;
                continue;
            }
            return;
        }
        throw new AssertionError("Process " + processName + " has not died.");
    }
}
