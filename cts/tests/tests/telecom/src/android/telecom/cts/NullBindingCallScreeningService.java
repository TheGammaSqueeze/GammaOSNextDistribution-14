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

package android.telecom.cts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

import java.util.concurrent.CountDownLatch;

/**
 * Provides a minimal CTS-test implementation of {@link CallScreeningService}.
 * This emulates an implementation of {@link CallScreeningService} that returns a null binding.
 * This is used to test null binding cases to ensure we unbind the service when a null binding is
 * received from onBind.
 */
public class NullBindingCallScreeningService extends CallScreeningService {
    private static final String TAG = NullBindingCallScreeningService.class.getSimpleName();
    public static CountDownLatch sBindLatch = new CountDownLatch(1);
    public static CountDownLatch sUnbindLatch = new CountDownLatch(1);

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind: returning null service");
        sUnbindLatch = new CountDownLatch(1);
        sBindLatch.countDown();
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind: unbinding service");
        sBindLatch = new CountDownLatch(1);
        sUnbindLatch.countDown();
        return false;
    }

    @Override
    public void onScreenCall(Call.Details callDetails) {
        Log.i(TAG, "onScreenCall");
    }

    public static void resetBindLatches() {
        sBindLatch = new CountDownLatch(1);
        sUnbindLatch = new CountDownLatch(1);
    }

    public static void enableNullBindingCallScreeningService(Context context) {
        ComponentName componentName = new ComponentName(context,
                NullBindingCallScreeningService.class);
        context.getPackageManager().setComponentEnabledSetting(componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    public static void disableNullBindingCallScreeningService(Context context) {
        ComponentName componentName = new ComponentName(context,
                NullBindingCallScreeningService.class);
        context.getPackageManager().setComponentEnabledSetting(componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }
}
