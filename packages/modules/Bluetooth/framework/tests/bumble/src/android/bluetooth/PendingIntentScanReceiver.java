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

package android.bluetooth;

import android.app.PendingIntent;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * PendingIntentScanReceiver is registered statically in the manifest file as a BroadcastReceiver
 * for the android.bluetooth.ACTION_SCAN_RESULT action. Tests can use nextScanResult() to get a
 * future that completes when scan results are next delivered.
 */
public class PendingIntentScanReceiver extends BroadcastReceiver {
    private static final String TAG = "PendingIntentScanReceiver";

    public static final String ACTION_SCAN_RESULT = "android.bluetooth.test.ACTION_SCAN_RESULT";

    private static Optional<CompletableFuture<List<ScanResult>>> sNextScanResultFuture =
            Optional.empty();

    /**
     * Constructs a new Intent associated with this class.
     *
     * @param context The context the to associate with the Intent.
     * @return The new Intent.
     */
    private static Intent newIntent(Context context) {
        Intent intent = new Intent();
        intent.setAction(PendingIntentScanReceiver.ACTION_SCAN_RESULT);
        intent.setClass(context, PendingIntentScanReceiver.class);
        return intent;
    }

    /**
     * Constructs a new PendingIntent associated with this class.
     *
     * @param context The context to associate the PendingIntent with.
     * @param requestCode The request code to uniquely identify this PendingIntent with.
     * @return
     */
    public static PendingIntent newBroadcastPendingIntent(Context context, int requestCode) {
        return PendingIntent.getBroadcast(
                context, requestCode, newIntent(context), PendingIntent.FLAG_CANCEL_CURRENT);
    }

    /**
     * Use this method for statically registered receivers.
     *
     * @return A future that will complete when the next scan result is received.
     */
    public static CompletableFuture<List<ScanResult>> nextScanResult()
            throws IllegalStateException {
        if (sNextScanResultFuture.isPresent()) {
            throw new IllegalStateException("scan result future already set");
        }
        sNextScanResultFuture = Optional.of(new CompletableFuture<List<ScanResult>>());
        return sNextScanResultFuture.get();
    }

    /** Clears the future waiting for the next static receiver scan result, if any. */
    public static void resetNextScanResultFuture() {
        sNextScanResultFuture = Optional.empty();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive() intent: " + intent);

        if (intent.getAction() != ACTION_SCAN_RESULT) {
            throw new RuntimeException();
        }

        int errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0);
        if (errorCode != 0) {
            Log.e(TAG, "onReceive() error: " + errorCode);
            throw new RuntimeException("onReceive() unexpected error: " + errorCode);
        }

        List<ScanResult> scanResults =
                intent.getParcelableExtra(
                        BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
                        new ArrayList<ScanResult>().getClass());

        if (sNextScanResultFuture.isPresent()) {
            sNextScanResultFuture.get().complete(scanResults);
            sNextScanResultFuture = Optional.empty();
        } else {
            throw new IllegalStateException("scan result received but no future set");
        }
    }
}
