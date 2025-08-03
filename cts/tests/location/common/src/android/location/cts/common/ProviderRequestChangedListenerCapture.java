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

package android.location.cts.common;

import android.content.Context;
import android.location.LocationManager;
import android.location.provider.ProviderRequest;
import android.util.Pair;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ProviderRequest.ChangedListener} that automatically unregisters itself
 * when closed.
 */
public class ProviderRequestChangedListenerCapture implements
        ProviderRequest.ChangedListener, AutoCloseable {
    private final LocationManager mLocationManager;
    private final LinkedBlockingQueue<Pair<String, ProviderRequest>> mProviderRequestChanges;

    public ProviderRequestChangedListenerCapture(Context context) {
        mLocationManager = context.getSystemService(LocationManager.class);
        mProviderRequestChanges = new LinkedBlockingQueue<>();
    }

    public Pair<String, ProviderRequest> getNextProviderRequest(long timeoutMs)
            throws InterruptedException {
        return mProviderRequestChanges.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onProviderRequestChanged(String provider, ProviderRequest request) {
        mProviderRequestChanges.add(new Pair<>(provider, request));
    }

    @Override
    public void close() throws Exception {
        mLocationManager.removeProviderRequestChangedListener(this);
    }
}
