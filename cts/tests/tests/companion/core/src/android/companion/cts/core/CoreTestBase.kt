package android.companion.cts.core

import android.annotation.CallSuper
import android.companion.CompanionDeviceManager
import android.companion.cts.common.AppHelper
import android.companion.cts.common.TestBase
import kotlin.test.assertTrue

open class CoreTestBase : TestBase() {
    protected val testApp = AppHelper(
            instrumentation, userId, TEST_APP_PACKAGE_NAME, TEST_APP_APK_PATH)

    @CallSuper
    override fun setUp() {
        super.setUp()

        // Make sure test app is installed.
        with(testApp) {
            if (!isInstalled()) install()
            assertTrue("Test app $packageName is not installed") { isInstalled() }
        }
    }

    protected val NO_OP_LISTENER: CompanionDeviceManager.OnAssociationsChangedListener =
        CompanionDeviceManager.OnAssociationsChangedListener { }

    protected val NO_OP_CALLBACK: CompanionDeviceManager.Callback =
        object : CompanionDeviceManager.Callback() {
            override fun onFailure(error: CharSequence?) = Unit
        }
}