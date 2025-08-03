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
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.android.car.internal.LargeParcelable;

public final class LargeParcelableTestService extends Service {
    private static final String TAG = LargeParcelableTestService.class.getSimpleName();

    private final IJavaTestBinder.Stub mIBnderTestImpl = new IJavaTestBinder.Stub() {

        @Override
        public TestLargeParcelable echoTestLargeParcelable(TestLargeParcelable p) {
            Log.i(TAG, "echoTestLargeParcelable, TestLargeParcelable:" + p);
            if (p != null) {
                Log.i(TAG, "byteArray:" + ((p.byteData == null) ? null : p.byteData.length));
            }
            return p;
        }

        @Override
        public LargeParcelable echoLargeParcelable(LargeParcelable p) {
            Log.i(TAG, "echoLargeParcelable, LargeParcelable:" + p);
            if (p != null) {
                TestParcelable tp = (TestParcelable) (p.getParcelable());
                if (tp != null) {
                    Log.i(TAG, "byteArray:"
                            + ((tp.byteData == null) ? null : tp.byteData.length));
                }
            }
            return p;
        }

        @Override
        public long echoLongWithTestLargeParcelable(TestLargeParcelable p, long v) {
            return v + calcByteSum(p);
        }

        @Override
        public long echoLongWithLargeParcelable(LargeParcelable p, long v) {
            return v;
        }
    };

    public static long calcByteSum(TestLargeParcelable p) {
        long ret = 0;
        if (p != null && p.byteData != null) {
            for (int i = 0; i < p.byteData.length; i++) {
                ret = ret + p.byteData[i];
            }
        }
        return ret;
    }

    @Override
    public IBinder onBind(Intent intent) {
        LargeParcelable.setClassLoader(getClassLoader());
        return mIBnderTestImpl;
    }
}
