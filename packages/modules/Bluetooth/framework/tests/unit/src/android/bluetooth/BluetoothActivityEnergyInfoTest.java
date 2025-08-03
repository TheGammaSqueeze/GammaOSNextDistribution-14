/*
 * Copyright 2022 The Android Open Source Project
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

package android.bluetooth;

import static android.bluetooth.BluetoothActivityEnergyInfo.BT_STACK_STATE_INVALID;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/** Test cases for {@link BluetoothActivityEnergyInfo}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothActivityEnergyInfoTest {

    @Test
    public void constructor() {
        long timestamp = 10000;
        int stackState = BT_STACK_STATE_INVALID;
        long txTime = 100;
        long rxTime = 200;
        long idleTime = 300;
        long energyUsed = 10;
        BluetoothActivityEnergyInfo info =
                new BluetoothActivityEnergyInfo(
                        timestamp, stackState, txTime, rxTime, idleTime, energyUsed);

        assertThat(info.getTimestampMillis()).isEqualTo(timestamp);
        assertThat(info.getBluetoothStackState()).isEqualTo(stackState);
        assertThat(info.getControllerTxTimeMillis()).isEqualTo(txTime);
        assertThat(info.getControllerRxTimeMillis()).isEqualTo(rxTime);
        assertThat(info.getControllerIdleTimeMillis()).isEqualTo(idleTime);
        assertThat(info.getControllerEnergyUsed()).isEqualTo(energyUsed);
        assertThat(info.getUidTraffic()).isEmpty();
    }

    @Test
    public void setUidTraffic() {
        long timestamp = 10000;
        int stackState = BT_STACK_STATE_INVALID;
        long txTime = 100;
        long rxTime = 200;
        long idleTime = 300;
        long energyUsed = 10;
        BluetoothActivityEnergyInfo info =
                new BluetoothActivityEnergyInfo(
                        timestamp, stackState, txTime, rxTime, idleTime, energyUsed);

        ArrayList<UidTraffic> traffics = new ArrayList<>();
        UidTraffic traffic = new UidTraffic(123, 300, 400);
        traffics.add(traffic);
        info.setUidTraffic(traffics);

        assertThat(info.getUidTraffic().size()).isEqualTo(1);
        assertThat(info.getUidTraffic().get(0)).isEqualTo(traffic);
    }

    @Test
    public void isValid() {
        long timestamp = 10000;
        int stackState = BT_STACK_STATE_INVALID;
        long txTime = 100;
        long rxTime = 200;
        long idleTime = 300;
        long energyUsed = 10;
        BluetoothActivityEnergyInfo info =
                new BluetoothActivityEnergyInfo(
                        timestamp, stackState, txTime, rxTime, idleTime, energyUsed);

        assertThat(info.isValid()).isEqualTo(true);

        info =
                new BluetoothActivityEnergyInfo(
                        timestamp, stackState, -1, rxTime, idleTime, energyUsed);
        assertThat(info.isValid()).isEqualTo(false);

        info =
                new BluetoothActivityEnergyInfo(
                        timestamp, stackState, txTime, -1, idleTime, energyUsed);
        assertThat(info.isValid()).isEqualTo(false);

        info =
                new BluetoothActivityEnergyInfo(
                        timestamp, stackState, txTime, rxTime, -1, energyUsed);
        assertThat(info.isValid()).isEqualTo(false);
    }

    @Test
    public void writeToParcel() {
        long timestamp = 10000;
        int stackState = BT_STACK_STATE_INVALID;
        long txTime = 100;
        long rxTime = 200;
        long idleTime = 300;
        long energyUsed = 10;
        BluetoothActivityEnergyInfo info =
                new BluetoothActivityEnergyInfo(
                        timestamp, stackState, txTime, rxTime, idleTime, energyUsed);

        Parcel parcel = Parcel.obtain();
        info.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BluetoothActivityEnergyInfo infoFromParcel =
                BluetoothActivityEnergyInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(infoFromParcel.getTimestampMillis()).isEqualTo(timestamp);
        assertThat(infoFromParcel.getBluetoothStackState()).isEqualTo(stackState);
        assertThat(infoFromParcel.getControllerTxTimeMillis()).isEqualTo(txTime);
        assertThat(infoFromParcel.getControllerRxTimeMillis()).isEqualTo(rxTime);
        assertThat(infoFromParcel.getControllerIdleTimeMillis()).isEqualTo(idleTime);
        assertThat(infoFromParcel.getControllerEnergyUsed()).isEqualTo(energyUsed);
        assertThat(infoFromParcel.getUidTraffic()).isEmpty();
    }

    @Test
    public void toString_ThrowsNoExceptions() {
        long timestamp = 10000;
        int stackState = BT_STACK_STATE_INVALID;
        long txTime = 100;
        long rxTime = 200;
        long idleTime = 300;
        long energyUsed = 10;
        BluetoothActivityEnergyInfo info =
                new BluetoothActivityEnergyInfo(
                        timestamp, stackState, txTime, rxTime, idleTime, energyUsed);

        try {
            String infoString = info.toString();
        } catch (Exception e) {
            Assert.fail("Should throw a RuntimeException");
        }
    }
}
