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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.util.concurrent.CountDownLatch;

/**
 * A minimal {@link Service} implementation intended to test cases where a {@link ConnectionService}
 * tries to return a null binding.
 */
public class NullBindingConnectionService extends Service {
    public static CountDownLatch sBindLatch = new CountDownLatch(1);
    public static CountDownLatch sUnbindLatch = new CountDownLatch(1);

    public NullBindingConnectionService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        CountDownLatch latch = sBindLatch;
        sUnbindLatch = new CountDownLatch(1);
        latch.countDown();
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        CountDownLatch latch = sUnbindLatch;
        sBindLatch = new CountDownLatch(1);
        latch.countDown();
        return false;
    }
}
