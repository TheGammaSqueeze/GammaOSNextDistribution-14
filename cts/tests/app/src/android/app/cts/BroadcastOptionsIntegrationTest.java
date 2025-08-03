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

package android.app.cts;

import static android.app.cts.ActivityManagerFgsBgStartTest.PACKAGE_NAME_APP1;
import static android.app.cts.ActivityManagerFgsBgStartTest.PACKAGE_NAME_APP2;
import static android.app.cts.ActivityManagerFgsBgStartTest.WAITFOR_MSEC;
import static android.app.cts.BroadcastOptionsTest.cloneViaBundle;
import static android.app.stubs.LocalForegroundService.ACTION_START_FGS_RESULT;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertThrows;

import android.app.BroadcastOptions;
import android.app.Instrumentation;
import android.app.cts.android.app.cts.tools.WaitForBroadcast;
import android.app.stubs.CommandReceiver;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BroadcastOptionsIntegrationTest {
    private void assertBroadcastSuccess(BroadcastOptions options) {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final WaitForBroadcast waiter = new WaitForBroadcast(instrumentation.getTargetContext());
        waiter.prepare(ACTION_START_FGS_RESULT);
        CommandReceiver.sendCommandWithBroadcastOptions(instrumentation.getContext(),
                CommandReceiver.COMMAND_START_FOREGROUND_SERVICE,
                PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null,
                options.toBundle());
        waiter.doWait(WAITFOR_MSEC);
    }

    private void assertBroadcastFailure(BroadcastOptions options) {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final WaitForBroadcast waiter = new WaitForBroadcast(instrumentation.getTargetContext());
        waiter.prepare(ACTION_START_FGS_RESULT);
        CommandReceiver.sendCommandWithBroadcastOptions(instrumentation.getContext(),
                CommandReceiver.COMMAND_START_FOREGROUND_SERVICE,
                PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null,
                options.toBundle());
        assertThrows(Exception.class, () -> waiter.doWait(WAITFOR_MSEC));
    }

    @Test
    public void testRequireCompatChange_simple() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            final int uid = android.os.Process.myUid();
            final BroadcastOptions options = BroadcastOptions.makeBasic();

            // Default passes
            assertTrue(options.testRequireCompatChange(uid));
            assertTrue(cloneViaBundle(options).testRequireCompatChange(uid));

            // Verify both enabled and disabled
            options.setRequireCompatChange(BroadcastOptions.CHANGE_ALWAYS_ENABLED, true);
            assertTrue(options.testRequireCompatChange(uid));
            assertTrue(cloneViaBundle(options).testRequireCompatChange(uid));
            options.setRequireCompatChange(BroadcastOptions.CHANGE_ALWAYS_ENABLED, false);
            assertFalse(options.testRequireCompatChange(uid));
            assertFalse(cloneViaBundle(options).testRequireCompatChange(uid));

            // And back to default passes
            options.clearRequireCompatChange();
            assertTrue(options.testRequireCompatChange(uid));
            assertTrue(cloneViaBundle(options).testRequireCompatChange(uid));
        });
    }

    @Test
    public void testRequireCompatChange_enabled_success() {
        final BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setRequireCompatChange(BroadcastOptions.CHANGE_ALWAYS_ENABLED, true);
        assertBroadcastSuccess(options);
    }

    @Test
    public void testRequireCompatChange_enabled_failure() {
        final BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setRequireCompatChange(BroadcastOptions.CHANGE_ALWAYS_DISABLED, true);
        assertBroadcastFailure(options);
    }

    @Test
    public void testRequireCompatChange_disabled_success() {
        final BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setRequireCompatChange(BroadcastOptions.CHANGE_ALWAYS_DISABLED, false);
        assertBroadcastSuccess(options);
    }

    @Test
    public void testRequireCompatChange_disabled_failure() {
        final BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setRequireCompatChange(BroadcastOptions.CHANGE_ALWAYS_ENABLED, false);
        assertBroadcastFailure(options);
    }
}
