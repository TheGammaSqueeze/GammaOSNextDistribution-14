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

@file:Suppress("DEPRECATION") // This file tests a bunch of deprecated methods : don't warn about it

package com.android.server

import android.net.ConnectivityManager
import android.os.Build
import androidx.test.filters.SmallTest
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@DevSdkIgnoreRunner.MonitorThreadLeak
@RunWith(DevSdkIgnoreRunner::class)
@SmallTest
@IgnoreUpTo(Build.VERSION_CODES.R)
class CSBasicMethodsTest : CSTest() {
    @Test
    fun testNetworkTypes() {
        // Ensure that mocks for the networkAttributes config variable work as expected. If they
        // don't, then tests that depend on CONNECTIVITY_ACTION broadcasts for these network types
        // will fail. Failing here is much easier to debug.
        assertTrue(cm.isNetworkSupported(ConnectivityManager.TYPE_WIFI))
        assertTrue(cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE))
        assertTrue(cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE_MMS))
        assertTrue(cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE_FOTA))
        assertFalse(cm.isNetworkSupported(ConnectivityManager.TYPE_PROXY))

        // Check that TYPE_ETHERNET is supported. Unlike the asserts above, which only validate our
        // mocks, this assert exercises the ConnectivityService code path that ensures that
        // TYPE_ETHERNET is supported if the ethernet service is running.
        assertTrue(cm.isNetworkSupported(ConnectivityManager.TYPE_ETHERNET))
    }
}
