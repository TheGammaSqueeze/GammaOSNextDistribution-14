/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server;

import static android.net.BpfNetMapsConstants.CONFIGURATION_MAP_PATH;
import static android.net.BpfNetMapsConstants.COOKIE_TAG_MAP_PATH;
import static android.net.BpfNetMapsConstants.CURRENT_STATS_MAP_CONFIGURATION_KEY;
import static android.net.BpfNetMapsConstants.DATA_SAVER_DISABLED;
import static android.net.BpfNetMapsConstants.DATA_SAVER_ENABLED;
import static android.net.BpfNetMapsConstants.DATA_SAVER_ENABLED_KEY;
import static android.net.BpfNetMapsConstants.DATA_SAVER_ENABLED_MAP_PATH;
import static android.net.BpfNetMapsConstants.HAPPY_BOX_MATCH;
import static android.net.BpfNetMapsConstants.IIF_MATCH;
import static android.net.BpfNetMapsConstants.INGRESS_DISCARD_MAP_PATH;
import static android.net.BpfNetMapsConstants.LOCKDOWN_VPN_MATCH;
import static android.net.BpfNetMapsConstants.PENALTY_BOX_MATCH;
import static android.net.BpfNetMapsConstants.UID_OWNER_MAP_PATH;
import static android.net.BpfNetMapsConstants.UID_PERMISSION_MAP_PATH;
import static android.net.BpfNetMapsConstants.UID_RULES_CONFIGURATION_KEY;
import static android.net.BpfNetMapsUtils.getMatchByFirewallChain;
import static android.net.BpfNetMapsUtils.isFirewallAllowList;
import static android.net.BpfNetMapsUtils.matchToString;
import static android.net.ConnectivityManager.FIREWALL_RULE_ALLOW;
import static android.net.ConnectivityManager.FIREWALL_RULE_DENY;
import static android.net.INetd.PERMISSION_INTERNET;
import static android.net.INetd.PERMISSION_NONE;
import static android.net.INetd.PERMISSION_UNINSTALLED;
import static android.net.INetd.PERMISSION_UPDATE_DEVICE_STATS;
import static android.system.OsConstants.EINVAL;
import static android.system.OsConstants.ENODEV;
import static android.system.OsConstants.ENOENT;
import static android.system.OsConstants.EOPNOTSUPP;

import static com.android.server.ConnectivityStatsLog.NETWORK_BPF_MAP_INFO;

import android.app.StatsManager;
import android.content.Context;
import android.net.BpfNetMapsReader;
import android.net.INetd;
import android.net.UidOwnerValue;
import android.os.Build;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.ErrnoException;
import android.system.Os;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.Pair;
import android.util.StatsEvent;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.BackgroundThread;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.BpfDump;
import com.android.net.module.util.BpfMap;
import com.android.net.module.util.IBpfMap;
import com.android.net.module.util.Struct;
import com.android.net.module.util.Struct.S32;
import com.android.net.module.util.Struct.U32;
import com.android.net.module.util.Struct.U8;
import com.android.net.module.util.bpf.CookieTagMapKey;
import com.android.net.module.util.bpf.CookieTagMapValue;
import com.android.net.module.util.bpf.IngressDiscardKey;
import com.android.net.module.util.bpf.IngressDiscardValue;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/**
 * BpfNetMaps is responsible for providing traffic controller relevant functionality.
 *
 * {@hide}
 */
public class BpfNetMaps {
    static {
        if (SdkLevel.isAtLeastT()) {
            System.loadLibrary("service-connectivity");
        }
    }

    private static final String TAG = "BpfNetMaps";
    private final INetd mNetd;
    private final Dependencies mDeps;
    // Use legacy netd for releases before T.
    private static boolean sInitialized = false;

    // Lock for sConfigurationMap entry for UID_RULES_CONFIGURATION_KEY.
    // This entry is not accessed by others.
    // BpfNetMaps acquires this lock while sequence of read, modify, and write.
    private static final Object sUidRulesConfigBpfMapLock = new Object();

    // Lock for sConfigurationMap entry for CURRENT_STATS_MAP_CONFIGURATION_KEY.
    // BpfNetMaps acquires this lock while sequence of read, modify, and write.
    // BpfNetMaps is an only writer of this entry.
    private static final Object sCurrentStatsMapConfigLock = new Object();

    private static final long UID_RULES_DEFAULT_CONFIGURATION = 0;
    private static final long STATS_SELECT_MAP_A = 0;
    private static final long STATS_SELECT_MAP_B = 1;

    private static IBpfMap<S32, U32> sConfigurationMap = null;
    // BpfMap for UID_OWNER_MAP_PATH. This map is not accessed by others.
    private static IBpfMap<S32, UidOwnerValue> sUidOwnerMap = null;
    private static IBpfMap<S32, U8> sUidPermissionMap = null;
    private static IBpfMap<CookieTagMapKey, CookieTagMapValue> sCookieTagMap = null;
    // TODO: Add BOOL class and replace U8?
    private static IBpfMap<S32, U8> sDataSaverEnabledMap = null;
    private static IBpfMap<IngressDiscardKey, IngressDiscardValue> sIngressDiscardMap = null;

    private static final List<Pair<Integer, String>> PERMISSION_LIST = Arrays.asList(
            Pair.create(PERMISSION_INTERNET, "PERMISSION_INTERNET"),
            Pair.create(PERMISSION_UPDATE_DEVICE_STATS, "PERMISSION_UPDATE_DEVICE_STATS")
    );

    /**
     * Set configurationMap for test.
     */
    @VisibleForTesting
    public static void setConfigurationMapForTest(IBpfMap<S32, U32> configurationMap) {
        sConfigurationMap = configurationMap;
    }

    /**
     * Set uidOwnerMap for test.
     */
    @VisibleForTesting
    public static void setUidOwnerMapForTest(IBpfMap<S32, UidOwnerValue> uidOwnerMap) {
        sUidOwnerMap = uidOwnerMap;
    }

    /**
     * Set uidPermissionMap for test.
     */
    @VisibleForTesting
    public static void setUidPermissionMapForTest(IBpfMap<S32, U8> uidPermissionMap) {
        sUidPermissionMap = uidPermissionMap;
    }

    /**
     * Set cookieTagMap for test.
     */
    @VisibleForTesting
    public static void setCookieTagMapForTest(
            IBpfMap<CookieTagMapKey, CookieTagMapValue> cookieTagMap) {
        sCookieTagMap = cookieTagMap;
    }

    /**
     * Set dataSaverEnabledMap for test.
     */
    @VisibleForTesting
    public static void setDataSaverEnabledMapForTest(IBpfMap<S32, U8> dataSaverEnabledMap) {
        sDataSaverEnabledMap = dataSaverEnabledMap;
    }

    /**
     * Set ingressDiscardMap for test.
     */
    @VisibleForTesting
    public static void setIngressDiscardMapForTest(
            IBpfMap<IngressDiscardKey, IngressDiscardValue> ingressDiscardMap) {
        sIngressDiscardMap = ingressDiscardMap;
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static IBpfMap<S32, U32> getConfigurationMap() {
        try {
            return new BpfMap<>(
                    CONFIGURATION_MAP_PATH, S32.class, U32.class);
        } catch (ErrnoException e) {
            throw new IllegalStateException("Cannot open netd configuration map", e);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static IBpfMap<S32, UidOwnerValue> getUidOwnerMap() {
        try {
            return new BpfMap<>(
                    UID_OWNER_MAP_PATH, S32.class, UidOwnerValue.class);
        } catch (ErrnoException e) {
            throw new IllegalStateException("Cannot open uid owner map", e);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static IBpfMap<S32, U8> getUidPermissionMap() {
        try {
            return new BpfMap<>(
                    UID_PERMISSION_MAP_PATH, S32.class, U8.class);
        } catch (ErrnoException e) {
            throw new IllegalStateException("Cannot open uid permission map", e);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static IBpfMap<CookieTagMapKey, CookieTagMapValue> getCookieTagMap() {
        try {
            return new BpfMap<>(COOKIE_TAG_MAP_PATH,
                    CookieTagMapKey.class, CookieTagMapValue.class);
        } catch (ErrnoException e) {
            throw new IllegalStateException("Cannot open cookie tag map", e);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static IBpfMap<S32, U8> getDataSaverEnabledMap() {
        try {
            return new BpfMap<>(
                    DATA_SAVER_ENABLED_MAP_PATH, S32.class, U8.class);
        } catch (ErrnoException e) {
            throw new IllegalStateException("Cannot open data saver enabled map", e);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static IBpfMap<IngressDiscardKey, IngressDiscardValue> getIngressDiscardMap() {
        try {
            return new BpfMap<>(INGRESS_DISCARD_MAP_PATH,
                    IngressDiscardKey.class, IngressDiscardValue.class);
        } catch (ErrnoException e) {
            throw new IllegalStateException("Cannot open ingress discard map", e);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static void initBpfMaps() {
        if (sConfigurationMap == null) {
            sConfigurationMap = getConfigurationMap();
        }
        try {
            sConfigurationMap.updateEntry(UID_RULES_CONFIGURATION_KEY,
                    new U32(UID_RULES_DEFAULT_CONFIGURATION));
        } catch (ErrnoException e) {
            throw new IllegalStateException("Failed to initialize uid rules configuration", e);
        }
        try {
            sConfigurationMap.updateEntry(CURRENT_STATS_MAP_CONFIGURATION_KEY,
                    new U32(STATS_SELECT_MAP_A));
        } catch (ErrnoException e) {
            throw new IllegalStateException("Failed to initialize current stats configuration", e);
        }

        if (sUidOwnerMap == null) {
            sUidOwnerMap = getUidOwnerMap();
        }
        try {
            sUidOwnerMap.clear();
        } catch (ErrnoException e) {
            throw new IllegalStateException("Failed to initialize uid owner map", e);
        }

        if (sUidPermissionMap == null) {
            sUidPermissionMap = getUidPermissionMap();
        }

        if (sCookieTagMap == null) {
            sCookieTagMap = getCookieTagMap();
        }

        if (sDataSaverEnabledMap == null) {
            sDataSaverEnabledMap = getDataSaverEnabledMap();
        }
        try {
            sDataSaverEnabledMap.updateEntry(DATA_SAVER_ENABLED_KEY, new U8(DATA_SAVER_DISABLED));
        } catch (ErrnoException e) {
            throw new IllegalStateException("Failed to initialize data saver configuration", e);
        }

        if (sIngressDiscardMap == null) {
            sIngressDiscardMap = getIngressDiscardMap();
        }
        try {
            sIngressDiscardMap.clear();
        } catch (ErrnoException e) {
            throw new IllegalStateException("Failed to initialize ingress discard map", e);
        }
    }

    /**
     * Initializes the class if it is not already initialized. This method will open maps but not
     * cause any other effects. This method may be called multiple times on any thread.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static synchronized void ensureInitialized(final Context context) {
        if (sInitialized) return;
        initBpfMaps();
        sInitialized = true;
    }

    /**
     * Dependencies of BpfNetMaps, for injection in tests.
     */
    @VisibleForTesting
    public static class Dependencies {
        /**
         * Get interface index.
         */
        public int getIfIndex(final String ifName) {
            return Os.if_nametoindex(ifName);
        }

        /**
         * Get interface name
         */
        public String getIfName(final int ifIndex) {
            return Os.if_indextoname(ifIndex);
        }

        /**
         * Synchronously call in to kernel to synchronize_rcu()
         */
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        public int synchronizeKernelRCU() {
            try {
                BpfMap.synchronizeKernelRCU();
            } catch (ErrnoException e) {
                return -e.errno;
            }
            return 0;
        }

        /**
         * Build Stats Event for NETWORK_BPF_MAP_INFO atom
         */
        public StatsEvent buildStatsEvent(final int cookieTagMapSize, final int uidOwnerMapSize,
                final int uidPermissionMapSize) {
            return ConnectivityStatsLog.buildStatsEvent(NETWORK_BPF_MAP_INFO, cookieTagMapSize,
                    uidOwnerMapSize, uidPermissionMapSize);
        }
    }

    /** Constructor used after T that doesn't need to use netd anymore. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public BpfNetMaps(final Context context) {
        this(context, null);

        if (!SdkLevel.isAtLeastT()) throw new IllegalArgumentException("BpfNetMaps need to use netd before T");
    }

    public BpfNetMaps(final Context context, final INetd netd) {
        this(context, netd, new Dependencies());
    }

    @VisibleForTesting
    public BpfNetMaps(final Context context, final INetd netd, final Dependencies deps) {
        if (SdkLevel.isAtLeastT()) {
            try {
                ensureInitialized(context);
            } catch(Throwable t) {
                android.util.Log.e("PHH", "Failed initialization BpfMaps, doing without it", t);
            }
        }
        mNetd = netd;
        mDeps = deps;
    }

    private void maybeThrow(final int err, final String msg) {
        if (err != 0) {
            throw new ServiceSpecificException(err, msg + ": " + Os.strerror(err));
        }
    }

    private void throwIfPreT(final String msg) {
        if (!SdkLevel.isAtLeastT()) {
            throw new UnsupportedOperationException(msg);
        }
    }

    private void removeRule(final int uid, final long match, final String caller) {
        if (sUidOwnerMap == null) return;

        try {
            synchronized (sUidOwnerMap) {
                final UidOwnerValue oldMatch = sUidOwnerMap.getValue(new S32(uid));

                if (oldMatch == null) {
                    throw new ServiceSpecificException(ENOENT,
                            "sUidOwnerMap does not have entry for uid: " + uid);
                }

                final UidOwnerValue newMatch = new UidOwnerValue(
                        (match == IIF_MATCH) ? 0 : oldMatch.iif,
                        oldMatch.rule & ~match
                );

                if (newMatch.rule == 0) {
                    sUidOwnerMap.deleteEntry(new S32(uid));
                } else {
                    sUidOwnerMap.updateEntry(new S32(uid), newMatch);
                }
            }
        } catch (ErrnoException e) {
            throw new ServiceSpecificException(e.errno,
                    caller + " failed to remove rule: " + Os.strerror(e.errno));
        }
    }

    private void addRule(final int uid, final long match, final int iif, final String caller) {
        if (sUidOwnerMap == null) return;

        if (match != IIF_MATCH && iif != 0) {
            throw new ServiceSpecificException(EINVAL,
                    "Non-interface match must have zero interface index");
        }

        try {
            synchronized (sUidOwnerMap) {
                final UidOwnerValue oldMatch = sUidOwnerMap.getValue(new S32(uid));

                final UidOwnerValue newMatch;
                if (oldMatch != null) {
                    newMatch = new UidOwnerValue(
                            (match == IIF_MATCH) ? iif : oldMatch.iif,
                            oldMatch.rule | match
                    );
                } else {
                    newMatch = new UidOwnerValue(
                            iif,
                            match
                    );
                }
                sUidOwnerMap.updateEntry(new S32(uid), newMatch);
            }
        } catch (ErrnoException e) {
            throw new ServiceSpecificException(e.errno,
                    caller + " failed to add rule: " + Os.strerror(e.errno));
        }
    }

    private void addRule(final int uid, final long match, final String caller) {
        addRule(uid, match, 0 /* iif */, caller);
    }

    /**
     * Add naughty app bandwidth rule for specific app
     *
     * @param uid uid of target app
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void addNaughtyApp(final int uid) {
        throwIfPreT("addNaughtyApp is not available on pre-T devices");

        addRule(uid, PENALTY_BOX_MATCH, "addNaughtyApp");
    }

    /**
     * Remove naughty app bandwidth rule for specific app
     *
     * @param uid uid of target app
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void removeNaughtyApp(final int uid) {
        throwIfPreT("removeNaughtyApp is not available on pre-T devices");

        removeRule(uid, PENALTY_BOX_MATCH, "removeNaughtyApp");
    }

    /**
     * Add nice app bandwidth rule for specific app
     *
     * @param uid uid of target app
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void addNiceApp(final int uid) {
        throwIfPreT("addNiceApp is not available on pre-T devices");

        addRule(uid, HAPPY_BOX_MATCH, "addNiceApp");
    }

    /**
     * Remove nice app bandwidth rule for specific app
     *
     * @param uid uid of target app
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void removeNiceApp(final int uid) {
        throwIfPreT("removeNiceApp is not available on pre-T devices");

        removeRule(uid, HAPPY_BOX_MATCH, "removeNiceApp");
    }

    /**
     * Set target firewall child chain
     *
     * @param childChain target chain to enable
     * @param enable     whether to enable or disable child chain.
     * @throws UnsupportedOperationException if called on pre-T devices.
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void setChildChain(final int childChain, final boolean enable) {
        throwIfPreT("setChildChain is not available on pre-T devices");

        if (sConfigurationMap == null) return;

        final long match = getMatchByFirewallChain(childChain);
        try {
            synchronized (sUidRulesConfigBpfMapLock) {
                final U32 config = sConfigurationMap.getValue(UID_RULES_CONFIGURATION_KEY);
                final long newConfig = enable ? (config.val | match) : (config.val & ~match);
                sConfigurationMap.updateEntry(UID_RULES_CONFIGURATION_KEY, new U32(newConfig));
            }
        } catch (ErrnoException e) {
            throw new ServiceSpecificException(e.errno,
                    "Unable to set child chain: " + Os.strerror(e.errno));
        }
    }

    /**
     * Get the specified firewall chain's status.
     *
     * @param childChain target chain
     * @return {@code true} if chain is enabled, {@code false} if chain is not enabled.
     * @throws UnsupportedOperationException if called on pre-T devices.
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     *
     * @deprecated Use {@link BpfNetMapsReader#isChainEnabled} instead.
     */
    // TODO: Migrate the callers to use {@link BpfNetMapsReader#isChainEnabled} instead.
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public boolean isChainEnabled(final int childChain) {
        return BpfNetMapsReader.isChainEnabled(sConfigurationMap, childChain);
    }

    private Set<Integer> asSet(final int[] uids) {
        final Set<Integer> uidSet = new ArraySet<>();
        for (final int uid : uids) {
            uidSet.add(uid);
        }
        return uidSet;
    }

    /**
     * Replaces the contents of the specified UID-based firewall chain.
     * Enables the chain for specified uids and disables the chain for non-specified uids.
     *
     * @param chain       Target chain.
     * @param uids        The list of UIDs to allow/deny.
     * @throws UnsupportedOperationException if called on pre-T devices.
     * @throws IllegalArgumentException if {@code chain} is not a valid chain.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void replaceUidChain(final int chain, final int[] uids) {
        throwIfPreT("replaceUidChain is not available on pre-T devices");

        if (sUidOwnerMap == null) return;

        final long match;
        try {
            match = getMatchByFirewallChain(chain);
        } catch (ServiceSpecificException e) {
            // Throws IllegalArgumentException to keep the behavior of
            // ConnectivityManager#replaceFirewallChain API
            throw new IllegalArgumentException("Invalid firewall chain: " + chain);
        }
        final Set<Integer> uidSet = asSet(uids);
        final Set<Integer> uidSetToRemoveRule = new ArraySet<>();
        try {
            synchronized (sUidOwnerMap) {
                sUidOwnerMap.forEach((uid, config) -> {
                    // config could be null if there is a concurrent entry deletion.
                    // http://b/220084230. But sUidOwnerMap update must be done while holding a
                    // lock, so this should not happen.
                    if (config == null) {
                        Log.wtf(TAG, "sUidOwnerMap entry was deleted while holding a lock");
                    } else if (!uidSet.contains((int) uid.val) && (config.rule & match) != 0) {
                        uidSetToRemoveRule.add((int) uid.val);
                    }
                });

                for (final int uid : uidSetToRemoveRule) {
                    removeRule(uid, match, "replaceUidChain");
                }
                for (final int uid : uids) {
                    addRule(uid, match, "replaceUidChain");
                }
            }
        } catch (ErrnoException | ServiceSpecificException e) {
            Log.e(TAG, "replaceUidChain failed: " + e);
        }
    }

    /**
     * Set firewall rule for uid
     *
     * @param childChain   target chain
     * @param uid          uid to allow/deny
     * @param firewallRule either FIREWALL_RULE_ALLOW or FIREWALL_RULE_DENY
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void setUidRule(final int childChain, final int uid, final int firewallRule) {
        throwIfPreT("setUidRule is not available on pre-T devices");

        final long match = getMatchByFirewallChain(childChain);
        final boolean isAllowList = isFirewallAllowList(childChain);
        final boolean add = (firewallRule == FIREWALL_RULE_ALLOW && isAllowList)
                || (firewallRule == FIREWALL_RULE_DENY && !isAllowList);

        if (add) {
            addRule(uid, match, "setUidRule");
        } else {
            removeRule(uid, match, "setUidRule");
        }
    }

    /**
     * Get firewall rule of specified firewall chain on specified uid.
     *
     * @param childChain target chain
     * @param uid        target uid
     * @return either FIREWALL_RULE_ALLOW or FIREWALL_RULE_DENY
     * @throws UnsupportedOperationException if called on pre-T devices.
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     *
     * @deprecated use {@link BpfNetMapsReader#getUidRule} instead.
     */
    // TODO: Migrate the callers to use {@link BpfNetMapsReader#getUidRule} instead.
    public int getUidRule(final int childChain, final int uid) {
        return BpfNetMapsReader.getUidRule(sUidOwnerMap, childChain, uid);
    }

    private Set<Integer> getUidsMatchEnabled(final int childChain) throws ErrnoException {
        final long match = getMatchByFirewallChain(childChain);
        Set<Integer> uids = new ArraySet<>();

        if (sUidOwnerMap == null) return uids;

        synchronized (sUidOwnerMap) {
            sUidOwnerMap.forEach((uid, val) -> {
                if (val == null) {
                    Log.wtf(TAG, "sUidOwnerMap entry was deleted while holding a lock");
                } else {
                    if ((val.rule & match) != 0) {
                        uids.add(uid.val);
                    }
                }
            });
        }
        return uids;
    }

    /**
     * Get uids that has FIREWALL_RULE_ALLOW on allowlist chain.
     * Allowlist means the firewall denies all by default, uids must be explicitly allowed.
     *
     * Note that uids that has FIREWALL_RULE_DENY on allowlist chain can not be computed from the
     * bpf map, since all the uids that does not have explicit FIREWALL_RULE_ALLOW rule in bpf map
     * are determined to have FIREWALL_RULE_DENY.
     *
     * @param childChain target chain
     * @return Set of uids
     */
    public Set<Integer> getUidsWithAllowRuleOnAllowListChain(final int childChain)
            throws ErrnoException {
        if (!isFirewallAllowList(childChain)) {
            throw new IllegalArgumentException("getUidsWithAllowRuleOnAllowListChain is called with"
                    + " denylist chain:" + childChain);
        }
        // Corresponding match is enabled for uids that has FIREWALL_RULE_ALLOW on allowlist chain.
        return getUidsMatchEnabled(childChain);
    }

    /**
     * Get uids that has FIREWALL_RULE_DENY on denylist chain.
     * Denylist means the firewall allows all by default, uids must be explicitly denyed
     *
     * Note that uids that has FIREWALL_RULE_ALLOW on denylist chain can not be computed from the
     * bpf map, since all the uids that does not have explicit FIREWALL_RULE_DENY rule in bpf map
     * are determined to have the FIREWALL_RULE_ALLOW.
     *
     * @param childChain target chain
     * @return Set of uids
     */
    public Set<Integer> getUidsWithDenyRuleOnDenyListChain(final int childChain)
            throws ErrnoException {
        if (isFirewallAllowList(childChain)) {
            throw new IllegalArgumentException("getUidsWithDenyRuleOnDenyListChain is called with"
                    + " allowlist chain:" + childChain);
        }
        // Corresponding match is enabled for uids that has FIREWALL_RULE_DENY on denylist chain.
        return getUidsMatchEnabled(childChain);
    }

    /**
     * Add ingress interface filtering rules to a list of UIDs
     *
     * For a given uid, once a filtering rule is added, the kernel will only allow packets from the
     * allowed interface and loopback to be sent to the list of UIDs.
     *
     * Calling this method on one or more UIDs with an existing filtering rule but a different
     * interface name will result in the filtering rule being updated to allow the new interface
     * instead. Otherwise calling this method will not affect existing rules set on other UIDs.
     *
     * @param ifName the name of the interface on which the filtering rules will allow packets to
     *               be received.
     * @param uids   an array of UIDs which the filtering rules will be set
     * @throws RemoteException when netd has crashed.
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     */
    public void addUidInterfaceRules(final String ifName, final int[] uids) throws RemoteException {
        if (!SdkLevel.isAtLeastT()) {
            mNetd.firewallAddUidInterfaceRules(ifName, uids);
            return;
        }

        // Null ifName is a wildcard to allow apps to receive packets on all interfaces and
        // ifIndex is set to 0.
        final int ifIndex;
        if (ifName == null) {
            ifIndex = 0;
        } else {
            ifIndex = mDeps.getIfIndex(ifName);
            if (ifIndex == 0) {
                throw new ServiceSpecificException(ENODEV,
                        "Failed to get index of interface " + ifName);
            }
        }
        for (final int uid : uids) {
            try {
                addRule(uid, IIF_MATCH, ifIndex, "addUidInterfaceRules");
            } catch (ServiceSpecificException e) {
                Log.e(TAG, "addRule failed uid=" + uid + " ifName=" + ifName + ", " + e);
            }
        }
    }

    /**
     * Remove ingress interface filtering rules from a list of UIDs
     *
     * Clear the ingress interface filtering rules from the list of UIDs which were previously set
     * by addUidInterfaceRules(). Ignore any uid which does not have filtering rule.
     *
     * @param uids an array of UIDs from which the filtering rules will be removed
     * @throws RemoteException when netd has crashed.
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     */
    public void removeUidInterfaceRules(final int[] uids) throws RemoteException {
        if (!SdkLevel.isAtLeastT()) {
            mNetd.firewallRemoveUidInterfaceRules(uids);
            return;
        }

        for (final int uid : uids) {
            try {
                removeRule(uid, IIF_MATCH, "removeUidInterfaceRules");
            } catch (ServiceSpecificException e) {
                Log.e(TAG, "removeRule failed uid=" + uid + ", " + e);
            }
        }
    }

    /**
     * Update lockdown rule for uid
     *
     * @param  uid          target uid to add/remove the rule
     * @param  add          {@code true} to add the rule, {@code false} to remove the rule.
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void updateUidLockdownRule(final int uid, final boolean add) {
        throwIfPreT("updateUidLockdownRule is not available on pre-T devices");

        if (add) {
            addRule(uid, LOCKDOWN_VPN_MATCH, "updateUidLockdownRule");
        } else {
            removeRule(uid, LOCKDOWN_VPN_MATCH, "updateUidLockdownRule");
        }
    }

    /**
     * Request netd to change the current active network stats map.
     *
     * @throws UnsupportedOperationException if called on pre-T devices.
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void swapActiveStatsMap() {
        throwIfPreT("swapActiveStatsMap is not available on pre-T devices");

        if (sConfigurationMap == null) return;

        try {
            synchronized (sCurrentStatsMapConfigLock) {
                final long config = sConfigurationMap.getValue(
                        CURRENT_STATS_MAP_CONFIGURATION_KEY).val;
                final long newConfig = (config == STATS_SELECT_MAP_A)
                        ? STATS_SELECT_MAP_B : STATS_SELECT_MAP_A;
                sConfigurationMap.updateEntry(CURRENT_STATS_MAP_CONFIGURATION_KEY,
                        new U32(newConfig));
            }
        } catch (ErrnoException e) {
            throw new ServiceSpecificException(e.errno, "Failed to swap active stats map");
        }

        // After changing the config, it's needed to make sure all the current running eBPF
        // programs are finished and all the CPUs are aware of this config change before the old
        // map is modified. So special hack is needed here to wait for the kernel to do a
        // synchronize_rcu(). Once the kernel called synchronize_rcu(), the updated config will
        // be available to all cores and the next eBPF programs triggered inside the kernel will
        // use the new map configuration. So once this function returns it is safe to modify the
        // old stats map without concerning about race between the kernel and userspace.
        final int err = mDeps.synchronizeKernelRCU();
        maybeThrow(err, "synchronizeKernelRCU failed");
    }

    /**
     * Assigns android.permission.INTERNET and/or android.permission.UPDATE_DEVICE_STATS to the uids
     * specified. Or remove all permissions from the uids.
     *
     * @param permissions The permission to grant, it could be either PERMISSION_INTERNET and/or
     *                    PERMISSION_UPDATE_DEVICE_STATS. If the permission is NO_PERMISSIONS, then
     *                    revoke all permissions for the uids.
     * @param uids        uid of users to grant permission
     * @throws RemoteException when netd has crashed.
     */
    public void setNetPermForUids(final int permissions, final int[] uids) throws RemoteException {
        if (!SdkLevel.isAtLeastT()) {
            mNetd.trafficSetNetPermForUids(permissions, uids);
            return;
        }

        if (sUidPermissionMap == null) return;

        // Remove the entry if package is uninstalled or uid has only INTERNET permission.
        if (permissions == PERMISSION_UNINSTALLED || permissions == PERMISSION_INTERNET) {
            for (final int uid : uids) {
                try {
                    sUidPermissionMap.deleteEntry(new S32(uid));
                } catch (ErrnoException e) {
                    Log.e(TAG, "Failed to remove uid " + uid + " from permission map: " + e);
                }
            }
            return;
        }

        for (final int uid : uids) {
            try {
                sUidPermissionMap.updateEntry(new S32(uid), new U8((short) permissions));
            } catch (ErrnoException e) {
                Log.e(TAG, "Failed to set permission "
                        + permissions + " to uid " + uid + ": " + e);
            }
        }
    }

    /**
     * Set Data Saver enabled or disabled
     *
     * @param enable     whether Data Saver is enabled or disabled.
     * @throws UnsupportedOperationException if called on pre-T devices.
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void setDataSaverEnabled(boolean enable) {
        throwIfPreT("setDataSaverEnabled is not available on pre-T devices");

        if (sDataSaverEnabledMap == null) return;

        try {
            final short config = enable ? DATA_SAVER_ENABLED : DATA_SAVER_DISABLED;
            sDataSaverEnabledMap.updateEntry(DATA_SAVER_ENABLED_KEY, new U8(config));
        } catch (ErrnoException e) {
            throw new ServiceSpecificException(e.errno, "Unable to set data saver: "
                    + Os.strerror(e.errno));
        }
    }

    /**
     * Set ingress discard rule
     *
     * @param address target address to set the ingress discard rule
     * @param iface allowed interface
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void setIngressDiscardRule(final InetAddress address, final String iface) {
        throwIfPreT("setIngressDiscardRule is not available on pre-T devices");

        if (sIngressDiscardMap == null) return;

        final int ifIndex = mDeps.getIfIndex(iface);
        if (ifIndex == 0) {
            Log.e(TAG, "Failed to get if index, skip setting ingress discard rule for " + address
                    + "(" + iface + ")");
            return;
        }
        try {
            sIngressDiscardMap.updateEntry(new IngressDiscardKey(address),
                    new IngressDiscardValue(ifIndex, ifIndex));
        } catch (ErrnoException e) {
            Log.e(TAG, "Failed to set ingress discard rule for " + address + "("
                    + iface + "), " + e);
        }
    }

    /**
     * Remove ingress discard rule
     *
     * @param address target address to remove the ingress discard rule
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void removeIngressDiscardRule(final InetAddress address) {
        throwIfPreT("removeIngressDiscardRule is not available on pre-T devices");

        if (sIngressDiscardMap == null) return;

        try {
            sIngressDiscardMap.deleteEntry(new IngressDiscardKey(address));
        } catch (ErrnoException e) {
            Log.e(TAG, "Failed to remove ingress discard rule for " + address + ", " + e);
        }
    }

    /** Register callback for statsd to pull atom. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void setPullAtomCallback(final Context context) {
        throwIfPreT("setPullAtomCallback is not available on pre-T devices");

        final StatsManager statsManager = context.getSystemService(StatsManager.class);
        statsManager.setPullAtomCallback(NETWORK_BPF_MAP_INFO, null /* metadata */,
                BackgroundThread.getExecutor(), this::pullBpfMapInfoAtom);
    }

    private <K extends Struct, V extends Struct> int getMapSize(IBpfMap<K, V> map)
            throws ErrnoException {
        // forEach could restart iteration from the beginning if there is a concurrent entry
        // deletion. netd and skDestroyListener could delete CookieTagMap entry concurrently.
        // So using Set to count the number of entry in the map.
        Set<K> keySet = new ArraySet<>();
        if (map != null) {
            map.forEach((k, v) -> keySet.add(k));
        }
        return keySet.size();
    }

    /** Callback for StatsManager#setPullAtomCallback */
    @VisibleForTesting
    public int pullBpfMapInfoAtom(final int atomTag, final List<StatsEvent> data) {
        if (atomTag != NETWORK_BPF_MAP_INFO) {
            Log.e(TAG, "Unexpected atom tag: " + atomTag);
            return StatsManager.PULL_SKIP;
        }

        try {
            data.add(mDeps.buildStatsEvent(getMapSize(sCookieTagMap), getMapSize(sUidOwnerMap),
                    getMapSize(sUidPermissionMap)));
        } catch (ErrnoException e) {
            Log.e(TAG, "Failed to pull NETWORK_BPF_MAP_INFO atom: " + e);
            return StatsManager.PULL_SKIP;
        }
        return StatsManager.PULL_SUCCESS;
    }

    private String permissionToString(int permissionMask) {
        if (permissionMask == PERMISSION_NONE) {
            return "PERMISSION_NONE";
        }
        if (permissionMask == PERMISSION_UNINSTALLED) {
            // PERMISSION_UNINSTALLED should never appear in the map
            return "PERMISSION_UNINSTALLED error!";
        }

        final StringJoiner sj = new StringJoiner(" ");
        for (Pair<Integer, String> permission: PERMISSION_LIST) {
            final int permissionFlag = permission.first;
            final String permissionName = permission.second;
            if ((permissionMask & permissionFlag) != 0) {
                sj.add(permissionName);
                permissionMask &= ~permissionFlag;
            }
        }
        if (permissionMask != 0) {
            sj.add("PERMISSION_UNKNOWN(" + permissionMask + ")");
        }
        return sj.toString();
    }

    private void dumpOwnerMatchConfig(final IndentingPrintWriter pw) {
        if (sConfigurationMap == null) return;

        try {
            final long match = sConfigurationMap.getValue(UID_RULES_CONFIGURATION_KEY).val;
            pw.println("current ownerMatch configuration: " + match + " " + matchToString(match));
        } catch (ErrnoException e) {
            pw.println("Failed to read ownerMatch configuration: " + e);
        }
    }

    private void dumpCurrentStatsMapConfig(final IndentingPrintWriter pw) {
        if (sConfigurationMap == null) return;

        try {
            final long config = sConfigurationMap.getValue(CURRENT_STATS_MAP_CONFIGURATION_KEY).val;
            final String currentStatsMap =
                    (config == STATS_SELECT_MAP_A) ? "SELECT_MAP_A" : "SELECT_MAP_B";
            pw.println("current statsMap configuration: " + config + " " + currentStatsMap);
        } catch (ErrnoException e) {
            pw.println("Falied to read current statsMap configuration: " + e);
        }
    }

    private void dumpDataSaverConfig(final IndentingPrintWriter pw) {
        if (sDataSaverEnabledMap == null) return;

        try {
            final short config = sDataSaverEnabledMap.getValue(DATA_SAVER_ENABLED_KEY).val;
            // Any non-zero value converted from short to boolean is true by convention.
            pw.println("sDataSaverEnabledMap: " + (config != DATA_SAVER_DISABLED));
        } catch (ErrnoException e) {
            pw.println("Failed to read data saver configuration: " + e);
        }
    }
    /**
     * Dump BPF maps
     *
     * @param pw print writer
     * @param fd file descriptor to output
     * @param verbose verbose dump flag, if true dump the BpfMap contents
     * @throws IOException when file descriptor is invalid.
     * @throws ServiceSpecificException when the method is called on an unsupported device.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void dump(final IndentingPrintWriter pw, final FileDescriptor fd, boolean verbose)
            throws IOException, ServiceSpecificException {
        if (!SdkLevel.isAtLeastT()) {
            throw new ServiceSpecificException(
                    EOPNOTSUPP, "dumpsys connectivity trafficcontroller dump not available on pre-T"
                    + " devices, use dumpsys netd trafficcontroller instead.");
        }

        pw.println("TrafficController");  // required by CTS testDumpBpfNetMaps

        pw.println();
        if (verbose) {
            pw.println();
            pw.println("BPF map content:");
            pw.increaseIndent();

            dumpOwnerMatchConfig(pw);
            dumpCurrentStatsMapConfig(pw);
            pw.println();

            // TODO: Remove CookieTagMap content dump
            // NetworkStatsService also dumps CookieTagMap and NetworkStatsService is a right place
            // to dump CookieTagMap. But the TagSocketTest in CTS depends on this dump so the tests
            // need to be updated before remove the dump from BpfNetMaps.
            BpfDump.dumpMap(sCookieTagMap, pw, "sCookieTagMap",
                    (key, value) -> "cookie=" + key.socketCookie
                            + " tag=0x" + Long.toHexString(value.tag)
                            + " uid=" + value.uid);
            BpfDump.dumpMap(sUidOwnerMap, pw, "sUidOwnerMap",
                    (uid, match) -> {
                        if ((match.rule & IIF_MATCH) != 0) {
                            // TODO: convert interface index to interface name by IfaceIndexNameMap
                            return uid.val + " " + matchToString(match.rule) + " " + match.iif;
                        } else {
                            return uid.val + " " + matchToString(match.rule);
                        }
                    });
            BpfDump.dumpMap(sUidPermissionMap, pw, "sUidPermissionMap",
                    (uid, permission) -> uid.val + " " + permissionToString(permission.val));
            BpfDump.dumpMap(sIngressDiscardMap, pw, "sIngressDiscardMap",
                    (key, value) -> "[" + key.dstAddr + "]: "
                            + value.iif1 + "(" + mDeps.getIfName(value.iif1) + "), "
                            + value.iif2 + "(" + mDeps.getIfName(value.iif2) + ")");
            dumpDataSaverConfig(pw);
            pw.decreaseIndent();
        }
    }
}
