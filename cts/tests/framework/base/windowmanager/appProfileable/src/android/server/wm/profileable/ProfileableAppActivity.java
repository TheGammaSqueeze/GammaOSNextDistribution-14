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
 * limitations under the License
 */

package android.server.wm.profileable;

import static android.server.wm.profileable.Components.ProfileableAppActivity.COMMAND_WAIT_FOR_PROFILE_OUTPUT;
import static android.server.wm.profileable.Components.ProfileableAppActivity.OUTPUT_DIR;
import static android.server.wm.profileable.Components.ProfileableAppActivity.OUTPUT_NAME;

import android.os.Bundle;
import android.os.FileObserver;
import android.os.SystemClock;
import android.server.wm.CommandSession.BasicTestActivity;
import android.util.Log;

import java.io.File;

public class ProfileableAppActivity extends BasicTestActivity {
    private static final String TAG = ProfileableAppActivity.class.getSimpleName();

    private final TraceFileObserver mTraceFileObserver = new TraceFileObserver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mPrintCallbackLog = true;
        super.onCreate(savedInstanceState);
        mTraceFileObserver.startWatching();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTraceFileObserver.stopWatching();
    }

    @Override
    public void handleCommand(String command, Bundle data) {
        if (COMMAND_WAIT_FOR_PROFILE_OUTPUT.equals(command)) {
            // Do not wait on main thread because the message PROFILER_CONTROL to stop the profile
            // is also scheduled on main thread.
            new Thread(() -> {
                mTraceFileObserver.waitForComplete();
                runOnUiThread(() -> reply(command));
            }).start();
            return;
        }

        super.handleCommand(command, data);
    }

    /** Monitor the close event of trace file. */
    private static class TraceFileObserver extends FileObserver {
        /**
         * The timeout for the close event of trace file. Although the trace file may take longer
         * than 3 seconds, it is enough because the test only checks the first line of the file.
         * Note that this timeout must be less than the value of
         * {@link android.server.wm.CommandSession.ActivitySession.Response#TIMEOUT_MILLIS}.
         * Otherwise the caller who sent {@link COMMAND_WAIT_FOR_PROFILE_OUTPUT} may get exception.
         */
        private static final long TIMEOUT_MS = 4500L;
        private volatile boolean mDone;

        TraceFileObserver() {
            super(new File(OUTPUT_DIR), CLOSE_WRITE);
        }

        @Override
        public synchronized void onEvent(int event, String path) {
            Log.i(TAG, "onEvent path=" + path);
            if (OUTPUT_NAME.equals(path)) {
                mDone = true;
                notifyAll();
            }
        }

        /** Waits for a {@link CLOSE_WRITE} event. */
        synchronized void waitForComplete() {
            if (!mDone) {
                final long startTimeMs = SystemClock.elapsedRealtime();
                try {
                    wait(TIMEOUT_MS);
                    if (SystemClock.elapsedRealtime() - startTimeMs >= TIMEOUT_MS) {
                        Log.e(TAG, "waitForComplete timeout");
                    }
                } catch (InterruptedException ignored) {}
            }
            mDone = false;
        }
    }
}
