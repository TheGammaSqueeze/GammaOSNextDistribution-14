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

import static com.android.sts.common.NativePocCrashAsserter.assertNoCrash;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.NativePoc;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.sts.common.util.TombstoneUtils;
import com.android.sts.common.util.TombstoneUtils.Config.BacktraceFilterPattern;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_21241 extends NonRootSecurityTestCase {

    // b/271849189
    // Vulnerability Behaviour : SIGSEGV in self
    // Vulnerable Library: libnfc-nci (As per AOSP code)
    // Vulnerable Function: rw_i93_send_to_upper (As per AOSP code)
    @AsbSecurityTest(cveBugId = 271849189)
    @Test
    public void testPocCVE_2023_21241() {
        try {
            final String binaryName = "CVE-2023-21241";
            String[] signals = {TombstoneUtils.Signals.SIGSEGV};

            TombstoneUtils.Config crashConfig =
                    new TombstoneUtils.Config()
                            .setProcessPatterns(binaryName)
                            .setBacktraceIncludes(
                                    new BacktraceFilterPattern(
                                            "libnfc-nci", "rw_i93_send_to_upper"))
                            .setSignals(signals);

            NativePoc.builder()
                    .pocName(binaryName)
                    .asserter(assertNoCrash(crashConfig))
                    .build()
                    .run(this);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
