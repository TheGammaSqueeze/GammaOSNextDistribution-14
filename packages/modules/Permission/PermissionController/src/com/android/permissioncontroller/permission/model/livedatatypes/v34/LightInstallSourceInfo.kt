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

package com.android.permissioncontroller.permission.model.livedatatypes.v34

import android.content.pm.PackageInstaller.PACKAGE_SOURCE_STORE
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED

/**
 * A lighter version of the system's InstallSourceInfo class, containing select information about
 * the install source.
 */
class LightInstallSourceInfo {
    private constructor() {
        initiatingPackageName = null
        isPreloadedApp = false
        supportsSafetyLabel = false
    }

    constructor(packageSource: Int, initiatingPackage: String?) {
        initiatingPackageName = initiatingPackage
        // Stores should be setting PACKAGE_SOURCE_STORE, but it's not enforced. So include the
        // default source of unspecified. All other sources should be explicitly set to another
        // PACKAGE_SOURCE_ value
        val isStoreInstalled =
            initiatingPackageName != null &&
                (packageSource == PACKAGE_SOURCE_STORE ||
                    packageSource == PACKAGE_SOURCE_UNSPECIFIED)

        isPreloadedApp =
            initiatingPackageName == null && packageSource == PACKAGE_SOURCE_UNSPECIFIED
        supportsSafetyLabel = isStoreInstalled || isPreloadedApp
    }

    /** The package name of the install source (usually the app store) */
    val initiatingPackageName: String?

    /** Whether packages from this install source support safety labels. */
    val supportsSafetyLabel: Boolean

    /** Whether this install source indicates that the package was preloaded onto the device. */
    val isPreloadedApp: Boolean

    companion object {
        /** Indicates an unavailable install source. */
        val INSTALL_SOURCE_UNAVAILABLE = LightInstallSourceInfo()
    }
}
