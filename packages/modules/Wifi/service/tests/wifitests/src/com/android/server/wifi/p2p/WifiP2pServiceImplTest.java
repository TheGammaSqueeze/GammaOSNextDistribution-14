/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.wifi.p2p;

import static android.net.NetworkInfo.DetailedState.CONNECTING;
import static android.net.NetworkInfo.DetailedState.DISCONNECTED;
import static android.net.NetworkInfo.DetailedState.FAILED;
import static android.net.NetworkInfo.DetailedState.IDLE;
import static android.net.wifi.WifiManager.EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.net.module.util.Inet4AddressUtils.inet4AddressToIntHTL;
import static com.android.server.wifi.WifiSettingsConfigStore.D2D_ALLOWED_WHEN_INFRA_STA_DISABLED;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_P2P_DEVICE_ADDRESS;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_P2P_DEVICE_NAME;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_P2P_PENDING_FACTORY_RESET;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_VERBOSE_LOGGING_ENABLED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.BroadcastOptions;
import android.app.test.MockAnswerUtil;
import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.MacAddress;
import android.net.NetworkInfo;
import android.net.NetworkStack;
import android.net.TetheredClient;
import android.net.TetheringInterface;
import android.net.TetheringManager;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.IWifiP2pListener;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pExtListenParams;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pProvDiscEvent;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.WorkSource;
import android.os.test.TestLooper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.LocalLog;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.test.filters.SmallTest;

import com.android.internal.util.AsyncChannel;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.Clock;
import com.android.server.wifi.DeviceConfigFacade;
import com.android.server.wifi.FakeWifiLog;
import com.android.server.wifi.FrameworkFacade;
import com.android.server.wifi.HalDeviceManager;
import com.android.server.wifi.InterfaceConflictManager;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiDiagnostics;
import com.android.server.wifi.WifiDialogManager;
import com.android.server.wifi.WifiGlobals;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiP2pConnection;
import com.android.server.wifi.WifiSettingsConfigStore;
import com.android.server.wifi.coex.CoexManager;
import com.android.server.wifi.proto.nano.WifiMetricsProto;
import com.android.server.wifi.proto.nano.WifiMetricsProto.P2pConnectionEvent;
import com.android.server.wifi.util.LastCallerInfoManager;
import com.android.server.wifi.util.NetdWrapper;
import com.android.server.wifi.util.StringUtil;
import com.android.server.wifi.util.WaitingState;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.server.wifi.util.WifiPermissionsWrapper;
import com.android.wifi.flags.FeatureFlags;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.Spy;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Unit test harness for WifiP2pServiceImpl.
 */
@SmallTest
public class WifiP2pServiceImplTest extends WifiBaseTest {
    private static final String TAG = "WifiP2pServiceImplTest";
    private static final String IFACE_NAME_P2P = "mockP2p0";
    private static final String P2P_GO_IP = "192.168.49.1";
    private static final String P2P_PEER_IP = "192.168.49.50";
    private static final long STATE_CHANGE_WAITING_TIME = 1000;
    private static final String thisDeviceMac = "11:22:33:44:55:66";
    private static final String PEER_INTERFACE_ADDRESS = "aa:bb:cc:dd:aa:bb";
    private static final String thisDeviceName = "thisDeviceName";
    private static final String ANONYMIZED_DEVICE_ADDRESS = "02:00:00:00:00:00";
    private static final String TEST_PACKAGE_NAME = "com.p2p.test";
    private static final String TEST_NETWORK_NAME = "DIRECT-xy-NEW";
    private static final String TEST_ANDROID_ID = "314Deadbeef";
    private static final String[] TEST_REQUIRED_PERMISSIONS_T =
            new String[] {
                    android.Manifest.permission.NEARBY_WIFI_DEVICES,
                    android.Manifest.permission.ACCESS_WIFI_STATE
            };
    private static final String[] TEST_EXCLUDED_PERMISSIONS_T =
            new String[] {
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            };
    private static final int TEST_GROUP_FREQUENCY = 5180;
    private static final int P2P_INVITATION_RECEIVED_TIMEOUT_MS = 5180;
    private static final int P2P_PEER_AUTH_TIMEOUT_MS = 1000;
    private static final int P2P_EXT_LISTEN_PERIOD_MS = 250;
    private static final int P2P_EXT_LISTEN_INTERVAL_MS = 450;

    private ArgumentCaptor<BroadcastReceiver> mBcastRxCaptor = ArgumentCaptor.forClass(
            BroadcastReceiver.class);
    private ArgumentCaptor<WorkSource> mWsCaptor = ArgumentCaptor.forClass(WorkSource.class);
    private Binder mClient1;
    private Binder mClient2;

    private BroadcastReceiver mLocationModeReceiver;
    private BroadcastReceiver mWifiStateChangedReceiver;
    private BroadcastReceiver mTetherStateReceiver;
    private BroadcastReceiver mUserRestrictionReceiver;
    private Handler mClientHandler;
    private Messenger mP2pStateMachineMessenger;
    private Messenger mClientMessenger;
    private WifiP2pServiceImpl mWifiP2pServiceImpl;
    private TestLooper mClientHanderLooper;
    private TestLooper mLooper;
    private MacAddress mTestWifiP2pPeerAddress;
    private WifiP2pConfig mTestWifiP2pPeerConfig;
    private WifiP2pConfig mTestWifiP2pFastConnectionConfig;
    private WifiP2pConfig mTestWifiP2pJoinExistingGroupConfig;
    private WifiP2pGroup mTestWifiP2pNewPersistentGoGroup;
    private WifiP2pGroup mTestWifiP2pGroup;
    private WifiP2pDevice mTestWifiP2pDevice;
    private WifiP2pGroupList mGroups = new WifiP2pGroupList(null, null);
    private WifiP2pDevice mTestThisDevice;
    private ArgumentCaptor<Message> mMessageCaptor = ArgumentCaptor.forClass(Message.class);
    private MockitoSession mStaticMockSession = null;
    private Bundle mAttribution = new Bundle();
    private ArgumentCaptor<TetheringManager.TetheringEventCallback> mTetheringEventCallbackCaptor =
            ArgumentCaptor.forClass(TetheringManager.TetheringEventCallback.class);
    private TetheringManager.TetheringEventCallback mTetheringEventCallback;
    private Bundle mExtras = new Bundle();
    private IWifiP2pListener mP2pListener = mock(IWifiP2pListener.class);
    private ArgumentCaptor<WifiSettingsConfigStore.OnSettingsChangedListener>
            mD2DAllowedSettingsCallbackCaptor =
            ArgumentCaptor.forClass(WifiSettingsConfigStore.OnSettingsChangedListener.class);

    @Mock Bundle mBundle;
    @Mock Context mContext;
    @Mock FrameworkFacade mFrameworkFacade;
    @Mock HandlerThread mHandlerThread;
    @Mock NetdWrapper mNetdWrapper;
    @Mock PackageManager mPackageManager;
    @Mock Resources mResources;
    @Mock Configuration mConfiguration;
    @Mock NetworkInterface mP2pNetworkInterface;
    @Mock WifiInjector mWifiInjector;
    @Mock BroadcastOptions mBroadcastOptions;
    @Mock WifiManager mMockWifiManager;
    @Mock WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock WifiSettingsConfigStore mWifiSettingsConfigStore;
    @Mock WifiPermissionsWrapper mWifiPermissionsWrapper;
    @Mock WifiP2pNative mWifiNative;
    @Mock WifiP2pServiceInfo mTestWifiP2pServiceInfo;
    @Mock WifiP2pServiceRequest mTestWifiP2pServiceRequest;
    @Mock UserManager mUserManager;
    @Mock WifiP2pMetrics mWifiP2pMetrics;
    @Mock WifiManager mWifiManager;
    @Mock WifiInfo mWifiInfo;
    @Mock CoexManager mCoexManager;
    @Spy FakeWifiLog mLog;
    @Spy MockWifiP2pMonitor mWifiMonitor;
    @Mock WifiGlobals mWifiGlobals;
    @Mock AlarmManager mAlarmManager;
    @Mock WifiDialogManager mWifiDialogManager;
    @Mock WifiDialogManager.DialogHandle mDialogHandle;
    @Mock InterfaceConflictManager mInterfaceConflictManager;
    @Mock Clock mClock;
    @Mock LayoutInflater mLayoutInflater;
    @Mock View mView;
    @Mock AlertDialog.Builder mAlertDialogBuilder;
    @Mock AlertDialog mAlertDialog;
    @Mock AsyncChannel mAsyncChannel;
    @Mock LastCallerInfoManager mLastCallerInfoManager;
    CoexManager.CoexListener mCoexListener;
    @Mock DeviceConfigFacade mDeviceConfigFacade;
    @Mock TetheringManager mTetheringManager;
    @Mock WifiDiagnostics mWifiDiagnostics;
    @Mock WifiP2pConnection mWifiP2pConnection;
    @Mock Collection<TetheredClient> mClients;
    @Mock TetheredClient mTetheredClient;
    @Mock TetheredClient.AddressInfo mAddressInfo;
    @Mock List<TetheredClient.AddressInfo> mAddresses;
    @Mock LocalLog mLocalLog;
    @Mock FeatureFlags mFeatureFlags;

    private void generatorTestData() {
        mTestWifiP2pGroup = new WifiP2pGroup();
        mTestWifiP2pGroup.setNetworkName("TestGroupName");
        mTestWifiP2pDevice = spy(new WifiP2pDevice());
        mTestWifiP2pDevice.deviceName = "TestDeviceName";
        mTestWifiP2pDevice.deviceAddress = "aa:bb:cc:dd:ee:ff";

        mTestWifiP2pPeerAddress = MacAddress.fromString(mTestWifiP2pDevice.deviceAddress);

        // for general connect command
        mTestWifiP2pPeerConfig = new WifiP2pConfig();
        mTestWifiP2pPeerConfig.deviceAddress = mTestWifiP2pDevice.deviceAddress;

        // for config with join existing group flag set to true
        mTestWifiP2pJoinExistingGroupConfig = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString((mTestWifiP2pDevice.deviceAddress)))
                .setJoinExistingGroup(true)
                .build();

        // for fast-connection connect command
        mTestWifiP2pFastConnectionConfig = new WifiP2pConfig.Builder()
                .setNetworkName("DIRECT-XY-HELLO")
                .setPassphrase("DEADBEEF")
                .build();

        // for general group started event
        mTestWifiP2pNewPersistentGoGroup = new WifiP2pGroup();
        mTestWifiP2pNewPersistentGoGroup.setNetworkId(WifiP2pGroup.NETWORK_ID_PERSISTENT);
        mTestWifiP2pNewPersistentGoGroup.setNetworkName(TEST_NETWORK_NAME);
        mTestWifiP2pNewPersistentGoGroup.setOwner(new WifiP2pDevice(thisDeviceMac));
        mTestWifiP2pNewPersistentGoGroup.setIsGroupOwner(true);
        mTestWifiP2pNewPersistentGoGroup.setInterface(IFACE_NAME_P2P);

        mGroups.clear();
        WifiP2pGroup group1 = new WifiP2pGroup();
        group1.setNetworkId(0);
        group1.setNetworkName(mTestWifiP2pGroup.getNetworkName());
        group1.setOwner(mTestWifiP2pDevice);
        group1.setIsGroupOwner(false);
        mGroups.add(group1);

        WifiP2pGroup group2 = new WifiP2pGroup();
        group2.setNetworkId(1);
        group2.setNetworkName("DIRECT-ab-Hello");
        group2.setOwner(new WifiP2pDevice("12:34:56:78:90:ab"));
        group2.setIsGroupOwner(false);
        mGroups.add(group2);

        WifiP2pGroup group3 = new WifiP2pGroup();
        group3.setNetworkId(2);
        group3.setNetworkName("DIRECT-cd-OWNER");
        group3.setOwner(new WifiP2pDevice(thisDeviceMac));
        group3.setIsGroupOwner(true);
        mGroups.add(group3);

        mTestThisDevice = new WifiP2pDevice();
        mTestThisDevice.deviceName = thisDeviceName;
        mTestThisDevice.deviceAddress = thisDeviceMac;
        mTestThisDevice.primaryDeviceType = "10-0050F204-5";
    }

    /**
     * Simulate Location Mode change: Changes the location manager return values and dispatches a
     * broadcast.
     *
     * @param isLocationModeEnabled whether the location mode is enabled.,
     */
    private void simulateLocationModeChange(boolean isLocationModeEnabled) {
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(isLocationModeEnabled);

        Intent intent = new Intent(LocationManager.MODE_CHANGED_ACTION);
        mLocationModeReceiver.onReceive(mContext, intent);
    }

    /**
     * Simulate Wi-Fi state change: broadcast state change and modify the API return value.
     *
     * @param isWifiOn whether the wifi mode is enabled.
     */
    private void simulateWifiStateChange(boolean isWifiOn) {
        when(mMockWifiManager.getWifiState()).thenReturn(
                isWifiOn ? WifiManager.WIFI_STATE_ENABLED : WifiManager.WIFI_STATE_DISABLED);

        Intent intent = new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intent.putExtra(WifiManager.EXTRA_WIFI_STATE,
                isWifiOn ? WifiManager.WIFI_STATE_ENABLED : WifiManager.WIFI_STATE_DISABLED);
        mWifiStateChangedReceiver.onReceive(mContext, intent);
    }

    /**
     * Simulate DISALLOW_WIFI_DIRECT user restriction change.
     *
     * @param isWifiDirectDisallowed whether user restriction is set.
     */
    private void simulateUserRestrictionChange(boolean isWifiDirectDisallowed) {
        when(mBundle.getBoolean(UserManager.DISALLOW_WIFI_DIRECT))
                .thenReturn(isWifiDirectDisallowed);
        Intent intent = new Intent(UserManager.ACTION_USER_RESTRICTIONS_CHANGED);
        mUserRestrictionReceiver.onReceive(mContext, intent);
    }

    /**
     * Simulate tethering flow is completed
     */
    private void simulateTetherReady() throws Exception {
        if (!SdkLevel.isAtLeastS()) {
            ArrayList<String> availableList = new ArrayList<>();
            ArrayList<String> localOnlyList = new ArrayList<>();
            localOnlyList.add(IFACE_NAME_P2P);
            ArrayList<String> tetherList = new ArrayList<>();
            ArrayList<String> erroredList = new ArrayList<>();

            Intent intent = new Intent(TetheringManager.ACTION_TETHER_STATE_CHANGED);
            intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            intent.putStringArrayListExtra(TetheringManager.EXTRA_AVAILABLE_TETHER, availableList);
            intent.putStringArrayListExtra(TetheringManager.EXTRA_ACTIVE_LOCAL_ONLY, localOnlyList);
            intent.putStringArrayListExtra(TetheringManager.EXTRA_ACTIVE_TETHER, tetherList);
            intent.putStringArrayListExtra(TetheringManager.EXTRA_ERRORED_TETHER, erroredList);
            mTetherStateReceiver.onReceive(mContext, intent);
            mLooper.dispatchAll();
        } else {
            mTetheringEventCallback.onLocalOnlyInterfacesChanged(Collections.unmodifiableSet(
                    (new ArraySet(new TetheringInterface[]{
                            new TetheringInterface(TetheringManager.TETHERING_WIFI_P2P,
                                    IFACE_NAME_P2P)}))));
            mLooper.dispatchAll();
        }
    }

    private void simulateOnClientsChanged() throws Exception {
        List<TetheredClient> clients = new ArrayList<>(1);
        clients.add(mTetheredClient);
        mClients = clients;
        mTetheringEventCallback.onClientsChanged(mClients);
        mLooper.dispatchAll();
    }

    /**
     * Mock send WifiP2pManager.UPDATE_CHANNEL_INFO
     *
     * @param pkgName package name used for p2p channel init
     * @param featureId The feature in the package
     * @param binder client binder used for p2p channel init
     * @param replyMessenger for checking replied message.
     */
    private void sendChannelInfoUpdateMsg(String pkgName, @Nullable String featureId,
            Binder binder, Messenger replyMessenger) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pManager.UPDATE_CHANNEL_INFO;
        Bundle bundle = new Bundle();
        bundle.putString(WifiP2pManager.CALLING_PACKAGE, pkgName);
        bundle.putString(WifiP2pManager.CALLING_FEATURE_ID, featureId);
        bundle.putBinder(WifiP2pManager.CALLING_BINDER, binder);
        if (SdkLevel.isAtLeastS()) {
            msg.obj = new AttributionSource(1000, TEST_PACKAGE_NAME, null);
        }
        msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, bundle);
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Mock send WifiP2pManager.ADD_LOCAL_SERVICE with mTestWifiP2pServiceInfo
     *
     * @param replyMessenger for checking replied message.
     */
    private void sendAddLocalServiceMsg(Messenger replyMessenger) throws Exception {
        Message msg = Message.obtain();
        Bundle extras = new Bundle();
        extras.putParcelable(WifiP2pManager.EXTRA_PARAM_KEY_SERVICE_INFO, mTestWifiP2pServiceInfo);
        msg.what = WifiP2pManager.ADD_LOCAL_SERVICE;
        if (SdkLevel.isAtLeastS()) {
            msg.obj = new AttributionSource(1000, TEST_PACKAGE_NAME, null);
        }
        msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, extras);
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Mock send WifiP2pManager.CONNECT with ConfigValidAsGroup
     *
     * @param replyMessenger for checking replied message.
     */
    private void sendConnectMsgWithConfigValidAsGroup(Messenger replyMessenger) throws Exception {
        sendConnectMsg(replyMessenger, mTestWifiP2pFastConnectionConfig);
    }

    /**
     * Mock send WifiP2pManager.CREATE_GROUP with ConfigValidAsGroup
     *
     * @param replyMessenger for checking replied message.
     */
    private void sendCreateGroupMsgWithConfigValidAsGroup(Messenger replyMessenger)
            throws Exception {
        sendCreateGroupMsg(replyMessenger, 0, mTestWifiP2pFastConnectionConfig);
    }

    /**
     * Mock send WifiP2pManager.DISCOVER_PEERS
     *
     * @param replyMessenger for checking replied message.
     */
    private void sendDiscoverPeersMsg(Messenger replyMessenger) throws Exception {
        sendDiscoverPeersMsg(
                replyMessenger, WifiP2pManager.WIFI_P2P_SCAN_FULL,
                WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED);
    }

    /**
     * Mock send WifiP2pManager.DISCOVER_PEERS
     *
     * @param replyMessenger for checking replied message.
     * @param type indicates what channels to scan.
     * @param frequencyMhz is the frequency to be scanned.
     */
    private void sendDiscoverPeersMsg(
            Messenger replyMessenger, @WifiP2pManager.WifiP2pScanType int type,
            int frequencyMhz) throws Exception {
        Message msg = Message.obtain();
        Bundle extras = new Bundle();
        extras.putInt(WifiP2pManager.EXTRA_PARAM_KEY_PEER_DISCOVERY_FREQ, frequencyMhz);
        msg.what = WifiP2pManager.DISCOVER_PEERS;
        msg.arg1 = type;
        if (SdkLevel.isAtLeastS()) {
            msg.obj = new AttributionSource(1000, TEST_PACKAGE_NAME, null);
        }
        msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, extras);
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Mock send WifiP2pManager.ADD_SERVICE_REQUEST with mocked mTestWifiP2pServiceRequest
     *
     * @param replyMessenger for checking replied message.
     */
    private void sendAddServiceRequestMsg(Messenger replyMessenger) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pManager.ADD_SERVICE_REQUEST;
        msg.replyTo = replyMessenger;
        msg.obj = mTestWifiP2pServiceRequest;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Mock send WifiP2pManager.DISCOVER_SERVICES
     *
     * @param replyMessenger for checking replied message.
     */
    private void sendDiscoverServiceMsg(Messenger replyMessenger) throws Exception {
        Message msg = Message.obtain();
        Bundle extras = new Bundle();
        msg.what = WifiP2pManager.DISCOVER_SERVICES;
        if (SdkLevel.isAtLeastS()) {
            msg.obj = new AttributionSource(1000, TEST_PACKAGE_NAME, null);
        }
        msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, extras);
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Mock send WifiP2pManager.REQUEST_PEERS
     *
     * @param replyMessenger for checking replied message.
     */
    private void sendRequestPeersMsg(Messenger replyMessenger) throws Exception {
        Message msg = Message.obtain();
        Bundle extras = new Bundle();
        msg.what = WifiP2pManager.REQUEST_PEERS;
        if (SdkLevel.isAtLeastS()) {
            msg.obj = new AttributionSource(1000, TEST_PACKAGE_NAME, null);
        }
        msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, extras);
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    private void sendNegotiationRequestEvent(WifiP2pConfig config) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pMonitor.P2P_GO_NEGOTIATION_REQUEST_EVENT;
        msg.obj = config;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Mock send WifiP2pManager.REQUEST_GROUP_INFO
     *
     * @param replyMessenger for checking replied message.
     */
    private void sendRequestGroupInfoMsg(Messenger replyMessenger) throws Exception {
        Message msg = Message.obtain();
        Bundle extras = new Bundle();
        msg.what = WifiP2pManager.REQUEST_GROUP_INFO;
        if (SdkLevel.isAtLeastS()) {
            msg.obj = new AttributionSource(1000, TEST_PACKAGE_NAME, null);
        }
        msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, extras);
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Mock send WifiP2pManager.DELETE_PERSISTENT_GROUP.
     *
     * @param replyMessenger for checking replied message.
     * @param netId is the network id of the p2p group.
     */
    private void sendDeletePersistentGroupMsg(Messenger replyMessenger,
            int netId) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pManager.DELETE_PERSISTENT_GROUP;
        msg.arg1 = netId;
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send WifiP2pMonitor.P2P_GROUP_STARTED_EVENT.
     *
     * @param group the started group.
     */
    private void sendGroupStartedMsg(WifiP2pGroup group) throws Exception {
        if (group.getNetworkId() == WifiP2pGroup.NETWORK_ID_PERSISTENT) {
            mGroups.add(group);
        }

        Message msg = Message.obtain();
        msg.what = WifiP2pMonitor.P2P_GROUP_STARTED_EVENT;
        msg.obj = group;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send WifiP2pMonitor.P2P_GROUP_REMOVED_EVENT.
     */
    private void sendGroupRemovedMsg() throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pMonitor.P2P_GROUP_REMOVED_EVENT;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send WifiP2pMonitor.P2P_DEVICE_FOUND_EVENT.
     *
     * @param device the found device.
     */
    private void sendDeviceFoundEventMsg(WifiP2pDevice device) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pMonitor.P2P_DEVICE_FOUND_EVENT;
        msg.obj = device;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send WifiP2pMonitor.P2P_INVITATION_RESULT_EVENT.
     *
     * @param status invitation result.
     */
    private void sendInvitationResultMsg(
            WifiP2pServiceImpl.P2pStatus status) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pMonitor.P2P_INVITATION_RESULT_EVENT;
        msg.obj = status;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send WifiP2pMonitor.P2P_INVITATION_RECEIVED_EVENT.
     *
     * @param group Peer group information
     */
    private void sendInvitationReceivedMsg(WifiP2pGroup group) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pMonitor.P2P_INVITATION_RECEIVED_EVENT;
        msg.obj = group;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send WifiP2pMonitor.AP_STA_CONNECTED_EVENT.
     *
     * @param device Peer device information.
     */
    private void sendApStaConnectedEvent(WifiP2pDevice device) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pMonitor.AP_STA_CONNECTED_EVENT;
        msg.obj = device;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send WifiP2pMonitor.AP_STA_DISCONNECTED_EVENT.
     *
     * @param device Peer device information.
     */
    private void sendApStaDisConnectedEvent(WifiP2pDevice device) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pMonitor.AP_STA_DISCONNECTED_EVENT;
        msg.obj = device;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Mock send WifiP2pManager.SET_CHANNEL
     *
     * @param replyMessenger for checking replied message.
     * @param p2pChannels stores the listen and operating channels.
     */
    private void sendSetChannelMsg(Messenger replyMessenger,
            Bundle p2pChannels) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pManager.SET_CHANNEL;
        msg.replyTo = replyMessenger;
        msg.obj = p2pChannels;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Mock send WifiP2pManager.START_WPS
     *
     * @param replyMessenger for checking replied message.
     * @param wps is the WPS configuration.
     */
    private void sendStartWpsMsg(Messenger replyMessenger, WpsInfo wps) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pManager.START_WPS;
        msg.replyTo = replyMessenger;
        msg.obj = wps;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Mock send WifiP2pManager.SET_DEVICE_NAME
     *
     * @param replyMessenger for checking replied message.
     * @param dev is the P2p device configuration.
     */
    private void sendSetDeviceNameMsg(
            Messenger replyMessenger, WifiP2pDevice dev) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pManager.SET_DEVICE_NAME;
        msg.replyTo = replyMessenger;
        msg.obj = dev;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Mock send WifiP2pManager.SET_WFD_INFO
     *
     * @param replyMessenger for checking replied message.
     * @param wfdInfo is the P2p device's wfd information.
     */
    private void sendSetWfdInfoMsg(
            Messenger replyMessenger, WifiP2pWfdInfo wfdInfo) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pManager.SET_WFD_INFO;
        msg.replyTo = replyMessenger;
        msg.obj = wfdInfo;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Mock send WifiP2pManager.SET_ONGOING_PEER_CONFIG
     *
     * @param replyMessenger for checking replied message.
     * @param config used for change an ongoing peer connection.
     */
    private void sendSetOngoingPeerConfigMsg(
            Messenger replyMessenger, WifiP2pConfig config) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pManager.SET_ONGOING_PEER_CONFIG;
        msg.replyTo = replyMessenger;
        msg.obj = config;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Mock send WifiP2pManager.REMOVE_LOCAL_SERVICE.
     *
     * @param replyMessenger for checking replied message.
     * @param servInfo is the local service information.
     */
    private void sendRemoveLocalServiceMsg(Messenger replyMessenger,
            WifiP2pServiceInfo servInfo) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pManager.REMOVE_LOCAL_SERVICE;
        msg.obj = servInfo;
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Mock send WifiP2pManager.REMOVE_SERVICE_REQUEST.
     *
     * @param replyMessenger for checking replied message.
     * @param req is the service discovery request.
     */
    private void sendRemoveServiceRequestMsg(Messenger replyMessenger,
            WifiP2pServiceRequest req) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pManager.REMOVE_SERVICE_REQUEST;
        msg.obj = req;
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send Connect API msg.
     *
     * @param replyMessenger for checking replied message.
     * @param config options as described in {@link WifiP2pConfig} class.
     */
    private void sendConnectMsg(Messenger replyMessenger,
            WifiP2pConfig config) throws Exception {
        Message msg = Message.obtain();
        Bundle extras = new Bundle();
        extras.putParcelable(WifiP2pManager.EXTRA_PARAM_KEY_CONFIG, config);
        msg.what = WifiP2pManager.CONNECT;
        if (SdkLevel.isAtLeastS()) {
            msg.obj = new AttributionSource(1000, TEST_PACKAGE_NAME, null);
        }
        msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, extras);
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }


    /**
     * Send Set Ongoing Peer Config API msg.
     *
     * @param replyMessenger for checking replied message.
     * @param config options as described in {@link WifiP2pConfig} class.
     */
    private void sendSetOngoingPeerMsg(Messenger replyMessenger,
            WifiP2pConfig config) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pManager.SET_ONGOING_PEER_CONFIG;
        msg.obj = config;
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send CreateGroup API msg.
     *
     * @param replyMessenger for checking replied message.
     * @param config options as described in {@link WifiP2pConfig} class.
     */
    private void sendCreateGroupMsg(Messenger replyMessenger,
            int netId,
            WifiP2pConfig config) throws Exception {
        Message msg = Message.obtain();
        Bundle extras = new Bundle();
        extras.putParcelable(WifiP2pManager.EXTRA_PARAM_KEY_CONFIG, config);
        msg.what = WifiP2pManager.CREATE_GROUP;
        msg.arg1 = netId;
        if (SdkLevel.isAtLeastS()) {
            msg.obj = new AttributionSource(1000, TEST_PACKAGE_NAME, null);
        }
        msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, extras);
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send SetVendorElements API msg.
     *
     * @param replyMessenger For checking replied message.
     * @param ies The list of information elements.
     */
    private void sendSetVendorElementsMsg(Messenger replyMessenger,
            ArrayList<ScanResult.InformationElement> ies) throws Exception {
        Message msg = Message.obtain();
        Bundle extras = new Bundle();
        extras.putParcelableArrayList(WifiP2pManager.EXTRA_PARAM_KEY_INFORMATION_ELEMENT_LIST,
                ies);
        msg.what = WifiP2pManager.SET_VENDOR_ELEMENTS;
        if (SdkLevel.isAtLeastS()) {
            msg.obj = mContext.getAttributionSource();
        }
        msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, extras);
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send AddExternalApprover API msg.
     *
     * @param replyMessenger For checking replied message.
     * @param devAddr the peer address.
     * @param binder the application binder.
     */
    private void sendAddExternalApproverMsg(Messenger replyMessenger,
            MacAddress devAddr, Binder binder) throws Exception {
        Message msg = Message.obtain();
        Bundle extras = new Bundle();
        if (null != devAddr) {
            extras.putParcelable(WifiP2pManager.EXTRA_PARAM_KEY_PEER_ADDRESS, devAddr);
        }
        if (null != binder) {
            extras.putBinder(WifiP2pManager.CALLING_BINDER, binder);
        }
        msg.what = WifiP2pManager.ADD_EXTERNAL_APPROVER;
        if (SdkLevel.isAtLeastS()) {
            msg.obj = new AttributionSource(1000, TEST_PACKAGE_NAME, null);
        }
        msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, extras);
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send RemoveExternalApprover API msg.
     *
     * @param replyMessenger For checking replied message.
     * @param devAddr the peer address.
     */
    private void sendRemoveExternalApproverMsg(Messenger replyMessenger,
            MacAddress devAddr, Binder binder) throws Exception {
        Message msg = Message.obtain();
        Bundle extras = new Bundle();
        if (null != devAddr) {
            extras.putParcelable(WifiP2pManager.EXTRA_PARAM_KEY_PEER_ADDRESS, devAddr);
        }
        if (null != binder) {
            extras.putBinder(WifiP2pManager.CALLING_BINDER, binder);
        }
        msg.what = WifiP2pManager.REMOVE_EXTERNAL_APPROVER;
        if (SdkLevel.isAtLeastS()) {
            msg.obj = new AttributionSource(1000, TEST_PACKAGE_NAME, null);
        }
        msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, extras);
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send SetConnectionRequestResult API msg.
     *
     * @param replyMessenger For checking replied message.
     * @param devAddr the peer address.
     * @param result the decision for the incoming request.
     */
    private void sendSetConnectionRequestResultMsg(Messenger replyMessenger,
            MacAddress devAddr, int result, Binder binder) throws Exception {
        Message msg = Message.obtain();
        Bundle extras = new Bundle();
        if (null != devAddr) {
            extras.putParcelable(WifiP2pManager.EXTRA_PARAM_KEY_PEER_ADDRESS, devAddr);
        }
        if (null != binder) {
            extras.putBinder(WifiP2pManager.CALLING_BINDER, binder);
        }
        msg.what = WifiP2pManager.SET_CONNECTION_REQUEST_RESULT;
        if (SdkLevel.isAtLeastS()) {
            msg.obj = new AttributionSource(1000, TEST_PACKAGE_NAME, null);
        }
        msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, extras);
        msg.arg1 = result;
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send P2P_GO_NEGOTIATION_FAILURE_EVENT
     *
     * @param replyMessenger For checking replied message.
     * @param status the P2pStatus.
     */
    private void sendGoNegotiationFailureEvent(Messenger replyMessenger,
            WifiP2pServiceImpl.P2pStatus status) throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pMonitor.P2P_GO_NEGOTIATION_FAILURE_EVENT;
        msg.obj = status;
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send AsyncChannel.CMD_CHANNEL_HALF_CONNECTED
     *
     * @param replyMessenger For checking replied message.
     * @param channel AsyncChannel of the connection
     */
    private void sendChannelHalfConnectedEvent(Messenger replyMessenger, AsyncChannel channel)
            throws Exception {
        Message msg = Message.obtain();
        msg.what = AsyncChannel.CMD_CHANNEL_HALF_CONNECTED;
        msg.arg1 = AsyncChannel.STATUS_SUCCESSFUL;
        msg.obj = channel;
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    private void sendOngoingPeerConfig(Messenger replyMessenger, AsyncChannel channel)
            throws Exception {
        Message msg = Message.obtain();
        msg.what = WifiP2pManager.SET_ONGOING_PEER_CONFIG;
        msg.arg1 = AsyncChannel.STATUS_SUCCESSFUL;
        msg.obj = channel;
        msg.replyTo = replyMessenger;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send simple API msg.
     *
     * Mock the API msg without arguments.
     *
     * @param replyMessenger for checking replied message.
     */
    private void sendSimpleMsg(Messenger replyMessenger,
            int what) throws Exception {
        Message msg = Message.obtain();
        Bundle extras = new Bundle();
        msg.what = what;
        if (SdkLevel.isAtLeastS()) {
            msg.obj = new AttributionSource(1000, TEST_PACKAGE_NAME, null);
        }
        msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, extras);
        if (replyMessenger != null) {
            msg.replyTo = replyMessenger;
        }
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send simple API msg.
     *
     * Mock the API msg with objects.
     *
     * @param replyMessenger for checking replied message.
     */
    private void sendSimpleMsg(Messenger replyMessenger,
            int what, Object obj) throws Exception {
        Message msg = Message.obtain();
        msg.what = what;
        msg.obj = obj;
        if (replyMessenger != null) {
            msg.replyTo = replyMessenger;
        }
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    private void setTargetSdkGreaterThanT() {
        if (SdkLevel.isAtLeastT()) {
            when(mWifiPermissionsUtil.isTargetSdkLessThan(any(),
                    eq(Build.VERSION_CODES.TIRAMISU), anyInt())).thenReturn(false);
        }
    }

    /**
     * Send simple API msg.
     *
     * Mock the API msg with int arg.
     *
     * @param replyMessenger for checking replied message.
     */
    private void sendSimpleMsg(Messenger replyMessenger,
            int what, int arg1) throws Exception {
        Message msg = Message.obtain();
        msg.what = what;
        msg.arg1 = arg1;
        if (replyMessenger != null) {
            msg.replyTo = replyMessenger;
        }
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Send simple API msg.
     *
     * Mock the API msg with int arg.
     *
     * @param replyMessenger for checking replied message.
     */
    private void sendSimpleMsg(Messenger replyMessenger,
            int what, int arg1, Object obj) throws Exception {
        Message msg = Message.obtain();
        msg.what = what;
        msg.arg1 = arg1;
        msg.obj = obj;
        if (replyMessenger != null) {
            msg.replyTo = replyMessenger;
        }
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /** This helper is for no client info tests only. */
    private void forceP2pEnabledWithoutClientInfo() throws Exception {
        simulateWifiStateChange(true);
        simulateLocationModeChange(true);
        mWifiP2pServiceImpl.getMessenger(mClient1, TEST_PACKAGE_NAME, null);
        // send a command to force P2P enabled.
        sendSimpleMsg(mClientMessenger, WifiP2pManager.PING);
    }

    /**
     * force P2p State enter InactiveState to start others unit test
     *
     * @param clientBinder client binder to use for p2p channel init
     */
    private void forceP2pEnabled(Binder clientBinder) throws Exception {
        simulateWifiStateChange(true);
        simulateLocationModeChange(true);
        checkIsP2pInitWhenClientConnected(true, clientBinder,
                new WorkSource(clientBinder.getCallingUid(), TEST_PACKAGE_NAME));
        verify(mTetheringManager).registerTetheringEventCallback(any(Executor.class),
                mTetheringEventCallbackCaptor.capture());
        mTetheringEventCallback = mTetheringEventCallbackCaptor.getValue();
        if (SdkLevel.isAtLeastU()) {
            ArgumentCaptor<String[]> permissionCaptor = ArgumentCaptor.forClass(String[].class);
            verify(mContext).sendBroadcastWithMultiplePermissions(any(),
                    permissionCaptor.capture());
            String[] permission = permissionCaptor.getValue();
            assertEquals(new String[]{NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK}, permission);
        }
        verify(mContext).sendBroadcastWithMultiplePermissions(
                argThat(new WifiP2pServiceImplTest
                        .P2pConnectionChangedIntentMatcherForNetworkState(IDLE)), any());
        verify(mContext, never()).sendBroadcastWithMultiplePermissions(
                argThat(new WifiP2pServiceImplTest
                        .P2pConnectionChangedIntentMatcherForNetworkState(FAILED)), any());
        if (SdkLevel.isAtLeastT()) {
            verify(mContext, atLeastOnce()).sendBroadcast(
                    argThat(new WifiP2pServiceImplTest
                            .P2pConnectionChangedIntentMatcherForNetworkState(IDLE)), any(), any());
            verify(mBroadcastOptions, atLeastOnce())
                    .setRequireAllOfPermissions(TEST_REQUIRED_PERMISSIONS_T);
            verify(mBroadcastOptions, atLeastOnce())
                    .setRequireNoneOfPermissions(TEST_EXCLUDED_PERMISSIONS_T);
        }
    }

    private void simulateInitChannel(Binder clientBinder, Bundle bundle) throws Exception {
        mWifiP2pServiceImpl.getMessenger(clientBinder, TEST_PACKAGE_NAME, bundle);
        sendChannelInfoUpdateMsg(TEST_PACKAGE_NAME, "testFeature", clientBinder, mClientMessenger);
    }

    private void simulateInitChannel(Binder clientBinder) throws Exception {
        simulateInitChannel(clientBinder, null);
    }

    /**
     * Check is P2p init as expected when client connected
     *
     * @param expectInit set true if p2p init should succeed as expected, set false when
     *        expected init should not happen
     * @param expectReplace set true if p2p worksource replace should succeed as expected, set false
     *        when replace should not happen
     * @param clientBinder client binder to use for p2p channel init
     * @param expectedRequestorWs Expected merged requestorWs
     */
    private void checkIsP2pInitWhenClientConnected(boolean expectInit,
            Binder clientBinder, WorkSource expectedRequestorWs)
            throws Exception {
        simulateInitChannel(clientBinder);
        if (expectInit) {
            // send a command to force P2P enabled.
            sendSimpleMsg(mClientMessenger, WifiP2pManager.PING);
        }
        mLooper.dispatchAll();
        reset(mClientHandler);
        if (expectInit) {
            verify(mWifiNative).setupInterface(any(), any(), eq(expectedRequestorWs));
            verify(mNetdWrapper).setInterfaceUp(anyString());
            verify(mWifiMonitor, atLeastOnce()).registerHandler(anyString(), anyInt(), any());
            // Verify timer is scheduled
            verify(mAlarmManager, times(2)).setExact(anyInt(), anyLong(),
                    eq(mWifiP2pServiceImpl.P2P_IDLE_SHUTDOWN_MESSAGE_TIMEOUT_TAG), any(), any());
        } else {
            verify(mWifiNative, never()).setupInterface(any(), any(), any());
            verify(mNetdWrapper, never()).setInterfaceUp(anyString());
            verify(mWifiMonitor, never()).registerHandler(anyString(), anyInt(), any());
        }
    }

    /**
     * Check is P2p teardown as expected when client disconnected
     *
     * @param expectTearDown set true if p2p teardown should succeed as expected,
     *        set false when expected teardown should not happen
     * @param expectReplace set true if p2p worksource replace should succeed as expected, set false
     *        when replace should not happen
     * @param clientBinder client binder to use for p2p channel init
     * @param expectedRequestorWs Expected merged requestorWs
     */
    private void checkIsP2pTearDownWhenClientDisconnected(
            boolean expectTearDown,
            Binder clientBinder, WorkSource expectedRequestorWs) throws Exception {
        mWifiP2pServiceImpl.close(clientBinder);
        mLooper.dispatchAll();
        if (expectTearDown) {
            verify(mWifiNative).teardownInterface();
            verify(mWifiMonitor).stopMonitoring(anyString());
        } else {
            verify(mWifiNative, never()).teardownInterface();
            verify(mWifiMonitor, never()).stopMonitoring(anyString());
        }
    }

    private void verifyDeviceChangedBroadcastIntent(Intent intent) {
        WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
        assertEquals(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION, intent.getAction());
        assertEquals(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT, intent.getFlags());
        assertEquals(mTestThisDevice.deviceName, device.deviceName);
        if (!TextUtils.isEmpty(mTestThisDevice.deviceAddress)) {
            assertEquals(ANONYMIZED_DEVICE_ADDRESS, device.deviceAddress);
        } else {
            assertEquals(mTestThisDevice.deviceAddress, device.deviceAddress);
        }
        assertEquals(mTestThisDevice.primaryDeviceType, device.primaryDeviceType);
        assertEquals(mTestThisDevice.secondaryDeviceType, device.secondaryDeviceType);
        assertEquals(mTestThisDevice.wpsConfigMethodsSupported, device.wpsConfigMethodsSupported);
        assertEquals(mTestThisDevice.deviceCapability, device.deviceCapability);
        assertEquals(mTestThisDevice.groupCapability, device.groupCapability);
        assertEquals(mTestThisDevice.status, device.status);
        if (mTestThisDevice.wfdInfo != null) {
            assertEquals(mTestThisDevice.wfdInfo.isEnabled(),
                    device.wfdInfo.isEnabled());
            assertEquals(mTestThisDevice.wfdInfo.getDeviceInfoHex(),
                    device.wfdInfo.getDeviceInfoHex());
            assertEquals(mTestThisDevice.wfdInfo.getControlPort(),
                    device.wfdInfo.getControlPort());
            assertEquals(mTestThisDevice.wfdInfo.getMaxThroughput(),
                    device.wfdInfo.getMaxThroughput());
        } else {
            assertEquals(mTestThisDevice.wfdInfo, device.wfdInfo);
        }
    }

    /**
     * Check the broadcast of WIFI_P2P_THIS_DEVICE_CHANGED_ACTION is sent as expected.
     */
    private void checkSendThisDeviceChangedBroadcast() throws RemoteException {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        String[] permission_gold;
        if (mWifiPermissionsUtil.isLocationModeEnabled()) {
            permission_gold = new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_WIFI_STATE};
        } else {
            permission_gold = new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_WIFI_STATE,
                    android.Manifest.permission.NETWORK_SETTINGS};
        }
        ArgumentCaptor<String []> permissionCaptor = ArgumentCaptor.forClass(String[].class);
        String [] permission;
        if (SdkLevel.isAtLeastU()) {
            verify(mContext, atLeastOnce()).sendBroadcast(intentCaptor.capture(), any(), any());
            verify(mBroadcastOptions, atLeastOnce()).setRequireAllOfPermissions(
                    permissionCaptor.capture());
            permission = permissionCaptor.getAllValues().get(0);
        } else {
            verify(mContext, atLeastOnce()).sendBroadcastWithMultiplePermissions(
                    intentCaptor.capture(), permissionCaptor.capture());
            permission = permissionCaptor.getValue();
        }
        Arrays.sort(permission);
        Arrays.sort(permission_gold);
        assertEquals(permission_gold, permission);
        verifyDeviceChangedBroadcastIntent(intentCaptor.getValue());
        if (SdkLevel.isAtLeastT()) {
            // verify the same broadcast is also sent to apps with NEARBY_WIFI_DEVICES permission
            // but without ACCESS_FINE_LOCATION.
            verify(mContext, atLeastOnce()).sendBroadcast(
                    intentCaptor.capture(), any(), any());
            verify(mBroadcastOptions, atLeastOnce())
                    .setRequireAllOfPermissions(TEST_REQUIRED_PERMISSIONS_T);
            if (mWifiPermissionsUtil.isLocationModeEnabled()) {
                verify(mBroadcastOptions, atLeastOnce())
                        .setRequireNoneOfPermissions(TEST_EXCLUDED_PERMISSIONS_T);
            }
            verifyDeviceChangedBroadcastIntent(intentCaptor.getValue());
        }
        if (SdkLevel.isAtLeastT()) {
            verify(mP2pListener, atLeastOnce()).onDeviceConfigurationChanged(any());
        }
    }

    /**
     * Check the broadcast of ACTION_WIFI_P2P_PERSISTENT_GROUPS_CHANGED is sent as expected.
     */
    private void checkSendP2pPersistentGroupsChangedBroadcast() {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, atLeastOnce()).sendStickyBroadcastAsUser(
                intentCaptor.capture(), eq(UserHandle.ALL));
        Intent intent = intentCaptor.getValue();
        assertEquals(WifiP2pManager.ACTION_WIFI_P2P_PERSISTENT_GROUPS_CHANGED, intent.getAction());
        assertEquals(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT, intent.getFlags());
    }

    private class P2pConnectionChangedIntentMatcherForNetworkState
            implements ArgumentMatcher<Intent> {
        private final NetworkInfo.DetailedState mState;
        P2pConnectionChangedIntentMatcherForNetworkState(NetworkInfo.DetailedState state) {
            mState = state;
        }
        @Override
        public boolean matches(Intent intent) {
            if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION != intent.getAction()) {
                return false;
            }
            if (mState == null) {
                return true;
            }
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            return networkInfo.getDetailedState() == mState;
        }
    }

    private class P2pStateChangedIntentMatcher implements ArgumentMatcher<Intent> {
        private final int mState;
        P2pStateChangedIntentMatcher(int state) {
            mState = state;
        }
        @Override
        public boolean matches(Intent intent) {
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION != intent.getAction()) {
                return false;
            }
            return mState == intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE,
                    WifiP2pManager.WIFI_P2P_STATE_DISABLED);
        }
    }

    /**
     * Set up the instance of WifiP2pServiceImpl for testing.
     *
     * @param supported defines the p2p is supported or not in this instance.
     */
    private void setUpWifiP2pServiceImpl(boolean supported) throws Exception {
        reset(mContext, mFrameworkFacade, mHandlerThread, mPackageManager, mResources,
                mWifiInjector, mWifiNative);

        generatorTestData();
        mClientHanderLooper = new TestLooper();
        mClientHandler = spy(new Handler(mClientHanderLooper.getLooper()));
        mClientMessenger =  new Messenger(mClientHandler);
        mLooper = new TestLooper();

        when(mContext.getSystemService(Context.ALARM_SERVICE))
                .thenReturn(mAlarmManager);
        when(mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .thenReturn(mLayoutInflater);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getSystemService(WifiManager.class)).thenReturn(mWifiManager);
        if (SdkLevel.isAtLeastS()) {
            when(mContext.getAttributionSource()).thenReturn(
                    new AttributionSource(1000, TEST_PACKAGE_NAME, null));
        }
        when(mContext.getOpPackageName()).thenReturn("android");
        when(mContext.getAttributionTag()).thenReturn("feature");
        when(mWifiManager.getConnectionInfo()).thenReturn(mWifiInfo);
        when(mWifiGlobals.isP2pMacRandomizationSupported()).thenReturn(false);
        when(mWifiSettingsConfigStore.get(eq(WIFI_P2P_DEVICE_ADDRESS))).thenReturn(thisDeviceMac);
        when(mWifiSettingsConfigStore.get(eq(WIFI_P2P_DEVICE_NAME))).thenReturn(thisDeviceName);
        when(mWifiSettingsConfigStore.get(eq(WIFI_P2P_PENDING_FACTORY_RESET))).thenReturn(false);
        when(mWifiSettingsConfigStore.get(eq(D2D_ALLOWED_WHEN_INFRA_STA_DISABLED)))
                .thenReturn(false);
        when(mHandlerThread.getLooper()).thenReturn(mLooper.getLooper());
        if (supported) {
            when(mPackageManager.hasSystemFeature(eq(PackageManager.FEATURE_WIFI_DIRECT)))
                    .thenReturn(true);
        } else {
            when(mPackageManager.hasSystemFeature(eq(PackageManager.FEATURE_WIFI_DIRECT)))
                    .thenReturn(false);
        }
        when(mResources.getString(R.string.config_wifi_p2p_device_type))
                .thenReturn("10-0050F204-5");
        when(mResources.getInteger(R.integer.config_p2pInvitationReceivedDialogTimeoutMs))
                .thenReturn(P2P_INVITATION_RECEIVED_TIMEOUT_MS);
        when(mResources.getInteger(R.integer.config_wifiP2pJoinRequestAuthorizingTimeoutMs))
                .thenReturn(P2P_PEER_AUTH_TIMEOUT_MS);
        when(mResources.getInteger(R.integer.config_wifiP2pExtListenPeriodMs))
                .thenReturn(P2P_EXT_LISTEN_PERIOD_MS);
        when(mResources.getInteger(R.integer.config_wifiP2pExtListenIntervalMs))
                .thenReturn(P2P_EXT_LISTEN_INTERVAL_MS);
        when(mResources.getBoolean(R.bool
                .config_p2pWaitForPeerInviteOnInviteStatusInfoUnavailable)).thenReturn(false);
        when(mResources.getBoolean(R.bool
                .config_wifiP2pGoIpAddressAllocationInEapolFrames)).thenReturn(false);
        when(mResources.getInteger(R.integer.config_wifiConfigurationWifiRunnerThresholdInMs))
                .thenReturn(4000);
        when(mResources.getConfiguration()).thenReturn(mConfiguration);
        when(mWifiInjector.getFrameworkFacade()).thenReturn(mFrameworkFacade);
        when(mWifiInjector.getUserManager()).thenReturn(mUserManager);
        when(mWifiInjector.getWifiP2pMetrics()).thenReturn(mWifiP2pMetrics);
        when(mWifiInjector.getWifiP2pMonitor()).thenReturn(mWifiMonitor);
        when(mWifiInjector.getWifiP2pNative()).thenReturn(mWifiNative);
        when(mWifiInjector.getWifiP2pServiceHandlerThread()).thenReturn(mHandlerThread);
        when(mWifiInjector.getWifiPermissionsUtil()).thenReturn(mWifiPermissionsUtil);
        when(mWifiInjector.getSettingsConfigStore()).thenReturn(mWifiSettingsConfigStore);
        when(mWifiInjector.getCoexManager()).thenReturn(mCoexManager);
        when(mWifiInjector.getWifiGlobals()).thenReturn(mWifiGlobals);
        when(mWifiInjector.makeBroadcastOptions()).thenReturn(mBroadcastOptions);
        when(mWifiInjector.getWifiDialogManager()).thenReturn(mWifiDialogManager);
        when(mWifiInjector.getLastCallerInfoManager()).thenReturn(mLastCallerInfoManager);
        when(mWifiInjector.getWifiP2pConnection()).thenReturn(mWifiP2pConnection);

        when(mWifiDialogManager.createP2pInvitationReceivedDialog(any(), anyBoolean(), any(),
                anyInt(), any(), any())).thenReturn(mDialogHandle);
        when(mWifiDialogManager.createP2pInvitationSentDialog(any(), any(), anyInt()))
                .thenReturn(mDialogHandle);
        when(mWifiInjector.getClock()).thenReturn(mClock);
        when(mWifiInjector.getInterfaceConflictManager()).thenReturn(mInterfaceConflictManager);
        // enable all permissions, disable specific permissions in tests
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.checkNetworkStackPermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.checkReadWifiCredentialPermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.checkCanAccessWifiDirect(anyString(), anyString(), anyInt(),
                anyBoolean())).thenReturn(true);
        when(mInterfaceConflictManager.manageInterfaceConflictForStateMachine(any(), any(), any(),
                any(), any(), eq(HalDeviceManager.HDM_CREATE_IFACE_P2P), any(), anyBoolean()))
                .thenReturn(InterfaceConflictManager.ICM_EXECUTE_COMMAND);
        // Mock target SDK to less than T by default to keep existing tests working.
        if (SdkLevel.isAtLeastT()) {
            when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                    .thenReturn(true);
            when(mWifiPermissionsUtil.isTargetSdkLessThan(any(),
                    eq(Build.VERSION_CODES.TIRAMISU), anyInt())).thenReturn(true);
        }
        when(mWifiNative.setupInterface(any(), any(), any())).thenReturn(IFACE_NAME_P2P);
        when(mWifiNative.p2pGetDeviceAddress()).thenReturn(thisDeviceMac);
        when(mUserManager.getUserRestrictions()).thenReturn(mBundle);
        when(mFrameworkFacade.makeAlertDialogBuilder(any())).thenReturn(mAlertDialogBuilder);
        when(mAlertDialogBuilder.setTitle(any())).thenReturn(mAlertDialogBuilder);
        when(mAlertDialogBuilder.setView(any())).thenReturn(mAlertDialogBuilder);
        when(mAlertDialogBuilder.setMessage(any())).thenReturn(mAlertDialogBuilder);
        when(mAlertDialogBuilder.setPositiveButton(any(), any())).thenReturn(mAlertDialogBuilder);
        when(mAlertDialogBuilder.setNegativeButton(any(), any())).thenReturn(mAlertDialogBuilder);
        when(mAlertDialogBuilder.setOnCancelListener(any())).thenReturn(mAlertDialogBuilder);
        when(mAlertDialogBuilder.setPositiveButton(any(), any())).thenReturn(mAlertDialogBuilder);
        when(mAlertDialogBuilder.create()).thenReturn(mAlertDialog);
        when(mAlertDialog.getWindow()).thenReturn(mock(Window.class));
        if (SdkLevel.isAtLeastT()) {
            when(mBundle.getBoolean(UserManager.DISALLOW_WIFI_DIRECT)).thenReturn(false);
        }
        doAnswer(new AnswerWithArguments() {
            public boolean answer(WifiP2pGroupList groups) {
                groups.clear();
                for (WifiP2pGroup group : mGroups.getGroupList()) {
                    groups.add(group);
                }
                return true;
            }
        }).when(mWifiNative).p2pListNetworks(any(WifiP2pGroupList.class));
        doAnswer(new AnswerWithArguments() {
            public boolean answer(int netId) {
                mGroups.remove(netId);
                return true;
            }
        }).when(mWifiNative).removeP2pNetwork(anyInt());
        when(mWifiNative.setVendorElements(any())).thenReturn(true);
        when(mWifiSettingsConfigStore.get(eq(WIFI_VERBOSE_LOGGING_ENABLED))).thenReturn(true);

        doAnswer(new AnswerWithArguments() {
            public void answer(CoexManager.CoexListener listener) {
                mCoexListener = listener;
            }
        }).when(mCoexManager).registerCoexListener(any(CoexManager.CoexListener.class));
        when(mCoexManager.getCoexRestrictions()).thenReturn(0);
        when(mCoexManager.getCoexUnsafeChannels()).thenReturn(Collections.emptyList());
        when(mWifiInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mDeviceConfigFacade.getFeatureFlags()).thenReturn(mFeatureFlags);
        when(mWifiInjector.getWifiDiagnostics()).thenReturn(mWifiDiagnostics);
        when(mWifiInjector.getWifiHandlerLocalLog()).thenReturn(mLocalLog);
        when(mDeviceConfigFacade.isP2pFailureBugreportEnabled()).thenReturn(false);
        when(mContext.getSystemService(TetheringManager.class)).thenReturn(mTetheringManager);
        when(mP2pListener.asBinder()).thenReturn(mock(IBinder.class));

        mWifiP2pServiceImpl = new WifiP2pServiceImpl(mContext, mWifiInjector);
        if (supported) {
            // register these event:
            // * WifiManager.WIFI_STATE_CHANGED_ACTION        -- always
            // * LocationManager.MODE_CHANGED_ACTION          -- always
            // * TetheringManager.ACTION_TETHER_STATE_CHANGED -- <  S
            // * UserManager.ACTION_USER_RESTRICTIONS_CHANGED -- >= T
            if (SdkLevel.isAtLeastT()) {
                verify(mContext, times(3)).registerReceiver(mBcastRxCaptor.capture(),
                        any(IntentFilter.class));
                mUserRestrictionReceiver = mBcastRxCaptor.getAllValues().get(2);
            } else if (SdkLevel.isAtLeastS()) {
                verify(mContext, times(2)).registerReceiver(mBcastRxCaptor.capture(),
                        any(IntentFilter.class));
            }  else {
                verify(mContext, times(3)).registerReceiver(mBcastRxCaptor.capture(),
                        any(IntentFilter.class));
                mTetherStateReceiver = mBcastRxCaptor.getAllValues().get(2);
            }
            mWifiStateChangedReceiver = mBcastRxCaptor.getAllValues().get(0);
            mLocationModeReceiver = mBcastRxCaptor.getAllValues().get(1);
            verify(mWifiSettingsConfigStore).registerChangeListener(
                    eq(D2D_ALLOWED_WHEN_INFRA_STA_DISABLED),
                    mD2DAllowedSettingsCallbackCaptor.capture(), any());
        }

        verify(mWifiPermissionsUtil, never()).isLocationModeEnabled();
        verify(mContext, never()).sendBroadcastMultiplePermissions(any(Intent.class), any());
        verify(mContext, never()).sendBroadcast(any(Intent.class), anyString(), any(Bundle.class));
        mWifiP2pServiceImpl.mNetdWrapper = mNetdWrapper;
        mP2pStateMachineMessenger = mWifiP2pServiceImpl.getP2pStateMachineMessenger();
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession = mockitoSession()
                .mockStatic(NetworkInterface.class)
                .mockStatic(Process.class)
                .startMocking();
        lenient().when(NetworkInterface.getByName(eq(IFACE_NAME_P2P)))
                .thenReturn(mP2pNetworkInterface);
        when(mLayoutInflater.cloneInContext(any())).thenReturn(mLayoutInflater);
        when(mLayoutInflater.inflate(anyInt(), any())).thenReturn(mView);
        when(mLayoutInflater.inflate(anyInt(), any(), anyBoolean())).thenReturn(mView);
        when(mView.findViewById(eq(R.id.name))).thenReturn(mock(TextView.class));
        when(mView.findViewById(eq(R.id.value))).thenReturn(mock(TextView.class));
        when(mView.findViewById(eq(R.id.info))).thenReturn(mock(ViewGroup.class));
        ArrayList<InetAddress> p2pInetAddresses = new ArrayList<>();
        p2pInetAddresses.add(InetAddresses.parseNumericAddress(P2P_GO_IP));
        when(mP2pNetworkInterface.getInetAddresses())
                .thenReturn(Collections.enumeration(p2pInetAddresses));

        setUpWifiP2pServiceImpl(true);
        mClient1 = new Binder();
        mClient2 = new Binder();
        when(mContext.createContextAsUser(any(), anyInt())).thenReturn(mContext);
        mWifiP2pServiceImpl.handleBootCompleted();
        if (SdkLevel.isAtLeastT()) {
            mWifiP2pServiceImpl.registerWifiP2pListener(mP2pListener, TEST_PACKAGE_NAME, mExtras);
        }
    }

    @After
    public void cleanUp() throws Exception {
        mStaticMockSession.finishMocking();
    }

    /**
     * Send P2P statemachine internal message.
     */
    private void sendP2pStateMachineMessage(int what) throws Exception {
        Message msg = Message.obtain();
        msg.what = what;
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();
    }

    /**
     * Mock enter Disabled state.
     */
    private void mockEnterDisabledState() throws Exception {
        sendP2pStateMachineMessage(WifiP2pMonitor.SUP_DISCONNECTION_EVENT);
    }

    /**
     * Mock enter GroupNegotiation state.
     */
    private void mockEnterGroupNegotiationState() throws Exception {
        sendCreateGroupMsg(mClientMessenger, WifiP2pGroup.NETWORK_ID_TEMPORARY, null);
    }


    /**
     * Mock enter ProvisionDiscovery state.
     */
    private void mockEnterProvisionDiscoveryState() throws Exception {
        mockPeersList();
        sendConnectMsg(mClientMessenger, mTestWifiP2pPeerConfig);
    }

    private void mockEnterProvisionDiscoveryState(WifiP2pConfig p2pConfig) throws Exception {
        mockPeersList();
        sendConnectMsg(mClientMessenger, p2pConfig);
    }

    /**
     * Mock enter Group created state.
     */
    private void mockEnterGroupCreatedState() throws Exception {
        forceP2pEnabled(mClient1);
        WifiP2pGroup group = new WifiP2pGroup();
        group.setNetworkId(WifiP2pGroup.NETWORK_ID_PERSISTENT);
        group.setNetworkName(TEST_NETWORK_NAME);
        group.setOwner(new WifiP2pDevice("thisDeviceMac"));
        group.setIsGroupOwner(true);
        group.setInterface(IFACE_NAME_P2P);
        sendGroupStartedMsg(group);
        simulateTetherReady();
    }

    /**
     * Mock enter the user authorizing negotiation request state.
     */
    private void mockEnterUserAuthorizingNegotiationRequestState(int wpsType) throws Exception {
        mockPeersList();

        // Enter UserAuthorizingNegotiationRequestState
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = mTestWifiP2pDevice.deviceAddress;
        config.wps = new WpsInfo();
        config.wps.setup = wpsType;

        sendNegotiationRequestEvent(config);
    }

    /** Mock enter the user authorizing invite request state. */
    private void mockEnterUserAuthorizingInviteRequestState() throws Exception {
        mockPeersList();
        // Enter UserAuthorizingNegotiationRequestState
        sendInvitationReceivedMsg(mTestWifiP2pGroup);
    }

    /**
     * Mock WifiP2pServiceImpl.mPeers.
     */
    private void mockPeersList() throws Exception {
        sendDeviceFoundEventMsg(mTestWifiP2pDevice);
    }

    /**
     * Verify that p2p init / teardown whn a client connects / disconnects
     * with wifi enabled
     */
    @Test
    public void testP2pInitWhenClientConnectWithWifiEnabled() throws Exception {
        simulateWifiStateChange(true);
        checkIsP2pInitWhenClientConnected(true, mClient1,
                new WorkSource(mClient1.getCallingUid(), TEST_PACKAGE_NAME));
        checkIsP2pTearDownWhenClientDisconnected(true, mClient1, null);
    }

    /**
     * Verify that p2p doesn't init when  a client connects / disconnects
     * with wifi disabled
     */
    @Test
    public void testP2pDoesntInitWhenClientConnectWithWifiDisabled()
            throws Exception {
        simulateWifiStateChange(false);
        checkIsP2pInitWhenClientConnected(false, mClient1,
                new WorkSource(mClient1.getCallingUid(), TEST_PACKAGE_NAME));
        checkIsP2pTearDownWhenClientDisconnected(false, mClient1, null);
    }

    /**
     * Verify device status is reset to UNAVAILABLE on P2P disabled.
     */
    @Test
    public void testP2pDeviceUnavailableOnP2pDisabled() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);

        simulateWifiStateChange(false);
        mLooper.dispatchAll();
        // Device status is AVAILABLE
        sendChannelInfoUpdateMsg("testPkg1", "testFeature", mClient1, mClientMessenger);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DEVICE_INFO);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_DEVICE_INFO, mMessageCaptor.getValue().what);
        assertEquals(WifiP2pDevice.AVAILABLE,
                ((WifiP2pDevice) mMessageCaptor.getValue().obj).status);

        // Force to back disable state
        mockEnterDisabledState();

        // Device status is UNAVAILABLE
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DEVICE_INFO);
        verify(mClientHandler, times(2)).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_DEVICE_INFO, mMessageCaptor.getValue().what);
        assertEquals(WifiP2pDevice.UNAVAILABLE,
                ((WifiP2pDevice) mMessageCaptor.getValue().obj).status);
    }

    /**
     * Verify that p2p init / teardown when wifi off / on
     * with a client connected
     */
    @Test
    public void checkIsP2pInitForWifiChanges() throws Exception {
        forceP2pEnabled(mClient1);

        simulateWifiStateChange(false);
        mLooper.dispatchAll();
        verify(mWifiNative).teardownInterface();
        verify(mWifiMonitor).stopMonitoring(anyString());
        // Force to back disable state for next test
        mockEnterDisabledState();

        // wifi off / on won't initialize the p2p interface.
        simulateWifiStateChange(true);
        mLooper.dispatchAll();
        verify(mWifiNative, times(1)).setupInterface(any(), any(), any());
        verify(mNetdWrapper, times(1)).setInterfaceUp(anyString());
        verify(mWifiMonitor, atLeastOnce()).registerHandler(anyString(), anyInt(), any());

        // Lazy initialization is done once receiving a command.
        sendSimpleMsg(mClientMessenger, WifiP2pManager.DISCOVER_PEERS);
        verify(mWifiNative, times(2)).setupInterface(any(), any(), any());
        verify(mNetdWrapper, times(2)).setInterfaceUp(anyString());
    }

    /**
     * Verify that p2p will teardown /won't init when DISALLOW_WIFI_DIRECT user restriction is set
     */
    @Test
    public void checkIsP2pInitForUserRestrictionChanges() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        forceP2pEnabled(mClient1);

        // p2p interface disabled when user restriction is set
        when(mBundle.getBoolean(UserManager.DISALLOW_WIFI_DIRECT)).thenReturn(true);
        Intent intent = new Intent(UserManager.ACTION_USER_RESTRICTIONS_CHANGED);
        mUserRestrictionReceiver.onReceive(mContext, intent);
        mLooper.dispatchAll();
        verify(mWifiNative).teardownInterface();
        verify(mWifiMonitor).stopMonitoring(anyString());
        // Force to back disable state for next test
        mockEnterDisabledState();

        // p2p interface won't initialize when user restriction is set
        sendSimpleMsg(mClientMessenger, WifiP2pManager.DISCOVER_PEERS);
        verify(mWifiNative, times(1)).setupInterface(any(), any(), any());
        verify(mNetdWrapper, times(1)).setInterfaceUp(anyString());
    }

    /**
     * Verify WIFI_P2P_STATE_CHANGED_ACTION broadcast sent when p2p availability changes
     */
    @Test
    public void checkP2pStateChangedBroadcast() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        InOrder inOrder = inOrder(mContext);
        forceP2pEnabled(mClient1);
        inOrder.verify(mContext).sendStickyBroadcastAsUser(argThat(
                new WifiP2pServiceImplTest.P2pStateChangedIntentMatcher(
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED)), any());
        if (SdkLevel.isAtLeastT()) {
            verify(mP2pListener).onStateChanged(eq(true));
        }

        // disabled broadcast sent when user restriction is set
        simulateUserRestrictionChange(true);
        mLooper.dispatchAll();
        inOrder.verify(mContext).sendStickyBroadcastAsUser(argThat(
                new WifiP2pServiceImplTest.P2pStateChangedIntentMatcher(
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED)), any());
        if (SdkLevel.isAtLeastT()) {
            verify(mP2pListener).onStateChanged(eq(false));
        }

        // no disabled broadcast sent when Wi-Fi is disabled since broadcast already sent
        simulateWifiStateChange(false);
        mLooper.dispatchAll();
        inOrder.verify(mContext, never()).sendStickyBroadcastAsUser(argThat(
                new WifiP2pServiceImplTest.P2pStateChangedIntentMatcher(
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED)), any());

        // no enabled broadcast sent when user restriction is removed since Wi-Fi is still disabled
        simulateUserRestrictionChange(false);
        mLooper.dispatchAll();
        inOrder.verify(mContext, never()).sendStickyBroadcastAsUser(argThat(
                new WifiP2pServiceImplTest.P2pStateChangedIntentMatcher(
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED)), any());

        // enabled broadcast sent when Wi-Fi is enabled
        simulateWifiStateChange(true);
        mLooper.dispatchAll();
        inOrder.verify(mContext).sendStickyBroadcastAsUser(argThat(
                new WifiP2pServiceImplTest.P2pStateChangedIntentMatcher(
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED)), any());
    }

    /**
     * Verify p2p init / teardown when two clients connect / disconnect
     */
    @Test
    public void checkIsP2pInitForTwoClientsConnection() throws Exception {
        forceP2pEnabled(mClient1);
        WorkSource expectedRequestorWs =
                new WorkSource(mClient1.getCallingUid(), TEST_PACKAGE_NAME);
        reset(mWifiNative);
        reset(mNetdWrapper);
        reset(mWifiMonitor);
        // P2pInit check count should keep in once, same as one client connected case.
        checkIsP2pInitWhenClientConnected(false, mClient2, expectedRequestorWs);
        checkIsP2pTearDownWhenClientDisconnected(false, mClient2,
                new WorkSource(mClient1.getCallingUid(), TEST_PACKAGE_NAME));
        checkIsP2pTearDownWhenClientDisconnected(true, mClient1, null);
    }

    /**
     * Verify WifiP2pManager.ADD_LOCAL_SERVICE_FAILED is returned when a caller
     * uses abnormal way to send WifiP2pManager.ADD_LOCAL_SERVICE (i.e no channel info updated).
     */
    @Test
    public void testAddLocalServiceFailureWhenNoChannelUpdated() throws Exception {
        forceP2pEnabledWithoutClientInfo();
        sendAddLocalServiceMsg(mClientMessenger);
        verify(mWifiNative, never()).p2pServiceAdd(any());
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.ADD_LOCAL_SERVICE_FAILED));
    }

    /**
     * Verify WifiP2pManager.ADD_LOCAL_SERVICE_FAILED is returned when a caller
     * uses wrong package name to initialize a channel.
     */
    @Test
    public void testAddLocalServiceFailureWhenChannelUpdateWrongPkgName() throws Exception {
        forceP2pEnabledWithoutClientInfo();
        doThrow(new SecurityException("P2p unit test"))
                .when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        sendChannelInfoUpdateMsg("wrongpkg", "testFeature", mClient1, mClientMessenger);
        sendAddLocalServiceMsg(mClientMessenger);
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.ADD_LOCAL_SERVICE_FAILED));
        verify(mWifiNative, never()).p2pServiceAdd(any());
    }

    /**
     * Verify WifiP2pManager.ADD_LOCAL_SERVICE_FAILED is returned when a caller
     * without proper permission attmepts to send WifiP2pManager.ADD_LOCAL_SERVICE.
     */
    @Test
    public void testAddLocalServiceFailureWhenCallerPermissionDenied() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        doNothing().when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        if (SdkLevel.isAtLeastT()) {
            when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                    .thenReturn(false);
        } else {
            when(mWifiPermissionsUtil.checkCanAccessWifiDirect(any(), any(), anyInt(),
                    anyBoolean())).thenReturn(false);
        }
        sendAddLocalServiceMsg(mClientMessenger);
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.ADD_LOCAL_SERVICE_FAILED));
        verify(mWifiNative, never()).p2pServiceAdd(any());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }
    }

    private void verifyAddLocalService() throws Exception {
        doNothing().when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        when(mWifiNative.p2pServiceAdd(any())).thenReturn(true);
        sendAddLocalServiceMsg(mClientMessenger);
        verify(mWifiNative).p2pServiceAdd(any());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.ADD_LOCAL_SERVICE_SUCCEEDED));
    }

    /**
     * Verify the caller with proper permission sends WifiP2pManager.ADD_LOCAL_SERVICE.
     */
    @Test
    public void testAddLocalServiceSuccess() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        verifyAddLocalService();
    }

    /**
     * Verify WifiP2pManager.ADD_LOCAL_SERVICE_FAILED is returned when native call failure.
     */
    @Test
    public void testAddLocalServiceFailureWhenNativeCallFailure() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        doNothing().when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        when(mWifiNative.p2pServiceAdd(any())).thenReturn(false);
        sendAddLocalServiceMsg(mClientMessenger);
        verify(mWifiNative).p2pServiceAdd(any());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.ADD_LOCAL_SERVICE_FAILED));
    }

    /**
     * Verify WifiP2pManager.CONNECT_FAILED is returned when a caller
     * uses abnormal way to send WifiP2pManager.CONNECT (i.e no channel info updated).
     */
    @Test
    public void testConnectWithConfigValidAsGroupFailureWhenNoChannelUpdated() throws Exception {
        forceP2pEnabledWithoutClientInfo();

        sendConnectMsgWithConfigValidAsGroup(mClientMessenger);
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.CONNECT_FAILED));
        verify(mWifiNative, never()).p2pGroupAdd(any(), anyBoolean());
    }

    /**
     * Verify WifiP2pManager.CONNECT_FAILED is returned when a caller
     * uses wrong package name to initialize a channel.
     */
    @Test
    public void testConnectWithConfigValidAsGroupFailureWhenChannelUpdateWrongPkgName()
            throws Exception {
        forceP2pEnabledWithoutClientInfo();
        doThrow(new SecurityException("P2p unit test"))
                .when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        sendChannelInfoUpdateMsg("wrongpkg", "testFeature", mClient1, mClientMessenger);
        sendConnectMsgWithConfigValidAsGroup(mClientMessenger);
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.CONNECT_FAILED));
        verify(mWifiNative, never()).p2pGroupAdd(any(), anyBoolean());
    }

    /**
     * Verify WifiP2pManager.CONNECT_FAILED is returned when a caller
     * without proper permission attmepts to send WifiP2pManager.CONNECT.
     */
    @Test
    public void testConnectWithConfigValidAsGroupFailureWhenPermissionDenied() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        doNothing().when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        if (SdkLevel.isAtLeastT()) {
            when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                    .thenReturn(false);
        } else {
            when(mWifiPermissionsUtil.checkCanAccessWifiDirect(any(), any(), anyInt(),
                    anyBoolean())).thenReturn(false);
        }
        sendConnectMsgWithConfigValidAsGroup(mClientMessenger);
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.CONNECT_FAILED));
        verify(mWifiNative, never()).p2pGroupAdd(any(), anyBoolean());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(false));
        }
    }

    /**
     * Verify the caller with proper permission sends WifiP2pManager.CONNECT.
     */
    @Test
    public void testConnectWithConfigValidAsGroupSuccess() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(any(), eq(true))).thenReturn(true);
        sendConnectMsgWithConfigValidAsGroup(mClientMessenger);
        verify(mWifiNative).p2pGroupAdd(any(), eq(true));
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(false));
        }
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.CONNECT_SUCCEEDED));
    }

    /**
     * Verify WifiP2pManager.CONNECT_FAILED is returned when native call failure.
     */
    @Test
    public void testConnectWithConfigValidAsGroupFailureWhenNativeCallFailure() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(any(), eq(true))).thenReturn(false);
        sendConnectMsgWithConfigValidAsGroup(mClientMessenger);
        verify(mWifiNative).p2pGroupAdd(any(), eq(true));
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(false));
        }
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.CONNECT_FAILED));
    }

    /**
     * Verify WifiP2pManager.CREATE_GROUP_FAILED is returned when a caller
     * uses abnormal way to send WifiP2pManager.CREATE_GROUP (i.e no channel info updated).
     */
    @Test
    public void testCreateGroupWithConfigValidAsGroupFailureWhenNoChannelUpdated()
            throws Exception {
        forceP2pEnabledWithoutClientInfo();

        sendCreateGroupMsgWithConfigValidAsGroup(mClientMessenger);
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.CREATE_GROUP_FAILED));
        verify(mWifiNative, never()).p2pGroupAdd(anyBoolean());
        verify(mWifiNative, never()).p2pGroupAdd(any(), anyBoolean());
    }

    /**
     * Verify WifiP2pManager.CREATE_GROUP_FAILED is returned with null object when a caller
     * uses wrong package name to initialize a channel.
     */
    @Test
    public void testCreateGroupWithConfigValidAsGroupFailureWhenChannelUpdateWrongPkgName()
            throws Exception {
        forceP2pEnabledWithoutClientInfo();
        doThrow(new SecurityException("P2p unit test"))
                .when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        sendChannelInfoUpdateMsg("wrongpkg", "testFeature", mClient1, mClientMessenger);
        sendCreateGroupMsgWithConfigValidAsGroup(mClientMessenger);
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.CREATE_GROUP_FAILED));
        verify(mWifiNative, never()).p2pGroupAdd(anyBoolean());
        verify(mWifiNative, never()).p2pGroupAdd(any(), anyBoolean());
    }

    /**
     * Verify WifiP2pManager.CREATE_GROUP_FAILED is returned when a caller
     * without proper permission attmepts to send WifiP2pManager.CREATE_GROUP.
     */
    @Test
    public void testCreateGroupWithConfigValidAsGroupFailureWhenPermissionDenied()
            throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        doNothing().when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        if (SdkLevel.isAtLeastT()) {
            when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                    .thenReturn(false);
        } else {
            when(mWifiPermissionsUtil.checkCanAccessWifiDirect(any(), any(), anyInt(),
                    anyBoolean())).thenReturn(false);
        }
        sendCreateGroupMsgWithConfigValidAsGroup(mClientMessenger);
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.CREATE_GROUP_FAILED));
        verify(mWifiNative, never()).p2pGroupAdd(anyBoolean());
        verify(mWifiNative, never()).p2pGroupAdd(any(), anyBoolean());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(false));
        }
    }

    /**
     * Verify the caller with proper permission sends WifiP2pManager.CREATE_GROUP.
     */
    @Test
    public void testCreateGroupWithConfigValidAsGroupSuccess() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(any(), eq(false))).thenReturn(true);
        sendCreateGroupMsgWithConfigValidAsGroup(mClientMessenger);
        verify(mWifiNative).p2pGroupAdd(any(), eq(false));
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.CREATE_GROUP_SUCCEEDED));
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
            verify(mContext).sendBroadcastWithMultiplePermissions(
                    argThat(new WifiP2pServiceImplTest
                           .P2pConnectionChangedIntentMatcherForNetworkState(CONNECTING)), any());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
            verify(mContext, never()).sendBroadcastWithMultiplePermissions(
                    argThat(new WifiP2pServiceImplTest
                           .P2pConnectionChangedIntentMatcherForNetworkState(CONNECTING)), any());
        }
    }

    /**
     * Verify WifiP2pManager.CREATE_GROUP_FAILED is returned when native call failure.
     */
    @Test
    public void testCreateGroupWithConfigValidAsGroupFailureWhenNativeCallFailure()
            throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(any(), eq(false))).thenReturn(false);
        sendCreateGroupMsgWithConfigValidAsGroup(mClientMessenger);
        verify(mWifiNative).p2pGroupAdd(any(), eq(false));
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(false));
        }
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.CREATE_GROUP_FAILED));
        verify(mWifiP2pMetrics).endConnectionEvent(eq(P2pConnectionEvent.CLF_CREATE_GROUP_FAILED));
    }

    /**
     * Verify WifiP2pManager.DISCOVER_PEERS_FAILED is returned when a caller
     * uses abnormal way to send WifiP2pManager.REQUEST_PEERS (i.e no channel info updated).
     */
    @Test
    public void testDiscoverPeersFailureWhenNoChannelUpdated() throws Exception {
        forceP2pEnabledWithoutClientInfo();

        sendDiscoverPeersMsg(mClientMessenger);
        verify(mWifiNative, never()).p2pFind(anyInt());
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.DISCOVER_PEERS_FAILED));
    }

    /**
     * Verify WifiP2pManager.DISCOVER_PEERS_FAILED is returned when a caller
     * uses wrong package name to initialize a channel.
     */
    @Test
    public void testDiscoverPeersFailureWhenChannelUpdateWrongPkgName() throws Exception {
        forceP2pEnabledWithoutClientInfo();
        doThrow(new SecurityException("P2p unit test"))
                .when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        sendChannelInfoUpdateMsg("wrongpkg", "testFeature", mClient1, mClientMessenger);
        sendDiscoverPeersMsg(mClientMessenger);
        verify(mWifiNative, never()).p2pFind(anyInt());
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.DISCOVER_PEERS_FAILED));
    }

    /**
     * Verify WifiP2pManager.DISCOVER_PEERS_FAILED is returned with null object when a caller
     * without proper permission attmepts to send WifiP2pManager.DISCOVER_PEERS.
     */
    @Test
    public void testDiscoverPeersFailureWhenPermissionDenied() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        doNothing().when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        if (SdkLevel.isAtLeastT()) {
            when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                    .thenReturn(false);
        } else {
            when(mWifiPermissionsUtil.checkCanAccessWifiDirect(any(), any(), anyInt(),
                    anyBoolean())).thenReturn(false);
        }
        sendDiscoverPeersMsg(mClientMessenger);
        verify(mWifiNative, never()).p2pFind(anyInt());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.DISCOVER_PEERS_FAILED));
    }

    /**
     * Verify WifiP2pManager.DISCOVER_PEERS_FAILED is returned with null object when a caller
     * attmepts to send WifiP2pManager.DISCOVER_PEERS and location mode is disabled.
     */
    @Test
    public void testDiscoverPeersFailureWhenLocationModeDisabled() throws Exception {
        forceP2pEnabled(mClient1);
        simulateLocationModeChange(false);
        doNothing().when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        when(mWifiPermissionsUtil.checkCanAccessWifiDirect(anyString(), anyString(), anyInt(),
                eq(false))).thenReturn(true);
        when(mWifiPermissionsUtil.checkCanAccessWifiDirect(anyString(), anyString(), anyInt(),
                eq(true))).thenReturn(false);
        sendDiscoverPeersMsg(mClientMessenger);
        verify(mWifiNative, never()).p2pFind(anyInt());
        verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                eq("testFeature"), anyInt(), eq(true));
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.DISCOVER_PEERS_FAILED));
    }

    /**
     * Verify the caller with proper permission sends WifiP2pManager.DISCOVER_PEERS
     * with scan type, WIFI_P2P_SCAN_FULL.
     */
    @Test
    public void testDiscoverPeersFullSuccess() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pFind(anyInt())).thenReturn(true);
        sendDiscoverPeersMsg(mClientMessenger);
        verify(mWifiNative).p2pFind(anyInt());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.DISCOVER_PEERS_SUCCEEDED));
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_P2P_DISCOVER_PEERS), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
        if (SdkLevel.isAtLeastT()) {
            verify(mP2pListener).onDiscoveryStateChanged(eq(true));
        }
    }

    /**
     * Verify the caller with proper permission sends WifiP2pManager.DISCOVER_PEERS
     * with scan type, WIFI_P2P_SCAN_SOCIAL.
     */
    @Test
    public void testDiscoverPeersSocialSuccess() throws Exception {
        setTargetSdkGreaterThanT();
        when(mWifiNative.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_FLEXIBLE_DISCOVERY);
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pFind(anyInt(), anyInt(), anyInt())).thenReturn(true);
        sendDiscoverPeersMsg(
                mClientMessenger, WifiP2pManager.WIFI_P2P_SCAN_SOCIAL,
                WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED);
        verify(mWifiNative).p2pFind(
                eq(WifiP2pManager.WIFI_P2P_SCAN_SOCIAL),
                eq(WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED), anyInt());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.DISCOVER_PEERS_SUCCEEDED));
    }

    /**
     * Verify the caller with proper permission sends WifiP2pManager.DISCOVER_PEERS
     * with scan type, WIFI_P2P_SCAN_SINGLE_FREQ.
     */
    @Test
    public void testDiscoverPeersSpecificFrequencySuccess() throws Exception {
        setTargetSdkGreaterThanT();
        when(mWifiNative.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_FLEXIBLE_DISCOVERY);
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pFind(anyInt(), anyInt(), anyInt())).thenReturn(true);
        int freq = 2412;
        sendDiscoverPeersMsg(
                mClientMessenger, WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ, freq);
        verify(mWifiNative).p2pFind(
                eq(WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ), eq(freq), anyInt());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.DISCOVER_PEERS_SUCCEEDED));
    }

    /**
     * Verify WifiP2pManager.DISCOVER_PEERS_FAILED is returned when native call failure.
     */
    @Test
    public void testDiscoverPeersFailureWhenNativeCallFailure() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pFind(anyInt())).thenReturn(false);
        sendDiscoverPeersMsg(mClientMessenger);
        verify(mWifiNative).p2pFind(anyInt());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.DISCOVER_PEERS_FAILED));
    }


    /**
     * Verify WifiP2pManager.DISCOVER_SERVICES_FAILED is returned when a caller
     * uses abnormal way to send WifiP2pManager.DISCOVER_SERVICES (i.e no channel info updated).
     */
    @Test
    public void testDiscoverServicesFailureWhenNoChannelUpdated() throws Exception {
        when(mWifiNative.p2pServDiscReq(anyString(), anyString())).thenReturn("mServiceDiscReqId");

        forceP2pEnabledWithoutClientInfo();

        sendAddServiceRequestMsg(mClientMessenger);
        sendDiscoverServiceMsg(mClientMessenger);
        verify(mWifiNative, never()).p2pServDiscReq(anyString(), anyString());
        verify(mWifiNative, never()).p2pFind(anyInt());
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.DISCOVER_SERVICES_FAILED));
    }

    /**
     * Verify WifiP2pManager.DISCOVER_SERVICES_FAILED is returned when a caller
     * uses wrong package name to initialize a channel.
     */
    @Test
    public void testDiscoverServicesFailureWhenChannelUpdateWrongPkgName() throws Exception {
        when(mWifiNative.p2pServDiscReq(anyString(), anyString())).thenReturn("mServiceDiscReqId");
        forceP2pEnabledWithoutClientInfo();
        doThrow(new SecurityException("P2p unit test"))
                .when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        sendChannelInfoUpdateMsg("wrongpkg", "testFeature", mClient1, mClientMessenger);
        sendAddServiceRequestMsg(mClientMessenger);
        sendDiscoverServiceMsg(mClientMessenger);
        verify(mWifiNative, never()).p2pServDiscReq(anyString(), anyString());
        verify(mWifiNative, never()).p2pFind(anyInt());
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.DISCOVER_SERVICES_FAILED));
    }

    /**
     * Verify WifiP2pManager.DISCOVER_SERVICES_FAILED is returned when a caller
     * without proper permission attmepts to send WifiP2pManager.DISCOVER_SERVICES.
     */
    @Test
    public void testDiscoverServicesFailureWhenPermissionDenied() throws Exception {
        setTargetSdkGreaterThanT();
        when(mWifiNative.p2pServDiscReq(anyString(), anyString()))
                .thenReturn("mServiceDiscReqId");
        forceP2pEnabled(mClient1);
        doNothing().when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        if (SdkLevel.isAtLeastT()) {
            when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                    .thenReturn(false);
        } else {
            when(mWifiPermissionsUtil.checkCanAccessWifiDirect(any(), any(), anyInt(),
                    anyBoolean())).thenReturn(false);
        }
        sendAddServiceRequestMsg(mClientMessenger);
        sendDiscoverServiceMsg(mClientMessenger);
        verify(mWifiNative, never()).p2pServDiscReq(anyString(), anyString());
        verify(mWifiNative, never()).p2pFind(anyInt(), anyInt(), anyInt());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.DISCOVER_SERVICES_FAILED));
    }

    /**
     * Verify WifiP2pManager.DISCOVER_SERVICES_FAILED is returned when a caller
     * attmepts to send WifiP2pManager.DISCOVER_SERVICES and location mode is disabled.
     */
    @Test
    public void testDiscoverServicesFailureWhenLocationModeDisabled() throws Exception {
        when(mWifiNative.p2pServDiscReq(anyString(), anyString()))
                .thenReturn("mServiceDiscReqId");
        forceP2pEnabled(mClient1);
        simulateLocationModeChange(false);
        doNothing().when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        when(mWifiPermissionsUtil.checkCanAccessWifiDirect(anyString(), anyString(), anyInt(),
                eq(false))).thenReturn(true);
        when(mWifiPermissionsUtil.checkCanAccessWifiDirect(anyString(), anyString(), anyInt(),
                eq(true))).thenReturn(false);
        sendAddServiceRequestMsg(mClientMessenger);
        sendDiscoverServiceMsg(mClientMessenger);
        verify(mWifiNative, never()).p2pServDiscReq(anyString(), anyString());
        verify(mWifiNative, never()).p2pFind(anyInt(), anyInt(), anyInt());
        verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                eq("testFeature"), anyInt(), eq(true));
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.DISCOVER_SERVICES_FAILED));
    }

    /**
     * Verify the caller with proper permission sends WifiP2pManager.DISCOVER_SERVICES.
     */
    @Test
    public void testDiscoverServicesSuccess() throws Exception {
        setTargetSdkGreaterThanT();
        when(mWifiNative.p2pServDiscReq(anyString(), anyString()))
                .thenReturn("mServiceDiscReqId");
        when(mWifiNative.p2pFind(anyInt())).thenReturn(true);
        forceP2pEnabled(mClient1);
        sendAddServiceRequestMsg(mClientMessenger);
        sendDiscoverServiceMsg(mClientMessenger);
        verify(mWifiNative).p2pServDiscReq(anyString(), anyString());
        verify(mWifiNative, atLeastOnce()).p2pFind(anyInt());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.DISCOVER_SERVICES_SUCCEEDED));
    }

    /**
     * Verify WifiP2pManager.DISCOVER_SERVICES_FAILED is returned when add service failure.
     */
    @Test
    public void testDiscoverServicesFailureWhenAddServiceRequestFailure() throws Exception {
        setTargetSdkGreaterThanT();
        when(mWifiNative.p2pServDiscReq(anyString(), anyString())).thenReturn(null);
        forceP2pEnabled(mClient1);
        sendAddServiceRequestMsg(mClientMessenger);
        sendDiscoverServiceMsg(mClientMessenger);
        verify(mWifiNative).p2pServDiscReq(anyString(), anyString());
        verify(mWifiNative, never()).p2pFind(anyInt(), anyInt(), anyInt());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.DISCOVER_SERVICES_FAILED));
    }

    /**
     * Verify WifiP2pManager.DISCOVER_SERVICES_FAILED is returned when native call failure.
     */
    @Test
    public void testDiscoverServicesFailureWhenNativeCallFailure() throws Exception {
        setTargetSdkGreaterThanT();
        when(mWifiNative.p2pServDiscReq(anyString(), anyString()))
                .thenReturn("mServiceDiscReqId");
        when(mWifiNative.p2pFind(anyInt())).thenReturn(false);
        forceP2pEnabled(mClient1);
        sendAddServiceRequestMsg(mClientMessenger);
        sendDiscoverServiceMsg(mClientMessenger);
        verify(mWifiNative).p2pServDiscReq(anyString(), anyString());
        verify(mWifiNative).p2pFind(anyInt());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.DISCOVER_SERVICES_FAILED));
    }

    /**
     * Verify WifiP2pManager.RESPONSE_PEERS is returned with null object when a caller
     * uses abnormal way to send WifiP2pManager.REQUEST_PEERS (i.e no channel info updated).
     */
    @Test
    public void testRequestPeersFailureWhenNoChannelUpdated() throws Exception {
        forceP2pEnabledWithoutClientInfo();

        mockPeersList();
        sendRequestPeersMsg(mClientMessenger);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        WifiP2pDeviceList peers = (WifiP2pDeviceList) mMessageCaptor.getValue().obj;
        assertEquals(WifiP2pManager.RESPONSE_PEERS, mMessageCaptor.getValue().what);
        assertNull(peers.get(mTestWifiP2pDevice.deviceAddress));

    }

    /**
     * Verify WifiP2pManager.RESPONSE_PEERS is returned with null object when a caller
     * uses wrong package name to initialize a channel.
     */
    @Test
    public void testRequestPeersFailureWhenChannelUpdateWrongPkgName() throws Exception {
        forceP2pEnabledWithoutClientInfo();
        mockPeersList();
        doThrow(new SecurityException("P2p unit test"))
                .when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        sendChannelInfoUpdateMsg("wrongpkg", "testFeature", mClient1, mClientMessenger);
        sendRequestPeersMsg(mClientMessenger);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        WifiP2pDeviceList peers = (WifiP2pDeviceList) mMessageCaptor.getValue().obj;
        assertEquals(WifiP2pManager.RESPONSE_PEERS, mMessageCaptor.getValue().what);
        assertNull(peers.get(mTestWifiP2pDevice.deviceAddress));
    }

    /**
     * Verify WifiP2pManager.RESPONSE_PEERS is returned with null object when a caller
     * without proper permission attmepts to send WifiP2pManager.REQUEST_PEERS.
     */
    @Test
    public void testRequestPeersFailureWhenPermissionDenied() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        mockPeersList();
        doNothing().when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        if (SdkLevel.isAtLeastT()) {
            when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                    .thenReturn(false);
        } else {
            when(mWifiPermissionsUtil.checkCanAccessWifiDirect(any(), any(), anyInt(),
                    anyBoolean())).thenReturn(false);
        }
        sendRequestPeersMsg(mClientMessenger);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
        WifiP2pDeviceList peers = (WifiP2pDeviceList) mMessageCaptor.getValue().obj;
        assertEquals(WifiP2pManager.RESPONSE_PEERS, mMessageCaptor.getValue().what);
        assertNull(peers.get(mTestWifiP2pDevice.deviceAddress));

    }

    /**
     * Verify WifiP2pManager.RESPONSE_PEERS is returned with null object when a caller
     * attmepts to send WifiP2pManager.REQUEST_PEERS and location mode is disabled.
     */
    @Test
    public void testRequestPeersFailureWhenLocationModeDisabled() throws Exception {
        forceP2pEnabled(mClient1);
        simulateLocationModeChange(false);
        mockPeersList();
        doNothing().when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        when(mWifiPermissionsUtil.checkCanAccessWifiDirect(anyString(), anyString(), anyInt(),
                eq(false))).thenReturn(true);
        when(mWifiPermissionsUtil.checkCanAccessWifiDirect(anyString(), anyString(), anyInt(),
                eq(true))).thenReturn(false);
        sendRequestPeersMsg(mClientMessenger);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                eq("testFeature"), anyInt(), eq(true));
        WifiP2pDeviceList peers = (WifiP2pDeviceList) mMessageCaptor.getValue().obj;
        assertEquals(WifiP2pManager.RESPONSE_PEERS, mMessageCaptor.getValue().what);
        assertNull(peers.get(mTestWifiP2pDevice.deviceAddress));

    }

    /**
     * Verify WifiP2pManager.RESPONSE_PEERS is returned with expect object when a caller
     * with proper permission to send WifiP2pManager.REQUEST_PEERS.
     */
    @Test
    public void testRequestPeersSuccess() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        mockPeersList();
        sendRequestPeersMsg(mClientMessenger);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
        WifiP2pDeviceList peers = (WifiP2pDeviceList) mMessageCaptor.getValue().obj;
        assertEquals(WifiP2pManager.RESPONSE_PEERS, mMessageCaptor.getValue().what);
        assertNotEquals(null, peers.get(mTestWifiP2pDevice.deviceAddress));
    }

    /**
     * Verify WifiP2pManager.RESPONSE_GROUP_INFO is returned with null object when a caller
     * uses abnormal way to send WifiP2pManager.REQUEST_GROUP_INFO (i.e no channel info updated).
     */
    @Test
    public void testRequestGroupInfoFailureWhenNoChannelUpdated() throws Exception {
        forceP2pEnabledWithoutClientInfo();
        reset(mClientHandler);

        sendGroupStartedMsg(mTestWifiP2pGroup);
        sendRequestGroupInfoMsg(mClientMessenger);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_GROUP_INFO, mMessageCaptor.getValue().what);
        assertNull(mMessageCaptor.getValue().obj);
    }

    /**
     * Verify WifiP2pManager.RESPONSE_GROUP_INFO is returned with null object when a caller
     * uses wrong package name to initialize a channel.
     */
    @Test
    public void testRequestGroupInfoFailureWhenChannelUpdateWrongPkgName() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiPermissionsUtil.isTargetSdkLessThan(any(), anyInt(), anyInt()))
                .thenReturn(false);
        forceP2pEnabled(mClient1);

        sendGroupStartedMsg(mTestWifiP2pGroup);
        simulateTetherReady();
        doThrow(new SecurityException("P2p unit test"))
                .when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());

        sendRequestGroupInfoMsg(mClientMessenger);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_GROUP_INFO, mMessageCaptor.getValue().what);
        assertNull(mMessageCaptor.getValue().obj);
    }

    /**
     * Verify WifiP2pManager.RESPONSE_GROUP_INFO is returned with null object when a caller
     * without proper permission attempts to send WifiP2pManager.REQUEST_GROUP_INFO.
     */
    @Test
    public void testRequestGroupInfoFailureWhenPermissionDenied() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        sendGroupStartedMsg(mTestWifiP2pGroup);
        simulateTetherReady();
        doNothing().when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        if (SdkLevel.isAtLeastT()) {
            when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                    .thenReturn(false);
        } else {
            when(mWifiPermissionsUtil.checkCanAccessWifiDirect(any(), any(), anyInt(),
                    anyBoolean())).thenReturn(false);
        }
        sendRequestGroupInfoMsg(mClientMessenger);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(false));
        }
        assertEquals(WifiP2pManager.RESPONSE_GROUP_INFO, mMessageCaptor.getValue().what);
        assertNull(mMessageCaptor.getValue().obj);
    }

    /**
     * Verify WifiP2pManager.RESPONSE_GROUP_INFO is returned with expect object when a caller
     * with proper permission.
     */
    @Test
    public void testRequestGroupInfoSuccess() throws Exception {
        setTargetSdkGreaterThanT();
        mTestWifiP2pGroup.setOwner(mTestThisDevice);
        forceP2pEnabled(mClient1);
        sendGroupStartedMsg(mTestWifiP2pGroup);
        simulateTetherReady();
        when(mWifiPermissionsUtil.checkLocalMacAddressPermission(anyInt())).thenReturn(false);
        sendRequestGroupInfoMsg(mClientMessenger);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(false));
        }
        assertEquals(WifiP2pManager.RESPONSE_GROUP_INFO, mMessageCaptor.getValue().what);
        WifiP2pGroup wifiP2pGroup = (WifiP2pGroup) mMessageCaptor.getValue().obj;
        assertEquals(mTestWifiP2pGroup.getNetworkName(), wifiP2pGroup.getNetworkName());
        // Ensure that our own MAC address is anonymized if we're the group owner.
        assertEquals(ANONYMIZED_DEVICE_ADDRESS, wifiP2pGroup.getOwner().deviceAddress);
    }

    /**
     * Verify WifiP2pManager.RESPONSE_GROUP_INFO does not anonymize this device's MAC address when
     * requested by an app with the LOCAL_MAC_ADDRESS permission.
     */
    @Test
    public void testRequestGroupInfoIncludesMacForNetworkSettingsApp() throws Exception {
        setTargetSdkGreaterThanT();
        mTestWifiP2pGroup.setOwner(mTestThisDevice);
        forceP2pEnabled(mClient1);
        sendGroupStartedMsg(mTestWifiP2pGroup);
        simulateTetherReady();
        when(mWifiPermissionsUtil.checkLocalMacAddressPermission(anyInt())).thenReturn(true);
        sendRequestGroupInfoMsg(mClientMessenger);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(false));
        }
        assertEquals(WifiP2pManager.RESPONSE_GROUP_INFO, mMessageCaptor.getValue().what);
        WifiP2pGroup wifiP2pGroup = (WifiP2pGroup) mMessageCaptor.getValue().obj;
        assertEquals(thisDeviceMac, wifiP2pGroup.getOwner().deviceAddress);
    }

    /**
     * Verify WifiP2pManager.START_LISTEN_FAILED is returned when a caller
     * without proper permission attempts to send WifiP2pManager.START_LISTEN.
     */
    @Test
    public void testStartListenFailureWhenPermissionDenied() throws Exception {
        when(mWifiPermissionsUtil.checkCanAccessWifiDirect(anyString(), anyString(), anyInt(),
                anyBoolean())).thenReturn(false);
        forceP2pEnabled(mClient1);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.START_LISTEN);
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.START_LISTEN_FAILED));
        // p2pFlush should be invoked once in forceP2pEnabled.
        verify(mWifiNative).p2pFlush();
        verify(mWifiNative, never()).p2pStopFind();
        verify(mWifiNative, never()).p2pExtListen(anyBoolean(), anyInt(), anyInt(), eq(null));
    }

    /**
     * Verify WifiP2pManager.START_LISTEN_FAILED is returned when native call failure.
     */
    @Test
    public void testStartListenFailureWhenNativeCallFailure() throws Exception {
        setTargetSdkGreaterThanT();
        when(mWifiNative.p2pExtListen(eq(true), anyInt(), anyInt(), any())).thenReturn(false);
        forceP2pEnabled(mClient1);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.START_LISTEN);
        // p2pFlush should be invoked once in forceP2pEnabled.
        verify(mWifiNative).p2pFlush();
        verify(mWifiNative).p2pStopFind();
        verify(mWifiNative).p2pExtListen(eq(true), eq(P2P_EXT_LISTEN_PERIOD_MS),
                eq(P2P_EXT_LISTEN_INTERVAL_MS), eq(null));
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.START_LISTEN_FAILED));
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
    }

    /**
     * Verify the caller with proper permission sends WifiP2pManager.START_LISTEN.
     */
    @Test
    public void testStartListenSuccess() throws Exception {
        setTargetSdkGreaterThanT();
        when(mWifiNative.p2pExtListen(eq(true), anyInt(), anyInt(), any())).thenReturn(true);
        forceP2pEnabled(mClient1);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.START_LISTEN);
        // p2pFlush should be invoked once in forceP2pEnabled.
        verify(mWifiNative).p2pFlush();
        verify(mWifiNative).p2pStopFind();
        verify(mWifiNative).p2pExtListen(eq(true), eq(P2P_EXT_LISTEN_PERIOD_MS),
                eq(P2P_EXT_LISTEN_INTERVAL_MS), eq(null));
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.START_LISTEN_SUCCEEDED));
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
        if (SdkLevel.isAtLeastT()) {
            verify(mP2pListener).onListenStateChanged(eq(true));
        }
    }

    /**
     * Verify the caller with proper permission sends WifiP2pManager.START_LISTEN.
     */
    @Test
    public void testStartListenSuccessWithGroup() throws Exception {
        setTargetSdkGreaterThanT();
        when(mWifiNative.p2pExtListen(eq(true), anyInt(), anyInt(), any())).thenReturn(true);
        mockEnterGroupCreatedState();
        sendSimpleMsg(mClientMessenger, WifiP2pManager.START_LISTEN);
        // p2pFlush should be invoked once in forceP2pEnabled.
        verify(mWifiNative).p2pFlush();
        verify(mWifiNative, times(2)).p2pStopFind();
        verify(mWifiNative).p2pExtListen(eq(true), eq(P2P_EXT_LISTEN_PERIOD_MS),
                eq(P2P_EXT_LISTEN_INTERVAL_MS), eq(null));
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.START_LISTEN_SUCCEEDED));
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_P2P_START_LISTENING), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /**
     * Verify that a caller with proper permission can send WifiP2pManager.START_LISTEN
     * with additional parameters.
     */
    @Test
    public void testStartListenSuccessWithExtListenParams() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        setTargetSdkGreaterThanT();
        when(mWifiNative.p2pExtListen(eq(true), anyInt(), anyInt(), any())).thenReturn(true);
        forceP2pEnabled(mClient1);

        OuiKeyedData vendorDataElement =
                new OuiKeyedData.Builder(0x00aabbcc, new PersistableBundle()).build();
        List<OuiKeyedData> vendorData = Arrays.asList(vendorDataElement);
        WifiP2pExtListenParams extListenParams =
                new WifiP2pExtListenParams.Builder().setVendorData(vendorData).build();

        Message msg = Message.obtain();
        msg.what = WifiP2pManager.START_LISTEN;
        msg.arg1 = WifiP2pManager.WIFI_P2P_EXT_LISTEN_WITH_PARAMS;
        msg.replyTo = mClientMessenger;
        msg.obj = new AttributionSource(1000, TEST_PACKAGE_NAME, null);

        Bundle extras = new Bundle();
        extras.putParcelable(WifiP2pManager.EXTRA_PARAM_KEY_EXT_LISTEN_PARAMS, extListenParams);
        msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, extras);
        mP2pStateMachineMessenger.send(Message.obtain(msg));
        mLooper.dispatchAll();

        // p2pFlush should be invoked once during forceP2pEnabled.
        verify(mWifiNative).p2pFlush();
        verify(mWifiNative).p2pStopFind();
        verify(mWifiNative).p2pExtListen(eq(true), eq(P2P_EXT_LISTEN_PERIOD_MS),
                eq(P2P_EXT_LISTEN_INTERVAL_MS), any(WifiP2pExtListenParams.class));
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.START_LISTEN_SUCCEEDED));
        verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                any(), eq(true), any());
        verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                any(), any(), anyInt(), anyBoolean());
    }

    @Test
    public void testStartListenAndSetChannelFailureWithDefaultStateHandling() throws Exception {
        setTargetSdkGreaterThanT();
        when(mInterfaceConflictManager.manageInterfaceConflictForStateMachine(any(), any(), any(),
                any(), any(), eq(HalDeviceManager.HDM_CREATE_IFACE_P2P), any(), anyBoolean()))
                .thenReturn(InterfaceConflictManager.ICM_ABORT_COMMAND);

        mockEnterDisabledState();
        sendSimpleMsg(mClientMessenger, WifiP2pManager.START_LISTEN);
        verify(mWifiNative, never()).setupInterface(any(), any(), any());
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.START_LISTEN_FAILED));

        mockEnterDisabledState();
        sendSimpleMsg(mClientMessenger, WifiP2pManager.SET_CHANNEL);
        verify(mWifiNative, never()).setupInterface(any(), any(), any());
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.SET_CHANNEL_FAILED));
    }

    /**
     * Verify WifiP2pManager.STOP_LISTEN_FAILED is returned when native call failure.
     */
    @Test
    public void testStopListenFailureWhenNativeCallFailure() throws Exception {
        when(mWifiNative.p2pExtListen(eq(false), anyInt(), anyInt(), any())).thenReturn(false);
        forceP2pEnabled(mClient1);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.STOP_LISTEN);
        verify(mWifiNative).p2pStopFind();
        verify(mWifiNative).p2pExtListen(eq(false), anyInt(), anyInt(), eq(null));
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.STOP_LISTEN_FAILED));
    }

    /**
     * Verify the caller with proper permission sends WifiP2pManager.STOP_LISTEN.
     */
    @Test
    public void testStopListenSuccess() throws Exception {
        when(mWifiNative.p2pExtListen(eq(false), anyInt(), anyInt(), any())).thenReturn(true);
        forceP2pEnabled(mClient1);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.STOP_LISTEN);
        verify(mWifiNative).p2pStopFind();
        verify(mWifiNative).p2pExtListen(eq(false), anyInt(), anyInt(), eq(null));
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.STOP_LISTEN_SUCCEEDED));
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_P2P_STOP_LISTENING), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /** Verify the p2p randomized MAC feature is enabled if OEM supports it. */
    @Test
    public void testP2pRandomMacWithOemSupport() throws Exception {
        when(mWifiGlobals.isP2pMacRandomizationSupported()).thenReturn(true);
        forceP2pEnabled(mClient1);
        verify(mWifiNative, never()).setMacRandomization(eq(false));
        verify(mWifiNative).setMacRandomization(eq(true));
    }

    /** Verify the p2p randomized MAC feature is disabled if OEM does not support it. */
    @Test
    public void testP2pRandomMacWithoutOemSupport() throws Exception {
        when(mWifiGlobals.isP2pMacRandomizationSupported()).thenReturn(false);
        forceP2pEnabled(mClient1);
        verify(mWifiNative, never()).setMacRandomization(eq(true));
        verify(mWifiNative).setMacRandomization(eq(false));
    }

    /**
     * Verify the caller sends WifiP2pManager.DELETE_PERSISTENT_GROUP.
     */
    @Test
    public void testDeletePersistentGroupSuccess() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);

        sendDeletePersistentGroupMsg(mClientMessenger, WifiP2pGroup.NETWORK_ID_PERSISTENT);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.DELETE_PERSISTENT_GROUP_SUCCEEDED, message.what);
    }

    /**
     * Verify that respond with DELETE_PERSISTENT_GROUP_FAILED
     * when caller sends DELETE_PERSISTENT_GROUP and p2p is disabled.
     */
    @Test
    public void testDeletePersistentGroupFailureWhenP2pDisabled() throws Exception {
        sendDeletePersistentGroupMsg(mClientMessenger, WifiP2pGroup.NETWORK_ID_PERSISTENT);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.DELETE_PERSISTENT_GROUP_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     * Verify that respond with DELETE_PERSISTENT_GROUP_FAILED
     * when caller sends DELETE_PERSISTENT_GROUP and p2p is unsupported.
     */
    @Test
    public void testDeletePersistentGroupFailureWhenP2pUnsupported() throws Exception {
        setUpWifiP2pServiceImpl(false);
        sendDeletePersistentGroupMsg(mClientMessenger, WifiP2pGroup.NETWORK_ID_PERSISTENT);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.DELETE_PERSISTENT_GROUP_FAILED, message.what);
        assertEquals(WifiP2pManager.P2P_UNSUPPORTED, message.arg1);
    }

    /**
     * Verify that respond with DELETE_PERSISTENT_GROUP_FAILED
     * when caller sends DELETE_PERSISTENT_GROUP and doesn't have the necessary permissions.
     */
    @Test
    public void testDeletePersistentGroupFailureWhenNoPermissions() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);

        // no permissions held
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkStackPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(false);

        sendDeletePersistentGroupMsg(mClientMessenger, WifiP2pGroup.NETWORK_ID_PERSISTENT);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.DELETE_PERSISTENT_GROUP_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify the peer scan counter is increased while receiving WifiP2pManager.DISCOVER_PEERS at
     * P2pEnabledState.
     */
    @Test
    public void testPeerScanMetricWhenSendDiscoverPeers() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pFind(anyInt())).thenReturn(true);
        sendDiscoverPeersMsg(mClientMessenger);
        verify(mWifiP2pMetrics).incrementPeerScans();
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
    }

    /**
     * Verify the service scan counter is increased while receiving
     * WifiP2pManager.DISCOVER_SERVICES at P2pEnabledState.
     */
    @Test
    public void testServiceScanMetricWhenSendDiscoverServices() throws Exception {
        setTargetSdkGreaterThanT();
        when(mWifiNative.p2pServDiscReq(anyString(), anyString()))
                .thenReturn("mServiceDiscReqId");
        when(mWifiNative.p2pFind(anyInt())).thenReturn(true);
        forceP2pEnabled(mClient1);
        sendAddServiceRequestMsg(mClientMessenger);
        sendDiscoverServiceMsg(mClientMessenger);
        verify(mWifiP2pMetrics).incrementServiceScans();
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(true));
        }
    }

    /**
     * Verify the persistent group counter is updated while receiving
     * WifiP2pManager.FACTORY_RESET.
     */
    @Test
    public void testPersistentGroupMetricWhenSendFactoryReset() throws Exception {
        forceP2pEnabled(mClient1);

        // permissions for factory reset
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt()))
                .thenReturn(true);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_NETWORK_RESET), any()))
                .thenReturn(false);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_CONFIG_WIFI), any()))
                .thenReturn(false);

        ArgumentCaptor<WifiP2pGroupList> groupsCaptor =
                ArgumentCaptor.forClass(WifiP2pGroupList.class);
        verify(mWifiP2pMetrics).updatePersistentGroup(groupsCaptor.capture());
        assertEquals(3, groupsCaptor.getValue().getGroupList().size());

        sendSimpleMsg(mClientMessenger, WifiP2pManager.FACTORY_RESET);

        verify(mWifiP2pMetrics, times(2)).updatePersistentGroup(groupsCaptor.capture());
        // the captured object is the same object, just get the latest one is ok.
        assertEquals(0, groupsCaptor.getValue().getGroupList().size());
    }

    /**
     * Verify the persistent group counter is updated while receiving
     * WifiP2pMonitor.P2P_GROUP_STARTED_EVENT.
     */
    @Test
    public void testPersistentGroupMetricWhenSendP2pGroupStartedEvent() throws Exception {
        forceP2pEnabled(mClient1);

        ArgumentCaptor<WifiP2pGroupList> groupsCaptor =
                ArgumentCaptor.forClass(WifiP2pGroupList.class);
        verify(mWifiP2pMetrics).updatePersistentGroup(groupsCaptor.capture());
        assertEquals(3, groupsCaptor.getValue().getGroupList().size());

        sendGroupStartedMsg(mTestWifiP2pNewPersistentGoGroup);
        simulateTetherReady();

        verify(mWifiP2pMetrics, times(2)).updatePersistentGroup(groupsCaptor.capture());
        // the captured object is the same object, just get the latest one is ok.
        assertEquals(4, groupsCaptor.getValue().getGroupList().size());
    }

    /**
     * Verify the persistent group counter is updated while receiving
     * WifiP2pManager.DELETE_PERSISTENT_GROUP.
     */
    @Test
    public void testPersistentGroupMetricWhenSendDeletePersistentGroup() throws Exception {
        forceP2pEnabled(mClient1);

        ArgumentCaptor<WifiP2pGroupList> groupsCaptor =
                ArgumentCaptor.forClass(WifiP2pGroupList.class);
        verify(mWifiP2pMetrics).updatePersistentGroup(groupsCaptor.capture());
        assertEquals(3, groupsCaptor.getValue().getGroupList().size());

        sendDeletePersistentGroupMsg(mClientMessenger, 0);

        verify(mWifiP2pMetrics, times(2)).updatePersistentGroup(groupsCaptor.capture());
        // the captured object is the same object, just get the latest one is ok.
        assertEquals(2, groupsCaptor.getValue().getGroupList().size());
    }

    /**
     * Verify the group event.
     */
    @Test
    public void testGroupEventMetric() throws Exception {
        forceP2pEnabled(mClient1);

        sendGroupStartedMsg(mTestWifiP2pNewPersistentGoGroup);
        simulateTetherReady();

        ArgumentCaptor<WifiP2pGroup> groupCaptor =
                ArgumentCaptor.forClass(WifiP2pGroup.class);
        verify(mWifiP2pMetrics).startGroupEvent(groupCaptor.capture());
        WifiP2pGroup groupCaptured = groupCaptor.getValue();
        assertEquals(mTestWifiP2pNewPersistentGoGroup.toString(), groupCaptured.toString());

        sendGroupRemovedMsg();
        verify(mWifiP2pMetrics).endGroupEvent();
    }

    /**
     * Verify the connection event for a fresh connection.
     */
    @Test
    public void testStartFreshConnectionEventWhenSendConnect() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);

        mockPeersList();
        sendConnectMsg(mClientMessenger, mTestWifiP2pPeerConfig);
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(false));
        }

        ArgumentCaptor<WifiP2pConfig> configCaptor =
                ArgumentCaptor.forClass(WifiP2pConfig.class);
        verify(mWifiP2pMetrics).startConnectionEvent(
                eq(P2pConnectionEvent.CONNECTION_FRESH),
                configCaptor.capture(),
                eq(WifiMetricsProto.GroupEvent.GROUP_UNKNOWN),
                eq(mClient1.getCallingUid()));
        assertEquals(mTestWifiP2pPeerConfig.toString(), configCaptor.getValue().toString());
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_P2P_CONNECT), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /**
     * Verify the connection event for a reinvoked connection.
     */
    @Test
    public void testStartReinvokeConnectionEventWhenSendConnect() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(anyInt()))
                .thenReturn(true);
        when(mTestWifiP2pDevice.isGroupOwner()).thenReturn(true);
        when(mWifiNative.p2pGetSsid(eq(mTestWifiP2pDevice.deviceAddress)))
                .thenReturn(mTestWifiP2pGroup.getNetworkName());

        mockPeersList();
        sendConnectMsg(mClientMessenger, mTestWifiP2pPeerConfig);
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(false));
        }

        ArgumentCaptor<WifiP2pConfig> configCaptor =
                ArgumentCaptor.forClass(WifiP2pConfig.class);
        verify(mWifiP2pMetrics).startConnectionEvent(
                eq(P2pConnectionEvent.CONNECTION_REINVOKE),
                configCaptor.capture(),
                eq(WifiMetricsProto.GroupEvent.GROUP_UNKNOWN),
                eq(mClient1.getCallingUid()));
        assertEquals(mTestWifiP2pPeerConfig.toString(), configCaptor.getValue().toString());
    }

    /**
     * Verify the connection event for a reinvoked connection via
     * createGroup API.
     *
     * If there is a persistent group whose owner is this deivce, this would be
     * a reinvoked group.
     */
    @Test
    public void testStartReinvokeConnectionEventWhenCreateGroup()
            throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);

        sendCreateGroupMsg(mClientMessenger, WifiP2pGroup.NETWORK_ID_PERSISTENT, null);
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }

        verify(mWifiP2pMetrics).startConnectionEvent(
                eq(P2pConnectionEvent.CONNECTION_REINVOKE),
                eq(null),
                eq(WifiMetricsProto.GroupEvent.GROUP_OWNER),
                eq(mClient1.getCallingUid()));
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_P2P_CREATE_GROUP), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /**
     * Verify the connection event for a local connection while setting
     * netId to {@link WifiP2pGroup#NETWORK_ID_PERSISTENT}.
     */
    @Test
    public void testStartLocalConnectionWhenCreateGroup() throws Exception {
        mGroups.clear();
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);

        sendCreateGroupMsg(mClientMessenger, WifiP2pGroup.NETWORK_ID_PERSISTENT, null);
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }

        verify(mWifiP2pMetrics).startConnectionEvent(
                eq(P2pConnectionEvent.CONNECTION_LOCAL),
                eq(null),
                eq(WifiMetricsProto.GroupEvent.GROUP_OWNER),
                eq(mClient1.getCallingUid()));
    }

    /**
     * Verify the connection event for a local connection while setting the
     * netId to {@link WifiP2pGroup#NETWORK_ID_TEMPORARY}.
     */
    @Test
    public void testStartLocalConnectionEventWhenCreateTemporaryGroup() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);

        sendCreateGroupMsg(mClientMessenger, WifiP2pGroup.NETWORK_ID_TEMPORARY, null);
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }

        verify(mWifiP2pMetrics).startConnectionEvent(
                eq(P2pConnectionEvent.CONNECTION_LOCAL),
                eq(null),
                eq(WifiMetricsProto.GroupEvent.GROUP_OWNER),
                eq(mClient1.getCallingUid()));
    }

    /**
     * Verify the connection event for a fast connection via
     * connect with config.
     */
    @Test
    public void testStartFastConnectionEventWhenSendConnectWithConfig()
            throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(any(), eq(true))).thenReturn(true);

        sendConnectMsg(mClientMessenger, mTestWifiP2pFastConnectionConfig);
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(false));
        }

        ArgumentCaptor<WifiP2pConfig> configCaptor =
                ArgumentCaptor.forClass(WifiP2pConfig.class);
        verify(mWifiP2pMetrics).startConnectionEvent(
                eq(P2pConnectionEvent.CONNECTION_FAST),
                configCaptor.capture(),
                eq(WifiMetricsProto.GroupEvent.GROUP_CLIENT),
                eq(mClient1.getCallingUid()));
        assertEquals(mTestWifiP2pFastConnectionConfig.toString(),
                configCaptor.getValue().toString());
    }

    @Test
    public void testStartFastConnectionEventWhenSendConnectWithConfigAndP2pClose()
            throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(any(), eq(true))).thenReturn(true);

        sendConnectMsg(mClientMessenger, mTestWifiP2pFastConnectionConfig);
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(false));
        }

        ArgumentCaptor<WifiP2pConfig> configCaptor =
                ArgumentCaptor.forClass(WifiP2pConfig.class);
        verify(mWifiP2pMetrics).startConnectionEvent(
                eq(P2pConnectionEvent.CONNECTION_FAST),
                configCaptor.capture(),
                eq(WifiMetricsProto.GroupEvent.GROUP_CLIENT),
                eq(mClient1.getCallingUid()));
        assertEquals(mTestWifiP2pFastConnectionConfig.toString(),
                configCaptor.getValue().toString());

        mWifiP2pServiceImpl.close(mClient1);
        mLooper.dispatchAll();
        verify(mWifiP2pMetrics).endConnectionEvent(eq(P2pConnectionEvent.CLF_GROUP_REMOVED));
    }

    /**
     * Verify the connection event for a fast connection via
     * createGroup API with config.
     */
    @Test
    public void testStartFastConnectionEventWhenCreateGroupWithConfigTwice()
            throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mDeviceConfigFacade.isP2pFailureBugreportEnabled()).thenReturn(true);
        sendCreateGroupMsg(mClientMessenger, 0, mTestWifiP2pFastConnectionConfig);
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }

        ArgumentCaptor<WifiP2pConfig> configCaptor =
                ArgumentCaptor.forClass(WifiP2pConfig.class);
        verify(mWifiP2pMetrics).startConnectionEvent(
                eq(P2pConnectionEvent.CONNECTION_FAST),
                configCaptor.capture(),
                eq(WifiMetricsProto.GroupEvent.GROUP_OWNER),
                eq(mClient1.getCallingUid()));
        assertEquals(mTestWifiP2pFastConnectionConfig.toString(),
                configCaptor.getValue().toString());
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_P2P_CREATE_GROUP_P2P_CONFIG),
                anyInt(), anyInt(), anyInt(), anyString(), eq(true));
        verify(mWifiDiagnostics, never()).takeBugReport(anyString(), anyString());

        when(mWifiP2pMetrics.hasOngoingConnection()).thenReturn(true);
        sendCreateGroupMsg(mClientMessenger, 0, mTestWifiP2pFastConnectionConfig);

        verify(mWifiDiagnostics).takeBugReport(anyString(), anyString());
    }

    /**
     * Verify the connection event ends while the group is formed.
     */
    @Test
    public void testEndConnectionEventWhenGroupFormed() throws Exception {
        forceP2pEnabled(mClient1);

        WifiP2pGroup group = new WifiP2pGroup();
        group.setNetworkId(WifiP2pGroup.NETWORK_ID_PERSISTENT);
        group.setNetworkName(TEST_NETWORK_NAME);
        group.setOwner(new WifiP2pDevice("thisDeviceMac"));
        group.setIsGroupOwner(true);
        group.setInterface(IFACE_NAME_P2P);
        sendGroupStartedMsg(group);
        simulateTetherReady();
        verify(mWifiP2pMetrics).endConnectionEvent(
                eq(P2pConnectionEvent.CLF_NONE));
    }

    @Test
    public void testOnClientsChangedOnApStaConnection() throws Exception {
        when(mTetheredClient.getTetheringType()).thenReturn(TetheringManager.TETHERING_WIFI_P2P);
        when(mTetheredClient.getAddresses()).thenReturn(mAddresses);
        when(mAddresses.size()).thenReturn(1);
        when(mAddresses.get(0)).thenReturn(mAddressInfo);
        when(mTetheredClient.getMacAddress())
                .thenReturn(MacAddress.fromString(PEER_INTERFACE_ADDRESS));
        when(mAddressInfo.getAddress()).thenReturn(new LinkAddress(P2P_PEER_IP + "/" + "24"));

        forceP2pEnabled(mClient1);

        // Update the P2P peer list.
        WifiP2pDeviceList peers;
        WifiP2pDevice dev;
        mockPeersList();
        sendRequestPeersMsg(mClientMessenger);
        verify(mClientHandler, times(1)).sendMessage(mMessageCaptor.capture());

        // Start the P2P GO.
        WifiP2pGroup group = new WifiP2pGroup();
        group.setNetworkId(WifiP2pGroup.NETWORK_ID_PERSISTENT);
        group.setNetworkName("DIRECT-xy-NEW");
        group.setOwner(new WifiP2pDevice("thisDeviceMac"));
        group.setIsGroupOwner(true);
        group.setInterface(IFACE_NAME_P2P);
        sendGroupStartedMsg(group);
        simulateTetherReady();

        // Trigger AP_STA_CONNECTED event & validate the results.
        WifiP2pDevice connectedClientDevice = new WifiP2pDevice(mTestWifiP2pDevice);
        connectedClientDevice.setInterfaceMacAddress(MacAddress.fromString(PEER_INTERFACE_ADDRESS));
        sendApStaConnectedEvent(connectedClientDevice);
        simulateOnClientsChanged();
        // Verify that the connected client is added to the p2p group client list.
        sendRequestGroupInfoMsg(mClientMessenger);
        verify(mClientHandler, times(2)).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_GROUP_INFO, mMessageCaptor.getValue().what);
        WifiP2pGroup wifiP2pGroup = (WifiP2pGroup) mMessageCaptor.getValue().obj;
        assertEquals(1, wifiP2pGroup.getClientList().size());
        WifiP2pDevice client = wifiP2pGroup.getClientList().iterator().next();
        assertNotNull(client);
        assertEquals(mTestWifiP2pDevice.deviceAddress, client.deviceAddress);
        assertEquals(MacAddress.fromString(PEER_INTERFACE_ADDRESS),
                client.getInterfaceMacAddress());
        if (SdkLevel.isAtLeastV()) {
            assertEquals(InetAddresses.parseNumericAddress(P2P_PEER_IP), client.getIpAddress());
        }

        // Verify that the interface MAC address and IP address is not updated in the P2P peer list.
        sendRequestPeersMsg(mClientMessenger);
        verify(mClientHandler, times(3)).sendMessage(mMessageCaptor.capture());
        peers = (WifiP2pDeviceList) mMessageCaptor.getValue().obj;
        assertEquals(WifiP2pManager.RESPONSE_PEERS, mMessageCaptor.getValue().what);
        dev = peers.get(mTestWifiP2pDevice.deviceAddress);
        assertNotNull(dev);
        assertNull(dev.getInterfaceMacAddress());
        if (SdkLevel.isAtLeastV()) {
            assertNull(dev.getIpAddress());
        }

    }

    @Test
    public void testApStaConnectedDisconnectedEventWithEapolIPAddress() throws Exception {
        forceP2pEnabled(mClient1);

        WifiP2pDeviceList peers;
        WifiP2pDevice dev;
        WifiP2pGroup wifiP2pGroup;

        // Update the P2P Peer list.
        mockPeersList();
        sendRequestPeersMsg(mClientMessenger);
        verify(mClientHandler, times(1)).sendMessage(mMessageCaptor.capture());

        // Start the P2P GO.
        WifiP2pGroup group = new WifiP2pGroup();
        group.setNetworkId(WifiP2pGroup.NETWORK_ID_PERSISTENT);
        group.setNetworkName("DIRECT-xy-NEW");
        group.setOwner(new WifiP2pDevice("thisDeviceMac"));
        group.setIsGroupOwner(true);
        group.setInterface(IFACE_NAME_P2P);
        sendGroupStartedMsg(group);
        simulateTetherReady();

        // Trigger AP_STA_CONNECTED event & validate the results.
        WifiP2pDevice connectedClientDevice = new WifiP2pDevice(mTestWifiP2pDevice);
        connectedClientDevice.setInterfaceMacAddress(MacAddress.fromString(PEER_INTERFACE_ADDRESS));
        connectedClientDevice.setIpAddress(InetAddresses.parseNumericAddress(P2P_PEER_IP));
        sendApStaConnectedEvent(connectedClientDevice);
        // Verify that the connected client is added to the p2p group client list.
        sendRequestGroupInfoMsg(mClientMessenger);
        verify(mClientHandler, times(2)).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_GROUP_INFO, mMessageCaptor.getValue().what);
        wifiP2pGroup = (WifiP2pGroup) mMessageCaptor.getValue().obj;
        assertEquals(1, wifiP2pGroup.getClientList().size());
        WifiP2pDevice client = wifiP2pGroup.getClientList().iterator().next();
        assertNotNull(client);
        assertEquals(mTestWifiP2pDevice.deviceAddress, client.deviceAddress);
        assertEquals(MacAddress.fromString(PEER_INTERFACE_ADDRESS),
                client.getInterfaceMacAddress());
        if (SdkLevel.isAtLeastV()) {
            assertEquals(InetAddresses.parseNumericAddress(P2P_PEER_IP), client.getIpAddress());
        }

        // Verify that the interface MAC address and IP address is not set in the P2P peer list.
        sendRequestPeersMsg(mClientMessenger);
        verify(mClientHandler, times(3)).sendMessage(mMessageCaptor.capture());
        peers = (WifiP2pDeviceList) mMessageCaptor.getValue().obj;
        assertEquals(WifiP2pManager.RESPONSE_PEERS, mMessageCaptor.getValue().what);
        dev = peers.get(mTestWifiP2pDevice.deviceAddress);
        assertNotNull(dev);
        assertNull(dev.getInterfaceMacAddress());
        if (SdkLevel.isAtLeastV()) {
            assertNull(dev.getIpAddress());
        }

        // Trigger AP_STA_DISCONNECTED event & validate the results.
        sendApStaDisConnectedEvent(connectedClientDevice);
        // Verify that the client is removed from the P2P group client list.
        sendRequestGroupInfoMsg(mClientMessenger);
        verify(mClientHandler, times(4)).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_GROUP_INFO, mMessageCaptor.getValue().what);
        wifiP2pGroup = (WifiP2pGroup) mMessageCaptor.getValue().obj;
        assertNotNull(wifiP2pGroup);
        assertEquals(0, wifiP2pGroup.getClientList().size());

        // Verify that the P2P device is not removed from the P2P peer list.
        sendRequestPeersMsg(mClientMessenger);
        verify(mClientHandler, times(5)).sendMessage(mMessageCaptor.capture());
        peers = (WifiP2pDeviceList) mMessageCaptor.getValue().obj;
        assertEquals(WifiP2pManager.RESPONSE_PEERS, mMessageCaptor.getValue().what);
        dev = peers.get(mTestWifiP2pDevice.deviceAddress);
        assertNotNull(dev);
        assertEquals(mTestWifiP2pDevice.deviceAddress, dev.deviceAddress);
    }

    /**
     * Verify the connection event ends due to timeout.
     */
    @Test
    public void testEndConnectionEventWhenTimeout() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);

        mockEnterGroupNegotiationState();
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }

        mLooper.moveTimeForward(120 * 1000 * 2);
        mLooper.dispatchAll();

        verify(mWifiP2pMetrics).endConnectionEvent(
                eq(P2pConnectionEvent.CLF_TIMEOUT));
    }

    /**
     * Verify accepting the frequency conflict dialog will send a disconnect wifi request.
     */
    @Test
    public void testAcceptFrequencyConflictDialogSendsDisconnectWifiRequest() throws Exception {
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);
        AsyncChannel wifiChannel = mAsyncChannel;
        sendChannelHalfConnectedEvent(mClientMessenger, wifiChannel);
        WifiDialogManager.DialogHandle dialogHandle = mock(WifiDialogManager.DialogHandle.class);
        when(mWifiDialogManager.createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(dialogHandle);
        ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> callbackCaptor =
                ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);

        mockEnterGroupNegotiationState();
        mockPeersList();
        sendSetOngoingPeerConfigMsg(mClientMessenger, mTestWifiP2pPeerConfig);
        mLooper.dispatchAll();
        sendGoNegotiationFailureEvent(mClientMessenger,
                WifiP2pServiceImpl.P2pStatus.NO_COMMON_CHANNEL);

        if (!SdkLevel.isAtLeastT()) {
            verify(mAlertDialog).show();
            ArgumentCaptor<DialogInterface.OnClickListener> clickListener = ArgumentCaptor.forClass(
                    DialogInterface.OnClickListener.class);
            verify(mAlertDialogBuilder).setPositiveButton(any(), clickListener.capture());
            clickListener.getValue().onClick(mAlertDialog, DialogInterface.BUTTON_POSITIVE);
        } else {
            verify(mWifiDialogManager).createSimpleDialog(
                    any(), any(), any(), any(), any(), callbackCaptor.capture(), any());
            verify(dialogHandle).launchDialog();
            callbackCaptor.getValue().onPositiveButtonClicked();
        }
        mLooper.dispatchAll();
        verify(wifiChannel).sendMessage(WifiP2pServiceImpl.DISCONNECT_WIFI_REQUEST, 1);
    }

    /**
     * Verify DISCONNECT_WIFI_REQUEST is cleared when cancelConnect() is called.
     */
    @Test
    public void testClearDisconnectWifiRequestOnCallCancelConnect() throws Exception {
        // accept the frequency conflict dialog to start next try.
        testAcceptFrequencyConflictDialogSendsDisconnectWifiRequest();

        reset(mAsyncChannel);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.CANCEL_CONNECT);
        mLooper.moveTimeForward(100);
        mLooper.dispatchAll();
        verify(mAsyncChannel).sendMessage(WifiP2pServiceImpl.DISCONNECT_WIFI_REQUEST, 0);
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_P2P_CANCEL_CONNECT), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /**
     * Verify DISCONNECT_WIFI_REQUEST is cleared when group formation fails.
     */
    @Test
    public void testClearDisconnectWifiRequestWhenGroupFormationFails() throws Exception {
        // accept the frequency conflict dialog to start next try.
        testAcceptFrequencyConflictDialogSendsDisconnectWifiRequest();

        reset(mAsyncChannel);
        // Send a reject to trigger handleGroupCreationFailure().
        sendSimpleMsg(mClientMessenger, WifiP2pServiceImpl.DROP_WIFI_USER_REJECT);
        verify(mAsyncChannel).sendMessage(WifiP2pServiceImpl.DISCONNECT_WIFI_REQUEST, 0);
    }

    /**
     * Verify declining the frequency conflict dialog will end the P2P connection event.
     */
    @Test
    public void testDeclineFrequencyConflictDialogEndsP2pConnectionEvent() throws Exception {
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);
        AsyncChannel wifiChannel = mock(AsyncChannel.class);
        sendChannelHalfConnectedEvent(mClientMessenger, wifiChannel);
        WifiDialogManager.DialogHandle dialogHandle = mock(WifiDialogManager.DialogHandle.class);
        when(mWifiDialogManager.createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(dialogHandle);
        ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> callbackCaptor =
                ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);

        mockEnterGroupNegotiationState();
        mockPeersList();
        sendSetOngoingPeerConfigMsg(mClientMessenger, mTestWifiP2pPeerConfig);
        mLooper.dispatchAll();
        sendGoNegotiationFailureEvent(mClientMessenger,
                WifiP2pServiceImpl.P2pStatus.NO_COMMON_CHANNEL);

        if (!SdkLevel.isAtLeastT()) {
            verify(mAlertDialog).show();
            ArgumentCaptor<DialogInterface.OnClickListener> clickListener = ArgumentCaptor.forClass(
                    DialogInterface.OnClickListener.class);
            verify(mAlertDialogBuilder).setNegativeButton(any(), clickListener.capture());
            clickListener.getValue().onClick(mAlertDialog, DialogInterface.BUTTON_NEGATIVE);
        } else {
            verify(mWifiDialogManager).createSimpleDialog(
                    any(), any(), any(), any(), any(), callbackCaptor.capture(), any());
            verify(dialogHandle).launchDialog();
            callbackCaptor.getValue().onNegativeButtonClicked();

        }
        mLooper.dispatchAll();
        verify(mWifiP2pMetrics).endConnectionEvent(P2pConnectionEvent.CLF_USER_REJECT);
    }

    /**
     * Verify the frequency conflict dialog is dismissed when the frequency conflict state exits.
     */
    @Test
    public void testFrequencyConflictDialogDismissedOnStateExit() throws Exception {
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);
        AsyncChannel wifiChannel = mock(AsyncChannel.class);
        sendChannelHalfConnectedEvent(mClientMessenger, wifiChannel);
        WifiDialogManager.DialogHandle dialogHandle = mock(WifiDialogManager.DialogHandle.class);
        when(mWifiDialogManager.createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(dialogHandle);
        ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> callbackCaptor =
                ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);

        mockEnterGroupNegotiationState();
        mockPeersList();
        sendSetOngoingPeerConfigMsg(mClientMessenger, mTestWifiP2pPeerConfig);
        mLooper.dispatchAll();
        sendGoNegotiationFailureEvent(mClientMessenger,
                WifiP2pServiceImpl.P2pStatus.NO_COMMON_CHANNEL);
        WifiP2pGroup group = new WifiP2pGroup();
        group.setNetworkId(WifiP2pGroup.NETWORK_ID_PERSISTENT);
        group.setNetworkName(TEST_NETWORK_NAME);
        group.setOwner(new WifiP2pDevice("thisDeviceMac"));
        group.setIsGroupOwner(true);
        group.setInterface(IFACE_NAME_P2P);
        sendGroupStartedMsg(group);

        if (!SdkLevel.isAtLeastT()) {
            verify(mAlertDialog).show();
            verify(mAlertDialog).dismiss();
        } else {
            verify(mWifiDialogManager).createSimpleDialog(
                    any(), any(), any(), any(), any(), callbackCaptor.capture(), any());
            verify(dialogHandle).launchDialog();
            verify(dialogHandle).dismissDialog();
        }
    }

    /**
     * Verify the connection event ends due to the cancellation.
     */
    @Test
    public void testEndConnectionEventWhenCancel() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);

        mockEnterGroupNegotiationState();
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }

        sendSimpleMsg(mClientMessenger, WifiP2pManager.CANCEL_CONNECT);

        verify(mWifiP2pMetrics).endConnectionEvent(
                eq(P2pConnectionEvent.CLF_CANCEL));
    }

    /**
     * Verify the connection event ends due to the provision discovery failure.
     */
    @Test
    public void testEndConnectionEventWhenProvDiscFailure() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);

        mockEnterProvisionDiscoveryState();
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }
        WifiP2pProvDiscEvent pdEvent = new WifiP2pProvDiscEvent();
        pdEvent.device.deviceAddress = mTestWifiP2pPeerConfig.deviceAddress;

        sendSimpleMsg(null, WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT,
                WifiP2pMonitor.PROV_DISC_STATUS_UNKNOWN, pdEvent);

        verify(mWifiP2pMetrics).endConnectionEvent(
                eq(P2pConnectionEvent.CLF_PROV_DISC_FAIL));
    }

    /**
     * Verify the connection event ends due to the group removal.
     */
    @Test
    public void testEndConnectionEventWhenGroupRemoval() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);

        mockEnterGroupNegotiationState();
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }

        sendSimpleMsg(null, WifiP2pMonitor.P2P_GROUP_REMOVED_EVENT);
        verify(mContext).sendBroadcastWithMultiplePermissions(
                argThat(new WifiP2pServiceImplTest
                        .P2pConnectionChangedIntentMatcherForNetworkState(FAILED)), any());
        if (SdkLevel.isAtLeastT()) {
            verify(mContext, atLeastOnce()).sendBroadcast(
                    argThat(new WifiP2pServiceImplTest
                            .P2pConnectionChangedIntentMatcherForNetworkState(FAILED)), any(),
                    any());
            verify(mBroadcastOptions, atLeastOnce())
                    .setRequireAllOfPermissions(TEST_REQUIRED_PERMISSIONS_T);
            verify(mBroadcastOptions, atLeastOnce())
                    .setRequireNoneOfPermissions(TEST_EXCLUDED_PERMISSIONS_T);
        }

        verify(mWifiP2pMetrics).endConnectionEvent(eq(P2pConnectionEvent.CLF_GROUP_REMOVED));
    }

    @Test
    public void testStartP2pLocationOn() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        simulateLocationModeChange(true);
        simulateWifiStateChange(true);
        checkIsP2pInitWhenClientConnected(true, mClient1,
                new WorkSource(mClient1.getCallingUid(), TEST_PACKAGE_NAME));

        verify(mBroadcastOptions, atLeastOnce())
                .setRequireAllOfPermissions(TEST_REQUIRED_PERMISSIONS_T);
        verify(mBroadcastOptions, atLeastOnce())
                .setRequireNoneOfPermissions(TEST_EXCLUDED_PERMISSIONS_T);
    }

    @Test
    public void testStartP2pLocationOff() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        simulateLocationModeChange(false);
        simulateWifiStateChange(true);
        checkIsP2pInitWhenClientConnected(true, mClient1,
                new WorkSource(mClient1.getCallingUid(), TEST_PACKAGE_NAME));

        verify(mBroadcastOptions, atLeastOnce())
                .setRequireAllOfPermissions(TEST_REQUIRED_PERMISSIONS_T);
        verify(mBroadcastOptions, never()).setRequireNoneOfPermissions(TEST_EXCLUDED_PERMISSIONS_T);
    }

    /**
     * Verify the connection event ends due to the invitation failure.
     */
    @Test
    public void testEndConnectionEventWhenInvitationFailure() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);

        mockEnterGroupNegotiationState();
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }

        sendInvitationResultMsg(WifiP2pServiceImpl.P2pStatus.UNKNOWN);

        verify(mWifiP2pMetrics).endConnectionEvent(
                eq(P2pConnectionEvent.CLF_INVITATION_FAIL));
    }

    /**
     * Verify the connection event ends when peer reject the connect request in negotiation flow.
     */
    @Test
    public void testEndConnectionEventWhenPeerRejectTheConnectRequestInNegotiationFlow()
            throws Exception {
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);
        mockEnterGroupNegotiationState();

        WifiP2pProvDiscEvent pdEvent = new WifiP2pProvDiscEvent();
        pdEvent.device = mTestWifiP2pDevice;
        sendSimpleMsg(null,
                WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT,
                WifiP2pMonitor.PROV_DISC_STATUS_REJECTED,
                pdEvent);

        verify(mWifiP2pMetrics).endConnectionEvent(
                eq(P2pConnectionEvent.CLF_GROUP_REMOVED));
    }

    /**
     * Verify WifiP2pManager.RESPONSE_DEVICE_INFO is returned with null object when a caller
     * without proper permission attempts.
     */
    @Test
    public void testRequestDeviceInfoFailureWhenPermissionDenied() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        doNothing().when(mWifiPermissionsUtil).checkPackage(anyInt(), anyString());
        if (SdkLevel.isAtLeastT()) {
            when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                    .thenReturn(false);
        } else {
            when(mWifiPermissionsUtil.checkCanAccessWifiDirect(any(), any(), anyInt(),
                    anyBoolean())).thenReturn(false);
        }
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DEVICE_INFO);
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_DEVICE_INFO, mMessageCaptor.getValue().what);
        assertNull(mMessageCaptor.getValue().obj);
    }

    /**
     * Verify WifiP2pManager.RESPONSE_DEVICE_INFO is returned with expect object when a caller
     * with proper permission attempts in p2p enabled state.
     */
    @Test
    public void testRequestDeviceInfoSuccessWhenP2pEnabled() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DEVICE_INFO);
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_DEVICE_INFO, mMessageCaptor.getValue().what);
        WifiP2pDevice wifiP2pDevice = (WifiP2pDevice) mMessageCaptor.getValue().obj;
        assertEquals(ANONYMIZED_DEVICE_ADDRESS, wifiP2pDevice.deviceAddress);
        assertEquals(thisDeviceName, wifiP2pDevice.deviceName);
    }

    /**
     * Verify WifiP2pManager.RESPONSE_DEVICE_INFO is returned with empty object when a caller
     * with proper permission attempts in p2p disabled state.
     */
    @Test
    public void testRequestDeviceInfoReturnEmptyWifiP2pDeviceWhenP2pDisabled() throws Exception {
        setTargetSdkGreaterThanT();
        simulateInitChannel(mClient1);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DEVICE_INFO);
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_DEVICE_INFO, mMessageCaptor.getValue().what);
        WifiP2pDevice wifiP2pDevice = (WifiP2pDevice) mMessageCaptor.getValue().obj;
        assertEquals(ANONYMIZED_DEVICE_ADDRESS, wifiP2pDevice.deviceAddress);
        assertEquals(thisDeviceName, wifiP2pDevice.deviceName);
    }

    /**
     * Verify WifiP2pManager.RESPONSE_DEVICE_INFO returns an object with the actual device MAC when
     * the caller holds the LOCAL_MAC_ADDRESS permission.
     */
    @Test
    public void testRequestDeviceInfoReturnsActualMacForNetworkSettingsApp() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        when(mWifiPermissionsUtil.checkLocalMacAddressPermission(anyInt())).thenReturn(true);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DEVICE_INFO);
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(false));
        }
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_DEVICE_INFO, mMessageCaptor.getValue().what);
        WifiP2pDevice wifiP2pDevice = (WifiP2pDevice) mMessageCaptor.getValue().obj;
        assertEquals(thisDeviceMac, wifiP2pDevice.deviceAddress);
        assertEquals(thisDeviceName, wifiP2pDevice.deviceName);
    }

    /**
     * Verify WifiP2pManager.REQUEST_DEVICE_INFO won't enable P2P when
     * P2P MAC randomization is disabled and P2P device address exists.
     */
    @Test
    public void testRequestDeviceInfoNotSetUpInterfaceWhenDeviceAddressExists() throws Exception {
        when(mWifiGlobals.isP2pMacRandomizationSupported()).thenReturn(false);

        setTargetSdkGreaterThanT();
        // Entering P2pEnabledState will populate the device address to the mThisDevice.
        forceP2pEnabled(mClient1);
        when(mWifiPermissionsUtil.checkLocalMacAddressPermission(anyInt())).thenReturn(true);

        // The interface is set up for getting the device address.
        verify(mWifiNative).setupInterface(any(), any(), any());

        // Go back to P2pDisabledState.
        sendP2pStateMachineMessage(WifiP2pServiceImpl.DISABLE_P2P);
        sendP2pStateMachineMessage(WifiP2pMonitor.SUP_DISCONNECTION_EVENT);
        mLooper.dispatchAll();

        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DEVICE_INFO);
        // Still 1 time, no additional interface setup.
        verify(mWifiNative).setupInterface(any(), any(), any());
    }

    /**
     * Verify WifiP2pManager.REQUEST_DEVICE_INFO enable P2P when
     * P2P MAC randomization is enabled and P2P device address exists.
     */
    @Test
    public void testRequestDeviceInfoSetUpInterfaceWhenRandomMacEnabled() throws Exception {
        when(mWifiGlobals.isP2pMacRandomizationSupported()).thenReturn(true);

        setTargetSdkGreaterThanT();
        // Entering P2pEnabledState will populate the device address to the mThisDevice.
        forceP2pEnabled(mClient1);
        when(mWifiPermissionsUtil.checkLocalMacAddressPermission(anyInt())).thenReturn(true);

        // The interface is set up for getting the device address.
        verify(mWifiNative).setupInterface(any(), any(), any());

        // Go back to P2pDisabledState.
        sendP2pStateMachineMessage(WifiP2pServiceImpl.DISABLE_P2P);
        sendP2pStateMachineMessage(WifiP2pMonitor.SUP_DISCONNECTION_EVENT);
        mLooper.dispatchAll();

        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DEVICE_INFO);
        // REQUEST_DEVICE_INFO results in the second setup.
        verify(mWifiNative, times(2)).setupInterface(any(), any(), any());
    }

    private String verifyCustomizeDefaultDeviceName(String expectedName, boolean isRandomPostfix)
            throws Exception {
        forceP2pEnabled(mClient1);
        when(mWifiPermissionsUtil.checkLocalMacAddressPermission(anyInt())).thenReturn(true);

        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DEVICE_INFO);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_DEVICE_INFO, mMessageCaptor.getValue().what);

        WifiP2pDevice wifiP2pDevice = (WifiP2pDevice) mMessageCaptor.getValue().obj;
        if (isRandomPostfix) {
            assertEquals(expectedName,
                    wifiP2pDevice.deviceName.substring(0, expectedName.length()));
        } else {
            assertEquals(expectedName, wifiP2pDevice.deviceName);
        }
        return wifiP2pDevice.deviceName;
    }

    private void setupDefaultDeviceNameCustomization(
            String prefix, int postfixDigit) {
        when(mWifiSettingsConfigStore.get(eq(WIFI_P2P_DEVICE_NAME))).thenReturn(null);
        when(mFrameworkFacade.getSecureStringSetting(any(), eq(Settings.Secure.ANDROID_ID)))
                .thenReturn(TEST_ANDROID_ID);
        when(mWifiGlobals.getWifiP2pDeviceNamePrefix()).thenReturn(prefix);
        when(mWifiGlobals.getWifiP2pDeviceNamePostfixNumDigits()).thenReturn(postfixDigit);
    }

    /** Verify that the default device name is customized by overlay on S or older. */
    @Test
    public void testCustomizeDefaultDeviceNameOnSorOlder() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        setupDefaultDeviceNameCustomization("Niceboat-", -1);
        verifyCustomizeDefaultDeviceName("Niceboat-" + TEST_ANDROID_ID.substring(0, 4), false);
    }

    /** Verify that the default device name is customized by overlay. */
    @Test
    public void testCustomizeDefaultDeviceName() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        setupDefaultDeviceNameCustomization("Niceboat-", -1);
        verifyCustomizeDefaultDeviceName("Niceboat-", true);
    }

    /** Verify that the prefix fallback to Android_ if the prefix is too long. */
    @Test
    public void testCustomizeDefaultDeviceNameTooLongPrefix() throws Exception {
        setupDefaultDeviceNameCustomization(
                StringUtil.generateRandomNumberString(
                        WifiP2pServiceImpl.DEVICE_NAME_PREFIX_LENGTH_MAX + 1), 4);
        verifyCustomizeDefaultDeviceName(WifiP2pServiceImpl.DEFAULT_DEVICE_NAME_PREFIX, true);
    }

    /** Verify that the prefix fallback to Android_ if the prefix is empty. */
    @Test
    public void testCustomizeDefaultDeviceNameEmptyPrefix() throws Exception {
        setupDefaultDeviceNameCustomization("", 6);
        verifyCustomizeDefaultDeviceName(WifiP2pServiceImpl.DEFAULT_DEVICE_NAME_PREFIX, true);
    }

    /** Verify that the postfix fallbacks to 4-digit ANDROID_ID if the length is smaller than 4. */
    @Test
    public void testCustomizeDefaultDeviceNamePostfixTooShortOnSorOlder() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        setupDefaultDeviceNameCustomization("Prefix",
                WifiP2pServiceImpl.DEVICE_NAME_POSTFIX_LENGTH_MIN - 1);
        verifyCustomizeDefaultDeviceName("Prefix" + TEST_ANDROID_ID.substring(0, 4), true);
    }

    /** Verify that the postfix fallbacks to 4-digit ANDROID_ID if the length is smaller than 4. */
    @Test
    public void testCustomizeDefaultDeviceNamePostfixTooShort() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        setupDefaultDeviceNameCustomization("Prefix",
                WifiP2pServiceImpl.DEVICE_NAME_POSTFIX_LENGTH_MIN - 1);
        verifyCustomizeDefaultDeviceName("Prefix", true);
    }

    /** Verify that the postfix fallbacks to 4-digit ANDROID_ID if the length is 0.*/
    @Test
    public void testCustomizeDefaultDeviceNamePostfixIsZeroLengthOnSorOlder() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        setupDefaultDeviceNameCustomization("Prefix", 0);
        verifyCustomizeDefaultDeviceName("Prefix" + TEST_ANDROID_ID.substring(0, 4), true);
    }

    /** Verify that the postfix fallbacks to 4-digit ANDROID_ID if the length is 0.*/
    @Test
    public void testCustomizeDefaultDeviceNamePostfixIsZeroLength() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        setupDefaultDeviceNameCustomization("Prefix", 0);
        verifyCustomizeDefaultDeviceName("Prefix", true);
    }

    /** Verify that the digit length exceeds the remaining bytes. */
    @Test
    public void testCustomizeDefaultDeviceNameWithFewerRemainingBytes() throws Exception {
        int postfixLength = 6;
        String prefix = StringUtil.generateRandomNumberString(
                WifiP2pServiceImpl.DEVICE_NAME_LENGTH_MAX - postfixLength + 1);
        setupDefaultDeviceNameCustomization(prefix, postfixLength);
        verifyCustomizeDefaultDeviceName(prefix, true);
    }

    /** Verify that the default device name is customized by overlay
     * when saved one is an empty string. */
    @Test
    public void testCustomizeDefaultDeviceNameWithEmptySavedNameOnSorOlder() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        setupDefaultDeviceNameCustomization("Niceboat-", -1);
        when(mWifiSettingsConfigStore.get(eq(WIFI_P2P_DEVICE_NAME))).thenReturn("");
        verifyCustomizeDefaultDeviceName("Niceboat-" + TEST_ANDROID_ID.substring(0, 4), false);
    }

    /** Verify that the default device name is customized by overlay
     * when saved one is an empty string. */
    @Test
    public void testCustomizeDefaultDeviceNameWithEmptySavedName() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        setupDefaultDeviceNameCustomization("Niceboat-", -1);
        when(mWifiSettingsConfigStore.get(eq(WIFI_P2P_DEVICE_NAME))).thenReturn("");
        verifyCustomizeDefaultDeviceName("Niceboat-", true);
    }

    /** Verify that the default device name is preserved in a period. */
    @Test
    public void testCustomizeDefaultDeviceNameIsPreserved() throws Exception {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);

        setupDefaultDeviceNameCustomization("Niceboat-", 4);
        String defaultDeviceName = verifyCustomizeDefaultDeviceName("Niceboat-", true);

        // re-init P2P, and the default name should be the same.
        mockEnterDisabledState();
        sendP2pStateMachineMessage(WifiP2pServiceImpl.ENABLE_P2P);
        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DEVICE_INFO);
        verify(mClientHandler, times(2)).sendMessage(msgCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_DEVICE_INFO, msgCaptor.getValue().what);
        WifiP2pDevice wifiP2pDevice = (WifiP2pDevice) msgCaptor.getAllValues().get(1).obj;
        assertEquals(defaultDeviceName, wifiP2pDevice.deviceName);

        // After the default name expires, the default name should be changed.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(
                WifiP2pServiceImpl.DEFAULT_DEVICE_NAME_LIFE_TIME_MILLIS);
        mockEnterDisabledState();
        // Activate this client
        sendSimpleMsg(mClientMessenger, WifiP2pManager.DISCOVER_PEERS);
        sendP2pStateMachineMessage(WifiP2pServiceImpl.ENABLE_P2P);
        msgCaptor = ArgumentCaptor.forClass(Message.class);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DEVICE_INFO);
        verify(mClientHandler, times(4)).sendMessage(msgCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_DEVICE_INFO, msgCaptor.getValue().what);
        wifiP2pDevice = (WifiP2pDevice) msgCaptor.getAllValues().get(3).obj;
        assertNotEquals(defaultDeviceName, wifiP2pDevice.deviceName);
    }

    /**
     * Verify the caller sends WifiP2pManager.STOP_DISCOVERY.
     */
    @Test
    public void testStopDiscoverySuccess() throws Exception {
        when(mWifiNative.p2pStopFind()).thenReturn(true);
        forceP2pEnabled(mClient1);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.STOP_DISCOVERY);
        verify(mWifiNative).p2pStopFind();
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.STOP_DISCOVERY_SUCCEEDED, message.what);
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_P2P_STOP_PEER_DISCOVERY), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /**
     * Verify WifiP2pManager.STOP_DISCOVERY_FAILED is returned when native call failure.
     */
    @Test
    public void testStopDiscoveryFailureWhenNativeCallFailure() throws Exception {
        when(mWifiNative.p2pStopFind()).thenReturn(false);
        forceP2pEnabled(mClient1);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.STOP_DISCOVERY);
        verify(mWifiNative).p2pStopFind();
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.STOP_DISCOVERY_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify WifiP2pManager.STOP_DISCOVERY_FAILED is returned when p2p is disabled.
     */
    @Test
    public void testStopDiscoveryFailureWhenP2pDisabled() throws Exception {
        sendSimpleMsg(mClientMessenger, WifiP2pManager.STOP_DISCOVERY);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.STOP_DISCOVERY_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     * Verify WifiP2pManager.STOP_DISCOVERY_FAILED is returned when p2p is unsupported.
     */
    @Test
    public void testStopDiscoveryFailureWhenP2pUnsupported() throws Exception {
        setUpWifiP2pServiceImpl(false);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.STOP_DISCOVERY);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.STOP_DISCOVERY_FAILED, message.what);
        assertEquals(WifiP2pManager.P2P_UNSUPPORTED, message.arg1);
    }

    /**
     * Verify WifiP2pManager.STOP_DISCOVERY_FAILED is returned when creating group.
     */
    @Test
    public void testStopDiscoveryFailureWhenCreatingGroup() throws Exception {
        // Move to group creating state
        testConnectWithConfigValidAsGroupSuccess();
        sendSimpleMsg(mClientMessenger, WifiP2pManager.STOP_DISCOVERY);
        verify(mClientHandler, atLeastOnce()).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.STOP_DISCOVERY_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     * Verify WifiP2pManager.START_LISTEN_FAILED is returned when creating group.
     */
    @Test
    public void testStartListenFailureWhenCreatingGroup() throws Exception {
        // Move to group creating state
        testConnectWithConfigValidAsGroupSuccess();
        sendSimpleMsg(mClientMessenger, WifiP2pManager.START_LISTEN);
        verify(mClientHandler, atLeastOnce()).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.START_LISTEN_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     * Verify WifiP2pManager.STOP_LISTEN_SUCCEEDED is returned when native call to stop listen
     * succeed in creating group.
     */
    @Test
    public void testStopListenSuccessWhenNativeCallSucceedInCreatingGroup() throws Exception {
        when(mWifiNative.p2pExtListen(eq(false), anyInt(), anyInt(), any())).thenReturn(true);
        // Move to group creating state
        testConnectWithConfigValidAsGroupSuccess();
        sendSimpleMsg(mClientMessenger, WifiP2pManager.STOP_LISTEN);
        verify(mClientHandler, atLeastOnce()).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.STOP_LISTEN_SUCCEEDED, message.what);
    }

    /**
     * Verify WifiP2pManager.STOP_LISTEN_FAILED is returned when native call to stop listen
     * fail in creating group.
     */
    @Test
    public void testStopListenFailureWhenNativeCallFailInCreatingGroup() throws Exception {
        when(mWifiNative.p2pExtListen(eq(false), anyInt(), anyInt(), any())).thenReturn(false);
        // Move to group creating state
        testConnectWithConfigValidAsGroupSuccess();
        sendSimpleMsg(mClientMessenger, WifiP2pManager.STOP_LISTEN);
        verify(mClientHandler, atLeastOnce()).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.STOP_LISTEN_FAILED, message.what);
    }

    /**
     * Verify the caller sends WifiP2pManager.CANCEL_CONNECT.
     */
    @Test
    public void testCancelConnectSuccess() throws Exception {
        // Move to group creating state
        testConnectWithConfigValidAsGroupSuccess();

        sendSimpleMsg(mClientMessenger, WifiP2pManager.CANCEL_CONNECT);
        verify(mClientHandler, atLeastOnce()).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertNotEquals(WifiP2pManager.CANCEL_CONNECT_SUCCEEDED, message.what);

        mLooper.moveTimeForward(100);
        mLooper.dispatchAll();
        verify(mClientHandler, atLeastOnce()).sendMessage(mMessageCaptor.capture());
        message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.CANCEL_CONNECT_SUCCEEDED, message.what);
    }

    /**
     * Verify WifiP2pManager.CANCEL_CONNECT_FAILED is returned when p2p is inactive.
     */
    @Test
    public void testCancelConnectFailureWhenP2pInactive() throws Exception {
        // Move to inactive state
        forceP2pEnabled(mClient1);

        sendSimpleMsg(mClientMessenger, WifiP2pManager.CANCEL_CONNECT);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.CANCEL_CONNECT_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     * Verify WifiP2pManager.CANCEL_CONNECT_FAILED is returned when p2p is disabled.
     */
    @Test
    public void testCancelConnectFailureWhenP2pDisabled() throws Exception {
        sendSimpleMsg(mClientMessenger, WifiP2pManager.CANCEL_CONNECT);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.CANCEL_CONNECT_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     * Verify WifiP2pManager.CANCEL_CONNECT_FAILED is returned when p2p is unsupported.
     */
    @Test
    public void testCancelConnectFailureWhenP2pUnsupported() throws Exception {
        setUpWifiP2pServiceImpl(false);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.CANCEL_CONNECT);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.CANCEL_CONNECT_FAILED, message.what);
        assertEquals(WifiP2pManager.P2P_UNSUPPORTED, message.arg1);
    }

    /**
     * Verify the caller sends WifiP2pManager.REMOVE_GROUP.
     */
    @Test
    public void testRemoveGroupSuccess() throws Exception {
        mockEnterGroupCreatedState();

        when(mWifiNative.p2pGroupRemove(eq(IFACE_NAME_P2P))).thenReturn(true);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REMOVE_GROUP);
        verify(mWifiNative).p2pGroupRemove(eq(IFACE_NAME_P2P));
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_GROUP_SUCCEEDED, message.what);
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_P2P_REMOVE_GROUP), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /**
     *  Verify WifiP2pManager.REMOVE_GROUP_FAILED is returned when native call failure.
     */
    @Test
    public void testRemoveGroupFailureWhenNativeCallFailure() throws Exception {
        mockEnterGroupCreatedState();

        when(mWifiNative.p2pGroupRemove(eq(IFACE_NAME_P2P))).thenReturn(false);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REMOVE_GROUP);
        verify(mWifiNative).p2pGroupRemove(eq(IFACE_NAME_P2P));
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_GROUP_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     *  Verify WifiP2pManager.REMOVE_GROUP_FAILED is returned when p2p is creating group.
     */
    @Test
    public void testRemoveGroupFailureWhenP2pCreatingGroup() throws Exception {
        // Move to group creating state
        testConnectWithConfigValidAsGroupSuccess();

        sendSimpleMsg(mClientMessenger, WifiP2pManager.REMOVE_GROUP);
        verify(mClientHandler, atLeastOnce()).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_GROUP_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     *  Verify WifiP2pManager.REMOVE_GROUP_FAILED is returned when p2p is inactive.
     */
    @Test
    public void testRemoveGroupFailureWhenP2pInactive() throws Exception {
        // Move to inactive state
        forceP2pEnabled(mClient1);

        sendSimpleMsg(mClientMessenger, WifiP2pManager.REMOVE_GROUP);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_GROUP_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     *  Verify WifiP2pManager.REMOVE_GROUP_FAILED is returned when p2p is disabled.
     */
    @Test
    public void testRemoveGroupFailureWhenP2pDisabled() throws Exception {
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REMOVE_GROUP);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_GROUP_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     *  Verify WifiP2pManager.REMOVE_GROUP_FAILED is returned when p2p is unsupported.
     */
    @Test
    public void testRemoveGroupFailureWhenP2pUnsupported() throws Exception {
        setUpWifiP2pServiceImpl(false);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REMOVE_GROUP);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_GROUP_FAILED, message.what);
        assertEquals(WifiP2pManager.P2P_UNSUPPORTED, message.arg1);
    }

    /**
     * Verify the caller sends WifiP2pManager.SET_CHANNEL.
     */
    @Test
    public void testSetChannelSuccess() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);

        Bundle p2pChannels = new Bundle();
        p2pChannels.putInt("lc", 1);
        p2pChannels.putInt("oc", 2);
        when(mWifiNative.p2pSetListenChannel(anyInt())).thenReturn(true);
        when(mWifiNative.p2pSetOperatingChannel(anyInt(), any())).thenReturn(true);
        sendSetChannelMsg(mClientMessenger, p2pChannels);
        verify(mWifiNative).p2pSetListenChannel(eq(1));
        verify(mWifiNative).p2pSetOperatingChannel(eq(2), any());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_CHANNEL_SUCCEEDED, message.what);
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_P2P_SET_CHANNELS), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /**
     *  Verify WifiP2pManager.SET_CHANNEL_FAILED is returned when native call failure.
     */
    @Test
    public void testSetChannelFailureWhenNativeCallSetListenChannelFailure() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);

        Bundle p2pChannels = new Bundle();
        p2pChannels.putInt("lc", 1);
        p2pChannels.putInt("oc", 2);
        when(mWifiNative.p2pSetListenChannel(anyInt())).thenReturn(false);
        when(mWifiNative.p2pSetOperatingChannel(anyInt(), any())).thenReturn(true);
        sendSetChannelMsg(mClientMessenger, p2pChannels);
        verify(mWifiNative).p2pSetListenChannel(eq(1));
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_CHANNEL_FAILED, message.what);
    }

    /**
     *  Verify WifiP2pManager.SET_CHANNEL_FAILED is returned when native call failure.
     */
    @Test
    public void testSetChannelFailureWhenNativeCallSetOperatingChannelFailure() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);

        Bundle p2pChannels = new Bundle();
        p2pChannels.putInt("lc", 1);
        p2pChannels.putInt("oc", 2);
        when(mWifiNative.p2pSetListenChannel(anyInt())).thenReturn(true);
        when(mWifiNative.p2pSetOperatingChannel(anyInt(), any())).thenReturn(false);
        sendSetChannelMsg(mClientMessenger, p2pChannels);
        verify(mWifiNative).p2pSetListenChannel(eq(1));
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_CHANNEL_FAILED, message.what);
    }

    /**
     *  Verify WifiP2pManager.SET_CHANNEL_FAILED is returned when no permissions are held.
     */
    @Test
    public void testSetChannelFailureWhenNoPermissions() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);

        // no permissions held
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkStackPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(false);

        Bundle p2pChannels = new Bundle();
        p2pChannels.putInt("lc", 1);
        p2pChannels.putInt("oc", 2);
        sendSetChannelMsg(mClientMessenger, p2pChannels);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_CHANNEL_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     *  Verify p2pSetListenChannel doesn't been called when message contain null object.
     */
    @Test
    public void testSetChannelFailureWhenObjectIsNull() throws Exception {
        when(mWifiNative.p2pSetListenChannel(anyInt())).thenReturn(true);
        when(mWifiNative.p2pSetOperatingChannel(anyInt(), any())).thenReturn(true);

        // Move to enabled state
        forceP2pEnabled(mClient1);

        when(mWifiNative.p2pSetListenChannel(anyInt())).thenReturn(false);
        when(mWifiNative.p2pSetOperatingChannel(anyInt(), any())).thenReturn(true);
        sendSetChannelMsg(mClientMessenger, null);
        // Should be called only once on entering P2pEnabledState.
        verify(mWifiNative, times(1)).p2pSetListenChannel(anyInt());
        verify(mWifiNative, times(1)).p2pSetOperatingChannel(anyInt(), any());
    }

    /**
     * Verify the caller sends WifiP2pManager.START_WPS with push button configuration.
     */
    @Test
    public void testStartWpsWithPbcSuccess() throws Exception {
        mockEnterGroupCreatedState();

        when(mWifiNative.startWpsPbc(anyString(), any())).thenReturn(true);
        WpsInfo wps = new WpsInfo();
        wps.setup = WpsInfo.PBC;
        sendStartWpsMsg(mClientMessenger, wps);
        verify(mWifiNative).startWpsPbc(eq(IFACE_NAME_P2P), isNull());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.START_WPS_SUCCEEDED, message.what);
    }

    /**
     * Verify the caller sends WifiP2pManager.START_WPS with pin display.
     */
    @Test
    public void testStartWpsWithPinDisplaySuccess() throws Exception {
        // TODO(hsiuchangchen): This test item is related to UI.
    }

    /**
     * Verify the caller sends WifiP2pManager.START_WPS with pin keypad.
     */
    @Test
    public void testStartWpsWithPinKeypadSuccess() throws Exception {
        mockEnterGroupCreatedState();

        when(mWifiNative.startWpsPinKeypad(anyString(), anyString())).thenReturn(true);
        WpsInfo wps = new WpsInfo();
        wps.setup = WpsInfo.KEYPAD;
        wps.pin = "1234";
        sendStartWpsMsg(mClientMessenger, wps);
        verify(mWifiNative).startWpsPinKeypad(eq(IFACE_NAME_P2P), eq("1234"));
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.START_WPS_SUCCEEDED, message.what);
    }

    /**
     * Verify WifiP2pManager.START_WPS_FAILED is returned when message contain null object.
     */
    @Test
    public void testStartWpsFailureWhenObjectIsNull() throws Exception {
        mockEnterGroupCreatedState();

        WpsInfo wps = null;
        sendStartWpsMsg(mClientMessenger, wps);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.START_WPS_FAILED, message.what);
    }

    /**
     * Verify WifiP2pManager.START_WPS_FAILED is returned when native call failure with
     * push button configuration.
     */
    @Test
    public void testStartWpsWithPbcFailureWhenNativeCallFailure() throws Exception {
        mockEnterGroupCreatedState();
        when(mWifiNative.startWpsPbc(anyString(), any())).thenReturn(false);
        WpsInfo wps = new WpsInfo();
        wps.setup = WpsInfo.PBC;
        sendStartWpsMsg(mClientMessenger, wps);
        verify(mWifiNative).startWpsPbc(eq(IFACE_NAME_P2P), isNull());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.START_WPS_FAILED, message.what);
    }

    /**
     * Verify WifiP2pManager.START_WPS_FAILED is returned when native call failure with
     * pin display.
     */
    @Test
    public void testStartWpsWithPinDisplayFailureWhenNativeCallFailure() throws Exception {
        mockEnterGroupCreatedState();

        when(mWifiNative.startWpsPinDisplay(anyString(), any())).thenReturn("abcd");
        WpsInfo wps = new WpsInfo();
        wps.setup = WpsInfo.DISPLAY;
        sendStartWpsMsg(mClientMessenger, wps);
        verify(mWifiNative).startWpsPinDisplay(eq(IFACE_NAME_P2P), isNull());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.START_WPS_FAILED, message.what);
    }

    /**
     * Verify WifiP2pManager.START_WPS_FAILED is returned when native call failure with
     * pin keypad.
     */
    @Test
    public void testStartWpsWithPinKeypadFailureWhenNativeCallFailure() throws Exception {
        mockEnterGroupCreatedState();

        when(mWifiNative.startWpsPinKeypad(anyString(), anyString())).thenReturn(false);
        WpsInfo wps = new WpsInfo();
        wps.setup = WpsInfo.KEYPAD;
        wps.pin = "1234";
        sendStartWpsMsg(mClientMessenger, wps);
        verify(mWifiNative).startWpsPinKeypad(eq(IFACE_NAME_P2P), eq("1234"));
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.START_WPS_FAILED, message.what);
    }

    /**
     *  Verify WifiP2pManager.START_WPS_FAILED is returned when p2p is inactive.
     */
    @Test
    public void testStartWpsFailureWhenP2pInactive() throws Exception {
        // Move to inactive state
        forceP2pEnabled(mClient1);

        WpsInfo wps = new WpsInfo();
        sendStartWpsMsg(mClientMessenger, wps);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.START_WPS_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     *  Verify WifiP2pManager.START_WPS_FAILED is returned when p2p is disabled.
     */
    @Test
    public void testStartWpsFailureWhenP2pDisabled() throws Exception {
        WpsInfo wps = new WpsInfo();
        sendStartWpsMsg(mClientMessenger, wps);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.START_WPS_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     *  Verify WifiP2pManager.START_WPS_FAILED is returned when p2p is unsupported.
     */
    @Test
    public void testStartWpsFailureWhenP2pUnsupported() throws Exception {
        setUpWifiP2pServiceImpl(false);
        WpsInfo wps = new WpsInfo();
        sendStartWpsMsg(mClientMessenger, wps);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.START_WPS_FAILED, message.what);
        assertEquals(WifiP2pManager.P2P_UNSUPPORTED, message.arg1);
    }

    /**
     * Verify the caller sends WifiP2pManager.SET_DEVICE_NAME.
     */
    @Test
    public void testSetDeviceNameSuccess() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        mTestThisDevice.deviceName = "another-name";
        when(mWifiNative.setDeviceName(anyString())).thenReturn(true);
        when(mWifiNative.setP2pSsidPostfix(anyString())).thenReturn(true);
        sendSetDeviceNameMsg(mClientMessenger, mTestThisDevice);
        verify(mWifiNative).setDeviceName(eq(mTestThisDevice.deviceName));
        verify(mWifiNative).setP2pSsidPostfix(eq("-" + mTestThisDevice.deviceName));
        verify(mWifiSettingsConfigStore).put(
                eq(WIFI_P2P_DEVICE_NAME), eq(mTestThisDevice.deviceName));
        checkSendThisDeviceChangedBroadcast();
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_DEVICE_NAME_SUCCEEDED, message.what);
    }

    /**
     * Verify the caller sends WifiP2pManager.SET_DEVICE_NAME with a name
     * whose length exceeds 22.
     */
    @Test
    public void testSetDeviceNameFailureWithANameLongerThanMaxPostfixLength() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        mTestThisDevice.deviceName = "12345678901234567890123";
        when(mWifiNative.setDeviceName(anyString())).thenReturn(true);
        when(mWifiNative.setP2pSsidPostfix(anyString())).thenReturn(true);
        sendSetDeviceNameMsg(mClientMessenger, mTestThisDevice);
        verify(mWifiNative).setDeviceName(eq(mTestThisDevice.deviceName));
        verify(mWifiNative).setP2pSsidPostfix(
                eq("-" + mTestThisDevice.deviceName.substring(
                        0, WifiP2pServiceImpl.GROUP_NAME_POSTFIX_LENGTH_MAX)));
        verify(mWifiSettingsConfigStore).put(
                eq(WIFI_P2P_DEVICE_NAME), eq(mTestThisDevice.deviceName));
        checkSendThisDeviceChangedBroadcast();
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_DEVICE_NAME_SUCCEEDED, message.what);
    }

    /**
     * Verify the caller sends WifiP2pManager.SET_DEVICE_NAME with a multilingual
     * string whose length exceeds 22.
     */
    @Test
    public void testSetDeviceNameFailureWithANameLongerThanMaxPostfixLengthForMultiByteCharacter()
            throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        // 3-byte for each character, it occupies 24 bytes.
        mTestThisDevice.deviceName = "한국어키보드보드";
        when(mWifiNative.setDeviceName(anyString())).thenReturn(true);
        when(mWifiNative.setP2pSsidPostfix(anyString())).thenReturn(true);
        sendSetDeviceNameMsg(mClientMessenger, mTestThisDevice);
        verify(mWifiNative).setDeviceName(eq(mTestThisDevice.deviceName));
        // As the last character is a multi-byte character, the last
        // character should be truncated completely.
        verify(mWifiNative).setP2pSsidPostfix(eq("-한국어키보드보"));
        verify(mWifiSettingsConfigStore).put(
                eq(WIFI_P2P_DEVICE_NAME), eq(mTestThisDevice.deviceName));
        checkSendThisDeviceChangedBroadcast();
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_DEVICE_NAME_SUCCEEDED, message.what);
    }

    /**
     * Verify the caller sends WifiP2pManager.SET_DEVICE_NAME with a string
     * longer than the maximum length.
     */
    @Test
    public void testSetDeviceNameFailureWithNameExceedMaximumLength() throws Exception {
        when(mWifiGlobals.isP2pMacRandomizationSupported()).thenReturn(true);
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        // clear the one called on entering P2pEnabledState.
        reset(mWifiNative);
        mTestThisDevice.deviceName = "123456789012345678901234567890123";
        when(mWifiNative.setDeviceName(anyString())).thenReturn(true);
        when(mWifiNative.setP2pSsidPostfix(anyString())).thenReturn(true);
        sendSetDeviceNameMsg(mClientMessenger, mTestThisDevice);
        verify(mWifiNative, never()).setDeviceName(any());
        verify(mWifiNative, never()).setP2pSsidPostfix(any());
        verify(mWifiSettingsConfigStore, never()).put(any(), any());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_DEVICE_NAME_FAILED, message.what);
    }

    /**
     * Verify the caller sends WifiP2pManager.SET_DEVICE_NAME with a multilingual string
     * longer than the maximum length.
     */
    @Test
    public void testSetDeviceNameFailureWithMultilingualNameExceedMaximumLength() throws Exception {
        when(mWifiGlobals.isP2pMacRandomizationSupported()).thenReturn(true);
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        // clear the one called on entering P2pEnabledState.
        reset(mWifiNative);
        // 3-byte for each character, it occupies 33 bytes.
        mTestThisDevice.deviceName = "한국어키보드보드한국어";
        when(mWifiNative.setDeviceName(anyString())).thenReturn(true);
        when(mWifiNative.setP2pSsidPostfix(anyString())).thenReturn(true);
        sendSetDeviceNameMsg(mClientMessenger, mTestThisDevice);
        verify(mWifiNative, never()).setDeviceName(any());
        verify(mWifiNative, never()).setP2pSsidPostfix(any());
        verify(mWifiSettingsConfigStore, never()).put(any(), any());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_DEVICE_NAME_FAILED, message.what);
    }

    /**
     * Verify the caller sends WifiP2pManager.SET_DEVICE_NAME with an empty name.
     */
    @Test
    public void testSetDeviceNameFailureWithEmptyName() throws Exception {
        when(mWifiGlobals.isP2pMacRandomizationSupported()).thenReturn(true);
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        // clear the one called on entering P2pEnabledState.
        reset(mWifiNative);
        mTestThisDevice.deviceName = "";
        when(mWifiNative.setDeviceName(anyString())).thenReturn(true);
        sendSetDeviceNameMsg(mClientMessenger, mTestThisDevice);
        verify(mWifiNative, never()).setDeviceName(any());
        verify(mWifiSettingsConfigStore, never()).put(any(), any());

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_DEVICE_NAME_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify WifiP2pManager.SET_DEVICE_NAME_FAILED is returned when native call failed.
     */
    @Test
    public void testSetDeviceNameFailureWhenNativeCallFailure() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);

        // clear the one called on entering P2pEnabledState.
        reset(mWifiNative);
        when(mWifiNative.setP2pSsidPostfix(anyString())).thenReturn(true);
        when(mWifiNative.setDeviceName(anyString())).thenReturn(false);
        sendSetDeviceNameMsg(mClientMessenger, mTestThisDevice);
        verify(mWifiNative).setDeviceName(eq(mTestThisDevice.deviceName));
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_DEVICE_NAME_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify WifiP2pManager.SET_DEVICE_NAME_FAILED is returned when p2p device is null.
     */
    @Test
    public void testSetDeviceNameFailureWhenDeviceIsNull() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);

        sendSetDeviceNameMsg(mClientMessenger, null);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_DEVICE_NAME_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify WifiP2pManager.SET_DEVICE_NAME_FAILED is returned when p2p device's name is null.
     */
    @Test
    public void testSetDeviceNameFailureWhenDeviceNameIsNull() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);

        mTestThisDevice.deviceName = null;
        sendSetDeviceNameMsg(mClientMessenger, mTestThisDevice);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_DEVICE_NAME_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify WifiP2pManager.SET_DEVICE_NAME_FAILED is returned when p2p is disabled.
     */
    @Test
    public void testSetDeviceNameFailureWhenP2pDisabled() throws Exception {
        sendSetDeviceNameMsg(mClientMessenger, mTestThisDevice);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_DEVICE_NAME_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     * Verify WifiP2pManager.SET_DEVICE_NAME_FAILED is returned when p2p is unsupported.
     */
    @Test
    public void testSetDeviceNameFailureWhenP2pUnsupported() throws Exception {
        setUpWifiP2pServiceImpl(false);
        sendSetDeviceNameMsg(mClientMessenger, mTestThisDevice);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_DEVICE_NAME_FAILED, message.what);
        assertEquals(WifiP2pManager.P2P_UNSUPPORTED, message.arg1);
    }

    /**
     * Verify WifiP2pManager.SET_DEVICE_NAME_FAILED is returned when no permissions are held.
     */
    @Test
    public void testSetDeviceNameFailureWhenNoPermissions() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);

        // no permissions held
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkStackPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(false);

        sendSetDeviceNameMsg(mClientMessenger, null);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_DEVICE_NAME_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    @Test
    public void testRestoreDeviceNameAndMacWhenMacRandomizationNotSupported() throws Exception {
        simulateWifiStateChange(true);
        simulateInitChannel(mClient1);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DEVICE_INFO);

        verify(mWifiNative, never()).setupInterface(any(), any(), any());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_DEVICE_INFO, mMessageCaptor.getValue().what);
        WifiP2pDevice wifiP2pDevice = (WifiP2pDevice) mMessageCaptor.getValue().obj;
        //maybeEraseOwnDeviceAddress
        assertEquals(ANONYMIZED_DEVICE_ADDRESS, wifiP2pDevice.deviceAddress);
        assertEquals(thisDeviceName, wifiP2pDevice.deviceName);
    }

    /**
     * Verify the caller sends WifiP2pManager.SET_WFD_INFO with wfd enabled.
     */
    @Test
    public void testSetWfdInfoSuccessWithWfdEnabled() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        mTestThisDevice.wfdInfo = new WifiP2pWfdInfo();
        mTestThisDevice.wfdInfo.setEnabled(true);
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiNative.setWfdEnable(anyBoolean())).thenReturn(true);
        when(mWifiNative.setWfdDeviceInfo(anyString())).thenReturn(true);
        sendSetWfdInfoMsg(mClientMessenger, mTestThisDevice.wfdInfo);

        verify(mWifiInjector).getWifiPermissionsWrapper();
        verify(mWifiPermissionsWrapper).getUidPermission(
                eq(android.Manifest.permission.CONFIGURE_WIFI_DISPLAY), anyInt());
        verify(mWifiNative).setWfdEnable(eq(true));
        verify(mWifiNative).setWfdDeviceInfo(eq(mTestThisDevice.wfdInfo.getDeviceInfoHex()));
        checkSendThisDeviceChangedBroadcast();
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_WFD_INFO_SUCCEEDED, message.what);
    }

    /**
     * Verify the caller sends WifiP2pManager.SET_WFD_INFO with wfd is disabled.
     */
    @Test
    public void testSetWfdInfoSuccessWithWfdDisabled() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        mTestThisDevice.wfdInfo = new WifiP2pWfdInfo();
        mTestThisDevice.wfdInfo.setEnabled(false);
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiNative.setWfdEnable(anyBoolean())).thenReturn(true);
        sendSetWfdInfoMsg(mClientMessenger, mTestThisDevice.wfdInfo);

        verify(mWifiInjector).getWifiPermissionsWrapper();
        verify(mWifiPermissionsWrapper).getUidPermission(
                eq(android.Manifest.permission.CONFIGURE_WIFI_DISPLAY), anyInt());
        verify(mWifiNative).setWfdEnable(eq(false));
        checkSendThisDeviceChangedBroadcast();
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_WFD_INFO_SUCCEEDED, message.what);
    }

    /**
     * Verify WifiP2pManager.SET_WFD_INFO_FAILED is returned when wfd permission denied.
     */
    @Test
    public void testSetWfdInfoFailureWhenWfdPermissionDenied() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        mTestThisDevice.wfdInfo = new WifiP2pWfdInfo();
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        sendSetWfdInfoMsg(mClientMessenger, mTestThisDevice.wfdInfo);
        verify(mWifiInjector).getWifiPermissionsWrapper();
        verify(mWifiPermissionsWrapper).getUidPermission(
                eq(android.Manifest.permission.CONFIGURE_WIFI_DISPLAY), anyInt());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_WFD_INFO_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify WifiP2pManager.SET_WFD_INFO_FAILED is returned when wfdInfo is null.
     */
    @Test
    public void testSetWfdInfoFailureWhenWfdInfoIsNull() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        mTestThisDevice.wfdInfo = null;
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        sendSetWfdInfoMsg(mClientMessenger, mTestThisDevice.wfdInfo);
        verify(mWifiInjector).getWifiPermissionsWrapper();
        verify(mWifiPermissionsWrapper).getUidPermission(
                eq(android.Manifest.permission.CONFIGURE_WIFI_DISPLAY), anyInt());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_WFD_INFO_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify WifiP2pManager.SET_WFD_INFO_FAILED is returned when wfd is enabled
     * and native call "setWfdEnable" failure.
     */
    @Test
    public void testSetWfdInfoFailureWithWfdEnabledWhenNativeCallFailure1() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        mTestThisDevice.wfdInfo = new WifiP2pWfdInfo();
        mTestThisDevice.wfdInfo.setEnabled(true);
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiNative.setWfdEnable(anyBoolean())).thenReturn(false);
        sendSetWfdInfoMsg(mClientMessenger, mTestThisDevice.wfdInfo);

        verify(mWifiInjector).getWifiPermissionsWrapper();
        verify(mWifiPermissionsWrapper).getUidPermission(
                eq(android.Manifest.permission.CONFIGURE_WIFI_DISPLAY), anyInt());
        verify(mWifiNative).setWfdEnable(eq(true));
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_WFD_INFO_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify WifiP2pManager.SET_WFD_INFO_FAILED is returned when wfd is enabled
     * and native call "setWfdDeviceInfo" failure.
     */
    @Test
    public void testSetWfdInfoFailureWithWfdEnabledWhenNativeCallFailure2() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        mTestThisDevice.wfdInfo = new WifiP2pWfdInfo();
        mTestThisDevice.wfdInfo.setEnabled(true);
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiNative.setWfdEnable(anyBoolean())).thenReturn(true);
        when(mWifiNative.setWfdDeviceInfo(anyString())).thenReturn(false);
        sendSetWfdInfoMsg(mClientMessenger, mTestThisDevice.wfdInfo);

        verify(mWifiInjector).getWifiPermissionsWrapper();
        verify(mWifiPermissionsWrapper).getUidPermission(
                eq(android.Manifest.permission.CONFIGURE_WIFI_DISPLAY), anyInt());
        verify(mWifiNative).setWfdEnable(eq(true));
        verify(mWifiNative).setWfdDeviceInfo(eq(mTestThisDevice.wfdInfo.getDeviceInfoHex()));
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_WFD_INFO_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify WifiP2pManager.SET_WFD_INFO_FAILED is returned when wfd is disabled
     * and native call failure.
     */
    @Test
    public void testSetWfdInfoFailureWithWfdDisabledWhenNativeCallFailure() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        mTestThisDevice.wfdInfo = new WifiP2pWfdInfo();
        mTestThisDevice.wfdInfo.setEnabled(false);
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiNative.setWfdEnable(anyBoolean())).thenReturn(false);
        sendSetWfdInfoMsg(mClientMessenger, mTestThisDevice.wfdInfo);

        verify(mWifiInjector).getWifiPermissionsWrapper();
        verify(mWifiPermissionsWrapper).getUidPermission(
                eq(android.Manifest.permission.CONFIGURE_WIFI_DISPLAY), anyInt());
        verify(mWifiNative).setWfdEnable(eq(false));
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_WFD_INFO_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify WifiP2pManager.SET_WFD_INFO_FAILED is returned when wfd permission denied
     * and p2p is disabled.
     */
    @Test
    public void testSetWfdInfoFailureWhenWfdPermissionDeniedAndP2pDisabled() throws Exception {
        mTestThisDevice.wfdInfo = new WifiP2pWfdInfo();
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        sendSetWfdInfoMsg(mClientMessenger, mTestThisDevice.wfdInfo);
        verify(mWifiInjector).getWifiPermissionsWrapper();
        verify(mWifiPermissionsWrapper).getUidPermission(
                eq(android.Manifest.permission.CONFIGURE_WIFI_DISPLAY), anyInt());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_WFD_INFO_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify WifiP2pManager.SET_WFD_INFO_FAILED is returned when p2p is unsupported.
     */
    @Test
    public void testSetWfdInfoFailureWhenP2pUnsupported() throws Exception {
        setUpWifiP2pServiceImpl(false);
        mTestThisDevice.wfdInfo = new WifiP2pWfdInfo();
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        sendSetWfdInfoMsg(mClientMessenger, mTestThisDevice.wfdInfo);
        verify(mWifiInjector).getWifiPermissionsWrapper();
        verify(mWifiPermissionsWrapper).getUidPermission(
                eq(android.Manifest.permission.CONFIGURE_WIFI_DISPLAY), anyInt());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_WFD_INFO_FAILED, message.what);
        assertEquals(WifiP2pManager.P2P_UNSUPPORTED, message.arg1);
    }

    /**
     * Verify WifiP2pManager.SET_WFD_INFO_FAILED is returned when wfd permission denied
     * and p2p is unsupported.
     */
    @Test
    public void testSetWfdInfoFailureWhenWfdPermissionDeniedAndP2pUnsupported() throws Exception {
        setUpWifiP2pServiceImpl(false);
        mTestThisDevice.wfdInfo = new WifiP2pWfdInfo();
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        sendSetWfdInfoMsg(mClientMessenger, mTestThisDevice.wfdInfo);
        verify(mWifiInjector).getWifiPermissionsWrapper();
        verify(mWifiPermissionsWrapper).getUidPermission(
                eq(android.Manifest.permission.CONFIGURE_WIFI_DISPLAY), anyInt());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_WFD_INFO_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify the call setMiracastMode when p2p is enabled.
     */
    @Test
    public void testSetMiracastModeWhenP2pEnabled() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);

        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        mWifiP2pServiceImpl.setMiracastMode(0);
        mLooper.dispatchAll();
        verify(mWifiInjector).getWifiPermissionsWrapper();
        verify(mWifiPermissionsWrapper).getUidPermission(
                eq(android.Manifest.permission.CONFIGURE_WIFI_DISPLAY), anyInt());
        verify(mWifiNative).setMiracastMode(eq(0));
    }

    /**
     * Verify the call setMiracastMode when p2p is disable.
     */
    @Test
    public void testSetMiracastModeWhenP2pDisabled() throws Exception {
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        mWifiP2pServiceImpl.setMiracastMode(0);
        mLooper.dispatchAll();
        verify(mWifiInjector).getWifiPermissionsWrapper();
        verify(mWifiPermissionsWrapper).getUidPermission(
                eq(android.Manifest.permission.CONFIGURE_WIFI_DISPLAY), anyInt());
        verify(mWifiNative, never()).setMiracastMode(anyInt());
    }

    /**
     * Verify the call setMiracastMode when CONFIGURE_WIFI_DISPLAY permission denied.
     */
    @Test(expected = SecurityException.class)
    public void testSetMiracastModeWhenPermissionDeined() throws Exception {
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        mWifiP2pServiceImpl.setMiracastMode(0);
        verify(mWifiInjector).getWifiPermissionsWrapper();
        verify(mWifiPermissionsWrapper).getUidPermission(
                eq(android.Manifest.permission.CONFIGURE_WIFI_DISPLAY), anyInt());
        verify(mWifiNative, never()).setMiracastMode(anyInt());
    }

    /**
     * Verify the caller sends WifiP2pManager.FACTORY_RESET when p2p is enabled.
     */
    @Test
    public void testFactoryResetSuccessWhenP2pEnabled() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);

        when(mWifiInjector.getUserManager()).thenReturn(mUserManager);
        when(mPackageManager.getNameForUid(anyInt())).thenReturn("testPkg");
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_NETWORK_RESET), any()))
                .thenReturn(false);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_CONFIG_WIFI), any()))
                .thenReturn(false);
        when(mWifiNative.p2pListNetworks(any())).thenReturn(true);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.FACTORY_RESET);
        checkSendP2pPersistentGroupsChangedBroadcast();
        if (SdkLevel.isAtLeastT()) {
            verify(mP2pListener, atLeastOnce()).onPersistentGroupsChanged(any());
        }
        verify(mWifiInjector).getUserManager();
        verify(mPackageManager).getNameForUid(anyInt());
        verify(mWifiPermissionsUtil).checkNetworkSettingsPermission(anyInt());
        verify(mUserManager).hasUserRestrictionForUser(
                eq(UserManager.DISALLOW_NETWORK_RESET), any());
        verify(mUserManager).hasUserRestrictionForUser(eq(UserManager.DISALLOW_CONFIG_WIFI), any());
        verify(mWifiNative, atLeastOnce()).p2pListNetworks(any());
        verify(mWifiNative).teardownInterface();
        verify(mWifiSettingsConfigStore).put(eq(WIFI_P2P_PENDING_FACTORY_RESET), eq(false));
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.FACTORY_RESET_SUCCEEDED, message.what);
    }

    /**
     * Verify the caller sends WifiP2pManager.FACTORY_RESET when p2p is disabled at first
     * and changes to enabled.
     */
    @Test
    public void testFactoryResetSuccessWhenP2pFromDisabledToEnabled() throws Exception {
        when(mWifiInjector.getUserManager()).thenReturn(mUserManager);
        when(mPackageManager.getNameForUid(anyInt())).thenReturn("testPkg");
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_NETWORK_RESET), any()))
                .thenReturn(false);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_CONFIG_WIFI), any()))
                .thenReturn(false);
        when(mWifiNative.p2pListNetworks(any())).thenReturn(true);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.FACTORY_RESET);
        verify(mWifiInjector).getUserManager();
        verify(mPackageManager).getNameForUid(anyInt());
        verify(mWifiPermissionsUtil).checkNetworkSettingsPermission(anyInt());
        verify(mUserManager).hasUserRestrictionForUser(
                eq(UserManager.DISALLOW_NETWORK_RESET), any());
        verify(mUserManager).hasUserRestrictionForUser(eq(UserManager.DISALLOW_CONFIG_WIFI), any());
        verify(mWifiNative, never()).p2pListNetworks(any());
        verify(mWifiSettingsConfigStore).put(eq(WIFI_P2P_PENDING_FACTORY_RESET), eq(true));
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.FACTORY_RESET_SUCCEEDED, message.what);

        // Move to enabled state
        when(mWifiSettingsConfigStore.get(eq(WIFI_P2P_PENDING_FACTORY_RESET))).thenReturn(true);
        forceP2pEnabled(mClient1);
        verify(mPackageManager, times(2)).getNameForUid(anyInt());
        verify(mWifiPermissionsUtil, times(2)).checkNetworkSettingsPermission(anyInt());
        verify(mUserManager, times(2)).hasUserRestrictionForUser(
                eq(UserManager.DISALLOW_NETWORK_RESET), any());
        verify(mUserManager, times(2)).hasUserRestrictionForUser(
                eq(UserManager.DISALLOW_CONFIG_WIFI), any());
        verify(mWifiNative, atLeastOnce()).p2pListNetworks(any());
        verify(mWifiSettingsConfigStore).get(eq(WIFI_P2P_PENDING_FACTORY_RESET));
        verify(mWifiSettingsConfigStore).put(eq(WIFI_P2P_PENDING_FACTORY_RESET), eq(false));
        checkSendP2pPersistentGroupsChangedBroadcast();
    }

    /**
     * Verify WifiP2pManager.FACTORY_RESET_FAILED is returned without network setting permission.
     */
    @Test
    public void testFactoryResetFailureWithoutNetworkSettingPermission() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);

        when(mWifiInjector.getUserManager()).thenReturn(mUserManager);
        when(mPackageManager.getNameForUid(anyInt())).thenReturn("testPkg");
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.FACTORY_RESET);
        verify(mWifiInjector).getUserManager();
        verify(mPackageManager).getNameForUid(anyInt());
        verify(mWifiPermissionsUtil).checkNetworkSettingsPermission(anyInt());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.FACTORY_RESET_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify WifiP2pManager.FACTORY_RESET_FAILED is returned when network reset disallow.
     */
    @Test
    public void testFactoryResetFailureWhenNetworkResetDisallow() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);

        when(mWifiInjector.getUserManager()).thenReturn(mUserManager);
        when(mPackageManager.getNameForUid(anyInt())).thenReturn("testPkg");
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_NETWORK_RESET), any()))
                .thenReturn(true);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.FACTORY_RESET);
        verify(mWifiInjector).getUserManager();
        verify(mPackageManager).getNameForUid(anyInt());
        verify(mWifiPermissionsUtil).checkNetworkSettingsPermission(anyInt());
        verify(mUserManager).hasUserRestrictionForUser(
                eq(UserManager.DISALLOW_NETWORK_RESET), any());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.FACTORY_RESET_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify WifiP2pManager.FACTORY_RESET_FAILED is returned when config wifi disallow.
     */
    @Test
    public void testFactoryResetFailureWhenConfigWifiDisallow() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);

        when(mWifiInjector.getUserManager()).thenReturn(mUserManager);
        when(mPackageManager.getNameForUid(anyInt())).thenReturn(TEST_PACKAGE_NAME);
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_NETWORK_RESET), any()))
                .thenReturn(false);
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_CONFIG_WIFI), any()))
                .thenReturn(true);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.FACTORY_RESET);
        verify(mWifiInjector).getUserManager();
        verify(mPackageManager).getNameForUid(anyInt());
        verify(mWifiPermissionsUtil).checkNetworkSettingsPermission(anyInt());
        verify(mUserManager).hasUserRestrictionForUser(
                eq(UserManager.DISALLOW_NETWORK_RESET), any());
        verify(mUserManager).hasUserRestrictionForUser(eq(UserManager.DISALLOW_CONFIG_WIFI), any());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.FACTORY_RESET_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify WifiP2pManager.FACTORY_RESET_FAILED is returned when p2p is unsupported.
     */
    @Test
    public void testFactoryResetFailureWhenP2pUnsupported() throws Exception {
        setUpWifiP2pServiceImpl(false);

        sendSimpleMsg(mClientMessenger, WifiP2pManager.FACTORY_RESET);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.FACTORY_RESET_FAILED, message.what);
        assertEquals(WifiP2pManager.P2P_UNSUPPORTED, message.arg1);
    }

    /**
     * Verify the caller sends WifiP2pManager.SET_ONGOING_PEER_CONFIG.
     */
    @Test
    public void testSetOngingPeerConfigSuccess() throws Exception {
        forceP2pEnabled(mClient1);
        mockPeersList();
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = mTestWifiP2pDevice.deviceAddress;

        when(mWifiPermissionsUtil.checkNetworkStackPermission(anyInt())).thenReturn(true);
        sendSetOngoingPeerConfigMsg(mClientMessenger, config);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_ONGOING_PEER_CONFIG_SUCCEEDED, message.what);
    }

    /**
     * Verify WifiP2pManager.SET_ONGOING_PEER_CONFIG_FAILED is returned without NETWORK_STACK
     * permission.
     */
    @Test
    public void testSetOngingPeerConfigFailureWithoutPermission() throws Exception {
        forceP2pEnabled(mClient1);
        mockPeersList();
        WifiP2pConfig config = new WifiP2pConfig();

        when(mWifiPermissionsUtil.checkNetworkStackPermission(anyInt())).thenReturn(false);
        sendSetOngoingPeerConfigMsg(mClientMessenger, config);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_ONGOING_PEER_CONFIG_FAILED, message.what);
    }

    /**
     * Verify WifiP2pManager.SET_ONGOING_PEER_CONFIG_FAILED is returned with invalid peer config.
     */
    @Test
    public void testSetOngoingPeerConfigFailureWithInvalidPeerConfig() throws Exception {
        forceP2pEnabled(mClient1);
        mockPeersList();
        WifiP2pConfig config = new WifiP2pConfig();

        when(mWifiPermissionsUtil.checkNetworkStackPermission(anyInt())).thenReturn(true);
        sendSetOngoingPeerConfigMsg(mClientMessenger, config);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_ONGOING_PEER_CONFIG_FAILED, message.what);
    }

    /**
     * Verify that respond with RESPONSE_ONGOING_PEER_CONFIG
     * when caller sends REQUEST_ONGOING_PEER_CONFIG and permission is granted.
     */
    @Test
    public void testRequestOngoingPeerConfigSuccess() throws Exception {
        forceP2pEnabled(mClient1);

        when(mWifiPermissionsUtil.checkNetworkStackPermission(anyInt())).thenReturn(true);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_ONGOING_PEER_CONFIG);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        WifiP2pConfig config = (WifiP2pConfig) message.obj;
        assertEquals(WifiP2pManager.RESPONSE_ONGOING_PEER_CONFIG, message.what);
        assertNotNull(config);
    }

    /**
     * Verify that respond with RESPONSE_ONGOING_PEER_CONFIG
     * when caller sends REQUEST_ONGOING_PEER_CONFIG and has no NETWORK_STACK permission.
     */
    @Test
    public void testRequestOngoingPeerConfigFailureWithoutPermission() throws Exception {
        forceP2pEnabled(mClient1);

        when(mWifiPermissionsUtil.checkNetworkStackPermission(anyInt())).thenReturn(false);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_ONGOING_PEER_CONFIG);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        WifiP2pConfig config = (WifiP2pConfig) message.obj;
        assertEquals(WifiP2pManager.RESPONSE_ONGOING_PEER_CONFIG, message.what);
        assertNull(config);
    }

    /**
     * Verify that respond with RESPONSE_PERSISTENT_GROUP_INFO
     * when caller sends REQUEST_PERSISTENT_GROUP_INFO.
     */
    @Test
    public void testRequestPersistentGroupInfoSuccess() throws Exception {
        // Ensure our own MAC address is not anonymized in the result
        when(mWifiPermissionsUtil.checkLocalMacAddressPermission(anyInt())).thenReturn(true);
        forceP2pEnabled(mClient1);

        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_PERSISTENT_GROUP_INFO);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        WifiP2pGroupList groups = (WifiP2pGroupList) message.obj;
        assertEquals(WifiP2pManager.RESPONSE_PERSISTENT_GROUP_INFO, message.what);
        // WifiP2pGroupList does not implement equals operator,
        // use toString to compare two lists.
        assertEquals(mGroups.toString(), groups.toString());
    }

    /**
     * Verify that when no permissions are held, an empty {@link WifiP2pGroupList} is returned.
     */
    @Test
    public void testRequestPersistentGroupInfoNoPermissionFailure() throws Exception {
        // Ensure our own MAC address is not anonymized in the result
        forceP2pEnabled(mClient1);

        // no permissions held
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkNetworkStackPermission(anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkReadWifiCredentialPermission(anyInt())).thenReturn(false);

        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_PERSISTENT_GROUP_INFO);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        WifiP2pGroupList groups = (WifiP2pGroupList) message.obj;
        assertEquals(WifiP2pManager.RESPONSE_PERSISTENT_GROUP_INFO, message.what);
        // WifiP2pGroupList does not implement equals operator,
        // use toString to compare two lists.
        // Expect empty WifiP2pGroupList()
        assertEquals(new WifiP2pGroupList().toString(), groups.toString());
    }

    /**
     * Verify that respond with RESPONSE_PERSISTENT_GROUP_INFO
     * when caller sends REQUEST_PERSISTENT_GROUP_INFO without LOCATION_FINE permission.
     */
    @Test
    public void testRequestPersistentGroupInfoNoLocationFinePermission() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        // Ensure our own MAC address is not anonymized in the result
        when(mWifiPermissionsUtil.checkLocalMacAddressPermission(anyInt())).thenReturn(true);
        when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                .thenReturn(false);
        when(mWifiPermissionsUtil.isTargetSdkLessThan(any(),
                eq(Build.VERSION_CODES.TIRAMISU), anyInt())).thenReturn(false);
        when(mWifiPermissionsUtil.checkCallersLocationPermission(
                anyString(), anyString(), anyInt(), anyBoolean(), any())).thenReturn(false);
        forceP2pEnabled(mClient1);

        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_PERSISTENT_GROUP_INFO);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        WifiP2pGroupList groups = (WifiP2pGroupList) message.obj;
        assertEquals(WifiP2pManager.RESPONSE_PERSISTENT_GROUP_INFO, message.what);
        // WifiP2pGroupList does not implement equals operator,
        // use toString to compare two lists.
        // Expect empty WifiP2pGroupList()
        assertEquals(new WifiP2pGroupList().toString(), groups.toString());
    }

    /**
     * Verify that respond with RESPONSE_CONNECTION_INFO
     * when caller sends REQUEST_CONNECTION_INFO.
     */
    @Test
    public void testRequestConnectionInfoSuccess() throws Exception {
        forceP2pEnabled(mClient1);

        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_CONNECTION_INFO);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        WifiP2pInfo info = (WifiP2pInfo) message.obj;
        assertEquals(WifiP2pManager.RESPONSE_CONNECTION_INFO, message.what);
        // WifiP2pInfo does not implement equals operator,
        // use toString to compare two objects.
        assertEquals((new WifiP2pInfo()).toString(), info.toString());
    }

    /**
     * Verify that respond with RESPONSE_P2P_STATE
     * when caller sends REQUEST_P2P_STATE and p2p is enabled.
     */
    @Test
    public void testRequestP2pStateEnabled() throws Exception {
        forceP2pEnabled(mClient1);

        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_P2P_STATE);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.RESPONSE_P2P_STATE, message.what);
        assertEquals(WifiP2pManager.WIFI_P2P_STATE_ENABLED, message.arg1);
    }

    /**
     * Verify that respond with RESPONSE_P2P_STATE
     * when caller sends REQUEST_P2P_STATE and p2p is disabled.
     */
    @Test
    public void testRequestP2pStateDisabled() throws Exception {
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_P2P_STATE);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.RESPONSE_P2P_STATE, message.what);
        assertEquals(WifiP2pManager.WIFI_P2P_STATE_DISABLED, message.arg1);
    }

    /**
     * Verify that respond with RESPONSE_DISCOVERY_STATE
     * when caller sends REQUEST_DISCOVERY_STATE and discovery is started.
     */
    @Test
    public void testRequestDiscoveryStateWhenStarted() throws Exception {
        forceP2pEnabled(mClient1);
        when(mWifiPermissionsUtil.checkCanAccessWifiDirect(any(), any(),
                anyInt(), anyBoolean())).thenReturn(false);
        when(mWifiPermissionsUtil.checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME), eq("testFeature"),
                anyInt(), anyBoolean())).thenReturn(true);
        when(mWifiNative.p2pFind(anyInt())).thenReturn(true);
        sendDiscoverPeersMsg(mClientMessenger);
        verify(mWifiNative).p2pFind(anyInt());
        verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                eq(TEST_PACKAGE_NAME), eq("testFeature"), anyInt(), eq(true));

        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DISCOVERY_STATE);

        // there are 2 responses:
        // * WifiP2pManager.DISCOVER_PEERS_SUCCEEDED
        // * WifiP2pManager.RESPONSE_DISCOVERY_STATE
        verify(mClientHandler, times(2)).sendMessage(mMessageCaptor.capture());
        List<Message> messages = mMessageCaptor.getAllValues();
        assertEquals(WifiP2pManager.DISCOVER_PEERS_SUCCEEDED, messages.get(0).what);
        assertEquals(WifiP2pManager.RESPONSE_DISCOVERY_STATE, messages.get(1).what);
        assertEquals(WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED, messages.get(1).arg1);
    }

    /**
     * Verify that respond with RESPONSE_DISCOVERY_STATE
     * when caller sends REQUEST_DISCOVERY_STATE and discovery is stopped.
     */
    @Test
    public void testRequestDiscoveryStateWhenStopped() throws Exception {
        forceP2pEnabled(mClient1);

        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DISCOVERY_STATE);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.RESPONSE_DISCOVERY_STATE, message.what);
        assertEquals(WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED, message.arg1);
    }

    @Test
    public void testGetListenStateWhenStarted() throws Exception {
        forceP2pEnabled(mClient1);
        when(mWifiPermissionsUtil.checkCanAccessWifiDirect(anyString(), anyString(), anyInt(),
                anyBoolean())).thenReturn(false);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.GET_LISTEN_STATE);
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.GET_LISTEN_STATE_FAILED));

        when(mWifiPermissionsUtil.checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME), eq("testFeature"),
                anyInt(), anyBoolean())).thenReturn(true);
        when(mWifiNative.p2pExtListen(anyBoolean(), anyInt(), anyInt(), any())).thenReturn(true);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.START_LISTEN);
        verify(mWifiNative).p2pStopFind();
        sendSimpleMsg(mClientMessenger, WifiP2pManager.GET_LISTEN_STATE);

        sendSimpleMsg(mClientMessenger, WifiP2pManager.STOP_LISTEN);
        verify(mWifiNative, times(2)).p2pExtListen(anyBoolean(), anyInt(), anyInt(), eq(null));
        sendSimpleMsg(mClientMessenger, WifiP2pManager.GET_LISTEN_STATE);

        verify(mClientHandler, times(5)).sendMessage(mMessageCaptor.capture());
        List<Message> messages = mMessageCaptor.getAllValues();
        assertEquals(WifiP2pManager.GET_LISTEN_STATE_FAILED, messages.get(0).what);
        assertEquals(WifiP2pManager.START_LISTEN_SUCCEEDED, messages.get(1).what);
        assertEquals(WifiP2pManager.RESPONSE_GET_LISTEN_STATE, messages.get(2).what);
        assertEquals(WifiP2pManager.WIFI_P2P_LISTEN_STARTED, messages.get(2).arg1);

        assertEquals(WifiP2pManager.STOP_LISTEN_SUCCEEDED, messages.get(3).what);
        assertEquals(WifiP2pManager.RESPONSE_GET_LISTEN_STATE, messages.get(4).what);
        assertEquals(WifiP2pManager.WIFI_P2P_LISTEN_STOPPED, messages.get(4).arg1);
    }

    @Test
    public void testGetListenStateWhenStopped() throws Exception {
        forceP2pEnabled(mClient1);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.GET_LISTEN_STATE);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.RESPONSE_GET_LISTEN_STATE, message.what);
        assertEquals(WifiP2pManager.WIFI_P2P_LISTEN_STOPPED, message.arg1);
    }

    /**
     * Verify that respond with RESPONSE_NETWORK_INFO
     * when caller sends REQUEST_NETWORK_INFO.
     */
    @Test
    public void testRequestNetworkInfoSuccess() throws Exception {
        NetworkInfo info_gold =
                new NetworkInfo(ConnectivityManager.TYPE_WIFI_P2P, 0, "WIFI_P2P", "");

        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_NETWORK_INFO);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        NetworkInfo info = (NetworkInfo) message.obj;
        assertEquals(WifiP2pManager.RESPONSE_NETWORK_INFO, message.what);
        assertEquals(info_gold.toString(), info.toString());
    }

    /**
     * Verify the caller sends WifiP2pManager.REMOVE_LOCAL_SERVICE.
     */
    @Test
    public void testRemoveLocalServiceSuccess() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        verifyAddLocalService();

        sendRemoveLocalServiceMsg(mClientMessenger, mTestWifiP2pServiceInfo);
        verify(mWifiNative).p2pServiceDel(any(WifiP2pServiceInfo.class));

        assertTrue(mClientHandler.hasMessages(WifiP2pManager.REMOVE_LOCAL_SERVICE_SUCCEEDED));
    }

    /**
     * Verify the caller sends WifiP2pManager.REMOVE_LOCAL_SERVICE without client info.
     */
    @Test
    public void testRemoveLocalServiceFailureWithoutClientInfo() throws Exception {
        forceP2pEnabledWithoutClientInfo();

        sendRemoveLocalServiceMsg(mClientMessenger, mTestWifiP2pServiceInfo);
        verify(mWifiNative, never()).p2pServiceDel(any(WifiP2pServiceInfo.class));

        assertTrue(mClientHandler.hasMessages(WifiP2pManager.REMOVE_LOCAL_SERVICE_FAILED));
    }

    /**
     * Verify the caller sends WifiP2pManager.REMOVE_LOCAL_SERVICE when service info is null.
     */
    @Test
    public void testRemoveLocalServiceSuccessWithNullServiceInfo() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        verifyAddLocalService();

        sendRemoveLocalServiceMsg(mClientMessenger, null);
        verify(mWifiNative, never()).p2pServiceDel(any(WifiP2pServiceInfo.class));

        assertTrue(mClientHandler.hasMessages(WifiP2pManager.REMOVE_LOCAL_SERVICE_SUCCEEDED));
    }

    /**
     * Verify that respond with REMOVE_LOCAL_SERVICE_FAILED
     * when caller sends REMOVE_LOCAL_SERVICE and p2p is disabled.
     */
    @Test
    public void testRemoveLocalServiceFailureWhenP2pDisabled() throws Exception {
        sendRemoveLocalServiceMsg(mClientMessenger, null);
        verify(mWifiNative, never()).p2pServiceDel(any(WifiP2pServiceInfo.class));

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_LOCAL_SERVICE_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     * Verify that respond with REMOVE_LOCAL_SERVICE_FAILED
     * when caller sends REMOVE_LOCAL_SERVICE and p2p is unsupported.
     */
    @Test
    public void testRemoveLocalServiceFailureWhenP2pUnsupported() throws Exception {
        setUpWifiP2pServiceImpl(false);

        sendRemoveLocalServiceMsg(mClientMessenger, null);
        verify(mWifiNative, never()).p2pServiceDel(any(WifiP2pServiceInfo.class));

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_LOCAL_SERVICE_FAILED, message.what);
        assertEquals(WifiP2pManager.P2P_UNSUPPORTED, message.arg1);
    }

    /**
     * Verify the caller sends WifiP2pManager.CLEAR_LOCAL_SERVICES.
     */
    @Test
    public void testClearLocalServiceSuccess() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        verifyAddLocalService();

        sendSimpleMsg(mClientMessenger, WifiP2pManager.CLEAR_LOCAL_SERVICES);
        verify(mWifiNative, atLeastOnce()).p2pServiceDel(any(WifiP2pServiceInfo.class));

        assertTrue(mClientHandler.hasMessages(WifiP2pManager.CLEAR_LOCAL_SERVICES_SUCCEEDED));
    }

    /**
     * Verify the caller sends WifiP2pManager.CLEAR_LOCAL_SERVICES without client info.
     */
    @Test
    public void testClearLocalServiceFailureWithoutClientInfo() throws Exception {
        forceP2pEnabledWithoutClientInfo();

        sendSimpleMsg(mClientMessenger, WifiP2pManager.CLEAR_LOCAL_SERVICES);
        verify(mWifiNative, never()).p2pServiceDel(any(WifiP2pServiceInfo.class));

        assertTrue(mClientHandler.hasMessages(WifiP2pManager.CLEAR_LOCAL_SERVICES_FAILED));
    }

    /**
     * Verify that respond with CLEAR_LOCAL_SERVICES_FAILED
     * when caller sends CLEAR_LOCAL_SERVICES and p2p is disabled.
     */
    @Test
    public void testClearLocalServiceFailureWhenP2pDisabled() throws Exception {
        sendSimpleMsg(mClientMessenger, WifiP2pManager.CLEAR_LOCAL_SERVICES);
        verify(mWifiNative, never()).p2pServiceDel(any(WifiP2pServiceInfo.class));

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.CLEAR_LOCAL_SERVICES_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     * Verify that respond with CLEAR_LOCAL_SERVICES_FAILED
     * when caller sends CLEAR_LOCAL_SERVICES and p2p is unsupported.
     */
    @Test
    public void testClearLocalServiceFailureWhenP2pUnsupported() throws Exception {
        setUpWifiP2pServiceImpl(false);

        sendSimpleMsg(mClientMessenger, WifiP2pManager.CLEAR_LOCAL_SERVICES);
        verify(mWifiNative, never()).p2pServiceDel(any(WifiP2pServiceInfo.class));

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.CLEAR_LOCAL_SERVICES_FAILED, message.what);
        assertEquals(WifiP2pManager.P2P_UNSUPPORTED, message.arg1);
    }

    /**
     * Verify the caller sends WifiP2pManager.ADD_SERVICE_REQUEST without services discover.
     */
    @Test
    public void testAddServiceRequestNoOverflow() throws Exception {
        forceP2pEnabled(mClient1);

        for (int i = 0; i < 256; i++) {
            reset(mTestWifiP2pServiceRequest);
            sendAddServiceRequestMsg(mClientMessenger);
            ArgumentCaptor<Integer> idCaptor = ArgumentCaptor.forClass(int.class);
            verify(mTestWifiP2pServiceRequest).setTransactionId(idCaptor.capture());
            assertTrue(idCaptor.getValue().intValue() > 0);
        }
    }

    private void verifyAddServiceRequest() throws Exception {
        sendAddServiceRequestMsg(mClientMessenger);
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.ADD_SERVICE_REQUEST_SUCCEEDED));
    }

    /**
     * Verify the caller sends WifiP2pManager.ADD_SERVICE_REQUEST without services discover.
     */
    @Test
    public void testAddServiceRequestSuccessWithoutServiceDiscover() throws Exception {
        forceP2pEnabled(mClient1);
        verifyAddServiceRequest();
    }

    /**
     * Verify the caller sends WifiP2pManager.ADD_SERVICE_REQUEST with services discover.
     */
    @Test
    public void testAddServiceRequestSuccessWithServiceDiscover() throws Exception {
        testDiscoverServicesSuccess();

        sendAddServiceRequestMsg(mClientMessenger);
        verify(mWifiNative, atLeastOnce()).p2pServDiscReq(eq("00:00:00:00:00:00"), anyString());

        assertTrue(mClientHandler.hasMessages(WifiP2pManager.ADD_SERVICE_REQUEST_SUCCEEDED));
    }

    /**
     * Verify WifiP2pManager.ADD_SERVICE_REQUEST_FAILED is returned with null request.
     */
    @Test
    public void testAddServiceRequestFailureWithNullRequest() throws Exception {
        forceP2pEnabled(mClient1);

        sendSimpleMsg(mClientMessenger, WifiP2pManager.ADD_SERVICE_REQUEST, null);

        assertTrue(mClientHandler.hasMessages(WifiP2pManager.ADD_SERVICE_REQUEST_FAILED));
    }

    /**
     * Verify WifiP2pManager.ADD_SERVICE_REQUEST_FAILED is returned without client info.
     */
    @Test
    public void testAddServiceRequestFailureWithoutClientInfo() throws Exception {
        forceP2pEnabledWithoutClientInfo();

        sendAddServiceRequestMsg(mClientMessenger);

        assertTrue(mClientHandler.hasMessages(WifiP2pManager.ADD_SERVICE_REQUEST_FAILED));
    }

    /**
     * Verify that respond with ADD_SERVICE_REQUEST_FAILED
     * when caller sends ADD_SERVICE_REQUEST and p2p is disabled.
     */
    @Test
    public void testAddServiceRequestFailureWhenP2pDisabled() throws Exception {
        sendAddServiceRequestMsg(mClientMessenger);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.ADD_SERVICE_REQUEST_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     * Verify that respond with ADD_SERVICE_REQUEST_FAILED
     * when caller sends ADD_SERVICE_REQUEST and p2p is unsupported.
     */
    @Test
    public void testAddServiceRequestFailureWhenP2pUnsupported() throws Exception {
        setUpWifiP2pServiceImpl(false);

        sendAddServiceRequestMsg(mClientMessenger);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.ADD_SERVICE_REQUEST_FAILED, message.what);
        assertEquals(WifiP2pManager.P2P_UNSUPPORTED, message.arg1);
    }

    /**
     * Verify the caller sends WifiP2pManager.REMOVE_SERVICE_REQUEST.
     */
    @Test
    public void testRemoveServiceRequestSuccess() throws Exception {
        forceP2pEnabled(mClient1);
        verifyAddServiceRequest();

        sendRemoveServiceRequestMsg(mClientMessenger, mTestWifiP2pServiceRequest);

        assertTrue(mClientHandler.hasMessages(WifiP2pManager.REMOVE_SERVICE_REQUEST_SUCCEEDED));
    }

    /**
     * Verify the caller sends WifiP2pManager.REMOVE_SERVICE_REQUEST without client info.
     */
    @Test
    public void testRemoveServiceRequestFailureWithoutClientInfo() throws Exception {
        forceP2pEnabledWithoutClientInfo();

        sendRemoveServiceRequestMsg(mClientMessenger, mTestWifiP2pServiceRequest);

        assertTrue(mClientHandler.hasMessages(WifiP2pManager.REMOVE_SERVICE_REQUEST_FAILED));
    }

    /**
     * Verify the caller sends WifiP2pManager.REMOVE_SERVICE_REQUEST when service info is null.
     */
    @Test
    public void testRemoveServiceRequestSuccessWithNullServiceInfo() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);
        verifyAddLocalService();

        sendRemoveServiceRequestMsg(mClientMessenger, null);

        assertTrue(mClientHandler.hasMessages(WifiP2pManager.REMOVE_SERVICE_REQUEST_SUCCEEDED));
    }

    /**
     * Verify that respond with REMOVE_SERVICE_REQUEST_FAILED
     * when caller sends REMOVE_SERVICE_REQUEST and p2p is disabled.
     */
    @Test
    public void testRemoveServiceRequestFailureWhenP2pDisabled() throws Exception {
        sendRemoveServiceRequestMsg(mClientMessenger, null);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_SERVICE_REQUEST_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     * Verify that respond with REMOVE_SERVICE_REQUEST_FAILED
     * when caller sends REMOVE_SERVICE_REQUEST and p2p is unsupported.
     */
    @Test
    public void testRemoveServiceRequestFailureWhenP2pUnsupported() throws Exception {
        setUpWifiP2pServiceImpl(false);

        sendRemoveServiceRequestMsg(mClientMessenger, null);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_SERVICE_REQUEST_FAILED, message.what);
        assertEquals(WifiP2pManager.P2P_UNSUPPORTED, message.arg1);
    }

    /**
     * Verify the caller sends WifiP2pManager.CLEAR_SERVICE_REQUESTS.
     */
    @Test
    public void testClearServiceRequestsSuccess() throws Exception {
        forceP2pEnabled(mClient1);
        verifyAddServiceRequest();

        sendSimpleMsg(mClientMessenger, WifiP2pManager.CLEAR_SERVICE_REQUESTS);

        assertTrue(mClientHandler.hasMessages(WifiP2pManager.CLEAR_SERVICE_REQUESTS_SUCCEEDED));
    }

    /**
     * Verify the caller sends WifiP2pManager.CLEAR_SERVICE_REQUESTS without client info.
     */
    @Test
    public void testClearServiceRequestsFailureWithoutClientInfo() throws Exception {
        forceP2pEnabledWithoutClientInfo();

        sendSimpleMsg(mClientMessenger, WifiP2pManager.CLEAR_SERVICE_REQUESTS);

        assertTrue(mClientHandler.hasMessages(WifiP2pManager.CLEAR_SERVICE_REQUESTS_FAILED));
    }

    /**
     * Verify that respond with CLEAR_SERVICE_REQUESTS_FAILED
     * when caller sends CLEAR_SERVICE_REQUEST and p2p is disabled.
     */
    @Test
    public void testClearServiceRequestsFailureWhenP2pDisabled() throws Exception {
        sendSimpleMsg(mClientMessenger, WifiP2pManager.CLEAR_SERVICE_REQUESTS);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.CLEAR_SERVICE_REQUESTS_FAILED, message.what);
        assertEquals(WifiP2pManager.BUSY, message.arg1);
    }

    /**
     * Verify that respond with CLEAR_SERVICE_REQUESTS_FAILED
     * when caller sends CLEAR_SERVICE_REQUEST and p2p is unsupported.
     */
    @Test
    public void testClearServiceRequestsFailureWhenP2pUnsupported() throws Exception {
        setUpWifiP2pServiceImpl(false);

        sendSimpleMsg(mClientMessenger, WifiP2pManager.CLEAR_SERVICE_REQUESTS);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.CLEAR_SERVICE_REQUESTS_FAILED, message.what);
        assertEquals(WifiP2pManager.P2P_UNSUPPORTED, message.arg1);
    }

    /**
     * Verify when prior to Android T, stopping discovery is executed when location mode is
     * turned off.
     */
    @Test
    public void testStopDiscoveryWhenLocationModeIsDisabled() throws Exception {
        forceP2pEnabled(mClient1);
        simulateLocationModeChange(false);
        mLooper.dispatchAll();
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiNative, never()).p2pStopFind();
        } else {
            verify(mWifiNative).p2pStopFind();
        }
    }

    /**
     * Verify a network name which is too long is rejected.
     */
    @Test
    public void testSendConnectMsgWithTooLongNetworkName() throws Exception {
        mTestWifiP2pFastConnectionConfig.networkName = "DIRECT-xy-abcdefghijklmnopqrstuvw";
        sendConnectMsg(mClientMessenger, mTestWifiP2pFastConnectionConfig);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.CONNECT_FAILED, message.what);
    }

    /**
     * Verify a network name which is too short is rejected.
     */
    @Test
    public void testSendConnectMsgWithTooShortNetworkName() throws Exception {
        setTargetSdkGreaterThanT();
        mTestWifiP2pFastConnectionConfig.networkName = "DIRECT-x";
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(any(), eq(true))).thenReturn(true);
        sendConnectMsg(mClientMessenger, mTestWifiP2pFastConnectionConfig);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.CONNECT_FAILED, message.what);
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        }
    }

    private void verifyGroupOwnerIntentSelection(int netId, int freq, int expectedGoIntent)
            throws Exception {
        when(mWifiInfo.getNetworkId()).thenReturn(netId);
        when(mWifiInfo.getFrequency()).thenReturn(freq);
        forceP2pEnabled(mClient1);

        mockEnterProvisionDiscoveryState();

        WifiP2pProvDiscEvent pdEvent = new WifiP2pProvDiscEvent();
        pdEvent.device = mTestWifiP2pDevice;
        sendSimpleMsg(null,
                WifiP2pMonitor.P2P_PROV_DISC_PBC_RSP_EVENT,
                pdEvent);

        ArgumentCaptor<WifiP2pConfig> configCaptor =
                ArgumentCaptor.forClass(WifiP2pConfig.class);
        verify(mWifiNative).p2pConnect(configCaptor.capture(), anyBoolean());
        WifiP2pConfig config = configCaptor.getValue();
        assertEquals(expectedGoIntent, config.groupOwnerIntent);
    }

    /**
     * Verify the group owner intent value is selected correctly when there is no STA connection.
     */
    @Test
    public void testGroupOwnerIntentSelectionWithoutStaConnection() throws Exception {
        verifyGroupOwnerIntentSelection(WifiConfiguration.INVALID_NETWORK_ID, 2412,
                WifiP2pServiceImpl.DEFAULT_GROUP_OWNER_INTENT);
    }

    /**
     * Verify the group owner intent value is selected correctly when STA is connected in 2.4GHz
     */
    @Test
    public void testGroupOwnerIntentSelectionWith24GStaConnectionWith24g5gDbs() throws Exception {
        verifyGroupOwnerIntentSelection(1, 2412, 7);
    }

    /**
     * Verify the group owner intent value is selected correctly when STA is connected in 5GHz
     * without 5GHz/6GHz DBS support.
     */
    @Test
    public void testGroupOwnerIntentSelectionWith5GHzStaConnectionWithout5g6gDbs()
            throws Exception {
        verifyGroupOwnerIntentSelection(1, 5200, 8);
    }

    /**
     * Verify the group owner intent value is selected correctly when STA is connected in 5GHz
     * with 5GHz/6GHz DBS support.
     */
    @Test
    public void testGroupOwnerIntentSelectionWith5GHzStaConnectionWith24g5gDbs5g6gDbs()
            throws Exception {
        when(mWifiNative.is5g6gDbsSupported()).thenReturn(true);
        verifyGroupOwnerIntentSelection(1, 5200, 9);
    }

    /**
     * Verify the group owner intent value is selected correctly when STA is connected in 6GHz
     * without 5GHz/6GHz DBS support.
     */
    @Test
    public void testGroupOwnerIntentSelectionWith6GHzStaConnectionWithout5g6gDbs()
            throws Exception {
        verifyGroupOwnerIntentSelection(1, 6000, 11);
    }

    /**
     * Verify the group owner intent value is selected correctly when STA is connected in 6GHz
     * with 5GHz/6GHz DBS support.
     */
    @Test
    public void testGroupOwnerIntentSelectionWith6GHzStaConnectionWith5g6gDbs() throws Exception {
        when(mWifiNative.is5g6gDbsSupported()).thenReturn(true);
        verifyGroupOwnerIntentSelection(1, 6000, 12);
    }

    /**
     * Verify that a P2P_PROV_DISC_SHOW_PIN_EVENT for config method DISPLAY triggers an invitation
     * sent dialog with the correct PIN.
     */
    @Test
    public void testProvisionDiscoveryShowPinEventLaunchesInvitationSentDialog() throws Exception {
        when(mWifiInfo.getNetworkId()).thenReturn(WifiConfiguration.INVALID_NETWORK_ID);
        when(mWifiInfo.getFrequency()).thenReturn(2412);
        mTestWifiP2pPeerConfig.wps.setup = WpsInfo.DISPLAY;
        forceP2pEnabled(mClient1);

        mockEnterProvisionDiscoveryState();

        WifiP2pProvDiscEvent pdEvent = new WifiP2pProvDiscEvent();
        pdEvent.device = mTestWifiP2pDevice;
        pdEvent.pin = "pin";
        sendSimpleMsg(null,
                WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT,
                pdEvent);

        verify(mWifiNative).p2pConnect(any(), anyBoolean());
        if (!SdkLevel.isAtLeastT()) {
            verify(mAlertDialog).show();
        } else {
            verify(mWifiDialogManager).createP2pInvitationSentDialog(
                    pdEvent.device.deviceName, pdEvent.pin, Display.DEFAULT_DISPLAY);
            verify(mDialogHandle).launchDialog();
        }
    }

    /**
     * Verify that a P2P_PROV_DISC_SHOW_PIN_EVENT for config method DISPLAY triggers an invitation
     * sent dialog with the correct PIN.
     */
    @Test
    public void testProvisionDiscoveryShowPinEventInactiveStateLaunchesInvitationReceivedDialog()
            throws Exception {
        when(mWifiInfo.getNetworkId()).thenReturn(WifiConfiguration.INVALID_NETWORK_ID);
        when(mWifiInfo.getFrequency()).thenReturn(2412);
        mTestWifiP2pPeerConfig.wps.setup = WpsInfo.DISPLAY;
        forceP2pEnabled(mClient1);

        sendDeviceFoundEventMsg(mTestWifiP2pDevice);
        WifiP2pProvDiscEvent pdEvent = new WifiP2pProvDiscEvent();
        pdEvent.device = mTestWifiP2pDevice;
        pdEvent.pin = "pin";
        sendSimpleMsg(null,
                WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT,
                pdEvent);

        if (!SdkLevel.isAtLeastT()) {
            verify(mAlertDialog).show();
        } else {
            verify(mWifiDialogManager).createP2pInvitationReceivedDialog(
                    eq(pdEvent.device.deviceName), eq(false), eq(pdEvent.pin),
                    anyInt(), any(), any());
            verify(mDialogHandle).launchDialog();
        }
    }

    private List<CoexUnsafeChannel> setupCoexMock(int restrictionBits) {
        assumeTrue(SdkLevel.isAtLeastS());
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        unsafeChannels.add(new CoexUnsafeChannel(WifiScanner.WIFI_BAND_24_GHZ, 1));
        unsafeChannels.add(new CoexUnsafeChannel(WifiScanner.WIFI_BAND_24_GHZ, 2));
        unsafeChannels.add(new CoexUnsafeChannel(WifiScanner.WIFI_BAND_24_GHZ, 3));
        unsafeChannels.add(new CoexUnsafeChannel(WifiScanner.WIFI_BAND_5_GHZ, 36));
        unsafeChannels.add(new CoexUnsafeChannel(WifiScanner.WIFI_BAND_5_GHZ, 40));
        unsafeChannels.add(new CoexUnsafeChannel(WifiScanner.WIFI_BAND_5_GHZ, 165));
        when(mCoexManager.getCoexRestrictions()).thenReturn(restrictionBits);
        when(mCoexManager.getCoexUnsafeChannels()).thenReturn(unsafeChannels);
        when(mWifiNative.p2pSetListenChannel(anyInt())).thenReturn(true);
        when(mWifiNative.p2pSetOperatingChannel(anyInt(), any())).thenReturn(true);
        return unsafeChannels;
    }

    /** Verify P2P unsafe channels are set if P2P bit presents in restriction bits. */
    @Test
    public void testCoexCallbackWithWifiP2pUnsafeChannels() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        setupCoexMock(0);
        assertNotNull(mCoexListener);
        forceP2pEnabled(mClient1);
        mLooper.dispatchAll();

        List<CoexUnsafeChannel> unsafeChannels =
                setupCoexMock(WifiManager.COEX_RESTRICTION_WIFI_DIRECT);
        mCoexListener.onCoexUnsafeChannelsChanged();
        mLooper.dispatchAll();

        // On entering P2pEnabledState, these are called once first.
        verify(mWifiNative, times(2)).p2pSetListenChannel(eq(0));
        ArgumentCaptor<List<CoexUnsafeChannel>> unsafeChannelsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mWifiNative, times(2)).p2pSetOperatingChannel(eq(0), unsafeChannelsCaptor.capture());
        List<List<CoexUnsafeChannel>> capturedUnsafeChannelsList =
                unsafeChannelsCaptor.getAllValues();
        // The second one is what we sent.
        assertEquals(unsafeChannels, capturedUnsafeChannelsList.get(1));
    }

    /** Verify P2P unsafe channels are cleared if P2P bit does not present in restriction bits. */
    @Test
    public void testCoexCallbackWithoutWifiP2pInRestrictionBits() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        setupCoexMock(0);
        assertNotNull(mCoexListener);
        forceP2pEnabled(mClient1);
        mLooper.dispatchAll();

        mCoexListener.onCoexUnsafeChannelsChanged();
        mLooper.dispatchAll();

        // On entering P2pEnabledState, these are called once first.
        verify(mWifiNative, times(2)).p2pSetListenChannel(eq(0));
        ArgumentCaptor<List<CoexUnsafeChannel>> unsafeChannelsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mWifiNative, times(2)).p2pSetOperatingChannel(eq(0), unsafeChannelsCaptor.capture());
        List<List<CoexUnsafeChannel>> capturedUnsafeChannelsList =
                unsafeChannelsCaptor.getAllValues();
        // The second one is what we sent.
        assertEquals(0, capturedUnsafeChannelsList.get(1).size());
    }

    /**
     * Verify the caller sends WifiP2pManager.SET_WFD_INFO with wfd enabled
     * and WFD R2 device info.
     */
    @Test
    public void testSetWfdR2InfoSuccessWithWfdEnabled() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        mTestThisDevice.wfdInfo = new WifiP2pWfdInfo();
        mTestThisDevice.wfdInfo.setEnabled(true);
        mTestThisDevice.wfdInfo.setR2DeviceType(WifiP2pWfdInfo.DEVICE_TYPE_WFD_SOURCE);
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiNative.setWfdEnable(anyBoolean())).thenReturn(true);
        when(mWifiNative.setWfdDeviceInfo(anyString())).thenReturn(true);
        when(mWifiNative.setWfdR2DeviceInfo(anyString())).thenReturn(true);
        sendSetWfdInfoMsg(mClientMessenger, mTestThisDevice.wfdInfo);

        verify(mWifiInjector).getWifiPermissionsWrapper();
        verify(mWifiPermissionsWrapper).getUidPermission(
                eq(android.Manifest.permission.CONFIGURE_WIFI_DISPLAY), anyInt());
        verify(mWifiNative).setWfdEnable(eq(true));
        verify(mWifiNative).setWfdDeviceInfo(eq(mTestThisDevice.wfdInfo.getDeviceInfoHex()));
        verify(mWifiNative).setWfdR2DeviceInfo(eq(mTestThisDevice.wfdInfo.getR2DeviceInfoHex()));
        checkSendThisDeviceChangedBroadcast();
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_WFD_INFO_SUCCEEDED, message.what);
    }

    /**
     * Verify WifiP2pManager.SET_WFD_INFO_FAILED is returned when wfd is enabled,
     * WFD R2 device, and native call "setWfdR2DeviceInfo" failure.
     */
    @Test
    public void testSetWfdR2InfoFailureWithWfdEnabledWhenNativeCallFailure2() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        mTestThisDevice.wfdInfo = new WifiP2pWfdInfo();
        mTestThisDevice.wfdInfo.setEnabled(true);
        mTestThisDevice.wfdInfo.setR2DeviceType(WifiP2pWfdInfo.DEVICE_TYPE_WFD_SOURCE);
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiNative.setWfdEnable(anyBoolean())).thenReturn(true);
        when(mWifiNative.setWfdDeviceInfo(anyString())).thenReturn(true);
        when(mWifiNative.setWfdR2DeviceInfo(anyString())).thenReturn(false);
        sendSetWfdInfoMsg(mClientMessenger, mTestThisDevice.wfdInfo);

        verify(mWifiInjector).getWifiPermissionsWrapper();
        verify(mWifiPermissionsWrapper).getUidPermission(
                eq(android.Manifest.permission.CONFIGURE_WIFI_DISPLAY), anyInt());
        verify(mWifiNative).setWfdEnable(eq(true));
        verify(mWifiNative).setWfdDeviceInfo(eq(mTestThisDevice.wfdInfo.getDeviceInfoHex()));
        verify(mWifiNative).setWfdR2DeviceInfo(eq(mTestThisDevice.wfdInfo.getR2DeviceInfoHex()));
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.SET_WFD_INFO_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     * Verify that P2P group is removed during group creating failure.
     */
    @Test
    public void testGroupCreatingFailureDueToTethering() throws Exception {
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);
        when(mWifiNative.p2pGroupRemove(eq(IFACE_NAME_P2P))).thenReturn(true);
        when(mWifiPermissionsUtil.checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME), eq("testFeature"),
                anyInt(), anyBoolean())).thenReturn(true);

        WifiP2pGroup group = new WifiP2pGroup();
        group.setNetworkId(WifiP2pGroup.NETWORK_ID_PERSISTENT);
        group.setNetworkName(TEST_NETWORK_NAME);
        group.setOwner(new WifiP2pDevice("thisDeviceMac"));
        group.setIsGroupOwner(true);
        group.setInterface(IFACE_NAME_P2P);

        forceP2pEnabled(mClient1);
        mLooper.dispatchAll();
        sendCreateGroupMsg(mClientMessenger, WifiP2pGroup.NETWORK_ID_TEMPORARY, null);
        mLooper.dispatchAll();

        sendGroupStartedMsg(group);
        mLooper.dispatchAll();

        mLooper.moveTimeForward(120 * 1000 * 2);
        mLooper.dispatchAll();

        verify(mWifiNative).p2pGroupRemove(group.getInterface());
    }

    /**
     * Verify the idle timer is cancelled after leaving inactive state.
     */
    @Test
    public void testIdleTimeoutCancelledAfterLeavingInactiveState() throws Exception {
        setTargetSdkGreaterThanT();
        forceP2pEnabled(mClient1);

        mockPeersList();
        sendConnectMsg(mClientMessenger, mTestWifiP2pPeerConfig);
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiPermissionsUtil, atLeastOnce()).checkNearbyDevicesPermission(
                    any(), eq(true), any());
            verify(mWifiPermissionsUtil, never()).checkCanAccessWifiDirect(
                    any(), any(), anyInt(), anyBoolean());
        } else {
            verify(mWifiPermissionsUtil).checkCanAccessWifiDirect(eq(TEST_PACKAGE_NAME),
                    eq("testFeature"), anyInt(), eq(false));
        }

        ArgumentCaptor<WifiP2pConfig> configCaptor =
                ArgumentCaptor.forClass(WifiP2pConfig.class);
        verify(mWifiP2pMetrics).startConnectionEvent(
                eq(P2pConnectionEvent.CONNECTION_FRESH),
                configCaptor.capture(),
                eq(WifiMetricsProto.GroupEvent.GROUP_UNKNOWN),
                eq(mClient1.getCallingUid()));
        assertEquals(mTestWifiP2pPeerConfig.toString(), configCaptor.getValue().toString());
        // Verify timer is cannelled
        // Includes re-schedule 3 times:
        // 1. forceP2pEnabled(): enter InactiveState
        // 2. CONNECT
        // 3. leave InactiveState
        verify(mAlarmManager, times(3)).setExact(anyInt(), anyLong(),
                eq(mWifiP2pServiceImpl.P2P_IDLE_SHUTDOWN_MESSAGE_TIMEOUT_TAG), any(), any());
        verify(mAlarmManager, times(3)).cancel(eq(mWifiP2pServiceImpl.mP2pIdleShutdownMessage));
    }

    /**
     * Verify the interface down after idle timer is triggered.
     */
    @Test
    public void testIdleTimeoutTriggered() throws Exception {
        forceP2pEnabled(mClient1);
        mWifiP2pServiceImpl.mP2pIdleShutdownMessage.onAlarm();
        mLooper.dispatchAll();
        verify(mWifiNative).teardownInterface();
        verify(mWifiMonitor).stopMonitoring(anyString());
    }

    /**
     * Verify the WFD info is set again on going back to P2pEnabledState
     * for the IdleShutdown case.
     */
    @Test
    public void testWfdInfoIsSetAtP2pEnabledStateForIdleShutdown() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        mTestThisDevice.wfdInfo = new WifiP2pWfdInfo();
        mTestThisDevice.wfdInfo.setEnabled(true);
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiNative.setWfdEnable(anyBoolean())).thenReturn(true);
        when(mWifiNative.setWfdDeviceInfo(anyString())).thenReturn(true);
        sendSetWfdInfoMsg(mClientMessenger, mTestThisDevice.wfdInfo);

        // P2P is off due to IDLE and data should be kept for the resume.
        mWifiP2pServiceImpl.mP2pIdleShutdownMessage.onAlarm();
        mLooper.dispatchAll();
        verify(mWifiNative).teardownInterface();
        verify(mWifiMonitor).stopMonitoring(anyString());
        sendSimpleMsg(null, WifiP2pMonitor.SUP_DISCONNECTION_EVENT);

        reset(mWifiNative);
        when(mWifiNative.setupInterface(any(), any(), any())).thenReturn(IFACE_NAME_P2P);
        when(mWifiNative.setWfdEnable(anyBoolean())).thenReturn(true);
        when(mWifiNative.setWfdDeviceInfo(anyString())).thenReturn(true);

        // send a command to resume P2P
        sendSimpleMsg(mClientMessenger, WifiP2pManager.DISCOVER_PEERS);

        // Restore data for resuming from idle shutdown.
        verify(mWifiNative).setWfdEnable(eq(true));
        verify(mWifiNative).setWfdDeviceInfo(eq(mTestThisDevice.wfdInfo.getDeviceInfoHex()));
    }

    /**
     * Verify the WFD info is set again on going back to P2pEnabledState
     * for the normal shutdown case.
     */
    @Test
    public void testWfdInfoIsSetAtP2pEnabledStateForNormalShutdown() throws Exception {
        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        mTestThisDevice.wfdInfo = new WifiP2pWfdInfo();
        mTestThisDevice.wfdInfo.setEnabled(true);
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiNative.setWfdEnable(anyBoolean())).thenReturn(true);
        when(mWifiNative.setWfdDeviceInfo(anyString())).thenReturn(true);
        sendSetWfdInfoMsg(mClientMessenger, mTestThisDevice.wfdInfo);

        // P2P is really disabled when wifi is off.
        simulateWifiStateChange(false);
        mLooper.dispatchAll();
        verify(mWifiNative).teardownInterface();
        verify(mWifiMonitor).stopMonitoring(anyString());

        reset(mWifiNative);
        when(mWifiNative.setupInterface(any(), any(), any())).thenReturn(IFACE_NAME_P2P);
        when(mWifiNative.setWfdEnable(anyBoolean())).thenReturn(true);
        when(mWifiNative.setWfdDeviceInfo(anyString())).thenReturn(true);

        // send a command to resume P2P
        sendSimpleMsg(mClientMessenger, WifiP2pManager.DISCOVER_PEERS);
        mLooper.dispatchAll();

        // In normal case, wfd info is cleared.
        verify(mWifiNative, never()).setWfdEnable(anyBoolean());
        verify(mWifiNative, never()).setWfdDeviceInfo(anyString());
    }

    /**
     * Verify the WFD info is set if WFD info is set at P2pDisabledState.
     */
    @Test
    public void testWfdInfoIsSetAtP2pEnabledWithPreSetWfdInfo() throws Exception {
        mTestThisDevice.wfdInfo = new WifiP2pWfdInfo();
        mTestThisDevice.wfdInfo.setEnabled(true);
        when(mWifiInjector.getWifiPermissionsWrapper()).thenReturn(mWifiPermissionsWrapper);
        when(mWifiPermissionsWrapper.getUidPermission(anyString(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mWifiNative.setWfdEnable(anyBoolean())).thenReturn(true);
        when(mWifiNative.setWfdDeviceInfo(anyString())).thenReturn(true);
        sendSetWfdInfoMsg(mClientMessenger, mTestThisDevice.wfdInfo);

        // At disabled state, WFD info is stored in the service, but not set actually.
        verify(mWifiNative, never()).setWfdEnable(anyBoolean());
        verify(mWifiNative, never()).setWfdDeviceInfo(any());

        // Move to enabled state
        forceP2pEnabled(mClient1);
        mTestThisDevice.status = mTestThisDevice.AVAILABLE;

        // Restore data for resuming from idle shutdown.
        verify(mWifiNative).setWfdEnable(eq(true));
        verify(mWifiNative).setWfdDeviceInfo(eq(mTestThisDevice.wfdInfo.getDeviceInfoHex()));
    }

    /**
     * Verify the frequency changed event handling.
     */
    @Test
    public void testP2pFrequencyChangedEventHandling() throws Exception {
        // Move to group created state
        forceP2pEnabled(mClient1);
        WifiP2pGroup group = new WifiP2pGroup();
        group.setNetworkId(WifiP2pGroup.NETWORK_ID_PERSISTENT);
        group.setNetworkName(TEST_NETWORK_NAME);
        group.setOwner(new WifiP2pDevice("thisDeviceMac"));
        group.setIsGroupOwner(true);
        group.setInterface(IFACE_NAME_P2P);
        sendGroupStartedMsg(group);
        simulateTetherReady();

        // Send Frequency changed event.
        sendSimpleMsg(null,
                WifiP2pMonitor.P2P_FREQUENCY_CHANGED_EVENT,
                TEST_GROUP_FREQUENCY);

        // send WifiP2pManager.REQUEST_GROUP_INFO and check the updated frequency.
        sendRequestGroupInfoMsg(mClientMessenger);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_GROUP_INFO, mMessageCaptor.getValue().what);
        WifiP2pGroup wifiP2pGroup = (WifiP2pGroup) mMessageCaptor.getValue().obj;
        assertEquals(TEST_GROUP_FREQUENCY, wifiP2pGroup.getFrequency());
    }

    /*
     * Verify the caller sends WifiP2pManager.REMOVE_CLIENT.
     */
    @Test
    public void testRemoveClientSuccess() throws Exception {
        when(mWifiNative.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_GROUP_CLIENT_REMOVAL);
        mockEnterGroupCreatedState();

        when(mWifiNative.removeClient(anyString())).thenReturn(true);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REMOVE_CLIENT, mTestWifiP2pPeerAddress);
        verify(mWifiNative).removeClient(anyString());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_CLIENT_SUCCEEDED, message.what);
    }

    /**
     *  Verify WifiP2pManager.REMOVE_CLIENT_FAILED is returned when native call failure.
     */
    @Test
    public void testRemoveClientFailureWhenNativeCallFailure() throws Exception {
        when(mWifiNative.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_GROUP_CLIENT_REMOVAL);
        mockEnterGroupCreatedState();

        when(mWifiNative.removeClient(anyString())).thenReturn(false);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REMOVE_CLIENT, mTestWifiP2pPeerAddress);
        verify(mWifiNative).removeClient(anyString());
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_CLIENT_FAILED, message.what);
        assertEquals(WifiP2pManager.ERROR, message.arg1);
    }

    /**
     *  Verify WifiP2pManager.REMOVE_CLIENT_SUCCEEDED is returned when p2p is creating group.
     */
    @Test
    public void testRemoveClientSuccessWhenP2pCreatingGroup() throws Exception {
        when(mWifiNative.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_GROUP_CLIENT_REMOVAL);
        // Move to group creating state
        testConnectWithConfigValidAsGroupSuccess();

        sendSimpleMsg(mClientMessenger, WifiP2pManager.REMOVE_CLIENT, mTestWifiP2pPeerAddress);
        verify(mClientHandler, atLeastOnce()).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_CLIENT_SUCCEEDED, message.what);
    }

    /**
     *  Verify WifiP2pManager.REMOVE_CLIENT_SUCCEEDED is returned when p2p is inactive.
     */
    @Test
    public void testRemoveClientSuccessWhenP2pInactive() throws Exception {
        when(mWifiNative.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_GROUP_CLIENT_REMOVAL);
        // Move to inactive state
        forceP2pEnabled(mClient1);

        sendSimpleMsg(mClientMessenger, WifiP2pManager.REMOVE_CLIENT, mTestWifiP2pPeerAddress);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_CLIENT_SUCCEEDED, message.what);
    }

    /**
     *  Verify WifiP2pManager.REMOVE_CLIENT_SUCCEEDED is returned when p2p is disabled.
     */
    @Test
    public void testRemoveClientSuccessWhenP2pDisabled() throws Exception {
        when(mWifiNative.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_GROUP_CLIENT_REMOVAL);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REMOVE_CLIENT, mTestWifiP2pPeerAddress);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_CLIENT_SUCCEEDED, message.what);
    }

    /**
     *  Verify WifiP2pManager.REMOVE_CLIENT_FAILED is returned when p2p is unsupported.
     */
    @Test
    public void testRemoveClientFailureWhenP2pUnsupported() throws Exception {
        setUpWifiP2pServiceImpl(false);
        when(mWifiNative.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_GROUP_CLIENT_REMOVAL);
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REMOVE_CLIENT, mTestWifiP2pPeerAddress);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(WifiP2pManager.REMOVE_CLIENT_FAILED, message.what);
        assertEquals(WifiP2pManager.P2P_UNSUPPORTED, message.arg1);
    }

    /**
     * Verify attribution is passed in correctly by WifiP2pManager#getMessenger.
     */
    @Test
    public void testGetMessenger_InvalidAttributions() {
        assumeTrue(SdkLevel.isAtLeastS());
        AttributionSource attributionSource = mock(AttributionSource.class);
        when(attributionSource.checkCallingUid()).thenReturn(true);
        when(attributionSource.isTrusted(any(Context.class))).thenReturn(true);
        mAttribution.putParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, attributionSource);
        mWifiP2pServiceImpl = spy(mWifiP2pServiceImpl);
        lenient().when(mWifiP2pServiceImpl.getMockableCallingUid()).thenReturn(Process.SYSTEM_UID);
        assertThrows(SecurityException.class, () -> {
            mWifiP2pServiceImpl.getMessenger(new Binder(), TEST_PACKAGE_NAME, null);
        });

        assertThrows(SecurityException.class, () -> {
            mWifiP2pServiceImpl.getMessenger(new Binder(), TEST_PACKAGE_NAME, new Bundle());
        });

        assertThrows(SecurityException.class, () -> {
            Bundle nullEntry = new Bundle();
            nullEntry.putParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, null);
            mWifiP2pServiceImpl.getMessenger(new Binder(), TEST_PACKAGE_NAME, nullEntry);
        });

        assertThrows(SecurityException.class, () -> {
            Bundle incorrectEntry = new Bundle();
            incorrectEntry.putInt(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, 10);
            mWifiP2pServiceImpl.getMessenger(new Binder(), TEST_PACKAGE_NAME, incorrectEntry);
        });

        when(attributionSource.checkCallingUid()).thenReturn(false);
        assertThrows(SecurityException.class, () -> {
            mWifiP2pServiceImpl.getMessenger(new Binder(), TEST_PACKAGE_NAME, mAttribution);
        });
        when(attributionSource.checkCallingUid()).thenReturn(true); // restore

        // single first attributions should not fail - even if (theoretically, doesn't happen in
        // practice) are not trusted. I.e. this call checks that this method isn't called.
        AttributionSource freshAs = mock(AttributionSource.class);
        Bundle freshAttribution = new Bundle();
        freshAttribution.putParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, freshAs);
        when(freshAs.checkCallingUid()).thenReturn(true);
        when(freshAs.isTrusted(any(Context.class))).thenReturn(false);
        mWifiP2pServiceImpl.getMessenger(new Binder(), TEST_PACKAGE_NAME, freshAttribution);
        verify(freshAs, never()).isTrusted(any());

        AttributionSource originalCaller = mock(AttributionSource.class);
        when(originalCaller.getUid()).thenReturn(12345);
        when(originalCaller.getPackageName()).thenReturn(TEST_PACKAGE_NAME + ".other");
        when(originalCaller.isTrusted(any(Context.class))).thenReturn(false);
        when(attributionSource.getNext()).thenReturn(originalCaller);
        assertThrows(SecurityException.class, () -> {
            mWifiP2pServiceImpl.getMessenger(new Binder(), TEST_PACKAGE_NAME, mAttribution);
        });
    }

    /**
     * Verify p2p connection dialog triggering without any Display ID information
     */
    @Test
    public void testInvitationReceivedDialogTrigger() throws Exception {
        forceP2pEnabled(mClient1);
        mockPeersList();
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = mTestWifiP2pDevice.deviceAddress;
        config.wps = new WpsInfo();
        config.wps.setup = WpsInfo.PBC;

        // "simple" client connect (no display ID)
        sendNegotiationRequestEvent(config);
        if (!SdkLevel.isAtLeastT()) {
            verify(mAlertDialog).show();
        } else {
            verify(mWifiDialogManager).createP2pInvitationReceivedDialog(anyString(), anyBoolean(),
                    any(), eq(Display.DEFAULT_DISPLAY), any(), any());
            verify(mDialogHandle).launchDialog(P2P_INVITATION_RECEIVED_TIMEOUT_MS);
        }
    }

    /**
     * Verify p2p connection dialog triggering with a privileged caller specifying a display ID.
     */
    @Test
    public void testInvitationReceivedDialogTriggerWithDisplayId() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        final int someNonDefaultDisplayId = 123;

        forceP2pEnabled(mClient1);
        mockPeersList();
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = mTestWifiP2pDevice.deviceAddress;
        config.wps = new WpsInfo();
        config.wps.setup = WpsInfo.PBC;

        // add a privileged client with a display ID
        Bundle bundle = new Bundle();
        bundle.putInt(WifiP2pManager.EXTRA_PARAM_KEY_DISPLAY_ID, someNonDefaultDisplayId);
        when(mWifiPermissionsUtil.isSystem(eq(TEST_PACKAGE_NAME), anyInt())).thenReturn(true);
        simulateInitChannel(mClient2, bundle);

        sendNegotiationRequestEvent(config);
        verify(mWifiDialogManager).createP2pInvitationReceivedDialog(anyString(),
                anyBoolean(), any(), eq(someNonDefaultDisplayId), any(), any());
        verify(mDialogHandle).launchDialog(P2P_INVITATION_RECEIVED_TIMEOUT_MS);
    }

    /**
     * Verify p2p connection dialog triggering with a privileged client adding a Display ID but then
     * closing (i.e. removing itself).
     */
    @Test
    public void testInvitationReceivedDialogTriggerWithDisplayIdDeleted() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        forceP2pEnabled(mClient1);
        mockPeersList();
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = mTestWifiP2pDevice.deviceAddress;
        config.wps = new WpsInfo();
        config.wps.setup = WpsInfo.PBC;

        // add a privileged client with a display ID
        Bundle bundle = new Bundle();
        bundle.putInt(WifiP2pManager.EXTRA_PARAM_KEY_DISPLAY_ID, 123);
        when(mWifiPermissionsUtil.isSystem(eq(TEST_PACKAGE_NAME), anyInt())).thenReturn(true);
        simulateInitChannel(mClient2, bundle);
        mWifiP2pServiceImpl.close(mClient2);

        // "simple" client connect (no display ID)
        sendNegotiationRequestEvent(config);
        verify(mWifiDialogManager).createP2pInvitationReceivedDialog(anyString(), anyBoolean(),
                any(), eq(Display.DEFAULT_DISPLAY), any(), any());
        verify(mDialogHandle).launchDialog(P2P_INVITATION_RECEIVED_TIMEOUT_MS);
    }

    private void verifySetVendorElement(boolean isP2pActivated, boolean shouldSucceed,
            boolean hasPermission, boolean shouldSetToNative) throws Exception {

        when(mWifiNative.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_SET_VENDOR_ELEMENTS);
        when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                .thenReturn(hasPermission);
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt()))
                .thenReturn(hasPermission);

        simulateWifiStateChange(true);
        simulateLocationModeChange(true);
        checkIsP2pInitWhenClientConnected(isP2pActivated, mClient1,
                new WorkSource(mClient1.getCallingUid(), TEST_PACKAGE_NAME));

        ArrayList<ScanResult.InformationElement> ies = new ArrayList<>();
        ies.add(new ScanResult.InformationElement(
                ScanResult.InformationElement.EID_VSA, 0,
                        new byte[]{(byte) 0xa, (byte) 0xb}));
        HashSet<ScanResult.InformationElement> expectedIes = new HashSet<>();
        expectedIes.add(ies.get(0));

        sendSetVendorElementsMsg(mClientMessenger, ies);

        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        if (shouldSucceed) {
            assertEquals(WifiP2pManager.SET_VENDOR_ELEMENTS_SUCCEEDED, message.what);
        } else {
            assertEquals(WifiP2pManager.SET_VENDOR_ELEMENTS_FAILED, message.what);
        }

        // Launch a peer discovery to set cached VSIEs to the native service.
        sendDiscoverPeersMsg(mClientMessenger);
        if (shouldSetToNative) {
            if (shouldSucceed) {
                verify(mWifiNative).setVendorElements(eq(expectedIes));
            } else {
                // If failed to set vendor elements, there is no entry in the list.
                verify(mWifiNative).setVendorElements(eq(
                        new HashSet<ScanResult.InformationElement>()));
            }
        } else {
            verify(mWifiNative, never()).setVendorElements(any());
        }
    }
    /**
     * Verify sunny scenario for setVendorElements when P2P is not in EnabledState.
     */
    @Test
    public void testSetVendorElementsSuccessForIdleShutdown() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isP2pActivated = false, shouldSucceed = true;
        boolean hasPermission = true, shouldSetToNative = true;
        verifySetVendorElement(isP2pActivated, shouldSucceed,
                hasPermission, shouldSetToNative);
    }

    /**
     * Verify sunny scenario for setVendorElements when P2P is in EnabledState.
     */
    @Test
    public void testSetVendorElementsSuccessForActiveP2p() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isP2pActivated = true, shouldSucceed = true;
        boolean hasPermission = true, shouldSetToNative = true;
        verifySetVendorElement(isP2pActivated, shouldSucceed,
                hasPermission, shouldSetToNative);
    }

    /**
     * Verify failure scenario for setVendorElements when no NEARBY permission.
     */
    @Test
    public void testSetVendorElementsFailureWithoutNearbyPermission() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isP2pActivated = false, shouldSucceed = false;
        boolean hasPermission = false, shouldSetToNative = true;
        verifySetVendorElement(isP2pActivated, shouldSucceed,
                hasPermission, shouldSetToNative);
    }

    private void verifyAddExternalApprover(boolean hasPermission,
            boolean shouldSucceed) throws Exception {
        verifyAddExternalApprover(new Binder(), hasPermission, shouldSucceed);
    }

    private void verifyAddExternalApprover(Binder binder, boolean hasPermission,
            boolean shouldSucceed) throws Exception {
        verifyAddExternalApprover(binder, hasPermission,
                shouldSucceed,
                MacAddress.fromString(mTestWifiP2pDevice.deviceAddress));
    }

    private void verifyAddExternalApprover(Binder binder, boolean hasPermission,
            boolean shouldSucceed, MacAddress devAddr) throws Exception {
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(hasPermission);
        when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                .thenReturn(hasPermission);

        simulateInitChannel(mClient1);

        sendAddExternalApproverMsg(mClientMessenger, devAddr, binder);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        if (shouldSucceed) {
            assertEquals(WifiP2pManager.EXTERNAL_APPROVER_ATTACH, message.what);
        } else {
            assertEquals(WifiP2pManager.EXTERNAL_APPROVER_DETACH, message.what);
        }
    }

    /**
     * Verify sunny scenario for addExternalApprover.
     */
    @Test
    public void testAddExternalApproverSuccess() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        boolean hasPermission = true, shouldSucceed = true;
        verifyAddExternalApprover(hasPermission, shouldSucceed);
    }

    /**
     * Verify failure scenario for addExternalApprover when
     * the caller has no proper permission.
     */
    @Test
    public void testAddExternalApproverFailureWithoutPermission() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        boolean hasPermission = false, shouldSucceed = false;
        verifyAddExternalApprover(hasPermission, shouldSucceed);
    }

    private void verifyRemoveExternalApprover(boolean hasPermission,
            boolean shouldSucceed) throws Exception {
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(hasPermission);
        when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                .thenReturn(hasPermission);
        MacAddress devAddr = MacAddress.fromString(
                mTestWifiP2pDevice.deviceAddress);

        simulateInitChannel(mClient1);

        sendRemoveExternalApproverMsg(mClientMessenger, devAddr, mClient1);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        if (shouldSucceed) {
            assertEquals(WifiP2pManager.REMOVE_EXTERNAL_APPROVER_SUCCEEDED, message.what);
        } else {
            assertEquals(WifiP2pManager.REMOVE_EXTERNAL_APPROVER_FAILED, message.what);
        }
    }


    /**
     * Verify sunny scenario for removeExternalApprover.
     */
    @Test
    public void testRemoveExternalApproverSuccess() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        boolean hasPermission = true, shouldSucceed = true;
        verifyRemoveExternalApprover(hasPermission, shouldSucceed);
    }

    /**
     * Verify failure scenario for removeExternalApprover when
     * the caller has no proper permission.
     */
    @Test
    public void testRemoveExternalApproverFailureWithoutPermission() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        boolean hasPermission = false, shouldSucceed = false;
        verifyRemoveExternalApprover(hasPermission, shouldSucceed);
    }

    private void verifySetConnectionRequestResult(MacAddress addr,
            boolean hasApprover,
            boolean hasPermission, boolean shouldSucceed,
            int wpsType, int result) throws Exception {
        Binder binder = new Binder();

        forceP2pEnabled(mClient1);
        mockPeersList();

        if (hasApprover) {
            verifyAddExternalApprover(binder, true, true, addr);
        }

        mockEnterUserAuthorizingNegotiationRequestState(wpsType);

        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(hasPermission);
        when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                .thenReturn(hasPermission);
        sendSetConnectionRequestResultMsg(mClientMessenger,
                MacAddress.fromString(mTestWifiP2pDevice.deviceAddress),
                result, binder);
        if (shouldSucceed) {
            // There are 4 replies:
            // * EXTERNAL_APPROVER_ATTACH
            // * EXTERNAL_APPROVER_CONNECTION_REQUESTED
            // * EXTERNAL_APPROVER_DETACH
            // * SET_CONNECTION_REQUEST_RESULT_SUCCEEDED
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(mClientHandler, times(4)).sendMessage(messageCaptor.capture());
            List<Message> messages = messageCaptor.getAllValues();
            assertEquals(WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_SUCCEEDED,
                    messages.get(3).what);
        } else {
            int expectedMessageCount = hasApprover ? 3 : 1;
            // There are 2 additional replies if having a approver.
            // * (With an approver) EXTERNAL_APPROVER_ATTACH
            // * (With an approver) EXTERNAL_APPROVER_CONNECTION_REQUESTED
            // * SET_CONNECTION_REQUEST_RESULT_FAILED
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(mClientHandler, times(expectedMessageCount)).sendMessage(
                    messageCaptor.capture());
            List<Message> messages = messageCaptor.getAllValues();
            assertEquals(WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_FAILED,
                    messages.get(expectedMessageCount - 1).what);
        }
    }

    /**
     * Verify sunny scenario for setConnectionRequestResult.
     */
    @Test
    public void testSetConnectionRequestResultSuccess() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        boolean hasApprover = true, hasPermission = true, shouldSucceed = true;
        verifySetConnectionRequestResult(MacAddress.fromString(mTestWifiP2pDevice.deviceAddress),
                hasApprover, hasPermission, shouldSucceed,
                WpsInfo.PBC, WifiP2pManager.CONNECTION_REQUEST_ACCEPT);
    }

    /**
     * Verify sunny scenario for setConnectionRequestResult with the wildcard address.
     */
    @Test
    public void testSetConnectionRequestResultWithWildcardAddressSuccess() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        boolean hasApprover = true, hasPermission = true, shouldSucceed = true;
        verifySetConnectionRequestResult(MacAddress.BROADCAST_ADDRESS,
                hasApprover, hasPermission, shouldSucceed,
                WpsInfo.PBC, WifiP2pManager.CONNECTION_REQUEST_ACCEPT);
    }

    private void verifyMultiApproverMatch(List<MacAddress> addresses, MacAddress expectedMatch)
            throws Exception {
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(true);
        when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                .thenReturn(true);
        Binder binder = new Binder();

        forceP2pEnabled(mClient1);
        mockPeersList();

        for (MacAddress addr: addresses) {
            verifyAddExternalApprover(binder, true /* hasPermission */,
                    true /* shouldSucceed */, addr);
            reset(mClientHandler);
        }

        // Received a request from mTestWifiP2pDevice
        mockEnterUserAuthorizingNegotiationRequestState(WpsInfo.PBC);

        sendSetConnectionRequestResultMsg(mClientMessenger,
                MacAddress.fromString(mTestWifiP2pDevice.deviceAddress),
                WifiP2pManager.CONNECTION_REQUEST_ACCEPT, binder);
        // There are 3 replies:
        // * EXTERNAL_APPROVER_CONNECTION_REQUESTED
        // * EXTERNAL_APPROVER_DETACH
        // * SET_CONNECTION_REQUEST_RESULT_SUCCEEDED
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mClientHandler, times(3)).sendMessage(messageCaptor.capture());
        List<Message> messages = messageCaptor.getAllValues();

        assertEquals(WifiP2pManager.EXTERNAL_APPROVER_CONNECTION_REQUESTED,
                messages.get(0).what);
        Bundle requestBundle = (Bundle) messages.get(0).obj;
        WifiP2pDevice requestDevice = requestBundle.getParcelable(
                WifiP2pManager.EXTRA_PARAM_KEY_DEVICE);
        assertEquals(mTestWifiP2pDevice.deviceAddress, requestDevice.deviceAddress);

        assertEquals(WifiP2pManager.EXTERNAL_APPROVER_DETACH,
                messages.get(1).what);
        assertEquals(expectedMatch, (MacAddress) messages.get(1).obj);

        assertEquals(WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_SUCCEEDED,
                messages.get(2).what);
    }

    /**
     * Verify that a registered address could be matched correctly
     * with additional wildcard address.
     */
    @Test
    public void testDirectMatchWithWildcardAddress() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        List<MacAddress> addresses = new ArrayList<>();
        addresses.add(MacAddress.fromString(mTestWifiP2pDevice.deviceAddress));
        addresses.add(MacAddress.BROADCAST_ADDRESS);
        verifyMultiApproverMatch(addresses,
                MacAddress.fromString(mTestWifiP2pDevice.deviceAddress));
    }

    /**
     * Verify that a unkonwn address could be matched against the wildcard address correctly
     * with an address and the wildcard address.
     */
    @Test
    public void testWildcardAddressMatch() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        List<MacAddress> addresses = new ArrayList<>();
        addresses.add(MacAddress.fromString("00:02:00:00:00:00"));
        addresses.add(MacAddress.BROADCAST_ADDRESS);
        verifyMultiApproverMatch(addresses, MacAddress.BROADCAST_ADDRESS);
    }

    /**
     * Verify the failure scenario for setConnectionRequestResult without permissions.
     */
    @Test
    public void testSetConnectionRequestResultFailureWithoutPermission() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        boolean hasApprover = true, hasPermission = false, shouldSucceed = false;
        verifySetConnectionRequestResult(MacAddress.fromString(mTestWifiP2pDevice.deviceAddress),
                hasApprover, hasPermission, shouldSucceed,
                WpsInfo.PBC, WifiP2pManager.CONNECTION_REQUEST_ACCEPT);
    }

    /**
     * Verify the failure scenario for setConnectionRequestResult without a registered approver.
     */
    @Test
    public void testSetConnectionRequestResultFailureWithoutApprover() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        boolean hasApprover = false, hasPermission = true, shouldSucceed = false;
        verifySetConnectionRequestResult(MacAddress.fromString(mTestWifiP2pDevice.deviceAddress),
                hasApprover, hasPermission, shouldSucceed,
                WpsInfo.PBC, WifiP2pManager.CONNECTION_REQUEST_ACCEPT);
    }

    /** Verify the failure scenario for setConnectionRequestResult without a saved peer config. */
    @Test
    public void testSetConnectionRequestResultFailureWithoutSavedPeerConfig() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(anyInt()))
                .thenReturn(true);
        when(mWifiPermissionsUtil.checkNearbyDevicesPermission(any(), anyBoolean(), any()))
                .thenReturn(true);
        Binder binder = new Binder();
        mockEnterGroupCreatedState();
        sendSetConnectionRequestResultMsg(
                mClientMessenger,
                MacAddress.fromString(mTestWifiP2pDevice.deviceAddress),
                WifiP2pManager.CONNECTION_REQUEST_ACCEPT,
                binder);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mClientHandler).sendMessage(messageCaptor.capture());
        List<Message> messages = messageCaptor.getAllValues();
        assertEquals(WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_FAILED, messages.get(0).what);
    }

    /**
     * Verify that deferring pin to the framework works normally.
     */
    @Test
    public void testSetConnectionRequestResultDeferPinToFramework() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        boolean hasApprover = true, hasPermission = true, shouldSucceed = true;
        verifySetConnectionRequestResult(MacAddress.fromString(mTestWifiP2pDevice.deviceAddress),
                hasApprover, hasPermission, shouldSucceed,
                WpsInfo.KEYPAD, WifiP2pManager.CONNECTION_REQUEST_DEFER_SHOW_PIN_TO_SERVICE);
    }

    /**
     * Validate p2p initialization when user approval is required.
     */
    public void runTestP2pWithUserApproval(boolean userAcceptsRequest) throws Exception {
        ArgumentCaptor<State> mTargetStateCaptor = ArgumentCaptor.forClass(State.class);
        ArgumentCaptor<WaitingState> mWaitingStateCaptor = ArgumentCaptor.forClass(
                WaitingState.class);
        InOrder inOrder = inOrder(mInterfaceConflictManager);

        simulateWifiStateChange(true);
        simulateInitChannel(mClient1);

        // simulate user approval needed
        when(mInterfaceConflictManager.manageInterfaceConflictForStateMachine(any(), any(), any(),
                any(), any(), eq(HalDeviceManager.HDM_CREATE_IFACE_P2P), any(), anyBoolean()))
                .thenAnswer(new MockAnswerUtil.AnswerWithArguments() {
                    public int answer(String tag, Message msg, StateMachine stateMachine,
                            WaitingState waitingState, State targetState, int createIfaceType,
                            WorkSource requestorWs,
                            boolean bypassDialog) {
                        stateMachine.deferMessage(msg);
                        stateMachine.transitionTo(waitingState);
                        return InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER;
                    }
                });

        sendSimpleMsg(mClientMessenger, WifiP2pManager.DISCOVER_PEERS);
        mLooper.dispatchAll();
        inOrder.verify(mInterfaceConflictManager).manageInterfaceConflictForStateMachine(any(),
                any(), any(), mWaitingStateCaptor.capture(), mTargetStateCaptor.capture(),
                eq(HalDeviceManager.HDM_CREATE_IFACE_P2P), any(), anyBoolean());

        // simulate user approval triggered and granted
        when(mInterfaceConflictManager.manageInterfaceConflictForStateMachine(any(), any(), any(),
                any(), any(), eq(HalDeviceManager.HDM_CREATE_IFACE_P2P), any(), anyBoolean()))
                .thenReturn(userAcceptsRequest ? InterfaceConflictManager.ICM_EXECUTE_COMMAND
                        : InterfaceConflictManager.ICM_ABORT_COMMAND);
        mWaitingStateCaptor.getValue().sendTransitionStateCommand(mTargetStateCaptor.getValue());
        mLooper.dispatchAll();

        verify(mWifiNative, userAcceptsRequest ? times(1) : never()).setupInterface(any(), any(),
                eq(new WorkSource(mClient1.getCallingUid(), TEST_PACKAGE_NAME)));
        if (userAcceptsRequest) {
            // Device status is AVAILABLE
            mTestThisDevice.status = WifiP2pDevice.AVAILABLE;
            checkSendThisDeviceChangedBroadcast();
            sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DEVICE_INFO);
            verify(mClientHandler, times(2)).sendMessage(mMessageCaptor.capture());
            assertEquals(WifiP2pManager.RESPONSE_DEVICE_INFO, mMessageCaptor.getValue().what);
            assertEquals(WifiP2pDevice.AVAILABLE,
                    ((WifiP2pDevice) mMessageCaptor.getValue().obj).status);
        } else {
            // Device status is UNAVAILABLE
            mTestThisDevice = new WifiP2pDevice();
            mTestThisDevice.deviceName = thisDeviceName;
            mTestThisDevice.deviceAddress = thisDeviceMac;
            checkSendThisDeviceChangedBroadcast();
            sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_DEVICE_INFO);
            verify(mClientHandler, times(2)).sendMessage(mMessageCaptor.capture());
            assertEquals(WifiP2pManager.RESPONSE_DEVICE_INFO, mMessageCaptor.getValue().what);
            assertEquals(WifiP2pDevice.UNAVAILABLE,
                    ((WifiP2pDevice) mMessageCaptor.getValue().obj).status);
        }
    }

    /**
     * Validate p2p initialization when user approval is required and granted.
     */
    @Test
    public void testP2pWithUserApprovalAccept() throws Exception {
        runTestP2pWithUserApproval(true);
    }

    /**
     * Validate p2p initialization when user approval is required and granted.
     */
    @Test
    public void testP2pWithUserApprovalReject() throws Exception {
        runTestP2pWithUserApproval(false);
    }

    /**
     * Validate InterfaceConflictManager is reset if user approval occurs after wifi turned off.
     */
    @Test
    public void testInterfaceConflictManagerResetIfWifiTurnedOff() throws Exception {
        ArgumentCaptor<State> mTargetStateCaptor = ArgumentCaptor.forClass(State.class);
        ArgumentCaptor<WaitingState> mWaitingStateCaptor = ArgumentCaptor.forClass(
                WaitingState.class);
        InOrder inOrder = inOrder(mInterfaceConflictManager);

        simulateWifiStateChange(true);
        simulateInitChannel(mClient1);

        // simulate user approval needed
        when(mInterfaceConflictManager.manageInterfaceConflictForStateMachine(any(), any(), any(),
                any(), any(), eq(HalDeviceManager.HDM_CREATE_IFACE_P2P), any(), anyBoolean()))
                .thenAnswer(new MockAnswerUtil.AnswerWithArguments() {
                    public int answer(String tag, Message msg, StateMachine stateMachine,
                            WaitingState waitingState, State targetState,
                            int createIfaceType,
                            WorkSource requestorWs,
                            boolean bypassDialog) {
                        stateMachine.deferMessage(msg);
                        stateMachine.transitionTo(waitingState);
                        return InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER;
                    }
                });

        sendSimpleMsg(mClientMessenger, WifiP2pManager.DISCOVER_PEERS);
        mLooper.dispatchAll();
        inOrder.verify(mInterfaceConflictManager).manageInterfaceConflictForStateMachine(any(),
                any(), any(), mWaitingStateCaptor.capture(), mTargetStateCaptor.capture(),
                eq(HalDeviceManager.HDM_CREATE_IFACE_P2P), any(), anyBoolean());

        // Turn off Wi-Fi
        simulateWifiStateChange(false);

        // simulate user approval triggered and granted
        when(mInterfaceConflictManager.manageInterfaceConflictForStateMachine(any(), any(), any(),
                any(), any(), eq(HalDeviceManager.HDM_CREATE_IFACE_P2P), any(), anyBoolean()))
                .thenReturn(InterfaceConflictManager.ICM_EXECUTE_COMMAND);
        mWaitingStateCaptor.getValue().sendTransitionStateCommand(mTargetStateCaptor.getValue());
        mLooper.dispatchAll();

        // Verify InterfaceConflictManager is reset
        verify(mInterfaceConflictManager).reset();
        verify(mWifiNative, never()).setupInterface(any(), any(),
                eq(new WorkSource(mClient1.getCallingUid(), TEST_PACKAGE_NAME)));
    }

    /*
     * Verify the connection event ends due to the provision discovery failure.
     */
    @Test
    public void testProvDiscRejectEventForProvDisc() throws Exception {
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);

        mockEnterProvisionDiscoveryState();

        WifiP2pProvDiscEvent pdEvent = new WifiP2pProvDiscEvent();
        pdEvent.device = mTestWifiP2pDevice;
        sendSimpleMsg(null,
                WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT,
                WifiP2pMonitor.PROV_DISC_STATUS_REJECTED,
                pdEvent);
        verify(mWifiNative).p2pCancelConnect();
        if (SdkLevel.isAtLeastT()) {
            verify(mP2pListener).onGroupCreationFailed();
        }
    }

    /**
     * Verify the p2p reject is sent on canceling a request.
     */
    @Test
    public void testSendP2pRejectWhenCancelRequest() throws Exception {
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);

        mockEnterProvisionDiscoveryState();

        WifiP2pProvDiscEvent pdEvent = new WifiP2pProvDiscEvent();
        pdEvent.device = mTestWifiP2pDevice;
        sendSimpleMsg(null, WifiP2pManager.CANCEL_CONNECT);
        verify(mWifiNative).p2pReject(eq(mTestWifiP2pDevice.deviceAddress));
    }

    /**
     *
     */
    @Test
    public void testSendP2pRejectOnRejectRequest() throws Exception {
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);
        forceP2pEnabled(mClient1);

        mockEnterUserAuthorizingNegotiationRequestState(WpsInfo.PBC);

        sendSimpleMsg(null, WifiP2pServiceImpl.PEER_CONNECTION_USER_REJECT);
        verify(mWifiNative).p2pReject(eq(mTestWifiP2pDevice.deviceAddress));
        if (SdkLevel.isAtLeastT()) {
            verify(mP2pListener).onGroupNegotiationRejectedByUser();
        }
    }

    /**
     * Verifies that we broadcast WIFI_P2P_CONNECTION_CHANGED_ACTION with DISCONNECTED state upon
     * user reject for negotiation request.
     * @throws Exception
     */
    @Test
    public void testBroadcastDisconnectedStateOnRejectRequest() throws Exception {
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);
        forceP2pEnabled(mClient1);

        mockEnterUserAuthorizingNegotiationRequestState(WpsInfo.PBC);

        sendSimpleMsg(null, WifiP2pServiceImpl.PEER_CONNECTION_USER_REJECT);
        verify(mContext).sendBroadcastWithMultiplePermissions(
                argThat(new WifiP2pServiceImplTest
                        .P2pConnectionChangedIntentMatcherForNetworkState(DISCONNECTED)), any());
    }

    @Test
    public void testDismissDialogOnReceiveProvDiscFailureEvent() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);
        forceP2pEnabled(mClient1);

        mockEnterUserAuthorizingNegotiationRequestState(WpsInfo.PBC);
        WifiP2pProvDiscEvent pdEvent = new WifiP2pProvDiscEvent();
        pdEvent.device = mTestWifiP2pDevice;
        sendSimpleMsg(null,
                WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT,
                WifiP2pMonitor.PROV_DISC_STATUS_REJECTED,
                pdEvent);
        verify(mDialogHandle).dismissDialog();
    }

    @Test
    public void testDismissDialogOnReceiveProvDiscFailureEventPreT() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);
        forceP2pEnabled(mClient1);

        mockEnterUserAuthorizingNegotiationRequestState(WpsInfo.PBC);
        WifiP2pProvDiscEvent pdEvent = new WifiP2pProvDiscEvent();
        pdEvent.device = mTestWifiP2pDevice;
        sendSimpleMsg(null,
                WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT,
                WifiP2pMonitor.PROV_DISC_STATUS_REJECTED,
                pdEvent);
        verify(mAlertDialog).dismiss();
    }

    /**
     * Verify the tethering request is sent with TETHER_PRIVILEGED permission.
     */
    @Test
    public void testTetheringRequestWithTetherPrivilegedPermission() throws Exception {
        mockEnterGroupCreatedState();

        String[] permission_gold = new String[]{android.Manifest.permission.TETHER_PRIVILEGED};
        if (SdkLevel.isAtLeastU()) {
            permission_gold = new String[]{NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK};
        }
        ArgumentCaptor<String[]> permissionCaptor = ArgumentCaptor.forClass(String[].class);
        String[] permission;
        // 3 connection changed event:
        // * Enter Enabled state
        // * Enter Group Connecting state
        // * Tethering request.
        if (SdkLevel.isAtLeastT()) {
            verify(mContext, times(3)).sendBroadcastWithMultiplePermissions(
                    argThat(new WifiP2pServiceImplTest
                           .P2pConnectionChangedIntentMatcherForNetworkState(null)),
                    permissionCaptor.capture());
            permission = permissionCaptor.getAllValues().get(2);
        } else {
            verify(mContext, times(2)).sendBroadcastWithMultiplePermissions(
                    argThat(new WifiP2pServiceImplTest
                          .P2pConnectionChangedIntentMatcherForNetworkState(IDLE)),
                    permissionCaptor.capture());
            permission = permissionCaptor.getAllValues().get(1);
        }
        Arrays.sort(permission);
        Arrays.sort(permission_gold);
        assertEquals(permission_gold, permission);
    }

    /**
     * Verify ENABLE_P2P won't set up P2P interface when there is no
     * active client.
     */
    @Test
    public void testEnableP2pIsIgnoredWhenNoActiveClient() throws Exception {
        simulateWifiStateChange(true);
        simulateLocationModeChange(true);

        simulateInitChannel(mClient1);
        sendP2pStateMachineMessage(WifiP2pServiceImpl.ENABLE_P2P);
        mLooper.dispatchAll();
        verify(mWifiNative, never()).setupInterface(any(), any(), any());
        verify(mNetdWrapper, never()).setInterfaceUp(anyString());
        verify(mWifiMonitor, never()).registerHandler(anyString(), anyInt(), any());
    }

    @Test
    public void testPbcReconnectForUnknownGroupInvitation() throws Exception {
        forceP2pEnabled(mClient1);
        when(mWifiNative.getGroupCapability(any())).thenReturn(0);
        when(mWifiNative.p2pReinvoke(anyInt(), any())).thenReturn(true);
        when(mWifiNative.p2pGetSsid(any())).thenReturn(null);
        when(mTestWifiP2pDevice.isGroupOwner()).thenReturn(false);
        when(mTestWifiP2pDevice.isInvitationCapable()).thenReturn(true);

        mockPeersList();
        // Trigger reinvoke to enter Group Negotiation state
        mTestWifiP2pPeerConfig.wps.setup = WpsInfo.PBC;
        sendConnectMsg(mClientMessenger, mTestWifiP2pPeerConfig);
        // Got the unknown group result from the peer.
        sendInvitationResultMsg(WifiP2pServiceImpl.P2pStatus.UNKNOWN_P2P_GROUP);

        // For PBC request, p2p should start from negotiation request directly.
        verify(mWifiNative, never()).p2pProvisionDiscovery(any());
        verify(mWifiNative).p2pConnect(eq(mTestWifiP2pPeerConfig), anyBoolean());
    }

    @Test
    public void testPinReconnectForUnknownGroupInvitation() throws Exception {
        forceP2pEnabled(mClient1);
        when(mWifiNative.getGroupCapability(any())).thenReturn(0);
        when(mWifiNative.p2pReinvoke(anyInt(), any())).thenReturn(true);
        when(mWifiNative.p2pGetSsid(any())).thenReturn(null);
        when(mTestWifiP2pDevice.isGroupOwner()).thenReturn(false);
        when(mTestWifiP2pDevice.isInvitationCapable()).thenReturn(true);

        mockPeersList();
        // Trigger reinvoke to enter Group Negotiation state
        mTestWifiP2pPeerConfig.wps.setup = WpsInfo.KEYPAD;
        sendConnectMsg(mClientMessenger, mTestWifiP2pPeerConfig);
        // Got the unknown group result from the peer.
        sendInvitationResultMsg(WifiP2pServiceImpl.P2pStatus.UNKNOWN_P2P_GROUP);

        // For Keypad/Display pin-based request, p2p should start the p2p connection
        // from provision discovery to exchange pin codes.
        verify(mWifiNative).p2pProvisionDiscovery(mTestWifiP2pPeerConfig);
    }

    /**
     * Verify internal reconnect works normally.
     */
    @Test
    public void testInternalReconnectForUnknownP2pGroup() throws Exception {
        lenient().when(Process.myUid()).thenReturn(Process.SYSTEM_UID);
        when(mWifiNative.p2pInvite(any(), any())).thenReturn(true);
        // Put newly formed group to the saved group list.
        WifiP2pGroup savedGroup = new WifiP2pGroup(mTestWifiP2pNewPersistentGoGroup);
        savedGroup.setNetworkId(99);
        mGroups.add(savedGroup);

        forceP2pEnabled(mClient1);
        sendGroupStartedMsg(mTestWifiP2pNewPersistentGoGroup);
        simulateTetherReady();

        // Mock an outgoing invitation.
        mockPeersList();
        sendSetOngoingPeerConfigMsg(mClientMessenger, mTestWifiP2pPeerConfig);
        mLooper.dispatchAll();

        // The peer does not know this group, reconnect to the peer.
        sendInvitationResultMsg(WifiP2pServiceImpl.P2pStatus.UNKNOWN_P2P_GROUP);
        mLooper.dispatchAll();

        verify(mWifiNative).p2pInvite(any(), eq(mTestWifiP2pPeerConfig.deviceAddress));
    }

    @Test
    public void testSurplusProvisionDiscoveryRequestsBehavior() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);

        mockEnterGroupCreatedState();

        // The first provision discvoery request triggers the dialog.
        WifiP2pProvDiscEvent pdEvent = new WifiP2pProvDiscEvent();
        pdEvent.device = mTestWifiP2pDevice;
        sendSimpleMsg(null, WifiP2pMonitor.P2P_PROV_DISC_PBC_REQ_EVENT,
                pdEvent);

        verify(mWifiDialogManager).createP2pInvitationReceivedDialog(
                eq(mTestWifiP2pDevice.deviceAddress), anyBoolean(),
                any(), anyInt(), any(), any());
        verify(mDialogHandle).launchDialog(P2P_INVITATION_RECEIVED_TIMEOUT_MS);

        // Handle it programmatically.
        sendSimpleMsg(null, WifiP2pServiceImpl.PEER_CONNECTION_USER_REJECT);

        // Send another provision discvoery request again.
        sendSimpleMsg(null, WifiP2pMonitor.P2P_PROV_DISC_PBC_REQ_EVENT,
                pdEvent);

        // This surplus request should not trigger a dialog.
        reset(mWifiDialogManager);
        reset(mDialogHandle);
        verify(mWifiDialogManager, never()).createP2pInvitationReceivedDialog(
                any(), anyBoolean(), any(), anyInt(), any(), any());

        when(mWifiDialogManager.createP2pInvitationReceivedDialog(any(), anyBoolean(), any(),
                anyInt(), any(), any())).thenReturn(mDialogHandle);
        when(mWifiDialogManager.createP2pInvitationSentDialog(any(), any(), anyInt()))
                .thenReturn(mDialogHandle);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(P2P_PEER_AUTH_TIMEOUT_MS + 1L);

        // Send another provision discvoery request again after the timeout
        sendSimpleMsg(null, WifiP2pMonitor.P2P_PROV_DISC_PBC_REQ_EVENT,
                pdEvent);

        // Another dialog should be triggered.
        verify(mWifiDialogManager).createP2pInvitationReceivedDialog(
                eq(mTestWifiP2pDevice.deviceAddress), anyBoolean(),
                any(), anyInt(), any(), any());
        verify(mDialogHandle).launchDialog(P2P_INVITATION_RECEIVED_TIMEOUT_MS);
    }

    @Test
    public void testP2pInfoIsClearedWhenP2pIsDisabledDurningNegotiation() throws Exception {
        forceP2pEnabled(mClient1);
        WifiP2pGroup group = new WifiP2pGroup();
        group.setNetworkId(WifiP2pGroup.NETWORK_ID_PERSISTENT);
        group.setNetworkName(TEST_NETWORK_NAME);
        group.setOwner(new WifiP2pDevice("thisDeviceMac"));
        group.setIsGroupOwner(true);
        group.setInterface(IFACE_NAME_P2P);
        sendGroupStartedMsg(group);

        // P2P group is formed, the internal group data are filled.
        // The tether request is not done yet, so it stays at GroupNegotiationState.
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_CONNECTION_INFO);
        verify(mClientHandler).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_CONNECTION_INFO, mMessageCaptor.getValue().what);
        assertTrue(((WifiP2pInfo) mMessageCaptor.getValue().obj).groupFormed);

        // Go back to P2pDisabledState.
        sendP2pStateMachineMessage(WifiP2pServiceImpl.DISABLE_P2P);
        sendP2pStateMachineMessage(WifiP2pMonitor.SUP_DISCONNECTION_EVENT);
        mLooper.dispatchAll();

        // p2p info should be cleared.
        sendSimpleMsg(mClientMessenger, WifiP2pManager.REQUEST_CONNECTION_INFO);
        verify(mClientHandler, times(2)).sendMessage(mMessageCaptor.capture());
        assertEquals(WifiP2pManager.RESPONSE_CONNECTION_INFO, mMessageCaptor.getValue().what);
        assertFalse(((WifiP2pInfo) mMessageCaptor.getValue().obj).groupFormed);
    }

    @Test
    public void testGroupStartedTetheringDirectCallback() throws Exception {
        when(mWifiNative.p2pExtListen(anyBoolean(), anyInt(), anyInt(), any())).thenReturn(true);
        assumeTrue(SdkLevel.isAtLeastS());
        forceP2pEnabled(mClient1);
        verify(mTetheringManager).registerTetheringEventCallback(any(), any());

        //create/start/remove group
        when(mWifiNative.p2pGroupAdd(any(), eq(false))).thenReturn(true);
        sendCreateGroupMsgWithConfigValidAsGroup(mClientMessenger);
        assertTrue(mClientHandler.hasMessages(WifiP2pManager.CREATE_GROUP_SUCCEEDED));
        WifiP2pGroup group = new WifiP2pGroup();
        group.setNetworkName("DIRECT-test");
        group.setOwner(new WifiP2pDevice("thisDeviceMac"));
        group.setIsGroupOwner(true);
        group.setInterface(IFACE_NAME_P2P);
        sendGroupStartedMsg(group);
        simulateTetherReady();
        verify(mWifiP2pMetrics).startGroupEvent(group);
        verify(mWifiNative).p2pStopFind();
        verify(mWifiNative).p2pExtListen(eq(false), anyInt(), anyInt(), eq(null));
        sendGroupRemovedMsg();

        //force to back disabled state
        mockEnterDisabledState();
        verify(mTetheringManager).unregisterTetheringEventCallback(any());
    }

    /**
     * Verify that GroupFailure triggers internal p2p discover
     */
    @Test
    public void testGroupfailuretriggerInternalDiscoverPeers() throws Exception {
        lenient().when(Process.myUid()).thenReturn(Process.SYSTEM_UID);
        when(mWifiNative.p2pFind(anyInt())).thenReturn(true);
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(anyBoolean())).thenReturn(true);

        mockEnterProvisionDiscoveryState();

        WifiP2pProvDiscEvent pdEvent = new WifiP2pProvDiscEvent();
        pdEvent.device = mTestWifiP2pDevice;

        /* Trigger a group failure */
        sendSimpleMsg(null, WifiP2pManager.CANCEL_CONNECT);
        mLooper.moveTimeForward(100);
        mLooper.dispatchAll();

        /* Verify that p2p discover is triggered */
        verify(mWifiNative).p2pFind(anyInt());
    }

    @Test
    public void testFallbackToNegotiationOnInviteStatusCodeInfoUnavailable() throws Exception {
        forceP2pEnabled(mClient1);
        when(mWifiNative.getGroupCapability(any())).thenReturn(0);
        when(mWifiNative.p2pReinvoke(anyInt(), any())).thenReturn(true);
        when(mWifiNative.p2pGetSsid(any())).thenReturn(null);
        when(mTestWifiP2pDevice.isGroupOwner()).thenReturn(false);
        when(mTestWifiP2pDevice.isInvitationCapable()).thenReturn(true);

        mockPeersList();
        // Trigger reinvoke to enter Group Negotiation state
        mTestWifiP2pPeerConfig.wps.setup = WpsInfo.KEYPAD;
        sendConnectMsg(mClientMessenger, mTestWifiP2pPeerConfig);
        // Got Fail: information is currently unavailable
        sendInvitationResultMsg(WifiP2pServiceImpl.P2pStatus.INFORMATION_IS_CURRENTLY_UNAVAILABLE);

        verify(mWifiNative).p2pConnect(eq(mTestWifiP2pPeerConfig), anyBoolean());
        verify(mWifiP2pMetrics).setFallbackToNegotiationOnInviteStatusInfoUnavailable();
    }

    @Test
    public void testFallbackToP2pConnectWithWpsProvisioningOnInviteRequestFromAnUnknownNetwork()
            throws Exception {
        forceP2pEnabled(mClient1);
        mockEnterUserAuthorizingInviteRequestState();
        // Return an unknown network name
        when(mWifiNative.p2pGetSsid(eq(mTestWifiP2pDevice.deviceAddress)))
                .thenReturn(mTestWifiP2pGroup.getNetworkName() + "unknown");
        sendSimpleMsg(null, WifiP2pServiceImpl.PEER_CONNECTION_USER_ACCEPT);
        verify(mWifiNative, never()).p2pGroupAdd(anyInt());
        verify(mWifiNative).p2pConnect(any(), anyBoolean());
    }

    @Test
    public void testP2pSetupPersistentGroupOnInviteRequestFromAKnownNetwork() throws Exception {
        forceP2pEnabled(mClient1);
        mockEnterUserAuthorizingInviteRequestState();
        when(mWifiNative.p2pGetSsid(eq(mTestWifiP2pDevice.deviceAddress)))
                .thenReturn(mTestWifiP2pGroup.getNetworkName());
        sendSimpleMsg(null, WifiP2pServiceImpl.PEER_CONNECTION_USER_ACCEPT);
        verify(mWifiNative).p2pGroupAdd(anyInt());
    }

    @Test
    public void testSetListenOnInviteStatusCodeInfoUnavailable() throws Exception {
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pExtListen(anyBoolean(), anyInt(), anyInt(), any())).thenReturn(true);
        when(mWifiNative.getGroupCapability(any())).thenReturn(0);
        when(mWifiNative.p2pReinvoke(anyInt(), any())).thenReturn(true);
        when(mWifiNative.p2pGetSsid(any())).thenReturn(null);
        when(mTestWifiP2pDevice.isGroupOwner()).thenReturn(false);
        when(mTestWifiP2pDevice.isInvitationCapable()).thenReturn(true);
        when(mResources.getBoolean(R.bool
                .config_p2pWaitForPeerInviteOnInviteStatusInfoUnavailable)).thenReturn(true);

        mockPeersList();
        // Trigger reinvoke to enter Group Negotiation state
        mTestWifiP2pPeerConfig.wps.setup = WpsInfo.KEYPAD;
        sendConnectMsg(mClientMessenger, mTestWifiP2pPeerConfig);
        // Got Fail: information is currently unavailable
        sendInvitationResultMsg(WifiP2pServiceImpl.P2pStatus.INFORMATION_IS_CURRENTLY_UNAVAILABLE);

        verify(mWifiNative, times(2)).p2pStopFind();
        verify(mWifiNative).p2pExtListen(eq(true), eq(P2P_EXT_LISTEN_PERIOD_MS),
                eq(P2P_EXT_LISTEN_INTERVAL_MS), eq(null));
    }

    /**
     * Verify that p2p connect with Join existing group is set
     */
    @Test
    public void verifyP2pConnectWithJoinExistingGroupSet() throws Exception {
        forceP2pEnabled(mClient1);
        mockEnterProvisionDiscoveryState(mTestWifiP2pJoinExistingGroupConfig);

        WifiP2pProvDiscEvent pdEvent = new WifiP2pProvDiscEvent();
        pdEvent.device = mTestWifiP2pDevice;
        sendSimpleMsg(null,
                WifiP2pMonitor.P2P_PROV_DISC_PBC_RSP_EVENT,
                pdEvent);

        ArgumentCaptor<WifiP2pConfig> configCaptor =
                ArgumentCaptor.forClass(WifiP2pConfig.class);
        verify(mWifiNative).p2pConnect(configCaptor.capture(), eq(true));
        WifiP2pConfig config = configCaptor.getValue();
        assertTrue(config.isJoinExistingGroup());
    }

    /**
     * Verify that EAPOL IP Address allocation parameters are set
     */
    @Test
    public void checkConfigureEapolIpAddressAllocationParams() throws Exception {
        when(mResources.getBoolean(R.bool
                .config_wifiP2pGoIpAddressAllocationInEapolFrames)).thenReturn(true);
        forceP2pEnabled(mClient1);
        InetAddress ipAddressGo = InetAddresses.parseNumericAddress(
                WifiP2pServiceImpl.GO_EAPOL_IP_ADDRESS);
        InetAddress subnetMask = InetAddresses.parseNumericAddress(
                WifiP2pServiceImpl.GO_EAPOL_IP_SUBNET_MASK);
        InetAddress ipAddressRangeStart = InetAddresses.parseNumericAddress(
                WifiP2pServiceImpl.GO_EAPOL_IP_RANGE_DEFAULT_START_ADDRESS);
        InetAddress ipAddressRangeEnd = InetAddresses.parseNumericAddress(
                WifiP2pServiceImpl.GO_EAPOL_IP_RANGE_DEFAULT_END_ADDRESS);
        verify(mWifiNative).configureEapolIpAddressAllocationParams(
                eq(inet4AddressToIntHTL((Inet4Address) ipAddressGo)),
                eq(inet4AddressToIntHTL((Inet4Address) subnetMask)),
                eq(inet4AddressToIntHTL((Inet4Address) ipAddressRangeStart)),
                eq(inet4AddressToIntHTL((Inet4Address) ipAddressRangeEnd)));
    }

    /**
     * Verify that EAPOL IP Address allocation parameters are not configured when the feature is not
     * enabled via overlay configuration item.
     */
    @Test
    public void testConfigureEapolIpAddressAllocationParamsWhenConfigOverlayDisabled()
            throws Exception {
        forceP2pEnabled(mClient1);
        verify(mWifiNative, never()).configureEapolIpAddressAllocationParams(anyInt(),
                anyInt(), anyInt(), anyInt());
    }

    @Test
    public void testSendP2pConnectionChangedBroadcast() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<String[]> permissionCaptor = ArgumentCaptor.forClass(String[].class);
        String[] receiverPermissions;
        int flags = Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT;

        // mock p2p enabled
        forceP2pEnabled(mClient1);
        if (!SdkLevel.isAtLeastU()) {
            receiverPermissions = new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_WIFI_STATE};
            verify(mContext).sendBroadcastWithMultiplePermissions(argThat((Intent intent) -> {
                WifiP2pInfo p2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                WifiP2pGroup p2pGroup = intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
                assert p2pInfo != null;
                return intent.getAction().equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                        && intent.getFlags() == Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                        && Objects.isNull(intent.getPackage())
                        && !p2pInfo.groupFormed && Objects.isNull(p2pGroup);
            }), permissionCaptor.capture());
            assertEquals(receiverPermissions, permissionCaptor.getValue());
        } else {
            receiverPermissions = new String[]{NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK};
            flags |= Intent.FLAG_RECEIVER_FOREGROUND;
            verify(mContext).sendBroadcastWithMultiplePermissions(intentCaptor.capture(),
                    permissionCaptor.capture());
            Intent intent = intentCaptor.getValue();
            assertEquals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION, intent.getAction());
            assertEquals(flags, intent.getFlags());
            WifiP2pInfo p2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
            WifiP2pGroup p2pGroup = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
            assert p2pInfo != null;
            assertFalse(p2pInfo.groupFormed);
            assertFalse(p2pInfo.isGroupOwner);
            assertNull(p2pGroup);
            assertEquals(receiverPermissions, permissionCaptor.getValue());
        }

        // mock group started
        WifiP2pGroup group = new WifiP2pGroup();
        group.setNetworkId(WifiP2pGroup.NETWORK_ID_PERSISTENT);
        group.setNetworkName("DIRECT-xy-NEW");
        group.setOwner(new WifiP2pDevice("thisDeviceMac"));
        group.setIsGroupOwner(true);
        group.setInterface(IFACE_NAME_P2P);
        sendGroupStartedMsg(group);
        simulateTetherReady();

        // reassign captor again, otherwise captor.getAllValues() will include previous values
        intentCaptor = ArgumentCaptor.forClass(Intent.class);
        permissionCaptor = ArgumentCaptor.forClass(String[].class);
        if (!SdkLevel.isAtLeastU()) {
            // there are other broadcasts from sendThisDeviceChangedBroadcast() called by
            // updateThisDevice()
            verify(mContext, times(6)).sendBroadcastWithMultiplePermissions(intentCaptor.capture(),
                    permissionCaptor.capture());
        } else {
            verify(mContext, times(3)).sendBroadcastWithMultiplePermissions(intentCaptor.capture(),
                    permissionCaptor.capture());
        }
        ArrayList<Intent> intentArrayList = new ArrayList<>();
        ArrayList<String[]> permissionArrayList = new ArrayList<>();
        for (int i = 0; i < intentCaptor.getAllValues().size(); i++) {
            Intent intent = intentCaptor.getAllValues().get(i);
            if (intent.getAction().equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)) {
                intentArrayList.add(intent);
                permissionArrayList.add(permissionCaptor.getAllValues().get(i));
            }
        }
        assertEquals(3, intentArrayList.size());
        /* Total three WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION broadcasts are as follows.
         * ----------------------------------------------------------------------------------------
         * State    p2pInfo.groupFormed    p2pGroup    intent.permission intent.flag intent.package
         * ----------------------------------------------------------------------------------------
         * PreU:
         * P2pEnabledState      :false  null         RECEIVER_PERMISSIONS_FOR_BROADCAST(location ON)
         *         Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
         *         null
         * GroupCreatingState   :false p2pGroup.isGo RECEIVER_PERMISSIONS_FOR_BROADCAST(location ON)
         *         Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
         *         null
         * GroupNegotiationState:true  p2pGroup.isGo android.Manifest.permission.TETHER_PRIVILEGED
         *         Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
         *         com.android.networkstack.tethering
         *
         * PostU:
         * P2pEnabledState      : false  null           android.permission.MAINLINE_NETWORK_STACK
         *         Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT|Intent.FLAG_RECEIVER_FOREGROUND
         *         null
         * GroupCreatingState   : false  p2pGroup.isGo  android.permission.MAINLINE_NETWORK_STACK
         *         Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT|Intent.FLAG_RECEIVER_FOREGROUND
         *         null
         * GroupNegotiationState: true   p2pGroup.isGo  android.permission.MAINLINE_NETWORK_STACK
         *         Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT|Intent.FLAG_RECEIVER_FOREGROUND
         *         com.android.networkstack.tethering
         */
        Intent intent = intentArrayList.get(1);
        assertEquals(flags, intent.getFlags());
        assertNull(intent.getPackage());
        WifiP2pInfo p2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
        assert p2pInfo != null;
        assertFalse(p2pInfo.groupFormed);
        WifiP2pGroup p2pGroup = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
        assert p2pGroup != null;
        assertTrue(p2pGroup.isGroupOwner());
        assertEquals(receiverPermissions, permissionArrayList.get(1));

        intent = intentArrayList.get(2);
        assertEquals(flags, intent.getFlags());
        assertNotNull(intent.getPackage());
        p2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
        assert p2pInfo != null;
        assertTrue(p2pInfo.groupFormed);
        p2pGroup = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
        assert p2pGroup != null;
        assertTrue(p2pGroup.isGroupOwner());
        if (!SdkLevel.isAtLeastU()) {
            receiverPermissions = new String[]{android.Manifest.permission.TETHER_PRIVILEGED};
        }
        assertEquals(receiverPermissions, permissionArrayList.get(2));
    }

    /**
     * Verify that p2p doesn't disable when wifi disabled and D2d is allowed when
     * infra sta is disabled.
     */
    @Test
    public void testP2pDoesInitWhenClientConnectWithWifiDisabledAndD2DAllowed()
            throws Exception {
        when(mWifiSettingsConfigStore.get(eq(D2D_ALLOWED_WHEN_INFRA_STA_DISABLED)))
                .thenReturn(true);
        when(mWifiGlobals.isD2dSupportedWhenInfraStaDisabled()).thenReturn(true);
        when(mFeatureFlags.d2dWhenInfraStaOff()).thenReturn(true);
        simulateWifiStateChange(false);
        checkIsP2pInitWhenClientConnected(true, mClient1,
                new WorkSource(mClient1.getCallingUid(), TEST_PACKAGE_NAME));
    }

    @Test
    public void testWifiP2pListener() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        forceP2pEnabled(mClient1);
        when(mWifiNative.p2pGroupAdd(any(), eq(false))).thenReturn(true);
        WifiP2pGroup p2pGroup = new WifiP2pGroup();
        p2pGroup.setNetworkId(WifiP2pGroup.NETWORK_ID_PERSISTENT);
        p2pGroup.setNetworkName(TEST_NETWORK_NAME);
        p2pGroup.setOwner(new WifiP2pDevice("thisDeviceMac"));
        p2pGroup.setIsGroupOwner(true);
        p2pGroup.setInterface(IFACE_NAME_P2P);

        sendCreateGroupMsgWithConfigValidAsGroup(mClientMessenger);
        sendGroupStartedMsg(p2pGroup);
        simulateTetherReady();
        verify(mP2pListener).onGroupCreating();
        ArgumentCaptor<WifiP2pInfo> p2pInfoCaptor = ArgumentCaptor.forClass(WifiP2pInfo.class);
        ArgumentCaptor<WifiP2pGroup> p2pGroupCaptor = ArgumentCaptor.forClass(WifiP2pGroup.class);
        verify(mP2pListener).onGroupCreated(p2pInfoCaptor.capture(), p2pGroupCaptor.capture());
        assertEquals(TEST_NETWORK_NAME, p2pGroupCaptor.getValue().getNetworkName());
        assertTrue(p2pGroupCaptor.getValue().isClientListEmpty());
        assertTrue(p2pInfoCaptor.getValue().groupFormed);

        WifiP2pDevice peerClientDevice = new WifiP2pDevice();
        peerClientDevice.deviceName = "peerClientDeviceName";
        peerClientDevice.deviceAddress = "11:22:33:aa:bb:cc";
        peerClientDevice.setInterfaceMacAddress(MacAddress.fromString(PEER_INTERFACE_ADDRESS));
        sendSimpleMsg(null, WifiP2pMonitor.AP_STA_CONNECTED_EVENT, peerClientDevice);
        verify(mP2pListener).onPeerClientJoined(p2pInfoCaptor.capture(),
                p2pGroupCaptor.capture());
        assertFalse(p2pGroupCaptor.getValue().isClientListEmpty());
        assertTrue(p2pInfoCaptor.getValue().groupFormed);

        sendSimpleMsg(null, WifiP2pMonitor.AP_STA_DISCONNECTED_EVENT, peerClientDevice);
        verify(mP2pListener).onPeerClientDisconnected(p2pInfoCaptor.capture(),
                p2pGroupCaptor.capture());
        assertTrue(p2pGroupCaptor.getValue().isClientListEmpty());
        assertTrue(p2pInfoCaptor.getValue().groupFormed);
        verify(mP2pListener, atLeastOnce()).onPeerListChanged(any());

        sendSimpleMsg(null, WifiP2pMonitor.P2P_FREQUENCY_CHANGED_EVENT, TEST_GROUP_FREQUENCY);
        verify(mP2pListener).onFrequencyChanged(p2pInfoCaptor.capture(),
                p2pGroupCaptor.capture());
        assertEquals(TEST_GROUP_FREQUENCY, p2pGroupCaptor.getValue().getFrequency());

        sendGroupRemovedMsg();
        mockEnterDisabledState();
        mLooper.dispatchAll();
        verify(mP2pListener).onGroupRemoved();

        mWifiP2pServiceImpl.unregisterWifiP2pListener(mP2pListener);
        doThrow(new SecurityException()).when(mWifiPermissionsUtil).enforceNearbyDevicesPermission(
                any(), anyBoolean(), any());
        assertThrows(SecurityException.class,
                () -> mWifiP2pServiceImpl.registerWifiP2pListener(mP2pListener, TEST_PACKAGE_NAME,
                        mExtras));
    }

    /**
     * Verify that p2p disable when the D2d allowed value changes to false
     */
    @Test
    public void testP2pChangeToDisableWhenD2DAllowedToFalse()
            throws Exception {
        when(mWifiSettingsConfigStore.get(eq(D2D_ALLOWED_WHEN_INFRA_STA_DISABLED)))
                .thenReturn(true);
        when(mWifiGlobals.isD2dSupportedWhenInfraStaDisabled()).thenReturn(true);
        when(mFeatureFlags.d2dWhenInfraStaOff()).thenReturn(true);
        simulateWifiStateChange(false);
        checkIsP2pInitWhenClientConnected(true, mClient1,
                new WorkSource(mClient1.getCallingUid(), TEST_PACKAGE_NAME));
        when(mWifiSettingsConfigStore.get(eq(D2D_ALLOWED_WHEN_INFRA_STA_DISABLED)))
                .thenReturn(false);
        mD2DAllowedSettingsCallbackCaptor.getValue().onSettingsChanged(
                D2D_ALLOWED_WHEN_INFRA_STA_DISABLED, false);
        mLooper.dispatchAll();
        // P2P is really disabled when wifi is off.
        verify(mWifiNative).teardownInterface();
        verify(mWifiMonitor).stopMonitoring(anyString());
    }
}
