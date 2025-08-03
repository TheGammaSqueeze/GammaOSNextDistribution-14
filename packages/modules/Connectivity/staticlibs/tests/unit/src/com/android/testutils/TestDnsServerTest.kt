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

package com.android.testutils

import android.net.DnsResolver.CLASS_IN
import android.net.DnsResolver.TYPE_AAAA
import android.net.Network
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.net.module.util.DnsPacket
import com.android.net.module.util.DnsPacket.DnsRecord
import libcore.net.InetAddressUtils
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

val TEST_V6_ADDR = InetAddressUtils.parseNumericAddress("2001:db8::3")
const val TEST_DOMAIN = "hello.example.com"

@RunWith(AndroidJUnit4::class)
@SmallTest
class TestDnsServerTest {
    private val network = Mockito.mock(Network::class.java)
    private val localAddr = InetSocketAddress(InetAddress.getLocalHost(), 0 /* port */)
    private val testServer: TestDnsServer = TestDnsServer(network, localAddr)

    @After
    fun tearDown() {
        if (testServer.isAlive) testServer.stop()
    }

    @Test
    fun testStartStop() {
        repeat(100) {
            val server = TestDnsServer(network, localAddr)
            server.start()
            assertTrue(server.isAlive)
            server.stop()
            assertFalse(server.isAlive)
        }

        // Test illegal start/stop.
        assertFailsWith<IllegalStateException> { testServer.stop() }
        testServer.start()
        assertTrue(testServer.isAlive)
        assertFailsWith<IllegalStateException> { testServer.start() }
        testServer.stop()
        assertFalse(testServer.isAlive)
        assertFailsWith<IllegalStateException> { testServer.stop() }
        // TestDnsServer rejects start after stop.
        assertFailsWith<IllegalStateException> { testServer.start() }
    }

    @Test
    fun testHandleDnsQuery() {
        testServer.setAnswer(TEST_DOMAIN, listOf(TEST_V6_ADDR))
        testServer.start()

        // Mock query and send it to the test server.
        val queryHeader = DnsPacket.DnsHeader(0xbeef /* id */,
                0x0 /* flag */, 1 /* qcount */, 0 /* ancount */)
        val qlist = listOf(DnsRecord.makeQuestion(TEST_DOMAIN, TYPE_AAAA, CLASS_IN))
        val queryPacket = TestDnsServer.DnsQueryPacket(queryHeader, qlist, emptyList())
        val response = resolve(queryPacket, testServer.port)

        // Verify expected answer packet. Set QR bit of flag to 1 for response packet
        // according to RFC 1035 section 4.1.1.
        val answerHeader = DnsPacket.DnsHeader(0xbeef,
            1 shl 15 /* flag */, 1 /* qcount */, 1 /* ancount */)
        val alist = listOf(DnsRecord.makeAOrAAAARecord(DnsPacket.ANSECTION, TEST_DOMAIN,
                    CLASS_IN, DEFAULT_TTL_S, TEST_V6_ADDR))
        val expectedAnswerPacket = TestDnsServer.DnsAnswerPacket(answerHeader, qlist, alist)
        assertEquals(expectedAnswerPacket, response)

        // Clean up the server in tearDown.
    }

    private fun resolve(queryDnsPacket: DnsPacket, serverPort: Int): TestDnsServer.DnsAnswerPacket {
        val bytes = queryDnsPacket.bytes
        // Create a new client socket, the socket will be bound to a
        // random port other than the server port.
        val socket = DatagramSocket(localAddr).also { it.soTimeout = 100 }
        val queryPacket = DatagramPacket(bytes, bytes.size, localAddr.address, serverPort)

        // Send query and wait for the reply.
        socket.send(queryPacket)
        val buffer = ByteArray(MAX_BUF_SIZE)
        val reply = DatagramPacket(buffer, buffer.size)
        socket.receive(reply)
        return TestDnsServer.DnsAnswerPacket(reply.data)
    }

    // TODO: Add more tests, which includes:
    //  * Empty question RR packet (or more unexpected states)
    //  * No answer found (setAnswer empty list at L.78)
    //  * Test one or multi A record(s)
    //  * Test multi AAAA records
    //  * Test CNAME records
}
