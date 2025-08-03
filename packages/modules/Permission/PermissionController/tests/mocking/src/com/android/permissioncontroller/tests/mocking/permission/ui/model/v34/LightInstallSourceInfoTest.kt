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

package com.android.permissioncontroller.tests.mocking.permission.ui.model.v34

import android.content.pm.PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_OTHER
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_STORE
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED
import android.os.Build
import androidx.test.filters.SdkSuppress
import com.android.permissioncontroller.permission.model.livedatatypes.v34.LightInstallSourceInfo
import com.android.permissioncontroller.permission.model.livedatatypes.v34.LightInstallSourceInfo.Companion.INSTALL_SOURCE_UNAVAILABLE
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tests for [LightInstallSourceInfo]. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class LightInstallSourceInfoTest {
    @Test
    fun initiatingPackageName_withDefaultConstructor_returnsNull() {
        val installSourceInfo = INSTALL_SOURCE_UNAVAILABLE

        assertThat(installSourceInfo.initiatingPackageName).isEqualTo(null)
    }

    @Test
    fun initiatingPackageName_whenSet_returnsInitiatingPackageName() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_STORE, INITIATING_PKG_NAME)

        assertThat(installSourceInfo.initiatingPackageName).isEqualTo(INITIATING_PKG_NAME)
    }

    @Test
    fun initiatingPackageName_whenNotSet_returnsNull() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_STORE, null)

        assertThat(installSourceInfo.initiatingPackageName).isEqualTo(null)
    }

    @Test
    fun supportsSafetyLabel_withSourceStore_andPackageName_returnsTrue() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_STORE, INITIATING_PKG_NAME)

        assertThat(installSourceInfo.supportsSafetyLabel).isEqualTo(true)
    }

    @Test
    fun supportsSafetyLabel_withSourceOther_andPackageName_returnsFalse() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_OTHER, INITIATING_PKG_NAME)

        assertThat(installSourceInfo.supportsSafetyLabel).isEqualTo(false)
    }

    @Test
    fun supportsSafetyLabel_withSourceUnspecified_andPackageName_returnsTrue() {
        val installSourceInfo =
            LightInstallSourceInfo(PACKAGE_SOURCE_UNSPECIFIED, INITIATING_PKG_NAME)

        assertThat(installSourceInfo.supportsSafetyLabel).isEqualTo(true)
    }

    @Test
    fun supportsSafetyLabel_withSourceLocalFile_andPackageName_returnsFalse() {
        val installSourceInfo =
            LightInstallSourceInfo(PACKAGE_SOURCE_LOCAL_FILE, INITIATING_PKG_NAME)

        assertThat(installSourceInfo.supportsSafetyLabel).isEqualTo(false)
    }

    @Test
    fun supportsSafetyLabel_withSourceDownloadedFile_andPackageName_returnsFalse() {
        val installSourceInfo =
            LightInstallSourceInfo(PACKAGE_SOURCE_DOWNLOADED_FILE, INITIATING_PKG_NAME)

        assertThat(installSourceInfo.supportsSafetyLabel).isEqualTo(false)
    }

    @Test
    fun supportsSafetyLabel_withSourceStore_noPackageName_returnsFalse() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_STORE, null)

        assertThat(installSourceInfo.supportsSafetyLabel).isEqualTo(false)
    }

    @Test
    fun supportsSafetyLabel_withSourceOther_noPackageName_returnsFalse() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_OTHER, null)

        assertThat(installSourceInfo.supportsSafetyLabel).isEqualTo(false)
    }

    @Test
    fun supportsSafetyLabel_withSourceUnspecified_noPackageName_returnsTrue() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_UNSPECIFIED, null)

        assertThat(installSourceInfo.supportsSafetyLabel).isEqualTo(true)
    }

    @Test
    fun supportsSafetyLabel_withSourceLocalFile_noPackageName_returnsFalse() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_LOCAL_FILE, null)

        assertThat(installSourceInfo.supportsSafetyLabel).isEqualTo(false)
    }

    @Test
    fun supportsSafetyLabel_withSourceDownloadedFile_noPackageName_returnsFalse() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_DOWNLOADED_FILE, null)

        assertThat(installSourceInfo.supportsSafetyLabel).isEqualTo(false)
    }

    @Test
    fun supportsSafetyLabel_withNoSource_noPackageName_returnsFalse() {
        val installSourceInfo = INSTALL_SOURCE_UNAVAILABLE

        assertThat(installSourceInfo.supportsSafetyLabel).isEqualTo(false)
    }

    @Test
    fun isPreloadedApp_withSourceStore_andPackageName_returnsFalse() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_STORE, INITIATING_PKG_NAME)

        assertThat(installSourceInfo.isPreloadedApp).isEqualTo(false)
    }

    @Test
    fun isPreloadedApp_withSourceOther_andPackageName_returnsFalse() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_OTHER, INITIATING_PKG_NAME)

        assertThat(installSourceInfo.isPreloadedApp).isEqualTo(false)
    }

    @Test
    fun isPreloadedApp_withSourceUnspecified_andPackageName_returnsFalse() {
        val installSourceInfo =
            LightInstallSourceInfo(PACKAGE_SOURCE_UNSPECIFIED, INITIATING_PKG_NAME)

        assertThat(installSourceInfo.isPreloadedApp).isEqualTo(false)
    }

    @Test
    fun isPreloadedApp_withSourceLocalFile_andPackageName_returnsFalse() {
        val installSourceInfo =
            LightInstallSourceInfo(PACKAGE_SOURCE_LOCAL_FILE, INITIATING_PKG_NAME)

        assertThat(installSourceInfo.isPreloadedApp).isEqualTo(false)
    }

    @Test
    fun isPreloadedApp_withSourceDownloadedFile_andPackageName_returnsFalse() {
        val installSourceInfo =
            LightInstallSourceInfo(PACKAGE_SOURCE_DOWNLOADED_FILE, INITIATING_PKG_NAME)

        assertThat(installSourceInfo.isPreloadedApp).isEqualTo(false)
    }

    @Test
    fun isPreloadedApp_withSourceStore_noPackageName_returnsFalse() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_STORE, null)

        assertThat(installSourceInfo.isPreloadedApp).isEqualTo(false)
    }

    @Test
    fun isPreloadedApp_withSourceOther_noPackageName_returnsFalse() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_OTHER, null)

        assertThat(installSourceInfo.isPreloadedApp).isEqualTo(false)
    }

    @Test
    fun isPreloadedApp_withSourceUnspecified_noPackageName_returnsTrue() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_UNSPECIFIED, null)

        assertThat(installSourceInfo.isPreloadedApp).isEqualTo(true)
    }

    @Test
    fun isPreloadedApp_withSourceLocalFile_noPackageName_returnsFalse() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_LOCAL_FILE, null)

        assertThat(installSourceInfo.isPreloadedApp).isEqualTo(false)
    }

    @Test
    fun isPreloadedApp_withSourceDownloadedFile_noPackageName_returnsFalse() {
        val installSourceInfo = LightInstallSourceInfo(PACKAGE_SOURCE_DOWNLOADED_FILE, null)

        assertThat(installSourceInfo.isPreloadedApp).isEqualTo(false)
    }

    @Test
    fun isPreloadedApp_withNoSource_noPackageName_returnsFalse() {
        val installSourceInfo = INSTALL_SOURCE_UNAVAILABLE

        assertThat(installSourceInfo.isPreloadedApp).isEqualTo(false)
    }

    companion object {
        private const val INITIATING_PKG_NAME = "pkg_name"
    }
}
