/*
 * Copyright 2023 The Android Open Source Project
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
package com.android.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for SignedLongLong.java
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SignedLongLongTest {

    @Test
    public void compareTo_sameValue_returnsZero() {
        long mostSigBits = 1352;
        long leastSigBits = 53423;

        SignedLongLong value = new SignedLongLong(mostSigBits, leastSigBits);
        SignedLongLong sameValue = new SignedLongLong(mostSigBits, leastSigBits);

        assertThat(value.compareTo(sameValue)).isEqualTo(0);
    }

    @Test
    public void compareTo_biggerLeastSigBits_returnsMinusOne() {
        long commonMostSigBits = 12345;
        long leastSigBits = 1;
        SignedLongLong value = new SignedLongLong(leastSigBits, commonMostSigBits);

        long biggerLeastSigBits = 2;
        SignedLongLong biggerValue = new SignedLongLong(biggerLeastSigBits, commonMostSigBits);

        assertThat(value.compareTo(biggerValue)).isEqualTo(-1);
    }

    @Test
    public void compareTo_smallerLeastSigBits_returnsOne() {
        long commonMostSigBits = 12345;
        long leastSigBits = 2;
        SignedLongLong value = new SignedLongLong(leastSigBits, commonMostSigBits);

        long smallerLeastSigBits = 1;
        SignedLongLong smallerValue = new SignedLongLong(smallerLeastSigBits, commonMostSigBits);

        assertThat(value.compareTo(smallerValue)).isEqualTo(1);
    }

    @Test
    public void compareTo_biggerMostSigBits_returnsMinusOne() {
        long commonLeastSigBits = 12345;
        long mostSigBits = 1;
        SignedLongLong value = new SignedLongLong(commonLeastSigBits, mostSigBits);

        long biggerMostSigBits = 2;
        SignedLongLong biggerValue = new SignedLongLong(commonLeastSigBits, biggerMostSigBits);

        assertThat(value.compareTo(biggerValue)).isEqualTo(-1);
    }

    @Test
    public void compareTo_smallerMostSigBits_returnsOne() {
        long commonLeastSigBits = 12345;
        long mostSigBits = 2;
        SignedLongLong value = new SignedLongLong(commonLeastSigBits, mostSigBits);

        long smallerMostSigBits = 1;
        SignedLongLong smallerValue = new SignedLongLong(commonLeastSigBits, smallerMostSigBits);

        assertThat(value.compareTo(smallerValue)).isEqualTo(1);
    }

    @Test
    public void toString_RepresentedAsHexValues() {
        SignedLongLong value = new SignedLongLong(2, 11);

        assertThat(value.toString()).isEqualTo("B0000000000000002");
    }

    @SuppressWarnings("EqualsIncompatibleType")
    @Test
    public void equals_variousCases() {
        SignedLongLong value = new SignedLongLong(1, 2);

        assertThat(value.equals(value)).isTrue();
        assertThat(value.equals(null)).isFalse();
        assertThat(value.equals("a random string")).isFalse();
        assertThat(value.equals(new SignedLongLong(1, 1))).isFalse();
        assertThat(value.equals(new SignedLongLong(2, 2))).isFalse();
        assertThat(value.equals(new SignedLongLong(1, 2))).isTrue();
    }

    @Test
    public void fromString_whenStringIsNull_throwsNPE() {
        assertThrows(NullPointerException.class, () -> SignedLongLong.fromString(null));
    }

    @Test
    public void fromString_whenLengthIsInvalid_throwsNumberFormatException() {
        assertThrows(NumberFormatException.class, () -> SignedLongLong.fromString(""));
    }

    @Test
    public void fromString_whenLengthIsNotGreaterThan16() throws Exception {
        String strValue = "1";

        assertThat(SignedLongLong.fromString(strValue))
                .isEqualTo(new SignedLongLong(1, 0));
    }

    @Test
    public void fromString_whenLengthIsGreaterThan16() throws Exception {
        String strValue = "B0000000000000002";

        assertThat(SignedLongLong.fromString(strValue))
                .isEqualTo(new SignedLongLong(2, 11));
    }
}
