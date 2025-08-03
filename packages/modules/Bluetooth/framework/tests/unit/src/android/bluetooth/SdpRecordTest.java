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

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/** Test cases for {@link SdpRecord}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SdpRecordTest {

    @Test
    public void createSdpRecord() {
        int rawSize = 1;
        byte[] rawData = new byte[] {0x1};

        SdpRecord record = new SdpRecord(rawSize, rawData);

        assertThat(record.getRawSize()).isEqualTo(rawSize);
        assertThat(record.getRawData()).isEqualTo(rawData);
    }

    @Test
    public void writeToParcel() {
        int rawSize = 1;
        byte[] rawData = new byte[] {0x1};

        SdpRecord originalRecord = new SdpRecord(rawSize, rawData);

        Parcel parcel = Parcel.obtain();
        originalRecord.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        SdpRecord recordOut = (SdpRecord) SdpRecord.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(recordOut.getRawSize()).isEqualTo(originalRecord.getRawSize());
        assertThat(recordOut.getRawData()).isEqualTo(originalRecord.getRawData());
    }

    @Test
    public void sdpRecordToString() {
        int rawSize = 1;
        byte[] rawData = new byte[] {0x1};

        SdpRecord record = new SdpRecord(rawSize, rawData);

        String sdpRecordString = record.toString();
        String expectedToString =
                "BluetoothSdpRecord [rawData="
                        + Arrays.toString(rawData)
                        + ", rawSize="
                        + rawSize
                        + "]";

        assertThat(sdpRecordString).isEqualTo(expectedToString);
    }
}
