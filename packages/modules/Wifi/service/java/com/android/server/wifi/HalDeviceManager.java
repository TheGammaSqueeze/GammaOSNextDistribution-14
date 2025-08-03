/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.wifi;

import static com.android.server.wifi.HalDeviceManagerUtil.jsonToStaticChipInfo;
import static com.android.server.wifi.HalDeviceManagerUtil.staticChipInfoToJson;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_STATIC_CHIP_INFO;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.NetworkInfo;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiScanner;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.WorkSource;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.HalDeviceManagerUtil.StaticChipInfo;
import com.android.server.wifi.hal.WifiApIface;
import com.android.server.wifi.hal.WifiChip;
import com.android.server.wifi.hal.WifiHal;
import com.android.server.wifi.hal.WifiNanIface;
import com.android.server.wifi.hal.WifiP2pIface;
import com.android.server.wifi.hal.WifiRttController;
import com.android.server.wifi.hal.WifiStaIface;
import com.android.server.wifi.util.WorkSourceHelper;
import com.android.wifi.flags.FeatureFlags;
import com.android.wifi.resources.R;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles device management through the HAL interface.
 */
public class HalDeviceManager {
    private static final String TAG = "HalDevMgr";
    private static final boolean VDBG = false;
    private final FeatureFlags mFeatureFlags;
    private boolean mDbg = false;

    public static final long CHIP_CAPABILITY_ANY = 0L;
    private static final long CHIP_CAPABILITY_UNINITIALIZED = -1L;

    private static final int DBS_24G_5G_MASK =
            WifiScanner.WIFI_BAND_24_GHZ | WifiScanner.WIFI_BAND_5_GHZ;
    private static final int DBS_5G_6G_MASK =
            WifiScanner.WIFI_BAND_5_GHZ | WifiScanner.WIFI_BAND_6_GHZ;

    private static final int START_HAL_RETRY_INTERVAL_MS = 20;
    // Number of attempts a start() is re-tried. A value of 0 means no retries after a single
    // attempt.
    @VisibleForTesting
    public static final int START_HAL_RETRY_TIMES = 3;

    private final WifiContext mContext;
    private final Clock mClock;
    private final WifiInjector mWifiInjector;
    private final Handler mEventHandler;
    private WifiHal mWifiHal;
    private WifiDeathRecipient mIWifiDeathRecipient;
    private boolean mIsConcurrencyComboLoadedFromDriver;
    private boolean mWaitForDestroyedListeners;
    // Map of Interface name to their associated ConcreteClientModeManager
    private final Map<String, ConcreteClientModeManager> mClientModeManagers = new ArrayMap<>();
    // Map of Interface name to their associated SoftApManager
    private final Map<String, SoftApManager> mSoftApManagers = new ArrayMap<>();
    private boolean mIsP2pConnected = false;

    /**
     * Public API for querying interfaces from the HalDeviceManager.
     *
     * TODO (b/256648410): Consider replacing these values with WifiChip.IFACE_TYPE_
     *                     to avoid duplication.
     */
    public static final int HDM_CREATE_IFACE_STA = 0;
    public static final int HDM_CREATE_IFACE_AP = 1;
    public static final int HDM_CREATE_IFACE_AP_BRIDGE = 2;
    public static final int HDM_CREATE_IFACE_P2P = 3;
    public static final int HDM_CREATE_IFACE_NAN = 4;

    @IntDef(flag = false, prefix = { "HDM_CREATE_IFACE_TYPE_" }, value = {
            HDM_CREATE_IFACE_STA,
            HDM_CREATE_IFACE_AP,
            HDM_CREATE_IFACE_AP_BRIDGE,
            HDM_CREATE_IFACE_P2P,
            HDM_CREATE_IFACE_NAN,
    })
    public @interface HdmIfaceTypeForCreation {};

    public static final SparseIntArray HAL_IFACE_MAP = new SparseIntArray() {{
            put(HDM_CREATE_IFACE_STA, WifiChip.IFACE_TYPE_STA);
            put(HDM_CREATE_IFACE_AP, WifiChip.IFACE_TYPE_AP);
            put(HDM_CREATE_IFACE_AP_BRIDGE, WifiChip.IFACE_TYPE_AP);
            put(HDM_CREATE_IFACE_P2P, WifiChip.IFACE_TYPE_P2P);
            put(HDM_CREATE_IFACE_NAN, WifiChip.IFACE_TYPE_NAN);
        }};

    public static final SparseIntArray CONCURRENCY_TYPE_TO_CREATE_TYPE_MAP = new SparseIntArray() {{
            put(WifiChip.IFACE_CONCURRENCY_TYPE_STA, HDM_CREATE_IFACE_STA);
            put(WifiChip.IFACE_CONCURRENCY_TYPE_AP, HDM_CREATE_IFACE_AP);
            put(WifiChip.IFACE_CONCURRENCY_TYPE_AP_BRIDGED, HDM_CREATE_IFACE_AP_BRIDGE);
            put(WifiChip.IFACE_CONCURRENCY_TYPE_P2P, HDM_CREATE_IFACE_P2P);
            put(WifiChip.IFACE_CONCURRENCY_TYPE_NAN, HDM_CREATE_IFACE_NAN);
        }};


    // public API
    public HalDeviceManager(WifiContext context, Clock clock, WifiInjector wifiInjector,
            Handler handler) {
        mContext = context;
        mClock = clock;
        mWifiInjector = wifiInjector;
        mFeatureFlags = mWifiInjector.getDeviceConfigFacade().getFeatureFlags();
        mEventHandler = handler;
        mIWifiDeathRecipient = new WifiDeathRecipient();
        mWifiHal = getWifiHalMockable(context, wifiInjector);
        // Monitor P2P connection to treat disconnected P2P as low priority.
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!intent.getAction().equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)) {
                    return;
                }
                NetworkInfo networkInfo =
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                mIsP2pConnected = networkInfo != null
                        && networkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED;
            }
        }, intentFilter, null, mEventHandler);
    }

    @VisibleForTesting
    protected WifiHal getWifiHalMockable(WifiContext context, WifiInjector wifiInjector) {
        return new WifiHal(context, wifiInjector.getSsidTranslator());
    }

    /**
     * Enables verbose logging.
     */
    public void enableVerboseLogging(boolean verboseEnabled) {
        mDbg = verboseEnabled;

        if (VDBG) {
            mDbg = true; // just override
        }
    }

    /**
     * Actually starts the HalDeviceManager: separate from constructor since may want to phase
     * at a later time.
     *
     * TODO: if decide that no need for separating construction from initialization (e.g. both are
     * done at injector) then move to constructor.
     */
    public void initialize() {
        initializeInternal();
        registerWifiHalEventCallback();
    }

    /**
     * Register a ManagerStatusListener to get information about the status of the manager. Use the
     * isReady() and isStarted() methods to check status immediately after registration and when
     * triggered.
     *
     * It is safe to re-register the same callback object - duplicates are detected and only a
     * single copy kept.
     *
     * @param listener ManagerStatusListener listener object.
     * @param handler Handler on which to dispatch listener. Null implies the listener will be
     *                invoked synchronously from the context of the client which triggered the
     *                state change.
     */
    public void registerStatusListener(@NonNull ManagerStatusListener listener,
            @Nullable Handler handler) {
        synchronized (mLock) {
            if (!mManagerStatusListeners.add(new ManagerStatusListenerProxy(listener, handler))) {
                Log.w(TAG, "registerStatusListener: duplicate registration ignored");
            }
        }
    }

    /**
     * Returns whether the vendor HAL is supported on this device or not.
     */
    public boolean isSupported() {
        return mWifiHal.isSupported();
    }

    /**
     * Returns the current status of the HalDeviceManager: whether or not it is ready to execute
     * commands. A return of 'false' indicates that the HAL service (IWifi) is not available. Use
     * the registerStatusListener() to listener for status changes.
     */
    public boolean isReady() {
        return mWifiHal.isInitializationComplete();
    }

    /**
     * Returns the current status of Wi-Fi: started (true) or stopped (false).
     */
    public boolean isStarted() {
        return isWifiStarted();
    }

    /**
     * Attempts to start Wi-Fi. Returns the success (true) or failure (false) or
     * the start operation. Will also dispatch any registered ManagerStatusCallback.onStart() on
     * success.
     */
    public boolean start() {
        return startWifi();
    }

    /**
     * Stops Wi-Fi. Will also dispatch any registeredManagerStatusCallback.onStop().
     */
    public void stop() {
        stopWifi();
        mWifiHal.invalidate();
    }

    /**
     * HAL device manager status change listener.
     */
    public interface ManagerStatusListener {
        /**
         * Indicates that the status of the HalDeviceManager has changed. Use isReady() and
         * isStarted() to obtain status information.
         */
        void onStatusChanged();
    }

    /**
     * Return the set of supported interface types across all Wi-Fi chips on the device.
     *
     * @return A set of IfaceTypes constants (possibly empty, e.g. on error).
     */
    public Set<Integer> getSupportedIfaceTypes() {
        return getSupportedIfaceTypesInternal(null);
    }

    /**
     * Return the set of supported interface types for the specified Wi-Fi chip.
     *
     * @return A set of IfaceTypes constants  (possibly empty, e.g. on error).
     */
    public Set<Integer> getSupportedIfaceTypes(WifiChip chip) {
        return getSupportedIfaceTypesInternal(chip);
    }

    // interface-specific behavior

    /**
     * Create a STA interface if possible. Changes chip mode and removes conflicting interfaces if
     * needed and permitted by priority.
     *
     * @param requiredChipCapabilities The bitmask of Capabilities which are required.
     *                                 See IWifiChip.hal for documentation.
     * @param destroyedListener Optional (nullable) listener to call when the allocated interface
     *                          is removed. Will only be registered and used if an interface is
     *                          created successfully.
     * @param handler Handler on which to dispatch listener. Must be non Null if destroyedListener
     *                is set. If the this handler is running on the same thread as the client which
     *                triggered the iface destruction, the listener will be invoked synchronously
     *                from that context of the client.
     * @param requestorWs Requestor worksource. This will be used to determine priority of this
     *                    interface using rules based on the requestor app's context.
     * @param concreteClientModeManager ConcreteClientModeManager requesting the interface.
     * @return A newly created interface - or null if the interface could not be created.
     */
    public WifiStaIface createStaIface(
            long requiredChipCapabilities,
            @Nullable InterfaceDestroyedListener destroyedListener, @Nullable Handler handler,
            @NonNull WorkSource requestorWs,
            @NonNull ConcreteClientModeManager concreteClientModeManager) {
        if (concreteClientModeManager == null) {
            Log.wtf(TAG, "Cannot create STA Iface with null ConcreteClientModeManager");
            return null;
        }
        WifiStaIface staIface = (WifiStaIface) createIface(HDM_CREATE_IFACE_STA,
                requiredChipCapabilities, destroyedListener, handler, requestorWs, null);
        if (staIface != null) {
            mClientModeManagers.put(getName(staIface), concreteClientModeManager);
        }
        return staIface;
    }

    /**
     * Create a STA interface if possible. Changes chip mode and removes conflicting interfaces if
     * needed and permitted by priority.
     *
     * @param destroyedListener Optional (nullable) listener to call when the allocated interface
     *                          is removed. Will only be registered and used if an interface is
     *                          created successfully.
     * @param handler Handler on which to dispatch listener. Must be non Null if destroyedListener
     *                is set. If the handler is running on the same thread as the client which
     *                triggered the iface destruction, the listener will be invoked synchronously
     *                from that context of the client.
     * @param requestorWs Requestor worksource. This will be used to determine priority of this
     *                    interface using rules based on the requestor app's context.
     * @param concreteClientModeManager ConcreteClientModeManager requesting the interface.
     * @return A newly created interface - or null if the interface could not be created.
     */
    public WifiStaIface createStaIface(
            @Nullable InterfaceDestroyedListener destroyedListener, @Nullable Handler handler,
            @NonNull WorkSource requestorWs,
            @NonNull ConcreteClientModeManager concreteClientModeManager) {
        return createStaIface(CHIP_CAPABILITY_ANY, destroyedListener, handler, requestorWs,
                concreteClientModeManager);
    }

    /**
     * Create AP interface if possible (see createStaIface doc).
     */
    public WifiApIface createApIface(
            long requiredChipCapabilities,
            @Nullable InterfaceDestroyedListener destroyedListener, @Nullable Handler handler,
            @NonNull WorkSource requestorWs, boolean isBridged,
            @NonNull SoftApManager softApManager, @NonNull List<OuiKeyedData> vendorData) {
        if (softApManager == null) {
            Log.e(TAG, "Cannot create AP Iface with null SoftApManager");
            return null;
        }
        WifiApIface apIface = (WifiApIface) createIface(isBridged ? HDM_CREATE_IFACE_AP_BRIDGE
                : HDM_CREATE_IFACE_AP, requiredChipCapabilities, destroyedListener,
                handler, requestorWs, vendorData);
        if (apIface != null) {
            mSoftApManagers.put(getName(apIface), softApManager);
        }
        return apIface;
    }

    /**
     * Create P2P interface if possible (see createStaIface doc).
     */
    public String createP2pIface(
            long requiredChipCapabilities,
            @Nullable InterfaceDestroyedListener destroyedListener,
            @Nullable Handler handler, @NonNull WorkSource requestorWs) {
        WifiP2pIface iface = (WifiP2pIface) createIface(HDM_CREATE_IFACE_P2P,
                requiredChipCapabilities, destroyedListener, handler, requestorWs, null);
        if (iface == null) {
            return null;
        }
        String ifaceName = getName(iface);
        if (TextUtils.isEmpty(ifaceName)) {
            removeIface(iface);
            return null;
        }
        mWifiP2pIfaces.put(ifaceName, iface);
        return ifaceName;
    }

    /**
     * Create P2P interface if possible (see createStaIface doc).
     */
    public String createP2pIface(@Nullable InterfaceDestroyedListener destroyedListener,
            @Nullable Handler handler, @NonNull WorkSource requestorWs) {
        return createP2pIface(CHIP_CAPABILITY_ANY, destroyedListener, handler, requestorWs);
    }

    /**
     * Create NAN interface if possible (see createStaIface doc).
     */
    public WifiNanIface createNanIface(@Nullable InterfaceDestroyedListener destroyedListener,
            @Nullable Handler handler, @NonNull WorkSource requestorWs) {
        return (WifiNanIface) createIface(HDM_CREATE_IFACE_NAN, CHIP_CAPABILITY_ANY,
                destroyedListener, handler, requestorWs, null);
    }

    /**
     * Removes (releases/destroys) the given interface. Will trigger any registered
     * InterfaceDestroyedListeners.
     */
    public boolean removeIface(WifiHal.WifiInterface iface) {
        boolean success = removeIfaceInternal(iface, /* validateRttController */true);
        return success;
    }

    /**
     * Wrapper around {@link #removeIface(WifiHal.WifiInterface)} for P2P ifaces.
     */
    public boolean removeP2pIface(String ifaceName) {
        WifiP2pIface iface = mWifiP2pIfaces.get(ifaceName);
        if (iface == null) return false;
        if (!removeIface(iface)) {
            Log.e(TAG, "Unable to remove p2p iface " + ifaceName);
            return false;
        }
        mWifiP2pIfaces.remove(ifaceName);
        return true;
    }

    private InterfaceCacheEntry getInterfaceCacheEntry(WifiHal.WifiInterface iface) {
        String name = getName(iface);
        int type = getType(iface);
        if (VDBG) Log.d(TAG, "getInterfaceCacheEntry: iface(name)=" + name);

        synchronized (mLock) {
            InterfaceCacheEntry cacheEntry = mInterfaceInfoCache.get(Pair.create(name, type));
            if (cacheEntry == null) {
                Log.e(TAG, "getInterfaceCacheEntry: no entry for iface(name)=" + name);
                return null;
            }

            return cacheEntry;
        }
    }

    /**
     * Returns the WifiChip corresponding to the specified interface (or null on error).
     *
     * Note: clients must not perform chip mode changes or interface management (create/delete)
     * operations on WifiChip directly. However, they can use the WifiChip interface to perform
     * other functions - e.g. calling the debug/trace methods.
     */
    public WifiChip getChip(WifiHal.WifiInterface iface) {
        synchronized (mLock) {
            InterfaceCacheEntry cacheEntry = getInterfaceCacheEntry(iface);
            return (cacheEntry == null) ? null : cacheEntry.chip;
        }
    }

    private WifiChipInfo getChipInfo(WifiHal.WifiInterface iface) {
        synchronized (mLock) {
            InterfaceCacheEntry cacheEntry = getInterfaceCacheEntry(iface);
            if (cacheEntry == null) return null;

            WifiChipInfo[] chipInfos = getAllChipInfoCached();
            if (chipInfos == null) return null;

            for (WifiChipInfo info: chipInfos) {
                if (info.chipId == cacheEntry.chipId) {
                    return info;
                }
            }
            return null;
        }
    }

    /**
     * See {@link WifiNative#getSupportedBandCombinations(String)}.
     */
    public Set<List<Integer>> getSupportedBandCombinations(WifiHal.WifiInterface iface) {
        synchronized (mLock) {
            Set<List<Integer>> combinations = getCachedSupportedBandCombinations(iface);
            if (combinations == null) return null;
            return Collections.unmodifiableSet(combinations);
        }
    }

    private Set<List<Integer>> getCachedSupportedBandCombinations(
            WifiHal.WifiInterface iface) {
        WifiChipInfo info = getChipInfo(iface);
        if (info == null) return null;
        // If there is no band combination information, cache it.
        if (info.bandCombinations == null) {
            if (info.radioCombinations == null) {
                WifiChip chip = getChip(iface);
                if (chip == null) return null;
                info.radioCombinations = getChipSupportedRadioCombinations(chip);
            }
            info.bandCombinations = getChipSupportedBandCombinations(info.radioCombinations);
            if (mDbg) {
                Log.d(TAG, "radioCombinations=" + info.radioCombinations
                        + " bandCombinations=" + info.bandCombinations);
            }
        }
        return info.bandCombinations;
    }

    /**
     * See {@link WifiNative#isBandCombinationSupported(String, List)}.
     */
    public boolean isBandCombinationSupported(WifiHal.WifiInterface iface,
            @NonNull List<Integer> bands) {
        synchronized (mLock) {
            Set<List<Integer>> combinations = getCachedSupportedBandCombinations(iface);
            if (combinations == null) return false;
            // Lookup depends on the order of the bands. So sort it.
            return combinations.contains(bands.stream().sorted().collect(Collectors.toList()));
        }
    }

    /**
     * Indicate whether 2.4GHz/5GHz DBS is supported.
     *
     * @param iface The interface on the chip.
     * @return true if supported; false, otherwise;
     */
    public boolean is24g5gDbsSupported(WifiHal.WifiInterface iface) {
        return isBandCombinationSupported(iface,
                Arrays.asList(WifiScanner.WIFI_BAND_24_GHZ, WifiScanner.WIFI_BAND_5_GHZ));
    }

    /**
     * Wrapper around {@link #is24g5gDbsSupported(WifiHal.WifiInterface)} for P2P ifaces.
     */
    public boolean is24g5gDbsSupportedOnP2pIface(String ifaceName) {
        WifiP2pIface iface = mWifiP2pIfaces.get(ifaceName);
        if (iface == null) return false;
        return is24g5gDbsSupported(iface);
    }

    /**
     * Indicate whether 5GHz/6GHz DBS is supported.
     *
     * @param iface The interface on the chip.
     * @return true if supported; false, otherwise;
     */
    public boolean is5g6gDbsSupported(WifiHal.WifiInterface iface) {
        return isBandCombinationSupported(iface,
                Arrays.asList(WifiScanner.WIFI_BAND_5_GHZ, WifiScanner.WIFI_BAND_6_GHZ));
    }

    /**
     * Wrapper around {@link #is5g6gDbsSupported(WifiHal.WifiInterface)} for P2P ifaces.
     */
    public boolean is5g6gDbsSupportedOnP2pIface(String ifaceName) {
        WifiP2pIface iface = mWifiP2pIfaces.get(ifaceName);
        if (iface == null) return false;
        return is5g6gDbsSupported(iface);
    }

    /**
     * Replace the requestorWs info for the associated info.
     *
     * When a new iface is requested via
     * {@link #createIface(int, long, InterfaceDestroyedListener, Handler, WorkSource, List)}, the clients
     * pass in a worksource which includes all the apps that triggered the iface creation. However,
     * this list of apps can change during the lifetime of the iface (as new apps request the same
     * iface or existing apps release their request for the iface). This API can be invoked multiple
     * times to replace the entire requestor info for the provided iface.
     *
     * Note: This is a wholesale replacement of the requestor info. The corresponding client is
     * responsible for individual add/remove of apps in the WorkSource passed in.
     */
    public boolean replaceRequestorWs(@NonNull WifiHal.WifiInterface iface,
            @NonNull WorkSource newRequestorWs) {
        String name = getName(iface);
        int type = getType(iface);
        if (VDBG) {
            Log.d(TAG, "replaceRequestorWs: iface(name)=" + name + ", newRequestorWs="
                    + newRequestorWs);
        }

        synchronized (mLock) {
            InterfaceCacheEntry cacheEntry = mInterfaceInfoCache.get(Pair.create(name, type));
            if (cacheEntry == null) {
                Log.e(TAG, "replaceRequestorWs: no entry for iface(name)=" + name);
                return false;
            }
            cacheEntry.requestorWsHelper = mWifiInjector.makeWsHelper(newRequestorWs);
            return true;
        }
    }

    /**
     * Wrapper around {@link #replaceRequestorWs(WifiHal.WifiInterface, WorkSource)} for P2P ifaces.
     */
    public boolean replaceRequestorWsForP2pIface(String ifaceName,
            @NonNull WorkSource newRequestorWs) {
        WifiP2pIface iface = mWifiP2pIfaces.get(ifaceName);
        if (iface == null) return false;
        return replaceRequestorWs(iface, newRequestorWs);
    }

    /**
     * Wrapper around {@link #replaceRequestorWs(WifiHal.WifiInterface, WorkSource)} for NAN ifaces.
     */
    public boolean replaceRequestorWsForNanIface(@NonNull WifiNanIface iface,
            @NonNull WorkSource newRequestorWs) {
        return replaceRequestorWs(iface, newRequestorWs);
    }

    /**
     * Register a SubsystemRestartListener to listen to the subsystem restart event from HAL.
     * Use the action() to forward the event to SelfRecovery when receiving the event from HAL.
     *
     * @param listener SubsystemRestartListener listener object.
     * @param handler Handler on which to dispatch listener. Null implies the listener will be
     *                invoked synchronously from the context of the client which triggered the
     *                state change.
     */
    public void registerSubsystemRestartListener(@NonNull SubsystemRestartListener listener,
            @Nullable Handler handler) {
        if (listener == null) {
            Log.wtf(TAG, "registerSubsystemRestartListener with nulls!? listener=" + listener);
            return;
        }
        if (!mSubsystemRestartListener.add(new SubsystemRestartListenerProxy(listener, handler))) {
            Log.w(TAG, "registerSubsystemRestartListener: duplicate registration ignored");
        }
    }

    /**
     * Register a callback object for RTT life-cycle events. The callback object registration
     * indicates that an RTT controller should be created whenever possible. The callback object
     * will be called with a new RTT controller whenever it is created (or at registration time
     * if an RTT controller already exists). The callback object will also be triggered whenever
     * an existing RTT controller is destroyed (the previous copies must be discarded by the
     * recipient).
     *
     * Each listener should maintain a single callback object to register here. The callback can
     * be registered upon the listener's initialization, and re-registered on HDM status changes, if
     * {@link #isStarted} is true.
     *
     * @param callback InterfaceRttControllerLifecycleCallback object.
     * @param handler Handler on which to dispatch callback
     */
    public void registerRttControllerLifecycleCallback(
            @NonNull InterfaceRttControllerLifecycleCallback callback, @NonNull Handler handler) {
        if (VDBG) {
            Log.d(TAG, "registerRttControllerLifecycleCallback: callback=" + callback + ", handler="
                    + handler);
        }

        if (callback == null || handler == null) {
            Log.wtf(TAG, "registerRttControllerLifecycleCallback with nulls!? callback=" + callback
                    + ", handler=" + handler);
            return;
        }

        synchronized (mLock) {
            InterfaceRttControllerLifecycleCallbackProxy proxy =
                    new InterfaceRttControllerLifecycleCallbackProxy(callback, handler);
            if (!mRttControllerLifecycleCallbacks.add(proxy)) {
                Log.d(TAG,
                        "registerRttControllerLifecycleCallback: registering an existing callback="
                                + callback);
                return;
            }

            if (mWifiRttController == null) {
                mWifiRttController = createRttControllerIfPossible();
            }
            if (mWifiRttController != null) {
                proxy.onNewRttController(mWifiRttController);
            }
        }
    }

    /**
     * Return the name of the input interface or null on error.
     */
    public String getName(WifiHal.WifiInterface iface) {
        if (iface == null) {
            return "<null>";
        }
        return iface.getName();
    }

    /**
     * Called when subsystem restart
     */
    public interface SubsystemRestartListener {
        /**
         * Called for subsystem restart event from the HAL.
         * It will trigger recovery mechanism in framework.
         */
        void onSubsystemRestart();
    }

    /**
     * Called when interface is destroyed.
     */
    public interface InterfaceDestroyedListener {
        /**
         * Called for every interface on which registered when destroyed - whether
         * destroyed by releaseIface() or through chip mode change or through Wi-Fi
         * going down.
         *
         * Can be registered when the interface is requested with createXxxIface() - will
         * only be valid if the interface creation was successful - i.e. a non-null was returned.
         *
         * @param ifaceName Name of the interface that was destroyed.
         */
        void onDestroyed(@NonNull String ifaceName);
    }

    /**
     * Called on RTT controller lifecycle events. RTT controller is a singleton which will be
     * created when possible (after first lifecycle registration) and destroyed if necessary.
     *
     * Determination of availability is determined by the HAL. Creation attempts (if requested
     * by registration of interface) will be done on any mode changes.
     */
    public interface InterfaceRttControllerLifecycleCallback {
        /**
         * Called when an RTT controller was created (or for newly registered listeners - if it
         * was already available). The controller provided by this callback may be destroyed by
         * the HAL at which point the {@link #onRttControllerDestroyed()} will be called.
         *
         * Note: this callback can be triggered to replace an existing controller (instead of
         * calling the Destroyed callback in between).
         *
         * @param controller The RTT controller object.
         */
        void onNewRttController(@NonNull WifiRttController controller);

        /**
         * Called when the previously provided RTT controller is destroyed. Clients must discard
         * their copy. A new copy may be provided later by
         * {@link #onNewRttController(WifiRttController)}.
         */
        void onRttControllerDestroyed();
    }

    /**
     * Returns whether the provided @HdmIfaceTypeForCreation combo can be supported by the device.
     * Note: This only returns an answer based on the create type combination exposed by the HAL.
     * The actual iface creation/deletion rules depend on the iface priorities set in
     * {@link #allowedToDelete(int, int, int, int)}
     *
     * @param createTypeCombo SparseArray keyed in by @HdmIfaceTypeForCreation to number of ifaces
     *                         needed.
     * @return true if the device supports the provided combo, false otherwise.
     */
    public boolean canDeviceSupportCreateTypeCombo(SparseArray<Integer> createTypeCombo) {
        if (VDBG) {
            Log.d(TAG, "canDeviceSupportCreateTypeCombo: createTypeCombo=" + createTypeCombo);
        }

        synchronized (mLock) {
            int[] requestedCombo = new int[CREATE_TYPES_BY_PRIORITY.length];
            for (int createType : CREATE_TYPES_BY_PRIORITY) {
                requestedCombo[createType] = createTypeCombo.get(createType, 0);
            }
            for (StaticChipInfo staticChipInfo : getStaticChipInfos()) {
                SparseArray<List<int[][]>> expandedCreateTypeCombosPerChipModeId =
                        getExpandedCreateTypeCombosPerChipModeId(
                                staticChipInfo.getAvailableModes());
                for (int i = 0; i < expandedCreateTypeCombosPerChipModeId.size(); i++) {
                    int chipModeId = expandedCreateTypeCombosPerChipModeId.keyAt(i);
                    for (int[][] expandedCreateTypeCombo
                            : expandedCreateTypeCombosPerChipModeId.get(chipModeId)) {
                        for (int[] supportedCombo : expandedCreateTypeCombo) {
                            if (canCreateTypeComboSupportRequestedCreateTypeCombo(
                                    supportedCombo, requestedCombo)) {
                                if (VDBG) {
                                    Log.d(TAG, "Device can support createTypeCombo="
                                            + createTypeCombo);
                                }
                                return true;
                            }
                        }
                    }
                }
            }
            if (VDBG) {
                Log.d(TAG, "Device cannot support createTypeCombo=" + createTypeCombo);
            }
            return false;
        }
    }

    /**
     * Returns whether the provided Iface can be requested by specifier requestor.
     *
     * @param createIfaceType Type of iface requested.
     * @param requiredChipCapabilities The bitmask of Capabilities which are required.
     *                                 See the HAL for documentation.
     * @param requestorWs Requestor worksource. This will be used to determine priority of this
     *                    interface using rules based on the requestor app's context.
     * @return true if the device supports the provided combo, false otherwise.
     */
    public boolean isItPossibleToCreateIface(@HdmIfaceTypeForCreation int createIfaceType,
            long requiredChipCapabilities, WorkSource requestorWs) {
        if (VDBG) {
            Log.d(TAG, "isItPossibleToCreateIface: createIfaceType=" + createIfaceType
                    + ", requiredChipCapabilities=" + requiredChipCapabilities);
        }
        return getIfacesToDestroyForRequest(createIfaceType, true, requiredChipCapabilities,
                requestorWs) != null;
    }

    /**
     * Returns whether the provided Iface can be requested by specifier requestor.
     *
     * @param createIfaceType Type of iface requested.
     * @param requestorWs Requestor worksource. This will be used to determine priority of this
     *                    interface using rules based on the requestor app's context.
     * @return true if the device supports the provided combo, false otherwise.
     */
    public boolean isItPossibleToCreateIface(
            @HdmIfaceTypeForCreation int createIfaceType, WorkSource requestorWs) {
        return isItPossibleToCreateIface(
                createIfaceType, CHIP_CAPABILITY_ANY, requestorWs);
    }

    /**
     * Returns the list of interfaces that would be deleted to create the provided Iface requested
     * by the specified requestor.
     *
     * Return types imply:
     * - null: interface cannot be created
     * - empty list: interface can be crated w/o destroying any other interfaces
     * - otherwise: a list of interfaces to be destroyed
     *
     * @param createIfaceType Type of iface requested.
     * @param queryForNewInterface True: request another interface of the specified type, False: if
     *                             there's already an interface of the specified type then no need
     *                             for further operation.
     * @param requiredChipCapabilities The bitmask of Capabilities which are required.
     *                                 See the HAL for documentation.
     * @param requestorWs Requestor worksource. This will be used to determine priority of this
     *                    interface using rules based on the requestor app's context.
     * @return the list of interfaces that would have to be destroyed.
     */
    private List<WifiIfaceInfo> getIfacesToDestroyForRequest(
            @HdmIfaceTypeForCreation int createIfaceType, boolean queryForNewInterface,
            long requiredChipCapabilities, WorkSource requestorWs) {
        if (VDBG) {
            Log.d(TAG, "getIfacesToDestroyForRequest: ifaceType=" + createIfaceType
                    + ", requiredChipCapabilities=" + requiredChipCapabilities
                    + ", requestorWs=" + requestorWs);
        }

        IfaceCreationData creationData;
        synchronized (mLock) {
            if (!mWifiHal.isInitializationComplete()) {
                Log.e(TAG, "getIfacesToDestroyForRequest: Wifi Hal is not available");
                return null;
            }
            WifiChipInfo[] chipInfos = getAllChipInfo(false);
            if (chipInfos == null) {
                Log.e(TAG, "getIfacesToDestroyForRequest: no chip info found");
                stopWifi(); // major error: shutting down
                return null;
            }

            if (!validateInterfaceCacheAndRetrieveRequestorWs(chipInfos)) {
                Log.e(TAG, "getIfacesToDestroyForRequest: local cache is invalid!");
                stopWifi(); // major error: shutting down
                return null;
            }

            if (!queryForNewInterface) {
                for (WifiChipInfo chipInfo: chipInfos) {
                    if (chipInfo.ifaces[createIfaceType].length != 0) {
                        return Collections.emptyList(); // approve w/o deleting any interfaces
                    }
                }
            }

            creationData = getBestIfaceCreationProposal(chipInfos, createIfaceType,
                    requiredChipCapabilities, requestorWs);
        }

        if (creationData == null) {
            return null; // impossible to create requested interface
        }

        List<WifiIfaceInfo> ifaces = new ArrayList<>();
        boolean isModeConfigNeeded = !creationData.chipInfo.currentModeIdValid
                || creationData.chipInfo.currentModeId != creationData.chipModeId;
        if (!isModeConfigNeeded && (creationData.interfacesToBeRemovedFirst == null
                || creationData.interfacesToBeRemovedFirst.isEmpty())) {
            // can create interface w/o deleting any other interfaces
            return ifaces;
        }

        if (isModeConfigNeeded) {
            if (VDBG) {
                Log.d(TAG, "getIfacesToDestroyForRequest: mode change from - "
                        + creationData.chipInfo.currentModeId + ", to - "
                        + creationData.chipModeId);
            }
            for (WifiIfaceInfo[] ifaceInfos: creationData.chipInfo.ifaces) {
                ifaces.addAll(Arrays.asList(ifaceInfos));
            }
        } else {
            ifaces.addAll(creationData.interfacesToBeRemovedFirst);
        }

        return ifaces;
    }

    /**
     * Returns the details of what it would take to create the provided Iface requested by the
     * specified requestor. The details are the list of other interfaces which would have to be
     * destroyed.
     *
     * Return types imply:
     * - null: interface cannot be created
     * - empty list: interface can be crated w/o destroying any other interfaces
     * - otherwise: a list of interfaces to be destroyed
     *
     * @param createIfaceType Type of iface requested.
     * @param queryForNewInterface True: request another interface of the specified type, False: if
     *                             there's already an interface of the specified type then no need
     *                             for further operation.
     * @param requestorWs Requestor worksource. This will be used to determine priority of this
     *                    interface using rules based on the requestor app's context.
     * @return the list of interfaces that would have to be destroyed and their worksource. The
     * interface type is described using @HdmIfaceTypeForCreation.
     */
    public List<Pair<Integer, WorkSource>> reportImpactToCreateIface(
            @HdmIfaceTypeForCreation int createIfaceType, boolean queryForNewInterface,
            WorkSource requestorWs) {
        List<WifiIfaceInfo> ifaces = getIfacesToDestroyForRequest(createIfaceType,
                queryForNewInterface, CHIP_CAPABILITY_ANY, requestorWs);
        if (ifaces == null) {
            return null;
        }
        List<Pair<Integer, WorkSource>> impact = new ArrayList<>();
        for (WifiIfaceInfo iface : ifaces) {
            impact.add(new Pair<>(iface.createType, iface.requestorWsHelper.getWorkSource()));
        }
        return impact;
    }

    /**
     * Helper method to return true if the given iface request will result in deleting an iface
     * requested by a privileged worksource.
     */
    public boolean creatingIfaceWillDeletePrivilegedIface(
            @HdmIfaceTypeForCreation int ifaceType, WorkSource requestorWs) {
        List<WifiIfaceInfo> ifaces = getIfacesToDestroyForRequest(ifaceType, true,
                CHIP_CAPABILITY_ANY, requestorWs);
        if (ifaces == null) {
            return false;
        }
        for (WifiIfaceInfo iface : ifaces) {
            if (iface.requestorWsHelper.getRequestorWsPriority()
                    == WorkSourceHelper.PRIORITY_PRIVILEGED && !isDisconnectedP2p(iface)) {
                return true;
            }
        }
        return false;
    }

    // internal state

    /* This "PRIORITY" is not for deciding interface elimination (that is controlled by
     * allowedToDeleteIfaceTypeForRequestedType. This priority is used for:
     * - Comparing 2 configuration options
     * - Order of dispatch of available for request listeners
     */
    private static final int[] IFACE_TYPES_BY_PRIORITY =
            {WifiChip.IFACE_TYPE_AP, WifiChip.IFACE_TYPE_STA, WifiChip.IFACE_TYPE_P2P,
                    WifiChip.IFACE_TYPE_NAN};
    private static final int[] CREATE_TYPES_BY_PRIORITY =
            {HDM_CREATE_IFACE_AP, HDM_CREATE_IFACE_AP_BRIDGE, HDM_CREATE_IFACE_STA,
                    HDM_CREATE_IFACE_P2P, HDM_CREATE_IFACE_NAN};

    private final Object mLock = new Object();

    private WifiRttController mWifiRttController;
    private HashMap<String, WifiP2pIface> mWifiP2pIfaces = new HashMap<>();
    private final WifiHal.Callback mWifiEventCallback = new WifiEventCallback();
    private final Set<ManagerStatusListenerProxy> mManagerStatusListeners = new HashSet<>();
    private final Set<InterfaceRttControllerLifecycleCallbackProxy>
            mRttControllerLifecycleCallbacks = new HashSet<>();
    private final Set<SubsystemRestartListenerProxy> mSubsystemRestartListener = new HashSet<>();

    /*
     * This is the only place where we cache HAL information in this manager. Necessary since
     * we need to keep a list of registered destroyed listeners. Will be validated regularly
     * in getAllChipInfoAndValidateCache().
     */
    private final Map<Pair<String, Integer>, InterfaceCacheEntry> mInterfaceInfoCache =
            new HashMap<>();

    private class InterfaceCacheEntry {
        public WifiChip chip;
        public int chipId;
        public String name;
        public int type;
        public Set<InterfaceDestroyedListenerProxy> destroyedListeners = new HashSet<>();
        public long creationTime;
        public WorkSourceHelper requestorWsHelper;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{name=").append(name).append(", type=").append(type)
                    .append(", destroyedListeners.size()=").append(destroyedListeners.size())
                    .append(", RequestorWs=").append(requestorWsHelper)
                    .append(", creationTime=").append(creationTime).append("}");
            return sb.toString();
        }
    }

    private class WifiIfaceInfo {
        public String name;
        public WifiHal.WifiInterface iface;
        public @HdmIfaceTypeForCreation int createType;
        public WorkSourceHelper requestorWsHelper;

        @Override
        public String toString() {
            return "{name=" + name + ", iface=" + iface + ", requestorWs=" + requestorWsHelper
                    + " }";
        }
    }

    private class WifiChipInfo {
        public WifiChip chip;
        public int chipId = -1;
        public ArrayList<WifiChip.ChipMode> availableModes;
        public boolean currentModeIdValid = false;
        public int currentModeId = -1;
        // Arrays of WifiIfaceInfo indexed by @HdmIfaceTypeForCreation, in order of creation as
        // returned by WifiChip.getXxxIfaceNames.
        public WifiIfaceInfo[][] ifaces = new WifiIfaceInfo[CREATE_TYPES_BY_PRIORITY.length][];
        public long chipCapabilities;
        public List<WifiChip.WifiRadioCombination> radioCombinations = null;
        // A data structure for the faster band combination lookup.
        public Set<List<Integer>> bandCombinations = null;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{chipId=").append(chipId).append(", availableModes=").append(availableModes)
                    .append(", currentModeIdValid=").append(currentModeIdValid)
                    .append(", currentModeId=").append(currentModeId)
                    .append(", chipCapabilities=").append(chipCapabilities)
                    .append(", radioCombinations=").append(radioCombinations)
                    .append(", bandCombinations=").append(bandCombinations);
            for (int type: IFACE_TYPES_BY_PRIORITY) {
                sb.append(", ifaces[" + type + "].length=").append(ifaces[type].length);
            }
            sb.append("}");
            return sb.toString();
        }
    }

    protected boolean isWaitForDestroyedListenersMockable() {
        return mWaitForDestroyedListeners;
    }

    // internal implementation

    private void initializeInternal() {
        mWifiHal.initialize(mIWifiDeathRecipient);
    }

    private static String getIfaceTypeToString(@HdmIfaceTypeForCreation int type) {
        switch (type) {
            case HDM_CREATE_IFACE_STA:
                return "STA";
            case HDM_CREATE_IFACE_AP:
                return "AP";
            case HDM_CREATE_IFACE_AP_BRIDGE:
                return "AP_BRIDGE";
            case HDM_CREATE_IFACE_P2P:
                return "P2P";
            case HDM_CREATE_IFACE_NAN:
                return "NAN";
            default:
                return "UNKNOWN " + type;
        }
    }

    private void teardownInternal() {
        managerStatusListenerDispatch();
        dispatchAllDestroyedListeners();

        mWifiRttController = null;
        dispatchRttControllerLifecycleOnDestroyed();
        mRttControllerLifecycleCallbacks.clear();
        mWifiP2pIfaces.clear();
    }

    private class WifiDeathRecipient implements WifiHal.DeathRecipient {
        @Override
        public void onDeath() {
            mEventHandler.post(() -> {
                synchronized (mLock) { // prevents race condition with surrounding method
                    teardownInternal();
                }
            });
        }
    }

    /**
     * Register the wifi HAL event callback. Reset the Wifi HAL interface when it fails.
     * @return true if success.
     */
    private boolean registerWifiHalEventCallback() {
        return mWifiHal.registerEventCallback(mWifiEventCallback);
    }

    @Nullable
    private WifiChipInfo[] mCachedWifiChipInfos = null;

    /**
     * Get current information about all the chips in the system: modes, current mode (if any), and
     * any existing interfaces.
     *
     * <p>Intended to be called for any external iface support related queries. This information is
     * cached to reduce performance overhead (unlike {@link #getAllChipInfo(boolean)}).
     */
    private WifiChipInfo[] getAllChipInfoCached() {
        if (mCachedWifiChipInfos == null) {
            mCachedWifiChipInfos = getAllChipInfo(false);
        }
        return mCachedWifiChipInfos;
    }

    /**
     * Get current information about all the chips in the system: modes, current mode (if any), and
     * any existing interfaces.
     *
     * <p>Intended to be called whenever we need to configure the chips - information is NOT cached
     * (to reduce the likelihood that we get out-of-sync).
     */
    private WifiChipInfo[] getAllChipInfo(boolean forceReadChipInfoFromDriver) {
        if (VDBG) Log.d(TAG, "getAllChipInfo");

        synchronized (mLock) {
            if (!isWifiStarted()) {
                return null;
            }

            // get all chip IDs
            List<Integer> chipIds = mWifiHal.getChipIds();
            if (chipIds == null) {
                return null;
            }

            if (VDBG) Log.d(TAG, "getChipIds=" + Arrays.toString(chipIds.toArray()));
            if (chipIds.size() == 0) {
                Log.e(TAG, "Should have at least 1 chip!");
                return null;
            }

            SparseArray<StaticChipInfo> staticChipInfoPerId = new SparseArray<>();
            for (StaticChipInfo staticChipInfo : getStaticChipInfos()) {
                staticChipInfoPerId.put(staticChipInfo.getChipId(), staticChipInfo);
            }

            int chipInfoIndex = 0;
            WifiChipInfo[] chipsInfo = new WifiChipInfo[chipIds.size()];

            for (Integer chipId : chipIds) {
                WifiChip chip = mWifiHal.getChip(chipId);
                if (chip == null) {
                    return null;
                }

                WifiChip.Response<Integer> currentMode = chip.getMode();
                if (currentMode.getStatusCode() != WifiHal.WIFI_STATUS_SUCCESS
                        && currentMode.getStatusCode() != WifiHal.WIFI_STATUS_ERROR_NOT_AVAILABLE) {
                    return null;
                }

                long chipCapabilities = getChipCapabilities(chip);

                List<String> ifaceNames = chip.getStaIfaceNames();
                if (ifaceNames == null) {
                    return null;
                }

                int ifaceIndex = 0;
                WifiIfaceInfo[] staIfaces = new WifiIfaceInfo[ifaceNames.size()];
                for (String ifaceName: ifaceNames) {
                    WifiHal.WifiInterface iface = chip.getStaIface(ifaceName);
                    if (iface == null) {
                        return null;
                    }
                    WifiIfaceInfo ifaceInfo = new WifiIfaceInfo();
                    ifaceInfo.name = ifaceName;
                    ifaceInfo.iface = iface;
                    ifaceInfo.createType = HDM_CREATE_IFACE_STA;
                    staIfaces[ifaceIndex++] = ifaceInfo;
                }

                ifaceIndex = 0;
                ifaceNames = chip.getApIfaceNames();
                if (ifaceNames == null) {
                    return null;
                }

                WifiIfaceInfo[] apIfaces = new WifiIfaceInfo[ifaceNames.size()];
                for (String ifaceName : ifaceNames) {
                    WifiHal.WifiInterface iface = chip.getApIface(ifaceName);
                    if (iface == null) {
                        return null;
                    }
                    WifiIfaceInfo ifaceInfo = new WifiIfaceInfo();
                    ifaceInfo.name = ifaceName;
                    ifaceInfo.iface = iface;
                    ifaceInfo.createType = HDM_CREATE_IFACE_AP;
                    apIfaces[ifaceIndex++] = ifaceInfo;
                }

                int numBridgedAps = 0;
                for (WifiIfaceInfo apIfaceInfo : apIfaces) {
                    List<String> bridgedInstances = ((WifiApIface) apIfaceInfo.iface)
                            .getBridgedInstances();
                    // Only count bridged APs with more than 1 instance as a bridged
                    // AP; 1 instance bridged APs will be counted as single AP.
                    if (bridgedInstances != null && bridgedInstances.size() > 1) {
                        apIfaceInfo.createType = HDM_CREATE_IFACE_AP_BRIDGE;
                        numBridgedAps++;
                    }
                }

                WifiIfaceInfo[] singleApIfaces = new WifiIfaceInfo[apIfaces.length - numBridgedAps];
                WifiIfaceInfo[] bridgedApIfaces = new WifiIfaceInfo[numBridgedAps];
                int singleApIndex = 0;
                int bridgedApIndex = 0;
                for (WifiIfaceInfo apIfaceInfo : apIfaces) {
                    if (apIfaceInfo.createType == HDM_CREATE_IFACE_AP_BRIDGE) {
                        bridgedApIfaces[bridgedApIndex++] = apIfaceInfo;
                    } else {
                        singleApIfaces[singleApIndex++] = apIfaceInfo;
                    }
                }

                ifaceIndex = 0;
                ifaceNames = chip.getP2pIfaceNames();
                if (ifaceNames == null) {
                    return null;
                }

                WifiIfaceInfo[] p2pIfaces = new WifiIfaceInfo[ifaceNames.size()];
                for (String ifaceName : ifaceNames) {
                    WifiHal.WifiInterface iface = chip.getP2pIface(ifaceName);
                    if (iface == null) {
                        return null;
                    }
                    WifiIfaceInfo ifaceInfo = new WifiIfaceInfo();
                    ifaceInfo.name = ifaceName;
                    ifaceInfo.iface = iface;
                    ifaceInfo.createType = HDM_CREATE_IFACE_P2P;
                    p2pIfaces[ifaceIndex++] = ifaceInfo;
                }

                ifaceIndex = 0;
                ifaceNames = chip.getNanIfaceNames();
                if (ifaceNames == null) {
                    return null;
                }

                WifiIfaceInfo[] nanIfaces = new WifiIfaceInfo[ifaceNames.size()];
                for (String ifaceName : ifaceNames) {
                    WifiHal.WifiInterface iface = chip.getNanIface(ifaceName);
                    if (iface == null) {
                        return null;
                    }
                    WifiIfaceInfo ifaceInfo = new WifiIfaceInfo();
                    ifaceInfo.name = ifaceName;
                    ifaceInfo.iface = iface;
                    ifaceInfo.createType = HDM_CREATE_IFACE_NAN;
                    nanIfaces[ifaceIndex++] = ifaceInfo;
                }

                WifiChipInfo chipInfo = new WifiChipInfo();
                chipsInfo[chipInfoIndex++] = chipInfo;

                chipInfo.chip = chip;
                chipInfo.chipId = chipId;
                StaticChipInfo staticChipInfo = staticChipInfoPerId.get(chipId);
                if (forceReadChipInfoFromDriver || staticChipInfo == null) {
                    List<WifiChip.ChipMode> chipModes = chip.getAvailableModes();
                    if (chipModes == null) {
                        return null;
                    }
                    chipInfo.availableModes = new ArrayList<>(chipModes);
                } else {
                    chipInfo.availableModes = staticChipInfo.getAvailableModes();
                }
                chipInfo.currentModeIdValid =
                        currentMode.getStatusCode() == WifiHal.WIFI_STATUS_SUCCESS;
                chipInfo.currentModeId = currentMode.getValue();
                chipInfo.chipCapabilities = chipCapabilities;
                chipInfo.ifaces[HDM_CREATE_IFACE_STA] = staIfaces;
                chipInfo.ifaces[HDM_CREATE_IFACE_AP] = singleApIfaces;
                chipInfo.ifaces[HDM_CREATE_IFACE_AP_BRIDGE] = bridgedApIfaces;
                chipInfo.ifaces[HDM_CREATE_IFACE_P2P] = p2pIfaces;
                chipInfo.ifaces[HDM_CREATE_IFACE_NAN] = nanIfaces;
            }
            return chipsInfo;
        }
    }

    @Nullable
    private StaticChipInfo[] mCachedStaticChipInfos = null;

    @NonNull
    private StaticChipInfo[] getStaticChipInfos() {
        if (mCachedStaticChipInfos == null) {
            mCachedStaticChipInfos = loadStaticChipInfoFromStore();
        }
        return mCachedStaticChipInfos;
    }

    private void saveStaticChipInfoToStore(StaticChipInfo[] staticChipInfos) {
        try {
            JSONArray staticChipInfosJson = new JSONArray();
            for (StaticChipInfo staticChipInfo : staticChipInfos) {
                staticChipInfosJson.put(staticChipInfoToJson(staticChipInfo));
            }
            mWifiInjector.getSettingsConfigStore().put(WIFI_STATIC_CHIP_INFO,
                    staticChipInfosJson.toString());
        } catch (JSONException e) {
            Log.e(TAG, "JSONException while converting StaticChipInfo to JSON: " + e);
        }
    }

    private StaticChipInfo[] loadStaticChipInfoFromStore() {
        StaticChipInfo[] staticChipInfos = new StaticChipInfo[0];
        String configString = mWifiInjector.getSettingsConfigStore().get(WIFI_STATIC_CHIP_INFO);
        if (TextUtils.isEmpty(configString)) {
            return staticChipInfos;
        }
        try {
            JSONArray staticChipInfosJson = new JSONArray(
                    mWifiInjector.getSettingsConfigStore().get(WIFI_STATIC_CHIP_INFO));
            staticChipInfos = new StaticChipInfo[staticChipInfosJson.length()];
            for (int i = 0; i < staticChipInfosJson.length(); i++) {
                staticChipInfos[i] = jsonToStaticChipInfo(staticChipInfosJson.getJSONObject(i));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load static chip info from store: " + e);
        }
        return staticChipInfos;
    }

    @NonNull
    private StaticChipInfo[] convertWifiChipInfoToStaticChipInfos(
            @NonNull WifiChipInfo[] chipInfos) {
        StaticChipInfo[] staticChipInfos = new StaticChipInfo[chipInfos.length];
        for (int i = 0; i < chipInfos.length; i++) {
            WifiChipInfo chipInfo = chipInfos[i];
            staticChipInfos[i] = new StaticChipInfo(
                    chipInfo.chipId,
                    chipInfo.chipCapabilities,
                    chipInfo.availableModes);
        }
        return staticChipInfos;
    }

    /**
     * Checks the local state of this object (the cached state) against the input 'chipInfos'
     * state (which is a live representation of the Wi-Fi firmware status - read through the HAL).
     * Returns 'true' if there are no discrepancies - 'false' otherwise.
     *
     * A discrepancy is if any local state contains references to a chip or interface which are not
     * found on the information read from the chip.
     *
     * Also, fills in the |requestorWs| corresponding to each active iface in |WifiChipInfo|.
     */
    private boolean validateInterfaceCacheAndRetrieveRequestorWs(WifiChipInfo[] chipInfos) {
        if (VDBG) Log.d(TAG, "validateInterfaceCache");

        synchronized (mLock) {
            for (InterfaceCacheEntry entry: mInterfaceInfoCache.values()) {
                // search for chip
                WifiChipInfo matchingChipInfo = null;
                for (WifiChipInfo ci: chipInfos) {
                    if (ci.chipId == entry.chipId) {
                        matchingChipInfo = ci;
                        break;
                    }
                }
                if (matchingChipInfo == null) {
                    Log.e(TAG, "validateInterfaceCache: no chip found for " + entry);
                    return false;
                }

                // search for matching interface cache entry by iterating through the corresponding
                // HdmIfaceTypeForCreation values.
                boolean matchFound = false;
                for (int createType : CREATE_TYPES_BY_PRIORITY) {
                    if (HAL_IFACE_MAP.get(createType) != entry.type) {
                        continue;
                    }
                    WifiIfaceInfo[] ifaceInfoList = matchingChipInfo.ifaces[createType];
                    if (ifaceInfoList == null) {
                        Log.e(TAG, "validateInterfaceCache: invalid type on entry " + entry);
                        return false;
                    }
                    for (WifiIfaceInfo ifaceInfo : ifaceInfoList) {
                        if (ifaceInfo.name.equals(entry.name)) {
                            ifaceInfo.requestorWsHelper = entry.requestorWsHelper;
                            matchFound = true;
                            break;
                        }
                    }
                }
                if (!matchFound) {
                    Log.e(TAG, "validateInterfaceCache: no interface found for " + entry);
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isWifiStarted() {
        if (VDBG) Log.d(TAG, "isWifiStart");
        synchronized (mLock) {
            return mWifiHal.isStarted();
        }
    }

    private boolean startWifi() {
        if (VDBG) Log.d(TAG, "startWifi");
        initializeInternal();
        synchronized (mLock) {
            int triedCount = 0;
            while (triedCount <= START_HAL_RETRY_TIMES) {
                int status = mWifiHal.start();
                if (status == WifiHal.WIFI_STATUS_SUCCESS) {
                    managerStatusListenerDispatch();
                    if (triedCount != 0) {
                        Log.d(TAG, "start IWifi succeeded after trying "
                                 + triedCount + " times");
                    }
                    WifiChipInfo[] wifiChipInfos = getAllChipInfo(false);
                    if (wifiChipInfos == null) {
                        Log.e(TAG, "Started wifi but could not get current chip info.");
                    }
                    return true;
                } else if (status == WifiHal.WIFI_STATUS_ERROR_NOT_AVAILABLE) {
                    // Should retry. Hal might still be stopping. the registered event
                    // callback will not be cleared.
                    Log.e(TAG, "Cannot start wifi because unavailable. Retrying...");
                    try {
                        Thread.sleep(START_HAL_RETRY_INTERVAL_MS);
                    } catch (InterruptedException ignore) {
                        // no-op
                    }
                    triedCount++;
                } else {
                    // Should not retry on other failures.
                    // Will be handled in the onFailure event.
                    Log.e(TAG, "Cannot start IWifi. Status: " + status);
                    return false;
                }
            }
            Log.e(TAG, "Cannot start IWifi after trying " + triedCount + " times");
            return false;
        }
    }

    private void stopWifi() {
        if (VDBG) Log.d(TAG, "stopWifi");
        synchronized (mLock) {
            if (!mWifiHal.isInitializationComplete()) {
                Log.w(TAG, "stopWifi was called, but Wifi Hal is not initialized");
                return;
            }
            if (!mWifiHal.stop()) {
                Log.e(TAG, "Cannot stop IWifi");
            }
            // even on failure since WTF??
            teardownInternal();
        }
    }

    private class WifiEventCallback implements WifiHal.Callback {
        @Override
        public void onStart() {
            mEventHandler.post(() -> {
                if (VDBG) Log.d(TAG, "IWifiEventCallback.onStart");
                // NOP: only happens in reaction to my calls - will handle directly
            });
        }

        @Override
        public void onStop() {
            mEventHandler.post(() -> {
                if (VDBG) Log.d(TAG, "IWifiEventCallback.onStop");
                // NOP: only happens in reaction to my calls - will handle directly
            });
        }

        @Override
        public void onFailure(int status) {
            mEventHandler.post(() -> {
                Log.e(TAG, "IWifiEventCallback.onFailure. Status: " + status);
                synchronized (mLock) {
                    teardownInternal();
                }
            });
        }

        @Override
        public void onSubsystemRestart(int status) {
            Log.i(TAG, "onSubsystemRestart");
            mEventHandler.post(() -> {
                Log.i(TAG, "IWifiEventCallback.onSubsystemRestart. Status: " + status);
                synchronized (mLock) {
                    Log.i(TAG, "Attempting to invoke mSubsystemRestartListener");
                    for (SubsystemRestartListenerProxy cb : mSubsystemRestartListener) {
                        Log.i(TAG, "Invoking mSubsystemRestartListener");
                        cb.action();
                    }
                }
            });
        }
    }

    private void managerStatusListenerDispatch() {
        synchronized (mLock) {
            for (ManagerStatusListenerProxy cb : mManagerStatusListeners) {
                cb.trigger(false);
            }
        }
    }

    private class ManagerStatusListenerProxy  extends
            ListenerProxy<ManagerStatusListener> {
        ManagerStatusListenerProxy(ManagerStatusListener statusListener, Handler handler) {
            super(statusListener, handler, "ManagerStatusListenerProxy");
        }

        @Override
        protected void action() {
            mListener.onStatusChanged();
        }
    }

    private Set<Integer> getSupportedIfaceTypesInternal(WifiChip chip) {
        Set<Integer> results = new HashSet<>();

        WifiChipInfo[] chipInfos = getAllChipInfoCached();
        if (chipInfos == null) {
            Log.e(TAG, "getSupportedIfaceTypesInternal: no chip info found");
            return results;
        }

        int chipIdIfProvided = 0; // NOT using 0 as a magic value
        if (chip != null) {
            chipIdIfProvided = chip.getId();
            if (chipIdIfProvided == -1) {
                return results;
            }
        }

        for (WifiChipInfo wci: chipInfos) {
            if (chip != null && wci.chipId != chipIdIfProvided) {
                continue;
            }
            // Map the IfaceConcurrencyTypes to the corresponding IfaceType.
            for (WifiChip.ChipMode cm : wci.availableModes) {
                for (WifiChip.ChipConcurrencyCombination cic : cm.availableCombinations) {
                    for (WifiChip.ChipConcurrencyCombinationLimit cicl : cic.limits) {
                        for (int concurrencyType: cicl.types) {
                            results.add(HAL_IFACE_MAP.get(
                                    CONCURRENCY_TYPE_TO_CREATE_TYPE_MAP.get(concurrencyType)));
                        }
                    }
                }
            }
        }
        return results;
    }

    private WifiHal.WifiInterface createIface(@HdmIfaceTypeForCreation int createIfaceType,
            long requiredChipCapabilities, InterfaceDestroyedListener destroyedListener,
            Handler handler, WorkSource requestorWs, @Nullable List<OuiKeyedData> vendorData) {
        if (mDbg) {
            Log.d(TAG, "createIface: createIfaceType=" + createIfaceType
                    + ", requiredChipCapabilities=" + requiredChipCapabilities
                    + ", requestorWs=" + requestorWs);
        }
        if (destroyedListener != null && handler == null) {
            Log.wtf(TAG, "createIface: createIfaceType=" + createIfaceType
                    + "with NonNull destroyedListener but Null handler");
            return null;
        }

        synchronized (mLock) {
            WifiChipInfo[] chipInfos = getAllChipInfo(false);
            if (chipInfos == null) {
                Log.e(TAG, "createIface: no chip info found");
                stopWifi(); // major error: shutting down
                // Event callback has been invalidated in HAL stop, register it again.
                registerWifiHalEventCallback();
                return null;
            }

            if (!validateInterfaceCacheAndRetrieveRequestorWs(chipInfos)) {
                Log.e(TAG, "createIface: local cache is invalid!");
                stopWifi(); // major error: shutting down
                // Event callback has been invalidated in HAL stop, register it again.
                registerWifiHalEventCallback();
                return null;
            }

            return createIfaceIfPossible(
                    chipInfos, createIfaceType, requiredChipCapabilities,
                    destroyedListener, handler, requestorWs, vendorData);
        }
    }

    private static boolean isChipCapabilitiesSupported(long currentChipCapabilities,
            long requiredChipCapabilities) {
        if (requiredChipCapabilities == CHIP_CAPABILITY_ANY) return true;

        if (CHIP_CAPABILITY_UNINITIALIZED == currentChipCapabilities) return true;

        return (currentChipCapabilities & requiredChipCapabilities)
                == requiredChipCapabilities;
    }

    private IfaceCreationData getBestIfaceCreationProposal(
            WifiChipInfo[] chipInfos, @HdmIfaceTypeForCreation int createIfaceType,
            long requiredChipCapabilities, WorkSource requestorWs) {
        int targetHalIfaceType = HAL_IFACE_MAP.get(createIfaceType);
        if (VDBG) {
            Log.d(TAG, "getBestIfaceCreationProposal: chipInfos=" + Arrays.deepToString(chipInfos)
                    + ", createIfaceType=" + createIfaceType
                    + ", targetHalIfaceType=" + targetHalIfaceType
                    + ", requiredChipCapabilities=" + requiredChipCapabilities
                    + ", requestorWs=" + requestorWs);
        }
        synchronized (mLock) {
            IfaceCreationData bestIfaceCreationProposal = null;
            for (WifiChipInfo chipInfo : chipInfos) {
                if (!isChipCapabilitiesSupported(
                        chipInfo.chipCapabilities, requiredChipCapabilities)) {
                    continue;
                }

                SparseArray<List<int[][]>> expandedCreateTypeCombosPerChipModeId =
                        getExpandedCreateTypeCombosPerChipModeId(chipInfo.availableModes);
                for (int i = 0; i < expandedCreateTypeCombosPerChipModeId.size(); i++) {
                    int chipModeId = expandedCreateTypeCombosPerChipModeId.keyAt(i);
                    for (int[][] expandedCreateTypeCombo :
                            expandedCreateTypeCombosPerChipModeId.get(chipModeId)) {
                        for (int[] createTypeCombo : expandedCreateTypeCombo) {
                            IfaceCreationData currentProposal = canCreateTypeComboSupportRequest(
                                    chipInfo, chipModeId, createTypeCombo, createIfaceType,
                                    requestorWs);
                            if (compareIfaceCreationData(currentProposal,
                                    bestIfaceCreationProposal)) {
                                if (VDBG) Log.d(TAG, "new proposal accepted");
                                bestIfaceCreationProposal = currentProposal;
                            }
                        }
                    }
                }
            }
            if (bestIfaceCreationProposal == null) {
                List<String> createIfaceInfoString = new ArrayList<String>();
                for (WifiChipInfo chipInfo : chipInfos) {
                    for (int existingCreateType : CREATE_TYPES_BY_PRIORITY) {
                        WifiIfaceInfo[] createTypeIfaces = chipInfo.ifaces[existingCreateType];
                        for (WifiIfaceInfo intfInfo : createTypeIfaces) {
                            if (intfInfo != null) {
                                createIfaceInfoString.add(
                                        "name="
                                                + intfInfo.name
                                                + " type="
                                                + getIfaceTypeToString(intfInfo.createType));
                            }
                        }
                    }
                }
                Log.i(
                        TAG,
                        "bestIfaceCreationProposal is null,"
                                + " requestIface="
                                + getIfaceTypeToString(createIfaceType)
                                + ", existingIface="
                                + createIfaceInfoString);
            }
            return bestIfaceCreationProposal;
        }
    }

    /**
     * Returns a SparseArray indexed by ChipModeId, containing Lists of expanded create type combos
     * supported by that id.
     */
    private SparseArray<List<int[][]>> getExpandedCreateTypeCombosPerChipModeId(
            ArrayList<WifiChip.ChipMode> chipModes) {
        SparseArray<List<int[][]>> combosPerChipModeId = new SparseArray<>();
        for (WifiChip.ChipMode chipMode : chipModes) {
            List<int[][]> expandedCreateTypeCombos = new ArrayList<>();
            for (WifiChip.ChipConcurrencyCombination chipConcurrencyCombo
                    : chipMode.availableCombinations) {
                expandedCreateTypeCombos.add(expandCreateTypeCombo(chipConcurrencyCombo));
            }
            combosPerChipModeId.put(chipMode.id, expandedCreateTypeCombos);
        }
        return combosPerChipModeId;
    }

    private WifiHal.WifiInterface createIfaceIfPossible(
            WifiChipInfo[] chipInfos, @HdmIfaceTypeForCreation int createIfaceType,
            long requiredChipCapabilities, InterfaceDestroyedListener destroyedListener,
            Handler handler, WorkSource requestorWs, @Nullable List<OuiKeyedData> vendorData) {
        int targetHalIfaceType = HAL_IFACE_MAP.get(createIfaceType);
        if (VDBG) {
            Log.d(TAG, "createIfaceIfPossible: chipInfos=" + Arrays.deepToString(chipInfos)
                    + ", createIfaceType=" + createIfaceType
                    + ", targetHalIfaceType=" + targetHalIfaceType
                    + ", requiredChipCapabilities=" + requiredChipCapabilities
                    + ", requestorWs=" + requestorWs);
        }
        if (vendorData != null && !vendorData.isEmpty()) {
            Log.d(TAG, "Request includes vendor data. ifaceType=" + createIfaceType
                    + ", vendorDataSize=" + vendorData.size());
        }
        synchronized (mLock) {
            IfaceCreationData bestIfaceCreationProposal = getBestIfaceCreationProposal(chipInfos,
                    createIfaceType, requiredChipCapabilities, requestorWs);

            if (bestIfaceCreationProposal != null) {
                WifiHal.WifiInterface iface = executeChipReconfiguration(bestIfaceCreationProposal,
                        createIfaceType, vendorData);
                if (iface == null) {
                    // If the chip reconfiguration failed, we'll need to clean up internal state.
                    Log.e(TAG, "Teardown Wifi internal state");
                    mWifiHal.invalidate();
                    teardownInternal();
                } else {
                    InterfaceCacheEntry cacheEntry = new InterfaceCacheEntry();

                    cacheEntry.chip = bestIfaceCreationProposal.chipInfo.chip;
                    cacheEntry.chipId = bestIfaceCreationProposal.chipInfo.chipId;
                    cacheEntry.name = getName(iface);
                    cacheEntry.type = targetHalIfaceType;
                    cacheEntry.requestorWsHelper = mWifiInjector.makeWsHelper(requestorWs);
                    if (destroyedListener != null) {
                        cacheEntry.destroyedListeners.add(
                                new InterfaceDestroyedListenerProxy(
                                        cacheEntry.name, destroyedListener, handler));
                    }
                    cacheEntry.creationTime = mClock.getElapsedSinceBootMillis();

                    if (mDbg) Log.d(TAG, "createIfaceIfPossible: added cacheEntry=" + cacheEntry);
                    mInterfaceInfoCache.put(
                            Pair.create(cacheEntry.name, cacheEntry.type), cacheEntry);
                    return iface;
                }
            }
        }

        Log.e(TAG, "createIfaceIfPossible: Failed to create iface for ifaceType=" + createIfaceType
                + ", requestorWs=" + requestorWs);
        return null;
    }

    /**
     * Expands (or provides an alternative representation) of the ChipConcurrencyCombination as all
     * possible combinations of @HdmIfaceTypeForCreation.
     *
     * Returns [# of combinations][4 (@HdmIfaceTypeForCreation)]
     *
     * Note: there could be duplicates - allow (inefficient but ...).
     * TODO: optimize by using a Set as opposed to a []: will remove duplicates. Will need to
     * provide correct hashes.
     */
    private int[][] expandCreateTypeCombo(
            WifiChip.ChipConcurrencyCombination chipConcurrencyCombo) {
        int numOfCombos = 1;
        for (WifiChip.ChipConcurrencyCombinationLimit limit : chipConcurrencyCombo.limits) {
            for (int i = 0; i < limit.maxIfaces; ++i) {
                numOfCombos *= limit.types.size();
            }
        }

        int[][] expandedCreateTypeCombo =
                new int[numOfCombos][CREATE_TYPES_BY_PRIORITY.length];

        int span = numOfCombos; // span of an individual type (or sub-tree size)
        for (WifiChip.ChipConcurrencyCombinationLimit limit : chipConcurrencyCombo.limits) {
            for (int i = 0; i < limit.maxIfaces; ++i) {
                span /= limit.types.size();
                for (int k = 0; k < numOfCombos; ++k) {
                    expandedCreateTypeCombo[k][CONCURRENCY_TYPE_TO_CREATE_TYPE_MAP.get(
                            limit.types.get((k / span) % limit.types.size()))]++;
                }
            }
        }
        if (VDBG) {
            Log.d(TAG, "ChipConcurrencyCombo " + chipConcurrencyCombo
                    + " expands to HdmIfaceTypeForCreation combo "
                    + Arrays.deepToString(expandedCreateTypeCombo));
        }
        return expandedCreateTypeCombo;
    }

    private class IfaceCreationData {
        public WifiChipInfo chipInfo;
        public int chipModeId;
        public @NonNull List<WifiIfaceInfo> interfacesToBeRemovedFirst = new ArrayList<>();
        public @NonNull List<WifiIfaceInfo> interfacesToBeDowngraded = new ArrayList<>();

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{chipInfo=").append(chipInfo).append(", chipModeId=").append(chipModeId)
                    .append(", interfacesToBeRemovedFirst=").append(interfacesToBeRemovedFirst)
                    .append(", interfacesToBeDowngraded=").append(interfacesToBeDowngraded)
                    .append(")");
            return sb.toString();
        }
    }

    /**
     * Checks whether the input chip-create-type-combo can support the requested create type:
     * if not then returns null, if yes then returns information containing the list of interfaces
     * which would have to be removed first before an interface of the given create type can be
     * created.
     *
     * Note: the list of interfaces to be removed is EMPTY if a chip mode change is required - in
     * that case ALL the interfaces on the current chip have to be removed first.
     *
     * Response determined based on:
     * - Mode configuration: i.e. could the mode support the interface type in principle
     */
    private IfaceCreationData canCreateTypeComboSupportRequest(
            WifiChipInfo chipInfo,
            int chipModeId,
            int[] chipCreateTypeCombo,
            @HdmIfaceTypeForCreation int requestedCreateType,
            WorkSource requestorWs) {
        if (VDBG) {
            Log.d(TAG, "canCreateTypeComboSupportRequest: chipInfo=" + chipInfo
                    + ", chipModeId=" + chipModeId
                    + ", chipCreateTypeCombo=" + Arrays.toString(chipCreateTypeCombo)
                    + ", requestedCreateType=" + requestedCreateType
                    + ", requestorWs=" + requestorWs);
        }

        // short-circuit: does the combo even support the requested type?
        if (chipCreateTypeCombo[requestedCreateType] == 0) {
            if (VDBG) Log.d(TAG, "Requested create type not supported by combo");
            return null;
        }

        IfaceCreationData ifaceCreationData = new IfaceCreationData();
        ifaceCreationData.chipInfo = chipInfo;
        ifaceCreationData.chipModeId = chipModeId;

        boolean isChipModeChangeProposed =
                chipInfo.currentModeIdValid && chipInfo.currentModeId != chipModeId;

        // short-circuit: can't change chip-mode if an existing interface on this chip has a higher
        // priority than the requested interface
        if (isChipModeChangeProposed) {
            for (int existingCreateType : CREATE_TYPES_BY_PRIORITY) {
                WifiIfaceInfo[] createTypeIfaces = chipInfo.ifaces[existingCreateType];
                if (selectInterfacesToDelete(createTypeIfaces.length, requestedCreateType,
                        requestorWs, existingCreateType, createTypeIfaces) == null) {
                    if (VDBG) {
                        Log.d(TAG, "Couldn't delete existing create type "
                                + existingCreateType + " interfaces for requested type");
                    }
                    return null;
                }
            }

            // but if priority allows the mode change then we're good to go
            return ifaceCreationData;
        }

        // possibly supported
        for (int existingCreateType : CREATE_TYPES_BY_PRIORITY) {
            WifiIfaceInfo[] createTypeIfaces = chipInfo.ifaces[existingCreateType];
            int numExcessIfaces = createTypeIfaces.length - chipCreateTypeCombo[existingCreateType];
            // need to count the requested create type as well
            if (existingCreateType == requestedCreateType) {
                numExcessIfaces += 1;
            }
            if (numExcessIfaces > 0) { // may need to delete some
                // Try downgrading bridged APs before we consider deleting them.
                if (existingCreateType == HDM_CREATE_IFACE_AP_BRIDGE) {
                    int availableSingleApCapacity = chipCreateTypeCombo[HDM_CREATE_IFACE_AP]
                            - chipInfo.ifaces[HDM_CREATE_IFACE_AP].length;
                    if (requestedCreateType == HDM_CREATE_IFACE_AP) {
                        availableSingleApCapacity -= 1;
                    }
                    if (availableSingleApCapacity >= numExcessIfaces) {
                        List<WifiIfaceInfo> interfacesToBeDowngraded =
                                selectBridgedApInterfacesToDowngrade(
                                        numExcessIfaces, createTypeIfaces);
                        if (interfacesToBeDowngraded != null) {
                            ifaceCreationData.interfacesToBeDowngraded.addAll(
                                    interfacesToBeDowngraded);
                            continue;
                        }
                        // Can't downgrade enough bridged APs, fall through to delete them.
                        if (VDBG) {
                            Log.d(TAG, "Could not downgrade enough bridged APs for request.");
                        }
                    }
                }
                List<WifiIfaceInfo> selectedIfacesToDelete =
                        selectInterfacesToDelete(numExcessIfaces, requestedCreateType, requestorWs,
                                existingCreateType, createTypeIfaces);
                if (selectedIfacesToDelete == null) {
                    if (VDBG) {
                        Log.d(TAG, "Would need to delete some higher priority interfaces");
                    }
                    return null;
                }
                ifaceCreationData.interfacesToBeRemovedFirst.addAll(selectedIfacesToDelete);
            }
        }
        return ifaceCreationData;
    }

    /**
     * Compares two options to create an interface and determines which is the 'best'. Returns
     * true if proposal 1 (val1) is better, other false.
     *
     * Note: both proposals are 'acceptable' bases on priority criteria.
     *
     * Criteria:
     * - Proposal is better if it means removing fewer high priority interfaces, or downgrades the
     *   fewest interfaces.
     */
    private boolean compareIfaceCreationData(IfaceCreationData val1, IfaceCreationData val2) {
        if (VDBG) Log.d(TAG, "compareIfaceCreationData: val1=" + val1 + ", val2=" + val2);

        // deal with trivial case of one or the other being null
        if (val1 == null) {
            return false;
        } else if (val2 == null) {
            return true;
        }

        int[] val1NumIfacesToBeRemoved = new int[CREATE_TYPES_BY_PRIORITY.length];
        if (val1.chipInfo.currentModeIdValid
                && val1.chipInfo.currentModeId != val1.chipModeId) {
            for (int createType : CREATE_TYPES_BY_PRIORITY) {
                val1NumIfacesToBeRemoved[createType] = val1.chipInfo.ifaces[createType].length;
            }
        } else {
            for (WifiIfaceInfo ifaceToRemove : val1.interfacesToBeRemovedFirst) {
                val1NumIfacesToBeRemoved[ifaceToRemove.createType]++;
            }
        }
        int[] val2NumIfacesToBeRemoved = new int[CREATE_TYPES_BY_PRIORITY.length];
        if (val2.chipInfo.currentModeIdValid
                && val2.chipInfo.currentModeId != val2.chipModeId) {
            for (int createType : CREATE_TYPES_BY_PRIORITY) {
                val2NumIfacesToBeRemoved[createType] = val2.chipInfo.ifaces[createType].length;
            }
        } else {
            for (WifiIfaceInfo ifaceToRemove : val2.interfacesToBeRemovedFirst) {
                val2NumIfacesToBeRemoved[ifaceToRemove.createType]++;
            }
        }

        for (int createType: CREATE_TYPES_BY_PRIORITY) {
            if (val1NumIfacesToBeRemoved[createType] != val2NumIfacesToBeRemoved[createType]) {
                if (VDBG) {
                    Log.d(TAG, "decision based on num ifaces to be removed, createType="
                            + createType + ", new proposal will remove "
                            + val1NumIfacesToBeRemoved[createType] + " iface, and old proposal"
                            + "will remove " + val2NumIfacesToBeRemoved[createType] + " iface");
                }
                return val1NumIfacesToBeRemoved[createType] < val2NumIfacesToBeRemoved[createType];
            }
        }

        int val1NumIFacesToBeDowngraded = val1.interfacesToBeDowngraded.size();
        int val2NumIFacesToBeDowngraded = val2.interfacesToBeDowngraded.size();
        if (val1NumIFacesToBeDowngraded != val2NumIFacesToBeDowngraded) {
            return val1NumIFacesToBeDowngraded < val2NumIFacesToBeDowngraded;
        }

        // arbitrary - flip a coin
        if (VDBG) Log.d(TAG, "proposals identical - flip a coin");
        return false;
    }

    /**
     * Returns whether interface request from |newRequestorWsPriority| is allowed to delete an
     * interface request from |existingRequestorWsPriority|.
     *
     * Rule:
     *  - If |newRequestorWsPriority| > |existingRequestorWsPriority|, then YES.
     *  - If they are at the same priority level, then
     *      - If both are privileged and not for the same interface type, then YES.
     *      - Else, NO.
     */
    private boolean allowedToDelete(
            @HdmIfaceTypeForCreation int requestedCreateType,
            @NonNull WorkSourceHelper newRequestorWs, @NonNull WifiIfaceInfo existingIfaceInfo) {
        int existingCreateType = existingIfaceInfo.createType;
        WorkSourceHelper existingRequestorWs = existingIfaceInfo.requestorWsHelper;
        @WorkSourceHelper.RequestorWsPriority int newRequestorWsPriority =
                newRequestorWs.getRequestorWsPriority();
        @WorkSourceHelper.RequestorWsPriority int existingRequestorWsPriority =
                existingRequestorWs.getRequestorWsPriority();
        if (!SdkLevel.isAtLeastS()) {
            return allowedToDeleteForR(requestedCreateType, existingCreateType);
        }

        // Special case to let other requesters delete secondary internet STAs
        if (existingCreateType == HDM_CREATE_IFACE_STA
                && newRequestorWsPriority > WorkSourceHelper.PRIORITY_BG) {
            ConcreteClientModeManager cmm = mClientModeManagers.get(existingIfaceInfo.name);
            if (cmm != null && cmm.isSecondaryInternet()) {
                if (mDbg) {
                    Log.i(TAG, "Requested create type " + requestedCreateType + " from "
                            + newRequestorWs + " can delete secondary internet STA from "
                            + existingRequestorWs);
                }
                return true;
            }
        }

        // Allow FG apps to delete any disconnected P2P iface if they are older than
        // config_disconnectedP2pIfaceLowPriorityTimeoutMs.
        if (newRequestorWsPriority > WorkSourceHelper.PRIORITY_BG
                && isDisconnectedP2p(existingIfaceInfo)) {
            return true;
        }

        // Defer deletion decision to the InterfaceConflictManager dialog.
        if (mWifiInjector.getInterfaceConflictManager().needsUserApprovalToDelete(
                        requestedCreateType, newRequestorWs,
                        existingCreateType, existingRequestorWs)) {
            return true;
        }

        // If the new request is higher priority than existing priority, then the new requestor
        // wins. This is because at all other priority levels (except privileged), existing caller
        // wins if both the requests are at the same priority level.
        if (newRequestorWsPriority > existingRequestorWsPriority) {
            return true;
        }
        if (newRequestorWsPriority == existingRequestorWsPriority) {
            // If both the requests are same priority for the same iface type, the existing
            // requestor wins.
            if (requestedCreateType == existingCreateType) {
                return false;
            }
            // If both the requests are privileged, the new requestor wins unless it's P2P against
            // AP (for when the user enables SoftAP with P2P Settings open) or primary STA
            // (since P2P isn't supported without STA).
            if (newRequestorWsPriority == WorkSourceHelper.PRIORITY_PRIVILEGED) {
                if (requestedCreateType == HDM_CREATE_IFACE_P2P) {
                    if (existingCreateType == HDM_CREATE_IFACE_AP
                            || existingCreateType == HDM_CREATE_IFACE_AP_BRIDGE) {
                        return false;
                    }
                    if (existingCreateType == HDM_CREATE_IFACE_STA) {
                        ConcreteClientModeManager cmm = mClientModeManagers.get(
                                existingIfaceInfo.name);
                        if (cmm != null && (cmm.getRole()
                                == ActiveModeManager.ROLE_CLIENT_PRIMARY)) {
                            return false;
                        }
                    }
                }
                return true;
            }
        }

        // Allow LOHS to beat Settings STA if there's no STA+AP concurrency (legacy behavior)
        if (allowedToDeleteForNoStaApConcurrencyLohs(
                requestedCreateType, newRequestorWsPriority,
                existingCreateType, existingRequestorWsPriority)) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if the requested iface is a LOHS trying to delete a Settings STA on a device
     * that doesn't support STA + AP concurrency, false otherwise.
     */
    private boolean allowedToDeleteForNoStaApConcurrencyLohs(
            @HdmIfaceTypeForCreation int requestedCreateType,
            @WorkSourceHelper.RequestorWsPriority int newRequestorWsPriority,
            @HdmIfaceTypeForCreation int existingCreateType,
            @WorkSourceHelper.RequestorWsPriority int existingRequestorWsPriority) {
        return (requestedCreateType == HDM_CREATE_IFACE_AP
                        || requestedCreateType == HDM_CREATE_IFACE_AP_BRIDGE)
                && newRequestorWsPriority != WorkSourceHelper.PRIORITY_INTERNAL
                && newRequestorWsPriority != WorkSourceHelper.PRIORITY_PRIVILEGED
                && existingCreateType == HDM_CREATE_IFACE_STA
                && existingRequestorWsPriority == WorkSourceHelper.PRIORITY_PRIVILEGED
                && !canDeviceSupportCreateTypeCombo(
                        new SparseArray<Integer>() {
                            {
                                put(HDM_CREATE_IFACE_STA, 1);
                                put(HDM_CREATE_IFACE_AP, 1);
                            }
                        });
    }

    /**
     * Returns true if we're allowed to delete the existing interface type for the requested
     * interface type.
     *
     * Rules - applies in order:
     *
     * General rules:
     * 1. No interface will be destroyed for a requested interface of the same type
     *
     * Type-specific rules (but note that the general rules are applied first):
     * 2. Request for AP or STA will destroy any other interface
     * 3. Request for P2P will destroy NAN-only
     * 4. Request for NAN will destroy P2P-only
     */
    private static boolean allowedToDeleteForR(
            @HdmIfaceTypeForCreation int requestedCreateType,
            @HdmIfaceTypeForCreation int existingCreateType) {
        // rule 1
        if (existingCreateType == requestedCreateType) {
            return false;
        }

        // rule 2
        if (requestedCreateType == HDM_CREATE_IFACE_P2P) {
            return existingCreateType == HDM_CREATE_IFACE_NAN;
        }

        // rule 3
        if (requestedCreateType == HDM_CREATE_IFACE_NAN) {
            return existingCreateType == HDM_CREATE_IFACE_P2P;
        }

        // rule 4, the requestedCreateType is either AP/AP_BRIDGED or STA
        return true;
    }

    private boolean isDisconnectedP2p(WifiIfaceInfo p2pInfo) {
        int unusedP2pTimeoutMs = mContext.getResources().getInteger(
                R.integer.config_disconnectedP2pIfaceLowPriorityTimeoutMs);
        if (p2pInfo.createType == HDM_CREATE_IFACE_P2P
                && !mIsP2pConnected
                && unusedP2pTimeoutMs >= 0) {
            InterfaceCacheEntry ifaceCacheEntry = mInterfaceInfoCache.get(
                    Pair.create(p2pInfo.name, getType(p2pInfo.iface)));
            if (ifaceCacheEntry != null && mClock.getElapsedSinceBootMillis()
                    >= ifaceCacheEntry.creationTime + unusedP2pTimeoutMs) {
                if (mDbg) {
                    Log.i(TAG, "Allowed to delete disconnected P2P iface: " + ifaceCacheEntry);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Selects the interfaces of a given type and quantity to delete for a requested interface.
     * If the specified quantity of interfaces cannot be deleted, returns null.
     *
     * Only interfaces with lower priority than the requestor will be selected, in ascending order
     * of priority. Priority is determined by the following rules:
     * 1. Requests for interfaces have the following priority which are based on corresponding
     * requesting  app's context. Priorities in decreasing order (i.e (i) has the highest priority,
     * (v) has the lowest priority).
     *  - (i) Requests from privileged apps (i.e settings, setup wizard, connectivity stack, etc)
     *  - (ii) Requests from system apps.
     *  - (iii) Requests from foreground apps.
     *  - (iv) Requests from foreground services.
     *  - (v) Requests from everything else (lumped together as "background").
     * Note: If there are more than 1 app requesting for a particular interface, then we consider
     * the priority of the highest priority app among them.
     * For ex: If there is a system app and a foreground requesting for NAN iface, then we use the
     * system app to determine the priority of the interface request.
     * 2. If there are 2 conflicting interface requests from apps with the same priority, then
     *    - (i) If both the apps are privileged and not for the same interface type, the new request
     *          wins (last caller wins).
     *    - (ii) Else, the existing request wins (first caller wins).
     * Note: Privileged apps are the ones that the user is directly interacting with, hence we use
     * last caller wins to decide among those, for all other apps we try to minimize disruption to
     * existing requests.
     * For ex: User turns on wifi, then hotspot on legacy devices which do not support STA + AP, we
     * want the last request from the user (i.e hotspot) to be honored.
     *
     * @param requestedQuantity Number of interfaces which need to be selected.
     * @param requestedCreateType Requested iface type.
     * @param requestorWs Requestor worksource.
     * @param existingCreateType Existing iface type.
     * @param existingInterfaces Array of interfaces to be selected from in order of creation.
     */
    private List<WifiIfaceInfo> selectInterfacesToDelete(int requestedQuantity,
            @HdmIfaceTypeForCreation int requestedCreateType, @NonNull WorkSource requestorWs,
            @HdmIfaceTypeForCreation int existingCreateType,
            @NonNull WifiIfaceInfo[] existingInterfaces) {
        if (VDBG) {
            Log.d(TAG, "selectInterfacesToDelete: requestedQuantity=" + requestedQuantity
                    + ", requestedCreateType=" + requestedCreateType
                    + ", requestorWs=" + requestorWs
                    + ", existingCreateType=" + existingCreateType
                    + ", existingInterfaces=" + Arrays.toString(existingInterfaces));
        }
        WorkSourceHelper newRequestorWsHelper = mWifiInjector.makeWsHelper(requestorWs);

        boolean lookupError = false;
        // Map of priority levels to ifaces to delete.
        Map<Integer, List<WifiIfaceInfo>> ifacesToDeleteMap = new HashMap<>();
        // Reverse order to make sure later created interfaces deleted firstly
        for (int i = existingInterfaces.length - 1; i >= 0; i--) {
            WifiIfaceInfo info = existingInterfaces[i];
            InterfaceCacheEntry cacheEntry;
            synchronized (mLock) {
                cacheEntry = mInterfaceInfoCache.get(Pair.create(info.name, getType(info.iface)));
            }
            if (cacheEntry == null) {
                Log.e(TAG,
                        "selectInterfacesToDelete: can't find cache entry with name=" + info.name);
                lookupError = true;
                break;
            }
            int newRequestorWsPriority = newRequestorWsHelper.getRequestorWsPriority();
            int existingRequestorWsPriority = cacheEntry.requestorWsHelper.getRequestorWsPriority();
            boolean isAllowedToDelete = allowedToDelete(requestedCreateType, newRequestorWsHelper,
                    info);
            if (VDBG) {
                Log.d(TAG, "info=" + info + ":  allowedToDelete=" + isAllowedToDelete
                        + " (requestedCreateType=" + requestedCreateType
                        + ", newRequestorWsPriority=" + newRequestorWsPriority
                        + ", existingCreateType=" + existingCreateType
                        + ", existingRequestorWsPriority=" + existingRequestorWsPriority + ")");
            }
            if (isAllowedToDelete) {
                ifacesToDeleteMap.computeIfAbsent(
                        existingRequestorWsPriority, v -> new ArrayList<>()).add(info);
            }
        }

        List<WifiIfaceInfo> ifacesToDelete;
        if (lookupError) {
            Log.e(TAG, "selectInterfacesToDelete: falling back to arbitrary selection");
            ifacesToDelete = Arrays.asList(Arrays.copyOf(existingInterfaces, requestedQuantity));
        } else {
            int numIfacesToDelete = 0;
            ifacesToDelete = new ArrayList<>(requestedQuantity);
            // Iterate from lowest priority to highest priority ifaces.
            for (int i = WorkSourceHelper.PRIORITY_MIN; i <= WorkSourceHelper.PRIORITY_MAX; i++) {
                List<WifiIfaceInfo> ifacesToDeleteListWithinPriority =
                        ifacesToDeleteMap.getOrDefault(i, new ArrayList<>());
                int numIfacesToDeleteWithinPriority =
                        Math.min(requestedQuantity - numIfacesToDelete,
                                ifacesToDeleteListWithinPriority.size());
                ifacesToDelete.addAll(
                        ifacesToDeleteListWithinPriority.subList(
                                0, numIfacesToDeleteWithinPriority));
                numIfacesToDelete += numIfacesToDeleteWithinPriority;
                if (numIfacesToDelete == requestedQuantity) {
                    break;
                }
            }
        }
        if (ifacesToDelete.size() < requestedQuantity) {
            return null;
        }
        return ifacesToDelete;
    }

    /**
     * Selects the requested quantity of bridged AP ifaces available for downgrade in order of
     * creation, or returns null if the requested quantity cannot be satisfied.
     *
     * @param requestedQuantity Number of interfaces which need to be selected
     * @param bridgedApIfaces Array of bridged AP interfaces in order of creation
     */
    private List<WifiIfaceInfo> selectBridgedApInterfacesToDowngrade(int requestedQuantity,
            WifiIfaceInfo[] bridgedApIfaces) {
        List<WifiIfaceInfo> ifacesToDowngrade = new ArrayList<>();
        for (WifiIfaceInfo ifaceInfo : bridgedApIfaces) {
            String name = getName(ifaceInfo.iface);
            if (name == null) {
                continue;
            }
            SoftApManager softApManager = mSoftApManagers.get(name);
            if (softApManager == null) {
                Log.e(TAG, "selectBridgedApInterfacesToDowngrade: Could not find SoftApManager for"
                        + " iface: " + ifaceInfo.iface);
                continue;
            }
            String instanceForRemoval =
                    softApManager.getBridgedApDowngradeIfaceInstanceForRemoval();
            if (instanceForRemoval == null) {
                continue;
            }
            ifacesToDowngrade.add(ifaceInfo);
            if (ifacesToDowngrade.size() >= requestedQuantity) {
                break;
            }
        }
        if (ifacesToDowngrade.size() < requestedQuantity) {
            return null;
        }
        if (VDBG) {
            Log.i(TAG, "selectBridgedApInterfacesToDowngrade: ifaces to downgrade "
                    + ifacesToDowngrade);
        }
        return ifacesToDowngrade;
    }

    /**
     * Checks whether the expanded @HdmIfaceTypeForCreation combo can support the requested combo.
     */
    private boolean canCreateTypeComboSupportRequestedCreateTypeCombo(
            int[] chipCombo, int[] requestedCombo) {
        if (VDBG) {
            Log.d(TAG, "canCreateTypeComboSupportRequestedCreateTypeCombo: "
                    + "chipCombo=" + Arrays.toString(chipCombo)
                    + ", requestedCombo=" + Arrays.toString(requestedCombo));
        }
        for (int createType : CREATE_TYPES_BY_PRIORITY) {
            if (chipCombo[createType]
                    < requestedCombo[createType]) {
                if (VDBG) Log.d(TAG, "Requested type not supported by combo");
                return false;
            }
        }
        return true;
    }

    /**
     * Performs chip reconfiguration per the input:
     * - Removes the specified interfaces
     * - Reconfigures the chip to the new chip mode (if necessary)
     * - Creates the new interface
     *
     * Returns the newly created interface or a null on any error.
     */
    private WifiHal.WifiInterface executeChipReconfiguration(IfaceCreationData ifaceCreationData,
            @HdmIfaceTypeForCreation int createIfaceType, @Nullable List<OuiKeyedData> vendorData) {
        if (mDbg) {
            Log.d(TAG, "executeChipReconfiguration: ifaceCreationData=" + ifaceCreationData
                    + ", createIfaceType=" + createIfaceType);
        }
        synchronized (mLock) {
            // is this a mode change?
            boolean isModeConfigNeeded = !ifaceCreationData.chipInfo.currentModeIdValid
                    || ifaceCreationData.chipInfo.currentModeId != ifaceCreationData.chipModeId;
            if (mDbg) Log.d(TAG, "isModeConfigNeeded=" + isModeConfigNeeded);
            Log.i(TAG, "currentModeId=" + ifaceCreationData.chipInfo.currentModeId
                    + ", requestModeId=" + ifaceCreationData.chipModeId
                    + ", currentModeIdValid=" + ifaceCreationData.chipInfo.currentModeIdValid);

            // first delete interfaces/change modes
            if (isModeConfigNeeded) {
                // remove all interfaces pre mode-change
                // TODO: is this necessary? note that even if we don't want to explicitly
                // remove the interfaces we do need to call the onDeleted callbacks - which
                // this does
                for (WifiIfaceInfo[] ifaceInfos : ifaceCreationData.chipInfo.ifaces) {
                    for (WifiIfaceInfo ifaceInfo : ifaceInfos) {
                        removeIfaceInternal(ifaceInfo.iface,
                                /* validateRttController */false); // ignore return value
                    }
                }

                // Configure mode using the cached chip info, then reload chip info if needed
                boolean configureChipSuccess =
                        ifaceCreationData.chipInfo.chip.configureChip(ifaceCreationData.chipModeId);
                if (!mIsConcurrencyComboLoadedFromDriver) {
                    WifiChipInfo[] wifiChipInfos = getAllChipInfo(true);
                    if (wifiChipInfos != null) {
                        mCachedStaticChipInfos =
                                convertWifiChipInfoToStaticChipInfos(wifiChipInfos);
                        saveStaticChipInfoToStore(mCachedStaticChipInfos);
                        if (configureChipSuccess) {
                            // Successful chip configuration suggests that the modes are valid
                            Log.i(TAG, "Chip info loaded successfully from the HAL");
                            mIsConcurrencyComboLoadedFromDriver = true;
                        }
                    } else {
                        Log.e(TAG, "Could not get current chip info.");
                    }
                }
                if (!configureChipSuccess) {
                    Log.e(TAG, "executeChipReconfiguration: configureChip error");
                    return null;
                }
            } else {
                // remove all interfaces on the delete list
                for (WifiIfaceInfo ifaceInfo : ifaceCreationData.interfacesToBeRemovedFirst) {
                    removeIfaceInternal(ifaceInfo.iface,
                            /* validateRttController */false); // ignore return value
                }
                // downgrade all interfaces on the downgrade list
                for (WifiIfaceInfo ifaceInfo : ifaceCreationData.interfacesToBeDowngraded) {
                    if (ifaceInfo.createType == HDM_CREATE_IFACE_AP_BRIDGE) {
                        if (!downgradeBridgedApIface(ifaceInfo)) {
                            Log.e(TAG, "executeChipReconfiguration: failed to downgrade bridged"
                                    + " AP: " + ifaceInfo);
                            return null;
                        }
                    }
                }
            }

            // create new interface
            WifiHal.WifiInterface iface = null;
            switch (createIfaceType) {
                case HDM_CREATE_IFACE_STA:
                    iface = ifaceCreationData.chipInfo.chip.createStaIface();
                    break;
                case HDM_CREATE_IFACE_AP_BRIDGE:
                    iface = ifaceCreationData.chipInfo.chip.createBridgedApIface(vendorData);
                    break;
                case HDM_CREATE_IFACE_AP:
                    iface = ifaceCreationData.chipInfo.chip.createApIface(vendorData);
                    break;
                case HDM_CREATE_IFACE_P2P:
                    iface = ifaceCreationData.chipInfo.chip.createP2pIface();
                    break;
                case HDM_CREATE_IFACE_NAN:
                    iface = ifaceCreationData.chipInfo.chip.createNanIface();
                    break;
            }

            updateRttControllerWhenInterfaceChanges();

            if (iface == null) {
                Log.e(TAG, "executeChipReconfiguration: failed to create interface"
                        + " createIfaceType=" + createIfaceType);
                return null;
            }

            return iface;
        }
    }

    /**
     * Remove a Iface from IWifiChip.
     * @param iface the interface need to be removed
     * @param validateRttController if RttController validation is required. If any iface creation
     *                              is guaranteed after removing iface, this can be false. Otherwise
     *                              this must be true.
     * @return True if removal succeed, otherwise false.
     */
    private boolean removeIfaceInternal(WifiHal.WifiInterface iface,
            boolean validateRttController) {
        String name = getName(iface);
        int type = getType(iface);
        if (mDbg) Log.d(TAG, "removeIfaceInternal: iface(name)=" + name + ", type=" + type);

        if (type == -1) {
            Log.e(TAG, "removeIfaceInternal: can't get type -- iface(name)=" + name);
            return false;
        }

        synchronized (mLock) {
            WifiChip chip = getChip(iface);
            if (chip == null) {
                Log.e(TAG, "removeIfaceInternal: null IWifiChip -- iface(name)=" + name);
                return false;
            }

            if (name == null) {
                Log.e(TAG, "removeIfaceInternal: can't get name");
                return false;
            }

            // dispatch listeners on other threads to prevent race conditions in case the HAL is
            // blocking and they get notification about destruction from HAL before cleaning up
            // status.
            dispatchDestroyedListeners(name, type, true);

            boolean success = false;
            switch (type) {
                case WifiChip.IFACE_TYPE_STA:
                    mClientModeManagers.remove(name);
                    success = chip.removeStaIface(name);
                    break;
                case WifiChip.IFACE_TYPE_AP:
                    success = chip.removeApIface(name);
                    break;
                case WifiChip.IFACE_TYPE_P2P:
                    success = chip.removeP2pIface(name);
                    break;
                case WifiChip.IFACE_TYPE_NAN:
                    success = chip.removeNanIface(name);
                    break;
                default:
                    Log.wtf(TAG, "removeIfaceInternal: invalid type=" + type);
                    return false;
            }

            // dispatch listeners no matter what status
            dispatchDestroyedListeners(name, type, false);
            if (validateRttController) {
                // Try to update the RttController
                updateRttControllerWhenInterfaceChanges();
            }

            if (success) {
                return true;
            } else {
                Log.e(TAG, "IWifiChip.removeXxxIface failed, name=" + name + ", type=" + type);
                return false;
            }
        }
    }

    // dispatch all destroyed listeners registered for the specified interface AND remove the
    // cache entries for the called listeners
    // onlyOnOtherThreads = true: only call listeners on other threads
    // onlyOnOtherThreads = false: call all listeners
    private void dispatchDestroyedListeners(String name, int type, boolean onlyOnOtherThreads) {
        if (VDBG) Log.d(TAG, "dispatchDestroyedListeners: iface(name)=" + name);

        List<InterfaceDestroyedListenerProxy> triggerList = new ArrayList<>();
        synchronized (mLock) {
            InterfaceCacheEntry entry = mInterfaceInfoCache.get(Pair.create(name, type));
            if (entry == null) {
                Log.e(TAG, "dispatchDestroyedListeners: no cache entry for iface(name)=" + name);
                return;
            }

            Iterator<InterfaceDestroyedListenerProxy> iterator =
                    entry.destroyedListeners.iterator();
            while (iterator.hasNext()) {
                InterfaceDestroyedListenerProxy listener = iterator.next();
                if (!onlyOnOtherThreads || !listener.requestedToRunInCurrentThread()) {
                    triggerList.add(listener);
                    iterator.remove();
                }
            }
            if (!onlyOnOtherThreads) { // leave entry until final call to *all* callbacks
                mInterfaceInfoCache.remove(Pair.create(name, type));
            }
        }

        for (InterfaceDestroyedListenerProxy listener : triggerList) {
            listener.trigger(isWaitForDestroyedListenersMockable());
        }
    }

    // dispatch all destroyed listeners registered to all interfaces
    private void dispatchAllDestroyedListeners() {
        if (VDBG) Log.d(TAG, "dispatchAllDestroyedListeners");

        List<InterfaceDestroyedListenerProxy> triggerList = new ArrayList<>();
        synchronized (mLock) {
            for (InterfaceCacheEntry cacheEntry: mInterfaceInfoCache.values()) {
                for (InterfaceDestroyedListenerProxy listener : cacheEntry.destroyedListeners) {
                    triggerList.add(listener);
                }
                cacheEntry.destroyedListeners.clear(); // for insurance
            }
            mInterfaceInfoCache.clear();
        }

        for (InterfaceDestroyedListenerProxy listener : triggerList) {
            listener.trigger(false);
        }
    }

    private boolean downgradeBridgedApIface(WifiIfaceInfo bridgedApIfaceInfo) {
        String name = getName(bridgedApIfaceInfo.iface);
        if (name == null) {
            return false;
        }
        SoftApManager bridgedSoftApManager = mSoftApManagers.get(name);
        if (bridgedSoftApManager == null) {
            Log.e(TAG, "Could not find SoftApManager for bridged AP iface " + name);
            return false;
        }
        WifiChip chip = getChip(bridgedApIfaceInfo.iface);
        if (chip == null) {
            return false;
        }
        String instanceForRemoval =
                bridgedSoftApManager.getBridgedApDowngradeIfaceInstanceForRemoval();
        return chip.removeIfaceInstanceFromBridgedApIface(name, instanceForRemoval);
    }

    private abstract class ListenerProxy<LISTENER>  {
        protected LISTENER mListener;
        private Handler mHandler;

        // override equals & hash to make sure that the container HashSet is unique with respect to
        // the contained listener
        @Override
        public boolean equals(Object obj) {
            return mListener == ((ListenerProxy<LISTENER>) obj).mListener;
        }

        @Override
        public int hashCode() {
            return mListener.hashCode();
        }

        public boolean requestedToRunInCurrentThread() {
            if (mHandler == null) return true;
            long currentTid = mWifiInjector.getCurrentThreadId();
            long handlerTid = mHandler.getLooper().getThread().getId();
            return currentTid == handlerTid;
        }

        void trigger(boolean isRunAtFront) {
            // TODO(b/199792691): The thread check is needed to preserve the existing
            //  assumptions of synchronous execution of the "onDestroyed" callback as much as
            //  possible. This is needed to prevent regressions caused by posting to the handler
            //  thread changing the code execution order.
            //  When all wifi services (ie. WifiAware, WifiP2p) get moved to the wifi handler
            //  thread, remove this thread check and the Handler#post() and simply always
            //  invoke the callback directly.
            if (mFeatureFlags.singleWifiThread()) {
                action();
                return;
            }
            if (requestedToRunInCurrentThread()) {
                // Already running on the same handler thread. Trigger listener synchronously.
                action();
            } else if (isRunAtFront) {
                // Current thread is not the thread the listener should be invoked on.
                // Post action to the intended thread and run synchronously.
                new WifiThreadRunner(mHandler).runAtFront(() -> {
                    action();
                });
            } else {
                // Current thread is not the thread the listener should be invoked on.
                // Post action to the intended thread.
                if (mHandler instanceof RunnerHandler) {
                    RunnerHandler rh = (RunnerHandler) mHandler;
                    rh.postToFront(() -> action());
                } else {
                    mHandler.postAtFrontOfQueue(() -> action());
                }
            }
        }

        protected void action() {}

        ListenerProxy(LISTENER listener, Handler handler, String tag) {
            mListener = listener;
            mHandler = handler;
        }
    }

    private class SubsystemRestartListenerProxy extends
            ListenerProxy<SubsystemRestartListener> {
        SubsystemRestartListenerProxy(@NonNull SubsystemRestartListener subsystemRestartListener,
                                        Handler handler) {
            super(subsystemRestartListener, handler, "SubsystemRestartListenerProxy");
        }

        @Override
        protected void action() {
            mListener.onSubsystemRestart();
        }
    }

    private class InterfaceDestroyedListenerProxy extends
            ListenerProxy<InterfaceDestroyedListener> {
        private final String mIfaceName;
        InterfaceDestroyedListenerProxy(@NonNull String ifaceName,
                                        @NonNull InterfaceDestroyedListener destroyedListener,
                                        @NonNull Handler handler) {
            super(destroyedListener, handler, "InterfaceDestroyedListenerProxy");
            mIfaceName = ifaceName;
        }

        @Override
        protected void action() {
            mListener.onDestroyed(mIfaceName);
        }
    }

    private class InterfaceRttControllerLifecycleCallbackProxy implements
            InterfaceRttControllerLifecycleCallback {
        private InterfaceRttControllerLifecycleCallback mCallback;
        private Handler mHandler;

        InterfaceRttControllerLifecycleCallbackProxy(
                InterfaceRttControllerLifecycleCallback callback, Handler handler) {
            mCallback = callback;
            mHandler = handler;
        }

        // override equals & hash to make sure that the container HashSet is unique with respect to
        // the contained listener
        @Override
        public boolean equals(Object obj) {
            return mCallback == ((InterfaceRttControllerLifecycleCallbackProxy) obj).mCallback;
        }

        @Override
        public int hashCode() {
            return mCallback.hashCode();
        }

        @Override
        public void onNewRttController(WifiRttController controller) {
            mHandler.post(() -> mCallback.onNewRttController(controller));
        }

        @Override
        public void onRttControllerDestroyed() {
            mHandler.post(() -> mCallback.onRttControllerDestroyed());
        }
    }

    private void dispatchRttControllerLifecycleOnNew() {
        if (VDBG) {
            Log.v(TAG, "dispatchRttControllerLifecycleOnNew: # cbs="
                    + mRttControllerLifecycleCallbacks.size());
        }
        for (InterfaceRttControllerLifecycleCallbackProxy cbp : mRttControllerLifecycleCallbacks) {
            cbp.onNewRttController(mWifiRttController);
        }
    }

    private void dispatchRttControllerLifecycleOnDestroyed() {
        for (InterfaceRttControllerLifecycleCallbackProxy cbp : mRttControllerLifecycleCallbacks) {
            cbp.onRttControllerDestroyed();
        }
    }

    /**
     * Updates the RttController when the interface changes:
     * - Handles callbacks to registered listeners
     * - Handles creation of new RttController
     */
    private void updateRttControllerWhenInterfaceChanges() {
        synchronized (mLock) {
            if (mWifiRttController != null && mWifiRttController.validate()) {
                if (mDbg) {
                    Log.d(TAG, "Current RttController is valid, Don't try to create a new one");
                }
                return;
            }
            boolean controllerDestroyed = mWifiRttController != null;
            mWifiRttController = null;
            if (mRttControllerLifecycleCallbacks.size() == 0) {
                Log.d(TAG, "updateRttController: no one is interested in RTT controllers");
                return;
            }

            WifiRttController newRttController = createRttControllerIfPossible();
            if (newRttController == null) {
                if (controllerDestroyed) {
                    dispatchRttControllerLifecycleOnDestroyed();
                }
            } else {
                mWifiRttController = newRttController;
                dispatchRttControllerLifecycleOnNew();
            }
        }
    }

    /**
     * Try to create and set up a new RttController.
     *
     * @return The new RttController - or null on failure.
     */
    private WifiRttController createRttControllerIfPossible() {
        synchronized (mLock) {
            if (!isWifiStarted()) {
                Log.d(TAG, "createRttControllerIfPossible: Wifi is not started");
                return null;
            }

            WifiChipInfo[] chipInfos = getAllChipInfo(false);
            if (chipInfos == null) {
                Log.d(TAG, "createRttControllerIfPossible: no chip info found - most likely chip "
                        + "not up yet");
                return null;
            }

            for (WifiChipInfo chipInfo : chipInfos) {
                if (!chipInfo.currentModeIdValid) {
                    if (VDBG) {
                        Log.d(TAG, "createRttControllerIfPossible: chip not configured yet: "
                                + chipInfo);
                    }
                    continue;
                }

                WifiRttController rttController = chipInfo.chip.createRttController();
                if (rttController != null) {
                    if (!rttController.setup()) {
                        return null;
                    }
                    rttController.enableVerboseLogging(mDbg);
                    return rttController;
                }
            }
        }

        Log.w(TAG, "createRttControllerIfPossible: not available from any of the chips");
        return null;
    }

    // general utilities

    // Will return -1 for invalid results! Otherwise will return one of the 4 valid values.
    @VisibleForTesting
    protected static int getType(WifiHal.WifiInterface iface) {
        if (iface instanceof WifiStaIface) {
            return WifiChip.IFACE_TYPE_STA;
        } else if (iface instanceof WifiApIface) {
            return WifiChip.IFACE_TYPE_AP;
        } else if (iface instanceof WifiP2pIface) {
            return WifiChip.IFACE_TYPE_P2P;
        } else if (iface instanceof WifiNanIface) {
            return WifiChip.IFACE_TYPE_NAN;
        }
        return -1;
    }

    private static Set<List<Integer>> getChipSupportedBandCombinations(
            List<WifiChip.WifiRadioCombination> combinations) {
        Set<List<Integer>> lookupTable = new ArraySet<>();
        if (combinations == null) return lookupTable;
        // Add radio combinations to the lookup table.
        for (WifiChip.WifiRadioCombination combination : combinations) {
            // Build list of bands.
            List<Integer> bands = new ArrayList<>();
            for (WifiChip.WifiRadioConfiguration config : combination.radioConfigurations) {
                bands.add(config.bandInfo);
            }
            // Sort the list of bands as hash code depends on the order and content of the list.
            Collections.sort(bands);
            lookupTable.add(Collections.unmodifiableList(bands));
        }
        return lookupTable;
    }

    /**
     * Get the chip capabilities.
     *
     * @param wifiChip WifiChip to get the features for.
     * @return Bitset of WifiManager.WIFI_FEATURE_* values.
     */
    public long getChipCapabilities(@NonNull WifiChip wifiChip) {
        if (wifiChip == null) return 0;

        WifiChip.Response<Long> capsResp = wifiChip.getCapabilitiesBeforeIfacesExist();
        if (capsResp.getStatusCode() == WifiHal.WIFI_STATUS_SUCCESS) {
            return capsResp.getValue();
        } else if (capsResp.getStatusCode() != WifiHal.WIFI_STATUS_ERROR_REMOTE_EXCEPTION) {
            // Non-remote exception here is likely because HIDL HAL < v1.5
            // does not support getting capabilities before creating an interface.
            return CHIP_CAPABILITY_UNINITIALIZED;
        } else { // remote exception
            return 0;
        }
    }

    /**
     * Get the supported radio combinations.
     *
     * This is called after creating an interface and need at least v1.6 HAL.
     *
     * @param wifiChip WifiChip
     * @return List of supported Wifi radio combinations
     */
    private List<WifiChip.WifiRadioCombination> getChipSupportedRadioCombinations(
            @NonNull WifiChip wifiChip) {
        synchronized (mLock) {
            if (wifiChip == null) return null;
            return wifiChip.getSupportedRadioCombinations();
        }
    }

    /**
     * Initialization after boot completes to get boot-dependent resources.
     */
    public void handleBootCompleted() {
        Resources res = mContext.getResources();
        mWaitForDestroyedListeners = res.getBoolean(R.bool.config_wifiWaitForDestroyedListeners);
    }

    /**
     * Dump the internal state of the class.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of HalDeviceManager:");
        synchronized (mLock) {
            pw.println("  mManagerStatusListeners: " + mManagerStatusListeners);
            pw.println("  mInterfaceInfoCache: " + mInterfaceInfoCache);
        }
        pw.println("  mDebugChipsInfo: " + Arrays.toString(getAllChipInfo(false)));
    }
}
