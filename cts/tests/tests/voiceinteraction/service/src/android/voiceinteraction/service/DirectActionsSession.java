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
 * See the License for the specific language governing permissions andf
 * limitations under the License.
 */

package android.voiceinteraction.service;

import android.app.DirectAction;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.RemoteCallback;
import android.service.voice.VisibleActivityInfo;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;
import android.voiceinteraction.common.Utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Sessions for testing direct action related functionality
 */
public class DirectActionsSession extends VoiceInteractionSession {
    private static final String TAG = DirectActionsSession.class.getSimpleName();

    private final ReentrantLock mLock = new ReentrantLock();
    private final Condition mCondition = mLock.newCondition();

    // GuardedBy("mLock")
    private @Nullable
    ActivityId mActivityId;

    // GuardedBy("mLock")
    private boolean mActionsInvalidated;

    private final VisibleActivityCallback mCallback = new VisibleActivityCallback() {
        @Override
        public void onVisible(VisibleActivityInfo activityInfo) {
            VisibleActivityCallback.super.onVisible(activityInfo);
            Log.v(TAG, "onVisible : " + activityInfo);
            broadcastVisibleActivityCallback(Utils.VISIBLE_ACTIVITY_CALLBACK_ONVISIBLE_INTENT,
                    activityInfo.getActivityId().getTaskId());
        }

        @Override
        public void onInvisible(ActivityId activityId) {
            VisibleActivityCallback.super.onInvisible(activityId);
            Log.v(TAG, "onInvisible : " + activityId);
            broadcastVisibleActivityCallback(Utils.VISIBLE_ACTIVITY_CALLBACK_ONINVISIBLE_INTENT,
                    activityId.getTaskId());
        }
    };

    public DirectActionsSession(@NonNull Context context) {
        super(context);
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        if (args == null) {
            Log.e("TODO", "onshow() received null args");
            return;
        }
        final RemoteCallback callback = args.getParcelable(Utils.VOICE_INTERACTION_KEY_CALLBACK);

        final RemoteCallback control = new RemoteCallback((cmdArgs) -> {
            final String command = cmdArgs.getString(Utils.VOICE_INTERACTION_KEY_COMMAND);
            Log.v(TAG, "on remote callback: command=" + command);
            final RemoteCallback commandCallback = cmdArgs.getParcelable(
                    Utils.VOICE_INTERACTION_KEY_CALLBACK);
            switch (command) {
                case Utils.DIRECT_ACTIONS_SESSION_CMD_PERFORM_ACTION: {
                    executeWithAssist((result) -> performDirectAction(cmdArgs, result),
                            commandCallback);
                } break;
                case Utils.DIRECT_ACTIONS_SESSION_CMD_PERFORM_ACTION_CANCEL: {
                    executeWithAssist((result) -> performDirectActionAndCancel(cmdArgs, result),
                            commandCallback);
                } break;
                case Utils.DIRECT_ACTIONS_SESSION_CMD_GET_ACTIONS: {
                    executeWithAssist(this::getDirectActions, commandCallback);
                } break;
                case Utils.VOICE_INTERACTION_SESSION_CMD_FINISH: {
                    executeWithAssist(this::performHide, commandCallback);
                } break;
                case Utils.DIRECT_ACTIONS_SESSION_CMD_DETECT_ACTIONS_CHANGED: {
                    executeWithAssist(this::detectDirectActionsInvalidated, commandCallback);
                } break;
                case Utils.VISIBLE_ACTIVITY_CMD_REGISTER_CALLBACK: {
                    executeWithAssist(
                            (result) -> registerVisibleActivityCallbackInternal(cmdArgs, result),
                            commandCallback);
                } break;
                case Utils.VISIBLE_ACTIVITY_CMD_UNREGISTER_CALLBACK: {
                    executeWithAssist(this::unregisterVisibleActivityCallbackInternal,
                            commandCallback);
                } break;
            }
        });

        final Bundle result = new Bundle();
        result.putParcelable(Utils.VOICE_INTERACTION_KEY_CONTROL, control);
        callback.sendResult(result);
    }

    @Override
    public void onHandleAssist(AssistState state) {
        Log.v(TAG, "onHandleAssist");
        if (state.getIndex() == 0) {
            mLock.lock();
            try {
                mActivityId = state.getActivityId();
                mCondition.signalAll();
            } finally {
                mLock.unlock();
            }
        }
    }

    @Override
    public void onDirectActionsInvalidated(ActivityId activityId) {
         mLock.lock();
         try {
            mActionsInvalidated = true;
            mCondition.signalAll();
        } finally {
             mLock.unlock();
         }
    }

    private void executeWithAssist(@Nullable Consumer<Bundle> command,
            @NonNull RemoteCallback callback) {
        mLock.lock();
        try {
            if (mActivityId == null) {
                Utils.await(mCondition);
            }
            final Bundle result = new Bundle();
            if (mActivityId != null) {
                command.accept(result);
                callback.sendResult(result);
            } else {
                callback.sendResult(result);
            }
        } finally {
            mLock.unlock();
        }
    }

    private void getDirectActions(@NonNull Bundle outResult) {
        final ArrayList<DirectAction> actions = new ArrayList<>();

        final CountDownLatch latch = new CountDownLatch(1);

        mLock.lock();
        try {
            requestDirectActions(mActivityId, null, AsyncTask.THREAD_POOL_EXECUTOR, (b) -> {
                actions.addAll(b);
                latch.countDown();
            });
        } finally {
            mLock.unlock();
        }
        Utils.await(latch);

        outResult.putParcelableArrayList(Utils.DIRECT_ACTIONS_KEY_RESULT, actions);
        Log.v(TAG, "getDirectActions(): " + Utils.toBundleString(outResult));
    }

    private void performDirectAction(@NonNull Bundle args, @NonNull Bundle outResult) {
        final DirectAction action = args.getParcelable(
                Utils.VOICE_INTERACTION_DIRECT_ACTIONS_KEY_ACTION);
        final Bundle arguments = args.getBundle(Utils.VOICE_INTERACTION_KEY_ARGUMENTS);

        final Bundle result = new Bundle();
        final CountDownLatch latch = new CountDownLatch(1);
        performDirectAction(action, arguments, null, AsyncTask.THREAD_POOL_EXECUTOR, (b) -> {
            result.putAll(b);
            latch.countDown();
        });
        Utils.await(latch);

        outResult.putBundle(Utils.DIRECT_ACTIONS_KEY_RESULT, result);
        Log.v(TAG, "performDirectAction(): " + Utils.toBundleString(outResult));
    }

    private void performDirectActionAndCancel(@NonNull Bundle args, @NonNull Bundle outResult) {
        final DirectAction action = args.getParcelable(
                Utils.VOICE_INTERACTION_DIRECT_ACTIONS_KEY_ACTION);
        final Bundle arguments = args.getBundle(Utils.VOICE_INTERACTION_KEY_ARGUMENTS);
        final Bundle result = new Bundle();

        final CountDownLatch cancelLatch = new CountDownLatch(1);
        final RemoteCallback cancelCallback = new RemoteCallback((b) -> {
            result.clear();
            result.putAll(b);
            cancelLatch.countDown();
        });
        arguments.putParcelable(Utils.DIRECT_ACTIONS_KEY_CANCEL_CALLBACK, cancelCallback);

        final CancellationSignal cancellationSignal = new CancellationSignal();

        final CountDownLatch resultLatch = new CountDownLatch(1);

        performDirectAction(action, arguments, cancellationSignal,
                AsyncTask.THREAD_POOL_EXECUTOR, (b) ->
            resultLatch.countDown()
        );

        Utils.await(resultLatch);

        cancellationSignal.cancel();

        Utils.await(cancelLatch);

        outResult.putBundle(Utils.DIRECT_ACTIONS_KEY_RESULT, result);
        Log.v(TAG, "performDirectActionAndCancel(): " + Utils.toBundleString(outResult));
    }

    private void detectDirectActionsInvalidated(@NonNull Bundle outResult) {
        mLock.lock();
        try {
            if (!mActionsInvalidated) {
                Utils.await(mCondition);
            }
            outResult.putBoolean(Utils.DIRECT_ACTIONS_KEY_RESULT, mActionsInvalidated);
            Log.v(TAG, "detectDirectActionsInvalidated(): " + Utils.toBundleString(outResult));
            mActionsInvalidated = false;
        } finally {
            mLock.unlock();
        }
    }

    private void performHide(@NonNull Bundle outResult) {
        finish();
        outResult.putBoolean(Utils.DIRECT_ACTIONS_KEY_RESULT, true);
        Log.v(TAG, "performHide(): " + Utils.toBundleString(outResult));
    }

    private void registerVisibleActivityCallbackInternal(@NonNull Bundle args,
            @NonNull Bundle outResult) {
        Log.v(TAG, "registerVisibleActivityCallbackInternal");
        final Bundle arguments = args.getBundle(Utils.VOICE_INTERACTION_KEY_ARGUMENTS);
        final int callbackParameter = arguments.getInt(
                Utils.VISIBLE_ACTIVITY_CMD_REGISTER_CALLBACK);
        Log.v(TAG, "callbackParameter : " + callbackParameter);

        mLock.lock();
        try {
            switch (callbackParameter) {
                case Utils.VISIBLE_ACTIVITY_CALLBACK_REGISTER_NORMAL:
                    registerVisibleActivityCallback(Executors.newSingleThreadExecutor(), mCallback);
                    break;
                case Utils.VISIBLE_ACTIVITY_CALLBACK_REGISTER_WITHOUT_EXECUTOR:
                    registerVisibleActivityCallback(/* executor */ null, mCallback);
                    break;
                case Utils.VISIBLE_ACTIVITY_CALLBACK_REGISTER_WITHOUT_CALLBACK:
                    registerVisibleActivityCallback(
                            Executors.newSingleThreadExecutor(), /* callback */ null);
                    break;
            }
            outResult.putBoolean(Utils.VISIBLE_ACTIVITY_KEY_RESULT, true);
        } catch (Throwable t) {
            outResult.putSerializable(Utils.VISIBLE_ACTIVITY_KEY_RESULT, t);
        } finally {
            mLock.unlock();
        }
        Log.v(TAG, "registerVisibleActivityCallbackInternal(): " + Utils.toBundleString(outResult));
    }

    private void unregisterVisibleActivityCallbackInternal(@NonNull Bundle outResult) {
        Log.v(TAG, "unregisterVisibleActivityCallbackInternal");
        mLock.lock();
        try {
            unregisterVisibleActivityCallback(mCallback);
        } finally {
            mLock.unlock();
        }
        outResult.putBoolean(Utils.VISIBLE_ACTIVITY_KEY_RESULT, true);
        Log.v(TAG,
                "unregisterVisibleActivityCallbackInternal(): " + Utils.toBundleString(outResult));
    }

    private void broadcastVisibleActivityCallback(String intentName, int taskId) {
        final Intent intent = new Intent(intentName)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_REGISTERED_ONLY)
                .putExtra(Utils.VOICE_INTERACTION_KEY_TASKID, taskId);
        Log.v(TAG, "broadcast intent = " + intent + ", taskId = " + taskId);
        getContext().sendBroadcast(intent);
    }
}
