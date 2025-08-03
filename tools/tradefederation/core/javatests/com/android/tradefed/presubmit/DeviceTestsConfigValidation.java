/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.ConfigurationUtil;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.suite.ITestSuite;
import com.android.tradefed.testtype.suite.ValidateSuiteConfigHelper;

import com.google.common.base.Joiner;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Validation tests to run against the configuration in device-tests.zip to ensure they can all
 * parse.
 *
 * <p>Do not add to UnitTests.java. This is meant to run standalone.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class DeviceTestsConfigValidation implements IBuildReceiver {

    @Option(
            name = "config-extension",
            description = "The expected extension from configuration to check.")
    private String mConfigExtension = "config";

    @Option(
            name = "disallowed-test-type",
            description = "The disallowed test type for configs in device-tests.zip")
    private List<String> mDisallowedTestTypes = new ArrayList<>();

    private IBuildInfo mBuild;

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
        List<File> configs = new ArrayList<>();
        IDeviceBuildInfo deviceBuildInfo = (IDeviceBuildInfo) mBuild;
        File testsDir = deviceBuildInfo.getTestsDir();
        List<File> extraTestCasesDirs = Arrays.asList(testsDir);
        String configPattern = ".*\\." + mConfigExtension + "$";
        configs.addAll(
                ConfigurationUtil.getConfigNamesFileFromDirs(
                        null, extraTestCasesDirs, Arrays.asList(configPattern)));
        for (File config : configs) {
            try {
                IConfiguration c =
                        configFactory.createConfigurationFromArgs(
                                new String[] {config.getAbsolutePath()});
                // All configurations in device-tests.zip should be module since they are generated
                // from AndroidTest.xml
                ValidateSuiteConfigHelper.validateConfig(c);

                for (IDeviceConfiguration dConfig : c.getDeviceConfig()) {
                    GeneralTestsConfigValidation.validatePreparers(
                            c, config, dConfig.getTargetPreparers());
                }
                // Check that all the tests runners are well supported.
                GeneralTestsConfigValidation.checkRunners(c.getTests(), "device-tests");

                ConfigurationDescriptor cd = c.getConfigurationDescription();
                GeneralTestsConfigValidation.checkModuleParameters(
                        c.getName(), cd.getMetaData(ITestSuite.PARAMETER_KEY));

                // Check for disallowed test types
                GeneralTestsConfigValidation.checkDisallowedTestType(c, mDisallowedTestTypes);

                // Add more checks if necessary
            } catch (ConfigurationException e) {
                errors.add(String.format("\t%s: %s", config.getName(), e.getMessage()));
            }
        }

        // If any errors report them in a final exception.
        if (!errors.isEmpty()) {
            throw new ConfigurationException(
                    String.format("Fail configuration check:\n%s", Joiner.on("\n").join(errors)));
        }
    }
}
