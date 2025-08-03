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

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS;
import static com.android.queryable.queries.ActivityQuery.activity;
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.app.admin.ManagedSubscriptionsPolicy;
import android.app.admin.RemoteDevicePolicyManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.TelephonyManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureGlobalSettingSet;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.DefaultSmsApplication;
import com.android.bedstead.harrier.policies.DefaultSmsApplicationSystemOnly;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.remotedpc.RemotePolicyManager;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.Objects;

// TODO(b/198442101): Add tests for the COPE case when we can sideload system apps
@RunWith(BedsteadJUnit4.class)
public final class DefaultSmsApplicationTest {
    @ClassRule
    @Rule
    public static DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final TestApp sSmsApp = sDeviceState.testApps()
            .query()
            .whereActivities().contains(
                    activity().where().intentFilters().contains(
                            intentFilter().where().actions().contains(Intent.ACTION_SENDTO)))
            .get();
    private static final String FAKE_SMS_APP_NAME = "FakeSmsAppName";

    private ComponentName mAdmin;
    private RemoteDevicePolicyManager mDpm;
    private TelephonyManager mTelephonyManager;
    private RoleManager mRoleManager;

    @Before
    public void setUp() {
        RemotePolicyManager dpc = sDeviceState.dpc();
        mAdmin = dpc.componentName();
        mDpm = dpc.devicePolicyManager();
        mTelephonyManager = sContext.getSystemService(TelephonyManager.class);
        mRoleManager = sContext.getSystemService(RoleManager.class);
    }

    // TODO: Add tests for SetDefaultSmsApplicationSystemOnly

    // TODO(b/198588696): Add support is @RequireSmsCapable and @RequireNotSmsCapable
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DefaultSmsApplication.class)
    @RequireNotHeadlessSystemUserMode(reason = "b/279731298")
    @EnsureGlobalSettingSet(key =
            Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, value = "1")
    public void setDefaultSmsApplication_works() {
        //TODO(b/273529454): replace with EnsureTelephonyEnabledInUser annotation
        if (mDpm.isOrganizationOwnedDeviceWithManagedProfile()) {
            mDpm.setManagedSubscriptionsPolicy(new ManagedSubscriptionsPolicy(
                    ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS));
        }
        assumeTrue(isSmsCapable());
        String previousSmsAppName = getDefaultSmsPackage();
        try (TestAppInstance smsApp = sSmsApp.install()) {
            mDpm.setDefaultSmsApplication(mAdmin, smsApp.packageName());
            assertThat(getDefaultSmsPackage()).isEqualTo(smsApp.packageName());
        } finally {
            mDpm.setDefaultSmsApplication(mAdmin, previousSmsAppName);
            //TODO(b/273529454): replace with EnsureTelephonyEnabledInUser annotation
            if (mDpm.isOrganizationOwnedDeviceWithManagedProfile()) {
                mDpm.setManagedSubscriptionsPolicy(new ManagedSubscriptionsPolicy(
                        ManagedSubscriptionsPolicy.TYPE_ALL_PERSONAL_SUBSCRIPTIONS));
            }
        }
    }

    // TODO(b/198588696): Add support is @RequireSmsCapable and @RequireNotSmsCapable
    @Postsubmit(reason = "new test")
    @PolicyDoesNotApplyTest(policy = DefaultSmsApplication.class)
    @EnsureGlobalSettingSet(key =
            Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, value = "1")
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    public void setDefaultSmsApplication_unchanged() {
        assumeTrue(isSmsCapable());
        //TODO(b/273529454): replace with EnsureTelephonyEnabledInUser annotation
        if (mDpm.isOrganizationOwnedDeviceWithManagedProfile()) {
            mDpm.setManagedSubscriptionsPolicy(new ManagedSubscriptionsPolicy(
                    ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS));
        }
        String previousSmsAppInTest = getDefaultSmsPackage();
        String previousSmsAppInDpc = getDefaultSmsPackageInDpc();
        try (TestAppInstance smsApp = sSmsApp.install(sDeviceState.dpc().user())) {
            mDpm.setDefaultSmsApplication(mAdmin, smsApp.packageName());

            assertThat(Telephony.Sms.getDefaultSmsPackage(sContext))
                    .isEqualTo(previousSmsAppInTest);
        } finally {
            mDpm.setDefaultSmsApplication(mAdmin, previousSmsAppInDpc);
            //TODO(b/273529454): replace with EnsureTelephonyEnabledInUser annotation
            if (mDpm.isOrganizationOwnedDeviceWithManagedProfile()) {
                mDpm.setManagedSubscriptionsPolicy(new ManagedSubscriptionsPolicy(
                        ManagedSubscriptionsPolicy.TYPE_ALL_PERSONAL_SUBSCRIPTIONS));
            }
        }
    }

    // TODO(b/198588696): Add support is @RequireSmsCapable and @RequireNotSmsCapable
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DefaultSmsApplication.class)
    @EnsureGlobalSettingSet(key =
            Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, value = "1")
    public void setDefaultSmsApplication_smsPackageDoesNotExist_unchanged() {
        assumeTrue(isSmsCapable());
        //TODO(b/273529454): replace with EnsureTelephonyEnabledInUser annotation
        if (mDpm.isOrganizationOwnedDeviceWithManagedProfile()) {
            mDpm.setManagedSubscriptionsPolicy(new ManagedSubscriptionsPolicy(
                    ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS));
        }
        String previousSmsAppName = getDefaultSmsPackage();

        mDpm.setDefaultSmsApplication(mAdmin, FAKE_SMS_APP_NAME);

        try {
            assertThat(getDefaultSmsPackage()).isEqualTo(previousSmsAppName);
        } finally {
            mDpm.setDefaultSmsApplication(mAdmin, previousSmsAppName);
            //TODO(b/273529454): replace with EnsureTelephonyEnabledInUser annotation
            if (mDpm.isOrganizationOwnedDeviceWithManagedProfile()) {
                mDpm.setManagedSubscriptionsPolicy(new ManagedSubscriptionsPolicy(
                        ManagedSubscriptionsPolicy.TYPE_ALL_PERSONAL_SUBSCRIPTIONS));
            }
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = {DefaultSmsApplication.class, DefaultSmsApplicationSystemOnly.class})
    public void setDefaultSmsApplication_nullAdmin_throwsException() {
        try (TestAppInstance smsApp = sSmsApp.install()) {

            assertThrows(NullPointerException.class, () ->
                    mDpm.setDefaultSmsApplication(
                            /* admin= */ null, smsApp.packageName()));
        }
    }

    // TODO(b/198588696): Add support is @RequireSmsCapable and @RequireNotSmsCapable
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DefaultSmsApplication.class)
    @EnsureGlobalSettingSet(key =
            Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, value = "1")
    public void setDefaultSmsApplication_notSmsCapable_unchanged() {
        assumeFalse(isSmsCapable());
        //TODO(b/273529454): replace with EnsureTelephonyEnabledInUser annotation
        if (mDpm.isOrganizationOwnedDeviceWithManagedProfile()) {
            mDpm.setManagedSubscriptionsPolicy(new ManagedSubscriptionsPolicy(
                    ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS));
        }
        String previousSmsAppName = getDefaultSmsPackage();
        try (TestAppInstance smsApp = sSmsApp.install()) {

            mDpm.setDefaultSmsApplication(mAdmin, smsApp.packageName());

            // ROLE_SMS behaviour(SmsRoleBehaviour.java) changes based on the
            // ManagedSubscriptionsPolicy on the work profile, so asserting isSmsCapable here again
            // to check that if device is actually smsCapable or else assert that default sms app
            // does not changes.
            assertThat(isSmsCapable() || Objects.equals(getDefaultSmsPackage(),
                    previousSmsAppName)).isTrue();
        } finally {
            mDpm.setDefaultSmsApplication(mAdmin, previousSmsAppName);
            //TODO(b/273529454): replace with EnsureTelephonyEnabledInUser annotation
            if (mDpm.isOrganizationOwnedDeviceWithManagedProfile()) {
                mDpm.setManagedSubscriptionsPolicy(new ManagedSubscriptionsPolicy(
                        ManagedSubscriptionsPolicy.TYPE_ALL_PERSONAL_SUBSCRIPTIONS));
            }
        }
    }

    @Ignore("b/300397938")
    @Postsubmit(reason = "new test")
    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = {
            DefaultSmsApplication.class, DefaultSmsApplicationSystemOnly.class},
            includeNonDeviceAdminStates = false)
    public void setDefaultSmsApplication_invalidAdmin_throwsException() {
        try (TestAppInstance smsApp = sSmsApp.install()) {

            assertThrows(SecurityException.class, () ->
                    mDpm.setDefaultSmsApplication(mAdmin, smsApp.packageName()));
        }
    }

    private String getDefaultSmsPackage() {
        return Telephony.Sms.getDefaultSmsPackage(sContext);
    }

    private String getDefaultSmsPackageInDpc() {
        // TODO(268461966): Make the call via the dpc
        try {
            return Telephony.Sms.getDefaultSmsPackage(
                    sContext.createPackageContextAsUser(
                            sDeviceState.dpc().packageName(),
                            /* flags= */ 0,
                            sDeviceState.dpc().user().userHandle()));
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isSmsCapable() {
        return mTelephonyManager.isSmsCapable()
                || (mRoleManager != null && mRoleManager.isRoleAvailable(RoleManager.ROLE_SMS));
    }
}
