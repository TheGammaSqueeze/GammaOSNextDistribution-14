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

package device.google.atv.mdns_offload;

/**
 * Service interface to communicate with network interfaces that provide mDNS Offload
 * capabilities.
 * Requirements:
 * - Must respond the raw packet (we guarantee a well formed packet) as a direct answer to queries
 * matching. No post processing is expected from the offload responder.
 * - Optional: Must keep track of the number of hits and misses.
 */
@VintfStability
interface IMdnsOffload {
    parcelable MdnsProtocolData {
        /**
         * Defines a mDNS response to answer during setOffloadState(true) mode and the
         * criteria to match against the incoming queries.
         */
        parcelable MatchCriteria {
            /* QTYPE RRTYPE */
            int type;
            /* RRNAME offset in the rawOffloadPacket */
            int nameOffset;
        }

        /**
         * Ready to send DNS packet containing mDNS responses in the
         * AnswerSection. It follows the packet specification as described in
         * the RFC6762 (https://www.rfc-editor.org/rfc/rfc6762).
         * - All the answers are stored in the answer section, nothing in the
         *   additional section.
         * - RRNAME and RDATA part might use name compression, so the
         *   implementation must be able to read compressed names.
         */
        byte[] rawOffloadPacket;

        /**
         * The list of criteria to match against.
         * Each criteria contains a QNAME offset to read from the
         * rawOffloadPacket. The offset is a starting point to read the name.
         * The name then follows the name definition standard as defined in the
         * RFC1035-4.1.4 (https://www.rfc-editor.org/rfc/rfc1035#section-4.1.4)
         */
        List<MatchCriteria> matchCriteriaList;
    }

    /**
     * Set the current offload state on the hardware.
     * If enabled=false everything should be forwarded to the CPU regardless of the
     * offloaded responses or the set passthrough behavior. Waking up the CPU if
     * required.
     * If enabled=true is passed, the offload behavior must be applied (respecting
     * all the contracts stated in the following methods).
     *
     * Returns the offload state after the change has been applied.
     *
     * The default state at boot is expected to be enabled=false.
     */
    boolean setOffloadState(boolean enabled);

    /**
     * Reset the state of the hardware or the offload service to a clear state.
     * Only used when the OffloadManager starts to ensure a clear state.
     *
     * The default state is:
     * The passthrough state is DROP_ALL for every available network interface.
     * The passthrough lists are empty for every available network interface.
     * Any internal state holding previously offloaded record sets must be cleared.
     * Internal counters of hits and misses must be reset to 0.
     */
    void resetAll();

    /**
     * Add a fully formed mDNS response to the offload on the specified network interface.
     * See https://developer.android.com/reference/java/net/NetworkInterface#getNetworkInterfaces()
     *
     * Returns a recordKey represented by an int.
     * If the key returned is >= 0; the insertion was successful.
     * If the key returned is < 0; the insertion failed.
     *
     * When the network interface receives an mDNS query packet it must iterate over
     * all the queries. For each query where the qName and qType matches the rRName and
     * rRtype of any of the MatchCriteria, the sender MUST return the rawOffloadPacket
     * response AS IS.
     * If the qType is ANY, then the match must be done only against the rRName in the
     * match criteria. If many queries match the same MdnsProtocolData, a special
     * attention must be taken to only send the response once, minimizing noise on the
     * network.
     *
     * Additional queries may use name compression in the QNAME field.
     * https://www.rfc-editor.org/rfc/rfc1035#section-4.1.4
     * In order to be compliant with the standard, the offload implementation MUST
     * be able to decode compressed QNAMEs.
     *
     * Apart from the sign of the key, no other assumption should be taken from the
     * user side. The implementation is free to reuse an old key for a new insertion
     * as long as it doesn't conflict with any active one.
     *
     * IT IS NOT part of the contract of this method to guarantee uniqueness
     * with previously added items. THE ONLY requirement is that this method succeeds
     * if it can guarantee the offload of the provided responses on the given
     * network interface, and fails otherwise.
     */
    int addProtocolResponses(String networkInterface, // eth0, wlan0,...
            in MdnsProtocolData offloadData);

    /**
     * Removes a record set that matches the given record key. Does nothing if
     * this record key belongs to nothing.
     */
    void removeProtocolResponses(int recordKey);

    /**
     * Returns the >= 0 number of hits for the given recordKey.
     * Resets the internal counter to 0 for the given record key.
     * If the implementation is not able to track hits, return -1.
     */
    int getAndResetHitCounter(int recordKey);

    /**
     * Returns the >= 0 number of non fulfilled queries.
     * Resets the internal counter to 0 at every call.
     * If the implementation is not able to track misses, return -1.
     */
    int getAndResetMissCounter();

    /**
     * Add the supplied QNAME to the allowlist that can forward queries to the
     * main system when the offload cannot provide responses. Return true if
     * the addition is successful,false otherwise. The allowlist is defined per
     * network interface (eth0, wlan0,...)
     * This is only used to filter queries. The matching is done only against QNAME.
     * Be careful with this list, as it will allow additional queries to wake up
     * the system. If the same QNAME is used in the offloaded records and in the
     * passthrough list, the offloaded response takes precedence.
     */
    boolean addToPassthroughList(String networkInterface, String qname);

    /**
     * Removes the supplied QNAME from the passthrough allowlist of a given
     * networkInterface.
     */
    void removeFromPassthroughList(String networkInterface, String qname);

    enum PassthroughBehavior {
        // All the queries are forwarded to the system without any modification.
        FORWARD_ALL,
        // All the queries are dropped.
        DROP_ALL,
        // Only the queries present in the passthrough list are forwarded
        // to the system without any modification.
        PASSTHROUGH_LIST,
    }

    /**
     * Sets the passtrough behavior for the offload on a specific networkInterface.
     */
    void setPassthroughBehavior(String networkInterface, PassthroughBehavior behavior);
}
