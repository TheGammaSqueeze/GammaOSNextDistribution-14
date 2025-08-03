/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.app.cts;

import static junit.framework.Assert.fail;

import android.app.cts.android.app.cts.tools.WatchUidRunner;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test system behavior of a bad provider.
 */
@RunWith(AndroidJUnit4.class)
@Presubmit
public class BadProviderTest {
    private static final String AUTHORITY = "com.android.cts.stubbad.badprovider";
    private static final String TEST_PACKAGE_NAME = "com.android.cts.stubbad";
    private static final int WAIT_TIME = 5000;

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testExitOnCreate() {
        WatchUidRunner uidWatcher = null;
        ContentResolver res = mContext.getContentResolver();
        HandlerThread worker = new HandlerThread("work");
        worker.start();
        Handler handler = new Handler(worker.getLooper());
        try {
            ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(
                    TEST_PACKAGE_NAME, 0);
            uidWatcher = new WatchUidRunner(InstrumentationRegistry.getInstrumentation(),
                    appInfo.uid, WAIT_TIME);
            long startTs = SystemClock.uptimeMillis();
            handler.post(()->
                res.query(Uri.parse("content://" + AUTHORITY), null, null, null, null)
            );
            // Ensure the system will try at least 3 times for a bad content provider.
            uidWatcher.waitFor(WatchUidRunner.CMD_GONE, null);
            uidWatcher.waitFor(WatchUidRunner.CMD_GONE, null);
            uidWatcher.waitFor(WatchUidRunner.CMD_GONE, null);
            // Finish the watcher
            uidWatcher.finish();
            // Sleep for 10 seconds and initialize the watcher again
            // (content provider publish timeout is 10 seconds)
            Thread.sleep(Math.max(0, 10000 - (SystemClock.uptimeMillis() - startTs)));
            uidWatcher = new WatchUidRunner(InstrumentationRegistry.getInstrumentation(),
                    appInfo.uid, WAIT_TIME);
            // By now we shouldn't see it's retrying again.
            try {
                uidWatcher.waitFor(WatchUidRunner.CMD_GONE, null);
                fail("Excessive attempts to bring up a provider");
            } catch (IllegalStateException e) {
            }
        } catch (Exception e) {
            fail("Unexpected exception while query provider: " + e.getMessage());
        } finally {
            if (uidWatcher != null) {
                uidWatcher.finish();
            }
            worker.quitSafely();
        }
    }
}
