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

import static android.net.BpfNetMapsConstants.ALLOW_CHAINS;
import static android.net.BpfNetMapsConstants.CURRENT_STATS_MAP_CONFIGURATION_KEY;
import static android.net.BpfNetMapsConstants.DATA_SAVER_ENABLED_KEY;
import static android.net.BpfNetMapsConstants.DATA_SAVER_DISABLED;
import static android.net.BpfNetMapsConstants.DATA_SAVER_ENABLED;
import static android.net.BpfNetMapsConstants.DENY_CHAINS;
import static android.net.BpfNetMapsConstants.DOZABLE_MATCH;
import static android.net.BpfNetMapsConstants.HAPPY_BOX_MATCH;
import static android.net.BpfNetMapsConstants.IIF_MATCH;
import static android.net.BpfNetMapsConstants.LOCKDOWN_VPN_MATCH;
import static android.net.BpfNetMapsConstants.LOW_POWER_STANDBY_MATCH;
import static android.net.BpfNetMapsConstants.NO_MATCH;
import static android.net.BpfNetMapsConstants.OEM_DENY_1_MATCH;
import static android.net.BpfNetMapsConstants.OEM_DENY_2_MATCH;
import static android.net.BpfNetMapsConstants.OEM_DENY_3_MATCH;
import static android.net.BpfNetMapsConstants.PENALTY_BOX_MATCH;
import static android.net.BpfNetMapsConstants.POWERSAVE_MATCH;
import static android.net.BpfNetMapsConstants.RESTRICTED_MATCH;
import static android.net.BpfNetMapsConstants.STANDBY_MATCH;
import static android.net.BpfNetMapsConstants.UID_RULES_CONFIGURATION_KEY;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_DOZABLE;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_LOW_POWER_STANDBY;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_1;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_2;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_3;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_POWERSAVE;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_RESTRICTED;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_STANDBY;
import static android.net.ConnectivityManager.FIREWALL_RULE_ALLOW;
import static android.net.ConnectivityManager.FIREWALL_RULE_DENY;
import static android.net.INetd.PERMISSION_INTERNET;
import static android.net.INetd.PERMISSION_NONE;
import static android.net.INetd.PERMISSION_UNINSTALLED;
import static android.net.INetd.PERMISSION_UPDATE_DEVICE_STATS;
import static android.system.OsConstants.EINVAL;
import static android.system.OsConstants.EPERM;

import static com.android.server.ConnectivityStatsLog.NETWORK_BPF_MAP_INFO;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.StatsManager;
import android.content.Context;
import android.net.BpfNetMapsUtils;
import android.net.INetd;
import android.net.InetAddresses;
import android.net.UidOwnerValue;
import android.os.Build;
import android.os.ServiceSpecificException;
import android.system.ErrnoException;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.IBpfMap;
import com.android.net.module.util.Struct.S32;
import com.android.net.module.util.Struct.U32;
import com.android.net.module.util.Struct.U8;
import com.android.net.module.util.bpf.CookieTagMapKey;
import com.android.net.module.util.bpf.CookieTagMapValue;
import com.android.net.module.util.bpf.IngressDiscardKey;
import com.android.net.module.util.bpf.IngressDiscardValue;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreAfter;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.TestBpfMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;
import java.io.StringWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.ArrayList;
import java.util.List;

@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
public final class BpfNetMapsTest {
    private static final String TAG = "BpfNetMapsTest";

    @Rule
    public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    private static final int TEST_UID = 10086;
    private static final int[] TEST_UIDS = {10002, 10003};
    private static final String TEST_IF_NAME = "wlan0";
    private static final int TEST_IF_INDEX = 7;
    private static final int NO_IIF = 0;
    private static final int NULL_IIF = 0;
    private static final Inet4Address TEST_V4_ADDRESS =
            (Inet4Address) InetAddresses.parseNumericAddress("192.0.2.1");
    private static final Inet6Address TEST_V6_ADDRESS =
            (Inet6Address) InetAddresses.parseNumericAddress("2001:db8::1");
    private static final String CHAINNAME = "fw_dozable";

    private static final long STATS_SELECT_MAP_A = 0;
    private static final long STATS_SELECT_MAP_B = 1;

    private static final List<Integer> FIREWALL_CHAINS = new ArrayList<>();
    static {
        FIREWALL_CHAINS.addAll(ALLOW_CHAINS);
        FIREWALL_CHAINS.addAll(DENY_CHAINS);
    }

    private BpfNetMaps mBpfNetMaps;

    @Mock INetd mNetd;
    @Mock BpfNetMaps.Dependencies mDeps;
    @Mock Context mContext;
    private final IBpfMap<S32, U32> mConfigurationMap = new TestBpfMap<>(S32.class, U32.class);
    private final IBpfMap<S32, UidOwnerValue> mUidOwnerMap =
            new TestBpfMap<>(S32.class, UidOwnerValue.class);
    private final IBpfMap<S32, U8> mUidPermissionMap = new TestBpfMap<>(S32.class, U8.class);
    private final IBpfMap<CookieTagMapKey, CookieTagMapValue> mCookieTagMap =
            spy(new TestBpfMap<>(CookieTagMapKey.class, CookieTagMapValue.class));
    private final IBpfMap<S32, U8> mDataSaverEnabledMap = new TestBpfMap<>(S32.class, U8.class);
    private final IBpfMap<IngressDiscardKey, IngressDiscardValue> mIngressDiscardMap =
            new TestBpfMap<>(IngressDiscardKey.class, IngressDiscardValue.class);

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(TEST_IF_INDEX).when(mDeps).getIfIndex(TEST_IF_NAME);
        doReturn(TEST_IF_NAME).when(mDeps).getIfName(TEST_IF_INDEX);
        doReturn(0).when(mDeps).synchronizeKernelRCU();
        BpfNetMaps.setConfigurationMapForTest(mConfigurationMap);
        mConfigurationMap.updateEntry(UID_RULES_CONFIGURATION_KEY, new U32(0));
        mConfigurationMap.updateEntry(
                CURRENT_STATS_MAP_CONFIGURATION_KEY, new U32(STATS_SELECT_MAP_A));
        BpfNetMaps.setUidOwnerMapForTest(mUidOwnerMap);
        BpfNetMaps.setUidPermissionMapForTest(mUidPermissionMap);
        BpfNetMaps.setCookieTagMapForTest(mCookieTagMap);
        BpfNetMaps.setDataSaverEnabledMapForTest(mDataSaverEnabledMap);
        mDataSaverEnabledMap.updateEntry(DATA_SAVER_ENABLED_KEY, new U8(DATA_SAVER_DISABLED));
        BpfNetMaps.setIngressDiscardMapForTest(mIngressDiscardMap);
        mBpfNetMaps = new BpfNetMaps(mContext, mNetd, mDeps);
    }

    @Test
    public void testBpfNetMapsBeforeT() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        mBpfNetMaps.addUidInterfaceRules(TEST_IF_NAME, TEST_UIDS);
        verify(mNetd).firewallAddUidInterfaceRules(TEST_IF_NAME, TEST_UIDS);
        mBpfNetMaps.removeUidInterfaceRules(TEST_UIDS);
        verify(mNetd).firewallRemoveUidInterfaceRules(TEST_UIDS);
        mBpfNetMaps.setNetPermForUids(PERMISSION_INTERNET, TEST_UIDS);
        verify(mNetd).trafficSetNetPermForUids(PERMISSION_INTERNET, TEST_UIDS);
    }

    private long getMatch(final List<Integer> chains) {
        long match = 0;
        for (final int chain: chains) {
            match |= BpfNetMapsUtils.getMatchByFirewallChain(chain);
        }
        return match;
    }

    private void doTestIsChainEnabled(final List<Integer> enableChains) throws Exception {
        mConfigurationMap.updateEntry(UID_RULES_CONFIGURATION_KEY, new U32(getMatch(enableChains)));

        for (final int chain: FIREWALL_CHAINS) {
            final String testCase = "EnabledChains: " + enableChains + " CheckedChain: " + chain;
            if (enableChains.contains(chain)) {
                assertTrue("Expected isChainEnabled returns True, " + testCase,
                        mBpfNetMaps.isChainEnabled(chain));
            } else {
                assertFalse("Expected isChainEnabled returns False, " + testCase,
                        mBpfNetMaps.isChainEnabled(chain));
            }
        }
    }

    private void doTestIsChainEnabled(final int enableChain) throws Exception {
        doTestIsChainEnabled(List.of(enableChain));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testIsChainEnabled() throws Exception {
        doTestIsChainEnabled(FIREWALL_CHAIN_DOZABLE);
        doTestIsChainEnabled(FIREWALL_CHAIN_STANDBY);
        doTestIsChainEnabled(FIREWALL_CHAIN_POWERSAVE);
        doTestIsChainEnabled(FIREWALL_CHAIN_RESTRICTED);
        doTestIsChainEnabled(FIREWALL_CHAIN_LOW_POWER_STANDBY);
        doTestIsChainEnabled(FIREWALL_CHAIN_OEM_DENY_1);
        doTestIsChainEnabled(FIREWALL_CHAIN_OEM_DENY_2);
        doTestIsChainEnabled(FIREWALL_CHAIN_OEM_DENY_3);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testIsChainEnabledMultipleChainEnabled() throws Exception {
        doTestIsChainEnabled(List.of(
                FIREWALL_CHAIN_DOZABLE,
                FIREWALL_CHAIN_STANDBY));
        doTestIsChainEnabled(List.of(
                FIREWALL_CHAIN_DOZABLE,
                FIREWALL_CHAIN_STANDBY,
                FIREWALL_CHAIN_POWERSAVE,
                FIREWALL_CHAIN_RESTRICTED));
        doTestIsChainEnabled(FIREWALL_CHAINS);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testIsChainEnabledInvalidChain() {
        final Class<ServiceSpecificException> expected = ServiceSpecificException.class;
        assertThrows(expected, () -> mBpfNetMaps.isChainEnabled(-1 /* childChain */));
        assertThrows(expected, () -> mBpfNetMaps.isChainEnabled(1000 /* childChain */));
    }

    @Test
    @IgnoreAfter(Build.VERSION_CODES.S_V2)
    public void testIsChainEnabledBeforeT() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBpfNetMaps.isChainEnabled(FIREWALL_CHAIN_DOZABLE));
    }

    private void doTestSetChildChain(final List<Integer> testChains) throws Exception {
        long expectedMatch = 0;
        for (final int chain: testChains) {
            expectedMatch |= BpfNetMapsUtils.getMatchByFirewallChain(chain);
        }

        assertEquals(0, mConfigurationMap.getValue(UID_RULES_CONFIGURATION_KEY).val);

        for (final int chain: testChains) {
            mBpfNetMaps.setChildChain(chain, true /* enable */);
        }
        assertEquals(expectedMatch, mConfigurationMap.getValue(UID_RULES_CONFIGURATION_KEY).val);

        for (final int chain: testChains) {
            mBpfNetMaps.setChildChain(chain, false /* enable */);
        }
        assertEquals(0, mConfigurationMap.getValue(UID_RULES_CONFIGURATION_KEY).val);
    }

    private void doTestSetChildChain(final int testChain) throws Exception {
        doTestSetChildChain(List.of(testChain));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetChildChain() throws Exception {
        mConfigurationMap.updateEntry(UID_RULES_CONFIGURATION_KEY, new U32(0));
        doTestSetChildChain(FIREWALL_CHAIN_DOZABLE);
        doTestSetChildChain(FIREWALL_CHAIN_STANDBY);
        doTestSetChildChain(FIREWALL_CHAIN_POWERSAVE);
        doTestSetChildChain(FIREWALL_CHAIN_RESTRICTED);
        doTestSetChildChain(FIREWALL_CHAIN_LOW_POWER_STANDBY);
        doTestSetChildChain(FIREWALL_CHAIN_OEM_DENY_1);
        doTestSetChildChain(FIREWALL_CHAIN_OEM_DENY_2);
        doTestSetChildChain(FIREWALL_CHAIN_OEM_DENY_3);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetChildChainMultipleChain() throws Exception {
        mConfigurationMap.updateEntry(UID_RULES_CONFIGURATION_KEY, new U32(0));
        doTestSetChildChain(List.of(
                FIREWALL_CHAIN_DOZABLE,
                FIREWALL_CHAIN_STANDBY));
        doTestSetChildChain(List.of(
                FIREWALL_CHAIN_DOZABLE,
                FIREWALL_CHAIN_STANDBY,
                FIREWALL_CHAIN_POWERSAVE,
                FIREWALL_CHAIN_RESTRICTED));
        doTestSetChildChain(FIREWALL_CHAINS);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetChildChainInvalidChain() {
        final Class<ServiceSpecificException> expected = ServiceSpecificException.class;
        assertThrows(expected,
                () -> mBpfNetMaps.setChildChain(-1 /* childChain */, true /* enable */));
        assertThrows(expected,
                () -> mBpfNetMaps.setChildChain(1000 /* childChain */, true /* enable */));
    }

    @Test
    @IgnoreAfter(Build.VERSION_CODES.S_V2)
    public void testSetChildChainBeforeT() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBpfNetMaps.setChildChain(FIREWALL_CHAIN_DOZABLE, true /* enable */));
    }

    private void checkUidOwnerValue(final int uid, final int expectedIif,
            final long expectedMatch) throws Exception {
        final UidOwnerValue config = mUidOwnerMap.getValue(new S32(uid));
        if (expectedMatch == 0) {
            assertNull(config);
        } else {
            assertEquals(expectedIif, config.iif);
            assertEquals(expectedMatch, config.rule);
        }
    }

    private void doTestRemoveNaughtyApp(final int iif, final long match) throws Exception {
        mUidOwnerMap.updateEntry(new S32(TEST_UID), new UidOwnerValue(iif, match));

        mBpfNetMaps.removeNaughtyApp(TEST_UID);

        checkUidOwnerValue(TEST_UID, iif, match & ~PENALTY_BOX_MATCH);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testRemoveNaughtyApp() throws Exception {
        doTestRemoveNaughtyApp(NO_IIF, PENALTY_BOX_MATCH);

        // PENALTY_BOX_MATCH with other matches
        doTestRemoveNaughtyApp(NO_IIF, PENALTY_BOX_MATCH | DOZABLE_MATCH | POWERSAVE_MATCH);

        // PENALTY_BOX_MATCH with IIF_MATCH
        doTestRemoveNaughtyApp(TEST_IF_INDEX, PENALTY_BOX_MATCH | IIF_MATCH);

        // PENALTY_BOX_MATCH is not enabled
        doTestRemoveNaughtyApp(NO_IIF, DOZABLE_MATCH | POWERSAVE_MATCH | RESTRICTED_MATCH);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testRemoveNaughtyAppMissingUid() {
        // UidOwnerMap does not have entry for TEST_UID
        assertThrows(ServiceSpecificException.class,
                () -> mBpfNetMaps.removeNaughtyApp(TEST_UID));
    }

    @Test
    @IgnoreAfter(Build.VERSION_CODES.S_V2)
    public void testRemoveNaughtyAppBeforeT() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBpfNetMaps.removeNaughtyApp(TEST_UID));
    }

    private void doTestAddNaughtyApp(final int iif, final long match) throws Exception {
        if (match != NO_MATCH) {
            mUidOwnerMap.updateEntry(new S32(TEST_UID), new UidOwnerValue(iif, match));
        }

        mBpfNetMaps.addNaughtyApp(TEST_UID);

        checkUidOwnerValue(TEST_UID, iif, match | PENALTY_BOX_MATCH);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testAddNaughtyApp() throws Exception {
        doTestAddNaughtyApp(NO_IIF, NO_MATCH);

        // Other matches are enabled
        doTestAddNaughtyApp(NO_IIF, DOZABLE_MATCH | POWERSAVE_MATCH | RESTRICTED_MATCH);

        // IIF_MATCH is enabled
        doTestAddNaughtyApp(TEST_IF_INDEX, IIF_MATCH);

        // PENALTY_BOX_MATCH is already enabled
        doTestAddNaughtyApp(NO_IIF, PENALTY_BOX_MATCH | DOZABLE_MATCH);
    }

    @Test
    @IgnoreAfter(Build.VERSION_CODES.S_V2)
    public void testAddNaughtyAppBeforeT() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBpfNetMaps.addNaughtyApp(TEST_UID));
    }

    private void doTestRemoveNiceApp(final int iif, final long match) throws Exception {
        mUidOwnerMap.updateEntry(new S32(TEST_UID), new UidOwnerValue(iif, match));

        mBpfNetMaps.removeNiceApp(TEST_UID);

        checkUidOwnerValue(TEST_UID, iif, match & ~HAPPY_BOX_MATCH);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testRemoveNiceApp() throws Exception {
        doTestRemoveNiceApp(NO_IIF, HAPPY_BOX_MATCH);

        // HAPPY_BOX_MATCH with other matches
        doTestRemoveNiceApp(NO_IIF, HAPPY_BOX_MATCH | DOZABLE_MATCH | POWERSAVE_MATCH);

        // HAPPY_BOX_MATCH with IIF_MATCH
        doTestRemoveNiceApp(TEST_IF_INDEX, HAPPY_BOX_MATCH | IIF_MATCH);

        // HAPPY_BOX_MATCH is not enabled
        doTestRemoveNiceApp(NO_IIF, DOZABLE_MATCH | POWERSAVE_MATCH | RESTRICTED_MATCH);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testRemoveNiceAppMissingUid() {
        // UidOwnerMap does not have entry for TEST_UID
        assertThrows(ServiceSpecificException.class,
                () -> mBpfNetMaps.removeNiceApp(TEST_UID));
    }

    @Test
    @IgnoreAfter(Build.VERSION_CODES.S_V2)
    public void testRemoveNiceAppBeforeT() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBpfNetMaps.removeNiceApp(TEST_UID));
    }

    private void doTestAddNiceApp(final int iif, final long match) throws Exception {
        if (match != NO_MATCH) {
            mUidOwnerMap.updateEntry(new S32(TEST_UID), new UidOwnerValue(iif, match));
        }

        mBpfNetMaps.addNiceApp(TEST_UID);

        checkUidOwnerValue(TEST_UID, iif, match | HAPPY_BOX_MATCH);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testAddNiceApp() throws Exception {
        doTestAddNiceApp(NO_IIF, NO_MATCH);

        // Other matches are enabled
        doTestAddNiceApp(NO_IIF, DOZABLE_MATCH | POWERSAVE_MATCH | RESTRICTED_MATCH);

        // IIF_MATCH is enabled
        doTestAddNiceApp(TEST_IF_INDEX, IIF_MATCH);

        // HAPPY_BOX_MATCH is already enabled
        doTestAddNiceApp(NO_IIF, HAPPY_BOX_MATCH | DOZABLE_MATCH);
    }

    @Test
    @IgnoreAfter(Build.VERSION_CODES.S_V2)
    public void testAddNiceAppBeforeT() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBpfNetMaps.addNiceApp(TEST_UID));
    }

    private void doTestUpdateUidLockdownRule(final int iif, final long match, final boolean add)
            throws Exception {
        if (match != NO_MATCH) {
            mUidOwnerMap.updateEntry(new S32(TEST_UID), new UidOwnerValue(iif, match));
        }

        mBpfNetMaps.updateUidLockdownRule(TEST_UID, add);

        final long expectedMatch = add ? match | LOCKDOWN_VPN_MATCH : match & ~LOCKDOWN_VPN_MATCH;
        checkUidOwnerValue(TEST_UID, iif, expectedMatch);
    }

    private static final boolean ADD = true;
    private static final boolean REMOVE = false;

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testUpdateUidLockdownRuleAddLockdown() throws Exception {
        doTestUpdateUidLockdownRule(NO_IIF, NO_MATCH, ADD);

        // Other matches are enabled
        doTestUpdateUidLockdownRule(
                NO_IIF, DOZABLE_MATCH | POWERSAVE_MATCH | RESTRICTED_MATCH, ADD);

        // IIF_MATCH is enabled
        doTestUpdateUidLockdownRule(TEST_IF_INDEX, DOZABLE_MATCH, ADD);

        // LOCKDOWN_VPN_MATCH is already enabled
        doTestUpdateUidLockdownRule(NO_IIF, LOCKDOWN_VPN_MATCH | DOZABLE_MATCH, ADD);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testUpdateUidLockdownRuleRemoveLockdown() throws Exception {
        doTestUpdateUidLockdownRule(NO_IIF, LOCKDOWN_VPN_MATCH, REMOVE);

        // LOCKDOWN_VPN_MATCH with other matches
        doTestUpdateUidLockdownRule(
                NO_IIF, LOCKDOWN_VPN_MATCH | POWERSAVE_MATCH | RESTRICTED_MATCH, REMOVE);

        // LOCKDOWN_VPN_MATCH with IIF_MATCH
        doTestUpdateUidLockdownRule(TEST_IF_INDEX, LOCKDOWN_VPN_MATCH | IIF_MATCH, REMOVE);

        // LOCKDOWN_VPN_MATCH is not enabled
        doTestUpdateUidLockdownRule(NO_IIF, POWERSAVE_MATCH | RESTRICTED_MATCH, REMOVE);
    }

    @Test
    @IgnoreAfter(Build.VERSION_CODES.S_V2)
    public void testUpdateUidLockdownRuleBeforeT() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBpfNetMaps.updateUidLockdownRule(TEST_UID, true /* add */));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testAddUidInterfaceRules() throws Exception {
        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];

        mBpfNetMaps.addUidInterfaceRules(TEST_IF_NAME, TEST_UIDS);

        checkUidOwnerValue(uid0, TEST_IF_INDEX, IIF_MATCH);
        checkUidOwnerValue(uid1, TEST_IF_INDEX, IIF_MATCH);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testAddUidInterfaceRulesWithOtherMatch() throws Exception {
        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];
        final long match0 = DOZABLE_MATCH;
        final long match1 = DOZABLE_MATCH | POWERSAVE_MATCH | RESTRICTED_MATCH;
        mUidOwnerMap.updateEntry(new S32(uid0), new UidOwnerValue(NO_IIF, match0));
        mUidOwnerMap.updateEntry(new S32(uid1), new UidOwnerValue(NO_IIF, match1));

        mBpfNetMaps.addUidInterfaceRules(TEST_IF_NAME, TEST_UIDS);

        checkUidOwnerValue(uid0, TEST_IF_INDEX, match0 | IIF_MATCH);
        checkUidOwnerValue(uid1, TEST_IF_INDEX, match1 | IIF_MATCH);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testAddUidInterfaceRulesWithExistingIifMatch() throws Exception {
        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];
        final long match0 = IIF_MATCH;
        final long match1 = IIF_MATCH | DOZABLE_MATCH | POWERSAVE_MATCH | RESTRICTED_MATCH;
        mUidOwnerMap.updateEntry(new S32(uid0), new UidOwnerValue(TEST_IF_INDEX + 1, match0));
        mUidOwnerMap.updateEntry(new S32(uid1), new UidOwnerValue(NULL_IIF, match1));

        mBpfNetMaps.addUidInterfaceRules(TEST_IF_NAME, TEST_UIDS);

        checkUidOwnerValue(uid0, TEST_IF_INDEX, match0);
        checkUidOwnerValue(uid1, TEST_IF_INDEX, match1);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testAddUidInterfaceRulesGetIfIndexFail() {
        doReturn(0).when(mDeps).getIfIndex(TEST_IF_NAME);
        assertThrows(ServiceSpecificException.class,
                () -> mBpfNetMaps.addUidInterfaceRules(TEST_IF_NAME, TEST_UIDS));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testAddUidInterfaceRulesWithNullInterface() throws Exception {
        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];
        final long match0 = IIF_MATCH;
        final long match1 = IIF_MATCH | DOZABLE_MATCH | POWERSAVE_MATCH | RESTRICTED_MATCH;
        mUidOwnerMap.updateEntry(new S32(uid0), new UidOwnerValue(TEST_IF_INDEX, match0));
        mUidOwnerMap.updateEntry(new S32(uid1), new UidOwnerValue(NULL_IIF, match1));

        mBpfNetMaps.addUidInterfaceRules(null /* ifName */, TEST_UIDS);

        checkUidOwnerValue(uid0, NULL_IIF, match0);
        checkUidOwnerValue(uid1, NULL_IIF, match1);
    }

    private void doTestRemoveUidInterfaceRules(final int iif0, final long match0,
            final int iif1, final long match1) throws Exception {
        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];
        mUidOwnerMap.updateEntry(new S32(uid0), new UidOwnerValue(iif0, match0));
        mUidOwnerMap.updateEntry(new S32(uid1), new UidOwnerValue(iif1, match1));

        mBpfNetMaps.removeUidInterfaceRules(TEST_UIDS);

        checkUidOwnerValue(uid0, NO_IIF, match0 & ~IIF_MATCH);
        checkUidOwnerValue(uid1, NO_IIF, match1 & ~IIF_MATCH);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testRemoveUidInterfaceRules() throws Exception {
        doTestRemoveUidInterfaceRules(TEST_IF_INDEX, IIF_MATCH, NULL_IIF, IIF_MATCH);

        // IIF_MATCH and other matches are enabled
        doTestRemoveUidInterfaceRules(TEST_IF_INDEX, IIF_MATCH | DOZABLE_MATCH,
                NULL_IIF, IIF_MATCH | DOZABLE_MATCH | RESTRICTED_MATCH);

        // IIF_MATCH is not enabled
        doTestRemoveUidInterfaceRules(NO_IIF, DOZABLE_MATCH,
                NO_IIF, DOZABLE_MATCH | POWERSAVE_MATCH | RESTRICTED_MATCH);
    }

    private void doTestSetUidRule(final List<Integer> testChains) throws Exception {
        mUidOwnerMap.updateEntry(new S32(TEST_UID), new UidOwnerValue(TEST_IF_INDEX, IIF_MATCH));

        for (final int chain: testChains) {
            final int ruleToAddMatch = BpfNetMapsUtils.isFirewallAllowList(chain)
                    ? FIREWALL_RULE_ALLOW : FIREWALL_RULE_DENY;
            mBpfNetMaps.setUidRule(chain, TEST_UID, ruleToAddMatch);
        }

        checkUidOwnerValue(TEST_UID, TEST_IF_INDEX, IIF_MATCH | getMatch(testChains));

        for (final int chain: testChains) {
            final int ruleToRemoveMatch = BpfNetMapsUtils.isFirewallAllowList(chain)
                    ? FIREWALL_RULE_DENY : FIREWALL_RULE_ALLOW;
            mBpfNetMaps.setUidRule(chain, TEST_UID, ruleToRemoveMatch);
        }

        checkUidOwnerValue(TEST_UID, TEST_IF_INDEX, IIF_MATCH);
    }

    private void doTestSetUidRule(final int testChain) throws Exception {
        doTestSetUidRule(List.of(testChain));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetUidRule() throws Exception {
        doTestSetUidRule(FIREWALL_CHAIN_DOZABLE);
        doTestSetUidRule(FIREWALL_CHAIN_STANDBY);
        doTestSetUidRule(FIREWALL_CHAIN_POWERSAVE);
        doTestSetUidRule(FIREWALL_CHAIN_RESTRICTED);
        doTestSetUidRule(FIREWALL_CHAIN_LOW_POWER_STANDBY);
        doTestSetUidRule(FIREWALL_CHAIN_OEM_DENY_1);
        doTestSetUidRule(FIREWALL_CHAIN_OEM_DENY_2);
        doTestSetUidRule(FIREWALL_CHAIN_OEM_DENY_3);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetUidRuleMultipleChain() throws Exception {
        doTestSetUidRule(List.of(
                FIREWALL_CHAIN_DOZABLE,
                FIREWALL_CHAIN_STANDBY));
        doTestSetUidRule(List.of(
                FIREWALL_CHAIN_DOZABLE,
                FIREWALL_CHAIN_STANDBY,
                FIREWALL_CHAIN_POWERSAVE,
                FIREWALL_CHAIN_RESTRICTED));
        doTestSetUidRule(FIREWALL_CHAINS);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetUidRuleRemoveRuleFromUidWithNoRule() {
        final Class<ServiceSpecificException> expected = ServiceSpecificException.class;
        assertThrows(expected,
                () -> mBpfNetMaps.setUidRule(FIREWALL_CHAIN_DOZABLE, TEST_UID, FIREWALL_RULE_DENY));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetUidRuleInvalidChain() {
        final Class<ServiceSpecificException> expected = ServiceSpecificException.class;
        assertThrows(expected,
                () -> mBpfNetMaps.setUidRule(-1 /* childChain */, TEST_UID, FIREWALL_RULE_ALLOW));
        assertThrows(expected,
                () -> mBpfNetMaps.setUidRule(1000 /* childChain */, TEST_UID, FIREWALL_RULE_ALLOW));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetUidRuleInvalidRule() {
        final Class<ServiceSpecificException> expected = ServiceSpecificException.class;
        assertThrows(expected, () ->
                mBpfNetMaps.setUidRule(FIREWALL_CHAIN_DOZABLE, TEST_UID, -1 /* firewallRule */));
        assertThrows(expected, () ->
                mBpfNetMaps.setUidRule(FIREWALL_CHAIN_DOZABLE, TEST_UID, 1000 /* firewallRule */));
    }

    @Test
    @IgnoreAfter(Build.VERSION_CODES.S_V2)
    public void testSetUidRuleBeforeT() {
        assertThrows(UnsupportedOperationException.class, () ->
                mBpfNetMaps.setUidRule(FIREWALL_CHAIN_DOZABLE, TEST_UID, FIREWALL_RULE_ALLOW));
    }

    private void doTestGetUidRule(final List<Integer> enableChains) throws Exception {
        mUidOwnerMap.updateEntry(new S32(TEST_UID), new UidOwnerValue(0, getMatch(enableChains)));

        for (final int chain: FIREWALL_CHAINS) {
            final String testCase = "EnabledChains: " + enableChains + " CheckedChain: " + chain;
            if (enableChains.contains(chain)) {
                final int expectedRule = BpfNetMapsUtils.isFirewallAllowList(chain)
                        ? FIREWALL_RULE_ALLOW : FIREWALL_RULE_DENY;
                assertEquals(testCase, expectedRule, mBpfNetMaps.getUidRule(chain, TEST_UID));
            } else {
                final int expectedRule = BpfNetMapsUtils.isFirewallAllowList(chain)
                        ? FIREWALL_RULE_DENY : FIREWALL_RULE_ALLOW;
                assertEquals(testCase, expectedRule, mBpfNetMaps.getUidRule(chain, TEST_UID));
            }
        }
    }

    private void doTestGetUidRule(final int enableChain) throws Exception {
        doTestGetUidRule(List.of(enableChain));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testGetUidRule() throws Exception {
        doTestGetUidRule(FIREWALL_CHAIN_DOZABLE);
        doTestGetUidRule(FIREWALL_CHAIN_STANDBY);
        doTestGetUidRule(FIREWALL_CHAIN_POWERSAVE);
        doTestGetUidRule(FIREWALL_CHAIN_RESTRICTED);
        doTestGetUidRule(FIREWALL_CHAIN_LOW_POWER_STANDBY);
        doTestGetUidRule(FIREWALL_CHAIN_OEM_DENY_1);
        doTestGetUidRule(FIREWALL_CHAIN_OEM_DENY_2);
        doTestGetUidRule(FIREWALL_CHAIN_OEM_DENY_3);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testGetUidRuleMultipleChainEnabled() throws Exception {
        doTestGetUidRule(List.of(
                FIREWALL_CHAIN_DOZABLE,
                FIREWALL_CHAIN_STANDBY));
        doTestGetUidRule(List.of(
                FIREWALL_CHAIN_DOZABLE,
                FIREWALL_CHAIN_STANDBY,
                FIREWALL_CHAIN_POWERSAVE,
                FIREWALL_CHAIN_RESTRICTED));
        doTestGetUidRule(FIREWALL_CHAINS);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testGetUidRuleNoEntry() throws Exception {
        mUidOwnerMap.clear();
        for (final int chain: FIREWALL_CHAINS) {
            final int expectedRule = BpfNetMapsUtils.isFirewallAllowList(chain)
                    ? FIREWALL_RULE_DENY : FIREWALL_RULE_ALLOW;
            assertEquals(expectedRule, mBpfNetMaps.getUidRule(chain, TEST_UID));
        }
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testGetUidRuleInvalidChain() {
        final Class<ServiceSpecificException> expected = ServiceSpecificException.class;
        assertThrows(expected, () -> mBpfNetMaps.getUidRule(-1 /* childChain */, TEST_UID));
        assertThrows(expected, () -> mBpfNetMaps.getUidRule(1000 /* childChain */, TEST_UID));
    }

    @Test
    @IgnoreAfter(Build.VERSION_CODES.S_V2)
    public void testGetUidRuleBeforeT() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBpfNetMaps.getUidRule(FIREWALL_CHAIN_DOZABLE, TEST_UID));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testReplaceUidChain() throws Exception {
        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];

        mBpfNetMaps.replaceUidChain(FIREWALL_CHAIN_DOZABLE, TEST_UIDS);

        checkUidOwnerValue(uid0, NO_IIF, DOZABLE_MATCH);
        checkUidOwnerValue(uid1, NO_IIF, DOZABLE_MATCH);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testReplaceUidChainWithOtherMatch() throws Exception {
        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];
        final long match0 = POWERSAVE_MATCH;
        final long match1 = POWERSAVE_MATCH | RESTRICTED_MATCH;
        mUidOwnerMap.updateEntry(new S32(uid0), new UidOwnerValue(NO_IIF, match0));
        mUidOwnerMap.updateEntry(new S32(uid1), new UidOwnerValue(NO_IIF, match1));

        mBpfNetMaps.replaceUidChain(FIREWALL_CHAIN_DOZABLE, new int[]{uid1});

        checkUidOwnerValue(uid0, NO_IIF, match0);
        checkUidOwnerValue(uid1, NO_IIF, match1 | DOZABLE_MATCH);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testReplaceUidChainWithExistingIifMatch() throws Exception {
        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];
        final long match0 = IIF_MATCH;
        final long match1 = IIF_MATCH | POWERSAVE_MATCH | RESTRICTED_MATCH;
        mUidOwnerMap.updateEntry(new S32(uid0), new UidOwnerValue(TEST_IF_INDEX, match0));
        mUidOwnerMap.updateEntry(new S32(uid1), new UidOwnerValue(NULL_IIF, match1));

        mBpfNetMaps.replaceUidChain(FIREWALL_CHAIN_DOZABLE, TEST_UIDS);

        checkUidOwnerValue(uid0, TEST_IF_INDEX, match0 | DOZABLE_MATCH);
        checkUidOwnerValue(uid1, NULL_IIF, match1 | DOZABLE_MATCH);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testReplaceUidChainRemoveExistingMatch() throws Exception {
        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];
        final long match0 = IIF_MATCH | DOZABLE_MATCH;
        final long match1 = IIF_MATCH | POWERSAVE_MATCH | RESTRICTED_MATCH;
        mUidOwnerMap.updateEntry(new S32(uid0), new UidOwnerValue(TEST_IF_INDEX, match0));
        mUidOwnerMap.updateEntry(new S32(uid1), new UidOwnerValue(NULL_IIF, match1));

        mBpfNetMaps.replaceUidChain(FIREWALL_CHAIN_DOZABLE, new int[]{uid1});

        checkUidOwnerValue(uid0, TEST_IF_INDEX, match0 & ~DOZABLE_MATCH);
        checkUidOwnerValue(uid1, NULL_IIF, match1 | DOZABLE_MATCH);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testReplaceUidChainInvalidChain() {
        final Class<IllegalArgumentException> expected = IllegalArgumentException.class;
        assertThrows(expected, () -> mBpfNetMaps.replaceUidChain(-1 /* chain */, TEST_UIDS));
        assertThrows(expected, () -> mBpfNetMaps.replaceUidChain(1000 /* chain */, TEST_UIDS));
    }

    @Test
    @IgnoreAfter(Build.VERSION_CODES.S_V2)
    public void testReplaceUidChainBeforeT() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBpfNetMaps.replaceUidChain(FIREWALL_CHAIN_DOZABLE, TEST_UIDS));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetNetPermForUidsGrantInternetPermission() throws Exception {
        mBpfNetMaps.setNetPermForUids(PERMISSION_INTERNET, TEST_UIDS);

        assertTrue(mUidPermissionMap.isEmpty());
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetNetPermForUidsGrantUpdateStatsPermission() throws Exception {
        mBpfNetMaps.setNetPermForUids(PERMISSION_UPDATE_DEVICE_STATS, TEST_UIDS);

        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];
        assertEquals(PERMISSION_UPDATE_DEVICE_STATS, mUidPermissionMap.getValue(new S32(uid0)).val);
        assertEquals(PERMISSION_UPDATE_DEVICE_STATS, mUidPermissionMap.getValue(new S32(uid1)).val);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetNetPermForUidsGrantMultiplePermissions() throws Exception {
        final int permission = PERMISSION_INTERNET | PERMISSION_UPDATE_DEVICE_STATS;
        mBpfNetMaps.setNetPermForUids(permission, TEST_UIDS);

        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];
        assertEquals(permission, mUidPermissionMap.getValue(new S32(uid0)).val);
        assertEquals(permission, mUidPermissionMap.getValue(new S32(uid1)).val);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetNetPermForUidsRevokeInternetPermission() throws Exception {
        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];
        mBpfNetMaps.setNetPermForUids(PERMISSION_INTERNET, TEST_UIDS);
        mBpfNetMaps.setNetPermForUids(PERMISSION_NONE, new int[]{uid0});

        assertEquals(PERMISSION_NONE, mUidPermissionMap.getValue(new S32(uid0)).val);
        assertNull(mUidPermissionMap.getValue(new S32(uid1)));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetNetPermForUidsRevokeUpdateDeviceStatsPermission() throws Exception {
        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];
        mBpfNetMaps.setNetPermForUids(PERMISSION_UPDATE_DEVICE_STATS, TEST_UIDS);
        mBpfNetMaps.setNetPermForUids(PERMISSION_NONE, new int[]{uid0});

        assertEquals(PERMISSION_NONE, mUidPermissionMap.getValue(new S32(uid0)).val);
        assertEquals(PERMISSION_UPDATE_DEVICE_STATS, mUidPermissionMap.getValue(new S32(uid1)).val);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetNetPermForUidsRevokeMultiplePermissions() throws Exception {
        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];
        final int permission = PERMISSION_INTERNET | PERMISSION_UPDATE_DEVICE_STATS;
        mBpfNetMaps.setNetPermForUids(permission, TEST_UIDS);
        mBpfNetMaps.setNetPermForUids(PERMISSION_NONE, new int[]{uid0});

        assertEquals(PERMISSION_NONE, mUidPermissionMap.getValue(new S32(uid0)).val);
        assertEquals(permission, mUidPermissionMap.getValue(new S32(uid1)).val);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetNetPermForUidsPermissionUninstalled() throws Exception {
        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];
        final int permission = PERMISSION_INTERNET | PERMISSION_UPDATE_DEVICE_STATS;
        mBpfNetMaps.setNetPermForUids(permission, TEST_UIDS);
        mBpfNetMaps.setNetPermForUids(PERMISSION_UNINSTALLED, new int[]{uid0});

        assertNull(mUidPermissionMap.getValue(new S32(uid0)));
        assertEquals(permission, mUidPermissionMap.getValue(new S32(uid1)).val);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetNetPermForUidsDuplicatedRequestSilentlyIgnored() throws Exception {
        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];
        final int permission = PERMISSION_INTERNET | PERMISSION_UPDATE_DEVICE_STATS;

        mBpfNetMaps.setNetPermForUids(permission, TEST_UIDS);
        assertEquals(permission, mUidPermissionMap.getValue(new S32(uid0)).val);
        assertEquals(permission, mUidPermissionMap.getValue(new S32(uid1)).val);

        mBpfNetMaps.setNetPermForUids(permission, TEST_UIDS);
        assertEquals(permission, mUidPermissionMap.getValue(new S32(uid0)).val);
        assertEquals(permission, mUidPermissionMap.getValue(new S32(uid1)).val);

        mBpfNetMaps.setNetPermForUids(PERMISSION_NONE, TEST_UIDS);
        assertEquals(PERMISSION_NONE, mUidPermissionMap.getValue(new S32(uid0)).val);
        assertEquals(PERMISSION_NONE, mUidPermissionMap.getValue(new S32(uid1)).val);

        mBpfNetMaps.setNetPermForUids(PERMISSION_NONE, TEST_UIDS);
        assertEquals(PERMISSION_NONE, mUidPermissionMap.getValue(new S32(uid0)).val);
        assertEquals(PERMISSION_NONE, mUidPermissionMap.getValue(new S32(uid1)).val);

        mBpfNetMaps.setNetPermForUids(PERMISSION_UNINSTALLED, TEST_UIDS);
        assertNull(mUidPermissionMap.getValue(new S32(uid0)));
        assertNull(mUidPermissionMap.getValue(new S32(uid1)));

        mBpfNetMaps.setNetPermForUids(PERMISSION_UNINSTALLED, TEST_UIDS);
        assertNull(mUidPermissionMap.getValue(new S32(uid0)));
        assertNull(mUidPermissionMap.getValue(new S32(uid1)));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSwapActiveStatsMap() throws Exception {
        mConfigurationMap.updateEntry(
                CURRENT_STATS_MAP_CONFIGURATION_KEY, new U32(STATS_SELECT_MAP_A));

        mBpfNetMaps.swapActiveStatsMap();
        assertEquals(STATS_SELECT_MAP_B,
                mConfigurationMap.getValue(CURRENT_STATS_MAP_CONFIGURATION_KEY).val);

        mBpfNetMaps.swapActiveStatsMap();
        assertEquals(STATS_SELECT_MAP_A,
                mConfigurationMap.getValue(CURRENT_STATS_MAP_CONFIGURATION_KEY).val);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSwapActiveStatsMapSynchronizeKernelRCUFail() throws Exception {
        doReturn(EPERM).when(mDeps).synchronizeKernelRCU();
        mConfigurationMap.updateEntry(
                CURRENT_STATS_MAP_CONFIGURATION_KEY, new U32(STATS_SELECT_MAP_A));

        assertThrows(ServiceSpecificException.class, () -> mBpfNetMaps.swapActiveStatsMap());
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testPullBpfMapInfo() throws Exception {
        // mCookieTagMap has 1 entry
        mCookieTagMap.updateEntry(new CookieTagMapKey(0), new CookieTagMapValue(0, 0));

        // mUidOwnerMap has 2 entries
        mUidOwnerMap.updateEntry(new S32(0), new UidOwnerValue(0, 0));
        mUidOwnerMap.updateEntry(new S32(1), new UidOwnerValue(0, 0));

        // mUidPermissionMap has 3 entries
        mUidPermissionMap.updateEntry(new S32(0), new U8((short) 0));
        mUidPermissionMap.updateEntry(new S32(1), new U8((short) 0));
        mUidPermissionMap.updateEntry(new S32(2), new U8((short) 0));

        final int ret = mBpfNetMaps.pullBpfMapInfoAtom(NETWORK_BPF_MAP_INFO, new ArrayList<>());
        assertEquals(StatsManager.PULL_SUCCESS, ret);
        verify(mDeps).buildStatsEvent(
                1 /* cookieTagMapSize */, 2 /* uidOwnerMapSize */, 3 /* uidPermissionMapSize */);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testPullBpfMapInfoGetMapSizeFailure() throws Exception {
        doThrow(new ErrnoException("", EINVAL)).when(mCookieTagMap).forEach(any());
        final int ret = mBpfNetMaps.pullBpfMapInfoAtom(NETWORK_BPF_MAP_INFO, new ArrayList<>());
        assertEquals(StatsManager.PULL_SKIP, ret);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testPullBpfMapInfoUnexpectedAtomTag() {
        final int ret = mBpfNetMaps.pullBpfMapInfoAtom(-1 /* atomTag */, new ArrayList<>());
        assertEquals(StatsManager.PULL_SKIP, ret);
    }

    private void assertDumpContains(final String dump, final String message) {
        assertTrue(String.format("dump(%s) does not contain '%s'", dump, message),
                dump.contains(message));
    }

    private String getDump() throws Exception {
        final StringWriter sw = new StringWriter();
        mBpfNetMaps.dump(new IndentingPrintWriter(sw), new FileDescriptor(), true /* verbose */);
        return sw.toString();
    }

    private void doTestDumpUidPermissionMap(final int permission, final String permissionString)
            throws Exception {
        mUidPermissionMap.updateEntry(new S32(TEST_UID), new U8((short) permission));
        assertDumpContains(getDump(), TEST_UID + " " + permissionString);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testDumpUidPermissionMap() throws Exception {
        doTestDumpUidPermissionMap(PERMISSION_NONE, "PERMISSION_NONE");
        doTestDumpUidPermissionMap(PERMISSION_INTERNET | PERMISSION_UPDATE_DEVICE_STATS,
                "PERMISSION_INTERNET PERMISSION_UPDATE_DEVICE_STATS");
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testDumpUidPermissionMapInvalidPermission() throws Exception {
        doTestDumpUidPermissionMap(PERMISSION_UNINSTALLED, "PERMISSION_UNINSTALLED error!");
        doTestDumpUidPermissionMap(PERMISSION_INTERNET | 1 << 6,
                "PERMISSION_INTERNET PERMISSION_UNKNOWN(64)");
    }

    void doTestDumpUidOwnerMap(final int iif, final long match, final String matchString)
            throws Exception {
        mUidOwnerMap.updateEntry(new S32(TEST_UID), new UidOwnerValue(iif, match));
        assertDumpContains(getDump(), TEST_UID + " " + matchString);
    }

    void doTestDumpUidOwnerMap(final long match, final String matchString) throws Exception {
        doTestDumpUidOwnerMap(0 /* iif */, match, matchString);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testDumpUidOwnerMap() throws Exception {
        doTestDumpUidOwnerMap(HAPPY_BOX_MATCH, "HAPPY_BOX_MATCH");
        doTestDumpUidOwnerMap(PENALTY_BOX_MATCH, "PENALTY_BOX_MATCH");
        doTestDumpUidOwnerMap(DOZABLE_MATCH, "DOZABLE_MATCH");
        doTestDumpUidOwnerMap(STANDBY_MATCH, "STANDBY_MATCH");
        doTestDumpUidOwnerMap(POWERSAVE_MATCH, "POWERSAVE_MATCH");
        doTestDumpUidOwnerMap(RESTRICTED_MATCH, "RESTRICTED_MATCH");
        doTestDumpUidOwnerMap(LOW_POWER_STANDBY_MATCH, "LOW_POWER_STANDBY_MATCH");
        doTestDumpUidOwnerMap(LOCKDOWN_VPN_MATCH, "LOCKDOWN_VPN_MATCH");
        doTestDumpUidOwnerMap(OEM_DENY_1_MATCH, "OEM_DENY_1_MATCH");
        doTestDumpUidOwnerMap(OEM_DENY_2_MATCH, "OEM_DENY_2_MATCH");
        doTestDumpUidOwnerMap(OEM_DENY_3_MATCH, "OEM_DENY_3_MATCH");

        doTestDumpUidOwnerMap(HAPPY_BOX_MATCH | POWERSAVE_MATCH,
                "HAPPY_BOX_MATCH POWERSAVE_MATCH");
        doTestDumpUidOwnerMap(DOZABLE_MATCH | LOCKDOWN_VPN_MATCH | OEM_DENY_1_MATCH,
                "DOZABLE_MATCH LOCKDOWN_VPN_MATCH OEM_DENY_1_MATCH");
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testDumpUidOwnerMapWithIifMatch() throws Exception {
        doTestDumpUidOwnerMap(TEST_IF_INDEX, IIF_MATCH, "IIF_MATCH " + TEST_IF_INDEX);
        doTestDumpUidOwnerMap(TEST_IF_INDEX,
                IIF_MATCH | DOZABLE_MATCH | LOCKDOWN_VPN_MATCH | OEM_DENY_1_MATCH,
                "DOZABLE_MATCH IIF_MATCH LOCKDOWN_VPN_MATCH OEM_DENY_1_MATCH " + TEST_IF_INDEX);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testDumpUidOwnerMapWithInvalidMatch() throws Exception {
        final long invalid_match = 1L << 31;
        doTestDumpUidOwnerMap(invalid_match, "UNKNOWN_MATCH(" + invalid_match + ")");
        doTestDumpUidOwnerMap(DOZABLE_MATCH | invalid_match,
                "DOZABLE_MATCH UNKNOWN_MATCH(" + invalid_match + ")");
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testDumpCurrentStatsMapConfig() throws Exception {
        mConfigurationMap.updateEntry(
                CURRENT_STATS_MAP_CONFIGURATION_KEY, new U32(STATS_SELECT_MAP_A));
        assertDumpContains(getDump(), "current statsMap configuration: 0 SELECT_MAP_A");

        mConfigurationMap.updateEntry(
                CURRENT_STATS_MAP_CONFIGURATION_KEY, new U32(STATS_SELECT_MAP_B));
        assertDumpContains(getDump(), "current statsMap configuration: 1 SELECT_MAP_B");
    }

    private void doTestDumpOwnerMatchConfig(final long match, final String matchString)
            throws Exception {
        mConfigurationMap.updateEntry(UID_RULES_CONFIGURATION_KEY, new U32(match));
        assertDumpContains(getDump(),
                "current ownerMatch configuration: " + match + " " + matchString);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testDumpUidOwnerMapConfig() throws Exception {
        doTestDumpOwnerMatchConfig(HAPPY_BOX_MATCH, "HAPPY_BOX_MATCH");
        doTestDumpOwnerMatchConfig(PENALTY_BOX_MATCH, "PENALTY_BOX_MATCH");
        doTestDumpOwnerMatchConfig(DOZABLE_MATCH, "DOZABLE_MATCH");
        doTestDumpOwnerMatchConfig(STANDBY_MATCH, "STANDBY_MATCH");
        doTestDumpOwnerMatchConfig(POWERSAVE_MATCH, "POWERSAVE_MATCH");
        doTestDumpOwnerMatchConfig(RESTRICTED_MATCH, "RESTRICTED_MATCH");
        doTestDumpOwnerMatchConfig(LOW_POWER_STANDBY_MATCH, "LOW_POWER_STANDBY_MATCH");
        doTestDumpOwnerMatchConfig(IIF_MATCH, "IIF_MATCH");
        doTestDumpOwnerMatchConfig(LOCKDOWN_VPN_MATCH, "LOCKDOWN_VPN_MATCH");
        doTestDumpOwnerMatchConfig(OEM_DENY_1_MATCH, "OEM_DENY_1_MATCH");
        doTestDumpOwnerMatchConfig(OEM_DENY_2_MATCH, "OEM_DENY_2_MATCH");
        doTestDumpOwnerMatchConfig(OEM_DENY_3_MATCH, "OEM_DENY_3_MATCH");

        doTestDumpOwnerMatchConfig(HAPPY_BOX_MATCH | POWERSAVE_MATCH,
                "HAPPY_BOX_MATCH POWERSAVE_MATCH");
        doTestDumpOwnerMatchConfig(DOZABLE_MATCH | LOCKDOWN_VPN_MATCH | OEM_DENY_1_MATCH,
                "DOZABLE_MATCH LOCKDOWN_VPN_MATCH OEM_DENY_1_MATCH");
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testDumpUidOwnerMapConfigWithInvalidMatch() throws Exception {
        final long invalid_match = 1L << 31;
        doTestDumpOwnerMatchConfig(invalid_match, "UNKNOWN_MATCH(" + invalid_match + ")");
        doTestDumpOwnerMatchConfig(DOZABLE_MATCH | invalid_match,
                "DOZABLE_MATCH UNKNOWN_MATCH(" + invalid_match + ")");
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testDumpCookieTagMap() throws Exception {
        mCookieTagMap.updateEntry(new CookieTagMapKey(123), new CookieTagMapValue(456, 0x789));
        assertDumpContains(getDump(), "cookie=123 tag=0x789 uid=456");
    }

    private void doTestDumpDataSaverConfig(final short value, final boolean expected)
            throws Exception {
        mDataSaverEnabledMap.updateEntry(DATA_SAVER_ENABLED_KEY, new U8(value));
        assertDumpContains(getDump(),
                "sDataSaverEnabledMap: " + expected);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testDumpDataSaverConfig() throws Exception {
        doTestDumpDataSaverConfig(DATA_SAVER_DISABLED, false);
        doTestDumpDataSaverConfig(DATA_SAVER_ENABLED, true);
        doTestDumpDataSaverConfig((short) 2, true);
    }

    @Test
    public void testGetUids() throws ErrnoException {
        final int uid0 = TEST_UIDS[0];
        final int uid1 = TEST_UIDS[1];
        final long match0 = DOZABLE_MATCH | POWERSAVE_MATCH;
        final long match1 = DOZABLE_MATCH | STANDBY_MATCH;
        mUidOwnerMap.updateEntry(new S32(uid0), new UidOwnerValue(NULL_IIF, match0));
        mUidOwnerMap.updateEntry(new S32(uid1), new UidOwnerValue(NULL_IIF, match1));

        assertEquals(new ArraySet<>(List.of(uid0, uid1)),
                mBpfNetMaps.getUidsWithAllowRuleOnAllowListChain(FIREWALL_CHAIN_DOZABLE));
        assertEquals(new ArraySet<>(List.of(uid0)),
                mBpfNetMaps.getUidsWithAllowRuleOnAllowListChain(FIREWALL_CHAIN_POWERSAVE));

        assertEquals(new ArraySet<>(List.of(uid1)),
                mBpfNetMaps.getUidsWithDenyRuleOnDenyListChain(FIREWALL_CHAIN_STANDBY));
        assertEquals(new ArraySet<>(),
                mBpfNetMaps.getUidsWithDenyRuleOnDenyListChain(FIREWALL_CHAIN_OEM_DENY_1));
    }

    @Test
    public void testGetUidsIllegalArgument() {
        final Class<IllegalArgumentException> expected = IllegalArgumentException.class;
        assertThrows(expected,
                () -> mBpfNetMaps.getUidsWithDenyRuleOnDenyListChain(FIREWALL_CHAIN_DOZABLE));
        assertThrows(expected,
                () -> mBpfNetMaps.getUidsWithAllowRuleOnAllowListChain(FIREWALL_CHAIN_OEM_DENY_1));
    }

    @Test
    @IgnoreAfter(Build.VERSION_CODES.S_V2)
    public void testSetDataSaverEnabledBeforeT() {
        for (boolean enable : new boolean[]{true, false}) {
            assertThrows(UnsupportedOperationException.class,
                    () -> mBpfNetMaps.setDataSaverEnabled(enable));
        }
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetDataSaverEnabled() throws Exception {
        for (boolean enable : new boolean[]{true, false}) {
            mBpfNetMaps.setDataSaverEnabled(enable);
            assertEquals(enable ? DATA_SAVER_ENABLED : DATA_SAVER_DISABLED,
                    mDataSaverEnabledMap.getValue(DATA_SAVER_ENABLED_KEY).val);
        }
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetIngressDiscardRule_V4address() throws Exception {
        mBpfNetMaps.setIngressDiscardRule(TEST_V4_ADDRESS, TEST_IF_NAME);
        final IngressDiscardValue val = mIngressDiscardMap.getValue(new IngressDiscardKey(
                TEST_V4_ADDRESS));
        assertEquals(TEST_IF_INDEX, val.iif1);
        assertEquals(TEST_IF_INDEX, val.iif2);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testSetIngressDiscardRule_V6address() throws Exception {
        mBpfNetMaps.setIngressDiscardRule(TEST_V6_ADDRESS, TEST_IF_NAME);
        final IngressDiscardValue val =
                mIngressDiscardMap.getValue(new IngressDiscardKey(TEST_V6_ADDRESS));
        assertEquals(TEST_IF_INDEX, val.iif1);
        assertEquals(TEST_IF_INDEX, val.iif2);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testRemoveIngressDiscardRule() throws Exception {
        mBpfNetMaps.setIngressDiscardRule(TEST_V4_ADDRESS, TEST_IF_NAME);
        mBpfNetMaps.setIngressDiscardRule(TEST_V6_ADDRESS, TEST_IF_NAME);
        final IngressDiscardKey v4Key = new IngressDiscardKey(TEST_V4_ADDRESS);
        final IngressDiscardKey v6Key = new IngressDiscardKey(TEST_V6_ADDRESS);
        assertTrue(mIngressDiscardMap.containsKey(v4Key));
        assertTrue(mIngressDiscardMap.containsKey(v6Key));

        mBpfNetMaps.removeIngressDiscardRule(TEST_V4_ADDRESS);
        assertFalse(mIngressDiscardMap.containsKey(v4Key));
        assertTrue(mIngressDiscardMap.containsKey(v6Key));

        mBpfNetMaps.removeIngressDiscardRule(TEST_V6_ADDRESS);
        assertFalse(mIngressDiscardMap.containsKey(v4Key));
        assertFalse(mIngressDiscardMap.containsKey(v6Key));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testDumpIngressDiscardRule() throws Exception {
        mBpfNetMaps.setIngressDiscardRule(TEST_V4_ADDRESS, TEST_IF_NAME);
        mBpfNetMaps.setIngressDiscardRule(TEST_V6_ADDRESS, TEST_IF_NAME);
        final String dump = getDump();
        assertDumpContains(dump, TEST_V4_ADDRESS.getHostAddress());
        assertDumpContains(dump, TEST_V6_ADDRESS.getHostAddress());
        assertDumpContains(dump, TEST_IF_INDEX + "(" + TEST_IF_NAME + ")");
    }
}
