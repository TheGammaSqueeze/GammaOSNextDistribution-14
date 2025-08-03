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

import android.net.ConnectivityManager
import android.net.Proxy
import android.net.ProxyInfo
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ProxyTest {
    @get:Rule
    val ignoreRule = DevSdkIgnoreRule()

    @Test
    fun testConstructor() {
        Proxy()
    }

    @Test
    fun testAccessProperties() {
        val minValidPort = 0
        val maxValidPort = 65535
        val defaultPort = Proxy.getDefaultPort()
        if (null == Proxy.getDefaultHost()) {
            assertEquals(-1, defaultPort.toLong())
        } else {
            Assert.assertTrue(defaultPort in minValidPort..maxValidPort)
        }
    }

    private fun verifyProxySystemProperties(info: ProxyInfo) {
        assertEquals(info.host, System.getProperty("http.proxyHost"))
        assertEquals(info.host, System.getProperty("https.proxyHost"))

        assertEquals(info.port.toString(), System.getProperty("http.proxyPort"))
        assertEquals(info.port.toString(), System.getProperty("https.proxyPort"))

        val strExcludes = if (info.exclusionList.isEmpty()) null
                else TextUtils.join("|", info.exclusionList)
        assertEquals(strExcludes, System.getProperty("https.nonProxyHosts"))
        assertEquals(strExcludes, System.getProperty("http.nonProxyHosts"))
    }

    private fun getDefaultProxy(): ProxyInfo? {
        return InstrumentationRegistry.getInstrumentation().context
                .getSystemService(ConnectivityManager::class.java)!!
                .getDefaultProxy()
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.R) // setHttpProxyConfiguration was added in S
    fun testSetHttpProxyConfiguration_DirectProxy() {
        val info = ProxyInfo.buildDirectProxy(
                "testproxy.android.com",
                12345 /* port */,
                listOf("testexclude1.android.com", "testexclude2.android.com"))
        val original = getDefaultProxy()
        try {
            Proxy.setHttpProxyConfiguration(info)
            verifyProxySystemProperties(info)
        } finally {
            Proxy.setHttpProxyConfiguration(original)
        }
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.R) // setHttpProxyConfiguration was added in S
    fun testSetHttpProxyConfiguration_PacProxy() {
        val pacInfo = ProxyInfo.buildPacProxy(Uri.parse("http://testpac.android.com/pac.pac"))
        val original = getDefaultProxy()
        try {
            Proxy.setHttpProxyConfiguration(pacInfo)
            verifyProxySystemProperties(pacInfo)
        } finally {
            Proxy.setHttpProxyConfiguration(original)
        }
    }
}
