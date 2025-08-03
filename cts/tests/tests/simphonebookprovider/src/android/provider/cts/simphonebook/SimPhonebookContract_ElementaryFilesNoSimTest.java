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

import static com.android.internal.telephony.testing.CursorSubject.assertThat;
import static com.android.internal.telephony.testing.TelephonyAssertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.SimPhonebookContract.ElementaryFiles;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/** Tests of {@link ElementaryFiles} that validate the behavior when no SIM is present. */
@RunWith(AndroidJUnit4.class)
public class SimPhonebookContract_ElementaryFilesNoSimTest {

    @Rule
    public final TestRule telephonyRequirementRule =
            new RequiredFeatureRule(PackageManager.FEATURE_TELEPHONY);

    private ContentResolver mResolver;

    @Before
    public void setUp() {
        mResolver = ApplicationProvider.getApplicationContext().getContentResolver();
    }

    @Test
    public void getType_elementaryFiles() {
        String mimeType = mResolver.getType(ElementaryFiles.CONTENT_URI);

        assertThat(mimeType).isEqualTo(ElementaryFiles.CONTENT_TYPE);
    }

    @Test
    public void getType_itemUri() {
        String mimeType = mResolver.getType(
                ElementaryFiles.getItemUri(1, ElementaryFiles.EF_ADN));

        assertThat(mimeType).isEqualTo(ElementaryFiles.CONTENT_ITEM_TYPE);
    }

    @Test
    public void getType_invalidUri_returnsNull() {
        assertThat(mResolver.getType(
                ElementaryFiles.CONTENT_URI.buildUpon().appendPath("invalid").build())).isNull();
    }

    @Test
    public void query_nullProjection_includesAllSupportedColumns() {
        try (Cursor cursor = query(null)) {
            assertThat(cursor.getColumnNames()).asList().containsAtLeast(
                    ElementaryFiles.SLOT_INDEX,
                    ElementaryFiles.SUBSCRIPTION_ID,
                    ElementaryFiles.EF_TYPE,
                    ElementaryFiles.MAX_RECORDS,
                    ElementaryFiles.NAME_MAX_LENGTH,
                    ElementaryFiles.PHONE_NUMBER_MAX_LENGTH,
                    ElementaryFiles.RECORD_COUNT
            );
        }
    }

    @Test
    public void query_withProjection_resultHasProjection() {
        // A subset of the columns
        try (Cursor cursor = query(
                new String[]{
                        ElementaryFiles.EF_TYPE,
                        ElementaryFiles.SUBSCRIPTION_ID
                })) {
            assertThat(cursor).hasColumnNames(
                    ElementaryFiles.EF_TYPE, ElementaryFiles.SUBSCRIPTION_ID);
        }

        String[] projection = new String[]{
                ElementaryFiles.EF_TYPE,
                ElementaryFiles.MAX_RECORDS,
                ElementaryFiles.NAME_MAX_LENGTH,
                ElementaryFiles.PHONE_NUMBER_MAX_LENGTH,
                ElementaryFiles.EF_TYPE,
                ElementaryFiles.SLOT_INDEX
        };

        // All columns in a random order.
        Collections.shuffle(Arrays.asList(projection));
        try (Cursor cursor = query(
                projection)) {
            assertThat(cursor).hasColumnNames(projection);
        }

        // Try another random order just to make sure.
        Collections.shuffle(Arrays.asList(projection));
        try (Cursor cursor = query(
                projection)) {
            assertThat(cursor).hasColumnNames(projection);
        }
    }

    @Test
    public void insert_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> mResolver.insert(ElementaryFiles.CONTENT_URI, new ContentValues()));
    }

    @Test
    public void update_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> mResolver.update(ElementaryFiles.CONTENT_URI, new ContentValues(), null));
    }

    @Test
    public void delete_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> mResolver.delete(ElementaryFiles.CONTENT_URI, null));
    }

    @NonNull
    private Cursor query(String[] projection) {
        return Objects.requireNonNull(
                mResolver.query(ElementaryFiles.CONTENT_URI, projection, null, null));
    }

}
