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


package android.telephony.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.telephony.CellBroadcastIdRange;
import android.telephony.SmsCbMessage;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CellBroadcastIdRangeTest {
    private static final int[][] CHANNEL_VALUES = {
        {0, 999}, {1000, 1003}, {1004, 0x0FFF}, {0x1000, 0x10FF}, {0x1100, 0x112F},
        {0x1130, 0x1900}, {0x1901, 0x9FFF}, {0xA000, 0xFFFE}, {0xFFFF, 0xFFFF}};
    private static final int[] TYPE_VALUES = {
        SmsCbMessage.MESSAGE_FORMAT_3GPP, SmsCbMessage.MESSAGE_FORMAT_3GPP2};
    private static final boolean[] ENABLED_VALUES = {true, false};

    @Test
    @ApiTest(apis = {
            "android.telephony.CellBroadcastIdRange#CellBroadcastIdRange",
            "android.telephony.CellBroadcastIdRange#getStartId",
            "android.telephony.CellBroadcastIdRange#getEndId",
            "android.telephony.CellBroadcastIdRange#getType",
            "android.telephony.CellBroadcastIdRange#isEnabled"})
    public void testCreateCellBroadcastIdRange() {
        //IllegalArgumentException is expected if id is negative
        assertThrows(IllegalArgumentException.class, () -> {
            new CellBroadcastIdRange(-1, 1, SmsCbMessage.MESSAGE_FORMAT_3GPP, true);
        });

        //IllegalArgumentException is expected if startId > endId
        assertThrows(IllegalArgumentException.class, () -> {
            new CellBroadcastIdRange(0x1000, 1, SmsCbMessage.MESSAGE_FORMAT_3GPP, true);
        });

        for (int i = 0; i < CHANNEL_VALUES.length; i++) {
            for (int j = 0; j < TYPE_VALUES.length; j++) {
                for (int k = 0; k < ENABLED_VALUES.length; k++) {
                    CellBroadcastIdRange range = new CellBroadcastIdRange(CHANNEL_VALUES[i][0],
                            CHANNEL_VALUES[i][1], TYPE_VALUES[j], ENABLED_VALUES[k]);
                    assertEquals(range.getStartId(), CHANNEL_VALUES[i][0]);
                    assertEquals(range.getEndId(), CHANNEL_VALUES[i][1]);
                    assertEquals(range.getType(), TYPE_VALUES[j]);
                    assertEquals(range.isEnabled(), ENABLED_VALUES[k]);
                }
            }
        }
    }

    @Test
    @ApiTest(apis = {
            "android.telephony.CellBroadcastIdRange#CellBroadcastIdRange",
            "android.telephony.CellBroadcastIdRange#equals"})
    public void testEquals() {
        CellBroadcastIdRange range1 = new CellBroadcastIdRange(CHANNEL_VALUES[0][0],
                CHANNEL_VALUES[0][1], TYPE_VALUES[0], ENABLED_VALUES[0]);
        CellBroadcastIdRange range2 = new CellBroadcastIdRange(CHANNEL_VALUES[0][0],
                CHANNEL_VALUES[0][1], TYPE_VALUES[0], ENABLED_VALUES[0]);

        assertTrue(!range1.equals(null));
        assertTrue(range1.equals(range2));

        range2 = new CellBroadcastIdRange(CHANNEL_VALUES[1][0], CHANNEL_VALUES[1][1],
                TYPE_VALUES[0], ENABLED_VALUES[0]);

        assertTrue(!range1.equals(range2));

        range2 = new CellBroadcastIdRange(CHANNEL_VALUES[0][0], CHANNEL_VALUES[0][1],
                TYPE_VALUES[1], ENABLED_VALUES[0]);

        assertTrue(!range1.equals(range2));

        range2 = new CellBroadcastIdRange(CHANNEL_VALUES[0][0], CHANNEL_VALUES[0][1],
                TYPE_VALUES[0], ENABLED_VALUES[1]);

        assertTrue(!range1.equals(range2));
    }

    @Test
    @ApiTest(apis = {
            "android.telephony.CellBroadcastIdRange#CellBroadcastIdRange",
            "android.telephony.CellBroadcastIdRange#describeContents"})
    public void testDescribeContents() {
        for (int i = 0; i < CHANNEL_VALUES.length; i++) {
            for (int j = 0; j < TYPE_VALUES.length; j++) {
                for (int k = 0; k < ENABLED_VALUES.length; k++) {
                    CellBroadcastIdRange range = new CellBroadcastIdRange(CHANNEL_VALUES[i][0],
                            CHANNEL_VALUES[i][1], TYPE_VALUES[j], ENABLED_VALUES[k]);
                    assertEquals(0, range.describeContents());
                }
            }
        }
    }

    @Test
    @ApiTest(apis = {
            "android.telephony.CellBroadcastIdRange#CellBroadcastIdRange",
            "android.telephony.CellBroadcastIdRange#writeToParcel",
            "android.telephony.CellBroadcastIdRange#CREATOR"})
    public void testParcelUnparcel() {
        for (int i = 0; i < CHANNEL_VALUES.length; i++) {
            for (int j = 0; j < TYPE_VALUES.length; j++) {
                for (int k = 0; k < ENABLED_VALUES.length; k++) {
                    CellBroadcastIdRange range = new CellBroadcastIdRange(CHANNEL_VALUES[i][0],
                            CHANNEL_VALUES[i][1], TYPE_VALUES[j], ENABLED_VALUES[k]);

                    Parcel parcel = Parcel.obtain();
                    range.writeToParcel(parcel, 0);
                    parcel.setDataPosition(0);
                    CellBroadcastIdRange range2 =
                            CellBroadcastIdRange.CREATOR.createFromParcel(parcel);
                    parcel.recycle();

                    assertEquals(range, range2);
                }
            }
        }
    }
}
