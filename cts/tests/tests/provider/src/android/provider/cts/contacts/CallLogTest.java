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
 * limitations under the License
 */

package android.provider.cts.contacts;

import static org.junit.Assert.assertArrayEquals;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.cts.R;
import android.test.InstrumentationTestCase;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.ShellUtils;
import com.android.server.telecom.flags.Flags;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CallLogTest extends InstrumentationTestCase {
    private static final String TAG = CallLogTest.class.getSimpleName();
    // Test Call Log Entry
    private static final String TEST_NUMBER = "5625698388";
    private static final int TEST_DATE = 1000;
    private static final int TEST_DURATION = 30;
    // Test Voicemail Log Entry
    private static final String TEST_VOICEMAIL_NUMBER = "1119871234";
    private static final int TEST_VOCIEMAIL_DATE = 1;
    private static final int TEST_VOICEMAIL_DURATION = 5;
    // Timeout
    private static final long CONTENT_RESOLVER_TIMEOUT_MS = 5000;
    // SQL Selection Column Names
    private static final String SELECTION_TYPE = "type";
    private static final String SELECTION_NUMBER = "number";
    private static final String SELECTION_DATE = "date";
    private static final String SELECTION_DURATION = "duration";
    private static final String SELECTION_NEW = "new";
    // SQL Selection as array
    private static final String[] SELECTION =
            new String[]{SELECTION_TYPE, SELECTION_NUMBER, SELECTION_DATE,
                    SELECTION_DURATION, SELECTION_NEW};
    // Test filter URI that throws Security Exception
    private static final Uri INVALID_FILTER_URI = Uri.parse(
            "content://call_log/calls/filter/test\uD83D')) union select type,name,"
                    + "tbl_name,rootpage,sql FROM SQLITE_MASTER; --");
    // Test call composer URI that throws Security Exception
    private static final Uri INVALID_CALL_LOG_URI = Uri.parse(
            "content://call_log/call_composer/%2fdata%2fdata%2fcom.android.providers"
                    + ".contacts%2fdatabases%2fcontacts2.db");
    // Test Failure Error
    private static final String TEST_FAIL_DID_NOT_TRHOW_SE =
            "fail test because Security Exception was not throw";
    private static final String HSUM_MGG = "in headless system user mode (HSUM); skipping tests.";
    // Instance vars
    private ContentResolver mContentResolver;

    // Class to objectify the call log data (returned from a Cursor object)
    public class LogEntry {
        // properties
        public Integer type;
        public String number;
        public Integer date;
        public Integer duration;
        public Integer newCount;
        public String extras;

        // setter
        public void setValue(String selectionColumn, String value) {
            if (value == null) {
                // Integer.valueOf(value) throws NumberFormatException if string is null.
                // so return early if value is null.
                return;
            }
            try {
                switch (selectionColumn) {
                    case SELECTION_TYPE:
                        type = Integer.valueOf(value);
                        break;
                    case SELECTION_NUMBER:
                        number = value;
                        break;
                    case SELECTION_DATE:
                        date = Integer.valueOf(value);
                        break;
                    case SELECTION_DURATION:
                        duration = Integer.valueOf(value);
                        break;
                    case SELECTION_NEW:
                        newCount = Integer.valueOf(value);
                        break;
                    default:
                        extras = value;
                }
            } catch (NumberFormatException e) {
                // pass through
            }
        }
    }

    @Override
    public void setUp() throws Exception {
        // Sets up this package as default dialer in super.
        super.setUp();
        mContentResolver = getInstrumentation().getContext().getContentResolver();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Ensure that the existing query functionality still works.  To verify the functionality,
     * this test adds a single call and voicemail entry to the logs, queries the logs,
     * and asserts the entries are returned.
     */
    public void testPopulateAndQueryCallAndVoicemailLogs() {
        if (UserManager.isHeadlessSystemUserMode()) {
            Log.i(TAG, "testPopulateAndQueryCallAndVoicemailLogs: " + HSUM_MGG);
            return;
        }
        Context context = getInstrumentation().getContext();
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELECOM)) {
            // This is tied to default-dialer, so don't test if the device doesn't have telecom.
            return;
        }

        try {
            // needed in order to populate call log database
            ShellUtils.runShellCommand("telecom set-default-dialer %s",
                    getInstrumentation().getContext().getPackageName());

            populateLogsWithDefaults();

            // query and get cursor
            Cursor cursor = mContentResolver
                    .query(Calls.CONTENT_URI_WITH_VOICEMAIL, SELECTION, null, null);

            // extract the data from the cursor and put the objects in a map
            Map<String, LogEntry> entries = collectCursorEntries(cursor);

            // cleanup
            cursor.close();

            // call entry
            assertEquals(TEST_NUMBER, entries.get(TEST_NUMBER).number);
            // voicemail entry
            assertEquals(TEST_VOICEMAIL_NUMBER, entries.get(TEST_VOICEMAIL_NUMBER).number);
        } finally {
            //cleanup
            deletePopulatedLogs();
            ShellUtils.runShellCommand("telecom set-default-dialer default");
        }
    }

    /**
     * Test scenario where an app calls {@link ContentResolver#query} with an invalid URI.
     *
     * The URI is invalid because it attempts to bypass voicemail permissions and grab the voicemail
     * log data without the proper voicemail permissions.
     *
     * Therefore, a Security Exception is thrown.
     */
    public void testInvalidQueryToCallLog() {
        Context context = getInstrumentation().getContext();
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELECOM)) {
            // This is tied to default-dialer, so don't test if the device doesn't have telecom.
            return;
        }

        try {
            // needed in order to populate call log database
            ShellUtils.runShellCommand("telecom set-default-dialer %s",
                    getInstrumentation().getContext().getPackageName());

            populateLogsWithDefaults();

            // drop voicemail permissions
            ShellUtils.runShellCommand("telecom set-default-dialer default");

            // query and get cursor  (expecting to hit Security Exception with call)
            Cursor cursor = mContentResolver
                    .query(INVALID_FILTER_URI, SELECTION, null, null);

            // the previous line should throw an exception
            fail(TEST_FAIL_DID_NOT_TRHOW_SE);
        } catch (SecurityException e) {
            // success...
            assertNotNull(e.toString());
        } finally {
            //cleanup
            ShellUtils.runShellCommand("telecom set-default-dialer %s",
                    getInstrumentation().getContext().getPackageName());
            deletePopulatedLogs();
            ShellUtils.runShellCommand("telecom set-default-dialer default");
        }
    }

    /**
     * Tests scenario where an app gives {@link ContentResolver} a file to open that is not in the
     * Call Log directory. Will throw a Security Exception if the file passed is found && a valid
     * ParcelFileDescriptor is returned.
     */
    public void testOpenFileOutsideOfScopeThrowsException() throws FileNotFoundException {
        if (UserManager.isHeadlessSystemUserMode()) {
            Log.i(TAG, "testOpenFileOutsideOfScopeThrowsException: " + HSUM_MGG);
            return;
        }
        try {
            Context context = getInstrumentation().getContext();
            ContentResolver resolver = context.getContentResolver();
            ParcelFileDescriptor fileDescriptor =
                    resolver.openFile(INVALID_CALL_LOG_URI, "w", null);
            // only fail the test if the file is found
            if (fileDescriptor != null) {
                // invalid file found instead of hitting SE.  manually fail the test.
                fail(TEST_FAIL_DID_NOT_TRHOW_SE);
            }
        } catch (SecurityException se) {
            assertNotNull(se.toString());
        }
    }

    /**
     * Tests scenario where an app gives {@link ContentResolver} {@link ContentValues} to delete
     * in a table that is not owned by the Call Log directory.
     */
    public void testDeleteFileOutsideOfScopeThrowsException() {
        try {
            Context context = getInstrumentation().getContext();
            ContentResolver resolver = context.getContentResolver();
            int numFilesDeleted =
                    resolver.delete(INVALID_CALL_LOG_URI, "w", null);
            if (numFilesDeleted > 0) {
                // resolver.delete should throw an SE instead of deleting file
                fail(TEST_FAIL_DID_NOT_TRHOW_SE);
            }
        } catch (SecurityException e) {
            assertNotNull(e.toString());
        }
    }

    /**
     * Tests scenario where an app gives {@link ContentResolver} {@link ContentValues} to insert
     * in a table that is not owned by the Call Log directory.
     */
    public void testInsertFileOutsideOfScopeThrowsException() {
        if (UserManager.isHeadlessSystemUserMode()) {
            Log.i(TAG, "testInsertFileOutsideOfScopeThrowsException: " + HSUM_MGG);
            return;
        }
        try {
            Context context = getInstrumentation().getContext();
            ContentResolver resolver = context.getContentResolver();
            Uri uri =
                    resolver.insert(INVALID_CALL_LOG_URI, new ContentValues());
            if (uri != null) {
                // resolver.insert(...) should throw an SE instead of inserting ContentValues
                fail(TEST_FAIL_DID_NOT_TRHOW_SE);
            }
        } catch (SecurityException e) {
            assertNotNull(e.toString());
        }
    }

    public void testGetLastOutgoingCall() {
        if (UserManager.isHeadlessSystemUserMode()) {
            Log.i(TAG, "testGetLastOutgoingCall: " + HSUM_MGG);
            return;
        }
        // Clear call log and ensure there are no outgoing calls
        Context context = getInstrumentation().getContext();
        ContentResolver resolver = context.getContentResolver();
        resolver.delete(CallLog.Calls.CONTENT_URI, null, null);

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return "";
                    }

                    @Override
                    public Object actual() {
                        return CallLog.Calls.getLastOutgoingCall(context);
                    }
                },
                CONTENT_RESOLVER_TIMEOUT_MS,
                "getLastOutgoingCall did not return empty after CallLog was cleared"
        );

        // Add a single call and verify it returns as last outgoing call
        ContentValues values = new ContentValues();
        values.put(CallLog.Calls.NUMBER, TEST_NUMBER);
        values.put(CallLog.Calls.TYPE, Integer.valueOf(CallLog.Calls.OUTGOING_TYPE));
        values.put(CallLog.Calls.DATE, Long.valueOf(0 /*start time*/));
        values.put(CallLog.Calls.DURATION, Long.valueOf(5 /*call duration*/));

        resolver.insert(CallLog.Calls.CONTENT_URI, values);

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return TEST_NUMBER;
                    }

                    @Override
                    public Object actual() {
                        return CallLog.Calls.getLastOutgoingCall(context);
                    }
                },
                CONTENT_RESOLVER_TIMEOUT_MS,
                "getLastOutgoingCall did not return " + TEST_NUMBER + " as expected"
        );
    }

    /**
     * Verify the {@link CallLog.Calls#IS_BUSINESS_CALL} and
     * {@link CallLog.Calls#ASSERTED_DISPLAY_NAME} values can be populated in the call logs and
     * fetched.
     */
    @RequiresFlagsEnabled(Flags.FLAG_BUSINESS_CALL_COMPOSER)
    public void testInsertingBusinessCallComposerValues() {
        if (!Flags.businessCallComposer()) {
            return;
        }
        final String[] businessCallSelection =
                new String[]{Calls.NUMBER, Calls.TYPE, Calls.IS_BUSINESS_CALL,
                        Calls.ASSERTED_DISPLAY_NAME};
        try {
            // needed in order to populate call log database
            ShellUtils.runShellCommand("telecom set-default-dialer %s",
                    getInstrumentation().getContext().getPackageName());

            // Add a business call to the call logs via the ContentResolver
            String businessName = "Google";
            Uri newlyCreatedCallLogRow = mContentResolver.insert(CallLog.Calls.CONTENT_URI,
                    createBusinessCallValues(true /*isBusinessCall*/, businessName));
            // fetch the newly inserted call log and assert the values
            Cursor cursor = mContentResolver.query(newlyCreatedCallLogRow, businessCallSelection,
                    Calls.NUMBER + " = " + TEST_NUMBER, null, Calls.DEFAULT_SORT_ORDER);
            assertNotNull(cursor);
            verifyBusinessCallValues(cursor, true /*isBusinessCall*/, businessName);

            // Add a non business call to the call logs
            newlyCreatedCallLogRow = mContentResolver.insert(CallLog.Calls.CONTENT_URI,
                    createBusinessCallValues(false /*isBusinessCall*/, "" /* businessName */));
            // fetch the newly inserted call log and assert the values
            cursor = mContentResolver.query(newlyCreatedCallLogRow, businessCallSelection,
                    Calls.NUMBER + " = " + TEST_NUMBER, null, Calls.DEFAULT_SORT_ORDER);
            assertNotNull(cursor);
            verifyBusinessCallValues(cursor, false /*isBusinessCall*/, "" /* businessName */);
        } finally {
            deleteCallLogRowsWithNumber(TEST_NUMBER);
        }
    }

    public void testLocationStorageAndRetrieval() {
        Context context = getInstrumentation().getContext();

        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            // This is tied to default-dialer, so don't test if the device doesn't have telephony.
            return;
        }

        UserHandle currentUser = UserHandle.of(
                ShellIdentityUtils.invokeStaticMethodWithShellPermissions(
                        () -> ActivityManager.getCurrentUser()));
        CallLog.AddCallParams.AddCallParametersBuilder builder =
                new CallLog.AddCallParams.AddCallParametersBuilder();
        builder.setAddForAllUsers(false);
        builder.setUserToBeInsertedTo(currentUser);
        // Some random spot in the North Atlantic
        double lat = 24.877323;
        double lon = -68.952545;
        builder.setLatitude(lat);
        builder.setLongitude(lon);
        ShellUtils.runShellCommand("telecom set-default-dialer %s",
                getInstrumentation().getContext().getPackageName());

        try {
            Uri uri;
            getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS,
                            Manifest.permission.READ_VOICEMAIL);
            try {
                uri = CallLog.Calls.addCall(context, builder.build());
            } finally {
                getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
            }
            assertNotNull(uri);

            Cursor cursor = context.getContentResolver().query(
                    uri, new String[]{CallLog.Calls.LOCATION}, null, null);
            assertEquals(1, cursor.getCount());
            cursor.moveToFirst();
            String locationUriString = cursor.getString(
                    cursor.getColumnIndex(CallLog.Calls.LOCATION));
            assertNotNull(locationUriString);

            Uri locationUri = Uri.parse(locationUriString);
            Cursor locationCursor = context.getContentResolver().query(locationUri,
                    new String[]{CallLog.Locations.LATITUDE, CallLog.Locations.LONGITUDE}, null,
                    null);
            assertEquals(1, locationCursor.getCount());
            locationCursor.moveToFirst();
            double storedLat = locationCursor.getDouble(
                    locationCursor.getColumnIndex(CallLog.Locations.LATITUDE));
            double storedLon = locationCursor.getDouble(
                    locationCursor.getColumnIndex(CallLog.Locations.LONGITUDE));
            assertEquals(lat, storedLat);
            assertEquals(lon, storedLon);
        } finally {
            ShellUtils.runShellCommand("telecom set-default-dialer default");
        }
    }

    public void testCallComposerImageStorage() throws Exception {
        Context context = getInstrumentation().getContext();
        byte[] expected = readResourceDrawable(context, R.drawable.testimage);

        CompletableFuture<Pair<Uri, CallLog.CallComposerLoggingException>> resultFuture =
                new CompletableFuture<>();
        Pair<Uri, CallLog.CallComposerLoggingException> result;
        try (InputStream inputStream =
                     context.getResources().openRawResource(R.drawable.testimage)) {
            CallLog.storeCallComposerPicture(
                    context.createContextAsUser(android.os.Process.myUserHandle(), 0),
                    inputStream,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<Uri, CallLog.CallComposerLoggingException>() {
                        @Override
                        public void onResult(@NonNull Uri result) {
                            resultFuture.complete(Pair.create(result, null));
                        }

                        @Override
                        public void onError(CallLog.CallComposerLoggingException error) {
                            resultFuture.complete(Pair.create(null, error));
                        }
                    });
           result = resultFuture.get(CONTENT_RESOLVER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        if (result.second != null) {
            fail("Got error " + result.second.getErrorCode() + " when storing image");
        }
        Uri imageLocation = result.first;

        try (ParcelFileDescriptor pfd =
                context.getContentResolver().openFileDescriptor(imageLocation, "r")) {
            byte[] remoteBytes = readBytes(new FileInputStream(pfd.getFileDescriptor()));
            assertArrayEquals(expected, remoteBytes);
        }
    }

    public void testCallComposerLoggingException() {
        CallLog.CallComposerLoggingException e =
                new CallLog.CallComposerLoggingException(
                        CallLog.CallComposerLoggingException.ERROR_STORAGE_FULL);
        assertEquals(CallLog.CallComposerLoggingException.ERROR_STORAGE_FULL,
                e.getErrorCode());
    }

    private byte[] readResourceDrawable(Context context, int id) throws Exception {
        InputStream inputStream = context.getResources().openRawResource(id);
        return readBytes(inputStream);
    }

    private byte[] readBytes(InputStream inputStream) throws Exception {
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int numRead;
        do {
            numRead = inputStream.read(buffer);
            if (numRead > 0) output.write(buffer, 0, numRead);
        } while (numRead > 0);
        return output.toByteArray();
    }

    private void waitUntilConditionIsTrueOrTimeout(Condition condition, long timeout,
            String description) {
        final long start = System.currentTimeMillis();
        while (!condition.expected().equals(condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            sleep(50);
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    protected interface Condition {
        Object expected();
        Object actual();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    private ContentValues getDefaultValues(int type, String number, int date, int duration) {
        ContentValues values = new ContentValues();
        values.put(Calls.TYPE, type);
        values.put(Calls.NUMBER, number);
        values.put(Calls.NUMBER_PRESENTATION, Calls.PRESENTATION_ALLOWED);
        values.put(Calls.DATE, date);
        values.put(Calls.DURATION, duration);
        values.put(Calls.NEW, 1);
        return values;
    }

    private ContentValues getDefaultCallValues() {
        return getDefaultValues(Calls.INCOMING_TYPE, TEST_NUMBER, TEST_DATE, TEST_DURATION);
    }

    private ContentValues getDefaultVoicemailValues() {
        return getDefaultValues(Calls.VOICEMAIL_TYPE, TEST_VOICEMAIL_NUMBER, TEST_VOCIEMAIL_DATE,
                TEST_VOICEMAIL_DURATION);
    }

    private void deletePopulatedLogs() {
        // delete TEST_NUMBER in the call logs
        mContentResolver.delete(CallLog.Calls.CONTENT_URI,
                Calls.NUMBER + "=" + TEST_NUMBER, null);
        // delete TEST_VOICEMAIL_NUMBER in the voicemail logs
        mContentResolver.delete(Calls.CONTENT_URI_WITH_VOICEMAIL,
                Calls.NUMBER + "=" + TEST_VOICEMAIL_NUMBER, null);
        // cleanup extra entry created in this test that does not have a Calls.NUMBER
        mContentResolver.delete(Calls.CONTENT_URI_WITH_VOICEMAIL,
                Calls.DATE + "=" + "0", null);
    }

    private void populateLogsWithDefaults() {
        // add call log entry
        mContentResolver.insert(Calls.CONTENT_URI, getDefaultCallValues());
        // add voicemail entry
        mContentResolver.insert(Calls.CONTENT_URI_WITH_VOICEMAIL, getDefaultVoicemailValues());
    }

    /**
     * This helper deletes all call logs that have the @param number passed in
     */
    private void deleteCallLogRowsWithNumber(String number) {
        mContentResolver.delete(Calls.CONTENT_URI, Calls.NUMBER + " = " + number, null);
    }

    private ContentValues createBusinessCallValues(boolean isBusiness, String displayName) {
        ContentValues values = new ContentValues();
        values.put(Calls.NUMBER, TEST_NUMBER);
        values.put(Calls.TYPE, Integer.valueOf(Calls.INCOMING_TYPE));
        values.put(Calls.ASSERTED_DISPLAY_NAME, displayName);
        values.put(Calls.IS_BUSINESS_CALL, (isBusiness ? 1 : 0));
        return values;
    }

    private void verifyBusinessCallValues(
            Cursor cursor,
            boolean isBusiness,
            String displayName) {
        // extract the data from the cursor and put the objects in a map
        cursor.moveToFirst();

        assertEquals((isBusiness ? 1 : 0), cursor.getInt(
                cursor.getColumnIndex(Calls.IS_BUSINESS_CALL)));

        assertEquals(displayName, cursor.getString(
                cursor.getColumnIndex(Calls.ASSERTED_DISPLAY_NAME)));
    }

    /**
     * Helper method for a test that wants to objectify the cursor data into LogEntry objects.
     * NOTE: The key for the map is the phone number, so you can only store one object per number.
     *
     * @return all the data in the cursor in a LogEntry map
     */
    public Map<String, LogEntry> collectCursorEntries(Cursor cursor) {
        Map<String, LogEntry> entries = new HashMap<>();
        // iterate through every row in the cursor
        while (cursor.moveToNext()) {
            LogEntry e = new LogEntry();
            // iterate through each column (should be the SELECTION given to query)
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                e.setValue(cursor.getColumnName(i), cursor.getString(i));
            }
            // don't add if bad number (should never happen)
            if (e.number != null && !e.number.isEmpty()) {
                entries.put(e.number, e);
            }
        }
        return entries;
    }
}
