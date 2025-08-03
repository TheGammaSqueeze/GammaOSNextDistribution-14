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

package com.android.adservices.service.measurement.noising;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyLong;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.TriggerSpec;
import com.android.adservices.service.measurement.TriggerSpecs;
import com.android.adservices.service.measurement.TriggerSpecsUtil;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** Unit tests for ImpressionNoiseUtil class. */
@SuppressWarnings("ParameterName")
public class ImpressionNoiseUtilTest {
    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(ImpressionNoiseUtil.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @FunctionalInterface
    public interface ThreeArgumentConsumer<T1, T2, T3> {
        void apply(T1 t1, T2 t2, T3 t3);
    }

    private final ThreeArgumentConsumer<ImpressionNoiseParams, List<int[]>, Integer>
            mGenerateReportConfigTester =
                    (noiseParams, expectedReports, sequenceIndex) -> {
                        List<int[]> actualReports =
                                ImpressionNoiseUtil.getReportConfigsForSequenceIndex(
                                        noiseParams, sequenceIndex);
                        assertReportEquality(expectedReports, actualReports);
                    };

    private final ThreeArgumentConsumer<ImpressionNoiseParams, List<int[]>, ThreadLocalRandom>
            mStateSelectionTester =
                    (noiseParams, expectedReports, rand) -> {
                        List<int[]> actualReports =
                                ImpressionNoiseUtil.selectRandomStateAndGenerateReportConfigs(
                                        noiseParams, rand);
                        assertReportEquality(expectedReports, actualReports);
                    };

    private interface FourArgumentConsumer<T1, T2, T3, T4> {
        void apply(T1 t1, T2 t2, T3 t3, T4 t4);
    }

    private final FourArgumentConsumer<TriggerSpecs, Integer, List<int[]>, ThreadLocalRandom>
            mStateSelectionTesterFlexEvent =
                    (triggerSpecs, destinationMultiplier, expectedReports, rand) -> {
                        List<int[]> actualReports =
                                ImpressionNoiseUtil
                                        .selectFlexEventReportRandomStateAndGenerateReportConfigs(
                                                triggerSpecs, destinationMultiplier, rand);
                        assertReportEquality(expectedReports, actualReports);
                    };

    private void assertReportEquality(List<int[]> expectedReports, List<int[]> actualReports) {
        assertEquals(expectedReports.size(), actualReports.size());
        for (int i = 0; i < expectedReports.size(); i++) {
            assertArrayEquals(expectedReports.get(i), actualReports.get(i));
        }
    }

    @Test
    public void selectRandomStateAndGenerateReportConfigs_event() {
        // Total states: {nCk ∋ n = 3 (2 * 1 + 1), k = 1} -> 3
        ImpressionNoiseParams noiseParams =
                new ImpressionNoiseParams(
                        /* reportCount= */ 1,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 1);

        ExtendedMockito.doReturn(0L).when(() -> ImpressionNoiseUtil.nextLong(any(), anyLong()));

        mStateSelectionTester.apply(
                /* impressionNoise= */ noiseParams,
                /* expectedReports= */ Collections.emptyList(),
                /* rand= */ ThreadLocalRandom.current()
        );

        ExtendedMockito.doReturn(2L).when(() -> ImpressionNoiseUtil.nextLong(any(), anyLong()));

        mStateSelectionTester.apply(
                /* impressionNoise= */ noiseParams,
                /* expectedReports= */ Collections.singletonList(new int[] {1, 0, 0}),
                /* rand= */ ThreadLocalRandom.current());
    }

    @Test
    public void selectRandomStateAndGenerateReportConfigs_eventWithInstallAttribution() {
        // Total states: {nCk ∋ n = 6 (2 * 2 + 2), k = 2} -> 15
        ImpressionNoiseParams noiseParams =
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 2,
                        /* destinationMultiplier */ 1);

        ExtendedMockito.doReturn(6L).when(() -> ImpressionNoiseUtil.nextLong(any(), anyLong()));

        mStateSelectionTester.apply(
                /* impressionNoise= */ noiseParams,
                /* expectedReports= */ Collections.singletonList(new int[] {0, 1, 0}),
                /* rand= */ ThreadLocalRandom.current());

        ExtendedMockito.doReturn(2L).when(() -> ImpressionNoiseUtil.nextLong(any(), anyLong()));

        mStateSelectionTester.apply(
                /* impressionNoise= */ noiseParams,
                /* expectedReports= */ Arrays.asList(new int[] {0, 0, 0}, new int[] {0, 0, 0}),
                /* rand= */ ThreadLocalRandom.current());
    }

    @Test
    public void selectRandomStateAndGenerateReportConfigs_navigation() {
        // Total states: {nCk ∋ n = 27 (8 * 3 + 3), k = 3} -> 2925
        ImpressionNoiseParams noiseParams =
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 3,
                        /* destinationMultiplier */ 1);

        ExtendedMockito.doReturn(1416L).when(() -> ImpressionNoiseUtil.nextLong(any(), anyLong()));

        mStateSelectionTester.apply(
                /* impressionNoise= */ noiseParams,
                /* expectedReports= */ Arrays.asList(
                        new int[] {2, 2, 0}, new int[] {3, 1, 0}, new int[] {7, 0, 0}),
                /* rand= */ ThreadLocalRandom.current());

        ExtendedMockito.doReturn(1112L).when(() -> ImpressionNoiseUtil.nextLong(any(), anyLong()));

        mStateSelectionTester.apply(
                /* impressionNoise= */ noiseParams,
                /* expectedReports= */ Arrays.asList(
                        new int[] {0, 2, 0}, new int[] {7, 1, 0}, new int[] {6, 0, 0}),
                /* rand= */ ThreadLocalRandom.current());
    }

    @Test
    public void getReportConfigsForSequenceIndex_event() {
        // Total states: {nCk ∋ n = 3 (2 * 1 + 1), k = 1} -> 3
        ImpressionNoiseParams eventNoiseParams =
                new ImpressionNoiseParams(
                        /* reportCount= */ 1,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 1);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventNoiseParams,
                /*expectedReports=*/ Collections.emptyList(),
                /*sequenceIndex=*/ 0);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventNoiseParams,
                /*expectedReports=*/ Collections.singletonList(new int[] {0, 0, 0}),
                /*sequenceIndex=*/ 1);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventNoiseParams,
                /*expectedReports=*/ Collections.singletonList(new int[] {1, 0, 0}),
                /*sequenceIndex=*/ 2);
    }

    @Test
    public void getReportConfigsForSequenceIndex_eventWithDualDestination() {
        // Total states: {nCk ∋ n = 5 (2 * 2 * 1 + 1), k = 1} -> 5
        ImpressionNoiseParams eventNoiseParams =
                new ImpressionNoiseParams(
                        /* reportCount= */ 1,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 2);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventNoiseParams,
                /*expectedReports=*/ Collections.emptyList(),
                /*sequenceIndex=*/ 0);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventNoiseParams,
                /*expectedReports=*/ Collections.singletonList(new int[] {0, 0, 0}),
                /*sequenceIndex=*/ 1);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventNoiseParams,
                /*expectedReports=*/ Collections.singletonList(new int[] {1, 0, 0}),
                /*sequenceIndex=*/ 2);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventNoiseParams,
                /*expectedReports=*/ Collections.singletonList(new int[] {0, 0, 1}),
                /*sequenceIndex=*/ 3);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventNoiseParams,
                /*expectedReports=*/ Collections.singletonList(new int[] {1, 0, 1}),
                /*sequenceIndex=*/ 4);
    }

    @Test
    public void getReportConfigsForSequenceIndex_navigation() {
        // Total states: {nCk ∋ n = 27 (8 * 3 + 3), k = 3} -> 2925
        ImpressionNoiseParams navigationNoiseParams =
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 3,
                        /* destinationMultiplier */ 1);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Collections.emptyList(),
                /*sequenceIndex=*/ 0);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Collections.singletonList(new int[] {3, 0, 0}),
                /*sequenceIndex=*/ 20);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Arrays.asList(new int[] {4, 0, 0}, new int[] {2, 0, 0}),
                /*sequenceIndex=*/ 41);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Arrays.asList(new int[] {4, 0, 0}, new int[] {4, 0, 0}),
                /*sequenceIndex=*/ 50);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Arrays.asList(
                        new int[] {1, 2, 0}, new int[] {6, 1, 0}, new int[] {7, 0, 0}),
                /*sequenceIndex=*/ 1268);
    }

    @Test
    public void getReportConfigsForSequenceIndex_navigationDualDestination() {
        // Total states: {nCk ∋ n = 51 (8 * 3 * 2 + 3), k = 3} -> 20825
        ImpressionNoiseParams navigationNoiseParams =
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 3,
                        /* destinationMultiplier */ 2);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Collections.emptyList(),
                /*sequenceIndex=*/ 0);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Collections.singletonList(new int[] {3, 0, 0}),
                /*sequenceIndex=*/ 20);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Arrays.asList(new int[] {4, 0, 0}, new int[] {2, 0, 0}),
                /*sequenceIndex=*/ 41);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Arrays.asList(new int[] {4, 0, 0}, new int[] {4, 0, 0}),
                /*sequenceIndex=*/ 50);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Arrays.asList(
                        new int[] {1, 2, 0}, new int[] {6, 1, 0}, new int[] {7, 0, 0}),
                /*sequenceIndex=*/ 1268);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Arrays.asList(
                        new int[] {3, 1, 1}, new int[] {0, 1, 1}, new int[] {2, 0, 0}),
                /*sequenceIndex=*/ 9000);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Arrays.asList(new int[] {5, 1, 1}, new int[] {6, 1, 0}),
                /*sequenceIndex=*/ 10000);
    }

    @Test
    public void getReportConfigsForSequenceIndex_eventWithInstallAttribution() {
        // Total states: {nCk ∋ n = 6 (2 * 2 + 2), k = 2} -> 15
        ImpressionNoiseParams eventWithInstallAttributionNoiseParams =
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 2,
                        /* destinationMultiplier */ 1);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventWithInstallAttributionNoiseParams,
                /*expectedReports=*/ Collections.emptyList(),
                /*sequenceIndex=*/ 0);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventWithInstallAttributionNoiseParams,
                /*expectedReports=*/ Collections.singletonList(new int[] {0, 0, 0}),
                /*sequenceIndex=*/ 1);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventWithInstallAttributionNoiseParams,
                /*expectedReports=*/ Arrays.asList(new int[] {0, 0, 0}, new int[] {0, 0, 0}),
                /*sequenceIndex=*/ 2);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventWithInstallAttributionNoiseParams,
                /*expectedReports=*/ Collections.singletonList(new int[] {1, 1, 0}),
                /*sequenceIndex=*/ 10);
    }

    @Test
    public void
            selectFlexEventReportRandomStateAndGenerateReportConfigs_singleDestinationType_equal()
                    throws JSONException {
        TriggerSpecs testTriggerSpecsObject = getValidTriggerSpecsForRandomOrderTest();

        ExtendedMockito.doReturn(3L).when(() -> ImpressionNoiseUtil.nextLong(any(), anyLong()));

        mStateSelectionTesterFlexEvent.apply(
                testTriggerSpecsObject,
                1,
                /*expectedReports=*/ Collections.singletonList(new int[] {1, 0, 0}),
                ThreadLocalRandom.current());

        ExtendedMockito.doReturn(5L).when(() -> ImpressionNoiseUtil.nextLong(any(), anyLong()));

        mStateSelectionTesterFlexEvent.apply(
                testTriggerSpecsObject,
                1,
                /*expectedReports=*/ Arrays.asList(new int[] {1, 0, 0}, new int[] {0, 1, 0}),
                ThreadLocalRandom.current());
    }

    @Test
    public void
            selectFlexEventReportRandomStateAndGenerateReportConfigs_doubleDestinationType_equal()
                    throws JSONException {
        ExtendedMockito.doReturn(16L).when(() -> ImpressionNoiseUtil.nextLong(any(), anyLong()));

        mStateSelectionTesterFlexEvent.apply(
                getValidTriggerSpecsForRandomOrderTest(),
                2,
                /*expectedReports=*/ Arrays.asList(new int[] {1, 1, 0}, new int[] {0, 0, 0}),
                ThreadLocalRandom.current());

        ExtendedMockito.doReturn(12L).when(() -> ImpressionNoiseUtil.nextLong(any(), anyLong()));

        mStateSelectionTesterFlexEvent.apply(
                getValidTriggerSpecsForRandomOrderTest(),
                2,
                /*expectedReports=*/ Arrays.asList(new int[] {1, 0, 1}, new int[] {0, 0, 1}),
                ThreadLocalRandom.current());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        final ImpressionNoiseParams data1 = createExample();
        final ImpressionNoiseParams data2 = createExample();
        final Set<ImpressionNoiseParams> dataSet1 = Set.of(data1);
        final Set<ImpressionNoiseParams> dataSet2 = Set.of(data2);
        assertEquals(data1.hashCode(), data2.hashCode());
        assertEquals(data1, data2);
        assertEquals(dataSet1, dataSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        final ImpressionNoiseParams data1 = createExample();
        final ImpressionNoiseParams data2 = new ImpressionNoiseParams(1, 1, 1, 1);
        final Set<ImpressionNoiseParams> dataSet1 = Set.of(data1);
        final Set<ImpressionNoiseParams> dataSet2 = Set.of(data2);
        assertNotEquals(data1.hashCode(), data2.hashCode());
        assertNotEquals(data1, data2);
        assertNotEquals(dataSet1, dataSet2);
    }

    @Test
    public void testToString() {
        final ImpressionNoiseParams data1 = createExample();
        final ImpressionNoiseParams data2 = createExample();
        assertEquals(data1.toString(), data2.toString());
    }

    private ImpressionNoiseParams createExample() {
        return new ImpressionNoiseParams(
                /* reportCount= */ 2,
                /* triggerDataCardinality= */ 4,
                /* reportingWindowCount= */ 8,
                /* destinationMultiplier */ 1);
    }

    private static TriggerSpec[] getValidTriggerSpecArray() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("trigger_data", new JSONArray(new int[] {1, 2}));
        JSONObject windows = new JSONObject();
        windows.put("start_time", 0);
        windows.put(
                "end_times",
                new JSONArray(new long[] {TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7)}));
        json.put("event_report_windows", windows);
        json.put("summary_window_operator", TriggerSpec.SummaryOperatorType.COUNT);
        json.put("summary_buckets", new JSONArray(new int[] {1}));

        return TriggerSpecsUtil.triggerSpecArrayFrom(
                new JSONArray(new JSONObject[] {json}).toString());
    }

    /**
     * This test case is for the tests involving random order. Any parameters changes will get false
     * results and it is difficult to debug.
     *
     * @return TriggerSpecs for test
     * @throws JSONException JSON syntax error
     */
    private static TriggerSpecs getValidTriggerSpecsForRandomOrderTest() throws JSONException {
        Source source = SourceFixture.getMinimalValidSourceBuilder().build();
        TriggerSpecs triggerSpecs = new TriggerSpecs(getValidTriggerSpecArray(), 3, source);
        // Oblige building privacy parameters for the trigger specs
        triggerSpecs.getInformationGain(source, FlagsFactory.getFlagsForTest());
        return triggerSpecs;
    }
}
