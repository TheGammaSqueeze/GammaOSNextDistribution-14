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

package android.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Test cases for {@link SdpMasRecord}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SdpMasRecordTest {

    @Test
    public void createSdpMasRecord() {
        int masInstanceId = 1;
        int l2capPsm = 1;
        int rfcommChannelNumber = 1;
        int profileVersion = 1;
        int supportedFeatures = 1;
        int supportedMessageTypes = 1;
        String serviceName = "MasRecord";

        SdpMasRecord record =
                new SdpMasRecord(
                        masInstanceId,
                        l2capPsm,
                        rfcommChannelNumber,
                        profileVersion,
                        supportedFeatures,
                        supportedMessageTypes,
                        serviceName);

        assertThat(record.getMasInstanceId()).isEqualTo(masInstanceId);
        assertThat(record.getL2capPsm()).isEqualTo(l2capPsm);
        assertThat(record.getRfcommCannelNumber()).isEqualTo(rfcommChannelNumber);
        assertThat(record.getProfileVersion()).isEqualTo(profileVersion);
        assertThat(record.getSupportedFeatures()).isEqualTo(supportedFeatures);
        assertThat(record.getSupportedMessageTypes()).isEqualTo(supportedMessageTypes);
        assertThat(record.getServiceName()).isEqualTo(serviceName);
    }

    @Test
    public void writeToParcel() {
        int masInstanceId = 1;
        int l2capPsm = 1;
        int rfcommChannelNumber = 1;
        int profileVersion = 1;
        int supportedFeatures = 1;
        int supportedMessageTypes = 1;
        String serviceName = "MasRecord";

        SdpMasRecord originalRecord =
                new SdpMasRecord(
                        masInstanceId,
                        l2capPsm,
                        rfcommChannelNumber,
                        profileVersion,
                        supportedFeatures,
                        supportedMessageTypes,
                        serviceName);

        Parcel parcel = Parcel.obtain();
        originalRecord.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        SdpMasRecord recordOut = (SdpMasRecord) SdpMasRecord.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(recordOut.getMasInstanceId()).isEqualTo(originalRecord.getMasInstanceId());
        assertThat(recordOut.getL2capPsm()).isEqualTo(originalRecord.getL2capPsm());
        assertThat(recordOut.getRfcommCannelNumber())
                .isEqualTo(originalRecord.getRfcommCannelNumber());
        assertThat(recordOut.getProfileVersion()).isEqualTo(originalRecord.getProfileVersion());
        assertThat(recordOut.getSupportedFeatures())
                .isEqualTo(originalRecord.getSupportedFeatures());
        assertThat(recordOut.getSupportedMessageTypes())
                .isEqualTo(originalRecord.getSupportedMessageTypes());
        assertThat(recordOut.getServiceName()).isEqualTo(originalRecord.getServiceName());
    }

    @Test
    public void sdpMasRecordToString() {
        int masInstanceId = 1;
        int l2capPsm = 1;
        int rfcommChannelNumber = 1;
        int profileVersion = 1;
        int supportedFeatures = 1;
        int supportedMessageTypes = 1;
        String serviceName = "MasRecord";

        SdpMasRecord record =
                new SdpMasRecord(
                        masInstanceId,
                        l2capPsm,
                        rfcommChannelNumber,
                        profileVersion,
                        supportedFeatures,
                        supportedMessageTypes,
                        serviceName);

        String sdpMasRecordString = record.toString();
        String expectedToString =
                "Bluetooth MAS SDP Record:\n"
                        + "Mas Instance Id: "
                        + masInstanceId
                        + "\n"
                        + "RFCOMM Chan Number: "
                        + l2capPsm
                        + "\n"
                        + "L2CAP PSM: "
                        + rfcommChannelNumber
                        + "\n"
                        + "Service Name: "
                        + serviceName
                        + "\n"
                        + "Profile version: "
                        + profileVersion
                        + "\n"
                        + "Supported msg types: "
                        + supportedMessageTypes
                        + "\n"
                        + "Supported features: "
                        + supportedFeatures
                        + "\n";

        assertThat(sdpMasRecordString).isEqualTo(expectedToString);
    }
}
