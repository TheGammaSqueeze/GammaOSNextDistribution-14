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

import android.permission.cts.PermissionUtils.grantPermission
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.uninstallApp

// Test Apps' APK files
private const val APK_DIRECTORY = "/data/local/tmp/pc-permissionui/"
private const val LOCATION_PERM_USER_APK = "$APK_DIRECTORY/AppThatRequestsLocation.apk"
private const val CAMERA_PERM_USER_APK = "$APK_DIRECTORY/PermissionUiUseCameraPermissionApp.apk"
private const val HEALTH_CONNECT_PERMISSION_USER_APK =
    "$APK_DIRECTORY/PermissionUiUseHealthConnectPermissionApp.apk"
private const val INVALID_HEALTH_CONNECT_PERMISSION_USER_APK =
    "$APK_DIRECTORY/PermissionUiInvalidUseHealthConnectPermissionApp.apk"
private const val ADDITIONAL_PERM_USER_APK =
    "$APK_DIRECTORY/PermissionUiUseAdditionalPermissionApp.apk"
private const val TWO_ADDITIONAL_PERM_USER_APK =
    "$APK_DIRECTORY/PermissionUiUseTwoAdditionalPermissionsApp.apk"
private const val ADDITIONAL_PERM_DEFINER_APK =
    "$APK_DIRECTORY/PermissionUiDefineAdditionalPermissionApp.apk"

// All 4 of the AppThatUses_X_Permission(s) applications share the same package name.
private const val PERM_DEFINER_PACKAGE =
    "com.android.permissioncontroller.tests.appthatdefinespermission"
const val PERM_USER_PACKAGE = "com.android.permissioncontroller.tests.appthatrequestpermission"

const val CAMERA_TEST_APP_LABEL = "CameraRequestApp"

const val TEST_APP_DEFINED_PERMISSION_A = "com.android.permissioncontroller.tests.A"
const val TEST_APP_DEFINED_PERMISSION_B = "com.android.permissioncontroller.tests.B"
const val TEST_APP_DEFINED_PERMISSION_C = "com.android.permissioncontroller.tests.C"

const val TEST_APP_DEFINED_PERMISSION_A_LABEL = "Permission A"
const val TEST_APP_DEFINED_PERMISSION_B_LABEL = "Permission B"
const val TEST_APP_DEFINED_PERMISSION_C_LABEL = "Permission C"

fun installTestAppThatRequestsLocation() = install(LOCATION_PERM_USER_APK)

fun installTestAppThatUsesCameraPermission() = install(CAMERA_PERM_USER_APK)

fun installTestAppThatUsesHealthConnectPermission() = install(HEALTH_CONNECT_PERMISSION_USER_APK)

fun installInvalidTestAppThatUsesHealthConnectPermission() =
    install(INVALID_HEALTH_CONNECT_PERMISSION_USER_APK)

fun installTestAppThatUsesAdditionalPermission() = install(ADDITIONAL_PERM_USER_APK)

fun installTestAppThatUsesTwoAdditionalPermissions() = install(TWO_ADDITIONAL_PERM_USER_APK)

fun installTestAppThatDefinesAdditionalPermissions() = install(ADDITIONAL_PERM_DEFINER_APK)

fun uninstallTestApps() {
    uninstallApp(PERM_USER_PACKAGE)
    uninstallApp(PERM_DEFINER_PACKAGE)
}

fun grantTestAppPermission(permission: String) = grantPermission(PERM_USER_PACKAGE, permission)
