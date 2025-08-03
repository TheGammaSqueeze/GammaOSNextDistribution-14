/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.util.testmapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;

import com.google.common.collect.Sets;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link TestMapping}. */
@RunWith(JUnit4.class)
public class TestMappingTest {

    private static final String TEST_DATA_DIR = "testdata";
    private static final String TEST_MAPPING = "TEST_MAPPING";
    private static final String TEST_MAPPINGS_ZIP = "test_mappings.zip";
    private static final String DISABLED_PRESUBMIT_TESTS = "disabled-presubmit-tests";
    private static final Set<String> NO_MATCHED_PATTERNS = new HashSet<>();
    private static final boolean IGNORE_IMPORTS = true;
    private TestMapping mTestMapping;

    @Before
    public void setUp() throws Exception {
        mTestMapping = new TestMapping();
    }

    /** Test for {@link TestMapping#getTests()} implementation. */
    @Test
    public void testparseTestMapping() throws Exception {
        File tempDir = null;
        File testMappingFile = null;

        try {
            tempDir = FileUtil.createTempDir("test_mapping");
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            File testMappingRootDir = FileUtil.createTempDir("subdir", tempDir);
            String rootDirName = testMappingRootDir.getName();
            testMappingFile =
                    FileUtil.saveResourceFile(resourceStream, testMappingRootDir, TEST_MAPPING);
            Set<TestInfo> tests =
                    mTestMapping.getTests(
                            mTestMapping.getTestCollection(
                                    testMappingFile.toPath(),
                                    Paths.get(tempDir.getAbsolutePath()),
                                    NO_MATCHED_PATTERNS),
                            "presubmit",
                            null,
                            true,
                            null,
                            new HashSet<String>());
            assertEquals(1, tests.size());
            Set<String> names = new HashSet<String>();
            for (TestInfo test : tests) {
                names.add(test.getName());
            }
            assertTrue(names.contains("test1"));

            tests =
                    mTestMapping.getTests(
                            mTestMapping.getTestCollection(
                                    testMappingFile.toPath(),
                                    Paths.get(tempDir.getAbsolutePath()),
                                    NO_MATCHED_PATTERNS),
                            "presubmit",
                            null,
                            false,
                            null,
                            new HashSet<String>());
            assertEquals(1, tests.size());
            names = new HashSet<String>();
            for (TestInfo test : tests) {
                names.add(test.getName());
            }
            assertTrue(names.contains("suite/stub1"));

            tests =
                    mTestMapping.getTests(
                            mTestMapping.getTestCollection(
                                    testMappingFile.toPath(),
                                    Paths.get(tempDir.getAbsolutePath()),
                                    NO_MATCHED_PATTERNS),
                            "postsubmit",
                            null,
                            false,
                            null,
                            new HashSet<String>());
            assertEquals(2, tests.size());
            TestOption testOption =
                    new TestOption(
                            "instrumentation-arg",
                            "annotation=android.platform.test.annotations.Presubmit");
            names = new HashSet<String>();
            Set<TestOption> testOptions = new HashSet<TestOption>();
            for (TestInfo test : tests) {
                names.add(test.getName());
                testOptions.addAll(test.getOptions());
            }
            assertTrue(names.contains("test2"));
            assertTrue(names.contains("instrument"));
            assertTrue(testOptions.contains(testOption));

            tests =
                    mTestMapping.getTests(
                            mTestMapping.getTestCollection(
                                    testMappingFile.toPath(),
                                    Paths.get(tempDir.getAbsolutePath()),
                                    NO_MATCHED_PATTERNS),
                            "othertype",
                            null,
                            false,
                            null,
                            new HashSet<String>());
            assertEquals(1, tests.size());
            names = new HashSet<String>();
            testOptions = new HashSet<TestOption>();
            Set<String> sources = new HashSet<String>();
            for (TestInfo test : tests) {
                names.add(test.getName());
                testOptions.addAll(test.getOptions());
                sources.addAll(test.getSources());
            }
            assertTrue(names.contains("test3"));
            assertEquals(1, testOptions.size());
            assertTrue(sources.contains(rootDirName));
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /** Test for {@link TestMapping#getTests()} throw exception for malformatted json file. */
    @Test(expected = RuntimeException.class)
    public void testparseTestMapping_BadJson() throws Exception {
        File tempDir = null;

        try {
            tempDir = FileUtil.createTempDir("test_mapping");
            File testMappingFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPING).toFile();
            FileUtil.writeToFile("bad format json file", testMappingFile);
            mTestMapping.getTests(
                    mTestMapping.getTestCollection(
                            testMappingFile.toPath(),
                            Paths.get(tempDir.getAbsolutePath()),
                            NO_MATCHED_PATTERNS),
                    "presubmit",
                    null,
                    false,
                    null,
                    new HashSet<String>());
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /** Test for {@link TestMapping#getTests()} for loading tests from test_mappings.zip. */
    @Test
    public void testGetTests() throws Exception {
        File tempDir = null;
        IBuildInfo mockBuildInfo = mock(IBuildInfo.class);
        try {
            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, tempDir, DISABLED_PRESUBMIT_TESTS);
            List<File> filesToZip =
                    Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));

            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);

            // Ensure the static variable doesn't have any relative path configured.
            Set<TestInfo> tests =
                    mTestMapping.getTests(
                            mockBuildInfo, "presubmit", false, null, new HashSet<String>());
            assertEquals(0, tests.size());

            tests =
                    mTestMapping.getTests(
                            mockBuildInfo, "presubmit", true, null, new HashSet<String>());
            assertEquals(2, tests.size());
            Set<String> names = new HashSet<String>();
            for (TestInfo test : tests) {
                names.add(test.getName());
                if (test.getName().equals("test1")) {
                    assertTrue(test.getHostOnly());
                } else {
                    assertFalse(test.getHostOnly());
                }
            }
            assertTrue(!names.contains("suite/stub1"));
            assertTrue(names.contains("test1"));
            verify(mockBuildInfo, times(2)).getFile(TEST_MAPPINGS_ZIP);
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMapping#getTests()} for loading tests from test_mappings.zip for matching
     * keywords.
     */
    @Test
    public void testGetTests_matchKeywords() throws Exception {
        File tempDir = null;
        IBuildInfo mockBuildInfo = mock(IBuildInfo.class);
        try {
            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, tempDir, DISABLED_PRESUBMIT_TESTS);
            List<File> filesToZip =
                    Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));

            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);

            Set<TestInfo> tests =
                    mTestMapping.getTests(
                            mockBuildInfo,
                            "presubmit",
                            false,
                            Sets.newHashSet("key_1"),
                            new HashSet<String>());
            assertEquals(1, tests.size());
            assertEquals("suite/stub2", tests.iterator().next().getName());
            verify(mockBuildInfo, times(1)).getFile(TEST_MAPPINGS_ZIP);
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMapping#getTests()} for loading tests from test_mappings.zip for matching
     * keywords.
     */
    @Test
    public void testGetTests_withIgnoreKeywords() throws Exception {
        File tempDir = null;
        IBuildInfo mockBuildInfo = mock(IBuildInfo.class);
        try {
            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);
            List<File> filesToZip = Arrays.asList(srcDir);

            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);

            Set<TestInfo> tests =
                    mTestMapping.getTests(
                            mockBuildInfo,
                            "presubmit",
                            false,
                            new HashSet<String>(),
                            Sets.newHashSet("key_1"));
            assertEquals(3, tests.size());
            verify(mockBuildInfo, times(1)).getFile(TEST_MAPPINGS_ZIP);
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMapping#getAllTestMappingPaths(Path)} to get TEST_MAPPING files from
     * child directory.
     */
    @Test
    public void testGetAllTestMappingPaths_FromChildDirectory() throws Exception {
        File tempDir = null;
        try {
            tempDir = FileUtil.createTempDir("test_mapping");
            Path testMappingsRootPath = Paths.get(tempDir.getAbsolutePath());
            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            List<String> testMappingRelativePaths = new ArrayList<>();
            Path relPath = testMappingsRootPath.relativize(Paths.get(subDir.getAbsolutePath()));
            testMappingRelativePaths.add(relPath.toString());
            TestMapping testMapping = new TestMapping(testMappingRelativePaths, true);
            Set<Path> paths = testMapping.getAllTestMappingPaths(testMappingsRootPath);
            assertEquals(2, paths.size());
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMapping#getAllTestMappingPaths(Path)} to get TEST_MAPPING files from
     * parent directory.
     */
    @Test
    public void testGetAllTestMappingPaths_FromParentDirectory() throws Exception {
        File tempDir = null;
        try {
            tempDir = FileUtil.createTempDir("test_mapping");
            Path testMappingsRootPath = Paths.get(tempDir.getAbsolutePath());
            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            List<String> testMappingRelativePaths = new ArrayList<>();
            Path relPath = testMappingsRootPath.relativize(Paths.get(srcDir.getAbsolutePath()));
            testMappingRelativePaths.add(relPath.toString());
            TestMapping testMapping = new TestMapping(testMappingRelativePaths, true);
            Set<Path> paths = testMapping.getAllTestMappingPaths(testMappingsRootPath);
            assertEquals(1, paths.size());
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMapping#getAllTestMappingPaths(Path)} to fail when no TEST_MAPPING files
     * found.
     */
    @Test(expected = RuntimeException.class)
    public void testGetAllTestMappingPaths_NoFilesFound() throws Exception {
        File tempDir = null;
        try {
            tempDir = FileUtil.createTempDir("test_mapping");
            Path testMappingsRootPath = Paths.get(tempDir.getAbsolutePath());
            File srcDir = FileUtil.createTempDir("src", tempDir);

            List<String> testMappingRelativePaths = new ArrayList<>();
            Path relPath = testMappingsRootPath.relativize(Paths.get(srcDir.getAbsolutePath()));
            testMappingRelativePaths.add(relPath.toString());
            TestMapping testMapping = new TestMapping(testMappingRelativePaths, true);
            // No TEST_MAPPING files should be found according to the srcDir, getAllTestMappingPaths
            // method shall raise RuntimeException.
            testMapping.getAllTestMappingPaths(testMappingsRootPath);
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestInfo#merge()} for merging two TestInfo objects to fail when module names
     * are different.
     */
    @Test(expected = RuntimeException.class)
    public void testMergeFailByName() throws Exception {
        TestInfo test1 = new TestInfo("test1", "folder1", false);
        TestInfo test2 = new TestInfo("test2", "folder1", false);
        test1.merge(test2);
    }

    /**
     * Test for {@link TestInfo#merge()} for merging two TestInfo objects to fail when device
     * requirements are different.
     */
    @Test(expected = RuntimeException.class)
    public void testMergeFailByHostOnly() throws Exception {
        TestInfo test1 = new TestInfo("test1", "folder1", false);
        TestInfo test2 = new TestInfo("test2", "folder1", true);
        test1.merge(test2);
    }

    /**
     * Test for {@link TestInfo#merge()} for merging two TestInfo objects, one of which has no
     * option.
     */
    @Test
    public void testMergeSuccess() throws Exception {
        // Check that the test without any option should be the merge result.
        TestInfo test1 = new TestInfo("test1", "folder1", false);
        TestInfo test2 = new TestInfo("test1", "folder1", false);
        test2.addOption(new TestOption("include-filter", "value"));
        test1.merge(test2);
        assertTrue(test1.getOptions().isEmpty());
        assertFalse(test1.getHostOnly());

        test1 = new TestInfo("test1", "folder1", false);
        test2 = new TestInfo("test1", "folder1", false);
        test1.addOption(new TestOption("include-filter", "value"));
        test1.merge(test2);
        assertTrue(test1.getOptions().isEmpty());
        assertFalse(test1.getHostOnly());

        test1 = new TestInfo("test1", "folder1", true);
        test2 = new TestInfo("test1", "folder1", true);
        test1.addOption(new TestOption("include-filter", "value"));
        test1.merge(test2);
        assertTrue(test1.getOptions().isEmpty());
        assertTrue(test1.getHostOnly());
    }

    /**
     * Test for {@link TestInfo#merge()} for merging two TestInfo objects, each has a different
     * include-filter.
     */
    @Test
    public void testMergeSuccess_2Filters() throws Exception {
        // Check that the test without any option should be the merge result.
        TestInfo test1 = new TestInfo("test1", "folder1", false);
        TestInfo test2 = new TestInfo("test1", "folder2", false);
        TestOption option1 = new TestOption("include-filter", "value1");
        test1.addOption(option1);
        TestOption option2 = new TestOption("include-filter", "value2");
        test2.addOption(option2);
        test1.merge(test2);
        assertEquals(2, test1.getOptions().size());
        assertTrue(new HashSet<TestOption>(test1.getOptions()).contains(option1));
        assertTrue(new HashSet<TestOption>(test1.getOptions()).contains(option2));
        assertEquals(2, test1.getSources().size());
        assertTrue(test1.getSources().contains("folder1"));
        assertTrue(test1.getSources().contains("folder2"));
    }

    /**
     * Test for {@link TestInfo#merge()} for merging two TestInfo objects, each has mixed
     * include-filter and exclude-filter.
     */
    @Test
    public void testMergeSuccess_multiFilters() throws Exception {
        // Check that the test without any option should be the merge result.
        TestInfo test1 = new TestInfo("test1", "folder1", false);
        TestInfo test2 = new TestInfo("test1", "folder2", false);
        TestOption inclusiveOption1 = new TestOption("include-filter", "value1");
        test1.addOption(inclusiveOption1);
        TestOption exclusiveOption1 = new TestOption("exclude-filter", "exclude-value1");
        test1.addOption(exclusiveOption1);
        TestOption exclusiveOption2 = new TestOption("exclude-filter", "exclude-value2");
        test1.addOption(exclusiveOption2);
        TestOption otherOption1 = new TestOption("somefilter", "");
        test1.addOption(otherOption1);

        TestOption inclusiveOption2 = new TestOption("include-filter", "value2");
        test2.addOption(inclusiveOption2);
        // Same exclusive option as in test1.
        test2.addOption(exclusiveOption1);
        TestOption exclusiveOption3 = new TestOption("exclude-filter", "exclude-value1");
        test2.addOption(exclusiveOption3);
        TestOption otherOption2 = new TestOption("somefilter2", "value2");
        test2.addOption(otherOption2);

        test1.merge(test2);
        assertEquals(5, test1.getOptions().size());
        Set<TestOption> mergedOptions = new HashSet<TestOption>(test1.getOptions());
        // Options from test1.
        assertTrue(mergedOptions.contains(inclusiveOption1));
        assertTrue(mergedOptions.contains(otherOption1));
        // Shared exclusive option between test1 and test2.
        assertTrue(mergedOptions.contains(exclusiveOption1));
        // Options from test2.
        assertTrue(mergedOptions.contains(inclusiveOption2));
        assertTrue(mergedOptions.contains(otherOption2));
        // Both folders are in sources
        assertEquals(2, test1.getSources().size());
        assertTrue(test1.getSources().contains("folder1"));
        assertTrue(test1.getSources().contains("folder2"));
    }

    /**
     * Test for {@link TestInfo#merge()} for merging two TestInfo objects, each has a different
     * include-filter and include-annotation option.
     */
    @Test
    public void testMergeSuccess_MultiFilters_dropIncludeAnnotation() throws Exception {
        // Check that the test without all options except include-annotation option should be the
        // merge result.
        TestInfo test1 = new TestInfo("test1", "folder1", false);
        TestInfo test2 = new TestInfo("test1", "folder1", false);
        TestOption option1 = new TestOption("include-filter", "value1");
        test1.addOption(option1);
        TestOption optionIncludeAnnotation =
                new TestOption("include-annotation", "androidx.test.filters.FlakyTest");
        test1.addOption(optionIncludeAnnotation);
        TestOption option2 = new TestOption("include-filter", "value2");
        test2.addOption(option2);
        test1.merge(test2);
        assertEquals(2, test1.getOptions().size());
        assertTrue(new HashSet<TestOption>(test1.getOptions()).contains(option1));
        assertTrue(new HashSet<TestOption>(test1.getOptions()).contains(option2));
    }

    /**
     * Test for {@link TestInfo#merge()} for merging two TestInfo objects, each has a different
     * include-filter and exclude-annotation option.
     */
    @Test
    public void testMergeSuccess_MultiFilters_keepExcludeAnnotation() throws Exception {
        // Check that the test without all options including exclude-annotation option should be the
        // merge result.
        TestInfo test1 = new TestInfo("test1", "folder1", false);
        TestInfo test2 = new TestInfo("test1", "folder1", false);
        TestOption option1 = new TestOption("include-filter", "value1");
        test1.addOption(option1);
        TestOption optionExcludeAnnotation1 =
                new TestOption("exclude-annotation", "androidx.test.filters.FlakyTest");
        test1.addOption(optionExcludeAnnotation1);
        TestOption optionExcludeAnnotation2 =
                new TestOption("exclude-annotation", "another-annotation");
        test1.addOption(optionExcludeAnnotation2);
        TestOption option2 = new TestOption("include-filter", "value2");
        test2.addOption(option2);
        TestOption optionExcludeAnnotation3 =
                new TestOption("exclude-annotation", "androidx.test.filters.FlakyTest");
        test1.addOption(optionExcludeAnnotation3);
        test1.merge(test2);
        assertEquals(4, test1.getOptions().size());
        assertTrue(new HashSet<TestOption>(test1.getOptions()).contains(option1));
        assertTrue(new HashSet<TestOption>(test1.getOptions()).contains(optionExcludeAnnotation1));
        assertTrue(new HashSet<TestOption>(test1.getOptions()).contains(optionExcludeAnnotation2));
        assertTrue(new HashSet<TestOption>(test1.getOptions()).contains(option2));
    }

    /**
     * Test for {@link TestMapping#getAllTests()} for loading tests from test_mappings directory.
     */
    @Test
    public void testGetAllTests() throws Exception {
        File tempDir = null;
        try {
            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, tempDir, DISABLED_PRESUBMIT_TESTS);
            Map<String, Set<TestInfo>> allTests = mTestMapping.getAllTests(tempDir);
            Set<TestInfo> tests = allTests.get("presubmit");
            assertEquals(6, tests.size());

            tests = allTests.get("postsubmit");
            assertEquals(4, tests.size());

            tests = allTests.get("othertype");
            assertEquals(1, tests.size());
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /** Test for {@link TestMapping#extractTestMappingsZip()} for extracting test mappings zip. */
    @Test
    public void testExtractTestMappingsZip() throws Exception {
        File tempDir = null;
        File extractedFile = null;
        try {
            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, tempDir, DISABLED_PRESUBMIT_TESTS);
            List<File> filesToZip =
                    Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));

            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            extractedFile = TestMapping.extractTestMappingsZip(zipFile);
            Map<String, Set<TestInfo>> allTests = mTestMapping.getAllTests(tempDir);
            Set<TestInfo> tests = allTests.get("presubmit");
            assertEquals(6, tests.size());

            tests = allTests.get("postsubmit");
            assertEquals(4, tests.size());

            tests = allTests.get("othertype");
            assertEquals(1, tests.size());
        } finally {
            FileUtil.recursiveDelete(tempDir);
            FileUtil.recursiveDelete(extractedFile);
        }
    }

    /** Test for {@link TestMapping#getDisabledTests()} for getting disabled tests. */
    @Test
    public void testGetDisabledTests() throws Exception {
        File tempDir = null;
        try {
            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, tempDir, DISABLED_PRESUBMIT_TESTS);
            Path tempDirPath = Paths.get(tempDir.getAbsolutePath());
            Set<String> disabledTests = mTestMapping.getDisabledTests(tempDirPath, "presubmit");
            assertEquals(2, disabledTests.size());

            disabledTests = mTestMapping.getDisabledTests(tempDirPath, "postsubmit");
            assertEquals(0, disabledTests.size());

            disabledTests = mTestMapping.getDisabledTests(tempDirPath, "othertype");
            assertEquals(0, disabledTests.size());
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /** Test for {@link TestMapping#removeComments()} for removing comments in TEST_MAPPING file. */
    @Test
    public void testRemoveComments() throws Exception {
        String jsonString = getJsonStringByName("test_mapping_with_comments1");
        String goldenString = getJsonStringByName("test_mapping_golden1");
        assertEquals(mTestMapping.removeComments(jsonString), goldenString);
    }

    /** Test for {@link TestMapping#removeComments()} for removing comments in TEST_MAPPING file. */
    @Test
    public void testRemoveComments2() throws Exception {
        String jsonString = getJsonStringByName("test_mapping_with_comments2");
        String goldenString = getJsonStringByName("test_mapping_golden2");
        assertEquals(mTestMapping.removeComments(jsonString), goldenString);
    }

    /**
     * Test for {@link TestMapping#listTestMappingFiles()} for list TEST_MAPPING files, include
     * import TEST_MAPPING files.
     */
    @Test
    public void testIncludeImports() throws Exception {
        // Test directory structure:
        // ├── disabled-presubmit-tests
        // ├── path1
        // │   └── TEST_MAPPING
        // ├── path2
        // │   ├── path3
        // │   │  └── TEST_MAPPING
        // │   └── TEST_MAPPING
        // └── test_mappings.zip
        File tempDir = null;
        IBuildInfo mockBuildInfo = mock(IBuildInfo.class);
        try {
            tempDir = FileUtil.createTempDir("test_mapping");
            File path1 = new File(tempDir.getAbsolutePath() + "/path1");
            path1.mkdir();
            String srcFile =
                    File.separator + TEST_DATA_DIR + File.separator + "test_mapping_with_import_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, path1, TEST_MAPPING);

            File path2 = new File(tempDir.getAbsolutePath() + "/path2");
            path2.mkdir();
            srcFile =
                    File.separator + TEST_DATA_DIR + File.separator + "test_mapping_with_import_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, path2, TEST_MAPPING);

            File path3 = new File(path2.getAbsolutePath() + "/path3");
            path3.mkdir();
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, path3, TEST_MAPPING);

            srcFile = File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, tempDir, DISABLED_PRESUBMIT_TESTS);
            List<File> filesToZip =
                    Arrays.asList(path1, path2, new File(tempDir, DISABLED_PRESUBMIT_TESTS));

            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);

            Set<String> names = new HashSet<String>();
            TestMapping testMapping = new TestMapping(new ArrayList<>(), false);
            Set<TestInfo> testInfos =
                    testMapping.getTests(
                            testMapping.getTestCollection(
                                    path3.toPath().resolve(TEST_MAPPING),
                                    tempDir.toPath(),
                                    NO_MATCHED_PATTERNS),
                            "presubmit",
                            null,
                            true,
                            null,
                            new HashSet<String>());
            assertEquals(3, testInfos.size());
            for (TestInfo test : testInfos) {
                names.add(test.getName());
                if (test.getName().equals("test1")) {
                    assertEquals(2, test.getImportPaths().size());
                    assertTrue(test.getImportPaths().contains("path1"));
                    assertTrue(test.getImportPaths().contains("path2"));
                }
            }
            assertTrue(names.contains("import-test1"));
            assertTrue(names.contains("import-test2"));
            assertTrue(names.contains("test1"));

            testInfos =
                    testMapping.getTests(
                            testMapping.getTestCollection(
                                    path3.toPath().resolve(TEST_MAPPING),
                                    tempDir.toPath(),
                                    NO_MATCHED_PATTERNS),
                            "presubmit",
                            null,
                            false,
                            null,
                            new HashSet<String>());
            names.clear();
            for (TestInfo test : testInfos) {
                names.add(test.getName());
            }
            assertTrue(names.contains("suite/stub1"));
            assertTrue(names.contains("import-test3"));
            assertEquals(2, testInfos.size());
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMapping#listTestMappingFiles()} for list TEST_MAPPING files, ignore
     * import TEST_MAPPING files.
     */
    @Test
    public void testExcludeImports() throws Exception {
        // Test directory structure:
        // ├── disabled-presubmit-tests
        // ├── path1
        // │   └── TEST_MAPPING
        // ├── path2
        // │   ├── path3
        // │   │  └── TEST_MAPPING
        // │   └── TEST_MAPPING
        // └── test_mappings.zip
        File tempDir = null;
        IBuildInfo mockBuildInfo = mock(IBuildInfo.class);
        try {
            tempDir = FileUtil.createTempDir("test_mapping");
            File path1 = new File(tempDir.getAbsolutePath() + "/path1");
            path1.mkdir();
            String srcFile =
                    File.separator + TEST_DATA_DIR + File.separator + "test_mapping_with_import_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, path1, TEST_MAPPING);

            File path2 = new File(tempDir.getAbsolutePath() + "/path2");
            path2.mkdir();
            srcFile =
                    File.separator + TEST_DATA_DIR + File.separator + "test_mapping_with_import_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, path2, TEST_MAPPING);

            File path3 = new File(path2.getAbsolutePath() + "/path3");
            path3.mkdir();
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, path3, TEST_MAPPING);

            srcFile = File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, tempDir, DISABLED_PRESUBMIT_TESTS);
            List<File> filesToZip =
                    Arrays.asList(path1, path2, new File(tempDir, DISABLED_PRESUBMIT_TESTS));

            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);

            Set<String> names = new HashSet<String>();
            Set<TestInfo> testInfos =
                    mTestMapping.getTests(
                            mTestMapping.getTestCollection(
                                    path3.toPath().resolve(TEST_MAPPING),
                                    tempDir.toPath(),
                                    NO_MATCHED_PATTERNS),
                            "presubmit",
                            null,
                            true,
                            null,
                            new HashSet<String>());
            assertEquals(1, testInfos.size());
            for (TestInfo test : testInfos) {
                names.add(test.getName());
            }

            assertFalse(names.contains("import-test1"));
            assertFalse(names.contains("import-test2"));
            assertTrue(names.contains("test1"));
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMapping#getTestMappingSources()} for collecting paths of TEST_MAPPING
     * files
     */
    @Test
    public void testGetTestMappingSources() throws Exception {
        // Test directory structure:
        // ├── disabled-presubmit-tests
        // ├── src1
        // |   ├── sub_dir1
        // |   |  └── TEST_MAPPING
        // │   └── TEST_MAPPING
        // ├── src2
        // │   └── TEST_MAPPING
        // └── test_mappings.zip
        File tempDir = null;
        try {
            tempDir = FileUtil.createTempDir("test_mapping");
            File srcDir = FileUtil.createNamedTempDir(tempDir, "src1");
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);

            File subDir = FileUtil.createNamedTempDir(srcDir, "sub_dir1");
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            subDir = FileUtil.createNamedTempDir(tempDir, "src2");
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            srcFile = File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, tempDir, DISABLED_PRESUBMIT_TESTS);
            List<File> filesToZip =
                    Arrays.asList(srcDir, subDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));

            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            Set<String> sources = mTestMapping.getTestMappingSources(zipFile);
            assertEquals(3, sources.size());
            assertTrue(sources.contains("src1/TEST_MAPPING"));
            assertTrue(sources.contains("src1/sub_dir1/TEST_MAPPING"));
            assertTrue(sources.contains("src2/TEST_MAPPING"));
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMapping#mergeTestMappingZips()} for merging a missed test_mappings.zip.
     */
    @Test
    public void testMergeMissedTestMappingZips() throws Exception {
        // Test directory1 structure:
        // ├── disabled-presubmit-tests
        // ├── src1
        // │   └── TEST_MAPPING
        // └── test_mappings.zip
        File tempDir = null;
        File baseDir = null;
        IBuildInfo mockBuildInfo = mock(IBuildInfo.class);
        try {
            baseDir = FileUtil.createTempDir("base_directory");
            // Create 1 test_mappings.zip
            tempDir = FileUtil.createTempDir("test_mapping");
            File srcDir = FileUtil.createNamedTempDir(tempDir, "src1");
            createTestMapping(srcDir, "test_mapping_kernel1");
            createTestMapping(tempDir, DISABLED_PRESUBMIT_TESTS);
            List<File> filesToZip =
                    Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));
            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);
            when(mockBuildInfo.getFile("extra-zip")).thenReturn(null);
            try {
                mTestMapping.mergeTestMappingZips(
                        mockBuildInfo, Arrays.asList("extra-zip"), zipFile, baseDir);
                fail("Should have thrown an exception.");
            } catch (HarnessRuntimeException expected) {
                // expected
                assertEquals(
                        "Missing extra-zip in the BuildInfo file.", expected.getMessage());
            }
        } finally {
            FileUtil.recursiveDelete(tempDir);
            FileUtil.recursiveDelete(baseDir);
        }
    }

    /**
     * Test for {@link TestMapping#mergeTestMappingZips()} to ensure no duplicated source of
     * TEST_MAPPING files.
     */
    @Test
    public void testMergeTestMappingZipsWithDuplicateSources() throws Exception {
        // 1 test_mappings.zip structure:
        // ├── disabled-presubmit-tests
        // ├── src1
        // │   └── TEST_MAPPING
        // └── test_mappings.zip
        //
        // another test_mappings.zip structure:
        // ├── disabled-presubmit-tests
        // ├── src1
        // │   └── TEST_MAPPING
        // └── test_mappings.zip
        File tempDir = null;
        File baseDir = null;
        IBuildInfo mockBuildInfo = mock(IBuildInfo.class);
        try {
            baseDir = FileUtil.createTempDir("base_directory");
            // Create 1 test_mappings.zip
            tempDir = FileUtil.createTempDir("test_mapping");
            File srcDir = FileUtil.createNamedTempDir(tempDir, "src1");
            createTestMapping(srcDir, "test_mapping_kernel1");
            createTestMapping(tempDir, DISABLED_PRESUBMIT_TESTS);
            List<File> filesToZip =
                    Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));
            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);
            when(mockBuildInfo.getFile("extra-zip")).thenReturn(zipFile);
            try {
                mTestMapping.mergeTestMappingZips(
                        mockBuildInfo, Arrays.asList("extra-zip"), zipFile, baseDir);
                fail("Should have thrown an exception.");
            } catch (HarnessRuntimeException expected) {
                // expected
                assertTrue(expected.getMessage().contains("Collision of Test Mapping file"));
            }
        } finally {
            FileUtil.recursiveDelete(tempDir);
            FileUtil.recursiveDelete(baseDir);
        }
    }

    /** Test for {@link TestMapping#getTests()} for loading tests from 2 test_mappings.zip. */
    @Test
    public void testGetTestsWithAdditionalTestMappingZips() throws Exception {
        // Test directory1 structure:
        // ├── disabled-presubmit-tests
        // ├── src1
        // │   └── TEST_MAPPING
        // └── test_mappings.zip
        //
        // Test directory2 structure:
        // ├── disabled-presubmit-tests
        // ├── src2
        // │   └── TEST_MAPPING
        // └── test_mappings.zip
        File tempDir = null;
        File tempDir2 = null;
        IBuildInfo mockBuildInfo = mock(IBuildInfo.class);
        try {
            // Create 1 test_mappings.zip
            tempDir = FileUtil.createTempDir("test_mapping");
            File srcDir = FileUtil.createNamedTempDir(tempDir, "src1");
            createTestMapping(srcDir, "test_mapping_kernel1");
            createTestMapping(tempDir, DISABLED_PRESUBMIT_TESTS);
            List<File> filesToZip =
                    Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));
            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            // Create another 1 test_mappings.zip
            tempDir2 = FileUtil.createTempDir("test_mapping");
            File srcDir2 = FileUtil.createNamedTempDir(tempDir2, "src2");
            createTestMapping(srcDir2, "test_mapping_kernel2");
            createTestMapping(tempDir2, DISABLED_PRESUBMIT_TESTS);
            List<File> filesToZip2 =
                    Arrays.asList(srcDir2, new File(tempDir2, DISABLED_PRESUBMIT_TESTS));
            File zipFile2 = Paths.get(tempDir2.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip2, zipFile2);

            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);
            when(mockBuildInfo.getFile("extra-zip")).thenReturn(zipFile2);
            Set<TestInfo> results =
                    mTestMapping.getTests(
                            mockBuildInfo,
                            "presubmit",
                            false,
                            null,
                            new HashSet<String>(),
                            Arrays.asList("extra-zip"),
                            new HashSet<>());
            assertEquals(2, results.size());
            Set<String> names = new HashSet<String>();
            for (TestInfo test : results) {
                names.add(test.getName());
            }
            assertTrue(names.contains("test1"));
            assertTrue(names.contains("test2"));
        } finally {
            FileUtil.recursiveDelete(tempDir);
            FileUtil.recursiveDelete(tempDir2);
        }
    }

    /**
     * Test for {@link TestMapping#getTests(Map, String, Set, boolean, Set)} for parsing
     * TEST_MAPPING with checking file_patterns matched.
     */
    @Test
    public void testparseTestMappingWithFilePatterns() throws Exception {
        File tempDir = null;
        File testMappingFile = null;

        try {
            tempDir = FileUtil.createTempDir("test_mapping");
            String srcFile =
                    File.separator
                            + TEST_DATA_DIR
                            + File.separator
                            + "test_mapping_with_file_patterns_java";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            File testMappingRootDir = FileUtil.createTempDir("subdir", tempDir);
            String rootDirName = testMappingRootDir.getName();
            Set<String> matchedPatternPaths =
                    new HashSet<>(
                            Arrays.asList(
                                    rootDirName + File.separator + "a/b/c.java",
                                    rootDirName + File.separator + "b/c.java"));
            testMappingFile =
                    FileUtil.saveResourceFile(resourceStream, testMappingRootDir, TEST_MAPPING);
            List<String> testMappingPaths =
                    new ArrayList<String>(List.of(testMappingRootDir.toString()));
            TestMapping testMapping = new TestMapping(testMappingPaths, IGNORE_IMPORTS);
            Set<TestInfo> tests =
                    testMapping.getTests(
                            testMapping.getTestCollection(
                                    testMappingFile.toPath(),
                                    Paths.get(tempDir.getAbsolutePath()),
                                    matchedPatternPaths),
                            "presubmit",
                            null,
                            false,
                            null,
                            new HashSet<String>());
            Set<String> names = new HashSet<String>();
            for (TestInfo test : tests) {
                names.add(test.getName());
            }
            assertTrue(names.contains("test_java"));

            // test with matched file is TEST_MAPPING
            matchedPatternPaths.clear();
            matchedPatternPaths.add(rootDirName + File.separator + "a/TEST_MAPPING");
            tests =
                    testMapping.getTests(
                            testMapping.getTestCollection(
                                    testMappingFile.toPath(),
                                    Paths.get(tempDir.getAbsolutePath()),
                                    matchedPatternPaths),
                            "presubmit",
                            null,
                            false,
                            null,
                            new HashSet<String>());
            assertEquals(2, tests.size());

            // test with no matched file.
            matchedPatternPaths.clear();
            matchedPatternPaths.add(rootDirName + File.separator + "a/b/c.jar");
            tests =
                    testMapping.getTests(
                            testMapping.getTestCollection(
                                    testMappingFile.toPath(),
                                    Paths.get(tempDir.getAbsolutePath()),
                                    matchedPatternPaths),
                            "presubmit",
                            null,
                            false,
                            null,
                            new HashSet<String>());
            assertEquals(0, tests.size());

            // Test with no test mapping path passed from TMSR, skip the file patterns checking.
            TestMapping testMappingWithEmptyPath = new TestMapping();
            tests =
                    testMappingWithEmptyPath.getTests(
                            testMappingWithEmptyPath.getTestCollection(
                                    testMappingFile.toPath(),
                                    Paths.get(tempDir.getAbsolutePath()),
                                    matchedPatternPaths),
                            "presubmit",
                            null,
                            false,
                            null,
                            new HashSet<String>());
            assertEquals(2, tests.size());
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    private void createTestMapping(File srcDir, String srcName) throws Exception {
        String srcFile = File.separator + TEST_DATA_DIR + File.separator + srcName;
        InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
        FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
    }

    private String getJsonStringByName(String fileName) throws Exception {
        File tempDir = null;
        try {
            tempDir = FileUtil.createTempDir("test_mapping");
            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + fileName;
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            Path file = Paths.get(srcDir.getAbsolutePath(), TEST_MAPPING);
            return String.join("\n", Files.readAllLines(file, StandardCharsets.UTF_8));
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }
}
