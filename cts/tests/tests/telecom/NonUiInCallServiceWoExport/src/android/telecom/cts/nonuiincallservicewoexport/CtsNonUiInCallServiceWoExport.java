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

package android.telecom.cts.nonuiincallservicewoexport;

import android.content.Intent;
import android.os.IBinder;
import android.telecom.cts.MockInCallService;
import android.util.Log;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CtsNonUiInCallServiceWoExport extends MockInCallService {

    protected static final int TIMEOUT = 10000;
    private static final String TAG =
            CtsNonUiInCallServiceWoExport.class.getSimpleName();
    public static final String PACKAGE_NAME =
            CtsNonUiInCallServiceWoExport.class.getPackage().getName();

    // vars
    static CompletableFuture<Boolean> sBindRequestFuture = new CompletableFuture<>();

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind future=" + sBindRequestFuture);
        sBindRequestFuture.complete(true);
        // Sets mIsServiceBound
        IBinder result = super.onBind(intent);
        return result;
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
            Log.e(TAG, "hit exception in waitForBindRequest" + e.toString());
            return false;
        }
    }

    static void reset() {
        sBindRequestFuture = new CompletableFuture<>();
    }
}
