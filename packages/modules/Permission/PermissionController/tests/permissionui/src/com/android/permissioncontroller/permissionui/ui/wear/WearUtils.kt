/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.permissionui.ui.wear

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import org.junit.Assert.assertNotNull

private const val TIMEOUT = 30_000L

fun isWear() =
    InstrumentationRegistry.getInstrumentation()
        .targetContext
        .packageManager
        .hasSystemFeature(PackageManager.FEATURE_WATCH)

fun clickObject(text: String) {
    eventually {
        val obj = waitFindObject(By.textContains(text), TIMEOUT)
        assertNotNull(obj)
        obj.click()
    }
}
