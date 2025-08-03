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

import com.android.intentresolver.v2.shared.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A simple repository which can be initialized from a list and updated. */
class FakeUserRepository(userList: List<User>) : UserRepository {
    internal data class UserState(val user: User, val available: Boolean)

    private val userState = MutableStateFlow(userList.map { UserState(it, available = true) })

    // Expose a List<User> from List<UserState>
    override val users = userState.map { userList -> userList.map { it.user } }

    fun addUser(user: User, available: Boolean) {
        require(userState.value.none { it.user.id == user.id }) {
            "A User with ${user.id} already exists!"
        }
        userState.update { it + UserState(user, available) }
    }

    fun removeUser(user: User) {
        require(userState.value.any { it.user.id == user.id }) {
            "A User with ${user.id} does not exist!"
        }
        userState.update { it.filterNot { state -> state.user.id == user.id } }
    }

    override val availability =
        userState.map { userStateList -> userStateList.associate { it.user to it.available } }

    fun updateState(user: User, available: Boolean) {
        userState.update { userStateList ->
            userStateList.map { userState ->
                if (userState.user.id == user.id) {
                    UserState(user, available)
                } else {
                    userState
                }
            }
        }
    }

    override suspend fun requestState(user: User, available: Boolean) {
        updateState(user, available)
    }
}
