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
package com.android.tradefed.testtype.bazel;

import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.result.ITestInvocationListener;

/** Listener for cached tests that only reports module level events. */
final class SparseTestListener extends NullTestListener {

    private final ITestInvocationListener mDelegate;

    public SparseTestListener(ITestInvocationListener delegate) {
        mDelegate = delegate;
    }

    private ITestInvocationListener delegate() {
        return mDelegate;
    }

    @Override
    public void testModuleStarted(IInvocationContext moduleContext) {
        moduleContext.addInvocationAttribute("sparse-module", "true");
        delegate().testModuleStarted(moduleContext);
    }

    @Override
    public void testModuleEnded() {
        delegate().testModuleEnded();
    }
}
