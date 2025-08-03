/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android;

import static org.mockito.Mockito.spy;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.android.internal.telephony.PhoneConfigurationManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Helper class to load Mockito Resources into a test.
 */
public class TelephonyTestBase {
    @Rule public final MockitoRule mocks = MockitoJUnit.rule();

    protected TestContext mContext;

    private final HashMap<InstanceKey, Object> mOldInstances = new HashMap<>();
    private final LinkedList<InstanceKey> mInstanceKeys = new LinkedList<>();

    @Before
    public void setUp() throws Exception {
        mContext = spy(new TestContext());
        // Set up the looper if it does not exist on the test thread.
        if (Looper.myLooper() == null) {
            Looper.prepare();
            // Wait until the looper is not null anymore
            for(int i = 0; i < 5; i++) {
                if (Looper.myLooper() != null) {
                    break;
                }
                Looper.prepare();
                Thread.sleep(100);
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        // Ensure there are no static references to handlers after test completes.
        PhoneConfigurationManager.unregisterAllMultiSimConfigChangeRegistrants();
        restoreInstances();
    }

    protected final boolean waitForExecutorAction(Executor executor, long timeoutMillis) {
        final CountDownLatch lock = new CountDownLatch(1);
        executor.execute(() -> {
            lock.countDown();
        });
        while (lock.getCount() > 0) {
            try {
                return lock.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
        return true;
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

    protected final void waitForHandlerActionDelayed(Handler h, long timeoutMillis, long delayMs) {
        final CountDownLatch lock = new CountDownLatch(1);
        h.postDelayed(lock::countDown, delayMs);
        while (lock.getCount() > 0) {
            try {
                lock.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }

    protected void waitForMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Log.e("TelephonyTestBase", "InterruptedException while waiting: " + e);
        }
    }

    protected synchronized void replaceInstance(final Class c, final String instanceName,
            final Object obj, final Object newValue)
            throws Exception {
        Field field = c.getDeclaredField(instanceName);
        field.setAccessible(true);

        InstanceKey key = new InstanceKey(c, instanceName, obj);
        if (!mOldInstances.containsKey(key)) {
            mOldInstances.put(key, field.get(obj));
            mInstanceKeys.add(key);
        }
        field.set(obj, newValue);
    }

    private synchronized void restoreInstances() throws Exception {
        Iterator<InstanceKey> it = mInstanceKeys.descendingIterator();

        while (it.hasNext()) {
            InstanceKey key = it.next();
            Field field = key.mClass.getDeclaredField(key.mInstName);
            field.setAccessible(true);
            field.set(key.mObj, mOldInstances.get(key));
        }

        mInstanceKeys.clear();
        mOldInstances.clear();
    }

    private static class InstanceKey {
        public final Class mClass;
        public final String mInstName;
        public final Object mObj;
        InstanceKey(final Class c, final String instName, final Object obj) {
            mClass = c;
            mInstName = instName;
            mObj = obj;
        }

        @Override
        public int hashCode() {
            return (mClass.getName().hashCode() * 31 + mInstName.hashCode()) * 31;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }

            InstanceKey other = (InstanceKey) obj;
            return (other.mClass == mClass && other.mInstName.equals(mInstName)
                    && other.mObj == mObj);
        }
    }

    protected TestContext getTestContext() {
        return mContext;
    }
}
