/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.sts.common.util.TombstoneUtils;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2018_9537 extends NonRootSecurityTestCase {

    /**
     * b/112891564
     * Vulnerability Behaviour: SIGSEGV in self (Android P),
     *                          SIGABRT in self (Android Q onward)
     */
    @AsbSecurityTest(cveBugId = 112891564)
    @Test
    public void testPocCVE_2018_9537() throws Exception {
        String binaryName = "CVE-2018-9537";
        String signals[] = {TombstoneUtils.Signals.SIGSEGV, TombstoneUtils.Signals.SIGBUS, TombstoneUtils.Signals.SIGABRT};
        AdbUtils.pocConfig testConfig = new AdbUtils.pocConfig(binaryName, getDevice());
        // example of check crash to skip:
        // Abort message: 'frameworks/av/media/extractors/mkv/MatroskaExtractor.cpp:548 CHECK(mCluster) failed.'
        testConfig.config = new TombstoneUtils.Config()
                .setProcessPatterns(binaryName)
                .appendAbortMessageExcludes("CHECK\\(.*?\\)");
        testConfig.config.setSignals(signals);
        AdbUtils.runPocAssertNoCrashesNotVulnerable(testConfig);
    }
}
