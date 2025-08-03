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

package com.android.cts.net.hostside.networkslicingtestapp;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NetworkSelfDeclaredCapabilitiesTest {

    @Rule
    public final DevSdkIgnoreRule mDevSdkIgnoreRule = new DevSdkIgnoreRule();

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void requestNetwork_withoutRequestCapabilities() {
        final ConnectivityManager cm =
                (ConnectivityManager)
                        InstrumentationRegistry.getInstrumentation()
                                .getContext()
                                .getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkRequest request =
                new NetworkRequest.Builder().build();
        final ConnectivityManager.NetworkCallback callback =
                new ConnectivityManager.NetworkCallback();
        cm.requestNetwork(request, callback);
        cm.unregisterNetworkCallback(callback);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void requestNetwork_withSelfDeclaredCapabilities() {
        final ConnectivityManager cm =
                (ConnectivityManager)
                        InstrumentationRegistry.getInstrumentation()
                                .getContext()
                                .getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkRequest request =
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                        .build();
        final ConnectivityManager.NetworkCallback callback =
                new ConnectivityManager.NetworkCallback();
        cm.requestNetwork(request, callback);
        cm.unregisterNetworkCallback(callback);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void requestNetwork_lackingRequiredSelfDeclaredCapabilities() {
        final ConnectivityManager cm =
                (ConnectivityManager)
                        InstrumentationRegistry.getInstrumentation()
                                .getContext()
                                .getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkRequest request =
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                        .build();
        final ConnectivityManager.NetworkCallback callback =
                new ConnectivityManager.NetworkCallback();
        assertThrows(
                SecurityException.class,
                () -> cm.requestNetwork(request, callback));
    }

}
