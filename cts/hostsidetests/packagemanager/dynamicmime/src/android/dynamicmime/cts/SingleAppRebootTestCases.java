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
 * Single app reboot test cases
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
public class SingleAppRebootTestCases extends RebootTestCaseBase {

    @Test
    public void testAddSimilarTypes() throws DeviceNotAvailableException {
        runTestWithReboot("SingleAppTest", "testAddSimilarTypes");
    }

    @Test
    public void testAddDifferentTypes() throws DeviceNotAvailableException {
        runTestWithReboot("SingleAppTest", "testAddDifferentTypes");
    }

    @Test
    public void testResetWithoutIntersection() throws DeviceNotAvailableException {
        runTestWithReboot("SingleAppTest", "testResetWithoutIntersection");
    }

    @Test
    public void testResetWithIntersection() throws DeviceNotAvailableException {
        runTestWithReboot("SingleAppTest", "testResetWithIntersection");
    }

    @Test
    public void testClear() throws DeviceNotAvailableException {
        runTestWithReboot("SingleAppTest", "testClear");
    }
}
