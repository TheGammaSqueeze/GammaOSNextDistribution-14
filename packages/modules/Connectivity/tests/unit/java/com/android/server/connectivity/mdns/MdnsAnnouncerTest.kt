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

import android.net.InetAddresses.parseNumericAddress
import android.os.Build
import android.os.HandlerThread
import android.os.SystemClock
import com.android.internal.util.HexDump
import com.android.net.module.util.SharedLog
import com.android.server.connectivity.mdns.MdnsAnnouncer.AnnouncementInfo
import com.android.server.connectivity.mdns.MdnsAnnouncer.BaseAnnouncementInfo
import com.android.server.connectivity.mdns.MdnsRecordRepository.getReverseDnsAddress
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import java.net.DatagramPacket
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
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify

private const val FIRST_ANNOUNCES_DELAY = 100L
private const val FIRST_ANNOUNCES_COUNT = 2
private const val NEXT_ANNOUNCES_DELAY = 1L
private const val TEST_TIMEOUT_MS = 1000L

@RunWith(DevSdkIgnoreRunner::class)
@IgnoreUpTo(Build.VERSION_CODES.S_V2)
class MdnsAnnouncerTest {

    private val thread = HandlerThread(MdnsAnnouncerTest::class.simpleName)
    private val socket = mock(MdnsInterfaceSocket::class.java)
    private val sharedLog = mock(SharedLog::class.java)
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

    private class TestAnnouncementInfo(
        announcedRecords: List<MdnsRecord>,
        additionalRecords: List<MdnsRecord>
    ) : AnnouncementInfo(1 /* serviceId */, announcedRecords, additionalRecords) {
        override fun getDelayMs(nextIndex: Int) =
                if (nextIndex < FIRST_ANNOUNCES_COUNT) {
                    FIRST_ANNOUNCES_DELAY
                } else {
                    NEXT_ANNOUNCES_DELAY
                }
    }

    @Test
    fun testAnnounce() {
        val replySender = MdnsReplySender(
                thread.looper, socket, buffer, sharedLog, true /* enableDebugLog */, flags)
        @Suppress("UNCHECKED_CAST")
        val cb = mock(MdnsPacketRepeater.PacketRepeaterCallback::class.java)
                as MdnsPacketRepeater.PacketRepeaterCallback<BaseAnnouncementInfo>
        val announcer = MdnsAnnouncer(thread.looper, replySender, cb, sharedLog)
        /*
        The expected packet replicates records announced when registering a service, as observed in
        the legacy mDNS implementation (some ordering differs to be more readable).
        Obtained with scapy 2.5.0 RC3 (2.4.5 does not compress TLDs like .arpa properly) with:
        scapy.raw(scapy.dns_compress(scapy.DNS(rd=0, qr=1, aa=1,
        qd = None,
        an =
        scapy.DNSRR(type='PTR', rrname='123.2.0.192.in-addr.arpa.', rdata='Android.local',
            rclass=0x8001, ttl=120) /
        scapy.DNSRR(type='PTR',
            rrname='3.2.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.B.D.0.1.0.0.2.ip6.arpa',
            rdata='Android.local', rclass=0x8001, ttl=120) /
        scapy.DNSRR(type='PTR',
            rrname='6.5.4.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.B.D.0.1.0.0.2.ip6.arpa',
            rdata='Android.local', rclass=0x8001, ttl=120) /
        scapy.DNSRR(type='PTR', rrname='_testtype._tcp.local',
            rdata='testservice._testtype._tcp.local', rclass='IN', ttl=4500) /
	    scapy.DNSRRSRV(rrname='testservice._testtype._tcp.local', rclass=0x8001, port=31234,
	        target='Android.local', ttl=120) /
	    scapy.DNSRR(type='TXT', rrname='testservice._testtype._tcp.local', rclass=0x8001, rdata='',
	        ttl=4500) /
        scapy.DNSRR(type='A', rrname='Android.local', rclass=0x8001, rdata='192.0.2.123', ttl=120) /
        scapy.DNSRR(type='AAAA', rrname='Android.local', rclass=0x8001, rdata='2001:db8::123',
            ttl=120) /
        scapy.DNSRR(type='AAAA', rrname='Android.local', rclass=0x8001, rdata='2001:db8::456',
            ttl=120),
        ar =
        scapy.DNSRRNSEC(rrname='123.2.0.192.in-addr.arpa.', rclass=0x8001, ttl=120,
            nextname='123.2.0.192.in-addr.arpa.', typebitmaps=[12]) /
        scapy.DNSRRNSEC(
            rrname='3.2.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.B.D.0.1.0.0.2.ip6.arpa',
            rclass=0x8001, ttl=120,
            nextname='3.2.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.B.D.0.1.0.0.2.ip6.arpa',
            typebitmaps=[12]) /
        scapy.DNSRRNSEC(
            rrname='6.5.4.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.B.D.0.1.0.0.2.ip6.arpa',
            rclass=0x8001, ttl=120,
            nextname='6.5.4.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.B.D.0.1.0.0.2.ip6.arpa',
            typebitmaps=[12]) /
	    scapy.DNSRRNSEC(
	        rrname='testservice._testtype._tcp.local', rclass=0x8001, ttl=4500,
	        nextname='testservice._testtype._tcp.local', typebitmaps=[16, 33]) /
        scapy.DNSRRNSEC(
            rrname='Android.local', rclass=0x8001, ttl=120, nextname='Android.local',
            typebitmaps=[1, 28]))
        )).hex().upper()
        */
        val expected = "00008400000000090000000503313233013201300331393207696E2D61646472046172706" +
                "100000C800100000078000F07416E64726F6964056C6F63616C00013301320131013001300130013" +
                "00130013001300130013001300130013001300130013001300130013001300130013001380142014" +
                "40130013101300130013203697036C020000C8001000000780002C030013601350134C045000C800" +
                "1000000780002C030095F7465737474797065045F746370C038000C000100001194000E0B7465737" +
                "473657276696365C0A5C0C000218001000000780008000000007A02C030C0C000108001000011940" +
                "000C03000018001000000780004C000027BC030001C800100000078001020010DB80000000000000" +
                "00000000123C030001C800100000078001020010DB8000000000000000000000456C00C002F80010" +
                "00000780006C00C00020008C03F002F8001000000780006C03F00020008C091002F8001000000780" +
                "006C09100020008C0C0002F8001000011940009C0C000050000800040C030002F800100000078000" +
                "8C030000440000008"

        val hostname = arrayOf("Android", "local")
        val serviceType = arrayOf("_testtype", "_tcp", "local")
        val serviceName = arrayOf("testservice", "_testtype", "_tcp", "local")
        val v4Addr = parseNumericAddress("192.0.2.123")
        val v6Addr1 = parseNumericAddress("2001:DB8::123")
        val v6Addr2 = parseNumericAddress("2001:DB8::456")
        val v4AddrRev = getReverseDnsAddress(v4Addr)
        val v6Addr1Rev = getReverseDnsAddress(v6Addr1)
        val v6Addr2Rev = getReverseDnsAddress(v6Addr2)

        val announcedRecords = listOf(
                // Reverse address records
                MdnsPointerRecord(v4AddrRev,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        hostname),
                MdnsPointerRecord(v6Addr1Rev,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        hostname),
                MdnsPointerRecord(v6Addr2Rev,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        hostname),
                // Service registration records (RFC6763)
                MdnsPointerRecord(
                        serviceType,
                        0L /* receiptTimeMillis */,
                        // Not a unique name owned by the announcer, so cacheFlush=false
                        false /* cacheFlush */,
                        4500000L /* ttlMillis */,
                        serviceName),
                MdnsServiceRecord(
                        serviceName,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        0 /* servicePriority */,
                        0 /* serviceWeight */,
                        31234 /* servicePort */,
                        hostname),
                MdnsTextRecord(
                        serviceName,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        4500000L /* ttlMillis */,
                        emptyList() /* entries */),
                // Address records for the hostname
                MdnsInetAddressRecord(hostname,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        v4Addr),
                MdnsInetAddressRecord(hostname,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        v6Addr1),
                MdnsInetAddressRecord(hostname,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        v6Addr2))
        // Negative responses (RFC6762 6.1)
        val additionalRecords = listOf(
                MdnsNsecRecord(v4AddrRev,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        v4AddrRev,
                        intArrayOf(MdnsRecord.TYPE_PTR)),
                MdnsNsecRecord(v6Addr1Rev,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        v6Addr1Rev,
                        intArrayOf(MdnsRecord.TYPE_PTR)),
                MdnsNsecRecord(v6Addr2Rev,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        v6Addr2Rev,
                        intArrayOf(MdnsRecord.TYPE_PTR)),
                MdnsNsecRecord(serviceName,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        4500000L /* ttlMillis */,
                        serviceName,
                        intArrayOf(MdnsRecord.TYPE_TXT, MdnsRecord.TYPE_SRV)),
                MdnsNsecRecord(hostname,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        hostname,
                        intArrayOf(MdnsRecord.TYPE_A, MdnsRecord.TYPE_AAAA)))
        val request = TestAnnouncementInfo(announcedRecords, additionalRecords)

        val timeStart = SystemClock.elapsedRealtime()
        val startDelay = 50L
        val sendId = 1
        announcer.startSending(sendId, request, startDelay)

        val captor = ArgumentCaptor.forClass(DatagramPacket::class.java)
        repeat(FIRST_ANNOUNCES_COUNT) { i ->
            verify(cb, timeout(TEST_TIMEOUT_MS)).onSent(i, request, 1 /* sentPacketCount */)
            verify(socket, atLeast(i + 1)).send(any())
            val now = SystemClock.elapsedRealtime()
            assertTrue(now > timeStart + startDelay + i * FIRST_ANNOUNCES_DELAY)
            // Loops can be much slower than the expected timing (>100ms delay), use
            // TEST_TIMEOUT_MS as tolerance.
            assertTrue(now < timeStart + startDelay + (i + 1) * FIRST_ANNOUNCES_DELAY +
                TEST_TIMEOUT_MS)
        }

        // Subsequent announces should happen quickly (NEXT_ANNOUNCES_DELAY)
        verify(socket, timeout(TEST_TIMEOUT_MS).times(MdnsAnnouncer.ANNOUNCEMENT_COUNT))
                .send(captor.capture())
        verify(cb, timeout(TEST_TIMEOUT_MS)).onFinished(request)

        captor.allValues.forEach {
            assertEquals(expected, HexDump.toHexString(it.data))
        }
    }
}
