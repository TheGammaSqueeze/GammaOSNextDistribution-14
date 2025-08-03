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

package android.permission.cts

import android.os.Build
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Test

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S, codeName = "S")
class PlatformPermissionGroupMappingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.context
    private val packageManager = context.packageManager

    @Test
    fun platformPermissionHasPermissionGroup() {
        val future = CompletableFuture<String>()
        packageManager.getGroupOfPlatformPermission(
            android.Manifest.permission.READ_CALENDAR,
            context.mainExecutor
        ) {
            future.complete(it)
        }
        val permissionGroupName = future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        assertThat(permissionGroupName).isEqualTo(android.Manifest.permission_group.CALENDAR)
    }

    @Test
    fun platformPermissionGroupHasPermission() {
        val future = CompletableFuture<List<String>>()
        packageManager.getPlatformPermissionsForGroup(
            android.Manifest.permission_group.CALENDAR,
            context.mainExecutor
        ) {
            future.complete(it)
        }
        val permissionNames = future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        assertThat(permissionNames).contains(android.Manifest.permission.READ_CALENDAR)
    }

    companion object {
        private const val TIMEOUT_MILLIS = 15 * 1000L
    }
}
