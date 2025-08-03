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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS;

import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_SECURE_LOCK_SCREEN;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.PersistableBundle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.TrustAgentInfo;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public final class TrustAgentInfoTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final ComponentName TRUST_AGENT_COMPONENT =
            new ComponentName("com.trustagent", "com.trustagent.xxx");
    private static final ComponentName NOT_CONFIGURED_TRUST_AGENT_COMPONENT =
            new ComponentName("com.trustagent.not_configured", "com.trustagent.xxx");
    private static final String BUNDLE_KEY = "testing";
    private static final String BUNDLE_VALUE = "value";

    private static final PersistableBundle sConfig = new PersistableBundle(
            Bundle.forPair(BUNDLE_KEY, BUNDLE_VALUE));

    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setTrustAgentConfiguration",
            "android.app.admin.DevicePolicyManager#getTrustAgentConfiguration"})
    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = TrustAgentInfo.class)
    public void setTrustAgentConfiguration_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT, sConfig);

            List<PersistableBundle> configs =
                    sDeviceState.dpc().devicePolicyManager().getTrustAgentConfiguration(
                            sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT);

            assertThat(configs).hasSize(1);
            PersistableBundle actual = configs.get(0);
            assertThat(actual.getString(BUNDLE_KEY)).isEqualTo(BUNDLE_VALUE);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT, null);
        }
    }

    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setTrustAgentConfiguration",
            "android.app.admin.DevicePolicyManager#getTrustAgentConfiguration"})
    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @PolicyAppliesTest(policy = TrustAgentInfo.class)
    public void setTrustAgentConfiguration_componentNotConfigured_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT,
                    sConfig);

            assertThat(TestApis.devicePolicy().getTrustAgentConfiguration(
                    NOT_CONFIGURED_TRUST_AGENT_COMPONENT)).isEmpty();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT, null);
        }
    }

    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setTrustAgentConfiguration",
            "android.app.admin.DevicePolicyManager#getTrustAgentConfiguration"})
    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @PolicyAppliesTest(policy = TrustAgentInfo.class)
    public void setTrustAgentConfiguration_trustAgentIsEnabled_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT,
                    sConfig);

            Set<PersistableBundle> configs =
                    TestApis.devicePolicy().getTrustAgentConfiguration(TRUST_AGENT_COMPONENT);
            assertThat(configs.stream().noneMatch(
                    c -> c.getString(BUNDLE_KEY).equals(BUNDLE_VALUE))).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT, null);
        }
    }

    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setTrustAgentConfiguration",
            "android.app.admin.DevicePolicyManager#getTrustAgentConfiguration"})
    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @PolicyAppliesTest(policy = TrustAgentInfo.class)
    public void setTrustAgentConfiguration_trustAgentIsDisabled_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT,
                    sConfig);
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_TRUST_AGENTS);

            Set<PersistableBundle> configs =
                    TestApis.devicePolicy().getTrustAgentConfiguration(TRUST_AGENT_COMPONENT);
            assertThat(configs.stream().anyMatch(
                    c -> c.getString(BUNDLE_KEY).equals(BUNDLE_VALUE))).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FEATURES_NONE);

            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT, null);
        }
    }

    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setTrustAgentConfiguration",
            "android.app.admin.DevicePolicyManager#getTrustAgentConfiguration"})
    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @PolicyAppliesTest(policy = TrustAgentInfo.class)
    public void setTrustAgentConfiguration_trustAgentIsConfigured_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT,
                    sConfig);
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_TRUST_AGENTS);

            Set<PersistableBundle> configs =
                    TestApis.devicePolicy().getTrustAgentConfiguration(TRUST_AGENT_COMPONENT);
            assertThat(configs.stream().anyMatch(
                    c -> c.getString(BUNDLE_KEY).equals(BUNDLE_VALUE))).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FEATURES_NONE);

            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT, null);
        }
    }

    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setTrustAgentConfiguration",
            "android.app.admin.DevicePolicyManager#getTrustAgentConfiguration"})
    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @PolicyDoesNotApplyTest(policy = TrustAgentInfo.class)
    public void setTrustAgentConfiguration_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT,
                    sConfig);

            assertThat(TestApis.devicePolicy().getTrustAgentConfiguration(TRUST_AGENT_COMPONENT))
                    .isEmpty();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT, null);
        }
    }

    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setTrustAgentConfiguration",
            "android.app.admin.DevicePolicyManager#getTrustAgentConfiguration"})
    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @PolicyDoesNotApplyTest(policy = TrustAgentInfo.class)
    public void setTrustAgentConfiguration_doesNotApply_trustAgentIsConfigured_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT,
                    sConfig);
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_TRUST_AGENTS);

            Set<PersistableBundle> configs =
                    TestApis.devicePolicy().getTrustAgentConfiguration(TRUST_AGENT_COMPONENT);
            assertThat(configs.stream().noneMatch(
                    c -> c.getString(BUNDLE_KEY).equals(BUNDLE_VALUE))).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FEATURES_NONE);

            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT, null);
        }
    }


    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setTrustAgentConfiguration"})
    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CannotSetPolicyTest(policy = TrustAgentInfo.class, includeNonDeviceAdminStates = false)
    public void setTrustAgentConfiguration_cannotSet_throwsException() {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                        sDeviceState.dpc().componentName(), TRUST_AGENT_COMPONENT,
                        sConfig));
    }

}
