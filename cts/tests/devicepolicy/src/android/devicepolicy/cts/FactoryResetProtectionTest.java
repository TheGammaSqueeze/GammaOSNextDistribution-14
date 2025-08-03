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

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.FactoryResetProtectionPolicy;
import android.service.persistentdata.PersistentDataBlockManager;
import android.stats.devicepolicy.EventId;
import android.util.Xml;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFactoryResetProtectionPolicySupported;
import com.android.bedstead.harrier.annotations.RequireSystemServiceAvailable;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.policies.FactoryResetProtection;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.utils.ParcelTest;
import com.android.bedstead.nene.utils.XmlTest;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(BedsteadJUnit4.class)
@RequireSystemServiceAvailable(PersistentDataBlockManager.class)
@RequireFactoryResetProtectionPolicySupported
public final class FactoryResetProtectionTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final FactoryResetProtectionPolicy FACTORY_RESET_PROTECTION_POLICY =
            new FactoryResetProtectionPolicy.Builder()
                    .setFactoryResetProtectionEnabled(true)
                    .setFactoryResetProtectionAccounts(List.of("test@account.com")).build();
    private static final String TAG_FACTORY_RESET_PROTECTION_POLICY =
            "factory_reset_protection_policy";
    private static final String KEY_FACTORY_RESET_PROTECTION_ACCOUNT =
            "factory_reset_protection_account";

    @CannotSetPolicyTest(policy = FactoryResetProtection.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setFactoryResetProtectionPolicy")
    public void setFactoryResetProtectionPolicy_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .setFactoryResetProtectionPolicy(
                                sDeviceState.dpc().componentName(),
                                FACTORY_RESET_PROTECTION_POLICY));
    }

    @CannotSetPolicyTest(policy = FactoryResetProtection.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getFactoryResetProtectionPolicy")
    public void getFactoryResetProtectionPolicy_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .getFactoryResetProtectionPolicy(
                                sDeviceState.dpc().componentName()));
    }

    @CanSetPolicyTest(policy = FactoryResetProtection.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setFactoryResetProtectionPolicy")
    public void setFactoryResetProtectionPolicy_setsFactoryResetProtectionPolicy() {
        FactoryResetProtectionPolicy originalFrpPolicy = sDeviceState.dpc().devicePolicyManager()
                .getFactoryResetProtectionPolicy(sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setFactoryResetProtectionPolicy(sDeviceState.dpc().componentName(),
                            FACTORY_RESET_PROTECTION_POLICY);

            assertThat(isEqualToFactoryResetProtectionPolicy(
                    sDeviceState.dpc().devicePolicyManager().getFactoryResetProtectionPolicy(
                            sDeviceState.dpc().componentName()))).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setFactoryResetProtectionPolicy(
                    sDeviceState.dpc().componentName(), originalFrpPolicy);
        }
    }

    @CanSetPolicyTest(policy = FactoryResetProtection.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setFactoryResetProtectionPolicy")
    public void setFactoryResetProtectionPolicy_isLogged() {
        FactoryResetProtectionPolicy originalFrpPolicy = sDeviceState.dpc().devicePolicyManager()
                .getFactoryResetProtectionPolicy(sDeviceState.dpc().componentName());

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setFactoryResetProtectionPolicy(sDeviceState.dpc().componentName(),
                            FACTORY_RESET_PROTECTION_POLICY);

            assertThat(metrics.query()
                    .whereType()
                    .isEqualTo(EventId.SET_FACTORY_RESET_PROTECTION_VALUE)
                    .whereAdminPackageName()
                    .isEqualTo(sDeviceState.dpc().componentName().getPackageName()))
                    .wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setFactoryResetProtectionPolicy(
                    sDeviceState.dpc().componentName(), originalFrpPolicy);
        }
    }

    @Test
    @Postsubmit(reason = "New test")
    @ApiTest(apis = "android.app.admin.FactoryResetProtectionPolicy#readFromXml")
    public void factoryResetProtectionPolicy_readFromXml_parserThrowsXmlParserException_doesNotDeserialize() {
        assertThat(FactoryResetProtectionPolicy.readFromXml(Xml.newFastPullParser())).isNull();
    }

    @Test
    @Postsubmit(reason = "New test")
    public void factoryResetProtectionPolicy_validXml_correctSerializationAndDeserialization() {
        List<String> factoryResetProtectionAccounts = XmlTest.serializeAndDeserialize(
                TAG_FACTORY_RESET_PROTECTION_POLICY,
                KEY_FACTORY_RESET_PROTECTION_ACCOUNT,
                FACTORY_RESET_PROTECTION_POLICY.getFactoryResetProtectionAccounts());

        FactoryResetProtectionPolicy policy = new FactoryResetProtectionPolicy.Builder()
                .setFactoryResetProtectionAccounts(factoryResetProtectionAccounts)
                .setFactoryResetProtectionEnabled(true)
                .build();
        assertThat(isEqualToFactoryResetProtectionPolicy(policy)).isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    public void factoryResetProtectionPolicy_correctParcelingAndUnparceling() {
        FactoryResetProtectionPolicy actualPolicy = ParcelTest.parcelAndUnparcel(
                FactoryResetProtectionPolicy.class, FACTORY_RESET_PROTECTION_POLICY);

        assertThat(isEqualToFactoryResetProtectionPolicy(actualPolicy)).isTrue();
    }
    
    private boolean isEqualToFactoryResetProtectionPolicy(FactoryResetProtectionPolicy policy) {
        assertThat(policy.isFactoryResetProtectionEnabled())
                .isEqualTo(FACTORY_RESET_PROTECTION_POLICY.isFactoryResetProtectionEnabled());
        assertThat(policy.getFactoryResetProtectionAccounts())
                .hasSize(FACTORY_RESET_PROTECTION_POLICY.getFactoryResetProtectionAccounts().size());
        assertThat(policy.getFactoryResetProtectionAccounts()
                .containsAll(FACTORY_RESET_PROTECTION_POLICY.getFactoryResetProtectionAccounts()))
                .isTrue();
        return true; // TODO: Fix this test
    }
}
