/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.cellbroadcastreceiver;

import static com.android.cellbroadcastservice.CellBroadcastMetrics.ERRSRC_CBR;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.ERRTYPE_PROVIDERINIT;

import android.annotation.NonNull;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.Telephony;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.CountDownLatch;

/**
 * ContentProvider for the database of received cell broadcasts.
 */
public class CellBroadcastContentProvider extends ContentProvider {
    private static final String TAG = "CellBroadcastContentProvider";

    /** URI matcher for ContentProvider queries. */
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    /** Authority string for content URIs. */
    @VisibleForTesting
    public static final String CB_AUTHORITY = "cellbroadcasts-app";

    /** Content URI for notifying observers. */
    static final Uri CONTENT_URI = Uri.parse("content://cellbroadcasts-app/");

    /** URI matcher type to get all cell broadcasts. */
    private static final int CB_ALL = 0;

    /** URI matcher type to get a cell broadcast by ID. */
    private static final int CB_ALL_ID = 1;

    /** MIME type for the list of all cell broadcasts. */
    private static final String CB_LIST_TYPE = "vnd.android.cursor.dir/cellbroadcast";

    /** MIME type for an individual cell broadcast. */
    private static final String CB_TYPE = "vnd.android.cursor.item/cellbroadcast";

    public static final String CALL_MIGRATION_METHOD = "migrate-legacy-data";

    static {
        sUriMatcher.addURI(CB_AUTHORITY, null, CB_ALL);
        sUriMatcher.addURI(CB_AUTHORITY, "#", CB_ALL_ID);
    }

    /**
     * The database for this content provider. Before using this we need to wait on
     * mInitializedLatch, which counts down once initialization finishes in a background thread
     */

    @VisibleForTesting
    public CellBroadcastDatabaseHelper mOpenHelper;

    // Latch which counts down from 1 when initialization in CellBroadcastOpenHelper.tryToMigrateV13
    // is finished
    private final CountDownLatch mInitializedLatch = new CountDownLatch(1);

    /**
     * Initialize content provider.
     * @return true if the provider was successfully loaded, false otherwise
     */
    @Override
    public boolean onCreate() {
        mOpenHelper = new CellBroadcastDatabaseHelper(getContext(), false);
        // trigger this to create database explicitly. Otherwise the db will be created only after
        // the first query/update/insertion. Data migration is done inside db creation and we want
        // to migrate data from cellbroadcast-legacy immediately when upgrade to the mainline module
        // rather than migrate after the first emergency alert.
        // getReadable database will also call tryToMigrateV13 which copies the DB file to allow
        // for safe rollbacks.
        // This is done in a background thread to avoid triggering an ANR if the disk operations are
        // too slow, and all other database uses should wait for the latch.
        new Thread(() -> {
            mOpenHelper.getReadableDatabase();
            mInitializedLatch.countDown();
        }).start();
        return true;
    }

    protected SQLiteDatabase awaitInitAndGetWritableDatabase() {
        while (mInitializedLatch.getCount() != 0) {
            try {
                mInitializedLatch.await();
            } catch (InterruptedException e) {
                CellBroadcastReceiverMetrics.getInstance().logModuleError(
                        ERRSRC_CBR, ERRTYPE_PROVIDERINIT);
                Log.e(TAG, "Interrupted while waiting for db initialization. e=" + e);
            }
        }
        return mOpenHelper.getWritableDatabase();
    }

    protected SQLiteDatabase awaitInitAndGetReadableDatabase() {
        while (mInitializedLatch.getCount() != 0) {
            try {
                mInitializedLatch.await();
            } catch (InterruptedException e) {
                CellBroadcastReceiverMetrics.getInstance().logModuleError(
                        ERRSRC_CBR, ERRTYPE_PROVIDERINIT);
                Log.e(TAG, "Interrupted while waiting for db initialization. e=" + e);
            }
        }
        return mOpenHelper.getReadableDatabase();
    }

    /**
     * Return a cursor for the cell broadcast table.
     * @param uri the URI to query.
     * @param projection the list of columns to put into the cursor, or null.
     * @param selection the selection criteria to apply when filtering rows, or null.
     * @param selectionArgs values to replace ?s in selection string.
     * @param sortOrder how the rows in the cursor should be sorted, or null to sort from most
     *  recently received to least recently received.
     * @return a Cursor or null.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(CellBroadcastDatabaseHelper.TABLE_NAME);

        int match = sUriMatcher.match(uri);
        switch (match) {
            case CB_ALL:
                // get all broadcasts
                break;

            case CB_ALL_ID:
                // get broadcast by ID
                qb.appendWhere("(_id=" + uri.getPathSegments().get(0) + ')');
                break;

            default:
                Log.e(TAG, "Invalid query: " + uri);
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        String orderBy;
        if (!TextUtils.isEmpty(sortOrder)) {
            orderBy = sortOrder;
        } else {
            orderBy = Telephony.CellBroadcasts.DEFAULT_SORT_ORDER;
        }

        SQLiteDatabase db = awaitInitAndGetReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), CONTENT_URI);
        }
        return c;
    }

    /**
     * Return the MIME type of the data at the specified URI.
     * @param uri the URI to query.
     * @return a MIME type string, or null if there is no type.
     */
    @Override
    public String getType(Uri uri) {
        int match = sUriMatcher.match(uri);
        switch (match) {
            case CB_ALL:
                return CB_LIST_TYPE;

            case CB_ALL_ID:
                return CB_TYPE;

            default:
                return null;
        }
    }

    /**
     * Insert a new row. This throws an exception, as the database can only be modified by
     * calling custom methods in this class, and not via the ContentProvider interface.
     * @param uri the content:// URI of the insertion request.
     * @param values a set of column_name/value pairs to add to the database.
     * @return the URI for the newly inserted item.
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("insert not supported");
    }

    /**
     * Delete one or more rows. This throws an exception, as the database can only be modified by
     * calling custom methods in this class, and not via the ContentProvider interface.
     * @param uri the full URI to query, including a row ID (if a specific record is requested).
     * @param selection an optional restriction to apply to rows when deleting.
     * @return the number of rows affected.
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("delete not supported");
    }

    /**
     * Update one or more rows. This throws an exception, as the database can only be modified by
     * calling custom methods in this class, and not via the ContentProvider interface.
     * @param uri the URI to query, potentially including the row ID.
     * @param values a Bundle mapping from column names to new column values.
     * @param selection an optional filter to match rows to update.
     * @return the number of rows affected.
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("update not supported");
    }

    @Override
    public Bundle call(String method, String name, Bundle args) {
        Log.d(TAG, "call:"
                + " method=" + method
                + " name=" + name
                + " args=" + args);
        // this is to handle a content-provider defined method: migration
        if (CALL_MIGRATION_METHOD.equals(method)) {
            mOpenHelper.migrateFromLegacyIfNeeded(awaitInitAndGetReadableDatabase());
        }
        return null;
    }

    private ContentValues getContentValues(SmsCbMessage message) {
        ContentValues cv = new ContentValues();
        cv.put(Telephony.CellBroadcasts.SLOT_INDEX, message.getSlotIndex());
        cv.put(Telephony.CellBroadcasts.GEOGRAPHICAL_SCOPE, message.getGeographicalScope());
        SmsCbLocation location = message.getLocation();
        cv.put(Telephony.CellBroadcasts.PLMN, location.getPlmn());
        if (location.getLac() != -1) {
            cv.put(Telephony.CellBroadcasts.LAC, location.getLac());
        }
        if (location.getCid() != -1) {
            cv.put(Telephony.CellBroadcasts.CID, location.getCid());
        }
        cv.put(Telephony.CellBroadcasts.SERIAL_NUMBER, message.getSerialNumber());
        cv.put(Telephony.CellBroadcasts.SERVICE_CATEGORY, message.getServiceCategory());
        cv.put(Telephony.CellBroadcasts.LANGUAGE_CODE, message.getLanguageCode());
        cv.put(Telephony.CellBroadcasts.MESSAGE_BODY, message.getMessageBody());
        cv.put(Telephony.CellBroadcasts.DELIVERY_TIME, message.getReceivedTime());
        cv.put(Telephony.CellBroadcasts.MESSAGE_FORMAT, message.getMessageFormat());
        cv.put(Telephony.CellBroadcasts.MESSAGE_PRIORITY, message.getMessagePriority());

        SmsCbEtwsInfo etwsInfo = message.getEtwsWarningInfo();
        if (etwsInfo != null) {
            cv.put(Telephony.CellBroadcasts.ETWS_WARNING_TYPE, etwsInfo.getWarningType());
        }

        SmsCbCmasInfo cmasInfo = message.getCmasWarningInfo();
        if (cmasInfo != null) {
            cv.put(Telephony.CellBroadcasts.CMAS_MESSAGE_CLASS, cmasInfo.getMessageClass());
            cv.put(Telephony.CellBroadcasts.CMAS_CATEGORY, cmasInfo.getCategory());
            cv.put(Telephony.CellBroadcasts.CMAS_RESPONSE_TYPE, cmasInfo.getResponseType());
            cv.put(Telephony.CellBroadcasts.CMAS_SEVERITY, cmasInfo.getSeverity());
            cv.put(Telephony.CellBroadcasts.CMAS_URGENCY, cmasInfo.getUrgency());
            cv.put(Telephony.CellBroadcasts.CMAS_CERTAINTY, cmasInfo.getCertainty());
        }

        return cv;
    }

    /**
     * Internal method to insert a new Cell Broadcast into the database and notify observers.
     * @param message the message to insert
     * @return true if the broadcast is new, false if it's a duplicate broadcast.
     */
    @VisibleForTesting
    public boolean insertNewBroadcast(SmsCbMessage message) {
        SQLiteDatabase db = awaitInitAndGetWritableDatabase();
        ContentValues cv = getContentValues(message);

        // Note: this method previously queried the database for duplicate message IDs, but this
        // is not compatible with CMAS carrier requirements and could also cause other emergency
        // alerts, e.g. ETWS, to not display if the database is filled with old messages.
        // Use duplicate message ID detection in CellBroadcastAlertService instead of DB query.
        long rowId = db.insert(CellBroadcastDatabaseHelper.TABLE_NAME, null, cv);
        if (rowId == -1) {
            Log.e(TAG, "failed to insert new broadcast into database");
            // Return true on DB write failure because we still want to notify the user.
            // The SmsCbMessage will be passed with the intent, so the message will be
            // displayed in the emergency alert dialog, or the dialog that is displayed when
            // the user selects the notification for a non-emergency broadcast, even if the
            // broadcast could not be written to the database.
        }
        return true;    // broadcast is not a duplicate
    }

    /**
     * Internal method to delete a cell broadcast by row ID and notify observers.
     * @param rowId the row ID of the broadcast to delete
     * @return true if the database was updated, false otherwise
     */
    @VisibleForTesting
    public boolean deleteBroadcast(long rowId) {
        SQLiteDatabase db = awaitInitAndGetWritableDatabase();

        int rowCount = db.delete(CellBroadcastDatabaseHelper.TABLE_NAME,
                Telephony.CellBroadcasts._ID + "=?",
                new String[]{Long.toString(rowId)});
        if (rowCount != 0) {
            return true;
        } else {
            Log.e(TAG, "failed to delete broadcast at row " + rowId);
            return false;
        }
    }

    /**
     * Internal method to delete all cell broadcasts and notify observers.
     * @return true if the database was updated, false otherwise
     */
    @VisibleForTesting
    public boolean deleteAllBroadcasts() {
        SQLiteDatabase db = awaitInitAndGetWritableDatabase();

        int rowCount = db.delete(CellBroadcastDatabaseHelper.TABLE_NAME, null, null);
        if (rowCount != 0) {
            return true;
        } else {
            Log.e(TAG, "failed to delete all broadcasts");
            return false;
        }
    }

    /**
     * Internal method to mark a broadcast as read and notify observers. The broadcast can be
     * identified by delivery time (for new alerts) or by row ID. The caller is responsible for
     * decrementing the unread non-emergency alert count, if necessary.
     *
     * @param columnName the column name to query (ID or delivery time)
     * @param columnValue the ID or delivery time of the broadcast to mark read
     * @return true if the database was updated, false otherwise
     */
    boolean markBroadcastRead(String columnName, long columnValue) {
        SQLiteDatabase db = awaitInitAndGetWritableDatabase();

        ContentValues cv = new ContentValues(1);
        cv.put(Telephony.CellBroadcasts.MESSAGE_READ, 1);

        String whereClause = columnName + "=?";
        String[] whereArgs = new String[]{Long.toString(columnValue)};

        int rowCount = db.update(CellBroadcastDatabaseHelper.TABLE_NAME, cv, whereClause, whereArgs);
        if (rowCount != 0) {
            return true;
        } else {
            Log.e(TAG, "failed to mark broadcast read: " + columnName + " = " + columnValue);
            return false;
        }
    }

    /**
     * Internal method to mark a broadcast received in direct boot mode. After user unlocks, mark
     * all messages not in direct boot mode.
     *
     * @param columnName the column name to query (ID or delivery time)
     * @param columnValue the ID or delivery time of the broadcast to mark read
     * @param isSmsSyncPending whether the message was pending for SMS inbox synchronization
     * @return true if the database was updated, false otherwise
     */
    @VisibleForTesting
    public boolean markBroadcastSmsSyncPending(String columnName, long columnValue,
            boolean isSmsSyncPending) {
        SQLiteDatabase db = awaitInitAndGetWritableDatabase();

        ContentValues cv = new ContentValues(1);
        cv.put(CellBroadcastDatabaseHelper.SMS_SYNC_PENDING, isSmsSyncPending ? 1 : 0);

        String whereClause = columnName + "=?";
        String[] whereArgs = new String[]{Long.toString(columnValue)};

        int rowCount = db.update(CellBroadcastDatabaseHelper.TABLE_NAME, cv, whereClause,
                whereArgs);
        if (rowCount != 0) {
            return true;
        } else {
            Log.e(TAG, "failed to mark broadcast pending for sms inbox sync:  " + isSmsSyncPending
                    + " where: " + columnName + " = " + columnValue);
            return false;
        }
    }

    /**
     * Write message to sms inbox if pending. e.g, when receive alerts in direct boot mode, we
     * might need to sync message to sms inbox after user unlock.
     * @param context
     */

    @VisibleForTesting
    public void resyncToSmsInbox(@NonNull Context context) {
        // query all messages currently marked as sms inbox sync pending
        try (Cursor cursor = query(
                CellBroadcastContentProvider.CONTENT_URI,
                CellBroadcastDatabaseHelper.QUERY_COLUMNS,
                CellBroadcastDatabaseHelper.SMS_SYNC_PENDING + "=1",
                null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    SmsCbMessage message = CellBroadcastCursorAdapter
                            .createFromCursor(context, cursor);
                    if (message != null) {
                        Log.d(TAG, "handling message received pending for sms sync: "
                                + message.toString());
                        writeMessageToSmsInbox(message, context);
                        // mark message received in direct mode was handled
                        markBroadcastSmsSyncPending(
                                Telephony.CellBroadcasts.DELIVERY_TIME,
                                message.getReceivedTime(), false);
                    }
                }
            }
        }
    }

    /**
     * Write displayed cellbroadcast messages to sms inbox
     *
     * @param message The cell broadcast message.
     */
    @VisibleForTesting
    public void writeMessageToSmsInbox(@NonNull SmsCbMessage message, @NonNull Context context) {
        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        if (!userManager.isSystemUser()) {
            // SMS database is single-user mode, discard non-system users to avoid inserting twice.
            Log.d(TAG, "ignoring writeMessageToSmsInbox due to non-system user");
            return;
        }
        // Note SMS database is not direct boot aware for privacy reasons, we should only interact
        // with sms db until users has unlocked.
        if (!userManager.isUserUnlocked()) {
            Log.d(TAG, "ignoring writeMessageToSmsInbox due to direct boot mode");
            // need to retry after user unlock
            markBroadcastSmsSyncPending(Telephony.CellBroadcasts.DELIVERY_TIME,
                        message.getReceivedTime(), true);
            return;
        }
        // composing SMS
        ContentValues cv = new ContentValues();
        cv.put(Telephony.Sms.Inbox.BODY, message.getMessageBody());
        cv.put(Telephony.Sms.Inbox.DATE, message.getReceivedTime());
        cv.put(Telephony.Sms.Inbox.SUBSCRIPTION_ID, message.getSubscriptionId());
        cv.put(Telephony.Sms.Inbox.SUBJECT, context.getString(
                CellBroadcastResources.getDialogTitleResource(context, message)));
        cv.put(Telephony.Sms.Inbox.ADDRESS,
                CellBroadcastResources.getSmsSenderAddressResourceEnglishString(context, message));
        cv.put(Telephony.Sms.Inbox.THREAD_ID, Telephony.Threads.getOrCreateThreadId(context,
                CellBroadcastResources.getSmsSenderAddressResourceEnglishString(context, message)));
        if (CellBroadcastSettings.getResourcesByOperator(context, message.getSubscriptionId(),
                        CellBroadcastReceiver.getRoamingOperatorSupported(context))
                .getBoolean(R.bool.always_mark_sms_read)) {
            // Always mark SMS message READ. End users expect when they read new CBS messages,
            // the unread alert count in the notification should be decreased, as they thought it
            // was coming from SMS. Now we are marking those SMS as read (SMS now serve as a message
            // history purpose) and that should give clear messages to end-users that alerts are not
            // from the SMS app but CellBroadcast and they should tap the notification to read alert
            // in order to see decreased unread message count.
            cv.put(Telephony.Sms.Inbox.READ, 1);
        }
        Uri uri = context.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI, cv);
        if (uri == null) {
            Log.e(TAG, "writeMessageToSmsInbox: failed");
        } else {
            Log.d(TAG, "writeMessageToSmsInbox: succeed uri = " + uri);
        }
    }

    /** Callback for users of AsyncCellBroadcastOperation. */
    interface CellBroadcastOperation {
        /**
         * Perform an operation using the specified provider.
         * @param provider the CellBroadcastContentProvider to use
         * @return true if any rows were changed, false otherwise
         */
        boolean execute(CellBroadcastContentProvider provider);
    }

    /**
     * Async task to call this content provider's internal methods on a background thread.
     * The caller supplies the CellBroadcastOperation object to call for this provider.
     */
    static class AsyncCellBroadcastTask extends AsyncTask<CellBroadcastOperation, Void, Void> {
        /** Reference to this app's content resolver. */
        private ContentResolver mContentResolver;

        AsyncCellBroadcastTask(ContentResolver contentResolver) {
            mContentResolver = contentResolver;
        }

        /**
         * Perform a generic operation on the CellBroadcastContentProvider.
         * @param params the CellBroadcastOperation object to call for this provider
         * @return void
         */
        @Override
        protected Void doInBackground(CellBroadcastOperation... params) {
            ContentProviderClient cpc = mContentResolver.acquireContentProviderClient(
                    CellBroadcastContentProvider.CB_AUTHORITY);
            CellBroadcastContentProvider provider = (CellBroadcastContentProvider)
                    cpc.getLocalContentProvider();

            if (provider != null) {
                try {
                    boolean changed = params[0].execute(provider);
                    if (changed) {
                        Log.d(TAG, "database changed: notifying observers...");
                        mContentResolver.notifyChange(CONTENT_URI, null, false);
                    }
                } finally {
                    cpc.release();
                }
            } else {
                Log.e(TAG, "getLocalContentProvider() returned null");
            }

            mContentResolver = null;    // free reference to content resolver
            return null;
        }
    }
}
