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
package android.car.apitest;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static org.junit.Assert.fail;

import android.platform.test.annotations.Presubmit;
import android.text.TextUtils;

import com.android.compatibility.common.util.NonApiTest;

import org.junit.Test;

@Presubmit
public final class PreInstalledPackagesTest extends CarLessApiTestBase {


    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing package allowlist, which is not "
            + "an API or CDD requirement")
    public void testNoCriticalErrors_currentMode() {
        assertNoCriticalErrors(/* enforceMode= */ false);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing package allowlist, which is not "
            + "an API or CDD requirement")
    public void testNoCriticalErrors_enforceMode() {
        assertNoCriticalErrors(/* enforceMode= */ true);
    }

    private static void assertNoCriticalErrors(boolean enforceMode) {
        String cmd = "cmd user report-system-user-package-whitelist-problems --critical-only%s";
        String mode =  enforceMode ? " --mode 1" : "";
        String result = runShellCommand(cmd, mode);
        if (!TextUtils.isEmpty(result)) {
            fail("Command '" + String.format(cmd, mode) + "' reported errors:\n" + result
                    + "\nPlease check go/aaos-packages-allowlisting to fix the test!");
        }
    }
}
