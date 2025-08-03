/*
 * Copyright (C) 2017 The Android Open Source Project
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.net.MacAddress;
import android.net.wifi.OuiKeyedDataUtil;
import android.net.wifi.ScanResult;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit test harness for {@link android.net.wifi.p2p.WifiP2pDevice}
 */
@SmallTest
public class WifiP2pDeviceTest {
    private static final MacAddress INTERFACE_MAC_ADDRESS =
            MacAddress.fromString("aa:bb:cc:dd:ee:10");

    /**
     * Compare two p2p devices.
     *
     * @param devA is the first device to be compared
     * @param devB is the second device to be compared
     */
    private void compareWifiP2pDevices(WifiP2pDevice devA, WifiP2pDevice devB) {
        assertEquals(devA.deviceName, devB.deviceName);
        assertEquals(devA.deviceAddress, devB.deviceAddress);
        assertEquals(devA.primaryDeviceType, devB.primaryDeviceType);
        assertEquals(devA.secondaryDeviceType, devB.secondaryDeviceType);
        assertEquals(devA.wpsConfigMethodsSupported, devB.wpsConfigMethodsSupported);
        assertEquals(devA.deviceCapability, devB.deviceCapability);
        assertEquals(devA.groupCapability, devB.groupCapability);
        assertEquals(devA.status, devB.status);
        if (devA.wfdInfo != null) {
            assertEquals(devA.wfdInfo.isEnabled(), devB.wfdInfo.isEnabled());
            assertEquals(devA.wfdInfo.getDeviceInfoHex(), devB.wfdInfo.getDeviceInfoHex());
            assertEquals(devA.wfdInfo.getControlPort(), devB.wfdInfo.getControlPort());
            assertEquals(devA.wfdInfo.getMaxThroughput(), devB.wfdInfo.getMaxThroughput());
        } else {
            assertEquals(devA.wfdInfo, devB.wfdInfo);
        }
    }

    /**
     * Check equals and hashCode consistency
     */
    @Test
    public void testEqualsWithHashCode() throws Exception {
        WifiP2pDevice dev_a = new WifiP2pDevice();
        dev_a.deviceAddress = new String("02:90:4c:a0:92:54");
        WifiP2pDevice dev_b = new WifiP2pDevice();
        dev_b.deviceAddress = new String("02:90:4c:a0:92:54");

        assertTrue(dev_a.equals(dev_b));
        assertEquals(dev_a.hashCode(), dev_b.hashCode());
    }

    /**
     * Check the copy constructor with default values.
     */
    @Test
    public void testCopyConstructorWithDefaultValues() throws Exception {
        WifiP2pDevice device = new WifiP2pDevice();
        WifiP2pDevice copy = new WifiP2pDevice(device);
        compareWifiP2pDevices(device, copy);
    }

    /**
     * Check the copy constructor with updated values.
     */
    @Test
    public void testCopyConstructorWithUpdatedValues() throws Exception {
        WifiP2pDevice device = new WifiP2pDevice();
        device.deviceName = "deviceName";
        device.deviceAddress = "11:22:33:44:55:66";
        device.primaryDeviceType = "primaryDeviceType";
        device.secondaryDeviceType = "secondaryDeviceType";
        device.wpsConfigMethodsSupported = 0x0008;
        device.deviceCapability = 1;
        device.groupCapability = 1;
        device.status = WifiP2pDevice.CONNECTED;
        device.wfdInfo = new WifiP2pWfdInfo();
        WifiP2pDevice copy = new WifiP2pDevice(device);
        compareWifiP2pDevices(device, copy);
    }

    /**
     * Check the copy constructor when the wfdInfo of the source object is null.
     */
    @Test
    public void testCopyConstructorWithNullWfdInfo() throws Exception {
        WifiP2pDevice device = new WifiP2pDevice();
        device.deviceName = "deviceName";
        device.deviceAddress = "11:22:33:44:55:66";
        device.primaryDeviceType = "primaryDeviceType";
        device.secondaryDeviceType = "secondaryDeviceType";
        device.wpsConfigMethodsSupported = 0x0008;
        device.deviceCapability = 1;
        device.groupCapability = 1;
        device.status = WifiP2pDevice.CONNECTED;
        device.wfdInfo = null;
        WifiP2pDevice copy = new WifiP2pDevice(device);
        compareWifiP2pDevices(device, copy);
    }

    /**
     * Test that application information setter/getter.
     */
    @Test
    public void testVendorElementsSetterGetter() {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiP2pDevice device = new WifiP2pDevice();
        List<ScanResult.InformationElement> ies =  new ArrayList<>(Arrays.asList(
                new ScanResult.InformationElement(ScanResult.InformationElement.EID_VSA,
                        0, new byte[]{1, 2, 3, 4})));
        device.setVendorElements(ies);
        assertEquals(ies, device.getVendorElements());
    }

    /** Tests that this class can be properly parceled and unparceled. */
    @Test
    public void testParcelReadWrite() {
        WifiP2pDevice device = new WifiP2pDevice();
        device.deviceName = "deviceName";
        device.deviceAddress = "11:22:33:44:55:66";
        device.primaryDeviceType = "primaryDeviceType";
        device.secondaryDeviceType = "secondaryDeviceType";
        device.wpsConfigMethodsSupported = 0x0008;
        device.deviceCapability = 1;
        device.groupCapability = 1;
        device.status = WifiP2pDevice.CONNECTED;
        if (SdkLevel.isAtLeastV()) {
            device.setVendorData(OuiKeyedDataUtil.createTestOuiKeyedDataList(5));
        }

        Parcel parcel = Parcel.obtain();
        device.writeToParcel(parcel, 0);
        parcel.setDataPosition(0); // Rewind data position back to the beginning for read.
        WifiP2pDevice unparceledDevice = WifiP2pDevice.CREATOR.createFromParcel(parcel);

        assertEquals(device.deviceName, unparceledDevice.deviceName);
        assertEquals(device.deviceAddress, unparceledDevice.deviceAddress);
        assertEquals(device.primaryDeviceType, unparceledDevice.primaryDeviceType);
        assertEquals(device.secondaryDeviceType, unparceledDevice.secondaryDeviceType);
        assertEquals(device.wpsConfigMethodsSupported, unparceledDevice.wpsConfigMethodsSupported);
        assertEquals(device.deviceCapability, unparceledDevice.deviceCapability);
        assertEquals(device.groupCapability, unparceledDevice.groupCapability);
        assertEquals(device.status, unparceledDevice.status);
        if (SdkLevel.isAtLeastV()) {
            assertEquals(device.getVendorData(), unparceledDevice.getVendorData());
        }
    }

    /**
     * Test the setter/getter for device interface MAC address.
     */
    @Test
    public void testInterfaceMacAddressSetterGetter() {
        assumeTrue(SdkLevel.isAtLeastV());
        WifiP2pDevice device = new WifiP2pDevice();
        device.setInterfaceMacAddress(INTERFACE_MAC_ADDRESS);
        assertEquals(INTERFACE_MAC_ADDRESS, device.getInterfaceMacAddress());
    }

    /**
     * Test the setter/getter for device IP address.
     */
    @Test
    public void testIpAddressSetterGetter() {
        assumeTrue(SdkLevel.isAtLeastV());
        WifiP2pDevice device = new WifiP2pDevice();
        InetAddress ipAddress;
        try {
            ipAddress = InetAddress.getByName("192.168.49.1");
        } catch (UnknownHostException e) {
            return;
        }
        device.setIpAddress(ipAddress);
        assertEquals("192.168.49.1", device.getIpAddress().getHostAddress());
    }
}
