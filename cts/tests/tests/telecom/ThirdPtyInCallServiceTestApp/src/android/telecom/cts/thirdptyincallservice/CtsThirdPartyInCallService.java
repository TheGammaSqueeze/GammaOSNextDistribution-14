/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.telecom.cts.thirdptyincallservice;

import android.content.Intent;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.cts.MockInCallService;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CtsThirdPartyInCallService extends MockInCallService {

    public static final String PACKAGE_NAME = "android.telecom.cts.thirdptyincallservice";
    private static final String TAG = CtsThirdPartyInCallService.class.getSimpleName();
    protected static final int TIMEOUT = 10000;

    private static CtsThirdPartyInCallService sInstance;
    private static CountDownLatch sServiceBoundLatch = new CountDownLatch(1);
    private static CountDownLatch sServiceUnboundlatch = new CountDownLatch(1);
    private CountDownLatch mExtrasChangedLatch = new CountDownLatch(1);
    private String mExpectedKey;
    private String mExpectedValue;

    private static Set<Call> sCalls = new HashSet<>();

    private Call.Callback mCallback = new Call.Callback() {
        @Override
        public void onDetailsChanged(Call call, Call.Details details) {
            Bundle extras = details.getExtras();
            if (extras != null && extras.containsKey(mExpectedKey)
                    && extras.getString(mExpectedKey).equals(mExpectedValue)) {
                mExtrasChangedLatch.countDown();
            }
        }
    };

    /**
     * Used to bind a call
     * @param intent
     * @return
     */
    @Override
    public android.os.IBinder onBind(Intent intent) {
        sInstance = this;
        long olderState = sServiceBoundLatch.getCount();
        sServiceBoundLatch.countDown();
        Log.d(TAG, "In Call Service on bind, " + olderState + " -> " + sServiceBoundLatch);
        mExtrasChangedLatch = new CountDownLatch(1);
        return super.onBind(intent);
    }

    /**
     * Used to unbind a call
     * @param intent
     * @return
     */
    @Override
    public boolean onUnbind(Intent intent) {
        long olderState = sServiceUnboundlatch.getCount();
        sServiceUnboundlatch.countDown();
        Log.d(TAG, "In Call Service unbind, " + olderState + " -> " + sServiceUnboundlatch);
        return super.onUnbind(intent);
    }

    @Override
    public void onCallAdded(Call call) {
        Log.i(TAG, "onCallAdded");
        super.onCallAdded(call);
        sCalls.add(call);
    }

    @Override
    public void onCallRemoved(Call call) {
        Log.i(TAG, "onCallRemoved");
        super.onCallRemoved(call);
        sCalls.add(call);
    }

    private static boolean checkLatch(CountDownLatch latch) {
        synchronized (sLock) {
            boolean success = false;
            try {
                success = latch.await(TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                success = false;
            }
            return success;
        }
    }

    public static void resetLatchForServiceBound(boolean bind) {
        synchronized (sLock) {
            if (bind) {
                // Set bind to true, reset unbind latch for unbinding.
                sServiceUnboundlatch = new CountDownLatch(1);
            } else {
                // Set bind to false, reset bind latch for binding.
                sServiceBoundLatch = new CountDownLatch(1);
            }
        }
    }

    public static boolean checkBindStatus(boolean bind) {
        Log.i(CtsThirdPartyInCallService.class.getSimpleName(),
                "checking latch status: service " + (bind ? "bound" : "not bound"));
        return bind ?
                checkLatch(sServiceBoundLatch)
                : checkLatch(sServiceUnboundlatch);
    }

    public static void resetCalls() {
        synchronized (sLock) {
            Log.i(TAG, "clearing all the third party calls.");
            for (Call c : sCalls) {
                c.disconnect();
            }
            sCalls.clear();
        }
    }

    public static int getLocalCallCount() {
        synchronized (sLock) {
            return sCalls.size();
        }
    }

    public static CtsThirdPartyInCallService getInstance() {
        return sInstance;
    }

    public void setExpectedExtra(String newKey, String newValue) {
        mExpectedKey = newKey;
        mExpectedValue = newValue;
    }

    public boolean waitForExtrasChanged() {
        try {
            mExtrasChangedLatch.await(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }

        // Make sure one of the calls had the expected extra
        return sCalls.stream().filter(c -> c.getDetails().getExtras() != null
                && c.getDetails().getExtras().containsKey(mExpectedKey)
                && c.getDetails().getExtras().getString(mExpectedKey).equals(mExpectedValue))
                .count() > 0;
    }
}
