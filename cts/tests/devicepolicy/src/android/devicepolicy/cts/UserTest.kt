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
package android.devicepolicy.cts

import android.os.UserManager
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.EnsureCanAddUser
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser
import com.android.bedstead.harrier.annotations.EnsureHasPermission
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.RequireRunOnAdditionalUser
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser
import com.android.bedstead.harrier.annotations.RequireRunOnSystemUser
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest
import com.android.bedstead.harrier.policies.DisallowRemoveUser
import com.android.bedstead.harrier.policies.DisallowUserSwitch
import com.android.bedstead.harrier.policies.ReceiveUserCallbacks
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.permissions.CommonPermissions
import com.android.bedstead.nene.types.OptionalBoolean.ANY
import com.android.bedstead.nene.types.OptionalBoolean.FALSE
import com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_REMOVE_USER
import com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_USER_SWITCH
import com.android.compatibility.common.util.ApiTest
import com.android.eventlib.truth.EventLogsSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert.assertThrows

@RunWith(BedsteadJUnit4::class)
class UserTest {
    @CannotSetPolicyTest(policy = [DisallowRemoveUser::class], includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_REMOVE_USER"])
    fun setUserRestriction_disallowRemoveUser_cannotSet_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().addUserRestriction(
                deviceState.dpc().componentName(), DISALLOW_REMOVE_USER
            )
        }
    }

    @PolicyAppliesTest(policy = [DisallowRemoveUser::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_REMOVE_USER"])
    fun setUserRestriction_disallowRemoveUser_isSet() {
        try {
            deviceState.dpc().devicePolicyManager().addUserRestriction(
                deviceState.dpc().componentName(), DISALLOW_REMOVE_USER
            )

            assertThat(
                TestApis.devicePolicy().userRestrictions()
                    .isSet(DISALLOW_REMOVE_USER)
            ).isTrue()
        } finally {
            deviceState.dpc().devicePolicyManager().clearUserRestriction(
                deviceState.dpc().componentName(), DISALLOW_REMOVE_USER
            )
        }
    }

    @PolicyDoesNotApplyTest(policy = [DisallowRemoveUser::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_REMOVE_USER"])
    fun setUserRestriction_disallowRemoveUser_isNotSet() {
        try {
            deviceState.dpc().devicePolicyManager().addUserRestriction(
                deviceState.dpc().componentName(), DISALLOW_REMOVE_USER
            )

            assertThat(
                TestApis.devicePolicy().userRestrictions()
                    .isSet(DISALLOW_REMOVE_USER)
            ).isFalse()
        } finally {
            deviceState.dpc().devicePolicyManager().clearUserRestriction(
                deviceState.dpc().componentName(), DISALLOW_REMOVE_USER
            )
        }
    }

    @EnsureHasAdditionalUser(switchedToUser = FALSE)
    @EnsureDoesNotHaveUserRestriction(
        value = DISALLOW_REMOVE_USER,
        onUser = UserType.ADMIN_USER
    )
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_REMOVE_USER"])
    @EnsureHasPermission(CommonPermissions.CREATE_USERS)
    @RequireRunOnSystemUser(switchedToUser = ANY)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_REMOVE_USER, onUser = UserType.ANY)
    fun removeUser_disallowRemoveUserIsNotSet_isRemoved() {
        val additionalUser = deviceState.additionalUser()

        val result = localUserManager.removeUser(additionalUser.userHandle())

        assertThat(result).isTrue()
        assertThat(additionalUser.exists()).isFalse()
    }

    @EnsureHasAdditionalUser
    @EnsureHasUserRestriction(
        value = DISALLOW_REMOVE_USER,
        onUser = UserType.ADMIN_USER
    )
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_REMOVE_USER"])
    @EnsureHasPermission(CommonPermissions.CREATE_USERS)
    @RequireRunOnSystemUser(switchedToUser = ANY)
    fun removeUser_disallowRemoveUserIsSetOnAdminUser_returnsFalse() {
        val additionalUser = deviceState.additionalUser()

        val result = localUserManager.removeUser(additionalUser.userHandle())

        assertThat(result).isFalse()
        assertThat(additionalUser.exists()).isTrue()
    }

    @EnsureHasUserRestriction(value = DISALLOW_REMOVE_USER)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_REMOVE_USER"])
    @EnsureHasPermission(CommonPermissions.CREATE_USERS)
    @RequireRunOnAdditionalUser
    fun removeUser_ownUser_disallowRemoveUserIsSet_returnsFalse() {
        val result = localUserManager.removeUser(TestApis.users().instrumented().userHandle())

        assertThat(result).isFalse()
    }

    @CannotSetPolicyTest(policy = [DisallowUserSwitch::class], includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_USER_SWITCH"])
    fun setUserRestriction_disallowUserSwitch_cannotSet_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().addUserRestriction(
                deviceState.dpc().componentName(), DISALLOW_USER_SWITCH
            )
        }
    }

    @PolicyAppliesTest(policy = [DisallowUserSwitch::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_USER_SWITCH"])
    fun setUserRestriction_disallowUserSwitch_isSet() {
        try {
            deviceState.dpc().devicePolicyManager().addUserRestriction(
                deviceState.dpc().componentName(), DISALLOW_USER_SWITCH
            )

            assertThat(
                TestApis.devicePolicy().userRestrictions()
                    .isSet(DISALLOW_USER_SWITCH)
            ).isTrue()
        } finally {
            deviceState.dpc().devicePolicyManager().clearUserRestriction(
                deviceState.dpc().componentName(), DISALLOW_USER_SWITCH
            )
        }
    }

    @PolicyDoesNotApplyTest(policy = [DisallowUserSwitch::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_USER_SWITCH"])
    fun setUserRestriction_disallowUserSwitch_isNotSet() {
        try {
            deviceState.dpc().devicePolicyManager().addUserRestriction(
                deviceState.dpc().componentName(), DISALLOW_USER_SWITCH
            )

            assertThat(
                TestApis.devicePolicy().userRestrictions()
                    .isSet(DISALLOW_USER_SWITCH)
            ).isFalse()
        } finally {
            deviceState.dpc().devicePolicyManager().clearUserRestriction(
                deviceState.dpc().componentName(), DISALLOW_USER_SWITCH
            )
        }
    }

    @RequireRunOnInitialUser
    @EnsureHasPermission(CommonPermissions.INTERACT_ACROSS_USERS)
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_USER_SWITCH"])
    @Postsubmit(reason = "new test")
    @Test
    @EnsureDoesNotHaveUserRestriction(
        value = DISALLOW_USER_SWITCH,
        onUser = UserType.ADMIN_USER
    )
    fun getUserSwitchability_disallowUserSwitchIsNotSet_isNotDisallowed() {
        assertThat(localUserManager.userSwitchability)
            .isNotEqualTo(UserManager.SWITCHABILITY_STATUS_USER_SWITCH_DISALLOWED)
    }

    @RequireRunOnInitialUser
    @EnsureHasPermission(CommonPermissions.INTERACT_ACROSS_USERS)
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_USER_SWITCH"])
    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasUserRestriction(
        value = DISALLOW_USER_SWITCH,
        onUser = UserType.ADMIN_USER
    )
    fun getUserSwitchability_disallowUserSwitchIsSet_isDisallowed() {
        assertThat(localUserManager.userSwitchability)
            .isEqualTo(UserManager.SWITCHABILITY_STATUS_USER_SWITCH_DISALLOWED)
    }

    // TODO: Figure out how to add tests that the broadcasts ARE NOT received by other types of DPC

    @EnsureCanAddUser
    @CanSetPolicyTest(policy = [ReceiveUserCallbacks::class])
    @ApiTest(apis = ["android.app.admin.DeviceAdminReceiver#onUserAdded"])
    fun addUser_userAddedCallbackIsReceived() {
        TestApis.users().createUser().create().use { user ->

            assertThat(
                deviceState.dpc().events().userAdded()
                    .whereAddedUser().id().isEqualTo(user.id())
            )
                .eventOccurred()
        }
    }

    @CanSetPolicyTest(policy = [ReceiveUserCallbacks::class])
    @EnsureHasAdditionalUser(switchedToUser = FALSE)
    @ApiTest(apis = ["android.app.admin.DeviceAdminReceiver#onUserRemoved"])
    fun removeUser_userRemovedCallbackIsReceived() {
        deviceState.additionalUser().remove()

        assertThat(
            deviceState.dpc().events().userRemoved()
                .whereRemovedUser().id().isEqualTo(deviceState.additionalUser().id())
        )
            .eventOccurred()
    }

    @EnsureCanAddUser
    @CanSetPolicyTest(policy = [ReceiveUserCallbacks::class])
    @EnsureHasAdditionalUser(switchedToUser = FALSE)
    @ApiTest(apis = ["android.app.admin.DeviceAdminReceiver#onUserStarted"])
    fun startUser_userStartedCallbackIsReceived() {
        deviceState.additionalUser().stop()
        deviceState.additionalUser().start()

        assertThat(
            deviceState.dpc().events().userStarted()
                .whereStartedUser().id().isEqualTo(deviceState.additionalUser().id())
        )
            .eventOccurred()
    }

    @EnsureCanAddUser
    @CanSetPolicyTest(policy = [ReceiveUserCallbacks::class])
    @EnsureHasAdditionalUser(switchedToUser = FALSE)
    @ApiTest(apis = ["android.app.admin.DeviceAdminReceiver#onUserStopped"])
    fun stopUser_userStoppedCallbackIsReceived() {
        deviceState.additionalUser().start()
        deviceState.additionalUser().stop()

        assertThat(
            deviceState.dpc().events().userStopped()
                .whereStoppedUser().id().isEqualTo(deviceState.additionalUser().id())
        )
            .eventOccurred()

    }

    @EnsureCanAddUser
    @CanSetPolicyTest(policy = [ReceiveUserCallbacks::class])
    @EnsureHasAdditionalUser(switchedToUser = FALSE)
    @ApiTest(apis = ["android.app.admin.DeviceAdminReceiver#onUserSwitched"])
    fun switchUser_userSwitchedCallbackIsReceived() {
        deviceState.additionalUser().switchTo()

        assertThat(
            deviceState.dpc().events().userSwitched()
                .whereSwitchedUser().id().isEqualTo(deviceState.additionalUser().id())
        ).eventOccurred()
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
        private val localUserManager = TestApis.context().instrumentedContext().getSystemService(
            UserManager::class.java
        )!!
    }
}
