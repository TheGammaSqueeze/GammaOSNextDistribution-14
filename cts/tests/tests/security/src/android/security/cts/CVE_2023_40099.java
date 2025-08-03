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

package android.security.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import com.google.common.io.ByteStreams;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_40099 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 261709193)
    @Test
    public void testPocCVE_2023_40099() {
        try {
            // Execute an invalid shell command
            Process process = Runtime.getRuntime().exec("am start-activity");

            // Fetch the output of the executed command
            String stdErrorOutput = new String(ByteStreams.toByteArray(process.getErrorStream()));

            // Without fix, the invalid shell command gets executed and throws
            // 'IllegalArgumentException'
            final String targetMethod =
                    "com.android.server.am.ActivityManagerService.onShellCommand";
            assertWithMessage("Device is vulnerable to b/261709193")
                    .that(
                            stdErrorOutput.contains(IllegalArgumentException.class.getName())
                                    && stdErrorOutput.contains(targetMethod))
                    .isFalse();
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
