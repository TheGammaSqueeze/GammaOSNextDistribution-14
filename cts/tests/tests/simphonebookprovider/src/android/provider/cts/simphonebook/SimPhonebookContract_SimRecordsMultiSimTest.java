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

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.SimPhonebookContract.ElementaryFiles;
import android.provider.SimPhonebookContract.SimRecords;
import android.telephony.SubscriptionInfo;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Objects;

/** Tests of {@link SimRecords} for devices that have multiple SIM cards. */
@RunWith(AndroidJUnit4.class)
public class SimPhonebookContract_SimRecordsMultiSimTest {

    private final SimPhonebookRequirementsRule mRequirementsRule =
            new SimPhonebookRequirementsRule(2);
    @Rule
    public final TestRule mRule = RuleChain.outerRule(mRequirementsRule)
            .around(new SimsCleanupRule(EF_ADN));

    private ContentResolver mResolver;

    private int mSubscriptionId1;
    private int mSubscriptionId2;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mResolver = context.getContentResolver();
        RemovableSims removableSims = new RemovableSims(context);
        List<SubscriptionInfo> infos = removableSims.getSubscriptionInfoForRemovableSims();
        mSubscriptionId1 = infos.get(0).getSubscriptionId();
        mSubscriptionId2 = infos.get(1).getSubscriptionId();
    }

    @Test
    public void queryAdn_returnsCursorWithRecordsFromCorrectSim() {
        insertAdn(mSubscriptionId1, "Name1 Adn1", "5550101");
        insertAdn(mSubscriptionId2, "Name1 Adn2", "5550102");
        insertAdn(mSubscriptionId2, "Name2 Adn2", "5550103");
        insertAdn(mSubscriptionId2, "Name3 Adn2", "5550104");

        String[] projection = {
                SimRecords.SUBSCRIPTION_ID,
                SimRecords.ELEMENTARY_FILE_TYPE,
                SimRecords.NAME,
                SimRecords.PHONE_NUMBER
        };
        try (Cursor sim1Cursor = query(SimRecords.getContentUri(mSubscriptionId1, EF_ADN),
                projection);
             Cursor sim2Cursor = query(SimRecords.getContentUri(mSubscriptionId2, EF_ADN),
                     projection)) {
            assertThat(sim1Cursor).hasCount(1);
            assertThat(sim1Cursor).hasSingleRow(mSubscriptionId1, EF_ADN, "Name1 Adn1", "5550101");

            assertThat(sim2Cursor).hasCount(3);
            assertThat(sim2Cursor).atRow(0)
                    .hasRowValues(mSubscriptionId2, ElementaryFiles.EF_ADN, "Name1 Adn2",
                            "5550102");
            assertThat(sim2Cursor).atRow(1)
                    .hasRowValues(mSubscriptionId2, ElementaryFiles.EF_ADN, "Name2 Adn2",
                            "5550103");
            assertThat(sim2Cursor).atRow(2)
                    .hasRowValues(mSubscriptionId2, ElementaryFiles.EF_ADN, "Name3 Adn2",
                            "5550104");
        }
    }

    @Test
    public void insertAdn_returnsUriForCorrectSim() {
        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name Sim1");
        values.put(SimRecords.PHONE_NUMBER, "5550101");
        Uri uriSim1 = mResolver.insert(SimRecords.getContentUri(mSubscriptionId1, EF_ADN), values);
        values.put(SimRecords.NAME, "Name Sim2");
        values.put(SimRecords.PHONE_NUMBER, "5550102");
        Uri uriSim2 = mResolver.insert(SimRecords.getContentUri(mSubscriptionId2, EF_ADN), values);

        String[] projection = {
                SimRecords.SUBSCRIPTION_ID,
                SimRecords.PHONE_NUMBER
        };
        try (Cursor sim1Cursor = query(uriSim1, projection);
             Cursor sim2Cursor = query(uriSim2, projection)) {
            assertThat(sim1Cursor).hasSingleRow(mSubscriptionId1, "5550101");
            assertThat(sim2Cursor).hasSingleRow(mSubscriptionId2, "5550102");
        }
    }

    @Test
    public void updateAdn_updatesCorrectRecord() {
        Uri sim1Uri = insertAdn(mSubscriptionId1, "Initial", "5550101");
        Uri sim2Uri = insertAdn(mSubscriptionId2, "Initial", "5550101");

        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Updated Sim1");
        values.put(SimRecords.PHONE_NUMBER, "5550102");

        int count = mResolver.update(sim1Uri, values, null);
        assertThat(count).isEqualTo(1);

        values.put(SimRecords.NAME, "Updated Sim2");
        values.put(SimRecords.PHONE_NUMBER, "5550103");

        count = mResolver.update(sim2Uri, values, null);
        assertThat(count).isEqualTo(1);

        String[] projection =
                {SimRecords.SUBSCRIPTION_ID, SimRecords.NAME, SimRecords.PHONE_NUMBER};
        try (Cursor cursor1 = query(SimRecords.getContentUri(mSubscriptionId1, EF_ADN), projection);
             Cursor cursor2 = query(SimRecords.getContentUri(mSubscriptionId2, EF_ADN),
                     projection)) {
            assertThat(cursor1).hasSingleRow(mSubscriptionId1, "Updated Sim1", "5550102");
            assertThat(cursor2).hasSingleRow(mSubscriptionId2, "Updated Sim2", "5550103");
        }
    }

    @Test
    public void deleteAdn_deletesCorrectRecord() {
        Uri sim1Uri = insertAdn(mSubscriptionId1, "Initial", "5550101");
        Uri sim2Uri = insertAdn(mSubscriptionId2, "Initial", "5550101");

        int count = mResolver.delete(sim1Uri, null);
        assertThat(count).isEqualTo(1);

        try (Cursor cursor1 = query(SimRecords.getContentUri(mSubscriptionId1, EF_ADN), null);
             Cursor cursor2 = query(SimRecords.getContentUri(mSubscriptionId2, EF_ADN), null)) {
            assertThat(cursor1).hasCount(0);
            assertThat(cursor2).hasCount(1);
        }

        // Add back record for first SIM card
        insertAdn(mSubscriptionId1, "Initial", "5550101");

        count = mResolver.delete(sim2Uri, null);
        assertThat(count).isEqualTo(1);

        try (Cursor cursor1 = query(SimRecords.getContentUri(mSubscriptionId1, EF_ADN), null);
             Cursor cursor2 = query(SimRecords.getContentUri(mSubscriptionId2, EF_ADN), null)) {
            assertThat(cursor1).hasCount(1);
            assertThat(cursor2).hasCount(0);
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
