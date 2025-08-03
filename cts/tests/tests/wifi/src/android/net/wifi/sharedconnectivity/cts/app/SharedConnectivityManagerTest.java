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

package android.net.wifi.sharedconnectivity.cts.app;

import static android.net.wifi.WifiInfo.SECURITY_TYPE_EAP;
import static android.net.wifi.WifiInfo.SECURITY_TYPE_WEP;
import static android.net.wifi.sharedconnectivity.app.HotspotNetwork.NETWORK_TYPE_CELLULAR;
import static android.net.wifi.sharedconnectivity.app.KnownNetwork.NETWORK_SOURCE_NEARBY_SELF;
import static android.net.wifi.sharedconnectivity.app.NetworkProviderInfo.DEVICE_TYPE_TABLET;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.net.wifi.sharedconnectivity.app.HotspotNetwork;
import android.net.wifi.sharedconnectivity.app.HotspotNetworkConnectionStatus;
import android.net.wifi.sharedconnectivity.app.KnownNetwork;
import android.net.wifi.sharedconnectivity.app.KnownNetworkConnectionStatus;
import android.net.wifi.sharedconnectivity.app.NetworkProviderInfo;
import android.net.wifi.sharedconnectivity.app.SharedConnectivityClientCallback;
import android.net.wifi.sharedconnectivity.app.SharedConnectivityManager;
import android.net.wifi.sharedconnectivity.app.SharedConnectivitySettingsState;
import android.net.wifi.sharedconnectivity.service.ISharedConnectivityService;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.NonMainlineTest;
import com.android.modules.utils.build.SdkLevel;
import com.android.wifi.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * CTS tests for {@link SharedConnectivityManager}.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@NonMainlineTest
public class SharedConnectivityManagerTest {
    private static final long DEVICE_ID = 11L;
    private static final int NETWORK_TYPE = NETWORK_TYPE_CELLULAR;
    private static final String NETWORK_NAME = "TEST_NETWORK";
    private static final String HOTSPOT_SSID = "TEST_SSID";
    private static final int[] HOTSPOT_SECURITY_TYPES = {SECURITY_TYPE_WEP, SECURITY_TYPE_EAP};

    private static final int NETWORK_SOURCE = NETWORK_SOURCE_NEARBY_SELF;
    private static final String SSID = "TEST_SSID";
    private static final int[] SECURITY_TYPES = {SECURITY_TYPE_WEP};

    private static final String SERVICE_PACKAGE_NAME = "TEST_PACKAGE";
    private static final String SERVICE_INTENT_ACTION = "TEST_INTENT_ACTION";

    private NetworkProviderInfo mNetworkProviderInfo;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock
    Context mContext;
    @Mock
    ISharedConnectivityService mService;
    @Mock
    Executor mExecutor;
    @Mock
    SharedConnectivityClientCallback mClientCallback, mClientCallback2;
    @Mock
    Resources mResources;
    @Mock
    ISharedConnectivityService.Stub mIBinder;
    @Mock
    UserManager mUserManager;

    @Captor
    private ArgumentCaptor<IntentFilter> mIntentFilterCaptor;

    private static final ComponentName COMPONENT_NAME =
            new ComponentName("dummypkg", "dummycls");

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        setResources(mContext);

        NetworkProviderInfo.Builder builder =
                new NetworkProviderInfo.Builder("TEST_NAME", "TEST_MODEL")
                        .setDeviceType(DEVICE_TYPE_TABLET)
                        .setConnectionStrength(2)
                        .setBatteryPercentage(50);

        if (Flags.networkProviderBatteryChargingStatus() && SdkLevel.isAtLeastV()) {
            builder.setBatteryCharging(false);
        }

        mNetworkProviderInfo = builder.build();

        when(mContext.getSystemServiceName(UserManager.class)).thenReturn(Context.USER_SERVICE);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        when(mUserManager.isUserUnlocked()).thenReturn(true);
        when(mContext.bindService(any(Intent.class), any(ServiceConnection.class),
                anyInt())).thenReturn(true);
    }

    @Test
    public void resourcesNotDefined_createShouldReturnNull() {
        when(mResources.getString(anyInt())).thenThrow(new Resources.NotFoundException());

        assertThat(SharedConnectivityManager.create(mContext)).isNull();
    }

    @Test
    public void resourceStringsAreEmpty_createShouldReturnNull() {
        when(mResources.getString(anyInt())).thenReturn("");

        assertThat(SharedConnectivityManager.create(mContext)).isNull();
    }

    @Test
    public void bindingToServiceOnFirstCallbackRegistration() {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);

        manager.registerCallback(mExecutor, mClientCallback);

        verify(mContext).bindService(any(Intent.class), any(ServiceConnection.class), anyInt());
    }

    @Test
    public void bindToServiceFails_userUnlocked_callsOnRegisterCallbackFailed() {
        when(mContext.bindService(any(Intent.class), any(ServiceConnection.class),
                anyInt())).thenReturn(false);
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);

        manager.registerCallback(mExecutor, mClientCallback);

        verify(mClientCallback).onRegisterCallbackFailed(any(IllegalStateException.class));
    }

    @Test
    public void bindToServiceFails_userLocked_registerReceiver() {
        when(mUserManager.isUserUnlocked()).thenReturn(false);
        when(mContext.bindService(any(Intent.class), any(ServiceConnection.class),
                anyInt())).thenReturn(false);
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);

        manager.registerCallback(mExecutor, mClientCallback);

        verify(mContext).registerReceiver(any(BroadcastReceiver.class),
                mIntentFilterCaptor.capture());
        IntentFilter intentFilter = mIntentFilterCaptor.getValue();
        assertThat(intentFilter.getAction(0)).isEqualTo(Intent.ACTION_USER_UNLOCKED);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SHARED_CONNECTIVITY_BROADCAST_RECEIVER_TEST_API)
    public void broadcastReceiver_onReceiveUnlock_retriesBind() {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);

        manager.getBroadcastReceiver().onReceive(mContext, new Intent(Intent.ACTION_USER_UNLOCKED));

        verify(mContext).bindService(any(Intent.class), any(ServiceConnection.class), anyInt());
    }

    @Test
    public void bindIsCalledOnceOnMultipleCallbackRegistrations() throws Exception {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);

        manager.registerCallback(mExecutor, mClientCallback);
        verify(mContext, times(1)).bindService(any(Intent.class), any(ServiceConnection.class),
                anyInt());

        manager.registerCallback(mExecutor, mClientCallback2);
        verify(mContext, times(1)).bindService(any(Intent.class), any(ServiceConnection.class),
                anyInt());
    }

    @Test
    public void unbindIsCalledOnLastCallbackUnregistrations() throws Exception {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);

        manager.registerCallback(mExecutor, mClientCallback);
        manager.registerCallback(mExecutor, mClientCallback2);
        manager.unregisterCallback(mClientCallback);
        verify(mContext, never()).unbindService(
                any(ServiceConnection.class));

        manager.unregisterCallback(mClientCallback2);
        verify(mContext, times(1)).unbindService(
                any(ServiceConnection.class));
    }

    @Test
    public void bindIsCalledOnRegisterAfterUnbind() {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.registerCallback(mExecutor, mClientCallback);
        manager.getServiceConnection().onServiceConnected(COMPONENT_NAME, mIBinder);
        manager.unregisterCallback(mClientCallback);

        manager.registerCallback(mExecutor, mClientCallback2);

        verify(mContext, times(2)).bindService(any(Intent.class), any(ServiceConnection.class),
                anyInt());
    }

    @Test
    public void registerCallback_serviceNotConnected_canUnregisterAndReregister() {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);

        manager.registerCallback(mExecutor, mClientCallback);
        manager.unregisterCallback(mClientCallback);
        manager.registerCallback(mExecutor, mClientCallback);

        verify(mClientCallback, never()).onRegisterCallbackFailed(any(Exception.class));
    }

    @Test
    public void registerCallback_serviceConnected() throws Exception {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);

        manager.registerCallback(mExecutor, mClientCallback);

        verify(mService).registerCallback(any());
        verify(mClientCallback, never()).onRegisterCallbackFailed(any(Exception.class));
    }

    @Test
    public void registerCallback_doubleRegistration_shouldFail() throws Exception {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);

        manager.registerCallback(mExecutor, mClientCallback);
        manager.registerCallback(mExecutor, mClientCallback);

        verify(mClientCallback).onRegisterCallbackFailed(any(IllegalStateException.class));
    }

    @Test
    public void registerCallback_remoteException_shouldFail() throws Exception {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);
        doThrow(new RemoteException()).when(mService).registerCallback(any());

        manager.registerCallback(mExecutor, mClientCallback);

        verify(mClientCallback).onRegisterCallbackFailed(any(RemoteException.class));
    }

    @Test
    public void unregisterCallback_withoutRegisteringFirst_serviceNotConnected_shouldFail() {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(null);

        assertThat(manager.unregisterCallback(mClientCallback)).isFalse();
    }

    @Test
    public void unregisterCallback_withoutRegisteringFirst_serviceConnected_shouldFail() {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);

        assertThat(manager.unregisterCallback(mClientCallback)).isFalse();
    }

    @Test
    public void unregisterCallback() throws Exception {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);

        manager.registerCallback(mExecutor, mClientCallback);

        assertThat(manager.unregisterCallback(mClientCallback)).isTrue();
        verify(mService).unregisterCallback(any());
    }

    @Test
    public void unregisterCallback_doubleUnregistration_serviceConnected_shouldFail() {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);

        manager.registerCallback(mExecutor, mClientCallback);
        manager.unregisterCallback(mClientCallback);

        assertThat(manager.unregisterCallback(mClientCallback)).isFalse();
    }

    @Test
    public void unregisterCallback_doubleUnregistration_serviceNotConnected_shouldFail() {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(null);

        manager.registerCallback(mExecutor, mClientCallback);
        manager.unregisterCallback(mClientCallback);

        assertThat(manager.unregisterCallback(mClientCallback)).isFalse();
    }

    @Test
    public void unregisterCallback_remoteException_shouldFail() throws Exception {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);

        doThrow(new RemoteException()).when(mService).unregisterCallback(any());

        assertThat(manager.unregisterCallback(mClientCallback)).isFalse();
    }

    @Test
    public void onServiceDisconnected() {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);

        manager.registerCallback(mExecutor, mClientCallback);
        manager.getServiceConnection().onServiceConnected(COMPONENT_NAME, mIBinder);
        manager.getServiceConnection().onServiceDisconnected(COMPONENT_NAME);

        verify(mClientCallback).onServiceDisconnected();
    }

    @Test
    public void connectHotspotNetwork_serviceNotConnected_shouldFail() {
        HotspotNetwork network = buildHotspotNetwork();
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(null);

        assertThat(manager.connectHotspotNetwork(network)).isFalse();
    }

    @Test
    public void connectHotspotNetwork() throws RemoteException {
        HotspotNetwork network = buildHotspotNetwork();
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);

        manager.connectHotspotNetwork(network);

        verify(mService).connectHotspotNetwork(network);
    }

    @Test
    public void connectHotspotNetwork_remoteException_shouldFail() throws RemoteException {
        HotspotNetwork network = buildHotspotNetwork();
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);
        doThrow(new RemoteException()).when(mService).connectHotspotNetwork(network);

        assertThat(manager.connectHotspotNetwork(network)).isFalse();
    }

    @Test
    public void disconnectHotspotNetwork_serviceNotConnected_shouldFail() {
        HotspotNetwork network = buildHotspotNetwork();
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(null);

        assertThat(manager.disconnectHotspotNetwork(network)).isFalse();
    }

    @Test
    public void disconnectHotspotNetwork() throws RemoteException {
        HotspotNetwork network = buildHotspotNetwork();
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);

        manager.disconnectHotspotNetwork(network);

        verify(mService).disconnectHotspotNetwork(network);
    }

    @Test
    public void disconnectHotspotNetwork_remoteException_shouldFail() throws RemoteException {
        HotspotNetwork network = buildHotspotNetwork();
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);
        doThrow(new RemoteException()).when(mService).disconnectHotspotNetwork(any());

        assertThat(manager.disconnectHotspotNetwork(network)).isFalse();
    }

    @Test
    public void connectKnownNetwork_serviceNotConnected_shouldFail() throws RemoteException {
        KnownNetwork network = buildKnownNetwork();
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(null);

        assertThat(manager.connectKnownNetwork(network)).isFalse();
    }

    @Test
    public void connectKnownNetwork() throws RemoteException {
        KnownNetwork network = buildKnownNetwork();
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);

        manager.connectKnownNetwork(network);

        verify(mService).connectKnownNetwork(network);
    }

    @Test
    public void connectKnownNetwork_remoteException_shouldFail() throws RemoteException {
        KnownNetwork network = buildKnownNetwork();
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);
        doThrow(new RemoteException()).when(mService).connectKnownNetwork(network);

        assertThat(manager.connectKnownNetwork(network)).isFalse();
    }

    @Test
    public void forgetKnownNetwork_serviceNotConnected_shouldFail() {
        KnownNetwork network = buildKnownNetwork();
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(null);

        assertThat(manager.forgetKnownNetwork(network)).isFalse();
    }

    @Test
    public void forgetKnownNetwork_serviceConnected() throws RemoteException {
        KnownNetwork network = buildKnownNetwork();
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);

        manager.forgetKnownNetwork(network);

        verify(mService).forgetKnownNetwork(network);
    }

    @Test
    public void forgetKnownNetwork_remoteException_shouldFail() throws RemoteException {
        KnownNetwork network = buildKnownNetwork();
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);
        doThrow(new RemoteException()).when(mService).forgetKnownNetwork(network);

        assertThat(manager.forgetKnownNetwork(network)).isFalse();
    }

    @Test
    public void getHotspotNetworks_serviceNotConnected_shouldReturnNull() {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(null);

        assertThat(manager.getHotspotNetworks()).isNull();
    }

    @Test
    public void getHotspotNetworks_remoteException_shouldReturnNull() throws RemoteException {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);
        doThrow(new RemoteException()).when(mService).getHotspotNetworks();

        assertThat(manager.getHotspotNetworks()).isNull();
    }

    @Test
    public void getHotspotNetworks_shouldReturnNetworksList() throws RemoteException {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        List<HotspotNetwork> networks = List.of(buildHotspotNetwork());
        manager.setService(mService);
        when(mService.getHotspotNetworks()).thenReturn(networks);

        assertThat(manager.getHotspotNetworks()).containsExactly(buildHotspotNetwork());
    }

    @Test
    public void getKnownNetworks_serviceNotConnected_shouldReturnNull()
            throws RemoteException {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(null);

        assertThat(manager.getKnownNetworks()).isNull();
    }

    @Test
    public void getKnownNetworks_remoteException_shouldReturnNull() throws RemoteException {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);
        doThrow(new RemoteException()).when(mService).getKnownNetworks();

        assertThat(manager.getKnownNetworks()).isNull();
    }

    @Test
    public void getKnownNetworks_shouldReturnNetworksList() throws RemoteException {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        List<KnownNetwork> networks = List.of(buildKnownNetwork());
        manager.setService(mService);
        when(mService.getKnownNetworks()).thenReturn(networks);

        assertThat(manager.getKnownNetworks()).containsExactly(buildKnownNetwork());
    }

    @Test
    public void getSettingsState_serviceNotConnected_shouldReturnNull() throws RemoteException {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(null);

        assertThat(manager.getSettingsState()).isNull();
    }

    @Test
    public void getSettingsState_remoteException_shouldReturnNull() throws RemoteException {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);
        doThrow(new RemoteException()).when(mService).getSettingsState();

        assertThat(manager.getSettingsState()).isNull();
    }

    @Test
    public void getSettingsState_serviceConnected_shouldReturnState() throws RemoteException {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        SharedConnectivitySettingsState state =
                new SharedConnectivitySettingsState.Builder().setInstantTetherEnabled(true)
                        .setExtras(new Bundle()).build();
        manager.setService(mService);
        when(mService.getSettingsState()).thenReturn(state);

        assertThat(manager.getSettingsState()).isEqualTo(state);
    }

    @Test
    public void getHotspotNetworkConnectionStatus_serviceNotConnected_shouldReturnNull()
            throws RemoteException {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(null);

        assertThat(manager.getHotspotNetworkConnectionStatus()).isNull();
    }

    @Test
    public void getHotspotNetworkConnectionStatus_remoteException_shouldReturnNull()
            throws RemoteException {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);
        doThrow(new RemoteException()).when(mService).getHotspotNetworkConnectionStatus();

        assertThat(manager.getHotspotNetworkConnectionStatus()).isNull();
    }

    @Test
    public void getHotspotNetworkConnectionStatus_serviceConnected_shouldReturnStatus()
            throws RemoteException {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        HotspotNetworkConnectionStatus status = new HotspotNetworkConnectionStatus.Builder()
                .setStatus(HotspotNetworkConnectionStatus.CONNECTION_STATUS_ENABLING_HOTSPOT)
                .setExtras(new Bundle()).build();
        manager.setService(mService);
        when(mService.getHotspotNetworkConnectionStatus()).thenReturn(status);

        assertThat(manager.getHotspotNetworkConnectionStatus()).isEqualTo(status);
    }

    @Test
    public void getKnownNetworkConnectionStatus_serviceNotConnected_shouldReturnNull()
            throws RemoteException {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(null);

        assertThat(manager.getKnownNetworkConnectionStatus()).isNull();
    }

    @Test
    public void getKnownNetworkConnectionStatus_remoteException_shouldReturnNull()
            throws RemoteException {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        manager.setService(mService);
        doThrow(new RemoteException()).when(mService).getKnownNetworkConnectionStatus();

        assertThat(manager.getKnownNetworkConnectionStatus()).isNull();
    }

    @Test
    public void getKnownNetworkConnectionStatus_serviceConnected_shouldReturnStatus()
            throws RemoteException {
        SharedConnectivityManager manager = SharedConnectivityManager.create(mContext);
        KnownNetworkConnectionStatus status = new KnownNetworkConnectionStatus.Builder()
                .setStatus(KnownNetworkConnectionStatus.CONNECTION_STATUS_SAVED)
                .setExtras(new Bundle()).build();
        manager.setService(mService);
        when(mService.getKnownNetworkConnectionStatus()).thenReturn(status);

        assertThat(manager.getKnownNetworkConnectionStatus()).isEqualTo(status);
    }

    private void setResources(@Mock Context context) {
        when(context.getResources()).thenReturn(mResources);
        when(mResources.getString(anyInt()))
                .thenReturn(SERVICE_PACKAGE_NAME, SERVICE_INTENT_ACTION);
    }

    private HotspotNetwork buildHotspotNetwork() {
        HotspotNetwork.Builder builder = new HotspotNetwork.Builder()
                .setDeviceId(DEVICE_ID)
                .setNetworkProviderInfo(mNetworkProviderInfo)
                .setHostNetworkType(NETWORK_TYPE)
                .setNetworkName(NETWORK_NAME)
                .setHotspotSsid(HOTSPOT_SSID);
        Arrays.stream(HOTSPOT_SECURITY_TYPES).forEach(builder::addHotspotSecurityType);
        return builder.build();
    }

    private KnownNetwork buildKnownNetwork() {
        KnownNetwork.Builder builder = new KnownNetwork.Builder().setNetworkSource(NETWORK_SOURCE)
                .setSsid(SSID).setNetworkProviderInfo(mNetworkProviderInfo);
        Arrays.stream(SECURITY_TYPES).forEach(builder::addSecurityType);
        return builder.build();
    }
}
