/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.app.ActivityManager;
import android.content.Context;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class RunningAppProcessInfoTest extends StsExtraBusinessLogicTestCase {
    /*
     * This test verifies severity vulnerability: apps can bypass the L restrictions in
     * getRunningTasks()is fixed. The test tries to get current RunningAppProcessInfo and passes
     * the test if it is not able to get other process information.
     */

    @AsbSecurityTest(cveBugId = 20034603)
    @Test
    public void testRunningAppProcessInfo() {
        ActivityManager amActivityManager =
                (ActivityManager)
                        getInstrumentation()
                                .getContext()
                                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appList =
                amActivityManager.getRunningAppProcesses();

        // Assembles app list for logging
        List<String> processNames =
                appList.stream()
                        .map((processInfo) -> processInfo.processName)
                        .collect(Collectors.toList());

        // The test will pass if it is able to get only its process info
        assertTrue(
                "Device is vulnerable to CVE-2015-3833. Running app processes: "
                        + processNames.toString(),
                (appList.size() == 1));
    }
}
