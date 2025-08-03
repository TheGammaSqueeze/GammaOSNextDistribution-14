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

import android.Manifest.permission.CAMERA
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.permissioncontroller.permissionui.ui.grantTestAppPermission
import com.android.permissioncontroller.permissionui.ui.installTestAppThatUsesCameraPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Run with: atest ManagePermissionsFragmentTest */
@RunWith(AndroidJUnit4::class)
class ManagePermissionsFragmentTest : TelevisionUiBaseTest() {
    @Before fun launch() = launchPermissionController()

    @Test
    fun test_bodySensors_permissionGroup_isNotShown() {
        assertFalse(
            "Found \"Body sensors\" permission",
            uiDevice.hasElementWithTitle(bodySensorsPermissionLabel)
        )
    }

    @Test
    fun test_camera_permissionGroup_isShown_whenUsed() {
        // Make sure Camera permission group is not shown at first.
        assertFalse(
            "Found \"Camera\" permission",
            uiDevice.hasElementWithTitle(cameraPermissionLabel)
        )

        // Install app that uses Camera permission...
        installTestAppThatUsesCameraPermission()
        // ... grant the permission ...
        grantTestAppPermission(CAMERA)
        // ... make sure now the Camera permission is shown.
        assertTrue(
            "Could not find \"Camera\" permission",
            uiDevice.focusOnElementWithTitle(cameraPermissionLabel)
        )
    }

    @Test
    fun test_otherPermissions_Button_isShown() {
        uiDevice.navigateToTheBottom()
        assertEquals(
            "The last item should be the \"Other permissions\" button",
            otherPermissionsLabel,
            uiDevice.focusedElementTitle
        )
        assertTrue(
            "\"Other permissions\" button should be clickable",
            uiDevice.focusedElement.isClickable
        )
    }
}
