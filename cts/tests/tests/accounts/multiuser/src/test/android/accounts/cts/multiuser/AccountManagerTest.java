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

package android.accounts.cts.multiuser;

import static com.android.bedstead.harrier.UserType.ADDITIONAL_USER;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_PROFILES;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.accounts.AccountManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasAccount;
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class AccountManagerTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    @EnsureHasAdditionalUser
    @EnsureDoesNotHavePermission(
            {INTERACT_ACROSS_USERS, INTERACT_ACROSS_PROFILES, INTERACT_ACROSS_USERS_FULL})
    @EnsureHasAccount(onUser = ADDITIONAL_USER, features = "feature")
    public void hasFeature_crossUser_withoutPermission_throwsException() {
        AccountManager otherUserAccountManager =
                TestApis.context().androidContextAsUser(
                        sDeviceState.additionalUser()).getSystemService(
                                AccountManager.class);

        assertThrows(SecurityException.class, () ->
                otherUserAccountManager.hasFeatures(
                        sDeviceState.account().account(), new String[]{"feature"},
                        /* callback= */ null, /* handler= */ null));
    }

    @Test
    @EnsureHasAdditionalUser
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    @EnsureHasAccount(onUser = ADDITIONAL_USER, features = "feature")
    public void hasFeature_crossUser_accountHasFeature_returnsTrue() throws Exception {
        AccountManager otherUserAccountManager =
                TestApis.context().androidContextAsUser(
                        sDeviceState.additionalUser()).getSystemService(
                                AccountManager.class);

        assertThat(otherUserAccountManager.hasFeatures(
                    sDeviceState.account().account(), new String[]{"feature"},
                    /* callback= */ null, /* handler= */ null).getResult()).isTrue();
    }

    @Test
    @EnsureHasAdditionalUser
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    @EnsureHasAccount(onUser = ADDITIONAL_USER, features = {})
    public void hasFeature_crossUser_accountDoesNotHaveFeature_returnsFalse() throws Exception {
        AccountManager otherUserAccountManager =
                TestApis.context().androidContextAsUser(
                        sDeviceState.additionalUser()).getSystemService(
                                AccountManager.class);

        assertThat(otherUserAccountManager.hasFeatures(
                    sDeviceState.account().account(), new String[]{"feature"},
                    /* callback= */ null, /* handler= */ null).getResult()).isFalse();
    }
}
