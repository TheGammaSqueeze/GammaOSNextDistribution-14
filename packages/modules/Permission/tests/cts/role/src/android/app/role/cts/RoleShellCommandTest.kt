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

package android.app.role.cts

import android.app.role.RoleManager
import android.os.Build
import android.os.UserHandle
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests role shell commands. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S, codeName = "S")
class RoleShellCommandTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.context
    private val roleManager = context.getSystemService(RoleManager::class.java)!!
    private val userId = UserHandle.myUserId()

    private var roleHolder: String? = null
    private var wasBypassingRoleQualification: Boolean = false

    @Before
    fun saveRoleHolder() {
        roleHolder = getRoleHolders().firstOrNull()
        if (roleHolder == APP_PACKAGE_NAME) {
            removeRoleHolder()
            roleHolder = null
        }
    }

    @Before
    fun saveBypassingRoleQualification() {
        wasBypassingRoleQualification = isBypassingRoleQualification()
    }

    @After
    fun restoreRoleHolder() {
        removeRoleHolder()
        roleHolder?.let { addRoleHolder(it) }
        assertIsRoleHolder(false)
    }

    @After
    fun restoreBypassingRoleQualification() {
        setBypassingRoleQualification(wasBypassingRoleQualification)
    }

    @Before
    fun installApp() {
        installPackage(APP_APK_PATH)
    }

    @After
    fun uninstallApp() {
        uninstallPackage(APP_PACKAGE_NAME)
    }

    @Test
    fun helpPrintsNonEmpty() {
        assertThat(runShellCommandOrThrow("cmd role help")).isNotEmpty()
    }

    @Test
    fun dontAddRoleHolderThenIsNotRoleHolder() {
        assertIsRoleHolder(false)
    }

    @Test
    fun addRoleHolderThenIsRoleHolder() {
        addRoleHolder()

        assertIsRoleHolder(true)
    }

    @Test
    fun addAndRemoveRoleHolderThenIsNotRoleHolder() {
        addRoleHolder()
        removeRoleHolder()

        assertIsRoleHolder(false)
    }

    @Test
    fun addAndClearRoleHolderThenIsNotRoleHolder() {
        addRoleHolder()
        clearRoleHolders()

        assertIsRoleHolder(false)
    }

    @Test
    fun addInvalidRoleHolderThenFails() {
        assertThrows(AssertionError::class.java) {
            runShellCommandOrThrow("cmd role add-role-holder --user $userId $ROLE_NAME invalid")
        }
    }

    @Test
    fun addRoleHolderThenAppearsInDumpsys() {
        addRoleHolder()

        assertThat(runShellCommandOrThrow("dumpsys role")).contains(APP_PACKAGE_NAME)
    }

    @Test
    fun setBypassingRoleQualificationToTrueThenSetsToTrue() {
        setBypassingRoleQualification(false)

        runShellCommandOrThrow("cmd role set-bypassing-role-qualification true")

        assertThat(isBypassingRoleQualification()).isTrue()
    }

    @Test
    fun setBypassingRoleQualificationToFalseThenSetsToFalse() {
        setBypassingRoleQualification(true)

        runShellCommandOrThrow("cmd role set-bypassing-role-qualification false")

        assertThat(isBypassingRoleQualification()).isFalse()
    }

    private fun addRoleHolder(packageName: String = APP_PACKAGE_NAME) {
        runShellCommandOrThrow("cmd role add-role-holder --user $userId $ROLE_NAME $packageName")
    }

    private fun removeRoleHolder(packageName: String = APP_PACKAGE_NAME) {
        runShellCommandOrThrow("cmd role remove-role-holder --user $userId $ROLE_NAME $packageName")
    }

    private fun clearRoleHolders() {
        runShellCommandOrThrow("cmd role clear-role-holders --user $userId $ROLE_NAME")
    }

    private fun getRoleHolders(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runShellCommandOrThrow("cmd role get-role-holders --user $userId $ROLE_NAME")
                .trim()
                .let { if (it.isNotEmpty()) it.split(";") else emptyList() }
        } else {
            callWithShellPermissionIdentity { roleManager.getRoleHolders(ROLE_NAME) }
        }

    private fun assertIsRoleHolder(shouldBeRoleHolder: Boolean) {
        val packageNames = getRoleHolders()
        if (shouldBeRoleHolder) {
            assertThat(packageNames).contains(APP_PACKAGE_NAME)
        } else {
            assertThat(packageNames).doesNotContain(APP_PACKAGE_NAME)
        }
    }

    private fun installPackage(apkPath: String) {
        assertThat(runShellCommandOrThrow("pm install -r --user $userId $apkPath").trim())
            .isEqualTo("Success")
    }

    private fun uninstallPackage(packageName: String) {
        assertThat(runShellCommandOrThrow("pm uninstall --user $userId $packageName").trim())
            .isEqualTo("Success")
    }

    private fun isBypassingRoleQualification(): Boolean = callWithShellPermissionIdentity {
        roleManager.isBypassingRoleQualification()
    }

    private fun setBypassingRoleQualification(value: Boolean) {
        callWithShellPermissionIdentity { roleManager.setBypassingRoleQualification(value) }
    }

    companion object {
        private const val ROLE_NAME = RoleManager.ROLE_BROWSER
        private const val APP_APK_PATH = "/data/local/tmp/cts-role/CtsRoleTestApp.apk"
        private const val APP_PACKAGE_NAME = "android.app.role.cts.app"
    }
}
