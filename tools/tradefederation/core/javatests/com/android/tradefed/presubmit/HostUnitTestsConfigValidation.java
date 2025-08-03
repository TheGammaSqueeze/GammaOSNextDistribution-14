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
package com.android.tradefed.presubmit;

import static org.junit.Assert.assertTrue;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.ConfigurationUtil;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.Option;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.PushFilePreparer;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.suite.ValidateSuiteConfigHelper;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.testmapping.TestInfo;
import com.android.tradefed.util.testmapping.TestMapping;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validation tests to run against the configuration in host-unit-tests.zip to ensure they can all
 * parse.
 *
 * <p>Do not add to UnitTests.java. This is meant to run standalone.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class HostUnitTestsConfigValidation implements IBuildReceiver {

    @Option(
            name = "disallowed-test-type",
            description = "The disallowed test type for configs in host-unit-tests.zip")
    private List<String> mDisallowedTestTypes = new ArrayList<>();

    private IBuildInfo mBuild;

    /**
     * List of the officially supported runners in general-tests. Any new addition should go through
     * a review to ensure all runners have a high quality bar.
     */
    private static final Set<String> SUPPORTED_TEST_RUNNERS =
            new HashSet<>(
                    Arrays.asList(
                            // Only accept runners that can be pure host-tests.
                            "com.android.tradefed.testtype.HostGTest",
                            "com.android.tradefed.testtype.IsolatedHostTest",
                            "com.android.tradefed.testtype.python.PythonBinaryHostTest",
                            "com.android.tradefed.testtype.binary.ExecutableHostTest",
                            "com.android.tradefed.testtype.rust.RustBinaryHostTest"));

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuild = buildInfo;
    }

    /** Get all the configuration copied to the build tests dir and check if they load. */
    @Test
    public void testConfigsLoad() throws Exception {
        List<String> errors = new ArrayList<>();
        Assume.assumeTrue(mBuild instanceof IDeviceBuildInfo);

        IConfigurationFactory configFactory = ConfigurationFactory.getInstance();
        List<String> configs = new ArrayList<>();
        IDeviceBuildInfo deviceBuildInfo = (IDeviceBuildInfo) mBuild;
        File testsDir = deviceBuildInfo.getTestsDir();
        List<File> extraTestCasesDirs = Arrays.asList(testsDir);
        configs.addAll(ConfigurationUtil.getConfigNamesFromDirs(null, extraTestCasesDirs));
        for (String configName : configs) {
            try {
                IConfiguration c =
                        configFactory.createConfigurationFromArgs(new String[] {configName});
                // All configurations in host-unit-tests.zip should be module since they are
                // generated from AndroidTest.xml
                ValidateSuiteConfigHelper.validateConfig(c);

                checkPreparers(c.getTargetPreparers(), "host-unit-tests");
                // Check that all the tests runners are well supported.
                checkRunners(c.getTests(), "host-unit-tests");

                // Check for disallowed test types
                GeneralTestsConfigValidation.checkDisallowedTestType(c, mDisallowedTestTypes);

                // Add more checks if necessary
            } catch (ConfigurationException e) {
                errors.add(String.format("\t%s: %s", configName, e.getMessage()));
            }
        }

        // If any errors report them in a final exception.
        if (!errors.isEmpty()) {
            throw new ConfigurationException(
                    String.format("Fail configuration check:\n%s", Joiner.on("\n").join(errors)));
        }
    }

    private static void checkPreparers(List<ITargetPreparer> preparers, String name)
            throws ConfigurationException {
        for (ITargetPreparer preparer : preparers) {
            // Check that all preparers are supported.
            if (preparer instanceof PushFilePreparer) {
                throw new ConfigurationException(
                        String.format(
                                "preparer %s is not supported in %s.",
                                preparer.getClass().getCanonicalName(), name));
            }
        }
    }

    private static void checkRunners(List<IRemoteTest> tests, String name)
            throws ConfigurationException {
        for (IRemoteTest test : tests) {
            // Check that all the tests runners are well supported.
            if (!SUPPORTED_TEST_RUNNERS.contains(test.getClass().getCanonicalName())) {
                throw new ConfigurationException(
                        String.format(
                                "testtype %s is not officially supported in %s. "
                                        + "The supported ones are: %s",
                                test.getClass().getCanonicalName(), name, SUPPORTED_TEST_RUNNERS));
            }
        }
    }

    // This list contains exemption to the duplication of host-unit-tests & TEST_MAPPING.
    // This will be used when migrating default and clean up as we clear the TEST_MAPPING files.
    private static final Set<String> EXEMPTION_LIST = Collections.emptySet();

    // These are the final modules allowed to be in host test mapping to prevent new addition.
    private static final Set<String> FINAL_MODULE_LIST =
            ImmutableSet.of("hello_world_test", "tvts-tradefed-tests");

    /**
     * This test ensures that unit tests are not also running as part of test mapping to avoid
     * double running them.
     */
    @Test
    public void testNotInTestMappingPresubmit() {
        List<String> errors = getErrors("presubmit");
        if (!errors.isEmpty()) {
            String message =
                    String.format("Fail configuration check:\n%s", Joiner.on("\n").join(errors));
            assertTrue(message, errors.isEmpty());
        }
    }

    /**
     * This test ensures that unit tests are not also running as part of test mapping to avoid
     * double running them.
     */
    @Test
    public void testNotInTestMappingPostsubmit() {
        List<String> errors = getErrors("postsubmit");
        if (!errors.isEmpty()) {
            String message =
                    String.format("Fail configuration check:\n%s", Joiner.on("\n").join(errors));
            assertTrue(message, errors.isEmpty());
        }
    }

    private List<String> getErrors(String group) {
        // We need the test mapping files for this test.
        Assume.assumeNotNull(mBuild.getFile("test_mappings.zip"));

        TestMapping testMapping = new TestMapping();
        Set<TestInfo> testInfosToRun =
                testMapping.getTests(
                        mBuild,
                        group, /* host */
                        true, /* keywords */
                        new HashSet<>(), /* ignoreKeywords */
                        new HashSet<>());

        List<String> errors = new ArrayList<>();
        List<String> configs = new ArrayList<>();
        IDeviceBuildInfo deviceBuildInfo = (IDeviceBuildInfo) mBuild;
        File testsDir = deviceBuildInfo.getTestsDir();
        List<File> extraTestCasesDirs = Arrays.asList(testsDir);
        configs.addAll(ConfigurationUtil.getConfigNamesFromDirs(null, extraTestCasesDirs));

        Map<String, Set<String>> infos = new HashMap<>();
        testInfosToRun.stream().forEach(e -> infos.put(e.getName(), e.getSources()));
        for (String configName : configs) {
            String moduleName = FileUtil.getBaseName(new File(configName).getName());
            if (infos.containsKey(moduleName) && !EXEMPTION_LIST.contains(moduleName)) {
                errors.add(
                        String.format(
                                "Target '%s' is already running in host-unit-tests, it doesn't "
                                        + "need the test mapping config: %s",
                                moduleName, infos.get(moduleName)));
            } else if (infos.containsKey(moduleName) && !FINAL_MODULE_LIST.contains(moduleName)) {
                errors.add(
                        String.format(
                                "Target '%s' is attempted to be added to host test mapping. We do"
                                    + " not currently allow new addition, consider using unit_tests"
                                    + "  setup instead.",
                                moduleName));
            }
        }
        return errors;
    }
}
