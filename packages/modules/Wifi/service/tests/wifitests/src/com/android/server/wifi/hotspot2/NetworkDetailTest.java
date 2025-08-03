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

package com.android.server.wifi.hotspot2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.wifi.MloLink;
import android.net.wifi.ScanResult.InformationElement;
import android.text.TextUtils;

import com.android.server.wifi.WifiBaseTest;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

/**
 * Unit tests for {@link NetworkDetail}.
 */
public class NetworkDetailTest extends WifiBaseTest {
    private static final String TEST_AP_MLD_MAC_ADDRESS = "02:34:56:78:9a:bc";
    private static final String TEST_BSSID = "02:03:04:05:06:07";

    @Before
    public void setUp() throws Exception {
    }

    /**
     * Verify that When creating a NetworkDetail from a ScanResult without MultiLink IE,
     * the MLO Information will not be propagated into NetworkDetail.
     */
    @Test
    public void verifyFromScanResultNoMultiLinkIe() throws Exception {
        InformationElement[] ies = new InformationElement[] {};
        NetworkDetail networkDetail = new NetworkDetail(TEST_BSSID, ies,
                Collections.emptyList(), 5745);

        assertTrue(networkDetail.toKeyString() + " not expected!",
                TextUtils.equals("'null':02:03:04:05:06:07", networkDetail.toKeyString()));
        assertNull(networkDetail.getMldMacAddress());
        assertEquals(MloLink.INVALID_MLO_LINK_ID, networkDetail.getMloLinkId());
        assertTrue(networkDetail.getAffiliatedMloLinks().isEmpty());
    }

    /**
     * Verify that When creating a NetworkDetail from a ScanResult with MultiLink IE with no links,
     * and an RNR with MLO links, the MLO Information will be properly propagated into
     * the NetworkDetail.
     */
    @Test
    public void verifyFromScanResultRnrMultiIeNoLinksIe() throws Exception {
        InformationElement[] ies = new InformationElement[2];

        // RNR IE
        ies[0] = new InformationElement();
        ies[0].id = InformationElement.EID_RNR;
        ies[0].bytes = new byte[] {
                (byte) 0x00,  (byte) 0x04, (byte) 81,   (byte) 11,   // First TBTT Info
                (byte) 0x00,  (byte) 0x00, (byte) 0x01, (byte) 0x00, //  First Set
                (byte) 0x10,  (byte) 0x04, (byte) 120,  (byte) 149,  // Second TBTT Info
                (byte) 0x00,  (byte) 0x00, (byte) 0x02, (byte) 0x00, //  First Set
                (byte) 0x00,  (byte) 0x22, (byte) 0x01, (byte) 0x00  //  Second Set
        };

        // Multi-Link IE
        ies[1] = new InformationElement();
        ies[1].id = InformationElement.EID_EXTENSION_PRESENT;
        ies[1].idExt = InformationElement.EID_EXT_MULTI_LINK;
        ies[1].bytes = new byte[] {
                (byte) 0x10,  (byte) 0x00,                              // Control
                (byte) 0x08,  (byte) 0x02, (byte) 0x34, (byte) 0x56,    // Common Info
                (byte) 0x78,  (byte) 0x9A, (byte) 0xBC, (byte) 0x01
        };

        NetworkDetail networkDetail = new NetworkDetail(TEST_BSSID, ies,
                Collections.emptyList(), 5745);

        assertNotNull(networkDetail.getMldMacAddress());
        assertEquals(TEST_AP_MLD_MAC_ADDRESS, networkDetail.getMldMacAddress().toString());
        assertEquals(1, networkDetail.getMloLinkId());
        assertEquals(3, networkDetail.getAffiliatedMloLinks().size());
    }

    /**
     * Verify that When creating a NetworkDetail from a ScanResult with MultiLink IE with links,
     * and an RNR with MLO links, the MLO Information will be propagated properly into
     * the NetworkDetail with links info taken from RNR.
     */
    @Test
    public void verifyFromScanResultRnrMultiIeWithLinks() throws Exception {
        InformationElement[] ies = new InformationElement[2];

        // RNR IE
        ies[0] = new InformationElement();
        ies[0].id = InformationElement.EID_RNR;
        ies[0].bytes = new byte[] {
                (byte) 0x00,  (byte) 0x04, (byte) 81,   (byte) 11,   // First TBTT Info
                (byte) 0x00,  (byte) 0x00, (byte) 0x01, (byte) 0x00, //  First Set
                (byte) 0x10,  (byte) 0x04, (byte) 120,  (byte) 149,  // Second TBTT Info
                (byte) 0x00,  (byte) 0x00, (byte) 0x02, (byte) 0x00, //  First Set
                (byte) 0x00,  (byte) 0x22, (byte) 0x01, (byte) 0x00  //  Second Set
        };

        // Multi-Link IE
        ies[1] = new InformationElement();
        ies[1].id = InformationElement.EID_EXTENSION_PRESENT;
        ies[1].idExt = InformationElement.EID_EXT_MULTI_LINK;
        ies[1].bytes = new byte[] {
                (byte) 0x10,  (byte) 0x00,                              // Control
                (byte) 0x08,  (byte) 0x02, (byte) 0x34, (byte) 0x56,    // Common Info
                (byte) 0x78,  (byte) 0x9A, (byte) 0xBC, (byte) 0x01,
                (byte) 0x00,  (byte) 0x08, (byte) 0x02, (byte) 0x00,    // First Link Info
                (byte) 0x00,  (byte) 0x00, (byte) 0x00, (byte) 0x00,    //
                (byte) 0x00,  (byte) 0x08, (byte) 0x03, (byte) 0x00,    // Second Link Info
                (byte) 0x00,  (byte) 0x00, (byte) 0x00, (byte) 0x00     //
        };

        NetworkDetail networkDetail = new NetworkDetail(TEST_BSSID, ies,
                Collections.emptyList(), 5745);

        assertNotNull(networkDetail.getMldMacAddress());
        assertEquals(TEST_AP_MLD_MAC_ADDRESS, networkDetail.getMldMacAddress().toString());
        assertEquals(1, networkDetail.getMloLinkId());
        assertEquals(3, networkDetail.getAffiliatedMloLinks().size());
    }

    /**
     * Verify that When creating a NetworkDetail from a ScanResult with MultiLink IE with no links,
     * and an RNR with no MLO links, the MLO Information will be properly propagated into
     * the NetworkDetail, and no links is included as affiliated MLO Links.
     */
    @Test
    public void verifyFromScanResultRnrNoLinksMultiIeNoLinksIe() throws Exception {
        InformationElement[] ies = new InformationElement[2];

        // RNR IE
        ies[0] = new InformationElement();
        ies[0].id = InformationElement.EID_RNR;
        ies[0].bytes = new byte[] {
                (byte) 0x00,  (byte) 0x04, (byte) 81,   (byte) 11,   // First TBTT Info
                (byte) 0x00,  (byte) 0x21, (byte) 0x01, (byte) 0x00, //  First Set
                (byte) 0x10,  (byte) 0x04, (byte) 120,  (byte) 149,  // Second TBTT Info
                (byte) 0x00,  (byte) 0x22, (byte) 0x02, (byte) 0x00, //  First Set
                (byte) 0x00,  (byte) 0x23, (byte) 0x01, (byte) 0x00  //  Second Set
        };

        // Multi-Link IE
        ies[1] = new InformationElement();
        ies[1].id = InformationElement.EID_EXTENSION_PRESENT;
        ies[1].idExt = InformationElement.EID_EXT_MULTI_LINK;
        ies[1].bytes = new byte[] {
                (byte) 0x10,  (byte) 0x00,                              // Control
                (byte) 0x08,  (byte) 0x02, (byte) 0x34, (byte) 0x56,    // Common Info
                (byte) 0x78,  (byte) 0x9A, (byte) 0xBC, (byte) 0x01
        };

        NetworkDetail networkDetail = new NetworkDetail(TEST_BSSID, ies,
                Collections.emptyList(), 5745);

        assertNotNull(networkDetail.getMldMacAddress());
        assertEquals(TEST_AP_MLD_MAC_ADDRESS, networkDetail.getMldMacAddress().toString());
        assertEquals(1, networkDetail.getMloLinkId());
        assertTrue(networkDetail.getAffiliatedMloLinks().isEmpty());
    }

    @Test
    public void  verifySameTextFormat() {
        long[] testBssids = {0x11ab0d0f7890L, 0x1, 0x3300, 0x0, 0xffffffffffffL};
        String[] testBssidMacStrs = {"11:ab:0d:0f:78:90", "00:00:00:00:00:01", "00:00:00:00:33:00",
                "00:00:00:00:00:00", "ff:ff:ff:ff:ff:ff"};
        for (int i = 0; i < testBssids.length; i++) {
            String bssidStr1 = testBssidMacStrs[i];
            String bssidStr2 = Utils.macToString(testBssids[i]);
            assertTrue(bssidStr1 + " not equals " + bssidStr2,
                    TextUtils.equals(bssidStr1, bssidStr2));
        }
    }
}

