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
package android.telephony.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.telephony.AvailableNetworkInfo;
import android.telephony.RadioAccessSpecifier;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AvailableNetworkInfoTest {
    private static final String OPERATOR_MCCMNC_1 = "123456";
    private static final String OPERATOR_MCCMNC_2 = "246135";
    private static final RadioAccessSpecifier RAS_1 =
        new RadioAccessSpecifier(1, new int[]{2,4,6,8}, new int[]{1,3,5,7});
    private static final RadioAccessSpecifier RAS_2 =
        new RadioAccessSpecifier(2, new int[]{1,3,5,7}, new int[]{2,4,6,8});
    private static final int SUB_ID = 123;

    private static List<String> mccMncs = new ArrayList<String>();
    private static List<Integer> bands = new ArrayList<Integer>();
    private static List<RadioAccessSpecifier> ras = new ArrayList<RadioAccessSpecifier>();

    static {
        mccMncs.add(OPERATOR_MCCMNC_1);
        mccMncs.add(OPERATOR_MCCMNC_2);

        ras.add(RAS_1);
        ras.add(RAS_2);
    }

    @Test
    public void testAvailableNetworkInfo() {
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(SUB_ID,
            AvailableNetworkInfo.PRIORITY_HIGH, mccMncs, bands);
        assertEquals(0, availableNetworkInfo.describeContents());
        assertEquals(SUB_ID, availableNetworkInfo.getSubId());
        assertEquals(AvailableNetworkInfo.PRIORITY_HIGH, availableNetworkInfo.getPriority());
        assertEquals(mccMncs, availableNetworkInfo.getMccMncs());
        assertEquals(bands, availableNetworkInfo.getBands());

        Parcel availableNetworkInfoParcel = Parcel.obtain();
        availableNetworkInfo.writeToParcel(availableNetworkInfoParcel, 0);
        availableNetworkInfoParcel.setDataPosition(0);
        AvailableNetworkInfo tempAvailableNetworkInfo =
            AvailableNetworkInfo.CREATOR.createFromParcel(availableNetworkInfoParcel);
        assertTrue(tempAvailableNetworkInfo.equals(availableNetworkInfo));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAvailableNetworkInfoBuilder_invalidPriority_throwsIllegalArgumentException() {
        AvailableNetworkInfo.Builder availableNetworkInfo =
            new AvailableNetworkInfo.Builder(SUB_ID)
                .setPriority(0);
    }

    @Test(expected = NullPointerException.class)
    public void testAvailableNetworkInfoBuilder_nullMccMnc_throwsNullPointerException() {
        AvailableNetworkInfo.Builder availableNetworkInfo =
            new AvailableNetworkInfo.Builder(SUB_ID)
                .setMccMncs(null);
    }

    @Test(expected = NullPointerException.class)
    public void testAvailableNetworkInfoBuilder_nullRAS_throwsNullPointerException() {
        AvailableNetworkInfo.Builder availableNetworkInfo =
            new AvailableNetworkInfo.Builder(SUB_ID)
                .setRadioAccessSpecifiers(null);
    }

    @Test
    public void testAvailableNetworkInfoBuilder_success() {
        AvailableNetworkInfo availableNetworkInfo =
            new AvailableNetworkInfo.Builder(SUB_ID)
                .setMccMncs(mccMncs)
                .setPriority(AvailableNetworkInfo.PRIORITY_HIGH)
                .setRadioAccessSpecifiers(ras)
                .build();

        assertEquals(SUB_ID, availableNetworkInfo.getSubId());
        assertEquals(AvailableNetworkInfo.PRIORITY_HIGH, availableNetworkInfo.getPriority());
        assertEquals(mccMncs, availableNetworkInfo.getMccMncs());
        assertEquals(ras, availableNetworkInfo.getRadioAccessSpecifiers());
        assertEquals(bands, availableNetworkInfo.getBands());
    }
}