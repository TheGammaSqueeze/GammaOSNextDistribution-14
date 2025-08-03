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

import static org.junit.Assert.*;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class Poc16_05 extends NonRootSecurityTestCase {
    /**
     *  b/27555981
     */
    @Test
    @AsbSecurityTest(cveBugId = 27555981)
    public void testPocCVE_2016_2460() throws Exception {
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPoc("CVE-2016-2460", getDevice(), 60);

        String logcat =  AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatchesMultiLine("IGraphicBufferProducer_Info is Leaked", logcat);
    }

    /**
     *  b/27275324
     */
    @Test
    @AsbSecurityTest(cveBugId = 27275324)
    public void testPocCVE_2015_1805() throws Exception {
      AdbUtils.runPoc("CVE-2015-1805", getDevice(), TIMEOUT_NONDETERMINISTIC);
    }
}
