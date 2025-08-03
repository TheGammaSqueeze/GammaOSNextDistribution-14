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
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_40114 extends NonRootSecurityTestCase {

    // b/243381410
    // Vulnerability Behaviour: Use after free in libmtp
    // Vulnerable Library: libmtp (As per AOSP code)
    // Vulnerable Function(s): MtpFfsHandle::sendEvent, MtpFfsHandle::doSendEvent (As per AOSP code)
    @AsbSecurityTest(cveBugId = 243381410)
    @Test
    public void testPocCVE_2023_40114() {
        ITestDevice device = null;
        String descriptorFilePath = null;
        try {
            final String library = "libmtp";
            final String process = "system_server";
            final String binaryName = "CVE-2023-40114";
            device = getDevice();

            // Create a file descriptor required for instantiating 'MtpFfsCompatHandle' in poc.cpp
            descriptorFilePath =
                    new File("/data/local/tmp/", "CVE_2023_40114_descriptor_file")
                            .getAbsolutePath();
            device.pushString("", descriptorFilePath);
            String[] signals = {TombstoneUtils.Signals.SIGSEGV};

            TombstoneUtils.Config crashConfig =
                    new TombstoneUtils.Config()
                            .setProcessPatterns(binaryName)
                            .setBacktraceIncludes(
                                    new BacktraceFilterPattern("libmtp", "MtpFfsHandle::sendEvent"),
                                    new BacktraceFilterPattern(
                                            "libmtp", "MtpFfsHandle::doSendEvent"))
                            .setSignals(signals);

            NativePoc.builder()
                    .assumePocExitSuccess(false)
                    .pocName(binaryName)
                    .args("CVE_2023_40114_descriptor_file")
                    .asserter(assertNoCrash(crashConfig))
                    .build()
                    .run(this);
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                device.deleteFile(descriptorFilePath);
            } catch (Exception e) {
                // ignore all exceptions
            }
        }
    }
}
