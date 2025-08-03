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

package com.android.bedstead.harrier;

/**
 * A result which can be exposed by {@link BedsteadRunResultsProvider}.
 */
public final class BedsteadResult {
    public static final int PASSED_RESULT = 0;
    public static final int FAILED_RESULT = 1;
    public static final int IGNORED_RESULT = 2;
    public static final int ASSUMPTION_FAILED_RESULT = 3;

    public final int mIndex;
    public final String mTestName;
    public int mResult;
    public String mMessage;
    public String mStackTrace;
    public boolean mHasBeenFetched = false;
    public long mRuntime;
    public boolean mIsFinished = false;

    public BedsteadResult(int index, String testName) {
        mIndex = index;
        mTestName = testName;
    }
}
