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

package com.android.permissioncontroller.permissionui.ui

import android.content.Intent
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.uninstallApp
import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.permissioncontroller.permissionui.wakeUpScreen
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

/**
 * Superclass of all tests for {@link PermissionAppsFragmentTest}.
 *
 * <p>Leave abstract to prevent the test runner from trying to run it
 *
 * Currently, none of the tests that extend [PermissionAppsFragmentTest] run on TV.
 *
 * TODO(b/178576541): Adapt and run on TV.
 */
abstract class PermissionAppsFragmentTest(
    val userApk: String,
    val userPkg: String,
    val perm: String,
    val definerApk: String? = null,
    val definerPkg: String? = null
) : BasePermissionUiTest() {
    val pkgSelector = By.text(userPkg)
    var startTimeMillis: Long = 0

    private fun scrollFindFromTop(selector: BySelector): UiObject2? {
        val scrollable = uiDevice.findObject(By.scrollable(true))
        if (scrollable != null) {
            logCheckPoint("found scrollable, so proceeding to scroll")
            logCheckPoint("starting scroll up")
            scrollable.scrollUntil(Direction.UP, Until.scrollFinished(Direction.UP))
            logCheckPoint("finished scroll up")
            logCheckPoint("starting scroll down")
            var uiObject = scrollable.scrollUntil(Direction.DOWN, Until.findObject(selector))
            logCheckPoint("finished scroll down")
            return uiObject
        }
        logCheckPoint("no scrollable on screen, so finding object directly")
        return uiDevice.findObject(selector)
    }

    @Before
    fun setUp() {
        startTimeMillis = System.currentTimeMillis()
        logCheckPoint("setUp: started")
        assumeFalse(isTelevision)
        wakeUpScreen()
        if (definerApk != null) {
            install(definerApk)
        }
        uninstallApp(userPkg)
        uiDevice.performActionAndWait(
            {
                runWithShellPermissionIdentity {
                    instrumentationContext.startActivity(
                        Intent(Intent.ACTION_MANAGE_PERMISSION_APPS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            putExtra(Intent.EXTRA_PERMISSION_NAME, perm)
                        }
                    )
                }
            },
            Until.newWindow(),
            NEW_WINDOW_TIMEOUT_MILLIS
        )
        logCheckPoint("setUp: finished")
    }

    @Test
    fun testAppAppearanceReflectsInstallation() {
        logCheckPoint("testAppAppearanceReflectsInstallation: started")

        // Install package
        install(userApk)
        logCheckPoint("installed app")

        // Expect *to* find package listed on screen
        eventually(
            {
                val pkg = scrollFindFromTop(pkgSelector)
                assertWithMessage(
                        "Package '$userPkg' was NOT visible after installing, but should be"
                    )
                    .that(pkg)
                    .isNotNull()
            },
            Companion.SCROLL_TIMEOUT_MILLIS
        )

        // Uninstall app
        uninstallApp(userPkg)
        logCheckPoint("uninstalled app")

        // Expect *not to* find package listed on screen
        eventually(
            {
                val pkg = scrollFindFromTop(pkgSelector)
                assertWithMessage(
                        "Package '$userPkg' was visible after uninstalling, but should NOT be"
                    )
                    .that(pkg)
                    .isNull()
            },
            Companion.SCROLL_TIMEOUT_MILLIS
        )

        logCheckPoint("confirmed installed app shown on screen")
        logCheckPoint("testAppAppearanceReflectsInstallation: finished")
    }

    @After
    fun tearDown() {
        logCheckPoint("tearDown: started")
        if (definerPkg != null) {
            uninstallApp(definerPkg)
        }
        uninstallApp(userPkg)

        uiDevice.pressBack()
        uiDevice.pressHome()
        logCheckPoint("tearDown: finished")
    }

    fun logCheckPoint(logMessage: String) {
        val elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000
        Log.v(TAG, "(${elapsedSeconds}s): $logMessage")
    }

    companion object {
        const val NEW_WINDOW_TIMEOUT_MILLIS = 25_000L
        const val SCROLL_TIMEOUT_MILLIS = 25_000L
        val TAG = PermissionAppsFragmentTest::class.java.simpleName
    }
}
