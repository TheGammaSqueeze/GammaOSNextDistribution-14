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

package com.android.car.scriptexecutortest.nonsystemuser;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.telemetry.scriptexecutorinterface.IScriptExecutor;
import com.android.car.telemetry.scriptexecutorinterface.IScriptExecutorListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public final class ScriptExecutorNonSystemUserTest {

    private IScriptExecutor mScriptExecutor;
    private Context mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private static final class ScriptExecutorListener extends IScriptExecutorListener.Stub {
        public PersistableBundle mInterimResult;
        public PersistableBundle mFinalResult;
        public int mErrorType;
        public String mMessage;
        public String mStackTrace;
        public final CountDownLatch mResponseLatch = new CountDownLatch(1);

        @Override
        public void onScriptFinished(PersistableBundle result) {
            mFinalResult = result;
            mResponseLatch.countDown();
        }

        @Override
        public void onSuccess(PersistableBundle stateToPersist) {
            mInterimResult = stateToPersist;
            mResponseLatch.countDown();
        }

        @Override
        public void onError(int errorType, String message, String stackTrace) {
            mErrorType = errorType;
            mMessage = message;
            mStackTrace = stackTrace;
            mResponseLatch.countDown();
        }

        @Override
        public void onMetricsReport(PersistableBundle report, PersistableBundle stateToPersist) {
            mFinalResult = report;
            mInterimResult = stateToPersist;
            mResponseLatch.countDown();
        }

        private boolean awaitResponse(int waitTimeSec) throws InterruptedException {
            return mResponseLatch.await(waitTimeSec, TimeUnit.SECONDS);
        }
    }

    private final ScriptExecutorListener mListener = new ScriptExecutorListener();

    private final PersistableBundle mPublishedData = new PersistableBundle();
    private final PersistableBundle mSavedState = new PersistableBundle();

    private final CountDownLatch mBindLatch = new CountDownLatch(1);

    private static final int BIND_SERVICE_TIMEOUT_SEC = 5;
    private static final int SCRIPT_PROCESSING_TIMEOUT_SEC = 10;

    private final ServiceConnection mScriptExecutorConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName className, IBinder service) {
                    mScriptExecutor = IScriptExecutor.Stub.asInterface(service);
                    mBindLatch.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName className) {
                    assertWithMessage("Service unexpectedly disconnected").fail();
                }
            };

    @Before
    public void setUp() throws InterruptedException {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(
                        "com.android.car.scriptexecutor",
                        "com.android.car.scriptexecutor.ScriptExecutor"));
        mContext.bindServiceAsUser(
                intent, mScriptExecutorConnection, Context.BIND_AUTO_CREATE, UserHandle.SYSTEM);
        if (!mBindLatch.await(BIND_SERVICE_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            assertWithMessage("Failed to bind to ScriptExecutor service").fail();
        }
    }

    @Test
    public void invokeScript_FromNonSystemUser_isNotProcessed() throws Exception {
        String script =
                "function hello(data, state)\n"
                        + "    result = {hello=\"world\"}\n"
                        + "    on_success(result)\n"
                        + "end\n";

        mScriptExecutor.invokeScript(script, "hello", mPublishedData, mSavedState, mListener);

        assertThat(mListener.awaitResponse(SCRIPT_PROCESSING_TIMEOUT_SEC)).isFalse();
    }

    @Test
    public void invokeScriptForLargeInput_FromNonSystemUser_isNotProcessed() throws Exception {
        String script =
                "function hello(data, state)\n"
                        + "    result = {hello=\"world\"}\n"
                        + "    on_success(result)\n"
                        + "end\n";

        ParcelFileDescriptor[] fds = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor writeFd = fds[1];
        ParcelFileDescriptor readFd = fds[0];

        mScriptExecutor.invokeScriptForLargeInput(
                script, "large_published_data", readFd, mSavedState, mListener);

        readFd.close();
        writeFd.close();

        assertThat(mListener.awaitResponse(SCRIPT_PROCESSING_TIMEOUT_SEC)).isFalse();
    }
}
