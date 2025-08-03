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

package com.android.safetycenter.testing

import android.app.UiAutomation
import android.app.UiAutomation.ALL_PERMISSIONS
import androidx.annotation.GuardedBy
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.compatibility.common.util.SystemUtil
import com.google.common.collect.HashMultiset
import com.google.common.collect.Multiset

/** A class to facilitate working with System Shell permissions. */
object ShellPermissions {

    private val lock = Object()
    @GuardedBy("lock") private val nestedPermissions: Multiset<String> = HashMultiset.create()

    /**
     * Behaves the same way as [SystemUtil.callWithShellPermissionIdentity], but allows nesting
     * calls to it by merging the adopted [permissions] within each [block].
     *
     * Note that [SystemUtil.callWithShellPermissionIdentity] should NOT be used together with this
     * method.
     */
    fun <T> callWithShellPermissionIdentity(vararg permissions: String, block: () -> T): T {
        val uiAutomation = getInstrumentation().getUiAutomation()
        val permissionsToAddForThisBlock =
            if (permissions.isEmpty()) {
                ALL_PERMISSIONS
            } else {
                permissions.toSet()
            }
        synchronized(lock) {
            permissionsToAddForThisBlock.forEach { nestedPermissions.add(it) }
            uiAutomation.adoptShellPermissionIdentityFor(nestedPermissions.elementSet())
        }
        try {
            return block()
        } finally {
            synchronized(lock) {
                permissionsToAddForThisBlock.forEach { nestedPermissions.remove(it) }
                if (nestedPermissions.isEmpty()) {
                    uiAutomation.dropShellPermissionIdentity()
                } else {
                    uiAutomation.adoptShellPermissionIdentityFor(nestedPermissions.elementSet())
                }
            }
        }
    }

    private fun UiAutomation.adoptShellPermissionIdentityFor(permissionsToAdopt: Set<String>) {
        if (permissionsToAdopt.containsAll(ALL_PERMISSIONS)) {
            adoptShellPermissionIdentity()
        } else {
            adoptShellPermissionIdentity(*permissionsToAdopt.toTypedArray())
        }
    }
}
