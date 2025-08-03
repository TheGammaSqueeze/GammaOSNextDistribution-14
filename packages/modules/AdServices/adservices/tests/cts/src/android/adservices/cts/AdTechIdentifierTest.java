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

import android.adservices.common.AdTechIdentifier;
import android.os.Parcel;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class AdTechIdentifierTest {

    private static final String AD_TECH_ID_STRING = "example.com";
    private static final String DIFFERENT_AD_TECH_ID_STRING = "example.org";
    private static final int ARRAY_SIZE = 10;
    private static final int DESCRIBE_CONTENTS_EXPECTATION = 0;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testAdTechIdentifierCreatorArray() {
        Assert.assertArrayEquals(
                new AdTechIdentifier[ARRAY_SIZE], AdTechIdentifier.CREATOR.newArray(ARRAY_SIZE));
    }

    @Test
    public void testAdTechIdentifierParceling() {
        AdTechIdentifier preParcel = AdTechIdentifier.fromString(AD_TECH_ID_STRING);
        Parcel parcel = Parcel.obtain();
        preParcel.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdTechIdentifier postParcel = AdTechIdentifier.CREATOR.createFromParcel(parcel);
        assertEquals(preParcel, postParcel);
    }

    @Test
    public void testAdTechIdentifierDescribeContents() {
        assertEquals(
                DESCRIBE_CONTENTS_EXPECTATION,
                AdTechIdentifier.fromString(AD_TECH_ID_STRING).describeContents());
    }

    @Test
    public void testBuildValidAdTechIdentifierSuccess() {
        AdTechIdentifier validAdTechId = AdTechIdentifier.fromString(AD_TECH_ID_STRING);
        assertEquals(AD_TECH_ID_STRING, validAdTechId.toString());
    }

    @Test(expected = NullPointerException.class)
    public void testBuildAdTechIdentifierNull() {
        AdTechIdentifier.fromString(null);
    }

    @Test
    public void testBuildValidAdTechIdentifierSuccessNoValidation() {
        AdTechIdentifier validAdTechId = AdTechIdentifier.fromString(AD_TECH_ID_STRING, false);
        assertEquals(AD_TECH_ID_STRING, validAdTechId.toString());
    }

    @Test
    public void testAdTechIdentifierEquality() {
        AdTechIdentifier identicalId1 = AdTechIdentifier.fromString(AD_TECH_ID_STRING);
        AdTechIdentifier identicalId2 = AdTechIdentifier.fromString(AD_TECH_ID_STRING);
        AdTechIdentifier differentId = AdTechIdentifier.fromString(DIFFERENT_AD_TECH_ID_STRING);
        assertEquals(identicalId1, identicalId2);
        assertNotEquals(identicalId1, differentId);
        assertNotEquals(identicalId2, differentId);
    }

    @Test
    public void testAdTechIdentifierHashCode() {
        AdTechIdentifier identicalId1 = AdTechIdentifier.fromString(AD_TECH_ID_STRING);
        AdTechIdentifier identicalId2 = AdTechIdentifier.fromString(AD_TECH_ID_STRING);
        AdTechIdentifier differentId = AdTechIdentifier.fromString(DIFFERENT_AD_TECH_ID_STRING);
        assertEquals(identicalId1.hashCode(), identicalId2.hashCode());
        assertNotEquals(identicalId1.hashCode(), differentId.hashCode());
        assertNotEquals(identicalId2.hashCode(), differentId.hashCode());
    }

    @Test
    public void testAdTechIdentifierToString() {
        AdTechIdentifier validAdTechId = AdTechIdentifier.fromString(AD_TECH_ID_STRING);
        assertEquals(AD_TECH_ID_STRING, validAdTechId.toString());
    }
}
