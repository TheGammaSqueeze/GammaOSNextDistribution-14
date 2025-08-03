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

package com.android.bluetooth.btservice;

import static android.bluetooth.BluetoothA2dp.OPTIONAL_CODECS_NOT_SUPPORTED;
import static android.bluetooth.BluetoothA2dp.OPTIONAL_CODECS_PREF_DISABLED;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.mock.MockCursor;
import android.util.Log;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.btservice.storage.Metadata;
import com.android.bluetooth.btservice.storage.MetadataDatabase;
import com.android.bluetooth.opp.BluetoothShare;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DataMigrationTest {
    private static final String TAG = "DataMigrationTest";

    private static final String AUTHORITY = "bluetooth_legacy.provider";

    private static final String TEST_PREF = "DatabaseTestPref";

    private MockContentResolver mMockContentResolver;

    private Context mTargetContext;
    private SharedPreferences mPrefs;

    @Mock private Context mMockContext;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mTargetContext = InstrumentationRegistry.getTargetContext();
        mTargetContext.deleteSharedPreferences(TEST_PREF);
        mPrefs = mTargetContext.getSharedPreferences(TEST_PREF, Context.MODE_PRIVATE);
        mPrefs.edit().clear().apply();

        mMockContentResolver = new MockContentResolver(mTargetContext);
        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mMockContext.getCacheDir()).thenReturn(mTargetContext.getCacheDir());

        when(mMockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mPrefs);

    }

    @After
    public void tearDown() throws Exception {
        mPrefs.edit().clear().apply();
        mTargetContext.deleteSharedPreferences(TEST_PREF);
        mTargetContext.deleteDatabase("TestBluetoothDb");
        mTargetContext.deleteDatabase("TestOppDb");
    }

    private void assertRunStatus(int status) {
        assertThat(DataMigration.run(mMockContext)).isEqualTo(status);
        assertThat(DataMigration.migrationStatus(mMockContext)).isEqualTo(status);
    }

    /**
     * Test: execute Empty migration
     */
    @Test
    public void testEmptyMigration() {
        BluetoothLegacyContentProvider fakeContentProvider =
                new BluetoothLegacyContentProvider(mMockContext);
        mMockContentResolver.addProvider(AUTHORITY, fakeContentProvider);

        final int nCallCount = DataMigration.sharedPreferencesKeys.length
                + 1; // +1 for default preferences
        final int nBundleCount = 2; // `bluetooth_db` && `btopp.db`

        assertRunStatus(DataMigration.MIGRATION_STATUS_COMPLETED);
        assertThat(fakeContentProvider.mCallCount).isEqualTo(nCallCount);
        assertThat(fakeContentProvider.mBundleCount).isEqualTo(nBundleCount);

        // run it twice to trigger an already completed migration
        assertRunStatus(DataMigration.MIGRATION_STATUS_COMPLETED);
        // ContentProvider should not have any more calls made than previously
        assertThat(fakeContentProvider.mCallCount).isEqualTo(nCallCount);
        assertThat(fakeContentProvider.mBundleCount).isEqualTo(nBundleCount);
    }

    private static class BluetoothLegacyContentProvider extends MockContentProvider {
        BluetoothLegacyContentProvider(Context ctx) {
            super(ctx);
        }
        int mCallCount = 0;
        int mBundleCount = 0;
        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            mBundleCount++;
            return null;
        }
        @Override
        public Bundle call(String method, String arg, Bundle extras) {
            mCallCount++;
            return null;
        }
    }

    /**
     * Test: execute migration without having a content provided registered
     */
    @Test
    public void testMissingProvider() {
        assertThat(DataMigration.isMigrationApkInstalled(mMockContext)).isFalse();

        assertRunStatus(DataMigration.MIGRATION_STATUS_MISSING_APK);

        mMockContentResolver.addProvider(AUTHORITY, new MockContentProvider(mMockContext));
        assertThat(DataMigration.isMigrationApkInstalled(mMockContext)).isTrue();
    }

    /**
     * Test: execute migration after too many attempt
     */
    @Test
    public void testTooManyAttempt() {
        assertThat(mPrefs.getInt(DataMigration.MIGRATION_ATTEMPT_PROPERTY, -1))
            .isEqualTo(-1);

        for (int i = 0; i < DataMigration.MAX_ATTEMPT; i++) {
            assertThat(DataMigration.incrementeMigrationAttempt(mMockContext))
                .isTrue();
            assertThat(mPrefs.getInt(DataMigration.MIGRATION_ATTEMPT_PROPERTY, -1))
                .isEqualTo(i + 1);
        }
        assertThat(DataMigration.incrementeMigrationAttempt(mMockContext))
            .isFalse();
        assertThat(mPrefs.getInt(DataMigration.MIGRATION_ATTEMPT_PROPERTY, -1))
            .isEqualTo(DataMigration.MAX_ATTEMPT + 1);

        mMockContentResolver.addProvider(AUTHORITY, new MockContentProvider(mMockContext));
        assertRunStatus(DataMigration.MIGRATION_STATUS_MAX_ATTEMPT);
    }

    /**
     * Test: execute migration of SharedPreferences
     */
    @Test
    public void testSharedPreferencesMigration() {
        BluetoothLegacySharedPreferencesContentProvider fakeContentProvider =
                new BluetoothLegacySharedPreferencesContentProvider(mMockContext);
        mMockContentResolver.addProvider(AUTHORITY, fakeContentProvider);

        assertThat(DataMigration.sharedPreferencesMigration("Boolean", mMockContext)).isTrue();
        assertThat(mPrefs.getBoolean("keyBoolean", false)).isTrue();
        assertThat(fakeContentProvider.mCallCount).isEqualTo(2);

        assertThat(DataMigration.sharedPreferencesMigration("Long", mMockContext)).isTrue();
        assertThat(mPrefs.getLong("keyLong", -1)).isEqualTo(42);
        assertThat(fakeContentProvider.mCallCount).isEqualTo(4);

        assertThat(DataMigration.sharedPreferencesMigration("Int", mMockContext)).isTrue();
        assertThat(mPrefs.getInt("keyInt", -1)).isEqualTo(42);
        assertThat(fakeContentProvider.mCallCount).isEqualTo(6);

        assertThat(DataMigration.sharedPreferencesMigration("String", mMockContext)).isTrue();
        assertThat(mPrefs.getString("keyString", "Not42")).isEqualTo("42");
        assertThat(fakeContentProvider.mCallCount).isEqualTo(8);

        // Check not overriding an existing value:
        mPrefs.edit().putString("keyString2", "already 42").apply();
        assertThat(DataMigration.sharedPreferencesMigration("String2", mMockContext)).isTrue();
        assertThat(mPrefs.getString("keyString2", "Not42")).isEqualTo("already 42");
        assertThat(fakeContentProvider.mCallCount).isEqualTo(10);

        assertThat(DataMigration.sharedPreferencesMigration("Invalid", mMockContext)).isFalse();

        assertThat(DataMigration.sharedPreferencesMigration("null", mMockContext)).isFalse();

        assertThat(DataMigration.sharedPreferencesMigration("empty", mMockContext)).isFalse();

        assertThat(DataMigration
                .sharedPreferencesMigration("anything else", mMockContext)).isTrue();
    }

    private static class BluetoothLegacySharedPreferencesContentProvider
            extends MockContentProvider {
        BluetoothLegacySharedPreferencesContentProvider(Context ctx) {
            super(ctx);
        }
        String mLastMethod = null;
        int mCallCount = 0;
        int mBundleCount = 0;
        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            mBundleCount++;
            return null;
        }
        @Override
        public Bundle call(String method, String arg, Bundle extras) {
            mCallCount++;
            mLastMethod = method;
            assertThat(method).isNotNull();
            assertThat(arg).isNotNull();
            assertThat(extras).isNull();
            final String key = "key" + arg;
            Bundle b = new Bundle();
            b.putStringArrayList(DataMigration.KEY_LIST, new ArrayList<String>(Arrays.asList(key)));
            switch(arg) {
                case "Boolean":
                    b.putBoolean(key, true);
                    break;
                case "Long":
                    b.putLong(key, Long.valueOf(42));
                    break;
                case "Int":
                    b.putInt(key, 42);
                    break;
                case "String":
                    b.putString(key, "42");
                    break;
                case "String2":
                    b.putString(key, "42");
                    break;
                case "null":
                    b.putObject(key, null);
                    break;
                case "Invalid":
                     // Put anything different from Boolean/Long/Integer/String
                    b.putFloat(key, 42f);
                    break;
                case "empty":
                    // Do not put anything in the bundle and remove the key
                    b = new Bundle();
                    break;
                default:
                    return null;
            }
            return b;
        }
    }

    /**
     * Test: execute migration of BLUETOOTH_DATABASE and OPP_DATABASE without correct data
     */
    @Test
    public void testIncompleteDbMigration() {
        when(mMockContext.getDatabasePath("btopp.db"))
            .thenReturn(mTargetContext.getDatabasePath("TestOppDb"));
        when(mMockContext.getDatabasePath("bluetooth_db"))
            .thenReturn(mTargetContext.getDatabasePath("TestBluetoothDb"));

        BluetoothLegacyDbContentProvider fakeContentProvider =
                new BluetoothLegacyDbContentProvider(mMockContext);
        mMockContentResolver.addProvider(AUTHORITY, fakeContentProvider);

        fakeContentProvider.mCursor = new FakeCursor(FAKE_SAMPLE);
        assertThat(DataMigration.bluetoothDatabaseMigration(mMockContext)).isFalse();

        fakeContentProvider.mCursor = new FakeCursor(FAKE_SAMPLE);
        assertThat(DataMigration.oppDatabaseMigration(mMockContext)).isFalse();
    }

    private static final List<Pair<String, Object>> FAKE_SAMPLE =
            Arrays.asList(
                    new Pair("wrong_key", "wrong_content")
    );

    /**
     * Test: execute migration of BLUETOOTH_DATABASE
     */
    @Test
    public void testBluetoothDbMigration() {
        when(mMockContext.getDatabasePath("bluetooth_db"))
            .thenReturn(mTargetContext.getDatabasePath("TestBluetoothDb"));

        BluetoothLegacyDbContentProvider fakeContentProvider =
                new BluetoothLegacyDbContentProvider(mMockContext);
        mMockContentResolver.addProvider(AUTHORITY, fakeContentProvider);

        Cursor c = new FakeCursor(BLUETOOTH_DATABASE_SAMPLE);
        fakeContentProvider.mCursor = c;
        assertThat(DataMigration.bluetoothDatabaseMigration(mMockContext)).isTrue();

        MetadataDatabase database = MetadataDatabase.createDatabaseWithoutMigration(mMockContext);
        Metadata metadata = database.load().get(0);

        Log.d(TAG, "Metadata migrated: " + metadata);

        assertWithMessage("Address mismatch")
            .that(metadata.getAddress()).isEqualTo("my_address");
        assertWithMessage("Connection policy mismatch")
            .that(metadata.getProfileConnectionPolicy(BluetoothProfile.A2DP))
            .isEqualTo(CONNECTION_POLICY_FORBIDDEN);
        assertWithMessage("Custom metadata mismatch")
            .that(metadata.getCustomizedMeta(BluetoothDevice.METADATA_UNTETHERED_LEFT_CHARGING))
            .isEqualTo(CUSTOM_META);
    }

    private static final byte[] CUSTOM_META =  new byte[]{ 42, 43, 44};

    private static final List<Pair<String, Object>> BLUETOOTH_DATABASE_SAMPLE =
            Arrays.asList(
                    new Pair("address", "my_address"),
                    new Pair("migrated", 1),
                    new Pair("a2dpSupportsOptionalCodecs", OPTIONAL_CODECS_NOT_SUPPORTED),
                    new Pair("a2dpOptionalCodecsEnabled", OPTIONAL_CODECS_PREF_DISABLED),
                    new Pair("last_active_time", 42),
                    new Pair("is_active_a2dp_device", 1),

                    // connection_policy
                    new Pair("a2dp_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("a2dp_sink_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("hfp_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("hfp_client_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("hid_host_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("pan_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("pbap_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("pbap_client_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("map_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("sap_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("hearing_aid_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("hap_client_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("map_client_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("le_audio_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("volume_control_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("csip_set_coordinator_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("le_call_control_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("bass_client_connection_policy", CONNECTION_POLICY_FORBIDDEN),
                    new Pair("battery_connection_policy", CONNECTION_POLICY_FORBIDDEN),

                    // Custom meta-data
                    new Pair("manufacturer_name", CUSTOM_META),
                    new Pair("model_name", CUSTOM_META),
                    new Pair("software_version", CUSTOM_META),
                    new Pair("hardware_version", CUSTOM_META),
                    new Pair("companion_app", CUSTOM_META),
                    new Pair("main_icon", CUSTOM_META),
                    new Pair("is_untethered_headset", CUSTOM_META),
                    new Pair("untethered_left_icon", CUSTOM_META),
                    new Pair("untethered_right_icon", CUSTOM_META),
                    new Pair("untethered_case_icon", CUSTOM_META),
                    new Pair("untethered_left_battery", CUSTOM_META),
                    new Pair("untethered_right_battery", CUSTOM_META),
                    new Pair("untethered_case_battery", CUSTOM_META),
                    new Pair("untethered_left_charging", CUSTOM_META),
                    new Pair("untethered_right_charging", CUSTOM_META),
                    new Pair("untethered_case_charging", CUSTOM_META),
                    new Pair("enhanced_settings_ui_uri", CUSTOM_META),
                    new Pair("device_type", CUSTOM_META),
                    new Pair("main_battery", CUSTOM_META),
                    new Pair("main_charging", CUSTOM_META),
                    new Pair("main_low_battery_threshold", CUSTOM_META),
                    new Pair("untethered_left_low_battery_threshold", CUSTOM_META),
                    new Pair("untethered_right_low_battery_threshold", CUSTOM_META),
                    new Pair("untethered_case_low_battery_threshold", CUSTOM_META),
                    new Pair("spatial_audio", CUSTOM_META),
                    new Pair("fastpair_customized", CUSTOM_META)
    );

    /**
     * Test: execute migration of OPP_DATABASE
     */
    @Test
    public void testOppDbMigration() {
        when(mMockContext.getDatabasePath("btopp.db"))
            .thenReturn(mTargetContext.getDatabasePath("TestOppDb"));

        BluetoothLegacyDbContentProvider fakeContentProvider =
                new BluetoothLegacyDbContentProvider(mMockContext);
        mMockContentResolver.addProvider(AUTHORITY, fakeContentProvider);

        Cursor c = new FakeCursor(OPP_DATABASE_SAMPLE);
        fakeContentProvider.mCursor = c;
        assertThat(DataMigration.oppDatabaseMigration(mMockContext)).isTrue();
    }

    private static final List<Pair<String, Object>> OPP_DATABASE_SAMPLE =
            Arrays.asList(
                    // String
                    new Pair(BluetoothShare.URI, "content"),
                    new Pair(BluetoothShare.FILENAME_HINT, "content"),
                    new Pair(BluetoothShare.MIMETYPE, "content"),
                    new Pair(BluetoothShare.DESTINATION, "content"),

                    // Int
                    new Pair(BluetoothShare.VISIBILITY, 42),
                    new Pair(BluetoothShare.USER_CONFIRMATION, 42),
                    new Pair(BluetoothShare.DIRECTION, 42),
                    new Pair(BluetoothShare.STATUS, 42),
                    new Pair("scanned" /* Constants.MEDIA_SCANNED */, 42),

                    // Long
                    new Pair(BluetoothShare.TOTAL_BYTES, 42L),
                    new Pair(BluetoothShare.TIMESTAMP, 42L)
    );

    private static class BluetoothLegacyDbContentProvider extends MockContentProvider {
        BluetoothLegacyDbContentProvider(Context ctx) {
            super(ctx);
        }
        String mLastMethod = null;
        Cursor mCursor = null;
        int mCallCount = 0;
        int mBundleCount = 0;
        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            mBundleCount++;
            return mCursor;
        }
        @Override
        public Bundle call(String method, String arg, Bundle extras) {
            mCallCount++;
            return null;
        }
    }

    private static class FakeCursor extends MockCursor {
        int mNumItem = 1;
        List<Pair<String, Object>> mRows;

        FakeCursor(List<Pair<String, Object>> rows) {
            mRows = rows;
        }

        @Override
        public String getString(int columnIndex) {
            return (String) (mRows.get(columnIndex).second);
        }

        @Override
        public byte[] getBlob(int columnIndex) {
            return (byte[]) (mRows.get(columnIndex).second);
        }

        @Override
        public int getInt(int columnIndex) {
            return (int) (mRows.get(columnIndex).second);
        }

        @Override
        public long getLong(int columnIndex) {
            return (long) (mRows.get(columnIndex).second);
        }

        @Override
        public boolean moveToNext() {
            return mNumItem-- > 0;
        }

        @Override
        public int getCount() {
            return 1;
        }

        @Override
        public int getColumnIndexOrThrow(String columnName) {
            for (int i = 0; i < mRows.size(); i++) {
                if (columnName.equals(mRows.get(i).first)) {
                    return i;
                }
            }
            throw new IllegalArgumentException("No such column: " + columnName);
        }

        @Override
        public int getColumnIndex(String columnName) {
            for (int i = 0; i < mRows.size(); i++) {
                if (columnName.equals(mRows.get(i).first)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public void close() {}
    }
}
