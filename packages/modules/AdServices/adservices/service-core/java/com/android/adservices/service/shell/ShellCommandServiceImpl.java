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

package com.android.adservices.service.shell;

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;

import android.adservices.shell.IShellCommand;
import android.adservices.shell.IShellCommandCallback;
import android.adservices.shell.ShellCommandParam;
import android.adservices.shell.ShellCommandResult;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Implements a service which runs shell command in the AdServices process.
 *
 * <p>This internally calls {@link AdServicesShellCommandHandler} which has main logic to execute
 * the shell command.
 *
 * @hide
 */
public final class ShellCommandServiceImpl extends IShellCommand.Stub {

    @VisibleForTesting
    static final int RESULT_SHELL_COMMAND_EXECUTION_TIMED_OUT = Integer.MIN_VALUE;

    private static final long MAX_COMMAND_DURATION_MILLIS = 3000L;
    private final ScheduledThreadPoolExecutor mSchedulingExecutorService;
    private final ListeningExecutorService mExecutorService;

    private final long mMaxCommandDurationMillis;
    private ShellCommandFactorySupplier mShellCommandFactorySupplier;

    @VisibleForTesting
    public ShellCommandServiceImpl(
            @NonNull ShellCommandFactorySupplier shellCommandFactorySupplier,
            @NonNull ListeningExecutorService executorService,
            @NonNull ScheduledThreadPoolExecutor schedulingExecutorService,
            long maxCommandDurationMillis) {
        mShellCommandFactorySupplier =
                Objects.requireNonNull(
                        shellCommandFactorySupplier, "shellCommandFactorySupplier cannot be null");

        mExecutorService = executorService;
        mSchedulingExecutorService = schedulingExecutorService;
        mMaxCommandDurationMillis = maxCommandDurationMillis;
    }

    public ShellCommandServiceImpl() {
        this(
                new AdservicesShellCommandFactorySupplier(),
                AdServicesExecutors.getLightWeightExecutor(),
                AdServicesExecutors.getScheduler(),
                MAX_COMMAND_DURATION_MILLIS);
    }

    @Override
    public void runShellCommand(ShellCommandParam param, IShellCommandCallback callback) {
        StringWriter outStringWriter = new StringWriter();
        StringWriter errStringWriter = new StringWriter();

        PrintWriter outPw = new PrintWriter(outStringWriter);
        PrintWriter errPw = new PrintWriter(errStringWriter);

        AdServicesShellCommandHandler handler =
                new AdServicesShellCommandHandler(outPw, errPw, mShellCommandFactorySupplier);
        FluentFuture.from(mExecutorService.submit(() -> handler.run(param.getCommandArgs())))
                .withTimeout(
                        mMaxCommandDurationMillis,
                        TimeUnit.MILLISECONDS,
                        mSchedulingExecutorService)
                .addCallback(
                        new FutureCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer resultCode) {
                                ShellCommandResult response =
                                        new ShellCommandResult.Builder()
                                                .setResultCode(resultCode)
                                                .setOut(outStringWriter.toString())
                                                .setErr(errStringWriter.toString())
                                                .build();
                                notifyCaller(response, callback, param, outPw, errPw);
                            }

                            @Override
                            // We can be here only for timeout.
                            // All other failures are processed in the handler.
                            public void onFailure(Throwable timeoutException) {
                                Log.w(
                                        TAG,
                                        "Service failure when processing command ",
                                        timeoutException);

                                int resultCode = RESULT_SHELL_COMMAND_EXECUTION_TIMED_OUT;

                                ShellCommandResult response =
                                        new ShellCommandResult.Builder()
                                                .setResultCode(resultCode)
                                                .setErr(
                                                        "Timeout processing command "
                                                                + timeoutException)
                                                .build();

                                notifyCaller(response, callback, param, outPw, errPw);
                            }
                        },
                        mExecutorService);
    }

    private static void notifyCaller(
            ShellCommandResult response,
            IShellCommandCallback callback,
            ShellCommandParam param,
            PrintWriter outPw,
            PrintWriter errPw) {
        try {
            callback.onResult(response);
        } catch (RemoteException e) {
            Log.e(
                    TAG,
                    String.format("Unable to send result to the callback for request: %s", param),
                    e);
        }

        outPw.close();
        errPw.close();
    }
}
