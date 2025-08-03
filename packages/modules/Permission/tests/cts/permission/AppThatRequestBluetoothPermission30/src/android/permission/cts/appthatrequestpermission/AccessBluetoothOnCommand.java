/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.permission.cts.appthatrequestpermission;

import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.AttributionSource;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextParams;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AccessBluetoothOnCommand extends ContentProvider {
    private static final String TAG = "AccessBluetoothOnCommand";
    private static final String DISAVOWAL_APP_PKG = "android.permission.cts.appneverforlocation";

    private enum Result {
        UNKNOWN, EXCEPTION, EMPTY, FILTERED, FULL
    }

    @Override
    public Bundle call(String authority, String method, String arg, Bundle extras) {
        final Bundle res = new Bundle();

        BluetoothLeScanner scanner = null;
        ScanCallback scanCallback = null;

        try {
            Context context = ("PROXY".equals(arg)) ? createProxyingContext() : getContext();
            scanner = context.getSystemService(BluetoothManager.class)
                    .getAdapter().getBluetoothLeScanner();

            final Set<String> observedScans = ConcurrentHashMap.newKeySet();
            final AtomicInteger observedErrorCode = new AtomicInteger(0);

            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    Log.v(TAG, "onScanResult() - result = " + result);
                    observedScans.add(Base64.encodeToString(result.getScanRecord().getBytes(), 0));
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    for (ScanResult result : results) {
                        onScanResult(0, result);
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.v(TAG, "onScanFailed() - errorCode = " + errorCode);
                    observedErrorCode.set(errorCode);
                }
            };

            scanner.startScan(scanCallback);

            // Wait a few seconds to figure out what we actually observed
            SystemClock.sleep(3000);

            if (observedErrorCode.get() > 0) {
                throw new RuntimeException("Scan returned error code: " + observedErrorCode.get());
            }

            switch (observedScans.size()) {
                case 0:
                    res.putInt(Intent.EXTRA_INDEX, Result.EMPTY.ordinal());
                    break;
                case 1:
                    res.putInt(Intent.EXTRA_INDEX, Result.FILTERED.ordinal());
                    break;
                case 5:
                    res.putInt(Intent.EXTRA_INDEX, Result.FULL.ordinal());
                    break;
                default:
                    res.putInt(Intent.EXTRA_INDEX, Result.UNKNOWN.ordinal());
                    break;
            }
        } catch (Throwable t) {
            Log.v(TAG, "Failed to scan", t);
            res.putInt(Intent.EXTRA_INDEX, Result.EXCEPTION.ordinal());
        } finally {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception e) {
            }
        }
        return res;
    }

    private Context createProxyingContext() throws PackageManager.NameNotFoundException {
        int disavowingAppUid =
                getContext().getPackageManager().getPackageUid(DISAVOWAL_APP_PKG, 0);
        AttributionSource attrib = new AttributionSource.Builder(disavowingAppUid)
                .setPackageName(DISAVOWAL_APP_PKG)
                .build();
        return getContext().createContext(
                new ContextParams.Builder().setNextAttributionSource(attrib).build());
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
