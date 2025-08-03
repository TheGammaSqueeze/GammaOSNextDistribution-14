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

package com.google.android.car.kitchensink.bluetooth;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;

/**
 * Custom {@link InCallService} that allows Kitchen Sink to manage phone calls using the public
 * Telecom APIs. See https://developer.android.com/reference/android/telecom/InCallService.
 *
 * <p> Kitchen Sink must be selected as the default phone app in order to manage phone calls.
 */
public class InCallServiceImpl extends InCallService {
    private static final String TAG = "InCallServiceImpl";
    public static final String ACTION_LOCAL_BIND = "local_bind";

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: " + intent);
        return ACTION_LOCAL_BIND.equals(intent.getAction())
                ? new LocalBinder()
                : super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind, intent: " + intent);
        if (ACTION_LOCAL_BIND.equals(intent.getAction())) {
            return false;
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        Log.i(TAG, "Call" + call.toString() + " added");
    }

    public class LocalBinder extends Binder {
        public InCallServiceImpl getService() {
            if (getCallingPid() == Process.myPid()) {
                return InCallServiceImpl.this;
            }
            return null;
        }
    }
}
