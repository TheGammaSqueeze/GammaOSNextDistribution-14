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

package android.server.wm.activity;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerStateHelper;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.FeatureUtil;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Presubmit
public class StartActivityAsUserTests {
    static final String EXTRA_CALLBACK = "callback";
    static final String KEY_USER_ID = "user id";

    private static final String PACKAGE = "android.server.wm.cts";
    private static final String CLASS = "android.server.wm.activity.StartActivityAsUserActivity";
    private static final String USER_CREATION_OVERRIDE = "debug.user.creation_override";
    private static final int INVALID_STACK = -1;
    private static final boolean SUPPORTS_MULTIPLE_USERS = UserManager.supportsMultipleUsers();

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final ActivityManager mAm = mContext.getSystemService(ActivityManager.class);

    private static int sSecondUserId;
    private static boolean sCloneProfileSupported = true;

    private WindowManagerStateHelper mAmWmState = new WindowManagerStateHelper();
    private static final String TAG = StartActivityAsUserTests.class.getSimpleName();

    @BeforeClass
    public static void createSecondUser() {
        if (!SUPPORTS_MULTIPLE_USERS) {
            return;
        }

        if (FeatureUtil.isAutomotive()) {
            if (!Build.isDebuggable()) {
                // "debug.user.creation_override" only works for debuggable builds. Skip the tests
                // for non-debuggable builds in Automotive.
                sCloneProfileSupported = false;
                return;
            }
            // In Automotive, temporarily remove restrictions on user-creation.
            runShellCommand("setprop " + USER_CREATION_OVERRIDE + " true");
        }

        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final String output = runShellCommand(
                "pm create-user --user-type android.os.usertype.profile.CLONE --profileOf "
                        + context.getUserId() + " user2");

        try {
            sSecondUserId = Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
        } catch (StringIndexOutOfBoundsException |  NumberFormatException e) {
            Log.e(TAG, "Failed to create user of type android.os.usertype.profile.CLONE");
        }

        if (sSecondUserId == 0) {
            // A profile user was not successfully created.
            // This will fail the tests in checkMultipleUsersNotSupportedOrSecondUserCreated().
            return;
        }
        runShellCommand("pm install-existing --user " + sSecondUserId + " android.server.wm.cts");
        runShellCommand("am start-user -w " + sSecondUserId);
    }

    @AfterClass
    public static void removeSecondUser() {
        if (sSecondUserId == 0) {
            return;
        }
        runShellCommand("am stop-user -w -f " + sSecondUserId);
        runShellCommand("pm remove-user " + sSecondUserId);
        sSecondUserId = 0;
        if (FeatureUtil.isAutomotive() && Build.isDebuggable()) {
            runShellCommand("setprop " + USER_CREATION_OVERRIDE + " false");
        }
    }

    @Before
    public void checkMultipleUsersNotSupportedOrSecondUserCreated() {
        assumeTrue(SUPPORTS_MULTIPLE_USERS);
        assumeTrue(sCloneProfileSupported);
        assertThat(sSecondUserId).isNotEqualTo(0);
    }

    @Test
    public void startActivityValidUser() throws Throwable {
        verifyStartActivityAsValidUser(false /* withOptions */);
    }

    @Test
    public void startActivityInvalidUser() {
        verifyStartActivityAsInvalidUser(false /* withOptions */);
    }

    @Test
    public void startActivityAsValidUserWithOptions() throws Throwable {
        verifyStartActivityAsValidUser(true /* withOptions */);
    }

    @Test
    public void startActivityAsInvalidUserWithOptions() {
        verifyStartActivityAsInvalidUser(true /* withOptions */);
    }

    private void verifyStartActivityAsValidUser(boolean withOptions) throws Throwable {
        int[] secondUser = {-1};
        CountDownLatch latch = new CountDownLatch(1);
        RemoteCallback cb = new RemoteCallback((Bundle result) -> {
            secondUser[0] = result.getInt(KEY_USER_ID);
            latch.countDown();
        });

        final Intent intent = new Intent(mContext, StartActivityAsUserActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_CALLBACK, cb);
        UserHandle secondUserHandle = UserHandle.of(sSecondUserId);

        runWithShellPermissionIdentity(() -> {
            if (withOptions) {
                mContext.startActivityAsUser(intent, ActivityOptions.makeBasic().toBundle(),
                        secondUserHandle);
            } else {
                mContext.startActivityAsUser(intent, secondUserHandle);
            }
        });

        latch.await(5, TimeUnit.SECONDS);
        assertThat(secondUser[0]).isEqualTo(sSecondUserId);

        // The StartActivityAsUserActivity calls finish() in onCreate and here waits for the
        // activity removed to prevent impacting other tests.
        mAmWmState.waitForActivityRemoved(intent.getComponent());
    }

    private void verifyStartActivityAsInvalidUser(boolean withOptions) {
        UserHandle secondUserHandle = UserHandle.of(sSecondUserId * 100);
        int[] stackId = {-1};

        final Intent intent = new Intent(mContext, StartActivityAsUserActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        runWithShellPermissionIdentity(() -> {
            if (withOptions) {
                mContext.startActivityAsUser(intent, ActivityOptions.makeBasic().toBundle(),
                        secondUserHandle);
            } else {
                mContext.startActivityAsUser(intent, secondUserHandle);
            }
            WindowManagerState amState = mAmWmState;
            amState.computeState();
            ComponentName componentName = ComponentName.createRelative(PACKAGE, CLASS);
            stackId[0] = amState.getRootTaskIdByActivity(componentName);
        });

        assertThat(stackId[0]).isEqualTo(INVALID_STACK);
    }
}
