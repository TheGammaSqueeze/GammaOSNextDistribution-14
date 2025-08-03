/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.carrierapi.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static java.util.Map.entry;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.Telephony.Carriers;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for the APN database exposed by {@link Carriers}.
 *
 * <p>Test using `atest CtsCarrierApiTestCases:ApnDatabaseTest` or `make cts -j64 && cts-tradefed
 * run cts -m CtsCarrierApiTestCases --test android.carrierapi.cts.ApnDatabaseTest`
 */
@RunWith(AndroidJUnit4.class)
public class ApnDatabaseTest extends BaseCarrierApiTest {
    private static final String TAG = "ApnDatabaseTest";

    private ContentResolver mContentResolver;

    private static final String NAME = "carrierName";
    private static final String APN = "apn";
    private static final String PROXY = "proxy";
    private static final String PORT = "port";
    private static final String MMSC = "mmsc";
    private static final String MMSPROXY = "mmsproxy";
    private static final String MMSPORT = "mmsport";
    private static final String NUMERIC = "numeric";
    private static final String USER = "user";
    private static final String PASSWORD = "password";
    private static final String AUTH_TYPE = "auth_type";
    private static final String TYPE = "type";
    private static final String PROTOCOL = "protocol";
    private static final String ROAMING_PROTOCOL = "roaming_protocol";
    private static final String CARRIER_ENABLED = "true";
    private static final String NETWORK_TYPE_BITMASK = "0";
    private static final String BEARER = "0";

    private static final Map<String, String> APN_MAP = Map.ofEntries(
            entry(Carriers.NAME, NAME),
            entry(Carriers.APN, APN),
            entry(Carriers.PROXY, PROXY),
            entry(Carriers.PORT, PORT),
            entry(Carriers.MMSC, MMSC),
            entry(Carriers.MMSPROXY, MMSPROXY),
            entry(Carriers.MMSPORT, MMSPORT),
            entry(Carriers.NUMERIC, NUMERIC),
            entry(Carriers.USER, USER),
            entry(Carriers.PASSWORD, PASSWORD),
            entry(Carriers.AUTH_TYPE, AUTH_TYPE),
            entry(Carriers.TYPE, TYPE),
            entry(Carriers.PROTOCOL, PROTOCOL),
            entry(Carriers.ROAMING_PROTOCOL, ROAMING_PROTOCOL),
            entry(Carriers.CARRIER_ENABLED, CARRIER_ENABLED),
            entry(Carriers.NETWORK_TYPE_BITMASK, NETWORK_TYPE_BITMASK),
            entry(Carriers.BEARER, BEARER));

    // Faked network type bitmask and its compatible bearer bitmask.
    private static final int NETWORK_TYPE_BITMASK_NUMBER = 1 << (13 - 1);
    private static final int RIL_RADIO_TECHNOLOGY_BITMASK_NUMBER = 1 << (14 - 1);

    @Before
    public void setUp() throws Exception {
        mContentResolver = getContext().getContentResolver();
    }

    /**
     * Test inserting, querying, updating and deleting values in carriers table. Verify that the
     * inserted values match the result of the query and are deleted.
     */
    @Test
    public void testValidCase() {
        Uri uri = Carriers.CONTENT_URI;
        // Create A set of column_name/value pairs to add to the database.
        ContentValues contentValues = makeDefaultContentValues();

        try {
            // Insert the value into database.
            Log.d(TAG, "testInsertCarriers Inserting contentValues: " + contentValues.toString());
            Uri newUri = mContentResolver.insert(uri, contentValues);
            assertWithMessage("Failed to insert to table").that(newUri).isNotNull();

            // Get the values in table.
            final String selection = Carriers.NUMERIC + "=?";
            String[] selectionArgs = {NUMERIC};
            String[] apnProjection = APN_MAP.keySet().toArray(new String[APN_MAP.size()]);
            Log.d(
                    TAG,
                    "testInsertCarriers query projection: "
                            + Arrays.toString(apnProjection)
                            + "\ntestInsertCarriers selection: "
                            + selection
                            + "\ntestInsertCarriers selectionArgs: "
                            + Arrays.toString(selectionArgs));
            Cursor cursor =
                    mContentResolver.query(uri, apnProjection, selection, selectionArgs, null);

            // Verify that the inserted value match the results of the query
            assertWithMessage("Failed to query the table").that(cursor).isNotNull();
            assertWithMessage("Unexpected number of APNs returned by cursor")
                    .that(cursor.getCount())
                    .isEqualTo(1);
            cursor.moveToFirst();
            for (Map.Entry<String, String> entry : APN_MAP.entrySet()) {
                assertWithMessage("Unexpected value returned by cursor")
                        .that(cursor.getString(cursor.getColumnIndex(entry.getKey())))
                        .isEqualTo(entry.getValue());
            }

            // update the apn
            final String newApn = "newapn";
            Log.d(TAG, "Update the APN field to: " + newApn);
            contentValues.put(Carriers.APN, newApn);
            final int updateCount =
                    mContentResolver.update(uri, contentValues, selection, selectionArgs);
            assertWithMessage("Unexpected number of rows updated").that(updateCount).isEqualTo(1);

            // Verify the updated value
            cursor = mContentResolver.query(uri, apnProjection, selection, selectionArgs, null);
            assertWithMessage("Failed to query the table").that(cursor).isNotNull();
            assertWithMessage("Unexpected number of APNs returned by cursor")
                    .that(cursor.getCount())
                    .isEqualTo(1);
            cursor.moveToFirst();
            assertWithMessage("Unexpected value returned by cursor")
                    .that(cursor.getString(cursor.getColumnIndex(Carriers.APN)))
                    .isEqualTo(newApn);

            // delete test content
            final String selectionToDelete = Carriers.NUMERIC + "=?";
            String[] selectionArgsToDelete = {NUMERIC};
            Log.d(
                    TAG,
                    "testInsertCarriers deleting selection: "
                            + selectionToDelete
                            + "testInsertCarriers selectionArgs: "
                            + Arrays.toString(selectionArgs));
            int numRowsDeleted =
                    mContentResolver.delete(uri, selectionToDelete, selectionArgsToDelete);
            assertWithMessage("Unexpected number of rows deleted")
                    .that(numRowsDeleted)
                    .isEqualTo(1);

            // verify that deleted values are gone
            cursor = mContentResolver.query(uri, apnProjection, selection, selectionArgs, null);
            assertWithMessage("Unexpected number of rows deleted")
                    .that(cursor.getCount())
                    .isEqualTo(0);
        } catch (SecurityException e) {
            fail(NO_CARRIER_PRIVILEGES_FAILURE_MESSAGE);
        }
    }

    @Test
    public void testQueryConflictCase() {
        String invalidColumn = "random";
        Uri uri = Carriers.CONTENT_URI;
        // Create a set of column_name/value pairs to add to the database.
        ContentValues contentValues = new ContentValues();
        contentValues.put(Carriers.NAME, NAME);
        contentValues.put(Carriers.APN, APN);
        contentValues.put(Carriers.PORT, PORT);
        contentValues.put(Carriers.PROTOCOL, PROTOCOL);
        contentValues.put(Carriers.NUMERIC, NUMERIC);

        try {
            // Insert the value into database.
            Log.d(TAG, "testInsertCarriers Inserting contentValues: " + contentValues.toString());
            Uri newUri = mContentResolver.insert(uri, contentValues);
            assertWithMessage("Failed to insert to table").that(newUri).isNotNull();

            // Try to get the value with invalid selection
            final String[] testProjection = {
                Carriers.NAME, Carriers.APN, Carriers.PORT, Carriers.PROTOCOL, Carriers.NUMERIC,
            };
            final String selection = invalidColumn + "=?";
            String[] selectionArgs = {invalidColumn};
            Log.d(
                    TAG,
                    "testInsertCarriers query projection: "
                            + Arrays.toString(testProjection)
                            + "\ntestInsertCarriers selection: "
                            + selection
                            + "\ntestInsertCarriers selectionArgs: "
                            + Arrays.toString(selectionArgs));
            Cursor cursor =
                    mContentResolver.query(uri, testProjection, selection, selectionArgs, null);
            assertWithMessage("Failed to query the table").that(cursor).isNull();

            // delete test content
            final String selectionToDelete = Carriers.NAME + "=?";
            String[] selectionArgsToDelete = {NAME};
            Log.d(
                    TAG,
                    "testInsertCarriers deleting selection: "
                            + selectionToDelete
                            + "testInsertCarriers selectionArgs: "
                            + Arrays.toString(selectionArgs));
            int numRowsDeleted =
                    mContentResolver.delete(uri, selectionToDelete, selectionArgsToDelete);
            assertWithMessage("Unexpected number of rows deleted")
                    .that(numRowsDeleted)
                    .isEqualTo(1);

            // verify that deleted values are gone
            cursor =
                    mContentResolver.query(
                            uri, testProjection, selectionToDelete, selectionArgsToDelete, null);
            assertWithMessage("Unexpected number of rows deleted")
                    .that(cursor.getCount())
                    .isEqualTo(0);
        } catch (SecurityException e) {
            fail(NO_CARRIER_PRIVILEGES_FAILURE_MESSAGE);
        }
    }

    @Test
    public void testUpdateConflictCase() {
        Uri uri = Carriers.CONTENT_URI;
        // Create a set of column_name/value pairs to add to the database.
        ContentValues contentValues = new ContentValues();
        contentValues.put(Carriers.NAME, NAME);
        contentValues.put(Carriers.APN, APN);
        contentValues.put(Carriers.PORT, PORT);
        contentValues.put(Carriers.PROTOCOL, PROTOCOL);
        contentValues.put(Carriers.NUMERIC, NUMERIC);

        try {
            // Insert the value into database.
            Log.d(TAG, "testInsertCarriers Inserting contentValues: " + contentValues.toString());
            Uri newUri = mContentResolver.insert(uri, contentValues);
            assertWithMessage("Failed to insert to table").that(newUri).isNotNull();

            // Try to get the value with invalid selection
            final String[] testProjection = {
                Carriers.NAME, Carriers.APN, Carriers.PORT, Carriers.PROTOCOL, Carriers.NUMERIC,
            };
            String selection = Carriers.NAME + "=?";
            String[] selectionArgs = {NAME};
            Log.d(
                    TAG,
                    "testInsertCarriers query projection: "
                            + Arrays.toString(testProjection)
                            + "\ntestInsertCarriers selection: "
                            + selection
                            + "\ntestInsertCarriers selectionArgs: "
                            + Arrays.toString(selectionArgs));
            Cursor cursor =
                    mContentResolver.query(uri, testProjection, selection, selectionArgs, null);
            assertWithMessage("Unexpected number of APNs returned by cursor")
                    .that(cursor.getCount())
                    .isEqualTo(1);

            // Update the table with invalid column
            String invalidColumn = "random";
            contentValues.put(invalidColumn, invalidColumn);
            // Expected: If there's no such a column, an exception will be thrown and
            // ActivityManager will kill this process shortly.
            assertThrows(
                    SQLiteException.class,
                    () -> mContentResolver.update(uri, contentValues, selection, selectionArgs));

            // delete test content
            final String selectionToDelete = Carriers.NAME + "=?";
            String[] selectionArgsToDelete = {NAME};
            Log.d(
                    TAG,
                    "testInsertCarriers deleting selection: "
                            + selectionToDelete
                            + "testInsertCarriers selectionArgs: "
                            + Arrays.toString(selectionArgs));
            int numRowsDeleted =
                    mContentResolver.delete(uri, selectionToDelete, selectionArgsToDelete);
            assertWithMessage("Unexpected number of rows deleted")
                    .that(numRowsDeleted)
                    .isEqualTo(1);

            // verify that deleted values are gone
            cursor =
                    mContentResolver.query(
                            uri, testProjection, selectionToDelete, selectionArgsToDelete, null);
            assertWithMessage("Unexpected number of rows deleted")
                    .that(cursor.getCount())
                    .isEqualTo(0);
        } catch (SecurityException e) {
            fail(NO_CARRIER_PRIVILEGES_FAILURE_MESSAGE);
        }
    }

    @Test
    public void testDeleteConflictCase() {
        String invalidColumn = "random";
        Uri uri = Carriers.CONTENT_URI;
        // Create a set of column_name/value pairs to add to the database.
        ContentValues contentValues = new ContentValues();
        contentValues.put(Carriers.NAME, NAME);
        contentValues.put(Carriers.APN, APN);
        contentValues.put(Carriers.PORT, PORT);
        contentValues.put(Carriers.PROTOCOL, PROTOCOL);
        contentValues.put(Carriers.NUMERIC, NUMERIC);

        try {
            // Insert the value into database.
            Log.d(TAG, "testInsertCarriers Inserting contentValues: " + contentValues.toString());
            Uri newUri = mContentResolver.insert(uri, contentValues);
            assertWithMessage("Failed to insert to table").that(newUri).isNotNull();

            // Get the values in table.
            final String[] testProjection = {
                Carriers.NAME, Carriers.APN, Carriers.PORT, Carriers.PROTOCOL, Carriers.NUMERIC,
            };
            String selection = Carriers.NAME + "=?";
            String[] selectionArgs = {NAME};
            Log.d(
                    TAG,
                    "testInsertCarriers query projection: "
                            + Arrays.toString(testProjection)
                            + "\ntestInsertCarriers selection: "
                            + selection
                            + "\ntestInsertCarriers selectionArgs: "
                            + Arrays.toString(selectionArgs));
            Cursor cursor =
                    mContentResolver.query(uri, testProjection, selection, selectionArgs, null);
            assertWithMessage("Unexpected number of APNs returned by cursor")
                    .that(cursor.getCount())
                    .isEqualTo(1);

            // try to delete with invalid selection
            String invalidSelectionToDelete = invalidColumn + "=?";
            String[] invalidSelectionArgsToDelete = {invalidColumn};
            Log.d(
                    TAG,
                    "testInsertCarriers deleting selection: "
                            + invalidSelectionToDelete
                            + "testInsertCarriers selectionArgs: "
                            + Arrays.toString(selectionArgs));

            // Expected: If there's no such a column, an exception will be thrown and
            // ActivityManager will kill this process shortly.
            assertThrows(
                    SQLiteException.class,
                    () ->
                            mContentResolver.delete(
                                    uri, invalidSelectionToDelete, invalidSelectionArgsToDelete));

            // verify that deleted value is still there
            selection = Carriers.NAME + "=?";
            selectionArgs[0] = NAME;
            cursor = mContentResolver.query(uri, testProjection, selection, selectionArgs, null);
            assertWithMessage("Unexpected number of APNs returned by cursor")
                    .that(cursor.getCount())
                    .isEqualTo(1);

            // delete test content
            String selectionToDelete = Carriers.NAME + "=?";
            String[] selectionArgsToDelete = {NAME};
            Log.d(
                    TAG,
                    "testInsertCarriers deleting selection: "
                            + selectionToDelete
                            + "testInsertCarriers selectionArgs: "
                            + Arrays.toString(selectionArgs));
            int numRowsDeleted =
                    mContentResolver.delete(uri, selectionToDelete, selectionArgsToDelete);
            assertWithMessage("Unexpected number of rows deleted")
                    .that(numRowsDeleted)
                    .isEqualTo(1);

            // verify that deleted values are gone
            cursor = mContentResolver.query(uri, testProjection, selection, selectionArgs, null);
            assertWithMessage("Unexpected number of rows deleted")
                    .that(cursor.getCount())
                    .isEqualTo(0);
        } catch (SecurityException e) {
            fail(NO_CARRIER_PRIVILEGES_FAILURE_MESSAGE);
        }
    }

    private ContentValues makeDefaultContentValues() {
        ContentValues contentValues = new ContentValues();
        for (Map.Entry<String, String> entry : APN_MAP.entrySet()) {
            contentValues.put(entry.getKey(), entry.getValue());
        }
        return contentValues;
    }
}
