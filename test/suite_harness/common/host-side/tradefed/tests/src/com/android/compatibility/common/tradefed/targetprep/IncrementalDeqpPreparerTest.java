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

package com.android.compatibility.common.tradefed.targetprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link IncrementalDeqpPreparer}. */
@RunWith(JUnit4.class)
public class IncrementalDeqpPreparerTest {

    private IncrementalDeqpPreparer mPreparer;
    private ITestDevice mMockDevice;

    @Before
    public void setUp() throws Exception {
        mPreparer = new IncrementalDeqpPreparer();
        mMockDevice = mock(ITestDevice.class);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testSkipPreparerWhenReportExists() throws Exception {
        File resultDir = FileUtil.createTempDir("result");
        try {
            IBuildInfo mMockBuildInfo = new BuildInfo();
            IInvocationContext mMockContext = new InvocationContext();
            mMockContext.addDeviceBuildInfo("build", mMockBuildInfo);
            mMockContext.addAllocatedDevice("device", mMockDevice);
            File deviceInfoDir = new File(resultDir, "device-info-files");
            deviceInfoDir.mkdir();
            File report = new File(deviceInfoDir, IncrementalDeqpPreparer.REPORT_NAME);
            report.createNewFile();
            CompatibilityBuildHelper mMockBuildHelper =
                    new CompatibilityBuildHelper(mMockBuildInfo) {
                        @Override
                        public File getResultDir() {
                            return resultDir;
                        }
                    };

            mPreparer.runIncrementalDeqp(mMockContext, mMockDevice, mMockBuildHelper);
        } finally {
            FileUtil.recursiveDelete(resultDir);
        }
    }

    @Test
    public void testParseDump() throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/testdata/perf-dump.txt");
        String content = StreamUtil.getStringFromStream(inputStream);
        Set<String> dependency = mPreparer.parseDump(content);
        Set<String> expect = new HashSet<>();
        expect.add("file_2");
        expect.add("file_3");
        assertEquals(dependency, expect);
    }

    @Test
    public void testGetTargetFileHash() throws IOException, TargetSetupError {
        Set<String> fileSet =
                new HashSet<>(
                        Arrays.asList(
                                "/system/deqp_dependency_file_a.so",
                                "/vendor/deqp_dependency_file_b.so",
                                "/vendor/file_not_exists.so"));
        // base_build_target-files.zip is a stripped down version of the target-files.zip generated
        // from the android build system, with a few added mocked target files for testing.
        InputStream zipStream =
                getClass().getResourceAsStream("/testdata/base_build_target-files.zip");
        File zipFile = FileUtil.createTempFile("targetFile", ".zip");
        try {
            FileUtil.writeToFile(zipStream, zipFile);
            Map<String, String> fileHashMap = mPreparer.getTargetFileHash(fileSet, zipFile);

            assertEquals(fileHashMap.size(), 2);
            assertEquals(
                    fileHashMap.get("/system/deqp_dependency_file_a.so"),
                    StreamUtil.calculateMd5(
                            new ByteArrayInputStream(
                                    "placeholder\nplaceholder\n"
                                            .getBytes(StandardCharsets.UTF_8))));
            assertEquals(
                    fileHashMap.get("/vendor/deqp_dependency_file_b.so"),
                    StreamUtil.calculateMd5(
                            new ByteArrayInputStream(
                                    ("placeholder\nplaceholder" + "\nplaceholder\n\n")
                                            .getBytes(StandardCharsets.UTF_8))));
        } finally {
            FileUtil.deleteFile(zipFile);
        }
    }

    @Test
    public void testCheckTestLogAllTestExecuted() throws IOException {
        InputStream testListStream = getClass().getResourceAsStream("/testdata/test_list.txt");
        InputStream logStream = getClass().getResourceAsStream("/testdata/log_1.qpa");
        String testListContent = StreamUtil.getStringFromStream(testListStream);
        String logContent = StreamUtil.getStringFromStream(logStream);

        assertTrue(mPreparer.checkTestLog(testListContent, logContent));
    }

    @Test
    public void testCheckTestLogTestCrashes() throws IOException {
        InputStream testListStream = getClass().getResourceAsStream("/testdata/test_list.txt");
        InputStream logStream = getClass().getResourceAsStream("/testdata/log_2.qpa");
        String testListContent = StreamUtil.getStringFromStream(testListStream);
        String logContent = StreamUtil.getStringFromStream(logStream);

        assertFalse(mPreparer.checkTestLog(testListContent, logContent));
    }

    @Test
    public void testGetTestFileName() {
        assertEquals(mPreparer.getTestFileName("vk-32"), "vk-incremental-deqp.txt");
        assertEquals(mPreparer.getTestFileName("gles-32"), "gles3-incremental-deqp.txt");
    }

    @Test
    public void testGetBinaryFileName() {
        assertEquals(mPreparer.getBinaryFileName("vk-32"), "deqp-binary32");
        assertEquals(mPreparer.getBinaryFileName("vk-64"), "deqp-binary64");
    }

    @Test
    public void getBuildFingerPrint() throws IOException, TargetSetupError {
        // base_build_target-files.zip is a stripped down version of the target-files.zip generated
        // from the android build system, with a few added mocked target files for testing.
        InputStream zipStream =
                getClass().getResourceAsStream("/testdata/base_build_target-files.zip");
        File zipFile = FileUtil.createTempFile("targetFile", ".zip");
        try {
            FileUtil.writeToFile(zipStream, zipFile);

            assertEquals(
                    mPreparer.getBuildFingerPrint(zipFile, mMockDevice),
                    "generic/aosp_cf_x86_64_phone/vsoc_x86_64:S/AOSP"
                            + ".MASTER/7363308:userdebug/test-keys");
        } finally {
            FileUtil.deleteFile(zipFile);
        }
    }
}
