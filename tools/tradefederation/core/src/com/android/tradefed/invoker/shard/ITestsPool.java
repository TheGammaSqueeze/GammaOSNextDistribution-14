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
package com.android.tradefed.invoker.shard;

import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.shard.token.ITokenRequest;
import com.android.tradefed.testtype.IRemoteTest;

/** Interface describing a pool of tests that we can access and run */
public interface ITestsPool {

    /** Poll the next test to be executed. */
    public IRemoteTest poll(TestInformation info, boolean reportNotExecuted);

    /** Returns the list of test that was rejected to run on all devices. */
    public ITokenRequest pollRejectedTokenModule();
}
