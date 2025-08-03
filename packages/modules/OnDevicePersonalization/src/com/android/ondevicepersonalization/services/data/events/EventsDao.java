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

package com.android.ondevicepersonalization.services.data.events;

import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.data.DbUtils;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Dao used to manage access to Events and Queries tables
 */
public class EventsDao {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "EventsDao";
    private static final String JOINED_EVENT_TIME_MILLIS = "eventTimeMillis";
    private static final String JOINED_QUERY_TIME_MILLIS = "queryTimeMillis";

    private static volatile EventsDao sSingleton;

    private final OnDevicePersonalizationDbHelper mDbHelper;

    private EventsDao(@NonNull OnDevicePersonalizationDbHelper dbHelper) {
        this.mDbHelper = dbHelper;
    }

    /** Returns an instance of the EventsDao given a context. */
    public static EventsDao getInstance(@NonNull Context context) {
        if (sSingleton == null) {
            synchronized (EventsDao.class) {
                if (sSingleton == null) {
                    OnDevicePersonalizationDbHelper dbHelper =
                            OnDevicePersonalizationDbHelper.getInstance(context);
                    sSingleton = new EventsDao(dbHelper);
                }
            }
        }
        return sSingleton;
    }

    /**
     * Returns an instance of the EventsDao given a context. This is used
     * for testing only.
     */
    @VisibleForTesting
    public static EventsDao getInstanceForTest(@NonNull Context context) {
        synchronized (EventsDao.class) {
            if (sSingleton == null) {
                OnDevicePersonalizationDbHelper dbHelper =
                        OnDevicePersonalizationDbHelper.getInstanceForTest(context);
                sSingleton = new EventsDao(dbHelper);
            }
            return sSingleton;
        }
    }

    /**
     * Inserts the Event into the Events table.
     *
     * @return The row id of the newly inserted row if successful, -1 otherwise
     */
    public long insertEvent(@NonNull Event event) {
        try {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(EventsContract.EventsEntry.QUERY_ID, event.getQueryId());
            values.put(EventsContract.EventsEntry.ROW_INDEX, event.getRowIndex());
            values.put(EventsContract.EventsEntry.TIME_MILLIS, event.getTimeMillis());
            values.put(EventsContract.EventsEntry.SERVICE_NAME,
                    event.getServiceName());
            values.put(EventsContract.EventsEntry.TYPE, event.getType());
            values.put(EventsContract.EventsEntry.EVENT_DATA, event.getEventData());
            return db.insert(EventsContract.EventsEntry.TABLE_NAME, null,
                    values);
        } catch (SQLiteException e) {
            sLogger.e(TAG + ": Failed to insert event", e);
        }
        return -1;
    }


    /**
     * Inserts the List of Events into the Events table.
     *
     * @return true if all inserts succeeded, false otherwise.
     */
    public boolean insertEvents(@NonNull List<Event> events) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            db.beginTransactionNonExclusive();
            for (Event event : events) {
                if (insertEvent(event) == -1) {
                    return false;
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            sLogger.e(TAG + ": Failed to insert events", e);
            return false;
        } finally {
            db.endTransaction();
        }
        return true;
    }

    /**
     * Inserts the Query into the Queries table.
     *
     * @return The row id of the newly inserted row if successful, -1 otherwise
     */
    public long insertQuery(@NonNull Query query) {
        try {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(QueriesContract.QueriesEntry.TIME_MILLIS, query.getTimeMillis());
            values.put(QueriesContract.QueriesEntry.SERVICE_NAME,
                    query.getServiceName());
            values.put(QueriesContract.QueriesEntry.QUERY_DATA, query.getQueryData());
            return db.insert(QueriesContract.QueriesEntry.TABLE_NAME, null,
                    values);
        } catch (SQLiteException e) {
            sLogger.e(TAG + ": Failed to insert query", e);
        }
        return -1;
    }

    /**
     * Updates the eventState, adds it if it doesn't already exist.
     *
     * @return true if the update/insert succeeded, false otherwise
     */
    public boolean updateOrInsertEventState(EventState eventState) {
        try {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(EventStateContract.EventStateEntry.TOKEN, eventState.getToken());
            values.put(EventStateContract.EventStateEntry.SERVICE_NAME,
                    eventState.getServiceName());
            values.put(EventStateContract.EventStateEntry.TASK_IDENTIFIER,
                    eventState.getTaskIdentifier());
            return db.insertWithOnConflict(EventStateContract.EventStateEntry.TABLE_NAME,
                    null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1;
        } catch (SQLiteException e) {
            sLogger.e(TAG + ": Failed to update or insert eventState", e);
        }
        return false;
    }

    /**
     * Updates/inserts a list of EventStates as a transaction
     *
     * @return true if the all the update/inserts succeeded, false otherwise
     */
    public boolean updateOrInsertEventStatesTransaction(List<EventState> eventStates) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            db.beginTransactionNonExclusive();
            for (EventState eventState : eventStates) {
                if (!updateOrInsertEventState(eventState)) {
                    return false;
                }
            }

            db.setTransactionSuccessful();
        } catch (Exception e) {
            sLogger.e(TAG + ": Failed to insert/update eventstates", e);
            return false;
        } finally {
            db.endTransaction();
        }
        return true;
    }

    /**
     * Gets the eventState for the given package and task
     *
     * @return eventState if found, null otherwise
     */
    public EventState getEventState(String taskIdentifier, ComponentName service) {
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection = EventStateContract.EventStateEntry.TASK_IDENTIFIER + " = ? AND "
                + EventStateContract.EventStateEntry.SERVICE_NAME + " = ?";
        String[] selectionArgs = {taskIdentifier, DbUtils.toTableValue(service)};
        String[] projection = {EventStateContract.EventStateEntry.TOKEN};
        try (Cursor cursor = db.query(
                EventStateContract.EventStateEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                /* groupBy= */ null,
                /* having= */ null,
                /* orderBy= */ null
        )) {
            if (cursor.moveToFirst()) {
                byte[] token = cursor.getBlob(cursor.getColumnIndexOrThrow(
                        EventStateContract.EventStateEntry.TOKEN));

                return new EventState.Builder()
                        .setToken(token)
                        .setService(service)
                        .setTaskIdentifier(taskIdentifier)
                        .build();
            }
        } catch (SQLiteException e) {
            sLogger.e(TAG + ": Failed to read eventState", e);
        }
        return null;
    }

    /**
     * Queries the events and queries table to return all new rows from given ids for the given
     * package
     *
     * @param service            Name of the service to read rows for
     * @param fromEventId        EventId to find all new rows from
     * @param fromQueryId        QueryId to find all new rows from
     * @return List of JoinedEvents.
     */
    public List<JoinedEvent> readAllNewRowsForPackage(ComponentName service,
            long fromEventId, long fromQueryId) {
        String serviceName = DbUtils.toTableValue(service);
        // Query on the joined query & event table
        String joinedSelection = EventsContract.EventsEntry.EVENT_ID + " > ?"
                + " AND " + EventsContract.EventsEntry.TABLE_NAME + "."
                + EventsContract.EventsEntry.SERVICE_NAME + " = ?";
        String[] joinedSelectionArgs = {String.valueOf(fromEventId), serviceName};
        List<JoinedEvent> joinedEventList = readJoinedTableRows(joinedSelection,
                joinedSelectionArgs);

        // Query on the queries table
        String queriesSelection = QueriesContract.QueriesEntry.QUERY_ID + " > ?"
                + " AND " + QueriesContract.QueriesEntry.SERVICE_NAME + " = ?";
        String[] queriesSelectionArgs = {String.valueOf(fromQueryId), serviceName};
        List<Query> queryList = readQueryRows(queriesSelection, queriesSelectionArgs);
        for (Query query : queryList) {
            joinedEventList.add(new JoinedEvent.Builder()
                    .setQueryId(query.getQueryId())
                    .setQueryData(query.getQueryData())
                    .setQueryTimeMillis(query.getTimeMillis())
                    .setService(query.getService())
                    .build());
        }
        return joinedEventList;
    }

    /**
     * Queries the events and queries table to return all new rows from given ids for all packages
     *
     * @param fromEventId EventId to find all new rows from
     * @param fromQueryId QueryId to find all new rows from
     * @return List of JoinedEvents.
     */
    public List<JoinedEvent> readAllNewRows(long fromEventId, long fromQueryId) {
        // Query on the joined query & event table
        String joinedSelection = EventsContract.EventsEntry.EVENT_ID + " > ?";
        String[] joinedSelectionArgs = {String.valueOf(fromEventId)};
        List<JoinedEvent> joinedEventList = readJoinedTableRows(joinedSelection,
                joinedSelectionArgs);

        // Query on the queries table
        String queriesSelection = QueriesContract.QueriesEntry.QUERY_ID + " > ?";
        String[] queriesSelectionArgs = {String.valueOf(fromQueryId)};
        List<Query> queryList = readQueryRows(queriesSelection, queriesSelectionArgs);
        for (Query query : queryList) {
            joinedEventList.add(new JoinedEvent.Builder()
                    .setQueryId(query.getQueryId())
                    .setQueryData(query.getQueryData())
                    .setQueryTimeMillis(query.getTimeMillis())
                    .setService(query.getService())
                    .build());
        }
        return joinedEventList;
    }

    private List<Query> readQueryRows(String selection, String[] selectionArgs) {
        List<Query> queries = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String orderBy = QueriesContract.QueriesEntry.QUERY_ID;
        try (Cursor cursor = db.query(
                QueriesContract.QueriesEntry.TABLE_NAME,
                /* projection= */ null,
                selection,
                selectionArgs,
                /* groupBy= */ null,
                /* having= */ null,
                orderBy
        )) {
            while (cursor.moveToNext()) {
                long queryId = cursor.getLong(
                        cursor.getColumnIndexOrThrow(QueriesContract.QueriesEntry.QUERY_ID));
                byte[] queryData = cursor.getBlob(
                        cursor.getColumnIndexOrThrow(QueriesContract.QueriesEntry.QUERY_DATA));
                long timeMillis = cursor.getLong(
                        cursor.getColumnIndexOrThrow(QueriesContract.QueriesEntry.TIME_MILLIS));
                String serviceName = cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                QueriesContract.QueriesEntry.SERVICE_NAME));
                queries.add(new Query.Builder()
                        .setQueryId(queryId)
                        .setQueryData(queryData)
                        .setTimeMillis(timeMillis)
                        .setService(DbUtils.fromTableValue(serviceName))
                        .build()
                );
            }
        } catch (IllegalArgumentException e) {
            sLogger.e(e, TAG + ": Failed parse resulting query");
            return new ArrayList<>();
        }
        return queries;
    }

    private List<JoinedEvent> readJoinedTableRows(String selection, String[] selectionArgs) {
        List<JoinedEvent> joinedEventList = new ArrayList<>();

        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String select = "SELECT "
                + EventsContract.EventsEntry.EVENT_ID + ","
                + EventsContract.EventsEntry.ROW_INDEX + ","
                + EventsContract.EventsEntry.TYPE + ","
                + EventsContract.EventsEntry.TABLE_NAME + "."
                + EventsContract.EventsEntry.SERVICE_NAME + ","
                + EventsContract.EventsEntry.EVENT_DATA + ","
                + EventsContract.EventsEntry.TABLE_NAME + "."
                + EventsContract.EventsEntry.TIME_MILLIS + " AS " + JOINED_EVENT_TIME_MILLIS + ","
                + EventsContract.EventsEntry.TABLE_NAME + "."
                + EventsContract.EventsEntry.QUERY_ID + ","
                + QueriesContract.QueriesEntry.QUERY_DATA + ","
                + QueriesContract.QueriesEntry.TABLE_NAME + "."
                + QueriesContract.QueriesEntry.TIME_MILLIS + " AS " + JOINED_QUERY_TIME_MILLIS;
        String from = " FROM " + EventsContract.EventsEntry.TABLE_NAME
                + " INNER JOIN " + QueriesContract.QueriesEntry.TABLE_NAME
                + " ON "
                + QueriesContract.QueriesEntry.TABLE_NAME + "."
                + QueriesContract.QueriesEntry.QUERY_ID + " = "
                + EventsContract.EventsEntry.TABLE_NAME + "." + EventsContract.EventsEntry.QUERY_ID;
        String where = " WHERE " + selection;
        String orderBy = " ORDER BY " + EventsContract.EventsEntry.EVENT_ID;
        String query = select + from + where + orderBy;
        try (Cursor cursor = db.rawQuery(query, selectionArgs)) {
            while (cursor.moveToNext()) {
                long eventId = cursor.getLong(
                        cursor.getColumnIndexOrThrow(EventsContract.EventsEntry.EVENT_ID));
                int rowIndex = cursor.getInt(
                        cursor.getColumnIndexOrThrow(EventsContract.EventsEntry.ROW_INDEX));
                int type = cursor.getInt(
                        cursor.getColumnIndexOrThrow(EventsContract.EventsEntry.TYPE));
                String serviceName = cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                EventsContract.EventsEntry.SERVICE_NAME));
                byte[] eventData = cursor.getBlob(
                        cursor.getColumnIndexOrThrow(EventsContract.EventsEntry.EVENT_DATA));
                long eventTimeMillis = cursor.getLong(
                        cursor.getColumnIndexOrThrow(JOINED_EVENT_TIME_MILLIS));
                long queryId = cursor.getLong(
                        cursor.getColumnIndexOrThrow(QueriesContract.QueriesEntry.QUERY_ID));
                byte[] queryData = cursor.getBlob(
                        cursor.getColumnIndexOrThrow(QueriesContract.QueriesEntry.QUERY_DATA));
                long queryTimeMillis = cursor.getLong(
                        cursor.getColumnIndexOrThrow(JOINED_QUERY_TIME_MILLIS));
                joinedEventList.add(new JoinedEvent.Builder()
                        .setEventId(eventId)
                        .setRowIndex(rowIndex)
                        .setType(type)
                        .setEventData(eventData)
                        .setEventTimeMillis(eventTimeMillis)
                        .setQueryId(queryId)
                        .setQueryData(queryData)
                        .setQueryTimeMillis(queryTimeMillis)
                        .setService(DbUtils.fromTableValue(serviceName))
                        .build()
                );
            }
        } catch (IllegalArgumentException e) {
            sLogger.e(e, TAG + ": Failed parse resulting query of join statement");
            return new ArrayList<>();
        }
        return joinedEventList;
    }

    /**
     * Deletes all eventStates for the given packageName
     *
     * @return true if the delete executed successfully, false otherwise.
     */
    public boolean deleteEventState(ComponentName service) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            String selection = EventStateContract.EventStateEntry.SERVICE_NAME + " = ?";
            String[] selectionArgs = {DbUtils.toTableValue(service)};
            db.delete(EventStateContract.EventStateEntry.TABLE_NAME, selection,
                    selectionArgs);
        } catch (Exception e) {
            sLogger.e(e, TAG + ": Failed to delete eventState for: " + service.toString());
            return false;
        }
        return true;
    }

    /**
     * Deletes all events and queries older than the given timestamp
     *
     * @return true if the delete executed successfully, false otherwise.
     */
    public boolean deleteEventsAndQueries(long timestamp) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            db.beginTransactionNonExclusive();
            // Delete from events table first to satisfy FK requirements.
            String eventsSelection = EventsContract.EventsEntry.TIME_MILLIS + " < ?";
            String[] eventsSelectionArgs = {String.valueOf(timestamp)};
            db.delete(EventsContract.EventsEntry.TABLE_NAME, eventsSelection,
                    eventsSelectionArgs);

            // Delete from queries table older than timestamp AND have no events left.
            String queriesSelection = QueriesContract.QueriesEntry.TIME_MILLIS + " < ?"
                    + " AND " + QueriesContract.QueriesEntry.QUERY_ID
                    + " NOT IN (SELECT " + EventsContract.EventsEntry.QUERY_ID
                    + " FROM " + EventsContract.EventsEntry.TABLE_NAME + ")";
            String[] queriesSelectionArgs = {String.valueOf(timestamp)};
            db.delete(QueriesContract.QueriesEntry.TABLE_NAME, queriesSelection,
                    queriesSelectionArgs);

            db.setTransactionSuccessful();
        } catch (Exception e) {
            sLogger.e(e, TAG + ": Failed to delete events and queries older than: " + timestamp);
            return false;
        } finally {
            db.endTransaction();
        }
        return true;
    }

    /**
     * Reads all queries in the query table between the given timestamps.
     *
     * @return List of Query in the query table.
     */
    public List<Query> readAllQueries(long startTimeMillis, long endTimeMillis,
            ComponentName service) {
        String selection = QueriesContract.QueriesEntry.TIME_MILLIS + " > ?"
                + " AND " + QueriesContract.QueriesEntry.TIME_MILLIS + " < ?"
                + " AND " + QueriesContract.QueriesEntry.SERVICE_NAME + " = ?";
        String[] selectionArgs = {String.valueOf(startTimeMillis), String.valueOf(
                endTimeMillis), DbUtils.toTableValue(service)};
        return readQueryRows(selection, selectionArgs);
    }

    /**
     * Reads all ids in the event table between the given timestamps.
     *
     * @return List of ids in the event table.
     */
    public List<Long> readAllEventIds(long startTimeMillis, long endTimeMillis,
            ComponentName service) {
        List<Long> idList = new ArrayList<>();
        try {
            SQLiteDatabase db = mDbHelper.getReadableDatabase();
            String[] projection = {EventsContract.EventsEntry.EVENT_ID};
            String selection = EventsContract.EventsEntry.TIME_MILLIS + " > ?"
                    + " AND " + EventsContract.EventsEntry.TIME_MILLIS + " < ?"
                    + " AND " + EventsContract.EventsEntry.SERVICE_NAME + " = ?";
            String[] selectionArgs = {String.valueOf(startTimeMillis), String.valueOf(
                    endTimeMillis), DbUtils.toTableValue(service)};
            String orderBy = EventsContract.EventsEntry.EVENT_ID;
            try (Cursor cursor = db.query(
                    EventsContract.EventsEntry.TABLE_NAME,
                    projection,
                    selection,
                    selectionArgs,
                    /* groupBy= */ null,
                    /* having= */ null,
                    orderBy
            )) {
                while (cursor.moveToNext()) {
                    Long id = cursor.getLong(
                            cursor.getColumnIndexOrThrow(EventsContract.EventsEntry.EVENT_ID));
                    idList.add(id);
                }
                cursor.close();
                return idList;
            }
        } catch (SQLiteException e) {
            sLogger.e(TAG + ": Failed to read event ids", e);
        }
        return idList;
    }


    /**
     * Reads all ids in the event table associated with the specified queryId
     *
     * @return List of ids in the event table.
     */
    public List<Long> readAllEventIdsForQuery(long queryId, ComponentName service) {
        List<Long> idList = new ArrayList<>();
        try {
            SQLiteDatabase db = mDbHelper.getReadableDatabase();
            String[] projection = {EventsContract.EventsEntry.EVENT_ID};
            String selection = EventsContract.EventsEntry.QUERY_ID + " = ?"
                    + " AND " + EventsContract.EventsEntry.SERVICE_NAME + " = ?";
            String[] selectionArgs = {String.valueOf(queryId), DbUtils.toTableValue(service)};
            String orderBy = EventsContract.EventsEntry.EVENT_ID;
            try (Cursor cursor = db.query(
                    EventsContract.EventsEntry.TABLE_NAME,
                    projection,
                    selection,
                    selectionArgs,
                    /* groupBy= */ null,
                    /* having= */ null,
                    orderBy
            )) {
                while (cursor.moveToNext()) {
                    Long id = cursor.getLong(
                            cursor.getColumnIndexOrThrow(EventsContract.EventsEntry.EVENT_ID));
                    idList.add(id);
                }
                cursor.close();
                return idList;
            }
        } catch (SQLiteException e) {
            sLogger.e(TAG + ": Failed to read event ids for specified queryid", e);
        }
        return idList;
    }

    /**
     * Reads single row in the query table
     *
     * @return Query object for the single row requested
     */
    public Query readSingleQueryRow(long queryId, ComponentName service) {
        try {
            SQLiteDatabase db = mDbHelper.getReadableDatabase();
            String selection = QueriesContract.QueriesEntry.QUERY_ID + " = ?"
                    + " AND " + QueriesContract.QueriesEntry.SERVICE_NAME + " = ?";
            String[] selectionArgs = {String.valueOf(queryId), DbUtils.toTableValue(service)};
            try (Cursor cursor = db.query(
                    QueriesContract.QueriesEntry.TABLE_NAME,
                    /* projection= */ null,
                    selection,
                    selectionArgs,
                    /* groupBy= */ null,
                    /* having= */ null,
                    /* orderBy= */ null
            )) {
                if (cursor.getCount() < 1) {
                    sLogger.d(TAG + ": Failed to find requested id: " + queryId);
                    return null;
                }
                cursor.moveToNext();
                long id = cursor.getLong(
                        cursor.getColumnIndexOrThrow(QueriesContract.QueriesEntry.QUERY_ID));
                byte[] queryData = cursor.getBlob(
                        cursor.getColumnIndexOrThrow(QueriesContract.QueriesEntry.QUERY_DATA));
                long timeMillis = cursor.getLong(
                        cursor.getColumnIndexOrThrow(QueriesContract.QueriesEntry.TIME_MILLIS));
                String serviceName = cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                QueriesContract.QueriesEntry.SERVICE_NAME));
                return new Query.Builder()
                        .setQueryId(id)
                        .setQueryData(queryData)
                        .setTimeMillis(timeMillis)
                        .setService(service)
                        .build();
            }
        } catch (SQLiteException e) {
            sLogger.e(TAG + ": Failed to read query row", e);
        }
        return null;
    }

    /**
     * Reads single row in the event table joined with its corresponding query
     *
     * @return JoinedEvent representing the event joined with its query
     */
    public JoinedEvent readSingleJoinedTableRow(long eventId, ComponentName service) {
        String selection = EventsContract.EventsEntry.EVENT_ID + " = ?"
                + " AND " + EventsContract.EventsEntry.TABLE_NAME + "."
                + EventsContract.EventsEntry.SERVICE_NAME + " = ?";
        String[] selectionArgs = {String.valueOf(eventId), DbUtils.toTableValue(service)};
        List<JoinedEvent> joinedEventList = readJoinedTableRows(selection, selectionArgs);
        if (joinedEventList.size() < 1) {
            sLogger.d(TAG + ": Failed to find requested id: " + eventId);
            return null;
        }
        return joinedEventList.get(0);
    }

    /**
     * Reads all row in the event table joined with its corresponding query within the given time
     * range.
     *
     * @return List of JoinedEvents representing the event joined with its query
     */
    public List<JoinedEvent> readJoinedTableRows(long startTimeMillis, long endTimeMillis,
            ComponentName service) {
        String selection = JOINED_EVENT_TIME_MILLIS + " > ?"
                + " AND " + JOINED_EVENT_TIME_MILLIS + " < ?"
                + " AND " + EventsContract.EventsEntry.TABLE_NAME + "."
                + EventsContract.EventsEntry.SERVICE_NAME + " = ?";
        String[] selectionArgs = {String.valueOf(startTimeMillis), String.valueOf(
                endTimeMillis), DbUtils.toTableValue(service)};
        return readJoinedTableRows(selection, selectionArgs);
    }
}
