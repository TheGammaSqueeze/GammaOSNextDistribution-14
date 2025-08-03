/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tradefed.testtype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.MockitoFileUtil;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.StringEscapeUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.AdditionalMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link GoogleBenchmarkTest}. */
@RunWith(JUnit4.class)
public class GoogleBenchmarkTestTest {

    @Mock ITestInvocationListener mMockInvocationListener;
    @Mock ITestDevice mMockITestDevice;
    private GoogleBenchmarkTest mGoogleBenchmarkTest;
    private TestInformation mTestInfo;
    private TestDescription mDummyTest;
    private OptionSetter mSetter;

    /** Helper to initialize the various EasyMocks we'll need. */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mDummyTest = new TestDescription("Class", "method");
        when(mMockITestDevice.getSerialNumber()).thenReturn("serial");
        mGoogleBenchmarkTest =
                new GoogleBenchmarkTest() {

                    @Override
                    GoogleBenchmarkResultParser createResultParser(
                            String runName, ITestInvocationListener listener) {
                        return new GoogleBenchmarkResultParser(runName, listener) {
                            @Override
                            public Map<String, String> parse(CommandResult cmd_result) {
                                listener.testStarted(mDummyTest);
                                listener.testEnded(mDummyTest, Collections.emptyMap());
                                return Collections.emptyMap();
                            }
                        };
                    }
                };
        mGoogleBenchmarkTest.setDevice(mMockITestDevice);
        mTestInfo = TestInformation.newBuilder().build();
    }

    /** Test the run method for a couple tests */
    @Test
    public void testRun() throws DeviceNotAvailableException {
        final String nativeTestPath = GoogleBenchmarkTest.DEFAULT_TEST_PATH;
        final String test1 = "test1";
        final String test2 = "test2";
        MockitoFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, test1, test2);
        when(mMockITestDevice.doesFileExist(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(nativeTestPath + "/test1")).thenReturn(false);
        when(mMockITestDevice.isDirectory(nativeTestPath + "/test2")).thenReturn(false);
        String[] files = new String[] {"test1", "test2"};
        when(mMockITestDevice.getChildren(nativeTestPath)).thenReturn(files);
        when(mMockITestDevice.executeShellCommand(Mockito.contains("chmod"))).thenReturn("");

        when(mMockITestDevice.executeShellV2Command(
                        eq(String.format("%s/test1 --benchmark_list_tests=true", nativeTestPath)),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt()))
                .thenReturn(getCommandResult("method1\nmethod2\nmethod3"));

        when(mMockITestDevice.executeShellV2Command(
                        eq(String.format("%s/test2 --benchmark_list_tests=true", nativeTestPath)),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt()))
                .thenReturn(getCommandResult("method1\nmethod2\n"));

        mGoogleBenchmarkTest.run(mTestInfo, mMockInvocationListener);

        verify(mMockITestDevice, times(2))
                .executeShellV2Command(
                        Mockito.contains(test1),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
        verify(mMockITestDevice, times(2))
                .executeShellV2Command(
                        Mockito.contains(test2),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
        verify(mMockInvocationListener).testRunStarted(test1, 3);
        verify(mMockInvocationListener, times(2)).testStarted(mDummyTest);
        verify(mMockInvocationListener, times(2))
                .testEnded(eq(mDummyTest), Mockito.<HashMap<String, String>>any());
        verify(mMockInvocationListener).testRunStarted(test2, 2);
        verify(mMockITestDevice, times(2)).executeShellCommand(Mockito.contains("chmod"));
        verify(mMockInvocationListener, times(2))
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Test the run method when no device is set. */
    @Test
    public void testRun_noDevice() throws DeviceNotAvailableException {
        mGoogleBenchmarkTest.setDevice(null);
        try {
            mGoogleBenchmarkTest.run(mTestInfo, mMockInvocationListener);
        } catch (IllegalArgumentException e) {
            assertEquals("Device has not been set", e.getMessage());
            return;
        }
        fail();
    }

    /** Test the run method for a couple tests */
    @Test
    public void testRun_noBenchmarkDir() throws DeviceNotAvailableException {
        when(mMockITestDevice.doesFileExist(GoogleBenchmarkTest.DEFAULT_TEST_PATH))
                .thenReturn(false);

        try {
            mGoogleBenchmarkTest.run(mTestInfo, mMockInvocationListener);
            fail("Should have thrown an exception.");
        } catch (RuntimeException e) {
            // expected
        }
    }

    /** Test the run method for a couple tests with a module name */
    @Test
    public void testRun_withSingleModuleName() throws DeviceNotAvailableException {
        final String moduleName = "module";
        final String nativeTestPath =
                String.format("%s/%s", GoogleBenchmarkTest.DEFAULT_TEST_PATH, moduleName);
        mGoogleBenchmarkTest.addModuleName(moduleName);
        final String test1 = "test1";
        final String test2 = "test2";
        MockitoFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, test1, test2);
        when(mMockITestDevice.doesFileExist(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(nativeTestPath + "/test1")).thenReturn(false);
        when(mMockITestDevice.isDirectory(nativeTestPath + "/test2")).thenReturn(false);
        String[] files = new String[] {"test1", "test2"};
        when(mMockITestDevice.getChildren(nativeTestPath)).thenReturn(files);
        when(mMockITestDevice.executeShellCommand(Mockito.contains("chmod"))).thenReturn("");
        when(mMockITestDevice.executeShellV2Command(
                        eq(String.format("%s/test1 --benchmark_list_tests=true", nativeTestPath)),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt()))
                .thenReturn(getCommandResult("\nmethod1\nmethod2\nmethod3\n\n"));
        when(mMockITestDevice.executeShellV2Command(
                        eq(String.format("%s/test2 --benchmark_list_tests=true", nativeTestPath)),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt()))
                .thenReturn(getCommandResult("method1\nmethod2\n"));

        mGoogleBenchmarkTest.run(mTestInfo, mMockInvocationListener);

        verify(mMockInvocationListener).testRunStarted(test1, 3);
        verify(mMockInvocationListener, times(2)).testStarted(mDummyTest);
        verify(mMockInvocationListener, times(2))
                .testEnded(eq(mDummyTest), Mockito.<HashMap<String, String>>any());
        verify(mMockInvocationListener).testRunStarted(test2, 2);
        verify(mMockITestDevice, times(2))
                .executeShellV2Command(
                        Mockito.contains(test1),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
        verify(mMockITestDevice, times(2))
                .executeShellV2Command(
                        Mockito.contains(test2),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
        verify(mMockITestDevice, times(2)).executeShellCommand(Mockito.contains("chmod"));
        verify(mMockInvocationListener, times(2))
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Test the run method for a couple tests with multiple module names */
    @Test
    public void testRun_withMultipleModuleNames() throws DeviceNotAvailableException {
        final List<String> moduleNames =
                new ArrayList<>(Arrays.asList("module1", "module2", "module3"));
        List<String> nativeTestPaths = new ArrayList<>();
        for (String moduleName : moduleNames) {
            nativeTestPaths.add(
                    String.format("%s/%s", GoogleBenchmarkTest.DEFAULT_TEST_PATH, moduleName));
            mGoogleBenchmarkTest.addModuleName(moduleName);
        }
        final String test1 = "test1";
        final String test2 = "test2";

        for (String nativeTestPath : nativeTestPaths) {
            MockitoFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, test1, test2);
            when(mMockITestDevice.doesFileExist(nativeTestPath)).thenReturn(true);
            when(mMockITestDevice.isDirectory(nativeTestPath)).thenReturn(true);
            when(mMockITestDevice.isDirectory(nativeTestPath + "/test1")).thenReturn(false);
            when(mMockITestDevice.isDirectory(nativeTestPath + "/test2")).thenReturn(false);
            String[] files = new String[] {"test1", "test2"};
            when(mMockITestDevice.getChildren(nativeTestPath)).thenReturn(files);
            when(mMockITestDevice.executeShellCommand(Mockito.contains("chmod"))).thenReturn("");
            when(mMockITestDevice.executeShellV2Command(
                            eq(
                                    String.format(
                                            "%s/test1 --benchmark_list_tests=true",
                                            nativeTestPath)),
                            Mockito.anyLong(),
                            (TimeUnit) Mockito.any(),
                            Mockito.anyInt()))
                    .thenReturn(getCommandResult("\nmethod1\nmethod2\nmethod3\n\n"));
            when(mMockITestDevice.executeShellV2Command(
                            eq(
                                    String.format(
                                            "%s/test2 --benchmark_list_tests=true",
                                            nativeTestPath)),
                            Mockito.anyLong(),
                            (TimeUnit) Mockito.any(),
                            Mockito.anyInt()))
                    .thenReturn(getCommandResult("method1\nmethod2\n"));
        }

        mGoogleBenchmarkTest.run(mTestInfo, mMockInvocationListener);

        InOrder inOrderVerifier = inOrder(mMockITestDevice);

        for (String moduleName : moduleNames) {
            String patten =
                    ".*" + moduleName + ".*" + mGoogleBenchmarkTest.GBENCHMARK_JSON_OUTPUT_FORMAT;
            inOrderVerifier
                    .verify(mMockITestDevice)
                    .executeShellV2Command(
                            Mockito.matches(patten),
                            Mockito.anyLong(),
                            (TimeUnit) Mockito.any(),
                            Mockito.anyInt());
        }
    }

    /** Test the run method for a couple tests with a module name */
    @Test
    public void testRun_withRunReportName() throws DeviceNotAvailableException {
        final String nativeTestPath = GoogleBenchmarkTest.DEFAULT_TEST_PATH;
        final String test1 = "test1";
        final String reportName = "reportName";
        mGoogleBenchmarkTest.setReportRunName(reportName);
        MockitoFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, test1);
        when(mMockITestDevice.doesFileExist(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(nativeTestPath + "/test1")).thenReturn(false);
        String[] files = new String[] {"test1"};
        when(mMockITestDevice.getChildren(nativeTestPath)).thenReturn(files);
        when(mMockITestDevice.executeShellCommand(Mockito.contains("chmod"))).thenReturn("");
        when(mMockITestDevice.executeShellV2Command(
                        eq(String.format("%s/test1 --benchmark_list_tests=true", nativeTestPath)),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt()))
                .thenReturn(getCommandResult("method1\nmethod2\nmethod3"));

        mGoogleBenchmarkTest.run(mTestInfo, mMockInvocationListener);

        verify(mMockITestDevice, times(2))
                .executeShellV2Command(
                        Mockito.contains(test1),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());

        // Expect reportName instead of test name
        verify(mMockInvocationListener).testRunStarted(reportName, 3);
        verify(mMockInvocationListener).testStarted(mDummyTest);
        verify(mMockInvocationListener)
                .testEnded(eq(mDummyTest), Mockito.<HashMap<String, String>>any());
        verify(mMockInvocationListener, times(1))
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Test the run method when exec shell throw exeception. */
    @Test
    public void testRun_exceptionDuringExecShell() throws DeviceNotAvailableException {
        final String nativeTestPath = GoogleBenchmarkTest.DEFAULT_TEST_PATH;
        final String test1 = "test1";
        MockitoFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, test1);
        when(mMockITestDevice.doesFileExist(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(nativeTestPath + "/test1")).thenReturn(false);
        String[] files = new String[] {"test1"};
        when(mMockITestDevice.getChildren(nativeTestPath)).thenReturn(files);
        when(mMockITestDevice.executeShellCommand(Mockito.contains("chmod"))).thenReturn("");
        doThrow(new DeviceNotAvailableException("dnae", "serial"))
                .when(mMockITestDevice)
                .executeShellV2Command(
                        Mockito.contains(test1),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
        when(mMockITestDevice.executeShellV2Command(
                        eq(String.format("%s/test1 --benchmark_list_tests=true", nativeTestPath)),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt()))
                .thenReturn(getCommandResult("method1\nmethod2\nmethod3"));

        try {
            mGoogleBenchmarkTest.run(mTestInfo, mMockInvocationListener);
            fail();
        } catch (DeviceNotAvailableException e) {
            // expected
            verify(mMockInvocationListener).testRunStarted(test1, 3);
            verify(mMockInvocationListener).testRunFailed((String) Mockito.any());
            // Even with exception testrunEnded is expected.
            verify(mMockInvocationListener, times(1))
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        }
    }

    /** File exclusion regex filter should skip matched filepaths. */
    @Test
    public void testFileExclusionRegexFilter_skipMatched() {
        // Skip files ending in .txt
        mGoogleBenchmarkTest.addFileExclusionFilterRegex(".*\\.txt");
        assertFalse(mGoogleBenchmarkTest.shouldSkipFile("/some/path/file/binary"));
        assertFalse(mGoogleBenchmarkTest.shouldSkipFile("/some/path/file/random.dat"));
        assertTrue(mGoogleBenchmarkTest.shouldSkipFile("/some/path/file/test.txt"));
        // Always skip files ending in .config
        assertTrue(mGoogleBenchmarkTest.shouldSkipFile("/some/path/file/random.config"));
    }

    /** File exclusion regex filter for multi filters. */
    @Test
    public void testFileExclusionRegexFilter_skipMultiMatched() {
        // Skip files ending in .txt
        mGoogleBenchmarkTest.addFileExclusionFilterRegex(".*\\.txt");
        // Also skip files ending in .dat
        mGoogleBenchmarkTest.addFileExclusionFilterRegex(".*\\.dat");
        assertFalse(mGoogleBenchmarkTest.shouldSkipFile("/some/path/file/binary"));
        assertTrue(mGoogleBenchmarkTest.shouldSkipFile("/some/path/file/random.dat"));
        assertTrue(mGoogleBenchmarkTest.shouldSkipFile("/some/path/file/test.txt"));
        // Always skip files ending in .config
        assertTrue(mGoogleBenchmarkTest.shouldSkipFile("/some/path/file/random.config"));
    }

    /** File exclusion regex filter should always skip .config file. */
    @Test
    public void testFileExclusionRegexFilter_skipDefaultMatched() {
        // Always skip files ending in .config
        assertTrue(mGoogleBenchmarkTest.shouldSkipFile("/some/path/file/random.config"));
        // Other file should not be skipped
        assertFalse(mGoogleBenchmarkTest.shouldSkipFile("/some/path/file/random.configs"));
        assertFalse(mGoogleBenchmarkTest.shouldSkipFile("/some/path/file/binary"));
        assertFalse(mGoogleBenchmarkTest.shouldSkipFile("/some/path/file/random.dat"));
        assertFalse(mGoogleBenchmarkTest.shouldSkipFile("/some/path/file/test.txt"));
    }

    /** Test getFilterFlagForFilters. */
    @Test
    public void testGetFilterFlagForFilters() {
        Set<String> filters = new LinkedHashSet<>(Arrays.asList("filter1", "filter2"));
        String filterFlag = mGoogleBenchmarkTest.getFilterFlagForFilters(filters);
        assertEquals(
                String.format(
                        " %s=%s", GoogleBenchmarkTest.GBENCHMARK_FILTER_OPTION, "filter1|filter2"),
                filterFlag);
    }

    /** Test getFilterFlagForFilters - no filters. */
    @Test
    public void testGetFilterFlagForFilters_noFilters() {
        Set<String> filters = new LinkedHashSet<>();
        String filterFlag = mGoogleBenchmarkTest.getFilterFlagForFilters(filters);
        assertEquals("", filterFlag);
    }

    /** Test getFilterFlagForTests. */
    @Test
    public void testGetFilterFlagForTests() {
        Set<String> tests = new LinkedHashSet<>(Arrays.asList("test1", "test2"));
        String filterFlag = mGoogleBenchmarkTest.getFilterFlagForTests(tests);
        assertEquals(
                String.format(
                        " %s=%s", GoogleBenchmarkTest.GBENCHMARK_FILTER_OPTION, "^test1$|^test2$"),
                filterFlag);
    }

    /** Test getFilterFlagForTests - no tests. */
    @Test
    public void testGetFilterFlagForTests_noFilters() {
        Set<String> tests = new LinkedHashSet<>();
        String filterFlag = mGoogleBenchmarkTest.getFilterFlagForTests(tests);
        assertEquals("", filterFlag);
    }

    /**
     * Helper function to do the actual filtering test.
     *
     * @param incTests tests to include
     * @param excTests tests to exclude
     * @param filteredTests filtered tests
     * @throws DeviceNotAvailableException
     */
    private void doTestFilter(String incTests, String excTests, Set<String> filteredTests)
            throws DeviceNotAvailableException {
        String nativeTestPath = GoogleBenchmarkTest.DEFAULT_TEST_PATH;
        String testPath = nativeTestPath + "/test1";
        // configure the mock file system to have a single test
        MockitoFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, "test1");
        when(mMockITestDevice.doesFileExist(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(testPath)).thenReturn(false);
        when(mMockITestDevice.executeShellCommand(Mockito.contains("chmod"))).thenReturn("");
        String[] files = new String[] {"test1"};
        when(mMockITestDevice.getChildren(nativeTestPath)).thenReturn(files);

        // List tests to include
        if (mGoogleBenchmarkTest.getIncludeFilters().size() > 0) {
            String incFilterFlag =
                    mGoogleBenchmarkTest.getFilterFlagForFilters(
                            mGoogleBenchmarkTest.getIncludeFilters());
            when(mMockITestDevice.executeShellV2Command(
                            Mockito.contains(StringEscapeUtils.escapeShell(incFilterFlag)),
                            Mockito.anyLong(),
                            (TimeUnit) Mockito.any(),
                            Mockito.anyInt()))
                    .thenReturn(getCommandResult(incTests));
        } else {
            when(mMockITestDevice.executeShellV2Command(
                            AdditionalMatchers.not(
                                    Mockito.contains(GoogleBenchmarkTest.GBENCHMARK_FILTER_OPTION)),
                            Mockito.anyLong(),
                            (TimeUnit) Mockito.any(),
                            Mockito.anyInt()))
                    .thenReturn(getCommandResult(incTests));
        }
        if (mGoogleBenchmarkTest.getExcludeFilters().size() > 0) {
            // List tests to exclude
            String excFilterFlag =
                    mGoogleBenchmarkTest.getFilterFlagForFilters(
                            mGoogleBenchmarkTest.getExcludeFilters());
            when(mMockITestDevice.executeShellV2Command(
                            Mockito.contains(StringEscapeUtils.escapeShell(excFilterFlag)),
                            Mockito.anyLong(),
                            (TimeUnit) Mockito.any(),
                            Mockito.anyInt()))
                    .thenReturn(getCommandResult(excTests));
        }

        mGoogleBenchmarkTest.run(mTestInfo, mMockInvocationListener);
        if (filteredTests != null && filteredTests.size() > 0) {
            // Running filtered tests
            String testFilterFlag = mGoogleBenchmarkTest.getFilterFlagForTests(filteredTests);
            verify(mMockITestDevice)
                    .executeShellV2Command(
                            Mockito.contains(StringEscapeUtils.escapeShell(testFilterFlag)),
                            Mockito.anyLong(),
                            (TimeUnit) Mockito.any(),
                            Mockito.anyInt());
            verify(mMockInvocationListener).testRunStarted("test1", filteredTests.size());
            verify(mMockInvocationListener).testStarted(mDummyTest);
            verify(mMockInvocationListener)
                    .testEnded(eq(mDummyTest), Mockito.<HashMap<String, String>>any());
            // Running filtered tests
            verify(mMockInvocationListener, times(1))
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        }
    }

    /** Test no matching tests for the filters. */
    @Test
    public void testNoMatchingTests() throws DeviceNotAvailableException {
        Set<String> incFilters = new LinkedHashSet<>(Arrays.asList("X", "Y"));
        String incTests = "Failed to match any benchmarks against regex: X|Y";

        mGoogleBenchmarkTest.addAllIncludeFilters(incFilters);
        doTestFilter(incTests, null /* excTests */, null /* testFilter */);
    }

    /** Test the include filtering of test methods. */
    @Test
    public void testIncludeFilter() throws DeviceNotAvailableException {
        Set<String> incFilters = new LinkedHashSet<>(Arrays.asList("A", "B"));
        String incTests = "A\nAa\nB\nBb";
        Set<String> filteredTests = new LinkedHashSet<>(Arrays.asList("A", "Aa", "B", "Bb"));

        mGoogleBenchmarkTest.addAllIncludeFilters(incFilters);
        doTestFilter(incTests, null /* excTests */, filteredTests);
    }

    /** Test the exclude filtering of test methods. */
    @Test
    public void testExcludeFilter() throws DeviceNotAvailableException {
        String incTests = "A\nAa\nB\nBb\nC\nCc";
        Set<String> excFilters = new LinkedHashSet<>(Arrays.asList("Bb", "C"));
        String excTests = "Bb\nC\nCc";
        Set<String> filteredTests = new LinkedHashSet<>(Arrays.asList("A", "Aa", "B"));

        mGoogleBenchmarkTest.addAllExcludeFilters(excFilters);
        doTestFilter(incTests, excTests, filteredTests);
    }

    /** Test the include & exclude filtering of test methods. */
    @Test
    public void testIncludeAndExcludeFilter() throws DeviceNotAvailableException {
        Set<String> incFilters = new LinkedHashSet<>(Arrays.asList("A", "B"));
        String incTests = "A\nAa\nB\nBb";
        Set<String> excFilters = new LinkedHashSet<>(Arrays.asList("Bb", "C"));
        String excTests = "Bb\nC\nCc";
        Set<String> filteredTests = new LinkedHashSet<>(Arrays.asList("A", "Aa", "B"));

        mGoogleBenchmarkTest.addAllIncludeFilters(incFilters);
        mGoogleBenchmarkTest.addAllExcludeFilters(excFilters);
        doTestFilter(incTests, excTests, filteredTests);
    }

    /** Test the ITestDescription filter format "class#method". */
    @Test
    public void testClearFilter() throws DeviceNotAvailableException {
        Set<String> incFilters = new LinkedHashSet<>(Arrays.asList("X#A", "X#B"));
        Set<String> expectedIncFilters = new LinkedHashSet<>(Arrays.asList("A", "B"));
        mGoogleBenchmarkTest.addAllIncludeFilters(incFilters);
        assertEquals(expectedIncFilters, mGoogleBenchmarkTest.getIncludeFilters());
    }

    /** Test behavior for command lines too long to be run by ADB */
    @Test
    public void testCommandTooLong() throws DeviceNotAvailableException {
        String deviceScriptPath = "/data/local/tmp/gbenchmarktest_script.sh";
        StringBuilder testNameBuilder = new StringBuilder();
        for (int i = 0; i < GoogleBenchmarkTest.ADB_CMD_CHAR_LIMIT; i++) {
            testNameBuilder.append("a");
        }
        String testName = testNameBuilder.toString();
        // filter string will be longer than GTest.ADB_CMD_CHAR_LIMIT

        String nativeTestPath = GoogleBenchmarkTest.DEFAULT_TEST_PATH;
        String testPath = nativeTestPath + "/" + testName;
        // configure the mock file system to have a single test
        MockitoFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, "test1");
        when(mMockITestDevice.doesFileExist(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(testPath)).thenReturn(false);
        when(mMockITestDevice.executeShellCommand(Mockito.contains("chmod"))).thenReturn("");
        String[] files = new String[] {testName};
        when(mMockITestDevice.getChildren(nativeTestPath)).thenReturn(files);
        // List tests
        when(mMockITestDevice.pushString(Mockito.<String>any(), eq(deviceScriptPath)))
                .thenReturn(Boolean.TRUE);
        when(mMockITestDevice.executeShellV2Command(
                        eq(String.format("sh %s", deviceScriptPath)),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt()))
                .thenReturn(getCommandResult("test"));
        // Run tests
        when(mMockITestDevice.pushString(Mockito.<String>any(), eq(deviceScriptPath)))
                .thenReturn(Boolean.TRUE);

        mGoogleBenchmarkTest.run(mTestInfo, mMockInvocationListener);

        verify(mMockITestDevice, times(2))
                .executeShellV2Command(
                        eq(String.format("sh %s", deviceScriptPath)),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
        verify(mMockITestDevice, times(2)).deleteFile(deviceScriptPath);
        verify(mMockInvocationListener).testRunStarted(testName, 1);
        verify(mMockInvocationListener).testStarted(mDummyTest);
        verify(mMockInvocationListener)
                .testEnded(eq(mDummyTest), Mockito.<HashMap<String, String>>any());
        verify(mMockInvocationListener, times(1))
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Test the run method for a couple tests which set ld-library-path */
    @Test
    public void testRun_withLDLibPath() throws ConfigurationException, DeviceNotAvailableException {
        final String nativeTestPath = GoogleBenchmarkTest.DEFAULT_TEST_PATH;
        final String test1 = "test1";
        mSetter = new OptionSetter(mGoogleBenchmarkTest);
        mSetter.setOptionValue("ld-library-path", "my/ld/path");
        MockitoFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, test1);
        when(mMockITestDevice.doesFileExist(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(nativeTestPath + "/test1")).thenReturn(false);
        String[] files = new String[] {"test1"};
        when(mMockITestDevice.getChildren(nativeTestPath)).thenReturn(files);
        when(mMockITestDevice.executeShellCommand(Mockito.contains("chmod"))).thenReturn("");
        when(mMockITestDevice.executeShellV2Command(
                        eq(
                                String.format(
                                        "LD_LIBRARY_PATH=my/ld/path %s/test1"
                                                + " --benchmark_list_tests=true",
                                        nativeTestPath)),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt()))
                .thenReturn(getCommandResult("method1\nmethod2\nmethod3"));

        mGoogleBenchmarkTest.run(mTestInfo, mMockInvocationListener);

        verify(mMockITestDevice, times(2))
                .executeShellV2Command(
                        Mockito.contains(test1),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
        verify(mMockInvocationListener).testRunStarted(test1, 3);
        verify(mMockInvocationListener).testStarted(mDummyTest);
        verify(mMockInvocationListener)
                .testEnded(eq(mDummyTest), Mockito.<HashMap<String, String>>any());

        verify(mMockITestDevice, times(1)).executeShellCommand(Mockito.contains("chmod"));
        verify(mMockInvocationListener, times(1))
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    private static CommandResult getCommandResult(String output) {
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStdout(output);
        result.setExitCode(0);
        return result;
    }
}
