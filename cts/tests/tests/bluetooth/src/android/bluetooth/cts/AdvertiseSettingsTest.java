/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSetParameters;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for {@link AdvertiseSettings}.
 */
@RunWith(AndroidJUnit4.class)
public class AdvertiseSettingsTest {

    @Before
    public void setUp() {
        Assume.assumeTrue(TestUtils.isBleSupported(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void defaultSettings() {
        AdvertiseSettings settings = new AdvertiseSettings.Builder().build();
        assertEquals(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER, settings.getMode());
        assertEquals(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM, settings.getTxPowerLevel());
        assertEquals(0, settings.getTimeout());
        assertTrue(settings.isConnectable());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void describeContents() {
        AdvertiseSettings settings = new AdvertiseSettings.Builder().build();
        assertEquals(0, settings.describeContents());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void readWriteParcel() {
        final int timeoutMillis = 60 * 1000;
        Parcel parcel = Parcel.obtain();
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(false)
                .setDiscoverable(false)
                .setTimeout(timeoutMillis)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_DEFAULT)
                .build();
        settings.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseSettings settingsFromParcel = AdvertiseSettings.CREATOR.createFromParcel(parcel);
        assertEquals(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY, settingsFromParcel.getMode());
        assertEquals(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM,
                settingsFromParcel.getTxPowerLevel());
        assertEquals(timeoutMillis, settingsFromParcel.getTimeout());
        assertFalse(settings.isConnectable());
        assertFalse(settings.isDiscoverable());
        assertEquals(AdvertisingSetParameters.ADDRESS_TYPE_DEFAULT, settings.getOwnAddressType());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void illegalTimeout() {
        AdvertiseSettings.Builder builder = new AdvertiseSettings.Builder();
        builder.setTimeout(0).build();
        builder.setTimeout(180 * 1000).build();
        // Maximum timeout is 3 minutes.
        try {
            builder.setTimeout(180 * 1000 + 1).build();
            fail("should not allow setting timeout to more than 3 minutes");
        } catch (IllegalArgumentException e) {
            // nothing to do.
        }
        // Negative time out is not allowed.
        try {
            builder.setTimeout(-1).build();
            fail("should not allow setting timeout to more than 3 minutes");
        } catch (IllegalArgumentException e) {
            // nothing to do.
        }

    }
}
