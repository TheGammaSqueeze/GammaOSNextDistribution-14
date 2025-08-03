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

package com.android.car.cluster.home;

import android.app.Activity;
import android.car.Car;
import android.car.cluster.ClusterActivityState;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

/**
 * Skeleton Activity for Home UI in Cluster display.
 */
public class ClusterHomeActivity extends Activity implements ClusterHomeActivityInterface {

    private static final String TAG = ClusterHomeActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = getLayoutInflater().inflate(R.layout.cluster_home_activity, /* root= */ null);
        setContentView(view);
        logIntent(getIntent());
    }

    /**
     * {@inheritDoc}
     *
     * <p>This activity is used for FULL mode only, thus always return {@code false}.
     *    Use {@link ClusterHomeActivityLightMode} for the LIGHT mode.
     */
    @Override
    public boolean isClusterInLightMode() {
        return false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        logIntent(intent);
    }

    private static void logIntent(Intent intent) {
        Log.d(TAG, "Got Intent=" + intent);
        if (intent.hasExtra(Car.CAR_EXTRA_CLUSTER_ACTIVITY_STATE)) {
            Bundle extra = intent.getBundleExtra(Car.CAR_EXTRA_CLUSTER_ACTIVITY_STATE);
            ClusterActivityState activityState = ClusterActivityState.fromBundle(extra);
            Log.d(TAG, ">> ClusterActivityState=" + activityState);
        }
    }
}