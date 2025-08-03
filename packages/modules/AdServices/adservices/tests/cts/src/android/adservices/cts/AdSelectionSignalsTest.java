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

package android.adservices.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.adservices.common.AdSelectionSignals;
import android.os.Parcel;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class AdSelectionSignalsTest {

    private static final String SIGNALS_STRING = "{\"a\":\"b\"}";
    private static final String DIFFERENT_SIGNALS_STRING = "{\"a\":\"c\"}";
    private static final int ARRAY_SIZE = 10;
    private static final int DESCRIBE_CONTENTS_EXPECTATION = 0;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testAdSelectionSignalsGetSizeInBytes() {
        AdSelectionSignals identicalId = AdSelectionSignals.fromString(SIGNALS_STRING);
        assertEquals(identicalId.toString().getBytes().length, SIGNALS_STRING.getBytes().length);
    }

    @Test
    public void testAdSelectionSignalsCreatorArray() {
        Assert.assertArrayEquals(
                new AdSelectionSignals[ARRAY_SIZE],
                AdSelectionSignals.CREATOR.newArray(ARRAY_SIZE));
    }

    @Test
    public void testAdSelectionSignalsParceling() {
        AdSelectionSignals preParcel = AdSelectionSignals.fromString(SIGNALS_STRING);
        Parcel parcel = Parcel.obtain();
        preParcel.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdSelectionSignals postParcel = AdSelectionSignals.CREATOR.createFromParcel(parcel);
        assertEquals(preParcel, postParcel);
    }

    @Test
    public void testAdSelectionSignalsDescribeContents() {
        assertEquals(
                DESCRIBE_CONTENTS_EXPECTATION,
                AdSelectionSignals.fromString(SIGNALS_STRING).describeContents());
    }

    @Test
    public void testBuildValidAdSelectionSignalsSuccess() {
        AdSelectionSignals validSignals = AdSelectionSignals.fromString(SIGNALS_STRING);
        assertEquals(SIGNALS_STRING, validSignals.toString());
    }

    @Test
    public void testBuildValidAdSelectionSignalsSuccessNoValidation() {
        AdSelectionSignals validSignals = AdSelectionSignals.fromString(SIGNALS_STRING, false);
        assertEquals(SIGNALS_STRING, validSignals.toString());
    }

    @Test(expected = NullPointerException.class)
    public void testBuildAdSelectionSignalsNull() {
        AdSelectionSignals.fromString(null);
    }

    @Test
    public void testAdSelectionSignalsEquality() {
        AdSelectionSignals identicalId1 = AdSelectionSignals.fromString(SIGNALS_STRING);
        AdSelectionSignals identicalId2 = AdSelectionSignals.fromString(SIGNALS_STRING);
        AdSelectionSignals differentId = AdSelectionSignals.fromString(DIFFERENT_SIGNALS_STRING);
        assertEquals(identicalId1, identicalId2);
        assertNotEquals(identicalId1, differentId);
        assertNotEquals(identicalId2, differentId);
    }

    @Test
    public void testAdSelectionSignalsHashCode() {
        AdSelectionSignals identicalId1 = AdSelectionSignals.fromString(SIGNALS_STRING);
        AdSelectionSignals identicalId2 = AdSelectionSignals.fromString(SIGNALS_STRING);
        AdSelectionSignals differentId = AdSelectionSignals.fromString(DIFFERENT_SIGNALS_STRING);
        assertEquals(identicalId1.hashCode(), identicalId2.hashCode());
        assertNotEquals(identicalId1.hashCode(), differentId.hashCode());
        assertNotEquals(identicalId2.hashCode(), differentId.hashCode());
    }

    @Test
    public void testAdSelectionSignalsToString() {
        AdSelectionSignals validSignals = AdSelectionSignals.fromString(SIGNALS_STRING);
        assertEquals(SIGNALS_STRING, validSignals.toString());
    }
}
