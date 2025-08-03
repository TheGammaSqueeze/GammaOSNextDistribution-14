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

/** Test cases for {@link SdpSapsRecord}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SdpSapsRecordTest {

    @Test
    public void createSdpSapsRecord() {
        int rfcommChannelNumber = 1;
        int profileVersion = 1;
        String serviceName = "SdpSapsRecord";

        SdpSapsRecord record = new SdpSapsRecord(rfcommChannelNumber, profileVersion, serviceName);

        assertThat(record.getRfcommCannelNumber()).isEqualTo(rfcommChannelNumber);
        assertThat(record.getProfileVersion()).isEqualTo(profileVersion);
        assertThat(record.getServiceName()).isEqualTo(serviceName);
    }

    @Test
    public void writeToParcel() {
        int rfcommChannelNumber = 1;
        int profileVersion = 1;
        String serviceName = "SdpSapsRecord";

        SdpSapsRecord originalRecord =
                new SdpSapsRecord(rfcommChannelNumber, profileVersion, serviceName);

        Parcel parcel = Parcel.obtain();
        originalRecord.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        SdpSapsRecord recordOut = (SdpSapsRecord) SdpSapsRecord.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(recordOut.getRfcommCannelNumber())
                .isEqualTo(originalRecord.getRfcommCannelNumber());
        assertThat(recordOut.getProfileVersion()).isEqualTo(originalRecord.getProfileVersion());
        assertThat(recordOut.getServiceName()).isEqualTo(originalRecord.getServiceName());
    }

    @Test
    public void sdpSapsRecordToString() {
        int rfcommChannelNumber = 1;
        int profileVersion = 1;
        String serviceName = "SdpSapsRecord";

        SdpSapsRecord record = new SdpSapsRecord(rfcommChannelNumber, profileVersion, serviceName);

        String sdpSapsRecordString = record.toString();
        String expectedToString =
                "Bluetooth MAS SDP Record:\n"
                        + "RFCOMM Chan Number: "
                        + rfcommChannelNumber
                        + "\n"
                        + "Service Name: "
                        + serviceName
                        + "\n"
                        + "Profile version: "
                        + profileVersion
                        + "\n";

        assertThat(sdpSapsRecordString).isEqualTo(expectedToString);
    }
}
