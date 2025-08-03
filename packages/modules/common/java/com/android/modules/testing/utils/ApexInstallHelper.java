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

package com.android.modules.testing.utils;

import static org.junit.Assert.assertTrue;

import android.cts.install.lib.host.InstallUtilsHost;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;

import com.android.internal.util.test.SystemPreparer;
import com.android.internal.util.test.SystemPreparer.DeviceProvider;
import com.android.internal.util.test.SystemPreparer.RebootStrategy;

import org.junit.rules.TemporaryFolder;

import java.io.File;

/**
 * Utility to help installing and updating apexes in tests as well as cleaning them up when the test
 * finishes.
 *
 * <p>Install vs updating: you can update any apex preinstalled in the system image as long as your
 * apex has a higher version than the one currently installed using
 * {@link #installApexAndReboot}. If you want to use an apex not preinstalled you can call
 * {@link #pushApexAndReboot}. After you push one (non-preinstalled) apex, you can update it using
 * either method.
 *
 * <p>In order to revert the apexes that were installed or pushed, call
 * {@link #revertChanges}. This normally happens from your {@code @AfterClass} method.
 *
 * <p>Requirements: the device must support apex updates and have root access via adb.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 *
 *     static private ApexInstallHelper sApexInstallHelper;
 *
 *     @BeforeClassWithInfo
 *     public static void beforeClassWithDevice(TestInformation testInformation)
 *             throws Exception {
 *          sApexInstallHelper = new ApexInstallHelper(testInformation);
 *          sApexInstallHelper.pushApexAndReboot("test_com.android.modules.example.apex");
 *          // assuming next line matches a preinstalled apex
 *          sApexInstallHelper.installApexAndReboot("art.apex");
 *     }
 *
 *      @AfterClass
 *      public static void afterClass() {
 *          sApexInstallHelper.revertChanges();
 *      }
 *
 *      // add your own @Test methods
 * }</pre>
 *
 * <p>Additionally, the apex files that you want to update or install need to be accessible
 * to the test:
 *
 * <pre>{@code
 *
 * java_test_host {
 *     name: "Example",
 *     data: [
 *         ":test_com.android.modules.example",
 *     ],
 *     static_libs: [
 *         "com.android.modules.testing.utils",
 *     ],
 * }</pre>
 *
 */
public class ApexInstallHelper {
    private final TemporaryFolder mTemporaryFolder;
    private final SystemPreparer mSystemPreparer;
    private final InstallUtilsHost mInstallUtilsHost;
    private final DeviceProvider mDeviceProvider;
    private boolean mCalledSetUp = false;

    public ApexInstallHelper(TestInformation testInformation) {
        this(testInformation, RebootStrategy.FULL);
    }

    public ApexInstallHelper(TestInformation testInformation, RebootStrategy rebootStrategy) {
        mDeviceProvider = testInformation::getDevice;
        mTemporaryFolder = new TemporaryFolder();
        mSystemPreparer = new SystemPreparer(mTemporaryFolder, rebootStrategy,
                /*testRuleDelegate=*/null, mDeviceProvider);
        mInstallUtilsHost = new InstallUtilsHost(testInformation);
    }

    private void setUp() throws Exception {
        if (mCalledSetUp) {
            return;
        }
        mCalledSetUp = true;
        mTemporaryFolder.create();
        // mSystemPreparer doesn't expect to be called to setUp

        ITestDevice device = mDeviceProvider.getDevice();
        if (!device.isAdbRoot()) {
            device.enableAdbRoot();
        }

        assertTrue("Device does not support updating APEX",
                mInstallUtilsHost.isApexUpdateSupported());
        assertTrue("Device requires root", device.isAdbRoot());
    }

    /**
     * Installs an apex (using {@code adb push}) on the device. This can be used to install a new
     * apex that is not preinstalled in the system image.
     *
     * @param apexFilename the filename of the apex to install.
     */
    public void pushApexAndReboot(String apexFilename) throws Exception {
        setUp();
        File apexFile = mInstallUtilsHost.getTestFile(apexFilename);
        mSystemPreparer.pushFile(apexFile, "/system/apex/" + apexFilename);
        mSystemPreparer.reboot();
    }

    /**
     * Installs an apex (using {@code adb install}) on the device. This can only be used to
     * install a newer version of an apex that is already installed on the device (either
     * preinstalled in the system image or previously installed using
     * {@link #installApexAndReboot}).
     *
     * @param apexFilename the filename of the apex which contains the update.
     */
    public void installApexAndReboot(String apexFilename) throws Exception {
        setUp();
        File apexFile = mInstallUtilsHost.getTestFile(apexFilename);
        mDeviceProvider.getDevice().installPackage(apexFile, true);
        mSystemPreparer.reboot();
    }

    /**
     * Calls this method to undo all installs and pushes performed by this helper when your test
     * has finished.
     *
     * <p>This should typically be called from your {@code @AfterClass} method.
     */
    public void revertChanges() {
        if (!mCalledSetUp) {
            return;
        }
        // in line with other tests, clean up system preparer before the temporary folder
        mSystemPreparer.after();
        mTemporaryFolder.delete();
        mCalledSetUp = false;
    }
}
