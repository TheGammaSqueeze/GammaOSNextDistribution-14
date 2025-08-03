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

import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL as NET_CAP_PORTAL
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkScore.KEEP_CONNECTED_NONE
import android.net.NetworkScore.POLICY_EXITING as EXITING
import android.net.NetworkScore.POLICY_TRANSPORT_PRIMARY as PRIMARY
import android.net.NetworkScore.POLICY_YIELD_TO_BAD_WIFI as YIELD_TO_BAD_WIFI
import android.os.Build
import androidx.test.filters.SmallTest
import com.android.connectivity.resources.R
import com.android.server.connectivity.FullScore.POLICY_AVOIDED_WHEN_UNVALIDATED as AVOIDED_UNVALID
import com.android.server.connectivity.FullScore.POLICY_EVER_EVALUATED as EVER_EVALUATED
import com.android.server.connectivity.FullScore.POLICY_EVER_VALIDATED as EVER_VALIDATED
import com.android.server.connectivity.FullScore.POLICY_IS_VALIDATED as IS_VALIDATED
import com.android.testutils.DevSdkIgnoreRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

private fun score(vararg policies: Int) = FullScore(
        policies.fold(0L) { acc, e -> acc or (1L shl e) }, KEEP_CONNECTED_NONE)
private fun caps(transport: Int, vararg capabilities: Int) =
        NetworkCapabilities.Builder().addTransportType(transport).apply {
            capabilities.forEach { addCapability(it) }
        }.build()

@SmallTest
@RunWith(Parameterized::class)
class NetworkRankerTest(private val activelyPreferBadWifi: Boolean) {
    private val mRanker = NetworkRanker(NetworkRanker.Configuration(activelyPreferBadWifi))

    private class TestScore(private val sc: FullScore, private val nc: NetworkCapabilities)
            : NetworkRanker.Scoreable {
        override fun getScore() = sc
        override fun getCapsNoCopy(): NetworkCapabilities = nc
    }

    @get:Rule
    val mIgnoreRule: DevSdkIgnoreRule = DevSdkIgnoreRule(ignoreClassUpTo = Build.VERSION_CODES.R)

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun ranker() = listOf(true, false)
    }

    // Helpers to shorten syntax
    private fun rank(vararg scores: TestScore) =
            mRanker.getBestNetworkByPolicy(scores.toList(), null /* currentSatisfier */)
    val CAPS_CELL = caps(TRANSPORT_CELLULAR)
    val CAPS_WIFI = caps(TRANSPORT_WIFI)
    val CAPS_WIFI_PORTAL = caps(TRANSPORT_WIFI, NET_CAP_PORTAL)

    @Test
    fun testYieldToBadWiFi_oneCell() {
        // Only cell, it wins
        val cell = TestScore(score(EVER_EVALUATED, YIELD_TO_BAD_WIFI, IS_VALIDATED), CAPS_CELL)
        assertEquals(cell, rank(cell))
    }

    @Test
    fun testPreferBadWifi_oneCellOneEvaluatingWifi() {
        val wifi = TestScore(score(), caps(TRANSPORT_WIFI))
        val cell = TestScore(score(YIELD_TO_BAD_WIFI, IS_VALIDATED, EVER_EVALUATED), CAPS_CELL)
        assertEquals(cell, rank(wifi, cell))
    }

    @Test
    fun testYieldToBadWiFi_oneCellOneBadWiFi() {
        // Bad wifi wins against yielding validated cell
        val badWifi = TestScore(score(EVER_EVALUATED, EVER_VALIDATED), CAPS_WIFI)
        val cell = TestScore(score(EVER_EVALUATED, YIELD_TO_BAD_WIFI, IS_VALIDATED), CAPS_CELL)
        assertEquals(badWifi, rank(badWifi, cell))
    }

    @Test
    fun testPreferBadWifi_oneCellOneBadWifi() {
        val badWifi = TestScore(score(EVER_EVALUATED), CAPS_WIFI)
        val cell = TestScore(score(EVER_EVALUATED, YIELD_TO_BAD_WIFI, IS_VALIDATED), CAPS_CELL)
        val winner = if (activelyPreferBadWifi) badWifi else cell
        assertEquals(winner, rank(badWifi, cell))
    }

    @Test
    fun testPreferBadWifi_oneCellOneCaptivePortalWifi() {
        val portalWifi = TestScore(score(EVER_EVALUATED), CAPS_WIFI_PORTAL)
        val cell = TestScore(score(EVER_EVALUATED, YIELD_TO_BAD_WIFI, IS_VALIDATED), CAPS_CELL)
        assertEquals(cell, rank(portalWifi, cell))
    }

    @Test
    fun testYieldToBadWifi_oneCellOneCaptivePortalWifiThatClosed() {
        val portalWifiClosed = TestScore(score(EVER_EVALUATED, EVER_VALIDATED), CAPS_WIFI_PORTAL)
        val cell = TestScore(score(EVER_EVALUATED, YIELD_TO_BAD_WIFI, IS_VALIDATED), CAPS_CELL)
        assertEquals(portalWifiClosed, rank(portalWifiClosed, cell))
    }

    @Test
    fun testYieldToBadWifi_avoidUnvalidated() {
        // Bad wifi avoided when unvalidated loses against yielding validated cell
        val cell = TestScore(score(EVER_EVALUATED, YIELD_TO_BAD_WIFI, IS_VALIDATED), CAPS_CELL)
        val avoidedWifi = TestScore(score(EVER_EVALUATED, EVER_VALIDATED, AVOIDED_UNVALID),
                CAPS_WIFI)
        assertEquals(cell, rank(cell, avoidedWifi))
    }

    @Test
    fun testYieldToBadWiFi_oneCellTwoBadWiFi() {
        // Bad wifi wins against yielding validated cell. Prefer the one that's primary.
        val primaryBadWifi = TestScore(score(EVER_EVALUATED, EVER_VALIDATED, PRIMARY), CAPS_WIFI)
        val secondaryBadWifi = TestScore(score(EVER_EVALUATED, EVER_VALIDATED), CAPS_WIFI)
        val cell = TestScore(score(EVER_EVALUATED, YIELD_TO_BAD_WIFI, IS_VALIDATED), CAPS_CELL)
        assertEquals(primaryBadWifi, rank(primaryBadWifi, secondaryBadWifi, cell))
    }

    @Test
    fun testYieldToBadWiFi_oneCellTwoBadWiFiOneNotAvoided() {
        // Bad wifi ever validated wins against bad wifi that never was validated (or was
        // avoided when bad).
        val badWifi = TestScore(score(EVER_EVALUATED, EVER_VALIDATED), CAPS_WIFI)
        val neverValidatedWifi = TestScore(score(), CAPS_WIFI)
        val cell = TestScore(score(EVER_EVALUATED, YIELD_TO_BAD_WIFI, IS_VALIDATED), CAPS_CELL)
        assertEquals(badWifi, rank(badWifi, neverValidatedWifi, cell))
    }

    @Test
    fun testYieldToBadWiFi_oneCellOneBadWiFiOneGoodWiFi() {
        // Good wifi wins
        val goodWifi = TestScore(score(EVER_EVALUATED, EVER_VALIDATED, IS_VALIDATED), CAPS_WIFI)
        val badWifi = TestScore(score(EVER_EVALUATED, EVER_VALIDATED, PRIMARY), CAPS_WIFI)
        val cell = TestScore(score(EVER_EVALUATED, YIELD_TO_BAD_WIFI, IS_VALIDATED), CAPS_CELL)
        assertEquals(goodWifi, rank(goodWifi, badWifi, cell))
    }

    @Test
    fun testPreferBadWifi_oneCellOneBadWifiOneEvaluatingWifi() {
        val cell = TestScore(score(EVER_EVALUATED, YIELD_TO_BAD_WIFI, IS_VALIDATED), CAPS_CELL)
        val badWifi = TestScore(score(EVER_EVALUATED), CAPS_WIFI)
        val evaluatingWifi = TestScore(score(), CAPS_WIFI)
        val winner = if (activelyPreferBadWifi) badWifi else cell
        assertEquals(winner, rank(cell, badWifi, evaluatingWifi))
    }

    @Test
    fun testYieldToBadWiFi_twoCellsOneBadWiFi() {
        // Cell that doesn't yield wins over cell that yields and bad wifi
        val cellNotYield = TestScore(score(EVER_EVALUATED, IS_VALIDATED), CAPS_CELL)
        val badWifi = TestScore(score(EVER_EVALUATED, EVER_VALIDATED, PRIMARY), CAPS_WIFI)
        val cellYield = TestScore(score(EVER_EVALUATED, YIELD_TO_BAD_WIFI, IS_VALIDATED), CAPS_CELL)
        assertEquals(cellNotYield, rank(cellNotYield, badWifi, cellYield))
    }

    @Test
    fun testYieldToBadWiFi_twoCellsOneBadWiFiOneGoodWiFi() {
        // Good wifi wins over cell that doesn't yield and cell that yields
        val goodWifi = TestScore(score(EVER_EVALUATED, IS_VALIDATED), CAPS_WIFI)
        val badWifi = TestScore(score(EVER_EVALUATED, EVER_VALIDATED, PRIMARY), CAPS_WIFI)
        val cellNotYield = TestScore(score(EVER_EVALUATED, IS_VALIDATED), CAPS_CELL)
        val cellYield = TestScore(score(EVER_EVALUATED, YIELD_TO_BAD_WIFI, IS_VALIDATED), CAPS_CELL)
        assertEquals(goodWifi, rank(goodWifi, badWifi, cellNotYield, cellYield))
    }

    @Test
    fun testYieldToBadWiFi_oneExitingGoodWiFi() {
        // Yielding cell wins over good exiting wifi
        val cell = TestScore(score(EVER_EVALUATED, YIELD_TO_BAD_WIFI, IS_VALIDATED), CAPS_CELL)
        val exitingWifi = TestScore(score(EVER_EVALUATED, IS_VALIDATED, EXITING), CAPS_WIFI)
        assertEquals(cell, rank(cell, exitingWifi))
    }

    @Test
    fun testYieldToBadWiFi_oneExitingBadWiFi() {
        // Yielding cell wins over bad exiting wifi
        val cell = TestScore(score(EVER_EVALUATED, YIELD_TO_BAD_WIFI, IS_VALIDATED), CAPS_CELL)
        val badExitingWifi = TestScore(score(EVER_EVALUATED, EVER_VALIDATED, EXITING), CAPS_WIFI)
        assertEquals(cell, rank(cell, badExitingWifi))
    }
}
