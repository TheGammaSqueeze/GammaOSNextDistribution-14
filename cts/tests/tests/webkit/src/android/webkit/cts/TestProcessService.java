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

package android.webkit.cts;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import com.google.common.util.concurrent.SettableFuture;

// Subclasses are the ones that get actually used, so make this abstract
abstract class TestProcessService extends Service {
    static final String REPLY_EXCEPTION_KEY = "exception";

    private final Handler mHandler;

    public TestProcessService() {
        HandlerThread handlerThread = new HandlerThread("TestThread");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
    }

    private final ITestProcessService.Stub mBinder = new ITestProcessService.Stub() {
        @Override
        public Bundle run(String testClassName) {
            final SettableFuture<Bundle> testResultFuture = SettableFuture.create();
            mHandler.post(() -> {
                Bundle testResultBundle = new Bundle();
                try {
                    Class testClass = Class.forName(testClassName);
                    TestProcessClient.TestRunnable test =
                            (TestProcessClient.TestRunnable) testClass.newInstance();
                    test.run(TestProcessService.this);
                } catch (Throwable t) {
                    testResultBundle.putSerializable(REPLY_EXCEPTION_KEY, t);
                }
                testResultFuture.set(testResultBundle);
            });
            return WebkitUtils.waitForFuture(testResultFuture);
        }

        @Override
        public void exit() {
            System.exit(0);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
