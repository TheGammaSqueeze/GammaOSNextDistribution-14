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

package android.nearby;

import static android.nearby.ScanRequest.SCAN_TYPE_NEARBY_PRESENCE;

import static com.google.common.truth.Truth.assertThat;

import android.os.Build;
import android.os.Parcel;

import androidx.annotation.RequiresApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class NearbyDeviceParcelableTest {

    private static final long DEVICE_ID = 1234;
    private static final String BLUETOOTH_ADDRESS = "00:11:22:33:FF:EE";
    private static final byte[] SCAN_DATA = new byte[] {1, 2, 3, 4};
    private static final byte[] SALT = new byte[] {1, 2, 3, 4};
    private static final String FAST_PAIR_MODEL_ID = "1234";
    private static final int RSSI = -60;
    private static final int TX_POWER = -10;
    private static final int ACTION = 1;
    private static final int MEDIUM_BLE = 1;

    private NearbyDeviceParcelable.Builder mBuilder;

    @Before
    public void setUp() {
        mBuilder =
                new NearbyDeviceParcelable.Builder()
                        .setDeviceId(DEVICE_ID)
                        .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                        .setName("testDevice")
                        .setMedium(MEDIUM_BLE)
                        .setRssi(RSSI)
                        .setFastPairModelId(FAST_PAIR_MODEL_ID)
                        .setBluetoothAddress(BLUETOOTH_ADDRESS)
                        .setData(SCAN_DATA);
    }

    @Test
    public void testNullFields() {
        PublicCredential publicCredential =
                new PublicCredential.Builder(
                        new byte[] {1},
                        new byte[] {2},
                        new byte[] {3},
                        new byte[] {4},
                        new byte[] {5})
                        .build();
        NearbyDeviceParcelable nearbyDeviceParcelable =
                new NearbyDeviceParcelable.Builder()
                        .setMedium(MEDIUM_BLE)
                        .setPublicCredential(publicCredential)
                        .setAction(ACTION)
                        .setRssi(RSSI)
                        .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                        .setTxPower(TX_POWER)
                        .setSalt(SALT)
                        .build();

        assertThat(nearbyDeviceParcelable.getDeviceId()).isEqualTo(-1);
        assertThat(nearbyDeviceParcelable.getName()).isNull();
        assertThat(nearbyDeviceParcelable.getFastPairModelId()).isNull();
        assertThat(nearbyDeviceParcelable.getBluetoothAddress()).isNull();
        assertThat(nearbyDeviceParcelable.getData()).isNull();
        assertThat(nearbyDeviceParcelable.getMedium()).isEqualTo(MEDIUM_BLE);
        assertThat(nearbyDeviceParcelable.getRssi()).isEqualTo(RSSI);
        assertThat(nearbyDeviceParcelable.getAction()).isEqualTo(ACTION);
        assertThat(nearbyDeviceParcelable.getPublicCredential()).isEqualTo(publicCredential);
        assertThat(nearbyDeviceParcelable.getSalt()).isEqualTo(SALT);
        assertThat(nearbyDeviceParcelable.getTxPower()).isEqualTo(TX_POWER);
    }

    @Test
    public void testWriteParcel() {
        NearbyDeviceParcelable nearbyDeviceParcelable = mBuilder.build();

        Parcel parcel = Parcel.obtain();
        nearbyDeviceParcelable.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        NearbyDeviceParcelable actualNearbyDevice =
                NearbyDeviceParcelable.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(actualNearbyDevice.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(actualNearbyDevice.getRssi()).isEqualTo(RSSI);
        assertThat(actualNearbyDevice.getFastPairModelId()).isEqualTo(FAST_PAIR_MODEL_ID);
        assertThat(actualNearbyDevice.getBluetoothAddress()).isEqualTo(BLUETOOTH_ADDRESS);
        assertThat(Arrays.equals(actualNearbyDevice.getData(), SCAN_DATA)).isTrue();
    }

    @Test
    public void testWriteParcel_nullModelId() {
        NearbyDeviceParcelable nearbyDeviceParcelable = mBuilder.setFastPairModelId(null).build();

        Parcel parcel = Parcel.obtain();
        nearbyDeviceParcelable.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        NearbyDeviceParcelable actualNearbyDevice =
                NearbyDeviceParcelable.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(actualNearbyDevice.getFastPairModelId()).isNull();
    }

    @Test
    public void testWriteParcel_nullBluetoothAddress() {
        NearbyDeviceParcelable nearbyDeviceParcelable = mBuilder.setBluetoothAddress(null).build();
        Parcel parcel = Parcel.obtain();
        nearbyDeviceParcelable.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        NearbyDeviceParcelable actualNearbyDevice =
                NearbyDeviceParcelable.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(actualNearbyDevice.getBluetoothAddress()).isNull();
    }

    @Test
    public void describeContents() {
        NearbyDeviceParcelable nearbyDeviceParcelable = mBuilder.setBluetoothAddress(null).build();
        assertThat(nearbyDeviceParcelable.describeContents()).isEqualTo(0);
    }

    @Test
    public void testEqual() {
        PublicCredential publicCredential =
                new PublicCredential.Builder(
                        new byte[] {1},
                        new byte[] {2},
                        new byte[] {3},
                        new byte[] {4},
                        new byte[] {5})
                        .build();
        NearbyDeviceParcelable nearbyDeviceParcelable1 =
                mBuilder.setPublicCredential(publicCredential).build();
        NearbyDeviceParcelable nearbyDeviceParcelable2 =
                mBuilder.setPublicCredential(publicCredential).build();
        assertThat(nearbyDeviceParcelable1.equals(nearbyDeviceParcelable2)).isTrue();
    }

    @Test
    public void testCreatorNewArray() {
        NearbyDeviceParcelable[] nearbyDeviceParcelables =
                NearbyDeviceParcelable.CREATOR.newArray(2);
        assertThat(nearbyDeviceParcelables.length).isEqualTo(2);
    }
}
