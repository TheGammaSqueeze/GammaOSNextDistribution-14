/*
 * Copyright (C) 2020 The Android Open Source Project
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

import java.util.function.Predicate

private const val POLL_FREQUENCY_MS = 1000L

/**
 * A class that can be used to reply to packets from a [TapPacketReader].
 *
 * A reply thread will be created to reply to incoming packets asynchronously.
 * The receiver creates a new read head on the [TapPacketReader], to read packets, so it does not
 * affect packets obtained through [TapPacketReader.popPacket].
 *
 * @param reader a [TapPacketReader] to obtain incoming packets and reply to them.
 * @param packetFilter A filter to apply to incoming packets.
 * @param name Name to use for the internal responder thread.
 */
abstract class PacketResponder(
    private val reader: TapPacketReader,
    private val packetFilter: Predicate<ByteArray>,
    name: String
) {
    private val replyThread = ReplyThread(name)

    protected abstract fun replyToPacket(packet: ByteArray, reader: TapPacketReader)

    /**
     * Start the [PacketResponder].
     */
    fun start() {
        replyThread.start()
    }

    /**
     * Stop the [PacketResponder].
     *
     * The responder cannot be used anymore after being stopped.
     */
    fun stop() {
        replyThread.interrupt()
        replyThread.join()
    }

    private inner class ReplyThread(name: String) : Thread(name) {
        override fun run() {
            try {
                // Create a new ReadHead so other packets polled on the reader are not affected
                val recvPackets = reader.receivedPackets.newReadHead()
                while (!isInterrupted) {
                    recvPackets.poll(POLL_FREQUENCY_MS, packetFilter::test)?.let {
                        replyToPacket(it, reader)
                    }
                }
            } catch (e: InterruptedException) {
                // Exit gracefully
            }
        }
    }
}
