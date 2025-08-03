/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.net;

import static org.junit.Assert.fail;

import android.platform.test.annotations.SecurityTest;

import com.android.ddmlib.Log;
import com.android.testutils.SkipPresubmit;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SkipPresubmit(reason = "Out of SLO flakiness")
public class HostsideRestrictBackgroundNetworkTests extends HostsideNetworkTestCase {

    @Before
    public void setUp() throws Exception {
        uninstallPackage(TEST_APP2_PKG, false);
        installPackage(TEST_APP2_APK);
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(TEST_APP2_PKG, true);
    }

    @SecurityTest
    @Test
    public void testDataWarningReceiver() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".DataWarningReceiverTest",
                "testSnoozeWarningNotReceived");
    }

    /**************************
     * Data Saver Mode tests. *
     **************************/

    @Test
    public void testDataSaverMode_disabled() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".DataSaverModeTest",
                "testGetRestrictBackgroundStatus_disabled");
    }

    @Test
    public void testDataSaverMode_whitelisted() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".DataSaverModeTest",
                "testGetRestrictBackgroundStatus_whitelisted");
    }

    @Test
    public void testDataSaverMode_enabled() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".DataSaverModeTest",
                "testGetRestrictBackgroundStatus_enabled");
    }

    @Test
    public void testDataSaverMode_blacklisted() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".DataSaverModeTest",
                "testGetRestrictBackgroundStatus_blacklisted");
    }

    @Test
    public void testDataSaverMode_reinstall() throws Exception {
        final int oldUid = getUid(TEST_APP2_PKG);

        // Make sure whitelist is revoked when package is removed
        addRestrictBackgroundWhitelist(oldUid);

        uninstallPackage(TEST_APP2_PKG, true);
        assertPackageUninstalled(TEST_APP2_PKG);
        assertRestrictBackgroundWhitelist(oldUid, false);

        installPackage(TEST_APP2_APK);
        final int newUid = getUid(TEST_APP2_PKG);
        assertRestrictBackgroundWhitelist(oldUid, false);
        assertRestrictBackgroundWhitelist(newUid, false);
    }

    @Test
    public void testDataSaverMode_requiredWhitelistedPackages() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".DataSaverModeTest",
                "testGetRestrictBackgroundStatus_requiredWhitelistedPackages");
    }

    @Test
    public void testDataSaverMode_broadcastNotSentOnUnsupportedDevices() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".DataSaverModeTest",
                "testBroadcastNotSentOnUnsupportedDevices");
    }

    /*****************************
     * Battery Saver Mode tests. *
     *****************************/

    @Test
    public void testBatterySaverModeMetered_disabled() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".BatterySaverModeMeteredTest",
                "testBackgroundNetworkAccess_disabled");
    }

    @Test
    public void testBatterySaverModeMetered_whitelisted() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".BatterySaverModeMeteredTest",
                "testBackgroundNetworkAccess_whitelisted");
    }

    @Test
    public void testBatterySaverModeMetered_enabled() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".BatterySaverModeMeteredTest",
                "testBackgroundNetworkAccess_enabled");
    }

    @Test
    public void testBatterySaverMode_reinstall() throws Exception {
        if (!isDozeModeEnabled()) {
            Log.w(TAG, "testBatterySaverMode_reinstall() skipped because device does not support "
                    + "Doze Mode");
            return;
        }

        addPowerSaveModeWhitelist(TEST_APP2_PKG);

        uninstallPackage(TEST_APP2_PKG, true);
        assertPackageUninstalled(TEST_APP2_PKG);
        assertPowerSaveModeWhitelist(TEST_APP2_PKG, false);

        installPackage(TEST_APP2_APK);
        assertPowerSaveModeWhitelist(TEST_APP2_PKG, false);
    }

    @Test
    public void testBatterySaverModeNonMetered_disabled() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".BatterySaverModeNonMeteredTest",
                "testBackgroundNetworkAccess_disabled");
    }

    @Test
    public void testBatterySaverModeNonMetered_whitelisted() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".BatterySaverModeNonMeteredTest",
                "testBackgroundNetworkAccess_whitelisted");
    }

    @Test
    public void testBatterySaverModeNonMetered_enabled() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".BatterySaverModeNonMeteredTest",
                "testBackgroundNetworkAccess_enabled");
    }

    /*******************
     * App idle tests. *
     *******************/

    @Test
    public void testAppIdleMetered_disabled() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".AppIdleMeteredTest",
                "testBackgroundNetworkAccess_disabled");
    }

    @Test
    public void testAppIdleMetered_whitelisted() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".AppIdleMeteredTest",
                "testBackgroundNetworkAccess_whitelisted");
    }

    @Test
    public void testAppIdleMetered_tempWhitelisted() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".AppIdleMeteredTest",
                "testBackgroundNetworkAccess_tempWhitelisted");
    }

    @Test
    public void testAppIdleMetered_enabled() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".AppIdleMeteredTest",
                "testBackgroundNetworkAccess_enabled");
    }

    @Test
    public void testAppIdleMetered_idleWhitelisted() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".AppIdleMeteredTest",
                "testAppIdleNetworkAccess_idleWhitelisted");
    }

    // TODO: currently power-save mode and idle uses the same whitelist, so this test would be
    // redundant (as it would be testing the same as testBatterySaverMode_reinstall())
    //    public void testAppIdle_reinstall() throws Exception {
    //    }

    @Test
    public void testAppIdleNonMetered_disabled() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".AppIdleNonMeteredTest",
                "testBackgroundNetworkAccess_disabled");
    }


    @Test
    public void testAppIdleNonMetered_whitelisted() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".AppIdleNonMeteredTest",
                "testBackgroundNetworkAccess_whitelisted");
    }

    @Test
    public void testAppIdleNonMetered_tempWhitelisted() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".AppIdleNonMeteredTest",
                "testBackgroundNetworkAccess_tempWhitelisted");
    }

    @Test
    public void testAppIdleNonMetered_enabled() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".AppIdleNonMeteredTest",
                "testBackgroundNetworkAccess_enabled");
    }

    @Test
    public void testAppIdleNonMetered_idleWhitelisted() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".AppIdleNonMeteredTest",
                "testAppIdleNetworkAccess_idleWhitelisted");
    }

    @Test
    public void testAppIdleNonMetered_whenCharging() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".AppIdleNonMeteredTest",
                "testAppIdleNetworkAccess_whenCharging");
    }

    @Test
    public void testAppIdleMetered_whenCharging() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".AppIdleMeteredTest",
                "testAppIdleNetworkAccess_whenCharging");
    }

    @Test
    public void testAppIdle_toast() throws Exception {
        // Check that showing a toast doesn't bring an app out of standby
        runDeviceTests(TEST_PKG, TEST_PKG + ".AppIdleNonMeteredTest",
                "testAppIdle_toast");
    }

    /********************
     * Doze Mode tests. *
     ********************/

    @Test
    public void testDozeModeMetered_disabled() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".DozeModeMeteredTest",
                "testBackgroundNetworkAccess_disabled");
    }

    @Test
    public void testDozeModeMetered_whitelisted() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".DozeModeMeteredTest",
                "testBackgroundNetworkAccess_whitelisted");
    }

    @Test
    public void testDozeModeMetered_enabled() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".DozeModeMeteredTest",
                "testBackgroundNetworkAccess_enabled");
    }

    @Test
    public void testDozeModeMetered_enabledButWhitelistedOnNotificationAction() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".DozeModeMeteredTest",
                "testBackgroundNetworkAccess_enabledButWhitelistedOnNotificationAction");
    }

    // TODO: currently power-save mode and idle uses the same whitelist, so this test would be
    // redundant (as it would be testing the same as testBatterySaverMode_reinstall())
    //    public void testDozeMode_reinstall() throws Exception {
    //    }

    @Test
    public void testDozeModeNonMetered_disabled() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".DozeModeNonMeteredTest",
                "testBackgroundNetworkAccess_disabled");
    }

    @Test
    public void testDozeModeNonMetered_whitelisted() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".DozeModeNonMeteredTest",
                "testBackgroundNetworkAccess_whitelisted");
    }

    @Test
    public void testDozeModeNonMetered_enabled() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".DozeModeNonMeteredTest",
                "testBackgroundNetworkAccess_enabled");
    }

    @Test
    public void testDozeModeNonMetered_enabledButWhitelistedOnNotificationAction()
            throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".DozeModeNonMeteredTest",
                "testBackgroundNetworkAccess_enabledButWhitelistedOnNotificationAction");
    }

    /**********************
     * Mixed modes tests. *
     **********************/

    @Test
    public void testDataAndBatterySaverModes_meteredNetwork() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".MixedModesTest",
                "testDataAndBatterySaverModes_meteredNetwork");
    }

    @Test
    public void testDataAndBatterySaverModes_nonMeteredNetwork() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".MixedModesTest",
                "testDataAndBatterySaverModes_nonMeteredNetwork");
    }

    @Test
    public void testDozeAndBatterySaverMode_powerSaveWhitelists() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".MixedModesTest",
                "testDozeAndBatterySaverMode_powerSaveWhitelists");
    }

    @Test
    public void testDozeAndAppIdle_powerSaveWhitelists() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".MixedModesTest",
                "testDozeAndAppIdle_powerSaveWhitelists");
    }

    @Test
    public void testAppIdleAndDoze_tempPowerSaveWhitelists() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".MixedModesTest",
                "testAppIdleAndDoze_tempPowerSaveWhitelists");
    }

    @Test
    public void testAppIdleAndBatterySaver_tempPowerSaveWhitelists() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".MixedModesTest",
                "testAppIdleAndBatterySaver_tempPowerSaveWhitelists");
    }

    @Test
    public void testDozeAndAppIdle_appIdleWhitelist() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".MixedModesTest",
                "testDozeAndAppIdle_appIdleWhitelist");
    }

    @Test
    public void testAppIdleAndDoze_tempPowerSaveAndAppIdleWhitelists() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".MixedModesTest",
                "testAppIdleAndDoze_tempPowerSaveAndAppIdleWhitelists");
    }

    @Test
    public void testAppIdleAndBatterySaver_tempPowerSaveAndAppIdleWhitelists() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".MixedModesTest",
                "testAppIdleAndBatterySaver_tempPowerSaveAndAppIdleWhitelists");
    }

    /**************************
     * Restricted mode tests. *
     **************************/

    @Test
    public void testNetworkAccess_restrictedMode() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".RestrictedModeTest",
                "testNetworkAccess");
    }

    @Test
    public void testNetworkAccess_restrictedMode_withBatterySaver() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".RestrictedModeTest",
                "testNetworkAccess_withBatterySaver");
    }

    /************************
     * Expedited job tests. *
     ************************/

    @Test
    public void testMeteredNetworkAccess_expeditedJob() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".ExpeditedJobMeteredTest");
    }

    @Test
    public void testNonMeteredNetworkAccess_expeditedJob() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".ExpeditedJobNonMeteredTest");
    }

    /*******************
     * Helper methods. *
     *******************/

    private void assertRestrictBackgroundWhitelist(int uid, boolean expected) throws Exception {
        final int max_tries = 5;
        boolean actual = false;
        for (int i = 1; i <= max_tries; i++) {
            final String output = runCommand("cmd netpolicy list restrict-background-whitelist ");
            actual = output.contains(Integer.toString(uid));
            if (expected == actual) {
                return;
            }
            Log.v(TAG, "whitelist check for uid " + uid + " doesn't match yet (expected "
                    + expected + ", got " + actual + "); sleeping 1s before polling again");
            RunUtil.getDefault().sleep(1000);
        }
        fail("whitelist check for uid " + uid + " failed: expected "
                + expected + ", got " + actual);
    }

    private void assertPowerSaveModeWhitelist(String packageName, boolean expected)
            throws Exception {
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        assertDelayedCommand("dumpsys deviceidle whitelist =" + packageName,
                Boolean.toString(expected));
    }

    /**
     * Asserts the result of a command, wait and re-running it a couple times if necessary.
     */
    private void assertDelayedCommand(String command, String expectedResult)
            throws InterruptedException, DeviceNotAvailableException {
        final int maxTries = 5;
        for (int i = 1; i <= maxTries; i++) {
            final String result = runCommand(command).trim();
            if (result.equals(expectedResult)) return;
            Log.v(TAG, "Command '" + command + "' returned '" + result + " instead of '"
                    + expectedResult + "' on attempt #; sleeping 1s before polling again");
            RunUtil.getDefault().sleep(1000);
        }
        fail("Command '" + command + "' did not return '" + expectedResult + "' after " + maxTries
                + " attempts");
    }

    protected void addRestrictBackgroundWhitelist(int uid) throws Exception {
        runCommand("cmd netpolicy add restrict-background-whitelist " + uid);
        assertRestrictBackgroundWhitelist(uid, true);
    }

    private void addPowerSaveModeWhitelist(String packageName) throws Exception {
        Log.i(TAG, "Adding package " + packageName + " to power-save-mode whitelist");
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        runCommand("dumpsys deviceidle whitelist +" + packageName);
        assertPowerSaveModeWhitelist(packageName, true);
    }

    protected boolean isDozeModeEnabled() throws Exception {
        final String result = runCommand("cmd deviceidle enabled deep").trim();
        return result.equals("1");
    }
}
