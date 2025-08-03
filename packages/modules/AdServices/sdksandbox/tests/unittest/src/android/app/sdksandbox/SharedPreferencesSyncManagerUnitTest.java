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

package android.app.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.annotation.Nullable;
import android.app.sdksandbox.testutils.StubSdkSandboxManagerService;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.test.InstrumentationRegistry;

import com.android.internal.annotations.GuardedBy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Tests {@link SharedPreferencesSyncManager} APIs. */
@RunWith(JUnit4.class)
public class SharedPreferencesSyncManagerUnitTest {

    private static final String KEY_TO_UPDATE = "hello1";
    private static final SharedPreferencesKey KEY_WITH_TYPE_TO_UPDATE =
            new SharedPreferencesKey(KEY_TO_UPDATE, SharedPreferencesKey.KEY_TYPE_STRING);
    private static final Map<String, String> TEST_DATA =
            Map.of(KEY_TO_UPDATE, "world1", "hello2", "world2", "empty", "");
    private static final Set<String> KEYS_TO_SYNC = Set.of(KEY_TO_UPDATE, "hello2", "empty");
    private static final Set<SharedPreferencesKey> KEYS_WITH_TYPE_TO_SYNC =
            Set.of(
                    new SharedPreferencesKey(KEY_TO_UPDATE, SharedPreferencesKey.KEY_TYPE_STRING),
                    new SharedPreferencesKey("hello2", SharedPreferencesKey.KEY_TYPE_STRING),
                    new SharedPreferencesKey("empty", SharedPreferencesKey.KEY_TYPE_STRING));

    private static final int SANDBOX_NOT_AVAILABLE_ERROR_CODE =
            ISharedPreferencesSyncCallback.SANDBOX_NOT_AVAILABLE;
    private static final String SANDBOX_NOT_AVAILABLE_ERROR_MSG = "Sandbox has not started yet";

    private SharedPreferencesSyncManager mSyncManager;
    private FakeSdkSandboxManagerService mSdkSandboxManagerService;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mSdkSandboxManagerService = new FakeSdkSandboxManagerService();
        mSyncManager = new SharedPreferencesSyncManager(mContext, mSdkSandboxManagerService);
    }

    @After
    public void tearDown() throws Exception {
        getDefaultSharedPreferences().edit().clear().commit();
    }

    @Test
    public void test_sharedPreferencesSyncManager_isSingleton() throws Exception {
        final SharedPreferencesSyncManager manager1 =
                SharedPreferencesSyncManager.getInstance(mContext, mSdkSandboxManagerService);
        final SharedPreferencesSyncManager manager2 =
                SharedPreferencesSyncManager.getInstance(mContext, mSdkSandboxManagerService);
        assertThat(manager1).isSameInstanceAs(manager2);

        Context mockContext = Mockito.mock(Context.class);
        Mockito.when(mockContext.getPackageName()).thenReturn(mContext.getPackageName());
        final SharedPreferencesSyncManager manager3 =
                SharedPreferencesSyncManager.getInstance(mockContext, mSdkSandboxManagerService);
        assertThat(manager1).isSameInstanceAs(manager3);
    }

    @Test
    public void test_sharedPreferencesSyncManager_isSingletonPerPackage() throws Exception {
        final SharedPreferencesSyncManager manager1 =
                SharedPreferencesSyncManager.getInstance(mContext, mSdkSandboxManagerService);

        Context mockContext = Mockito.mock(Context.class);
        final SharedPreferencesSyncManager manager2 =
                SharedPreferencesSyncManager.getInstance(mockContext, mSdkSandboxManagerService);
        assertThat(manager1).isNotSameInstanceAs(manager2);
    }

    @Test
    public void test_addSyncKeys_isIncremental() throws Exception {
        // Add one key
        mSyncManager.addSharedPreferencesSyncKeys(Set.of("foo"));
        assertThat(mSyncManager.getSharedPreferencesSyncKeys()).containsExactly("foo");

        // Add another key
        mSyncManager.addSharedPreferencesSyncKeys(Set.of("bar"));
        assertThat(mSyncManager.getSharedPreferencesSyncKeys()).containsExactly("foo", "bar");
    }

    @Test
    public void test_addSyncKeys_isIncremental_sameKeyCanBeAdded() throws Exception {
        mSyncManager.addSharedPreferencesSyncKeys(Set.of("foo"));
        assertThat(mSyncManager.getSharedPreferencesSyncKeys()).containsExactly("foo");

        mSyncManager.addSharedPreferencesSyncKeys(Set.of("foo"));
        assertThat(mSyncManager.getSharedPreferencesSyncKeys()).containsExactly("foo");
    }

    @Test
    public void test_removeKeys() throws Exception {
        mSyncManager.addSharedPreferencesSyncKeys(Set.of("foo", "bar"));

        // Remove key
        mSyncManager.removeSharedPreferencesSyncKeys(Set.of("foo"));

        assertThat(mSyncManager.getSharedPreferencesSyncKeys()).containsExactly("bar");
    }

    @Test
    public void test_removeKeys_updateSentForRemoval() throws Exception {
        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);

        // Remove key
        mSyncManager.removeSharedPreferencesSyncKeys(Set.of(KEY_TO_UPDATE));

        final SharedPreferencesUpdate update = mSdkSandboxManagerService.getLastUpdate();
        assertThat(update.getData().keySet()).doesNotContain(Set.of(KEY_TO_UPDATE));
        assertThat(update.getKeysInUpdate()).containsExactly(KEY_WITH_TYPE_TO_UPDATE);
    }

    @Test
    public void test_bulkSync_syncSpecifiedKeys() throws Exception {
        // Populate default shared preference with test data
        populateDefaultSharedPreference(TEST_DATA);
        // Add specific shared keys that we want to sync
        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);

        // Verify that sync manager passes the correct data to SdkSandboxManager
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate().getData();
        assertThat(mSdkSandboxManagerService.getCallingPackageName())
                .isEqualTo(mContext.getPackageName());
        assertThat(capturedData.keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
        for (String key : TEST_DATA.keySet()) {
            assertThat(capturedData.getString(key)).isEqualTo(TEST_DATA.get(key));
        }
    }

    @Test
    public void test_bulkSync_syncMissingKeys() throws Exception {
        // Add specific shared keys that we want to sync
        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);

        // Verify that sync manager passes empty value for missing keys
        final SharedPreferencesUpdate update = mSdkSandboxManagerService.getLastUpdate();
        assertThat(update.getKeysInUpdate()).containsExactlyElementsIn(KEYS_WITH_TYPE_TO_SYNC);
        assertThat(update.getData().keySet()).isEmpty();
    }

    @Test
    public void test_bulkSync_ignoreUnspecifiedKeys() throws Exception {
        // Populate default shared preference and set specific keys for sycing
        populateDefaultSharedPreference(TEST_DATA);
        // Populate extra data outside of shared key list
        populateDefaultSharedPreference(Map.of("extraKey", "notSpecifiedByApi"));

        // Set specific shared keys that we want to sync
        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);

        // Verify that sync manager passes the correct data to SdkSandboxManager
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate().getData();
        assertThat(capturedData.keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
    }

    @Test
    public void test_bulkSync_supportsAllTypesOfValues() throws Exception {
        // Populate default shared preference with all valid types

        final SharedPreferences pref = getDefaultSharedPreferences();
        final SharedPreferences.Editor editor = pref.edit();
        editor.putString("string", "value");
        editor.putBoolean("boolean", true);
        editor.putFloat("float", 1.2f);
        editor.putInt("int", 1);
        editor.putLong("long", 1L);
        editor.putStringSet("set", Set.of("value"));
        editor.commit();

        // Set keys to sync and then sync data
        final Set<String> keysToSync = Set.of("string", "boolean", "float", "int", "long", "set");
        mSyncManager.addSharedPreferencesSyncKeys(keysToSync);

        // Verify that sync manager passes the correct data to SdkSandboxManager
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate().getData();
        assertThat(capturedData.getString("string")).isEqualTo(pref.getString("string", ""));
        assertThat(capturedData.getBoolean("boolean")).isEqualTo(pref.getBoolean("boolean", false));
        assertThat(capturedData.getFloat("float")).isEqualTo(pref.getFloat("float", 0.0f));
        assertThat(capturedData.getInt("int")).isEqualTo(pref.getInt("int", 0));
        assertThat(capturedData.getLong("long")).isEqualTo(pref.getLong("long", 0L));
        assertThat(capturedData.getStringArrayList("set"))
                .containsExactlyElementsIn(pref.getStringSet("set", Collections.emptySet()));
        assertThat(capturedData.keySet()).hasSize(6);
    }

    @Test
    public void test_updateListener_syncsFurtherUpdates() throws Exception {
        // Set specified keys for sycing and register listener
        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);

        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, "update").commit();

        // Verify that SyncManager tried to sync only twice: once for bulk and once for live update.
        mSdkSandboxManagerService.blockForReceivingUpdates(2);
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate().getData();
        assertThat(capturedData.keySet()).containsExactly(KEY_TO_UPDATE);
        assertThat(capturedData.getString(KEY_TO_UPDATE)).isEqualTo("update");
    }

    @Test
    public void test_updateListener_ignoresUnspecifiedKeys() throws Exception {
        // Set specified keys for sycing and register listener
        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);
        mSdkSandboxManagerService.clearUpdates();

        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().putString("unspecified_key", "update").commit();

        // Verify SdkSandboxManagerService does not receive the update for unspecified key
        assertThrows(
                TimeoutException.class,
                () -> mSdkSandboxManagerService.blockForReceivingUpdates(1));
    }

    @Test
    public void test_updateListener_supportsAllTypesOfValues() throws Exception {
        // Set keys to sync and then sync data to register listener
        final Set<String> keysToSync = Set.of("string", "boolean", "float", "int", "long", "set");
        mSyncManager.addSharedPreferencesSyncKeys(keysToSync);

        // Clear the bulk update for ease of reasoning
        mSdkSandboxManagerService.clearUpdates();

        // Update the shared preference
        final SharedPreferences pref = getDefaultSharedPreferences();
        final SharedPreferences.Editor editor = pref.edit();
        editor.putString("string", "value");
        editor.putBoolean("boolean", true);
        editor.putFloat("float", 1.2f);
        editor.putInt("int", 1);
        editor.putLong("long", 1L);
        editor.putStringSet("set", Set.of("value"));
        editor.commit();

        // Verify that sync manager receives one bundle for each key update
        mSdkSandboxManagerService.blockForReceivingUpdates(6);
        final ArrayList<SharedPreferencesUpdate> allUpdates =
                mSdkSandboxManagerService.getAllUpdates();
        assertThat(allUpdates).hasSize(6);
        for (SharedPreferencesUpdate update : allUpdates) {
            final Bundle data = update.getData();
            assertThat(data.keySet()).hasSize(1);
            final String key = data.keySet().toArray()[0].toString();
            if (key.equals("string")) {
                assertThat(data.getString(key)).isEqualTo(pref.getString(key, ""));
            } else if (key.equals("boolean")) {
                assertThat(data.getBoolean(key)).isEqualTo(pref.getBoolean(key, false));
            } else if (key.equals("float")) {
                assertThat(data.getFloat(key)).isEqualTo(pref.getFloat(key, 0.0f));
            } else if (key.equals("int")) {
                assertThat(data.getInt(key)).isEqualTo(pref.getInt(key, 0));
            } else if (key.equals("long")) {
                assertThat(data.getLong(key)).isEqualTo(pref.getLong(key, 0L));
            } else if (key.equals("set")) {
                assertThat(data.getStringArrayList(key))
                        .containsExactlyElementsIn(pref.getStringSet(key, Collections.emptySet()));
            } else {
                fail("Unknown key found");
            }
        }
    }

    /** Test that we can handle removal of keys */
    @Test
    public void test_updateListener_removeKey() throws Exception {
        populateDefaultSharedPreference(TEST_DATA);
        // Set keys to sync and then sync data to register listener
        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);

        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().remove(KEY_TO_UPDATE).commit();

        // Verify that SyncManager tried to sync only twice: once for bulk and once for live update.
        mSdkSandboxManagerService.blockForReceivingUpdates(2);
        final SharedPreferencesUpdate update = mSdkSandboxManagerService.getLastUpdate();
        assertThat(update.getData().keySet()).doesNotContain(KEY_TO_UPDATE);
        assertThat(update.getKeysInUpdate()).containsExactly(KEY_WITH_TYPE_TO_UPDATE);
    }

    /** Test that we can handle removal of keys by putting null */
    @Test
    public void test_updateListener_putNullValueForKey() throws Exception {
        populateDefaultSharedPreference(TEST_DATA);
        // Set keys to sync and then sync data to register listener
        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);

        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, null).commit();

        // Verify that SyncManager tried to sync only twice: once for bulk and once for live update.
        mSdkSandboxManagerService.blockForReceivingUpdates(2);
        final SharedPreferencesUpdate update = mSdkSandboxManagerService.getLastUpdate();
        assertThat(update.getData().keySet()).doesNotContain(KEY_TO_UPDATE);
        assertThat(update.getKeysInUpdate()).containsExactly(KEY_WITH_TYPE_TO_UPDATE);
    }

    @Test
    public void test_updateListener_removeAllKeys() throws Exception {
        populateDefaultSharedPreference(TEST_DATA);
        // Set keys to sync and then sync data to register listener
        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);

        // Clear all keys
        getDefaultSharedPreferences().edit().clear().commit();

        // Verify that SyncManager tried to sync only twice: once for bulk and once for live update.
        mSdkSandboxManagerService.blockForReceivingUpdates(2);
        final SharedPreferencesUpdate lastUpdate = mSdkSandboxManagerService.getLastUpdate();
        assertThat(lastUpdate.getData().keySet()).isEmpty();
        assertThat(lastUpdate.getKeysInUpdate()).containsExactlyElementsIn(KEYS_WITH_TYPE_TO_SYNC);
    }

    @Test
    public void test_updateListener_multipleCalls_updateListenerRegisteredOnce() throws Exception {
        // Add keys to sync and then sync data to register listener
        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);

        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);

        // Verify updating SharedPreferences results in only one update
        mSdkSandboxManagerService.clearUpdates(); // For cleaner observation
        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, "update").commit();
        // Only one update should be received
        assertThrows(
                TimeoutException.class,
                () -> mSdkSandboxManagerService.blockForReceivingUpdates(2));
    }

    @Test
    public void test_syncDataFromClient_reusesCallback() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);
        mSdkSandboxManagerService.blockForReceivingUpdates(1);
        final ISharedPreferencesSyncCallback bulkSyncCallback =
                mSdkSandboxManagerService.getLastCallback();

        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, "update").commit();
        mSdkSandboxManagerService.blockForReceivingUpdates(2);
        final ISharedPreferencesSyncCallback updateListenerCallback1 =
                mSdkSandboxManagerService.getLastCallback();

        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, "update2").commit();
        mSdkSandboxManagerService.blockForReceivingUpdates(3);
        final ISharedPreferencesSyncCallback updateListenerCallback2 =
                mSdkSandboxManagerService.getLastCallback();

        assertThat(bulkSyncCallback).isSameInstanceAs(updateListenerCallback1);
        assertThat(bulkSyncCallback).isSameInstanceAs(updateListenerCallback2);
    }

    /** Test that we support starting sync before sandbox is created */
    @Test
    public void test_onError_bulksync_SandboxNotAvailableError() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);

        // Report sandbox has not been created
        mSdkSandboxManagerService
                .getLastCallback()
                .onError(SANDBOX_NOT_AVAILABLE_ERROR_CODE, SANDBOX_NOT_AVAILABLE_ERROR_MSG);
        // Verify that sync was still running
        assertThat(mSyncManager.isWaitingForSandbox()).isTrue();
    }

    /** Test that we support starting sync before sandbox is created */
    @Test
    public void test_onError_updateListener_sandboxNotAvailableError() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);

        // Update the SharedPreference to trigger listeners
        mSdkSandboxManagerService.clearUpdates(); // For ease of reasoning
        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, "update").commit();

        // Wait until update is received
        mSdkSandboxManagerService.blockForReceivingUpdates(1);
        // Report an error via the callback
        mSdkSandboxManagerService
                .getLastCallback()
                .onError(SANDBOX_NOT_AVAILABLE_ERROR_CODE, SANDBOX_NOT_AVAILABLE_ERROR_MSG);
        // Verify that sync is in waiting state now
        assertThat(mSyncManager.isWaitingForSandbox()).isTrue();
    }

    @Test
    public void test_onError_updateListener_notRegisteredWhenWaitingForSandbox() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);

        // Send SyncManager to waiting state
        mSdkSandboxManagerService
                .getLastCallback()
                .onError(SANDBOX_NOT_AVAILABLE_ERROR_CODE, SANDBOX_NOT_AVAILABLE_ERROR_MSG);

        // Update the SharedPreference to trigger listeners
        mSdkSandboxManagerService.clearUpdates(); // For ease of reasoning
        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, "update").commit();

        // Verify update not received
        assertThrows(
                TimeoutException.class,
                () -> mSdkSandboxManagerService.blockForReceivingUpdates(1));
    }

    @Test
    public void test_onSandboxStart_bulkSyncRetries() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.addSharedPreferencesSyncKeys(KEYS_TO_SYNC);

        // Send SyncManager to waiting state
        mSdkSandboxManagerService
                .getLastCallback()
                .onError(SANDBOX_NOT_AVAILABLE_ERROR_CODE, SANDBOX_NOT_AVAILABLE_ERROR_MSG);

        // Notify syncmanager eventually when sandbox starts
        final ISharedPreferencesSyncCallback firstCallback =
                mSdkSandboxManagerService.getLastCallback();
        mSdkSandboxManagerService.getLastCallback().onSandboxStart();

        // Verify another bulk sync update is sent to SdkSandboxManagerService
        mSdkSandboxManagerService.blockForReceivingUpdates(2);

        // Notify again, but this time it should not trigger a new update since we were not waiting.
        mSdkSandboxManagerService.getLastCallback().onSandboxStart();
        firstCallback.onSandboxStart();
        assertThrows(
                TimeoutException.class,
                () -> mSdkSandboxManagerService.blockForReceivingUpdates(3));
    }

    /** Write all key-values provided in the map to app's default SharedPreferences */
    private void populateDefaultSharedPreference(Map<String, String> data) {
        final SharedPreferences.Editor editor = getDefaultSharedPreferences().edit();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            editor.putString(entry.getKey(), entry.getValue());
        }
        editor.apply();
    }

    private SharedPreferences getDefaultSharedPreferences() {
        final Context appContext = mContext.getApplicationContext();
        return PreferenceManager.getDefaultSharedPreferences(appContext);
    }

    private static class FakeSdkSandboxManagerService extends StubSdkSandboxManagerService {
        @GuardedBy("this")
        private ArrayList<SharedPreferencesUpdate> mUpdateCache = new ArrayList<>();

        @GuardedBy("this")
        private ISharedPreferencesSyncCallback mLastCallback = null;

        @GuardedBy("this")
        private String mCallingPackageName = null;

        /** Gets updated when {@link blockForReceivingUpdates} is called. */
        private CountDownLatch mWaitForMoreUpdates = new CountDownLatch(0);

        @Override
        public synchronized void syncDataFromClient(
                String callingPackageName,
                SandboxLatencyInfo sandboxLatencyInfo,
                SharedPreferencesUpdate update,
                ISharedPreferencesSyncCallback callback) {
            if (mCallingPackageName == null) {
                mCallingPackageName = callingPackageName;
            } else {
                assertThat(mCallingPackageName).isEqualTo(callingPackageName);
            }

            mUpdateCache.add(update);
            mLastCallback = callback;
            mWaitForMoreUpdates.countDown();
        }

        public synchronized String getCallingPackageName() {
            return mCallingPackageName;
        }

        @Nullable
        public synchronized SharedPreferencesUpdate getLastUpdate() {
            if (mUpdateCache.isEmpty()) {
                throw new AssertionError(
                        "Fake SdkSandboxManagerService did not receive any update");
            }
            return mUpdateCache.get(mUpdateCache.size() - 1);
        }

        @Nullable
        public synchronized ISharedPreferencesSyncCallback getLastCallback() {
            return mLastCallback;
        }

        public synchronized ArrayList<SharedPreferencesUpdate> getAllUpdates() {
            return new ArrayList<>(mUpdateCache);
        }

        public synchronized int getNumberOfUpdatesReceived() {
            return mUpdateCache.size();
        }

        public synchronized void clearUpdates() {
            mUpdateCache.clear();
        }

        public void blockForReceivingUpdates(int numberOfUpdates) throws Exception {
            synchronized (this) {
                final int updatesNeeded = numberOfUpdates - getNumberOfUpdatesReceived();
                if (updatesNeeded <= 0) {
                    return;
                }
                mWaitForMoreUpdates = new CountDownLatch(updatesNeeded);
            }
            if (!mWaitForMoreUpdates.await(5000, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException(
                        "Failed to receive required number of updates. Required: "
                                + numberOfUpdates
                                + ", but found: "
                                + getNumberOfUpdatesReceived());
            }
        }
    }
}
