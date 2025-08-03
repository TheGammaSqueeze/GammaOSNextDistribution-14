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

package android.net.mdns.aidl;

/**
 * Get service address information.
 * This information combine all arguments that used by both request and callback.
 * Arguments are used by request:
 * - id
 * - hostname
 * - interfaceIdx
 *
 * Arguments are used by callback:
 * - id
 * - hostname
 * - interfaceIdx
 * - netId
 * - address
 * - result
 *
 * {@hide}
 */
@JavaOnlyImmutable
@JavaDerive(equals=true, toString=true)
parcelable GetAddressInfo {
    /**
     * The operation ID.
     */
    int id;

    /**
     * The getting address result.
     */
    int result;

    /**
     * The fully qualified domain name of the host to be queried for.
     */
    @utf8InCpp String hostname;

    /**
     * The service address info, it's IPv4 or IPv6 addres.
     */
    @utf8InCpp String address;

    /**
     * The interface index on which to issue the query. 0 indicates "all interfaces".
     */
    int interfaceIdx;

    /**
     * The net id to which the answers pertain.
     */
    int netId;
}
