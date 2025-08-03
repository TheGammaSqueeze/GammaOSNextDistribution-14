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

/**
 * The DNS health result of queries.
 *
 * Only sent for API calls that actually generated network traffic:
 *  - No cache hits
 *  - No power management errors (rcode == INTERNAL_ERROR && errno == EPERM)
 *  - Only lookup result is OK or TIMEOUT.
 *
 * This must not be used for use-cases other than evaluating the health of DNS queries. The behavior
 * of this interface will change across regular module updates, and in particular it will be updated
 * to stop sending one event by DNS API call, and instead report network failures in realtime.
 *
 * Non-public API, only callable from Connectivity or NetworkStack Mainline modules.
 * {@hide}
 */
@JavaDerive(toString=true)
parcelable DnsHealthEventParcel {

    /*** The ID of the network that lookup was performed on. */
    int netId;

    /**
     * The health result of queries.
     * Currently, this value is for data stall detection and it only checks whether result is
     * timeout or not. So if the result is neither OK nor TIMEOUT, will not send this event.
     */
    int healthResult;

    /*** The round trip time for each dns query that received a reply */
    int[] successRttMicros;
}
