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

package com.android.phone;

import static android.provider.SimPhonebookContract.ElementaryFiles.EF_ADN;
import static android.provider.SimPhonebookContract.ElementaryFiles.EF_FDN;
import static android.provider.SimPhonebookContract.ElementaryFiles.EF_SDN;

import static com.android.internal.telephony.testing.CursorSubject.assertThat;
import static com.android.internal.telephony.testing.TelephonyAssertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.SimPhonebookContract;
import android.provider.SimPhonebookContract.ElementaryFiles;
import android.provider.SimPhonebookContract.SimRecords;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.provider.ProviderTestRule;

import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.uicc.AdnCapacity;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccConstants;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.google.common.truth.Correspondence;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public final class SimPhonebookProviderTest {

    private static final String EMOJI = new String(Character.toChars(0x1F642));
    private static final Correspondence<AdnRecord, AdnRecord> ADN_RECORD_IS_EQUAL =
            Correspondence.from(AdnRecord::isEqual, "isEqual");

    @Rule
    public final ProviderTestRule mProviderRule = new ProviderTestRule.Builder(
            TestableSimPhonebookProvider.class, SimPhonebookContract.AUTHORITY).build();

    private ContentResolver mResolver;
    private FakeIccPhoneBook mIccPhoneBook;
    private SubscriptionManager mMockSubscriptionManager;

    private static List<SubscriptionInfo> createSubscriptionsWithIds(int... subscriptionIds) {
        ImmutableList.Builder<SubscriptionInfo> builder = ImmutableList.builderWithExpectedSize(
                subscriptionIds.length);
        for (int i = 0; i < subscriptionIds.length; i++) {
            builder.add(createSubscriptionInfo(i, subscriptionIds[i]));
        }
        return builder.build();
    }

    private static SubscriptionInfo createSubscriptionInfo(int slotIndex, int subscriptiondId) {
        return new SubscriptionInfo(
                subscriptiondId, "", slotIndex, null, null, 0, 0, null, 0, null, null, null, null,
                false, null, null);
    }

    @Before
    public void setUp() {
        mMockSubscriptionManager = spy(
                Objects.requireNonNull(ApplicationProvider.getApplicationContext()
                        .getSystemService(SubscriptionManager.class)));
        mIccPhoneBook = new FakeIccPhoneBook();
        mResolver = mProviderRule.getResolver();

        TestableSimPhonebookProvider.setup(mResolver, mMockSubscriptionManager, mIccPhoneBook);
    }

    @Test
    public void query_entityFiles_returnsCursorWithCorrectProjection() {
        // Null projection
        try (Cursor cursor = mResolver.query(ElementaryFiles.CONTENT_URI, null, null,
                null)) {
            assertThat(Objects.requireNonNull(cursor).getColumnNames()).asList()
                    .containsExactlyElementsIn(
                            SimPhonebookProvider.ELEMENTARY_FILES_ALL_COLUMNS);
        }

        // Empty projection
        try (Cursor cursor = mResolver.query(ElementaryFiles.CONTENT_URI, new String[0], null,
                null)) {
            assertThat(cursor).hasColumnNames();
        }

        // Single column
        try (Cursor cursor = mResolver.query(ElementaryFiles.CONTENT_URI, new String[]{
                ElementaryFiles.EF_TYPE
        }, null, null)) {
            assertThat(cursor).hasColumnNames(ElementaryFiles.EF_TYPE);
        }

        // Duplicate column
        try (Cursor cursor = mResolver.query(ElementaryFiles.CONTENT_URI, new String[]{
                ElementaryFiles.SUBSCRIPTION_ID, ElementaryFiles.SUBSCRIPTION_ID
        }, null, null)) {
            assertThat(cursor).hasColumnNames(ElementaryFiles.SUBSCRIPTION_ID,
                    ElementaryFiles.SUBSCRIPTION_ID);
        }

        // Random order of all columns
        String[] projection = Arrays.copyOf(
                SimPhonebookProvider.ELEMENTARY_FILES_ALL_COLUMNS,
                SimPhonebookProvider.ELEMENTARY_FILES_ALL_COLUMNS.length);
        Collections.shuffle(Arrays.asList(projection));
        try (Cursor cursor = mResolver.query(ElementaryFiles.CONTENT_URI, projection, null, null)) {
            assertThat(cursor).hasColumnNames(projection);
        }
    }

    @Test
    public void query_entityFiles_unrecognizedColumn_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                mResolver.query(ElementaryFiles.CONTENT_URI, new String[]{"invalid_column"}, null,
                        null));
    }

    @Test
    public void query_entityFiles_noSim_returnsEmptyCursor() {
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(
                ImmutableList.of());

        try (Cursor cursor = mResolver.query(ElementaryFiles.CONTENT_URI, null, null, null)) {
            assertThat(cursor).hasCount(0);
        }
    }

    @Test
    public void query_entityFiles_multiSim_returnsCursorWithRowForEachSimEf() {
        setupSimsWithSubscriptionIds(2, 3, 7);

        mIccPhoneBook.setupEfWithSizes(2, IccConstants.EF_ADN, 10, 25);
        mIccPhoneBook.setupEfWithSizes(2, IccConstants.EF_FDN, 5, 20);
        mIccPhoneBook.setupEfWithSizes(2, IccConstants.EF_SDN, 15, 20);
        mIccPhoneBook.setupEfWithSizes(3, IccConstants.EF_ADN, 100, 30);
        // These Will be omitted from results because zero size indicates the EF is not supported.
        mIccPhoneBook.setupEfWithSizes(3, IccConstants.EF_FDN, 0, 0);
        mIccPhoneBook.setupEfWithSizes(3, IccConstants.EF_SDN, 0, 0);
        mIccPhoneBook.setupEfWithSizes(7, IccConstants.EF_ADN, 0, 0);
        mIccPhoneBook.setupEfWithSizes(7, IccConstants.EF_FDN, 0, 0);
        mIccPhoneBook.setupEfWithSizes(7, IccConstants.EF_SDN, 0, 0);

        String[] projection = {
                ElementaryFiles.SLOT_INDEX, ElementaryFiles.SUBSCRIPTION_ID,
                ElementaryFiles.EF_TYPE, ElementaryFiles.MAX_RECORDS,
                ElementaryFiles.NAME_MAX_LENGTH, ElementaryFiles.PHONE_NUMBER_MAX_LENGTH
        };
        try (Cursor cursor = mResolver.query(ElementaryFiles.CONTENT_URI, projection, null, null)) {
            assertThat(cursor).hasColumnNames(projection);

            assertThat(cursor)
                    .atRow(0).hasRowValues(0, 2, ElementaryFiles.EF_ADN, 10, 11, 20)
                    .atRow(1).hasRowValues(0, 2, ElementaryFiles.EF_FDN, 5, 6, 20)
                    .atRow(2).hasRowValues(0, 2, ElementaryFiles.EF_SDN, 15, 6, 20)
                    .atRow(3).hasRowValues(1, 3, ElementaryFiles.EF_ADN, 100, 16, 20);
        }
    }

    @Test
    public void query_entityFiles_simWithZeroSizes_returnsEmptyCursor() {
        setupSimsWithSubscriptionIds(1);

        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_ADN, 0, 0);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_FDN, 0, 0);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_SDN, 0, 0);

        try (Cursor cursor = mResolver.query(ElementaryFiles.CONTENT_URI, null, null, null)) {
            assertThat(cursor).hasCount(0);
        }
    }

    /**
     * USIM cards support more than 255 records by having multiple files for one EF type but
     * IIccPhoneBook.getAdnRecordsSizeForSubscriber returns the size for a single file and so is
     * inaccurate for such SIMs.
     *
     * <p>See b/201385523#comment4 and b/201685690
     */
    @Test
    public void query_entityFiles_adnRecordCountExceedsSize_returnsAdnRecordCountAsMaxRecords() {
        setupSimsWithSubscriptionIds(1);

        // There are 400 records returned by getAdnRecordsInEfForSubscriber but the count returned
        // by getAdnRecordsSizeForSubscriber is only 200.
        AdnRecord[] records = mIccPhoneBook.createEmptyRecords(IccConstants.EF_ADN, 400);
        mIccPhoneBook.setRecordsSize(1, IccConstants.EF_ADN, 200, 20);
        mIccPhoneBook.setRecords(1, IccConstants.EF_ADN, records);

        String[] projection = {
                ElementaryFiles.SUBSCRIPTION_ID, ElementaryFiles.EF_TYPE,
                ElementaryFiles.MAX_RECORDS
        };
        try (Cursor cursor = mResolver.query(
                ElementaryFiles.CONTENT_URI, projection, null, null)) {
            assertThat(cursor).hasCount(1);
            assertThat(cursor)
                    .atRow(0).hasRowValues(1, ElementaryFiles.EF_ADN, 400);
        }
    }

    @Test
    public void query_entityFilesItem_nullProjection_returnsCursorWithCorrectProjection() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        // Null projection
        try (Cursor cursor = mResolver.query(ElementaryFiles.getItemUri(1, EF_ADN), null, null,
                null)) {
            assertThat(Objects.requireNonNull(cursor).getColumnNames()).asList()
                    .containsExactlyElementsIn(
                            SimPhonebookProvider.ELEMENTARY_FILES_ALL_COLUMNS);
        }
    }

    @Test
    public void query_elementaryFilesItem_nonExistentSubscriptionId_returnsEmptyCursor() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        // Subscription ID 2 does not exist
        Uri nonExistentElementaryFileItemUri = ElementaryFiles.getItemUri(2, EF_ADN);

        try (Cursor cursor = mResolver.query(nonExistentElementaryFileItemUri, null, null, null)) {
            assertThat(Objects.requireNonNull(cursor)).hasCount(0);
        }
    }

    @Test
    public void query_adnRecords_returnsCursorWithMatchingProjection() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);
        Uri contentAdn = SimRecords.getContentUri(1, EF_ADN);

        // Null projection
        try (Cursor cursor = mResolver.query(contentAdn, null, null, null)) {
            assertThat(Objects.requireNonNull(cursor).getColumnNames()).asList()
                    .containsExactlyElementsIn(SimPhonebookProvider.SIM_RECORDS_ALL_COLUMNS);
        }

        // Empty projection
        try (Cursor cursor = mResolver.query(contentAdn, new String[0], null, null)) {
            assertThat(cursor).hasColumnNames();
        }

        // Single column
        try (Cursor cursor = mResolver.query(contentAdn, new String[]{
                SimRecords.PHONE_NUMBER
        }, null, null)) {
            assertThat(cursor).hasColumnNames(SimRecords.PHONE_NUMBER);
        }

        // Duplicate column
        try (Cursor cursor = mResolver.query(contentAdn, new String[]{
                SimRecords.PHONE_NUMBER, SimRecords.PHONE_NUMBER
        }, null, null)) {
            assertThat(cursor).hasColumnNames(SimRecords.PHONE_NUMBER, SimRecords.PHONE_NUMBER);
        }

        // Random order of all columns
        String[] projection = Arrays.copyOf(
                SimPhonebookProvider.SIM_RECORDS_ALL_COLUMNS,
                SimPhonebookProvider.SIM_RECORDS_ALL_COLUMNS.length);
        Collections.shuffle(Arrays.asList(projection));
        try (Cursor cursor = mResolver.query(contentAdn, projection, null, null)) {
            assertThat(cursor).hasColumnNames(projection);
        }
    }

    @Test
    public void query_adnRecords_invalidColumnProjection_throwsIllegalArgumentException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);
        Uri contentAdn = SimRecords.getContentUri(1, EF_ADN);

        assertThrows(IllegalArgumentException.class, () -> Closeables.close(
                mResolver.query(contentAdn, new String[] {
                        "an_unsupported_column",
                }, null, null), false)
        );

        assertThrows(IllegalArgumentException.class, () -> Closeables.close(
                mResolver.query(contentAdn, new String[] {
                        SimRecords.RECORD_NUMBER,
                        "an_unsupported_column"
                }, null, null), false)
        );

        assertThrows(IllegalArgumentException.class, () -> Closeables.close(
                mResolver.query(contentAdn, new String[] {
                        "an_unsupported_column",
                        SimRecords.RECORD_NUMBER
                }, null, null), false)
        );
    }

    @Test
    public void query_adnRecords_noRecords_returnsEmptyCursor() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        try (Cursor cursor = mResolver.query(SimRecords.getContentUri(1, EF_ADN), null, null,
                null)) {
            assertThat(cursor).hasCount(0);
        }
    }

    @Test
    public void query_simRecords_nullRecordList_returnsEmptyCursor() throws Exception {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);
        // Use a mock so that a null list can be returned
        IIccPhoneBook mockIccPhoneBook = mock(
                IIccPhoneBook.class, AdditionalAnswers.delegatesTo(mIccPhoneBook));
        when(mockIccPhoneBook.getAdnRecordsInEfForSubscriber(anyInt(), anyInt())).thenReturn(null);
        TestableSimPhonebookProvider.setup(mResolver, mMockSubscriptionManager, mockIccPhoneBook);

        try (Cursor adnCursor = mResolver.query(SimRecords.getContentUri(1, EF_ADN), null, null,
                null);
             Cursor fdnCursor = mResolver.query(SimRecords.getContentUri(1, EF_FDN), null, null,
                     null);
             Cursor sdnCursor = mResolver.query(SimRecords.getContentUri(1, EF_SDN), null, null,
                     null)
        ) {
            assertThat(adnCursor).hasCount(0);
            assertThat(fdnCursor).hasCount(0);
            assertThat(sdnCursor).hasCount(0);
        }
    }

    @Test
    public void query_simRecords_singleSim_returnsDataForCorrectEf() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.addRecord(1, IccConstants.EF_ADN, "Person Adn1", "8005550101");
        mIccPhoneBook.addRecord(1, IccConstants.EF_ADN, "Person Adn2", "8005550102");
        mIccPhoneBook.addRecord(1, IccConstants.EF_FDN, "Person Fdn", "8005550103");
        mIccPhoneBook.addRecord(1, IccConstants.EF_SDN, "Person Sdn", "8005550104");
        mIccPhoneBook.setDefaultSubscriptionId(1);

        String[] projection = {
                SimRecords.SUBSCRIPTION_ID,
                SimRecords.ELEMENTARY_FILE_TYPE,
                SimRecords.RECORD_NUMBER,
                SimRecords.NAME,
                SimRecords.PHONE_NUMBER
        };
        try (Cursor adnCursor = mResolver.query(SimRecords.getContentUri(1, EF_ADN),
                projection, null, null);
             Cursor fdnCursor = mResolver.query(SimRecords.getContentUri(1, EF_FDN),
                     projection, null, null);
             Cursor sdnCursor = mResolver.query(SimRecords.getContentUri(1, EF_SDN),
                     projection, null, null)
        ) {

            assertThat(adnCursor)
                    .atRow(0).hasRowValues(1, ElementaryFiles.EF_ADN, 1, "Person Adn1",
                    "8005550101")
                    .atRow(1).hasRowValues(1, ElementaryFiles.EF_ADN, 2, "Person Adn2",
                    "8005550102");
            assertThat(fdnCursor)
                    .atRow(0).hasRowValues(1, ElementaryFiles.EF_FDN, 1, "Person Fdn",
                    "8005550103");
            assertThat(sdnCursor)
                    .atRow(0).hasRowValues(1, ElementaryFiles.EF_SDN, 1, "Person Sdn",
                    "8005550104");
        }
    }

    @Test
    public void query_adnRecords_returnsAdnData() {
        setupSimsWithSubscriptionIds(1, 2, 4);
        mIccPhoneBook.addRecord(1, IccConstants.EF_ADN, "Person Sim1", "8005550101");
        mIccPhoneBook.addRecord(1, IccConstants.EF_FDN, "Omitted Sim1", "8005550199");
        mIccPhoneBook.addRecord(2, IccConstants.EF_ADN, "Person Sim2a", "8005550103");
        mIccPhoneBook.addRecord(2, IccConstants.EF_ADN, "Person Sim2b", "8005550104");
        mIccPhoneBook.addRecord(2, IccConstants.EF_ADN, "Person Sim2c", "8005550105");
        mIccPhoneBook.addRecord(2, IccConstants.EF_SDN, "Omitted Sim2", "8005550198");
        mIccPhoneBook.addRecord(4, IccConstants.EF_ADN, "Person Sim4", "8005550106");
        mIccPhoneBook.setDefaultSubscriptionId(1);

        String[] projection = {
                SimRecords.SUBSCRIPTION_ID,
                SimRecords.ELEMENTARY_FILE_TYPE,
                SimRecords.RECORD_NUMBER,
                SimRecords.NAME,
                SimRecords.PHONE_NUMBER
        };
        try (Cursor cursorSim1 = mResolver.query(SimRecords.getContentUri(1, EF_ADN),
                projection, null, null);
             Cursor cursorSim2 = mResolver.query(SimRecords.getContentUri(2, EF_ADN),
                     projection, null, null);
             Cursor cursorSim4 = mResolver.query(SimRecords.getContentUri(4, EF_ADN),
                     projection, null, null)
        ) {

            assertThat(cursorSim1).hasData(new Object[][]{
                    {1, ElementaryFiles.EF_ADN, 1, "Person Sim1", "8005550101"},
            });
            assertThat(cursorSim2).hasData(new Object[][]{
                    {2, ElementaryFiles.EF_ADN, 1, "Person Sim2a", "8005550103"},
                    {2, ElementaryFiles.EF_ADN, 2, "Person Sim2b", "8005550104"},
                    {2, ElementaryFiles.EF_ADN, 3, "Person Sim2c", "8005550105"},
            });
            assertThat(cursorSim4).hasData(new Object[][]{
                    {4, ElementaryFiles.EF_ADN, 1, "Person Sim4", "8005550106"},
            });
        }
    }

    @Test
    public void query_fdnRecords_returnsFdnData() {
        setupSimsWithSubscriptionIds(1, 2, 4);
        mIccPhoneBook.makeAllEfsSupported(1, 2, 4);
        mIccPhoneBook.addRecord(1, IccConstants.EF_ADN, "Person Sim1", "8005550101");
        mIccPhoneBook.addRecord(2, IccConstants.EF_ADN, "Person Sim2a", "8005550103");
        mIccPhoneBook.addRecord(2, IccConstants.EF_FDN, "Person Sim2b", "8005550104");
        mIccPhoneBook.addRecord(2, IccConstants.EF_FDN, "Person Sim2c", "8005550105");
        mIccPhoneBook.addRecord(4, IccConstants.EF_SDN, "Person Sim4", "8005550106");
        mIccPhoneBook.setDefaultSubscriptionId(1);

        String[] projection = {
                SimRecords.SUBSCRIPTION_ID,
                SimRecords.ELEMENTARY_FILE_TYPE,
                SimRecords.RECORD_NUMBER,
                SimRecords.NAME,
                SimRecords.PHONE_NUMBER
        };
        try (Cursor cursorSim1Fdn = mResolver.query(SimRecords.getContentUri(1, EF_FDN),
                projection, null, null);
             Cursor cursorSim2Fdn = mResolver.query(SimRecords.getContentUri(2, EF_FDN),
                     projection, null, null);
             Cursor cursorSim4Fdn = mResolver.query(SimRecords.getContentUri(4, EF_FDN),
                     projection, null, null)
        ) {

            assertThat(cursorSim1Fdn).hasCount(0);
            assertThat(cursorSim2Fdn).hasData(new Object[][]{
                    {2, ElementaryFiles.EF_FDN, 1, "Person Sim2b", "8005550104"},
                    {2, ElementaryFiles.EF_FDN, 2, "Person Sim2c", "8005550105"},
            });
            assertThat(cursorSim4Fdn).hasCount(0);
        }
    }

    @Test
    public void query_sdnRecords_returnsSdnData() {
        setupSimsWithSubscriptionIds(1, 2, 4);
        mIccPhoneBook.makeAllEfsSupported(1, 2, 4);
        mIccPhoneBook.addRecord(1, IccConstants.EF_ADN, "Person Adn1", "8005550101");
        mIccPhoneBook.addRecord(1, IccConstants.EF_FDN, "Person Fdn1", "8005550102");
        mIccPhoneBook.addRecord(1, IccConstants.EF_SDN, "Person Sdn1", "8005550103");
        mIccPhoneBook.addRecord(2, IccConstants.EF_ADN, "Person Adn2a", "8005550104");
        mIccPhoneBook.addRecord(2, IccConstants.EF_FDN, "Person Fdn2b", "8005550105");
        mIccPhoneBook.addRecord(4, IccConstants.EF_SDN, "Person Sdn4a", "8005550106");
        mIccPhoneBook.addRecord(4, IccConstants.EF_SDN, "Person Sdn4b", "8005550107");
        mIccPhoneBook.setDefaultSubscriptionId(1);

        String[] projection = {
                SimRecords.SUBSCRIPTION_ID,
                SimRecords.ELEMENTARY_FILE_TYPE,
                SimRecords.RECORD_NUMBER,
                SimRecords.NAME,
                SimRecords.PHONE_NUMBER
        };
        try (Cursor cursorSim1Sdn = mResolver.query(SimRecords.getContentUri(1, EF_SDN),
                projection, null, null);
             Cursor cursorSim2Sdn = mResolver.query(SimRecords.getContentUri(2, EF_SDN),
                     projection, null, null);
             Cursor cursorSim4Sdn = mResolver.query(SimRecords.getContentUri(4, EF_SDN),
                     projection, null, null)
        ) {

            assertThat(cursorSim1Sdn)
                    .atRow(0).hasRowValues(1, ElementaryFiles.EF_SDN, 1, "Person Sdn1",
                    "8005550103");
            assertThat(cursorSim2Sdn).hasCount(0);
            assertThat(cursorSim4Sdn)
                    .atRow(0).hasRowValues(4, ElementaryFiles.EF_SDN, 1, "Person Sdn4a",
                    "8005550106")
                    .atRow(1).hasRowValues(4, ElementaryFiles.EF_SDN, 2, "Person Sdn4b",
                    "8005550107");
        }
    }

    @Test
    public void query_adnRecords_nonExistentSim_throwsCorrectException() {
        setupSimsWithSubscriptionIds(1);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.query(SimRecords.getContentUri(123, EF_ADN), null, null, null));
        assertThat(e).hasMessageThat().isEqualTo("No active SIM with subscription ID 123");
    }

    @Test
    public void insert_nonExistentSim_throwsCorrectException() {
        setupSimsWithSubscriptionIds(1);
        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        values.put(SimRecords.PHONE_NUMBER, "123");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.insert(SimRecords.getContentUri(123, EF_ADN), values));
        assertThat(e).hasMessageThat().isEqualTo("No active SIM with subscription ID 123");
    }

    @Test
    public void update_nonExistentSim_throwsCorrectException() {
        setupSimsWithSubscriptionIds(1);
        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        values.put(SimRecords.PHONE_NUMBER, "123");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.update(SimRecords.getItemUri(123, EF_ADN, 1), values, null));
        assertThat(e).hasMessageThat().isEqualTo("No active SIM with subscription ID 123");
    }

    @Test
    public void delete_nonExistentSim_throwsCorrectException() {
        setupSimsWithSubscriptionIds(1);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.delete(SimRecords.getItemUri(123, EF_ADN, 1), null));
        assertThat(e).hasMessageThat().isEqualTo("No active SIM with subscription ID 123");
    }

    @Test
    public void query_adnRecords_zeroSizeEf_throwsCorrectException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_ADN, 0, 0);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.query(SimRecords.getContentUri(1, EF_ADN), null, null, null));
        assertThat(e).hasMessageThat().isEqualTo(
                "adn is not supported for SIM with subscription ID 1");
    }

    @Test
    public void query_itemUri_returnsCorrectRow() {
        setupSimsWithSubscriptionIds(1, 2);
        mIccPhoneBook.addRecord(1,
                new AdnRecord(IccConstants.EF_ADN, 1, "Name@Adn1[1]", "8005550101"));
        mIccPhoneBook.addRecord(1,
                new AdnRecord(IccConstants.EF_ADN, 2, "Name@Adn1[2]", "8005550102"));
        mIccPhoneBook.addRecord(1,
                new AdnRecord(IccConstants.EF_ADN, 3, "Name@Adn1[3]", "8005550103"));
        mIccPhoneBook.addRecord(2,
                new AdnRecord(IccConstants.EF_ADN, 3, "Name@Adn2[3]", "8005550104"));
        mIccPhoneBook.addRecord(1,
                new AdnRecord(IccConstants.EF_FDN, 1, "Name@Fdn1[1]", "8005550105"));
        mIccPhoneBook.addRecord(2,
                new AdnRecord(IccConstants.EF_SDN, 1, "Name@Sdn2[1]", "8005550106"));

        String[] projection = {
                SimRecords.SUBSCRIPTION_ID, SimRecords.ELEMENTARY_FILE_TYPE,
                SimRecords.RECORD_NUMBER, SimRecords.NAME, SimRecords.PHONE_NUMBER
        };
        try (Cursor item1 = mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 1),
                projection, null, null);
             Cursor item2 = mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 3),
                     projection, null, null);
             Cursor item3 = mResolver.query(SimRecords.getItemUri(2, ElementaryFiles.EF_ADN, 3),
                     projection, null, null);
             Cursor item4 = mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_FDN, 1),
                     projection, null, null);
             Cursor item5 = mResolver.query(SimRecords.getItemUri(2, ElementaryFiles.EF_SDN, 1),
                     projection, null, null)
        ) {
            assertThat(item1).hasSingleRow(1, ElementaryFiles.EF_ADN, 1, "Name@Adn1[1]",
                    "8005550101");
            assertThat(item2).hasSingleRow(1, ElementaryFiles.EF_ADN, 3, "Name@Adn1[3]",
                    "8005550103");
            assertThat(item3).hasSingleRow(2, ElementaryFiles.EF_ADN, 3, "Name@Adn2[3]",
                    "8005550104");
            assertThat(item4).hasSingleRow(1, ElementaryFiles.EF_FDN, 1, "Name@Fdn1[1]",
                    "8005550105");
            assertThat(item5).hasSingleRow(2, ElementaryFiles.EF_SDN, 1, "Name@Sdn2[1]",
                    "8005550106");
        }
    }

    @Test
    public void query_itemUriNullProjection_returnsCursorWithAllColumns() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        try (Cursor cursor = mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 1),
                null, null, null)
        ) {
            assertThat(Objects.requireNonNull(
                    cursor).getColumnNames()).asList().containsExactlyElementsIn(
                    SimPhonebookProvider.SIM_RECORDS_ALL_COLUMNS);
        }
    }

    @Test
    public void query_itemUriEmptyRecord_returnsEmptyCursor() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_ADN, 1, 30);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_FDN, 1, 30);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_SDN, 1, 30);

        try (Cursor adnItem = mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 1),
                null, null, null);
             Cursor fdnItem = mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_FDN, 1),
                     null, null, null);
             Cursor sdnItem = mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_SDN, 1),
                     null, null, null)
        ) {
            assertThat(adnItem).hasCount(0);
            assertThat(fdnItem).hasCount(0);
            assertThat(sdnItem).hasCount(0);
        }
    }

    @Test
    public void query_itemUriIndexExceedsMax_returnsEmptyCursor() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_ADN, 1, 30);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_FDN, 1, 30);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_SDN, 1, 30);

        try (Cursor adnItem = mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 2),
                null, null, null);
             Cursor fdnItem = mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_FDN, 2),
                     null, null, null);
             Cursor sdnItem = mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_SDN, 2),
                     null, null, null)
        ) {
            assertThat(adnItem).hasCount(0);
            assertThat(fdnItem).hasCount(0);
            assertThat(sdnItem).hasCount(0);
        }
    }

    @Test
    public void query_invalidItemIndex_throwsIllegalArgumentException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        assertThrows(IllegalArgumentException.class, () ->
                mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, -1),
                        null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_FDN, -1),
                        null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_SDN, -1),
                        null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 0),
                        null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_FDN, 0),
                        null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                mResolver.query(SimRecords.getItemUri(1, ElementaryFiles.EF_SDN, 0),
                        null, null, null));
    }

    @Test
    public void insert_adnRecord_addsAdnRecordAndReturnsUriForNewRecord() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "First Last");
        values.put(SimRecords.PHONE_NUMBER, "8005550101");

        Uri uri = mResolver.insert(SimRecords.getContentUri(1, EF_ADN), values);

        List<AdnRecord> records = mIccPhoneBook.getAdnRecordsInEfForSubscriber(
                1, IccConstants.EF_ADN).stream()
                .filter(((Predicate<AdnRecord>) AdnRecord::isEmpty).negate())
                .collect(Collectors.toList());

        assertThat(records)
                .comparingElementsUsing(ADN_RECORD_IS_EQUAL)
                .containsExactly(new AdnRecord(IccConstants.EF_ADN, 1, "First Last", "8005550101"));

        assertThat(uri).isEqualTo(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 1));
    }

    @Test
    public void insert_adnRecordWithExistingRecords_returnsUriWithCorrectIndex() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.setDefaultSubscriptionId(1);
        mIccPhoneBook.addRecord(new AdnRecord(IccConstants.EF_ADN, 2, "Existing1", "8005550101"));
        mIccPhoneBook.addRecord(new AdnRecord(IccConstants.EF_ADN, 3, "Existing2", "8005550102"));
        mIccPhoneBook.addRecord(new AdnRecord(IccConstants.EF_ADN, 5, "Existing3", "8005550103"));

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "New1");
        values.put(SimRecords.PHONE_NUMBER, "8005550104");

        Uri insert1 = mResolver.insert(SimRecords.getContentUri(1, EF_ADN), values);
        values.put(SimRecords.NAME, "New2");
        values.put(SimRecords.PHONE_NUMBER, "8005550105");
        Uri insert2 = mResolver.insert(SimRecords.getContentUri(1, EF_ADN), values);
        values.put(SimRecords.NAME, "New3");
        values.put(SimRecords.PHONE_NUMBER, "8005550106");
        Uri insert3 = mResolver.insert(SimRecords.getContentUri(1, EF_ADN), values);

        assertThat(
                mIccPhoneBook.getAdnRecordsInEfForSubscriber(1, IccConstants.EF_ADN).subList(0, 6))
                .comparingElementsUsing(ADN_RECORD_IS_EQUAL)
                .containsExactly(
                        new AdnRecord(IccConstants.EF_ADN, 1, "New1", "8005550104"),
                        new AdnRecord(IccConstants.EF_ADN, 2, "Existing1", "8005550101"),
                        new AdnRecord(IccConstants.EF_ADN, 3, "Existing2", "8005550102"),
                        new AdnRecord(IccConstants.EF_ADN, 4, "New2", "8005550105"),
                        new AdnRecord(IccConstants.EF_ADN, 5, "Existing3", "8005550103"),
                        new AdnRecord(IccConstants.EF_ADN, 6, "New3", "8005550106"));
        assertThat(insert1).isEqualTo(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 1));
        assertThat(insert2).isEqualTo(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 4));
        assertThat(insert3).isEqualTo(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 6));
    }

    @Test
    public void insert_efFull_throwsCorrectException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_ADN, 1, 30);
        mIccPhoneBook.addRecord(1, IccConstants.EF_ADN, "Existing", "8005550101");

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "New");
        values.put(SimRecords.PHONE_NUMBER, "8005550102");

        Uri uri = SimRecords.getContentUri(1, EF_ADN);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> mResolver.insert(uri, values));
        assertThat(e).hasMessageThat().isEqualTo(
                uri + " is full. Please delete records to add new ones.");
    }

    @Test
    public void insert_nameWithNonGsmCharacters_addsAdnRecord() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        ContentValues values = new ContentValues();
        String name = "abc日本" + EMOJI;
        values.put(SimRecords.NAME, name);
        values.put(SimRecords.PHONE_NUMBER, "8005550101");

        Uri uri = mResolver.insert(SimRecords.getContentUri(1, EF_ADN), values);

        List<AdnRecord> records = mIccPhoneBook.getAdnRecordsInEfForSubscriber(
                1, IccConstants.EF_ADN).stream()
                .filter(((Predicate<AdnRecord>) AdnRecord::isEmpty).negate())
                .collect(Collectors.toList());

        assertThat(records)
                .comparingElementsUsing(ADN_RECORD_IS_EQUAL)
                .containsExactly(new AdnRecord(IccConstants.EF_ADN, 1, name, "8005550101"));

        assertThat(uri).isEqualTo(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 1));
    }

    @Test
    public void insert_nullValues_returnsNull() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        Uri result = mResolver.insert(SimRecords.getContentUri(1, EF_ADN), null);

        assertThat(result).isNull();
    }

    @Test
    public void update_nullValues_returnsZero() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);
        mIccPhoneBook.addAdnRecord(1, "Name", "5550101");

        int result = mResolver.update(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 1), null,
                null);

        assertThat(result).isEqualTo(0);
    }

    @Test
    public void insert_emptyValues_returnsNull() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        Uri result = mResolver.insert(SimRecords.getContentUri(1, EF_ADN), new ContentValues());

        assertThat(result).isNull();
    }

    @Test
    public void insert_nameOmitted_createsRecordWithJustPhoneNumber() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        ContentValues values = new ContentValues();
        // No name
        values.put(SimRecords.PHONE_NUMBER, "18005550101");

        mResolver.insert(SimRecords.getContentUri(1, EF_ADN), values);

        // Null name
        values.putNull(SimRecords.NAME);
        values.put(SimRecords.PHONE_NUMBER, "18005550102");
        mResolver.insert(SimRecords.getContentUri(1, EF_ADN), values);

        // Empty name
        values.put(SimRecords.NAME, "");
        values.put(SimRecords.PHONE_NUMBER, "18005550103");
        mResolver.insert(SimRecords.getContentUri(1, EF_ADN), values);

        assertThat(mIccPhoneBook.getAllValidRecords())
                .comparingElementsUsing(ADN_RECORD_IS_EQUAL)
                .containsExactly(
                        new AdnRecord(IccConstants.EF_ADN, 1, "", "18005550101"),
                        new AdnRecord(IccConstants.EF_ADN, 2, "", "18005550102"),
                        new AdnRecord(IccConstants.EF_ADN, 3, "", "18005550103"));
    }

    @Test
    public void insert_phoneNumberOmitted_throwsCorrectException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_ADN, 1, 25);

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.insert(SimRecords.getContentUri(1, EF_ADN), values));
        assertThat(e).hasMessageThat().isEqualTo(SimRecords.PHONE_NUMBER + " is required.");
    }

    @Test
    public void insert_nameTooLong_throwsCorrectException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_ADN, 1, 25);

        ContentValues values = new ContentValues();
        // Name is limited to 11 characters when the max record size is 25
        values.put(SimRecords.NAME, "1234567890ab");
        values.put(SimRecords.PHONE_NUMBER, "8005550102");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.insert(SimRecords.getContentUri(1, EF_ADN), values));

        assertThat(e).hasMessageThat().isEqualTo(SimRecords.NAME + " is too long.");

        // 2 bytes per character and 4 for the emoji. So this is 14 characters long.
        values.put(SimRecords.NAME, "abc日本" + EMOJI);
        e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.insert(SimRecords.getContentUri(1, EF_ADN), values));

        assertThat(e).hasMessageThat().isEqualTo(SimRecords.NAME + " is too long.");
    }

    @Test
    public void insert_phoneNumberTooLong_throwsCorrectException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_ADN, 1, 25);

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        // 21 digits is longer than max of 20
        values.put(SimRecords.PHONE_NUMBER, "123456789012345678901");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.insert(SimRecords.getContentUri(1, EF_ADN), values));

        assertThat(e).hasMessageThat().isEqualTo(SimRecords.PHONE_NUMBER + " is too long.");
    }

    @Test
    public void insert_numberWithInvalidCharacters_throwsCorrectException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_ADN, 1, 32);

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        values.put(SimRecords.PHONE_NUMBER, "(800)555-0190 x7777");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.insert(SimRecords.getContentUri(1, ElementaryFiles.EF_ADN),
                        values,
                        null));
        assertThat(e).hasMessageThat().isEqualTo(
                SimRecords.PHONE_NUMBER + " contains unsupported characters.");

        // The insert didn't actually change the data.
        assertThat(mIccPhoneBook.getAllValidRecords()).isEmpty();
    }

    @Test
    public void insert_unsupportedColumn_throwsCorrectException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_ADN, 1, 25);

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        values.put(SimRecords.PHONE_NUMBER, "18005550101");
        values.put(SimRecords.RECORD_NUMBER, 8);
        values.put("extra_phone2", "18005550102");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.insert(SimRecords.getContentUri(1, EF_ADN), values));
        assertThat(e).hasMessageThat().isEqualTo("Unsupported columns: "
                + SimRecords.RECORD_NUMBER + ",extra_phone2");
    }

    @Test
    public void update_existingRecord_updatesRecord() {
        setupSimsWithSubscriptionIds(1, 2);
        AdnRecord[] unchanged = new AdnRecord[]{
                new AdnRecord(IccConstants.EF_ADN, 3, "Other1", "8005550102"),
                new AdnRecord(IccConstants.EF_ADN, 2, "Other2", "8005550103"),
                new AdnRecord(IccConstants.EF_FDN, 2, "Other3", "8005550104")
        };
        mIccPhoneBook.addRecord(1, unchanged[0]);
        mIccPhoneBook.addRecord(2, unchanged[1]);
        mIccPhoneBook.addRecord(2, unchanged[2]);
        mIccPhoneBook.addRecord(1,
                new AdnRecord(IccConstants.EF_ADN, 2, "Initial Name", "8005550101"));

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Updated Name");
        values.put(SimRecords.PHONE_NUMBER, "8005550105");

        int result = mResolver.update(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 2), values,
                null);

        assertThat(result).isEqualTo(1);

        List<AdnRecord> finalRecords = mIccPhoneBook.getAllValidRecords();

        assertThat(finalRecords).comparingElementsUsing(ADN_RECORD_IS_EQUAL)
                .containsAtLeastElementsIn(unchanged);
        assertThat(finalRecords).comparingElementsUsing(ADN_RECORD_IS_EQUAL)
                .doesNotContain(
                        new AdnRecord(IccConstants.EF_ADN, 2, "Initial Name", "80005550101"));
        assertThat(finalRecords).comparingElementsUsing(ADN_RECORD_IS_EQUAL)
                .contains(new AdnRecord(IccConstants.EF_ADN, 2, "Updated Name", "8005550105"));
    }

    @Test
    public void update_emptyRecord_updatesRecord() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "name");
        values.put(SimRecords.PHONE_NUMBER, "18005550101");
        // No record actually exists with record number 10 but we allow clients to update it
        // as a way to set the information at a specific record number.
        int result = mResolver.update(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 10),
                values, null);

        assertThat(result).isEqualTo(1);
        List<AdnRecord> finalRecords = mIccPhoneBook.getAllValidRecords();
        assertThat(finalRecords).comparingElementsUsing(ADN_RECORD_IS_EQUAL)
                .containsExactly(new AdnRecord(IccConstants.EF_ADN, 10, "name", "18005550101"));
    }

    @Test
    public void delete_existingRecord_deletesRecord() {
        setupSimsWithSubscriptionIds(1, 2);
        AdnRecord[] unchanged = new AdnRecord[]{
                new AdnRecord(IccConstants.EF_ADN, 3, "Other1", "8005550102"),
                new AdnRecord(IccConstants.EF_ADN, 2, "Other2", "8005550103"),
                new AdnRecord(IccConstants.EF_FDN, 2, "Other3", "8005550104")
        };
        mIccPhoneBook.addRecord(1,
                new AdnRecord(IccConstants.EF_ADN, 2, "Initial Name", "8005550101"));
        mIccPhoneBook.addRecord(1, unchanged[0]);
        mIccPhoneBook.addRecord(2, unchanged[1]);
        mIccPhoneBook.addRecord(2, unchanged[2]);

        int result = mResolver.delete(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 2), null);

        assertThat(result).isEqualTo(1);

        assertThat(mIccPhoneBook.getAllValidRecords()).comparingElementsUsing(ADN_RECORD_IS_EQUAL)
                .containsExactlyElementsIn(unchanged);
    }

    @Test
    public void update_indexExceedingMax_returnsZero() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_ADN, 1, 30);

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "name");
        values.put(SimRecords.PHONE_NUMBER, "18005551010");
        int result = mResolver.update(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 2),
                values, null);

        assertThat(result).isEqualTo(0);
    }

    @Test
    public void update_indexOverflow_throwsIllegalArgumentException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "name");
        values.put(SimRecords.PHONE_NUMBER, "18005551010");
        assertThrows(IllegalArgumentException.class, () -> mResolver.update(
                SimRecords.getContentUri(1, EF_ADN).buildUpon().appendPath(
                        String.valueOf((Long.MAX_VALUE))).build(),
                values, null));
    }

    @Test
    public void delete_emptyRecord_returnsZero() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        int result = mResolver.delete(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 2), null);

        assertThat(result).isEqualTo(0);
    }

    @Test
    public void delete_indexExceedingMax_returnsZero() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_ADN, 1, 30);

        int result = mResolver.delete(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 2), null);

        assertThat(result).isEqualTo(0);
    }

    @Test
    public void delete_indexOverflow_throwsIllegalArgumentException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        assertThrows(IllegalArgumentException.class, () -> mResolver.delete(
                SimRecords.getContentUri(1, EF_ADN).buildUpon().appendPath(
                        String.valueOf((Long.MAX_VALUE))).build(),
                null));
    }

    @Test
    public void update_nameOrNumberTooLong_throwsCorrectException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_ADN, 1, 25);
        mIccPhoneBook.addRecord(1, IccConstants.EF_ADN, "Initial", "8005550101");

        ContentValues values = new ContentValues();
        // Name is limited to 11 characters
        values.put(SimRecords.NAME, "1234567890ab");
        values.put(SimRecords.PHONE_NUMBER, "8005550102");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.update(SimRecords.getItemUri(
                        1, ElementaryFiles.EF_ADN, 1), values, null));
        assertThat(e).hasMessageThat().isEqualTo(SimRecords.NAME + " is too long.");

        values.put(SimRecords.NAME, "abc");
        values.put(SimRecords.PHONE_NUMBER, "123456789012345678901");

        e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.update(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 1),
                        values,
                        null));
        assertThat(e).hasMessageThat().isEqualTo(SimRecords.PHONE_NUMBER + " is too long.");
        // The updates didn't actually change the data
        assertThat(mIccPhoneBook.getAllValidRecords())
                .comparingElementsUsing(Correspondence.from(AdnRecord::isEqual, "isEqual"))
                .containsExactly(new AdnRecord(IccConstants.EF_ADN, 1, "Initial", "8005550101"));
    }

    @Test
    public void update_numberWithInvalidCharacters_throwsCorrectException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.setupEfWithSizes(1, IccConstants.EF_ADN, 1, 32);
        mIccPhoneBook.addRecord(1, IccConstants.EF_ADN, "Initial", "8005550101");

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        values.put(SimRecords.PHONE_NUMBER, "(800)555-0190 x7777");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.update(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 1),
                        values,
                        null));
        assertThat(e).hasMessageThat().isEqualTo(
                SimRecords.PHONE_NUMBER + " contains unsupported characters.");

        // The update didn't actually change the data.
        assertThat(mIccPhoneBook.getAllValidRecords())
                .comparingElementsUsing(Correspondence.from(AdnRecord::isEqual, "isEqual"))
                .containsExactly(new AdnRecord(IccConstants.EF_ADN, 1, "Initial", "8005550101"));
    }

    @Test
    public void insert_nonAdnDirUris_throwsUnsupportedOperationException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        values.put(SimRecords.PHONE_NUMBER, "8005550101");

        assertThrows(UnsupportedOperationException.class, () ->
                mResolver.insert(ElementaryFiles.CONTENT_URI, values));
        assertThrows(UnsupportedOperationException.class,
                () -> mResolver.insert(SimRecords.getContentUri(1, EF_FDN), values));
        assertThrows(UnsupportedOperationException.class,
                () -> mResolver.insert(SimRecords.getContentUri(1, EF_SDN), values));
        assertThrows(UnsupportedOperationException.class, () ->
                mResolver.insert(SimRecords.getItemUri(1, ElementaryFiles.EF_FDN, 1), values));
        assertThrows(UnsupportedOperationException.class, () ->
                mResolver.insert(SimRecords.getItemUri(1, ElementaryFiles.EF_SDN, 1), values));
    }

    @Test
    public void update_nonAdnDirUris_throwsUnsupportedOperationException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        values.put(SimRecords.PHONE_NUMBER, "8005550101");

        assertThrows(UnsupportedOperationException.class, () ->
                mResolver.update(ElementaryFiles.CONTENT_URI, values, null));
        assertThrows(UnsupportedOperationException.class,
                () -> mResolver.update(SimRecords.getContentUri(1, EF_FDN), values, null));
        assertThrows(UnsupportedOperationException.class,
                () -> mResolver.update(SimRecords.getContentUri(1, EF_SDN), values, null));
        assertThrows(UnsupportedOperationException.class,
                () -> mResolver.update(SimRecords.getContentUri(1, EF_SDN), values, null));
        assertThrows(UnsupportedOperationException.class, () ->
                mResolver.update(SimRecords.getItemUri(1, ElementaryFiles.EF_FDN, 1), values,
                        null));
        assertThrows(UnsupportedOperationException.class, () ->
                mResolver.update(SimRecords.getItemUri(1, ElementaryFiles.EF_SDN, 1), values,
                        null));
    }

    @Test
    public void delete_nonAdnDirUris_throwsUnsupportedOperationException() {
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        values.put(SimRecords.PHONE_NUMBER, "8005550101");

        assertThrows(UnsupportedOperationException.class, () ->
                mResolver.delete(ElementaryFiles.CONTENT_URI, null));
        assertThrows(UnsupportedOperationException.class,
                () -> mResolver.delete(SimRecords.getContentUri(1, EF_FDN), null));
        assertThrows(UnsupportedOperationException.class,
                () -> mResolver.delete(SimRecords.getContentUri(1, EF_SDN), null));
        assertThrows(UnsupportedOperationException.class,
                () -> mResolver.delete(SimRecords.getContentUri(1, EF_SDN), null));
        assertThrows(UnsupportedOperationException.class, () ->
                mResolver.delete(SimRecords.getItemUri(1, ElementaryFiles.EF_FDN, 1), null));
        assertThrows(UnsupportedOperationException.class, () ->
                mResolver.delete(SimRecords.getItemUri(1, ElementaryFiles.EF_SDN, 1), null));
    }

    @Test
    public void subscriptionsChange_callsNotifyChange() {
        // Clear invocations that happened in setUp
        Mockito.reset(mMockSubscriptionManager);
        // Stubbing this prevents the spied instance from calling the listener when it is added
        // which may cause flakiness.
        doNothing().when(mMockSubscriptionManager)
                .addOnSubscriptionsChangedListener(any(), any());
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);
        SimPhonebookProvider.ContentNotifier mockNotifier = mock(
                SimPhonebookProvider.ContentNotifier.class);
        ArgumentCaptor<SubscriptionManager.OnSubscriptionsChangedListener> listenerCaptor =
                ArgumentCaptor.forClass(SubscriptionManager.OnSubscriptionsChangedListener.class);

        TestableSimPhonebookProvider.setup(
                mResolver, mMockSubscriptionManager, mIccPhoneBook, mockNotifier);
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), listenerCaptor.capture());

        // Fake the initial call that is made by SubscriptionManager when a listener is registered
        // with addOnSubscriptionsChangedListener
        listenerCaptor.getValue().onSubscriptionsChanged();

        // First subscription change
        setupSimsWithSubscriptionIds(1, 2);
        listenerCaptor.getValue().onSubscriptionsChanged();

        // Second subscription change
        setupSimsWithSubscriptionIds(1);
        listenerCaptor.getValue().onSubscriptionsChanged();

        // Listener is called but subscriptions didn't change so this won't notify
        listenerCaptor.getValue().onSubscriptionsChanged();

        verify(mockNotifier, times(2)).notifyChange(eq(SimPhonebookContract.AUTHORITY_URI));
    }

    @Test
    public void insert_callsNotifyChange() {
        // Clear invocations that happened in setUp
        Mockito.reset(mMockSubscriptionManager);
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.makeAllEfsSupported(1);
        SimPhonebookProvider.ContentNotifier mockNotifier = mock(
                SimPhonebookProvider.ContentNotifier.class);

        TestableSimPhonebookProvider.setup(
                mResolver, mMockSubscriptionManager, mIccPhoneBook, mockNotifier);

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "name");
        values.put(SimRecords.PHONE_NUMBER, "5550101");
        mResolver.insert(SimRecords.getContentUri(1, EF_ADN), values);

        verify(mockNotifier).notifyChange(eq(SimPhonebookContract.AUTHORITY_URI));
    }

    @Test
    public void update_callsNotifyChange() {
        // Clear invocations that happened in setUp
        Mockito.reset(mMockSubscriptionManager);
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.addAdnRecord(1, "Initial", "5550101");
        SimPhonebookProvider.ContentNotifier mockNotifier = mock(
                SimPhonebookProvider.ContentNotifier.class);

        TestableSimPhonebookProvider.setup(
                mResolver, mMockSubscriptionManager, mIccPhoneBook, mockNotifier);

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Updated");
        values.put(SimRecords.PHONE_NUMBER, "5550102");
        mResolver.update(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 1), values, null);

        verify(mockNotifier).notifyChange(eq(SimPhonebookContract.AUTHORITY_URI));
    }

    @Test
    public void delete_callsNotifyChange() {
        // Clear invocations that happened in setUp
        Mockito.reset(mMockSubscriptionManager);
        setupSimsWithSubscriptionIds(1);
        mIccPhoneBook.addAdnRecord(1, "Initial", "5550101");
        SimPhonebookProvider.ContentNotifier mockNotifier = mock(
                SimPhonebookProvider.ContentNotifier.class);

        TestableSimPhonebookProvider.setup(
                mResolver, mMockSubscriptionManager, mIccPhoneBook, mockNotifier);

        mResolver.delete(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 1), null);

        verify(mockNotifier).notifyChange(eq(SimPhonebookContract.AUTHORITY_URI));
    }

    @Test
    public void getEncodedNameLength_returnsValueIsCorrect() {
        String name = "";
        int length = SimRecords.getEncodedNameLength(mResolver, name);
        assertThat(length).isEqualTo(0);

        name = "First Last";
        length = SimRecords.getEncodedNameLength(mResolver, name);
        assertThat(length).isEqualTo(name.length());

        name = "日本";
        length = SimRecords.getEncodedNameLength(mResolver, name);
        assertThat(length).isEqualTo(name.length() * 2 + 1);

        name = EMOJI;
        length = SimRecords.getEncodedNameLength(mResolver, name);
        assertThat(length).isEqualTo(name.length() * 2 + 1);

        name = "abc日本" + EMOJI;
        length = SimRecords.getEncodedNameLength(mResolver, name);
        assertThat(length).isEqualTo(name.length() * 2 + 1);
    }

    private void setupSimsWithSubscriptionIds(int... subscriptionIds) {
        when(mMockSubscriptionManager.getActiveSubscriptionIdList()).thenReturn(subscriptionIds);
        when(mMockSubscriptionManager.getActiveSubscriptionInfoCount())
                .thenReturn(subscriptionIds.length);
        List<SubscriptionInfo> subscriptions = createSubscriptionsWithIds(subscriptionIds);
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(subscriptions);
        for (SubscriptionInfo info : subscriptions) {
            when(mMockSubscriptionManager.getActiveSubscriptionInfo(info.getSubscriptionId()))
                    .thenReturn(info);
        }
    }

    public static class FakeIccPhoneBook extends IIccPhoneBook.Default {

        private static final int DEFAULT_RECORD_SIZE = 30;
        private static final int DEFAULT_RECORDS_COUNT = 100;

        // The key for both maps is the (subscription ID, efid)
        private Map<Pair<Integer, Integer>, AdnRecord[]> mRecords = new HashMap<>();
        // The value is the single record size
        private Map<Pair<Integer, Integer>, int[]> mRecordSizes = new HashMap<>();

        private int mDefaultSubscriptionId = 101;

        private void addRecord(Pair<Integer, Integer> key, AdnRecord record) {
            // Assume that if records are being added then the test wants it to be a valid
            // elementary file so set sizes as well.
            if (!mRecordSizes.containsKey(key)) {
                setupEfWithSizes(key.first, key.second,
                        Math.max(record.getRecId(), DEFAULT_RECORDS_COUNT), DEFAULT_RECORD_SIZE);
            }
            mRecords.get(key)[record.getRecId() - 1] = record;
        }

        public void addRecord(AdnRecord record) {
            addRecord(Pair.create(mDefaultSubscriptionId, record.getEfid()), record);
        }

        public void addRecord(int subscriptionId, AdnRecord record) {
            addRecord(Pair.create(subscriptionId, record.getEfid()), record);
        }

        public void addRecord(int subscriptionId, int efId, String name, String phoneNumber) {
            Pair<Integer, Integer> key = Pair.create(subscriptionId, efId);
            AdnRecord[] records = mRecords.computeIfAbsent(key, unused ->
                    createEmptyRecords(efId, 100));
            int recordIndex = -1;
            for (int i = 0; i < records.length; i++) {
                if (records[i].isEmpty()) {
                    recordIndex = i;
                    break;
                }
            }
            if (recordIndex == -1) {
                throw new IllegalStateException("");
            }
            addRecord(key, new AdnRecord(efId, recordIndex + 1, name, phoneNumber));
        }

        public void addAdnRecord(int subscriptionId, String name, String phoneNumber) {
            addRecord(subscriptionId, IccConstants.EF_ADN, name, phoneNumber);
        }

        public void addAdnRecord(String name, String phoneNumber) {
            addRecord(mDefaultSubscriptionId, IccConstants.EF_ADN, name, phoneNumber);
        }

        public List<AdnRecord> getAllValidRecords() {
            List<AdnRecord> result = new ArrayList<>();
            for (AdnRecord[] records : mRecords.values()) {
                for (AdnRecord record : records) {
                    if (!record.isEmpty()) {
                        result.add(record);
                    }
                }
            }
            return result;
        }

        public void makeAllEfsSupported() {
            makeAllEfsSupported(mDefaultSubscriptionId);
        }

        /**
         * Sets up the fake to return valid records size for all elementary files for the provided
         * subscription IDs.
         */
        public void makeAllEfsSupported(int... subscriptionIds) {
            for (int subId : subscriptionIds) {
                makeAllEfsSupported(subId);
            }
        }

        /**
         * Sets up the fake to return valid records size for all elementary files for the provided
         * subscription IDs.
         */
        public void makeAllEfsSupported(int subscriptionId) {
            setupEfWithSizes(subscriptionId, IccConstants.EF_ADN, DEFAULT_RECORDS_COUNT,
                    DEFAULT_RECORD_SIZE);
            setupEfWithSizes(subscriptionId, IccConstants.EF_FDN, DEFAULT_RECORDS_COUNT,
                    DEFAULT_RECORD_SIZE);
            setupEfWithSizes(subscriptionId, IccConstants.EF_SDN, DEFAULT_RECORDS_COUNT,
                    DEFAULT_RECORD_SIZE);
        }

        public void setRecords(int subscriptionId, int efid, AdnRecord[] records) {
            mRecords.put(Pair.create(subscriptionId, efid), records);
        }

        public void setRecordsSize(int subscriptionId, int efid, int maxRecordCount,
                int maxRecordSize) {
            setRecordsSize(Pair.create(subscriptionId, efid), maxRecordCount, maxRecordSize);
        }

        private void setRecordsSize(Pair<Integer, Integer> key, int maxRecordCount,
                int maxRecordSize) {
            int[] sizes = { maxRecordSize, maxRecordSize * maxRecordCount, maxRecordCount };
            mRecordSizes.put(key, sizes);
        }

        public void setupEfWithSizes(int subscriptionId, int efid, int maxRecordCount,
                int maxRecordSize) {
            Pair<Integer, Integer> key = Pair.create(subscriptionId, efid);
            setRecordsSize(key, maxRecordCount, maxRecordSize);
            AdnRecord[] records = mRecords.computeIfAbsent(key, unused ->
                    createEmptyRecords(efid, maxRecordCount));
            if (records.length < maxRecordCount) {
                throw new IllegalStateException("Records already initialized with a smaller size");
            }
        }

        AdnRecord[] createEmptyRecords(int efid, int count) {
            AdnRecord[] records = new AdnRecord[count];
            for (int i = 0; i < records.length; i++) {
                if (records[i] == null) {
                    records[i] = new AdnRecord(efid, i + 1, "", "");
                }
            }
            return records;
        }

        public void setDefaultSubscriptionId(int defaultSubscriptionId) {
            mDefaultSubscriptionId = defaultSubscriptionId;
        }

        public void reset() {
            mRecords.clear();
            mRecordSizes.clear();
        }

        @Override
        public List<AdnRecord> getAdnRecordsInEf(int efid) {
            return getAdnRecordsInEfForSubscriber(mDefaultSubscriptionId, efid);
        }

        @Override
        public List<AdnRecord> getAdnRecordsInEfForSubscriber(int subId, int efid) {
            return Arrays.asList(
                    mRecords.getOrDefault(Pair.create(subId, efid), new AdnRecord[0]));
        }

        @Override
        public boolean updateAdnRecordsInEfBySearchForSubscriber(int subId, int efid,
                ContentValues values, String pin2) {
            final String oldTag = values.getAsString(IccProvider.STR_TAG);
            final String oldPhoneNumber = values.getAsString(IccProvider.STR_NUMBER);
            final String newTag = values.getAsString(IccProvider.STR_NEW_TAG);
            final String newPhoneNumber = values.getAsString(IccProvider.STR_NEW_NUMBER);
            return updateAdnRecordsInEfBySearchForSubscriber(subId, efid, oldTag, oldPhoneNumber,
                    newTag, newPhoneNumber, pin2);

        }

        private boolean updateAdnRecordsInEfBySearchForSubscriber(int subId, int efid, String oldTag,
                String oldPhoneNumber, String newTag, String newPhoneNumber, String pin2) {
            if (!oldTag.isEmpty() || !oldPhoneNumber.isEmpty()) {
                throw new IllegalArgumentException(
                        "updateAdnRecordsInEfBySearchForSubscriber only supports insert");
            }
            addRecord(subId, efid, newTag, newPhoneNumber);
            return true;
        }

        @Override
        public boolean updateAdnRecordsInEfByIndexForSubscriber(int subId, int efid,
                ContentValues values, int index, String pin2) {
            final String newTag = values.getAsString(IccProvider.STR_NEW_TAG);
            final String newPhoneNumber = values.getAsString(IccProvider.STR_NEW_NUMBER);
            return updateAdnRecordsInEfByIndexForSubscriber(subId, efid, newTag, newPhoneNumber,
                    index, pin2);

        }

        private boolean updateAdnRecordsInEfByIndexForSubscriber(int subId, int efid, String newTag,
                String newPhoneNumber, int index, String pin2) {
            AdnRecord[] records = mRecords.computeIfAbsent(Pair.create(subId, efid), unused ->
                    createEmptyRecords(efid, 100));
            records[index - 1] = new AdnRecord(efid, index, newTag, newPhoneNumber);
            return true;
        }

        @Override
        public int[] getAdnRecordsSize(int efid) {
            return getAdnRecordsSizeForSubscriber(mDefaultSubscriptionId, efid);
        }

        @Override
        public int[] getAdnRecordsSizeForSubscriber(int subId, int efid) {
            Pair<Integer, Integer> key = Pair.create(subId, efid);
            int[] recordsSize = mRecordSizes.get(key);
            if (recordsSize == null) {
                return new int[]{0, 0, 0};
            }
            return recordsSize;
        }

        @Override
        public AdnCapacity getAdnRecordsCapacityForSubscriber(int subId) {
            return new AdnCapacity(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * Implementation of SimPhonebookProvider that allows test-doubles to be injected.
     *
     * <p>The ProviderTestRule doesn't seem to allow a better way to do this since it just
     * invokes the constructor.
     */
    public static class TestableSimPhonebookProvider extends SimPhonebookProvider {

        public static void setup(
                ContentResolver resolver,
                SubscriptionManager subscriptionManager,
                IIccPhoneBook iccPhoneBook) {
            setup(resolver, subscriptionManager, iccPhoneBook, uri -> {
            });
        }

        public static void setup(
                ContentResolver resolver,
                SubscriptionManager subscriptionManager,
                IIccPhoneBook iccPhoneBook,
                ContentNotifier notifier) {
            TestableSimPhonebookProvider provider =
                    (TestableSimPhonebookProvider) Objects.requireNonNull(
                            resolver.acquireContentProviderClient(
                                    SimPhonebookContract.AUTHORITY))
                            .getLocalContentProvider();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    provider.onCreate(subscriptionManager, () -> iccPhoneBook, notifier));
        }

        @Override
        public boolean onCreate() {
            // We stub super.onCreate because it initializes services which causes an
            // IllegalArgumentException because of the context used for the test.
            return true;
        }
    }
}
