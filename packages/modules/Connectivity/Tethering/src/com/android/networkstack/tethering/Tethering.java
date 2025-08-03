/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.networkstack.tethering;

import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.Manifest.permission.NETWORK_STACK;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.hardware.usb.UsbManager.USB_CONFIGURED;
import static android.hardware.usb.UsbManager.USB_CONNECTED;
import static android.hardware.usb.UsbManager.USB_FUNCTION_NCM;
import static android.hardware.usb.UsbManager.USB_FUNCTION_RNDIS;
import static android.net.ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.net.ConnectivityManager.EXTRA_NETWORK_INFO;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
import static android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK;
import static android.net.TetheringManager.ACTION_TETHER_STATE_CHANGED;
import static android.net.TetheringManager.CONNECTIVITY_SCOPE_LOCAL;
import static android.net.TetheringManager.EXTRA_ACTIVE_LOCAL_ONLY;
import static android.net.TetheringManager.EXTRA_ACTIVE_TETHER;
import static android.net.TetheringManager.EXTRA_AVAILABLE_TETHER;
import static android.net.TetheringManager.EXTRA_ERRORED_TETHER;
import static android.net.TetheringManager.TETHERING_BLUETOOTH;
import static android.net.TetheringManager.TETHERING_ETHERNET;
import static android.net.TetheringManager.TETHERING_INVALID;
import static android.net.TetheringManager.TETHERING_NCM;
import static android.net.TetheringManager.TETHERING_USB;
import static android.net.TetheringManager.TETHERING_WIFI;
import static android.net.TetheringManager.TETHERING_WIFI_P2P;
import static android.net.TetheringManager.TETHERING_WIGIG;
import static android.net.TetheringManager.TETHER_ERROR_INTERNAL_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_NO_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_SERVICE_UNAVAIL;
import static android.net.TetheringManager.TETHER_ERROR_UNAVAIL_IFACE;
import static android.net.TetheringManager.TETHER_ERROR_UNKNOWN_IFACE;
import static android.net.TetheringManager.TETHER_ERROR_UNKNOWN_TYPE;
import static android.net.TetheringManager.TETHER_HARDWARE_OFFLOAD_FAILED;
import static android.net.TetheringManager.TETHER_HARDWARE_OFFLOAD_STARTED;
import static android.net.TetheringManager.TETHER_HARDWARE_OFFLOAD_STOPPED;
import static android.net.TetheringManager.toIfaces;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_INTERFACE_NAME;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_MODE;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_STATE;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_CONFIGURATION_ERROR;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_LOCAL_ONLY;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_TETHERED;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_UNSPECIFIED;
import static android.net.wifi.WifiManager.SoftApCallback;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_DISABLED;
import static android.telephony.CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import static com.android.networkstack.tethering.TetheringConfiguration.TETHER_FORCE_USB_FUNCTIONS;
import static com.android.networkstack.tethering.TetheringNotificationUpdater.DOWNSTREAM_NONE;
import static com.android.networkstack.tethering.UpstreamNetworkMonitor.isCellular;
import static com.android.networkstack.tethering.util.TetheringMessageBase.BASE_MAIN_SM;

import android.app.usage.NetworkStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.EthernetManager;
import android.net.IIntResultListener;
import android.net.INetd;
import android.net.ITetheringEventCallback;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.RoutingCoordinatorManager;
import android.net.TetherStatesParcel;
import android.net.TetheredClient;
import android.net.TetheringCallbackStartedParcel;
import android.net.TetheringConfigurationParcel;
import android.net.TetheringInterface;
import android.net.TetheringManager.TetheringRequest;
import android.net.TetheringRequestParcel;
import android.net.Uri;
import android.net.ip.IpServer;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceSpecificException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.MessageUtils;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.BaseNetdUnsolicitedEventListener;
import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.HandlerUtils;
import com.android.net.module.util.NetdUtils;
import com.android.net.module.util.SdkUtil.LateSdk;
import com.android.net.module.util.SharedLog;
import com.android.networkstack.apishim.common.BluetoothPanShim;
import com.android.networkstack.apishim.common.BluetoothPanShim.TetheredInterfaceCallbackShim;
import com.android.networkstack.apishim.common.BluetoothPanShim.TetheredInterfaceRequestShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;
import com.android.networkstack.tethering.metrics.TetheringMetrics;
import com.android.networkstack.tethering.util.InterfaceSet;
import com.android.networkstack.tethering.util.PrefixUtils;
import com.android.networkstack.tethering.util.TetheringUtils;
import com.android.networkstack.tethering.util.VersionedBroadcastListener;
import com.android.networkstack.tethering.wear.WearableConnectionManager;

import lineageos.providers.LineageSettings;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 *
 * This class holds much of the business logic to allow Android devices
 * to act as IP gateways via USB, BT, and WiFi interfaces.
 */
public class Tethering {

    private static final String TAG = Tethering.class.getSimpleName();
    private static final boolean DBG = false;
    private static final boolean VDBG = false;

    private static final Class[] sMessageClasses = {
            Tethering.class, TetherMainSM.class, IpServer.class
    };
    private static final SparseArray<String> sMagicDecoderRing =
            MessageUtils.findMessageNames(sMessageClasses);

    private static final int DUMP_TIMEOUT_MS = 10_000;

    // Keep in sync with NETID_UNSET in system/netd/include/netid_client.h
    private static final int NETID_UNSET = 0;

    private static class TetherState {
        public final IpServer ipServer;
        public int lastState;
        public int lastError;
        // This field only valid for TETHERING_USB and TETHERING_NCM.
        // TODO: Change this from boolean to int for extension.
        public final boolean isNcm;

        TetherState(IpServer ipServer, boolean isNcm) {
            this.ipServer = ipServer;
            // Assume all state machines start out available and with no errors.
            lastState = IpServer.STATE_AVAILABLE;
            lastError = TETHER_ERROR_NO_ERROR;
            this.isNcm = isNcm;
        }

        public boolean isCurrentlyServing() {
            switch (lastState) {
                case IpServer.STATE_TETHERED:
                case IpServer.STATE_LOCAL_ONLY:
                    return true;
                default:
                    return false;
            }
        }
    }

    /**
     * Cookie added when registering {@link android.net.TetheringManager.TetheringEventCallback}.
     */
    private static class CallbackCookie {
        public final boolean hasListClientsPermission;

        private CallbackCookie(boolean hasListClientsPermission) {
            this.hasListClientsPermission = hasListClientsPermission;
        }
    }

    private final SharedLog mLog = new SharedLog(TAG);
    private final RemoteCallbackList<ITetheringEventCallback> mTetheringEventCallbacks =
            new RemoteCallbackList<>();
    // Currently active tethering requests per tethering type. Only one of each type can be
    // requested at a time. After a tethering type is requested, the map keeps tethering parameters
    // to be used after the interface comes up asynchronously.
    private final SparseArray<TetheringRequestParcel> mActiveTetheringRequests =
            new SparseArray<>();

    private final Context mContext;
    private final ArrayMap<String, TetherState> mTetherStates;
    private final BroadcastReceiver mStateReceiver;
    private final Looper mLooper;
    private final TetherMainSM mTetherMainSM;
    private final OffloadController mOffloadController;
    private final UpstreamNetworkMonitor mUpstreamNetworkMonitor;
    // TODO: Figure out how to merge this and other downstream-tracking objects
    // into a single coherent structure.
    private final HashSet<IpServer> mForwardedDownstreams;
    private final VersionedBroadcastListener mCarrierConfigChange;
    private final TetheringDependencies mDeps;
    private final EntitlementManager mEntitlementMgr;
    private final Handler mHandler;
    private final INetd mNetd;
    private final NetdCallback mNetdCallback;
    // Contains null if the connectivity module is unsupported, as the routing coordinator is not
    // available. Must use LateSdk because MessageUtils enumerates fields in this class, so it
    // must be able to find all classes at runtime.
    @NonNull private final LateSdk<RoutingCoordinatorManager> mRoutingCoordinator;
    private final UserRestrictionActionListener mTetheringRestriction;
    private final ActiveDataSubIdListener mActiveDataSubIdListener;
    private final ConnectedClientsTracker mConnectedClientsTracker;
    private final TetheringThreadExecutor mExecutor;
    private final TetheringNotificationUpdater mNotificationUpdater;
    private final UserManager mUserManager;
    private final BpfCoordinator mBpfCoordinator;
    private final PrivateAddressCoordinator mPrivateAddressCoordinator;
    private final TetheringMetrics mTetheringMetrics;
    private final WearableConnectionManager mWearableConnectionManager;
    private int mActiveDataSubId = INVALID_SUBSCRIPTION_ID;

    private volatile TetheringConfiguration mConfig;
    private InterfaceSet mCurrentUpstreamIfaceSet;

    private boolean mRndisEnabled;       // track the RNDIS function enabled state
    private boolean mNcmEnabled;         // track the NCM function enabled state
    // True iff. WiFi tethering should be started when soft AP is ready.
    private boolean mWifiTetherRequested;
    private Network mTetherUpstream;
    private TetherStatesParcel mTetherStatesParcel;
    private boolean mDataSaverEnabled = false;
    private String mWifiP2pTetherInterface = null;
    private int mOffloadStatus = TETHER_HARDWARE_OFFLOAD_STOPPED;

    private EthernetManager.TetheredInterfaceRequest mEthernetIfaceRequest;
    private TetheredInterfaceRequestShim mBluetoothIfaceRequest;
    private String mConfiguredEthernetIface;
    private String mConfiguredBluetoothIface;
    private EthernetCallback mEthernetCallback;
    private TetheredInterfaceCallbackShim mBluetoothCallback;
    private SettingsObserver mSettingsObserver;
    private BluetoothPan mBluetoothPan;
    private PanServiceListener mBluetoothPanListener;
    private ArrayList<Pair<Boolean, IIntResultListener>> mPendingPanRequests;
    // AIDL doesn't support Set<Integer>. Maintain a int bitmap here. When the bitmap is passed to
    // TetheringManager, TetheringManager would convert it to a set of Integer types.
    // mSupportedTypeBitmap should always be updated inside tethering internal thread but it may be
    // read from binder thread which called TetheringService directly.
    private volatile long mSupportedTypeBitmap;

    public Tethering(TetheringDependencies deps) {
        mLog.mark("Tethering.constructed");
        mDeps = deps;
        mContext = mDeps.getContext();
        mNetd = mDeps.getINetd(mContext);
        mRoutingCoordinator = mDeps.getRoutingCoordinator(mContext);
        mLooper = mDeps.makeTetheringLooper();
        mNotificationUpdater = mDeps.makeNotificationUpdater(mContext, mLooper);
        mTetheringMetrics = mDeps.makeTetheringMetrics();

        // This is intended to ensrure that if something calls startTethering(bluetooth) just after
        // bluetooth is enabled. Before onServiceConnected is called, store the calls into this
        // list and handle them as soon as onServiceConnected is called.
        mPendingPanRequests = new ArrayList<>();

        mTetherStates = new ArrayMap<>();
        mConnectedClientsTracker = new ConnectedClientsTracker();

        mTetherMainSM = new TetherMainSM("TetherMain", mLooper, deps);
        mTetherMainSM.start();

        mHandler = mTetherMainSM.getHandler();
        mOffloadController = mDeps.makeOffloadController(mHandler, mLog,
                new OffloadController.Dependencies() {

                    @Override
                    public TetheringConfiguration getTetherConfig() {
                        return mConfig;
                    }
                });
        mUpstreamNetworkMonitor = mDeps.makeUpstreamNetworkMonitor(mContext, mHandler, mLog,
                (what, obj) -> {
                    mTetherMainSM.sendMessage(TetherMainSM.EVENT_UPSTREAM_CALLBACK, what, 0, obj);
                });
        mForwardedDownstreams = new HashSet<>();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CARRIER_CONFIG_CHANGED);
        // EntitlementManager will send EVENT_UPSTREAM_PERMISSION_CHANGED when cellular upstream
        // permission is changed according to entitlement check result.
        mEntitlementMgr = mDeps.makeEntitlementManager(mContext, mHandler, mLog,
                () -> mTetherMainSM.sendMessage(
                TetherMainSM.EVENT_UPSTREAM_PERMISSION_CHANGED));
        mEntitlementMgr.setOnTetherProvisioningFailedListener((downstream, reason) -> {
            mLog.log("OBSERVED OnTetherProvisioningFailed : " + reason);
            stopTethering(downstream);
        });
        mEntitlementMgr.setTetheringConfigurationFetcher(() -> {
            return mConfig;
        });

        mCarrierConfigChange = new VersionedBroadcastListener(
                "CarrierConfigChangeListener", mContext, mHandler, filter,
                (Intent ignored) -> {
                    mLog.log("OBSERVED carrier config change");
                    updateConfiguration();
                    mEntitlementMgr.reevaluateSimCardProvisioning(mConfig);
                });

        mSettingsObserver = new SettingsObserver(mContext, mHandler);
        mSettingsObserver.startObserve();

        mStateReceiver = new StateReceiver();

        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        mTetheringRestriction = new UserRestrictionActionListener(
                mUserManager, this, mNotificationUpdater);
        mExecutor = new TetheringThreadExecutor(mHandler);
        mActiveDataSubIdListener = new ActiveDataSubIdListener(mExecutor);
        mNetdCallback = new NetdCallback();

        // Load tethering configuration.
        updateConfiguration();
        mConfig.readEnableSyncSM(mContext);
        // It is OK for the configuration to be passed to the PrivateAddressCoordinator at
        // construction time because the only part of the configuration it uses is
        // shouldEnableWifiP2pDedicatedIp(), and currently do not support changing that.
        mPrivateAddressCoordinator = mDeps.makePrivateAddressCoordinator(mContext, mConfig);

        // Must be initialized after tethering configuration is loaded because BpfCoordinator
        // constructor needs to use the configuration.
        mBpfCoordinator = mDeps.makeBpfCoordinator(
                new BpfCoordinator.Dependencies() {
                    @NonNull
                    public Handler getHandler() {
                        return mHandler;
                    }

                    @NonNull
                    public INetd getNetd() {
                        return mNetd;
                    }

                    @NonNull
                    public NetworkStatsManager getNetworkStatsManager() {
                        return mContext.getSystemService(NetworkStatsManager.class);
                    }

                    @NonNull
                    public SharedLog getSharedLog() {
                        return mLog;
                    }

                    @Nullable
                    public TetheringConfiguration getTetherConfig() {
                        return mConfig;
                    }
                });

        if (SdkLevel.isAtLeastT() && mConfig.isWearTetheringEnabled()) {
            mWearableConnectionManager = mDeps.makeWearableConnectionManager(mContext);
        } else {
            mWearableConnectionManager = null;
        }

        startStateMachineUpdaters();
    }

    private class SettingsObserver extends ContentObserver {
        private final Uri mForceUsbFunctions;
        private final Uri mTetherSupported;
        private final Context mContext;

        SettingsObserver(Context ctx, Handler handler) {
            super(handler);
            mContext = ctx;
            mForceUsbFunctions = Settings.Global.getUriFor(TETHER_FORCE_USB_FUNCTIONS);
            mTetherSupported = Settings.Global.getUriFor(Settings.Global.TETHER_SUPPORTED);
        }

        public void startObserve() {
            mContext.getContentResolver().registerContentObserver(mForceUsbFunctions, false, this);
            mContext.getContentResolver().registerContentObserver(mTetherSupported, false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            Log.wtf(TAG, "Should never be reached.");
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (mForceUsbFunctions.equals(uri)) {
                mLog.i("OBSERVED TETHER_FORCE_USB_FUNCTIONS settings change");
                final boolean isUsingNcm = mConfig.isUsingNcm();
                updateConfiguration();
                if (isUsingNcm != mConfig.isUsingNcm()) {
                    stopTetheringInternal(TETHERING_USB);
                    stopTetheringInternal(TETHERING_NCM);
                }
            } else if (mTetherSupported.equals(uri)) {
                mLog.i("OBSERVED TETHER_SUPPORTED settings change");
                updateSupportedDownstreams(mConfig);
            } else {
                mLog.e("Unexpected settings change: " + uri);
            }
        }
    }

    @VisibleForTesting
    ContentObserver getSettingsObserverForTest() {
        return mSettingsObserver;
    }

    /**
     * Start to register callbacks.
     * Call this function when tethering is ready to handle callback events.
     */
    private void startStateMachineUpdaters() {
        try {
            mNetd.registerUnsolicitedEventListener(mNetdCallback);
        } catch (RemoteException e) {
            mLog.e("Unable to register netd UnsolicitedEventListener");
        }
        mCarrierConfigChange.startListening();
        mContext.getSystemService(TelephonyManager.class).listen(mActiveDataSubIdListener,
                PhoneStateListener.LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_STATE);
        filter.addAction(CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(UserManager.ACTION_USER_RESTRICTIONS_CHANGED);
        filter.addAction(ACTION_RESTRICT_BACKGROUND_CHANGED);
        mContext.registerReceiver(mStateReceiver, filter, null, mHandler);

        final IntentFilter noUpstreamFilter = new IntentFilter();
        noUpstreamFilter.addAction(TetheringNotificationUpdater.ACTION_DISABLE_TETHERING);
        mContext.registerReceiver(
                mStateReceiver, noUpstreamFilter, PERMISSION_MAINLINE_NETWORK_STACK, mHandler);

        final WifiManager wifiManager = getWifiManager();
        if (wifiManager != null) {
            wifiManager.registerSoftApCallback(mExecutor, new TetheringSoftApCallback());
            if (SdkLevel.isAtLeastT()) {
                // Although WifiManager#registerLocalOnlyHotspotSoftApCallback document that it need
                // NEARBY_WIFI_DEVICES permission, but actually a caller who have NETWORK_STACK
                // or MAINLINE_NETWORK_STACK permission can also use this API.
                wifiManager.registerLocalOnlyHotspotSoftApCallback(mExecutor,
                        new LocalOnlyHotspotCallback());
            }
        }

        startTrackDefaultNetwork();

        // Listen for allowing tethering upstream via VPN settings changes
        final ContentObserver vpnSettingObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean self) {
                // Reconsider tethering upstream
                mUpstreamNetworkMonitor.maybeUpdateDefaultNetworkCallback();
            }
        };
        mContext.getContentResolver().registerContentObserver(LineageSettings.Secure.getUriFor(
                LineageSettings.Secure.TETHERING_ALLOW_VPN_UPSTREAMS), false, vpnSettingObserver);
    }

    private class TetheringThreadExecutor implements Executor {
        private final Handler mTetherHandler;
        TetheringThreadExecutor(Handler handler) {
            mTetherHandler = handler;
        }
        @Override
        public void execute(Runnable command) {
            if (!mTetherHandler.post(command)) {
                throw new RejectedExecutionException(mTetherHandler + " is shutting down");
            }
        }
    }

    private class ActiveDataSubIdListener extends PhoneStateListener {
        ActiveDataSubIdListener(Executor executor) {
            super(executor);
        }

        @Override
        public void onActiveDataSubscriptionIdChanged(int subId) {
            mLog.log("OBSERVED active data subscription change, from " + mActiveDataSubId
                    + " to " + subId);
            if (subId == mActiveDataSubId) return;

            mActiveDataSubId = subId;
            updateConfiguration();
            mNotificationUpdater.onActiveDataSubscriptionIdChanged(subId);
            // To avoid launching unexpected provisioning checks, ignore re-provisioning
            // when no CarrierConfig loaded yet. Assume reevaluateSimCardProvisioning()
            // will be triggered again when CarrierConfig is loaded.
            if (TetheringConfiguration.getCarrierConfig(mContext, subId) != null) {
                mEntitlementMgr.reevaluateSimCardProvisioning(mConfig);
            } else {
                mLog.log("IGNORED reevaluate provisioning, no carrier config loaded");
            }
        }
    }

    private WifiManager getWifiManager() {
        return (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }

    // NOTE: This is always invoked on the mLooper thread.
    private void updateConfiguration() {
        mConfig = mDeps.generateTetheringConfiguration(mContext, mLog, mActiveDataSubId);
        mUpstreamNetworkMonitor.setUpstreamConfig(mConfig.chooseUpstreamAutomatically,
                mConfig.isDunRequired);
        reportConfigurationChanged(mConfig.toStableParcelable());

        updateSupportedDownstreams(mConfig);
    }

    private void maybeDunSettingChanged() {
        final boolean isDunRequired = TetheringConfiguration.checkDunRequired(mContext);
        if (isDunRequired == mConfig.isDunRequired) return;
        updateConfiguration();
    }

    private class NetdCallback extends BaseNetdUnsolicitedEventListener {
        @Override
        public void onInterfaceChanged(String ifName, boolean up) {
            mHandler.post(() -> interfaceStatusChanged(ifName, up));
        }

        @Override
        public void onInterfaceLinkStateChanged(String ifName, boolean up) {
            mHandler.post(() -> interfaceLinkStateChanged(ifName, up));
        }

        @Override
        public void onInterfaceAdded(String ifName) {
            mHandler.post(() -> interfaceAdded(ifName));
        }

        @Override
        public void onInterfaceRemoved(String ifName) {
            mHandler.post(() -> interfaceRemoved(ifName));
        }
    }

    private class TetheringSoftApCallback implements SoftApCallback {
        @Override
        public void onConnectedClientsChanged(final List<WifiClient> clients) {
            updateConnectedClients(clients, null);
        }
    }

    private class LocalOnlyHotspotCallback implements SoftApCallback {
        @Override
        public void onConnectedClientsChanged(final List<WifiClient> clients) {
            updateConnectedClients(null, clients);
        }
    }

    // This method needs to exist because TETHERING_BLUETOOTH before Android T and TETHERING_WIGIG
    // can't use enableIpServing.
    private void processInterfaceStateChange(final String iface, boolean enabled) {
        // Do not listen to USB interface state changes or USB interface add/removes. USB tethering
        // is driven only by USB_ACTION broadcasts.
        final int type = ifaceNameToType(iface);
        if (type == TETHERING_USB || type == TETHERING_NCM) return;

        if (type == TETHERING_BLUETOOTH && SdkLevel.isAtLeastT()) return;

        if (enabled) {
            ensureIpServerStarted(iface);
        } else {
            ensureIpServerStopped(iface);
        }
    }

    void interfaceStatusChanged(String iface, boolean up) {
        // Never called directly: only called from interfaceLinkStateChanged.
        // See NetlinkHandler.cpp: notifyInterfaceChanged.
        if (VDBG) Log.d(TAG, "interfaceStatusChanged " + iface + ", " + up);

        final int type = ifaceNameToType(iface);
        if (!up && type != TETHERING_BLUETOOTH && type != TETHERING_WIGIG) {
            // Ignore usb interface down after enabling RNDIS.
            // We will handle disconnect in interfaceRemoved.
            // Similarly, ignore interface down for WiFi.  We monitor WiFi AP status
            // through the WifiManager.WIFI_AP_STATE_CHANGED_ACTION intent.
            if (VDBG) Log.d(TAG, "ignore interface down for " + iface);
            return;
        }

        processInterfaceStateChange(iface, up);
    }

    void interfaceLinkStateChanged(String iface, boolean up) {
        interfaceStatusChanged(iface, up);
    }

    private int ifaceNameToType(String iface) {
        final TetheringConfiguration cfg = mConfig;

        if (cfg.isWifi(iface)) {
            return TETHERING_WIFI;
        } else if (cfg.isWigig(iface)) {
            return TETHERING_WIGIG;
        } else if (cfg.isWifiP2p(iface)) {
            return TETHERING_WIFI_P2P;
        } else if (cfg.isUsb(iface)) {
            return TETHERING_USB;
        } else if (cfg.isBluetooth(iface)) {
            return TETHERING_BLUETOOTH;
        } else if (cfg.isNcm(iface)) {
            return TETHERING_NCM;
        }
        return TETHERING_INVALID;
    }

    void interfaceAdded(String iface) {
        if (VDBG) Log.d(TAG, "interfaceAdded " + iface);
        processInterfaceStateChange(iface, true /* enabled */);
    }

    void interfaceRemoved(String iface) {
        if (VDBG) Log.d(TAG, "interfaceRemoved " + iface);
        processInterfaceStateChange(iface, false /* enabled */);
    }

    void startTethering(final TetheringRequestParcel request, final String callerPkg,
            final IIntResultListener listener) {
        mHandler.post(() -> {
            final TetheringRequestParcel unfinishedRequest = mActiveTetheringRequests.get(
                    request.tetheringType);
            // If tethering is already enabled with a different request,
            // disable before re-enabling.
            if (unfinishedRequest != null
                    && !TetheringUtils.isTetheringRequestEquals(unfinishedRequest, request)) {
                enableTetheringInternal(request.tetheringType, false /* disabled */, null);
                mEntitlementMgr.stopProvisioningIfNeeded(request.tetheringType);
            }
            mActiveTetheringRequests.put(request.tetheringType, request);

            if (request.exemptFromEntitlementCheck) {
                mEntitlementMgr.setExemptedDownstreamType(request.tetheringType);
            } else {
                mEntitlementMgr.startProvisioningIfNeeded(request.tetheringType,
                        request.showProvisioningUi);
            }
            enableTetheringInternal(request.tetheringType, true /* enabled */, listener);
            mTetheringMetrics.createBuilder(request.tetheringType, callerPkg);
        });
    }

    void stopTethering(int type) {
        mHandler.post(() -> {
            stopTetheringInternal(type);
        });
    }
    void stopTetheringInternal(int type) {
        mActiveTetheringRequests.remove(type);

        enableTetheringInternal(type, false /* disabled */, null);
        mEntitlementMgr.stopProvisioningIfNeeded(type);
    }

    /**
     * Enables or disables tethering for the given type. If provisioning is required, it will
     * schedule provisioning rechecks for the specified interface.
     */
    private void enableTetheringInternal(int type, boolean enable,
            final IIntResultListener listener) {
        int result = TETHER_ERROR_NO_ERROR;
        switch (type) {
            case TETHERING_WIFI:
                result = setWifiTethering(enable);
                break;
            case TETHERING_USB:
                result = setUsbTethering(enable);
                break;
            case TETHERING_BLUETOOTH:
                setBluetoothTethering(enable, listener);
                break;
            case TETHERING_NCM:
                result = setNcmTethering(enable);
                break;
            case TETHERING_ETHERNET:
                result = setEthernetTethering(enable);
                break;
            default:
                Log.w(TAG, "Invalid tether type.");
                result = TETHER_ERROR_UNKNOWN_TYPE;
        }

        // The result of Bluetooth tethering will be sent by #setBluetoothTethering.
        if (type != TETHERING_BLUETOOTH) {
            sendTetherResult(listener, result, type);
        }
    }

    private void sendTetherResult(final IIntResultListener listener, final int result,
            final int type) {
        if (listener != null) {
            try {
                listener.onResult(result);
            } catch (RemoteException e) { }
        }

        // If changing tethering fail, remove corresponding request
        // no matter who trigger the start/stop.
        if (result != TETHER_ERROR_NO_ERROR) {
            mActiveTetheringRequests.remove(type);
            mTetheringMetrics.updateErrorCode(type, result);
            mTetheringMetrics.sendReport(type);
        }
    }

    private int setWifiTethering(final boolean enable) {
        final long ident = Binder.clearCallingIdentity();
        try {
            final WifiManager mgr = getWifiManager();
            if (mgr == null) {
                mLog.e("setWifiTethering: failed to get WifiManager!");
                return TETHER_ERROR_SERVICE_UNAVAIL;
            }
            if ((enable && mgr.startTetheredHotspot(null /* use existing softap config */))
                    || (!enable && mgr.stopSoftAp())) {
                mWifiTetherRequested = enable;
                return TETHER_ERROR_NO_ERROR;
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        return TETHER_ERROR_INTERNAL_ERROR;
    }

    private void setBluetoothTethering(final boolean enable, final IIntResultListener listener) {
        final BluetoothAdapter adapter = mDeps.getBluetoothAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Log.w(TAG, "Tried to enable bluetooth tethering with null or disabled adapter. null: "
                    + (adapter == null));
            sendTetherResult(listener, TETHER_ERROR_SERVICE_UNAVAIL, TETHERING_BLUETOOTH);
            return;
        }

        if (mBluetoothPanListener != null && mBluetoothPanListener.isConnected()) {
            // The PAN service is connected. Enable or disable bluetooth tethering.
            // When bluetooth tethering is enabled, any time a PAN client pairs with this
            // host, bluetooth will bring up a bt-pan interface and notify tethering to
            // enable IP serving.
            setBluetoothTetheringSettings(mBluetoothPan, enable, listener);
            return;
        }

        // The reference of IIntResultListener should only exist when application want to start
        // tethering but tethering is not bound to pan service yet. Even if the calling process
        // dies, the referenice of IIntResultListener would still keep in mPendingPanRequests. Once
        // tethering bound to pan service (onServiceConnected) or bluetooth just crash
        // (onServiceDisconnected), all the references from mPendingPanRequests would be cleared.
        mPendingPanRequests.add(new Pair(enable, listener));

        // Bluetooth tethering is not a popular feature. To avoid bind to bluetooth pan service all
        // the time but user never use bluetooth tethering. mBluetoothPanListener is created first
        // time someone calls a bluetooth tethering method (even if it's just to disable tethering
        // when it's already disabled) and never unset after that.
        if (mBluetoothPanListener == null) {
            mBluetoothPanListener = new PanServiceListener();
            adapter.getProfileProxy(mContext, mBluetoothPanListener, BluetoothProfile.PAN);
        }
    }

    private class PanServiceListener implements ServiceListener {
        private boolean mIsConnected = false;

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            // Posting this to handling onServiceConnected in tethering handler thread may have
            // race condition that bluetooth service may disconnected when tethering thread
            // actaully handle onServiceconnected. If this race happen, calling
            // BluetoothPan#setBluetoothTethering would silently fail. It is fine because pan
            // service is unreachable and both bluetooth and bluetooth tethering settings are off.
            mHandler.post(() -> {
                mBluetoothPan = (BluetoothPan) proxy;
                mIsConnected = true;

                for (Pair<Boolean, IIntResultListener> request : mPendingPanRequests) {
                    setBluetoothTetheringSettings(mBluetoothPan, request.first, request.second);
                }
                mPendingPanRequests.clear();
            });
        }

        @Override
        public void onServiceDisconnected(int profile) {
            mHandler.post(() -> {
                // onServiceDisconnected means Bluetooth is off (or crashed) and is not
                // reachable before next onServiceConnected.
                mIsConnected = false;

                for (Pair<Boolean, IIntResultListener> request : mPendingPanRequests) {
                    sendTetherResult(request.second, TETHER_ERROR_SERVICE_UNAVAIL,
                            TETHERING_BLUETOOTH);
                }
                mPendingPanRequests.clear();
                mBluetoothIfaceRequest = null;
                mBluetoothCallback = null;
                maybeDisableBluetoothIpServing();
            });
        }

        public boolean isConnected() {
            return mIsConnected;
        }
    }

    private void setBluetoothTetheringSettings(@NonNull final BluetoothPan bluetoothPan,
            final boolean enable, final IIntResultListener listener) {
        if (SdkLevel.isAtLeastT()) {
            changeBluetoothTetheringSettings(bluetoothPan, enable);
        } else {
            changeBluetoothTetheringSettingsPreT(bluetoothPan, enable);
        }

        // Enabling bluetooth tethering settings can silently fail. Send internal error if the
        // result is not expected.
        final int result = bluetoothPan.isTetheringOn() == enable
                ? TETHER_ERROR_NO_ERROR : TETHER_ERROR_INTERNAL_ERROR;
        sendTetherResult(listener, result, TETHERING_BLUETOOTH);
    }

    private void changeBluetoothTetheringSettingsPreT(@NonNull final BluetoothPan bluetoothPan,
            final boolean enable) {
        bluetoothPan.setBluetoothTethering(enable);
    }

    private void changeBluetoothTetheringSettings(@NonNull final BluetoothPan bluetoothPan,
            final boolean enable) {
        final BluetoothPanShim panShim = mDeps.makeBluetoothPanShim(bluetoothPan);
        if (enable) {
            if (mBluetoothIfaceRequest != null) {
                Log.d(TAG, "Bluetooth tethering settings already enabled");
                return;
            }

            mBluetoothCallback = new BluetoothCallback();
            try {
                mBluetoothIfaceRequest = panShim.requestTetheredInterface(mExecutor,
                        mBluetoothCallback);
            } catch (UnsupportedApiLevelException e) {
                Log.wtf(TAG, "Use unsupported API, " + e);
            }
        } else {
            if (mBluetoothIfaceRequest == null) {
                Log.d(TAG, "Bluetooth tethering settings already disabled");
                return;
            }

            mBluetoothIfaceRequest.release();
            mBluetoothIfaceRequest = null;
            mBluetoothCallback = null;
            // If bluetooth request is released, tethering won't able to receive
            // onUnavailable callback, explicitly disable bluetooth IpServer manually.
            maybeDisableBluetoothIpServing();
        }
    }

    // BluetoothCallback is only called after T. Before T, PanService would call tether/untether to
    // notify bluetooth interface status.
    private class BluetoothCallback implements TetheredInterfaceCallbackShim {
        @Override
        public void onAvailable(String iface) {
            if (this != mBluetoothCallback) return;

            enableIpServing(TETHERING_BLUETOOTH, iface, getRequestedState(TETHERING_BLUETOOTH));
            mConfiguredBluetoothIface = iface;
        }

        @Override
        public void onUnavailable() {
            if (this != mBluetoothCallback) return;

            maybeDisableBluetoothIpServing();
        }
    }

    private void maybeDisableBluetoothIpServing() {
        if (mConfiguredBluetoothIface == null) return;

        ensureIpServerStopped(mConfiguredBluetoothIface);
        mConfiguredBluetoothIface = null;
    }

    private int setEthernetTethering(final boolean enable) {
        final EthernetManager em = (EthernetManager) mContext.getSystemService(
                Context.ETHERNET_SERVICE);
        if (enable) {
            if (mEthernetCallback != null) {
                Log.d(TAG, "Ethernet tethering already started");
                return TETHER_ERROR_NO_ERROR;
            }

            mEthernetCallback = new EthernetCallback();
            mEthernetIfaceRequest = em.requestTetheredInterface(mExecutor, mEthernetCallback);
        } else {
            stopEthernetTethering();
        }
        return TETHER_ERROR_NO_ERROR;
    }

    private void stopEthernetTethering() {
        if (mConfiguredEthernetIface != null) {
            ensureIpServerStopped(mConfiguredEthernetIface);
            mConfiguredEthernetIface = null;
        }
        if (mEthernetCallback != null) {
            mEthernetIfaceRequest.release();
            mEthernetCallback = null;
            mEthernetIfaceRequest = null;
        }
    }

    private class EthernetCallback implements EthernetManager.TetheredInterfaceCallback {
        @Override
        public void onAvailable(String iface) {
            if (this != mEthernetCallback) {
                // Ethernet callback arrived after Ethernet tethering stopped. Ignore.
                return;
            }
            enableIpServing(TETHERING_ETHERNET, iface, getRequestedState(TETHERING_ETHERNET));
            mConfiguredEthernetIface = iface;
        }

        @Override
        public void onUnavailable() {
            if (this != mEthernetCallback) {
                // onAvailable called after stopping Ethernet tethering.
                return;
            }
            stopEthernetTethering();
        }
    }

    void tether(String iface, int requestedState, final IIntResultListener listener) {
        mHandler.post(() -> {
            try {
                listener.onResult(tether(iface, requestedState));
            } catch (RemoteException e) { }
        });
    }

    private int tether(String iface, int requestedState) {
        if (DBG) Log.d(TAG, "Tethering " + iface);
        TetherState tetherState = mTetherStates.get(iface);
        if (tetherState == null) {
            Log.e(TAG, "Tried to Tether an unknown iface: " + iface + ", ignoring");
            return TETHER_ERROR_UNKNOWN_IFACE;
        }
        // Ignore the error status of the interface.  If the interface is available,
        // the errors are referring to past tethering attempts anyway.
        if (tetherState.lastState != IpServer.STATE_AVAILABLE) {
            Log.e(TAG, "Tried to Tether an unavailable iface: " + iface + ", ignoring");
            return TETHER_ERROR_UNAVAIL_IFACE;
        }
        // NOTE: If a CMD_TETHER_REQUESTED message is already in the TISM's queue but not yet
        // processed, this will be a no-op and it will not return an error.
        //
        // This code cannot race with untether() because they both run on the handler thread.
        final int type = tetherState.ipServer.interfaceType();
        final TetheringRequestParcel request = mActiveTetheringRequests.get(type, null);
        if (request != null) {
            mActiveTetheringRequests.delete(type);
        }
        tetherState.ipServer.enable(requestedState, request);
        return TETHER_ERROR_NO_ERROR;
    }

    void untether(String iface, final IIntResultListener listener) {
        mHandler.post(() -> {
            try {
                listener.onResult(untether(iface));
            } catch (RemoteException e) {
            }
        });
    }

    int untether(String iface) {
        if (DBG) Log.d(TAG, "Untethering " + iface);
        TetherState tetherState = mTetherStates.get(iface);
        if (tetherState == null) {
            Log.e(TAG, "Tried to Untether an unknown iface :" + iface + ", ignoring");
            return TETHER_ERROR_UNKNOWN_IFACE;
        }
        if (!tetherState.isCurrentlyServing()) {
            Log.e(TAG, "Tried to untether an inactive iface :" + iface + ", ignoring");
            return TETHER_ERROR_UNAVAIL_IFACE;
        }
        tetherState.ipServer.unwanted();
        return TETHER_ERROR_NO_ERROR;
    }

    void untetherAll() {
        stopTethering(TETHERING_WIFI);
        stopTethering(TETHERING_WIFI_P2P);
        stopTethering(TETHERING_USB);
        stopTethering(TETHERING_BLUETOOTH);
        stopTethering(TETHERING_ETHERNET);
    }

    @VisibleForTesting
    int getLastErrorForTest(String iface) {
        TetherState tetherState = mTetherStates.get(iface);
        if (tetherState == null) {
            Log.e(TAG, "Tried to getLastErrorForTest on an unknown iface :" + iface
                    + ", ignoring");
            return TETHER_ERROR_UNKNOWN_IFACE;
        }
        return tetherState.lastError;
    }

    boolean isTetherProvisioningRequired() {
        final TetheringConfiguration cfg = mConfig;
        return mEntitlementMgr.isTetherProvisioningRequired(cfg);
    }

    private int getRequestedState(int type) {
        final TetheringRequestParcel request = mActiveTetheringRequests.get(type);

        // The request could have been deleted before we had a chance to complete it.
        // If so, assume that the scope is the default scope for this tethering type.
        // This likely doesn't matter - if the request has been deleted, then tethering is
        // likely going to be stopped soon anyway.
        final int connectivityScope = (request != null)
                ? request.connectivityScope
                : TetheringRequest.getDefaultConnectivityScope(type);

        return connectivityScope == CONNECTIVITY_SCOPE_LOCAL
                ? IpServer.STATE_LOCAL_ONLY
                : IpServer.STATE_TETHERED;
    }

    private int getServedUsbType(boolean forNcmFunction) {
        // TETHERING_NCM is only used if the device does not use NCM for regular USB tethering.
        if (forNcmFunction && !mConfig.isUsingNcm()) return TETHERING_NCM;

        return TETHERING_USB;
    }

    // TODO: Figure out how to update for local hotspot mode interfaces.
    private void sendTetherStateChangedBroadcast() {
        if (!isTetheringSupported()) return;

        final ArrayList<TetheringInterface> available = new ArrayList<>();
        final ArrayList<TetheringInterface> tethered = new ArrayList<>();
        final ArrayList<TetheringInterface> localOnly = new ArrayList<>();
        final ArrayList<TetheringInterface> errored = new ArrayList<>();
        final ArrayList<Integer> lastErrors = new ArrayList<>();

        int downstreamTypesMask = DOWNSTREAM_NONE;
        for (int i = 0; i < mTetherStates.size(); i++) {
            final TetherState tetherState = mTetherStates.valueAt(i);
            final int type = tetherState.ipServer.interfaceType();
            final String iface = mTetherStates.keyAt(i);
            final TetheringInterface tetheringIface = new TetheringInterface(type, iface);
            if (tetherState.lastError != TETHER_ERROR_NO_ERROR) {
                errored.add(tetheringIface);
                lastErrors.add(tetherState.lastError);
            } else if (tetherState.lastState == IpServer.STATE_AVAILABLE) {
                available.add(tetheringIface);
            } else if (tetherState.lastState == IpServer.STATE_LOCAL_ONLY) {
                localOnly.add(tetheringIface);
            } else if (tetherState.lastState == IpServer.STATE_TETHERED) {
                switch (type) {
                    case TETHERING_USB:
                    case TETHERING_WIFI:
                    case TETHERING_BLUETOOTH:
                        downstreamTypesMask |= (1 << type);
                        break;
                    default:
                        // Do nothing.
                }
                tethered.add(tetheringIface);
            }
        }

        mTetherStatesParcel = buildTetherStatesParcel(available, localOnly, tethered, errored,
                lastErrors);
        reportTetherStateChanged(mTetherStatesParcel);

        mContext.sendStickyBroadcastAsUser(buildStateChangeIntent(available, localOnly, tethered,
                errored), UserHandle.ALL);
        if (DBG) {
            Log.d(TAG, String.format(
                    "reportTetherStateChanged %s=[%s] %s=[%s] %s=[%s] %s=[%s]",
                    "avail", TextUtils.join(",", available),
                    "local_only", TextUtils.join(",", localOnly),
                    "tether", TextUtils.join(",", tethered),
                    "error", TextUtils.join(",", errored)));
        }

        mNotificationUpdater.onDownstreamChanged(downstreamTypesMask);
    }

    private TetherStatesParcel buildTetherStatesParcel(
            final ArrayList<TetheringInterface> available,
            final ArrayList<TetheringInterface> localOnly,
            final ArrayList<TetheringInterface> tethered,
            final ArrayList<TetheringInterface> errored,
            final ArrayList<Integer> lastErrors) {
        final TetherStatesParcel parcel = new TetherStatesParcel();

        parcel.availableList = available.toArray(new TetheringInterface[0]);
        parcel.tetheredList = tethered.toArray(new TetheringInterface[0]);
        parcel.localOnlyList = localOnly.toArray(new TetheringInterface[0]);
        parcel.erroredIfaceList = errored.toArray(new TetheringInterface[0]);
        parcel.lastErrorList = new int[lastErrors.size()];
        for (int i = 0; i < lastErrors.size(); i++) {
            parcel.lastErrorList[i] = lastErrors.get(i);
        }

        return parcel;
    }

    private Intent buildStateChangeIntent(final ArrayList<TetheringInterface> available,
            final ArrayList<TetheringInterface> localOnly,
            final ArrayList<TetheringInterface> tethered,
            final ArrayList<TetheringInterface> errored) {
        final Intent bcast = new Intent(ACTION_TETHER_STATE_CHANGED);
        bcast.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);

        bcast.putStringArrayListExtra(EXTRA_AVAILABLE_TETHER, toIfaces(available));
        bcast.putStringArrayListExtra(EXTRA_ACTIVE_LOCAL_ONLY, toIfaces(localOnly));
        bcast.putStringArrayListExtra(EXTRA_ACTIVE_TETHER, toIfaces(tethered));
        bcast.putStringArrayListExtra(EXTRA_ERRORED_TETHER, toIfaces(errored));

        return bcast;
    }

    private class StateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context content, Intent intent) {
            final String action = intent.getAction();
            if (action == null) return;

            if (action.equals(UsbManager.ACTION_USB_STATE)) {
                handleUsbAction(intent);
            } else if (action.equals(CONNECTIVITY_ACTION)) {
                handleConnectivityAction(intent);
            } else if (action.equals(WifiManager.WIFI_AP_STATE_CHANGED_ACTION)) {
                handleWifiApAction(intent);
            } else if (action.equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)) {
                handleWifiP2pAction(intent);
            } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                mLog.log("OBSERVED configuration changed");
                updateConfiguration();
            } else if (action.equals(UserManager.ACTION_USER_RESTRICTIONS_CHANGED)) {
                mLog.log("OBSERVED user restrictions changed");
                handleUserRestrictionAction();
            } else if (action.equals(ACTION_RESTRICT_BACKGROUND_CHANGED)) {
                mLog.log("OBSERVED data saver changed");
                handleDataSaverChanged();
            } else if (action.equals(TetheringNotificationUpdater.ACTION_DISABLE_TETHERING)) {
                untetherAll();
            }
        }

        private void handleConnectivityAction(Intent intent) {
            // CONNECTIVITY_ACTION is not handled since U+ device.
            if (SdkLevel.isAtLeastU()) return;

            final NetworkInfo networkInfo =
                    (NetworkInfo) intent.getParcelableExtra(EXTRA_NETWORK_INFO);
            if (networkInfo == null
                    || networkInfo.getDetailedState() == NetworkInfo.DetailedState.FAILED) {
                return;
            }

            if (VDBG) Log.d(TAG, "Tethering got CONNECTIVITY_ACTION: " + networkInfo.toString());
            mTetherMainSM.sendMessage(TetherMainSM.CMD_UPSTREAM_CHANGED);
        }

        private void handleUsbAction(Intent intent) {
            final boolean usbConnected = intent.getBooleanExtra(USB_CONNECTED, false);
            final boolean usbConfigured = intent.getBooleanExtra(USB_CONFIGURED, false);
            final boolean usbRndis = intent.getBooleanExtra(USB_FUNCTION_RNDIS, false);
            final boolean usbNcm = intent.getBooleanExtra(USB_FUNCTION_NCM, false);

            mLog.i(String.format("USB bcast connected:%s configured:%s rndis:%s ncm:%s",
                    usbConnected, usbConfigured, usbRndis, usbNcm));

            // There are three types of ACTION_USB_STATE:
            //
            //     - DISCONNECTED (USB_CONNECTED and USB_CONFIGURED are 0)
            //       Meaning: USB connection has ended either because of
            //       software reset or hard unplug.
            //
            //     - CONNECTED (USB_CONNECTED is 1, USB_CONFIGURED is 0)
            //       Meaning: the first stage of USB protocol handshake has
            //       occurred but it is not complete.
            //
            //     - CONFIGURED (USB_CONNECTED and USB_CONFIGURED are 1)
            //       Meaning: the USB handshake is completely done and all the
            //       functions are ready to use.
            //
            // For more explanation, see b/62552150 .
            boolean rndisEnabled = usbConfigured && usbRndis;
            boolean ncmEnabled = usbConfigured && usbNcm;
            if (!usbConnected) {
                // Don't stop provisioning if function is disabled but usb is still connected. The
                // function may be disable/enable to handle ip conflict condition (disabling the
                // function is necessary to ensure the connected device sees a disconnect).
                // Normally the provisioning should be stopped by stopTethering(int)
                maybeStopUsbProvisioning();
                rndisEnabled = false;
                ncmEnabled = false;
            }

            if (mRndisEnabled != rndisEnabled) {
                changeUsbIpServing(rndisEnabled, false /* forNcmFunction */);
                mRndisEnabled = rndisEnabled;
            }

            if (mNcmEnabled != ncmEnabled) {
                changeUsbIpServing(ncmEnabled, true /* forNcmFunction */);
                mNcmEnabled = ncmEnabled;
            }
        }

        private void changeUsbIpServing(boolean enable, boolean forNcmFunction) {
            if (enable) {
                // enable ip serving if function is enabled and usb is configured.
                enableUsbIpServing(forNcmFunction);
            } else {
                disableUsbIpServing(forNcmFunction);
            }
        }

        private void maybeStopUsbProvisioning() {
            for (int i = 0; i < mTetherStates.size(); i++) {
                final int type = mTetherStates.valueAt(i).ipServer.interfaceType();
                if (type == TETHERING_USB || type == TETHERING_NCM) {
                    mEntitlementMgr.stopProvisioningIfNeeded(type);
                }
            }
        }

        private void handleWifiApAction(Intent intent) {
            final int curState = intent.getIntExtra(EXTRA_WIFI_AP_STATE, WIFI_AP_STATE_DISABLED);
            final String ifname = intent.getStringExtra(EXTRA_WIFI_AP_INTERFACE_NAME);
            final int ipmode = intent.getIntExtra(EXTRA_WIFI_AP_MODE, IFACE_IP_MODE_UNSPECIFIED);

            switch (curState) {
                case WifiManager.WIFI_AP_STATE_ENABLING:
                    // We can see this state on the way to both enabled and failure states.
                    break;
                case WifiManager.WIFI_AP_STATE_ENABLED:
                    enableWifiIpServing(ifname, ipmode);
                    break;
                case WifiManager.WIFI_AP_STATE_DISABLING:
                    // We can see this state on the way to disabled.
                    break;
                case WifiManager.WIFI_AP_STATE_DISABLED:
                case WifiManager.WIFI_AP_STATE_FAILED:
                default:
                    disableWifiIpServing(ifname, curState);
                    break;
            }
        }

        private boolean isGroupOwner(WifiP2pGroup group) {
            return group != null && group.isGroupOwner()
                    && !TextUtils.isEmpty(group.getInterface());
        }

        private void handleWifiP2pAction(Intent intent) {
            if (mConfig.isWifiP2pLegacyTetheringMode()) return;

            final WifiP2pInfo p2pInfo =
                    (WifiP2pInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
            final WifiP2pGroup group =
                    (WifiP2pGroup) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);

            mLog.i("WifiP2pAction: P2pInfo: " + p2pInfo + " Group: " + group);

            // if no group is formed, bring it down if needed.
            if (p2pInfo == null || !p2pInfo.groupFormed) {
                disableWifiP2pIpServingIfNeeded(mWifiP2pTetherInterface);
                mWifiP2pTetherInterface = null;
                return;
            }

            // If there is a group but the device is not the owner, bail out.
            if (!isGroupOwner(group)) return;

            // If already serving from the correct interface, nothing to do.
            if (group.getInterface().equals(mWifiP2pTetherInterface)) return;

            // If already serving from another interface, turn it down first.
            if (!TextUtils.isEmpty(mWifiP2pTetherInterface)) {
                mLog.w("P2P tethered interface " + mWifiP2pTetherInterface
                        + "is different from current interface "
                        + group.getInterface() + ", re-tether it");
                disableWifiP2pIpServingIfNeeded(mWifiP2pTetherInterface);
            }

            // Finally bring up serving on the new interface
            mWifiP2pTetherInterface = group.getInterface();
            enableWifiP2pIpServing(mWifiP2pTetherInterface);
        }

        private void handleUserRestrictionAction() {
            if (mTetheringRestriction.onUserRestrictionsChanged()) {
                updateSupportedDownstreams(mConfig);
            }
        }

        private void handleDataSaverChanged() {
            final ConnectivityManager connMgr = (ConnectivityManager) mContext.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            final boolean isDataSaverEnabled = connMgr.getRestrictBackgroundStatus()
                    != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;

            if (mDataSaverEnabled == isDataSaverEnabled) return;

            mDataSaverEnabled = isDataSaverEnabled;
            if (mDataSaverEnabled) {
                untetherAll();
            }
        }
    }

    @VisibleForTesting
    SparseArray<TetheringRequestParcel> getActiveTetheringRequests() {
        return mActiveTetheringRequests;
    }

    @VisibleForTesting
    boolean isTetheringActive() {
        return getTetheredIfaces().length > 0;
    }

    // TODO: Refine TetheringTest then remove UserRestrictionActionListener class and handle
    // onUserRestrictionsChanged inside Tethering#handleUserRestrictionAction directly.
    @VisibleForTesting
    protected static class UserRestrictionActionListener {
        private final UserManager mUserMgr;
        private final Tethering mTethering;
        private final TetheringNotificationUpdater mNotificationUpdater;
        public boolean mDisallowTethering;

        public UserRestrictionActionListener(@NonNull UserManager um, @NonNull Tethering tethering,
                @NonNull TetheringNotificationUpdater updater) {
            mUserMgr = um;
            mTethering = tethering;
            mNotificationUpdater = updater;
            mDisallowTethering = false;
        }

        // return whether tethering disallowed is changed.
        public boolean onUserRestrictionsChanged() {
            // getUserRestrictions gets restriction for this process' user, which is the primary
            // user. This is fine because DISALLOW_CONFIG_TETHERING can only be set on the primary
            // user. See UserManager.DISALLOW_CONFIG_TETHERING.
            final Bundle restrictions = mUserMgr.getUserRestrictions();
            final boolean newlyDisallowed =
                    restrictions.getBoolean(UserManager.DISALLOW_CONFIG_TETHERING);
            final boolean prevDisallowed = mDisallowTethering;
            mDisallowTethering = newlyDisallowed;

            final boolean tetheringDisallowedChanged = (newlyDisallowed != prevDisallowed);
            if (!tetheringDisallowedChanged) return false;

            if (!newlyDisallowed) {
                // Clear the restricted notification when user is allowed to have tethering
                // function.
                mNotificationUpdater.tetheringRestrictionLifted();
                return true;
            }

            if (mTethering.isTetheringActive()) {
                // Restricted notification is shown when tethering function is disallowed on
                // user's device.
                mNotificationUpdater.notifyTetheringDisabledByRestriction();

                // Untether from all downstreams since tethering is disallowed.
                mTethering.untetherAll();
            }

            return true;
            // TODO(b/148139325): send tetheringSupported on restriction change
        }
    }

    private void enableIpServing(int tetheringType, String ifname, int ipServingMode) {
        enableIpServing(tetheringType, ifname, ipServingMode, false /* isNcm */);
    }

    private void enableIpServing(int tetheringType, String ifname, int ipServingMode,
            boolean isNcm) {
        ensureIpServerStarted(ifname, tetheringType, isNcm);
        if (tether(ifname, ipServingMode) != TETHER_ERROR_NO_ERROR) {
            Log.e(TAG, "unable start tethering on iface " + ifname);
        }
    }

    private void disableWifiIpServingCommon(int tetheringType, String ifname) {
        if (!TextUtils.isEmpty(ifname) && mTetherStates.containsKey(ifname)) {
            mTetherStates.get(ifname).ipServer.unwanted();
            return;
        }

        if (SdkLevel.isAtLeastT()) {
            mLog.e("Tethering no longer handle untracked interface after T: " + ifname);
            return;
        }

        // Attempt to guess the interface name before T. Pure AOSP code should never enter here
        // because WIFI_AP_STATE_CHANGED intent always include ifname and it should be tracked
        // by mTetherStates. In case OEMs have some modification in wifi side which pass null
        // or empty ifname. Before T, tethering allow to disable the first wifi ipServer if
        // given ifname don't match any tracking ipServer.
        for (int i = 0; i < mTetherStates.size(); i++) {
            final IpServer ipServer = mTetherStates.valueAt(i).ipServer;
            if (ipServer.interfaceType() == tetheringType) {
                ipServer.unwanted();
                return;
            }
        }
        mLog.log("Error disabling Wi-Fi IP serving; "
                + (TextUtils.isEmpty(ifname) ? "no interface name specified"
                                           : "specified interface: " + ifname));
    }

    private void disableWifiIpServing(String ifname, int apState) {
        // Regardless of whether we requested this transition, the AP has gone
        // down.  Don't try to tether again unless we're requested to do so.
        mWifiTetherRequested = false;

        mLog.log("Canceling WiFi tethering request - interface=" + ifname + " state=" + apState);

        disableWifiIpServingCommon(TETHERING_WIFI, ifname);
    }

    private void enableWifiP2pIpServing(String ifname) {
        if (TextUtils.isEmpty(ifname)) {
            mLog.e("Cannot enable P2P IP serving with invalid interface");
            return;
        }

        // After T, tethering always trust the iface pass by state change intent. This allow
        // tethering to deprecate tetherable p2p regexs after T.
        final int type = SdkLevel.isAtLeastT() ? TETHERING_WIFI_P2P : ifaceNameToType(ifname);
        if (!checkTetherableType(type)) {
            mLog.e(ifname + " is not a tetherable iface, ignoring");
            return;
        }
        enableIpServing(type, ifname, IpServer.STATE_LOCAL_ONLY);
    }

    private void disableWifiP2pIpServingIfNeeded(String ifname) {
        if (TextUtils.isEmpty(ifname)) return;

        mLog.log("Canceling P2P tethering request - interface=" + ifname);
        disableWifiIpServingCommon(TETHERING_WIFI_P2P, ifname);
    }

    private void enableWifiIpServing(String ifname, int wifiIpMode) {
        mLog.log("request WiFi tethering - interface=" + ifname + " state=" + wifiIpMode);

        // Map wifiIpMode values to IpServer.Callback serving states, inferring
        // from mWifiTetherRequested as a final "best guess".
        final int ipServingMode;
        switch (wifiIpMode) {
            case IFACE_IP_MODE_TETHERED:
                ipServingMode = IpServer.STATE_TETHERED;
                break;
            case IFACE_IP_MODE_LOCAL_ONLY:
                ipServingMode = IpServer.STATE_LOCAL_ONLY;
                break;
            default:
                mLog.e("Cannot enable IP serving in unknown WiFi mode: " + wifiIpMode);
                return;
        }

        // After T, tethering always trust the iface pass by state change intent. This allow
        // tethering to deprecate tetherable wifi regexs after T.
        final int type = SdkLevel.isAtLeastT() ? TETHERING_WIFI : ifaceNameToType(ifname);
        if (!checkTetherableType(type)) {
            mLog.e(ifname + " is not a tetherable iface, ignoring");
            return;
        }

        if (!TextUtils.isEmpty(ifname)) {
            enableIpServing(type, ifname, ipServingMode);
        } else {
            mLog.e("Cannot enable IP serving on missing interface name");
        }
    }

    // TODO: Pass TetheringRequest into this method. The code can look at the existing requests
    // to see which one matches the function that was enabled. That will tell the code what
    // tethering type was requested, without having to guess it from the configuration.
    // This method:
    //     - allows requesting either tethering or local hotspot serving states
    //     - only tethers the first matching interface in listInterfaces()
    //       order of a given type
    private void enableUsbIpServing(boolean forNcmFunction) {
        // Note: TetheringConfiguration#isUsingNcm can change between the call to
        // startTethering(TETHERING_USB) and the ACTION_USB_STATE broadcast. If the USB tethering
        // function changes from NCM to RNDIS, this can lead to Tethering starting NCM tethering
        // as local-only. But if this happens, the SettingsObserver will call stopTetheringInternal
        // for both TETHERING_USB and TETHERING_NCM, so the local-only NCM interface will be
        // stopped immediately.
        final int tetheringType = getServedUsbType(forNcmFunction);
        final int requestedState = getRequestedState(tetheringType);
        String[] ifaces = null;
        try {
            ifaces = mNetd.interfaceGetList();
        } catch (RemoteException | ServiceSpecificException e) {
            mLog.e("Cannot enableUsbIpServing due to error listing Interfaces" + e);
            return;
        }

        if (ifaces != null) {
            for (String iface : ifaces) {
                if (ifaceNameToType(iface) == tetheringType) {
                    enableIpServing(tetheringType, iface, requestedState, forNcmFunction);
                    return;
                }
            }
        }

        mLog.e("could not enable IpServer for function " + (forNcmFunction ? "NCM" : "RNDIS"));
    }

    private void disableUsbIpServing(boolean forNcmFunction) {
        for (int i = 0; i < mTetherStates.size(); i++) {
            final TetherState state = mTetherStates.valueAt(i);
            final int type = state.ipServer.interfaceType();
            if (type != TETHERING_USB && type != TETHERING_NCM) continue;

            if (state.isNcm == forNcmFunction) {
                ensureIpServerStopped(state.ipServer.interfaceName());
            }
        }
    }

    TetheringConfiguration getTetheringConfiguration() {
        return mConfig;
    }

    private boolean isEthernetSupported() {
        return mContext.getSystemService(Context.ETHERNET_SERVICE) != null;
    }

    void setUsbTethering(boolean enable, IIntResultListener listener) {
        mHandler.post(() -> {
            try {
                listener.onResult(setUsbTethering(enable));
            } catch (RemoteException e) { }
        });
    }

    private int setUsbTethering(boolean enable) {
        if (VDBG) Log.d(TAG, "setUsbTethering(" + enable + ")");
        UsbManager usbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            mLog.e("setUsbTethering: failed to get UsbManager!");
            return TETHER_ERROR_SERVICE_UNAVAIL;
        }

        final long usbFunction = mConfig.isUsingNcm()
                ? UsbManager.FUNCTION_NCM : UsbManager.FUNCTION_RNDIS;
        usbManager.setCurrentFunctions(enable ? usbFunction : UsbManager.FUNCTION_NONE);

        return TETHER_ERROR_NO_ERROR;
    }

    private int setNcmTethering(boolean enable) {
        if (VDBG) Log.d(TAG, "setNcmTethering(" + enable + ")");

        // If TETHERING_USB is forced to use ncm function, TETHERING_NCM would no longer be
        // available.
        if (mConfig.isUsingNcm() && enable) return TETHER_ERROR_SERVICE_UNAVAIL;

        UsbManager usbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        usbManager.setCurrentFunctions(enable ? UsbManager.FUNCTION_NCM : UsbManager.FUNCTION_NONE);
        return TETHER_ERROR_NO_ERROR;
    }

    // TODO review API - figure out how to delete these entirely.
    String[] getTetheredIfaces() {
        ArrayList<String> list = new ArrayList<String>();
        for (int i = 0; i < mTetherStates.size(); i++) {
            TetherState tetherState = mTetherStates.valueAt(i);
            if (tetherState.lastState == IpServer.STATE_TETHERED) {
                list.add(mTetherStates.keyAt(i));
            }
        }
        return list.toArray(new String[list.size()]);
    }

    String[] getTetherableIfacesForTest() {
        ArrayList<String> list = new ArrayList<String>();
        for (int i = 0; i < mTetherStates.size(); i++) {
            TetherState tetherState = mTetherStates.valueAt(i);
            if (tetherState.lastState == IpServer.STATE_AVAILABLE) {
                list.add(mTetherStates.keyAt(i));
            }
        }
        return list.toArray(new String[list.size()]);
    }

    private void logMessage(State state, int what) {
        mLog.log(state.getName() + " got " + sMagicDecoderRing.get(what, Integer.toString(what)));
    }

    private boolean upstreamWanted() {
        if (!mForwardedDownstreams.isEmpty()) return true;
        return mWifiTetherRequested;
    }

    // Needed because the canonical source of upstream truth is just the
    // upstream interface set, |mCurrentUpstreamIfaceSet|.
    private boolean pertainsToCurrentUpstream(UpstreamNetworkState ns) {
        if (ns != null && ns.linkProperties != null && mCurrentUpstreamIfaceSet != null) {
            for (String ifname : ns.linkProperties.getAllInterfaceNames()) {
                if (mCurrentUpstreamIfaceSet.ifnames.contains(ifname)) {
                    return true;
                }
            }
        }
        return false;
    }

    class TetherMainSM extends StateMachine {
        // an interface SM has requested Tethering/Local Hotspot
        static final int EVENT_IFACE_SERVING_STATE_ACTIVE       = BASE_MAIN_SM + 1;
        // an interface SM has unrequested Tethering/Local Hotspot
        static final int EVENT_IFACE_SERVING_STATE_INACTIVE     = BASE_MAIN_SM + 2;
        // upstream connection change - do the right thing
        static final int CMD_UPSTREAM_CHANGED                   = BASE_MAIN_SM + 3;
        // we don't have a valid upstream conn, check again after a delay
        static final int CMD_RETRY_UPSTREAM                     = BASE_MAIN_SM + 4;
        // Events from NetworkCallbacks that we process on the main state
        // machine thread on behalf of the UpstreamNetworkMonitor.
        static final int EVENT_UPSTREAM_CALLBACK                = BASE_MAIN_SM + 5;
        // we treated the error and want now to clear it
        static final int CMD_CLEAR_ERROR                        = BASE_MAIN_SM + 6;
        static final int EVENT_IFACE_UPDATE_LINKPROPERTIES      = BASE_MAIN_SM + 7;
        // Events from EntitlementManager to choose upstream again.
        static final int EVENT_UPSTREAM_PERMISSION_CHANGED      = BASE_MAIN_SM + 8;
        // Internal request from IpServer to enable or disable downstream.
        static final int EVENT_REQUEST_CHANGE_DOWNSTREAM        = BASE_MAIN_SM + 9;
        private final State mInitialState;
        private final State mTetherModeAliveState;

        private final State mSetIpForwardingEnabledErrorState;
        private final State mSetIpForwardingDisabledErrorState;
        private final State mStartTetheringErrorState;
        private final State mStopTetheringErrorState;
        private final State mSetDnsForwardersErrorState;

        // This list is a little subtle.  It contains all the interfaces that currently are
        // requesting tethering, regardless of whether these interfaces are still members of
        // mTetherStates.  This allows us to maintain the following predicates:
        //
        // 1) mTetherStates contains the set of all currently existing, tetherable, link state up
        //    interfaces.
        // 2) mNotifyList contains all state machines that may have outstanding tethering state
        //    that needs to be torn down.
        // 3) Use mNotifyList for predictable ordering order for ConnectedClientsTracker.
        //
        // Because we excise interfaces immediately from mTetherStates, we must maintain mNotifyList
        // so that the garbage collector does not clean up the state machine before it has a chance
        // to tear itself down.
        private final ArrayList<IpServer> mNotifyList;
        private final IPv6TetheringCoordinator mIPv6TetheringCoordinator;
        private final OffloadWrapper mOffload;

        private static final int UPSTREAM_SETTLE_TIME_MS     = 10000;

        TetherMainSM(String name, Looper looper, TetheringDependencies deps) {
            super(name, looper);

            mInitialState = new InitialState();
            mTetherModeAliveState = new TetherModeAliveState();
            mSetIpForwardingEnabledErrorState = new SetIpForwardingEnabledErrorState();
            mSetIpForwardingDisabledErrorState = new SetIpForwardingDisabledErrorState();
            mStartTetheringErrorState = new StartTetheringErrorState();
            mStopTetheringErrorState = new StopTetheringErrorState();
            mSetDnsForwardersErrorState = new SetDnsForwardersErrorState();

            addState(mInitialState);
            addState(mTetherModeAliveState);
            addState(mSetIpForwardingEnabledErrorState);
            addState(mSetIpForwardingDisabledErrorState);
            addState(mStartTetheringErrorState);
            addState(mStopTetheringErrorState);
            addState(mSetDnsForwardersErrorState);

            mNotifyList = new ArrayList<>();
            mIPv6TetheringCoordinator = deps.makeIPv6TetheringCoordinator(mNotifyList, mLog);
            mOffload = new OffloadWrapper();

            setInitialState(mInitialState);
        }

        /**
         * Returns all downstreams that are serving clients, regardless of they are actually
         * tethered or localOnly. This must be called on the tethering thread (not thread-safe).
         */
        @NonNull
        public List<IpServer> getAllDownstreams() {
            return mNotifyList;
        }

        class InitialState extends State {
            @Override
            public boolean processMessage(Message message) {
                logMessage(this, message.what);
                switch (message.what) {
                    case EVENT_IFACE_SERVING_STATE_ACTIVE: {
                        final IpServer who = (IpServer) message.obj;
                        if (VDBG) Log.d(TAG, "Tether Mode requested by " + who);
                        handleInterfaceServingStateActive(message.arg1, who);
                        transitionTo(mTetherModeAliveState);
                        break;
                    }
                    case EVENT_IFACE_SERVING_STATE_INACTIVE: {
                        final IpServer who = (IpServer) message.obj;
                        if (VDBG) Log.d(TAG, "Tether Mode unrequested by " + who);
                        handleInterfaceServingStateInactive(who);
                        break;
                    }
                    case EVENT_IFACE_UPDATE_LINKPROPERTIES:
                        // Silently ignore these for now.
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }
        }

        protected boolean turnOnMainTetherSettings() {
            final TetheringConfiguration cfg = mConfig;
            try {
                mNetd.ipfwdEnableForwarding(TAG);
            } catch (RemoteException | ServiceSpecificException e) {
                mLog.e(e);
                transitionTo(mSetIpForwardingEnabledErrorState);
                return false;
            }

            // TODO: Randomize DHCPv4 ranges, especially in hotspot mode.
            // Legacy DHCP server is disabled if passed an empty ranges array
            final String[] dhcpRanges = cfg.useLegacyDhcpServer()
                    ? cfg.legacyDhcpRanges : new String[0];
            try {
                NetdUtils.tetherStart(mNetd, true /** usingLegacyDnsProxy */, dhcpRanges);
            } catch (RemoteException | ServiceSpecificException e) {
                try {
                    // Stop and retry.
                    mNetd.tetherStop();
                    NetdUtils.tetherStart(mNetd, true /** usingLegacyDnsProxy */, dhcpRanges);
                } catch (RemoteException | ServiceSpecificException ee) {
                    mLog.e(ee);
                    transitionTo(mStartTetheringErrorState);
                    return false;
                }
            }
            mLog.log("SET main tether settings: ON");
            return true;
        }

        protected boolean turnOffMainTetherSettings() {
            try {
                mNetd.tetherStop();
            } catch (RemoteException | ServiceSpecificException e) {
                mLog.e(e);
                transitionTo(mStopTetheringErrorState);
                return false;
            }
            try {
                mNetd.ipfwdDisableForwarding(TAG);
            } catch (RemoteException | ServiceSpecificException e) {
                mLog.e(e);
                transitionTo(mSetIpForwardingDisabledErrorState);
                return false;
            }
            transitionTo(mInitialState);
            mLog.log("SET main tether settings: OFF");
            return true;
        }

        protected void chooseUpstreamType(boolean tryCell) {
            // We rebuild configuration on ACTION_CONFIGURATION_CHANGED, but we
            // do not currently know how to watch for changes in DUN settings.
            maybeDunSettingChanged();

            final TetheringConfiguration config = mConfig;
            final UpstreamNetworkState ns = (config.chooseUpstreamAutomatically)
                    ? mUpstreamNetworkMonitor.getCurrentPreferredUpstream()
                    : mUpstreamNetworkMonitor.selectPreferredUpstreamType(
                            config.preferredUpstreamIfaceTypes);

            if (ns == null) {
                if (tryCell) {
                    mUpstreamNetworkMonitor.setTryCell(true);
                    // We think mobile should be coming up; don't set a retry.
                } else {
                    sendMessageDelayed(CMD_RETRY_UPSTREAM, UPSTREAM_SETTLE_TIME_MS);
                }
            } else if (!isCellular(ns)) {
                mUpstreamNetworkMonitor.setTryCell(false);
            }

            setUpstreamNetwork(ns);
            final Network newUpstream = (ns != null) ? ns.network : null;
            if (!Objects.equals(mTetherUpstream, newUpstream)) {
                mTetherUpstream = newUpstream;
                reportUpstreamChanged(mTetherUpstream);
                // Need to notify capabilities change after upstream network changed because
                // upstream may switch to existing network which don't have
                // UpstreamNetworkMonitor.EVENT_ON_CAPABILITIES callback.
                mNotificationUpdater.onUpstreamCapabilitiesChanged(
                        (ns != null) ? ns.networkCapabilities : null);
            }
            mTetheringMetrics.maybeUpdateUpstreamType(ns);
        }

        protected void setUpstreamNetwork(UpstreamNetworkState ns) {
            InterfaceSet ifaces = null;
            if (ns != null) {
                // Find the interface with the default IPv4 route. It may be the
                // interface described by linkProperties, or one of the interfaces
                // stacked on top of it.
                mLog.i("Looking for default routes on: " + ns.linkProperties);
                ifaces = TetheringInterfaceUtils.getTetheringInterfaces(ns);
                mLog.i("Found upstream interface(s): " + ifaces);
            }

            if (ifaces != null) {
                setDnsForwarders(ns.network, ns.linkProperties);
            }
            notifyDownstreamsOfNewUpstreamIface(ifaces);
            if (ns != null && pertainsToCurrentUpstream(ns)) {
                // If we already have UpstreamNetworkState for this network update it immediately.
                handleNewUpstreamNetworkState(ns);
            } else if (mCurrentUpstreamIfaceSet == null) {
                // There are no available upstream networks.
                handleNewUpstreamNetworkState(null);
            }
        }

        protected void setDnsForwarders(final Network network, final LinkProperties lp) {
            // TODO: Set v4 and/or v6 DNS per available connectivity.
            final Collection<InetAddress> dnses = lp.getDnsServers();
            // TODO: Properly support the absence of DNS servers.
            final String[] dnsServers;
            if (dnses != null && !dnses.isEmpty()) {
                dnsServers = new String[dnses.size()];
                int i = 0;
                for (InetAddress dns : dnses) {
                    dnsServers[i++] = dns.getHostAddress();
                }
            } else {
                dnsServers = mConfig.defaultIPv4DNS;
            }
            final int netId = (network != null) ? network.getNetId() : NETID_UNSET;
            try {
                mNetd.tetherDnsSet(netId, dnsServers);
                mLog.log(String.format(
                        "SET DNS forwarders: network=%s dnsServers=%s",
                        network, Arrays.toString(dnsServers)));
            } catch (RemoteException | ServiceSpecificException e) {
                // TODO: Investigate how this can fail and what exactly
                // happens if/when such failures occur.
                mLog.e("setting DNS forwarders failed, " + e);
                transitionTo(mSetDnsForwardersErrorState);
            }
        }

        protected void notifyDownstreamsOfNewUpstreamIface(InterfaceSet ifaces) {
            mCurrentUpstreamIfaceSet = ifaces;
            for (IpServer ipServer : mNotifyList) {
                ipServer.sendMessage(IpServer.CMD_TETHER_CONNECTION_CHANGED, ifaces);
            }
        }

        protected void handleNewUpstreamNetworkState(UpstreamNetworkState ns) {
            mIPv6TetheringCoordinator.updateUpstreamNetworkState(ns);
            mOffload.updateUpstreamNetworkState(ns);
            mBpfCoordinator.updateUpstreamNetworkState(ns);
        }

        private void handleInterfaceServingStateActive(int mode, IpServer who) {
            if (mNotifyList.indexOf(who) < 0) {
                mNotifyList.add(who);
                mIPv6TetheringCoordinator.addActiveDownstream(who, mode);
            }

            if (mode == IpServer.STATE_TETHERED) {
                // No need to notify OffloadController just yet as there are no
                // "offload-able" prefixes to pass along. This will handled
                // when the TISM informs Tethering of its LinkProperties.
                mForwardedDownstreams.add(who);
            } else {
                mOffload.excludeDownstreamInterface(who.interfaceName());
                mForwardedDownstreams.remove(who);
            }

            // If this is a Wi-Fi interface, notify WifiManager of the active serving state.
            if (who.interfaceType() == TETHERING_WIFI) {
                final WifiManager mgr = getWifiManager();
                final String iface = who.interfaceName();
                switch (mode) {
                    case IpServer.STATE_TETHERED:
                        mgr.updateInterfaceIpState(iface, IFACE_IP_MODE_TETHERED);
                        break;
                    case IpServer.STATE_LOCAL_ONLY:
                        mgr.updateInterfaceIpState(iface, IFACE_IP_MODE_LOCAL_ONLY);
                        break;
                    default:
                        Log.wtf(TAG, "Unknown active serving mode: " + mode);
                        break;
                }
            }
        }

        private void handleInterfaceServingStateInactive(IpServer who) {
            mNotifyList.remove(who);
            mIPv6TetheringCoordinator.removeActiveDownstream(who);
            mOffload.excludeDownstreamInterface(who.interfaceName());
            mForwardedDownstreams.remove(who);
            maybeDhcpLeasesChanged();

            // If this is a Wi-Fi interface, tell WifiManager of any errors
            // or the inactive serving state.
            if (who.interfaceType() == TETHERING_WIFI) {
                final WifiManager mgr = getWifiManager();
                final String iface = who.interfaceName();
                if (mgr == null) {
                    Log.wtf(TAG, "Skipping WifiManager notification about inactive tethering");
                } else if (who.lastError() != TETHER_ERROR_NO_ERROR) {
                    mgr.updateInterfaceIpState(iface, IFACE_IP_MODE_CONFIGURATION_ERROR);
                } else {
                    mgr.updateInterfaceIpState(iface, IFACE_IP_MODE_UNSPECIFIED);
                }
            }
        }

        @VisibleForTesting
        void handleUpstreamNetworkMonitorCallback(int arg1, Object o) {
            if (arg1 == UpstreamNetworkMonitor.NOTIFY_LOCAL_PREFIXES) {
                mOffload.sendOffloadExemptPrefixes((Set<IpPrefix>) o);
                return;
            }

            final UpstreamNetworkState ns = (UpstreamNetworkState) o;
            switch (arg1) {
                case UpstreamNetworkMonitor.EVENT_ON_LINKPROPERTIES:
                    mPrivateAddressCoordinator.updateUpstreamPrefix(ns);
                    break;
                case UpstreamNetworkMonitor.EVENT_ON_LOST:
                    mPrivateAddressCoordinator.removeUpstreamPrefix(ns.network);
                    break;
            }

            if (mConfig.chooseUpstreamAutomatically
                    && arg1 == UpstreamNetworkMonitor.EVENT_DEFAULT_SWITCHED) {
                chooseUpstreamType(true);
                return;
            }

            if (ns == null || !pertainsToCurrentUpstream(ns)) {
                // TODO: In future, this is where upstream evaluation and selection
                // could be handled for notifications which include sufficient data.
                // For example, after CONNECTIVITY_ACTION listening is removed, here
                // is where we could observe a Wi-Fi network becoming available and
                // passing validation.
                if (mCurrentUpstreamIfaceSet == null) {
                    // If we have no upstream interface, try to run through upstream
                    // selection again.  If, for example, IPv4 connectivity has shown up
                    // after IPv6 (e.g., 464xlat became available) we want the chance to
                    // notice and act accordingly.
                    chooseUpstreamType(false);
                }
                return;
            }

            switch (arg1) {
                case UpstreamNetworkMonitor.EVENT_ON_CAPABILITIES:
                    if (ns.network.equals(mTetherUpstream)) {
                        mNotificationUpdater.onUpstreamCapabilitiesChanged(ns.networkCapabilities);
                    }
                    handleNewUpstreamNetworkState(ns);
                    break;
                case UpstreamNetworkMonitor.EVENT_ON_LINKPROPERTIES:
                    chooseUpstreamType(false);
                    break;
                case UpstreamNetworkMonitor.EVENT_ON_LOST:
                    // TODO: Re-evaluate possible upstreams. Currently upstream
                    // reevaluation is triggered via received CONNECTIVITY_ACTION
                    // broadcasts that result in being passed a
                    // TetherMainSM.CMD_UPSTREAM_CHANGED.
                    handleNewUpstreamNetworkState(null);

                    if (SdkLevel.isAtLeastU()) {
                        // Need to try DUN immediately if Wi-Fi goes down.
                        chooseUpstreamType(true);
                    }
                    break;
                default:
                    mLog.e("Unknown arg1 value: " + arg1);
                    break;
            }
        }

        class TetherModeAliveState extends State {
            boolean mUpstreamWanted = false;
            boolean mTryCell = true;

            @Override
            public void enter() {
                // If turning on main tether settings fails, we have already
                // transitioned to an error state; exit early.
                if (!turnOnMainTetherSettings()) {
                    return;
                }

                mPrivateAddressCoordinator.maybeRemoveDeprecatedUpstreams();
                mUpstreamNetworkMonitor.startObserveAllNetworks();

                // TODO: De-duplicate with updateUpstreamWanted() below.
                if (upstreamWanted()) {
                    mUpstreamWanted = true;
                    mOffload.start();
                    chooseUpstreamType(true);
                    mTryCell = false;
                }

                // TODO: Check the upstream interface if it is managed by BPF offload.
                mBpfCoordinator.startPolling();
            }

            @Override
            public void exit() {
                mOffload.stop();
                mUpstreamNetworkMonitor.stop();
                notifyDownstreamsOfNewUpstreamIface(null);
                handleNewUpstreamNetworkState(null);
                if (mTetherUpstream != null) {
                    mTetherUpstream = null;
                    reportUpstreamChanged(null);
                    mNotificationUpdater.onUpstreamCapabilitiesChanged(null);
                }
                mBpfCoordinator.stopPolling();
                mTetheringMetrics.cleanup();
            }

            private boolean updateUpstreamWanted() {
                final boolean previousUpstreamWanted = mUpstreamWanted;
                mUpstreamWanted = upstreamWanted();
                if (mUpstreamWanted != previousUpstreamWanted) {
                    if (mUpstreamWanted) {
                        mOffload.start();
                    } else {
                        mOffload.stop();
                    }
                }
                return previousUpstreamWanted;
            }

            @Override
            public boolean processMessage(Message message) {
                logMessage(this, message.what);
                boolean retValue = true;
                switch (message.what) {
                    case EVENT_IFACE_SERVING_STATE_ACTIVE: {
                        IpServer who = (IpServer) message.obj;
                        if (VDBG) Log.d(TAG, "Tether Mode requested by " + who);
                        handleInterfaceServingStateActive(message.arg1, who);
                        who.sendMessage(IpServer.CMD_TETHER_CONNECTION_CHANGED,
                                mCurrentUpstreamIfaceSet);
                        // If there has been a change and an upstream is now
                        // desired, kick off the selection process.
                        final boolean previousUpstreamWanted = updateUpstreamWanted();
                        if (!previousUpstreamWanted && mUpstreamWanted) {
                            chooseUpstreamType(true);
                        }
                        break;
                    }
                    case EVENT_IFACE_SERVING_STATE_INACTIVE: {
                        IpServer who = (IpServer) message.obj;
                        if (VDBG) Log.d(TAG, "Tether Mode unrequested by " + who);
                        handleInterfaceServingStateInactive(who);

                        if (mNotifyList.isEmpty()) {
                            // This transitions us out of TetherModeAliveState,
                            // either to InitialState or an error state.
                            turnOffMainTetherSettings();
                            break;
                        }

                        if (DBG) {
                            Log.d(TAG, "TetherModeAlive still has " + mNotifyList.size()
                                    + " live requests:");
                            for (IpServer o : mNotifyList) {
                                Log.d(TAG, "  " + o);
                            }
                        }
                        // If there has been a change and an upstream is no
                        // longer desired, release any mobile requests.
                        final boolean previousUpstreamWanted = updateUpstreamWanted();
                        if (previousUpstreamWanted && !mUpstreamWanted) {
                            mUpstreamNetworkMonitor.setTryCell(false);
                        }
                        break;
                    }
                    case EVENT_IFACE_UPDATE_LINKPROPERTIES: {
                        final LinkProperties newLp = (LinkProperties) message.obj;
                        if (message.arg1 == IpServer.STATE_TETHERED) {
                            mOffload.updateDownstreamLinkProperties(newLp);
                        } else {
                            mOffload.excludeDownstreamInterface(newLp.getInterfaceName());
                        }
                        break;
                    }
                    case EVENT_UPSTREAM_PERMISSION_CHANGED:
                    case CMD_UPSTREAM_CHANGED:
                        updateUpstreamWanted();
                        if (!mUpstreamWanted) break;

                        // Need to try DUN immediately if Wi-Fi goes down.
                        chooseUpstreamType(true);
                        mTryCell = false;
                        break;
                    case CMD_RETRY_UPSTREAM:
                        updateUpstreamWanted();
                        if (!mUpstreamWanted) break;

                        chooseUpstreamType(mTryCell);
                        mTryCell = !mTryCell;
                        break;
                    case EVENT_UPSTREAM_CALLBACK: {
                        updateUpstreamWanted();
                        if (mUpstreamWanted) {
                            handleUpstreamNetworkMonitorCallback(message.arg1, message.obj);
                        }
                        break;
                    }
                    case EVENT_REQUEST_CHANGE_DOWNSTREAM: {
                        final int tetheringType = message.arg1;
                        final Boolean enabled = (Boolean) message.obj;
                        enableTetheringInternal(tetheringType, enabled, null);
                        break;
                    }
                    default:
                        retValue = false;
                        break;
                }
                return retValue;
            }
        }

        class ErrorState extends State {
            private int mErrorNotification;

            @Override
            public boolean processMessage(Message message) {
                boolean retValue = true;
                switch (message.what) {
                    case EVENT_IFACE_SERVING_STATE_ACTIVE:
                        IpServer who = (IpServer) message.obj;
                        who.sendMessage(mErrorNotification);
                        break;
                    case CMD_CLEAR_ERROR:
                        mErrorNotification = TETHER_ERROR_NO_ERROR;
                        transitionTo(mInitialState);
                        break;
                    default:
                        retValue = false;
                }
                return retValue;
            }

            void notify(int msgType) {
                mErrorNotification = msgType;
                for (IpServer ipServer : mNotifyList) {
                    ipServer.sendMessage(msgType);
                }
            }

        }

        class SetIpForwardingEnabledErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "Error in setIpForwardingEnabled");
                notify(IpServer.CMD_IP_FORWARDING_ENABLE_ERROR);
            }
        }

        class SetIpForwardingDisabledErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "Error in setIpForwardingDisabled");
                notify(IpServer.CMD_IP_FORWARDING_DISABLE_ERROR);
            }
        }

        class StartTetheringErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "Error in startTethering");
                notify(IpServer.CMD_START_TETHERING_ERROR);
                try {
                    mNetd.ipfwdDisableForwarding(TAG);
                } catch (RemoteException | ServiceSpecificException e) { }
            }
        }

        class StopTetheringErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "Error in stopTethering");
                notify(IpServer.CMD_STOP_TETHERING_ERROR);
                try {
                    mNetd.ipfwdDisableForwarding(TAG);
                } catch (RemoteException | ServiceSpecificException e) { }
            }
        }

        class SetDnsForwardersErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "Error in setDnsForwarders");
                notify(IpServer.CMD_SET_DNS_FORWARDERS_ERROR);
                try {
                    mNetd.tetherStop();
                } catch (RemoteException | ServiceSpecificException e) { }
                try {
                    mNetd.ipfwdDisableForwarding(TAG);
                } catch (RemoteException | ServiceSpecificException e) { }
            }
        }

        // A wrapper class to handle multiple situations where several calls to
        // the OffloadController need to happen together.
        //
        // TODO: This suggests that the interface between OffloadController and
        // Tethering is in need of improvement. Refactor these calls into the
        // OffloadController implementation.
        class OffloadWrapper {
            public void start() {
                final int status = mOffloadController.start() ? TETHER_HARDWARE_OFFLOAD_STARTED
                        : TETHER_HARDWARE_OFFLOAD_FAILED;
                updateOffloadStatus(status);
                sendOffloadExemptPrefixes();
            }

            public void stop() {
                mOffloadController.stop();
                updateOffloadStatus(TETHER_HARDWARE_OFFLOAD_STOPPED);
            }

            public void updateUpstreamNetworkState(UpstreamNetworkState ns) {
                // Disable hw offload on vpn upstream interfaces.
                // setUpstreamLinkProperties() interprets null as disable.
                if (ns != null && ns.networkCapabilities != null
                        && !ns.networkCapabilities.hasCapability(NET_CAPABILITY_NOT_VPN)) {
                    ns = null;
                }
                mOffloadController.setUpstreamLinkProperties(
                        (ns != null) ? ns.linkProperties : null);
            }

            public void updateDownstreamLinkProperties(LinkProperties newLp) {
                // Update the list of offload-exempt prefixes before adding
                // new prefixes on downstream interfaces to the offload HAL.
                sendOffloadExemptPrefixes();
                mOffloadController.notifyDownstreamLinkProperties(newLp);
            }

            public void excludeDownstreamInterface(String ifname) {
                // This and other interfaces may be in local-only hotspot mode;
                // resend all local prefixes to the OffloadController.
                sendOffloadExemptPrefixes();
                mOffloadController.removeDownstreamInterface(ifname);
            }

            public void sendOffloadExemptPrefixes() {
                sendOffloadExemptPrefixes(mUpstreamNetworkMonitor.getLocalPrefixes());
            }

            public void sendOffloadExemptPrefixes(final Set<IpPrefix> localPrefixes) {
                // Add in well-known minimum set.
                PrefixUtils.addNonForwardablePrefixes(localPrefixes);
                // Add tragically hardcoded prefixes.
                localPrefixes.add(PrefixUtils.DEFAULT_WIFI_P2P_PREFIX);

                // Maybe add prefixes or addresses for downstreams, depending on
                // the IP serving mode of each.
                for (IpServer ipServer : mNotifyList) {
                    final LinkProperties lp = ipServer.linkProperties();

                    switch (ipServer.servingMode()) {
                        case IpServer.STATE_UNAVAILABLE:
                        case IpServer.STATE_AVAILABLE:
                            // No usable LinkProperties in these states.
                            continue;
                        case IpServer.STATE_TETHERED:
                            // Only add IPv4 /32 and IPv6 /128 prefixes. The
                            // directly-connected prefixes will be sent as
                            // downstream "offload-able" prefixes.
                            for (LinkAddress addr : lp.getAllLinkAddresses()) {
                                final InetAddress ip = addr.getAddress();
                                if (ip.isLinkLocalAddress()) continue;
                                localPrefixes.add(PrefixUtils.ipAddressAsPrefix(ip));
                            }
                            break;
                        case IpServer.STATE_LOCAL_ONLY:
                            // Add prefixes covering all local IPs.
                            localPrefixes.addAll(PrefixUtils.localPrefixesFrom(lp));
                            break;
                    }
                }

                mOffloadController.setLocalPrefixes(localPrefixes);
            }

            private void updateOffloadStatus(final int newStatus) {
                if (newStatus == mOffloadStatus) return;

                mOffloadStatus = newStatus;
                reportOffloadStatusChanged(mOffloadStatus);
            }
        }
    }

    private void startTrackDefaultNetwork() {
        mUpstreamNetworkMonitor.startTrackDefaultNetwork(mEntitlementMgr);
    }

    /** Get the latest value of the tethering entitlement check. */
    void requestLatestTetheringEntitlementResult(int type, ResultReceiver receiver,
            boolean showEntitlementUi) {
        if (receiver == null) return;

        mHandler.post(() -> {
            mEntitlementMgr.requestLatestTetheringEntitlementResult(type, receiver,
                    showEntitlementUi);
        });
    }

    /** Register tethering event callback */
    void registerTetheringEventCallback(ITetheringEventCallback callback) {
        final boolean hasListPermission =
                hasCallingPermission(NETWORK_SETTINGS)
                        || hasCallingPermission(PERMISSION_MAINLINE_NETWORK_STACK)
                        || hasCallingPermission(NETWORK_STACK);
        mHandler.post(() -> {
            mTetheringEventCallbacks.register(callback, new CallbackCookie(hasListPermission));
            final TetheringCallbackStartedParcel parcel = new TetheringCallbackStartedParcel();
            parcel.supportedTypes = mSupportedTypeBitmap;
            parcel.upstreamNetwork = mTetherUpstream;
            parcel.config = mConfig.toStableParcelable();
            parcel.states =
                    mTetherStatesParcel != null ? mTetherStatesParcel : emptyTetherStatesParcel();
            parcel.tetheredClients = hasListPermission
                    ? mConnectedClientsTracker.getLastTetheredClients()
                    : Collections.emptyList();
            parcel.offloadStatus = mOffloadStatus;
            try {
                callback.onCallbackStarted(parcel);
            } catch (RemoteException e) {
                // Not really very much to do here.
            }
        });
    }

    private TetherStatesParcel emptyTetherStatesParcel() {
        final TetherStatesParcel parcel = new TetherStatesParcel();
        parcel.availableList = new TetheringInterface[0];
        parcel.tetheredList = new TetheringInterface[0];
        parcel.localOnlyList = new TetheringInterface[0];
        parcel.erroredIfaceList = new TetheringInterface[0];
        parcel.lastErrorList = new int[0];

        return parcel;
    }

    private boolean hasCallingPermission(@NonNull String permission) {
        return mContext.checkCallingOrSelfPermission(permission) == PERMISSION_GRANTED;
    }

    /** Unregister tethering event callback */
    void unregisterTetheringEventCallback(ITetheringEventCallback callback) {
        if (callback == null) {
            throw new NullPointerException();
        }
        mHandler.post(() -> {
            mTetheringEventCallbacks.unregister(callback);
        });
    }

    private void reportTetheringSupportedChange(final long supportedBitmap) {
        final int length = mTetheringEventCallbacks.beginBroadcast();
        try {
            for (int i = 0; i < length; i++) {
                try {
                    mTetheringEventCallbacks.getBroadcastItem(i).onSupportedTetheringTypes(
                            supportedBitmap);
                } catch (RemoteException e) {
                    // Not really very much to do here.
                }
            }
        } finally {
            mTetheringEventCallbacks.finishBroadcast();
        }
    }

    private void reportUpstreamChanged(final Network network) {
        final int length = mTetheringEventCallbacks.beginBroadcast();
        try {
            for (int i = 0; i < length; i++) {
                try {
                    mTetheringEventCallbacks.getBroadcastItem(i).onUpstreamChanged(network);
                } catch (RemoteException e) {
                    // Not really very much to do here.
                }
            }
        } finally {
            mTetheringEventCallbacks.finishBroadcast();
        }
    }

    private void reportConfigurationChanged(TetheringConfigurationParcel config) {
        final int length = mTetheringEventCallbacks.beginBroadcast();
        try {
            for (int i = 0; i < length; i++) {
                try {
                    mTetheringEventCallbacks.getBroadcastItem(i).onConfigurationChanged(config);
                    // TODO(b/148139325): send tetheringSupported on configuration change
                } catch (RemoteException e) {
                    // Not really very much to do here.
                }
            }
        } finally {
            mTetheringEventCallbacks.finishBroadcast();
        }
    }

    private void reportTetherStateChanged(TetherStatesParcel states) {
        final int length = mTetheringEventCallbacks.beginBroadcast();
        try {
            for (int i = 0; i < length; i++) {
                try {
                    mTetheringEventCallbacks.getBroadcastItem(i).onTetherStatesChanged(states);
                } catch (RemoteException e) {
                    // Not really very much to do here.
                }
            }
        } finally {
            mTetheringEventCallbacks.finishBroadcast();
        }
    }

    private void reportTetherClientsChanged(List<TetheredClient> clients) {
        final int length = mTetheringEventCallbacks.beginBroadcast();
        try {
            for (int i = 0; i < length; i++) {
                try {
                    final CallbackCookie cookie =
                            (CallbackCookie) mTetheringEventCallbacks.getBroadcastCookie(i);
                    if (!cookie.hasListClientsPermission) continue;
                    mTetheringEventCallbacks.getBroadcastItem(i).onTetherClientsChanged(clients);
                } catch (RemoteException e) {
                    // Not really very much to do here.
                }
            }
        } finally {
            mTetheringEventCallbacks.finishBroadcast();
        }
    }

    private void reportOffloadStatusChanged(final int status) {
        final int length = mTetheringEventCallbacks.beginBroadcast();
        try {
            for (int i = 0; i < length; i++) {
                try {
                    mTetheringEventCallbacks.getBroadcastItem(i).onOffloadStatusChanged(status);
                } catch (RemoteException e) {
                    // Not really very much to do here.
                }
            }
        } finally {
            mTetheringEventCallbacks.finishBroadcast();
        }
    }

    private void updateSupportedDownstreams(final TetheringConfiguration config) {
        final long preSupportedBitmap = mSupportedTypeBitmap;

        if (!isTetheringAllowed() || mEntitlementMgr.isProvisioningNeededButUnavailable()) {
            mSupportedTypeBitmap = 0;
        } else {
            mSupportedTypeBitmap = makeSupportedDownstreams(config);
        }

        if (preSupportedBitmap != mSupportedTypeBitmap) {
            reportTetheringSupportedChange(mSupportedTypeBitmap);
        }
    }

    private long makeSupportedDownstreams(final TetheringConfiguration config) {
        long types = 0;
        if (config.tetherableUsbRegexs.length != 0) types |= (1 << TETHERING_USB);

        if (config.tetherableWifiRegexs.length != 0) types |= (1 << TETHERING_WIFI);

        if (config.tetherableBluetoothRegexs.length != 0) types |= (1 << TETHERING_BLUETOOTH);

        // Before T, isTetheringSupported would return true if wifi, usb and bluetooth tethering are
        // disabled (whole tethering settings would be hidden). This means tethering would also not
        // support wifi p2p, ethernet tethering and mirrorlink. This is wrong but probably there are
        // some devices in the field rely on this to disable tethering entirely.
        if (!SdkLevel.isAtLeastT() && types == 0) return types;

        if (config.tetherableNcmRegexs.length != 0) types |= (1 << TETHERING_NCM);

        if (config.tetherableWifiP2pRegexs.length != 0) types |= (1 << TETHERING_WIFI_P2P);

        if (isEthernetSupported()) types |= (1 << TETHERING_ETHERNET);

        return types;
    }

    // if ro.tether.denied = true we default to no tethering
    // gservices could set the secure setting to 1 though to enable it on a build where it
    // had previously been turned off.
    boolean isTetheringAllowed() {
        final int defaultVal = mDeps.isTetheringDenied() ? 0 : 1;
        final boolean tetherSupported = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.TETHER_SUPPORTED, defaultVal) != 0;
        return tetherSupported
                && !mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING);
    }

    boolean isTetheringSupported() {
        return mSupportedTypeBitmap > 0;
    }

    private void dumpBpf(IndentingPrintWriter pw) {
        pw.println("BPF offload:");
        pw.increaseIndent();
        mBpfCoordinator.dump(pw);
        pw.decreaseIndent();
    }

    void doDump(@NonNull FileDescriptor fd, @NonNull PrintWriter writer, @Nullable String[] args) {
        // Binder.java closes the resource for us.
        @SuppressWarnings("resource") final IndentingPrintWriter pw = new IndentingPrintWriter(
                writer, "  ");

        // Used for testing instead of human debug.
        if (CollectionUtils.contains(args, "bpfRawMap")) {
            mBpfCoordinator.dumpRawMap(pw, args);
            return;
        }

        if (CollectionUtils.contains(args, "bpf")) {
            dumpBpf(pw);
            return;
        }

        pw.println("Tethering:");
        pw.increaseIndent();

        pw.println("Callbacks registered: "
                + mTetheringEventCallbacks.getRegisteredCallbackCount());

        pw.println("Configuration:");
        pw.increaseIndent();
        final TetheringConfiguration cfg = mConfig;
        cfg.dump(pw);
        pw.decreaseIndent();

        pw.println("Entitlement:");
        pw.increaseIndent();
        mEntitlementMgr.dump(pw);
        pw.decreaseIndent();

        pw.println("Tether state:");
        pw.increaseIndent();
        for (int i = 0; i < mTetherStates.size(); i++) {
            final String iface = mTetherStates.keyAt(i);
            final TetherState tetherState = mTetherStates.valueAt(i);
            pw.print(iface + " - ");

            switch (tetherState.lastState) {
                case IpServer.STATE_UNAVAILABLE:
                    pw.print("UnavailableState");
                    break;
                case IpServer.STATE_AVAILABLE:
                    pw.print("AvailableState");
                    break;
                case IpServer.STATE_TETHERED:
                    pw.print("TetheredState");
                    break;
                case IpServer.STATE_LOCAL_ONLY:
                    pw.print("LocalHotspotState");
                    break;
                default:
                    pw.print("UnknownState");
                    break;
            }
            pw.println(" - lastError = " + tetherState.lastError);
        }
        pw.println("Upstream wanted: " + upstreamWanted());
        pw.println("Current upstream interface(s): " + mCurrentUpstreamIfaceSet);
        pw.decreaseIndent();

        pw.println("Hardware offload:");
        pw.increaseIndent();
        mOffloadController.dump(pw);
        pw.decreaseIndent();

        dumpBpf(pw);

        pw.println("Private address coordinator:");
        pw.increaseIndent();
        mPrivateAddressCoordinator.dump(pw);
        pw.decreaseIndent();

        if (mWearableConnectionManager != null) {
            pw.println("WearableConnectionManager:");
            pw.increaseIndent();
            mWearableConnectionManager.dump(pw);
            pw.decreaseIndent();
        }

        pw.println("Log:");
        pw.increaseIndent();
        if (CollectionUtils.contains(args, "--short")) {
            pw.println("<log removed for brevity>");
        } else {
            mLog.dump(fd, pw, args);
        }
        pw.decreaseIndent();

        pw.decreaseIndent();
    }

    void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter writer, @Nullable String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PERMISSION_GRANTED) {
            writer.println("Permission Denial: can't dump.");
            return;
        }

        if (!HandlerUtils.runWithScissorsForDump(mHandler, () -> doDump(fd, writer, args),
                DUMP_TIMEOUT_MS)) {
            writer.println("Dump timeout after " + DUMP_TIMEOUT_MS + "ms");
        }
    }

    private void maybeDhcpLeasesChanged() {
        // null means wifi clients did not change.
        updateConnectedClients(null, null);
    }

    private void updateConnectedClients(final List<WifiClient> wifiClients,
            final List<WifiClient> localOnlyClients) {
        if (mConnectedClientsTracker.updateConnectedClients(mTetherMainSM.getAllDownstreams(),
                wifiClients, localOnlyClients)) {
            reportTetherClientsChanged(mConnectedClientsTracker.getLastTetheredClients());
        }
    }

    private class ControlCallback extends IpServer.Callback {
        @Override
        public void updateInterfaceState(IpServer who, int state, int lastError) {
            final String iface = who.interfaceName();
            final TetherState tetherState = mTetherStates.get(iface);
            if (tetherState != null && tetherState.ipServer.equals(who)) {
                tetherState.lastState = state;
                tetherState.lastError = lastError;
            } else {
                if (DBG) Log.d(TAG, "got notification from stale iface " + iface);
            }

            mLog.log(String.format("OBSERVED iface=%s state=%s error=%s", iface, state, lastError));

            // If TetherMainSM is in ErrorState, TetherMainSM stays there.
            // Thus we give a chance for TetherMainSM to recover to InitialState
            // by sending CMD_CLEAR_ERROR
            if (lastError == TETHER_ERROR_INTERNAL_ERROR) {
                mTetherMainSM.sendMessage(TetherMainSM.CMD_CLEAR_ERROR, who);
            }
            int which;
            switch (state) {
                case IpServer.STATE_UNAVAILABLE:
                case IpServer.STATE_AVAILABLE:
                    which = TetherMainSM.EVENT_IFACE_SERVING_STATE_INACTIVE;
                    break;
                case IpServer.STATE_TETHERED:
                case IpServer.STATE_LOCAL_ONLY:
                    which = TetherMainSM.EVENT_IFACE_SERVING_STATE_ACTIVE;
                    break;
                default:
                    Log.wtf(TAG, "Unknown interface state: " + state);
                    return;
            }
            mTetherMainSM.sendMessage(which, state, 0, who);
            sendTetherStateChangedBroadcast();
        }

        @Override
        public void updateLinkProperties(IpServer who, LinkProperties newLp) {
            final String iface = who.interfaceName();
            final int state;
            final TetherState tetherState = mTetherStates.get(iface);
            if (tetherState != null && tetherState.ipServer.equals(who)) {
                state = tetherState.lastState;
            } else {
                mLog.log("got notification from stale iface " + iface);
                return;
            }

            mLog.log(String.format(
                    "OBSERVED LinkProperties update iface=%s state=%s lp=%s",
                    iface, IpServer.getStateString(state), newLp));
            final int which = TetherMainSM.EVENT_IFACE_UPDATE_LINKPROPERTIES;
            mTetherMainSM.sendMessage(which, state, 0, newLp);
        }

        @Override
        public void dhcpLeasesChanged() {
            maybeDhcpLeasesChanged();
        }

        @Override
        public void requestEnableTethering(int tetheringType, boolean enabled) {
            mTetherMainSM.sendMessage(TetherMainSM.EVENT_REQUEST_CHANGE_DOWNSTREAM,
                    tetheringType, 0, enabled ? Boolean.TRUE : Boolean.FALSE);
        }
    }

    private boolean hasSystemFeature(final String feature) {
        return mContext.getPackageManager().hasSystemFeature(feature);
    }

    private boolean checkTetherableType(int type) {
        if ((type == TETHERING_WIFI || type == TETHERING_WIGIG)
                && !hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            return false;
        }

        if (type == TETHERING_WIFI_P2P && !hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            return false;
        }

        return type != TETHERING_INVALID;
    }

    private void ensureIpServerStarted(final String iface) {
        // If we don't care about this type of interface, ignore.
        final int interfaceType = ifaceNameToType(iface);
        if (!checkTetherableType(interfaceType)) {
            mLog.log(iface + " is used for " + interfaceType + " which is not tetherable"
                     + " (-1 == INVALID is expected on upstream interface)");
            return;
        }

        ensureIpServerStarted(iface, interfaceType, false /* isNcm */);
    }

    private void ensureIpServerStarted(final String iface, int interfaceType, boolean isNcm) {
        // If we have already started a TISM for this interface, skip.
        if (mTetherStates.containsKey(iface)) {
            mLog.log("active iface (" + iface + ") reported as added, ignoring");
            return;
        }

        mLog.i("adding IpServer for: " + iface);
        final TetherState tetherState = new TetherState(
                new IpServer(iface, mHandler, interfaceType, mLog, mNetd, mBpfCoordinator,
                        mRoutingCoordinator, new ControlCallback(), mConfig,
                        mPrivateAddressCoordinator, mTetheringMetrics,
                        mDeps.makeIpServerDependencies()), isNcm);
        mTetherStates.put(iface, tetherState);
        tetherState.ipServer.start();
    }

    private void ensureIpServerStopped(final String iface) {
        final TetherState tetherState = mTetherStates.get(iface);
        if (tetherState == null) return;

        tetherState.ipServer.stop();
        mLog.i("removing IpServer for: " + iface);
        mTetherStates.remove(iface);
    }

    private static String[] copy(String[] strarray) {
        return Arrays.copyOf(strarray, strarray.length);
    }

    void setPreferTestNetworks(final boolean prefer, IIntResultListener listener) {
        mHandler.post(() -> {
            mUpstreamNetworkMonitor.setPreferTestNetworks(prefer);
            try {
                listener.onResult(TETHER_ERROR_NO_ERROR);
            } catch (RemoteException e) { }
        });
    }

    @VisibleForTesting
    public TetherMainSM getTetherMainSMForTesting() {
        return mTetherMainSM;
    }
}
