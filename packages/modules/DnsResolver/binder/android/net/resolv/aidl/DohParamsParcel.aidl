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

package android.net.resolv.aidl;

/**
 * DNS-over-HTTPS configuration parameters. Represents a single DoH server.
 *
 * Note that although this parcelable is for DNS-over-HTTPS configuration parameters, there is
 * no field in this parcelable to specify an exact HTTPS protocol (h2 or h3) because DnsResolver
 * only supports DNS-over-HTTPS/3. The configuration parameters are for h3.
 *
 * {@hide}
 */
@JavaDerive(equals=true, toString=true) @JavaOnlyImmutable
parcelable DohParamsParcel {
    /**
     * The server hostname.
     */
     String name = "";

    /**
     * The server IP addresses. They are not sorted.
     */
     String[] ips = {};

    /**
     * A part of the URI template used to construct the URL for DNS resolution.
     * It's derived only from DNS SVCB SvcParamKey "dohpath".
     */
     String dohpath = "";

    /**
     * The port used to reach the servers.
     */
     int port = -1;
}
