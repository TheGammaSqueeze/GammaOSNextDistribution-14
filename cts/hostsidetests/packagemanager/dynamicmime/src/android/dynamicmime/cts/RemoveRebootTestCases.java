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

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Remove mime type reboot test cases
 *
 * Reuses existing test cases from {@link android.dynamicmime.testapp.SingleAppTest}
 * by "inserting" device reboot between setup part (MIME group commands) and verification part
 * (MIME group assertions) in each test case
 *
 * @see android.dynamicmime.testapp.reboot.PreRebootSingleAppTest
 * @see android.dynamicmime.testapp.reboot.PostRebootSingleAppTest
 * @see #runTestWithReboot(String, String)
 */
@ApiTest(apis = {
        "android.content.pm.PackageManager#getMimeGroup",
        "android.content.pm.PackageManager#setMimeGroup"
})
@RunWith(DeviceJUnit4ClassRunner.class)
public class RemoveRebootTestCases extends RebootTestCaseBase {

    @Test
    public void testRemoveExactType() throws DeviceNotAvailableException {
        runTestWithReboot("SingleAppTest", "testRemoveExactType");
    }

    @Test
    public void testRemoveDifferentType() throws DeviceNotAvailableException {
        runTestWithReboot("SingleAppTest", "testRemoveDifferentType");
    }

    @Test
    public void testRemoveSameBaseType_keepSpecific() throws DeviceNotAvailableException {
        runTestWithReboot("SingleAppTest", "testRemoveSameBaseType_keepSpecific");
    }

    @Test
    public void testRemoveSameBaseType_keepWildcard() throws DeviceNotAvailableException {
        runTestWithReboot("SingleAppTest", "testRemoveSameBaseType_keepWildcard");
    }

    @Test
    public void testRemoveAnyType_keepSpecific() throws DeviceNotAvailableException {
        runTestWithReboot("SingleAppTest", "testRemoveAnyType_keepSpecific");
    }

    @Test
    public void testRemoveAnyType_keepWildcard() throws DeviceNotAvailableException {
        runTestWithReboot("SingleAppTest", "testRemoveAnyType_keepWildcard");
    }
}
