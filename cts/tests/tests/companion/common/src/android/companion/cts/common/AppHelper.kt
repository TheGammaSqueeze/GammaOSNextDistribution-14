/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.companion.cts.common

import android.app.Instrumentation
import android.net.MacAddress

/** Utility class for interacting with applications via Shell */
class AppHelper(
    private val instrumentation: Instrumentation,
    val userId: Int,
    val packageName: String,
    private val apkPath: String? = null
) {
    fun associate(macAddress: MacAddress, role: String = "") =
            runShellCommand("cmd companiondevice associate $userId $packageName $macAddress $role")

    fun disassociate(macAddress: MacAddress) =
            runShellCommand("cmd companiondevice disassociate $userId $packageName $macAddress")

    fun disassociateAll() =
            runShellCommand("cmd companiondevice disassociate-all $userId $packageName")

    fun isInstalled(): Boolean =
            runShellCommand("pm list packages --user $userId $packageName").isNotBlank()

    fun install() = apkPath?.let { runShellCommand("pm install --user $userId $apkPath") }
            ?: throw UnsupportedOperationException("APK path is not provided.")

    fun uninstall() = runShellCommand("pm uninstall --user $userId $packageName")

    fun clearData() = runShellCommand("pm clear --user $userId $packageName")

    fun isRoleHolder(role: String) =
            runShellCommand("cmd role get-role-holders --user $userId $role").contains(packageName)

    fun addToHoldersOfRole(role: String) =
            runShellCommand("cmd role add-role-holder --user $userId $role $packageName")

    fun removeFromHoldersOfRole(role: String) =
            runShellCommand("cmd role remove-role-holder --user $userId $role $packageName")

    fun withRole(role: String, block: () -> Unit) {
        addToHoldersOfRole(role)
        try {
            block()
        } finally {
            removeFromHoldersOfRole(role)
        }
    }

    private fun runShellCommand(cmd: String) = instrumentation.runShellCommand(cmd)
}
