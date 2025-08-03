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

package com.android.tv.mdnsoffloadmanager;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.tv.mdnsoffloadmanager.util.WakeLockWrapper;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import device.google.atv.mdns_offload.IMdnsOffload;
import device.google.atv.mdns_offload.IMdnsOffloadManager;


public class MdnsOffloadManagerService extends Service {

    private static final String TAG = MdnsOffloadManagerService.class.getSimpleName();
    private static final int VENDOR_SERVICE_COMPONENT_ID =
            R.string.config_mdnsOffloadVendorServiceComponent;
    private static final int AWAIT_DUMP_SECONDS = 5;

    private final ConnectivityManager.NetworkCallback mNetworkCallback =
            new ConnectivityManagerNetworkCallback();
    private final Map<String, InterfaceOffloadManager> mInterfaceOffloadManagers = new HashMap<>();
    private final Injector mInjector;
    private Handler mHandler;
    private PriorityListManager mPriorityListManager;
    private OffloadIntentStore mOffloadIntentStore;
    private OffloadWriter mOffloadWriter;
    private ConnectivityManager mConnectivityManager;
    private PackageManager mPackageManager;
    private WakeLockWrapper mWakeLock;

    public MdnsOffloadManagerService() {
        this(new Injector());
    }

    @VisibleForTesting
    MdnsOffloadManagerService(@NonNull Injector injector) {
        super();
        injector.setContext(this);
        mInjector = injector;
    }

    @VisibleForTesting
    static class Injector {

        private Context mContext = null;
        private Looper mLooper = null;

        void setContext(Context context) {
            mContext = context;
        }

        synchronized Looper getLooper() {
            if (mLooper == null) {
                HandlerThread ht = new HandlerThread("MdnsOffloadManager");
                ht.start();
                mLooper = ht.getLooper();
            }
            return mLooper;
        }

        Resources getResources() {
            return mContext.getResources();
        }

        ConnectivityManager getConnectivityManager() {
            return mContext.getSystemService(ConnectivityManager.class);
        }


        PowerManager.LowPowerStandbyPolicy getLowPowerStandbyPolicy() {
            return mContext.getSystemService(PowerManager.class).getLowPowerStandbyPolicy();
        }

        WakeLockWrapper newWakeLock() {
            return new WakeLockWrapper(
                    mContext.getSystemService(PowerManager.class)
                            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG));
        }

        PackageManager getPackageManager() {
            return mContext.getPackageManager();
        }

        boolean isInteractive() {
            return mContext.getSystemService(PowerManager.class).isInteractive();
        }

        boolean bindService(Intent intent, ServiceConnection connection, int flags) {
            return mContext.bindService(intent, connection, flags);
        }

        void registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) {
            mContext.registerReceiver(receiver, filter, flags);
        }

        int getCallingUid() {
            return Binder.getCallingUid();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler(mInjector.getLooper());
        mPriorityListManager = new PriorityListManager(mInjector.getResources());
        mOffloadIntentStore = new OffloadIntentStore(mPriorityListManager);
        mOffloadWriter = new OffloadWriter();
        mConnectivityManager = mInjector.getConnectivityManager();
        mPackageManager = mInjector.getPackageManager();
        mWakeLock = mInjector.newWakeLock();
        bindVendorService();
        setupScreenBroadcastReceiver();
        setupConnectivityListener();
        setupStandbyPolicyListener();
    }

    private void bindVendorService() {
        String vendorServicePath = mInjector.getResources().getString(VENDOR_SERVICE_COMPONENT_ID);

        if (vendorServicePath.isEmpty()) {
            String msg = "vendorServicePath is empty. Bind cannot proceed.";
            Log.e(TAG, msg);
            throw new IllegalArgumentException(msg);
        }
        ComponentName componentName = ComponentName.unflattenFromString(vendorServicePath);
        if (componentName == null) {
            String msg = "componentName cannot be extracted from vendorServicePath."
                    + " Bind cannot proceed.";
            Log.e(TAG, msg);
            throw new IllegalArgumentException(msg);
        }

        Log.d(TAG, "IMdnsOffloadManager is binding to: " + componentName);

        Intent explicitIntent = new Intent();
        explicitIntent.setComponent(componentName);
        boolean bindingSuccessful = mInjector.bindService(
                explicitIntent, mVendorServiceConnection, Context.BIND_AUTO_CREATE);
        if (!bindingSuccessful) {
            String msg = "Failed to bind to vendor service at {" + vendorServicePath + "}.";
            Log.e(TAG, msg);
            throw new IllegalStateException(msg);
        }
    }

    private void setupScreenBroadcastReceiver() {
        BroadcastReceiver receiver = new ScreenBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mInjector.registerReceiver(receiver, filter, 0);
        mHandler.post(() -> mOffloadWriter.setOffloadState(!mInjector.isInteractive()));
    }

    private void setupConnectivityListener() {
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build();
        mConnectivityManager.registerNetworkCallback(networkRequest, mNetworkCallback);
    }

    private void setupStandbyPolicyListener() {
        BroadcastReceiver receiver = new LowPowerStandbyPolicyReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(PowerManager.ACTION_LOW_POWER_STANDBY_POLICY_CHANGED);
        mInjector.registerReceiver(receiver, filter, 0);
        refreshAppIdAllowlist();
    }

    private void refreshAppIdAllowlist() {
        PowerManager.LowPowerStandbyPolicy standbyPolicy = mInjector.getLowPowerStandbyPolicy();
        Set<Integer> allowedAppIds = standbyPolicy.getExemptPackages()
                .stream()
                .map(pkg -> {
                    try {
                        return mPackageManager.getPackageUid(pkg, 0);
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.w(TAG, "Unable to get UID of package {" + pkg + "}.");
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .map(UserHandle::getAppId)
                .collect(Collectors.toSet());
        mHandler.post(() -> {
            mOffloadIntentStore.setAppIdAllowlist(allowedAppIds);
            mInterfaceOffloadManagers.values()
                    .forEach(InterfaceOffloadManager::onAppIdAllowlistUpdated);
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mOffloadManagerBinder;
    }

    @Override
    protected void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strings) {
        CountDownLatch doneSignal = new CountDownLatch(1);
        mHandler.post(() -> {
            dump(printWriter);
            doneSignal.countDown();
        });
        boolean success = false;
        try {
            success = doneSignal.await(AWAIT_DUMP_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        if (!success) {
            Log.e(TAG, "Failed to dump state on handler thread");
        }
    }

    @WorkerThread
    private void dump(PrintWriter writer) {
        mOffloadIntentStore.dump(writer);
        mInterfaceOffloadManagers.values().forEach(manager -> manager.dump(writer));
        mOffloadWriter.dump(writer);
        mOffloadIntentStore.dumpProtocolData(writer);
    }

    private final IMdnsOffloadManager.Stub mOffloadManagerBinder = new IMdnsOffloadManager.Stub() {
        @Override
        public int addProtocolResponses(@NonNull String networkInterface,
                @NonNull OffloadServiceInfo serviceOffloadData,
                @NonNull IBinder clientToken) {
            Objects.requireNonNull(networkInterface);
            Objects.requireNonNull(serviceOffloadData);
            Objects.requireNonNull(clientToken);
            int callerUid = mInjector.getCallingUid();
            OffloadIntentStore.OffloadIntent offloadIntent =
                    mOffloadIntentStore.registerOffloadIntent(
                            networkInterface, serviceOffloadData, clientToken, callerUid);
            try {
                offloadIntent.mClientToken.linkToDeath(
                        () -> removeProtocolResponses(offloadIntent.mRecordKey, clientToken), 0);
            } catch (RemoteException e) {
                String msg = "Error while setting a callback for linkToDeath binder" +
                        " {" + offloadIntent.mClientToken + "} in addProtocolResponses.";
                Log.e(TAG, msg, e);
                return offloadIntent.mRecordKey;
            }
            mHandler.post(() -> {
                getInterfaceOffloadManager(networkInterface).refreshProtocolResponses();
            });
            return offloadIntent.mRecordKey;
        }

        @Override
        public void removeProtocolResponses(int recordKey, @NonNull IBinder clientToken) {
            if (recordKey <= 0) {
                throw new IllegalArgumentException("recordKey must be positive");
            }
            Objects.requireNonNull(clientToken);
            mHandler.post(() -> {
                OffloadIntentStore.OffloadIntent offloadIntent =
                        mOffloadIntentStore.getAndRemoveOffloadIntent(recordKey, clientToken);
                if (offloadIntent == null) {
                    return;
                }
                getInterfaceOffloadManager(offloadIntent.mNetworkInterface)
                        .refreshProtocolResponses();
            });
        }

        @Override
        public void addToPassthroughList(
                @NonNull String networkInterface,
                @NonNull String qname,
                @NonNull IBinder clientToken) {
            Objects.requireNonNull(networkInterface);
            Objects.requireNonNull(qname);
            Objects.requireNonNull(clientToken);
            int callerUid = mInjector.getCallingUid();
            mHandler.post(() -> {
                OffloadIntentStore.PassthroughIntent ptIntent =
                        mOffloadIntentStore.registerPassthroughIntent(
                                networkInterface, qname, clientToken, callerUid);
                IBinder token = ptIntent.mClientToken;
                try {
                    token.linkToDeath(
                            () -> removeFromPassthroughList(
                                    networkInterface, ptIntent.mCanonicalQName, token), 0);
                } catch (RemoteException e) {
                    String msg = "Error while setting a callback for linkToDeath binder {"
                            + token + "} in addToPassthroughList.";
                    Log.e(TAG, msg, e);
                    return;
                }
                getInterfaceOffloadManager(networkInterface).refreshPassthroughList();
            });
        }

        @Override
        public void removeFromPassthroughList(
                @NonNull String networkInterface,
                @NonNull String qname,
                @NonNull IBinder clientToken) {
            Objects.requireNonNull(networkInterface);
            Objects.requireNonNull(qname);
            Objects.requireNonNull(clientToken);
            mHandler.post(() -> {
                boolean removed = mOffloadIntentStore.removePassthroughIntent(qname, clientToken);
                if (removed) {
                    getInterfaceOffloadManager(networkInterface).refreshPassthroughList();
                }
            });
        }

        @Override
        public int getInterfaceVersion() {
            return super.VERSION;
        }

        @Override
        public String getInterfaceHash() {
            return super.HASH;
        }
    };

    private InterfaceOffloadManager getInterfaceOffloadManager(String networkInterface) {
        return mInterfaceOffloadManagers.computeIfAbsent(
                networkInterface,
                iface -> new InterfaceOffloadManager(iface, mOffloadIntentStore, mOffloadWriter));
    }

    private final ServiceConnection mVendorServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "IMdnsOffload service bound successfully.");
            IMdnsOffload vendorService = IMdnsOffload.Stub.asInterface(service);
            mHandler.post(() -> {
                mOffloadWriter.setVendorService(vendorService);
                mOffloadWriter.resetAll();
                mInterfaceOffloadManagers.values()
                        .forEach(InterfaceOffloadManager::onVendorServiceConnected);
                mOffloadWriter.applyOffloadState();
            });
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.e(TAG, "IMdnsOffload service has unexpectedly disconnected.");
            mHandler.post(() -> {
                mOffloadWriter.setVendorService(null);
                mInterfaceOffloadManagers.values()
                        .forEach(InterfaceOffloadManager::onVendorServiceDisconnected);
            });
        }
    };

    private class ScreenBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Note: Screen on/off here is actually historical naming for the overall interactive
            // state of the device:
            // https://developer.android.com/reference/android/os/PowerManager#isInteractive()
            String action = intent.getAction();
            mHandler.post(() -> {
                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    mOffloadWriter.setOffloadState(false);
                    mOffloadWriter.retrieveAndClearMetrics(mOffloadIntentStore.getRecordKeys());
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    try {
                        mWakeLock.acquire(5000);
                        mOffloadWriter.setOffloadState(true);
                    } finally {
                        mWakeLock.release();
                    }
                }
            });
        }
    }

    private class LowPowerStandbyPolicyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!PowerManager.ACTION_LOW_POWER_STANDBY_POLICY_CHANGED.equals(intent.getAction())) {
                return;
            }
            refreshAppIdAllowlist();
        }
    }

    private class ConnectivityManagerNetworkCallback extends ConnectivityManager.NetworkCallback {
        private final Map<Network, LinkProperties> mLinkProperties = new HashMap<>();

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            // We only want to know the interface name of a network. This method is
            // called right after onAvailable() or any other important change during the lifecycle
            // of the network.
            mHandler.post(() -> {
                LinkProperties previousProperties = mLinkProperties.put(network, linkProperties);
                if (previousProperties != null &&
                        !previousProperties.getInterfaceName().equals(
                                linkProperties.getInterfaceName())) {
                    // This means that the interface changed names, which may happen
                    // but very rarely.
                    InterfaceOffloadManager offloadManager =
                            getInterfaceOffloadManager(previousProperties.getInterfaceName());
                    offloadManager.onNetworkLost();
                }

                // We trigger an onNetworkAvailable even if the existing is the same in case
                // anything needs to be refreshed due to the LinkProperties change.
                InterfaceOffloadManager offloadManager =
                        getInterfaceOffloadManager(linkProperties.getInterfaceName());
                offloadManager.onNetworkAvailable();
            });
        }

        @Override
        public void onLost(@NonNull Network network) {
            mHandler.post(() -> {
                // Network object is guaranteed to match a network object from a previous
                // onLinkPropertiesChanged() so the LinkProperties must be available to retrieve
                // the associated iface.
                LinkProperties previousProperties = mLinkProperties.remove(network);
                if (previousProperties == null){
                    Log.w(TAG,"Network "+ network + " lost before being available.");
                    return;
                }
                InterfaceOffloadManager offloadManager =
                        getInterfaceOffloadManager(previousProperties.getInterfaceName());
                offloadManager.onNetworkLost();
            });
        }
    }
}
