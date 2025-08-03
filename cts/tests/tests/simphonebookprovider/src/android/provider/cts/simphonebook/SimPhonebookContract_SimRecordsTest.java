/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.provider.cts.simphonebook;

import static android.provider.SimPhonebookContract.ElementaryFiles.EF_ADN;

import static com.android.internal.telephony.testing.CursorSubject.assertThat;
import static com.android.internal.telephony.testing.TelephonyAssertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.SimPhonebookContract.ElementaryFiles;
import android.provider.SimPhonebookContract.SimRecords;
import android.telephony.PhoneNumberUtils;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.RequiredFeatureRule;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/** Tests of {@link SimRecords}. */
@RunWith(AndroidJUnit4.class)
public class SimPhonebookContract_SimRecordsTest {

    private final SimsCleanupRule mAdnCleanupRule = new SimsCleanupRule(ElementaryFiles.EF_ADN);
    @Rule
    public final TestRule mRule = RuleChain
            .outerRule(new RequiredFeatureRule(PackageManager.FEATURE_TELEPHONY))
            .around(new SimPhonebookRequirementsRule())
            .around(mAdnCleanupRule);

    private Context mContext;
    private ContentResolver mResolver;
    private int mDefaultSubscriptionId;
    public static final String PHONE_REQUIRED_MESSAGE = SimRecords.PHONE_NUMBER + " is required.";

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mResolver = mContext.getContentResolver();
        mDefaultSubscriptionId = new RemovableSims(mContext).getDefaultSubscriptionId();
    }

    @Test
    public void queryAdn_nullProjection_returnsCursorWithAllSupportedColumns() {
        Uri uri = SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN);
        String[] projection = null;
        try (Cursor cursor = query(uri, projection)) {
            assertThat(cursor.getColumnNames()).asList().containsAtLeast(
                    SimRecords.SUBSCRIPTION_ID,
                    SimRecords.ELEMENTARY_FILE_TYPE,
                    SimRecords.RECORD_NUMBER,
                    SimRecords.NAME,
                    SimRecords.PHONE_NUMBER
            );
        }
    }

    @Test
    public void queryAdn_nonNullProjection_returnsCursorWithProvidedProjection() {
        Uri adnUri = SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN);

        try (Cursor cursor = query(adnUri, new String[]{SimRecords.NAME})) {
            assertThat(cursor).hasColumnNames(SimRecords.NAME);
        }

        String[] all = {
                SimRecords.SUBSCRIPTION_ID, SimRecords.RECORD_NUMBER,
                SimRecords.NAME, SimRecords.PHONE_NUMBER
        };

        Collections.shuffle(Arrays.asList(all));
        try (Cursor cursor = query(adnUri, all)) {
            assertThat(cursor).hasColumnNames(all);
        }

        Collections.shuffle(Arrays.asList(all));
        try (Cursor cursor = query(adnUri, all)) {
            assertThat(cursor).hasColumnNames(all);
        }
    }

    @Test
    public void queryAdn_noAdnRecords_returnsEmptyCursor() {
        try (Cursor cursor = query(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                null)) {
            assertThat(cursor).hasCount(0);
        }
    }

    @Test
    public void queryAdn_nonEmpty_returnsAdnRecordsFromSim() {
        insertAdn(mDefaultSubscriptionId, "Name1", "5550101");
        insertAdn(mDefaultSubscriptionId, "Name2", "5550102");
        insertAdn(mDefaultSubscriptionId, "Name3", "5550103");

        String[] projection = {
                SimRecords.SUBSCRIPTION_ID,
                SimRecords.ELEMENTARY_FILE_TYPE,
                SimRecords.NAME,
                SimRecords.PHONE_NUMBER
        };
        try (Cursor cursor = query(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                projection)) {
            assertThat(cursor).hasCount(3);
            assertThat(cursor).atRow(0)
                    .hasRowValues(mDefaultSubscriptionId, ElementaryFiles.EF_ADN, "Name1",
                            "5550101");
            assertThat(cursor).atRow(1)
                    .hasRowValues(mDefaultSubscriptionId, ElementaryFiles.EF_ADN, "Name2",
                            "5550102");
            assertThat(cursor).atRow(2)
                    .hasRowValues(mDefaultSubscriptionId, ElementaryFiles.EF_ADN, "Name3",
                            "5550103");
        }
    }

    @Test
    public void query_itemUri_returnsCursorWithCorrectItem() {
        Uri insert1 = insertAdn(mDefaultSubscriptionId, "Name1", "18005550101");
        Uri insert2 = insertAdn(mDefaultSubscriptionId, "Name2", "18005550102");
        Uri insert3 = insertAdn(mDefaultSubscriptionId, "Name3", "18005550103");

        String[] projection = {SimRecords.RECORD_NUMBER};
        int index1, index2, index3;
        try (Cursor cursor1 = query(insert1, projection);
             Cursor cursor2 = query(insert2, projection);
             Cursor cursor3 = query(insert3, projection)
        ) {
            cursor1.moveToFirst();
            index1 = cursor1.getInt(0);
            cursor2.moveToFirst();
            index2 = cursor2.getInt(0);
            cursor3.moveToFirst();
            index3 = cursor3.getInt(0);
        }

        projection = new String[]{SimRecords.NAME, SimRecords.PHONE_NUMBER};
        // By default the insert returns the item uri but we won't require it in CTS.
        try (Cursor cursor1 = query(SimRecords.getItemUri(
                mDefaultSubscriptionId, ElementaryFiles.EF_ADN, index1), projection);
             Cursor cursor2 = query(
                     SimRecords.getItemUri(mDefaultSubscriptionId, ElementaryFiles.EF_ADN, index2),
                     projection);
             Cursor cursor3 = query(
                     SimRecords.getItemUri(mDefaultSubscriptionId, ElementaryFiles.EF_ADN, index3),
                     projection)
        ) {
            assertThat(cursor1).hasSingleRow("Name1", "18005550101");
            assertThat(cursor2).hasSingleRow("Name2", "18005550102");
            assertThat(cursor3).hasSingleRow("Name3", "18005550103");
        }

    }

    @Test
    public void query_indexOutOfBounds_returnsEmptyCursor() {
        int maxRecords;
        try (Cursor cursor = query(
                ElementaryFiles.getItemUri(mDefaultSubscriptionId, ElementaryFiles.EF_ADN),
                new String[]{ElementaryFiles.MAX_RECORDS})) {
            cursor.moveToFirst();
            maxRecords = cursor.getInt(0);
        }

        try (Cursor cursor = query(SimRecords.getItemUri(mDefaultSubscriptionId,
                ElementaryFiles.EF_ADN, maxRecords + 1), null)) {
            assertThat(cursor).hasCount(0);
        }
    }

    @Test
    public void insertAdn_addsRecordAndReturnsValidUri() {
        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        values.put(SimRecords.PHONE_NUMBER, "8005550101");

        Uri uri = mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                values);

        try (Cursor cursor = query(uri, new String[]{
                SimRecords.SUBSCRIPTION_ID,
                SimRecords.ELEMENTARY_FILE_TYPE,
                SimRecords.RECORD_NUMBER,
                SimRecords.NAME,
                SimRecords.PHONE_NUMBER
        })) {
            assertThat(cursor).hasSingleRow(
                    mDefaultSubscriptionId, ElementaryFiles.EF_ADN,
                    Integer.parseInt(uri.getLastPathSegment()),
                    "Name", "8005550101");
        }
    }

    @Test
    public void updateAdn_existingRecord_updatesRecordAndReturns1() {
        Uri uri = insertAdn(mDefaultSubscriptionId, "Initial", "8005550101");

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Updated");
        values.put(SimRecords.PHONE_NUMBER, "8005550102");
        int count = mResolver.update(uri, values, null);
        mResolver.update(uri, values, null, null);

        assertThat(count).isEqualTo(1);
        try (Cursor cursor = query(uri, new String[]{
                SimRecords.NAME,
                SimRecords.PHONE_NUMBER
        })) {
            assertThat(cursor).hasSingleRow("Updated", "8005550102");
        }
    }

    @Test
    public void updateAdn_unusedIndex_updatesRecordAndReturns1() {
        Uri uri = SimRecords.getItemUri(mDefaultSubscriptionId, EF_ADN, 1);
        // Assert for clarity that there is no data stored for record number 1
        try (Cursor cursor = query(uri, null)) {
            assertThat(cursor).hasCount(0);
        }

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Updated");
        values.put(SimRecords.PHONE_NUMBER, "8005550102");
        int count = mResolver.update(uri, values, null);
        mResolver.update(SimRecords.getItemUri(mDefaultSubscriptionId, EF_ADN, 1), values, null,
                null);

        assertThat(count).isEqualTo(1);
        try (Cursor cursor = query(uri, new String[]{
                SimRecords.NAME,
                SimRecords.PHONE_NUMBER
        })) {
            assertThat(cursor).hasSingleRow("Updated", "8005550102");
        }
    }

    @Test
    public void deleteAdn_existingRecord_removesRecordAndReturns1() {
        Uri uri = insertAdn(mDefaultSubscriptionId, "Initial", "8005550101");

        int count = mResolver.delete(uri, null);
        mResolver.delete(uri, null, null);

        assertThat(count).isEqualTo(1);
        try (Cursor cursor = query(uri, null)) {
            assertThat(cursor).hasCount(0);
        }
    }

    @Test
    public void insertThenDeleteAdn_urisRemainValid() {
        Uri uri1 = insertAdn(mDefaultSubscriptionId, "Name1", "5550101");
        Uri uri2 = insertAdn(mDefaultSubscriptionId, "Name2", "5550102");
        Uri uri3 = insertAdn(mDefaultSubscriptionId, "Name3", "5550103");

        mResolver.delete(uri2, null);
        Uri uri4 = insertAdn(mDefaultSubscriptionId, "Name4", "5550104");

        String[] projection = {
                SimRecords.RECORD_NUMBER,
                SimRecords.NAME,
                SimRecords.PHONE_NUMBER
        };

        try (Cursor cursor = query(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                projection)) {
            // We don't require a specific order.
            assertThat(cursor).asLists().containsExactlyElementsIn(
                    ImmutableList.of(
                            ImmutableList.of(uri1.getLastPathSegment(), "Name1", "5550101"),
                            ImmutableList.of(uri3.getLastPathSegment(), "Name3", "5550103"),
                            ImmutableList.of(uri4.getLastPathSegment(), "Name4", "5550104")
                    )
            );
        }
    }

    @Test
    public void insertAdn_phoneNumberWithDialableNonDigits_addsRecord() {
        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        String phone = "+1234567890#*" + PhoneNumberUtils.PAUSE + PhoneNumberUtils.WAIT
                + PhoneNumberUtils.WILD;
        values.put(SimRecords.PHONE_NUMBER, phone);
        Uri uri = mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                values);

        try (Cursor cursor = query(uri, new String[]{
                SimRecords.NAME, SimRecords.PHONE_NUMBER
        })) {
            assertThat(cursor).hasSingleRow("Name", phone);
        }
    }

    @Test
    public void insertAdn_valueLengthLimits() {
        String name;
        String phoneNumber;
        // Get the limits and create data from them.
        try (Cursor cursor = query(
                ElementaryFiles.getItemUri(mDefaultSubscriptionId, ElementaryFiles.EF_ADN),
                new String[]{
                        ElementaryFiles.NAME_MAX_LENGTH,
                        ElementaryFiles.PHONE_NUMBER_MAX_LENGTH
                })) {
            cursor.moveToFirst();
            char[] chars = new char[cursor.getInt(0)];
            Arrays.fill(chars, 'a');
            name = new String(chars);
            chars = new char[cursor.getInt(1)];
            Arrays.fill(chars, '1');
            phoneNumber = new String(chars);
        }
        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, name);
        values.put(SimRecords.PHONE_NUMBER, phoneNumber);
        // Creating an item with values at the limits should succeed
        mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN), values);

        // The name exceeds the max length by 1
        values.put(SimRecords.NAME, name + "b");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                        values));
        assertThat(e).hasMessageThat().isEqualTo(SimRecords.NAME + " is too long.");

        // The phone number exceeds the max length by 1
        values.put(SimRecords.NAME, name);
        values.put(SimRecords.PHONE_NUMBER, phoneNumber + '2');
        e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                        values));
        assertThat(e).hasMessageThat().isEqualTo(SimRecords.PHONE_NUMBER + " is too long.");

        // Check that only the initial insert added data.
        try (Cursor cursor = query(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                new String[]{SimRecords.NAME, SimRecords.PHONE_NUMBER})) {
            assertThat(cursor).hasSingleRow(name, phoneNumber);
        }
    }

    @Test
    public void insertAdn_phoneNumberOnly_addsRecord() {
        ContentValues values = new ContentValues();
        values.put(SimRecords.PHONE_NUMBER, "18005550101");
        Uri noNameUri = mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                values);
        values.putNull(SimRecords.NAME);
        Uri nullNameUri = mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                values);
        values.put(SimRecords.NAME, "");
        Uri emptyNameUri = mResolver.insert(
                SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN), values);

        String[] projection = {SimRecords.NAME, SimRecords.PHONE_NUMBER};
        try (Cursor noName = query(noNameUri, projection);
             Cursor nullName = query(nullNameUri, projection);
             Cursor emptyName = query(emptyNameUri, projection)
        ) {
            assertThat(noName).hasSingleRow("", "18005550101");
            assertThat(nullName).hasSingleRow("", "18005550101");
            assertThat(emptyName).hasSingleRow("", "18005550101");
        }
    }


    @Test
    public void insertAdn_nameWithNonGsmAlphabetCharacters_addsRecord() {
        String name1 = "日本";

        ContentValues values = new ContentValues();
        values.put(SimRecords.PHONE_NUMBER, "18005550101");

        values.put(SimRecords.NAME, name1);
        mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN), values);

        String name2 = "abc日本";
        values.put(SimRecords.NAME, name2);
        mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN), values);

        String name3 = "日a本bc";
        values.put(SimRecords.NAME, name3);
        mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN), values);
        String[] projection = {SimRecords.NAME};
        try (Cursor cursor = query(
                SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN), projection)) {
            assertThat(cursor).hasData(new Object[][]{{name1}, {name2}, {name3}});
        }
    }

    @Test
    public void insertAdn_nameOnly_throwsCorrectException() {
        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                        values));
        assertThat(e).hasMessageThat().isEqualTo(PHONE_REQUIRED_MESSAGE);

        values.putNull(SimRecords.PHONE_NUMBER);

        e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                        values));
        assertThat(e).hasMessageThat().isEqualTo(PHONE_REQUIRED_MESSAGE);

        values.put(SimRecords.PHONE_NUMBER, "");

        e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                        values));
        assertThat(e).hasMessageThat().isEqualTo(PHONE_REQUIRED_MESSAGE);
    }

    @Test
    public void update_nameOnly_throwsCorrectException() {
        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        values.put(SimRecords.PHONE_NUMBER, "18005550101");

        Uri itemUri = mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                values);

        values.remove(SimRecords.PHONE_NUMBER);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                mResolver.update(itemUri, values, null));

        assertThat(e).hasMessageThat().isEqualTo(PHONE_REQUIRED_MESSAGE);

        values.putNull(SimRecords.PHONE_NUMBER);
        e = assertThrows(IllegalArgumentException.class, () ->
                mResolver.update(itemUri, values, null));

        assertThat(e).hasMessageThat().isEqualTo(PHONE_REQUIRED_MESSAGE);

        values.put(SimRecords.PHONE_NUMBER, "");
        e = assertThrows(IllegalArgumentException.class, () ->
                mResolver.update(itemUri, values, null));

        assertThat(e).hasMessageThat().isEqualTo(PHONE_REQUIRED_MESSAGE);
    }

    @Test
    public void insertAdn_phoneNumberContainingNonDialable_throwsCorrectException() {
        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        values.put(SimRecords.PHONE_NUMBER, "A555TEST x7777");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                        values));
        assertThat(e).hasMessageThat()
                .isEqualTo(SimRecords.PHONE_NUMBER + " contains unsupported characters.");
    }

    @Test
    public void updateAdn_phoneNumberContainingNonDialable_throwsCorrectException() {
        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        values.put(SimRecords.PHONE_NUMBER, "5550101");
        Uri itemUri = mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                values);

        values.put(SimRecords.PHONE_NUMBER, "A555TEST x7777");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                mResolver.update(itemUri, values, null));

        assertThat(e).hasMessageThat()
                .isEqualTo(SimRecords.PHONE_NUMBER + " contains unsupported characters.");

        try (Cursor cursor = query(itemUri, new String[]{
                SimRecords.NAME,
                SimRecords.PHONE_NUMBER
        })) {
            assertThat(cursor).hasSingleRow("Name", "5550101");
        }
    }

    @Test
    public void updateAdn_duplicateRecord_updatesCorrectRecord() {
        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Initial");
        values.put(SimRecords.PHONE_NUMBER, "5550101");
        // Create three records with the same values
        mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN), values);
        Uri uri2 = mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                values);
        mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN), values);

        // Update the second of the 3
        values.put(SimRecords.NAME, "Updated");
        values.put(SimRecords.PHONE_NUMBER, "5550102");
        mResolver.update(uri2, values, null);

        try (Cursor cursor = query(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                new String[]{
                        SimRecords.NAME,
                        SimRecords.PHONE_NUMBER
                })) {
            assertThat(cursor).hasCount(3);
            assertThat(cursor).atRow(0).hasRowValues("Initial", "5550101");
            assertThat(cursor).atRow(1).hasRowValues("Updated", "5550102");
            assertThat(cursor).atRow(2).hasRowValues("Initial", "5550101");
        }
    }

    @Test
    public void deleteAdn_duplicateRecord_deletesCorrectRecord() {
        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Initial");
        values.put(SimRecords.PHONE_NUMBER, "5550101");
        mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN), values);
        Uri uri2 = mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                values);
        mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN), values);

        mResolver.delete(uri2, null, null);

        try (Cursor cursor = query(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                new String[]{
                        SimRecords.NAME,
                        SimRecords.PHONE_NUMBER
                })) {
            assertThat(cursor).hasCount(2);
            assertThat(cursor).atRow(0).hasRowValues("Initial", "5550101");
            assertThat(cursor).atRow(1).hasRowValues("Initial", "5550101");
        }
    }

    @Test
    public void delete_unusedIndex_returnsZero() {
        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        values.put(SimRecords.PHONE_NUMBER, "5550101");
        mResolver.insert(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN), values);

        int result = mResolver.delete(
                SimRecords.getItemUri(mDefaultSubscriptionId, ElementaryFiles.EF_ADN, 10), null);

        assertThat(result).isEqualTo(0);
        try (Cursor cursor = query(SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN),
                new String[]{SimRecords.NAME, SimRecords.PHONE_NUMBER})) {
            assertThat(cursor).hasSingleRow("Name", "5550101");
        }
    }

    /** This is a pretty slow test because it requires filling up the ADN file. */
    @LargeTest
    @Test
    public void insertAdn_efFull_throwsCorrectException() {
        int maxRecords;
        try (Cursor cursor = query(
                ElementaryFiles.getItemUri(mDefaultSubscriptionId, ElementaryFiles.EF_ADN),
                new String[]{ElementaryFiles.MAX_RECORDS})) {

            assertThat(cursor.moveToFirst()).isTrue();
            maxRecords = cursor.getInt(0);
        }
        Uri adn = SimRecords.getContentUri(mDefaultSubscriptionId, EF_ADN);
        ContentValues[] values = new ContentValues[maxRecords];
        for (int i = 0; i < maxRecords; i++) {
            values[i] = new ContentValues();
            values[i].put(SimRecords.NAME, "Name" + i);
            values[i].put(SimRecords.PHONE_NUMBER, String.valueOf(i));
        }
        mResolver.bulkInsert(adn, values);

        ContentValues moreValues = new ContentValues();
        moreValues.put(SimRecords.NAME, "Final Name");
        moreValues.put(SimRecords.PHONE_NUMBER, "5555");
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                mResolver.insert(adn, moreValues));
        assertThat(e).hasMessageThat().startsWith(adn + " is full.");

        mResolver.delete(SimRecords.getItemUri(mDefaultSubscriptionId, ElementaryFiles.EF_ADN, 1),
                null);

        // This should work now that one has been deleted.
        Uri uri = mResolver.insert(adn, moreValues);

        try (Cursor itemCursor = query(uri,
                new String[]{SimRecords.NAME, SimRecords.PHONE_NUMBER})) {
            assertThat(itemCursor).hasSingleRow("Final Name", "5555");
        }
    }

    @NonNull
    private Cursor query(Uri uri, String[] projection) {
        return Objects.requireNonNull(mResolver.query(uri, projection, null, null));
    }

    private Uri insertAdn(int subscriptionId, String name, String phoneNumber) {
        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, name);
        values.put(SimRecords.PHONE_NUMBER, phoneNumber);
        return mResolver.insert(SimRecords.getContentUri(subscriptionId, EF_ADN), values);
    }
}
