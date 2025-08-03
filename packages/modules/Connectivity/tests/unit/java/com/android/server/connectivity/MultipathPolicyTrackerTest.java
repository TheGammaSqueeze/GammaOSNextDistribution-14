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

package com.android.server.connectivity;

import static android.content.Intent.ACTION_CONFIGURATION_CHANGED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkPolicy.LIMIT_DISABLED;
import static android.net.NetworkPolicy.SNOOZE_NEVER;
import static android.net.NetworkPolicy.WARNING_DISABLED;
import static android.provider.Settings.Global.NETWORK_DEFAULT_DAILY_MULTIPATH_QUOTA_BYTES;

import static com.android.server.net.NetworkPolicyManagerInternal.QUOTA_TYPE_MULTIPATH;
import static com.android.server.net.NetworkPolicyManagerService.OPPORTUNISTIC_QUOTA_UNKNOWN;
import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.EthernetNetworkSpecifier;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.net.NetworkTemplate;
import android.net.TelephonyNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.test.mock.MockContentResolver;
import android.util.DataUnit;
import android.util.Range;
import android.util.RecurrenceRule;

import androidx.test.filters.SmallTest;

import com.android.internal.R;
import com.android.internal.util.test.FakeSettingsProvider;
import com.android.server.LocalServices;
import com.android.server.net.NetworkPolicyManagerInternal;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
public class MultipathPolicyTrackerTest {
    private static final Network TEST_NETWORK = new Network(123);
    private static final int POLICY_SNOOZED = -100;
    private static final String TEST_IMSI1 = "TEST_IMSI1";

    @Mock private Context mContext;
    @Mock private Context mUserAllContext;
    @Mock private Resources mResources;
    @Mock private Handler mHandler;
    @Mock private MultipathPolicyTracker.Dependencies mDeps;
    @Mock private Clock mClock;
    @Mock private ConnectivityManager mCM;
    @Mock private NetworkPolicyManager mNPM;
    @Mock private NetworkStatsManager mStatsManager;
    @Mock private NetworkPolicyManagerInternal mNPMI;
    @Mock private TelephonyManager mTelephonyManager;
    private MockContentResolver mContentResolver;

    private ArgumentCaptor<BroadcastReceiver> mConfigChangeReceiverCaptor;

    private MultipathPolicyTracker mTracker;

    private Clock mPreviousRecurrenceRuleClock;
    private boolean mRecurrenceRuleClockMocked;

    private <T> void mockService(String serviceName, Class<T> serviceClass, T service) {
        doReturn(serviceName).when(mContext).getSystemServiceName(serviceClass);
        doReturn(service).when(mContext).getSystemService(serviceName);
        if (mContext.getSystemService(serviceClass) == null) {
            // Test is using mockito-extended
            doCallRealMethod().when(mContext).getSystemService(serviceClass);
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mPreviousRecurrenceRuleClock = RecurrenceRule.sClock;
        RecurrenceRule.sClock = mClock;
        mRecurrenceRuleClockMocked = true;

        mConfigChangeReceiverCaptor = ArgumentCaptor.forClass(BroadcastReceiver.class);

        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getApplicationInfo()).thenReturn(new ApplicationInfo());
        // Mock user id to all users that Context#registerReceiver will register with all users too.
        doReturn(UserHandle.ALL.getIdentifier()).when(mUserAllContext).getUserId();
        when(mContext.createContextAsUser(eq(UserHandle.ALL), anyInt()))
                .thenReturn(mUserAllContext);
        when(mUserAllContext.registerReceiver(mConfigChangeReceiverCaptor.capture(),
                argThat(f -> f.hasAction(ACTION_CONFIGURATION_CHANGED)), any(), any()))
                .thenReturn(null);

        when(mDeps.getClock()).thenReturn(mClock);

        when(mTelephonyManager.createForSubscriptionId(anyInt())).thenReturn(mTelephonyManager);
        when(mTelephonyManager.getSubscriberId()).thenReturn(TEST_IMSI1);

        mContentResolver = Mockito.spy(new MockContentResolver(mContext));
        mContentResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        Settings.Global.clearProviderForTest();
        when(mContext.getContentResolver()).thenReturn(mContentResolver);

        mockService(Context.CONNECTIVITY_SERVICE, ConnectivityManager.class, mCM);
        mockService(Context.NETWORK_POLICY_SERVICE, NetworkPolicyManager.class, mNPM);
        mockService(Context.NETWORK_STATS_SERVICE, NetworkStatsManager.class, mStatsManager);
        mockService(Context.TELEPHONY_SERVICE, TelephonyManager.class, mTelephonyManager);

        LocalServices.removeServiceForTest(NetworkPolicyManagerInternal.class);
        LocalServices.addService(NetworkPolicyManagerInternal.class, mNPMI);

        mTracker = new MultipathPolicyTracker(mContext, mHandler, mDeps);
    }

    @After
    public void tearDown() {
        // Avoid setting static clock to null (which should normally not be the case)
        // if MockitoAnnotations.initMocks threw an exception
        if (mRecurrenceRuleClockMocked) {
            RecurrenceRule.sClock = mPreviousRecurrenceRuleClock;
        }
        mRecurrenceRuleClockMocked = false;
    }

    private void setDefaultQuotaGlobalSetting(long setting) {
        Settings.Global.putInt(mContentResolver, NETWORK_DEFAULT_DAILY_MULTIPATH_QUOTA_BYTES,
                (int) setting);
    }

    private void prepareGetMultipathPreferenceTest(
            long usedBytesToday, long subscriptionQuota, long policyWarning, long policyLimit,
            long defaultGlobalSetting, long defaultResSetting, boolean roaming) {

        // TODO: tests should not use ZoneId.systemDefault() once code handles TZ correctly.
        final ZonedDateTime now = ZonedDateTime.ofInstant(
                Instant.parse("2017-04-02T10:11:12Z"), ZoneId.systemDefault());
        final ZonedDateTime startOfDay = now.truncatedTo(ChronoUnit.DAYS);
        when(mClock.millis()).thenReturn(now.toInstant().toEpochMilli());
        when(mClock.instant()).thenReturn(now.toInstant());
        when(mClock.getZone()).thenReturn(ZoneId.systemDefault());

        // Setup plan quota
        when(mNPMI.getSubscriptionOpportunisticQuota(TEST_NETWORK, QUOTA_TYPE_MULTIPATH))
                .thenReturn(subscriptionQuota);

        // Prepare stats to be mocked.
        final NetworkStats.Bucket mockedStatsBucket = mock(NetworkStats.Bucket.class);
        when(mockedStatsBucket.getTxBytes()).thenReturn(usedBytesToday / 3);
        when(mockedStatsBucket.getRxBytes()).thenReturn(usedBytesToday - usedBytesToday / 3);

        // Setup user policy warning / limit
        if (policyWarning != WARNING_DISABLED || policyLimit != LIMIT_DISABLED) {
            final Instant recurrenceStart = Instant.parse("2017-04-01T00:00:00Z");
            final RecurrenceRule recurrenceRule = new RecurrenceRule(
                    ZonedDateTime.ofInstant(
                            recurrenceStart,
                            ZoneId.systemDefault()),
                    null /* end */,
                    Period.ofMonths(1));
            final boolean snoozeWarning = policyWarning == POLICY_SNOOZED;
            final boolean snoozeLimit = policyLimit == POLICY_SNOOZED;
            when(mNPM.getNetworkPolicies()).thenReturn(new NetworkPolicy[] {
                    new NetworkPolicy(
                            new NetworkTemplate.Builder(NetworkTemplate.MATCH_MOBILE)
                                    .setSubscriberIds(Set.of(TEST_IMSI1))
                                    .setMeteredness(android.net.NetworkStats.METERED_YES).build(),
                            recurrenceRule,
                            snoozeWarning ? 0 : policyWarning,
                            snoozeLimit ? 0 : policyLimit,
                            snoozeWarning ? recurrenceStart.toEpochMilli() + 1 : SNOOZE_NEVER,
                            snoozeLimit ? recurrenceStart.toEpochMilli() + 1 : SNOOZE_NEVER,
                            SNOOZE_NEVER,
                            true /* metered */,
                            false /* inferred */)
            });

            // Mock stats for this month.
            final Range<ZonedDateTime> cycleOfTheMonth = recurrenceRule.cycleIterator().next();
            when(mStatsManager.querySummaryForDevice(any(),
                    eq(cycleOfTheMonth.getLower().toInstant().toEpochMilli()),
                    eq(cycleOfTheMonth.getUpper().toInstant().toEpochMilli())))
                    .thenReturn(mockedStatsBucket);
        } else {
            when(mNPM.getNetworkPolicies()).thenReturn(new NetworkPolicy[0]);
        }

        // Setup default quota in settings and resources
        if (defaultGlobalSetting > 0) {
            setDefaultQuotaGlobalSetting(defaultGlobalSetting);
        }
        when(mResources.getInteger(R.integer.config_networkDefaultDailyMultipathQuotaBytes))
                .thenReturn((int) defaultResSetting);

        // Mock stats for today.
        when(mStatsManager.querySummaryForDevice(any(),
                eq(startOfDay.toInstant().toEpochMilli()),
                eq(now.toInstant().toEpochMilli()))).thenReturn(mockedStatsBucket);

        ArgumentCaptor<ConnectivityManager.NetworkCallback> networkCallback =
                ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback.class);
        mTracker.start();
        verify(mCM).registerNetworkCallback(any(), networkCallback.capture(), any());

        // Simulate callback after capability changes
        NetworkCapabilities capabilities = new NetworkCapabilities()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addTransportType(TRANSPORT_CELLULAR)
                .setNetworkSpecifier(new EthernetNetworkSpecifier("eth234"));
        if (!roaming) {
            capabilities.addCapability(NET_CAPABILITY_NOT_ROAMING);
        }
        networkCallback.getValue().onCapabilitiesChanged(
                TEST_NETWORK,
                capabilities);

        // make sure it also works with the new introduced  TelephonyNetworkSpecifier
        capabilities = new NetworkCapabilities()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addTransportType(TRANSPORT_CELLULAR)
                .setNetworkSpecifier(new TelephonyNetworkSpecifier.Builder()
                        .setSubscriptionId(234).build());
        if (!roaming) {
            capabilities.addCapability(NET_CAPABILITY_NOT_ROAMING);
        }
        networkCallback.getValue().onCapabilitiesChanged(
                TEST_NETWORK,
                capabilities);
    }

    @Test
    public void testGetMultipathPreference_SubscriptionQuota() {
        prepareGetMultipathPreferenceTest(
                DataUnit.MEGABYTES.toBytes(2) /* usedBytesToday */,
                DataUnit.MEGABYTES.toBytes(14) /* subscriptionQuota */,
                DataUnit.MEGABYTES.toBytes(100) /* policyWarning */,
                LIMIT_DISABLED,
                DataUnit.MEGABYTES.toBytes(12) /* defaultGlobalSetting */,
                2_500_000 /* defaultResSetting */,
                false /* roaming */);

        verify(mStatsManager, times(1)).registerUsageCallback(
                any(), eq(DataUnit.MEGABYTES.toBytes(12)), any(), any());
    }

    @Test
    public void testGetMultipathPreference_UserWarningQuota() {
        prepareGetMultipathPreferenceTest(
                DataUnit.MEGABYTES.toBytes(7) /* usedBytesToday */,
                OPPORTUNISTIC_QUOTA_UNKNOWN,
                // Remaining days are 29 days from Apr. 2nd to May 1st.
                // Set limit so that 15MB * remaining days will be 5% of the remaining limit,
                // so it will be 15 * 29 / 0.05 + used bytes.
                DataUnit.MEGABYTES.toBytes(15 * 29 * 20 + 7) /* policyWarning */,
                LIMIT_DISABLED,
                DataUnit.MEGABYTES.toBytes(12) /* defaultGlobalSetting */,
                2_500_000 /* defaultResSetting */,
                false /* roaming */);

        // Daily budget should be 15MB (5% of daily quota), 7MB used today: callback set for 8MB
        verify(mStatsManager, times(1)).registerUsageCallback(
                any(), eq(DataUnit.MEGABYTES.toBytes(8)), any(), any());
    }

    @Test
    public void testGetMultipathPreference_SnoozedWarningQuota() {
        prepareGetMultipathPreferenceTest(
                DataUnit.MEGABYTES.toBytes(7) /* usedBytesToday */,
                OPPORTUNISTIC_QUOTA_UNKNOWN,
                POLICY_SNOOZED /* policyWarning */,
                // Remaining days are 29 days from Apr. 2nd to May 1st.
                // Set limit so that 15MB * remaining days will be 5% of the remaining limit,
                // so it will be 15 * 29 / 0.05 + used bytes.
                DataUnit.MEGABYTES.toBytes(15 * 29 * 20 + 7) /* policyLimit */,
                DataUnit.MEGABYTES.toBytes(12) /* defaultGlobalSetting */,
                2_500_000 /* defaultResSetting */,
                false /* roaming */);

        // Daily budget should be 15MB (5% of daily quota), 7MB used today: callback set for 8MB
        verify(mStatsManager, times(1)).registerUsageCallback(
                any(), eq(DataUnit.MEGABYTES.toBytes(8)), any(), any());
    }

    @Test
    public void testGetMultipathPreference_SnoozedBothQuota() {
        prepareGetMultipathPreferenceTest(
                DataUnit.MEGABYTES.toBytes(7) /* usedBytesToday */,
                OPPORTUNISTIC_QUOTA_UNKNOWN,
                // 29 days from Apr. 2nd to May 1st
                POLICY_SNOOZED /* policyWarning */,
                POLICY_SNOOZED /* policyLimit */,
                DataUnit.MEGABYTES.toBytes(12) /* defaultGlobalSetting */,
                2_500_000 /* defaultResSetting */,
                false /* roaming */);

        // Default global setting should be used: 12 - 7 = 5
        verify(mStatsManager, times(1)).registerUsageCallback(
                any(), eq(DataUnit.MEGABYTES.toBytes(5)), any(), any());
    }

    @Test
    public void testGetMultipathPreference_SettingChanged() {
        prepareGetMultipathPreferenceTest(
                DataUnit.MEGABYTES.toBytes(2) /* usedBytesToday */,
                OPPORTUNISTIC_QUOTA_UNKNOWN,
                WARNING_DISABLED,
                LIMIT_DISABLED,
                -1 /* defaultGlobalSetting */,
                DataUnit.MEGABYTES.toBytes(10) /* defaultResSetting */,
                false /* roaming */);

        verify(mStatsManager, times(1)).registerUsageCallback(
                any(), eq(DataUnit.MEGABYTES.toBytes(8)), any(), any());

        // Update setting
        setDefaultQuotaGlobalSetting(DataUnit.MEGABYTES.toBytes(14));
        mTracker.mSettingsObserver.onChange(
                false, Settings.Global.getUriFor(NETWORK_DEFAULT_DAILY_MULTIPATH_QUOTA_BYTES));

        // Callback must have been re-registered with new setting
        verify(mStatsManager, times(1)).unregisterUsageCallback(any());
        verify(mStatsManager, times(1)).registerUsageCallback(
                any(), eq(DataUnit.MEGABYTES.toBytes(12)), any(), any());
    }

    @Test
    public void testGetMultipathPreference_ResourceChanged() {
        prepareGetMultipathPreferenceTest(
                DataUnit.MEGABYTES.toBytes(2) /* usedBytesToday */,
                OPPORTUNISTIC_QUOTA_UNKNOWN,
                WARNING_DISABLED,
                LIMIT_DISABLED,
                -1 /* defaultGlobalSetting */,
                DataUnit.MEGABYTES.toBytes(14) /* defaultResSetting */,
                false /* roaming */);

        verify(mStatsManager, times(1)).registerUsageCallback(
                any(), eq(DataUnit.MEGABYTES.toBytes(12)), any(), any());

        when(mResources.getInteger(R.integer.config_networkDefaultDailyMultipathQuotaBytes))
                .thenReturn((int) DataUnit.MEGABYTES.toBytes(16));

        final BroadcastReceiver configChangeReceiver = mConfigChangeReceiverCaptor.getValue();
        assertNotNull(configChangeReceiver);
        configChangeReceiver.onReceive(mContext, new Intent());

        // Uses the new setting (16 - 2 = 14MB)
        verify(mStatsManager, times(1)).registerUsageCallback(
                any(), eq(DataUnit.MEGABYTES.toBytes(14)), any(), any());
    }

    @DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
    @Test
    public void testOnThresholdReached() {
        prepareGetMultipathPreferenceTest(
                DataUnit.MEGABYTES.toBytes(2) /* usedBytesToday */,
                DataUnit.MEGABYTES.toBytes(14) /* subscriptionQuota */,
                DataUnit.MEGABYTES.toBytes(100) /* policyWarning */,
                LIMIT_DISABLED,
                DataUnit.MEGABYTES.toBytes(12) /* defaultGlobalSetting */,
                2_500_000 /* defaultResSetting */,
                false /* roaming */);

        final ArgumentCaptor<NetworkStatsManager.UsageCallback> usageCallbackCaptor =
                ArgumentCaptor.forClass(NetworkStatsManager.UsageCallback.class);
        final ArgumentCaptor<NetworkTemplate> networkTemplateCaptor =
                ArgumentCaptor.forClass(NetworkTemplate.class);
        // Verify the callback is registered with quota - used = 14 - 2 = 12MB.
        verify(mStatsManager, times(1)).registerUsageCallback(
                networkTemplateCaptor.capture(), eq(DataUnit.MEGABYTES.toBytes(12)), any(),
                usageCallbackCaptor.capture());

        // Capture arguments for later use.
        final NetworkStatsManager.UsageCallback usageCallback = usageCallbackCaptor.getValue();
        final NetworkTemplate template = networkTemplateCaptor.getValue();
        assertNotNull(usageCallback);
        assertNotNull(template);

        // Decrease quota from 14 to 11, and trigger the event.
        // TODO: Mock daily and monthly used bytes instead of changing subscription to simulate
        //  remaining quota changed.
        when(mNPMI.getSubscriptionOpportunisticQuota(TEST_NETWORK, QUOTA_TYPE_MULTIPATH))
                .thenReturn(DataUnit.MEGABYTES.toBytes(11));
        usageCallback.onThresholdReached(template);

        // Callback must have been re-registered with new remaining quota = 11 - 2 = 9MB.
        verify(mStatsManager, times(1))
                .unregisterUsageCallback(eq(usageCallback));
        verify(mStatsManager, times(1)).registerUsageCallback(
                eq(template), eq(DataUnit.MEGABYTES.toBytes(9)), any(), eq(usageCallback));
    }
}
