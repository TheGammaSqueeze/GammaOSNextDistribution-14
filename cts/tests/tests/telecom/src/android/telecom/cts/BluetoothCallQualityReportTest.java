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

package android.telecom.cts;

import android.telecom.BluetoothCallQualityReport;

public class BluetoothCallQualityReportTest extends BaseTelecomTestWithMockServices {
    /**
     * Test passing of BT call quality report to CDS.
     */
    public void testBluetoothCallQualityReport() {
        long timestamp = System.currentTimeMillis();
        int rssi = 1;
        int snr = 2;
        int retransmissionCount = 3;
        int packetsNotReceiveCount = 4;
        int negativeAcknowledgementCount = 5;

        BluetoothCallQualityReport report = new BluetoothCallQualityReport.Builder()
                .setSentTimestampMillis(timestamp)
                .setChoppyVoice(true)
                .setRssiDbm(rssi)
                .setSnrDb(snr)
                .setRetransmittedPacketsCount(retransmissionCount)
                .setPacketsNotReceivedCount(packetsNotReceiveCount)
                .setNegativeAcknowledgementCount(negativeAcknowledgementCount)
                .build();

        assertEquals(timestamp, report.getSentTimestampMillis());
        assertEquals(true, report.isChoppyVoice());
        assertEquals(rssi, report.getRssiDbm());
        assertEquals(snr, report.getSnrDb());
        assertEquals(retransmissionCount, report.getRetransmittedPacketsCount());
        assertEquals(packetsNotReceiveCount, report.getPacketsNotReceivedCount());
        assertEquals(negativeAcknowledgementCount, report.getNegativeAcknowledgementCount());
    }
}