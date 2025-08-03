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

package android.voiceinteraction.cts.testcore;

import android.app.DirectAction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.util.Log;
import android.voiceinteraction.common.Utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ThrowingRunnable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A control class to help to perform operations between test case and testing
 * VoiceInteractionSession.
 */
public class VoiceInteractionSessionControl {
    private static final String TAG = VoiceInteractionSessionControl.class.getSimpleName();

    private @NonNull Context mContext;
    private @Nullable RemoteCallback mControl;

    protected VoiceInteractionSessionControl(Context context) {
        mContext = context;
    }

    protected void startVoiceInteractionSession(Intent intent) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        final RemoteCallback callback = new RemoteCallback((result) -> {
            mControl = result.getParcelable(Utils.VOICE_INTERACTION_KEY_CONTROL);
            latch.countDown();
        });

        intent.putExtra(Utils.VOICE_INTERACTION_KEY_CALLBACK, callback);

        Log.v(TAG, "startVoiceInteractionSession(): " + intent);
        mContext.startActivity(intent);

        final long timeoutMs = Utils.getAdjustedOperationTimeoutMs();
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new TimeoutException("activity not started in " + timeoutMs
                    + "ms");
        }
    }

    public void stopVoiceInteractionSession() throws Exception {
        executeCommand(Utils.VOICE_INTERACTION_SESSION_CMD_FINISH,
                null /*directAction*/, null /*arguments*/, null /*postActionCommand*/);
    }

    @NonNull
    protected Bundle executeCommand(@NonNull String action, @Nullable DirectAction directAction,
            @Nullable Bundle arguments, @Nullable ThrowingRunnable postActionCommand)
            throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        final Bundle result = new Bundle();

        final RemoteCallback callback = new RemoteCallback((b) -> {
            result.putAll(b);
            latch.countDown();
        });

        final Bundle command = new Bundle();
        command.putString(Utils.VOICE_INTERACTION_KEY_COMMAND, action);
        command.putParcelable(Utils.VOICE_INTERACTION_DIRECT_ACTIONS_KEY_ACTION, directAction);
        command.putBundle(Utils.VOICE_INTERACTION_KEY_ARGUMENTS, arguments);
        command.putParcelable(Utils.VOICE_INTERACTION_KEY_CALLBACK, callback);

        Log.v(TAG, "executeCommand(): action=" + action + " command="
                + Utils.toBundleString(command));
        mControl.sendResult(command);

        if (postActionCommand != null) {
            Log.v(TAG, "Executing post-action command for " + action);
            postActionCommand.run();
        }

        final long timeoutMs = Utils.getAdjustedOperationTimeoutMs();
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new TimeoutException(
                    "result not received in " + timeoutMs + "ms");
        }
        Log.v(TAG, "returning " + Utils.toBundleString(result));
        return result;
    }
}
