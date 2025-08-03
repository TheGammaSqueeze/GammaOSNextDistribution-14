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

import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * A class wrapper for the trigger specification from the input argument during source registration
 */
public class TriggerSpec {
    private List<UnsignedLong> mTriggerData;
    private Long mEventReportWindowsStart;
    private List<Long> mEventReportWindowsEnd;
    private SummaryOperatorType mSummaryWindowOperator;
    private List<Long> mSummaryBuckets;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TriggerSpec)) {
            return false;
        }
        TriggerSpec t = (TriggerSpec) obj;
        return Objects.equals(mTriggerData, t.mTriggerData)
                && mEventReportWindowsStart.equals(t.mEventReportWindowsStart)
                && mEventReportWindowsEnd.equals(t.mEventReportWindowsEnd)
                && mSummaryWindowOperator == t.mSummaryWindowOperator
                && mSummaryBuckets.equals(t.mSummaryBuckets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mTriggerData,
                mEventReportWindowsStart,
                mEventReportWindowsEnd,
                mSummaryWindowOperator,
                mSummaryBuckets);
    }

    /**
     * @return Trigger Data
     */
    public List<UnsignedLong> getTriggerData() {
        return mTriggerData;
    }

    /**
     * @return Event Report Windows Start
     */
    public Long getEventReportWindowsStart() {
        return mEventReportWindowsStart;
    }

    /** @return Event Report Windows End */
    public List<Long> getEventReportWindowsEnd() {
        return mEventReportWindowsEnd;
    }

    /** @return Summary Window Operator */
    public SummaryOperatorType getSummaryWindowOperator() {
        return mSummaryWindowOperator;
    }

    /**
     * @return Summary Bucket
     */
    public List<Long> getSummaryBuckets() {
        return mSummaryBuckets;
    }

    /**
     * Encode the parameter to JSON
     *
     * @return json object encode this class
     */
    public JSONObject encodeJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(
                "trigger_data",
                new JSONArray(
                        mTriggerData.stream()
                                .map(UnsignedLong::toString)
                                .collect(Collectors.toList())));
        JSONObject windows = new JSONObject();
        windows.put("start_time", mEventReportWindowsStart);
        windows.put("end_times", new JSONArray(mEventReportWindowsEnd));
        json.put("event_report_windows", windows);
        json.put("summary_window_operator", mSummaryWindowOperator.name().toLowerCase());
        json.put("summary_buckets", new JSONArray(mSummaryBuckets));
        return json;
    }

    /** Util function to check if the provided list values are in strictly increasing order. */
    public static <T extends Comparable<T>> boolean isStrictIncreasing(List<T> list) {
        if (list.size() < 2) {
            return true;
        }
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i).compareTo(list.get(i - 1)) <= 0) {
                return false;
            }
        }
        return true;
    }

    /** The choice of the summary operator with the reporting window */
    public enum SummaryOperatorType {
        COUNT,
        VALUE_SUM
    }

    private static List<Long> getLongListFromJSON(JSONObject json, String key)
            throws JSONException {
        return getLongListFromJSON(json.getJSONArray(key));
    }

    /**
     * Parses long JSONArray into List<Long>
     *
     * @param jsonArray the JSON Array
     * @return the parsed List<Long>
     */
    public static List<Long> getLongListFromJSON(JSONArray jsonArray) throws JSONException {
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            result.add(jsonArray.getLong(i));
        }
        return result;
    }

    private static List<UnsignedLong> getTriggerDataArrayFromJSON(JSONObject json, String key)
            throws JSONException {
        return getTriggerDataArrayFromJSON(json.getJSONArray(key));
    }

    /**
     * Parses long JSONArray into List<UnsignedLong>
     *
     * @param jsonArray the JSON Array
     * @return a list of UnsignedLong
     */
    public static List<UnsignedLong> getTriggerDataArrayFromJSON(JSONArray jsonArray)
            throws JSONException {
        List<UnsignedLong> result = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            result.add(new UnsignedLong(jsonArray.getString(i)));
        }
        return result;
    }

    /** */
    public static final class Builder {
        private final TriggerSpec mBuilding;

        public Builder(JSONObject jsonObject) throws JSONException, IllegalArgumentException {
            mBuilding = new TriggerSpec();
            mBuilding.mSummaryWindowOperator = SummaryOperatorType.COUNT;
            mBuilding.mEventReportWindowsStart = 0L;
            mBuilding.mSummaryBuckets = new ArrayList<>();
            mBuilding.mEventReportWindowsEnd = new ArrayList<>();

            this.setTriggerData(
                    getTriggerDataArrayFromJSON(
                            jsonObject, TriggerSpecs.FlexEventReportJsonKeys.TRIGGER_DATA));
            if (!jsonObject.isNull(TriggerSpecs.FlexEventReportJsonKeys.EVENT_REPORT_WINDOWS)) {
                JSONObject jsonReportWindows =
                        jsonObject.getJSONObject(
                                TriggerSpecs.FlexEventReportJsonKeys.EVENT_REPORT_WINDOWS);
                if (!jsonReportWindows.isNull(
                        TriggerSpecs.FlexEventReportJsonKeys.START_TIME)) {
                    this.setEventReportWindowsStart(
                            jsonReportWindows.getLong(
                                    TriggerSpecs.FlexEventReportJsonKeys.START_TIME));
                }
                this.setEventReportWindowsEnd(
                        getLongListFromJSON(
                                jsonReportWindows,
                                TriggerSpecs.FlexEventReportJsonKeys.END_TIMES));
            }

            if (!jsonObject.isNull(
                    TriggerSpecs.FlexEventReportJsonKeys.SUMMARY_WINDOW_OPERATOR)) {
                this.setSummaryWindowOperator(
                        SummaryOperatorType.valueOf(
                                jsonObject
                                        .getString(
                                                TriggerSpecs.FlexEventReportJsonKeys
                                                        .SUMMARY_WINDOW_OPERATOR)
                                        .toUpperCase()));
            }
            if (!jsonObject.isNull(TriggerSpecs.FlexEventReportJsonKeys.SUMMARY_BUCKETS)) {
                this.setSummaryBuckets(
                        getLongListFromJSON(
                                jsonObject,
                                TriggerSpecs.FlexEventReportJsonKeys.SUMMARY_BUCKETS));
            }
        }

        public Builder(
                JSONObject jsonObject,
                long defaultStart,
                List<Long> defaultWindowEnds,
                int maxEventLevelReports) throws JSONException, IllegalArgumentException {
            mBuilding = new TriggerSpec();
            mBuilding.mSummaryWindowOperator = SummaryOperatorType.COUNT;
            mBuilding.mEventReportWindowsStart = defaultStart;
            mBuilding.mSummaryBuckets = new ArrayList<>();
            mBuilding.mEventReportWindowsEnd = defaultWindowEnds;

            this.setTriggerData(
                    getTriggerDataArrayFromJSON(
                            jsonObject, TriggerSpecs.FlexEventReportJsonKeys.TRIGGER_DATA));
            if (!jsonObject.isNull(TriggerSpecs.FlexEventReportJsonKeys.EVENT_REPORT_WINDOWS)) {
                JSONObject jsonReportWindows =
                        jsonObject.getJSONObject(
                                TriggerSpecs.FlexEventReportJsonKeys.EVENT_REPORT_WINDOWS);
                if (!jsonReportWindows.isNull(
                        TriggerSpecs.FlexEventReportJsonKeys.START_TIME)) {
                    this.setEventReportWindowsStart(
                            jsonReportWindows.getLong(
                                    TriggerSpecs.FlexEventReportJsonKeys.START_TIME));
                }

                this.setEventReportWindowsEnd(
                        getLongListFromJSON(
                                jsonReportWindows,
                                TriggerSpecs.FlexEventReportJsonKeys.END_TIMES));
            }

            if (!jsonObject.isNull(
                    TriggerSpecs.FlexEventReportJsonKeys.SUMMARY_WINDOW_OPERATOR)) {
                this.setSummaryWindowOperator(
                        SummaryOperatorType.valueOf(
                                jsonObject
                                        .getString(
                                                TriggerSpecs.FlexEventReportJsonKeys
                                                        .SUMMARY_WINDOW_OPERATOR)
                                        .toUpperCase()));
            }
            if (!jsonObject.isNull(TriggerSpecs.FlexEventReportJsonKeys.SUMMARY_BUCKETS)) {
                List<Long> summaryBuckets =
                        getLongListFromJSON(
                                jsonObject,
                                TriggerSpecs.FlexEventReportJsonKeys.SUMMARY_BUCKETS);
                this.setSummaryBuckets(summaryBuckets.subList(
                        0, Math.min(summaryBuckets.size(), maxEventLevelReports)));
            } else {
                this.setSummaryBuckets(
                        LongStream.range(1, maxEventLevelReports + 1)
                                .boxed()
                                .collect(Collectors.toList()));
            }
        }

        /** See {@link TriggerSpec#getTriggerData()} ()}. */
        public Builder setTriggerData(List<UnsignedLong> triggerData) {
            mBuilding.mTriggerData = triggerData;
            return this;
        }

        /** See {@link TriggerSpec#getEventReportWindowsStart()} ()}. */
        public Builder setEventReportWindowsStart(Long eventReportWindowsStart) {
            mBuilding.mEventReportWindowsStart = eventReportWindowsStart;
            return this;
        }

        /** See {@link TriggerSpec#getEventReportWindowsEnd()} ()}. */
        public Builder setEventReportWindowsEnd(List<Long> eventReportWindowsEnd) {
            mBuilding.mEventReportWindowsEnd = eventReportWindowsEnd;
            return this;
        }

        /** See {@link TriggerSpec#getSummaryWindowOperator()} ()}. */
        public Builder setSummaryWindowOperator(SummaryOperatorType summaryWindowOperator) {
            mBuilding.mSummaryWindowOperator = summaryWindowOperator;
            return this;
        }

        /** See {@link TriggerSpec#getSummaryBucket()} ()}. */
        public Builder setSummaryBuckets(List<Long> summaryBuckets) {
            mBuilding.mSummaryBuckets = summaryBuckets;
            return this;
        }

        /** Build the {@link TriggerSpec}. */
        public TriggerSpec build() {
            return mBuilding;
        }
    }
}
