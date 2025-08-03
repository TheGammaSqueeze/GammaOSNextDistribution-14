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

package com.android.bedstead.testapp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.compatibility.common.util.enterprise.DeviceAdminReceiverUtils;

/**
 * {@link BroadcastReceiver} allowing for simple control of test apps using broadcasts.
 */
public final class TestAppBroadcastController extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        setResultCode(Activity.RESULT_OK);
        if (DeviceAdminReceiverUtils.disableSelf(context, intent)) return;
    }
}
