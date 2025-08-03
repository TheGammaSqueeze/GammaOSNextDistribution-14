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

package com.android.rkpdapp.hosttest;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class RkpdWidevineTests extends BaseHostJUnit4Test {
    private static final String WV_CERT_LOCATION = "/data/vendor/mediadrm/IDM1013/L1/oemcert.bin";

    private void deleteWidevineCert() throws Exception {
        assertWithMessage("Test requires ability to get root.")
                .that(getDevice().enableAdbRoot())
                .isTrue();
        getDevice().executeShellCommand("rm " + WV_CERT_LOCATION);
    }

    private void runTest(String testMethodName) throws Exception {
        String testPackagePrefix = null;
        Set<ITestDevice.ApexInfo> mActiveApexes = getDevice().getActiveApexes();
        for (ITestDevice.ApexInfo ap : mActiveApexes) {
            if ("com.android.rkpd".equals(ap.name)) {
                testPackagePrefix = "com.android.rkpdapp";
                break;
            } else if ("com.google.android.rkpd".equals(ap.name)) {
                testPackagePrefix = "com.google.android.rkpdapp";
                break;
            }
        }
        if (testPackagePrefix == null) {
            assertWithMessage("rkpd mainline module not installed").fail();
        }

        String testClassName = testPackagePrefix + ".wvtest.WidevineHostTestHelperTests";
        String testPackageName = "com.android.rkpdapp.e2etest";
        assertThat(runDeviceTests(testPackageName, testClassName, testMethodName))
                .isTrue();
    }

    @Test
    public void testIfProvisioningNeededIsConsistentWithSystemStatus() throws Exception {
        runTest("testIfProvisioningNeededIsConsistentWithSystemStatus");
    }

    @Test
    public void testWipeAndReprovisionCert() throws Exception {
        deleteWidevineCert();
        runTest("testProvisionWidevine");
    }
}
