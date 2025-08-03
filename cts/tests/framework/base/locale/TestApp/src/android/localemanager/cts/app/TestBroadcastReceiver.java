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

package android.localemanager.cts.app;

import static android.localemanager.cts.util.LocaleConstants.TEST_APP_BROADCAST_INFO_PROVIDER_ACTION;
import static android.localemanager.cts.util.LocaleUtils.constructResultIntent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * A broadcast receiver that listens to {@link android.content.Intent#ACTION_LOCALE_CHANGED}
 * when the locale for this app is changed by the test.
 */
public class TestBroadcastReceiver extends BroadcastReceiver{

    @Override
    public void onReceive(Context context, Intent intent) {

        // Upon successful receipt of broadcast, send back the response to the test which invoked
        // the change, so that it can verify correctness.
        if (Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
            context.sendBroadcast(constructResultIntent(TEST_APP_BROADCAST_INFO_PROVIDER_ACTION,
                    intent));
        }
    }
}
