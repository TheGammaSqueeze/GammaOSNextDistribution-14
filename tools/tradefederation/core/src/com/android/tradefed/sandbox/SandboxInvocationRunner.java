/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tradefed.sandbox;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.PrettyPrintDelimiter;

/** Run the tests associated with the invocation in the sandbox. */
public class SandboxInvocationRunner {

    /**
     * Do setup and run the tests.
     *
     * @return True if the invocation is successful. False otherwise.
     */
    public static boolean prepareAndRun(
            TestInformation info, IConfiguration config, ITestInvocationListener listener)
            throws Throwable {
        prepareSandbox(info, config, listener);
        return runSandbox(info, config, listener);
    }

    public static void teardownSandbox(IConfiguration config) {
        ISandbox sandbox =
                (ISandbox) config.getConfigurationObject(Configuration.SANDBOX_TYPE_NAME);
        if (sandbox == null) {
            throw new RuntimeException("Couldn't find the sandbox object.");
        }
        sandbox.tearDown();
    }

    /** Preparation step of the sandbox */
    public static void prepareSandbox(
            TestInformation info, IConfiguration config, ITestInvocationListener listener)
            throws Throwable {
        ISandbox sandbox =
                (ISandbox) config.getConfigurationObject(Configuration.SANDBOX_TYPE_NAME);
        if (sandbox == null) {
            throw new RuntimeException("Couldn't find the sandbox object.");
        }
        PrettyPrintDelimiter.printStageDelimiter("Starting Sandbox Environment Setup");
        Exception res = null;
        try {
            res = sandbox.prepareEnvironment(info.getContext(), config, listener);
        } catch (RuntimeException e) {
            sandbox.tearDown();
            throw e;
        }
        if (res != null) {
            CLog.w("Sandbox prepareEnvironment threw an Exception.");
            sandbox.tearDown();
            throw res;
        }
        PrettyPrintDelimiter.printStageDelimiter("Done with Sandbox Environment Setup");
    }

    /** Execution step of the sandbox */
    public static boolean runSandbox(
            TestInformation info, IConfiguration config, ITestInvocationListener listener)
            throws Throwable {
        ISandbox sandbox =
                (ISandbox) config.getConfigurationObject(Configuration.SANDBOX_TYPE_NAME);
        if (sandbox == null) {
            throw new RuntimeException("Couldn't find the sandbox object.");
        }
        long start = System.currentTimeMillis();
        try {
            CommandResult result = sandbox.run(info, config, listener);
            return CommandStatus.SUCCESS.equals(result.getStatus());
        } finally {
            sandbox.tearDown();
            // Only log if it was no already logged to keep the value closest to execution
            if (!InvocationMetricLogger.getInvocationMetrics()
                    .containsKey(InvocationMetricKey.TEST_PAIR.toString())) {
                InvocationMetricLogger.addInvocationPairMetrics(
                        InvocationMetricKey.TEST_PAIR, start, System.currentTimeMillis());
            }
        }
    }
}
