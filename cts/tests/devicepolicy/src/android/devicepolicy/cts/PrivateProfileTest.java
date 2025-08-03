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

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_ADD_PRIVATE_PROFILE;
import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.UserManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasNoPrivateProfile;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireMultiUserSupport;
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDeviceOwner;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RequireNotHeadlessSystemUserMode(reason = "Private profiles are disabled on HSUM.")
@RunWith(BedsteadJUnit4.class)
// TODO(b/297332594): Migrate this test class to private space test module once added.
public final class PrivateProfileTest {
    @ClassRule
    @Rule
    public static DeviceState sDeviceState = new DeviceState();

    /**
     * Test creation of private profile should not be allowed when device owner is set.
     */
    @Test
    @EnsureHasDeviceOwner
    @RequireRunOnInitialUser
    @RequireMultiUserSupport
    @EnsureHasNoPrivateProfile
    public void hasDeviceOwner_disallowAddPrivateProfileIsSet() {
        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_ADD_PRIVATE_PROFILE)).isTrue();
    }

    /**
     * Test creation of private profile should be allowed on devices with a work profile.
     */
    @Test
    @EnsureHasWorkProfile
    @EnsureHasNoDeviceOwner
    @EnsureHasNoPrivateProfile
    @RequireRunOnInitialUser
    @RequireMultiUserSupport
    //TODO(b/297160795): Exclude scenarios where private space will not be available.
    public void hasWorkProfile_disallowAddPrivateProfileIsNotSet() {
        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_ADD_PRIVATE_PROFILE)).isFalse();
    }

    /**
     * Test creation of private profile should be allowed on organization-owned devices.
     */
    @Test
    @EnsureHasWorkProfile(isOrganizationOwned = true)
    @EnsureHasNoDeviceOwner
    @EnsureHasNoPrivateProfile
    @RequireRunOnInitialUser
    @RequireMultiUserSupport
    //TODO(b/297160795): Exclude scenarios where private space will not be available.
    public void hasOrganizationOwnedWorkProfile_disallowAddPrivateProfileIsNotSet() {
        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_ADD_PRIVATE_PROFILE)).isFalse();
    }

    /**
     * Test creation of private profile should be allowed when when the restriction is not set.
     */
    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoPrivateProfile
    @RequireRunOnInitialUser
    @RequireMultiUserSupport
    @EnsureDoesNotHaveUserRestriction(DISALLOW_ADD_PRIVATE_PROFILE)
    //TODO(b/297160795): Exclude scenarios where private space will not be available.
    public void addPrivateProfile_disallowAddPrivateProfileIsNotSet_addsPrivateProfile() {
        try (UserReference privateProfile = createPrivateProfile()) {
            assertThat(privateProfile.exists()).isTrue();
        }
    }

    /**
     * Test creation of private profile should not be allowed when when the restriction is set.
     */
    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoPrivateProfile
    @RequireRunOnInitialUser
    @RequireMultiUserSupport
    @EnsureHasUserRestriction(DISALLOW_ADD_PRIVATE_PROFILE)
    //TODO(b/297160795): Exclude scenarios where private space will not be available.
    public void addPrivateProfile_disallowAddPrivateProfileIsSet_throwsException() {
        assertThrows(NeneException.class, () -> createPrivateProfile());
    }

    private UserReference createPrivateProfile() {
        return TestApis.users().createUser()
                .parent(TestApis.users().instrumented())
                .type(TestApis.users().supportedType(UserManager.USER_TYPE_PROFILE_PRIVATE))
                .create();
    }
}
