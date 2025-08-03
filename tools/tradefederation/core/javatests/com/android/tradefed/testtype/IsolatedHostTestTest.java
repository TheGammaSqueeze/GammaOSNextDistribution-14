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
package com.android.tradefed.testtype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.build.BuildInfoKey.BuildInfoFileKey;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.testtype.coverage.CoverageOptions;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ResourceUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Unit tests for {@link IsolatedHostTest}. */
@RunWith(JUnit4.class)
public class IsolatedHostTestTest {

    private static final String PACKAGE = "/com/android/tradefed/referencetests";
    private IsolatedHostTest mHostTest;
    @Mock ITestInvocationListener mListener;
    private IBuildInfo mMockBuildInfo;
    private ServerSocket mMockServer;
    private File mMockTestDir;

    /**
     * (copied and altered from JarHostTestTest) Helper to read a file from the res/testtype
     * directory and return it.
     *
     * @param filename the name of the file in the resources.
     * @param parentDir dir where to put the jar. Null if in default tmp directory.
     * @param name name to use in the target directory for the jar.
     * @return the extracted jar file.
     */
    protected File getJarResource(String filename, File parentDir, String name) throws IOException {
        File jarFile = FileUtil.createTempFile("tradefed-isolation", ".jar", parentDir);
        boolean res =
                ResourceUtil.extractResourceWithAltAsFile(filename, PACKAGE + filename, jarFile);
        if (!res) {
            FileUtil.deleteFile(jarFile);
            throw new IOException(String.format("Failed to read resource '%s'", filename));
        }
        return jarFile;
    }

    private void makeDirAndAddToList(File parentDir, String dirName, List<String> list) {
        File lib = new File(parentDir, dirName);
        lib.mkdir();
        list.add(lib.getAbsolutePath());
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mHostTest =
                new IsolatedHostTest() {
                    @Override
                    String getEnvironment(String key) {
                        return null;
                    }
                };

        mMockBuildInfo = Mockito.mock(IBuildInfo.class);
        mMockServer = Mockito.mock(ServerSocket.class);
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mHostTest.setBuild(mMockBuildInfo);
        mHostTest.setServer(mMockServer);
        mMockTestDir = FileUtil.createTempDir("isolatedhosttesttest");
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mMockTestDir);
        mHostTest.deleteTempFiles();
    }

    @Test
    public void testRobolectricResourcesPositive() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("use-robolectric-resources", "true");
        File androidAll = new File(mMockTestDir, "android-all");
        androidAll.mkdirs();
        doReturn(mMockTestDir).when(mMockBuildInfo).getFile(BuildInfoFileKey.HOST_LINKED_DIR);
        doReturn(36000).when(mMockServer).getLocalPort();
        doReturn(Inet4Address.getByName("localhost")).when(mMockServer).getInetAddress();

        List<String> commandArgs = mHostTest.compileCommandArgs("", null);
        assertTrue(commandArgs.contains("-Drobolectric.offline=true"));
        assertTrue(commandArgs.contains("-Drobolectric.logging=stdout"));
        assertTrue(commandArgs.contains("-Drobolectric.resourcesMode=binary"));
        assertTrue(
                commandArgs.stream()
                        .anyMatch(
                                s ->
                                        s.contains(
                                                "-Drobolectric.dependency.dir="
                                                        + androidAll.getAbsolutePath()
                                                        + "/")));
    }

    @Test
    public void testRavenwoodResourcesPositive() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("use-ravenwood-resources", "true");

        File dir = new File(mMockTestDir, "ravenwood-runtime");
        dir.mkdirs();
        File.createTempFile("temp", ".jar", dir);

        // Create the JNI directories.
        List<String> ldLibraryPath = new ArrayList<>();
        makeDirAndAddToList(dir, "lib", ldLibraryPath);
        makeDirAndAddToList(dir, "lib64", ldLibraryPath);

        doReturn(mMockTestDir).when(mMockBuildInfo).getFile(BuildInfoFileKey.HOST_LINKED_DIR);
        doReturn(36000).when(mMockServer).getLocalPort();
        doReturn(Inet4Address.getByName("localhost")).when(mMockServer).getInetAddress();
        assertTrue(mHostTest.compileClassPath().contains("ravenwood-runtime"));

        String expectedLdLibraryPath = String.join(java.io.File.pathSeparator, ldLibraryPath);
        assertEquals(expectedLdLibraryPath, mHostTest.compileLdLibraryPathInner(null));

        List<String> commandArgs = mHostTest.compileCommandArgs("", null);
        assertTrue(commandArgs.contains("-Dandroid.junit.runner=org.junit.runners.JUnit4"));
    }

    @Test
    public void testUploadReportArtifacts() throws Exception {
        File artifactsDir =
                FileUtil.createTempDir("isolatedhosttesttest-robolectric-screenshot-artifacts-dir");
        File pngFile = FileUtil.createTempFile("test", ".png", artifactsDir);
        File pbFile = FileUtil.createTempFile("test", ".pb", artifactsDir);
        mHostTest.uploadTestArtifacts(artifactsDir, mListener);
        // verify both files were uploaded using testLog
        verify(mListener, times(2)).testLog((String) Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void testRobolectricResourcesNegative() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("use-robolectric-resources", "false");
        doReturn(mMockTestDir).when(mMockBuildInfo).getFile(BuildInfoFileKey.HOST_LINKED_DIR);
        doReturn(36000).when(mMockServer).getLocalPort();
        doReturn(Inet4Address.getByName("localhost")).when(mMockServer).getInetAddress();

        List<String> commandArgs = mHostTest.compileCommandArgs("", null);
        assertFalse(commandArgs.contains("-Drobolectric.offline=true"));
        assertFalse(commandArgs.contains("-Drobolectric.logging=stdout"));
        assertFalse(commandArgs.contains("-Drobolectric.resourcesMode=binary"));
        assertFalse(
                commandArgs.stream().anyMatch(s -> s.contains("-Drobolectric.dependency.dir=")));
    }

    @Test
    public void testRavenwoodResourcesNegative() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("use-ravenwood-resources", "false");
        doReturn(mMockTestDir).when(mMockBuildInfo).getFile(BuildInfoFileKey.HOST_LINKED_DIR);
        doReturn(36000).when(mMockServer).getLocalPort();
        doReturn(Inet4Address.getByName("localhost")).when(mMockServer).getInetAddress();
        assertFalse(mHostTest.compileClassPath().contains("ravenwood-runtime"));

        List<String> commandArgs = mHostTest.compileCommandArgs("", null);
        assertFalse(commandArgs.contains("-Dandroid.junit.runner=org.junit.runners.JUnit4"));
    }

    @Test
    public void testCoverageArgsAreAdded_whenCoverageIsTurnedOn() throws Exception {
        CoverageOptions coverageOptions = new CoverageOptions();
        OptionSetter setter = new OptionSetter(coverageOptions);
        setter.setOptionValue("coverage", "true");
        setter.setOptionValue("jacocoagent-path", "path/to/jacocoagent.jar");
        IConfiguration config = new Configuration("config", "Test config");
        config.setCoverageOptions(coverageOptions);
        mHostTest.setConfiguration(config);
        doReturn(mMockTestDir).when(mMockBuildInfo).getFile(BuildInfoFileKey.HOST_LINKED_DIR);
        doReturn(36000).when(mMockServer).getLocalPort();
        doReturn(Inet4Address.getByName("localhost")).when(mMockServer).getInetAddress();

        List<String> commandArgs = mHostTest.compileCommandArgs("", null);

        String javaAgent =
                String.format(
                        "-javaagent:path/to/jacocoagent.jar=destfile=%s,"
                                + "inclnolocationclasses=true,"
                                + "exclclassloader=jdk.internal.reflect.DelegatingClassLoader",
                        mHostTest.getCoverageExecFile().getAbsolutePath());
        assertTrue(commandArgs.contains(javaAgent));
        FileUtil.deleteFile(mHostTest.getCoverageExecFile());
    }

    /**
     * TODO(murj) need to figure out a strategy with jdesprez on how to test the classpath
     * determination functionality.
     *
     * @throws Exception
     */
    @Test
    public void testRobolectricResourcesClasspathPositive() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("use-robolectric-resources", "true");
    }

    /**
     * TODO(murj) same as above
     *
     * @throws Exception
     */
    @Test
    public void testRobolectricResourcesClasspathNegative() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("use-robolectric-resources", "false");
    }

    private OptionSetter setUpSimpleMockJarTest(String jarName) throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        File jar = getJarResource("/" + jarName, mMockTestDir, jarName);
        doReturn(mMockTestDir).when(mMockBuildInfo).getFile(BuildInfoFileKey.HOST_LINKED_DIR);
        doReturn(mMockTestDir).when(mMockBuildInfo).getFile(BuildInfoFileKey.TESTDIR_IMAGE);
        setter.setOptionValue("jar", jar.getName());
        setter.setOptionValue("exclude-paths", "org/junit");
        setter.setOptionValue("exclude-paths", "junit");
        return setter;
    }

    @Test
    public void testSimpleFailingTestLifecycle() throws Exception {
        final String jarName = "SimpleFailingTest.jar";
        final String className = "com.android.tradefed.referencetests.SimpleFailingTest";
        setUpSimpleMockJarTest(jarName);
        TestInformation testInfo = TestInformation.newBuilder().build();
        TestDescription test = new TestDescription(className, "test2Plus2");

        mHostTest.run(testInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(test), Mockito.anyLong());
        verify(mListener).testFailed(Mockito.eq(test), (String) Mockito.any());
        verify(mListener)
                .testEnded(
                        Mockito.eq(test), Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mListener)
                .testLog((String) Mockito.any(), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testSimplePassingTestLifecycle() throws Exception {
        final String jarName = "SimplePassingTest.jar";
        final String className = "com.android.tradefed.referencetests.SimplePassingTest";
        setUpSimpleMockJarTest(jarName);
        TestInformation testInfo = TestInformation.newBuilder().build();
        TestDescription test = new TestDescription(className, "test2Plus2");

        mHostTest.run(testInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(test), Mockito.anyLong());
        verify(mListener)
                .testEnded(
                        Mockito.eq(test), Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mListener)
                .testLog((String) Mockito.any(), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testIncludeFilterByMethodLifecycle() throws Exception {
        final String jarName = "OnePassingOneFailingTest.jar";
        final String className = "com.android.tradefed.referencetests.OnePassingOneFailingTest";
        setUpSimpleMockJarTest(jarName);

        mHostTest.addIncludeFilter(className + "#test1Passing");
        TestInformation testInfo = TestInformation.newBuilder().build();
        TestDescription test = new TestDescription(className, "test1Passing");

        mHostTest.run(testInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(test), Mockito.anyLong());
        verify(mListener)
                .testEnded(
                        Mockito.eq(test), Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mListener)
                .testLog((String) Mockito.any(), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testIncludeFilterByClassLifecycle() throws Exception {
        final String jarName = "OnePassingOneFailingTest.jar";
        final String className = "com.android.tradefed.referencetests.OnePassingOneFailingTest";
        setUpSimpleMockJarTest(jarName);

        mHostTest.addIncludeFilter(className);
        TestInformation testInfo = TestInformation.newBuilder().build();
        TestDescription test1 = new TestDescription(className, "test1Passing");
        TestDescription test2 = new TestDescription(className, "test2Failing");

        mHostTest.run(testInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        verify(mListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        verify(mListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        verify(mListener).testFailed(Mockito.eq(test2), (String) Mockito.any());
        verify(mListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mListener)
                .testLog((String) Mockito.any(), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testIncludeFilterByModuleLifecycle() throws Exception {
        final String jarName = "OnePassingOneFailingTest.jar";
        final String className = "com.android.tradefed.referencetests.OnePassingOneFailingTest";
        setUpSimpleMockJarTest(jarName);

        mHostTest.addIncludeFilter("com.android.tradefed.referencetests");
        TestInformation testInfo = TestInformation.newBuilder().build();
        TestDescription test1 = new TestDescription(className, "test1Passing");
        TestDescription test2 = new TestDescription(className, "test2Failing");

        // One passing test followed by one failing test flow

        mHostTest.run(testInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        verify(mListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        verify(mListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        verify(mListener).testFailed(Mockito.eq(test2), (String) Mockito.any());
        verify(mListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mListener)
                .testLog((String) Mockito.any(), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testExcludeFilterByMethodLifecycle() throws Exception {
        final String jarName = "OnePassingOneFailingTest.jar";
        final String className = "com.android.tradefed.referencetests.OnePassingOneFailingTest";
        setUpSimpleMockJarTest(jarName);

        mHostTest.addExcludeFilter(className + "#test2Failing");
        TestInformation testInfo = TestInformation.newBuilder().build();
        TestDescription test = new TestDescription(className, "test1Passing");

        // One passing test flow

        mHostTest.run(testInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(test), Mockito.anyLong());
        verify(mListener)
                .testEnded(
                        Mockito.eq(test), Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mListener)
                .testLog((String) Mockito.any(), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testExcludeFilterByClassLifecycle() throws Exception {
        final String jarName = "OnePassingOneFailingTest.jar";
        final String className = "com.android.tradefed.referencetests.OnePassingOneFailingTest";
        setUpSimpleMockJarTest(jarName);

        mHostTest.addExcludeFilter(className);
        TestInformation testInfo = TestInformation.newBuilder().build();

        // Typical no tests found flow

        mHostTest.run(testInfo, mListener);

        verify(mListener)
                .testLog((String) Mockito.any(), Mockito.eq(LogDataType.TEXT), Mockito.any());
    }

    @Test
    public void testExcludeFilterByModuleLifecycle() throws Exception {
        final String jarName = "OnePassingOneFailingTest.jar";
        setUpSimpleMockJarTest(jarName);

        mHostTest.addExcludeFilter("com.android.tradefed.referencetests");
        TestInformation testInfo = TestInformation.newBuilder().build();

        // Typical no tests found flow

        mHostTest.run(testInfo, mListener);

        verify(mListener)
                .testLog((String) Mockito.any(), Mockito.eq(LogDataType.TEXT), Mockito.any());
    }

    @Test
    public void testConflictingFilterLifecycle() throws Exception {
        final String jarName = "OnePassingOneFailingTest.jar";
        final String className = "com.android.tradefed.referencetests.OnePassingOneFailingTest";
        setUpSimpleMockJarTest(jarName);

        mHostTest.addIncludeFilter(className + "#test1Passing");
        mHostTest.addIncludeFilter(className + "#test2Failing");
        mHostTest.addExcludeFilter(className + "#test2Failing");
        TestInformation testInfo = TestInformation.newBuilder().build();
        TestDescription test = new TestDescription(className, "test1Passing");

        // One passing test flow

        mHostTest.run(testInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(test), Mockito.anyLong());
        verify(mListener)
                .testEnded(
                        Mockito.eq(test), Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mListener)
                .testLog((String) Mockito.any(), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testConflictingFilterNoTestsLeftLifecycle() throws Exception {
        final String jarName = "OnePassingOneFailingTest.jar";
        final String className = "com.android.tradefed.referencetests.OnePassingOneFailingTest";
        setUpSimpleMockJarTest(jarName);

        mHostTest.addIncludeFilter(className + "#test2Failing");
        mHostTest.addExcludeFilter(className + "#test2Failing");
        TestInformation testInfo = TestInformation.newBuilder().build();

        mHostTest.run(testInfo, mListener);

        verify(mListener)
                .testLog((String) Mockito.any(), Mockito.eq(LogDataType.TEXT), Mockito.any());
    }

    @Test
    public void testParameterizedTest() throws Exception {
        final String jarName = "OnePassOneFailParamTest.jar";
        setUpSimpleMockJarTest(jarName);
        TestInformation testInfo = TestInformation.newBuilder().build();
        String className = "com.android.tradefed.referencetests.OnePassOneFailParamTest";

        TestDescription test1 = new TestDescription(className, "testBoolean[0]");
        TestDescription test2 = new TestDescription(className, "testBoolean[1]");

        mHostTest.run(testInfo, mListener);

        verify(mListener).testRunStarted(Mockito.eq(className), Mockito.eq(2));
        verify(mListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        verify(mListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        verify(mListener).testFailed(Mockito.eq(test2), (String) Mockito.any());
        verify(mListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mListener)
                .testLog((String) Mockito.any(), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testParameterizedTest_exclude() throws Exception {
        final String jarName = "OnePassOneFailParamTest.jar";
        setUpSimpleMockJarTest(jarName);
        TestInformation testInfo = TestInformation.newBuilder().build();
        String className = "com.android.tradefed.referencetests.OnePassOneFailParamTest";

        TestDescription test1 = new TestDescription(className, "testBoolean[0]");
        mHostTest.addExcludeFilter(className + "#testBoolean[1]");

        mHostTest.run(testInfo, mListener);

        verify(mListener).testRunStarted(Mockito.eq(className), Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        verify(mListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mListener)
                .testLog((String) Mockito.any(), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testCompileLdLibraryPath() throws Exception {
        setUpSimpleMockJarTest("SimplePassingTest.jar");
        List<String> paths = new ArrayList<>();

        makeDirAndAddToList(mMockTestDir, "lib", paths);
        makeDirAndAddToList(mMockTestDir, "lib64", paths);

        // Simulate $ANDROID_HOST_OUT
        File androidHostOut = new File(mMockTestDir, "ANDROID_HOST_OUT");
        androidHostOut.mkdirs();
        makeDirAndAddToList(androidHostOut, "lib", paths);
        makeDirAndAddToList(androidHostOut, "lib64", paths);

        final String expectedLdLibraryPath = String.join(java.io.File.pathSeparator, paths);
        final String ldLibraryPath =
                mHostTest.compileLdLibraryPathInner(androidHostOut.getAbsolutePath());
        assertEquals(expectedLdLibraryPath, ldLibraryPath);
    }

    @Test
    public void testIgnoreAndAssumptionFailure() throws Exception {
        final String jarName = "PassIgnoreAssumeTest.jar";
        final String className = "com.android.tradefed.referencetests.PassIgnoreAssumeTest";
        setUpSimpleMockJarTest(jarName);
        TestInformation testInfo = TestInformation.newBuilder().build();
        TestDescription testPass = new TestDescription(className, "testPass");
        TestDescription testIgnore = new TestDescription(className, "testDoesNotRun");
        TestDescription testAssumption = new TestDescription(className, "testAssume");

        mHostTest.run(testInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(3));
        verify(mListener).testStarted(Mockito.eq(testPass), Mockito.anyLong());
        verify(mListener)
                .testEnded(
                        Mockito.eq(testPass),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mListener).testStarted(Mockito.eq(testIgnore), Mockito.anyLong());
        verify(mListener).testIgnored(testIgnore);
        verify(mListener)
                .testEnded(
                        Mockito.eq(testIgnore),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mListener).testStarted(Mockito.eq(testAssumption), Mockito.anyLong());
        verify(mListener).testAssumptionFailure(Mockito.eq(testAssumption), (String) Mockito.any());
        verify(mListener)
                .testEnded(
                        Mockito.eq(testAssumption),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());

        verify(mListener)
                .testLog((String) Mockito.any(), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }
}
