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

package com.google.android.tv.btservices;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.google.android.tv.btservices.remote.DefaultProxy;
import com.google.android.tv.btservices.remote.DfuBinary;
import com.google.android.tv.btservices.remote.DfuManager;
import com.google.android.tv.btservices.remote.DfuProvider;
import com.google.android.tv.btservices.remote.RemoteProxy;
import com.google.android.tv.btservices.remote.RemoteProxy.BatteryResult;
import com.google.android.tv.btservices.remote.RemoteProxy.DfuResult;
import com.google.android.tv.btservices.remote.Version;
import com.google.android.tv.btservices.settings.BluetoothDeviceProvider;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public abstract class BluetoothDeviceService
        extends Service implements DfuManager.Listener, DfuProvider.Listener {
    private static final String TAG = "Atv.BtDeviceService";
    private static final boolean DEBUG = false;
    private static final String USER_SETUP_COMPLETE = "user_setup_complete";
    private static final String TV_USER_SETUP_COMPLETE = "tv_user_setup_complete";
    private static final String FASTPAIR_PROCESS = "com.google.android.gms.ui";

    private static final long BATTERY_VALIDITY_PERIOD_MS =
            TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);

    private static final long NOTIFY_FIRMWARE_UPDATE_DELAY_MS = 7000;
    private static final long PERIODIC_DFU_CHECK_MS =
            TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);
    private static final long INITIATE_DFU_CHECK_DELAY_MS =
            TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);
    // Following list is describes the set of intents to try in case we need to pair a remote. The
    // list should be traversed in order.
    private static final List<Intent> ORDERED_PAIRING_INTENTS = Collections.unmodifiableList(
            Arrays.asList(new Intent("com.google.android.tvsetup.app.REPAIR_REMOTE"),
                    new Intent("com.google.android.intent.action.CONNECT_INPUT")));
    protected final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<BluetoothDeviceProvider.Listener> mListeners = new ArrayList<>();
    private final List<DfuManager.Listener> mDfuListeners = new ArrayList<>();
    private final Binder mBinder = new LocalBinder();
    // Maps a MAC address to the last time battery level was refreshed. This is
    // used only by devices that uses polling for battery level.
    private final Map<BluetoothDevice, Stopwatch> mLastBatteryRefreshWatch = new HashMap<>();
    private final Map<BluetoothDevice, RemoteProxy> mProxies = new HashMap<>();
    private final Ticker ticker = new Ticker() {
        public long read() {
            return android.os.SystemClock.elapsedRealtimeNanos();
        }
    };
    private final Runnable mCheckDfu = this::checkDfu;
    BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final BluetoothDevice device = getDeviceHelper(intent);
            // The sequence of a typical connection is: acl connected, bonding, bonded, profile
            // connecting, profile connected.
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                switch (state) {
                    case BluetoothDevice.BOND_BONDED:
                        mHandler.post(() -> addDevice(device));
                        break;
                    case BluetoothDevice.BOND_NONE:
                        mHandler.post(() -> onDeviceUnbonded(device));
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        break;
                    default:
                        if (DEBUG) Log.e(TAG, "unknown state " + state + " " + device);
                }
            } else {
                switch (action) {
                    case BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED:
                        int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                        mHandler.post(() -> onA2dpConnectionStateChanged(device.getName(), state));
                        break;
                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        Log.i(TAG, "acl connected " + device);
                        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                            mHandler.post(() -> addDevice(device));
                            mHandler.post(() -> onDeviceUpdated(device));
                        }
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        Log.i(TAG, "acl disconnected " + device);
                        mHandler.post(() -> removeDevice(device));
                        mHandler.post(() -> onDeviceUpdated(device));
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED:
                        Log.i(TAG, "acl disconnect requested: " + device);
                        break;
                }
            }
        }
    };

    protected boolean isRemote(BluetoothDevice device) {
        boolean res = BluetoothUtils.isRemote(this, device);
        if (DEBUG) {
            Log.d(TAG, "Device " + device.getName() + " isRemote(): " + res);
        }
        return res;
    }

    protected static List<Intent> getPairingIntents() {
        return ORDERED_PAIRING_INTENTS;
    }

    public static void startPairingRemoteActivity(Context context) {
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            return;
        }
        Intent candidateIntent = null;
        intentLoop:
        for (Intent intent : getPairingIntents()) {
            for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
                if (info.activityInfo == null || info.activityInfo.applicationInfo == null) {
                    continue;
                }
                boolean isSystemApp = ((info.activityInfo.applicationInfo.flags &
                        ApplicationInfo.FLAG_SYSTEM) != 0) ||
                        (info.activityInfo.applicationInfo.isOem());
                Log.i(TAG, "Found activity: " + info.activityInfo + " for intent: " + intent +
                        " is System/OEM app: " + isSystemApp);
                if (!isSystemApp) {
                    continue;
                }
                candidateIntent = intent;
                break intentLoop;
            }
        }
        if (candidateIntent != null) {
            Intent intent = new Intent(candidateIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else {
            Log.w(TAG, "Did not find suitable intents for remote pairing.");
        }
    }

    private static BluetoothDevice getDeviceHelper(Intent intent) {
        if (intent == null) {
            return null;
        }
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    }

    protected static List<BluetoothDevice> getDevices() {
        final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null) {
            Set<BluetoothDevice> devices = btAdapter.getBondedDevices();
            if (devices != null) {
                return new ArrayList<>(devices);
            }
        }
        return Collections.emptyList();
    }

    public static BluetoothDevice findDevice(String address) {
        List<BluetoothDevice> devices = getDevices();
        BluetoothDevice curDevice = null;
        for (BluetoothDevice device : devices) {
            if (address.equals(device.getAddress())) {
                curDevice = device;
                break;
            }
        }
        return curDevice;
    }

    private static void forgetDevice(BluetoothDevice device) {
        if (device == null || !device.removeBond()) {
            Log.w(TAG, "failed to remove bond: " + device);
        }
    }

    public static void forgetAndRepair(Context context, BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "null device");
            return;
        }
        if (!device.removeBond()) {
            Log.w(TAG, "failed to remove bond");
        }
        startPairingRemoteActivity(context);
    }

    private static boolean isSetupComplete(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), USER_SETUP_COMPLETE, 0) > 0
                && Settings.Secure.getInt(context.getContentResolver(), TV_USER_SETUP_COMPLETE, 0)
                > 0;
    }

    protected abstract DfuProvider getDfuProvider();

    protected abstract RemoteProxy createRemoteProxy(BluetoothDevice device);

    private RemoteProxy createDefaultProxy(BluetoothDevice device){
        return new DefaultProxy(this, device);
    }

    private void checkDfu() {
        List<BluetoothDevice> devices = getDevices();
        for (BluetoothDevice device : devices) {
            if (!isRemote(device)) {
                continue;
            }
            deviceCheckDfu(device);
        }
        mHandler.removeCallbacks(mCheckDfu);
        mHandler.postDelayed(mCheckDfu, PERIODIC_DFU_CHECK_MS);
    }

    private void onDeviceUpdated(BluetoothDevice device) {
        mListeners.forEach(listener -> listener.onDeviceUpdated(device));
    }

    private void onDfuUpdated(BluetoothDevice device, DfuResult res) {
        mDfuListeners.forEach(listener -> listener.onDfuProgress(device, res));
    }

    private void addDevice(BluetoothDevice device) {
        if (device == null || !device.isConnected()) {
            return;
        }

        final RemoteProxy proxy;
        if (!isRemote(device)) {
            proxy = createDefaultProxy(device);
        } else {
            proxy = createRemoteProxy(device);
        }
        mProxies.put(device, proxy);

        if (!proxy.initialize(this)) {
            removeDevice(device);
            return;
        }

        // Initiate version read.
        refreshRemoteVersion(device, result -> {
            onDeviceUpdated(device);
        });

        // Initiate battery level read.
        initializeDeviceBatteryLevelRead(device);

        mHandler.postDelayed(() ->
                deviceCheckDfu(device), NOTIFY_FIRMWARE_UPDATE_DELAY_MS);
    }

    private void removeDevice(BluetoothDevice device) {
        RemoteProxy proxy = getRemoteProxy(device);
        if (proxy == null) {
            return;
        }
        // Clean up info for the disconnected device.
        mHandler.post(() -> {
            NotificationCenter.dismissUpdateNotification(device);
            mLastBatteryRefreshWatch.remove(device);
            mProxies.remove(device);
        });
    }

    private void onDeviceUnbonded(BluetoothDevice device) {
        NotificationCenter.dismissUpdateNotification(device);
        onDeviceUpdated(device);
    }

    private void deviceCheckDfu(BluetoothDevice device) {
        if (device == null || !device.isConnected() || !isRemote(device)) {
            NotificationCenter.dismissUpdateNotification(device);
            return;
        }

        RemoteProxy proxy = getRemoteProxy(device);
        if (proxy == null) {
            Log.e(TAG, "deviceCheckDfu: no proxy");
            NotificationCenter.dismissUpdateNotification(device);
            return;
        }

        if (proxy.getDfuState() != null) {
            Log.e(TAG, "deviceCheckDfu: already doing DFU for: " + device);
            NotificationCenter.dismissUpdateNotification(device);
            return;
        }

        if (proxy.supportsBackgroundDfu()) {
            // `startRemoteDfu` checks if all criteria for dfu are met.
            startRemoteDfu(device, true);
        } else {
            if (hasRemoteUpgrade(device) && !isRemoteLowBattery(device)) {
                NotificationCenter.sendDfuNotification(device);
            } else {
                NotificationCenter.dismissUpdateNotification(device);
            }
        }

        onDeviceUpdated(device);
    }

    private void connectDevice(BluetoothDevice device) {
        if (device != null) {
            CachedBluetoothDevice cachedDevice =
                    BluetoothUtils.getCachedBluetoothDevice(this, device);
            if (cachedDevice != null) {
                cachedDevice.connect();
            }
        }
    }

    private void disconnectDevice(BluetoothDevice device) {
        if (device != null) {
            CachedBluetoothDevice cachedDevice =
                    BluetoothUtils.getCachedBluetoothDevice(this, device);
            if (cachedDevice != null) {
                cachedDevice.disconnect();
            }
        }
    }

    private void renameDevice(BluetoothDevice device, String newName) {
        if (device != null) {
            device.setAlias(newName);
            mHandler.post(() -> onDeviceUpdated(device));
        }
    }

    private void refreshLowBatteryNotification(BluetoothDevice device, boolean forceNotification) {
        if (!isRemote(device)){
            return;
        }

        RemoteProxy proxy = getRemoteProxy(device);
        if (proxy == null) {
            return;
        }

        if (isRemoteCriticalBattery(device)) {
            NotificationCenter.refreshLowBatteryNotification(
                    device, NotificationCenter.BatteryState.CRITICAL, forceNotification);
        } else if (isRemoteLowBattery(device)) {
            NotificationCenter.refreshLowBatteryNotification(
                    device, NotificationCenter.BatteryState.LOW, forceNotification);
        } else {
            NotificationCenter.refreshLowBatteryNotification(
                    device, NotificationCenter.BatteryState.GOOD, forceNotification);
        }
    }

    private int getLowBatteryLevel(BluetoothDevice device) {
        RemoteProxy proxy = getRemoteProxy(device);
        if (proxy == null) {
            return RemoteProxy.DEFAULT_LOW_BATTERY_LEVEL;
        }
        return proxy.lowBatteryLevel();
    }

    private int getCriticalBatteryLevel(BluetoothDevice device) {
        return RemoteProxy.DEFAULT_CRITICAL_BATTERY_LEVEL;
    }

    protected boolean isRemoteLowBattery(BluetoothDevice device) {
        if (device == null) {
            return false;
        }
        if (!BluetoothUtils.isConnected(device)) {
            return false;
        }
        if (!isRemote(device)) {
            return false;
        }

        final int battery = getBatteryLevel(device);
        if (battery == BluetoothDevice.BATTERY_LEVEL_UNKNOWN) {
            return false;
        }
        return battery <= getLowBatteryLevel(device);
    }

    protected boolean isRemoteCriticalBattery(BluetoothDevice device) {
        if (device == null) {
            return false;
        }
        if (!BluetoothUtils.isConnected(device)) {
            return false;
        }
        if (!isRemote(device)) {
            return false;
        }

        final int battery = getBatteryLevel(device);
        if (battery == BluetoothDevice.BATTERY_LEVEL_UNKNOWN) {
            return false;
        }
        return battery <= getCriticalBatteryLevel(device);
    }

    protected boolean hasRemoteUpgrade(BluetoothDevice device) {
        if (device == null) {
            return false;
        }
        if (!BluetoothUtils.isConnected(device)) {
            return false;
        }
        if (!isRemote(device)) {
            return false;
        }

        final DfuProvider provider = getDfuProvider();
        if (provider == null) {
            return false;
        }

        Version version = getRemoteVersion(device);
        if (version == null || version.equals(Version.BAD_VERSION)) {
            return false;
        }
        final String name = BluetoothUtils.getOriginalName(device);
        return provider.getDfu(name, version) != null;
    }

    // DfuManager.Listener implementation.
    @Override
    public void onDfuProgress(BluetoothDevice device, RemoteProxy.DfuResult result) {
        mHandler.post(() -> onDfuUpdated(device, result));
    }

    private DfuResult getRemoteDfuState(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "getRemoteDfuState: no device");
            return null;
        }
        if (!BluetoothUtils.isConnected(device)) {
            return null;
        }
        if (!isRemote(device)) {
            Log.e(TAG, "getRemoteDfuState: not a remote " + device);
            return null;
        }

        RemoteProxy proxy = getRemoteProxy(device);
        return proxy != null ? proxy.getDfuState() : null;
    }

    protected void startRemoteDfu(String address) {
        BluetoothDevice device = findDevice(address);
        startRemoteDfu(device, false);
    }

    protected void startRemoteDfu(BluetoothDevice device, boolean background) {
        if (device == null) {
            Log.e(TAG, "startRemoteDfu: ");
            return;
        }
        if (!isRemote(device)) {
            Log.e(TAG, "startRemoteDfu: not a remote " + device);
            return;
        }

        if (!hasRemoteUpgrade(device)) {
            Log.e(TAG, "startRemoteDfu: not eligible for upgrade " + device);
            return;
        }

        if (isRemoteLowBattery(device)) {
            Log.e(TAG, "startRemoteDfu: cannot update due to low battery " + device);
            return;
        }

        if (!isSetupComplete(this)) {
            Log.e(TAG, "startRemoteDfu: oobe must be completed before updating " + device);
            return;
        }

        final DfuProvider provider = getDfuProvider();
        if (provider == null) {
            Log.e(TAG, "startRemoteDfu: no remote dfu provider");
            return;
        }

        RemoteProxy proxy = getRemoteProxy(device);
        if (proxy == null) {
            Log.e(TAG, "startRemoteDfu: proxy is null");
            return;
        }

        proxy.refreshVersion().thenAccept(status -> {
            if (!status) {
                return;
            }

            Version currentVersion = proxy.getLastKnownVersion();
            final String name = BluetoothUtils.getOriginalName(device);
            final DfuBinary dfu = provider.getDfu(name, currentVersion);

            if (dfu == null) {
                Log.e(TAG, "Unexpected null dfu binary");
                return;
            }

            Set<Version> versionsNeedRepairing = provider.getManualReconnectionVersions();

            final boolean needsRepair = versionsNeedRepairing.contains(currentVersion);

            Log.i(TAG, "current: " + currentVersion + " new version: " + dfu.getVersion() +
                    " repair: " + needsRepair);

            NotificationCenter.dismissUpdateNotification(device);
            proxy.requestDfu(dfu, this, background).thenAccept(result -> {
                DfuResult newResult = result;
                if (result == DfuResult.RESULT_DEVICE_BUSY) {
                    Log.i(TAG, "Device busy, skipping remote update request for " + device);
                    return;
                }

                if (result == DfuResult.RESULT_SUCCESS && needsRepair) {
                    newResult = DfuResult.RESULT_SUCCESS_NEEDS_PAIRING;
                }
                onDfuUpdated(device, newResult);
            });
        });
    }

    private void onA2dpConnectionStateChanged(String deviceName, int connectionStatus) {
        // Avoiding showing Toast while Fastpair is in Foreground.
        if (fastPairInForeground()) {
            return;
        }
        String resStr;
        String text;
        switch (connectionStatus) {
            case BluetoothProfile.STATE_CONNECTED:
                resStr = getResources().getString(R.string.settings_bt_pair_toast_connected);
                text = String.format(resStr, deviceName);
                Toast.makeText(BluetoothDeviceService.this.getApplicationContext(),
                        text, Toast.LENGTH_SHORT).show();
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                resStr = getResources().getString(R.string.settings_bt_pair_toast_disconnected);
                text = String.format(resStr, deviceName);
                Toast.makeText(BluetoothDeviceService.this.getApplicationContext(),
                        text, Toast.LENGTH_SHORT).show();
                break;
            case BluetoothProfile.STATE_CONNECTING:
            case BluetoothProfile.STATE_DISCONNECTING:
            default:
                break;
        }
    }

    private boolean fastPairInForeground() {
        ActivityManager activityManager = getSystemService(ActivityManager.class);
        if (activityManager == null) {
            return false;
        }
        List<RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        for (RunningAppProcessInfo appProcess : appProcesses) {
            if (FASTPAIR_PROCESS.equals(appProcess.processName) &&
                appProcess.importance  == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true;
            }
        }
        return false;
    }

    private RemoteProxy getRemoteProxy(BluetoothDevice device) {
        if (device == null) {
            return null;
        }
        return mProxies.get(device);
    }

    /**
     * Synchronous remote version read from RemoteProxy.
     *
     * This should only be called by UI thread that needs result immediately.
     */
    protected Version getRemoteVersion(BluetoothDevice device) {
        RemoteProxy proxy = getRemoteProxy(device);

        if (proxy != null) {
            return proxy.getLastKnownVersion();
        } else {
            return Version.BAD_VERSION;
        }
    }

    protected Version getRemoteVersion(String address) {
        BluetoothDevice device = findDevice(address);
        return getRemoteVersion(device);
    }

    /** Asynchronous remote version refresh. */
    private void refreshRemoteVersion(BluetoothDevice device, Consumer<Version> callback) {
        RemoteProxy proxy = getRemoteProxy(device);

        if (proxy != null) {
            proxy.refreshVersion().thenAccept(status -> {
                if (status) {
                    Version version = proxy.getLastKnownVersion();
                    callback.accept(version);
                } else {
                    Log.w(TAG, "Failed to refresh remote version.");
                }
            });
        }
    }

    private int getBatteryLevel(BluetoothDevice device) {
        if (device == null) {
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        if (!BluetoothUtils.isConnected(device)) {
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }

        RemoteProxy proxy = getRemoteProxy(device);
        if (proxy != null) {
            BatteryResult result = proxy.getLastKnownBatteryLevel();

            if (result.code() == BatteryResult.SUCCESS) {
                Stopwatch stopwatch = mLastBatteryRefreshWatch.get(device);
                if (stopwatch != null &&
                    stopwatch.elapsed(TimeUnit.MILLISECONDS) > BATTERY_VALIDITY_PERIOD_MS) {
                    stopwatch.reset();
                    stopwatch.start();

                    proxy.refreshBatteryLevel().thenAccept(status -> {
                        if (status) {
                            refreshLowBatteryNotification(device, false);
                            onDeviceUpdated(device);
                        }
                    });
                }

                return result.battery();
            }
        }

        return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
    }

    private String mapBatteryLevel(Context context, BluetoothDevice device, int level) {
        RemoteProxy proxy = getRemoteProxy(device);

        if (proxy == null) {
            return context.getString(
                    R.string.settings_remote_battery_level_percentage_label, level);
        }

        return proxy.mapBatteryLevel(context, level);
    }

    private void initializeDeviceBatteryLevelRead(BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "initializeDeviceBatteryLevelRead for null device");
            return;
        }

        RemoteProxy proxy = getRemoteProxy(device);
        if (proxy != null) {
            proxy.registerBatteryLevelCallback(() -> {
                refreshLowBatteryNotification(device, false);
                onDeviceUpdated(device);
            }).thenAccept(callbackRegistered -> {
                proxy.refreshBatteryLevel().thenAccept(result -> {
                    if (result) {
                        if (!callbackRegistered) {
                            // Callback is not registered. Enable polling.
                            Stopwatch stopwatch = Stopwatch.createStarted(ticker);
                            mLastBatteryRefreshWatch.put(device, stopwatch);
                        } else {
                            mLastBatteryRefreshWatch.remove(device);
                        }

                        refreshLowBatteryNotification(device, true);
                        onDeviceUpdated(device);
                    }
                });
            });
        }
    }

    private void initiateDfuCheck() {
        NotificationCenter.resetUpdateNotification();
        checkDfu();
    }

    // Implements DfuProvider.Listener
    @Override
    public void onDfuFileAdd() {
        initiateDfuCheck();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.e(TAG, "onCreate");

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED); // Headset connection
        registerReceiver(mBluetoothReceiver, filter);
        for (BluetoothDevice device : getDevices()) {
            if (device.isConnected()) {
                addDevice(device);
            }
        }

        mHandler.postDelayed(this::initiateDfuCheck, INITIATE_DFU_CHECK_DELAY_MS);

        NotificationCenter.initialize(this);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.e(TAG, "onDestroy");
        unregisterReceiver(mBluetoothReceiver);

        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        for (BluetoothDevice device : getDevices()) {
            if (!device.isConnected()) {
                continue;
            }

            RemoteProxy proxy = getRemoteProxy(device);

            if (proxy == null) {
                continue;
            }

            writer.printf("%s (%s):%n", device.getName(), device.getAddress());

            Version version = proxy.getLastKnownVersion();
            writer.printf("  Firmware Version: %s%n", version.toString());

            int battLevel = proxy.getLastKnownBatteryLevel().battery();
            writer.printf("  Battery Level: %d%n", battLevel);
        }
    }

    public class LocalBinder extends Binder implements BluetoothDeviceProvider {

        public List<BluetoothDevice> getDevices() {
            return BluetoothDeviceService.getDevices();
        }

        @Override
        public void connectDevice(BluetoothDevice device) {
            BluetoothDeviceService.this.connectDevice(device);
        }

        @Override
        public void disconnectDevice(BluetoothDevice device) {
            BluetoothDeviceService.this.disconnectDevice(device);
        }

        @Override
        public void forgetDevice(BluetoothDevice device) {
            BluetoothDeviceService.forgetDevice(device);
        }

        @Override
        public void renameDevice(BluetoothDevice device, String newName) {
            BluetoothDeviceService.this.renameDevice(device, newName);
        }

        @Override
        public int getBatteryLevel(BluetoothDevice device) {
            return BluetoothDeviceService.this.getBatteryLevel(device);
        }

        @Override
        public String mapBatteryLevel(Context context, BluetoothDevice device, int level) {
            return BluetoothDeviceService.this.mapBatteryLevel(context, device, level);
        }

        @Override
        public Version getVersion(BluetoothDevice device) {
            return BluetoothDeviceService.this.getRemoteVersion(device);
        }

        @Override
        public boolean hasUpgrade(BluetoothDevice device) {
            return BluetoothDeviceService.this.hasRemoteUpgrade(device);
        }

        @Override
        public boolean isBatteryLow(BluetoothDevice device) {
            return BluetoothDeviceService.this.isRemoteLowBattery(device);
        }

        @Override
        public DfuResult getDfuState(BluetoothDevice device) {
            return BluetoothDeviceService.this.getRemoteDfuState(device);
        }

        @Override
        public void startDfu(BluetoothDevice device) {
            BluetoothDeviceService.this.startRemoteDfu(device, false);
        }

        @Override
        public void addListener(BluetoothDeviceProvider.Listener listener) {
            mHandler.post(() -> {
                mListeners.add(listener);

                // Trigger first update after listener callback is registered.
                for (BluetoothDevice device : getDevices()) {
                    if (device.isConnected()) {
                        listener.onDeviceUpdated(device);
                    }
                }
            });
        }

        @Override
        public void removeListener(BluetoothDeviceProvider.Listener listener) {
            mHandler.post(() -> mListeners.remove(listener));
        }

        @Override
        public void addListener(DfuManager.Listener listener) {
            mHandler.post(() -> mDfuListeners.add(listener));
        }

        @Override
        public void removeListener(DfuManager.Listener listener) {
            mHandler.post(() -> mDfuListeners.remove(listener));
        }

        public void dismissDfuNotification(String address) {
            BluetoothDevice device = BluetoothDeviceService.findDevice(address);
            NotificationCenter.dismissUpdateNotification(device);
        }
    }
}
