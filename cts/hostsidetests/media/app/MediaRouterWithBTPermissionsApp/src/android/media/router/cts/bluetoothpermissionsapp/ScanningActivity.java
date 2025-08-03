/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.router.cts.bluetoothpermissionsapp;

import android.annotation.Nullable;
import android.app.Activity;
import android.media.MediaRouter2;
import android.media.RouteDiscoveryPreference;
import android.os.Bundle;
import android.view.WindowManager;

import java.util.List;

/**
 * Keeps the screen on and registers a scan request for the duration of its lifecycle.
 *
 * <p>Note that the screen must remain on in order to prevent the system server from discarding this
 * app's scan request due to low package importance.
 */
public final class ScanningActivity extends Activity {

    @Nullable private MediaRouter2 mMediaRouter2;
    private static final MediaRouter2.RouteCallback EMPTY_CALLBACK =
            new MediaRouter2.RouteCallback() {};

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setTurnScreenOn(true);
        setShowWhenLocked(true);
        mMediaRouter2 = MediaRouter2.getInstance(this);
        mMediaRouter2.registerRouteCallback(
                /* executor= */ Runnable::run,
                EMPTY_CALLBACK,
                new RouteDiscoveryPreference.Builder(
                                /* preferredFeatures= */ List.of("foo"), /* activeScan= */ true)
                        .build());
    }

    @Override
    protected void onDestroy() {
        if (mMediaRouter2 != null) {
            mMediaRouter2.unregisterRouteCallback(EMPTY_CALLBACK);
            mMediaRouter2.stopScan();
            mMediaRouter2 = null;
        }
        super.onDestroy();
    }
}
