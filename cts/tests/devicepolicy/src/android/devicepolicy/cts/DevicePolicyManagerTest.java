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

import static android.app.admin.flags.Flags.FLAG_DEVICE_POLICY_SIZE_TRACKING_ENABLED;

import static com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.content.Context;

import com.android.bedstead.flags.annotations.RequireFlagsEnabled;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/**
 * Tests should only be added to this class if there is nowhere else they could reasonably
 * go.
 */
@RunWith(BedsteadJUnit4.class)
public final class DevicePolicyManagerTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);

    private static final String MANAGE_PROFILE_AND_DEVICE_OWNERS =
            "android.permission.MANAGE_PROFILE_AND_DEVICE_OWNERS";
    private static final String MANAGE_DEVICE_ADMINS = "android.permission.MANAGE_DEVICE_ADMINS";

    @EnsureHasDeviceOwner
    @EnsureDoesNotHavePermission(MANAGE_DEVICE_ADMINS)
    @Test
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#removeActiveAdmin")
    public void removeActiveAdmin_adminPassedDoesNotBelongToCaller_throwsException() {
        assertThrows(SecurityException.class, () -> sDevicePolicyManager.removeActiveAdmin(
                sDeviceState.deviceOwner().componentName()));
    }

    @EnsureHasDeviceOwner
    @EnsureHasPermission(MANAGE_DEVICE_ADMINS)
    @Test
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#removeActiveAdmin")
    public void removeActiveAdmin_adminPassedDoesNotBelongToCaller_manageDeviceAdminsPermission_noException() {
        sDevicePolicyManager.removeActiveAdmin(
                sDeviceState.deviceOwner().componentName());
    }

    @Test
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getDevicePolicyManagementRoleHolderPackage")
    public void getDeviceManagerRoleHolderPackageName_doesNotCrash() {
        sDevicePolicyManager.getDevicePolicyManagementRoleHolderPackage();
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasNoDpc
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPolicyManagedProfiles")
    public void getPolicyManagedProfiles_noManagedProfiles_returnsEmptyList() {
        assertThat(sDevicePolicyManager.getPolicyManagedProfiles(
                TestApis.context().instrumentationContext().getUser())).isEmpty();
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasWorkProfile
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPolicyManagedProfiles")
    public void getPolicyManagedProfiles_hasWorkProfile_returnsWorkProfileUser() {
        assertThat(sDevicePolicyManager.getPolicyManagedProfiles(
                TestApis.context().instrumentationContext().getUser()))
                .containsExactly(sDeviceState.workProfile().userHandle());
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasNoDpc
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPolicyManagedProfiles")
    public void getPolicyManagedProfiles_hasManagedProfileNoProfileOwner_returnsEmptyList() {
        try (UserReference user = TestApis.users().createUser().type(MANAGED_PROFILE_TYPE_NAME)
                .parent(TestApis.users().instrumented()).create()) {
            assertThat(sDevicePolicyManager.getPolicyManagedProfiles(
                    TestApis.context().instrumentationContext().getUser()))
                    .isEmpty();
        }
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasNoDpc
    @EnsureDoesNotHavePermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPolicyManagedProfiles")
    public void getPolicyManagedProfiles_noPermission_returnsEmptyList() {
        assertThrows(SecurityException.class, () -> sDevicePolicyManager.getPolicyManagedProfiles(
                TestApis.context().instrumentationContext().getUser()));
    }

    @RequireFlagsEnabled(FLAG_DEVICE_POLICY_SIZE_TRACKING_ENABLED)
    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMaxPolicyStorageLimit")
    @Ignore
    public void setMaxPolicyStorageLimit_setsLimit() {
        int currentLimit = sDevicePolicyManager.getMaxPolicyStorageLimit();
        try {

            sDevicePolicyManager.setMaxPolicyStorageLimit(-1);

            assertThat(sDevicePolicyManager.getMaxPolicyStorageLimit()).isEqualTo(-1);

        } finally {
            sDevicePolicyManager.setMaxPolicyStorageLimit(currentLimit);
        }
    }

    @RequireFlagsEnabled(FLAG_DEVICE_POLICY_SIZE_TRACKING_ENABLED)
    @Postsubmit(reason = "new test")
    @Test
    @EnsureDoesNotHavePermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMaxPolicyStorageLimit")
    @Ignore
    public void setMaxPolicyStorageLimit_noPermission_throwsException() {
        assertThrows(
                SecurityException.class, () -> sDevicePolicyManager.setMaxPolicyStorageLimit(-1));
    }

    @RequireFlagsEnabled(FLAG_DEVICE_POLICY_SIZE_TRACKING_ENABLED)
    @Postsubmit(reason = "new test")
    @Test
    @EnsureDoesNotHavePermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getMaxPolicyStorageLimit")
    @Ignore
    public void getMaxPolicyStorageLimit_noPermission_throwsException() {
        assertThrows(
                SecurityException.class, () -> sDevicePolicyManager.getMaxPolicyStorageLimit());
    }
}
