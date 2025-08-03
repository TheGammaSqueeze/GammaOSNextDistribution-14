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

package android.icu.extratest.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import android.icu.dev.test.TestFmwk;
import android.icu.impl.JavaTimeZone;
import android.icu.testsharding.MainTestShard;
import android.icu.util.TimeZone;

@MainTestShard
@RunWith(JUnit4.class)
public class TimeZoneTest extends TestFmwk {

    /**
     * Test if an {@link android.icu.impl.JavaTimeZone} instance can be obtained for all tz ids.
     */
    @Test
    public void testGetTimezone_JavaTimeZone() {
        for (String tzId : java.util.TimeZone.getAvailableIDs()) {
            TimeZone tz = TimeZone.getTimeZone(tzId, TimeZone.TIMEZONE_JDK);
            assertNotNull("Time zone is null: " + tzId, tz);
            assertTrue("Time zone is not an instance of JavaTimeZone: " + tzId,
                tz instanceof JavaTimeZone);
        }
    }
}