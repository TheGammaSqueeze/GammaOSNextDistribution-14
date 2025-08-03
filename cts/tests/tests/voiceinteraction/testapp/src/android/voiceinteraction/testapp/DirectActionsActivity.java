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

package android.voiceinteraction.testapp;

import android.app.Activity;
import android.app.DirectAction;
import android.app.VoiceInteractor;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.RemoteCallback;
import android.util.Log;
import android.voiceinteraction.common.Utils;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.PollingCheck;

import com.google.common.truth.Truth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Activity to test direct action behaviors.
 */
public final class DirectActionsActivity extends Activity {

    private static final String TAG = DirectActionsActivity.class.getSimpleName();

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
                case Utils.DIRECT_ACTIONS_ACTIVITY_CMD_DESTROYED_INTERACTOR: {
                    final RemoteCallback commandCallback = cmdArgs.getParcelable(
                            Utils.VOICE_INTERACTION_KEY_CALLBACK);
                    detectDestroyedInteractor(commandCallback);
                } break;
                case Utils.VOICE_INTERACTION_ACTIVITY_CMD_FINISH: {
                    final RemoteCallback commandCallback = cmdArgs.getParcelable(
                            Utils.VOICE_INTERACTION_KEY_CALLBACK);
                    doFinish(commandCallback);
                } break;
                case Utils.DIRECT_ACTIONS_ACTIVITY_CMD_INVALIDATE_ACTIONS: {
                    final RemoteCallback commandCallback = cmdArgs.getParcelable(
                            Utils.VOICE_INTERACTION_KEY_CALLBACK);
                    invalidateDirectActions(commandCallback);
                } break;
                case Utils.DIRECT_ACTIONS_ACTIVITY_CMD_GET_PACKAGE_NAME: {
                    final RemoteCallback commandCallback = cmdArgs.getParcelable(
                            Utils.VOICE_INTERACTION_KEY_CALLBACK);
                    getPackageName(commandCallback);
                } break;
                case Utils.DIRECT_ACTIONS_ACTIVITY_CMD_GET_PACKAGE_INFO: {
                    final RemoteCallback commandCallback = cmdArgs.getParcelable(
                            Utils.VOICE_INTERACTION_KEY_CALLBACK);
                    getPackageInfo(commandCallback);
                } break;
            }
        });

        final Bundle result = new Bundle();
        result.putParcelable(Utils.VOICE_INTERACTION_KEY_CONTROL, control);
        Log.v(TAG, "onResume(): result=" + Utils.toBundleString(result));
        callBack.sendResult(result);
    }

    @Override
    public void onGetDirectActions(@NonNull CancellationSignal cancellationSignal,
            @NonNull Consumer<List<DirectAction>> callback) {
        if (getVoiceInteractor() == null) {
            Log.e(TAG, "onGetDirectActions(): no voice interactor");
            callback.accept(Collections.emptyList());
            return;
        }
        Log.v(TAG, "onGetDirectActions()");
        final DirectAction action = new DirectAction.Builder(Utils.DIRECT_ACTIONS_ACTION_ID)
                .setExtras(Utils.DIRECT_ACTIONS_ACTION_EXTRAS)
                .setLocusId(Utils.DIRECT_ACTIONS_LOCUS_ID)
                .build();

        final ArrayList<DirectAction> actions = new ArrayList<>();
        actions.add(action);
        callback.accept(actions);
    }

    @Override
    public void onPerformDirectAction(String actionId, Bundle arguments,
            CancellationSignal cancellationSignal, Consumer<Bundle> callback) {
        Log.v(TAG, "onPerformDirectAction(): " + Utils.toBundleString(arguments));
        if (arguments == null || !arguments.getString(Utils.VOICE_INTERACTION_KEY_ARGUMENTS)
                .equals(Utils.VOICE_INTERACTION_KEY_ARGUMENTS)) {
            reportActionFailed(callback);
            return;
        }
        final RemoteCallback cancelCallback = arguments.getParcelable(
                Utils.DIRECT_ACTIONS_KEY_CANCEL_CALLBACK);
        if (cancelCallback != null) {
            cancellationSignal.setOnCancelListener(() -> reportActionCancelled(
                    cancelCallback::sendResult));
            reportActionExecuting(callback);
        } else {
            reportActionPerformed(callback);
        }
    }

    private void detectDestroyedInteractor(@NonNull RemoteCallback callback) {
        final CountDownLatch latch = new CountDownLatch(1);
        final VoiceInteractor interactor = getVoiceInteractor();
        interactor.registerOnDestroyedCallback(AsyncTask.THREAD_POOL_EXECUTOR, latch::countDown);
        Utils.await(latch);

        try {
            // Check that the interactor is properly marked destroyed. Polls the values since
            // there's no synchronization between destroy() and these methods.
            long pollingTimeoutMs = 3000;
            PollingCheck.check(
                    "onDestroyedCallback called but interactor isn't destroyed",
                    pollingTimeoutMs,
                    interactor::isDestroyed);
            PollingCheck.check(
                    "onDestroyedCallback called but activity still has an interactor",
                    pollingTimeoutMs,
                    () -> getVoiceInteractor() == null);
        } catch (Exception e) {
            Truth.assertWithMessage("Unexpected exception: " + e).fail();
        }

        final Bundle result = new Bundle();
        result.putBoolean(Utils.DIRECT_ACTIONS_KEY_RESULT, true);
        Log.v(TAG, "detectDestroyedInteractor(): " + Utils.toBundleString(result));
        callback.sendResult(result);
    }

    private void invalidateDirectActions(@NonNull RemoteCallback callback) {
        getVoiceInteractor().notifyDirectActionsChanged();
        final Bundle result = new Bundle();
        result.putBoolean(Utils.DIRECT_ACTIONS_KEY_RESULT, true);
        Log.v(TAG, "invalidateDirectActions(): " + Utils.toBundleString(result));
        callback.sendResult(result);
    }

    private void getPackageName(@NonNull RemoteCallback callback) {
        String packageName = getVoiceInteractor().getPackageName();
        final Bundle result = new Bundle();
        result.putString(Utils.DIRECT_ACTIONS_KEY_RESULT, packageName);
        Log.v(TAG, "getPackageName(): " + Utils.toBundleString(result));
        callback.sendResult(result);
    }

    private void getPackageInfo(@NonNull RemoteCallback callback) {
        String packageName = getVoiceInteractor().getPackageName();
        PackageManager packageManager = getPackageManager();
        final Bundle result = new Bundle();
        if (packageManager != null) {
            Log.v(TAG, "Found Package Manager");
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(packageName,
                        PackageManager.GET_META_DATA | PackageManager.GET_SERVICES);
                result.putParcelable(Utils.DIRECT_ACTIONS_KEY_RESULT, packageInfo);
                callback.sendResult(result);
                return;
            } catch (NameNotFoundException e) {
                Log.e(TAG, "getPackageInfo failed: " + e.toString());
            } catch (Exception e) {
                Log.e(TAG, "getPackageInfo failed with Exception: " + e.toString());
            }
        }

        result.putParcelable(Utils.DIRECT_ACTIONS_KEY_RESULT, null);
        callback.sendResult(result);
    }

    private void doFinish(@NonNull RemoteCallback callback) {
        finish();
        final Bundle result = new Bundle();
        result.putBoolean(Utils.DIRECT_ACTIONS_KEY_RESULT, true);
        Log.v(TAG, "doFinish(): " + Utils.toBundleString(result));
        callback.sendResult(result);
    }

    private static void reportActionPerformed(Consumer<Bundle> callback) {
        final Bundle result = new Bundle();
        result.putString(Utils.DIRECT_ACTIONS_KEY_RESULT,
                Utils.DIRECT_ACTIONS_RESULT_PERFORMED);
        Log.v(TAG, "reportActionPerformed(): " + Utils.toBundleString(result));
        callback.accept(result);
    }

    private static void reportActionCancelled(Consumer<Bundle> callback) {
        final Bundle result = new Bundle();
        result.putString(Utils.DIRECT_ACTIONS_KEY_RESULT,
                Utils.DIRECT_ACTIONS_RESULT_CANCELLED);
        Log.v(TAG, "reportActionCancelled(): " + Utils.toBundleString(result));
        callback.accept(result);
    }

    private static void reportActionExecuting(Consumer<Bundle> callback) {
        final Bundle result = new Bundle();
        result.putString(Utils.DIRECT_ACTIONS_KEY_RESULT,
                Utils.DIRECT_ACTIONS_RESULT_EXECUTING);
        Log.v(TAG, "reportActionExecuting(): " + Utils.toBundleString(result));
        callback.accept(result);
    }

    private static void reportActionFailed(Consumer<Bundle> callback) {
        Log.v(TAG, "reportActionFailed()");
        callback.accept( new Bundle());
    }
}
