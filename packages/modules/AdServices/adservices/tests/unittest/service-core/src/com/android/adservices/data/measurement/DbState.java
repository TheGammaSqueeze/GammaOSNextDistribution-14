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

package com.android.adservices.data.measurement;

import static java.util.function.Predicate.not;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.android.adservices.common.WebUtil;
import com.android.adservices.service.measurement.Attribution;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.registration.AsyncRedirect;
import com.android.adservices.service.measurement.registration.AsyncRegistration;
import com.android.adservices.service.measurement.reporting.DebugReport;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Class for providing test data for measurement tests.
 */
public class DbState {

    private static final String DEFAULT_REGISTRATION_URI =
            WebUtil.validUrl("https://test.example.test");
    List<Source> mSourceList;
    List<SourceDestination> mSourceDestinationList;
    List<Trigger> mTriggerList;
    List<EventReport> mEventReportList;
    List<Attribution> mAttrRateLimitList;
    List<AggregateEncryptionKey> mAggregateEncryptionKeyList;
    List<AggregateReport> mAggregateReportList;
    List<DebugReport> mDebugReportList;
    List<AsyncRegistration> mAsyncRegistrationList;
    List<KeyValueData> mKeyValueDataList;

    public DbState() {
        mSourceList = new ArrayList<>();
        mSourceDestinationList = new ArrayList<>();
        mTriggerList = new ArrayList<>();
        mEventReportList = new ArrayList<>();
        mAttrRateLimitList = new ArrayList<>();
        mAggregateEncryptionKeyList = new ArrayList<>();
        mAggregateReportList = new ArrayList<>();
        mDebugReportList = new ArrayList<>();
        mAsyncRegistrationList = new ArrayList<>();
        mKeyValueDataList = new ArrayList<>();
    }

    public DbState(JSONObject testInput) throws JSONException {
        this();

        // Sources
        if (testInput.has("sources")) {
            JSONArray sources = testInput.getJSONArray("sources");
            for (int i = 0; i < sources.length(); i++) {
                JSONObject sJSON = sources.getJSONObject(i);
                Source source = getSourceFrom(sJSON);
                mSourceList.add(source);
            }
        }

        // SourceDestinations
        if (testInput.has("source_destinations")) {
            JSONArray sourceDestinations = testInput.getJSONArray("source_destinations");
            for (int i = 0; i < sourceDestinations.length(); i++) {
                JSONObject sdJSON = sourceDestinations.getJSONObject(i);
                SourceDestination sourceDestination = getSourceDestinationFrom(sdJSON);
                mSourceDestinationList.add(sourceDestination);
            }
        }

        // Triggers
        if (testInput.has("triggers")) {
            JSONArray triggers = testInput.getJSONArray("triggers");
            for (int i = 0; i < triggers.length(); i++) {
                JSONObject tJSON = triggers.getJSONObject(i);
                Trigger trigger = getTriggerFrom(tJSON);
                mTriggerList.add(trigger);
            }
        }

        // EventReports
        if (testInput.has("event_reports")) {
            JSONArray eventReports = testInput.getJSONArray("event_reports");
            for (int i = 0; i < eventReports.length(); i++) {
                JSONObject rJSON = eventReports.getJSONObject(i);
                EventReport eventReport = getEventReportFrom(rJSON);
                mEventReportList.add(eventReport);
            }
        }

        // Attributions
        if (testInput.has("attributions")) {
            JSONArray attrs = testInput.getJSONArray("attributions");
            for (int i = 0; i < attrs.length(); i++) {
                JSONObject attrJSON = attrs.getJSONObject(i);
                Attribution attrRateLimit = getAttributionFrom(attrJSON);
                mAttrRateLimitList.add(attrRateLimit);
            }
        }

        // AggregateEncryptionKeys
        if (testInput.has("aggregate_encryption_keys")) {
            JSONArray keys = testInput.getJSONArray("aggregate_encryption_keys");
            for (int i = 0; i < keys.length(); i++) {
                JSONObject keyJSON = keys.getJSONObject(i);
                AggregateEncryptionKey key = getAggregateEncryptionKeyFrom(keyJSON);
                mAggregateEncryptionKeyList.add(key);
            }
        }

        if (testInput.has("aggregate_reports")) {
            // AggregateReports
            JSONArray aggregateReports = testInput.getJSONArray("aggregate_reports");
            for (int i = 0; i < aggregateReports.length(); i++) {
                JSONObject rJSON = aggregateReports.getJSONObject(i);
                AggregateReport aggregateReport = getAggregateReportFrom(rJSON);
                mAggregateReportList.add(aggregateReport);
            }
        }

        if (testInput.has("debug_reports")) {
            // DebugReports
            JSONArray debugReports = testInput.getJSONArray("debug_reports");
            for (int i = 0; i < debugReports.length(); i++) {
                JSONObject rJSON = debugReports.getJSONObject(i);
                DebugReport debugReport = getDebugReportFrom(rJSON);
                mDebugReportList.add(debugReport);
            }
        }

        if (testInput.has("async_registrations")) {
            // AsyncRegistrations
            JSONArray asyncRegistrations = testInput.getJSONArray("async_registrations");
            for (int i = 0; i < asyncRegistrations.length(); i++) {
                JSONObject aJSON = asyncRegistrations.getJSONObject(i);
                AsyncRegistration asyncRegistration = getAsyncRegistrationFrom(aJSON);
                mAsyncRegistrationList.add(asyncRegistration);
            }
        }

        if (testInput.has("key_values")) {
            // KeyValues
            JSONArray keyValues = testInput.getJSONArray("key_values");
            for (int i = 0; i < keyValues.length(); i++) {
                JSONObject aJSON = keyValues.getJSONObject(i);
                KeyValueData keyValueData = getKeyValueDataFrom(aJSON);
                mKeyValueDataList.add(keyValueData);
            }
        }
    }

    public DbState(SQLiteDatabase readerDB) {
        this();

        // Read Source table
        Cursor sourceCursor = readerDB.query(MeasurementTables.SourceContract.TABLE,
                null, null, null, null, null, MeasurementTables.SourceContract.ID);
        while (sourceCursor.moveToNext()) {
            mSourceList.add(SqliteObjectMapper.constructSourceFromCursor(sourceCursor));
        }
        sourceCursor.close();

        // Read SourceDestination table
        Cursor destCursor = readerDB.query(MeasurementTables.SourceDestination.TABLE,
                null, null, null, null, null, null);
        while (destCursor.moveToNext()) {
            mSourceDestinationList.add(getSourceDestinationFrom(destCursor));
        }
        destCursor.close();

        // Read Trigger table
        Cursor triggerCursor = readerDB.query(MeasurementTables.TriggerContract.TABLE,
                null, null, null, null, null, MeasurementTables.TriggerContract.ID);
        while (triggerCursor.moveToNext()) {
            mTriggerList.add(SqliteObjectMapper.constructTriggerFromCursor(triggerCursor));
        }
        triggerCursor.close();

        // Read EventReport table
        Cursor eventReportCursor =
                readerDB.query(
                        MeasurementTables.EventReportContract.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MeasurementTables.EventReportContract.ID);
        while (eventReportCursor.moveToNext()) {
            mEventReportList.add(
                    SqliteObjectMapper.constructEventReportFromCursor(eventReportCursor));
        }
        eventReportCursor.close();

        // Read Attribution table
        Cursor attrCursor = readerDB.query(MeasurementTables.AttributionContract.TABLE,
                null, null, null, null, null, MeasurementTables.AttributionContract.ID);
        while (attrCursor.moveToNext()) {
            mAttrRateLimitList.add(getAttributionFrom(attrCursor));
        }
        attrCursor.close();

        // Read AggregateReport table
        Cursor aggregateReportCursor =
                readerDB.query(
                        MeasurementTables.AggregateReport.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MeasurementTables.AggregateReport.ID);
        while (aggregateReportCursor.moveToNext()) {
            mAggregateReportList.add(
                    SqliteObjectMapper.constructAggregateReport(aggregateReportCursor));
        }
        aggregateReportCursor.close();

        // Read AggregateEncryptionKey table
        Cursor keyCursor = readerDB.query(MeasurementTables.AggregateEncryptionKey.TABLE,
                null, null, null, null, null, MeasurementTables.AggregateEncryptionKey.ID);
        while (keyCursor.moveToNext()) {
            mAggregateEncryptionKeyList.add(
                    SqliteObjectMapper.constructAggregateEncryptionKeyFromCursor(keyCursor));
        }
        keyCursor.close();

        // Read DebugReport table
        Cursor debugReportCursor =
                readerDB.query(
                        MeasurementTables.DebugReportContract.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MeasurementTables.DebugReportContract.ID);
        while (debugReportCursor.moveToNext()) {
            mDebugReportList.add(
                    SqliteObjectMapper.constructDebugReportFromCursor(debugReportCursor));
        }
        debugReportCursor.close();

        // Read AsyncRegistration table
        Cursor asyncRegistrationCursor =
                readerDB.query(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        while (asyncRegistrationCursor.moveToNext()) {
            mAsyncRegistrationList.add(
                    SqliteObjectMapper.constructAsyncRegistration(asyncRegistrationCursor));
        }
        asyncRegistrationCursor.close();

        // Read KeyValueData table
        Cursor keyValueDataCursor =
                readerDB.query(
                        MeasurementTables.KeyValueDataContract.TABLE,
                        new String[] {
                            MeasurementTables.KeyValueDataContract.KEY,
                            MeasurementTables.KeyValueDataContract.VALUE,
                            MeasurementTables.KeyValueDataContract.DATA_TYPE
                        },
                        null,
                        null,
                        null,
                        null,
                        MeasurementTables.KeyValueDataContract.KEY);
        while (keyValueDataCursor.moveToNext()) {
            KeyValueData.Builder builder = new KeyValueData.Builder();
            builder.setKey(keyValueDataCursor.getString(0));
            builder.setValue(keyValueDataCursor.getString(1));
            builder.setDataType(KeyValueData.DataType.valueOf(keyValueDataCursor.getString(2)));
            mKeyValueDataList.add(builder.build());
        }
        keyValueDataCursor.close();
    }

    public void sortAll() {
        mSourceList.sort(
                Comparator.comparing(Source::getEventTime)
                        .thenComparing(Source::getPriority));

        mSourceDestinationList.sort(
                Comparator.comparing(SourceDestination::getSourceId)
                        .thenComparing(SourceDestination::getDestinationType)
                        .thenComparing(SourceDestination::getDestination));

        mTriggerList.sort(Comparator.comparing(Trigger::getTriggerTime));

        mEventReportList.sort(
                Comparator.comparing(EventReport::getReportTime)
                        .thenComparing(EventReport::getTriggerTime));

        mAttrRateLimitList.sort(
                Comparator.comparing(Attribution::getScope)
                        .thenComparing(Attribution::getTriggerId));

        mAggregateEncryptionKeyList.sort(
                Comparator.comparing(AggregateEncryptionKey::getKeyId));

        mAggregateReportList.sort(
                Comparator.comparing(AggregateReport::getScheduledReportTime)
                        .thenComparing(AggregateReport::getSourceRegistrationTime));

        mDebugReportList.sort(Comparator.comparing(DebugReport::getId));

        mAsyncRegistrationList.sort(Comparator.comparing(AsyncRegistration::getRequestTime));

        mKeyValueDataList.sort(Comparator.comparing(KeyValueData::getKey));
    }

    public List<AggregateEncryptionKey> getAggregateEncryptionKeyList() {
        return mAggregateEncryptionKeyList;
    }

    private Source getSourceFrom(JSONObject sJSON) throws JSONException {
        Source.Builder builder =
                new Source.Builder()
                        .setId(sJSON.getString("id"))
                        .setEventId(new UnsignedLong(sJSON.getString("eventId")))
                        .setSourceType(
                                Source.SourceType.valueOf(
                                        sJSON.getString("sourceType").toUpperCase(Locale.ENGLISH)))
                        .setPublisher(Uri.parse(sJSON.getString("publisher")))
                        .setPublisherType(sJSON.optInt("publisherType"))
                        .setAggregateSource(sJSON.optString("aggregationKeys", null))
                        .setAggregateContributions(sJSON.optInt("aggregateContributions"))
                        .setEnrollmentId(sJSON.getString("enrollmentId"))
                        .setEventTime(sJSON.getLong("eventTime"))
                        .setExpiryTime(sJSON.getLong("expiryTime"))
                        .setEventReportWindow(sJSON.getLong("eventReportWindow"))
                        .setAggregatableReportWindow(sJSON.optLong("aggregatableReportWindow"))
                        .setPriority(sJSON.getLong("priority"))
                        .setStatus(sJSON.getInt("status"))
                        .setRegistrant(Uri.parse(sJSON.getString("registrant")))
                        .setInstallAttributionWindow(
                                sJSON.optLong(
                                        "installAttributionWindow", TimeUnit.DAYS.toMillis(30)))
                        .setInstallCooldownWindow(sJSON.optLong("installCooldownWindow", 0))
                        .setInstallAttributed(sJSON.optBoolean("installAttributed", false))
                        .setAttributionMode(
                                sJSON.optInt("attribution_mode", Source.AttributionMode.TRUTHFULLY))
                        .setFilterDataString(sJSON.optString("filterData", null))
                        .setRegistrationOrigin(getRegistrationOrigin(sJSON))
                        .setTriggerSpecsString(sJSON.optString("triggerSpecs", null))
                        .setEventAttributionStatus(sJSON.optString("eventAttributionStatus", null));

        if (sJSON.opt("aggregateReportDedupKeys") != null) {
            builder.setAggregateReportDedupKeys(
                    commaSeparatedUnsignedStringsToList(
                            sJSON.getString("aggregateReportDedupKeys")));
        }

        return builder.build();
    }

    private static List<UnsignedLong> commaSeparatedUnsignedStringsToList(String values) {
        if (values == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(values.split(","))
                .map(String::trim)
                .filter(not(String::isEmpty))
                .map(UnsignedLong::new)
                .collect(Collectors.toList());
    }

    private SourceDestination getSourceDestinationFrom(JSONObject sdJSON) throws JSONException {
        return new SourceDestination.Builder()
                .setSourceId(sdJSON.getString("sourceId"))
                .setDestination(sdJSON.getString("destination"))
                .setDestinationType(sdJSON.getInt("destinationType"))
                .build();
    }

    private Trigger getTriggerFrom(JSONObject tJSON) throws JSONException {
        return new Trigger.Builder()
                .setId(tJSON.getString("id"))
                .setAttributionDestination(Uri.parse(tJSON.getString("attributionDestination")))
                .setDestinationType(tJSON.optInt("destinationType"))
                .setEnrollmentId(tJSON.getString("enrollmentId"))
                .setEventTriggers(tJSON.getString("eventTriggers"))
                .setAggregateTriggerData(tJSON.optString("aggregatableTriggerData", null))
                .setAggregateValues(tJSON.optString("aggregatableValues", null))
                .setTriggerTime(tJSON.getLong("triggerTime"))
                .setStatus(tJSON.getInt("status"))
                .setRegistrant(Uri.parse(tJSON.getString("registrant")))
                .setFilters(tJSON.optString("filters", null))
                .setNotFilters(tJSON.optString("not_filters", null))
                .setRegistrationOrigin(getRegistrationOrigin(tJSON))
                .setAggregatableSourceRegistrationTimeConfig(
                        Trigger.SourceRegistrationTimeConfig.valueOf(
                                tJSON.getString("aggregatableSourceRegistrationTimeConfig")))
                .build();
    }

    private EventReport getEventReportFrom(JSONObject rJSON) throws JSONException {
        return new EventReport.Builder()
                .setId(rJSON.getString("id"))
                .setSourceEventId(new UnsignedLong(rJSON.getString("sourceEventId")))
                .setAttributionDestinations(
                        SqliteObjectMapper.destinationsStringToList(
                                rJSON.getString("attributionDestination")))
                .setEnrollmentId(rJSON.getString("enrollmentId"))
                .setTriggerData(new UnsignedLong(rJSON.getString("triggerData")))
                .setTriggerTime(rJSON.getLong("triggerTime"))
                .setReportTime(rJSON.getLong("reportTime"))
                .setTriggerPriority(rJSON.getLong("triggerPriority"))
                .setStatus(rJSON.getInt("status"))
                .setRandomizedTriggerRate(rJSON.optDouble("randomizedTriggerRate", 0.0D))
                .setSourceType(
                        Source.SourceType.valueOf(
                                rJSON.getString("sourceType").toUpperCase(Locale.ENGLISH)))
                .setSourceId(rJSON.optString("sourceId", null))
                .setTriggerId(rJSON.optString("triggerId", null))
                .setRegistrationOrigin(getRegistrationOrigin(rJSON))
                .build();
    }

    private Attribution getAttributionFrom(JSONObject attrJSON)
            throws JSONException {
        return new Attribution.Builder()
                .setId(attrJSON.getString("id"))
                .setScope(attrJSON.optInt("scope", 0))
                .setSourceSite(attrJSON.getString("sourceSite"))
                .setSourceOrigin(attrJSON.getString("sourceOrigin"))
                .setDestinationSite(attrJSON.getString("destinationSite"))
                .setDestinationOrigin(attrJSON.getString("destinationOrigin"))
                .setEnrollmentId(attrJSON.getString("enrollmentId"))
                .setTriggerTime(attrJSON.getLong("triggerTime"))
                .setRegistrant(attrJSON.getString("registrant"))
                .setSourceId(attrJSON.optString("sourceId", null))
                .setTriggerId(attrJSON.optString("triggerId", null))
                .build();
    }

    private AggregateEncryptionKey getAggregateEncryptionKeyFrom(JSONObject keyJSON)
            throws JSONException {
        return new AggregateEncryptionKey.Builder()
                .setId(keyJSON.getString("id"))
                .setKeyId(keyJSON.getString("keyId"))
                .setPublicKey(keyJSON.getString("publicKey"))
                .setExpiry(keyJSON.getLong("expiry"))
                .setAggregationCoordinatorOrigin(
                        WebUtil.validUri(keyJSON.getString("aggregation_coordinator_origin")))
                .build();
    }

    private Attribution getAttributionFrom(Cursor cursor) {
        return new Attribution.Builder()
                .setId(
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.AttributionContract.ID)))
                .setSourceSite(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.SOURCE_SITE)))
                .setScope(
                        cursor.getInt(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.SCOPE)))
                .setSourceOrigin(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.SOURCE_ORIGIN)))
                .setDestinationSite(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.DESTINATION_SITE)))
                .setDestinationOrigin(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.DESTINATION_ORIGIN)))
                .setEnrollmentId(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.ENROLLMENT_ID)))
                .setTriggerTime(
                        cursor.getLong(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.TRIGGER_TIME)))
                .setRegistrant(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.REGISTRANT)))
                .setSourceId(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.SOURCE_ID)))
                .setTriggerId(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.TRIGGER_ID)))
                .build();
    }

    private SourceDestination getSourceDestinationFrom(Cursor cursor) {
        return new SourceDestination.Builder()
                .setSourceId(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.SourceDestination.SOURCE_ID)))
                .setDestination(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.SourceDestination.DESTINATION)))
                .setDestinationType(
                        cursor.getInt(
                                cursor.getColumnIndex(
                                        MeasurementTables.SourceDestination.DESTINATION_TYPE)))
                .build();
    }

    private AggregateReport getAggregateReportFrom(JSONObject rJSON)
            throws JSONException {
        AggregateReport.Builder builder =
                new AggregateReport.Builder()
                        .setId(rJSON.getString("id"))
                        .setPublisher(Uri.parse(rJSON.getString("publisher")))
                        .setAttributionDestination(
                                Uri.parse(rJSON.getString("attributionDestination")))
                        .setSourceRegistrationTime(rJSON.getLong("sourceRegistrationTime"))
                        .setScheduledReportTime(rJSON.getLong("scheduledReportTime"))
                        .setEnrollmentId(rJSON.getString("enrollmentId"))
                        .setDebugCleartextPayload(rJSON.getString("debugCleartextPayload"))
                        .setStatus(rJSON.getInt("status"))
                        .setApiVersion(rJSON.optString("apiVersion", null))
                        .setSourceId(rJSON.optString("sourceId", null))
                        .setTriggerId(rJSON.optString("triggerId", null))
                        .setRegistrationOrigin(getRegistrationOrigin(rJSON))
                        .setAggregationCoordinatorOrigin(
                                Uri.parse(
                                        rJSON.optString(
                                                "aggregation_coordinator_origin",
                                                "https://test.test")))
                        .setIsFakeReport(rJSON.optBoolean("isFakeReport", false));

        if (rJSON.opt("dedupKey") != null) {
            builder.setDedupKey(new UnsignedLong(rJSON.getString("dedupKey")));
        }

        return builder.build();
    }

    private DebugReport getDebugReportFrom(JSONObject rJSON) throws JSONException {
        DebugReport.Builder builder =
                new DebugReport.Builder()
                        .setId(rJSON.getString("id"))
                        .setType(rJSON.getString("type"))
                        .setBody(rJSON.getString("body"));
        if (rJSON.has("registrant")) {
            builder.setRegistrant(Uri.parse(rJSON.getString("registrant")));
        }
        return builder.build();
    }

    private AsyncRegistration getAsyncRegistrationFrom(JSONObject aJSON) throws JSONException {
        return new AsyncRegistration.Builder()
                .setRegistrationId(aJSON.getString("registrationId"))
                .setRegistrationUri(Uri.parse(aJSON.getString("registrationUri")))
                .setTopOrigin(Uri.parse(aJSON.getString("topOrigin")))
                .setRegistrant(Uri.parse(aJSON.getString("registrant")))
                .setOsDestination(Uri.parse(aJSON.getString("osDestination")))
                .setRequestTime(aJSON.getLong("requestTime"))
                .setAdIdPermission(aJSON.getBoolean("adIdPermission"))
                .setId(aJSON.getString("id"))
                .setType(
                        AsyncRegistration.RegistrationType.values()[
                                aJSON.getInt("registrationType")])
                .setPlatformAdId(aJSON.getString("platformAdId"))
                .setDebugKeyAllowed(aJSON.getBoolean("debugKeyAllowed"))
                .setRetryCount(aJSON.getInt("retryCount"))
                .setVerifiedDestination(Uri.parse(aJSON.getString("verifiedDestination")))
                .setWebDestination(Uri.parse(aJSON.getString("webDestination")))
                .setSourceType(Source.SourceType.values()[aJSON.getInt("sourceType")])
                .setRedirectBehavior(
                        AsyncRedirect.RedirectBehavior.valueOf(aJSON.getString("redirectBehavior")))
                .build();
    }

    private KeyValueData getKeyValueDataFrom(JSONObject kJSON) throws JSONException {
        return new KeyValueData.Builder()
                .setKey(kJSON.getString("key"))
                .setDataType(KeyValueData.DataType.valueOf(kJSON.getString("dataType")))
                .setValue(kJSON.getString("value"))
                .build();
    }

    private Uri getRegistrationOrigin(JSONObject json) throws JSONException {
        return Uri.parse(
                json.isNull("registration_origin")
                        ? DEFAULT_REGISTRATION_URI
                        : json.get("registration_origin").toString());
    }
}
