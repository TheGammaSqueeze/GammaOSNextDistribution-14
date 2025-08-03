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
 * Resolution service information.
 * This information combine all arguments that used by both request and callback.
 * Arguments are used by request:
 * - id
 * - serviceName
 * - registrationType
 * - domain
 * - interfaceIdx
 *
 * Arguments are used by callback:
 * - id
 * - port
 * - serviceFullName
 * - hostname
 * - txtRecord
 * - interfaceIdx
 * - result
 *
 * {@hide}
 */
@JavaOnlyImmutable
@JavaDerive(equals=true, toString=true)
parcelable ResolutionInfo {
    /**
     * The operation ID.
     */
    int id;

    /**
     * The resolution result.
     */
    int result;

    /**
     * The service name to be resolved.
     */
    @utf8InCpp String serviceName;

    /**
     * The service type to be resolved.
     */
    @utf8InCpp String registrationType;

    /**
     * The service domain to be resolved.
     */
    @utf8InCpp String domain;

    /**
     * The resolved full service domain name, in the form <servicename>.<protocol>.<domain>.
     */
    @utf8InCpp String serviceFullName;

    /**
     * The target hostname of the machine providing the service.
     */
    @utf8InCpp String hostname;

    /**
     * The port on which connections are accepted for this service.
     */
    int port;

    /**
     * The service's txt record.
     */
    byte[] txtRecord;

    /**
     * The interface index on which to resolve the service. 0 indicates "all interfaces".
     */
    int interfaceIdx;
}
