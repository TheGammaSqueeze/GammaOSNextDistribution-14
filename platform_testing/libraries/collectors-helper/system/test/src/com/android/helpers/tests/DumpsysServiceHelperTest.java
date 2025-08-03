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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;

import com.android.helpers.DumpsysServiceHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Android unit test for {@link DumpsysServiceHelper}
 *
 * <p>To run: atest CollectorsHelperTest:com.android.helpers.tests.DumpsysServiceHelperTest
 */
@RunWith(AndroidJUnit4.class)
public class DumpsysServiceHelperTest {

    private static final String DUMPSYS_OUTPUT_DIRECTORY = "/sdcard/test_results";

    private static final String DUMPSYS_OUTPUT_FILENAME = "dumpsys_service_%s.txt";

    private static final String DUMPSYS_SERVICE_KEY = "dumpsys_service_%s";

    // Service name used for testing
    private static final String TEST_SERVICE_NAME = "media.camera";

    private static final String TEST_SERVICE_NAME_2 = "meminfo";

    private DumpsysServiceHelper mDumpsysServiceHelper;

    @Before
    public void setUp() {
        mDumpsysServiceHelper = new DumpsysServiceHelper();
    }

    @After
    public void tearDown() {
        new File(DUMPSYS_OUTPUT_DIRECTORY).delete();
    }

    @Test
    public void testCollectDumpsys_noService() {
        mDumpsysServiceHelper.startCollecting();
        mDumpsysServiceHelper.stopCollecting();
        Map<String, String> results = mDumpsysServiceHelper.getMetrics();

        assertTrue(results.isEmpty());
    }

    @Test
    public void testCollectDumpsys_oneService() {
        mDumpsysServiceHelper.setUp(TEST_SERVICE_NAME);

        mDumpsysServiceHelper.startCollecting();
        mDumpsysServiceHelper.stopCollecting();
        Map<String, String> results = mDumpsysServiceHelper.getMetrics();

        assertFalse(results.isEmpty());
        String key = String.format(DUMPSYS_SERVICE_KEY, TEST_SERVICE_NAME);
        String path =
                Paths.get(
                                DUMPSYS_OUTPUT_DIRECTORY,
                                String.format(DUMPSYS_OUTPUT_FILENAME, TEST_SERVICE_NAME))
                        .toString();
        assertEquals(results.get(key), path);
    }

    @Test
    public void testCollectDumpsys_multipleServices() {
        mDumpsysServiceHelper.setUp(TEST_SERVICE_NAME, TEST_SERVICE_NAME_2);

        mDumpsysServiceHelper.startCollecting();
        mDumpsysServiceHelper.stopCollecting();
        Map<String, String> results = mDumpsysServiceHelper.getMetrics();

        String key1 = String.format(DUMPSYS_SERVICE_KEY, TEST_SERVICE_NAME);
        String path1 =
                Paths.get(
                                DUMPSYS_OUTPUT_DIRECTORY,
                                String.format(DUMPSYS_OUTPUT_FILENAME, TEST_SERVICE_NAME))
                        .toString();
        assertEquals(results.get(key1), path1);
        String key2 = String.format(DUMPSYS_SERVICE_KEY, TEST_SERVICE_NAME_2);
        String path2 =
                Paths.get(
                                DUMPSYS_OUTPUT_DIRECTORY,
                                String.format(DUMPSYS_OUTPUT_FILENAME, TEST_SERVICE_NAME_2))
                        .toString();
        assertEquals(results.get(key2), path2);
    }
}
