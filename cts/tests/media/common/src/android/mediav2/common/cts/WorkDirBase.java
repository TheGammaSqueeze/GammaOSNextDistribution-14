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

package android.mediav2.common.cts;

import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;

import java.io.File;

/**
 * Return the primary shared/external storage directory used by the tests
 */
public class WorkDirBase {
    private static final String TAG = "WorkDirBase";
    private static final String MEDIA_PATH_INSTR_ARG_KEY = "media-path";

    private static final int WAIT_FOR_STORAGE_TIMEOUT_MILLIS = 30000;
    private static final int WAIT_FOR_STORAGE_SLEEP_MILLIS = 1000;

    private static boolean sPolledForStatus = false;
    private static boolean sExternalMounted = false;

    /**
     * Return a File for the top of External Storage.
     * Ensures that external storage is mounted before returing, otherwise errors.
     */
    public static File getTopDir() {
        // All of this is to handle delays in storage being ready during cuttlefish coverage runs.
        // We don't see any of this in normal on-devlce runs.
        // TODO(): can we remove this logic once we resolve the underlying root cause
        //
        // seeing instances where storage seems to go back to unmounted.
        // so if we notice that, let's retry the polling.  But only if it has
        // been mounted at least one time; we want to avoid a constant stream
        // of 30-second delays looking for it to become mounted.
        String storageState = Environment.getExternalStorageState();
        if (sExternalMounted && !Environment.MEDIA_MOUNTED.equals(storageState)) {
            Log.w(TAG, "Previously mounted external storage is now " + storageState);
            sPolledForStatus = false;
            // preserve that it has been mounted before.
        }
        // we wait a bit if storage is not yet ready.
        // We only do this 1 time per run, so bad storage state doesn't explode runtime.
        //
        // we deliberately skip any synchronization here.
        // a race condition means 2 threads wait, 2 threads check status, and Both of them
        // set sPolledForStatus to true, which is result we want.
        // there isn't a deadlock, there isn't data contention, there isn't a count to get wrong.
        //
        if (!sPolledForStatus) {
            int i;
            long start_millis = SystemClock.elapsedRealtime();
            for (i = 0; i < WAIT_FOR_STORAGE_TIMEOUT_MILLIS / WAIT_FOR_STORAGE_SLEEP_MILLIS; i++) {
                String currentState = Environment.getExternalStorageState();
                if (Environment.MEDIA_MOUNTED.equals(currentState)) {
                    break;
                }
                try {
                    Thread.sleep(WAIT_FOR_STORAGE_SLEEP_MILLIS);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted sleep while waiting for external storage");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            String finalState = Environment.getExternalStorageState();
            long end_millis = SystemClock.elapsedRealtime();
            Log.w(TAG, "Waited " + (end_millis - start_millis)
                            + " milliseconds for external storage readiness, final: " + finalState);
            sPolledForStatus = true;
        }
        // fatal if not mounted by now
        Assert.assertEquals("Missing external storage, is this running in instant mode?",
                        Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());
        sExternalMounted = true;
        return Environment.getExternalStorageDirectory();
    }

    public static String getTopDirString() {
        return getTopDir().getAbsolutePath() + File.separator;
    }

    protected static final String getMediaDirString(String defaultFolderName) {
        android.os.Bundle bundle = InstrumentationRegistry.getArguments();
        String mediaDirString = bundle.getString(MEDIA_PATH_INSTR_ARG_KEY);
        if (mediaDirString == null) {
            return getTopDirString() + "test/" + defaultFolderName + "/";
        }
        // user has specified the mediaDirString via instrumentation-arg
        if (mediaDirString.endsWith(File.separator)) {
            return mediaDirString;
        }
        return mediaDirString + File.separator;
    }
}
