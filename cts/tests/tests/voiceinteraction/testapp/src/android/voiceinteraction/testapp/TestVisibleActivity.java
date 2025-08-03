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

package android.voiceinteraction.testapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteCallback;
import android.util.Log;
import android.voiceinteraction.common.Utils;

import androidx.annotation.NonNull;

/**
 * The activity is used to test visible activity mechanism of VoiceInteractionSession.
 */
public final class TestVisibleActivity extends Activity {

    private static final String TAG = TestVisibleActivity.class.getSimpleName();

    // For debugging the visible activity mechanism of VoiceInteractionSession, we need to
    // override these activity lifecycle stage.
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, " in onCreate");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, " in onStart");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, " in onRestart");
    }

    @Override
    protected void onResume() {
        super.onResume();

        final Intent intent = getIntent();
        Log.v(TAG, "onResume: " + intent);
        final Bundle args = intent.getExtras();
        final RemoteCallback callBack = args.getParcelable(Utils.VOICE_INTERACTION_KEY_CALLBACK);

        final RemoteCallback control = new RemoteCallback((cmdArgs) -> {
            final String command = cmdArgs.getString(Utils.VOICE_INTERACTION_KEY_COMMAND);
            Log.v(TAG, "on remote callback: command=" + command);
            switch (command) {
                case Utils.VOICE_INTERACTION_ACTIVITY_CMD_FINISH: {
                    final RemoteCallback commandCallback = cmdArgs.getParcelable(
                            Utils.VOICE_INTERACTION_KEY_CALLBACK);
                    doFinish(commandCallback);
                } break;
                case Utils.VOICE_INTERACTION_ACTIVITY_CMD_CRASH: {
                    final RemoteCallback commandCallback = cmdArgs.getParcelable(
                            Utils.VOICE_INTERACTION_KEY_CALLBACK);
                    doCrash(commandCallback);
                } break;
            }
        });

        final Bundle result = new Bundle();
        result.putParcelable(Utils.VOICE_INTERACTION_KEY_CONTROL, control);
        result.putInt(Utils.VOICE_INTERACTION_KEY_TASKID, getTaskId());
        Log.v(TAG, "onResume(): result=" + Utils.toBundleString(result));
        callBack.sendResult(result);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, " in onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, " in onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, " in onDestroy");
    }

    private void doFinish(@NonNull RemoteCallback callback) {
        finish();
        final Bundle result = new Bundle();
        result.putBoolean(Utils.VISIBLE_ACTIVITY_KEY_RESULT, true);
        Log.v(TAG, "doFinish(): " + Utils.toBundleString(result));
        callback.sendResult(result);
    }

    private void doCrash(@NonNull RemoteCallback callback) {
        final Bundle result = new Bundle();
        result.putBoolean(Utils.VISIBLE_ACTIVITY_KEY_RESULT, true);
        Log.v(TAG, "doCrash(): " + Utils.toBundleString(result));
        callback.sendResult(result);
        Log.v(TAG, "Crash itself. Pid: " + Process.myPid());
        Process.killProcess(Process.myPid());
    }
}
