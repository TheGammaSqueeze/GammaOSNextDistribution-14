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

package com.android.server.wifi.util;

import static org.junit.Assert.*;

import android.net.wifi.WifiManager;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.WifiBaseTest;

import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Pattern;

/**
 * Unit tests for {@link com.android.server.wifi.util.LastCallerInfoManager}.
 */
@SmallTest
public class LastCallerInfoManagerTest extends WifiBaseTest {

    private LastCallerInfoManager mLastCallerInfoManager = new LastCallerInfoManager();
    /**
     * Test that the dump matches the values put
     */
    @Test
    public void testPutAndDump() throws Exception {
        mLastCallerInfoManager.put(WifiManager.API_SOFT_AP, 10, 11, 12, "Package", true);

        StringWriter sw = new StringWriter();
        mLastCallerInfoManager.dump(new PrintWriter(sw));
        String serviceDump = sw.toString();
        Pattern logLineRegex = Pattern.compile(
                "SoftAp: tid=10 uid=11 pid=12 packageName=Package toggleState=true");
        assertTrue("dump did not contain the expected log"
                + ": " + serviceDump + "\n", logLineRegex.matcher(serviceDump).find());
    }

    @Test
    public void testGet() {
        // expect null before anything is set
        assertNull(mLastCallerInfoManager.get(WifiManager.API_SCANNING_ENABLED));

        // Test getting some LastCallerInfo
        mLastCallerInfoManager.put(WifiManager.API_SCANNING_ENABLED, 10, 11, 12, "Package", true);
        LastCallerInfoManager.LastCallerInfo lastCallerInfo =
                mLastCallerInfoManager.get(WifiManager.API_SCANNING_ENABLED);
        assertEquals("Package", lastCallerInfo.getPackageName());
        assertTrue(lastCallerInfo.getToggleState());
    }

}
