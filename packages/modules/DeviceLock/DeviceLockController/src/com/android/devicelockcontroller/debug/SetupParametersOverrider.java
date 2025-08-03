/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.devicelockcontroller.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserManager;
import android.text.TextUtils;

import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * A helper class used to receive commands from ADB.
 * Used for testing purpose only.
 */
public final class SetupParametersOverrider extends BroadcastReceiver {

    private static final String TAG = "SetupParametersOverrider";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Build.isDebuggable()) {
            LogUtil.w(TAG, "Adb command is not supported in non-debuggable build!");
            return;
        }
        if (!TextUtils.equals(intent.getComponent().getClassName(), this.getClass().getName())) {
            LogUtil.w(TAG, "Implicit intent should not be used!");
            return;
        }

        final boolean isUserProfile =
                context.getSystemService(UserManager.class).isProfile();
        if (isUserProfile) {
            LogUtil.w(TAG, "Broadcast should not target user profiles");
            return;
        }

        Futures.addCallback(
                SetupParametersClient.getInstance().overridePrefs(intent.getExtras()),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        LogUtil.i(TAG, "Successfully override setup parameters!");
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Failed to override setup parameters!", t);
                    }
                }, MoreExecutors.directExecutor());
    }
}
