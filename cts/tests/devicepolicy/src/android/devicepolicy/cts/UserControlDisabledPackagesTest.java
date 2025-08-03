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

import static android.app.admin.DevicePolicyIdentifiers.USER_CONTROL_DISABLED_PACKAGES_POLICY;
import static android.app.admin.TargetUser.GLOBAL_USER_ID;

import static com.android.bedstead.harrier.annotations.enterprise.MostRestrictiveCoexistenceTest.DPC_1;
import static com.android.bedstead.harrier.annotations.enterprise.MostRestrictiveCoexistenceTest.DPC_2;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.nene.flags.CommonFlags.DevicePolicyManager.ENABLE_DEVICE_POLICY_ENGINE_FLAG;
import static com.android.bedstead.nene.flags.CommonFlags.NAMESPACE_DEVICE_POLICY_MANAGER;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import android.Manifest.permission;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.NoArgsPolicyKey;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateResult;
import android.app.admin.StringSetUnion;
import android.devicepolicy.cts.utils.PolicyEngineUtils;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.os.Bundle;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.MostRestrictiveCoexistenceTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.UserControlDisabledPackages;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public final class UserControlDisabledPackagesTest {
    private static final String TAG = "UserControlDisabledPackagesTest";

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp =
            sDeviceState.testApps().query().whereActivities().isNotEmpty().get();

    private static final TestApp sSecondTestApp =
            sDeviceState.testApps().query().whereActivities().isEmpty().get();

    private static final ActivityManager sActivityManager =
            TestApis.context().instrumentedContext().getSystemService(ActivityManager.class);

    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext()
                    .getSystemService(DevicePolicyManager.class);

    private static final String PACKAGE_NAME = "com.android.foo.bar.baz";

    @CannotSetPolicyTest(policy = UserControlDisabledPackages.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    public void setUserControlDisabledPackages_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager()
                    .setUserControlDisabledPackages(sDeviceState.dpc().componentName(),
                            Arrays.asList(PACKAGE_NAME));
        });
    }

    @CanSetPolicyTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "New test")
    public void setUserControlDisabledPackages_verifyMetricIsLogged() {
        List<String> originalDisabledPackages =
                sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        sDeviceState.dpc().componentName());

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    Arrays.asList(PACKAGE_NAME));

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_USER_CONTROL_DISABLED_PACKAGES_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().packageName())
                    .whereStrings().contains(PACKAGE_NAME)).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    originalDisabledPackages);
        }
    }

    @CanSetPolicyTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setUserControlDisabledPackages_toOneProtectedPackage() {
        List<String> originalDisabledPackages =
                sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        sDeviceState.dpc().componentName());

        sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(sDeviceState.dpc().componentName(),
                Arrays.asList(PACKAGE_NAME));
        try {
            assertThat(sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                    sDeviceState.dpc().componentName()))
                    .contains(PACKAGE_NAME);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    originalDisabledPackages);
        }
    }

    @CannotSetPolicyTest(policy = UserControlDisabledPackages.class)
    public void setUserControlDisabledPackages_notAllowedToSetProtectedPackages_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                        sDeviceState.dpc().componentName(),
                        Collections.emptyList()));
    }

    @CannotSetPolicyTest(policy = UserControlDisabledPackages.class)
    public void
    getUserControlDisabledPackages_notAllowedToRetrieveProtectedPackages_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        sDeviceState.dpc().componentName()));
    }

    @EnsureHasPermission(value = permission.FORCE_STOP_PACKAGES)
    @PolicyAppliesTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setUserControlDisabledPackages_launchActivity_verifyPackageNotStopped()
            throws Exception {
        Assume.assumeTrue("Needs to launch activity",
                TestApis.users().instrumented().canShowActivities());
        List<String> originalDisabledPackages =
                sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        sDeviceState.dpc().componentName());
        String testAppPackageName = sTestApp.packageName();

        try (TestAppInstance instance = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(), Arrays.asList(testAppPackageName));

            instance.activities().any().start();
            int processIdBeforeStopping = instance.process().pid();

            sActivityManager.forceStopPackage(testAppPackageName);

            try {
                assertPackageNotStopped(sTestApp.pkg(), processIdBeforeStopping);
            } finally {
                stopPackage(sTestApp.pkg());
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    originalDisabledPackages);
        }
    }

    @EnsureHasPermission(value = permission.FORCE_STOP_PACKAGES)
    @PolicyDoesNotApplyTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "new test")
    public void setUserControlDisabledPackages_launchActivity_verifyPackageStopped()
            throws Exception {
        Assume.assumeTrue("Needs to launch activity",
                TestApis.users().instrumented().canShowActivities());
        List<String> originalDisabledPackages =
                sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        sDeviceState.dpc().componentName());
        String testAppPackageName = sTestApp.packageName();

        try (TestAppInstance instance = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(), Arrays.asList(testAppPackageName));

            instance.activities().any().start();
            int processIdBeforeStopping = instance.process().pid();

            sActivityManager.forceStopPackage(testAppPackageName);

            try {
                assertPackageStopped(sTestApp.pkg(), processIdBeforeStopping);
            } finally {
                stopPackage(sTestApp.pkg());
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    originalDisabledPackages);
        }
    }

    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUserControlDisabledPackages",
            "android.app.admin.DevicePolicyManager#setUserControlDisabledPackages",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @PolicyAppliesTest(policy = UserControlDisabledPackages.class)
    public void getDevicePolicyState_setUserControlDisabledPackages_returnsPolicy() {
        String testAppPackageName = sTestApp.packageName();
        try (TestAppInstance instance = sTestApp.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                        sDeviceState.dpc().componentName(), Arrays.asList(testAppPackageName));

                PolicyState<Set<String>> policyState = PolicyEngineUtils.getStringSetPolicyState(
                        new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                        TestApis.users().instrumented().userHandle());

                assertThat(policyState.getCurrentResolvedPolicy()).contains(
                        sTestApp.packageName());
            } finally {
                sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                        sDeviceState.dpc().componentName(),
                        new ArrayList<>());
            }
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUserControlDisabledPackages"})
    // TODO: enable after adding the broadcast receiver to relevant test apps.
//    @PolicyAppliesTest(policy = UserControlDisabledPackages.class)
    @EnsureHasDeviceOwner(isPrimary = true)
    public void policyUpdateReceiver_setUserControlDisabledPackages_receivedPolicySetBroadcast() {
        String testAppPackageName = sTestApp.packageName();
        try (TestAppInstance instance = sTestApp.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                        sDeviceState.dpc().componentName(), Arrays.asList(testAppPackageName));

                PolicySetResultUtils.assertPolicySetResultReceived(
                        sDeviceState,
                        USER_CONTROL_DISABLED_PACKAGES_POLICY,
                        PolicyUpdateResult.RESULT_POLICY_SET, GLOBAL_USER_ID, new Bundle());
            } finally {
                sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                        sDeviceState.dpc().componentName(),
                        new ArrayList<>());
            }
        }
    }

    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUserControlDisabledPackages",
            "android.app.admin.DevicePolicyManager#setUserControlDisabledPackages",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @CanSetPolicyTest(policy = UserControlDisabledPackages.class, singleTestOnly = true)
    public void getDevicePolicyState_setUserControlDisabledPackages_returnsCorrectResolutionMechanism() {
        String testAppPackageName = sTestApp.packageName();
        try (TestAppInstance instance = sTestApp.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                        sDeviceState.dpc().componentName(), Arrays.asList(testAppPackageName));

                PolicyState<Set<String>> policyState = PolicyEngineUtils.getStringSetPolicyState(
                        new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                        TestApis.users().instrumented().userHandle());

                assertThat(policyState.getResolutionMechanism()).isEqualTo(
                        StringSetUnion.STRING_SET_UNION);
            } finally {
                sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                        sDeviceState.dpc().componentName(),
                        new ArrayList<>());
            }
        }
    }

    // TODO: use most recent test annotation
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUserControlDisabledPackages",
            "android.app.admin.DevicePolicyManager#setUserControlDisabledPackages"})
    @MostRestrictiveCoexistenceTest(policy = UserControlDisabledPackages.class)
    public void setUserControlDisabledPackages_bothSet_appliesBoth() {
        String testAppPackageName = sTestApp.packageName();
        String secondTestAppPackageName = sSecondTestApp.packageName();
        try (TestAppInstance instance = sTestApp.install()) {
            try (TestAppInstance secondInstance = sSecondTestApp.install()) {
                try {
                    sDeviceState.testApp(DPC_1).devicePolicyManager()
                            .setUserControlDisabledPackages(
                                    /* componentName= */ null,
                                    Arrays.asList(testAppPackageName));
                    sDeviceState.testApp(DPC_2).devicePolicyManager()
                            .setUserControlDisabledPackages(
                                    /* componentName= */ null,
                                    Arrays.asList(secondTestAppPackageName));

                    PolicyState<Set<String>> policyState =
                            PolicyEngineUtils.getStringSetPolicyState(
                                    new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                                    TestApis.users().instrumented().userHandle());

                    assertThat(policyState.getCurrentResolvedPolicy()).containsExactlyElementsIn(
                            Set.of(testAppPackageName, secondTestAppPackageName));
                    assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager()
                            .getUserControlDisabledPackages(/* componentName= */ null))
                            .containsExactlyElementsIn(
                                    Set.of(testAppPackageName, secondTestAppPackageName));
                    assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager()
                            .getUserControlDisabledPackages(/* componentName= */ null))
                            .containsExactlyElementsIn(
                                    Set.of(testAppPackageName, secondTestAppPackageName));
                } finally {
                    sDeviceState.testApp(DPC_1).devicePolicyManager()
                            .setUserControlDisabledPackages(
                                    /* componentName= */ null, new ArrayList<>());
                    sDeviceState.testApp(DPC_2).devicePolicyManager()
                            .setUserControlDisabledPackages(
                                    /* componentName= */ null, new ArrayList<>());
                }
            }
        }
    }

    // TODO: use most recent test annotation
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUserControlDisabledPackages",
            "android.app.admin.DevicePolicyManager#setUserControlDisabledPackages"})
    @MostRestrictiveCoexistenceTest(policy = UserControlDisabledPackages.class)
    public void setUserControlDisabledPackages_bothSetThenOneUnsets_appliesOne() {
        String testAppPackageName = sTestApp.packageName();
        String secondTestAppPackageName = sSecondTestApp.packageName();
        try (TestAppInstance instance = sTestApp.install()) {
            try (TestAppInstance secondInstance = sSecondTestApp.install()) {
                try {
                    sDeviceState.testApp(DPC_1).devicePolicyManager()
                            .setUserControlDisabledPackages(
                                    /* componentName= */ null,
                                    Arrays.asList(testAppPackageName));
                    sDeviceState.testApp(DPC_2).devicePolicyManager()
                            .setUserControlDisabledPackages(
                                    /* componentName= */ null,
                                    Arrays.asList(secondTestAppPackageName));
                    sDeviceState.testApp(DPC_1).devicePolicyManager()
                            .setUserControlDisabledPackages(
                                    /* componentName= */ null, new ArrayList<>());

                    PolicyState<Set<String>> policyState =
                            PolicyEngineUtils.getStringSetPolicyState(
                                    new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                                    TestApis.users().instrumented().userHandle());

                    assertThat(policyState.getCurrentResolvedPolicy()).containsExactly(
                            secondTestAppPackageName);
                    assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager()
                            .getUserControlDisabledPackages(/* componentName= */ null))
                            .containsExactly(secondTestAppPackageName);
                    assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager()
                            .getUserControlDisabledPackages(/* componentName= */ null))
                            .containsExactly(secondTestAppPackageName);
                } finally {
                    sDeviceState.testApp(DPC_1).devicePolicyManager()
                            .setUserControlDisabledPackages(
                                    /* componentName= */ null, new ArrayList<>());
                    sDeviceState.testApp(DPC_2).devicePolicyManager()
                            .setUserControlDisabledPackages(
                                    /* componentName= */ null, new ArrayList<>());
                }
            }
        }
    }

    // TODO: use most recent test annotation
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUserControlDisabledPackages",
            "android.app.admin.DevicePolicyManager#setUserControlDisabledPackages"})
    @MostRestrictiveCoexistenceTest(policy = UserControlDisabledPackages.class)
    public void setUserControlDisabledPackages_bothSetThenBothUnsets_nothingApplied() {
        String testAppPackageName = sTestApp.packageName();
        String secondTestAppPackageName = sSecondTestApp.packageName();
        try (TestAppInstance instance = sTestApp.install()) {
            try (TestAppInstance secondInstance = sSecondTestApp.install()) {
                try {
                    sDeviceState.testApp(DPC_1).devicePolicyManager()
                            .setUserControlDisabledPackages(
                                    /* componentName= */ null,
                                    Arrays.asList(testAppPackageName));
                    sDeviceState.testApp(DPC_2).devicePolicyManager()
                            .setUserControlDisabledPackages(
                                    /* componentName= */ null,
                                    Arrays.asList(secondTestAppPackageName));
                    sDeviceState.testApp(DPC_1).devicePolicyManager()
                            .setUserControlDisabledPackages(
                                    /* componentName= */ null, new ArrayList<>());
                    sDeviceState.testApp(DPC_2).devicePolicyManager()
                            .setUserControlDisabledPackages(
                                    /* componentName= */ null, new ArrayList<>());

                    PolicyState<Set<String>> policyState =
                            PolicyEngineUtils.getStringSetPolicyState(
                                    new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                                    TestApis.users().instrumented().userHandle());

                    assertThat(policyState).isNull();
                    assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager()
                            .getUserControlDisabledPackages(/* componentName= */ null))
                            .isEmpty();
                    assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager()
                            .getUserControlDisabledPackages(/* componentName= */ null))
                            .isEmpty();
                } finally {
                    sDeviceState.testApp(DPC_1).devicePolicyManager()
                            .setUserControlDisabledPackages(
                                    /* componentName= */ null, new ArrayList<>());
                    sDeviceState.testApp(DPC_2).devicePolicyManager()
                            .setUserControlDisabledPackages(
                                    /* componentName= */ null, new ArrayList<>());
                }
            }
        }
    }

    @PolicyAppliesTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUserControlDisabledPackages",
            "android.app.admin.DevicePolicyManager#getUserControlDisabledPackages"})
    public void setUserControlDisabledPackages_policyMigration_works() {
        TestApis.flags().set(
                NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "false");
        try {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(), Arrays.asList(PACKAGE_NAME));

            sLocalDevicePolicyManager.triggerDevicePolicyEngineMigration(true);
            TestApis.flags().set(
                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "true");

            PolicyState<Set<String>> policyState = PolicyEngineUtils.getStringSetPolicyState(
                    new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).contains(
                    PACKAGE_NAME);
            assertThat(sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                    sDeviceState.dpc().componentName()))
                    .contains(PACKAGE_NAME);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    new ArrayList<>());
            TestApis.flags().set(
                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, null);
        }
    }

    private void stopPackage(Package pkg) throws Exception {
        sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(sDeviceState.dpc().componentName(),
                Collections.emptyList());

        pkg.forceStop();
    }

    private void assertPackageStopped(Package pkg, int processIdBeforeStopping)
            throws Exception {
        Poll.forValue("Package " + pkg + " stopped",
                        () -> isProcessRunning(pkg, processIdBeforeStopping))
                .toBeEqualTo(false)
                .errorOnFail()
                .await();
    }

    private void assertPackageNotStopped(Package pkg, int processIdBeforeStopping)
            throws Exception {
        assertWithMessage("Package %s stopped", pkg)
                .that(isProcessRunning(pkg, processIdBeforeStopping)).isTrue();
    }

    private boolean isProcessRunning(Package pkg, int processIdBeforeStopping) throws Exception {
        return pkg.runningProcesses().stream().anyMatch(p -> p.pid() == processIdBeforeStopping);
    }
}
