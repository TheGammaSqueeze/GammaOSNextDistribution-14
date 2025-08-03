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

package android.nfc.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.nfc.AvailableNfcAntenna;
import android.nfc.NfcAdapter;
import android.nfc.NfcAntennaInfo;

import androidx.test.InstrumentationRegistry;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NfcAntennaLocationApiTest {

    private static final int ANTENNA_X = 12;
    private static final int ANTENNA_Y = 13;

    private boolean supportsHardware() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    private NfcAdapter mAdapter;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        assumeTrue(supportsHardware());
        mContext = InstrumentationRegistry.getContext();
        mAdapter = NfcAdapter.getDefaultAdapter(mContext);
        assertNotNull(mAdapter);
    }

    @After
    public void tearDown() throws Exception {
    }

    /** Tests getNfcAntennaInfo API */
    @Test
    public void testGetNfcAntennaInfo() {
        NfcAntennaInfo nfcAntennaInfo = mAdapter.getNfcAntennaInfo();

        assertNotNull(nfcAntennaInfo);

        AvailableNfcAntenna availableNfcAntenna = new AvailableNfcAntenna(ANTENNA_X, ANTENNA_Y);

        assertEquals("Wrong nfc antenna X axis",
                availableNfcAntenna.getLocationX(), ANTENNA_X);
        assertEquals("Wrong nfc antenna Y axis",
                availableNfcAntenna.getLocationY(), ANTENNA_Y);
    }

    @Test
    public void testNfcAntennaInfoConstructor() {
        int deviceWidth = 0;
        int deviceHeight = 0;
        boolean deviceFoldable = false;
        NfcAntennaInfo nfcAntennaInfo = new NfcAntennaInfo(deviceWidth, deviceHeight,
            deviceFoldable, new ArrayList<AvailableNfcAntenna>());

        assertEquals("Device widths do not match", deviceWidth,
                nfcAntennaInfo.getDeviceWidth());
        assertEquals("Device heights do not match", deviceHeight,
                nfcAntennaInfo.getDeviceHeight());
        assertEquals("Device foldable do not match", deviceFoldable,
                nfcAntennaInfo.isDeviceFoldable());
        assertEquals("Wrong number of available antennas", 0,
                nfcAntennaInfo.getAvailableNfcAntennas().size());
    }
}
