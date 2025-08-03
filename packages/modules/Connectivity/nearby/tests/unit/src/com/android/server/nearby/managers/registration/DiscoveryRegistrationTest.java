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

package com.android.server.nearby.managers.registration;

import static android.nearby.PresenceCredential.IDENTITY_TYPE_PRIVATE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.AppOpsManager;
import android.nearby.DataElement;
import android.nearby.IScanListener;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.PresenceScanFilter;
import android.nearby.PublicCredential;
import android.nearby.ScanCallback;
import android.nearby.ScanFilter;
import android.nearby.ScanRequest;
import android.os.IBinder;
import android.util.ArraySet;

import androidx.annotation.NonNull;

import com.android.server.nearby.managers.ListenerMultiplexer;
import com.android.server.nearby.managers.MergedDiscoveryRequest;
import com.android.server.nearby.util.identity.CallerIdentity;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * Unit test for {@link DiscoveryRegistration} class.
 */
public class DiscoveryRegistrationTest {
    private static final int RSSI = -40;
    private static final int ACTION = 123;
    private static final byte[] SECRETE_ID = new byte[]{1, 2, 3, 4};
    private static final byte[] AUTHENTICITY_KEY = new byte[]{0, 1, 1, 1};
    private static final byte[] PUBLIC_KEY = new byte[]{1, 1, 2, 2};
    private static final byte[] ENCRYPTED_METADATA = new byte[]{1, 2, 3, 4, 5};
    private static final byte[] METADATA_ENCRYPTION_KEY_TAG = new byte[]{1, 1, 3, 4, 5};
    private static final int KEY = 3;
    private static final byte[] VALUE = new byte[]{1, 1, 1, 1};
    private final PublicCredential mPublicCredential = new PublicCredential.Builder(SECRETE_ID,
            AUTHENTICITY_KEY, PUBLIC_KEY, ENCRYPTED_METADATA,
            METADATA_ENCRYPTION_KEY_TAG).setIdentityType(IDENTITY_TYPE_PRIVATE).build();
    private final PresenceScanFilter mFilter = new PresenceScanFilter.Builder().setMaxPathLoss(
            50).addCredential(mPublicCredential).addPresenceAction(ACTION).addExtendedProperty(
            new DataElement(KEY, VALUE)).build();
    private DiscoveryRegistration mDiscoveryRegistration;
    private ScanRequest mScanRequest;
    private TestDiscoveryManager mOwner;
    private Object mMultiplexLock;
    @Mock
    private IScanListener mCallback;
    @Mock
    private CallerIdentity mIdentity;
    @Mock
    private AppOpsManager mAppOpsManager;
    @Mock
    private IBinder mBinder;

    @Before
    public void setUp() {
        initMocks(this);
        when(mCallback.asBinder()).thenReturn(mBinder);
        when(mAppOpsManager.noteOp(eq("android:bluetooth_scan"), eq(0), eq(null), eq(null),
                eq(null))).thenReturn(AppOpsManager.MODE_ALLOWED);

        mOwner = new TestDiscoveryManager();
        mMultiplexLock = new Object();
        mScanRequest = new ScanRequest.Builder().setScanType(
                ScanRequest.SCAN_TYPE_NEARBY_PRESENCE).addScanFilter(mFilter).build();
        mDiscoveryRegistration = new DiscoveryRegistration(mOwner, mScanRequest, mCallback,
                Executors.newSingleThreadExecutor(), mIdentity, mMultiplexLock, mAppOpsManager);
    }

    @Test
    public void test_getScanRequest() {
        assertThat(mDiscoveryRegistration.getScanRequest()).isEqualTo(mScanRequest);
    }

    @Test
    public void test_getActions() {
        Set<Integer> result = new ArraySet<>();
        result.add(ACTION);
        assertThat(mDiscoveryRegistration.getActions()).isEqualTo(result);
    }

    @Test
    public void test_getOwner() {
        assertThat(mDiscoveryRegistration.getOwner()).isEqualTo(mOwner);
    }

    @Test
    public void test_getPresenceScanFilters() {
        Set<ScanFilter> result = new ArraySet<>();
        result.add(mFilter);
        assertThat(mDiscoveryRegistration.getPresenceScanFilters()).isEqualTo(result);
    }

    @Test
    public void test_presenceFilterMatches_match() {
        NearbyDeviceParcelable device = new NearbyDeviceParcelable.Builder().setDeviceId(
                123).setName("test").setTxPower(RSSI + 1).setRssi(RSSI).setScanType(
                ScanRequest.SCAN_TYPE_NEARBY_PRESENCE).setAction(ACTION).setEncryptionKeyTag(
                METADATA_ENCRYPTION_KEY_TAG).build();
        assertThat(DiscoveryRegistration.presenceFilterMatches(device, List.of(mFilter))).isTrue();
    }

    @Test
    public void test_presenceFilterMatches_emptyFilter() {
        NearbyDeviceParcelable device = new NearbyDeviceParcelable.Builder().setDeviceId(
                123).setName("test").setScanType(ScanRequest.SCAN_TYPE_NEARBY_PRESENCE).build();
        assertThat(DiscoveryRegistration.presenceFilterMatches(device, List.of())).isTrue();
    }

    @Test
    public void test_presenceFilterMatches_actionNotMatch() {
        NearbyDeviceParcelable device = new NearbyDeviceParcelable.Builder().setDeviceId(
                12).setName("test").setRssi(RSSI).setScanType(
                ScanRequest.SCAN_TYPE_NEARBY_PRESENCE).setAction(5).setEncryptionKeyTag(
                METADATA_ENCRYPTION_KEY_TAG).build();
        assertThat(DiscoveryRegistration.presenceFilterMatches(device, List.of(mFilter))).isFalse();
    }

    @Test
    public void test_onDiscoveredOnUpdatedCalled() throws Exception {
        final long deviceId = 122;
        NearbyDeviceParcelable.Builder builder = new NearbyDeviceParcelable.Builder().setDeviceId(
                deviceId).setName("test").setTxPower(RSSI + 1).setRssi(RSSI).setScanType(
                ScanRequest.SCAN_TYPE_NEARBY_PRESENCE).setAction(ACTION).setEncryptionKeyTag(
                METADATA_ENCRYPTION_KEY_TAG);
        runOperation(mDiscoveryRegistration.onNearbyDeviceDiscovered(builder.build()));

        verify(mCallback, times(1)).onDiscovered(eq(builder.build()));
        verify(mCallback, never()).onUpdated(any());
        verify(mCallback, never()).onLost(any());
        verify(mCallback, never()).onError(anyInt());
        assertThat(mDiscoveryRegistration.getDiscoveryOnLostAlarms().get(deviceId)).isNotNull();
        reset(mCallback);

        // Update RSSI
        runOperation(
                mDiscoveryRegistration.onNearbyDeviceDiscovered(builder.setRssi(RSSI - 1).build()));
        verify(mCallback, never()).onDiscovered(any());
        verify(mCallback, times(1)).onUpdated(eq(builder.build()));
        verify(mCallback, never()).onLost(any());
        verify(mCallback, never()).onError(anyInt());
        assertThat(mDiscoveryRegistration.getDiscoveryOnLostAlarms().get(deviceId)).isNotNull();
    }

    @Test
    public void test_onLost() throws Exception {
        final long deviceId = 123;
        NearbyDeviceParcelable device = new NearbyDeviceParcelable.Builder().setDeviceId(
                deviceId).setName("test").setTxPower(RSSI + 1).setRssi(RSSI).setScanType(
                ScanRequest.SCAN_TYPE_NEARBY_PRESENCE).setAction(ACTION).setEncryptionKeyTag(
                METADATA_ENCRYPTION_KEY_TAG).build();
        runOperation(mDiscoveryRegistration.onNearbyDeviceDiscovered(device));
        assertThat(mDiscoveryRegistration.getDiscoveryOnLostAlarms().get(deviceId)).isNotNull();
        verify(mCallback, times(1)).onDiscovered(eq(device));
        verify(mCallback, never()).onUpdated(any());
        verify(mCallback, never()).onError(anyInt());
        verify(mCallback, never()).onLost(any());
        reset(mCallback);

        runOperation(mDiscoveryRegistration.reportDeviceLost(device));

        assertThat(mDiscoveryRegistration.getDiscoveryOnLostAlarms().get(deviceId)).isNull();
        verify(mCallback, never()).onDiscovered(eq(device));
        verify(mCallback, never()).onUpdated(any());
        verify(mCallback, never()).onError(anyInt());
        verify(mCallback, times(1)).onLost(eq(device));
    }

    @Test
    public void test_onError() throws Exception {
        AppOpsManager manager = mock(AppOpsManager.class);
        when(manager.noteOp(eq("android:bluetooth_scan"), eq(0), eq(null), eq(null),
                eq(null))).thenReturn(AppOpsManager.MODE_IGNORED);

        DiscoveryRegistration r = new DiscoveryRegistration(mOwner, mScanRequest, mCallback,
                Executors.newSingleThreadExecutor(), mIdentity, mMultiplexLock, manager);

        NearbyDeviceParcelable device = new NearbyDeviceParcelable.Builder().setDeviceId(
                123).setName("test").setTxPower(RSSI + 1).setRssi(RSSI).setScanType(
                ScanRequest.SCAN_TYPE_NEARBY_PRESENCE).setAction(ACTION).setEncryptionKeyTag(
                METADATA_ENCRYPTION_KEY_TAG).build();
        runOperation(r.onNearbyDeviceDiscovered(device));

        verify(mCallback, never()).onDiscovered(any());
        verify(mCallback, never()).onUpdated(any());
        verify(mCallback, never()).onLost(any());
        verify(mCallback, times(1)).onError(eq(ScanCallback.ERROR_PERMISSION_DENIED));
    }

    private void runOperation(BinderListenerRegistration.ListenerOperation<IScanListener> operation)
            throws Exception {
        if (operation == null) {
            return;
        }
        operation.onScheduled(false);
        operation.operate(mCallback);
        operation.onComplete(/* success= */ true);
    }

    private static class TestDiscoveryManager extends
            ListenerMultiplexer<IScanListener, DiscoveryRegistration, MergedDiscoveryRequest> {

        @Override
        public MergedDiscoveryRequest mergeRegistrations(
                @NonNull Collection<DiscoveryRegistration> discoveryRegistrations) {
            return null;
        }

        @Override
        public void onMergedRegistrationsUpdated() {

        }
    }
}
