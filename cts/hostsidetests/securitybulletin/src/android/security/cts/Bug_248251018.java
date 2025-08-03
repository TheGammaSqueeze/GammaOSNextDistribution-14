/*
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
public class Bug_248251018 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 248251018)
    public void testEmergencyInfo_cannotInteractAcrossUsers() throws Exception {
        String packageList =
                getDevice()
                        .executeShellV2Command("pm list package com.android.emergency")
                        .getStdout();
        if (!packageList.isEmpty()) {
            String result =
                    getDevice()
                            .executeShellV2Command("dumpsys package com.android.emergency")
                            .getStdout();
            assertFalse(result.contains("android.permission.INTERACT_ACROSS_USERS_FULL"));
        }
    }
}
