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

package com.android.helpers.tests;

import static org.junit.Assert.assertEquals;

import androidx.test.runner.AndroidJUnit4;

import com.android.helpers.TimeInStateHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Android unit test for {@link TimeInStateHelper}
 *
 * <p>To run: atest CollectorsHelperTest:com.android.helpers.tests.TimeInStateHelperTest
 */
@RunWith(AndroidJUnit4.class)
public class TimeInStateHelperTest {
    private static final String METRIC_KEY_PREFIX = "time_in_state";
    private static final String AVG_FREQ_KEY_SUFFIX = "avg_freq";

    private File file1;
    private File file2;

    private TimeInStateHelper mTimeInStateHelper;

    private void writeToFile(File file, String content) {
        try (FileWriter writer = new FileWriter(file.getPath())) {
            writer.write(content);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private String constructKey(String key, String freq) {
        return METRIC_KEY_PREFIX + "_" + key + "_" + freq;
    }

    @Before
    public void setUp() {
        mTimeInStateHelper = new TimeInStateHelper();

        try {
            file1 = File.createTempFile("temp1", "time_in_state");
            file1.deleteOnExit();
            file2 = File.createTempFile("temp2", "time_in_state");
            file2.deleteOnExit();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testCollectTimeInState_noSource() {
        mTimeInStateHelper.startCollecting();
        mTimeInStateHelper.stopCollecting();
        Map<String, Long> results = mTimeInStateHelper.getMetrics();

        assertEquals(results.size(), 0);
    }

    @Test
    public void testCollectTimeInState_oneSource() {
        String key1 = "key1";
        mTimeInStateHelper.setUp(key1 + "@" + file1.getPath());

        String content = "10000   100\n" + "250000  100\n";

        writeToFile(file1, content);
        mTimeInStateHelper.startCollecting();

        content = "10000   200\n" + "250000  100\n" + "35000   100\n";
        writeToFile(file1, content);
        mTimeInStateHelper.stopCollecting();
        Map<String, Long> results = mTimeInStateHelper.getMetrics();

        assertEquals(results.size(), 3);
        assertEquals(results.get(constructKey(key1, "10000")).longValue(), 100L);
        assertEquals(results.get(constructKey(key1, "35000")).longValue(), 100L);
        assertEquals(results.get(constructKey(key1, AVG_FREQ_KEY_SUFFIX)).longValue(), 22500L);
    }

    @Test
    public void testCollectTimeInState_multipleSources() {
        String key1 = "key1";
        String key2 = "key2";
        mTimeInStateHelper.setUp(key1 + "@" + file1.getPath(), key2 + "@" + file2.getPath());

        String content1 = "10000   200\n";
        writeToFile(file1, content1);
        String content2 = "250000   100\n" + "750000   100\n";
        writeToFile(file2, content2);
        mTimeInStateHelper.startCollecting();

        content1 = "10000   250\n" + "150000  50";
        writeToFile(file1, content1);
        content2 = "250000   180\n" + "750000   120\n";
        writeToFile(file2, content2);
        mTimeInStateHelper.stopCollecting();
        Map<String, Long> results = mTimeInStateHelper.getMetrics();

        assertEquals(results.size(), 6);
        assertEquals(results.get(constructKey(key1, "10000")).longValue(), 50L);
        assertEquals(results.get(constructKey(key1, "150000")).longValue(), 50L);
        assertEquals(results.get(constructKey(key1, AVG_FREQ_KEY_SUFFIX)).longValue(), 80000L);
        assertEquals(results.get(constructKey(key2, "250000")).longValue(), 80L);
        assertEquals(results.get(constructKey(key2, "750000")).longValue(), 20L);
        assertEquals(results.get(constructKey(key2, AVG_FREQ_KEY_SUFFIX)).longValue(), 350000L);
    }
}
