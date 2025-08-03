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

package com.android.car.internal.test;

import android.app.Service;
import android.car.apitest.IStableAIDLTestBinder;
import android.car.apitest.IStableAIDLTestCallback;
import android.car.apitest.StableAIDLTestLargeParcelable;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import com.android.car.internal.LargeParcelable;

public final class IStableAIDLBinderTestService extends Service {
    private static final String TAG = IStableAIDLBinderTestService.class.getSimpleName();

    private final IStableAIDLBinderTestImpl mBinder = new IStableAIDLBinderTestImpl();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This class shows how binder call is wrapped to make it more efficient with shared memory.
    // Most code is copied from auto-generated code with only small changes.
    private static final class IStableAIDLBinderTestImpl extends IStableAIDLTestBinder.Stub {
        // copied due to package scope.
        static final int TRANSACTION_echo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);

        @Override
        public StableAIDLTestLargeParcelable echo(StableAIDLTestLargeParcelable p) {
            return p;
        }

        @Override
        public long echoWithLong(StableAIDLTestLargeParcelable p, long v) {
            StableAIDLTestLargeParcelable r =
                    (StableAIDLTestLargeParcelable) LargeParcelable.reconstructStableAIDLParcelable(
                            p, false);
            return calcByteSum(r) + v;
        }

        @Override
        public void echoWithCallback(IStableAIDLTestCallback callback,
                StableAIDLTestLargeParcelable p) {
            try {
                callback.reply(p);
            } catch (RemoteException e) {
                throw new ServiceSpecificException(-1, "failed to send reply: " + e.toString());
            }
        }
    }

    public static long calcByteSum(StableAIDLTestLargeParcelable p) {
        long ret = 0;
        if (p != null && p.payload != null) {
            for (int i = 0; i < p.payload.length; i++) {
                ret = ret + p.payload[i];
            }
        }
        return ret;
    }
}
