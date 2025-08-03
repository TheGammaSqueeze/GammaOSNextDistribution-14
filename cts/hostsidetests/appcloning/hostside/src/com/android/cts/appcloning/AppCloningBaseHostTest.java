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

package com.android.cts.appcloning;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.CommandResult;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

public class AppCloningBaseHostTest extends BaseHostTestCase {

    protected static final String APP_A_PACKAGE = "com.android.cts.appcloningtestapp";
    protected static final String APP_A = "CtsAppCloningTestApp.apk";

    private static final String TEST_CLASS_A = APP_A_PACKAGE + ".AppCloningDeviceTest";

    private static final String FEATURE_WATCH = "android.hardware.type.watch";
    private static final String FEATURE_EMBEDDED = "android.hardware.type.embedded";
    private static final String FEATURE_LEANBACK = "android.software.leanback"; // TV
    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";
    private static final long DEFAULT_INSTRUMENTATION_TIMEOUT_MS = 600_000; // 10min

    protected static final String MEDIA_PROVIDER_URL = "content://media";

    protected static String sCloneUserId;

    protected static String sPublicSdCardVol;

    protected static void createAndStartCloneUser() throws Exception {
        // create clone user
        String output = sDevice.executeShellCommand(
                "pm create-user --profileOf 0 --user-type android.os.usertype.profile.CLONE "
                        + "testUser");
        failIfCloneUserIsAlreadyPresentOnDevice(output);
        sCloneUserId = output.substring(output.lastIndexOf(' ') + 1).replaceAll("[^0-9]",
                "");
        assertThat(sCloneUserId).isNotEmpty();

        startUserAndWait(sCloneUserId);
    }

    private static void failIfCloneUserIsAlreadyPresentOnDevice(String output) {
        if (output.contains("Cannot add more profiles")) {
            fail("A clone user-profile is already present on device. "
                    + "Please remove and re-run the test");
        }
    }

    protected static void startUserAndWait(String userId) throws DeviceNotAvailableException {
        CommandResult out = sDevice.executeShellV2Command("am start-user -w " + userId);
        assertThat(isSuccessful(out)).isTrue();
    }

    protected static void waitForBroadcastIdle() throws DeviceNotAvailableException {
        CommandResult out = sDevice.executeShellV2Command(
                "am wait-for-broadcast-idle", 240, TimeUnit.SECONDS);
        assertThat(isSuccessful(out)).isTrue();
        if (!out.getStdout().contains("All broadcast queues are idle!")) {
            LogUtil.CLog.e("Output from 'am wait-for-broadcast-idle': %s", out);
            fail("'am wait-for-broadcase-idle' did not complete.");
        }
    }

    protected static void removeUser(String userId) throws Exception {
        sDevice.executeShellCommand("pm remove-user " + userId);
    }

    protected static void createSDCardVirtualDisk() throws Exception {
        //remove any existing volume that was mounted before
        removeVirtualDisk();
        String existingPublicVolume = getPublicVolumeExcluding(null);
        sDevice.executeShellCommand("sm set-force-adoptable on");
        sDevice.executeShellCommand("sm set-virtual-disk true");
        eventually(AppCloningBaseHostTest::partitionDisks, 60000,
                "Could not create public volume in time");
        // Need to do a short wait, to allow the newly created volume to mount.
        Thread.sleep(2000);
        sPublicSdCardVol = getPublicVolumeExcluding(existingPublicVolume);
        assertThat(sPublicSdCardVol).isNotNull();
        assertThat(sPublicSdCardVol).isNotEmpty();
        assertThat(sPublicSdCardVol).isNotEqualTo("null");
    }

    protected static void removeVirtualDisk() throws Exception {
        sDevice.executeShellCommand("sm set-virtual-disk false");
        //sleep to make sure that it is unmounted
        Thread.sleep(4000);
    }

    public static void baseHostSetup(ITestDevice device) throws Exception {
        setDevice(device);

        assumeTrue("Hardware type doesn't support clone profiles", isHardwareSupported());
        assumeTrue("Device doesn't support multiple users", supportsMultipleUsers());
        assumeFalse("Device is in headless system user mode", isHeadlessSystemUserMode());
        assumeTrue(isAtLeastS());
        assumeFalse("Device uses sdcardfs", usesSdcardFs());

        createAndStartCloneUser();
        waitForBroadcastIdle();
    }

    public static void baseHostTeardown() throws Exception {
        if (!isAppCloningSupportedOnDevice()) return;

        removeUser(sCloneUserId);
        waitForBroadcastIdle();
    }

    public static boolean isAppCloningSupportedOnDevice() throws Exception {
        return supportsMultipleUsers() && !isHeadlessSystemUserMode() && isAtLeastS()
                && !usesSdcardFs() && isHardwareSupported();
    }

    protected static void assumeHasDeviceFeature(String feature)
            throws DeviceNotAvailableException {
        assumeTrue("device doesn't have " + feature, sDevice.hasFeature(feature));
    }

    protected static boolean supportsMoreThanTwoUsers() throws DeviceNotAvailableException {
        return sDevice.getMaxNumberOfUsersSupported() > 2
                && sDevice.getMaxNumberOfRunningUsersSupported() > 2;
    }

    protected static boolean doesDeviceHaveFeature(String feature)
            throws DeviceNotAvailableException {
        return sDevice.hasFeature(feature);
    }

    protected static boolean isAppCloningBuildingBlockConfigEnabled(ITestDevice testDevice)
            throws DeviceNotAvailableException {
        String buildingBlocksConfigIdentifier =
                "android:bool/config_enableAppCloningBuildingBlocks";
        CommandResult commandResult = testDevice.executeShellV2Command(String.format(
                "cmd overlay lookup android %s", buildingBlocksConfigIdentifier));
        assertTrue(isSuccessful(commandResult));
        return Boolean.parseBoolean(commandResult.getStdout().trim());
    }

    protected CommandResult runContentProviderCommand(String commandType, String userId,
            String provider, String relativePath, String... args) throws Exception {
        String fullUri = provider + relativePath;
        return executeShellV2Command("content %s --user %s --uri %s %s",
                commandType, userId, fullUri, String.join(" ", args));
    }

    protected static boolean usesSdcardFs() throws Exception {
        CommandResult out = sDevice.executeShellV2Command("cat /proc/mounts");
        assertThat(isSuccessful(out)).isTrue();
        for (String line : out.getStdout().split("\n")) {
            String[] split = line.split(" ");
            if (split.length >= 3 && split[2].equals("sdcardfs")) {
                return true;
            }
        }
        return false;
    }

    protected void runDeviceTestAsUserInPkgA(@Nonnull String testMethod, int userId,
            @Nonnull Map<String, String> args) throws Exception {

        runDeviceTestAsUser(APP_A_PACKAGE, TEST_CLASS_A, testMethod, userId, args);
    }

    protected void runDeviceTestAsUser(@Nonnull String testPackage, @Nonnull String testClass,
            @Nonnull String testMethod, int userId, @Nonnull Map<String, String> args)
            throws Exception {
        DeviceTestRunOptions deviceTestRunOptions = new DeviceTestRunOptions(testPackage)
                .setTestClassName(testClass)
                .setTestMethodName(testMethod)
                .setMaxInstrumentationTimeoutMs(DEFAULT_INSTRUMENTATION_TIMEOUT_MS)
                .setUserId(userId);
        for (Map.Entry<String, String> entry : args.entrySet()) {
            deviceTestRunOptions.addInstrumentationArg(entry.getKey(), entry.getValue());
        }

        assertWithMessage(testMethod + " failed").that(
                runDeviceTests(deviceTestRunOptions)).isTrue();
    }

    /**
     * Set the feature flag value on device
     * @param namespace namespace of feature flag
     * @param flag name of feature flag
     * @param value to be assigned
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    protected static void setFeatureFlagValue(String namespace, String flag, String value)
            throws DeviceNotAvailableException {
        sDevice.executeShellCommand("device_config put " + namespace + " " + flag + " " + value);
    }

    protected static boolean isHardwareSupported() throws DeviceNotAvailableException {
        // Clone profiles are not supported on all form factors, only on handheld devices.
        return !sDevice.hasFeature(FEATURE_EMBEDDED)
                && !sDevice.hasFeature(FEATURE_WATCH)
                && !sDevice.hasFeature(FEATURE_LEANBACK)
                && !sDevice.hasFeature(FEATURE_AUTOMOTIVE);
    }
}
