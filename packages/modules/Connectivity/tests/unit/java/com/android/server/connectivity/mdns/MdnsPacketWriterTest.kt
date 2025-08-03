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

import android.net.InetAddresses
import android.os.Build
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import java.net.InetSocketAddress
import kotlin.test.assertContentEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DevSdkIgnoreRunner::class)
@IgnoreUpTo(Build.VERSION_CODES.S_V2)
class MdnsPacketWriterTest {
    @Test
    fun testNameCompression() {
        val writer = MdnsPacketWriter(ByteArray(1000))
        writer.writeLabels(arrayOf("my", "FIRST", "name"))
        writer.writeLabels(arrayOf("my", "second", "name"))
        writer.writeLabels(arrayOf("other", "first", "name"))
        writer.writeLabels(arrayOf("my", "second", "name"))
        writer.writeLabels(arrayOf("unrelated"))

        val packet = writer.getPacket(
                InetSocketAddress(InetAddresses.parseNumericAddress("2001:db8::123"), 123))

        // Each label takes length + 1. So "first.name" offset = 3, "name" offset = 9
        val expected = "my".label() + "FIRST".label() + "name".label() + 0x00.toByte() +
                // "my.second.name" offset = 15
                "my".label() + "second".label() + byteArrayOf(0xC0.toByte(), 9) +
                "other".label() + byteArrayOf(0xC0.toByte(), 3) +
                byteArrayOf(0xC0.toByte(), 15) +
                "unrelated".label() + 0x00.toByte()

        assertContentEquals(expected, packet.data.copyOfRange(0, packet.length))
    }
}

private fun String.label() = byteArrayOf(length.toByte()) + encodeToByteArray()
