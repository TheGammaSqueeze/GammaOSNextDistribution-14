package com.android.intentresolver.v2.platform

import android.content.Context
import android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE
import android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE
import android.content.Intent.ACTION_PROFILE_ADDED
import android.content.Intent.ACTION_PROFILE_AVAILABLE
import android.content.Intent.ACTION_PROFILE_REMOVED
import android.content.Intent.ACTION_PROFILE_UNAVAILABLE
import android.content.pm.UserInfo
import android.content.pm.UserInfo.FLAG_FULL
import android.content.pm.UserInfo.FLAG_INITIALIZED
import android.content.pm.UserInfo.FLAG_PROFILE
import android.content.pm.UserInfo.NO_PROFILE_GROUP_ID
import android.os.IUserManager
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.NonNull
import com.android.intentresolver.THROWS_EXCEPTION
import com.android.intentresolver.mock
import com.android.intentresolver.v2.data.repository.UserRepositoryImpl.UserEvent
import com.android.intentresolver.v2.platform.FakeUserManager.State
import com.android.intentresolver.whenever
import kotlin.random.Random
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.withSettings

/**
 * A stand-in for [UserManager] to support testing of data layer components which depend on it.
 *
 * This fake targets system applications which need to interact with any or all of the current
 * user's associated profiles (as reported by [getEnabledProfiles]). Support for manipulating
 * non-profile (full) secondary users (switching active foreground user, adding or removing users)
 * is not included.
 *
 * Upon creation [FakeUserManager] contains a single primary (full) user with a randomized ID. This
 * is available from [FakeUserManager.state] using [primaryUserHandle][State.primaryUserHandle] or
 * [getPrimaryUser][State.getPrimaryUser].
 *
 * To make state changes, use functions available from [FakeUserManager.state]:
 * * [createProfile][State.createProfile]
 * * [removeProfile][State.removeProfile]
 * * [setQuietMode][State.setQuietMode]
 *
 * Any functionality not explicitly overridden here is guaranteed to throw an exception when
 * accessed (access to the real system service is prevented).
 */
class FakeUserManager(val state: State = State()) :
    UserManager(/* context = */ mockContext(), /* service = */ mockService()) {

    enum class ProfileType {
        WORK,
        CLONE,
        PRIVATE
    }

    override fun getProfileParent(userHandle: UserHandle): UserHandle? {
        return state.getUserOrNull(userHandle)?.let { user ->
            if (user.isProfile) {
                state.getUserOrNull(UserHandle.of(user.profileGroupId))?.userHandle
            } else {
                null
            }
        }
    }

    override fun getUserInfo(userId: Int): UserInfo? {
        return state.getUserOrNull(UserHandle.of(userId))
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getEnabledProfiles(userId: Int): List<UserInfo> {
        val user = state.users.single { it.id == userId }
        return state.users.filter { other ->
            user.id == other.id || user.profileGroupId == other.profileGroupId
        }
    }

    override fun requestQuietModeEnabled(
        enableQuietMode: Boolean,
        @NonNull userHandle: UserHandle
    ): Boolean {
        state.setQuietMode(userHandle, enableQuietMode)
        return true
    }

    override fun isQuietModeEnabled(userHandle: UserHandle): Boolean {
        return state.getUser(userHandle).isQuietModeEnabled
    }

    override fun toString(): String {
        return "FakeUserManager(state=$state)"
    }

    class State {
        private val eventChannel = Channel<UserEvent>()
        private val userInfoMap: MutableMap<UserHandle, UserInfo> = mutableMapOf()

        /** The id of the primary/full/system user, which is automatically created. */
        val primaryUserHandle: UserHandle

        /**
         * Retrieves the primary user. The value returned changes, but the values are immutable.
         *
         * Do not cache this value in tests, between operations.
         */
        fun getPrimaryUser(): UserInfo = getUser(primaryUserHandle)

        private var nextUserId: Int = 100 + Random.nextInt(0, 900)

        /**
         * A flow of [UserEvent] which emulates those normally generated from system broadcasts.
         *
         * Events are produced by calls to [createPrimaryUser], [createProfile], [removeProfile].
         */
        val userEvents: Flow<UserEvent>

        val users: List<UserInfo>
            get() = userInfoMap.values.toList()

        val userHandles: List<UserHandle>
            get() = userInfoMap.keys.toList()

        init {
            primaryUserHandle = createPrimaryUser(allocateNextId())
            userEvents = eventChannel.consumeAsFlow()
        }

        private fun allocateNextId() = nextUserId++

        private fun createPrimaryUser(id: Int): UserHandle {
            val userInfo =
                UserInfo(id, "", "", FLAG_INITIALIZED or FLAG_FULL, USER_TYPE_FULL_SYSTEM)
            userInfoMap[userInfo.userHandle] = userInfo
            return userInfo.userHandle
        }

        fun getUserOrNull(handle: UserHandle): UserInfo? = userInfoMap[handle]

        fun getUser(handle: UserHandle): UserInfo =
            requireNotNull(getUserOrNull(handle)) {
                "Expected userInfoMap to contain an entry for $handle"
            }

        fun setQuietMode(user: UserHandle, quietMode: Boolean) {
            userInfoMap[user]?.also {
                it.flags =
                    if (quietMode) {
                        it.flags or UserInfo.FLAG_QUIET_MODE
                    } else {
                        it.flags and UserInfo.FLAG_QUIET_MODE.inv()
                    }
                val actions = mutableListOf<String>()
                if (quietMode) {
                    actions += ACTION_PROFILE_UNAVAILABLE
                    if (it.isManagedProfile) {
                        actions += ACTION_MANAGED_PROFILE_UNAVAILABLE
                    }
                } else {
                    actions += ACTION_PROFILE_AVAILABLE
                    if (it.isManagedProfile) {
                        actions += ACTION_MANAGED_PROFILE_AVAILABLE
                    }
                }
                actions.forEach { action ->
                    eventChannel.trySend(UserEvent(action, user, quietMode))
                }
            }
        }

        fun createProfile(type: ProfileType, parent: UserHandle = primaryUserHandle): UserHandle {
            val parentUser = getUser(parent)
            require(!parentUser.isProfile) { "Parent user cannot be a profile" }

            // Ensure the parent user has a valid profileGroupId
            if (parentUser.profileGroupId == NO_PROFILE_GROUP_ID) {
                parentUser.profileGroupId = parentUser.id
            }
            val id = allocateNextId()
            val userInfo =
                UserInfo(id, "", "", FLAG_INITIALIZED or FLAG_PROFILE, type.toUserType()).apply {
                    profileGroupId = parentUser.profileGroupId
                }
            userInfoMap[userInfo.userHandle] = userInfo
            eventChannel.trySend(UserEvent(ACTION_PROFILE_ADDED, userInfo.userHandle))
            return userInfo.userHandle
        }

        fun removeProfile(handle: UserHandle): Boolean {
            return userInfoMap[handle]?.let { user ->
                require(user.isProfile) { "Only profiles can be removed" }
                userInfoMap.remove(user.userHandle)
                eventChannel.trySend(UserEvent(ACTION_PROFILE_REMOVED, user.userHandle))
                return true
            }
                ?: false
        }

        override fun toString() = buildString {
            append("State(nextUserId=$nextUserId, userInfoMap=[")
            userInfoMap.entries.forEach {
                append("UserHandle[${it.key.identifier}] = ${it.value.debugString},")
            }
            append("])")
        }
    }
}

/** A safe mock of [Context] which throws on any unstubbed method call. */
private fun mockContext(user: UserHandle = UserHandle.SYSTEM): Context {
    return mock<Context>(withSettings().defaultAnswer(THROWS_EXCEPTION)) {
        doAnswer(RETURNS_SELF).whenever(this).applicationContext
        doReturn(user).whenever(this).user
        doReturn(user.identifier).whenever(this).userId
    }
}

private fun FakeUserManager.ProfileType.toUserType(): String {
    return when (this) {
        FakeUserManager.ProfileType.WORK -> UserManager.USER_TYPE_PROFILE_MANAGED
        FakeUserManager.ProfileType.CLONE -> UserManager.USER_TYPE_PROFILE_CLONE
        FakeUserManager.ProfileType.PRIVATE -> UserManager.USER_TYPE_PROFILE_PRIVATE
    }
}

/** A safe mock of [IUserManager] which throws on any unstubbed method call. */
fun mockService(): IUserManager {
    return mock<IUserManager>(withSettings().defaultAnswer(THROWS_EXCEPTION))
}

val UserInfo.debugString: String
    get() =
        "UserInfo(id=$id, profileGroupId=$profileGroupId, name=$name, " +
            "type=$userType, flags=${UserInfo.flagsToString(flags)})"
