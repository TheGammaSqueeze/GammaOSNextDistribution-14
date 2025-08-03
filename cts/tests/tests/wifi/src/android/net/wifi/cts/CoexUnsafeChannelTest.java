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

package android.net.wifi.cts;

import static android.net.wifi.CoexUnsafeChannel.POWER_CAP_NONE;

import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.WifiScanner;
import android.os.Build;
import android.test.AndroidTestCase;

import androidx.test.filters.SdkSuppress;

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
public class CoexUnsafeChannelTest extends AndroidTestCase {
    final static int TEST_BAND = WifiScanner.WIFI_BAND_24_GHZ;
    final static int TEST_CHANNEL = 6;
    final static int TEST_POWER_CAP_DBM = -50;

    public void testNoPowerCapConstructor() {
        CoexUnsafeChannel unsafeChannel = new CoexUnsafeChannel(TEST_BAND, TEST_CHANNEL);

        assertEquals(TEST_BAND, unsafeChannel.getBand());
        assertEquals(TEST_CHANNEL, unsafeChannel.getChannel());
        assertEquals(POWER_CAP_NONE, unsafeChannel.getPowerCapDbm());
    }

    public void testPowerCapConstructor() {
        CoexUnsafeChannel unsafeChannel = new CoexUnsafeChannel(TEST_BAND, TEST_CHANNEL,
                TEST_POWER_CAP_DBM);

        assertEquals(TEST_BAND, unsafeChannel.getBand());
        assertEquals(TEST_CHANNEL, unsafeChannel.getChannel());
        assertEquals(TEST_POWER_CAP_DBM, unsafeChannel.getPowerCapDbm());
    }
}
