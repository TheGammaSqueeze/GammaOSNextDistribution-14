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

import static org.junit.Assert.fail;

import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Test cases for {@link ScanCallback}.
 */
@RunWith(AndroidJUnit4.class)
public class ScanCallbackTest {

    // Scan types are used to determine which callback method is expected.
    private final static int SCAN_TYPE_SUCCESS = 0;
    private final static int SCAN_TYPE_FAIL = 1;
    private final static int SCAN_TYPE_BATCH = 2;

    private MockScanner mMockScanner = new MockScanner();
    private BleScanCallback mMockScanCallback = new BleScanCallback();

    @Before
    public void setUp() {
        Assume.assumeTrue(TestUtils.isBleSupported(
                InstrumentationRegistry.getInstrumentation().getContext()));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void scanSuccess() {
        mMockScanCallback.mScanType = SCAN_TYPE_SUCCESS;
        mMockScanner.startScan(new ScanSettings.Builder().build(), mMockScanCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void batchScans() {
        ScanSettings settings = new ScanSettings.Builder().setReportDelay(1000).build();
        mMockScanCallback.mScanType = SCAN_TYPE_BATCH;
        mMockScanner.startScan(settings, mMockScanCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void scanFail() {
        ScanSettings settings = new ScanSettings.Builder().build();
        // The first scan is success.
        mMockScanCallback.mScanType = SCAN_TYPE_SUCCESS;
        mMockScanner.startScan(settings, mMockScanCallback);
        // A second scan with the same callback should fail.
        mMockScanCallback.mScanType = SCAN_TYPE_FAIL;
        mMockScanner.startScan(settings, mMockScanCallback);
    }

    // A mock scanner for mocking BLE scanner functionalities.
    private static class MockScanner {
        private Set<ScanCallback> mCallbacks = new HashSet<>();

        void startScan(ScanSettings settings, ScanCallback callback) {
            synchronized (mCallbacks) {
                if (mCallbacks.contains(callback)) {
                    callback.onScanFailed(ScanCallback.SCAN_FAILED_ALREADY_STARTED);
                    return;
                }
                mCallbacks.add(callback);
                if (settings.getReportDelayMillis() == 0) {
                    callback.onScanResult(0, null);
                } else {
                    callback.onBatchScanResults(null);
                }
            }
        }
    }

    private static class BleScanCallback extends ScanCallback {
        int mScanType = SCAN_TYPE_SUCCESS;

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (mScanType != SCAN_TYPE_SUCCESS) {
                fail("scan should fail");
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (mScanType != SCAN_TYPE_BATCH) {
                fail("not a batch scan");
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            if (mScanType != SCAN_TYPE_FAIL) {
                fail("scan should not fail");
            }
        }

    }
}
