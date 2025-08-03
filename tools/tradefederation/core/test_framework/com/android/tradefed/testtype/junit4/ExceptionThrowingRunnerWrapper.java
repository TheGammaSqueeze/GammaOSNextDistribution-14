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

import com.android.tradefed.invoker.TestInformation;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;

public class ExceptionThrowingRunnerWrapper extends Runner {
    private Runner runner;
    private TestInformation mTestInfo;

    public ExceptionThrowingRunnerWrapper(Runner runner, TestInformation testInfo) {
        this.runner = runner;
        this.mTestInfo = testInfo;
    }

    @Override
    public Description getDescription() {
        return runner.getDescription();
    }

    @Override
    public void run(RunNotifier notifier) {
        RunNotifierWrapper runNotifierWrapper = new RunNotifierWrapper(notifier);
        runNotifierWrapper.setTestInfo(mTestInfo);
        runner.run(runNotifierWrapper);
    }
}
