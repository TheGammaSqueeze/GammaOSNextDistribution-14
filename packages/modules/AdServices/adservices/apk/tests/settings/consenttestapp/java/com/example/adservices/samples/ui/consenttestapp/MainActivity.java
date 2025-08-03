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
package com.example.adservices.samples.ui.consenttestapp;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Android application activity for testing Topics API by providing a button in UI that initiate
 * user's interaction with Topics Manager in the background. Response from Topics API will be shown
 * in the app as text as well as toast message. In case anything goes wrong in this process, error
 * message will also be shown in toast to suggest the Exception encountered.
 */
public class MainActivity extends AppCompatActivity {

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final AdvertisingCustomAudienceClient advertisingCustomAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(this)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        Futures.addCallback(
                advertisingCustomAudienceClient.joinCustomAudience(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .build()),
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {}

                    @Override
                    public void onFailure(Throwable t) {}
                },
                directExecutor());
    }
}
