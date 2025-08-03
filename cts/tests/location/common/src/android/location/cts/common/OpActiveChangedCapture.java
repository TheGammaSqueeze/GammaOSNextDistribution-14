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

package android.location.cts.common;

import android.app.AppOpsManager;
import android.os.Looper;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class OpActiveChangedCapture implements AppOpsManager.OnOpActiveChangedListener,
        AutoCloseable {

    private final AppOpsManager mAppOps;
    private final String mOp;
    private final LinkedBlockingQueue<Boolean> mActives;

    public OpActiveChangedCapture(AppOpsManager appOps, String op) {
        mAppOps = appOps;
        mOp = op;
        mActives = new LinkedBlockingQueue<>();
    }

    public Boolean getNextActive(long timeoutMs) throws InterruptedException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new AssertionError("getNextActive() called from main thread");
        }

        return mActives.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        mAppOps.stopWatchingActive(this);
    }

    @Override
    public void onOpActiveChanged(String op, int uid, String packageName, boolean active) {
        if (op.equals(mOp)) {
            mActives.add(active);
        }
    }
}
