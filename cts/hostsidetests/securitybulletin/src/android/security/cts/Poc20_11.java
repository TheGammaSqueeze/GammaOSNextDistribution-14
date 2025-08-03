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
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class Poc20_11 extends NonRootSecurityTestCase {

    /**
     * b/162741784
     */
    @Test
    @AsbSecurityTest(cveBugId = 162741784)
    public void testPocCVE_2020_0437() throws Exception {
        assumeFalse(moduleIsPlayManaged("com.google.android.cellbroadcast"));
        AdbUtils.runCommandLine("logcat -c", getDevice());
        AdbUtils.runCommandLine(
            "am broadcast " +
            "-a com.android.cellbroadcastreceiver.intent.action.MARK_AS_READ " +
            "-n com.android.cellbroadcastreceiver/.CellBroadcastReceiver " +
            "--el com.android.cellbroadcastreceiver.intent.extra.ID 1596185475000",
            getDevice());
        String logcat = AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatches("CellBroadcastContentProvider: failed to mark broadcast read", logcat);
    }
}
