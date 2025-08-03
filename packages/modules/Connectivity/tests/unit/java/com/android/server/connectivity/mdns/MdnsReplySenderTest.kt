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

package com.android.server.connectivity.mdns

import android.net.InetAddresses
import android.net.LinkAddress
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.android.net.module.util.SharedLog
import com.android.server.connectivity.mdns.MdnsConstants.IPV4_SOCKET_ADDR
import com.android.server.connectivity.mdns.MdnsConstants.IPV6_SOCKET_ADDR
import com.android.server.connectivity.mdns.MdnsReplySender.getReplyDestination
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.argThat
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

private const val TEST_PORT = 12345
private const val DEFAULT_TIMEOUT_MS = 2000L
private const val LONG_TTL = 4_500_000L
private const val SHORT_TTL = 120_000L

@RunWith(DevSdkIgnoreRunner::class)
@IgnoreUpTo(Build.VERSION_CODES.S_V2)
class MdnsReplySenderTest {
    private val serviceName = arrayOf("MyTestService", "_testservice", "_tcp", "local")
    private val otherServiceName = arrayOf("OtherTestService", "_testservice", "_tcp", "local")
    private val serviceType = arrayOf("_testservice", "_tcp", "local")
    private val source = InetSocketAddress(
            InetAddresses.parseNumericAddress("192.0.2.1"), TEST_PORT)
    private val hostname = arrayOf("Android_000102030405060708090A0B0C0D0E0F", "local")
    private val otherHostname = arrayOf("Android_0F0E0D0C0B0A09080706050403020100", "local")
    private val hostAddresses = listOf(
            LinkAddress(InetAddresses.parseNumericAddress("192.0.2.111"), 24),
            LinkAddress(InetAddresses.parseNumericAddress("2001:db8::111"), 64),
            LinkAddress(InetAddresses.parseNumericAddress("2001:db8::222"), 64))
    private val answers = listOf(
            MdnsPointerRecord(serviceType, 0L /* receiptTimeMillis */, false /* cacheFlush */,
                    LONG_TTL, serviceName))
    private val otherAnswers = listOf(
            MdnsPointerRecord(serviceType, 0L /* receiptTimeMillis */, false /* cacheFlush */,
                    LONG_TTL, otherServiceName))
    private val additionalAnswers = listOf(
            MdnsTextRecord(serviceName, 0L /* receiptTimeMillis */, true /* cacheFlush */, LONG_TTL,
                    emptyList() /* entries */),
            MdnsServiceRecord(serviceName, 0L /* receiptTimeMillis */, true /* cacheFlush */,
                    SHORT_TTL, 0 /* servicePriority */, 0 /* serviceWeight */, TEST_PORT, hostname),
            MdnsInetAddressRecord(hostname, 0L /* receiptTimeMillis */, true /* cacheFlush */,
                    SHORT_TTL, hostAddresses[0].address),
            MdnsInetAddressRecord(hostname, 0L /* receiptTimeMillis */, true /* cacheFlush */,
                    SHORT_TTL, hostAddresses[1].address),
            MdnsInetAddressRecord(hostname, 0L /* receiptTimeMillis */, true /* cacheFlush */,
                    SHORT_TTL, hostAddresses[2].address),
            MdnsNsecRecord(serviceName, 0L /* receiptTimeMillis */, true /* cacheFlush */, LONG_TTL,
                    serviceName /* nextDomain */,
                    intArrayOf(MdnsRecord.TYPE_TXT, MdnsRecord.TYPE_SRV)),
            MdnsNsecRecord(hostname, 0L /* receiptTimeMillis */, true /* cacheFlush */, SHORT_TTL,
                    hostname /* nextDomain */, intArrayOf(MdnsRecord.TYPE_A, MdnsRecord.TYPE_AAAA)))
    private val otherAdditionalAnswers = listOf(
            MdnsTextRecord(otherServiceName, 0L /* receiptTimeMillis */, true /* cacheFlush */,
                    LONG_TTL, emptyList() /* entries */),
            MdnsServiceRecord(otherServiceName, 0L /* receiptTimeMillis */, true /* cacheFlush */,
                    SHORT_TTL, 0 /* servicePriority */, 0 /* serviceWeight */, TEST_PORT,
                    otherHostname),
            MdnsInetAddressRecord(otherHostname, 0L /* receiptTimeMillis */, true /* cacheFlush */,
                    SHORT_TTL, hostAddresses[0].address),
            MdnsInetAddressRecord(otherHostname, 0L /* receiptTimeMillis */, true /* cacheFlush */,
                    SHORT_TTL, hostAddresses[1].address),
            MdnsInetAddressRecord(otherHostname, 0L /* receiptTimeMillis */, true /* cacheFlush */,
                    SHORT_TTL, hostAddresses[2].address),
            MdnsNsecRecord(otherServiceName, 0L /* receiptTimeMillis */, true /* cacheFlush */,
                    LONG_TTL, otherServiceName /* nextDomain */,
                    intArrayOf(MdnsRecord.TYPE_TXT, MdnsRecord.TYPE_SRV)),
            MdnsNsecRecord(otherHostname, 0L /* receiptTimeMillis */, true /* cacheFlush */,
                    SHORT_TTL, otherHostname /* nextDomain */,
                    intArrayOf(MdnsRecord.TYPE_A, MdnsRecord.TYPE_AAAA)))
    private val thread = HandlerThread(MdnsReplySenderTest::class.simpleName)
    private val socket = mock(MdnsInterfaceSocket::class.java)
    private val buffer = ByteArray(1500)
    private val sharedLog = SharedLog(MdnsReplySenderTest::class.simpleName)
    private val deps = mock(MdnsReplySender.Dependencies::class.java)
    private val handler by lazy { Handler(thread.looper) }

    @Before
    fun setUp() {
        thread.start()
        doReturn(true).`when`(socket).hasJoinedIpv4()
        doReturn(true).`when`(socket).hasJoinedIpv6()
    }

    @After
    fun tearDown() {
        thread.quitSafely()
        thread.join()
    }

    private fun <T> runningOnHandlerAndReturn(functor: (() -> T)): T {
        val future = CompletableFuture<T>()
        handler.post {
            future.complete(functor())
        }
        return future.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    private fun sendNow(sender: MdnsReplySender, packet: MdnsPacket, dest: InetSocketAddress):
            Unit = runningOnHandlerAndReturn { sender.sendNow(packet, dest) }

    private fun queueReply(sender: MdnsReplySender, reply: MdnsReplyInfo):
            Unit = runningOnHandlerAndReturn { sender.queueReply(reply) }

    private fun buildFlags(enableKAS: Boolean): MdnsFeatureFlags {
        return MdnsFeatureFlags.newBuilder()
                .setIsKnownAnswerSuppressionEnabled(enableKAS).build()
    }

    private fun createSender(enableKAS: Boolean): MdnsReplySender =
            MdnsReplySender(thread.looper, socket, buffer, sharedLog, false /* enableDebugLog */,
                    deps, buildFlags(enableKAS))

    @Test
    fun testSendNow() {
        val replySender = createSender(enableKAS = false)
        val packet = MdnsPacket(0x8400,
                emptyList() /* questions */,
                answers,
                emptyList() /* authorityRecords */,
                additionalAnswers)
        sendNow(replySender, packet, IPV4_SOCKET_ADDR)
        verify(socket).send(argThat{ it.socketAddress.equals(IPV4_SOCKET_ADDR) })
    }

    private fun verifyMessageQueued(
            sender: MdnsReplySender,
            replies: List<MdnsReplyInfo>
    ): Pair<Handler, Message> {
        val handlerCaptor = ArgumentCaptor.forClass(Handler::class.java)
        val messageCaptor = ArgumentCaptor.forClass(Message::class.java)
        for (reply in replies) {
            queueReply(sender, reply)
            verify(deps).sendMessageDelayed(
                    handlerCaptor.capture(), messageCaptor.capture(), eq(reply.sendDelayMs))
        }
        return Pair(handlerCaptor.value, messageCaptor.value)
    }

    private fun verifyReplySent(
            realHandler: Handler,
            delayMessage: Message,
            remainingAnswers: List<MdnsRecord>
    ) {
        val datagramPacketCaptor = ArgumentCaptor.forClass(DatagramPacket::class.java)
        realHandler.sendMessage(delayMessage)
        verify(socket, timeout(DEFAULT_TIMEOUT_MS)).send(datagramPacketCaptor.capture())

        val dPacket = datagramPacketCaptor.value
        val mdnsPacket = MdnsPacket.parse(MdnsPacketReader(
                dPacket.data, dPacket.length, buildFlags(enableKAS = false)))
        assertEquals(mdnsPacket.answers.toSet(), remainingAnswers.toSet())
    }

    @Test
    fun testQueueReply() {
        val replySender = createSender(enableKAS = false)
        val reply = MdnsReplyInfo(answers, additionalAnswers, 20L /* sendDelayMs */,
                IPV4_SOCKET_ADDR, source, emptyList())
        val (handler, message) = verifyMessageQueued(replySender, listOf(reply))
        verifyReplySent(handler, message, answers)
    }

    @Test
    fun testQueueReply_KnownAnswerSuppressionEnabled() {
        val replySender = createSender(enableKAS = true)
        val reply = MdnsReplyInfo(answers, additionalAnswers, 20L /* sendDelayMs */,
                IPV4_SOCKET_ADDR, source, emptyList())
        val (handler, message) = verifyMessageQueued(replySender, listOf(reply))
        verifyReplySent(handler, message, answers)
    }

    @Test
    fun testQueueReply_MultiplePacket() {
        val replySender = createSender(enableKAS = true)
        val reply = MdnsReplyInfo(answers, additionalAnswers, 400L /* sendDelayMs */,
                IPV4_SOCKET_ADDR, source, emptyList())
        verifyMessageQueued(replySender, listOf(reply))

        // Receive a known-answer packet and verify no message queued.
        val knownAnswersReply = MdnsReplyInfo(emptyList(), emptyList(), 0L /* sendDelayMs */,
                IPV4_SOCKET_ADDR, source, answers)
        queueReply(replySender, knownAnswersReply)
        verify(deps, times(1)).sendMessageDelayed(any(), any(), anyLong())
    }

    @Test
    fun testQueueReply_MultiplePacket_LostSubsequentPacket() {
        val replySender = createSender(enableKAS = true)
        val reply = MdnsReplyInfo(answers, additionalAnswers, 400L /* sendDelayMs */,
                IPV4_SOCKET_ADDR, source, emptyList())
        val (handler, message) = verifyMessageQueued(replySender, listOf(reply))

        // No subsequent packets
        verifyReplySent(handler, message, answers)
    }

    @Test
    fun testQueueReply_MultiplePacket_OtherKnownAnswer() {
        val replySender = createSender(enableKAS = true)
        val reply = MdnsReplyInfo(answers, additionalAnswers, 400L /* sendDelayMs */,
                IPV4_SOCKET_ADDR, source, emptyList())
        // Other known-answer service
        val otherKnownAnswersReply = MdnsReplyInfo(emptyList(), emptyList(), 0L /* sendDelayMs */,
                IPV4_SOCKET_ADDR, source, otherAnswers)
        val (handler, message) = verifyMessageQueued(
                replySender, listOf(reply, otherKnownAnswersReply))
        verifyReplySent(handler, message, answers)
    }

    @Test
    fun testQueueReply_MultiplePacket_TwoKnownAnswerPackets() {
        val replySender = createSender(enableKAS = true)
        val reply = MdnsReplyInfo(answers, additionalAnswers, 400L /* sendDelayMs */,
                IPV4_SOCKET_ADDR, source, emptyList())
        val firstKnownAnswerReply = MdnsReplyInfo(emptyList(), emptyList(), 401L /* sendDelayMs */,
                IPV4_SOCKET_ADDR, source, otherAnswers)
        verifyMessageQueued(replySender, listOf(reply, firstKnownAnswerReply))

        // Second known-answer service
        val secondKnownAnswerReply = MdnsReplyInfo(emptyList(), emptyList(), 0L /* sendDelayMs */,
                IPV4_SOCKET_ADDR, source, answers)
        queueReply(replySender, secondKnownAnswerReply)

        // Verify that no reply is queued, as all answers are known.
        verify(deps, times(2)).sendMessageDelayed(any(), any(), anyLong())
    }

    @Test
    fun testQueueReply_MultiplePacket_LostSecondaryPacket() {
        val replySender = createSender(enableKAS = true)
        val reply = MdnsReplyInfo(answers, additionalAnswers, 400L /* sendDelayMs */,
                IPV4_SOCKET_ADDR, source, emptyList())
        val firstKnownAnswerReply = MdnsReplyInfo(emptyList(), emptyList(), 401L /* sendDelayMs */,
                IPV4_SOCKET_ADDR, source, otherAnswers)
        val (handler, message) = verifyMessageQueued(
                replySender, listOf(reply, firstKnownAnswerReply))

        // Second known-answer service lost
        verifyReplySent(handler, message, answers)
    }

    @Test
    fun testQueueReply_MultiplePacket_WithMultipleQuestions() {
        val replySender = createSender(enableKAS = true)
        val twoAnswers = listOf(
                MdnsPointerRecord(serviceType, 0L /* receiptTimeMillis */, false /* cacheFlush */,
                        LONG_TTL, serviceName),
                MdnsServiceRecord(otherServiceName, 0L /* receiptTimeMillis */,
                        true /* cacheFlush */, SHORT_TTL, 0 /* servicePriority */,
                        0 /* serviceWeight */, TEST_PORT, otherHostname))
        val reply = MdnsReplyInfo(twoAnswers, additionalAnswers, 400L /* sendDelayMs */,
                IPV4_SOCKET_ADDR, source, emptyList())
        val knownAnswersReply = MdnsReplyInfo(otherAnswers, otherAdditionalAnswers,
                20L /* sendDelayMs */, IPV4_SOCKET_ADDR, source, answers)
        val (handler, message) = verifyMessageQueued(replySender, listOf(reply, knownAnswersReply))

        val remainingAnswers = listOf(
                MdnsPointerRecord(serviceType, 0L /* receiptTimeMillis */, false /* cacheFlush */,
                        LONG_TTL, otherServiceName),
                MdnsServiceRecord(otherServiceName, 0L /* receiptTimeMillis */,
                        true /* cacheFlush */, SHORT_TTL, 0 /* servicePriority */,
                        0 /* serviceWeight */, TEST_PORT, otherHostname))
        verifyReplySent(handler, message, remainingAnswers)
    }

    @Test
    fun testGetReplyDestination() {
        assertEquals(IPV4_SOCKET_ADDR, getReplyDestination(IPV4_SOCKET_ADDR, IPV4_SOCKET_ADDR))
        assertEquals(IPV6_SOCKET_ADDR, getReplyDestination(IPV6_SOCKET_ADDR, IPV6_SOCKET_ADDR))
        assertEquals(IPV4_SOCKET_ADDR, getReplyDestination(source, IPV4_SOCKET_ADDR))
        assertEquals(IPV6_SOCKET_ADDR, getReplyDestination(source, IPV6_SOCKET_ADDR))
        assertEquals(source, getReplyDestination(source, source))
    }
}
