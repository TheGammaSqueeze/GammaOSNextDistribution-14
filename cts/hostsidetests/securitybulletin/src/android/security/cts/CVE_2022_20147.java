/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.sts.common.util.TombstoneUtils;
import com.android.sts.common.util.TombstoneUtils.Config.BacktraceFilterPattern;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2022_20147 extends NonRootSecurityTestCase {
    /**
     * b/221216105
     * Vulnerability Behaviour: SIGSEGV in self
     * Vulnerable Library: libnfc-nci (As per AOSP code)
     * Vulnerable Function: nfa_dm_check_set_config (As per AOSP code)
     */
    @AsbSecurityTest(cveBugId = 221216105)
    @Test
    public void testPocCVE_2022_20147() {
        try {
            AdbUtils.assumeHasNfc(getDevice());
            assumeIsSupportedNfcDevice(getDevice());
            pocPusher.only64();
            String signals[] = { TombstoneUtils.Signals.SIGSEGV };
            String binaryName = "CVE-2022-20147";
            AdbUtils.pocConfig testConfig = new AdbUtils.pocConfig(binaryName,
                    getDevice());
            testConfig.config = new TombstoneUtils.Config()
                    .setProcessPatterns(Pattern.compile(binaryName))
                    .setBacktraceIncludes(new BacktraceFilterPattern(
                            "libnfc-nci", "nfa_dm_check_set_config"));
            testConfig.config.setBacktraceExcludes(
                    new BacktraceFilterPattern("libdl", "__cfi_slowpath"));
            testConfig.config.setSignals(signals);
            AdbUtils.runPocAssertNoCrashesNotVulnerable(testConfig);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
