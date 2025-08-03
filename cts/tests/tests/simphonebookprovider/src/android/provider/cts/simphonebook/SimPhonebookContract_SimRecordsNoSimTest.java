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
import static android.provider.SimPhonebookContract.ElementaryFiles.EF_FDN;
import static android.provider.SimPhonebookContract.ElementaryFiles.EF_SDN;

import static com.android.internal.telephony.testing.TelephonyAssertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeThat;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.SimPhonebookContract;
import android.provider.SimPhonebookContract.ElementaryFiles;
import android.provider.SimPhonebookContract.SimRecords;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.Objects;

/** Tests of {@link SimRecords} that validate the behavior when no SIM is present. */
@RunWith(AndroidJUnit4.class)
public class SimPhonebookContract_SimRecordsNoSimTest {
    private static final int MISSING_SIM_SUBSCRIPTION_ID = 9043;
    private static final String MISSING_SIM_EXCEPTION_MESSAGE =
            "No active SIM with subscription ID " + MISSING_SIM_SUBSCRIPTION_ID;

    @Rule
    public final TestRule telephonyRequirementRule =
            new RequiredFeatureRule(PackageManager.FEATURE_TELEPHONY);

    private Context mContext;
    private ContentResolver mResolver;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mResolver = mContext.getContentResolver();
    }

    @Test
    public void getType_recordsDir() {
        assertThat(
                mResolver.getType(SimRecords.getContentUri(1, ElementaryFiles.EF_ADN))).isEqualTo(
                SimRecords.CONTENT_TYPE);
        assertThat(
                mResolver.getType(SimRecords.getContentUri(1, ElementaryFiles.EF_FDN))).isEqualTo(
                SimRecords.CONTENT_TYPE);
        assertThat(
                mResolver.getType(SimRecords.getContentUri(1, ElementaryFiles.EF_SDN))).isEqualTo(
                SimRecords.CONTENT_TYPE);
    }

    @Test
    public void getType_recordItem() {
        assertThat(
                mResolver.getType(SimRecords.getItemUri(1, ElementaryFiles.EF_ADN, 3))).isEqualTo(
                SimRecords.CONTENT_ITEM_TYPE);
        assertThat(
                mResolver.getType(SimRecords.getItemUri(1, ElementaryFiles.EF_FDN, 3))).isEqualTo(
                SimRecords.CONTENT_ITEM_TYPE);
        assertThat(
                mResolver.getType(SimRecords.getItemUri(1, ElementaryFiles.EF_SDN, 3))).isEqualTo(
                SimRecords.CONTENT_ITEM_TYPE);
    }

    @Test
    public void getType_invalidUri_returnsNull() {
        assertThat(mResolver.getType(
                SimRecords.getContentUri(1, ElementaryFiles.EF_ADN).buildUpon().appendPath(
                        "invalid").build())).isNull();
    }

    @Test
    public void query_nonExistentSim_throwsIllegalArgumentException() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> query(SimRecords.getContentUri(MISSING_SIM_SUBSCRIPTION_ID, EF_ADN), null)
        );
        assertThat(e).hasMessageThat().isEqualTo(MISSING_SIM_EXCEPTION_MESSAGE);

        e = assertThrows(IllegalArgumentException.class,
                () -> query(SimRecords.getContentUri(MISSING_SIM_SUBSCRIPTION_ID, EF_FDN), null)
        );
        assertThat(e).hasMessageThat().isEqualTo(MISSING_SIM_EXCEPTION_MESSAGE);

        e = assertThrows(IllegalArgumentException.class,
                () -> query(SimRecords.getContentUri(MISSING_SIM_SUBSCRIPTION_ID, EF_SDN), null)
        );
        assertThat(e).hasMessageThat().isEqualTo(MISSING_SIM_EXCEPTION_MESSAGE);
    }

    @Test
    public void query_invalidUri_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> query(SimRecords.getContentUri(1,
                EF_ADN).buildUpon().appendPath("invalid").build(), null)
        );
    }

    @Test
    public void query_subscriptionIdOverflow_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                query(SimPhonebookContract.AUTHORITY_URI.buildUpon()
                        .appendPath("subid")
                        .appendPath(Long.toString(Long.MAX_VALUE))
                        .appendPath("adn").build(), null));
        assertThrows(IllegalArgumentException.class, () ->
                query(SimPhonebookContract.AUTHORITY_URI.buildUpon()
                        .appendPath("subid")
                        .appendPath(Long.toString(Long.MAX_VALUE))
                        .appendPath("fdn").build(), null));
        assertThrows(IllegalArgumentException.class, () ->
                query(SimPhonebookContract.AUTHORITY_URI.buildUpon()
                        .appendPath("subid")
                        .appendPath(Long.toString(Long.MAX_VALUE))
                        .appendPath("sdn").build(), null));
    }

    @Test
    public void insert_nonAdnDirUris_throwsUnsupportedOperationException() {
        ContentValues values = new ContentValues();
        values.put(SimRecords.NAME, "Name");
        values.put(SimRecords.PHONE_NUMBER, "8005550101");

        // Depending on the context where the test is run the test app may have carrier privileges
        // e.g. omitting this will cause a failure when running on cuttlefish if it is launched
        // with --modem_simulator_sim_type=2
        if (!mContext.getSystemService(TelephonyManager.class).hasCarrierPrivileges()) {
            assertThrows(UnsupportedOperationException.class,
                    () -> mResolver.insert(
                            SimRecords.getContentUri(MISSING_SIM_SUBSCRIPTION_ID, EF_FDN), values));
        }
        assertThrows(UnsupportedOperationException.class,
                () -> mResolver.insert(
                        SimRecords.getContentUri(MISSING_SIM_SUBSCRIPTION_ID, EF_SDN), values));
        assertThrows(UnsupportedOperationException.class, () ->
                mResolver.insert(
                        SimRecords.getItemUri(MISSING_SIM_SUBSCRIPTION_ID, ElementaryFiles.EF_ADN,
                                1),
                        values));
        assertThrows(UnsupportedOperationException.class, () ->
                mResolver.insert(
                        SimRecords.getItemUri(MISSING_SIM_SUBSCRIPTION_ID, ElementaryFiles.EF_FDN,
                                1),
                        values));
        assertThrows(UnsupportedOperationException.class, () ->
                mResolver.insert(
                        SimRecords.getItemUri(MISSING_SIM_SUBSCRIPTION_ID, ElementaryFiles.EF_SDN,
                                1),
                        values));
    }

    @Test
    public void insertAdn_throwsCorrectException() {
        ContentValues values = new ContentValues();
        values.put(SimRecords.PHONE_NUMBER, "5550101");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mResolver.insert(
                        SimRecords.getContentUri(MISSING_SIM_SUBSCRIPTION_ID, EF_ADN), values));

        assertThat(e).hasMessageThat().isEqualTo(MISSING_SIM_EXCEPTION_MESSAGE);
    }

    @Test
    public void updateAdn_throwsCorrectException() {
        ContentValues values = new ContentValues();
        values.put(SimRecords.PHONE_NUMBER, "5550101");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                mResolver.update(SimRecords.getItemUri(
                        MISSING_SIM_SUBSCRIPTION_ID, ElementaryFiles.EF_ADN, 1), values, null));

        assertThat(e).hasMessageThat().isEqualTo(MISSING_SIM_EXCEPTION_MESSAGE);
    }

    @Test
    public void deleteAdn_throwsCorrectException() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                mResolver.delete(SimRecords.getItemUri(
                        MISSING_SIM_SUBSCRIPTION_ID, ElementaryFiles.EF_ADN, 1), null));

        assertThat(e).hasMessageThat().isEqualTo(MISSING_SIM_EXCEPTION_MESSAGE);
    }

    @Test
    public void getEncodedNameLength_emptyName() {
        assertThat(SimRecords.getEncodedNameLength(mResolver, "")).isEqualTo(0);
    }

    @Test
    public void getEncodedNameLength_basicGsm() {
        // This isn't actually exhaustive of all the basic GSM alphabet characters but
        // it's pretty good coverage. These should all require 1 byte each when encoded.
        String name = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz 1234567890"
                + "!@#$%&*()_+,.?;:<>";
        assertThat(SimRecords.getEncodedNameLength(mResolver, name)).isEqualTo(name.length());
    }

    @Test
    public void getEncodedNameLength_ucsEncodable() {
        String name = "日本";
        // This won't use GMS alphabet so at minimum it will need to use a byte to indicate the
        // encoding and then also multiple bytes for the characters (or another offset byte).
        assertThat(SimRecords.getEncodedNameLength(mResolver, name))
                .isGreaterThan(name.length() + 1);

        // When the name contains a mix it still must use a less efficient encoding.
        name = "abc日本";
        assertThat(SimRecords.getEncodedNameLength(mResolver, name))
                .isGreaterThan(name.length() + 1);
    }


    @NonNull
    private Cursor query(Uri uri, String[] projection) {
        return Objects.requireNonNull(mResolver.query(uri, projection, null, null));
    }
}
