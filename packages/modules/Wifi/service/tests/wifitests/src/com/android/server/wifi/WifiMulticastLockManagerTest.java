/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.os.BatteryStatsManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.WorkSource;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.ActiveModeWarden.PrimaryClientModeManagerChangedCallback;
import com.android.server.wifi.WifiMulticastLockManager.FilterController;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

/**
 * Unit tests for {@link com.android.server.wifi.WifiMulticastLockManager}.
 */
@SmallTest
public class WifiMulticastLockManagerTest extends WifiBaseTest {
    private static final String WL_1_TAG = "Wakelock-1";
    private static final String WL_2_TAG = "Wakelock-2";

    private TestLooper mLooper;
    @Mock ConcreteClientModeManager mClientModeManager;
    @Mock ConcreteClientModeManager mClientModeManager2;
    @Spy FakeFilterController mFilterController = new FakeFilterController();
    @Spy FakeFilterController mFilterController2 = new FakeFilterController();
    @Mock BatteryStatsManager mBatteryStats;
    @Mock ActiveModeWarden mActiveModeWarden;
    @Captor ArgumentCaptor<PrimaryClientModeManagerChangedCallback> mPrimaryChangedCallbackCaptor;
    WifiMulticastLockManager mManager;

    /**
     * Initialize |WifiMulticastLockManager| instance before each test.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();

        when(mClientModeManager.getMcastLockManagerFilterController())
                .thenReturn(mFilterController);
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);

        when(mClientModeManager2.getMcastLockManagerFilterController())
                .thenReturn(mFilterController2);
        when(mClientModeManager2.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);

        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(mClientModeManager);
        mManager = new WifiMulticastLockManager(mActiveModeWarden, mBatteryStats,
                mLooper.getLooper());

        verify(mActiveModeWarden).registerPrimaryClientModeManagerChangedCallback(
                mPrimaryChangedCallbackCaptor.capture());
    }

    /**
     * Test behavior when no locks are held.
     */
    @Test
    public void noLocks() {
        assertFalse(mManager.isMulticastEnabled());
        mManager.initializeFiltering();
        verify(mFilterController, times(1)).startFilteringMulticastPackets();
    }

    /**
     * Test behavior when one lock is acquired then released.
     */
    @Test
    public void oneLock() throws RemoteException {
        IBinder binder = mock(IBinder.class);
        mManager.acquireLock(binder, WL_1_TAG);
        assertTrue(mManager.isMulticastEnabled());
        verify(mFilterController).stopFilteringMulticastPackets();
        mManager.initializeFiltering();
        verify(mFilterController, times(0)).startFilteringMulticastPackets();
        ArgumentCaptor<WorkSource> wsCaptor = ArgumentCaptor.forClass(WorkSource.class);
        verify(mBatteryStats).reportWifiMulticastEnabled(wsCaptor.capture());
        assertNotNull(wsCaptor.getValue());
        assertEquals(Binder.getCallingUid(), wsCaptor.getValue().getAttributionUid());
        verify(mBatteryStats, times(0)).reportWifiMulticastDisabled(any());

        mManager.releaseLock(WL_1_TAG);
        verify(mBatteryStats).reportWifiMulticastDisabled(wsCaptor.capture());
        assertNotNull(wsCaptor.getValue());
        assertEquals(Binder.getCallingUid(), wsCaptor.getValue().getAttributionUid());
        assertFalse(mManager.isMulticastEnabled());
    }

    private static class FakeFilterController implements FilterController {

        /** filters by default */
        private boolean mIsFilteringStarted = true;

        @Override
        public void startFilteringMulticastPackets() {
            mIsFilteringStarted = true;
        }

        @Override
        public void stopFilteringMulticastPackets() {
            mIsFilteringStarted = false;
        }

        public boolean isFilteringStarted() {
            return mIsFilteringStarted;
        }
    }

    /**
     * Test behavior when one lock is acquired, the primary ClientModeManager is changed, then
     * the lock is released.
     */
    @Test
    public void oneLock_changePrimaryClientModeManager() throws RemoteException {
        // CMM1 filter started by default
        assertTrue(mFilterController.isFilteringStarted());
        // CMM2 filter started by default
        assertTrue(mFilterController2.isFilteringStarted());

        IBinder binder = mock(IBinder.class);
        mManager.acquireLock(binder, WL_1_TAG);
        assertTrue(mManager.isMulticastEnabled());
        // CMM1 filtering stopped
        assertFalse(mFilterController.isFilteringStarted());
        // CMM2 still started
        assertTrue(mFilterController2.isFilteringStarted());

        // switch CMM1 to secondary
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        mPrimaryChangedCallbackCaptor.getValue().onChange(mClientModeManager, null);
        // switch CMM2 to primary
        when(mClientModeManager2.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(mClientModeManager2);
        mPrimaryChangedCallbackCaptor.getValue().onChange(null, mClientModeManager2);

        assertTrue(mManager.isMulticastEnabled());
        // CMM1 filter started
        assertTrue(mFilterController.isFilteringStarted());
        // CMM2 filter stopped
        assertFalse(mFilterController2.isFilteringStarted());

        mManager.releaseLock(WL_1_TAG);
        assertFalse(mManager.isMulticastEnabled());
        // CMM1 filter started
        assertTrue(mFilterController.isFilteringStarted());
        // CMM2 filter started
        assertTrue(mFilterController2.isFilteringStarted());
    }

    /**
     * Test behavior when one lock is acquired then released with the wrong tag.
     */
    @Test
    public void oneLock_wrongName() throws RemoteException {
        IBinder binder = mock(IBinder.class);
        mManager.acquireLock(binder, WL_1_TAG);
        assertTrue(mManager.isMulticastEnabled());
        verify(mFilterController).stopFilteringMulticastPackets();
        mManager.initializeFiltering();
        verify(mFilterController, never()).startFilteringMulticastPackets();
        verify(mBatteryStats).reportWifiMulticastEnabled(any());
        verify(mBatteryStats, never()).reportWifiMulticastDisabled(any());

        mManager.releaseLock(WL_2_TAG);
        verify(mBatteryStats, never()).reportWifiMulticastDisabled(any());
        assertTrue(mManager.isMulticastEnabled());
    }

    /**
     * Test behavior when multiple locks are acquired then released in nesting order.
     */
    @Test
    public void multipleLocksInOrder() throws RemoteException {
        IBinder binder = mock(IBinder.class);

        InOrder inOrderHandler = inOrder(mFilterController);
        InOrder inOrderBatteryStats = inOrder(mBatteryStats);

        mManager.acquireLock(binder, WL_1_TAG);
        inOrderHandler.verify(mFilterController).stopFilteringMulticastPackets();
        inOrderBatteryStats.verify(mBatteryStats).reportWifiMulticastEnabled(any());
        assertTrue(mManager.isMulticastEnabled());

        mManager.acquireLock(binder, WL_2_TAG);
        inOrderHandler.verify(mFilterController).stopFilteringMulticastPackets();
        inOrderBatteryStats.verify(mBatteryStats).reportWifiMulticastEnabled(any());
        assertTrue(mManager.isMulticastEnabled());

        mManager.initializeFiltering();
        inOrderHandler.verify(mFilterController, never()).startFilteringMulticastPackets();

        mManager.releaseLock(WL_2_TAG);
        inOrderHandler.verify(mFilterController, never()).startFilteringMulticastPackets();
        inOrderBatteryStats.verify(mBatteryStats).reportWifiMulticastDisabled(any());
        assertTrue(mManager.isMulticastEnabled());

        mManager.releaseLock(WL_1_TAG);
        inOrderHandler.verify(mFilterController).startFilteringMulticastPackets();
        inOrderBatteryStats.verify(mBatteryStats).reportWifiMulticastDisabled(any());
        assertFalse(mManager.isMulticastEnabled());
    }

    /**
     * Test behavior when multiple locks are aquired then released out of nesting order.
     */
    @Test
    public void multipleLocksOutOfOrder() throws RemoteException {
        IBinder binder = mock(IBinder.class);

        InOrder inOrderHandler = inOrder(mFilterController);
        InOrder inOrderBatteryStats = inOrder(mBatteryStats);

        mManager.acquireLock(binder, WL_1_TAG);
        inOrderHandler.verify(mFilterController).stopFilteringMulticastPackets();
        inOrderBatteryStats.verify(mBatteryStats).reportWifiMulticastEnabled(any());
        assertTrue(mManager.isMulticastEnabled());

        mManager.acquireLock(binder, WL_2_TAG);
        inOrderHandler.verify(mFilterController).stopFilteringMulticastPackets();
        inOrderBatteryStats.verify(mBatteryStats).reportWifiMulticastEnabled(any());
        assertTrue(mManager.isMulticastEnabled());

        mManager.initializeFiltering();
        inOrderHandler.verify(mFilterController, never()).startFilteringMulticastPackets();

        mManager.releaseLock(WL_1_TAG);
        inOrderHandler.verify(mFilterController, never()).startFilteringMulticastPackets();
        inOrderBatteryStats.verify(mBatteryStats).reportWifiMulticastDisabled(any());
        assertTrue(mManager.isMulticastEnabled());

        mManager.releaseLock(WL_2_TAG);
        inOrderHandler.verify(mFilterController).startFilteringMulticastPackets();
        inOrderBatteryStats.verify(mBatteryStats).reportWifiMulticastDisabled(any());
        assertFalse(mManager.isMulticastEnabled());
    }
}
