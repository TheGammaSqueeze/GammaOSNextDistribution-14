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

import com.android.settingslib.net.NetworkCycleChartData;
import com.android.settingslib.net.NetworkCycleChartDataLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to manage the callbacks needed to calculate data usage cycles.
 */
public class DataUsageCycleManager {

    /**
     * Callback for when NetworkDataCycles is loaded.
     */
    public interface DataUsageCycleLoaderCallback {
        /**
         * Called when the data is successfully loaded from
         * {@link DataUsageCycleManager.NetworkDataCyclesResult}.
         */
        void onDataLoaded(List<NetworkCycleChartData> networkCycleChartData);
    }

    @VisibleForTesting
    static final int NETWORK_CYCLE_LOADER_ID = 11;

    private final Context mContext;
    private final NetworkTemplate mNetworkTemplate;
    private final List<DataUsageCycleManager.DataUsageCycleLoaderCallback> mListeners =
            new ArrayList<>();

    DataUsageCycleManager(Context context, NetworkTemplate networkTemplate) {
        mContext = context;
        mNetworkTemplate = networkTemplate;
    }

    /**
     * Registers a listener that will be notified once the data is loaded.
     */
    public void registerListener(DataUsageCycleManager.DataUsageCycleLoaderCallback listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    /**
     * Unregisters the listener.
     */
    public void unregisterListener(DataUsageCycleManager.DataUsageCycleLoaderCallback listener) {
        mListeners.remove(listener);
    }

    /**
     * Start calculating the storage stats.
     */
    public void startLoading(LoaderManager loaderManager) {
        loaderManager.restartLoader(NETWORK_CYCLE_LOADER_ID, null, new NetworkDataCyclesResult());
    }

    private void onReceive(List<NetworkCycleChartData> networkCycleDataLoaderList) {
        for (DataUsageCycleManager.DataUsageCycleLoaderCallback listener : mListeners) {
            listener.onDataLoaded(networkCycleDataLoaderList);
        }
    }

    /**
     * Callback to calculate network data cycles.
     */
    private class NetworkDataCyclesResult implements LoaderManager.LoaderCallbacks
            <List<NetworkCycleChartData>> {
        @Override
        public Loader<List<NetworkCycleChartData>> onCreateLoader(int id, Bundle args) {
            return NetworkCycleChartDataLoader.builder(mContext)
                    .setNetworkTemplate(mNetworkTemplate)
                    .build();
        }

        @Override
        public void onLoadFinished(@NonNull Loader<List<NetworkCycleChartData>> loader,
                List<NetworkCycleChartData> networkCycleDataLoaderList) {
            onReceive(networkCycleDataLoaderList);
        }

        @Override
        public void onLoaderReset(Loader<List<NetworkCycleChartData>> loader) {}
    }
}
