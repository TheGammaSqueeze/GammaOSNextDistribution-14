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

package com.android.adservices.service.measurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashSet;

public class AttributedTriggerTest {
    private static final String TRIGGER_ID1 = "triggerId1";
    private static final String TRIGGER_ID2 = "triggerId2";
    private static final long PRIORITY1 = 3L;
    private static final long PRIORITY2 = 4L;
    private static final UnsignedLong TRIGGER_DATA1 = new UnsignedLong("556");
    private static final UnsignedLong TRIGGER_DATA2 = new UnsignedLong("446");
    private static final long VALUE1 = 5L;
    private static final long VALUE2 = 66L;
    private static final long TRIGGER_TIME1 = 1689564817000L;
    private static final long TRIGGER_TIME2 = 1689564817010L;
    private static final UnsignedLong DEDUP_KEY1 = new UnsignedLong("453");
    private static final UnsignedLong DEDUP_KEY2 = new UnsignedLong("357");
    private static final UnsignedLong DEBUG_KEY1 = new UnsignedLong("980");
    private static final UnsignedLong DEBUG_KEY2 = new UnsignedLong("812");
    private static final boolean HAS_SOURCE_DEBUG_KEY1 = true;
    private static final boolean HAS_SOURCE_DEBUG_KEY2 = false;
    private static final long CONTRIBUTION = 50L;

    @Test
    public void equals_pass() {
        assertEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1));
    }

    @Test
    public void equals_fail() {
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1),
                new AttributedTrigger(
                        TRIGGER_ID2,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEDUP_KEY1,
                        HAS_SOURCE_DEBUG_KEY1));
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY2,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1));
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA2,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1));
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE2,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1));
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME2,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1));
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY2,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1));
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY2,
                        HAS_SOURCE_DEBUG_KEY1));
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY2));
    }

    @Test
    public void hashCode_pass() {
        assertEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode(),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode());
    }

    @Test
    public void hashCode_fail() {
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode(),
                new AttributedTrigger(
                        TRIGGER_ID2,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode());
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode(),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY2,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode());
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode(),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA2,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode());
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode(),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE2,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode());
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode(),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME2,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode());
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode(),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY2,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode());
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode(),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY2,
                        HAS_SOURCE_DEBUG_KEY1).hashCode());
        assertNotEquals(
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1).hashCode(),
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY2).hashCode());
    }

    @Test
    public void getters() {
        AttributedTrigger attributedTrigger =
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1);

        attributedTrigger.addContribution(CONTRIBUTION);
        assertEquals(CONTRIBUTION, attributedTrigger.getContribution());
        assertEquals(TRIGGER_ID1, attributedTrigger.getTriggerId());
        assertEquals(PRIORITY1, attributedTrigger.getPriority());
        assertEquals(TRIGGER_DATA1, attributedTrigger.getTriggerData());
        assertEquals(VALUE1, attributedTrigger.getValue());
        assertEquals(TRIGGER_TIME1, attributedTrigger.getTriggerTime());
        assertEquals(DEDUP_KEY1, attributedTrigger.getDedupKey());
        assertEquals(DEBUG_KEY1, attributedTrigger.getDebugKey());
        assertEquals(HAS_SOURCE_DEBUG_KEY1, attributedTrigger.hasSourceDebugKey());
    }

    @Test
    public void remainingValue() {
        AttributedTrigger attributedTrigger1 =
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        TRIGGER_DATA1,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1);

        attributedTrigger1.addContribution(CONTRIBUTION);
        assertEquals(-45L, attributedTrigger1.remainingValue());

        AttributedTrigger attributedTrigger2 =
                new AttributedTrigger(
                        TRIGGER_ID2,
                        PRIORITY2,
                        TRIGGER_DATA2,
                        VALUE2,
                        TRIGGER_TIME2,
                        DEDUP_KEY2,
                        DEBUG_KEY2,
                        HAS_SOURCE_DEBUG_KEY2);

        attributedTrigger2.addContribution(CONTRIBUTION);
        assertEquals(16L, attributedTrigger2.remainingValue());
    }

    @Test
    public void encodeToJson_jsonConstructor_equalAfterReconstructing() throws JSONException {
        JSONObject triggerObj = new JSONObject();
        triggerObj.put("trigger_id", TRIGGER_ID1);
        triggerObj.put("trigger_data", TRIGGER_DATA1);
        triggerObj.put("dedup_key", DEDUP_KEY1.toString());

        AttributedTrigger triggerFromJson = new AttributedTrigger(triggerObj);
        JSONObject encodedObj = triggerFromJson.encodeToJson();

        // The original object used in the constructor is similar to the encoded object
        HashSet<String> keys = new HashSet(triggerObj.keySet());
        keys.addAll(encodedObj.keySet());
        for (String key : keys) {
            assertEquals(triggerObj.get(key).toString(), encodedObj.get(key).toString());
        }

        // The AttributedTrigger constructed from the original object is equal to the
        // AttributedTrigger constructed from the encoded object.
        assertEquals(triggerFromJson, new AttributedTrigger(encodedObj));
    }

    @Test
    public void encodeToJsonFlexApi_jsonConstructor_equalAfterReconstructing()
            throws JSONException {
        JSONObject triggerObj = new JSONObject();
        triggerObj.put("trigger_id", TRIGGER_ID1);
        triggerObj.put("priority", PRIORITY1);
        triggerObj.put("trigger_data", TRIGGER_DATA1.toString());
        triggerObj.put("value", VALUE1);
        triggerObj.put("trigger_time", TRIGGER_TIME1);
        triggerObj.put("dedup_key", DEDUP_KEY1.toString());
        triggerObj.put("debug_key", DEBUG_KEY1.toString());
        triggerObj.put("has_source_debug_key", HAS_SOURCE_DEBUG_KEY1);

        AttributedTrigger triggerFromJson = new AttributedTrigger(triggerObj);
        JSONObject encodedObj = triggerFromJson.encodeToJsonFlexApi();

        // The original object used in the constructor is similar to the encoded object
        HashSet<String> keys = new HashSet(triggerObj.keySet());
        keys.addAll(encodedObj.keySet());
        for (String key : keys) {
            assertEquals(triggerObj.get(key).toString(), encodedObj.get(key).toString());
        }

        // The AttributedTrigger constructed from the original object is equal to the
        // AttributedTrigger constructed from the encoded object.
        assertEquals(triggerFromJson, new AttributedTrigger(encodedObj));
    }

    @Test
    public void encodeToJsonFlexApi_triggerDataNull_throws() {
        AttributedTrigger attributedTrigger =
                new AttributedTrigger(
                        TRIGGER_ID1,
                        PRIORITY1,
                        /* trigger data */ null,
                        VALUE1,
                        TRIGGER_TIME1,
                        DEDUP_KEY1,
                        DEBUG_KEY1,
                        HAS_SOURCE_DEBUG_KEY1);

        assertThrows(
                NullPointerException.class,
                () -> attributedTrigger.encodeToJsonFlexApi());
    }
}
