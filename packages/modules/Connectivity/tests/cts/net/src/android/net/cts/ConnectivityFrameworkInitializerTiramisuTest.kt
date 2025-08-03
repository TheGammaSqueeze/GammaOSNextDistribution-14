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

package android.net.cts

import android.net.nsd.NsdManager
import androidx.test.platform.app.InstrumentationRegistry
import com.android.networkstack.apishim.ConnectivityFrameworkInitShimImpl
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.SC_V2
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

private val cfiShim = ConnectivityFrameworkInitShimImpl.newInstance()

@RunWith(DevSdkIgnoreRunner::class)
// ConnectivityFrameworkInitializerTiramisu was added in T
@IgnoreUpTo(SC_V2) // TODO: Use to Build.VERSION_CODES.SC_V2 when available
class ConnectivityFrameworkInitializerTiramisuTest {
    @Test
    fun testServicesRegistered() {
        val ctx = InstrumentationRegistry.getInstrumentation().context as android.content.Context
        assertNotNull(ctx.getSystemService(NsdManager::class.java),
                "NsdManager not registered")
    }

    // registerServiceWrappers can only be called during initialization and should throw otherwise
    @Test(expected = IllegalStateException::class)
    fun testThrows() {
        cfiShim.registerServiceWrappers()
    }
}
