//
// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package android.platform.helpers.notesrole

import android.Manifest.permission.INTERACT_ACROSS_USERS
import android.Manifest.permission.MANAGE_ROLE_HOLDERS
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.os.UserHandle
import android.platform.uiautomator_helpers.DeviceHelpers
import androidx.core.content.getSystemService
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.AssumptionViolatedException

/** A helper class to manage [RoleManager.ROLE_NOTES] in end to end tests. */
class NotesRoleUtil(private val context: Context) {

    private val roleManager = context.getSystemService<RoleManager>()!!

    /**
     * Checks if the following assumptions are true:
     * - Android version is at least the supplied required android version. By default U+.
     * - Notes role is available.
     * - The required package is installed.
     *
     * @throws [AssumptionViolatedException] when any of the above assumptions are not met to help
     *   skip a test
     */
    fun checkAndroidRolePackageAssumptions(
        requiredAndroidVersion: Int = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        requiredPackage: String,
    ) {
        assumeTrue(
            "Build SDK should be at least $requiredAndroidVersion",
            Build.VERSION.SDK_INT >= requiredAndroidVersion
        )
        assumeTrue(
            "Notes role should be enabled",
            roleManager.isRoleAvailable(RoleManager.ROLE_NOTES)
        )
        assumeTrue("$requiredPackage should be installed", isPackageInstalled(requiredPackage))
    }

    /** Returns the Notes role holder package name. */
    fun getRoleHolderPackageName(userHandle: UserHandle = context.user): String =
        callWithManageRoleHolderPermission {
            roleManager
                .getRoleHoldersAsUser(RoleManager.ROLE_NOTES, userHandle)
                .firstOrNull()
                .orEmpty()
        }

    /** Force stops the supplied package */
    fun forceStopPackage(packageName: String) {
        DeviceHelpers.shell("am force-stop $packageName")
    }

    /**
     * Sets the Notes role holder to "None" by clearing existing role holders. This is possible
     * because Notes role is an exclusive role.
     */
    fun clearRoleHolder(userHandle: UserHandle = context.user) {
        val clearRoleHoldersFuture = CompletableFuture<Boolean>()
        callWithManageRoleHolderPermission {
            roleManager.clearRoleHoldersAsUser(
                RoleManager.ROLE_NOTES,
                /* flags= */ 0,
                userHandle,
                context.mainExecutor,
                clearRoleHoldersFuture::complete
            )
        }

        // Synchronously wait for the role to update.
        assert(
            clearRoleHoldersFuture.get(
                ROLE_MANAGER_VERIFICATION_TIMEOUT_IN_SECONDS,
                TimeUnit.SECONDS
            )
        ) {
            "Failed to clear notes role holder"
        }
    }

    /** Sets the supplied package as the Notes role holder app. */
    fun setRoleHolder(packageName: String, userHandle: UserHandle = context.user) {
        val currentRoleHolderPackageName = getRoleHolderPackageName()

        // Return early if current role holder package is the same as supplied package name.
        if (currentRoleHolderPackageName == packageName) {
            return
        }

        // Notes role is an exclusive role, so clear other role holders before adding the supplied
        // package to the role. RoleManager has an "addRoleHolder" but no setRoleHolder API so we
        // have to clear the current role holder before adding the supplied package as role holder.
        clearRoleHolder(userHandle)

        // If the supplied package name is empty it indicates that we want to select "None" as the
        // new Notes role holder, so return early in this case.
        if (packageName.isEmpty()) {
            return
        }

        // Add the supplied package name to the Notes role.
        val addRoleHolderFuture = CompletableFuture<Boolean>()
        callWithManageRoleHolderPermission {
            roleManager.addRoleHolderAsUser(
                RoleManager.ROLE_NOTES,
                packageName,
                /* flags= */ 0,
                userHandle,
                context.mainExecutor,
                addRoleHolderFuture::complete
            )
        }

        // Synchronously wait for the role to update.
        assert(
            addRoleHolderFuture.get(ROLE_MANAGER_VERIFICATION_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
        ) {
            "Failed to set $packageName as default notes role holder"
        }
    }

    /** Calls the supplied callable with the provided permissions using shell identity. */
    fun <T> callWithShellIdentityPermissions(vararg permissions: String, callable: () -> T): T {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

        try {
            uiAutomation.adoptShellPermissionIdentity(*permissions)
            return callable()
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun isPackageInstalled(packageName: String) =
        runCatching { context.packageManager.getPackageInfo(packageName, /* flags= */ 0) }.isSuccess

    private fun <T> callWithManageRoleHolderPermission(callable: () -> T): T {
        return callWithShellIdentityPermissions(MANAGE_ROLE_HOLDERS, INTERACT_ACROSS_USERS) {
            callable()
        }
    }

    private companion object {
        const val ROLE_MANAGER_VERIFICATION_TIMEOUT_IN_SECONDS = 10L
    }
}
