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

package com.android.adservices.service.measurement.aggregation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import androidx.test.filters.SmallTest;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Set;

/** Unit tests for {@link AggregateHistogramContribution} */
@SmallTest
public final class AggregateHistogramContributionTest {

    private AggregateHistogramContribution createExample() {
        return new AggregateHistogramContribution.Builder()
                .setKey(BigInteger.valueOf(100L))
                .setValue(1).build();
    }

    @Test
    public void testCreation() throws Exception {
        AggregateHistogramContribution contribution = createExample();
        assertEquals(100L, contribution.getKey().longValue());
        assertEquals(1, contribution.getValue());
    }

    @Test
    public void testDefaults() throws Exception {
        AggregateHistogramContribution contribution =
                new AggregateHistogramContribution.Builder().build();
        assertEquals(0L, contribution.getKey().longValue());
        assertEquals(0, contribution.getValue());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        final AggregateHistogramContribution contribution1 = createExample();
        final AggregateHistogramContribution contribution2 = createExample();
        final Set<AggregateHistogramContribution> contributionSet1 = Set.of(contribution1);
        final Set<AggregateHistogramContribution> contributionSet2 = Set.of(contribution2);
        assertEquals(contribution1.hashCode(), contribution2.hashCode());
        assertEquals(contribution1, contribution2);
        assertEquals(contributionSet1, contributionSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        final AggregateHistogramContribution contribution1 = createExample();
        final AggregateHistogramContribution contribution2 =
                new AggregateHistogramContribution.Builder()
                        .setKey(BigInteger.valueOf(100L))
                        .setValue(2)
                        .build();
        final Set<AggregateHistogramContribution> contributionSet1 = Set.of(contribution1);
        final Set<AggregateHistogramContribution> contributionSet2 = Set.of(contribution2);
        assertNotEquals(contribution1.hashCode(), contribution2.hashCode());
        assertNotEquals(contribution1, contribution2);
        assertNotEquals(contributionSet1, contributionSet2);
    }

    @Test
    public void storesBucketAsStringInJson() throws JSONException {
        String largeIntegerStr = "334864848949865686038563574111108070905";
        AggregateHistogramContribution contribution =
                new AggregateHistogramContribution.Builder()
                        .setKey(new BigInteger(largeIntegerStr))
                        .setValue(1)
                        .build();
        JSONObject jsonObj = contribution.toJSONObject();
        // Convert the JSONObject to string and use the JSON parser to confirm the large integer is
        // correctly set in a string, rather than a value in scientific notation, which can happen
        // with a large integer type.
        String jsonStr = jsonObj.toString();
        JSONObject parsedJson = new JSONObject(jsonStr);
        assertEquals(
                largeIntegerStr,
                parsedJson.getString(AggregateHistogramContribution.BUCKET));
    }

    @Test
    public void fromJsonObject_createsAggregateHistogramContribution() throws JSONException {
        // Setup
        JSONObject jsonObject = new JSONObject();
        BigInteger key = new BigInteger("1234");
        jsonObject.put(AggregateHistogramContribution.BUCKET, key);
        int value = 54645;
        jsonObject.put(AggregateHistogramContribution.VALUE, value);

        AggregateHistogramContribution expected =
                new AggregateHistogramContribution.Builder().setKey(key).setValue(value).build();

        // Assertion
        AggregateHistogramContribution actual =
                new AggregateHistogramContribution.Builder().fromJsonObject(jsonObject);
        assertEquals(expected, actual);
    }
}
