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
import android.net.LinkAddress
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.HandlerThread
import com.android.server.connectivity.mdns.MdnsAnnouncer.AnnouncementInfo
import com.android.server.connectivity.mdns.MdnsInterfaceAdvertiser.CONFLICT_HOST
import com.android.server.connectivity.mdns.MdnsInterfaceAdvertiser.CONFLICT_SERVICE
import com.android.server.connectivity.mdns.MdnsRecord.TYPE_A
import com.android.server.connectivity.mdns.MdnsRecord.TYPE_AAAA
import com.android.server.connectivity.mdns.MdnsRecord.TYPE_PTR
import com.android.server.connectivity.mdns.MdnsRecord.TYPE_SRV
import com.android.server.connectivity.mdns.MdnsRecord.TYPE_TXT
import com.android.server.connectivity.mdns.MdnsRecordRepository.Dependencies
import com.android.server.connectivity.mdns.MdnsRecordRepository.getReverseDnsAddress
import com.android.server.connectivity.mdns.MdnsServiceInfo.TextEntry
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import com.google.common.truth.Truth.assertThat
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.Collections
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_SERVICE_ID_1 = 42
private const val TEST_SERVICE_ID_2 = 43
private const val TEST_SERVICE_ID_3 = 44
private const val TEST_CUSTOM_HOST_ID_1 = 45
private const val TEST_CUSTOM_HOST_ID_2 = 46
private const val TEST_SERVICE_CUSTOM_HOST_ID_1 = 48
private const val TEST_PORT = 12345
private const val TEST_SUBTYPE = "_subtype"
private const val TEST_SUBTYPE2 = "_subtype2"
// RFC6762 10. Resource Record TTL Values and Cache Coherency
// The recommended TTL value for Multicast DNS resource records with a host name as the resource
// record's name (e.g., A, AAAA, HINFO) or a host name contained within the resource record's rdata
// (e.g., SRV, reverse mapping PTR record) SHOULD be 120 seconds. The recommended TTL value for
// other Multicast DNS resource records is 75 minutes.
private const val LONG_TTL = 4_500_000L
private const val SHORT_TTL = 120_000L
private val TEST_HOSTNAME = arrayOf("Android_000102030405060708090A0B0C0D0E0F", "local")
private val TEST_ADDRESSES = listOf(
        LinkAddress(parseNumericAddress("192.0.2.111"), 24),
        LinkAddress(parseNumericAddress("2001:db8::111"), 64),
        LinkAddress(parseNumericAddress("2001:db8::222"), 64))

private val TEST_SERVICE_1 = NsdServiceInfo().apply {
    serviceType = "_testservice._tcp"
    serviceName = "MyTestService"
    port = TEST_PORT
}

private val TEST_SERVICE_2 = NsdServiceInfo().apply {
    serviceType = "_testservice._tcp"
    serviceName = "MyOtherTestService"
    port = TEST_PORT
}

private val TEST_SERVICE_3 = NsdServiceInfo().apply {
    serviceType = "_TESTSERVICE._tcp"
    serviceName = "MyTESTSERVICE"
    port = TEST_PORT
}

private val TEST_CUSTOM_HOST_1 = NsdServiceInfo().apply {
    hostname = "TestHost"
    hostAddresses = listOf(parseNumericAddress("2001:db8::1"), parseNumericAddress("2001:db8::2"))
}

private val TEST_CUSTOM_HOST_1_NAME = arrayOf("TestHost", "local")

private val TEST_CUSTOM_HOST_2 = NsdServiceInfo().apply {
    hostname = "OtherTestHost"
    hostAddresses = listOf(parseNumericAddress("2001:db8::3"), parseNumericAddress("2001:db8::4"))
}

private val TEST_SERVICE_CUSTOM_HOST_1 = NsdServiceInfo().apply {
    hostname = "TestHost"
    hostAddresses = listOf(parseNumericAddress("2001:db8::1"))
    serviceType = "_testservice._tcp"
    serviceName = "TestService"
    port = TEST_PORT
}

@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
class MdnsRecordRepositoryTest {
    private val thread = HandlerThread(MdnsRecordRepositoryTest::class.simpleName)
    private val deps = object : Dependencies() {
        override fun getInterfaceInetAddresses(iface: NetworkInterface) =
                Collections.enumeration(TEST_ADDRESSES.map { it.address })
    }

    @Before
    fun setUp() {
        thread.start()
    }

    @After
    fun tearDown() {
        thread.quitSafely()
        thread.join()
    }

    private fun makeFlags(
        includeInetAddressesInProbing: Boolean = false,
        isKnownAnswerSuppressionEnabled: Boolean = false,
        unicastReplyEnabled: Boolean = true
    ) = MdnsFeatureFlags.Builder()
        .setIncludeInetAddressRecordsInProbing(includeInetAddressesInProbing)
        .setIsKnownAnswerSuppressionEnabled(isKnownAnswerSuppressionEnabled)
        .setIsUnicastReplyEnabled(unicastReplyEnabled)
        .build()

    @Test
    fun testAddServiceAndProbe() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        assertEquals(0, repository.servicesCount)
        assertEquals(-1,
                repository.addService(TEST_SERVICE_ID_1, TEST_SERVICE_1, null /* ttl */))
        assertEquals(1, repository.servicesCount)

        val probingInfo = repository.setServiceProbing(TEST_SERVICE_ID_1)
        assertNotNull(probingInfo)
        assertTrue(repository.isProbing(TEST_SERVICE_ID_1))

        assertEquals(TEST_SERVICE_ID_1, probingInfo.serviceId)
        val packet = probingInfo.getPacket(0)

        assertEquals(0, packet.transactionId)
        assertEquals(MdnsConstants.FLAGS_QUERY, packet.flags)
        assertEquals(0, packet.answers.size)
        assertEquals(0, packet.additionalRecords.size)

        assertEquals(1, packet.questions.size)
        val expectedName = arrayOf("MyTestService", "_testservice", "_tcp", "local")
        assertEquals(MdnsAnyRecord(expectedName, false /* unicast */), packet.questions[0])

        assertEquals(1, packet.authorityRecords.size)
        assertEquals(MdnsServiceRecord(expectedName,
                0L /* receiptTimeMillis */,
                false /* cacheFlush */,
                SHORT_TTL /* ttlMillis */,
                0 /* servicePriority */, 0 /* serviceWeight */,
                TEST_PORT, TEST_HOSTNAME), packet.authorityRecords[0])

        assertContentEquals(intArrayOf(TEST_SERVICE_ID_1), repository.clearServices())
    }

    @Test
    fun testAddAndConflicts() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1)
        assertFailsWith(NameConflictException::class) {
            repository.addService(TEST_SERVICE_ID_2, TEST_SERVICE_1, null /* ttl */)
        }
        assertFailsWith(NameConflictException::class) {
            repository.addService(TEST_SERVICE_ID_3, TEST_SERVICE_3, null /* ttl */)
        }
    }

    @Test
    fun testAddAndUpdates() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1)

        assertFailsWith(IllegalArgumentException::class) {
            repository.updateService(TEST_SERVICE_ID_2, emptySet() /* subtype */)
        }

        repository.updateService(TEST_SERVICE_ID_1, setOf(TEST_SUBTYPE))

        val queriedName = arrayOf(TEST_SUBTYPE, "_sub", "_testservice", "_tcp", "local")
        val questions = listOf(MdnsPointerRecord(queriedName, false /* isUnicast */))
        val query = MdnsPacket(0 /* flags */, questions, emptyList() /* answers */,
                emptyList() /* authorityRecords */, emptyList() /* additionalRecords */)
        val src = InetSocketAddress(parseNumericAddress("192.0.2.123"), 5353)
        val reply = repository.getReply(query, src)

        assertNotNull(reply)

        // TTLs as per RFC6762 10.
        val longTtl = 4_500_000L
        val serviceName = arrayOf("MyTestService", "_testservice", "_tcp", "local")

        assertEquals(listOf(
                MdnsPointerRecord(
                        queriedName,
                        0L /* receiptTimeMillis */,
                        false /* cacheFlush */,
                        longTtl,
                        serviceName),
        ), reply.answers)
    }

    @Test
    fun testInvalidReuseOfServiceId() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.addService(TEST_SERVICE_ID_1, TEST_SERVICE_1, null /* ttl */)
        assertFailsWith(IllegalArgumentException::class) {
            repository.addService(TEST_SERVICE_ID_1, TEST_SERVICE_2, null /* ttl */)
        }
    }

    @Test
    fun testHasActiveService() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        assertFalse(repository.hasActiveService(TEST_SERVICE_ID_1))

        repository.addService(TEST_SERVICE_ID_1, TEST_SERVICE_1, null /* ttl */)
        assertTrue(repository.hasActiveService(TEST_SERVICE_ID_1))

        val probingInfo = repository.setServiceProbing(TEST_SERVICE_ID_1)
        repository.onProbingSucceeded(probingInfo)
        repository.onAdvertisementSent(TEST_SERVICE_ID_1, 2 /* sentPacketCount */)
        assertTrue(repository.hasActiveService(TEST_SERVICE_ID_1))

        repository.exitService(TEST_SERVICE_ID_1)
        assertFalse(repository.hasActiveService(TEST_SERVICE_ID_1))
    }

    @Test
    fun testExitAnnouncements() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1)
        repository.onAdvertisementSent(TEST_SERVICE_ID_1, 2 /* sentPacketCount */)

        val exitAnnouncement = repository.exitService(TEST_SERVICE_ID_1)
        assertNotNull(exitAnnouncement)
        assertEquals(1, repository.servicesCount)
        val packet = exitAnnouncement.getPacket(0)

        assertEquals(0, packet.transactionId)
        assertEquals(0x8400 /* response, authoritative */, packet.flags)
        assertEquals(0, packet.questions.size)
        assertEquals(0, packet.authorityRecords.size)
        assertEquals(0, packet.additionalRecords.size)

        assertContentEquals(listOf(
                MdnsPointerRecord(
                        arrayOf("_testservice", "_tcp", "local"),
                        0L /* receiptTimeMillis */,
                        false /* cacheFlush */,
                        0L /* ttlMillis */,
                        arrayOf("MyTestService", "_testservice", "_tcp", "local"))
        ), packet.answers)

        repository.removeService(TEST_SERVICE_ID_1)
        assertEquals(0, repository.servicesCount)
    }

    @Test
    fun testExitAnnouncements_WithSubtypes() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1,
                setOf(TEST_SUBTYPE, TEST_SUBTYPE2))
        repository.onAdvertisementSent(TEST_SERVICE_ID_1, 2 /* sentPacketCount */)

        val exitAnnouncement = repository.exitService(TEST_SERVICE_ID_1)
        assertNotNull(exitAnnouncement)
        assertEquals(1, repository.servicesCount)
        val packet = exitAnnouncement.getPacket(0)

        assertEquals(0, packet.transactionId)
        assertEquals(0x8400 /* response, authoritative */, packet.flags)
        assertEquals(0, packet.questions.size)
        assertEquals(0, packet.authorityRecords.size)
        assertEquals(0, packet.additionalRecords.size)

        assertThat(packet.answers).containsExactly(
                MdnsPointerRecord(
                        arrayOf("_testservice", "_tcp", "local"),
                        0L /* receiptTimeMillis */,
                        false /* cacheFlush */,
                        0L /* ttlMillis */,
                        arrayOf("MyTestService", "_testservice", "_tcp", "local")),
                MdnsPointerRecord(
                        arrayOf("_subtype", "_sub", "_testservice", "_tcp", "local"),
                        0L /* receiptTimeMillis */,
                        false /* cacheFlush */,
                        0L /* ttlMillis */,
                        arrayOf("MyTestService", "_testservice", "_tcp", "local")),
                MdnsPointerRecord(
                        arrayOf("_subtype2", "_sub", "_testservice", "_tcp", "local"),
                        0L /* receiptTimeMillis */,
                        false /* cacheFlush */,
                        0L /* ttlMillis */,
                        arrayOf("MyTestService", "_testservice", "_tcp", "local")))

        repository.removeService(TEST_SERVICE_ID_1)
        assertEquals(0, repository.servicesCount)
    }

    @Test
    fun testExitingServiceReAdded() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1)
        repository.onAdvertisementSent(TEST_SERVICE_ID_1, 2 /* sentPacketCount */)
        repository.exitService(TEST_SERVICE_ID_1)

        assertEquals(TEST_SERVICE_ID_1,
                repository.addService(TEST_SERVICE_ID_2, TEST_SERVICE_1, null /* ttl */))
        assertEquals(1, repository.servicesCount)

        repository.removeService(TEST_SERVICE_ID_2)
        assertEquals(0, repository.servicesCount)
    }

    @Test
    fun testOnProbingSucceeded() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        val announcementInfo = repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1,
                setOf(TEST_SUBTYPE, TEST_SUBTYPE2))
        repository.onAdvertisementSent(TEST_SERVICE_ID_1, 2 /* sentPacketCount */)
        val packet = announcementInfo.getPacket(0)

        assertEquals(0, packet.transactionId)
        assertEquals(0x8400 /* response, authoritative */, packet.flags)
        assertEquals(0, packet.questions.size)
        assertEquals(0, packet.authorityRecords.size)

        val serviceType = arrayOf("_testservice", "_tcp", "local")
        val serviceSubtype = arrayOf(TEST_SUBTYPE, "_sub", "_testservice", "_tcp", "local")
        val serviceSubtype2 = arrayOf(TEST_SUBTYPE2, "_sub", "_testservice", "_tcp", "local")
        val serviceName = arrayOf("MyTestService", "_testservice", "_tcp", "local")
        val v4AddrRev = getReverseDnsAddress(TEST_ADDRESSES[0].address)
        val v6Addr1Rev = getReverseDnsAddress(TEST_ADDRESSES[1].address)
        val v6Addr2Rev = getReverseDnsAddress(TEST_ADDRESSES[2].address)

        assertThat(packet.answers).containsExactly(
                // Reverse address and address records for the hostname
                MdnsPointerRecord(v4AddrRev,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        TEST_HOSTNAME),
                MdnsInetAddressRecord(TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        TEST_ADDRESSES[0].address),
                MdnsPointerRecord(v6Addr1Rev,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        TEST_HOSTNAME),
                MdnsInetAddressRecord(TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        TEST_ADDRESSES[1].address),
                MdnsPointerRecord(v6Addr2Rev,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        TEST_HOSTNAME),
                MdnsInetAddressRecord(TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        TEST_ADDRESSES[2].address),
                // Service registration records (RFC6763)
                MdnsPointerRecord(
                        serviceType,
                        0L /* receiptTimeMillis */,
                        // Not a unique name owned by the announcer, so cacheFlush=false
                        false /* cacheFlush */,
                        4500000L /* ttlMillis */,
                        serviceName),
                MdnsPointerRecord(
                        serviceSubtype,
                        0L /* receiptTimeMillis */,
                        // Not a unique name owned by the announcer, so cacheFlush=false
                        false /* cacheFlush */,
                        4500000L /* ttlMillis */,
                        serviceName),
                MdnsPointerRecord(
                        serviceSubtype2,
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
                        TEST_PORT /* servicePort */,
                        TEST_HOSTNAME),
                MdnsTextRecord(
                        serviceName,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        4500000L /* ttlMillis */,
                        emptyList() /* entries */),
                // Service type enumeration record (RFC6763 9.)
                MdnsPointerRecord(
                        arrayOf("_services", "_dns-sd", "_udp", "local"),
                        0L /* receiptTimeMillis */,
                        false /* cacheFlush */,
                        4500000L /* ttlMillis */,
                        serviceType))

        assertContentEquals(listOf(
                MdnsNsecRecord(v4AddrRev,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        v4AddrRev,
                        intArrayOf(TYPE_PTR)),
                MdnsNsecRecord(TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        TEST_HOSTNAME,
                        intArrayOf(TYPE_A, TYPE_AAAA)),
                MdnsNsecRecord(v6Addr1Rev,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        v6Addr1Rev,
                        intArrayOf(TYPE_PTR)),
                MdnsNsecRecord(v6Addr2Rev,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        120000L /* ttlMillis */,
                        v6Addr2Rev,
                        intArrayOf(TYPE_PTR)),
                MdnsNsecRecord(serviceName,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        4500000L /* ttlMillis */,
                        serviceName,
                        intArrayOf(TYPE_TXT, TYPE_SRV))
        ), packet.additionalRecords)
    }

    @Test
    fun testGetOffloadPacket() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1)
        val serviceName = arrayOf("MyTestService", "_testservice", "_tcp", "local")
        val serviceType = arrayOf("_testservice", "_tcp", "local")
        val offloadPacket = repository.getOffloadPacket(TEST_SERVICE_ID_1)
        assertEquals(0, offloadPacket.transactionId)
        assertEquals(0x8400, offloadPacket.flags)
        assertEquals(0, offloadPacket.questions.size)
        assertEquals(0, offloadPacket.additionalRecords.size)
        assertEquals(0, offloadPacket.authorityRecords.size)
        assertContentEquals(listOf(
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
                TEST_PORT /* servicePort */,
                TEST_HOSTNAME),
            MdnsTextRecord(
                serviceName,
                0L /* receiptTimeMillis */,
                true /* cacheFlush */,
                4500000L /* ttlMillis */,
                emptyList() /* entries */),
            MdnsInetAddressRecord(TEST_HOSTNAME,
                0L /* receiptTimeMillis */,
                true /* cacheFlush */,
                120000L /* ttlMillis */,
                TEST_ADDRESSES[0].address),
            MdnsInetAddressRecord(TEST_HOSTNAME,
                0L /* receiptTimeMillis */,
                true /* cacheFlush */,
                120000L /* ttlMillis */,
                TEST_ADDRESSES[1].address),
            MdnsInetAddressRecord(TEST_HOSTNAME,
                0L /* receiptTimeMillis */,
                true /* cacheFlush */,
                120000L /* ttlMillis */,
                TEST_ADDRESSES[2].address),
        ), offloadPacket.answers)
    }

    @Test
    fun testGetReverseDnsAddress() {
        val expectedV6 = "1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.B.D.0.1.0.0.2.ip6.arpa"
                .split(".").toTypedArray()
        assertContentEquals(expectedV6, getReverseDnsAddress(parseNumericAddress("2001:db8::1")))
        val expectedV4 = "123.2.0.192.in-addr.arpa".split(".").toTypedArray()
        assertContentEquals(expectedV4, getReverseDnsAddress(parseNumericAddress("192.0.2.123")))
    }

    @Test
    fun testGetReplyCaseInsensitive() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1)
        val questionsCaseInSensitive = listOf(
                MdnsPointerRecord(arrayOf("_TESTSERVICE", "_TCP", "local"), false /* isUnicast */))
        val queryCaseInsensitive = MdnsPacket(0 /* flags */, questionsCaseInSensitive,
            emptyList() /* answers */, emptyList() /* authorityRecords */,
            emptyList() /* additionalRecords */)
        val src = InetSocketAddress(parseNumericAddress("192.0.2.123"), 5353)
        val replyCaseInsensitive = repository.getReply(queryCaseInsensitive, src)
        assertNotNull(replyCaseInsensitive)
        assertEquals(1, replyCaseInsensitive.answers.size)
        assertEquals(7, replyCaseInsensitive.additionalAnswers.size)
    }

    /**
     * Creates mDNS query packet with given query names and types.
     */
    private fun makeQuery(vararg queries: Pair<Int, Array<String>>): MdnsPacket {
        val questions = queries.map { (type, name) -> makeQuestionRecord(name, type) }
        return MdnsPacket(0 /* flags */, questions, emptyList() /* answers */,
                emptyList() /* authorityRecords */, emptyList() /* additionalRecords */)
    }

    private fun makeQuestionRecord(name: Array<String>, type: Int): MdnsRecord {
        when (type) {
            TYPE_PTR -> return MdnsPointerRecord(name, false /* isUnicast */)
            TYPE_SRV -> return MdnsServiceRecord(name, false /* isUnicast */)
            TYPE_TXT -> return MdnsTextRecord(name, false /* isUnicast */)
            TYPE_A, TYPE_AAAA -> return MdnsInetAddressRecord(name, type, false /* isUnicast */)
            else -> fail("Unexpected question type: $type")
        }
    }

    @Test
    fun testGetReply_singlePtrQuestion_returnsSrvTxtAddressNsecRecords() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1, setOf(TEST_SUBTYPE))
        val src = InetSocketAddress(parseNumericAddress("192.0.2.123"), 5353)
        val serviceName = arrayOf("MyTestService", "_testservice", "_tcp", "local")

        val query = makeQuery(TYPE_PTR to arrayOf("_testservice", "_tcp", "local"))
        val reply = repository.getReply(query, src)

        assertNotNull(reply)
        assertEquals(listOf(
                MdnsPointerRecord(
                    arrayOf("_testservice", "_tcp", "local"), 0L, false, LONG_TTL, serviceName)),
            reply.answers)
        assertEquals(listOf(
                MdnsTextRecord(serviceName, 0L, true, LONG_TTL, emptyList()),
                MdnsServiceRecord(serviceName, 0L, true, SHORT_TTL, 0, 0, TEST_PORT, TEST_HOSTNAME),
                MdnsInetAddressRecord(
                    TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_ADDRESSES[0].address),
                MdnsInetAddressRecord(
                    TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_ADDRESSES[1].address),
                MdnsInetAddressRecord(
                    TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_ADDRESSES[2].address),
                MdnsNsecRecord(serviceName, 0L, true, LONG_TTL, serviceName /* nextDomain */,
                        intArrayOf(TYPE_TXT, TYPE_SRV)),
                MdnsNsecRecord(TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_HOSTNAME /* nextDomain */,
                        intArrayOf(TYPE_A, TYPE_AAAA)),
            ), reply.additionalAnswers)
    }


    @Test
    fun testGetReply_ptrQuestionForServiceWithCustomHost_customHostUsedInAdditionalAnswers() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_CUSTOM_HOST_ID_1, TEST_SERVICE_CUSTOM_HOST_1,
                setOf(TEST_SUBTYPE, TEST_SUBTYPE2))
        val src = InetSocketAddress(parseNumericAddress("fe80::1234"), 5353)
        val serviceName = arrayOf("TestService", "_testservice", "_tcp", "local")

        val query = makeQuery(TYPE_PTR to arrayOf("_testservice", "_tcp", "local"))
        val reply = repository.getReply(query, src)

        assertNotNull(reply)
        assertEquals(listOf(
                MdnsPointerRecord(
                        arrayOf("_testservice", "_tcp", "local"),
                        0L, false, LONG_TTL, serviceName)),
                reply.answers)
        assertEquals(listOf(
                MdnsTextRecord(serviceName, 0L, true, LONG_TTL, listOf()),
                MdnsServiceRecord(serviceName, 0L, true, SHORT_TTL,
                        0, 0, TEST_PORT, TEST_CUSTOM_HOST_1_NAME),
                MdnsInetAddressRecord(
                        TEST_CUSTOM_HOST_1_NAME, 0L, true, SHORT_TTL,
                        parseNumericAddress("2001:db8::1")),
                MdnsNsecRecord(serviceName, 0L, true, LONG_TTL, serviceName /* nextDomain */,
                        intArrayOf(TYPE_TXT, TYPE_SRV)),
                MdnsNsecRecord(TEST_CUSTOM_HOST_1_NAME, 0L, true, SHORT_TTL,
                        TEST_CUSTOM_HOST_1_NAME /* nextDomain */,
                        intArrayOf(TYPE_AAAA)),
        ), reply.additionalAnswers)
    }

    @Test
    fun testGetReply_ptrQuestionForServicesWithSameCustomHost_customHostUsedInAdditionalAnswers() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        val serviceWithCustomHost1 = NsdServiceInfo().apply {
            hostname = "TestHost"
            hostAddresses = listOf(
                    parseNumericAddress("2001:db8::1"),
                    parseNumericAddress("192.0.2.1"))
            serviceType = "_testservice._tcp"
            serviceName = "TestService1"
            port = TEST_PORT
        }
        val serviceWithCustomHost2 = NsdServiceInfo().apply {
            hostname = "TestHost"
            hostAddresses = listOf(
                    parseNumericAddress("2001:db8::1"),
                    parseNumericAddress("2001:db8::3"))
        }
        repository.addServiceAndFinishProbing(TEST_SERVICE_ID_1, serviceWithCustomHost1)
        repository.addServiceAndFinishProbing(TEST_SERVICE_ID_2, serviceWithCustomHost2)
        val src = InetSocketAddress(parseNumericAddress("fe80::1234"), 5353)
        val serviceName = arrayOf("TestService1", "_testservice", "_tcp", "local")

        val query = makeQuery(TYPE_PTR to arrayOf("_testservice", "_tcp", "local"))
        val reply = repository.getReply(query, src)

        assertNotNull(reply)
        assertEquals(listOf(
                MdnsPointerRecord(
                        arrayOf("_testservice", "_tcp", "local"),
                        0L, false, LONG_TTL, serviceName)),
                reply.answers)
        assertEquals(listOf(
                MdnsTextRecord(serviceName, 0L, true, LONG_TTL, listOf()),
                MdnsServiceRecord(serviceName, 0L, true, SHORT_TTL,
                        0, 0, TEST_PORT, TEST_CUSTOM_HOST_1_NAME),
                MdnsInetAddressRecord(
                        TEST_CUSTOM_HOST_1_NAME, 0L, true, SHORT_TTL,
                        parseNumericAddress("2001:db8::1")),
                MdnsInetAddressRecord(
                        TEST_CUSTOM_HOST_1_NAME, 0L, true, SHORT_TTL,
                        parseNumericAddress("192.0.2.1")),
                MdnsInetAddressRecord(
                        TEST_CUSTOM_HOST_1_NAME, 0L, true, SHORT_TTL,
                        parseNumericAddress("2001:db8::3")),
                MdnsNsecRecord(serviceName, 0L, true, LONG_TTL, serviceName /* nextDomain */,
                        intArrayOf(TYPE_TXT, TYPE_SRV)),
                MdnsNsecRecord(TEST_CUSTOM_HOST_1_NAME, 0L, true, SHORT_TTL,
                        TEST_CUSTOM_HOST_1_NAME /* nextDomain */,
                        intArrayOf(TYPE_A, TYPE_AAAA)),
        ), reply.additionalAnswers)
    }

    @Test
    fun testGetReply_singleSubtypePtrQuestion_returnsSrvTxtAddressNsecRecords() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1, setOf(TEST_SUBTYPE))
        val src = InetSocketAddress(parseNumericAddress("192.0.2.123"), 5353)
        val serviceName = arrayOf("MyTestService", "_testservice", "_tcp", "local")

        val query = makeQuery(
                TYPE_PTR to arrayOf(TEST_SUBTYPE, "_sub", "_testservice", "_tcp", "local"))
        val reply = repository.getReply(query, src)

        assertNotNull(reply)
        assertEquals(listOf(
                MdnsPointerRecord(
                    arrayOf(TEST_SUBTYPE, "_sub", "_testservice", "_tcp", "local"), 0L, false,
                    LONG_TTL, serviceName)),
            reply.answers)
        assertEquals(listOf(
                MdnsTextRecord(serviceName, 0L, true, LONG_TTL, emptyList()),
                MdnsServiceRecord(serviceName, 0L, true, SHORT_TTL, 0, 0, TEST_PORT, TEST_HOSTNAME),
                MdnsInetAddressRecord(
                    TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_ADDRESSES[0].address),
                MdnsInetAddressRecord(
                    TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_ADDRESSES[1].address),
                MdnsInetAddressRecord(
                    TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_ADDRESSES[2].address),
                MdnsNsecRecord(serviceName, 0L, true, LONG_TTL, serviceName /* nextDomain */,
                        intArrayOf(TYPE_TXT, TYPE_SRV)),
                MdnsNsecRecord(TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_HOSTNAME /* nextDomain */,
                        intArrayOf(TYPE_A, TYPE_AAAA)),
            ), reply.additionalAnswers)
    }

    @Test
    fun testGetReply_duplicatePtrQuestions_doesNotReturnDuplicateRecords() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1, setOf(TEST_SUBTYPE))
        val src = InetSocketAddress(parseNumericAddress("192.0.2.123"), 5353)
        val serviceName = arrayOf("MyTestService", "_testservice", "_tcp", "local")

        val query = makeQuery(
                TYPE_PTR to arrayOf("_testservice", "_tcp", "local"),
                TYPE_PTR to arrayOf("_testservice", "_tcp", "local"))
        val reply = repository.getReply(query, src)

        assertNotNull(reply)
        assertEquals(listOf(
                MdnsPointerRecord(
                    arrayOf("_testservice", "_tcp", "local"), 0L, false, LONG_TTL, serviceName)),
            reply.answers)
        assertEquals(listOf(
                MdnsTextRecord(serviceName, 0L, true, LONG_TTL, emptyList()),
                MdnsServiceRecord(serviceName, 0L, true, SHORT_TTL, 0, 0, TEST_PORT, TEST_HOSTNAME),
                MdnsInetAddressRecord(
                    TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_ADDRESSES[0].address),
                MdnsInetAddressRecord(
                    TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_ADDRESSES[1].address),
                MdnsInetAddressRecord(
                    TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_ADDRESSES[2].address),
                MdnsNsecRecord(serviceName, 0L, true, LONG_TTL, serviceName /* nextDomain */,
                        intArrayOf(TYPE_TXT, TYPE_SRV)),
                MdnsNsecRecord(TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_HOSTNAME /* nextDomain */,
                        intArrayOf(TYPE_A, TYPE_AAAA)),
            ), reply.additionalAnswers)
    }

    @Test
    fun testGetReply_multiplePtrQuestionsWithSubtype_doesNotReturnDuplicateRecords() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1, setOf(TEST_SUBTYPE))
        val src = InetSocketAddress(parseNumericAddress("192.0.2.123"), 5353)
        val serviceName = arrayOf("MyTestService", "_testservice", "_tcp", "local")

        val query = makeQuery(
                TYPE_PTR to arrayOf("_testservice", "_tcp", "local"),
                TYPE_PTR to arrayOf(TEST_SUBTYPE, "_sub", "_testservice", "_tcp", "local"))
        val reply = repository.getReply(query, src)

        assertNotNull(reply)
        assertEquals(listOf(
                MdnsPointerRecord(
                    arrayOf("_testservice", "_tcp", "local"), 0L, false, LONG_TTL, serviceName),
                MdnsPointerRecord(
                    arrayOf(TEST_SUBTYPE, "_sub", "_testservice", "_tcp", "local"),
                    0L, false, LONG_TTL, serviceName)),
            reply.answers)
        assertEquals(listOf(
                MdnsTextRecord(serviceName, 0L, true, LONG_TTL, emptyList()),
                MdnsServiceRecord(serviceName, 0L, true, SHORT_TTL, 0, 0, TEST_PORT, TEST_HOSTNAME),
                MdnsInetAddressRecord(
                    TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_ADDRESSES[0].address),
                MdnsInetAddressRecord(
                    TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_ADDRESSES[1].address),
                MdnsInetAddressRecord(
                    TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_ADDRESSES[2].address),
                MdnsNsecRecord(serviceName, 0L, true, LONG_TTL, serviceName /* nextDomain */,
                        intArrayOf(TYPE_TXT, TYPE_SRV)),
                MdnsNsecRecord(TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_HOSTNAME /* nextDomain */,
                        intArrayOf(TYPE_A, TYPE_AAAA)),
            ), reply.additionalAnswers)
    }

    @Test
    fun testGetReply_txtQuestion_returnsNoNsecRecord() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1, setOf(TEST_SUBTYPE))
        val src = InetSocketAddress(parseNumericAddress("192.0.2.123"), 5353)
        val serviceName = arrayOf("MyTestService", "_testservice", "_tcp", "local")

        val query = makeQuery(TYPE_TXT to serviceName)
        val reply = repository.getReply(query, src)

        assertNotNull(reply)
        assertEquals(listOf(MdnsTextRecord(serviceName, 0L, true, LONG_TTL, emptyList())),
                reply.answers)
        // No NSEC records because the reply doesn't include the SRV record
        assertTrue(reply.additionalAnswers.isEmpty())
    }

    @Test
    fun testGetReply_AAAAQuestionButNoIpv6Address_returnsNsecRecord() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(
                TEST_SERVICE_ID_1, TEST_SERVICE_1, setOf(TEST_SUBTYPE),
                listOf(LinkAddress(parseNumericAddress("192.0.2.111"), 24)))
        val src = InetSocketAddress(parseNumericAddress("192.0.2.123"), 5353)

        val query = makeQuery(TYPE_AAAA to TEST_HOSTNAME)
        val reply = repository.getReply(query, src)

        assertNotNull(reply)
        assertTrue(reply.answers.isEmpty())
        assertEquals(listOf(
                MdnsNsecRecord(TEST_HOSTNAME, 0L, true, LONG_TTL, TEST_HOSTNAME /* nextDomain */,
                        intArrayOf(TYPE_AAAA))),
            reply.additionalAnswers)
    }

    @Test
    fun testGetReply_AAAAQuestionForCustomHost_returnsAAAARecords() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(
                TEST_CUSTOM_HOST_ID_1, TEST_CUSTOM_HOST_1, subtypes = setOf(),
                listOf(LinkAddress(parseNumericAddress("192.0.2.111"), 24)))
        repository.addService(TEST_CUSTOM_HOST_ID_2, TEST_CUSTOM_HOST_2, null /* ttl */)
        val src = InetSocketAddress(parseNumericAddress("fe80::123"), 5353)

        val query = makeQuery(TYPE_AAAA to TEST_CUSTOM_HOST_1_NAME)
        val reply = repository.getReply(query, src)

        assertNotNull(reply)
        assertEquals(listOf(
                MdnsInetAddressRecord(TEST_CUSTOM_HOST_1_NAME,
                        0, false, LONG_TTL, parseNumericAddress("2001:db8::1")),
                MdnsInetAddressRecord(TEST_CUSTOM_HOST_1_NAME,
                        0, false, LONG_TTL, parseNumericAddress("2001:db8::2"))),
                reply.answers)
        assertEquals(
                listOf(MdnsNsecRecord(TEST_CUSTOM_HOST_1_NAME,
                        0L, true, SHORT_TTL,
                        TEST_CUSTOM_HOST_1_NAME /* nextDomain */,
                        intArrayOf(TYPE_AAAA))),
                reply.additionalAnswers)
    }


    @Test
    fun testGetReply_AAAAQuestionForCustomHostInMultipleRegistrations_returnsAAAARecords() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())

        repository.addServiceAndFinishProbing(TEST_CUSTOM_HOST_ID_1, NsdServiceInfo().apply {
            hostname = "TestHost"
            hostAddresses = listOf(
                    parseNumericAddress("2001:db8::1"),
                    parseNumericAddress("2001:db8::2"))
        })
        repository.addServiceAndFinishProbing(TEST_CUSTOM_HOST_ID_2, NsdServiceInfo().apply {
            hostname = "TestHost"
            hostAddresses = listOf(
                    parseNumericAddress("2001:db8::1"),
                    parseNumericAddress("2001:db8::3"))
        })
        val src = InetSocketAddress(parseNumericAddress("fe80::123"), 5353)

        val query = makeQuery(TYPE_AAAA to TEST_CUSTOM_HOST_1_NAME)
        val reply = repository.getReply(query, src)

        assertNotNull(reply)
        assertEquals(listOf(
                MdnsInetAddressRecord(TEST_CUSTOM_HOST_1_NAME,
                        0, false, LONG_TTL, parseNumericAddress("2001:db8::1")),
                MdnsInetAddressRecord(TEST_CUSTOM_HOST_1_NAME,
                        0, false, LONG_TTL, parseNumericAddress("2001:db8::2")),
                MdnsInetAddressRecord(TEST_CUSTOM_HOST_1_NAME,
                        0, false, LONG_TTL, parseNumericAddress("2001:db8::3"))),
                reply.answers)
        assertEquals(
                listOf(MdnsNsecRecord(TEST_CUSTOM_HOST_1_NAME,
                        0L, true, SHORT_TTL,
                        TEST_CUSTOM_HOST_1_NAME /* nextDomain */,
                        intArrayOf(TYPE_AAAA))),
                reply.additionalAnswers)
    }

    @Test
    fun testGetReply_customHostRemoved_noAnswerToAAAAQuestion() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(
                TEST_CUSTOM_HOST_ID_1, TEST_CUSTOM_HOST_1, subtypes = setOf(),
                listOf(LinkAddress(parseNumericAddress("192.0.2.111"), 24)))
        repository.addService(
                TEST_SERVICE_CUSTOM_HOST_ID_1, TEST_SERVICE_CUSTOM_HOST_1, null /* ttl */)
        repository.removeService(TEST_CUSTOM_HOST_ID_1)
        repository.removeService(TEST_SERVICE_CUSTOM_HOST_ID_1)

        val src = InetSocketAddress(parseNumericAddress("fe80::123"), 5353)

        val query = makeQuery(TYPE_AAAA to TEST_CUSTOM_HOST_1_NAME)
        val reply = repository.getReply(query, src)

        assertNull(reply)
    }

    @Test
    fun testGetReply_ptrAndSrvQuestions_doesNotReturnSrvRecordInAdditionalAnswerSection() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1, setOf(TEST_SUBTYPE))
        val src = InetSocketAddress(parseNumericAddress("192.0.2.123"), 5353)
        val serviceName = arrayOf("MyTestService", "_testservice", "_tcp", "local")

        val query = makeQuery(
                TYPE_PTR to arrayOf("_testservice", "_tcp", "local"),
                TYPE_SRV to serviceName)
        val reply = repository.getReply(query, src)

        assertNotNull(reply)
        assertEquals(listOf(
                MdnsPointerRecord(
                    arrayOf("_testservice", "_tcp", "local"), 0L, false, LONG_TTL, serviceName),
                MdnsServiceRecord(
                    serviceName, 0L, true, SHORT_TTL, 0, 0, TEST_PORT, TEST_HOSTNAME)),
            reply.answers)
        assertFalse(reply.additionalAnswers.any { it -> it is MdnsServiceRecord })
    }

    @Test
    fun testGetReply_srvTxtAddressQuestions_returnsAllRecordsInAnswerSectionExceptNsec() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1, setOf(TEST_SUBTYPE))
        val src = InetSocketAddress(parseNumericAddress("192.0.2.123"), 5353)
        val serviceName = arrayOf("MyTestService", "_testservice", "_tcp", "local")

        val query = makeQuery(
                TYPE_SRV to serviceName,
                TYPE_TXT to serviceName,
                TYPE_SRV to serviceName,
                TYPE_A to TEST_HOSTNAME,
                TYPE_AAAA to TEST_HOSTNAME)
        val reply = repository.getReply(query, src)

        assertNotNull(reply)
        assertEquals(listOf(
                MdnsServiceRecord(serviceName, 0L, true, SHORT_TTL, 0, 0, TEST_PORT, TEST_HOSTNAME),
                MdnsTextRecord(serviceName, 0L, true, LONG_TTL, emptyList()),
                MdnsInetAddressRecord(
                        TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_ADDRESSES[0].address),
                MdnsInetAddressRecord(
                        TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_ADDRESSES[1].address),
                MdnsInetAddressRecord(
                        TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_ADDRESSES[2].address)),
            reply.answers)
        assertEquals(listOf(
                MdnsNsecRecord(serviceName, 0L, true, LONG_TTL, serviceName /* nextDomain */,
                        intArrayOf(TYPE_TXT, TYPE_SRV)),
                MdnsNsecRecord(TEST_HOSTNAME, 0L, true, SHORT_TTL, TEST_HOSTNAME /* nextDomain */,
                        intArrayOf(TYPE_A, TYPE_AAAA))),
            reply.additionalAnswers)
    }

    @Test
    fun testGetReply_queryWithIpv4Address_replyWithIpv4Address() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1, setOf(TEST_SUBTYPE))
        val query = makeQuery(TYPE_PTR to arrayOf("_testservice", "_tcp", "local"))

        val srcIpv4 = InetSocketAddress(parseNumericAddress("192.0.2.123"), 5353)
        val replyIpv4 = repository.getReply(query, srcIpv4)

        assertNotNull(replyIpv4)
        assertEquals(MdnsConstants.getMdnsIPv4Address(), replyIpv4.destination.address)
        assertEquals(MdnsConstants.MDNS_PORT, replyIpv4.destination.port)
    }

    @Test
    fun testGetReply_queryWithIpv6Address_replyWithIpv6Address() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1, setOf(TEST_SUBTYPE))
        val query = makeQuery(TYPE_PTR to arrayOf("_testservice", "_tcp", "local"))

        val srcIpv6 = InetSocketAddress(parseNumericAddress("2001:db8::123"), 5353)
        val replyIpv6 = repository.getReply(query, srcIpv6)

        assertNotNull(replyIpv6)
        assertEquals(MdnsConstants.getMdnsIPv6Address(), replyIpv6.destination.address)
        assertEquals(MdnsConstants.MDNS_PORT, replyIpv6.destination.port)
    }

    @Test
    fun testGetConflictingServices() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.addService(TEST_SERVICE_ID_1, TEST_SERVICE_1, null /* ttl */)
        repository.addService(TEST_SERVICE_ID_2, TEST_SERVICE_2, null /* ttl */)

        val packet = MdnsPacket(
                0 /* flags */,
                emptyList() /* questions */,
                listOf(
                    MdnsServiceRecord(
                            arrayOf("MyTestService", "_testservice", "_tcp", "local"),
                            0L /* receiptTimeMillis */, true /* cacheFlush */, 0L /* ttlMillis */,
                            0 /* servicePriority */, 0 /* serviceWeight */,
                            TEST_SERVICE_1.port + 1,
                            TEST_HOSTNAME),
                    MdnsTextRecord(
                            arrayOf("MyOtherTestService", "_testservice", "_tcp", "local"),
                            0L /* receiptTimeMillis */, true /* cacheFlush */, 0L /* ttlMillis */,
                            listOf(TextEntry.fromString("somedifferent=entry"))),
                ) /* answers */,
                emptyList() /* authorityRecords */,
                emptyList() /* additionalRecords */)

        assertEquals(
                mapOf(
                        TEST_SERVICE_ID_1 to CONFLICT_SERVICE,
                        TEST_SERVICE_ID_2 to CONFLICT_SERVICE),
                repository.getConflictingServices(packet))
    }

    @Test
    fun testGetConflictingServicesCaseInsensitive() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.addService(TEST_SERVICE_ID_1, TEST_SERVICE_1, null /* ttl */)
        repository.addService(TEST_SERVICE_ID_2, TEST_SERVICE_2, null /* ttl */)

        val packet = MdnsPacket(
            0 /* flags */,
            emptyList() /* questions */,
            listOf(
                MdnsServiceRecord(
                    arrayOf("MYTESTSERVICE", "_TESTSERVICE", "_tcp", "local"),
                    0L /* receiptTimeMillis */, true /* cacheFlush */, 0L /* ttlMillis */,
                    0 /* servicePriority */, 0 /* serviceWeight */,
                    TEST_SERVICE_1.port + 1,
                    TEST_HOSTNAME),
                MdnsTextRecord(
                    arrayOf("MYOTHERTESTSERVICE", "_TESTSERVICE", "_tcp", "local"),
                    0L /* receiptTimeMillis */, true /* cacheFlush */, 0L /* ttlMillis */,
                    listOf(TextEntry.fromString("somedifferent=entry"))),
            ) /* answers */,
            emptyList() /* authorityRecords */,
            emptyList() /* additionalRecords */)

        assertEquals(
                mapOf(TEST_SERVICE_ID_1 to CONFLICT_SERVICE,
                        TEST_SERVICE_ID_2 to CONFLICT_SERVICE),
                repository.getConflictingServices(packet))
    }

    @Test
    fun testGetConflictingServices_customHosts_differentAddresses() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.addService(TEST_CUSTOM_HOST_ID_1, TEST_CUSTOM_HOST_1, null /* ttl */)
        repository.addService(TEST_CUSTOM_HOST_ID_2, TEST_CUSTOM_HOST_2, null /* ttl */)

        val packet = MdnsPacket(
                0, /* flags */
                emptyList(), /* questions */
                listOf(
                        MdnsInetAddressRecord(arrayOf("TestHost", "local"),
                                0L /* receiptTimeMillis */, true /* cacheFlush */,
                                0L /* ttlMillis */, parseNumericAddress("2001:db8::5")),
                        MdnsInetAddressRecord(arrayOf("TestHost", "local"),
                                0L /* receiptTimeMillis */, true /* cacheFlush */,
                                0L /* ttlMillis */, parseNumericAddress("2001:db8::6")),
                ) /* answers */,
                emptyList() /* authorityRecords */,
                emptyList() /* additionalRecords */)

        assertEquals(mapOf(TEST_CUSTOM_HOST_ID_1 to CONFLICT_HOST),
                repository.getConflictingServices(packet))
    }

    @Test
    fun testGetConflictingServices_customHosts_moreAddressesThanUs_conflict() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.addService(TEST_CUSTOM_HOST_ID_1, TEST_CUSTOM_HOST_1, null /* ttl */)
        repository.addService(TEST_CUSTOM_HOST_ID_2, TEST_CUSTOM_HOST_2, null /* ttl */)

        val packet = MdnsPacket(
                0, /* flags */
                emptyList(), /* questions */
                listOf(
                        MdnsInetAddressRecord(arrayOf("TestHost", "local"),
                                0L /* receiptTimeMillis */, true /* cacheFlush */,
                                0L /* ttlMillis */, parseNumericAddress("2001:db8::1")),
                        MdnsInetAddressRecord(arrayOf("TestHost", "local"),
                                0L /* receiptTimeMillis */, true /* cacheFlush */,
                                0L /* ttlMillis */, parseNumericAddress("2001:db8::2")),
                        MdnsInetAddressRecord(arrayOf("TestHost", "local"),
                                0L /* receiptTimeMillis */, true /* cacheFlush */,
                                0L /* ttlMillis */, parseNumericAddress("2001:db8::3")),
                ) /* answers */,
                emptyList() /* authorityRecords */,
                emptyList() /* additionalRecords */)

        assertEquals(mapOf(TEST_CUSTOM_HOST_ID_1 to CONFLICT_HOST),
                repository.getConflictingServices(packet))
    }

    @Test
    fun testGetConflictingServices_customHostsReplyHasFewerAddressesThanUs_noConflict() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.addService(TEST_CUSTOM_HOST_ID_1, TEST_CUSTOM_HOST_1, null /* ttl */)
        repository.addService(TEST_CUSTOM_HOST_ID_2, TEST_CUSTOM_HOST_2, null /* ttl */)

        val packet = MdnsPacket(
                0, /* flags */
                emptyList(), /* questions */
                listOf(
                        MdnsInetAddressRecord(arrayOf("TestHost", "local"),
                                0L /* receiptTimeMillis */, true /* cacheFlush */,
                                0L /* ttlMillis */, parseNumericAddress("2001:db8::2")),
                ) /* answers */,
                emptyList() /* authorityRecords */,
                emptyList() /* additionalRecords */)

        assertEquals(emptyMap(),
                repository.getConflictingServices(packet))
    }

    @Test
    fun testGetConflictingServices_customHostsReplyHasIdenticalHosts_noConflict() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.addService(TEST_CUSTOM_HOST_ID_1, TEST_CUSTOM_HOST_1, null /* ttl */)
        repository.addService(TEST_CUSTOM_HOST_ID_2, TEST_CUSTOM_HOST_2, null /* ttl */)

        val packet = MdnsPacket(
                0, /* flags */
                emptyList(), /* questions */
                listOf(
                        MdnsInetAddressRecord(arrayOf("TestHost", "local"),
                                0L /* receiptTimeMillis */, true /* cacheFlush */,
                                0L /* ttlMillis */, parseNumericAddress("2001:db8::1")),
                        MdnsInetAddressRecord(arrayOf("TestHost", "local"),
                                0L /* receiptTimeMillis */, true /* cacheFlush */,
                                0L /* ttlMillis */, parseNumericAddress("2001:db8::2")),
                ) /* answers */,
                emptyList() /* authorityRecords */,
                emptyList() /* additionalRecords */)

        assertEquals(emptyMap(),
                repository.getConflictingServices(packet))
    }


    @Test
    fun testGetConflictingServices_customHostsCaseInsensitiveReplyHasIdenticalHosts_noConflict() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.addService(TEST_CUSTOM_HOST_ID_1, TEST_CUSTOM_HOST_1, null /* ttl */)
        repository.addService(TEST_CUSTOM_HOST_ID_2, TEST_CUSTOM_HOST_2, null /* ttl */)

        val packet = MdnsPacket(
                0, /* flags */
                emptyList(), /* questions */
                listOf(
                        MdnsInetAddressRecord(arrayOf("TESTHOST", "local"),
                                0L /* receiptTimeMillis */, true /* cacheFlush */,
                                0L /* ttlMillis */, parseNumericAddress("2001:db8::1")),
                        MdnsInetAddressRecord(arrayOf("testhost", "local"),
                                0L /* receiptTimeMillis */, true /* cacheFlush */,
                                0L /* ttlMillis */, parseNumericAddress("2001:db8::2")),
                ) /* answers */,
                emptyList() /* authorityRecords */,
                emptyList() /* additionalRecords */)

        assertEquals(emptyMap(),
                repository.getConflictingServices(packet))
    }

    @Test
    fun testGetConflictingServices_IdenticalService() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.addService(TEST_SERVICE_ID_1, TEST_SERVICE_1, null /* ttl */)
        repository.addService(TEST_SERVICE_ID_2, TEST_SERVICE_2, null /* ttl */)

        val otherTtlMillis = 1234L
        val packet = MdnsPacket(
                0 /* flags */,
                emptyList() /* questions */,
                listOf(
                        MdnsServiceRecord(
                                arrayOf("MyTestService", "_testservice", "_tcp", "local"),
                                0L /* receiptTimeMillis */, true /* cacheFlush */,
                                otherTtlMillis, 0 /* servicePriority */, 0 /* serviceWeight */,
                                TEST_SERVICE_1.port,
                                arrayOf("ANDROID_000102030405060708090A0B0C0D0E0F", "local")),
                        MdnsTextRecord(
                                arrayOf("MyOtherTestService", "_testservice", "_tcp", "local"),
                                0L /* receiptTimeMillis */, true /* cacheFlush */,
                                otherTtlMillis, emptyList()),
                ) /* answers */,
                emptyList() /* authorityRecords */,
                emptyList() /* additionalRecords */)

        // Above records are identical to the actual registrations: no conflict
        assertEquals(emptyMap(), repository.getConflictingServices(packet))
    }

    @Test
    fun testGetConflictingServicesCaseInsensitive_IdenticalService() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.addService(TEST_SERVICE_ID_1, TEST_SERVICE_1, null /* ttl */)
        repository.addService(TEST_SERVICE_ID_2, TEST_SERVICE_2, null /* ttl */)

        val otherTtlMillis = 1234L
        val packet = MdnsPacket(
                0 /* flags */,
                emptyList() /* questions */,
                listOf(
                        MdnsServiceRecord(
                                arrayOf("MYTESTSERVICE", "_TESTSERVICE", "_tcp", "local"),
                                0L /* receiptTimeMillis */, true /* cacheFlush */,
                                otherTtlMillis, 0 /* servicePriority */, 0 /* serviceWeight */,
                                TEST_SERVICE_1.port,
                                TEST_HOSTNAME),
                        MdnsTextRecord(
                                arrayOf("MyOtherTestService", "_TESTSERVICE", "_tcp", "local"),
                                0L /* receiptTimeMillis */, true /* cacheFlush */,
                                otherTtlMillis, emptyList()),
                ) /* answers */,
                emptyList() /* authorityRecords */,
                emptyList() /* additionalRecords */)

        // Above records are identical to the actual registrations: no conflict
        assertEquals(emptyMap(), repository.getConflictingServices(packet))
    }

    @Test
    fun testGetServiceRepliedRequestsCount() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1)
        // Verify that there is no packet replied.
        assertEquals(MdnsConstants.NO_PACKET,
                repository.getServiceRepliedRequestsCount(TEST_SERVICE_ID_1))

        val questions = listOf(
                MdnsPointerRecord(arrayOf("_testservice", "_tcp", "local"), false /* isUnicast */))
        val query = MdnsPacket(0 /* flags */, questions, emptyList() /* answers */,
                emptyList() /* authorityRecords */, emptyList() /* additionalRecords */)
        val src = InetSocketAddress(parseNumericAddress("192.0.2.123"), 5353)

        // Reply to the question and verify there is one packet replied.
        val reply = repository.getReply(query, src)
        assertNotNull(reply)
        assertEquals(1, repository.getServiceRepliedRequestsCount(TEST_SERVICE_ID_1))

        // No package replied for unknown service.
        assertEquals(MdnsConstants.NO_PACKET,
                repository.getServiceRepliedRequestsCount(TEST_SERVICE_ID_2))
    }

    @Test
    fun testIncludeInetAddressRecordsInProbing() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME,
            makeFlags(includeInetAddressesInProbing = true))
        repository.updateAddresses(TEST_ADDRESSES)
        assertEquals(0, repository.servicesCount)
        assertEquals(-1,
                repository.addService(TEST_SERVICE_ID_1, TEST_SERVICE_1, null /* ttl */))
        assertEquals(1, repository.servicesCount)

        val probingInfo = repository.setServiceProbing(TEST_SERVICE_ID_1)
        assertNotNull(probingInfo)
        assertTrue(repository.isProbing(TEST_SERVICE_ID_1))

        assertEquals(TEST_SERVICE_ID_1, probingInfo.serviceId)
        val packet = probingInfo.getPacket(0)

        assertEquals(MdnsConstants.FLAGS_QUERY, packet.flags)
        assertEquals(0, packet.answers.size)
        assertEquals(0, packet.additionalRecords.size)

        assertEquals(2, packet.questions.size)
        val expectedName = arrayOf("MyTestService", "_testservice", "_tcp", "local")
        assertContentEquals(listOf(
            MdnsAnyRecord(expectedName, false /* unicast */),
            MdnsAnyRecord(TEST_HOSTNAME, false /* unicast */),
        ), packet.questions)

        assertEquals(4, packet.authorityRecords.size)
        assertContentEquals(listOf(
            MdnsServiceRecord(
                expectedName,
                0L /* receiptTimeMillis */,
                false /* cacheFlush */,
                SHORT_TTL /* ttlMillis */,
                0 /* servicePriority */,
                0 /* serviceWeight */,
                TEST_PORT,
                TEST_HOSTNAME),
            MdnsInetAddressRecord(
                TEST_HOSTNAME,
                0L /* receiptTimeMillis */,
                false /* cacheFlush */,
                SHORT_TTL /* ttlMillis */,
                TEST_ADDRESSES[0].address),
            MdnsInetAddressRecord(
                TEST_HOSTNAME,
                0L /* receiptTimeMillis */,
                false /* cacheFlush */,
                SHORT_TTL /* ttlMillis */,
                TEST_ADDRESSES[1].address),
            MdnsInetAddressRecord(
                TEST_HOSTNAME,
                0L /* receiptTimeMillis */,
                false /* cacheFlush */,
                SHORT_TTL /* ttlMillis */,
                TEST_ADDRESSES[2].address)
        ), packet.authorityRecords)

        assertContentEquals(intArrayOf(TEST_SERVICE_ID_1), repository.clearServices())
    }

    private fun doGetReplyWithAnswersTest(
            questions: List<MdnsRecord>,
            knownAnswers: List<MdnsRecord>,
            replyAnswers: List<MdnsRecord>,
            additionalAnswers: List<MdnsRecord>
    ) {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME,
            makeFlags(isKnownAnswerSuppressionEnabled = true))
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1)
        val query = MdnsPacket(0 /* flags */, questions, knownAnswers,
                emptyList() /* authorityRecords */, emptyList() /* additionalRecords */)
        val src = InetSocketAddress(parseNumericAddress("192.0.2.123"), 5353)
        val reply = repository.getReply(query, src)

        if (replyAnswers.isEmpty() || additionalAnswers.isEmpty()) {
            assertNull(reply)
            return
        }

        assertNotNull(reply)
        // Source address is IPv4
        assertEquals(MdnsConstants.getMdnsIPv4Address(), reply.destination.address)
        assertEquals(MdnsConstants.MDNS_PORT, reply.destination.port)
        assertEquals(replyAnswers, reply.answers)
        assertEquals(additionalAnswers, reply.additionalAnswers)
        assertEquals(knownAnswers, reply.knownAnswers)
    }

    @Test
    fun testGetReply_HasAnswers() {
        val queriedName = arrayOf("_testservice", "_tcp", "local")
        val questions = listOf(MdnsPointerRecord(queriedName, false /* isUnicast */))
        val knownAnswers = listOf(MdnsPointerRecord(
                arrayOf("_testservice", "_tcp", "local"),
                0L /* receiptTimeMillis */,
                false /* cacheFlush */,
                LONG_TTL,
                arrayOf("MyTestService", "_testservice", "_tcp", "local")))
        doGetReplyWithAnswersTest(questions, knownAnswers, emptyList() /* replyAnswers */,
                emptyList() /* additionalAnswers */)
    }

    @Test
    fun testGetReply_HasAnswers_TtlLessThanHalf() {
        val queriedName = arrayOf("_testservice", "_tcp", "local")
        val serviceName = arrayOf("MyTestService", "_testservice", "_tcp", "local")
        val questions = listOf(MdnsPointerRecord(queriedName, false /* isUnicast */))
        val knownAnswers = listOf(MdnsPointerRecord(
                arrayOf("_testservice", "_tcp", "local"),
                0L /* receiptTimeMillis */,
                false /* cacheFlush */,
                (LONG_TTL / 2 - 1000L),
                arrayOf("MyTestService", "_testservice", "_tcp", "local")))
        val replyAnswers = listOf(MdnsPointerRecord(
                queriedName,
                0L /* receiptTimeMillis */,
                false /* cacheFlush */,
                LONG_TTL,
                serviceName))
        val additionalAnswers = listOf(
                MdnsTextRecord(
                        serviceName,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        LONG_TTL,
                        emptyList() /* entries */),
                MdnsServiceRecord(
                        serviceName,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        SHORT_TTL,
                        0 /* servicePriority */,
                        0 /* serviceWeight */,
                        TEST_PORT,
                        TEST_HOSTNAME),
                MdnsInetAddressRecord(
                        TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        SHORT_TTL,
                        TEST_ADDRESSES[0].address),
                MdnsInetAddressRecord(
                        TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        SHORT_TTL,
                        TEST_ADDRESSES[1].address),
                MdnsInetAddressRecord(
                        TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        SHORT_TTL,
                        TEST_ADDRESSES[2].address),
                MdnsNsecRecord(
                        serviceName,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        LONG_TTL,
                        serviceName /* nextDomain */,
                        intArrayOf(MdnsRecord.TYPE_TXT, MdnsRecord.TYPE_SRV)),
                MdnsNsecRecord(
                        TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        SHORT_TTL,
                        TEST_HOSTNAME /* nextDomain */,
                        intArrayOf(MdnsRecord.TYPE_A, MdnsRecord.TYPE_AAAA)))
        doGetReplyWithAnswersTest(questions, knownAnswers, replyAnswers, additionalAnswers)
    }

    @Test
    fun testGetReply_HasAnotherAnswer() {
        val queriedName = arrayOf("_testservice", "_tcp", "local")
        val serviceName = arrayOf("MyTestService", "_testservice", "_tcp", "local")
        val questions = listOf(MdnsPointerRecord(queriedName, false /* isUnicast */))
        val knownAnswers = listOf(MdnsPointerRecord(
                queriedName,
                0L /* receiptTimeMillis */,
                false /* cacheFlush */,
                LONG_TTL,
                arrayOf("MyOtherTestService", "_testservice", "_tcp", "local")))
        val replyAnswers = listOf(MdnsPointerRecord(
                queriedName,
                0L /* receiptTimeMillis */,
                false /* cacheFlush */,
                LONG_TTL,
                serviceName))
        val additionalAnswers = listOf(
                MdnsTextRecord(
                        serviceName,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        LONG_TTL,
                        emptyList() /* entries */),
                MdnsServiceRecord(
                        serviceName,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        SHORT_TTL,
                        0 /* servicePriority */,
                        0 /* serviceWeight */,
                        TEST_PORT,
                        TEST_HOSTNAME),
                MdnsInetAddressRecord(
                        TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        SHORT_TTL,
                        TEST_ADDRESSES[0].address),
                MdnsInetAddressRecord(
                        TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        SHORT_TTL,
                        TEST_ADDRESSES[1].address),
                MdnsInetAddressRecord(
                        TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        SHORT_TTL,
                        TEST_ADDRESSES[2].address),
                MdnsNsecRecord(
                        serviceName,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        LONG_TTL,
                        serviceName /* nextDomain */,
                        intArrayOf(MdnsRecord.TYPE_TXT, MdnsRecord.TYPE_SRV)),
                MdnsNsecRecord(
                        TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        SHORT_TTL,
                        TEST_HOSTNAME /* nextDomain */,
                        intArrayOf(MdnsRecord.TYPE_A, MdnsRecord.TYPE_AAAA)))
        doGetReplyWithAnswersTest(questions, knownAnswers, replyAnswers, additionalAnswers)
    }

    @Test
    fun testGetReply_HasAnswers_MultiQuestions() {
        val queriedName = arrayOf("_testservice", "_tcp", "local")
        val serviceName = arrayOf("MyTestService", "_testservice", "_tcp", "local")
        val questions = listOf(
                MdnsPointerRecord(queriedName, false /* isUnicast */),
                MdnsServiceRecord(serviceName, false /* isUnicast */))
        val knownAnswers = listOf(MdnsPointerRecord(
                queriedName,
                0L /* receiptTimeMillis */,
                false /* cacheFlush */,
                LONG_TTL - 1000L,
                serviceName))
        val replyAnswers = listOf(MdnsServiceRecord(
                serviceName,
                0L /* receiptTimeMillis */,
                false /* cacheFlush */,
                SHORT_TTL /* ttlMillis */,
                0 /* servicePriority */,
                0 /* serviceWeight */,
                TEST_PORT,
                TEST_HOSTNAME))
        val additionalAnswers = listOf(
                MdnsInetAddressRecord(
                        TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        SHORT_TTL,
                        TEST_ADDRESSES[0].address),
                MdnsInetAddressRecord(
                        TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        SHORT_TTL,
                        TEST_ADDRESSES[1].address),
                MdnsInetAddressRecord(
                        TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        SHORT_TTL,
                        TEST_ADDRESSES[2].address),
                MdnsNsecRecord(
                        TEST_HOSTNAME,
                        0L /* receiptTimeMillis */,
                        true /* cacheFlush */,
                        SHORT_TTL,
                        TEST_HOSTNAME /* nextDomain */,
                        intArrayOf(MdnsRecord.TYPE_A, MdnsRecord.TYPE_AAAA)))
        doGetReplyWithAnswersTest(questions, knownAnswers, replyAnswers, additionalAnswers)
    }

    @Test
    fun testGetReply_HasAnswers_MultiQuestions_NoReply() {
        val queriedName = arrayOf("_testservice", "_tcp", "local")
        val serviceName = arrayOf("MyTestService", "_testservice", "_tcp", "local")
        val questions = listOf(
                MdnsPointerRecord(queriedName, false /* isUnicast */),
                MdnsServiceRecord(serviceName, false /* isUnicast */))
        val knownAnswers = listOf(
            MdnsPointerRecord(
                queriedName,
                0L /* receiptTimeMillis */,
                false /* cacheFlush */,
                LONG_TTL - 1000L,
                serviceName
            ),
            MdnsServiceRecord(
                serviceName,
                0L /* receiptTimeMillis */,
                false /* cacheFlush */,
                SHORT_TTL - 15_000L,
                0 /* servicePriority */,
                0 /* serviceWeight */,
                TEST_PORT,
                TEST_HOSTNAME
            )
        )
        doGetReplyWithAnswersTest(questions, knownAnswers, emptyList() /* replyAnswers */,
                emptyList() /* additionalAnswers */)
    }

    @Test
    fun testReplyUnicastToQueryUnicastQuestions() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1)

        // Ask for 2 services, only the first one is known and requests unicast reply
        val questions = listOf(
            MdnsPointerRecord(arrayOf("_testservice", "_tcp", "local"), true /* isUnicast */),
            MdnsPointerRecord(arrayOf("_otherservice", "_tcp", "local"), true /* isUnicast */))
        val query = MdnsPacket(0 /* flags */, questions, emptyList() /* answers */,
                emptyList() /* authorityRecords */, emptyList() /* additionalRecords */)
        val src = InetSocketAddress(parseNumericAddress("2001:db8::123"), 5353)

        // Reply to the question and verify it is sent to the source.
        val reply = repository.getReply(query, src)
        assertNotNull(reply)
        assertEquals(src, reply.destination)
    }

    @Test
    fun testReplyMulticastToQueryUnicastAndMulticastMixedQuestions() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1)
        repository.addServiceAndFinishProbing(TEST_SERVICE_ID_2, NsdServiceInfo().apply {
            serviceType = "_otherservice._tcp"
            serviceName = "OtherTestService"
            port = TEST_PORT
        })

        // Ask for 2 services, both are known and only the first one requests unicast reply
        val questions = listOf(
            MdnsPointerRecord(arrayOf("_testservice", "_tcp", "local"), true /* isUnicast */),
            MdnsPointerRecord(arrayOf("_otherservice", "_tcp", "local"), false /* isUnicast */))
        val query = MdnsPacket(0 /* flags */, questions, emptyList() /* answers */,
                emptyList() /* authorityRecords */, emptyList() /* additionalRecords */)
        val src = InetSocketAddress(parseNumericAddress("2001:db8::123"), 5353)

        // Reply to the question and verify it is sent multicast.
        val reply = repository.getReply(query, src)
        assertNotNull(reply)
        assertEquals(MdnsConstants.getMdnsIPv6Address(), reply.destination.address)
    }

    @Test
    fun testReplyMulticastWhenNoUnicastQueryMatches() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME, makeFlags())
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1)

        // Ask for 2 services, the first one requests a unicast reply but is unknown
        val questions = listOf(
            MdnsPointerRecord(arrayOf("_otherservice", "_tcp", "local"), true /* isUnicast */),
            MdnsPointerRecord(arrayOf("_testservice", "_tcp", "local"), false /* isUnicast */))
        val query = MdnsPacket(0 /* flags */, questions, emptyList() /* answers */,
                emptyList() /* authorityRecords */, emptyList() /* additionalRecords */)
        val src = InetSocketAddress(parseNumericAddress("2001:db8::123"), 5353)

        // Reply to the question and verify it is sent multicast.
        val reply = repository.getReply(query, src)
        assertNotNull(reply)
        assertEquals(MdnsConstants.getMdnsIPv6Address(), reply.destination.address)
    }

    @Test
    fun testReplyMulticastWhenUnicastFeatureDisabled() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME,
            makeFlags(unicastReplyEnabled = false))
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1)

        // The service is known and requests unicast reply, but the feature is disabled
        val questions = listOf(
            MdnsPointerRecord(arrayOf("_testservice", "_tcp", "local"), true /* isUnicast */))
        val query = MdnsPacket(0 /* flags */, questions, emptyList() /* answers */,
                emptyList() /* authorityRecords */, emptyList() /* additionalRecords */)
        val src = InetSocketAddress(parseNumericAddress("2001:db8::123"), 5353)

        // Reply to the question and verify it is sent multicast.
        val reply = repository.getReply(query, src)
        assertNotNull(reply)
        assertEquals(MdnsConstants.getMdnsIPv6Address(), reply.destination.address)
    }

    @Test
    fun testGetReply_OnlyKnownAnswers() {
        val repository = MdnsRecordRepository(thread.looper, deps, TEST_HOSTNAME,
                makeFlags(isKnownAnswerSuppressionEnabled = true))
        repository.initWithService(TEST_SERVICE_ID_1, TEST_SERVICE_1)
        val knownAnswers = listOf(MdnsPointerRecord(
                arrayOf("_testservice", "_tcp", "local"),
                0L /* receiptTimeMillis */,
                false /* cacheFlush */,
                LONG_TTL - 1000L,
                arrayOf("MyTestService", "_testservice", "_tcp", "local")))
        val query = MdnsPacket(MdnsConstants.FLAG_TRUNCATED /* flags */, emptyList(),
                knownAnswers, emptyList() /* authorityRecords */,
                emptyList() /* additionalRecords */)
        val src = InetSocketAddress(parseNumericAddress("192.0.2.123"), 5353)
        val reply = repository.getReply(query, src)
        assertNotNull(reply)
        assertEquals(0, reply.answers.size)
        assertEquals(0, reply.additionalAnswers.size)
        assertEquals(knownAnswers, reply.knownAnswers)
    }
}

private fun MdnsRecordRepository.initWithService(
    serviceId: Int,
    serviceInfo: NsdServiceInfo,
    subtypes: Set<String> = setOf(),
    addresses: List<LinkAddress> = TEST_ADDRESSES
): AnnouncementInfo {
    updateAddresses(addresses)
    serviceInfo.setSubtypes(subtypes)
    return addServiceAndFinishProbing(serviceId, serviceInfo)
}

private fun MdnsRecordRepository.addServiceAndFinishProbing(
    serviceId: Int,
    serviceInfo: NsdServiceInfo
): AnnouncementInfo {
    addService(serviceId, serviceInfo, null /* ttl */)
    val probingInfo = setServiceProbing(serviceId)
    assertNotNull(probingInfo)
    return onProbingSucceeded(probingInfo)
}
