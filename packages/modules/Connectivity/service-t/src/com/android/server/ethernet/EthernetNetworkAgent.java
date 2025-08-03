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

package com.android.server.ethernet;

import android.content.Context;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkProvider;
import android.net.NetworkScore;
import android.os.Looper;
import android.annotation.NonNull;
import android.annotation.Nullable;

public class EthernetNetworkAgent extends NetworkAgent {

    private static final String TAG = "EthernetNetworkAgent";

    public interface Callbacks {
        void onNetworkUnwanted();
    }

    private final Callbacks mCallbacks;

    EthernetNetworkAgent(
            @NonNull Context context,
            @NonNull Looper looper,
            @NonNull NetworkCapabilities nc,
            @NonNull LinkProperties lp,
            @NonNull NetworkAgentConfig config,
            @Nullable NetworkProvider provider,
            @NonNull Callbacks cb) {
        super(context, looper, TAG, nc, lp, new NetworkScore.Builder().build(), config, provider);
        mCallbacks = cb;
    }

    @Override
    public void onNetworkUnwanted() {
        mCallbacks.onNetworkUnwanted();
    }

    // sendLinkProperties is final in NetworkAgent, so it cannot be mocked.
    public void sendLinkPropertiesImpl(LinkProperties lp) {
        sendLinkProperties(lp);
    }

    public Callbacks getCallbacks() {
        return mCallbacks;
    }
}
