/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.net.wifi.p2p;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit test harness for {@link android.net.wifi.p2p.WifiP2pWfdInfo}
 */
@SmallTest
public class WifiP2pWfdInfoTest {

    private static final int TEST_CTRL_PORT = 9999;
    private static final int TEST_MAX_TPUT = 1024;

    private WifiP2pWfdInfo mSourceInfo = new WifiP2pWfdInfo(
            0,
            TEST_CTRL_PORT,
            TEST_MAX_TPUT);

    @Before
    public void setUp() {
        // initialize device info flags.
        mSourceInfo.setDeviceType(WifiP2pWfdInfo.DEVICE_TYPE_WFD_SOURCE);
        mSourceInfo.setSessionAvailable(true);
        mSourceInfo.setContentProtectionSupported(true);
    }

    /**
     * Verifies setters/getters.
     */
    @Test
    public void testSettersGetters() throws Exception {
        WifiP2pWfdInfo info = new WifiP2pWfdInfo();

        info.setEnabled(true);
        assertTrue(info.isEnabled());

        info.setDeviceType(WifiP2pWfdInfo.DEVICE_TYPE_WFD_SOURCE);
        assertEquals(WifiP2pWfdInfo.DEVICE_TYPE_WFD_SOURCE, info.getDeviceType());

        info.setSessionAvailable(true);
        assertTrue(info.isSessionAvailable());

        info.setContentProtectionSupported(true);
        assertTrue(info.isContentProtectionSupported());

        info.setControlPort(TEST_CTRL_PORT);
        assertEquals(TEST_CTRL_PORT, info.getControlPort());

        info.setMaxThroughput(TEST_MAX_TPUT);
        assertEquals(TEST_MAX_TPUT, info.getMaxThroughput());

        assertEquals("0110270f0400", info.getDeviceInfoHex());
    }

    /**
     * Verify WFD R2 setters/getters.
     */
    @Test
    public void testWfdR2SetterGetter() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiP2pWfdInfo info = new WifiP2pWfdInfo();
        assertFalse(info.isR2Supported());
        info.setR2DeviceType(WifiP2pWfdInfo.DEVICE_TYPE_WFD_SOURCE);
        assertEquals(WifiP2pWfdInfo.DEVICE_TYPE_WFD_SOURCE, info.getR2DeviceType());

        info.setR2DeviceInfo(0x10f);
        assertEquals(0x10f, info.getR2DeviceInfo());

        assertTrue(info.isR2Supported());
    }

    /**
     * Verify coupled-sink usage APIs.
     */
    @Test
    public void testCoupledSinkUsage() throws Exception {
        WifiP2pWfdInfo info = new WifiP2pWfdInfo();

        info.setCoupledSinkSupportAtSink(true);
        assertTrue(info.isCoupledSinkSupportedAtSink());
        info.setCoupledSinkSupportAtSink(false);
        assertFalse(info.isCoupledSinkSupportedAtSink());

        info.setCoupledSinkSupportAtSource(true);
        assertTrue(info.isCoupledSinkSupportedAtSource());
        info.setCoupledSinkSupportAtSource(false);
        assertFalse(info.isCoupledSinkSupportedAtSource());
    }

    /**
     * Verifies copy constructor.
     */
    @Test
    public void testCopyOperator() throws Exception {
        WifiP2pWfdInfo copiedInfo = new WifiP2pWfdInfo(mSourceInfo);

        // no equals operator, use toString for data comparison.
        assertEquals(mSourceInfo.toString(), copiedInfo.toString());
    }

    /**
     * Verifies parcel serialization/deserialization.
     */
    @Test
    public void testParcelOperation() throws Exception {
        Parcel parcelW = Parcel.obtain();
        mSourceInfo.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        WifiP2pWfdInfo fromParcel = WifiP2pWfdInfo.CREATOR.createFromParcel(parcelR);

        // no equals operator, use toString for data comparison.
        assertEquals(mSourceInfo.toString(), fromParcel.toString());
    }
}
