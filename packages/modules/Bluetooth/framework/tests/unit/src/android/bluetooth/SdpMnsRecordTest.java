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

/** Test cases for {@link SdpMnsRecord}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SdpMnsRecordTest {

    @Test
    public void createSdpMnsRecord() {
        int l2capPsm = 1;
        int rfcommChannelNumber = 1;
        int profileVersion = 1;
        int supportedFeatures = 1;
        String serviceName = "MnsRecord";

        SdpMnsRecord record =
                new SdpMnsRecord(
                        l2capPsm,
                        rfcommChannelNumber,
                        profileVersion,
                        supportedFeatures,
                        serviceName);

        assertThat(record.getL2capPsm()).isEqualTo(l2capPsm);
        assertThat(record.getRfcommChannelNumber()).isEqualTo(rfcommChannelNumber);
        assertThat(record.getProfileVersion()).isEqualTo(profileVersion);
        assertThat(record.getSupportedFeatures()).isEqualTo(supportedFeatures);
        assertThat(record.getServiceName()).isEqualTo(serviceName);
    }

    @Test
    public void writeToParcel() {
        int l2capPsm = 1;
        int rfcommChannelNumber = 1;
        int profileVersion = 1;
        int supportedFeatures = 1;
        String serviceName = "MnsRecord";

        SdpMnsRecord originalRecord =
                new SdpMnsRecord(
                        l2capPsm,
                        rfcommChannelNumber,
                        profileVersion,
                        supportedFeatures,
                        serviceName);

        Parcel parcel = Parcel.obtain();
        originalRecord.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        SdpMnsRecord recordOut = (SdpMnsRecord) SdpMnsRecord.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(recordOut.getL2capPsm()).isEqualTo(originalRecord.getL2capPsm());
        assertThat(recordOut.getRfcommChannelNumber())
                .isEqualTo(originalRecord.getRfcommChannelNumber());
        assertThat(recordOut.getProfileVersion()).isEqualTo(originalRecord.getProfileVersion());
        assertThat(recordOut.getSupportedFeatures())
                .isEqualTo(originalRecord.getSupportedFeatures());
        assertThat(recordOut.getServiceName()).isEqualTo(originalRecord.getServiceName());
    }

    @Test
    public void sdpMnsRecordToString() {
        int l2capPsm = 1;
        int rfcommChannelNumber = 1;
        int profileVersion = 1;
        int supportedFeatures = 1;
        String serviceName = "MnsRecord";

        SdpMnsRecord record =
                new SdpMnsRecord(
                        l2capPsm,
                        rfcommChannelNumber,
                        profileVersion,
                        supportedFeatures,
                        serviceName);

        String sdpMnsRecordString = record.toString();
        String expectedToString =
                "Bluetooth MNS SDP Record:\n"
                        + "RFCOMM Chan Number: "
                        + rfcommChannelNumber
                        + "\n"
                        + "L2CAP PSM: "
                        + l2capPsm
                        + "\n"
                        + "Service Name: "
                        + serviceName
                        + "\n"
                        + "Supported features: "
                        + supportedFeatures
                        + "\n"
                        + "Profile_version: "
                        + profileVersion
                        + "\n";

        assertThat(sdpMnsRecordString).isEqualTo(expectedToString);
    }
}
