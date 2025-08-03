/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net.wifi.cts.app;

import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TransportInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * An activity that retrieves Transport info and returns status.
 */
public class RetrieveTransportInfoAndReturnStatusActivity extends Activity {
    private static final String TAG = "RetrieveTransportInfoAndReturnStatusActivity";
    private static final String STATUS_EXTRA = "android.net.wifi.cts.app.extra.STATUS";
    private static final int DURATION_NETWORK_CONNECTION_MILLIS = 60_000;

    private static class TestNetworkCallback extends ConnectivityManager.NetworkCallback {
        private final CountDownLatch mCountDownLatch;
        public boolean onAvailableCalled = false;
        public NetworkCapabilities networkCapabilities;

        TestNetworkCallback(CountDownLatch countDownLatch) {
            super(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO);
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onAvailable(Network network) {
            onAvailableCalled = true;
        }

        @Override
        public void onCapabilitiesChanged(Network network,
                NetworkCapabilities networkCapabilities) {
            if (onAvailableCalled) {
                this.networkCapabilities = networkCapabilities;
                mCountDownLatch.countDown();
            }
        }
    }

    public static boolean canRetrieveSsidFromTransportInfo(
            String logTag, ConnectivityManager connectivityManager) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        TestNetworkCallback testNetworkCallback = new TestNetworkCallback(countDownLatch);
        try {
            // File a callback for wifi network.
            connectivityManager.registerNetworkCallback(
                    new NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                            .build(),
                    testNetworkCallback);
            // Wait for callback.
            if (!countDownLatch.await(
                    DURATION_NETWORK_CONNECTION_MILLIS, TimeUnit.MILLISECONDS)) {
                Log.e(logTag, "Timed out waiting for wifi network");
                return false;
            }
            if (!testNetworkCallback.onAvailableCalled) {
                Log.e(logTag, "Failed to get wifi network onAvailable");
                return false;
            }
            TransportInfo transportInfo =
                    testNetworkCallback.networkCapabilities.getTransportInfo();
            if (!(transportInfo instanceof WifiInfo)) {
                Log.e(logTag, "Failed to retrieve WifiInfo");
                return false;
            }
            WifiInfo wifiInfo = (WifiInfo) transportInfo;
            boolean succeeded = !Objects.equals(wifiInfo.getSSID(), WifiManager.UNKNOWN_SSID);
            if (succeeded) {
                Log.v(logTag, "SSID from transport info retrieval succeeded");
            } else {
                Log.v(logTag, "Failed to retrieve SSID from transport info");
            }
            return succeeded;
        } catch (InterruptedException e) {
            return false;
        } finally {
            connectivityManager.unregisterNetworkCallback(testNetworkCallback);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ConnectivityManager connectivityManager  = getSystemService(ConnectivityManager.class);
        setResult(RESULT_OK, new Intent().putExtra(
                STATUS_EXTRA, canRetrieveSsidFromTransportInfo(TAG, connectivityManager)));
        finish();
    }
}
