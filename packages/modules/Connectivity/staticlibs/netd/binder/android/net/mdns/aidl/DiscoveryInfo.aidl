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
 * Discovery service information.
 * This information combine all arguments that used by both request and callback.
 * Arguments are used by request:
 * - id
 * - registrationType
 * - interfaceIdx
 *
 * Arguments are used by callback:
 * - id
 * - serviceName
 * - registrationType
 * - domainName
 * - interfaceIdx
 * - netId
 * - result
 *
 * {@hide}
 */
@JavaOnlyImmutable
@JavaDerive(equals=true, toString=true)
parcelable DiscoveryInfo {
    /**
     * The operation ID.
     * Must be unique among all operations (registration/discovery/resolution/getting address) and
     * can't be reused.
     * To stop a operation, it needs to use corresponding operation id.
     */
    int id;

    /**
     * The discovery result.
     */
    int result;

    /**
     * The discovered service name.
     */
    @utf8InCpp String serviceName;

    /**
     * The service type being discovered for followed by the protocol, separated by a dot
     * (e.g. "_ftp._tcp"). The transport protocol must be "_tcp" or "_udp".
     */
    @utf8InCpp String registrationType;

    /**
     * The domain of the discovered service instance.
     */
    @utf8InCpp String domainName;

    /**
     * The interface index on which to discover services. 0 indicates "all interfaces".
     */
    int interfaceIdx;

    /**
     * The net id on which the service is advertised.
     */
    int netId;
}
