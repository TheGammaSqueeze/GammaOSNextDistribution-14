/*
 * Copyright (C) 2016 Google Inc.
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

package com.android.cellbroadcastreceiver.unit;

import static org.mockito.Mockito.spy;

import android.app.Activity;
import android.app.ResourcesManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Handler;
import android.test.ActivityUnitTestCase;
import android.util.Log;
import android.view.Display;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public class CellBroadcastActivityTestCase<T extends Activity> extends ActivityUnitTestCase<T> {

    protected TestContext mContext;

    private T mActivity;

    CellBroadcastActivityTestCase(Class<T> activityClass) {
        super(activityClass);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = new TestContext(getInstrumentation().getTargetContext());
        setActivityContext(mContext);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    protected T startActivity() throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity = startActivity(createActivityIntent(), null, null);
            }
        });
        return mActivity;
    }

    protected void stopActivity() throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                getInstrumentation().callActivityOnStop(mActivity);
            }
        });
    }

    protected void leaveActivity() throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                getInstrumentation().callActivityOnUserLeaving(mActivity);
            }
        });
    }

    public static void waitForMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    protected Intent createActivityIntent() {
        Intent intent = new Intent();
        return intent;
    }

    protected <S> void injectSystemService(Class<S> cls, S service) {
        mContext.injectSystemService(cls, service);
    }

    protected final void waitForHandlerAction(Handler h, long timeoutMillis) {
        final CountDownLatch lock = new CountDownLatch(1);
        h.post(lock::countDown);
        while (lock.getCount() > 0) {
            try {
                lock.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }
    public static class TestContext extends ContextWrapper {

        private static final String TAG = TestContext.class.getSimpleName();

        private HashMap<String, Object> mInjectedSystemServices = new HashMap<>();

        private Resources mResources;

        boolean mIsOverrideConfigurationEnabled;

        private PackageManager mPackageManager;

        private SharedPreferences mSharedPreferences;

        public TestContext(Context base) {
            super(base);
            mResources = spy(super.getResources());
        }

        public <S> void injectSystemService(Class<S> cls, S service) {
            final String name = getSystemServiceName(cls);
            mInjectedSystemServices.put(name, service);
        }

        public void injectPackageManager(PackageManager packageManager) {
            mPackageManager = packageManager;
        }

        public void injectSharedPreferences(SharedPreferences sp) {
            mSharedPreferences = sp;
        }

        @Override
        public Display getDisplay() {
            return ResourcesManager.getInstance().getAdjustedDisplay(Display.DEFAULT_DISPLAY,
                    null);
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public Object getSystemService(String name) {
            if (mInjectedSystemServices.containsKey(name)) {
                Log.d(TAG, "return mocked system service for " + name);
                return mInjectedSystemServices.get(name);
            }
            Log.d(TAG, "return real system service for " + name);
            return super.getSystemService(name);
        }

        @Override
        public Resources getResources() {
            return mResources;
        }

        @Override
        public PackageManager getPackageManager() {
            if (mPackageManager != null) {
                return mPackageManager;
            }
            return super.getPackageManager();
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            if (mSharedPreferences != null) {
                return mSharedPreferences;
            }
            return super.getSharedPreferences(name, mode);
        }

        @Override
        public Context createConfigurationContext(Configuration overrideConfiguration) {
            if (!mIsOverrideConfigurationEnabled) {
                return this;
            }

            TestContext newTestContext = new TestContext(
                    super.createConfigurationContext(overrideConfiguration));
            newTestContext.mInjectedSystemServices.putAll(mInjectedSystemServices);
            return newTestContext;
        }

        public void enableOverrideConfiguration(boolean enabled) {
            mIsOverrideConfigurationEnabled = enabled;
        }

    }

    protected void waitForChange(BooleanSupplier condition, long timeoutMs) {
        CountDownLatch latch = new CountDownLatch(1);
        new Thread(() -> {
            while (latch.getCount() > 0 && !condition.getAsBoolean()) {
                // do nothing
            }
            latch.countDown();
        }).start();

        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // do nothing
        }
        latch.countDown();
    }
}
