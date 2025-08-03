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

package android.security.cts;

import static org.junit.Assert.assertTrue;

import android.cts.host.utils.DeviceJUnit4ClassRunnerWithParameters;
import android.cts.host.utils.DeviceJUnit4Parameterized;
import android.platform.test.annotations.RestrictedBuildTest;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.PropertyUtil;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Neverallow Rules SELinux tests.
 *
 * This is a parametrised test. It extracts the neverallow rules from the
 * platform policy which is embedded in the CTS distribution. Each rule
 * generates its own test to ensure that it is not violated by the device
 * policy.
 *
 * A set of criteria can be used in the platform policy to skip the test
 * depending on the device (e.g., launching version). See sConditions below.
 *
 */
@RunWith(DeviceJUnit4Parameterized.class)
@UseParametersRunnerFactory(DeviceJUnit4ClassRunnerWithParameters.RunnerFactory.class)
public class SELinuxNeverallowRulesTest extends BaseHostJUnit4Test {
    private File sepolicyAnalyze;
    private File devicePolicyFile;
    private File deviceSystemPolicyFile;

    private IBuildInfo mBuild;
    private int mVendorSepolicyVersion = -1;
    private int mSystemSepolicyVersion = -1;

    /**
     * A reference to the device under test.
     */
    private ITestDevice mDevice;

    private static String[] sConditions = {
        "TREBLE_ONLY",
        "COMPATIBLE_PROPERTY_ONLY",
        "LAUNCHING_WITH_R_ONLY",
        "LAUNCHING_WITH_S_ONLY",
    };

    protected static class NeverAllowRule {
        public String mText;
        public boolean fullTrebleOnly;
        public boolean launchingWithROnly;
        public boolean launchingWithSOnly;
        public boolean compatiblePropertyOnly;

        NeverAllowRule(String text, HashMap<String, Integer> conditions) {
            mText = text;
            if (conditions.getOrDefault("TREBLE_ONLY", 0) > 0) {
                fullTrebleOnly = true;
            }
            if (conditions.getOrDefault("COMPATIBLE_PROPERTY_ONLY", 0) > 0) {
                compatiblePropertyOnly = true;
            }
            if (conditions.getOrDefault("LAUNCHING_WITH_R_ONLY", 0) > 0) {
                launchingWithROnly = true;
            }
            if (conditions.getOrDefault("LAUNCHING_WITH_S_ONLY", 0) > 0) {
                launchingWithSOnly = true;
            }
        }

        public String toString() {
            return "Rule [text= " + mText
                   + ", fullTrebleOnly=" + fullTrebleOnly
                   + ", compatiblePropertyOnly=" + compatiblePropertyOnly
                   + ", launchingWithROnly=" + launchingWithROnly
                   + ", launchingWithSOnly=" + launchingWithSOnly
                   + "]";
        }
    }

    public static ArrayList<NeverAllowRule> parsePolicy(String policy) throws Exception {
        String patternConditions = Arrays.stream(sConditions)
                .flatMap(condition -> Stream.of("BEGIN_" + condition, "END_" + condition))
                .collect(Collectors.joining("|"));

        /* Uncomment conditions delimiter lines. */
        Pattern uncommentConditions = Pattern.compile("^\\s*#\\s*(" + patternConditions + ")\\s*$",
                Pattern.MULTILINE);
        Matcher matcher = uncommentConditions.matcher(policy);
        policy = matcher.replaceAll("$1");

        /* Remove all comments. */
        Pattern comments = Pattern.compile("#.*?$", Pattern.MULTILINE);
        matcher = comments.matcher(policy);
        policy = matcher.replaceAll("");

        /* Use a pattern to match all the neverallow rules or a condition. */
        Pattern neverAllowPattern = Pattern.compile(
                "(neverallow\\s[^;]+?;|" + patternConditions + ")",
                Pattern.MULTILINE);

        ArrayList<NeverAllowRule> rules = new ArrayList();
        HashMap<String, Integer> conditions = new HashMap();

        matcher = neverAllowPattern.matcher(policy);
        while (matcher.find()) {
            String rule = matcher.group(1).replace("\n", " ");
            if (rule.startsWith("BEGIN_")) {
                String section = rule.substring(6);
                conditions.put(section, conditions.getOrDefault(section, 0) + 1);
            } else if (rule.startsWith("END_")) {
                String section = rule.substring(4);
                Integer v = conditions.getOrDefault(section, 0);
                assertTrue("Condition " + rule + " found without BEGIN", v > 0);
                conditions.put(section, v - 1);
            } else if (rule.startsWith("neverallow")) {
                rules.add(new NeverAllowRule(rule, conditions));
            } else {
                throw new Exception("Unknown rule: " + rule);
            }
        }

        for (Map.Entry<String, Integer> condition : conditions.entrySet()) {
            if (condition.getValue() != 0) {
                throw new Exception("End of input while inside " + condition.getKey() + " section");
            }
        }

        return rules;
    }

    /**
     * Generate the test parameters based on the embedded policy (general_sepolicy.conf).
     */
    @Parameters
    public static Iterable<NeverAllowRule> generateRules() throws Exception {
        File publicPolicy = SELinuxHostTest.copyResourceToTempFile("/general_sepolicy.conf");
        String policy = Files.readString(publicPolicy.toPath());

        ArrayList<NeverAllowRule> rules = parsePolicy(policy);

        assertTrue("No test generated from the CTS-embedded policy", !rules.isEmpty());
        return rules;
    }

    /* Parameter generated by generateRules() and available to testNeverallowRules */
    @Parameter
    public NeverAllowRule mRule;

    @Before
    public void setUp() throws Exception {
        mDevice = getDevice();
        mBuild = getBuild();

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuild);
        sepolicyAnalyze = SELinuxHostTest.copyResourceToTempFile("/sepolicy-analyze");
        sepolicyAnalyze.setExecutable(true);

        devicePolicyFile = SELinuxHostTest.getDevicePolicyFile(mDevice);

        if (isSepolicySplit()) {
            deviceSystemPolicyFile =
                    SELinuxHostTest.getDeviceSystemPolicyFile(mDevice);

            // Caching this variable to save time.
            if (mVendorSepolicyVersion == -1) {
                mVendorSepolicyVersion =
                        SELinuxHostTest.getVendorSepolicyVersion(mBuild, mDevice);
            }
            if (mSystemSepolicyVersion == -1) {
                mSystemSepolicyVersion =
                        SELinuxHostTest.getSystemSepolicyVersion(mBuild);
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        sepolicyAnalyze.delete();
    }

    private boolean isFullTrebleDevice() throws Exception {
        return SELinuxHostTest.isFullTrebleDevice(mDevice);
    }

    private boolean isDeviceLaunchingWithR() throws Exception {
        return PropertyUtil.getFirstApiLevel(mDevice) > 29;
    }

    private boolean isDeviceLaunchingWithS() throws Exception {
        return PropertyUtil.getFirstApiLevel(mDevice) > 30;
    }

    private boolean isCompatiblePropertyEnforcedDevice() throws Exception {
        return SELinuxHostTest.isCompatiblePropertyEnforcedDevice(mDevice);
    }

    private boolean isSepolicySplit() throws Exception {
        return SELinuxHostTest.isSepolicySplit(mDevice);
    }

    @Test
    @RestrictedBuildTest
    public void testNeverallowRules() throws Exception {

        if ((mRule.fullTrebleOnly) && (!isFullTrebleDevice())) {
            // This test applies only to Treble devices but this device isn't one
            return;
        }
        if ((mRule.launchingWithROnly) && (!isDeviceLaunchingWithR())) {
            // This test applies only to devices launching with R or later but this device isn't one
            return;
        }
        if ((mRule.launchingWithSOnly) && (!isDeviceLaunchingWithS())) {
            // This test applies only to devices launching with S or later but this device isn't one
            return;
        }
        if ((mRule.compatiblePropertyOnly) && (!isCompatiblePropertyEnforcedDevice())) {
            // This test applies only to devices on which compatible property is enforced but this
            // device isn't one
            return;
        }

        // If sepolicy is split and vendor sepolicy version is behind platform's,
        // only test against platform policy.
        File policyFile =
                (isSepolicySplit() && mVendorSepolicyVersion < mSystemSepolicyVersion)
                ? deviceSystemPolicyFile : devicePolicyFile;

        /* run sepolicy-analyze neverallow check on policy file using given neverallow rules */
        ProcessBuilder pb = new ProcessBuilder(sepolicyAnalyze.getAbsolutePath(),
                policyFile.getAbsolutePath(), "neverallow", "-n",
                mRule.mText);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader result = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        StringBuilder errorString = new StringBuilder();
        while ((line = result.readLine()) != null) {
            errorString.append(line);
            errorString.append("\n");
        }
        p.waitFor();
        assertTrue("The following errors were encountered when validating the SELinux"
                   + "neverallow rule:\n" + mRule.mText + "\n" + errorString,
                   errorString.length() == 0);
    }
}
