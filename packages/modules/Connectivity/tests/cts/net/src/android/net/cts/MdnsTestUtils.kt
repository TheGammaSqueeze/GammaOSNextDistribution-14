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
package android.net.cts

import android.net.DnsResolver
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Process
import com.android.net.module.util.ArrayTrackRecord
import com.android.net.module.util.DnsPacket
import com.android.net.module.util.NetworkStackConstants.ETHER_HEADER_LEN
import com.android.net.module.util.NetworkStackConstants.IPV6_ADDR_LEN
import com.android.net.module.util.NetworkStackConstants.IPV6_DST_ADDR_OFFSET
import com.android.net.module.util.NetworkStackConstants.IPV6_HEADER_LEN
import com.android.net.module.util.NetworkStackConstants.UDP_HEADER_LEN
import com.android.net.module.util.TrackRecord
import com.android.testutils.IPv6UdpFilter
import com.android.testutils.TapPacketReader
import java.net.Inet6Address
import java.net.InetAddress
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

private const val MDNS_REGISTRATION_TIMEOUT_MS = 10_000L
private const val MDNS_PORT = 5353.toShort()
const val MDNS_CALLBACK_TIMEOUT = 2000L
const val MDNS_NO_CALLBACK_TIMEOUT_MS = 200L

interface NsdEvent
open class NsdRecord<T : NsdEvent> private constructor(
    private val history: ArrayTrackRecord<T>,
    private val expectedThreadId: Int? = null
) : TrackRecord<T> by history {
    constructor(expectedThreadId: Int? = null) : this(ArrayTrackRecord(), expectedThreadId)

    val nextEvents = history.newReadHead()

    override fun add(e: T): Boolean {
        if (expectedThreadId != null) {
            assertEquals(
                expectedThreadId, Process.myTid(),
                "Callback is running on the wrong thread"
            )
        }
        return history.add(e)
    }

    inline fun <reified V : NsdEvent> expectCallbackEventually(
        timeoutMs: Long = MDNS_CALLBACK_TIMEOUT,
        crossinline predicate: (V) -> Boolean = { true }
    ): V = nextEvents.poll(timeoutMs) { e -> e is V && predicate(e) } as V?
        ?: fail("Callback for ${V::class.java.simpleName} not seen after $timeoutMs ms")

    inline fun <reified V : NsdEvent> expectCallback(timeoutMs: Long = MDNS_CALLBACK_TIMEOUT): V {
        val nextEvent = nextEvents.poll(timeoutMs)
        assertNotNull(
            nextEvent, "No callback received after $timeoutMs ms, expected " +
                    "${V::class.java.simpleName}"
        )
        assertTrue(
            nextEvent is V, "Expected ${V::class.java.simpleName} but got " +
                    nextEvent.javaClass.simpleName
        )
        return nextEvent
    }

    inline fun assertNoCallback(timeoutMs: Long = MDNS_NO_CALLBACK_TIMEOUT_MS) {
        val cb = nextEvents.poll(timeoutMs)
        assertNull(cb, "Expected no callback but got $cb")
    }
}

class NsdDiscoveryRecord(expectedThreadId: Int? = null) :
    NsdManager.DiscoveryListener, NsdRecord<NsdDiscoveryRecord.DiscoveryEvent>(expectedThreadId) {
    sealed class DiscoveryEvent : NsdEvent {
        data class StartDiscoveryFailed(val serviceType: String, val errorCode: Int) :
            DiscoveryEvent()

        data class StopDiscoveryFailed(val serviceType: String, val errorCode: Int) :
            DiscoveryEvent()

        data class DiscoveryStarted(val serviceType: String) : DiscoveryEvent()
        data class DiscoveryStopped(val serviceType: String) : DiscoveryEvent()
        data class ServiceFound(val serviceInfo: NsdServiceInfo) : DiscoveryEvent()
        data class ServiceLost(val serviceInfo: NsdServiceInfo) : DiscoveryEvent()
    }

    override fun onStartDiscoveryFailed(serviceType: String, err: Int) {
        add(DiscoveryEvent.StartDiscoveryFailed(serviceType, err))
    }

    override fun onStopDiscoveryFailed(serviceType: String, err: Int) {
        add(DiscoveryEvent.StopDiscoveryFailed(serviceType, err))
    }

    override fun onDiscoveryStarted(serviceType: String) {
        add(DiscoveryEvent.DiscoveryStarted(serviceType))
    }

    override fun onDiscoveryStopped(serviceType: String) {
        add(DiscoveryEvent.DiscoveryStopped(serviceType))
    }

    override fun onServiceFound(si: NsdServiceInfo) {
        add(DiscoveryEvent.ServiceFound(si))
    }

    override fun onServiceLost(si: NsdServiceInfo) {
        add(DiscoveryEvent.ServiceLost(si))
    }

    fun waitForServiceDiscovered(
        serviceName: String,
        serviceType: String,
        expectedNetwork: Network? = null
    ): NsdServiceInfo {
        val serviceFound = expectCallbackEventually<DiscoveryEvent.ServiceFound> {
            it.serviceInfo.serviceName == serviceName &&
                    (expectedNetwork == null ||
                            expectedNetwork == it.serviceInfo.network)
        }.serviceInfo
        // Discovered service types have a dot at the end
        assertEquals("$serviceType.", serviceFound.serviceType)
        return serviceFound
    }
}

class NsdRegistrationRecord(expectedThreadId: Int? = null) : NsdManager.RegistrationListener,
    NsdRecord<NsdRegistrationRecord.RegistrationEvent>(expectedThreadId) {
    sealed class RegistrationEvent : NsdEvent {
        abstract val serviceInfo: NsdServiceInfo

        data class RegistrationFailed(
            override val serviceInfo: NsdServiceInfo,
            val errorCode: Int
        ) : RegistrationEvent()

        data class UnregistrationFailed(
            override val serviceInfo: NsdServiceInfo,
            val errorCode: Int
        ) : RegistrationEvent()

        data class ServiceRegistered(override val serviceInfo: NsdServiceInfo) :
            RegistrationEvent()

        data class ServiceUnregistered(override val serviceInfo: NsdServiceInfo) :
            RegistrationEvent()
    }

    override fun onRegistrationFailed(si: NsdServiceInfo, err: Int) {
        add(RegistrationEvent.RegistrationFailed(si, err))
    }

    override fun onUnregistrationFailed(si: NsdServiceInfo, err: Int) {
        add(RegistrationEvent.UnregistrationFailed(si, err))
    }

    override fun onServiceRegistered(si: NsdServiceInfo) {
        add(RegistrationEvent.ServiceRegistered(si))
    }

    override fun onServiceUnregistered(si: NsdServiceInfo) {
        add(RegistrationEvent.ServiceUnregistered(si))
    }
}

class NsdResolveRecord : NsdManager.ResolveListener,
    NsdRecord<NsdResolveRecord.ResolveEvent>() {
    sealed class ResolveEvent : NsdEvent {
        data class ResolveFailed(val serviceInfo: NsdServiceInfo, val errorCode: Int) :
            ResolveEvent()

        data class ServiceResolved(val serviceInfo: NsdServiceInfo) : ResolveEvent()
        data class ResolutionStopped(val serviceInfo: NsdServiceInfo) : ResolveEvent()
        data class StopResolutionFailed(val serviceInfo: NsdServiceInfo, val errorCode: Int) :
            ResolveEvent()
    }

    override fun onResolveFailed(si: NsdServiceInfo, err: Int) {
        add(ResolveEvent.ResolveFailed(si, err))
    }

    override fun onServiceResolved(si: NsdServiceInfo) {
        add(ResolveEvent.ServiceResolved(si))
    }

    override fun onResolutionStopped(si: NsdServiceInfo) {
        add(ResolveEvent.ResolutionStopped(si))
    }

    override fun onStopResolutionFailed(si: NsdServiceInfo, err: Int) {
        super.onStopResolutionFailed(si, err)
        add(ResolveEvent.StopResolutionFailed(si, err))
    }
}

class NsdServiceInfoCallbackRecord : NsdManager.ServiceInfoCallback,
    NsdRecord<NsdServiceInfoCallbackRecord.ServiceInfoCallbackEvent>() {
    sealed class ServiceInfoCallbackEvent : NsdEvent {
        data class RegisterCallbackFailed(val errorCode: Int) : ServiceInfoCallbackEvent()
        data class ServiceUpdated(val serviceInfo: NsdServiceInfo) : ServiceInfoCallbackEvent()
        object ServiceUpdatedLost : ServiceInfoCallbackEvent()
        object UnregisterCallbackSucceeded : ServiceInfoCallbackEvent()
    }

    override fun onServiceInfoCallbackRegistrationFailed(err: Int) {
        add(ServiceInfoCallbackEvent.RegisterCallbackFailed(err))
    }

    override fun onServiceUpdated(si: NsdServiceInfo) {
        add(ServiceInfoCallbackEvent.ServiceUpdated(si))
    }

    override fun onServiceLost() {
        add(ServiceInfoCallbackEvent.ServiceUpdatedLost)
    }

    override fun onServiceInfoCallbackUnregistered() {
        add(ServiceInfoCallbackEvent.UnregisterCallbackSucceeded)
    }
}

private fun getMdnsPayload(packet: ByteArray) = packet.copyOfRange(
    ETHER_HEADER_LEN + IPV6_HEADER_LEN + UDP_HEADER_LEN, packet.size)

private fun getDstAddr(packet: ByteArray): Inet6Address {
    val v6AddrPos = ETHER_HEADER_LEN + IPV6_DST_ADDR_OFFSET
    return Inet6Address.getByAddress(packet.copyOfRange(v6AddrPos, v6AddrPos + IPV6_ADDR_LEN))
            as Inet6Address
}

fun TapPacketReader.pollForMdnsPacket(
    timeoutMs: Long = MDNS_REGISTRATION_TIMEOUT_MS,
    predicate: (TestDnsPacket) -> Boolean
): TestDnsPacket? {
    val mdnsProbeFilter = IPv6UdpFilter(srcPort = MDNS_PORT, dstPort = MDNS_PORT).and {
        val dst = getDstAddr(it)
        val mdnsPayload = getMdnsPayload(it)
        try {
            predicate(TestDnsPacket(mdnsPayload, dst))
        } catch (e: DnsPacket.ParseException) {
            false
        }
    }
    return poll(timeoutMs, mdnsProbeFilter)?.let {
        TestDnsPacket(getMdnsPayload(it), getDstAddr(it))
    }
}

fun TapPacketReader.pollForProbe(
    serviceName: String,
    serviceType: String,
    timeoutMs: Long = MDNS_REGISTRATION_TIMEOUT_MS
): TestDnsPacket? = pollForMdnsPacket(timeoutMs) {
    it.isProbeFor("$serviceName.$serviceType.local")
}

fun TapPacketReader.pollForAdvertisement(
    serviceName: String,
    serviceType: String,
    timeoutMs: Long = MDNS_REGISTRATION_TIMEOUT_MS
): TestDnsPacket? = pollForMdnsPacket(timeoutMs) {
    it.isReplyFor("$serviceName.$serviceType.local")
}

fun TapPacketReader.pollForQuery(
    recordName: String,
    vararg requiredTypes: Int,
    timeoutMs: Long = MDNS_REGISTRATION_TIMEOUT_MS
): TestDnsPacket? = pollForMdnsPacket(timeoutMs) { it.isQueryFor(recordName, *requiredTypes) }

fun TapPacketReader.pollForReply(
    serviceName: String,
    serviceType: String,
    timeoutMs: Long = MDNS_REGISTRATION_TIMEOUT_MS
): TestDnsPacket? = pollForMdnsPacket(timeoutMs) {
    it.isReplyFor("$serviceName.$serviceType.local")
}

class TestDnsPacket(data: ByteArray, val dstAddr: InetAddress) : DnsPacket(data) {
    val header: DnsHeader
        get() = mHeader
    val records: Array<List<DnsRecord>>
        get() = mRecords
    fun isProbeFor(name: String): Boolean = mRecords[QDSECTION].any {
        it.dName == name && it.nsType == DnsResolver.TYPE_ANY
    }

    fun isReplyFor(name: String, type: Int = DnsResolver.TYPE_SRV): Boolean =
        mRecords[ANSECTION].any {
            it.dName == name && it.nsType == type
        }

    fun isQueryFor(name: String, vararg requiredTypes: Int): Boolean = requiredTypes.all { type ->
        mRecords[QDSECTION].any {
            it.dName == name && it.nsType == type
        }
    }
}
