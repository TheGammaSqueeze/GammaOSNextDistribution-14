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

import android.net.Network
import android.util.Log
import com.android.internal.annotations.GuardedBy
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.annotations.VisibleForTesting.Visibility.PRIVATE
import com.android.net.module.util.DnsPacket
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketException
import java.util.ArrayList

private const val TAG = "TestDnsServer"
private const val VDBG = true
@VisibleForTesting(visibility = PRIVATE)
const val MAX_BUF_SIZE = 8192

/**
 * A simple implementation of Dns Server that can be bound on specific address and Network.
 *
 * The caller should use start() to make the server start a new thread to receive DNS queries
 * on the bound address, [isAlive] to check status, and stop() for stopping.
 * The server allows user to manipulate the records to be answered through
 * [setAnswer] at runtime.
 *
 * This server runs on its own thread. Please make sure writing the query to the socket
 * happens-after using [setAnswer] to guarantee the correct answer is returned. If possible,
 * use [setAnswer] before calling [start] for simplicity.
 */
class TestDnsServer(network: Network, addr: InetSocketAddress) {
    enum class Status {
        NOT_STARTED, STARTED, STOPPED
    }
    @GuardedBy("thread")
    private var status: Status = Status.NOT_STARTED
    private val thread = ReceivingThread()
    private val socket = DatagramSocket(addr).also { network.bindSocket(it) }
    private val ansProvider = DnsAnswerProvider()

    // The buffer to store the received packet. They are being reused for
    // efficiency and it's fine because they are only ever accessed
    // on the server thread in a sequential manner.
    private val buffer = ByteArray(MAX_BUF_SIZE)
    private val packet = DatagramPacket(buffer, buffer.size)

    fun setAnswer(hostname: String, answer: List<InetAddress>) =
        ansProvider.setAnswer(hostname, answer)

    private fun processPacket() {
        // Blocking read and try construct a DnsQueryPacket object.
        socket.receive(packet)
        val q = DnsQueryPacket(packet.data)
        handleDnsQuery(q, packet.socketAddress)
    }

    // TODO: Add support to reply some error with a DNS reply packet with failure RCODE.
    private fun handleDnsQuery(q: DnsQueryPacket, src: SocketAddress) {
        val queryRecords = q.queryRecords
        if (queryRecords.size != 1) {
            throw IllegalArgumentException(
                "Expected one dns query record but got ${queryRecords.size}"
            )
        }
        val answerRecords = queryRecords[0].let { ansProvider.getAnswer(it.dName, it.nsType) }

        if (VDBG) {
            Log.v(TAG, "handleDnsPacket: " +
                        queryRecords.map { "${it.dName},${it.nsType}" }.joinToString() +
                        " ansCount=${answerRecords.size} socketAddress=$src")
        }

        val bytes = q.getAnswerPacket(answerRecords).bytes
        val reply = DatagramPacket(bytes, bytes.size, src)
        socket.send(reply)
    }

    fun start() {
        synchronized(thread) {
            if (status != Status.NOT_STARTED) {
                throw IllegalStateException("unexpected status: $status")
            }
            thread.start()
            status = Status.STARTED
        }
    }
    fun stop() {
        synchronized(thread) {
            if (status != Status.STARTED) {
                throw IllegalStateException("unexpected status: $status")
            }
            // The thread needs to be interrupted before closing the socket to prevent a data
            // race where the thread tries to read from the socket while it's being closed.
            // DatagramSocket is not thread-safe and running both concurrently can end up in
            // getPort() returning -1 after it's been checked not to, resulting in a crash by
            // IllegalArgumentException inside the DatagramSocket implementation.
            thread.interrupt()
            socket.close()
            thread.join()
            status = Status.STOPPED
        }
    }
    val isAlive get() = thread.isAlive
    val port get() = socket.localPort

    inner class ReceivingThread : Thread() {
        override fun run() {
            while (!interrupted() && !socket.isClosed) {
                try {
                    processPacket()
                } catch (e: InterruptedException) {
                    // The caller terminated the server, exit.
                    break
                } catch (e: SocketException) {
                    // The caller terminated the server, exit.
                    break
                }
            }
            Log.i(TAG, "exiting socket={$socket}")
        }
    }

    @VisibleForTesting(visibility = PRIVATE)
    class DnsQueryPacket : DnsPacket {
        constructor(data: ByteArray) : super(data)
        constructor(header: DnsHeader, qd: List<DnsRecord>, an: List<DnsRecord>) :
                super(header, qd, an)

        init {
            if (mHeader.isResponse) {
                throw ParseException("Not a query packet")
            }
        }

        val queryRecords: List<DnsRecord>
            get() = mRecords[QDSECTION]

        fun getAnswerPacket(ar: List<DnsRecord>): DnsAnswerPacket {
            // Set QR bit of flag to 1 for response packet according to RFC 1035 section 4.1.1.
            val flags = 1 shl 15
            val qr = ArrayList(mRecords[QDSECTION])
            // Copy the query packet header id to the answer packet as RFC 1035 section 4.1.1.
            val header = DnsHeader(mHeader.id, flags, qr.size, ar.size)
            return DnsAnswerPacket(header, qr, ar)
        }
    }

    class DnsAnswerPacket : DnsPacket {
        constructor(header: DnsHeader, qr: List<DnsRecord>, ar: List<DnsRecord>) :
                super(header, qr, ar)
        @VisibleForTesting(visibility = PRIVATE)
        constructor(bytes: ByteArray) : super(bytes)
    }
}
