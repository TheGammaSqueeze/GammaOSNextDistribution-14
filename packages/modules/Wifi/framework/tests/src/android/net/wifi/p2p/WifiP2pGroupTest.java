/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.net.wifi.p2p;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.InetAddresses;
import android.net.MacAddress;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.OuiKeyedDataUtil;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Test;

import java.util.Collection;
import java.util.List;

/**
 * Unit test harness for {@link android.net.wifi.p2p.WifiP2pGroup}
 */
@SmallTest
public class WifiP2pGroupTest {

    private static final String INTERFACE = "p2p-p2p0-3";
    private static final int NETWORK_ID = 9;
    private static final String NETWORK_NAME = "DIRECT-xy-Hello";
    private static final String PASSPHRASE = "HelloWorld";
    private static final WifiP2pDevice GROUP_OWNER = new WifiP2pDevice("de:ad:be:ef:00:01");
    private static final int FREQUENCY = 5300;
    private static final String CLIENT_1_DEV_ADDRESS = "aa:bb:cc:dd:ee:01";
    private static final String CLIENT_2_DEV_ADDRESS = "aa:bb:cc:dd:ee:02";
    private static final WifiP2pDevice CLIENT_1 = new WifiP2pDevice(CLIENT_1_DEV_ADDRESS);
    private static final WifiP2pDevice CLIENT_2 = new WifiP2pDevice(CLIENT_2_DEV_ADDRESS);
    private static final MacAddress CLIENT_1_INTERFACE_MAC_ADDRESS =
            MacAddress.fromString("aa:bb:cc:dd:ee:10");
    private static final String CLIENT_1_IP_ADDRESS = "192.168.49.10";
    private static final List<OuiKeyedData> VENDOR_DATA =
            OuiKeyedDataUtil.createTestOuiKeyedDataList(5);

    /**
     * Verify setter/getter functions.
     */
    @Test
    public void testSetterGetter() throws Exception {
        WifiP2pGroup group = new WifiP2pGroup();

        group.setInterface(INTERFACE);
        group.setNetworkId(NETWORK_ID);
        group.setNetworkName(NETWORK_NAME);
        group.setPassphrase(PASSPHRASE);
        group.setIsGroupOwner(false);
        group.setOwner(GROUP_OWNER);
        group.setFrequency(FREQUENCY);
        group.addClient(CLIENT_1.deviceAddress);
        group.setClientInterfaceMacAddress(CLIENT_1.deviceAddress,
                CLIENT_1_INTERFACE_MAC_ADDRESS);
        if (SdkLevel.isAtLeastV()) {
            group.setClientIpAddress(CLIENT_1_INTERFACE_MAC_ADDRESS,
                    InetAddresses.parseNumericAddress(CLIENT_1_IP_ADDRESS));
        }
        group.addClient(CLIENT_2);
        if (SdkLevel.isAtLeastV()) {
            group.setVendorData(VENDOR_DATA);
        }

        assertEquals(INTERFACE, group.getInterface());
        assertEquals(NETWORK_ID, group.getNetworkId());
        assertEquals(NETWORK_NAME, group.getNetworkName());
        assertEquals(PASSPHRASE, group.getPassphrase());
        assertFalse(group.isGroupOwner());
        assertEquals(GROUP_OWNER, group.getOwner());
        assertEquals(FREQUENCY, group.getFrequency());
        if (SdkLevel.isAtLeastV()) {
            assertTrue(VENDOR_DATA.equals(group.getVendorData()));
        }

        assertFalse(group.isClientListEmpty());
        assertTrue(group.contains(CLIENT_1));

        assertEquals(2, group.getClientList().size());
        Collection<WifiP2pDevice> clientsList = group.getClientList();
        for (WifiP2pDevice client : clientsList) {
            if (client.deviceAddress.equals(CLIENT_1_DEV_ADDRESS)) {
                assertEquals(CLIENT_1_INTERFACE_MAC_ADDRESS, client.getInterfaceMacAddress());
                if (SdkLevel.isAtLeastV()) {
                    assertEquals(CLIENT_1_IP_ADDRESS, client.getIpAddress().getHostAddress());
                }
            } else if (client.deviceAddress.equals(CLIENT_2_DEV_ADDRESS)) {
                assertNull(client.getInterfaceMacAddress());
                if (SdkLevel.isAtLeastV()) {
                    assertNull(client.getIpAddress());
                }
            }
        }

        group.removeClient(CLIENT_1);
        group.removeClient(CLIENT_2.deviceAddress);
        assertFalse(group.contains(CLIENT_1));
        assertTrue(group.isClientListEmpty());

        Parcel parcelW = Parcel.obtain();
        group.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        WifiP2pGroup fromParcel = WifiP2pGroup.CREATOR.createFromParcel(parcelR);

        if (SdkLevel.isAtLeastV()) {
            assertTrue(VENDOR_DATA.equals(fromParcel.getVendorData()));
        }
        assertEquals(group.toString(), fromParcel.toString());

    }
}
