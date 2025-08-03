package com.android.intentresolver.v2.data.repository

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE
import android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE
import android.content.Intent.ACTION_PROFILE_ADDED
import android.content.Intent.ACTION_PROFILE_AVAILABLE
import android.content.Intent.ACTION_PROFILE_REMOVED
import android.content.Intent.ACTION_PROFILE_UNAVAILABLE
import android.content.Intent.EXTRA_QUIET_MODE
import android.content.Intent.EXTRA_USER
import android.content.IntentFilter
import android.content.pm.UserInfo
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.intentresolver.inject.Background
import com.android.intentresolver.inject.Main
import com.android.intentresolver.inject.ProfileParent
import com.android.intentresolver.v2.data.broadcastFlow
import com.android.intentresolver.v2.data.repository.UserRepositoryImpl.UserEvent
import com.android.intentresolver.v2.shared.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

interface UserRepository {
    /**
     * A [Flow] user profile groups. Each list contains the context user along with all members of
     * the profile group. This includes the (Full) parent user, if the context user is a profile.
     */
    val users: Flow<List<User>>

    /**
     * A [Flow] of availability. Only profile users may become unavailable.
     *
     * Availability is currently defined as not being in [quietMode][UserInfo.isQuietModeEnabled].
     */
    val availability: Flow<Map<User, Boolean>>

    /**
     * Request that availability be updated to the requested state. This currently includes toggling
     * quiet mode as needed. This may involve additional background actions, such as starting or
     * stopping a profile user (along with their many associated processes).
     *
     * If successful, the change will be applied after the call returns and can be observed using
     * [UserRepository.isAvailable] for the given user.
     *
     * No actions are taken if the user is already in requested state.
     *
     * @throws IllegalArgumentException if called for an unsupported user type
     */
    suspend fun requestState(user: User, available: Boolean)
}

private const val TAG = "UserRepository"

private data class UserWithState(val user: User, val available: Boolean)

private typealias UserStates = List<UserWithState>

/** Tracks and publishes state for the parent user and associated profiles. */
class UserRepositoryImpl
@VisibleForTesting
constructor(
    private val profileParent: UserHandle,
    private val userManager: UserManager,
    /** A flow of events which represent user-state changes from [UserManager]. */
    private val userEvents: Flow<UserEvent>,
    scope: CoroutineScope,
    private val backgroundDispatcher: CoroutineDispatcher
) : UserRepository {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @ProfileParent profileParent: UserHandle,
        userManager: UserManager,
        @Main scope: CoroutineScope,
        @Background background: CoroutineDispatcher
    ) : this(
        profileParent,
        userManager,
        userEvents = userBroadcastFlow(context, profileParent),
        scope,
        background
    )

    data class UserEvent(val action: String, val user: UserHandle, val quietMode: Boolean = false)

    /**
     * An exception which indicates that an inconsistency exists between the user state map and the
     * rest of the system.
     */
    internal class UserStateException(
        override val message: String,
        val event: UserEvent,
        override val cause: Throwable? = null
    ) : RuntimeException("$message: event=$event", cause)

    private val sharingScope = CoroutineScope(scope.coroutineContext + backgroundDispatcher)
    private val usersWithState: Flow<UserStates> =
        userEvents
            .onStart { emit(UserEvent(INITIALIZE, profileParent)) }
            .onEach { Log.i(TAG, "userEvent: $it") }
            .runningFold<UserEvent, UserStates>(emptyList()) { users, event ->
                try {
                    // Handle an action by performing some operation, then returning a new map
                    when (event.action) {
                        INITIALIZE -> createNewUserStates(profileParent)
                        ACTION_PROFILE_ADDED -> handleProfileAdded(event, users)
                        ACTION_PROFILE_REMOVED -> handleProfileRemoved(event, users)
                        ACTION_MANAGED_PROFILE_UNAVAILABLE,
                        ACTION_MANAGED_PROFILE_AVAILABLE,
                        ACTION_PROFILE_AVAILABLE,
                        ACTION_PROFILE_UNAVAILABLE -> handleAvailability(event, users)
                        else -> {
                            Log.w(TAG, "Unhandled event: $event)")
                            users
                        }
                    }
                } catch (e: UserStateException) {
                    Log.e(TAG, "An error occurred handling an event: ${e.event}", e)
                    Log.e(TAG, "Attempting to recover...")
                    createNewUserStates(profileParent)
                }
            }
            .distinctUntilChanged()
            .onEach { Log.i(TAG, "userStateList: $it") }
            .stateIn(sharingScope, SharingStarted.Eagerly, emptyList())
            .filterNot { it.isEmpty() }

    override val users: Flow<List<User>> =
        usersWithState.map { userStateMap -> userStateMap.map { it.user } }.distinctUntilChanged()

    override val availability: Flow<Map<User, Boolean>> =
        usersWithState
            .map { list -> list.associate { it.user to it.available } }
            .distinctUntilChanged()

    override suspend fun requestState(user: User, available: Boolean) {
        require(user.type == User.Type.PROFILE) { "Only profile users are supported" }
        return withContext(backgroundDispatcher) {
            Log.i(TAG, "requestQuietModeEnabled: ${!available} for user $user")
            userManager.requestQuietModeEnabled(/* enableQuietMode = */ !available, user.handle)
        }
    }

    private fun List<UserWithState>.update(handle: UserHandle, user: UserWithState) =
        filter { it.user.id != handle.identifier } + user

    private fun handleAvailability(event: UserEvent, current: UserStates): UserStates {
        val userEntry =
            current.firstOrNull { it.user.id == event.user.identifier }
                ?: throw UserStateException("User was not present in the map", event)
        return current.update(event.user, userEntry.copy(available = !event.quietMode))
    }

    private fun handleProfileRemoved(event: UserEvent, current: UserStates): UserStates {
        if (!current.any { it.user.id == event.user.identifier }) {
            throw UserStateException("User was not present in the map", event)
        }
        return current.filter { it.user.id != event.user.identifier }
    }

    private suspend fun handleProfileAdded(event: UserEvent, current: UserStates): UserStates {
        val user =
            try {
                requireNotNull(readUser(event.user))
            } catch (e: Exception) {
                throw UserStateException("Failed to read user from UserManager", event, e)
            }
        return current + UserWithState(user, !event.quietMode)
    }

    private suspend fun createNewUserStates(user: UserHandle): UserStates {
        val profiles = readProfileGroup(user)
        return profiles.mapNotNull { userInfo ->
            userInfo.toUser()?.let { user -> UserWithState(user, userInfo.isAvailable()) }
        }
    }

    private suspend fun readProfileGroup(member: UserHandle): List<UserInfo> {
        return withContext(backgroundDispatcher) {
                @Suppress("DEPRECATION") userManager.getEnabledProfiles(member.identifier)
            }
            .toList()
    }

    /** Read [UserInfo] from [UserManager], or null if not found or an unsupported type. */
    private suspend fun readUser(user: UserHandle): User? {
        val userInfo =
            withContext(backgroundDispatcher) { userManager.getUserInfo(user.identifier) }
        return userInfo?.let { info ->
            info.getSupportedUserRole()?.let { role -> User(info.id, role) }
        }
    }
}

/** Used with [broadcastFlow] to transform a UserManager broadcast action into a [UserEvent]. */
private fun Intent.toUserEvent(): UserEvent? {
    val action = action
    val user = extras?.getParcelable(EXTRA_USER, UserHandle::class.java)
    val quietMode = extras?.getBoolean(EXTRA_QUIET_MODE, false) ?: false
    return if (user == null || action == null) {
        null
    } else {
        UserEvent(action, user, quietMode)
    }
}

const val INITIALIZE = "INITIALIZE"

private fun createFilter(actions: Iterable<String>): IntentFilter {
    return IntentFilter().apply { actions.forEach(::addAction) }
}

private fun UserInfo?.isAvailable(): Boolean {
    return this?.isQuietModeEnabled != true
}

private fun userBroadcastFlow(context: Context, profileParent: UserHandle): Flow<UserEvent> {
    val userActions =
        setOf(
            ACTION_PROFILE_ADDED,
            ACTION_PROFILE_REMOVED,

            // Quiet mode enabled/disabled for managed
            // From: UserController.broadcastProfileAvailabilityChanges
            // In response to setQuietModeEnabled
            ACTION_MANAGED_PROFILE_AVAILABLE, // quiet mode, sent for manage profiles only
            ACTION_MANAGED_PROFILE_UNAVAILABLE, // quiet mode, sent for manage profiles only

            // Quiet mode toggled for profile type, requires flag 'android.os.allow_private_profile
            // true'
            ACTION_PROFILE_AVAILABLE, // quiet mode,
            ACTION_PROFILE_UNAVAILABLE, // quiet mode, sent for any profile type
        )
    return broadcastFlow(context, createFilter(userActions), profileParent, Intent::toUserEvent)
}
