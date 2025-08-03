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

package android.telephony.ims.cts;

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.telephony.ims.RcsClientConfiguration;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class RcsClientConfigurationTest {

    private static final String RCS_VERSION = "9.0";
    private static final String RCS_PROFILE = RcsClientConfiguration.RCS_PROFILE_2_3;
    private static final String CLIENT_VENDOR = "Android";
    private static final String CLIENT_VERSION = "RCSAndrd-1.0";
    private static final boolean RCS_ENABLED_BY_USER = true;

    @Test
    public void testRcsClientConfigurationApi() {
        RcsClientConfiguration rcc = new RcsClientConfiguration(
                RCS_VERSION, RCS_PROFILE, CLIENT_VENDOR, CLIENT_VERSION, RCS_ENABLED_BY_USER);

        assertEquals(RCS_VERSION, rcc.getRcsVersion());
        assertEquals(RCS_PROFILE, rcc.getRcsProfile());
        assertEquals(CLIENT_VENDOR, rcc.getClientVendor());
        assertEquals(CLIENT_VERSION, rcc.getClientVersion());
        assertEquals(RCS_ENABLED_BY_USER, rcc.isRcsEnabledByUser());
    }

    @Test
    public void testRcsClientConfigurationParcel() {
        RcsClientConfiguration rcc = new RcsClientConfiguration(
                RCS_VERSION, RCS_PROFILE, CLIENT_VENDOR, CLIENT_VERSION, RCS_ENABLED_BY_USER);
        Parcel rccParcel = Parcel.obtain();
        rcc.writeToParcel(rccParcel, 0);
        rccParcel.setDataPosition(0);
        RcsClientConfiguration checkRcc =
                RcsClientConfiguration.CREATOR.createFromParcel(rccParcel);

        assertEquals(0, rcc.describeContents());
        assertEquals(rcc, checkRcc);
    }
}
