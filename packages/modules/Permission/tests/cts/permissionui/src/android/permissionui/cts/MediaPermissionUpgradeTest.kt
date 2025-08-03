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

package android.permissionui.cts

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_AUDIO
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.os.Build
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.CddTest
import org.junit.Test

/** Tests media storage permission behavior upon app upgrade. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
@CddTest(requirement = "9.1/C-0-1")
@FlakyTest
class MediaPermissionUpgradeTest : BaseUsePermissionTest() {
    @Test
    fun testAfterUpgradeToTiramisuThenNoGrantDialogShownForMediaPerms() {
        // Install 32
        installPackage(APP_APK_PATH_32)

        // Request STORAGE, and click allow
        requestAppPermissionsAndAssertResult(
            READ_EXTERNAL_STORAGE to true,
            waitForWindowTransition = !isWatch
        ) {
            clickPermissionRequestAllowButton()
        }

        // Upgrade 32 -> 33
        installPackage(APP_APK_PATH_LATEST, reinstall = true)

        // Request READ_MEDIA_*
        requestAppPermissionsAndAssertResult(
            READ_MEDIA_AUDIO to true,
            READ_MEDIA_VIDEO to true,
            READ_MEDIA_IMAGES to true,
            waitForWindowTransition = false
        ) {
            // Don't click any grant dialog buttons because no grant dialog should appear
        }
    }
}
