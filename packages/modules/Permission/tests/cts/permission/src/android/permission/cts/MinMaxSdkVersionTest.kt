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

package android.permission.cts

import android.app.Instrumentation
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.permission.cts.PermissionUtils.install
import android.platform.test.annotations.AppModeFull
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.CddTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
@CddTest(requirement = "9.1/C-0-1")
@AppModeFull(reason = "Instant apps cannot read state of other packages.")
class MinMaxSdkVersionTest {
    private val mInstrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private var mUiDevice: UiDevice? = null
    private var mContext: Context? = null

    @Before
    fun setup() {
        mUiDevice = UiDevice.getInstance(mInstrumentation)
        mContext = mInstrumentation?.targetContext
    }

    @Test
    fun givenMinSdkVersionInRangeThenPermissionIsRequested() {
        install(TEST_APP_PATH)

        Assert.assertTrue(appRequestsPermission(MINSDK_LT_DEVICESDK))
    }

    @Test
    fun givenMinSdkVersionOutOfRangeThenPermissionIsNotRequested() {
        install(TEST_APP_PATH)

        Assert.assertFalse(appRequestsPermission(MINSDK_GT_DEVICESDK))
    }

    @Test
    fun givenMaxSdkVersionInRangeThenPermissionIsRequested() {
        install(TEST_APP_PATH)

        Assert.assertTrue(appRequestsPermission(MAXSDK_GT_DEVICESDK))
    }

    @Test
    fun givenMaxSdkVersionOutOfRangeThenPermissionIsNotRequested() {
        install(TEST_APP_PATH)

        Assert.assertFalse(appRequestsPermission(MAXSDK_LT_DEVICESDK))
    }

    private fun appRequestsPermission(permName: String): Boolean {
        val packageInfo =
            mContext!!
                .packageManager
                .getPackageInfo(TEST_APP_PKG_NAME, PackageManager.GET_PERMISSIONS)
        return packageInfo.requestedPermissions!!.any { it == permName }
    }

    companion object {
        private const val TEST_APP_NAME = "CtsAppThatRequestsMultiplePermissionsWithMinMaxSdk.apk"
        private const val TMP_DIR = "/data/local/tmp/cts-permission/"
        private const val TEST_APP_PATH = TMP_DIR + TEST_APP_NAME
        private const val TEST_APP_PKG_NAME = "android.permission.cts.appthatrequestpermission"
        private const val CUSTOM_PERMS = "$TEST_APP_PKG_NAME.permissions"
        private const val MINSDK_LT_DEVICESDK = "$CUSTOM_PERMS.MINSDK_LT_DEVICESDK"
        private const val MINSDK_GT_DEVICESDK = "$CUSTOM_PERMS.MINSDK_GT_DEVICESDK"
        private const val MAXSDK_LT_DEVICESDK = "$CUSTOM_PERMS.MAXSDK_LT_DEVICESDK"
        private const val MAXSDK_GT_DEVICESDK = "$CUSTOM_PERMS.MAXSDK_GT_DEVICESDK"
    }
}
