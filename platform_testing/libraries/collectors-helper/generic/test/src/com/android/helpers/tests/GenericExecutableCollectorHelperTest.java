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

package com.android.helpers.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import androidx.test.runner.AndroidJUnit4;

import com.android.helpers.GenericExecutableCollectorHelper;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

/**
 * Android unit tests for {@link GenericExecutableCollectorHelper}.
 *
 * <p>To run: atest CollectorsHelperAospTest:GenericExecutableCollectorHelperTest
 */
@RunWith(AndroidJUnit4.class)
public class GenericExecutableCollectorHelperTest {
    private static final String TAG = GenericExecutableCollectorHelperTest.class.getSimpleName();
    private static final String VALID_EMPTY_DIR = "/data";
    private static final String INVALID_INPUT_DIR = "0";
    private static final String TEST_FILE_NAME = "test_file_";
    private static File sTestFile1;
    private static File sTestFile2;
    private static String sTestFile1NamePrefix;
    private static String sTestFile2NamePrefix;
    private @Spy GenericExecutableCollectorHelper mGenericExecutableCollectorHelper;

    @BeforeClass
    public static void setUpFiles() throws IOException {
        sTestFile1 = Files.createTempFile(TEST_FILE_NAME, "1").toFile();
        sTestFile2 = Files.createTempFile(TEST_FILE_NAME, "2").toFile();
        sTestFile1NamePrefix = sTestFile1.getName() + "_";
        sTestFile2NamePrefix = sTestFile2.getName() + "_";
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    /** Test invalid input directory and throw an IllegalArgumentException */
    @Test
    public void testBadInputDir() {
        assertThrows(
                "Executable directory was not a directory or was not specified.",
                IllegalArgumentException.class,
                () -> mGenericExecutableCollectorHelper.setUp(INVALID_INPUT_DIR));
    }

    /** Test valid input directory but empty folder and throw an IllegalArgumentException */
    @Test
    public void testEmptyDir() {
        assertThrows(
                String.format("No test file found in the directory %s", VALID_EMPTY_DIR),
                IllegalArgumentException.class,
                () -> mGenericExecutableCollectorHelper.setUp(VALID_EMPTY_DIR));
    }

    /** Test valid input directory */
    @Test
    public void testGoodDir() throws IOException {
        mGenericExecutableCollectorHelper.setUp(sTestFile1.getParent());
        assertTrue(mGenericExecutableCollectorHelper.startCollecting());
    }

    /** Test valid input directory with multiple files */
    @Test
    public void testMultipleGoodFiles() throws IOException {
        String testOutput1 =
                "name,binder_threads_in_use,binder_threads_started,client_count\n"
                        + "DockObserver,0,32,2\n"
                        + "SurfaceFlinger,0,5,8\n"
                        + "SurfaceFlingerAIDL,0,5,8\n";
        String testOutput2 =
                "name,binder_threads_in_use,binder_threads_started,client_count\n"
                        + "camera.provider/internal/0,0,3,3\n"
                        + "cas.IMediaCasService/default,1,1,2\n"
                        + "confirmationui.IConfirmationUI/default,0,1,2\n";
        doReturn(testOutput1)
                .when(mGenericExecutableCollectorHelper)
                .executeShellCommand(sTestFile1.getPath());
        doReturn(testOutput2)
                .when(mGenericExecutableCollectorHelper)
                .executeShellCommand(sTestFile2.getPath());

        mGenericExecutableCollectorHelper.setUp(sTestFile1.getParent());
        assertTrue(mGenericExecutableCollectorHelper.startCollecting());
        Map<String, String> metrics = mGenericExecutableCollectorHelper.getMetrics();

        assertFalse(metrics.isEmpty());
        assertTrue(
                metrics.containsKey(sTestFile1NamePrefix + "DockObserver_binder_threads_in_use"));
        assertTrue(
                metrics.containsKey(sTestFile1NamePrefix + "DockObserver_binder_threads_started"));
        assertTrue(metrics.containsKey(sTestFile1NamePrefix + "DockObserver_client_count"));
        assertEquals(
                metrics.get(sTestFile1NamePrefix + "SurfaceFlinger_binder_threads_in_use"), "0");
        assertEquals(
                metrics.get(sTestFile1NamePrefix + "SurfaceFlinger_binder_threads_started"), "5");
        assertEquals(metrics.get(sTestFile1NamePrefix + "SurfaceFlinger_client_count"), "8");

        assertTrue(
                metrics.containsKey(
                        sTestFile2NamePrefix
                                + "confirmationui.IConfirmationUI/default_binder_threads_in_use"));
        assertTrue(
                metrics.containsKey(
                        sTestFile2NamePrefix
                                + "confirmationui.IConfirmationUI/default_binder_threads_started"));
        assertTrue(
                metrics.containsKey(
                        sTestFile2NamePrefix
                                + "confirmationui.IConfirmationUI/default_client_count"));
        assertEquals(
                metrics.get(
                        sTestFile2NamePrefix + "camera.provider/internal/0_binder_threads_in_use"),
                "0");
        assertEquals(
                metrics.get(
                        sTestFile2NamePrefix + "camera.provider/internal/0_binder_threads_started"),
                "3");
        assertEquals(
                metrics.get(sTestFile2NamePrefix + "camera.provider/internal/0_client_count"), "3");
    }

    /**
     * Test valid input directory with multiple files. If there is a bad file, the metrics are still
     * collected from other good files.
     */
    @Test
    public void testBadExectuable_goodExecutableStillCollects() throws IOException {
        String testOutput2 =
                "name,binder_threads_in_use,binder_threads_started,client_count\n"
                        + "camera.provider/internal/0,0,3,3\n"
                        + "cas.IMediaCasService/default,1,1,2\n"
                        + "confirmationui.IConfirmationUI/default,0,1,2\n";
        doThrow(IOException.class)
                .when(mGenericExecutableCollectorHelper)
                .executeShellCommand(sTestFile1.getPath());
        doReturn(testOutput2)
                .when(mGenericExecutableCollectorHelper)
                .executeShellCommand(sTestFile2.getPath());

        mGenericExecutableCollectorHelper.setUp(sTestFile1.getParent());
        assertTrue(mGenericExecutableCollectorHelper.startCollecting());
        Map<String, String> metrics = mGenericExecutableCollectorHelper.getMetrics();

        assertFalse(metrics.isEmpty());
        assertTrue(
                metrics.containsKey(
                        sTestFile2NamePrefix
                                + "confirmationui.IConfirmationUI/default_binder_threads_in_use"));
        assertTrue(
                metrics.containsKey(
                        sTestFile2NamePrefix
                                + "confirmationui.IConfirmationUI/default_binder_threads_started"));
        assertTrue(
                metrics.containsKey(
                        sTestFile2NamePrefix
                                + "confirmationui.IConfirmationUI/default_client_count"));
        assertEquals(
                metrics.get(
                        sTestFile2NamePrefix + "camera.provider/internal/0_binder_threads_in_use"),
                "0");
        assertEquals(
                metrics.get(
                        sTestFile2NamePrefix + "camera.provider/internal/0_binder_threads_started"),
                "3");
        assertEquals(
                metrics.get(sTestFile2NamePrefix + "camera.provider/internal/0_client_count"), "3");
    }
}
