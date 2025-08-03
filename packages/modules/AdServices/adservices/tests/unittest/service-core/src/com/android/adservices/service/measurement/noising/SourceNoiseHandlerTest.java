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

package com.android.adservices.service.measurement.noising;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.net.Uri;

import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.reporting.EventReportWindowCalcDelegate;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class SourceNoiseHandlerTest {
    private Flags mFlags;
    private SourceNoiseHandler mSourceNoiseHandler;

    @Before
    public void setup() {
        mFlags = mock(Flags.class);
        doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
                .when(mFlags).getMeasurementVtcConfigurableMaxEventReportsCount();
        doReturn(Flags.MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS)
                .when(mFlags).getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(Flags.MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS)
                .when(mFlags).getMeasurementEventReportsCtcEarlyReportingWindows();
        mSourceNoiseHandler =
                spy(new SourceNoiseHandler(mFlags, new EventReportWindowCalcDelegate(mFlags)));
    }

    @Test
    public void fakeReports_flexEventReport_generatesFromStaticReportStates() {
        Source source = SourceFixture.getValidSourceWithFlexEventReportWithFewerState();
        // Force increase the probability of random attribution.
        doReturn(0.50D).when(mSourceNoiseHandler).getRandomizedSourceResponsePickRate(source);
        int falseCount = 0;
        int neverCount = 0;
        int truthCount = 0;
        for (int i = 0; i < 500; i++) {
            List<Source.FakeReport> fakeReports =
                    mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(source);
            if (source.getAttributionMode() == Source.AttributionMode.FALSELY) {
                falseCount++;
                assertNotEquals(0, fakeReports.size());
            } else if (source.getAttributionMode() == Source.AttributionMode.NEVER) {
                neverCount++;
                assertEquals(0, fakeReports.size());
            } else {
                truthCount++;
            }
        }
        assertNotEquals(0, falseCount);
        assertNotEquals(0, neverCount);
        assertNotEquals(0, truthCount);
    }

    @Test
    public void impressionNoiseParamGeneration() {
        long eventTime = System.currentTimeMillis();
        Source eventSource30dExpiry =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 1,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(eventSource30dExpiry));

        Source eventSource7dExpiry =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 1,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(eventSource7dExpiry));

        Source eventSource2dExpiry =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 1,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(eventSource2dExpiry));

        Source navigationSource30dExpiry =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(eventTime)
                        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 3,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(navigationSource30dExpiry));

        Source navigationSource7dExpiry =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(eventTime)
                        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(7))
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 2,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(navigationSource7dExpiry));

        Source navigationSource2dExpiry =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(eventTime)
                        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(2))
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(navigationSource2dExpiry));
    }

    @Test
    public void impressionNoiseParamGeneration_flexLiteAPI() {
        doReturn(true).when(mFlags).getMeasurementFlexLiteApiEnabled();
        long eventTime = System.currentTimeMillis();
        Source eventSource2Windows =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
                        .setMaxEventLevelReports(2)
                        .setEventReportWindows("{ 'end_times': [3600, 7200]}")
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 2,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(eventSource2Windows));

        Source eventSource2Windows2Destinations =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setWebDestinations(List.of(Uri.parse("https://example.test")))
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
                        .setMaxEventLevelReports(2)
                        .setEventReportWindows("{ 'end_times': [3600, 7200]}")
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 2,
                        /* destinationMultiplier */ 2),
                mSourceNoiseHandler.getImpressionNoiseParams(eventSource2Windows2Destinations));

        Source eventSource1Window =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setMaxEventLevelReports(2)
                        .setEventReportWindows("{'end_times': [3600]}")
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(eventSource1Window));

        Source navigationSource3Windows =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
                        .setMaxEventLevelReports(3)
                        .setEventReportWindows("{'end_times': [3600, 7200, 86400]}")
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 3,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(navigationSource3Windows));

        Source navigationSource2Window =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(7))
                        .setMaxEventLevelReports(3)
                        .setEventReportWindows("{'end_times': [3600, 7200]}")
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 2,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(navigationSource2Window));
    }

    @Test
    public void impressionNoiseParamGeneration_withInstallAttribution() {
        long eventTime = System.currentTimeMillis();

        Source eventSource30dExpiry =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                        .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                        .setEventTime(eventTime)
                        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 2,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(eventSource30dExpiry));

        Source eventSource7dExpiry =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                        .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                        .setEventTime(eventTime)
                        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(7))
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 2,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(eventSource7dExpiry));

        Source eventSource2dExpiry =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                        .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                        .setEventTime(eventTime)
                        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(2))
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(eventSource2dExpiry));

        Source navigationSource30dExpiry =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                        .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                        .setEventTime(eventTime)
                        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 3,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(navigationSource30dExpiry));

        Source navigationSource7dExpiry =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                        .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                        .setEventTime(eventTime)
                        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(7))
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 2,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(navigationSource7dExpiry));

        Source navigationSource2dExpiry =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                        .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                        .setEventTime(eventTime)
                        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(2))
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(navigationSource2dExpiry));
        Source eventSourceWith2Destinations30dExpiry =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                        .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                        .setEventTime(eventTime)
                        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 2,
                        /* destinationMultiplier */ 1),
                mSourceNoiseHandler.getImpressionNoiseParams(
                        eventSourceWith2Destinations30dExpiry));
    }

    @Test
    public void testFakeReportGeneration() {
        long expiry = System.currentTimeMillis();
        // Single (App) destination, EVENT type
        verifyAlgorithmicFakeReportGeneration(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(null)
                        .setEventReportWindow(expiry)
                        .build(),
                PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY);

        // Single (App) destination, NAVIGATION type
        verifyAlgorithmicFakeReportGeneration(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(null)
                        .setEventReportWindow(expiry)
                        .build(),
                PrivacyParams.getNavigationTriggerDataCardinality());

        // Single (Web) destination, EVENT type
        verifyAlgorithmicFakeReportGeneration(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiry)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .build(),
                PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY);

        // Single (Web) destination, NAVIGATION type
        verifyAlgorithmicFakeReportGeneration(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiry)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .build(),
                PrivacyParams.getNavigationTriggerDataCardinality());

        // Both destinations set, EVENT type
        verifyAlgorithmicFakeReportGeneration(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiry)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .build(),
                PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY);

        // Both destinations set, NAVIGATION type
        verifyAlgorithmicFakeReportGeneration(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiry)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .build(),
                PrivacyParams.getNavigationTriggerDataCardinality());

        // App destination with cooldown window
        verifyAlgorithmicFakeReportGeneration(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiry)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(null)
                        .setInstallCooldownWindow(
                                SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW)
                        .build(),
                PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY);
    }

    private List<Source.FakeReport> convertToReportsState(int[][] reportsState, Source source) {
        return Arrays.stream(reportsState)
                .map(
                        reportState ->
                                new Source.FakeReport(
                                        new UnsignedLong(Long.valueOf(reportState[0])),
                                        new EventReportWindowCalcDelegate(mFlags)
                                                .getReportingTimeForNoising(
                                                        source, reportState[1]),
                                        reportState[2] == 0
                                                ? source.getAppDestinations()
                                                : source.getWebDestinations()))
                .collect(Collectors.toList());
    }

    private void verifyAlgorithmicFakeReportGeneration(Source source, int expectedCardinality) {
        // Force increase the probability of random attribution.
        doReturn(0.50D).when(mSourceNoiseHandler).getRandomizedSourceResponsePickRate(source);
        int falseCount = 0;
        int neverCount = 0;
        int truthCount = 0;
        for (int i = 0; i < 500; i++) {
            List<Source.FakeReport> fakeReports =
                    mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(source);
            if (source.getAttributionMode() == Source.AttributionMode.FALSELY) {
                falseCount++;
                assertNotEquals(0, fakeReports.size());
                for (Source.FakeReport report : fakeReports) {
                    assertTrue(
                            source.getEventReportWindow() + TimeUnit.HOURS.toMillis(1)
                                    >= report.getReportingTime());
                    Long triggerData = report.getTriggerData().getValue();
                    assertTrue(0 <= triggerData && triggerData < expectedCardinality);
                }
            } else if (source.getAttributionMode() == Source.AttributionMode.NEVER) {
                neverCount++;
                assertEquals(0, fakeReports.size());
            } else {
                truthCount++;
            }
        }
        assertNotEquals(0, falseCount);
        assertNotEquals(0, neverCount);
        assertNotEquals(0, truthCount);
    }
}
