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
package com.example.adservices.samples.appsetid.app;

import android.adservices.appsetid.AppSetId;
import android.adservices.appsetid.AppSetIdManager;
import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Android application activity for testing reading AppSetId. It displays the appSetId on a textView
 * on the screen. If there is an error, it displays the error.
 */
public class MainActivity extends AppCompatActivity {
    private Button mAppSetIdButton;
    private TextView mAppSetIdTextView;
    private AppSetIdManager mAppSetIdManager;
    private final Executor mExecutor = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAppSetIdTextView = findViewById(R.id.appSetIdTextView);
        mAppSetIdButton = findViewById(R.id.appSetIdButton);

        // AppSetIdManager can not be called on R until OutcomeReceiver dependencies are removed.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            setAppSetIdText("Device not supported.");
            return;
        }

        mAppSetIdManager =
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        ? this.getSystemService(AppSetIdManager.class)
                        : AppSetIdManager.get(this);
        registerAppSetIdButton();
    }

    private void registerAppSetIdButton() {
        OutcomeReceiver<AppSetId, Exception> appSetIdCallback =
                new OutcomeReceiver<AppSetId, Exception>() {
                    @Override
                    public void onResult(@NonNull AppSetId appSetId) {
                        setAppSetIdText(getAppSetIdDisplayString(appSetId));
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        setAppSetIdText(error.toString());
                    }
                };

        mAppSetIdButton.setOnClickListener(v -> getAppSetId(mExecutor, appSetIdCallback));
    }

    @TargetApi(Build.VERSION_CODES.S)
    @SuppressWarnings("NewApi")
    private void getAppSetId(Executor executor, OutcomeReceiver<AppSetId, Exception> callback) {
        // getService() in AdIdManager throws on main thread and doesn't offload the error to the
        // callback. Catch it to avoid app to crash.
        try {
            mAppSetIdManager.getAppSetId(executor, callback);
        } catch (IllegalStateException e) {
            callback.onError(e);
        }
    }

    private void setAppSetIdText(String text) {
        runOnUiThread(() -> mAppSetIdTextView.setText(text));
    }

    @SuppressWarnings("NewApi")
    private String getAppSetIdDisplayString(AppSetId appSetId) {
        return "AppSetId: " + appSetId.getId() + "\n" + "AppSetId Scope: " + appSetId.getScope();
    }
}
