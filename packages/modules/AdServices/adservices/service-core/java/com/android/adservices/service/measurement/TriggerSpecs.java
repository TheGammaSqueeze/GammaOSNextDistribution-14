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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Pair;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.noising.Combinatorics;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A class wrapper for the trigger specification from the input argument during source registration
 */
public class TriggerSpecs {
    private final TriggerSpec[] mTriggerSpecs;
    private int mMaxEventLevelReports;
    private PrivacyComputationParams mPrivacyParams;
    private final Map<UnsignedLong, Integer> mTriggerDataToTriggerSpecIndexMap = new HashMap<>();
    // Reference to a list that is a property of the Source object.
    private List<AttributedTrigger> mAttributedTriggersRef;

    // Trigger data magnitude is restricted to 32 bits.
    public static final UnsignedLong MAX_TRIGGER_DATA_VALUE = new UnsignedLong((1L << 32) - 1L);
    // Max bucket threshold is 32 bits.
    public static final long MAX_BUCKET_THRESHOLD = (1L << 32) - 1L;

    /** The JSON keys for flexible event report API input */
    public interface FlexEventReportJsonKeys {
        String VALUE = "value";
        String PRIORITY = "priority";
        String TRIGGER_TIME = "trigger_time";
        String TRIGGER_DATA = "trigger_data";
        String FLIP_PROBABILITY = "flip_probability";
        String END_TIMES = "end_times";
        String START_TIME = "start_time";
        String SUMMARY_WINDOW_OPERATOR = "summary_window_operator";
        String EVENT_REPORT_WINDOWS = "event_report_windows";
        String SUMMARY_BUCKETS = "summary_buckets";
    }

    public TriggerSpecs(
            String triggerSpecsString,
            String maxEventLevelReports,
            Source source,
            String privacyParametersString)
            throws JSONException {
        this(
                triggerSpecsString,
                Integer.parseInt(maxEventLevelReports),
                source,
                privacyParametersString);
    }

    /**
     * This constructor is called during the attribution process. Current trigger status will be
     * read and process to determine the outcome of incoming trigger.
     *
     * @param triggerSpecsString input trigger specs from ad tech
     * @param maxEventLevelReports max event level reports from ad tech
     * @param source the source associated with this trigger specification
     * @param privacyParametersString computed privacy parameters
     * @throws JSONException JSON exception
     */
    public TriggerSpecs(
            String triggerSpecsString,
            int maxEventLevelReports,
            @Nullable Source source,
            String privacyParametersString)
            throws JSONException {
        if (triggerSpecsString == null || triggerSpecsString.isEmpty()) {
            throw new JSONException("the source is not registered as flexible event report API");
        }
        JSONArray triggerSpecs = new JSONArray(triggerSpecsString);
        mTriggerSpecs = new TriggerSpec[triggerSpecs.length()];
        for (int i = 0; i < triggerSpecs.length(); i++) {
            mTriggerSpecs[i] = new TriggerSpec.Builder(triggerSpecs.getJSONObject(i)).build();
            for (UnsignedLong triggerData : mTriggerSpecs[i].getTriggerData()) {
                mTriggerDataToTriggerSpecIndexMap.put(triggerData, i);
            }
        }

        mMaxEventLevelReports = maxEventLevelReports;

        if (source != null) {
            mAttributedTriggersRef = source.getAttributedTriggers();
        }

        mPrivacyParams = new PrivacyComputationParams(privacyParametersString);
    }

    /**
     * This constructor is called during the source registration process.
     *
     * @param triggerSpecs trigger specs from ad tech
     * @param maxEventLevelReports max event level reports from ad tech
     * @param source the {@code Source} associated with this trigger specification
     */
    public TriggerSpecs(@NonNull TriggerSpec[] triggerSpecs, int maxEventLevelReports,
            Source source) {
        mTriggerSpecs = triggerSpecs;
        mMaxEventLevelReports = maxEventLevelReports;
        if (source != null) {
            mAttributedTriggersRef = source.getAttributedTriggers();
        }
        for (int i = 0; i < triggerSpecs.length; i++) {
            for (UnsignedLong triggerData : triggerSpecs[i].getTriggerData()) {
                mTriggerDataToTriggerSpecIndexMap.put(triggerData, i);
            }
        }
    }

    /**
     * @return the information gain
     */
    public double getInformationGain(Source source, Flags flags) {
        if (mPrivacyParams == null) {
            buildPrivacyParameters(source, flags);
        }
        return mPrivacyParams.getInformationGain();
    }

    /** @return the probability to use fake report */
    public double getFlipProbability(Source source, Flags flags) {
        if (mPrivacyParams == null) {
            buildPrivacyParameters(source, flags);
        }
        return mPrivacyParams.getFlipProbability();
    }

    /**
     * Get the parameters for the privacy computation. 1st element: total report cap, an array with
     * 1 element is used to store the integer; 2nd element: number of windows per trigger data type;
     * 3rd element: number of report cap per trigger data type.
     *
     * @return the parameters to computer number of states and fake report
     */
    public int[][] getPrivacyParamsForComputation() {
        // TODO (b/313920181): build privacy params in case null.
        int[][] params = new int[3][];
        params[0] = new int[] {mMaxEventLevelReports};
        params[1] = mPrivacyParams.getPerTypeNumWindowList();
        params[2] = mPrivacyParams.getPerTypeCapList();
        return params;
    }

    /**
     * getter method for mTriggerSpecs
     *
     * @return the array of TriggerSpec
     */
    public TriggerSpec[] getTriggerSpecs() {
        return mTriggerSpecs;
    }

    /**
     * @return Max number of reports)
     */
    public int getMaxReports() {
        return mMaxEventLevelReports;
    }

    /**
     * Get the trigger datum given a trigger datum index. In the flexible event API, the trigger
     * data are distributed uniquely among the trigger spec objects.
     *
     * @param triggerDataIndex The index of the triggerData
     * @return the trigger data
     */
    public UnsignedLong getTriggerDataFromIndex(int triggerDataIndex) {
        for (TriggerSpec triggerSpec : mTriggerSpecs) {
            int prevTriggerDataIndex = triggerDataIndex;
            triggerDataIndex -= triggerSpec.getTriggerData().size();
            if (triggerDataIndex < 0) {
                return triggerSpec.getTriggerData().get(prevTriggerDataIndex);
            }
        }
        // will not reach here
        return null;
    }

    /**
     * @param index the index of the summary bucket
     * @param summaryBuckets the summary bucket
     * @return return single summary bucket of the index
     */
    public static Pair<Long, Long> getSummaryBucketFromIndex(
            int index, @NonNull List<Long> summaryBuckets) {
        return new Pair<>(
                summaryBuckets.get(index),
                index < summaryBuckets.size() - 1
                        ? summaryBuckets.get(index + 1) - 1
                        : MAX_BUCKET_THRESHOLD);
    }

   /**
     * @param triggerData the trigger data
     * @return the summary bucket configured for the trigger data
     */
    public List<Long> getSummaryBucketsForTriggerData(UnsignedLong triggerData) {
        int index = mTriggerDataToTriggerSpecIndexMap.get(triggerData);
        return mTriggerSpecs[index].getSummaryBuckets();
    }

    /**
     * @param triggerData the trigger data
     * @return the summary operator type configured for the trigger data
     */
    public TriggerSpec.SummaryOperatorType getSummaryOperatorType(UnsignedLong triggerData) {
        int index = mTriggerDataToTriggerSpecIndexMap.get(triggerData);
        return mTriggerSpecs[index].getSummaryWindowOperator();
    }

    /**
     * @param triggerData the trigger data
     * @return the event report windows start time configured for the trigger data
     */
    public Long findReportingStartTimeForTriggerData(UnsignedLong triggerData) {
        int index = mTriggerDataToTriggerSpecIndexMap.get(triggerData);
        return mTriggerSpecs[index].getEventReportWindowsStart();
    }

    /**
     * @param triggerData the trigger data
     * @return the event report window ends configured for the trigger data
     */
    public List<Long> findReportingEndTimesForTriggerData(UnsignedLong triggerData) {
        int index = mTriggerDataToTriggerSpecIndexMap.get(triggerData);
        return mTriggerSpecs[index].getEventReportWindowsEnd();
    }

    /**
     * Prepares structures for flex attribution handling.
     *
     * @param sourceEventReports delivered and pending reports for the source
     * @param triggerTime trigger time
     * @param reportsToDelete a list that the method will populate with reports to delete
     * @param triggerDataToBucketIndexMap a map that the method will populate with the current
     * bucket index per trigger data after considering delivered reports
     */
    public void prepareFlexAttribution(
            List<EventReport> sourceEventReports,
            long triggerTime,
            List<EventReport> reportsToDelete,
            Map<UnsignedLong, Integer> triggerDataToBucketIndexMap) {
        // Completed reports represent an ordered sequence of summary buckets.
        sourceEventReports.sort(
                Comparator.comparing(EventReport::getTriggerData)
                        .thenComparing(Comparator.comparingLong(
                                eventReport -> eventReport.getTriggerSummaryBucket().first)));

        // Iterate over completed reports and store for each attributed trigger its contribution.
        // Also record the list of pending reports to delete and recreate an updated sequence for.
        for (EventReport eventReport : sourceEventReports) {
            // Delete pending reports since we may have different ones based on new trigger priority
            // ordering.
            if (eventReport.getReportTime() >= triggerTime) {
                reportsToDelete.add(eventReport);
                continue;
            }

            UnsignedLong triggerData = eventReport.getTriggerData();

            // Event reports are sorted by summary bucket so this event report must be either for
            // the first or the next bucket. The index for the map is one higher, corresponding to
            // the current bucket we'll start with for attribution.
            triggerDataToBucketIndexMap.merge(triggerData, 1, (oldValue, value) -> oldValue + 1);

            List<Long> buckets = getSummaryBucketsForTriggerData(triggerData);
            int bucketIndex = triggerDataToBucketIndexMap.get(triggerData) - 1;
            long prevBucket = bucketIndex == 0 ? 0L : buckets.get(bucketIndex - 1);
            long bucketSize = buckets.get(bucketIndex) - prevBucket;

            for (AttributedTrigger attributedTrigger : mAttributedTriggersRef) {
                bucketSize -= restoreTriggerContributionAndGetBucketDelta(
                        attributedTrigger, eventReport, bucketSize);
                // We've covered the triggers that contributed to this report so we can exit the
                // iteration.
                if (bucketSize == 0L) {
                    break;
                }
            }
        }
    }

    private long restoreTriggerContributionAndGetBucketDelta(
            AttributedTrigger attributedTrigger, EventReport eventReport, long bucketSize) {
        // Skip this trigger since if it did not contribute to completed reports or if trigger data
        // do not match.
        if (attributedTrigger.getTriggerTime() >= eventReport.getReportTime()
                || !Objects.equals(attributedTrigger.getTriggerData(),
                        eventReport.getTriggerData())) {
            return 0L;
        }

        // Value sum operator.
        if (getSummaryOperatorType(eventReport.getTriggerData())
                == TriggerSpec.SummaryOperatorType.VALUE_SUM) {
            // The trigger can cover the full bucket size of the completed report.
            if (attributedTrigger.remainingValue() >= bucketSize) {
                attributedTrigger.addContribution(bucketSize);
                return bucketSize;
            // The trigger only covers some of the report's bucket.
            } else {
                long diff = attributedTrigger.remainingValue();
                attributedTrigger.addContribution(diff);
                return diff;
            }
        // Count operator for a trigger that we haven't counted yet.
        } else if (attributedTrigger.getContribution() == 0L) {
            attributedTrigger.addContribution(1L);
            return 1L;
        }

        return 0L;
    }

    private void buildPrivacyParameters(Source source, Flags flags) {
        mPrivacyParams = new PrivacyComputationParams(source, flags);
    }

    private int[] computePerTypeNumWindowList() {
        List<Integer> list = new ArrayList<>();
        for (TriggerSpec triggerSpec : mTriggerSpecs) {
            for (UnsignedLong ignored : triggerSpec.getTriggerData()) {
                list.add(triggerSpec.getEventReportWindowsEnd().size());
            }
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    private int[] computePerTypeCapList() {
        List<Integer> list = new ArrayList<>();
        for (TriggerSpec triggerSpec : mTriggerSpecs) {
            for (UnsignedLong ignored : triggerSpec.getTriggerData()) {
                list.add(triggerSpec.getSummaryBuckets().size());
            }
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TriggerSpecs)) {
            return false;
        }
        TriggerSpecs t = (TriggerSpecs) obj;

        return mMaxEventLevelReports == t.mMaxEventLevelReports
                && Objects.equals(mAttributedTriggersRef, t.mAttributedTriggersRef)
                && Arrays.equals(mTriggerSpecs, t.mTriggerSpecs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Arrays.hashCode(mTriggerSpecs),
                mMaxEventLevelReports,
                mAttributedTriggersRef);
    }

    /**
     * Encode the privacy reporting parameters to JSON
     *
     * @return json object encode this class
     */
    public String encodeToJson() {
        return encodeToJson(mTriggerSpecs);
    }

    /**
     * Encodes provided {@link TriggerSpec} into {@link JSONArray} string.
     *
     * @param triggerSpecs triggerSpec array to be encoded
     * @return JSON encoded String
     */
    public static String encodeToJson(TriggerSpec[] triggerSpecs) {
        try {
            JSONObject[] triggerSpecsArray = new JSONObject[triggerSpecs.length];
            for (int i = 0; i < triggerSpecs.length; i++) {
                triggerSpecsArray[i] = triggerSpecs[i].encodeJSON();
            }
            return new JSONArray(triggerSpecsArray).toString();
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e("TriggerSpecs::encodeToJson is unable to encode TriggerSpecs");
            return null;
        }
    }

    /**
     * Encode the result of privacy parameters computed based on input parameters to JSON
     *
     * @return String encoded the privacy parameters
     */
    public String encodePrivacyParametersToJsonString() {
        JSONObject json = new JSONObject();
        try {
            json.put(
                    FlexEventReportJsonKeys.FLIP_PROBABILITY,
                    mPrivacyParams.mFlipProbability);
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(
                            "TriggerSpecs::encodePrivacyParametersToJsonString is unable to encode"
                                    + " PrivacyParams to JSON");
            return null;
        }
        return json.toString();
    }

    /**
     * @param triggerData the triggerData to be checked
     * @return whether the triggerData is registered
     */
    public boolean containsTriggerData(UnsignedLong triggerData) {
        return mTriggerDataToTriggerSpecIndexMap.containsKey(triggerData);
    }

    /**
     * @return the trigger data cardinality across all trigger specs
     */
    public int getTriggerDataCardinality() {
        return mTriggerDataToTriggerSpecIndexMap.size();
    }

    @VisibleForTesting
    public List<AttributedTrigger> getAttributedTriggers() {
        return mAttributedTriggersRef;
    }

    private class PrivacyComputationParams {
        private final int[] mPerTypeNumWindowList;
        private final int[] mPerTypeCapList;
        private final long mNumStates;
        private final double mFlipProbability;
        private final double mInformationGain;

        PrivacyComputationParams(Source source, Flags flags) {
            mPerTypeNumWindowList = computePerTypeNumWindowList();
            mPerTypeCapList = computePerTypeCapList();

            // Doubling the window cap for each trigger data type correlates with counting report
            // states that treat having a web destination as different from an app destination.
            int destinationMultiplier = source.getDestinationTypeMultiplier(flags);
            int[] updatedPerTypeNumWindowList = new int[mPerTypeNumWindowList.length];
            for (int i = 0; i < mPerTypeNumWindowList.length; i++) {
                updatedPerTypeNumWindowList[i] = mPerTypeNumWindowList[i] * destinationMultiplier;
            }

            // compute number of state and other privacy parameters
            mNumStates =
                    Combinatorics.getNumStatesFlexApi(
                            mMaxEventLevelReports, updatedPerTypeNumWindowList, mPerTypeCapList);
            mFlipProbability = Combinatorics.getFlipProbability(mNumStates);
            mInformationGain = Combinatorics.getInformationGain(mNumStates, mFlipProbability);
        }

        PrivacyComputationParams(String inputLine) throws JSONException {
            JSONObject json = new JSONObject(inputLine);
            mFlipProbability =
                    json.getDouble(FlexEventReportJsonKeys.FLIP_PROBABILITY);
            mPerTypeNumWindowList = null;
            mPerTypeCapList = null;
            mNumStates = -1;
            mInformationGain = -1.0;
        }

        private double getFlipProbability() {
            return mFlipProbability;
        }

        private double getInformationGain() {
            return mInformationGain;
        }

        private int[] getPerTypeNumWindowList() {
            return mPerTypeNumWindowList;
        }

        private int[] getPerTypeCapList() {
            return mPerTypeCapList;
        }
    }
}
