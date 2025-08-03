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

package android.voiceinteraction.cts;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertThrows;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteCallback;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;
import android.voiceinteraction.common.Utils;
import android.voiceinteraction.cts.testcore.VoiceInteractionSessionControl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests for reliable visible activity lookup related functions.
 */
@AppModeFull(reason = "DirectActionsTest is enough")
public class VoiceInteractionSessionVisibleActivityTest extends AbstractVoiceInteractionTestCase {
    private static final String TAG =
            VoiceInteractionSessionVisibleActivityTest.class.getSimpleName();

    private static final int INVALID_TASK_ID = -1;

    @NonNull private final SessionControl mSessionControl = new SessionControl();
    @NonNull private final ActivityControl mActivityControl = new ActivityControl();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Test
    public void testVoiceInteractionSession_registerVisibleActivityCallback_beforeOnCreate()
            throws Throwable {
        assertThrows(IllegalStateException.class,
                () -> new VoiceInteractionSession(mContext,
                        mHandler).registerVisibleActivityCallback(mock(Executor.class),
                        mock(VoiceInteractionSession.VisibleActivityCallback.class)));
    }

    @Test
    public void testVoiceInteractionSession_registerVisibleActivityCallback_withoutExecutor()
            throws Throwable {
        // Start a VoiceInteractionSession and make sure the session has been created.
        mSessionControl.startVoiceInteractionSession();

        try {
            // Register the VisibleActivityCallback with null executor, it will cause
            // NullPointerException.
            final Bundle result = mSessionControl.registerVisibleActivityCallback(
                    Utils.VISIBLE_ACTIVITY_CALLBACK_REGISTER_WITHOUT_EXECUTOR);

            // Verify if getting the NullPointerException.
            assertThat(result).isNotNull();
            assertThat(
                    result.getSerializable(Utils.VISIBLE_ACTIVITY_KEY_RESULT).getClass()).isEqualTo(
                    NullPointerException.class);
        } finally {
            mSessionControl.unregisterVisibleActivityCallback();
            mSessionControl.stopVoiceInteractionSession();
        }
    }

    @Test
    public void testVoiceInteractionSession_registerVisibleActivityCallback_withoutCallback()
            throws Throwable {
        // Start a VoiceInteractionSession and make sure the session has been created.
        mSessionControl.startVoiceInteractionSession();

        try {
            // Register the VisibleActivityCallback with null callback, it will cause
            // NullPointerException.
            final Bundle result = mSessionControl.registerVisibleActivityCallback(
                    Utils.VISIBLE_ACTIVITY_CALLBACK_REGISTER_WITHOUT_CALLBACK);

            // Verify if getting the NullPointerException.
            assertThat(result).isNotNull();
            assertThat(
                    result.getSerializable(Utils.VISIBLE_ACTIVITY_KEY_RESULT).getClass()).isEqualTo(
                    NullPointerException.class);
        } finally {
            mSessionControl.unregisterVisibleActivityCallback();
            mSessionControl.stopVoiceInteractionSession();
        }
    }

    @Test
    public void testVoiceInteractionSession_unregisterVisibleActivityCallback_withoutCallback()
            throws Throwable {
        assertThrows(NullPointerException.class,
                () -> new VoiceInteractionSession(mContext,
                        mHandler).unregisterVisibleActivityCallback(/* callback= */ null));
    }

    @Test
    public void testReceiveVisibleActivityCallbackAfterStartNewOrFinishActivity() throws Exception {
        // Start a VoiceInteractionSession and make sure the session has been created.
        mSessionControl.startVoiceInteractionSession();

        try {
            registerVisibleActivityCallback();

            // After starting a new activity, the VisibleActivityCallback.onVisible should be
            // called with this new activity.
            Intent visibleResult = getResultOnPerformActivityChange(
                    Utils.ACTIVITY_NEW, /* expectedVisibleResult= */ true);
            assertThat(visibleResult).isNotNull();
            assertThat(visibleResult.getIntExtra(Utils.VOICE_INTERACTION_KEY_TASKID,
                    INVALID_TASK_ID)).isEqualTo(mActivityControl.mTaskId);

            // After finishing an activity, the VisibleActivityCallback.onInVisible should be
            // called with this finishing activity.
            Intent invisibleResult = getResultOnPerformActivityChange(
                    Utils.ACTIVITY_FINISH, /* expectedVisibleResult= */ false);
            assertThat(invisibleResult).isNotNull();
            assertThat(invisibleResult.getIntExtra(Utils.VOICE_INTERACTION_KEY_TASKID,
                    INVALID_TASK_ID)).isEqualTo(mActivityControl.mTaskId);
        } finally {
            mSessionControl.unregisterVisibleActivityCallback();
            mSessionControl.stopVoiceInteractionSession();
        }
    }

    @Test
    public void testReceiveVisibleActivityCallbackAfterCrashActivity() throws Exception {
        // Start a VoiceInteractionSession and make sure the session has been created.
        mSessionControl.startVoiceInteractionSession();

        try {
            registerVisibleActivityCallback();

            // After starting a new activity, the VisibleActivityCallback.onVisible should be
            // called with this new activity.
            Intent visibleResult = getResultOnPerformActivityChange(
                    Utils.ACTIVITY_NEW, /* expectedVisibleResult= */ true);
            assertThat(visibleResult).isNotNull();
            assertThat(visibleResult.getIntExtra(Utils.VOICE_INTERACTION_KEY_TASKID,
                    INVALID_TASK_ID)).isEqualTo(mActivityControl.mTaskId);

            // After crashing an activity, the VisibleActivityCallback.onInVisible should be
            // called with this crashing activity.
            Intent invisibleResult = getResultOnPerformActivityChange(
                    Utils.ACTIVITY_CRASH, /* expectedVisibleResult= */ false);
            assertThat(invisibleResult).isNotNull();
            assertThat(invisibleResult.getIntExtra(Utils.VOICE_INTERACTION_KEY_TASKID,
                    INVALID_TASK_ID)).isEqualTo(mActivityControl.mTaskId);
        } finally {
            mSessionControl.unregisterVisibleActivityCallback();
            mSessionControl.stopVoiceInteractionSession();
        }
    }

    private Intent getResultOnPerformActivityChange(int activityChange,
            boolean expectedVisibleResult) throws Exception {
        // Sleep one second to reduce the impact of changing activity state.
        Thread.sleep(1000);

        final BlockingBroadcastReceiver onVisibleReceiver = new BlockingBroadcastReceiver(
                mContext, Utils.VISIBLE_ACTIVITY_CALLBACK_ONVISIBLE_INTENT);
        onVisibleReceiver.register();

        final BlockingBroadcastReceiver onInvisibleReceiver = new BlockingBroadcastReceiver(
                mContext, Utils.VISIBLE_ACTIVITY_CALLBACK_ONINVISIBLE_INTENT);
        onInvisibleReceiver.register();

        Log.v(TAG, "performActivityChange : " + activityChange);
        switch (activityChange) {
            case Utils.ACTIVITY_NEW:
                // Start a new activity
                mActivityControl.startActivity();
                break;
            case Utils.ACTIVITY_FINISH:
                // Finish an activity
                mActivityControl.finishActivity();
                break;
            case Utils.ACTIVITY_CRASH:
                // Crash an activity
                mActivityControl.crashActivity();
                break;
        }

        final long timeoutMs = Utils.getAdjustedOperationTimeoutMs();
        final Intent onVisibleIntent = onVisibleReceiver.awaitForBroadcast(
                timeoutMs);
        Log.v(TAG, "onVisibleIntent : " + onVisibleIntent);
        onVisibleReceiver.unregisterQuietly();

        final Intent onInvisibleIntent = onInvisibleReceiver.awaitForBroadcast(
                timeoutMs);
        Log.v(TAG, "onInvisibleIntent : " + onVisibleIntent);
        onInvisibleReceiver.unregisterQuietly();

        if (expectedVisibleResult) {
            return onVisibleIntent;
        }
        return onInvisibleIntent;
    }

    private void registerVisibleActivityCallback() throws Exception {
        // Register the VisibleActivityCallback first, the VisibleActivityCallback.onVisible
        // or VisibleActivityCallback.onInvisible that will be called when visible activities
        // have been changed.
        final BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(mContext,
                Utils.VISIBLE_ACTIVITY_CALLBACK_ONVISIBLE_INTENT);
        receiver.register();

        // Register the VisibleActivityCallback and the VisibleActivityCallback.onVisible will
        // be called immediately with current visible activities.
        mSessionControl.registerVisibleActivityCallback(
                Utils.VISIBLE_ACTIVITY_CALLBACK_REGISTER_NORMAL);

        // Verify if the VisibleActivityCallback.onVisible has been called.
        final long timeoutMs = Utils.getAdjustedOperationTimeoutMs();
        Intent intent = receiver.awaitForBroadcast(timeoutMs);
        receiver.unregisterQuietly();

        assertThat(intent).isNotNull();
        assertThat(intent.getIntExtra(Utils.VOICE_INTERACTION_KEY_TASKID,
                INVALID_TASK_ID)).isGreaterThan(INVALID_TASK_ID);
    }

    private final class SessionControl extends VoiceInteractionSessionControl {

        SessionControl() {
            super(mContext);
        }

        private void startVoiceInteractionSession() throws Exception {
            final Intent intent = new Intent();
            intent.putExtra(Utils.VOICE_INTERACTION_KEY_CLASS,
                    "android.voiceinteraction.service.DirectActionsSession");
            intent.setClassName("android.voiceinteraction.service",
                    "android.voiceinteraction.service.VoiceInteractionMain");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startVoiceInteractionSession(intent);
        }

        Bundle registerVisibleActivityCallback(int callbackParameter) throws Exception {
            final Bundle arguments = new Bundle();
            arguments.putInt(Utils.VISIBLE_ACTIVITY_CMD_REGISTER_CALLBACK, callbackParameter);
            final Bundle result = executeCommand(
                    Utils.VISIBLE_ACTIVITY_CMD_REGISTER_CALLBACK, null /*directAction*/,
                    arguments, null /*postActionCommand*/);
            return result;
        }

        boolean unregisterVisibleActivityCallback() throws Exception {
            final Bundle result = executeCommand(
                    Utils.VISIBLE_ACTIVITY_CMD_UNREGISTER_CALLBACK, null /*directAction*/,
                    null /*arguments*/, null /*postActionCommand*/);
            return result.getBoolean(Utils.VISIBLE_ACTIVITY_KEY_RESULT);
        }
    }

    // TODO: (b/245720308) Refactor ActivityControl with DirectActionsTest
    private final class ActivityControl {

        @Nullable private RemoteCallback mControl;
        int mTaskId;

        void startActivity() throws Exception {
            final CountDownLatch latch = new CountDownLatch(1);

            final RemoteCallback callback = new RemoteCallback((result) -> {
                Log.v(TAG, "ActivityControl: testapp called the callback: "
                        + Utils.toBundleString(result));
                mControl = result.getParcelable(Utils.VOICE_INTERACTION_KEY_CONTROL);
                mTaskId = result.getInt(Utils.VOICE_INTERACTION_KEY_TASKID);
                latch.countDown();
            });

            final Intent intent = new Intent()
                    .setAction("android.intent.action.TestVisibleActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(Utils.VOICE_INTERACTION_KEY_CALLBACK, callback);
            if (mContext.getPackageManager().isInstantApp()) {
                // Override app-links domain verification.
                runShellCommand(
                        String.format(
                                "pm set-app-links-user-selection --user cur --package %1$s true"
                                        + " %1$s",
                                Utils.TEST_APP_PACKAGE));
            } else {
                intent.setPackage(Utils.TEST_APP_PACKAGE);
            }

            Log.v(TAG, "startActivity: " + intent);
            mContext.startActivity(intent);

            final long timeoutMs = Utils.getAdjustedOperationTimeoutMs();
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException(
                        "activity not started in " + timeoutMs + "ms");
            }
        }

        void finishActivity() throws Exception {
            executeRemoteCommand(Utils.VOICE_INTERACTION_ACTIVITY_CMD_FINISH);
        }

        void crashActivity() throws Exception {
            executeRemoteCommand(Utils.VOICE_INTERACTION_ACTIVITY_CMD_CRASH);
        }

        @NonNull Bundle executeRemoteCommand(@NonNull String action) throws Exception {
            final Bundle result = new Bundle();

            final CountDownLatch latch = new CountDownLatch(1);

            final RemoteCallback callback = new RemoteCallback((b) -> {
                Log.v(TAG, "executeRemoteCommand(): received result from '" + action + "': "
                        + Utils.toBundleString(b));
                if (b != null) {
                    result.putAll(b);
                }
                latch.countDown();
            });

            final Bundle command = new Bundle();
            command.putString(Utils.VOICE_INTERACTION_KEY_COMMAND, action);
            command.putParcelable(Utils.VOICE_INTERACTION_KEY_CALLBACK, callback);

            Log.v(TAG, "executeRemoteCommand(): sending command for '" + action + "'");
            if (mControl != null) {
                mControl.sendResult(command);
            }

            final long timeoutMs = Utils.getAdjustedOperationTimeoutMs();
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException(
                        "result not received in " + timeoutMs + "ms");
            }
            return result;
        }
    }
}
