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

package com.android.server.nearby.managers;

import static android.Manifest.permission.READ_DEVICE_CONFIG;
import static android.nearby.PresenceCredential.IDENTITY_TYPE_PRIVATE;
import static android.nearby.ScanRequest.SCAN_TYPE_NEARBY_PRESENCE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AppOpsManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.nearby.DataElement;
import android.nearby.IScanListener;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.PresenceScanFilter;
import android.nearby.PublicCredential;
import android.nearby.ScanRequest;
import android.os.IBinder;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.nearby.injector.Injector;
import com.android.server.nearby.provider.BleDiscoveryProvider;
import com.android.server.nearby.provider.ChreCommunication;
import com.android.server.nearby.provider.ChreDiscoveryProvider;
import com.android.server.nearby.provider.DiscoveryProviderController;
import com.android.server.nearby.util.identity.CallerIdentity;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Unit test for {@link DiscoveryProviderManagerLegacy} class.
 */
public class DiscoveryProviderManagerLegacyTest {
    private static final int SCAN_MODE_CHRE_ONLY = 3;
    private static final int DATA_TYPE_SCAN_MODE = 102;
    private static final int UID = 1234;
    private static final int PID = 5678;
    private static final String PACKAGE_NAME = "android.nearby.test";
    private static final int RSSI = -60;
    @Mock
    Injector mInjector;
    @Mock
    Context mContext;
    @Mock
    AppOpsManager mAppOpsManager;
    @Mock
    BleDiscoveryProvider mBleDiscoveryProvider;
    @Mock
    ChreDiscoveryProvider mChreDiscoveryProvider;
    @Mock
    DiscoveryProviderController mBluetoothController;
    @Mock
    DiscoveryProviderController mChreController;
    @Mock
    IScanListener mScanListener;
    @Mock
    CallerIdentity mCallerIdentity;
    @Mock
    DiscoveryProviderManagerLegacy.ScanListenerDeathRecipient mScanListenerDeathRecipient;
    @Mock
    IBinder mIBinder;
    @Mock
    BluetoothAdapter mBluetoothAdapter;
    private DiscoveryProviderManagerLegacy mDiscoveryProviderManager;
    private Map<IBinder, DiscoveryProviderManagerLegacy.ScanListenerRecord>
            mScanTypeScanListenerRecordMap;

    private static PresenceScanFilter getPresenceScanFilter() {
        final byte[] secretId = new byte[]{1, 2, 3, 4};
        final byte[] authenticityKey = new byte[]{0, 1, 1, 1};
        final byte[] publicKey = new byte[]{1, 1, 2, 2};
        final byte[] encryptedMetadata = new byte[]{1, 2, 3, 4, 5};
        final byte[] metadataEncryptionKeyTag = new byte[]{1, 1, 3, 4, 5};

        PublicCredential credential = new PublicCredential.Builder(
                secretId, authenticityKey, publicKey, encryptedMetadata, metadataEncryptionKeyTag)
                .setIdentityType(IDENTITY_TYPE_PRIVATE)
                .build();

        final int action = 123;
        return new PresenceScanFilter.Builder()
                .addCredential(credential)
                .setMaxPathLoss(RSSI)
                .addPresenceAction(action)
                .build();
    }

    private static PresenceScanFilter getChreOnlyPresenceScanFilter() {
        final byte[] secretId = new byte[]{1, 2, 3, 4};
        final byte[] authenticityKey = new byte[]{0, 1, 1, 1};
        final byte[] publicKey = new byte[]{1, 1, 2, 2};
        final byte[] encryptedMetadata = new byte[]{1, 2, 3, 4, 5};
        final byte[] metadataEncryptionKeyTag = new byte[]{1, 1, 3, 4, 5};

        PublicCredential credential = new PublicCredential.Builder(
                secretId, authenticityKey, publicKey, encryptedMetadata, metadataEncryptionKeyTag)
                .setIdentityType(IDENTITY_TYPE_PRIVATE)
                .build();

        final int action = 123;
        DataElement scanModeElement = new DataElement(DATA_TYPE_SCAN_MODE,
                new byte[]{SCAN_MODE_CHRE_ONLY});
        return new PresenceScanFilter.Builder()
                .addCredential(credential)
                .setMaxPathLoss(RSSI)
                .addPresenceAction(action)
                .addExtendedProperty(scanModeElement)
                .build();
    }

    @Before
    public void setup() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                READ_DEVICE_CONFIG);
        MockitoAnnotations.initMocks(this);
        when(mInjector.getAppOpsManager()).thenReturn(mAppOpsManager);
        when(mInjector.getBluetoothAdapter()).thenReturn(mBluetoothAdapter);
        when(mBleDiscoveryProvider.getController()).thenReturn(mBluetoothController);
        when(mChreDiscoveryProvider.getController()).thenReturn(mChreController);
        when(mBluetoothAdapter.isBleScanAlwaysAvailable()).thenReturn(true);
        when(mBluetoothAdapter.isEnabled()).thenReturn(true);

        mScanTypeScanListenerRecordMap = new HashMap<>();
        mDiscoveryProviderManager =
                new DiscoveryProviderManagerLegacy(mContext, mInjector,
                        mBleDiscoveryProvider,
                        mChreDiscoveryProvider,
                        mScanTypeScanListenerRecordMap);
        mCallerIdentity = CallerIdentity
                .forTest(UID, PID, PACKAGE_NAME, /* attributionTag= */ null);
    }

    @Test
    public void testOnNearbyDeviceDiscovered() {
        NearbyDeviceParcelable nearbyDeviceParcelable = new NearbyDeviceParcelable.Builder()
                .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                .build();
        mDiscoveryProviderManager.onNearbyDeviceDiscovered(nearbyDeviceParcelable);
    }

    @Test
    public void testInvalidateProviderScanMode() {
        mDiscoveryProviderManager.invalidateProviderScanMode();
    }

    @Test
    public void test_enableBleWhenBleOff() throws Exception {
        when(mBluetoothAdapter.isEnabled()).thenReturn(false);
        mDiscoveryProviderManager.init();
        verify(mBluetoothAdapter, times(1)).enableBLE();
    }

    @Test
    public void testStartProviders_chreOnlyChreAvailable_bleProviderNotStarted() {
        when(mChreDiscoveryProvider.available()).thenReturn(true);

        ScanRequest scanRequest = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                .addScanFilter(getChreOnlyPresenceScanFilter()).build();
        DiscoveryProviderManagerLegacy.ScanListenerRecord record =
                new DiscoveryProviderManagerLegacy.ScanListenerRecord(
                        scanRequest, mScanListener,
                        mCallerIdentity, mScanListenerDeathRecipient);
        mScanTypeScanListenerRecordMap.put(mIBinder, record);

        Boolean start = mDiscoveryProviderManager.startProviders(scanRequest);
        verify(mBluetoothController, never()).start();
        assertThat(start).isTrue();
    }

    @Test
    public void testStartProviders_chreOnlyChreAvailable_multipleFilters_bleProviderNotStarted() {
        when(mChreDiscoveryProvider.available()).thenReturn(true);

        ScanRequest scanRequest = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                .addScanFilter(getChreOnlyPresenceScanFilter())
                .addScanFilter(getPresenceScanFilter()).build();
        DiscoveryProviderManagerLegacy.ScanListenerRecord record =
                new DiscoveryProviderManagerLegacy.ScanListenerRecord(
                        scanRequest, mScanListener,
                        mCallerIdentity, mScanListenerDeathRecipient);
        mScanTypeScanListenerRecordMap.put(mIBinder, record);

        Boolean start = mDiscoveryProviderManager.startProviders(scanRequest);
        verify(mBluetoothController, never()).start();
        assertThat(start).isTrue();
    }

    @Test
    public void testStartProviders_chreOnlyChreUnavailable_bleProviderNotStarted() {
        when(mChreDiscoveryProvider.available()).thenReturn(false);

        ScanRequest scanRequest = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                .addScanFilter(getChreOnlyPresenceScanFilter()).build();
        DiscoveryProviderManagerLegacy.ScanListenerRecord record =
                new DiscoveryProviderManagerLegacy.ScanListenerRecord(
                        scanRequest, mScanListener,
                        mCallerIdentity, mScanListenerDeathRecipient);
        mScanTypeScanListenerRecordMap.put(mIBinder, record);

        Boolean start = mDiscoveryProviderManager.startProviders(scanRequest);
        verify(mBluetoothController, never()).start();
        assertThat(start).isFalse();
    }

    @Test
    public void testStartProviders_notChreOnlyChreAvailable_bleProviderNotStarted() {
        when(mChreDiscoveryProvider.available()).thenReturn(true);

        ScanRequest scanRequest = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                .addScanFilter(getPresenceScanFilter()).build();
        DiscoveryProviderManagerLegacy.ScanListenerRecord record =
                new DiscoveryProviderManagerLegacy.ScanListenerRecord(
                        scanRequest, mScanListener,
                        mCallerIdentity, mScanListenerDeathRecipient);
        mScanTypeScanListenerRecordMap.put(mIBinder, record);

        Boolean start = mDiscoveryProviderManager.startProviders(scanRequest);
        verify(mBluetoothController, never()).start();
        assertThat(start).isTrue();
    }

    @Test
    public void testStartProviders_notChreOnlyChreUnavailable_bleProviderStarted() {
        when(mChreDiscoveryProvider.available()).thenReturn(false);

        ScanRequest scanRequest = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                .addScanFilter(getPresenceScanFilter()).build();
        DiscoveryProviderManagerLegacy.ScanListenerRecord record =
                new DiscoveryProviderManagerLegacy.ScanListenerRecord(
                        scanRequest, mScanListener,
                        mCallerIdentity, mScanListenerDeathRecipient);
        mScanTypeScanListenerRecordMap.put(mIBinder, record);

        Boolean start = mDiscoveryProviderManager.startProviders(scanRequest);
        verify(mBluetoothController, atLeastOnce()).start();
        assertThat(start).isTrue();
    }

    @Test
    public void testStartProviders_chreOnlyChreUndetermined_bleProviderNotStarted() {
        when(mChreDiscoveryProvider.available()).thenReturn(null);

        ScanRequest scanRequest = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                .addScanFilter(getChreOnlyPresenceScanFilter()).build();
        DiscoveryProviderManagerLegacy.ScanListenerRecord record =
                new DiscoveryProviderManagerLegacy.ScanListenerRecord(
                        scanRequest, mScanListener,
                        mCallerIdentity, mScanListenerDeathRecipient);
        mScanTypeScanListenerRecordMap.put(mIBinder, record);

        Boolean start = mDiscoveryProviderManager.startProviders(scanRequest);
        verify(mBluetoothController, never()).start();
        assertThat(start).isNull();
    }

    @Test
    public void testStartProviders_notChreOnlyChreUndetermined_bleProviderStarted() {
        when(mChreDiscoveryProvider.available()).thenReturn(null);

        ScanRequest scanRequest = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                .addScanFilter(getPresenceScanFilter()).build();
        DiscoveryProviderManagerLegacy.ScanListenerRecord record =
                new DiscoveryProviderManagerLegacy.ScanListenerRecord(
                        scanRequest, mScanListener,
                        mCallerIdentity, mScanListenerDeathRecipient);
        mScanTypeScanListenerRecordMap.put(mIBinder, record);

        Boolean start = mDiscoveryProviderManager.startProviders(scanRequest);
        verify(mBluetoothController, atLeastOnce()).start();
        assertThat(start).isTrue();
    }

    @Test
    public void test_stopChreProvider_clearFilters() throws Exception {
        // Cannot use mocked ChreDiscoveryProvider,
        // so we cannot use class variable mDiscoveryProviderManager
        ExecutorService executor = Executors.newSingleThreadExecutor();
        DiscoveryProviderManagerLegacy manager =
                new DiscoveryProviderManagerLegacy(mContext, mInjector,
                        mBleDiscoveryProvider,
                        new ChreDiscoveryProvider(
                                mContext,
                                new ChreCommunication(mInjector, mContext, executor), executor),
                        mScanTypeScanListenerRecordMap);
        ScanRequest scanRequest = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                .addScanFilter(getPresenceScanFilter()).build();
        DiscoveryProviderManagerLegacy.ScanListenerRecord record =
                new DiscoveryProviderManagerLegacy.ScanListenerRecord(
                        scanRequest, mScanListener,
                        mCallerIdentity, mScanListenerDeathRecipient);
        mScanTypeScanListenerRecordMap.put(mIBinder, record);
        manager.startChreProvider(List.of(getPresenceScanFilter()));
        // This is an asynchronized process. The filters will be set in executor thread. So we need
        // to wait for some time to get the correct result.
        Thread.sleep(200);

        assertThat(manager.mChreDiscoveryProvider.getController().isStarted())
                .isTrue();
        assertThat(manager.mChreDiscoveryProvider.getFiltersLocked()).isNotNull();

        manager.stopChreProvider();
        Thread.sleep(200);
        // The filters should be cleared right after.
        assertThat(manager.mChreDiscoveryProvider.getController().isStarted())
                .isFalse();
        assertThat(manager.mChreDiscoveryProvider.getFiltersLocked()).isEmpty();
    }

    @Test
    public void test_restartChreProvider() throws Exception {
        // Cannot use mocked ChreDiscoveryProvider,
        // so we cannot use class variable mDiscoveryProviderManager
        ExecutorService executor = Executors.newSingleThreadExecutor();
        DiscoveryProviderManagerLegacy manager =
                new DiscoveryProviderManagerLegacy(mContext, mInjector,
                        mBleDiscoveryProvider,
                        new ChreDiscoveryProvider(
                                mContext,
                                new ChreCommunication(mInjector, mContext, executor), executor),
                        mScanTypeScanListenerRecordMap);
        ScanRequest scanRequest = new ScanRequest.Builder()
                .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                .addScanFilter(getPresenceScanFilter()).build();
        DiscoveryProviderManagerLegacy.ScanListenerRecord record =
                new DiscoveryProviderManagerLegacy.ScanListenerRecord(scanRequest, mScanListener,
                        mCallerIdentity, mScanListenerDeathRecipient);
        mScanTypeScanListenerRecordMap.put(mIBinder, record);
        manager.startChreProvider(List.of(getPresenceScanFilter()));
        // This is an asynchronized process. The filters will be set in executor thread. So we need
        // to wait for some time to get the correct result.
        Thread.sleep(200);

        assertThat(manager.mChreDiscoveryProvider.getController().isStarted())
                .isTrue();
        assertThat(manager.mChreDiscoveryProvider.getFiltersLocked()).isNotNull();

        // We want to make sure quickly restart the provider the filters should
        // be reset correctly.
        // See b/255922206, there can be a race condition that filters get cleared because onStop()
        // get executed after onStart() if they are called from different threads.
        manager.stopChreProvider();
        manager.mChreDiscoveryProvider.getController().setProviderScanFilters(
                List.of(getPresenceScanFilter()));
        manager.startChreProvider(List.of(getPresenceScanFilter()));
        Thread.sleep(200);
        assertThat(manager.mChreDiscoveryProvider.getController().isStarted())
                .isTrue();
        assertThat(manager.mChreDiscoveryProvider.getFiltersLocked()).isNotNull();

        // Wait for enough time
        Thread.sleep(1000);

        assertThat(manager.mChreDiscoveryProvider.getController().isStarted())
                .isTrue();
        assertThat(manager.mChreDiscoveryProvider.getFiltersLocked()).isNotNull();
    }

    @Test
    public void isBluetoothEnabledTest_bluetoothEnabled() {
        when(mBluetoothAdapter.isEnabled()).thenReturn(true);
        when(mBluetoothAdapter.isLeEnabled()).thenReturn(false);
        when(mBluetoothAdapter.isBleScanAlwaysAvailable()).thenReturn(true);
        when(mBluetoothAdapter.enableBLE()).thenReturn(true);

        assertThat(mDiscoveryProviderManager.setBleScanEnabled()).isTrue();
    }

    @Test
    public void isBluetoothEnabledTest_bleEnabled() {
        when(mBluetoothAdapter.isEnabled()).thenReturn(false);
        when(mBluetoothAdapter.isLeEnabled()).thenReturn(true);
        when(mBluetoothAdapter.isBleScanAlwaysAvailable()).thenReturn(true);
        when(mBluetoothAdapter.enableBLE()).thenReturn(true);

        assertThat(mDiscoveryProviderManager.setBleScanEnabled()).isTrue();
    }

    @Test
    public void enabledTest_enabled() {
        when(mBluetoothAdapter.isEnabled()).thenReturn(false);
        when(mBluetoothAdapter.isLeEnabled()).thenReturn(false);
        when(mBluetoothAdapter.isBleScanAlwaysAvailable()).thenReturn(true);
        when(mBluetoothAdapter.enableBLE()).thenReturn(true);

        assertThat(mDiscoveryProviderManager.setBleScanEnabled()).isTrue();
    }

    @Test
    public void enabledTest_enableFailed() {
        when(mBluetoothAdapter.isEnabled()).thenReturn(false);
        when(mBluetoothAdapter.isLeEnabled()).thenReturn(false);
        when(mBluetoothAdapter.isBleScanAlwaysAvailable()).thenReturn(true);
        when(mBluetoothAdapter.enableBLE()).thenReturn(false);

        assertThat(mDiscoveryProviderManager.setBleScanEnabled()).isFalse();
    }

    @Test
    public void enabledTest_scanIsOn() {
        when(mBluetoothAdapter.isEnabled()).thenReturn(true);
        when(mBluetoothAdapter.isLeEnabled()).thenReturn(false);
        when(mBluetoothAdapter.isBleScanAlwaysAvailable()).thenReturn(false);

        assertThat(mDiscoveryProviderManager.setBleScanEnabled()).isTrue();
    }

    @Test
    public void enabledTest_failed() {
        when(mBluetoothAdapter.isEnabled()).thenReturn(false);
        when(mBluetoothAdapter.isLeEnabled()).thenReturn(false);
        when(mBluetoothAdapter.isBleScanAlwaysAvailable()).thenReturn(false);

        assertThat(mDiscoveryProviderManager.setBleScanEnabled()).isFalse();
    }
}
