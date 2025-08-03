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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Data;

import androidx.test.core.app.ApplicationProvider;

import com.android.server.appsearch.contactsindexer.appsearchtypes.ContactPoint;
import com.android.server.appsearch.contactsindexer.appsearchtypes.Person;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ContactDataHandlerTest {
    private static final String TEST_NAMESPACE = "TESTNAMESPACE";
    private static final String TEST_ID = "TESTID";

    private ContactDataHandler mContactDataHandler;
    private Context mContext;
    private Resources mResources;

    // Make a MatrixCursor based on ContentValues and return it.
    private static Cursor makeCursorFromContentValues(ContentValues values) {
        MatrixCursor cursor = new MatrixCursor(values.keySet().toArray(new String[0]));
        MatrixCursor.RowBuilder builder = cursor.newRow();
        for (String key : values.keySet()) {
            builder.add(key, values.get(key));
        }
        return cursor;
    }

    // Read from a single-line cursor and populate the data into the builderHelper.
    private void convertRowToPerson(Cursor cursor, PersonBuilderHelper builderHelper) {
        if (cursor != null) {
            try {
                assertThat(cursor.getCount()).isEqualTo(1);
                assertThat(cursor.moveToFirst()).isTrue();
                mContactDataHandler.convertCursorToPerson(cursor, builderHelper);
            } finally {
                cursor.close();
            }
        }
    }

    private void setLocaleString(String language, String country) {
        Locale locale = new Locale(language, country);
        // here we update locale for date formatters
        Locale.setDefault(locale);
        // here we update locale for app resources
        Configuration config = mResources.getConfiguration();
        config.setLocale(locale);
        mContext = mContext.createConfigurationContext(config);
    }

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        mResources = mContext.getResources();
        setLocaleString("en", "US");

        mContactDataHandler =
                new ContactDataHandler(mResources);
    }

    @Test
    public void testConvertCurrentRowToPersonWhenCursorNotSet_expectException() {
        PersonBuilderHelper builderHelper = new PersonBuilderHelper("id",
                new Person.Builder("namespace", "id", "name"));
        assertThrows(NullPointerException.class, () ->
                mContactDataHandler.convertCursorToPerson(/*cursor=*/ null, builderHelper));
    }

    @Test
    public void testConvertCurrentRowToPerson_labelCustom_typeCustom() {
        int type = 0; // Custom
        String name = "name";
        String address = "emailAddress@google.com";
        String label = "CustomLabel";
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Email.ADDRESS, address);
        values.put(CommonDataKinds.Email.TYPE, type);
        values.put(CommonDataKinds.Email.LABEL, label);
        Cursor cursor = makeCursorFromContentValues(values);
        long creationTimestampMillis = System.currentTimeMillis();
        Person personExpected =
                new PersonBuilderHelper(TEST_ID,
                        new Person.Builder(TEST_NAMESPACE, TEST_ID, name)
                )
                        .setCreationTimestampMillis(creationTimestampMillis)
                        .addEmailToPerson(label, address)
                        .buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)).setCreationTimestampMillis(
                creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        // Since the type is 1(Homes), we won't use user provided label. So it is fine to be null.
        ContactPoint[] contactPoints = personTested.getContactPoints();
        assertThat(contactPoints.length).isEqualTo(1);
        assertThat(contactPoints[0].getLabel()).isEqualTo(label);
        assertThat(contactPoints[0].getEmails()).asList().containsExactly(address);
        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testConvertCurrentRowToPerson_labelIsNull_typeCustom() {
        int type = 0; // Custom
        String name = "name";
        String address = "emailAddress@google.com";
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Email.ADDRESS, address);
        values.put(CommonDataKinds.Email.TYPE, type);
        long creationTimestampMillis = System.currentTimeMillis();
        // label is not set in the values
        Cursor cursor = makeCursorFromContentValues(values);
        // default value for custom label if null is provided by user.
        String expectedLabel = "Custom";
        Person personExpected = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)
        )
                .setCreationTimestampMillis(creationTimestampMillis)
                .addEmailToPerson(expectedLabel, address)
                .buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID,
                        name)).setCreationTimestampMillis(creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        // Since the type is 1(Homes), we won't use user provided label. So it is fine to be null.
        ContactPoint[] contactPoints = personTested.getContactPoints();
        assertThat(contactPoints.length).isEqualTo(1);
        assertThat(contactPoints[0].getLabel()).isEqualTo(expectedLabel);
        assertThat(contactPoints[0].getEmails()).asList().containsExactly(address);
        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testConvertCurrentRowToPerson_labelIsNull_typeHome() {
        int type = 1; // Home
        String name = "name";
        String address = "emailAddress@google.com";
        String label = CommonDataKinds.Email.getTypeLabel(mResources, type, /* label= */
                null).toString(); // usually "Home"
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Email.ADDRESS, address);
        values.put(CommonDataKinds.Email.TYPE, type);
        long creationTimestampMillis = System.currentTimeMillis();
        // label is not set in the values
        Cursor cursor = makeCursorFromContentValues(values);
        Person personExpected = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)
        ).addEmailToPerson(label, address)
                .setCreationTimestampMillis(creationTimestampMillis).buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name))
                .setCreationTimestampMillis(creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        // Since the type is 1(Home), we won't use user provided label. So it is fine to be null.
        ContactPoint[] contactPoints = personTested.getContactPoints();
        assertThat(contactPoints.length).isEqualTo(1);
        assertThat(contactPoints[0].getLabel()).isEqualTo(label);
        assertThat(contactPoints[0].getEmails()).asList().containsExactly(address);
        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testConvertCurrentRowToPerson_email() {
        int type = 1; // Home
        String name = "name";
        String address = "emailAddress@google.com";
        String label = CommonDataKinds.Email.getTypeLabel(mResources, type, /* label= */
                null).toString(); // usually "Home"
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Email.ADDRESS, address);
        values.put(CommonDataKinds.Email.TYPE, type);
        values.put(CommonDataKinds.Email.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);
        Person personExpected = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)
        ).setCreationTimestampMillis(creationTimestampMillis)
                .addEmailToPerson(label, address).buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)).setCreationTimestampMillis(
                creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        ContactPoint[] contactPoints = personTested.getContactPoints();
        assertThat(contactPoints.length).isEqualTo(1);
        assertThat(contactPoints[0].getLabel()).isEqualTo(label);
        assertThat(contactPoints[0].getEmails()).asList().containsExactly(address);
        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testConvertCurrentRowToPerson_nickName() {
        String name = "name";
        String nick = "nickName";
        ContentValues values = new ContentValues();
        long creationTimestampMillis = System.currentTimeMillis();
        values.put(Data.MIMETYPE, CommonDataKinds.Nickname.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Nickname.NAME, nick);
        Cursor cursor = makeCursorFromContentValues(values);
        Person personExpected = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name).addAdditionalName(
                        Person.TYPE_NICKNAME, nick)
        ).setCreationTimestampMillis(creationTimestampMillis).buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)).setCreationTimestampMillis(
                creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        assertThat(personTested.getAdditionalNameTypes()).asList().containsExactly(
                (long) Person.TYPE_NICKNAME);
        assertThat(personTested.getAdditionalNames()).asList().containsExactly(nick);
        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testConvertCurrentRowToPerson_note() {
        String name = "name";
        String note = "note";
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Note.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Note.NOTE, note);
        Cursor cursor = makeCursorFromContentValues(values);

        Person personExpected = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID,
                        name).addNote(note)).setCreationTimestampMillis(0).buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID,
                        name)).setCreationTimestampMillis(0);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        assertThat(personTested.getNotes()).asList().containsExactly(note);
        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testPhoneNumber_generatesAllVariants_withE164FormatInCP2_oneDigitUS() {
        String name = "name";
        String number = "(202) 555-0111";
        String numberInE164 = "+1202 5550111";
        int type = 1; // Home
        String label = CommonDataKinds.Phone.getTypeLabel(mResources, type, /* label= */
                null).toString(); // usually "Home"
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Phone.NUMBER, number);
        values.put(CommonDataKinds.Phone.NORMALIZED_NUMBER, numberInE164);
        values.put(CommonDataKinds.Phone.TYPE, type);
        values.put(CommonDataKinds.Phone.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)).setCreationTimestampMillis(
                creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        ContactPoint[] contactPoints = personTested.getContactPoints();
        assertThat(contactPoints.length).isEqualTo(1);
        assertThat(contactPoints[0].getLabel()).isEqualTo(label);
        List<String> phoneNumbers = Arrays.asList(contactPoints[0].getPhones());
        assertThat(phoneNumbers).containsExactly(number, "2025550111", "+12025550111",
                "1 (202) 555-0111", "(202) 5550111");
        // 1st one should be the original phone number
        assertThat(phoneNumbers.get(0)).isEqualTo(number);
    }

    @Test
    public void testPhoneNumber_generatesAllVariants_withE164FormatInCP2_oneDigitDO() {
        String name = "name";
        String number = "809-686-5797";
        String numberInE164 = "+1 8096865797";
        int type = 1; // Home
        String label = CommonDataKinds.Phone.getTypeLabel(mResources, type, /* label= */
                null).toString(); // usually "Home"
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Phone.NUMBER, number);
        values.put(CommonDataKinds.Phone.NORMALIZED_NUMBER, numberInE164);
        values.put(CommonDataKinds.Phone.TYPE, type);
        values.put(CommonDataKinds.Phone.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)).setCreationTimestampMillis(
                creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        ContactPoint[] contactPoints = personTested.getContactPoints();
        assertThat(contactPoints.length).isEqualTo(1);
        assertThat(contactPoints[0].getLabel()).isEqualTo(label);
        List<String> phoneNumbers = Arrays.asList(contactPoints[0].getPhones());
        assertThat(phoneNumbers).containsExactly(number, "(809) 6865797",
                "(809) 686-5797", "+18096865797", "8096865797", "1 (809) 686-5797");
        // 1st one should be the original phone number
        assertThat(phoneNumbers.get(0)).isEqualTo(number);
    }

    @Test
    public void testPhoneNumber_generatesAllVariants_withE164FormatInCP2_twoDigitCN() {
        mContactDataHandler = new ContactDataHandler(mContext.getResources());
        String name = "name";
        // They are different on purpose so we can see want is generated from original and what
        // is from the e164 in CP2.
        String number = "134-5678-9012-0000";
        String numberE164 = "+8613456789012";
        int type = 1; // Home
        String label = CommonDataKinds.Phone.getTypeLabel(mResources, type, /* label= */
                null).toString(); // usually "Home"
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Phone.NUMBER, number);
        values.put(CommonDataKinds.Phone.NORMALIZED_NUMBER, numberE164);
        values.put(CommonDataKinds.Phone.TYPE, type);
        values.put(CommonDataKinds.Phone.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)).setCreationTimestampMillis(
                creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        ContactPoint[] contactPoints = personTested.getContactPoints();
        assertThat(contactPoints.length).isEqualTo(1);
        assertThat(contactPoints[0].getLabel()).isEqualTo(label);
        List<String> phoneNumbers = Arrays.asList(contactPoints[0].getPhones());
        assertThat(phoneNumbers).containsExactly(number, "134567890120000", "86 134 5678 9012",
                "+8613456789012", "134 5678 9012", "13456789012", "134 56789012");
        // 1st should be the original one
        assertThat(phoneNumbers.get(0)).isEqualTo(number);
    }

    @Test
    public void testPhoneNumber_generatesAllVariants_withE164FormatInCP2_twoDigitES() {
        mContactDataHandler = new ContactDataHandler(mContext.getResources());
        String name = "name";
        String number = "913 30 28 00";
        String numberE164 = "+34913302800";
        int type = 1; // Home
        String label = CommonDataKinds.Phone.getTypeLabel(mResources, type, /* label= */
                null).toString(); // usually "Home"
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Phone.NUMBER, number);
        values.put(CommonDataKinds.Phone.NORMALIZED_NUMBER, numberE164);
        values.put(CommonDataKinds.Phone.TYPE, type);
        values.put(CommonDataKinds.Phone.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)).setCreationTimestampMillis(
                creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        ContactPoint[] contactPoints = personTested.getContactPoints();
        assertThat(contactPoints.length).isEqualTo(1);
        assertThat(contactPoints[0].getLabel()).isEqualTo(label);
        List<String> phoneNumbers = Arrays.asList(contactPoints[0].getPhones());
        assertThat(phoneNumbers).containsExactly(number, "913302800", "34 913 30 28 00",
                "913 30 2800", "+34913302800");
        // 1st should be the original one
        assertThat(phoneNumbers.get(0)).isEqualTo(number);
    }

    @Test
    public void testPhoneNumber_generatesAllVariants_withE164FormatInCP2_threeDigitBG() {
        mContactDataHandler = new ContactDataHandler(mContext.getResources());
        String name = "name";
        String number = "29335000";
        String numberE164 = "+35929335000";
        int type = 1; // Home
        String label = CommonDataKinds.Phone.getTypeLabel(mResources, type, /* label= */
                null).toString(); // usually "Home"
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Phone.NUMBER, number);
        values.put(CommonDataKinds.Phone.NORMALIZED_NUMBER, numberE164);
        values.put(CommonDataKinds.Phone.TYPE, type);
        values.put(CommonDataKinds.Phone.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)).setCreationTimestampMillis(
                creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        ContactPoint[] contactPoints = personTested.getContactPoints();
        assertThat(contactPoints.length).isEqualTo(1);
        assertThat(contactPoints[0].getLabel()).isEqualTo(label);
        List<String> phoneNumbers = Arrays.asList(contactPoints[0].getPhones());
        assertThat(phoneNumbers).containsExactly(number, "+35929335000", "359 2 933 5000",
                "2 9335000", "2 933 5000");
        // 1st should be the original one
        assertThat(phoneNumbers.get(0)).isEqualTo(number);
    }

    @Test
    public void testPhone_generatesAllVariants_withoutE164FormatInCP2_withoutFallback() {
        String name = "name";
        // Original number is in e164 format.
        String number = "(202) 555-0111";
        int type = 1; // Home
        String label = CommonDataKinds.Phone.getTypeLabel(mResources, type, /* label= */
                null).toString(); // usually "Home"
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Phone.NUMBER, number);
        values.put(CommonDataKinds.Phone.TYPE, type);
        values.put(CommonDataKinds.Phone.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)).setCreationTimestampMillis(
                creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        ContactPoint[] contactPoints = personTested.getContactPoints();
        assertThat(contactPoints.length).isEqualTo(1);
        assertThat(contactPoints[0].getLabel()).isEqualTo(label);
        List<String> phoneNumbers = Arrays.asList(contactPoints[0].getPhones());
        assertThat(phoneNumbers).containsExactly(number, "2025550111");
        assertThat(phoneNumbers.get(0)).isEqualTo(number);
    }

    @Test
    public void testPhone_generatesAllVariants_withoutE164FormatInCP2_withFallback_originalE164() {
        String name = "name";
        // Original number is in e164 format.
        String number = "+12025550111";
        int type = 1; // Home
        String label = CommonDataKinds.Phone.getTypeLabel(mResources, type, /* label= */
                null).toString(); // usually "Home"
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Phone.NUMBER, number);
        values.put(CommonDataKinds.Phone.TYPE, type);
        values.put(CommonDataKinds.Phone.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)).setCreationTimestampMillis(
                creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        ContactPoint[] contactPoints = personTested.getContactPoints();
        assertThat(contactPoints.length).isEqualTo(1);
        assertThat(contactPoints[0].getLabel()).isEqualTo(label);
        List<String> phoneNumbers = Arrays.asList(contactPoints[0].getPhones());
        assertThat(phoneNumbers).containsExactly("+12025550111", "(202) 555-0111", "2025550111",
                "1 (202) 555-0111", "(202) 5550111");
        assertThat(phoneNumbers.get(0)).isEqualTo(number);
    }

    @Test
    public void testPhoneNumberInvalidE164_generatesAllVariants_withoutE164FormatInCP2() {
        String name = "name";
        String number = "+11234567890";
        int type = 1; // Home
        String label = CommonDataKinds.Phone.getTypeLabel(mResources, type, /* label= */
                null).toString(); // usually "Home"
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Phone.NUMBER, number);
        values.put(CommonDataKinds.Phone.TYPE, type);
        values.put(CommonDataKinds.Phone.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)).setCreationTimestampMillis(
                creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        ContactPoint[] contactPoints = personTested.getContactPoints();
        assertThat(contactPoints.length).isEqualTo(1);
        assertThat(contactPoints[0].getLabel()).isEqualTo(label);
        List<String> phoneNumbers = Arrays.asList(contactPoints[0].getPhones());
        assertThat(phoneNumbers).containsExactly(number, "1234567890", "1 1234567890");
        assertThat(phoneNumbers.get(0)).isEqualTo(number);
    }

    @Test
    public void testInvalidPhoneNumber_withoutE164FormatInCP2() {
        String name = "name";
        String number = "123-456-7890";
        int type = 1; // Home
        String label = CommonDataKinds.Phone.getTypeLabel(mResources, type, /* label= */
                null).toString(); // usually "Home"
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Phone.NUMBER, number);
        values.put(CommonDataKinds.Phone.TYPE, type);
        values.put(CommonDataKinds.Phone.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)).setCreationTimestampMillis(
                creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        ContactPoint[] contactPoints = personTested.getContactPoints();
        assertThat(contactPoints.length).isEqualTo(1);
        assertThat(contactPoints[0].getLabel()).isEqualTo(label);
        List<String> phoneNumbers = Arrays.asList(contactPoints[0].getPhones());
        assertThat(phoneNumbers).containsExactly(number, "1234567890");
        assertThat(phoneNumbers.get(0)).isEqualTo(number);
    }

    @Test
    public void testTwoPhoneNumber_generatesAllVariantsInOrder_withoutE164FormatInCP2() {
        mContactDataHandler = new ContactDataHandler(mContext.getResources());
        String name = "name";
        String number1 = "+12025550111";
        int type = 1; // Home
        String label = CommonDataKinds.Phone.getTypeLabel(mResources, type, /* label= */
                null).toString(); // usually "Home"
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Phone.NUMBER, number1);
        values.put(CommonDataKinds.Phone.TYPE, type);
        values.put(CommonDataKinds.Phone.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        // Read first phone number
        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)).setCreationTimestampMillis(
                creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);

        // Read second
        String number2 = "+12025550116";
        values.put(CommonDataKinds.Phone.NUMBER, number2);
        cursor = makeCursorFromContentValues(values);
        convertRowToPerson(cursor, helperTested);

        Person personTested = helperTested.buildPerson();

        ContactPoint[] contactPoints = personTested.getContactPoints();
        assertThat(contactPoints.length).isEqualTo(1);
        assertThat(contactPoints[0].getLabel()).isEqualTo(label);
        List<String> phoneNumbers = Arrays.asList(contactPoints[0].getPhones());
        assertThat(phoneNumbers).containsExactly(number1, number2, "(202) 555-0111",
                "2025550111", "1 (202) 555-0111", "(202) 5550111", "(202) 555-0116",
                "2025550116", "1 (202) 555-0116", "(202) 5550116");
        assertThat(phoneNumbers.get(0)).isEqualTo(number1);
        assertThat(phoneNumbers.get(1)).isEqualTo(number2);
    }

    @Test
    public void testConvertCurrentRowToPerson_postal() {
        String name = "name";
        int type = 1; // Home
        String postal = "structuredPostalFormattedAddress";
        String label = CommonDataKinds.StructuredPostal.getTypeLabel(mResources, type, /* label= */
                null).toString(); // usually "Home"
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, postal);
        values.put(CommonDataKinds.StructuredPostal.TYPE, type);
        values.put(CommonDataKinds.StructuredPostal.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        Person personExpected = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)
        )
                .addAddressToPerson(CommonDataKinds.StructuredPostal.getTypeLabel(
                        mResources, type, label).toString(), postal)
                .setCreationTimestampMillis(creationTimestampMillis)
                .buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name))
                .setCreationTimestampMillis(creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        ContactPoint[] contactPoints = personTested.getContactPoints();
        assertThat(contactPoints.length).isEqualTo(1);
        assertThat(contactPoints[0].getLabel()).isEqualTo(label);
        assertThat(contactPoints[0].getAddresses()).asList().containsExactly(postal);
        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testConvertCurrentRowToPerson_name() {
        String name = "name";
        String rawContactId = "raw" + TEST_ID;
        String givenName = "structuredNameGivenName" + TEST_ID;
        String middleName = "structuredNameMiddleName" + TEST_ID;
        String familyName = "structuredNameFamilyName" + TEST_ID;
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.NAME_RAW_CONTACT_ID, rawContactId);
        values.put(CommonDataKinds.StructuredName.GIVEN_NAME, givenName);
        values.put(CommonDataKinds.StructuredName.MIDDLE_NAME, middleName);
        values.put(CommonDataKinds.StructuredName.FAMILY_NAME, familyName);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        Person personExpected = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name).setGivenName(
                        givenName).setMiddleName(middleName).setFamilyName(familyName)
        ).setCreationTimestampMillis(creationTimestampMillis).buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID,
                        name)).setCreationTimestampMillis(creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        assertThat(personTested.getGivenName()).isEqualTo(givenName);
        assertThat(personTested.getMiddleName()).isEqualTo(middleName);
        assertThat(personTested.getFamilyName()).isEqualTo(familyName);
        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testConvertCurrentRowToPerson_organization() {
        String name = "name";
        String company = "company";
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Organization.COMPANY, company);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        Person personExpected = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name).addAffiliation(company)
        ).setCreationTimestampMillis(creationTimestampMillis).buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID,
                        name)).setCreationTimestampMillis(creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testConvertCurrentRowToPerson_organizationWithJobTitle() {
        String name = "name";
        String title = "Software Engineer";
        String company = "Google Inc";
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Organization.TITLE, title);
        values.put(CommonDataKinds.Organization.COMPANY, company);
        Cursor cursor = makeCursorFromContentValues(values);

        Person personExpected = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name).addAffiliation(
                        "Software Engineer, Google Inc")).setCreationTimestampMillis(
                0).buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID,
                        name)).setCreationTimestampMillis(0);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testConvertCurrentRowToPerson_organizationWithJobTitleAndDepartment() {
        String name = "name";
        String title = "Software Engineer";
        String department = "Google Cloud";
        String company = "Google Inc";
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Organization.TITLE, title);
        values.put(CommonDataKinds.Organization.DEPARTMENT, department);
        values.put(CommonDataKinds.Organization.COMPANY, company);
        Cursor cursor = makeCursorFromContentValues(values);

        Person personExpected = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name).addAffiliation(
                        "Software Engineer, Google Cloud, Google Inc")).setCreationTimestampMillis(
                0).buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID,
                        name)).setCreationTimestampMillis(0);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testConvertCurrentRowToPerson_relation_nameNotNull() {
        String name = "name";
        String relationName = "relationName";
        int type = CommonDataKinds.Relation.TYPE_BROTHER;
        String label = null;
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Relation.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Relation.NAME, relationName);
        values.put(CommonDataKinds.Relation.TYPE, type);
        values.put(CommonDataKinds.Relation.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        Person personExpected = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name).addRelation(relationName)
        ).setCreationTimestampMillis(creationTimestampMillis).buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name))
                .setCreationTimestampMillis(creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testConvertCurrentRowToPerson_relation_nameNull_typeNotCustom() {
        String name = "name";
        String relationName = null;
        int type = CommonDataKinds.Relation.TYPE_BROTHER;
        String label = null;
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Relation.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Relation.NAME, relationName);
        values.put(CommonDataKinds.Relation.TYPE, type);
        values.put(CommonDataKinds.Relation.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        Person personExpected = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name).addRelation(
                        CommonDataKinds.Relation.getTypeLabel(mResources, type, label).toString())
        ).setCreationTimestampMillis(creationTimestampMillis).buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID,
                        name)).setCreationTimestampMillis(creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testConvertCurrentRowToPerson_relation_nameNull_typeCustom_labelNull() {
        String name = "name";
        String relationName = null;
        int type = CommonDataKinds.Relation.TYPE_CUSTOM;
        String label = null;
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Relation.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Relation.NAME, relationName);
        values.put(CommonDataKinds.Relation.TYPE, type);
        values.put(CommonDataKinds.Relation.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        Person personExpected = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name).addRelation(
                        CommonDataKinds.Relation.getTypeLabel(mResources, type, label).toString())
        ).setCreationTimestampMillis(creationTimestampMillis).buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID,
                        name)).setCreationTimestampMillis(creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testConvertCurrentRowToPerson_relation_nameNull_typeCustom_labelNotNull() {
        String name = "name";
        String relationName = null;
        int type = CommonDataKinds.Relation.TYPE_CUSTOM;
        String label = "CustomLabel";
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Relation.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Relation.NAME, relationName);
        values.put(CommonDataKinds.Relation.TYPE, type);
        values.put(CommonDataKinds.Relation.LABEL, label);
        long creationTimestampMillis = System.currentTimeMillis();
        Cursor cursor = makeCursorFromContentValues(values);

        Person personExpected = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name).addRelation(label)
        ).setCreationTimestampMillis(creationTimestampMillis).buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)).setCreationTimestampMillis(
                creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testHandleCurrentRowWithUnknownMimeType() {
        // Change the Mimetype of StructuredName.
        String name = "name";
        long creationTimestampMillis = System.currentTimeMillis();
        MatrixCursor cursor = new MatrixCursor(
                new String[]{Data.MIMETYPE, CommonDataKinds.StructuredName.GIVEN_NAME});
        cursor.newRow().add("testUnknownMimeType", "testGivenName");

        Person personExpected = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name)
        ).setCreationTimestampMillis(creationTimestampMillis).buildPerson();

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID, name))
                .setCreationTimestampMillis(creationTimestampMillis);
        convertRowToPerson(cursor, helperTested);
        Person personTested = helperTested.buildPerson();

        // Couldn't read values correctly from an unknown mime type.
        assertThat(personTested.getGivenName()).isNull();
        assertThat(personTested).isEqualTo(personExpected);
    }

    @Test
    public void testConvertCurrentRowToPerson_nullEmailAddress_noExceptionThrown() {
        int type = 0; // Custom
        String name = "name";
        String label = "CustomLabel";
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Email.ADDRESS, /*address=*/ (String) null);
        values.put(CommonDataKinds.Email.TYPE, type);
        values.put(CommonDataKinds.Email.LABEL, label);
        Cursor cursor = makeCursorFromContentValues(values);

        PersonBuilderHelper helperTested = new PersonBuilderHelper(TEST_ID,
                new Person.Builder(TEST_NAMESPACE, TEST_ID,
                        name)).setCreationTimestampMillis(0);
        convertRowToPerson(cursor, helperTested);

        // no NPE thrown.
    }
}
