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

import static com.android.adservices.service.Flags.MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS;
import static com.android.adservices.service.measurement.PrivacyParams.INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
import static com.android.adservices.service.measurement.SourceFixture.ValidSourceParams;
import static com.android.adservices.service.measurement.SourceFixture.getMinimalValidSourceBuilder;
import static com.android.adservices.service.measurement.TriggerFixture.getValidTriggerBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.net.Uri;
import android.provider.DeviceConfig;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.WebUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.noising.SourceNoiseHandler;
import com.android.adservices.service.measurement.reporting.EventReportWindowCalcDelegate;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.modules.utils.testing.TestableDeviceConfig;

import com.google.common.collect.ImmutableList;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link EventReport} */
@SmallTest
public final class EventReportTest {
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    private static final double EVENT_NOISE_PROBABILITY = 0.0000025D;
    private static final double INSTALL_ATTR_EVENT_NOISE_PROBABILITY = 0.0000125D;
    private static final double INSTALL_ATTR_NAVIGATION_NOISE_PROBABILITY = 0.0024263D;
    private static final double NAVIGATION_NOISE_PROBABILITY = 0.0024263D;

    private static final double DOUBLE_MAX_DELTA = 0.0000001D;
    private static final long TRIGGER_PRIORITY = 345678L;

    private static final long VALUE = 1234567L;
    private static final UnsignedLong TRIGGER_DEDUP_KEY = new UnsignedLong(2345678L);
    private static final UnsignedLong TRIGGER_DATA = new UnsignedLong(4L);
    private static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(237865L);
    private static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(928762L);
    private static final List<UnsignedLong> TRIGGER_DEBUG_KEYS = List.of(
            new UnsignedLong(928762L), new UnsignedLong("9223372036854775809"));
    private static final String SOURCE_ID = UUID.randomUUID().toString();
    private static final String TRIGGER_ID = UUID.randomUUID().toString();
    private static final Uri REGISTRATION_ORIGIN =
            WebUtil.validUri("https://subdomain.example.test");
    private static final Uri APP_DESTINATION = Uri.parse("android-app://example1.app");
    private static final Uri WEB_DESTINATION = Uri.parse("https://example1.test");
    private static final String EVENT_TRIGGERS =
            "[\n"
                    + "{\n"
                    + "  \"trigger_data\": \""
                    + TRIGGER_DATA
                    + "\",\n"
                    + "  \"priority\": \""
                    + TRIGGER_PRIORITY
                    + "\",\n"
                    + "  \"value\": \""
                    + VALUE
                    + "\",\n"
                    + "  \"deduplication_key\": \""
                    + TRIGGER_DEDUP_KEY
                    + "\",\n"
                    + "  \"filters\": [{\n"
                    + "    \"source_type\": [\"navigation\"],\n"
                    + "    \"key_1\": [\"value_1\"] \n"
                    + "   }]\n"
                    + "}"
                    + "]\n";
    private static final Pair<UnsignedLong, UnsignedLong> sDebugKeyPair =
            new Pair<>(SOURCE_DEBUG_KEY, TRIGGER_DEBUG_KEY);

    private EventReportWindowCalcDelegate mEventReportWindowCalcDelegate;
    private SourceNoiseHandler mSourceNoiseHandler;
    private Flags mFlags;

    @Before
    public void setup() {
        final String phOverridingValue = ValidSourceParams.ENROLLMENT_ID;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_debug_join_key_enrollment_allowlist",
                phOverridingValue,
                /* makeDefault */ false);
        mFlags = spy(FlagsFactory.getFlags());
        mEventReportWindowCalcDelegate = new EventReportWindowCalcDelegate(mFlags);
        mSourceNoiseHandler = new SourceNoiseHandler(mFlags);

        final String phOverridingValueAdIdMatching = "";
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_debug_key_ad_id_matching_enrollment_blocklist",
                phOverridingValueAdIdMatching,
                /* makeDefault */ false);
    }

    @Test
    public void creation_success() {
        EventReport eventReport = createExample();
        assertEquals("1", eventReport.getId());
        assertEquals(new UnsignedLong(21L), eventReport.getSourceEventId());
        assertEquals("enrollment-id", eventReport.getEnrollmentId());
        assertEquals("https://bar.test",
                eventReport.getAttributionDestinations().get(0).toString());
        assertEquals(1000L, eventReport.getTriggerTime());
        assertEquals(new UnsignedLong(8L), eventReport.getTriggerData());
        assertEquals(2L, eventReport.getTriggerPriority());
        assertEquals(new UnsignedLong(3L), eventReport.getTriggerDedupKey());
        assertEquals(2000L, eventReport.getReportTime());
        assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
        assertEquals(EventReport.DebugReportStatus.PENDING, eventReport.getDebugReportStatus());
        assertEquals(Source.SourceType.NAVIGATION, eventReport.getSourceType());
        assertEquals(SOURCE_DEBUG_KEY, eventReport.getSourceDebugKey());
        assertEquals(TRIGGER_DEBUG_KEY, eventReport.getTriggerDebugKey());
        assertEquals(TRIGGER_DEBUG_KEYS, eventReport.getTriggerDebugKeys());
        assertEquals(SOURCE_ID, eventReport.getSourceId());
        assertEquals(TRIGGER_ID, eventReport.getTriggerId());
        assertEquals(REGISTRATION_ORIGIN, eventReport.getRegistrationOrigin());
    }

    @Test
    public void creationSuccessSingleSourceDebugKey() {
        EventReport eventReport = createExampleSingleSourceDebugKey();
        assertEquals("1", eventReport.getId());
        assertEquals(new UnsignedLong(21L), eventReport.getSourceEventId());
        assertEquals("enrollment-id", eventReport.getEnrollmentId());
        assertEquals("https://bar.test",
                eventReport.getAttributionDestinations().get(0).toString());
        assertEquals(1000L, eventReport.getTriggerTime());
        assertEquals(new UnsignedLong(8L), eventReport.getTriggerData());
        assertEquals(2L, eventReport.getTriggerPriority());
        assertEquals(100L, eventReport.getTriggerValue());
        assertEquals(new UnsignedLong(3L), eventReport.getTriggerDedupKey());
        assertEquals(2000L, eventReport.getReportTime());
        assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
        assertEquals(EventReport.DebugReportStatus.PENDING, eventReport.getDebugReportStatus());
        assertEquals(Source.SourceType.NAVIGATION, eventReport.getSourceType());
        assertEquals(SOURCE_DEBUG_KEY, eventReport.getSourceDebugKey());
        assertNull(eventReport.getTriggerDebugKey());
        assertEquals(SOURCE_ID, eventReport.getSourceId());
        assertEquals(TRIGGER_ID, eventReport.getTriggerId());
        assertEquals(REGISTRATION_ORIGIN, eventReport.getRegistrationOrigin());
    }

    @Test
    public void creationSuccessSingleTriggerDebugKey() {
        EventReport eventReport = createExampleSingleTriggerDebugKey();
        assertEquals("1", eventReport.getId());
        assertEquals(new UnsignedLong(21L), eventReport.getSourceEventId());
        assertEquals("enrollment-id", eventReport.getEnrollmentId());
        assertEquals("https://bar.test",
                eventReport.getAttributionDestinations().get(0).toString());
        assertEquals(1000L, eventReport.getTriggerTime());
        assertEquals(new UnsignedLong(8L), eventReport.getTriggerData());
        assertEquals(2L, eventReport.getTriggerPriority());
        assertEquals(100L, eventReport.getTriggerValue());
        assertEquals(new UnsignedLong(3L), eventReport.getTriggerDedupKey());
        assertEquals(2000L, eventReport.getReportTime());
        assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
        assertEquals(EventReport.DebugReportStatus.PENDING, eventReport.getDebugReportStatus());
        assertEquals(Source.SourceType.NAVIGATION, eventReport.getSourceType());
        assertNull(eventReport.getSourceDebugKey());
        assertEquals(TRIGGER_DEBUG_KEY, eventReport.getTriggerDebugKey());
        assertEquals(SOURCE_ID, eventReport.getSourceId());
        assertEquals(TRIGGER_ID, eventReport.getTriggerId());
        assertEquals(REGISTRATION_ORIGIN, eventReport.getRegistrationOrigin());
    }

    @Test
    public void creationSuccessMultipleTriggerDebugKeys() {
        EventReport eventReport = createExampleMultipleTriggerDebugKeys();
        assertEquals("1", eventReport.getId());
        assertEquals(new UnsignedLong(21L), eventReport.getSourceEventId());
        assertEquals("enrollment-id", eventReport.getEnrollmentId());
        assertEquals("https://bar.test",
                eventReport.getAttributionDestinations().get(0).toString());
        assertEquals(1000L, eventReport.getTriggerTime());
        assertEquals(new UnsignedLong(8L), eventReport.getTriggerData());
        assertEquals(2L, eventReport.getTriggerPriority());
        assertEquals(100L, eventReport.getTriggerValue());
        assertEquals(new UnsignedLong(3L), eventReport.getTriggerDedupKey());
        assertEquals(2000L, eventReport.getReportTime());
        assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
        assertEquals(EventReport.DebugReportStatus.PENDING, eventReport.getDebugReportStatus());
        assertEquals(Source.SourceType.NAVIGATION, eventReport.getSourceType());
        assertNull(eventReport.getSourceDebugKey());
        assertNull(eventReport.getTriggerDebugKey());
        assertEquals(TRIGGER_DEBUG_KEYS, eventReport.getTriggerDebugKeys());
        assertEquals(SOURCE_ID, eventReport.getSourceId());
        assertEquals(TRIGGER_ID, eventReport.getTriggerId());
        assertEquals(REGISTRATION_ORIGIN, eventReport.getRegistrationOrigin());
    }

    @Test
    public void defaults_success() {
        EventReport eventReport = new EventReport.Builder().build();
        assertNull(eventReport.getId());
        assertNull(eventReport.getSourceEventId());
        assertNull(eventReport.getEnrollmentId());
        assertNull(eventReport.getAttributionDestinations());
        assertEquals(0L, eventReport.getTriggerTime());
        assertNull(eventReport.getTriggerData());
        assertEquals(0L, eventReport.getTriggerPriority());
        assertEquals(0L, eventReport.getTriggerValue());
        assertNull(eventReport.getTriggerDedupKey());
        assertEquals(0L, eventReport.getReportTime());
        assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
        assertEquals(EventReport.DebugReportStatus.NONE, eventReport.getDebugReportStatus());
        assertNull(eventReport.getSourceType());
        assertNull(eventReport.getSourceDebugKey());
        assertNull(eventReport.getTriggerDebugKey());
        assertEquals(Collections.emptyList(), eventReport.getTriggerDebugKeys());
        assertNull(eventReport.getSourceId());
        assertNull(eventReport.getTriggerId());
        assertNull(eventReport.getRegistrationOrigin());
    }

    @Test
    public void populateFromSourceAndTrigger_eventSourceAppDestWithoutInstallAttribution()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.EVENT, false, APP_DESTINATION, null);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), APP_DESTINATION, EventSurfaceType.APP);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers(mFlags);
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(
                                source,
                                trigger,
                                eventTriggers.get(0),
                                sDebugKeyPair,
                                mEventReportWindowCalcDelegate,
                                mSourceNoiseHandler,
                                source.getAttributionDestinations(trigger.getDestinationType()))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(0L, report.getTriggerValue());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        // Truncated data 4 % 2 = 0
        assertEquals(new UnsignedLong(0L), report.getTriggerData());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(),
                report.getAttributionDestinations().get(0));
        assertEquals(source.getEventReportWindow() + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                report.getReportTime());
        assertEquals(source.getSourceType(), report.getSourceType());
        assertEquals(EVENT_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
        assertEquals(REGISTRATION_ORIGIN, report.getRegistrationOrigin());
    }

    @Test
    public void populate_eventSourceAppDestWithoutInstallConfigured() throws JSONException {
        long baseTime = System.currentTimeMillis();
        String earlyReportingWindows1h1d =
                String.join(
                        ",",
                        Long.toString(TimeUnit.HOURS.toSeconds(1)),
                        Long.toString(TimeUnit.DAYS.toSeconds(1)));
        doReturn(earlyReportingWindows1h1d)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(3).when(mFlags).getMeasurementVtcConfigurableMaxEventReportsCount();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.EVENT, false, APP_DESTINATION, null);
        Trigger trigger =
                createTriggerForTest(
                        baseTime + TimeUnit.SECONDS.toMillis(10),
                        APP_DESTINATION,
                        EventSurfaceType.APP);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers(mFlags);
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(
                                source,
                                trigger,
                                eventTriggers.get(0),
                                sDebugKeyPair,
                                mEventReportWindowCalcDelegate,
                                mSourceNoiseHandler,
                                source.getAttributionDestinations(trigger.getDestinationType()))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        // Truncated data 4 % 2 = 0
        assertEquals(new UnsignedLong(0L), report.getTriggerData());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(
                trigger.getAttributionDestination(), report.getAttributionDestinations().get(0));
        assertEquals(
                baseTime + TimeUnit.HOURS.toMillis(1) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                report.getReportTime());
        assertEquals(source.getSourceType(), report.getSourceType());
        // VTC, 3-1-3 config
        assertEquals(0.0000698, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
        assertEquals(REGISTRATION_ORIGIN, report.getRegistrationOrigin());
    }

    @Test
    public void testpopulate_shouldTruncateTriggerDataWith64thBit() throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.NAVIGATION, false, APP_DESTINATION, null);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), APP_DESTINATION, EventSurfaceType.APP);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers(mFlags);
        EventTrigger eventTrigger = spy(eventTriggers.get(0));
        when(eventTrigger.getTriggerData()).thenReturn(new UnsignedLong(-50003L));
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(
                                source,
                                trigger,
                                eventTrigger,
                                sDebugKeyPair,
                                mEventReportWindowCalcDelegate,
                                mSourceNoiseHandler,
                                source.getAttributionDestinations(trigger.getDestinationType()))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        // uint64 18446744073709501613 = long -50003
        // Truncated data 18446744073709501613 % 8 = 5
        assertEquals(new UnsignedLong(5L), report.getTriggerData());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(APP_DESTINATION, report.getAttributionDestinations().get(0));
        assertEquals(
                source.getEventTime()
                        + NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                report.getReportTime());
        assertEquals(Source.SourceType.NAVIGATION, report.getSourceType());
        assertEquals(
                NAVIGATION_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
        assertEquals(REGISTRATION_ORIGIN, report.getRegistrationOrigin());
    }

    @Test
    public void populate_eventSourceWebDestWithoutInstallAttribution() throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.EVENT, false, null, WEB_DESTINATION);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), WEB_DESTINATION, EventSurfaceType.WEB);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers(mFlags);
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(
                                source,
                                trigger,
                                eventTriggers.get(0),
                                sDebugKeyPair,
                                mEventReportWindowCalcDelegate,
                                mSourceNoiseHandler,
                                source.getAttributionDestinations(trigger.getDestinationType()))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        // Truncated data 4 % 2 = 0
        assertEquals(new UnsignedLong(0L), report.getTriggerData());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(),
                report.getAttributionDestinations().get(0));
        assertEquals(source.getEventReportWindow() + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                report.getReportTime());
        assertEquals(Source.SourceType.EVENT, report.getSourceType());
        assertEquals(EVENT_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
        assertEquals(REGISTRATION_ORIGIN, report.getRegistrationOrigin());
    }

    @Test
    public void populate_eventSourceAppDestWithInstallAttribution() throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(baseTime, Source.SourceType.EVENT, true, APP_DESTINATION, null);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), APP_DESTINATION, EventSurfaceType.APP);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers(mFlags);
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(
                                source,
                                trigger,
                                eventTriggers.get(0),
                                sDebugKeyPair,
                                mEventReportWindowCalcDelegate,
                                mSourceNoiseHandler,
                                source.getAttributionDestinations(trigger.getDestinationType()))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(),
                report.getAttributionDestinations().get(0));
        assertEquals(source.getEventReportWindow() + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                report.getReportTime());
        assertEquals(Source.SourceType.EVENT, report.getSourceType());
        assertEquals(
                INSTALL_ATTR_EVENT_NOISE_PROBABILITY,
                report.getRandomizedTriggerRate(),
                DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
        assertEquals(REGISTRATION_ORIGIN, report.getRegistrationOrigin());
    }

    @Test
    public void populate_eventSourceWebDestWithInstallAttribution() throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(baseTime, Source.SourceType.EVENT, true, null, WEB_DESTINATION);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), WEB_DESTINATION, EventSurfaceType.WEB);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers(mFlags);
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(
                                source,
                                trigger,
                                eventTriggers.get(0),
                                sDebugKeyPair,
                                mEventReportWindowCalcDelegate,
                                mSourceNoiseHandler,
                                source.getAttributionDestinations(trigger.getDestinationType()))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(),
                report.getAttributionDestinations().get(0));
        assertEquals(source.getEventReportWindow() + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                report.getReportTime());
        assertEquals(Source.SourceType.EVENT, report.getSourceType());
        assertEquals(EVENT_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
        assertEquals(REGISTRATION_ORIGIN, report.getRegistrationOrigin());
    }

    @Test
    public void populate_navigationSourceAppDestWithoutInstall() throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.NAVIGATION, false, APP_DESTINATION, null);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), APP_DESTINATION, EventSurfaceType.APP);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers(mFlags);
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(
                                source,
                                trigger,
                                eventTriggers.get(0),
                                sDebugKeyPair,
                                mEventReportWindowCalcDelegate,
                                mSourceNoiseHandler,
                                source.getAttributionDestinations(trigger.getDestinationType()))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(APP_DESTINATION, report.getAttributionDestinations().get(0));
        assertEquals(
                source.getEventTime()
                        + NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                report.getReportTime());
        assertEquals(Source.SourceType.NAVIGATION, report.getSourceType());
        assertEquals(
                NAVIGATION_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
        assertEquals(REGISTRATION_ORIGIN, report.getRegistrationOrigin());
    }

    @Test
    public void populate_navigationSourceWebDestWithoutInstall() throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.NAVIGATION, false, null, WEB_DESTINATION);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), WEB_DESTINATION, EventSurfaceType.WEB);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers(mFlags);
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(
                                source,
                                trigger,
                                eventTriggers.get(0),
                                sDebugKeyPair,
                                mEventReportWindowCalcDelegate,
                                mSourceNoiseHandler,
                                source.getAttributionDestinations(trigger.getDestinationType()))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(),
                report.getAttributionDestinations().get(0));
        assertEquals(
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, trigger.getTriggerTime(), EventSurfaceType.WEB),
                report.getReportTime());
        assertEquals(source.getSourceType(), report.getSourceType());
        assertEquals(
                NAVIGATION_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
        assertEquals(REGISTRATION_ORIGIN, report.getRegistrationOrigin());
    }

    @Test
    public void testPopulate_navigationSourceAppDestWithInstallAttribution() throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.NAVIGATION, true, APP_DESTINATION, null);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), APP_DESTINATION, EventSurfaceType.APP);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers(mFlags);
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(
                                source,
                                trigger,
                                eventTriggers.get(0),
                                sDebugKeyPair,
                                mEventReportWindowCalcDelegate,
                                mSourceNoiseHandler,
                                source.getAttributionDestinations(trigger.getDestinationType()))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(new UnsignedLong(4L), report.getTriggerData());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(),
                report.getAttributionDestinations().get(0));
        // One hour after install attributed navigation type window
        assertEquals(
                source.getEventTime()
                        + INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                report.getReportTime());
        assertEquals(Source.SourceType.NAVIGATION, report.getSourceType());
        assertEquals(
                INSTALL_ATTR_NAVIGATION_NOISE_PROBABILITY,
                report.getRandomizedTriggerRate(),
                DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
        assertEquals(REGISTRATION_ORIGIN, report.getRegistrationOrigin());
    }

    @Test
    public void testpopulate_navigationSourceWebDestWithInstallAttribution() throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.NAVIGATION, true, null, WEB_DESTINATION);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), WEB_DESTINATION, EventSurfaceType.WEB);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers(mFlags);
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(
                                source,
                                trigger,
                                eventTriggers.get(0),
                                sDebugKeyPair,
                                mEventReportWindowCalcDelegate,
                                mSourceNoiseHandler,
                                source.getAttributionDestinations(trigger.getDestinationType()))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(new UnsignedLong(4L), report.getTriggerData());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(),
                report.getAttributionDestinations().get(0));
        // One hour after regular navigation type window (without install attribution consideration)
        assertEquals(
                source.getEventTime()
                        + NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                report.getReportTime());
        assertEquals(source.getSourceType(), report.getSourceType());
        assertEquals(
                NAVIGATION_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
        assertEquals(REGISTRATION_ORIGIN, report.getRegistrationOrigin());
    }

    @Test
    public void populate_eventSourceWebAndAppDest_coarseDestination() throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                getMinimalValidSourceBuilder()
                        .setId(SOURCE_ID)
                        .setEventId(new UnsignedLong(10L))
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallCooldownWindow(0)
                        .setEventTime(baseTime)
                        .setEnrollmentId("enrollment-id")
                        .setAppDestinations(Collections.singletonList(APP_DESTINATION))
                        .setWebDestinations(Collections.singletonList(WEB_DESTINATION))
                        .setExpiryTime(baseTime + TimeUnit.DAYS.toMillis(30))
                        .setEventReportWindow(baseTime + TimeUnit.DAYS.toMillis(10))
                        .setCoarseEventReportDestinations(true)
                        .build();
        Trigger trigger =
                createTriggerForTest(
                        baseTime + TimeUnit.SECONDS.toMillis(10),
                        WEB_DESTINATION,
                        EventSurfaceType.WEB);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers(mFlags);
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(
                                source,
                                trigger,
                                eventTriggers.get(0),
                                sDebugKeyPair,
                                mEventReportWindowCalcDelegate,
                                mSourceNoiseHandler,
                                new ImmutableList.Builder<Uri>()
                                        .add(APP_DESTINATION)
                                        .add(WEB_DESTINATION)
                                        .build())
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        // Truncated data 4 % 2 = 0
        assertEquals(new UnsignedLong(0L), report.getTriggerData());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(2, report.getAttributionDestinations().size());
        assertEquals(
                source.getAppDestinations().get(0), report.getAttributionDestinations().get(0));
        assertEquals(
                source.getWebDestinations().get(0), report.getAttributionDestinations().get(1));
        assertEquals(source.getEventReportWindow() + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                report.getReportTime());
        assertEquals(Source.SourceType.EVENT, report.getSourceType());
        assertEquals(EVENT_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
        assertEquals(REGISTRATION_ORIGIN, report.getRegistrationOrigin());
    }

    @Test
    public void testHashCode_equals() {
        final EventReport eventReport1 = createExample();
        final EventReport eventReport2 = createExample();
        final Set<EventReport> eventReportSet1 = Set.of(eventReport1);
        final Set<EventReport> eventReportSet2 = Set.of(eventReport2);
        assertEquals(eventReport1.hashCode(), eventReport2.hashCode());
        assertEquals(eventReport1, eventReport2);
        assertEquals(eventReportSet1, eventReportSet2);
    }

    @Test
    public void testHashCode_notEquals() {
        final EventReport eventReport1 = createExample();
        final EventReport eventReport2 =
                new EventReport.Builder()
                        .setId("1")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setTriggerTime(1000L)
                        .setTriggerData(new UnsignedLong(8L))
                        .setTriggerPriority(2L)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setReportTime(2000L)
                        .setStatus(EventReport.Status.PENDING)
                        .setStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .build();
        final Set<EventReport> eventReportSet1 = Set.of(eventReport1);
        final Set<EventReport> eventReportSet2 = Set.of(eventReport2);
        assertNotEquals(eventReport1.hashCode(), eventReport2.hashCode());
        assertNotEquals(eventReport1, eventReport2);
        assertNotEquals(eventReportSet1, eventReportSet2);
    }

    @Test
    public void populateFromSourceAndTrigger_setsRegistrationOrigin_FromTrigger()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                getMinimalValidSourceBuilder()
                        .setId(SOURCE_ID)
                        .setEventId(new UnsignedLong(10L))
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallCooldownWindow(100)
                        .setEventTime(baseTime)
                        .setEnrollmentId("enrollment-id")
                        .setAppDestinations(getNullableUriList(APP_DESTINATION))
                        .setWebDestinations(getNullableUriList(null))
                        .setEventReportWindow(TimeUnit.DAYS.toMillis(10))
                        .setRegistrationOrigin(REGISTRATION_ORIGIN)
                        .build();

        Uri triggerRegistrationUrl = WebUtil.validUri("https://trigger.example.test");
        Trigger trigger =
                getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setTriggerTime(baseTime + TimeUnit.SECONDS.toMillis(10))
                        .setEventTriggers(EVENT_TRIGGERS)
                        .setEnrollmentId("enrollment-id")
                        .setAttributionDestination(APP_DESTINATION)
                        .setDestinationType(EventSurfaceType.APP)
                        .setRegistrationOrigin(triggerRegistrationUrl)
                        .build();

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers(mFlags);
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(
                                source,
                                trigger,
                                eventTriggers.get(0),
                                sDebugKeyPair,
                                mEventReportWindowCalcDelegate,
                                mSourceNoiseHandler,
                                source.getAttributionDestinations(trigger.getDestinationType()))
                        .build();

        assertEquals(triggerRegistrationUrl, report.getRegistrationOrigin());
        assertEquals("enrollment-id", report.getEnrollmentId());
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
    }

    @Test
    public void setTriggerSummaryBucket_fromString_equalToExpectation() {
        EventReport eventReport =
                EventReportFixture.getBaseEventReportBuild()
                        .setTriggerSummaryBucket("3,10")
                        .build();

        assertEquals(new Pair<>(3L, 10L), eventReport.getTriggerSummaryBucket());
    }

    private Source createSourceForTest(
            long eventTime,
            Source.SourceType sourceType,
            boolean isInstallAttributable,
            Uri appDestination,
            Uri webDestination) {
        return getMinimalValidSourceBuilder()
                .setId(SOURCE_ID)
                .setEventId(new UnsignedLong(10L))
                .setSourceType(sourceType)
                .setInstallCooldownWindow(isInstallAttributable ? 100 : 0)
                .setEventTime(eventTime)
                .setEnrollmentId("enrollment-id")
                .setAppDestinations(getNullableUriList(appDestination))
                .setWebDestinations(getNullableUriList(webDestination))
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(10))
                .build();
    }

    private Trigger createTriggerForTest(
            long eventTime, Uri destination, @EventSurfaceType int destinationType) {
        return getValidTriggerBuilder()
                .setId(TRIGGER_ID)
                .setTriggerTime(eventTime)
                .setEventTriggers(EVENT_TRIGGERS)
                .setEnrollmentId("enrollment-id")
                .setAttributionDestination(destination)
                .setDestinationType(destinationType)
                .build();
    }

    private EventReport createExample() {
        String summaryBucket = null;
        return new EventReport.Builder()
                .setId("1")
                .setSourceEventId(new UnsignedLong(21L))
                .setEnrollmentId("enrollment-id")
                .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                .setTriggerTime(1000L)
                .setTriggerData(new UnsignedLong(8L))
                .setTriggerPriority(2L)
                .setTriggerValue(100L)
                .setTriggerDedupKey(new UnsignedLong(3L))
                .setReportTime(2000L)
                .setStatus(EventReport.Status.PENDING)
                .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                .setSourceType(Source.SourceType.NAVIGATION)
                .setSourceDebugKey(SOURCE_DEBUG_KEY)
                .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                .setTriggerDebugKeys(TRIGGER_DEBUG_KEYS)
                .setSourceId(SOURCE_ID)
                .setTriggerId(TRIGGER_ID)
                .setRegistrationOrigin(REGISTRATION_ORIGIN)
                .setTriggerSummaryBucket(summaryBucket)
                .build();
    }

    private EventReport createExampleSingleTriggerDebugKey() {
        return new EventReport.Builder()
                .setId("1")
                .setSourceEventId(new UnsignedLong(21L))
                .setEnrollmentId("enrollment-id")
                .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                .setTriggerTime(1000L)
                .setTriggerData(new UnsignedLong(8L))
                .setTriggerPriority(2L)
                .setTriggerValue(100L)
                .setTriggerDedupKey(new UnsignedLong(3L))
                .setReportTime(2000L)
                .setStatus(EventReport.Status.PENDING)
                .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                .setSourceType(Source.SourceType.NAVIGATION)
                .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                .setSourceId(SOURCE_ID)
                .setTriggerId(TRIGGER_ID)
                .setRegistrationOrigin(REGISTRATION_ORIGIN)
                .build();
    }

    private EventReport createExampleMultipleTriggerDebugKeys() {
        return new EventReport.Builder()
                .setId("1")
                .setSourceEventId(new UnsignedLong(21L))
                .setEnrollmentId("enrollment-id")
                .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                .setTriggerTime(1000L)
                .setTriggerData(new UnsignedLong(8L))
                .setTriggerPriority(2L)
                .setTriggerValue(100L)
                .setTriggerDedupKey(new UnsignedLong(3L))
                .setReportTime(2000L)
                .setStatus(EventReport.Status.PENDING)
                .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                .setSourceType(Source.SourceType.NAVIGATION)
                .setTriggerDebugKeys(TRIGGER_DEBUG_KEYS)
                .setSourceId(SOURCE_ID)
                .setTriggerId(TRIGGER_ID)
                .setRegistrationOrigin(REGISTRATION_ORIGIN)
                .build();
    }

    private EventReport createExampleSingleSourceDebugKey() {
        return new EventReport.Builder()
                .setId("1")
                .setSourceEventId(new UnsignedLong(21L))
                .setEnrollmentId("enrollment-id")
                .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                .setTriggerTime(1000L)
                .setTriggerData(new UnsignedLong(8L))
                .setTriggerPriority(2L)
                .setTriggerValue(100L)
                .setTriggerDedupKey(new UnsignedLong(3L))
                .setReportTime(2000L)
                .setStatus(EventReport.Status.PENDING)
                .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                .setSourceType(Source.SourceType.NAVIGATION)
                .setSourceDebugKey(SOURCE_DEBUG_KEY)
                .setSourceId(SOURCE_ID)
                .setTriggerId(TRIGGER_ID)
                .setRegistrationOrigin(REGISTRATION_ORIGIN)
                .build();
    }

    private static Trigger createTriggerForDebugJoinKeyTests(
            int destinationType,
            boolean adIdPermission,
            boolean arDebugPermission,
            Uri registrant,
            String debugJoinKey) {
        return getValidTriggerBuilder()
                .setId(TRIGGER_ID)
                .setEventTriggers(EVENT_TRIGGERS)
                .setArDebugPermission(arDebugPermission)
                .setAdIdPermission(adIdPermission)
                .setRegistrant(registrant)
                .setDestinationType(destinationType)
                .setDebugKey(TRIGGER_DEBUG_KEY)
                .setDebugJoinKey(debugJoinKey)
                .build();
    }

    private static Source createSourceForDebugJoinKeyTests(
            int publisherType,
            boolean adIdPermission,
            boolean arDebugPermission,
            Uri registrant,
            String debugJoinKey) {
        return getMinimalValidSourceBuilder()
                .setId(SOURCE_ID)
                .setArDebugPermission(arDebugPermission)
                .setAdIdPermission(adIdPermission)
                .setDebugKey(SOURCE_DEBUG_KEY)
                .setPublisherType(publisherType)
                .setRegistrant(registrant)
                .setDebugJoinKey(debugJoinKey)
                .build();
    }

    private static Source createSourceForAdIdDebugKeyTests(
            int publisherType,
            boolean adIdPermission,
            boolean arDebugPermission,
            Uri registrant,
            String platformAdId,
            String debugAdId) {
        return getMinimalValidSourceBuilder()
                .setId(SOURCE_ID)
                .setArDebugPermission(arDebugPermission)
                .setAdIdPermission(adIdPermission)
                .setDebugKey(SOURCE_DEBUG_KEY)
                .setPublisherType(publisherType)
                .setRegistrant(registrant)
                .setPlatformAdId(platformAdId)
                .setDebugAdId(debugAdId)
                .build();
    }

    private static Trigger createTriggerForAdIdDebugKeyTests(
            int destinationType,
            boolean adIdPermission,
            boolean arDebugPermission,
            Uri registrant,
            String platformAdId,
            String debugAdId) {
        return getValidTriggerBuilder()
                .setId(TRIGGER_ID)
                .setEventTriggers(EVENT_TRIGGERS)
                .setArDebugPermission(arDebugPermission)
                .setAdIdPermission(adIdPermission)
                .setRegistrant(registrant)
                .setDestinationType(destinationType)
                .setDebugKey(TRIGGER_DEBUG_KEY)
                .setPlatformAdId(platformAdId)
                .setDebugAdId(debugAdId)
                .build();
    }

    private static List<Uri> getNullableUriList(Uri uri) {
        return uri == null ? null : List.of(uri);
    }
}
