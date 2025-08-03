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

import static android.app.admin.DevicePolicyIdentifiers.ACCOUNT_MANAGEMENT_DISABLED_POLICY;
import static android.app.admin.TargetUser.LOCAL_USER_ID;
import static android.devicepolicy.cts.utils.PolicyEngineUtils.TRUE_MORE_RESTRICTIVE;
import static android.os.UserManager.DISALLOW_MODIFY_ACCOUNTS;

import static com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest.LESS_IMPORTANT;
import static com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest.MORE_IMPORTANT;
import static com.android.bedstead.harrier.annotations.enterprise.MostRestrictiveCoexistenceTest.DPC_1;
import static com.android.bedstead.harrier.annotations.enterprise.MostRestrictiveCoexistenceTest.DPC_2;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.nene.flags.CommonFlags.DevicePolicyManager.ENABLE_DEVICE_POLICY_ENGINE_FLAG;
import static com.android.bedstead.nene.flags.CommonFlags.NAMESPACE_DEVICE_POLICY_MANAGER;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.OperationCanceledException;
import android.app.admin.AccountTypePolicyKey;
import android.app.admin.DevicePolicyManager;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateResult;
import android.content.Context;
import android.devicepolicy.cts.utils.PolicyEngineUtils;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.os.Bundle;
import android.os.UserManager;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasAccount;
import com.android.bedstead.harrier.annotations.EnsureHasAccountAuthenticator;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest;
import com.android.bedstead.harrier.annotations.enterprise.MostRestrictiveCoexistenceTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.AccountManagement;
import com.android.bedstead.harrier.policies.DisallowModifyAccounts;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.accounts.AccountReference;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.compatibility.common.util.ApiTest;
import com.android.bedstead.nene.userrestrictions.CommonUserRestrictions;

import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(BedsteadJUnit4.class)
@EnsureHasAccountAuthenticator
public final class AccountManagementTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private static final DevicePolicyManager sLocalDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);
    private static final AccountManager sLocalAccountManager =
            sContext.getSystemService(AccountManager.class);

    private static final String ACCOUNT_TYPE = "android.devicepolicy.cts";
    private static final String AUTH_TOKEN_TYPE = "testAuthTokenType";
    private static final String[] REQUIRED_FEATURES =
            new String[]{"testRequiredFeature1", "testRequiredFeature2"};
    private static final String REQUIRED_FEATURES_STR = "testRequiredFeature1;testRequiredFeature2";


    private AccountManager mAccountManager;

    @Before
    public void setUp() {
        mAccountManager = sContext.getSystemService(AccountManager.class);
    }

    // TODO: Fill out PolicyApplies and PolicyDoesNotApply tests

    @CanSetPolicyTest(policy = AccountManagement.class)
    public void getAccountTypesWithManagementDisabled_emptyByDefault() {
        assertThat(sDeviceState.dpc().devicePolicyManager().getAccountTypesWithManagementDisabled())
                .isEmpty();
    }

    @Postsubmit(reason = "new test")
    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = AccountManagement.class, includeNonDeviceAdminStates = false)
    public void setAccountTypesWithManagementDisabled_invalidAdmin_throwsException() {
        Exception exception = assertThrows(Exception.class, () ->
                sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                        sDeviceState.dpc().componentName(),
                        sDeviceState.accounts().accountType(), /* disabled= */ false));

        assertTrue("Expected OperationCanceledException or SecurityException to be thrown",
                (exception instanceof OperationCanceledException)
                        || (exception instanceof SecurityException));
    }

    @CanSetPolicyTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_disableAccountType_works() {
        try {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getAccountTypesWithManagementDisabled()).asList().contains(
                    sDeviceState.accounts().accountType());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.accounts().accountType(), /* disabled= */ false);
        }
    }

    @CanSetPolicyTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_addSameAccountTypeTwice_presentOnlyOnce() {
        try {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.accounts().accountType(),
                    /* disabled= */ true);
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            assertThat(
                    Arrays.stream(sDeviceState.dpc().devicePolicyManager()
                                    .getAccountTypesWithManagementDisabled())
                            .filter(s -> s.equals(sDeviceState.accounts().accountType()))
                            .count()).isEqualTo(1);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @CanSetPolicyTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_disableThenEnable_notDisabled() {
        sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                sDeviceState.dpc().componentName(),
                sDeviceState.accounts().accountType(),
                /* disabled= */ true);
        sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                sDeviceState.dpc().componentName(),
                sDeviceState.accounts().accountType(),
                /* disabled= */ false);

        assertThat(sDeviceState.dpc().devicePolicyManager().getAccountTypesWithManagementDisabled())
                .asList().doesNotContain(
                        sDeviceState.accounts().accountType());
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AccountManagement.class)
    // Not passing for permission based caller as AccountManagerService is special casing DO/PO
    public void addAccount_fromDpcWithAccountManagementDisabled_accountAdded()
            throws Exception {
        Assume.assumeTrue("Only makes sense on DPC user",
                TestApis.users().instrumented().equals(sDeviceState.dpc().user()));
        try {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            // Management is disabled, but the DO/PO is still allowed to use the APIs

            try (AccountReference account = TestApis.accounts().wrap(
                            sDeviceState.dpc().user(),
                            sDeviceState.dpc().accountManager())
                    .addAccount()
                    .type(sDeviceState.accounts().accountType())
                    .add()) {
                assertThat(sDeviceState.accounts().allAccounts()).contains(account);
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    // TODO: Rewrite DISALLOW_MODIFY_ACCOUNTS tests using new user restriction test structure

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DisallowModifyAccounts.class)
    // Not passing for permission based caller as AccountManagerService is special casing DO/PO
    public void addAccount_fromDpcWithDisallowModifyAccountsRestriction_accountAdded()
            throws Exception {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_MODIFY_ACCOUNTS);

            // Management is disabled, but the DO/PO is still allowed to use the APIs
            try (AccountReference account = TestApis.accounts().wrap(
                            sDeviceState.dpc().user(),
                            sDeviceState.dpc().accountManager())
                    .addAccount()
                    .type(sDeviceState.accounts().accountType())
                    .add()) {
                assertThat(sDeviceState.accounts().allAccounts()).contains(account);
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_MODIFY_ACCOUNTS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DisallowModifyAccounts.class)
    // Not passing for permission based caller as AccountManagerService is special casing DO/PO
    public void removeAccount_fromDpcWithDisallowModifyAccountsRestriction_accountRemoved()
            throws Exception {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_MODIFY_ACCOUNTS);

            // Management is disabled, but the DO/PO is still allowed to use the APIs
            AccountReference account = TestApis.accounts().wrap(
                            sDeviceState.dpc().user(), sDeviceState.dpc().accountManager())
                    .addAccount()
                    .type(sDeviceState.accounts().accountType())
                    .add();

            Bundle result = sDeviceState.dpc().accountManager().removeAccount(
                            account.account(),
                            /* activity= */ null,
                            /* callback= */  null,
                            /* handler= */ null)
                    .getResult();

            assertThat(result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT)).isTrue();
            assertThat(sDeviceState.accounts().allAccounts()).doesNotContain(account);
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_MODIFY_ACCOUNTS);
        }
    }

    @PolicyAppliesTest(policy = DisallowModifyAccounts.class)
    public void addAccount_withDisallowModifyAccountsRestriction_throwsException() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), UserManager.DISALLOW_MODIFY_ACCOUNTS);

            assertThrows(OperationCanceledException.class, () ->
                    mAccountManager.addAccount(
                            sDeviceState.accounts().accountType(),
                            /* authTokenType= */ null,
                            /* requiredFeatures= */ null,
                            /* addAccountOptions= */ null,
                            /* activity= */ null,
                            /* callback= */ null,
                            /* handler= */ null).getResult());
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), UserManager.DISALLOW_MODIFY_ACCOUNTS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DisallowModifyAccounts.class)
    @EnsureDoesNotHaveUserRestriction(CommonUserRestrictions.DISALLOW_MODIFY_ACCOUNTS)
    public void removeAccount_withDisallowModifyAccountsRestriction_throwsException()
            throws Exception {
        AccountReference account = null;
        try {
            account = sDeviceState.accounts().addAccount().add();

            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), UserManager.DISALLOW_MODIFY_ACCOUNTS);

            NeneException e = assertThrows(NeneException.class, account::remove);
            assertThat(e).hasCauseThat().isInstanceOf(OperationCanceledException.class);
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), UserManager.DISALLOW_MODIFY_ACCOUNTS);

            if (account != null) {
                account.remove();
            }
        }
    }

    @PolicyAppliesTest(policy = AccountManagement.class)
    public void addAccount_withAccountManagementDisabled_throwsException() {
        try {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            assertThrows(Exception.class, () ->
                    sDeviceState.accounts().addAccount().add());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AccountManagement.class)
    @EnsureHasAccount
    @EnsureDoesNotHaveUserRestriction(CommonUserRestrictions.DISALLOW_MODIFY_ACCOUNTS)
    public void removeAccount_withAccountManagementDisabled_throwsException()
            throws Exception {
        try {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.account().type(),
                    /* disabled= */ true);

            NeneException e = assertThrows(NeneException.class, () ->
                    sDeviceState.account().remove());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_accountTypeTooLong_throws() {
        // String too long for account type, cannot be serialized correctly
        String badAccountType = new String(new char[100000]).replace('\0', 'A');
        assertThrows(IllegalArgumentException.class, () ->
                sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                        sDeviceState.dpc().componentName(), badAccountType, /* disabled= */ false));
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setAccountManagementDisabled",
            "android.app.admin.DevicePolicyManager#getAccountTypesWithManagementDisabled",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @PolicyAppliesTest(policy = AccountManagement.class)
    public void getDevicePolicyState_setAccountManagementDisabled_returnsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            sDeviceState.accounts().accountType()),
                    TestApis.users().instrumented().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setAccountManagementDisabled",
            "android.app.admin.DevicePolicyManager#getAccountTypesWithManagementDisabled"})
    // TODO: enable after adding the broadcast receiver to relevant test apps.
//    @PolicyAppliesTest(policy = AccountManagement.class)
    @EnsureHasDeviceOwner(isPrimary = true)
    public void policyUpdateReceiver_setAccountManagementDisabled_receivedPolicySetBroadcast() {
        try {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            PolicySetResultUtils.assertPolicySetResultReceived(
                    sDeviceState,
                    ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, new Bundle());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setAccountManagementDisabled",
            "android.app.admin.DevicePolicyManager#getAccountTypesWithManagementDisabled",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @CanSetPolicyTest(policy = AccountManagement.class, singleTestOnly = true)
    public void getDevicePolicyState_setAccountManagementDisabled_returnsCorrectResolutionMechanism() {
        try {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            sDeviceState.accounts().accountType()),
                    TestApis.users().instrumented().userHandle());

            assertThat(PolicyEngineUtils.getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(TRUE_MORE_RESTRICTIVE);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setAccountManagementDisabled",
            "android.app.admin.DevicePolicyManager#getAccountTypesWithManagementDisabled"})
    @MostRestrictiveCoexistenceTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_bothTrue_isAccountManagementDisabledIsTrue() {
        try {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ true);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager()
                    .getAccountTypesWithManagementDisabled()).asList().contains(
                    sDeviceState.accounts().accountType());
            assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager()
                    .getAccountTypesWithManagementDisabled()).asList().contains(
                    sDeviceState.accounts().accountType());
            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            sDeviceState.accounts().accountType()),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
            assertThrows(Exception.class, () ->
                    sDeviceState.accounts().addAccount().add());

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setAccountManagementDisabled",
            "android.app.admin.DevicePolicyManager#getAccountTypesWithManagementDisabled"})
    @MostRestrictiveCoexistenceTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_bothFalse_isAccountManagementDisabledIsFalse() {
        sDeviceState.testApp(DPC_1).devicePolicyManager().setAccountManagementDisabled(
                /* componentName= */ null, sDeviceState.accounts().accountType(),
                /* disabled= */ false);
        sDeviceState.testApp(DPC_2).devicePolicyManager().setAccountManagementDisabled(
                /* componentName= */ null, sDeviceState.accounts().accountType(),
                /* disabled= */ false);

        assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager()
                .getAccountTypesWithManagementDisabled()).asList().doesNotContain(
                sDeviceState.accounts().accountType());
        assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager()
                .getAccountTypesWithManagementDisabled()).asList().doesNotContain(
                sDeviceState.accounts().accountType());
        PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                new AccountTypePolicyKey(
                        ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                        sDeviceState.accounts().accountType()),
                TestApis.users().instrumented().userHandle());
        assertThat(policyState).isNull();
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setAccountManagementDisabled",
            "android.app.admin.DevicePolicyManager#getAccountTypesWithManagementDisabled"})
    @MostRestrictiveCoexistenceTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_differentValues_isAccountManagementDisabledIsTrue() {
        try {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ true);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ false);

            assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager()
                    .getAccountTypesWithManagementDisabled()).asList().contains(
                    sDeviceState.accounts().accountType());
            assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager()
                    .getAccountTypesWithManagementDisabled()).asList().contains(
                    sDeviceState.accounts().accountType());
            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            sDeviceState.accounts().accountType()),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
            assertThrows(Exception.class, () ->
                    sDeviceState.accounts().addAccount().add());

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setAccountManagementDisabled",
            "android.app.admin.DevicePolicyManager#getAccountTypesWithManagementDisabled"})
    @MostRestrictiveCoexistenceTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_differentValuesThenBothFalse_isAccountManagementDisabledIsFalse() {
        try {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ true);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
            sDeviceState.testApp(DPC_1).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ false);

            assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager()
                    .getAccountTypesWithManagementDisabled()).asList().doesNotContain(
                    sDeviceState.accounts().accountType());
            assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager()
                    .getAccountTypesWithManagementDisabled()).asList().doesNotContain(
                    sDeviceState.accounts().accountType());
            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            sDeviceState.accounts().accountType()),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState).isNull();

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setAccountManagementDisabled",
            "android.app.admin.DevicePolicyManager#getAccountTypesWithManagementDisabled"})
    @MostRestrictiveCoexistenceTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_bothTrueThenOneFalse_isAccountManagementDisabledIsTrue() {
        try {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ true);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ true);
            sDeviceState.testApp(DPC_1).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ false);

            assertThat(sDeviceState.testApp(DPC_1).devicePolicyManager()
                    .getAccountTypesWithManagementDisabled()).asList().contains(
                    sDeviceState.accounts().accountType());
            assertThat(sDeviceState.testApp(DPC_2).devicePolicyManager()
                    .getAccountTypesWithManagementDisabled()).asList().contains(
                    sDeviceState.accounts().accountType());
            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            sDeviceState.accounts().accountType()),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
            assertThrows(Exception.class, () ->
                    sDeviceState.accounts().addAccount().add());

        } finally {
            sDeviceState.testApp(DPC_1).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
            sDeviceState.testApp(DPC_2).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @Ignore("b/312605194 Ignore until test failure is root caused")
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = AccountManagement.class)
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setAccountManagementDisabled",
            "android.app.admin.DevicePolicyManager#getAccountTypesWithManagementDisabled"})
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    public void setAccountManagementDisabled_policyMigration_works() {
        try {
            TestApis.flags().set(
                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "false");
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            sLocalDevicePolicyManager.triggerDevicePolicyEngineMigration(true);
            TestApis.flags().set(
                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "true");

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            sDeviceState.accounts().accountType()),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getAccountTypesWithManagementDisabled()).asList().contains(
                    sDeviceState.accounts().accountType());
            assertThrows(Exception.class, () ->
                    sDeviceState.accounts().addAccount().add());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
            TestApis.flags().set(
                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, null);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setAccountManagementDisabled",
            "android.app.admin.DevicePolicyManager#getAccountTypesWithManagementDisabled"})
    @MostImportantCoexistenceTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_setByDPCAndPermission_DPCRemoved_stillEnforced() {
        try {
            sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ true);
            sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            // Remove DPC
            sDeviceState.dpc().devicePolicyManager().clearDeviceOwnerApp(
                    sDeviceState.dpc().packageName());

            assertThat(sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                    .getAccountTypesWithManagementDisabled()).asList().contains(
                    sDeviceState.accounts().accountType());
            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            sDeviceState.accounts().accountType()),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
            assertThrows(Exception.class, () ->
                    sDeviceState.accounts().addAccount().add());

        } finally {
            sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setAccountManagementDisabled",
            "android.app.admin.DevicePolicyManager#getAccountTypesWithManagementDisabled"})
    @MostImportantCoexistenceTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_setByPermission_appRemoved_notEnforced() {
        try {
            sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager().setAccountManagementDisabled(
                    /* componentName= */ null, sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            // uninstall app
            sDeviceState.testApp(LESS_IMPORTANT).uninstall();

            assertThat(sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                    .getAccountTypesWithManagementDisabled()).asList().doesNotContain(
                    sDeviceState.accounts().accountType());
            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            sDeviceState.accounts().accountType()),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState).isNull();
            AccountReference account = sDeviceState.accounts().addAccount().add();
            if (account != null) {
                account.remove();
            }

        } finally {
            try {
                sDeviceState.testApp(
                        LESS_IMPORTANT).devicePolicyManager().setAccountManagementDisabled(
                        /* componentName= */ null, sDeviceState.accounts().accountType(),
                        /* disabled= */ false);
            } catch (Exception e) {
                // expected if app was uninstalled
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.AccountManager#startAddAccountSession"})
    @Test
    public void startAddAccountSession_isLogged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            AccountManagerFuture<Bundle> future =
                    sLocalAccountManager.startAddAccountSession(
                            sDeviceState.accounts().accountType(),
                            AUTH_TOKEN_TYPE,
                            REQUIRED_FEATURES,
                            /* options= */ null,
                            /* activity= */ null,
                            /* callback= */ null,
                            /* handler= */ null);
            waitForFutureDone(future);

            // asserting for an empty admin package name, since we do not have an
            // admin here.
            assertThat(metrics.query()
                    .whereAdminPackageName().isEqualTo("")
                    .whereType().isEqualTo(EventId.ADD_ACCOUNT_VALUE)
                    .whereStrings().contains(sDeviceState.accounts().accountType(),
                            ACCOUNT_TYPE, AUTH_TOKEN_TYPE,
                            REQUIRED_FEATURES_STR)
            ).wasLogged();
        }
    }

    private static void waitForFutureDone(AccountManagerFuture<Bundle> future) {
        while (!future.isDone()) {
            // we make sure the task is completed before asserting.
        }
    }
}
