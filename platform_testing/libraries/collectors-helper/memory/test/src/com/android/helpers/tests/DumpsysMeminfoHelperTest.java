/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.helpers.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;

import com.android.helpers.DumpsysMeminfoHelper;
import com.android.helpers.MetricUtility;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Android unit test for {@link DumpsysMeminfoHelper}
 *
 * <p>To run: atest CollectorsHelperTest:com.android.helpers.tests.DumpsysMeminfoHelperTest
 */
@RunWith(AndroidJUnit4.class)
public class DumpsysMeminfoHelperTest {

    // Process name used for testing
    private static final String TEST_PROCESS_NAME = "com.android.systemui";
    // Second process name used for testing
    private static final String TEST_PROCESS_NAME_2 = "init";
    // Third process name used for testing
    private static final String TEST_PROCESS_NAME_3 = "com.google.android.apps.nexuslauncher";
    // A process that does not exist
    private static final String PROCESS_NOT_FOUND = "process_not_found";
    // A object category that does not exist
    private static final String OBJECT_NOT_FOUND = "object_not_found";
    // First valid test object category that does exist
    private static final String OBJECT_VIEW = "View";
    // Second valid test object category that does exist
    private static final String OBJECT_VIEW_ROOT_IMPL = "ViewRootImpl";
    // com.android.systemui view object key.
    private static final String SYSTEMUI_VIEW_KEY = "com.android.systemui_View";
    // com.android.systemui ViewRootImpl object key.
    private static final String SYSTEMUI_VIEW_ROOT_KEY = "com.android.systemui_ViewRootImpl";
    // com.google.android.apps.nexuslauncher view object key.
    private static final String LAUNCHER_VIEW_KEY = "com.google.android.apps.nexuslauncher_View";
    // com.google.android.apps.nexuslauncher ViewRootImpl object key.
    private static final String LAUNCHER_VIEW_ROOT_KEY =
            "com.google.android.apps.nexuslauncher_ViewRootImpl";
    // Invalid object key for the valid process name.
    private static final String INVALID_PROCESS_OBJECT_KEY =
            "com.android.systemui_object_not_found";

    private static final String[] CATEGORIES = {"native", "dalvik"};

    private static final String[] METRICS = {
        "pss_total", "shared_dirty", "private_dirty", "heap_size", "heap_alloc"
    };

    private static final String UNIT = "kb";

    private static final String METRIC_SOURCE = "dumpsys";

    private DumpsysMeminfoHelper mDumpsysMeminfoHelper;

    @Before
    public void setUp() {
        mDumpsysMeminfoHelper = new DumpsysMeminfoHelper();
    }

    @Test
    public void testCollectMeminfo_noProcess() {
        mDumpsysMeminfoHelper.startCollecting();
        Map<String, Long> results = mDumpsysMeminfoHelper.getMetrics();
        mDumpsysMeminfoHelper.stopCollecting();
        assertTrue(results.isEmpty());
    }

    @Test
    public void testCollectMeminfo_nullProcess() {
        mDumpsysMeminfoHelper.setProcessNames(null);
        mDumpsysMeminfoHelper.startCollecting();
        Map<String, Long> results = mDumpsysMeminfoHelper.getMetrics();
        assertTrue(results.isEmpty());
    }

    @Test
    public void testCollectMeminfo_nullProcessObjectMap() {
        mDumpsysMeminfoHelper.setProcessObjectNamesMap(null);
        mDumpsysMeminfoHelper.startCollecting();
        Map<String, Long> results = mDumpsysMeminfoHelper.getMetrics();
        assertTrue(results.isEmpty());
    }

    @Test
    public void testCollectMeminfo_wrongProcesses() {
        mDumpsysMeminfoHelper.setProcessNames(PROCESS_NOT_FOUND, null, "");
        mDumpsysMeminfoHelper.startCollecting();
        Map<String, Long> results = mDumpsysMeminfoHelper.getMetrics();
        assertTrue(results.isEmpty());
    }

    @Test
    public void testCollectMeminfo_wrongProcessesNameInProcessObjectMap() {
        Map<String, List<String>> processObjectMap = new HashMap<>();
        List<String> objectList = new ArrayList<>();
        objectList.add("View");
        processObjectMap.put(PROCESS_NOT_FOUND, objectList);
        mDumpsysMeminfoHelper.setProcessObjectNamesMap(processObjectMap);
        mDumpsysMeminfoHelper.startCollecting();
        Map<String, Long> results = mDumpsysMeminfoHelper.getMetrics();
        assertTrue(results.isEmpty());
    }

    @Test
    public void testCollectMeminfo_wrongObjectNameInProcessObjectMap() {
        Map<String, List<String>> processObjectMap = new HashMap<>();
        List<String> objectList = new ArrayList<>();
        objectList.add(OBJECT_NOT_FOUND);
        processObjectMap.put(TEST_PROCESS_NAME, objectList);
        mDumpsysMeminfoHelper.setProcessObjectNamesMap(processObjectMap);
        mDumpsysMeminfoHelper.startCollecting();
        Map<String, Long> results = mDumpsysMeminfoHelper.getMetrics();
        // Reporting -1 for the invalid object for alerting purpose.
        assertTrue(results.get(INVALID_PROCESS_OBJECT_KEY) == -1L);
    }

    @Test
    public void testCollectMeminfo_oneProcess() {
        mDumpsysMeminfoHelper.setProcessNames(TEST_PROCESS_NAME);
        mDumpsysMeminfoHelper.startCollecting();
        Map<String, Long> results = mDumpsysMeminfoHelper.getMetrics();
        mDumpsysMeminfoHelper.stopCollecting();
        assertFalse(results.isEmpty());
        verifyKeysForProcess(results, TEST_PROCESS_NAME);
    }

    @Test
    public void testCollectMeminfo_multipleProcesses() {
        mDumpsysMeminfoHelper.setProcessNames(TEST_PROCESS_NAME, TEST_PROCESS_NAME_2);
        mDumpsysMeminfoHelper.startCollecting();
        Map<String, Long> results = mDumpsysMeminfoHelper.getMetrics();
        mDumpsysMeminfoHelper.stopCollecting();
        assertFalse(results.isEmpty());
        verifyKeysForProcess(results, TEST_PROCESS_NAME);
        verifyKeysForProcess(results, TEST_PROCESS_NAME_2);
    }

    @Test
    public void testCollectMeminfo_oneProcessObjectMap() {
        Map<String, List<String>> processObjectMap = new HashMap<>();
        List<String> objectList = new ArrayList<>();
        objectList.add(OBJECT_VIEW);
        objectList.add(OBJECT_VIEW_ROOT_IMPL);
        processObjectMap.put(TEST_PROCESS_NAME, objectList);
        mDumpsysMeminfoHelper.setProcessObjectNamesMap(processObjectMap);
        mDumpsysMeminfoHelper.startCollecting();
        Map<String, Long> results = mDumpsysMeminfoHelper.getMetrics();
        assertTrue(results.keySet().contains(SYSTEMUI_VIEW_KEY));
        assertTrue(results.keySet().contains(SYSTEMUI_VIEW_ROOT_KEY));
    }

    @Test
    public void testCollectMeminfo_twoProcessObjectMap() {
        Map<String, List<String>> processObjectMap = new HashMap<>();
        List<String> objectList = new ArrayList<>();
        objectList.add(OBJECT_VIEW);
        objectList.add(OBJECT_VIEW_ROOT_IMPL);
        processObjectMap.put(TEST_PROCESS_NAME, objectList);
        processObjectMap.put(TEST_PROCESS_NAME_3, objectList);
        mDumpsysMeminfoHelper.setProcessObjectNamesMap(processObjectMap);
        mDumpsysMeminfoHelper.startCollecting();
        Map<String, Long> results = mDumpsysMeminfoHelper.getMetrics();
        assertTrue(results.keySet().contains(SYSTEMUI_VIEW_KEY));
        assertTrue(results.keySet().contains(SYSTEMUI_VIEW_ROOT_KEY));
        assertTrue(results.keySet().contains(LAUNCHER_VIEW_KEY));
        assertTrue(results.keySet().contains(LAUNCHER_VIEW_ROOT_KEY));
    }

    @Test
    public void testCollectMeminfo_oneProcessAndProcessObjectMap() {
        Map<String, List<String>> processObjectMap = new HashMap<>();
        List<String> objectList = new ArrayList<>();
        objectList.add(OBJECT_VIEW);
        objectList.add(OBJECT_VIEW_ROOT_IMPL);
        processObjectMap.put(TEST_PROCESS_NAME, objectList);
        mDumpsysMeminfoHelper.setProcessNames(TEST_PROCESS_NAME);
        mDumpsysMeminfoHelper.setProcessObjectNamesMap(processObjectMap);
        mDumpsysMeminfoHelper.startCollecting();
        Map<String, Long> results = mDumpsysMeminfoHelper.getMetrics();
        verifyKeysForProcess(results, TEST_PROCESS_NAME);
        assertTrue(results.keySet().contains(SYSTEMUI_VIEW_KEY));
        assertTrue(results.keySet().contains(SYSTEMUI_VIEW_ROOT_KEY));
    }

    private void verifyKeysForProcess(Map<String, Long> results, String processName) {
        for (String category : CATEGORIES) {
            for (String metric : METRICS) {
                assertTrue(
                        results.containsKey(
                                MetricUtility.constructKey(
                                        METRIC_SOURCE, category, metric, UNIT, processName)));
            }
        }
        assertTrue(
                results.containsKey(
                        MetricUtility.constructKey(
                                METRIC_SOURCE, "total", "pss_total", UNIT, processName)));
    }
}
