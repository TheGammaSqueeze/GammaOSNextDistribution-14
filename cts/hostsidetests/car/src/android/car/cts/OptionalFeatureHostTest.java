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

package android.car.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.junit.Assume.assumeThat;

import android.car.feature.Flags;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.car.CarFeatureControlDumpProto;
import com.android.compatibility.common.util.ProtoUtils;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Check Optional Feature related car configs.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class OptionalFeatureHostTest extends CarHostJUnit4TestCase {

    private static final String[] MANDATORY_FEATURES = {
            "android.car.input",
            "app_focus",
            "audio",
            "cabin",
            "car_bluetooth",
            "car_bugreport",
            "car_device_policy_service",
            "car_media",
            "car_navigation_service",
            "car_occupant_zone_service",
            "car_user_service",
            "car_watchdog",
            "drivingstate",
            "hvac",
            "info",
            "package",
            "power",
            "projection",
            "property",
            "sensor",
            "uxrestriction",
            "vendor_extension"
    };

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    /**
     * Partners can use the same system image for multiple product configs with variation in
     * optional feature support. But CTS should run in a config where VHAL
     * DISABLED_OPTIONAL_FEATURES property does not disable any optional features.
     * Use text dump to get the number of features.
     */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testNoDisabledFeaturesFromVHAL_textDump() throws Exception {
        List<String> features = findFeatureListFromCarServiceDump("mDisabledFeaturesFromVhal");
        assertWithMessage("Number of features disabled from VHAL").that(features).isEmpty();
    }

    /**
     * Partners can use the same system image for multiple product configs with variation in
     * optional feature support. But CTS should run in a config where VHAL
     * DISABLED_OPTIONAL_FEATURES property does not disable any optional features.
     * Use proto dump to get the number of features.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testNoDisabledFeaturesFromVHAL_protoDump() throws Exception {
        CarFeatureControlDumpProto featureControlDump = getFeatureControlDumpProto();

        int numDisabledFeaturesFromVhal = featureControlDump.getDisabledFeaturesFromVhalCount();

        assertWithMessage("Number of features disabled from VHAL").that(
                numDisabledFeaturesFromVhal).isEqualTo(0);
    }

    /**
     * Experimental features cannot be shipped. There should be no experimental features available
     * in the device. Use text dump to get the number of available experimental features.
     */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testNoExperimentalFeatures_textDump() throws Exception {
        // experimental feature disabled in user build
        assumeUserBuild();
        List<String> features = findFeatureListFromCarServiceDump("mAvailableExperimentalFeatures");
        assertWithMessage("Number of experimental features available").that(features).isEmpty();
    }

    /**
     * Experimental features cannot be shipped. There should be no experimental features available
     * in the device. Use proto dump to get the number of available experimental features.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testNoExperimentalFeatures_protoDump() throws Exception {
        // experimental feature disabled in user build
        assumeUserBuild();
        CarFeatureControlDumpProto featureControlDump = getFeatureControlDumpProto();

        int numExperimentalFeatures = featureControlDump.getAvailableExperimentalFeaturesCount();

        assertWithMessage("Number of experimental features available").that(
                numExperimentalFeatures).isEqualTo(0);
    }

    /**
     * Experimental car service package should not exist in the device.
     */
    @Test
    public void testNoExperimentalCarServicePackage() throws Exception {
        // experimental feature disabled in user build
        assumeUserBuild();
        // Only check for user 0 as experimental car service is launched as user 0.
        String output = getDevice().executeShellCommand(
                "pm list package com.android.experimentalcar").strip();
        assertWithMessage("Experimental car service package").that(output).isEmpty();
    }

    /**
     * All optional features declared from {@code mDefaultEnabledFeaturesFromConfig} should be
     * enabled for CTS test. Use text dump to get the optional features list.
     */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testAllOptionalFeaturesEnabled_textDump() throws Exception {
        List<String> enabledFeatures = findFeatureListFromCarServiceDump("mEnabledFeatures");
        List<String> optionalFeaturesFromConfig = findFeatureListFromCarServiceDump(
                "mDefaultEnabledFeaturesFromConfig");
        assertWithMessage("Missing optional features from config").that(
                enabledFeatures).containsAtLeastElementsIn(optionalFeaturesFromConfig);
    }

    /**
     * All optional features declared from {@code mDefaultEnabledFeaturesFromConfig} should be
     * enabled for CTS test. Use proto dump to get the optional features list.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testAllOptionalFeaturesEnabled_protoDump() throws Exception {
        List<String> enabledFeatures = getEnabledFeatures();
        CarFeatureControlDumpProto featureControlDump = getFeatureControlDumpProto();
        List<String> optionalFeaturesFromConfig =
                featureControlDump.getDefaultEnabledFeaturesFromConfigList();
        assertWithMessage("Missing optional features from config").that(
                enabledFeatures).containsAtLeastElementsIn(optionalFeaturesFromConfig);
    }

    /**
     * Confirms that selected mandatory features are enabled.
     */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testAllMandatoryFeaturesEnabled_textDump() throws Exception {
        List<String> enabledFeatures = findFeatureListFromCarServiceDump("mEnabledFeatures");

        assertWithMessage("Missing mandatory features").that(
                enabledFeatures).containsAtLeastElementsIn(MANDATORY_FEATURES);
    }

    /**
     * Confirms that selected mandatory features are enabled.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testAllMandatoryFeaturesEnabled_protoDump() throws Exception {
        List<String> enabledFeatures = getEnabledFeatures();

        assertWithMessage("Missing mandatory features").that(
                enabledFeatures).containsAtLeastElementsIn(MANDATORY_FEATURES);
    }

    /**
     * Confirms that adb command cannot drop feature.
     */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testNoFeatureChangeAfterRebootForAdbCommand_textDump() throws Exception {
        List<String> enabledFeaturesOrig = findFeatureListFromCarServiceDump("mEnabledFeatures");
        disableFeatures(enabledFeaturesOrig);

        getDevice().reboot();
        List<String> enabledFeaturesAfterReboot = findFeatureListFromCarServiceDump(
                "mEnabledFeatures");

        assertWithMessage(
                "Comparison between enabled features before and after device reboot").that(
                        enabledFeaturesOrig).containsExactlyElementsIn(enabledFeaturesAfterReboot);
    }

    /**
     * Confirms that adb command cannot drop feature.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testNoFeatureChangeAfterRebootForAdbCommand_protoDump() throws Exception {
        List<String> enabledFeaturesOrig = getEnabledFeatures();
        disableFeatures(enabledFeaturesOrig);

        getDevice().reboot();
        List<String> enabledFeaturesAfterReboot = getEnabledFeatures();

        assertWithMessage(
                "Comparison between enabled features before and after device reboot").that(
                enabledFeaturesOrig).containsExactlyElementsIn(enabledFeaturesAfterReboot);
    }

    private List<String> findFeatureListFromCarServiceDump(String featureDumpName)
            throws Exception {
        String output = getDevice().executeShellCommand(
                "dumpsys car_service --services CarFeatureController");
        Pattern pattern = Pattern.compile(featureDumpName + ":\\[(.*)\\]");
        Matcher m = pattern.matcher(output);
        if (!m.find()) {
            return Collections.EMPTY_LIST;
        }
        String[] features = m.group(1).split(", ");
        ArrayList<String> featureList = new ArrayList<>(features.length);
        for (String feature : features) {
            if (feature.isEmpty()) {
                continue;
            }
            featureList.add(feature);
        }
        return featureList;
    }

    private CarFeatureControlDumpProto getFeatureControlDumpProto() throws Exception {
        return ProtoUtils.getProto(getDevice(), CarFeatureControlDumpProto.parser(),
                "dumpsys car_service --services CarFeatureController --proto");
    }

    private List<String> getEnabledFeatures() throws Exception {
        CarFeatureControlDumpProto featureControlDump = getFeatureControlDumpProto();
        return featureControlDump.getEnabledFeaturesList();
    }

    private void disableFeatures(List<String> enabledFeaturesOrig) throws Exception {
        for (String feature : enabledFeaturesOrig) {
            getDevice().executeShellCommand("cmd car_service disable-feature %s" + feature);
        }
    }

    private void assumeUserBuild() throws Exception {
        assumeThat(getDevice().getBuildFlavor(), endsWith("-user"));
    }
}
