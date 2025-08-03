/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.cts.install.host;

import static com.android.cts.shim.lib.ShimPackage.SHIM_APEX_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;

import com.android.ddmlib.Log;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.rules.ExternalResource;

/**
 * Clears shim Apex if needed before and after each test to prevent flaky and reduce
 * reboot time.
 */
final class ShimApexRule extends ExternalResource {
    private static final String TAG = ShimApexRule.class.getSimpleName();
    private final BaseHostJUnit4Test mHostTest;

    ShimApexRule(BaseHostJUnit4Test hostTest) {
        mHostTest = hostTest;
    }

    @Override
    protected void before() throws Throwable {
        uninstallShimApexIfNecessary();
    }

    @Override
    protected void after() {
        try {
            uninstallShimApexIfNecessary();
        } catch (DeviceNotAvailableException e) {
            e.printStackTrace();
        }
    }

    /**
     * Uninstalls a shim apex only if it's latest version is installed on /data partition (i.e.
     * it has a version higher than {@code 1}).
     *
     * <p>This is purely to optimize tests run time. Since uninstalling an apex requires a reboot,
     * and only a small subset of tests successfully install an apex, this code avoids ~10
     * unnecessary reboots.
     */
    private void uninstallShimApexIfNecessary() throws DeviceNotAvailableException {
        if (getShimApex().sourceDir.startsWith("/system")) {
            // System version is active, nothing to uninstall.
            return;
        }
        // Non system version is active, need to uninstall it and reboot the device.
        Log.i(TAG, "Uninstalling shim apex");
        final String errorMessage =
                mHostTest.getDevice().uninstallPackage(SHIM_APEX_PACKAGE_NAME);
        if (errorMessage != null) {
            Log.e(TAG, "Failed to uninstall " + SHIM_APEX_PACKAGE_NAME + " : " + errorMessage);
            return;
        }

        mHostTest.getDevice().reboot();
        ITestDevice.ApexInfo shim = getShimApex();
        assertThat(shim.versionCode).isEqualTo(1L);
        assertThat(shim.sourceDir).startsWith("/system");
    }

    private ITestDevice.ApexInfo getShimApex() throws DeviceNotAvailableException {
        return mHostTest.getDevice().getActiveApexes().stream().filter(
                apex -> apex.name.equals(SHIM_APEX_PACKAGE_NAME)).findAny().orElseThrow(
                () -> new AssertionError("Can't find " + SHIM_APEX_PACKAGE_NAME));
    }
}
