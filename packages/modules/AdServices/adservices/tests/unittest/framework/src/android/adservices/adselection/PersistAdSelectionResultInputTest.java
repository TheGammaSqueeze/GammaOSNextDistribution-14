/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.adservices.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.os.Parcel;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class PersistAdSelectionResultInputTest {
    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER;
    private static final AdTechIdentifier ANOTHER_SELLER = AdSelectionConfigFixture.SELLER_1;
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final String ANOTHER_CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME_1;
    private static final byte[] AD_SELECTION_RESULT = new byte[] {1, 2, 3, 4};
    private static final byte[] ANOTHER_AD_SELECTION_RESULT = new byte[] {5, 6, 7, 8};
    private static final long AD_SELECTION_ID = 12345;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildPersistAdSelectionResultInput() {
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setSeller(SELLER)
                        .setAdSelectionResult(AD_SELECTION_RESULT)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        assertThat(persistAdSelectionResultInput.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        assertThat(persistAdSelectionResultInput.getSeller()).isEqualTo(SELLER);
        assertThat(persistAdSelectionResultInput.getAdSelectionResult())
                .isEqualTo(AD_SELECTION_RESULT);
        assertThat(persistAdSelectionResultInput.getCallerPackageName())
                .isEqualTo(CALLER_PACKAGE_NAME);
    }

    @Test
    public void testParcelPersistAdSelectionResultInput() {
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setSeller(SELLER)
                        .setAdSelectionResult(AD_SELECTION_RESULT)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        Parcel p = Parcel.obtain();
        persistAdSelectionResultInput.writeToParcel(p, 0);
        p.setDataPosition(0);
        PersistAdSelectionResultInput fromParcel =
                PersistAdSelectionResultInput.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        assertThat(fromParcel.getSeller()).isEqualTo(SELLER);
        assertThat(fromParcel.getAdSelectionResult()).isEqualTo(AD_SELECTION_RESULT);
        assertThat(fromParcel.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
    }

    @Test
    public void testParcelPersistAdSelectionResultInputWithNullValues() {
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setSeller(null)
                        .setAdSelectionResult(null)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        Parcel p = Parcel.obtain();
        persistAdSelectionResultInput.writeToParcel(p, 0);
        p.setDataPosition(0);
        PersistAdSelectionResultInput fromParcel =
                PersistAdSelectionResultInput.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        assertThat(fromParcel.getSeller()).isNull();
        assertThat(fromParcel.getAdSelectionResult()).isNull();
        assertThat(fromParcel.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
    }

    @Test
    public void testFailsToBuildWithUnsetAdSelectionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new PersistAdSelectionResultInput.Builder()
                            // Not setting AdSelectionId making it null.
                            .setSeller(SELLER)
                            .setAdSelectionResult(AD_SELECTION_RESULT)
                            .setCallerPackageName(CALLER_PACKAGE_NAME)
                            .build();
                });
    }

    @Test
    public void testPersistAdSelectionResultInputWithSameValuesAreEqual() {
        PersistAdSelectionResultInput obj1 =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setSeller(SELLER)
                        .setAdSelectionResult(AD_SELECTION_RESULT)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultInput obj2 =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setSeller(SELLER)
                        .setAdSelectionResult(AD_SELECTION_RESULT)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        assertThat(obj1).isEqualTo(obj2);
    }

    @Test
    public void testPersistAdSelectionResultInputWithDifferentValuesAreNotEqual() {
        PersistAdSelectionResultInput obj1 =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setSeller(SELLER)
                        .setAdSelectionResult(AD_SELECTION_RESULT)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultInput obj2 =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setSeller(SELLER)
                        .setAdSelectionResult(ANOTHER_AD_SELECTION_RESULT)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        assertThat(obj1).isNotEqualTo(obj2);
    }

    @Test
    public void testPersistAdSelectionResultInputDescribeContents() {
        PersistAdSelectionResultInput obj =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setSeller(SELLER)
                        .setAdSelectionResult(AD_SELECTION_RESULT)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        assertEquals(0, obj.describeContents());
    }

    @Test
    public void testEqualPersistAdSelectionResultInputsHaveSameHashCode() {
        PersistAdSelectionResultInput obj1 =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setSeller(SELLER)
                        .setAdSelectionResult(AD_SELECTION_RESULT)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultInput obj2 =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setSeller(SELLER)
                        .setAdSelectionResult(AD_SELECTION_RESULT)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        CommonFixture.assertHaveSameHashCode(obj1, obj2);
    }

    @Test
    public void testNotEqualPersistAdSelectionResultInputsHaveDifferentHashCodes() {
        PersistAdSelectionResultInput obj1 =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setSeller(SELLER)
                        .setAdSelectionResult(AD_SELECTION_RESULT)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultInput obj2 =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setSeller(ANOTHER_SELLER)
                        .setAdSelectionResult(AD_SELECTION_RESULT)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultInput obj3 =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setSeller(SELLER)
                        .setAdSelectionResult(ANOTHER_AD_SELECTION_RESULT)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultInput obj4 =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setSeller(SELLER)
                        .setAdSelectionResult(AD_SELECTION_RESULT)
                        .setCallerPackageName(ANOTHER_CALLER_PACKAGE_NAME)
                        .build();

        CommonFixture.assertDifferentHashCode(obj1, obj2, obj3, obj4);
    }

    @Test
    public void testMutabilityForAdSelectionData() {
        byte originalValue = 1;
        byte[] adSelectionResultData = new byte[] {originalValue};
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setSeller(SELLER)
                        .setAdSelectionResult(adSelectionResultData)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        assertThat(persistAdSelectionResultInput.getAdSelectionResult())
                .isEqualTo(adSelectionResultData);

        byte newValue = 5;
        adSelectionResultData[0] = newValue;
        assertThat(persistAdSelectionResultInput.getAdSelectionResult()).isNotNull();
        assertThat(persistAdSelectionResultInput.getAdSelectionResult()[0])
                .isEqualTo(originalValue);
    }
}
