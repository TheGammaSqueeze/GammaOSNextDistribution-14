/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.net;

@JavaDerive(equals=true, toString=true)
parcelable PrivateDnsConfigParcel {
    /**
     * The hostname of private DNS provider.
     */
    String hostname;

    /**
     * The DoT server IP addresses of `hostname`. They are not sorted.
     */
    String[] ips;

    /**
     * The private DNS mode associated with this PrivateDnsConfigParcel.
     * If it's set, the value must be one of the following constants defined in
     * ConnectivitySettingsManager.
     *   - PRIVATE_DNS_MODE_OFF (1)
     *   - PRIVATE_DNS_MODE_OPPORTUNISTIC (2)
     *   - PRIVATE_DNS_MODE_PROVIDER_HOSTNAME (3)
     *
     * For compatibility with old PrivateDnsConfigParcel, set the default value to -1 to indicate
     * that the sender is using an old version of PrivateDnsConfigParcel and that the receiver
     * cannot determine the private DNS mode by reading this field.
     */
    int privateDnsMode = -1;

    /**
     * The following fields with the prefix "doh" store the DoH3 information discovered from
     * DDR. The similar fields are defined in DnsResolver as well. Although duplicating code
     * is not a good idea, it avoids the complexity and confusion of having a parcelable
     * containing a nested parcelable where the client and server could have a different version
     * of the nested parcelable.
     */

    /**
     * The DoH server hostname derived from TargetName field of a DNS SVCB response.
     */
    String dohName = "";

    /**
     * The DoH server IP addresses of `dohName`. They are not sorted.
     */
    String[] dohIps = {};

    /**
     * A part of the URI template used to construct the URL for DNS resolution.
     * It's derived only from DNS SVCB SvcParamKey "dohpath".
     * The URI template for DNS resolution is as follows:
     *     https://<dohName>/<dohPath>
     */
    String dohPath = "";

    /**
     * The port used to reach the DoH servers.
     */
    int dohPort = -1;
}
