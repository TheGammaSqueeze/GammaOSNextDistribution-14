/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.car.networking.preferenceupdater.components;

import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.OemNetworkPreferences;
import android.util.Log;
import android.util.SparseArray;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Class wraps OemNetworkPreferences (PANS) APIs and routine around it */
public final class OemNetworkPreferencesAdapter {
    private static final String TAG = OemNetworkPreferencesAdapter.class.getSimpleName();

    // Seconds to wait for setOemNetworkPreference() call to complete
    private static final int PANS_CALL_TIMEOUT_SEC = 5;
    public static final int[] OEM_NETWORK_PREFERENCE_ARRAY =
            new int[] {
                OEM_NETWORK_PREFERENCE_OEM_PAID,
                OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK,
                OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY,
                OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY,
            };

    private final ConnectivityManager mConnectivityManager;
    private final Context mContext;

    private static class OemListenerCallback implements Runnable {
        final CompletableFuture<Object> mDone = new CompletableFuture<>();

        @Override
        public void run() {
            mDone.complete(new Object());
        }

        void expectOnComplete() throws Exception {
            mDone.get(PANS_CALL_TIMEOUT_SEC, TimeUnit.SECONDS);
        }
    }

    public OemNetworkPreferencesAdapter(Context ctx) {
        mConnectivityManager = ctx.getSystemService(ConnectivityManager.class);
        mContext = ctx;
    }

    /**
     * Accepts mapping <OemNetworkPreference.<id>> -> <Set of applications> and calls PANS APIs to
     * apply them.
     */
    public void applyPreference(@Nullable SparseArray<Set<String>> preference) {
        // We want to create listener and wait for the call to end before proceeding further.
        // To address that better, we will use CompletableFuture.
        final OemListenerCallback listener = new OemListenerCallback();
        mConnectivityManager.setOemNetworkPreference(
                generatePrefsFrom(preference), r -> r.run(), listener);
        // We don't want to be blocked and wait for results forver, thus we will wait for 5 seconds
        // before cancelling future.
        try {
            listener.expectOnComplete();
            Log.d(TAG, "New OEM Network Preferences are now applied.");
        } catch (Exception ex) {
            /**
             * From 4 potential exceptions: - CancellationException - if this future was cancelled -
             * ExecutionException - if this future completed exceptionally - InterruptedException -
             * if the current thread was interrupted while waiting - TimeoutException - if the wait
             * timed out For now since we are not handling exceptions customly, we simply print the
             * exception and silence it.
             *
             * TODO(b/183749278): Improve exceptoin handling in this case.
             */
            Log.e(TAG, "Call into setOemNetworkPreference() has failed with exception", ex);
        }
    }

    /**
     * This should reset the OEM Network preferences set by this application or by any other
     * application which calls into setOemNetworkPreferences() API.
     */
    public void resetNetworkPreferences() {
        // Considering that applyPreference will call into setOemNetworkPreference() all we need
        // is to pass null and it will delete PersonalStorage data and will reset PANS.
        applyPreference(null);
    }

    private OemNetworkPreferences generatePrefsFrom(@Nullable SparseArray<Set<String>> preference) {
        OemNetworkPreferences.Builder builder = new OemNetworkPreferences.Builder();

        // Iterate through all available oem network preference types
        for (int type : OEM_NETWORK_PREFERENCE_ARRAY) {
            Set<String> apps =
                    preference == null
                            ? OemAppsManager.getDefaultAppsFor(mContext, type)
                            : preference.get(type);
            addPreferenceFromAppsSet(type, builder, apps);
        }
        return builder.build();
    }

    private void addPreferenceFromAppsSet(
            int type, @NonNull OemNetworkPreferences.Builder builder, @NonNull Set<String> apps) {
        for (String app : apps) {
            builder.addNetworkPreference(app, type);
        }
    }
}
