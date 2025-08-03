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

package android.net.http.cts

import android.net.http.ConnectionMigrationOptions
import android.net.http.ConnectionMigrationOptions.MIGRATION_OPTION_ENABLED
import android.net.http.ConnectionMigrationOptions.MIGRATION_OPTION_UNSPECIFIED
import android.os.Build
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
class ConnectionMigrationOptionsTest {

    @Test
    fun testConnectionMigrationOptions_defaultValues() {
        val options =
                ConnectionMigrationOptions.Builder().build()

        assertEquals(MIGRATION_OPTION_UNSPECIFIED, options.allowNonDefaultNetworkUsage)
        assertEquals(MIGRATION_OPTION_UNSPECIFIED, options.defaultNetworkMigration)
        assertEquals(MIGRATION_OPTION_UNSPECIFIED, options.pathDegradationMigration)
    }

    @Test
    fun testConnectionMigrationOptions_enableDefaultNetworkMigration_returnSetValue() {
        val options =
            ConnectionMigrationOptions.Builder()
                    .setDefaultNetworkMigration(MIGRATION_OPTION_ENABLED)
                    .build()

        assertEquals(MIGRATION_OPTION_ENABLED, options.defaultNetworkMigration)
    }

    @Test
    fun testConnectionMigrationOptions_enablePathDegradationMigration_returnSetValue() {
        val options =
            ConnectionMigrationOptions.Builder()
                    .setPathDegradationMigration(MIGRATION_OPTION_ENABLED)
                    .build()

        assertEquals(MIGRATION_OPTION_ENABLED, options.pathDegradationMigration)
    }

    @Test
    fun testConnectionMigrationOptions_allowNonDefaultNetworkUsage_returnSetValue() {
        val options =
                ConnectionMigrationOptions.Builder()
                        .setAllowNonDefaultNetworkUsage(MIGRATION_OPTION_ENABLED).build()

        assertEquals(MIGRATION_OPTION_ENABLED, options.allowNonDefaultNetworkUsage)
    }
}
