/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.example.adservices.samples.adid.app;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdManager;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Android application activity for testing reading AdId. It displays the adId on a textView on the
 * screen. If there is an error, it displays the error.
 */
public class MainActivity extends AppCompatActivity {
    private Button mAdIdButton;
    private TextView mAdIdTextView;
    private AdIdManager mAdIdManager;
    private final Executor mExecutor = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAdIdTextView = findViewById(R.id.adIdTextView);
        mAdIdButton = findViewById(R.id.adIdButton);

        mAdIdManager =
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        ? this.getSystemService(AdIdManager.class)
                        : AdIdManager.get(this);
        registerAdIdButton();
    }

    @SuppressWarnings("NewApi")
    private void registerAdIdButton() {
        AdServicesOutcomeReceiver<AdId, Exception> adIdCallback =
                new AdServicesOutcomeReceiver<AdId, Exception>() {
                    @Override
                    public void onResult(@NonNull AdId adId) {
                        setAdIdText(getAdIdDisplayString(adId));
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        setAdIdText(error.toString());
                    }
                };

        mAdIdButton.setOnClickListener(v -> getAdId(mExecutor, adIdCallback));
    }

    @SuppressWarnings("NewApi")
    private void getAdId(Executor executor, AdServicesOutcomeReceiver<AdId, Exception> callback) {
        // getService() in AdIdManager throws on main thread and doesn't offload the error to the
        // callback. Catch it to avoid app to crash.
        try {
            mAdIdManager.getAdId(executor, callback);
        } catch (IllegalStateException e) {
            callback.onError(e);
        }
    }

    private void setAdIdText(String text) {
        runOnUiThread(() -> mAdIdTextView.setText(text));
    }

    @SuppressWarnings("NewApi")
    private String getAdIdDisplayString(AdId adId) {
        return "AdId: " + adId.getAdId() + "\n" + "LAT: " + adId.isLimitAdTrackingEnabled();
    }
}
