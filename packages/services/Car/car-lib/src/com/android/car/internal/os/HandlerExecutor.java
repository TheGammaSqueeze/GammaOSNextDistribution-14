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

package com.android.car.internal.os;

import android.annotation.NonNull;
import android.os.Handler;

import com.android.internal.util.Preconditions;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * {@code Executor} to run on an {@code Handler}.
 *
 * @hide
 */
public class HandlerExecutor implements Executor {
    private final Handler mHandler;

    /** Construct with {@code Handler} */
    public HandlerExecutor(@NonNull Handler handler) {
        mHandler = Preconditions.checkNotNull(handler);
    }

    @Override
    public void execute(Runnable command) {
        if (!mHandler.post(command)) {
            throw new RejectedExecutionException(mHandler + " is shutting down");
        }
    }
}
