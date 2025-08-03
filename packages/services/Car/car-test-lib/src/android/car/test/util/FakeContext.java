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

package android.car.test.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Handler;
import android.os.MessageQueue;
import android.test.mock.MockContext;
import android.util.ArrayMap;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * A fake implementation for {@link android.content.Context}, that provides the following
 * functions:
 * <ul>
 *     <li> Fake implementations for {@link #registerReceiver} and {@link #unregisterReceiver}.
 *     The helper methods {@link #verifyReceiverRegistered} and
 *     {@link #verifyReceiverNotRegistered} can be used to validate the state.
 *     <li> Fake implementation for {@link #sendBroadcast} that sends the intent to a registered
 *     {@link BroadcastReceiver}.
 *     <li> Fake implementations for {@link #getSystemService} and {@link #getSystemServiceName}.
 *     Helper method {@link #setSystemService} can be used to provide values returned by these
 *     methods.
 *     <li> Fake implementation for {@link #getResources}. Helper method {@link #setResources}
 *     can be used to provide a value for this.
 * </ul>
 */
// TODO(b/202420937): Add unit tests for this class.
public final class FakeContext extends MockContext {

    private final Map<String, Object> mSystemServices = new ArrayMap<>();

    private BroadcastReceiver mReceiver;
    private IntentFilter mIntentFilter;
    private Handler mHandler;
    private Resources mResources;

    public <T> void setSystemService(Class<T> serviceClass, T serviceInstance) {
        mSystemServices.put(serviceClass.getName(), serviceInstance);
    }

    @Override
    public Object getSystemService(String name) {
        return mSystemServices.get(name);
    }

    @Override
    public String getSystemServiceName(Class<?> serviceClass) {
        return serviceClass.getName();
    }

    @Override
    public Resources getResources() {
        return mResources;
    }

    public void setResources(Resources resources) {
        mResources = resources;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        return registerReceiver(receiver, filter, null, null);
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter,
            String broadcastPermission, Handler scheduler) {
        mReceiver = receiver;
        mIntentFilter = filter;
        mHandler = scheduler;

        return null;
    }

    @Override
    public void sendBroadcast(Intent intent) {
        if (mHandler == null) {
            mReceiver.onReceive(this, intent);
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        MessageQueue.IdleHandler queueIdleHandler = () -> {
            latch.countDown();
            return false;
        };
        mHandler.getLooper().getQueue().addIdleHandler(queueIdleHandler);

        mHandler.post(() -> mReceiver.onReceive(this, intent));

        // wait until the queue is idle
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for Broadcast Intent to be received", e);
        } finally {
            mHandler.getLooper().getQueue().removeIdleHandler(queueIdleHandler);
        }
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {
        if (receiver == mReceiver) {
            mReceiver = null;
            mIntentFilter = null;
            mHandler = null;
        }
    }

    public void verifyReceiverNotRegistered() {
        assertThat(mIntentFilter).isNull();
        assertThat(mReceiver).isNull();
        assertThat(mHandler).isNull();
    }

    public void verifyReceiverRegistered(String expectedAction) {
        assertThat(mIntentFilter.actionsIterator()).isNotNull();
        ArrayList<String> actions = Lists.newArrayList(mIntentFilter.actionsIterator());
        assertWithMessage("IntentFilter actions").that(actions).contains(expectedAction);
        assertWithMessage("Registered BroadcastReceiver").that(mReceiver).isNotNull();
    }
}
