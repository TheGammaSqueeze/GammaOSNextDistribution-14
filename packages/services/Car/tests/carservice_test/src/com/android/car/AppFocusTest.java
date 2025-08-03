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
package com.android.car;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import android.car.Car;
import android.car.CarAppFocusManager;
import android.os.Process;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class AppFocusTest extends MockedCarTestBase {
    private static final String TAG = AppFocusTest.class.getSimpleName();
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 1000;

    @Test
    public void testFocusChange() throws Exception {
        CarAppFocusManager manager = (CarAppFocusManager) getCar().getCarManager(
                Car.APP_FOCUS_SERVICE);
        FocusChangedListener listener = new FocusChangedListener();
        FocusOwnershipCallback ownershipListener = new FocusOwnershipCallback();
        manager.addFocusListener(listener, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        manager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, ownershipListener);
        listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true);
        String[] myPackages = getContext().getPackageManager().getPackagesForUid(Process.myUid());
        assertThat(manager.getAppTypeOwner(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION))
                .containsExactlyElementsIn(myPackages);
        listener.resetWait();
        manager.abandonAppFocus(ownershipListener, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, false);
        assertThat(manager.getAppTypeOwner(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION)).isEmpty();
        manager.removeFocusListener(listener);
    }

    private static final class FocusChangedListener
            implements CarAppFocusManager.OnAppFocusChangedListener {
        private int mLastChangeAppType;
        private boolean mLastChangeAppActive;
        private final Semaphore mChangeWait = new Semaphore(0);

        private boolean waitForFocusChangeAndAssert(long timeoutMs, int expectedAppType,
                boolean expectedAppActive) throws Exception {
            if (!mChangeWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
            assertEquals(expectedAppType, mLastChangeAppType);
            assertEquals(expectedAppActive, mLastChangeAppActive);
            return true;
        }

        private void resetWait() {
            mChangeWait.drainPermits();
        }

        @Override
        public void onAppFocusChanged(int appType, boolean active) {
            Log.i(TAG, "onAppFocusChanged appType=" + appType + " active=" + active);
            mLastChangeAppType = appType;
            mLastChangeAppActive = active;
            mChangeWait.release();
        }
    }

    private static final class FocusOwnershipCallback
            implements CarAppFocusManager.OnAppFocusOwnershipCallback {
        private final Semaphore mLossEventWait = new Semaphore(0);

        private final Semaphore mGrantEventWait = new Semaphore(0);

        @Override
        public void onAppFocusOwnershipLost(int appType) {
            Log.i(TAG, "onAppFocusOwnershipLost " + appType);
            mLossEventWait.release();
        }

        @Override
        public void onAppFocusOwnershipGranted(int appType) {
            Log.i(TAG, "onAppFocusOwnershipGranted " + appType);
            mGrantEventWait.release();
        }
    }
}
