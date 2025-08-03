/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.net.wifi.WifiConfiguration;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@SmallTest
public class NonCarrierMergedNetworksStatusTrackerTest extends WifiBaseTest {
    private static final long TEST_MIN_DISABLE_ALL_DURATION = 1000;
    private static final long TEST_MAX_DISABLE_ALL_DURATION = 100000;
    private static final long TEST_MIN_DURATION_NOT_SEEN_IN_SCANS = 3000;
    private static final int TEST_SUBSCRIPTION_ID = 1;
    private static final int INVALID_SUBSCRIPTION_ID = -1;
    private NonCarrierMergedNetworksStatusTracker mNonCarrierMergedNetworksStatusTracker;
    private WifiConfiguration mTestNonCarrierMergedNetwork;
    @Mock private Clock mClock;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mNonCarrierMergedNetworksStatusTracker = new NonCarrierMergedNetworksStatusTracker(mClock);
        mTestNonCarrierMergedNetwork = WifiConfigurationTestUtil.createOpenNetwork();
    }

    // Need this because MissingCounterTimerLockList uses getWallClockMillis()
    private void setClockTime(long millis) {
        when(mClock.getWallClockMillis()).thenReturn(millis);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(millis);
    }

    /**
     * Verify that in the default state, networks are not disabled.
     */
    @Test
    public void testNetworkIsEnabledByDefault() {
        assertFalse(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(
                mTestNonCarrierMergedNetwork));
    }

    /**
     * Verify that after disableAllNonCarrierMergedNetworks is called, a non-carrier-merged network
     * that's not explicitly disabled by "temporarilyDisableNetwork" gets re-enabled when the min
     * disable duration passes.
     */
    @Test
    public void testDisableAllNonCarrierMergedNetworks() {
        // start disabling non-carrier-merged networks.
        mNonCarrierMergedNetworksStatusTracker.disableAllNonCarrierMergedNetworks(
                TEST_SUBSCRIPTION_ID, TEST_MIN_DISABLE_ALL_DURATION, TEST_MAX_DISABLE_ALL_DURATION);

        // verify the non-carrier-merged network is disabled before the disable duration is over.
        setClockTime(TEST_MIN_DISABLE_ALL_DURATION - 1);
        assertTrue(mNonCarrierMergedNetworksStatusTracker
                .isNetworkDisabled(mTestNonCarrierMergedNetwork));

        // verify the non-carrier-merged network is no longer disabled after the disable duration.
        setClockTime(TEST_MIN_DISABLE_ALL_DURATION);
        assertFalse(mNonCarrierMergedNetworksStatusTracker
                .isNetworkDisabled(mTestNonCarrierMergedNetwork));
    }

    /**
     * Verify that after disableAllNonCarrierMergedNetworks is called, a carrier-merged network
     * with matching subscription ID is still enabled.
     */
    @Test
    public void testCarrierMergedNetworkWithMatchingSubscriptionIdIsEnabled() {
        mNonCarrierMergedNetworksStatusTracker.disableAllNonCarrierMergedNetworks(
                TEST_SUBSCRIPTION_ID, TEST_MIN_DISABLE_ALL_DURATION, TEST_MAX_DISABLE_ALL_DURATION);
        assertTrue(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(
                mTestNonCarrierMergedNetwork));

        // verify a carrier-merged network with non-matching subscription ID is disabled.
        WifiConfiguration testConfig = WifiConfigurationTestUtil.createOpenNetwork();
        testConfig.carrierMerged = true;
        testConfig.subscriptionId = INVALID_SUBSCRIPTION_ID;
        assertTrue(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(testConfig));

        // verify a non-carrier-merged network with matching subscription ID is disabled.
        testConfig.carrierMerged = false;
        testConfig.subscriptionId = TEST_SUBSCRIPTION_ID;
        assertTrue(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(testConfig));

        // verify a carrier-merged network with matching subscription ID is not disabled.
        testConfig.carrierMerged = true;
        testConfig.subscriptionId = TEST_SUBSCRIPTION_ID;
        assertFalse(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(testConfig));
    }

    /**
     * Verify that after disableAllNonCarrierMergedNetworks is called, a non-carrier-merged network
     * is disabled until clear() is called.
     */
    @Test
    public void testClearWillUndoDisableAllNonCarrierMergedNetworks() {
        // first verify that without doing anything, non-carrier-merged networks are enabled.
        WifiConfiguration testConfig = WifiConfigurationTestUtil.createOpenNetwork();
        // start disabling non-carrier-merged networks.
        mNonCarrierMergedNetworksStatusTracker.disableAllNonCarrierMergedNetworks(
                TEST_SUBSCRIPTION_ID, TEST_MIN_DISABLE_ALL_DURATION, TEST_MAX_DISABLE_ALL_DURATION);

        // verify the non-carrier-merged network is disabled.
        assertTrue(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(testConfig));

        // verify the non-carrier-merged network is no longer disabled after "clear" is called.
        mNonCarrierMergedNetworksStatusTracker.clear();
        assertFalse(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(testConfig));
    }

    /**
     * Verify that when a specific network is disabled through temporarilyDisableNetwork. It is
     * re-enabled after it's not seen in scan results for the specified duration.
     */
    @Test
    public void testTemporarilyDisableNetwork() {
        mNonCarrierMergedNetworksStatusTracker.disableAllNonCarrierMergedNetworks(
                TEST_SUBSCRIPTION_ID, TEST_MIN_DISABLE_ALL_DURATION, TEST_MAX_DISABLE_ALL_DURATION);
        mNonCarrierMergedNetworksStatusTracker.temporarilyDisableNetwork(
                mTestNonCarrierMergedNetwork, TEST_MIN_DURATION_NOT_SEEN_IN_SCANS,
                TEST_MAX_DISABLE_ALL_DURATION);
        assertTrue(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(
                mTestNonCarrierMergedNetwork));

        mNonCarrierMergedNetworksStatusTracker.update(Collections.EMPTY_SET);
        assertTrue(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(
                mTestNonCarrierMergedNetwork));

        // verify that after the network is gone from scan results for long enough, the
        // network is no longer disabled.
        setClockTime(TEST_MIN_DURATION_NOT_SEEN_IN_SCANS + 1);
        assertFalse(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(
                mTestNonCarrierMergedNetwork));
    }

    /**
     * Verify that a non-carrier merged network disabled with temporarilyDisableNetwork gets
     * re-enabled after the max disable duration passes even though the network is still seen in
     * scan results.
     */
    @Test
    public void testTemporarilyDisableNetworkWithMaxDisableDuration() {
        mNonCarrierMergedNetworksStatusTracker.disableAllNonCarrierMergedNetworks(
                TEST_SUBSCRIPTION_ID, TEST_MIN_DISABLE_ALL_DURATION, TEST_MAX_DISABLE_ALL_DURATION);
        mNonCarrierMergedNetworksStatusTracker.temporarilyDisableNetwork(
                mTestNonCarrierMergedNetwork, TEST_MIN_DURATION_NOT_SEEN_IN_SCANS,
                TEST_MAX_DISABLE_ALL_DURATION);
        assertTrue(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(
                mTestNonCarrierMergedNetwork));

        // verify the network is still disabled after the network is seen in scan results 1ms
        // before the max disable duration timeout.
        setClockTime(TEST_MAX_DISABLE_ALL_DURATION - 1);
        Set<String> networks = new HashSet<>();
        networks.add(mTestNonCarrierMergedNetwork.SSID);
        mNonCarrierMergedNetworksStatusTracker.update(networks);
        assertTrue(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(
                mTestNonCarrierMergedNetwork));

        // verify the network is re-enabled after the max disable duration.
        setClockTime(TEST_MAX_DISABLE_ALL_DURATION);
        assertFalse(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(
                mTestNonCarrierMergedNetwork));
    }

    /**
     * Verify that a network disabled by temporarilyDisableNetwork is re-enabled when clear() is
     * called.
     */
    @Test
    public void testClearResetsTemporarilyDisableNetwork() {
        mNonCarrierMergedNetworksStatusTracker.disableAllNonCarrierMergedNetworks(
                TEST_SUBSCRIPTION_ID, TEST_MIN_DISABLE_ALL_DURATION, TEST_MAX_DISABLE_ALL_DURATION);
        mNonCarrierMergedNetworksStatusTracker.temporarilyDisableNetwork(
                mTestNonCarrierMergedNetwork, TEST_MIN_DURATION_NOT_SEEN_IN_SCANS,
                TEST_MAX_DISABLE_ALL_DURATION);
        assertTrue(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(
                mTestNonCarrierMergedNetwork));

        mNonCarrierMergedNetworksStatusTracker.clear();
        assertFalse(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(
                mTestNonCarrierMergedNetwork));
    }

    /**
     * Verify that when a temporarily disabled network shows up in scan results, we reset the
     * counter needed to re-enable it.
     */
    @Test
    public void testNetworkAppearingWillResetCounter() {
        mNonCarrierMergedNetworksStatusTracker.disableAllNonCarrierMergedNetworks(
                TEST_SUBSCRIPTION_ID, TEST_MIN_DISABLE_ALL_DURATION, TEST_MAX_DISABLE_ALL_DURATION);
        mNonCarrierMergedNetworksStatusTracker.temporarilyDisableNetwork(
                mTestNonCarrierMergedNetwork, TEST_MIN_DURATION_NOT_SEEN_IN_SCANS,
                TEST_MAX_DISABLE_ALL_DURATION);
        assertTrue(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(
                mTestNonCarrierMergedNetwork));
        mNonCarrierMergedNetworksStatusTracker.update(Collections.EMPTY_SET);

        // simulate the network appearing from scan results after some time.
        long networkAppearTime = 1200;
        setClockTime(networkAppearTime);
        Set<String> networks = new HashSet<>();
        networks.add(mTestNonCarrierMergedNetwork.SSID);
        mNonCarrierMergedNetworksStatusTracker.update(networks);

        // simulate the network dissapearing from scan results again.
        mNonCarrierMergedNetworksStatusTracker.update(Collections.EMPTY_SET);

        // verify that the timer was reset properly
        setClockTime(networkAppearTime + TEST_MIN_DURATION_NOT_SEEN_IN_SCANS);
        assertTrue(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(
                mTestNonCarrierMergedNetwork));
        setClockTime(networkAppearTime + TEST_MIN_DURATION_NOT_SEEN_IN_SCANS + 1);
        assertFalse(mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(
                mTestNonCarrierMergedNetwork));
    }
}
