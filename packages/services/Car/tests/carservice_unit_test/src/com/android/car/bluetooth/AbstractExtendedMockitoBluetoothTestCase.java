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
package com.android.car.bluetooth;

import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.car.AbstractExtendedMockitoCarServiceTestCase;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Context.RegisterReceiverFlags;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.test.mock.MockContentResolver;

import com.android.internal.util.test.BroadcastInterceptingContext;
import com.android.internal.util.test.FakeSettingsProvider;

import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.Mock;

import java.util.HashMap;

/**
 * Base class for Bluetooth tests, provides mocking of BluetoothAdapter. Also provides CarService,
 * and Settings-related, helpers.
 *
 * <p>Uses {@link com.android.dx.mockito.inline.extended.ExtendedMockito} to mock static and/or
 * final classes and methods.
 *
 * <p><b>Note: </b>when using this class, you must include the following
 * dependencies in {@code Android.bp} (or {@code Android.mk}:
 * <pre><code>
    jni_libs: [
        "libdexmakerjvmtiagent",
        "libstaticjvmtiagent",
    ],

   LOCAL_JNI_SHARED_LIBRARIES := \
      libdexmakerjvmtiagent \
      libstaticjvmtiagent \
 *  </code></pre>
 */
abstract class AbstractExtendedMockitoBluetoothTestCase
        extends AbstractExtendedMockitoCarServiceTestCase {

    protected static final int USER_ID = 116;

    @Mock protected UserManager mMockUserManager;

    // TODO(b/268515318): withoug this rule, BluetoothConnectionRetryManagerTest would fail, even
    // thouch AbstractExtendedMockitoTestCase clears it at the end. This is a hack, but it wouldn't
    // be needed if we adpt a @Rule based approach (as FrameworksMockingServicesTests is doing on
    // commit 70f1052679bfab11bbbef0d16f4709a7e45b7a1d)
    @Rule
    public final TestRule mClearInlineMocksRule = new TestRule() {

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    clearInlineMocks("AbstractExtendedMockitoBluetoothTestCase's rule");
                }
            };
        }
    };

    /**
     * Set the status of a user ID
     */
    public final void setUserUnlocked(int userId, boolean status) {
        when(mMockUserManager.isUserUnlocked(userId)).thenReturn(status);
        when(mMockUserManager.isUserUnlocked(UserHandle.of(userId))).thenReturn(status);
    }

    /**
     * For calls to {@code Settings}.
     */
    public class MockContext extends BroadcastInterceptingContext {
        private MockContentResolver mContentResolver;
        private FakeSettingsProvider mContentProvider;

        private final HashMap<String, Object> mMockedServices;

        MockContext(Context base) {
            super(base);
            FakeSettingsProvider.clearSettingsProvider();
            mContentResolver = new MockContentResolver(this);
            mContentProvider = new FakeSettingsProvider();
            mContentResolver.addProvider(Settings.AUTHORITY, mContentProvider);
            mMockedServices = new HashMap<String, Object>();
        }

        public void release() {
            FakeSettingsProvider.clearSettingsProvider();
        }

        @Override
        public ContentResolver getContentResolver() {
            return mContentResolver;
        }

        @Override
        public Context createContextAsUser(UserHandle user, @CreatePackageOptions int flags) {
            return this;
        }

        @Override
        public Intent registerReceiverAsUser(BroadcastReceiver receiver, UserHandle user,
                IntentFilter filter, String broadcastPermission, Handler scheduler,
                @RegisterReceiverFlags int flags) {
            throw new UnsupportedOperationException("Use createContextAsUser/registerReceiver");
        }

        public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter,
                @RegisterReceiverFlags int flags) {
            // BroadcastInterceptingReceiver doesn't have the variant with flags so just pass the
            // parameters to the function below for the test, as the flags don't really matter
            // for the purpose of getting our test broadcasts routed back to us
            return super.registerReceiver(receiver, filter, null, null);
        }

        public void addMockedSystemService(Class<?> serviceClass, Object service) {
            if (service == null) return;
            String name = getSystemServiceName(serviceClass);
            if (name == null) return;
            mMockedServices.put(name, service);
        }

        @Override
        public @Nullable Object getSystemService(String name) {
            if ((name != null) && name.equals(getSystemServiceName(UserManager.class))) {
                return mMockUserManager;
            } else if ((name != null) && mMockedServices.containsKey(name)) {
                return mMockedServices.get(name);
            }
            return super.getSystemService(name);
        }
    }
}
