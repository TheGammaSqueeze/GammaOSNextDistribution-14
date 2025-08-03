/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.app.stubs.shared;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class FutureServiceConnection implements ServiceConnection {
    private static final String TAG = "FutureServiceConnection";

    private volatile CompletableFuture<IBinder> mFuture = new CompletableFuture<>();

    public IBinder get(long timeoutMs) throws Exception {
        return mFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mFuture.complete(service);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.w(TAG, name.flattenToShortString() + " disconnected");
        mFuture = new CompletableFuture<>();
    }
}
