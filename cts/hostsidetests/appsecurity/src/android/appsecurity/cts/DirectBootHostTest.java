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

package android.appsecurity.cts;

import com.android.tradefed.util.RunUtil;
import static android.appsecurity.cts.Utils.waitForBootCompleted;

import static com.android.compatibility.common.util.PropertyUtil.getFirstApiLevel;
import static com.android.compatibility.common.util.PropertyUtil.getVendorApiLevel;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Set of tests that verify behavior of direct boot, if supported.
 * <p>
 * Note that these tests drive PIN setup manually instead of relying on device
 * administrators, which are not supported by all devices.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class DirectBootHostTest extends BaseHostJUnit4Test {
    private static final String TAG = "DirectBootHostTest";

    private static final String PKG = "com.android.cts.encryptionapp";
    private static final String CLASS = PKG + ".EncryptionAppTest";
    private static final String APK = "CtsEncryptionApp.apk";

    private static final String OTHER_APK = "CtsSplitApp.apk";
    private static final String OTHER_PKG = "com.android.cts.splitapp";

    private static final String FEATURE_DEVICE_ADMIN = "feature:android.software.device_admin";
    private static final String FEATURE_SECURE_LOCK_SCREEN =
            "feature:android.software.secure_lock_screen";
    private static final String FEATURE_AUTOMOTIVE = "feature:android.hardware.type.automotive";
    private static final String FEATURE_SECURITY_MODEL_COMPATIBLE =
            "feature:android.hardware.security.model.compatible";

    @Before
    public void setUp() throws Exception {
        Utils.prepareSingleUser(getDevice());
        assertNotNull(getAbi());
        assertNotNull(getBuild());

        getDevice().uninstallPackage(PKG);
        getDevice().uninstallPackage(OTHER_PKG);
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(PKG);
        getDevice().uninstallPackage(OTHER_PKG);
    }

    /**
     * Automotive devices MUST use FBE.
     */
    @Test
    public void testAutomotiveFbe() throws Exception {
        assumeSupportedDevice();
        assumeTrue("Device not automotive; skipping test", isAutomotiveDevice());
        assertTrue("Automotive devices must use FBE", fbeEnabled());
    }

    /**
     * If device uses FBE, verify the direct boot lifecycle.
     */
    @Test
    public void testDirectBoot() throws Exception {
        assumeSupportedDevice();
        assumeTrue("Device doesn't use FBE; skipping test", fbeEnabled());
        doDirectBootTest(true);
    }

    /**
     * If device doesn't use FBE, verify the legacy lifecycle.
     */
    @Test
    public void testNoDirectBoot() throws Exception {
        assumeSupportedDevice();
        assumeFalse("Device uses FBE; skipping test", fbeEnabled());
        doDirectBootTest(false);
    }

    public void doDirectBootTest(boolean fbeEnabled) throws Exception {
        try {
            // Set up test app and secure lock screens
            new InstallMultiple().addFile(APK).run();
            new InstallMultiple().addFile(OTHER_APK).run();

            // To receive boot broadcasts, kick our other app out of stopped state
            getDevice().executeShellCommand("am start -a android.intent.action.MAIN"
                    + " --user current"
                    + " -c android.intent.category.LAUNCHER com.android.cts.splitapp/.MyActivity");

            // Give enough time for PackageManager to persist stopped state
            RunUtil.getDefault().sleep(15000);

            runDeviceTestsAsCurrentUser(PKG, CLASS, "testSetUp");

            // Give enough time for vold to update keys
            RunUtil.getDefault().sleep(15000);

            // Reboot system into known state with keys ejected
            getDevice().rebootUntilOnline();
            waitForBootCompleted(getDevice());

            if (fbeEnabled) {
                runDeviceTestsAsCurrentUser(PKG, CLASS, "testVerifyLockedAndDismiss");
            } else {
                runDeviceTestsAsCurrentUser(PKG, CLASS, "testVerifyUnlockedAndDismiss");
            }

        } finally {
            try {
                // Remove secure lock screens and tear down test app
                runDeviceTestsAsCurrentUser(PKG, CLASS, "testTearDown");
            } finally {
                getDevice().uninstallPackage(PKG);

                // Get ourselves back into a known-good state
                getDevice().rebootUntilOnline();
                getDevice().waitForDeviceAvailable();
            }
        }
    }

    private void runDeviceTestsAsCurrentUser(
            String packageName, String testClassName, String testMethodName)
                throws DeviceNotAvailableException {
        Utils.runDeviceTestsAsCurrentUser(getDevice(), packageName, testClassName, testMethodName);
    }

    private boolean fbeEnabled() throws Exception {
        return "file".equals(getDevice().getProperty("ro.crypto.type"));
    }

    private void assumeSupportedDevice() throws Exception {
        assumeTrue("Skipping test: FEATURE_DEVICE_ADMIN missing.",
                getDevice().hasFeature(FEATURE_DEVICE_ADMIN));
        assumeTrue("Skipping test: FEATURE_SECURE_LOCK_SCREEN missing.",
                getDevice().hasFeature(FEATURE_SECURE_LOCK_SCREEN));
        // This feature name check only applies to devices that first shipped with
        // SC or later.
        final int firstApiLevel =
                Math.min(getFirstApiLevel(getDevice()), getVendorApiLevel(getDevice()));
        if (firstApiLevel >= 31) {
            assumeTrue("Skipping test: FEATURE_SECURITY_MODEL_COMPATIBLE missing.",
                    getDevice().hasFeature("feature:android.hardware.security.model.compatible"));
        }
    }

    private boolean isAutomotiveDevice() throws Exception {
        return getDevice().hasFeature(FEATURE_AUTOMOTIVE);
    }

    private class InstallMultiple extends BaseInstallMultiple<InstallMultiple> {
        public InstallMultiple() {
            super(getDevice(), getBuild(), getAbi());
            addArg("--force-queryable");
        }
    }
}
