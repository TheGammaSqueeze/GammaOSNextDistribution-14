/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package android.platform.uiautomator_helpers

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Adopt shell permissions for the target context.
 *
 * @param[permissions] the permission to adopt. Adopt all available permission is it's empty.
 */
class ShellPrivilege(vararg permissions: String) : AutoCloseable {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val uiAutomation = instrumentation.uiAutomation
    private var permissionsGranted = false

    init {
        permissionsGranted = grantMissingPermissions(*permissions)
    }

    /**
     * @return[Boolean] True is there are any missing permission and we've successfully granted all
     * of them.
     */
    private fun grantMissingPermissions(vararg permissions: String): Boolean {
        if (permissions.isEmpty()) {
            uiAutomation.adoptShellPermissionIdentity()
            return true
        }
        val missingPermissions = permissions.filter { !it.isGranted() }.toTypedArray()
        if (missingPermissions.isEmpty()) return false
        uiAutomation.adoptShellPermissionIdentity(*missingPermissions)
        return true
    }

    override fun close() {
        if (permissionsGranted) instrumentation.uiAutomation.dropShellPermissionIdentity()
        permissionsGranted = false
    }

    private fun String.isGranted(): Boolean =
        targetContext.checkCallingPermission(this) == PackageManager.PERMISSION_GRANTED
}
