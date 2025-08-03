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
public class Poc19_05 extends NonRootSecurityTestCase {
    /**
     * CVE-2019-2257
     */
    @Test
    @AsbSecurityTest(cveBugId = 112303441)
    public void testPocCVE_2019_2257() throws Exception {
        String result = AdbUtils.runCommandLine(
                                "dumpsys package com.qualcomm.qti.telephonyservice", getDevice());
        assertFalse(result.contains(
                            "permission com.qualcomm.permission.USE_QTI_TELEPHONY_SERVICE"));
    }
}
