/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import static java.util.Map.entry;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony.Carriers;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

/**
 * Tests for the APN database exposed by {@link Carriers}.
 */
@ApiTest(apis = {"android.provider.Telephony.Carriers#CONTENT_URI"})
@RunWith(AndroidJUnit4.class)
public class ApnTest {
    private static final String TAG = "ApnTest";

    private static final Uri CARRIER_TABLE_URI = Carriers.CONTENT_URI;

    private static final String NAME = "carrierName";
    private static final String APN = "apn";
    private static final String PROXY = "proxy";
    private static final String PORT = "port";
    private static final String MMSC = "mmsc";
    private static final String MMSPROXY = "mmsproxy";
    private static final String MMSPORT = "mmsport";
    private static final String USER = "user";
    private static final String PASSWORD = "password";
    private static final String AUTH_TYPE = "auth_type";
    private static final String TYPE = "type";
    private static final String PROTOCOL = "protocol";
    private static final String ROAMING_PROTOCOL = "roaming_protocol";
    private static final String CARRIER_ENABLED = "true";
    private static final String NETWORK_TYPE_BITMASK = "0";
    private static final String BEARER = "0";
    private static final String CARRIER_ID = String.valueOf(TelephonyManager.UNKNOWN_CARRIER_ID);

    private static final Map<String, String> APN_MAP =
            Map.ofEntries(
                    entry(Carriers.NAME, NAME),
                    entry(Carriers.APN, APN),
                    entry(Carriers.PROXY, PROXY),
                    entry(Carriers.PORT, PORT),
                    entry(Carriers.MMSC, MMSC),
                    entry(Carriers.MMSPROXY, MMSPROXY),
                    entry(Carriers.MMSPORT, MMSPORT),
                    entry(Carriers.USER, USER),
                    entry(Carriers.PASSWORD, PASSWORD),
                    entry(Carriers.AUTH_TYPE, AUTH_TYPE),
                    entry(Carriers.TYPE, TYPE),
                    entry(Carriers.PROTOCOL, PROTOCOL),
                    entry(Carriers.ROAMING_PROTOCOL, ROAMING_PROTOCOL),
                    entry(Carriers.CARRIER_ENABLED, CARRIER_ENABLED),
                    entry(Carriers.NETWORK_TYPE_BITMASK, NETWORK_TYPE_BITMASK),
                    entry(Carriers.BEARER, BEARER),
                    entry(Carriers.CARRIER_ID, CARRIER_ID));

    // make sure the numeric is empty aka default value
    private static final String TEST_APN_SELECTION = Carriers.NUMERIC + "= '' AND "
            + Carriers.CARRIER_ID + "=?";
    private static final String[] TEST_APN_SELECTION_ARGS = {CARRIER_ID};
    private static final String[] TEST_APN_PROJECTION =
            APN_MAP.keySet().toArray(new String[APN_MAP.size()]);

    private Context mContext;
    private ContentResolver mContentResolver;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY));

        mContentResolver = mContext.getContentResolver();
    }

    @After
    public void tearDown() throws Exception {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        // Ensures APNs are deleted in case a test threw.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();
        try {
            // Delete the test APN (potentially redundant for deletion test).
            mContentResolver.delete(CARRIER_TABLE_URI, TEST_APN_SELECTION, TEST_APN_SELECTION_ARGS);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    /* The CTS tests are intended to test APN insertion with {@link
     * android.Manifest.permission.READ_PHONE_STATE} and makes use of {@link
     * android.Manifest.permission.WRITE_APN_SETTINGS} instead of carrier privileges. This covers
     * a class of errors where APIs that require READ_PHONE_STATE are called during APN updates.
     * In these cases, if a caller has READ_PHONE_STATE, it is important to ensure that the binder
     * identity is appropriately cleared or handled. Otherwise, the calling UID may not match
     * the calling package during permission checks.
     */
    @Test
    public void testPermissionCheckForApnInsertion_success() {
        try {
            // Insert the test APN.
            insertTestApnAndValidateInsertion(/* grantPermission= */ true);
        } catch (SecurityException e) {
            fail(
                    "Test failed due to security exception. Permissions may be incorrectly checked"
                            + " or managed. "
                            + Log.getStackTraceString(e));
        } catch (Exception e) {
            fail("Test failed due to exception. " + Log.getStackTraceString(e));
        }
    }

    @Test
    public void testPermissionCheckForApnInsertion_noWriteApnSettings() {
        assertThrows(
                SecurityException.class,
                () -> insertTestApnAndValidateInsertion(/* grantPermission= */ false));
    }

    @Test
    public void testPermissionCheckForApnUpdate_success() {
        try {
            // Insert the test APN.
            insertTestApnAndValidateInsertion(/* grantPermission= */ true);
            grantWriteApnSettings();
            // Create an APN entry to update.
            ContentValues contentValues = makeDefaultContentValues();

            // Update the APN.
            final String newApn = "newapn";
            contentValues.put(Carriers.APN, newApn);
            final int updateCount =
                    mContentResolver.update(
                            CARRIER_TABLE_URI,
                            contentValues,
                            TEST_APN_SELECTION,
                            TEST_APN_SELECTION_ARGS);

            assertWithMessage("Unexpected number of rows updated").that(updateCount).isEqualTo(1);
            // Verify the updated value.
            Cursor cursor = queryTestApn();
            assertWithMessage("Failed to query the table").that(cursor).isNotNull();
            assertWithMessage("Unexpected number of APNs returned by cursor")
                    .that(cursor.getCount())
                    .isEqualTo(1);
            cursor.moveToFirst();
            assertWithMessage("Unexpected value returned by cursor")
                    .that(cursor.getString(cursor.getColumnIndex(Carriers.APN)))
                    .isEqualTo(newApn);
        } catch (SecurityException e) {
            fail(
                    "Test failed due to security exception. Permissions may be incorrectly checked"
                            + " or managed. "
                            + Log.getStackTraceString(e));
        } catch (Exception e) {
            fail("Test failed due to exception. " + Log.getStackTraceString(e));
        }
    }

    @Test
    public void testPermissionCheckForApnUpdate_noWriteApnSettings() {
        try {
            // Insert the test APN.
            insertTestApnAndValidateInsertion(/* grantPermission= */ true);
            // Create an APN entry to update.
            ContentValues contentValues = makeDefaultContentValues();

            // Attempt to update the APN without WRITE_APN_SETTINGS.
            contentValues.put(Carriers.APN, "newapn");
            assertThrows(
                    SecurityException.class,
                    () ->
                            mContentResolver.update(
                                    CARRIER_TABLE_URI,
                                    contentValues,
                                    TEST_APN_SELECTION,
                                    TEST_APN_SELECTION_ARGS));
        } catch (SecurityException e) {
            fail(
                    "Test failed due to security exception. Permissions may be incorrectly checked"
                            + " or managed. "
                            + Log.getStackTraceString(e));
        } catch (Exception e) {
            fail("Test failed due to exception. " + Log.getStackTraceString(e));
        }
    }

    @Test
    public void testPermissionCheckForApnDeletion_success() {
        try {
            insertTestApnAndValidateInsertion(/* grantPermission= */ true);
            grantWriteApnSettings();

            // Delete the APN.
            int numberOfRowsDeleted =
                    mContentResolver.delete(
                            CARRIER_TABLE_URI, TEST_APN_SELECTION, TEST_APN_SELECTION_ARGS);

            assertWithMessage("Unexpected number of rows deleted")
                    .that(numberOfRowsDeleted)
                    .isEqualTo(1);
            // Verify that deleted values are gone.
            Cursor cursor = queryTestApn();
            assertWithMessage("Unexpected number of rows deleted")
                    .that(cursor.getCount())
                    .isEqualTo(0);
        } catch (SecurityException e) {
            fail(
                    "Test failed due to security exception. Permissions may be incorrectly checked"
                            + " or managed. "
                            + Log.getStackTraceString(e));
        } catch (Exception e) {
            fail("Test failed due to exception. " + Log.getStackTraceString(e));
        }
    }

    @Test
    public void testPermissionCheckForApnDeletion_noWriteApnSettings() {
        try {
            insertTestApnAndValidateInsertion(/* grantPermission= */ true);

            // Attempt to delete the APN without WRITE_APN_SETTINGS.
            assertThrows(
                    SecurityException.class,
                    () ->
                            mContentResolver.delete(
                                    CARRIER_TABLE_URI,
                                    TEST_APN_SELECTION,
                                    TEST_APN_SELECTION_ARGS));
        } catch (SecurityException e) {
            fail(
                    "Test failed due to security exception. Permissions may be incorrectly checked"
                            + " or managed. "
                            + Log.getStackTraceString(e));
        } catch (Exception e) {
            fail("Test failed due to exception. " + Log.getStackTraceString(e));
        }
    }

    /**
     * Verify that the test APN setting can be inserted, queried, and validated
     * by {@link Carriers#CARRIER_ID} without {@link Carriers#NUMERIC} when
     * the permission is granted
     *
     * @param grantPermission whether to grant WRITE_APN_SETTINGS prior to insertion
     */
    private void insertTestApnAndValidateInsertion(boolean grantPermission) throws Exception {
        if (grantPermission) {
            grantWriteApnSettings();
        }
        // Create a set of column_name/value pairs to add to the database.
        ContentValues contentValues = makeDefaultContentValues();

        // Insert the value into database. Without permissions, this is expected to throw.
        Uri newUri = mContentResolver.insert(CARRIER_TABLE_URI, contentValues);

        assertWithMessage("Failed to insert to table").that(newUri).isNotNull();
        Cursor cursor = queryTestApn();
        // Verify that the inserted value match the results of the query.
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

        if (grantPermission) {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    /** Grants WRITE_APN_SETTINGS through shell identity. */
    private void grantWriteApnSettings() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.WRITE_APN_SETTINGS");
    }

    /** Returns a cursor corresponding to the test APN value. */
    private Cursor queryTestApn() {
        return mContentResolver.query(
                CARRIER_TABLE_URI,
                TEST_APN_PROJECTION,
                TEST_APN_SELECTION,
                TEST_APN_SELECTION_ARGS,
                /* sortOrder= */ null);
    }

    private ContentValues makeDefaultContentValues() {
        ContentValues contentValues = new ContentValues();
        APN_MAP.entrySet().stream()
                .forEach(entry -> contentValues.put(entry.getKey(), entry.getValue()));
        return contentValues;
    }
}
