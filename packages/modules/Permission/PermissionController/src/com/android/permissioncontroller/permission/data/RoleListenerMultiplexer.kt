/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.permissioncontroller.permission.data

import android.app.Application
import android.app.role.OnRoleHoldersChangedListener
import android.app.role.RoleManager
import android.os.UserHandle
import androidx.annotation.GuardedBy
import com.android.permissioncontroller.PermissionControllerApplication

/** Serves as a single shared Role Change Listener. */
object RoleListenerMultiplexer : OnRoleHoldersChangedListener {

    private val app: Application = PermissionControllerApplication.get()

    @GuardedBy("lock")
    private val callbacks =
        mutableMapOf<UserHandle, MutableMap<String, MutableList<RoleHoldersChangeCallback>>>()

    private val roleManager = app.getSystemService(RoleManager::class.java)!!

    private val lock = Object()

    override fun onRoleHoldersChanged(roleName: String, user: UserHandle) {
        val callbacksCopy: List<RoleHoldersChangeCallback>?
        synchronized(lock) { callbacksCopy = callbacks[user]?.get(roleName)?.toList() }
        callbacksCopy?.forEach { listener -> listener.onRoleHoldersChanged() }
    }

    fun addCallback(roleName: String, user: UserHandle, callback: RoleHoldersChangeCallback) {
        val wasEmpty: Boolean
        synchronized(lock) {
            val userCallbacks = callbacks.getOrPut(user, { mutableMapOf() })
            wasEmpty = userCallbacks.isEmpty()

            userCallbacks.getOrPut(roleName, { mutableListOf() }).add(callback)
        }

        if (wasEmpty) {
            roleManager.addOnRoleHoldersChangedListenerAsUser(app.mainExecutor, this, user)
        }
    }

    fun removeCallback(roleName: String, user: UserHandle, callback: RoleHoldersChangeCallback) {
        val userCallbacksEmpty: Boolean
        synchronized(lock) {
            val userCallbacks = callbacks[user] ?: return
            if (!userCallbacks.contains(roleName)) {
                return
            }

            if (!userCallbacks[roleName]!!.remove(callback)) {
                return
            }

            if (userCallbacks[roleName]!!.isEmpty()) {
                userCallbacks.remove(roleName)
            }

            userCallbacksEmpty = userCallbacks.isEmpty()
        }
        if (userCallbacksEmpty) {
            roleManager.removeOnRoleHoldersChangedListenerAsUser(this, user)
        }
    }

    interface RoleHoldersChangeCallback {
        fun onRoleHoldersChanged()
    }
}
