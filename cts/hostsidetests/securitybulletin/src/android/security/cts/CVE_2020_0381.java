/**
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

package android.security.cts;

import static org.junit.Assume.assumeFalse;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.sts.common.util.TombstoneUtils;
import com.android.sts.common.util.TombstoneUtils.Config.BacktraceFilterPattern;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2020_0381 extends NonRootSecurityTestCase {

    /**
     * b/150159669
     * Vulnerability Behaviour: SIGSEGV in self
     */
    @AsbSecurityTest(cveBugId = 150159669)
    @Test
    public void testPocCVE_2020_0381() throws Exception {
        assumeFalse(moduleIsPlayManaged("com.google.android.media"));
        String binaryName = "CVE-2020-0381";
        String inputFiles[] = {"cve_2020_0381.xmf", "cve_2020_0381.info"};
        String signals[] = {TombstoneUtils.Signals.SIGSEGV};
        AdbUtils.pocConfig testConfig = new AdbUtils.pocConfig(binaryName, getDevice());
        testConfig.config = new TombstoneUtils.Config().setProcessPatterns(Pattern.compile(binaryName))
                .setBacktraceIncludes(new BacktraceFilterPattern("libmidiextractor", "Parse_ptbl"));
        testConfig.config.setSignals(signals);
        testConfig.arguments =
                AdbUtils.TMP_PATH + inputFiles[0] + " " + AdbUtils.TMP_PATH + inputFiles[1];
        testConfig.inputFiles = Arrays.asList(inputFiles);
        testConfig.inputFilesDestination = AdbUtils.TMP_PATH;
        AdbUtils.runPocAssertNoCrashesNotVulnerable(testConfig);
    }
}
