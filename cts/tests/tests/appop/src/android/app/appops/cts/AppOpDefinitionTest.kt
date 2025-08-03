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

package android.app.appops.cts

import android.app.AppOpsManager
import android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
import android.content.pm.PermissionInfo.PROTECTION_FLAG_APPOP
import android.platform.test.annotations.AppModeFull
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test

@AppModeFull(reason = "Need to get system permission info")
class AppOpDefinitionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    companion object {
        private const val RUNTIME = "runtime"
        private const val APPOP = "appop"
    }

    @Ignore
    @Test
    fun ensureRuntimeAppOpMappingIsCorrect() {
        val missingPerms = mutableListOf<Triple<String, String, String>>()
        val opStrs = AppOpsManager.getOpStrs()
        for (opCode in 0 until AppOpsManager.getNumOps()) {
            val opStr = opStrs[opCode]
            val permission = AppOpsManager.opToPermission(opCode) ?: continue
            val permissionInfo = context.packageManager.getPermissionInfo(permission, 0)
            val isAppOp = (permissionInfo.protectionLevel and PROTECTION_FLAG_APPOP) != 0
            val isRuntime = permissionInfo.protection == PROTECTION_DANGEROUS
            if ((!isRuntime && !isAppOp) || AppOpsManager.permissionToOp(permission) != null) {
                continue
            }

            val permType = if (isRuntime) RUNTIME else APPOP
            missingPerms.add(Triple(opStr, permission, permType))
        }

        if (missingPerms.isEmpty()) {
            return
        }

        val message = StringBuilder()
        for ((opStr, perm, permType) in missingPerms) {
            message.append("$opStr missing mapping to $permType permission $perm \n")
        }
        fail(message.toString())
    }
}
