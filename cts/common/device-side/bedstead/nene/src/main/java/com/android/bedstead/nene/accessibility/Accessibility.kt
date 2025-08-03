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
package com.android.bedstead.nene.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.annotations.Experimental

/** Test APIs related to accessibility.  */
@Experimental
object Accessibility {

    private val accessibilityManager =
            TestApis.context().instrumentedContext()
                    .getSystemService(AccessibilityManager::class.java)!!

    /**
     * Get installed accessibility services.
     *
     *
     * See [AccessibilityManager.getInstalledAccessibilityServiceList].
     */
    fun installedAccessibilityServices(): Set<AccessibilityService> =
        accessibilityManager
                .installedAccessibilityServiceList.asSequence()
                .map { serviceInfo -> AccessibilityService(serviceInfo) }
                .toSet()

    /**
     * Get enabled accessibility services.
     *
     *
     * See [AccessibilityManager.getEnabledAccessibilityServiceList].
     */
    fun enabledAccessibilityServices(): Set<AccessibilityService> =
            accessibilityManager
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .asSequence()
                    .map { serviceInfo -> AccessibilityService(serviceInfo) }
                    .toSet()
}
