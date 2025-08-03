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

package android.permissionui.cts

import android.content.Intent
import androidx.test.filters.FlakyTest
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

/** Tests permission usage info action. */
@FlakyTest
class PermissionUsageInfoTest : BaseUsePermissionTest() {
    @Before
    fun assumeHandheld() {
        assumeFalse(isAutomotive)
        assumeFalse(isTv)
        assumeFalse(isWatch)
    }

    @Before
    fun installApp() {
        installPackage(APP_APK_PATH_LATEST)
    }

    @Test
    fun testPermissionUsageInfo() {
        doAndWaitForWindowTransition {
            runWithShellPermissionIdentity {
                context.startActivity(
                    Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS).apply {
                        putExtra(Intent.EXTRA_PACKAGE_NAME, APP_PACKAGE_NAME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
        click(By.res("com.android.permissioncontroller:id/icon"))
    }
}
