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

package com.android.server.remoteauth.connectivity;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.companion.AssociationInfo;
import android.companion.AssociationRequest;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link CdmConnectivityManager}. */
@RunWith(AndroidJUnit4.class)
public class CdmConnectivityManagerTest {
    @Rule public final MockitoRule mocks = MockitoJUnit.rule();

    @Mock CompanionDeviceManagerWrapper mCompanionDeviceManagerWrapper;

    private CdmConnectivityManager mCdmConnectivityManager;
    private ExecutorService mTestExecutor = Executors.newSingleThreadExecutor();

    @Before
    public void setUp() {
        mCdmConnectivityManager =
                new CdmConnectivityManager(mTestExecutor, mCompanionDeviceManagerWrapper);
    }

    @After
    public void tearDown() {
        mTestExecutor.shutdown();
    }

    @Test
    public void testStartDiscovery_callsGetAllAssociationsOnce() throws InterruptedException {
        mCdmConnectivityManager.startDiscovery(
                Utils.getFakeDiscoveryFilter(), Utils.getFakeDiscoveredDeviceReceiver());

        mTestExecutor.awaitTermination(1, TimeUnit.SECONDS);

        verify(mCompanionDeviceManagerWrapper, times(1)).getAllAssociations();
    }

    @Test
    public void testStartDiscovery_fetchesNoAssociations() {
        SettableFuture<Boolean> future = SettableFuture.create();

        when(mCompanionDeviceManagerWrapper.getAllAssociations())
                .thenReturn(Utils.getFakeAssociationInfoList(0));

        DiscoveredDeviceReceiver discoveredDeviceReceiver =
                new DiscoveredDeviceReceiver() {
                    @Override
                    public void onDiscovered(DiscoveredDevice unused) {
                        future.set(true);
                    }

                    @Override
                    public void onLost(DiscoveredDevice unused) {
                        future.set(true);
                    }
                };

        mCdmConnectivityManager.startDiscovery(
                Utils.getFakeDiscoveryFilter(), discoveredDeviceReceiver);

        assertThat(future.isDone()).isFalse();
    }

    @Test
    public void testStartDiscovery_DoesNotReturnNonWatchAssociations() throws InterruptedException {
        SettableFuture<Boolean> future = SettableFuture.create();
        DiscoveredDeviceReceiver discoveredDeviceReceiver =
                new DiscoveredDeviceReceiver() {
                    @Override
                    public void onDiscovered(DiscoveredDevice unused) {
                        future.set(true);
                    }

                    @Override
                    public void onLost(DiscoveredDevice unused) {
                        future.set(true);
                    }
                };

        when(mCompanionDeviceManagerWrapper.getDeviceProfile(any(AssociationInfo.class)))
                .thenReturn(Utils.FAKE_DEVICE_PROFILE);

        mCdmConnectivityManager.startDiscovery(
                Utils.getFakeDiscoveryFilter(), discoveredDeviceReceiver);

        mTestExecutor.awaitTermination(1, TimeUnit.SECONDS);

        verify(mCompanionDeviceManagerWrapper, times(1)).getAllAssociations();
        verify(mCompanionDeviceManagerWrapper, times(0))
                .getDeviceProfile(any(AssociationInfo.class));
        assertThat(future.isDone()).isFalse();
    }

    @Test
    public void testStartDiscovery_returnsOneWatchAssociation() throws InterruptedException {
        SettableFuture<Boolean> future = SettableFuture.create();
        DiscoveredDeviceReceiver discoveredDeviceReceiver =
                new DiscoveredDeviceReceiver() {
                    @Override
                    public void onDiscovered(DiscoveredDevice unused) {
                        future.set(true);
                    }
                };

        when(mCompanionDeviceManagerWrapper.getAllAssociations())
                .thenReturn(Utils.getFakeAssociationInfoList(1));
        when(mCompanionDeviceManagerWrapper.getDeviceProfile(any(AssociationInfo.class)))
                .thenReturn(AssociationRequest.DEVICE_PROFILE_WATCH);

        mCdmConnectivityManager.startDiscovery(
                Utils.getFakeDiscoveryFilter(), discoveredDeviceReceiver);

        mTestExecutor.awaitTermination(1, TimeUnit.SECONDS);

        verify(mCompanionDeviceManagerWrapper, times(1)).getAllAssociations();
        verify(mCompanionDeviceManagerWrapper, times(1))
                .getDeviceProfile(any(AssociationInfo.class));
        assertThat(future.isDone()).isTrue();
    }

    @Test
    public void testStartDiscovery_returnsMultipleWatchAssociations() throws InterruptedException {
        int numAssociations = 3;
        SettableFuture<Boolean> future = SettableFuture.create();
        DiscoveredDeviceReceiver discoveredDeviceReceiver =
                new DiscoveredDeviceReceiver() {
                    int mNumCallbacks = 0;

                    @Override
                    public void onDiscovered(DiscoveredDevice unused) {
                        ++mNumCallbacks;
                        if (mNumCallbacks == numAssociations) {
                            future.set(true);
                        }
                    }
                };

        when(mCompanionDeviceManagerWrapper.getAllAssociations())
                .thenReturn(Utils.getFakeAssociationInfoList(numAssociations));
        when(mCompanionDeviceManagerWrapper.getDeviceProfile(any(AssociationInfo.class)))
                .thenReturn(AssociationRequest.DEVICE_PROFILE_WATCH);

        mCdmConnectivityManager.startDiscovery(
                Utils.getFakeDiscoveryFilter(), discoveredDeviceReceiver);

        mTestExecutor.awaitTermination(1, TimeUnit.SECONDS);

        verify(mCompanionDeviceManagerWrapper, times(1)).getAllAssociations();
        verify(mCompanionDeviceManagerWrapper, times(numAssociations))
                .getDeviceProfile(any(AssociationInfo.class));
        assertThat(future.isDone()).isTrue();
    }

    @Test
    public void testMultipleStartDiscovery_runsAllCallbacks() throws InterruptedException {
        SettableFuture<Boolean> future1 = SettableFuture.create();
        SettableFuture<Boolean> future2 = SettableFuture.create();
        DiscoveredDeviceReceiver discoveredDeviceReceiver1 =
                new DiscoveredDeviceReceiver() {
                    @Override
                    public void onDiscovered(DiscoveredDevice unused) {
                        future1.set(true);
                    }
                };
        DiscoveredDeviceReceiver discoveredDeviceReceiver2 =
                new DiscoveredDeviceReceiver() {
                    @Override
                    public void onDiscovered(DiscoveredDevice unused) {
                        future2.set(true);
                    }
                };

        when(mCompanionDeviceManagerWrapper.getAllAssociations())
                .thenReturn(Utils.getFakeAssociationInfoList(1));
        when(mCompanionDeviceManagerWrapper.getDeviceProfile(any(AssociationInfo.class)))
                .thenReturn(AssociationRequest.DEVICE_PROFILE_WATCH);

        // Start discovery twice with different callbacks.
        mCdmConnectivityManager.startDiscovery(
                Utils.getFakeDiscoveryFilter(), discoveredDeviceReceiver1);
        mCdmConnectivityManager.startDiscovery(
                Utils.getFakeDiscoveryFilter(), discoveredDeviceReceiver2);

        mTestExecutor.awaitTermination(1, TimeUnit.SECONDS);

        verify(mCompanionDeviceManagerWrapper, times(2)).getAllAssociations();
        verify(mCompanionDeviceManagerWrapper, times(2))
                .getDeviceProfile(any(AssociationInfo.class));
        assertThat(future1.isDone()).isTrue();
        assertThat(future2.isDone()).isTrue();
    }

    @Test
    public void testStartDiscovery_returnsExpectedDiscoveredDevice() throws InterruptedException {
        SettableFuture<Boolean> future = SettableFuture.create();
        DiscoveredDeviceReceiver discoveredDeviceReceiver =
                new DiscoveredDeviceReceiver() {
                    @Override
                    public void onDiscovered(DiscoveredDevice device) {
                        assertThat(device.getConnectionInfo() instanceof CdmConnectionInfo)
                                .isTrue();

                        CdmConnectionInfo connectionInfo =
                                (CdmConnectionInfo) device.getConnectionInfo();
                        if (connectionInfo.getConnectionParams().getDeviceMacAddress().toString()
                                .equals(Utils.FAKE_PEER_ADDRESS)
                                && connectionInfo.getConnectionId() == Utils.FAKE_CONNECTION_ID) {
                            future.set(true);
                        }
                    }
                };

        when(mCompanionDeviceManagerWrapper.getAllAssociations())
                .thenReturn(Utils.getFakeAssociationInfoList(1));
        when(mCompanionDeviceManagerWrapper.getDeviceProfile(any(AssociationInfo.class)))
                .thenReturn(AssociationRequest.DEVICE_PROFILE_WATCH);

        mCdmConnectivityManager.startDiscovery(
                Utils.getFakeDiscoveryFilter(), discoveredDeviceReceiver);

        mTestExecutor.awaitTermination(1, TimeUnit.SECONDS);

        verify(mCompanionDeviceManagerWrapper, times(1)).getAllAssociations();
        verify(mCompanionDeviceManagerWrapper, times(1))
                .getDeviceProfile(any(AssociationInfo.class));
        assertThat(future.isDone()).isTrue();
    }

    @Test
    public void testStopDiscovery_removesCallback() {
        DiscoveredDeviceReceiver discoveredDeviceReceiver =
                new DiscoveredDeviceReceiver() {
                    @Override
                    public void onDiscovered(DiscoveredDevice unused) {}
                };

        DiscoveryFilter discoveryFilter = Utils.getFakeDiscoveryFilter();
        mCdmConnectivityManager.startDiscovery(discoveryFilter, discoveredDeviceReceiver);

        assertThat(mCdmConnectivityManager.hasPendingCallbacks(discoveredDeviceReceiver)).isTrue();

        mCdmConnectivityManager.stopDiscovery(discoveryFilter, discoveredDeviceReceiver);

        assertThat(mCdmConnectivityManager.hasPendingCallbacks(discoveredDeviceReceiver)).isFalse();
    }

    @Test
    public void testStopDiscovery_DoesNotRunCallback() {
        SettableFuture future = SettableFuture.create();
        DiscoveredDeviceReceiver discoveredDeviceReceiver =
                new DiscoveredDeviceReceiver() {
                    @Override
                    public void onDiscovered(DiscoveredDevice unused) {
                        future.set(true);
                    }
                };

        DiscoveryFilter discoveryFilter = Utils.getFakeDiscoveryFilter();
        mCdmConnectivityManager.startDiscovery(discoveryFilter, discoveredDeviceReceiver);
        mCdmConnectivityManager.stopDiscovery(discoveryFilter, discoveredDeviceReceiver);

        assertThat(future.isDone()).isFalse();
    }
}

