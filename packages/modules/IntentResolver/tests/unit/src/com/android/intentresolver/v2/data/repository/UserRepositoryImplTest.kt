package com.android.intentresolver.v2.data.repository

import android.content.Intent
import android.content.pm.UserInfo
import android.os.UserHandle
import android.os.UserHandle.SYSTEM
import android.os.UserHandle.USER_SYSTEM
import android.os.UserManager
import com.android.intentresolver.mock
import com.android.intentresolver.v2.coroutines.collectLastValue
import com.android.intentresolver.v2.platform.FakeUserManager
import com.android.intentresolver.v2.platform.FakeUserManager.ProfileType
import com.android.intentresolver.v2.shared.model.User
import com.android.intentresolver.v2.shared.model.User.Role
import com.android.intentresolver.whenever
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.doReturn

internal class UserRepositoryImplTest {
    private val userManager = FakeUserManager()
    private val userState = userManager.state

    @Test
    fun initialization() = runTest {
        val repo = createUserRepository(userManager)
        val users by collectLastValue(repo.users)

        assertWithMessage("collectLastValue(repo.users)").that(users).isNotNull()
        assertThat(users)
            .containsExactly(User(userState.primaryUserHandle.identifier, Role.PERSONAL))
    }

    @Test
    fun createProfile() = runTest {
        val repo = createUserRepository(userManager)
        val users by collectLastValue(repo.users)

        assertWithMessage("collectLastValue(repo.users)").that(users).isNotNull()
        assertThat(users!!.filter { it.role.type == User.Type.PROFILE }).isEmpty()

        val profile = userState.createProfile(ProfileType.WORK)
        assertThat(users).contains(User(profile.identifier, Role.WORK))
    }

    @Test
    fun removeProfile() = runTest {
        val repo = createUserRepository(userManager)
        val users by collectLastValue(repo.users)

        assertWithMessage("collectLastValue(repo.users)").that(users).isNotNull()
        val work = userState.createProfile(ProfileType.WORK)
        assertThat(users).contains(User(work.identifier, Role.WORK))

        userState.removeProfile(work)
        assertThat(users).doesNotContain(User(work.identifier, Role.WORK))
    }

    @Test
    fun isAvailable() = runTest {
        val repo = createUserRepository(userManager)
        val work = userState.createProfile(ProfileType.WORK)
        val workUser = User(work.identifier, Role.WORK)

        val available by collectLastValue(repo.availability)
        assertThat(available?.get(workUser)).isTrue()

        userState.setQuietMode(work, true)
        assertThat(available?.get(workUser)).isFalse()

        userState.setQuietMode(work, false)
        assertThat(available?.get(workUser)).isTrue()
    }

    @Test
    fun onHandleAvailabilityChange_userStateMaintained() = runTest {
        val repo = createUserRepository(userManager)
        val private = userState.createProfile(ProfileType.PRIVATE)
        val privateUser = User(private.identifier, Role.PRIVATE)

        val users by collectLastValue(repo.users)

        repo.requestState(privateUser, false)
        repo.requestState(privateUser, true)

        assertWithMessage("users.size")
                .that(users?.size ?: 0).isEqualTo(2) // personal + private

        assertWithMessage("No duplicate IDs")
                .that(users?.count { it.id == private.identifier }).isEqualTo(1)
    }

    @Test
    fun requestState() = runTest {
        val repo = createUserRepository(userManager)
        val work = userState.createProfile(ProfileType.WORK)
        val workUser = User(work.identifier, Role.WORK)

        val available by collectLastValue(repo.availability)
        assertThat(available?.get(workUser)).isTrue()

        repo.requestState(workUser, false)
        assertThat(available?.get(workUser)).isFalse()

        repo.requestState(workUser, true)
        assertThat(available?.get(workUser)).isTrue()
    }

    @Test(expected = IllegalArgumentException::class)
    fun requestState_invalidForFullUser() = runTest {
        val repo = createUserRepository(userManager)
        val primaryUser = User(userState.primaryUserHandle.identifier, Role.PERSONAL)
        repo.requestState(primaryUser, available = false)
    }

    /**
     * This and all the 'recovers_from_*' tests below all configure a static event flow instead of
     * using [FakeUserManager]. These tests verify that a invalid broadcast causes the flow to
     * reinitialize with the user profile group.
     */
    @Test
    fun recovers_from_invalid_profile_added_event() = runTest {
        val userManager =
            mockUserManager(validUser = USER_SYSTEM, invalidUser = UserHandle.USER_NULL)
        val events =
            flowOf(
                UserRepositoryImpl.UserEvent(
                    Intent.ACTION_PROFILE_ADDED,
                    UserHandle.of(UserHandle.USER_NULL)
                )
            )
        val repo =
            UserRepositoryImpl(
                profileParent = SYSTEM,
                userManager = userManager,
                userEvents = events,
                scope = backgroundScope,
                backgroundDispatcher = Dispatchers.Unconfined
            )
        val users by collectLastValue(repo.users)

        assertWithMessage("collectLastValue(repo.users)").that(users).isNotNull()
        assertThat(users).containsExactly(User(USER_SYSTEM, Role.PERSONAL))
    }

    @Test
    fun recovers_from_invalid_profile_removed_event() = runTest {
        val userManager =
            mockUserManager(validUser = USER_SYSTEM, invalidUser = UserHandle.USER_NULL)
        val events =
            flowOf(
                UserRepositoryImpl.UserEvent(
                    Intent.ACTION_PROFILE_REMOVED,
                    UserHandle.of(UserHandle.USER_NULL)
                )
            )
        val repo =
            UserRepositoryImpl(
                profileParent = SYSTEM,
                userManager = userManager,
                userEvents = events,
                scope = backgroundScope,
                backgroundDispatcher = Dispatchers.Unconfined
            )
        val users by collectLastValue(repo.users)

        assertWithMessage("collectLastValue(repo.users)").that(users).isNotNull()
        assertThat(users).containsExactly(User(USER_SYSTEM, Role.PERSONAL))
    }

    @Test
    fun recovers_from_invalid_profile_available_event() = runTest {
        val userManager =
            mockUserManager(validUser = USER_SYSTEM, invalidUser = UserHandle.USER_NULL)
        val events =
            flowOf(
                UserRepositoryImpl.UserEvent(
                    Intent.ACTION_PROFILE_AVAILABLE,
                    UserHandle.of(UserHandle.USER_NULL)
                )
            )
        val repo =
            UserRepositoryImpl(SYSTEM, userManager, events, backgroundScope, Dispatchers.Unconfined)
        val users by collectLastValue(repo.users)

        assertWithMessage("collectLastValue(repo.users)").that(users).isNotNull()
        assertThat(users).containsExactly(User(USER_SYSTEM, Role.PERSONAL))
    }

    @Test
    fun recovers_from_unknown_event() = runTest {
        val userManager =
            mockUserManager(validUser = USER_SYSTEM, invalidUser = UserHandle.USER_NULL)
        val events =
            flowOf(
                UserRepositoryImpl.UserEvent("UNKNOWN_EVENT", UserHandle.of(UserHandle.USER_NULL))
            )
        val repo =
            UserRepositoryImpl(
                profileParent = SYSTEM,
                userManager = userManager,
                userEvents = events,
                scope = backgroundScope,
                backgroundDispatcher = Dispatchers.Unconfined
            )
        val users by collectLastValue(repo.users)

        assertWithMessage("collectLastValue(repo.users)").that(users).isNotNull()
        assertThat(users).containsExactly(User(USER_SYSTEM, Role.PERSONAL))
    }
}

@Suppress("SameParameterValue", "DEPRECATION")
private fun mockUserManager(validUser: Int, invalidUser: Int) =
    mock<UserManager> {
        val info = UserInfo(validUser, "", "", UserInfo.FLAG_FULL)
        doReturn(listOf(info)).whenever(this).getEnabledProfiles(Mockito.anyInt())

        doReturn(info).whenever(this).getUserInfo(Mockito.eq(validUser))

        doReturn(listOf<UserInfo>()).whenever(this).getEnabledProfiles(Mockito.eq(invalidUser))

        doReturn(null).whenever(this).getUserInfo(Mockito.eq(invalidUser))
    }

private fun TestScope.createUserRepository(userManager: FakeUserManager) =
    UserRepositoryImpl(
        profileParent = userManager.state.primaryUserHandle,
        userManager = userManager,
        userEvents = userManager.state.userEvents,
        scope = backgroundScope,
        backgroundDispatcher = Dispatchers.Unconfined
    )
