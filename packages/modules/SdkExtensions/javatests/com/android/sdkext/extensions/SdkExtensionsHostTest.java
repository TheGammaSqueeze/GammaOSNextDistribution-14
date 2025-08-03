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

package com.android.sdkext.extensions;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.cts.install.lib.host.InstallUtilsHost;

import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.os.ext.testing.CurrentVersion;
import com.android.tests.rollback.host.AbandonSessionsRule;
import com.android.tradefed.device.ITestDevice.ApexInfo;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public class SdkExtensionsHostTest extends BaseHostJUnit4Test {

    private static final String APP_FILENAME = "sdkextensions_e2e_test_app.apk";
    private static final String APP_PACKAGE = "com.android.sdkext.extensions.apps";
    private static final String MEDIA_FILENAME = "test_com.android.media.apex";
    private static final String SDKEXTENSIONS_FILENAME = "test_com.android.sdkext.apex";

    private static String appFilename(String appName) {
        return "sdkextensions_e2e_test_app_req_" + appName + ".apk";
    }

    private static String appPackage(String appName) {
        return "com.android.sdkext.extensions.apps." + appName;
    }

    private static final Duration BOOT_COMPLETE_TIMEOUT = Duration.ofMinutes(2);

    private final InstallUtilsHost mInstallUtils = new InstallUtilsHost(this);

    private DeviceSdkLevel mDeviceSdkLevel;
    private Boolean mIsAtLeastS = null;
    private Boolean mIsAtLeastT = null;
    private Boolean mIsAtLeastU = null;

    @Rule public AbandonSessionsRule mHostTestRule = new AbandonSessionsRule(this);

    @Before
    public void setUp() throws Exception {
        assumeTrue("Updating APEX is not supported", mInstallUtils.isApexUpdateSupported());
        mDeviceSdkLevel = new DeviceSdkLevel(getDevice());
    }

    @Before
    public void installTestApp() throws Exception {
        File testAppFile = mInstallUtils.getTestFile(APP_FILENAME);
        String installResult = getDevice().installPackage(testAppFile, true);
        assertNull(installResult);
    }

    @Before // Generally not needed, but local test devices are sometimes in a "bad" start state.
    @After
    public void cleanup() throws Exception {
        getDevice().uninstallPackage(APP_PACKAGE);
        uninstallApexes(SDKEXTENSIONS_FILENAME, MEDIA_FILENAME);
    }

    @Test
    public void testDefault() throws Exception {
        assertVersionDefault();
    }

    @Test
    public void upgradeOneApexWithBump() throws Exception {
        assertVersionDefault();
        mInstallUtils.installApexes(SDKEXTENSIONS_FILENAME);
        reboot();

        // Version 12 requires sdkext, which is fulfilled
        // Version 45 requires sdkext + media, which isn't fulfilled
        assertRVersionEquals(12);
        assertSVersionEquals(12);
        assertTVersionEquals(12);
        assertTestMethodsPresent(); // 45 APIs are available on 12 too.
    }

    @Test
    public void upgradeOneApex() throws Exception {
        // Version 45 requires updated sdkext and media, so updating just media changes nothing.
        assertVersionDefault();
        mInstallUtils.installApexes(MEDIA_FILENAME);
        reboot();
        assertVersionDefault();
    }

    @Test
    public void upgradeTwoApexes() throws Exception {
        // Updating sdkext and media bumps the version to 45.
        assertVersionDefault();
        mInstallUtils.installApexes(MEDIA_FILENAME, SDKEXTENSIONS_FILENAME);
        reboot();
        assertVersion45();
    }

    private boolean canInstallApp(String appName) throws Exception {
        File appFile = mInstallUtils.getTestFile(appFilename(appName));
        String installResult = getDevice().installPackage(appFile, true);
        if (installResult != null) {
            return false;
        }
        assertNull(getDevice().uninstallPackage(appPackage(appName)));
        return true;
    }

    private String getExtensionVersionFromSysprop(String v) throws Exception {
        String command = "getprop build.version.extensions." + v;
        CommandResult res = getDevice().executeShellV2Command(command);
        assertEquals(0, (int) res.getExitCode());
        return res.getStdout().replace("\n", "");
    }

    private String broadcast(String action, String extra) throws Exception {
        String command = getBroadcastCommand(action, extra);
        CommandResult res = getDevice().executeShellV2Command(command);
        assertEquals(0, (int) res.getExitCode());
        Matcher matcher = Pattern.compile("data=\"([^\"]+)\"").matcher(res.getStdout());
        assertTrue("Unexpected output from am broadcast: " + res.getStdout(), matcher.find());
        return matcher.group(1);
    }

    private boolean broadcastForBoolean(String action, String extra) throws Exception {
        String result = broadcast(action, extra);
        if (result.equals("true") || result.equals("false")) {
            return result.equals("true");
        }
        throw getAppParsingError(result);
    }

    private int broadcastForInt(String action, String extra) throws Exception {
        String result = broadcast(action, extra);
        try {
            return Integer.parseInt(result);
        } catch (NumberFormatException e) {
            throw getAppParsingError(result);
        }
    }

    private Error getAppParsingError(String result) {
        String message = "App error! Full stack trace in logcat (grep for SdkExtensionsE2E): ";
        return new AssertionError(message + result);
    }

    private void assertVersionDefault() throws Exception {
        int expected =
                isAtLeastU()
                        ? CurrentVersion.CURRENT_TRAIN_VERSION
                        : isAtLeastT()
                                ? CurrentVersion.T_BASE_VERSION
                                : isAtLeastS()
                                        ? CurrentVersion.S_BASE_VERSION
                                        : CurrentVersion.R_BASE_VERSION;
        assertRVersionEquals(expected);
        assertSVersionEquals(expected);
        assertTVersionEquals(expected);
        assertTestMethodsNotPresent();
    }

    private void assertVersion45() throws Exception {
        assertRVersionEquals(45);
        assertSVersionEquals(45);
        assertTVersionEquals(45);
        assertTestMethodsPresent();
    }

    private void assertTestMethodsNotPresent() throws Exception {
        assertTrue(broadcastForBoolean("MAKE_CALLS_DEFAULT", null));
    }

    private void assertTestMethodsPresent() throws Exception {
        if (isAtLeastS()) {
            assertTrue(broadcastForBoolean("MAKE_CALLS_45", null));
        } else {
            // The APIs in the test apex are not currently getting installed correctly
            // on Android R devices because they rely on the dynamic classpath feature.
            // TODO(b/234361913): fix this
            assertTestMethodsNotPresent();
        }
    }

    private void assertRVersionEquals(int version) throws Exception {
        String[] apps =
                version >= 45
                        ? new String[] {"r12", "r45"}
                        : version >= 12 ? new String[] {"r12"} : new String[] {};
        assertExtensionVersionEquals("r", version, apps, true);
    }

    private void assertSVersionEquals(int version) throws Exception {
        // These APKs require the same R version as they do S version.
        int minVersion = Math.min(version, broadcastForInt("GET_SDK_VERSION", "r"));
        String[] apps =
                minVersion >= 45
                        ? new String[] {"s12", "s45"}
                        : minVersion >= 12 ? new String[] {"s12"} : new String[] {};
        assertExtensionVersionEquals("s", version, apps, isAtLeastS());
    }

    private void assertTVersionEquals(int version) throws Exception {
        assertExtensionVersionEquals("t", version, new String[] {}, isAtLeastT());
    }

    private void assertExtensionVersionEquals(
            String extension, int version, String[] apps, boolean expected) throws Exception {
        int appValue = broadcastForInt("GET_SDK_VERSION", extension);
        String syspropValue = getExtensionVersionFromSysprop(extension);
        if (expected) {
            assertEquals(version, appValue);
            assertEquals(String.valueOf(version), syspropValue);
            for (String app : apps) {
                assertTrue(canInstallApp(app));
            }
        } else {
            assertEquals(0, appValue);
            assertEquals("", syspropValue);
            for (String app : apps) {
                assertFalse(canInstallApp(app));
            }
        }
    }

    private static String getBroadcastCommand(String action, String extra) {
        String cmd = "am broadcast";
        cmd += " -a com.android.sdkext.extensions.apps." + action;
        if (extra != null) {
            cmd += " -e extra " + extra;
        }
        cmd += " -n com.android.sdkext.extensions.apps/.Receiver";
        return cmd;
    }

    private boolean isAtLeastS() throws Exception {
        if (mIsAtLeastS == null) {
            mIsAtLeastS = mDeviceSdkLevel.isDeviceAtLeastS();
        }
        return mIsAtLeastS;
    }

    private boolean isAtLeastT() throws Exception {
        if (mIsAtLeastT == null) {
            mIsAtLeastT = mDeviceSdkLevel.isDeviceAtLeastT();
        }
        return mIsAtLeastT;
    }

    private boolean isAtLeastU() throws Exception {
        if (mIsAtLeastU == null) {
            mIsAtLeastU = mDeviceSdkLevel.isDeviceAtLeastU();
        }
        return mIsAtLeastU;
    }

    private boolean uninstallApexes(String... filenames) throws Exception {
        boolean reboot = false;
        for (String filename : filenames) {
            ApexInfo apex = mInstallUtils.getApexInfo(mInstallUtils.getTestFile(filename));
            String res = getDevice().uninstallPackage(apex.name);
            // res is null for successful uninstalls (non-null likely implesfactory version).
            reboot |= res == null;
        }
        if (reboot) {
            reboot();
            return true;
        }
        return false;
    }

    private void reboot() throws Exception {
        getDevice().reboot();
        boolean success = getDevice().waitForBootComplete(BOOT_COMPLETE_TIMEOUT.toMillis());
        assertWithMessage("Device didn't boot in %s", BOOT_COMPLETE_TIMEOUT).that(success).isTrue();
    }
}
