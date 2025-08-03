/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.bluetooth.cts;

import static org.junit.Assert.assertTrue;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test cases for Bluetooth LE scans.
 * <p>
 * To run the test, the device must be placed in an environment that has at least 3 beacons, all
 * placed less than 5 meters away from the DUT.
 * <p>
 * Run 'run cts --class android.bluetooth.cts.BluetoothLeScanTest' in cts-tradefed to run the test
 * cases.
 */
@RunWith(AndroidJUnit4.class)
public class BluetoothLeScanTest {

    private static final String TAG = "BluetoothLeScanTest";

    private static final int SCAN_DURATION_MILLIS = 10000;
    private static final int BATCH_SCAN_REPORT_DELAY_MILLIS = 20000;
    private static final int SCAN_STOP_TIMEOUT = 2000;
    private CountDownLatch mFlushBatchScanLatch;

    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mScanner;
    // Whether location is on before running the tests.
    private boolean mLocationOn;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(android.Manifest.permission.BLUETOOTH_CONNECT);
        BluetoothManager manager = (BluetoothManager) mContext.getSystemService(
                Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            assertTrue(BTAdapterUtils.enableAdapter(mBluetoothAdapter, mContext));
        }
        mScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mLocationOn = TestUtils.isLocationOn(mContext);
        if (!mLocationOn) {
            TestUtils.enableLocation(mContext);
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                "android.bluetooth.cts", android.Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @After
    public void tearDown() {
        if (!mLocationOn) {
            TestUtils.disableLocation(mContext);
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Basic test case for BLE scans. Checks BLE scan timestamp is within correct range.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void basicBleScan() {
        long scanStartMillis = SystemClock.elapsedRealtime();
        Collection<ScanResult> scanResults = scan();
        long scanEndMillis = SystemClock.elapsedRealtime();
        Log.d(TAG, "scan result size:" + scanResults.size());
        assertTrue("Scan results shouldn't be empty", !scanResults.isEmpty());
        verifyTimestamp(scanResults, scanStartMillis, scanEndMillis);
    }

    /**
     * Test of scan filters. Ensures only beacons matching certain type of scan filters were
     * reported.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void scanFilter() {
        List<ScanFilter> filters = new ArrayList<ScanFilter>();
        ScanFilter filter = createScanFilter();
        if (filter == null) {
            Log.d(TAG, "no appropriate filter can be set");
            return;
        }
        filters.add(filter);

        BleScanCallback filterLeScanCallback = new BleScanCallback();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(
                ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        mScanner.startScan(filters, settings, filterLeScanCallback);
        TestUtils.sleep(SCAN_DURATION_MILLIS);
        mScanner.stopScan(filterLeScanCallback);
        TestUtils.sleep(SCAN_STOP_TIMEOUT);
        Collection<ScanResult> scanResults = filterLeScanCallback.getScanResults();
        for (ScanResult result : scanResults) {
            assertTrue(filter.matches(result));
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void scanFromSourceWithoutFilters() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        android.Manifest.permission.BLUETOOTH_PRIVILEGED,
                        android.Manifest.permission.UPDATE_DEVICE_STATS
                );
        BleScanCallback filterLeScanCallback = new BleScanCallback();
        mScanner.startScanFromSource(null, filterLeScanCallback);
        TestUtils.sleep(SCAN_DURATION_MILLIS);
        mScanner.stopScan(filterLeScanCallback);
        TestUtils.sleep(SCAN_STOP_TIMEOUT);
        Collection<ScanResult> scanResults = filterLeScanCallback.getScanResults();
        Log.d(TAG, "scan result size " + scanResults.size());
        assertTrue("scan results should not be empty", !scanResults.isEmpty());

    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void scanFromSourceWithFilters() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        android.Manifest.permission.BLUETOOTH_PRIVILEGED,
                        android.Manifest.permission.UPDATE_DEVICE_STATS
                );
        BleScanCallback filterLeScanCallback = new BleScanCallback();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(
                ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        mScanner.startScanFromSource(null, settings, null, filterLeScanCallback);
        TestUtils.sleep(SCAN_DURATION_MILLIS);
        mScanner.stopScan(filterLeScanCallback);
        TestUtils.sleep(SCAN_STOP_TIMEOUT);
        Collection<ScanResult> scanResults = filterLeScanCallback.getScanResults();
        Log.d(TAG, "scan result size " + scanResults.size());
        assertTrue("scan results should not be empty", !scanResults.isEmpty());
    }


    // Create a scan filter based on the nearby beacon with highest signal strength.
    private ScanFilter createScanFilter() {
        // Get a list of nearby beacons.
        List<ScanResult> scanResults = new ArrayList<ScanResult>(scan());
        assertTrue("Scan results shouldn't be empty", !scanResults.isEmpty());
        // Find the beacon with strongest signal strength, which is the target device for filter
        // scan.
        Collections.sort(scanResults, new RssiComparator());
        ScanResult result = scanResults.get(0);
        ScanRecord record = result.getScanRecord();
        if (record == null) {
            return null;
        }
        Map<ParcelUuid, byte[]> serviceData = record.getServiceData();
        if (serviceData != null && !serviceData.isEmpty()) {
            ParcelUuid uuid = serviceData.keySet().iterator().next();
            return new ScanFilter.Builder().setServiceData(uuid, new byte[]{0},
                    new byte[]{0}).build();
        }
        SparseArray<byte[]> manufacturerSpecificData = record.getManufacturerSpecificData();
        if (manufacturerSpecificData != null && manufacturerSpecificData.size() > 0) {
            return new ScanFilter.Builder().setManufacturerData(manufacturerSpecificData.keyAt(0),
                    new byte[]{0}, new byte[]{0}).build();
        }
        List<ParcelUuid> serviceUuids = record.getServiceUuids();
        if (serviceUuids != null && !serviceUuids.isEmpty()) {
            return new ScanFilter.Builder().setServiceUuid(serviceUuids.get(0)).build();
        }
        return null;
    }

//    /**
//     * Test of opportunistic BLE scans.
//     * Temporarily disable this test because it is interfered by the GmsCore;
//     * it fails when it obtains results from GmsCore explicit scan.
//     * TODO(b/70865144): re-enable this test.
//     */
//    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
//    @MediumTest
//    @Test
//    public void opportunisticScan() {
//        ScanSettings opportunisticScanSettings = new ScanSettings.Builder()
//                .setScanMode(ScanSettings.SCAN_MODE_OPPORTUNISTIC)
//                .build();
//        BleScanCallback emptyScanCallback = new BleScanCallback();
//        assertTrue("opportunistic scan shouldn't have scan results",
//                emptyScanCallback.getScanResults().isEmpty());
//
//        // No scans are really started with opportunistic scans only.
//        mScanner.startScan(Collections.<ScanFilter>emptyList(), opportunisticScanSettings,
//                emptyScanCallback);
//        sleep(SCAN_DURATION_MILLIS);
//        Log.d(TAG, "result: " + emptyScanCallback.getScanResults());
//        assertTrue("opportunistic scan shouldn't have scan results",
//                emptyScanCallback.getScanResults().isEmpty());
//
//        BleScanCallback regularScanCallback = new BleScanCallback();
//        ScanSettings regularScanSettings = new ScanSettings.Builder()
//                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
//        List<ScanFilter> filters = new ArrayList<>();
//        ScanFilter filter = createScanFilter();
//        if (filter != null) {
//            filters.add(filter);
//        } else {
//            Log.d(TAG, "no appropriate filter can be set");
//        }
//        mScanner.startScan(filters, regularScanSettings, regularScanCallback);
//        sleep(SCAN_DURATION_MILLIS);
//        // With normal BLE scan client, opportunistic scan client will get scan results.
//        assertTrue("opportunistic scan results shouldn't be empty",
//                !emptyScanCallback.getScanResults().isEmpty());
//
//        // No more scan results for opportunistic scan clients once the normal BLE scan clients
//        // stops.
//        mScanner.stopScan(regularScanCallback);
//        // In case we got scan results before scan was completely stopped.
//        sleep(SCAN_STOP_TIMEOUT);
//        emptyScanCallback.clear();
//        sleep(SCAN_DURATION_MILLIS);
//        assertTrue("opportunistic scan shouldn't have scan results",
//                emptyScanCallback.getScanResults().isEmpty());
//    }

    /**
     * Test case for BLE Batch scan.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void batchScan() {
        Assume.assumeTrue(isBleBatchScanSupported());

        ScanSettings batchScanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(BATCH_SCAN_REPORT_DELAY_MILLIS).build();
        BleScanCallback batchScanCallback = new BleScanCallback();
        mScanner.startScan(Collections.emptyList(), batchScanSettings,
                batchScanCallback);
        TestUtils.sleep(SCAN_DURATION_MILLIS);
        mScanner.flushPendingScanResults(batchScanCallback);
        mFlushBatchScanLatch = new CountDownLatch(1);
        List<ScanResult> results = batchScanCallback.getBatchScanResults();
        try {
            mFlushBatchScanLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // Nothing to do.
            Log.e(TAG, "interrupted!");
        }
        assertTrue(!results.isEmpty());
        long scanEndMillis = SystemClock.elapsedRealtime();
        mScanner.stopScan(batchScanCallback);
        verifyTimestamp(results, 0, scanEndMillis);
    }

    /**
     * Test case for starting a scan with a PendingIntent.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void startScanPendingIntent_nullnull() throws Exception {
        Assume.assumeTrue(isBleBatchScanSupported());

        Intent broadcastIntent = new Intent();
        broadcastIntent.setClass(mContext, BluetoothScanReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, 1, broadcastIntent,
                PendingIntent.FLAG_IMMUTABLE);
        CountDownLatch latch = BluetoothScanReceiver.createCountDownLatch();
        mScanner.startScan(null, null, pi);
        boolean gotResults = latch.await(20, TimeUnit.SECONDS);
        mScanner.stopScan(pi);
        assertTrue("Scan results not received", gotResults);
    }

    /**
     * Test case for starting a scan with a PendingIntent.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void startScanPendingIntent() throws Exception {
        Assume.assumeTrue(isBleBatchScanSupported());

        ScanSettings batchScanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0).build();
        ScanFilter filter = createScanFilter();
        ArrayList<ScanFilter> filters = null;
        if (filter != null) {
            filters = new ArrayList<>();
            filters.add(filter);
        } else {
            Log.d(TAG, "Could not add a filter");
        }
        Intent broadcastIntent = new Intent();
        broadcastIntent.setClass(mContext, BluetoothScanReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, 1, broadcastIntent,
                PendingIntent.FLAG_IMMUTABLE);
        CountDownLatch latch = BluetoothScanReceiver.createCountDownLatch();
        mScanner.startScan(filters, batchScanSettings, pi);
        boolean gotResults = latch.await(20, TimeUnit.SECONDS);
        mScanner.stopScan(pi);
        assertTrue("Scan results not received", gotResults);
    }

    // Verify timestamp of all scan results are within [scanStartMillis, scanEndMillis].
    private void verifyTimestamp(Collection<ScanResult> results, long scanStartMillis,
            long scanEndMillis) {
        for (ScanResult result : results) {
            long timestampMillis = TimeUnit.NANOSECONDS.toMillis(result.getTimestampNanos());
            assertTrue("Invalid timestamp: " + timestampMillis + " should be >= " + scanStartMillis,
                    timestampMillis >= scanStartMillis);
            assertTrue("Invalid timestamp: " + timestampMillis + " should be <= " + scanEndMillis,
                    timestampMillis <= scanEndMillis);
        }
    }

    // Perform a BLE scan to get results of nearby BLE devices.
    private Set<ScanResult> scan() {
        BleScanCallback regularLeScanCallback = new BleScanCallback();
        mScanner.startScan(regularLeScanCallback);
        TestUtils.sleep(SCAN_DURATION_MILLIS);
        mScanner.stopScan(regularLeScanCallback);
        TestUtils.sleep(SCAN_STOP_TIMEOUT);
        return regularLeScanCallback.getScanResults();
    }

    // Returns whether offloaded scan batching is supported.
    private boolean isBleBatchScanSupported() {
        return mBluetoothAdapter.isOffloadedScanBatchingSupported();
    }

    // Helper class for BLE scan callback.
    private class BleScanCallback extends ScanCallback {
        private final Set<ScanResult> mResults = new HashSet<ScanResult>();
        private final List<ScanResult> mBatchScanResults = new ArrayList<ScanResult>();

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                mResults.add(result);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            // In case onBatchScanResults are called due to buffer full, we want to collect all
            // scan results.
            mBatchScanResults.addAll(results);
            if (mFlushBatchScanLatch != null) {
                mFlushBatchScanLatch.countDown();
            }
        }

        // Clear regular and batch scan results.
        public synchronized void clear() {
            mResults.clear();
            mBatchScanResults.clear();
        }

        // Return regular BLE scan results accumulated so far.
        synchronized Set<ScanResult> getScanResults() {
            return Collections.unmodifiableSet(mResults);
        }

        // Return batch scan results.
        synchronized List<ScanResult> getBatchScanResults() {
            return Collections.unmodifiableList(mBatchScanResults);
        }
    }

    private class RssiComparator implements Comparator<ScanResult> {

        @Override
        public int compare(ScanResult lhs, ScanResult rhs) {
            return rhs.getRssi() - lhs.getRssi();
        }

    }

}
