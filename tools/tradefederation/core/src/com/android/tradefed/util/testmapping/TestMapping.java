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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.observatory.TestDiscoveryInvoker;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil2;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** A class for loading a TEST_MAPPING file. */
public class TestMapping {

    // Key for test sources information stored in metadata of ConfigurationDescription.
    public static final String TEST_SOURCES = "Test Sources";
    // Pattern used to identify mainline tests without parameterized modules configured.
    public static final Pattern MAINLINE_REGEX = Pattern.compile("(\\S+)\\[(\\S+)\\]");

    private static final String PRESUBMIT = "presubmit";
    private static final String IMPORTS = "imports";
    private static final String KEY_IMPORT_PATH = "path";
    private static final String KEY_HOST = "host";
    private static final String KEY_KEYWORDS = "keywords";
    private static final String KEY_FILE_PATTERNS = "file_patterns";
    private static final String KEY_NAME = "name";
    private static final String KEY_OPTIONS = "options";
    private static final String TEST_MAPPING = "TEST_MAPPING";
    public static final String TEST_MAPPINGS_ZIP = "test_mappings.zip";
    // A file containing module names that are disabled in presubmit test runs.
    private static final String DISABLED_PRESUBMIT_TESTS_FILE = "disabled-presubmit-tests";
    // Pattern used to identify comments start with "//" or "#" in TEST_MAPPING.
    private static final Pattern COMMENTS_REGEX = Pattern.compile(
            "(?m)[\\s\\t]*(//|#).*|(\".*?\")");
    private static final Set<String> COMMENTS = new HashSet<>(Arrays.asList("#", "//"));

    private List<String> mTestMappingRelativePaths = new ArrayList<>();

    private boolean mIgnoreTestMappingImports = true;

    /** Constructor to initialize an empty {@link TestMapping} object. */
    public TestMapping() {
        this(new ArrayList<>(), true);
    }

    /**
     * Constructor to create a {@link TestMapping} object.
     *
     * @param testMappingRelativePaths The {@link List<String>} to the TEST_MAPPING file paths.
     * @param ignoreTestMappingImports The {@link boolean} to ignore imports.
     */
    public TestMapping(List<String> testMappingRelativePaths, boolean ignoreTestMappingImports) {
        mTestMappingRelativePaths = testMappingRelativePaths;
        mIgnoreTestMappingImports = ignoreTestMappingImports;
    }

    /**
     * Helper to get the {@link Map} test collection from a path to TEST_MAPPING file.
     *
     * @param path The {@link Path} to a TEST_MAPPING file.
     * @param testMappingsDir The {@link Path} to the folder of all TEST_MAPPING files for a build.
     * @param matchedPatternPaths The {@link Set<String>} to file paths matched patterns.
     * @return A {@link Map} of test collection.
     */
    @VisibleForTesting
    Map<String, Set<TestInfo>> getTestCollection(
            Path path, Path testMappingsDir, Set<String> matchedPatternPaths) {
        Map<String, Set<TestInfo>> testCollection = new LinkedHashMap<>();
        String relativePath = testMappingsDir.relativize(path.getParent()).toString();
        String errorMessage = null;
        if (Files.notExists(path)) {
            CLog.d("TEST_MAPPING path not found: %s.", path);
            return testCollection;
        }
        try {
            String content = removeComments(
                    String.join("\n", Files.readAllLines(path, StandardCharsets.UTF_8)));
            if (Strings.isNullOrEmpty(content)) {
                return testCollection;
            }
            JSONTokener tokener = new JSONTokener(content);
            JSONObject root = new JSONObject(tokener);
            Iterator<String> testGroups = (Iterator<String>) root.keys();

            Set<Path> filePaths = new HashSet<>();
            if (!mIgnoreTestMappingImports) {
                listTestMappingFiles(path.getParent(), testMappingsDir, filePaths);
            }

            while (testGroups.hasNext()) {
                String group = testGroups.next();
                if (group.equals(IMPORTS)) {
                    continue;
                }
                Set<TestInfo> testsForGroup = new LinkedHashSet<>();
                testCollection.put(group, testsForGroup);
                JSONArray arr = root.getJSONArray(group);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject testObject = arr.getJSONObject(i);
                    boolean hostOnly = testObject.has(KEY_HOST) && testObject.getBoolean(KEY_HOST);
                    Set<String> keywords = new HashSet<>();
                    if (testObject.has(KEY_KEYWORDS)) {
                        JSONArray keywordArray = testObject.getJSONArray(KEY_KEYWORDS);
                        for (int j = 0; j < keywordArray.length(); j++) {
                            keywords.add(keywordArray.getString(j));
                        }
                    }
                    Set<String> filePatterns = new HashSet<>();
                    if (testObject.has(KEY_FILE_PATTERNS)) {
                        JSONArray filePatternArray = testObject.getJSONArray(KEY_FILE_PATTERNS);
                        for (int k = 0; k < filePatternArray.length(); k++) {
                            filePatterns.add(filePatternArray.getString(k));
                        }
                    }
                    if (!isMatchedFilePatterns(relativePath, matchedPatternPaths, filePatterns)) {
                        continue;
                    }
                    TestInfo test =
                            new TestInfo(
                                    testObject.getString(KEY_NAME),
                                    relativePath,
                                    hostOnly,
                                    keywords);
                    if (testObject.has(KEY_OPTIONS)) {
                        JSONArray optionObjects = testObject.getJSONArray(KEY_OPTIONS);
                        for (int j = 0; j < optionObjects.length(); j++) {
                            JSONObject optionObject = optionObjects.getJSONObject(j);
                            for (int k = 0; k < optionObject.names().length(); k++) {
                                String name = optionObject.names().getString(k);
                                String value = optionObject.getString(name);
                                TestOption option = new TestOption(name, value);
                                test.addOption(option);
                            }
                        }
                    }
                    for (Path filePath : filePaths) {
                        Path importPath = testMappingsDir.relativize(filePath).getParent();
                        if (!test.getSources().contains(importPath.toString())) {
                            test.addImportPaths(Collections.singleton(importPath.toString()));
                        }
                    }
                    testsForGroup.add(test);
                }
            }

            if (!mIgnoreTestMappingImports) {
                // No longer need to include import paths, filePaths includes all related paths.
                for (Path filePath : filePaths) {
                    Map<String, Set<TestInfo>> filePathImportedTestCollection =
                            new TestMapping(mTestMappingRelativePaths, true)
                                    .getTestCollection(
                                            filePath, testMappingsDir, matchedPatternPaths);
                    for (String group : filePathImportedTestCollection.keySet()) {
                        // Add all imported TestInfo to testCollection.
                        if (filePathImportedTestCollection.get(group) != null) {
                            if (testCollection.get(group) == null) {
                                testCollection.put(
                                        group, filePathImportedTestCollection.get(group));
                            } else {
                                testCollection
                                        .get(group)
                                        .addAll(filePathImportedTestCollection.get(group));
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            errorMessage = String.format("TEST_MAPPING file does not exist: %s.", path.toString());
            CLog.e(errorMessage);
        } catch (JSONException e) {
            errorMessage =
                    String.format(
                            "Error parsing TEST_MAPPING file: %s. Error: %s", path.toString(), e);
        }

        if (errorMessage != null) {
            CLog.e(errorMessage);
            throw new HarnessRuntimeException(
                    errorMessage, InfraErrorIdentifier.TEST_MAPPING_FILE_FORMAT_ISSUE);
        }
        return testCollection;
    }

    /**
     * Helper to check whether the given matched-pattern-paths matches the file patterns.
     *
     * @param testMappingDir A {@link String} to TEST_MAPPING directory path.
     * @param matchedPatternPaths A {@link Set<String>} to file paths matched patterns.
     * @param filePatterns A {@link Set<String>} to filePatterns from a TEST_MAPPING file.
     * @return A {@link Boolean} of matched result.
     */
    private boolean isMatchedFilePatterns(
            String testMappingDir, Set<String> matchedPatternPaths, Set<String> filePatterns) {
        Set<String> matchedPatternPathsInSource = new HashSet<>();
        for (String matchedPatternPath : matchedPatternPaths) {
            if (matchedPatternPath.matches(
                    String.join(File.separator, new String[] {testMappingDir, ".*"}))) {
                Path relativePath =
                        Paths.get(testMappingDir).relativize(Paths.get(matchedPatternPath));
                matchedPatternPathsInSource.add(relativePath.toString());
            }
        }
        // For POSTSUBMIT runs, Test Mapping should run the full tests, so return true when
        // mTestMappingRelativePaths is empty.
        if (mTestMappingRelativePaths.isEmpty() || filePatterns.isEmpty()) {
            return true;
        }
        for (String matchedPatternPathInSource : matchedPatternPathsInSource) {
            Path matchedPatternFilePath = Paths.get(matchedPatternPathInSource);
            if (matchedPatternFilePath.getFileName().toString().equals(TEST_MAPPING)) {
                return true;
            }
            for (String filePattern : filePatterns) {
                if (matchedPatternPathInSource.matches(filePattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Helper to remove comments in a TEST_MAPPING file to valid format. Only "//" and "#" are
     * regarded as comments.
     *
     * @param jsonContent A {@link String} of json which content is from a TEST_MAPPING file.
     * @return A {@link String} of valid json without comments.
     */
    @VisibleForTesting
    String removeComments(String jsonContent) {
        StringBuffer out = new StringBuffer();
        Matcher matcher = COMMENTS_REGEX.matcher(jsonContent);
        while (matcher.find()) {
            if (COMMENTS.contains(matcher.group(1))) {
                matcher.appendReplacement(out, "");
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Helper to list all test mapping files, look for all parent directories and related import
     * paths.
     *
     * @param testMappingDir The {@link Path} to a TEST_MAPPING file parent directory.
     * @param testMappingsRootDir The {@link Path} to the folder of all TEST_MAPPING files for a
     *     build.
     * @param filePaths A {@link Set<Path>} to store all TEST_MAPPING paths.
     */
    public void listTestMappingFiles(
            Path testMappingDir, Path testMappingsRootDir, Set<Path> filePaths) {
        String errorMessage = null;

        if (!testMappingDir.toAbsolutePath().startsWith(testMappingsRootDir.toAbsolutePath())) {
            CLog.d(
                    "SKIPPED: Path %s is not under test mapping directory %s.",
                    testMappingDir, testMappingsRootDir);
            return;
        }

        if (Files.notExists(testMappingDir)) {
            CLog.d("TEST_MAPPING path not found: %s.", testMappingDir);
            return;
        }

        try {
            Path testMappingPath = testMappingDir.resolve(TEST_MAPPING);
            filePaths.add(testMappingPath);
            String content =
                    removeComments(
                            String.join(
                                    "\n",
                                    Files.readAllLines(testMappingPath, StandardCharsets.UTF_8)));
            if (Strings.isNullOrEmpty(content)) {
                return;
            }
            JSONTokener tokener = new JSONTokener(content);
            JSONObject root = new JSONObject(tokener);
            if (root.has(IMPORTS) && !mIgnoreTestMappingImports) {
                JSONArray arr = root.getJSONArray(IMPORTS);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject testObject = arr.getJSONObject(i);
                    Path importPath = Paths.get(testObject.getString(KEY_IMPORT_PATH));
                    Path normImportPath =
                            Paths.get(testMappingsRootDir.toString(), importPath.toString());

                    Path importPathTestMappingPath = normImportPath.resolve(TEST_MAPPING);
                    if (!filePaths.contains(importPathTestMappingPath)) {
                        if (Files.exists(importPathTestMappingPath)) {
                            filePaths.add(importPathTestMappingPath);
                        }
                        listTestMappingFiles(importPath, testMappingsRootDir, filePaths);
                    }
                }
            }

            while (testMappingDir
                    .toAbsolutePath()
                    .startsWith(testMappingsRootDir.toAbsolutePath())) {
                if (testMappingDir.toAbsolutePath().equals(testMappingsRootDir.toAbsolutePath())) {
                    break;
                }
                Path upperDirectory = testMappingDir.getParent();
                Path upperDirectoryTestMappingPath = upperDirectory.resolve(TEST_MAPPING);
                if (Files.exists(upperDirectoryTestMappingPath)
                        && !filePaths.contains(upperDirectoryTestMappingPath)) {
                    filePaths.add(upperDirectoryTestMappingPath);
                    listTestMappingFiles(upperDirectory, testMappingsRootDir, filePaths);
                }
                testMappingDir = upperDirectory;
            }

        } catch (IOException e) {
            errorMessage =
                    String.format(
                            "Error reading TEST_MAPPING file: %s.", testMappingDir.toString());
        } catch (JSONException e) {
            errorMessage =
                    String.format(
                            "Error parsing TEST_MAPPING file: %s. Error: %s",
                            testMappingDir.toString(), e);
        }

        if (errorMessage != null) {
            CLog.e(errorMessage);
            throw new RuntimeException(errorMessage);
        }
    }

    /**
     * Helper to get all tests set from the given test collection, group name, disabled tests, host
     * test only, and keywords.
     *
     * @param testCollection A {@link Map} of the test collection.
     * @param testGroup A {@link String} of the test group.
     * @param disabledTests A set of {@link String} for the name of the disabled tests.
     * @param hostOnly true if only tests running on host and don't require device should be
     *     returned. false to return tests that require device to run.
     * @param keywords A set of {@link String} to be matched when filtering tests to run in a Test
     *     Mapping suite.
     * @param ignoreKeywords A set of {@link String} of keywords to be ignored.
     * @return A {@code Set<TestInfo>} of the test infos.
     */
    @VisibleForTesting
    Set<TestInfo> getTests(
            Map<String, Set<TestInfo>> testCollection,
            String testGroup,
            Set<String> disabledTests,
            boolean hostOnly,
            Set<String> keywords,
            Set<String> ignoreKeywords) {
        Set<TestInfo> tests = new LinkedHashSet<TestInfo>();
        for (TestInfo test : testCollection.getOrDefault(testGroup, new HashSet<>())) {
            if (disabledTests != null && disabledTests.contains(test.getName())) {
                continue;
            }
            if (test.getHostOnly() != hostOnly) {
                continue;
            }
            Set<String> testKeywords = test.getKeywords(ignoreKeywords);
            // Skip the test if no keyword is specified but the test requires certain keywords.
            if ((keywords == null || keywords.isEmpty()) && !testKeywords.isEmpty()) {
                continue;
            }
            // Skip the test if any of the required keywords is not specified by the test.
            if (keywords != null) {
                boolean allKeywordsFound = true;
                for (String keyword : keywords) {
                    if (!testKeywords.contains(keyword)) {
                        allKeywordsFound = false;
                        break;
                    }
                }
                // The test should be skipped if any keyword is missing in the test configuration.
                if (!allKeywordsFound) {
                    continue;
                }
            }
            tests.add(test);
        }

        return tests;
    }

    /**
     * Helper to find all tests in all TEST_MAPPING files based on an artifact in the device build.
     *
     * @param buildInfo the {@link IBuildInfo} describing the build.
     * @param testGroup a {@link String} of the test group.
     * @param hostOnly true if only tests running on host and don't require device should be
     *     returned. false to return tests that require device to run.
     * @param keywords A set of {@link String} to be matched when filtering tests to run in a Test
     *     Mapping suite.
     * @param ignoreKeywords A set of {@link String} of keywords to be ignored.
     * @return A {@code Set<TestInfo>} of tests set in the build artifact, test_mappings.zip.
     */
    public Set<TestInfo> getTests(
            IBuildInfo buildInfo,
            String testGroup,
            boolean hostOnly,
            Set<String> keywords,
            Set<String> ignoreKeywords) {
        return getTests(
                buildInfo,
                testGroup,
                hostOnly,
                keywords,
                ignoreKeywords,
                new ArrayList<>(),
                new HashSet<>());
    }

    /**
     * Helper to find all tests in all TEST_MAPPING files based on the given artifact. This is
     * needed when a suite run requires to run all tests in TEST_MAPPING files for a given group,
     * e.g., presubmit.
     *
     * @param buildInfo the {@link IBuildInfo} describing the build.
     * @param testGroup a {@link String} of the test group.
     * @param hostOnly true if only tests running on host and don't require device should be
     *     returned. false to return tests that require device to run.
     * @param keywords A set of {@link String} to be matched when filtering tests to run in a Test
     *     Mapping suite.
     * @param ignoreKeywords A set of {@link String} of keywords to be ignored.
     * @param extraZipNames A set of {@link String} for the name of additional test_mappings.zip
     *     that will be merged.
     * @param matchedPatternPaths The {@link Set<String>} to file paths matched patterns.
     * @return A {@code Set<TestInfo>} of tests set in the build artifact, test_mappings.zip.
     */
    public Set<TestInfo> getTests(
            IBuildInfo buildInfo,
            String testGroup,
            boolean hostOnly,
            Set<String> keywords,
            Set<String> ignoreKeywords,
            List<String> extraZipNames,
            Set<String> matchedPatternPaths) {
        Set<TestInfo> tests = Collections.synchronizedSet(new LinkedHashSet<TestInfo>());
        File zipFile;
        if (buildInfo == null) {
            zipFile = lookupTestMappingZip(TEST_MAPPINGS_ZIP);
        } else {
            zipFile = buildInfo.getFile(TEST_MAPPINGS_ZIP);
        }
        File testMappingsDir = null;
        boolean delete = false;
        // Handle already extracted entry
        if (zipFile.isDirectory()) {
            testMappingsDir = zipFile;
        } else {
            testMappingsDir = extractTestMappingsZip(zipFile);
            delete = true;
        }
        Path testMappingsRootPath = Paths.get(testMappingsDir.getAbsolutePath());
        CLog.d("Relative test mapping paths: %s", mTestMappingRelativePaths);
        try (Stream<Path> stream =
                mTestMappingRelativePaths.isEmpty()
                        ? Files.walk(testMappingsRootPath, FileVisitOption.FOLLOW_LINKS)
                        : getAllTestMappingPaths(testMappingsRootPath).stream()) {
            mergeTestMappingZips(buildInfo, extraZipNames, zipFile, testMappingsDir);
            Set<String> disabledTests = getDisabledTests(testMappingsRootPath, testGroup);
            stream.filter(path -> path.getFileName().toString().equals(TEST_MAPPING))
                    .forEach(
                            path ->
                                    tests.addAll(
                                            getTests(
                                                    getTestCollection(
                                                            path,
                                                            testMappingsRootPath,
                                                            matchedPatternPaths),
                                                    testGroup,
                                                    disabledTests,
                                                    hostOnly,
                                                    keywords,
                                                    ignoreKeywords)));

        } catch (IOException e) {
            throw new RuntimeException(
                    String.format(
                            "IO exception (%s) when reading tests from TEST_MAPPING files (%s)",
                            e.getMessage(), testMappingsDir.getAbsolutePath()), e);
        } finally {
            if (delete) {
                FileUtil.recursiveDelete(testMappingsDir);
            }
        }
        return tests;
    }

    /**
     * Helper to get all TEST_MAPPING paths relative to TEST_MAPPINGS_ZIP.
     *
     * @param testMappingsRootPath The {@link Path} to a test mappings zip path.
     * @return A {@code Set<Path>} of all the TEST_MAPPING paths relative to TEST_MAPPINGS_ZIP.
     */
    @VisibleForTesting
    Set<Path> getAllTestMappingPaths(Path testMappingsRootPath) {
        Set<Path> allTestMappingPaths = new LinkedHashSet<>();
        for (String path : mTestMappingRelativePaths) {
            boolean hasAdded = false;
            Path testMappingPath = testMappingsRootPath.resolve(path);
            // Recursively find the TEST_MAPPING file until reaching to testMappingsRootPath.
            while (!testMappingPath.equals(testMappingsRootPath)) {
                if (testMappingPath.resolve(TEST_MAPPING).toFile().exists()) {
                    hasAdded = true;
                    CLog.d("Adding TEST_MAPPING path: %s", testMappingPath);
                    allTestMappingPaths.add(testMappingPath.resolve(TEST_MAPPING));
                }
                testMappingPath = testMappingPath.getParent();
            }
            if (!hasAdded) {
                CLog.w("Couldn't find TEST_MAPPING files from %s", path);
            }
        }
        if (allTestMappingPaths.isEmpty()) {
            throw new RuntimeException(
                    String.format(
                            "Couldn't find TEST_MAPPING files from %s", mTestMappingRelativePaths));
        }
        CLog.d("All resolved TEST_MAPPING paths: %s", allTestMappingPaths);
        return allTestMappingPaths;
    }

    /**
     * Helper to find all tests in the TEST_MAPPING files from a given directory.
     *
     * @param testMappingsDir the {@link File} the directory containing all Test Mapping files.
     * @return A {@code Map<String, Set<TestInfo>>} of tests in the given directory and its child
     *     directories.
     */
    public Map<String, Set<TestInfo>> getAllTests(File testMappingsDir) {
        Map<String, Set<TestInfo>> allTests = new HashMap<String, Set<TestInfo>>();
        Path testMappingsRootPath = Paths.get(testMappingsDir.getAbsolutePath());
        try (Stream<Path> stream = Files.walk(testMappingsRootPath, FileVisitOption.FOLLOW_LINKS)) {
            stream.filter(path -> path.getFileName().toString().equals(TEST_MAPPING))
                    .forEach(
                            path ->
                                    getAllTests(allTests, path, testMappingsRootPath));

        } catch (IOException e) {
            throw new RuntimeException(
                    String.format(
                            "IO exception (%s) when reading tests from TEST_MAPPING files (%s)",
                            e.getMessage(), testMappingsDir.getAbsolutePath()),
                    e);
        }
        return allTests;
    }

    /**
     * Helper to find all tests in the TEST_MAPPING files from a given directory.
     *
     * @param allTests the {@code HashMap<String, Set<TestInfo>>} containing the tests of each test
     *     group.
     * @param path the {@link Path} to a TEST_MAPPING file.
     * @param testMappingsRootPath the {@link Path} to a test mappings zip path.
     */
    private void getAllTests(
            Map<String, Set<TestInfo>> allTests, Path path, Path testMappingsRootPath) {
        Map<String, Set<TestInfo>> testCollection =
                getTestCollection(path, testMappingsRootPath, new HashSet<>());
        for (String group : testCollection.keySet()) {
            allTests.computeIfAbsent(group, k -> new HashSet<>()).addAll(testCollection.get(group));
        }
    }

    /**
     * Extract a zip file and return the directory that contains the content of unzipped files.
     *
     * @param testMappingsZip A {@link File} of the test mappings zip to extract.
     * @return a {@link File} pointing to the temp directory for test mappings zip.
     */
    public static File extractTestMappingsZip(File testMappingsZip) {
        File testMappingsDir = null;
        try (CloseableTraceScope ignored = new CloseableTraceScope("extractTestMappingsZip")) {
            testMappingsDir = ZipUtil2.extractZipToTemp(testMappingsZip, TEST_MAPPINGS_ZIP);
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format(
                            "IO exception (%s) when extracting test mappings zip (%s)",
                            e.getMessage(), testMappingsZip.getAbsolutePath()), e);
        }
        return testMappingsDir;
    }

    /**
     * Get disabled tests from test mapping artifact.
     *
     * @param testMappingsRootPath The {@link Path} to a test mappings zip path.
     * @param testGroup a {@link String} of the test group.
     * @return a {@link Set<String>} containing all the disabled presubmit tests. No test is
     *     returned if the testGroup is not PRESUBMIT.
     */
    @VisibleForTesting
    Set<String> getDisabledTests(Path testMappingsRootPath, String testGroup) {
        Set<String> disabledTests = new HashSet<>();
        File disabledPresubmitTestsFile =
                new File(testMappingsRootPath.toString(), DISABLED_PRESUBMIT_TESTS_FILE);
        if (!(testGroup.equals(PRESUBMIT) && disabledPresubmitTestsFile.exists())) {
            return disabledTests;
        }
        try {
            disabledTests.addAll(
                    Arrays.asList(
                            FileUtil.readStringFromFile(disabledPresubmitTestsFile)
                                    .split("\\r?\\n")));
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format(
                            "IO exception (%s) when reading disabled tests from file (%s)",
                            e.getMessage(), disabledPresubmitTestsFile.getAbsolutePath()), e);
        }
        return disabledTests;
    }

    /**
     * Helper to get the matcher for parameterized mainline tests.
     *
     * @param {@code Set<TestInfo>} of tests set in the build artifact, test_mappings.zip.
     * @return A {@link Matcher} for parameterized mainline tests.
     */
    public static Matcher getMainlineTestModuleName(TestInfo info) throws ConfigurationException {
        Matcher matcher = MAINLINE_REGEX.matcher(info.getName());
        if (matcher.find()) {
            return matcher;
        }
        throw new ConfigurationException(
                String.format(
                        "Unmatched \"[]\" for \"%s\" configured in the %s. "
                                + "Parameter must contain square brackets.",
                        info.getName(), info.getSources()));
    }

    /**
     * Merge additional test mapping zips into the given directory.
     *
     * @param buildInfo the {@link IBuildInfo} describing the build.
     * @param extraZips A {@link List<String>} of additional zip file paths.
     * @param baseFile A {@link File} of base test mapping zip.
     * @param baseDir A {@link File} pointing to the temp directory for base test mappings zip.
     */
    @VisibleForTesting
    void mergeTestMappingZips(
            IBuildInfo buildInfo, List<String> extraZips, File baseFile, File baseDir)
            throws IOException {
        if (extraZips.isEmpty()) {
            return;
        }
        Set<String> baseNames = getTestMappingSources(baseFile);
        for (String zipName : extraZips) {
            File zipFile;
            if (buildInfo == null) {
                zipFile = lookupTestMappingZip(zipName);
            } else {
                zipFile = buildInfo.getFile(zipName);
            }
            if (zipFile == null) {
                throw new HarnessRuntimeException(
                        String.format("Missing %s in the BuildInfo file.", zipName),
                        InfraErrorIdentifier.ARTIFACT_NOT_FOUND);
            }
            Set<String> targetNames = getTestMappingSources(zipFile);
            validateSources(baseNames, targetNames, zipName);
            baseNames.addAll(targetNames);
            ZipUtil2.extractZip(zipFile, baseDir);
        }
    }

    /**
     * Helper to validate whether there exists collision of the path of Test Mapping files.
     *
     * @param base A {@link Set<String>} of the file paths.
     * @param target A {@link Set<String>} of the file paths.
     * @param zipName A {@link String} of the zip file path.
     */
    private void validateSources(Set<String> base, Set<String> target, String zipName) {
        for (String name : target) {
            if (base.contains(name)) {
                throw new HarnessRuntimeException(
                    String.format("Collision of Test Mapping file: %s in artifact: %s.",
                        name, zipName), InfraErrorIdentifier.TEST_MAPPING_PATH_COLLISION);
            }
        }
    }

    /**
     * Helper to collect the path of Test Mapping files with a given zip file.
     *
     * @param zipFile A {@link File} of the test mappings zip.
     * @return A {@link Set<String>} for file paths from the test mappings zip.
     */
    @VisibleForTesting
    Set<String> getTestMappingSources(File zipFile) {
        Set<String> fileNames = new HashSet<>();
        Enumeration<? extends ZipArchiveEntry> entries = null;
        ZipFile f = null;
        try {
            f = new ZipFile(zipFile);
            entries = f.getEntries();
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format(
                            "IO exception (%s) when accessing test_mappings.zip (%s)",
                            e.getMessage(), zipFile),
                    e);
        } finally {
            ZipUtil2.closeZip(f);
        }
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            // TODO: Temporarily exclude disabled-presubmit-test file. We'll need to revisit if that
            // file is used on the older branch/target, if no, remove that file.
            if (!entry.isDirectory() && !entry.getName().equals(DISABLED_PRESUBMIT_TESTS_FILE)) {
                fileNames.add(entry.getName());
            }
        }
        return fileNames;
    }

    /**
     * Helper to locate the test mapping zip file from environment.
     *
     * @param zipName The original name of a test mappings zip.
     * @return The test mapping file, or throw if unable to find.
     */
    private File lookupTestMappingZip(String zipName) {
        String directFile = System.getenv(TestDiscoveryInvoker.TEST_MAPPING_ZIP_FILE);
        if (directFile != null && new File(directFile).exists()) {
            return new File(directFile);
        }
        throw new HarnessRuntimeException(
                String.format("Unable to locate the test mapping zip file %s", zipName),
                InfraErrorIdentifier.TEST_MAPPING_FILE_NOT_EXIST);
    }
}
