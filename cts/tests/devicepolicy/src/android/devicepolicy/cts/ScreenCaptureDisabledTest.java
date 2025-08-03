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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyIdentifiers.SCREEN_CAPTURE_DISABLED_POLICY;
import static android.app.admin.TargetUser.GLOBAL_USER_ID;
import static android.devicepolicy.cts.utils.PolicyEngineUtils.TRUE_MORE_RESTRICTIVE;

import static com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest.LESS_IMPORTANT;
import static com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest.MORE_IMPORTANT;
import static com.android.bedstead.harrier.annotations.enterprise.MostRestrictiveCoexistenceTest.DPC_1;
import static com.android.bedstead.harrier.annotations.enterprise.MostRestrictiveCoexistenceTest.DPC_2;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.nene.flags.CommonFlags.DevicePolicyManager.ENABLE_DEVICE_POLICY_ENGINE_FLAG;
import static com.android.bedstead.nene.flags.CommonFlags.NAMESPACE_DEVICE_POLICY_MANAGER;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.UiAutomation;
import android.app.admin.DevicePolicyManager;
import android.app.admin.NoArgsPolicyKey;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.devicepolicy.cts.utils.PolicyEngineUtils;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.stats.devicepolicy.EventId;

import androidx.test.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn;
import com.android.bedstead.harrier.annotations.EnsureUnlocked;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest;
import com.android.bedstead.harrier.annotations.enterprise.MostRestrictiveCoexistenceTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.DisallowCamera;
import com.android.bedstead.harrier.policies.ScreenCaptureDisabled;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

@RunWith(BedsteadJUnit4.class)
public final class ScreenCaptureDisabledTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);
    private static final TestApp sTestApp =
            sDeviceState.testApps().query().whereActivities().isNotEmpty().get();

    private static final String CALLED_FROM_PARENT = "calledFromParent";
    private static final String NOT_CALLED_FROM_PARENT = "notCalledFromParent";

    /** See {@code DevicePolicyManager#POLICY_DISABLE_SCREEN_CAPTURE} */
    private static final String POLICY_DISABLE_SCREEN_CAPTURE = "policy_disable_screen_capture";

    /**
     * see {@code DevicePolicyManager.EXTRA_RESTRICTION}
     */
    private static final String EXTRA_RESTRICTION = "android.app.extra.RESTRICTION";

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_false_works() {
        sDeviceState.dpc().devicePolicyManager()
                .setScreenCaptureDisabled(sDeviceState.dpc().componentName(), false);

        assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null)).isFalse();
    }

    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_false_checkWithDPC_works() {
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);

            assertThat(sDeviceState.dpc().devicePolicyManager().getScreenCaptureDisabled(
                    sDeviceState.dpc().componentName())).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @CannotSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_false_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .setScreenCaptureDisabled(sDeviceState.dpc().componentName(), false));
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_true_works() {
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(
                    /* admin= */ null)).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_true_checkWithDPC_works() {
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(sDeviceState.dpc().devicePolicyManager().getScreenCaptureDisabled(
                    sDeviceState.dpc().componentName())).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @PolicyDoesNotApplyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_true_doesNotApply() {
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */
                    null)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @PolicyDoesNotApplyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @EnsureScreenIsOn
    @EnsureUnlocked
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_true_screenCaptureNoRedactionOrNull() {
        Assume.assumeTrue("Requires showing an activity",
                TestApis.users().instrumented().canShowActivities());
        try {

            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(takeScreenshotExpectingNoRedactionOrNull()).isFalse();

        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @EnsureScreenIsOn
    @EnsureUnlocked
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_true_screenCaptureRedactedOrNull() {
        Assume.assumeTrue("Requires showing an activity",
                TestApis.users().instrumented().canShowActivities());
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(takeScreenshotExpectingRedactionOrNull()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @EnsureScreenIsOn
    @EnsureUnlocked
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_false_screenCaptureNoRedactionOrNull() {
        Assume.assumeTrue("Requires showing an activity",
                TestApis.users().instrumented().canShowActivities());
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);

            assertThat(takeScreenshotExpectingNoRedactionOrNull()).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_true_metricsLogged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_SCREEN_CAPTURE_DISABLED_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().packageName())
                    .whereBoolean().isTrue()).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_false_metricsLogged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_SCREEN_CAPTURE_DISABLED_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().packageName())
                    .whereBoolean().isFalse()).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    public void getDevicePolicyState_setScreenCaptureDisabled_returnsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                    TestApis.users().instrumented().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    // TODO: enable after adding the broadcast receiver to relevant test apps.
//    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @EnsureHasDeviceOwner(isPrimary = true)
    public void policyUpdateReceiver_setScreenCaptureDisabled_receivedPolicySetBroadcast() {
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ true);

            PolicySetResultUtils.assertPolicySetResultReceived(
                    sDeviceState,
                    SCREEN_CAPTURE_DISABLED_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, GLOBAL_USER_ID, new Bundle());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class, singleTestOnly = true)
    public void getDevicePolicyState_setScreenCaptureDisabled_returnsCorrectResolutionMechanism() {
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                    TestApis.users().instrumented().userHandle());

            assertThat(PolicyEngineUtils.getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(TRUE_MORE_RESTRICTIVE);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ false);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    @MostRestrictiveCoexistenceTest(policy = ScreenCaptureDisabled.class)
    public void setScreenCaptureDisabled_bothTrue_getScreenCaptureDisabledIsTrue() {
        try {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ true);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
            assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null))
                    .isFalse();
            assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager()
                    .getScreenCaptureDisabled(/* admin= */ null)).isTrue();
            assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager()
                    .getScreenCaptureDisabled(/* admin= */ null)).isTrue();

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ false);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ false);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    @MostRestrictiveCoexistenceTest(policy = ScreenCaptureDisabled.class)
    public void setScreenCaptureDisabled_bothFalse_getScreenCaptureDisabledIsFalse() {
        sDeviceState.testApp(DPC_1).devicePolicyManager().setScreenCaptureDisabled(
                /* componentName= */ null, /* disabled= */ false);
        sDeviceState.testApp(DPC_2).devicePolicyManager().setScreenCaptureDisabled(
                /* componentName= */ null, /* disabled= */ false);

        PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                TestApis.users().instrumented().userHandle());
        assertThat(policyState).isNull();
        assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null))
                .isFalse();
        assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager()
                .getScreenCaptureDisabled(/* admin= */ null)).isFalse();
        assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager()
                .getScreenCaptureDisabled(/* admin= */ null)).isFalse();
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    @MostRestrictiveCoexistenceTest(policy = ScreenCaptureDisabled.class)
    public void setScreenCaptureDisabled_differentValues_getScreenCaptureDisabledIsTrue() {
        try {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ true);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ false);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
            assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null))
                    .isFalse();
            assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager()
                    .getScreenCaptureDisabled(/* admin= */ null)).isTrue();
            assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager()
                    .getScreenCaptureDisabled(/* admin= */ null)).isTrue();

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ false);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ false);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    @MostRestrictiveCoexistenceTest(policy = ScreenCaptureDisabled.class)
    public void setScreenCaptureDisabled_differentValuesThenBothFalse_getScreenCaptureDisabledIsFalse() {
        try {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ true);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ false);
            sDeviceState.testApp(DPC_1).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ false);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState).isNull();
            assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null))
                    .isFalse();
            assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager()
                    .getScreenCaptureDisabled(/* admin= */ null)).isFalse();
            assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager()
                    .getScreenCaptureDisabled(/* admin= */ null)).isFalse();

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ false);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ false);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    @MostRestrictiveCoexistenceTest(policy = ScreenCaptureDisabled.class)
    public void setScreenCaptureDisabled_bothTrueThenOneFalse_getScreenCaptureDisabledIsTrue() {
        try {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ true);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ true);
            sDeviceState.testApp(DPC_1).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ false);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
            assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null))
                    .isTrue();
            assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager()
                    .getScreenCaptureDisabled(/* admin= */ null)).isTrue();
            assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager()
                    .getScreenCaptureDisabled(/* admin= */ null)).isTrue();

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ false);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ false);
        }
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_policyMigration_works() {
        try {
            TestApis.flags().set(
                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "false");
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ true);

            sLocalDevicePolicyManager.triggerDevicePolicyEngineMigration(true);
            TestApis.flags().set(
                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "true");

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
            assertThat(
                    sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null)).isTrue();

        } finally {
            TestApis.flags().set(
                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "false");
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ false);
            TestApis.flags().set(
                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "true");
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ false);
            TestApis.flags().set(
                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, null);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    @MostImportantCoexistenceTest(policy = ScreenCaptureDisabled.class)
    public void setScreenCaptureDisabled_setByDPCAndPermission_DPCRemoved_stillEnforced() {
        try {
            sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ true);
            sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager().setScreenCaptureDisabled(
                    /* componentName= */ null, /* disabled= */ true);

            // Remove DPC
            sDeviceState.dpc().devicePolicyManager().clearDeviceOwnerApp(
                    sDeviceState.dpc().packageName());

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
            assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null))
                    .isTrue();
        } finally {
            try {
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager().setScreenCaptureDisabled(
                        /* componentName= */ null, /* disabled= */ false);
            } catch (Exception e) {
                // expected if app was uninstalled
            }
            try {
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager().setScreenCaptureDisabled(
                        /* componentName= */ null, /* disabled= */ false);
            } catch (Exception e) {
                // expected if app was uninstalled
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#createAdminSupportIntent",
            "android.app.admin.DevicePolicyManager#POLICY_DISABLE_SCREEN_CAPTURE"})
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    public void createAdminSupportIntent_disallowScreenCapture_createsIntent() {
        boolean originalScreenCaptureDisabledStatus = sDeviceState.dpc().devicePolicyManager()
                .getScreenCaptureDisabled(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ true);

            Intent intent = TestApis.devicePolicy().createAdminSupportIntent(
                    POLICY_DISABLE_SCREEN_CAPTURE);

            assertThat(intent.getStringExtra(EXTRA_RESTRICTION)).isEqualTo(
                    POLICY_DISABLE_SCREEN_CAPTURE);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), originalScreenCaptureDisabledStatus
            );
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#createAdminSupportIntent",
            "android.app.admin.DevicePolicyManager#POLICY_DISABLE_SCREEN_CAPTURE"})
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    public void createAdminSupportIntent_allowScreenCapture_doesNotCreate() {
        boolean originalScreenCaptureDisabledStatus = sDeviceState.dpc().devicePolicyManager()
                .getScreenCaptureDisabled(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ false);

            Intent intent = TestApis.devicePolicy().createAdminSupportIntent(
                    POLICY_DISABLE_SCREEN_CAPTURE);

            assertThat(intent).isNull();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), originalScreenCaptureDisabledStatus
            );
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    public void setScreenCaptureDisabled_true_wasLogged() {
        boolean originalScreenCaptureDisabledStatus = sDeviceState.dpc().devicePolicyManager()
                .getScreenCaptureDisabled(sDeviceState.dpc().componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            boolean isParentInstance = sDeviceState.dpc().isParentInstance();

            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ true);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_SCREEN_CAPTURE_DISABLED_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().componentName().getPackageName())
                    .whereBoolean().isEqualTo(true))
                    .wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), originalScreenCaptureDisabledStatus
            );
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    public void setScreenCaptureDisabled_false_wasLogged() {
        boolean originalScreenCaptureDisabledStatus = sDeviceState.dpc().devicePolicyManager()
                .getScreenCaptureDisabled(sDeviceState.dpc().componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            boolean isParentInstance = sDeviceState.dpc().isParentInstance();

            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ false);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_SCREEN_CAPTURE_DISABLED_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().componentName().getPackageName())
                    .whereBoolean().isEqualTo(false))
                    .wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), originalScreenCaptureDisabledStatus
            );
        }
    }

    private boolean takeScreenshotExpectingRedactionOrNull() {
        try (TestAppInstance testApp = sTestApp.install()) {
            // We show an activity on the current user, which should be redacted if the screen
            // capture disabled policy is applying to this user.
            testApp.activities().any().start();
            return Poll.forValue(
                    () -> checkScreenshotIsRedactedOrNull(sUiAutomation.takeScreenshot())).timeout(
                    Duration.ofMinutes(2)).toBeEqualTo(true).await();
        }
    }

    private boolean takeScreenshotExpectingNoRedactionOrNull() {
        try (TestAppInstance testApp = sTestApp.install()) {
            // We show an activity on the current user, which should be redacted if the screen
            // capture disabled policy is applying to this user.
            testApp.activities().any().start();
            return Poll.forValue(
                    () -> checkScreenshotIsRedactedOrNull(sUiAutomation.takeScreenshot())).timeout(
                    Duration.ofMinutes(2)).toBeEqualTo(false).await();
        }
    }

    private boolean checkScreenshotIsRedactedOrNull(Bitmap screenshot) {
        if (screenshot == null) {
            return true;
        }
        int width = screenshot.getWidth();
        int height = screenshot.getHeight();

        // Getting pixels of only the middle part(from y  = height/4 to 3/4(height)) of the
        // screenshot to check(screenshot is redacted) for only the middle part of the screen,
        // as there could be notifications in the top part and white line(navigation bar) at bottom
        // which are included in the screenshot and are not redacted(black). It's not perfect, but
        // seems best option to avoid any flakiness at this point.
        int len = width * (height / 2);
        int[] pixels = new int[len];
        screenshot.getPixels(pixels, 0, width, 0, height / 4, width, height / 2);

        for (int i = 0; i < len; ++i) {
            // Skip some pixels from the right to accommodate for the edge panel(present on
            // some devices) which will not be redacted in the screenshot.
            if ((i % width) /* X-position */ > (width - 34)) {
                // skipping edge panel
                continue;
            }
            if (!(pixels[i] == Color.BLACK || (pixels[i] == Color.TRANSPARENT && isAutomotive()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAutomotive() {
        return TestApis.context().instrumentedContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

}
