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

import com.android.intentresolver.Flags.FLAG_ENABLE_PRIVATE_PROFILE
import com.android.intentresolver.inject.FakeChooserServiceFlags
import com.android.intentresolver.inject.FakeIntentResolverFlags
import com.android.intentresolver.inject.IntentResolverFlags
import com.android.intentresolver.v2.data.repository.FakeUserRepository
import com.android.intentresolver.v2.domain.interactor.UserInteractor
import com.android.intentresolver.v2.shared.model.Profile
import com.android.intentresolver.v2.shared.model.User
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*

import org.junit.Test

class ProfileHelperTest {

    private val personalUser = User(0, User.Role.PERSONAL)
    private val cloneUser = User(10, User.Role.CLONE)

    private val personalProfile = Profile(Profile.Type.PERSONAL, personalUser)
    private val personalWithCloneProfile = Profile(Profile.Type.PERSONAL, personalUser, cloneUser)

    private val workUser = User(11, User.Role.WORK)
    private val workProfile = Profile(Profile.Type.WORK, workUser)

    private val privateUser = User(12, User.Role.PRIVATE)
    private val privateProfile = Profile(Profile.Type.PRIVATE, privateUser)

    private val flags = FakeIntentResolverFlags().apply {
        setFlag(FLAG_ENABLE_PRIVATE_PROFILE, true)
    }

    private fun assertProfiles(
        helper: ProfileHelper,
        personalProfile: Profile,
        workProfile: Profile? = null,
        privateProfile: Profile? = null) {

        assertThat(helper.personalProfile).isEqualTo(personalProfile)
        assertThat(helper.personalHandle).isEqualTo(personalProfile.primary.handle)

        personalProfile.clone?.also {
            assertThat(helper.cloneUserPresent).isTrue()
            assertThat(helper.cloneHandle).isEqualTo(it.handle)
        } ?: {
            assertThat(helper.cloneUserPresent).isFalse()
            assertThat(helper.cloneHandle).isNull()
        }

        workProfile?.also {
            assertThat(helper.workProfilePresent).isTrue()
            assertThat(helper.workProfile).isEqualTo(it)
            assertThat(helper.workHandle).isEqualTo(it.primary.handle)
        } ?: {
            assertThat(helper.workProfilePresent).isFalse()
            assertThat(helper.workProfile).isNull()
            assertThat(helper.workHandle).isNull()
        }

        privateProfile?.also {
            assertThat(helper.privateProfilePresent).isTrue()
            assertThat(helper.privateProfile).isEqualTo(it)
            assertThat(helper.privateHandle).isEqualTo(it.primary.handle)
        } ?: {
            assertThat(helper.privateProfilePresent).isFalse()
            assertThat(helper.privateProfile).isNull()
            assertThat(helper.privateHandle).isNull()
        }
    }


    @Test
    fun launchedByPersonal() = runTest {
        val repository = FakeUserRepository(listOf(personalUser))
        val interactor = UserInteractor(repository, launchedAs = personalUser.handle)
        val availability = interactor.availability.first()
        val launchedBy = interactor.launchedAsProfile.first()

        val helper = ProfileHelper(
            interactor = interactor,
            flags = flags,
            profiles = interactor.profiles.first(),
            launchedAsProfile = launchedBy)

        assertProfiles(helper, personalProfile)

        assertThat(helper.isLaunchedAsCloneProfile).isFalse()
        assertThat(helper.launchedAsProfileType).isEqualTo(Profile.Type.PERSONAL)
        assertThat(helper.getQueryIntentsHandle(personalUser.handle))
                .isEqualTo(personalProfile.primary.handle)
        assertThat(helper.tabOwnerUserHandleForLaunch).isEqualTo(personalProfile.primary.handle)
    }

    @Test
    fun launchedByPersonal_withClone() = runTest {
        val repository = FakeUserRepository(listOf(personalUser, cloneUser))
        val interactor = UserInteractor(repository, launchedAs = personalUser.handle)
        val availability = interactor.availability.first()
        val launchedBy = interactor.launchedAsProfile.first()

        val helper = ProfileHelper(
            interactor = interactor,
            flags = flags,
            profiles = interactor.profiles.first(),
            launchedAsProfile = launchedBy)

        assertProfiles(helper, personalWithCloneProfile)

        assertThat(helper.isLaunchedAsCloneProfile).isFalse()
        assertThat(helper.launchedAsProfileType).isEqualTo(Profile.Type.PERSONAL)
        assertThat(helper.getQueryIntentsHandle(personalUser.handle)).isEqualTo(personalUser.handle)
        assertThat(helper.tabOwnerUserHandleForLaunch).isEqualTo(personalProfile.primary.handle)
    }

    @Test
    fun launchedByClone() = runTest {
        val repository = FakeUserRepository(listOf(personalUser, cloneUser))
        val interactor = UserInteractor(repository, launchedAs = cloneUser.handle)
        val availability = interactor.availability.first()
        val launchedBy = interactor.launchedAsProfile.first()

        val helper = ProfileHelper(
            interactor = interactor,
            flags = flags,
            profiles = interactor.profiles.first(),
            launchedAsProfile = launchedBy)

        assertProfiles(helper, personalWithCloneProfile)

        assertThat(helper.isLaunchedAsCloneProfile).isTrue()
        assertThat(helper.launchedAsProfileType).isEqualTo(Profile.Type.PERSONAL)
        assertThat(helper.getQueryIntentsHandle(personalWithCloneProfile.primary.handle))
                .isEqualTo(personalWithCloneProfile.clone?.handle)
        assertThat(helper.tabOwnerUserHandleForLaunch)
                .isEqualTo(personalWithCloneProfile.primary.handle)
    }

    @Test
    fun launchedByPersonal_withWork() = runTest {
        val repository = FakeUserRepository(listOf(personalUser, workUser))
        val interactor = UserInteractor(repository, launchedAs = personalUser.handle)
        val availability = interactor.availability.first()
        val launchedBy = interactor.launchedAsProfile.first()

        val helper = ProfileHelper(
            interactor = interactor,
            flags = flags,
            profiles = interactor.profiles.first(),
            launchedAsProfile = launchedBy)


        assertProfiles(helper,
            personalProfile = personalProfile,
            workProfile = workProfile)

        assertThat(helper.launchedAsProfileType).isEqualTo(Profile.Type.PERSONAL)
        assertThat(helper.isLaunchedAsCloneProfile).isFalse()
        assertThat(helper.getQueryIntentsHandle(personalUser.handle))
                .isEqualTo(personalProfile.primary.handle)
        assertThat(helper.getQueryIntentsHandle(workUser.handle))
                .isEqualTo(workProfile.primary.handle)
        assertThat(helper.tabOwnerUserHandleForLaunch).isEqualTo(personalProfile.primary.handle)
    }

    @Test
    fun launchedByWork() = runTest {
        val repository = FakeUserRepository(listOf(personalUser, workUser))
        val interactor = UserInteractor(repository, launchedAs = workUser.handle)
        val availability = interactor.availability.first()
        val launchedBy = interactor.launchedAsProfile.first()

        val helper = ProfileHelper(
            interactor = interactor,
            flags = flags,
            profiles = interactor.profiles.first(),
            launchedAsProfile = launchedBy)

        assertProfiles(helper,
            personalProfile = personalProfile,
            workProfile = workProfile)

        assertThat(helper.isLaunchedAsCloneProfile).isFalse()
        assertThat(helper.launchedAsProfileType).isEqualTo(Profile.Type.WORK)
        assertThat(helper.getQueryIntentsHandle(personalProfile.primary.handle))
                .isEqualTo(personalProfile.primary.handle)
        assertThat(helper.getQueryIntentsHandle(workProfile.primary.handle))
                .isEqualTo(workProfile.primary.handle)
        assertThat(helper.tabOwnerUserHandleForLaunch)
                .isEqualTo(workProfile.primary.handle)
    }

    @Test
    fun launchedByPersonal_withPrivate() = runTest {
        val repository = FakeUserRepository(listOf(personalUser, privateUser))
        val interactor = UserInteractor(repository, launchedAs = personalUser.handle)
        val availability = interactor.availability.first()
        val launchedBy = interactor.launchedAsProfile.first()

        val helper = ProfileHelper(
            interactor = interactor,
            flags = flags,
            profiles = interactor.profiles.first(),
            launchedAsProfile = launchedBy)

        assertProfiles(helper,
            personalProfile = personalProfile,
            privateProfile = privateProfile)

        assertThat(helper.isLaunchedAsCloneProfile).isFalse()
        assertThat(helper.launchedAsProfileType).isEqualTo(Profile.Type.PERSONAL)
        assertThat(helper.getQueryIntentsHandle(personalProfile.primary.handle))
                .isEqualTo(personalProfile.primary.handle)
        assertThat(helper.getQueryIntentsHandle(privateProfile.primary.handle))
                .isEqualTo(privateProfile.primary.handle)
        assertThat(helper.tabOwnerUserHandleForLaunch).isEqualTo(personalProfile.primary.handle)
    }

    @Test
    fun launchedByPrivate() = runTest {
        val repository = FakeUserRepository(listOf(personalUser, privateUser))
        val interactor = UserInteractor(repository, launchedAs = privateUser.handle)
        val availability = interactor.availability.first()
        val launchedBy = interactor.launchedAsProfile.first()

        val helper = ProfileHelper(
            interactor = interactor,
            flags = flags,
            profiles = interactor.profiles.first(),
            launchedAsProfile = launchedBy)


        assertProfiles(helper,
            personalProfile = personalProfile,
            privateProfile = privateProfile)

        assertThat(helper.isLaunchedAsCloneProfile).isFalse()
        assertThat(helper.launchedAsProfileType).isEqualTo(Profile.Type.PRIVATE)
        assertThat(helper.getQueryIntentsHandle(personalProfile.primary.handle))
                .isEqualTo(personalProfile.primary.handle)
        assertThat(helper.getQueryIntentsHandle(privateProfile.primary.handle))
                .isEqualTo(privateProfile.primary.handle)
        assertThat(helper.tabOwnerUserHandleForLaunch).isEqualTo(privateProfile.primary.handle)
    }

    @Test
    fun launchedByPersonal_withPrivate_privateDisabled() = runTest {
        flags.setFlag(FLAG_ENABLE_PRIVATE_PROFILE, false)

        val repository = FakeUserRepository(listOf(personalUser, privateUser))
        val interactor = UserInteractor(repository, launchedAs = personalUser.handle)
        val availability = interactor.availability.first()
        val launchedBy = interactor.launchedAsProfile.first()

        val helper = ProfileHelper(
            interactor = interactor,
            flags = flags,
            profiles = interactor.profiles.first(),
            launchedAsProfile = launchedBy)

        assertProfiles(helper,
            personalProfile = personalProfile,
            privateProfile = null)

        assertThat(helper.isLaunchedAsCloneProfile).isFalse()
        assertThat(helper.launchedAsProfileType).isEqualTo(Profile.Type.PERSONAL)
        assertThat(helper.getQueryIntentsHandle(personalProfile.primary.handle))
                .isEqualTo(personalProfile.primary.handle)
        assertThat(helper.tabOwnerUserHandleForLaunch).isEqualTo(personalProfile.primary.handle)
    }
}