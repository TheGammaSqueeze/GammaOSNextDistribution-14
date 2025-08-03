/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.compatibility.common.tradefed.loading;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.targetprep.ApkInstaller;
import com.android.compatibility.common.tradefed.targetprep.PreconditionPreparer;
import com.android.compatibility.common.tradefed.testtype.JarHostTest;
import com.android.tradefed.build.FolderBuildInfo;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.invoker.ExecutionFiles.FilesKey;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.shard.token.TokenProperty;
import com.android.tradefed.targetprep.DeviceSetup;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.PythonVirtualenvPreparer;
import com.android.tradefed.testtype.AndroidJUnitTest;
import com.android.tradefed.testtype.GTest;
import com.android.tradefed.testtype.HostTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.testtype.suite.ITestSuite;
import com.android.tradefed.testtype.suite.TestSuiteInfo;
import com.android.tradefed.testtype.suite.ValidateSuiteConfigHelper;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ModuleTestTypeUtil;

import com.google.common.base.Strings;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Test that configuration in *TS can load and have expected properties.
 */
@RunWith(JUnit4.class)
public class CommonConfigLoadingTest {

    private static final Pattern TODO_BUG_PATTERN = Pattern.compile(".*TODO\\(b/[0-9]+\\).*", Pattern.DOTALL);

    /**
     * List of the officially supported runners in CTS, they meet all the interfaces criteria as
     * well as support sharding very well. Any new addition should go through a review.
     */
    private static final Set<String> SUPPORTED_SUITE_TEST_TYPE = new HashSet<>(Arrays.asList(
            // Suite runners
            "com.android.compatibility.common.tradefed.testtype.JarHostTest",
            "com.android.compatibility.testtype.DalvikTest",
            "com.android.compatibility.testtype.LibcoreTest",
            "com.drawelements.deqp.runner.DeqpTestRunner",
            // Tradefed runners
            "com.android.tradefed.testtype.AndroidJUnitTest",
            "com.android.tradefed.testtype.ArtRunTest",
            "com.android.tradefed.testtype.HostTest",
            "com.android.tradefed.testtype.GTest",
            "com.android.tradefed.testtype.mobly.MoblyBinaryHostTest",
            "com.android.tradefed.testtype.pandora.PtsBotTest",
            // VTS specific runners
            "com.android.tradefed.testtype.binary.KernelTargetTest",
            "com.android.tradefed.testtype.python.PythonBinaryHostTest",
            "com.android.tradefed.testtype.binary.ExecutableTargetTest",
            "com.android.tradefed.testtype.binary.ExecutableHostTest",
            "com.android.tradefed.testtype.rust.RustBinaryTest"
    ));

    /**
     * In Most cases we impose the usage of the AndroidJUnitRunner because it supports all the
     * features required (filtering, sharding, etc.). We do not typically expect people to need a
     * different runner.
     */
    private static final Set<String> ALLOWED_INSTRUMENTATION_RUNNER_NAME = new HashSet<>();
    static {
        ALLOWED_INSTRUMENTATION_RUNNER_NAME.add("android.support.test.runner.AndroidJUnitRunner");
        ALLOWED_INSTRUMENTATION_RUNNER_NAME.add("androidx.test.runner.AndroidJUnitRunner");
    }
    private static final Set<String> RUNNER_EXCEPTION = new HashSet<>();
    static {
        // Used for a bunch of system-api cts tests
        RUNNER_EXCEPTION.add("repackaged.android.test.InstrumentationTestRunner");
        // Used by a UiRendering scenario where an activity is persisted between tests
        RUNNER_EXCEPTION.add("android.uirendering.cts.runner.UiRenderingRunner");
        // Used by a Widget scenario where an activity is persisted between tests
        RUNNER_EXCEPTION.add("android.widget.cts.runner.WidgetRunner");
        // Used by a text scenario where an activity is persisted between tests
        RUNNER_EXCEPTION.add("android.text.cts.runner.CtsTextRunner");
        // Used to avoid crashing runner on -eng build due to Log.wtf() - b/216648699
        RUNNER_EXCEPTION.add("com.android.server.uwb.CustomTestRunner");
        RUNNER_EXCEPTION.add("com.android.server.wifi.CustomTestRunner");
        // HealthConnect APK use Hilt for dependency injection. For test setup it needs
        // to replace the main Application class with Test Application so Hilt can swap
        // dependencies for testing.
        RUNNER_EXCEPTION.add("com.android.healthconnect.controller.tests.HiltTestRunner");
    }

    /**
     * Test that configuration shipped in Tradefed can be parsed.
     * -> Exclude deprecated ApkInstaller.
     * -> Check if host-side tests are non empty.
     */
    @Test
    public void testConfigurationLoad() throws Exception {
        String rootVar = String.format("%s_ROOT", getSuiteName().toUpperCase());
        String suiteRoot = System.getProperty(rootVar);
        if (Strings.isNullOrEmpty(suiteRoot)) {
            fail(String.format("Should run within a suite context: %s doesn't exist", rootVar));
        }
        File testcases = new File(suiteRoot, String.format("/android-%s/testcases/", getSuiteName().toLowerCase()));
        if (!testcases.exists()) {
            fail(String.format("%s does not exist", testcases));
            return;
        }
        Set<File> listConfigs = FileUtil.findFilesObject(testcases, ".*\\.config");
        assertTrue(listConfigs.size() > 0);
        // Create a FolderBuildInfo to similate the CompatibilityBuildProvider
        FolderBuildInfo stubFolder = new FolderBuildInfo("-1", "-1");
        stubFolder.setRootDir(new File(suiteRoot));
        stubFolder.addBuildAttribute(CompatibilityBuildHelper.SUITE_NAME, getSuiteName().toUpperCase());
        stubFolder.addBuildAttribute("ROOT_DIR", suiteRoot);
        TestInformation stubTestInfo = TestInformation.newBuilder()
                .setInvocationContext(new InvocationContext()).build();
        stubTestInfo.executionFiles().put(FilesKey.TESTS_DIRECTORY, new File(suiteRoot));

        // We expect to be able to load every single config in testcases/
        for (File config : listConfigs) {
            IConfiguration c = ConfigurationFactory.getInstance()
                    .createConfigurationFromArgs(new String[] {config.getAbsolutePath()});
            if (c.getDeviceConfig().size() > 2) {
                throw new ConfigurationException(String.format("%s declares more than 2 devices.", config));
            }
            int deviceCount = 0;
            for (IDeviceConfiguration dConfig : c.getDeviceConfig()) {
                // Ensure the deprecated ApkInstaller is not used anymore.
                for (ITargetPreparer prep : dConfig.getTargetPreparers()) {
                    if (prep.getClass().isAssignableFrom(ApkInstaller.class)) {
                        throw new ConfigurationException(
                                String.format("%s: Use com.android.tradefed.targetprep.suite."
                                        + "SuiteApkInstaller instead of com.android.compatibility."
                                        + "common.tradefed.targetprep.ApkInstaller, options will be "
                                        + "the same.", config));
                    }
                    if (prep.getClass().isAssignableFrom(PreconditionPreparer.class)) {
                        throw new ConfigurationException(
                                String.format(
                                        "%s: includes a PreconditionPreparer (%s) which is not "
                                                + "allowed in modules.",
                                        config.getName(), prep.getClass()));
                    }
                    if (prep.getClass().isAssignableFrom(DeviceSetup.class)) {
                       DeviceSetup deviceSetup = (DeviceSetup) prep;
                       if (!deviceSetup.isForceSkipSystemProps()) {
                           throw new ConfigurationException(
                                   String.format("%s: %s needs to be configured with "
                                           + "<option name=\"force-skip-system-props\" "
                                           + "value=\"true\" /> in *TS.",
                                                 config.getName(), prep.getClass()));
                       }
                    }
                    if (prep.getClass().isAssignableFrom(PythonVirtualenvPreparer.class)) {
                        // Ensure each modules has a tracking bug to be imported.
                        checkPythonModules(config, deviceCount);
                    }
                }
                deviceCount++;
            }
            // We can ensure that Host side tests are not empty.
            for (IRemoteTest test : c.getTests()) {
                // Check that all the tests runners are well supported.
                if (!SUPPORTED_SUITE_TEST_TYPE.contains(test.getClass().getCanonicalName())) {
                    throw new ConfigurationException(
                            String.format(
                                    "testtype %s is not officially supported by *TS. "
                                            + "The supported ones are: %s",
                                    test.getClass().getCanonicalName(), SUPPORTED_SUITE_TEST_TYPE));
                }
                if (test instanceof HostTest) {
                    HostTest hostTest = (HostTest) test;
                    // We inject a made up folder so that it can find the tests.
                    hostTest.setBuild(stubFolder);
                    hostTest.setTestInformation(stubTestInfo);
                    int testCount = hostTest.countTestCases();
                    if (testCount == 0) {
                        throw new ConfigurationException(
                                String.format("%s: %s reports 0 test cases.",
                                        config.getName(), test));
                    }
                }
                if (test instanceof GTest) {
                    if (((GTest) test).isRebootBeforeTestEnabled()) {
                        throw new ConfigurationException(String.format(
                                "%s: instead of reboot-before-test use a RebootTargetPreparer "
                                + "which is more optimized during sharding.", config.getName()));
                    }
                }
                // Tests are expected to implement that interface.
                if (!(test instanceof ITestFilterReceiver)) {
                    throw new IllegalArgumentException(String.format(
                            "Test in module %s must implement ITestFilterReceiver.",
                            config.getName()));
                }
                // Ensure that the device runner is the AJUR one if explicitly specified.
                if (test instanceof AndroidJUnitTest) {
                    AndroidJUnitTest instru = (AndroidJUnitTest) test;
                    if (instru.getRunnerName() != null &&
                            !ALLOWED_INSTRUMENTATION_RUNNER_NAME.contains(instru.getRunnerName())) {
                        // Some runner are exempt
                        if (!RUNNER_EXCEPTION.contains(instru.getRunnerName())) {
                            throw new ConfigurationException(
                                    String.format("%s: uses '%s' instead of on of '%s' that are "
                                            + "expected", config.getName(), instru.getRunnerName(),
                                            ALLOWED_INSTRUMENTATION_RUNNER_NAME));
                        }
                    }
                }
            }

            ConfigurationDescriptor cd = c.getConfigurationDescription();
            Assert.assertNotNull(config + ": configuration descriptor is null", cd);

            // Check that specified tokens are expected
            checkTokens(config.getName(), cd.getMetaData(ITestSuite.TOKEN_KEY));

            // Check not-shardable: JarHostTest cannot create empty shards so it should never need
            // to be not-shardable.
            if (cd.isNotShardable()) {
                for (IRemoteTest test : c.getTests()) {
                    if (test.getClass().isAssignableFrom(JarHostTest.class)) {
                        throw new ConfigurationException(
                                String.format("config: %s. JarHostTest does not need the "
                                    + "not-shardable option.", config.getName()));
                    }
                }
            }
            // Ensure options have been set
            c.validateOptions();

            // Check that no performance test module is included
            if (ModuleTestTypeUtil.isPerformanceModule(c)) {
                throw new ConfigurationException(
                        String.format("config: %s. Performance test modules are not allowed in xTS",
                                config.getName()));
            }

            // Vailidate the module config doesn't contain inclusion tags
            ValidateSuiteConfigHelper.validateConfigFile(config);
        }
    }

    /** Test that all tokens can be resolved. */
    private void checkTokens(String configName, List<String> tokens) throws ConfigurationException {
        if (tokens == null) {
            return;
        }
        for (String token : tokens) {
            try {
                TokenProperty.valueOf(token.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException(
                        String.format(
                                "Config: %s includes an unknown token '%s'.", configName, token));
            }
        }
    }

    /**
     * For each usage of python virtualenv preparer, make sure we have tracking bugs to import as
     * source the python libs.
     */
    private void checkPythonModules(File config, int deviceCount)
            throws IOException, ConfigurationException {
        if (deviceCount != 0) {
            throw new ConfigurationException(
                    String.format("%s: PythonVirtualenvPreparer should only be declared for "
                            + "the first <device> tag in the config", config.getName()));
        }
        if (!TODO_BUG_PATTERN.matcher(FileUtil.readStringFromFile(config)).matches()) {
            throw new ConfigurationException(
                    String.format("%s: Contains some virtualenv python lib usage but no "
                            + "tracking bug to import them as source.", config.getName()));
        }
    }

    private String getSuiteName() {
        return TestSuiteInfo.getInstance().getName();
    }
}
