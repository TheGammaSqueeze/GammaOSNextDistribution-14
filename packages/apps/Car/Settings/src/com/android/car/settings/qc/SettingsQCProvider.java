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

package com.android.car.settings.qc;

import android.content.Context;
import android.net.Uri;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.car.qc.QCItem;
import com.android.car.qc.provider.BaseQCProvider;
import com.android.car.settings.R;
import com.android.settingslib.utils.ThreadUtils;

import java.util.Map;
import java.util.Set;

/**
 * Remote Quick Control provider for CarSettings.
 */
public class SettingsQCProvider extends BaseQCProvider {
    private final Map<Uri, SettingsQCItem> mSubscribedItems = new ArrayMap<>();
    @VisibleForTesting
    final Map<Uri, SettingsQCBackgroundWorker> mSubscribedWorkers = new ArrayMap<>();

    private Set<String> mAllowListedPackages;

    @Override
    public boolean onCreate() {
        boolean returnVal = super.onCreate();
        mAllowListedPackages = Set.of(getContext().getResources().getStringArray(
                R.array.settings_qc_provider_package_allowlist));
        return returnVal;
    }

    @Override
    public QCItem onBind(Uri uri) {
        if (SettingsQCRegistry.isValidUri(uri)) {
            Context context = getContext();
            return getQCItemFromUri(context, uri).getQCItem();
        }
        throw new IllegalArgumentException("Unrecognized uri: " + uri);
    }

    @Override
    public void onSubscribed(Uri uri) {
        if (SettingsQCRegistry.isValidUri(uri)) {
            Context context = getContext();
            SettingsQCItem qcItem = getQCItemFromUri(context, uri);
            ThreadUtils.postOnMainThread(() -> startBackgroundWorker(qcItem, uri));
        }
    }

    @Override
    public void onUnsubscribed(Uri uri) {
        ThreadUtils.postOnMainThread(() -> stopBackgroundWorker(uri));
    }

    @Override
    public void onDestroy(Uri uri) {
        ThreadUtils.postOnMainThread(() -> SettingsQCBackgroundWorker.shutdown(uri));
    }

    @Override
    public void shutdown() {
        ThreadUtils.postOnMainThread(SettingsQCBackgroundWorker::shutdown);
    }

    private SettingsQCItem getQCItemFromUri(Context context, Uri uri) {
        if (mSubscribedItems.containsKey(uri)) {
            return mSubscribedItems.get(uri);
        }
        Uri newUri = SettingsQCRegistry.removeParameterFromUri(uri);
        Class clazz = SettingsQCRegistry.getQCClassByUri(newUri);
        if (clazz == null) {
            throw new IllegalArgumentException("No QCItem found for uri: " + uri);
        }

        return SettingsQCItem.createInstance(context, clazz);
    }

    private void startBackgroundWorker(SettingsQCItem qcItem, Uri uri) {
        Class workerClass = qcItem.getBackgroundWorkerClass();
        if (workerClass == null) {
            return;
        }

        if (mSubscribedWorkers.containsKey(uri)) {
            return;
        }

        SettingsQCBackgroundWorker worker = SettingsQCBackgroundWorker.getInstance(
                getContext(), qcItem, uri);
        mSubscribedWorkers.put(uri, worker);
        worker.subscribe();
        mSubscribedItems.put(uri, qcItem);
    }

    private void stopBackgroundWorker(Uri uri) {
        SettingsQCBackgroundWorker worker = mSubscribedWorkers.get(uri);
        if (worker != null) {
            worker.unsubscribe();
            mSubscribedWorkers.remove(uri);
            mSubscribedItems.remove(uri);
        }
    }

    @NonNull
    @Override
    protected Set<String> getAllowlistedPackages() {
        return mAllowListedPackages;
    }
}
