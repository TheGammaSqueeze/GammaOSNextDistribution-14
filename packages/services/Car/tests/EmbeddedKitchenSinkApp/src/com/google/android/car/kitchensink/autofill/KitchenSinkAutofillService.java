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
package com.google.android.car.kitchensink.autofill;

import android.os.CancellationSignal;
import android.os.Process;
import android.service.autofill.AutofillService;
import android.service.autofill.FillCallback;
import android.service.autofill.FillRequest;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;

public final class KitchenSinkAutofillService extends AutofillService {

    private static final String TAG = KitchenSinkAutofillService.class.getSimpleName();

    private final AtomicInteger mNumberFillRequests = new AtomicInteger();
    private final AtomicInteger mNumberSaveRequests = new AtomicInteger();
    private final int mUserId = Process.myUserHandle().getIdentifier();

    @Override
    public void onConnected() {
        Log.d(TAG, "onConnected() for user " + mUserId);
        super.onConnected();
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "onDisconnected() for user " + mUserId);
        super.onDisconnected();
    }

    @Override
    public void onFillRequest(FillRequest request, CancellationSignal cancellationSignal,
            FillCallback callback) {
        int callNumber = mNumberFillRequests.incrementAndGet();
        Log.d(TAG, "onFillRequest#" + callNumber + " for user " + mUserId + ": " + request);
    }

    @Override
    public void onSaveRequest(SaveRequest request, SaveCallback callback) {
        int callNumber = mNumberSaveRequests.incrementAndGet();
        Log.d(TAG, "onSaveRequest#" + callNumber + " for user " + mUserId + ": " + request);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        boolean quiet = false;
        if (args != null && args.length > 0) {
            switch (args[0]) {
                case "-q":
                    quiet = true;
                    break;
            }
        }

        pw.printf("user id: %d\n", mUserId);
        pw.printf("# fill requests: %d\n", mNumberFillRequests.get());
        pw.printf("# save requests: %d\n", mNumberSaveRequests.get());

        if (quiet) {
            Log.d(TAG, "dump() calling with -q, skipping super's");
            return;
        }

        pw.println();
        super.dump(fd, pw, args);
    }
}
