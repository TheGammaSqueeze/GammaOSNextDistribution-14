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
package android.app.notification.current.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NotificationManagerBroadcastReceiver extends BroadcastReceiver {
    private CountDownLatch latch;
    public List<Intent> results = new ArrayList<>();
    public Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        results.add(intent);
        latch.countDown();
    }

    public Object getExtra(String key, int index, long timeout) throws InterruptedException {
        latch.await(timeout, TimeUnit.MILLISECONDS);
        return results.get(index).getExtras().get(key);
    }

    public void register(Context context, String action, int count) {
        latch = new CountDownLatch(count);
        mContext = context;
        mContext.registerReceiver(
                this, new IntentFilter(action), Context.RECEIVER_EXPORTED);
    }

    /**
     * Waits at most {@code duration} until all the broadcasts specified in {@link #register} have
     * arrived, and fails if they have not.
     */
    public void assertBroadcastsReceivedWithin(Duration duration) throws InterruptedException {
        boolean received = latch.await(duration.toMillis(), TimeUnit.MILLISECONDS);
        assertWithMessage(
                "Still missing " + latch.getCount() + " broadcasts after " + duration)
                .that(received).isTrue();
    }

    public void unregister() {
        mContext.unregisterReceiver(this);
    }
}
