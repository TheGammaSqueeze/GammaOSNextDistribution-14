/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan.epdg;

import android.net.LinkAddress;
import android.telephony.data.ApnSetting;
import android.telephony.data.NetworkSliceInfo;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class TunnelLinkPropertiesTest {

    private static final String IP_ADDRESS = "192.0.2.1";
    private static final String IPV6_ADDRESS = "2001:0db8:0000:0000:0000:ff00:0042:8329";
    private static final String DNS_ADDRESS = "8.8.8.8";
    private static final String PSCF_ADDRESS = "10.159.204.230";
    private static final String INTERFACE_NAME = "ipsec6";
    private static final NetworkSliceInfo SLICE_INFO =
            NetworkSliceSelectionAssistanceInformation.getSliceInfo(new byte[] {1});

    public static TunnelLinkProperties createTestTunnelLinkProperties(
            @ApnSetting.ProtocolType int protocolType) throws Exception {
        List<LinkAddress> mInternalAddressList = new ArrayList<>();
        List<InetAddress> mDNSAddressList = new ArrayList<>();
        List<InetAddress> mPCSFAddressList = new ArrayList<>();

        if (protocolType == ApnSetting.PROTOCOL_IP) {
            mInternalAddressList.add(new LinkAddress(InetAddress.getByName(IP_ADDRESS), 3));
        } else if (protocolType == ApnSetting.PROTOCOL_IPV6) {
            mInternalAddressList.add(new LinkAddress(InetAddress.getByName(IPV6_ADDRESS), 3));
        } else if (protocolType == ApnSetting.PROTOCOL_IPV4V6) {
            mInternalAddressList.add(new LinkAddress(InetAddress.getByName(IP_ADDRESS), 3));
            mInternalAddressList.add(new LinkAddress(InetAddress.getByName(IPV6_ADDRESS), 3));
        }
        mDNSAddressList.add(InetAddress.getByName(DNS_ADDRESS));
        mPCSFAddressList.add(InetAddress.getByName(PSCF_ADDRESS));

        return TunnelLinkProperties.builder()
                .setInternalAddresses(mInternalAddressList)
                .setDnsAddresses(mDNSAddressList)
                .setPcscfAddresses(mPCSFAddressList)
                .setIfaceName(INTERFACE_NAME)
                .setSliceInfo(SLICE_INFO)
                .build();
    }

    @Test
    public void testGetProtocolType_emptyInternalAddresses_returnsUnknown() throws Exception {
        List<InetAddress> mDNSAddressList = new ArrayList<>();
        List<InetAddress> mPCSFAddressList = new ArrayList<>();
        mDNSAddressList.add(InetAddress.getByName(DNS_ADDRESS));
        mPCSFAddressList.add(InetAddress.getByName(PSCF_ADDRESS));
        TunnelLinkProperties properties =
                TunnelLinkProperties.builder()
                        .setInternalAddresses(new ArrayList<>())
                        .setDnsAddresses(mDNSAddressList)
                        .setPcscfAddresses(mPCSFAddressList)
                        .setIfaceName(INTERFACE_NAME)
                        .setSliceInfo(SLICE_INFO)
                        .build();
        assertEquals(ApnSetting.PROTOCOL_UNKNOWN, properties.getProtocolType());
    }

    @Test
    public void testGetProtocolType_onlyIpv4InternalAddresses_returnsIp() throws Exception {
        TunnelLinkProperties properties = createTestTunnelLinkProperties(ApnSetting.PROTOCOL_IP);
        assertEquals(ApnSetting.PROTOCOL_IP, properties.getProtocolType());
    }

    @Test
    public void testGetProtocolType_onlyIpv6InternalAddresses_returnsIpv6() throws Exception {
        TunnelLinkProperties properties = createTestTunnelLinkProperties(ApnSetting.PROTOCOL_IPV6);
        assertEquals(ApnSetting.PROTOCOL_IPV6, properties.getProtocolType());
    }

    @Test
    public void testGetProtocolType_bothIpv4AndIpv6InternalAddresses_returnsIpv4v6()
            throws Exception {
        TunnelLinkProperties properties =
                createTestTunnelLinkProperties(ApnSetting.PROTOCOL_IPV4V6);
        assertEquals(ApnSetting.PROTOCOL_IPV4V6, properties.getProtocolType());
    }
}
