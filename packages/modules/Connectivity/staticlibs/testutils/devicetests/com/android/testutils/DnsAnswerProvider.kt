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
import com.android.net.module.util.DnsPacket
import com.android.net.module.util.DnsPacket.ANSECTION
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

const val DEFAULT_TTL_S = 5L

/**
 * Helper class to store the mapping of DNS queries.
 *
 * DnsAnswerProvider is built atop a ConcurrentHashMap and as such it provides the same
 * guarantees as ConcurrentHashMap between writing and reading elements. Specifically :
 * - Setting an answer happens-before reading the same answer.
 * - Callers can read and write concurrently from DnsAnswerProvider and expect no
 *   ConcurrentModificationException.
 * Freshness of the answers depends on ordering of the threads ; if callers need a
 * freshness guarantee, they need to provide the happens-before relationship from a
 * write that they want to observe to the read that they need to be observed.
 */
class DnsAnswerProvider {
    private val mDnsKeyToRecords = ConcurrentHashMap<String, List<DnsPacket.DnsRecord>>()

    /**
     * Get answer for the specified hostname.
     *
     * @param query the target hostname.
     * @param type type of record, could be A or AAAA.
     *
     * @return list of [DnsPacket.DnsRecord] associated to the query. Empty if no record matches.
     */
    fun getAnswer(query: String, type: Int) = mDnsKeyToRecords[query]
            .orEmpty().filter { it.nsType == type }

    /** Set answer for the specified {@code query}.
     *
     * @param query the target hostname
     * @param addresses [List<InetAddress>] which could be used to generate multiple A or AAAA
     *                  RRs with the corresponding addresses.
     */
    fun setAnswer(query: String, hosts: List<InetAddress>) = mDnsKeyToRecords.put(query, hosts.map {
            DnsPacket.DnsRecord.makeAOrAAAARecord(ANSECTION, query, CLASS_IN, DEFAULT_TTL_S, it)
        })

    fun clearAnswer(query: String) = mDnsKeyToRecords.remove(query)
    fun clearAll() = mDnsKeyToRecords.clear()
}