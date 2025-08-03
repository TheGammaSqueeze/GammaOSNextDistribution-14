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

package com.android.adservices.data.measurement.migration;

import android.content.ContentValues;
import android.net.Uri;

import com.android.adservices.common.WebUtil;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.registration.AsyncRegistration;
import com.android.adservices.service.measurement.util.UnsignedLong;

import java.util.ArrayList;
import java.util.List;

public class ContentValueFixtures {

    public static final Uri REGISTRATION_ORIGIN =
            WebUtil.validUri("https://subdomain.example.test");

    public static final Uri AGGREGATION_COORDINATOR_ORIGIN =
            WebUtil.validUri("https://coordinator.example.test");

    public static class AsyncRegistrationValues {
        public static final String ID = "async_registration_id";
        public static final String REGISTRATION_URI = "android-app://com.registration";
        public static final String WEB_DESTINATION = "https://com.web.destination";
        public static final String OS_DESTINATION = "android-app://com.os.destination";
        public static final String VERIFIED_DESTINATION = "android-app://com.verified.destination";
        public static final String TOP_ORIGIN = "android-app://com.top.origin";
        public static final long REDIRECT = 2;
        public static final long INPUT_EVENT = 1;
        public static final String REGISTRANT = "android-app://com.registrant";
        public static final long SCHEDULED_TIME = 8640000000L;
        public static final long RETRY_COUNT = 4;
        public static final long LAST_PROCESSING_TIME = 8650000000L;
        public static final long TYPE = AsyncRegistration.RegistrationType.WEB_TRIGGER.ordinal();

        // Added in V3.
        public static final String ENROLLMENT_ID = "enrollment-id";
        public static final long REDIRECT_TYPE = 2;
        public static final long REDIRECT_COUNT = 10;
        public static final long SOURCE_TYPE = Source.SourceType.NAVIGATION.ordinal();
        public static final long REQUEST_TIME = 8660000000L;
        public static final long DEBUG_KEY_ALLOWED = 1;
        public static final long AD_ID_PERMISSION = 0;

        // Added in V6.
        public static final String REGISTRATION_ID = "registration_id";

        // Added in V13.
        public static final String PLATFORM_AD_ID = "sample_platform_ad_id";

        // Added in V24
        public static final String POST_BODY = "{\"ad_location\":\"bottom_right\"}";
    }

    public static class SourceValues {
        public static final String ID = "source_id";
        public static final long EVENT_ID = 123L;
        public static final String PUBLISHER = "android-app://com.publisher";
        public static final long PUBLISHER_TYPE = 5;
        public static final String APP_DESTINATION = "android-app://com.destination";
        public static final String ENROLLMENT_ID = "enrollment_id";
        public static final long EVENT_TIME = 8640000000L;
        public static final long EXPIRY_TIME = 8640000010L;
        public static final long PRIORITY = 100L;
        public static final long STATUS = Source.Status.MARKED_TO_DELETE;
        public static final String DEDUP_KEYS = "1001";
        public static final String SOURCE_TYPE = Source.SourceType.EVENT.toString();
        public static final String REGISTRANT = "android-app://com.registrant";
        public static final long ATTRIBUTION_MODE = Source.AttributionMode.FALSELY;
        public static final long INSTALL_ATTRIBUTION_WINDOW = 841839879274L;
        public static final long INSTALL_COOLDOWN_WINDOW = 8418398274L;
        public static final long IS_INSTALL_ATTRIBUTED = 1;
        public static final String FILTER_DATA = "test filter data";
        public static final String AGGREGATE_SOURCE_V2_AND_BELOW =
                "[{\"id\": \"campaignCounts\",\"key_piece\": \"0x159\"},"
                        + "{\"id\": \"geoValue\",\"key_piece\": \"0x5\"}]";
        public static final long AGGREGATE_CONTRIBUTIONS = 6;
        public static final String WEB_DESTINATION = "https://destination.com";
        public static final long DEBUG_KEY = 12345L;

        // Added in V3.
        public static final String AGGREGATE_SOURCE_V3 =
                "{\"geoValue\":\"0x5\",\"campaignCounts\":\"0x159\"}";
        public static final long DEBUG_REPORTING = 10;
        public static final long AD_ID_PERMISSION = 11;
        public static final long AR_DEBUG_PERMISSION = 12;

        // Added in V6.
        public static final String EVENT_REPORT_DEDUP_KEY = "1001";
        public static final String AGGREGATE_REPORT_DEDUP_KEY = "2002";
        public static final long EVENT_REPORT_WINDOW = 400000L;
        public static final long AGGREGATE_REPORT_WINDOW = 500000L;
        public static final String REGISTRATION_ID = "registration_id";
        public static final String SHARED_AGGREGATION_KEY = "shared_aggregation_key";
        public static final long INSTALL_TIME = 8660000000L;
        public static final String DEBUG_JOIN_KEY = "sample_debug_join_key";

        // Added in V12.
        public static final String TRIGGER_SPECS = "sample_trigger_specs";
        public static final int COARSE_EVENT_REPORT_DESTINATIONS = 1;

        // Added in V13.
        public static final String PLATFORM_AD_ID = "sample_platform_ad_id";
        public static final String DEBUG_AD_ID = "sample_debug_ad_id";

        // Added in V14
        public static final Uri REGISTRATION_ORIGIN = ContentValueFixtures.REGISTRATION_ORIGIN;

        // Added in V15
        public static final int MAX_BUCKET_INCREMENTS = 3;
        public static final String EVENT_ATTRIBUTION_STATUS =
                "{\"trigger_id\": 123,\"priority\": 10, \"value\": 2}";

        public static final String PRIVACY_PARAMETERS = "{\"flip_probability\":0.0023}";
        // Added in V19
        public static final int MAX_EVENT_LEVEL_REPORTS = 3;
        public static final String EVENT_REPORT_WINDOWS = "{'end_times': [12, 123]}";
        public static final long SHARED_DEBUG_KEY = 2345L;

        // Added in V22
        public static final String SHARED_FILTER_DATA_KEYS = "shared_filter_data_keys";

        // Added in V30
        public static final String TRIGGER_DATA_MATCHING = Source.TriggerDataMatching.EXACT.name();
    }

    public static class SourceDestinationValues {
        // SOURCE_ID can be referenced from SourceValues.ID
        public static final String DESTINATION = "https://destination.test";
        public static final int DESTINATION_TYPE = 1;
    }

    public static class TriggerValues {
        public static final String ID = "trigger_id";
        public static final String ATTRIBUTION_DESTINATION = "android-app://com.destination";
        public static final long DESTINATION_TYPE = EventSurfaceType.WEB;
        public static final String ENROLLMENT_ID = "enrollment_id";
        public static final long TRIGGER_TIME = 8630000000L;
        public static final String FILTERS_V2_AND_BELOW = "{\"id1\":[\"val11\",\"val12\"]}";
        public static final String EVENT_TRIGGERS_V2_AND_BELOW =
                "[{\"trigger_data\":2,\"filters\":"
                        + FILTERS_V2_AND_BELOW
                        + ",\"not_filters\":"
                        + FILTERS_V2_AND_BELOW
                        + "},"
                        + "{\"priority\":100}]";
        public static final long STATUS = Trigger.Status.MARKED_TO_DELETE;
        public static final String REGISTRANT = "android-app://com.registrant";
        public static final String AGGREGATE_TRIGGER_DATA_V2_AND_BELOW =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"key11\"],"
                        + "\"filters\":"
                        + FILTERS_V2_AND_BELOW
                        + ",\"not_filters\":"
                        + FILTERS_V2_AND_BELOW
                        + "}]";
        public static final String AGGREGATE_VALUES =
                "{" + "\"campaignCounts\":32768," + "\"geoValue\":1664" + "}";
        public static final long DEBUG_KEY = 27836L;

        // Added in V3
        public static final String NOT_FILTERS = "{\"id2\":[\"val21\",\"val22\"]}";
        public static final String FILTERS_V3 = "[" + FILTERS_V2_AND_BELOW + "]";
        public static final String EVENT_TRIGGERS_V3 =
                "[{\"trigger_data\":2,\"filters\":"
                        + FILTERS_V3
                        + ",\"not_filters\":"
                        + FILTERS_V3
                        + "},"
                        + "{\"priority\":100,\"trigger_data\":\"0\"}]";
        public static final String AGGREGATE_TRIGGER_DATA_V3 =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"key11\"],"
                        + "\"filters\":"
                        + FILTERS_V3
                        + ",\"not_filters\":"
                        + FILTERS_V3
                        + "}]";
        public static final long DEBUG_REPORTING = 10;
        public static final long AD_ID_PERMISSION = 11;
        public static final long AR_DEBUG_PERMISSION = 12;

        // Added in V6.
        public static final String ATTRIBUTION_CONFIG = "attribution_config";
        public static final String X_NETWORK_KEY_MAPPING = "x_network_key_mapping";
        public static final String AGGREGATABLE_DEDUPLICATION_KEYS =
                "aggregatable_deduplication_keys";
        public static final String DEBUG_JOIN_KEY = "sample_debug_join_key";

        // Added in V13.
        public static final String PLATFORM_AD_ID = "sample_platform_ad_id";
        public static final String DEBUG_AD_ID = "sample_debug_ad_id";

        // Added in V14
        public static final Uri REGISTRATION_ORIGIN = ContentValueFixtures.REGISTRATION_ORIGIN;

        // Added in V20.
        public static final Uri AGGREGATION_COORDINATOR_ORIGIN =
                ContentValueFixtures.AGGREGATION_COORDINATOR_ORIGIN;

        // Added in V32
        public static final String AGGREGATABLE_SOURCE_REGISTRATION_TIME =
                Trigger.SourceRegistrationTimeConfig.INCLUDE.name();
    }

    public static class AttributionValues {
        public static final String ID = "attribution_id";
        public static final String SOURCE_SITE = "android-app://com.source-site";
        public static final String SOURCE_ORIGIN = "android-app://com.source-origin";
        public static final String DESTINATION_SITE = "android-app://com.destination-site";
        public static final String DESTINATION_ORIGIN = "android-app://com.destination-origin";
        public static final String ENROLLMENT_ID = "enrollment_id";
        public static final long TRIGGER_TIME = 8630000000L;
        public static final String REGISTRANT = "android-app://com.registrant";

        // Added in V3.
        public static final String SOURCE_ID = "source_id";
        public static final String TRIGGER_ID = "trigger_id";

        // Added in V25
        public static final Uri REGISTRATION_ORIGIN = ContentValueFixtures.REGISTRATION_ORIGIN;

        // Added in V29
        public static final int SCOPE = 0;
    }

    public static class EventReportValues {
        public static final String ID = "event_report_id";
        public static final long SOURCE_ID_V2_AND_BELOW = 123L;
        public static final String ENROLLMENT_ID = "enrollment_id";
        public static final String ATTRIBUTION_DESTINATION = "android-app://com.destination";
        public static final long REPORT_TIME = 8640000000L;
        public static final long TRIGGER_DATA = 1;
        public static final long TRIGGER_PRIORITY = 100L;
        public static final long TRIGGER_DEDUP_KEY = 56;
        public static final long TRIGGER_TIME = 8630000000L;
        public static final long STATUS = EventReport.Status.MARKED_TO_DELETE;
        public static final String SOURCE_TYPE = Source.SourceType.EVENT.toString();
        public static final double RANDOMIZED_TRIGGER_RATE = 0.0000025F;

        // Added in V2
        public static final long SOURCE_DEBUG_KEY = 12345L;
        public static final long TRIGGER_DEBUG_KEY = 34567L;

        // Added in V3
        public static final long SOURCE_EVENT_ID = SOURCE_ID_V2_AND_BELOW;
        public static final String SOURCE_ID_V3 = "source_id";
        public static final String TRIGGER_ID = "trigger_id";
        public static final long DEBUG_REPORT_STATUS = 4;
        // Added in V14
        public static final Uri REGISTRATION_ORIGIN = ContentValueFixtures.REGISTRATION_ORIGIN;

        // Added in V23
        public static final String TRIGGER_SUMMARY_BUCKET = "1,3";

        // Added in V28
        public static final String TRIGGER_DEBUG_KEYS = "9223372036854775809,15";
    }

    public static class AggregateReportValues {
        public static final String ID = "aggregate_report_id";
        public static final String PUBLISHER = "android-app://com.publisher";
        public static final String ATTRIBUTION_DESTINATION = "android-app://com.destination";
        public static final long SOURCE_REGISTRATION_TIME = 8640000000L;
        public static final long SCHEDULED_REPORT_TIME = 8640000200L;
        public static final String ENROLLMENT_ID = "enrollment_id";
        public static final String DEBUG_CLEARTEXT_PAYLOAD = "payload";
        public static final long STATUS = AggregateReport.Status.MARKED_TO_DELETE;
        public static final String API_VERSION = "api_version";

        // Added in V2
        public static final long SOURCE_DEBUG_KEY = 12345L;
        public static final long TRIGGER_DEBUG_KEY = 34567L;

        // Added in V3.
        public static final String SOURCE_ID = "source_id";
        public static final String TRIGGER_ID = "trigger_id";
        public static final long DEBUG_REPORT_STATUS = 4;

        // Added in V9
        public static final UnsignedLong DEDUP_KEY = new UnsignedLong(12L);
        // Added in V14
        public static final Uri REGISTRATION_ORIGIN = ContentValueFixtures.REGISTRATION_ORIGIN;

        // Added in V20
        public static final Uri AGGREGATION_COORDINATOR_ORIGIN =
                ContentValueFixtures.AGGREGATION_COORDINATOR_ORIGIN;

        // Added in V27
        public static final int IS_FAKE_REPORT = 0;
    }

    public static class AggregateEncryptionKeyValues {
        public static final String ID = "aggregate_encryption_key_id";
        public static final String KEY_ID = "key_id";
        public static final String PUBLIC_KEY = "public_key";
        public static final long EXPIRY = 8640000000L;
        // Added in V20
        public static final Uri AGGREGATION_COORDINATOR_ORIGIN =
                ContentValueFixtures.AGGREGATION_COORDINATOR_ORIGIN;
    }

    public static class DebugReportValues {
        // Added in V3.
        public static final String ID = "debug_report_id";
        public static final String TYPE = "source-noised";
        public static final String BODY = "{\"source_event_id\":\"123\"}";
        public static final String ENROLLMENT_ID = "enrollment_id";
        public static final Uri REGISTRATION_ORIGIN = ContentValueFixtures.REGISTRATION_ORIGIN;
        public static final String REFERENCE_ID = "reference_id";
    }

    public static class XnaIgnoredSourcesValues {
        // Added in V6.
        public static final String SOURCE_ID = "source_id";
        public static final String ENROLLMENT_ID = "enrollment_id";
    }

    public static class KeyValueDataValues {
        // REGISTRATION_REDIRECT_COUNT - RR
        public static final String RR_KEY = "reg_key";
        public static final KeyValueData.DataType RR_DATATYPE =
                KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT;
        public static final String RR_VALUE = "value";

        // AGGREGATE_REPORT_RETRY_COUNT - AR
        public static final String AR_KEY = "agg_key";
        public static final KeyValueData.DataType AR_DATATYPE =
                KeyValueData.DataType.AGGREGATE_REPORT_RETRY_COUNT;
        public static final String AR_VALUE = "1";

        // EVENT_REPORT_RETRY_COUNT - ER
        public static final String ER_KEY = "event_key";
        public static final KeyValueData.DataType ER_DATATYPE =
                KeyValueData.DataType.EVENT_REPORT_RETRY_COUNT;
        public static final String ER_VALUE = "1";

        // DEBUG_REPORT_RETRY_COUNT - DR
        public static final String DEBUG_KEY = "debug_key";
        public static final KeyValueData.DataType DR_DATATYPE =
                KeyValueData.DataType.DEBUG_REPORT_RETRY_COUNT;
        public static final String DR_VALUE = "1";
    }

    public static ContentValues generateAsyncRegistrationContentValuesV1() {
        ContentValues asyncRegistration = new ContentValues();

        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.ID, AsyncRegistrationValues.ID);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI,
                AsyncRegistrationValues.REGISTRATION_URI);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.WEB_DESTINATION,
                AsyncRegistrationValues.WEB_DESTINATION);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.OS_DESTINATION,
                AsyncRegistrationValues.OS_DESTINATION);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.VERIFIED_DESTINATION,
                AsyncRegistrationValues.VERIFIED_DESTINATION);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN,
                AsyncRegistrationValues.TOP_ORIGIN);
        asyncRegistration.put(
                MeasurementTablesDeprecated.AsyncRegistration.REDIRECT,
                AsyncRegistrationValues.REDIRECT);
        asyncRegistration.put(
                MeasurementTablesDeprecated.AsyncRegistration.INPUT_EVENT,
                AsyncRegistrationValues.INPUT_EVENT);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRANT,
                AsyncRegistrationValues.REGISTRANT);
        asyncRegistration.put(
                MeasurementTablesDeprecated.AsyncRegistration.SCHEDULED_TIME,
                AsyncRegistrationValues.SCHEDULED_TIME);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.RETRY_COUNT,
                AsyncRegistrationValues.RETRY_COUNT);
        asyncRegistration.put(
                MeasurementTablesDeprecated.AsyncRegistration.LAST_PROCESSING_TIME,
                AsyncRegistrationValues.LAST_PROCESSING_TIME);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.TYPE, AsyncRegistrationValues.TYPE);

        return asyncRegistration;
    }

    public static ContentValues generateAsyncRegistrationContentValuesV2() {
        // No differences in async registration fields between V1 and V2.
        return generateAsyncRegistrationContentValuesV1();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV3() {
        ContentValues asyncRegistration = generateAsyncRegistrationContentValuesV2();

        // Remove columns.
        asyncRegistration.remove(MeasurementTablesDeprecated.AsyncRegistration.REDIRECT);
        asyncRegistration.remove(MeasurementTablesDeprecated.AsyncRegistration.INPUT_EVENT);
        asyncRegistration.remove(MeasurementTablesDeprecated.AsyncRegistration.SCHEDULED_TIME);

        // Add columns.
        asyncRegistration.put(
                MeasurementTablesDeprecated.AsyncRegistration.ENROLLMENT_ID,
                AsyncRegistrationValues.ENROLLMENT_ID);
        asyncRegistration.put(
                MeasurementTablesDeprecated.AsyncRegistration.REDIRECT_TYPE,
                AsyncRegistrationValues.REDIRECT_TYPE);
        asyncRegistration.put(
                MeasurementTablesDeprecated.AsyncRegistration.REDIRECT_COUNT,
                AsyncRegistrationValues.REDIRECT_COUNT);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE,
                AsyncRegistrationValues.SOURCE_TYPE);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.REQUEST_TIME,
                AsyncRegistrationValues.REQUEST_TIME);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.DEBUG_KEY_ALLOWED,
                AsyncRegistrationValues.DEBUG_KEY_ALLOWED);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.AD_ID_PERMISSION,
                AsyncRegistrationValues.AD_ID_PERMISSION);

        return asyncRegistration;
    }

    public static ContentValues generateAsyncRegistrationContentValuesV6() {
        ContentValues values = generateAsyncRegistrationContentValuesV3();
        values.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID,
                AsyncRegistrationValues.REGISTRATION_ID);
        return values;
    }

    public static ContentValues generateAsyncRegistrationContentValuesV7() {
        return generateAsyncRegistrationContentValuesV6();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV8() {
        return generateAsyncRegistrationContentValuesV7();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV9() {
        return generateAsyncRegistrationContentValuesV8();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV10() {
        return generateAsyncRegistrationContentValuesV9();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV11() {
        ContentValues asyncRegistration = generateAsyncRegistrationContentValuesV10();
        asyncRegistration.remove(MeasurementTablesDeprecated.AsyncRegistration.ENROLLMENT_ID);
        asyncRegistration.remove(MeasurementTablesDeprecated.AsyncRegistration.REDIRECT_TYPE);
        asyncRegistration.remove(MeasurementTablesDeprecated.AsyncRegistration.REDIRECT_COUNT);
        asyncRegistration.remove(
                MeasurementTablesDeprecated.AsyncRegistration.LAST_PROCESSING_TIME);
        return asyncRegistration;
    }

    public static ContentValues generateAsyncRegistrationContentValuesV12() {
        return generateAsyncRegistrationContentValuesV11();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV13() {
        ContentValues asyncRegistration = generateAsyncRegistrationContentValuesV12();
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.PLATFORM_AD_ID,
                AsyncRegistrationValues.PLATFORM_AD_ID);
        return asyncRegistration;
    }

    public static ContentValues generateAsyncRegistrationContentValuesV14() {
        return generateAsyncRegistrationContentValuesV13();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV15() {
        return generateAsyncRegistrationContentValuesV14();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV16() {
        return generateAsyncRegistrationContentValuesV15();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV17() {
        return generateAsyncRegistrationContentValuesV16();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV18() {
        return generateAsyncRegistrationContentValuesV17();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV19() {
        return generateAsyncRegistrationContentValuesV18();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV20() {
        return generateAsyncRegistrationContentValuesV19();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV21() {
        return generateAsyncRegistrationContentValuesV20();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV22() {
        return generateAsyncRegistrationContentValuesV21();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV23() {
        return generateAsyncRegistrationContentValuesV22();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV24() {
        ContentValues asyncRegistration = generateAsyncRegistrationContentValuesV23();
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.REQUEST_POST_BODY,
                AsyncRegistrationValues.POST_BODY);
        return asyncRegistration;
    }

    /**
     * Get content values for V31 migration
     *
     * @return ContentValues for AsyncRegistration table
     */
    public static ContentValues generateAsyncRegistrationContentValuesV31() {
        return generateAsyncRegistrationContentValuesV24();
    }

    public static ContentValues generateSourceContentValuesV1() {
        ContentValues source = new ContentValues();

        source.put(MeasurementTables.SourceContract.ID, SourceValues.ID);
        source.put(MeasurementTables.SourceContract.EVENT_ID, SourceValues.EVENT_ID);
        source.put(MeasurementTables.SourceContract.PUBLISHER, SourceValues.PUBLISHER);
        source.put(MeasurementTables.SourceContract.PUBLISHER_TYPE, SourceValues.PUBLISHER_TYPE);
        source.put(
                MeasurementTablesDeprecated.SourceContract.APP_DESTINATION,
                SourceValues.APP_DESTINATION);
        source.put(MeasurementTables.SourceContract.ENROLLMENT_ID, SourceValues.ENROLLMENT_ID);
        source.put(MeasurementTables.SourceContract.EVENT_TIME, SourceValues.EVENT_TIME);
        source.put(MeasurementTables.SourceContract.EXPIRY_TIME, SourceValues.EXPIRY_TIME);
        source.put(MeasurementTables.SourceContract.PRIORITY, SourceValues.PRIORITY);
        source.put(MeasurementTables.SourceContract.STATUS, SourceValues.STATUS);
        source.put(MeasurementTablesDeprecated.SourceContract.DEDUP_KEYS, SourceValues.DEDUP_KEYS);
        source.put(MeasurementTables.SourceContract.SOURCE_TYPE, SourceValues.SOURCE_TYPE);
        source.put(MeasurementTables.SourceContract.REGISTRANT, SourceValues.REGISTRANT);
        source.put(
                MeasurementTables.SourceContract.ATTRIBUTION_MODE, SourceValues.ATTRIBUTION_MODE);
        source.put(
                MeasurementTables.SourceContract.INSTALL_ATTRIBUTION_WINDOW,
                SourceValues.INSTALL_ATTRIBUTION_WINDOW);
        source.put(
                MeasurementTables.SourceContract.INSTALL_COOLDOWN_WINDOW,
                SourceValues.INSTALL_COOLDOWN_WINDOW);
        source.put(
                MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED,
                SourceValues.IS_INSTALL_ATTRIBUTED);
        source.put(MeasurementTables.SourceContract.FILTER_DATA, SourceValues.FILTER_DATA);
        source.put(
                MeasurementTables.SourceContract.AGGREGATE_SOURCE,
                SourceValues.AGGREGATE_SOURCE_V2_AND_BELOW);
        source.put(
                MeasurementTables.SourceContract.AGGREGATE_CONTRIBUTIONS,
                SourceValues.AGGREGATE_CONTRIBUTIONS);
        source.put(MeasurementTablesDeprecated.SourceContract.WEB_DESTINATION,
                SourceValues.WEB_DESTINATION);
        source.put(MeasurementTables.SourceContract.DEBUG_KEY, SourceValues.DEBUG_KEY);

        return source;
    }

    public static ContentValues generateSourceContentValuesV2() {
        // No differences in source fields between V1 and V2.
        return generateSourceContentValuesV1();
    }

    public static ContentValues generateSourceContentValuesV3() {
        ContentValues source = generateSourceContentValuesV2();

        // Update columns.
        source.put(
                MeasurementTables.SourceContract.AGGREGATE_SOURCE,
                SourceValues.AGGREGATE_SOURCE_V3);

        // Add columns.
        source.put(MeasurementTables.SourceContract.DEBUG_REPORTING, SourceValues.DEBUG_REPORTING);
        source.put(
                MeasurementTables.SourceContract.AD_ID_PERMISSION, SourceValues.AD_ID_PERMISSION);
        source.put(
                MeasurementTables.SourceContract.AR_DEBUG_PERMISSION,
                SourceValues.AR_DEBUG_PERMISSION);

        return source;
    }

    public static ContentValues generateSourceContentValuesV6() {
        ContentValues values = generateSourceContentValuesV3();

        // Rename dedup key column
        values.remove(MeasurementTablesDeprecated.SourceContract.DEDUP_KEYS);
        values.put(
                MeasurementTables.SourceContract.EVENT_REPORT_DEDUP_KEYS,
                SourceValues.EVENT_REPORT_DEDUP_KEY);

        // Add new columns
        values.put(
                MeasurementTables.SourceContract.AGGREGATE_REPORT_DEDUP_KEYS,
                SourceValues.AGGREGATE_REPORT_DEDUP_KEY);
        values.put(
                MeasurementTables.SourceContract.EVENT_REPORT_WINDOW,
                SourceValues.EVENT_REPORT_WINDOW);
        values.put(
                MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW,
                SourceValues.AGGREGATE_REPORT_WINDOW);
        values.put(MeasurementTables.SourceContract.REGISTRATION_ID, SourceValues.REGISTRATION_ID);
        values.put(
                MeasurementTables.SourceContract.SHARED_AGGREGATION_KEYS,
                SourceValues.SHARED_AGGREGATION_KEY);
        values.put(MeasurementTables.SourceContract.INSTALL_TIME, SourceValues.INSTALL_TIME);

        return values;
    }

    public static ContentValues generateSourceContentValuesV7() {
        return generateSourceContentValuesV6();
    }

    public static ContentValues generateSourceContentValuesV8() {
        ContentValues values = generateSourceContentValuesV7();
        values.put(MeasurementTables.SourceContract.DEBUG_JOIN_KEY, SourceValues.DEBUG_JOIN_KEY);
        return values;
    }

    public static ContentValues generateSourceContentValuesV9() {
        ContentValues values = generateSourceContentValuesV8();
        values.remove(MeasurementTablesDeprecated.SourceContract.APP_DESTINATION);
        values.remove(MeasurementTablesDeprecated.SourceContract.WEB_DESTINATION);
        return values;
    }

    public static ContentValues generateSourceContentValuesV10() {
        return generateSourceContentValuesV9();
    }

    public static ContentValues generateSourceContentValuesV11() {
        return generateSourceContentValuesV10();
    }

    public static ContentValues generateSourceContentValuesV12() {
        ContentValues values = generateSourceContentValuesV11();
        values.put(MeasurementTables.SourceContract.TRIGGER_SPECS, SourceValues.TRIGGER_SPECS);
        values.put(
                MeasurementTablesDeprecated.SourceContract.MAX_BUCKET_INCREMENTS,
                SourceValues.MAX_BUCKET_INCREMENTS);
        return values;
    }

    public static ContentValues generateSourceContentValuesV13() {
        ContentValues values = generateSourceContentValuesV12();
        values.put(MeasurementTables.SourceContract.PLATFORM_AD_ID, SourceValues.PLATFORM_AD_ID);
        values.put(MeasurementTables.SourceContract.DEBUG_AD_ID, SourceValues.DEBUG_AD_ID);
        return values;
    }

    public static ContentValues generateSourceContentValuesV14() {
        ContentValues values = generateSourceContentValuesV13();
        values.put(
                MeasurementTables.SourceContract.REGISTRATION_ORIGIN,
                SourceValues.REGISTRATION_ORIGIN.toString());
        return values;
    }

    public static ContentValues generateSourceContentValuesV15() {
        return generateSourceContentValuesV14();
    }

    public static ContentValues generateSourceContentValuesV16() {
        ContentValues values = generateSourceContentValuesV15();
        values.put(
                MeasurementTables.SourceContract.COARSE_EVENT_REPORT_DESTINATIONS,
                SourceValues.COARSE_EVENT_REPORT_DESTINATIONS);
        return values;
    }

    public static ContentValues generateSourceContentValuesV17() {
        ContentValues values = generateSourceContentValuesV16();
        return values;
    }

    public static ContentValues generateSourceContentValuesV18() {
        ContentValues values = generateSourceContentValuesV17();
        values.put(
                MeasurementTables.SourceContract.EVENT_ATTRIBUTION_STATUS,
                SourceValues.EVENT_ATTRIBUTION_STATUS);
        values.put(
                MeasurementTables.SourceContract.PRIVACY_PARAMETERS,
                SourceValues.PRIVACY_PARAMETERS);
        return values;
    }

    public static ContentValues generateSourceContentValuesV19() {
        ContentValues values = generateSourceContentValuesV18();
        values.remove(MeasurementTablesDeprecated.SourceContract.MAX_BUCKET_INCREMENTS);
        values.put(
                MeasurementTables.SourceContract.MAX_EVENT_LEVEL_REPORTS,
                SourceValues.MAX_EVENT_LEVEL_REPORTS);
        values.put(
                MeasurementTables.SourceContract.EVENT_REPORT_WINDOWS,
                SourceValues.EVENT_REPORT_WINDOWS);
        return values;
    }

    public static ContentValues generateSourceContentValuesV20() {
        return generateSourceContentValuesV19();
    }

    public static ContentValues generateSourceContentValuesV21() {
        ContentValues values = generateSourceContentValuesV20();
        values.put(
                MeasurementTables.SourceContract.SHARED_DEBUG_KEY, SourceValues.SHARED_DEBUG_KEY);
        return values;
    }

    public static ContentValues generateSourceContentValuesV22() {
        ContentValues values = generateSourceContentValuesV21();
        values.put(
                MeasurementTables.SourceContract.SHARED_FILTER_DATA_KEYS,
                SourceValues.SHARED_FILTER_DATA_KEYS);
        return values;
    }

    /** Returns content values for source version 29 */
    public static ContentValues generateSourceContentValuesV29() {
        return generateSourceContentValuesV22();
    }

    /** Returns content values for source version 30 */
    public static ContentValues generateSourceContentValuesV30() {
        ContentValues values = generateSourceContentValuesV29();
        values.put(
                MeasurementTables.SourceContract.TRIGGER_DATA_MATCHING,
                SourceValues.TRIGGER_DATA_MATCHING);
        return values;
    }

    public static ContentValues generateSourceDestinationContentValuesV9() {
        ContentValues sourceDestination = new ContentValues();

        sourceDestination.put(MeasurementTables.SourceDestination.SOURCE_ID, SourceValues.ID);
        sourceDestination.put(MeasurementTables.SourceDestination.DESTINATION,
                SourceDestinationValues.DESTINATION);
        sourceDestination.put(MeasurementTables.SourceDestination.DESTINATION_TYPE,
                SourceDestinationValues.DESTINATION_TYPE);

        return sourceDestination;
    }

    public static ContentValues generateSourceDestinationContentValuesV10() {
        return generateSourceDestinationContentValuesV9();
    }

    public static ContentValues generateSourceDestinationContentValuesV11() {
        return generateSourceDestinationContentValuesV10();
    }

    public static ContentValues generateSourceDestinationContentValuesV12() {
        return generateSourceDestinationContentValuesV11();
    }

    public static ContentValues generateSourceDestinationContentValuesV13() {
        return generateSourceDestinationContentValuesV12();
    }

    public static ContentValues generateSourceDestinationContentValuesV14() {
        return generateSourceDestinationContentValuesV13();
    }

    public static ContentValues generateSourceDestinationContentValuesV15() {
        return generateSourceDestinationContentValuesV14();
    }

    public static ContentValues generateSourceDestinationContentValuesV16() {
        return generateSourceDestinationContentValuesV15();
    }

    public static ContentValues generateSourceDestinationContentValuesV17() {
        return generateSourceDestinationContentValuesV16();
    }

    public static ContentValues generateSourceDestinationContentValuesV18() {
        return generateSourceDestinationContentValuesV17();
    }

    public static ContentValues generateSourceDestinationContentValuesV19() {
        return generateSourceDestinationContentValuesV18();
    }

    public static ContentValues generateSourceDestinationContentValuesV20() {
        return generateSourceDestinationContentValuesV19();
    }

    public static ContentValues generateSourceDestinationContentValuesV21() {
        return generateSourceDestinationContentValuesV20();
    }

    public static ContentValues generateSourceDestinationContentValuesV22() {
        return generateSourceDestinationContentValuesV21();
    }

    public static ContentValues generateTriggerContentValuesV1() {
        ContentValues trigger = new ContentValues();

        trigger.put(MeasurementTables.TriggerContract.ID, TriggerValues.ID);
        trigger.put(
                MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                TriggerValues.ATTRIBUTION_DESTINATION);
        trigger.put(
                MeasurementTables.TriggerContract.DESTINATION_TYPE, TriggerValues.DESTINATION_TYPE);
        trigger.put(MeasurementTables.TriggerContract.ENROLLMENT_ID, TriggerValues.ENROLLMENT_ID);
        trigger.put(MeasurementTables.TriggerContract.TRIGGER_TIME, TriggerValues.TRIGGER_TIME);
        trigger.put(
                MeasurementTables.TriggerContract.EVENT_TRIGGERS,
                TriggerValues.EVENT_TRIGGERS_V2_AND_BELOW);
        trigger.put(MeasurementTables.TriggerContract.STATUS, TriggerValues.STATUS);
        trigger.put(MeasurementTables.TriggerContract.REGISTRANT, TriggerValues.REGISTRANT);
        trigger.put(
                MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                TriggerValues.AGGREGATE_TRIGGER_DATA_V2_AND_BELOW);
        trigger.put(
                MeasurementTables.TriggerContract.AGGREGATE_VALUES, TriggerValues.AGGREGATE_VALUES);
        trigger.put(MeasurementTables.TriggerContract.FILTERS, TriggerValues.FILTERS_V2_AND_BELOW);
        trigger.put(MeasurementTables.TriggerContract.DEBUG_KEY, TriggerValues.DEBUG_KEY);

        return trigger;
    }

    public static ContentValues generateTriggerContentValuesV2() {
        // No differences in trigger fields between V1 and V2.
        return generateTriggerContentValuesV1();
    }

    public static ContentValues generateTriggerContentValuesV3() {
        // No differences in trigger fields between V1 and V2.
        ContentValues trigger = generateTriggerContentValuesV2();

        // Update values.
        trigger.put(
                MeasurementTables.TriggerContract.EVENT_TRIGGERS, TriggerValues.EVENT_TRIGGERS_V3);
        trigger.put(
                MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                TriggerValues.AGGREGATE_TRIGGER_DATA_V3);
        trigger.put(MeasurementTables.TriggerContract.FILTERS, TriggerValues.FILTERS_V3);

        // Add columns.
        trigger.put(MeasurementTables.TriggerContract.NOT_FILTERS, TriggerValues.NOT_FILTERS);
        trigger.put(
                MeasurementTables.TriggerContract.DEBUG_REPORTING, TriggerValues.DEBUG_REPORTING);
        trigger.put(
                MeasurementTables.TriggerContract.AD_ID_PERMISSION, TriggerValues.AD_ID_PERMISSION);
        trigger.put(
                MeasurementTables.TriggerContract.AR_DEBUG_PERMISSION,
                TriggerValues.AR_DEBUG_PERMISSION);

        return trigger;
    }

    public static ContentValues generateTriggerContentValuesV6() {
        ContentValues values = generateTriggerContentValuesV3();
        values.put(
                MeasurementTables.TriggerContract.ATTRIBUTION_CONFIG,
                TriggerValues.ATTRIBUTION_CONFIG);
        values.put(
                MeasurementTables.TriggerContract.X_NETWORK_KEY_MAPPING,
                TriggerValues.X_NETWORK_KEY_MAPPING);
        values.put(
                MeasurementTables.TriggerContract.AGGREGATABLE_DEDUPLICATION_KEYS,
                TriggerValues.AGGREGATABLE_DEDUPLICATION_KEYS);
        return values;
    }

    public static ContentValues generateTriggerContentValuesV7() {
        return generateTriggerContentValuesV6();
    }

    public static ContentValues generateTriggerContentValuesV8() {
        ContentValues values = generateTriggerContentValuesV7();
        values.put(MeasurementTables.TriggerContract.DEBUG_JOIN_KEY, TriggerValues.DEBUG_JOIN_KEY);
        return values;
    }

    public static ContentValues generateTriggerContentValuesV9() {
        return generateTriggerContentValuesV8();
    }

    public static ContentValues generateTriggerContentValuesV10() {
        return generateTriggerContentValuesV9();
    }

    public static ContentValues generateTriggerContentValuesV11() {
        return generateTriggerContentValuesV10();
    }

    public static ContentValues generateTriggerContentValuesV12() {
        return generateTriggerContentValuesV11();
    }

    public static ContentValues generateTriggerContentValuesV13() {
        ContentValues values = generateTriggerContentValuesV12();
        values.put(MeasurementTables.TriggerContract.PLATFORM_AD_ID, TriggerValues.PLATFORM_AD_ID);
        values.put(MeasurementTables.TriggerContract.DEBUG_AD_ID, TriggerValues.DEBUG_AD_ID);
        return values;
    }

    public static ContentValues generateTriggerContentValuesV14() {
        ContentValues values = generateTriggerContentValuesV13();
        values.put(
                MeasurementTables.TriggerContract.REGISTRATION_ORIGIN,
                TriggerValues.REGISTRATION_ORIGIN.toString());
        return values;
    }

    public static ContentValues generateTriggerContentValuesV15() {
        return generateTriggerContentValuesV14();
    }

    public static ContentValues generateTriggerContentValuesV16() {
        return generateTriggerContentValuesV15();
    }

    public static ContentValues generateTriggerContentValuesV17() {
        return generateTriggerContentValuesV16();
    }

    public static ContentValues generateTriggerContentValuesV18() {
        return generateTriggerContentValuesV17();
    }

    public static ContentValues generateTriggerContentValuesV19() {
        return generateTriggerContentValuesV18();
    }

    public static ContentValues generateTriggerContentValuesV20() {
        ContentValues values = generateTriggerContentValuesV19();
        values.put(
                MeasurementTables.TriggerContract.AGGREGATION_COORDINATOR_ORIGIN,
                TriggerValues.AGGREGATION_COORDINATOR_ORIGIN.toString());
        return values;
    }

    public static ContentValues generateTriggerContentValuesV21() {
        return generateTriggerContentValuesV20();
    }

    /** Get ContentValues for V31 */
    public static ContentValues generateTriggerContentValuesV31() {
        return generateTriggerContentValuesV21();
    }

    /** Get ContentValues for V32 */
    public static ContentValues generateTriggerContentValuesV32() {
        ContentValues values = generateTriggerContentValuesV20();
        values.put(
                MeasurementTables.TriggerContract.AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG,
                TriggerValues.AGGREGATABLE_SOURCE_REGISTRATION_TIME);

        return values;
    }

    public static ContentValues generateAttributionContentValuesV1() {
        ContentValues attribution = new ContentValues();

        attribution.put(MeasurementTables.AttributionContract.ID, AttributionValues.ID);
        attribution.put(
                MeasurementTables.AttributionContract.SOURCE_SITE, AttributionValues.SOURCE_SITE);
        attribution.put(
                MeasurementTables.AttributionContract.SOURCE_ORIGIN,
                AttributionValues.SOURCE_ORIGIN);
        attribution.put(
                MeasurementTables.AttributionContract.DESTINATION_SITE,
                AttributionValues.DESTINATION_SITE);
        attribution.put(
                MeasurementTables.AttributionContract.DESTINATION_ORIGIN,
                AttributionValues.DESTINATION_ORIGIN);
        attribution.put(
                MeasurementTables.AttributionContract.ENROLLMENT_ID,
                AttributionValues.ENROLLMENT_ID);
        attribution.put(
                MeasurementTables.AttributionContract.TRIGGER_TIME, AttributionValues.TRIGGER_TIME);
        attribution.put(
                MeasurementTables.AttributionContract.REGISTRANT, AttributionValues.REGISTRANT);

        return attribution;
    }

    public static ContentValues generateAttributionContentValuesV2() {
        // No differences in attribution fields between V1 and V2.
        return generateAttributionContentValuesV1();
    }

    public static ContentValues generateAttributionContentValuesV3() {
        ContentValues attribution = generateAttributionContentValuesV2();

        // Add columns.
        attribution.put(
                MeasurementTables.AttributionContract.SOURCE_ID, AttributionValues.SOURCE_ID);
        attribution.put(
                MeasurementTables.AttributionContract.TRIGGER_ID, AttributionValues.TRIGGER_ID);

        return attribution;
    }

    public static ContentValues generateAttributionContentValuesV6() {
        return generateAttributionContentValuesV3();
    }

    public static ContentValues generateAttributionContentValuesV7() {
        return generateAttributionContentValuesV6();
    }

    public static ContentValues generateAttributionContentValuesV8() {
        return generateAttributionContentValuesV7();
    }

    public static ContentValues generateAttributionContentValuesV9() {
        return generateAttributionContentValuesV7();
    }

    public static ContentValues generateAttributionContentValuesV10() {
        return generateAttributionContentValuesV9();
    }

    public static ContentValues generateAttributionContentValuesV11() {
        return generateAttributionContentValuesV10();
    }

    public static ContentValues generateAttributionContentValuesV12() {
        return generateAttributionContentValuesV11();
    }

    public static ContentValues generateAttributionContentValuesV13() {
        return generateAttributionContentValuesV12();
    }

    public static ContentValues generateAttributionContentValuesV14() {
        return generateAttributionContentValuesV13();
    }

    public static ContentValues generateAttributionContentValuesV15() {
        return generateAttributionContentValuesV14();
    }

    public static ContentValues generateAttributionContentValuesV16() {
        return generateAttributionContentValuesV15();
    }

    public static ContentValues generateAttributionContentValuesV17() {
        return generateAttributionContentValuesV16();
    }

    public static ContentValues generateAttributionContentValuesV24() {
        return generateAttributionContentValuesV17();
    }

    public static ContentValues generateAttributionContentValuesV25() {
        ContentValues values = generateAttributionContentValuesV24();
        values.put(
                MeasurementTables.AttributionContract.REGISTRATION_ORIGIN,
                AttributionValues.REGISTRATION_ORIGIN.toString());
        return values;
    }

    /** Return content values for an attribution record version 26 */
    public static ContentValues generateAttributionContentValuesV26() {
        return generateAttributionContentValuesV25();
    }

    /** Return content values for an attribution record version 28 */
    public static ContentValues generateAttributionContentValuesV28() {
        return generateAttributionContentValuesV26();
    }

    /** Return content values for an attribution record version 29 */
    public static ContentValues generateAttributionContentValuesV29() {
        ContentValues values = generateAttributionContentValuesV28();
        values.put(
                MeasurementTables.AttributionContract.SCOPE,
                AttributionValues.SCOPE);
        return values;
    }

    public static ContentValues generateEventReportContentValuesV1() {
        ContentValues eventReport = new ContentValues();

        eventReport.put(MeasurementTables.EventReportContract.ID, EventReportValues.ID);
        eventReport.put(
                MeasurementTables.EventReportContract.SOURCE_ID,
                EventReportValues.SOURCE_ID_V2_AND_BELOW);
        eventReport.put(
                MeasurementTables.EventReportContract.ENROLLMENT_ID,
                EventReportValues.ENROLLMENT_ID);
        eventReport.put(
                MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                EventReportValues.ATTRIBUTION_DESTINATION);
        eventReport.put(
                MeasurementTables.EventReportContract.REPORT_TIME, EventReportValues.REPORT_TIME);
        eventReport.put(
                MeasurementTables.EventReportContract.TRIGGER_DATA, EventReportValues.TRIGGER_DATA);
        eventReport.put(
                MeasurementTables.EventReportContract.TRIGGER_PRIORITY,
                EventReportValues.TRIGGER_PRIORITY);
        eventReport.put(
                MeasurementTables.EventReportContract.TRIGGER_DEDUP_KEY,
                EventReportValues.TRIGGER_DEDUP_KEY);
        eventReport.put(
                MeasurementTables.EventReportContract.TRIGGER_TIME, EventReportValues.TRIGGER_TIME);
        eventReport.put(MeasurementTables.EventReportContract.STATUS, EventReportValues.STATUS);
        eventReport.put(
                MeasurementTables.EventReportContract.SOURCE_TYPE, EventReportValues.SOURCE_TYPE);
        eventReport.put(
                MeasurementTables.EventReportContract.RANDOMIZED_TRIGGER_RATE,
                EventReportValues.RANDOMIZED_TRIGGER_RATE);

        return eventReport;
    }

    public static ContentValues generateEventReportContentValuesV2() {
        ContentValues eventReport = generateEventReportContentValuesV1();

        eventReport.put(
                MeasurementTables.EventReportContract.SOURCE_DEBUG_KEY,
                EventReportValues.SOURCE_DEBUG_KEY);
        eventReport.put(
                MeasurementTables.EventReportContract.TRIGGER_DEBUG_KEY,
                EventReportValues.TRIGGER_DEBUG_KEY);

        return eventReport;
    }

    public static ContentValues generateEventReportContentValuesV3() {
        ContentValues eventReport = generateEventReportContentValuesV2();

        // Update column.
        eventReport.put(
                MeasurementTables.EventReportContract.SOURCE_ID, EventReportValues.SOURCE_ID_V3);

        // Add columns.
        eventReport.put(
                MeasurementTables.EventReportContract.SOURCE_EVENT_ID,
                EventReportValues.SOURCE_EVENT_ID);
        eventReport.put(
                MeasurementTables.EventReportContract.TRIGGER_ID, EventReportValues.TRIGGER_ID);
        eventReport.put(
                MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS,
                EventReportValues.DEBUG_REPORT_STATUS);

        return eventReport;
    }

    public static ContentValues generateEventReportContentValuesV6() {
        return generateEventReportContentValuesV3();
    }

    public static ContentValues generateEventReportContentValuesV7() {
        return generateEventReportContentValuesV6();
    }

    public static ContentValues generateEventReportContentValuesV8() {
        return generateEventReportContentValuesV7();
    }

    public static ContentValues generateEventReportContentValuesV9() {
        return generateEventReportContentValuesV8();
    }

    public static ContentValues generateEventReportContentValuesV10() {
        return generateEventReportContentValuesV9();
    }

    public static ContentValues generateEventReportContentValuesV11() {
        return generateEventReportContentValuesV10();
    }

    public static ContentValues generateEventReportContentValuesV12() {
        return generateEventReportContentValuesV11();
    }

    public static ContentValues generateEventReportContentValuesV13() {
        return generateEventReportContentValuesV12();
    }

    public static ContentValues generateEventReportContentValuesV14() {
        ContentValues eventReport = generateEventReportContentValuesV13();

        eventReport.put(
                MeasurementTables.EventReportContract.REGISTRATION_ORIGIN,
                EventReportValues.REGISTRATION_ORIGIN.toString());
        return eventReport;
    }

    public static ContentValues generateEventReportContentValuesV15() {
        return generateEventReportContentValuesV14();
    }

    public static ContentValues generateEventReportContentValuesV16() {
        return generateEventReportContentValuesV15();
    }

    public static ContentValues generateEventReportContentValuesV17() {
        return generateEventReportContentValuesV16();
    }

    public static ContentValues generateEventReportContentValuesV18() {
        return generateEventReportContentValuesV17();
    }

    public static ContentValues generateEventReportContentValuesV19() {
        return generateEventReportContentValuesV18();
    }

    public static ContentValues generateEventReportContentValuesV20() {
        return generateEventReportContentValuesV19();
    }

    public static ContentValues generateEventReportContentValuesV21() {
        return generateEventReportContentValuesV20();
    }

    public static ContentValues generateEventReportContentValuesV22() {
        return generateEventReportContentValuesV21();
    }

    public static ContentValues generateEventReportContentValuesV23() {
        ContentValues eventReport = generateEventReportContentValuesV22();
        eventReport.put(
                MeasurementTables.EventReportContract.TRIGGER_SUMMARY_BUCKET,
                EventReportValues.TRIGGER_SUMMARY_BUCKET);
        return eventReport;
    }

    /** Returns content values for event report version 26 */
    public static ContentValues generateEventReportContentValuesV26() {
        return generateEventReportContentValuesV23();
    }

    /** Returns content values for event report version 27 */
    public static ContentValues generateEventReportContentValuesV27() {
        return generateEventReportContentValuesV26();
    }

    /** Returns content values for event report version 28 */
    public static ContentValues generateEventReportContentValuesV28() {
        ContentValues eventReport = generateEventReportContentValuesV27();
        eventReport.put(
                MeasurementTables.EventReportContract.TRIGGER_DEBUG_KEYS,
                EventReportValues.TRIGGER_DEBUG_KEYS);
        return eventReport;
    }

    public static ContentValues generateAggregateReportContentValuesV1() {
        ContentValues aggregateReport = new ContentValues();

        aggregateReport.put(MeasurementTables.AggregateReport.ID, AggregateReportValues.ID);
        aggregateReport.put(
                MeasurementTables.AggregateReport.PUBLISHER, AggregateReportValues.PUBLISHER);
        aggregateReport.put(
                MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                AggregateReportValues.ATTRIBUTION_DESTINATION);
        aggregateReport.put(
                MeasurementTables.AggregateReport.SOURCE_REGISTRATION_TIME,
                AggregateReportValues.SOURCE_REGISTRATION_TIME);
        aggregateReport.put(
                MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME,
                AggregateReportValues.SCHEDULED_REPORT_TIME);
        aggregateReport.put(
                MeasurementTables.AggregateReport.ENROLLMENT_ID,
                AggregateReportValues.ENROLLMENT_ID);
        aggregateReport.put(
                MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD,
                AggregateReportValues.DEBUG_CLEARTEXT_PAYLOAD);
        aggregateReport.put(MeasurementTables.AggregateReport.STATUS, AggregateReportValues.STATUS);
        aggregateReport.put(
                MeasurementTables.AggregateReport.API_VERSION, AggregateReportValues.API_VERSION);

        return aggregateReport;
    }

    public static ContentValues generateAggregateReportContentValuesV2() {
        ContentValues aggregateReport = generateAggregateReportContentValuesV1();

        aggregateReport.put(
                MeasurementTables.AggregateReport.SOURCE_DEBUG_KEY,
                AggregateReportValues.SOURCE_DEBUG_KEY);
        aggregateReport.put(
                MeasurementTables.AggregateReport.TRIGGER_DEBUG_KEY,
                AggregateReportValues.TRIGGER_DEBUG_KEY);

        return aggregateReport;
    }

    public static ContentValues generateAggregateReportContentValuesV3() {
        ContentValues aggregateReport = generateAggregateReportContentValuesV2();

        // Add columns.
        aggregateReport.put(
                MeasurementTables.AggregateReport.SOURCE_ID, AggregateReportValues.SOURCE_ID);
        aggregateReport.put(
                MeasurementTables.AggregateReport.TRIGGER_ID, AggregateReportValues.TRIGGER_ID);
        aggregateReport.put(
                MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS,
                AggregateReportValues.DEBUG_REPORT_STATUS);

        return aggregateReport;
    }

    public static ContentValues generateAggregateReportContentValuesV6() {
        return generateAggregateReportContentValuesV3();
    }

    public static ContentValues generateAggregateReportContentValuesV7() {
        return generateAggregateReportContentValuesV6();
    }

    public static ContentValues generateAggregateReportContentValuesV8() {
        return generateAggregateReportContentValuesV7();
    }

    public static ContentValues generateAggregateReportContentValuesV9() {
        return generateAggregateReportContentValuesV8();
    }

    public static ContentValues generateAggregateReportContentValuesV10() {
        ContentValues aggregateReport = generateAggregateReportContentValuesV9();

        // Add columns.
        aggregateReport.put(
                MeasurementTables.AggregateReport.DEDUP_KEY,
                AggregateReportValues.DEDUP_KEY.getValue());

        return aggregateReport;
    }

    public static ContentValues generateAggregateReportContentValuesV11() {
        return generateAggregateReportContentValuesV10();
    }

    public static ContentValues generateAggregateReportContentValuesV12() {
        return generateAggregateReportContentValuesV11();
    }

    public static ContentValues generateAggregateReportContentValuesV13() {
        return generateAggregateReportContentValuesV12();
    }

    public static ContentValues generateAggregateReportContentValuesV14() {
        ContentValues aggregateReport = generateAggregateReportContentValuesV13();

        // Add columns.
        aggregateReport.put(
                MeasurementTables.AggregateReport.REGISTRATION_ORIGIN,
                AggregateReportValues.REGISTRATION_ORIGIN.toString());

        return aggregateReport;
    }

    public static ContentValues generateAggregateReportContentValuesV15() {
        return generateAggregateReportContentValuesV14();
    }

    public static ContentValues generateAggregateReportContentValuesV16() {
        return generateAggregateReportContentValuesV15();
    }

    public static ContentValues generateAggregateReportContentValuesV17() {
        return generateAggregateReportContentValuesV16();
    }

    public static ContentValues generateAggregateReportContentValuesV18() {
        return generateAggregateReportContentValuesV17();
    }

    public static ContentValues generateAggregateReportContentValuesV19() {
        return generateAggregateReportContentValuesV18();
    }

    public static ContentValues generateAggregateReportContentValuesV20() {
        ContentValues values = generateAggregateReportContentValuesV19();
        values.put(
                MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN,
                AggregateReportValues.AGGREGATION_COORDINATOR_ORIGIN.toString());
        return values;
    }

    public static ContentValues generateAggregateReportContentValuesV27() {
        ContentValues values = generateAggregateReportContentValuesV20();
        values.put(
                MeasurementTables.AggregateReport.IS_FAKE_REPORT,
                AggregateReportValues.IS_FAKE_REPORT);
        return values;
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV1() {
        ContentValues aggregateEncryptionKey = new ContentValues();

        aggregateEncryptionKey.put(
                MeasurementTables.AggregateEncryptionKey.ID, AggregateEncryptionKeyValues.ID);
        aggregateEncryptionKey.put(
                MeasurementTables.AggregateEncryptionKey.KEY_ID,
                AggregateEncryptionKeyValues.KEY_ID);
        aggregateEncryptionKey.put(
                MeasurementTables.AggregateEncryptionKey.PUBLIC_KEY,
                AggregateEncryptionKeyValues.PUBLIC_KEY);
        aggregateEncryptionKey.put(
                MeasurementTables.AggregateEncryptionKey.EXPIRY,
                AggregateEncryptionKeyValues.EXPIRY);

        return aggregateEncryptionKey;
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV2() {
        // No differences in aggregate encryption key fields between V1 and V2.
        return generateAggregateEncryptionKeyContentValuesV1();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV3() {
        // No differences in aggregate encryption key fields between V2 and V3.
        return generateAggregateEncryptionKeyContentValuesV2();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV6() {
        return generateAggregateEncryptionKeyContentValuesV3();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV7() {
        return generateAggregateEncryptionKeyContentValuesV6();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV8() {
        return generateAggregateEncryptionKeyContentValuesV7();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV9() {
        return generateAggregateEncryptionKeyContentValuesV7();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV10() {
        return generateAggregateEncryptionKeyContentValuesV9();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV11() {
        return generateAggregateEncryptionKeyContentValuesV10();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV12() {
        return generateAggregateEncryptionKeyContentValuesV11();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV13() {
        return generateAggregateEncryptionKeyContentValuesV12();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV14() {
        return generateAggregateEncryptionKeyContentValuesV13();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV15() {
        return generateAggregateEncryptionKeyContentValuesV14();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV16() {
        return generateAggregateEncryptionKeyContentValuesV15();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV17() {
        return generateAggregateEncryptionKeyContentValuesV16();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV18() {
        return generateAggregateEncryptionKeyContentValuesV17();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV19() {
        return generateAggregateEncryptionKeyContentValuesV18();
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV20() {
        ContentValues values = generateAggregateEncryptionKeyContentValuesV19();
        values.put(
                MeasurementTables.AggregateEncryptionKey.AGGREGATION_COORDINATOR_ORIGIN,
                AggregateEncryptionKeyValues.AGGREGATION_COORDINATOR_ORIGIN.toString());
        return values;
    }

    public static ContentValues generateDebugReportContentValuesV3() {
        ContentValues debugReport = new ContentValues();

        debugReport.put(MeasurementTables.DebugReportContract.ID, DebugReportValues.ID);
        debugReport.put(MeasurementTables.DebugReportContract.TYPE, DebugReportValues.TYPE);
        debugReport.put(MeasurementTables.DebugReportContract.BODY, DebugReportValues.BODY);
        debugReport.put(
                MeasurementTables.DebugReportContract.ENROLLMENT_ID,
                DebugReportValues.ENROLLMENT_ID);

        return debugReport;
    }

    public static ContentValues generateDebugReportContentValuesV6() {
        return generateDebugReportContentValuesV3();
    }

    public static ContentValues generateDebugReportContentValuesV7() {
        return generateDebugReportContentValuesV6();
    }

    public static ContentValues generateDebugReportContentValuesV8() {
        return generateDebugReportContentValuesV7();
    }

    public static ContentValues generateDebugReportContentValuesV9() {
        return generateDebugReportContentValuesV8();
    }

    public static ContentValues generateDebugReportContentValuesV10() {
        return generateDebugReportContentValuesV9();
    }

    public static ContentValues generateDebugReportContentValuesV11() {
        return generateDebugReportContentValuesV10();
    }

    public static ContentValues generateDebugReportContentValuesV12() {
        return generateDebugReportContentValuesV11();
    }

    public static ContentValues generateDebugReportContentValuesV13() {
        return generateDebugReportContentValuesV12();
    }

    public static ContentValues generateDebugReportContentValuesV14() {
        return generateDebugReportContentValuesV13();
    }

    public static ContentValues generateDebugReportContentValuesV15() {
        ContentValues debugReport = generateDebugReportContentValuesV14();
        debugReport.put(
                MeasurementTables.DebugReportContract.REGISTRATION_ORIGIN,
                DebugReportValues.REGISTRATION_ORIGIN.toString());

        return debugReport;
    }

    public static ContentValues generateDebugReportContentValuesV16() {
        return generateDebugReportContentValuesV15();
    }

    public static ContentValues generateDebugReportContentValuesV17() {
        return generateDebugReportContentValuesV16();
    }

    /**
     * @return DebugReport for V25
     */
    public static ContentValues generateDebugReportContentValuesV25() {
        return generateDebugReportContentValuesV17();
    }

    public static ContentValues generateXnaIgnoredSourcesContentValuesV6() {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.XnaIgnoredSourcesContract.SOURCE_ID,
                XnaIgnoredSourcesValues.SOURCE_ID);
        values.put(
                MeasurementTables.XnaIgnoredSourcesContract.ENROLLMENT_ID,
                XnaIgnoredSourcesValues.ENROLLMENT_ID);

        return values;
    }

    public static ContentValues generateXnaIgnoredSourcesContentValuesV7() {
        return generateXnaIgnoredSourcesContentValuesV6();
    }

    public static ContentValues generateXnaIgnoredSourcesContentValuesV8() {
        return generateXnaIgnoredSourcesContentValuesV7();
    }

    public static ContentValues generateXnaIgnoredSourcesContentValuesV9() {
        return generateXnaIgnoredSourcesContentValuesV8();
    }

    public static ContentValues generateXnaIgnoredSourcesContentValuesV10() {
        return generateXnaIgnoredSourcesContentValuesV9();
    }

    public static ContentValues generateXnaIgnoredSourcesContentValuesV11() {
        return generateXnaIgnoredSourcesContentValuesV10();
    }

    public static ContentValues generateXnaIgnoredSourcesContentValuesV12() {
        return generateXnaIgnoredSourcesContentValuesV11();
    }

    public static ContentValues generateXnaIgnoredSourcesContentValuesV13() {
        return generateXnaIgnoredSourcesContentValuesV12();
    }

    public static ContentValues generateXnaIgnoredSourcesContentValuesV14() {
        return generateXnaIgnoredSourcesContentValuesV13();
    }

    public static ContentValues generateXnaIgnoredSourcesContentValuesV15() {
        return generateXnaIgnoredSourcesContentValuesV14();
    }

    public static ContentValues generateXnaIgnoredSourcesContentValuesV16() {
        return generateXnaIgnoredSourcesContentValuesV15();
    }

    public static ContentValues generateXnaIgnoredSourcesContentValuesV17() {
        return generateXnaIgnoredSourcesContentValuesV16();
    }

    /**
     * @return List of KeyValeData CV for each Type in V25
     */
    public static List<ContentValues> generateKeyValueDataContentValuesV25() {
        List<ContentValues> list = new ArrayList<>();
        ContentValues rrValues = new ContentValues();

        rrValues.put(MeasurementTables.KeyValueDataContract.KEY, KeyValueDataValues.RR_KEY);
        rrValues.put(
                MeasurementTables.KeyValueDataContract.DATA_TYPE,
                KeyValueDataValues.RR_DATATYPE.toString());
        rrValues.put(MeasurementTables.KeyValueDataContract.VALUE, KeyValueDataValues.RR_VALUE);
        list.add(rrValues);

        ContentValues arValues = new ContentValues();
        arValues.put(MeasurementTables.KeyValueDataContract.KEY, KeyValueDataValues.AR_KEY);
        arValues.put(
                MeasurementTables.KeyValueDataContract.DATA_TYPE,
                KeyValueDataValues.AR_DATATYPE.toString());
        arValues.put(MeasurementTables.KeyValueDataContract.VALUE, KeyValueDataValues.AR_VALUE);
        list.add(arValues);

        ContentValues erValues = new ContentValues();
        erValues.put(MeasurementTables.KeyValueDataContract.KEY, KeyValueDataValues.ER_KEY);
        erValues.put(
                MeasurementTables.KeyValueDataContract.DATA_TYPE,
                KeyValueDataValues.ER_DATATYPE.toString());
        erValues.put(MeasurementTables.KeyValueDataContract.VALUE, KeyValueDataValues.ER_VALUE);
        list.add(erValues);

        return list;
    }
}
