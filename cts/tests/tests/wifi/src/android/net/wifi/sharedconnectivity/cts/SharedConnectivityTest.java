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

package android.net.wifi.sharedconnectivity.cts;

import static com.android.wifi.flags.Flags.networkProviderBatteryChargingStatus;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.sharedconnectivity.app.HotspotNetwork;
import android.net.wifi.sharedconnectivity.app.HotspotNetworkConnectionStatus;
import android.net.wifi.sharedconnectivity.app.KnownNetwork;
import android.net.wifi.sharedconnectivity.app.KnownNetworkConnectionStatus;
import android.net.wifi.sharedconnectivity.app.NetworkProviderInfo;
import android.net.wifi.sharedconnectivity.app.SharedConnectivityManager;
import android.net.wifi.sharedconnectivity.app.SharedConnectivitySettingsState;
import android.net.wifi.sharedconnectivity.app.cts.TestSharedConnectivityClientCallback;
import android.net.wifi.sharedconnectivity.service.cts.TestSharedConnectivityService;
import android.os.Build;
import android.os.Bundle;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.NonMainlineTest;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * These tests cover both SharedConnectivityService and SharedConnectivityManager.
 * Testing is done on these classes in their bound state.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@NonMainlineTest
public class SharedConnectivityTest {
    private static final String TAG = "SharedConnectivityTest";

    private static final String SERVICE_PACKAGE_NAME = "android.net.wifi.cts";
    private static final String SERVICE_INTENT_ACTION =
            "android.net.wifi.sharedconnectivity.service.cts.TestSharedConnectivityService.BIND";

    // Time between checks for state we expect.
    private static final long CHECK_DELAY_MILLIS = 500;
    // Number of times to check before failing.
    private static final long CHECK_RETRIES = 8;
    // Time to wait for callback's CountDownLatch.
    private static final long LATCH_TIMEOUT_SECS = 20;

    private HotspotNetwork mTestHotspotNetwork1;
    private HotspotNetwork mTestHotspotNetwork2;
    private KnownNetwork mTestKnownNetwork1;
    private KnownNetwork mTestKnownNetwork2;
    private HotspotNetworkConnectionStatus mTestHotspotNetworkConnectionStatus;
    private KnownNetworkConnectionStatus mTestKnownNetworkConnectionStatus;

    @Before
    public void setUp() {
        NetworkProviderInfo.Builder builder =
                new NetworkProviderInfo.Builder("Matt's Phone", "Pixel 6a")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(80)
                        .setConnectionStrength(3);
        if (networkProviderBatteryChargingStatus() && SdkLevel.isAtLeastV()) {
            builder.setBatteryCharging(false);
        }
        mTestHotspotNetwork1 = new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(builder.build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .setHotspotSsid("Instant Hotspot 12345")
                .setHotspotBssid("01:01:01:01:01:01")
                .addHotspotSecurityType(WifiInfo.SECURITY_TYPE_PSK)
                .build();

        builder = new NetworkProviderInfo.Builder("Matt's Laptop", "Pixelbook Go")
                .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_LAPTOP)
                .setBatteryPercentage(30)
                .setConnectionStrength(2);
        if (networkProviderBatteryChargingStatus() && SdkLevel.isAtLeastV()) {
            builder.setBatteryCharging(true);
        }
        mTestHotspotNetwork2 = new HotspotNetwork.Builder()
                .setDeviceId(2)
                .setNetworkProviderInfo(builder.build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_WIFI)
                .setNetworkName("Hotel Guest")
                .build();

        builder = new NetworkProviderInfo.Builder("Isaac's Phone", "Pixel 7")
                .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                .setBatteryPercentage(99)
                .setConnectionStrength(3);
        if (networkProviderBatteryChargingStatus() && SdkLevel.isAtLeastV()) {
            builder.setBatteryCharging(false);
        }
        mTestKnownNetwork1 = new KnownNetwork.Builder()
                .setNetworkSource(KnownNetwork.NETWORK_SOURCE_NEARBY_SELF)
                .setSsid("Home Network")
                .addSecurityType(WifiInfo.SECURITY_TYPE_WEP)
                .setNetworkProviderInfo(builder.build())
                .build();

        builder = new NetworkProviderInfo.Builder("Isaac's Work Phone", "Pixel 7 Pro")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(15)
                        .setConnectionStrength(1);
        if (networkProviderBatteryChargingStatus() && SdkLevel.isAtLeastV()) {
            builder.setBatteryCharging(true);
        }
        mTestKnownNetwork2 = new KnownNetwork.Builder()
                .setNetworkSource(KnownNetwork.NETWORK_SOURCE_NEARBY_SELF)
                .setSsid("Cafe Wifi")
                .addSecurityType(WifiInfo.SECURITY_TYPE_PSK)
                .setNetworkProviderInfo(builder.build())
                .build();

        mTestHotspotNetworkConnectionStatus = new HotspotNetworkConnectionStatus.Builder()
                .setStatus(HotspotNetworkConnectionStatus.CONNECTION_STATUS_ENABLING_HOTSPOT)
                .setHotspotNetwork(mTestHotspotNetwork1)
                .setExtras(Bundle.EMPTY)
                .build();

        mTestKnownNetworkConnectionStatus = new KnownNetworkConnectionStatus.Builder()
                .setStatus(KnownNetworkConnectionStatus.CONNECTION_STATUS_SAVED)
                .setKnownNetwork(mTestKnownNetwork1)
                .setExtras(Bundle.EMPTY)
                .build();
    }

    @Test
    public void registerCallback_withoutPermission_throwsSecurityException() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        grantPermission();
        SharedConnectivityManager manager = getManager(context);
        TestSharedConnectivityClientCallback callback =
                new TestSharedConnectivityClientCallback();
        // Registrations done before the service is connected are cached and executed in the
        // background. Need to wait for the service to be connected to test.
        manager.registerCallback(Runnable::run, callback);
        TestSharedConnectivityService service = getService();
        assertServiceConnected(callback);

        dropPermission();

        TestSharedConnectivityClientCallback callback2 =
                new TestSharedConnectivityClientCallback();
        assertThrows(SecurityException.class,
                () -> manager.registerCallback(Runnable::run, callback2));
    }

    @Test
    public void registerCallback_onServiceConnectedCallbackReceived()
            throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        grantPermission();
        try {
            SharedConnectivityManager manager = getManager(context);
            TestSharedConnectivityClientCallback callback =
                    new TestSharedConnectivityClientCallback();
            manager.registerCallback(Runnable::run, callback);
            TestSharedConnectivityService service = getService();

            assertServiceConnected(callback);
        } finally {
            dropPermission();
        }
    }

    @Test
    public void unregisterCallback_beforeRegister_unregisterUnsuccessful() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        grantPermission();
        try {
            SharedConnectivityManager manager = getManager(context);
            TestSharedConnectivityClientCallback callback =
                    new TestSharedConnectivityClientCallback();

            assertThat(manager.unregisterCallback(callback)).isFalse();
        } finally {
            dropPermission();
        }
    }

    @Test
    public void unregisterCallback_unregisterSuccessful() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        grantPermission();
        try {
            SharedConnectivityManager manager = getManager(context);
            CountDownLatch callbackLatch = new CountDownLatch(1);
            TestSharedConnectivityClientCallback callback =
                    new TestSharedConnectivityClientCallback() {
                        @Override
                        public void onSharedConnectivitySettingsChanged(
                                SharedConnectivitySettingsState state) {
                            super.onSharedConnectivitySettingsChanged(state);
                            callbackLatch.countDown();
                        }
                    };
            // Need to successfully register callback before testing unregister method.
            manager.registerCallback(Runnable::run, callback);
            assertServiceConnected(callback);
            TestSharedConnectivityService service = getService();
            CountDownLatch serviceLatch = new CountDownLatch(1);
            service.setCountdownLatch(serviceLatch);

            assertThat(manager.unregisterCallback(callback)).isTrue();
            // Wait for the unregister call to be executed in the service
            assertThat(serviceLatch.await(LATCH_TIMEOUT_SECS, TimeUnit.SECONDS)).isTrue();

            // Try to use callback and validate that manager was not updated.
            service.setSettingsState(buildSettingsState());
            assertThat(callbackLatch.await(LATCH_TIMEOUT_SECS, TimeUnit.SECONDS)).isFalse();
            assertThat(callback.getSharedConnectivitySettingsState()).isNull();
        } finally {
            dropPermission();
        }
    }

    @Test
    public void setHotspotNetworks_managerCallbackReceivedWithCorrectData() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        grantPermission();
        try {
            SharedConnectivityManager manager = getManager(context);
            CountDownLatch callbackLatch = new CountDownLatch(1);
            TestSharedConnectivityClientCallback callback =
                    new TestSharedConnectivityClientCallback() {
                        @Override
                        public void onHotspotNetworksUpdated(List<HotspotNetwork> networks) {
                            super.onHotspotNetworksUpdated(networks);
                            callbackLatch.countDown();
                        }
                    };
            manager.registerCallback(Runnable::run, callback);
            TestSharedConnectivityService service = getService();
            assertServiceConnected(callback);

            List<HotspotNetwork> networks = Arrays.asList(mTestHotspotNetwork1,
                    mTestHotspotNetwork2);
            service.setHotspotNetworks(networks);

            assertThat(callbackLatch.await(LATCH_TIMEOUT_SECS, TimeUnit.SECONDS)).isTrue();
            assertThat(callback.getHotspotNetworksList()).containsExactlyElementsIn(networks);
        } finally {
            dropPermission();
        }
    }

    @Test
    public void setKnownNetworks_managerCallbackReceivedWithCorrectData() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        grantPermission();
        try {
            SharedConnectivityManager manager = getManager(context);
            CountDownLatch callbackLatch = new CountDownLatch(1);
            TestSharedConnectivityClientCallback callback =
                    new TestSharedConnectivityClientCallback() {
                        @Override
                        public void onKnownNetworksUpdated(List<KnownNetwork> networks) {
                            super.onKnownNetworksUpdated(networks);
                            callbackLatch.countDown();
                        }
                    };
            manager.registerCallback(Runnable::run, callback);
            TestSharedConnectivityService service = getService();
            assertServiceConnected(callback);

            List<KnownNetwork> networks = Arrays.asList(mTestKnownNetwork1, mTestKnownNetwork2);
            service.setKnownNetworks(networks);

            assertThat(callbackLatch.await(LATCH_TIMEOUT_SECS, TimeUnit.SECONDS)).isTrue();
            assertThat(callback.getKnownNetworksList()).containsExactlyElementsIn(networks);
        } finally {
            dropPermission();
        }
    }

    @Test
    public void setSettingsState_managerCallbackReceivedWithCorrectData() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        grantPermission();
        try {
            SharedConnectivityManager manager = getManager(context);
            CountDownLatch callbackLatch = new CountDownLatch(1);
            TestSharedConnectivityClientCallback callback =
                    new TestSharedConnectivityClientCallback() {
                        @Override
                        public void onSharedConnectivitySettingsChanged(
                                SharedConnectivitySettingsState state) {
                            super.onSharedConnectivitySettingsChanged(state);
                            callbackLatch.countDown();
                        }
                    };
            manager.registerCallback(Runnable::run, callback);
            TestSharedConnectivityService service = getService();
            assertServiceConnected(callback);

            service.setSettingsState(buildSettingsState());

            assertThat(callbackLatch.await(LATCH_TIMEOUT_SECS, TimeUnit.SECONDS)).isTrue();
            assertThat(callback.getSharedConnectivitySettingsState()).isEqualTo(
                    buildSettingsState());
        } finally {
            dropPermission();
        }
    }

    @Test
    public void updateHotspotNetworkConnectionStatus_managerCallbackReceivedWithCorrectData()
            throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        grantPermission();
        try {
            SharedConnectivityManager manager = getManager(context);
            CountDownLatch callbackLatch = new CountDownLatch(1);
            TestSharedConnectivityClientCallback callback =
                    new TestSharedConnectivityClientCallback() {
                        @Override
                        public void onHotspotNetworkConnectionStatusChanged(
                                HotspotNetworkConnectionStatus status) {
                            super.onHotspotNetworkConnectionStatusChanged(status);
                            callbackLatch.countDown();
                        }
                    };
            manager.registerCallback(Runnable::run, callback);
            TestSharedConnectivityService service = getService();
            assertServiceConnected(callback);

            service.updateHotspotNetworkConnectionStatus(mTestHotspotNetworkConnectionStatus);

            assertThat(callbackLatch.await(LATCH_TIMEOUT_SECS, TimeUnit.SECONDS)).isTrue();
            assertThat(callback.getHotspotNetworkConnectionStatus()).isEqualTo(
                    mTestHotspotNetworkConnectionStatus);
        } finally {
            dropPermission();
        }
    }

    @Test
    public void updateKnownNetworkConnectionStatus_managerCallbackReceivedWithCorrectData()
            throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        grantPermission();
        try {
            SharedConnectivityManager manager = getManager(context);
            CountDownLatch callbackLatch = new CountDownLatch(1);
            TestSharedConnectivityClientCallback callback =
                    new TestSharedConnectivityClientCallback() {
                        @Override
                        public void onKnownNetworkConnectionStatusChanged(
                                KnownNetworkConnectionStatus status) {
                            super.onKnownNetworkConnectionStatusChanged(status);
                            callbackLatch.countDown();
                        }
                    };
            manager.registerCallback(Runnable::run, callback);
            TestSharedConnectivityService service = getService();
            assertServiceConnected(callback);

            service.updateKnownNetworkConnectionStatus(mTestKnownNetworkConnectionStatus);

            assertThat(callbackLatch.await(LATCH_TIMEOUT_SECS, TimeUnit.SECONDS)).isTrue();
            assertThat(callback.getKnownNetworkConnectionStatus()).isEqualTo(
                    mTestKnownNetworkConnectionStatus);
        } finally {
            dropPermission();
        }
    }

    @Test
    public void connectHotspotNetwork_serviceCallbackReceivedWithCorrectData() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        grantPermission();
        try {
            SharedConnectivityManager manager = getManager(context);
            TestSharedConnectivityClientCallback callback =
                    new TestSharedConnectivityClientCallback();
            manager.registerCallback(Runnable::run, callback);
            TestSharedConnectivityService service = getService();
            assertServiceConnected(callback);

            assertThat(manager.connectHotspotNetwork(mTestHotspotNetwork1)).isTrue();

            for (int i = 0; service.getConnectHotspotNetwork() == null && i < CHECK_RETRIES; i++) {
                Thread.sleep(CHECK_DELAY_MILLIS);
            }
            assertThat(service.getConnectHotspotNetwork()).isNotNull();
            assertThat(service.getConnectHotspotNetwork()).isEqualTo(mTestHotspotNetwork1);
        } finally {
            dropPermission();
        }
    }

    @Test
    public void disconnectHotspotNetwork_serviceCallbackReceivedWithCorrectData() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        grantPermission();
        try {
            SharedConnectivityManager manager = getManager(context);
            TestSharedConnectivityClientCallback callback =
                    new TestSharedConnectivityClientCallback();
            manager.registerCallback(Runnable::run, callback);
            TestSharedConnectivityService service = getService();
            assertServiceConnected(callback);

            manager.disconnectHotspotNetwork(mTestHotspotNetwork1);

            for (int i = 0; service.getDisconnectHotspotNetwork() == null && i < CHECK_RETRIES;
                    i++) {
                Thread.sleep(CHECK_DELAY_MILLIS);
            }
            assertThat(service.getDisconnectHotspotNetwork()).isNotNull();
            assertThat(service.getDisconnectHotspotNetwork()).isEqualTo(mTestHotspotNetwork1);
        } finally {
            dropPermission();
        }
    }

    @Test
    public void connectKnownNetwork_serviceCallbackReceivedWithCorrectData() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        grantPermission();
        try {
            SharedConnectivityManager manager = getManager(context);
            TestSharedConnectivityClientCallback callback =
                    new TestSharedConnectivityClientCallback();
            manager.registerCallback(Runnable::run, callback);
            TestSharedConnectivityService service = getService();
            assertServiceConnected(callback);

            manager.connectKnownNetwork(mTestKnownNetwork1);

            for (int i = 0; service.getConnectKnownNetwork() == null && i < CHECK_RETRIES; i++) {
                Thread.sleep(CHECK_DELAY_MILLIS);
            }
            assertThat(service.getConnectKnownNetwork()).isNotNull();
            assertThat(service.getConnectKnownNetwork()).isEqualTo(mTestKnownNetwork1);
        } finally {
            dropPermission();
        }
    }

    @Test
    public void forgetKnownNetwork_serviceCallbackReceivedWithCorrectData() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        grantPermission();
        try {
            SharedConnectivityManager manager = getManager(context);
            TestSharedConnectivityClientCallback callback =
                    new TestSharedConnectivityClientCallback();
            manager.registerCallback(Runnable::run, callback);
            TestSharedConnectivityService service = getService();
            assertServiceConnected(callback);

            manager.forgetKnownNetwork(mTestKnownNetwork1);

            for (int i = 0; service.getForgetKnownNetwork() == null && i < CHECK_RETRIES; i++) {
                Thread.sleep(CHECK_DELAY_MILLIS);
            }
            assertThat(service.getForgetKnownNetwork()).isNotNull();
            assertThat(service.getForgetKnownNetwork()).isEqualTo(mTestKnownNetwork1);
        } finally {
            dropPermission();
        }
    }

    @Test
    public void getHotspotNetworkConnectionStatus_withoutUpdate_returnsNull()
            throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        grantPermission();
        try {
            SharedConnectivityManager manager = getManager(context);
            TestSharedConnectivityClientCallback callback =
                    new TestSharedConnectivityClientCallback();
            manager.registerCallback(Runnable::run, callback);
            assertServiceConnected(callback);

            assertThat(callback.getHotspotNetworkConnectionStatus()).isNull();
        } finally {
            dropPermission();
        }
    }

    @Test
    public void getKnownNetworkConnectionStatus_withoutUpdate_returnsNull()
            throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        grantPermission();
        try {
            SharedConnectivityManager manager = getManager(context);
            TestSharedConnectivityClientCallback callback =
                    new TestSharedConnectivityClientCallback();
            manager.registerCallback(Runnable::run, callback);
            assertServiceConnected(callback);

            assertThat(callback.getKnownNetworkConnectionStatus()).isNull();
        } finally {
            dropPermission();
        }
    }

    private SharedConnectivityManager getManager(Context context) {
        return SharedConnectivityManager.create(context, SERVICE_PACKAGE_NAME,
                SERVICE_INTENT_ACTION);
    }

    private TestSharedConnectivityService getService() throws InterruptedException {
        TestSharedConnectivityService service = TestSharedConnectivityService.getInstance();
        for (int i = 0; service == null && i < CHECK_RETRIES; i++) {
            Thread.sleep(CHECK_DELAY_MILLIS);
            service = TestSharedConnectivityService.getInstance();
        }
        assertThat(service).isNotNull();
        return service;
    }

    private void grantPermission() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(android.Manifest.permission.NETWORK_SETTINGS);
    }

    private void dropPermission() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private void assertServiceConnected(TestSharedConnectivityClientCallback callback)
            throws InterruptedException {
        assertThat(callback.getServiceConnectedLatch().await(LATCH_TIMEOUT_SECS,
                TimeUnit.SECONDS)).isTrue();
    }

    private SharedConnectivitySettingsState buildSettingsState() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        return new SharedConnectivitySettingsState.Builder()
                        .setInstantTetherEnabled(true)
                        .setInstantTetherSettingsPendingIntent(
                                PendingIntent.getActivity(context, 0, new Intent(),
                                        PendingIntent.FLAG_IMMUTABLE))
                        .setExtras(Bundle.EMPTY)
                        .build();
    }
}
