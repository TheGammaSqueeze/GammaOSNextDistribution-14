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

package com.android.intentresolver.v2.data.repository

import com.android.intentresolver.v2.coroutines.collectLastValue
import com.android.intentresolver.v2.shared.model.User
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FakeUserRepositoryTest {
    private val baseId = Random.nextInt(1000, 2000)

    private val personalUser = User(id = baseId, role = User.Role.PERSONAL)
    private val cloneUser = User(id = baseId + 1, role = User.Role.CLONE)
    private val workUser = User(id = baseId + 2, role = User.Role.WORK)
    private val privateUser = User(id = baseId + 3, role = User.Role.PRIVATE)

    @Test
    fun init() = runTest {
        val repo = FakeUserRepository(listOf(personalUser, workUser, privateUser))

        val users by collectLastValue(repo.users)
        assertThat(users).containsExactly(personalUser, workUser, privateUser)
    }

    @Test
    fun addUser() = runTest {
        val repo = FakeUserRepository(emptyList())

        val users by collectLastValue(repo.users)
        assertThat(users).isEmpty()

        repo.addUser(personalUser, true)
        assertThat(users).containsExactly(personalUser)

        repo.addUser(workUser, false)
        assertThat(users).containsExactly(personalUser, workUser)
    }

    @Test
    fun removeUser() = runTest {
        val repo = FakeUserRepository(listOf(personalUser, workUser))

        val users by collectLastValue(repo.users)
        repo.removeUser(workUser)
        assertThat(users).containsExactly(personalUser)

        repo.removeUser(personalUser)
        assertThat(users).isEmpty()
    }

    @Test
    fun isAvailable_defaultValue() = runTest {
        val repo = FakeUserRepository(listOf(personalUser, workUser))

        val available by collectLastValue(repo.availability)

        repo.requestState(workUser, false)
        assertThat(available!![workUser]).isFalse()

        repo.requestState(workUser, true)
        assertThat(available!![workUser]).isTrue()
    }

    @Test
    fun isAvailable() = runTest {
        val repo = FakeUserRepository(listOf(personalUser, workUser))

        val available by collectLastValue(repo.availability)
        assertThat(available!![workUser]).isTrue()

        repo.requestState(workUser, false)
        assertThat(available!![workUser]).isFalse()

        repo.requestState(workUser, true)
        assertThat(available!![workUser]).isTrue()
    }

    @Test
    fun isAvailable_addRemove() = runTest {
        val repo = FakeUserRepository(listOf(personalUser, workUser))

        val available by collectLastValue(repo.availability)
        assertThat(available!![workUser]).isTrue()

        repo.removeUser(workUser)
        assertThat(available!![workUser]).isNull()

        repo.addUser(workUser, true)
        assertThat(available!![workUser]).isTrue()
    }
}
