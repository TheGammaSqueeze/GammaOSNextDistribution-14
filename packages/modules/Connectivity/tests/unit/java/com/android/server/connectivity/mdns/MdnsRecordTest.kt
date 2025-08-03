/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
class MdnsRecordTest {

    @Test
    fun testPointerRecordHasSubType() {
        val ptrRecord1 = MdnsPointerRecord(
            arrayOf("_testtype", "_sub", "_tcp", "local"),
            0L /* receiptTimeMillis */,
            false /* cacheFlush */,
            4500000 /* ttlMillis */,
            arrayOf("testservice", "_testtype", "_tcp", "local")
        )
        val ptrRecord2 = MdnsPointerRecord(
            arrayOf("_testtype", "_SUB", "_tcp", "local"),
            0L /* receiptTimeMillis */,
            false /* cacheFlush */,
            4500000 /* ttlMillis */,
            arrayOf("testservice", "_testtype", "_tcp", "local")
        )
        assertTrue(ptrRecord1.hasSubtype())
        assertTrue(ptrRecord2.hasSubtype())
    }

    @Test
    fun testEqualsCaseInsensitive() {
        val ptrRecord1 = MdnsPointerRecord(
            arrayOf("_testtype", "_tcp", "local"),
            0L /* receiptTimeMillis */,
            false /* cacheFlush */,
            4500000 /* ttlMillis */,
            arrayOf("testservice", "_testtype", "_tcp", "local")
            )
        val ptrRecord2 = MdnsPointerRecord(
            arrayOf("_testType", "_tcp", "local"),
            0L /* receiptTimeMillis */,
            false /* cacheFlush */,
            4500000 /* ttlMillis */,
            arrayOf("testsErvice", "_testtype", "_Tcp", "local")
        )
        assertEquals(ptrRecord1, ptrRecord2)
        assertEquals(ptrRecord1.hashCode(), ptrRecord2.hashCode())

        val srvRecord1 = MdnsServiceRecord(
            arrayOf("testservice", "_testtype", "_tcp", "local"),
            123 /* receiptTimeMillis */,
            false /* cacheFlush */,
            2000 /* ttlMillis */,
            0 /* servicePriority */,
            0 /* serviceWeight */,
            80 /* port */,
            arrayOf("hostname")
        )
        val srvRecord2 = MdnsServiceRecord(
            arrayOf("Testservice", "_testtype", "_tcp", "local"),
            123 /* receiptTimeMillis */,
            false /* cacheFlush */,
            2000 /* ttlMillis */,
            0 /* servicePriority */,
            0 /* serviceWeight */,
            80 /* port */,
            arrayOf("Hostname")
        )
        assertEquals(srvRecord1, srvRecord2)
        assertEquals(srvRecord1.hashCode(), srvRecord2.hashCode())

        val nsecRecord1 = MdnsNsecRecord(
            arrayOf("hostname"),
            0L /* receiptTimeMillis */,
            true /* cacheFlush */,
            2000L, /* ttlMillis */
            arrayOf("hostname"),
            intArrayOf(1, 2, 3) /* types */
        )
        val nsecRecord2 = MdnsNsecRecord(
            arrayOf("HOSTNAME"),
            0L /* receiptTimeMillis */,
            true /* cacheFlush */,
            2000L, /* ttlMillis */
            arrayOf("HOSTNAME"),
            intArrayOf(1, 2, 3) /* types */
        )
        assertEquals(nsecRecord1, nsecRecord2)
        assertEquals(nsecRecord1.hashCode(), nsecRecord2.hashCode())
    }

    @Test
    fun testLabelsAreSuffix() {
        val labels1 = arrayOf("a", "b", "c")
        val labels2 = arrayOf("B", "C")
        val labels3 = arrayOf("b", "c")
        val labels4 = arrayOf("b", "d")
        assertTrue(MdnsRecord.labelsAreSuffix(labels2, labels1))
        assertTrue(MdnsRecord.labelsAreSuffix(labels3, labels1))
        assertFalse(MdnsRecord.labelsAreSuffix(labels4, labels1))
    }
}
