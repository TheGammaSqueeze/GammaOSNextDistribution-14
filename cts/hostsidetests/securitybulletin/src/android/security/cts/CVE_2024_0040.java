/*
 * Copyright (C) 2024 The Android Open Source Project
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
public class CVE_2024_0040 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 300007708)
    @Test
    public void testPocCVE_2024_0040() {
        try {
            final String binaryName = "CVE-2024-0040";
            String[] signals = {TombstoneUtils.Signals.SIGSEGV};

            TombstoneUtils.Config crashConfig =
                    new TombstoneUtils.Config()
                            .setProcessPatterns(binaryName)
                            .setBacktraceIncludes(
                                    new BacktraceFilterPattern("libmtp", "setParameter"))
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
