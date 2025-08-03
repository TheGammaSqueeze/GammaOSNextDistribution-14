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

package com.android.networkstack.tethering;

import static android.net.ConnectivityManager.TYPE_BLUETOOTH;
import static android.net.ConnectivityManager.TYPE_ETHERNET;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.ConnectivityManager.TYPE_MOBILE_DUN;
import static android.net.ConnectivityManager.TYPE_MOBILE_HIPRI;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.NetworkCapabilities.NET_CAPABILITY_DUN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.IpPrefix;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.util.Range;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.SharedLog;
import com.android.networkstack.apishim.ConnectivityManagerShimImpl;
import com.android.networkstack.apishim.common.ConnectivityManagerShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;
import com.android.networkstack.tethering.util.PrefixUtils;

import lineageos.providers.LineageSettings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


/**
 * A class to centralize all the network and link properties information
 * pertaining to the current and any potential upstream network.
 *
 * The owner of UNM gets it to register network callbacks by calling the
 * following methods :
 * Calling #startTrackDefaultNetwork() to track the system default network.
 * Calling #startObserveAllNetworks() to observe all networks. Listening all
 * networks is necessary while the expression of preferred upstreams remains
 * a list of legacy connectivity types.  In future, this can be revisited.
 * Calling #setTryCell() to request bringing up mobile DUN or HIPRI.
 *
 * The methods and data members of this class are only to be accessed and
 * modified from the tethering main state machine thread. Any other
 * access semantics would necessitate the addition of locking.
 *
 * TODO: Move upstream selection logic here.
 *
 * All callback methods are run on the same thread as the specified target
 * state machine.  This class does not require locking when accessed from this
 * thread.  Access from other threads is not advised.
 *
 * @hide
 */
public class UpstreamNetworkMonitor {
    private static final String TAG = UpstreamNetworkMonitor.class.getSimpleName();
    private static final boolean DBG = false;
    private static final boolean VDBG = false;

    // Copied from frameworks/base/core/java/android/provider/Settings.java
    private static final String ALWAYS_ON_VPN_LOCKDOWN = "always_on_vpn_lockdown";

    public static final int EVENT_ON_CAPABILITIES   = 1;
    public static final int EVENT_ON_LINKPROPERTIES = 2;
    public static final int EVENT_ON_LOST           = 3;
    public static final int EVENT_DEFAULT_SWITCHED  = 4;
    public static final int NOTIFY_LOCAL_PREFIXES   = 10;
    // This value is used by deprecated preferredUpstreamIfaceTypes selection which is default
    // disabled.
    @VisibleForTesting
    public static final int TYPE_NONE = -1;

    private static final int CALLBACK_LISTEN_ALL = 1;
    private static final int CALLBACK_DEFAULT_INTERNET = 2;
    private static final int CALLBACK_MOBILE_REQUEST = 3;

    private static final SparseIntArray sLegacyTypeToTransport = new SparseIntArray();
    static {
        sLegacyTypeToTransport.put(TYPE_MOBILE,       NetworkCapabilities.TRANSPORT_CELLULAR);
        sLegacyTypeToTransport.put(TYPE_MOBILE_DUN,   NetworkCapabilities.TRANSPORT_CELLULAR);
        sLegacyTypeToTransport.put(TYPE_MOBILE_HIPRI, NetworkCapabilities.TRANSPORT_CELLULAR);
        sLegacyTypeToTransport.put(TYPE_WIFI,         NetworkCapabilities.TRANSPORT_WIFI);
        sLegacyTypeToTransport.put(TYPE_BLUETOOTH,    NetworkCapabilities.TRANSPORT_BLUETOOTH);
        sLegacyTypeToTransport.put(TYPE_ETHERNET,     NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    private final Context mContext;
    private final SharedLog mLog;
    private final Handler mHandler;
    private final EventListener mEventListener;
    private final HashMap<Network, UpstreamNetworkState> mNetworkMap = new HashMap<>();
    private HashSet<IpPrefix> mLocalPrefixes;
    private ConnectivityManager mCM;
    private EntitlementManager mEntitlementMgr;
    private NetworkCallback mListenAllCallback;
    private NetworkCallback mDefaultNetworkCallback;
    private NetworkCallback mMobileNetworkCallback;

    /** Whether Tethering has requested a cellular upstream. */
    private boolean mTryCell;
    /** Whether the carrier requires DUN. */
    private boolean mDunRequired;
    /** Whether automatic upstream selection is enabled. */
    private boolean mAutoUpstream;

    // Whether the current default upstream is mobile or not.
    private boolean mIsDefaultCellularUpstream;
    // The current system default network (not really used yet).
    private Network mDefaultInternetNetwork;
    private boolean mPreferTestNetworks;
    // Set if the Internet is considered reachable via the main user's VPN network
    private Network mTetheringUpstreamVpn;

    public UpstreamNetworkMonitor(Context ctx, Handler h, SharedLog log, EventListener listener) {
        mContext = ctx;
        mHandler = h;
        mLog = log.forSubComponent(TAG);
        mEventListener = listener;
        mLocalPrefixes = new HashSet<>();
        mIsDefaultCellularUpstream = false;
        mCM = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /**
     * Tracking the system default network. This method should be only called once when system is
     * ready, and the callback is never unregistered.
     *
     * @param entitle a EntitlementManager object to communicate between EntitlementManager and
     * UpstreamNetworkMonitor
     */
    public void startTrackDefaultNetwork(EntitlementManager entitle) {
        if (mDefaultNetworkCallback != null) {
            Log.wtf(TAG, "default network callback is already registered");
            return;
        }
        ConnectivityManagerShim mCmShim = ConnectivityManagerShimImpl.newInstance(mContext);
        registerAppropriateDefaultNetworkCallback();
        if (mEntitlementMgr == null) {
            mEntitlementMgr = entitle;
        }
    }

    /** Listen all networks. */
    public void startObserveAllNetworks() {
        stop();

        final NetworkRequest listenAllRequest = new NetworkRequest.Builder()
                .clearCapabilities().build();
        mListenAllCallback = new UpstreamNetworkCallback(CALLBACK_LISTEN_ALL);
        cm().registerNetworkCallback(listenAllRequest, mListenAllCallback, mHandler);
    }

    /**
     * Stop tracking candidate tethering upstreams and release mobile network request.
     * Note: this function is used when tethering is stopped because tethering do not need to
     * choose upstream anymore. But it would not stop default network tracking because
     * EntitlementManager may need to know default network to decide whether to request entitlement
     * check even tethering is not active yet.
     */
    public void stop() {
        setTryCell(false);

        releaseCallback(mListenAllCallback);
        mListenAllCallback = null;

        mTetheringUpstreamVpn = null;
        mNetworkMap.clear();
    }

    public void maybeUpdateDefaultNetworkCallback() {
        final NetworkCallback prevNetworkCallback = mDefaultNetworkCallback;
        if (prevNetworkCallback != null) {
            registerAppropriateDefaultNetworkCallback();
            cm().unregisterNetworkCallback(prevNetworkCallback);
        }
    }

    private void registerAppropriateDefaultNetworkCallback() {
        mDefaultNetworkCallback = new UpstreamNetworkCallback(CALLBACK_DEFAULT_INTERNET);
        final ConnectivityManagerShim cmShim = ConnectivityManagerShimImpl.newInstance(mContext);
        if (isAllowedToUseVpnUpstreams() && SdkLevel.isAtLeastU()) {
            try {
                cmShim.registerDefaultNetworkCallbackForUid(Process.ROOT_UID,
                        mDefaultNetworkCallback, mHandler);
            } catch (UnsupportedApiLevelException e) {
                Log.wtf(TAG, "Unexpected exception registering network callback for root UID"
                        + " to support hotspot VPN upstreams", e);
            }
        } else {
            cmShim.registerSystemDefaultNetworkCallback(mDefaultNetworkCallback, mHandler);
        }
    }

    private boolean isAllowedToUseVpnUpstreams() {
        return LineageSettings.Secure.getInt(mContext.getContentResolver(),
                LineageSettings.Secure.TETHERING_ALLOW_VPN_UPSTREAMS, 0) == 1;
    }

    private void reevaluateUpstreamRequirements(boolean tryCell, boolean autoUpstream,
            boolean dunRequired) {
        final boolean mobileRequestRequired = tryCell && (dunRequired || !autoUpstream);
        final boolean dunRequiredChanged = (mDunRequired != dunRequired);

        mTryCell = tryCell;
        mDunRequired = dunRequired;
        mAutoUpstream = autoUpstream;

        if (mobileRequestRequired && !mobileNetworkRequested()) {
            registerMobileNetworkRequest();
        } else if (mobileNetworkRequested() && !mobileRequestRequired) {
            releaseMobileNetworkRequest();
        } else if (mobileNetworkRequested() && dunRequiredChanged) {
            releaseMobileNetworkRequest();
            if (mobileRequestRequired) {
                registerMobileNetworkRequest();
            }
        }
    }

    /**
     * Informs UpstreamNetworkMonitor that a cellular upstream is desired.
     *
     * This may result in filing a NetworkRequest for DUN if it is required, or for MOBILE_HIPRI if
     * automatic upstream selection is disabled and MOBILE_HIPRI is the preferred upstream.
     */
    public void setTryCell(boolean tryCell) {
        reevaluateUpstreamRequirements(tryCell, mAutoUpstream, mDunRequired);
    }

    /** Informs UpstreamNetworkMonitor of upstream configuration parameters. */
    public void setUpstreamConfig(boolean autoUpstream, boolean dunRequired) {
        reevaluateUpstreamRequirements(mTryCell, autoUpstream, dunRequired);
    }

    /** Whether mobile network is requested. */
    public boolean mobileNetworkRequested() {
        return (mMobileNetworkCallback != null);
    }

    /** Request mobile network if mobile upstream is permitted. */
    private void registerMobileNetworkRequest() {
        if (!isCellularUpstreamPermitted()) {
            mLog.i("registerMobileNetworkRequest() is not permitted");
            releaseMobileNetworkRequest();
            return;
        }
        if (mMobileNetworkCallback != null) {
            mLog.e("registerMobileNetworkRequest() already registered");
            return;
        }

        final NetworkRequest mobileUpstreamRequest;
        if (mDunRequired) {
            mobileUpstreamRequest = new NetworkRequest.Builder()
                    .addCapability(NET_CAPABILITY_DUN)
                    .removeCapability(NET_CAPABILITY_NOT_RESTRICTED)
                    .addTransportType(TRANSPORT_CELLULAR).build();
        } else {
            mobileUpstreamRequest = new NetworkRequest.Builder()
                    .addCapability(NET_CAPABILITY_INTERNET)
                    .addTransportType(TRANSPORT_CELLULAR).build();
        }

        // The existing default network and DUN callbacks will be notified.
        // Therefore, to avoid duplicate notifications, we only register a no-op.
        mMobileNetworkCallback = new UpstreamNetworkCallback(CALLBACK_MOBILE_REQUEST);

        // The following use of the legacy type system cannot be removed until
        // upstream selection no longer finds networks by legacy type.
        // See also http://b/34364553 .
        final int legacyType = mDunRequired ? TYPE_MOBILE_DUN : TYPE_MOBILE_HIPRI;

        // TODO: Change the timeout from 0 (no onUnavailable callback) to some
        // moderate callback timeout. This might be useful for updating some UI.
        // Additionally, we log a message to aid in any subsequent debugging.
        mLog.i("requesting mobile upstream network: " + mobileUpstreamRequest
                + " mTryCell=" + mTryCell + " mAutoUpstream=" + mAutoUpstream
                + " mDunRequired=" + mDunRequired);

        cm().requestNetwork(mobileUpstreamRequest, 0, legacyType, mHandler,
                mMobileNetworkCallback);
    }

    /** Release mobile network request. */
    private void releaseMobileNetworkRequest() {
        if (mMobileNetworkCallback == null) return;

        cm().unregisterNetworkCallback(mMobileNetworkCallback);
        mMobileNetworkCallback = null;
    }

    // So many TODOs here, but chief among them is: make this functionality an
    // integral part of this class such that whenever a higher priority network
    // becomes available and useful we (a) file a request to keep it up as
    // necessary and (b) change all upstream tracking state accordingly (by
    // passing LinkProperties up to Tethering).
    /**
     * Select the first available network from |perferredTypes|.
     */
    public UpstreamNetworkState selectPreferredUpstreamType(Iterable<Integer> preferredTypes) {
        final TypeStatePair typeStatePair = findFirstAvailableUpstreamByType(
                mNetworkMap.values(), preferredTypes, isCellularUpstreamPermitted());

        mLog.log("preferred upstream type: " + typeStatePair.type);

        switch (typeStatePair.type) {
            case TYPE_MOBILE_DUN:
            case TYPE_MOBILE_HIPRI:
                // Tethering just selected mobile upstream in spite of the default network being
                // not mobile. This can happen because of the priority list.
                // Notify EntitlementManager to check permission for using mobile upstream.
                if (!mIsDefaultCellularUpstream) {
                    mEntitlementMgr.maybeRunProvisioning();
                }
                break;
        }

        return typeStatePair.ns;
    }

    /**
     * Get current preferred upstream network. If default network is cellular and DUN is required,
     * preferred upstream would be DUN otherwise preferred upstream is the same as default network.
     * Returns null if no current upstream is available.
     */
    public UpstreamNetworkState getCurrentPreferredUpstream() {
        // Use VPN upstreams if hotspot settings allow.
        if (isAllowedToUseVpnUpstreams()) {
            if (mTetheringUpstreamVpn != null) {
                return mNetworkMap.get(mTetheringUpstreamVpn);
            } else if (Settings.Secure.getInt(mContext.getContentResolver(),
                    ALWAYS_ON_VPN_LOCKDOWN, 0) == 1) {
                return null;
            }
        }
        final UpstreamNetworkState dfltState = (mDefaultInternetNetwork != null)
                ? mNetworkMap.get(mDefaultInternetNetwork)
                : null;
        if (mPreferTestNetworks) {
            final UpstreamNetworkState testState = findFirstTestNetwork(mNetworkMap.values());
            if (testState != null) return testState;
        }

        if (isNetworkUsableAndNotCellular(dfltState)) return dfltState;

        if (!isCellularUpstreamPermitted()) return null;

        if (!mDunRequired) return dfltState;

        // Find a DUN network. Note that code in Tethering causes a DUN request
        // to be filed, but this might be moved into this class in future.
        return findFirstDunNetwork(mNetworkMap.values());
    }

    /** Return local prefixes. */
    public Set<IpPrefix> getLocalPrefixes() {
        return (Set<IpPrefix>) mLocalPrefixes.clone();
    }

    private boolean isCellularUpstreamPermitted() {
        if (mEntitlementMgr != null) {
            return mEntitlementMgr.isCellularUpstreamPermitted();
        } else {
            // This flow should only happens in testing.
            return true;
        }
    }

    private void handleAvailable(Network network) {
        if (mNetworkMap.containsKey(network)) return;

        if (VDBG) Log.d(TAG, "onAvailable for " + network);
        mNetworkMap.put(network, new UpstreamNetworkState(null, null, network));
    }

    private void handleNetCap(Network network, NetworkCapabilities newNc) {
        if (isSystemVpnUsable(newNc)) mTetheringUpstreamVpn = network;
        final UpstreamNetworkState prev = mNetworkMap.get(network);
        if (prev == null || newNc.equals(prev.networkCapabilities)) {
            // Ignore notifications about networks for which we have not yet
            // received onAvailable() (should never happen) and any duplicate
            // notifications (e.g. matching more than one of our callbacks).
            return;
        }

        if (VDBG) {
            Log.d(TAG, String.format("EVENT_ON_CAPABILITIES for %s: %s",
                    network, newNc));
        }

        final UpstreamNetworkState uns =
                new UpstreamNetworkState(prev.linkProperties, newNc, network);
        mNetworkMap.put(network, uns);
        // TODO: If sufficient information is available to select a more
        // preferable upstream, do so now and notify the target.
        mEventListener.onUpstreamEvent(EVENT_ON_CAPABILITIES, uns);
    }

    private @Nullable UpstreamNetworkState updateLinkProperties(@NonNull Network network,
            LinkProperties newLp) {
        final UpstreamNetworkState prev = mNetworkMap.get(network);
        if (prev == null || newLp.equals(prev.linkProperties)) {
            // Ignore notifications about networks for which we have not yet
            // received onAvailable() (should never happen) and any duplicate
            // notifications (e.g. matching more than one of our callbacks).
            //
            // Also, it can happen that onLinkPropertiesChanged is called after
            // onLost removed the state from mNetworkMap. This is due to a bug
            // in disconnectAndDestroyNetwork, which calls nai.clatd.update()
            // after the onLost callbacks. This was fixed in S.
            // TODO: make this method void when R is no longer supported.
            return null;
        }

        if (VDBG) {
            Log.d(TAG, String.format("EVENT_ON_LINKPROPERTIES for %s: %s",
                    network, newLp));
        }

        final UpstreamNetworkState ns = new UpstreamNetworkState(newLp, prev.networkCapabilities,
                network);
        mNetworkMap.put(network, ns);
        return ns;
    }

    private void handleLinkProp(Network network, LinkProperties newLp) {
        final UpstreamNetworkState ns = updateLinkProperties(network, newLp);
        if (ns != null) {
            mEventListener.onUpstreamEvent(EVENT_ON_LINKPROPERTIES, ns);
        }
    }

    private void handleLost(Network network) {
        // There are few TODOs within ConnectivityService's rematching code
        // pertaining to spurious onLost() notifications.
        //
        // TODO: simplify this, probably if favor of code that:
        //     - selects a new upstream if mTetheringUpstreamNetwork has
        //       been lost (by any callback)
        //     - deletes the entry from the map only when the LISTEN_ALL
        //       callback gets notified.

        if (network.equals(mTetheringUpstreamVpn)) {
            mTetheringUpstreamVpn = null;
        }

        if (!mNetworkMap.containsKey(network)) {
            // Ignore loss of networks about which we had not previously
            // learned any information or for which we have already processed
            // an onLost() notification.
            return;
        }

        if (VDBG) Log.d(TAG, "EVENT_ON_LOST for " + network);

        // TODO: If sufficient information is available to select a more
        // preferable upstream, do so now and notify the target.  Likewise,
        // if the current upstream network is gone, notify the target of the
        // fact that we now have no upstream at all.
        mEventListener.onUpstreamEvent(EVENT_ON_LOST, mNetworkMap.remove(network));
    }

    private void maybeHandleNetworkSwitch(@NonNull Network network) {
        if (Objects.equals(mDefaultInternetNetwork, network)) return;

        final UpstreamNetworkState ns = mNetworkMap.get(network);
        if (ns == null) {
            // Can never happen unless there is a bug in ConnectivityService. Entries are only
            // removed from mNetworkMap when receiving onLost, and onLost for a given network can
            // never be followed by any other callback on that network.
            Log.wtf(TAG, "maybeHandleNetworkSwitch: no UpstreamNetworkState for " + network);
            return;
        }

        // Default network changed. Update local data and notify tethering.
        Log.d(TAG, "New default Internet network: " + network);
        mDefaultInternetNetwork = network;
        mEventListener.onUpstreamEvent(EVENT_DEFAULT_SWITCHED, ns);
    }

    private void recomputeLocalPrefixes() {
        final HashSet<IpPrefix> localPrefixes = allLocalPrefixes(mNetworkMap.values());
        if (!mLocalPrefixes.equals(localPrefixes)) {
            mLocalPrefixes = localPrefixes;
            mEventListener.onUpstreamEvent(NOTIFY_LOCAL_PREFIXES, localPrefixes.clone());
        }
    }

    // Fetch (and cache) a ConnectivityManager only if and when we need one.
    private ConnectivityManager cm() {
        if (mCM == null) {
            // MUST call the String variant to be able to write unittests.
            mCM = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        return mCM;
    }

    /**
     * A NetworkCallback class that handles information of interest directly
     * in the thread on which it is invoked. To avoid locking, this MUST be
     * run on the same thread as the target state machine's handler.
     */
    private class UpstreamNetworkCallback extends NetworkCallback {
        private final int mCallbackType;

        UpstreamNetworkCallback(int callbackType) {
            mCallbackType = callbackType;
        }

        @Override
        public void onAvailable(Network network) {
            handleAvailable(network);
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities newNc) {
            if (mCallbackType == CALLBACK_DEFAULT_INTERNET) {
                // mDefaultInternetNetwork is not updated here because upstream selection must only
                // run when the LinkProperties have been updated as well as the capabilities. If
                // this callback is due to a default network switch, then the system will invoke
                // onLinkPropertiesChanged right after this method and mDefaultInternetNetwork will
                // be updated then.
                //
                // Technically, mDefaultInternetNetwork could be updated here, because the
                // Callback#onChange implementation sends messages to the state machine running
                // on the same thread as this method. If there is new default network change,
                // the message cannot arrive until onLinkPropertiesChanged returns.
                // However, it is not a good idea to rely on that because fact that Tethering uses
                // multiple state machines running on the same thread is a major source of race
                // conditions and something that should be fixed.
                //
                // TODO: is it correct that this code always updates EntitlementManager?
                // This code runs when the default network connects or changes capabilities, but the
                // default network might not be the tethering upstream.
                final boolean newIsCellular = isCellular(newNc);
                if (mIsDefaultCellularUpstream != newIsCellular) {
                    mIsDefaultCellularUpstream = newIsCellular;
                    mEntitlementMgr.notifyUpstream(newIsCellular);
                }
                return;
            }

            handleNetCap(network, newNc);
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties newLp) {
            handleLinkProp(network, newLp);

            if (mCallbackType == CALLBACK_DEFAULT_INTERNET) {
                // When the default network callback calls onLinkPropertiesChanged, it means that
                // all the network information for the default network is known (because
                // onLinkPropertiesChanged is called after onAvailable and onCapabilitiesChanged).
                // Inform tethering that the default network might have changed.
                maybeHandleNetworkSwitch(network);
                return;
            }

            // Any non-LISTEN_ALL callback will necessarily concern a network that will
            // also match the LISTEN_ALL callback by construction of the LISTEN_ALL callback.
            // So it's not useful to do this work for non-LISTEN_ALL callbacks.
            if (mCallbackType == CALLBACK_LISTEN_ALL) {
                recomputeLocalPrefixes();
            }
        }

        @Override
        public void onLost(Network network) {
            if (mCallbackType == CALLBACK_DEFAULT_INTERNET) {
                mDefaultInternetNetwork = null;
                mIsDefaultCellularUpstream = false;
                mEntitlementMgr.notifyUpstream(false);
                Log.d(TAG, "Lost default Internet network: " + network);
                mEventListener.onUpstreamEvent(EVENT_DEFAULT_SWITCHED, null);
                return;
            }

            handleLost(network);
            // Any non-LISTEN_ALL callback will necessarily concern a network that will
            // also match the LISTEN_ALL callback by construction of the LISTEN_ALL callback.
            // So it's not useful to do this work for non-LISTEN_ALL callbacks.
            if (mCallbackType == CALLBACK_LISTEN_ALL) {
                recomputeLocalPrefixes();
            }
        }
    }

    private void releaseCallback(NetworkCallback cb) {
        if (cb != null) cm().unregisterNetworkCallback(cb);
    }

    private static class TypeStatePair {
        public int type = TYPE_NONE;
        public UpstreamNetworkState ns = null;
    }

    private static TypeStatePair findFirstAvailableUpstreamByType(
            Iterable<UpstreamNetworkState> netStates, Iterable<Integer> preferredTypes,
            boolean isCellularUpstreamPermitted) {
        final TypeStatePair result = new TypeStatePair();

        for (int type : preferredTypes) {
            NetworkCapabilities nc;
            try {
                nc = networkCapabilitiesForType(type);
            } catch (IllegalArgumentException iae) {
                Log.e(TAG, "No NetworkCapabilities mapping for legacy type: " + type);
                continue;
            }
            if (!isCellularUpstreamPermitted && isCellular(nc)) {
                continue;
            }

            for (UpstreamNetworkState value : netStates) {
                if (!nc.satisfiedByNetworkCapabilities(value.networkCapabilities)) {
                    continue;
                }

                result.type = type;
                result.ns = value;
                return result;
            }
        }

        return result;
    }

    private static HashSet<IpPrefix> allLocalPrefixes(Iterable<UpstreamNetworkState> netStates) {
        final HashSet<IpPrefix> prefixSet = new HashSet<>();

        for (UpstreamNetworkState ns : netStates) {
            final LinkProperties lp = ns.linkProperties;
            if (lp == null) continue;
            prefixSet.addAll(PrefixUtils.localPrefixesFrom(lp));
        }

        return prefixSet;
    }

    /** Check whether upstream is cellular. */
    static boolean isCellular(UpstreamNetworkState ns) {
        return (ns != null) && isCellular(ns.networkCapabilities);
    }

    private static boolean isCellular(NetworkCapabilities nc) {
        return (nc != null) && nc.hasTransport(TRANSPORT_CELLULAR)
               && nc.hasCapability(NET_CAPABILITY_NOT_VPN);
    }

    private static boolean hasCapability(UpstreamNetworkState ns, int netCap) {
        return (ns != null) && (ns.networkCapabilities != null)
               && ns.networkCapabilities.hasCapability(netCap);
    }

    private static boolean isNetworkUsableAndNotCellular(UpstreamNetworkState ns) {
        return (ns != null) && (ns.networkCapabilities != null) && (ns.linkProperties != null)
               && !isCellular(ns.networkCapabilities);
    }

    private static boolean isSystemVpnUsable(NetworkCapabilities nc) {
        return (nc != null)
                && appliesToRootUid(nc) // Only VPNs in system user apply to root UID
                && !nc.hasCapability(NET_CAPABILITY_NOT_VPN)
                && nc.hasCapability(NET_CAPABILITY_INTERNET);
    }

    private static boolean appliesToRootUid(NetworkCapabilities nc) {
        final Set<Range<Integer>> uids = nc.getUids();
        if (uids == null) return true;
        for (final Range<Integer> range : uids) {
            if (range.contains(Process.ROOT_UID)) return true;
        }
        return false;
    }

    private static UpstreamNetworkState findFirstDunNetwork(
            Iterable<UpstreamNetworkState> netStates) {
        for (UpstreamNetworkState ns : netStates) {
            if (isCellular(ns) && hasCapability(ns, NET_CAPABILITY_DUN)) return ns;
        }

        return null;
    }

    static boolean isTestNetwork(UpstreamNetworkState ns) {
        return ((ns != null) && (ns.networkCapabilities != null)
                && ns.networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_TEST));
    }

    private UpstreamNetworkState findFirstTestNetwork(
            Iterable<UpstreamNetworkState> netStates) {
        for (UpstreamNetworkState ns : netStates) {
            if (isTestNetwork(ns)) return ns;
        }

        return null;
    }

    /**
     * Given a legacy type (TYPE_WIFI, ...) returns the corresponding NetworkCapabilities instance.
     * This function is used for deprecated legacy type and be disabled by default.
     */
    @VisibleForTesting
    public static NetworkCapabilities networkCapabilitiesForType(int type) {
        final NetworkCapabilities.Builder builder = new NetworkCapabilities.Builder();

        // Map from type to transports.
        final int notFound = -1;
        final int transport = sLegacyTypeToTransport.get(type, notFound);
        if (transport == notFound) {
            throw new IllegalArgumentException("unknown legacy type: " + type);
        }
        builder.addTransportType(transport);

        if (type == TYPE_MOBILE_DUN) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_DUN);
            // DUN is restricted network, see NetworkCapabilities#FORCE_RESTRICTED_CAPABILITIES.
            builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        } else {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        return builder.build();
    }

    /** Set test network as preferred upstream. */
    public void setPreferTestNetworks(boolean prefer) {
        mPreferTestNetworks = prefer;
    }

    /** An interface to notify upstream network changes. */
    public interface EventListener {
        /** Notify the client of some event */
        void onUpstreamEvent(int what, Object obj);
    }
}
