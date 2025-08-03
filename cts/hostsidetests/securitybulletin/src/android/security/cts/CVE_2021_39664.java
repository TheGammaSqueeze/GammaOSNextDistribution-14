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
import com.android.sts.common.util.TombstoneUtils.Config.BacktraceFilterPattern;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_39664 extends NonRootSecurityTestCase {

    /**
     * b/203938029
     * Vulnerability Behaviour: SIGSEGV in self
     * Vulnerable Library: libandroidfw (As per AOSP code)
     * Vulnerable Function: android::LoadedPackage::Load (As per AOSP code)
     */
    @AsbSecurityTest(cveBugId = 203938029)
    @Test
    public void testPocCVE_2021_39664() throws Exception {
        String inputFiles[] = {"cve_2021_39664"};
        String signals[] = {TombstoneUtils.Signals.SIGSEGV};
        String binaryName = "CVE-2021-39664";
        AdbUtils.pocConfig testConfig = new AdbUtils.pocConfig(binaryName, getDevice());
        testConfig.config = new TombstoneUtils.Config().setProcessPatterns(Pattern.compile(binaryName))
                .setBacktraceIncludes(new BacktraceFilterPattern("libandroidfw",
                        "android::LoadedPackage::Load"));
        testConfig.config.setSignals(signals);
        testConfig.arguments = AdbUtils.TMP_PATH + inputFiles[0];
        testConfig.inputFiles = Arrays.asList(inputFiles);
        testConfig.inputFilesDestination = AdbUtils.TMP_PATH;
        AdbUtils.runPocAssertNoCrashesNotVulnerable(testConfig);
    }
}
