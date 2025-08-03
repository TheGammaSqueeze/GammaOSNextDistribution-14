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

package com.android.cts.tileserviceapp;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.Log;

/**
 * A simple activity that requests permissions and returns the result.
 */
public final class TileRequestActivity extends Activity {

    private static final String TAG = "TileRequestActivity";
    private String mTileLabel;
    private StatusBarManager mStatusBarManager;
    private Icon mIcon;

    private ComponentName getTileComponentName() {
        return new ComponentName(this, RequestTileService.class);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTileLabel = getString(R.string.tile_request_service_name);
        mStatusBarManager = getSystemService(StatusBarManager.class);
        mIcon = Icon.createWithResource(this, android.R.drawable.ic_dialog_alert);

        final Intent received = getIntent();
        Log.d(TAG, "Started with " + received);
        requestTile();
    }

    private void requestTile() {
        mStatusBarManager.requestAddTileService(
                getTileComponentName(),
                mTileLabel,
                mIcon,
                getMainExecutor(),
                integer -> {
                    setResult(integer);
                    finish();
                }
        );
    }
}
