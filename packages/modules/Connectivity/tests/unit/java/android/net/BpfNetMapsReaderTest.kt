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

package android.net

import android.net.BpfNetMapsConstants.DATA_SAVER_DISABLED
import android.net.BpfNetMapsConstants.DATA_SAVER_ENABLED
import android.net.BpfNetMapsConstants.DATA_SAVER_ENABLED_KEY
import android.net.BpfNetMapsConstants.DOZABLE_MATCH
import android.net.BpfNetMapsConstants.HAPPY_BOX_MATCH
import android.net.BpfNetMapsConstants.PENALTY_BOX_MATCH
import android.net.BpfNetMapsConstants.STANDBY_MATCH
import android.net.BpfNetMapsConstants.UID_RULES_CONFIGURATION_KEY
import android.net.BpfNetMapsUtils.getMatchByFirewallChain
import android.os.Build.VERSION_CODES
import com.android.net.module.util.IBpfMap
import com.android.net.module.util.Struct.S32
import com.android.net.module.util.Struct.U32
import com.android.net.module.util.Struct.U8
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.TestBpfMap
import java.lang.reflect.Modifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_UID1 = 1234
private const val TEST_UID2 = TEST_UID1 + 1
private const val TEST_UID3 = TEST_UID2 + 1
private const val NO_IIF = 0

// pre-T devices does not support Bpf.
@RunWith(DevSdkIgnoreRunner::class)
@IgnoreUpTo(VERSION_CODES.S_V2)
class BpfNetMapsReaderTest {
    @Rule
    @JvmField
    val ignoreRule = DevSdkIgnoreRule()

    private val testConfigurationMap: IBpfMap<S32, U32> = TestBpfMap()
    private val testUidOwnerMap: IBpfMap<S32, UidOwnerValue> = TestBpfMap()
    private val testDataSaverEnabledMap: IBpfMap<S32, U8> = TestBpfMap()
    private val bpfNetMapsReader = BpfNetMapsReader(
        TestDependencies(testConfigurationMap, testUidOwnerMap, testDataSaverEnabledMap))

    class TestDependencies(
        private val configMap: IBpfMap<S32, U32>,
        private val uidOwnerMap: IBpfMap<S32, UidOwnerValue>,
        private val dataSaverEnabledMap: IBpfMap<S32, U8>
    ) : BpfNetMapsReader.Dependencies() {
        override fun getConfigurationMap() = configMap
        override fun getUidOwnerMap() = uidOwnerMap
        override fun getDataSaverEnabledMap() = dataSaverEnabledMap
    }

    private fun doTestIsChainEnabled(chain: Int) {
        testConfigurationMap.updateEntry(
            UID_RULES_CONFIGURATION_KEY,
            U32(getMatchByFirewallChain(chain))
        )
        assertTrue(bpfNetMapsReader.isChainEnabled(chain))
        testConfigurationMap.updateEntry(UID_RULES_CONFIGURATION_KEY, U32(0))
        assertFalse(bpfNetMapsReader.isChainEnabled(chain))
    }

    @Test
    @Throws(Exception::class)
    fun testIsChainEnabled() {
        doTestIsChainEnabled(ConnectivityManager.FIREWALL_CHAIN_DOZABLE)
        doTestIsChainEnabled(ConnectivityManager.FIREWALL_CHAIN_STANDBY)
        doTestIsChainEnabled(ConnectivityManager.FIREWALL_CHAIN_POWERSAVE)
        doTestIsChainEnabled(ConnectivityManager.FIREWALL_CHAIN_RESTRICTED)
        doTestIsChainEnabled(ConnectivityManager.FIREWALL_CHAIN_LOW_POWER_STANDBY)
    }

    @Test
    fun testFirewallChainList() {
        // Verify that when a firewall chain constant is added, it should also be included in
        // firewall chain list.
        val declaredChains = ConnectivityManager::class.java.declaredFields.filter {
            Modifier.isStatic(it.modifiers) && it.name.startsWith("FIREWALL_CHAIN_")
        }
        // Verify the size matches, this also verifies no common item in allow and deny chains.
        assertEquals(BpfNetMapsConstants.ALLOW_CHAINS.size +
                BpfNetMapsConstants.DENY_CHAINS.size, declaredChains.size)
        declaredChains.forEach {
            assertTrue(BpfNetMapsConstants.ALLOW_CHAINS.contains(it.get(null)) ||
                    BpfNetMapsConstants.DENY_CHAINS.contains(it.get(null)))
        }
    }

    private fun mockChainEnabled(chain: Int, enabled: Boolean) {
        val config = testConfigurationMap.getValue(UID_RULES_CONFIGURATION_KEY).`val`
        val newConfig = if (enabled) {
            config or getMatchByFirewallChain(chain)
        } else {
            config and getMatchByFirewallChain(chain).inv()
        }
        testConfigurationMap.updateEntry(UID_RULES_CONFIGURATION_KEY, U32(newConfig))
    }

    fun isUidNetworkingBlocked(uid: Int, metered: Boolean = false, dataSaver: Boolean = false) =
            bpfNetMapsReader.isUidNetworkingBlocked(uid, metered, dataSaver)

    @Test
    fun testIsUidNetworkingBlockedByFirewallChains_allowChain() {
        // With everything disabled by default, verify the return value is false.
        testConfigurationMap.updateEntry(UID_RULES_CONFIGURATION_KEY, U32(0))
        assertFalse(isUidNetworkingBlocked(TEST_UID1))

        // Enable dozable chain but does not provide allowed list. Verify the network is blocked
        // for all uids.
        mockChainEnabled(ConnectivityManager.FIREWALL_CHAIN_DOZABLE, true)
        assertTrue(isUidNetworkingBlocked(TEST_UID1))
        assertTrue(isUidNetworkingBlocked(TEST_UID2))

        // Add uid1 to dozable allowed list. Verify the network is not blocked for uid1, while
        // uid2 is blocked.
        testUidOwnerMap.updateEntry(S32(TEST_UID1), UidOwnerValue(NO_IIF, DOZABLE_MATCH))
        assertFalse(isUidNetworkingBlocked(TEST_UID1))
        assertTrue(isUidNetworkingBlocked(TEST_UID2))
    }

    @Test
    fun testIsUidNetworkingBlockedByFirewallChains_denyChain() {
        // Enable standby chain but does not provide denied list. Verify the network is allowed
        // for all uids.
        testConfigurationMap.updateEntry(UID_RULES_CONFIGURATION_KEY, U32(0))
        mockChainEnabled(ConnectivityManager.FIREWALL_CHAIN_STANDBY, true)
        assertFalse(isUidNetworkingBlocked(TEST_UID1))
        assertFalse(isUidNetworkingBlocked(TEST_UID2))

        // Add uid1 to standby allowed list. Verify the network is blocked for uid1, while
        // uid2 is not blocked.
        testUidOwnerMap.updateEntry(S32(TEST_UID1), UidOwnerValue(NO_IIF, STANDBY_MATCH))
        assertTrue(isUidNetworkingBlocked(TEST_UID1))
        assertFalse(isUidNetworkingBlocked(TEST_UID2))
    }

    @Test
    fun testIsUidNetworkingBlockedByFirewallChains_blockedWithAllowed() {
        // Uids blocked by powersave chain but allowed by standby chain, verify the blocking
        // takes higher priority.
        testConfigurationMap.updateEntry(UID_RULES_CONFIGURATION_KEY, U32(0))
        mockChainEnabled(ConnectivityManager.FIREWALL_CHAIN_POWERSAVE, true)
        mockChainEnabled(ConnectivityManager.FIREWALL_CHAIN_STANDBY, true)
        assertTrue(isUidNetworkingBlocked(TEST_UID1))
    }

    @IgnoreUpTo(VERSION_CODES.S_V2)
    @Test
    fun testIsUidNetworkingBlockedByDataSaver() {
        // With everything disabled by default, verify the return value is false.
        testConfigurationMap.updateEntry(UID_RULES_CONFIGURATION_KEY, U32(0))
        assertFalse(isUidNetworkingBlocked(TEST_UID1, metered = true))

        // Add uid1 to penalty box, verify the network is blocked for uid1, while uid2 is not
        // affected.
        testUidOwnerMap.updateEntry(S32(TEST_UID1), UidOwnerValue(NO_IIF, PENALTY_BOX_MATCH))
        assertTrue(isUidNetworkingBlocked(TEST_UID1, metered = true))
        assertFalse(isUidNetworkingBlocked(TEST_UID2, metered = true))

        // Enable data saver, verify the network is blocked for uid1, uid2, but uid3 in happy box
        // is not affected.
        testUidOwnerMap.updateEntry(S32(TEST_UID3), UidOwnerValue(NO_IIF, HAPPY_BOX_MATCH))
        assertTrue(isUidNetworkingBlocked(TEST_UID1, metered = true, dataSaver = true))
        assertTrue(isUidNetworkingBlocked(TEST_UID2, metered = true, dataSaver = true))
        assertFalse(isUidNetworkingBlocked(TEST_UID3, metered = true, dataSaver = true))

        // Add uid1 to happy box as well, verify nothing is changed because penalty box has higher
        // priority.
        testUidOwnerMap.updateEntry(
            S32(TEST_UID1),
            UidOwnerValue(NO_IIF, PENALTY_BOX_MATCH or HAPPY_BOX_MATCH)
        )
        assertTrue(isUidNetworkingBlocked(TEST_UID1, metered = true, dataSaver = true))
        assertTrue(isUidNetworkingBlocked(TEST_UID2, metered = true, dataSaver = true))
        assertFalse(isUidNetworkingBlocked(TEST_UID3, metered = true, dataSaver = true))

        // Enable doze mode, verify uid3 is blocked even if it is in happy box.
        mockChainEnabled(ConnectivityManager.FIREWALL_CHAIN_DOZABLE, true)
        assertTrue(isUidNetworkingBlocked(TEST_UID1, metered = true, dataSaver = true))
        assertTrue(isUidNetworkingBlocked(TEST_UID2, metered = true, dataSaver = true))
        assertTrue(isUidNetworkingBlocked(TEST_UID3, metered = true, dataSaver = true))

        // Disable doze mode and data saver, only uid1 which is in penalty box is blocked.
        mockChainEnabled(ConnectivityManager.FIREWALL_CHAIN_DOZABLE, false)
        assertTrue(isUidNetworkingBlocked(TEST_UID1, metered = true))
        assertFalse(isUidNetworkingBlocked(TEST_UID2, metered = true))
        assertFalse(isUidNetworkingBlocked(TEST_UID3, metered = true))

        // Make the network non-metered, nothing is blocked.
        assertFalse(isUidNetworkingBlocked(TEST_UID1))
        assertFalse(isUidNetworkingBlocked(TEST_UID2))
        assertFalse(isUidNetworkingBlocked(TEST_UID3))
    }

    @Test
    fun testGetDataSaverEnabled() {
        testDataSaverEnabledMap.updateEntry(DATA_SAVER_ENABLED_KEY, U8(DATA_SAVER_DISABLED))
        assertFalse(bpfNetMapsReader.dataSaverEnabled)
        testDataSaverEnabledMap.updateEntry(DATA_SAVER_ENABLED_KEY, U8(DATA_SAVER_ENABLED))
        assertTrue(bpfNetMapsReader.dataSaverEnabled)
    }
}
