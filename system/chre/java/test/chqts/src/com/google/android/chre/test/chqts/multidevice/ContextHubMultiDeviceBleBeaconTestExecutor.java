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

package com.google.android.chre.test.chqts.multidevice;

import android.hardware.location.NanoAppBinary;

import com.google.android.chre.test.chqts.ContextHubBleTestExecutor;
import com.google.android.utils.chre.ChreApiTestUtil;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

import dev.chre.rpc.proto.ChreApiTest;

public class ContextHubMultiDeviceBleBeaconTestExecutor extends ContextHubBleTestExecutor {
    private static final int NUM_EVENTS_TO_GATHER = 10;

    private static final long TIMEOUT_IN_S = 30;

    private static final long TIMEOUT_IN_NS = TIMEOUT_IN_S * 1000000000L;

    public ContextHubMultiDeviceBleBeaconTestExecutor(NanoAppBinary nanoapp) {
        super(nanoapp);
    }

    /**
     * Gathers BLE advertisement events from the nanoapp for TIMEOUT_IN_NS or up to
     * NUM_EVENTS_TO_GATHER events. This function returns true if all
     * chreBleAdvertisingReport's contain advertisments for Google Eddystone and
     * there is at least one advertisement, otherwise it returns false.
     */
    public boolean gatherAndVerifyChreBleAdvertisementsForGoogleEddystone() throws Exception {
        Future<List<ChreApiTest.GeneralEventsMessage>> eventsFuture =
                new ChreApiTestUtil().gatherEvents(
                        mRpcClients.get(0),
                        Arrays.asList(CHRE_EVENT_BLE_ADVERTISEMENT),
                        NUM_EVENTS_TO_GATHER,
                        TIMEOUT_IN_NS);

        List<ChreApiTest.GeneralEventsMessage> events = eventsFuture.get();
        if (events == null) {
            return false;
        }

        boolean foundGoogleEddystoneBleAdvertisement = false;
        for (ChreApiTest.GeneralEventsMessage event: events) {
            if (!event.hasChreBleAdvertisementEvent()) {
                continue;
            }

            ChreApiTest.ChreBleAdvertisementEvent bleAdvertisementEvent =
                    event.getChreBleAdvertisementEvent();
            for (int i = 0; i < bleAdvertisementEvent.getReportsCount(); ++i) {
                ChreApiTest.ChreBleAdvertisingReport report = bleAdvertisementEvent.getReports(i);
                byte[] data = report.getData().toByteArray();
                if (data == null || data.length < 2) {
                    continue;
                }

                if (!searchForGoogleEddystoneAdvertisement(data)) {
                    return false;
                }
                foundGoogleEddystoneBleAdvertisement = true;
            }
        }
        return foundGoogleEddystoneBleAdvertisement;
    }

    /**
     * Starts a BLE scan with the Google Eddystone filter.
     */
    public void chreBleStartScanSyncWithGoogleEddystoneFilter() throws Exception {
        chreBleStartScanSync(getGoogleEddystoneScanFilter());
    }

    /**
     * Returns true if the data contains an advertisement for Google Eddystone,
     * otherwise returns false.
     */
    private boolean searchForGoogleEddystoneAdvertisement(byte[] data) {
        if (data.length < 2) {
            return false;
        }

        for (int j = 0; j < data.length - 1; ++j) {
            if (Byte.compare(data[j], (byte) 0xAA) == 0
                    && Byte.compare(data[j + 1], (byte) 0xFE) == 0) {
                return true;
            }
        }
        return false;
    }
}
