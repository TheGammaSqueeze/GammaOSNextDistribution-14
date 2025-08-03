/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.attributionsource.cts

import android.app.AppOpsManager
import android.content.AttributionSource
import android.os.Process
import android.permission.PermissionManager
import android.platform.test.annotations.AppModeFull
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

@AppModeFull(reason = "Instant apps cannot hold READ_CALENDAR")
class PermissionCheckerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.getContext()
    private val packageManager = context.packageManager
    private val appOpsManager = context.getSystemService(AppOpsManager::class.java)
    private val permissionManager = context.getSystemService(PermissionManager::class.java)
    private val currentUser = Process.myUserHandle()

    private val helperUid = packageManager.getPackageUid(HELPER_PACKAGE_NAME, 0)
    private val helperAttributionSource = AttributionSource.Builder(helperUid)
        .setPackageName(HELPER_PACKAGE_NAME)
        .build()

    @Before
    @After
    fun resetHelperPermissionState() {
        runWithShellPermissionIdentity {
            Thread.sleep(1000)
            packageManager.grantRuntimePermission(
                HELPER_PACKAGE_NAME, HELPER_PERMISSION_NAME, currentUser
            )
            Thread.sleep(1000)
            appOpsManager.setUidMode(HELPER_APP_OP_NAME, helperUid, AppOpsManager.MODE_ALLOWED)
            Thread.sleep(1000)
        }
    }

    @Test
    fun testCheckPermissionForPreflight() {
        assertThat(
            permissionManager.checkPermissionForPreflight(
                HELPER_PERMISSION_NAME, helperAttributionSource
            )
        ).isEqualTo(PermissionManager.PERMISSION_GRANTED)

        runWithShellPermissionIdentity {
            appOpsManager.setUidMode(HELPER_APP_OP_NAME, helperUid, AppOpsManager.MODE_IGNORED)
        }
        assertThat(
            permissionManager.checkPermissionForPreflight(
                HELPER_PERMISSION_NAME, helperAttributionSource
            )
        ).isEqualTo(PermissionManager.PERMISSION_SOFT_DENIED)

        runWithShellPermissionIdentity {
            packageManager.revokeRuntimePermission(
                HELPER_PACKAGE_NAME, HELPER_PERMISSION_NAME, currentUser
            )
        }
        assertThat(
            permissionManager.checkPermissionForPreflight(
                HELPER_PERMISSION_NAME, helperAttributionSource
            )
        ).isEqualTo(PermissionManager.PERMISSION_HARD_DENIED)
    }

    @Test
    fun testCheckPermissionForDataDelivery() {
        // checkPermissionForDataDelivery() requires UPDATE_APP_OPS_STATS.
        runWithShellPermissionIdentity {
            assertThat(
                permissionManager.checkPermissionForDataDelivery(
                    HELPER_PERMISSION_NAME, helperAttributionSource, null
                )
            ).isEqualTo(PermissionManager.PERMISSION_GRANTED)

            appOpsManager.setUidMode(HELPER_APP_OP_NAME, helperUid, AppOpsManager.MODE_IGNORED)
            assertThat(
                permissionManager.checkPermissionForDataDelivery(
                    HELPER_PERMISSION_NAME, helperAttributionSource, null
                )
            ).isEqualTo(PermissionManager.PERMISSION_SOFT_DENIED)

            packageManager.revokeRuntimePermission(
                HELPER_PACKAGE_NAME, HELPER_PERMISSION_NAME, currentUser
            )
            assertThat(
                permissionManager.checkPermissionForDataDelivery(
                    HELPER_PERMISSION_NAME, helperAttributionSource, null
                )
            ).isEqualTo(PermissionManager.PERMISSION_HARD_DENIED)
        }
    }

    @Test
    fun testCheckPermissionForDataDeliveryFromDataSource() {
        runWithShellPermissionIdentity({
            assertThat(
                permissionManager.checkPermissionForDataDeliveryFromDataSource(
                    HELPER_PERMISSION_NAME, helperAttributionSource, null
                )
            ).isEqualTo(PermissionManager.PERMISSION_GRANTED)
        }, android.Manifest.permission.UPDATE_APP_OPS_STATS)

        runWithShellPermissionIdentity {
            appOpsManager.setUidMode(HELPER_APP_OP_NAME, helperUid, AppOpsManager.MODE_IGNORED)
        }

        runWithShellPermissionIdentity({
            assertThat(
                permissionManager.checkPermissionForDataDeliveryFromDataSource(
                    HELPER_PERMISSION_NAME, helperAttributionSource, null
                )
            ).isEqualTo(PermissionManager.PERMISSION_SOFT_DENIED)
        }, android.Manifest.permission.UPDATE_APP_OPS_STATS)

        runWithShellPermissionIdentity {
            packageManager.revokeRuntimePermission(
                HELPER_PACKAGE_NAME, HELPER_PERMISSION_NAME, currentUser
            )
        }

        runWithShellPermissionIdentity({
            assertThat(
                permissionManager.checkPermissionForDataDeliveryFromDataSource(
                    HELPER_PERMISSION_NAME, helperAttributionSource, null
                )
            ).isEqualTo(PermissionManager.PERMISSION_SOFT_DENIED)
        }, android.Manifest.permission.UPDATE_APP_OPS_STATS)
    }

    companion object {
        private const val HELPER_PACKAGE_NAME = "android.attributionsource.cts.blamed"
        private const val HELPER_PERMISSION_NAME = android.Manifest.permission.READ_CALENDAR
        private const val HELPER_APP_OP_NAME = AppOpsManager.OPSTR_READ_CALENDAR
    }
}
