/**
 * Copyright (c) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.resolv.aidl;

import android.net.resolv.aidl.DnsHealthEventParcel;
import android.net.resolv.aidl.Nat64PrefixEventParcel;
import android.net.resolv.aidl.PrivateDnsValidationEventParcel;

/**
 * Unsolicited dns resolver events listener.
 * This one-way interface groups asynchronous notifications sent by dns resolver to any process that
 * registered itself via IDnsResolver.registerUnsolicitedEventListener.
 *
 * {@hide}
 */
oneway interface IDnsResolverUnsolicitedEventListener {

    /*** Types for {@code healthResult} of {@code DnsHealthEventParcel}. */
    const int DNS_HEALTH_RESULT_OK = 0;
    const int DNS_HEALTH_RESULT_TIMEOUT = 255;

    /**
     * Represents a DNS health result of queries.
     *
     * Sent by the resolver when it has events to report about its observed network health.
     * Refer to DnsHealthEventParcel for more details.
     *
     * @param dnsHealthEvent the DNS health result.
     */
    void onDnsHealthEvent(in DnsHealthEventParcel dnsHealthEvent);

    /*** Types for {@code prefixOperation} of {@code Nat64PrefixEventParcel}. */
    const int PREFIX_OPERATION_ADDED = 1;
    const int PREFIX_OPERATION_REMOVED = 2;

    /**
     * Represents a NAT64 prefix operation.
     *
     * @param nat64PrefixEvent the NAT64 prefix status.
     */
    void onNat64PrefixEvent(in Nat64PrefixEventParcel nat64PrefixEvent);

    /*** Types for {@code validation} of {@code PrivateDnsValidationEventParcel}. */
    const int VALIDATION_RESULT_SUCCESS = 1;
    const int VALIDATION_RESULT_FAILURE = 2;

    /*** Private DNS protocols for {@code protocol} of {@code PrivateDnsValidationEventParcel}. */
    const int PROTOCOL_DOT = 1;
    const int PROTOCOL_DOH = 2;

    /**
     * Represents a private DNS validation result.
     *
     * @param privateDnsValidationEvent the private DNS validation result.
     */
    void onPrivateDnsValidationEvent(in PrivateDnsValidationEventParcel privateDnsValidationEvent);
}
