package com.android.intentresolver.v2.platform

import android.content.pm.UserInfo
import android.content.pm.UserInfo.NO_PROFILE_GROUP_ID
import android.os.UserHandle
import android.os.UserManager
import com.android.intentresolver.v2.platform.FakeUserManager.ProfileType
import com.google.common.truth.Correspondence
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeUserManagerTest {
    private val userManager = FakeUserManager()
    private val state = userManager.state

    @Test
    fun initialState() {
        val personal = userManager.getEnabledProfiles(state.primaryUserHandle.identifier).single()

        assertThat(personal.id).isEqualTo(state.primaryUserHandle.identifier)
        assertThat(personal.userType).isEqualTo(UserManager.USER_TYPE_FULL_SYSTEM)
        assertThat(personal.flags and UserInfo.FLAG_FULL).isEqualTo(UserInfo.FLAG_FULL)
    }

    @Test
    fun getProfileParent() {
        val workHandle = state.createProfile(ProfileType.WORK)

        assertThat(userManager.getProfileParent(state.primaryUserHandle)).isNull()
        assertThat(userManager.getProfileParent(workHandle)).isEqualTo(state.primaryUserHandle)
        assertThat(userManager.getProfileParent(UserHandle.of(-1))).isNull()
    }

    @Test
    fun getUserInfo() {
        val personalUser =
            requireNotNull(userManager.getUserInfo(state.primaryUserHandle.identifier)) {
                "Expected getUserInfo to return non-null"
            }
        assertTrue(userInfoAreEqual.apply(personalUser, state.getPrimaryUser()))

        val workHandle = state.createProfile(ProfileType.WORK)

        val workUser =
            requireNotNull(userManager.getUserInfo(workHandle.identifier)) {
                "Expected getUserInfo to return non-null"
            }
        assertTrue(
            userInfoAreEqual.apply(workUser, userManager.getUserInfo(workHandle.identifier)!!)
        )
    }

    @Test
    fun getEnabledProfiles_usingParentId() {
        val personal = state.primaryUserHandle
        val work = state.createProfile(ProfileType.WORK)
        val private = state.createProfile(ProfileType.PRIVATE)

        val enabledProfiles = userManager.getEnabledProfiles(personal.identifier)

        assertWithMessage("enabledProfiles: List<UserInfo>")
            .that(enabledProfiles)
            .comparingElementsUsing(userInfoEquality)
            .displayingDiffsPairedBy { it.id }
            .containsExactly(state.getPrimaryUser(), state.getUser(work), state.getUser(private))
    }

    @Test
    fun getEnabledProfiles_usingProfileId() {
        val clone = state.createProfile(ProfileType.CLONE)

        val enabledProfiles = userManager.getEnabledProfiles(clone.identifier)

        assertWithMessage("getEnabledProfiles(clone.identifier)")
            .that(enabledProfiles)
            .comparingElementsUsing(userInfoEquality)
            .displayingDiffsPairedBy { it.id }
            .containsExactly(state.getPrimaryUser(), state.getUser(clone))
    }

    @Test
    fun getUserOrNull() {
        val personal = state.getPrimaryUser()

        assertThat(state.getUserOrNull(personal.userHandle)).isEqualTo(personal)
        assertThat(state.getUserOrNull(UserHandle.of(personal.id - 1))).isNull()
    }

    @Test
    fun createProfile() {
        // Order dependent: profile creation modifies the primary user
        val workHandle = state.createProfile(ProfileType.WORK)

        val primaryUser = state.getPrimaryUser()
        val workUser = state.getUser(workHandle)

        assertThat(primaryUser.profileGroupId).isNotEqualTo(NO_PROFILE_GROUP_ID)
        assertThat(workUser.profileGroupId).isEqualTo(primaryUser.profileGroupId)
    }

    @Test
    fun removeProfile() {
        val personal = state.getPrimaryUser()
        val work = state.createProfile(ProfileType.WORK)
        val private = state.createProfile(ProfileType.PRIVATE)

        state.removeProfile(private)
        assertThat(state.userHandles).containsExactly(personal.userHandle, work)
    }

    @Test(expected = IllegalArgumentException::class)
    fun removeProfile_primaryNotAllowed() {
        state.removeProfile(state.primaryUserHandle)
    }
}

private val userInfoAreEqual =
    Correspondence.BinaryPredicate<UserInfo, UserInfo> { actual, expected ->
        actual.id == expected.id &&
            actual.profileGroupId == expected.profileGroupId &&
            actual.userType == expected.userType &&
            actual.flags == expected.flags
    }

val userInfoEquality: Correspondence<UserInfo, UserInfo> =
    Correspondence.from(userInfoAreEqual, "==")
