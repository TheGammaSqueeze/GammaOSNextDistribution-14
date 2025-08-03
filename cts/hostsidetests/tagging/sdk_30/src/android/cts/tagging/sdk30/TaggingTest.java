/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.cts.tagging.sdk30;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation.ActivityResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.cts.tagging.Utils;
import android.os.Build;

import androidx.test.filters.SmallTest;
import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Rule;
import org.junit.Test;

import com.android.compatibility.common.util.DropBoxReceiver;

import android.cts.tagging.ServiceRunnerActivity;
import static android.cts.tagging.Constants.*;

import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TaggingTest {
    private static final String NATIVE_CRASH_TAG = "data_app_native_crash";
    private static String TAG = TaggingTest.class.getName();

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testHeapTaggingEnabled() {
        assertNotEquals(0, Utils.nativeHeapPointerTag());
    }

    @Test
    public void testHeapTaggingDisabled() {
        assertEquals(0, Utils.nativeHeapPointerTag());
    }

    @Test
    public void testMemoryTagSyncChecksEnabled() throws Exception {
        final DropBoxReceiver receiver =
                new DropBoxReceiver(
                        mContext,
                        NATIVE_CRASH_TAG,
                        mContext.getPackageName() + ":CrashProcess",
                        "SEGV_MTESERR",
                        "backtrace:");
        Intent intent = new Intent();
        intent.setClass(mContext, CrashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);

        assertTrue(receiver.await());
        assertTrue(Utils.mistaggedKernelUaccessFails());
    }

    @Test
    public void testMemoryTagAsyncChecksEnabled() throws Exception {
        final DropBoxReceiver receiver =
                new DropBoxReceiver(
                        mContext,
                        NATIVE_CRASH_TAG,
                        mContext.getPackageName() + ":CrashProcess",
                        Pattern.compile("SEGV_MTE[AS]ERR"),
                        "backtrace:");
        Intent intent = new Intent();
        intent.setClass(mContext, CrashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);

        assertTrue(receiver.await());
    }

    @Test
    public void testMemoryTagChecksDisabled() {
        Utils.accessMistaggedPointer();
        assertFalse(Utils.mistaggedKernelUaccessFails());
    }

    @Test
    public void testMemoryTagSyncActivityChecksEnabled() throws Exception {
        final DropBoxReceiver receiver =
                new DropBoxReceiver(
                        mContext,
                        NATIVE_CRASH_TAG,
                        mContext.getPackageName() + ":CrashMemtagSync",
                        "SEGV_MTESERR",
                        "backtrace:");
        Intent intent = new Intent();
        intent.setClass(mContext, CrashMemtagSyncActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);

        assertTrue(receiver.await());
    }

    @Test
    public void testMemoryTagAsyncActivityChecksEnabled() throws Exception {
        final DropBoxReceiver receiver =
                new DropBoxReceiver(
                        mContext,
                        NATIVE_CRASH_TAG,
                        mContext.getPackageName() + ":CrashMemtagAsync",
                        Pattern.compile("SEGV_MTE[AS]ERR"),
                        "backtrace:");
        Intent intent = new Intent();
        intent.setClass(mContext, CrashMemtagAsyncActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);

        assertTrue(receiver.await());
    }

    @Rule
    public ActivityTestRule<TestActivity> mTestActivityRule =
            new ActivityTestRule<>(
                    TestActivity.class, false /*initialTouchMode*/, false /*launchActivity*/);

    @Test
    public void testHeapZeroInitActivity() throws Exception {
      TestActivity activity = mTestActivityRule.launchActivity(null);
      activity.callActivity(HeapZeroInitActivity.class);
      assertFalse(activity.failed());
    }

    @Test
    public void testHeapZeroInitMemtagAsyncActivity() throws Exception {
      TestActivity activity = mTestActivityRule.launchActivity(null);
      activity.callActivity(HeapZeroInitMemtagAsyncActivity.class);
      assertFalse(activity.failed());
    }

    // Exported service tests.

    @Rule
    public ActivityTestRule<ServiceRunnerActivity> mServiceRunnerActivityRule = new ActivityTestRule<>(
        ServiceRunnerActivity.class, false /*initialTouchMode*/, false /*launchActivity*/);

    @Test
    public void testExportedMemtagSyncService() throws Exception {
      ServiceRunnerActivity activity = mServiceRunnerActivityRule.launchActivity(null);
      activity.runExternalService(new ComponentName(
          "android.cts.tagging.sdk30memtag", "android.cts.tagging.sdk30memtag.ExportedMemtagSyncService"));
      assertEquals(TAGGING_MODE_SYNC, activity.getResult());
    }

    @Test
    public void testExportedMemtagOffService() throws Exception {
      ServiceRunnerActivity activity = mServiceRunnerActivityRule.launchActivity(null);
      activity.runExternalService(new ComponentName(
          "android.cts.tagging.sdk30memtag", "android.cts.tagging.sdk30memtag.ExportedMemtagOffService"));
      assertEquals(TAGGING_MODE_OFF, activity.getResult());
    }

    @Test
    public void testExportedMemtagSyncAppZygoteService() throws Exception {
      ServiceRunnerActivity activity = mServiceRunnerActivityRule.launchActivity(null);
      activity.runExternalService(new ComponentName(
          "android.cts.tagging.sdk30memtag", "android.cts.tagging.sdk30memtag.ExportedMemtagSyncAppZygoteService"));
      assertEquals(TAGGING_MODE_SYNC, activity.getResult());
    }

    @Test
    public void testExportedMemtagOffAppZygoteService() throws Exception {
      ServiceRunnerActivity activity = mServiceRunnerActivityRule.launchActivity(null);
      activity.runExternalService(new ComponentName(
          "android.cts.tagging.sdk30memtag", "android.cts.tagging.sdk30memtag.ExportedMemtagOffAppZygoteService"));
      assertEquals(TAGGING_MODE_OFF, activity.getResult());
    }

}
