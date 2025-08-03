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

package com.android.bluetooth.le_scan;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES_AUTO_BATCH;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_AMBIENT_DISCOVERY;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_BALANCED;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_POWER;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_OPPORTUNISTIC;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_SCREEN_OFF;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_SCREEN_OFF_BALANCED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProtoEnums;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.location.LocationManager;
import android.os.BatteryStatsManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.test.TestLooper;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.util.Log;
import android.util.SparseIntArray;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.BluetoothAdapterProxy;
import com.android.bluetooth.btservice.MetricsLogger;
import com.android.bluetooth.flags.Flags;
import com.android.bluetooth.gatt.GattNativeInterface;
import com.android.bluetooth.gatt.GattObjectsFactory;
import com.android.bluetooth.gatt.GattService;
import com.android.internal.app.IBatteryStats;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Test cases for {@link ScanManager}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ScanManagerTest {
    private static final String TAG = ScanManagerTest.class.getSimpleName();
    private static final int DELAY_ASYNC_MS = 50;
    private static final int DELAY_DEFAULT_SCAN_TIMEOUT_MS = 1500000;
    private static final int DELAY_SCAN_TIMEOUT_MS = 100;
    private static final int DEFAULT_SCAN_REPORT_DELAY_MS = 100;
    private static final int DEFAULT_NUM_OFFLOAD_SCAN_FILTER = 16;
    private static final int DEFAULT_BYTES_OFFLOAD_SCAN_RESULT_STORAGE = 4096;
    private static final int DELAY_SCAN_UPGRADE_DURATION_MS = 150;
    private static final int DELAY_SCAN_DOWNGRADE_DURATION_MS = 100;

    private Context mTargetContext;
    private ScanManager mScanManager;
    private Handler mHandler;
    private TestLooper mTestLooper;
    private CountDownLatch mLatch;
    private long mScanReportDelay;

    // BatteryStatsManager is final and cannot be mocked with regular mockito, so just mock the
    // underlying binder calls.
    final BatteryStatsManager mBatteryStatsManager =
            new BatteryStatsManager(mock(IBatteryStats.class));

    @Rule public final ServiceTestRule mServiceRule = new ServiceTestRule();
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock private AdapterService mAdapterService;
    @Mock private GattService mMockGattService;
    @Mock private BluetoothAdapterProxy mBluetoothAdapterProxy;
    @Mock private LocationManager mLocationManager;
    @Spy private GattObjectsFactory mFactory = GattObjectsFactory.getInstance();
    @Mock private GattNativeInterface mNativeInterface;
    @Mock private ScanNativeInterface mScanNativeInterface;
    @Mock private MetricsLogger  mMetricsLogger;
    private AppScanStats mMockAppScanStats;

    private MockContentResolver mMockContentResolver;
    @Captor ArgumentCaptor<Long> mScanDurationCaptor;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);

        TestUtils.setAdapterService(mAdapterService);
        when(mAdapterService.getScanTimeoutMillis())
                .thenReturn((long) DELAY_DEFAULT_SCAN_TIMEOUT_MS);
        when(mAdapterService.getNumOfOffloadedScanFilterSupported())
                .thenReturn(DEFAULT_NUM_OFFLOAD_SCAN_FILTER);
        when(mAdapterService.getOffloadedScanResultStorage())
                .thenReturn(DEFAULT_BYTES_OFFLOAD_SCAN_RESULT_STORAGE);

        TestUtils.mockGetSystemService(
                mAdapterService, Context.LOCATION_SERVICE, LocationManager.class, mLocationManager);
        doReturn(true).when(mLocationManager).isLocationEnabled();

        TestUtils.mockGetSystemService(
                mMockGattService,
                Context.DISPLAY_SERVICE,
                DisplayManager.class,
                mTargetContext.getSystemService(DisplayManager.class));
        TestUtils.mockGetSystemService(
                mMockGattService,
                Context.BATTERY_STATS_SERVICE,
                BatteryStatsManager.class,
                mBatteryStatsManager);
        TestUtils.mockGetSystemService(mMockGattService, Context.ALARM_SERVICE, AlarmManager.class);

        mMockContentResolver = new MockContentResolver(mTargetContext);
        mMockContentResolver.addProvider(
                Settings.AUTHORITY,
                new MockContentProvider() {
                    @Override
                    public Bundle call(String method, String request, Bundle args) {
                        return Bundle.EMPTY;
                    }
                });
        doReturn(mMockContentResolver).when(mMockGattService).getContentResolver();
        BluetoothAdapterProxy.setInstanceForTesting(mBluetoothAdapterProxy);
        // Needed to mock Native call/callback when hw offload scan filter is enabled
        when(mBluetoothAdapterProxy.isOffloadedScanFilteringSupported()).thenReturn(true);

        GattObjectsFactory.setInstanceForTesting(mFactory);
        doReturn(mNativeInterface).when(mFactory).getNativeInterface();
        doReturn(mScanNativeInterface).when(mFactory).getScanNativeInterface();
        // Mock JNI callback in ScanNativeInterface
        doReturn(true).when(mScanNativeInterface).waitForCallback(anyInt());

        MetricsLogger.setInstanceForTesting(mMetricsLogger);

        doReturn(mTargetContext.getUser()).when(mMockGattService).getUser();
        doReturn(mTargetContext.getPackageName()).when(mMockGattService).getPackageName();

        mTestLooper = new TestLooper();
        mTestLooper.startAutoDispatch();
        mScanManager =
                new ScanManager(
                        mMockGattService,
                        mAdapterService,
                        mBluetoothAdapterProxy,
                        mTestLooper.getLooper());

        mHandler = mScanManager.getClientHandler();
        assertThat(mHandler).isNotNull();

        mLatch = new CountDownLatch(1);
        assertThat(mLatch).isNotNull();

        mScanReportDelay = DEFAULT_SCAN_REPORT_DELAY_MS;
        mMockAppScanStats = spy(new AppScanStats("Test", null, null, mMockGattService));
    }

    @After
    public void tearDown() throws Exception {
        mTestLooper.stopAutoDispatchAndIgnoreExceptions();
        TestUtils.clearAdapterService(mAdapterService);
        BluetoothAdapterProxy.setInstanceForTesting(null);
        GattObjectsFactory.setInstanceForTesting(null);
        MetricsLogger.setInstanceForTesting(null);
        MetricsLogger.getInstance();
    }

    private void testSleep(long millis) {
        try {
            mLatch.await(millis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Log.e(TAG, "Latch await", e);
        }
    }

    private void sendMessageWaitForProcessed(Message msg) {
        if (mHandler == null) {
            Log.e(TAG, "sendMessage: mHandler is null.");
            return;
        }
        mHandler.sendMessage(msg);
        // Wait for async work from handler thread
        TestUtils.waitForLooperToFinishScheduledTask(mHandler.getLooper());
    }

    private ScanClient createScanClient(
            int id,
            boolean isFiltered,
            boolean isEmptyFilter,
            int scanMode,
            boolean isBatch,
            boolean isAutoBatch) {
        List<ScanFilter> scanFilterList = createScanFilterList(isFiltered, isEmptyFilter);
        ScanSettings scanSettings = createScanSettings(scanMode, isBatch, isAutoBatch);

        ScanClient client = new ScanClient(id, scanSettings, scanFilterList);
        client.stats = mMockAppScanStats;
        client.stats.recordScanStart(scanSettings, scanFilterList, isFiltered, false, id);
        return client;
    }

    private ScanClient createScanClient(int id, boolean isFiltered, int scanMode) {
        return createScanClient(id, isFiltered, false, scanMode, false, false);
    }

    private ScanClient createScanClient(
            int id, boolean isFiltered, int scanMode,
            boolean isBatch, boolean isAutoBatch) {
        return createScanClient(id, isFiltered, false, scanMode, isBatch, isAutoBatch);
    }

    private ScanClient createScanClient(
            int id, boolean isFiltered, boolean isEmptyFilter, int scanMode) {
        return createScanClient(id, isFiltered, isEmptyFilter, scanMode, false, false);
    }

    private List<ScanFilter> createScanFilterList(boolean isFiltered, boolean isEmptyFilter) {
        List<ScanFilter> scanFilterList = null;
        if (isFiltered) {
            scanFilterList = new ArrayList<>();
            if (isEmptyFilter) {
                scanFilterList.add(new ScanFilter.Builder().build());
            } else {
                scanFilterList.add(new ScanFilter.Builder().setDeviceName("TestName").build());
            }
        }
        return scanFilterList;
    }

    private ScanSettings createScanSettings(int scanMode, boolean isBatch, boolean isAutoBatch) {

        ScanSettings scanSettings = null;
        if (isBatch && isAutoBatch) {
            int autoCallbackType = CALLBACK_TYPE_ALL_MATCHES_AUTO_BATCH;
            scanSettings = new ScanSettings.Builder().setScanMode(scanMode)
                    .setReportDelay(mScanReportDelay).setCallbackType(autoCallbackType)
                    .build();
        } else if (isBatch) {
            scanSettings = new ScanSettings.Builder().setScanMode(scanMode)
                    .setReportDelay(mScanReportDelay).build();
        } else {
            scanSettings = new ScanSettings.Builder().setScanMode(scanMode).build();
        }
        return scanSettings;
    }

    private Message createStartStopScanMessage(boolean isStartScan, Object obj) {
        Message message = new Message();
        message.what = isStartScan ? ScanManager.MSG_START_BLE_SCAN : ScanManager.MSG_STOP_BLE_SCAN;
        message.obj = obj;
        return message;
    }

    private Message createScreenOnOffMessage(boolean isScreenOn) {
        Message message = new Message();
        message.what = isScreenOn ? ScanManager.MSG_SCREEN_ON : ScanManager.MSG_SCREEN_OFF;
        message.obj = null;
        return message;
    }

    private Message createLocationOnOffMessage(boolean isLocationOn) {
        Message message = new Message();
        message.what = isLocationOn ? ScanManager.MSG_RESUME_SCANS : ScanManager.MSG_SUSPEND_SCANS;
        message.obj = null;
        return message;
    }

    private Message createImportanceMessage(boolean isForeground) {
        final int importance = isForeground ? ActivityManager.RunningAppProcessInfo
                .IMPORTANCE_FOREGROUND_SERVICE : ActivityManager.RunningAppProcessInfo
                .IMPORTANCE_FOREGROUND_SERVICE + 1;
        final int uid = Binder.getCallingUid();
        Message message = new Message();
        message.what = ScanManager.MSG_IMPORTANCE_CHANGE;
        message.obj = new ScanManager.UidImportance(uid, importance);
        return message;
    }

    private Message createConnectingMessage(boolean isConnectingOn) {
        Message message = new Message();
        message.what = isConnectingOn ? ScanManager.MSG_START_CONNECTING :
                ScanManager.MSG_STOP_CONNECTING;
        message.obj = null;
        return message;
    }

    @Test
    public void testScreenOffStartUnfilteredScan() {
        // Set filtered scan flag
        final boolean isFiltered = false;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_BALANCED);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_LOW_LATENCY);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_AMBIENT_DISCOVERY);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn off screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(false));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isTrue();
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
        }
    }

    @Test
    public void testScreenOffStartFilteredScan() {
        // Set filtered scan flag
        final boolean isFiltered = true;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_SCREEN_OFF);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_SCREEN_OFF_BALANCED);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_LOW_LATENCY);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_SCREEN_OFF_BALANCED);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn off screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(false));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
        }
    }

    @Test
    public void testScreenOffStartEmptyFilterScan() {
        // Set filtered scan flag
        final boolean isFiltered = true;
        final boolean isEmptyFilter = true;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_BALANCED);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_LOW_LATENCY);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_AMBIENT_DISCOVERY);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn off screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(false));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, isEmptyFilter, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isTrue();
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
        }
    }

    @Test
    public void testScreenOnStartUnfilteredScan() {
        // Set filtered scan flag
        final boolean isFiltered = false;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_BALANCED);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_LOW_LATENCY);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_AMBIENT_DISCOVERY);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
        }
    }

    @Test
    public void testScreenOnStartFilteredScan() {
        // Set filtered scan flag
        final boolean isFiltered = true;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_BALANCED);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_LOW_LATENCY);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_AMBIENT_DISCOVERY);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
        }
    }

    @Test
    public void testResumeUnfilteredScanAfterScreenOn() {
        // Set filtered scan flag
        final boolean isFiltered = false;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_SCREEN_OFF);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_SCREEN_OFF_BALANCED);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_LOW_LATENCY);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_SCREEN_OFF_BALANCED);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn off screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(false));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isTrue();
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
        }
    }

    @Test
    public void testResumeFilteredScanAfterScreenOn() {
        // Set filtered scan flag
        final boolean isFiltered = true;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_SCREEN_OFF);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_SCREEN_OFF_BALANCED);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_LOW_LATENCY);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_SCREEN_OFF_BALANCED);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn off screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(false));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
        }
    }

    @Test
    public void testUnfilteredScanTimeout() {
        // Set filtered scan flag
        final boolean isFiltered = false;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_OPPORTUNISTIC);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_OPPORTUNISTIC);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_OPPORTUNISTIC);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_OPPORTUNISTIC);
        // Set scan timeout through Mock
        when(mAdapterService.getScanTimeoutMillis()).thenReturn((long) DELAY_SCAN_TIMEOUT_MS);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
            // Wait for scan timeout
            testSleep(DELAY_SCAN_TIMEOUT_MS + DELAY_ASYNC_MS);
            TestUtils.waitForLooperToFinishScheduledTask(mHandler.getLooper());
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
            assertThat(client.stats.isScanTimeout(client.scannerId)).isTrue();
            // Turn off screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(false));
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
            // Set as background app
            sendMessageWaitForProcessed(createImportanceMessage(false));
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
            // Set as foreground app
            sendMessageWaitForProcessed(createImportanceMessage(true));
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
        }
    }

    @Test
    public void testFilteredScanTimeout() {
        mTestLooper.stopAutoDispatchAndIgnoreExceptions();
        // Set filtered scan flag
        final boolean isFiltered = true;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_LOW_POWER);
        // Set scan timeout through Mock
        when(mAdapterService.getScanTimeoutMillis()).thenReturn((long) DELAY_SCAN_TIMEOUT_MS);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn on screen
            mHandler.sendMessage(createScreenOnOffMessage(true));
            mTestLooper.dispatchAll();
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan, this sends scan timeout message with delay of DELAY_SCAN_TIMEOUT_MS
            mHandler.sendMessage(createStartStopScanMessage(true, client));
            mTestLooper.dispatchAll();
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
            // Move time forward so scan timeout message can be dispatched
            mTestLooper.moveTimeForward(DELAY_SCAN_TIMEOUT_MS + 1);
            // We can check that MSG_SCAN_TIMEOUT is in the message queue
            assertThat(mHandler.hasMessages(ScanManager.MSG_SCAN_TIMEOUT)).isTrue();
            // Since we are using a TestLooper, need to mock AppScanStats.isScanningTooLong to
            // return true because no real time is elapsed
            doReturn(true).when(mMockAppScanStats).isScanningTooLong();
            mTestLooper.dispatchAll();
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
            assertThat(client.stats.isScanTimeout(client.scannerId)).isTrue();
            // Turn off screen
            mHandler.sendMessage(createScreenOnOffMessage(false));
            mTestLooper.dispatchAll();
            assertThat(client.settings.getScanMode()).isEqualTo(SCAN_MODE_SCREEN_OFF);
            // Set as background app
            mHandler.sendMessage(createImportanceMessage(false));
            mTestLooper.dispatchAll();
            assertThat(client.settings.getScanMode()).isEqualTo(SCAN_MODE_SCREEN_OFF);
            // Turn on screen
            mHandler.sendMessage(createScreenOnOffMessage(true));
            mTestLooper.dispatchAll();
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
            // Set as foreground app
            mHandler.sendMessage(createImportanceMessage(true));
            mTestLooper.dispatchAll();
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
        }
    }

    @Test
    public void testScanTimeoutResetForNewScan() {
        mSetFlagsRule.enableFlags(Flags.FLAG_SCAN_TIMEOUT_RESET);

        mTestLooper.stopAutoDispatchAndIgnoreExceptions();
        // Set filtered scan flag
        final boolean isFiltered = false;
        when(mAdapterService.getScanTimeoutMillis()).thenReturn((long) DELAY_SCAN_TIMEOUT_MS);
        // Turn on screen
        mHandler.sendMessage(createScreenOnOffMessage(true));
        mTestLooper.dispatchAll();
        // Create scan client
        ScanClient client = createScanClient(0, isFiltered, SCAN_MODE_LOW_POWER);

        // Put a timeout message in the queue to emulate the scan being started already
        Message timeoutMessage = mHandler.obtainMessage(ScanManager.MSG_SCAN_TIMEOUT, client);
        mHandler.sendMessageDelayed(timeoutMessage, DELAY_SCAN_TIMEOUT_MS / 2);
        mHandler.sendMessage(createStartStopScanMessage(true, client));

        // Dispatching all messages only runs start scan
        assertThat(mTestLooper.dispatchAll()).isEqualTo(1);
        mTestLooper.moveTimeForward(DELAY_SCAN_TIMEOUT_MS / 2);
        assertThat(mHandler.hasMessages(ScanManager.MSG_SCAN_TIMEOUT, client)).isTrue();

        // After restarting the scan, we can check that the initial timeout message is not triggered
        assertThat(mTestLooper.dispatchAll()).isEqualTo(0);

        // After timeout, the next message that is run should be a timeout message
        mTestLooper.moveTimeForward(DELAY_SCAN_TIMEOUT_MS / 2 + 1);
        Message nextMessage = mTestLooper.nextMessage();
        assertThat(nextMessage.what).isEqualTo(ScanManager.MSG_SCAN_TIMEOUT);
        assertThat(nextMessage.obj).isEqualTo(client);
    }

    @Test
    public void testSwitchForeBackgroundUnfilteredScan() {
        // Set filtered scan flag
        final boolean isFiltered = false;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_LOW_POWER);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
            // Set as background app
            sendMessageWaitForProcessed(createImportanceMessage(false));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
            // Set as foreground app
            sendMessageWaitForProcessed(createImportanceMessage(true));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
        }
    }

    @Test
    public void testSwitchForeBackgroundFilteredScan() {
        // Set filtered scan flag
        final boolean isFiltered = true;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_LOW_POWER);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
            // Set as background app
            sendMessageWaitForProcessed(createImportanceMessage(false));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
            // Set as foreground app
            sendMessageWaitForProcessed(createImportanceMessage(true));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
        }
    }

    @Test
    public void testUpgradeStartScan() {
        // Set filtered scan flag
        final boolean isFiltered = true;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_BALANCED);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_LOW_LATENCY);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_LOW_LATENCY);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_LOW_LATENCY);
        // Set scan upgrade duration through Mock
        when(mAdapterService.getScanUpgradeDurationMillis()).
                thenReturn((long) DELAY_SCAN_UPGRADE_DURATION_MS);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            // Set as foreground app
            sendMessageWaitForProcessed(createImportanceMessage(true));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
            // Wait for upgrade duration
            testSleep(DELAY_SCAN_UPGRADE_DURATION_MS + DELAY_ASYNC_MS);
            TestUtils.waitForLooperToFinishScheduledTask(mHandler.getLooper());
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
        }
    }

    @Test
    public void testUpDowngradeStartScanForConcurrency() {
        // Set filtered scan flag
        final boolean isFiltered = true;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_BALANCED);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_BALANCED);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_BALANCED);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_BALANCED);
        // Set scan upgrade duration through Mock
        when(mAdapterService.getScanUpgradeDurationMillis()).
                thenReturn((long) DELAY_SCAN_UPGRADE_DURATION_MS);
        // Set scan downgrade duration through Mock
        when(mAdapterService.getScanDowngradeDurationMillis()).
                thenReturn((long) DELAY_SCAN_DOWNGRADE_DURATION_MS);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            // Set as foreground app
            sendMessageWaitForProcessed(createImportanceMessage(true));
            // Set connecting state
            sendMessageWaitForProcessed(createConnectingMessage(true));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
            // Wait for upgrade and downgrade duration
            int max_duration = DELAY_SCAN_UPGRADE_DURATION_MS > DELAY_SCAN_DOWNGRADE_DURATION_MS ?
                    DELAY_SCAN_UPGRADE_DURATION_MS : DELAY_SCAN_DOWNGRADE_DURATION_MS;
            testSleep(max_duration + DELAY_ASYNC_MS);
            TestUtils.waitForLooperToFinishScheduledTask(mHandler.getLooper());
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
        }
    }

    @Test
    public void testDowngradeDuringScanForConcurrency() {
        // Set filtered scan flag
        final boolean isFiltered = true;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_BALANCED);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_BALANCED);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_AMBIENT_DISCOVERY);
        // Set scan downgrade duration through Mock
        when(mAdapterService.getScanDowngradeDurationMillis()).
                thenReturn((long) DELAY_SCAN_DOWNGRADE_DURATION_MS);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            // Set as foreground app
            sendMessageWaitForProcessed(createImportanceMessage(true));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
            // Set connecting state
            sendMessageWaitForProcessed(createConnectingMessage(true));
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
            // Wait for downgrade duration
            testSleep(DELAY_SCAN_DOWNGRADE_DURATION_MS + DELAY_ASYNC_MS);
            TestUtils.waitForLooperToFinishScheduledTask(mHandler.getLooper());
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
        }
    }

    @Test
    public void testDowngradeDuringScanForConcurrencyScreenOff() {
        mTestLooper.stopAutoDispatchAndIgnoreExceptions();
        // Set filtered scan flag
        final boolean isFiltered = true;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_SCREEN_OFF);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_SCREEN_OFF_BALANCED);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_LOW_LATENCY);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_SCREEN_OFF_BALANCED);
        // Set scan downgrade duration through Mock
        when(mAdapterService.getScanDowngradeDurationMillis()).
                thenReturn((long) DELAY_SCAN_DOWNGRADE_DURATION_MS);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn on screen
            mHandler.sendMessage(createScreenOnOffMessage(true));
            // Set as foreground app
            mHandler.sendMessage(createImportanceMessage(true));
            mTestLooper.dispatchAll();
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            mHandler.sendMessage(createStartStopScanMessage(true, client));
            mTestLooper.dispatchAll();
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
            // Set connecting state
            mHandler.sendMessage(createConnectingMessage(true));
            // Turn off screen
            mHandler.sendMessage(createScreenOnOffMessage(false));
            // Dispatching all messages doesn't run MSG_STOP_CONNECTING which is sent with delay
            // during handling MSG_START_CONNECTING
            assertThat(mTestLooper.dispatchAll()).isEqualTo(2);
            // Move time forward so that MSG_STOP_CONNECTING can be dispatched
            mTestLooper.moveTimeForward(DELAY_SCAN_DOWNGRADE_DURATION_MS + 1);
            // We can check that MSG_STOP_CONNECTING is in the message queue
            assertThat(mHandler.hasMessages(ScanManager.MSG_STOP_CONNECTING)).isTrue();
            mTestLooper.dispatchAll();
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
        }
    }

    @Test
    public void testDowngradeDuringScanForConcurrencyBackground() {
        // Set filtered scan flag
        final boolean isFiltered = true;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_LOW_POWER);
        // Set scan downgrade duration through Mock
        when(mAdapterService.getScanDowngradeDurationMillis()).
                thenReturn((long) DELAY_SCAN_DOWNGRADE_DURATION_MS);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            // Set as foreground app
            sendMessageWaitForProcessed(createImportanceMessage(true));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
            // Set connecting state
            sendMessageWaitForProcessed(createConnectingMessage(true));
            // Set as background app
            sendMessageWaitForProcessed(createImportanceMessage(false));
            // Wait for downgrade duration
            testSleep(DELAY_SCAN_DOWNGRADE_DURATION_MS + DELAY_ASYNC_MS);
            TestUtils.waitForLooperToFinishScheduledTask(mHandler.getLooper());
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(client.settings.getScanMode()).isEqualTo(expectedScanMode);
        }
    }

    @Test
    public void testStartUnfilteredBatchScan() {
        // Set filtered and batch scan flag
        final boolean isFiltered = false;
        final boolean isBatch = true;
        final boolean isAutoBatch = false;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_BALANCED);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_LOW_LATENCY);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_LOW_LATENCY);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn off screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(false));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode, isBatch, isAutoBatch);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getBatchScanQueue().contains(client)).isFalse();
            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getBatchScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getBatchScanParams().scanMode).isEqualTo(expectedScanMode);
        }
    }

    @Test
    public void testStartFilteredBatchScan() {
        // Set filtered and batch scan flag
        final boolean isFiltered = true;
        final boolean isBatch = true;
        final boolean isAutoBatch = false;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_LOW_POWER);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_BALANCED);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_LOW_LATENCY);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_LOW_LATENCY);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn off screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(false));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode, isBatch, isAutoBatch);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getBatchScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getBatchScanParams().scanMode).isEqualTo(expectedScanMode);
            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getBatchScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getBatchScanParams().scanMode).isEqualTo(expectedScanMode);
        }
    }

    @Test
    public void testUnfilteredAutoBatchScan() {
        // Set filtered and batch scan flag
        final boolean isFiltered = false;
        final boolean isBatch = true;
        final boolean isAutoBatch = true;
        // Set report delay for auto batch scan callback type
        mScanReportDelay = ScanSettings.AUTO_BATCH_MIN_REPORT_DELAY_MILLIS;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_SCREEN_OFF);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_SCREEN_OFF);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_SCREEN_OFF);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_SCREEN_OFF);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn off screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(false));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode, isBatch, isAutoBatch);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getBatchScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getBatchScanParams()).isNull();
            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getBatchScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getBatchScanParams()).isNull();
            // Turn off screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(false));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getBatchScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getBatchScanParams()).isNull();
        }
    }

    @Test
    public void testFilteredAutoBatchScan() {
        // Set filtered and batch scan flag
        final boolean isFiltered = true;
        final boolean isBatch = true;
        final boolean isAutoBatch = true;
        // Set report delay for auto batch scan callback type
        mScanReportDelay = ScanSettings.AUTO_BATCH_MIN_REPORT_DELAY_MILLIS;
        // Set scan mode map {original scan mode (ScanMode) : expected scan mode (expectedScanMode)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_LOW_POWER, SCAN_MODE_SCREEN_OFF);
        scanModeMap.put(SCAN_MODE_BALANCED, SCAN_MODE_SCREEN_OFF);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, SCAN_MODE_SCREEN_OFF);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, SCAN_MODE_SCREEN_OFF);

        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            int expectedScanMode = scanModeMap.get(ScanMode);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " expectedScanMode: " + String.valueOf(expectedScanMode));

            // Turn off screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(false));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode, isBatch, isAutoBatch);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getBatchScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getBatchScanParams().scanMode).isEqualTo(expectedScanMode);
            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(client.settings.getScanMode()).isEqualTo(ScanMode);
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getBatchScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getBatchScanParams()).isNull();
            // Turn off screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(false));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getBatchScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getBatchScanParams().scanMode).isEqualTo(expectedScanMode);
        }
    }

    @Test
    public void testLocationAndScreenOnOffResumeUnfilteredScan() {
        // Set filtered scan flag
        final boolean isFiltered = false;
        // Set scan mode array
        int[] scanModeArr = {SCAN_MODE_LOW_POWER,
                SCAN_MODE_BALANCED,
                SCAN_MODE_LOW_LATENCY,
                SCAN_MODE_AMBIENT_DISCOVERY};

        for (int i = 0; i < scanModeArr.length; i++) {
            int ScanMode = scanModeArr[i];
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode));
            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
            // Turn off location
            doReturn(false).when(mLocationManager).isLocationEnabled();
            sendMessageWaitForProcessed(createLocationOnOffMessage(false));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isTrue();
            // Turn off screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(false));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isTrue();
            // Turn on screen
            sendMessageWaitForProcessed(createScreenOnOffMessage(true));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isFalse();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isTrue();
            // Turn on location
            doReturn(true).when(mLocationManager).isLocationEnabled();
            sendMessageWaitForProcessed(createLocationOnOffMessage(true));
            assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
            assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
        }
    }

    @Test
    public void testMetricsScanRadioDurationScreenOn() {
        // Set filtered scan flag
        final boolean isFiltered = true;
        // Turn on screen
        sendMessageWaitForProcessed(createScreenOnOffMessage(true));
        Mockito.clearInvocations(mMetricsLogger);
        // Create scan client
        ScanClient client = createScanClient(0, isFiltered, SCAN_MODE_LOW_POWER);
        // Start scan
        sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
        verify(mMetricsLogger, never())
                .cacheCount(eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR), anyLong());
        verify(mMetricsLogger, never())
                .cacheCount(
                        eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_ON),
                        anyLong());
        verify(mMetricsLogger, never()).cacheCount(
                eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_OFF), anyLong());
        Mockito.clearInvocations(mMetricsLogger);
        testSleep(50);
        // Stop scan
        sendMessageWaitForProcessed(createStartStopScanMessage(false, client));
        verify(mMetricsLogger, times(1))
                .cacheCount(eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR), anyLong());
        verify(mMetricsLogger, times(1))
                .cacheCount(
                        eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_ON),
                        anyLong());
        verify(mMetricsLogger, never()).cacheCount(
                eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_OFF), anyLong());
        Mockito.clearInvocations(mMetricsLogger);
    }

    @Test
    public void testMetricsScanRadioDurationScreenOnOff() {
        // Set filtered scan flag
        final boolean isFiltered = true;
        // Turn on screen
        sendMessageWaitForProcessed(createScreenOnOffMessage(true));
        Mockito.clearInvocations(mMetricsLogger);
        // Create scan client
        ScanClient client = createScanClient(0, isFiltered, SCAN_MODE_LOW_POWER);
        // Start scan
        sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
        verify(mMetricsLogger, never())
                .cacheCount(eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR), anyLong());
        verify(mMetricsLogger, never())
                .cacheCount(
                        eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_ON),
                        anyLong());
        verify(mMetricsLogger, never())
                .cacheCount(
                        eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_OFF),
                        anyLong());
        Mockito.clearInvocations(mMetricsLogger);
        testSleep(50);
        // Turn off screen
        sendMessageWaitForProcessed(createScreenOnOffMessage(false));
        verify(mMetricsLogger, times(1))
                .cacheCount(eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR), anyLong());
        verify(mMetricsLogger, times(1))
                .cacheCount(
                        eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_ON),
                        anyLong());
        verify(mMetricsLogger, never())
                .cacheCount(
                        eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_OFF),
                        anyLong());
        Mockito.clearInvocations(mMetricsLogger);
        testSleep(50);
        // Turn on screen
        sendMessageWaitForProcessed(createScreenOnOffMessage(true));
        verify(mMetricsLogger, atMost(3))
                .cacheCount(eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR), anyLong());
        verify(mMetricsLogger, atMost(2))
                .cacheCount(
                        eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_ON),
                        anyLong());
        verify(mMetricsLogger, atMost(2))
                .cacheCount(
                        eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_OFF),
                        anyLong());
        Mockito.clearInvocations(mMetricsLogger);
        testSleep(50);
        // Stop scan
        sendMessageWaitForProcessed(createStartStopScanMessage(false, client));
        verify(mMetricsLogger, times(1))
                .cacheCount(eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR), anyLong());
        verify(mMetricsLogger, times(1))
                .cacheCount(
                        eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_ON),
                        anyLong());
        verify(mMetricsLogger, never()).cacheCount(
                eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_OFF), anyLong());
        Mockito.clearInvocations(mMetricsLogger);
    }

    @Test
    public void testMetricsScanRadioDurationMultiScan() {
        // Set filtered scan flag
        final boolean isFiltered = true;
        // Turn on screen
        sendMessageWaitForProcessed(createScreenOnOffMessage(true));
        Mockito.clearInvocations(mMetricsLogger);
        // Create scan clients with different duty cycles
        ScanClient client = createScanClient(0, isFiltered, SCAN_MODE_LOW_POWER);
        ScanClient client2 = createScanClient(1, isFiltered, SCAN_MODE_BALANCED);
        // Start scan with lower duty cycle
        sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
        verify(mMetricsLogger, never())
                .cacheCount(eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR), anyLong());
        verify(mMetricsLogger, never())
                .cacheCount(
                        eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_ON),
                        anyLong());
        verify(mMetricsLogger, never()).cacheCount(
                eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_OFF), anyLong());
        Mockito.clearInvocations(mMetricsLogger);
        testSleep(50);
        // Start scan with higher duty cycle
        sendMessageWaitForProcessed(createStartStopScanMessage(true, client2));
        verify(mMetricsLogger, times(1))
                .cacheCount(eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR), anyLong());
        verify(mMetricsLogger, times(1))
                .cacheCount(
                        eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_ON),
                        anyLong());
        verify(mMetricsLogger, never()).cacheCount(
                eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_OFF), anyLong());
        Mockito.clearInvocations(mMetricsLogger);
        testSleep(50);
        // Stop scan with lower duty cycle
        sendMessageWaitForProcessed(createStartStopScanMessage(false, client));
        verify(mMetricsLogger, never()).cacheCount(anyInt(), anyLong());
        Mockito.clearInvocations(mMetricsLogger);
        // Stop scan with higher duty cycle
        sendMessageWaitForProcessed(createStartStopScanMessage(false, client2));
        verify(mMetricsLogger, times(1))
                .cacheCount(eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR), anyLong());
        verify(mMetricsLogger, times(1))
                .cacheCount(
                        eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_ON),
                        anyLong());
        verify(mMetricsLogger, never()).cacheCount(
                eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR_SCREEN_OFF), anyLong());
        Mockito.clearInvocations(mMetricsLogger);
    }

    @Test
    public void testMetricsScanRadioWeightedDuration() {
        // Set filtered scan flag
        final boolean isFiltered = true;
        final long scanTestDuration = 100;
        // Set scan mode map {scan mode (ScanMode) : scan weight (ScanWeight)}
        SparseIntArray scanModeMap = new SparseIntArray();
        scanModeMap.put(SCAN_MODE_SCREEN_OFF, AppScanStats.SCREEN_OFF_LOW_POWER_WEIGHT);
        scanModeMap.put(SCAN_MODE_LOW_POWER, AppScanStats.LOW_POWER_WEIGHT);
        scanModeMap.put(SCAN_MODE_BALANCED, AppScanStats.BALANCED_WEIGHT);
        scanModeMap.put(SCAN_MODE_LOW_LATENCY, AppScanStats.LOW_LATENCY_WEIGHT);
        scanModeMap.put(SCAN_MODE_AMBIENT_DISCOVERY, AppScanStats.AMBIENT_DISCOVERY_WEIGHT);

        // Turn on screen
        sendMessageWaitForProcessed(createScreenOnOffMessage(true));
        Mockito.clearInvocations(mMetricsLogger);
        for (int i = 0; i < scanModeMap.size(); i++) {
            int ScanMode = scanModeMap.keyAt(i);
            long weightedScanDuration = (long)(scanTestDuration * scanModeMap.get(ScanMode) * 0.01);
            Log.d(TAG, "ScanMode: " + String.valueOf(ScanMode)
                    + " weightedScanDuration: " + String.valueOf(weightedScanDuration));

            // Create scan client
            ScanClient client = createScanClient(i, isFiltered, ScanMode);
            // Start scan
            sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
            Mockito.clearInvocations(mMetricsLogger);
            // Wait for scan test duration
            testSleep(scanTestDuration);
            // Stop scan
            sendMessageWaitForProcessed(createStartStopScanMessage(false, client));
            verify(mMetricsLogger, times(1))
                    .cacheCount(
                            eq(BluetoothProtoEnums.LE_SCAN_RADIO_DURATION_REGULAR),
                            mScanDurationCaptor.capture());
            long capturedDuration = mScanDurationCaptor.getValue();
            Log.d(TAG, "capturedDuration: " + String.valueOf(capturedDuration));
            assertThat(capturedDuration).isAtLeast(weightedScanDuration);
            assertThat(capturedDuration).isAtMost(weightedScanDuration + DELAY_ASYNC_MS);
            Mockito.clearInvocations(mMetricsLogger);
        }
    }

    @Test
    public void testMetricsScreenOnOff() {
        // Turn off screen initially
        sendMessageWaitForProcessed(createScreenOnOffMessage(false));
        Mockito.clearInvocations(mMetricsLogger);
        // Turn on screen
        sendMessageWaitForProcessed(createScreenOnOffMessage(true));
        verify(mMetricsLogger, never()).cacheCount(
                eq(BluetoothProtoEnums.SCREEN_OFF_EVENT), anyLong());
        verify(mMetricsLogger, times(1)).cacheCount(
                eq(BluetoothProtoEnums.SCREEN_ON_EVENT), anyLong());
        Mockito.clearInvocations(mMetricsLogger);
        // Turn off screen
        sendMessageWaitForProcessed(createScreenOnOffMessage(false));
        verify(mMetricsLogger, never()).cacheCount(
                eq(BluetoothProtoEnums.SCREEN_ON_EVENT), anyLong());
        verify(mMetricsLogger, times(1)).cacheCount(
                eq(BluetoothProtoEnums.SCREEN_OFF_EVENT), anyLong());
        Mockito.clearInvocations(mMetricsLogger);
    }

    @Test
    public void testDowngradeWithNonNullClientAppScanStats() {
        // Set filtered scan flag
        final boolean isFiltered = true;
        // Set scan downgrade duration through Mock
        when(mAdapterService.getScanDowngradeDurationMillis())
                .thenReturn((long) DELAY_SCAN_DOWNGRADE_DURATION_MS);

        // Turn off screen
        sendMessageWaitForProcessed(createScreenOnOffMessage(false));
        // Create scan client
        ScanClient client = createScanClient(0, isFiltered, SCAN_MODE_LOW_LATENCY);
        // Start Scan
        sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
        assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
        assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
        assertThat(client.settings.getScanMode()).isEqualTo(SCAN_MODE_LOW_LATENCY);
        // Set connecting state
        sendMessageWaitForProcessed(createConnectingMessage(true));
        // SCAN_MODE_LOW_LATENCY is now downgraded to SCAN_MODE_BALANCED
        assertThat(client.settings.getScanMode()).isEqualTo(SCAN_MODE_BALANCED);
    }

    @Test
    public void testDowngradeWithNullClientAppScanStats() {
        // Set filtered scan flag
        final boolean isFiltered = true;
        // Set scan downgrade duration through Mock
        when(mAdapterService.getScanDowngradeDurationMillis())
                .thenReturn((long) DELAY_SCAN_DOWNGRADE_DURATION_MS);

        // Turn off screen
        sendMessageWaitForProcessed(createScreenOnOffMessage(false));
        // Create scan client
        ScanClient client = createScanClient(0, isFiltered, SCAN_MODE_LOW_LATENCY);
        // Start Scan
        sendMessageWaitForProcessed(createStartStopScanMessage(true, client));
        assertThat(mScanManager.getRegularScanQueue().contains(client)).isTrue();
        assertThat(mScanManager.getSuspendedScanQueue().contains(client)).isFalse();
        assertThat(client.settings.getScanMode()).isEqualTo(SCAN_MODE_LOW_LATENCY);
        // Set AppScanStats to null
        client.stats = null;
        // Set connecting state
        sendMessageWaitForProcessed(createConnectingMessage(true));
        // Since AppScanStats is null, no downgrade takes place for scan mode
        assertThat(client.settings.getScanMode()).isEqualTo(SCAN_MODE_LOW_LATENCY);
    }

    @Test
    public void profileConnectionStateChanged_sendStartConnectionMessage() {
        // Set scan downgrade duration through Mock
        when(mAdapterService.getScanDowngradeDurationMillis())
                .thenReturn((long) DELAY_SCAN_DOWNGRADE_DURATION_MS);
        assertThat(mScanManager.mIsConnecting).isFalse();

        mScanManager.handleBluetoothProfileConnectionStateChanged(
                BluetoothProfile.A2DP,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTING);

        // Wait for handleConnectingState to happen
        TestUtils.waitForLooperToFinishScheduledTask(mHandler.getLooper());
        assertThat(mScanManager.mIsConnecting).isTrue();
    }

    @Test
    public void multipleProfileConnectionStateChanged_updateCountersCorrectly()
            throws ExecutionException, InterruptedException {
        when(mAdapterService.getScanDowngradeDurationMillis())
                .thenReturn((long) DELAY_SCAN_DOWNGRADE_DURATION_MS);
        assertThat(mScanManager.mIsConnecting).isFalse();

        Thread t1 =
                new Thread(
                        () ->
                                mScanManager.handleBluetoothProfileConnectionStateChanged(
                                        BluetoothProfile.A2DP,
                                        BluetoothProfile.STATE_DISCONNECTED,
                                        BluetoothProfile.STATE_CONNECTING));
        Thread t2 =
                new Thread(
                        () ->
                                mScanManager.handleBluetoothProfileConnectionStateChanged(
                                        BluetoothProfile.HEADSET,
                                        BluetoothProfile.STATE_DISCONNECTED,
                                        BluetoothProfile.STATE_CONNECTING));

        // Connect 3 profiles concurrently.
        t1.start();
        t2.start();
        mScanManager.handleBluetoothProfileConnectionStateChanged(
                BluetoothProfile.HID_HOST,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTING);

        t1.join();
        t2.join();
        TestUtils.waitForLooperToFinishScheduledTask(mHandler.getLooper());
        assertThat(mScanManager.mProfilesConnecting).isEqualTo(3);
    }
}
