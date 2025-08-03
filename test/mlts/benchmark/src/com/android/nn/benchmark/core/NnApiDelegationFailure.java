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

package com.android.nn.benchmark.core;

import android.annotation.SuppressLint;

public class NnApiDelegationFailure extends BenchmarkException {
    private final int mNnApiErrno;

    @SuppressLint("DefaultLocale")
    public NnApiDelegationFailure(int nnApiErrno) {
        super(String.format("Nnapi call failed with error code %d", nnApiErrno));
        mNnApiErrno = nnApiErrno;
    }

    public NnApiDelegationFailure(String message) {
        super(message);
        mNnApiErrno = 0;
    }

    public int getNnApiErrno() {
        return mNnApiErrno;
    }
}
