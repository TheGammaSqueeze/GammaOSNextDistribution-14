/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.location.cts.common.gnss;

import android.content.Context;
import android.location.GnssNavigationMessage;
import android.location.LocationManager;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class GnssNavigationMessageCapture extends GnssNavigationMessage.Callback implements AutoCloseable {

    private final LocationManager mLocationManager;
    private final LinkedBlockingQueue<GnssNavigationMessage> mNavigationMessages;
    private final LinkedBlockingQueue<Integer> mStatuses;

    public GnssNavigationMessageCapture(Context context) {
        mLocationManager = context.getSystemService(LocationManager.class);
        mNavigationMessages = new LinkedBlockingQueue<>();
        mStatuses = new LinkedBlockingQueue<>();
    }

    public GnssNavigationMessage getNextNavigationMessage(long timeoutMs) throws InterruptedException {
        return mNavigationMessages.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public Integer getNextStatus(long timeoutMs) throws InterruptedException {
        return mStatuses.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void onGnssNavigationMessageReceived(GnssNavigationMessage message) {
        mNavigationMessages.add(message);
    }

    @Override
    public void onStatusChanged(int status) {
        mStatuses.add(status);
    }

    @Override
    public void close() {
        mLocationManager.unregisterGnssNavigationMessageCallback(this);
    }
}