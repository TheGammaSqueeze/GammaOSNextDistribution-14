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

package android.net.cts

import android.Manifest.permission.MANAGE_TEST_NETWORKS
import android.net.ConnectivityDiagnosticsManager
import android.net.ConnectivityFrameworkInitializer
import android.net.ConnectivityManager
import android.net.TestNetworkManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.runAsShell
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

@RunWith(DevSdkIgnoreRunner::class)
// ConnectivityFrameworkInitializer was added in S
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
class ConnectivityFrameworkInitializerTest {
    @Test
    fun testServicesRegistered() {
        val ctx = InstrumentationRegistry.getInstrumentation().context as android.content.Context
        assertNotNull(ctx.getSystemService(ConnectivityManager::class.java),
                "ConnectivityManager not registered")
        assertNotNull(ctx.getSystemService(ConnectivityDiagnosticsManager::class.java),
                "ConnectivityDiagnosticsManager not registered")

        runAsShell(MANAGE_TEST_NETWORKS) {
            assertNotNull(ctx.getSystemService(TestNetworkManager::class.java),
                "TestNetworkManager not registered")
        }
        // Do not test for DnsResolverServiceManager as it is internal to Connectivity, and
        // CTS does not have permission to get it anyway.
    }

    // registerServiceWrappers can only be called during initialization and should throw otherwise
    @Test(expected = IllegalStateException::class)
    fun testThrows() {
        ConnectivityFrameworkInitializer.registerServiceWrappers()
    }
}