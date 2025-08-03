/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telephonyprovider.cts;

import static android.telephony.cts.util.DefaultSmsAppHelper.assumeTelephony;
import static android.telephony.cts.util.DefaultSmsAppHelper.assumeMessaging;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.cts.util.DefaultSmsAppHelper;

import com.android.compatibility.common.util.ApiTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.OutputStream;

import javax.annotation.Nullable;

public class MmsPartTest {

    private static final String MMS_SUBJECT_ONE = "MMS Subject CTS One";
    private static final String MMS_SUBJECT_PART = "Part cleanup test";
    private static final String MMS_BODY = "MMS body CTS";
    private static final String MMS_BODY_UPDATE = "MMS body CTS Update";
    private static final String TEXT_PLAIN = "text/plain";
    public static final String IMAGE_JPEG = "image/jpeg";
    private static final String SRC_NAME = String.format("text.%06d.txt", 0);
    static final String PART_FILE_COUNT = "part_file_count";
    static final String PART_TABLE_ENTRY_COUNT = "part_table_entry_count";
    static final String DELETED_COUNT = "deleted_count";
    static final String METHOD_GARBAGE_COLLECT = "garbage_collect";

    /**
     * Parts must be inserted in relation to a message, this message ID is used for inserting a part
     * when the message ID is not important in relation to the current test.
     */
    private static final String TEST_MESSAGE_ID = "100";
    private ContentResolver mContentResolver;

    @BeforeClass
    public static void ensureDefaultSmsApp() {
        DefaultSmsAppHelper.ensureDefaultSmsApp();
    }

    @AfterClass
    public static void cleanup() {
        ContentResolver contentResolver = getInstrumentation().getContext().getContentResolver();
        contentResolver.delete(Telephony.Mms.Part.CONTENT_URI, null, null);
        contentResolver
                    .call(Telephony.MmsSms.CONTENT_URI, "garbage_collect", "delete", null);
    }

    @Before
    public void setUp() {
        cleanup();
    }

    @Before
    public void setupTestEnvironment() {
        assumeTelephony();
        assumeMessaging();
        cleanup();
        mContentResolver = getInstrumentation().getContext().getContentResolver();
    }

    @Test
    public void testMmsPartInsert_cannotInsertPartWithDataColumn() {
        ContentValues values = new ContentValues();
        values.put(Telephony.Mms.Part._DATA, "/dev/urandom");
        values.put(Telephony.Mms.Part.NAME, "testMmsPartInsert_cannotInsertPartWithDataColumn");

        Uri uri = insertTestMmsPartWithValues(values);
        assertThat(uri).isNull();
    }

    @Test
    public void testMmsPartInsert_canInsertPartWithoutDataColumn() throws Exception {
        String name = "testMmsInsert_canInsertPartWithoutDataColumn";

        Uri mmsUri = insertIntoMmsTable(MMS_SUBJECT_ONE);
        assertThat(mmsUri).isNotNull();
        final long mmsId = ContentUris.parseId(mmsUri);

        //Creating part uri using mmsId.
        final Uri partUri = Telephony.Mms.Part.getPartUriForMessage(String.valueOf(mmsId));
        Uri insertPartUri = insertIntoMmsPartTable(mmsUri, partUri, mmsId, MMS_BODY,
                name, TEXT_PLAIN);
        assertThatMmsPartInsertSucceeded(insertPartUri, name, MMS_BODY);
    }

    @Test
    public void testMmsPart_deletedPartIdsAreNotReused() throws Exception {
        long id1 = insertAndVerifyMmsPartReturningId("testMmsPart_deletedPartIdsAreNotReused_1");

        deletePartById(id1);

        long id2 = insertAndVerifyMmsPartReturningId("testMmsPart_deletedPartIdsAreNotReused_2");

        assertThat(id2).isGreaterThan(id1);
    }

    @Test
    public void testMmsPart_garbageCollectWithOnlyOneValidPart() throws Exception {
        long id1 = insertAndVerifyMmsPart(0);
        assertThat(id1).isGreaterThan(0);

        Bundle result =
                mContentResolver
                        .call(Telephony.MmsSms.CONTENT_URI, METHOD_GARBAGE_COLLECT,
                                null, null);

        assertThat(result.getInt(PART_FILE_COUNT)).isEqualTo(1);
        assertThat(result.getInt(PART_TABLE_ENTRY_COUNT)).isEqualTo(1);
        assertThat(result.getInt(DELETED_COUNT)).isEqualTo(0);
    }

    @Test
    public void testMmsPart_verifyLargeNumberOfParts() throws Exception {
        int partCount = 500;
        for (int i = 0; i < partCount; i++) {
            long id1 = insertAndVerifyMmsPart(i);
            assertThat(id1).isGreaterThan(0);
        }

        Bundle result =
                mContentResolver
                        .call(Telephony.MmsSms.CONTENT_URI, METHOD_GARBAGE_COLLECT,
                                null, null);

        assertThat(result.getInt(PART_FILE_COUNT)).isEqualTo(partCount);
        assertThat(result.getInt(PART_TABLE_ENTRY_COUNT)).isEqualTo(partCount);
        assertThat(result.getInt(DELETED_COUNT)).isEqualTo(0);
    }

    @Test
    public void testMmsPart_garbageCollectWithOnlyValidParts() throws Exception {
        int partCount = 10;
        for (int i = 0; i < partCount; i++) {
            long id1 = insertAndVerifyMmsPart(i);
            assertThat(id1).isGreaterThan(0);
        }

        Bundle result =
                mContentResolver
                        .call(Telephony.MmsSms.CONTENT_URI, METHOD_GARBAGE_COLLECT,
                                "delete", null);

        assertThat(result.getInt(PART_FILE_COUNT)).isEqualTo(partCount);
        assertThat(result.getInt(PART_TABLE_ENTRY_COUNT)).isEqualTo(partCount);
        assertThat(result.getInt(DELETED_COUNT)).isEqualTo(0);
    }

    private static Uri getMessagePartUri(long msgId) {
        return Uri.parse("content://mms/" + msgId + "/part");
    }

    @Test
    public void testMmsPartUpdate() throws Exception {
        //Inserting data to MMS table.
        Uri mmsUri = insertIntoMmsTable(MMS_SUBJECT_ONE);
        assertThat(mmsUri).isNotNull();
        final long mmsId = ContentUris.parseId(mmsUri);

        //Creating part uri using mmsId.
        final Uri partUri = Telephony.Mms.Part.getPartUriForMessage(String.valueOf(mmsId));

        //Inserting data to MmsPart table with mapping with mms uri.
        Uri insertPartUri = insertIntoMmsPartTable(mmsUri, partUri, mmsId, MMS_BODY, SRC_NAME,
                TEXT_PLAIN);
        assertThatMmsPartInsertSucceeded(insertPartUri, SRC_NAME, MMS_BODY);

        final ContentValues updateValues = new ContentValues();
        updateValues.put(Telephony.Mms.Part.TEXT, MMS_BODY_UPDATE);

        // Updating part table.
        int cursorUpdate = mContentResolver.update(partUri, updateValues, null, null);
        assertThat(cursorUpdate).isEqualTo(1);
        assertThatMmsPartInsertSucceeded(insertPartUri, SRC_NAME, MMS_BODY_UPDATE);

    }

    /**
     *  Verifies uri path outside the directory of mms parts  is not allowed.
     */
    @Test
    @ApiTest(apis = "com.android.providers.telephony.MmsProvider#update")
    public void testMmsPartUpdate_invalidUri() {
        ContentValues cv = new ContentValues();
        Uri uri = Uri.parse("content://mms/resetFilePerm/..%2F..%2F..%2F..%2F..%2F..%2F..%2F..%2F.."
                + "%2F..%2F..%2F..%2F..%2Fdata%2Fuser_de%2F0%2Fcom.android.providers.telephony"
                + "%2Fdatabases");
        int cursorUpdate = mContentResolver.update(uri, cv, null, null);
        assertThat(cursorUpdate).isEqualTo(0);
    }

    @Test
    public void testMmsPartDelete_canDeleteById() throws Exception {
        Uri mmsUri = insertIntoMmsTable(MMS_SUBJECT_ONE);
        assertThat(mmsUri).isNotNull();
        final long mmsId = ContentUris.parseId(mmsUri);
        final Uri partUri = Telephony.Mms.Part.getPartUriForMessage(String.valueOf(mmsId));

        Uri insertPartUri = insertIntoMmsPartTable(mmsUri, partUri, mmsId, MMS_BODY, SRC_NAME,
                TEXT_PLAIN);
        assertThat(insertPartUri).isNotNull();

        int deletedRows = mContentResolver.delete(partUri, null, null);

        assertThat(deletedRows).isEqualTo(1);

    }

    private long insertAndVerifyMmsPartReturningId(String name) throws Exception {
        Uri mmsUri = insertIntoMmsTable(MMS_SUBJECT_ONE);
        assertThat(mmsUri).isNotNull();
        final long mmsId = ContentUris.parseId(mmsUri);

        //Creating part uri using mmsId.
        final Uri partUri = Telephony.Mms.Part.getPartUriForMessage(String.valueOf(mmsId));
        Uri insertPartUri = insertIntoMmsPartTable(mmsUri, partUri, mmsId, MMS_BODY, name,
                TEXT_PLAIN);

        assertThatMmsPartInsertSucceeded(insertPartUri, name, MMS_BODY);
        return Long.parseLong(insertPartUri.getLastPathSegment());
    }

    private long insertAndVerifyMmsPart(int fileCounter) throws Exception {
        Uri mmsUri = insertIntoMmsTableNotText(MMS_SUBJECT_PART);
        assertThat(mmsUri).isNotNull();
        final long mmsId = ContentUris.parseId(mmsUri);

        // Creating part uri using mmsId.
        String filename = "file" + fileCounter;
        final Uri partUri = getPartUriForMessage(String.valueOf(mmsId));
        Uri insertPartUri = insertIntoMmsPartTable(partUri, mmsId, MMS_BODY, filename, IMAGE_JPEG);

        assertThatMmsPartInsertWithContentTypeSucceeded(insertPartUri, filename, IMAGE_JPEG);
        return Long.parseLong(insertPartUri.getLastPathSegment());
    }

    private static Uri getPartUriForMessage(String messageId) {
        return Telephony.Mms.CONTENT_URI
                .buildUpon()
                .appendPath(String.valueOf(messageId))
                .appendPath("part")
                .build();
    }

    private long insertAndVerifyMmsPart(String name) throws Exception {
        Uri mmsUri = insertIntoMmsTableNotText(MMS_SUBJECT_PART);
        assertThat(mmsUri).isNotNull();
        final long mmsId = ContentUris.parseId(mmsUri);

        //Creating part uri using mmsId.
        final Uri partUri = Telephony.Mms.Part.getPartUriForMessage(String.valueOf(mmsId));
        Uri insertPartUri = insertIntoMmsPartTable(mmsUri, partUri, mmsId, MMS_BODY, name,
                IMAGE_JPEG);

        assertThatMmsPartInsertWithContentTypeSucceeded(insertPartUri, name, IMAGE_JPEG);
        return Long.parseLong(insertPartUri.getLastPathSegment());
    }

    private void deletePartById(long partId) {
        Uri uri = Uri.withAppendedPath(Telephony.Mms.Part.CONTENT_URI, Long.toString(partId));
        int deletedRows = mContentResolver.delete(uri, null, null);
        assertThat(deletedRows).isEqualTo(1);
    }

    private Uri insertTestMmsPartWithValues(ContentValues values) {
        Uri insertUri = Telephony.Mms.Part.getPartUriForMessage(TEST_MESSAGE_ID);

        Uri uri = mContentResolver.insert(insertUri, values);
        return uri;
    }

    private void assertThatMmsPartInsertSucceeded(@Nullable Uri uriReturnedFromInsert,
            String nameOfAttemptedInsert, String textBody) {
        assertThat(uriReturnedFromInsert).isNotNull();

        Cursor cursor = mContentResolver.query(uriReturnedFromInsert, null, null, null);
        assertThat(cursor.getCount()).isEqualTo(1);

        cursor.moveToNext();
        String actualName = cursor.getString(cursor.getColumnIndex(Telephony.Mms.Part.NAME));
        assertThat(actualName).isEqualTo(nameOfAttemptedInsert);
        assertThat(cursor.getString(cursor.getColumnIndex(Telephony.Mms.Part.TEXT))).isEqualTo(
                textBody);
    }

    private void assertThatMmsPartInsertWithContentTypeSucceeded(
            @Nullable Uri uriReturnedFromInsert, String nameOfAttemptedInsert, String contentType) {
        assertThat(uriReturnedFromInsert).isNotNull();

        Cursor cursor = mContentResolver.query(uriReturnedFromInsert, null, null, null);
        assertThat(cursor.getCount()).isEqualTo(1);

        cursor.moveToNext();
        String actualName = cursor.getString(cursor.getColumnIndex(Telephony.Mms.Part.NAME));
        assertThat(actualName).isEqualTo(nameOfAttemptedInsert);
        assertThat(cursor.getString(cursor.getColumnIndex(Telephony.Mms.Part.CONTENT_TYPE)))
                .isEqualTo(contentType);
    }

    private Uri insertIntoMmsTable(String subject) {
        final ContentValues mmsValues = new ContentValues();
        mmsValues.put(Telephony.Mms.TEXT_ONLY, 1);
        mmsValues.put(Telephony.Mms.MESSAGE_TYPE, 128);
        mmsValues.put(Telephony.Mms.SUBJECT, subject);
        final Uri mmsUri = mContentResolver.insert(Telephony.Mms.CONTENT_URI, mmsValues);
        return mmsUri;
    }

    private Uri insertIntoMmsTableNotText(String subject) {
        final ContentValues mmsValues = new ContentValues();
        mmsValues.put(Telephony.Mms.TEXT_ONLY, 0);
        mmsValues.put(Telephony.Mms.MESSAGE_TYPE, 128);
        mmsValues.put(Telephony.Mms.SUBJECT, subject);
        final Uri mmsUri = mContentResolver.insert(Telephony.Mms.CONTENT_URI, mmsValues);
        return mmsUri;
    }

    private Uri insertIntoMmsPartTable(
            Uri partUri, long mmsId, String body, String name, String contentType)
            throws Exception {
        // Insert body part.
        final ContentValues values = new ContentValues();
        values.put(Telephony.Mms.Part.MSG_ID, mmsId);
        values.put(Telephony.Mms.Part.NAME, name);
        values.put(Telephony.Mms.Part.SEQ, 0);
        values.put(Telephony.Mms.Part.CONTENT_TYPE, contentType);
        values.put(Telephony.Mms.Part.CONTENT_ID, "<" + name + ">");
        values.put(Telephony.Mms.Part.CONTENT_LOCATION, name);
        values.put(Telephony.Mms.Part.CHARSET, 111);
        values.put(Telephony.Mms.Part.TEXT, body);
        Uri insertPartUri = mContentResolver.insert(partUri, values);

        if (!TEXT_PLAIN.equals(contentType)) {
            writePartFile(insertPartUri);
        }
        return insertPartUri;
    }

    private Uri insertIntoMmsPartTable(Uri mmsUri, Uri partUri, long mmsId, String body,
            String name, String contentType) throws Exception {
        // Insert body part.
        final ContentValues values = new ContentValues();
        values.put(Telephony.Mms.Part.MSG_ID, mmsId);
        values.put(Telephony.Mms.Part.NAME, name);
        values.put(Telephony.Mms.Part.SEQ, 0);
        values.put(Telephony.Mms.Part.CONTENT_TYPE, contentType);
        values.put(Telephony.Mms.Part.CONTENT_ID, "<" + SRC_NAME + ">");
        values.put(Telephony.Mms.Part.CONTENT_LOCATION, SRC_NAME);
        values.put(Telephony.Mms.Part.CHARSET, 111);
        values.put(Telephony.Mms.Part.TEXT, body);
        Uri insertPartUri = mContentResolver.insert(partUri, values);

        if (!TEXT_PLAIN.equals(contentType)) {
            writePartFile(insertPartUri);
        }
        return insertPartUri;
    }

    private void writePartFile(Uri uri) throws Exception {
        // uri can look like:
        // content://mms/part/98
        try (OutputStream os = mContentResolver.openOutputStream(uri)) {
            if (os == null) {
                throw new Exception("Failed to create output stream on " + uri);
            }
            String test = "This is  a test";
            os.write(test.getBytes(UTF_8), 0, test.length());
        }
    }
}
