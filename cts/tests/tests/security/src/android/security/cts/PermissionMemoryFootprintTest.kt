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

package android.security.cts

import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.platform.test.annotations.AsbSecurityTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.sts.common.util.StsExtraBusinessLogicTestCase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionMemoryFootprintTest : StsExtraBusinessLogicTestCase() {
    companion object {
        const val MAX_NUM_PERMISSIONS = 32000
        const val PKG_TREE_NAME = "com.android.cts"
        val LONG_DESCRIPTION = " ".repeat(MAX_NUM_PERMISSIONS / 10)
        val SHORT_DESCRIPTION = " ".repeat(MAX_NUM_PERMISSIONS / 100)

        val permInfo = PermissionInfo().apply {
            labelRes = 1
            protectionLevel = PermissionInfo.PROTECTION_NORMAL
        }
    }

    val packageManager: PackageManager = InstrumentationRegistry.getInstrumentation()
                    .getTargetContext().packageManager!!

    @Throws(SecurityException::class)
    private fun createOrRemovePermissions(
        largePerm: Boolean = true,
        add: Boolean = true,
        numPerms: Int = MAX_NUM_PERMISSIONS,
    ): Int {
        var numPermsCreated = 0
        for (i in 1..numPerms) {
            try {
                permInfo.name = "$PKG_TREE_NAME.$i"
                permInfo.nonLocalizedDescription = if (largePerm) {
                    LONG_DESCRIPTION
                } else {
                    SHORT_DESCRIPTION
                }

                if (add) {
                    packageManager.addPermission(permInfo)
                } else {
                    packageManager.removePermission(permInfo.name)
                }
            } catch (e: SecurityException) {
                break
            }
            numPermsCreated = i
        }
        return numPermsCreated
    }

    @Test
    @AsbSecurityTest(cveBugId = [242537498])
    fun checkAppsCreatingPermissionsAreCapped() {
        var numCreated = 0
        try {
            numCreated = createOrRemovePermissions()
            Assert.assertNotEquals("Expected at least one permission", numCreated, 0)
            Assert.assertNotEquals(numCreated, MAX_NUM_PERMISSIONS)
        } finally {
            createOrRemovePermissions(add = false, numPerms = numCreated)
        }
    }

    @Test
    @AsbSecurityTest(cveBugId = [242537498])
    fun checkAppsCantIncreasePermissionSizeAfterCreating() {
        var numCreatedShort = 0
        try {
            numCreatedShort = createOrRemovePermissions(largePerm = false)
            Assert.assertNotEquals("Expected at least one permission", numCreatedShort, 0)
            val numCreatedLong = createOrRemovePermissions(numPerms = 1)
            Assert.assertEquals("Expected to not be able to create a large permission",
                0, numCreatedLong)
        } finally {
            createOrRemovePermissions(add = false, numPerms = numCreatedShort)
        }
    }
}
