/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.cts.fgs.bootcompleted;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.app.cts.CtsAppTestUtils;
import android.app.cts.LocationBootCompletedFgs;
import android.app.stubs.BootCompletedFgs;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AmUtils;
import com.android.server.am.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class BootCompletedFgsStartTest {

    private static Context sTargetContext;
    private static Instrumentation sInstrumentation;

    public static final String BOOT_COMPLETED_FGS_FINISHED = "boot_completed_fgs_finished";

    public static final String FAKE_BOOT_COMPLETED = "PSEUDO_BOOT_COMPLETED";

    public static final String ALLOW_FGS_START_CMD =
            "am broadcast -a " + FAKE_BOOT_COMPLETED + " --allow-fgs-start-reason 200";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static CountDownLatch sBroadcastLatch = new CountDownLatch(1);

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // start an FGS
            sBroadcastLatch.countDown();
            Intent serviceIntent = new Intent(sTargetContext, BootCompletedFgs.class);
            try {
                sTargetContext.startForegroundService(serviceIntent);
            } catch (Exception e) {
                // Catch this with the latch
            }
        }
    };

    private void enableFgsRestriction(boolean enable, String packageName)
            throws Exception {
        final String action = enable ? "enable" : "disable";
        CtsAppTestUtils.executeShellCmd(sInstrumentation, "am compat " + action
                + " --no-kill FGS_BOOT_COMPLETED_RESTRICTIONS " + packageName);
    }

    private void startFgsBootCompleted() {
        sInstrumentation.getUiAutomation().adoptShellPermissionIdentity();
        sInstrumentation.getUiAutomation().executeShellCommand(ALLOW_FGS_START_CMD);
        sInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Before
    public void setUp() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sTargetContext = sInstrumentation.getTargetContext();
        BootCompletedFgs.latch = new CountDownLatch(1);
        sBroadcastLatch = new CountDownLatch(1);
        final IntentFilter filter = new IntentFilter();
        filter.addAction(FAKE_BOOT_COMPLETED);
        sTargetContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
        resetFgsRestrictionEnabled(sTargetContext.getPackageName());

        AmUtils.waitForBroadcastBarrier();
    }

    @After
    public void tearDown() throws Exception {
        sTargetContext.unregisterReceiver(mReceiver);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FGS_BOOT_COMPLETED)
    public void fgsTypeNotAllowedStartTest() throws Exception {
        try {
            BootCompletedFgs.types = FOREGROUND_SERVICE_TYPE_CAMERA
                    | FOREGROUND_SERVICE_TYPE_MICROPHONE;
            enableFgsRestriction(true, sTargetContext.getPackageName());
            startFgsBootCompleted();

            sBroadcastLatch.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(0, sBroadcastLatch.getCount());

            BootCompletedFgs.latch.await(10000, TimeUnit.MILLISECONDS);
            assertNotEquals(0, BootCompletedFgs.latch.getCount());
        } finally {
            Intent serviceIntent = new Intent(sTargetContext, BootCompletedFgs.class);
            sTargetContext.stopService(serviceIntent);
        }
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_FGS_BOOT_COMPLETED)
    public void fgsTypeNotAllowedStartTest_changesDisabled() throws Exception {
        try {
            BootCompletedFgs.types = FOREGROUND_SERVICE_TYPE_CAMERA
                    | FOREGROUND_SERVICE_TYPE_MICROPHONE;
            enableFgsRestriction(false, sTargetContext.getPackageName());
            startFgsBootCompleted();

            sBroadcastLatch.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(0, sBroadcastLatch.getCount());

            BootCompletedFgs.latch.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(0, BootCompletedFgs.latch.getCount());
        } finally {
            Intent serviceIntent = new Intent(sTargetContext, BootCompletedFgs.class);
            sTargetContext.stopService(serviceIntent);
        }
    }

    @Test
    public void fgsTypeAllowedStartTest() throws Exception {
        try {
            BootCompletedFgs.types = FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
            startFgsBootCompleted();

            sBroadcastLatch.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(0, sBroadcastLatch.getCount());

            BootCompletedFgs.latch.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(0, BootCompletedFgs.latch.getCount());
        } finally {
            Intent serviceIntent = new Intent(sTargetContext, BootCompletedFgs.class);
            sTargetContext.stopService(serviceIntent);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FGS_BOOT_COMPLETED)
    public void fgsTypeLocationAllowedStartTest() throws Exception {
        try {
            BootCompletedFgs.types = FOREGROUND_SERVICE_TYPE_LOCATION;
            startFgsBootCompleted();

            sBroadcastLatch.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(0, sBroadcastLatch.getCount());

            BootCompletedFgs.latch.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(0, BootCompletedFgs.latch.getCount());
        } finally {
            Intent serviceIntent = new Intent(sTargetContext, BootCompletedFgs.class);
            sTargetContext.stopService(serviceIntent);
        }
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_FGS_BOOT_COMPLETED)
    public void fgsTypeLocationAllowedStartTest_noPermission() throws Exception {
        LocationBootCompletedFgs.types = FOREGROUND_SERVICE_TYPE_LOCATION;
        enableFgsRestriction(true, sTargetContext.getPackageName());

        final IntentFilter filter = new IntentFilter();
        filter.addAction(FAKE_BOOT_COMPLETED);
        sTargetContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);

        final CountDownLatch fgsStartedLatch = new CountDownLatch(1);
        final BroadcastReceiver finishedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                fgsStartedLatch.countDown();
            }
        };

        final IntentFilter fgsFinishedActionFilter = new IntentFilter();
        fgsFinishedActionFilter.addAction(BOOT_COMPLETED_FGS_FINISHED);
        try {
            sTargetContext.registerReceiver(finishedReceiver,
                    fgsFinishedActionFilter, Context.RECEIVER_EXPORTED);
            startFgsBootCompleted();

            sBroadcastLatch.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(0, sBroadcastLatch.getCount());

            fgsStartedLatch.await(10000, TimeUnit.MILLISECONDS);
            assertNotEquals(0, fgsStartedLatch.getCount());
        } catch (Exception e) {
            fail("unexpected error: " + e);
        } finally {
            Intent serviceIntent = new Intent(sInstrumentation.getContext(),
                    LocationBootCompletedFgs.class);
            sTargetContext.stopService(serviceIntent);
            sTargetContext.unregisterReceiver(finishedReceiver);
        }
    }

    @Test
    public void fgsTypeHealthAllowedStartTest() throws Exception {
        try {
            BootCompletedFgs.types = FOREGROUND_SERVICE_TYPE_HEALTH;
            startFgsBootCompleted();

            sBroadcastLatch.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(0, sBroadcastLatch.getCount());

            BootCompletedFgs.latch.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(0, BootCompletedFgs.latch.getCount());
        } finally {
            Intent serviceIntent = new Intent(sTargetContext, BootCompletedFgs.class);
            sTargetContext.stopService(serviceIntent);
        }
    }

    @Test
    public void fgsTypeRemoteMessagingAllowedStartTest() throws Exception {
        try {
            BootCompletedFgs.types = FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING;
            startFgsBootCompleted();

            sBroadcastLatch.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(0, sBroadcastLatch.getCount());

            BootCompletedFgs.latch.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(0, BootCompletedFgs.latch.getCount());
        } finally {
            Intent serviceIntent = new Intent(sTargetContext, BootCompletedFgs.class);
            sTargetContext.stopService(serviceIntent);
        }
    }

    @Test
    public void fgsTypeMixedAllowedStartTest() throws Exception {
        try {
            BootCompletedFgs.types = FOREGROUND_SERVICE_TYPE_CAMERA
                    | FOREGROUND_SERVICE_TYPE_MICROPHONE
                    | FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
            startFgsBootCompleted();

            sBroadcastLatch.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(0, sBroadcastLatch.getCount());

            BootCompletedFgs.latch.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(0, BootCompletedFgs.latch.getCount());
        } finally {
            Intent serviceIntent = new Intent(sTargetContext, BootCompletedFgs.class);
            sTargetContext.stopService(serviceIntent);
        }
    }

    private void resetFgsRestrictionEnabled(String packageName) {
        sInstrumentation.getUiAutomation().executeShellCommand(
                "am compat reset --no-kill FGS_BOOT_COMPLETED_RESTRICTIONS " + packageName);
    }
}
