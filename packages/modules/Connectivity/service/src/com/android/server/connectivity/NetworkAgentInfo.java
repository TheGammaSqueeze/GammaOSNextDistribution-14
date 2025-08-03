/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.connectivity;

import static android.net.ConnectivityDiagnosticsManager.ConnectivityReport;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_TEST;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.NetworkCapabilities.transportNamesOf;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.CaptivePortalData;
import android.net.DscpPolicy;
import android.net.IDnsResolver;
import android.net.INetd;
import android.net.INetworkAgent;
import android.net.INetworkAgentRegistry;
import android.net.INetworkMonitor;
import android.net.LinkProperties;
import android.net.LocalNetworkConfig;
import android.net.NattKeepalivePacketData;
import android.net.Network;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkMonitorManager;
import android.net.NetworkRequest;
import android.net.NetworkScore;
import android.net.NetworkStateSnapshot;
import android.net.QosCallbackException;
import android.net.QosFilter;
import android.net.QosFilterParcelable;
import android.net.QosSession;
import android.net.TcpKeepalivePacketData;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.telephony.data.EpsBearerQosSessionAttributes;
import android.telephony.data.NrQosSessionAttributes;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.WakeupMessage;
import com.android.server.ConnectivityService;

import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A bag class used by ConnectivityService for holding a collection of most recent
 * information published by a particular NetworkAgent as well as the
 * AsyncChannel/messenger for reaching that NetworkAgent and lists of NetworkRequests
 * interested in using it.  Default sort order is descending by score.
 */
// States of a network:
// --------------------
// 1. registered, uncreated, disconnected, unvalidated
//    This state is entered when a NetworkFactory registers a NetworkAgent in any state except
//    the CONNECTED state.
// 2. registered, uncreated, connecting, unvalidated
//    This state is entered when a registered NetworkAgent for a VPN network transitions to the
//    CONNECTING state (TODO: go through this state for every network, not just VPNs).
//    ConnectivityService will tell netd to create the network early in order to add extra UID
//    routing rules referencing the netID. These rules need to be in place before the network is
//    connected to avoid racing against client apps trying to connect to a half-setup network.
// 3. registered, uncreated, connected, unvalidated
//    This state is entered when a registered NetworkAgent transitions to the CONNECTED state.
//    ConnectivityService will tell netd to create the network if it was not already created, and
//    immediately transition to state #4.
// 4. registered, created, connected, unvalidated
//    If this network can satisfy the default NetworkRequest, then NetworkMonitor will
//    probe for Internet connectivity.
//    If this network cannot satisfy the default NetworkRequest, it will immediately be
//    transitioned to state #5.
//    A network may remain in this state if NetworkMonitor fails to find Internet connectivity,
//    for example:
//    a. a captive portal is present, or
//    b. a WiFi router whose Internet backhaul is down, or
//    c. a wireless connection stops transferring packets temporarily (e.g. device is in elevator
//       or tunnel) but does not disconnect from the AP/cell tower, or
//    d. a stand-alone device offering a WiFi AP without an uplink for configuration purposes.
// 5. registered, created, connected, validated
// 6. registered, created, connected, (validated or unvalidated), destroyed
//    This is an optional state where the underlying native network is destroyed but the network is
//    still connected for scoring purposes, so can satisfy requests, including the default request.
//    It is used when the transport layer wants to replace a network with another network (e.g.,
//    when Wi-Fi has roamed to a different BSSID that is part of a different L3 network) and does
//    not want the device to switch to another network until the replacement connects and validates.
//
// The device's default network connection:
// ----------------------------------------
// Networks in states #4 and #5 may be used as a device's default network connection if they
// satisfy the default NetworkRequest.
// A network, that satisfies the default NetworkRequest, in state #5 should always be chosen
// in favor of a network, that satisfies the default NetworkRequest, in state #4.
// When deciding between two networks, that both satisfy the default NetworkRequest, to select
// for the default network connection, the one with the higher score should be chosen.
//
// When a network disconnects:
// ---------------------------
// If a network's transport disappears, for example:
// a. WiFi turned off, or
// b. cellular data turned off, or
// c. airplane mode is turned on, or
// d. a wireless connection disconnects from AP/cell tower entirely (e.g. device is out of range
//    of AP for an extended period of time, or switches to another AP without roaming)
// then that network can transition from any state (#1-#5) to unregistered.  This happens by
// the transport disconnecting their NetworkAgent's AsyncChannel with ConnectivityManager.
// ConnectivityService also tells netd to destroy the network.
//
// When ConnectivityService disconnects a network:
// -----------------------------------------------
// If a network is just connected, ConnectivityService will think it will be used soon, but might
// not be used. Thus, a 5s timer will be held to prevent the network being torn down immediately.
// This "nascent" state is implemented by the "lingering" logic below without relating to any
// request, and is used in some cases where network requests race with network establishment. The
// nascent state ends when the 5-second timer fires, or as soon as the network satisfies a
// request, whichever is earlier. In this state, the network is considered in the background.
//
// If a network has no chance of satisfying any requests (even if it were to become validated
// and enter state #5), ConnectivityService will disconnect the NetworkAgent's AsyncChannel.
//
// If the network was satisfying a foreground NetworkRequest (i.e. had been the highest scoring that
// satisfied the NetworkRequest's constraints), but is no longer the highest scoring network for any
// foreground NetworkRequest, then there will be a 30s pause to allow network communication to be
// wrapped up rather than abruptly terminated. During this pause the network is said to be
// "lingering". During this pause if the network begins satisfying a foreground NetworkRequest,
// ConnectivityService will cancel the future disconnection of the NetworkAgent's AsyncChannel, and
// the network is no longer considered "lingering". After the linger timer expires, if the network
// is satisfying one or more background NetworkRequests it is kept up in the background. If it is
// not, ConnectivityService disconnects the NetworkAgent's AsyncChannel.
public class NetworkAgentInfo implements NetworkRanker.Scoreable {

    @NonNull public NetworkInfo networkInfo;
    // This Network object should always be used if possible, so as to encourage reuse of the
    // enclosed socket factory and connection pool.  Avoid creating other Network objects.
    // This Network object is always valid.
    @NonNull public final Network network;
    @NonNull public LinkProperties linkProperties;
    // This should only be modified by ConnectivityService, via setNetworkCapabilities().
    // TODO: make this private with a getter.
    @NonNull public NetworkCapabilities networkCapabilities;
    @NonNull public final NetworkAgentConfig networkAgentConfig;
    @Nullable public LocalNetworkConfig localNetworkConfig;

    // Underlying networks declared by the agent.
    // The networks in this list might be declared by a VPN using setUnderlyingNetworks and are
    // not guaranteed to be current or correct, or even to exist.
    //
    // This array is read and iterated on multiple threads with no locking so its contents must
    // never be modified. When the list of networks changes, replace with a new array, on the
    // handler thread.
    public @Nullable volatile Network[] declaredUnderlyingNetworks;

    // The capabilities originally announced by the NetworkAgent, regardless of any capabilities
    // that were added or removed due to this network's underlying networks.
    //
    // As the name implies, these capabilities are not sanitized and are not to
    // be trusted. Most callers should simply use the {@link networkCapabilities}
    // field instead.
    private @Nullable NetworkCapabilities mDeclaredCapabilitiesUnsanitized;

    // Timestamp (SystemClock.elapsedRealtime()) when netd has been told to create this Network, or
    // 0 if it hasn't been done yet.
    // From this point on, the appropriate routing rules are setup and routes are added so packets
    // can begin flowing over the Network.
    // This is a sticky value; once set != 0 it is never changed.
    private long mCreatedTime;

    /** Notify this NAI that netd was just told to create this network */
    public void setCreated() {
        if (0L != mCreatedTime) throw new IllegalStateException("Already created");
        mCreatedTime = SystemClock.elapsedRealtime();
    }

    /** Returns whether netd was told to create this network */
    public boolean isCreated() {
        return mCreatedTime != 0L;
    }

    // Get the time (SystemClock.elapsedRealTime) when this network was created (or 0 if never).
    public long getCreatedTime() {
        return mCreatedTime;
    }

    // Timestamp of the first time (SystemClock.elapsedRealtime()) this network is marked as
    // connected, or 0 if this network has never been marked connected. Once set to non-zero, the
    // network shows up in API calls, is able to satisfy NetworkRequests and can become the default
    // network.
    // This is a sticky value; once set != 0 it is never changed.
    private long mConnectedTime;

    /** Notify this NAI that this network just connected */
    public void setConnected() {
        if (0L != mConnectedTime) throw new IllegalStateException("Already connected");
        mConnectedTime = SystemClock.elapsedRealtime();
    }

    /** Return whether this network ever connected */
    public boolean everConnected() {
        return mConnectedTime != 0L;
    }

    // Get the time (SystemClock.elapsedRealTime()) when this network was first connected, or 0 if
    // never.
    public long getConnectedTime() {
        return mConnectedTime;
    }

    // When this network has been destroyed and is being kept temporarily until it is replaced,
    // this is set to that timestamp (SystemClock.elapsedRealtime()). Zero otherwise.
    private long mDestroyedTime;

    /** Notify this NAI that this network was destroyed */
    public void setDestroyed() {
        if (0L != mDestroyedTime) throw new IllegalStateException("Already destroyed");
        mDestroyedTime = SystemClock.elapsedRealtime();
    }

    /** Return whether this network was destroyed */
    public boolean isDestroyed() {
        return 0L != mDestroyedTime;
    }

    // Timestamp of the last roaming (SystemClock.elapsedRealtime()) or 0 if never roamed.
    public long lastRoamTime;

    // Timestamp (SystemClock.elapsedRealtime()) of the first time this network successfully
    // passed validation or was deemed exempt of validation (see
    // {@link NetworkMonitorUtils#isValidationRequired}). Zero if the network requires
    // validation but never passed it successfully.
    // This is a sticky value; once set it is never changed even if further validation attempts are
    // made (whether they succeed or fail).
    private long mFirstValidationTime;

    // Timestamp (SystemClock.elapsedRealtime()) at which the latest validation attempt succeeded,
    // or 0 if the latest validation attempt failed.
    private long mCurrentValidationTime;

    /** Notify this NAI that this network just finished a validation check */
    public void setValidated(final boolean validated) {
        final long nowOrZero = validated ? SystemClock.elapsedRealtime() : 0L;
        if (validated && 0L == mFirstValidationTime) {
            mFirstValidationTime = nowOrZero;
        }
        mCurrentValidationTime = nowOrZero;
    }

    /**
     * Returns whether this network is currently validated.
     *
     * This is the result of the latest validation check. {@see #getCurrentValidationTime} for
     * when that check was performed.
     */
    public boolean isValidated() {
        return 0L != mCurrentValidationTime;
    }

    /**
     * Returns whether this network ever passed the validation checks successfully.
     *
     * Note that the network may no longer be validated at this time ever if this is true.
     * @see #isValidated
     */
    public boolean everValidated() {
        return 0L != mFirstValidationTime;
    }

    // Get the time (SystemClock.elapsedRealTime()) when this network was most recently validated,
    // or 0 if this network was found not to validate on the last attempt.
    public long getCurrentValidationTime() {
        return mCurrentValidationTime;
    }

    // Get the time (SystemClock.elapsedRealTime()) when this network was validated for the first
    // time (or 0 if never).
    public long getFirstValidationTime() {
        return mFirstValidationTime;
    }

    // Timestamp (SystemClock.elapsedRealtime()) at which the user requested this network be
    // avoided when unvalidated. Zero if this never happened for this network.
    // This is only meaningful if the system is configured to have some cell networks yield
    // to bad wifi, e.g., if the config_networkAvoidBadWifi option is set to 0 and the user has
    // not overridden that via Settings.Global.NETWORK_AVOID_BAD_WIFI.
    //
    // Normally the system always prefers a validated network to a non-validated one, even if
    // the non-validated one is cheaper. However, some cell networks may be configured by the
    // setting above to yield to WiFi even if that WiFi network goes bad. When this configuration
    // is active, specific networks can be marked to override this configuration so that the
    // system will revert to preferring such a cell to this network when this network goes bad. This
    // is achieved by calling {@link ConnectivityManager#setAvoidUnvalidated()}, and this field
    // is set to non-zero when this happened to this network.
    private long mAvoidUnvalidated;

    /** Set this network as being avoided when unvalidated. {@see mAvoidUnvalidated} */
    public void setAvoidUnvalidated() {
        if (0L != mAvoidUnvalidated) throw new IllegalStateException("Already avoided unvalidated");
        mAvoidUnvalidated = SystemClock.elapsedRealtime();
    }

    // Get the time (SystemClock.elapsedRealTime()) when this network was set to being avoided
    // when unvalidated, or 0 if this never happened.
    public long getAvoidUnvalidated() {
        return mAvoidUnvalidated;
    }

    // Timestamp (SystemClock.elapsedRealtime()) at which a captive portal was first detected
    // on this network, or zero if this never happened.
    // This is a sticky value; once set != 0 it is never changed.
    private long mFirstCaptivePortalDetectedTime;

    // Timestamp (SystemClock.elapsedRealtime()) at which the latest validation attempt found a
    // captive portal, or zero if the latest attempt didn't find a captive portal.
    private long mCurrentCaptivePortalDetectedTime;

    /** Notify this NAI that a captive portal has just been detected on this network */
    public void setCaptivePortalDetected(final boolean hasCaptivePortal) {
        if (!hasCaptivePortal) {
            mCurrentCaptivePortalDetectedTime = 0L;
            return;
        }
        final long now = SystemClock.elapsedRealtime();
        if (0L == mFirstCaptivePortalDetectedTime) mFirstCaptivePortalDetectedTime = now;
        mCurrentCaptivePortalDetectedTime = now;
    }

    /** Return whether a captive portal has ever been detected on this network */
    public boolean everCaptivePortalDetected() {
        return 0L != mFirstCaptivePortalDetectedTime;
    }

    /** Return whether this network has been detected to be behind a captive portal at the moment */
    public boolean captivePortalDetected() {
        return 0L != mCurrentCaptivePortalDetectedTime;
    }

    // Timestamp (SystemClock.elapsedRealtime()) at which the latest validation attempt found
    // partial connectivity, or zero if the latest attempt didn't find partial connectivity.
    private long mPartialConnectivityTime;

    public void setPartialConnectivity(final boolean value) {
        mPartialConnectivityTime = value ? SystemClock.elapsedRealtime() : 0L;
    }

    /** Return whether this NAI has partial connectivity */
    public boolean partialConnectivity() {
        return 0L != mPartialConnectivityTime;
    }

    // Timestamp (SystemClock.elapsedRealTime()) at which the first validation attempt concluded,
    // or timed out after {@link ConnectivityService#PROMPT_UNVALIDATED_DELAY_MS}. 0 if not yet.
    private long mFirstEvaluationConcludedTime;

    /**
     * Notify this NAI that this network has been evaluated.
     *
     * The stack considers that any result finding some working connectivity (valid, partial,
     * captive portal) is an initial validation. Negative result (not valid), however, is not
     * considered initial validation until {@link ConnectivityService#PROMPT_UNVALIDATED_DELAY_MS}
     * have elapsed. This is because some networks may spuriously fail for a short time immediately
     * after associating. If no positive result is found after the timeout has elapsed, then
     * the network has been evaluated once.
     *
     * @return true the first time this is called on this object, then always returns false.
     */
    public boolean setEvaluated() {
        if (0L != mFirstEvaluationConcludedTime) return false;
        mFirstEvaluationConcludedTime = SystemClock.elapsedRealtime();
        return true;
    }

    /** When this network ever concluded its first evaluation, or 0 if this never happened. */
    @VisibleForTesting
    public long getFirstEvaluationConcludedTime() {
        return mFirstEvaluationConcludedTime;
    }

    // Delay between when the network is disconnected and when the native network is destroyed.
    public int teardownDelayMs;

    // Captive portal info of the network from RFC8908, if any.
    // Obtained by ConnectivityService and merged into NetworkAgent-provided information.
    public CaptivePortalData capportApiData;

    // The UID of the remote entity that created this Network.
    public final int creatorUid;

    // Network agent portal info of the network, if any. This information is provided from
    // non-RFC8908 sources, such as Wi-Fi Passpoint, which can provide information such as Venue
    // URL, Terms & Conditions URL, and network friendly name.
    public CaptivePortalData networkAgentPortalData;

    // Indicate whether this device has the automotive feature.
    private final boolean mHasAutomotiveFeature;

    /**
     * Checks that a proposed update to the NCs of this NAI satisfies structural constraints.
     *
     * Some changes to NetworkCapabilities are structurally not supported by the stack, and
     * NetworkAgents are absolutely never allowed to try and do them. When one of these is
     * violated, this method returns false, which has ConnectivityService disconnect the network ;
     * this is meant to guarantee that no implementor ever tries to do this.
     */
    public boolean respectsNcStructuralConstraints(@NonNull final NetworkCapabilities proposedNc) {
        if (networkCapabilities.hasCapability(NET_CAPABILITY_LOCAL_NETWORK)
                != proposedNc.hasCapability(NET_CAPABILITY_LOCAL_NETWORK)) {
            return false;
        }
        return true;
    }

    /**
     * Sets the capabilities sent by the agent for later retrieval.
     * <p>
     * This method does not sanitize the capabilities before storing them ; instead, use
     * {@link #getDeclaredCapabilitiesSanitized} to retrieve a sanitized copy of the capabilities
     * as they were passed here.
     * <p>
     * This method makes a defensive copy to avoid issues where the passed object is later mutated.
     *
     * @param caps the caps sent by the agent
     */
    public void setDeclaredCapabilities(@NonNull final NetworkCapabilities caps) {
        mDeclaredCapabilitiesUnsanitized = new NetworkCapabilities(caps);
    }

    /**
     * Get the latest capabilities sent by the network agent, after sanitizing them.
     *
     * These are the capabilities as they were sent by the agent (but sanitized to conform to
     * their restrictions). They are NOT the capabilities currently applying to this agent ;
     * for that, use {@link #networkCapabilities}.
     *
     * Agents have restrictions on what capabilities they can send to Connectivity. For example,
     * they can't change the owner UID from what they declared before, and complex restrictions
     * apply to the allowedUids field.
     * They also should not mutate immutable capabilities, although for backward-compatibility
     * this is not enforced and limited to just a log.
     * Forbidden capabilities also make no sense for networks, so they are disallowed and
     * will be ignored with a warning.
     *
     * @param carrierPrivilegeAuthenticator the authenticator, to check access UIDs.
     */
    public NetworkCapabilities getDeclaredCapabilitiesSanitized(
            final CarrierPrivilegeAuthenticator carrierPrivilegeAuthenticator) {
        final NetworkCapabilities nc = new NetworkCapabilities(mDeclaredCapabilitiesUnsanitized);
        if (nc.hasConnectivityManagedCapability()) {
            Log.wtf(TAG, "BUG: " + this + " has CS-managed capability.");
            nc.removeAllForbiddenCapabilities();
        }
        if (networkCapabilities.getOwnerUid() != nc.getOwnerUid()) {
            Log.e(TAG, toShortString() + ": ignoring attempt to change owner from "
                    + networkCapabilities.getOwnerUid() + " to " + nc.getOwnerUid());
            nc.setOwnerUid(networkCapabilities.getOwnerUid());
        }
        restrictCapabilitiesFromNetworkAgent(nc, creatorUid, mHasAutomotiveFeature,
                mConnServiceDeps, carrierPrivilegeAuthenticator);
        return nc;
    }

    // Networks are lingered when they become unneeded as a result of their NetworkRequests being
    // satisfied by a higher-scoring network. so as to allow communication to wrap up before the
    // network is taken down.  This usually only happens to the default network. Lingering ends with
    // either the linger timeout expiring and the network being taken down, or the network
    // satisfying a request again.
    public static class InactivityTimer implements Comparable<InactivityTimer> {
        public final int requestId;
        public final long expiryMs;

        public InactivityTimer(int requestId, long expiryMs) {
            this.requestId = requestId;
            this.expiryMs = expiryMs;
        }
        public boolean equals(Object o) {
            if (!(o instanceof InactivityTimer)) return false;
            InactivityTimer other = (InactivityTimer) o;
            return (requestId == other.requestId) && (expiryMs == other.expiryMs);
        }
        public int hashCode() {
            return Objects.hash(requestId, expiryMs);
        }
        public int compareTo(InactivityTimer other) {
            return (expiryMs != other.expiryMs) ?
                    Long.compare(expiryMs, other.expiryMs) :
                    Integer.compare(requestId, other.requestId);
        }
        public String toString() {
            return String.format("%s, expires %dms", requestId,
                    expiryMs - SystemClock.elapsedRealtime());
        }
    }

    /**
     * Inform ConnectivityService that the network LINGER period has
     * expired.
     * obj = this NetworkAgentInfo
     */
    public static final int EVENT_NETWORK_LINGER_COMPLETE = 1001;

    /**
     * Inform ConnectivityService that the agent is half-connected.
     * arg1 = ARG_AGENT_SUCCESS or ARG_AGENT_FAILURE
     * obj = NetworkAgentInfo
     * @hide
     */
    public static final int EVENT_AGENT_REGISTERED = 1002;

    /**
     * Inform ConnectivityService that the agent was disconnected.
     * obj = NetworkAgentInfo
     * @hide
     */
    public static final int EVENT_AGENT_DISCONNECTED = 1003;

    /**
     * Argument for EVENT_AGENT_HALF_CONNECTED indicating failure.
     */
    public static final int ARG_AGENT_FAILURE = 0;

    /**
     * Argument for EVENT_AGENT_HALF_CONNECTED indicating success.
     */
    public static final int ARG_AGENT_SUCCESS = 1;

    // How long this network should linger for.
    private int mLingerDurationMs;

    // All inactivity timers for this network, sorted by expiry time. A timer is added whenever
    // a request is moved to a network with a better score, regardless of whether the network is or
    // was lingering or not. An inactivity timer is also added when a network connects
    // without immediately satisfying any requests.
    // TODO: determine if we can replace this with a smaller or unsorted data structure. (e.g.,
    // SparseLongArray) combined with the timestamp of when the last timer is scheduled to fire.
    private final SortedSet<InactivityTimer> mInactivityTimers = new TreeSet<>();

    // For fast lookups. Indexes into mInactivityTimers by request ID.
    private final SparseArray<InactivityTimer> mInactivityTimerForRequest = new SparseArray<>();

    // Inactivity expiry timer. Armed whenever mInactivityTimers is non-empty, regardless of
    // whether the network is inactive or not. Always set to the expiry of the mInactivityTimers
    // that expires last. When the timer fires, all inactivity state is cleared, and if the network
    // has no requests, it is torn down.
    private WakeupMessage mInactivityMessage;

    // Inactivity expiry. Holds the expiry time of the inactivity timer, or 0 if the timer is not
    // armed.
    private long mInactivityExpiryMs;

    // Whether the network is inactive or not. Must be maintained separately from the above because
    // it depends on the state of other networks and requests, which only ConnectivityService knows.
    // (Example: we don't linger a network if it would become the best for a NetworkRequest if it
    // validated).
    private boolean mInactive;

    // This represents the quality of the network. As opposed to NetworkScore, FullScore includes
    // the ConnectivityService-managed bits.
    private FullScore mScore;

    // The list of NetworkRequests being satisfied by this Network.
    private final SparseArray<NetworkRequest> mNetworkRequests = new SparseArray<>();

    // How many of the satisfied requests are actual requests and not listens.
    private int mNumRequestNetworkRequests = 0;

    // How many of the satisfied requests are of type BACKGROUND_REQUEST.
    private int mNumBackgroundNetworkRequests = 0;

    // The last ConnectivityReport made available for this network. This value is only null before a
    // report is generated. Once non-null, it will never be null again.
    @Nullable private ConnectivityReport mConnectivityReport;

    public final INetworkAgent networkAgent;
    // Only accessed from ConnectivityService handler thread
    private final AgentDeathMonitor mDeathMonitor = new AgentDeathMonitor();

    public final int factorySerialNumber;

    // Used by ConnectivityService to keep track of 464xlat.
    public final Nat464Xlat clatd;

    // Set after asynchronous creation of the NetworkMonitor.
    private volatile NetworkMonitorManager mNetworkMonitor;

    private static final String TAG = ConnectivityService.class.getSimpleName();
    private static final boolean VDBG = false;
    private final ConnectivityService mConnService;
    private final ConnectivityService.Dependencies mConnServiceDeps;
    private final Context mContext;
    private final Handler mHandler;
    private final QosCallbackTracker mQosCallbackTracker;

    private final long mCreationTime;

    public NetworkAgentInfo(INetworkAgent na, Network net, NetworkInfo info,
            @NonNull LinkProperties lp, @NonNull NetworkCapabilities nc,
            @Nullable LocalNetworkConfig localNetworkConfig,
            @NonNull NetworkScore score, Context context,
            Handler handler, NetworkAgentConfig config, ConnectivityService connService, INetd netd,
            IDnsResolver dnsResolver, int factorySerialNumber, int creatorUid,
            int lingerDurationMs, QosCallbackTracker qosCallbackTracker,
            ConnectivityService.Dependencies deps) {
        Objects.requireNonNull(net);
        Objects.requireNonNull(info);
        Objects.requireNonNull(lp);
        Objects.requireNonNull(nc);
        Objects.requireNonNull(context);
        Objects.requireNonNull(config);
        Objects.requireNonNull(qosCallbackTracker);
        networkAgent = na;
        network = net;
        networkInfo = info;
        linkProperties = lp;
        networkCapabilities = nc;
        this.localNetworkConfig = localNetworkConfig;
        networkAgentConfig = config;
        mConnService = connService;
        mConnServiceDeps = deps;
        setScore(score); // uses members connService, networkCapabilities and networkAgentConfig
        clatd = new Nat464Xlat(this, netd, dnsResolver, deps);
        mContext = context;
        mHandler = handler;
        this.factorySerialNumber = factorySerialNumber;
        this.creatorUid = creatorUid;
        mLingerDurationMs = lingerDurationMs;
        mQosCallbackTracker = qosCallbackTracker;
        declaredUnderlyingNetworks = (nc.getUnderlyingNetworks() != null)
                ? nc.getUnderlyingNetworks().toArray(new Network[0])
                : null;
        mCreationTime = System.currentTimeMillis();
        mHasAutomotiveFeature =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private class AgentDeathMonitor implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
            notifyDisconnected();
        }
    }

    /**
     * Notify the NetworkAgent that it was registered, and should be unregistered if it dies.
     *
     * Must be called from the ConnectivityService handler thread. A NetworkAgent can only be
     * registered once.
     */
    public void notifyRegistered() {
        try {
            networkAgent.asBinder().linkToDeath(mDeathMonitor, 0);
            networkAgent.onRegistered(new NetworkAgentMessageHandler(mHandler));
        } catch (RemoteException e) {
            Log.e(TAG, "Error registering NetworkAgent", e);
            maybeUnlinkDeathMonitor();
            mHandler.obtainMessage(EVENT_AGENT_REGISTERED, ARG_AGENT_FAILURE, 0, this)
                    .sendToTarget();
            return;
        }

        mHandler.obtainMessage(EVENT_AGENT_REGISTERED, ARG_AGENT_SUCCESS, 0, this).sendToTarget();
    }

    /**
     * Disconnect the NetworkAgent. Must be called from the ConnectivityService handler thread.
     */
    public void disconnect() {
        try {
            networkAgent.onDisconnected();
        } catch (RemoteException e) {
            Log.i(TAG, "Error disconnecting NetworkAgent", e);
            // Fall through: it's fine if the remote has died
        }

        notifyDisconnected();
        maybeUnlinkDeathMonitor();
    }

    private void maybeUnlinkDeathMonitor() {
        try {
            networkAgent.asBinder().unlinkToDeath(mDeathMonitor, 0);
        } catch (NoSuchElementException e) {
            // Was not linked: ignore
        }
    }

    private void notifyDisconnected() {
        // Note this may be called multiple times if ConnectivityService disconnects while the
        // NetworkAgent also dies. ConnectivityService ignores disconnects of already disconnected
        // agents.
        mHandler.obtainMessage(EVENT_AGENT_DISCONNECTED, this).sendToTarget();
    }

    /**
     * Notify the NetworkAgent that bandwidth update was requested.
     */
    public void onBandwidthUpdateRequested() {
        try {
            networkAgent.onBandwidthUpdateRequested();
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending bandwidth update request event", e);
        }
    }

    /**
     * Notify the NetworkAgent that validation status has changed.
     */
    public void onValidationStatusChanged(int validationStatus, @Nullable String captivePortalUrl) {
        try {
            networkAgent.onValidationStatusChanged(validationStatus, captivePortalUrl);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending validation status change event", e);
        }
    }

    /**
     * Notify the NetworkAgent that the acceptUnvalidated setting should be saved.
     */
    public void onSaveAcceptUnvalidated(boolean acceptUnvalidated) {
        try {
            networkAgent.onSaveAcceptUnvalidated(acceptUnvalidated);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending accept unvalidated event", e);
        }
    }

    /**
     * Notify the NetworkAgent that NATT socket keepalive should be started.
     */
    public void onStartNattSocketKeepalive(int slot, int intervalDurationMs,
            @NonNull NattKeepalivePacketData packetData) {
        try {
            networkAgent.onStartNattSocketKeepalive(slot, intervalDurationMs, packetData);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending NATT socket keepalive start event", e);
        }
    }

    /**
     * Notify the NetworkAgent that TCP socket keepalive should be started.
     */
    public void onStartTcpSocketKeepalive(int slot, int intervalDurationMs,
            @NonNull TcpKeepalivePacketData packetData) {
        try {
            networkAgent.onStartTcpSocketKeepalive(slot, intervalDurationMs, packetData);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending TCP socket keepalive start event", e);
        }
    }

    /**
     * Notify the NetworkAgent that socket keepalive should be stopped.
     */
    public void onStopSocketKeepalive(int slot) {
        try {
            networkAgent.onStopSocketKeepalive(slot);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending TCP socket keepalive stop event", e);
        }
    }

    /**
     * Notify the NetworkAgent that signal strength thresholds should be updated.
     */
    public void onSignalStrengthThresholdsUpdated(@NonNull int[] thresholds) {
        try {
            networkAgent.onSignalStrengthThresholdsUpdated(thresholds);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending signal strength thresholds event", e);
        }
    }

    /**
     * Notify the NetworkAgent that automatic reconnect should be prevented.
     */
    public void onPreventAutomaticReconnect() {
        try {
            networkAgent.onPreventAutomaticReconnect();
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending prevent automatic reconnect event", e);
        }
    }

    /**
     * Notify the NetworkAgent that a NATT keepalive packet filter should be added.
     */
    public void onAddNattKeepalivePacketFilter(int slot,
            @NonNull NattKeepalivePacketData packetData) {
        try {
            networkAgent.onAddNattKeepalivePacketFilter(slot, packetData);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending add NATT keepalive packet filter event", e);
        }
    }

    /**
     * Notify the NetworkAgent that a TCP keepalive packet filter should be added.
     */
    public void onAddTcpKeepalivePacketFilter(int slot,
            @NonNull TcpKeepalivePacketData packetData) {
        try {
            networkAgent.onAddTcpKeepalivePacketFilter(slot, packetData);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending add TCP keepalive packet filter event", e);
        }
    }

    /**
     * Notify the NetworkAgent that a keepalive packet filter should be removed.
     */
    public void onRemoveKeepalivePacketFilter(int slot) {
        try {
            networkAgent.onRemoveKeepalivePacketFilter(slot);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending remove keepalive packet filter event", e);
        }
    }

    /**
     * Notify the NetworkAgent that the qos filter should be registered against the given qos
     * callback id.
     */
    public void onQosFilterCallbackRegistered(final int qosCallbackId,
            final QosFilter qosFilter) {
        try {
            networkAgent.onQosFilterCallbackRegistered(qosCallbackId,
                    new QosFilterParcelable(qosFilter));
        } catch (final RemoteException e) {
            Log.e(TAG, "Error registering a qos callback id against a qos filter", e);
        }
    }

    /**
     * Notify the NetworkAgent that the given qos callback id should be unregistered.
     */
    public void onQosCallbackUnregistered(final int qosCallbackId) {
        try {
            networkAgent.onQosCallbackUnregistered(qosCallbackId);
        } catch (RemoteException e) {
            Log.e(TAG, "Error unregistering a qos callback id", e);
        }
    }

    /**
     * Notify the NetworkAgent that the network is successfully connected.
     */
    public void onNetworkCreated() {
        try {
            networkAgent.onNetworkCreated();
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending network created event", e);
        }
    }

    /**
     * Notify the NetworkAgent that the native network has been destroyed.
     */
    public void onNetworkDestroyed() {
        try {
            networkAgent.onNetworkDestroyed();
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending network destroyed event", e);
        }
    }

    // TODO: consider moving out of NetworkAgentInfo into its own class
    private class NetworkAgentMessageHandler extends INetworkAgentRegistry.Stub {
        private final Handler mHandler;

        private NetworkAgentMessageHandler(Handler handler) {
            mHandler = handler;
        }

        @Override
        public void sendNetworkCapabilities(@NonNull NetworkCapabilities nc) {
            Objects.requireNonNull(nc);
            mHandler.obtainMessage(NetworkAgent.EVENT_NETWORK_CAPABILITIES_CHANGED,
                    new Pair<>(NetworkAgentInfo.this, nc)).sendToTarget();
        }

        @Override
        public void sendLinkProperties(@NonNull LinkProperties lp) {
            Objects.requireNonNull(lp);
            mHandler.obtainMessage(NetworkAgent.EVENT_NETWORK_PROPERTIES_CHANGED,
                    new Pair<>(NetworkAgentInfo.this, lp)).sendToTarget();
        }

        @Override
        public void sendNetworkInfo(@NonNull NetworkInfo info) {
            Objects.requireNonNull(info);
            mHandler.obtainMessage(NetworkAgent.EVENT_NETWORK_INFO_CHANGED,
                    new Pair<>(NetworkAgentInfo.this, info)).sendToTarget();
        }

        @Override
        public void sendLocalNetworkConfig(@NonNull final LocalNetworkConfig config) {
            mHandler.obtainMessage(NetworkAgent.EVENT_LOCAL_NETWORK_CONFIG_CHANGED,
                    new Pair<>(NetworkAgentInfo.this, config)).sendToTarget();
        }

        @Override
        public void sendScore(@NonNull final NetworkScore score) {
            mHandler.obtainMessage(NetworkAgent.EVENT_NETWORK_SCORE_CHANGED,
                    new Pair<>(NetworkAgentInfo.this, score)).sendToTarget();
        }

        @Override
        public void sendExplicitlySelected(boolean explicitlySelected, boolean acceptPartial) {
            mHandler.obtainMessage(NetworkAgent.EVENT_SET_EXPLICITLY_SELECTED,
                    explicitlySelected ? 1 : 0, acceptPartial ? 1 : 0,
                    new Pair<>(NetworkAgentInfo.this, null)).sendToTarget();
        }

        @Override
        public void sendSocketKeepaliveEvent(int slot, int reason) {
            mHandler.obtainMessage(NetworkAgent.EVENT_SOCKET_KEEPALIVE,
                    slot, reason, new Pair<>(NetworkAgentInfo.this, null)).sendToTarget();
        }

        @Override
        public void sendUnderlyingNetworks(@Nullable List<Network> networks) {
            mHandler.obtainMessage(NetworkAgent.EVENT_UNDERLYING_NETWORKS_CHANGED,
                    new Pair<>(NetworkAgentInfo.this, networks)).sendToTarget();
        }

        @Override
        public void sendEpsQosSessionAvailable(final int qosCallbackId, final QosSession session,
                final EpsBearerQosSessionAttributes attributes) {
            mQosCallbackTracker.sendEventEpsQosSessionAvailable(qosCallbackId, session, attributes);
        }

        @Override
        public void sendNrQosSessionAvailable(final int qosCallbackId, final QosSession session,
                final NrQosSessionAttributes attributes) {
            mQosCallbackTracker.sendEventNrQosSessionAvailable(qosCallbackId, session, attributes);
        }

        @Override
        public void sendQosSessionLost(final int qosCallbackId, final QosSession session) {
            mQosCallbackTracker.sendEventQosSessionLost(qosCallbackId, session);
        }

        @Override
        public void sendQosCallbackError(final int qosCallbackId,
                @QosCallbackException.ExceptionType final int exceptionType) {
            mQosCallbackTracker.sendEventQosCallbackError(qosCallbackId, exceptionType);
        }

        @Override
        public void sendTeardownDelayMs(int teardownDelayMs) {
            mHandler.obtainMessage(NetworkAgent.EVENT_TEARDOWN_DELAY_CHANGED,
                    teardownDelayMs, 0, new Pair<>(NetworkAgentInfo.this, null)).sendToTarget();
        }

        @Override
        public void sendLingerDuration(final int durationMs) {
            mHandler.obtainMessage(NetworkAgent.EVENT_LINGER_DURATION_CHANGED,
                    new Pair<>(NetworkAgentInfo.this, durationMs)).sendToTarget();
        }

        @Override
        public void sendAddDscpPolicy(final DscpPolicy policy) {
            mHandler.obtainMessage(NetworkAgent.EVENT_ADD_DSCP_POLICY,
                    new Pair<>(NetworkAgentInfo.this, policy)).sendToTarget();
        }

        @Override
        public void sendRemoveDscpPolicy(final int policyId) {
            mHandler.obtainMessage(NetworkAgent.EVENT_REMOVE_DSCP_POLICY,
                    new Pair<>(NetworkAgentInfo.this, policyId)).sendToTarget();
        }

        @Override
        public void sendRemoveAllDscpPolicies() {
            mHandler.obtainMessage(NetworkAgent.EVENT_REMOVE_ALL_DSCP_POLICIES,
                    new Pair<>(NetworkAgentInfo.this, null)).sendToTarget();
        }

        @Override
        public void sendUnregisterAfterReplacement(final int timeoutMillis) {
            mHandler.obtainMessage(NetworkAgent.EVENT_UNREGISTER_AFTER_REPLACEMENT,
                    new Pair<>(NetworkAgentInfo.this, timeoutMillis)).sendToTarget();
        }
    }

    /**
     * Inform NetworkAgentInfo that a new NetworkMonitor was created.
     */
    public void onNetworkMonitorCreated(INetworkMonitor networkMonitor) {
        mNetworkMonitor = new NetworkMonitorManager(networkMonitor);
    }

    /**
     * Set the NetworkCapabilities on this NetworkAgentInfo. Also attempts to notify NetworkMonitor
     * of the new capabilities, if NetworkMonitor has been created.
     *
     * <p>If {@link NetworkMonitor#notifyNetworkCapabilitiesChanged(NetworkCapabilities)} fails,
     * the exception is logged but not reported to callers.
     *
     * @return the old capabilities of this network.
     */
    @NonNull public synchronized NetworkCapabilities getAndSetNetworkCapabilities(
            @NonNull final NetworkCapabilities nc) {
        final NetworkCapabilities oldNc = networkCapabilities;
        networkCapabilities = nc;
        updateScoreForNetworkAgentUpdate();
        final NetworkMonitorManager nm = mNetworkMonitor;
        if (nm != null) {
            nm.notifyNetworkCapabilitiesChanged(nc);
        }
        return oldNc;
    }

    private boolean yieldToBadWiFi() {
        // Only cellular networks yield to bad wifi
        return networkCapabilities.hasTransport(TRANSPORT_CELLULAR) && !mConnService.avoidBadWifi();
    }

    public ConnectivityService connService() {
        return mConnService;
    }

    public NetworkAgentConfig netAgentConfig() {
        return networkAgentConfig;
    }

    public Handler handler() {
        return mHandler;
    }

    public Network network() {
        return network;
    }

    /**
     * Get the generated v6 address of clat.
     */
    @Nullable
    public Inet6Address getClatv6SrcAddress() {
        return clatd.getClatv6SrcAddress();
    }

    /**
     * Get the generated v4 address of clat.
     */
    @Nullable
    public Inet4Address getClatv4SrcAddress() {
        return clatd.getClatv4SrcAddress();
    }

    /**
     * Translate the input v4 address to v6 clat address.
     */
    @Nullable
    public Inet6Address translateV4toClatV6(@NonNull Inet4Address addr) {
        return clatd.translateV4toV6(addr);
    }

    /**
     * Get the NetworkMonitorManager in this NetworkAgentInfo.
     *
     * <p>This will be null before {@link #onNetworkMonitorCreated(INetworkMonitor)} is called.
     */
    public NetworkMonitorManager networkMonitor() {
        return mNetworkMonitor;
    }

    // Functions for manipulating the requests satisfied by this network.
    //
    // These functions must only called on ConnectivityService's main thread.

    private static final boolean ADD = true;
    private static final boolean REMOVE = false;

    private void updateRequestCounts(boolean add, NetworkRequest request) {
        int delta = add ? +1 : -1;
        switch (request.type) {
            case REQUEST:
                mNumRequestNetworkRequests += delta;
                break;

            case BACKGROUND_REQUEST:
                mNumRequestNetworkRequests += delta;
                mNumBackgroundNetworkRequests += delta;
                break;

            case LISTEN:
            case LISTEN_FOR_BEST:
            case TRACK_DEFAULT:
            case TRACK_SYSTEM_DEFAULT:
                break;

            case NONE:
            default:
                Log.wtf(TAG, "Unhandled request type " + request.type);
                break;
        }
    }

    /**
     * Add {@code networkRequest} to this network as it's satisfied by this network.
     * @return true if {@code networkRequest} was added or false if {@code networkRequest} was
     *         already present.
     */
    public boolean addRequest(NetworkRequest networkRequest) {
        if (mHandler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException(
                    "Not running on ConnectivityService thread: "
                            + Thread.currentThread().getName());
        }
        NetworkRequest existing = mNetworkRequests.get(networkRequest.requestId);
        if (existing == networkRequest) return false;
        if (existing != null) {
            // Should only happen if the requestId wraps. If that happens lots of other things will
            // be broken as well.
            Log.wtf(TAG, String.format("Duplicate requestId for %s and %s on %s",
                    networkRequest, existing, toShortString()));
            updateRequestCounts(REMOVE, existing);
        }
        mNetworkRequests.put(networkRequest.requestId, networkRequest);
        updateRequestCounts(ADD, networkRequest);
        return true;
    }

    /**
     * Remove the specified request from this network.
     */
    public void removeRequest(int requestId) {
        if (mHandler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException(
                    "Not running on ConnectivityService thread: "
                            + Thread.currentThread().getName());
        }
        NetworkRequest existing = mNetworkRequests.get(requestId);
        if (existing == null) return;
        updateRequestCounts(REMOVE, existing);
        mNetworkRequests.remove(requestId);
        if (existing.isRequest()) {
            unlingerRequest(existing.requestId);
        }
    }

    /**
     * Returns whether this network is currently satisfying the request with the specified ID.
     */
    public boolean isSatisfyingRequest(int id) {
        return mNetworkRequests.get(id) != null;
    }

    /**
     * Returns the request at the specified position in the list of requests satisfied by this
     * network.
     */
    public NetworkRequest requestAt(int index) {
        if (mHandler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException(
                    "Not running on ConnectivityService thread: "
                            + Thread.currentThread().getName());
        }
        return mNetworkRequests.valueAt(index);
    }

    /**
     * Returns the number of requests currently satisfied by this network for which
     * {@link android.net.NetworkRequest#isRequest} returns {@code true}.
     */
    public int numRequestNetworkRequests() {
        return mNumRequestNetworkRequests;
    }

    /**
     * Returns the number of requests currently satisfied by this network of type
     * {@link android.net.NetworkRequest.Type#BACKGROUND_REQUEST}.
     */
    public int numBackgroundNetworkRequests() {
        return mNumBackgroundNetworkRequests;
    }

    /**
     * Returns the number of foreground requests currently satisfied by this network.
     */
    public int numForegroundNetworkRequests() {
        return mNumRequestNetworkRequests - mNumBackgroundNetworkRequests;
    }

    /**
     * Returns the number of requests of any type currently satisfied by this network.
     */
    public int numNetworkRequests() {
        if (mHandler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException(
                    "Not running on ConnectivityService thread: "
                            + Thread.currentThread().getName());
        }
        return mNetworkRequests.size();
    }

    /**
     * Returns whether the network is a background network. A network is a background network if it
     * does not have the NET_CAPABILITY_FOREGROUND capability, which implies it is satisfying no
     * foreground request, is not lingering (i.e. kept for a while after being outscored), and is
     * not a speculative network (i.e. kept pending validation when validation would have it
     * outscore another foreground network). That implies it is being kept up by some background
     * request (otherwise it would be torn down), maybe the mobile always-on request.
     */
    public boolean isBackgroundNetwork() {
        return !isVPN() && numForegroundNetworkRequests() == 0 && mNumBackgroundNetworkRequests > 0
                && !isLingering();
    }

    // Does this network satisfy request?
    public boolean satisfies(NetworkRequest request) {
        return everConnected()
                && request.networkCapabilities.satisfiedByNetworkCapabilities(networkCapabilities);
    }

    public boolean satisfiesImmutableCapabilitiesOf(NetworkRequest request) {
        return everConnected()
                && request.networkCapabilities.satisfiedByImmutableNetworkCapabilities(
                        networkCapabilities);
    }

    /** Whether this network is a VPN. */
    public boolean isVPN() {
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    /** Whether this network is a local network */
    public boolean isLocalNetwork() {
        return networkCapabilities.hasCapability(NET_CAPABILITY_LOCAL_NETWORK);
    }

    /**
     * Whether this network should propagate the capabilities from its underlying networks.
     * Currently only true for VPNs.
     */
    public boolean propagateUnderlyingCapabilities() {
        return isVPN();
    }

    // Caller must not mutate. This method is called frequently and making a defensive copy
    // would be too expensive. This is used by NetworkRanker.Scoreable, so it can be compared
    // against other scoreables.
    @Override public NetworkCapabilities getCapsNoCopy() {
        return networkCapabilities;
    }

    // NetworkRanker.Scoreable
    @Override public FullScore getScore() {
        return mScore;
    }

    /**
     * Mix-in the ConnectivityService-managed bits in the score.
     */
    public void setScore(final NetworkScore score) {
        final FullScore oldScore = mScore;
        mScore = FullScore.fromNetworkScore(score, networkCapabilities, networkAgentConfig,
                everValidated(), 0L != getAvoidUnvalidated(), yieldToBadWiFi(),
                0L != mFirstEvaluationConcludedTime, isDestroyed());
        maybeLogDifferences(oldScore);
    }

    /**
     * Update the ConnectivityService-managed bits in the score.
     *
     * Call this after changing any data that might affect the score (e.g., agent config).
     */
    public void updateScoreForNetworkAgentUpdate() {
        final FullScore oldScore = mScore;
        mScore = mScore.mixInScore(networkCapabilities, networkAgentConfig,
                everValidated(), 0L != getAvoidUnvalidated(), yieldToBadWiFi(),
                0L != mFirstEvaluationConcludedTime, isDestroyed());
        maybeLogDifferences(oldScore);
    }

    /**
     * Prints score differences to logcat, if any.
     * @param oldScore the old score. Differences from |oldScore| to |this| are logged, if any.
     */
    public void maybeLogDifferences(final FullScore oldScore) {
        final String differences = mScore.describeDifferencesFrom(oldScore);
        if (null != differences) {
            Log.i(TAG, "Update score for net " + network + " : " + differences);
        }
    }

    /**
     * Returns a Scoreable identical to this NAI, but validated.
     *
     * This is useful to probe what scoring would be if this network validated, to know
     * whether to provisionally keep a network that may or may not validate.
     *
     * @return a Scoreable identical to this NAI, but validated.
     */
    public NetworkRanker.Scoreable getValidatedScoreable() {
        return new NetworkRanker.Scoreable() {
            @Override public FullScore getScore() {
                return mScore.asValidated();
            }

            @Override public NetworkCapabilities getCapsNoCopy() {
                return networkCapabilities;
            }
        };
    }

    /**
     * Return a {@link NetworkStateSnapshot} for this network.
     */
    @NonNull
    public NetworkStateSnapshot getNetworkStateSnapshot() {
        synchronized (this) {
            // Network objects are outwardly immutable so there is no point in duplicating.
            // Duplicating also precludes sharing socket factories and connection pools.
            final String subscriberId = (networkAgentConfig != null)
                    ? networkAgentConfig.subscriberId : null;
            return new NetworkStateSnapshot(network, new NetworkCapabilities(networkCapabilities),
                    new LinkProperties(linkProperties), subscriberId, networkInfo.getType());
        }
    }

    /**
     * Sets the specified requestId to linger on this network for the specified time. Called by
     * ConnectivityService when any request is moved to another network with a higher score, or
     * when a network is newly created.
     *
     * @param requestId The requestId of the request that no longer need to be served by this
     *                  network. Or {@link NetworkRequest#REQUEST_ID_NONE} if this is the
     *                  {@code InactivityTimer} for a newly created network.
     */
    // TODO: Consider creating a dedicated function for nascent network, e.g. start/stopNascent.
    public void lingerRequest(int requestId, long now, long duration) {
        if (mInactivityTimerForRequest.get(requestId) != null) {
            // Cannot happen. Once a request is lingering on a particular network, we cannot
            // re-linger it unless that network becomes the best for that request again, in which
            // case we should have unlingered it.
            Log.wtf(TAG, toShortString() + ": request " + requestId + " already lingered");
        }
        final long expiryMs = now + duration;
        InactivityTimer timer = new InactivityTimer(requestId, expiryMs);
        if (VDBG) Log.d(TAG, "Adding InactivityTimer " + timer + " to " + toShortString());
        mInactivityTimers.add(timer);
        mInactivityTimerForRequest.put(requestId, timer);
    }

    /**
     * Sets the specified requestId to linger on this network for the timeout set when
     * initializing or modified by {@link #setLingerDuration(int)}. Called by
     * ConnectivityService when any request is moved to another network with a higher score.
     *
     * @param requestId The requestId of the request that no longer need to be served by this
     *                  network.
     * @param now current system timestamp obtained by {@code SystemClock.elapsedRealtime}.
     */
    public void lingerRequest(int requestId, long now) {
        lingerRequest(requestId, now, mLingerDurationMs);
    }

    /**
     * Cancel lingering. Called by ConnectivityService when a request is added to this network.
     * Returns true if the given requestId was lingering on this network, false otherwise.
     */
    public boolean unlingerRequest(int requestId) {
        InactivityTimer timer = mInactivityTimerForRequest.get(requestId);
        if (timer != null) {
            if (VDBG) {
                Log.d(TAG, "Removing InactivityTimer " + timer + " from " + toShortString());
            }
            mInactivityTimers.remove(timer);
            mInactivityTimerForRequest.remove(requestId);
            return true;
        }
        return false;
    }

    public long getInactivityExpiry() {
        return mInactivityExpiryMs;
    }

    public void updateInactivityTimer() {
        long newExpiry = mInactivityTimers.isEmpty() ? 0 : mInactivityTimers.last().expiryMs;
        if (newExpiry == mInactivityExpiryMs) return;

        // Even if we're going to reschedule the timer, cancel it first. This is because the
        // semantics of WakeupMessage guarantee that if cancel is called then the alarm will
        // never call its callback (handleLingerComplete), even if it has already fired.
        // WakeupMessage makes no such guarantees about rescheduling a message, so if mLingerMessage
        // has already been dispatched, rescheduling to some time in the future won't stop it
        // from calling its callback immediately.
        if (mInactivityMessage != null) {
            mInactivityMessage.cancel();
            mInactivityMessage = null;
        }

        if (newExpiry > 0) {
            // If the newExpiry timestamp is in the past, the wakeup message will fire immediately.
            mInactivityMessage = new WakeupMessage(
                    mContext, mHandler,
                    "NETWORK_LINGER_COMPLETE." + network.getNetId() /* cmdName */,
                    EVENT_NETWORK_LINGER_COMPLETE /* cmd */,
                    0 /* arg1 (unused) */, 0 /* arg2 (unused) */,
                    this /* obj (NetworkAgentInfo) */);
            mInactivityMessage.schedule(newExpiry);
        }

        mInactivityExpiryMs = newExpiry;
    }

    public void setInactive() {
        mInactive = true;
    }

    public void unsetInactive() {
        mInactive = false;
    }

    public boolean isInactive() {
        return mInactive;
    }

    public boolean isLingering() {
        return mInactive && !isNascent();
    }

    /**
     * Set the linger duration for this NAI.
     * @param durationMs The new linger duration, in milliseconds.
     */
    public void setLingerDuration(final int durationMs) {
        final long diff = durationMs - mLingerDurationMs;
        final ArrayList<InactivityTimer> newTimers = new ArrayList<>();
        for (final InactivityTimer timer : mInactivityTimers) {
            if (timer.requestId == NetworkRequest.REQUEST_ID_NONE) {
                // Don't touch nascent timer, re-add as is.
                newTimers.add(timer);
            } else {
                newTimers.add(new InactivityTimer(timer.requestId, timer.expiryMs + diff));
            }
        }
        mInactivityTimers.clear();
        mInactivityTimers.addAll(newTimers);
        updateInactivityTimer();
        mLingerDurationMs = durationMs;
    }

    /**
     * Return whether the network satisfies no request, but is still being kept up
     * because it has just connected less than
     * {@code ConnectivityService#DEFAULT_NASCENT_DELAY_MS}ms ago and is thus still considered
     * nascent. Note that nascent mechanism uses inactivity timer which isn't
     * associated with a request. Thus, use {@link NetworkRequest#REQUEST_ID_NONE} to identify it.
     *
     */
    public boolean isNascent() {
        return mInactive && mInactivityTimers.size() == 1
                && mInactivityTimers.first().requestId == NetworkRequest.REQUEST_ID_NONE;
    }

    public void clearInactivityState() {
        if (mInactivityMessage != null) {
            mInactivityMessage.cancel();
            mInactivityMessage = null;
        }
        mInactivityTimers.clear();
        mInactivityTimerForRequest.clear();
        // Sets mInactivityExpiryMs, cancels and nulls out mInactivityMessage.
        updateInactivityTimer();
        mInactive = false;
    }

    public void dumpInactivityTimers(PrintWriter pw) {
        for (InactivityTimer timer : mInactivityTimers) {
            pw.println(timer);
        }
    }

    /**
     * Dump the NAT64 xlat information.
     *
     * @param pw print writer.
     */
    public void dumpNat464Xlat(IndentingPrintWriter pw) {
        clatd.dump(pw);
    }

    /**
     * Sets the most recent ConnectivityReport for this network.
     *
     * <p>This should only be called from the ConnectivityService thread.
     *
     * @hide
     */
    public void setConnectivityReport(@NonNull ConnectivityReport connectivityReport) {
        mConnectivityReport = connectivityReport;
    }

    /**
     * Returns the most recent ConnectivityReport for this network, or null if none have been
     * reported yet.
     *
     * <p>This should only be called from the ConnectivityService thread.
     *
     * @hide
     */
    @Nullable
    public ConnectivityReport getConnectivityReport() {
        return mConnectivityReport;
    }

    /**
     * Make sure the NC from network agents don't contain stuff they shouldn't.
     *
     * @param nc the capabilities to sanitize
     * @param creatorUid the UID of the process creating this network agent
     * @param hasAutomotiveFeature true if this device has the automotive feature, false otherwise
     * @param authenticator the carrier privilege authenticator to check for telephony constraints
     */
    public static void restrictCapabilitiesFromNetworkAgent(@NonNull final NetworkCapabilities nc,
            final int creatorUid, final boolean hasAutomotiveFeature,
            @NonNull final ConnectivityService.Dependencies deps,
            @Nullable final CarrierPrivilegeAuthenticator authenticator) {
        if (nc.hasTransport(TRANSPORT_TEST)) {
            nc.restrictCapabilitiesForTestNetwork(creatorUid);
        }
        if (!areAllowedUidsAcceptableFromNetworkAgent(
                nc, hasAutomotiveFeature, deps, authenticator)) {
            nc.setAllowedUids(new ArraySet<>());
        }
    }

    private static boolean areAllowedUidsAcceptableFromNetworkAgent(
            @NonNull final NetworkCapabilities nc, final boolean hasAutomotiveFeature,
            @NonNull final ConnectivityService.Dependencies deps,
            @Nullable final CarrierPrivilegeAuthenticator carrierPrivilegeAuthenticator) {
        // NCs without access UIDs are fine.
        if (!nc.hasAllowedUids()) return true;
        // S and below must never accept access UIDs, even if an agent sends them, because netd
        // didn't support the required feature in S.
        if (!deps.isAtLeastT()) return false;

        // On a non-restricted network, access UIDs make no sense
        if (nc.hasCapability(NET_CAPABILITY_NOT_RESTRICTED)) return false;

        // If this network has TRANSPORT_TEST and nothing else, then the caller can do whatever
        // they want to access UIDs
        if (nc.hasSingleTransport(TRANSPORT_TEST)) return true;

        if (nc.hasTransport(TRANSPORT_ETHERNET)) {
            // Factories that make ethernet networks can allow UIDs for automotive devices.
            if (hasAutomotiveFeature) return true;
            // It's also admissible if the ethernet network has TRANSPORT_TEST, as long as it
            // doesn't have NET_CAPABILITY_INTERNET so it can't become the default network.
            if (nc.hasTransport(TRANSPORT_TEST) && !nc.hasCapability(NET_CAPABILITY_INTERNET)) {
                return true;
            }
            return false;
        }

        // Factories that make cell/wifi networks can allow the UID for the carrier service package.
        // This can only work in T where there is support for CarrierPrivilegeAuthenticator
        if (null != carrierPrivilegeAuthenticator
                && (nc.hasSingleTransportBesidesTest(TRANSPORT_CELLULAR)
                        || nc.hasSingleTransportBesidesTest(TRANSPORT_WIFI))
                && (1 == nc.getAllowedUidsNoCopy().size())
                && (carrierPrivilegeAuthenticator.isCarrierServiceUidForNetworkCapabilities(
                        nc.getAllowedUidsNoCopy().valueAt(0), nc))) {
            return true;
        }

        return false;
    }

    // TODO: Print shorter members first and only print the boolean variable which value is true
    // to improve readability.
    public String toString() {
        return "NetworkAgentInfo{"
                + "network{" + network + "}  handle{" + network.getNetworkHandle() + "}  ni{"
                + networkInfo.toShortString() + "} "
                + "created=" + Instant.ofEpochMilli(mCreationTime) + " "
                + mScore + " "
                + (isCreated() ? " created " + getCreatedTime() : "")
                + (isDestroyed() ? " destroyed " + mDestroyedTime : "")
                + (isNascent() ? " nascent" : (isLingering() ? " lingering" : ""))
                + (everValidated() ? " firstValidated " + getFirstValidationTime() : "")
                + (isValidated() ? " lastValidated " + getCurrentValidationTime() : "")
                + (partialConnectivity()
                        ? " partialConnectivity " + mPartialConnectivityTime : "")
                + (everCaptivePortalDetected()
                        ? " firstCaptivePortalDetected " + mFirstCaptivePortalDetectedTime : "")
                + (captivePortalDetected()
                        ? " currentCaptivePortalDetected " + mCurrentCaptivePortalDetectedTime : "")
                + (networkAgentConfig.explicitlySelected ? " explicitlySelected" : "")
                + (networkAgentConfig.acceptUnvalidated ? " acceptUnvalidated" : "")
                + (networkAgentConfig.acceptPartialConnectivity ? " acceptPartialConnectivity" : "")
                + (clatd.isStarted() ? " clat{" + clatd + "} " : "")
                + (declaredUnderlyingNetworks != null
                        ? " underlying{" + Arrays.toString(declaredUnderlyingNetworks) + "}" : "")
                + "  lp{" + linkProperties + "}"
                + "  nc{" + networkCapabilities + "}"
                + "  factorySerialNumber=" + factorySerialNumber
                + "}";
    }

    /**
     * Show a short string representing a Network.
     *
     * This is often not enough for debugging purposes for anything complex, but the full form
     * is very long and hard to read, so this is useful when there isn't a lot of ambiguity.
     * This represents the network with something like "[100 WIFI|VPN]" or "[108 CELLULAR]".
     */
    public String toShortString() {
        return "[" + network.getNetId() + " "
                + transportNamesOf(networkCapabilities.getTransportTypes()) + "]";
    }

    /**
     * Null-guarding version of NetworkAgentInfo#toShortString()
     */
    @NonNull
    public static String toShortString(@Nullable final NetworkAgentInfo nai) {
        return null != nai ? nai.toShortString() : "[null]";
    }
}
