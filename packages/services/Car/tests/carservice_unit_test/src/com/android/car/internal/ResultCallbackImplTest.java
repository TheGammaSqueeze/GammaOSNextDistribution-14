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

package com.android.car.internal;

import static com.google.common.truth.Truth.assertThat;

import android.car.ResultCallback;
import android.car.SyncResultCallback;
import android.car.user.UserCreationResult;
import android.os.Parcel;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public final class ResultCallbackImplTest {

    private static final int TIMEOUT_MS = 5000;
    private static final TimeUnit UNIT = TimeUnit.MILLISECONDS;
    private static final int STATUS = UserCreationResult.STATUS_SUCCESSFUL;

    @Test
    public void testParcelable() throws Exception {
        SyncResultCallback<UserCreationResult> callback = new SyncResultCallback<>();
        ResultCallbackImplInternal<UserCreationResult> resultCallbackImpl =
                new ResultCallbackImplInternal<UserCreationResult>(Runnable::run, callback);

        Parcel p = null;
        try {
            p = Parcel.obtain();
            resultCallbackImpl.writeToParcel(p, /* flags= */ 0);
            p.setDataPosition(0);
            @SuppressWarnings("unchecked")
            ResultCallbackImpl<UserCreationResult> newResultCallbackImpl =
                    ResultCallbackImpl.CREATOR.createFromParcel(p);

            newResultCallbackImpl.complete(new UserCreationResult(STATUS));

            assertThat(callback.get(TIMEOUT_MS, UNIT).getStatus()).isEqualTo(STATUS);
            assertThat(resultCallbackImpl.isCompleteCalled()).isTrue();
        } finally {
            if (p != null) {
                p.recycle();
            }
        }
    }

    @Test
    public void tesSameProcessCall() throws Exception {
        SyncResultCallback<UserCreationResult> callback = new SyncResultCallback<>();
        ResultCallbackImplInternal<UserCreationResult> resultCallbackImpl =
                new ResultCallbackImplInternal<UserCreationResult>(Runnable::run, callback);

        resultCallbackImpl.complete(new UserCreationResult(STATUS));

        assertThat(callback.get(TIMEOUT_MS, UNIT).getStatus()).isEqualTo(STATUS);
        assertThat(resultCallbackImpl.isCompleteCalled()).isTrue();
    }


    private static final class ResultCallbackImplInternal<V> extends ResultCallbackImpl<V> {
        private final CountDownLatch mLatch = new CountDownLatch(1);

        ResultCallbackImplInternal(Executor executor, ResultCallback<V> callback) {
            super(executor, callback);
        }

        @Override
        protected void onCompleted(V result) {
            mLatch.countDown();
        }

        public boolean isCompleteCalled() throws Exception {
            return mLatch.await(TIMEOUT_MS, UNIT);
        }

    }
}
