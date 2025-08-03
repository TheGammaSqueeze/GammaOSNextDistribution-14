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

package com.android.net.module.util;

import static android.net.InetAddresses.parseNumericAddress;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import static com.android.net.module.util.ConnectivityUtils.isIPv6ULA;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for ConnectivityUtils */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ConnectivityUtilsTest {
    @Test
    public void testIsIPv6ULA() {
        assertTrue(isIPv6ULA(parseNumericAddress("fc00::")));
        assertTrue(isIPv6ULA(parseNumericAddress("fc00::1")));
        assertTrue(isIPv6ULA(parseNumericAddress("fc00:1234::5678")));
        assertTrue(isIPv6ULA(parseNumericAddress("fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff")));

        assertFalse(isIPv6ULA(parseNumericAddress("fe00::")));
        assertFalse(isIPv6ULA(parseNumericAddress("2480:1248::123:456")));
    }
}
