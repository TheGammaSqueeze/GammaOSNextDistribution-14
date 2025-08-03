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

package android.provider.cts.contacts.hostside;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.Log;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(DeviceJUnit4ClassRunner.class)
@Ignore("Runtime too long and may lock up device when it fails")
public class ShadowCallLogTest extends BaseHostJUnit4Test {
    private static final String TAG = ShadowCallLogTest.class.getSimpleName();

    private static final String PKG = "android.provider.cts.contacts.testapp";
    private static final String CLASS = PKG + ".CallLogDirectBootTest";
    private static final String APK = "CtsCallLogDirectBootApp.apk";

    @Before
    public void setUp() throws Exception {
        assertNotNull(getAbi());
        assertNotNull(getBuild());

        getDevice().uninstallPackage(PKG);
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(PKG);
    }

    @Test
    public void testDirectBootCallLog() throws Exception {
        if (!"file".equals(getDevice().getProperty("ro.crypto.type"))) {
            Log.i(TAG, "Device doesn't use FBE, skipping.");
            return;
        }
        try {
            Log.i(TAG, "Test starting");
            waitForBootCompleted(getDevice());
            // Set up test app and secure lock screens
            CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
            File apkFile = buildHelper.getTestFile(APK);
            getDevice().installPackage(apkFile, true);
            Log.i(TAG, "Package installed");

            setupDevicePassword();

            // Give enough time for vold to update keys
            RunUtil.getDefault().sleep(15000);

            Log.i(TAG, "Rebooting device");
            // Reboot system into known state with keys ejected
            getDevice().rebootUntilOnline();
            waitForBootCompleted(getDevice());

            assertTrue(runDeviceTests(PKG, CLASS, "testShadowCallComposerPicture"));
        } catch (Throwable t) {
            Log.e(TAG, "Error encountered: " + t);
        } finally {
            try {
                // Remove secure lock screens and tear down test app
                Log.i(TAG, "Attempting to remove device password");
                removeDevicePassword();
            } finally {
                Log.i(TAG, "Final cleanup");
                getDevice().uninstallPackage(PKG);

                // Get ourselves back into a known-good state
                getDevice().rebootUntilOnline();
                getDevice().waitForDeviceAvailable();
            }
        }
    }

    private void setupDevicePassword() throws Exception {
        Log.i(TAG, "running device password setup");
        ITestDevice device = getDevice();
        device.executeShellCommand("locksettings set-disabled false");
        device.executeShellCommand("locksettings set-pin 12345");
    }

    private void removeDevicePassword() throws Exception {
        Log.i(TAG, "clearing device password");
        ITestDevice device = getDevice();
        device.executeShellCommand("locksettings clear --old 12345");
        device.executeShellCommand("locksettings set-disabled true");
    }

    public static void waitForBootCompleted(ITestDevice device) throws Exception {
        for (int i = 0; i < 45; i++) {
            if (isBootCompleted(device)) {
                Log.d(TAG, "Yay, system is ready!");
                // or is it really ready?
                // guard against potential USB mode switch weirdness at boot
                RunUtil.getDefault().sleep(10 * 1000);
                return;
            }
            Log.d(TAG, "Waiting for system ready...");
            RunUtil.getDefault().sleep(1000);
        }
        throw new AssertionError("System failed to become ready!");
    }

    private static boolean isBootCompleted(ITestDevice device) throws Exception {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        try {
            device.getIDevice().executeShellCommand("getprop sys.boot_completed", receiver);
        } catch (AdbCommandRejectedException e) {
            // do nothing: device might be temporarily disconnected
            Log.d(TAG, "Ignored AdbCommandRejectedException while `getprop sys.boot_completed`");
        }
        String output = receiver.getOutput();
        if (output != null) {
            output = output.trim();
        }
        return "1".equals(output);
    }
}
