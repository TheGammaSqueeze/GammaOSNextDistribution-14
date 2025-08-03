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
 * Registration service information.
 * This information combine all arguments that used by both request and callback.
 * Arguments are used by request:
 * - id
 * - serviceName
 * - registrationType
 * - port
 * - txtRecord
 * - interfaceIdx
 *
 * Arguments are used by callback:
 * - id
 * - serviceName
 * - registrationType
 * - result
 *
 * {@hide}
 */
@JavaOnlyImmutable
@JavaDerive(equals=true, toString=true)
parcelable RegistrationInfo {
    /**
     * The operation ID.
     */
    int id;

    /**
     * The registration result.
     */
    int result;

    /**
     * The service name to be registered.
     */
    @utf8InCpp String serviceName;

    /**
     * The service type followed by the protocol, separated by a dot (e.g. "_ftp._tcp"). The service
     * type must be an underscore, followed by 1-15 characters, which may be letters, digits, or
     * hyphens. The transport protocol must be "_tcp" or "_udp". New service types should be
     * registered at <http://www.dns-sd.org/ServiceTypes.html>.
     */
    @utf8InCpp String registrationType;

    /**
     * The port on which the service accepts connections.
     */
    int port;

    /**
     * The txt record.
     */
    byte[] txtRecord;

    /**
     * The interface index on which to register the service. 0 indicates "all interfaces".
     */
    int interfaceIdx;
}
