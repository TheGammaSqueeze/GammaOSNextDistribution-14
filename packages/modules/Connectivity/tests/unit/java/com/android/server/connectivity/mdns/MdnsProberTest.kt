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

package com.android.server.connectivity.mdns

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.android.internal.util.HexDump
import com.android.net.module.util.SharedLog
import com.android.server.connectivity.mdns.MdnsProber.ProbingInfo
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import java.net.DatagramPacket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

private const val TEST_TIMEOUT_MS = 10_000L
private const val SHORT_TIMEOUT_MS = 200L

private val TEST_SERVICE_NAME_1 = arrayOf("testservice", "_nmt", "_tcp", "local")
private val TEST_SERVICE_NAME_2 = arrayOf("testservice2", "_nmt", "_tcp", "local")
private val TEST_SERVICE_NAME_3 = arrayOf("Testservice", "_nmt", "_tcp", "local")

@RunWith(DevSdkIgnoreRunner::class)
@IgnoreUpTo(Build.VERSION_CODES.S_V2)
class MdnsProberTest {
    private val thread = HandlerThread(MdnsProberTest::class.simpleName)
    private val socket = mock(MdnsInterfaceSocket::class.java)
    private val sharedLog = mock(SharedLog::class.java)
    @Suppress("UNCHECKED_CAST")
    private val cb = mock(MdnsPacketRepeater.PacketRepeaterCallback::class.java)
        as MdnsPacketRepeater.PacketRepeaterCallback<ProbingInfo>
    private val buffer = ByteArray(1500)
    private val flags = MdnsFeatureFlags.newBuilder().build()

    @Before
    fun setUp() {
        doReturn(true).`when`(socket).hasJoinedIpv6()
        thread.start()
    }

    @After
    fun tearDown() {
        thread.quitSafely()
        thread.join()
    }

    private class TestProbeInfo(probeRecords: List<MdnsRecord>, private val delayMs: Long = 1L) :
            ProbingInfo(1 /* serviceId */, probeRecords) {
        // Just send the packets quickly. Timing-related tests for MdnsPacketRepeater are already
        // done in MdnsAnnouncerTest.
        override fun getDelayMs(nextIndex: Int) = delayMs
    }

    private class TestProber(
        looper: Looper,
        replySender: MdnsReplySender,
        cb: PacketRepeaterCallback<ProbingInfo>,
        sharedLog: SharedLog
    ) : MdnsProber(looper, replySender, cb, sharedLog) {
        override fun getInitialDelay() = 0L
    }

    private fun assertProbesSent(probeInfo: TestProbeInfo, expectedHex: String) {
        repeat(probeInfo.numSends) { i ->
            verify(cb, timeout(TEST_TIMEOUT_MS)).onSent(i, probeInfo, 1 /* sentPacketCount */)
            // If the probe interval is short, more than (i+1) probes may have been sent already
            verify(socket, atLeast(i + 1)).send(any())
        }

        val captor = ArgumentCaptor.forClass(DatagramPacket::class.java)
        // There should be exactly numSends probes sent at the end
        verify(socket, times(probeInfo.numSends)).send(captor.capture())

        captor.allValues.forEach {
            assertEquals(expectedHex, HexDump.toHexString(it.data))
        }
        verify(cb, timeout(TEST_TIMEOUT_MS)).onFinished(probeInfo)
    }

    private fun makeServiceRecord(name: Array<String>, port: Int) = MdnsServiceRecord(
            name,
            0L /* receiptTimeMillis */,
            false /* cacheFlush */,
            120_000L /* ttlMillis */,
            0 /* servicePriority */,
            0 /* serviceWeight */,
            port,
            arrayOf("myhostname", "local"))

    @Test
    fun testProbe() {
        val replySender = MdnsReplySender(
                thread.looper, socket, buffer, sharedLog, true /* enableDebugLog */, flags)
        val prober = TestProber(thread.looper, replySender, cb, sharedLog)
        val probeInfo = TestProbeInfo(
                listOf(makeServiceRecord(TEST_SERVICE_NAME_1, 37890)))
        prober.startProbing(probeInfo)

        // Inspect with python3:
        // import scapy.all as scapy; scapy.DNS(bytes.fromhex('[bytes]')).show2()
        val expected = "0000000000010000000100000B7465737473657276696365045F6E6D74045F746370056C" +
                "6F63616C0000FF0001C00C002100010000007800130000000094020A6D79686F73746E616D65C022"
        assertProbesSent(probeInfo, expected)
    }

    @Test
    fun testCreateProberCaseInsensitive() {
        val probeInfo = TestProbeInfo(
            listOf(makeServiceRecord(TEST_SERVICE_NAME_1, 37890),
                makeServiceRecord(TEST_SERVICE_NAME_2, 37890),
                makeServiceRecord(TEST_SERVICE_NAME_3, 37890)))
        assertEquals(2, probeInfo.getPacket(0).questions.size)
    }

    @Test
    fun testProbeMultipleRecords() {
        val replySender = MdnsReplySender(
                thread.looper, socket, buffer, sharedLog, true /* enableDebugLog */, flags)
        val prober = TestProber(thread.looper, replySender, cb, sharedLog)
        val probeInfo = TestProbeInfo(listOf(
                makeServiceRecord(TEST_SERVICE_NAME_1, 37890),
                makeServiceRecord(TEST_SERVICE_NAME_2, 37891),
                MdnsTextRecord(
                        // Same name as the first record; there should not be 2 duplicated questions
                        TEST_SERVICE_NAME_1,
                        0L /* receiptTimeMillis */,
                        false /* cacheFlush */,
                        120_000L /* ttlMillis */,
                        listOf(MdnsServiceInfo.TextEntry("testKey", "testValue")))))
        prober.startProbing(probeInfo)

        /*
        Expected data obtained with:
        scapy.raw(scapy.dns_compress(scapy.DNS(rd=0,
            qd =
                scapy.DNSQR(qname='testservice._nmt._tcp.local.', qtype='ALL') /
                scapy.DNSQR(qname='testservice2._nmt._tcp.local.', qtype='ALL'),
            ns=
                scapy.DNSRRSRV(rrname='testservice._nmt._tcp.local.', type='SRV', ttl=120,
                    port=37890, target='myhostname.local.') /
                scapy.DNSRRSRV(rrname='testservice2._nmt._tcp.local.', type='SRV', ttl=120,
                    port=37891, target='myhostname.local.') /
                scapy.DNSRR(type='TXT', ttl=120, rrname='testservice._nmt._tcp.local.',
                    rdata='testKey=testValue'))
        )).hex().upper()
         */
        val expected = "0000000000020000000300000B7465737473657276696365045F6E6D74045F746370056C6" +
                "F63616C0000FF00010C746573747365727669636532C01800FF0001C00C002100010000007800130" +
                "000000094020A6D79686F73746E616D65C022C02D00210001000000780008000000009403C052C00" +
                "C0010000100000078001211746573744B65793D7465737456616C7565"
        assertProbesSent(probeInfo, expected)
    }

    @Test
    fun testStopProbing() {
        val replySender = MdnsReplySender(
                thread.looper, socket, buffer, sharedLog, true /* enableDebugLog */, flags)
        val prober = TestProber(thread.looper, replySender, cb, sharedLog)
        val probeInfo = TestProbeInfo(
                listOf(makeServiceRecord(TEST_SERVICE_NAME_1, 37890)),
                // delayMs is the delay between each probe, so does not apply to the first one
                delayMs = SHORT_TIMEOUT_MS)
        prober.startProbing(probeInfo)

        // Expect the initial probe
        verify(cb, timeout(TEST_TIMEOUT_MS)).onSent(0, probeInfo, 1 /* sentPacketCount */)

        // Stop probing
        val stopResult = CompletableFuture<Boolean>()
        Handler(thread.looper).post { stopResult.complete(prober.stop(probeInfo.serviceId)) }
        assertTrue(stopResult.get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "stop should return true when probing was in progress")

        // Wait for a bit (more than the probe delay) to ensure no more probes were sent
        Thread.sleep(SHORT_TIMEOUT_MS * 2)
        verify(cb, never()).onSent(1, probeInfo, 1 /* sentPacketCount */)
        verify(cb, never()).onFinished(probeInfo)

        // Only one sent packet
        verify(socket, times(1)).send(any())
    }
}
