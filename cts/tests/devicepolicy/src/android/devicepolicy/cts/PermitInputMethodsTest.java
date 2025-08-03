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

import static android.app.admin.DevicePolicyIdentifiers.PERMITTED_INPUT_METHODS_POLICY;
import static android.app.admin.TargetUser.LOCAL_USER_ID;

import static com.android.bedstead.harrier.annotations.enterprise.MostRestrictiveCoexistenceTest.DPC_1;
import static com.android.bedstead.harrier.annotations.enterprise.MostRestrictiveCoexistenceTest.DPC_2;
import static com.android.bedstead.nene.flags.CommonFlags.DevicePolicyManager.ENABLE_DEVICE_POLICY_ENGINE_FLAG;
import static com.android.bedstead.nene.flags.CommonFlags.NAMESPACE_DEVICE_POLICY_MANAGER;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.QUERY_ADMIN_POLICY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.MostRecent;
import android.app.admin.NoArgsPolicyKey;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateResult;
import android.devicepolicy.cts.utils.PolicyEngineUtils;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.os.Bundle;
import android.util.Log;

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
import com.android.bedstead.harrier.policies.PermittedInputMethods;
import com.android.bedstead.harrier.policies.PermittedSystemInputMethods;
import com.android.bedstead.harrier.policies.ScreenCaptureDisabled;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.inputmethods.InputMethod;
import com.android.bedstead.nene.packages.Package;
import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
public final class PermitInputMethodsTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String TAG = PermitInputMethodsTest.class.getSimpleName();

    private static final DevicePolicyManager sLocalDevicePolicyManager = TestApis.context()
            .instrumentedContext().getSystemService(DevicePolicyManager.class);

    private static final Set<String> SYSTEM_INPUT_METHODS_PACKAGES =
            TestApis.inputMethods().installedInputMethods().stream()
                    .map(InputMethod::pkg)
                    .filter(Package::hasSystemFlag)
                    .map(Package::packageName)
                    .collect(Collectors.toSet());

    private static final List<String> NON_SYSTEM_INPUT_METHOD_PACKAGES =
            TestApis.inputMethods().installedInputMethods().stream()
                    .map(InputMethod::pkg)
                    .filter(p -> !p.hasSystemFlag())
                    .map(Package::packageName)
                    .collect(Collectors.toList());

    @After
    public void teardown() {
        try {
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), /* packageNames= */ null);
        } catch (Exception e) {
            // Required for tests with invalid admins.
            Log.w(TAG, "Failed to clean up the permitted input methods", e);
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = {PermittedInputMethods.class, PermittedSystemInputMethods.class})
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, QUERY_ADMIN_POLICY})
    public void setPermittedInputMethods_allPermitted() {
        assertThat(sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                sDeviceState.dpc().componentName(), /* packageNames= */ null)).isTrue();

        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getPermittedInputMethods(sDeviceState.dpc().componentName())).isNull();
        assertThat(sLocalDevicePolicyManager.getPermittedInputMethods()).isNull();
        assertThat(sLocalDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).isNull();
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = {PermittedInputMethods.class, PermittedSystemInputMethods.class})
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, QUERY_ADMIN_POLICY})
    public void setPermittedInputMethods_doesNotThrowException() {
        sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                sDeviceState.dpc().componentName(), /* packageNames= */ null);
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = {PermittedInputMethods.class, PermittedSystemInputMethods.class},
            includeNonDeviceAdminStates = false)
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, QUERY_ADMIN_POLICY})
    public void setPermittedInputMethods_canNotSet_throwsException() {
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), /* packageNames= */ null);
        });
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = PermittedSystemInputMethods.class)
    public void setPermittedInputMethods_nonEmptyList_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), /* packageNames= */ List.of("package"));
        });
    }

    @Postsubmit(reason = "New test")
    @PolicyDoesNotApplyTest(policy = PermittedInputMethods.class)
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, QUERY_ADMIN_POLICY})
    public void setPermittedInputMethods_policyDoesNotApply_isNotSet() {
        assumeFalse("A system input method is required",
                SYSTEM_INPUT_METHODS_PACKAGES.isEmpty());

        List<String> enabledNonSystemImes = NON_SYSTEM_INPUT_METHOD_PACKAGES;

        assertThat(sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                sDeviceState.dpc().componentName(), /* packageNames= */ enabledNonSystemImes)
        ).isTrue();

        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getPermittedInputMethods(sDeviceState.dpc().componentName()))
                .containsExactlyElementsIn(enabledNonSystemImes);
        assertThat(sLocalDevicePolicyManager.getPermittedInputMethods()).isNull();
        assertThat(sLocalDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).isNull();
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = PermittedInputMethods.class)
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, QUERY_ADMIN_POLICY})
    public void setPermittedInputMethods_includesSetPlusSystem() {
        assumeFalse("A system input method is required",
                SYSTEM_INPUT_METHODS_PACKAGES.isEmpty());

        List<String> enabledNonSystemImes = NON_SYSTEM_INPUT_METHOD_PACKAGES;
        Set<String> permittedPlusSystem = new HashSet<>();
        permittedPlusSystem.addAll(SYSTEM_INPUT_METHODS_PACKAGES);
        permittedPlusSystem.addAll(enabledNonSystemImes);

        assertThat(sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                sDeviceState.dpc().componentName(), /* packageNames= */ enabledNonSystemImes)
        ).isTrue();

        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getPermittedInputMethods(sDeviceState.dpc().componentName()))
                .containsExactlyElementsIn(enabledNonSystemImes);
        assertThat(sLocalDevicePolicyManager.getPermittedInputMethods())
                .containsExactlyElementsIn(permittedPlusSystem);
        assertThat(sLocalDevicePolicyManager.getPermittedInputMethodsForCurrentUser())
                .containsExactlyElementsIn(permittedPlusSystem);
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = {PermittedInputMethods.class, PermittedSystemInputMethods.class})
    public void setPermittedInputMethods_packageNameTooLong_throwsException() {
        // Invalid package name - too long
        List<String> badMethods = List.of(new String(new char[1000]).replace('\0', 'A'));
        assertThrows(IllegalArgumentException.class, () ->
                sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                        sDeviceState.dpc().componentName(), badMethods));
    }

    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setPermittedInputMethods",
            "android.app.admin.DevicePolicyManager#getPermittedInputMethods",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @PolicyAppliesTest(policy = PermittedInputMethods.class)
    public void getDevicePolicyState_setPermittedInputMethods_returnsPolicy() {
        assumeFalse("A system input method is required",
                SYSTEM_INPUT_METHODS_PACKAGES.isEmpty());

        try {
            Set<String> permittedPlusSystem = new HashSet<>();
            permittedPlusSystem.addAll(SYSTEM_INPUT_METHODS_PACKAGES);
            permittedPlusSystem.addAll(NON_SYSTEM_INPUT_METHOD_PACKAGES);
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), NON_SYSTEM_INPUT_METHOD_PACKAGES);

            PolicyState<Set<String>> policyState = PolicyEngineUtils.getStringSetPolicyState(
                    new NoArgsPolicyKey(PERMITTED_INPUT_METHODS_POLICY),
                    TestApis.users().instrumented().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).containsExactlyElementsIn(
                    NON_SYSTEM_INPUT_METHOD_PACKAGES);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), /* packageNames= */ null);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setPermittedInputMethods"})
    // TODO: enable after adding the broadcast receiver to relevant test apps.
//    @PolicyAppliesTest(policy = PermittedInputMethods.class)
    @EnsureHasDeviceOwner(isPrimary = true)
    public void policyUpdateReceiver_setPermittedInputMethods_receivedPolicySetBroadcast() {
        assumeFalse("A system input method is required",
                SYSTEM_INPUT_METHODS_PACKAGES.isEmpty());

        try {
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), NON_SYSTEM_INPUT_METHOD_PACKAGES);

            PolicySetResultUtils.assertPolicySetResultReceived(
                    sDeviceState,
                    PERMITTED_INPUT_METHODS_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, new Bundle());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), /* packageNames= */ null);
        }
    }

    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setPermittedInputMethods",
            "android.app.admin.DevicePolicyManager#getPermittedInputMethods",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @CanSetPolicyTest(policy = PermittedInputMethods.class, singleTestOnly = true)
    public void getDevicePolicyState_setPermittedInputMethods_returnsCorrectResolutionMechanism() {
        assumeFalse("A system input method is required",
                SYSTEM_INPUT_METHODS_PACKAGES.isEmpty());

        try {
            Set<String> permittedPlusSystem = new HashSet<>();
            permittedPlusSystem.addAll(SYSTEM_INPUT_METHODS_PACKAGES);
            permittedPlusSystem.addAll(NON_SYSTEM_INPUT_METHOD_PACKAGES);
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), NON_SYSTEM_INPUT_METHOD_PACKAGES);

            PolicyState<Set<String>> policyState = PolicyEngineUtils.getStringSetPolicyState(
                    new NoArgsPolicyKey(PERMITTED_INPUT_METHODS_POLICY),
                    TestApis.users().instrumented().userHandle());

            assertThat(PolicyEngineUtils.getMostRecentStringSetMechanism(policyState))
                    .isEqualTo(MostRecent.MOST_RECENT);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), /* packageNames= */ null);
        }
    }

    // TODO: use most recent test annotation
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setPermittedInputMethods",
            "android.app.admin.DevicePolicyManager#getPermittedInputMethods"})
    @MostRestrictiveCoexistenceTest(policy = PermittedInputMethods.class)
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, QUERY_ADMIN_POLICY})
    public void setPermittedInputMethods_bothSet_appliesMostRecent() {
        try {
            List<String> firstPackages = new ArrayList<>(NON_SYSTEM_INPUT_METHOD_PACKAGES);
            List<String> secondPackages = new ArrayList<>(NON_SYSTEM_INPUT_METHOD_PACKAGES);
            secondPackages.add("additionalPackage");
            Set<String> secondPackagesPlusSystem = new HashSet<>(secondPackages);
            secondPackagesPlusSystem.addAll(SYSTEM_INPUT_METHODS_PACKAGES);
            sDeviceState.testApp(DPC_1).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, firstPackages);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, secondPackages);


            PolicyState<Set<String>> policyState = PolicyEngineUtils.getStringSetPolicyState(
                    new NoArgsPolicyKey(PERMITTED_INPUT_METHODS_POLICY),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).containsExactlyElementsIn(
                    secondPackages);
            assertThat(sLocalDevicePolicyManager.getPermittedInputMethods())
                    .containsExactlyElementsIn(secondPackagesPlusSystem);

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, /* packageNames= */ null);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, /* packageNames= */ null);
        }
    }

    // TODO: use most recent test annotation
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setPermittedInputMethods",
            "android.app.admin.DevicePolicyManager#getPermittedInputMethods"})
    @MostRestrictiveCoexistenceTest(policy = PermittedInputMethods.class)
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, QUERY_ADMIN_POLICY})
    public void setPermittedInputMethods_bothSetReverseOrder_appliesMostRecent() {
        try {
            List<String> firstPackages = new ArrayList<>(NON_SYSTEM_INPUT_METHOD_PACKAGES);
            List<String> secondPackages = new ArrayList<>(NON_SYSTEM_INPUT_METHOD_PACKAGES);
            secondPackages.add("additionalPackage");
            Set<String> firstPackagesPlusSystem = new HashSet<>(firstPackages);
            firstPackagesPlusSystem.addAll(SYSTEM_INPUT_METHODS_PACKAGES);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, secondPackages);
            sDeviceState.testApp(DPC_1).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, firstPackages);


            PolicyState<Set<String>> policyState = PolicyEngineUtils.getStringSetPolicyState(
                    new NoArgsPolicyKey(PERMITTED_INPUT_METHODS_POLICY),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).containsExactlyElementsIn(
                    firstPackages);
            assertThat(sLocalDevicePolicyManager.getPermittedInputMethods())
                    .containsExactlyElementsIn(firstPackagesPlusSystem);

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, /* packageNames= */ null);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, /* packageNames= */ null);
        }
    }

    // TODO: use most recent test annotation
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setPermittedInputMethods",
            "android.app.admin.DevicePolicyManager#getPermittedInputMethods"})
    @MostRestrictiveCoexistenceTest(policy = PermittedInputMethods.class)
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, QUERY_ADMIN_POLICY})
    public void setPermittedInputMethods_bothSetThenOneUnsets_setsToPrevious() {
        try {
            List<String> firstPackages = new ArrayList<>(NON_SYSTEM_INPUT_METHOD_PACKAGES);
            List<String> secondPackages = new ArrayList<>(NON_SYSTEM_INPUT_METHOD_PACKAGES);
            secondPackages.add("additionalPackage");
            Set<String> secondPackagesPlusSystem = new HashSet<>(secondPackages);
            secondPackagesPlusSystem.addAll(SYSTEM_INPUT_METHODS_PACKAGES);
            sDeviceState.testApp(DPC_1).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, firstPackages);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, secondPackages);
            sDeviceState.testApp(DPC_1).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, /* packageNames= */ null);


            PolicyState<Set<String>> policyState = PolicyEngineUtils.getStringSetPolicyState(
                    new NoArgsPolicyKey(PERMITTED_INPUT_METHODS_POLICY),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).containsExactlyElementsIn(
                    secondPackages);
            assertThat(sLocalDevicePolicyManager.getPermittedInputMethods())
                    .containsExactlyElementsIn(secondPackagesPlusSystem);

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, /* packageNames= */ null);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, /* packageNames= */ null);
        }
    }

    // TODO: use most recent test annotation
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setPermittedInputMethods",
            "android.app.admin.DevicePolicyManager#getPermittedInputMethods"})
    @MostRestrictiveCoexistenceTest(policy = PermittedInputMethods.class)
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, QUERY_ADMIN_POLICY})
    public void setPermittedInputMethods_bothSetThenBothUnsets_unsets() {
        try {
            List<String> firstPackages = new ArrayList<>(NON_SYSTEM_INPUT_METHOD_PACKAGES);
            List<String> secondPackages = new ArrayList<>(NON_SYSTEM_INPUT_METHOD_PACKAGES);
            secondPackages.add("additionalPackage");
            Set<String> secondPackagesPlusSystem = new HashSet<>(secondPackages);
            secondPackagesPlusSystem.addAll(SYSTEM_INPUT_METHODS_PACKAGES);
            sDeviceState.testApp(DPC_1).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, firstPackages);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, secondPackages);
            sDeviceState.testApp(DPC_1).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, /* packageNames= */ null);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, /* packageNames= */ null);


            PolicyState<Set<String>> policyState = PolicyEngineUtils.getStringSetPolicyState(
                    new NoArgsPolicyKey(PERMITTED_INPUT_METHODS_POLICY),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState).isNull();
            assertThat(sLocalDevicePolicyManager.getPermittedInputMethods()).isNull();

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, /* packageNames= */ null);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setPermittedInputMethods(
                    /* componentName= */ null, /* packageNames= */ null);
        }
    }

    @PolicyAppliesTest(policy = PermittedInputMethods.class)
    @Postsubmit(reason = "new test")
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, QUERY_ADMIN_POLICY,
            MANAGE_PROFILE_AND_DEVICE_OWNERS})
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setPermittedInputMethods",
            "android.app.admin.DevicePolicyManager#getPermittedInputMethods"})
    public void setPermittedInputMethods_policyMigration_works() {
        try {
            assumeFalse("A system input method is required",
                    SYSTEM_INPUT_METHODS_PACKAGES.isEmpty());

            TestApis.flags().set(
                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "false");
            List<String> enabledNonSystemImes = NON_SYSTEM_INPUT_METHOD_PACKAGES;
            Set<String> permittedPlusSystem = new HashSet<>();
            permittedPlusSystem.addAll(SYSTEM_INPUT_METHODS_PACKAGES);
            permittedPlusSystem.addAll(enabledNonSystemImes);
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), /* packageNames= */ enabledNonSystemImes);

            sLocalDevicePolicyManager.triggerDevicePolicyEngineMigration(true);
            TestApis.flags().set(
                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "true");

            PolicyState<Set<String>> policyState = PolicyEngineUtils.getStringSetPolicyState(
                    new NoArgsPolicyKey(PERMITTED_INPUT_METHODS_POLICY),
                    TestApis.users().instrumented().userHandle());
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPermittedInputMethods(sDeviceState.dpc().componentName()))
                    .containsExactlyElementsIn(enabledNonSystemImes);
            assertThat(sLocalDevicePolicyManager.getPermittedInputMethods())
                    .containsExactlyElementsIn(permittedPlusSystem);
            assertThat(sLocalDevicePolicyManager.getPermittedInputMethodsForCurrentUser())
                    .containsExactlyElementsIn(permittedPlusSystem);
            assertThat(policyState.getCurrentResolvedPolicy())
                    .containsExactlyElementsIn(enabledNonSystemImes);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), /* packageNames= */ null);
            TestApis.flags().set(
                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, null);
        }
    }
}
