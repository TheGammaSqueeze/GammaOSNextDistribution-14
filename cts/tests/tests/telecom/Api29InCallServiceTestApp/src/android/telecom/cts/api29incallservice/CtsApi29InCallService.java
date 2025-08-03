/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telecom.cts.api29incallservice;

import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.cts.MockInCallService;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CtsApi29InCallService extends MockInCallService {

    public static final String PACKAGE_NAME = CtsApi29InCallService.class.getPackage().getName();
    private static final String TAG = CtsApi29InCallService.class.getSimpleName();
    protected static final int TIMEOUT = 10000;

    static Set<Call> sCalls = new HashSet<>();
    static int sHistoricalCallCount = 0;
    static boolean sShouldReturnNullBinding = false;
    static CompletableFuture<Boolean> sBindRequestFuture = new CompletableFuture<>();

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        if (!sCalls.contains(call)) {
            sHistoricalCallCount++;
        }
        sCalls.add(call);
        Log.i(TAG, "onCallAdded, size=" + sCalls.size());
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        sCalls.remove(call);
        Log.i(TAG, "onCallRemoved, size=" + sCalls.size());
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind future=" + sBindRequestFuture);
        sBindRequestFuture.complete(true);
        // Sets mIsServiceBound
        IBinder result = super.onBind(intent);
        if (!sShouldReturnNullBinding) {
            return result;
        }
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        reset();
        return super.onUnbind(intent);
    }

    public static boolean waitForBindRequest() {
        try {
            if (isServiceBound()) return true;
            return sBindRequestFuture.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            Log.e(TAG, "Waited too long for bind request. future=" + sBindRequestFuture);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static int getLocalCallCount() {
        synchronized (sLock) {
            return sCalls.size();
        }
    }

    static void reset() {
        sCalls.clear();
        sHistoricalCallCount = 0;
        sShouldReturnNullBinding = false;
        sBindRequestFuture = new CompletableFuture<>();
    }
}
