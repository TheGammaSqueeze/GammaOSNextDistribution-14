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
 * limitations under the License
 */

package com.android.server.net.integrationtests

import android.Manifest.permission.MANAGE_TEST_NETWORKS
import android.annotation.TargetApi
import android.app.usage.NetworkStats
import android.app.usage.NetworkStats.Bucket
import android.app.usage.NetworkStats.Bucket.TAG_NONE
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.TYPE_TEST
import android.net.InetAddresses
import android.net.IpPrefix
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.NetworkTemplate
import android.net.NetworkTemplate.MATCH_TEST
import android.net.TestNetworkSpecifier
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.net.integrationtests.NetworkStatsIntegrationTest.Direction.DOWNLOAD
import com.android.server.net.integrationtests.NetworkStatsIntegrationTest.Direction.UPLOAD
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.PacketBridge
import com.android.testutils.RecorderCallback.CallbackEntry.LinkPropertiesChanged
import com.android.testutils.TestDnsServer
import com.android.testutils.TestHttpServer
import com.android.testutils.TestableNetworkCallback
import com.android.testutils.runAsShell
import fi.iki.elonen.NanoHTTPD
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_OK
import java.net.InetSocketAddress
import java.net.URL
import java.nio.charset.Charset
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_TAG = 0xF00D

@RunWith(DevSdkIgnoreRunner::class)
@TargetApi(Build.VERSION_CODES.S)
@IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
class NetworkStatsIntegrationTest {
    private val TAG = NetworkStatsIntegrationTest::class.java.simpleName
    private val LOCAL_V6ADDR =
        LinkAddress(InetAddresses.parseNumericAddress("2001:db8::1234"), 64)

    // Remote address, both the client and server will have a hallucination that
    // they are talking to this address.
    private val REMOTE_V6ADDR =
        LinkAddress(InetAddresses.parseNumericAddress("dead:beef::808:808"), 64)
    private val REMOTE_V4ADDR =
        LinkAddress(InetAddresses.parseNumericAddress("8.8.8.8"), 32)
    private val DEFAULT_MTU = 1500
    private val DEFAULT_BUFFER_SIZE = 1500 // Any size greater than or equal to mtu
    private val CONNECTION_TIMEOUT_MILLIS = 15000
    private val TEST_DOWNLOAD_SIZE = 10000L
    private val TEST_UPLOAD_SIZE = 20000L
    private val HTTP_SERVER_NAME = "test.com"
    private val HTTP_SERVER_PORT = 8080 // Use port > 1024 to avoid restrictions on system ports
    private val DNS_INTERNAL_SERVER_PORT = 53
    private val DNS_EXTERNAL_SERVER_PORT = 1053
    private val TCP_ACK_SIZE = 72

    // Packet overheads that are not part of the actual data transmission, these
    // include DNS packets, TCP handshake/termination packets, and HTTP header
    // packets. These overheads were gathered from real samples and may not
    // be perfectly accurate because of DNS caches and TCP retransmissions, etc.
    private val CONSTANT_PACKET_OVERHEAD = 8

    // 130 is an observed average.
    private val CONSTANT_BYTES_OVERHEAD = 130 * CONSTANT_PACKET_OVERHEAD
    private val TOLERANCE = 1.3

    // Set up the packet bridge with two IPv6 address only test networks.
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.getContext()
    private val packetBridge = runAsShell(MANAGE_TEST_NETWORKS) {
        PacketBridge(
            context,
            listOf(LOCAL_V6ADDR),
            REMOTE_V6ADDR.address,
            listOf(
                Pair(DNS_INTERNAL_SERVER_PORT, DNS_EXTERNAL_SERVER_PORT)
            )
        )
    }
    private val cm = context.getSystemService(ConnectivityManager::class.java)!!

    // Set up DNS server for testing server and DNS64.
    private val fakeDns = TestDnsServer(
        packetBridge.externalNetwork,
        InetSocketAddress(LOCAL_V6ADDR.address, DNS_EXTERNAL_SERVER_PORT)
    ).apply {
        start()
        setAnswer(
            "ipv4only.arpa",
            listOf(IpPrefix(REMOTE_V6ADDR.address, REMOTE_V6ADDR.prefixLength).address)
        )
        setAnswer(HTTP_SERVER_NAME, listOf(REMOTE_V4ADDR.address))
    }

    // Start up test http server.
    private val httpServer = TestHttpServer(
        LOCAL_V6ADDR.address.hostAddress,
        HTTP_SERVER_PORT
    ).apply {
        start()
    }

    @Before
    fun setUp() {
        assumeTrue(shouldRunTests())
        packetBridge.start()
    }

    // For networkstack tests, it is not guaranteed that the tethering module will be
    // updated at the same time. If the tethering module is not new enough, it may not contain
    // the necessary abilities to run these tests. For example, The tests depends on test
    // network stats being counted, which can only be achieved when they are marked as TYPE_TEST.
    // If the tethering module does not support TYPE_TEST stats, then these tests will need
    // to be skipped.
    fun shouldRunTests() = cm.getNetworkInfo(packetBridge.internalNetwork)!!.type == TYPE_TEST

    @After
    fun tearDown() {
        packetBridge.stop()
        fakeDns.stop()
        httpServer.stop()
    }

    private fun waitFor464XlatReady(network: Network): String {
        val iface = cm.getLinkProperties(network)!!.interfaceName!!

        // Make a network request to listen to the specific test network.
        val nr = NetworkRequest.Builder()
            .clearCapabilities()
            .addTransportType(NetworkCapabilities.TRANSPORT_TEST)
            .setNetworkSpecifier(TestNetworkSpecifier(iface))
            .build()
        val testCb = TestableNetworkCallback()
        cm.registerNetworkCallback(nr, testCb)

        // Wait for the stacked address to be available.
        testCb.eventuallyExpect<LinkPropertiesChanged> {
            it.lp.stackedLinks.getOrNull(0)?.linkAddresses?.getOrNull(0) != null
        }

        return iface
    }

    private val Network.mtu: Int get() {
        val lp = cm.getLinkProperties(this)!!
        val mtuStacked = if (lp.stackedLinks[0]?.mtu != 0) lp.stackedLinks[0].mtu else DEFAULT_MTU
        val mtuInterface = if (lp.mtu != 0) lp.mtu else DEFAULT_MTU
        return mtuInterface.coerceAtMost(mtuStacked)
    }

    /**
     * Verify data usage download stats with test 464xlat networks.
     *
     * This test starts two test networks and binds them together, the internal one is for the
     * client to make http traffic on the test network, and the external one is for the mocked
     * http and dns server to bind to and provide responses.
     *
     * After Clat setup, the client will use clat v4 address to send packets to the mocked
     * server v4 address, which will be translated into a v6 packet by the clat daemon with
     * NAT64 prefix learned from the mocked DNS64 response. And send to the interface.
     *
     * While the packets are being forwarded to the external interface, the servers will see
     * the packets originated from the mocked v6 address, and destined to a local v6 address.
     */
    @Test
    fun test464XlatTcpStats() {
        // Wait for 464Xlat to be ready.
        val internalInterfaceName = waitFor464XlatReady(packetBridge.internalNetwork)
        val mtu = packetBridge.internalNetwork.mtu

        val snapshotBeforeTest = StatsSnapshot(context, internalInterfaceName)

        // Generate the download traffic.
        genHttpTraffic(packetBridge.internalNetwork, uploadSize = 0L, TEST_DOWNLOAD_SIZE)

        // In practice, for one way 10k download payload, the download usage is about
        // 11222~12880 bytes, with 14~17 packets. And the upload usage is about 1279~1626 bytes
        // with 14~17 packets, which is majorly contributed by TCP ACK packets.
        val snapshotAfterDownload = StatsSnapshot(context, internalInterfaceName)
        val (expectedDownloadLower, expectedDownloadUpper) = getExpectedStatsBounds(
            TEST_DOWNLOAD_SIZE,
            mtu,
            DOWNLOAD
        )
        assertOnlyNonTaggedStatsIncreases(
            snapshotBeforeTest,
            snapshotAfterDownload,
            expectedDownloadLower,
            expectedDownloadUpper
        )

        // Generate upload traffic with tag to verify tagged data accounting as well.
        genHttpTrafficWithTag(
            packetBridge.internalNetwork,
            TEST_UPLOAD_SIZE,
            downloadSize = 0L,
            TEST_TAG
        )

        // Verify upload data usage accounting.
        val snapshotAfterUpload = StatsSnapshot(context, internalInterfaceName)
        val (expectedUploadLower, expectedUploadUpper) = getExpectedStatsBounds(
            TEST_UPLOAD_SIZE,
            mtu,
            UPLOAD
        )
        assertAllStatsIncreases(
            snapshotAfterDownload,
            snapshotAfterUpload,
            expectedUploadLower,
            expectedUploadUpper
        )
    }

    private enum class Direction {
        DOWNLOAD,
        UPLOAD
    }

    private fun getExpectedStatsBounds(
        transmittedSize: Long,
        mtu: Int,
        direction: Direction
    ): Pair<BareStats, BareStats> {
        // This is already an underestimated value since the input doesn't include TCP/IP
        // layer overhead.
        val txBytesLower = transmittedSize
        // Include TCP/IP header overheads and retransmissions in the upper bound.
        val txBytesUpper = (transmittedSize * TOLERANCE).toLong()
        val txPacketsLower = txBytesLower / mtu + (CONSTANT_PACKET_OVERHEAD / TOLERANCE).toLong()
        val estTransmissionPacketsUpper = ceil(txBytesUpper / mtu.toDouble()).toLong()
        val txPacketsUpper = estTransmissionPacketsUpper +
                (CONSTANT_PACKET_OVERHEAD * TOLERANCE).toLong()
        // Assume ACK only sent once for the entire transmission.
        val rxPacketsLower = 1L + (CONSTANT_PACKET_OVERHEAD / TOLERANCE).toLong()
        // Assume ACK sent for every RX packet.
        val rxPacketsUpper = txPacketsUpper
        val rxBytesLower = 1L * TCP_ACK_SIZE + (CONSTANT_BYTES_OVERHEAD / TOLERANCE).toLong()
        val rxBytesUpper = estTransmissionPacketsUpper * TCP_ACK_SIZE +
                (CONSTANT_BYTES_OVERHEAD * TOLERANCE).toLong()

        return if (direction == UPLOAD) {
            BareStats(rxBytesLower, rxPacketsLower, txBytesLower, txPacketsLower) to
                    BareStats(rxBytesUpper, rxPacketsUpper, txBytesUpper, txPacketsUpper)
        } else {
            BareStats(txBytesLower, txPacketsLower, rxBytesLower, rxPacketsLower) to
                    BareStats(txBytesUpper, txPacketsUpper, rxBytesUpper, rxPacketsUpper)
        }
    }

    private fun genHttpTraffic(network: Network, uploadSize: Long, downloadSize: Long) =
        genHttpTrafficWithTag(network, uploadSize, downloadSize, NetworkStats.Bucket.TAG_NONE)

    private fun genHttpTrafficWithTag(
        network: Network,
        uploadSize: Long,
        downloadSize: Long,
        tag: Int
    ) {
        val path = "/test_upload_download"
        val buf = ByteArray(DEFAULT_BUFFER_SIZE)

        httpServer.addResponse(
            TestHttpServer.Request(path, NanoHTTPD.Method.POST), NanoHTTPD.Response.Status.OK,
            content = getRandomString(downloadSize)
        )
        var httpConnection: HttpURLConnection? = null
        try {
            TrafficStats.setThreadStatsTag(tag)
            val spec = "http://$HTTP_SERVER_NAME:${httpServer.listeningPort}$path"
            val url = URL(spec)
            httpConnection = network.openConnection(url) as HttpURLConnection
            httpConnection.connectTimeout = CONNECTION_TIMEOUT_MILLIS
            httpConnection.requestMethod = "POST"
            httpConnection.doOutput = true
            // Tell the server that the response should not be compressed. Otherwise, the data usage
            // accounted will be less than expected.
            httpConnection.setRequestProperty("Accept-Encoding", "identity")
            // Tell the server that to close connection after this request, this is needed to
            // prevent from reusing the same socket that has different tagging requirement.
            httpConnection.setRequestProperty("Connection", "close")

            // Send http body.
            val outputStream = BufferedOutputStream(httpConnection.outputStream)
            outputStream.write(getRandomString(uploadSize).toByteArray(Charset.forName("UTF-8")))
            outputStream.close()
            assertEquals(HTTP_OK, httpConnection.responseCode)

            // Receive response from the server.
            val inputStream = BufferedInputStream(httpConnection.getInputStream())
            var total = 0L
            while (true) {
                val count = inputStream.read(buf)
                if (count == -1) break // End-of-Stream
                total += count
            }
            assertEquals(downloadSize, total)
        } finally {
            httpConnection?.inputStream?.close()
            TrafficStats.clearThreadStatsTag()
        }
    }

    // NetworkStats.Bucket cannot be written. So another class is needed to
    // perform arithmetic operations.
    data class BareStats(
        val rxBytes: Long,
        val rxPackets: Long,
        val txBytes: Long,
        val txPackets: Long
    ) {
        operator fun plus(other: BareStats): BareStats {
            return BareStats(
                this.rxBytes + other.rxBytes, this.rxPackets + other.rxPackets,
                this.txBytes + other.txBytes, this.txPackets + other.txPackets
            )
        }

        operator fun minus(other: BareStats): BareStats {
            return BareStats(
                this.rxBytes - other.rxBytes, this.rxPackets - other.rxPackets,
                this.txBytes - other.txBytes, this.txPackets - other.txPackets
            )
        }

        fun reverse(): BareStats =
            BareStats(
                rxBytes = txBytes,
                rxPackets = txPackets,
                txBytes = rxBytes,
                txPackets = rxPackets
            )

        override fun toString(): String {
            return "BareStats{rx/txBytes=$rxBytes/$txBytes, rx/txPackets=$rxPackets/$txPackets}"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BareStats) return false

            if (rxBytes != other.rxBytes) return false
            if (rxPackets != other.rxPackets) return false
            if (txBytes != other.txBytes) return false
            if (txPackets != other.txPackets) return false

            return true
        }

        override fun hashCode(): Int {
            return (rxBytes * 11 + rxPackets * 13 + txBytes * 17 + txPackets * 19).toInt()
        }

        companion object {
            val EMPTY = BareStats(0L, 0L, 0L, 0L)
        }
    }

    data class StatsSnapshot(val context: Context, val iface: String) {
        val statsSummary = getNetworkSummary(iface)
        val statsUid = getUidDetail(iface, TAG_NONE)
        val taggedSummary = getTaggedNetworkSummary(iface, TEST_TAG)
        val taggedUid = getUidDetail(iface, TEST_TAG)
        val trafficStatsIface = getTrafficStatsIface(iface)
        val trafficStatsUid = getTrafficStatsUid(Process.myUid())

        private fun getUidDetail(iface: String, tag: Int): BareStats {
            return getNetworkStatsThat(iface, tag) { nsm, template ->
                nsm.queryDetailsForUidTagState(
                    template, Long.MIN_VALUE, Long.MAX_VALUE,
                    Process.myUid(), tag, Bucket.STATE_ALL
                )
            }
        }

        private fun getNetworkSummary(iface: String): BareStats {
            return getNetworkStatsThat(iface, TAG_NONE) { nsm, template ->
                nsm.querySummary(template, Long.MIN_VALUE, Long.MAX_VALUE)
            }
        }

        private fun getTaggedNetworkSummary(iface: String, tag: Int): BareStats {
            return getNetworkStatsThat(iface, tag) { nsm, template ->
                nsm.queryTaggedSummary(template, Long.MIN_VALUE, Long.MAX_VALUE)
            }
        }

        private fun getNetworkStatsThat(
            iface: String,
            tag: Int,
            queryApi: (nsm: NetworkStatsManager, template: NetworkTemplate) -> NetworkStats
        ): BareStats {
            val nsm = context.getSystemService(NetworkStatsManager::class.java)!!
            nsm.forceUpdate()
            val testTemplate = NetworkTemplate.Builder(MATCH_TEST)
                .setWifiNetworkKeys(setOf(iface)).build()
            val stats = queryApi.invoke(nsm, testTemplate)
            val filteredBuckets =
                stats.buckets().filter { it.uid == Process.myUid() && it.tag == tag }
            return filteredBuckets.fold(BareStats.EMPTY) { acc, it ->
                acc + BareStats(
                    it.rxBytes,
                    it.rxPackets,
                    it.txBytes,
                    it.txPackets
                )
            }
        }

        // Helper function to iterate buckets in app.usage.NetworkStats.
        private fun NetworkStats.buckets() = object : Iterable<NetworkStats.Bucket> {
            override fun iterator() = object : Iterator<NetworkStats.Bucket> {
                override operator fun hasNext() = hasNextBucket()
                override operator fun next() =
                    NetworkStats.Bucket().also { assertTrue(getNextBucket(it)) }
            }
        }

        private fun getTrafficStatsIface(iface: String): BareStats = BareStats(
            TrafficStats.getRxBytes(iface),
            TrafficStats.getRxPackets(iface),
            TrafficStats.getTxBytes(iface),
            TrafficStats.getTxPackets(iface)
        )

        private fun getTrafficStatsUid(uid: Int): BareStats = BareStats(
            TrafficStats.getUidRxBytes(uid),
            TrafficStats.getUidRxPackets(uid),
            TrafficStats.getUidTxBytes(uid),
            TrafficStats.getUidTxPackets(uid)
        )
    }

    private fun assertAllStatsIncreases(
        before: StatsSnapshot,
        after: StatsSnapshot,
        lower: BareStats,
        upper: BareStats
    ) {
        assertNonTaggedStatsIncreases(before, after, lower, upper)
        assertTaggedStatsIncreases(before, after, lower, upper)
    }

    private fun assertOnlyNonTaggedStatsIncreases(
        before: StatsSnapshot,
        after: StatsSnapshot,
        lower: BareStats,
        upper: BareStats
    ) {
        assertNonTaggedStatsIncreases(before, after, lower, upper)
        assertTaggedStatsEquals(before, after)
    }

    private fun assertNonTaggedStatsIncreases(
        before: StatsSnapshot,
        after: StatsSnapshot,
        lower: BareStats,
        upper: BareStats
    ) {
        assertInRange(
            "Unexpected iface traffic stats",
            after.iface,
            before.trafficStatsIface, after.trafficStatsIface,
            lower, upper
        )
        // Uid traffic stats are counted in both direction because the external network
        // traffic is also attributed to the test uid.
        assertInRange(
            "Unexpected uid traffic stats",
            after.iface,
            before.trafficStatsUid, after.trafficStatsUid,
            lower + lower.reverse(), upper + upper.reverse()
        )
        assertInRange(
            "Unexpected non-tagged summary stats",
            after.iface,
            before.statsSummary, after.statsSummary,
            lower, upper
        )
        assertInRange(
            "Unexpected non-tagged uid stats",
            after.iface,
            before.statsUid, after.statsUid,
            lower, upper
        )
    }

    private fun assertTaggedStatsEquals(before: StatsSnapshot, after: StatsSnapshot) {
        // Increment of tagged data should be zero since no tagged traffic was generated.
        assertEquals(
            before.taggedSummary,
            after.taggedSummary,
            "Unexpected tagged summary stats: ${after.iface}"
        )
        assertEquals(
            before.taggedUid,
            after.taggedUid,
            "Unexpected tagged uid stats: ${Process.myUid()} on ${after.iface}"
        )
    }

    private fun assertTaggedStatsIncreases(
        before: StatsSnapshot,
        after: StatsSnapshot,
        lower: BareStats,
        upper: BareStats
    ) {
        assertInRange(
            "Unexpected tagged summary stats",
            after.iface,
            before.taggedSummary, after.taggedSummary,
            lower,
            upper
        )
        assertInRange(
            "Unexpected tagged uid stats: ${Process.myUid()}",
            after.iface,
            before.taggedUid, after.taggedUid,
            lower,
            upper
        )
    }

    /** Verify the given BareStats is in range [lower, upper] */
    private fun assertInRange(
        tag: String,
        iface: String,
        before: BareStats,
        after: BareStats,
        lower: BareStats,
        upper: BareStats
    ) {
        // Passing the value after operation and the value before operation to dump the actual
        // numbers if it fails.
        assertTrue(checkInRange(before, after, lower, upper),
            "$tag on $iface: $after - $before is not within range [$lower, $upper]"
        )
    }

    private fun checkInRange(
            before: BareStats,
            after: BareStats,
            lower: BareStats,
            upper: BareStats
    ): Boolean {
        val value = after - before
        return value.rxBytes in lower.rxBytes..upper.rxBytes &&
                value.rxPackets in lower.rxPackets..upper.rxPackets &&
                value.txBytes in lower.txBytes..upper.txBytes &&
                value.txPackets in lower.txPackets..upper.txPackets
    }

    fun getRandomString(length: Long): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }
}
