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

package com.android.sdksandbox.cts.app;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.sdksandbox.cts.provider.storagetest.IStorageTestSdkApi;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Set;

@RunWith(JUnit4.class)
public class CtsSdkSandboxStorageTestApp {

    private static final String SDK_NAME = "com.android.sdksandbox.cts.provider.storagetest";

    @Rule public final ActivityScenarioRule mRule = new ActivityScenarioRule<>(TestActivity.class);

    private static final String KEY_TO_SYNC = "hello";
    private static final String BULK_SYNC_VALUE = "bulksync";
    private static final String UPDATE_VALUE = "update";

    private Context mContext;
    private SdkSandboxManager mSdkSandboxManager;
    private IStorageTestSdkApi mSdk;

    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();
        mSdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();
        mRule.getScenario();
        // unload SDK to fix flakiness
        mSdkSandboxManager.unloadSdk(SDK_NAME);
    }

    @After
    public void tearDown() {
        // unload SDK to fix flakiness
        if (mSdkSandboxManager != null) {
            mSdkSandboxManager.unloadSdk(SDK_NAME);
        }
    }

    @Test
    public void loadSdk() throws Exception {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        // Store the returned SDK interface so that we can interact with it later.
        mSdk = IStorageTestSdkApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
    }

    @Test
    public void testSharedPreferences_IsSyncedFromAppToSandbox() throws Exception {
        loadSdk();

        // Write to default shared preference
        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
        pref.edit().putString(KEY_TO_SYNC, BULK_SYNC_VALUE).commit();

        // Start syncing keys
        mSdkSandboxManager.addSyncedSharedPreferencesKeys(Set.of(KEY_TO_SYNC));
        // Allow some time for data to sync
        Thread.sleep(1000);

        // Verify same key can be read from the sandbox
        final String syncedValueInSandbox = mSdk.getSyncedSharedPreferencesString(KEY_TO_SYNC);
        assertThat(syncedValueInSandbox).isEqualTo(BULK_SYNC_VALUE);
    }

    @Test
    public void testSharedPreferences_SyncPropagatesUpdates() throws Exception {
        loadSdk();

        // Start syncing keys
        mSdkSandboxManager.addSyncedSharedPreferencesKeys(Set.of(KEY_TO_SYNC));

        // Update the default SharedPreferences
        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
        pref.edit().putString(KEY_TO_SYNC, UPDATE_VALUE).commit();
        // Allow some time for data to sync
        for (int i = 0; i < 10; i++) {
            Thread.sleep(500);

            // Verify update was propagated
            final String syncedValueInSandbox = mSdk.getSyncedSharedPreferencesString(KEY_TO_SYNC);
            if (syncedValueInSandbox.equals(UPDATE_VALUE)) {
                return;
            }
        }
        fail("failed to sync value in 5 seconds");
    }

    @Test
    public void testSharedPreferences_SyncStartedBeforeLoadingSdk() throws Exception {
        // Write to default shared preference
        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
        pref.edit().putString(KEY_TO_SYNC, BULK_SYNC_VALUE).commit();

        // Start syncing keys
        mSdkSandboxManager.addSyncedSharedPreferencesKeys(Set.of(KEY_TO_SYNC));

        // Load Sdk so that sandbox is started
        loadSdk();
        // Allow some time for data to sync
        Thread.sleep(1000);

        // Verify same key can be read from the sandbox
        final String syncedValueInSandbox = mSdk.getSyncedSharedPreferencesString(KEY_TO_SYNC);
        assertThat(syncedValueInSandbox).isEqualTo(BULK_SYNC_VALUE);
    }

    @Test
    public void testSharedPreferences_SyncRemoveKeys() throws Exception {
        loadSdk();

        // Write to default shared preference
        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
        pref.edit().putString(KEY_TO_SYNC, BULK_SYNC_VALUE).commit();

        // Start syncing keys
        mSdkSandboxManager.addSyncedSharedPreferencesKeys(Set.of(KEY_TO_SYNC));

        // Remove the key
        mSdkSandboxManager.removeSyncedSharedPreferencesKeys(Set.of(KEY_TO_SYNC));

        // Allow some time for data to sync
        Thread.sleep(1000);

        // Verify key has been removed from the sandbox
        final String syncedValueInSandbox = mSdk.getSyncedSharedPreferencesString(KEY_TO_SYNC);
        assertThat(syncedValueInSandbox).isEmpty();
    }

    @Test
    public void testSharedPreferences_SyncedDataClearedOnSandboxRestart() throws Exception {
        loadSdk();

        // Verify previously synced keys are not available in sandbox anymore
        final String syncedValueInSandbox = mSdk.getSyncedSharedPreferencesString(KEY_TO_SYNC);
        assertThat(syncedValueInSandbox).isEmpty();
    }
}
