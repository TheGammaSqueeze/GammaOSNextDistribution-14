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

package android.nearby;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class FastPairDeviceTest {
    private static final String NAME = "name";
    private static final byte[] DATA = new byte[] {0x01, 0x02};
    private static final String MODEL_ID = "112233";
    private static final int RSSI = -80;
    private static final int TX_POWER = -10;
    private static final String MAC_ADDRESS = "00:11:22:33:44:55";
    private static List<Integer> sMediums = new ArrayList<Integer>(List.of(1));
    private static FastPairDevice sDevice;


    @Before
    public void setup() {
        sDevice = new FastPairDevice(NAME, sMediums, RSSI, TX_POWER, MODEL_ID, MAC_ADDRESS, DATA);
    }

    @Test
    public void testParcelable() {
        Parcel dest = Parcel.obtain();
        sDevice.writeToParcel(dest, 0);
        dest.setDataPosition(0);
        FastPairDevice compareDevice = FastPairDevice.CREATOR.createFromParcel(dest);
        assertThat(compareDevice.getName()).isEqualTo(NAME);
        assertThat(compareDevice.getMediums()).isEqualTo(sMediums);
        assertThat(compareDevice.getRssi()).isEqualTo(RSSI);
        assertThat(compareDevice.getTxPower()).isEqualTo(TX_POWER);
        assertThat(compareDevice.getModelId()).isEqualTo(MODEL_ID);
        assertThat(compareDevice.getBluetoothAddress()).isEqualTo(MAC_ADDRESS);
        assertThat(compareDevice.getData()).isEqualTo(DATA);
        assertThat(compareDevice.equals(sDevice)).isTrue();
        assertThat(compareDevice.hashCode()).isEqualTo(sDevice.hashCode());
    }

    @Test
    public void describeContents() {
        assertThat(sDevice.describeContents()).isEqualTo(0);
    }

    @Test
    public void testToString() {
        assertThat(sDevice.toString()).isEqualTo(
                "FastPairDevice [name=name, medium={BLE} "
                        + "rssi=-80 txPower=-10 "
                        + "modelId=112233 bluetoothAddress=00:11:22:33:44:55]");
    }

    @Test
    public void testCreatorNewArray() {
        FastPairDevice[] fastPairDevices = FastPairDevice.CREATOR.newArray(2);
        assertThat(fastPairDevices.length).isEqualTo(2);
    }

    @Test
    public void testBuilder() {
        FastPairDevice.Builder builder = new FastPairDevice.Builder();
        FastPairDevice compareDevice = builder.setName(NAME)
                .addMedium(1)
                .setBluetoothAddress(MAC_ADDRESS)
                .setRssi(RSSI)
                .setTxPower(TX_POWER)
                .setData(DATA)
                .setModelId(MODEL_ID)
                .build();
        assertThat(compareDevice.getName()).isEqualTo(NAME);
        assertThat(compareDevice.getMediums()).isEqualTo(sMediums);
        assertThat(compareDevice.getRssi()).isEqualTo(RSSI);
        assertThat(compareDevice.getTxPower()).isEqualTo(TX_POWER);
        assertThat(compareDevice.getModelId()).isEqualTo(MODEL_ID);
        assertThat(compareDevice.getBluetoothAddress()).isEqualTo(MAC_ADDRESS);
        assertThat(compareDevice.getData()).isEqualTo(DATA);
    }
}
