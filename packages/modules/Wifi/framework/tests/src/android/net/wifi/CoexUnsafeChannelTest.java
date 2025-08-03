/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net.wifi;

import static android.net.wifi.CoexUnsafeChannel.POWER_CAP_NONE;
import static android.net.wifi.WifiScanner.WIFI_BAND_24_GHZ;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link android.net.wifi.CoexUnsafeChannel}.
 */
@SmallTest
public class CoexUnsafeChannelTest {
    @Before
    public void setUp() {
        assumeTrue(SdkLevel.isAtLeastS());
    }

    /**
     * Verifies {@link CoexUnsafeChannel#getPowerCapDbm()} returns POWER_CAP_NONE if no cap is set.
     */
    @Test
    public void testGetPowerCapDbm_noPowerCap_returnsPowerCapNone() {
        CoexUnsafeChannel unsafeChannel = new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6);

        assertThat(unsafeChannel.getPowerCapDbm()).isEqualTo(POWER_CAP_NONE);
    }

    /**
     * Verifies {@link CoexUnsafeChannel#getPowerCapDbm()} returns a power cap if a cap is set.
     */
    @Test
    public void testGetPowerCapDbm_powerCapSet_returnsSetPowerCap() {
        CoexUnsafeChannel unsafeChannel = new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6, -50);

        assertThat(unsafeChannel.getPowerCapDbm()).isEqualTo(-50);
    }

    /**
     * Verify parcel read/write for CoexUnsafeChannel with or without power cap.
     */
    @Test
    public void testParcelReadWrite_withOrWithoutCap_readEqualsWritten() throws Exception {
        CoexUnsafeChannel writeUnsafeChannelNoCap =
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6);
        CoexUnsafeChannel writeUnsafeChannelCapped =
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6, -50);

        CoexUnsafeChannel readUnsafeChannelNoCap = parcelReadWrite(writeUnsafeChannelNoCap);
        CoexUnsafeChannel readUnsafeChannelCapped = parcelReadWrite(writeUnsafeChannelCapped);

        assertThat(writeUnsafeChannelNoCap).isEqualTo(readUnsafeChannelNoCap);
        assertThat(writeUnsafeChannelCapped).isEqualTo(readUnsafeChannelCapped);
    }

    /**
     * Write the provided {@link CoexUnsafeChannel} to a parcel and deserialize it.
     */
    private static CoexUnsafeChannel parcelReadWrite(CoexUnsafeChannel writeResult)
            throws Exception {
        Parcel parcel = Parcel.obtain();
        writeResult.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);    // Rewind data position back to the beginning for read.
        return CoexUnsafeChannel.CREATOR.createFromParcel(parcel);
    }
}
