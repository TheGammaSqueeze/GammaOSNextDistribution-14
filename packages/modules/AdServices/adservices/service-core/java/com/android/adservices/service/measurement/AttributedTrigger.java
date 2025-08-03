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

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/** POJO for attributed trigger.  */
public class AttributedTrigger {
    private final String mTriggerId;
    private final long mPriority;
    private final UnsignedLong mTriggerData;
    private final long mValue;
    private final long mTriggerTime;
    private final UnsignedLong mDedupKey;
    private final UnsignedLong mDebugKey;
    private final Boolean mHasSourceDebugKey;
    private long mContribution;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AttributedTrigger)) {
            return false;
        }
        AttributedTrigger t = (AttributedTrigger) obj;

        return mTriggerId.equals(t.mTriggerId)
                && mPriority == t.mPriority
                && Objects.equals(mTriggerData, t.mTriggerData)
                && mValue == t.mValue
                && mTriggerTime == t.mTriggerTime
                && Objects.equals(mDedupKey, t.mDedupKey)
                && Objects.equals(mDebugKey, t.mDebugKey)
                && Objects.equals(mHasSourceDebugKey, t.mHasSourceDebugKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mTriggerId,
                mPriority,
                mTriggerData,
                mValue,
                mTriggerTime,
                mDedupKey,
                mDebugKey,
                mHasSourceDebugKey);
    }

    public AttributedTrigger(JSONObject json) throws JSONException {
        mTriggerId = json.getString(JsonKeys.TRIGGER_ID);
        if (!json.isNull(TriggerSpecs.FlexEventReportJsonKeys.PRIORITY)) {
            mPriority = json.getLong(TriggerSpecs.FlexEventReportJsonKeys.PRIORITY);
        } else {
            mPriority = 0L;
        }
        if (!json.isNull(TriggerSpecs.FlexEventReportJsonKeys.TRIGGER_DATA)) {
            mTriggerData = new UnsignedLong(
                    json.getString(TriggerSpecs.FlexEventReportJsonKeys.TRIGGER_DATA));
        } else {
            mTriggerData = null;
        }
        if (!json.isNull(TriggerSpecs.FlexEventReportJsonKeys.VALUE)) {
            mValue = json.getLong(TriggerSpecs.FlexEventReportJsonKeys.VALUE);
        } else {
            mValue = 0L;
        }
        if (!json.isNull(TriggerSpecs.FlexEventReportJsonKeys.TRIGGER_TIME)) {
            mTriggerTime = json.getLong(TriggerSpecs.FlexEventReportJsonKeys.TRIGGER_TIME);
        } else {
            mTriggerTime = 0L;
        }
        if (!json.isNull(JsonKeys.DEDUP_KEY)) {
            mDedupKey = new UnsignedLong(
                    json.getString(JsonKeys.DEDUP_KEY));
        } else {
            mDedupKey = null;
        }
        if (!json.isNull(JsonKeys.DEBUG_KEY)) {
            mDebugKey = new UnsignedLong(
                    json.getString(JsonKeys.DEBUG_KEY));
        } else {
            mDebugKey = null;
        }
        if (!json.isNull(JsonKeys.HAS_SOURCE_DEBUG_KEY)) {
            mHasSourceDebugKey = json.getBoolean(JsonKeys.HAS_SOURCE_DEBUG_KEY);
        } else {
            mHasSourceDebugKey = null;
        }
    }

    public AttributedTrigger(
            String triggerId,
            UnsignedLong triggerData,
            UnsignedLong dedupKey) {
        mTriggerId = triggerId;
        mDedupKey = dedupKey;
        mDebugKey = null;
        mHasSourceDebugKey = null;
        mPriority = 0L;
        mTriggerData = triggerData;
        mValue = 0L;
        mTriggerTime = 0L;
    }

    public AttributedTrigger(
            String triggerId,
            long priority,
            UnsignedLong triggerData,
            long value,
            long triggerTime,
            UnsignedLong dedupKey,
            UnsignedLong debugKey,
            boolean hasSourceDebugKey) {
        mTriggerId = triggerId;
        mPriority = priority;
        mTriggerData = triggerData;
        mValue = value;
        mTriggerTime = triggerTime;
        mDedupKey = dedupKey;
        mDebugKey = debugKey;
        mHasSourceDebugKey = hasSourceDebugKey;
    }

    public String getTriggerId() {
        return mTriggerId;
    }

    public long getPriority() {
        return mPriority;
    }

    public UnsignedLong getTriggerData() {
        return mTriggerData;
    }

    public long getValue() {
        return mValue;
    }

    public long getTriggerTime() {
        return mTriggerTime;
    }

    public UnsignedLong getDedupKey() {
        return mDedupKey;
    }

    public UnsignedLong getDebugKey() {
        return mDebugKey;
    }

    /**
     * Returns whether the source debug key was permitted and populated when this trigger was
     * attributed.
     */
    public boolean hasSourceDebugKey() {
        return mHasSourceDebugKey;
    }

    public long getContribution() {
        return mContribution;
    }

    /** Adds to the attributed trigger's contribution. */
    public void addContribution(long contribution) {
        mContribution += contribution;
    }

    /** Returns the remaining value this attributed trigger can contribute to summary buckets. */
    public long remainingValue() {
        return getValue() - getContribution();
    }

    /** Encodes the attributed trigger to a JSONObject */
    public JSONObject encodeToJson() {
        JSONObject json = new JSONObject();
        try {
            json.put(JsonKeys.TRIGGER_ID, mTriggerId);
            json.put(
                    TriggerSpecs.FlexEventReportJsonKeys.TRIGGER_DATA,
                    mTriggerData.toString());
            json.put(JsonKeys.DEDUP_KEY, mDedupKey.toString());
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "AttributedTrigger::encodeToJson cannot encode to JSON");
            return null;
        }
        return json;
    }

    /** Encodes the attributed trigger to a JSONObject */
    public JSONObject encodeToJsonFlexApi() {
        JSONObject json = new JSONObject();
        try {
            json.put(JsonKeys.TRIGGER_ID, mTriggerId);
            json.put(
                    TriggerSpecs.FlexEventReportJsonKeys.TRIGGER_DATA,
                    mTriggerData.toString());
            json.put(TriggerSpecs.FlexEventReportJsonKeys.TRIGGER_TIME, mTriggerTime);
            json.put(TriggerSpecs.FlexEventReportJsonKeys.VALUE, mValue);
            if (mDedupKey != null) {
                json.put(JsonKeys.DEDUP_KEY, mDedupKey.toString());
            }
            if (mDebugKey != null) {
                json.put(JsonKeys.DEBUG_KEY, mDebugKey.toString());
            }
            json.put(JsonKeys.HAS_SOURCE_DEBUG_KEY, mHasSourceDebugKey);
            json.put(TriggerSpecs.FlexEventReportJsonKeys.PRIORITY, mPriority);
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(
                            e,
                            "AttributedTrigger::encodeToJsonFlexApi cannot encode to JSON");
            return null;
        }
        return json;
    }

    private interface JsonKeys {
        String TRIGGER_ID = "trigger_id";
        String DEDUP_KEY = "dedup_key";
        String DEBUG_KEY = "debug_key";
        String HAS_SOURCE_DEBUG_KEY = "has_source_debug_key";
    }
}
