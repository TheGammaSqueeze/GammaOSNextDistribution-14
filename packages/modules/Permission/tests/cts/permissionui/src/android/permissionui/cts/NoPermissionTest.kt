/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.app.Activity
import androidx.test.filters.FlakyTest
import androidx.test.runner.AndroidJUnit4
import com.android.modules.utils.build.SdkLevel
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@FlakyTest
class NoPermissionTest : BaseUsePermissionTest() {
    @Test
    fun testStartActivity22() {
        Assume.assumeFalse(SdkLevel.isAtLeastT())
        installPackage(APP_APK_PATH_22_NONE)

        startAppActivityAndAssertResultCode(Activity.RESULT_OK) {}

        clearTargetSdkWarning()
    }

    @Test
    fun testStartActivityLatest() {
        installPackage(APP_APK_PATH_LATEST_NONE)

        startAppActivityAndAssertResultCode(Activity.RESULT_OK) {}
    }
}
