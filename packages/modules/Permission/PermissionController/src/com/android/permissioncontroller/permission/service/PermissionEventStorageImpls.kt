/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.permission.service

import android.annotation.SuppressLint
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.PermissionEvent
import com.android.permissioncontroller.permission.service.v33.PermissionDecisionStorageImpl

/** Singleton of all supported [PermissionEventStorage] on the device. */
class PermissionEventStorageImpls {
    companion object {
        @Volatile private var INSTANCE: List<PermissionEventStorage<out PermissionEvent>>? = null

        fun getInstance(): List<PermissionEventStorage<out PermissionEvent>> =
            INSTANCE ?: synchronized(this) { INSTANCE ?: createInstance().also { INSTANCE = it } }

        @SuppressLint("NewApi")
        private fun createInstance(): List<PermissionEventStorage<out PermissionEvent>> {
            val list = mutableListOf<PermissionEventStorage<out PermissionEvent>>()
            list.add(PermissionChangeStorageImpl.getInstance())
            val context = PermissionControllerApplication.get().applicationContext
            if (PermissionDecisionStorageImpl.isRecordPermissionsSupported(context)) {
                list.add(PermissionDecisionStorageImpl.getInstance())
            }
            return list
        }
    }
}
