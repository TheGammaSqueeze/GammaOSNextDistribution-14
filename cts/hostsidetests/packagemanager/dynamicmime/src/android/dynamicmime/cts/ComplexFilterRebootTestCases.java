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
 * Complex filter reboot test cases
 *
 * Reuses existing test cases from {@link android.dynamicmime.testapp.ComplexFilterTest}
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
public class ComplexFilterRebootTestCases extends RebootTestCaseBase {
    private static final String CLASS_COMPLEX_FILTER_TEST = "ComplexFilterTest";

    @Test
    public void testMimeGroupsNotIntersect() throws DeviceNotAvailableException {
        runTestWithReboot("ComplexFilterTest", "testMimeGroupsNotIntersect");
    }

    @Test
    public void testMimeGroupsIntersect() throws DeviceNotAvailableException {
        runTestWithReboot("ComplexFilterTest", "testMimeGroupsIntersect");
    }

    @Test
    public void testMimeGroupNotIntersectWithStaticType() throws DeviceNotAvailableException {
        runTestWithReboot("ComplexFilterTest", "testMimeGroupNotIntersectWithStaticType");
    }

    @Test
    public void testMimeGroupIntersectWithStaticType() throws DeviceNotAvailableException {
        runTestWithReboot("ComplexFilterTest", "testMimeGroupIntersectWithStaticType");
    }

    @Test
    public void testRemoveTypeFromIntersection() throws DeviceNotAvailableException {
        runTestWithReboot("ComplexFilterTest", "testRemoveTypeFromIntersection");
    }

    @Test
    public void testRemoveIntersectionFromBothGroups() throws DeviceNotAvailableException {
        runTestWithReboot("ComplexFilterTest", "testRemoveIntersectionFromBothGroups");
    }

    @Test
    public void testRemoveStaticTypeFromMimeGroup() throws DeviceNotAvailableException {
        runTestWithReboot("ComplexFilterTest", "testRemoveStaticTypeFromMimeGroup");
    }
}
