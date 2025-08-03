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

package com.android.permissioncontroller.permissionui.ui.television

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.permissioncontroller.permissionui.ui.TEST_APP_DEFINED_PERMISSION_A
import com.android.permissioncontroller.permissionui.ui.TEST_APP_DEFINED_PERMISSION_A_LABEL
import com.android.permissioncontroller.permissionui.ui.grantTestAppPermission
import com.android.permissioncontroller.permissionui.ui.installTestAppThatDefinesAdditionalPermissions
import com.android.permissioncontroller.permissionui.ui.installTestAppThatUsesAdditionalPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Run with: atest ManagePermissionsOtherFragmentTest */
@RunWith(AndroidJUnit4::class)
class ManagePermissionsOtherFragmentTest : TelevisionUiBaseTest() {

    @Before
    fun goToOtherPermissionScreen() {
        launchPermissionController()
        if (!uiDevice.focusOnElementWithTitle(otherPermissionsLabel))
            error("\"Other permissions\" button is not found")
        uiDevice.pressDPadCenter()
        uiDevice.waitForIdle()
        // Making sure are on the "Other permission" page now.
        // For this we can check the Fragment's title.
        assertEquals(
            "\"Other permissions\" didn't open",
            otherPermissionsLabel,
            uiDevice.fragmentDecorTitle
        )
    }

    @Test
    fun bodySensors_permissionGroup_isNotShown() {
        assertFalse(
            "Found \"Body sensors\" permission",
            uiDevice.focusOnElementWithTitle(bodySensorsPermissionLabel)
        )
    }

    @Test
    fun additionalPermissions_section_isNotShown_ifAllUnused() {
        assertFalse(
            "\"Additional permissions\" section is shown",
            uiDevice.hasElementWithTitle(additionalPermissionsLabel)
        )
    }

    @Test
    fun additionalPermissions_section_isShown_ifUsed() {
        // Install test application that defines additional dangerous permission A and normal
        // permissions B and C.
        installTestAppThatDefinesAdditionalPermissions()
        // Install test application that uses additional permissions A and B ...
        installTestAppThatUsesAdditionalPermission()
        // ... and grant the app the A permission.
        grantTestAppPermission(TEST_APP_DEFINED_PERMISSION_A)

        // Make sure the "Additional permissions" section is now shown...
        assertTrue(
            "\"Additional permissions\" section should be shown",
            uiDevice.hasElementWithTitle(additionalPermissionsLabel)
        )
        // ... and that we now have "Permission A" row.
        assertTrue(
            "Could not find \"Permission A\" row",
            uiDevice.focusOnElementWithTitle(TEST_APP_DEFINED_PERMISSION_A_LABEL)
        )
    }
}
