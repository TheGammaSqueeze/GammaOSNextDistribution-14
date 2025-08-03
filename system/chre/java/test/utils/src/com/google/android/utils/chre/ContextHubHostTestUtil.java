/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.android.utils.chre;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.NanoAppBinary;
import android.os.Build;
import android.os.Bundle;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.DynamicConfigDeviceSide;

import org.junit.Assert;
import org.junit.Assume;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A class that defines various utility functions to be used by Context Hub host tests.
 */
public class ContextHubHostTestUtil {
    /**
     * The names of the dynamic configs corresponding to each test suite.
     */
    public static final String[] DEVICE_DYNAMIC_CONFIG_NAMES =
            new String[] {"GtsGmscoreHostTestCases", "GtsLocationContextMultiDeviceTestCases"};

    public static String multiDeviceExternalNanoappPath = null;

    /**
     * Returns the path to the directory containing the nanoapp binaries.
     * It is the external path if passed in, otherwise the relative path
     * to the assets directory of the context of the calling app.
     *
     * @param context the Context to find the asset resources
     * @param hubInfo the ContextHubInfo describing the hub
     * @return the path to the nanoapps
     */
    public static String getNanoAppBinaryPath(Context context, ContextHubInfo hubInfo) {
        String path = getExternalNanoAppPath();

        // Only check for bundled nanoapps if the test is not in debug mode
        if (path == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                path = getNanoAppBinaryPathFromPlatformId(hubInfo.getChrePlatformId());
            } else {
                path = getNanoAppBinaryPathFromHubName(hubInfo.getName());
            }

            boolean haveNanoApps = false;
            try {
                haveNanoApps = context.getAssets().list(path).length > 0;
            } catch (IOException e) {
                Assert.fail("IOException while getting asset list at " + path);
            }

            // We anticipate this failure case (ContextHubInfo being of an unknown
            // platform for us) much more than the case of knowing about a platform
            // but not having a specific nanoapp built for it.  So we separate
            // out this error check to help the user debug this case more easily.
            Assert.assertTrue("None of the test nanoapps are available on the platform: " + path,
                    haveNanoApps);
        }
        return path;
    }

    /**
     * Waits on a CountDownLatch or assert if it timed out or was interrupted.
     *
     * @param latch                       the CountDownLatch
     * @param timeout                     the timeout duration
     * @param unit                        the timeout unit
     * @param timeoutErrorMessage         the message to display on timeout assert
     */
    public static void awaitCountDownLatchAssertOnFailure(
            CountDownLatch latch, long timeout, TimeUnit unit, String timeoutErrorMessage)
                    throws InterruptedException {
        boolean result = latch.await(timeout, unit);
        Assert.assertTrue(timeoutErrorMessage, result);
    }

    /**
     * Creates a NanoAppBinary object from the nanoapp filename.
     *
     * @param hub      the hub to create the binary for
     * @param filename the filename of the nanoapp
     * @return the NanoAppBinary object
     */
    public static NanoAppBinary createNanoAppBinary(ContextHubInfo hub, String filename) {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        String fullName = getNanoAppBinaryPath(context, hub) + "/" + filename;

        InputStream stream = getNanoAppInputStream(context, fullName);
        byte[] binary = null;
        try {
            binary = new byte[stream.available()];
            stream.read(binary);
        } catch (IOException e) {
            Assert.fail("IOException while reading binary for " + fullName + ": " + e.getMessage());
        }

        return new NanoAppBinary(binary);
    }

    /**
     * Read the nanoapp to an InputStream object.
     *
     * @param context   the Context to find the asset resources
     * @param fullName  the fullName of the nanoapp
     * @return the InputStream of the nanoapp
     */
    public static InputStream getNanoAppInputStream(Context context, String fullName) {
        InputStream inputStream = null;
        try {
            inputStream = (getExternalNanoAppPath() == null)
                    ? context.getAssets().open(fullName) :
                    new FileInputStream(new File(fullName));
        } catch (IOException e) {
            Assert.fail("Could not find asset " + fullName + ": "
                        + e.toString());
        }
        return inputStream;
    }

    /**
     * Converts a list of integers to a byte array.
     *
     * @param intArray the integer values
     * @return the byte[] array containing the integer values in byte order
     */
    public static byte[] intArrayToByteArray(int[] intArray) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * intArray.length);
        for (int i = 0; i < intArray.length; i++) {
            buffer.putInt(intArray[i]);
        }

        return buffer.array();
    }

    /**
     * Determines if the device under test is in the allowlist to run CHQTS.
     *
     * The device-side test currently skips the test only if the Context Hub
     * Service does not exist, but the Android framework currently exposes it unconditionally.
     * This method should be used to skip the test if the test device is not in the allowlist and
     * no Context Hub exists in order to avoid false positive failures. In the future, the framework
     * should be modified to only expose the service if CHRE is supported on the device. This hack
     * should then only be used on Android version that do not have that capability.
     *
     * @return true if the device is in the allowlist, false otherwise
     */
    public static boolean deviceInAllowlist() {
        DynamicConfigDeviceSide deviceDynamicConfig = getDynamicConfig();
        List<String> configValues = deviceDynamicConfig.getValues("chre_device_whitelist");
        Assert.assertTrue("Could not find device allowlist from dynamic config",
                configValues != null);

        String deviceName = Build.DEVICE;
        for (String element : configValues) {
            if (element.equals(deviceName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines if the device under test is in the denylist to NOT run CHQTS.
     *
     * The denylist is structured as "device,platform_id,max_chre_version", and characterizes
     * if a device should skip running CHQTS. For platform_id, and max_chre_version, "*" denotes
     * "matches everything".
     *
     * @return true if the device is in the denylist, false otherwise
     */
    public static boolean deviceInDenylist() {
        DynamicConfigDeviceSide deviceDynamicConfig = getDynamicConfig();
        List<String> configValues = deviceDynamicConfig.getValues("chre_device_blacklist");
        Assert.assertTrue("Could not find device denylist from dynamic config",
                configValues != null);

        String deviceName = Build.DEVICE;
        for (String element : configValues) {
            String[] delimited = element.split(",");
            if (delimited.length != 0 && delimited[0].equals(deviceName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the path of the nanoapps for a CHRE implementation using the platform ID.
     *
     * The dynamic configuration file for GtsGmscoreHostTestCases defines the key
     * 'chre_platform_id_nanoapp_dir_pairs', whose value is a list of strings of the form
     * '{platformId},{assetDirectoryName}', where platformId is one defined by the Context Hub
     * and is returned by ContextHubInfo.getChrePlatformId().
     *
     * @param platformId the platform ID of the hub
     * @return the path to the nanoapps
     */
    private static String getNanoAppBinaryPathFromPlatformId(long platformId) {
        DynamicConfigDeviceSide deviceDynamicConfig = getDynamicConfig();
        List<String> configValues =
                deviceDynamicConfig.getValues("chre_platform_id_nanoapp_dir_pairs");
        Assert.assertTrue("Could not find nanoapp asset list from dynamic config",
                configValues != null);

        String platformIdHexString = Long.toHexString(platformId);
        String path = null;
        for (String element : configValues) {
            String[] delimited = element.split(",");
            if (delimited.length == 2 && delimited[0].equals(platformIdHexString)) {
                path = delimited[1];
                break;
            }
        }

        Assert.assertTrue("Could not find known asset directory for hub with platform ID = 0x"
                + platformIdHexString, path != null);
        return path;
    }

    /**
     * Returns the path for nanoapps of a CHRE implementation using the Context Hub name.
     *
     * The dynamic configuration file for GtsGmscoreHostTestCases defines the key
     * 'chre_hubname_nanoapp_dir_pairs', whose value is a list of strings of the form
     * '{hubName},{assetDirectoryName}', where hubName is one defined by the Context Hub
     * and is returned by ContextHubInfo.getName(). This method should be used instead of
     * getNanoAppBinaryPathFromPlatformId() prior to Android P.
     *
     * @param contextHubName the name of the hub
     * @return the path to the nanoapps
     */
    private static String getNanoAppBinaryPathFromHubName(String contextHubName) {
        DynamicConfigDeviceSide deviceDynamicConfig = getDynamicConfig();
        List<String> configValues =
                deviceDynamicConfig.getValues("chre_hubname_nanoapp_dir_pairs");
        Assert.assertTrue("Could not find nanoapp asset list from dynamic config",
                configValues != null);

        String path = null;
        for (String element : configValues) {
            String[] delimited = element.split(",");
            if (delimited.length == 2 && delimited[0].equals(contextHubName)) {
                path = delimited[1];
                break;
            }
        }

        Assert.assertTrue("Could not find known asset directory for hub " + contextHubName,
                path != null);
        return path;
    }

    /**
     * @return the device side dynamic config for GtsGmscoreHostTestCases or
     *         GtsLocationContextMultiDeviceTestCases
     */
    private static DynamicConfigDeviceSide getDynamicConfig() {
        DynamicConfigDeviceSide deviceDynamicConfig = null;
        for (String deviceDynamicConfigName: DEVICE_DYNAMIC_CONFIG_NAMES) {
            try {
                deviceDynamicConfig = new DynamicConfigDeviceSide(deviceDynamicConfigName);
            } catch (XmlPullParserException e) {
                Assert.fail(e.getMessage());
            } catch (IOException e) {
                // Not found - try again
            }
        }

        if (deviceDynamicConfig == null) {
            Assert.fail("Could not get the device dynamic config.");
        }
        return deviceDynamicConfig;
    }

    /**
     * Returns the path to external nanoapps. This method should be used to debug the test
     * with custom unbundled nanoapps, and must not be used in actual certification.
     *
     * @return external nanoapp path, null if no externalNanoAppPath passed in
     */
    public static String getExternalNanoAppPath() {
        if (multiDeviceExternalNanoappPath != null) {
            return multiDeviceExternalNanoappPath;
        }

        Bundle extras = InstrumentationRegistry.getArguments();
        return (extras == null) ? null : extras.getString("externalNanoAppPath");
    }

    /**
     * Runs various checks to decide if the platform should run CHQTS.
     *
     * @param context The context at which the test should run.
     * @param manager The ContextHubManager on this app.
     */
    public static void checkDeviceShouldRunTest(Context context, ContextHubManager manager) {
        boolean supportsContextHub;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            supportsContextHub =
                    context.getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_CONTEXT_HUB);
            Assert.assertTrue("ContextHubManager must be null if feature is not supported.",
                    supportsContextHub || manager == null);
        } else {
            supportsContextHub = (manager != null);
        }
        Assume.assumeTrue("Device does not support Context Hub, skipping test", supportsContextHub);

        int numContextHubs;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            numContextHubs = manager.getContextHubs().size();
        } else {
            int[] handles = manager.getContextHubHandles();
            Assert.assertNotNull(handles);
            numContextHubs = handles.length;
        }

        // Only use allowlist logic on builds that do not require the Context Hub feature flag.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Use allowlist on platforms that reports no Context Hubs to prevent false positive
            // failures on devices that do not actually support CHRE.
            Assume.assumeTrue(
                    "Device not in allowlist and does not have Context Hub, skipping test",
                    numContextHubs != 0 || deviceInAllowlist());
        }

        // Use a denylist on platforms that should not run CHQTS.
        Assume.assumeTrue("Device is in denylist, skipping test", !deviceInDenylist());
    }
}
