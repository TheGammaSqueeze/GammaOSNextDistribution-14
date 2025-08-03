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

package android.companion.cts.uiautomation

import android.Manifest
import android.annotation.CallSuper
import android.app.role.RoleManager
import android.companion.cts.common.AppHelper
import android.companion.cts.common.TestBase
import android.companion.cts.uicommon.ASSOCIATION_REVOKE_APP_APK_PATH
import android.companion.cts.uicommon.ASSOCIATION_REVOKE_APP_NAME
import android.companion.cts.uicommon.CompanionDeviceManagerUi
import android.content.Context
import androidx.test.uiautomator.UiDevice
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import org.junit.AfterClass
import org.junit.BeforeClass

open class AssociationRevokedTestBase : TestBase() {
    private val roleManager: RoleManager by lazy {
        context.getSystemService(RoleManager::class.java)!!
    }

    val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
    protected val confirmationUi = CompanionDeviceManagerUi(uiDevice)
    protected val appContext: Context = instrumentation.targetContext
    protected val associationApp = AppHelper(
        instrumentation, userId, ASSOCIATION_REVOKE_APP_NAME, ASSOCIATION_REVOKE_APP_APK_PATH
    )

    @CallSuper
    override fun setUp() {
        super.setUp()

        with(associationApp) {
            if (!isInstalled()) install()
            assertTrue("Test app $packageName is not installed") { isInstalled() }
        }

        uiDevice.waitForIdle()
    }

    @CallSuper
    override fun tearDown() {
        uiDevice.pressBack()
        super.tearDown()
    }

    // Launch the AssociationRevoked test app and the app will launch the CDM dialog
    // directly from the Activity.onCreate()
    protected fun launchAppAndConfirmationUi() {
        val intent = appContext.packageManager.getLaunchIntentForPackage(associationApp.packageName)
        appContext.startActivity(intent)
        confirmationUi.waitUntilVisible(10.seconds)
    }

    protected fun getRoleHolders(profile: String): List<String> {
        val roleHolders = withShellPermissionIdentity(Manifest.permission.MANAGE_ROLE_HOLDERS) {
            roleManager.getRoleHolders(profile)
        }
        return roleHolders
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun setupBeforeClass() {
            // Enable bluetooth if it was disabled.
            val uiAutomationTestBase = UiAutomationTestBase(null, null)
            uiAutomationTestBase.enableBluetoothIfNeeded()
        }

        @JvmStatic
        @AfterClass
        fun tearDownAfterClass() {
            // Disable bluetooth if it was disabled.
            val uiAutomationTestBase = UiAutomationTestBase(null, null)
            uiAutomationTestBase.disableBluetoothIfNeeded()
        }
    }
}
