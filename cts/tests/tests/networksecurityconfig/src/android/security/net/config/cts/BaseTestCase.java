/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.security.net.config.cts;

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;

/**
 * Base test case for all tests under {@link android.security.net.config.cts}.
 */
public abstract class BaseTestCase {

    Context mContext = InstrumentationRegistry.getContext();

    private boolean isInternetConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }

        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);
        return caps != null && caps.hasCapability(NET_CAPABILITY_INTERNET);
    }

    @Before
    public void setUp() throws Exception {
        assertTrue("CTS requires a working internet connection", isInternetConnected(mContext));
    }
}
