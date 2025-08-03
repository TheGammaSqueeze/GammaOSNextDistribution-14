package com.android.intentresolver.v2.data.repository

import android.content.pm.UserInfo
import com.android.intentresolver.v2.shared.model.User
import com.android.intentresolver.v2.shared.model.User.Role

/** Maps the UserInfo to one of the defined [Roles][User.Role], if possible. */
fun UserInfo.getSupportedUserRole(): Role? =
    when {
        isFull -> Role.PERSONAL
        isManagedProfile -> Role.WORK
        isCloneProfile -> Role.CLONE
        isPrivateProfile -> Role.PRIVATE
        else -> null
    }

/**
 * Creates a [User], based on values from a [UserInfo].
 *
 * ```
 * val users: List<User> =
 *     getEnabledProfiles(user).map(::toUser).filterNotNull()
 * ```
 *
 * @return a [User] if the [UserInfo] matched a supported [Role], otherwise null
 */
fun UserInfo.toUser(): User? {
    return getSupportedUserRole()?.let { role -> User(userHandle.identifier, role) }
}
