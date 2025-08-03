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

import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FACE;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_IRIS;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS;
import static android.content.pm.PackageManager.FEATURE_SECURE_LOCK_SCREEN;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.ComponentName;
import android.os.PersistableBundle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.KeyguardDisableFace;
import com.android.bedstead.harrier.policies.KeyguardDisableFingerprint;
import com.android.bedstead.harrier.policies.KeyguardDisableIris;
import com.android.bedstead.harrier.policies.KeyguardDisableSecureCamera;
import com.android.bedstead.harrier.policies.KeyguardDisableSecureNotifications;
import com.android.bedstead.harrier.policies.KeyguardDisableTrustAgents;
import com.android.bedstead.harrier.policies.KeyguardDisableUnredactedNotifications;
import com.android.bedstead.harrier.policies.TrustAgentConfiguration;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
// TODO(b/273810424): Figure out expectations about applying to parent
public final class KeyguardTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final ComponentName TRUST_AGENT = new ComponentName("test.trust.agent", "t");

    private static final PersistableBundle CONFIGURATION =
            PersistableBundle.forPair("key", "test.trust.agent");

    // String longer than can be serialized to storage.
    private static final String VERY_LONG_STRING =
            new String(new char[100000]).replace('\0', 'A');

    @CannotSetPolicyTest(policy = KeyguardDisableSecureCamera.class,
            includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_SECURE_CAMERA"
    })
    public void setKeyguardDisabledFeatures_disableSecureCamera_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_SECURE_CAMERA));
    }

    @PolicyAppliesTest(policy = KeyguardDisableSecureCamera.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_SECURE_CAMERA"
    })
    public void setKeyguardDisabledFeatures_disableSecureCamera_featureIsSet() {
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_SECURE_CAMERA);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(
                    KEYGUARD_DISABLE_SECURE_CAMERA);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @PolicyDoesNotApplyTest(policy = KeyguardDisableSecureCamera.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_SECURE_CAMERA"
    })
    public void setKeyguardDisabledFeatures_disableSecureCamera_doesNotApply_featureIsNotSet() {
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_SECURE_CAMERA);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(
                    KEYGUARD_DISABLE_SECURE_CAMERA);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @CannotSetPolicyTest(policy = KeyguardDisableSecureNotifications.class,
            includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_SECURE_NOTIFICATIONS"
    })
    public void setKeyguardDisabledFeatures_disableSecureNotifications_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_SECURE_NOTIFICATIONS));
    }

    @PolicyAppliesTest(policy = KeyguardDisableSecureNotifications.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_SECURE_NOTIFICATIONS"
    })
    public void setKeyguardDisabledFeatures_disableSecureNotifications_featureIsSet() {
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_SECURE_NOTIFICATIONS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(
                    KEYGUARD_DISABLE_SECURE_NOTIFICATIONS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @PolicyDoesNotApplyTest(policy = KeyguardDisableSecureNotifications.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_SECURE_NOTIFICATIONS"
    })
    public void setKeyguardDisabledFeatures_disableSecureNotifications_doesNotApply_featureIsNotSet() {
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_SECURE_NOTIFICATIONS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(
                    KEYGUARD_DISABLE_SECURE_NOTIFICATIONS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @CannotSetPolicyTest(policy = KeyguardDisableTrustAgents.class,
            includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_TRUST_AGENTS"
    })
    public void setKeyguardDisabledFeatures_disableTrustAgents_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_TRUST_AGENTS));
    }

    @PolicyAppliesTest(policy = KeyguardDisableTrustAgents.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_TRUST_AGENTS"
    })
    public void setKeyguardDisabledFeatures_disableTrustAgents_featureIsSet() {
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_TRUST_AGENTS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(
                    KEYGUARD_DISABLE_TRUST_AGENTS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @PolicyDoesNotApplyTest(policy = KeyguardDisableTrustAgents.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_TRUST_AGENTS"
    })
    public void setKeyguardDisabledFeatures_disableTrustAgents_doesNotApply_featureIsNotSet() {
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_TRUST_AGENTS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(
                    KEYGUARD_DISABLE_TRUST_AGENTS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @CannotSetPolicyTest(policy = KeyguardDisableUnredactedNotifications.class,
            includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS"
    })
    public void setKeyguardDisabledFeatures_disableUnredactedNotifications_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS));
    }

    @PolicyAppliesTest(policy = KeyguardDisableUnredactedNotifications.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS"
    })
    public void setKeyguardDisabledFeatures_disableUnredactedNotifications_featureIsSet() {
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(
                    KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @PolicyDoesNotApplyTest(policy = KeyguardDisableUnredactedNotifications.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS"
    })
    public void setKeyguardDisabledFeatures_disableUnredactedNotifications_doesNotApply_featureIsNotSet() {
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(
                    KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @CannotSetPolicyTest(policy = KeyguardDisableFingerprint.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_FINGERPRINT"
    })
    public void setKeyguardDisabledFeatures_disableFingerprint_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FINGERPRINT));
    }

    @PolicyAppliesTest(policy = KeyguardDisableFingerprint.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_FINGERPRINT"
    })
    public void setKeyguardDisabledFeatures_disableFingerprint_featureIsSet() {
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FINGERPRINT);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(
                    KEYGUARD_DISABLE_FINGERPRINT);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @PolicyDoesNotApplyTest(policy = KeyguardDisableFingerprint.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_FINGERPRINT"
    })
    public void setKeyguardDisabledFeatures_disableFingerprint_doesNotApply_featureIsNotSet() {
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FINGERPRINT);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(
                    KEYGUARD_DISABLE_FINGERPRINT);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @CannotSetPolicyTest(policy = KeyguardDisableFace.class,
            includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_FACE"
    })
    public void setKeyguardDisabledFeatures_disableFace_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FACE));
    }

    @PolicyAppliesTest(policy = KeyguardDisableFace.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_FACE"
    })
    public void setKeyguardDisabledFeatures_disableFace_featureIsSet() {
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FACE);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(
                    KEYGUARD_DISABLE_FACE);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @PolicyDoesNotApplyTest(policy = KeyguardDisableFace.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_FACE"
    })
    public void setKeyguardDisabledFeatures_disableFace_doesNotApply_featureIsNotSet() {
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FACE);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(
                    KEYGUARD_DISABLE_FACE);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @CannotSetPolicyTest(policy = KeyguardDisableIris.class,
            includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_IRIS"
    })
    public void setKeyguardDisabledFeatures_disableIris_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_IRIS));
    }

    @PolicyAppliesTest(policy = KeyguardDisableIris.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_IRIS"
    })
    public void setKeyguardDisabledFeatures_disableIris_featureIsSet() {
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_IRIS);

            assertThat(sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName())).isEqualTo(KEYGUARD_DISABLE_IRIS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(
                    KEYGUARD_DISABLE_IRIS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @PolicyDoesNotApplyTest(policy = KeyguardDisableIris.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_IRIS"
    })
    public void setKeyguardDisabledFeatures_disableIris_doesNotApply_featureIsNotSet() {
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_IRIS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(
                    KEYGUARD_DISABLE_IRIS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CannotSetPolicyTest(policy = TrustAgentConfiguration.class,
            includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setTrustAgentConfiguration")
    public void setTrustAgentConfiguration_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .setTrustAgentConfiguration(
                        sDeviceState.dpc().componentName(), TRUST_AGENT, CONFIGURATION));
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @PolicyAppliesTest(policy = TrustAgentConfiguration.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setTrustAgentConfiguration",
            "android.app.admin.DevicePolicyManager#getTrustAgentConfiguration"})
    @Ignore // Needs trust agent testapp
    public void setTrustAgentConfiguration_trustAgentConfigurationIsSet() {
        List<PersistableBundle> originalConfigurations = sDeviceState.dpc().devicePolicyManager()
                .getTrustAgentConfiguration(sDeviceState.dpc().componentName(), TRUST_AGENT);
        PersistableBundle originalConfiguration = originalConfigurations == null
                || originalConfigurations.isEmpty() ? null
                : originalConfigurations.stream().findAny().get();

        try {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT, CONFIGURATION);

            assertContainsTestConfiguration(TestApis.devicePolicy().getTrustAgentConfiguration(
                    TRUST_AGENT));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT, originalConfiguration);
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @PolicyDoesNotApplyTest(policy = TrustAgentConfiguration.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setTrustAgentConfiguration",
            "android.app.admin.DevicePolicyManager#getTrustAgentConfiguration"})
    @Ignore // Needs trust agent testapp
    public void setTrustAgentConfiguration_doesNotApply_trustAgentConfigurationIsNotSet() {
        List<PersistableBundle> originalConfigurations = sDeviceState.dpc().devicePolicyManager()
                .getTrustAgentConfiguration(sDeviceState.dpc().componentName(), TRUST_AGENT);
        PersistableBundle originalConfiguration = originalConfigurations == null
                || originalConfigurations.isEmpty() ? null
                : originalConfigurations.stream().findAny().get();

        try {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT, CONFIGURATION);

            assertThat(TestApis.devicePolicy().getTrustAgentConfiguration(
                    TRUST_AGENT)).isEmpty();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT, originalConfiguration);
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = TrustAgentConfiguration.class)
    @Postsubmit(reason = "New test")
    public void setTrustAgentConfiguration_veryLongPackage_throws() {
        ComponentName badAgent =
                ComponentName.createRelative(VERY_LONG_STRING, TRUST_AGENT.getClassName());

        assertThrows(IllegalArgumentException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setTrustAgentConfiguration(
                        sDeviceState.dpc().componentName(), badAgent, CONFIGURATION));
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = TrustAgentConfiguration.class)
    @Postsubmit(reason = "New test")
    public void setTrustAgentConfiguration_veryLongClassName_throws() {
        ComponentName badAgent =
                ComponentName.createRelative(TRUST_AGENT.getPackageName(), VERY_LONG_STRING);

        assertThrows(IllegalArgumentException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setTrustAgentConfiguration(
                        sDeviceState.dpc().componentName(), badAgent, CONFIGURATION));
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = TrustAgentConfiguration.class)
    @Postsubmit(reason = "New test")
    public void setTrustAgentConfiguration_veryLongConfigValue_throws() {
        PersistableBundle badConfig =
                PersistableBundle.forPair("key", VERY_LONG_STRING);

        assertThrows(IllegalArgumentException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setTrustAgentConfiguration(
                        sDeviceState.dpc().componentName(), TRUST_AGENT, badConfig));
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = TrustAgentConfiguration.class)
    @Postsubmit(reason = "New test")
    public void setTrustAgentConfiguration_veryLongConfigKey_throws() {
        PersistableBundle badConfig =
                PersistableBundle.forPair(VERY_LONG_STRING, "value");

        assertThrows(IllegalArgumentException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setTrustAgentConfiguration(
                        sDeviceState.dpc().componentName(), TRUST_AGENT, badConfig));
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = TrustAgentConfiguration.class)
    @Postsubmit(reason = "New test")
    public void setTrustAgentConfiguration_veryLongConfigValueInArray_throws() {
        PersistableBundle badConfig = new PersistableBundle();
        badConfig.putStringArray("key", new String[]{VERY_LONG_STRING});

        assertThrows(IllegalArgumentException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setTrustAgentConfiguration(
                        sDeviceState.dpc().componentName(), TRUST_AGENT, badConfig));
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = TrustAgentConfiguration.class)
    @Postsubmit(reason = "New test")
    public void setTrustAgentConfiguration_veryLongConfigValueInNestedBundle_throws() {
        // Burrow it inside a deeply nested bundle
        PersistableBundle bundle = PersistableBundle.forPair("key", VERY_LONG_STRING);
        for (int i = 0; i < 100; i++) {
            PersistableBundle nextLayer = new PersistableBundle();
            nextLayer.putPersistableBundle("key", bundle);
            bundle = nextLayer;
        }
        final PersistableBundle badConfig = bundle;

        assertThrows(IllegalArgumentException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setTrustAgentConfiguration(
                        sDeviceState.dpc().componentName(), TRUST_AGENT, badConfig));
    }

    private void assertContainsTestConfiguration(Set<PersistableBundle> bundle) {
        assertThat(bundle).hasSize(1);
        assertThat(bundle.iterator().next().getString("key"))
                .isEqualTo(CONFIGURATION.getString("key"));
    }
}
