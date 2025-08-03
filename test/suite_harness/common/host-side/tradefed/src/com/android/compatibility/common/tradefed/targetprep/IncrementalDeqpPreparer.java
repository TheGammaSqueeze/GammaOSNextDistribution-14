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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.DeviceInfo;
import com.android.compatibility.common.util.HostInfoStore;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.TestErrorIdentifier;
import com.android.tradefed.targetprep.BaseTargetPreparer;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Collects the dEQP dependencies and compares the builds. */
@OptionClass(alias = "incremental-deqp-preparer")
public class IncrementalDeqpPreparer extends BaseTargetPreparer {

    @Option(
            name = "base-build",
            description =
                    "Absolute file path to a target file of the base build. Required for "
                            + "incremental dEQP.")
    private File mBaseBuild = null;

    @Option(
            name = "current-build",
            description =
                    "Absolute file path to a target file of the current build. Required for"
                            + " incremental dEQP.")
    private File mCurrentBuild = null;

    @Option(
            name = "extra-dependency",
            description =
                    "Absolute file path to a text file that includes extra dEQP test "
                            + "dependencies. Optional for incremental dEQP.")
    private File mExtraDependency = null;

    @Option(
            name = "fallback-strategy",
            description =
                    "The fallback strategy to apply if the incremental dEQP qualification testing "
                            + "for the builds fails.")
    private FallbackStrategy mFallbackStrategy = FallbackStrategy.ABORT_IF_ANY_EXCEPTION;

    private enum FallbackStrategy {
        // Continues to run full dEQP tests no matter an exception is thrown or not.
        RUN_FULL_DEQP,
        // Aborts if an exception is thrown in the preparer. Otherwise, runs full dEQP tests due to
        // dependency modifications.
        ABORT_IF_ANY_EXCEPTION;
    }

    private static final String MODULE_NAME = "CtsDeqpTestCases";
    private static final String DEVICE_DEQP_DIR = "/data/local/tmp";
    private static final String[] TEST_LIST =
            new String[] {"vk-32", "vk-64", "gles3-32", "gles3-64"};
    private static final String BASE_BUILD_FINGERPRINT_ATTRIBUTE = "base_build_fingerprint";
    private static final String CURRENT_BUILD_FINGERPRINT_ATTRIBUTE = "current_build_fingerprint";
    private static final String MODULE_ATTRIBUTE = "module";
    private static final String MODULE_NAME_ATTRIBUTE = "module_name";
    private static final String DEPENDENCY_ATTRIBUTE = "deps";
    private static final String EXTRA_DEPENDENCY_ATTRIBUTE = "extra_deps";
    private static final String DEPENDENCY_CHANGES_ATTRIBUTE = "deps_changes";
    private static final String DEPENDENCY_NAME_ATTRIBUTE = "dep_name";
    private static final String DEPENDENCY_DETAIL_ATTRIBUTE = "detail";
    private static final String DEPENDENCY_BASE_BUILD_HASH_ATTRIBUTE = "base_build_hash";
    private static final String DEPENDENCY_CURRENT_BUILD_HASH_ATTRIBUTE = "current_build_hash";
    private static final String NULL_BUILD_HASH = "0";
    private static final String DEQP_BINARY_FILE_NAME_32 = "deqp-binary32";

    private static final String DEPENDENCY_DETAIL_MISSING_IN_CURRENT = "MISSING_IN_CURRENT_BUILD";
    private static final String DEPENDENCY_DETAIL_MISSING_IN_BASE = "MISSING_IN_BASE_BUILD";
    private static final String DEPENDENCY_DETAIL_MISSING_IN_BASE_AND_CURRENT =
            "MISSING_IN_BASE_AND_CURRENT_BUILDS";
    private static final String DEPENDENCY_DETAIL_DIFFERENT_HASH =
            "BASE_AND_CURRENT_BUILD_DIFFERENT_HASH";

    private static final Pattern EXCLUDE_DEQP_PATTERN =
            Pattern.compile("(^/data/|^/apex/|^\\[vdso" + "\\]|^/dmabuf|^/kgsl-3d0|^/mali csf)");

    public static final String INCREMENTAL_DEQP_ATTRIBUTE_NAME = "incremental-deqp";
    public static final String REPORT_NAME = "IncrementalCtsDeviceInfo.deviceinfo.json";

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        try {
            ITestDevice device = testInfo.getDevice();
            CompatibilityBuildHelper buildHelper =
                    new CompatibilityBuildHelper(testInfo.getBuildInfo());
            IInvocationContext context = testInfo.getContext();
            runIncrementalDeqp(context, device, buildHelper);
        } catch (Exception e) {
            if (mFallbackStrategy == FallbackStrategy.ABORT_IF_ANY_EXCEPTION) {
                // Rethrows the exception to abort the task.
                throw e;
            }
            // Ignores the exception and continues to run full dEQP tests.
        }
    }

    /**
     * Runs a check to determine if the current build has changed dEQP dependencies or not. Will
     * signal to dEQP test runner whether the majority of dEQP cases can be skipped, and also
     * generate an incremental cts report with more details.
     *
     * <p>Synchronize this method so that multiple shards won't run it multiple times.
     */
    protected void runIncrementalDeqp(
            IInvocationContext context, ITestDevice device, CompatibilityBuildHelper buildHelper)
            throws TargetSetupError, DeviceNotAvailableException {
        // Make sure synchronization is on the class not the object.
        synchronized (IncrementalDeqpPreparer.class) {
            File jsonFile;
            try {
                File deviceInfoDir =
                        new File(buildHelper.getResultDir(), DeviceInfo.RESULT_DIR_NAME);
                jsonFile = new File(deviceInfoDir, REPORT_NAME);
                if (jsonFile.exists()) {
                    CLog.i("Another shard has already checked dEQP dependencies.");
                    return;
                }
            } catch (FileNotFoundException e) {
                throw new TargetSetupError(
                        "Fail to read invocation result directory.",
                        device.getDeviceDescriptor(),
                        TestErrorIdentifier.TEST_ABORTED);
            }

            Set<String> simpleperfDependencies = getDeqpDependencies(device);
            Set<String> extraDependencies = parseExtraDependency(device);
            Set<String> dependencies = new HashSet<>(simpleperfDependencies);
            dependencies.addAll(extraDependencies);

            // Write data of incremental dEQP to device info report.
            try (HostInfoStore store = new HostInfoStore(jsonFile)) {
                store.open();

                store.addResult(
                        BASE_BUILD_FINGERPRINT_ATTRIBUTE, getBuildFingerPrint(mBaseBuild, device));
                store.addResult(
                        CURRENT_BUILD_FINGERPRINT_ATTRIBUTE,
                        getBuildFingerPrint(mCurrentBuild, device));

                store.startArray(MODULE_ATTRIBUTE);
                store.startGroup(); // Module
                store.addResult(MODULE_NAME_ATTRIBUTE, MODULE_NAME);
                store.addListResult(
                        DEPENDENCY_ATTRIBUTE,
                        simpleperfDependencies.stream().sorted().collect(Collectors.toList()));
                store.addListResult(
                        EXTRA_DEPENDENCY_ATTRIBUTE,
                        extraDependencies.stream().sorted().collect(Collectors.toList()));
                store.startArray(DEPENDENCY_CHANGES_ATTRIBUTE);
                boolean noChange = true;
                Map<String, String> currentBuildHashMap =
                        getTargetFileHash(dependencies, mCurrentBuild);
                Map<String, String> baseBuildHashMap = getTargetFileHash(dependencies, mBaseBuild);

                for (String dependency : dependencies) {
                    if (!baseBuildHashMap.containsKey(dependency)
                            && currentBuildHashMap.containsKey(dependency)) {
                        noChange = false;
                        store.startGroup();
                        store.addResult(DEPENDENCY_NAME_ATTRIBUTE, dependency);
                        store.addResult(
                                DEPENDENCY_DETAIL_ATTRIBUTE, DEPENDENCY_DETAIL_MISSING_IN_BASE);
                        store.addResult(DEPENDENCY_BASE_BUILD_HASH_ATTRIBUTE, NULL_BUILD_HASH);
                        store.addResult(
                                DEPENDENCY_CURRENT_BUILD_HASH_ATTRIBUTE,
                                currentBuildHashMap.get(dependency));
                        store.endGroup();
                    } else if (!currentBuildHashMap.containsKey(dependency)
                            && baseBuildHashMap.containsKey(dependency)) {
                        noChange = false;
                        store.startGroup();
                        store.addResult(DEPENDENCY_NAME_ATTRIBUTE, dependency);
                        store.addResult(
                                DEPENDENCY_DETAIL_ATTRIBUTE, DEPENDENCY_DETAIL_MISSING_IN_CURRENT);
                        store.addResult(
                                DEPENDENCY_BASE_BUILD_HASH_ATTRIBUTE,
                                baseBuildHashMap.get(dependency));
                        store.addResult(DEPENDENCY_CURRENT_BUILD_HASH_ATTRIBUTE, NULL_BUILD_HASH);
                        store.endGroup();
                    } else if (!currentBuildHashMap.containsKey(dependency)
                            && !baseBuildHashMap.containsKey(dependency)) {
                        noChange = false;
                        store.startGroup();
                        store.addResult(DEPENDENCY_NAME_ATTRIBUTE, dependency);
                        store.addResult(
                                DEPENDENCY_DETAIL_ATTRIBUTE,
                                DEPENDENCY_DETAIL_MISSING_IN_BASE_AND_CURRENT);
                        store.addResult(DEPENDENCY_BASE_BUILD_HASH_ATTRIBUTE, NULL_BUILD_HASH);
                        store.addResult(DEPENDENCY_CURRENT_BUILD_HASH_ATTRIBUTE, NULL_BUILD_HASH);
                        store.endGroup();
                    } else if (!currentBuildHashMap
                            .get(dependency)
                            .equals(baseBuildHashMap.get(dependency))) {
                        noChange = false;
                        store.startGroup();
                        store.addResult(DEPENDENCY_NAME_ATTRIBUTE, dependency);
                        store.addResult(
                                DEPENDENCY_DETAIL_ATTRIBUTE, DEPENDENCY_DETAIL_DIFFERENT_HASH);
                        store.addResult(
                                DEPENDENCY_BASE_BUILD_HASH_ATTRIBUTE,
                                baseBuildHashMap.get(dependency));
                        store.addResult(
                                DEPENDENCY_CURRENT_BUILD_HASH_ATTRIBUTE,
                                currentBuildHashMap.get(dependency));
                        store.endGroup();
                    }
                }
                store.endArray(); // dEQP changes
                if (noChange) {
                    // Add an attribute to all shard's build info.
                    for (IBuildInfo bi : context.getBuildInfos()) {
                        bi.addBuildAttribute(INCREMENTAL_DEQP_ATTRIBUTE_NAME, "");
                    }
                }
                store.endGroup(); // Module
                store.endArray();
            } catch (IOException e) {
                throw new TargetSetupError(
                        "Failed to compare the builds",
                        e,
                        device.getDeviceDescriptor(),
                        TestErrorIdentifier.TEST_ABORTED);
            } catch (Exception e) {
                throw new TargetSetupError(
                        "Failed to write incremental dEQP report",
                        e,
                        device.getDeviceDescriptor(),
                        TestErrorIdentifier.TEST_ABORTED);
            } finally {
                if (jsonFile.exists() && jsonFile.length() == 0) {
                    FileUtil.deleteFile(jsonFile);
                }
            }
        }
    }

    /** Parses the extra dependency file and get dependencies. */
    private Set<String> parseExtraDependency(ITestDevice device) throws TargetSetupError {
        Set<String> result = new HashSet<>();
        if (mExtraDependency == null) {
            return result;
        }
        try {
            for (String line : Files.readAllLines(mExtraDependency.toPath())) {
                result.add(line.trim());
            }
        } catch (IOException e) {
            throw new TargetSetupError(
                    "Failed to parse extra dependencies file.",
                    e,
                    device.getDeviceDescriptor(),
                    TestErrorIdentifier.TEST_ABORTED);
        }
        return result;
    }

    /** Gets the filename of dEQP dependencies in build. */
    private Set<String> getDeqpDependencies(ITestDevice device)
            throws DeviceNotAvailableException, TargetSetupError {
        Set<String> result = new HashSet<>();

        for (String testName : TEST_LIST) {
            String perfFile = DEVICE_DEQP_DIR + "/" + testName + ".data";
            String binaryFile = DEVICE_DEQP_DIR + "/" + getBinaryFileName(testName);
            String testFile = DEVICE_DEQP_DIR + "/" + getTestFileName(testName);
            String logFile = DEVICE_DEQP_DIR + "/" + testName + ".qpa";

            String command =
                    String.format(
                            "cd %s && simpleperf record -o %s %s --deqp-caselist-file=%s "
                                    + "--deqp-log-images=disable --deqp-log-shader-sources=disable "
                                    + "--deqp-log-filename=%s --deqp-surface-type=fbo "
                                    + "--deqp-surface-width=2048 --deqp-surface-height=2048",
                            DEVICE_DEQP_DIR, perfFile, binaryFile, testFile, logFile);
            device.executeShellCommand(command);

            // Check the test log.
            String testFileContent = device.pullFileContents(testFile);
            if (testFileContent == null || testFileContent.isEmpty()) {
                throw new TargetSetupError(
                        String.format("Fail to read test file: %s", testFile),
                        device.getDeviceDescriptor(),
                        TestErrorIdentifier.TEST_ABORTED);
            }
            String logContent = device.pullFileContents(logFile);
            if (logContent == null || logContent.isEmpty()) {
                throw new TargetSetupError(
                        String.format("Fail to read simpleperf log file: %s", logFile),
                        device.getDeviceDescriptor(),
                        TestErrorIdentifier.TEST_ABORTED);
            }

            if (!checkTestLog(testFileContent, logContent)) {
                throw new TargetSetupError(
                        "dEQP binary tests are not executed. This may caused by test crash.",
                        device.getDeviceDescriptor(),
                        TestErrorIdentifier.TEST_ABORTED);
            }

            String dumpFile = DEVICE_DEQP_DIR + "/" + testName + "-perf-dump.txt";
            String dumpCommand = String.format("simpleperf dump %s > %s", perfFile, dumpFile);
            device.executeShellCommand(dumpCommand);
            String dumpContent = device.pullFileContents(dumpFile);

            result.addAll(parseDump(dumpContent));
        }

        return result;
    }

    /** Gets the hash value of the specified file's content from the target file. */
    protected Map<String, String> getTargetFileHash(Set<String> fileNames, File targetFile)
            throws IOException, TargetSetupError {
        ZipFile zipFile = new ZipFile(targetFile);

        Map<String, String> hashMap = new HashMap<>();
        for (String file : fileNames) {
            // Convert top directory's name to upper case.
            String[] arr = file.split("/", 3);
            if (arr.length < 3) {
                throw new TargetSetupError(
                        String.format(
                                "Fail to generate zip file entry for dependency: %s. A"
                                        + " valid dependency should be a file path located at a sub"
                                        + " directory.",
                                file),
                        TestErrorIdentifier.TEST_ABORTED);
            }
            String formattedName = arr[1].toUpperCase() + "/" + arr[2];

            ZipEntry entry = zipFile.getEntry(formattedName);
            if (entry == null) {
                CLog.i(
                        "Fail to find the file: %s in target files: %s",
                        formattedName, targetFile.getName());
                continue;
            }
            InputStream is = zipFile.getInputStream(entry);
            String md5 = StreamUtil.calculateMd5(is);
            hashMap.put(file, md5);
        }
        return hashMap;
    }

    /** Parses the dump file and gets list of dependencies. */
    protected Set<String> parseDump(String dumpContent) {
        boolean binaryExecuted = false;
        boolean correctMmap = false;
        Set<String> result = new HashSet<>();
        for (String line : dumpContent.split("\n")) {
            if (!binaryExecuted) {
                // dEQP binary has first been executed.
                Pattern pattern = Pattern.compile(" comm .*deqp-binary");
                if (pattern.matcher(line).find()) {
                    binaryExecuted = true;
                }
            } else {
                // New perf event
                if (!line.startsWith(" ")) {
                    // Ignore mmap with misc 1, they are not related to deqp binary
                    correctMmap = line.startsWith("record mmap") && !line.contains("misc 1");
                }

                // We have reached the filename for a valid perf event, add to the dependency map if
                // it isn't in the exclusion pattern
                if (line.contains("filename") && correctMmap) {
                    String dependency = line.substring(line.indexOf("filename") + 9).trim();
                    if (!EXCLUDE_DEQP_PATTERN.matcher(dependency).find()) {
                        result.add(dependency);
                    }
                }
            }
        }
        return result;
    }

    /** Checks the test log to see if all tests are executed. */
    protected boolean checkTestLog(String testListContent, String logContent) {
        int testCount = testListContent.split("\n").length;

        int executedTestCount = 0;
        for (String line : logContent.split("\n")) {
            if (line.contains("StatusCode=")) {
                executedTestCount++;
            }
        }
        return executedTestCount == testCount;
    }

    /** Gets dEQP binary's test list file based on test name */
    protected String getTestFileName(String testName) {
        if (testName.startsWith("vk")) {
            return "vk-incremental-deqp.txt";
        } else {
            return "gles3-incremental-deqp.txt";
        }
    }

    /** Gets dEQP binary's name based on the test name. */
    protected String getBinaryFileName(String testName) {
        if (testName.endsWith("32")) {
            return DEQP_BINARY_FILE_NAME_32;
        } else {
            return "deqp-binary64";
        }
    }

    /** Gets the build fingerprint from target files. */
    protected String getBuildFingerPrint(File targetFile, ITestDevice device)
            throws TargetSetupError {
        String fingerprint;
        try {
            ZipFile zipFile = new ZipFile(targetFile);
            ZipEntry entry = zipFile.getEntry("SYSTEM/build.prop");
            InputStream is = zipFile.getInputStream(entry);
            Properties prop = new Properties();
            prop.load(is);
            fingerprint = prop.getProperty("ro.system.build.fingerprint");
        } catch (IOException e) {
            throw new TargetSetupError(
                    String.format("Fail to get fingerprint from: %s", targetFile.getName()),
                    e,
                    device.getDeviceDescriptor(),
                    TestErrorIdentifier.TEST_ABORTED);
        }
        return fingerprint;
    }
}
