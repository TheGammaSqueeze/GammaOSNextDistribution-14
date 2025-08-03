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

package android.virtualdevice.cts.applaunch;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.test.rule.ServiceTestRule;

import java.util.concurrent.TimeoutException;

public class AppComponents {

    private AppComponents() {}

    /** An empty activity that does nothing. */
    public static class EmptyActivity extends Activity {}

    /** Another empty activity that does nothing. */
    public static class SecondActivity extends Activity {}

    /** A test service to test pending intents and context association with virtual devices. */
    public static class TestService extends Service {

        static final String ACTION_START_TRAMPOLINE_ACTIVITY =
                "android.virtualdevice.applaunch.ACTION_START_TRAMPOLINE_ACTIVITY";

        private final IBinder mBinder = new TestService.TestBinder();

        private class TestBinder extends Binder {
            TestService getService() {
                return TestService.this;
            }
        }

        public TestService() {}

        static TestService startService(Context context) throws TimeoutException {
            final Intent intent = new Intent(context, TestService.class);
            final ServiceTestRule serviceRule = new ServiceTestRule();
            IBinder serviceToken = serviceRule.bindService(intent);
            return ((TestService.TestBinder) serviceToken).getService();
        }

        @Override
        public IBinder onBind(Intent intent) {
            return mBinder;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (intent != null && ACTION_START_TRAMPOLINE_ACTIVITY.equals(intent.getAction())) {
                startActivity(new Intent(this, EmptyActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return START_STICKY;
        }
    }
}
