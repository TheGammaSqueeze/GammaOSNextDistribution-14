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

package android.telecom.cts;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.CountDownLatch;

public class NullBindingCallRedirectionServiceController extends Service {
    private static final String TAG = NullBindingCallRedirectionServiceController.class
            .getSimpleName();
    public static final String CONTROL_INTERFACE_ACTION =
            "android.telecom.cts.ACTION_CONTROL_CALL_REDIRECTION_SERVICE";
    public static final ComponentName CONTROL_INTERFACE_COMPONENT =
            ComponentName.unflattenFromString(
                    "android.telecom.cts/.NullBindingCallRedirectionServiceController");

    public static CountDownLatch sBindLatch = new CountDownLatch(1);
    public static CountDownLatch sUnbindLatch = new CountDownLatch(1);

    private static NullBindingCallRedirectionServiceController
            sCallRedirectionServiceController = null;


    public static NullBindingCallRedirectionServiceController getInstance() {
        return sCallRedirectionServiceController;
    }

    public static void resetBindLatches() {
        sBindLatch = new CountDownLatch(1);
        sUnbindLatch = new CountDownLatch(1);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind: returning null binding");
        sCallRedirectionServiceController = this;
        // Treat case as null binding from onBind. This should hit onNullBinding.
        sUnbindLatch = new CountDownLatch(1);
        sBindLatch.countDown();
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind: unbinding service");
        sCallRedirectionServiceController = null;
        sUnbindLatch.countDown();
        return false;
    }
}

