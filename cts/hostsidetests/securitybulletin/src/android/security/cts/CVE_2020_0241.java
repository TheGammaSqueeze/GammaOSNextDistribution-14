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

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.sts.common.util.TombstoneUtils;
import com.android.sts.common.util.TombstoneUtils.Config.BacktraceFilterPattern;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2020_0241 extends NonRootSecurityTestCase {

    /**
     * b/151456667
     * Vulnerability Behavior : SIGABRT in self
     * Vulnerable Library     : libmediaplayerservice (As per AOSP code)
     * Vulnerable Function    : android::NuPlayer::NuPlayerStreamListener::NuPlayerStreamListener
                                (As per AOSP code)
     */
    @AsbSecurityTest(cveBugId = 151456667)
    @Test
    public void testPocCVE_2020_0241() throws Exception {
        pocPusher.only32();
        String binaryName = "CVE-2020-0241";
        AdbUtils.pocConfig testConfig = new AdbUtils.pocConfig(binaryName, getDevice());
        testConfig.config = new TombstoneUtils.Config().setProcessPatterns(Pattern.compile(binaryName))
                .setBacktraceIncludes(new BacktraceFilterPattern("libmediaplayerservice",
                        "android::NuPlayer::NuPlayerStreamListener::NuPlayerStreamListener"));
        String[] signals = {TombstoneUtils.Signals.SIGABRT};
        testConfig.config.setSignals(signals);
        testConfig.config.setAbortMessageIncludes(
                AdbUtils.escapeRegexSpecialChars("Pure virtual function called"));
        AdbUtils.runPocAssertNoCrashesNotVulnerable(testConfig);
    }
}
