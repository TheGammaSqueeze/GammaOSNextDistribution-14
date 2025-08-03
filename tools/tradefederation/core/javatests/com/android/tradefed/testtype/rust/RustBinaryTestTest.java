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
 * limitations under the License.
 */
package com.android.tradefed.testtype.rust;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.MockitoFileUtil;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.coverage.CoverageOptions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link RustBinaryTest}. */
@RunWith(JUnit4.class)
public class RustBinaryTestTest {
    @Mock ITestInvocationListener mMockInvocationListener;
    @Mock IShellOutputReceiver mMockReceiver;
    @Mock ITestDevice mMockITestDevice;
    private RustBinaryTest mRustBinaryTest;
    private TestInformation mTestInfo;
    private Configuration mConfiguration;
    private OptionSetter mOptionsSetter;
    private CoverageOptions mCoverageOptions;
    private OptionSetter mCoverageOptionsSetter;

    /** Helper to initialize the various EasyMocks we'll need. */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockITestDevice.getSerialNumber()).thenReturn("serial");
        mRustBinaryTest =
                new RustBinaryTest() {
                    @Override
                    IShellOutputReceiver createParser(
                            ITestInvocationListener listener, String runName) {
                        return mMockReceiver;
                    }
                };
        mRustBinaryTest.setDevice(mMockITestDevice);
        InvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockITestDevice);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        mOptionsSetter = new OptionSetter(mRustBinaryTest);

        // Set up the coverage options
        mConfiguration = new Configuration("", "");
        mCoverageOptions = new CoverageOptions();
        mCoverageOptionsSetter = new OptionSetter(mCoverageOptions);

        mConfiguration.setCoverageOptions(mCoverageOptions);
        mRustBinaryTest.setConfiguration(mConfiguration);
    }

    private String runListOutput(int numTests) {
        return RustBinaryHostTestTest.runListOutput(numTests);
    }

    private String runListOutput(String[] tests) {
        return RustBinaryHostTestTest.runListOutput(tests);
    }

    private String runListBenchmarksOutput(int numTests) {
        return RustBinaryHostTestTest.runListBenchmarksOutput(numTests);
    }

    /** Add mocked Call "path --list" to count the number of tests. */
    private void mockCountTests(String path, String result) throws DeviceNotAvailableException {
        mockCountTests(path, result, "", "");
    }

    /** Add mocked Call "path --list" to count the number of tests. */
    private void mockCountTests(String path, String result, String flags, String filters)
            throws DeviceNotAvailableException {
        File file = new File(path);
        String dir = file.getParent();
        String cmd = "cd " + dir + " && " + path;
        if (flags.length() > 0) {
            cmd += " " + flags;
        }
        cmd += " -Zunstable-options --report-time";
        if (filters.length() == 0) {
            cmd += " --exact";
        } else {
            cmd += filters;
        }
        when(mMockITestDevice.executeShellCommand(cmd + " --list")).thenReturn(result);
    }

    /** Add mocked Call "path --list --bench" to count the number of tests. */
    private void mockCountBenchmarks(String path, String result)
            throws DeviceNotAvailableException {
        when(mMockITestDevice.executeShellCommand(
                        Mockito.contains(
                                path
                                        + " -Zunstable-options --report-time"
                                        + " --bench --color never --exact"
                                        + " --list")))
                .thenReturn(result);
    }

    /** Add mocked call to testRunStarted. */
    private void mockTestRunStarted(String name, int count) {
        mMockInvocationListener.testRunStarted(
                Mockito.eq(name), Mockito.eq(count), Mockito.anyInt(), Mockito.anyLong());
    }

    /** Add mocked shell command to run a test. */
    private void mockShellCommand(String path) throws DeviceNotAvailableException {
        mMockITestDevice.executeShellCommand(
                Mockito.contains(path),
                Mockito.same(mMockReceiver),
                Mockito.anyLong(),
                (TimeUnit) Mockito.any(),
                Mockito.anyInt());
    }

    /** Add mocked call to testRunEnded. */
    private void mockTestRunEnded() {
        mMockInvocationListener.testRunEnded(
                Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /** Call replay/run/verify. */
    private void callReplayRunVerify() throws DeviceNotAvailableException {

        mRustBinaryTest.run(mTestInfo, mMockInvocationListener);
    }

    /** Test run when the test dir is not found on the device. */
    @Test
    public void testRun_noTestDir() throws DeviceNotAvailableException {
        String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        when(mMockITestDevice.doesFileExist(testPath)).thenReturn(false);
        mockTestRunStarted(testPath, 1);
        mMockInvocationListener.testRunFailed("Could not find test directory " + testPath);
        mockTestRunEnded();
        callReplayRunVerify();
    }

    /** Test run when no device is set should throw an exception. */
    @Test
    public void testRun_noDevice() throws DeviceNotAvailableException {
        mRustBinaryTest.setDevice(null);

        try {
            mRustBinaryTest.run(mTestInfo, mMockInvocationListener);
            fail("an exception should have been thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /** Test the run method for not-found tests */
    @Test
    public void testNotFound() throws DeviceNotAvailableException {
        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;

        MockitoFileUtil.setMockDirContents(mMockITestDevice, testPath);
        when(mMockITestDevice.doesFileExist(testPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(testPath)).thenReturn(true);
        when(mMockITestDevice.getChildren(testPath)).thenReturn(new String[0]);
        mockTestRunStarted(testPath, 1);
        mMockInvocationListener.testRunFailed("No test found under " + testPath);
        mockTestRunEnded();

        callReplayRunVerify();
    }

    /** Test the run method for not-found tests in nested directories */
    @Test
    public void testNotFound2() throws DeviceNotAvailableException {
        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        final String[] dirs = new String[] {"d1", "d2"};
        final String[] d1dirs = new String[] {"d1_1"};
        final String[] nofiles = new String[0];

        MockitoFileUtil.setMockDirContents(mMockITestDevice, testPath, "d1", "d2");
        MockitoFileUtil.setMockDirContents(mMockITestDevice, testPath + "/d1", "d1_1");
        when(mMockITestDevice.doesFileExist(testPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(testPath)).thenReturn(true);
        when(mMockITestDevice.getChildren(testPath)).thenReturn(dirs);
        when(mMockITestDevice.isDirectory(testPath + "/d1")).thenReturn(true);
        when(mMockITestDevice.getChildren(testPath + "/d1")).thenReturn(d1dirs);
        when(mMockITestDevice.isDirectory(testPath + "/d1/d1_1")).thenReturn(true);
        when(mMockITestDevice.getChildren(testPath + "/d1/d1_1")).thenReturn(nofiles);
        when(mMockITestDevice.isDirectory(testPath + "/d2")).thenReturn(true);
        when(mMockITestDevice.getChildren(testPath + "/d2")).thenReturn(nofiles);
        mockTestRunStarted(testPath, 1);
        mMockInvocationListener.testRunFailed("No test found under " + testPath);
        mockTestRunEnded();

        callReplayRunVerify();
    }

    /** Test the run method for a couple tests */
    @Test
    public void testRun() throws DeviceNotAvailableException {
        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        final String test1 = "test1";
        final String test2 = "test2";
        final String testPath1 = String.format("%s/%s", testPath, test1);
        final String testPath2 = String.format("%s/%s", testPath, test2);
        final String[] files = new String[] {test1, test2};

        // Find files
        MockitoFileUtil.setMockDirContents(mMockITestDevice, testPath, test1, test2);
        when(mMockITestDevice.doesFileExist(testPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(testPath)).thenReturn(true);
        when(mMockITestDevice.getChildren(testPath)).thenReturn(files);
        when(mMockITestDevice.isDirectory(testPath1)).thenReturn(false);
        when(mMockITestDevice.isExecutable(testPath1)).thenReturn(true);
        when(mMockITestDevice.isDirectory(testPath2)).thenReturn(false);
        when(mMockITestDevice.isExecutable(testPath2)).thenReturn(true);

        mockCountTests(testPath1, runListOutput(3));
        mockTestRunStarted("test1", 3);
        mockShellCommand(test1);
        mockTestRunEnded();

        mockCountTests(testPath2, runListOutput(7));
        mockTestRunStarted("test2", 7);
        mockShellCommand(test2);
        mockTestRunEnded();
        callReplayRunVerify();
    }

    /** Test the run method when module name is specified */
    @Test
    public void testRun_moduleName() throws DeviceNotAvailableException {
        final String module = "test1";
        final String notModule = "not_a_test";
        final String modulePath =
                String.format(
                        "%s%s%s",
                        RustBinaryTest.DEFAULT_TEST_PATH,
                        FileListingService.FILE_SEPARATOR,
                        module);
        final String notModulePath =
                String.format(
                        "%s%s%s",
                        RustBinaryTest.DEFAULT_TEST_PATH,
                        FileListingService.FILE_SEPARATOR,
                        notModule);
        MockitoFileUtil.setMockDirContents(
                mMockITestDevice, RustBinaryTest.DEFAULT_TEST_PATH, module, notModule);

        mRustBinaryTest.setModuleName(module);
        when(mMockITestDevice.doesFileExist(modulePath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(modulePath)).thenReturn(false);
        when(mMockITestDevice.isExecutable(modulePath)).thenReturn(true);
        when(mMockITestDevice.doesFileExist(notModulePath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(notModulePath)).thenReturn(false);
        when(mMockITestDevice.isExecutable(notModulePath)).thenReturn(true);

        mockCountTests(modulePath, runListOutput(1));
        mockTestRunStarted("test1", 1);
        mockShellCommand(modulePath);
        mockTestRunEnded();
        callReplayRunVerify();
    }

    /** Test the run method for a test in a subdirectory */
    @Test
    public void testRun_nested() throws DeviceNotAvailableException {
        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        final String subFolderName = "subFolder";
        final String subDirPath = testPath + "/" + subFolderName;
        final String test1 = "test1";
        final String test1Path =
                String.format(
                        "%s%s%s%s%s",
                        testPath,
                        FileListingService.FILE_SEPARATOR,
                        subFolderName,
                        FileListingService.FILE_SEPARATOR,
                        test1);
        MockitoFileUtil.setMockDirPath(mMockITestDevice, testPath, subFolderName, test1);
        when(mMockITestDevice.doesFileExist(testPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(testPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(subDirPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(test1Path)).thenReturn(false);
        // report the file as executable
        when(mMockITestDevice.isExecutable(test1Path)).thenReturn(true);
        String[] files = new String[] {subFolderName};
        when(mMockITestDevice.getChildren(testPath)).thenReturn(files);
        String[] files2 = new String[] {"test1"};
        when(mMockITestDevice.getChildren(subDirPath)).thenReturn(files2);

        mockCountTests(test1Path, runListOutput(5));
        mockTestRunStarted("test1", 5);
        mockShellCommand(test1Path);
        mockTestRunEnded();
        callReplayRunVerify();
    }

    /** Tests that GCOV_PREFIX is set when running with the GCOV coverage toolchain. */
    @Test
    public void testGcovCoverage_GcovPrefixSet() throws Exception {
        mCoverageOptionsSetter.setOptionValue("coverage", "true");
        mCoverageOptionsSetter.setOptionValue("coverage-toolchain", "GCOV");

        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        final String test1 = "test1";
        final String testPath1 = String.format("%s/%s", testPath, test1);
        final String[] files = new String[] {test1};

        // Find files
        MockitoFileUtil.setMockDirContents(mMockITestDevice, testPath, test1);
        when(mMockITestDevice.doesFileExist(testPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(testPath)).thenReturn(true);
        when(mMockITestDevice.getChildren(testPath)).thenReturn(files);
        when(mMockITestDevice.isDirectory(testPath1)).thenReturn(false);
        when(mMockITestDevice.isExecutable(testPath1)).thenReturn(true);

        mockCountTests(testPath1, runListOutput(3));
        mockTestRunStarted("test1", 3);
        mockShellCommand("GCOV_PREFIX=/data/misc/trace");
        mockTestRunEnded();
        callReplayRunVerify();
    }

    /**
     * Helper function to do the actual filtering test.
     *
     * @param filterStrings The strings to search for in the Mock, to verify filtering was called
     * @throws DeviceNotAvailableException
     */
    private void doTestFilter(String[] filterStrings) throws DeviceNotAvailableException {
        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        final String test1 = "test1";
        final String testPath1 = String.format("%s/%s", testPath, test1);
        final String[] files = new String[] {test1};

        // Find files
        MockitoFileUtil.setMockDirContents(mMockITestDevice, testPath, test1);
        when(mMockITestDevice.doesFileExist(testPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(testPath)).thenReturn(true);
        when(mMockITestDevice.getChildren(testPath)).thenReturn(files);
        when(mMockITestDevice.isDirectory(testPath1)).thenReturn(false);
        when(mMockITestDevice.isExecutable(testPath1)).thenReturn(true);

        for (String filter : filterStrings) {
            mockCountTests(testPath1, runListOutput(3), "", filter);
        }
        mockTestRunStarted("test1", 3);
        for (String filter : filterStrings) {
            mockShellCommand(test1 + " -Zunstable-options --report-time" + filter);
        }
        mockTestRunEnded();
        callReplayRunVerify();
    }

    /** Test the exclude-filter option. */
    @Test
    public void testExcludeFilter() throws Exception {
        OptionSetter setter = new OptionSetter(mRustBinaryTest);
        setter.setOptionValue("exclude-filter", "NotMe");
        setter.setOptionValue("exclude-filter", "MyTest#Long");
        doTestFilter(new String[] {" --exact --skip NotMe --skip Long"});
    }

    /** Test both include- and exclude-filter options. */
    @Test
    public void testIncludeExcludeFilter() throws Exception {
        OptionSetter setter = new OptionSetter(mRustBinaryTest);
        setter.setOptionValue("exclude-filter", "NotMe2");
        setter.setOptionValue("include-filter", "MyTest#OnlyMe");
        setter.setOptionValue("exclude-filter", "MyTest#other");
        // Include filters are passed before exclude filters.
        doTestFilter(new String[] {" OnlyMe --exact --skip NotMe2 --skip other"});
    }

    /** Test multiple include- and exclude-filter options. */
    @Test
    public void testMultipleIncludeExcludeFilter() throws Exception {
        OptionSetter setter = new OptionSetter(mRustBinaryTest);
        setter.setOptionValue("exclude-filter", "MyTest#NotMe2");
        setter.setOptionValue("include-filter", "MyTest#OnlyMe");
        setter.setOptionValue("exclude-filter", "other");
        setter.setOptionValue("include-filter", "Me2");
        // Multiple include filters are run one by one.
        doTestFilter(
                new String[] {
                    " OnlyMe --exact --skip NotMe2 --skip other",
                    " Me2 --exact --skip NotMe2 --skip other"
                });
    }

    @Test
    public void testOptions() throws Exception {
        OptionSetter setter = new OptionSetter(mRustBinaryTest);
        setter.setOptionValue("native-test-flag", "--option");

        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        final String test1 = "test1";
        final String testPath1 = String.format("%s/%s", testPath, test1);
        final String[] files = new String[] {test1};

        // Find files
        MockitoFileUtil.setMockDirContents(mMockITestDevice, testPath, test1);
        when(mMockITestDevice.doesFileExist(testPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(testPath)).thenReturn(true);
        when(mMockITestDevice.getChildren(testPath)).thenReturn(files);
        when(mMockITestDevice.isDirectory(testPath1)).thenReturn(false);
        when(mMockITestDevice.isExecutable(testPath1)).thenReturn(true);

        mockCountTests(testPath1, runListOutput(42), "--option", "");
        mockTestRunStarted("test1", 42);
        mockShellCommand(testPath1 + " --option");
        mockTestRunEnded();
        callReplayRunVerify();
    }

    @Test
    public void testLdLibraryPathOption() throws Exception {
        OptionSetter setter = new OptionSetter(mRustBinaryTest);
        setter.setOptionValue("ld-library-path", "/library/path");

        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        final String test1 = "test1";
        final String testPath1 = String.format("%s/%s", testPath, test1);
        final String[] files = new String[] {test1};

        // Find files
        MockitoFileUtil.setMockDirContents(mMockITestDevice, testPath, test1);
        when(mMockITestDevice.doesFileExist(testPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(testPath)).thenReturn(true);
        when(mMockITestDevice.getChildren(testPath)).thenReturn(files);
        when(mMockITestDevice.isDirectory(testPath1)).thenReturn(false);
        when(mMockITestDevice.isExecutable(testPath1)).thenReturn(true);

        callReplayRunVerify();

        // Verify that the LD_LIBRARY_PATH environment variable is specified, and it's right before
        // the command for executing the test.
        verify(mMockITestDevice, Mockito.atLeastOnce())
                .executeShellCommand(
                        Mockito.contains(
                                "RUST_BACKTRACE=full LD_LIBRARY_PATH=/library/path"
                                        + " /data/local/tmp/test1"),
                        Mockito.same(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
    }

    /** Test the benchmark run for a couple tests */
    @Test
    public void testRun_benchmark() throws ConfigurationException, DeviceNotAvailableException {
        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        final String test1 = "test1";
        final String test2 = "test2";
        final String testPath1 = String.format("%s/%s", testPath, test1);
        final String testPath2 = String.format("%s/%s", testPath, test2);
        final String[] files = new String[] {test1, test2};

        mOptionsSetter.setOptionValue("is-benchmark", "true");

        // Find files
        MockitoFileUtil.setMockDirContents(mMockITestDevice, testPath, test1, test2);
        when(mMockITestDevice.doesFileExist(testPath)).thenReturn(true);
        when(mMockITestDevice.isDirectory(testPath)).thenReturn(true);
        when(mMockITestDevice.getChildren(testPath)).thenReturn(files);
        when(mMockITestDevice.isDirectory(testPath1)).thenReturn(false);
        when(mMockITestDevice.isExecutable(testPath1)).thenReturn(true);
        when(mMockITestDevice.isDirectory(testPath2)).thenReturn(false);
        when(mMockITestDevice.isExecutable(testPath2)).thenReturn(true);

        mockCountBenchmarks(testPath1, runListBenchmarksOutput(3));
        mockTestRunStarted("test1", 3);
        mockShellCommand(test1);
        mockTestRunEnded();

        mockCountBenchmarks(testPath2, runListBenchmarksOutput(7));
        mockTestRunStarted("test2", 7);
        mockShellCommand(test2);
        mockTestRunEnded();
        callReplayRunVerify();
    }
}
