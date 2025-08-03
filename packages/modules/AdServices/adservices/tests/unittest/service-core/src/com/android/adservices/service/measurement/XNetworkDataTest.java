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

package com.android.adservices.service.measurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.measurement.XNetworkData.XNetworkDataContract;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Set;

/** Unit tests for {@link XNetworkData} */
@SmallTest
public final class XNetworkDataTest {

    @Test
    public void testCreation() throws Exception {
        XNetworkData xNetworkData = createExample();
        assertEquals(
                12L, (long) xNetworkData.getKeyOffset().map(UnsignedLong::getValue).orElse(0L));
    }

    @Test
    public void creation_withJsonObject_success() throws JSONException {
        // Setup
        JSONObject xNetworkData = new JSONObject();
        UnsignedLong keyOffsetValue = new UnsignedLong(10L);
        xNetworkData.put(XNetworkDataContract.KEY_OFFSET, keyOffsetValue);
        XNetworkData expected = new XNetworkData.Builder().setKeyOffset(keyOffsetValue).build();

        // Execution
        XNetworkData actual = new XNetworkData.Builder(xNetworkData).build();

        // Assertion
        assertEquals(expected, actual);
    }

    @Test
    public void builder_withInvalidOffset_throwsJsonException() throws JSONException {
        // Setup
        JSONObject xNetworkDataJson = new JSONObject();
        xNetworkDataJson.put(XNetworkDataContract.KEY_OFFSET, "INVALID_VALUE");

        // Assertion
        assertThrows(JSONException.class, () -> new XNetworkData.Builder(xNetworkDataJson));
    }

    @Test
    public void testDefaults() throws Exception {
        XNetworkData xNetworkData = new XNetworkData.Builder().build();
        assertFalse(xNetworkData.getKeyOffset().isPresent());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        final XNetworkData network1 = createExample();
        final XNetworkData network2 = createExample();
        final Set<XNetworkData> networkSet1 = Set.of(network1);
        final Set<XNetworkData> networkSet2 = Set.of(network2);
        assertEquals(network1.hashCode(), network2.hashCode());
        assertEquals(network1, network2);
        assertEquals(networkSet1, networkSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        final XNetworkData network1 = createExample();
        final XNetworkData network2 =
                new XNetworkData.Builder().setKeyOffset(new UnsignedLong(13L)).build();
        final Set<XNetworkData> networkSet1 = Set.of(network1);
        final Set<XNetworkData> networkSet2 = Set.of(network2);
        assertNotEquals(network1.hashCode(), network2.hashCode());
        assertNotEquals(network1, network2);
        assertNotEquals(networkSet1, networkSet2);
    }

    private XNetworkData createExample() {
        return new XNetworkData.Builder().setKeyOffset(new UnsignedLong(12L)).build();
    }
}
