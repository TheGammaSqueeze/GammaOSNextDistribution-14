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
package com.android.car.settings.datausage;

import android.content.Context;
import android.net.NetworkTemplate;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.android.settingslib.net.NetworkCycleDataForUid;
import com.android.settingslib.net.NetworkCycleDataForUidLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to manage the callbacks needed to calculate data usage cycles for a specific app uid.
 */
public class AppSpecificDataUsageManager {

    /**
     * Callback when the NetworkDataCyclesForUid result is loaded.
     */
    public interface AppSpecificDataLoaderCallback {
        /**
         * Called when the data is successfully loaded from
         * {@link AppSpecificDataUsageManager.NetworkDataCyclesForUidResult}.
         */
        void onDataLoaded(List<NetworkCycleDataForUid> networkCycleChartDataForUid);
    }

    @VisibleForTesting
    static final int NETWORK_CYCLE_LOADER_ID = 12;

    private final Context mContext;
    private final NetworkTemplate mNetworkTemplate;
    private final int mUid;
    private final List<AppSpecificDataUsageManager.AppSpecificDataLoaderCallback> mListeners =
            new ArrayList<>();

    AppSpecificDataUsageManager(Context context, NetworkTemplate networkTemplate, int uid) {
        mContext = context;
        mNetworkTemplate = networkTemplate;
        mUid = uid;
    }

    /**
     * Registers a listener that will be notified once the data is loaded.
     */
    public void registerListener(
            AppSpecificDataUsageManager.AppSpecificDataLoaderCallback listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    /**
     * Unregisters the listener.
     */
    public void unregisterListener(
            AppSpecificDataUsageManager.AppSpecificDataLoaderCallback listener) {
        mListeners.remove(listener);
    }

    /**
     * Start calculating the storage stats for app uid.
     */
    public void startLoading(LoaderManager loaderManager) {
        loaderManager.restartLoader(NETWORK_CYCLE_LOADER_ID, null,
                new NetworkDataCyclesForUidResult());
    }

    private void onReceive(List<NetworkCycleDataForUid> networkCycleDataLoaderList) {
        for (AppSpecificDataUsageManager.AppSpecificDataLoaderCallback listener : mListeners) {
            listener.onDataLoaded(networkCycleDataLoaderList);
        }
    }

    /**
     * Callback to calculate network data cycles for app uid.
     */
    private class NetworkDataCyclesForUidResult implements LoaderManager.LoaderCallbacks
            <List<NetworkCycleDataForUid>> {
        @Override
        public Loader<List<NetworkCycleDataForUid>> onCreateLoader(int id, Bundle args) {
            NetworkCycleDataForUidLoader.Builder builder =
                    NetworkCycleDataForUidLoader.builder(mContext)
                            .setRetrieveDetail(true)
                            .addUid(mUid);
            builder.setNetworkTemplate(mNetworkTemplate);
            return builder.build();
        }

        @Override
        public void onLoadFinished(@NonNull Loader<List<NetworkCycleDataForUid>> loader,
                List<NetworkCycleDataForUid> networkCycleDataLoaderList) {
            onReceive(networkCycleDataLoaderList);
        }

        @Override
        public void onLoaderReset(Loader<List<NetworkCycleDataForUid>> loader) {}
    }
}
