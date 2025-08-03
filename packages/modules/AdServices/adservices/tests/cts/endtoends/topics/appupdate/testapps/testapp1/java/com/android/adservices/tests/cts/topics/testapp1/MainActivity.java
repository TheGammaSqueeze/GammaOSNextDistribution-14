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
package com.android.adservices.tests.cts.topics.testapp1;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.adservices.topics.Topic;
import android.annotation.NonNull;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * This test app is used for the CTS test of App Update flow in Topics API. It calls Topics API and
 * send the response to CTS test suite app by explicit broadcast.
 */
public class MainActivity extends Activity {
    @SuppressWarnings("unused")
    private static final String TAG = "testapp1";

    private static final String LOG_TAG = "adservices";
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    // Broadcast sent from test apps to test suite to pass GetTopicsResponse. Use two types of
    // broadcast to make the result more explicit.
    private static final String TOPIC_RESPONSE_BROADCAST_KEY = "topicIds";
    // This test app will send this broadcast to the CTS test suite if it receives non-empty topics.
    private static final String NON_EMPTY_TOPIC_RESPONSE_BROADCAST =
            "com.android.adservices.tests.cts.topics.NON_EMPTY_TOPIC_RESPONSE";
    // This test app will send this broadcast to the CTS test suite if it receives empty topics.
    private static final String EMPTY_TOPIC_RESPONSE_BROADCAST =
            "com.android.adservices.tests.cts.topics.EMPTY_TOPIC_RESPONSE";
    private static final String SDK_NAME = "sdk";

    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();

        try {
            // Call Topics API via sdk.
            GetTopicsResponse getTopicsResponse = callTopicsApi(SDK_NAME);
            sendGetTopicsResponseBroadcast(getTopicsResponse);
        } catch (Exception e) {
            Log.e(LOG_TAG, "getTopics() call failed, please check: " + e);
        }

        // Kill this test app
        finish();
    }

    // Send Broadcast to main test suite to pass the GetTopicsResponse
    @SuppressWarnings("NewApi")
    private void sendGetTopicsResponseBroadcast(@NonNull GetTopicsResponse getTopicsResponse) {
        List<Topic> topics = getTopicsResponse.getTopics();
        Log.v(LOG_TAG, "Test app gets topics: " + topics);

        // Put returned topic ids into extra to pass them to CTS test suite app
        Intent sendGetTopicsResponseBroadcast = new Intent();
        if (topics.isEmpty()) {
            sendGetTopicsResponseBroadcast.setAction(EMPTY_TOPIC_RESPONSE_BROADCAST);
        } else {
            sendGetTopicsResponseBroadcast.setAction(NON_EMPTY_TOPIC_RESPONSE_BROADCAST);
            int[] topicIds = topics.stream().mapToInt(Topic::getTopicId).toArray();
            sendGetTopicsResponseBroadcast.putExtra(TOPIC_RESPONSE_BROADCAST_KEY, topicIds);
        }

        mContext.sendBroadcast(sendGetTopicsResponseBroadcast);
    }

    private GetTopicsResponse callTopicsApi(@NonNull String sdk) throws Exception {
        AdvertisingTopicsClient.Builder builder =
                new AdvertisingTopicsClient.Builder()
                        .setContext(mContext)
                        .setExecutor(CALLBACK_EXECUTOR);
        if (!sdk.isEmpty()) {
            builder.setSdkName(sdk);
        }

        return builder.build().getTopics().get();
    }
}
