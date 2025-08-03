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
package com.android.tradefed.build.content;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.BuildInfoKey.BuildInfoFileKey;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.content.ContentAnalysisContext.AnalysisMethod;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.util.FileUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/** Unit tests for {@link TestContentAnalyzer}. */
@RunWith(JUnit4.class)
public class TestContentAnalyzerTest {

    private TestInformation mTestInformation;
    private IBuildInfo mBuildInfo;
    private IInvocationContext mInvocationContext;

    @Before
    public void setUp() {
        mInvocationContext = new InvocationContext();
        mBuildInfo = new BuildInfo();
        mInvocationContext.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, mBuildInfo);
        mTestInformation =
                TestInformation.newBuilder().setInvocationContext(mInvocationContext).build();
    }

    @Test
    public void testAnalyzeTestsDir_withChange() throws Exception {
        File testsDir = FileUtil.createTempDir("analysis-unit-tests");
        new File(testsDir, "DATA/app/DeviceHealthChecks/").mkdirs();
        new File(testsDir, "DATA/app/DeviceHealthChecks/DeviceHealthChecks.apk").createNewFile();
        new File(testsDir, "DATA/app/PermissionUtils/").mkdirs();
        new File(testsDir, "DATA/app/PermissionUtils/PermissionUtils.apk").createNewFile();
        mBuildInfo.setFile(BuildInfoFileKey.TESTDIR_IMAGE, testsDir, "P8888");
        ContentInformation contentInfo =
                new ContentInformation(
                        generateBaseContent(), "6777", generateCurrentContent(), "P8888");
        try {
            ContentAnalysisContext analysisContext =
                    new ContentAnalysisContext(
                            "mydevice-tests-P8888.zip", contentInfo, AnalysisMethod.FILE);

            TestContentAnalyzer analyzer =
                    new TestContentAnalyzer(
                            mTestInformation,
                            false,
                            Arrays.asList(analysisContext),
                            new ArrayList<String>(),
                            new ArrayList<String>());
            ContentAnalysisResults results = analyzer.evaluate();
            assertNotNull(results);
            assertTrue(results.hasAnyTestsChange());
        } finally {
            contentInfo.clean();
            FileUtil.recursiveDelete(testsDir);
        }
    }

    @Test
    public void testAnalyzeTestsDir_noChange() throws Exception {
        File testsDir = FileUtil.createTempDir("analysis-unit-tests");
        new File(testsDir, "DATA/app/DeviceHealthChecks/").mkdirs();
        new File(testsDir, "DATA/app/DeviceHealthChecks/DeviceHealthChecks.apk").createNewFile();
        mBuildInfo.setFile(BuildInfoFileKey.TESTDIR_IMAGE, testsDir, "P8888");
        ContentInformation contentInfo =
                new ContentInformation(
                        generateBaseContent(), "6777", generateCurrentContent(), "P8888");
        try {
            ContentAnalysisContext analysisContext =
                    new ContentAnalysisContext(
                            "mydevice-tests-P8888.zip", contentInfo, AnalysisMethod.FILE);

            TestContentAnalyzer analyzer =
                    new TestContentAnalyzer(
                            mTestInformation,
                            false,
                            Arrays.asList(analysisContext),
                            new ArrayList<String>(),
                            new ArrayList<String>());
            ContentAnalysisResults results = analyzer.evaluate();
            assertNotNull(results);
            assertFalse(results.hasAnyTestsChange());
        } finally {
            contentInfo.clean();
            FileUtil.recursiveDelete(testsDir);
        }
    }

    @Test
    public void testAnalyzeXtsSuite_withChange() throws Exception {
        File testsDir = FileUtil.createTempDir("analysis-unit-tests");
        File rootDir = new File(testsDir, "android-cts");
        rootDir.mkdir();
        File testCases = new File(rootDir, "testcases");
        testCases.mkdirs();
        new File(testCases, "module1").mkdirs();
        new File(testCases, "module1/someapk.apk").createNewFile();
        new File(testCases, "module2").mkdirs();
        new File(testCases, "module2/otherfile.xml").createNewFile();
        mBuildInfo.setFile(BuildInfoFileKey.ROOT_DIRECTORY, rootDir, "P8888");
        ContentInformation contentInfo =
                new ContentInformation(
                        generateBaseContent(), "6777", generateCurrentContent(), "P8888");
        try {
            ContentAnalysisContext analysisContext =
                    new ContentAnalysisContext(
                            "android-cts.zip", contentInfo, AnalysisMethod.MODULE_XTS);

            TestContentAnalyzer analyzer =
                    new TestContentAnalyzer(
                            mTestInformation,
                            false,
                            Arrays.asList(analysisContext),
                            new ArrayList<String>(),
                            new ArrayList<String>());
            ContentAnalysisResults results = analyzer.evaluate();
            assertNotNull(results);
            assertTrue(results.hasAnyTestsChange());
        } finally {
            contentInfo.clean();
            FileUtil.recursiveDelete(testsDir);
        }
    }

    @Test
    public void testAnalyzeXtsSuite_withDiscovery() throws Exception {
        File testsDir = FileUtil.createTempDir("analysis-unit-tests");
        File rootDir = new File(testsDir, "android-cts");
        rootDir.mkdir();
        File testCases = new File(rootDir, "testcases");
        testCases.mkdirs();
        new File(testCases, "module1").mkdirs();
        new File(testCases, "module1/someapk.apk").createNewFile();
        new File(testCases, "module2").mkdirs();
        new File(testCases, "module2/otherfile.xml").createNewFile();
        mBuildInfo.setFile(BuildInfoFileKey.ROOT_DIRECTORY, rootDir, "P8888");
        ContentInformation contentInfo =
                new ContentInformation(
                        generateBaseContent(), "6777", generateCurrentContent(), "P8888");
        try {
            ContentAnalysisContext analysisContext =
                    new ContentAnalysisContext(
                            "android-cts.zip", contentInfo, AnalysisMethod.MODULE_XTS);

            TestContentAnalyzer analyzer =
                    new TestContentAnalyzer(
                            mTestInformation,
                            false,
                            Arrays.asList(analysisContext),
                            Arrays.asList("module2"),
                            new ArrayList<String>());
            ContentAnalysisResults results = analyzer.evaluate();
            assertNotNull(results);
            // Only module2 is considered and didn't change
            assertFalse(results.hasAnyTestsChange());
        } finally {
            contentInfo.clean();
            FileUtil.recursiveDelete(testsDir);
        }
    }

    @Test
    public void testAnalyzeXtsSuite_noChange() throws Exception {
        File testsDir = FileUtil.createTempDir("analysis-unit-tests");
        File rootDir = new File(testsDir, "android-cts");
        rootDir.mkdir();
        File testCases = new File(rootDir, "testcases");
        testCases.mkdirs();
        new File(testCases, "module2").mkdirs();
        new File(testCases, "module2/otherfile.xml").createNewFile();
        mBuildInfo.setFile(BuildInfoFileKey.ROOT_DIRECTORY, rootDir, "P8888");
        ContentInformation contentInfo =
                new ContentInformation(
                        generateBaseContent(), "6777", generateCurrentContent(), "P8888");
        try {
            ContentAnalysisContext analysisContext =
                    new ContentAnalysisContext(
                            "android-cts.zip", contentInfo, AnalysisMethod.MODULE_XTS);

            TestContentAnalyzer analyzer =
                    new TestContentAnalyzer(
                            mTestInformation,
                            false,
                            Arrays.asList(analysisContext),
                            new ArrayList<String>(),
                            new ArrayList<String>());
            ContentAnalysisResults results = analyzer.evaluate();
            assertNotNull(results);
            assertFalse(results.hasAnyTestsChange());
        } finally {
            contentInfo.clean();
            FileUtil.recursiveDelete(testsDir);
        }
    }

    private File generateBaseContent() throws IOException {
        File content = FileUtil.createTempFile("artifacts-details-test", ".json");
        String baseContent =
                "[\n"
                    + "  {\n"
                    + "  \"artifact\": \"android-cts.zip\",\n"
                    + "  \"details\": [\n"
                    + "      {\n"
                    + "        \"digest\":"
                    + " \"acc469f0e5461328f89bd3afb3cfac52b40e35481d90a9899cfcdeb3c8eac627\",\n"
                    + "        \"path\": \"android-cts/testcases/module1/someapk.apk\",\n"
                    + "        \"size\": 8542\n"
                    + "      },\n"
                    + "      {\n"
                    + "        \"digest\":"
                    + " \"b69ad7f80ed55963c5782bee548e19b167406a03d5ae9204031f2ca7ff8b6304\",\n"
                    + "        \"path\": \"android-cts/testcases/module2/otherfile.xml\",\n"
                    + "        \"size\": 762\n"
                    + "      }\n"
                    + "    ]\n"
                    + "  },\n"
                    + "  {\n"
                    + "  \"artifact\": \"mydevice-tests-6777.zip\",\n"
                    + "  \"details\": [\n"
                    + "      {\n"
                    + "        \"digest\":"
                    + " \"acc469f0e5461328f89bd3afb3cfac52b40e35481d90a9899cfcdeb3c8eac627\",\n"
                    + "        \"path\": \"DATA/app/DeviceHealthChecks/DeviceHealthChecks.apk\",\n"
                    + "        \"size\": 8542\n"
                    + "      },\n"
                    + "      {\n"
                    + "        \"digest\":"
                    + " \"b69ad7f80ed55963c5782bee548e19b167406a03d5ae9204031f2ca7ff8b6304\",\n"
                    + "        \"path\": \"DATA/app/PermissionUtils/PermissionUtils.apk\",\n"
                    + "        \"size\": 762\n"
                    + "      }\n"
                    + "    ]\n"
                    + "  }\n"
                    + "]";
        FileUtil.writeToFile(baseContent, content);
        return content;
    }

    private File generateCurrentContent() throws IOException {
        File content = FileUtil.createTempFile("artifacts-details-test", ".json");
        String currentContent =
                "[\n"
                    + "  {\n"
                    + "  \"artifact\": \"android-cts.zip\",\n"
                    + "  \"details\": [\n"
                    + "      {\n"
                    + "        \"digest\": \"8888\",\n"
                    + "        \"path\": \"android-cts/testcases/module1/someapk.apk\",\n"
                    + "        \"size\": 8542\n"
                    + "      },\n"
                    + "      {\n"
                    + "        \"digest\":"
                    + " \"b69ad7f80ed55963c5782bee548e19b167406a03d5ae9204031f2ca7ff8b6304\",\n"
                    + "        \"path\": \"android-cts/testcases/module2/otherfile.xml\",\n"
                    + "        \"size\": 762\n"
                    + "      }\n"
                    + "    ]\n"
                    + "  },\n"
                    + "  {\n"
                    + "  \"artifact\": \"mydevice-tests-P8888.zip\",\n"
                    + "  \"details\": [\n"
                    + "      {\n"
                    + "        \"digest\":"
                    + " \"acc469f0e5461328f89bd3afb3cfac52b40e35481d90a9899cfcdeb3c8eac627\",\n"
                    + "        \"path\": \"DATA/app/DeviceHealthChecks/DeviceHealthChecks.apk\",\n"
                    + "        \"size\": 8542\n"
                    + "      },\n"
                    + "      {\n"
                    + "        \"digest\":"
                    + " \"b69ad7f80ed55963c5782bee54aaaaaaaaaaaaaaaaa31f2ca7ff8b6304\",\n"
                    + "        \"path\": \"DATA/app/PermissionUtils/PermissionUtils.apk\",\n"
                    + "        \"size\": 762\n"
                    + "      }\n"
                    + "    ]\n"
                    + "  }\n"
                    + "]";
        FileUtil.writeToFile(currentContent, content);
        return content;
    }
}
