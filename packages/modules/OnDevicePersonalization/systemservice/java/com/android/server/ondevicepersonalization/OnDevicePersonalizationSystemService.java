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
package com.android.server.ondevicepersonalization;

import static android.ondevicepersonalization.OnDevicePersonalizationSystemServiceManager.ON_DEVICE_PERSONALIZATION_SYSTEM_SERVICE;

import android.content.Context;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemService;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemServiceCallback;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.SystemService;

import java.io.IOException;
import java.util.Objects;

/**
 * @hide
 */
public class OnDevicePersonalizationSystemService
        extends IOnDevicePersonalizationSystemService.Stub {
    private static final String TAG = "OnDevicePersonalizationSystemService";
    // TODO(b/302991763): set up per-user directory if needed.
    private static final String ODP_BASE_DIR = "/data/system/ondevicepersonalization/0/";
    private static final String CONFIG_FILE_IDENTIFIER = "CONFIG";
    public static final String PERSONALIZATION_STATUS_KEY = "PERSONALIZATION_STATUS";
    private BooleanFileDataStore mDataStore = null;
    public static final int INTERNAL_SERVER_ERROR = 500;
    public static final int KEY_NOT_FOUND_ERROR = 404;

    // TODO(b/302992251): use a manager to access configs instead of directly exposing DataStore.
    @VisibleForTesting
    OnDevicePersonalizationSystemService(Context context, BooleanFileDataStore dataStore)
                    throws IOException {
        Objects.requireNonNull(context);
        Objects.requireNonNull(dataStore);
        this.mDataStore = dataStore;
        mDataStore.initialize();
    }

    @Override public void onRequest(
            Bundle bundle,
            IOnDevicePersonalizationSystemServiceCallback callback) {
        try {
            callback.onResult(null);
        } catch (RemoteException e) {
            Log.e(TAG, "Callback error", e);
        }
    }

    @Override
    public void setPersonalizationStatus(boolean enabled,
                                         IOnDevicePersonalizationSystemServiceCallback callback) {
        Bundle result = new Bundle();
        try {
            mDataStore.put(PERSONALIZATION_STATUS_KEY, enabled);
            // Confirm the value was updated.
            Boolean statusResult = mDataStore.get(PERSONALIZATION_STATUS_KEY);
            if (statusResult == null || statusResult.booleanValue() != enabled) {
                callback.onError(INTERNAL_SERVER_ERROR);
                return;
            }
            // Echo the result back
            result.putBoolean(PERSONALIZATION_STATUS_KEY, statusResult);
            callback.onResult(result);
        } catch (IOException e) {
            Log.e(TAG, "Unable to persist personalization status", e);
            try {
                callback.onError(INTERNAL_SERVER_ERROR);
            } catch (RemoteException re) {
                Log.e(TAG, "Callback error", e);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Callback error", e);
        }
    }

    @Override
    public void readPersonalizationStatus(
                    IOnDevicePersonalizationSystemServiceCallback callback) {
        Boolean result = null;
        Bundle bundle = new Bundle();
        try {
            result = mDataStore.get(PERSONALIZATION_STATUS_KEY);
            if (result == null) {
                Log.d(TAG, "Unable to restore personalization status");
                callback.onError(KEY_NOT_FOUND_ERROR);
            } else {
                bundle.putBoolean(PERSONALIZATION_STATUS_KEY, result.booleanValue());
                callback.onResult(bundle);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Callback error", e);
        }
    }

    /** @hide */
    public static class Lifecycle extends SystemService {
        private OnDevicePersonalizationSystemService mService;

        /** @hide */
        public Lifecycle(Context context) {
            super(context);
            try {
                mService = new OnDevicePersonalizationSystemService(getContext(),
                        new BooleanFileDataStore(ODP_BASE_DIR, CONFIG_FILE_IDENTIFIER));
            } catch (IOException e) {
                Log.e(TAG, "Cannot initialize the system service.", e);
            }
        }

        /** @hide */
        @Override
        public void onStart() {
            publishBinderService(ON_DEVICE_PERSONALIZATION_SYSTEM_SERVICE, mService);
            Log.i(TAG, "OnDevicePersonalizationSystemService started!");
        }
    }
}
