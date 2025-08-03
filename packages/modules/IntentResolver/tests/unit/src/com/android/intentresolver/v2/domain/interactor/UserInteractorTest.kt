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

import com.android.intentresolver.v2.coroutines.collectLastValue
import com.android.intentresolver.v2.data.repository.FakeUserRepository
import com.android.intentresolver.v2.shared.model.Profile
import com.android.intentresolver.v2.shared.model.Profile.Type.PERSONAL
import com.android.intentresolver.v2.shared.model.Profile.Type.PRIVATE
import com.android.intentresolver.v2.shared.model.Profile.Type.WORK
import com.android.intentresolver.v2.shared.model.User
import com.android.intentresolver.v2.shared.model.User.Role
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class UserInteractorTest {
    private val baseId = Random.nextInt(1000, 2000)

    private val personalUser = User(id = baseId, role = Role.PERSONAL)
    private val cloneUser = User(id = baseId + 1, role = Role.CLONE)
    private val workUser = User(id = baseId + 2, role = Role.WORK)
    private val privateUser = User(id = baseId + 3, role = Role.PRIVATE)

    val personalProfile = Profile(PERSONAL, personalUser)
    val workProfile = Profile(WORK, workUser)
    val privateProfile = Profile(PRIVATE, privateUser)

    @Test
    fun launchedByProfile(): Unit = runTest {
        val profileInteractor =
            UserInteractor(
                userRepository = FakeUserRepository(listOf(personalUser, cloneUser)),
                launchedAs = personalUser.handle
            )

        val launchedAsProfile by collectLastValue(profileInteractor.launchedAsProfile)

        assertThat(launchedAsProfile).isEqualTo(Profile(PERSONAL, personalUser, cloneUser))
    }

    @Test
    fun launchedByProfile_asClone(): Unit = runTest {
        val profileInteractor =
            UserInteractor(
                userRepository = FakeUserRepository(listOf(personalUser, cloneUser)),
                launchedAs = cloneUser.handle
            )
        val profiles by collectLastValue(profileInteractor.launchedAsProfile)

        assertThat(profiles).isEqualTo(Profile(PERSONAL, personalUser, cloneUser))
    }

    @Test
    fun profiles_withPersonal(): Unit = runTest {
        val profileInteractor =
            UserInteractor(
                userRepository = FakeUserRepository(listOf(personalUser)),
                launchedAs = personalUser.handle
            )

        val profiles by collectLastValue(profileInteractor.profiles)

        assertThat(profiles).containsExactly(Profile(PERSONAL, personalUser))
    }

    @Test
    fun profiles_addClone(): Unit = runTest {
        val fakeUserRepo = FakeUserRepository(listOf(personalUser))
        val profileInteractor =
            UserInteractor(userRepository = fakeUserRepo, launchedAs = personalUser.handle)

        val profiles by collectLastValue(profileInteractor.profiles)
        assertThat(profiles).containsExactly(Profile(PERSONAL, personalUser))

        fakeUserRepo.addUser(cloneUser, available = true)
        assertThat(profiles).containsExactly(Profile(PERSONAL, personalUser, cloneUser))
    }

    @Test
    fun profiles_withPersonalAndClone(): Unit = runTest {
        val profileInteractor =
            UserInteractor(
                userRepository = FakeUserRepository(listOf(personalUser, cloneUser)),
                launchedAs = personalUser.handle
            )
        val profiles by collectLastValue(profileInteractor.profiles)

        assertThat(profiles).containsExactly(Profile(PERSONAL, personalUser, cloneUser))
    }

    @Test
    fun profiles_withAllSupportedTypes(): Unit = runTest {
        val profileInteractor =
            UserInteractor(
                userRepository =
                    FakeUserRepository(listOf(personalUser, cloneUser, workUser, privateUser)),
                launchedAs = personalUser.handle
            )
        val profiles by collectLastValue(profileInteractor.profiles)

        assertThat(profiles)
            .containsExactly(
                Profile(PERSONAL, personalUser, cloneUser),
                Profile(WORK, workUser),
                Profile(PRIVATE, privateUser)
            )
    }

    @Test
    fun profiles_preservesIterationOrder(): Unit = runTest {
        val profileInteractor =
            UserInteractor(
                userRepository =
                    FakeUserRepository(listOf(workUser, cloneUser, privateUser, personalUser)),
                launchedAs = personalUser.handle
            )

        val profiles by collectLastValue(profileInteractor.profiles)

        assertThat(profiles)
            .containsExactly(
                Profile(WORK, workUser),
                Profile(PRIVATE, privateUser),
                Profile(PERSONAL, personalUser, cloneUser),
            )
    }

    @Test
    fun isAvailable_defaultValue() = runTest {
        val userRepo = FakeUserRepository(listOf(personalUser))
        userRepo.addUser(workUser, false)

        val interactor =
            UserInteractor(userRepository = userRepo, launchedAs = personalUser.handle)

        val availability by collectLastValue(interactor.availability)

        assertWithMessage("personalAvailable").that(availability?.get(personalProfile)).isTrue()
        assertWithMessage("workAvailable").that(availability?.get(workProfile)).isFalse()
    }

    @Test
    fun isAvailable() = runTest {
        val userRepo = FakeUserRepository(listOf(workUser, personalUser))
        val interactor =
            UserInteractor(userRepository = userRepo, launchedAs = personalUser.handle)

        val availability by collectLastValue(interactor.availability)

        // Default state is enabled in FakeUserManager
        assertWithMessage("workAvailable").that(availability?.get(workProfile)).isTrue()

        // Making user unavailable makes profile unavailable
        userRepo.requestState(workUser, false)
        assertWithMessage("workAvailable").that(availability?.get(workProfile)).isFalse()

        // Making user available makes profile available again
        userRepo.requestState(workUser, true)
        assertWithMessage("workAvailable").that(availability?.get(workProfile)).isTrue()

        // When a user is removed availability is removed as well.
        userRepo.removeUser(workUser)
        assertWithMessage("workAvailable").that(availability?.get(workProfile)).isNull()
    }

    /**
     * Similar to the above test in reverse: uses UserInteractor to modify state, and verify the
     * state of the UserRepository.
     */
    @Test
    fun updateState() = runTest {
        val userRepo = FakeUserRepository(listOf(workUser, personalUser))
        val userInteractor =
            UserInteractor(userRepository = userRepo, launchedAs = personalUser.handle)
        val workProfile = Profile(Profile.Type.WORK, workUser)

        val availability by collectLastValue(userRepo.availability)

        // Default state is enabled in FakeUserManager
        assertWithMessage("workAvailable").that(availability?.get(workUser)).isTrue()

        userInteractor.updateState(workProfile, false)
        assertWithMessage("workAvailable").that(availability?.get(workUser)).isFalse()

        userInteractor.updateState(workProfile, true)
        assertWithMessage("workAvailable").that(availability?.get(workUser)).isTrue()
    }
}
