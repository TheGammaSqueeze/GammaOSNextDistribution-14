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

package com.android.bedstead.harrier;

import static com.android.bedstead.harrier.UserType.INITIAL_USER;
import static com.android.bedstead.harrier.UserType.WORK_PROFILE;
import static com.android.bedstead.harrier.test.TestPolicy.POLICY_ARGUMENT_ONE;
import static com.android.bedstead.harrier.test.TestPolicy.POLICY_ARGUMENT_TWO;
import static com.android.bedstead.nene.flags.CommonFlags.DevicePolicyManager.ENABLE_DEVICE_POLICY_ENGINE_FLAG;
import static com.android.bedstead.nene.flags.CommonFlags.NAMESPACE_DEVICE_POLICY_MANAGER;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_PROFILES;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.BeforeClass;
import com.android.bedstead.harrier.annotations.CrossUserTest;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureRunsLate;
import com.android.bedstead.harrier.annotations.EnumTestParameter;
import com.android.bedstead.harrier.annotations.IntTestParameter;
import com.android.bedstead.harrier.annotations.PermissionTest;
import com.android.bedstead.harrier.annotations.PolicyArgument;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.RunWithFeatureFlagEnabledAndDisabled;
import com.android.bedstead.harrier.annotations.StringTestParameter;
import com.android.bedstead.harrier.annotations.UserPair;
import com.android.bedstead.harrier.annotations.UserTest;
import com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnParentOfProfileOwnerUsingParentInstance;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnParentOfProfileOwnerWithNoDeviceOwner;
import com.android.bedstead.harrier.exceptions.RestartTestException;
import com.android.bedstead.harrier.policies.LockTask;
import com.android.bedstead.harrier.test.TestPolicy;
import com.android.bedstead.nene.TestApis;
import com.android.queryable.annotations.IntegerQuery;
import com.android.queryable.annotations.Query;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public class BedsteadJUnit4Test {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @StringTestParameter({"A", "B"})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface TwoValuesStringTestParameter {

    }

    private enum EnumWithThreeValues {
        ONE, TWO, THREE
    }

    private static int sSimpleParameterizedCalls = 0;
    private static int sMultipleSimpleParameterizedCalls = 0;
    private static int sBedsteadParameterizedCalls = 0;
    private static int sBedsteadPlusSimpleParameterizedCalls = 0;
    private static int sIndirectParameterizedCalls = 0;
    private static int sIntParameterizedCalls = 0;
    private static int sEnumParameterizedCalls = 0;
    private static int sFeatureFlagTestCalls = 0;

    private static final String NAMESPACE = NAMESPACE_DEVICE_POLICY_MANAGER;
    private static final String KEY = ENABLE_DEVICE_POLICY_ENGINE_FLAG;

    private static int sBeforeClassCalls = 0;
    private static int sBeforeCalls = 0;

    private static final Set<Integer> sPolicyAppliesTestArguments = new HashSet<>();
    private static final Set<Integer> sPolicyDoesNotApplyTestArguments = new HashSet<>();
    private static final Set<Integer> sCanSetPolicyTestArguments = new HashSet<>();
    private static final Set<Integer> sCannotSetPolicyTestArguments = new HashSet<>();

    @AfterClass
    public static void afterClass() {
        assertThat(sSimpleParameterizedCalls).isEqualTo(2);
        assertThat(sMultipleSimpleParameterizedCalls).isEqualTo(4);
        assertThat(sBedsteadParameterizedCalls).isEqualTo(2);
        assertThat(sBedsteadPlusSimpleParameterizedCalls).isEqualTo(4);
        assertThat(sIndirectParameterizedCalls).isEqualTo(2);
        assertThat(sIntParameterizedCalls).isEqualTo(2);
        assertThat(sEnumParameterizedCalls).isEqualTo(3);
        assertThat(sFeatureFlagTestCalls).isEqualTo(2);

        sPolicyAppliesTestArguments.clear();
        sPolicyDoesNotApplyTestArguments.clear();
        sCanSetPolicyTestArguments.clear();
        sCannotSetPolicyTestArguments.clear();
    }

    @BeforeClass
    public static void beforeClass() {
        sBeforeClassCalls += 1;
    }

    @Before
    public void before() {
        sBeforeCalls += 1;
    }

    @Test
    @IncludeRunOnParentOfProfileOwnerUsingParentInstance
    @IncludeRunOnParentOfProfileOwnerWithNoDeviceOwner
    public void bedsteadParameterized() {
        sBedsteadParameterizedCalls += 1;
    }

    @Test
    @IncludeRunOnParentOfProfileOwnerUsingParentInstance
    @IncludeRunOnParentOfProfileOwnerWithNoDeviceOwner
    public void bedsteadPlusSimpleParameterized(@StringTestParameter({"A", "B"}) String argument) {
        sBedsteadPlusSimpleParameterizedCalls += 1;
    }

    @Test
    public void simpleParameterized(@StringTestParameter({"A", "B"}) String argument) {
        sSimpleParameterizedCalls += 1;
    }

    @Test
    public void multipleSimpleParameterized(
            @StringTestParameter({"A", "B"}) String argument1,
            @StringTestParameter({"C", "D"}) String argument2) {
        sMultipleSimpleParameterizedCalls += 1;
    }

    @Test
    public void indirectParameterized(@TwoValuesStringTestParameter String argument) {
        sIndirectParameterizedCalls += 1;
    }

    @Test
    public void intParameterized(@IntTestParameter({1, 2}) int argument) {
        sIntParameterizedCalls += 1;
    }

    @Test
    public void enumParameterized(
            @EnumTestParameter(EnumWithThreeValues.class) EnumWithThreeValues argument) {
        sEnumParameterizedCalls += 1;
    }

    @PermissionTest({INTERACT_ACROSS_PROFILES, INTERACT_ACROSS_USERS})
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    @Test
    public void permissionTestAnnotation_generatesRunsWithOnePermissionOrOther() {
        assertThat(TestApis.permissions().hasPermission(INTERACT_ACROSS_USERS_FULL)).isTrue();
        if (TestApis.permissions().hasPermission(INTERACT_ACROSS_PROFILES)) {
            assertThat(TestApis.permissions().hasPermission(INTERACT_ACROSS_USERS)).isFalse();
        } else {
            assertThat(TestApis.permissions().hasPermission(INTERACT_ACROSS_USERS)).isTrue();
        }
    }

    @UserTest({INITIAL_USER, WORK_PROFILE})
    @Test
    public void userTestAnnotation_isRunningOnCorrectUsers() {
        if (!TestApis.users().instrumented().equals(sDeviceState.initialUser())) {
            assertThat(TestApis.users().instrumented()).isEqualTo(sDeviceState.workProfile());
        }
    }

    @CrossUserTest({
            @UserPair(from = INITIAL_USER, to = WORK_PROFILE),
            @UserPair(from = WORK_PROFILE, to = INITIAL_USER),
    })
    @Test
    public void crossUserTestAnnotation_isRunningWithCorrectUserPairs() {
        if (TestApis.users().instrumented().equals(sDeviceState.initialUser())) {
            assertThat(sDeviceState.otherUser()).isEqualTo(sDeviceState.workProfile());
        } else {
            assertThat(TestApis.users().instrumented()).isEqualTo(sDeviceState.workProfile());
            assertThat(sDeviceState.otherUser()).isEqualTo(sDeviceState.initialUser());
        }
    }

    private static int sTestRuns = 0;

    @Test
    public void throwsRestartTestException_restartsTest() {
        if (sTestRuns < 2) {
            sTestRuns++;
            throw new RestartTestException("Testing that this restarts");
        }
        try {
            assertThat(sTestRuns).isEqualTo(2);
        } finally {
            sTestRuns = 0;
        }
    }

    @Test
    public void throwsRestartTestException_setupRunsWithEachRestart() {
        if (sTestRuns < 1) {
            sBeforeClassCalls = 0;
            sBeforeCalls = 0;
            sTestRuns++;
            throw new RestartTestException("Testing that this restarts and re-calls before");
        }
        try {
            assertThat(sBeforeClassCalls).isEqualTo(0);
            assertThat(sBeforeCalls).isEqualTo(1);
        } finally {
            sTestRuns = 0;
        }
    }

    @RequireRunOnInitialUser
    @Test
    public void requireRunOnInitialUser_runsOnInitialUser() {
        assertThat(TestApis.users().instrumented()).isEqualTo(TestApis.users().initial());
    }

    @RunWithFeatureFlagEnabledAndDisabled(namespace = NAMESPACE, key = KEY)
    @Test
    public void runWithFeatureFlagEnabledAndDisabledAnnotation_runs() {
        sFeatureFlagTestCalls += 1;
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion = @IntegerQuery(isEqualTo = 28))
    )
    public void additionalQueryParameters_policyAppliesTest_isRespected() {
        assertThat(sDeviceState.dpc().testApp().targetSdkVersion()).isEqualTo(28);
    }

    @PolicyDoesNotApplyTest(policy = LockTask.class)
    @AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion = @IntegerQuery(isEqualTo = 28))
    )
    public void additionalQueryParameters_policyDoesNotApplyTest_isRespected() {
        assertThat(sDeviceState.dpc().testApp().targetSdkVersion()).isEqualTo(28);
    }

    @CanSetPolicyTest(policy = LockTask.class)
    @AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion = @IntegerQuery(isEqualTo = 28))
    )
    public void additionalQueryParameters_canSetPolicyTest_isRespected() {
        assertThat(sDeviceState.dpc().testApp().targetSdkVersion()).isEqualTo(28);
    }

    @CannotSetPolicyTest(policy = LockTask.class)
    @AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion = @IntegerQuery(isEqualTo = 28))
    )
    public void additionalQueryParameters_cannotSetPolicyTest_isRespected() {
        assertThat(sDeviceState.dpc().testApp().targetSdkVersion()).isEqualTo(28);
    }

    @PolicyAppliesTest(policy = {
            TestPolicy.class
    })
    @Postsubmit(reason = "new test")
    public void policyAppliesTestAnnotation_withArguments_shouldApply(@PolicyArgument int flag) {
        sPolicyAppliesTestArguments.add(flag);
    }

    @PolicyDoesNotApplyTest(policy = {
            TestPolicy.class
    })
    @Postsubmit(reason = "new test")
    public void policyDoesNotApplyTestAnnotation_withArguments_shouldApply(
            @PolicyArgument int flag) {
        sPolicyDoesNotApplyTestArguments.add(flag);
    }

    @CanSetPolicyTest(policy = {
            TestPolicy.class
    })
    @Postsubmit(reason = "new test")
    public void canSetPolicyTestAnnotation_withArguments_shouldApply(
            @PolicyArgument int flag) {
        sCanSetPolicyTestArguments.add(flag);
    }

    @CannotSetPolicyTest(policy = {
            TestPolicy.class
    })
    @Postsubmit(reason = "new test")
    public void cannotSetPolicyTestAnnotation_withArguments_shouldApply(
            @PolicyArgument int flag) {
        sCannotSetPolicyTestArguments.add(flag);
    }

    /**
     * This test runs after {@code policyAppliesTestAnnotation_withArguments_shouldApply} and
     * asserts if {@code policyAppliesTestAnnotation_withArguments_shouldApply()} ran for all the
     * arguments specified in the policy used in that test.
     */
    @EnsureRunsLate
    @Test
    public void policyAppliesTestAnnotation_withArguments_containsAllPolicyArguments() {
        assertThat(sPolicyAppliesTestArguments).containsExactly(
                POLICY_ARGUMENT_ONE,
                POLICY_ARGUMENT_TWO);
    }

    /**
     * This test runs after {@code policyDoesNotApplyTestAnnotation_withArguments_shouldApply} and
     * asserts if {@code policyDoesNotApplyTestAnnotation_withArguments_shouldApply()} ran for all
     * the arguments specified in the policy used in that test.
     */
    @EnsureRunsLate
    @Test
    public void policyDoesNotApplyTestAnnotation_withArguments_containsAllPolicyArguments() {
        assertThat(sPolicyAppliesTestArguments).containsExactly(
                POLICY_ARGUMENT_ONE,
                POLICY_ARGUMENT_TWO);
    }

    /**
     * This test runs after
     * {@code canSetPolicyTestAnnotation_withArguments_shouldApply} and
     * asserts if
     * {@code canSetPolicyTestAnnotation_withArguments_shouldApply()} ran for
     * all the arguments specified in the policy used in that test.
     */
    @EnsureRunsLate
    @Test
    public void canSetPolicyTestAnnotation_withArguments_containsAllPolicyArguments() {
        assertThat(sCanSetPolicyTestArguments).containsExactly(
                POLICY_ARGUMENT_ONE,
                POLICY_ARGUMENT_TWO);
    }

    /**
     * This test runs after
     * {@code cannotSetPolicyTestAnnotation_withArguments_shouldApply} and
     * asserts if
     * {@code cannotSetPolicyTestAnnotation_withArguments_shouldApply()} ran for
     * all the arguments specified in the policy used in that test.
     */
    @EnsureRunsLate
    @Test
    public void cannotSetPolicyTestAnnotation_withArguments_containsAllPolicyArguments() {
        assertThat(sCannotSetPolicyTestArguments).containsExactly(
                POLICY_ARGUMENT_ONE,
                POLICY_ARGUMENT_TWO);
    }
}
