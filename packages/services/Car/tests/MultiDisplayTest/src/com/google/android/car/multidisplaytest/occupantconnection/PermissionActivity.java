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

package com.google.android.car.multidisplaytest.occupantconnection;

import static com.google.android.car.multidisplaytest.occupantconnection.Constants.ACCEPTED_RESULT_CODE;
import static com.google.android.car.multidisplaytest.occupantconnection.Constants.ACTION_CONNECTION_CANCELLED;
import static com.google.android.car.multidisplaytest.occupantconnection.Constants.KEY_MESSAGE_RECEIVER;
import static com.google.android.car.multidisplaytest.occupantconnection.Constants.REJECTED_RESULT_CODE;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Slog;
import android.widget.Button;

import androidx.annotation.Nullable;

import com.google.android.car.multidisplaytest.R;

public class PermissionActivity extends Activity {

    private String mTag;
    private ResultReceiver mResultReceiver;

    private final BroadcastReceiver mConnectionCancellationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_CONNECTION_CANCELLED.equals(intent.getAction())) {
                Slog.d(mTag, "Connection request was cancelled by the sender");
                finish();
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.occupant_connection_permission_activity);
        mTag = "OccupantConnection##" + getUserId();
        mResultReceiver = getIntent().getParcelableExtra(KEY_MESSAGE_RECEIVER);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Button positiveButton = findViewById(R.id.positive);
        positiveButton.setOnClickListener(v -> {
            Slog.d(mTag, "User approved the connection request");
            mResultReceiver.send(ACCEPTED_RESULT_CODE, null);
            finish();
        });
        Button negativeButton = findViewById(R.id.negative);
        negativeButton.setOnClickListener(v -> {
            Slog.d(mTag, "User rejected the connection request");
            mResultReceiver.send(REJECTED_RESULT_CODE, null);
            finish();
        });

        IntentFilter intentFilter = new IntentFilter(ACTION_CONNECTION_CANCELLED);
        registerReceiver(mConnectionCancellationReceiver, intentFilter, Context.RECEIVER_EXPORTED);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mConnectionCancellationReceiver);
        super.onPause();
    }
}
