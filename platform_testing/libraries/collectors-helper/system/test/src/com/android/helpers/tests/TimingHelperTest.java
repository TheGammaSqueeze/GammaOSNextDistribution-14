/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.helpers.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import com.android.helpers.TimingHelper;

/**
 * Android Unit tests for {@link TimingHelper}
 *
 * <p>atest CollectorsHelperTest:com.android.helpers.tests.TimingHelperTest
 */
@RunWith(AndroidJUnit4.class)
public class TimingHelperTest {

    private static final String TAG = TimingHelperTest.class.getSimpleName();

    @Spy private TimingHelper helper;

    @Before
    public void setUp() {
        helper = new TimingHelper();
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetTimingLogs() throws IOException {
        String validLine = "15869 15885 I ForTimingCollector: total-bugreport-duration:183122";
        String invalidLine = "13622 13622 I BogusLog: This is an unrelated log.";
        String logcat = validLine + "\n" + invalidLine + "\n";
        InputStream is = new ByteArrayInputStream(logcat.getBytes("UTF-8"));
        ArrayList<String> logs = helper.getTimingLogs(is);
        assertTrue(logs.contains(validLine));
        assertFalse(logs.contains(invalidLine));
    }

    @Test
    public void testParseTimingInfo() {
        // parseTimingInfo is only given lines that contain "ForTimingCollector".
        String log = "15869 15885 I ForTimingCollector: total-bugreport-duration:183122";
        String[] info = helper.parseTimingInfo(log);
        assertEquals(2, info.length);
        assertEquals("total-bugreport-duration", info[0]);
        assertEquals("183122", info[1]);
    }

    @Test
    public void testParseTimingInfoInvalidLog() {
        // Tests the case in which a user-defined metric log is incorrectly formatted.
        String nonNumerical = "18472 18472 I ForTimingCollector: is-bug-report:true";
        assertNull(helper.parseTimingInfo(nonNumerical));

        // Tests the case in which the metric key and value are not separated correctly.
        String incorrectSeparator = "27131 27131 I ForTimingCollector: idle-duration-ms=3231";
        assertNull(helper.parseTimingInfo(incorrectSeparator));

        // Tests the case in which there are multiple colons in the metric.
        String multipleSeparators = "16312 16312 I ForTimingCollector: section:showmap-ms:57411";
        assertNull(helper.parseTimingInfo(multipleSeparators));
    }

    @Test
    public void testGetMetrics() {
        ArrayList<String> logs =
                new ArrayList<>(
                        Arrays.asList(
                                "05-10 10:34:18.271 15271 15271 I ForTimingCollector:"
                                        + " total-bugreport-duration:183122",
                                "05-10 10:35:54.311 15271 15271 I ForTimingCollector:"
                                        + " bugreport-compress-duration:1631",
                                "05-10 10:36:41.742 15271 15271 E ForTimingCollector:"
                                        + " This is an unrelated log.",
                                "05-10 10:37:21.374 15271 15271 I ForTimingCollector:"
                                        + " total-test-duration:413824"));
        doReturn(logs).when(helper).getTimingLogs(any());
        Map<String, Integer> metrics = helper.getMetrics();
        assertEquals(3, metrics.size());
        assertTrue(metrics.get("total-bugreport-duration").equals(183122));
        assertTrue(metrics.get("bugreport-compress-duration").equals(1631));
        assertTrue(metrics.get("total-test-duration").equals(413824));
    }
}
