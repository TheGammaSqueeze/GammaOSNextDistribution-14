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

package com.android.intentresolver.v2

import android.os.UserHandle
import com.android.intentresolver.inject.IntentResolverFlags
import com.android.intentresolver.v2.domain.interactor.UserInteractor
import com.android.intentresolver.v2.shared.model.Profile
import com.android.intentresolver.v2.shared.model.User
import javax.inject.Inject

class ProfileHelper @Inject constructor(
    interactor: UserInteractor,
    private val flags: IntentResolverFlags,
    profiles: List<Profile>,
    launchedAsProfile: Profile,
) {
    private val launchedByHandle: UserHandle = interactor.launchedAs

    // Map UserHandle back to a user within launchedByProfile
    private val launchedByUser = when (launchedByHandle) {
        launchedAsProfile.primary.handle -> launchedAsProfile.primary
        launchedAsProfile.clone?.handle -> launchedAsProfile.clone
        else -> error("launchedByUser must be a member of launchedByProfile")
    }
    val launchedAsProfileType: Profile.Type = launchedAsProfile.type

    val personalProfile = profiles.single { it.type == Profile.Type.PERSONAL }
    val workProfile = profiles.singleOrNull { it.type == Profile.Type.WORK }
    val privateProfile = profiles.singleOrNull { it.type == Profile.Type.PRIVATE }

    val personalHandle = personalProfile.primary.handle
    val workHandle = workProfile?.primary?.handle
    val privateHandle = privateProfile?.primary?.handle?.takeIf { flags.enablePrivateProfile() }
    val cloneHandle = personalProfile.clone?.handle

    val isLaunchedAsCloneProfile = launchedByUser == launchedAsProfile.clone

    val cloneUserPresent = personalProfile.clone != null
    val workProfilePresent = workProfile != null
    val privateProfilePresent = privateProfile != null

    // Name retained for ease of review, to be renamed later
    val tabOwnerUserHandleForLaunch = if (launchedByUser.role == User.Role.CLONE) {
        // When started by clone user, return the profile owner instead
        launchedAsProfile.primary.handle
    } else {
        // Otherwise the launched user is used
        launchedByUser.handle
    }

    // Name retained for ease of review, to be renamed later
    fun getQueryIntentsHandle(handle: UserHandle): UserHandle? {
        return if (isLaunchedAsCloneProfile && handle == personalHandle) {
            cloneHandle
        } else {
            handle
        }
    }
}
