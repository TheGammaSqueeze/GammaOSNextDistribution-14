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
package com.android.adservices.cts;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.adservices.topics.Topic;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TopicsApiLogActivity extends AppCompatActivity {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SDK_NAME = "AdservicesCtsSdk";
    private static final String NEWLINE = "\n";
    private static final String TAG = "AdservicesCtsTest";
    private AdvertisingTopicsClient mAdvertisingTopicsClient;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdvertisingTopicsClient =
                new AdvertisingTopicsClient.Builder()
                        .setContext(this)
                        .setSdkName(SDK_NAME)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        ListenableFuture<GetTopicsResponse> getTopicsResponseFuture =
                mAdvertisingTopicsClient.getTopics();

        Futures.addCallback(
                getTopicsResponseFuture,
                new FutureCallback<GetTopicsResponse>() {
                    @Override
                    public void onSuccess(GetTopicsResponse result) {
                        Log.d(TAG, "GetTopics for sdk " + SDK_NAME + " succeeded!");
                        String topics = getTopics(result.getTopics());
                        Log.d(TAG, SDK_NAME + "'s topics: " + " " + topics);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e(TAG, "Failed to getTopics for sdk " + SDK_NAME);
                    }
                },
                directExecutor());
    }

    private String getTopics(List<Topic> arr) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (Topic topic : arr) {
            sb.append(index++).append(". ").append(topic.toString()).append(NEWLINE);
        }
        return sb.toString();
    }
}
