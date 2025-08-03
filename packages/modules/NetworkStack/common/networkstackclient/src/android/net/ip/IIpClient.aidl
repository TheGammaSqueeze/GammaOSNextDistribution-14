/**
 * Copyright (c) 2019, The Android Open Source Project
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
 * See the License for the specific language governing perNmissions and
 * limitations under the License.
 */
package android.net.ip;

import android.net.Layer2InformationParcelable;
import android.net.ProxyInfo;
import android.net.ProvisioningConfigurationParcelable;
import android.net.NattKeepalivePacketDataParcelable;
import android.net.TcpKeepalivePacketDataParcelable;
import android.net.apf.ApfCapabilities;

/** @hide */
oneway interface IIpClient {
    /**
     * Disable IPv4 provisioning.
     */
    const int PROV_IPV4_DISABLED = 0x00;

    /**
     * Enable IPv4 provisioning using static IP addresses.
     */
    const int PROV_IPV4_STATIC = 0x01;

    /**
     * Enable IPv4 provisioning using DHCP.
     */
    const int PROV_IPV4_DHCP = 0x02;

    /**
     * Disable IPv6 provisioning.
     */
    const int PROV_IPV6_DISABLED = 0x00;

    /**
     * Enable IPv6 provisioning via SLAAC.
     */
    const int PROV_IPV6_SLAAC = 0x01;

    /**
     * Enable IPv6 Link-local only.
     */
    const int PROV_IPV6_LINKLOCAL = 0x02;

    /**
     * Unset hostname setting.
     */
    const int HOSTNAME_SETTING_UNSET = 0x00;

    /**
     * Send hostname to IP provisioning server.
     */
    const int HOSTNAME_SETTING_SEND = 0x01;

    /**
     * Do not send hostname to IP provisioning server.
     */
    const int HOSTNAME_SETTING_DO_NOT_SEND = 0x02;

    void completedPreDhcpAction();
    void confirmConfiguration();
    void readPacketFilterComplete(in byte[] data);
    void shutdown();
    void startProvisioning(in ProvisioningConfigurationParcelable req);
    void stop();
    void setTcpBufferSizes(in String tcpBufferSizes);
    void setHttpProxy(in ProxyInfo proxyInfo);
    void setMulticastFilter(boolean enabled);
    void addKeepalivePacketFilter(int slot, in TcpKeepalivePacketDataParcelable pkt);
    void removeKeepalivePacketFilter(int slot);
    /* Group hint is the old name for cluster */
    void setL2KeyAndGroupHint(in String l2Key, in String cluster);
    void addNattKeepalivePacketFilter(int slot, in NattKeepalivePacketDataParcelable pkt);
    void notifyPreconnectionComplete(boolean success);
    void updateLayer2Information(in Layer2InformationParcelable info);
    /**
     * Update the APF capabilities.
     *
     * This method will update the APF capabilities used in IpClient and decide if a new APF
     * program should be installed to filter the incoming packets based on that. So far this
     * method only allows for the APF capabilities to go from null to non-null, and no other
     * changes are allowed. One use case is when WiFi interface switches from secondary to
     * primary in STA+STA mode.
     *
     * @param apfCapabilities the APF capabilities to update, should be non-null.
     */
    void updateApfCapabilities(in ApfCapabilities apfCapabilities);
}
