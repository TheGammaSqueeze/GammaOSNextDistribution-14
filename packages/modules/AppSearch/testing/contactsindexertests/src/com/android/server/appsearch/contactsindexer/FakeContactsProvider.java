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

package com.android.server.appsearch.contactsindexer;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.ProviderInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DeletedContacts;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.server.appsearch.contactsindexer.appsearchtypes.Person;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Fake Contacts Provider that provides basic insert, delete, and query functionality.
 */
public class FakeContactsProvider extends ContentProvider {
    private static final String TAG = "ContactsIndexerFakeCont";
    public static final String AUTHORITY = "com.android.contacts";

    private static final String DATABASE_NAME = "contacts.db";
    private static final int DATABASE_VERSION = 1;
    private static final String CONTACTS_TABLE = "contacts";
    private static final String DELETED_CONTACTS_TABLE = "deleted_contacts";

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    private static final int CONTACTS = 1;
    private static final int CONTACTS_ID = 2;
    private static final int DELETED_CONTACTS = 3;
    private static final int DATA = 4;
    private static final int PHONE = 5;
    private static final int EMAIL = 6;
    private static final int POSTAL = 7;

    static {
        sUriMatcher.addURI(AUTHORITY, "contacts", CONTACTS);
        sUriMatcher.addURI(AUTHORITY, "contacts/#", CONTACTS_ID);
        sUriMatcher.addURI(AUTHORITY, "deleted_contacts", DELETED_CONTACTS);
        sUriMatcher.addURI(AUTHORITY, "data", DATA);
        sUriMatcher.addURI(AUTHORITY, "data/phones", PHONE);
        sUriMatcher.addURI(AUTHORITY, "data/emails", EMAIL);
        sUriMatcher.addURI(AUTHORITY, "data/postals", POSTAL);
    }

    private static final String CONTACTS_DATA_ORDER_BY =
            Data.CONTACT_ID
                    + ","
                    + Data.IS_SUPER_PRIMARY
                    + " DESC"
                    + ","
                    + Data.IS_PRIMARY
                    + " DESC"
                    + ","
                    + Data.RAW_CONTACT_ID;

    public static final int[] EMAIL_TYPES = {
            Email.TYPE_CUSTOM, Email.TYPE_HOME, Email.TYPE_WORK, Email.TYPE_OTHER,
            Email.TYPE_MOBILE,
    };
    public static final int[] PHONE_TYPES = {
            Phone.TYPE_CUSTOM,
            Phone.TYPE_HOME,
            Phone.TYPE_MOBILE,
            Phone.TYPE_WORK,
            Phone.TYPE_FAX_WORK,
            Phone.TYPE_FAX_HOME,
            Phone.TYPE_PAGER,
            Phone.TYPE_OTHER,
            Phone.TYPE_CALLBACK,
            Phone.TYPE_CAR,
            Phone.TYPE_COMPANY_MAIN,
            Phone.TYPE_OTHER_FAX,
            Phone.TYPE_RADIO,
            Phone.TYPE_TELEX,
            Phone.TYPE_TTY_TDD,
            Phone.TYPE_WORK_MOBILE,
            Phone.TYPE_WORK_PAGER,
            Phone.TYPE_ASSISTANT,
            Phone.TYPE_MMS
    };
    public static final int[] STRUCTURED_POSTAL_TYPES = {
            StructuredPostal.TYPE_CUSTOM,
            StructuredPostal.TYPE_HOME,
            StructuredPostal.TYPE_WORK,
            StructuredPostal.TYPE_OTHER
    };
    private static final int VERY_IMPORTANT_SCORE = 3;
    private static final int IMPORTANT_SCORE = 2;
    private static final int ORDINARY_SCORE = 1;

    private final Resources mResources;

    private SQLiteOpenHelper mOpenHelper = null;
    private int mNumContacts;
    private long mMostRecentContactLastUpdatedTimestampMillis;
    private long mMostRecentDeletedContactTimestampMillis;

    // Data Query delay in millis added for testing
    private long mDataQueryDelayMs = 0;

    // Only odd contactIds should have additional data.
    private static boolean shouldhaveAdditionalData(long contactId) {
        return (contactId & 1) > 0;
    }

    // Use id's second least significant bit to make a fake isSuperPrimary field.
    private static int calculateIsSuperPrimary(long contactId) {
        return ((contactId & 1) > 0) ? 1 : 0;
    }

    // Use id's second least significant bit to make a fake isPrimary field.
    private static int calculateIsPrimary(long contactId) {
        return (((contactId >> 1) & 1) > 0) ? 1 : 0;
    }

    // Add fake email information into the ContentValues.
    private static void addEmail(long contactId, ContentValues values) {
        values.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        values.put(Data._ID, contactId);
        values.put(Data.IS_PRIMARY, calculateIsPrimary(contactId));
        values.put(Data.IS_SUPER_PRIMARY, calculateIsSuperPrimary(contactId));
        values.put(Email.ADDRESS, String.format("emailAddress%d@google.com", contactId));
        values.put(Email.TYPE, EMAIL_TYPES[(int) (contactId % EMAIL_TYPES.length)]);
        values.put(Email.LABEL, String.format("emailLabel%d", contactId));
    }

    // Add fake nickname into the ContentValues
    private static void addNickname(long contactId, ContentValues values) {
        values.put(Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE);
        values.put(Nickname.NAME, String.format("nicknameName%d", contactId));
    }

    // Add fake phone information into the ContentValues.
    private static void addPhone(long contactId, ContentValues values) {
        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values.put(Data._ID, contactId);
        values.put(Data.IS_PRIMARY, calculateIsPrimary(contactId));
        values.put(Data.IS_SUPER_PRIMARY, calculateIsSuperPrimary(contactId));
        values.put(Phone.NUMBER, String.format("phoneNumber%d", contactId));
        values.put(Phone.TYPE, PHONE_TYPES[(int) (contactId % PHONE_TYPES.length)]);
        values.put(Phone.LABEL, String.format("phoneLabel%d", contactId));
    }

    // Add fake postal information into ContentValues.
    private static void addStructuredPostal(long contactId, ContentValues values) {
        values.put(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE);
        values.put(Data._ID, contactId);
        values.put(Data.IS_PRIMARY, calculateIsPrimary(contactId));
        values.put(Data.IS_SUPER_PRIMARY, calculateIsSuperPrimary(contactId));
        values.put(
                StructuredPostal.FORMATTED_ADDRESS,
                String.format("structuredPostalFormattedAddress%d", contactId));
        values.put(
                StructuredPostal.TYPE,
                STRUCTURED_POSTAL_TYPES[(int) (contactId % STRUCTURED_POSTAL_TYPES.length)]);
        values.put(StructuredPostal.LABEL, String.format("structuredPostalLabel%d", contactId));
    }

    // Add fake raw contact information for the data
    private void addRawContactInfo(long rawContactsId, long nameRawContactsId,
            ContentValues values) {
        values.put(Data.RAW_CONTACT_ID, String.valueOf(rawContactsId));
        values.put(Data.NAME_RAW_CONTACT_ID, String.valueOf(nameRawContactsId));
    }

    // Add fake given and family name into ContentValues.
    private void addStructuredName(long contactId, ContentValues values) {
        values.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        values.put(StructuredName.GIVEN_NAME,
                String.format("structuredNameGivenName%d", contactId));
        values.put(
                StructuredName.MIDDLE_NAME,
                String.format("structuredNameMiddleName%d", contactId));
        values.put(
                StructuredName.FAMILY_NAME,
                String.format("structuredNameFamilyName%d", contactId));
    }

    // Add fake contact's basic information into ContentValues.
    private void addContactBasic(long i, ContentValues values) {
        values.put(Data.CONTACT_ID, i);
        values.put(Data.LOOKUP_KEY, String.format("lookupUri%d", i));
        values.put(Data.PHOTO_THUMBNAIL_URI, String.format("http://photoThumbNailUri%d.com", i));
        values.put(Data.DISPLAY_NAME_PRIMARY, String.format("displayName%d", i));
        values.put(Data.PHONETIC_NAME, String.format("phoneticName%d", i));
        values.put(Data.RAW_CONTACT_ID, i);
        // Set last updated timestamp as i so we could handle selection easily.
        values.put(Data.CONTACT_LAST_UPDATED_TIMESTAMP, i);
        values.put(Data.STARRED, i & 1);
    }

    private void addRowToCursorFromContentValues(
            ContentValues values, MatrixCursor cursor, String[] projection) {
        MatrixCursor.RowBuilder builder = cursor.newRow();
        for (int i = 0; i < projection.length; ++i) {
            builder.add(values.getAsString(projection[i]));
        }
    }

    private void addEmailToBuilder(PersonBuilderHelper builderHelper, long contactId) {
        int type = EMAIL_TYPES[(int) (contactId % EMAIL_TYPES.length)];
        builderHelper.addEmailToPerson(
                Email.getTypeLabel(mResources, type,
                        String.format("emailLabel%d", contactId)).toString(),
                String.format("emailAddress%d@google.com", contactId));
    }

    private void addNicknameToBuilder(PersonBuilderHelper builderHelper, long contactId) {
        builderHelper.getPersonBuilder().addAdditionalName(Person.TYPE_NICKNAME,
                String.format("nicknameName%d", contactId));
    }

    private void addPhoneToBuilder(PersonBuilderHelper builderHelper, long contactId) {
        int type = PHONE_TYPES[(int) (contactId % PHONE_TYPES.length)];
        builderHelper.addPhoneToPerson(
                Phone.getTypeLabel(mResources, type,
                        String.format("phoneLabel%d", contactId)).toString(),
                String.format("phoneNumber%d", contactId));
    }

    private void addStructuredPostalToBuilder(PersonBuilderHelper builderHelper,
            long contactId) {
        int type = STRUCTURED_POSTAL_TYPES[(int) (contactId % STRUCTURED_POSTAL_TYPES.length)];
        builderHelper.addAddressToPerson(
                StructuredPostal.getTypeLabel(
                        mResources, type, String.format("structuredPostalLabel%d", contactId))
                        .toString(),
                String.format("structuredPostalFormattedAddress%d", contactId));
    }

    private static void addStructuredNameToBuilder(PersonBuilderHelper builderHelper,
            long contactId) {
        if (shouldhaveAdditionalData(contactId)) {
            builderHelper.getPersonBuilder()
                    .setGivenName(String.format("structuredNameGivenName%d", contactId));
            builderHelper.getPersonBuilder()
                    .setMiddleName(String.format("structuredNameMiddleName%d", contactId));
            builderHelper.getPersonBuilder()
                    .setFamilyName(String.format("structuredNameFamilyName%d", contactId));
        }
    }

    public FakeContactsProvider() {
        this(ApplicationProvider.getApplicationContext().getResources());
    }

    public void setDataQueryDelayMs(long dataQueryDelayMs) {
        mDataQueryDelayMs = dataQueryDelayMs;
    }

    FakeContactsProvider(Resources resources) {
        mResources = resources;
    }

    // Parse the selection String which may be "IN (idlist)" or null for all ids.
    private List<Integer> parseList(String selection) {
        int left = -1;
        int right = -1;
        List<Integer> selectionIds = new ArrayList<>();
        if ((selection != null) && (selection.contains("IN"))) {
            left = selection.indexOf('(');
            right = selection.indexOf(')');
        }
        if ((left >= 0) && (right > left)) {
            // Read ids in the list. Ignore exceptions. Note that the list may be empty.
            String[] ids = selection.substring(left + 1, right).split(",");
            for (String i : ids) {
                try {
                    Integer id = Integer.valueOf(i);
                    if (id < mNumContacts) {
                        selectionIds.add(id);
                    }
                } catch (NumberFormatException e) {
                    // Do nothing.
                }
            }
        } else { // all ids
            for (int i = 1; i <= mNumContacts; ++i) {
                selectionIds.add(i);
            }
        }
        return selectionIds;
    }

    // Query all details for given contact ids.  Selection will be a list of ids or null. Since we
    // Save the phone, email, postal etc. information separately, a single contact id may have
    // multiply records. So the orderBy should group the information of the same contact id
    // together. We only process CONTACTS_DATA_ORDER_BY.
    protected Cursor manageDataQuery(
            String[] projection, String selection, String[] selectionArgs, String orderBy) {
        if (mDataQueryDelayMs > 0) {
             try {
                Thread.sleep(mDataQueryDelayMs);
            } catch (InterruptedException e) {
                Log.d(TAG, "Got exception while applying data query delay.", e);
            }
        }

        MatrixCursor cursor = null;
        // Details in id list.
        if (CONTACTS_DATA_ORDER_BY.equals(orderBy) && (projection != null)) {
            cursor = new MatrixCursor(projection);
            List<Integer> selectionIds = parseList(selection);
            Collections.sort(selectionIds);
            Log.d(TAG, Arrays.toString(selectionIds.toArray()));
            ContentValues values = new ContentValues();
            for (long i : selectionIds) {
                if ((i & 1) != 0) {
                    // Single contact.
                    values.clear();
                    addContactBasic(i, values);
                    addRowToCursorFromContentValues(values, cursor, projection);
                    // Same contact with email.
                    values.clear();
                    addContactBasic(i, values);
                    addEmail(i, values);
                    addRowToCursorFromContentValues(values, cursor, projection);
                    // Same contact with email.
                    values.clear();
                    addContactBasic(i, values);
                    addEmail(i + 1, values);
                    addRowToCursorFromContentValues(values, cursor, projection);
                    // Same contact with Nickname.
                    values.clear();
                    addContactBasic(i, values);
                    addNickname(i, values);
                    addRowToCursorFromContentValues(values, cursor, projection);
                    // Same contact with Nickname.
                    values.clear();
                    addContactBasic(i, values);
                    addNickname(i + 1, values);
                    addRowToCursorFromContentValues(values, cursor, projection);
                    // Same contact with Phone.
                    values.clear();
                    addContactBasic(i, values);
                    addPhone(i, values);
                    addRowToCursorFromContentValues(values, cursor, projection);
                    // Same contact with Phone.
                    values.clear();
                    addContactBasic(i, values);
                    addPhone(i + 1, values);
                    addRowToCursorFromContentValues(values, cursor, projection);
                    // Same contact with StructuredPostal.
                    values.clear();
                    addContactBasic(i, values);
                    addStructuredPostal(i, values);
                    addRowToCursorFromContentValues(values, cursor, projection);
                    // Same contact with StructuredPostal.
                    values.clear();
                    addContactBasic(i, values);
                    addStructuredPostal(i + 1, values);
                    addRowToCursorFromContentValues(values, cursor, projection);
                    // Same contact with StructuredName.
                    values.clear();
                    addContactBasic(i, values);
                    addRawContactInfo(/*rawContactsId=*/ i, /*nameRawContactsId=*/ i, values);
                    addStructuredName(i, values);
                    addRowToCursorFromContentValues(values, cursor, projection);
                    // Same contact with StructuredName
                    values.clear();
                    addContactBasic(i, values);
                    addRawContactInfo(/*rawContactsId=*/ i + 1, /*nameRawContactsId=*/ i, values);
                    // given and family name will be picked up since rawContactsId is same as
                    // nameRawContactsId
                    // for the current row.
                    addStructuredName(i + 1, values);
                    addRowToCursorFromContentValues(values, cursor, projection);
                } else {
                    // Single contact.
                    values.clear();
                    addContactBasic(i, values);
                    addRowToCursorFromContentValues(values, cursor, projection);
                }
            }
        }
        return cursor;
    }

    @Override
    public Cursor query(
            Uri uri, String[] projection, String selection, String[] selectionArgs,
            String orderBy) {
        Log.i(TAG, "uri = " + uri);
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setStrict(true);

        int match = sUriMatcher.match(uri);
        switch (match) {
            case CONTACTS:
                qb.setTables(CONTACTS_TABLE);
                break;

            case DELETED_CONTACTS:
                qb.setTables(DELETED_CONTACTS_TABLE);
                break;

            case DATA:
                return manageDataQuery(projection, selection, selectionArgs, orderBy);

            default:
                throw new UnsupportedOperationException();
        }

        String limit = uri.getQueryParameter(ContactsContract.LIMIT_PARAM_KEY);
        Cursor cursor = qb.query(db, projection, selection, selectionArgs, /*groupBy=*/ null,
                /*having=*/null, orderBy, limit);
        if (cursor == null) {
            Log.w(TAG, "query failed");
            return null;
        }
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    long getMostRecentContactUpdateTimestampMillis() {
        return mMostRecentContactLastUpdatedTimestampMillis;
    }

    long getMostRecentDeletedContactTimestampMillis() {
        return mMostRecentDeletedContactTimestampMillis;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(ApplicationProvider.getApplicationContext());
        return true;
    }

    @Override
    public void shutdown() {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.execSQL("delete from " + CONTACTS_TABLE);
        db.execSQL("delete from " + DELETED_CONTACTS_TABLE);
        db.close();
        mOpenHelper.close();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        if (sUriMatcher.match(uri) != CONTACTS_ID) {
            throw new IllegalArgumentException("delete: unknown URI " + uri);
        }
        // Insert tombstone into deleted_contacts table
        mMostRecentDeletedContactTimestampMillis = System.currentTimeMillis();
        long contactId = ContentUris.parseId(uri);
        ContentValues values = new ContentValues();
        values.put(DeletedContacts.CONTACT_ID, contactId);
        values.put(DeletedContacts.CONTACT_DELETED_TIMESTAMP,
                mMostRecentDeletedContactTimestampMillis);
        db.insertWithOnConflict(DELETED_CONTACTS_TABLE, /*nullColumnHack=*/ null,
                values, SQLiteDatabase.CONFLICT_REPLACE);

        // Delete contact from contacts table
        return db.delete(CONTACTS_TABLE, Contacts._ID + " = ?", new String[] {contactId + ""});
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        if (sUriMatcher.match(uri) != CONTACTS) {
            throw new IllegalArgumentException("insert: unknown URI " + uri);
        }

        mMostRecentContactLastUpdatedTimestampMillis = System.currentTimeMillis();
        values.put(Contacts._ID, mNumContacts++);
        values.put(Contacts.CONTACT_LAST_UPDATED_TIMESTAMP,
                mMostRecentContactLastUpdatedTimestampMillis);

        long rowId = db.insert(CONTACTS_TABLE, /*nullColumnHack=*/ null, values);

        getContext().getContentResolver().notifyChange(uri, /*observer=*/ null);
        return ContentUris.withAppendedId(Contacts.CONTENT_URI, rowId);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("update not supported");
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException("update not supported");
    }

    private static final class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, /*factory=*/ null, DATABASE_VERSION);
        }
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE contacts (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "contact_last_updated_timestamp INTEGER" +
                    ");");
            db.execSQL("CREATE TABLE deleted_contacts (" +
                    "contact_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "contact_deleted_timestamp INTEGER" +
                    ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // nothing to do
        }
    }
}
