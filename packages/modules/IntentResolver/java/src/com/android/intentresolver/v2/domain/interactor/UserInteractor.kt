/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.intentresolver.v2.domain.interactor

import android.os.UserHandle
import com.android.intentresolver.inject.ApplicationUser
import com.android.intentresolver.v2.data.repository.UserRepository
import com.android.intentresolver.v2.shared.model.Profile
import com.android.intentresolver.v2.shared.model.Profile.Type
import com.android.intentresolver.v2.shared.model.User
import com.android.intentresolver.v2.shared.model.User.Role
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** The high level User interface. */
class UserInteractor
@Inject
constructor(
    private val userRepository: UserRepository,
    /** The specific [User] of the application which started this one. */
    @ApplicationUser val launchedAs: UserHandle,
) {
    /** The profile group associated with the launching app user. */
    val profiles: Flow<List<Profile>> =
        userRepository.users.map { users ->
            users.mapNotNull { user ->
                when (user.role) {
                    // PERSONAL includes CLONE
                    Role.PERSONAL -> {
                        Profile(Type.PERSONAL, user, users.firstOrNull { it.role == Role.CLONE })
                    }
                    Role.CLONE -> {
                        /* ignore, included above */
                        null
                    }
                    // others map 1:1
                    else -> Profile(profileFromRole(user.role), user)
                }
            }
        }

    /** The [Profile] of the application which started this one. */
    val launchedAsProfile: Flow<Profile> =
        profiles.map { profiles ->
            // The launching user profile is the one with a primary id or clone id
            // matching the application user id. By definition there must always be exactly
            // one matching profile for the current user.
            profiles.single {
                it.primary.id == launchedAs.identifier || it.clone?.id == launchedAs.identifier
            }
        }
    /**
     * Provides a flow to report on the availability of profile. An unavailable profile may be
     * hidden or appear disabled within the app.
     */
    val availability: Flow<Map<Profile, Boolean>> =
        combine(profiles, userRepository.availability) { profiles, availability ->
            profiles.associateWith {
                availability.getOrDefault(it.primary, false)
            }
        }

    /**
     * Request the profile state be updated. In the case of enabling, the operation could take
     * significant time and/or require user input.
     */
    suspend fun updateState(profile: Profile, available: Boolean) {
        userRepository.requestState(profile.primary, available)
    }

    private fun profileFromRole(role: Role): Type =
        when (role) {
            Role.PERSONAL -> Type.PERSONAL
            Role.CLONE -> Type.PERSONAL /* CLONE maps to PERSONAL */
            Role.PRIVATE -> Type.PRIVATE
            Role.WORK -> Type.WORK
        }
}
