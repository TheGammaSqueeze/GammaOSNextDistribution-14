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

package com.android.car.power;

import static android.car.hardware.power.PowerComponent.AUDIO;
import static android.car.hardware.power.PowerComponent.BLUETOOTH;
import static android.car.hardware.power.PowerComponent.CELLULAR;
import static android.car.hardware.power.PowerComponent.CPU;
import static android.car.hardware.power.PowerComponent.DISPLAY;
import static android.car.hardware.power.PowerComponent.ETHERNET;
import static android.car.hardware.power.PowerComponent.INPUT;
import static android.car.hardware.power.PowerComponent.LOCATION;
import static android.car.hardware.power.PowerComponent.MEDIA;
import static android.car.hardware.power.PowerComponent.MICROPHONE;
import static android.car.hardware.power.PowerComponent.NFC;
import static android.car.hardware.power.PowerComponent.PROJECTION;
import static android.car.hardware.power.PowerComponent.TRUSTED_DEVICE_DETECTION;
import static android.car.hardware.power.PowerComponent.VISUAL_INTERACTION;
import static android.car.hardware.power.PowerComponent.VOICE_INTERACTION;
import static android.car.hardware.power.PowerComponent.WIFI;

import static com.android.car.test.power.CarPowerPolicyUtil.assertPolicyIdentical;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.feature.FakeFeatureFlagsImpl;
import android.car.feature.Flags;
import android.car.hardware.power.CarPowerPolicy;
import android.content.res.Resources;
import android.hardware.automotive.vehicle.VehicleApPowerStateReport;
import android.platform.test.annotations.RequiresFlagsDisabled;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.carservice_unittest.R;

import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;

public final class PolicyReaderUnitTest {
    private static final String TAG = PolicyReaderUnitTest.class.getSimpleName();

    private static final String POLICY_ID_NOT_EXIST = "policy_id_not_exist";
    private static final String POLICY_ID_OTHER_OFF = "policy_id_other_off";
    private static final String POLICY_ID_OTHER_ON = "policy_id_other_on";
    private static final String POLICY_ID_OTHER_UNTOUCHED = "policy_id_other_untouched";
    private static final String POLICY_ID_OTHER_NONE = "policy_id_other_none";
    private static final String POLICY_GROUP_ID_NOT_EXIST = "policy_group_id_not_exist";
    private static final String POLICY_GROUP_ID_BASIC = "basic_policy_group";
    private static final String POLICY_GROUP_ID_NO_DEFAULT_POLICY = "no_default_policy_group";
    private static final String POLICY_GROUP_ID_MIXED = "mixed_policy_group";
    private static final String NO_USER_INTERACTION_POLICY_ID =
            "system_power_policy_no_user_interaction";
    private static final String SUSPEND_PREP_POLICY_ID = "system_power_policy_suspend_prep";
    private static final String ALL_ON_POLICY_ID = "system_power_policy_all_on";
    private static final String INITIAL_ON_POLICY_ID = "system_power_policy_initial_on";
    private static final String POLICY_ID_CUSTOM_OTHER_OFF = "policy_id_custom_other_off";
    private static final int CUSTOM_COMPONENT_1000 = 1000;
    private static final int CUSTOM_COMPONENT_AUX_INPUT = 1002;
    private static final int CUSTOM_COMPONENT_SPECIAL_SENSOR = 1003;

    private static final CarPowerPolicy POLICY_OTHER_OFF = new CarPowerPolicy(POLICY_ID_OTHER_OFF,
            new int[]{WIFI},
            new int[]{AUDIO, MEDIA, DISPLAY, BLUETOOTH, CELLULAR, ETHERNET, PROJECTION, NFC, INPUT,
                    VOICE_INTERACTION, VISUAL_INTERACTION, TRUSTED_DEVICE_DETECTION, LOCATION,
                    MICROPHONE, CPU});
    private static final CarPowerPolicy POLICY_OTHER_ON = new CarPowerPolicy(POLICY_ID_OTHER_ON,
            new int[]{MEDIA, DISPLAY, BLUETOOTH, WIFI, CELLULAR, ETHERNET, PROJECTION, NFC, INPUT,
                    LOCATION, MICROPHONE, CPU},
            new int[]{AUDIO, VOICE_INTERACTION, VISUAL_INTERACTION, TRUSTED_DEVICE_DETECTION});
    private static final CarPowerPolicy POLICY_OTHER_UNTOUCHED =
            new CarPowerPolicy(POLICY_ID_OTHER_UNTOUCHED,
                    new int[]{AUDIO, DISPLAY, BLUETOOTH, WIFI, VOICE_INTERACTION,
                            VISUAL_INTERACTION, TRUSTED_DEVICE_DETECTION},
                    new int[]{});
    private static final CarPowerPolicy POLICY_OTHER_NONE = new CarPowerPolicy(POLICY_ID_OTHER_NONE,
            new int[]{WIFI},
            new int[]{AUDIO, VOICE_INTERACTION, VISUAL_INTERACTION, TRUSTED_DEVICE_DETECTION});
    private static final CarPowerPolicy SYSTEM_POWER_POLICY_NO_USER_INTERACTION =
            new CarPowerPolicy(NO_USER_INTERACTION_POLICY_ID,
                    new int[]{WIFI, CELLULAR, ETHERNET, TRUSTED_DEVICE_DETECTION, CPU},
                    new int[]{AUDIO, MEDIA, DISPLAY, BLUETOOTH, PROJECTION, NFC, INPUT,
                            VOICE_INTERACTION, VISUAL_INTERACTION, LOCATION, MICROPHONE});
    private static final CarPowerPolicy SYSTEM_POWER_POLICY_MODIFIED =
            new CarPowerPolicy(NO_USER_INTERACTION_POLICY_ID,
                    new int[]{BLUETOOTH, WIFI, CELLULAR, ETHERNET, NFC, CPU},
                    new int[]{AUDIO, MEDIA, DISPLAY, PROJECTION, INPUT, VOICE_INTERACTION,
                            VISUAL_INTERACTION, TRUSTED_DEVICE_DETECTION, LOCATION, MICROPHONE});
    private static final CarPowerPolicy SYSTEM_POWER_POLICY_SUSPEND_PREP =
            new CarPowerPolicy(SUSPEND_PREP_POLICY_ID,
                    new int[]{},
                    new int[]{AUDIO, BLUETOOTH, WIFI, LOCATION, MICROPHONE, CPU});

    private static final CarPowerPolicy SYSTEM_POWER_POLICY_CUSTOM_COMPONENTS = new CarPowerPolicy(
            NO_USER_INTERACTION_POLICY_ID,
            new int[]{BLUETOOTH, WIFI, CELLULAR, ETHERNET, NFC, CPU},
            new int[]{AUDIO, MEDIA, DISPLAY, PROJECTION, INPUT, VOICE_INTERACTION,
                    VISUAL_INTERACTION, TRUSTED_DEVICE_DETECTION, LOCATION, MICROPHONE,
                    CUSTOM_COMPONENT_SPECIAL_SENSOR});

    private static final CarPowerPolicy POLICY_CUSTOM_OTHER_OFF = new CarPowerPolicy(
            POLICY_ID_CUSTOM_OTHER_OFF,
            new int[]{WIFI, CUSTOM_COMPONENT_1000},
            new int[]{AUDIO, MEDIA, DISPLAY, BLUETOOTH, CELLULAR, ETHERNET, PROJECTION, NFC, INPUT,
                    VOICE_INTERACTION, VISUAL_INTERACTION, TRUSTED_DEVICE_DETECTION, LOCATION,
                    MICROPHONE, CPU, CUSTOM_COMPONENT_SPECIAL_SENSOR, CUSTOM_COMPONENT_AUX_INPUT});
    private static final CarPowerPolicy POLICY_OTHER_OFF_WITH_OEM_COMPONENTS = new CarPowerPolicy(
            POLICY_ID_OTHER_OFF,
            new int[]{WIFI, CUSTOM_COMPONENT_AUX_INPUT},
            new int[]{AUDIO, MEDIA, DISPLAY, BLUETOOTH, CELLULAR, ETHERNET, PROJECTION, NFC, INPUT,
                    VOICE_INTERACTION, VISUAL_INTERACTION, TRUSTED_DEVICE_DETECTION, LOCATION,
                    MICROPHONE, CPU, CUSTOM_COMPONENT_1000, CUSTOM_COMPONENT_SPECIAL_SENSOR});
    private static final CarPowerPolicy POLICY_OTHER_ON_WITH_OEM_COMPONENTS = new CarPowerPolicy(
            POLICY_ID_OTHER_ON,
            new int[]{MEDIA, DISPLAY, BLUETOOTH, WIFI, CELLULAR, ETHERNET, PROJECTION, NFC, INPUT,
                    LOCATION, MICROPHONE, CPU, CUSTOM_COMPONENT_1000,
                    CUSTOM_COMPONENT_SPECIAL_SENSOR},
            new int[]{AUDIO, VOICE_INTERACTION, VISUAL_INTERACTION, TRUSTED_DEVICE_DETECTION,
                    CUSTOM_COMPONENT_AUX_INPUT});
    private static final CarPowerPolicy POLICY_OTHER_UNTOUCHED_WITH_OEM_COMPONENTS =
            new CarPowerPolicy(POLICY_ID_OTHER_UNTOUCHED,
                    new int[]{AUDIO, DISPLAY, BLUETOOTH, WIFI, VOICE_INTERACTION,
                            VISUAL_INTERACTION, TRUSTED_DEVICE_DETECTION,
                            CUSTOM_COMPONENT_AUX_INPUT},
                    new int[]{});

    private final Resources mResources =
            InstrumentationRegistry.getInstrumentation().getTargetContext().getResources();

    private final PolicyReader mPolicyReader = new PolicyReader();
    private final FakeFeatureFlagsImpl mFeatureFlags = new FakeFeatureFlagsImpl();

    @Before
    public void setUp() throws Exception {
        mFeatureFlags.setFlag(Flags.FLAG_CAR_POWER_POLICY_REFACTORING, false);
        mPolicyReader.init(mFeatureFlags);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testSystemPowerPolicyNoUserInteraction() throws Exception {
        assertSystemPowerPolicy(NO_USER_INTERACTION_POLICY_ID,
                SYSTEM_POWER_POLICY_NO_USER_INTERACTION);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testSystemPowerPolicySuspendPrep() throws Exception {
        assertSystemPowerPolicy(SUSPEND_PREP_POLICY_ID, SYSTEM_POWER_POLICY_SUSPEND_PREP);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testValidXml_powerPolicy() throws Exception {
        readPowerPolicyXml(R.raw.valid_power_policy);

        assertValidPolicyPart();
        assertSystemPowerPolicy(NO_USER_INTERACTION_POLICY_ID, SYSTEM_POWER_POLICY_MODIFIED);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testValidXml_noPowerPolicyGroups() throws Exception {
        readPowerPolicyXml(R.raw.valid_power_policy_no_power_policy_groups);

        assertValidPolicyPart();
        assertNoPolicyGroupPart();
        assertSystemPowerPolicy(NO_USER_INTERACTION_POLICY_ID, SYSTEM_POWER_POLICY_MODIFIED);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testValidXml_noSystemPowerPolicy() throws Exception {
        readPowerPolicyXml(R.raw.valid_power_policy_no_system_power_policy);

        assertValidPolicyPart();
        assertValidPolicyGroupPart();
        assertSystemPowerPolicy(NO_USER_INTERACTION_POLICY_ID,
                SYSTEM_POWER_POLICY_NO_USER_INTERACTION);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testValidXml_policiesOnly() throws Exception {
        readPowerPolicyXml(R.raw.valid_power_policy_policies_only);

        assertValidPolicyPart();
        assertNoPolicyGroupPart();
        assertSystemPowerPolicy(NO_USER_INTERACTION_POLICY_ID,
                SYSTEM_POWER_POLICY_NO_USER_INTERACTION);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testValidXml_systemPowerPolicyOnly() throws Exception {
        readPowerPolicyXml(R.raw.valid_power_policy_system_power_policy_only);

        assertNoPolicyPart();
        assertNoPolicyGroupPart();
        assertSystemPowerPolicy(NO_USER_INTERACTION_POLICY_ID, SYSTEM_POWER_POLICY_MODIFIED);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testInvalidXml_incorrectGroupState() throws Exception {
        assertInvalidXml(R.raw.invalid_power_policy_group_incorrect_state);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testInvalidXml_missingGroupPolicy() throws Exception {
        assertInvalidXml(R.raw.invalid_power_policy_group_missing_policy);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testInvalidXml_incorrectPolicyId() throws Exception {
        assertInvalidXml(R.raw.invalid_power_policy_incorrect_id);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testInvalidXml_incorrectOtherComponent() throws Exception {
        assertInvalidXml(R.raw.invalid_power_policy_incorrect_othercomponent);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testInvalidXml_incorrectValue() throws Exception {
        assertInvalidXml(R.raw.invalid_power_policy_incorrect_value);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testInvalidXml_unknownComponent() throws Exception {
        assertInvalidXml(R.raw.invalid_power_policy_unknown_component);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testInvalidXml_incorrectSystemPolicyComponent() throws Exception {
        assertInvalidXml(R.raw.invalid_system_power_policy_incorrect_component);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testInvalidXml_incorrectSystemPolicyId() throws Exception {
        assertInvalidXml(R.raw.invalid_system_power_policy_incorrect_id);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testValidXmlWithDefaultPolicyGroup() throws Exception {
        try (InputStream inputStream = mResources.openRawResource(
                R.raw.valid_power_policy_default_policy_group)) {
            mPolicyReader.readPowerPolicyFromXml(inputStream);
        }

        assertThat(mPolicyReader.getDefaultPowerPolicyGroup()).isEqualTo("mixed_policy_group");
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testInvalidXml_wrongDefaultPolicyGroupId() throws Exception {
        assertInvalidXml(R.raw.invalid_system_power_policy_incorrect_default_power_policy_group_id);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testDefaultPolicies() throws Exception {
        assertDefaultPolicies();
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testDefaultPoliciesWithCustomVendorPolicies() throws Exception {
        readPowerPolicyXml(R.raw.valid_power_policy);

        assertDefaultPolicies();
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testValidXml_CustomComponents() throws Exception {
        readPowerPolicyXml(R.raw.valid_power_policy_custom_components);

        assertValidPolicyPart_withCustomComponents();
        checkPolicy(POLICY_ID_CUSTOM_OTHER_OFF, POLICY_CUSTOM_OTHER_OFF);

        assertSystemPowerPolicy(NO_USER_INTERACTION_POLICY_ID,
                SYSTEM_POWER_POLICY_CUSTOM_COMPONENTS);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAR_POWER_POLICY_REFACTORING)
    public void testValidXml_customComponentsAtFileBeginning() throws Exception {
        readPowerPolicyXml(R.raw.valid_power_policy_custom_components_at_beginning);

        assertValidPolicyPart_withCustomComponents();
        checkPolicy(POLICY_ID_CUSTOM_OTHER_OFF, POLICY_CUSTOM_OTHER_OFF);

        assertSystemPowerPolicy(NO_USER_INTERACTION_POLICY_ID,
                SYSTEM_POWER_POLICY_CUSTOM_COMPONENTS);
    }

    @Test
    public void testDefinePowerPolicy() throws Exception {
        readPowerPolicyXml(R.raw.valid_power_policy_custom_components);
        // test definition with system_ prefix
        assertThat(mPolicyReader.definePowerPolicy("system_power_policy_no_user_interaction",
                new String[]{}, new String[]{})).isEqualTo(
                PolicyOperationStatus.ERROR_INVALID_POWER_POLICY_ID);
        // test definition with empty
        assertThat(mPolicyReader.definePowerPolicy("", new String[]{}, new String[]{})).isEqualTo(
                PolicyOperationStatus.ERROR_INVALID_POWER_POLICY_ID);
        // test definition with null string
        assertThat(mPolicyReader.definePowerPolicy(null, new String[]{}, new String[]{})).isEqualTo(
                PolicyOperationStatus.ERROR_INVALID_POWER_POLICY_ID);
        // test policies with duplicate names
        assertThat(mPolicyReader.definePowerPolicy("duplicate_policy", new String[]{},
                new String[]{})).isEqualTo(PolicyOperationStatus.OK);
        assertThat(mPolicyReader.definePowerPolicy("duplicate_policy", new String[]{},
                new String[]{})).isEqualTo(
                PolicyOperationStatus.ERROR_DOUBLE_REGISTERED_POWER_POLICY_ID);
        // test policy with duplicate components
        assertThat(mPolicyReader.definePowerPolicy("policy_with_duplicate_elements",
                new String[]{"AUDIO", "MEDIA", "DISPLAY"}, new String[]{"DISPLAY"})).isEqualTo(
                PolicyOperationStatus.ERROR_DUPLICATED_POWER_COMPONENT);
        // test policy with duplicate custom components
        assertThat(mPolicyReader.definePowerPolicy("policy_with_duplicate_custom_elements",
                new String[]{"1000", "MEDIA"}, new String[]{"1000", "DISPLAY"})).isEqualTo(
                PolicyOperationStatus.ERROR_DUPLICATED_POWER_COMPONENT);
        // test policy with duplicate custom components
        assertThat(mPolicyReader.definePowerPolicy("policy_with_custom_elements",
                new String[]{"1001", "MEDIA"}, new String[]{"1000", "DISPLAY"})).isEqualTo(
                PolicyOperationStatus.OK);
    }

    private void assertDefaultPolicies() {
        assertThat(mPolicyReader.getPowerPolicy(ALL_ON_POLICY_ID)).isNotNull();
        assertThat(mPolicyReader.getPreemptivePowerPolicy(NO_USER_INTERACTION_POLICY_ID))
                .isNotNull();
        assertThat(mPolicyReader.getPowerPolicy(INITIAL_ON_POLICY_ID)).isNotNull();
        assertThat(mPolicyReader.getPreemptivePowerPolicy(SUSPEND_PREP_POLICY_ID)).isNotNull();
    }

    private void assertValidPolicyPart() throws Exception {
        assertThat(mPolicyReader.getPowerPolicy(POLICY_ID_NOT_EXIST)).isNull();
        checkPolicy(POLICY_ID_OTHER_OFF, POLICY_OTHER_OFF);
        checkPolicy(POLICY_ID_OTHER_UNTOUCHED, POLICY_OTHER_UNTOUCHED);
        checkPolicy(POLICY_ID_OTHER_ON, POLICY_OTHER_ON);
        checkPolicy(POLICY_ID_OTHER_NONE, POLICY_OTHER_NONE);
    }

    private void assertValidPolicyPart_withCustomComponents() throws Exception {
        assertThat(mPolicyReader.getPowerPolicy(POLICY_ID_NOT_EXIST)).isNull();
        checkPolicy(POLICY_ID_OTHER_OFF, POLICY_OTHER_OFF_WITH_OEM_COMPONENTS);
        checkPolicy(POLICY_ID_OTHER_UNTOUCHED, POLICY_OTHER_UNTOUCHED_WITH_OEM_COMPONENTS);
        checkPolicy(POLICY_ID_OTHER_ON, POLICY_OTHER_ON_WITH_OEM_COMPONENTS);
        checkPolicy(POLICY_ID_OTHER_NONE, POLICY_OTHER_NONE);
    }

    private void assertNoPolicyPart() throws Exception {
        assertThat(mPolicyReader.getPowerPolicy(POLICY_ID_NOT_EXIST)).isNull();
        assertThat(mPolicyReader.getPowerPolicy(POLICY_ID_OTHER_OFF)).isNull();
        assertThat(mPolicyReader.getPowerPolicy(POLICY_ID_OTHER_UNTOUCHED)).isNull();
        assertThat(mPolicyReader.getPowerPolicy(POLICY_ID_OTHER_ON)).isNull();
        assertThat(mPolicyReader.getPowerPolicy(POLICY_ID_OTHER_NONE)).isNull();
    }

    private void assertValidPolicyGroupPart() throws Exception {
        assertThat(mPolicyReader.getDefaultPowerPolicyForState(POLICY_GROUP_ID_NOT_EXIST,
                VehicleApPowerStateReport.WAIT_FOR_VHAL)).isNull();
        checkPolicyGroup(POLICY_GROUP_ID_MIXED, VehicleApPowerStateReport.WAIT_FOR_VHAL,
                POLICY_OTHER_ON);
        checkPolicyGroup(POLICY_GROUP_ID_MIXED, VehicleApPowerStateReport.ON,
                POLICY_OTHER_UNTOUCHED);
        assertThat(mPolicyReader.getDefaultPowerPolicyForState(POLICY_GROUP_ID_MIXED,
                VehicleApPowerStateReport.DEEP_SLEEP_ENTRY)).isNull();
        assertThat(mPolicyReader.getDefaultPowerPolicyForState(POLICY_GROUP_ID_MIXED,
                VehicleApPowerStateReport.SHUTDOWN_START)).isNull();
        assertThat(mPolicyReader.getDefaultPowerPolicyForState(POLICY_GROUP_ID_NO_DEFAULT_POLICY,
                VehicleApPowerStateReport.WAIT_FOR_VHAL)).isNull();
        assertThat(mPolicyReader.getDefaultPowerPolicyForState(POLICY_GROUP_ID_NO_DEFAULT_POLICY,
                VehicleApPowerStateReport.ON)).isNull();
        assertThat(mPolicyReader.getDefaultPowerPolicyForState(POLICY_GROUP_ID_NO_DEFAULT_POLICY,
                VehicleApPowerStateReport.DEEP_SLEEP_ENTRY)).isNull();
        assertThat(mPolicyReader.getDefaultPowerPolicyForState(POLICY_GROUP_ID_NO_DEFAULT_POLICY,
                VehicleApPowerStateReport.SHUTDOWN_START)).isNull();
    }

    private void assertNoPolicyGroupPart() throws Exception {
        assertThat(mPolicyReader.getDefaultPowerPolicyForState(POLICY_GROUP_ID_NOT_EXIST,
                VehicleApPowerStateReport.WAIT_FOR_VHAL)).isNull();
        assertThat(mPolicyReader.getDefaultPowerPolicyForState(POLICY_GROUP_ID_BASIC,
                VehicleApPowerStateReport.WAIT_FOR_VHAL)).isNull();
        assertThat(mPolicyReader.getDefaultPowerPolicyForState(POLICY_GROUP_ID_BASIC,
                VehicleApPowerStateReport.ON)).isNull();
    }

    private void assertSystemPowerPolicy(String policyId, CarPowerPolicy expectedSystemPolicy)
            throws Exception {
        CarPowerPolicy systemPolicy = mPolicyReader.getPreemptivePowerPolicy(policyId);
        assertThat(systemPolicy).isNotNull();
        assertPolicyIdentical(systemPolicy, expectedSystemPolicy);
    }

    private void assertInvalidXml(int id) throws Exception {
        assertThrows(PolicyReader.PolicyXmlException.class, () -> readPowerPolicyXml(id));
    }

    private void checkPolicy(String policyId, CarPowerPolicy expectedPolicy) throws Exception {
        CarPowerPolicy actualPolicy = mPolicyReader.getPowerPolicy(policyId);
        assertThat(actualPolicy).isNotNull();
        assertPolicyIdentical(actualPolicy, expectedPolicy);
    }

    private void checkPolicyGroup(String groupId, int state, CarPowerPolicy expectedPolicy)
            throws Exception {
        CarPowerPolicy actualPolicy = mPolicyReader.getDefaultPowerPolicyForState(groupId, state);
        assertThat(actualPolicy).isNotNull();
        assertPolicyIdentical(expectedPolicy, actualPolicy);
    }

    private void readPowerPolicyXml(int id) throws Exception {
        try (InputStream inputStream = mResources.openRawResource(id)) {
            mPolicyReader.readPowerPolicyFromXml(inputStream);
        }
    }
}
