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
public class CVE_2023_40077 extends NonRootSecurityTestCase {

    // b/298057702
    // Vulnerability Behaviour: SIGSEGV in self due to use-after-free
    // Vulnerable Library: libstagefright_foundation (As per AOSP code)
    // Vulnerable Function(s): MetaDataBase::setData (As per AOSP code)
    @AsbSecurityTest(cveBugId = 298057702)
    @Test
    public void testPocCVE_2023_40077() {
        try {
            final String library = "libstagefright_foundation";
            final String binaryName = "CVE-2023-40077";
            String[] signals = {TombstoneUtils.Signals.SIGSEGV};
            TombstoneUtils.Config crashConfig =
                    new TombstoneUtils.Config()
                            .setProcessPatterns(binaryName)
                            .setBacktraceIncludes(
                                    new BacktraceFilterPattern(library, "MetaDataBase::setData"))
                            .setSignals(signals)
                            .setIgnoreLowFaultAddress(true);
            NativePoc poc =
                    NativePoc.builder()
                            .pocName(binaryName)
                            .asserter(assertNoCrash(crashConfig))
                            .build();
            // Time taken for poc to complete is around 3 seconds. Hence choosing the timeout as
            // 30 seconds to facilitate a minimum of 10 runs.
            final long timeout = 30_000L;
            long startTime = System.currentTimeMillis();
            // The expected UAF crash gets reproduced at least once in every 10 runs, so a buffer
            // of 10 is added. Hence keeping the number of runs as 20.
            int runs = 20;
            // Due to the race condition, it is observed that an unrelated crash occurs
            // instead of the expected UAF crash in some runs and the PoC exits. This
            // causes an ASSUMPTION FAILURE. Therefore, the PoC is run in a loop for 20
            // times or for 30 seconds(whichever ends first), to ignore any unrelated crashes.
            do {
                try {
                    poc.run(this);
                } catch (Exception e) {
                    // Ignore exceptions due to any unrelated crash.
                }
                runs--;
            } while ((System.currentTimeMillis() - startTime <= timeout) && (runs > 0));
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
