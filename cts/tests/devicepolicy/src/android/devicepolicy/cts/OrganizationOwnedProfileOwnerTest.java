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

import static com.android.bedstead.nene.permissions.CommonPermissions.CREATE_USERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS;

import static com.google.common.truth.Truth.assertThat;

import android.os.UserManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnAdditionalUser;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class OrganizationOwnedProfileOwnerTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @EnsureHasWorkProfile(isOrganizationOwned = true)
    @EnsureHasPermission({CREATE_USERS, INTERACT_ACROSS_USERS})
    @Test
    public void organizationOwnedProfileOwner_userCannotBeRemoved() {
        assertThat(TestApis.context().instrumentedContext().getSystemService(UserManager.class)
                    .removeUser(sDeviceState.workProfile().userHandle())).isFalse();
        assertThat(sDeviceState.workProfile().exists()).isTrue();
    }

    // We run on a different user so we don't block deleting the parent
    @RequireRunOnAdditionalUser
    @EnsureHasWorkProfile(isOrganizationOwned = true)
    @EnsureHasPermission({CREATE_USERS, INTERACT_ACROSS_USERS})
    @Test
    public void organizationOwnedProfileOwner_parentCannotBeRemoved() {
        assertThat(TestApis.context().instrumentedContext().getSystemService(UserManager.class)
                .removeUser(sDeviceState.workProfile().parent().userHandle())).isFalse();
        assertThat(sDeviceState.workProfile().parent().exists()).isTrue();
    }
}
