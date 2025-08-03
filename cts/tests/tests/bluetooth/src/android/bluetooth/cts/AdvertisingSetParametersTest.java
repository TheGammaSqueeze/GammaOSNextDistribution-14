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

package android.bluetooth.cts;

import static android.bluetooth.BluetoothDevice.PHY_LE_1M;
import static android.bluetooth.BluetoothDevice.PHY_LE_2M;
import static android.bluetooth.BluetoothDevice.PHY_LE_CODED;
import static android.bluetooth.le.AdvertisingSetParameters.INTERVAL_LOW;
import static android.bluetooth.le.AdvertisingSetParameters.INTERVAL_MAX;
import static android.bluetooth.le.AdvertisingSetParameters.INTERVAL_MEDIUM;
import static android.bluetooth.le.AdvertisingSetParameters.INTERVAL_MIN;
import static android.bluetooth.le.AdvertisingSetParameters.TX_POWER_MAX;
import static android.bluetooth.le.AdvertisingSetParameters.TX_POWER_MEDIUM;
import static android.bluetooth.le.AdvertisingSetParameters.TX_POWER_MIN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.bluetooth.le.AdvertisingSetParameters;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AdvertisingSetParametersTest {

    @Before
    public void setUp() {
        Assume.assumeTrue(TestUtils.isBleSupported(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createFromParcel() {
        final Parcel parcel = Parcel.obtain();
        try {
            AdvertisingSetParameters params = new AdvertisingSetParameters.Builder().build();
            params.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            AdvertisingSetParameters paramsFromParcel =
                    AdvertisingSetParameters.CREATOR.createFromParcel(parcel);
            assertParamsEquals(params, paramsFromParcel);
        } finally {
            parcel.recycle();
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void defaultParameters() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder().build();

        assertFalse(params.isConnectable());
        assertTrue(params.isDiscoverable());
        assertFalse(params.isScannable());
        assertFalse(params.isLegacy());
        assertFalse(params.isAnonymous());
        assertFalse(params.includeTxPower());
        assertEquals(PHY_LE_1M, params.getPrimaryPhy());
        assertEquals(PHY_LE_1M, params.getSecondaryPhy());
        assertEquals(INTERVAL_LOW, params.getInterval());
        assertEquals(TX_POWER_MEDIUM, params.getTxPowerLevel());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isConnectable() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setConnectable(true)
                .build();
        assertTrue(params.isConnectable());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isDiscoverable() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setDiscoverable(false)
                .build();
        assertFalse(params.isDiscoverable());
    }


    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isScannable() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setScannable(true)
                .build();
        assertTrue(params.isScannable());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isLegacyMode() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setLegacyMode(true)
                .build();
        assertTrue(params.isLegacy());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void includeTxPower() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setIncludeTxPower(true)
                .build();
        assertTrue(params.includeTxPower());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setPrimaryPhyWithInvalidValue() {
        try {
            // Set invalid value
            new AdvertisingSetParameters.Builder().setPrimaryPhy(PHY_LE_2M);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setPrimaryPhyWithLE1M() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setPrimaryPhy(PHY_LE_1M)
                .build();
        assertEquals(PHY_LE_1M, params.getPrimaryPhy());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setPrimaryPhyWithLECoded() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setPrimaryPhy(PHY_LE_CODED)
                .build();
        assertEquals(PHY_LE_CODED, params.getPrimaryPhy());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setSecondaryPhyWithInvalidValue() {
        int INVALID_SECONDARY_PHY = -1;
        try {
            // Set invalid value
            new AdvertisingSetParameters.Builder().setSecondaryPhy(INVALID_SECONDARY_PHY);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setSecondaryPhyWithLE1M() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setSecondaryPhy(PHY_LE_1M)
                .build();
        assertEquals(PHY_LE_1M, params.getSecondaryPhy());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setSecondaryPhyWithLE2M() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setSecondaryPhy(PHY_LE_2M)
                .build();
        assertEquals(PHY_LE_2M, params.getSecondaryPhy());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setSecondaryPhyWithLECoded() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setSecondaryPhy(PHY_LE_CODED)
                .build();
        assertEquals(PHY_LE_CODED, params.getSecondaryPhy());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void intervalWithInvalidValues() {
        int[] invalidValues = {INTERVAL_MIN - 1, INTERVAL_MAX + 1};
        for (int i = 0; i < invalidValues.length; i++) {
            try {
                // Set invalid value
                new AdvertisingSetParameters.Builder().setInterval(invalidValues[i]);
                fail();
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void interval() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setInterval(INTERVAL_MEDIUM)
                .build();
        assertEquals(INTERVAL_MEDIUM, params.getInterval());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void txPowerLevelWithInvalidValues() {
        int[] invalidValues = { TX_POWER_MIN - 1, TX_POWER_MAX + 1 };
        for (int i = 0; i < invalidValues.length; i++) {
            try {
                // Set invalid value
                new AdvertisingSetParameters.Builder().setTxPowerLevel(TX_POWER_MIN - 1);
                fail();
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void txPowerLevel() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setTxPowerLevel(TX_POWER_MEDIUM)
                .build();
        assertEquals(TX_POWER_MEDIUM, params.getTxPowerLevel());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isAnonymous() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setAnonymous(true).build();
        assertTrue(params.isAnonymous());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void describeContents() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder().build();
        assertEquals(0, params.describeContents());
    }

    private void assertParamsEquals(AdvertisingSetParameters p, AdvertisingSetParameters other) {
        if (p == null && other == null) {
            return;
        }

        if (p == null || other == null) {
            fail("Cannot compare null with non-null value: p=" + p + ", other=" + other);
        }

        assertEquals(p.isConnectable(), other.isConnectable());
        assertEquals(p.isDiscoverable(), other.isDiscoverable());
        assertEquals(p.isScannable(), other.isScannable());
        assertEquals(p.isLegacy(), other.isLegacy());
        assertEquals(p.isAnonymous(), other.isAnonymous());
        assertEquals(p.includeTxPower(), other.includeTxPower());
        assertEquals(p.getPrimaryPhy(), other.getPrimaryPhy());
        assertEquals(p.getSecondaryPhy(), other.getSecondaryPhy());
        assertEquals(p.getInterval(), other.getInterval());
        assertEquals(p.getTxPowerLevel(), other.getTxPowerLevel());
    }
}
