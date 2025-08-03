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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

/**
 * Test of {@link AdvertiseCallback}.
 */
@RunWith(AndroidJUnit4.class)
public class AdvertiseCallbackTest {

    private final static int ADVERTISE_TYPE_SUCCESS = 0;
    private final static int ADVERTISE_TYPE_FAIL = 1;

    private final MockAdvertiser mMockAdvertiser = new MockAdvertiser();
    private final BleAdvertiseCallback mAdvertiseCallback = new BleAdvertiseCallback();

    @Before
    public void setUp() {
        Assume.assumeTrue(TestUtils.isBleSupported(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void advertiseSuccess() {
        mAdvertiseCallback.mAdvertiseType = ADVERTISE_TYPE_SUCCESS;
        mMockAdvertiser.startAdvertise(mAdvertiseCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void advertiseFailure() {
        mAdvertiseCallback.mAdvertiseType = ADVERTISE_TYPE_SUCCESS;
        mMockAdvertiser.startAdvertise(mAdvertiseCallback);

        // Second advertise with the same callback should fail.
        mAdvertiseCallback.mAdvertiseType = ADVERTISE_TYPE_FAIL;
        mMockAdvertiser.startAdvertise(mAdvertiseCallback);
    }

    // A mock advertiser which emulate BluetoothLeAdvertiser behavior.
    private static class MockAdvertiser {
        private Set<AdvertiseCallback> mCallbacks = new HashSet<>();

        void startAdvertise(AdvertiseCallback callback) {
            synchronized (mCallbacks) {
                if (mCallbacks.contains(callback)) {
                    callback.onStartFailure(AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED);
                } else {
                    callback.onStartSuccess(null);
                    mCallbacks.add(callback);
                }
            }
        }
    }

    private static class BleAdvertiseCallback extends AdvertiseCallback {
        int mAdvertiseType = ADVERTISE_TYPE_SUCCESS;

        @Override
        public void onStartSuccess(AdvertiseSettings settings) {
            if (mAdvertiseType == ADVERTISE_TYPE_FAIL) {
                fail("advertise should fail");
            }
        }

        @Override
        public void onStartFailure(int error) {
            if (mAdvertiseType == ADVERTISE_TYPE_SUCCESS) {
                assertEquals(AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED, error);
            }
        }
    }
}
