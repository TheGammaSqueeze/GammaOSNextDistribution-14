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

/** Test cases for {@link SdpOppOpsRecord}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SdpOppOpsRecordTest {

    @Test
    public void createSdpOppOpsRecord() {
        String serviceName = "OppOpsRecord";
        int rfcommChannel = 1;
        int l2capPsm = 1;
        int version = 1;
        byte[] formatsList = new byte[] {0x01};

        SdpOppOpsRecord record =
                new SdpOppOpsRecord(serviceName, rfcommChannel, l2capPsm, version, formatsList);

        assertThat(record.getServiceName()).isEqualTo(serviceName);
        assertThat(record.getRfcommChannel()).isEqualTo(rfcommChannel);
        assertThat(record.getL2capPsm()).isEqualTo(l2capPsm);
        assertThat(record.getProfileVersion()).isEqualTo(version);
        assertThat(record.getFormatsList()).isEqualTo(formatsList);
    }

    @Test
    public void writeToParcel() {
        String serviceName = "OppOpsRecord";
        int rfcommChannel = 1;
        int l2capPsm = 1;
        int version = 1;
        byte[] formatsList = new byte[] {0x01};

        SdpOppOpsRecord originalRecord =
                new SdpOppOpsRecord(serviceName, rfcommChannel, l2capPsm, version, formatsList);

        Parcel parcel = Parcel.obtain();
        originalRecord.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        SdpOppOpsRecord recordOut =
                (SdpOppOpsRecord) SdpOppOpsRecord.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(recordOut.getServiceName()).isEqualTo(originalRecord.getServiceName());
        assertThat(recordOut.getRfcommChannel()).isEqualTo(originalRecord.getRfcommChannel());
        assertThat(recordOut.getL2capPsm()).isEqualTo(originalRecord.getL2capPsm());
        assertThat(recordOut.getProfileVersion()).isEqualTo(originalRecord.getProfileVersion());
        assertThat(recordOut.getProfileVersion()).isEqualTo(originalRecord.getProfileVersion());
        assertThat(recordOut.getFormatsList()).isEqualTo(originalRecord.getFormatsList());
    }

    @Test
    public void sdpOppOpsRecordToString() {
        String serviceName = "OppOpsRecord";
        int rfcommChannel = 1;
        int l2capPsm = 1;
        int version = 1;
        byte[] formatsList = new byte[] {0x01};

        SdpOppOpsRecord record =
                new SdpOppOpsRecord(serviceName, rfcommChannel, l2capPsm, version, formatsList);

        String sdpOppOpsRecordString = record.toString();
        String expectedToString =
                "Bluetooth OPP Server SDP Record:\n"
                        + "  RFCOMM Chan Number: "
                        + rfcommChannel
                        + "\n"
                        + "  L2CAP PSM: "
                        + l2capPsm
                        + "\n"
                        + "  Profile version: "
                        + version
                        + "\n"
                        + "  Service Name: "
                        + serviceName
                        + "\n"
                        + "  Formats List: "
                        + Arrays.toString(formatsList);

        assertThat(sdpOppOpsRecordString).isEqualTo(expectedToString);
    }
}
