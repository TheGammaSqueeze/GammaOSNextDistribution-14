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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.MTE_DISABLED;
import static android.app.admin.DevicePolicyManager.MTE_ENABLED;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.policies.MteForceOff;
import com.android.bedstead.harrier.policies.MteForceOn;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class MtePolicyTest {
    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    void skipIfUnsupported() {
        assumeTrue(
                TestApis.systemProperties()
                        .getBoolean(
                                "ro.arm64.memtag.bootctl_device_policy_manager",
                                TestApis.systemProperties()
                                        .getBoolean(
                                                "ro.arm64.memtag.bootctl_settings_toggle", false)));
    }

    @CanSetPolicyTest(policy = MteForceOn.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMtePolicy")
    public void setMtePolicy_MTE_ENABLED_applies() {
        skipIfUnsupported();
        int originalValue = sDeviceState.dpc().devicePolicyManager().getMtePolicy();

        try {
            sDeviceState.dpc().devicePolicyManager().setMtePolicy(MTE_ENABLED);
            assertThat(sDeviceState.dpc().devicePolicyManager().getMtePolicy())
                    .isEqualTo(MTE_ENABLED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setMtePolicy(originalValue);
        }
    }

    @CanSetPolicyTest(policy = MteForceOn.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMtePolicy")
    public void setMtePolicy_MTE_ENABLED_logsEvent() {
        skipIfUnsupported();
        int originalValue = sDeviceState.dpc().devicePolicyManager().getMtePolicy();

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setMtePolicy(MTE_ENABLED);
            assertThat(metrics.query()
                               .whereType()
                               .isEqualTo(EventId.SET_MTE_POLICY_VALUE)
                               .whereInteger()
                               .isEqualTo(MTE_ENABLED)
                               .whereAdminPackageName()
                               .isEqualTo(sDeviceState.dpc().componentName().getPackageName()))
                    .wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setMtePolicy(originalValue);
        }
    }

    @CanSetPolicyTest(policy = MteForceOff.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setMtePolicy", "android.app.admin.DevicePolicyManager#getMtePolicy"})
    public void setMtePolicy_MTE_DISABLED_applies() {
        skipIfUnsupported();
        int originalValue = sDeviceState.dpc().devicePolicyManager().getMtePolicy();

        try {
            sDeviceState.dpc().devicePolicyManager().setMtePolicy(MTE_DISABLED);
            assertThat(sDeviceState.dpc().devicePolicyManager().getMtePolicy())
                    .isEqualTo(MTE_DISABLED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setMtePolicy(originalValue);
        }
    }

    @CanSetPolicyTest(policy = MteForceOff.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMtePolicy")
    public void setMtePolicy_MTE_DISABLED_logsEvent() {
        skipIfUnsupported();
        int originalValue = sDeviceState.dpc().devicePolicyManager().getMtePolicy();

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setMtePolicy(MTE_DISABLED);
            assertThat(metrics.query()
                               .whereType()
                               .isEqualTo(EventId.SET_MTE_POLICY_VALUE)
                               .whereInteger()
                               .isEqualTo(MTE_DISABLED)
                               .whereAdminPackageName()
                               .isEqualTo(sDeviceState.dpc().componentName().getPackageName()))
                    .wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setMtePolicy(originalValue);
        }
    }

    @CannotSetPolicyTest(policy = MteForceOn.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMtePolicy")
    public void setMtePolicy_MTE_ENABLED_throwsException() {
        skipIfUnsupported();
        assertThrows(
                SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setMtePolicy(MTE_ENABLED));
    }

    @CannotSetPolicyTest(policy = MteForceOff.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMtePolicy")
    public void setMtePolicy_MTE_DISABLED_throwsException() {
        skipIfUnsupported();
        assertThrows(
                SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setMtePolicy(MTE_DISABLED));
    }
}
