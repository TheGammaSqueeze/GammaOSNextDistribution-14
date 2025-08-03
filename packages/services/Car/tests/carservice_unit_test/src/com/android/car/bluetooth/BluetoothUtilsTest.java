/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.os.ParcelUuid;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

@RunWith(MockitoJUnitRunner.class)
public class BluetoothUtilsTest {
    static final String TEST_LOCAL_ADDRESS_STRING = "00:11:22:33:44:55";
    static final byte[] TEST_LOCAL_ADDRESS = {0x00, 0x11, 0x22, 0x33, 0x44, 0x55};

    private static final ParcelUuid[] EMPTY_UUIDS = new ParcelUuid[]{};
    private static final ParcelUuid[] A2DP_SOURCE_UUIDS =
            new ParcelUuid[]{BluetoothUuid.A2DP_SOURCE};
    private static final ParcelUuid[] A2DP_SINK_UUIDS =
            new ParcelUuid[]{BluetoothUuid.A2DP_SINK};
    private static final ParcelUuid[] HFP_HF_UUIDS =
            new ParcelUuid[]{BluetoothUuid.HFP};
    private static final ParcelUuid[] HFP_AG_UUIDS =
            new ParcelUuid[]{BluetoothUuid.HFP_AG, BluetoothUuid.HSP_AG};
    private static final ParcelUuid[] MAP_CLIENT_UUIDS =
            new ParcelUuid[]{BluetoothUuid.MAP, BluetoothUuid.MNS};
    private static final ParcelUuid[] MAP_SERVER_UUIDS =
            new ParcelUuid[]{BluetoothUuid.MAS};
    private static final ParcelUuid[] PAN_UUIDS =
            new ParcelUuid[]{BluetoothUuid.PANU, BluetoothUuid.NAP};
    private static final ParcelUuid[] PBAP_CLIENT_UUIDS =
            new ParcelUuid[]{BluetoothUuid.PBAP_PCE};
    private static final ParcelUuid[] PBAP_SERVER_UUIDS =
            new ParcelUuid[]{BluetoothUuid.PBAP_PSE};

    // Note that AVRCP's UUIDS are not used by our logic here at all as the connections to AVRCP are
    // entirely handled by the native code as an extension of A2DP connections. This should suffice
    // as a UUID that's real but will never impact profile support logic in the Car Framework.
    private static final ParcelUuid[] WRONG_UUIDS =
            new ParcelUuid[]{BluetoothUuid.AVRCP_CONTROLLER};

    @Mock
    private BluetoothDevice mMockBluetoothDevice;

    @Test
    public void testGetDeviceDebugInfo() {
        when(mMockBluetoothDevice.getName()).thenReturn("deviceName");
        when(mMockBluetoothDevice.getAddress()).thenReturn("deviceAddress");

        assertThat(BluetoothUtils.getDeviceDebugInfo(mMockBluetoothDevice))
                .isEqualTo("(name = deviceName, addr = deviceAddress)");
    }

    @Test
    public void testGetBytesFromAddress() {
        byte[] conversionResults = BluetoothUtils.getBytesFromAddress(TEST_LOCAL_ADDRESS_STRING);
        assertThat(conversionResults).isEqualTo(TEST_LOCAL_ADDRESS);
    }

    @Test
    public void testGetDeviceDebugInfo_nullDevice() {
        assertThat(BluetoothUtils.getDeviceDebugInfo(null)).isEqualTo("(null)");
    }

    @Test
    public void testIsA2dpSourceProfileSupportedBothSupported_returnsTrue() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(A2DP_SINK_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(A2DP_SOURCE_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.A2DP)).isTrue();
    }

    @Test
    public void testIsA2dpSourceProfileSupportedRemoteSupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(A2DP_SINK_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(WRONG_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.A2DP)).isFalse();
    }

    @Test
    public void testIsA2dpSourceProfileSupportedLocalSupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(WRONG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(A2DP_SOURCE_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.A2DP)).isFalse();
    }

    @Test
    public void testIsA2dpSourceProfileSupportedBothUnsupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(WRONG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(WRONG_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.A2DP)).isFalse();
    }

    @Test
    public void testIsA2dpSourceProfileSupportedBothSupportSameRole_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(A2DP_SOURCE_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(A2DP_SOURCE_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.A2DP)).isFalse();
    }

    @Test
    public void testIsA2dpSinkProfileSupportedBothSupported_returnsTrue() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(A2DP_SOURCE_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(A2DP_SINK_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.A2DP_SINK)).isTrue();
    }

    @Test
    public void testIsA2dpSinkProfileSupportedRemoteSupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(A2DP_SOURCE_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(WRONG_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.A2DP_SINK)).isFalse();
    }

    @Test
    public void testIsA2dpSinkProfileSupportedLocalSupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(WRONG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(A2DP_SINK_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.A2DP_SINK)).isFalse();
    }

    @Test
    public void testIsA2dpSinkProfileSupportedBothUnsupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(WRONG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(WRONG_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.A2DP_SINK)).isFalse();
    }

    @Test
    public void testIsA2dpSinkProfileSupportedBothSupportSameRole_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(A2DP_SINK_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(A2DP_SINK_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.A2DP_SINK)).isFalse();
    }

    @Test
    public void testIsHfpHfProfileSupportedBothSupported_returnsTrue() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(HFP_AG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(HFP_HF_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.HEADSET_CLIENT)).isTrue();
    }

    @Test
    public void testIsHfpHfProfileSupportedRemoteSupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(HFP_AG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(WRONG_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.HEADSET_CLIENT)).isFalse();
    }

    @Test
    public void testIsHfpHfProfileSupportedLocalSupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(WRONG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(HFP_HF_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.HEADSET_CLIENT)).isFalse();
    }

    @Test
    public void testIsHfpHfProfileSupportedBothUnsupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(WRONG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(WRONG_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.HEADSET_CLIENT)).isFalse();
    }

    @Test
    public void testIsHfpHfProfileSupportedSameRoleSupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(HFP_HF_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(HFP_HF_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.HEADSET_CLIENT)).isFalse();
    }

    @Test
    public void testIsMapClientProfileSupportedBothSupported_returnsTrue() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(MAP_SERVER_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(MAP_CLIENT_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.MAP_CLIENT)).isTrue();
    }

    @Test
    public void testIsMapClientProfileSupportedRemoteSupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(MAP_SERVER_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(WRONG_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.MAP_CLIENT)).isFalse();
    }

    @Test
    public void testIsMapClientProfileSupportedLocalSupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(WRONG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(MAP_CLIENT_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.MAP_CLIENT)).isFalse();
    }

    @Test
    public void testIsMapClientProfileSupportedBothUnsupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(WRONG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(WRONG_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.MAP_CLIENT)).isFalse();
    }

    @Test
    public void testIsMapClientProfileSupportedSameRoleSupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(MAP_CLIENT_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(MAP_CLIENT_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.MAP_CLIENT)).isFalse();
    }

    @Test
    public void testIsPanProfileSupportedBothSupported_returnsTrue() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(PAN_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(PAN_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.PAN)).isTrue();
    }

    @Test
    public void testIsPanProfileSupportedRemoteSupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(PAN_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(WRONG_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.PAN)).isFalse();
    }

    @Test
    public void testIsPanProfileSupportedLocalSupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(WRONG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(PAN_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.PAN)).isFalse();
    }

    @Test
    public void testIsPanProfileSupportedBothUnsupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(WRONG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(WRONG_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.PAN)).isFalse();
    }

    @Test
    public void testIsPbapClientProfileSupportedBothSupported_returnsTrue() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(PBAP_SERVER_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(PBAP_CLIENT_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.PBAP_CLIENT)).isTrue();
    }

    @Test
    public void testIsPbapClientProfileSupportedRemoteSupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(PBAP_SERVER_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(WRONG_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.PBAP_CLIENT)).isFalse();
    }

    @Test
    public void testIsPbapClientProfileSupportedLocalSupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(WRONG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(PBAP_CLIENT_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.PBAP_CLIENT)).isFalse();
    }

    @Test
    public void testIsPbapClientProfileSupportedBothUnsupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(WRONG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(WRONG_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.PBAP_CLIENT)).isFalse();
    }

    @Test
    public void testIsPbapClientProfileSupportedSameRoleSupported_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(PBAP_CLIENT_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(PBAP_CLIENT_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.PBAP_CLIENT)).isFalse();
    }

    @Test
    public void testIsProfileSupportedNullDevice_returnsFalse() {
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(WRONG_UUIDS),
                null, BluetoothProfile.HEADSET_CLIENT)).isFalse();
    }

    @Test
    public void testIsProfileSupportedNullRemoteUuids_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(null);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(WRONG_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.HEADSET_CLIENT)).isFalse();
    }

    @Test
    public void testIsProfileSupportedEmptyRemoteUuids_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(EMPTY_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(WRONG_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.HEADSET_CLIENT)).isFalse();
    }

    @Test
    public void testIsProfileSupportedNullLocalUuids_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(WRONG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(null, mMockBluetoothDevice,
                BluetoothProfile.HEADSET_CLIENT)).isFalse();
    }

    @Test
    public void testIsProfileSupportedEmptyLocalUuids_returnsFalse() {
        when(mMockBluetoothDevice.getUuids()).thenReturn(WRONG_UUIDS);
        assertThat(BluetoothUtils.isProfileSupported(Arrays.asList(EMPTY_UUIDS),
                mMockBluetoothDevice, BluetoothProfile.HEADSET_CLIENT)).isFalse();
    }
}
