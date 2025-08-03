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
package android.app.cts.shortfgstest;

import static android.app.cts.shortfgstesthelper.ShortFgsHelper.TAG;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.android.compatibility.common.util.TestUtils;

import org.junit.Assert;

/**
 * Helper class for binding / unbinding a service.
 */
public class ServiceBinder implements AutoCloseable {
    private final Context mContext;
    private final ComponentName mComponentName;
    private final int mFlags;

    private volatile boolean mCallbackCalled;
    private volatile boolean mIsBound;

    private ServiceBinder(Context context, ComponentName componentName, int flags) {
        mContext = context;
        mComponentName = componentName;
        mFlags = flags;
    }

    /**
     * Create a binding.
     */
    public static ServiceBinder bind(Context c, ComponentName cn, int flags) throws Exception {
        final ServiceBinder b = new ServiceBinder(c, cn, flags);

        b.mCallbackCalled = false;
        b.doBind();

        TestUtils.waitUntil("bindService() timed out", () -> b.mCallbackCalled);
        Assert.assertTrue("Service wasn't bound", b.isBound());

        return b;
    }

    /**
     * @return true if the service is currently bound.
     */
    public boolean isBound() {
        return mIsBound;
    }

    private void doBind() {
        Assert.assertFalse("Already bound", mIsBound);

        mContext.bindService(
                new Intent().setComponent(mComponentName),
                mServiceConnection,
                mFlags);
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.w(TAG, "onServiceConnected: " + name);
            mIsBound = true;
            mCallbackCalled = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "onServiceDisconnected: " + name);
            mIsBound = false;
            mCallbackCalled = true;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.w(TAG, "onBindingDied: " + name);
            mIsBound = false;
            mCallbackCalled = true;
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.w(TAG, "onNullBinding: " + name);
            mIsBound = false;
            mCallbackCalled = true;
        }
    };


    @Override
    public void close() throws Exception {
        try {
            mContext.unbindService(mServiceConnection);
        } finally {
            mIsBound = false;
        }
    }
}
