/*
 * Copyright (C) 2010 The Android Open Source Project
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.MockitoFileUtil;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.coverage.CoverageOptions;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link GTest}. */
@RunWith(JUnit4.class)
public class GTestTest {
    private static final String GTEST_FLAG_FILTER = "--gtest_filter";
    @Mock IInvocationContext mMockContext;
    @Mock ITestInvocationListener mMockInvocationListener;
    @Mock IShellOutputReceiver mMockReceiver;
    @Mock ITestDevice mMockITestDevice;
    private GTest mGTest;
    private OptionSetter mSetter;

    private TestInformation mTestInfo;
    private Configuration mConfiguration;
    private CoverageOptions mCoverageOptions;
    private OptionSetter mCoverageOptionsSetter;

    /** Helper to initialize the various EasyMocks we'll need. */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockITestDevice.getSerialNumber()).thenReturn("serial");
        when(mMockContext.getDevices()).thenReturn(ImmutableList.of(mMockITestDevice));
        mGTest =
                new GTest() {
                    @Override
                    IShellOutputReceiver createResultParser(
                            String runName, ITestInvocationListener listener) {
                        return mMockReceiver;
                    }

                    @Override
                    GTestXmlResultParser createXmlParser(
                            String testRunName, ITestInvocationListener listener) {
                        return new GTestXmlResultParser(testRunName, listener) {
                            @Override
                            public void parseResult(File f, CollectingOutputReceiver output) {
                                return;
                            }
                        };
                    }
                };
        mGTest.setDevice(mMockITestDevice);
        mSetter = new OptionSetter(mGTest);

        // Set up the coverage options
        mConfiguration = new Configuration("", "");
        mCoverageOptions = new CoverageOptions();
        mCoverageOptionsSetter = new OptionSetter(mCoverageOptions);

        mConfiguration.setCoverageOptions(mCoverageOptions);
        mGTest.setConfiguration(mConfiguration);

        mTestInfo = TestInformation.newBuilder().setInvocationContext(mMockContext).build();
    }

    /** Test run when the test dir is not found on the device. */
    @Test
    public void testRun_noTestDir() throws DeviceNotAvailableException {
        when(mMockITestDevice.doesFileExist(GTest.DEFAULT_NATIVETEST_PATH)).thenReturn(false);

        mGTest.run(mTestInfo, mMockInvocationListener);
        verify(mMockITestDevice).doesFileExist(GTest.DEFAULT_NATIVETEST_PATH);
    }

    /** Test run when no device is set should throw an exception. */
    @Test
    public void testRun_noDevice() throws DeviceNotAvailableException {
        mGTest.setDevice(null);

        try {
            mGTest.run(mTestInfo, mMockInvocationListener);
            fail("an exception should have been thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /** Test the run method for a couple tests */
    @Test
    public void testRun() throws DeviceNotAvailableException {
        final String nativeTestPath = GTest.DEFAULT_NATIVETEST_PATH;
        final String test1 = "test1";
        final String test2 = "test2";
        final String testPath1 = String.format("%s/%s", nativeTestPath, test1);
        final String testPath2 = String.format("%s/%s", nativeTestPath, test2);
        final String findCmd = String.format("find -L %s -type f -perm -a=x", nativeTestPath);
        final String findCmdOut = testPath1 + "\n" + testPath2;

        when(mMockITestDevice.doesFileExist(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.executeShellCommand(findCmd)).thenReturn(findCmdOut);

        mGTest.run(mTestInfo, mMockInvocationListener);

        verify(mMockITestDevice)
                .executeShellCommand(
                        Mockito.contains(test1),
                        Mockito.same(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
        verify(mMockITestDevice)
                .executeShellCommand(
                        Mockito.contains(test2),
                        Mockito.same(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
    }

    @Test
    public void testRunFilterAbiPath() throws DeviceNotAvailableException {
        final String nativeTestPath = GTest.DEFAULT_NATIVETEST_PATH;
        final String test1 = "arm/test1";
        final String test2 = "arm64/test2";
        final String testPath2 = String.format("%s/%s", nativeTestPath, test2);
        final String findCmd =
                String.format(
                        "find -L %s -type f -perm -a=x -not -path \"*/arm/*\" -not -path"
                            + " \"*/x86/*\" -not -path \"*/x86_64/*\" -not -path \"*/mips/*\" -not"
                            + " -path \"*/mips64/*\" -not -path \"*/riscv64/*\"",
                        nativeTestPath);
        final String findCmdOut = testPath2;

        MockitoFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, test1, test2);
        mGTest.setAbi(new Abi("arm64-v8a", "64"));

        when(mMockITestDevice.doesFileExist(nativeTestPath)).thenReturn(true);

        when(mMockITestDevice.executeShellCommand(findCmd)).thenReturn(findCmdOut);

        mGTest.run(mTestInfo, mMockInvocationListener);

        verify(mMockITestDevice)
                .executeShellCommand(
                        Mockito.contains(test2),
                        Mockito.same(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
    }

    /** Test the run method when module name is specified */
    @Test
    public void testRun_moduleName() throws DeviceNotAvailableException {
        final String nativeTestPath = GTest.DEFAULT_NATIVETEST_PATH;
        final String module = "test1";
        final String modulePath = String.format("%s/%s", nativeTestPath, module);
        final String findCmd = String.format("find -L %s -type f -perm -a=x", modulePath);
        final String findCmdOut = modulePath;

        MockitoFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, module, "not_a_test");

        mGTest.setModuleName(module);

        when(mMockITestDevice.doesFileExist(modulePath)).thenReturn(true);
        when(mMockITestDevice.executeShellCommand(findCmd)).thenReturn(findCmdOut);
        mGTest.run(mTestInfo, mMockInvocationListener);

        verify(mMockITestDevice)
                .executeShellCommand(
                        Mockito.contains(modulePath),
                        Mockito.same(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
    }

    /**
     * Helper function to do the actual filtering test.
     *
     * @param filterString The string to search for in the Mock, to verify filtering was called
     * @throws DeviceNotAvailableException
     */
    private void doTestFilter(String filterString) throws DeviceNotAvailableException {
        String nativeTestPath = GTest.DEFAULT_NATIVETEST_PATH;
        String testPath = nativeTestPath + "/test1";
        String findCmd = String.format("find -L %s -type f -perm -a=x", nativeTestPath);
        String findCmdOut = testPath;
        // configure the mock file system to have a single test
        MockitoFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, "test1");
        when(mMockITestDevice.doesFileExist(nativeTestPath)).thenReturn(true);

        when(mMockITestDevice.executeShellCommand(findCmd)).thenReturn(findCmdOut);

        mGTest.run(mTestInfo, mMockInvocationListener);

        verify(mMockITestDevice)
                .executeShellCommand(
                        Mockito.contains(filterString),
                        Mockito.same(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
    }

    /** Test the include filtering of test methods. */
    @Test
    public void testIncludeFilter() throws DeviceNotAvailableException {
        String includeFilter1 = "abc";
        String includeFilter2 = "def";
        mGTest.addIncludeFilter(includeFilter1);
        mGTest.addIncludeFilter(includeFilter2);
        doTestFilter(String.format("%s=%s:%s", GTEST_FLAG_FILTER, includeFilter1, includeFilter2));
    }

    /** Test that large filters are converted to flagfile. */
    @Test
    public void testLargeFilters() throws DeviceNotAvailableException {
        StringBuilder includeFilter1 = new StringBuilder("abc");
        for (int i = 0; i < 550; i++) {
            includeFilter1.append("a");
        }
        String includeFilter2 = "def";
        mGTest.addIncludeFilter(includeFilter1.toString());
        mGTest.addIncludeFilter(includeFilter2);

        when(mMockITestDevice.pushFile(Mockito.any(), Mockito.any())).thenReturn(true);

        doTestFilter(String.format("%s=/data/local/tmp/flagfile", GTestBase.GTEST_FLAG_FILE));
    }

    /** Test the exclude filtering of test methods. */
    @Test
    public void testExcludeFilter() throws DeviceNotAvailableException {
        String excludeFilter1 = "*don?tRunMe*";
        mGTest.addExcludeFilter(excludeFilter1);

        doTestFilter(String.format("%s=-%s", GTEST_FLAG_FILTER, excludeFilter1));
    }

    /** Test simultaneous include and exclude filtering of test methods. */
    @Test
    public void testIncludeAndExcludeFilters() throws DeviceNotAvailableException {
        String includeFilter1 = "pleaseRunMe";
        String includeFilter2 = "andMe";
        String excludeFilter1 = "dontRunMe";
        String excludeFilter2 = "orMe";
        mGTest.addIncludeFilter(includeFilter1);
        mGTest.addExcludeFilter(excludeFilter1);
        mGTest.addIncludeFilter(includeFilter2);
        mGTest.addExcludeFilter(excludeFilter2);

        doTestFilter(
                String.format(
                        "%s=%s:%s-%s:%s",
                        GTEST_FLAG_FILTER,
                        includeFilter1,
                        includeFilter2,
                        excludeFilter1,
                        excludeFilter2));
    }

    /** Test behavior for command lines too long to be run by ADB */
    @Test
    public void testCommandTooLong() throws DeviceNotAvailableException {
        String deviceScriptPath = "/data/local/tmp/gtest_script.sh";
        StringBuilder testNameBuilder = new StringBuilder();
        for (int i = 0; i < 1005; i++) {
            testNameBuilder.append("a");
        }
        String testName = testNameBuilder.toString();
        // filter string will be longer than GTest.GTEST_CMD_CHAR_LIMIT

        String nativeTestPath = GTest.DEFAULT_NATIVETEST_PATH;
        String testPath = nativeTestPath + "/" + testName;
        String findCmd = String.format("find -L %s -type f -perm -a=x", nativeTestPath);
        String findCmdOut = testPath;
        when(mMockITestDevice.doesFileExist(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.executeShellCommand(findCmd)).thenReturn(findCmdOut);
        // Expect push of script file
        when(mMockITestDevice.pushString(Mockito.<String>any(), Mockito.eq(deviceScriptPath)))
                .thenReturn(Boolean.TRUE);
        // chmod 755 for the shell script
        when(mMockITestDevice.executeShellCommand(Mockito.contains("chmod"))).thenReturn("");
        // Expect command to run shell script, rather than direct adb command

        // Expect deletion of file on device

        mGTest.run(mTestInfo, mMockInvocationListener);

        verify(mMockITestDevice, times(1)).executeShellCommand(Mockito.contains("chmod"));
        verify(mMockITestDevice)
                .executeShellCommand(
                        Mockito.eq(String.format("sh %s", deviceScriptPath)),
                        Mockito.same(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
        verify(mMockITestDevice).deleteFile(deviceScriptPath);
    }

    /** Empty file exclusion regex filter should not skip any files */
    @Test
    public void testFileExclusionRegexFilter_emptyfilters() throws Exception {
        // report /test_file as executable
        ITestDevice mockDevice = mock(ITestDevice.class);
        when(mockDevice.isExecutable("/test_file")).thenReturn(true);

        mGTest.setDevice(mockDevice);
        assertTrue(mGTest.shouldRunFile("/test_file"));
    }

    /** File exclusion regex filter should skip invalid filepath. */
    @Test
    public void testFileExclusionRegexFilter_invalidInputString() throws Exception {
        assertFalse(mGTest.shouldRunFile(null));
        assertFalse(mGTest.shouldRunFile(""));
    }

    /** File exclusion regex filter should skip matched filepaths. */
    @Test
    public void testFileExclusionRegexFilter_skipMatched() throws Exception {
        // report all files as executable
        ITestDevice mockDevice = mock(ITestDevice.class);
        when(mockDevice.isExecutable("/some/path/file/run_me")).thenReturn(true);
        when(mockDevice.isExecutable("/some/path/file/run_me2")).thenReturn(true);
        when(mockDevice.isExecutable("/some/path/file/run_me.not")).thenReturn(true);
        when(mockDevice.isExecutable("/some/path/file/run_me.so")).thenReturn(true);

        mGTest.setDevice(mockDevice);
        // Skip files ending in .not
        mGTest.addFileExclusionFilterRegex(".*\\.not");
        assertTrue(mGTest.shouldRunFile("/some/path/file/run_me"));
        assertTrue(mGTest.shouldRunFile("/some/path/file/run_me2"));
        assertFalse(mGTest.shouldRunFile("/some/path/file/run_me.not"));
        // Ensure that the default .so filter is present.
        assertFalse(mGTest.shouldRunFile("/some/path/file/run_me.so"));
    }

    /** File exclusion regex filter for multi filters. */
    @Test
    public void testFileExclusionRegexFilter_skipMultiMatched() throws Exception {
        // report all files as executable
        ITestDevice mockDevice = mock(ITestDevice.class);
        when(mockDevice.isExecutable("/some/path/file/run_me")).thenReturn(true);
        when(mockDevice.isExecutable("/some/path/file/run_me.not")).thenReturn(true);
        when(mockDevice.isExecutable("/some/path/file/run_me.not2")).thenReturn(true);

        mGTest.setDevice(mockDevice);
        // Skip files ending in .not
        mGTest.addFileExclusionFilterRegex(".*\\.not");
        // Also skip files ending in .not2
        mGTest.addFileExclusionFilterRegex(".*\\.not2");
        assertTrue(mGTest.shouldRunFile("/some/path/file/run_me"));
        assertFalse(mGTest.shouldRunFile("/some/path/file/run_me.not"));
        assertFalse(mGTest.shouldRunFile("/some/path/file/run_me.not2"));
    }

    /** Test the run method for a couple tests */
    @Test
    public void testRunXml() throws Exception {
        mSetter.setOptionValue("xml-output", "true");

        final String nativeTestPath = GTest.DEFAULT_NATIVETEST_PATH;
        final String test1 = "test1";
        final String test2 = "test2";
        final String testPath1 = String.format("%s/%s", nativeTestPath, test1);
        final String testPath2 = String.format("%s/%s", nativeTestPath, test2);
        final String findCmd = String.format("find -L %s -type f -perm -a=x", nativeTestPath);
        final String findCmdOut = testPath1 + "\n" + testPath2;

        MockitoFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, test1, test2);
        when(mMockITestDevice.doesFileExist(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.executeShellCommand(findCmd)).thenReturn(findCmdOut);

        when(mMockITestDevice.pullFile((String) Mockito.any(), (File) Mockito.any()))
                .thenReturn(true);

        mGTest.run(mTestInfo, mMockInvocationListener);

        verify(mMockITestDevice).deleteFile(testPath1 + "_res.xml");
        verify(mMockITestDevice).deleteFile(testPath2 + "_res.xml");
        verify(mMockITestDevice)
                .executeShellCommand(
                        Mockito.contains(test1),
                        (CollectingOutputReceiver) Mockito.any(),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
        verify(mMockITestDevice)
                .executeShellCommand(
                        Mockito.contains(test2),
                        (CollectingOutputReceiver) Mockito.any(),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
    }

    @Test
    public void testGetFileName() {
        String expected = "bar";
        String s1 = "/foo/" + expected;
        String s2 = expected;
        String s3 = "/foo/";
        assertEquals(expected, mGTest.getFileName(s1));
        assertEquals(expected, mGTest.getFileName(s2));
        try {
            mGTest.getFileName(s3);
            fail("Expected IllegalArgumentException not thrown");
        } catch (IllegalArgumentException iae) {
            // expected
        }
    }

    /** Test the include filtering by file of test methods. */
    @Test
    public void testFileFilter() throws Exception {
        String fileFilter = "presubmit";
        mSetter.setOptionValue("test-filter-key", fileFilter);
        String expectedFilterFile =
                String.format("%s/test1%s", GTest.DEFAULT_NATIVETEST_PATH, GTest.FILTER_EXTENSION);
        String fakeContent =
                "{\n"
                        + "    \"presubmit\": {\n"
                        + "        \"filter\": \"Foo1.*:Foo2.*\"\n"
                        + "    },\n"
                        + "    \"continuous\": {\n"
                        + "        \"filter\": \"Foo1.*:Foo2.*:Bar.*\"\n"
                        + "    }\n"
                        + "}\n";
        when(mMockITestDevice.doesFileExist(expectedFilterFile)).thenReturn(true);
        when(mMockITestDevice.executeShellCommand("cat \"" + expectedFilterFile + "\""))
                .thenReturn(fakeContent);
        doTestFilter(String.format("%s=%s", GTEST_FLAG_FILTER, "Foo1.*:Foo2.*"));
    }

    @Test
    public void testFileFilter_negative() throws Exception {
        String fileFilter = "presubmit";
        mSetter.setOptionValue("test-filter-key", fileFilter);
        String expectedFilterFile =
                String.format("%s/test1%s", GTest.DEFAULT_NATIVETEST_PATH, GTest.FILTER_EXTENSION);
        String fakeContent =
                "{\n"
                        + "    \"presubmit\": {\n"
                        + "        \"filter\": \"Foo1.*-Foo2.*\"\n"
                        + "    },\n"
                        + "    \"continuous\": {\n"
                        + "        \"filter\": \"Foo1.*:Foo2.*:Bar.*\"\n"
                        + "    }\n"
                        + "}\n";
        when(mMockITestDevice.doesFileExist(expectedFilterFile)).thenReturn(true);
        when(mMockITestDevice.executeShellCommand("cat \"" + expectedFilterFile + "\""))
                .thenReturn(fakeContent);
        doTestFilter(String.format("%s=%s", GTEST_FLAG_FILTER, "Foo1.*-Foo2.*"));
    }

    @Test
    public void testFileFilter_negativeOnly() throws Exception {
        String fileFilter = "presubmit";
        mSetter.setOptionValue("test-filter-key", fileFilter);
        String expectedFilterFile =
                String.format("%s/test1%s", GTest.DEFAULT_NATIVETEST_PATH, GTest.FILTER_EXTENSION);
        String fakeContent =
                "{\n"
                        + "    \"presubmit\": {\n"
                        + "        \"filter\": \"-Foo1.*:Foo2.*\"\n"
                        + "    },\n"
                        + "    \"continuous\": {\n"
                        + "        \"filter\": \"Foo1.*:Foo2.*:Bar.*\"\n"
                        + "    }\n"
                        + "}\n";
        when(mMockITestDevice.doesFileExist(expectedFilterFile)).thenReturn(true);
        when(mMockITestDevice.executeShellCommand("cat \"" + expectedFilterFile + "\""))
                .thenReturn(fakeContent);
        doTestFilter(String.format("%s=%s", GTEST_FLAG_FILTER, "-Foo1.*:Foo2.*"));
    }

    /**
     * Test the include filtering by providing a non existing filter. No filter will be applied in
     * this case.
     */
    @Test
    public void testFileFilter_notfound() throws Exception {
        String fileFilter = "garbage";
        mSetter.setOptionValue("test-filter-key", fileFilter);
        String expectedFilterFile =
                String.format("%s/test1%s", GTest.DEFAULT_NATIVETEST_PATH, GTest.FILTER_EXTENSION);
        String fakeContent =
                "{\n"
                        + "    \"presubmit\": {\n"
                        + "        \"filter\": \"Foo1.*:Foo2.*\"\n"
                        + "    },\n"
                        + "    \"continuous\": {\n"
                        + "        \"filter\": \"Foo1.*:Foo2.*:Bar.*\"\n"
                        + "    }\n"
                        + "}\n";
        when(mMockITestDevice.doesFileExist(expectedFilterFile)).thenReturn(true);
        when(mMockITestDevice.executeShellCommand("cat \"" + expectedFilterFile + "\""))
                .thenReturn(fakeContent);
        doTestFilter("");
    }

    /** Test {@link GTest#getGTestCmdLine(String, String)} with default options. */
    @Test
    public void testGetGTestCmdLine_defaults() {
        String cmd_line = mGTest.getGTestCmdLine("test_path", "flags");
        assertEquals("test_path flags", cmd_line);
    }

    /**
     * Test {@link GTest#getGTestFilters(String)} When the push to file fails, in this case we use
     * the original filter arguments instead of hte flagfile.
     */
    @Test
    public void testGetGTestFilters_largeFilters_pushFail() throws Exception {
        StringBuilder includeFilter1 = new StringBuilder("abc");
        for (int i = 0; i < 550; i++) {
            includeFilter1.append("a");
        }
        mGTest.addIncludeFilter(includeFilter1.toString());
        // Fail to push
        when(mMockITestDevice.pushFile(Mockito.any(), Mockito.any())).thenReturn(false);

        String flag = mGTest.getGTestFilters("/path/");
        // We fallback to the original command line filter
        assertEquals("--gtest_filter=" + includeFilter1.toString(), flag);
    }

    /**
     * Test {@link GTest#getGTestCmdLine(String, String)} with an LD_LIBRARY_PATH environment
     * variable option.
     */
    @Test
    public void testGetGTestCmdLine_ldLibraryPath() throws Exception {
        mSetter.setOptionValue("ld-library-path", "/path1:/path2:/path3");

        String cmd_line = mGTest.getGTestCmdLine("test_path", "flags");
        assertEquals("LD_LIBRARY_PATH=/path1:/path2:/path3 test_path flags", cmd_line);
    }

    /**
     * Test {@link GTest#getGTestCmdLine(String, String)} with multiple LD_LIBRARY_PATH environment
     * variable options on a 32-bit device.
     */
    @Test
    public void testGetGTestCmdLine_multipleLdLibraryPath32BitDevice() throws Exception {
        mGTest.setAbi(new Abi("armeabi-v7a", "32"));
        mSetter.setOptionValue("ld-library-path", "/lib");
        mSetter.setOptionValue("ld-library-path-32", "/lib32");
        mSetter.setOptionValue("ld-library-path-64", "/lib64");

        String cmd_line = mGTest.getGTestCmdLine("test_path", "flags");
        assertEquals("LD_LIBRARY_PATH=/lib32 test_path flags", cmd_line);
    }

    /**
     * Test {@link GTest#getGTestCmdLine(String, String)} with multiple LD_LIBRARY_PATH environment
     * variable options on a 64-bit device.
     */
    @Test
    public void testGetGTestCmdLine_multipleLdLibraryPath64BitDevice() throws Exception {
        mGTest.setAbi(new Abi("arm64-v8a", "64"));
        mSetter.setOptionValue("ld-library-path", "/lib");
        mSetter.setOptionValue("ld-library-path", "/lib32");
        mSetter.setOptionValue("ld-library-path", "/lib64");

        String cmd_line = mGTest.getGTestCmdLine("test_path", "flags");
        assertEquals("LD_LIBRARY_PATH=/lib64 test_path flags", cmd_line);
    }

    /** Test {@link GTest#getGTestCmdLine(String, String)} with environment variable. */
    @Test
    public void testGetGTestCmdLine_envVar() throws Exception {
        mSetter.setOptionValue("gtest-env", "VAR1=VAL1");
        mSetter.setOptionValue("gtest-env", "VAR2=VAL2");

        String cmd_line = mGTest.getGTestCmdLine("test_path", "flags");
        assertEquals("VAR1=VAL1 VAR2=VAL2 test_path flags", cmd_line);
    }

    /** Test {@link GTest#getGTestCmdLine(String, String)} with non-default user. */
    @Test
    public void testGetGTestCmdLine_runAs() throws Exception {
        mSetter.setOptionValue("run-test-as", "shell");

        String cmd_line = mGTest.getGTestCmdLine("test_path", "flags");
        assertEquals("su shell test_path flags", cmd_line);
    }

    /** Test GTest command line string for sharded tests. */
    @Test
    public void testGetGTestCmdLine_testShard() {
        mGTest.setShardIndex(1);
        mGTest.setShardCount(3);

        String cmd_line = mGTest.getGTestCmdLine("test_path", "flags");
        assertEquals("GTEST_SHARD_INDEX=1 GTEST_TOTAL_SHARDS=3 test_path flags", cmd_line);
    }
}
