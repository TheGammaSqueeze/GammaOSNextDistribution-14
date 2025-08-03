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

import android.annotation.Nullable;
import android.car.IResultCallback;
import android.car.ResultCallback;
import android.car.builtin.util.Slogf;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Implements a Parcelable class which takes {@link ResultCallback} as input and provides
 * {@link #complete(Object)} call to the binder receiver to send result back asynchronously.
 *
 * <p>
 * Managers can create this class with {@link ResultCallback} and {@link Executor} and pass it
 * across the binder from manager to service. On the service side, when results are available,
 * {@link #complete(Object)} should be called with the result.
 *
 * @param <V> refer to a Parcelable object.
 *
 * @hide
 */
public class ResultCallbackImpl<V> implements Parcelable {

    private static final String TAG = ResultCallbackImpl.class.getSimpleName();
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private final Object mLock = new Object();

    // TODO(b/269040634): Revisit this logic based on the CL discussion
    @GuardedBy("mLock")
    @Nullable
    private IResultCallback mReceiver;

    @Nullable
    private final ResultCallback<V> mCallback;
    @Nullable
    private final Executor mExecutor;

    public ResultCallbackImpl(Executor executor, ResultCallback<V> callback) {
        mExecutor = Objects.requireNonNull(executor, "executor cannot be null");
        mCallback = Objects.requireNonNull(callback, "callback cannot be null");
    }

    ResultCallbackImpl(Parcel in) {
        mExecutor = null;
        mCallback = null;
        mReceiver = IResultCallback.Stub.asInterface(in.readStrongBinder());
    }

    /**
     * Calls to update the {code result}.
     */
    public void complete(V result) {
        try {
            IResultCallback receiver;
            synchronized (mLock) {
                receiver = mReceiver;
            }

            if (receiver == null) {
                // Receive could be null if the call is not IPC.
                sendResult(result);
                return;
            }
            ResultWrapper<V> wrapper = new ResultWrapper<V>(result);
            receiver.complete(wrapper);
        } catch (Exception e) {
            Slogf.w(TAG, e, "ResultCallbackImpl failed.");
        }
    }

    /**
     * Called before sending the sending the result to the callback.
     */
    protected void onCompleted(V result) {}

    private void sendResult(V result) {
        if (mExecutor == null || mCallback == null) {
            // This should never happen. sendResult call will always be executed on the sender side.
            Slogf.wtf(TAG, "Executor or callback can not be null. Executor:%s Callback:%s",
                    mExecutor, mCallback);
            return;
        }

        if (DBG) {
            Slogf.d(TAG, "Sending results - %s", result.toString());
        }

        onCompleted(result);
        mExecutor.execute(() -> mCallback.onResult(result));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        synchronized (mLock) {
            if (mReceiver == null) {
                mReceiver = new MyCallbackReceiver();
            }
            dest.writeStrongBinder(mReceiver.asBinder());
        }
    }

    private final class MyCallbackReceiver extends IResultCallback.Stub {
        @Override
        public void complete(ResultWrapper resultCallback) {
            sendResult((V) resultCallback.getResult());
        }
    }

    public static final Parcelable.Creator<ResultCallbackImpl> CREATOR =
            new Parcelable.Creator<ResultCallbackImpl>() {
        public ResultCallbackImpl createFromParcel(Parcel in) {
            return new ResultCallbackImpl(in);
        }

        public ResultCallbackImpl[] newArray(int size) {
            return new ResultCallbackImpl[size];
        }
    };
}
