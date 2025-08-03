/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tradefed.testtype.junit4;

/**
 * Thrown when test phase timeout is triggered and an InterruptedException needs to be carried from
 * test execution thread to invocation execution thread.
 */
public class CarryInterruptedException extends RuntimeException {

    private InterruptedException mException;

    public CarryInterruptedException(InterruptedException e) {
        mException = e;
    }

    public InterruptedException getInterruptedException() {
        return mException;
    }
}
