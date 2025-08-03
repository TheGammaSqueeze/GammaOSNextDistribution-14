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

import android.util.Log
import com.android.intentresolver.v2.data.repository.FakeUserRepository
import com.android.intentresolver.v2.domain.interactor.UserInteractor
import com.android.intentresolver.v2.shared.model.Profile
import com.android.intentresolver.v2.shared.model.User
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val TAG = "ProfileAvailabilityTest"

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileAvailabilityTest {
    private val personalUser = User(0, User.Role.PERSONAL)
    private val workUser = User(10, User.Role.WORK)

    private val personalProfile = Profile(Profile.Type.PERSONAL, personalUser)
    private val workProfile = Profile(Profile.Type.WORK, workUser)

    private val repository = FakeUserRepository(listOf(personalUser, workUser))
    private val interactor = UserInteractor(repository, launchedAs = personalUser.handle)

    @Test
    fun testProfileAvailable() = runTest {
        val availability = ProfileAvailability(backgroundScope, interactor)
        runCurrent()

        assertThat(availability.isAvailable(personalProfile)).isTrue()
        assertThat(availability.isAvailable(workProfile)).isTrue()

        availability.requestQuietModeState(workProfile, true)
        runCurrent()

        assertThat(availability.isAvailable(workProfile)).isFalse()

        availability.requestQuietModeState(workProfile, false)
        runCurrent()

        assertThat(availability.isAvailable(workProfile)).isTrue()
    }

    @Test
    fun waitingToEnableProfile() = runTest {
        val availability = ProfileAvailability(backgroundScope, interactor)
        runCurrent()

        availability.requestQuietModeState(workProfile, true)
        assertThat(availability.waitingToEnableProfile).isFalse()
        runCurrent()

        availability.requestQuietModeState(workProfile, false)
        assertThat(availability.waitingToEnableProfile).isTrue()

        runCurrent()

        assertThat(availability.waitingToEnableProfile).isFalse()
    }
}