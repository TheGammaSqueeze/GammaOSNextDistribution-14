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

import android.app.Instrumentation
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.DisableAnimationRule
import org.junit.Rule

abstract class BasePermissionUiTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val uiDevice = UiDevice.getInstance(instrumentation)!!
    protected val instrumentationContext = instrumentation.context!!
    protected val targetContext = instrumentation.targetContext!!
    private val packageManager = instrumentationContext.packageManager!!
    protected val isTelevision =
        packageManager.run {
            hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)
        }

    @Rule fun disableAnimationsRule() = DisableAnimationRule()
}
