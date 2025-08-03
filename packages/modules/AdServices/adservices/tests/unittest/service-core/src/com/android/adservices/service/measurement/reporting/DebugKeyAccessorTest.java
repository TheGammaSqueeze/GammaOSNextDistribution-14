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

package com.android.adservices.service.measurement.reporting;

import static com.android.adservices.service.measurement.SourceFixture.ValidSourceParams;
import static com.android.adservices.service.measurement.SourceFixture.getMinimalValidSourceBuilder;
import static com.android.adservices.service.measurement.TriggerFixture.ValidTriggerParams;
import static com.android.adservices.service.measurement.TriggerFixture.getValidTriggerBuilder;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_WEB;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;
import android.util.Pair;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.util.AdIdEncryption;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MsmtAdIdMatchForDebugKeysStats;
import com.android.adservices.service.stats.MsmtDebugKeysMatchStats;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;


/** Unit tests for {@link DebugKeyAccessor} */
@RunWith(MockitoJUnitRunner.class)
public class DebugKeyAccessorTest {

    public static final String TRIGGER_ID = "triggerId1";
    public static final long TRIGGER_TIME = 234324L;
    private static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(111111L);
    private static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(222222L);
    private static final long DEFAULT_JOIN_KEY_HASH_LIMIT = 100;
    private static final long DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT = 5;
    private static final String PARENT_SOURCE_ID = "parentSourceId";
    private static final String TEST_ACTUAL_AD_ID_1 = "12345678-1234-1234-1234-123456789012";
    private static final String TEST_ACTUAL_AD_ID_2 = "22345678-1234-1234-1234-123456789012";
    private static final String TEST_SHA_ENCRYPTED_AD_ID_1 =
            AdIdEncryption.encryptAdIdAndEnrollmentSha256(
                    TEST_ACTUAL_AD_ID_1, ValidTriggerParams.ENROLLMENT_ID);
    private static final String TEST_SHA_ENCRYPTED_AD_ID_2 =
            AdIdEncryption.encryptAdIdAndEnrollmentSha256(
                    TEST_ACTUAL_AD_ID_2, ValidTriggerParams.ENROLLMENT_ID);

    @Mock private Flags mFlags;
    @Mock private AdServicesLogger mAdServicesLogger;

    @Mock private IMeasurementDao mMeasurementDao;
    private DebugKeyAccessor mDebugKeyAccessor;

    @Before
    public void setup() throws DatastoreException {
        mDebugKeyAccessor = new DebugKeyAccessor(mFlags, mAdServicesLogger, mMeasurementDao);
        when(mFlags.getMeasurementDebugJoinKeyHashLimit()).thenReturn(DEFAULT_JOIN_KEY_HASH_LIMIT);
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
                .thenReturn(
                        ValidSourceParams.ENROLLMENT_ID + "," + ValidTriggerParams.ENROLLMENT_ID);
        when(mFlags.getMeasurementPlatformDebugAdIdMatchingLimit())
                .thenReturn(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT);
        when(mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist()).thenReturn("");
    }

    @Test
    public void getDebugKeys_appToAppWithAdIdPermission_debugKeysPresent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appToAppNoAdIdPermission_debugKeysAbsent() throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        false,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appToAppNoAdIdPermissionWithJoinKeys_debugKeysAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appToAppWithSourceAdId_sourceDebugKeyPresent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appToAppWithTriggerAdId_triggerDebugKeyPresent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        false,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webToWebWithSameRegistrant_debugKeysPresent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webToWebNoJoinKeysAndDifferentRegistrants_debugKeysAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        Uri.parse("https://com.registrant1"),
                        null,
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        Uri.parse("https://com.registrant2"),
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webToWebDiffJoinKeysSameRegFalseArDebug_debugKeysAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key1",
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key2",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webToWebSameJoinKeysAndDifferentRegistrants_debugKeysPresent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        Uri.parse("https://com.registrant1"),
                        "debug-join-key",
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        Uri.parse("https://com.registrant2"),
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_WEB)
                        .setMatched(true)
                        .setDebugJoinKeyHashedValue(54L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .setSourceRegistrant(source.getRegistrant().toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeys_webToWebOnlySourceJoinKeyAndDifferentRegistrants_debugKeysAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        Uri.parse("https://com.registrant1"),
                        null,
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        Uri.parse("https://com.registrant2"),
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webToWebDiffJoinKeysAndDifferentRegistrants_debugKeysAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        Uri.parse("https://com.registrant1"),
                        "debug-join-key1",
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        Uri.parse("https://com.registrant2"),
                        "debug-join-key2",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_WEB)
                        .setMatched(false)
                        .setDebugJoinKeyHashedValue(0L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .setSourceRegistrant(source.getRegistrant().toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeys_webToWebSameRegistrantWithArDebugOnSource_sourceDebugKeysPresent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appToWebNoJoinKeys_debugKeysAbsent() throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appToWebJoinKeysMatch_debugKeysPresent() throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(true)
                        .setDebugJoinKeyHashedValue(54L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeys_appToWebOnlyTriggerJoinKeyProvided_debugKeysAbsent()
            throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appToWebJoinKeysMismatch_debugKeysAbsent() throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key1",
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key2",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(false)
                        .setDebugJoinKeyHashedValue(0L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeys_webToAppNoJoinKeys_debugKeysAbsent() throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webToAppJoinKeysMatch_debugKeysPresent() throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(true)
                        .setDebugJoinKeyHashedValue(54L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeys_webToAppOnlySourceJoinKeyProvided_debugKeysAbsent()
            throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webToAppJoinKeysMatchNotAllowListed_debugKeysAbsent()
            throws DatastoreException {
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist()).thenReturn("");
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webToAppJoinKeysMismatch_debugKeysAbsent() throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key1",
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key2",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(false)
                        .setDebugJoinKeyHashedValue(0L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeys_webToWebNotAllowListedDiffRegJoinKeysMatch_debugKeysAbsent()
            throws DatastoreException {
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
                .thenReturn("some_random_enrollment1,some_random_enrollment2");
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        Uri.parse("https://com.registrant1"),
                        "debug-join-key",
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        Uri.parse("https://com.registrant2"),
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appToWebJoinKeysMatchNotAllowListed_debugKeysAbsent()
            throws DatastoreException {
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
                .thenReturn("some_random_enrollment1,some_random_enrollment2");
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_noSourceTriggerAdIdPermission_triggerDebugKeyPresent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(null, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_noSourceTriggerNoAdIdPermission_triggerDebugKeyAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(null, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_noSourceTriggerArdebugPermission_triggerDebugKeyPresent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(null, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_noSourceTriggerNoArdebugPermission_triggerDebugKeyAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(null, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToAppWithAdIdPermission_debugKeysPresent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToAppBothNoAdIdPermission_debugKeysAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        false,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToAppNoTriggerAdId_debugKeysAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToAppNoSourceAdId_sourceDebugKeyAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        false,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToAppBothNoAdIdWithJoinKeys_debugKeysAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_webToWebWithSameRegistrant_debugKeysPresent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_webToWebTriggerNoArDebugPermission_debugKeysAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void
            getDebugKeysForVerbose_webToWebSameJoinKeysAndDifferentRegistrants_debugKeysPresent()
                    throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        Uri.parse("https://com.registrant1"),
                        "debug-join-key",
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        Uri.parse("https://com.registrant2"),
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_WEB)
                        .setMatched(true)
                        .setDebugJoinKeyHashedValue(54L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .setSourceRegistrant(source.getRegistrant().toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_webToWebNoJoinKeyDiffRegistrants_sourceDebugKeyAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        Uri.parse("https://com.registrant1"),
                        null,
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        Uri.parse("https://com.registrant2"),
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void
            getDebugKeysForVerbose_webToWebDiffJoinKeysDifferentRegistrants_sourceDebugKeyAbsent()
                    throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        Uri.parse("https://com.registrant1"),
                        "debug-join-key1",
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        Uri.parse("https://com.registrant2"),
                        "debug-join-key2",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_WEB)
                        .setMatched(false)
                        .setDebugJoinKeyHashedValue(0L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .setSourceRegistrant(source.getRegistrant().toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_webToWebSameRegistrantWithArDebug_debugKeysPresent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void
            getDebugKeysForVerbose_webToWebNotAllowListDiffRegJoinKeysMatch_sourceDebugKeyAbsent()
                    throws DatastoreException {
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
                .thenReturn("some_random_enrollment1,some_random_enrollment2");
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        Uri.parse("https://com.registrant1"),
                        "debug-join-key",
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        Uri.parse("https://com.registrant2"),
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToWebTriggerNoArDebugPermission_debugKeysAbsent()
            throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToWebNoJoinKeys_sourceDebugKeysAbsent()
            throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToWebJoinKeysMatch_debugKeysPresent()
            throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(true)
                        .setDebugJoinKeyHashedValue(54L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_appToWebNoSourceJoinKey_sourceDebugKeyAbsent()
            throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToWebJoinKeysMismatch_sourceDebugKeyAbsent()
            throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key1",
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key2",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(false)
                        .setDebugJoinKeyHashedValue(0L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_appToWebJoinKeysMatchNotAllowListed_sourceDebugKeyAbsent()
            throws DatastoreException {
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
                .thenReturn("some_random_enrollment1,some_random_enrollment2");
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_webToAppTriggerNoAdid_debugKeysAbsent()
            throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_webToAppNoJoinKeys_sourceDebugKeyAbsent()
            throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_webToAppJoinKeysMatch_debugKeysPresent()
            throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(true)
                        .setDebugJoinKeyHashedValue(54L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_webToAppJoinKeysMatchNotAllowListed_sourceDebugKeyAbsent()
            throws DatastoreException {
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist()).thenReturn("");
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_webToAppJoinKeysMismatch_sourceDebugKeyAbsent()
            throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key1",
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key2",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(false)
                        .setDebugJoinKeyHashedValue(0L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeys_adIdMatching_appToWeb_noAdIds_debugKeysAbsent()
            throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementAdIdMatchForDebugKeysStats(any());
    }

    @Test
    public void getDebugKeys_adIdMatching_appToWeb_nullPlatformAdId_debugKeysAbsent_logsMetric()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(false)
                        .setNumUniqueAdIds(1L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeys_adIdMatching_appToWeb_matchingAdIds_debugKeysPresent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(true)
                        .setNumUniqueAdIds(1L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeys_encryptedAdIdMatching_appToWeb_matchingAdIds_debugKeysPresent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(true)
                        .setNumUniqueAdIds(1L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeys_adIdMatching_appToWeb_nonMatchingAdIds_debugKeysAbsent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(2L);

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_2);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(false)
                        .setNumUniqueAdIds(2L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeys_adIdMatching_appToWeb_failedMatch_doesNotMatchJoinKeys()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        "test-debug-key",
                        TEST_ACTUAL_AD_ID_1,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "test-debug-key",
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_2);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        // The AdID matching attempt happens first and fails, so the debug join key matching does
        // not occur.
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(false)
                        .setNumUniqueAdIds(1L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_adIdMatching_appToWeb_blockedEnrollment_debugKeysAbsent()
            throws DatastoreException {
        when(mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist())
                .thenReturn(ValidTriggerParams.ENROLLMENT_ID);

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementAdIdMatchForDebugKeysStats(any());
    }

    @Test
    public void getDebugKeys_adIdMatching_appToWeb_allEnrollmentsBlocked_debugKeysAbsent()
            throws DatastoreException {
        when(mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist()).thenReturn("*");

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementAdIdMatchForDebugKeysStats(any());
    }

    @Test
    public void getDebugKeys_adIdMatching_appToWeb_uniqueAdIdLimitReached_debugKeysAbsent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any()))
                .thenReturn(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT);

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(false)
                        .setNumUniqueAdIds(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeys_adIdMatching_webToApp_noAdIds_debugKeysAbsent()
            throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementAdIdMatchForDebugKeysStats(any());
    }

    @Test
    public void getDebugKeys_adIdMatching_webToApp_nullPlatformAdId_debugKeysAbsent_logsMetric()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(false)
                        .setNumUniqueAdIds(1L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeys_adIdMatching_webToApp_matchingAdIds_debugKeysPresent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(true)
                        .setNumUniqueAdIds(1L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeys_encryptedAdIdMatching_webToApp_matchingAdIds_debugKeysPresent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(true)
                        .setNumUniqueAdIds(1L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeys_adIdMatching_webToApp_nonMatchingAdIds_debugKeysAbsent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(2L);

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_2,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(false)
                        .setNumUniqueAdIds(2L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeys_adIdMatching_webToApp_failedMatch_doesNotMatchJoinKeys()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(2L);

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "test-debug-key",
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        "test-debug-key",
                        TEST_ACTUAL_AD_ID_2,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        // The AdID matching attempt happens first and fails, so the debug join key matching does
        // not occur.
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(false)
                        .setNumUniqueAdIds(2L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_adIdMatching_webToApp_blockedEnrollment_debugKeysAbsent()
            throws DatastoreException {
        when(mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist())
                .thenReturn(ValidSourceParams.ENROLLMENT_ID);

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementAdIdMatchForDebugKeysStats(any());
    }

    @Test
    public void getDebugKeys_adIdMatching_webToApp_allEnrollmentsBlocked_debugKeysAbsent()
            throws DatastoreException {
        when(mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist()).thenReturn("*");

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementAdIdMatchForDebugKeysStats(any());
    }

    @Test
    public void getDebugKeys_adIdMatching_webToApp_uniqueAdIdLimitReached_debugKeysAbsent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any()))
                .thenReturn(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT);

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(false)
                        .setNumUniqueAdIds(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_adIdAppToWeb_noAdIds_sourceDebugKeyAbsent()
            throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementAdIdMatchForDebugKeysStats(any());
    }

    @Test
    public void
            getDebugKeysForVerbose_adIdAppToWeb_nullPlatformAdId_sourceDebugKeyAbsent_logsMetric()
                    throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(false)
                        .setNumUniqueAdIds(1L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_adIdAppToWeb_matchingAdIds_sourceDebugKeyPresent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(true)
                        .setNumUniqueAdIds(1L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_encryptedAdIdAppToWeb_matchingAdIds_sourceDebugKeyPresent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(true)
                        .setNumUniqueAdIds(1L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_adIdAppToWeb_nonMatchingAdIds_sourceDebugKeyAbsent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(2L);

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_2);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(false)
                        .setNumUniqueAdIds(2L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_adIdAppToWeb_failedMatch_sourceDebugKeyAbsent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(2L);

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        "test-debug-key",
                        TEST_ACTUAL_AD_ID_1,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "test-debug-key",
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_2);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        // The AdID matching attempt happens first and fails, so the debug join key matching does
        // not occur.
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(false)
                        .setNumUniqueAdIds(2L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_adIdAppToWeb_blockedEnrollment_sourceDebugKeyAbsent()
            throws DatastoreException {
        when(mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist())
                .thenReturn(ValidTriggerParams.ENROLLMENT_ID);

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementAdIdMatchForDebugKeysStats(any());
    }

    @Test
    public void getDebugKeysForVerbose_adIdAppToWeb_allEnrollmentsBlocked_sourceDebugKeyAbsent()
            throws DatastoreException {
        when(mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist()).thenReturn("*");

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementAdIdMatchForDebugKeysStats(any());
    }

    @Test
    public void getDebugKeysForVerbose_adIdAppToWeb_uniqueAdIdLimitReached_sourceDebugKeyAbsent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any()))
                .thenReturn(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT);

        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidSourceParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(false)
                        .setNumUniqueAdIds(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_adIdWebToApp_noAdIds_sourceDebugKeyAbsent()
            throws DatastoreException {
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementAdIdMatchForDebugKeysStats(any());
    }

    @Test
    public void
            getDebugKeysForVerbose_adIdWebToApp_nullPlatformAdId_sourceDebugKeyAbsent_logsMetric()
                    throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(false)
                        .setNumUniqueAdIds(1L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_adIdWebToApp_matchingAdIds_sourceDebugKeyPresent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(true)
                        .setNumUniqueAdIds(1L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_encryptedAdIdWebToApp_matchingAdIds_sourceDebugKeyPresent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(true)
                        .setNumUniqueAdIds(1L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_adIdWebToApp_nonMatchingAdIds_sourceDebugKeyAbsent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(2L);

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_2,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(false)
                        .setNumUniqueAdIds(2L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_adIdWebToApp_failedMatch_sourceDebugKeyAbsent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(2L);

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "test-debug-key",
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        "test-debug-key",
                        TEST_ACTUAL_AD_ID_2,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        // The AdID matching attempt happens first and fails, so the debug join key matching does
        // not occur.
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(false)
                        .setNumUniqueAdIds(2L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_adIdWebToApp_blockedEnrollment_sourceDebugKeyAbsent()
            throws DatastoreException {
        when(mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist())
                .thenReturn(ValidSourceParams.ENROLLMENT_ID);

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementAdIdMatchForDebugKeysStats(any());
    }

    @Test
    public void getDebugKeysForVerbose_adIdWebToApp_allEnrollmentsBlocked_sourceDebugKeyAbsent()
            throws DatastoreException {
        when(mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist()).thenReturn("*");

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementAdIdMatchForDebugKeysStats(any());
    }

    @Test
    public void getDebugKeysForVerbose_adIdWebToApp_uniqueAdIdLimitReached_sourceDebugKeyAbsent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any()))
                .thenReturn(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT);

        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(false)
                        .setNumUniqueAdIds(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeys_xnaAppToAppWithAdIdPermission_debugKeysPresent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Source derivedSource =
                Source.Builder.from(
                                createSource(
                                        EventSurfaceType.APP,
                                        true,
                                        false,
                                        ValidSourceParams.REGISTRANT,
                                        null,
                                        null,
                                        null))
                        .setParentId(PARENT_SOURCE_ID)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(derivedSource, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_xnaAppToAppNoAdIdPermission_debugKeysAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Source derivedSource =
                Source.Builder.from(
                                createSource(
                                        EventSurfaceType.APP,
                                        false,
                                        false,
                                        ValidSourceParams.REGISTRANT,
                                        null,
                                        null,
                                        null))
                        .setParentId(PARENT_SOURCE_ID)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(derivedSource, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_xnaWebToWebWithSameRegistrant_debugKeysPresent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Source derivedSource =
                Source.Builder.from(
                                createSource(
                                        EventSurfaceType.WEB,
                                        false,
                                        true,
                                        ValidSourceParams.REGISTRANT,
                                        null,
                                        null,
                                        null))
                        .setParentId(PARENT_SOURCE_ID)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(derivedSource, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_xnaWebToWebSameJoinKeysAndDifferentRegistrants_debugKeysAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        Uri.parse("https://com.registrant1"),
                        "debug-join-key",
                        null,
                        null);
        Source source =
                Source.Builder.from(
                                createSource(
                                        EventSurfaceType.WEB,
                                        false,
                                        false,
                                        Uri.parse("https://com.registrant2"),
                                        "debug-join-key",
                                        null,
                                        null))
                        .setParentId(PARENT_SOURCE_ID)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
    }

    @Test
    public void getDebugKeys_xnaAppToWebJoinKeysMatch_debugKeysAbsent() throws DatastoreException {
        Source source =
                Source.Builder.from(
                                createSource(
                                        EventSurfaceType.APP,
                                        false,
                                        true,
                                        ValidSourceParams.REGISTRANT,
                                        "debug-join-key",
                                        null,
                                        null))
                        .setParentId(PARENT_SOURCE_ID)
                        .build();
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
    }

    @Test
    public void getDebugKeys_xnaWebToAppJoinKeysMatch_debugKeysAbsent() throws DatastoreException {
        Source source =
                Source.Builder.from(
                                createSource(
                                        EventSurfaceType.WEB,
                                        false,
                                        false,
                                        ValidSourceParams.REGISTRANT,
                                        "debug-join-key",
                                        null,
                                        null))
                        .setParentId(PARENT_SOURCE_ID)
                        .build();
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
    }

    @Test
    public void getDebugKeys_adIdMatching_xnaAppToWeb_matchingAdIds_debugKeysPresent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                Source.Builder.from(
                                createSource(
                                        EventSurfaceType.APP,
                                        true,
                                        false,
                                        ValidSourceParams.REGISTRANT,
                                        null,
                                        TEST_ACTUAL_AD_ID_1,
                                        null))
                        .setParentId(PARENT_SOURCE_ID)
                        .build();
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(true)
                        .setNumUniqueAdIds(1L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeys_adIdMatching_xnaWebToApp_matchingAdIds_debugKeysAbsent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                Source.Builder.from(
                                createSource(
                                        EventSurfaceType.WEB,
                                        false,
                                        true,
                                        ValidSourceParams.REGISTRANT,
                                        null,
                                        null,
                                        TEST_ACTUAL_AD_ID_1))
                        .setParentId(PARENT_SOURCE_ID)
                        .build();
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
    }

    @Test
    public void getDebugKeysForVerbose_xnaAppToAppWithAdIdPermission_debugKeysPresent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Source source =
                Source.Builder.from(
                                createSource(
                                        EventSurfaceType.APP,
                                        true,
                                        false,
                                        ValidSourceParams.REGISTRANT,
                                        null,
                                        null,
                                        null))
                        .setParentId(PARENT_SOURCE_ID)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_xnaAppToWeb_matchingAdIds_debugKeysPresent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                Source.Builder.from(
                                createSource(
                                        EventSurfaceType.APP,
                                        true,
                                        false,
                                        ValidSourceParams.REGISTRANT,
                                        null,
                                        TEST_ACTUAL_AD_ID_1,
                                        null))
                        .setParentId(PARENT_SOURCE_ID)
                        .build();
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        TEST_SHA_ENCRYPTED_AD_ID_1);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(true)
                        .setNumUniqueAdIds(1L)
                        .setNumUniqueAdIdsLimit(DEFAULT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT)
                        .setSourceRegistrant(ValidSourceParams.REGISTRANT.toString())
                        .build();
        verify(mAdServicesLogger).logMeasurementAdIdMatchForDebugKeysStats(eq(stats));
    }

    @Test
    public void getDebugKeysForVerbose_xnaAppToWebJoinKeysMatch_sourceDebugKeysAbsent()
            throws DatastoreException {
        Source source =
                Source.Builder.from(
                                createSource(
                                        EventSurfaceType.APP,
                                        false,
                                        true,
                                        ValidSourceParams.REGISTRANT,
                                        "debug-join-key",
                                        null,
                                        null))
                        .setParentId(PARENT_SOURCE_ID)
                        .build();
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
    }

    @Test
    public void getDebugKeysForVerbose_xnaWebToApp_matchingAdIds_sourceDebugKeyAbsent()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(any())).thenReturn(1L);

        Source source =
                Source.Builder.from(
                                createSource(
                                        EventSurfaceType.WEB,
                                        false,
                                        true,
                                        ValidSourceParams.REGISTRANT,
                                        null,
                                        null,
                                        TEST_SHA_ENCRYPTED_AD_ID_1))
                        .setParentId(PARENT_SOURCE_ID)
                        .build();
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        TEST_ACTUAL_AD_ID_1,
                        null);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
    }

    @Test
    public void getDebugKeysForVerbose_xnaWebToAppJoinKeysMatch_sourceDebugKeysAbsent()
            throws DatastoreException {
        Source source =
                Source.Builder.from(
                                createSource(
                                        EventSurfaceType.WEB,
                                        false,
                                        false,
                                        ValidSourceParams.REGISTRANT,
                                        "debug-join-key",
                                        null,
                                        null))
                        .setParentId(PARENT_SOURCE_ID)
                        .build();
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key",
                        null,
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
    }

    @Test
    public void getDebugKeysForVerbose_xnaWebToWebWithSameRegistrant_debugKeysPresent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        null,
                        null,
                        null);
        Source source =
                Source.Builder.from(
                                createSource(
                                        EventSurfaceType.WEB,
                                        false,
                                        true,
                                        ValidSourceParams.REGISTRANT,
                                        null,
                                        null,
                                        null))
                        .setParentId(PARENT_SOURCE_ID)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_xnaW2WSameJoinKeysAndDiffRegistrants_sourceDebugKeysAbsent()
            throws DatastoreException {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        Uri.parse("https://com.registrant1"),
                        "debug-join-key",
                        null,
                        null);
        Source source =
                Source.Builder.from(
                                createSource(
                                        EventSurfaceType.WEB,
                                        false,
                                        false,
                                        Uri.parse("https://com.registrant2"),
                                        "debug-join-key",
                                        null,
                                        null))
                        .setParentId(PARENT_SOURCE_ID)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
    }

    private static Trigger createTrigger(
            int destinationType,
            boolean adIdPermission,
            boolean arDebugPermission,
            Uri registrant,
            String debugJoinKey,
            String platformAdId,
            String debugAdId) {
        return getValidTriggerBuilder()
                .setId(TRIGGER_ID)
                .setArDebugPermission(arDebugPermission)
                .setAdIdPermission(adIdPermission)
                .setRegistrant(registrant)
                .setDestinationType(destinationType)
                .setDebugKey(TRIGGER_DEBUG_KEY)
                .setDebugJoinKey(debugJoinKey)
                .setPlatformAdId(platformAdId)
                .setDebugAdId(debugAdId)
                .build();
    }

    private static Source createSource(
            int publisherType,
            boolean adIdPermission,
            boolean arDebugPermission,
            Uri registrant,
            String debugJoinKey,
            String platformAdId,
            String debugAdId) {
        return getMinimalValidSourceBuilder()
                .setArDebugPermission(arDebugPermission)
                .setAdIdPermission(adIdPermission)
                .setDebugKey(SOURCE_DEBUG_KEY)
                .setPublisherType(publisherType)
                .setRegistrant(registrant)
                .setDebugJoinKey(debugJoinKey)
                .setPlatformAdId(platformAdId)
                .setDebugAdId(debugAdId)
                .build();
    }
}
