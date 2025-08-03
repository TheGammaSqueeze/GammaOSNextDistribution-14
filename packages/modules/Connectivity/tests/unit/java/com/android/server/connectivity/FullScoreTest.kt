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

package com.android.server.connectivity

import android.net.NetworkAgentConfig
import android.net.NetworkCapabilities
import android.net.NetworkScore
import android.net.NetworkScore.KEEP_CONNECTED_FOR_HANDOVER
import android.net.NetworkScore.KEEP_CONNECTED_NONE
import android.os.Build
import android.text.TextUtils
import android.util.ArraySet
import android.util.Log
import androidx.test.filters.SmallTest
import com.android.server.connectivity.FullScore.MAX_CS_MANAGED_POLICY
import com.android.server.connectivity.FullScore.MIN_CS_MANAGED_POLICY
import com.android.server.connectivity.FullScore.POLICY_ACCEPT_UNVALIDATED
import com.android.server.connectivity.FullScore.POLICY_EVER_EVALUATED
import com.android.server.connectivity.FullScore.POLICY_EVER_USER_SELECTED
import com.android.server.connectivity.FullScore.POLICY_IS_DESTROYED
import com.android.server.connectivity.FullScore.POLICY_IS_UNMETERED
import com.android.server.connectivity.FullScore.POLICY_IS_VALIDATED
import com.android.server.connectivity.FullScore.POLICY_IS_VPN
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.full.staticProperties
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(DevSdkIgnoreRunner::class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
class FullScoreTest {
    // Convenience methods
    fun FullScore.withPolicies(
        validated: Boolean = false,
        vpn: Boolean = false,
        onceChosen: Boolean = false,
        acceptUnvalidated: Boolean = false,
        everEvaluated: Boolean = true,
        destroyed: Boolean = false
    ): FullScore {
        val nac = NetworkAgentConfig.Builder().apply {
            setUnvalidatedConnectivityAcceptable(acceptUnvalidated)
            setExplicitlySelected(onceChosen)
        }.build()
        val nc = NetworkCapabilities.Builder().apply {
            if (vpn) addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            if (validated) addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.build()
        return mixInScore(nc, nac, validated, false /* avoidUnvalidated */,
                false /* yieldToBadWifi */, everEvaluated, destroyed)
    }

    private val TAG = this::class.simpleName

    private lateinit var wtfHandler: Log.TerribleFailureHandler

    @Before
    fun setUp() {
        // policyNameOf will call Log.wtf if passed an invalid policy.
        wtfHandler = Log.setWtfHandler() { tagString, what, system ->
            Log.d(TAG, "WTF captured, ignoring: $tagString $what")
        }
    }

    @After
    fun tearDown() {
        Log.setWtfHandler(wtfHandler)
    }

    @Test
    fun testToString() {
        val string = FullScore(0L /* policy */, KEEP_CONNECTED_NONE)
                .withPolicies(vpn = true, acceptUnvalidated = true).toString()
        assertTrue(string.contains("Score("), string)
        assertTrue(string.contains("ACCEPT_UNVALIDATED"), string)
        assertTrue(string.contains("IS_VPN"), string)
        assertFalse(string.contains("IS_VALIDATED"), string)
        val foundNames = ArraySet<String>()
        getAllPolicies().forEach {
            val name = FullScore.policyNameOf(it.get() as Int)
            assertFalse(TextUtils.isEmpty(name))
            assertFalse(foundNames.contains(name))
            foundNames.add(name)
        }
        assertEquals("IS_UNMETERED", FullScore.policyNameOf(POLICY_IS_UNMETERED))
        val invalidPolicy = MAX_CS_MANAGED_POLICY + 1
        assertEquals(Integer.toString(invalidPolicy), FullScore.policyNameOf(invalidPolicy))
    }

    fun getAllPolicies() = Regex("POLICY_.*").let { nameRegex ->
        FullScore::class.staticProperties.filter { it.name.matches(nameRegex) }
    }

    @Test
    fun testHasPolicy() {
        val ns = FullScore(0L /* policy */, KEEP_CONNECTED_NONE)
        assertFalse(ns.hasPolicy(POLICY_IS_VALIDATED))
        assertFalse(ns.hasPolicy(POLICY_IS_VPN))
        assertFalse(ns.hasPolicy(POLICY_EVER_USER_SELECTED))
        assertFalse(ns.hasPolicy(POLICY_ACCEPT_UNVALIDATED))
        assertTrue(ns.withPolicies(validated = true).hasPolicy(POLICY_IS_VALIDATED))
        assertTrue(ns.withPolicies(vpn = true).hasPolicy(POLICY_IS_VPN))
        assertTrue(ns.withPolicies(onceChosen = true).hasPolicy(POLICY_EVER_USER_SELECTED))
        assertTrue(ns.withPolicies(acceptUnvalidated = true).hasPolicy(POLICY_ACCEPT_UNVALIDATED))
        assertTrue(ns.withPolicies(destroyed = true).hasPolicy(POLICY_IS_DESTROYED))
        assertTrue(ns.withPolicies(everEvaluated = true).hasPolicy(POLICY_EVER_EVALUATED))
    }

    @Test
    fun testMinMaxPolicyConstants() {
        val policies = getAllPolicies()

        policies.forEach { policy ->
            assertTrue(policy.get() as Int >= MIN_CS_MANAGED_POLICY)
            assertTrue(policy.get() as Int <= MAX_CS_MANAGED_POLICY)
        }
        assertEquals(MIN_CS_MANAGED_POLICY, policies.minOfOrNull { it.get() as Int })
        assertEquals(MAX_CS_MANAGED_POLICY, policies.maxOfOrNull { it.get() as Int })
    }

    @Test
    fun testEquals() {
        val ns1 = FullScore(0L /* policy */, KEEP_CONNECTED_NONE)
        val ns2 = FullScore(0L /* policy */, KEEP_CONNECTED_NONE)
        val ns3 = FullScore(0L /* policy */, KEEP_CONNECTED_FOR_HANDOVER)
        val ns4 = NetworkScore.Builder().setLegacyInt(50).build()
        assertEquals(ns1, ns1)
        assertEquals(ns2, ns1)
        assertNotEquals(ns1.withPolicies(validated = true), ns1)
        assertNotEquals(ns3, ns1)
        assertFalse(ns1.equals(ns4))
    }

    @Test
    fun testDescribeDifferences() {
        val ns1 = FullScore((1L shl POLICY_EVER_EVALUATED) or (1L shl POLICY_IS_VALIDATED),
                KEEP_CONNECTED_NONE)
        val ns2 = FullScore((1L shl POLICY_IS_VALIDATED) or (1L shl POLICY_IS_VPN) or
                (1L shl POLICY_IS_DESTROYED), KEEP_CONNECTED_NONE)
        assertEquals("-EVER_EVALUATED+IS_DESTROYED+IS_VPN",
                ns2.describeDifferencesFrom(ns1))
        assertEquals("-IS_DESTROYED-IS_VPN+EVER_EVALUATED",
                ns1.describeDifferencesFrom(ns2))
    }
}
