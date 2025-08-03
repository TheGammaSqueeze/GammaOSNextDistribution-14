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

package android.net;

import static android.net.BpfNetMapsConstants.CONFIGURATION_MAP_PATH;
import static android.net.BpfNetMapsConstants.DATA_SAVER_ENABLED;
import static android.net.BpfNetMapsConstants.DATA_SAVER_ENABLED_KEY;
import static android.net.BpfNetMapsConstants.DATA_SAVER_ENABLED_MAP_PATH;
import static android.net.BpfNetMapsConstants.HAPPY_BOX_MATCH;
import static android.net.BpfNetMapsConstants.PENALTY_BOX_MATCH;
import static android.net.BpfNetMapsConstants.UID_OWNER_MAP_PATH;
import static android.net.BpfNetMapsConstants.UID_RULES_CONFIGURATION_KEY;
import static android.net.BpfNetMapsUtils.getMatchByFirewallChain;
import static android.net.BpfNetMapsUtils.isFirewallAllowList;
import static android.net.BpfNetMapsUtils.throwIfPreT;
import static android.net.ConnectivityManager.FIREWALL_RULE_ALLOW;
import static android.net.ConnectivityManager.FIREWALL_RULE_DENY;

import android.annotation.NonNull;
import android.annotation.RequiresApi;
import android.os.Build;
import android.os.ServiceSpecificException;
import android.system.ErrnoException;
import android.system.Os;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.BpfMap;
import com.android.net.module.util.IBpfMap;
import com.android.net.module.util.Struct.S32;
import com.android.net.module.util.Struct.U32;
import com.android.net.module.util.Struct.U8;

/**
 * A helper class to *read* java BpfMaps.
 * @hide
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)  // BPF maps were only mainlined in T
public class BpfNetMapsReader {
    private static final String TAG = BpfNetMapsReader.class.getSimpleName();

    // Locally store the handle of bpf maps. The FileDescriptors are statically cached inside the
    // BpfMap implementation.

    // Bpf map to store various networking configurations, the format of the value is different
    // for different keys. See BpfNetMapsConstants#*_CONFIGURATION_KEY for keys.
    private final IBpfMap<S32, U32> mConfigurationMap;
    // Bpf map to store per uid traffic control configurations.
    // See {@link UidOwnerValue} for more detail.
    private final IBpfMap<S32, UidOwnerValue> mUidOwnerMap;
    private final IBpfMap<S32, U8> mDataSaverEnabledMap;
    private final Dependencies mDeps;

    // Bitmaps for calculating whether a given uid is blocked by firewall chains.
    private static final long sMaskDropIfSet;
    private static final long sMaskDropIfUnset;

    static {
        long maskDropIfSet = 0L;
        long maskDropIfUnset = 0L;

        for (int chain : BpfNetMapsConstants.ALLOW_CHAINS) {
            final long match = getMatchByFirewallChain(chain);
            maskDropIfUnset |= match;
        }
        for (int chain : BpfNetMapsConstants.DENY_CHAINS) {
            final long match = getMatchByFirewallChain(chain);
            maskDropIfSet |= match;
        }
        sMaskDropIfSet = maskDropIfSet;
        sMaskDropIfUnset = maskDropIfUnset;
    }

    private static class SingletonHolder {
        static final BpfNetMapsReader sInstance = new BpfNetMapsReader();
    }

    @NonNull
    public static BpfNetMapsReader getInstance() {
        return SingletonHolder.sInstance;
    }

    private BpfNetMapsReader() {
        this(new Dependencies());
    }

    // While the production code uses the singleton to optimize for performance and deal with
    // concurrent access, the test needs to use a non-static approach for dependency injection and
    // mocking virtual bpf maps.
    @VisibleForTesting
    public BpfNetMapsReader(@NonNull Dependencies deps) {
        if (!SdkLevel.isAtLeastT()) {
            throw new UnsupportedOperationException(
                    BpfNetMapsReader.class.getSimpleName() + " is not supported below Android T");
        }
        mDeps = deps;
        mConfigurationMap = mDeps.getConfigurationMap();
        mUidOwnerMap = mDeps.getUidOwnerMap();
        mDataSaverEnabledMap = mDeps.getDataSaverEnabledMap();
    }

    /**
     * Dependencies of BpfNetMapReader, for injection in tests.
     */
    @VisibleForTesting
    public static class Dependencies {
        /** Get the configuration map. */
        public IBpfMap<S32, U32> getConfigurationMap() {
            try {
                return new BpfMap<>(CONFIGURATION_MAP_PATH, BpfMap.BPF_F_RDONLY,
                        S32.class, U32.class);
            } catch (ErrnoException e) {
                return null;
            }
        }

        /** Get the uid owner map. */
        public IBpfMap<S32, UidOwnerValue> getUidOwnerMap() {
            try {
                return new BpfMap<>(UID_OWNER_MAP_PATH, BpfMap.BPF_F_RDONLY,
                        S32.class, UidOwnerValue.class);
            } catch (ErrnoException e) {
                return null;
            }
        }

        /** Get the data saver enabled map. */
        public  IBpfMap<S32, U8> getDataSaverEnabledMap() {
            try {
                return new BpfMap<>(DATA_SAVER_ENABLED_MAP_PATH, BpfMap.BPF_F_RDONLY, S32.class,
                        U8.class);
            } catch (ErrnoException e) {
                return null;
            }
        }
    }

    /**
     * Get the specified firewall chain's status.
     *
     * @param chain target chain
     * @return {@code true} if chain is enabled, {@code false} if chain is not enabled.
     * @throws UnsupportedOperationException if called on pre-T devices.
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     */
    public boolean isChainEnabled(final int chain) {
        return isChainEnabled(mConfigurationMap, chain);
    }

    /**
     * Get firewall rule of specified firewall chain on specified uid.
     *
     * @param chain target chain
     * @param uid        target uid
     * @return either {@link ConnectivityManager#FIREWALL_RULE_ALLOW} or
     *         {@link ConnectivityManager#FIREWALL_RULE_DENY}.
     * @throws UnsupportedOperationException if called on pre-T devices.
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     */
    public int getUidRule(final int chain, final int uid) {
        return getUidRule(mUidOwnerMap, chain, uid);
    }

    /**
     * Get the specified firewall chain's status.
     *
     * @param configurationMap target configurationMap
     * @param chain target chain
     * @return {@code true} if chain is enabled, {@code false} if chain is not enabled.
     * @throws UnsupportedOperationException if called on pre-T devices.
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     */
    public static boolean isChainEnabled(
            final IBpfMap<S32, U32> configurationMap, final int chain) {
        throwIfPreT("isChainEnabled is not available on pre-T devices");

        if (configurationMap == null) return false;

        final long match = getMatchByFirewallChain(chain);
        try {
            final U32 config = configurationMap.getValue(UID_RULES_CONFIGURATION_KEY);
            return (config.val & match) != 0;
        } catch (ErrnoException e) {
            throw new ServiceSpecificException(e.errno,
                    "Unable to get firewall chain status: " + Os.strerror(e.errno));
        }
    }

    /**
     * Get firewall rule of specified firewall chain on specified uid.
     *
     * @param uidOwnerMap target uidOwnerMap.
     * @param chain target chain.
     * @param uid target uid.
     * @return either FIREWALL_RULE_ALLOW or FIREWALL_RULE_DENY
     * @throws UnsupportedOperationException if called on pre-T devices.
     * @throws ServiceSpecificException      in case of failure, with an error code indicating the
     *                                       cause of the failure.
     */
    public static int getUidRule(final IBpfMap<S32, UidOwnerValue> uidOwnerMap,
            final int chain, final int uid) {
        throwIfPreT("getUidRule is not available on pre-T devices");

        if (uidOwnerMap == null) return FIREWALL_RULE_ALLOW;

        final long match = getMatchByFirewallChain(chain);
        final boolean isAllowList = isFirewallAllowList(chain);
        try {
            final UidOwnerValue uidMatch = uidOwnerMap.getValue(new S32(uid));
            final boolean isMatchEnabled = uidMatch != null && (uidMatch.rule & match) != 0;
            return isMatchEnabled == isAllowList ? FIREWALL_RULE_ALLOW : FIREWALL_RULE_DENY;
        } catch (ErrnoException e) {
            throw new ServiceSpecificException(e.errno,
                    "Unable to get uid rule status: " + Os.strerror(e.errno));
        }
    }

    /**
     * Return whether the network is blocked by firewall chains for the given uid.
     *
     * @param uid The target uid.
     * @param isNetworkMetered Whether the target network is metered.
     * @param isDataSaverEnabled Whether the data saver is enabled.
     *
     * @return True if the network is blocked. Otherwise, false.
     * @throws ServiceSpecificException if the read fails.
     *
     * @hide
     */
    public boolean isUidNetworkingBlocked(final int uid, boolean isNetworkMetered,
            boolean isDataSaverEnabled) {
        throwIfPreT("isUidBlockedByFirewallChains is not available on pre-T devices");

        if (mConfigurationMap == null) return false;

        final long uidRuleConfig;
        final long uidMatch;
        try {
            uidRuleConfig = mConfigurationMap.getValue(UID_RULES_CONFIGURATION_KEY).val;
            final UidOwnerValue value = mUidOwnerMap.getValue(new S32(uid));
            uidMatch = (value != null) ? value.rule : 0L;
        } catch (ErrnoException e) {
            throw new ServiceSpecificException(e.errno,
                    "Unable to get firewall chain status: " + Os.strerror(e.errno));
        }

        final boolean blockedByAllowChains = 0 != (uidRuleConfig & ~uidMatch & sMaskDropIfUnset);
        final boolean blockedByDenyChains = 0 != (uidRuleConfig & uidMatch & sMaskDropIfSet);
        if (blockedByAllowChains || blockedByDenyChains) {
            return true;
        }

        if (!isNetworkMetered) return false;
        if ((uidMatch & PENALTY_BOX_MATCH) != 0) return true;
        if ((uidMatch & HAPPY_BOX_MATCH) != 0) return false;
        return isDataSaverEnabled;
    }

    /**
     * Get Data Saver enabled or disabled
     *
     * @return whether Data Saver is enabled or disabled.
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *                                  cause of the failure.
     */
    public boolean getDataSaverEnabled() {
        throwIfPreT("getDataSaverEnabled is not available on pre-T devices");

        if (mDataSaverEnabledMap == null) return false;
        try {
            return mDataSaverEnabledMap.getValue(DATA_SAVER_ENABLED_KEY).val == DATA_SAVER_ENABLED;
        } catch (ErrnoException e) {
            throw new ServiceSpecificException(e.errno, "Unable to get data saver: "
                    + Os.strerror(e.errno));
        }
    }
}
