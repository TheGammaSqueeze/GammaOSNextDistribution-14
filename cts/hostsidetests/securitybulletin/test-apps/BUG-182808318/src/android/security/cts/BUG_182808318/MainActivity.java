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
package android.security.cts.BUG_182808318;

import static android.security.cts.BUG_182808318.Constants.LOG_TAG;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

/** Main activity for the test-app. */
public final class MainActivity extends AppCompatActivity {

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            startTapjacking();
        }
    };

    private Button btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        registerReceiver(mReceiver, new IntentFilter(Constants.ACTION_START_TAPJACKING));

        btnStart = (Button) findViewById(R.id.btnStart);
        btnStart.setOnClickListener(v -> startTapjacking());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        stopOverlayService();
    }

    public void startTapjacking() {
        Log.d(LOG_TAG, "Starting tap-jacking flow.");
        stopOverlayService();

        startOverlayService();
        Log.d(LOG_TAG, "Started overlay-service.");
    }

    private void startOverlayService() {
        startService(new Intent(getApplicationContext(), OverlayService.class));
    }

    private void stopOverlayService() {
        stopService(new Intent(getApplicationContext(), OverlayService.class));
    }
}
