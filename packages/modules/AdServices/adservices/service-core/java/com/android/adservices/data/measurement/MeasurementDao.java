/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE;

import android.adservices.measurement.DeletionRequest;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.measurement.Attribution;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.measurement.KeyValueData.DataType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.registration.AsyncRegistration;
import com.android.adservices.service.measurement.reporting.DebugReport;
import com.android.adservices.service.measurement.util.BaseUriExtractor;
import com.android.adservices.service.measurement.util.UnsignedLong;

import com.google.common.collect.ImmutableList;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Data Access Object for the Measurement PPAPI module. */
class MeasurementDao implements IMeasurementDao {

    private static final int MAX_COMPOUND_SELECT = 250;

    private Supplier<Boolean> mDbFileMaxSizeLimitReachedSupplier;
    private Supplier<Integer> mReportingRetryLimitSupplier;
    private Supplier<Boolean> mReportingRetryLimitEnabledSupplier;

    private SQLTransaction mSQLTransaction;

    MeasurementDao(
            @NonNull Supplier<Boolean> dbFileMaxSizeLimitReachedSupplier,
            @NonNull Supplier<Integer> reportingRetryLimitSupplier,
            @NonNull Supplier<Boolean> reportingRetryLimitEnabledSupplier) {
        mDbFileMaxSizeLimitReachedSupplier = dbFileMaxSizeLimitReachedSupplier;
        mReportingRetryLimitSupplier = reportingRetryLimitSupplier;
        mReportingRetryLimitEnabledSupplier = reportingRetryLimitEnabledSupplier;
    }

    @Override
    public void setTransaction(ITransaction transaction) {
        if (!(transaction instanceof SQLTransaction)) {
            throw new IllegalArgumentException("transaction should be a SQLTransaction.");
        }
        mSQLTransaction = (SQLTransaction) transaction;
    }

    @Override
    public void insertTrigger(@NonNull Trigger trigger) throws DatastoreException {
        if (mDbFileMaxSizeLimitReachedSupplier.get()) {
            LoggerFactory.getMeasurementLogger()
                    .d("DB size has reached the limit, trigger will not be inserted");
            return;
        }

        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.ID, UUID.randomUUID().toString());
        values.put(
                MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                trigger.getAttributionDestination().toString());
        values.put(
                MeasurementTables.TriggerContract.DESTINATION_TYPE, trigger.getDestinationType());
        values.put(MeasurementTables.TriggerContract.TRIGGER_TIME, trigger.getTriggerTime());
        values.put(MeasurementTables.TriggerContract.EVENT_TRIGGERS, trigger.getEventTriggers());
        values.put(MeasurementTables.TriggerContract.STATUS, Trigger.Status.PENDING);
        values.put(MeasurementTables.TriggerContract.ENROLLMENT_ID, trigger.getEnrollmentId());
        values.put(
                MeasurementTables.TriggerContract.REGISTRANT, trigger.getRegistrant().toString());
        values.put(
                MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                trigger.getAggregateTriggerData());
        values.put(
                MeasurementTables.TriggerContract.AGGREGATE_VALUES, trigger.getAggregateValues());
        values.put(
                MeasurementTables.TriggerContract.AGGREGATABLE_DEDUPLICATION_KEYS,
                trigger.getAggregateDeduplicationKeys());
        values.put(MeasurementTables.TriggerContract.FILTERS, trigger.getFilters());
        values.put(MeasurementTables.TriggerContract.NOT_FILTERS, trigger.getNotFilters());
        values.put(
                MeasurementTables.TriggerContract.DEBUG_KEY,
                getNullableUnsignedLong(trigger.getDebugKey()));
        values.put(MeasurementTables.TriggerContract.DEBUG_REPORTING, trigger.isDebugReporting());
        values.put(MeasurementTables.TriggerContract.AD_ID_PERMISSION, trigger.hasAdIdPermission());
        values.put(
                MeasurementTables.TriggerContract.AR_DEBUG_PERMISSION,
                trigger.hasArDebugPermission());
        values.put(
                MeasurementTables.TriggerContract.ATTRIBUTION_CONFIG,
                trigger.getAttributionConfig());
        values.put(
                MeasurementTables.TriggerContract.X_NETWORK_KEY_MAPPING,
                trigger.getAdtechKeyMapping());
        values.put(MeasurementTables.TriggerContract.DEBUG_JOIN_KEY, trigger.getDebugJoinKey());
        values.put(MeasurementTables.TriggerContract.PLATFORM_AD_ID, trigger.getPlatformAdId());
        values.put(MeasurementTables.TriggerContract.DEBUG_AD_ID, trigger.getDebugAdId());
        values.put(
                MeasurementTables.TriggerContract.REGISTRATION_ORIGIN,
                trigger.getRegistrationOrigin().toString());
        values.put(
                MeasurementTables.TriggerContract.AGGREGATION_COORDINATOR_ORIGIN,
                getNullableUriString(trigger.getAggregationCoordinatorOrigin()));
        values.put(
                MeasurementTables.TriggerContract.AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG,
                trigger.getAggregatableSourceRegistrationTimeConfig().name());

        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.TriggerContract.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        LoggerFactory.getMeasurementLogger().d("MeasurementDao: insertTrigger: rowId=" + rowId);
        if (rowId == -1) {
            throw new DatastoreException("Trigger insertion failed.");
        }
    }

    @Override
    public List<String> getPendingTriggerIds() throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.TriggerContract.TABLE,
                                new String[] {MeasurementTables.TriggerContract.ID},
                                MeasurementTables.TriggerContract.STATUS + " = ? ",
                                new String[] {String.valueOf(Trigger.Status.PENDING)},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ MeasurementTables.TriggerContract.TRIGGER_TIME,
                                /* limit= */ null)) {
            List<String> result = new ArrayList<>();
            while (cursor.moveToNext()) {
                result.add(cursor.getString(/* columnIndex= */ 0));
            }
            return result;
        }
    }

    @Override
    public Source getSource(@NonNull String sourceId) throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.SourceContract.TABLE,
                                /* columns= */ null,
                                MeasurementTables.SourceContract.ID + " = ? ",
                                new String[] {sourceId},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ null,
                                /* limit= */ null)) {
            if (cursor.getCount() == 0) {
                throw new DatastoreException("Source retrieval failed. Id: " + sourceId);
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructSourceFromCursor(cursor);
        }
    }

    @Override
    @Nullable
    public Pair<List<Uri>, List<Uri>> getSourceDestinations(@NonNull String sourceId)
            throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.SourceDestination.TABLE,
                                new String[] {
                                    MeasurementTables.SourceDestination.DESTINATION,
                                    MeasurementTables.SourceDestination.DESTINATION_TYPE
                                },
                                MeasurementTables.SourceDestination.SOURCE_ID + " = ? ",
                                new String[] {sourceId},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ null,
                                /* limit= */ null)) {
            List<Uri> appDestinations = new ArrayList<>();
            List<Uri> webDestinations = new ArrayList<>();
            while (cursor.moveToNext()) {
                int destinationType =
                        cursor.getInt(
                                cursor.getColumnIndex(
                                        MeasurementTables.SourceDestination.DESTINATION_TYPE));
                if (destinationType == EventSurfaceType.APP) {
                    appDestinations.add(
                            Uri.parse(
                                    cursor.getString(
                                            cursor.getColumnIndex(
                                                    MeasurementTables.SourceDestination
                                                            .DESTINATION))));
                } else {
                    webDestinations.add(
                            Uri.parse(
                                    cursor.getString(
                                            cursor.getColumnIndex(
                                                    MeasurementTables.SourceDestination
                                                            .DESTINATION))));
                }
            }
            return Pair.create(appDestinations, webDestinations);
        }
    }

    @Override
    public String getSourceRegistrant(@NonNull String sourceId) throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.SourceContract.TABLE,
                                new String[] {
                                    MeasurementTables.SourceContract.REGISTRANT,
                                },
                                MeasurementTables.SourceContract.ID + " = ? ",
                                new String[] {sourceId},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ null,
                                /* limit= */ null)) {
            if (cursor.getCount() == 0) {
                throw new DatastoreException("Source retrieval failed. Id: " + sourceId);
            }
            cursor.moveToNext();
            return cursor.getString(
                    cursor.getColumnIndex(MeasurementTables.SourceContract.REGISTRANT));
        }
    }

    @Override
    public Trigger getTrigger(@NonNull String triggerId) throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.TriggerContract.TABLE,
                                /* columns= */ null,
                                MeasurementTables.TriggerContract.ID + " = ? ",
                                new String[] {triggerId},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ null,
                                /* limit= */ null)) {
            if (cursor.getCount() == 0) {
                throw new DatastoreException("Trigger retrieval failed. Id: " + triggerId);
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructTriggerFromCursor(cursor);
        }
    }

    @Override
    public int getNumAggregateReportsPerDestination(
            @NonNull Uri attributionDestination, @EventSurfaceType int destinationType)
            throws DatastoreException {
        return getNumReportsPerDestination(
                MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                attributionDestination,
                destinationType);
    }

    @Override
    public int getNumEventReportsPerDestination(
            @NonNull Uri attributionDestination, @EventSurfaceType int destinationType)
            throws DatastoreException {
        return getNumReportsPerDestination(
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                attributionDestination,
                destinationType);
    }

    @Override
    public int getNumAggregateReportsPerSource(@NonNull String sourceId) throws DatastoreException {
        String query =
                String.format(
                        Locale.ENGLISH,
                        "SELECT COUNT(*) FROM %1$s WHERE %2$s = '%3$s'",
                        MeasurementTables.AggregateReport.TABLE,
                        MeasurementTables.AggregateReport.SOURCE_ID,
                        sourceId);
        return (int) DatabaseUtils.longForQuery(mSQLTransaction.getDatabase(), query, null);
    }

    @Override
    public EventReport getEventReport(@NonNull String eventReportId) throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.EventReportContract.TABLE,
                                null,
                                MeasurementTables.EventReportContract.ID + " = ? ",
                                new String[] {eventReportId},
                                null,
                                null,
                                null,
                                null)) {
            if (cursor.getCount() == 0) {
                throw new DatastoreException("EventReport retrieval failed. Id: " + eventReportId);
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEventReportFromCursor(cursor);
        }
    }

    @Override
    public AggregateReport getAggregateReport(@NonNull String aggregateReportId)
            throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.AggregateReport.TABLE,
                                null,
                                MeasurementTables.AggregateReport.ID + " = ? ",
                                new String[] {aggregateReportId},
                                null,
                                null,
                                null,
                                null)) {
            if (cursor.getCount() == 0) {
                throw new DatastoreException(
                        "AggregateReport retrieval failed. Id: " + aggregateReportId);
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructAggregateReport(cursor);
        }
    }

    @Nullable
    @Override
    public DebugReport getDebugReport(String debugReportId) throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.DebugReportContract.TABLE,
                                /* columns= */ null,
                                MeasurementTables.DebugReportContract.ID + " = ? ",
                                new String[] {debugReportId},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ null,
                                /* limit= */ null)) {
            if (cursor.getCount() == 0) {
                throw new DatastoreException("DebugReport retrieval failed. Id: " + debugReportId);
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructDebugReportFromCursor(cursor);
        }
    }

    @Override
    public String insertSource(@NonNull Source source) throws DatastoreException {
        if (mDbFileMaxSizeLimitReachedSupplier.get()) {
            LoggerFactory.getMeasurementLogger()
                    .d("DB size has reached the limit, source will not be inserted");
            return null;
        }

        String sourceId = UUID.randomUUID().toString();

        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.ID, sourceId);
        values.put(MeasurementTables.SourceContract.EVENT_ID, source.getEventId().getValue());
        values.put(MeasurementTables.SourceContract.PUBLISHER, source.getPublisher().toString());
        values.put(MeasurementTables.SourceContract.PUBLISHER_TYPE, source.getPublisherType());
        values.put(MeasurementTables.SourceContract.ENROLLMENT_ID, source.getEnrollmentId());
        values.put(MeasurementTables.SourceContract.EVENT_TIME, source.getEventTime());
        values.put(MeasurementTables.SourceContract.EXPIRY_TIME, source.getExpiryTime());
        values.put(
                MeasurementTables.SourceContract.EVENT_REPORT_WINDOW,
                source.getEventReportWindow());
        values.put(
                MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW,
                source.getAggregatableReportWindow());
        values.put(MeasurementTables.SourceContract.PRIORITY, source.getPriority());
        values.put(MeasurementTables.SourceContract.STATUS, source.getStatus());
        values.put(MeasurementTables.SourceContract.SOURCE_TYPE, source.getSourceType().name());
        values.put(MeasurementTables.SourceContract.REGISTRANT, source.getRegistrant().toString());
        values.put(
                MeasurementTables.SourceContract.INSTALL_ATTRIBUTION_WINDOW,
                source.getInstallAttributionWindow());
        values.put(
                MeasurementTables.SourceContract.INSTALL_COOLDOWN_WINDOW,
                source.getInstallCooldownWindow());
        values.put(MeasurementTables.SourceContract.ATTRIBUTION_MODE, source.getAttributionMode());
        values.put(MeasurementTables.SourceContract.AGGREGATE_SOURCE, source.getAggregateSource());
        values.put(MeasurementTables.SourceContract.FILTER_DATA, source.getFilterDataString());
        values.put(
                MeasurementTables.SourceContract.SHARED_FILTER_DATA_KEYS,
                source.getSharedFilterDataKeys());
        values.put(MeasurementTables.SourceContract.AGGREGATE_CONTRIBUTIONS, 0);
        values.put(
                MeasurementTables.SourceContract.DEBUG_KEY,
                getNullableUnsignedLong(source.getDebugKey()));
        values.put(MeasurementTables.SourceContract.DEBUG_REPORTING, source.isDebugReporting());
        values.put(MeasurementTables.SourceContract.AD_ID_PERMISSION, source.hasAdIdPermission());
        values.put(
                MeasurementTables.SourceContract.AR_DEBUG_PERMISSION,
                source.hasArDebugPermission());
        values.put(
                MeasurementTables.SourceContract.SHARED_AGGREGATION_KEYS,
                source.getSharedAggregationKeys());
        values.put(MeasurementTables.SourceContract.REGISTRATION_ID, source.getRegistrationId());
        values.put(MeasurementTables.SourceContract.INSTALL_TIME, source.getInstallTime());
        values.put(MeasurementTables.SourceContract.DEBUG_JOIN_KEY, source.getDebugJoinKey());
        values.put(MeasurementTables.SourceContract.PLATFORM_AD_ID, source.getPlatformAdId());
        values.put(MeasurementTables.SourceContract.DEBUG_AD_ID, source.getDebugAdId());
        values.put(
                MeasurementTables.SourceContract.REGISTRATION_ORIGIN,
                source.getRegistrationOrigin().toString());
        values.put(
                MeasurementTables.SourceContract.COARSE_EVENT_REPORT_DESTINATIONS,
                source.hasCoarseEventReportDestinations());
        if (source.getTriggerSpecs() != null) {
            values.put(
                    MeasurementTables.SourceContract.TRIGGER_SPECS,
                    source.getTriggerSpecs().encodeToJson());
            values.put(
                    MeasurementTables.SourceContract.PRIVACY_PARAMETERS,
                    source.getTriggerSpecs().encodePrivacyParametersToJsonString());
        }
        values.put(
                MeasurementTables.SourceContract.MAX_EVENT_LEVEL_REPORTS,
                source.getMaxEventLevelReports());
        values.put(
                MeasurementTables.SourceContract.EVENT_REPORT_WINDOWS,
                source.getEventReportWindows());
        values.put(
                MeasurementTables.SourceContract.SHARED_DEBUG_KEY,
                getNullableUnsignedLong(source.getSharedDebugKey()));
        values.put(
                MeasurementTables.SourceContract.TRIGGER_DATA_MATCHING,
                source.getTriggerDataMatching().name());

        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.SourceContract.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        LoggerFactory.getMeasurementLogger().d("MeasurementDao: insertSource: rowId=" + rowId);

        if (rowId == -1) {
            throw new DatastoreException("Source insertion failed.");
        }

        // Insert source destinations
        if (source.getAppDestinations() != null) {
            for (Uri appDestination : source.getAppDestinations()) {
                ContentValues destinationValues = new ContentValues();
                destinationValues.put(MeasurementTables.SourceDestination.SOURCE_ID, sourceId);
                destinationValues.put(
                        MeasurementTables.SourceDestination.DESTINATION_TYPE, EventSurfaceType.APP);
                destinationValues.put(
                        MeasurementTables.SourceDestination.DESTINATION, appDestination.toString());
                long destinationRowId =
                        mSQLTransaction
                                .getDatabase()
                                .insert(
                                        MeasurementTables.SourceDestination.TABLE,
                                        /* nullColumnHack= */ null,
                                        destinationValues);
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "MeasurementDao: insertSource: insert sourceDestination: rowId="
                                        + destinationRowId);
                if (destinationRowId == -1) {
                    throw new DatastoreException(
                            "Source insertion failed on inserting app destination.");
                }
            }
        }

        if (source.getWebDestinations() != null) {
            for (Uri webDestination : source.getWebDestinations()) {
                ContentValues destinationValues = new ContentValues();
                destinationValues.put(MeasurementTables.SourceDestination.SOURCE_ID, sourceId);
                destinationValues.put(
                        MeasurementTables.SourceDestination.DESTINATION_TYPE, EventSurfaceType.WEB);
                destinationValues.put(
                        MeasurementTables.SourceDestination.DESTINATION, webDestination.toString());
                long destinationRowId =
                        mSQLTransaction
                                .getDatabase()
                                .insert(
                                        MeasurementTables.SourceDestination.TABLE,
                                        /* nullColumnHack= */ null,
                                        destinationValues);
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "MeasurementDao: insertSource: insert sourceDestination: rowId="
                                        + destinationRowId);
                if (destinationRowId == -1) {
                    throw new DatastoreException(
                            "Source insertion failed on inserting web destination.");
                }
            }
        }
        return sourceId;
    }

    @Override
    public List<Source> getMatchingActiveSources(@NonNull Trigger trigger)
            throws DatastoreException {
        List<Source> sources = new ArrayList<>();
        Optional<String> destinationValue = getDestinationValue(trigger);
        if (!destinationValue.isPresent()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "getMatchingActiveSources: unable to obtain destination value: %s",
                            trigger.getAttributionDestination().toString());
            return sources;
        }
        String triggerDestinationValue = destinationValue.get();
        String sourceWhereStatement =
                String.format(
                        "%1$s.%2$s = ? "
                                + "AND %1$s.%3$s <= ? "
                                + "AND %1$s.%4$s > ? "
                                + "AND %1$s.%5$s = ?",
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.SourceContract.REGISTRATION_ORIGIN,
                        MeasurementTables.SourceContract.EVENT_TIME,
                        MeasurementTables.SourceContract.EXPIRY_TIME,
                        MeasurementTables.SourceContract.STATUS);

        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .rawQuery(
                                selectSourcesByDestination(
                                        triggerDestinationValue,
                                        trigger.getDestinationType(),
                                        sourceWhereStatement),
                                new String[] {
                                    trigger.getRegistrationOrigin().toString(),
                                    String.valueOf(trigger.getTriggerTime()),
                                    String.valueOf(trigger.getTriggerTime()),
                                    String.valueOf(Source.Status.ACTIVE)
                                })) {
            while (cursor.moveToNext()) {
                sources.add(SqliteObjectMapper.constructSourceFromCursor(cursor));
            }
            return sources;
        }
    }

    @Override
    public Optional<Source> getNearestDelayedMatchingActiveSource(@NonNull Trigger trigger)
            throws DatastoreException {
        Optional<String> destinationValue = getDestinationValue(trigger);
        if (!destinationValue.isPresent()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "getMatchingActiveDelayedSources: unable to obtain destination value:"
                                    + " %s",
                            trigger.getAttributionDestination().toString());
            return Optional.empty();
        }
        String triggerDestinationValue = destinationValue.get();
        String sourceWhereStatement =
                String.format(
                        "%1$s.%2$s = ? "
                                + "AND %1$s.%3$s > ? "
                                + "AND %1$s.%3$s <= ? "
                                + "AND %1$s.%4$s > ? "
                                + "AND %1$s.%5$s = ?",
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.SourceContract.REGISTRATION_ORIGIN,
                        MeasurementTables.SourceContract.EVENT_TIME,
                        MeasurementTables.SourceContract.EXPIRY_TIME,
                        MeasurementTables.SourceContract.STATUS);
        String sourceOrderByStatement =
                String.format(" ORDER BY %1$s ASC", MeasurementTables.SourceContract.EVENT_TIME);
        String sourceLimitStatement = String.format(" LIMIT %1$s", 1);
        long maxDelayedSourceRegistrationWindow =
                FlagsFactory.getFlags().getMeasurementMaxDelayedSourceRegistrationWindow();

        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .rawQuery(
                                selectSourcesByDestination(
                                                triggerDestinationValue,
                                                trigger.getDestinationType(),
                                                sourceWhereStatement)
                                        + sourceOrderByStatement
                                        + sourceLimitStatement,
                                new String[] {
                                    trigger.getRegistrationOrigin().toString(),
                                    String.valueOf(trigger.getTriggerTime()),
                                    String.valueOf(
                                            trigger.getTriggerTime()
                                                    + maxDelayedSourceRegistrationWindow),
                                    String.valueOf(trigger.getTriggerTime()),
                                    String.valueOf(Source.Status.ACTIVE)
                                })) {
            if (cursor.moveToNext()) {
                return Optional.of(SqliteObjectMapper.constructSourceFromCursor(cursor));
            }
            return Optional.empty();
        }
    }

    @Override
    public void updateTriggerStatus(Collection<String> triggerIds, @Trigger.Status int status)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.STATUS, status);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.TriggerContract.TABLE,
                                values,
                                MeasurementTables.TriggerContract.ID
                                        + " IN ("
                                        + Stream.generate(() -> "?")
                                                .limit(triggerIds.size())
                                                .collect(Collectors.joining(","))
                                        + ")",
                                triggerIds.toArray(new String[0]));
        if (rows != triggerIds.size()) {
            throw new DatastoreException("Trigger status update failed.");
        }
    }

    @Override
    public void updateSourceStatus(@NonNull Collection<String> sourceIds, @Source.Status int status)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.STATUS, status);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.SourceContract.TABLE,
                                values,
                                MeasurementTables.SourceContract.ID
                                        + " IN ("
                                        + Stream.generate(() -> "?")
                                                .limit(sourceIds.size())
                                                .collect(Collectors.joining(","))
                                        + ")",
                                sourceIds.toArray(new String[0]));
        if (rows != sourceIds.size()) {
            throw new DatastoreException("Source status update failed.");
        }
    }

    @Override
    public void updateSourceAttributedTriggers(@NonNull String sourceId,
            @Nullable String attributionStatus) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.SourceContract.EVENT_ATTRIBUTION_STATUS,
                attributionStatus);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.SourceContract.TABLE,
                                values,
                                MeasurementTables.SourceContract.ID + " = ?",
                                new String[] {sourceId});
        if (rows != 1) {
            throw new DatastoreException("Source  event attribution status update failed.");
        }
    }

    @Override
    public void updateSourceAggregateContributions(@NonNull Source source)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.SourceContract.AGGREGATE_CONTRIBUTIONS,
                source.getAggregateContributions());
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.SourceContract.TABLE,
                                values,
                                MeasurementTables.SourceContract.ID + " = ?",
                                new String[] {source.getId()});
        if (rows != 1) {
            throw new DatastoreException("Source aggregate contributions update failed.");
        }
    }

    @Override
    public void markEventReportStatus(@NonNull String eventReportId, @EventReport.Status int status)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.EventReportContract.STATUS, status);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.EventReportContract.TABLE,
                                values,
                                MeasurementTables.EventReportContract.ID + " = ?",
                                new String[] {eventReportId});
        if (rows != 1) {
            throw new DatastoreException("EventReport status update failed.");
        }
    }

    @Override
    public void updateEventReportSummaryBucket(
            @NonNull String eventReportId, @NonNull String summaryBucket)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.EventReportContract.TRIGGER_SUMMARY_BUCKET, summaryBucket);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.EventReportContract.TABLE,
                                values,
                                MeasurementTables.EventReportContract.ID + " = ?",
                                new String[] {eventReportId});
        if (rows != 1) {
            throw new DatastoreException("EventReport summary bucket update failed.");
        }
    }

    @Override
    public void markAggregateReportStatus(
            String aggregateReportId, @AggregateReport.Status int status)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AggregateReport.STATUS, status);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.AggregateReport.TABLE,
                                values,
                                MeasurementTables.AggregateReport.ID + " = ? ",
                                new String[] {aggregateReportId});
        if (rows != 1) {
            throw new DatastoreException("AggregateReport update failed");
        }
    }

    @Override
    public void markEventDebugReportDelivered(String eventReportId) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS,
                EventReport.DebugReportStatus.DELIVERED);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.EventReportContract.TABLE,
                                values,
                                MeasurementTables.EventReportContract.ID + " = ?",
                                new String[] {eventReportId});
        if (rows != 1) {
            throw new DatastoreException("EventReport update failed.");
        }
    }

    @Override
    public void markAggregateDebugReportDelivered(String aggregateReportId)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS,
                AggregateReport.DebugReportStatus.DELIVERED);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.AggregateReport.TABLE,
                                values,
                                MeasurementTables.AggregateReport.ID + " = ? ",
                                new String[] {aggregateReportId});
        if (rows != 1) {
            throw new DatastoreException("AggregateReport update failed");
        }
    }

    @Override
    @Nullable
    public List<EventReport> getSourceEventReports(Source source) throws DatastoreException {
        List<EventReport> eventReports = new ArrayList<>();
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.EventReportContract.TABLE,
                                /* columns= */ null,
                                MeasurementTables.EventReportContract.SOURCE_ID + " = ? ",
                                new String[] {source.getId()},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ null,
                                /* limit= */ null)) {
            while (cursor.moveToNext()) {
                eventReports.add(SqliteObjectMapper.constructEventReportFromCursor(cursor));
            }
            return eventReports;
        }
    }

    @Override
    public void deleteEventReport(EventReport eventReport) throws DatastoreException {
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .delete(
                                MeasurementTables.EventReportContract.TABLE,
                                MeasurementTables.EventReportContract.ID + " = ?",
                                new String[] {eventReport.getId()});
        if (rows != 1) {
            throw new DatastoreException("EventReport deletion failed.");
        }
    }

    @Override
    public void deleteDebugReport(String debugReportId) throws DatastoreException {
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .delete(
                                MeasurementTables.DebugReportContract.TABLE,
                                MeasurementTables.DebugReportContract.ID + " = ?",
                                new String[] {debugReportId});
        if (rows != 1) {
            throw new DatastoreException("DebugReport deletion failed.");
        }
        LoggerFactory.getMeasurementLogger()
                .d("MeasurementDao: deleteDebugReport: row deleted: " + rows);
    }

    @Override
    public int deleteDebugReports(
            @NonNull Uri registrant, @NonNull Instant start, @NonNull Instant end)
            throws DatastoreException {
        Objects.requireNonNull(registrant);
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        validateRange(start, end);
        Instant cappedStart = capDeletionRange(start);
        Instant cappedEnd = capDeletionRange(end);
        Function<String, String> registrantMatcher = getRegistrantMatcher(registrant);
        Function<String, String> timeMatcher = getTimeMatcher(cappedStart, cappedEnd);

        final SQLiteDatabase db = mSQLTransaction.getDatabase();
        return db.delete(
                MeasurementTables.DebugReportContract.TABLE,
                mergeConditions(
                        " AND ",
                        registrantMatcher.apply(MeasurementTables.DebugReportContract.REGISTRANT),
                        timeMatcher.apply(MeasurementTables.DebugReportContract.INSERTION_TIME)),
                /* whereArgs*/ null);
    }

    @Override
    public List<String> getPendingEventReportIdsInWindow(long windowStartTime, long windowEndTime)
            throws DatastoreException {
        List<String> eventReports = new ArrayList<>();
        try (Cursor cursor =
                mReportingRetryLimitEnabledSupplier.get()
                        ? pendingEventReportIdsInWindowCursorWithRetryLimit(
                                windowStartTime, windowEndTime)
                        : pendingEventReportIdsInWindowCursor(windowStartTime, windowEndTime)) {
            while (cursor.moveToNext()) {
                eventReports.add(
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.EventReportContract.ID)));
            }
            return eventReports;
        }
    }

    @Override
    public List<String> getPendingDebugEventReportIds() throws DatastoreException {
        List<String> eventReports = new ArrayList<>();
        try (Cursor cursor =
                mReportingRetryLimitEnabledSupplier.get()
                        ? pendingDebugEventReportIdsLimitRetryCursor()
                        : pendingDebugEventReportIdsCursor()) {
            while (cursor.moveToNext()) {
                eventReports.add(
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.EventReportContract.ID)));
            }
            return eventReports;
        }
    }

    @Override
    public List<String> getPendingEventReportIdsForGivenApp(Uri appName) throws DatastoreException {
        List<String> eventReports = new ArrayList<>();
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .rawQuery(
                                String.format(
                                        Locale.ENGLISH,
                                        "SELECT e.%1$s FROM %2$s e "
                                                + "INNER JOIN %3$s s ON (e.%4$s = s.%5$s) "
                                                + "WHERE e.%6$s = ? AND s.%7$s = ?",
                                        MeasurementTables.EventReportContract.ID,
                                        MeasurementTables.EventReportContract.TABLE,
                                        MeasurementTables.SourceContract.TABLE,
                                        MeasurementTables.EventReportContract.SOURCE_ID,
                                        MeasurementTables.SourceContract.ID,
                                        MeasurementTables.EventReportContract.STATUS,
                                        MeasurementTables.SourceContract.REGISTRANT),
                                new String[] {
                                    String.valueOf(EventReport.Status.PENDING),
                                    String.valueOf(appName)
                                })) {
            while (cursor.moveToNext()) {
                eventReports.add(
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.EventReportContract.ID)));
            }
            return eventReports;
        }
    }

    @Override
    public void insertEventReport(EventReport eventReport) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.EventReportContract.ID, UUID.randomUUID().toString());
        values.put(
                MeasurementTables.EventReportContract.SOURCE_EVENT_ID,
                eventReport.getSourceEventId().getValue());
        values.put(
                MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                String.join(
                        " ",
                        eventReport.getAttributionDestinations().stream()
                                .map(Uri::toString)
                                .collect(Collectors.toList())));
        values.put(
                MeasurementTables.EventReportContract.TRIGGER_TIME, eventReport.getTriggerTime());
        values.put(
                MeasurementTables.EventReportContract.TRIGGER_DATA,
                getNullableUnsignedLong(eventReport.getTriggerData()));
        values.put(
                MeasurementTables.EventReportContract.TRIGGER_DEDUP_KEY,
                getNullableUnsignedLong(eventReport.getTriggerDedupKey()));
        values.put(
                MeasurementTables.EventReportContract.ENROLLMENT_ID, eventReport.getEnrollmentId());
        values.put(MeasurementTables.EventReportContract.STATUS, eventReport.getStatus());
        values.put(
                MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS,
                eventReport.getDebugReportStatus());
        values.put(MeasurementTables.EventReportContract.REPORT_TIME, eventReport.getReportTime());
        values.put(
                MeasurementTables.EventReportContract.TRIGGER_PRIORITY,
                eventReport.getTriggerPriority());
        values.put(
                MeasurementTables.EventReportContract.SOURCE_TYPE,
                eventReport.getSourceType().toString());
        values.put(
                MeasurementTables.EventReportContract.RANDOMIZED_TRIGGER_RATE,
                eventReport.getRandomizedTriggerRate());
        values.put(
                MeasurementTables.EventReportContract.SOURCE_DEBUG_KEY,
                getNullableUnsignedLong(eventReport.getSourceDebugKey()));
        values.put(
                MeasurementTables.EventReportContract.TRIGGER_DEBUG_KEY,
                getNullableUnsignedLong(eventReport.getTriggerDebugKey()));
        values.put(
                MeasurementTables.EventReportContract.TRIGGER_DEBUG_KEYS,
                listToCommaSeparatedString(eventReport.getTriggerDebugKeys()));
        values.put(MeasurementTables.EventReportContract.SOURCE_ID, eventReport.getSourceId());
        values.put(MeasurementTables.EventReportContract.TRIGGER_ID, eventReport.getTriggerId());
        values.put(
                MeasurementTables.EventReportContract.REGISTRATION_ORIGIN,
                eventReport.getRegistrationOrigin().toString());
        values.put(
                MeasurementTables.EventReportContract.TRIGGER_SUMMARY_BUCKET,
                eventReport.getStringEncodedTriggerSummaryBucket());
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.EventReportContract.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        if (rowId == -1) {
            throw new DatastoreException("EventReport insertion failed.");
        }
    }

    @Override
    public void updateSourceEventReportDedupKeys(@NonNull Source source) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.SourceContract.EVENT_REPORT_DEDUP_KEYS,
                listToCommaSeparatedString(source.getEventReportDedupKeys()));
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.SourceContract.TABLE,
                                values,
                                MeasurementTables.SourceContract.ID + " = ?",
                                new String[] {source.getId()});
        if (rows != 1) {
            throw new DatastoreException("Source event report dedup key updated failed.");
        }
    }

    @Override
    public void updateSourceAggregateReportDedupKeys(@NonNull Source source)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.SourceContract.AGGREGATE_REPORT_DEDUP_KEYS,
                listToCommaSeparatedString(source.getAggregateReportDedupKeys()));
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.SourceContract.TABLE,
                                values,
                                MeasurementTables.SourceContract.ID + " = ?",
                                new String[] {source.getId()});
        if (rows != 1) {
            throw new DatastoreException("Source aggregate report dedup key updated failed.");
        }
    }

    @Override
    public void insertAttribution(@NonNull Attribution attribution) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AttributionContract.ID, UUID.randomUUID().toString());
        values.put(MeasurementTables.AttributionContract.SCOPE, attribution.getScope());
        values.put(MeasurementTables.AttributionContract.SOURCE_SITE, attribution.getSourceSite());
        values.put(
                MeasurementTables.AttributionContract.SOURCE_ORIGIN, attribution.getSourceOrigin());
        values.put(
                MeasurementTables.AttributionContract.DESTINATION_SITE,
                attribution.getDestinationSite());
        values.put(
                MeasurementTables.AttributionContract.DESTINATION_ORIGIN,
                attribution.getDestinationOrigin());
        values.put(
                MeasurementTables.AttributionContract.ENROLLMENT_ID, attribution.getEnrollmentId());
        values.put(
                MeasurementTables.AttributionContract.TRIGGER_TIME, attribution.getTriggerTime());
        values.put(MeasurementTables.AttributionContract.REGISTRANT, attribution.getRegistrant());
        values.put(MeasurementTables.AttributionContract.SOURCE_ID, attribution.getSourceId());
        values.put(MeasurementTables.AttributionContract.TRIGGER_ID, attribution.getTriggerId());
        values.put(
                MeasurementTables.AttributionContract.REGISTRATION_ORIGIN,
                attribution.getRegistrationOrigin().toString());
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.AttributionContract.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        if (rowId == -1) {
            throw new DatastoreException("Attribution insertion failed.");
        }
    }

    @Override
    public long getAttributionsPerRateLimitWindow(@NonNull Source source, @NonNull Trigger trigger)
            throws DatastoreException {
        Optional<Uri> publisherBaseUri =
                extractBaseUri(source.getPublisher(), source.getPublisherType());
        Optional<Uri> destinationBaseUri =
                extractBaseUri(trigger.getAttributionDestination(), trigger.getDestinationType());

        if (publisherBaseUri.isEmpty() || destinationBaseUri.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(
                            Locale.ENGLISH,
                            "getAttributionsPerRateLimitWindow:"
                                    + " getSourceAndDestinationTopPrivateDomains failed. Publisher:"
                                    + " %s; Attribution destination: %s",
                            source.getPublisher().toString(),
                            trigger.getAttributionDestination().toString()));
        }

        String publisherTopPrivateDomain = publisherBaseUri.get().toString();
        String triggerDestinationTopPrivateDomain = destinationBaseUri.get().toString();

        return DatabaseUtils.queryNumEntries(
                mSQLTransaction.getDatabase(),
                MeasurementTables.AttributionContract.TABLE,
                MeasurementTables.AttributionContract.SOURCE_SITE
                        + " = ? AND "
                        + MeasurementTables.AttributionContract.DESTINATION_SITE
                        + " = ? AND "
                        + MeasurementTables.AttributionContract.ENROLLMENT_ID
                        + " = ? AND "
                        + MeasurementTables.AttributionContract.TRIGGER_TIME
                        + " > ? AND "
                        + MeasurementTables.AttributionContract.TRIGGER_TIME
                        + " <= ? ",
                new String[] {
                    publisherTopPrivateDomain,
                    triggerDestinationTopPrivateDomain,
                    trigger.getEnrollmentId(),
                    String.valueOf(
                            trigger.getTriggerTime()
                                    - FlagsFactory.getFlags()
                                            .getMeasurementRateLimitWindowMilliseconds()),
                    String.valueOf(trigger.getTriggerTime())
                });
    }

    @Override
    public long getAttributionsPerRateLimitWindow(@Attribution.Scope int scope,
            @NonNull Source source, @NonNull Trigger trigger) throws DatastoreException {
        Optional<Uri> publisherBaseUri =
                extractBaseUri(source.getPublisher(), source.getPublisherType());
        Optional<Uri> destinationBaseUri =
                extractBaseUri(trigger.getAttributionDestination(), trigger.getDestinationType());

        if (publisherBaseUri.isEmpty() || destinationBaseUri.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(
                            Locale.ENGLISH,
                            "getAttributionsPerRateLimitWindow:"
                                    + " getSourceAndDestinationTopPrivateDomains failed. Publisher:"
                                    + " %s; Attribution destination: %s",
                            source.getPublisher().toString(),
                            trigger.getAttributionDestination().toString()));
        }

        String publisherTopPrivateDomain = publisherBaseUri.get().toString();
        String triggerDestinationTopPrivateDomain = destinationBaseUri.get().toString();

        return DatabaseUtils.queryNumEntries(
                mSQLTransaction.getDatabase(),
                MeasurementTables.AttributionContract.TABLE,
                MeasurementTables.AttributionContract.SCOPE
                        + " = ? AND "
                        + MeasurementTables.AttributionContract.SOURCE_SITE
                        + " = ? AND "
                        + MeasurementTables.AttributionContract.DESTINATION_SITE
                        + " = ? AND "
                        + MeasurementTables.AttributionContract.ENROLLMENT_ID
                        + " = ? AND "
                        + MeasurementTables.AttributionContract.TRIGGER_TIME
                        + " > ? AND "
                        + MeasurementTables.AttributionContract.TRIGGER_TIME
                        + " <= ? ",
                new String[] {
                    String.valueOf(scope),
                    publisherTopPrivateDomain,
                    triggerDestinationTopPrivateDomain,
                    trigger.getEnrollmentId(),
                    String.valueOf(
                            trigger.getTriggerTime()
                                    - FlagsFactory.getFlags()
                                            .getMeasurementRateLimitWindowMilliseconds()),
                    String.valueOf(trigger.getTriggerTime())
                });
    }

    @Override
    public long getNumSourcesPerPublisher(Uri publisherUri, @EventSurfaceType int publisherType)
            throws DatastoreException {
        return DatabaseUtils.queryNumEntries(
                mSQLTransaction.getDatabase(),
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.PUBLISHER + " = ?",
                new String[] {publisherUri.toString()});
    }

    @Override
    public long getNumTriggersPerDestination(Uri destination, @EventSurfaceType int destinationType)
            throws DatastoreException {
        return DatabaseUtils.queryNumEntries(
                mSQLTransaction.getDatabase(),
                MeasurementTables.TriggerContract.TABLE,
                getDestinationWhereStatement(destination, destinationType));
    }

    @Override
    public List<Uri> getUninstalledAppNamesHavingMeasurementData(List<Uri> installedApps)
            throws DatastoreException {
        final List<Uri> uninstallAppNames = new ArrayList<>();

        final String installedAppsFormatted = getUriValueList(installedApps);
        final String query =
                String.format(
                        Locale.ENGLISH,
                        "SELECT %2$s FROM %1$s WHERE %2$s NOT IN %7$s "
                                + "UNION "
                                + "SELECT %4$s FROM %3$s WHERE %4$s NOT IN %7$s "
                                + "UNION "
                                + "SELECT %6$s FROM %5$s WHERE %6$s NOT IN %7$s",
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.SourceContract.REGISTRANT,
                        MeasurementTables.TriggerContract.TABLE,
                        MeasurementTables.TriggerContract.REGISTRANT,
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        MeasurementTables.AsyncRegistrationContract.REGISTRANT,
                        installedAppsFormatted);
        try (Cursor cursor =
                mSQLTransaction.getDatabase().rawQuery(query, /* selectionArgs= */ null)) {
            while (cursor.moveToNext()) {
                uninstallAppNames.add(
                        Uri.parse(
                                cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                                MeasurementTables.SourceContract.REGISTRANT))));
            }
        }
        return uninstallAppNames;
    }

    @Override
    public Integer countDistinctReportingOriginsPerPublisherXDestInAttribution(
            Uri sourceSite,
            Uri destinationSite,
            Uri excludedReportingOrigin,
            long windowStartTime,
            long windowEndTime)
            throws DatastoreException {
        String query =
                String.format(
                        Locale.ENGLISH,
                        "SELECT COUNT(DISTINCT %1$s) FROM %2$s "
                                + "WHERE %3$s = ? AND %4$s = ? AND %1s != ? "
                                + "AND %5$s > ? AND %5$s <= ?",
                        MeasurementTables.AttributionContract.REGISTRATION_ORIGIN,
                        MeasurementTables.AttributionContract.TABLE,
                        MeasurementTables.AttributionContract.SOURCE_SITE,
                        MeasurementTables.AttributionContract.DESTINATION_SITE,
                        MeasurementTables.AttributionContract.TRIGGER_TIME);
        return (int)
                DatabaseUtils.longForQuery(
                        mSQLTransaction.getDatabase(),
                        query,
                        new String[] {
                            sourceSite.toString(),
                            destinationSite.toString(),
                            excludedReportingOrigin.toString(),
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime)
                        });
    }

    @Override
    public Integer countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
            Uri publisher,
            @EventSurfaceType int publisherType,
            String enrollmentId,
            List<Uri> excludedDestinations,
            @EventSurfaceType int destinationType,
            long windowStartTime,
            long windowEndTime)
            throws DatastoreException {
        String query =
                String.format(
                        Locale.ENGLISH,
                        "WITH source_ids AS ("
                                + "SELECT %1$s FROM %2$s "
                                + "WHERE %3$s AND %4$s = ? "
                                + "AND %5$s > ? AND %5$s <= ? AND %6$s > ?"
                                + ") "
                                + "SELECT COUNT(DISTINCT %7$s) FROM %8$s "
                                + "WHERE %9$s IN source_ids AND %10$s = ? "
                                + "AND %7$s NOT IN "
                                + getUriValueList(excludedDestinations),
                        MeasurementTables.SourceContract.ID,
                        MeasurementTables.SourceContract.TABLE,
                        getPublisherWhereStatement(publisher, publisherType),
                        MeasurementTables.SourceContract.ENROLLMENT_ID,
                        MeasurementTables.SourceContract.EVENT_TIME,
                        MeasurementTables.SourceContract.EXPIRY_TIME,
                        MeasurementTables.SourceDestination.DESTINATION,
                        MeasurementTables.SourceDestination.TABLE,
                        MeasurementTables.SourceDestination.SOURCE_ID,
                        MeasurementTables.SourceDestination.DESTINATION_TYPE);
        return (int)
                DatabaseUtils.longForQuery(
                        mSQLTransaction.getDatabase(),
                        query,
                        new String[] {
                            enrollmentId,
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(destinationType)
                        });
    }

    @Override
    public Integer countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
            Uri publisher,
            @EventSurfaceType int publisherType,
            String enrollmentId,
            List<Uri> excludedDestinations,
            @EventSurfaceType int destinationType,
            long windowEndTime)
            throws DatastoreException {
        String query =
                String.format(
                        Locale.ENGLISH,
                        "WITH source_ids AS ("
                                + "SELECT %1$s FROM %2$s "
                                + "WHERE %3$s AND %4$s = ? "
                                + "AND %5$s > ?"
                                + ") "
                                + "SELECT COUNT(DISTINCT %6$s) FROM %7$s "
                                + "WHERE %8$s IN source_ids AND %9$s = ? "
                                + "AND %6$s NOT IN "
                                + getUriValueList(excludedDestinations),
                        MeasurementTables.SourceContract.ID,
                        MeasurementTables.SourceContract.TABLE,
                        getPublisherWhereStatement(publisher, publisherType),
                        MeasurementTables.SourceContract.ENROLLMENT_ID,
                        MeasurementTables.SourceContract.EXPIRY_TIME,
                        MeasurementTables.SourceDestination.DESTINATION,
                        MeasurementTables.SourceDestination.TABLE,
                        MeasurementTables.SourceDestination.SOURCE_ID,
                        MeasurementTables.SourceDestination.DESTINATION_TYPE);
        return (int)
                DatabaseUtils.longForQuery(
                        mSQLTransaction.getDatabase(),
                        query,
                        new String[] {
                            enrollmentId,
                            String.valueOf(windowEndTime),
                            String.valueOf(destinationType)
                        });
    }

    @Override
    public Integer countDistinctDestinationsPerPublisherPerRateLimitWindow(
            Uri publisher,
            @EventSurfaceType int publisherType,
            List<Uri> excludedDestinations,
            @EventSurfaceType int destinationType,
            long windowStartTime,
            long windowEndTime)
            throws DatastoreException {
        String query =
                String.format(
                        Locale.ENGLISH,
                        "WITH source_ids AS ("
                                + "SELECT %1$s FROM %2$s "
                                + "WHERE %3$s "
                                + "AND %4$s > ? AND %4$s <= ? AND %5$s > ?"
                                + ") "
                                + "SELECT COUNT(DISTINCT %6$s) FROM %7$s "
                                + "WHERE %8$s IN source_ids AND %9$s = ? "
                                + "AND %6$s NOT IN "
                                + getUriValueList(excludedDestinations),
                        MeasurementTables.SourceContract.ID,
                        MeasurementTables.SourceContract.TABLE,
                        getPublisherWhereStatement(publisher, publisherType),
                        MeasurementTables.SourceContract.EVENT_TIME,
                        MeasurementTables.SourceContract.EXPIRY_TIME,
                        MeasurementTables.SourceDestination.DESTINATION,
                        MeasurementTables.SourceDestination.TABLE,
                        MeasurementTables.SourceDestination.SOURCE_ID,
                        MeasurementTables.SourceDestination.DESTINATION_TYPE);
        return (int)
                DatabaseUtils.longForQuery(
                        mSQLTransaction.getDatabase(),
                        query,
                        new String[] {
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(destinationType)
                        });
    }

    @Override
    public Integer countSourcesPerPublisherXEnrollmentExcludingRegOrigin(
            Uri registrationOrigin,
            Uri publisher,
            @EventSurfaceType int publisherType,
            String enrollmentId,
            long eventTime,
            long timePeriodInMs)
            throws DatastoreException {

        String query =
                String.format(
                        Locale.ENGLISH,
                        "SELECT COUNT (*) FROM %1$s "
                                + "WHERE %2$s AND "
                                + "%3$s = ? AND "
                                + "%4$s != ? AND "
                                + "%5$s > ?",
                        MeasurementTables.SourceContract.TABLE,
                        getPublisherWhereStatement(publisher, publisherType),
                        MeasurementTables.SourceContract.ENROLLMENT_ID,
                        MeasurementTables.SourceContract.REGISTRATION_ORIGIN,
                        MeasurementTables.SourceContract.EVENT_TIME);

        return (int)
                DatabaseUtils.longForQuery(
                        mSQLTransaction.getDatabase(),
                        query,
                        new String[] {
                            enrollmentId,
                            registrationOrigin.toString(),
                            String.valueOf(eventTime - timePeriodInMs)
                        });
    }

    @Override
    public Integer countDistinctReportingOriginsPerPublisherXDestinationInSource(
            Uri publisher,
            @EventSurfaceType int publisherType,
            List<Uri> destinations,
            Uri excludedReportingOrigin,
            long windowStartTime,
            long windowEndTime)
            throws DatastoreException {
        // Each destination can be paired with the given publisher. Return the maximum count of
        // distinct reporting origins among the pairs of destination-and-publisher.
        String query =
                String.format(
                        Locale.ENGLISH,
                        "WITH joined as ("
                                + "SELECT source."
                                + MeasurementTables.SourceContract.REGISTRATION_ORIGIN
                                + ", "
                                + "source_dest."
                                + MeasurementTables.SourceDestination.DESTINATION
                                + " "
                                + "FROM "
                                + MeasurementTables.SourceContract.TABLE
                                + " source "
                                + "INNER JOIN "
                                + MeasurementTables.SourceDestination.TABLE
                                + " source_dest"
                                + " ON (source."
                                + MeasurementTables.SourceContract.ID
                                + " = "
                                + "source_dest."
                                + MeasurementTables.SourceDestination.SOURCE_ID
                                + ") "
                                + "WHERE %1$s "
                                + "AND source."
                                + MeasurementTables.SourceContract.REGISTRATION_ORIGIN
                                + " != ? "
                                + "AND source."
                                + MeasurementTables.SourceContract.EVENT_TIME
                                + " > ? "
                                + "AND source."
                                + MeasurementTables.SourceContract.EVENT_TIME
                                + " <= ? "
                                + "AND source_dest."
                                + MeasurementTables.SourceDestination.DESTINATION
                                + " IN %2$s), distinct_registration_origins as (SELECT DENSE_RANK()"
                                + " OVER (PARTITION BY destination ORDER BY registration_origin) AS"
                                + " distinct_registration_origin FROM joined) SELECT"
                                + " MAX(distinct_registration_origin) FROM"
                                + " distinct_registration_origins",
                        getPublisherWhereStatement(publisher, publisherType),
                        getUriValueList(destinations));

        return (int)
                DatabaseUtils.longForQuery(
                        mSQLTransaction.getDatabase(),
                        query,
                        new String[] {
                            excludedReportingOrigin.toString(),
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime)
                        });
    }

    @Override
    public List<AggregateReport> fetchMatchingAggregateReports(
            @NonNull Collection<String> sourceIds, @NonNull Collection<String> triggerIds)
            throws DatastoreException {
        return fetchRecordsMatchingWithParameters(
                MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.SOURCE_ID,
                sourceIds,
                MeasurementTables.AggregateReport.TRIGGER_ID,
                triggerIds,
                SqliteObjectMapper::constructAggregateReport);
    }

    @Override
    public List<EventReport> fetchMatchingEventReports(
            @NonNull Collection<String> sourceIds, @NonNull Collection<String> triggerIds)
            throws DatastoreException {
        return fetchRecordsMatchingWithParameters(
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.SOURCE_ID,
                sourceIds,
                MeasurementTables.EventReportContract.TRIGGER_ID,
                triggerIds,
                SqliteObjectMapper::constructEventReportFromCursor);
    }

    @Override
    public Set<String> fetchFlexSourceIdsFor(@NonNull Collection<String> triggerIds)
            throws DatastoreException {
        Set<String> sourceIds = new HashSet<>();
        if (triggerIds.isEmpty()) {
            return new HashSet<>();
        }

        // Reduce the number of trigger IDs to query to a set and convert to a list for batching.
        List<String> triggerIdsList = new ArrayList<>(new HashSet<>(triggerIds));

        // SQlite limits the number of compound SELECT statements so we need to process them in
        // batches.
        int i = 0;
        while (i < triggerIdsList.size()) {
            int sublistEnd = Math.min(i + MAX_COMPOUND_SELECT, triggerIdsList.size());

            List<String> queries = triggerIdsList.subList(i, sublistEnd).stream().map(
                    triggerId ->
                            "SELECT * FROM " + MeasurementTables.SourceContract.TABLE
                                    + " WHERE "
                                        + MeasurementTables.SourceContract.TRIGGER_SPECS
                                        + " IS NOT NULL"
                                    + " AND "
                                        + MeasurementTables.SourceContract.EVENT_ATTRIBUTION_STATUS
                                        + " LIKE '%\"" + triggerId  + "\"%'")
                    .collect(Collectors.toList());

            String unionQuery = String.join(" UNION ", queries);

            try (Cursor cursor =
                    mSQLTransaction
                            .getDatabase()
                            .rawQuery(
                                    String.format(
                                            Locale.ENGLISH,
                                            "SELECT DISTINCT %s FROM (%s)",
                                            MeasurementTables.SourceContract.ID,
                                            unionQuery),
                                    null)) {
                while (cursor.moveToNext()) {
                    String sourceId =
                            cursor.getString(
                                    cursor.getColumnIndexOrThrow(
                                            MeasurementTables.SourceContract.ID));
                    sourceIds.add(sourceId);
                }
            }
            i += MAX_COMPOUND_SELECT;
        }
        return sourceIds;
    }

    @Override
    public void deleteFlexEventReportsAndAttributions(List<EventReport> eventReports)
            throws DatastoreException {
        if (eventReports.isEmpty()) {
            return;
        }

        String sourceId = eventReports.get(0).getSourceId();

        Map<String, List<String>> triggerIdToEventReportIdsMap =
                eventReports
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        EventReport::getTriggerId,
                                        Collectors.mapping(
                                                EventReport::getId, Collectors.toList())));

        SQLiteDatabase db = mSQLTransaction.getDatabase();

        for (Map.Entry<String, List<String>> entry : triggerIdToEventReportIdsMap.entrySet()) {
            String triggerId = entry.getKey();
            List<String> eventReportIds = entry.getValue();

            String eventReportsWhereStatement = MeasurementTables.EventReportContract.ID
                    + " IN ('" + String.join("','", eventReportIds) + "')";

            db.delete(
                    MeasurementTables.EventReportContract.TABLE,
                    eventReportsWhereStatement,
                    new String[0]);

            // There can be multiple attributions per source ID / trigger ID matching pair, so we
            // limit the number of attributions deleted to the number of event reports provided that
            // were associated with the trigger ID.
            String attributionWhereStatement = MeasurementTables.AttributionContract.ID
                    + " IN ("
                        + "SELECT " + MeasurementTables.AttributionContract.ID
                        + " FROM " + MeasurementTables.AttributionContract.TABLE
                        + " WHERE " + MeasurementTables.AttributionContract.SCOPE + " = ?"
                        + " AND " + MeasurementTables.AttributionContract.SOURCE_ID + " = ?"
                        + " AND " + MeasurementTables.AttributionContract.TRIGGER_ID + " = ?"
                        + " LIMIT " + String.valueOf(eventReportIds.size())
                        + ")";

            db.delete(
                    MeasurementTables.AttributionContract.TABLE,
                    attributionWhereStatement,
                    new String[] {
                            String.valueOf(Attribution.Scope.EVENT),
                            sourceId,
                            triggerId });
        }
    }

    private String getUriValueList(List<Uri> uriList) {
        // Construct query, as list of all packages present on the device
        StringBuilder valueList = new StringBuilder("(");
        valueList.append(
                uriList.stream()
                        .map((uri) -> DatabaseUtils.sqlEscapeString(uri.toString()))
                        .collect(Collectors.joining(", ")));
        valueList.append(")");
        return valueList.toString();
    }

    @Override
    public void deleteExpiredRecords(long earliestValidInsertion, int registrationRetryLimit)
            throws DatastoreException {
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        String earliestValidInsertionStr = String.valueOf(earliestValidInsertion);
        // Deleting the sources and triggers will take care of deleting records from
        // event report, aggregate report and attribution tables as well. No explicit deletion is
        // required for them. Although, having proactive deletion of expired records help clean up
        // space.
        // Source table
        db.delete(
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.EVENT_TIME + " < ?",
                new String[] {earliestValidInsertionStr});
        // Trigger table
        db.delete(
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.TRIGGER_TIME + " < ?",
                new String[] {earliestValidInsertionStr});
        // Event Reports
        // TODO(b/277362712): Optimize Deletion of event reports
        // We rely on source/trigger deletion to delete event reports because delivered event
        // reports are used for calculating total event reports for a Source during Attribution.

        // AggregateReport table
        db.delete(
                MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.STATUS
                        + " = ? OR "
                        + MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME
                        + " < ?",
                new String[] {
                    String.valueOf(AggregateReport.Status.DELIVERED), earliestValidInsertionStr
                });
        // Attribution table
        db.delete(
                MeasurementTables.AttributionContract.TABLE,
                MeasurementTables.AttributionContract.TRIGGER_TIME + " < ?",
                new String[] {earliestValidInsertionStr});
        // Async Registration table
        db.delete(
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTables.AsyncRegistrationContract.REQUEST_TIME + " < ? OR "
                        + MeasurementTables.AsyncRegistrationContract.RETRY_COUNT + " >= ? ",
                new String[] {earliestValidInsertionStr, String.valueOf(registrationRetryLimit)});

        // Cleanup unnecessary Registration Redirect Counts
        String subQuery =
                "SELECT "
                        + "DISTINCT("
                        + MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID
                        + ")"
                        + " FROM "
                        + MeasurementTables.AsyncRegistrationContract.TABLE;
        db.delete(
                MeasurementTables.KeyValueDataContract.TABLE,
                MeasurementTables.KeyValueDataContract.DATA_TYPE
                        + " = ? "
                        + " AND "
                        + MeasurementTables.KeyValueDataContract.KEY
                        + " NOT IN "
                        + "("
                        + subQuery
                        + ")",
                new String[] {KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT.toString()});

        // When Limiting Retries, consider Verbose Debug Reports Expired when Exceeds Limit.
        if (mReportingRetryLimitEnabledSupplier.get()) {
            db.delete(
                    MeasurementTables.DebugReportContract.TABLE,
                    MeasurementTables.DebugReportContract.ID
                            + " IN ("
                            + "SELECT "
                            + MeasurementTables.DebugReportContract.ID
                            + " FROM "
                            + MeasurementTables.DebugReportContract.TABLE
                            + " LEFT JOIN "
                            + MeasurementTables.KeyValueDataContract.TABLE
                            + " ON ("
                            + MeasurementTables.DebugReportContract.ID
                            + " = "
                            + MeasurementTables.KeyValueDataContract.KEY
                            + ") "
                            + "WHERE CAST("
                            + MeasurementTables.KeyValueDataContract.VALUE
                            + " AS INTEGER) >= ? "
                            + "AND "
                            + MeasurementTables.KeyValueDataContract.DATA_TYPE
                            + " = ? "
                            + ")",
                    new String[] {
                        mReportingRetryLimitSupplier.get().toString(),
                        DataType.DEBUG_REPORT_RETRY_COUNT.toString()
                    });
        } else {
            db.delete(
                    MeasurementTables.DebugReportContract.TABLE,
                    MeasurementTables.DebugReportContract.INSERTION_TIME + " < ?",
                    new String[] {earliestValidInsertionStr});
        }

        // Cleanup unnecessary AggregateReport Retry Counts
        subQuery =
                "SELECT "
                        + MeasurementTables.AggregateReport.ID
                        + " FROM "
                        + MeasurementTables.AggregateReport.TABLE;
        db.delete(
                MeasurementTables.KeyValueDataContract.TABLE,
                MeasurementTables.KeyValueDataContract.DATA_TYPE
                        + " IN (\""
                        + DataType.AGGREGATE_REPORT_RETRY_COUNT
                        + "\", \""
                        + DataType.DEBUG_AGGREGATE_REPORT_RETRY_COUNT
                        + "\")"
                        + " AND "
                        + MeasurementTables.KeyValueDataContract.KEY
                        + " NOT IN "
                        + "("
                        + subQuery
                        + ")",
                new String[] {});
        // Cleanup unnecessary DebugReport Retry Counts
        subQuery =
                "SELECT "
                        + MeasurementTables.DebugReportContract.ID
                        + " FROM "
                        + MeasurementTables.DebugReportContract.TABLE;
        db.delete(
                MeasurementTables.KeyValueDataContract.TABLE,
                MeasurementTables.KeyValueDataContract.DATA_TYPE
                        + " = ? "
                        + " AND "
                        + MeasurementTables.KeyValueDataContract.KEY
                        + " NOT IN "
                        + "("
                        + subQuery
                        + ")",
                new String[] {DataType.DEBUG_REPORT_RETRY_COUNT.toString()});
        // Cleanup unnecessary EventReport Retry Counts
        subQuery =
                "SELECT "
                        + MeasurementTables.EventReportContract.ID
                        + " FROM "
                        + MeasurementTables.EventReportContract.TABLE;
        db.delete(
                MeasurementTables.KeyValueDataContract.TABLE,
                MeasurementTables.KeyValueDataContract.DATA_TYPE
                        + " IN (\""
                        + DataType.EVENT_REPORT_RETRY_COUNT
                        + "\", \""
                        + DataType.DEBUG_EVENT_REPORT_RETRY_COUNT
                        + "\")"
                        + " AND "
                        + MeasurementTables.KeyValueDataContract.KEY
                        + " NOT IN "
                        + "("
                        + subQuery
                        + ")",
                new String[] {});
    }

    @Override
    public List<String> fetchMatchingSources(
            @NonNull Uri registrant,
            @NonNull Instant start,
            @NonNull Instant end,
            @NonNull List<Uri> origins,
            @NonNull List<Uri> domains,
            // TODO: change this to selection and invert selection mode
            @DeletionRequest.MatchBehavior int matchBehavior)
            throws DatastoreException {
        Objects.requireNonNull(registrant);
        Objects.requireNonNull(origins);
        Objects.requireNonNull(domains);
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        validateRange(start, end);
        Instant cappedStart = capDeletionRange(start);
        Instant cappedEnd = capDeletionRange(end);
        Function<String, String> registrantMatcher = getRegistrantMatcher(registrant);
        Function<String, String> siteMatcher = getSiteMatcher(origins, domains, matchBehavior);
        Function<String, String> timeMatcher = getTimeMatcher(cappedStart, cappedEnd);

        final SQLiteDatabase db = mSQLTransaction.getDatabase();
        ImmutableList.Builder<String> sourceIds = new ImmutableList.Builder<>();
        try (Cursor cursor =
                db.query(
                        MeasurementTables.SourceContract.TABLE,
                        new String[] {MeasurementTables.SourceContract.ID},
                        mergeConditions(
                                " AND ",
                                registrantMatcher.apply(
                                        MeasurementTables.SourceContract.REGISTRANT),
                                siteMatcher.apply(MeasurementTables.SourceContract.PUBLISHER),
                                timeMatcher.apply(MeasurementTables.SourceContract.EVENT_TIME)),
                        null,
                        null,
                        null,
                        null)) {
            while (cursor.moveToNext()) {
                sourceIds.add(cursor.getString(0));
            }
        }

        return sourceIds.build();
    }

    @Override
    public Set<String> fetchMatchingTriggers(
            @NonNull Uri registrant,
            @NonNull Instant start,
            @NonNull Instant end,
            @NonNull List<Uri> origins,
            @NonNull List<Uri> domains,
            // TODO: change this to selection and invert selection mode
            @DeletionRequest.MatchBehavior int matchBehavior)
            throws DatastoreException {
        Objects.requireNonNull(registrant);
        Objects.requireNonNull(origins);
        Objects.requireNonNull(domains);
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        validateRange(start, end);
        Instant cappedStart = capDeletionRange(start);
        Instant cappedEnd = capDeletionRange(end);
        Function<String, String> registrantMatcher = getRegistrantMatcher(registrant);
        Function<String, String> siteMatcher = getSiteMatcher(origins, domains, matchBehavior);
        Function<String, String> timeMatcher = getTimeMatcher(cappedStart, cappedEnd);

        final SQLiteDatabase db = mSQLTransaction.getDatabase();
        // Mutable set, as the caller may add trigger IDs from flex sources.
        Set<String> triggerIds = new HashSet<>();
        try (Cursor cursor =
                db.query(
                        MeasurementTables.TriggerContract.TABLE,
                        new String[] {MeasurementTables.TriggerContract.ID},
                        mergeConditions(
                                " AND ",
                                registrantMatcher.apply(
                                        MeasurementTables.TriggerContract.REGISTRANT),
                                siteMatcher.apply(
                                        MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION),
                                timeMatcher.apply(MeasurementTables.TriggerContract.TRIGGER_TIME)),
                        null,
                        null,
                        null,
                        null)) {
            while (cursor.moveToNext()) {
                triggerIds.add(cursor.getString(0));
            }
        }

        return triggerIds;
    }

    @Override
    public List<String> fetchMatchingAsyncRegistrations(
            @NonNull Uri registrant,
            @NonNull Instant start,
            @NonNull Instant end,
            @NonNull List<Uri> origins,
            @NonNull List<Uri> domains,
            // TODO: change this to selection and invert selection mode
            @DeletionRequest.MatchBehavior int matchBehavior)
            throws DatastoreException {
        Objects.requireNonNull(registrant);
        Objects.requireNonNull(origins);
        Objects.requireNonNull(domains);
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        validateRange(start, end);
        Instant cappedStart = capDeletionRange(start);
        Instant cappedEnd = capDeletionRange(end);
        Function<String, String> registrantMatcher = getRegistrantMatcher(registrant);
        Function<String, String> siteMatcher = getSiteMatcher(origins, domains, matchBehavior);
        Function<String, String> timeMatcher = getTimeMatcher(cappedStart, cappedEnd);

        final SQLiteDatabase db = mSQLTransaction.getDatabase();
        ImmutableList.Builder<String> asyncRegistrationIds = new ImmutableList.Builder<>();
        try (Cursor cursor =
                db.query(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        new String[] {MeasurementTables.AsyncRegistrationContract.ID},
                        mergeConditions(
                                " AND ",
                                registrantMatcher.apply(
                                        MeasurementTables.AsyncRegistrationContract.REGISTRANT),
                                siteMatcher.apply(
                                        MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN),
                                timeMatcher.apply(
                                        MeasurementTables.AsyncRegistrationContract.REQUEST_TIME)),
                        null,
                        null,
                        null,
                        null)) {
            while (cursor.moveToNext()) {
                asyncRegistrationIds.add(cursor.getString(0));
            }
        }

        return asyncRegistrationIds.build();
    }

    private static Function<String, String> getRegistrantMatcher(Uri registrant) {
        return (String columnName) ->
                columnName + " = " + DatabaseUtils.sqlEscapeString(registrant.toString());
    }

    private String listToCommaSeparatedString(List<UnsignedLong> list) {
        return list.stream().map(UnsignedLong::toString).collect(Collectors.joining(","));
    }

    private static Function<String, String> getTimeMatcher(Instant start, Instant end) {
        return (String columnName) -> {
            if (start == null || end == null) {
                return "";
            }
            return " ( "
                    + columnName
                    + " >= "
                    + start.toEpochMilli()
                    + " AND "
                    + columnName
                    + " <= "
                    + end.toEpochMilli()
                    + " ) ";
        };
    }

    private static Function<String, String> getSiteMatcher(
            List<Uri> origins,
            List<Uri> domains,
            @DeletionRequest.MatchBehavior int matchBehavior) {

        return (String columnName) -> {
            if (origins.isEmpty() && domains.isEmpty()) {
                if (matchBehavior == DeletionRequest.MATCH_BEHAVIOR_PRESERVE) {
                    // MATCH EVERYTHING
                    return "";
                } else {
                    // MATCH NOTHING
                    return columnName + " IN ()";
                }
            }
            StringBuilder whereBuilder = new StringBuilder();
            boolean started = false;
            if (!origins.isEmpty()) {
                started = true;
                whereBuilder.append("(");
                whereBuilder.append(columnName);
                // For Delete case:
                // (columnName IN ( origin1, origin2 )
                // For Preserve case:
                // (columnName NOT IN ( origin1, origin2 )
                if (matchBehavior == DeletionRequest.MATCH_BEHAVIOR_PRESERVE) {
                    whereBuilder.append(" NOT IN (");
                } else {
                    whereBuilder.append(" IN (");
                }
                whereBuilder.append(
                        origins.stream()
                                .map((o) -> DatabaseUtils.sqlEscapeString(o.toString()))
                                .collect(Collectors.joining(", ")));
                whereBuilder.append(")");
            }

            if (!domains.isEmpty()) {
                if (started) {
                    whereBuilder.append(
                            matchBehavior == DeletionRequest.MATCH_BEHAVIOR_PRESERVE
                                    ? " AND "
                                    : " OR ");
                } else {
                    whereBuilder.append(" ( ");
                    started = true;
                }
                whereBuilder.append(" ( ");
                String operator =
                        matchBehavior == DeletionRequest.MATCH_BEHAVIOR_PRESERVE
                                ? " NOT LIKE "
                                : " LIKE ";
                String concatOperator =
                        matchBehavior == DeletionRequest.MATCH_BEHAVIOR_PRESERVE ? " AND " : " OR ";
                String equalityOperator =
                        matchBehavior == DeletionRequest.MATCH_BEHAVIOR_PRESERVE ? " != " : " = ";
                // Domains have 2 cases: subdomain(*.example.com) and the parent domain(example.com)
                // For Delete case:
                // (columnName LIKE "SCHEME1://%.SITE1" OR columnName = "SCHEME1://SITE1") OR
                // (columnName LIKE "SCHEME2://%.SITE2" OR columnName = "SCHEME2://SITE2")
                // For Preserve case:
                // (columnName NOT LIKE 'SCHEME1://%.SITE1' AND columnName != 'SCHEME1://SITE1')
                // AND
                // (columnName NOT LIKE 'SCHEME2://%.SITE2' AND columnName != 'SCHEME2://SITE2')
                whereBuilder.append(
                        domains.stream()
                                .map(
                                        (uri) ->
                                                ("("
                                                        + columnName
                                                        + operator
                                                        + DatabaseUtils.sqlEscapeString(
                                                                uri.getScheme()
                                                                        + "://%."
                                                                        + uri.getAuthority())
                                                        + concatOperator
                                                        + columnName
                                                        + equalityOperator
                                                        + DatabaseUtils.sqlEscapeString(
                                                                uri.toString())
                                                        + ")"))
                                .collect(Collectors.joining(concatOperator)));
                whereBuilder.append(" ) ");
            }
            if (started) {
                whereBuilder.append(" ) ");
            }
            return whereBuilder.toString();
        };
    }

    private String mergeConditions(String operator, String... matcherStrings) {
        String res =
                Arrays.stream(matcherStrings)
                        .filter(Predicate.not(String::isEmpty))
                        .collect(Collectors.joining(operator));
        if (!res.isEmpty()) {
            res = "(" + res + ")";
        }
        return res;
    }

    @Override
    public void deleteAllMeasurementData(@NonNull List<String> tablesToExclude)
            throws DatastoreException {
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        for (String table : MeasurementTables.ALL_MSMT_TABLES) {
            if (!tablesToExclude.contains(table)) {
                db.delete(table, /* whereClause */ null, /* whereArgs */ null);
            }
        }
    }

    private void validateRange(Instant start, Instant end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start or end date is null");
        }

        if (start.isAfter(end)) {
            throw new IllegalArgumentException(
                    "invalid range, start date must be equal or before end date");
        }
    }

    @Override
    public void doInstallAttribution(Uri uri, long eventTimestamp) throws DatastoreException {
        SQLiteDatabase db = mSQLTransaction.getDatabase();

        String sourceIdSubQuery =
                selectSourceIdsByDestination(uri.toString(), EventSurfaceType.APP);

        String whereString =
                String.format(
                        Locale.ENGLISH,
                        mergeConditions(
                                " AND ",
                                MeasurementTables.SourceContract.ID + " IN source_ids",
                                MeasurementTables.SourceContract.EVENT_TIME + " <= %1$d",
                                MeasurementTables.SourceContract.EXPIRY_TIME + " > %1$d",
                                MeasurementTables.SourceContract.EVENT_TIME
                                        + " + "
                                        + MeasurementTables.SourceContract
                                                .INSTALL_ATTRIBUTION_WINDOW
                                        + " >= %1$d",
                                MeasurementTables.SourceContract.STATUS + " = %2$d"),
                        eventTimestamp,
                        Source.Status.ACTIVE);

        // Will generate the records that we are interested in
        String filterQuery =
                String.format(
                        Locale.ENGLISH,
                        " ( WITH source_ids AS (%1$s) SELECT * from %2$s WHERE %3$s )",
                        sourceIdSubQuery,
                        MeasurementTables.SourceContract.TABLE,
                        whereString);

        // The inner query picks the top record based on priority and recency order after applying
        // the filter. But first_value generates one value per partition but applies to all the rows
        // that input has, so we have to nest it with distinct in order to get unique source_ids.
        String sourceIdsProjection =
                String.format(
                        Locale.ENGLISH,
                        "SELECT DISTINCT(first_source_id) from "
                                + "(SELECT first_value(%1$s) "
                                + "OVER (PARTITION BY %2$s ORDER BY %3$s DESC, %4$s DESC) "
                                + "first_source_id FROM %5$s)",
                        MeasurementTables.SourceContract.ID,
                        MeasurementTables.SourceContract.REGISTRATION_ORIGIN,
                        MeasurementTables.SourceContract.PRIORITY,
                        MeasurementTables.SourceContract.EVENT_TIME,
                        filterQuery);

        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED, true);
        values.put(MeasurementTables.SourceContract.INSTALL_TIME, eventTimestamp);
        db.update(
                MeasurementTables.SourceContract.TABLE,
                values,
                MeasurementTables.SourceContract.ID + " IN (" + sourceIdsProjection + ")",
                null);
    }

    @Override
    public void undoInstallAttribution(Uri uri) throws DatastoreException {
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED, false);
        values.putNull(MeasurementTables.SourceContract.INSTALL_TIME);
        db.update(
                MeasurementTables.SourceContract.TABLE,
                values,
                MeasurementTables.SourceContract.ID
                        + " IN ("
                        + selectSourceIdsByDestination(uri.toString(), EventSurfaceType.APP)
                        + ")",
                new String[] {});
    }

    @Override
    public void insertAggregateEncryptionKey(AggregateEncryptionKey aggregateEncryptionKey)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AggregateEncryptionKey.ID, UUID.randomUUID().toString());
        values.put(
                MeasurementTables.AggregateEncryptionKey.KEY_ID, aggregateEncryptionKey.getKeyId());
        values.put(
                MeasurementTables.AggregateEncryptionKey.PUBLIC_KEY,
                aggregateEncryptionKey.getPublicKey());
        values.put(
                MeasurementTables.AggregateEncryptionKey.EXPIRY,
                aggregateEncryptionKey.getExpiry());
        values.put(
                MeasurementTables.AggregateEncryptionKey.AGGREGATION_COORDINATOR_ORIGIN,
                aggregateEncryptionKey.getAggregationCoordinatorOrigin().toString());
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.AggregateEncryptionKey.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        if (rowId == -1) {
            throw new DatastoreException("Aggregate encryption key insertion failed.");
        }
    }

    @Override
    public List<AggregateEncryptionKey> getNonExpiredAggregateEncryptionKeys(
            Uri coordinatorOrigin, long expiry) throws DatastoreException {
        List<AggregateEncryptionKey> aggregateEncryptionKeys = new ArrayList<>();
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.AggregateEncryptionKey.TABLE,
                                /* columns= */ null,
                                MeasurementTables.AggregateEncryptionKey.EXPIRY
                                        + " >= ? "
                                        + " AND "
                                        + MeasurementTables.AggregateEncryptionKey
                                                .AGGREGATION_COORDINATOR_ORIGIN
                                        + " = ?",
                                new String[] {String.valueOf(expiry), coordinatorOrigin.toString()},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ null,
                                /* limit= */ null)) {
            while (cursor.moveToNext()) {
                aggregateEncryptionKeys.add(
                        SqliteObjectMapper.constructAggregateEncryptionKeyFromCursor(cursor));
            }
            return aggregateEncryptionKeys;
        }
    }

    @Override
    public void deleteExpiredAggregateEncryptionKeys(long expiry) throws DatastoreException {
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        db.delete(
                MeasurementTables.AggregateEncryptionKey.TABLE,
                MeasurementTables.AggregateEncryptionKey.EXPIRY + " < ?",
                new String[] {String.valueOf(expiry)});
    }

    @Override
    public void insertAggregateReport(AggregateReport aggregateReport) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AggregateReport.ID, UUID.randomUUID().toString());
        values.put(
                MeasurementTables.AggregateReport.PUBLISHER,
                aggregateReport.getPublisher().toString());
        values.put(
                MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                aggregateReport.getAttributionDestination().toString());
        values.put(
                MeasurementTables.AggregateReport.SOURCE_REGISTRATION_TIME,
                aggregateReport.getSourceRegistrationTime());
        values.put(
                MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME,
                aggregateReport.getScheduledReportTime());
        values.put(
                MeasurementTables.AggregateReport.ENROLLMENT_ID, aggregateReport.getEnrollmentId());
        values.put(
                MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD,
                aggregateReport.getDebugCleartextPayload());
        values.put(MeasurementTables.AggregateReport.STATUS, aggregateReport.getStatus());
        values.put(
                MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS,
                aggregateReport.getDebugReportStatus());
        values.put(MeasurementTables.AggregateReport.API_VERSION, aggregateReport.getApiVersion());
        values.put(
                MeasurementTables.AggregateReport.SOURCE_DEBUG_KEY,
                getNullableUnsignedLong(aggregateReport.getSourceDebugKey()));
        values.put(
                MeasurementTables.AggregateReport.TRIGGER_DEBUG_KEY,
                getNullableUnsignedLong(aggregateReport.getTriggerDebugKey()));
        values.put(MeasurementTables.AggregateReport.SOURCE_ID, aggregateReport.getSourceId());
        values.put(MeasurementTables.AggregateReport.TRIGGER_ID, aggregateReport.getTriggerId());
        values.put(
                MeasurementTables.AggregateReport.DEDUP_KEY,
                aggregateReport.getDedupKey() != null
                        ? aggregateReport.getDedupKey().getValue()
                        : null);
        values.put(
                MeasurementTables.AggregateReport.REGISTRATION_ORIGIN,
                aggregateReport.getRegistrationOrigin().toString());
        values.put(
                MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN,
                aggregateReport.getAggregationCoordinatorOrigin().toString());
        values.put(
                MeasurementTables.AggregateReport.IS_FAKE_REPORT, aggregateReport.isFakeReport());
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.AggregateReport.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        if (rowId == -1) {
            throw new DatastoreException("Unencrypted aggregate payload insertion failed.");
        }
    }

    @Override
    public void insertDebugReport(DebugReport debugReport) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.DebugReportContract.ID, debugReport.getId());
        values.put(MeasurementTables.DebugReportContract.TYPE, debugReport.getType());
        values.put(MeasurementTables.DebugReportContract.BODY, debugReport.getBody().toString());
        values.put(
                MeasurementTables.DebugReportContract.ENROLLMENT_ID, debugReport.getEnrollmentId());
        values.put(
                MeasurementTables.DebugReportContract.REGISTRATION_ORIGIN,
                debugReport.getRegistrationOrigin().toString());
        values.put(
                MeasurementTables.DebugReportContract.REFERENCE_ID, debugReport.getReferenceId());
        values.put(
                MeasurementTables.DebugReportContract.INSERTION_TIME,
                debugReport.getInsertionTime());
        values.put(
                MeasurementTables.DebugReportContract.REGISTRANT,
                getNullableUriString(debugReport.getRegistrant()));
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.DebugReportContract.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        LoggerFactory.getMeasurementLogger().d("MeasurementDao: insertDebugReport: rowId=" + rowId);

        if (rowId == -1) {
            throw new DatastoreException("Debug report payload insertion failed.");
        }
    }

    @Override
    public Map<String, List<String>> getPendingAggregateReportIdsByCoordinatorInWindow(
            long windowStartTime, long windowEndTime) throws DatastoreException {
        Map<String, List<String>> result = new HashMap<>();
        try (Cursor cursor =
                mReportingRetryLimitEnabledSupplier.get()
                        ? pendingAggregateReportIdsByCoordinatorInWindowLimitRetryCursor(
                                windowStartTime, windowEndTime)
                        : pendingAggregateReportIdsByCoordinatorInWindowCursor(
                                windowStartTime, windowEndTime)) {
            while (cursor.moveToNext()) {
                String coordinator =
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AggregateReport
                                                .AGGREGATION_COORDINATOR_ORIGIN));
                result.putIfAbsent(coordinator, new ArrayList<>());
                result.get(coordinator)
                        .add(
                                cursor.getString(
                                        cursor.getColumnIndex(
                                                MeasurementTables.AggregateReport.ID)));
            }
            return result;
        }
    }

    @Override
    public List<String> getDebugReportIds() throws DatastoreException {
        List<String> debugReportIds = new ArrayList<>();
        try (Cursor cursor =
                mReportingRetryLimitEnabledSupplier.get()
                        ? debugReportIdsLimitRetryCursor()
                        : debugReportIdsCursor()) {
            while (cursor.moveToNext()) {
                debugReportIds.add(
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.DebugReportContract.ID)));
            }
            return debugReportIds;
        }
    }

    @Override
    public Map<String, List<String>> getPendingAggregateDebugReportIdsByCoordinator()
            throws DatastoreException {
        Map<String, List<String>> result = new HashMap<>();
        try (Cursor cursor =
                mReportingRetryLimitEnabledSupplier.get()
                        ? pendingAggregateDebugReportIdsByCoordinatorLimitRetryCursor()
                        : pendingAggregateDebugReportIdsByCoordinatorCursor()) {

            while (cursor.moveToNext()) {
                String coordinator =
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AggregateReport
                                                .AGGREGATION_COORDINATOR_ORIGIN));
                result.putIfAbsent(coordinator, new ArrayList<>());
                result.get(coordinator)
                        .add(
                                cursor.getString(
                                        cursor.getColumnIndex(
                                                MeasurementTables.AggregateReport.ID)));
            }
            return result;
        }
    }

    @Override
    public List<String> getPendingAggregateReportIdsForGivenApp(Uri appName)
            throws DatastoreException {
        List<String> aggregateReports = new ArrayList<>();
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.AggregateReport.TABLE,
                                null,
                                MeasurementTables.AggregateReport.PUBLISHER
                                        + " = ? AND "
                                        + MeasurementTables.AggregateReport.STATUS
                                        + " = ? ",
                                new String[] {
                                    appName.toString(),
                                    String.valueOf(AggregateReport.Status.PENDING)
                                },
                                null,
                                null,
                                "RANDOM()",
                                null)) {
            while (cursor.moveToNext()) {
                aggregateReports.add(
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.AggregateReport.ID)));
            }
            return aggregateReports;
        }
    }

    @Override
    public void deleteSources(@NonNull Collection<String> sourceIds) throws DatastoreException {
        deleteRecordsColumnBased(
                sourceIds,
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.ID);
    }

    @Override
    public void deleteTriggers(@NonNull Collection<String> triggerIds) throws DatastoreException {
        deleteRecordsColumnBased(
                triggerIds,
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.ID);
    }

    @Override
    public void deleteAsyncRegistrations(@NonNull List<String> asyncRegistrationIds)
            throws DatastoreException {
        deleteRecordsColumnBased(
                asyncRegistrationIds,
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTables.AsyncRegistrationContract.ID);
    }

    private void deleteRecordsColumnBased(
            Collection<String> columnValues, String tableName, String columnName)
            throws DatastoreException {
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .delete(
                                tableName,
                                columnName
                                        + " IN ("
                                        + Stream.generate(() -> "?")
                                                .limit(columnValues.size())
                                                .collect(Collectors.joining(","))
                                        + ")",
                                columnValues.toArray(new String[0]));
        if (rows < 0) {
            throw new DatastoreException(
                    String.format("Deletion failed from %1s on %2s.", tableName, columnName));
        }
    }

    @Override
    public List<Source> fetchTriggerMatchingSourcesForXna(
            @NonNull Trigger trigger, @NonNull Collection<String> xnaEnrollmentIds)
            throws DatastoreException {
        List<Source> sources = new ArrayList<>();
        Optional<String> destinationValue = getDestinationValue(trigger);
        if (!destinationValue.isPresent()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "getTriggerMatchingSourcesForXna: unable to obtain destination value:"
                                    + " %s",
                            trigger.getAttributionDestination().toString());
            return sources;
        }
        String triggerDestinationValue = destinationValue.get();
        String delimitedXnaEnrollmentIds =
                xnaEnrollmentIds.stream()
                        .map(DatabaseUtils::sqlEscapeString)
                        .collect(Collectors.joining(","));
        String triggerEnrollmentId = trigger.getEnrollmentId();
        String eligibleXnaEnrollmentRegisteredSourcesWhereClause =
                mergeConditions(
                        " AND ",
                        MeasurementTables.SourceContract.ENROLLMENT_ID
                                + " IN ("
                                + delimitedXnaEnrollmentIds
                                + ")",
                        MeasurementTables.SourceContract.ID
                                + " NOT IN "
                                // Avoid the sources which have lost XNA attribution before
                                + "("
                                + "select "
                                + MeasurementTables.XnaIgnoredSourcesContract.SOURCE_ID
                                + " from "
                                + MeasurementTables.XnaIgnoredSourcesContract.TABLE
                                + " where "
                                + MeasurementTables.XnaIgnoredSourcesContract.ENROLLMENT_ID
                                + " = "
                                + DatabaseUtils.sqlEscapeString(triggerEnrollmentId)
                                + ")",
                        MeasurementTables.SourceContract.REGISTRATION_ID
                                + " NOT IN "
                                // Avoid the sources (XNA parent) whose registration chain had a
                                // source registered by trigger's AdTech (by matching enrollmentId)
                                + "("
                                + "select "
                                + MeasurementTables.SourceContract.REGISTRATION_ID
                                + " from "
                                + MeasurementTables.SourceContract.TABLE
                                + " where "
                                + MeasurementTables.SourceContract.ENROLLMENT_ID
                                + " = "
                                + DatabaseUtils.sqlEscapeString(triggerEnrollmentId)
                                + ")",
                        MeasurementTables.SourceContract.SHARED_AGGREGATION_KEYS + " IS NOT NULL");

        String eligibleTriggerNetworkRegisteredSourcesWhereClause =
                String.format(
                        MeasurementTables.SourceContract.ENROLLMENT_ID + " = %s",
                        DatabaseUtils.sqlEscapeString(triggerEnrollmentId));
        // The following filtering logic is applied -
        //  - AND
        //     - destination == trigger's destination
        //     - expiryTime > triggerTime
        //     - eventTime < triggerTime
        //     - OR
        //      - AND
        //       - sourceEnrollmentId == trigger's enrollmentId
        //      - AND
        //       - sourceEnrollmentId IN XNA enrollment IDs
        //       - sourceId NOT IN (sources associated to XNA that have lost
        //         attribution in the past -- lose once lose always)
        //       - triggerEnrollmentId NOT IN (enrollmentIds of the sources registered under this
        // registration ID)
        //       - sharedAggregationKeys NOT NULL
        String sourceWhereStatement =
                mergeConditions(
                        " AND ",
                        MeasurementTables.SourceContract.EXPIRY_TIME + " > ?",
                        mergeConditions(
                                " OR ",
                                eligibleTriggerNetworkRegisteredSourcesWhereClause,
                                eligibleXnaEnrollmentRegisteredSourcesWhereClause),
                        MeasurementTables.SourceContract.EVENT_TIME + " <= ? ");

        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .rawQuery(
                                selectSourcesByDestination(
                                        triggerDestinationValue,
                                        trigger.getDestinationType(),
                                        sourceWhereStatement),
                                new String[] {
                                    String.valueOf(trigger.getTriggerTime()),
                                    String.valueOf(trigger.getTriggerTime())
                                })) {
            while (cursor.moveToNext()) {
                sources.add(SqliteObjectMapper.constructSourceFromCursor(cursor));
            }
            return sources;
        }
    }

    @Override
    public void insertIgnoredSourceForEnrollment(
            @NonNull String sourceId, @NonNull String enrollmentId) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.XnaIgnoredSourcesContract.SOURCE_ID, sourceId);
        values.put(MeasurementTables.XnaIgnoredSourcesContract.ENROLLMENT_ID, enrollmentId);
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.XnaIgnoredSourcesContract.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        if (rowId == -1) {
            throw new DatastoreException("Xna ignored source insertion failed.");
        }
    }

    private static Optional<String> getDestinationValue(Trigger trigger) {
        if (trigger.getDestinationType() == EventSurfaceType.APP) {
            return Optional.of(trigger.getAttributionDestination().toString());
        } else {
            Optional<Uri> topPrivateDomainAndScheme =
                    WebAddresses.topPrivateDomainAndScheme(trigger.getAttributionDestination());
            return topPrivateDomainAndScheme.map(Uri::toString);
        }
    }

    private static Optional<Uri> extractBaseUri(Uri uri, @EventSurfaceType int eventSurfaceType) {
        return eventSurfaceType == EventSurfaceType.APP
                ? Optional.of(BaseUriExtractor.getBaseUri(uri))
                : WebAddresses.topPrivateDomainAndScheme(uri);
    }

    private static String getPublisherWhereStatement(
            Uri publisher, @EventSurfaceType int publisherType) {
        if (publisherType == EventSurfaceType.APP) {
            return String.format(
                    Locale.ENGLISH,
                    "%s = %s",
                    MeasurementTables.SourceContract.PUBLISHER,
                    DatabaseUtils.sqlEscapeString(publisher.toString()));
        } else {
            return String.format(
                    Locale.ENGLISH,
                    "(%1$s = %2$s OR %1$s LIKE %3$s)",
                    MeasurementTables.SourceContract.PUBLISHER,
                    DatabaseUtils.sqlEscapeString(publisher.toString()),
                    DatabaseUtils.sqlEscapeString(
                            publisher.getScheme() + "://%." + publisher.getEncodedAuthority()));
        }
    }

    /** Returns a SQL where statement for matching an app/web destination. */
    private static String getDestinationWhereStatement(
            Uri destination, @EventSurfaceType int destinationType) {
        Optional<Uri> destinationBaseUriOptional = extractBaseUri(destination, destinationType);
        if (!destinationBaseUriOptional.isPresent()) {
            throw new IllegalArgumentException(
                    String.format(
                            Locale.ENGLISH,
                            "getDestinationWhereStatement:" + " Unable to extract base uri from %s",
                            destination.toString()));
        }
        Uri destinationBaseUri = destinationBaseUriOptional.get();

        if (destinationType == EventSurfaceType.APP) {
            return String.format(
                    Locale.ENGLISH,
                    "(%1$s = %2$s OR %1$s LIKE %3$s)",
                    MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                    DatabaseUtils.sqlEscapeString(destinationBaseUri.toString()),
                    DatabaseUtils.sqlEscapeString(destinationBaseUri + "/%"));
        } else {
            String schemeSubDomainMatcher =
                    destination.getScheme() + "://%." + destinationBaseUri.getAuthority();
            String schemeDomainMatcher =
                    destination.getScheme() + "://" + destinationBaseUri.getAuthority();
            String domainAndPathMatcher = schemeDomainMatcher + "/%";
            String subDomainAndPathMatcher = schemeSubDomainMatcher + "/%";
            return String.format(
                    Locale.ENGLISH,
                    "(%1$s = %2$s OR %1$s LIKE %3$s OR %1$s LIKE %4$s OR %1$s LIKE %5$s)",
                    MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                    DatabaseUtils.sqlEscapeString(schemeDomainMatcher),
                    DatabaseUtils.sqlEscapeString(schemeSubDomainMatcher),
                    DatabaseUtils.sqlEscapeString(domainAndPathMatcher),
                    DatabaseUtils.sqlEscapeString(subDomainAndPathMatcher));
        }
    }

    private static String getNullableUriString(@Nullable Uri uri) {
        return Optional.ofNullable(uri).map(Uri::toString).orElse(null);
    }

    private static Long getNullableUnsignedLong(@Nullable UnsignedLong ulong) {
        return Optional.ofNullable(ulong).map(UnsignedLong::getValue).orElse(null);
    }

    /**
     * Returns the min or max possible long value to avoid the ArithmeticException thrown when
     * calling toEpochMilli() on Instant.MAX or Instant.MIN
     */
    private static Instant capDeletionRange(Instant instant) {
        Instant[] instants = {
            Instant.ofEpochMilli(Long.MIN_VALUE), instant, Instant.ofEpochMilli(Long.MAX_VALUE)
        };
        Arrays.sort(instants);
        return instants[1];
    }

    public void insertAsyncRegistration(@NonNull AsyncRegistration asyncRegistration)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AsyncRegistrationContract.ID, asyncRegistration.getId());
        values.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI,
                asyncRegistration.getRegistrationUri().toString());
        values.put(
                MeasurementTables.AsyncRegistrationContract.WEB_DESTINATION,
                getNullableUriString(asyncRegistration.getWebDestination()));
        values.put(
                MeasurementTables.AsyncRegistrationContract.VERIFIED_DESTINATION,
                getNullableUriString(asyncRegistration.getVerifiedDestination()));
        values.put(
                MeasurementTables.AsyncRegistrationContract.OS_DESTINATION,
                getNullableUriString(asyncRegistration.getOsDestination()));
        values.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRANT,
                getNullableUriString(asyncRegistration.getRegistrant()));
        values.put(
                MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN,
                asyncRegistration.getTopOrigin().toString());
        values.put(
                MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE,
                asyncRegistration.getSourceType() == null
                        ? null
                        : asyncRegistration.getSourceType().ordinal());
        values.put(
                MeasurementTables.AsyncRegistrationContract.REQUEST_TIME,
                asyncRegistration.getRequestTime());
        values.put(
                MeasurementTables.AsyncRegistrationContract.RETRY_COUNT,
                asyncRegistration.getRetryCount());
        values.put(
                MeasurementTables.AsyncRegistrationContract.TYPE,
                asyncRegistration.getType().ordinal());
        values.put(
                MeasurementTables.AsyncRegistrationContract.DEBUG_KEY_ALLOWED,
                asyncRegistration.getDebugKeyAllowed());
        values.put(
                MeasurementTables.AsyncRegistrationContract.AD_ID_PERMISSION,
                asyncRegistration.hasAdIdPermission());
        values.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID,
                asyncRegistration.getRegistrationId());
        values.put(
                MeasurementTables.AsyncRegistrationContract.PLATFORM_AD_ID,
                asyncRegistration.getPlatformAdId());
        values.put(
                MeasurementTables.AsyncRegistrationContract.REQUEST_POST_BODY,
                asyncRegistration.getPostBody());
        values.put(
                MeasurementTables.AsyncRegistrationContract.REDIRECT_BEHAVIOR,
                asyncRegistration.getRedirectBehavior().name());
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        LoggerFactory.getMeasurementLogger()
                .d("MeasurementDao: insertAsyncRegistration: rowId=" + rowId);
        if (rowId == -1) {
            throw new DatastoreException("Async Registration insertion failed.");
        }
    }

    @Override
    public void deleteAsyncRegistration(@NonNull String id) throws DatastoreException {
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        int rows =
                db.delete(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        MeasurementTables.AsyncRegistrationContract.ID + " = ?",
                        new String[] {id});
        if (rows == 0) {
            // This is needed because we want to fail the transaction in case the registration was
            // deleted by some other job while it was being processed.
            throw new DatastoreException("Async Registration already deleted");
        }
        LoggerFactory.getMeasurementLogger()
                .d("MeasurementDao: deleteAsyncRegistration: rows affected=" + rows);
    }

    @Override
    public AsyncRegistration fetchNextQueuedAsyncRegistration(
            int retryLimit, Set<Uri> failedOrigins) throws DatastoreException {
        String originExclusion = "";

        if (!failedOrigins.isEmpty()) {
            List<String> notLikes = new ArrayList<>();
            failedOrigins.forEach(
                    (origin) -> {
                        notLikes.add(
                                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI
                                        + " NOT LIKE "
                                        + DatabaseUtils.sqlEscapeString(origin + "%"));
                    });
            originExclusion = mergeConditions(" AND ", notLikes.toArray(new String[0]));
        }
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                /* columns= */ null,
                                mergeConditions(
                                        " AND ",
                                        MeasurementTables.AsyncRegistrationContract.RETRY_COUNT
                                                + " < ? ",
                                        originExclusion),
                                new String[] {String.valueOf(retryLimit)},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ MeasurementTables.AsyncRegistrationContract
                                        .REQUEST_TIME,
                                /* limit= */ "1")) {
            if (cursor.getCount() == 0) {
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructAsyncRegistration(cursor);
        }
    }

    @Override
    public KeyValueData getKeyValueData(@NonNull String key, @NonNull DataType dataType)
            throws DatastoreException {
        String value = null;
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.KeyValueDataContract.TABLE,
                                new String[] {MeasurementTables.KeyValueDataContract.VALUE},
                                MeasurementTables.KeyValueDataContract.DATA_TYPE
                                        + " = ? "
                                        + " AND "
                                        + MeasurementTables.KeyValueDataContract.KEY
                                        + " = ?",
                                new String[] {dataType.toString(), key},
                                null,
                                null,
                                null,
                                null)) {
            if (cursor.moveToNext()) {
                value = cursor.getString(0);
            }
        }
        return new KeyValueData.Builder().setDataType(dataType).setKey(key).setValue(value).build();
    }

    @Override
    public void insertOrUpdateKeyValueData(@NonNull KeyValueData keyValueData)
            throws DatastoreException {
        ContentValues contentValues = new ContentValues();
        contentValues.put(
                MeasurementTables.KeyValueDataContract.DATA_TYPE,
                keyValueData.getDataType().toString());
        contentValues.put(MeasurementTables.KeyValueDataContract.KEY, keyValueData.getKey());
        contentValues.put(MeasurementTables.KeyValueDataContract.VALUE, keyValueData.getValue());
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insertWithOnConflict(
                                MeasurementTables.KeyValueDataContract.TABLE,
                                null,
                                contentValues,
                                CONFLICT_REPLACE);
        if (rowId == -1) {
            throw new DatastoreException("KeyValueData insertion failed: " + contentValues);
        }
    }

    @Override
    public void updateRetryCount(AsyncRegistration asyncRegistration) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.AsyncRegistrationContract.RETRY_COUNT,
                asyncRegistration.getRetryCount());
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                values,
                                MeasurementTables.AsyncRegistrationContract.ID + " = ?",
                                new String[] {asyncRegistration.getId()});
        if (rows != 1) {
            throw new DatastoreException("Retry Count update failed.");
        }
    }

    @Override
    public int incrementAndGetReportingRetryCount(String id, DataType reportType)
            throws DatastoreException {
        KeyValueData eventRetry = getKeyValueData(id, reportType);
        eventRetry.setReportRetryCount(eventRetry.getReportRetryCount() + 1);
        insertOrUpdateKeyValueData(eventRetry);
        int retryCount = eventRetry.getReportRetryCount();
        LoggerFactory.getMeasurementLogger()
                .d("Incrementing: " + reportType + " Retry Count: " + retryCount);
        return retryCount;
    }

    @Override
    public long countDistinctDebugAdIdsUsedByEnrollment(@NonNull String enrollmentId)
            throws DatastoreException {
        return DatabaseUtils.longForQuery(
                mSQLTransaction.getDatabase(),
                countDistinctDebugAdIdsUsedByEnrollmentQuery(),
                new String[] {
                    enrollmentId,
                    String.valueOf(EventSurfaceType.WEB),
                    enrollmentId,
                    String.valueOf(EventSurfaceType.WEB)
                });
    }

    private int getNumReportsPerDestination(
            String tableName,
            String columnName,
            Uri attributionDestination,
            @EventSurfaceType int destinationType)
            throws DatastoreException {
        Optional<Uri> destinationBaseUri = extractBaseUri(attributionDestination, destinationType);
        if (!destinationBaseUri.isPresent()) {
            throw new IllegalStateException("extractBaseUri failed for destination.");
        }

        // Example: https://destination.com
        String noSubdomainOrPostfixMatch =
                DatabaseUtils.sqlEscapeString(
                        destinationBaseUri.get().getScheme()
                                + "://"
                                + destinationBaseUri.get().getHost());

        // Example: https://subdomain.destination.com/path
        String subdomainAndPostfixMatch =
                DatabaseUtils.sqlEscapeString(
                        destinationBaseUri.get().getScheme()
                                + "://%."
                                + destinationBaseUri.get().getHost()
                                + "/%");

        // Example: https://subdomain.destination.com
        String subdomainMatch =
                DatabaseUtils.sqlEscapeString(
                        destinationBaseUri.get().getScheme()
                                + "://%."
                                + destinationBaseUri.get().getHost());

        // Example: https://destination.com/path
        String postfixMatch =
                DatabaseUtils.sqlEscapeString(
                        destinationBaseUri.get().getScheme()
                                + "://"
                                + destinationBaseUri.get().getHost()
                                + "/%");
        String query;
        if (destinationType == EventSurfaceType.WEB) {
            query =
                    String.format(
                            Locale.ENGLISH,
                            "SELECT COUNT(*) FROM %2$s WHERE (%1$s = %3$s"
                                    + " OR %1$s LIKE %4$s"
                                    + " OR %1$s LIKE %5$s"
                                    + " OR %1$s LIKE %6$s)",
                            columnName,
                            tableName,
                            noSubdomainOrPostfixMatch,
                            subdomainAndPostfixMatch,
                            subdomainMatch,
                            postfixMatch);
        } else {
            query =
                    String.format(
                            Locale.ENGLISH,
                            "SELECT COUNT(*) FROM %2$s WHERE"
                                    + " (%1$s = %3$s"
                                    + " OR %1$s LIKE %4$s)",
                            columnName,
                            tableName,
                            noSubdomainOrPostfixMatch,
                            postfixMatch);
        }

        if (FlagsFactory.getFlags().getMeasurementNullAggregateReportEnabled()
                && tableName.equals(MeasurementTables.AggregateReport.TABLE)) {
            query +=
                    String.format(
                            Locale.ENGLISH,
                            " AND %1$s = 0",
                            MeasurementTables.AggregateReport.IS_FAKE_REPORT);
        }

        return (int) DatabaseUtils.longForQuery(mSQLTransaction.getDatabase(), query, null);
    }

    private <T> List<T> fetchRecordsMatchingWithParameters(
            String tableName,
            String sourceColumnName,
            Collection<String> sourceIds,
            String triggerColumnName,
            Collection<String> triggerIds,
            Function<Cursor, T> sqlMapperFunction)
            throws DatastoreException {
        List<T> reports = new ArrayList<>();
        String delimitedSourceIds =
                sourceIds.stream()
                        .map(DatabaseUtils::sqlEscapeString)
                        .collect(Collectors.joining(","));
        String delimitedTriggerIds =
                triggerIds.stream()
                        .map(DatabaseUtils::sqlEscapeString)
                        .collect(Collectors.joining(","));

        String whereString =
                mergeConditions(
                        /* operator= */ " OR ",
                        sourceColumnName + " IN (" + delimitedSourceIds + ")",
                        triggerColumnName + " IN (" + delimitedTriggerIds + ")");
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .rawQuery(
                                String.format(
                                        Locale.ENGLISH,
                                        "SELECT * FROM %1$s WHERE " + whereString,
                                        tableName),
                                null)) {
            while (cursor.moveToNext()) {
                reports.add(sqlMapperFunction.apply(cursor));
            }
        }
        return reports;
    }

    /**
     * Given a destination and destination type, return a SELECT statement for matching source IDs.
     */
    private static String selectSourceIdsByDestination(
            String destination, @EventSurfaceType int destinationType) {
        return String.format(
                Locale.ENGLISH,
                "SELECT "
                        + MeasurementTables.SourceDestination.SOURCE_ID
                        + " "
                        + "FROM "
                        + MeasurementTables.SourceDestination.TABLE
                        + " "
                        + "WHERE "
                        + MeasurementTables.SourceDestination.DESTINATION
                        + " = "
                        + DatabaseUtils.sqlEscapeString(destination)
                        + " "
                        + "AND "
                        + MeasurementTables.SourceDestination.DESTINATION_TYPE
                        + " = "
                        + destinationType);
    }

    /**
     * Given a trigger destination and type, and constraints on the source, return all matching
     * sources.
     */
    private static String selectSourcesByDestination(
            String triggerDestinationValue,
            @EventSurfaceType int destinationType,
            String sourceWhereStatement) {
        return String.format(
                Locale.ENGLISH,
                "SELECT * FROM "
                        + MeasurementTables.SourceContract.TABLE
                        + " WHERE "
                        + MeasurementTables.SourceContract.ID
                        + " IN ("
                        + "SELECT "
                        + MeasurementTables.SourceDestination.SOURCE_ID
                        + " FROM "
                        + MeasurementTables.SourceDestination.TABLE
                        + " WHERE "
                        + MeasurementTables.SourceDestination.DESTINATION
                        + " = '"
                        + triggerDestinationValue
                        + "' "
                        + "AND "
                        + MeasurementTables.SourceDestination.DESTINATION_TYPE
                        + " = "
                        + destinationType
                        + ") "
                        + "AND ("
                        + sourceWhereStatement
                        + ")");
    }

    /**
     * Given an enrollment id, return the number unique debug ad id values present in sources and
     * triggers with this enrollment id.
     */
    private static String countDistinctDebugAdIdsUsedByEnrollmentQuery() {
        return String.format(
                Locale.ENGLISH,
                "SELECT COUNT (DISTINCT "
                        + MeasurementTables.SourceContract.DEBUG_AD_ID
                        + ") "
                        + "FROM ( "
                        + "SELECT "
                        + MeasurementTables.SourceContract.DEBUG_AD_ID
                        + " FROM "
                        + MeasurementTables.SourceContract.TABLE
                        + " WHERE "
                        + MeasurementTables.SourceContract.DEBUG_AD_ID
                        + " IS NOT NULL "
                        + "AND "
                        + MeasurementTables.SourceContract.ENROLLMENT_ID
                        + " = ? "
                        + "AND "
                        + MeasurementTables.SourceContract.PUBLISHER_TYPE
                        + " = ? "
                        + "UNION ALL "
                        + "SELECT "
                        + MeasurementTables.TriggerContract.DEBUG_AD_ID
                        + " FROM "
                        + MeasurementTables.TriggerContract.TABLE
                        + " WHERE "
                        + MeasurementTables.TriggerContract.DEBUG_AD_ID
                        + " IS NOT NULL "
                        + "AND "
                        + MeasurementTables.TriggerContract.ENROLLMENT_ID
                        + " = ? "
                        + "AND "
                        + MeasurementTables.TriggerContract.DESTINATION_TYPE
                        + " = ?"
                        + ")");
    }

    private Cursor pendingEventReportIdsInWindowCursor(long windowStartTime, long windowEndTime)
            throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .query(
                        MeasurementTables.EventReportContract.TABLE,
                        /* columns= */ null,
                        MeasurementTables.EventReportContract.REPORT_TIME
                                + " >= ? AND "
                                + MeasurementTables.EventReportContract.REPORT_TIME
                                + " <= ? AND "
                                + MeasurementTables.EventReportContract.STATUS
                                + " = ? ",
                        new String[] {
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(EventReport.Status.PENDING)
                        },
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ "RANDOM()",
                        /* limit= */ null);
    }

    private Cursor pendingEventReportIdsInWindowCursorWithRetryLimit(
            long windowStartTime, long windowEndTime) throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .rawQuery(
                        "SELECT "
                                + MeasurementTables.EventReportContract.ID
                                + ", "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " FROM "
                                + MeasurementTables.EventReportContract.TABLE
                                + " LEFT JOIN "
                                + MeasurementTables.KeyValueDataContract.TABLE
                                + " ON ("
                                + MeasurementTables.EventReportContract.ID
                                + " = "
                                + MeasurementTables.KeyValueDataContract.KEY
                                + ") "
                                + "WHERE "
                                + "(CAST("
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " AS INTEGER) < ?"
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " IS NULL)"
                                + " AND "
                                + MeasurementTables.EventReportContract.STATUS
                                + " = ? "
                                + "AND "
                                + MeasurementTables.EventReportContract.REPORT_TIME
                                + " >= ?"
                                + "AND "
                                + MeasurementTables.EventReportContract.REPORT_TIME
                                + " <= ?"
                                + "AND ("
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " = ? "
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " IS NULL)"
                                + " ORDER BY RANDOM()",
                        new String[] {
                            String.valueOf(mReportingRetryLimitSupplier.get()),
                            String.valueOf(EventReport.Status.PENDING),
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(DataType.EVENT_REPORT_RETRY_COUNT),
                        });
    }

    private Cursor pendingDebugEventReportIdsCursor() throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .query(
                        MeasurementTables.EventReportContract.TABLE,
                        /* columns= */ null,
                        MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS + " = ? ",
                        new String[] {String.valueOf(EventReport.DebugReportStatus.PENDING)},
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ "RANDOM()",
                        /* limit= */ null);
    }

    private Cursor pendingDebugEventReportIdsLimitRetryCursor() throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .rawQuery(
                        "SELECT "
                                + MeasurementTables.EventReportContract.ID
                                + ", "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " FROM "
                                + MeasurementTables.EventReportContract.TABLE
                                + " LEFT JOIN "
                                + MeasurementTables.KeyValueDataContract.TABLE
                                + " ON ("
                                + MeasurementTables.EventReportContract.ID
                                + " = "
                                + MeasurementTables.KeyValueDataContract.KEY
                                + ") "
                                + " WHERE "
                                + MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS
                                + " = ?"
                                + " AND (CAST("
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " AS INTEGER) < ? "
                                + " OR "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " IS NULL)"
                                + " AND ("
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " = ? "
                                + " OR "
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " IS NULL)"
                                + " ORDER BY RANDOM()",
                        new String[] {
                            String.valueOf(EventReport.DebugReportStatus.PENDING),
                            String.valueOf(mReportingRetryLimitSupplier.get()),
                            String.valueOf(DataType.DEBUG_EVENT_REPORT_RETRY_COUNT),
                        });
    }

    private Cursor pendingAggregateReportIdsByCoordinatorInWindowCursor(
            long windowStartTime, long windowEndTime) throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .query(
                        MeasurementTables.AggregateReport.TABLE,
                        /* columns= */ new String[] {
                            MeasurementTables.AggregateReport.ID,
                            MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN
                        },
                        MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME
                                + " >= ? AND "
                                + MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME
                                + " <= ? AND "
                                + MeasurementTables.AggregateReport.STATUS
                                + " = ? ",
                        new String[] {
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(AggregateReport.Status.PENDING)
                        },
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ "RANDOM()",
                        /* limit= */ null);
    }

    private Cursor pendingAggregateReportIdsByCoordinatorInWindowLimitRetryCursor(
            long windowStartTime, long windowEndTime) throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .rawQuery(
                        "SELECT "
                                + MeasurementTables.AggregateReport.ID
                                + ", "
                                + MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN
                                + ", "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " FROM "
                                + MeasurementTables.AggregateReport.TABLE
                                + " LEFT JOIN "
                                + MeasurementTables.KeyValueDataContract.TABLE
                                + " ON ("
                                + MeasurementTables.AggregateReport.ID
                                + " = "
                                + MeasurementTables.KeyValueDataContract.KEY
                                + ") "
                                + " WHERE "
                                + MeasurementTables.AggregateReport.STATUS
                                + " = ? "
                                + "AND (CAST("
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " AS INTEGER) < ? "
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " IS NULL)"
                                + " AND "
                                + MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME
                                + " >= ?"
                                + " AND "
                                + MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME
                                + " <= ?"
                                + "AND ("
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " = ? "
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " IS NULL)"
                                + " ORDER BY RANDOM()",
                        new String[] {
                            String.valueOf(AggregateReport.Status.PENDING),
                            String.valueOf(mReportingRetryLimitSupplier.get()),
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(DataType.AGGREGATE_REPORT_RETRY_COUNT),
                        });
    }

    private Cursor debugReportIdsCursor() throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .query(
                        MeasurementTables.DebugReportContract.TABLE,
                        /* columns= */ null,
                        /* selection= */ null,
                        /* selectionArgs= */ null,
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ null,
                        /* limit= */ null);
    }

    private Cursor debugReportIdsLimitRetryCursor() throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .rawQuery(
                        "SELECT "
                                + MeasurementTables.DebugReportContract.ID
                                + ", "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " FROM "
                                + MeasurementTables.DebugReportContract.TABLE
                                + " LEFT JOIN "
                                + MeasurementTables.KeyValueDataContract.TABLE
                                + " ON ("
                                + MeasurementTables.DebugReportContract.ID
                                + " = "
                                + MeasurementTables.KeyValueDataContract.KEY
                                + ") "
                                + "WHERE (CAST("
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " AS INTEGER) < ? "
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " IS NULL)"
                                + "AND ("
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " = ? "
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " IS NULL)",
                        new String[] {
                            String.valueOf(mReportingRetryLimitSupplier.get()),
                            String.valueOf(DataType.DEBUG_REPORT_RETRY_COUNT.toString()),
                        });
    }

    private Cursor pendingAggregateDebugReportIdsByCoordinatorCursor() throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .query(
                        MeasurementTables.AggregateReport.TABLE,
                        /* columns= */ new String[] {
                            MeasurementTables.AggregateReport.ID,
                            MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN,
                        },
                        MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS + " = ? ",
                        new String[] {String.valueOf(AggregateReport.DebugReportStatus.PENDING)},
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ "RANDOM()",
                        /* limit= */ null);
    }

    private Cursor pendingAggregateDebugReportIdsByCoordinatorLimitRetryCursor()
            throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .rawQuery(
                        "SELECT "
                                + MeasurementTables.AggregateReport.ID
                                + ", "
                                + MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN
                                + ", "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " FROM "
                                + MeasurementTables.AggregateReport.TABLE
                                + " LEFT JOIN "
                                + MeasurementTables.KeyValueDataContract.TABLE
                                + " ON ("
                                + MeasurementTables.AggregateReport.ID
                                + " = "
                                + MeasurementTables.KeyValueDataContract.KEY
                                + ") "
                                + " WHERE "
                                + MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS
                                + " = ? "
                                + "AND (CAST("
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " AS INTEGER) < ? "
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " IS NULL)"
                                + "AND ("
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " = ? "
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " IS NULL)"
                                + " ORDER BY RANDOM()",
                        new String[] {
                            String.valueOf(AggregateReport.DebugReportStatus.PENDING),
                            String.valueOf(mReportingRetryLimitSupplier.get()),
                            String.valueOf(DataType.DEBUG_AGGREGATE_REPORT_RETRY_COUNT),
                        });
    }
}
