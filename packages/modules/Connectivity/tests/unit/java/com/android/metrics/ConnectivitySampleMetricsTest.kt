package com.android.metrics

import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.CONNECTIVITY_MANAGED_CAPABILITIES
import android.net.NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL
import android.net.NetworkCapabilities.NET_CAPABILITY_ENTERPRISE
import android.net.NetworkCapabilities.NET_CAPABILITY_IMS
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED
import android.net.NetworkCapabilities.NET_CAPABILITY_PARTIAL_CONNECTIVITY
import android.net.NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.NET_ENTERPRISE_ID_1
import android.net.NetworkCapabilities.NET_ENTERPRISE_ID_3
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkScore
import android.net.NetworkScore.KEEP_CONNECTED_FOR_TEST
import android.net.NetworkScore.POLICY_EXITING
import android.net.NetworkScore.POLICY_TRANSPORT_PRIMARY
import android.os.Build
import android.os.Handler
import android.stats.connectivity.MeteredState
import android.stats.connectivity.ValidatedState
import androidx.test.filters.SmallTest
import com.android.net.module.util.BitUtils
import com.android.server.CSTest
import com.android.server.FromS
import com.android.server.connectivity.FullScore
import com.android.server.connectivity.FullScore.POLICY_IS_UNMETERED
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.fail

private fun <T> Handler.onHandler(f: () -> T): T {
    val future = CompletableFuture<T>()
    post { future.complete(f()) }
    return future.get()
}

private fun flags(vararg flags: Int) = flags.fold(0L) { acc, it -> acc or (1L shl it) }

private fun Number.toTransportsString() = StringBuilder().also { sb ->
    BitUtils.appendStringRepresentationOfBitMaskToStringBuilder(sb, this.toLong(),
            { NetworkCapabilities.transportNameOf(it) }, "|") }.toString()

private fun Number.toCapsString() = StringBuilder().also { sb ->
    BitUtils.appendStringRepresentationOfBitMaskToStringBuilder(sb, this.toLong(),
            { NetworkCapabilities.capabilityNameOf(it) }, "&") }.toString()

private fun Number.toPolicyString() = StringBuilder().also {sb ->
    BitUtils.appendStringRepresentationOfBitMaskToStringBuilder(sb, this.toLong(),
            { FullScore.policyNameOf(it) }, "|") }.toString()

private fun Number.exceptCSManaged() = this.toLong() and CONNECTIVITY_MANAGED_CAPABILITIES.inv()

private val NetworkCapabilities.meteredState get() = when {
    hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED) ->
        MeteredState.METERED_TEMPORARILY_UNMETERED
    hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ->
        MeteredState.METERED_NO
    else ->
        MeteredState.METERED_YES
}

private val NetworkCapabilities.validatedState get() = when {
    hasCapability(NET_CAPABILITY_CAPTIVE_PORTAL) -> ValidatedState.VS_PORTAL
    hasCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY) -> ValidatedState.VS_PARTIAL
    hasCapability(NET_CAPABILITY_VALIDATED) -> ValidatedState.VS_VALID
    else -> ValidatedState.VS_INVALID
}

@RunWith(DevSdkIgnoreRunner::class)
@SmallTest
@IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
class ConnectivitySampleMetricsTest : CSTest() {
    @Test
    fun testSampleConnectivityState() {
        val wifi1Caps = NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .addCapability(NET_CAPABILITY_NOT_METERED)
                .addCapability(NET_CAPABILITY_NOT_SUSPENDED)
                .addCapability(NET_CAPABILITY_NOT_ROAMING)
                .build()
        val wifi1Score = NetworkScore.Builder()
                .setKeepConnectedReason(KEEP_CONNECTED_FOR_TEST)
                .setExiting(true)
                .build()
        val agentWifi1 = Agent(nc = wifi1Caps, score = FromS(wifi1Score)).also { it.connect() }

        val wifi2Caps = NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .addCapability(NET_CAPABILITY_ENTERPRISE)
                .addCapability(NET_CAPABILITY_NOT_SUSPENDED)
                .addCapability(NET_CAPABILITY_NOT_ROAMING)
                .addEnterpriseId(NET_ENTERPRISE_ID_3)
                .build()
        val wifi2Score = NetworkScore.Builder()
                .setKeepConnectedReason(KEEP_CONNECTED_FOR_TEST)
                .setTransportPrimary(true)
                .build()
        val agentWifi2 = Agent(nc = wifi2Caps, score = FromS(wifi2Score)).also { it.connect() }

        val cellCaps = NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_IMS)
                .addCapability(NET_CAPABILITY_ENTERPRISE)
                .addCapability(NET_CAPABILITY_NOT_SUSPENDED)
                .addCapability(NET_CAPABILITY_NOT_ROAMING)
                .addEnterpriseId(NET_ENTERPRISE_ID_1)
                .build()
        val cellScore = NetworkScore.Builder()
                .setKeepConnectedReason(KEEP_CONNECTED_FOR_TEST)
                .build()
        val agentCell = Agent(nc = cellCaps, score = FromS(cellScore)).also { it.connect() }

        val stats = csHandler.onHandler { service.sampleConnectivityState() }
        assertEquals(3, stats.networks.networkDescriptionList.size)
        val foundCell = stats.networks.networkDescriptionList.find {
            it.transportTypes == (1 shl TRANSPORT_CELLULAR)
        } ?: fail("Can't find cell network (searching by transport)")
        val foundWifi1 = stats.networks.networkDescriptionList.find {
            it.transportTypes == (1 shl TRANSPORT_WIFI) &&
                    0L != (it.capabilities and (1L shl NET_CAPABILITY_NOT_METERED))
        } ?: fail("Can't find wifi1 (searching by WIFI transport and the NOT_METERED capability)")
        val foundWifi2 = stats.networks.networkDescriptionList.find {
            it.transportTypes == (1 shl TRANSPORT_WIFI) &&
                    0L != (it.capabilities and (1L shl NET_CAPABILITY_ENTERPRISE))
        } ?: fail("Can't find wifi2 (searching by WIFI transport and the ENTERPRISE capability)")

        fun checkNetworkDescription(
                network: String,
                found: NetworkDescription,
                expected: NetworkCapabilities
        ) {
            assertEquals(expected.transportTypesInternal, found.transportTypes.toLong(),
                    "Transports differ for network $network, " +
                            "expected ${expected.transportTypesInternal.toTransportsString()}, " +
                            "found ${found.transportTypes.toTransportsString()}")
            val expectedCaps = expected.capabilitiesInternal.exceptCSManaged()
            val foundCaps = found.capabilities.exceptCSManaged()
            assertEquals(expectedCaps, foundCaps,
                    "Capabilities differ for network $network, " +
                            "expected ${expectedCaps.toCapsString()}, " +
                            "found ${foundCaps.toCapsString()}")
            assertEquals(expected.enterpriseIdsInternal, found.enterpriseId,
                    "Enterprise IDs differ for network $network, " +
                            "expected ${expected.enterpriseIdsInternal}," +
                            " found ${found.enterpriseId}")
            assertEquals(expected.meteredState, found.meteredState,
                    "Metered states differ for network $network, " +
                            "expected ${expected.meteredState}, " +
                            "found ${found.meteredState}")
            assertEquals(expected.validatedState, found.validatedState,
                    "Validated states differ for network $network, " +
                            "expected ${expected.validatedState}, " +
                            "found ${found.validatedState}")
        }

        checkNetworkDescription("Cell network", foundCell, cellCaps)
        checkNetworkDescription("Wifi1", foundWifi1, wifi1Caps)
        checkNetworkDescription("Wifi2", foundWifi2, wifi2Caps)

        assertEquals(0, foundCell.scorePolicies, "Cell score policies incorrect, expected 0, " +
                        "found ${foundCell.scorePolicies.toPolicyString()}")
        val expectedWifi1Policies = flags(POLICY_EXITING, POLICY_IS_UNMETERED)
        assertEquals(expectedWifi1Policies, foundWifi1.scorePolicies,
                "Wifi1 score policies incorrect, " +
                        "expected ${expectedWifi1Policies.toPolicyString()}, " +
                        "found ${foundWifi1.scorePolicies.toPolicyString()}")
        val expectedWifi2Policies = flags(POLICY_TRANSPORT_PRIMARY)
        assertEquals(expectedWifi2Policies, foundWifi2.scorePolicies,
                "Wifi2 score policies incorrect, " +
                        "expected ${expectedWifi2Policies.toPolicyString()}, " +
                        "found ${foundWifi2.scorePolicies.toPolicyString()}")
    }
}
