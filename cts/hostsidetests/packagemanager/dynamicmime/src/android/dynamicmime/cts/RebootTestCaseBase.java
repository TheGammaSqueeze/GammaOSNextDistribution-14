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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

/**
 * Reboot test case Base
 *
 * The base class provides the basic methods to reuse existing test cases from
 * {@link android.dynamicmime.testapp.ComplexFilterTest} and
 * {@link android.dynamicmime.testapp.SingleAppTest} by "inserting" device reboot between
 * setup part (MIME group commands) and verification part (MIME group assertions) in each test case
 *
 * @see android.dynamicmime.testapp.reboot.PreRebootSingleAppTest
 * @see android.dynamicmime.testapp.reboot.PostRebootSingleAppTest
 * @see #runTestWithReboot(String, String)
 */

public class RebootTestCaseBase extends BaseHostJUnit4Test {
    private static final String PACKAGE_TEST_APP = "android.dynamicmime.testapp";
    private static final String PACKAGE_REBOOT_TESTS = PACKAGE_TEST_APP + ".reboot";

    private static final int SETTINGS_WRITE_TIMEOUT_MS = 10_000;

    protected void runTestWithReboot(String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        runPreReboot(testClassName, testMethodName);
        waitForSettingsWrite();
        getDevice().reboot();
        runPostReboot(testClassName, testMethodName);
    }

    protected void runPostReboot(String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        runDeviceTests(PACKAGE_TEST_APP, PACKAGE_REBOOT_TESTS + ".PostReboot" + testClassName,
                testMethodName);
    }

    protected void waitForSettingsWrite() {
        RunUtil.getDefault().sleep(SETTINGS_WRITE_TIMEOUT_MS);
    }

    protected void runPreReboot(String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        runDeviceTests(PACKAGE_TEST_APP, PACKAGE_REBOOT_TESTS + ".PreReboot" + testClassName,
                testMethodName);
    }
}
