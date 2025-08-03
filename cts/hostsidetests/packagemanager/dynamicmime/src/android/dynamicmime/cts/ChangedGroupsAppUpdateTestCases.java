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

package android.dynamicmime.cts;

import com.android.compatibility.common.util.ApiTest;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Invokes device-side changed groups app update tests as is, no need for any host-side setup
 */
@ApiTest(apis = {
        "android.content.pm.PackageManager#getMimeGroup",
        "android.content.pm.PackageManager#setMimeGroup"
})
@RunWith(DeviceJUnit4ClassRunner.class)
public class ChangedGroupsAppUpdateTestCases extends BaseHostJUnit4Test {
    private static final String PACKAGE_TEST_APP = "android.dynamicmime.testapp";

    @Test
    public void testUpdateRemoveEmptyGroup()
            throws DeviceNotAvailableException {
        runDeviceTestsWithMethodName("testUpdateRemoveEmptyGroup");
    }

    @Test
    public void testUpdateRemoveNonEmptyGroup()
            throws DeviceNotAvailableException {
        runDeviceTestsWithMethodName("testUpdateRemoveNonEmptyGroup");
    }

    private void runDeviceTestsWithMethodName(String methodName)
            throws DeviceNotAvailableException {
        runDeviceTests(PACKAGE_TEST_APP, PACKAGE_TEST_APP + ".update.ChangedGroupsTest",
                methodName);
    }
}
