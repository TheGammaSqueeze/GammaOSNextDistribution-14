/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.net

import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.modules.utils.build.SdkLevel.isAtLeastS
import com.android.modules.utils.build.SdkLevel.isAtLeastT
import com.android.testutils.ConnectivityModuleTest
import com.android.testutils.assertParcelingIsLossless
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
@ConnectivityModuleTest
class NetworkAgentConfigTest {
    @Test
    fun testParcelNetworkAgentConfig() {
        val config = NetworkAgentConfig.Builder().apply {
            setExplicitlySelected(true)
            setLegacyType(ConnectivityManager.TYPE_ETHERNET)
            setSubscriberId("MySubId")
            setPartialConnectivityAcceptable(false)
            setUnvalidatedConnectivityAcceptable(true)
            if (isAtLeastS()) {
                setBypassableVpn(true)
            }
            if (isAtLeastT()) {
                setLocalRoutesExcludedForVpn(true)
                setVpnRequiresValidation(true)
            }
        }.build()
        assertParcelingIsLossless(config)
    }

    @Test
    fun testBuilder() {
        val testExtraInfo = "mylegacyExtraInfo"
        val config = NetworkAgentConfig.Builder().apply {
            setExplicitlySelected(true)
            setLegacyType(ConnectivityManager.TYPE_ETHERNET)
            setSubscriberId("MySubId")
            setPartialConnectivityAcceptable(false)
            setUnvalidatedConnectivityAcceptable(true)
            setLegacyTypeName("TEST_NETWORK")
            if (isAtLeastS()) {
                setLegacyExtraInfo(testExtraInfo)
                setNat64DetectionEnabled(false)
                setProvisioningNotificationEnabled(false)
                setBypassableVpn(true)
            }
            if (isAtLeastT()) {
                setLocalRoutesExcludedForVpn(true)
                setVpnRequiresValidation(true)
            }
        }.build()

        assertTrue(config.isExplicitlySelected())
        assertEquals(ConnectivityManager.TYPE_ETHERNET, config.getLegacyType())
        assertEquals("MySubId", config.getSubscriberId())
        assertFalse(config.isPartialConnectivityAcceptable())
        assertTrue(config.isUnvalidatedConnectivityAcceptable())
        assertEquals("TEST_NETWORK", config.getLegacyTypeName())
        if (isAtLeastT()) {
            assertTrue(config.areLocalRoutesExcludedForVpn())
            assertTrue(config.isVpnValidationRequired())
        }
        if (isAtLeastS()) {
            assertEquals(testExtraInfo, config.getLegacyExtraInfo())
            assertFalse(config.isNat64DetectionEnabled())
            assertFalse(config.isProvisioningNotificationEnabled())
            assertTrue(config.isBypassableVpn())
        } else {
            assertTrue(config.isNat64DetectionEnabled())
            assertTrue(config.isProvisioningNotificationEnabled())
        }
    }
}
