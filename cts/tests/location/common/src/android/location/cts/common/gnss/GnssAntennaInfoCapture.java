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
import android.location.GnssAntennaInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class GnssAntennaInfoCapture implements GnssAntennaInfo.Listener, AutoCloseable {

    private final LocationManager mLocationManager;
    private final LinkedBlockingQueue<List<GnssAntennaInfo>> mAntennaInfo;

    public GnssAntennaInfoCapture(Context context) {
        mLocationManager = context.getSystemService(LocationManager.class);
        mAntennaInfo = new LinkedBlockingQueue<>();
    }

    public List<GnssAntennaInfo> getNextAntennaInfo(long timeoutMs) throws InterruptedException {
        return mAntennaInfo.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onGnssAntennaInfoReceived(List<GnssAntennaInfo> gnssAntennaInfos) {
        mAntennaInfo.add(gnssAntennaInfos);
    }

    @Override
    public void close() {
        mLocationManager.unregisterAntennaInfoListener(this);
    }
}