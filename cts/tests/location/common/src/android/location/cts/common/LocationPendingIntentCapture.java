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

package android.location.cts.common;

import static android.location.LocationManager.KEY_FLUSH_COMPLETE;
import static android.location.LocationManager.KEY_LOCATION_CHANGED;
import static android.location.LocationManager.KEY_PROVIDER_ENABLED;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;

import com.google.common.base.Preconditions;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LocationPendingIntentCapture extends BroadcastCapture {

    private static final String ACTION = "android.location.cts.LOCATION_BROADCAST";
    private static final AtomicInteger sRequestCode = new AtomicInteger(0);

    private final LocationManager mLocationManager;
    private final PendingIntent mPendingIntent;
    private final LinkedBlockingQueue<Location> mLocations;
    private final LinkedBlockingQueue<Integer> mFlushes;
    private final LinkedBlockingQueue<Boolean> mProviderChanges;

    public LocationPendingIntentCapture(Context context) {
        super(context);

        mLocationManager = context.getSystemService(LocationManager.class);
        mPendingIntent = PendingIntent.getBroadcast(context, sRequestCode.getAndIncrement(),
                new Intent(ACTION)
                        .setPackage(context.getPackageName())
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
        mLocations = new LinkedBlockingQueue<>();
        mFlushes = new LinkedBlockingQueue<>();
        mProviderChanges = new LinkedBlockingQueue<>();

        register(ACTION);
    }

    public PendingIntent getPendingIntent() {
        return mPendingIntent;
    }

    public Location getNextLocation(long timeoutMs) throws InterruptedException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new AssertionError("getNextLocation() called from main thread");
        }

        return mLocations.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public Integer getNextFlush(long timeoutMs) throws InterruptedException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new AssertionError("getNextFlush() called from main thread");
        }

        return mFlushes.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public Boolean getNextProviderChange(long timeoutMs) throws InterruptedException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new AssertionError("getNextProviderChange() called from main thread");
        }

        return mProviderChanges.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        super.close();
        mLocationManager.removeUpdates(mPendingIntent);
        mPendingIntent.cancel();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent.hasExtra(KEY_PROVIDER_ENABLED)) {
            mProviderChanges.add(intent.getBooleanExtra(KEY_PROVIDER_ENABLED, false));
        } else if (intent.hasExtra(KEY_LOCATION_CHANGED)) {
            mLocations.add(intent.getParcelableExtra(KEY_LOCATION_CHANGED));
        } else if (intent.hasExtra(KEY_FLUSH_COMPLETE)) {
            int requestCode = intent.getIntExtra(KEY_FLUSH_COMPLETE, Integer.MIN_VALUE);
            Preconditions.checkArgument(requestCode != Integer.MIN_VALUE);
            mFlushes.add(requestCode);
        }
    }
}