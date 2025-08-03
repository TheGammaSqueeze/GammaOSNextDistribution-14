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

package com.android.permissioncontroller.permissionui.ui

import android.permission.cts.PermissionUtils.uninstallApp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObjectOrNull
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

private const val PERMISSION_APPS_DESCRIPTION = "Apps with this permission"

/**
 * Simple tests for {@link PermissionAppsFragment} when showing custom permission
 *
 * Currently, does NOT run on TV (same as the other tests that extend [PermissionAppsFragmentTest]).
 *
 * TODO(b/178576541): Adapt and run on TV.
 */
@RunWith(AndroidJUnit4::class)
class CustomPermissionAppsFragmentTest :
    PermissionAppsFragmentTest(
        "/data/local/tmp/pc-permissionui" + "/PermissionUiUseAdditionalPermissionApp.apk",
        "com.android.permissioncontroller.tests.appthatrequestpermission",
        "com.android.permissioncontroller.tests.A",
        "/data/local/tmp/pc-permissionui" + "/PermissionUiDefineAdditionalPermissionApp.apk",
        "com.android.permissioncontroller.tests.appthatdefinespermission"
    ) {
    @Ignore("b/155112992")
    @Test
    fun fragmentIsClosedWhenPermissionIsRemoved() {
        uninstallApp(definerApk!!)
        eventually { assertNull(waitFindObjectOrNull(By.text(PERMISSION_APPS_DESCRIPTION))) }
    }
}
