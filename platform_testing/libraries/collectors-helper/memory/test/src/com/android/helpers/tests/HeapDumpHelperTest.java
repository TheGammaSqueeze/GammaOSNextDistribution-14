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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.helpers.HeapDumpHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Map;

/**
 * Android Unit tests for {@link HeapDumpHelper}.
 *
 * To run:
 * atest CollectorsHelperTest:com.android.helpers.tests.HeapDumpHelperTest
 */
@RunWith(AndroidJUnit4.class)
public class HeapDumpHelperTest {
    private static final String TAG = HeapDumpHelperTest.class.getSimpleName();
    private HeapDumpHelper mHeapDumpHelper;

    @Before
    public void setUp() {
        mHeapDumpHelper = new HeapDumpHelper();
    }

    @After
    public void tearDown() throws IOException {
        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        for (Map.Entry<String, String> entry : mHeapDumpHelper.getMetrics().entrySet()) {
            uiDevice.executeShellCommand(String.format("rm %s", entry.getValue()));
        }
    }

    @Test
    public void testHeapDumpCollectionNoProcess() {
        mHeapDumpHelper.setUp("/data/local/tmp/", "");
        assertTrue(mHeapDumpHelper.startCollecting("sample-heapdump-1"));
        Map<String, String> metrics = mHeapDumpHelper.getMetrics();
        assertTrue(metrics.size() == 2);
        assertTrue(metrics.get("managed_heapdump_empty_files_count").equalsIgnoreCase("0"));
        assertTrue(metrics.get("native_heapdump_empty_files_count").equalsIgnoreCase("0"));
    }

    @Test
    public void testSuccessfulHeapDumpCollection() {
        mHeapDumpHelper.setUp("/data/local/tmp/", "com.android.systemui");
        assertTrue(mHeapDumpHelper.startCollecting("sample-heapdump-1"));
        Map<String, String> metrics = mHeapDumpHelper.getMetrics();
        assertTrue(metrics.size() == 3);
        assertTrue(metrics.get("managed_heapdump_empty_files_count").equalsIgnoreCase("0"));
        assertTrue(metrics.get("native_heapdump_empty_files_count").equalsIgnoreCase("0"));
        assertTrue(
                metrics.get("managed_heapdump_file_1")
                        .equalsIgnoreCase(
                                "/data/local/tmp/"
                                        + "managed_heapdump_file_"
                                        + "com.android.systemui_sample-heapdump-1.hprof"));
    }

    @Test
    public void testHeapCollectionProcessWithSpecialChars() {
        mHeapDumpHelper.setUp("/data/local/tmp/", "/system/bin/surfaceflinger");
        assertTrue(mHeapDumpHelper.startCollecting("sample-heapdump-1"));
        Map<String, String> metrics = mHeapDumpHelper.getMetrics();
        assertTrue(metrics.size() == 3);
        assertTrue(metrics.get("managed_heapdump_empty_files_count").equalsIgnoreCase("1"));
        assertTrue(metrics.get("native_heapdump_empty_files_count").equalsIgnoreCase("0"));
        assertTrue(
                metrics.get("managed_heapdump_file_1")
                        .equalsIgnoreCase(
                                "/data/local/tmp/"
                                        + "EMPTY-managed_heapdump_file_"
                                        + "#system#bin#surfaceflinger_sample-heapdump-1.hprof"));
    }

    @Test
    public void testSuccessfulHeapDumpCollectionForTwoProcesses() {
        String[] processNames = new String[] { "com.android.systemui", "system_server" };
        mHeapDumpHelper.setUp("/data/local/tmp/", processNames);
        assertTrue(mHeapDumpHelper.startCollecting("sample-heapdump-2"));
        Map<String, String> metrics = mHeapDumpHelper.getMetrics();
        assertTrue(metrics.size() == 4);
        assertTrue(metrics.get("managed_heapdump_empty_files_count").equalsIgnoreCase("0"));
        assertTrue(metrics.get("native_heapdump_empty_files_count").equalsIgnoreCase("0"));
        assertTrue(
                metrics.get("managed_heapdump_file_1")
                        .equalsIgnoreCase(
                                "/data/local/tmp/"
                                        + "managed_heapdump_file_"
                                        + "com.android.systemui_sample-heapdump-2.hprof"));
        assertTrue(
                metrics.get("managed_heapdump_file_2")
                        .equalsIgnoreCase(
                                "/data/local/tmp/"
                                        + "managed_heapdump_file_"
                                        + "system_server_sample-heapdump-2.hprof"));
    }

    @Test
    public void testSuccessfulNativeHeapDumpCollectionForTwoProcesses() {
        String[] processNames = new String[] {"com.android.systemui", "system_server"};
        mHeapDumpHelper.setUp("/data/local/tmp/", processNames);
        mHeapDumpHelper.enableNativeHeapDump();
        assertTrue(mHeapDumpHelper.startCollecting("sample-heapdump-2"));
        Map<String, String> metrics = mHeapDumpHelper.getMetrics();
        assertTrue(metrics.size() == 6);
        assertTrue(metrics.get("managed_heapdump_empty_files_count").equalsIgnoreCase("0"));
        assertTrue(metrics.get("native_heapdump_empty_files_count").equalsIgnoreCase("0"));
        assertTrue(
                metrics.get("managed_heapdump_file_1")
                        .equalsIgnoreCase(
                                "/data/local/tmp/"
                                        + "managed_heapdump_file_"
                                        + "com.android.systemui_sample-heapdump-2.hprof"));
        assertTrue(
                metrics.get("managed_heapdump_file_2")
                        .equalsIgnoreCase(
                                "/data/local/tmp/"
                                        + "managed_heapdump_file_"
                                        + "system_server_sample-heapdump-2.hprof"));
        assertTrue(
                metrics.get("native_heapdump_file_1")
                        .equalsIgnoreCase(
                                "/data/local/tmp/"
                                        + "native_heapdump_file_"
                                        + "com.android.systemui_sample-heapdump-2.txt"));
        assertTrue(
                metrics.get("native_heapdump_file_2")
                        .equalsIgnoreCase(
                                "/data/local/tmp/"
                                    + "native_heapdump_file_system_server_sample-heapdump-2.txt"));
    }

    @Test
    public void testHeapDumpNotCollectedWithEmptyId() {
        mHeapDumpHelper.setUp("/data/local/tmp/", "com.android.systemui");
        assertFalse(mHeapDumpHelper.startCollecting(""));
        Map<String, String> metrics = mHeapDumpHelper.getMetrics();
        assertTrue(metrics.size() == 2);
        assertTrue(metrics.get("managed_heapdump_empty_files_count").equalsIgnoreCase("0"));
        assertTrue(metrics.get("native_heapdump_empty_files_count").equalsIgnoreCase("0"));
    }

    @Test
    public void testHeapDumpCollectionForInvalidProcesses() {
        String[] processNames = new String[] {"systemui", "twoshay"};
        mHeapDumpHelper.setUp("/data/local/tmp/", processNames);
        assertTrue(mHeapDumpHelper.startCollecting("sample-heapdump-2"));
        Map<String, String> metrics = mHeapDumpHelper.getMetrics();
        assertTrue(metrics.size() == 3);
        assertTrue(metrics.get("managed_heapdump_empty_files_count").equalsIgnoreCase("1"));
        assertTrue(metrics.get("native_heapdump_empty_files_count").equalsIgnoreCase("0"));
        assertTrue(
                metrics.get("managed_heapdump_file_1")
                        .equalsIgnoreCase(
                                "/data/local/tmp/"
                                        + "EMPTY-managed_heapdump_file_"
                                        + "twoshay_sample-heapdump-2.hprof"));
    }

    @Test
    public void testNativeHeapDumpCollectionForInvalidProcesses() {
        String[] processNames = new String[] {"systemui", "twoshay"};
        mHeapDumpHelper.setUp("/data/local/tmp/", processNames);
        mHeapDumpHelper.enableNativeHeapDump();
        assertTrue(mHeapDumpHelper.startCollecting("sample-heapdump-2"));
        Map<String, String> metrics = mHeapDumpHelper.getMetrics();
        assertTrue(metrics.size() == 4);
        assertTrue(metrics.get("managed_heapdump_empty_files_count").equalsIgnoreCase("1"));
        assertTrue(metrics.get("native_heapdump_empty_files_count").equalsIgnoreCase("1"));
        assertTrue(
                metrics.get("managed_heapdump_file_1")
                        .equalsIgnoreCase(
                                "/data/local/tmp/"
                                        + "EMPTY-managed_heapdump_file_"
                                        + "twoshay_sample-heapdump-2.hprof"));
        assertTrue(
                metrics.get("native_heapdump_file_1")
                        .equalsIgnoreCase(
                                "/data/local/tmp/"
                                    + "EMPTY-native_heapdump_file_twoshay_sample-heapdump-2.txt"));
    }
}
