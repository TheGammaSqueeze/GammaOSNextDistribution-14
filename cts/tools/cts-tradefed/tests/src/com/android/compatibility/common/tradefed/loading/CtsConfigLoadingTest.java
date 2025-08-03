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
package com.android.compatibility.common.tradefed.loading;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.FolderBuildInfo;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.invoker.ExecutionFiles.FilesKey;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.suite.ITestSuite;
import com.android.tradefed.testtype.suite.TestSuiteInfo;
import com.android.tradefed.testtype.suite.params.ModuleParameters;
import com.android.tradefed.util.FileUtil;

import com.google.common.base.Strings;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test CTS specific config requirements.
 */
@RunWith(JUnit4.class)
public class CtsConfigLoadingTest {

    private static final String METADATA_COMPONENT = "component";
    private static final Set<String> KNOWN_COMPONENTS =
            new HashSet<>(
                    Arrays.asList(
                            // modifications to the list below must be reviewed
                            "abuse",
                            "adservices",
                            "art",
                            "auth",
                            "auto",
                            "autofill",
                            "backup",
                            "bionic",
                            "bluetooth",
                            "camera",
                            "contentcapture",
                            "deviceinfo",
                            "deqp",
                            "devtools",
                            "framework",
                            "graphics",
                            "hdmi",
                            "inputmethod",
                            "libcore",
                            "libnativehelper",
                            "location",
                            "media",
                            "metrics",
                            "misc",
                            "mocking",
                            "networking",
                            "neuralnetworks",
                            "packagemanager",
                            "permissions",
                            "print",
                            "renderscript",
                            "security",
                            "statsd",
                            "systems",
                            "sysui",
                            "telecom",
                            "tv",
                            "uitoolkit",
                            "uwb",
                            "vr",
                            "webview",
                            "wifi"));
    private static final Set<String> KNOWN_MISC_MODULES =
            new HashSet<>(
                    Arrays.asList(
                            // Modifications to the list below must be approved by someone in
                            // test/suite_harness/OWNERS.
                            "CtsSliceTestCases.config",
                            "CtsSampleDeviceTestCases.config",
                            "CtsUsbTests.config",
                            "CtsGpuToolsHostTestCases.config",
                            "CtsEdiHostTestCases.config",
                            "CtsClassLoaderFactoryPathClassLoaderTestCases.config",
                            "CtsSampleHostTestCases.config",
                            "CtsHardwareTestCases.config",
                            "CtsAndroidAppTestCases.config",
                            "CtsClassLoaderFactoryInMemoryDexClassLoaderTestCases.config",
                            "CtsAppComponentFactoryTestCases.config",
                            "CtsSeccompHostTestCases.config"));


    /**
     * Families of module parameterization that MUST be specified explicitly in the module
     * AndroidTest.xml.
     */
    private static final Set<String> MANDATORY_PARAMETERS_FAMILY = new HashSet<>();

    static {
        MANDATORY_PARAMETERS_FAMILY.add(ModuleParameters.INSTANT_APP_FAMILY);
        MANDATORY_PARAMETERS_FAMILY.add(ModuleParameters.MULTI_ABI_FAMILY);
        MANDATORY_PARAMETERS_FAMILY.add(ModuleParameters.SECONDARY_USER_FAMILY);
    }

    /**
     * AllowList to start enforcing metadata on modules. No additional entry will be allowed! This
     * is meant to burn down the remaining modules definition.
     */
    private static final Set<String> ALLOWLIST_MODULE_PARAMETERS = new HashSet<>();

    static {
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
            fail(String.format("%s does not exists", testcases));
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

        List<String> missingMandatoryParameters = new ArrayList<>();
        // We expect to be able to load every single config in testcases/
        for (File config : listConfigs) {
            IConfiguration c = ConfigurationFactory.getInstance()
                    .createConfigurationFromArgs(new String[] {config.getAbsolutePath()});

            ConfigurationDescriptor cd = c.getConfigurationDescription();
            Assert.assertNotNull(config + ": configuration descriptor is null", cd);
            List<String> component = cd.getMetaData(METADATA_COMPONENT);
            Assert.assertNotNull(String.format("Missing module metadata field \"component\", "
                    + "please add the following line to your AndroidTest.xml:\n"
                    + "<option name=\"config-descriptor:metadata\" key=\"component\" "
                    + "value=\"...\" />\nwhere \"value\" must be one of: %s\n"
                    + "config: %s", KNOWN_COMPONENTS, config),
                    component);
            Assert.assertEquals(String.format("Module config contains more than one \"component\" "
                    + "metadata field: %s\nconfig: %s", component, config),
                    1, component.size());
            String cmp = component.get(0);
            Assert.assertTrue(String.format("Module config contains unknown \"component\" metadata "
                    + "field \"%s\", supported ones are: %s\nconfig: %s",
                    cmp, KNOWN_COMPONENTS, config), KNOWN_COMPONENTS.contains(cmp));

            if ("misc".equals(cmp)) {
                String configFileName = config.getName();
                Assert.assertTrue(
                        String.format(
                                "Adding new module %s to \"misc\" component is restricted, "
                                        + "please pick a component that your module fits in",
                                configFileName),
                        KNOWN_MISC_MODULES.contains(configFileName));
            }

            // Check that specified parameters are expected
            boolean res =
                    checkModuleParameters(
                            config.getName(), cd.getMetaData(ITestSuite.PARAMETER_KEY));
            if (!res) {
                missingMandatoryParameters.add(config.getName());
            }

            String suiteName = getSuiteName().toLowerCase();
            // Ensure each CTS module is tagged with <option name="test-suite-tag" value="cts" />
            Assert.assertTrue(String.format(
                    "Module config %s does not contains "
                    + "'<option name=\"test-suite-tag\" value=\"%s\" />'", config.getName(), suiteName),
                    cd.getSuiteTags().contains(suiteName));

            // Ensure options have been set
            c.validateOptions();
        }

        // Exempt the allow list
        missingMandatoryParameters.removeAll(ALLOWLIST_MODULE_PARAMETERS);
        // Ensure the mandatory fields are filled
        if (!missingMandatoryParameters.isEmpty()) {
            String msg =
                    String.format(
                            "The following %s modules are missing some of the mandatory "
                                    + "parameters [instant_app, not_instant_app, "
                                    + "multi_abi, not_multi_abi, "
                                    + "secondary_user, not_secondary_user]: '%s'",
                            missingMandatoryParameters.size(), missingMandatoryParameters);
            throw new ConfigurationException(msg);
        }
    }

    /** Test that all parameter metadata can be resolved. */
    private boolean checkModuleParameters(String configName, List<String> parameters)
            throws ConfigurationException {
        if (parameters == null) {
            return false;
        }
        Map<String, Boolean> families = createFamilyCheckMap();
        for (String param : parameters) {
            try {
                ModuleParameters p = ModuleParameters.valueOf(param.toUpperCase());
                if (families.containsKey(p.getFamily())) {
                    families.put(p.getFamily(), true);
                }
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException(
                        String.format("Config: %s includes an unknown parameter '%s'.",
                                configName, param));
            }
        }
        if (families.containsValue(false)) {
            return false;
        }
        return true;
    }

    private Map<String, Boolean> createFamilyCheckMap() {
        Map<String, Boolean> families = new HashMap<>();
        for (String family : MANDATORY_PARAMETERS_FAMILY) {
            families.put(family, false);
        }
        return families;
    }

    private String getSuiteName() {
        return TestSuiteInfo.getInstance().getName();
    }
}
