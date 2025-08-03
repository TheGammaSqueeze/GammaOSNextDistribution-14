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

package android.stats.cts_root;

import static com.google.common.truth.Truth.assertThat;

import android.app.StatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.stats.StatsHelper;

import org.junit.Test;

public class StatsHelperTest {
    private static final String TAG = StatsHelperTest.class.getSimpleName();

    private static class MockReceiver extends BroadcastReceiver  {
        protected boolean mCalledOnReceive = false;

        @Override
        public synchronized void onReceive(Context context, Intent intent) {
            mCalledOnReceive = true;
            onReceiveInternal(context, intent);
            notifyAll();
        }

        protected void onReceiveInternal(Context context, Intent intent) {
        }

        public synchronized void waitForReceiver(long timeout) throws InterruptedException {
            if (!mCalledOnReceive) {
                wait(timeout);
            }
            assertThat(mCalledOnReceive).isTrue();
        }
    }

    @Test
    public void testSendStatsdReadyBroadcast() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final IntentFilter intentFilter = new IntentFilter(StatsManager.ACTION_STATSD_STARTED);
        final int flags = 0x01000000; // Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND
        MockReceiver receiver = new MockReceiver() {
            @Override
            protected void onReceiveInternal(Context context, Intent intent) {
                assertThat(intent.getAction()).isEqualTo(StatsManager.ACTION_STATSD_STARTED);
                assertThat(intent.getFlags() & flags).isEqualTo(flags);
            }
        };

        context.registerReceiver(receiver, intentFilter);

        StatsHelper.sendStatsdReadyBroadcast(context);

        receiver.waitForReceiver(2_000);
    }
}
