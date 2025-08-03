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

package android.server.wm.shared;

import android.os.Bundle;
import android.os.Parcel;
import android.util.Pair;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

/**
 * Version of {@link android.os.ResultReceiver} that blocks waiting for result and doesn't have
 * serialization problems (as long as both ends have access to this class) since it doesn't work via
 * subclassing.
 */
public class BlockingResultReceiver extends android.os.ResultReceiver {
    private final CompletableFuture<Pair<Integer, Bundle>> mFuture = new CompletableFuture<>();

    public BlockingResultReceiver() {
        super(/* handler */ null);
    }

    @Override
    protected void onReceiveResult(int code, Bundle data) {
        mFuture.complete(new Pair<>(code, data));
    }

    public Bundle getData(long timeoutMs)
            throws InterruptedException, ExecutionException, TimeoutException {
        return mFuture.get(timeoutMs, TimeUnit.MILLISECONDS).second;
    }

    public int getCode(long timeoutMs)
            throws InterruptedException, ExecutionException, TimeoutException {
        return mFuture.get(timeoutMs, TimeUnit.MILLISECONDS).first;
    }
}
