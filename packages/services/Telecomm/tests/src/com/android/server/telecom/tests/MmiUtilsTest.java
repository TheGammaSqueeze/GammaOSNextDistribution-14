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

package com.android.server.telecom.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.server.telecom.MmiUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MmiUtilsTest extends TelecomTestCase {

    private static final String[] sDangerousDialStrings = {
        "*21*1234567#", // fwd unconditionally to 1234567,
        "*67*1234567#", // fwd to 1234567 when line is busy
        "*61*1234567#", // fwd to 1234567 when no one picks up
        "*62*1234567#", // fwd to 1234567 when out of range
        "*004*1234567#", // fwd to 1234567 when busy, not pickup up, out of range
        "*004*1234567#", // fwd to 1234567 conditionally
        "**21*1234567#", // fwd unconditionally to 1234567

        // north american vertical service codes

        "*094565678", // Selective Call Blocking/Reporting
        "*4278889", // Change Forward-To Number for Customer Programmable Call Forwarding Don't
                    // Answer
        "*5644456", // Change Forward-To Number for ISDN Call Forwarding
        "*6045677", // Selective Call Rejection Activation
        "*635678", // Selective Call Forwarding Activation
        "*64678899", // Selective Call Acceptance Activation
        "*683456", // Call Forwarding Busy Line/Don't Answer Activation
        "*721234", // Call Forwarding Activation
        "*77", // Anonymous Call Rejection Activation
        "*78", // Do Not Disturb Activation
    };

    private MmiUtils mMmiUtils = new MmiUtils();
    private static final String[] sNonDangerousDialStrings = {"*6712345678", "*272", "*272911"};

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testDangerousDialStringsDetected() throws Exception {
        for (String s : sDangerousDialStrings) {
            Uri.Builder b = new Uri.Builder();
            b.scheme("tel").opaquePart(s);
            assertTrue(mMmiUtils.isDangerousMmiOrVerticalCode(b.build()));
        }
    }

    @SmallTest
    @Test
    public void testNonDangerousDialStringsNotDetected() throws Exception {
        for (String s : sNonDangerousDialStrings) {
            Uri.Builder b = new Uri.Builder();
            b.scheme("tel").opaquePart(s);
            assertFalse(mMmiUtils.isDangerousMmiOrVerticalCode(b.build()));
        }
    }
}
